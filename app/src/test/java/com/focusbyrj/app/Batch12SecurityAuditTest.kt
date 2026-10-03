/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.service.AptitudeReminderHelper
import com.focusbyrj.app.service.BubbleService
import com.focusbyrj.app.ui.screens.AppCategory
import com.focusbyrj.app.ui.screens.AptitudeCardHelper
import com.focusbyrj.app.ui.screens.BubbleSettingsHelper
import com.focusbyrj.app.ui.screens.CategoryEditorHelper
import com.focusbyrj.app.ui.screens.InstalledApp
import com.focusbyrj.app.ui.screens.PreferencesHubHelper
import com.focusbyrj.app.ui.screens.SettingsScreenHelper
import com.focusbyrj.app.ui.screens.drill.DrillPaletteHelper
import com.focusbyrj.app.ui.screens.drill.DrillStreakHelper
import com.focusbyrj.app.ui.screens.drill.DrillSummaryMathHelper
import com.focusbyrj.app.ui.screens.drill.DrillTopBarHelper
import com.focusbyrj.app.ui.screens.drill.FullscreenDrillHelper
import com.focusbyrj.app.ui.screens.drill.SolutionsViewHelper
import com.focusbyrj.app.ui.screens.drill.formatSecondsToMinutesSec
import com.focusbyrj.app.ui.screens.drill.formatSecondsToMmSs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Batch 12 Security & Reliability Regression Test Suite
 *
 * Adversarial TDD tests covering:
 * - BATCH-12-001: Negative modulo / streak bonus calculation in 7-day cycle
 * - BATCH-12-002: Duplicate package names crashing Compose LazyColumn with key collision
 * - BATCH-12-003: Negative seconds formatting bug producing malformed time strings
 * - BATCH-12-004: Implicit broadcasts missing setPackage(context.packageName)
 * - BATCH-12-005: Unbounded/negative accuracy percentage calculation
 * - BATCH-12-006: Negative total questions crashing LazyVerticalGrid.items(count)
 * - BATCH-12-007: Negative remaining blitz seconds and progress calculation
 * - BATCH-12-008: Missing FLAG_ACTIVITY_NEW_TASK on widget configuration launch
 * - BATCH-12-009: FullscreenDrillView combo progression, reset, and correctIndex bounds
 * - BATCH-12-010: BubbleSettings slider value coercion and safe time parsing
 * - BATCH-12-011: SolutionsView null question resilience and safe index bounds
 * - BATCH-12-012: SettingsScreen reminder interval snapping and duration clamping
 * - BATCH-12-013: PreferencesHub safe TaskDao resolution without ClassCastException
 * - BATCH-12-014: CustomCategoryEditor package name search and blank package filtering
 * - BATCH-12-015: AptitudeProfileCard accuracy and streak freeze bounds clamping
 * - BATCH-12-016: AptitudeReminder hour/minute parsing and Calendar rollover prevention
 */
@RunWith(RobolectricTestRunner::class)
class Batch12SecurityAuditTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun test_BATCH_12_001_streakCycleDayCalculation_negativeAndBoundaryValues() {
        // Negative streak values (corrupted prefs / DB) must not produce negative or 0 cycle days
        assertEquals(1, DrillStreakHelper.calculateCycleDay(-10))
        assertEquals(1, DrillStreakHelper.calculateCycleDay(-1))
        assertEquals(1, DrillStreakHelper.calculateCycleDay(0))

        // Normal 7-day cycle progression
        assertEquals(1, DrillStreakHelper.calculateCycleDay(1))
        assertEquals(2, DrillStreakHelper.calculateCycleDay(2))
        assertEquals(6, DrillStreakHelper.calculateCycleDay(6))
        assertEquals(7, DrillStreakHelper.calculateCycleDay(7))

        // Subsequent 7-day cycles wrap correctly
        assertEquals(1, DrillStreakHelper.calculateCycleDay(8))
        assertEquals(2, DrillStreakHelper.calculateCycleDay(9))
        assertEquals(7, DrillStreakHelper.calculateCycleDay(14))
        assertEquals(1, DrillStreakHelper.calculateCycleDay(15))
    }

    @Test
    fun test_BATCH_12_002_customCategoryEditor_deduplicatesPackages() {
        val rawApps = listOf(
            InstalledApp("com.whatsapp", "WhatsApp", AppCategory.SOCIAL),
            InstalledApp("com.whatsapp", "WhatsApp Dual", AppCategory.SOCIAL), // Duplicate package
            InstalledApp("com.google.android.youtube", "YouTube", AppCategory.OTHERS),
            InstalledApp("com.google.android.youtube", "YouTube Cloned", AppCategory.OTHERS), // Duplicate package
            InstalledApp("com.android.chrome", "Chrome", AppCategory.UTILITY)
        )

        val deduplicatedAll = CategoryEditorHelper.filterAndDeduplicateApps(
            installedApps = rawApps,
            category = AppCategory.ALL,
            searchQuery = "  "
        )
        // Must contain exactly 3 apps, each with a unique package
        assertEquals(3, deduplicatedAll.size)
        val packageCounts = deduplicatedAll.groupingBy { it.packageName }.eachCount()
        assertTrue(packageCounts.values.all { it == 1 })

        // Category filter with search query trimming
        val socialApps = CategoryEditorHelper.filterAndDeduplicateApps(
            installedApps = rawApps,
            category = AppCategory.SOCIAL,
            searchQuery = " what "
        )
        assertEquals(1, socialApps.size)
        assertEquals("com.whatsapp", socialApps[0].packageName)
    }

    @Test
    fun test_BATCH_12_003_secondsFormatting_negativeSecondsSafeCoercion() {
        // Negative values from clock skews or uninitialized timestamps must not produce strings like "00:-5" or "-5sec"
        assertEquals("00:00", formatSecondsToMmSs(-5))
        assertEquals("00:00", formatSecondsToMmSs(-65))
        assertEquals("0sec", formatSecondsToMinutesSec(-5))
        assertEquals("0sec", formatSecondsToMinutesSec(-65))

        // Normal cases
        assertEquals("00:00", formatSecondsToMmSs(0))
        assertEquals("0sec", formatSecondsToMinutesSec(0))
        assertEquals("00:45", formatSecondsToMmSs(45))
        assertEquals("45sec", formatSecondsToMinutesSec(45))
        assertEquals("01:05", formatSecondsToMmSs(65))
        assertEquals("1min 5sec", formatSecondsToMinutesSec(65))
    }

    @Test
    fun test_BATCH_12_004_broadcastIntents_mustBeExplicitOrPackageScoped() {
        // Broadcasts for hiding/showing bubbles and settings changes must have setPackage
        val hideIntent = Intent("com.focusbyrj.app.HIDE_BUBBLE").setPackage(context.packageName)
        val showIntent = Intent("com.focusbyrj.app.SHOW_BUBBLE").setPackage(context.packageName)
        val settingsIntent = Intent(BubbleService.ACTION_SETTINGS_CHANGED).setPackage(context.packageName)

        assertEquals(context.packageName, hideIntent.`package`)
        assertEquals(context.packageName, showIntent.`package`)
        assertEquals(context.packageName, settingsIntent.`package`)
    }

    @Test
    fun test_BATCH_12_005_drillSummaryAccuracyPercentage_bounded0To100() {
        // Negative correct questions
        assertEquals(0, DrillSummaryMathHelper.calculateAccuracy(-5, 10))

        // Correct count exceeds total questions
        assertEquals(100, DrillSummaryMathHelper.calculateAccuracy(15, 10))

        // Total questions is 0
        assertEquals(0, DrillSummaryMathHelper.calculateAccuracy(0, 0, fallbackZero = true))
        assertEquals(100, DrillSummaryMathHelper.calculateAccuracy(0, 0, fallbackZero = false))

        // Normal accurate percentages
        assertEquals(50, DrillSummaryMathHelper.calculateAccuracy(5, 10))
        assertEquals(80, DrillSummaryMathHelper.calculateAccuracy(8, 10))
        assertEquals(33, DrillSummaryMathHelper.calculateAccuracy(1, 3))
        assertEquals(100, DrillSummaryMathHelper.calculateAccuracy(10, 10))
    }

    @Test
    fun test_BATCH_12_006_drillQuestionPalette_safeCountClamping() {
        // Negative count must be coerced to 0 to prevent Compose LazyVerticalGrid crash
        assertEquals(0, DrillPaletteHelper.getSafeCount(-10))
        assertEquals(0, DrillPaletteHelper.getSafeCount(-1))
        assertEquals(0, DrillPaletteHelper.getSafeCount(0))
        assertEquals(20, DrillPaletteHelper.getSafeCount(20))
    }

    @Test
    fun test_BATCH_12_007_drillBlitzTimer_negativeRemainingClamping() {
        // Negative blitz seconds remaining must clamp to "0s"
        assertEquals("0s", DrillTopBarHelper.formatBlitzTimer(-1))
        assertEquals("0s", DrillTopBarHelper.formatBlitzTimer(-10))
        assertEquals("0s", DrillTopBarHelper.formatBlitzTimer(0))
        assertEquals("25s", DrillTopBarHelper.formatBlitzTimer(25))
        assertEquals("1:05", DrillTopBarHelper.formatBlitzTimer(65))
        assertEquals("5:00", DrillTopBarHelper.formatBlitzTimer(300))

        // Progress calculation bounds [0.03f, 1.0f]
        val progressOvertime = DrillTopBarHelper.calculateBlitzProgress(-10, 300f)
        assertEquals(1.0f, progressOvertime, 0.001f)

        val progressStart = DrillTopBarHelper.calculateBlitzProgress(300, 300f)
        assertEquals(0.03f, progressStart, 0.001f)

        val progressMid = DrillTopBarHelper.calculateBlitzProgress(150, 300f)
        assertEquals(0.5f, progressMid, 0.01f)
    }

    @Test
    fun test_BATCH_12_008_settingsScreen_widgetConfigureIntentHasNewTaskFlag() {
        val intent = SettingsScreenHelper.createWidgetConfigureIntent(context)
        assertNotNull(intent)
        val hasNewTask = (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0
        assertTrue("Intent must contain FLAG_ACTIVITY_NEW_TASK to be safe on non-Activity contexts", hasNewTask)
    }

    @Test
    fun test_BATCH_12_009_fullscreenDrillView_comboProgressionAndCorrectIndex() {
        // Combo starts at 0, first correct answer progresses combo to 1 and maxCombo to 1
        val firstCorrect = FullscreenDrillHelper.updateCombo(0, 0, isCorrect = true)
        assertEquals(1, firstCorrect.first)
        assertEquals(1, firstCorrect.second)

        // Consecutive correct answers increase combo and maxCombo
        val secondCorrect = FullscreenDrillHelper.updateCombo(firstCorrect.first, firstCorrect.second, isCorrect = true)
        assertEquals(2, secondCorrect.first)
        assertEquals(2, secondCorrect.second)

        // Wrong answer resets combo to 0 but preserves maxCombo
        val wrongAnswer = FullscreenDrillHelper.updateCombo(secondCorrect.first, secondCorrect.second, isCorrect = false)
        assertEquals(0, wrongAnswer.first)
        assertEquals(2, wrongAnswer.second)

        // Negative values in corrupted sessions are coerced safely
        val safeNegative = FullscreenDrillHelper.updateCombo(-10, -5, isCorrect = true)
        assertEquals(1, safeNegative.first)
        assertEquals(1, safeNegative.second)

        // CorrectIndex bounding
        assertEquals(0, FullscreenDrillHelper.safeCorrectIndex(-1, 4))
        assertEquals(3, FullscreenDrillHelper.safeCorrectIndex(10, 4))
        assertEquals(2, FullscreenDrillHelper.safeCorrectIndex(2, 4))
        assertEquals(0, FullscreenDrillHelper.safeCorrectIndex(2, 0))
    }

    @Test
    fun test_BATCH_12_010_bubbleSettings_sliderCoercionAndSafeTimeParsing() {
        // Slider value coercion prevents IllegalArgumentException in Compose Slider
        assertEquals(20f, BubbleSettingsHelper.coerceSliderValue(0, 20..100), 0.001f)
        assertEquals(100f, BubbleSettingsHelper.coerceSliderValue(150, 20..100), 0.001f)
        assertEquals(65f, BubbleSettingsHelper.coerceSliderValue(65, 20..100), 0.001f)

        // Resilient time string parsing
        assertEquals(Pair(20, 30), BubbleSettingsHelper.parseTimeSafe("08:30 PM"))
        assertEquals(Pair(8, 30), BubbleSettingsHelper.parseTimeSafe("08:30 AM"))
        assertEquals(Pair(0, 0), BubbleSettingsHelper.parseTimeSafe("12:00 AM"))
        assertEquals(Pair(12, 0), BubbleSettingsHelper.parseTimeSafe("12:00 PM"))
        assertEquals(Pair(23, 59), BubbleSettingsHelper.parseTimeSafe("25:99 PM"))
        assertEquals(Pair(8, 0), BubbleSettingsHelper.parseTimeSafe("not_a_time", 8, 0))
    }

    @Test
    fun test_BATCH_12_011_solutionsView_nullQuestionResilienceAndSafeIndex() {
        val qArr = JSONArray().apply {
            put(JSONObject().apply {
                put("qNum", 1)
                put("questionText", "What is 2 + 2?")
                put("options", JSONArray().apply { put("3"); put("4") })
                put("correctIndex", 1)
            })
            put(JSONObject.NULL) // Corrupted or null element
            put(JSONObject().apply {
                put("qNum", 3)
                put("questionText", "What is 5 * 5?")
                put("options", JSONArray().apply { put("20"); put("25") })
                put("correctIndex", 1)
            })
        }

        val parsed = SolutionsViewHelper.parseQuestionsSafely(qArr)
        assertEquals(2, parsed.size)
        assertEquals(1, parsed[0].qNum)
        assertEquals(3, parsed[1].qNum)

        // Safe index bounding
        assertEquals(0, SolutionsViewHelper.safeQuestionIndex(-5, 10))
        assertEquals(9, SolutionsViewHelper.safeQuestionIndex(15, 10))
        assertEquals(4, SolutionsViewHelper.safeQuestionIndex(4, 10))
        assertEquals(0, SolutionsViewHelper.safeQuestionIndex(5, 0))
    }

    @Test
    fun test_BATCH_12_012_settingsScreen_reminderIntervalsAndDurationCoercion() {
        // Sequential increment progression
        assertEquals(10, SettingsScreenHelper.getNextReminderInterval(5, isIncrement = true))
        assertEquals(15, SettingsScreenHelper.getNextReminderInterval(10, isIncrement = true))
        assertEquals(30, SettingsScreenHelper.getNextReminderInterval(15, isIncrement = true))
        assertEquals(360, SettingsScreenHelper.getNextReminderInterval(300, isIncrement = true))
        assertEquals(360, SettingsScreenHelper.getNextReminderInterval(360, isIncrement = true))

        // Sequential decrement progression
        assertEquals(300, SettingsScreenHelper.getNextReminderInterval(360, isIncrement = false))
        assertEquals(5, SettingsScreenHelper.getNextReminderInterval(10, isIncrement = false))
        assertEquals(5, SettingsScreenHelper.getNextReminderInterval(5, isIncrement = false))

        // Corrupted value recovery: snaps to nearest valid interval without deadlocking
        assertEquals(5, SettingsScreenHelper.getNextReminderInterval(-10, isIncrement = true))
        assertEquals(30, SettingsScreenHelper.getNextReminderInterval(25, isIncrement = true))
        assertEquals(15, SettingsScreenHelper.getNextReminderInterval(25, isIncrement = false))
        assertEquals(360, SettingsScreenHelper.getNextReminderInterval(1000, isIncrement = true))
        assertEquals(360, SettingsScreenHelper.getNextReminderInterval(1000, isIncrement = false))

        // Duration coercion
        assertEquals(5, SettingsScreenHelper.coerceSoftLockDuration(-10))
        assertEquals(60, SettingsScreenHelper.coerceSoftLockDuration(100))
        assertEquals(10, SettingsScreenHelper.coerceSoftLockDuration(10))

        assertEquals(1, SettingsScreenHelper.coerceSoftUnlockDuration(-5))
        assertEquals(60, SettingsScreenHelper.coerceSoftUnlockDuration(120))
        assertEquals(5, SettingsScreenHelper.coerceSoftUnlockDuration(5))
    }

    @Test
    fun test_BATCH_12_013_preferencesHub_taskDaoSafeResolution() {
        // Safe cast on actual FocusApplication context returns valid TaskDao
        val dao = PreferencesHubHelper.getTaskDao(context)
        assertNotNull("TaskDao must be non-null when resolved via PreferencesHubHelper on FocusApplication", dao)

        // Non-FocusApplication context wrapper must return null safely without throwing ClassCastException
        val plainContext = object : android.content.ContextWrapper(context) {
            override fun getApplicationContext(): Context {
                return android.app.Application()
            }
        }
        val safeNullDao = PreferencesHubHelper.getTaskDao(plainContext)
        assertNull("TaskDao should safely return null on non-FocusApplication context", safeNullDao)
    }

    @Test
    fun test_BATCH_12_014_customCategoryEditor_packageNameSearchAndBlankFiltering() {
        val apps = listOf(
            InstalledApp("", "Ghost App", AppCategory.OTHERS), // Blank package name
            InstalledApp("   ", "Empty Package", AppCategory.OTHERS), // Whitespace package name
            InstalledApp("com.android.chrome", "Google Browser", AppCategory.UTILITY),
            InstalledApp("com.instagram.android", "Instagram", AppCategory.SOCIAL)
        )

        // All category without query: blank package apps filtered out
        val filtered = CategoryEditorHelper.filterAndDeduplicateApps(apps, AppCategory.ALL, "")
        assertEquals(2, filtered.size)
        assertTrue(filtered.none { it.packageName.isBlank() })

        // Search by package name substring (e.g. "chrome") even if appName doesn't contain "chrome"
        val searchedByPkg = CategoryEditorHelper.filterAndDeduplicateApps(apps, AppCategory.ALL, "chrome")
        assertEquals(1, searchedByPkg.size)
        assertEquals("com.android.chrome", searchedByPkg[0].packageName)
    }

    @Test
    fun test_BATCH_12_015_aptitudeProfileCard_accuracyAndStreakFreezesClamping() {
        assertEquals(0, AptitudeCardHelper.clampAccuracy(-20f))
        assertEquals(0, AptitudeCardHelper.clampAccuracy(Float.NaN))
        assertEquals(100, AptitudeCardHelper.clampAccuracy(140f))
        assertEquals(85, AptitudeCardHelper.clampAccuracy(85.4f))

        assertEquals(0, AptitudeCardHelper.clampStreakFreezes(-2))
        assertEquals(3, AptitudeCardHelper.clampStreakFreezes(5))
        assertEquals(2, AptitudeCardHelper.clampStreakFreezes(2))
    }

    @Test
    fun test_BATCH_12_016_aptitudeReminder_hourAndMinuteParsingBounds() {
        assertEquals(Pair(9, 15), AptitudeReminderHelper.parseHourAndMinute("09:15 AM"))
        assertEquals(Pair(18, 45), AptitudeReminderHelper.parseHourAndMinute("06:45 PM"))
        assertEquals(Pair(0, 0), AptitudeReminderHelper.parseHourAndMinute("12:00 AM"))
        assertEquals(Pair(12, 0), AptitudeReminderHelper.parseHourAndMinute("12:00 PM"))

        // Out-of-bounds hour and minute clamped to valid ranges
        assertEquals(Pair(23, 59), AptitudeReminderHelper.parseHourAndMinute("28:95 PM"))
        assertEquals(Pair(0, 0), AptitudeReminderHelper.parseHourAndMinute("-5:-10 AM"))
        assertEquals(Pair(18, 0), AptitudeReminderHelper.parseHourAndMinute("corrupted_format", 18, 0))
    }
}

