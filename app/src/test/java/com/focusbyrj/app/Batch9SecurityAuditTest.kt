/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitRepository
import com.focusbyrj.app.data.HabitType
import com.focusbyrj.app.data.chat.AyvaChatDatabase
import com.focusbyrj.app.data.chat.toEntity
import com.focusbyrj.app.ui.screens.notes.McqTextParser
import com.focusbyrj.app.util.FocusStatsManager
import com.focusbyrj.app.util.PersistedChatMessage
import com.focusbyrj.app.util.getSafeBoolean
import com.focusbyrj.app.util.getSafeInt
import com.focusbyrj.app.util.router.AyvaIntentRouter
import com.focusbyrj.app.util.router.RouterDestination
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

/**
 * Batch 9 Security & Bug Audit Regression Test Suite
 *
 * Adversarial TDD tests covering:
 * - BATCH-9-001: SafePrefsExtensions getSafeInt prevents 64-bit Long truncation/corruption and getSafeBoolean self-heals stored Ints
 * - BATCH-9-002: HabitRepository.cleanPlaceholderData guards against unintended deletion of user-created habits via migration flag
 * - BATCH-9-003: HabitRepository.recordHabitProgressForDate sanitizes and clamps negative completed counts
 * - BATCH-9-004: AyvaChatDao.deleteOldestMessages with zero or negative limit prevents SQLite whole-table deletion
 * - BATCH-9-005: AyvaIntentRouter /create slash command strips task creation trigger verbs from explicit input
 * - BATCH-9-006: FocusStatsManager.refreshStats covers full 18-week (126-day) history window for AccountScreen heatmap
 * - BATCH-9-007: McqTextParser handles single-line dense input extracting answer and explanation without polluting the last option
 */
@RunWith(RobolectricTestRunner::class)
class Batch9SecurityAuditTest {

    private lateinit var context: Context
    private lateinit var focusDb: FocusDatabase
    private lateinit var habitRepo: HabitRepository

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<FocusApplication>()
        context = app
        focusDb = app.database
        habitRepo = HabitRepository(focusDb.habitDao())
    }

    @After
    fun tearDown() {
        // Clean up databases and test preferences
        val safePrefs = context.getSharedPreferences("test_batch9_safe_prefs", Context.MODE_PRIVATE)
        safePrefs.edit().clear().commit()
    }

    @Test
    fun batch9_001_getSafeIntDoesNotCorruptLongValueExceedingIntMax() {
        val prefs = context.getSharedPreferences("test_batch9_safe_prefs", Context.MODE_PRIVATE)
        val epochTimestamp = 1727580000000L // Exceeds Int.MAX_VALUE (2147483647)
        prefs.edit().putLong("timestamp_key", epochTimestamp).commit()

        // Calling getSafeInt on a Long that cannot fit into an Int should return defValue
        // and MUST NOT overwrite the preference with a truncated 32-bit integer!
        val retrieved = prefs.getSafeInt("timestamp_key", -1)
        assertEquals(-1, retrieved)

        // Ensure the original 64-bit value is intact and not corrupted to a truncated int
        val originalLong = prefs.getLong("timestamp_key", 0L)
        assertEquals(epochTimestamp, originalLong)
    }

    @Test
    fun batch9_001_getSafeBooleanSelfHealsWhenIntStored() {
        val prefs = context.getSharedPreferences("test_batch9_safe_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("flag_active", 1)
            .putInt("flag_inactive", 0)
            .commit()

        // Reading an Int as Boolean should self-heal and return corresponding boolean
        val active = prefs.getSafeBoolean("flag_active", false)
        val inactive = prefs.getSafeBoolean("flag_inactive", true)

        assertTrue("1 should resolve to true", active)
        assertFalse("0 should resolve to false", inactive)

        // Verify self-healed into boolean in preferences
        assertTrue(prefs.getBoolean("flag_active", false))
        assertFalse(prefs.getBoolean("flag_inactive", true))
    }

    @Test
    fun batch9_002_cleanPlaceholderDataGuardedByPreferenceFlagDoesNotDeleteUserHabits() = runBlocking {
        // Clean placeholder once to mark migration as completed
        habitRepo.cleanPlaceholderData(context)

        // Now user creates a habit titled "Deep Reading"
        val userHabit = Habit(
            title = "Deep Reading",
            description = "My daily reading routine",
            iconEmoji = "📚",
            colorHex = "#38BDF8",
            type = HabitType.ONCE_DAILY,
            targetPerDay = 1
        )
        val habitId = habitRepo.insertHabit(userHabit)
        assertTrue(habitId > 0)

        // Subsequent cleanPlaceholderData execution (e.g. on next app start or AccountScreen visit)
        // MUST NOT delete the user's habit!
        habitRepo.cleanPlaceholderData(context)

        val retrievedHabit = habitRepo.getHabitById(habitId)
        assertNotNull("User-created habit 'Deep Reading' must NOT be deleted after migration", retrievedHabit)
        assertEquals("Deep Reading", retrievedHabit?.title)
    }

    @Test
    fun batch9_003_recordHabitProgressForDateSanitizesNegativeCounts() = runBlocking {
        val testHabit = Habit(
            title = "Daily Pushups",
            description = "Fitness",
            iconEmoji = "💪",
            colorHex = "#F43F5E",
            type = HabitType.ONCE_DAILY,
            targetPerDay = 5
        )
        val habitId = habitRepo.insertHabit(testHabit)
        val dateStr = "2026-09-29"

        // Pass negative count
        habitRepo.recordHabitProgressForDate(habitId, dateStr, -10)

        val log = focusDb.habitDao().getLogForHabitAndDate(habitId, dateStr)
        assertNotNull(log)
        assertEquals("Negative count must be sanitized to 0", 0, log?.completedCount)
    }

    @Test
    fun batch9_004_deleteOldestMessagesWithZeroOrNegativeCountDoesNotWipeTable() = runBlocking {
        val db = AyvaChatDatabase.getDatabase(context)
        val chatDao = db.chatDao()
        chatDao.clearAllMessages()

        val msg1 = PersistedChatMessage(id = "msg_1", text = "Hello", isUser = true, timestamp = 1000L).toEntity()
        val msg2 = PersistedChatMessage(id = "msg_2", text = "Hi there", isUser = false, timestamp = 2000L).toEntity()
        chatDao.insertMessages(listOf(msg1, msg2))

        assertEquals(2, chatDao.getMessageCount())

        // Calling with count = 0 or count < 0 must NOT execute LIMIT 0 or LIMIT -1 (which would delete all)
        chatDao.deleteOldestMessages(0)
        assertEquals("Count 0 must not delete any messages", 2, chatDao.getMessageCount())

        chatDao.deleteOldestMessages(-5)
        assertEquals("Negative count must not delete all messages", 2, chatDao.getMessageCount())
    }

    @Test
    fun batch9_005_slashCreateStripsTriggerWordsFromExplicitInput() {
        val dest1 = AyvaIntentRouter.route("/create add buy groceries tomorrow")
        assertTrue("Destination should be TaskCreation", dest1 is RouterDestination.TaskCreation)
        val task1 = dest1 as RouterDestination.TaskCreation
        assertTrue("buy groceries".equals(task1.title, ignoreCase = true))
        assertNotNull(task1.dueDate)

        val dest2 = AyvaIntentRouter.route("/task remind me to call doctor 5pm")
        assertTrue("Destination should be TaskCreation", dest2 is RouterDestination.TaskCreation)
        val task2 = dest2 as RouterDestination.TaskCreation
        assertTrue("call doctor".equals(task2.title, ignoreCase = true))
    }

    @Test
    fun batch9_006_focusStatsManagerLoads126DayWindowForHeatmap() {
        val prefs = context.getSharedPreferences("focus_stats_prefs", Context.MODE_PRIVATE)
        val calPast = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -60) }
        val pastKey = "focus_day_${calPast.get(Calendar.YEAR)}_${calPast.get(Calendar.DAY_OF_YEAR)}"
        val testDurationMs = 1800000L // 30 minutes
        // Ensure app install timestamp is prior to the test date so history isn't zeroed
        prefs.edit()
            .putLong("app_install_timestamp", calPast.timeInMillis - 86400000L)
            .putLong(pastKey, testDurationMs)
            .commit()

        FocusStatsManager.refreshStats(context)
        val stats = FocusStatsManager.statsFlow.value
        val pastDayOfYear = calPast.get(Calendar.DAY_OF_YEAR)

        assertEquals(
            "FocusStatsManager must include days up to 126 days ago for the 18-week heatmap",
            testDurationMs,
            stats.dailyFocusMinutes[pastDayOfYear]
        )
    }

    @Test
    fun batch9_007_mcqParserExtractsSingleLineAnswersAndExplanations() {
        val input = "Q12. Which organelle is known as the powerhouse of the cell? a) Nucleus b) Mitochondria c) Ribosome d) Golgi apparatus Answer: B. Explanation: Mitochondria produces ATP."
        val parsed = McqTextParser.parse(input)
        assertNotNull(parsed)
        assertEquals("Golgi apparatus", parsed!!.options[3].second)
        assertEquals("B.", parsed.answer)
        assertEquals("Mitochondria produces ATP.", parsed.explanation)
    }
}
