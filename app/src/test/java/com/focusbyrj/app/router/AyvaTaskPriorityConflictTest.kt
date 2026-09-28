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

package com.focusbyrj.app.router

import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskType
import com.focusbyrj.app.util.NluIntent
import com.focusbyrj.app.util.OfflineNluEngine
import com.focusbyrj.app.util.router.AyvaIntentRouter
import com.focusbyrj.app.util.router.RouterDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AyvaTaskPriorityConflictTest {

    @Test
    fun testConflict2_CleanAllOverdueTasksDoesNotWipeChat() {
        val result = OfflineNluEngine.classifyIntent("clean all overdue tasks")
        assertNotEquals("Chat must NOT be wiped on 'clean all overdue tasks'", NluIntent.CLEAR_CHAT, result)
    }

    @Test
    fun testConflict3_QuestionsDoNotTriggerDrillOrProfile() {
        val drillQuestionResult = OfflineNluEngine.classifyIntent("How does arithmetic drill work?")
        assertNotEquals("Question must NOT launch arithmetic drill", NluIntent.START_DRILL, drillQuestionResult)

        val xpQuestionResult = OfflineNluEngine.classifyIntent("How do I earn XP points?")
        assertNotEquals("Question must NOT launch profile", NluIntent.SHOW_PROFILE, xpQuestionResult)
    }

    @Test
    fun testConflict4_CasualPhrasesDoNotCreateTasksSilently() {
        val destination = AyvaIntentRouter.route("I am feeling lazy today")
        assertTrue("Casual phrase must route to TalkQuery, not TaskCreation", destination is RouterDestination.TalkQuery)
    }

    @Test
    fun testRoutineAmbiguityPresentsTaskFirst() {
        val destination = AyvaIntentRouter.route("morning exercise 7am")
        assertTrue("Routine ambiguity must present ConflictCard", destination is RouterDestination.ConflictCard)
        val card = destination as RouterDestination.ConflictCard
        assertTrue("Option 1 must be Task Creation", card.options[0].label.contains("Add as Todo Task"))
        assertTrue("Option 1 command must be /create", card.options[0].command.startsWith("/create"))
    }

    @Test
    fun testHabitAmbiguityPresentsTaskFirst() {
        val destination = AyvaIntentRouter.route("drink 2L water")
        assertTrue("Habit ambiguity must present ConflictCard", destination is RouterDestination.ConflictCard)
        val card = destination as RouterDestination.ConflictCard
        assertTrue("Option 1 must be Task Creation", card.options[0].label.contains("Add as Todo Task"))
        assertTrue("Option 1 command must be /create", card.options[0].command.startsWith("/create"))
    }

    @Test
    fun testTimerAmbiguityPresentsTaskFirst() {
        val destination = AyvaIntentRouter.route("deep work session for 2 hours")
        assertTrue("Timer ambiguity must present ConflictCard", destination is RouterDestination.ConflictCard)
        val card = destination as RouterDestination.ConflictCard
        assertTrue("Option 1 must be Task Creation", card.options[0].label.contains("Add as Todo Task"))
        assertTrue("Option 1 command must be /create", card.options[0].command.startsWith("/create"))
    }

    @Test
    fun testMathDrillAmbiguityPresentsTaskFirst() {
        val destination = AyvaIntentRouter.route("math practice chapter 4")
        assertTrue("Math drill ambiguity must present ConflictCard", destination is RouterDestination.ConflictCard)
        val card = destination as RouterDestination.ConflictCard
        assertTrue("Option 1 must be Task Creation", card.options[0].label.contains("Add as Todo Task"))
        assertTrue("Option 1 command must be /create", card.options[0].command.startsWith("/create"))
    }

    @Test
    fun testAppBlockAmbiguityPresentsTaskFirst() {
        val destination = AyvaIntentRouter.route("block instagram after 6pm")
        assertTrue("App block ambiguity must present ConflictCard", destination is RouterDestination.ConflictCard)
        val card = destination as RouterDestination.ConflictCard
        assertTrue("Option 1 must be Task Creation", card.options[0].label.contains("Add as Todo Task"))
        assertTrue("Option 1 command must be /create", card.options[0].command.startsWith("/create"))
    }

    @Test
    fun testSummaryAmbiguityPresentsTaskFirst() {
        val destination = AyvaIntentRouter.route("review quarterly presentation")
        assertTrue("Summary ambiguity must present ConflictCard", destination is RouterDestination.ConflictCard)
        val card = destination as RouterDestination.ConflictCard
        assertTrue("Option 1 must be Task Creation", card.options[0].label.contains("Add as Todo Task"))
        assertTrue("Option 1 command must be /create", card.options[0].command.startsWith("/create"))
    }

    @Test
    fun testDuplicateTaskConflictPresentsTaskFirst() {
        val existingTasks = listOf(
            Task(id = 1, title = "Buy groceries", type = TaskType.TASK)
        )
        val destination = AyvaIntentRouter.route("add buy groceries", pendingTasks = existingTasks)
        assertTrue("Duplicate task must present ConflictCard", destination is RouterDestination.ConflictCard)
        val card = destination as RouterDestination.ConflictCard
        assertTrue("Option 1 must be Add as New Task", card.options[0].label.contains("Add as New Task"))
        assertTrue("Option 1 command must be /create", card.options[0].command.startsWith("/create"))
    }

    @Test
    fun testActionableQuestionPresentsTaskFirst() {
        val destination = AyvaIntentRouter.route("What to buy for dinner tonight?")
        assertTrue("Actionable question must present ConflictCard", destination is RouterDestination.ConflictCard)
        val card = destination as RouterDestination.ConflictCard
        assertTrue("Option 1 must be Task Creation", card.options[0].label.contains("Add as Todo Task"))
        assertTrue("Option 1 command must be /create", card.options[0].command.startsWith("/create"))
    }
}
