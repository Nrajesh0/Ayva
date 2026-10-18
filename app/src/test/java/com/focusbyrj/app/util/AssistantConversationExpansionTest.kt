package com.focusbyrj.app.util

import android.app.Application
import android.content.*
import android.content.pm.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.util.assistant.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AssistantConversationExpansionTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    private val pkg = "com.example.reader"
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        for (name in listOf("focus_prefs", "bubble_prefs", "ayva_action_receipts", "ayva_personalization")) context.getSharedPreferences(name, 0).edit().clear().commit()
        AssistantSessionStore(context).clear(); FocusEconomyManager.init(context); FocusSessionManager.stop(context)
        val info = ApplicationInfo().apply { packageName = pkg; enabled = true; nonLocalizedLabel = "Reader" }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = info })
        @Suppress("DEPRECATION")
        shadowOf(context.packageManager).setResolveInfosForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), listOf(ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Main"; enabled = true; applicationInfo = info } }))
    }
    @After fun cleanup() { AssistantSessionStore(context).clear(); FocusSessionManager.stop(context); db.close() }
    private suspend fun say(text: String) = OfflineAssistant.respond(text, context) ?: error("Not handled: $text")
    private fun state() = AssistantSessionStore(context).load()
    private fun confirm(r: AyvaTalkEngine.TalkResponse) = r.actions.filterIsInstance<TalkAction.AskQuery>().firstOrNull { it.query.startsWith("/ayva confirm ") }?.query ?: error("No confirmation: ${r.formattedText}")
    private fun undo(r: AyvaTalkEngine.TalkResponse) = r.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva undo ") }.query
    private suspend fun routine() = db.scheduleDao().getAllSchedulesSync().single()
    private suspend fun addRoutine(name: String = "Study") {
        db.scheduleDao().insertSchedule(FocusSchedule(name = name, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0, daysOfWeek = "2,3,4,5,6", mode = "SOFT", appsToBlock = pkg))
    }
    private suspend fun addHabit(): Long {
        val id = db.habitDao().insertHabit(Habit(title = "Reading"))
        OfflineAssistant.selectReference(context, OfflineAssistant.availableReferences(context).first { it.kind == "habit" && it.id == id })
        return id
    }

    @Test fun clarificationPreservesTaskSlotAndDoesNotBecomeTheTaskTitle() = runBlocking {
        say("remind me tomorrow at 6pm")
        val pending = state().pending.single()
        for (question in listOf("what do you need from me", "why are you asking", "what now", "continue")) {
            assertTrue(say(question).formattedText.contains("title"))
            assertEquals(pending, state().pending.single()); assertEquals("task_title", state().expected)
        }
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        say("buy milk")
        assertEquals("Buy milk", db.taskDao().getAllActiveTasksList().single().title)
    }
    @Test fun previewQuestionsAndRepeatingAreReadOnlyAndKeepTheCurrentNonce() = runBlocking {
        val original = say("/talk set reminder interval to 30 minutes")
        val nonce = state().confirmationId
        val explanation = say("what will change")
        assertTrue(explanation.formattedText.contains("current 15 minutes → proposed 30 minutes"))
        assertEquals(nonce, state().confirmationId)
        assertEquals(explanation.formattedText, say("repeat that").formattedText)
        assertEquals("15", AssistantPreferences.read(context, AssistantPreferences.find("reminder interval")!!))
        say(confirm(original))
        assertEquals("30", AssistantPreferences.read(context, AssistantPreferences.find("reminder interval")!!))
        assertTrue(say("what will change").formattedText.contains("no pending"))
    }
    @Test fun correctionGuidanceMatchesTheFeatureAndCancelsTheOldPreview() = runBlocking {
        val original = say("set reminder interval to 30 minutes")
        val response = say("you misunderstood")
        assertTrue(response.formattedText.contains("setting")); assertFalse(response.formattedText.contains("task and"))
        assertNull(state().confirmationId); say(confirm(original)); say("yes")
        assertEquals("15", AssistantPreferences.read(context, AssistantPreferences.find("reminder interval")!!))
    }
    @Test fun recapCanFindAnUnfinishedQuestionWithoutResumingOrApplyingIt() = runBlocking {
        say("remind me tomorrow"); say("what time is it")
        assertTrue(say("where were we").formattedText.contains("back to that reminder"))
        assertNull(state().expected)
        say("back to that reminder"); assertEquals("task_title", state().expected)
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }
    @Test fun choiceClarificationPreservesTheDisplayedChoices() = runBlocking {
        db.taskDao().insertTask(Task(title = "Read chapter")); db.taskDao().insertTask(Task(title = "Read notes"))
        say("reschedule Read")
        val choices = state().choices; assertEquals(2, choices.size)
        assertTrue(say("what are my options").formattedText.contains("number"))
        assertEquals(choices, state().choices)
        assertTrue(db.taskDao().getAllActiveTasksList().all { it.dueDate == null })
    }
    @Test fun routineInlineEditsAndPronounsUseTheSelectedRoutineAndSupportUndo() = runBlocking {
        addRoutine(); val before = routine()
        say("show routine Study")
        val preview = say("change its days to weekends")
        assertEquals(before, routine())
        val saved = say(confirm(preview)); assertEquals("1,7", routine().daysOfWeek)
        say(undo(saved)); assertEquals(before, routine())
    }
    @Test fun routinePreviewCanReviseSeveralFieldsWithoutLosingEarlierEdits() = runBlocking {
        addRoutine(); val before = routine()
        val old = say("change routine Study days to weekends")
        say("change its time to 7pm to 9pm")
        val revised = say("make it soft")
        val action = state().pending.single() as AssistantAction.SaveRoutine
        assertEquals("1,7", action.draft.daysOfWeek); assertEquals(19, action.draft.startHour)
        assertEquals(before, action.expected); assertEquals(before, routine())
        say(confirm(old)); assertEquals(before, routine())
        assertTrue(say("what will change").formattedText.contains("19:00–21:00"))
        say(confirm(revised)); assertEquals(19, routine().startHour); assertEquals("1,7", routine().daysOfWeek)
    }
    @Test fun routineGuideAllowsCorrectingEarlierFieldsWhileKeepingItsCurrentQuestion() = runBlocking {
        say("create routine Study"); say("weekdays")
        assertTrue(say("actually weekends").formattedText.contains("time window"))
        say("change its days to sometimes")
        assertEquals("days", state().featureState!!.getJSONObject("routineDraft").getString("field"))
        assertTrue(say("weekends").formattedText.contains("time window"))
        say("7pm to 9pm"); say("Reader")
        val preview = say("soft")
        assertTrue(db.scheduleDao().getAllSchedulesSync().isEmpty())
        say(confirm(preview)); assertEquals("1,7", routine().daysOfWeek); assertEquals(19, routine().startHour)
        assertTrue(say("when does it run").formattedText.contains("19:00–21:00"))
    }
    @Test fun invalidRoutineRevisionInvalidatesOldConfirmationAndRetainsOtherDraftValues() = runBlocking {
        addRoutine(); val old = say("change routine Study days to weekends")
        say("change its time to 25pm to 27pm")
        assertEquals("feature_value", state().expected); assertNull(state().confirmationId)
        say(confirm(old)); assertEquals("2,3,4,5,6", routine().daysOfWeek)
        val fixed = say("7pm to 9pm"); say(confirm(fixed))
        assertEquals("1,7", routine().daysOfWeek); assertEquals(19, routine().startHour)
    }
    @Test fun staleRoutineRevisionAndStaleReferenceCannotOverwriteAnExternalEdit() = runBlocking {
        addRoutine(); val old = say("change routine Study days to weekends")
        db.scheduleDao().insertSchedule(routine().copy(name = "Changed elsewhere"))
        assertTrue(say("make it soft").formattedText.contains("changed while"))
        say(confirm(old)); assertEquals("Changed elsewhere", routine().name)
        assertEquals("2,3,4,5,6", routine().daysOfWeek)
    }
    @Test fun activeStrictRoutineStillRejectsContextualEdits() = runBlocking {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        db.scheduleDao().insertSchedule(FocusSchedule(name = "Study", startHour = (hour + 23) % 24, startMinute = 0, endHour = (hour + 1) % 24, endMinute = 0, daysOfWeek = "1,2,3,4,5,6,7", mode = "HARD", appsToBlock = pkg))
        say("show routine Study")
        assertTrue(say("make it soft").formattedText.contains("strict")); assertNull(state().expected)
        assertEquals("HARD", routine().mode)
    }
    @Test fun routineReferenceExpiresIndependentlyAndListsReleaseSingularContext() = runBlocking {
        addRoutine(); say("show routine Study")
        val store = AssistantSessionStore(context); val session = store.load()
        session.featureState!!.getJSONObject("routineRef").put("at", System.currentTimeMillis() - 900_001L); store.save(session)
        assertTrue(say("change its days to weekends").formattedText.contains("Which routine"))
        assertNull(state().expected); assertEquals("2,3,4,5,6", routine().daysOfWeek)
        say("show routine Study"); say("show routines")
        assertTrue(say("edit it").formattedText.contains("Which routine"))
    }
    @Test fun routineDraftReadsKeepTheConfirmationAndDistinguishSavedFromProposedValues() = runBlocking {
        addRoutine(); val preview = say("change routine Study time to 7pm to 9pm")
        val id = state().confirmationId
        assertTrue(say("when does it run").formattedText.contains("draft, not saved"))
        assertEquals(id, state().confirmationId)
        assertEquals(9, routine().startHour)
        say(confirm(preview)); assertEquals(19, routine().startHour)
    }
    @Test fun routineEnableAndPauseFollowUpsAreConfirmedAndUseSavedSchedules() = runBlocking {
        addRoutine(); say("show routine Study")
        val preview = say("pause it"); assertTrue(routine().isEnabled)
        val revised = say("make it soft")
        say(confirm(preview)); assertTrue(routine().isEnabled)
        say(confirm(revised)); assertFalse(routine().isEnabled)
        say(confirm(say("resume it"))); assertTrue(routine().isEnabled)
        addRoutine("Rest")
        val group = say("disable routine all")
        assertTrue(say("edit it").formattedText.contains("Which routine"))
        say(confirm(group)); assertTrue(db.scheduleDao().getAllSchedulesSync().all { it.isEnabled })
    }
    @Test fun habitScheduleReadsAndFollowUpsUseItsSavedValuesInsteadOfGlobalSettings() = runBlocking {
        val id = addHabit()
        assertTrue(say("what is its schedule").formattedText.contains("Once daily at 09:00"))
        val preview = say("change its reminder time to 7pm")
        val nonce = state().confirmationId
        val explanation = say("what will change").formattedText
        assertTrue(explanation.contains("19:00")); assertFalse(explanation.contains("@1140"))
        assertTrue(say("when does it remind me").formattedText.contains("currently saved"))
        assertEquals(nonce, state().confirmationId)
        assertEquals(9, db.habitDao().getHabitById(id)!!.fixedReminderHour)
        say(confirm(preview)); assertEquals(19, db.habitDao().getHabitById(id)!!.fixedReminderHour)
        say(confirm(say("turn its reminders off"))); assertFalse(db.habitDao().getHabitById(id)!!.isReminderEnabled)
    }
    @Test fun habitPreviewCorrectionsKeepTheOriginalSnapshotAndReplaceTheNonce() = runBlocking {
        val id = addHabit(); val before = db.habitDao().getHabitById(id)!!
        val old = say("change its interval to every 60 minutes")
        val revised = say("actually 90 minutes")
        assertEquals(before, (state().pending.single() as AssistantAction.EditHabit).expected)
        say(confirm(old)); assertEquals(before, db.habitDao().getHabitById(id))
        say(confirm(revised)); assertEquals(90, db.habitDao().getHabitById(id)!!.totalIntervalMinutes)
    }
    @Test fun invalidHabitCorrectionRequiresAValueAndCannotReuseAnOldButton() = runBlocking {
        val id = addHabit(); val old = say("change its reminder time to 7pm")
        say("actually 27pm"); assertEquals("habit_edit_value", state().expected)
        say(confirm(old)); assertEquals(9, db.habitDao().getHabitById(id)!!.fixedReminderHour)
        val fixed = say("at 8pm"); say(confirm(fixed))
        assertEquals(20, db.habitDao().getHabitById(id)!!.fixedReminderHour)
    }
    @Test fun habitCorrectionCannotOverwriteAnExternalEdit() = runBlocking {
        val id = addHabit(); val old = say("change its interval to 60 minutes")
        db.habitDao().updateHabit(db.habitDao().getHabitById(id)!!.copy(title = "External"))
        assertTrue(say("actually 90 minutes").formattedText.contains("changed while"))
        say(confirm(old)); assertEquals("External", db.habitDao().getHabitById(id)!!.title)
        assertEquals(HabitType.ONCE_DAILY, db.habitDao().getHabitById(id)!!.type)
    }
    @Test fun featureReferencesDoNotLeakAcrossTopics() = runBlocking {
        addRoutine(); say("show routine Study"); addHabit()
        say("change its interval to 90 minutes")
        assertTrue(state().pending.single() is AssistantAction.EditHabit)
        assertFalse(state().featureState?.has("routineRef") == true)
        say("cancel"); say("/talk reminder interval"); say("make it 30 minutes")
        assertTrue(state().pending.single() is AssistantAction.SetPreference)
        assertEquals("2,3,4,5,6", routine().daysOfWeek)
    }
    @Test fun negatedLocksAndQualifiedConfirmationCannotApplyThePreviousPreview() = runBlocking {
        val old = say("lock all")
        say("don't unlock all"); say(confirm(old)); say("yes")
        assertTrue(db.appRestrictionDao().getAllRestrictionsSync().isEmpty())
        say("lock all")
        say("yes but make them soft")
        assertTrue(db.appRestrictionDao().getAllRestrictionsSync().isEmpty())
        assertEquals("SOFT", (state().pending.single() as AssistantAction.SetAppRules).rules.single().draft.mode)
    }
}
