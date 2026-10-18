package com.focusbyrj.app.router

import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.ChatConversationContext
import com.focusbyrj.app.util.ChatTaskDraft
import com.focusbyrj.app.util.NluIntent
import com.focusbyrj.app.util.OfflineNluEngine
import com.focusbyrj.app.util.command.AyvaChatCommands
import com.focusbyrj.app.util.router.AyvaIntentRouter
import com.focusbyrj.app.util.router.RouterDestination
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AyvaChatConversationTest {
    @Test fun politeTaskRequestsNeedNoSlash() {
        for (query in listOf("buy milk tomorrow at 6pm", "Could you remind me to buy milk tomorrow at 6pm?", "Hey Ayva, please add a task to buy milk tomorrow at 6pm")) {
            val destination = AyvaIntentRouter.route(query) as RouterDestination.TaskCreation
            assertEquals(query, "Buy milk", destination.title)
            assertNotNull(query, destination.dueDate)
        }
    }

    @Test fun featureWordsInsideAnExplicitTaskDoNotHijackCreation() {
        for (query in listOf("remind me to book an appointment tomorrow", "add task review app notes tomorrow", "remind me to plan morning routine tomorrow")) {
            assertTrue(query, AyvaIntentRouter.route(query) is RouterDestination.TaskCreation)
        }
    }

    @Test fun questionsAndConversationCannotSilentlyCreateTasks() {
        for (query in listOf("I feel tired today", "I am stressed about math", "thanks", "yes", "okay", "cancel", "how does arithmetic drill work?", "how do I earn XP points?", "why are apps not blocking?", "tomorrow at 6pm")) {
            assertTrue(query, AyvaIntentRouter.route(query) is RouterDestination.TalkQuery)
            assertEquals(query, NluIntent.UNKNOWN, OfflineNluEngine.parse(query, emptyList()).intent)
        }
    }

    @Test fun appStateQueriesDoNotBecomeTasks() {
        for (query in listOf("check permissions", "read my notes", "check status", "help", "focus status", "screen time")) {
            assertFalse(query, AyvaIntentRouter.route(query) is RouterDestination.TaskCreation)
        }
    }

    @Test fun taskListAliasesStillListTasks() {
        for (query in listOf("task", "todo", "todo list", "tasks", "/task", "/task all", "/tasks all")) {
            assertTrue(query, AyvaIntentRouter.route(query) is RouterDestination.DirectCommand)
        }
    }

    @Test fun bareTitleCanStillBeAddedWithoutTypingACommand() {
        val choice = AyvaIntentRouter.route("Quarterly presentation") as RouterDestination.ConflictCard
        assertEquals("Add as Todo Task", choice.options.first().label)
        val draft = AyvaIntentRouter.route(choice.options.first().command) as RouterDestination.TaskCreation
        assertEquals("Quarterly presentation", draft.title)
    }

    @Test fun duplicateChoicesCarryStableIdentityAndCanResolve() {
        val task = Task(id = 81, title = "Buy milk")
        val duplicate = AyvaIntentRouter.route("buy milk tomorrow", listOf(task)) as RouterDestination.ConflictCard
        assertTrue(AyvaIntentRouter.route(duplicate.options[0].command, listOf(task)) is RouterDestination.TaskCreation)
        val completeQuery = AyvaChatCommands.talkQuery(duplicate.options[1].command)
        val parsed = OfflineNluEngine.parse(completeQuery, listOf(Task(id = 4, title = "Other"), task))
        assertEquals(NluIntent.COMPLETE, parsed.intent)
        assertEquals(81L, parsed.targetTask?.id)
    }

    @Test fun selectingOneOfSeveralMatchingTasksDoesNotLoop() {
        val tasks = listOf(Task(id = 78, title = "Gym Leg Day"), Task(id = 90, title = "Gym Chest Day"))
        val choices = OfflineNluEngine.parse("reschedule gym to 6pm", tasks).conflictOptions
        val query = AyvaChatCommands.talkQuery(choices.first { it.label.contains("Leg Day") }.command)
        val selected = OfflineNluEngine.parse(query, tasks.reversed())
        assertEquals(NluIntent.RESCHEDULE, selected.intent)
        assertEquals(78L, selected.targetTask?.id)
    }

    @Test fun slashAliasesPreserveVerbAndArguments() {
        assertEquals("block Instagram in soft mode", AyvaChatCommands.talkQuery("/block Instagram in soft mode"))
        assertEquals("unblock YouTube", AyvaChatCommands.talkQuery("/unblock YouTube"))
        assertEquals("vacation on", AyvaChatCommands.talkQuery("/vacation on"))
        assertEquals("reschedule id:88 tomorrow", AyvaChatCommands.talkQuery("/talk reschedule id:88 tomorrow"))
        assertEquals("start routine Morning", AyvaChatCommands.talkQuery("/routine start Morning"))
        assertEquals("/talk habit done Reading", (AyvaIntentRouter.route("/habit done Reading") as RouterDestination.DirectCommand).command)
    }

    @Test fun naturalAppActionsRetainModeAndDuration() {
        val routed = AyvaIntentRouter.route("block YouTube in soft mode for 30 minutes") as RouterDestination.DirectCommand
        assertEquals("block YouTube in soft mode for 30 minutes", AyvaChatCommands.talkQuery(routed.command))
    }

    @Test fun taskDraftKeepsRecurrenceAndComposerFlagsAcrossAliases() {
        val natural = ChatTaskDraft.parse("remind me to take medicine every day at 8am", true, true)!!
        val slash = ChatTaskDraft.parse("/create remind me to take medicine every day at 8am", true, true)!!
        assertEquals("Take medicine", natural.title)
        assertEquals(natural.title, slash.title)
        assertEquals(RecurrencePattern.DAILY, natural.recurrence)
        assertEquals(natural.recurrence, slash.recurrence)
        assertTrue(natural.isPriority && natural.isPersistent && slash.isPriority && slash.isPersistent)
        assertEquals(RouterDestination.DirectCommand("/talk remind me to take medicine every day at 8am"), AyvaIntentRouter.route("/talk remind me to take medicine every day at 8am"))
    }

    @Test fun taskTitlePrefixMustBeACompleteWord() {
        assertEquals("Address book", ChatTaskDraft.parse("/create address book")!!.title)
        assertEquals("Taskforce briefing", ChatTaskDraft.parse("/create Taskforce briefing")!!.title)
    }

    @Test fun missingTaskTitleNeverCreatesAPlaceholder() {
        for (query in listOf("add", "remind me to", "/create", "add task tomorrow")) {
            assertNull(query, ChatTaskDraft.parse(query))
            assertFalse(query, AyvaIntentRouter.route(query) is RouterDestination.TaskCreation)
        }
    }

    @Test fun taskFollowUpsSurviveListReordering() {
        val session = ChatConversationContext()
        session.focusTask(55)
        val tasks = listOf(Task(id = 10, title = "Other"), Task(id = 55, title = "Buy milk"))
        val resolved = session.resolve("move it to tomorrow at 6pm", tasks)
        val parsed = OfflineNluEngine.parse(resolved, tasks.reversed())
        assertEquals(55L, parsed.targetTask?.id)
        assertEquals(NluIntent.RESCHEDULE, parsed.intent)
        assertNotNull(parsed.targetDateMs)
        assertEquals("complete id:55", session.resolve("done", tasks))
    }

    @Test fun freeTextDateAnswersReschedulePrompt() {
        val session = ChatConversationContext()
        val tasks = listOf(Task(id = 55, title = "Buy milk"))
        session.focusTask(55, requestDate = true)
        assertEquals("reschedule id:55 tomorrow at 6pm", session.resolve("tomorrow at 6pm", tasks))
        assertEquals("reschedule id:55 friday at 4pm", session.resolve("actually Friday at 4pm", tasks))
    }

    @Test fun unrelatedMessageCannotBeInterpretedAsDateAnswer() {
        val session = ChatConversationContext()
        val tasks = listOf(Task(id = 55, title = "Buy milk"))
        session.focusTask(55, requestDate = true)
        assertEquals("I feel tired tomorrow", session.resolve("I feel tired tomorrow", tasks))
        assertEquals("buy bread tomorrow", session.resolve("buy bread tomorrow", tasks))
    }

    @Test fun cancelledExpiredOrRemovedTaskReferencesAreNotReused() {
        var now = 100L
        val session = ChatConversationContext { now }
        val tasks = listOf(Task(id = 55, title = "Buy milk"))
        session.focusTask(55)
        now += 600_001
        assertEquals("complete it", session.resolve("complete it", tasks))
        session.focusTask(55)
        assertEquals("complete it", session.resolve("complete it", emptyList()))
        session.focusTask(55)
        session.resolve("cancel", tasks)
        assertEquals("complete it", session.resolve("complete it", tasks))
    }

    @Test fun callInATitleDoesNotSelectAllTasks() {
        val tasks = listOf(Task(id = 55, title = "Call mom"), Task(id = 88, title = "Buy milk"))
        val result = OfflineNluEngine.parse("complete Call mom", tasks)
        assertFalse(result.isAllTasks)
        assertEquals(55L, result.targetTask?.id)
    }

    @Test fun missingStableIdNeverCreatesOrSelectsAnotherTask() {
        val tasks = listOf(Task(id = 55, title = "Buy milk"))
        val result = OfflineNluEngine.parse("reschedule id:99 tomorrow", tasks)
        assertEquals(NluIntent.RESCHEDULE, result.intent)
        assertNull(result.targetTask)
        assertFalse(result.isAllTasks)
    }
}
