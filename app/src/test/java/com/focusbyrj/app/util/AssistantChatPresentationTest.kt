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
import com.focusbyrj.app.ui.screens.toPersistedChatMessage
import com.focusbyrj.app.ui.screens.toChatMessage
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
class AssistantChatPresentationTest {
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
    }
    @After fun tearDown() { AssistantSessionStore(context).clear(); db.close() }
    private suspend fun add(title: String) = db.taskDao().insertTask(Task(title = title, dueDate = System.currentTimeMillis() + 172_800_000L))
    private suspend fun say(text: String) = OfflineAssistant.respond(text, context) ?: error("Not handled: $text")
    private suspend fun reference(id: Long) = OfflineAssistant.availableReferences(context).first { it.kind == "task" && it.id == id }

    @Test fun receiptIsFrozenAfterLaterTaskEditsAndTopicSwitch() = runBlocking {
        val first = add("Review proposal"); val second = add("Call Dad")
        OfflineAssistant.selectReference(context, reference(first))
        val response = say("move it to tomorrow at 10am")
        val before = AssistantPresentation.data(response.jsonPayload)!!.getJSONObject("receipt").toString()
        val task = db.taskDao().getTaskById(first)!!
        db.taskDao().updateTask(task.copy(title = "Edited outside chat", dueDate = task.dueDate!! + 86_400_000L, updatedAt = task.updatedAt + 1))
        OfflineAssistant.selectReference(context, reference(second))
        assertEquals(before, AssistantPresentation.data(response.jsonPayload)!!.getJSONObject("receipt").toString())
        assertEquals(first, AssistantPresentation.replyTarget(response.jsonPayload)!!.id)
        assertEquals(second, OfflineAssistant.chatState(context).reference!!.id)
    }

    @Test fun referenceProjectionDoesNotExtendSessionAndExpiryHidesActions() = runBlocking {
        val id = add("Read")
        OfflineAssistant.selectReference(context, reference(id))
        val stamp = AssistantSessionStore(context).load().touchedAt
        OfflineAssistant.chatState(context); OfflineAssistant.chatState(context)
        assertEquals(stamp, AssistantSessionStore(context).load().touchedAt)
        val oldStore = AssistantSessionStore(context) { System.currentTimeMillis() - 900_001L }
        oldStore.save(AssistantSession(taskId = id, undoId = "old", confirmationId = "old"))
        val state = OfflineAssistant.chatState(context)
        assertNull(state.reference); assertFalse(state.allows("/ayva confirm old")); assertFalse(state.allows("/ayva undo old"))
    }

    @Test fun releasingReferenceCancelsPendingWithoutChangingTasks() = runBlocking {
        val id = add("Read")
        OfflineAssistant.selectReference(context, reference(id))
        say("reschedule it")
        assertNotNull(AssistantSessionStore(context).load().expected)
        val task = db.taskDao().getTaskById(id)
        OfflineAssistant.releaseReference(context)
        assertNull(OfflineAssistant.chatState(context).reference)
        say("yes")
        assertEquals(task, db.taskDao().getTaskById(id))
        assertNull(AssistantSessionStore(context).load().expected)
    }

    @Test fun replyingToCurrentDateQuestionKeepsItsPendingSlot() = runBlocking {
        val id = add("Review proposal")
        OfflineAssistant.selectReference(context, reference(id))
        val question = say("reschedule it")
        val ref = AssistantPresentation.replyTarget(question.jsonPayload)!!
        assertEquals(id, ref.id)
        assertNotNull(AssistantPresentation.reference(question.jsonPayload))
        assertNotNull(AssistantSessionStore(context).load().expected)
        OfflineAssistant.respond("tomorrow at 10am", context, replyTarget = ref, quotedReply = question.formattedText)
        assertNotEquals(AssistantPresentation.reference(question.jsonPayload)!!.detail, AssistantPresentation.due(db.taskDao().getTaskById(id)!!.dueDate))
    }

    @Test fun selectingHistoricalReferenceDoesNotReviveAnotherQuestion() = runBlocking {
        val first = add("Review proposal"); val second = add("Call Dad")
        OfflineAssistant.selectReference(context, reference(second)); say("reschedule it")
        val before = db.taskDao().getTaskById(first)
        OfflineAssistant.respond("yes", context, replyTarget = reference(first), quotedReply = "An older reply")
        assertNull(AssistantSessionStore(context).load().expected)
        assertEquals(before, db.taskDao().getTaskById(first))
        assertEquals(first, OfflineAssistant.chatState(context).reference!!.id)
    }

    @Test fun invalidReferenceCannotRedirectAFollowup() = runBlocking {
        val id = add("Read")
        OfflineAssistant.selectReference(context, reference(id))
        assertFalse(OfflineAssistant.selectReference(context, AssistantItemReference("task", Long.MAX_VALUE, "Forged title")))
        assertEquals(id, OfflineAssistant.chatState(context).reference!!.id)
        assertFalse(OfflineAssistant.selectReference(context, AssistantItemReference("unexpected", id, "Read")))
    }

    @Test fun undoReceiptTargetsOriginalItemAfterSwitchingTopics() = runBlocking {
        val first = add("Review proposal"); val second = add("Call Dad")
        OfflineAssistant.selectReference(context, reference(first)); val before = db.taskDao().getTaskById(first)!!.dueDate
        val response = say("move it to tomorrow at 10am")
        val command = response.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva undo ") }.query
        OfflineAssistant.selectReference(context, reference(second))
        val undone = say(command)
        assertEquals(before, db.taskDao().getTaskById(first)!!.dueDate)
        assertEquals(first, AssistantPresentation.reference(undone.jsonPayload)!!.id)
        assertEquals(second, OfflineAssistant.chatState(context).reference!!.id)
        assertFalse(OfflineAssistant.chatState(context).allows(command))
    }

    @Test fun quotedReminderQuestionKeepsTheUnfinishedCreationDraft() = runBlocking {
        val question = say("remind me tomorrow at 10am")
        assertEquals("task_title", AssistantSessionStore(context).load().expected)
        OfflineAssistant.respond("buy paper", context, quotedReply = question.formattedText)
        val created = db.taskDao().getAllActiveTasksList()
        assertEquals(1, created.size)
        assertTrue(created.single().title.contains("paper", ignoreCase = true))
        assertNotNull(created.single().dueDate)
    }

    @Test fun unavailableQuotedTargetDoesNotMutateTheActiveTask() = runBlocking {
        val id = add("Review proposal")
        OfflineAssistant.selectReference(context, reference(id))
        val before = db.taskDao().getTaskById(id)
        val response = OfflineAssistant.respond("mark it done", context,
            replyTarget = AssistantItemReference("task", Long.MAX_VALUE, "Missing"), quotedReply = "Old reply")!!
        assertTrue(response.formattedText.contains("no longer available"))
        assertEquals(before, db.taskDao().getTaskById(id))
        assertNull(AssistantPresentation.data(response.jsonPayload)?.optJSONObject("receipt"))
    }

    @Test fun habitSelectionCannotReuseATaskWithTheSameNumericId() = runBlocking {
        val taskId = add("Review proposal")
        val habitId = db.habitDao().insertHabit(com.focusbyrj.app.data.Habit(title = "Water", targetPerDay = 8))
        OfflineAssistant.selectReference(context, reference(taskId)); say("reschedule it")
        val habit = OfflineAssistant.availableReferences(context).first { it.kind == "habit" && it.id == habitId }
        assertTrue(OfflineAssistant.selectReference(context, habit))
        val state = OfflineAssistant.chatState(context)
        assertEquals("habit", state.reference!!.kind)
        assertNull(AssistantSessionStore(context).load().taskId)
        assertNull(AssistantSessionStore(context).load().expected)
        say("done")
        assertFalse(db.taskDao().getTaskById(taskId)!!.isCompleted)
        db.habitDao().archiveHabit(habitId)
        assertFalse(OfflineAssistant.selectReference(context, habit))
    }

    @Test fun internalActionTokensHaveFriendlyDisplayLabelsWithoutChangingTheirQuery() = runBlocking {
        val id = add("Review proposal")
        OfflineAssistant.selectReference(context, reference(id))
        val response = say("move it to tomorrow at 10am")
        val undo = response.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva undo ") }
        val label = AssistantPresentation.actionLabel(response.jsonPayload, undo.query)
        assertNotNull(label); assertFalse(label!!.contains("/ayva")); assertFalse(label.contains(undo.query.substringAfterLast(' ')))
        assertTrue(OfflineAssistant.chatState(context).allows(undo.query))
        assertNull(AssistantPresentation.actionLabel(response.jsonPayload, "unrelated command"))
    }

    @Test fun newReminderQuestionDoesNotClaimThePreviousTaskAsItsTarget() = runBlocking {
        val previous = add("Review proposal")
        OfflineAssistant.selectReference(context, reference(previous))
        val question = say("remind me tomorrow at 10am")
        assertNull(OfflineAssistant.chatState(context).reference)
        assertNull(AssistantPresentation.replyTarget(question.jsonPayload))
        OfflineAssistant.respond("buy paper", context, quotedReply = question.formattedText)
        val active = OfflineAssistant.chatState(context).reference!!
        assertNotEquals(previous, active.id)
        assertTrue(active.title.contains("paper", ignoreCase = true))
        assertFalse(db.taskDao().getTaskById(previous)!!.isCompleted)
    }

    @Test fun quotedTextAndTargetSurviveExistingMessageConversion() {
        val reference = AssistantItemReference("task", 7L, "Original title", "Original time")
        val payload = AssistantPresentation.withReply("Original reply", reference, "original")
        val restored = com.focusbyrj.app.ui.screens.ChatMessage("reply", "Move it", true, talkActionJson = payload).toPersistedChatMessage().toChatMessage()
        assertEquals("Original reply", AssistantPresentation.replyReference(restored.talkActionJson)!!.getString("text"))
        assertEquals(reference, AssistantItemReference.fromJson(AssistantPresentation.replyReference(restored.talkActionJson)!!.getJSONObject("reference")))
    }
}
