package com.focusbyrj.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import com.focusbyrj.app.widget.ExamCountdown
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ProductivityFeaturesTest {
    @Test fun monthlyRecurrenceClamps31stAndResetsSubtasks() {
        val base = java.util.Calendar.getInstance().apply {
            set(get(java.util.Calendar.YEAR) + 1, java.util.Calendar.JANUARY, 31, 10, 0, 0)
        }
        val task = Task(title = "Pay bills", dueDate = base.timeInMillis,
            recurrence = RecurrencePattern.MONTHLY,
            subtasksJson = Subtask.listToJson(listOf(Subtask(id = "bill", title = "Pay", isDone = true))))
        val next = com.focusbyrj.app.util.TaskReminderHelper.generateNextRecurringTask(task)
        val date = java.util.Calendar.getInstance().apply { timeInMillis = next.dueDate!! }
        assertEquals(base.get(java.util.Calendar.YEAR), date.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.FEBRUARY, date.get(java.util.Calendar.MONTH))
        assertEquals(date.getActualMaximum(java.util.Calendar.DAY_OF_MONTH), date.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals("bill", next.subtasks.single().id)
        assertFalse(next.subtasks.single().isDone)
    }
    @Test fun templateCopiesHaveFreshStepsAndNoOldState() {
        val source = Task(id = 4, title = "Trip", dueDate = 2000, isCompleted = true,
            subtasksJson = Subtask.listToJson(listOf(Subtask(title = "Pack", isDone = true))),
            planningJson = TaskPlanning(requireSubtasks = true, availableFrom = 1000, prerequisiteTaskIds = setOf(5)).json())
        val template = ProductivityLibrary.template(source)
        val a = ProductivityLibrary.instantiate(template); val b = ProductivityLibrary.instantiate(template)
        assertNotEquals(a.subtasks.single().id, b.subtasks.single().id)
        assertFalse(a.subtasks.single().isDone); assertFalse(a.isCompleted); assertEquals(2000L, a.dueDate)
        assertTrue(a.planning!!.requireSubtasks); assertEquals(1000L, a.planning!!.availableFrom); assertEquals(setOf(5L), a.planning!!.prerequisiteTaskIds)
    }
    @Test fun templatePreservesAllTaskSettingsAndShiftsDatesOnReuse() {
        val now = Instant.parse("2026-10-20T08:00:00Z").toEpochMilli()
        val task = Task(title = "Anniversary", details = "Details", type = TaskType.ANNIVERSARY,
            recurrence = RecurrencePattern.YEARLY, isPersistent = true, isPriority = true,
            dueDate = now + 172800000,
            planningJson = TaskPlanning(minutes = 30, start = now + 86400000, availableFrom = now,
                timezone = "UTC", prerequisiteTaskIds = setOf(7), parentTaskId = 8, requireSubtasks = true, workReminders = false).json())
        val j = ProductivityLibrary.template(task, now)
        val copy = ProductivityLibrary.instantiate(j, now + 86400000)
        assertEquals(task.type, copy.type); assertEquals(task.recurrence, copy.recurrence)
        assertTrue(copy.isPersistent); assertTrue(copy.isPriority)
        assertEquals(task.dueDate, copy.dueDate)
        assertEquals(now, copy.planning!!.availableFrom)
        assertEquals(now + 86400000, copy.planning!!.start)
        val relative = ProductivityLibrary.instantiate(ProductivityLibrary.template(task.copy(type = TaskType.TASK), now), now + 86400000)
        assertEquals(task.dueDate!! + 86400000, relative.dueDate)
        assertEquals(now + 86400000, relative.planning!!.availableFrom)
        assertEquals(now + 172800000, relative.planning!!.start)
        assertFalse(copy.planning!!.workReminders); assertEquals(setOf(7L), copy.planning!!.prerequisiteTaskIds)
        val imported = ProductivityLibrary.remapChain(j.toString(), emptyMap(), mapOf(7L to 70L, 8L to 80L))!!
        val rebound = ProductivityLibrary.instantiate(JSONObject(imported), now)
        assertEquals(setOf(70L), rebound.planning!!.prerequisiteTaskIds); assertEquals(80L, rebound.planning!!.parentTaskId)
    }
    @Test fun legacyChecklistTemplatesStillOpenWithTaskDefaults() {
        val old = JSONObject().put("kind", "template").put("name", "Old checklist").put("steps", JSONArray(listOf("Pack")))
        val task = ProductivityLibrary.instantiate(old)
        assertEquals("Pack", task.subtasks.single().title); assertEquals(TaskType.TASK, task.type)
        assertNull(task.dueDate); assertEquals(RecurrencePattern.NONE, task.recurrence)
    }
    @Test fun importedRoutineOnlyUsesImportedHabits() {
        val raw = JSONObject().put("kind", "chain").put("name", "Morning").put("habits", JSONArray(listOf(1, 2))).toString()
        val restored = JSONObject(ProductivityLibrary.remapChain(raw, mapOf(2L to 99L))!!).getJSONArray("habits")
        assertEquals(1, restored.length()); assertEquals(99, restored.getLong(0))
        assertNull(ProductivityLibrary.remapChain(raw, emptyMap()))
    }
    @Test fun weeklyTargetsCountDaysAndHaveWeeklyStreaks() {
        val f = HabitFrequency(mode = "WEEKLY", weeklyTarget = 2, since = "2026-10-01")
        val dates = listOf("2026-10-19", "2026-10-20", "2026-10-26", "2026-10-27")
        val logs = dates.map { HabitLog(habitId = 1, date = it, completedCount = 20) }
        assertEquals(2, f.weekCount(logs, LocalDate.parse("2026-10-30")))
        assertEquals(2 to 2, f.weeklyStreaks(logs, LocalDate.parse("2026-10-30")))
        assertEquals(1, f.weekCount(listOf(logs.last()), LocalDate.parse("2026-10-30")))
    }
    @Test fun selectedDaysSkipRestDaysButPreserveEarlierDailySchedule() {
        val f = HabitFrequency(mode = "DAYS", days = setOf(1, 3, 5), since = "2026-10-26")
        assertFalse(f.scheduled(LocalDate.parse("2026-10-27")))
        assertTrue(f.scheduled(LocalDate.parse("2026-10-28")))
        assertTrue(f.scheduled(LocalDate.parse("2026-10-25")))
    }
    @Test fun dependencyChecksAndPurgeKeepReferencedTasks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FocusDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.taskDao()
            val first = dao.insertTaskChecked(Task(title = "First"))
            val next = dao.insertTaskChecked(Task(title = "Next", planningJson = TaskPlanning(prerequisiteTaskIds = setOf(first)).json()))
            var rejected = false
            try { dao.updateTaskChecked(dao.getTaskById(next)!!.copy(isCompleted = true)) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected); assertFalse(dao.getTaskById(next)!!.isCompleted)
            dao.updateTaskChecked(dao.getTaskById(first)!!.copy(isCompleted = true, completedAt = 1))
            dao.deleteCompletedTasksBefore(100)
            assertNotNull(dao.getTaskById(first))
            dao.updateTaskChecked(dao.getTaskById(next)!!.copy(isCompleted = true))
            rejected = false
            try { dao.deleteWithDependencies(dao.getTaskById(first)!!) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            dao.deleteWithDependencies(dao.getTaskById(first)!!, detach = true)
            assertTrue(dao.getTaskById(next)!!.planning!!.prerequisiteTaskIds.isEmpty())
        } finally { db.close() }
    }
    @Test fun startAfterDeadlineRejectedWithoutChangingTask() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FocusDatabase::class.java).allowMainThreadQueries().build()
        try {
            val id = db.taskDao().insertTaskChecked(Task(title = "Exam", dueDate = 1000))
            var rejected = false
            try { db.taskDao().updateTaskChecked(db.taskDao().getTaskById(id)!!.copy(planningJson = TaskPlanning(availableFrom = 2000).json())) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected); assertEquals("", db.taskDao().getTaskById(id)!!.planningJson)
        } finally { db.close() }
    }
    @Test fun countdownUsesLocalCalendarAcrossDstAndTransitionsAtExamTime() {
        val c = ExamCountdown(date = "2026-03-09", time = "09:00", zone = "America/New_York", startDate = "2026-03-01")
        c.validate()
        assertEquals("2 days left", c.headline(Instant.parse("2026-03-07T14:00:00Z")))
        assertEquals("Today", c.headline(Instant.parse("2026-03-09T12:59:00Z")))
        assertEquals(c.endedText, c.headline(Instant.parse("2026-03-09T13:00:00Z")))
        assertEquals(100, c.progress(Instant.parse("2026-03-09T13:00:00Z")))
        assertEquals(c, ExamCountdown.read(c.json()))
    }
    @Test fun weeklyReminderWaitsUntilNextMondayAndBadgeDoesNotClaimDailyStreak() {
        val habit = Habit(title = "Practice", frequencyJson = HabitFrequency(mode = "WEEKLY", weeklyTarget = 3).json())
        val next = com.focusbyrj.app.util.HabitAlarmScheduler.calculateNextTriggerTime(
            habit.copy(frequencyJson = habit.frequency.copy(mode = "DAYS", days = setOf(1)).json()), isGoalCompletedToday = true)!!
        val day = java.time.Instant.ofEpochMilli(next).atZone(java.time.ZoneId.systemDefault())
        assertEquals(java.time.DayOfWeek.MONDAY, day.dayOfWeek)
        assertTrue(next > System.currentTimeMillis())
        assertEquals("3 days / week", com.focusbyrj.app.util.HabitMicroCopyProvider.getStreakBadge(5, habit).label)
    }
    @Test fun countdownRejectsNonexistentAndAmbiguousTimes() {
        assertThrows(IllegalArgumentException::class.java) { ExamCountdown(date = "2026-03-08", time = "02:30", zone = "America/New_York", startDate = "2026-03-01").validate() }
        assertThrows(IllegalArgumentException::class.java) { ExamCountdown(date = "2026-11-01", time = "01:30", zone = "America/New_York", startDate = "2026-10-01").validate() }
    }
}
