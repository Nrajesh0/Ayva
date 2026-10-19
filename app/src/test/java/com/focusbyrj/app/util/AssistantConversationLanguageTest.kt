package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.assistant.*
import com.focusbyrj.app.util.router.AyvaIntentRouter
import com.focusbyrj.app.util.router.RouterDestination
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AssistantConversationLanguageTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    @Before fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        AssistantSessionStore(context).clear()
        for (name in listOf("focus_prefs", "bubble_prefs", "ayva_action_receipts", "ayva_personalization")) context.getSharedPreferences(name, 0).edit().clear().commit()
        FocusEconomyManager.init(context)
        AyvaTalkEngine.resetConversation()
    }
    @After fun tearDown() { AssistantSessionStore(context).clear(); db.close(); AyvaTalkEngine.resetConversation() }
    private suspend fun say(text: String) = OfflineAssistant.respond(text, context) ?: error("Not handled: $text")
    private fun state() = AssistantSessionStore(context).load()
    private suspend fun add(title: String, priority: Boolean = false) = db.taskDao().insertTask(Task(title = title, isPriority = priority))
    private suspend fun task(id: Long) = db.taskDao().getTaskById(id)!!
    private suspend fun select(id: Long) { say("let's work on id:$id") }
    private fun confirm(response: AyvaTalkEngine.TalkResponse) = response.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva confirm ") }.query

    @Test fun examplesHypotheticalsAndTyposCannotReplayDeletion() = runBlocking {
        val id = add("Send report")
        for (input in listOf("If I delete all tasks what happens?", "Suppose complete all tasks", "My friend said delete all tasks", "\"delete all tasks\"", "I should delete all tasks", "Here is an example: delete all tasks", "The instruction says complete all tasks", "deleet all tasks")) {
            val old = confirm(say("delete all tasks"))
            assertTrue(input, AyvaIntentRouter.route(input) is RouterDestination.TalkQuery)
            say(input); say(old); say("yes")
            assertFalse(input, task(id).isTrashed)
            assertFalse(input, task(id).isCompleted)
            assertEquals(NluIntent.UNKNOWN, OfflineNluEngine.parse(input, listOf(task(id))).intent)
        }
    }
    @Test fun legacyKeywordDescriptionsCannotGrantWriteAuthority() {
        val writes = setOf(NluIntent.CLEAR_CHAT, NluIntent.START_ROUTINE, NluIntent.STOP_ROUTINE, NluIntent.BLOCK_APP, NluIntent.UNBLOCK, NluIntent.COMPLETE, NluIntent.DELETE, NluIntent.RESCHEDULE)
        for (input in listOf("the chat is clear", "the routine has a fresh start", "routine at the end of the day", "social apps allow conversation", "the report is done")) {
            assertFalse(input, OfflineNluEngine.classifyIntent(input) in writes)
            assertFalse(input, AyvaIntentRouter.route(input) == RouterDestination.DirectCommand("/clear"))
        }
        assertEquals(NluIntent.CLEAR_CHAT, OfflineNluEngine.classifyIntent("clear chat"))
        assertEquals(NluIntent.START_ROUTINE, OfflineNluEngine.classifyIntent("start routine Morning"))
    }

    @Test fun unknownAppInfoCannotLeaveImplicitTaskMutationTarget() = runBlocking {
        val id = add("Keep report")
        select(id)
        say("/talk zqxv plom")
        assertNull(state().taskId)
        say("complete it")
        assertFalse(task(id).isCompleted)
    }

    @Test fun ambiguousDatedStatementRequiresChoiceButExplicitCaptureStillWorks() = runBlocking {
        val input = "Dad will visit tomorrow"
        assertTrue(AyvaIntentRouter.route(input) is RouterDestination.ConflictCard)
        assertEquals(NluIntent.CONFLICT, OfflineNluEngine.parse(input, emptyList()).intent)
        val clarification = say(input)
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        assertTrue(clarification.actions.filterIsInstance<TalkAction.AskQuery>().isNotEmpty())
        say("buy milk tomorrow")
        assertEquals("buy milk", db.taskDao().getAllActiveTasksList().single().title.lowercase())
    }

    @Test fun titleContentsRemainLiteralAfterExplicitCapture() = runBlocking {
        say("remind me to imagine delete all tasks tomorrow")
        assertTrue(db.taskDao().getAllActiveTasksList().single().title.contains("imagine delete all tasks", true))
    }
    @Test fun externalEditWithUnchangedTimestampInvalidatesPreview() = runBlocking {
        val id = add("Send report")
        val old = confirm(say("delete all tasks"))
        db.taskDao().updateTask(task(id).copy(title = "Updated externally"))
        say(old)
        assertFalse(task(id).isTrashed)
    }
    @Test fun authorityExpiresEvenWhenSessionIsKeptAlive() = runBlocking {
        val id = add("Send report"); select(id)
        val old = confirm(say("delete all tasks"))
        val snapshot = state(); snapshot.referenceAt = System.currentTimeMillis() - 900_001; snapshot.confirmationAt = snapshot.referenceAt
        AssistantSessionStore(context).save(snapshot)
        say(old); say("complete it")
        assertFalse(task(id).isTrashed); assertFalse(task(id).isCompleted)
    }

    @Test fun politeCreationIsConsistentAcrossRouterAndCoordinator() = runBlocking {
        val input = "Could you please add task buy milk tomorrow at 6pm?"
        val route = AyvaIntentRouter.route(input) as RouterDestination.TaskCreation
        say(input)
        val actual = db.taskDao().getAllActiveTasksList().single()
        assertEquals(route.title, actual.title); assertEquals(route.dueDate, actual.dueDate)
    }
    @Test fun wouldYouMindUsesExistingTarget() = runBlocking {
        val id = add("Read chapter"); select(id)
        say("Would you mind moving it to Friday at 9am?")
        assertNotNull(task(id).dueDate); assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun explicitTaskContentsDoNotBecomeCommands() = runBlocking {
        say("I'd like you to remind me to not delete files tomorrow at 6pm")
        assertTrue(db.taskDao().getAllActiveTasksList().single().title.contains("not delete files", true))
    }
    @Test fun negationInvalidatesPriorConfirmation() = runBlocking {
        val id = add("Read chapter"); select(id)
        val token = confirm(say("delete all tasks"))
        say("Would you mind not deleting it?"); say(token)
        assertFalse(task(id).isTrashed); assertNull(state().confirmationId)
    }
    @Test fun tentativeActionsAskInsteadOfMutating() = runBlocking {
        val id = add("Read chapter"); select(id)
        val reply = say("Maybe delete it")
        assertTrue(reply.formattedText.contains("how that works")); say("yes")
        assertFalse(task(id).isTrashed)
    }
    @Test fun guardsApplyToRouterAndNlu() {
        for (text in listOf("don't delete all tasks", "perhaps complete all tasks", "I'm thinking about deleting all tasks")) {
            assertTrue(AyvaIntentRouter.route(text) is RouterDestination.TalkQuery)
            assertEquals(NluIntent.UNKNOWN, OfflineNluEngine.parse(text, emptyList()).intent)
        }
    }
    @Test fun guidanceRequestsAreQuestionsRatherThanActions() = runBlocking {
        val id = add("Read chapter"); select(id)
        for (input in listOf("Help me delete it", "Would you mind explaining how to delete it?", "Tell me how to delete it")) {
            assertTrue(OfflineNluEngine.isQuestion(OfflineNluEngine.normalizeRequest(input)))
            AyvaTalkEngine.answerTalkQueryWithActions(input, context)
            assertFalse(task(id).isTrashed); assertNull(state().confirmationId)
        }
    }
    @Test fun thanksDoesNotFillMissingReminderTitle() = runBlocking {
        say("remind me tomorrow at 6pm"); say("Thanks, Ayva!")
        assertEquals("task_title", state().expected); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        say("buy paper")
        assertTrue(db.taskDao().getAllActiveTasksList().single().title.contains("paper", true))
    }
    @Test fun courtesyKeepsPreviewButDoesNotConfirmIt() = runBlocking {
        val id = add("Read chapter"); select(id)
        val preview = say("delete all tasks"); val token = confirm(preview)
        assertEquals(token, confirm(say("thanks"))); assertFalse(task(id).isTrashed)
        say("yes, thanks"); assertTrue(task(id).isTrashed)
    }
    @Test fun waitKeepsPreviewWithoutApplyingIt() = runBlocking {
        val id = add("Read chapter"); select(id)
        val token = confirm(say("delete all tasks"))
        assertEquals(token, confirm(say("not yet"))); assertFalse(task(id).isTrashed)
        say(token); assertTrue(task(id).isTrashed)
    }
    @Test fun preferenceDoesNotFillReminderDraft() = runBlocking {
        say("remind me tomorrow at 6pm"); say("be concise"); say("no emoji")
        assertEquals("task_title", state().expected); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        say("buy paper"); assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun emojiAndDetailPreferencesAreIndependent() = runBlocking {
        say("no emoji"); assertFalse(AssistantReplyStyle.emoji(context)); assertFalse(AssistantReplyStyle.concise(context))
        val detailed = say("help").formattedText
        assertFalse(detailed.contains("💬"))
        say("be concise"); val brief = say("help").formattedText
        assertTrue(brief.lines().size < detailed.lines().size)
        say("use emojis"); assertTrue(AssistantReplyStyle.concise(context)); assertTrue(say("help").formattedText.contains("💬"))
    }
    @Test fun noEmojiNeverRemovesUserTitleCharacters() = runBlocking {
        say("no emoji"); say("add task Read 📚 tomorrow")
        assertTrue(db.taskDao().getAllActiveTasksList().single().title.contains("📚"))
    }
    @Test fun explicitSlashCanCreateLiteralCourtesyTitle() = runBlocking {
        say("/create thanks")
        assertEquals("Thanks", db.taskDao().getAllActiveTasksList().single().title)
    }
    @Test fun unknownUsesFocusedFallbackAndConversationTopic() = runBlocking {
        val response = AyvaTalkEngine.answerTalkQueryWithActions("zqxv plom", context)
        assertEquals(2, response.actions.size); assertTrue(response.formattedText.contains("didn't catch"))
        assertEquals("conversation", state().topic)
    }
    @Test fun repeatedUnknownOffersSpecificExamples() = runBlocking {
        AyvaTalkEngine.answerTalkQueryWithActions("zqxv plom", context)
        val response = AyvaTalkEngine.answerTalkQueryWithActions("zqxv plom", context)
        assertTrue(response.formattedText.contains("Try one specific request"))
    }
    @Test fun fallbackCanResumeAnUnfinishedQuestion() = runBlocking {
        say("remind me tomorrow at 6pm"); say("what time is it")
        val response = AyvaTalkEngine.answerTalkQueryWithActions("zqxv plom", context)
        val resume = response.actions.filterIsInstance<TalkAction.AskQuery>().first { it.label == "Continue the request" }
        say(resume.query); assertEquals("task_title", state().expected)
        say("buy paper"); assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun expiredQuestionDoesNotOfferResume() = runBlocking {
        say("remind me tomorrow at 6pm"); say("what time is it")
        val snapshot = state(); snapshot.suspendedRequest!!.put("savedAt", System.currentTimeMillis() - 900_001)
        AssistantSessionStore(context).save(snapshot)
        val response = OfflineAssistant.clarifyUnknown(context, "zqxv plom")
        assertFalse(response.actions.filterIsInstance<TalkAction.AskQuery>().any { it.label == "Continue the request" })
    }
    @Test fun guidedDateEditRetainsTargetThroughUnclearAnswer() = runBlocking {
        val id = add("Read chapter"); select(id)
        say("change it"); assertEquals("task_edit_field", state().expected)
        say("date"); assertEquals("task_date", state().expected)
        say("after lunch"); assertEquals("task_date", state().expected); assertNull(task(id).dueDate)
        say("tomorrow at 8am"); assertNotNull(task(id).dueDate)
    }
    @Test fun guidedRenameRequiresConfirmationAndKeepsOtherFields() = runBlocking {
        val id = add("Read chapter", true); val before = task(id); select(id)
        say("change it"); say("title"); say("Call Dad")
        assertEquals(before, task(id)); assertEquals("confirm", state().expected)
        say("yes"); assertEquals("Call Dad", task(id).title); assertTrue(task(id).isPriority)
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun guidedNormalPriorityAcceptsNoAsValueThenAsksConfirmation() = runBlocking {
        val id = add("Read chapter", true); select(id)
        say("edit it"); say("priority"); say("no")
        assertEquals("confirm", state().expected); assertTrue(task(id).isPriority)
        say("yes"); assertFalse(task(id).isPriority)
    }
    @Test fun guidedHighPriorityDoesNotTreatYesAsPrematureConfirmation() = runBlocking {
        val id = add("Read chapter"); select(id)
        say("edit it"); say("priority"); say("yes")
        assertEquals("confirm", state().expected); assertFalse(task(id).isPriority)
        say("yes"); assertTrue(task(id).isPriority)
    }
    @Test fun courtesyDuringEditDoesNotRenameTask() = runBlocking {
        val id = add("Read chapter"); select(id)
        say("edit it"); say("title"); say("great")
        assertEquals("task_new_title", state().expected); assertEquals("Read chapter", task(id).title)
        say("\"Great\""); say("yes"); assertEquals("Great", task(id).title)
    }
    @Test fun editQuestionCanResumeAfterInterruption() = runBlocking {
        val id = add("Read chapter"); select(id)
        say("change it"); say("title"); say("what time is it"); say("back to that reminder")
        assertEquals("task_new_title", state().expected)
        say("Read fiction"); say("yes"); assertEquals("Read fiction", task(id).title)
    }
    @Test fun editRejectsExternallyChangedTarget() = runBlocking {
        val id = add("Read chapter"); select(id); say("edit it"); say("title")
        db.taskDao().updateTask(task(id).copy(title = "External", updatedAt = task(id).updatedAt + 1))
        say("New name"); say("yes"); assertEquals("External", task(id).title)
    }
    @Test fun editingGroupKeepsAllSelectedIds() = runBlocking {
        val a = add("One"); val b = add("Two"); val c = add("Three")
        say("show my tasks"); say("let's work on the first two tasks")
        say("change them"); say("priority"); say("high"); say("yes")
        assertTrue(task(a).isPriority && task(b).isPriority); assertFalse(task(c).isPriority)
    }
    @Test fun quotedRenameIsLiteralEvenWithSlashAndSeparator() = runBlocking {
        val id = add("Read chapter"); select(id)
        say("edit it"); say("title"); say("\"Finish; /clear\""); say("yes")
        assertEquals("Finish; /clear", task(id).title)
    }
    @Test fun qualifiedYesReplacesPreviewInsteadOfConfirmingOldAction() = runBlocking {
        val id = add("Read chapter"); select(id); val old = confirm(say("delete all tasks"))
        say("yes but move it to Friday at 9am"); say(old)
        assertFalse(task(id).isTrashed); assertNotNull(task(id).dueDate)
    }
    @Test fun unknownEditTargetCannotBecomeNewTask() = runBlocking {
        say("fix it"); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }
    @Test fun courtesyDoesNotSelectAmbiguousTask() = runBlocking {
        add("Read chapter one"); add("Read chapter two")
        say("delete Read chapter"); val choices = state().choices.size
        assertTrue(choices > 1); say("thanks"); assertEquals(choices, state().choices.size)
        assertTrue(db.taskDao().getAllActiveTasksList().none { it.isTrashed })
    }
    @Test fun taskActionInterruptsRenameInsteadOfBecomingItsTitle() = runBlocking {
        val id = add("Read chapter"); select(id)
        say("edit it"); say("title"); say("move it to Friday at 9am")
        assertEquals("Read chapter", task(id).title); assertNotNull(task(id).dueDate)
    }
    @Test fun noEmojiAppliesToNewSuggestionAndConfirmationIcons() = runBlocking {
        val id = add("Read chapter"); select(id); say("no emoji")
        assertTrue(say("help").actions.all { it.emoji.isEmpty() })
        assertTrue(OfflineAssistant.clarifyUnknown(context, "zqxv plom").actions.all { it.emoji.isEmpty() })
        select(id); assertTrue(say("edit it").actions.all { it.emoji.isEmpty() })
        say("priority"); assertTrue(say("high").actions.all { it.emoji.isEmpty() })
    }
    @Test fun ambiguousEditUsesTheChosenTaskId() = runBlocking {
        val a = add("One"); val b = add("Two")
        say("change my task"); assertTrue(state().choices.size > 1)
        val choice = state().choices.indexOfFirst { it.label == "Two" }
        say((choice + 1).toString()); say("priority"); say("high"); say("yes")
        assertFalse(task(a).isPriority); assertTrue(task(b).isPriority)
    }
    @Test fun literalFieldAnswersCannotBeRewrittenAsTyposOrHijackedBySettingHelp() = runBlocking {
        val id = add("Original"); select(id)
        say("edit it"); say("title"); say("\"soft mode wait\""); say("yes")
        assertEquals("soft mode wait", task(id).title)
        say("edit it"); say("title"); say("“Tommorow’s Report”"); say("yes")
        assertEquals("Tommorow’s Report", task(id).title)
        assertEquals("10", AssistantCapabilities.setting("soft_lock_duration").current(context))
    }
    @Test fun quotedReminderTitleKeepsDateWordsAndHeldReminderDate() = runBlocking {
        say("remind me tomorrow at 6pm")
        val due = state().pending.filterIsInstance<AssistantAction.CreateTask>().single().draft.dueDate
        say("\"Is tomorrow a good day?\"")
        val saved = db.taskDao().getAllActiveTasksList().single()
        assertEquals("Is tomorrow a good day?", saved.title); assertEquals(due, saved.dueDate)
    }

}
