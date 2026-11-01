package com.focusbyrj.app.service

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSettings
import java.time.Duration
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ReminderOverlayRecoveryTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    private val main get() = shadowOf(Looper.getMainLooper())
    private val due = System.currentTimeMillis() - 1000L
    private val today get() = LocalDate.now().toString()

    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        Application::class.java.getDeclaredMethod("attach", Context::class.java)
            .apply { isAccessible = true; invoke(app, context) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        UnifiedOverlayCoordinator.clearQueue()
        main.idleFor(Duration.ofSeconds(1))
        ReminderOverlayStore(context).write(emptyList())
        ShadowSettings.setCanDrawOverlays(true)
    }

    @After fun cleanup() {
        UnifiedOverlayCoordinator.clearQueue()
        main.idleFor(Duration.ofSeconds(1))
        ReminderOverlayStore(context).write(emptyList())
        db.close()
    }

    private fun task(id: Long) = Task(id = id, title = "Private task $id", dueDate = due)
    private fun record(id: Long) = PendingOverlayReminder("task", id, due)
    private fun item(task: Task) = OverlayQueueItem.TaskItem(task.id, "Old title", "", task.dueDate ?: due,
        "TASK", "NONE", false, false)
    private fun currentTask() = TaskReminderOverlayManager::class.java.getDeclaredField("currentTaskId")
        .apply { isAccessible = true }.getLong(TaskReminderOverlayManager)
    private fun awaitTask(id: Long) {
        repeat(400) {
            main.idleFor(Duration.ofMillis(25))
            if (currentTask() == id && TaskReminderOverlayManager.isShowing) return
            Thread.sleep(5) // Give Room's background executor time to finish.
        }
        assertEquals("Expected task popup after DB validation", id, currentTask())
        assertTrue(TaskReminderOverlayManager.isShowing)
    }

    @Test fun restoresAllPendingIdentitiesInOrder() = runBlocking {
        db.taskDao().insertTasks((1L..3L).map { task(it) })
        ReminderOverlayStore(context).write((1L..3L).map { record(it) })
        UnifiedOverlayCoordinator.restorePending(context)
        awaitTask(1)
        assertEquals(listOf(1L, 2L, 3L), ReminderOverlayStore(context).read().map { it.id })
        TaskReminderOverlayManager.dismissTask(1, context)
        awaitTask(2)
        assertEquals(listOf(2L, 3L), ReminderOverlayStore(context).read().map { it.id })
        TaskReminderOverlayManager.dismissTask(2, context)
        awaitTask(3)
    }

    @Test fun restartReplaysInterruptedPopupAndWaitingReminder() = runBlocking {
        db.taskDao().insertTasks(listOf(task(1), task(2)))
        ReminderOverlayStore(context).write(listOf(record(1), record(2)))
        UnifiedOverlayCoordinator.restorePending(context)
        awaitTask(1)
        val diskSnapshot = ReminderOverlayStore(context).read()
        UnifiedOverlayCoordinator.clearQueue()
        main.idleFor(Duration.ofSeconds(1))
        ReminderOverlayStore(context).write(diskSnapshot)
        UnifiedOverlayCoordinator.restorePending(context)
        awaitTask(1)
        TaskReminderOverlayManager.dismissTask(1, context)
        awaitTask(2)
    }

    @Test fun completedDeletedTrashedUndatedAndRescheduledTasksAreSkipped() = runBlocking {
        val tasks = listOf(task(1).copy(isCompleted = true), task(2).copy(isTrashed = true),
            task(3).copy(deletedAt = due), task(4).copy(dueDate = null),
            task(5).copy(dueDate = due + 3_600_000), task(6))
        db.taskDao().insertTasks(tasks)
        for (task in tasks.take(5)) assertNull(UnifiedOverlayCoordinator.refreshItem(context,
            OverlayQueueItem.RestoredItem(record(task.id))))
        assertNull(UnifiedOverlayCoordinator.refreshItem(context, OverlayQueueItem.RestoredItem(record(999))))
        assertNotNull(UnifiedOverlayCoordinator.refreshItem(context, item(tasks.last())))
    }

    @Test fun refreshesTitleAndDetailsFromDatabaseBeforeDisplay() = runBlocking {
        val task = task(1).copy(title = "Updated title", details = "Updated details", isPersistent = true)
        db.taskDao().insertTask(task)
        val refreshed = UnifiedOverlayCoordinator.refreshItem(context, item(task)) as OverlayQueueItem.TaskItem
        assertEquals("Updated title", refreshed.taskTitle)
        assertEquals("Updated details", refreshed.taskDetails)
        assertTrue(refreshed.isPersistent)
    }

    @Test fun archivedPausedDisabledAndOldDayHabitsAreSkipped() = runBlocking {
        val habits = listOf(Habit(id = 1, title = "Archived", isArchived = true),
            Habit(id = 2, title = "Paused", pausedAt = due),
            Habit(id = 3, title = "Disabled", isReminderEnabled = false),
            Habit(id = 4, title = "Read"))
        db.habitDao().insertHabits(habits)
        for (habit in habits.take(3)) assertNull(UnifiedOverlayCoordinator.refreshItem(context,
            OverlayQueueItem.HabitItem(habit, 0, 1)))
        assertNull(UnifiedOverlayCoordinator.refreshItem(context,
            OverlayQueueItem.HabitItem(habits.last(), 0, 1, reminderDate = LocalDate.now().minusDays(1).toString())))
        assertNotNull(UnifiedOverlayCoordinator.refreshItem(context, OverlayQueueItem.HabitItem(habits.last(), 0, 1)))
    }

    @Test fun cancellingWhileDatabaseValidationIsPendingDoesNotOpenCancelledPopup() = runBlocking {
        db.taskDao().insertTasks(listOf(task(1), task(2)))
        TaskReminderOverlayManager.showReminderOverlay(context, 1, "One", "", due, "TASK", "NONE", false)
        TaskReminderOverlayManager.showReminderOverlay(context, 2, "Two", "", due, "TASK", "NONE", false)
        TaskReminderOverlayManager.dismissTask(1, context)
        awaitTask(2)
        assertEquals(listOf(2L), ReminderOverlayStore(context).read().map { it.id })
    }

    @Test fun permissionFailureCanRecoverNextTimeWithoutLosingOrder() = runBlocking {
        db.taskDao().insertTasks(listOf(task(1), task(2)))
        ReminderOverlayStore(context).write(listOf(record(1), record(2)))
        ShadowSettings.setCanDrawOverlays(false)
        UnifiedOverlayCoordinator.restorePending(context)
        repeat(100) { main.idleFor(Duration.ofMillis(50)); Thread.sleep(5) }
        val pending = ReminderOverlayStore(context).read()
        assertEquals(listOf(1L, 2L), pending.map { it.id })
        UnifiedOverlayCoordinator.clearQueue()
        main.idleFor(Duration.ofSeconds(1))
        ReminderOverlayStore(context).write(pending)
        ShadowSettings.setCanDrawOverlays(true)
        UnifiedOverlayCoordinator.restorePending(context)
        awaitTask(1)
        TaskReminderOverlayManager.dismissTask(1, context)
        awaitTask(2)
    }

    @Test fun diskStoreContainsNoTaskTitlesOrDetailsAndDeduplicatesIdentities() {
        ReminderOverlayStore(context).write(listOf(record(1), record(1), PendingOverlayReminder("habit", 1, reminderDate = today)))
        val saved = ReminderOverlayStore(context).read()
        assertEquals(listOf("task:1", "habit:1"), saved.map { it.key })
        val json = java.io.File(context.noBackupFilesDir, "pending-reminder-overlays.json").readText()
        assertFalse(json.contains("Private task"))
        assertFalse(json.contains("taskTitle"))
    }
    @Test fun completedHabitProgressIsRecheckedBeforeDisplay() = runBlocking {
        val habit = Habit(id = 1, title = "Read", targetPerDay = 3)
        db.habitDao().insertHabit(habit)
        db.habitDao().insertOrUpdateLog(HabitLog(habitId = 1, date = today, completedCount = 3, targetCount = 3))
        assertNull(UnifiedOverlayCoordinator.refreshItem(context, OverlayQueueItem.HabitItem(habit, 0, 3)))
    }

    @Test fun taskCompletedWhileWaitingIsSkippedAndRemainingQueueContinues() = runBlocking {
        db.taskDao().insertTasks((1L..3L).map { task(it) })
        ReminderOverlayStore(context).write((1L..3L).map { record(it) })
        UnifiedOverlayCoordinator.restorePending(context)
        awaitTask(1)
        db.taskDao().insertTask(task(2).copy(isCompleted = true))
        TaskReminderOverlayManager.dismissTask(1, context)
        awaitTask(3)
        assertEquals(listOf(3L), ReminderOverlayStore(context).read().map { it.id })
    }

    @Test fun databaseFailureKeepsPendingIdentitiesForRetry() {
        // Force a real query error. Closing an unopened Room DB can simply let it
        // reopen as a new empty in-memory DB, which is not a failure injection.
        db.openHelper.writableDatabase.execSQL("DROP TABLE tasks")
        ReminderOverlayStore(context).write(listOf(record(1), record(2)))
        UnifiedOverlayCoordinator.restorePending(context)
        repeat(100) { main.idleFor(Duration.ofMillis(50)); Thread.sleep(5) }
        assertFalse(UnifiedOverlayCoordinator.isAnyOverlayShowing)
        assertEquals(listOf(1L, 2L), ReminderOverlayStore(context).read().map { it.id })
    }

}
