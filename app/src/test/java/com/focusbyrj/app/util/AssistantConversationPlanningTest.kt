package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.*
import com.focusbyrj.app.util.assistant.*
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
class AssistantConversationPlanningTest {
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
    @After fun tearDown() { AssistantSessionStore(context).clear(); FocusSessionManager.stop(context); db.close(); AyvaTalkEngine.resetConversation() }
    private suspend fun say(text: String) = OfflineAssistant.respond(text, context) ?: error("Not handled: $text")
    private fun state() = AssistantSessionStore(context).load()
    private suspend fun add(title: String, priority: Boolean = false) = db.taskDao().insertTask(Task(title = title, isPriority = priority))
    private suspend fun task(id: Long) = db.taskDao().getTaskById(id)!!
    private suspend fun select(id: Long) { say("let's work on id:$id") }
    private fun confirm(response: AyvaTalkEngine.TalkResponse) = response.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva confirm ") }.query

    private suspend fun habit(title: String, target: Int = 1, archived: Boolean = false) = db.habitDao().insertHabit(Habit(title = title, targetPerDay = target, isArchived = archived))
    private suspend fun savedHabit(id: Long) = db.habitDao().getHabitById(id)!!
    private fun prefs() = context.getSharedPreferences("ayva_personalization", 0)
    private suspend fun due(title: String, whenDue: String, priority: Boolean = false) = db.taskDao().insertTask(Task(title = title, dueDate = SmartDateParser.parse(whenDue).timestamp, isPriority = priority))
    private fun focusQuery(response: AyvaTalkEngine.TalkResponse) = response.actions.filterIsInstance<TalkAction.AskQuery>().first { it.label == "Start focus" }.query
    @Test fun explicitlySavedFocusDefaultSurvivesSessionReset() = runBlocking {
        say("remember that I prefer 15-minute focus sessions")
        assertEquals(15, AssistantPersonalization.focusMinutes(context)); OfflineAssistant.reset(context)
        say("start focus"); assertEquals(900L, FocusSessionManager.initialSeconds.value)
    }
    @Test fun focusPreferenceDoesNotChangeRunningTimer() = runBlocking {
        say("start focus for 25 minutes"); say("remember that I prefer 10-minute focus sessions")
        assertEquals(1500L, FocusSessionManager.initialSeconds.value); assertEquals(10, AssistantPersonalization.focusMinutes(context))
    }
    @Test fun explicitFocusDurationOverridesPreference() = runBlocking {
        say("remember that I prefer 15-minute focus sessions"); say("start focus for 7 minutes")
        assertEquals(420L, FocusSessionManager.initialSeconds.value)
    }
    @Test fun taskLinkedFocusUsesSavedDefault() = runBlocking {
        val id = add("Read"); say("remember that I prefer 12-minute focus sessions"); say("focus on Read")
        assertEquals(720L, FocusSessionManager.initialSeconds.value); assertEquals(id, FocusSessionManager.taskId(context))
    }
    @Test fun invalidFocusPreferencesNeverOverwriteValidValue() = runBlocking {
        say("remember that I prefer 15-minute focus sessions")
        for (n in listOf("0", "241", "999999999999999999999999999")) {
            say("remember that I prefer $n-minute focus sessions"); assertEquals(15, AssistantPersonalization.focusMinutes(context))
        }
    }
    @Test fun profileIsAllowlistedAndNeverStoresArbitraryFacts() = runBlocking {
        say("remember my password is supersecret"); say("remember that my doctor is Smith")
        assertTrue(prefs().all.isEmpty()); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }
    @Test fun explicitReminderStillCreatesTaskRatherThanProfile() = runBlocking {
        say("remember to call Mum tomorrow")
        assertEquals(1, db.taskDao().getAllActiveTasksList().size); assertTrue(prefs().all.isEmpty())
    }
    @Test fun slashCreationCanUseLiteralPreferenceText() = runBlocking {
        say("/create remember that I prefer 15-minute focus sessions")
        assertEquals(1, db.taskDao().getAllActiveTasksList().size); assertTrue(prefs().all.isEmpty())
    }
    @Test fun profileReviewShowsOnlyExplicitValues() = runBlocking {
        say("call me Raj"); say("remember that I prefer 15-minute focus sessions"); say("remember that I prefer 2 tasks in my plan")
        say("remember that I prefer due dates first"); say("no emoji")
        val response = say("what do you remember about me").formattedText
        assertTrue(response.contains("Raj")); assertTrue(response.contains("15 minutes")); assertTrue(response.contains("2 tasks")); assertTrue(response.contains("due dates first"))
        assertEquals(2, AssistantPersonalization.planSize(context)); assertEquals("due", AssistantPersonalization.planOrder(context))
    }
    @Test fun preferenceRemovalIsScoped() = runBlocking {
        say("call me Raj"); say("remember that I prefer 15-minute focus sessions"); say("remember that I prefer 2 tasks in my plan")
        say("forget my focus preference"); assertEquals(25, AssistantPersonalization.focusMinutes(context))
        assertEquals("Raj", prefs().getString("name", null)); assertEquals(2, AssistantPersonalization.planSize(context))
        say("forget my planning preferences"); assertEquals(3, AssistantPersonalization.planSize(context)); assertEquals("Raj", prefs().getString("name", null))
        say("forget my name"); assertFalse(prefs().contains("name"))
    }
    @Test fun replyPreferencesCanBeExplicitlyRememberedAndReset() = runBlocking {
        say("remember that I prefer brief replies"); say("remember that I prefer no emoji")
        assertTrue(AssistantReplyStyle.concise(context)); assertFalse(AssistantReplyStyle.emoji(context))
        say("forget my reply preferences"); assertFalse(AssistantReplyStyle.concise(context)); assertTrue(AssistantReplyStyle.emoji(context))
    }
    @Test fun ordinaryConversationDoesNotInferDurablePreferences() = runBlocking {
        add("Read"); say("I feel tired"); say("I have 10 minutes"); say("due dates first")
        assertTrue(prefs().all.isEmpty())
    }
    @Test fun profileCommandsDoNotFillMissingTaskTitle() = runBlocking {
        say("remind me tomorrow at 6pm"); say("call me Raj"); say("what do you remember about me")
        say("remember that I prefer 15-minute focus sessions")
        assertEquals("task_title", state().expected); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        say("buy paper"); assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun profileReviewDoesNotAuthorizePendingMutation() = runBlocking {
        val id = add("Read"); val token = confirm(say("delete all tasks"))
        say("show my preferences"); assertFalse(task(id).isTrashed); assertEquals(token, confirm(say("thanks")))
        say(token); assertTrue(task(id).isTrashed)
    }
    @Test fun forgetEverythingClearsLiveAndDurableContext() = runBlocking {
        val id = add("Read"); say("call me Raj"); say("remember that I prefer 15-minute focus sessions"); say("plan my day")
        say("forget my preferences")
        assertTrue(prefs().all.isEmpty()); assertNull(state().planning); assertTrue(state().taskListIds.isEmpty()); assertNull(state().taskId)
        assertEquals("Read", task(id).title)
    }
    @Test fun clearChatKeepsExplicitPreferencesButDropsPlan() = runBlocking {
        add("Read"); say("remember that I prefer 15-minute focus sessions"); say("plan my day")
        OfflineAssistant.respond("/clear", context)
        assertNull(state().planning); assertEquals(15, AssistantPersonalization.focusMinutes(context))
    }
    @Test fun planPreferenceBoundsDoNotOverwriteSavedValues() = runBlocking {
        say("remember that I prefer 2 tasks in my plan")
        say("remember that I prefer 0 tasks in my plan"); say("remember that I prefer 6 tasks in my plan")
        assertEquals(2, AssistantPersonalization.planSize(context))
    }
    @Test fun corruptPreferenceTypesUseSafeDefaults() {
        prefs().edit().putString("focus_minutes", "bad").putInt("plan_size", 999).putString("plan_order", "bad").commit()
        assertEquals(25, AssistantPersonalization.focusMinutes(context)); assertEquals(3, AssistantPersonalization.planSize(context)); assertEquals("priority", AssistantPersonalization.planOrder(context))
    }
    @Test fun timeBudgetPlanDoesNotMutateTasksOrStartTimer() = runBlocking {
        val a = add("One"); val b = add("Two"); val before = listOf(task(a), task(b))
        val response = say("I have 30 minutes")
        assertTrue(response.formattedText.contains("15-minute work block")); assertTrue(response.formattedText.contains("not estimates of task completion"))
        assertEquals(before, listOf(task(a), task(b))); assertFalse(FocusSessionManager.active.value)
        assertEquals(listOf(a, b), state().taskListIds)
    }
    @Test fun planCanStartFocusOnStableFirstTask() = runBlocking {
        val a = add("One"); add("Two"); val plan = say("I have 30 minutes"); say(focusQuery(plan))
        assertEquals(a, FocusSessionManager.taskId(context)); assertEquals(900L, FocusSessionManager.initialSeconds.value)
    }
    @Test fun todayPlanExcludesFutureDatesButIncludesUndatedTasks() = runBlocking {
        val future = due("Future", "in 10 days", true); val today = due("Today", "today at 11pm"); val undated = add("Any time")
        say("plan my day")
        assertFalse(future in state().taskListIds); assertTrue(today in state().taskListIds); assertTrue(undated in state().taskListIds)
    }
    @Test fun overdueTasksComeBeforeUndatedPriority() = runBlocking {
        val overdue = db.taskDao().insertTask(Task(title = "Late", dueDate = System.currentTimeMillis() - 60000))
        add("Priority", true); say("what should I do next"); assertEquals(overdue, state().taskId)
    }
    @Test fun dueDatePreferenceChangesNewPlanOrdering() = runBlocking {
        val priority = add("Important", true); val dated = due("Dated", "tomorrow at 11pm")
        say("make a plan"); assertEquals(priority, state().taskId)
        say("remember that I prefer due dates first"); say("make a plan"); assertEquals(dated, state().taskId)
    }
    @Test fun temporaryOrderDoesNotPersistAsPreference() = runBlocking {
        add("Important", true); val dated = due("Dated", "tomorrow at 11pm")
        say("make a plan"); say("due dates first"); assertEquals(dated, state().taskId)
        assertFalse(prefs().contains("plan_order")); say("make a plan"); assertEquals("priority", state().planning!!.order)
    }
    @Test fun configuredPlanSizeMatchesDisplayedReferenceList() = runBlocking {
        repeat(5) { add("Item $it") }; say("remember that I prefer 2 tasks in my plan"); say("plan my day")
        assertEquals(2, state().taskListIds.size)
        assertEquals(2, state().planning!!.limit)
    }
    @Test fun concisePlansKeepAllDisplayedTaskIdentities() = runBlocking {
        repeat(3) { add("Item $it") }; say("be concise"); val result = say("plan my day")
        assertEquals(3, state().taskListIds.size)
        for (n in 0..2) assertTrue(result.formattedText.contains("Item $n"))
    }
    @Test fun oneMinuteBudgetCannotAllocateFiveWorkBlocks() = runBlocking {
        repeat(5) { add("Item $it") }; say("remember that I prefer 5 tasks in my plan")
        val result = say("I only have 1 minute")
        assertEquals(1, state().taskListIds.size); assertTrue(result.formattedText.contains("1-minute work block"))
    }
    @Test fun smallFocusPreferenceLeavesExplicitUnallocatedBudget() = runBlocking {
        repeat(3) { add("Item $it") }; say("remember that I prefer 5-minute focus sessions")
        assertTrue(say("I have 30 minutes").formattedText.contains("15 minutes remain unallocated"))
    }
    @Test fun skipPlanTaskDoesNotCompleteOrPostponeIt() = runBlocking {
        val a = add("One"); val b = add("Two"); val before = task(a)
        say("make a plan"); say("skip that one")
        assertEquals(b, state().taskId); assertEquals(before, task(a)); assertTrue(a in state().planning!!.excluded)
        say("include skipped tasks"); assertEquals(a, state().taskId)
    }
    @Test fun onlyTodayAndIncludeFutureAdjustSessionScope() = runBlocking {
        val future = due("Future", "in 10 days"); add("Undated")
        say("make a plan"); assertTrue(future in state().taskListIds)
        say("only today"); assertFalse(future in state().taskListIds)
        say("include future tasks"); assertTrue(future in state().taskListIds)
    }
    @Test fun explanationUsesLivePriorityAndDueInformation() = runBlocking {
        val a = add("One"); val b = add("Two")
        say("make a plan"); db.taskDao().updateTask(task(b).copy(isPriority = true, updatedAt = task(b).updatedAt + 1))
        val response = say("why this order")
        assertEquals(b, state().taskId); assertTrue(response.formattedText.contains("marked priority")); assertFalse(task(a).isCompleted)
    }
    @Test fun completedTasksDisappearWhenPlanIsRefreshed() = runBlocking {
        val a = add("One"); val b = add("Two"); say("make a plan")
        db.taskDao().updateTask(task(a).copy(isCompleted = true)); say("show my plan")
        assertEquals(b, state().taskId); assertFalse(a in state().taskListIds)
    }
    @Test fun trashedTasksCannotBeChosenByOldPlanFocusButton() = runBlocking {
        val a = add("One"); add("Two"); val query = focusQuery(say("make a plan"))
        db.taskDao().updateTask(task(a).copy(isTrashed = true)); say(query)
        assertFalse(FocusSessionManager.active.value)
    }
    @Test fun oldPlanFocusButtonCannotRetargetNewPlan() = runBlocking {
        val a = add("One"); val b = add("Two"); val query = focusQuery(say("make a plan"))
        say("skip that one"); assertEquals(b, state().taskId); say(query)
        assertEquals(a, FocusSessionManager.taskId(context))
    }
    @Test fun oldSkipButtonCannotSkipDifferentCurrentTask() = runBlocking {
        val a = add("One"); val b = add("Two"); val plan = say("make a plan")
        val button = plan.actions.filterIsInstance<TalkAction.AskQuery>().first { it.label == "Another option" }.query
        say("skip that one"); val before = state().planning!!.excluded; say(button)
        assertEquals(before, state().planning!!.excluded); assertEquals(b, state().taskId); assertTrue(a in before)
    }
    @Test fun runningTimerIsNotReplacedOrChangedByPlan() = runBlocking {
        add("Read"); say("start focus for 25 minutes"); val result = say("I have 10 minutes")
        assertEquals(1500L, FocusSessionManager.initialSeconds.value); assertTrue(FocusSessionManager.active.value)
        assertFalse(result.actions.any { it.label == "Start focus" }); assertTrue(result.formattedText.contains("hasn't changed it"))
    }
    @Test fun emptyTaskListDoesNotInventPlanItems() = runBlocking {
        val response = say("I have 30 minutes")
        assertTrue(response.formattedText.contains("task list is clear")); assertTrue(state().taskListIds.isEmpty()); assertTrue(response.actions.isEmpty())
    }
    @Test fun noMatchingTodayTasksDoesNotPullFutureTaskSilently() = runBlocking {
        due("Future", "in 10 days"); val response = say("plan my day")
        assertTrue(response.formattedText.contains("No tasks match")); assertTrue(state().taskListIds.isEmpty())
    }
    @Test fun budgetQuestionConsumesOnlyValidTime() = runBlocking {
        add("Read"); say("plan my time"); assertEquals("plan_budget", state().expected)
        say("thanks"); say("2 litres"); assertEquals("plan_budget", state().expected)
        say("half an hour"); assertEquals(30, state().planning!!.minutes); assertNull(state().expected)
    }
    @Test fun fullBudgetPhraseCanAnswerQuestionWithoutSuspendingItAgain() = runBlocking {
        add("Read"); say("plan my time"); say("I have 20 minutes")
        assertEquals(20, state().planning!!.minutes); assertNull(state().suspendedRequest)
    }
    @Test fun invalidBudgetDoesNotGuessOrStartFocus() = runBlocking {
        add("Read")
        for (value in listOf("0 minutes", "481 minutes", "1.5 minutes", "10 to 20 minutes")) {
            say("plan the next $value"); assertEquals("plan_budget", state().expected); assertFalse(FocusSessionManager.active.value)
        }
        say("30"); assertEquals(30, state().planning!!.minutes)
    }
    @Test fun planningQuestionCanBeInterruptedAndResumed() = runBlocking {
        add("Read"); say("plan my time"); say("what time is it"); say("back to planning")
        assertEquals("plan_budget", state().expected); say("20 minutes"); assertEquals(20, state().planning!!.minutes)
    }
    @Test fun reminderQuestionSurvivesPlanningInterruption() = runBlocking {
        add("Read"); say("remind me tomorrow at 6pm"); say("I have 30 minutes")
        assertNull(state().expected); say("back to that reminder"); assertEquals("task_title", state().expected)
        say("buy paper"); assertEquals(2, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun cancellationDropsUnfinishedPlan() = runBlocking {
        add("Read"); say("plan my time"); say("cancel")
        assertNull(state().planning); assertNull(state().expected)
    }
    @Test fun planHasOwnExpiryDespiteNewSessionActivity() = runBlocking {
        add("Read"); say("make a plan"); val snapshot = state()
        snapshot.planning = snapshot.planning!!.copy(createdAt = System.currentTimeMillis() - 900_001)
        AssistantSessionStore(context).save(snapshot)
        assertTrue(say("show my plan").formattedText.contains("no longer available")); assertNull(state().planning)
    }
    @Test fun expiredPlanningQuestionCannotBeKeptAliveByPreferenceChange() = runBlocking {
        add("Read"); say("plan my time"); val snapshot = state()
        snapshot.planning = snapshot.planning!!.copy(createdAt = System.currentTimeMillis() - 900_001)
        AssistantSessionStore(context).save(snapshot); say("be concise"); say("30 minutes")
        assertNull(state().expected); assertNull(state().planning); assertFalse(FocusSessionManager.active.value)
    }
    @Test fun planContextSurvivesSessionSerialization() = runBlocking {
        add("One"); add("Two"); say("I have 30 minutes"); say("skip that one")
        val before = state(); val loaded = AssistantSession.fromJson(before.toJson())
        assertEquals(before.planning, loaded.planning)
        val old = before.toJson().apply { remove("planning") }
        assertNull(AssistantSession.fromJson(old).planning)
    }
    @Test fun planningSuggestionsRespectNoEmoji() = runBlocking {
        add("Read"); say("no emoji"); assertTrue(say("make a plan").actions.all { it.emoji.isEmpty() })
    }
    @Test fun taskWithEmotionWordsIsNotConvertedIntoPlan() = runBlocking {
        val id = add("Stuck door"); say("complete Stuck door"); assertTrue(task(id).isCompleted)
        say("remind me to rest when tired tomorrow"); assertEquals(1, db.taskDao().getAllActiveTasksList().count { !it.isCompleted })
    }
    @Test fun colloquialBudgetPhraseIsParsedAsTime() = runBlocking {
        add("Read"); say("I have got 20 minutes"); assertEquals(20, state().planning!!.minutes); assertNull(state().expected)
    }
    @Test fun expiredPlanReleasesItsImplicitTaskReferences() = runBlocking {
        val id = add("Read"); say("make a plan"); val snapshot = state()
        snapshot.planning = snapshot.planning!!.copy(createdAt = System.currentTimeMillis() - 900_001)
        AssistantSessionStore(context).save(snapshot); say("show my plan")
        assertNull(state().taskId); assertTrue(state().taskListIds.isEmpty())
        say("complete it"); assertFalse(task(id).isCompleted)
    }
}
