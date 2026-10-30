package com.focusbyrj.app.util

import android.app.Application
import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.util.assistant.*
import com.focusbyrj.app.util.backup.DataSafetyManager
import com.focusbyrj.app.util.sync.VaultSyncManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CanonicalTaskPlanningTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    private lateinit var previousZone: TimeZone
    private val now get() = System.currentTimeMillis()
    @Before fun setUp() {
        previousZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        ConversationPlanStore.clearAll(context); AssistantSessionStore(context).clear(); ChatModeStore.set(context, ChatMode.CHAT)
    }
    @After fun tearDown() {
        ConversationPlanStore.clearAll(context); AssistantSessionStore(context).clear(); ChatModeStore.set(context, ChatMode.CHAT)
        db.close(); TimeZone.setDefault(previousZone)
    }
    private suspend fun say(s: String) = OfflineAssistant.respond(s, context)!!.formattedText
    private fun current() = ConversationPlanStore(context).active()!!
    private suspend fun save(p: PlanWorkspace): ConversationPlanCommitter.Result = ConversationPlanCommitter.commit(context,
        p.copy(reviewedRevision = p.revision, reviewedAt = now, reviewProcess = "test:review"), now, "test")
    private fun metadata(start: Long? = now + 3_600_000, parent: Long? = null, prerequisites: Set<Long> = emptySet()) =
        TaskPlanning(goal = "Trip", parentTaskId = parent, prerequisiteTaskIds = prerequisites, minutes = 20, start = start, timezone = "UTC")

    @Test fun reviewedSavePersistsHierarchyAndSessionEvenWhenChildComesFirst() = runBlocking {
        val parent = PlanStep(title = "Prepare")
        val prerequisite = PlanStep(title = "Research", minutes = 10, start = now + 3_600_000)
        val child = PlanStep(title = "Book", parentId = parent.id, prerequisites = setOf(prerequisite.id),
            minutes = 30, start = prerequisite.end!!, deadline = now + 86_400_000)
        val result = save(PlanWorkspace(goal = "Trip", steps = listOf(child, prerequisite, parent)))
        val rows = db.taskDao().getAllTasksList().associateBy { it.title }
        val p = rows.getValue("Book").planning!!
        assertEquals(rows.getValue("Prepare").id, p.parentTaskId)
        assertEquals(setOf(rows.getValue("Research").id), p.prerequisiteTaskIds)
        assertEquals(child.start, p.start); assertEquals(child.end, p.end); assertEquals(child.deadline, rows.getValue("Book").dueDate)
        assertNull(rows.getValue("Research").dueDate)
        assertEquals(rows.getValue("Book"), TaskJson.decode(JSONObject(result.plan.steps.first().taskSnapshot!!)))
        ConversationPlanStore.clearAll(context)
        assertEquals(2, canonicalWorkSessions(db.taskDao().getAllTasksList()).size)
    }
    @Test fun workAddedAfterReviewRejectsEntireCommit() = runBlocking {
        val step = PlanStep(title = "Research", minutes = 20, start = now + 3_600_000)
        val p = PlanWorkspace(steps = listOf(step), reviewedRevision = 1, reviewedAt = now, reviewProcess = "test:review")
        val occupied = Task(title = "Protected work", planningJson = metadata(step.start).json())
        db.taskDao().insertTask(occupied)
        try { ConversationPlanCommitter.commit(context, p, now, "test"); fail("Should reject conflict") }
        catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("conflicts")) }
        assertEquals(listOf("Protected work"), db.taskDao().getAllTasksList().map { it.title })
        assertTrue(db.assistantPlanReceiptDao().pending().isEmpty())
    }
    @Test fun discardedWorkspaceStillReservesCanonicalWorkButDeadlineDoesNot() = runBlocking {
        val at = now + 3_600_000
        db.taskDao().insertTask(Task(title = "Deadline only", dueDate = at))
        val saved = save(PlanWorkspace(goal = "First", steps = listOf(PlanStep(title = "Research", minutes = 20, start = at)))).plan
        ConversationPlanStore.clearAll(context)
        val next = PlanWorkspace(goal = "Second")
        val occupied = occupiedWork(context, next, ConversationPlanStore(context))
        assertEquals(listOf("Research"), occupied.map { it.title })
        assertTrue(occupiedWork(context, saved, ConversationPlanStore(context)).isEmpty())
        val task = db.taskDao().getAllTasksList().single { it.title == "Research" }
        db.taskDao().softDeleteTask(task.id)
        assertTrue(occupiedWork(context, next, ConversationPlanStore(context)).isEmpty())
    }
    @Test fun nativeEditsReplaceSavedReservationsButUnsavedProposalsStillReserveTime() = runBlocking {
        val at = now + 3_600_000
        val saved = save(PlanWorkspace(goal = "First", steps = listOf(PlanStep(title = "Research", minutes = 20, start = at)))).plan
        val row = db.taskDao().getAllTasksList().single()
        db.taskDao().updateTask(row.copy(planningJson = row.planning!!.copy(start = at + 3_600_000).json()))
        val other = PlanWorkspace(goal = "Second")
        val store = ConversationPlanStore(context)
        assertEquals(listOf(at + 3_600_000), occupiedWork(context, other, store).map { it.start })
        store.put(saved.changed(steps = saved.steps.map { it.copy(start = at + 7_200_000) }))
        assertEquals(setOf(at + 3_600_000, at + 7_200_000), occupiedWork(context, other, store).map { it.start }.toSet())
    }
    @Test fun nativeWorkEditInvalidatesSaveAndRefreshAdoptsIt() = runBlocking {
        say("/plan trip"); say("add Research for 20 minutes"); say("save plan"); say("apply plan")
        val task = db.taskDao().getAllTasksList().single()
        val native = task.copy(planningJson = task.planning!!.copy(start = now + 3_600_000, minutes = 35).json(), updatedAt = task.updatedAt + 1)
        db.taskDao().updateTask(native)
        say("rename Research to Transport research"); say("save plan")
        assertTrue(say("apply plan").contains("changed outside"))
        assertEquals(native, db.taskDao().getTaskById(task.id))
        say("refresh saved tasks")
        assertEquals(35, current().steps.single().minutes); assertEquals(native.planning!!.start, current().steps.single().start)
        db.taskDao().updateTask(native.copy(planningJson = native.planning!!.copy(start = null).json()))
        say("refresh saved tasks"); assertNull(current().steps.single().start)
    }
    @Test fun externalNativeParentRequiresExplicitSeparateCopyWithoutChangingOriginal() = runBlocking {
        say("/plan project"); say("add Research for 20 minutes"); say("save plan"); say("apply plan")
        val saved = db.taskDao().getAllTasksList().single()
        val parent = db.taskDao().insertTask(Task(title = "Outside parent"))
        val native = saved.copy(planningJson = saved.planning!!.copy(parentTaskId = parent).json(), updatedAt = saved.updatedAt + 1)
        db.taskDao().updateTask(native)
        val prior = current().steps.single()
        assertTrue(say("refresh saved tasks").contains("outside this draft"))
        assertEquals(prior, current().steps.single())
        assertTrue(say("unlink Research").contains("original task is unchanged"))
        assertNull(current().steps.single().taskSnapshot)
        assertEquals(native, db.taskDao().getTaskById(saved.id))
        say("rename Research to Separate research"); say("save plan"); say("apply plan")
        assertEquals(native, db.taskDao().getTaskById(saved.id))
        assertEquals(3, db.taskDao().getAllTasksList().size)
    }
    @Test fun undoRestoresCanonicalMetadataAndDeadlineExactly() = runBlocking {
        say("/plan project"); say("add Research for 20 minutes"); say("save plan"); say("apply plan")
        val before = db.taskDao().getAllTasksList().single()
        say("give Research 40 minutes"); say("save plan"); say("apply plan")
        assertEquals(40, db.taskDao().getTaskById(before.id)!!.planning!!.minutes)
        say("undo saved changes")
        val after = db.taskDao().getTaskById(before.id)!!
        assertEquals(before.planningJson, after.planningJson); assertEquals(before.dueDate, after.dueDate)
    }
    @Test fun mergeRemapsParentAndPrerequisitesAndNeverBindsMissingIds() = runBlocking {
        val unrelated = db.taskDao().insertTask(Task(id = 99, title = "Local unrelated"))
        val rows = listOf(Task(id = 5, title = "Child", planningJson = metadata(parent = 6, prerequisites = setOf(6, unrelated)).json()),
            Task(id = 6, title = "Parent"))
        val ids = db.taskDao().restoreTasks(rows, replace = false)
        val child = db.taskDao().getTaskById(ids.getValue(5))!!.planning!!
        assertEquals(ids.getValue(6), child.parentTaskId); assertEquals(setOf(ids.getValue(6)), child.prerequisiteTaskIds)
        assertEquals(1, child.missingPrerequisites); assertFalse(unrelated in child.prerequisiteTaskIds)
        val partial = metadata(parent = 99).remap(emptyMap())
        assertNull(partial.parentTaskId); assertTrue(partial.parentMissing)
    }
    @Test fun invalidGraphIsRejectedBeforeReplacementOrPartialInsert() = runBlocking {
        val existing = db.taskDao().insertTask(Task(title = "Keep"))
        try { db.taskDao().restoreTasks(listOf(Task(id = 1, title = "One", planningJson = metadata(null, 2).json()),
            Task(id = 2, title = "Two", planningJson = metadata(null, 1).json())), replace = true); fail("Should reject cycle") }
        catch (_: IllegalArgumentException) { }
        assertEquals("Keep", db.taskDao().getTaskById(existing)!!.title)
    }
    @Test fun taskJsonReadsLegacyRecordsAndIncludesMetadataInVersionEquality() {
        val legacy = Task(title = "Old")
        assertEquals(legacy, TaskJson.decode(TaskJson.encode(legacy)))
        val planned = legacy.copy(planningJson = metadata().json())
        assertEquals(planned, TaskJson.decode(TaskJson.encode(planned)))
        assertNotEquals(legacy, planned)
        assertThrows(IllegalArgumentException::class.java) { TaskPlanning.read(metadata().json().replace("\"version\":2", "\"version\":50")) }
    }
    @Test fun earlierWorkAppearsInTodayAndWidgetAgendaWithoutChangingDeadline() {
        val midnight = 1_900_000_000_000L
        val deadline = midnight + 7 * 86_400_000L
        val task = Task(title = "Book", dueDate = deadline, planningJson = metadata(midnight + 3_600_000).json())
        assertTrue(task.isInTodayAgenda(midnight + 86_400_000)); assertFalse(task.isInUpcomingAgenda(midnight + 86_400_000))
        assertEquals(deadline, task.dueDate)
        assertTrue(task.copy(planningJson = metadata(midnight + 2 * 86_400_000).json()).isInUpcomingAgenda(midnight + 86_400_000))
        assertTrue(task.copy(dueDate = null, planningJson = "").isInTodayAgenda(midnight + 86_400_000))
    }
    @Test fun workIntervalDoesNotMoveDeadlineAlarmAndNewOccurrenceDropsOldGraph() {
        val deadline = now + 86_400_000L
        val task = Task(id = 22, title = "Book", dueDate = deadline, recurrence = RecurrencePattern.WEEKLY, planningJson = metadata().copy(workReminders = false).json())
        val alarms = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        TaskReminderHelper.scheduleReminder(context, task)
        assertEquals(deadline, alarms.nextScheduledAlarm!!.triggerAtTime)
        TaskReminderHelper.cancelReminder(context, task)
        TaskReminderHelper.scheduleReminder(context, task.copy(dueDate = null))
        assertNull(alarms.nextScheduledAlarm)
        val next = TaskReminderHelper.generateNextRecurringTask(task)
        assertNull(next.planning!!.parentTaskId); assertTrue(next.planning!!.prerequisiteTaskIds.isEmpty())
        assertNotEquals(task.planning!!.start, next.planning!!.start)
    }
    @Test fun rollingSnapshotAndVaultMergeKeepNativePlanMetadata(): Unit = runBlocking {
        val noteDao = NoteDatabase.getInstance(context).noteDao(); noteDao.deleteAllNotes()
        db.taskDao().insertTask(Task(id = 10, title = "Parent"))
        val task = Task(id = 11, title = "Child", planningJson = metadata(parent = 10, prerequisites = setOf(10)).json())
        db.taskDao().insertTask(task)
        val path = DataSafetyManager.writePreOpSnapshot(context, noteDao, "canonical_test", db)!!
        db.taskDao().deleteAllTasks()
        val restored = DataSafetyManager.restoreSnapshot(context, path, noteDao, db)
        assertTrue(restored.exceptionOrNull()?.stackTraceToString(), restored.isSuccess)
        assertEquals(task, db.taskDao().getTaskById(11))
        val json = VaultSyncManager.createVaultJson(noteDao, db.taskDao())
        assertTrue(VaultSyncManager.restoreVaultFromJson(json, noteDao, db.taskDao(), mergeMode = true).isSuccess)
        val child = db.taskDao().getAllTasksList().single { it.id != 11L && it.title == "Child" }
        assertNotEquals(10L, child.planning!!.parentTaskId)
        assertEquals(setOf(child.planning!!.parentTaskId), child.planning!!.prerequisiteTaskIds)
        File(path).delete()
    }
    @Test fun schema13MigrationPreservesTaskAndDefaultsToUnplanned() = runBlocking {
        val name = "native-planning-migration"
        val old = Room.databaseBuilder(context, FocusDatabase::class.java, name).allowMainThreadQueries().build()
        val id = old.taskDao().insertTask(Task(title = "Keep", details = "Details", subtasksJson = "[]", dueDate = 12345))
        old.openHelper.writableDatabase.apply {
            // Recreate the pre-pause habit schema as well as the old task schema.
            val entities = org.json.JSONObject(java.io.File("schemas/com.focusbyrj.app.data.FocusDatabase/14.json").readText())
                .getJSONObject("database").getJSONArray("entities")
            val habitTable = (0 until entities.length()).map { entities.getJSONObject(it) }
                .single { it.getString("tableName") == "habits" }
            execSQL("DROP TABLE habits")
            execSQL(habitTable.getString("createSql").replace("\${TABLE_NAME}", "habits"))
            execSQL("ALTER TABLE tasks RENAME TO tasks_newer")
            execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, details TEXT NOT NULL, dueDate INTEGER, isCompleted INTEGER NOT NULL, type TEXT NOT NULL, recurrence TEXT NOT NULL, isPersistent INTEGER NOT NULL, isPriority INTEGER NOT NULL, completedAt INTEGER, updatedAt INTEGER NOT NULL, isTrashed INTEGER NOT NULL, trashedAt INTEGER, deletedAt INTEGER, subtasksJson TEXT NOT NULL)")
            execSQL("INSERT INTO tasks SELECT id,title,details,dueDate,isCompleted,type,recurrence,isPersistent,isPriority,completedAt,updatedAt,isTrashed,trashedAt,deletedAt,subtasksJson FROM tasks_newer")
            execSQL("DROP TABLE tasks_newer")
            execSQL("UPDATE room_master_table SET identity_hash = 'c05397edcaf1599f1c330e239134e851' WHERE id = 42")
            version = 13
        }
        old.close()
        val migrated = Room.databaseBuilder(context, FocusDatabase::class.java, name).addMigrations(FocusDatabase.MIGRATION_13_14, FocusDatabase.MIGRATION_14_15, FocusDatabase.MIGRATION_15_16).allowMainThreadQueries().build()
        try {
            val task = migrated.taskDao().getTaskById(id)!!
            assertEquals("Details", task.details); assertEquals(12345L, task.dueDate); assertEquals("", task.planningJson)
            assertNull(task.planning); assertTrue(migrated.assistantPlanReceiptDao().pending().isEmpty())
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
