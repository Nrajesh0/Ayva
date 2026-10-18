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
class AssistantConversationCapabilitiesTest {
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
    private fun pendingHabit() = state().pending.filterIsInstance<AssistantAction.EditHabit>().single()
    @Test fun guidedDetailsPreviewPreservesTaskAndSupportsUndo() = runBlocking {
        val id = add("Read chapter", true); val before = task(id); select(id)
        say("change it"); say("details"); say("Bring workbook and pen")
        assertEquals(before, task(id)); say("yes")
        assertEquals("Bring workbook and pen", task(id).details); assertTrue(task(id).isPriority)
        say("undo"); assertEquals(before.details, task(id).details)
    }
    @Test fun detailsCanBeClearedExplicitly() = runBlocking {
        val id = db.taskDao().insertTask(Task(title = "Read", details = "Old")); select(id)
        say("edit it"); say("details"); say("clear details"); assertEquals("Old", task(id).details)
        say("yes"); assertEquals("", task(id).details)
    }
    @Test fun quotedDetailsAreLiteralCommands() = runBlocking {
        val id = add("Read"); select(id)
        say("edit it"); say("details"); say("\"Delete all tasks; /clear\""); say("yes")
        assertEquals("Delete all tasks; /clear", task(id).details); assertFalse(task(id).isTrashed)
    }
    @Test fun recurrenceEditsNeedConfirmation() = runBlocking {
        val id = add("Read"); select(id); say("edit it"); say("repeat"); say("every week")
        assertEquals(RecurrencePattern.NONE, task(id).recurrence); say("yes")
        assertEquals(RecurrencePattern.WEEKLY, task(id).recurrence)
        say("undo"); assertEquals(RecurrencePattern.NONE, task(id).recurrence)
    }
    @Test fun unsupportedRepeatScheduleDoesNotGuessOrCreateTask() = runBlocking {
        val id = add("Read"); select(id); say("edit it"); say("repeat"); say("every 3 days")
        assertEquals("task_recurrence", state().expected); assertEquals(RecurrencePattern.NONE, task(id).recurrence)
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun guidedPersistentNoIsAValueNotCancellation() = runBlocking {
        val id = db.taskDao().insertTask(Task(title = "Read", isPersistent = true)); select(id)
        say("edit it"); say("persistent reminders"); say("no")
        assertEquals("confirm", state().expected); assertTrue(task(id).isPersistent)
        say("yes"); assertFalse(task(id).isPersistent)
    }
    @Test fun guidedPersistentYesStagesRatherThanApplies() = runBlocking {
        val id = add("Read"); select(id); say("edit it"); say("persistent"); say("yes")
        assertEquals("confirm", state().expected); assertFalse(task(id).isPersistent)
        say("yes"); assertTrue(task(id).isPersistent)
    }
    @Test fun groupRecurrenceChangesOnlySelection() = runBlocking {
        val a = add("One"); val b = add("Two"); val c = add("Three")
        say("show my tasks"); say("let's work on the first two tasks"); say("change them"); say("repeat"); say("daily"); say("yes")
        assertEquals(RecurrencePattern.DAILY, task(a).recurrence); assertEquals(RecurrencePattern.DAILY, task(b).recurrence)
        assertEquals(RecurrencePattern.NONE, task(c).recurrence)
    }
    @Test fun newTaskFieldQuestionCanResume() = runBlocking {
        val id = add("Read"); select(id); say("edit it"); say("repeat"); say("what time is it"); say("back to that reminder")
        assertEquals("task_recurrence", state().expected); say("monthly"); say("yes")
        assertEquals(RecurrencePattern.MONTHLY, task(id).recurrence)
    }
    @Test fun newFieldPreviewRejectsStaleTarget() = runBlocking {
        val id = add("Read"); select(id); say("edit it"); say("details"); val token = confirm(say("New details"))
        db.taskDao().updateTask(task(id).copy(details = "External", updatedAt = task(id).updatedAt + 1))
        say(token); assertEquals("External", task(id).details)
    }
    @Test fun habitRenameDoesNotRenameSimilarlyNamedTask() = runBlocking {
        val h = habit("Reading"); val t = add("Reading"); val before = savedHabit(h)
        val token = confirm(say("rename habit Reading to Fiction")); assertEquals(before, savedHabit(h))
        say(token); assertEquals("Fiction", savedHabit(h).title); assertEquals("Reading", task(t).title)
        say("undo"); assertEquals(before, savedHabit(h))
    }
    @Test fun habitEditPreservesEveryOtherSetting() = runBlocking {
        val before = Habit(title = "Water", description = "Daily water", iconEmoji = "💧", colorHex = "#AABBCC", type = HabitType.INTERVAL_WINDOW,
            targetPerDay = 8, intervalHours = 1, intervalMinutes = 15, windowStartHour = 7, windowStartMinute = 30, windowEndHour = 21,
            windowEndMinute = 20, fixedReminderHour = 10, fixedReminderMinute = 5, reminderSound = "BELL")
        val id = db.habitDao().insertHabit(before)
        say("rename habit Water to Drink water"); say("yes")
        assertEquals(before.copy(id = id, title = "Drink water"), savedHabit(id))
        val action = AssistantAction.EditHabit(id, HabitField.TARGET, "9", savedHabit(id))
        assertEquals(action, AssistantAction.fromJson(action.toJson()))
    }
    @Test fun newHabitGoalPreservesRecordedDailyTargetAndProgress() = runBlocking {
        val id = habit("Reading", 3)
        val date = (context.applicationContext as FocusApplication).habitRepository.getTodayDateString()
        val log = HabitLog(habitId = id, date = date, completedCount = 2, targetCount = 3, lastCompletedTimestamp = 1234)
        val logId = db.habitDao().insertOrUpdateLog(log)
        say("change habit Reading goal to 5"); say("yes")
        assertEquals(5, savedHabit(id).targetPerDay); assertEquals(log.copy(id = logId), db.habitDao().getLogForHabitAndDate(id, date))
        assertTrue(say("show habit Reading").formattedText.contains("2/3 today"))
        say("undo"); assertEquals(3, savedHabit(id).targetPerDay)
    }
    @Test fun missingHabitGoalAsksThenConfirms() = runBlocking {
        val id = habit("Reading"); say("change habit Reading goal")
        assertEquals("habit_edit_value", state().expected); say("thanks"); assertEquals("habit_edit_value", state().expected)
        say("3 times a day"); assertEquals("confirm", state().expected); assertEquals(1, savedHabit(id).targetPerDay)
        say("yes"); assertEquals(3, savedHabit(id).targetPerDay)
    }
    @Test fun invalidHabitGoalKeepsQuestionWithoutSideEffects() = runBlocking {
        val id = habit("Reading"); say("change habit Reading goal to 0")
        assertEquals("habit_edit_value", state().expected)
        say("101"); assertEquals("habit_edit_value", state().expected); assertEquals(1, savedHabit(id).targetPerDay)
        say("2 litres"); assertEquals("habit_edit_value", state().expected); assertEquals(1, savedHabit(id).targetPerDay)
    }
    @Test fun habitEditQuestionCanResumeAfterInterruption() = runBlocking {
        val id = habit("Reading"); say("change habit Reading goal"); say("what time is it"); say("back to the habit")
        assertEquals(id, state().habitId); assertEquals("habit_edit_value", state().expected)
        say("4"); say("yes"); assertEquals(4, savedHabit(id).targetPerDay)
    }
    @Test fun staleHabitQuestionCannotOverwriteExternalChange() = runBlocking {
        val id = habit("Reading"); say("change habit Reading goal"); db.habitDao().updateHabit(savedHabit(id).copy(targetPerDay = 7))
        say("3"); assertNull(state().expected); assertEquals(7, savedHabit(id).targetPerDay)
    }
    @Test fun staleHabitPreviewCannotOverwriteExternalChange() = runBlocking {
        val id = habit("Reading"); val token = confirm(say("rename habit Reading to Fiction"))
        db.habitDao().updateHabit(savedHabit(id).copy(description = "External")); say(token)
        assertEquals("Reading", savedHabit(id).title); assertEquals("External", savedHabit(id).description)
    }
    @Test fun habitExecutorRechecksSnapshotInsideTransaction() = runBlocking {
        val id = habit("Reading"); val action = AssistantAction.EditHabit(id, HabitField.TARGET, "2", savedHabit(id))
        db.habitDao().updateHabit(savedHabit(id).copy(targetPerDay = 8))
        try { AssistantActionExecutor.execute(context, action); fail("Stale edit executed") } catch (expected: IllegalArgumentException) { }
        assertEquals(8, savedHabit(id).targetPerDay)
    }
    @Test fun habitUndoRejectsNewerExternalChange() = runBlocking {
        val id = habit("Reading"); say("rename habit Reading to Fiction"); say("yes")
        db.habitDao().updateHabit(savedHabit(id).copy(description = "External")); say("undo")
        assertEquals("Fiction", savedHabit(id).title); assertEquals("External", savedHabit(id).description)
    }
    @Test fun duplicateHabitNameIsRejected() = runBlocking {
        val id = habit("Reading"); habit("Water"); say("rename habit Reading to Water"); say("yes")
        assertEquals("Reading", savedHabit(id).title)
    }
    @Test fun ambiguousHabitChoiceRequiresFreshConfirmation() = runBlocking {
        val a = habit("Read fiction"); val b = habit("Read nonfiction")
        val choices = say("rename habit Read to Books")
        val query = choices.actions.filterIsInstance<TalkAction.AskQuery>().first { it.label == "Read nonfiction" }.query
        say(query); assertEquals("confirm", state().expected)
        assertEquals("Read nonfiction", savedHabit(b).title); say("yes")
        assertEquals("Books", savedHabit(b).title); assertEquals("Read fiction", savedHabit(a).title)
        say(query); assertEquals("Books", savedHabit(b).title)
    }
    @Test fun habitRenameChoicePayloadCannotExecuteCommands() = runBlocking {
        habit("Read fiction"); val b = habit("Read nonfiction")
        val choices = say("rename habit Read to \"Books; /clear\"")
        val choice = choices.actions.filterIsInstance<TalkAction.AskQuery>().first { it.label == "Read nonfiction" }.query
        say(choice); assertEquals("confirm", state().expected); say("yes")
        assertEquals("Books; /clear", savedHabit(b).title)
    }
    @Test fun archiveAndRestoreKeepHabitHistory() = runBlocking {
        val id = habit("Reading"); val date = (context.applicationContext as FocusApplication).habitRepository.getTodayDateString()
        db.habitDao().insertOrUpdateLog(HabitLog(habitId = id, date = date, completedCount = 1))
        say("archive habit Reading"); assertFalse(savedHabit(id).isArchived); say("yes"); assertTrue(savedHabit(id).isArchived)
        assertNotNull(db.habitDao().getLogForHabitAndDate(id, date)); assertTrue(say("archived habits").formattedText.contains("Reading"))
        say("restore habit Reading"); say("yes"); assertFalse(savedHabit(id).isArchived)
        assertNotNull(db.habitDao().getLogForHabitAndDate(id, date))
    }
    @Test fun archiveUndoRestoresSameIdentity() = runBlocking {
        val id = habit("Reading"); say("archive habit Reading"); say("yes"); say("undo")
        assertFalse(savedHabit(id).isArchived); assertEquals(1, db.habitDao().getAllHabitsSync().size)
    }
    @Test fun restoreCannotCreateDuplicateActiveHabit() = runBlocking {
        val id = habit("Reading", archived = true); habit("Reading")
        say("restore habit id:$id"); say("yes"); assertTrue(savedHabit(id).isArchived)
    }
    @Test fun reminderToggleCanBeUndone() = runBlocking {
        val id = habit("Reading"); say("turn off reminders for habit Reading"); say("yes"); assertFalse(savedHabit(id).isReminderEnabled)
        say("undo"); assertTrue(savedHabit(id).isReminderEnabled)
    }
    @Test fun habitPronounsStayInHabitDomain() = runBlocking {
        val h = habit("Reading"); val t = add("Reading")
        say("show habit Reading"); say("rename it to Fiction"); say("yes")
        assertEquals("Fiction", savedHabit(h).title); assertEquals("Reading", task(t).title)
        say("show my tasks"); say("set its goal to 4"); assertEquals(1, savedHabit(h).targetPerDay)
    }
    @Test fun negatedHabitMutationNeverPreviewsOrApplies() = runBlocking {
        val id = habit("Reading"); say("don't archive habit Reading"); say("yes")
        assertFalse(savedHabit(id).isArchived); assertNull(state().confirmationId)
    }
    @Test fun habitRenameCanUseLiteralCourtesyName() = runBlocking {
        val id = habit("Reading"); say("rename habit Reading"); say("thanks")
        assertEquals("habit_edit_value", state().expected)
        say("\"Thanks\""); say("yes"); assertEquals("Thanks", savedHabit(id).title)
    }
    @Test fun taskCreationAboutHabitDoesNotEditHabit() = runBlocking {
        val id = habit("Reading"); say("remind me to archive habit Reading tomorrow")
        assertFalse(savedHabit(id).isArchived); assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun focusOnSelectedTaskPersistsAssociation() = runBlocking {
        val id = add("Read chapter"); select(id); say("focus on it for 15 minutes")
        assertTrue(FocusSessionManager.active.value); assertEquals(id, FocusSessionManager.taskId(context))
        assertEquals(900L, FocusSessionManager.initialSeconds.value)
        assertTrue(say("timer status").formattedText.contains("Read chapter")); assertFalse(task(id).isCompleted)
    }
    @Test fun focusedTaskStatusUsesCurrentTitle() = runBlocking {
        val id = add("Read chapter"); say("focus on Read chapter")
        db.taskDao().updateTask(task(id).copy(title = "Fiction", updatedAt = task(id).updatedAt + 1))
        assertTrue(say("what am I focusing on").formattedText.contains("Fiction"))
        assertEquals(id, FocusSessionManager.taskId(context))
    }
    @Test fun completingFocusTaskDoesNotStopCountdown() = runBlocking {
        val id = add("Read chapter"); say("focus on Read chapter for 10 minutes"); say("complete focus task")
        assertTrue(task(id).isCompleted); assertTrue(FocusSessionManager.active.value)
        assertTrue(say("timer status").formattedText.contains("is completed"))
    }
    @Test fun ambiguousFocusChoiceUsesSelectedId() = runBlocking {
        add("Read fiction"); val id = add("Read nonfiction")
        val response = say("focus on Read for 15 minutes")
        assertFalse(FocusSessionManager.active.value)
        val query = response.actions.filterIsInstance<TalkAction.AskQuery>().first { it.label == "Read nonfiction" }.query
        say(query); assertEquals(id, FocusSessionManager.taskId(context)); assertEquals(900L, FocusSessionManager.initialSeconds.value)
    }
    @Test fun focusChoiceRejectsCompletedTask() = runBlocking {
        add("Read fiction"); val id = add("Read nonfiction")
        val response = say("focus on Read for 15 minutes")
        val query = response.actions.filterIsInstance<TalkAction.AskQuery>().first { it.label == "Read nonfiction" }.query
        db.taskDao().updateTask(task(id).copy(isCompleted = true)); say(query)
        assertFalse(FocusSessionManager.active.value)
    }
    @Test fun focusDoesNotReplaceRunningSession() = runBlocking {
        val a = add("One"); add("Two"); say("focus on One for 15 minutes"); say("focus on Two for 30 minutes")
        assertEquals(a, FocusSessionManager.taskId(context)); assertEquals(900L, FocusSessionManager.initialSeconds.value)
    }
    @Test fun invalidFocusDurationsDoNotStartCountdown() = runBlocking {
        add("Read chapter")
        for (duration in listOf("0 minutes", "241 minutes", "1.5 minutes", "later", "15 to 30 minutes")) {
            say("focus on Read chapter for $duration"); assertFalse(FocusSessionManager.active.value)
        }
    }
    @Test fun focusCompletionRejectsUnavailableTask() = runBlocking {
        val id = add("Read chapter"); say("focus on Read chapter"); db.taskDao().updateTask(task(id).copy(isTrashed = true))
        say("complete focus task"); assertFalse(task(id).isCompleted); assertTrue(FocusSessionManager.active.value)
        assertTrue(say("timer status").formattedText.contains("no longer active"))
    }
    @Test fun stoppingFocusClearsAssociationBeforeNextUnlinkedTimer() = runBlocking {
        val id = add("Read chapter"); say("focus on Read chapter"); assertEquals(id, FocusSessionManager.taskId(context))
        say("stop focus"); say("yes"); assertNull(FocusSessionManager.taskId(context))
        say("start focus for 5 minutes"); assertNull(FocusSessionManager.taskId(context))
    }
    @Test fun oldFocusActionsRemainCompatible() {
        assertEquals(AssistantAction.StartFocus(25), AssistantAction.fromJson(org.json.JSONObject("{\"kind\":\"focus\",\"minutes\":25}")))
    }
    @Test fun ordinaryNewTextCanFillDetailsWithoutCreatingTask() = runBlocking {
        val id = add("Read"); select(id); say("edit it"); say("details"); say("New details"); say("yes")
        assertEquals("New details", task(id).details); assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun explicitNewTaskInterruptsDetailsQuestion() = runBlocking {
        val id = add("Read"); select(id); say("edit it"); say("details"); say("new task Buy paper")
        assertEquals("", task(id).details); assertEquals(2, db.taskDao().getAllActiveTasksList().size)
        say("back to that reminder"); assertEquals("task_details", state().expected)
    }
    @Test fun newHabitRequestInterruptsMissingTaskTitle() = runBlocking {
        say("remind me tomorrow at 6pm"); say("add habit Reading")
        assertTrue(state().pending.single() is AssistantAction.CreateHabit); say("yes")
        assertEquals("Reading", db.habitDao().getAllHabitsSync().single().title)
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        say("back to that reminder"); assertEquals("task_title", state().expected)
    }
    @Test fun focusAssociationSurvivesConversationReset() = runBlocking {
        val id = add("Read"); say("focus on Read for 10 minutes"); OfflineAssistant.reset(context)
        FocusSessionManager.restore(context)
        assertEquals(id, FocusSessionManager.taskId(context)); assertTrue(say("focus task").formattedText.contains("Read"))
    }
    @Test fun newCapabilitySuggestionsRespectNoEmoji() = runBlocking {
        habit("Reading"); add("Read chapter"); say("no emoji")
        assertTrue(say("show habit Reading").actions.all { it.emoji.isEmpty() })
        say("focus on Read chapter")
        assertTrue(say("focus task").actions.all { it.emoji.isEmpty() })
    }
    @Test fun focusTaskTitleCanContainForWithoutDuration() = runBlocking {
        val id = add("Prepare for exam"); say("focus on Prepare for exam")
        assertEquals(id, FocusSessionManager.taskId(context)); assertEquals(1500L, FocusSessionManager.initialSeconds.value)
    }
    @Test fun focusQuotedTitleSeparatesOnlyTrailingDuration() = runBlocking {
        val id = add("Prepare for exam"); say("focus on \"Prepare for exam\" for 15 minutes")
        assertEquals(id, FocusSessionManager.taskId(context)); assertEquals(900L, FocusSessionManager.initialSeconds.value)
    }
    @Test fun focusDurationFollowsTaskWhoseNameContainsFor() = runBlocking {
        val id = add("Prepare for exam"); say("focus on Prepare for exam for 15 minutes")
        assertEquals(id, FocusSessionManager.taskId(context)); assertEquals(900L, FocusSessionManager.initialSeconds.value)
    }
    @Test fun duplicateFullFocusTitlesCannotSilentlyChooseFirst() = runBlocking {
        add("Prepare for exam"); val b = add("Prepare for exam")
        val response = say("focus on Prepare for exam")
        assertFalse(FocusSessionManager.active.value)
        say(response.actions.filterIsInstance<TalkAction.AskQuery>()[1].query)
        assertEquals(b, FocusSessionManager.taskId(context))
    }
    @Test fun focusPronounDoesNotBecomeTaskNamedIt() = runBlocking {
        add("It"); val b = add("Read"); select(b); say("focus on it")
        assertEquals(b, FocusSessionManager.taskId(context))
    }
    @Test fun quotedFocusNameIsLiteralEvenWhenItLooksLikePronoun() = runBlocking {
        val a = add("It"); val b = add("Read"); select(b); say("focus on \"It\" for 10 minutes")
        assertEquals(a, FocusSessionManager.taskId(context))
    }
    @Test fun oldFocusCompletionButtonCannotRetargetNewSession() = runBlocking {
        val a = add("One"); val b = add("Two"); say("focus on One")
        val button = say("timer status").actions.filterIsInstance<TalkAction.AskQuery>().first { it.label == "Mark task done" }.query
        say("stop focus"); say("yes"); say("focus on Two"); say(button)
        assertTrue(task(a).isCompleted); assertFalse(task(b).isCompleted)
        assertEquals(b, FocusSessionManager.taskId(context))
    }
}
