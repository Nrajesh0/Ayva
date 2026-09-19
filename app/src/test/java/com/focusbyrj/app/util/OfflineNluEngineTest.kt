package com.focusbyrj.app.util

import com.focusbyrj.app.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class OfflineNluEngineTest {

    @Test
    fun testRescheduleIntent() {
        val result = OfflineNluEngine.parse("reschedule task 1 to tomorrow", emptyList())
        assertEquals(NluIntent.RESCHEDULE, result.intent)
    }

    @Test
    fun testCompleteIntent() {
        val result = OfflineNluEngine.parse("mark 2nd task as done", emptyList())
        assertEquals(NluIntent.COMPLETE, result.intent)
    }

    @Test
    fun testTargetExtractionByTitle() {
        val tasks = listOf(Task(id = 1, title = "Buy groceries", type = com.focusbyrj.app.data.TaskType.TASK), Task(id = 2, title = "Finish report", type = com.focusbyrj.app.data.TaskType.TASK))
        val result = OfflineNluEngine.parse("finish report", tasks)
        assertNotNull(result.targetTask)
        assertEquals("Finish report", result.targetTask?.title)
    }
    
    @Test
    fun testTargetExtractionByOrdinal() {
        val tasks = listOf(Task(id = 1, title = "Buy groceries", type = com.focusbyrj.app.data.TaskType.TASK), Task(id = 2, title = "Finish report", type = com.focusbyrj.app.data.TaskType.TASK))
        val result1 = OfflineNluEngine.parse("complete 1st task", tasks)
        assertEquals("Buy groceries", result1.targetTask?.title)
        
        val result2 = OfflineNluEngine.parse("cancel task 2", tasks)
        assertEquals("Finish report", result2.targetTask?.title)
    }
    
    @Test
    fun testBlockIntent() {
         val result1 = OfflineNluEngine.parse("block instagram", emptyList())
         assertEquals(NluIntent.BLOCK_APP, result1.intent)
         assertEquals("instagram", result1.targetFilterOrAppName)
         assertEquals("HARD", result1.blockMode)
         
         val result2 = OfflineNluEngine.parse("soft block Social filter", emptyList())
         assertEquals(NluIntent.BLOCK_FILTER, result2.intent)
         assertEquals("social", result2.targetFilterOrAppName)
         assertEquals("SOFT", result2.blockMode)
    }
    
    @Test
    fun testRoutinesIntent() {
        val result = OfflineNluEngine.parse("list my routines", emptyList())
        assertEquals(NluIntent.LIST_ROUTINES, result.intent)
    }

    @Test
    fun testExplicitTaskCreation() {
        val result1 = OfflineNluEngine.parse("add check car tire pressure tomorrow at 5pm", emptyList())
        assertEquals(NluIntent.CREATE_TASK, result1.intent)
        assertNotNull(result1.targetDateMs)
        assertEquals("Check car tire pressure", result1.createdTaskTitle)

        val result2 = OfflineNluEngine.parse("remind me to inspect kitchen pipes tomorrow at 9am", emptyList())
        assertEquals(NluIntent.CREATE_TASK, result2.intent)
        assertNotNull(result2.targetDateMs)
        assertEquals("Inspect kitchen pipes", result2.createdTaskTitle)
    }

    @Test
    fun testVerbWithoutExistingTaskBecomesCreateTask() {
        val result1 = OfflineNluEngine.parse("move furniture tomorrow at 3pm", emptyList())
        assertEquals(NluIntent.CREATE_TASK, result1.intent)

        val result2 = OfflineNluEngine.parse("push code to github tonight at 8pm", emptyList())
        assertEquals(NluIntent.CREATE_TASK, result2.intent)
    }

    @Test
    fun testConflictResolutionWhenQueryMatchesTaskAndHasNewSchedule() {
        val tasks = listOf(
            Task(id = 1, title = "Buy groceries", type = com.focusbyrj.app.data.TaskType.TASK),
            Task(id = 2, title = "Finish report", type = com.focusbyrj.app.data.TaskType.TASK)
        )
        val result = OfflineNluEngine.parse("finish report tomorrow at 5pm", tasks)
        assertEquals(NluIntent.CONFLICT, result.intent)
        assertTrue(result.conflictOptions.isNotEmpty())
        assertTrue(result.conflictOptions.any { it.label.contains("Reschedule", ignoreCase = true) || it.label.contains("Update", ignoreCase = true) })
        assertTrue(result.conflictOptions.any { it.label.contains("Create", ignoreCase = true) })
    }

    @Test
    fun testConflictResolutionWhenMultipleTasksMatch() {
        val tasks = listOf(
            Task(id = 1, title = "Gym Leg Day", type = com.focusbyrj.app.data.TaskType.TASK),
            Task(id = 2, title = "Gym Chest Day", type = com.focusbyrj.app.data.TaskType.TASK)
        )
        val result = OfflineNluEngine.parse("reschedule gym to 6pm", tasks)
        assertEquals(NluIntent.CONFLICT, result.intent)
        assertTrue(result.conflictOptions.size >= 2)
        assertTrue(result.conflictOptions.any { it.label.contains("Leg Day") })
        assertTrue(result.conflictOptions.any { it.label.contains("Chest Day") })
    }

    @Test
    fun testConflictWhenAddingExistingTask() {
        val tasks = listOf(Task(id = 1, title = "Buy groceries", type = com.focusbyrj.app.data.TaskType.TASK))
        val result = OfflineNluEngine.parse("remind me to buy groceries tomorrow at 6pm", tasks)
        assertEquals(NluIntent.CONFLICT, result.intent)
        assertTrue(result.conflictOptions.any { it.label.contains("Reschedule", ignoreCase = true) || it.label.contains("Update", ignoreCase = true) })
        assertTrue(result.conflictOptions.any { it.label.contains("Mark Done", ignoreCase = true) })
        assertTrue(result.conflictOptions.any { it.label.contains("Create", ignoreCase = true) })
    }

    @Test
    fun testConflictFocusSessionVsTodo() {
        val result = OfflineNluEngine.parse("focus for 25 minutes", emptyList())
        assertEquals(NluIntent.CONFLICT, result.intent)
        assertTrue(result.conflictOptions.any { it.label.contains("Focus", ignoreCase = true) })
        assertTrue(result.conflictOptions.any { it.label.contains("Todo", ignoreCase = true) })
    }

    @Test
    fun testConflictMathDrillVsTodo() {
        val result = OfflineNluEngine.parse("practice math", emptyList())
        assertEquals(NluIntent.CONFLICT, result.intent)
        assertTrue(result.conflictOptions.any { it.label.contains("Math", ignoreCase = true) || it.label.contains("Drill", ignoreCase = true) })
        assertTrue(result.conflictOptions.any { it.label.contains("Todo", ignoreCase = true) })
    }

    @Test
    fun testConflictHabitTrackerVsTodo() {
        val result = OfflineNluEngine.parse("meditate daily", emptyList())
        assertEquals(NluIntent.CONFLICT, result.intent)
        assertTrue(result.conflictOptions.any { it.label.contains("Habit", ignoreCase = true) })
        assertTrue(result.conflictOptions.any { it.label.contains("Todo", ignoreCase = true) })
    }

    @Test
    fun testConflictDailyQuestsVsTodo() {
        val result = OfflineNluEngine.parse("daily quests", emptyList())
        assertEquals(NluIntent.CONFLICT, result.intent)
        assertTrue(result.conflictOptions.any { it.label.contains("Quests", ignoreCase = true) })
        assertTrue(result.conflictOptions.any { it.label.contains("Todo", ignoreCase = true) })
    }

    @Test
    fun testConflictSummaryVsProfile() {
        val result = OfflineNluEngine.parse("summary", emptyList())
        assertEquals(NluIntent.CONFLICT, result.intent)
        assertTrue(result.conflictOptions.any { it.label.contains("Profile", ignoreCase = true) })
        assertTrue(result.conflictOptions.any { it.label.contains("Summary", ignoreCase = true) })
    }

    @Test
    fun testConflictClearDisambiguation() {
        val result = OfflineNluEngine.parse("clear all", emptyList())
        assertEquals(NluIntent.CONFLICT, result.intent)
        assertTrue(result.conflictOptions.any { it.label.contains("Chat", ignoreCase = true) })
        assertTrue(result.conflictOptions.any { it.label.contains("Complete", ignoreCase = true) })
        assertTrue(result.conflictOptions.any { it.label.contains("Delete", ignoreCase = true) })
    }

    @Test
    fun testCheckAcPricesAt9am() {
        val result = OfflineNluEngine.parse("check ac prices at 9 am", emptyList())
        println("testCheckAcPricesAt9am result: intent=${result.intent}, title=${result.createdTaskTitle}, targetDate=${result.targetDateMs}, conflictPrompt=${result.conflictPrompt}")
        assertEquals(NluIntent.CREATE_TASK, result.intent)
    }

    @Test
    fun testAddTaskVariants() {
        val r1 = OfflineNluEngine.parse("add task buy milk", emptyList())
        assertEquals(NluIntent.CREATE_TASK, r1.intent)
        assertEquals("Buy milk", r1.createdTaskTitle)

        val r2 = OfflineNluEngine.parse("add buy milk", emptyList())
        assertEquals(NluIntent.CREATE_TASK, r2.intent)
        assertEquals("Buy milk", r2.createdTaskTitle)

        val r3 = OfflineNluEngine.parse("create task prepare presentation", emptyList())
        assertEquals(NluIntent.CREATE_TASK, r3.intent)
        assertEquals("Prepare presentation", r3.createdTaskTitle)

        val r4 = OfflineNluEngine.parse("schedule doctor visit tomorrow at 4pm", emptyList())
        assertEquals(NluIntent.CREATE_TASK, r4.intent)
        assertNotNull(r4.targetDateMs)
        assertEquals("Doctor visit", r4.createdTaskTitle)
    }
}
