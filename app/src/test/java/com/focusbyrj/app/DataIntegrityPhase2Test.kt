package com.focusbyrj.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.AppRestriction
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.FocusSchedule
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitType
import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskType
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.backup.DataSafetyManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DataIntegrityPhase2Test {

    private lateinit var context: Context
    private lateinit var focusDb: FocusDatabase
    private lateinit var noteDb: NoteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        focusDb = Room.inMemoryDatabaseBuilder(context, FocusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        noteDb = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        focusDb.close()
        noteDb.close()
    }

    @Test
    fun testTaskSoftDeleteAndRestoreLifecycle() {
        runBlocking {
        val taskDao = focusDb.taskDao()

        // 1. Insert an active task
        val task = Task(
            title = "Finalize quarterly budget",
            details = "Review departmental spending",
            dueDate = System.currentTimeMillis() + 86400000L,
            isCompleted = false,
            type = TaskType.TASK,
            recurrence = RecurrencePattern.NONE,
            isPriority = true
        )
        val taskId = taskDao.insertTask(task)
        assertTrue("Task inserted with valid ID", taskId > 0)

        // Verify task appears in active list and not in trash
        val initialActive = taskDao.getAllActiveTasksList()
        assertEquals(1, initialActive.size)
        assertEquals("Finalize quarterly budget", initialActive[0].title)
        assertFalse(initialActive[0].isTrashed)
        assertNull(initialActive[0].trashedAt)

        val initialTrash = taskDao.getTrashedTasksSync()
        assertTrue("Trash should initially be empty", initialTrash.isEmpty())

        // 2. Soft-delete (move to trash)
        val trashTimestamp = System.currentTimeMillis()
        taskDao.softDeleteTask(taskId, trashTimestamp)

        // Active query should now be completely filtered
        val activeAfterTrash = taskDao.getAllActiveTasksList()
        assertTrue("Active tasks query must exclude trashed tasks", activeAfterTrash.isEmpty())

        // Trashed query must return the item with metadata intact
        val trashAfterDelete = taskDao.getTrashedTasksSync()
        assertEquals(1, trashAfterDelete.size)
        assertEquals(taskId, trashAfterDelete[0].id)
        assertTrue("isTrashed must be true", trashAfterDelete[0].isTrashed)
        assertEquals(trashTimestamp, trashAfterDelete[0].trashedAt)

        // 3. Restore from trash
        val restoreTimestamp = System.currentTimeMillis()
        taskDao.updateTrashStatus(taskId, isTrashed = false, updatedAt = restoreTimestamp)

        val activeAfterRestore = taskDao.getAllActiveTasksList()
        assertEquals("Restored task must re-appear in active list", 1, activeAfterRestore.size)
        assertFalse(activeAfterRestore[0].isTrashed)
        assertNull("trashedAt must be cleared on restore", activeAfterRestore[0].trashedAt)

        val trashAfterRestore = taskDao.getTrashedTasksSync()
        assertTrue("Trash must be empty after restore", trashAfterRestore.isEmpty())
    }
}

    @Test
    fun test30DayAutomatedTrashPurging() {
        runBlocking {
        val noteDao = noteDb.noteDao()
        val taskDao = focusDb.taskDao()

        val now = System.currentTimeMillis()
        val thirtyFiveDaysAgo = now - (35L * 24L * 60L * 60L * 1000L)
        val fiveDaysAgo = now - (5L * 24L * 60L * 60L * 1000L)

        // 1. Expired Note (> 30 days old in trash)
        val expiredNote = NoteEntity(
            title = "Old scratchpad",
            content = "Obsolete notes",
            isTrashed = true,
            trashedAt = thirtyFiveDaysAgo
        )
        noteDao.insertNote(expiredNote)

        // 2. Fresh Note in trash (< 30 days old)
        val freshTrashedNote = NoteEntity(
            title = "Recent deleted note",
            content = "Keep safe in trash",
            isTrashed = true,
            trashedAt = fiveDaysAgo
        )
        val freshNoteId = noteDao.insertNote(freshTrashedNote)

        // 3. Active Note (not trashed)
        val activeNote = NoteEntity(
            title = "Active Note",
            content = "Important current content",
            isTrashed = false
        )
        val activeNoteId = noteDao.insertNote(activeNote)

        // 4. Expired Task (> 30 days old in trash)
        val expiredTask = Task(
            title = "Expired trash task",
            isTrashed = true,
            trashedAt = thirtyFiveDaysAgo
        )
        taskDao.insertTask(expiredTask)

        // 5. Fresh Task in trash (< 30 days old)
        val freshTrashedTask = Task(
            title = "Recent deleted task",
            isTrashed = true,
            trashedAt = fiveDaysAgo
        )
        val freshTaskId = taskDao.insertTask(freshTrashedTask)

        // 6. Active Task (not trashed)
        val activeTask = Task(
            title = "Active Task",
            isTrashed = false
        )
        val activeTaskId = taskDao.insertTask(activeTask)

        // Execute purge with 30-day retention
        val totalPurged = DataSafetyManager.purgeExpiredTrash(
            context = context,
            noteDao = noteDao,
            taskDao = taskDao,
            retentionDays = 30,
            focusDb = focusDb
        )

        assertEquals("Exactly 2 items (1 note + 1 task) should be purged", 2, totalPurged)

        // Verify active items survived
        assertNotNull(noteDao.getNoteById(activeNoteId))
        assertNotNull(taskDao.getTaskById(activeTaskId))

        // Verify fresh trashed items survived (< 30 days)
        assertNotNull(noteDao.getNoteById(freshNoteId))
        assertNotNull(taskDao.getTaskById(freshTaskId))

        // Verify expired items were permanently removed
        val remainingTrashNotes = noteDao.getTrashedNotesSync()
        assertEquals(1, remainingTrashNotes.size)
        assertEquals(freshNoteId, remainingTrashNotes[0].id)

        val remainingTrashTasks = taskDao.getTrashedTasksSync()
        assertEquals(1, remainingTrashTasks.size)
        assertEquals(freshTaskId, remainingTrashTasks[0].id)
    }
}

    @Test
    fun testMultiTableSnapshotCreationAndRestoration() {
        runBlocking {
        val noteDao = noteDb.noteDao()
        val taskDao = focusDb.taskDao()
        val habitDao = focusDb.habitDao()
        val scheduleDao = focusDb.scheduleDao()
        val restrictionDao = focusDb.appRestrictionDao()

        // Seed comprehensive data across all 5 tables
        val note = NoteEntity(
            title = "Architectural Blueprint",
            content = "Multi-table data safety specification",
            colorKey = "amber",
            isPinned = true
        )
        noteDao.insertNote(note)

        val task = Task(
            title = "Audit encryption headers",
            details = "Verify Argon2id and AES-256-GCM parameters",
            isPriority = true,
            isTrashed = true,
            trashedAt = System.currentTimeMillis() - 10000L
        )
        taskDao.insertTask(task)

        val habit = Habit(
            title = "Deep Focus Routine",
            description = "Uninterrupted 90m block",
            iconEmoji = "🧠",
            type = HabitType.ONCE_DAILY
        )
        habitDao.insertHabit(habit)

        val schedule = FocusSchedule(
            name = "Morning Focus Session",
            startHour = 9,
            startMinute = 0,
            endHour = 12,
            endMinute = 0,
            daysOfWeek = "1,2,3,4,5"
        )
        scheduleDao.insertSchedule(schedule)

        val restriction = AppRestriction(
            packageName = "com.distracting.social",
            appName = "Social Feed",
            isRestricted = true,
            mode = "HARD"
        )
        restrictionDao.insertRestriction(restriction)

        // 1. Write pre-op snapshot
        val snapshotPath = DataSafetyManager.writePreOpSnapshot(
            context = context,
            noteDao = noteDao,
            operationTag = "unit_test_full_backup",
            focusDb = focusDb
        )
        assertNotNull("Snapshot path must not be null", snapshotPath)
        val snapshotFile = File(snapshotPath!!)
        assertTrue("Snapshot file must exist on disk", snapshotFile.exists())

        // 2. Validate snapshot JSON contents
        val jsonStr = DataSafetyManager.readAtomically(snapshotFile)
        val json = JSONObject(jsonStr)
        assertEquals(2, json.getInt("snapshotVersion"))
        assertEquals(1, json.getInt("noteCount"))
        assertEquals(1, json.getInt("taskCount"))
        assertEquals(1, json.getInt("habitCount"))
        assertEquals(1, json.getInt("scheduleCount"))
        assertEquals(1, json.getInt("restrictionCount"))

        // Verify task in snapshot captured trash attributes
        val tasksArr = json.getJSONArray("tasks")
        val taskObj = tasksArr.getJSONObject(0)
        assertEquals("Audit encryption headers", taskObj.getString("title"))
        assertTrue("Task in snapshot must retain isTrashed", taskObj.getBoolean("isTrashed"))
        assertTrue("Task in snapshot must retain trashedAt", taskObj.has("trashedAt"))

        // 3. Clear all tables to simulate catastrophic failure
        noteDb.clearAllTables()
        focusDb.clearAllTables()

        assertEquals(0, noteDao.getAllNotesList().size)
        assertEquals(0, taskDao.getAllTasksList().size)
        assertEquals(0, habitDao.getAllHabitsSync().size)
        assertEquals(0, scheduleDao.getAllSchedulesSync().size)
        assertEquals(0, restrictionDao.getAllRestrictionsSync().size)

        // 4. Restore everything from snapshot
        val restoreResult = DataSafetyManager.restoreSnapshot(
            context = context,
            snapshotPath = snapshotPath,
            noteDao = noteDao,
            focusDb = focusDb
        )

        assertTrue("Restore must succeed", restoreResult.isSuccess)
        assertEquals("Total 5 items restored across all tables", 5, restoreResult.getOrNull())

        // Verify restored content in both databases
        val restoredNotes = noteDao.getAllNotesList()
        assertEquals(1, restoredNotes.size)
        assertEquals("Architectural Blueprint", restoredNotes[0].title)

        val restoredTasks = taskDao.getAllTasksList()
        assertEquals(1, restoredTasks.size)
        assertEquals("Audit encryption headers", restoredTasks[0].title)
        assertTrue("Restored task must preserve isTrashed = true", restoredTasks[0].isTrashed)
        assertNotNull("Restored task must preserve trashedAt", restoredTasks[0].trashedAt)

        val restoredHabits = habitDao.getAllHabitsSync()
        assertEquals(1, restoredHabits.size)
        assertEquals("Deep Focus Routine", restoredHabits[0].title)

        val restoredSchedules = scheduleDao.getAllSchedulesSync()
        assertEquals(1, restoredSchedules.size)
        assertEquals("Morning Focus Session", restoredSchedules[0].name)

        val restoredRestrictions = restrictionDao.getAllRestrictionsSync()
        assertEquals(1, restoredRestrictions.size)
        assertEquals("com.distracting.social", restoredRestrictions[0].packageName)

        // Cleanup
        snapshotFile.delete()
    }
}

    @Test
    fun testTaskFingerprintIncludesTrashState() {
        val activeTask = Task(
            id = 101L,
            title = "Pay internet bill",
            details = "Before 5 PM",
            isCompleted = false,
            isTrashed = false,
            trashedAt = null
        )

        val activeFingerprint = "${activeTask.title}|${activeTask.details}|${activeTask.dueDate}|${activeTask.isCompleted}|${activeTask.type}|${activeTask.recurrence}|${activeTask.isPersistent}|${activeTask.isPriority}|${activeTask.isTrashed}|${activeTask.trashedAt}"

        val trashedTask = activeTask.copy(
            isTrashed = true,
            trashedAt = 1700000000000L
        )

        val trashedFingerprint = "${trashedTask.title}|${trashedTask.details}|${trashedTask.dueDate}|${trashedTask.isCompleted}|${trashedTask.type}|${trashedTask.recurrence}|${trashedTask.isPersistent}|${trashedTask.isPriority}|${trashedTask.isTrashed}|${trashedTask.trashedAt}"

        assertFalse(
            "Task fingerprint must change when task is moved to trash or restored to trigger cloud sync",
            activeFingerprint == trashedFingerprint
        )
        assertTrue(trashedFingerprint.contains("true|1700000000000"))
    }
}
