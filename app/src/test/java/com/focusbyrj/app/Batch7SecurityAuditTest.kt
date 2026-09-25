package com.focusbyrj.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskRepository
import com.focusbyrj.app.ui.components.ChestRarity
import com.focusbyrj.app.util.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

/**
 * Batch 7 Security & Bug Audit Regression Test Suite
 *
 * Adversarial TDD tests covering:
 * - BATCH-7-001: ArithmeticEngine quadratic equations & bounds validation
 * - BATCH-7-002: AyvaTalkEngine task completion marks completed without deleting row or queuing cloud deletion tombstone
 * - BATCH-7-003: AyvaTalkEngine task reschedule updates monotonic updatedAt timestamp
 * - BATCH-7-004: SmartDateParser regex prevents matching words containing "am" (team, exam, stream) as morning context
 * - BATCH-7-005: SmartDateParser "today" default time does not produce immediate overdue timestamps in the afternoon
 * - BATCH-7-006: DailyQuestManager / DailyQuestsCard claiming early bird does not hijack or consume night owl chest
 * - BATCH-7-007: ArithmeticEngine fraction simplification guarantees clean integer division without truncation
 * - BATCH-7-008: FocusEconomyManager & AptitudeManager streak freeze economy synchronization
 */
@RunWith(RobolectricTestRunner::class)
class Batch7SecurityAuditTest {

    private lateinit var app: FocusApplication
    private lateinit var context: Context
    private lateinit var focusDb: FocusDatabase
    private lateinit var taskRepo: TaskRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<FocusApplication>()
        context = app.applicationContext
        focusDb = app.database
        taskRepo = app.taskRepository

        FocusEconomyManager.init(context)
        AptitudeManager.init(context)
        DailyQuestManager.init(context)

        // Reset shared preferences
        context.getSharedPreferences("supabase_sync_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("daily_learning_quests_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("aptitude_economy_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("focus_economy_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("focus_pending_deletions", Context.MODE_PRIVATE).edit().clear().commit()

        runBlocking {
            focusDb.taskDao().deleteAllTasks()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            focusDb.taskDao().deleteAllTasks()
        }
    }

    @Test
    fun testQuadraticEngineGeneratesConsistentRelationshipsAndNoCrashingBounds() {
        // BATCH-7-001: Generate quadratics 200 times and verify bounds never crash and correctIndex in 0..4
        for (i in 0 until 200) {
            val q = ArithmeticEngine.generateQuadratic(ArithmeticDifficulty.MEDIUM)
            assertTrue(q.options.isNotEmpty())
            assertTrue(q.correctIndex in 0..4)
            assertTrue(q.explanation.isNotBlank())
        }
    }

    @Test
    fun testArithmeticEngineFractionDivisionCleanInteger() {
        // BATCH-7-007: Test 100 iterations of Medium and Hard fraction simplification
        // to verify clean divisibility (no fractional remainder truncated into integer)
        for (i in 0 until 100) {
            val qMed = ArithmeticEngine.generateFractionBODMAS(ArithmeticDifficulty.MEDIUM)
            assertNotNull(qMed)
            assertTrue(qMed.options.size >= 4)
            // Answer string must be in explanation
            assertTrue("Explanation should contain final answer", qMed.explanation.contains("= ${qMed.options[qMed.correctIndex]}"))

            val qHard = ArithmeticEngine.generateFractionBODMAS(ArithmeticDifficulty.HARD)
            assertNotNull(qHard)
            assertTrue(qHard.options.size >= 4)
            assertTrue("Explanation should contain final answer", qHard.explanation.contains("= ${qHard.options[qHard.correctIndex]}"))
        }
    }

    @Test
    fun testAyvaTalkEngineCompleteTaskMarksCompletedWithoutDeletingRowOrQueuingTombstone() = runBlocking {
        // BATCH-7-002: Completing a task via Ayva Talk must NOT delete the row from DB,
        // and must NOT record a deletion tombstone in SupabaseSyncEngine.
        val taskId = focusDb.taskDao().insertTask(
            Task(
                title = "Buy groceries",
                isCompleted = false,
                dueDate = System.currentTimeMillis() + 3600000L
            )
        )

        val taskBefore = focusDb.taskDao().getTaskById(taskId)
        assertNotNull(taskBefore)
        assertFalse(taskBefore!!.isCompleted)

        // Send complete command to AyvaTalkEngine
        val response = AyvaTalkEngine.answerTalkQueryWithActions("complete Buy groceries", context)
        assertNotNull(response)

        // Verify task still exists in DB and is marked complete
        val taskAfter = focusDb.taskDao().getTaskById(taskId)
        assertNotNull("Task row must NOT be deleted from Room DB on completion!", taskAfter)
        assertTrue("Task must be marked completed!", taskAfter!!.isCompleted)
        assertNotNull("Task completedAt must be recorded!", taskAfter.completedAt)

        // Verify NO deletion tombstone was queued in focus_pending_deletions
        val deletionPrefs = context.getSharedPreferences("focus_pending_deletions", Context.MODE_PRIVATE)
        val pendingDeletions = deletionPrefs.getStringSet("pending_deletions", emptySet()) ?: emptySet()
        assertFalse(
            "Completing a task must NOT queue a deletion tombstone in SupabaseSyncEngine!",
            pendingDeletions.any { it.startsWith("TASK:") }
        )
    }

    @Test
    fun testAyvaTalkEngineCompleteAllTasksMarksCompletedWithoutDeletingRows() = runBlocking {
        // BATCH-7-002: Completing all tasks must mark them complete without deleting rows or queuing tombstones
        val id1 = focusDb.taskDao().insertTask(Task(title = "Task Alpha", isCompleted = false))
        val id2 = focusDb.taskDao().insertTask(Task(title = "Task Beta", isCompleted = false))

        val response = AyvaTalkEngine.answerTalkQueryWithActions("complete all", context)
        assertNotNull(response)

        val t1 = focusDb.taskDao().getTaskById(id1)
        val t2 = focusDb.taskDao().getTaskById(id2)
        assertNotNull("Task 1 must not be deleted", t1)
        assertNotNull("Task 2 must not be deleted", t2)
        assertTrue("Task 1 must be marked completed", t1!!.isCompleted)
        assertTrue("Task 2 must be marked completed", t2!!.isCompleted)

        val deletionPrefs = context.getSharedPreferences("focus_pending_deletions", Context.MODE_PRIVATE)
        val pendingDeletions = deletionPrefs.getStringSet("pending_deletions", emptySet()) ?: emptySet()
        assertTrue("No tombstones should be queued for completed tasks", pendingDeletions.none { it.startsWith("TASK:") })
    }

    @Test
    fun testAyvaTalkEngineRescheduleUpdatesTimestamp() = runBlocking {
        // BATCH-7-003: Rescheduling a task must update its updatedAt timestamp
        val initialUpdatedAt = 100000L
        val taskId = focusDb.taskDao().insertTask(
            Task(
                title = "Submit project proposal",
                dueDate = System.currentTimeMillis() + 3600000L,
                updatedAt = initialUpdatedAt
            )
        )

        val response = AyvaTalkEngine.answerTalkQueryWithActions("reschedule Submit project proposal to tomorrow at 5pm", context)
        assertNotNull(response)

        val updatedTask = focusDb.taskDao().getTaskById(taskId)
        assertNotNull(updatedTask)
        assertTrue("updatedAt must be updated on reschedule for LWW cloud sync!", updatedTask!!.updatedAt > initialUpdatedAt)
    }

    @Test
    fun testSmartDateParserTeamMeetingDoesNotTreatAmAsMorning() {
        // BATCH-7-004: Words like "team", "exam", "stream" contain "am" but must NOT trigger isMorningContext
        val resultTeam = SmartDateParser.parse("Team meeting at 5:30")
        assertNotNull(resultTeam.timestamp)
        val calTeam = Calendar.getInstance().apply { timeInMillis = resultTeam.timestamp!! }
        // Should parse to 17:30 (5:30 PM), NOT 05:30 (5:30 AM)
        assertEquals("5:30 with 'team' should be treated as 5:30 PM (17:30)", 17, calTeam.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calTeam.get(Calendar.MINUTE))

        val resultExam = SmartDateParser.parse("Exam at 4:30")
        assertNotNull(resultExam.timestamp)
        val calExam = Calendar.getInstance().apply { timeInMillis = resultExam.timestamp!! }
        assertEquals("4:30 with 'exam' should be treated as 4:30 PM (16:30)", 16, calExam.get(Calendar.HOUR_OF_DAY))

        val resultStream = SmartDateParser.parse("Live stream at 6:15")
        assertNotNull(resultStream.timestamp)
        val calStream = Calendar.getInstance().apply { timeInMillis = resultStream.timestamp!! }
        assertEquals("6:15 with 'stream' should be treated as 6:15 PM (18:15)", 18, calStream.get(Calendar.HOUR_OF_DAY))

        // Actual explicit "am" should still parse to morning
        val resultActualAm = SmartDateParser.parse("Breakfast at 5:30 am")
        assertNotNull(resultActualAm.timestamp)
        val calActualAm = Calendar.getInstance().apply { timeInMillis = resultActualAm.timestamp!! }
        assertEquals("5:30 am should be 5:30 AM", 5, calActualAm.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun testSmartDateParserTodayDoesNotCreateOverdueTask() {
        // BATCH-7-005: When user specifies "today" without a time, it must not set 8:00 AM if it is already past 8:00 AM
        val result = SmartDateParser.parse("Buy groceries today")
        assertNotNull(result.timestamp)
        val now = System.currentTimeMillis()
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val currentHour = nowCal.get(Calendar.HOUR_OF_DAY)

        if (currentHour >= 8) {
            // Task timestamp must not be in the past (overdue)
            assertTrue("Task created 'today' after 8 AM must have timestamp >= current time or end-of-day!", result.timestamp!! >= now || (result.timestamp!! >= now - 60000L))
        }
    }

    @Test
    fun testDailyQuestClaimEarlyBirdDoesNotStealNightOwlChest() {
        // BATCH-7-006: Opening/claiming Early Bird chest must NOT consume Night Owl chest
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val prefs = context.getSharedPreferences("daily_learning_quests_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("quest_date", today)
            .putString("quest_early_bird_earned_date", today)
            .putString("quest_early_bird_earned_date_night", today)
            .putBoolean("quest_morning_chest_claimed", false)
            .putBoolean("quest_evening_chest_claimed", false)
            .commit()

        DailyQuestManager.refreshState()
        val stateBefore = DailyQuestManager.stateFlow.value
        assertTrue(stateBefore.isEarlyBirdAvailable)
        assertTrue(stateBefore.isNightOwlAvailable)

        // Claim Early Bird chest with rarity COMMON
        DailyQuestManager.claimMysteryChest(ChestRarity.COMMON)

        DailyQuestManager.refreshState()
        val stateAfter = DailyQuestManager.stateFlow.value
        assertTrue("Morning chest should be marked claimed", stateAfter.morningChestClaimed)
        assertFalse("Night owl chest must NOT be hijacked or claimed!", stateAfter.eveningChestClaimed)
        assertTrue("Night owl chest should still be available!", stateAfter.isNightOwlAvailable)
    }

    @Test
    fun testFocusEconomyAndAptitudeStreakFreezeSync() {
        // BATCH-7-008: Buying a streak freeze in FocusEconomyManager must sync with AptitudeManager
        val ecoPrefs = context.getSharedPreferences("focus_economy_prefs", Context.MODE_PRIVATE)
        val aptPrefs = context.getSharedPreferences("aptitude_economy_prefs", Context.MODE_PRIVATE)

        ecoPrefs.edit().putInt("gold", 500).putInt("streak_freezes", 0).commit()
        aptPrefs.edit().putInt("streak_freezes_count", 0).commit()

        FocusEconomyManager.init(context)
        AptitudeManager.init(context)

        val aptFreezesBefore = AptitudeManager.getStreakFreezesCount()
        assertEquals(0, aptFreezesBefore)

        val bought = FocusEconomyManager.buyStreakFreeze(150)
        assertTrue("Streak freeze purchase should succeed", bought)

        // Verify FocusEconomyManager has 1 freeze
        assertEquals(1, FocusEconomyManager.profileFlow.value.streakFreezes)

        // Verify AptitudeManager received the synced freeze
        AptitudeManager.refreshProfile()
        val aptFreezesAfter = AptitudeManager.getStreakFreezesCount()
        assertEquals("AptitudeManager streak freezes must increment when bought in FocusEconomyManager", 1, aptFreezesAfter)
    }

    @Test
    fun testAyvaTalkNotesQueriesAreCompletelyRemovedAndDoNotTouchRoomOrLeak() = runBlocking {
        // BATCH-7-009: Ayva Chat must NEVER query, read, create, search, or leak notes from Room DB
        val noteDb = com.focusbyrj.app.data.note.NoteDatabase.getInstance(context)
        val noteDao = noteDb.noteDao()
        noteDao.insertNote(
            com.focusbyrj.app.data.note.NoteEntity(
                title = "Private Financial Credentials",
                content = "PIN code is 987654. Bank routing 021000021.",
                isPinned = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        // 1. Querying notes list
        val listResp = AyvaTalkEngine.answerTalkQueryWithActions("notes", context)
        assertNotNull(listResp)
        assertTrue(
            "Response should redirect to Keep Notes for privacy",
            listResp.formattedText.contains("Keep Notes") || listResp.formattedText.contains("privacy")
        )
        assertFalse(
            "Ayva must NEVER print private note title or snippet in chat transcript!",
            listResp.formattedText.contains("Private Financial Credentials") || listResp.formattedText.contains("987654")
        )
        assertTrue(
            "Ayva should provide action navigation to notes screen",
            listResp.actions.any { it is TalkAction.NavigateAppScreen && it.route == "notes" }
        )

        // 2. Searching notes
        val searchResp = AyvaTalkEngine.answerTalkQueryWithActions("search notes Financial", context)
        assertNotNull(searchResp)
        assertFalse(
            "Ayva must NEVER search or leak note contents via search in chat!",
            searchResp.formattedText.contains("987654") || searchResp.formattedText.contains("routing")
        )
        assertTrue(
            "Ayva should provide action navigation to notes screen",
            searchResp.actions.any { it is TalkAction.NavigateAppScreen && it.route == "notes" }
        )

        // 3. Creating note via chat must be rejected/redirected, not inserted into Room
        val countBefore = noteDao.getAllActiveNotesSync().size
        val createResp = AyvaTalkEngine.answerTalkQueryWithActions("create note Chat Note: do not create this in Room", context)
        assertNotNull(createResp)
        assertTrue(
            "Response should inform user notes cannot be created in chat window",
            createResp.formattedText.contains("Keep Notes") || createResp.formattedText.contains("privacy")
        )
        val countAfter = noteDao.getAllActiveNotesSync().size
        assertEquals("Chat query must NOT insert notes into Room database!", countBefore, countAfter)
    }

    @Test
    fun testAyvaTalkDeleteAllRequiresConfirmationAndMovesToTrashWithPreOpSnapshot() = runBlocking {
        // BATCH-7-010: Bulk delete via Ayva Talk requires 2-step confirmation, moves to trash, and creates pre-op snapshot
        val id1 = focusDb.taskDao().insertTask(Task(title = "Task Alpha", isCompleted = false))
        val id2 = focusDb.taskDao().insertTask(Task(title = "Task Beta", isCompleted = false))

        // First pass: "delete all tasks" without confirmation
        val unconfirmedResp = AyvaTalkEngine.answerTalkQueryWithActions("delete all tasks", context)
        assertNotNull(unconfirmedResp)
        assertTrue(
            "Bulk delete must prompt for confirmation first!",
            unconfirmedResp.formattedText.contains("Confirm") || unconfirmedResp.formattedText.contains("Are you sure")
        )
        assertTrue(
            "Confirmation prompt must offer confirm action chip",
            unconfirmedResp.actions.any { (it as? TalkAction.AskQuery)?.query?.contains("confirm_delete_all") == true }
        )

        // Tasks must NOT be deleted yet!
        val t1Before = focusDb.taskDao().getTaskById(id1)
        val t2Before = focusDb.taskDao().getTaskById(id2)
        assertNotNull("Task 1 must not be deleted before confirmation", t1Before)
        assertNotNull("Task 2 must not be deleted before confirmation", t2Before)
        assertFalse("Task 1 must not be trashed before confirmation", t1Before!!.isTrashed)
        assertFalse("Task 2 must not be trashed before confirmation", t2Before!!.isTrashed)

        // Second pass: confirmed "/talk confirm_delete_all"
        val confirmedResp = AyvaTalkEngine.answerTalkQueryWithActions("/talk confirm_delete_all", context)
        assertNotNull(confirmedResp)
        assertTrue(
            "Response should indicate tasks were moved to Trash",
            confirmedResp.formattedText.contains("Trash") || confirmedResp.formattedText.contains("trash")
        )

        // Tasks must be SOFT-DELETED (in trash), NOT hard-deleted from SQLite!
        val t1After = focusDb.taskDao().getTaskById(id1)
        val t2After = focusDb.taskDao().getTaskById(id2)
        assertNotNull("Task 1 must still exist in SQLite table", t1After)
        assertNotNull("Task 2 must still exist in SQLite table", t2After)
        assertTrue("Task 1 must be moved to Trash (isTrashed = true)!", t1After!!.isTrashed)
        assertTrue("Task 2 must be moved to Trash (isTrashed = true)!", t2After!!.isTrashed)

        // Pre-op backup snapshot must have been created in internal storage
        val preOpDir = java.io.File(context.filesDir, "auto_backups/pre_op")
        assertTrue("Pre-op directory must exist", preOpDir.exists())
        val snapshots = preOpDir.listFiles { file -> file.name.contains("ayva_talk_delete_all") }
        assertTrue("A pre-op snapshot must be recorded for bulk task deletion!", snapshots != null && snapshots.isNotEmpty())
    }

    @Test
    fun testAyvaTalkSingleTaskDeleteMovesToTrashInsteadOfHardDelete() = runBlocking {
        // BATCH-7-011: Deleting a single task in Ayva Talk moves it to trash rather than permanent deletion
        val taskId = focusDb.taskDao().insertTask(
            Task(
                title = "File annual tax declaration",
                isCompleted = false,
                dueDate = System.currentTimeMillis() + 86400000L
            )
        )

        val resp = AyvaTalkEngine.answerTalkQueryWithActions("delete File annual tax declaration", context)
        assertNotNull(resp)
        assertTrue(
            "Response should confirm moving task to trash",
            resp.formattedText.contains("Trash") || resp.formattedText.contains("trash")
        )

        val taskAfter = focusDb.taskDao().getTaskById(taskId)
        assertNotNull("Task must NOT be hard-deleted from SQLite!", taskAfter)
        assertTrue("Task must have isTrashed = true!", taskAfter!!.isTrashed)

        val trashedTasks = focusDb.taskDao().getTrashedTasksSync()
        assertTrue("Task must be present in trashed tasks list", trashedTasks.any { it.id == taskId })
        assertNotNull("Task must have trashedAt timestamp set", taskAfter.trashedAt)
    }

    @Test
    fun testAyvaTalkCleanOverdueRequiresConfirmationAndMovesToTrashWithPreOpSnapshot() = runBlocking {
        // BATCH-7-012: Cleaning overdue tasks requires confirmation and soft-deletes with snapshot
        val now = System.currentTimeMillis()
        val overdueId = focusDb.taskDao().insertTask(
            Task(
                title = "Overdue task to clean",
                isCompleted = false,
                dueDate = now - 7200000L // 2 hours ago
            )
        )

        // First pass: unconfirmed
        val unconfirmedResp = AyvaTalkEngine.answerTalkQueryWithActions("clean overdue tasks", context)
        assertNotNull(unconfirmedResp)
        assertTrue(
            "Overdue clean must prompt for confirmation first",
            unconfirmedResp.formattedText.contains("Confirm") || unconfirmedResp.formattedText.contains("Are you sure")
        )

        val taskBefore = focusDb.taskDao().getTaskById(overdueId)
        assertNotNull(taskBefore)
        assertFalse("Task must not be trashed before confirmation", taskBefore!!.isTrashed)

        // Second pass: confirmed
        val confirmedResp = AyvaTalkEngine.answerTalkQueryWithActions("/talk confirm_delete_overdue", context)
        assertNotNull(confirmedResp)

        val taskAfter = focusDb.taskDao().getTaskById(overdueId)
        assertNotNull("Task must still exist in Room table", taskAfter)
        assertTrue("Task must be moved to Trash (isTrashed = true)!", taskAfter!!.isTrashed)

        val preOpDir = java.io.File(context.filesDir, "auto_backups/pre_op")
        val snapshots = preOpDir.listFiles { file -> file.name.contains("ayva_talk_delete_overdue") }
        assertTrue("A pre-op snapshot must be recorded for overdue task deletion!", snapshots != null && snapshots.isNotEmpty())
    }

    @Test
    fun testStreakFreezeBidirectionalSyncAndConsumption() {
        // BATCH-7-013: Bidirectional streak freeze sync between AptitudeManager and FocusEconomyManager
        val ecoPrefs = context.getSharedPreferences("focus_economy_prefs", Context.MODE_PRIVATE)
        val aptPrefs = context.getSharedPreferences("aptitude_economy_prefs", Context.MODE_PRIVATE)
        ecoPrefs.edit().putInt("streak_freezes", 0).commit()
        aptPrefs.edit().putInt("streak_freezes_count", 0).commit()

        FocusEconomyManager.init(context)
        AptitudeManager.init(context)

        // 1. Awarding streak freeze via AptitudeManager (e.g. from Mystery Chest) must sync to FocusEconomyManager
        AptitudeManager.addStreakFreezes(2)
        assertEquals(2, AptitudeManager.getStreakFreezesCount())
        assertEquals("FocusEconomyManager must receive synced freeze count", 2, FocusEconomyManager.profileFlow.value.streakFreezes)

        // 2. Both must cap at 3 shields
        AptitudeManager.addStreakFreezes(5)
        assertEquals(3, AptitudeManager.getStreakFreezesCount())
        assertEquals(3, FocusEconomyManager.profileFlow.value.streakFreezes)

        // 3. Consuming freeze in AptitudeManager must decrement FocusEconomyManager
        AptitudeManager.consumeStreakFreeze()
        assertEquals(2, AptitudeManager.getStreakFreezesCount())
        assertEquals("Consuming freeze in AptitudeManager must decrement FocusEconomyManager", 2, FocusEconomyManager.profileFlow.value.streakFreezes)

        // 4. Consuming freeze in FocusEconomyManager must decrement AptitudeManager
        val used = FocusEconomyManager.useStreakFreeze()
        assertTrue(used)
        assertEquals(1, FocusEconomyManager.profileFlow.value.streakFreezes)
        assertEquals("Consuming freeze in FocusEconomyManager must decrement AptitudeManager", 1, AptitudeManager.getStreakFreezesCount())
    }

    @Test
    fun testAptitudeManagerDaysBetweenDstSafe() {
        // BATCH-7-014: getDaysBetween must be immune to daylight saving time 23h/25h transitions
        val days1 = AptitudeManager.getDaysBetweenDates("2026-03-29", "2026-03-30")
        assertEquals("Consecutive dates must always be exactly 1 day apart", 1, days1)

        val days2 = AptitudeManager.getDaysBetweenDates("2026-03-28", "2026-03-30")
        assertEquals("Dates 2 days apart must always be exactly 2 days apart", 2, days2)
    }

    @Test
    fun testAyvaTalkStopRoutineRefusesWhenStrictActive() = runBlocking {
        // BATCH-7-016: Stopping a routine via Ayva Talk must be rejected when strict mode or active schedule is in progress
        val scheduleDao = focusDb.scheduleDao()
        val nowCal = Calendar.getInstance()
        val curH = nowCal.get(Calendar.HOUR_OF_DAY)

        val scheduleId = 101
        scheduleDao.insertSchedule(
            com.focusbyrj.app.data.FocusSchedule(
                id = scheduleId,
                name = "Deep Work Protocol",
                startHour = (curH - 1 + 24) % 24,
                startMinute = 0,
                endHour = (curH + 1) % 24,
                endMinute = 59,
                daysOfWeek = "1,2,3,4,5,6,7",
                isEnabled = true,
                mode = "HARD",
                appsToBlock = "com.instagram.android"
            )
        )

        // Turn on strict mode in focus_prefs
        val focusPrefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        focusPrefs.edit().putString("block_mode", "HARD").commit()

        val resp = AyvaTalkEngine.answerTalkQueryWithActions("stop routine Deep Work Protocol", context)
        assertNotNull(resp)
        assertTrue(
            "Stopping a strict active routine must be refused!",
            resp.formattedText.contains("Strict") || resp.formattedText.contains("cannot be stopped") || resp.formattedText.contains("Focus Mode Active") || resp.formattedText.contains("in progress")
        )

        val scheduleAfter = scheduleDao.getScheduleById(scheduleId)
        assertNotNull(scheduleAfter)
        assertTrue("Schedule must remain enabled when stopping is refused under strict mode!", scheduleAfter!!.isEnabled)
    }

    @Test
    fun testArithmeticEngineRemainderOptionsValid() {
        // BATCH-7-017: Remainder options must always be valid remainders (< divisor)
        for (i in 0 until 50) {
            val q = ArithmeticEngine.generateQuestion(ArithmeticDifficulty.HARD)
            if (q.title.contains("Remainder", ignoreCase = true)) {
                val modMatch = Regex("divided by (\\d+)").find(q.questionText)
                if (modMatch != null) {
                    val divisor = modMatch.groupValues[1].toInt()
                    assertEquals("Questions must have exactly 5 options", 5, q.options.size)
                    for (opt in q.options) {
                        val optVal = opt.toIntOrNull()
                        assertNotNull("Option should be integer remainder: $opt", optVal)
                        assertTrue(
                            "Option $optVal must be in 0 until $divisor for question: ${q.questionText}",
                            optVal!! in 0 until divisor
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testSmartDateParserPrefixStripping() {
        // BATCH-7-018: SmartDateParser must cleanly strip natural command prefixes
        val r1 = SmartDateParser.parse("Schedule a reminder to buy groceries tomorrow at 5pm")
        assertEquals("buy groceries", r1.cleanText)
        assertTrue(r1.hasTime)

        val r2 = SmartDateParser.parse("add a task call dentist tomorrow")
        assertEquals("call dentist", r2.cleanText)

        val r3 = SmartDateParser.parse("I need to submit quarterly report on Friday")
        assertEquals("submit quarterly report", r3.cleanText)
    }
}
