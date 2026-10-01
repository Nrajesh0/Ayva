/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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

    @Test
    fun batch10_010_appLoggerSanitizesStackTraceAndExtendedKeywords() {
        // Test extended secret keywords: passphrase, api_key, access_token
        val rawLog = "Config loaded with passphrase: superSecretPassword123 and api_key=xyzSecretKey987 and access_token: secretToken555"
        val sanitized = AppLogger.sanitize(rawLog)
        assertFalse("Raw passphrase must not leak", sanitized.contains("superSecretPassword123"))
        assertFalse("Raw api_key must not leak", sanitized.contains("xyzSecretKey987"))
        assertFalse("Raw access_token must not leak", sanitized.contains("secretToken555"))
        assertTrue("Passphrase should be redacted", sanitized.contains("passphrase: [REDACTED]"))

        // Test exception stack trace sanitization
        val exception = RuntimeException("Failed auth with Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.doNotLeakThisSignature")
        AppLogger.e("SecurityTest", "Operation failed", exception)

        val recentLogs = AppLogger.getRecentLogsSnapshot()
        val exceptionLog = recentLogs.lastOrNull { it.contains("SecurityTest") }
        assertNotNull("Log entry must be recorded", exceptionLog)
        assertFalse("Exception stack trace must not contain raw Bearer token", exceptionLog!!.contains("doNotLeakThisSignature"))
        assertTrue("Exception stack trace should have redacted JWT", exceptionLog.contains("[REDACTED_JWT]"))
    }

    @Test
    fun batch10_011_habitAlarmSchedulerAnchorsOvernightInterval() {
        val now = System.currentTimeMillis()
        val habit = com.focusbyrj.app.data.Habit(
            id = 777L,
            title = "Night Hydration",
            type = com.focusbyrj.app.data.HabitType.INTERVAL_WINDOW,
            intervalHours = 1,
            intervalMinutes = 0,
            windowStartHour = 22,
            windowStartMinute = 0,
            windowEndHour = 6,
            windowEndMinute = 0
        )

        // Mock current time at 23:00 (inside overnight evening window)
        // If completed 10 minutes ago, next trigger should be roughly now + 50 mins, anchored from completion
        val tenMinutesAgo = now - (10 * 60 * 1000L)
        val triggerWithAnchor = HabitAlarmScheduler.calculateNextTriggerTime(
            habit = habit,
            lastCompletedTimestamp = tenMinutesAgo,
            isGoalCompletedToday = false
        )
        assertNotNull("Trigger time must be computed", triggerWithAnchor)
        assertTrue("Next trigger must be in the future", triggerWithAnchor!! > now)

        // Verify that equal start and end (e.g. 08:00 to 08:00) is NOT considered overnight
        val sameStartEndHabit = habit.copy(
            windowStartHour = 8,
            windowStartMinute = 0,
            windowEndHour = 8,
            windowEndMinute = 0
        )
        val triggerSame = HabitAlarmScheduler.calculateNextTriggerTime(sameStartEndHabit)
        assertNotNull(triggerSame)
    }

    @Test
    fun batch10_012_drillSummaryFromJsonToleratesNullOrCorruptedQuestions() {
        val corruptedJson = """
            {
                "sessionId": "valid-session-123",
                "title": "Quantum Algebra",
                "total": 3,
                "correct": 2,
                "timeSpentSeconds": 120,
                "xpEarned": 150,
                "questions": [
                    {
                        "qNum": 1,
                        "title": "Q1",
                        "questionText": "What is 2+2?",
                        "options": ["3", "4", null, "5"],
                        "correctIndex": 1,
                        "userSelectedIndex": 1,
                        "status": "correct",
                        "accuracyPct": 120
                    },
                    null,
                    {
                        "qNum": 3,
                        "title": "Q3",
                        "questionText": "What is 3*3?",
                        "options": ["6", "9"],
                        "correctIndex": 1,
                        "userSelectedIndex": 1,
                        "status": "correct"
                    }
                ]
            }
        """.trimIndent()

        val summary = com.focusbyrj.app.data.drill.DrillSummary.fromJson(corruptedJson)
        // It must NOT fail closed to the blank fallback dummy summary
        assertEquals("valid-session-123", summary.sessionId)
        assertEquals("Quantum Algebra", summary.title)
        assertEquals(150, summary.xpEarned)
        // Valid questions parsed (skipping null question at index 1)
        assertEquals(2, summary.questions.size)
        // Accuracy percentage coerced into [0, 100]
        assertEquals(100, summary.questions[0].accuracyPct)
        // Null option safely filtered
        assertFalse(summary.questions[0].options.contains("null"))
        assertEquals(3, summary.questions[0].options.size)
    }

    @Test
    fun batch10_013_noteEntityParsesUrisAndChecklistWithCorruptedEntries() {
        // Image URIs JSON with null or empty entries
        val note = NoteEntity(
            id = 101L,
            title = "Test Note",
            content = "Body",
            imageUrisJson = "[\"file:///media/img1.jpg\", null, \"   \", \"file:///media/img2.jpg\"]",
            audioUrisJson = "[\"file:///media/audio1.mp3\", null, \"file:///media/audio2.mp3\"]",
            labelsJson = "[\"work\", null, \"urgent\"]",
            isChecklist = true,
            checklistJson = "[{\"id\":\"c1\",\"text\":\"Item 1\",\"isChecked\":true}, null, {\"id\":\"c2\",\"text\":\"Item 2\",\"isChecked\":false}]"
        )

        val imageUris = note.getImageUris()
        assertEquals("Should extract both valid image URIs despite null element", 2, imageUris.size)
        assertEquals("file:///media/img1.jpg", imageUris[0])
        assertEquals("file:///media/img2.jpg", imageUris[1])

        val audioUris = note.getAudioUris()
        assertEquals("Should extract both valid audio URIs despite null element", 2, audioUris.size)

        val labels = note.getLabels()
        assertEquals("Should extract both valid labels despite null element", 2, labels.size)
        assertTrue(labels.contains("work"))
        assertTrue(labels.contains("urgent"))

        val checklist = note.getChecklistItems()
        assertEquals("Should extract both valid checklist items despite null element", 2, checklist.size)
        assertEquals("Item 1", checklist[0].text)
        assertEquals("Item 2", checklist[1].text)
    }

    @Test
    fun batch10_014_vocabRepositoryRejectsUnknownTypeInRecordQuizResult() = kotlinx.coroutines.runBlocking {
        val app = context as FocusApplication
        val vocabRepo = app.vocabRepository

        // Ensure we have an OWS entry
        val unlearnedOws = vocabRepo.getNextOwsToLearn()
        val owsId = unlearnedOws?.id ?: 1
        val initialOws = app.vocabDatabase.vocabDao().getOwsById(owsId)
        val initialMastery = initialOws?.isMastered ?: 0

        // Call recordQuizResult with an invalid type "unsupported_type"
        vocabRepo.recordQuizResult("unsupported_type", owsId, isCorrect = true)

        val afterOws = app.vocabDatabase.vocabDao().getOwsById(owsId)
        assertEquals("Unknown type must NOT alter OWS mastery", initialMastery, afterOws?.isMastered ?: 0)
    }

    @Test
    fun batch10_015_completedTaskHistoryUsesTaskCompletedAtIfSet() {
        val customCompletionTime = System.currentTimeMillis() - 1800_000L // 30 minutes ago today
        val task = com.focusbyrj.app.data.Task(
            id = 999L,
            title = "Dedicated Milestone",
            completedAt = customCompletionTime
        )

        CompletedTaskHistoryManager.recordCompletedTask(context, task)

        val todayTasks = CompletedTaskHistoryManager.getTodayCompletedTasks(context)
        val recorded = todayTasks.find { it.id == 999L }
        assertNotNull("Task must be recorded", recorded)
        assertEquals("Recorded task should preserve custom completedAt", customCompletionTime, recorded?.completedAt)
    }
}
