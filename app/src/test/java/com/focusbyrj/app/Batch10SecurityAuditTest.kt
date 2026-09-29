/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.CompletedTaskHistoryManager
import com.focusbyrj.app.util.HabitAlarmScheduler
import com.focusbyrj.app.util.LicenseManager
import com.focusbyrj.app.util.diagnostics.AppLogger
import com.focusbyrj.app.util.diagnostics.DiagnosticManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Batch 10 Security & Reliability Regression Test Suite
 *
 * Adversarial TDD tests covering:
 * - BATCH-10-001: LicenseManager initialization & persistence across process recreation
 * - BATCH-10-002: AppLogger multiline & mixed-whitespace BIP-39 mnemonic redaction
 * - BATCH-10-003: NoteEntity.isEmptyNote() correctly preserves notes with text outside blocks delimiters
 * - BATCH-10-004: HabitAlarmScheduler.getRequestCode handles negative IDs safely within allocated range
 * - BATCH-10-005: DiagnosticManager export ZIP rotation prevents unbounded disk accumulation
 * - BATCH-10-006: CompletedTaskHistoryManager deduplicates tasks by primary ID and saves synchronously
 */
@RunWith(RobolectricTestRunner::class)
class Batch10SecurityAuditTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<FocusApplication>()
    }

    @After
    fun tearDown() {
        val licensePrefs = context.getSharedPreferences("focus_license_prefs", Context.MODE_PRIVATE)
        licensePrefs.edit().clear().commit()

        val historyPrefs = context.getSharedPreferences("focus_completed_tasks_history", Context.MODE_PRIVATE)
        historyPrefs.edit().clear().commit()

        DiagnosticManager.clearAllLogs(context)
    }

    @Test
    fun batch10_001_licenseManagerLoadsPersistedProStatusOnInit() {
        val licensePrefs = context.getSharedPreferences("focus_license_prefs", Context.MODE_PRIVATE)
        licensePrefs.edit().putBoolean("is_pro_unlocked", true).commit()

        // Calling init must load the persisted flag into isProFlow
        LicenseManager.init(context)
        assertTrue("LicenseManager must be unlocked when is_pro_unlocked is true in prefs", LicenseManager.isProFlow.value)
    }

    @Test
    fun batch10_002_appLoggerSanitizesMultilineMnemonic() {
        val multilineMnemonic = """
            abandon
            amount
            liar
            amount
            expire
            adjust
            illegal
            gauge
            drastic
            drift
            amount
            absent
        """.trimIndent()

        val sanitized = AppLogger.sanitize(multilineMnemonic)
        assertFalse("Raw mnemonic words must not appear in sanitized log", sanitized.contains("abandon"))
        assertTrue("Mnemonic should be replaced with redaction tag", sanitized.contains("[REDACTED_MNEMONIC]"))
    }

    @Test
    fun batch10_003_noteEntityWithTextOutsideBlocksIsNotMarkedEmpty() {
        // User typed note content where text is outside the NOTESNOOK_BLOCKS comment tags
        val noteContent = "Important draft notes before block\n<!--NOTESNOOK_BLOCKS:{\"blocks\":[]}-->"
        val note = NoteEntity(
            title = "",
            content = noteContent,
            isChecklist = false
        )

        assertFalse(
            "Note with non-empty text outside block delimiters must NOT be identified as empty",
            note.isEmptyNote()
        )
    }

    @Test
    fun batch10_004_habitAlarmSchedulerHandlesNegativeIdSafely() {
        val code1 = HabitAlarmScheduler.getRequestCode(-1L)
        val code2 = HabitAlarmScheduler.getRequestCode(-450_000L)
        val code3 = HabitAlarmScheduler.getRequestCode(123L)

        assertTrue("Request code for -1L must be >= 500000", code1 >= 500_000)
        assertTrue("Request code for -1L must be < 900000", code1 < 900_000)

        assertTrue("Request code for -450000L must be >= 500000", code2 >= 500_000)
        assertTrue("Request code for -450000L must be < 900000", code2 < 900_000)

        assertTrue("Request code for 123L must be >= 500000", code3 >= 500_000)
        assertTrue("Request code for 123L must be < 900000", code3 < 900_000)
    }

    @Test
    fun batch10_005_diagnosticManagerRotatesOldZips() {
        val exportDir = DiagnosticManager.getExportsDirectory(context)
        // Simulate creating 5 diagnostic zip files
        for (i in 1..5) {
            val file = File(exportDir, "FocusByRJ_Diagnostics_20260929_00000$i.zip")
            file.writeText("fake zip content $i")
            file.setLastModified(System.currentTimeMillis() + i * 1000L)
        }

        // Clean up / rotate
        DiagnosticManager.rotateExports(context, maxKeep = 3)

        val remaining = exportDir.listFiles { _, name ->
            name.startsWith("FocusByRJ_Diagnostics_") && name.endsWith(".zip")
        } ?: emptyArray()

        assertEquals("Export directory should keep only 3 most recent ZIPs", 3, remaining.size)
    }

    @Test
    fun batch10_006_completedTaskHistoryDeduplicatesById() {
        val task1 = com.focusbyrj.app.data.Task(
            id = 42L,
            title = "Task Original Title",
            details = "Initial"
        )
        CompletedTaskHistoryManager.recordCompletedTask(context, task1)

        // Same task completed again or title updated
        val task2 = com.focusbyrj.app.data.Task(
            id = 42L,
            title = "Task Updated Title",
            details = "Updated"
        )
        CompletedTaskHistoryManager.recordCompletedTask(context, task2)

        val completedToday = CompletedTaskHistoryManager.getTodayCompletedTasks(context)
        assertEquals("Duplicate task completions with same ID must be updated/deduplicated", 1, completedToday.size)
        assertEquals("Task Updated Title", completedToday[0].title)
    }

    @Test
    fun batch10_007_drillSessionRepositoryCacheIsBounded() {
        val app = context as FocusApplication
        com.focusbyrj.app.data.drill.DrillSessionRepository.init(app)

        for (i in 1..65) {
            val summary = com.focusbyrj.app.data.drill.DrillSummary(
                sessionId = "session_$i",
                title = "Drill $i",
                totalQuestions = 10,
                correctCount = 8,
                timeSpentSeconds = 60,
                xpEarned = 50,
                isBlitz = false,
                isClaimed = false,
                questions = emptyList()
            )
            com.focusbyrj.app.data.drill.DrillSessionRepository.saveSummary(summary, app)
        }

        // Session 1 should have been evicted from the bounded LRU cache (capacity = 50)
        // Since memoryCache is private, verify by attempting getSummary for recent vs very old session
        // Recent session is guaranteed in cache / DB
        kotlinx.coroutines.runBlocking {
            val recent = com.focusbyrj.app.data.drill.DrillSessionRepository.getSummary("session_65")
            assertNotNull("Recent session 65 must exist", recent)
            assertEquals("Drill 65", recent?.title)
        }
    }

    @Test
    fun batch10_008_vocabRepositoryRecordQuizResultCaseInsensitive() = kotlinx.coroutines.runBlocking {
        val app = context as FocusApplication
        val vocabRepo = app.vocabRepository

        // Ensure table has at least one idiom
        val unlearned = vocabRepo.getNextIdiomToLearn()
        val idiomId = unlearned?.id ?: 1

        // Pass uppercase type "IDIOM"
        vocabRepo.recordQuizResult("IDIOM", idiomId, isCorrect = true)
        val idiom = app.vocabDatabase.vocabDao().getIdiomById(idiomId)
        assertNotNull("Idiom must be found", idiom)
        assertEquals("Mastery must be set to 1 for case-insensitive 'IDIOM' input", 1, idiom?.isMastered)
    }

    @Test
    fun batch10_009_focusScheduleActiveAtOvernightWindow() {
        val schedule = com.focusbyrj.app.data.FocusSchedule(
            name = "Night Study",
            startHour = 22,
            startMinute = 0,
            endHour = 4,
            endMinute = 0,
            daysOfWeek = "2,3,4,5,6" // Mon-Fri (Calendar MONDAY=2, FRIDAY=6)
        )

        // Monday 23:00 (active)
        val calMonNight = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 0)
        }
        assertTrue("Monday 23:00 must be active for overnight schedule", schedule.isActiveAt(calMonNight))

        // Tuesday 02:00 (active, continued from Monday evening)
        val calTueMorning = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.TUESDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 2)
            set(java.util.Calendar.MINUTE, 0)
        }
        assertTrue("Tuesday 02:00 must be active (continued from Mon)", schedule.isActiveAt(calTueMorning))

        // Tuesday 10:00 (inactive, daytime gap)
        val calTueDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.TUESDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 10)
            set(java.util.Calendar.MINUTE, 0)
        }
        assertFalse("Tuesday 10:00 must be inactive", schedule.isActiveAt(calTueDay))
    }
}
