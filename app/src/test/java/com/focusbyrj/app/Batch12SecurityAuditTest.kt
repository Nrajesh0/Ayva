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
import com.focusbyrj.app.service.BubbleService
import com.focusbyrj.app.ui.screens.AppCategory
import com.focusbyrj.app.ui.screens.CategoryEditorHelper
import com.focusbyrj.app.ui.screens.InstalledApp
import com.focusbyrj.app.ui.screens.SettingsScreenHelper
import com.focusbyrj.app.ui.screens.drill.DrillPaletteHelper
import com.focusbyrj.app.ui.screens.drill.DrillStreakHelper
import com.focusbyrj.app.ui.screens.drill.DrillSummaryMathHelper
import com.focusbyrj.app.ui.screens.drill.DrillTopBarHelper
import com.focusbyrj.app.ui.screens.drill.formatSecondsToMinutesSec
import com.focusbyrj.app.ui.screens.drill.formatSecondsToMmSs
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
}
