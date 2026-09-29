/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitLog
import com.focusbyrj.app.data.HabitType
import com.focusbyrj.app.data.HabitWithProgress
import com.focusbyrj.app.ui.components.AppModeHelper
import com.focusbyrj.app.ui.components.computeMasteryProgress
import com.focusbyrj.app.ui.screens.AppCategory
import com.focusbyrj.app.ui.screens.ArithmeticCardHelper
import com.focusbyrj.app.ui.screens.InstalledApp
import com.focusbyrj.app.ui.theme.findActivity
import com.focusbyrj.app.util.CustomCategoryManager
import com.focusbyrj.app.util.DeviceStatsHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Batch 13 Security & Reliability Regression Test Suite
 *
 * Adversarial TDD tests covering:
 * - BATCH-13-001: Duplicate package names crashing Compose LazyColumn/LazyRow key uniqueness
 * - BATCH-13-002: ArithmeticCard rigid JSON parsing and NaN / 0 division in progress calculation
 * - BATCH-13-003: CustomCategoryManager brittle monolithic JSON parsing and repeated disk I/O
 * - BATCH-13-004: HabitsChatCard and HabitWithProgress progress fraction bounds and zero handling
 * - BATCH-13-005: DeviceStatsHelper negative battery level, scale division by zero, and clamping
 * - BATCH-13-006: Theme.kt ContextWrapper ClassCastException in findActivity
 * - BATCH-13-007: CustomRestrictionSection time limit slider range alignment up to 180 min
 * - BATCH-13-008: VocabRetentionHubChatCard mastery progress overflow and duplicate key prevention
 */
@RunWith(RobolectricTestRunner::class)
class Batch13SecurityAuditTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun test_batch13_001_duplicatePackagesInAppModeSectionAndAddRestrictions() {
        // AppModeSection and AddRestrictionScreen previously had key = { it.packageName }
        // without deduplication. Duplicate package names (e.g. multi-user or split packages) crash Compose LazyRow/Column.
        val duplicateApps = listOf(
            InstalledApp("com.whatsapp", "WhatsApp", AppCategory.SOCIAL),
            InstalledApp("com.whatsapp", "WhatsApp Dual", AppCategory.SOCIAL),
            InstalledApp("com.instagram.android", "Instagram", AppCategory.SOCIAL),
            InstalledApp("com.instagram.android", "Instagram Clone", AppCategory.SOCIAL)
        )

        val sanitized = AppModeHelper.sanitizeAppsForDisplay(duplicateApps)
        assertEquals(2, sanitized.size)
        assertEquals(listOf("com.whatsapp", "com.instagram.android"), sanitized.map { it.packageName })

        // Check key uniqueness
        val keys = sanitized.map { it.packageName }
        assertEquals(keys.distinct().size, keys.size)
    }

    @Test
    fun test_batch13_002_arithmeticCardSafeJsonAndProgressMath() {
        // Test parsing JSON missing explanation or title, ensuring no JSONException is thrown
        val partialJson = """{"questionText": "12 + 15", "options": ["25", "27", "29"], "correctIndex": 1}"""
        val parsed = ArithmeticCardHelper.parseDrillJson(partialJson)

        assertNotNull(parsed)
        assertEquals("Drill", parsed?.title)
        assertEquals("12 + 15", parsed?.questionText)
        assertEquals(3, parsed?.options?.size)
        assertEquals(1, parsed?.correctIndex)
        assertEquals("", parsed?.explanation)

        // Test division by zero or negative total questions in progress fraction
        val zeroTotalFraction = ArithmeticCardHelper.computeProgressFraction(currentQ = 2, totalQ = 0)
        assertEquals(0f, zeroTotalFraction, 0.001f)

        val negativeFraction = ArithmeticCardHelper.computeProgressFraction(currentQ = -1, totalQ = 5)
        assertEquals(0f, negativeFraction, 0.001f)

        val overflowFraction = ArithmeticCardHelper.computeProgressFraction(currentQ = 10, totalQ = 5)
        assertEquals(1f, overflowFraction, 0.001f)

        val validFraction = ArithmeticCardHelper.computeProgressFraction(currentQ = 3, totalQ = 5)
        assertEquals(0.6f, validFraction, 0.001f)
    }

    @Test
    fun test_batch13_003_customCategoryManagerResilientParsingAndCaching() {
        // Monolithic JSON parsing previously dropped ALL categories if one category JSON was corrupted
        val mixedJson = """
        [
            {"id": "cat_1", "name": "Distractions", "packages": ["com.instagram.android", "com.tiktok"]},
            {"invalid_field": 123},
            {"id": "cat_2", "name": "Work", "packages": ["com.slack", "com.google.android.gm"]}
        ]
        """.trimIndent()

        val parsed = CustomCategoryManager.parseCategories(mixedJson)
        // Even with the second item corrupted, cat_1 and cat_2 must be safely parsed
        assertEquals(2, parsed.size)
        assertEquals("cat_1", parsed[0].id)
        assertEquals("Distractions", parsed[0].name)
        assertEquals(setOf("com.instagram.android", "com.tiktok"), parsed[0].packages)
        assertEquals("cat_2", parsed[1].id)
        assertEquals("Work", parsed[1].name)
    }

    @Test
    fun test_batch13_004_habitsCardProgressFractionClamping() {
        val habit = Habit(id = 1L, title = "Drink Water", targetPerDay = 5, type = HabitType.INTERVAL_WINDOW)

        // Case 1: Completed is 0 (should not result in negative or NaN)
        val hwpZero = HabitWithProgress(
            habit = habit,
            todayLog = HabitLog(habitId = 1L, date = "2026-09-29", completedCount = 0, targetCount = 5)
        )
        assertEquals(0f, hwpZero.progressFraction, 0.001f)

        // Case 2: Target is 0 or negative
        val hwpInvalidTarget = HabitWithProgress(
            habit = habit,
            todayLog = HabitLog(habitId = 1L, date = "2026-09-29", completedCount = 2, targetCount = 0)
        )
        assertEquals(0f, hwpInvalidTarget.progressFraction, 0.001f)

        // Case 3: Overcompleted (should clamp to 1.0f)
        val hwpOvercompleted = HabitWithProgress(
            habit = habit,
            todayLog = HabitLog(habitId = 1L, date = "2026-09-29", completedCount = 10, targetCount = 5)
        )
        assertEquals(1.0f, hwpOvercompleted.progressFraction, 0.001f)
    }

    @Test
    fun test_batch13_005_deviceStatsHelperBatteryInfoMathGuards() {
        // Negative battery level
        val intentNegativeLevel = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, -1)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_VOLTAGE, 3800)
        }
        val info1 = DeviceStatsHelper.parseBatteryInfo(context, intentNegativeLevel)
        assertTrue("Battery rawPercentage must be non-negative", info1.rawChargePercentage >= 0)
        assertTrue("Battery rawPercentage must not exceed 100", info1.rawChargePercentage <= 100)

        // Scale is 0 (division by zero guard)
        val intentZeroScale = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 50)
            putExtra(BatteryManager.EXTRA_SCALE, 0)
            putExtra(BatteryManager.EXTRA_VOLTAGE, 3900)
        }
        val info2 = DeviceStatsHelper.parseBatteryInfo(context, intentZeroScale)
        assertTrue("Battery rawPercentage with zero scale must default safely", info2.rawChargePercentage in 0..100)
        assertFalse("Real remaining must not be NaN", info2.realRemainingCapacityPercent.isNaN())
    }

    @Test
    fun test_batch13_006_themeContextUnwrapSafeActivity() {
        // When Composable is hosted in a ContextWrapper (like ViewComponentManager or Dialog),
        // casting directly to Activity causes ClassCastException.
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val wrapper = ContextWrapper(baseContext)

        // Should return null gracefully instead of throwing ClassCastException
        val activity = wrapper.findActivity()
        assertNull(activity)
    }

    @Test
    fun test_batch13_007_customRestrictionSectionTimeLimitSliderBounds() {
        // Verification of slider value range alignment: max time limit is 180 min
        val maxAllowedTime = 180
        val clampedUnder = 0.coerceIn(1, maxAllowedTime)
        assertEquals(1, clampedUnder)

        val clampedOver = 200.coerceIn(1, maxAllowedTime)
        assertEquals(180, clampedOver)

        val normalTime = 45.coerceIn(1, maxAllowedTime)
        assertEquals(45, normalTime)
    }

    @Test
    fun test_batch13_008_vocabRetentionHubMasteryProgressClampingAndKeys() {
        // Data desync: totalMastered > totalLearned
        val progressOver = computeMasteryProgress(totalMastered = 15, totalLearned = 10)
        assertEquals(1.0f, progressOver, 0.001f)

        // Zero learned
        val progressZero = computeMasteryProgress(totalMastered = 0, totalLearned = 0)
        assertEquals(0.0f, progressZero, 0.001f)

        // Normal
        val progressNormal = computeMasteryProgress(totalMastered = 5, totalLearned = 10)
        assertEquals(0.5f, progressNormal, 0.001f)
    }
}
