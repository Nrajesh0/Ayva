package com.focusbyrj.app.data

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TaskOptionsTest {
    @Test fun recurringChecklistResetsCompletionAndPreservesSteps() {
        val step = Subtask(title = "Review", isDone = true)
        val original = Task(title = "Weekly review", dueDate = 1_800_000_000_000L, recurrence = RecurrencePattern.WEEKLY,
            isCompleted = true, subtasksJson = Subtask.listToJson(listOf(step)))
        val next = com.focusbyrj.app.util.TaskReminderHelper.generateNextRecurringTask(original)
        assertEquals(listOf(step.copy(isDone = false)), next.subtasks)
        assertFalse(next.isCompleted)
        assertTrue(original.subtasks.single().isDone)
    }
    @Test fun futureAvailabilitySeparatesTodayAndUpcoming() {
        val task = Task(title = "Prepare", planningJson = TaskPlanning(availableFrom = 2000L).json())
        assertFalse(task.isInTodayAgenda(1000L))
        assertTrue(task.isInUpcomingAgenda(1000L))
        assertTrue(task.isInTodayAgenda(3000L))
    }
    @Test fun metadataSurvivesRoundTripAndImportRemapping() {
        val value = TaskPlanning(availableFrom = 2000L, requireSubtasks = true)
        assertEquals(value, TaskPlanning.read(value.json()))
        assertEquals(value, TaskPlanning.read(TaskPlanning.remap(value.json(), emptyMap())))
    }
    @Test fun completionRequiresAllStepsOnlyWhenEnabled() {
        val task = Task(title = "Prepare", subtasksJson = Subtask.listToJson(listOf(Subtask(title = "Pack"))),
            planningJson = TaskPlanning(requireSubtasks = true).json())
        assertThrows(IllegalArgumentException::class.java) { TaskCompletionRules.validate(task) }
        TaskCompletionRules.validate(task.copy(subtasksJson = Subtask.listToJson(task.subtasks.map { it.copy(isDone = true) })))
        TaskCompletionRules.validate(task.copy(planningJson = ""))
    }
}
