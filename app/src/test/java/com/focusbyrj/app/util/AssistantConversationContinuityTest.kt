package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.util.assistant.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/** Complete conversations against Room, including interruptions and stale targets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AssistantConversationContinuityTest {
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
    private suspend fun say(text: String) = OfflineAssistant.respond(text, context) ?: error("Not handled: $text")
    private fun date(text: String) = SmartDateParser.parse(text).timestamp!!
    private suspend fun add(title: String, time: String? = null) = db.taskDao().insertTask(Task(title = title, dueDate = time?.let(::date)))
    private suspend fun task(id: Long) = db.taskDao().getTaskById(id)!!
    private fun state() = AssistantSessionStore(context).load()
    private fun command(response: AyvaTalkEngine.TalkResponse, verb: String) = response.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva $verb ") }.query
    private fun day(timestamp: Long) = Calendar.getInstance().apply { timeInMillis = timestamp }.let { listOf(it.get(Calendar.YEAR), it.get(Calendar.DAY_OF_YEAR)) }
    private fun hour(timestamp: Long) = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)

    @Test fun datePreviewCanKeepClocksAndReviseOneItemBeforeConfirmation() = runBlocking {
        val a = add("Read chapter", "tomorrow at 9am"); val b = add("Call Dad", "tomorrow at 4pm")
        val other = add("Other task", "in 10 days")
        val before = listOf(task(a), task(b)); val otherDue = task(other).dueDate
        say("show tomorrow's tasks")
        val first = say("move the first two to Friday at 10am")
        val oldToken = command(first, "confirm")
        say("keep their original times")
        val revised = say("Actually, leave the second one tomorrow")
        assertEquals(before, listOf(task(a), task(b)))
        say(oldToken)
        assertEquals(before, listOf(task(a), task(b)))
        say(command(revised, "confirm"))
        assertEquals(day(date("Friday")), day(task(a).dueDate!!)); assertEquals(9, hour(task(a).dueDate!!))
        assertEquals(day(date("tomorrow")), day(task(b).dueDate!!)); assertEquals(16, hour(task(b).dueDate!!))
        assertEquals(otherDue, task(other).dueDate)
        say("undo")
        assertEquals(before.map { it.dueDate }, listOf(task(a).dueDate, task(b).dueDate))
    }

    @Test fun ordinalGroupsKeepDisplayedOrderAfterDatabaseReordering() = runBlocking {
        val a = add("One", "tomorrow at 9am"); val b = add("Two", "tomorrow at 10am"); val c = add("Three", "tomorrow at 11am")
        say("show my tasks")
        db.taskDao().updateTask(task(c).copy(isPriority = true, updatedAt = task(c).updatedAt + 1))
        say("complete the first two")
        assertFalse(task(a).isCompleted); assertFalse(task(b).isCompleted)
        say("yes")
        assertTrue(task(a).isCompleted); assertTrue(task(b).isCompleted); assertFalse(task(c).isCompleted)
    }

    @Test fun missingMemberCannotSilentlyShrinkAGroup() = runBlocking {
        val a = add("One"); val b = add("Two")
        say("show my tasks")
        db.taskDao().updateTask(task(b).copy(isTrashed = true))
        val reply = say("complete the first two")
        assertTrue(reply.formattedText.contains("no longer available"))
        say("yes")
        assertFalse(task(a).isCompleted)
    }

    @Test fun bothRequiresExactlyTwoIdentifiedTasks() = runBlocking {
        repeat(3) { add("Item $it") }
        say("show my tasks")
        assertTrue(say("complete both").formattedText.contains("Which 2 tasks"))
        say("yes")
        assertTrue(db.taskDao().getAllActiveTasksList().none { it.isCompleted })
    }

    @Test fun ordinalWithoutDisplayedListAsksInsteadOfSortingTheDatabase() = runBlocking {
        val a = add("One"); add("Two")
        say("complete the first one")
        say("yes")
        assertFalse(task(a).isCompleted)
    }

    @Test fun groupSelectionSurvivesApplyingAnAction() = runBlocking {
        val a = add("One"); val b = add("Two"); val c = add("Three")
        say("show my tasks"); say("make the first two priority"); say("yes")
        assertEquals(listOf(a, b), state().selectedTaskIds)
        say("make them persistent"); say("yes")
        assertTrue(task(a).isPersistent && task(b).isPersistent)
        assertFalse(task(c).isPriority || task(c).isPersistent)
    }

    @Test fun selectingSeveralTasksDoesNotApplyAnAction() = runBlocking {
        val a = add("One"); val b = add("Two"); add("Three")
        say("show my tasks")
        say("let's work on the first two tasks")
        assertEquals(listOf(a, b), state().selectedTaskIds)
        assertTrue(db.taskDao().getAllActiveTasksList().none { it.isPriority })
        say("make both priority"); say("yes")
        assertTrue(task(a).isPriority && task(b).isPriority)
    }

    @Test fun explicitOrdinalPairsAndLastGroupsUseTheSameSnapshot() = runBlocking {
        val ids = (1..4).map { add("Item $it") }
        say("show my tasks"); say("make the second and fourth priority"); say("yes")
        assertEquals(listOf(ids[1], ids[3]), state().selectedTaskIds)
        assertFalse(task(ids[0]).isPriority || task(ids[2]).isPriority)
        say("make the last two persistent"); say("yes")
        assertTrue(task(ids[2]).isPersistent && task(ids[3]).isPersistent)
        assertFalse(task(ids[0]).isPersistent || task(ids[1]).isPersistent)
    }

    @Test fun implicitTaskGroupsDoNotCrossIntoHabitContext() = runBlocking {
        val a = add("One"); add("Two")
        db.habitDao().insertHabit(Habit(title = "Water", targetPerDay = 8))
        say("show my tasks"); say("log habit water")
        say("complete the first one"); say("complete both")
        assertFalse(task(a).isCompleted)
        say("back to that task list"); say("complete the first one")
        assertTrue(task(a).isCompleted)
    }

    @Test fun expiredListCannotBeUsedAfterUnrelatedActivityExtendsTheSession() = runBlocking {
        val a = add("One"); add("Two")
        say("show my tasks")
        val snapshot = state(); snapshot.taskListAt = System.currentTimeMillis() - 900_001
        AssistantSessionStore(context).save(snapshot)
        say("complete the first two"); say("yes")
        assertFalse(task(a).isCompleted)
    }

    @Test fun hiddenTwentyFirstTaskCannotBeSelectedByItsNumber() = runBlocking {
        val ids = (1..21).map { add("Item $it") }
        say("show my tasks")
        assertEquals(20, state().taskListIds.size)
        say("complete task 21"); say("yes")
        assertFalse(task(ids.last()).isCompleted)
    }

    @Test fun truncatedLongListCannotResolveLastOrAllOfThem() {
        val snapshot = AssistantSession(topic = "tasks")
        snapshot.rememberTaskList((1L..101L).toList())
        val tasks = (1L..101L).map { Task(id = it, title = "Item $it") }
        assertNotNull(AssistantTaskLanguage.select("last", tasks, snapshot).clarification)
        assertNotNull(AssistantTaskLanguage.select("them", tasks, snapshot).clarification)
        assertEquals(listOf(1L, 2L), AssistantTaskLanguage.select("first two", tasks, snapshot).tasks.map { it.id })
    }

    @Test fun previousTaskMeansTheEarlierTaskRatherThanTheCurrentOne() = runBlocking {
        val a = add("Read fiction"); val b = add("Call Dad")
        say("let's work on Read fiction"); say("let's work on Call Dad"); say("back to the task")
        assertEquals(a, state().taskId)
        assertFalse(task(b).isCompleted)
    }

    @Test fun descriptiveTaskReferenceCanSelectTheReadingTask() = runBlocking {
        val a = add("Read fiction"); add("Call Dad")
        say("let's work on the reading task")
        assertEquals(a, state().taskId)
    }

    @Test fun reminderAndSettingQuestionsCanBeResumedByTopic() = runBlocking {
        say("remind me tomorrow at 6pm")
        say("change reminder interval")
        say("what time is it")
        say("back to that reminder"); say("buy paper")
        assertEquals(18, hour(db.taskDao().getAllActiveTasksList().single().dueDate!!))
        say("back to the setting")
        assertEquals("setting_value", state().expected)
        say("30 minutes"); say("yes")
        assertNull(state().expected)
    }

    @Test fun resumingOneQuestionKeepsTheOtherLiveQuestion() = runBlocking {
        say("remind me tomorrow at 6pm"); say("change reminder interval")
        say("back to that reminder"); say("buy paper")
        say("back to the setting")
        assertEquals("setting_value", state().expected)
    }

    @Test fun groupDateQuestionCanResumeWithoutLosingItsTargets() = runBlocking {
        val a = add("One"); val b = add("Two"); val c = add("Three")
        say("show my tasks"); say("reschedule the first two")
        say("what time is it"); say("back to that reminder")
        say("Friday at 4pm")
        assertNull(task(a).dueDate)
        say("yes")
        assertNotNull(task(a).dueDate); assertNotNull(task(b).dueDate); assertNull(task(c).dueDate)
    }

    @Test fun resumedGroupQuestionRejectsAnExternallyChangedMember() = runBlocking {
        val a = add("One"); val b = add("Two")
        say("show my tasks"); say("reschedule the first two"); say("what time is it")
        db.taskDao().updateTask(task(b).copy(title = "Edited elsewhere", updatedAt = task(b).updatedAt + 1))
        assertTrue(say("back to that reminder").formattedText.contains("no unfinished"))
        say("yes")
        assertNull(task(a).dueDate)
    }

    @Test fun unfinishedNewReminderIsIndependentOfThePreviouslyFocusedTask() = runBlocking {
        val old = add("Old task")
        say("let's work on Old task"); say("remind me tomorrow at 6pm"); say("what time is it")
        db.taskDao().updateTask(task(old).copy(isCompleted = true))
        say("back to that reminder"); say("buy paper")
        assertEquals(1, db.taskDao().getAllActiveTasksList().count { !it.isCompleted })
    }

    @Test fun questionStackIsBoundedAndClearHistoryErasesAllReferenceState() = runBlocking {
        for (i in 1..5) say("remind me in $i days")
        assertTrue(state().olderQuestions.size <= 2)
        OfflineAssistant.respond("/clear", context)
        assertNull(state().suspendedRequest); assertTrue(state().olderQuestions.isEmpty())
        assertTrue(state().selectedTaskIds.isEmpty() && state().taskListIds.isEmpty())
    }

    @Test fun forgetPreferencesAlsoErasesStoredListsAndQuestions() = runBlocking {
        add("One"); say("show my tasks"); say("remind me tomorrow"); say("what time is it")
        say("forget my preferences")
        assertTrue(state().taskListIds.isEmpty()); assertTrue(state().olderQuestions.isEmpty()); assertNull(state().suspendedRequest)
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }

    @Test fun removingAnItemFromPreviewLeavesOtherEditsAndExpiresOldConfirm() = runBlocking {
        val a = add("One"); val b = add("Two")
        say("show my tasks")
        val preview = say("move the first two to Friday")
        val revised = say("except the second one")
        say(command(preview, "confirm")); assertNull(task(a).dueDate)
        say(command(revised, "confirm"))
        assertNotNull(task(a).dueDate); assertNull(task(b).dueDate)
    }

    @Test fun absentOriginalTimeRequestsClarificationWithoutApplyingOldPreview() = runBlocking {
        val a = add("One"); val b = add("Two", "tomorrow at 4pm")
        say("show my tasks")
        val preview = say("move the first two to Friday at 10am")
        assertTrue(say("keep their original times").formattedText.contains("no original time"))
        say(command(preview, "confirm")); assertNull(task(a).dueDate)
        say("actually Friday at 8am"); say("yes")
        assertEquals(8, hour(task(a).dueDate!!)); assertEquals(8, hour(task(b).dueDate!!))
    }

    @Test fun externalEditInvalidatesARevisionAndItsPreviousConfirm() = runBlocking {
        val a = add("One"); val b = add("Two")
        say("show my tasks"); val preview = say("move the first two to Friday")
        db.taskDao().updateTask(task(b).copy(updatedAt = task(b).updatedAt + 1))
        assertTrue(say("actually Monday").formattedText.contains("changed while"))
        say(command(preview, "confirm")); assertNull(task(a).dueDate); assertNull(task(b).dueDate)
    }

    @Test fun negatedDateRepairPreservesThePreviewClocksAndTargets() = runBlocking {
        val a = add("One", "tomorrow at 9am"); val b = add("Two", "tomorrow at 4pm")
        say("show my tasks"); say("move the first two to Friday")
        say("Not Friday—Monday"); say("yes")
        assertEquals(day(date("Monday")), day(task(a).dueDate!!))
        assertEquals(day(date("Monday")), day(task(b).dueDate!!))
        assertEquals(9, hour(task(a).dueDate!!)); assertEquals(16, hour(task(b).dueDate!!))
        assertEquals(2, db.taskDao().getAllActiveTasksList().size)
    }

    @Test fun revisionCannotAddAnotherTaskToThePreviewByAccident() = runBlocking {
        val a = add("One"); val b = add("Two"); val c = add("Three")
        say("show my tasks"); val original = say("move the first two to Friday")
        assertTrue(say("actually move the third one to Monday").formattedText.contains("isn't in this preview"))
        say(command(original, "confirm")); assertNull(task(a).dueDate)
        say("actually Monday at 8am"); say("yes")
        assertNotNull(task(a).dueDate); assertNotNull(task(b).dueDate); assertNull(task(c).dueDate)
    }

    @Test fun aSeparateExplicitTaskRequestReplacesThePendingPreview() = runBlocking {
        val a = add("One"); add("Two"); val c = add("Three")
        say("show my tasks"); val original = say("move the first two to Friday")
        say("move Three to Monday at 8am")
        assertNotNull(task(c).dueDate); assertNull(task(a).dueDate)
        say(command(original, "confirm")); assertNull(task(a).dueDate)
    }

    @Test fun slashAnswerToReminderTitleDoesNotLeaveAnotherUnfinishedReminder() = runBlocking {
        say("remind me tomorrow at 6pm"); say("/create buy paper")
        assertEquals(18, hour(db.taskDao().getAllActiveTasksList().single().dueDate!!))
        assertNull(state().suspendedRequest)
        assertTrue(say("back to that reminder").formattedText.contains("no unfinished"))
    }

    @Test fun expiredUnfinishedQuestionCannotBeRevivedByNewerSessionActivity() = runBlocking {
        say("remind me tomorrow at 6pm"); say("what time is it")
        val snapshot = state()
        snapshot.suspendedRequest!!.put("savedAt", System.currentTimeMillis() - 900_001)
        AssistantSessionStore(context).save(snapshot)
        assertTrue(say("back to that reminder").formattedText.contains("no unfinished"))
        assertNull(state().expected); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }

    @Test fun unknownPluralDescriptionsAndOverflowCountsCannotSelectTheWholeList() = runBlocking {
        val a = add("One"); add("Two"); say("show my tasks")
        say("complete those books"); say("yes")
        assertFalse(task(a).isCompleted)
        say("complete those 99999999999999999999"); say("yes")
        assertFalse(task(a).isCompleted)
    }

    @Test fun resumingATaskQuestionRestoresItsOriginalListRatherThanANewerList() = runBlocking {
        val a = add("One"); val b = add("Two"); val c = add("Three")
        say("show my tasks"); say("reschedule the first two")
        db.taskDao().updateTask(task(c).copy(isPriority = true, updatedAt = task(c).updatedAt + 1))
        say("show priority tasks")
        assertEquals(listOf(c), state().taskListIds)
        val resumed = say("back to that reminder")
        assertTrue(resumed.formattedText.contains("One") && resumed.formattedText.contains("Two"))
        assertEquals(listOf(a, b, c), state().taskListIds)
        say("Friday at 4pm"); say("except the second one"); say("yes")
        assertNotNull(task(a).dueDate); assertNull(task(b).dueDate); assertNull(task(c).dueDate)
    }

    @Test fun oversizedSelectionCannotBecomeASilentPartialSelectionAfterReload() {
        val snapshot = AssistantSession()
        snapshot.focusTasks((1L..150L).toList())
        val restored = AssistantSession.fromJson(snapshot.toJson())
        val tasks = (1L..150L).map { Task(id = it, title = "Item $it") }
        val result = AssistantTaskLanguage.select("them", tasks, restored)
        assertNotNull(result.clarification); assertTrue(result.tasks.isEmpty())
    }

    @Test fun ambiguousHabitQuestionCannotReuseAnEarlierTaskOrdinal() = runBlocking {
        val a = add("One"); add("Two"); say("show my tasks")
        db.habitDao().insertHabit(Habit(title = "Morning water", targetPerDay = 8))
        db.habitDao().insertHabit(Habit(title = "Evening water", targetPerDay = 8))
        say("log habit water")
        say("complete the first one"); say("yes")
        assertFalse(task(a).isCompleted)
    }

    @Test fun newReminderQuestionDoesNotLeaveThePreviousTaskAsIt() = runBlocking {
        val a = add("Old task")
        say("let's work on Old task"); say("remind me tomorrow at 6pm")
        say("complete it")
        assertFalse(task(a).isCompleted)
        assertNull(state().taskId)
    }

    @Test fun taskClarificationBecomesTheMostRecentlyDisplayedTaskOrder() = runBlocking {
        val a = add("Other"); val b = add("Gym legs"); val c = add("Gym chest")
        say("show my tasks"); say("reschedule gym tomorrow at 6pm"); say("first")
        say("complete the second one")
        assertFalse(task(a).isCompleted); assertFalse(task(b).isCompleted); assertTrue(task(c).isCompleted)
    }

    @Test fun timerStatusInterruptionPreservesTheGroupDateQuestion() = runBlocking {
        val a = add("One"); val b = add("Two"); val c = add("Three")
        say("show my tasks"); say("reschedule the first two")
        say("timer status")
        assertEquals("focus", state().topic)
        assertNotNull("Timer status must retain the unfinished question", state().suspendedRequest)
        val resumed = say("back to that reminder")
        assertEquals(resumed.formattedText, "task_date", state().expected)
        val preview = say("actually Friday at 4pm")
        assertEquals(preview.formattedText, "confirm", state().expected)
        say("yes")
        assertNotNull(task(a).dueDate); assertNotNull(task(b).dueDate); assertNull(task(c).dueDate)
    }

    @Test fun failedResumeCannotLeaveAnEarlierConfirmationLive() = runBlocking {
        val a = add("Keep me")
        val preview = say("delete all tasks")
        say("back to that reminder"); say("yes"); say(command(preview, "confirm"))
        assertFalse(task(a).isTrashed)
    }

    @Test fun unavailableNaturalUndoCannotLeaveAnEarlierConfirmationLive() = runBlocking {
        val a = add("Keep me")
        val preview = say("delete all tasks")
        say("undo"); say("yes"); say(command(preview, "confirm"))
        assertFalse(task(a).isTrashed)
    }

    @Test fun ambiguousTopicReferenceInvalidatesThePendingSettingConfirmation() = runBlocking {
        val spec = AssistantPreferences.find("reminder interval")!!
        val before = AssistantPreferences.read(context, spec)
        val preview = say("set reminder interval to 30 minutes")
        say("the other one"); say("yes"); say(command(preview, "confirm"))
        assertEquals(before, AssistantPreferences.read(context, spec))
    }

    @Test fun naturalTaskReferenceCanInterruptAndResumeANewReminderQuestion() = runBlocking {
        val a = add("Existing task")
        say("remind me tomorrow at 6pm"); say("let's work on Existing task")
        assertEquals(a, state().taskId)
        say("back to that reminder"); say("buy paper")
        val added = db.taskDao().getAllActiveTasksList().first { it.id != a }
        assertEquals(18, hour(added.dueDate!!)); assertFalse(task(a).isCompleted)
    }

    @Test fun olderSessionJsonStillLoadsWithoutTheNewFields() {
        val json = AssistantSession(topic = "tasks").toJson()
        for (key in listOf("taskList", "taskListAt", "taskListComplete", "selectedTasks", "olderQuestions")) json.remove(key)
        val restored = AssistantSession.fromJson(json)
        assertTrue(restored.taskListIds.isEmpty() && restored.olderQuestions.isEmpty())
        assertNull(restored.suspendedRequest)
    }
}
