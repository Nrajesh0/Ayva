package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
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
import java.io.File
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class OfflineAssistantBatchTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase

    @Before fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        for (name in listOf("focus_prefs", "bubble_prefs", "ayva_action_receipts", "ayva_personalization", "focus_completed_tasks_history")) context.getSharedPreferences(name, 0).edit().clear().commit()
        AssistantSessionStore(context).clear()
        FocusEconomyManager.init(context)
        AyvaTalkEngine.resetConversation()
    }

    @After fun tearDown() {
        FocusSessionManager.stop(context)
        AssistantSessionStore(context).clear()
        AyvaTalkEngine.resetConversation()
        db.close()
    }

    private suspend fun say(text: String, priority: Boolean = false, persistent: Boolean = false) =
        OfflineAssistant.respond(text, context, priority, persistent) ?: error("Not handled: $text")
    private fun command(response: AyvaTalkEngine.TalkResponse, label: String) =
        (response.actions.first { it.label.contains(label, true) } as TalkAction.AskQuery).query
    private suspend fun allTasks() = db.taskDao().getAllActiveTasksList()
    private suspend fun add(title: String, priority: Boolean = false, due: Long? = null): Long = db.taskDao().insertTask(Task(title = title, isPriority = priority, dueDate = due))

    @Test fun ordinaryTaskAndCorrectionsUseOneEntity() = runBlocking {
        say("Could you remind me to buy milk tomorrow at 6pm?")
        val id = allTasks().single().id
        say("rename it to Buy oat milk")
        say("make it priority")
        say("make it persistent")
        val task = db.taskDao().getTaskById(id)!!
        assertEquals("Buy oat milk", task.title)
        assertTrue(task.isPriority && task.isPersistent)
        assertEquals(1, allTasks().size)
    }

    @Test fun explicitTaskIsNotSwallowedByMoodWords() = runBlocking {
        say("remind me to take a break when tired tomorrow")
        assertEquals(1, allTasks().size)
        assertTrue(allTasks().single().title.contains("tired"))
    }

    @Test fun explicitCreateCannotStartATimer() = runBlocking {
        say("/create start focus for 25 minutes")
        assertEquals(1, allTasks().size)
        assertFalse(FocusSessionManager.active.value)
    }

    @Test fun missingTitleRetainsComposerAttributes() = runBlocking {
        say("add", true, true)
        say("call the dentist tomorrow")
        assertTrue(allTasks().single().isPriority)
        assertTrue(allTasks().single().isPersistent)
    }

    @Test fun duplicateChoiceRetainsDraftAndIsConsumedOnce() = runBlocking {
        add("Buy milk")
        val choices = say("buy milk tomorrow", true, true)
        val choose = command(choices, "separate")
        say(choose)
        say(choose)
        assertEquals(2, allTasks().size)
        assertTrue(allTasks().last().isPriority && allTasks().last().isPersistent)
    }

    @Test fun ambiguousTaskCanBeChosenByOrdinal() = runBlocking {
        val first = add("Gym legs")
        val second = add("Gym chest")
        say("reschedule gym tomorrow at 6pm")
        say("the second one")
        assertNull(db.taskDao().getTaskById(first)!!.dueDate)
        assertNotNull(db.taskDao().getTaskById(second)!!.dueDate)
    }

    @Test fun ordinalsReferToDisplayedOrderAfterDatabaseChanges() = runBlocking {
        val first = add("First")
        val second = add("Second")
        say("show my tasks")
        db.taskDao().updateTask(db.taskDao().getTaskById(first)!!.copy(isPriority = true))
        db.taskDao().updateTask(db.taskDao().getTaskById(second)!!.copy(dueDate = System.currentTimeMillis() + 60_000))
        say("complete the second one")
        assertFalse(db.taskDao().getTaskById(first)!!.isCompleted)
        assertTrue(db.taskDao().getTaskById(second)!!.isCompleted)
    }

    @Test fun dateOnlyReplyAnswersReschedulingQuestion() = runBlocking {
        val id = add("Dentist")
        say("reschedule Dentist")
        say("tomorrow at 4pm")
        val date = Calendar.getInstance().apply { timeInMillis = db.taskDao().getTaskById(id)!!.dueDate!! }
        assertEquals(16, date.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun dateOnlyReschedulePreservesExistingClock() = runBlocking {
        val old = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 17); set(Calendar.MINUTE, 45) }.timeInMillis
        val id = add("Dentist", due = old)
        say("reschedule Dentist next week")
        val updated = Calendar.getInstance().apply { timeInMillis = db.taskDao().getTaskById(id)!!.dueDate!! }
        assertEquals(17, updated.get(Calendar.HOUR_OF_DAY)); assertEquals(45, updated.get(Calendar.MINUTE))
    }

    @Test fun removeDueDateCancelsWithoutDeletingTask() = runBlocking {
        say("buy milk tomorrow")
        say("remove due date from it")
        assertNull(allTasks().single().dueDate)
        assertFalse(allTasks().single().isTrashed)
    }

    @Test fun taskRecurrenceCanBeChangedInConversation() = runBlocking {
        say("buy milk tomorrow")
        say("repeat it weekly")
        assertEquals(RecurrencePattern.WEEKLY, allTasks().single().recurrence)
    }

    @Test fun bulkDeleteWaitsForScopedConfirmation() = runBlocking {
        val first = add("One"); val second = add("Two")
        val response = say("delete all tasks")
        assertFalse(db.taskDao().getTaskById(first)!!.isTrashed)
        say(command(response, "Confirm"))
        assertTrue(db.taskDao().getTaskById(first)!!.isTrashed && db.taskDao().getTaskById(second)!!.isTrashed)
    }

    @Test fun bulkPreviewCannotExpandToNewlyAddedTasks() = runBlocking {
        val old = add("Old")
        say("complete all tasks")
        val newer = add("New")
        say("yes")
        assertTrue(db.taskDao().getTaskById(old)!!.isCompleted)
        assertFalse(db.taskDao().getTaskById(newer)!!.isCompleted)
    }

    @Test fun changedTargetInvalidatesConfirmation() = runBlocking {
        val id = add("One")
        say("delete all tasks")
        val original = db.taskDao().getTaskById(id)!!
        db.taskDao().updateTask(original.copy(title = "Newer title", updatedAt = original.updatedAt + 1))
        val result = say("yes")
        assertTrue(result.formattedText.contains("changed"))
        assertFalse(db.taskDao().getTaskById(id)!!.isTrashed)
    }

    @Test fun cancelMakesVisibleConfirmationStale() = runBlocking {
        val id = add("One")
        val prompt = say("delete all tasks")
        say("cancel")
        say(command(prompt, "Confirm"))
        assertFalse(db.taskDao().getTaskById(id)!!.isTrashed)
    }

    @Test fun yesWithoutPendingActionCannotMutate() = runBlocking {
        val id = add("One")
        say("yes")
        assertFalse(db.taskDao().getTaskById(id)!!.isCompleted)
        assertFalse(db.taskDao().getTaskById(id)!!.isTrashed)
    }

    @Test fun undoCreationMovesOnlyCreatedTaskToTrash() = runBlocking {
        val old = add("Existing")
        say("buy milk tomorrow")
        val created = allTasks().first { it.id != old }.id
        say("undo")
        assertTrue(db.taskDao().getTaskById(created)!!.isTrashed)
        assertFalse(db.taskDao().getTaskById(old)!!.isTrashed)
    }

    @Test fun undoCannotOverwriteANewerTaskEdit() = runBlocking {
        say("buy milk tomorrow")
        val task = allTasks().single()
        db.taskDao().updateTask(task.copy(title = "Externally edited", updatedAt = task.updatedAt + 1))
        assertTrue(say("undo").formattedText.contains("newer"))
        assertEquals("Externally edited", db.taskDao().getTaskById(task.id)!!.title)
    }

    @Test fun completionUndoCannotFarmRewards() = runBlocking {
        say("buy milk")
        say("complete it")
        val rewarded = FocusEconomyManager.profileFlow.value.pendingXp
        say("undo")
        say("complete it")
        assertEquals(rewarded, FocusEconomyManager.profileFlow.value.pendingXp)
    }

    @Test fun recurringCompletionAndUndoHandleTheNextOccurrence() = runBlocking {
        say("take medicine every day at 8am")
        val original = allTasks().single().id
        say("complete it")
        assertEquals(2, allTasks().size)
        say("undo")
        assertEquals(1, allTasks().size)
        assertFalse(db.taskDao().getTaskById(original)!!.isCompleted)
    }

    @Test fun settingsAskForValueThenConfirmThenApply() = runBlocking {
        say("change reminder interval")
        say("30 minutes")
        assertEquals(15, context.getSharedPreferences("focus_prefs", 0).getInt("persistent_reminder_interval", 15))
        say("yes")
        assertEquals(30, context.getSharedPreferences("focus_prefs", 0).getInt("persistent_reminder_interval", 15))
    }

    @Test fun settingsRejectNegativeAndOutOfRangeValues() = runBlocking {
        say("set wait timer to -10 seconds")
        say("set wait timer to 900 seconds")
        assertEquals(10, context.getSharedPreferences("focus_prefs", 0).getInt("soft_lock_duration", 10))
    }

    @Test fun unitsAreConvertedWithoutConcatenatingNumbers() = runBlocking {
        say("set wait timer to 1 minute"); say("yes")
        assertEquals(60, context.getSharedPreferences("focus_prefs", 0).getInt("soft_lock_duration", 10))
        say("set reminder interval to 30 or 45")
        assertNotEquals("confirm", AssistantSessionStore(context).load().expected)
    }

    @Test fun clockSettingsValidateAndNormalize() = runBlocking {
        say("set morning briefing time to 9:30pm"); say("yes")
        assertEquals("09:30 PM", context.getSharedPreferences("focus_prefs", 0).getString("morning_brief_time", ""))
        say("set morning briefing time to 25:90"); say("yes")
        assertEquals("09:30 PM", context.getSharedPreferences("focus_prefs", 0).getString("morning_brief_time", ""))
    }

    @Test fun unknownPreferenceCannotBeInjectedThroughAChip() {
        val action = TalkAction.DirectPrefUpdate("isSessionActive", "boolean", "false", "off", "Injected")
        assertFalse(action.execute(context))
    }

    @Test fun directPreferenceChipUsesTheSameValidationAndRequiresReview() = runBlocking {
        val invalid = TalkAction.DirectPrefUpdate("soft_lock_duration", "int", "-10", "-10", "Invalid")
        assertFalse(invalid.execute(context))
        val valid = invalid.copy(targetValue = "20")
        assertTrue(valid.execute(context))
        assertEquals(10, context.getSharedPreferences("focus_prefs", 0).getInt("soft_lock_duration", 10))
        val preview = say(AssistantPreferences.previewQuery(AssistantPreferences.find("wait timer")!!, "20"))
        say(command(preview, "Confirm"))
        assertEquals(20, context.getSharedPreferences("focus_prefs", 0).getInt("soft_lock_duration", 10))
    }

    @Test fun habitCreationIsConfirmedAndLoggingIsCapped() = runBlocking {
        say("add habit water 2 times a day")
        assertTrue(db.habitDao().getAllHabitsSync().isEmpty())
        say("yes")
        val habit = db.habitDao().getAllHabitsSync().single()
        assertEquals("water", habit.title)
        assertEquals(2, habit.targetPerDay)
        repeat(3) { say("log habit water") }
        assertEquals(2, db.habitDao().getAllLogsForHabitSync(habit.id).single().completedCount)
    }

    @Test fun ambiguousHabitsOfferChoicesBeforeLogging() = runBlocking {
        val first = db.habitDao().insertHabit(Habit(title = "Read fiction"))
        val second = db.habitDao().insertHabit(Habit(title = "Read nonfiction"))
        say("log habit read")
        assertTrue(db.habitDao().getAllLogsSync().isEmpty())
        say("second")
        assertTrue(db.habitDao().getAllLogsForHabitSync(first).isEmpty())
        assertEquals(1, db.habitDao().getAllLogsForHabitSync(second).single().completedCount)
    }

    @Test fun routineEnablingReportsScheduleNotImmediateActivation() = runBlocking {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) % 7 + 1
        db.scheduleDao().insertSchedule(FocusSchedule(id = 9, name = "Study", startHour = 9, startMinute = 0, endHour = 10, endMinute = 0, daysOfWeek = day.toString(), isEnabled = false))
        say("enable routine Study")
        val result = say("yes")
        assertTrue(db.scheduleDao().getScheduleById(9)!!.isEnabled)
        assertTrue(result.formattedText.contains("scheduled window"))
    }

    @Test fun focusTimerIsRealAndSharedWithDashboardState() = runBlocking {
        say("start focus for 25 minutes")
        assertTrue(FocusSessionManager.active.value)
        assertTrue(context.getSharedPreferences("focus_prefs", 0).getBoolean("isSessionActive", false))
        assertEquals(1500L, FocusSessionManager.initialSeconds.value)
        assertTrue(say("time remaining").formattedText.contains("remaining"))
        say("stop focus"); assertTrue(FocusSessionManager.active.value)
        say("yes"); assertFalse(FocusSessionManager.active.value)
    }

    @Test fun startingAnotherTimerDoesNotReplaceRunningTimer() = runBlocking {
        say("start focus for 25 minutes")
        say("start focus for 5 minutes")
        assertEquals(1500L, FocusSessionManager.initialSeconds.value)
    }

    @Test fun compoundActionsPreviewBeforeAnyMutation() = runBlocking {
        val preview = say("buy bread; call Mum tomorrow")
        assertTrue(allTasks().isEmpty())
        assertTrue(preview.formattedText.contains("batch"))
        say("yes")
        assertEquals(2, allTasks().size)
    }

    @Test fun compoundModifierAppliesToProspectiveDraft() = runBlocking {
        say("buy milk tomorrow and make it priority")
        assertTrue(allTasks().isEmpty())
        say("yes")
        assertTrue(allTasks().single().isPriority)
    }

    @Test fun unsupportedCompoundMakesNoChanges() = runBlocking {
        say("buy milk; delete an unknown task")
        assertTrue(allTasks().isEmpty())
    }

    @Test fun ordinaryConjunctionAndQuotedTitlesDoNotSplit() {
        assertEquals(listOf("buy bread and butter"), AssistantCompoundParser.split("buy bread and butter"))
        assertEquals(listOf("buy \"War and Peace\"", "call Mum"), AssistantCompoundParser.split("buy \"War and Peace\"; call Mum"))
    }

    @Test fun contextIsEncryptedAndSurvivesReload() = runBlocking {
        say("buy Confidential Milk tomorrow")
        val state = AssistantSessionStore(context).load()
        assertEquals(allTasks().single().id, state.taskId)
        assertFalse(File(context.filesDir, "ayva_conversation_state.enc").readBytes().toString(Charsets.UTF_8).contains("Confidential"))
        say("make it priority")
        assertTrue(allTasks().single().isPriority)
    }

    @Test fun expiredContextCannotReuseAConfirmation() = runBlocking {
        val id = add("Keep")
        val prompt = say("delete all tasks")
        val state = AssistantSessionStore(context).load()
        AssistantSessionStore(context) { 1L }.save(state)
        say(command(prompt, "Confirm"))
        assertFalse(db.taskDao().getTaskById(id)!!.isTrashed)
    }

    @Test fun personalizationCanBeForgottenWithoutDeletingTasks() = runBlocking {
        val id = add("Keep")
        say("call me Raj")
        assertTrue(say("what is my name").formattedText.contains("Raj"))
        say("forget my preferences")
        assertFalse(say("what is my name").formattedText.contains("Raj"))
        assertNotNull(db.taskDao().getTaskById(id))
    }

    @Test fun suggestionsUseActualPriorityAndDoNotChangeDates() = runBlocking {
        add("Normal")
        val important = add("Urgent report", true)
        assertTrue(say("I feel overwhelmed").formattedText.contains("Urgent report"))
        assertNull(db.taskDao().getTaskById(important)!!.dueDate)
    }

    @Test fun arithmeticHasPrecedenceAndDoesNotExecuteCode() {
        assertTrue(AssistantCalculator.answer("calculate 2 + 3 * 4")!!.contains("14"))
        assertNull(AssistantCalculator.answer("calculate Runtime.getRuntime()"))
        assertTrue(runCatching { AssistantCalculator.answer("calculate 4 / 0") }.isFailure)
    }

    @Test fun clearChatAlsoClearsPendingContext() = runBlocking {
        val id = add("Keep")
        val preview = say("delete all tasks")
        OfflineAssistant.respond("/clear", context)
        say(command(preview, "Confirm"))
        assertFalse(db.taskDao().getTaskById(id)!!.isTrashed)
    }

    @Test fun notesStayOutsideTheAssistant() = runBlocking {
        assertTrue(say("read my notes").formattedText.contains("cannot be created, searched, or viewed"))
        assertTrue(allTasks().isEmpty())
    }
    @Test fun dateFirstReminderAsksForTitleAndKeepsItsDate() = runBlocking {
        val response = say("remind me tomorrow at 6pm")
        assertTrue(response.formattedText.contains("What should I remind you"))
        assertTrue(allTasks().isEmpty())
        say("Buy milk")
        val due = Calendar.getInstance().apply { timeInMillis = allTasks().single().dueDate!! }
        assertEquals(18, due.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun invalidSettingSlotCanBeCorrected() = runBlocking {
        say("set wait timer to -10 seconds")
        say("20 seconds"); say("yes")
        assertEquals(20, context.getSharedPreferences("focus_prefs", 0).getInt("soft_lock_duration", 10))
        say("change it to 30 seconds"); say("yes")
        assertEquals(30, context.getSharedPreferences("focus_prefs", 0).getInt("soft_lock_duration", 10))
    }

    @Test fun settingConfirmationRejectsChangesMadeElsewhere() = runBlocking {
        say("set wait timer to 20 seconds")
        context.getSharedPreferences("focus_prefs", 0).edit().putInt("soft_lock_duration", 25).commit()
        assertTrue(say("yes").formattedText.contains("changed"))
        assertEquals(25, context.getSharedPreferences("focus_prefs", 0).getInt("soft_lock_duration", 10))
    }

    @Test fun contradictorySettingValuesRequireClarification() = runBlocking {
        say("turn floating bubble on or off")
        assertEquals("setting_value", AssistantSessionStore(context).load().expected)
        say("set theme to dark or light")
        assertEquals("setting_value", AssistantSessionStore(context).load().expected)
        say("set morning briefing time to -9:00")
        assertEquals("setting_value", AssistantSessionStore(context).load().expected)
    }

    @Test fun habitMultipleLogUndoAndRelogCannotFarmRewards() = runBlocking {
        val id = db.habitDao().insertHabit(Habit(title = "Water", targetPerDay = 3))
        say("log habit water 2 times")
        val rewarded = FocusEconomyManager.profileFlow.value.pendingXp
        assertEquals(2, db.habitDao().getAllLogsForHabitSync(id).single().completedCount)
        say("undo")
        assertEquals(0, db.habitDao().getAllLogsForHabitSync(id).single().completedCount)
        say("log habit water 2 times")
        assertEquals(rewarded, FocusEconomyManager.profileFlow.value.pendingXp)
    }

    @Test fun naturalRoutineNameOrderAndBulkConfirmation() = runBlocking {
        for (id in 1..2) db.scheduleDao().insertSchedule(FocusSchedule(id = id, name = if (id == 1) "Morning" else "Study", startHour = 9, startMinute = 0, endHour = 10, endMinute = 0, daysOfWeek = "1,2,3,4,5,6,7", isEnabled = false))
        say("enable morning routine"); say("yes")
        assertTrue(db.scheduleDao().getScheduleById(1)!!.isEnabled)
        assertFalse(db.scheduleDao().getScheduleById(2)!!.isEnabled)
        say("enable all routines")
        assertFalse(db.scheduleDao().getScheduleById(2)!!.isEnabled)
        say("yes")
        assertTrue(db.scheduleDao().getScheduleById(2)!!.isEnabled)
    }

    @Test fun bulkUndoRestoresEverySelectedTask() = runBlocking {
        val ids = listOf(add("First"), add("Second"), add("Third"))
        say("complete all tasks"); say("yes")
        assertTrue(ids.all { db.taskDao().getTaskById(it)!!.isCompleted })
        say("undo")
        assertTrue(ids.none { db.taskDao().getTaskById(it)!!.isCompleted })
    }

    @Test fun compoundTaskUndoRestoresTheWholeBatch() = runBlocking {
        say("buy milk; call Mum tomorrow"); say("yes")
        assertEquals(2, allTasks().size)
        say("undo")
        assertTrue(allTasks().isEmpty())
    }

    @Test fun focusStopConfirmationCannotStopANewerSession() = runBlocking {
        say("start focus for 25 minutes")
        val preview = say("stop focus")
        FocusSessionManager.stop(context)
        FocusSessionManager.start(context, 5)
        assertTrue(say(command(preview, "Confirm")).formattedText.contains("changed"))
        assertTrue(FocusSessionManager.active.value)
        assertEquals(300L, FocusSessionManager.initialSeconds.value)
    }

    @Test fun expiredTimerRestoresInactiveAndCannotDoubleAccount() = runBlocking {
        say("start focus for 1 minute")
        val prefs = context.getSharedPreferences("focus_prefs", 0)
        prefs.edit().putLong("focus_end_elapsed_ms", android.os.SystemClock.elapsedRealtime() - 1).commit()
        FocusSessionManager.restore(context)
        assertFalse(FocusSessionManager.active.value)
        assertFalse(prefs.getBoolean("isSessionActive", true))
        assertEquals(60L, prefs.getLong("focus_accounted_seconds", 0))
        val rewarded = FocusEconomyManager.profileFlow.value.pendingXp
        FocusSessionManager.restore(context)
        assertEquals(rewarded, FocusEconomyManager.profileFlow.value.pendingXp)
    }

    @Test fun planUsesRunningTimerInsteadOfOfferingAnother() = runBlocking {
        add("Read", priority = true)
        say("start focus for 25 minutes")
        val response = say("what should I do next")
        assertTrue(response.formattedText.contains("already running"))
        assertTrue(response.actions.none { it.label == "Start focus" })
    }

    @Test fun naturalLearningRequestsUseExistingInteractiveFlows() = runBlocking {
        assertEquals("/drill hard 20", say("start math drill hard 20 questions").cliCommandRedirect)
        assertEquals("/vocab", say("teach me a word").cliCommandRedirect)
    }

    @Test fun unrelatedNewTopicExpiresTheOldConfirmation() = runBlocking {
        val id = add("Keep")
        val preview = say("delete all tasks")
        say("what time is it")
        say("yes")
        say(command(preview, "Confirm"))
        assertFalse(db.taskDao().getTaskById(id)!!.isTrashed)
    }

    @Test fun correctionReplacesPendingSettingPreview() = runBlocking {
        val preview = say("set wait timer to 20 seconds")
        say("actually 30 seconds")
        say(command(preview, "Confirm"))
        assertEquals(10, context.getSharedPreferences("focus_prefs", 0).getInt("soft_lock_duration", 10))
        say("yes")
        assertEquals(30, context.getSharedPreferences("focus_prefs", 0).getInt("soft_lock_duration", 10))
    }

    @Test fun habitFollowUpCannotCompleteAnEarlierTask() = runBlocking {
        say("buy milk")
        val taskId = allTasks().single().id
        val habitId = db.habitDao().insertHabit(Habit(title = "Water", targetPerDay = 3))
        say("log habit water")
        say("done")
        assertEquals(2, db.habitDao().getAllLogsForHabitSync(habitId).single().completedCount)
        assertFalse(db.taskDao().getTaskById(taskId)!!.isCompleted)
    }

    @Test fun noteTopicCannotReuseAnEarlierTaskReference() = runBlocking {
        say("buy milk")
        say("read my notes")
        say("delete it")
        assertFalse(allTasks().single().isTrashed)
        assertEquals("choice", AssistantSessionStore(context).load().expected)
    }

    @Test fun changingHabitTargetPreservesTodaysRecordedGoal() = runBlocking {
        val id = db.habitDao().insertHabit(Habit(title = "Water", targetPerDay = 2))
        say("log habit water")
        val habit = db.habitDao().getHabitById(id)!!
        db.habitDao().updateHabit(habit.copy(targetPerDay = 5))
        val response = say("log habit water 4 times")
        val log = db.habitDao().getAllLogsForHabitSync(id).single()
        assertEquals(2, log.targetCount)
        assertEquals(2, log.completedCount)
        assertTrue(response.formattedText.contains("2/2"))
    }

    @Test fun interruptedReminderResumesWithOriginalDateAndComposerFlags() = runBlocking {
        say("remind me tomorrow at 6pm", priority = true, persistent = true)
        say("what time is it")
        assertNull(AssistantSessionStore(context).load().expected)
        say("back to that reminder")
        assertEquals("task_title", AssistantSessionStore(context).load().expected)
        say("call Mum")
        val task = allTasks().single()
        assertEquals("Call Mum", task.title)
        assertTrue(task.isPriority && task.isPersistent)
        assertEquals(18, Calendar.getInstance().apply { timeInMillis = task.dueDate!! }.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun interruptedRescheduleCannotOverwriteAnExternalEdit() = runBlocking {
        val id = add("Dentist")
        say("reschedule Dentist")
        say("what time is it")
        db.taskDao().updateTask(db.taskDao().getTaskById(id)!!.copy(title = "Changed elsewhere", updatedAt = System.currentTimeMillis() + 1))
        assertTrue(say("back to that reminder").formattedText.contains("no unfinished"))
        say("yes")
        assertNull(db.taskDao().getTaskById(id)!!.dueDate)
    }

    @Test fun interruptedConfirmationIsNeverResumable() = runBlocking {
        val id = add("Keep")
        val preview = say("delete all tasks")
        say("what time is it")
        say("back to that reminder")
        say("yes")
        say(command(preview, "Confirm"))
        assertFalse(db.taskDao().getTaskById(id)!!.isTrashed)
    }

    @Test fun userCanSelectAndCorrectTheReferenceWithoutChangingTasks() = runBlocking {
        val first = add("Read fiction")
        val second = add("Read history")
        say("show my tasks")
        say("let's work on the first task")
        assertEquals(first, AssistantSessionStore(context).load().taskId)
        say("No, the other reading one")
        assertEquals(second, AssistantSessionStore(context).load().taskId)
        assertTrue(allTasks().none { it.isCompleted })
        say("done")
        assertTrue(db.taskDao().getTaskById(second)!!.isCompleted)
        assertFalse(db.taskDao().getTaskById(first)!!.isCompleted)
    }

    @Test fun ambiguousOtherTaskAsksRatherThanGuessing() = runBlocking {
        repeat(3) { add("Read volume ${it + 1}") }
        say("show my tasks")
        say("let's work on the first task")
        val answer = say("the other one")
        assertTrue(answer.actions.size >= 2)
        assertTrue(allTasks().none { it.isCompleted })
    }

    @Test fun explicitReturnToTaskDoesNotReuseHabitContext() = runBlocking {
        say("buy milk")
        val task = allTasks().single()
        val habit = db.habitDao().insertHabit(Habit(title = "Water", targetPerDay = 4))
        say("log habit water")
        assertNull(AssistantSessionStore(context).load().taskId)
        say("back to the task")
        assertEquals(task.id, AssistantSessionStore(context).load().taskId)
        say("done")
        assertTrue(db.taskDao().getTaskById(task.id)!!.isCompleted)
        assertEquals(1, db.habitDao().getAllLogsForHabitSync(habit).single().completedCount)
    }

    @Test fun combinedCorrectionWaitsForTimeAndCanBeUndoneTogether() = runBlocking {
        say("remind me to call Mum tomorrow at 6pm")
        val original = allTasks().single()
        val question = say("Actually, Dad instead of Mum, and after lunch")
        assertTrue(question.formattedText.contains("exact day and time"))
        assertEquals(original, db.taskDao().getTaskById(original.id))
        val preview = say("tomorrow at 3pm")
        assertTrue(preview.formattedText.contains("Call Dad"))
        assertEquals(original, db.taskDao().getTaskById(original.id))
        say("yes")
        val corrected = allTasks().single()
        assertEquals(original.id, corrected.id)
        assertEquals("Call Dad", corrected.title)
        assertEquals(15, Calendar.getInstance().apply { timeInMillis = corrected.dueDate!! }.get(Calendar.HOUR_OF_DAY))
        say("undo")
        val restored = db.taskDao().getTaskById(original.id)!!
        assertEquals(original.title, restored.title)
        assertEquals(original.dueDate, restored.dueDate)
    }

    @Test fun combinedCorrectionRejectsTargetChangesDuringClarification() = runBlocking {
        say("call Mum tomorrow")
        val task = allTasks().single()
        say("Actually, Dad instead of Mum, and after lunch")
        db.taskDao().updateTask(task.copy(title = "Edited elsewhere", updatedAt = task.updatedAt + 1))
        assertTrue(say("tomorrow at 3pm").formattedText.contains("changed while"))
        say("yes")
        assertEquals("Edited elsewhere", db.taskDao().getTaskById(task.id)!!.title)
    }

    @Test fun titleCorrectionUsesLiteralReplacementText() = runBlocking {
        say("call Mum tomorrow")
        say("Actually, Dad \$5 instead of Mum")
        assertEquals("Call Dad \$5", allTasks().single().title)
    }

    @Test fun missingReplacementDoesNotInventANewTask() = runBlocking {
        say("call Mum tomorrow")
        assertTrue(say("Actually, Dad instead of Bob").formattedText.contains("Which part"))
        assertEquals("Call Mum", allTasks().single().title)
    }

    @Test fun naturalDrillRequestsAndFollowUpsKeepExistingFeatureDispatch() = runBlocking {
        assertEquals("/drill hard 20", say("Can we do a hard drill with twenty questions?").cliCommandRedirect)
        assertEquals("/drill medium 20", say("easier, same number of questions").cliCommandRedirect)
        assertEquals("/drill hard 20", say("give me a harder round").cliCommandRedirect)
        assertEquals("/drill hard 20", say("another round").cliCommandRedirect)
        assertEquals("/drill hard 20", say("do it again").cliCommandRedirect)
        assertTrue(allTasks().isEmpty())
    }

    @Test fun unsupportedDrillCountsAskInsteadOfSilentlyChangingTheRequest() = runBlocking {
        val response = say("start a hard drill with 15 questions")
        assertNull(response.cliCommandRedirect)
        assertTrue(response.formattedText.contains("10, 20 or unlimited"))
        assertTrue(allTasks().isEmpty())
    }

    @Test fun featureFollowUpAfterUnrelatedTopicDoesNotGuess() = runBlocking {
        say("start drill hard 20")
        say("what time is it")
        assertNull(say("harder").cliCommandRedirect)
        assertEquals("clock", AssistantSessionStore(context).load().topic)
        assertEquals("/drill hard 20", say("repeat my last drill").cliCommandRedirect)
    }

    @Test fun learningResultsComeFromRecordedCompletionAndAreConsumedOnce() = runBlocking {
        say("start drill medium 20")
        val now = System.currentTimeMillis()
        val summary = org.json.JSONObject().put("sessionId", "completed-run").put("difficulty", "medium")
            .put("targetQuestions", 20).put("total", 12).put("correct", 9).put("isBlitz", false).toString()
        OfflineAssistant.recordLearningResult(context, summary, now)
        assertTrue(say("How did I do?").formattedText.contains("9 of 12"))
        assertTrue(say("What was my score?").formattedText.contains("75%"))
        say("what time is it")
        OfflineAssistant.recordLearningResult(context, summary, now)
        assertEquals("clock", AssistantSessionStore(context).load().topic)
        assertEquals("/drill medium 20", say("repeat my last drill").cliCommandRedirect)
        assertEquals("/drill hard 20", say("harder, same number of questions").cliCommandRedirect)
        OfflineAssistant.recordLearningResult(context, summary, now)
        assertNull(AssistantSessionStore(context).load().learning!!.total)
    }

    @Test fun expiredAndMalformedTestResultsAreNotConversationContext() = runBlocking {
        val summary = org.json.JSONObject().put("sessionId", "old-run").put("difficulty", "hard")
            .put("targetQuestions", 20).put("total", 10).put("correct", 11).toString()
        OfflineAssistant.recordLearningResult(context, summary, System.currentTimeMillis())
        assertNull(AssistantSessionStore(context).load().learning)
        OfflineAssistant.recordLearningResult(context, summary.replace("11", "8"), System.currentTimeMillis() - 901_000)
        assertNull(AssistantSessionStore(context).load().learning)
    }

    @Test fun vocabularyAndBlitzFollowUpsRespectTheirDifferentModes() = runBlocking {
        assertEquals("/vocab", say("practice my vocabulary").cliCommandRedirect)
        assertEquals("/vocab learn_more", say("teach me more words").cliCommandRedirect)
        assertEquals("/vocab_quiz", say("quiz me on vocabulary").cliCommandRedirect)
        assertNull(say("harder").cliCommandRedirect)
        assertNull(say("repeat my last drill").cliCommandRedirect)
        assertEquals("/vocab_quiz", say("another round").cliCommandRedirect)
        assertEquals("/blitz", say("give me a speed round").cliCommandRedirect)
        assertEquals("/blitz", say("another round").cliCommandRedirect)
    }

    @Test fun clearHistoryForgetsResumableQuestionsAndLearningConfiguration() = runBlocking {
        say("remind me tomorrow at 6pm")
        say("start drill hard 20")
        OfflineAssistant.respond("/clear", context)
        assertNull(AssistantSessionStore(context).load().suspendedRequest)
        assertNull(AssistantSessionStore(context).load().learning)
        assertTrue(AssistantSessionStore(context).load().recentTaskIds.isEmpty())
        assertNull(say("harder").cliCommandRedirect)
    }

    @Test fun everydayWaterReportUsesRealHabitProgressAndGoalCap() = runBlocking {
        val id = db.habitDao().insertHabit(Habit(title = "Drink water", targetPerDay = 3))
        say("I drank two glasses of water")
        assertEquals(2, db.habitDao().getAllLogsForHabitSync(id).single().completedCount)
        say("one more")
        say("had another glass")
        assertEquals(3, db.habitDao().getAllLogsForHabitSync(id).single().completedCount)
        assertTrue(allTasks().isEmpty())
    }

    @Test fun ambiguousWaterReportRequiresHabitSelection() = runBlocking {
        val first = db.habitDao().insertHabit(Habit(title = "Morning water", targetPerDay = 4))
        val second = db.habitDao().insertHabit(Habit(title = "Evening water", targetPerDay = 4))
        say("I drank two glasses of water")
        assertTrue(db.habitDao().getAllLogsForHabitSync(first).isEmpty())
        assertTrue(db.habitDao().getAllLogsForHabitSync(second).isEmpty())
        say("No, the second one")
        assertEquals(2, db.habitDao().getAllLogsForHabitSync(second).single().completedCount)
        assertTrue(db.habitDao().getAllLogsForHabitSync(first).isEmpty())
    }

    @Test fun waterVolumeDoesNotBecomeAHabitCountOrATask() = runBlocking {
        val id = db.habitDao().insertHabit(Habit(title = "Water", targetPerDay = 4))
        val response = say("I drank 2 litres of water")
        assertTrue(response.formattedText.contains("How many glasses"))
        assertTrue(db.habitDao().getAllLogsForHabitSync(id).isEmpty())
        assertTrue(allTasks().isEmpty())
    }

    @Test fun explicitReminderAboutLearningCannotLaunchPractice() = runBlocking {
        say("remind me to do a hard drill with 20 questions tomorrow")
        assertEquals(1, allTasks().size)
        assertNull(AssistantSessionStore(context).load().learning)
    }

    @Test fun implicitHabitReferenceCannotReenterAnEarlierTaskList() = runBlocking {
        val first = add("Read fiction")
        val second = add("Read history")
        say("show my tasks")
        say("let's work on the first task")
        val habit = db.habitDao().insertHabit(Habit(title = "Water", targetPerDay = 4))
        say("log habit water")
        assertTrue(say("the other one").formattedText.contains("Which habit"))
        assertNull(AssistantSessionStore(context).load().taskId)
        say("done")
        assertFalse(db.taskDao().getTaskById(first)!!.isCompleted)
        assertFalse(db.taskDao().getTaskById(second)!!.isCompleted)
        assertEquals(2, db.habitDao().getAllLogsForHabitSync(habit).single().completedCount)
    }

    @Test fun singularGlassUsesTheSameHabitLoggingPath() = runBlocking {
        val id = db.habitDao().insertHabit(Habit(title = "Water", targetPerDay = 4))
        say("I had a glass of water")
        assertEquals(1, db.habitDao().getAllLogsForHabitSync(id).single().completedCount)
    }

    @Test fun negatedDateCorrectionPreservesTheExistingClock() = runBlocking {
        say("call Mum Friday at 6pm")
        val task = allTasks().single()
        say("Not Friday—Monday. Keep the time")
        val updated = db.taskDao().getTaskById(task.id)!!
        assertEquals(1, allTasks().size)
        val date = Calendar.getInstance().apply { timeInMillis = updated.dueDate!! }
        assertEquals(Calendar.MONDAY, date.get(Calendar.DAY_OF_WEEK))
        assertEquals(18, date.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun legacyFaqHistoryCannotOverrideTheCanonicalTopic() = runBlocking {
        AyvaTalkEngine.answerTalkQueryWithActions("soft mode wait timer", context)
        assertEquals("setting:soft_lock_duration", AssistantSessionStore(context).load().topic)
        say("what time is it")
        val response = AyvaTalkEngine.answerTalkQueryWithActions("how to change it?", context)
        assertFalse(response.formattedText.contains("Soft Mode Wait Timer"))
    }

    @Test fun otherReadingReferenceCannotChooseAnUnrelatedTask() = runBlocking {
        val reading = add("Read fiction")
        val groceries = add("Buy groceries")
        say("show my tasks")
        say("let's work on the first task")
        say("the other reading one")
        assertNull(AssistantSessionStore(context).load().taskId)
        say("done")
        assertFalse(db.taskDao().getTaskById(reading)!!.isCompleted)
        assertFalse(db.taskDao().getTaskById(groceries)!!.isCompleted)
    }

    @Test fun habitReportInterruptsAMissingHabitTitleAndCanResumeIt() = runBlocking {
        val water = db.habitDao().insertHabit(Habit(title = "Water", targetPerDay = 4))
        say("add habit")
        say("I drank two glasses of water")
        assertEquals(2, db.habitDao().getAllLogsForHabitSync(water).single().completedCount)
        assertEquals(1, db.habitDao().getAllHabitsSync().size)
        say("continue that request")
        say("Stretch")
        say("yes")
        assertEquals(setOf("Water", "Stretch"), db.habitDao().getAllHabitsSync().map { it.title }.toSet())
    }

    @Test fun forgettingContextDoesNotReimportAnAlreadySeenTestCard() = runBlocking {
        val timestamp = System.currentTimeMillis()
        val summary = org.json.JSONObject().put("sessionId", "seen-result").put("difficulty", "medium")
            .put("targetQuestions", 20).put("total", 10).put("correct", 8).toString()
        OfflineAssistant.recordLearningResult(context, summary, timestamp)
        say("forget my preferences")
        OfflineAssistant.recordLearningResult(context, summary, timestamp)
        assertNull(AssistantSessionStore(context).load().learning)
        assertNull(say("harder").cliCommandRedirect)
    }

}
