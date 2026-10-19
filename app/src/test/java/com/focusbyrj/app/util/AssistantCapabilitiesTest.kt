package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.FocusSchedule
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.assistant.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AssistantCapabilitiesTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    @Before fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        for (owner in listOf("focus_prefs", "bubble_prefs")) context.getSharedPreferences(owner, 0).edit().clear().commit()
        AssistantSessionStore(context).clear()
        AyvaTalkEngine.resetConversation()
        FocusEconomyManager.init(context)
    }
    @After fun tearDown() {
        AssistantSessionStore(context).clear()
        AyvaTalkEngine.resetConversation()
        db.close()
    }
    private suspend fun say(text: String) = AyvaTalkEngine.answerTalkQueryWithActions(text, context)
    private fun confirm(reply: AyvaTalkEngine.TalkResponse) = reply.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva confirm ") }.query
    private fun pending() = AssistantSessionStore(context).load()
    private suspend fun addRoutine(name: String): Int {
        db.scheduleDao().insertSchedule(FocusSchedule(name = name, startHour = 7, startMinute = 0, endHour = 8, endMinute = 0, daysOfWeek = "2,3,4,5,6", mode = "SOFT", isEnabled = false))
        return db.scheduleDao().getAllSchedulesSync().single { it.name == name }.id
    }

    @Test fun catalogueHasUniqueStableIdsAndNoPrivateDataWriteCapability() {
        val entries = AssistantCapabilities.all
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        assertEquals(AssistantCapabilities.domains.keys, entries.map { it.domain }.toSet())
        assertTrue(entries.filter { it.access == CapabilityAccess.WRITE }.all { it.required.isNotEmpty() || it.id == "focus.stop" })
        assertEquals(CapabilityAccess.UNAVAILABLE, entries.single { it.id == "private_data.excluded" }.access)
        assertTrue(entries.none { it.access == CapabilityAccess.WRITE && (it.id.startsWith("private_data") || it.domain == "backup") })
    }

    @Test fun everySettingUsesItsAuthoritativeOwnerAndHasBothContracts() = runBlocking {
        val bubble = context.getSharedPreferences("bubble_prefs", 0)
        val focus = context.getSharedPreferences("focus_prefs", 0)
        bubble.edit().putInt("bubble_glow_intensity", 25).commit()
        focus.edit().putInt("bubble_glow_intensity", 90).commit()
        val capability = AssistantCapabilities.setting("bubble_glow_intensity")
        assertEquals("25", capability.current(context))
        assertEquals(CapabilityAccess.READ, capability.readContract.access)
        assertEquals(CapabilityReview.ALWAYS, capability.writeContract.review)
        assertEquals("bubble_prefs", capability.spec.owner)
        val info = say("/talk settings bubble")
        assertTrue(info.formattedText, info.formattedText.contains("25 %"))
        assertFalse(info.formattedText.contains("90 %"))
        assertEquals(AssistantPreferences.specs.map { it.key }.toSet(), AssistantCapabilities.settings.map { it.spec.key }.toSet())
    }

    @Test fun allPublishedSettingChoicesProducePreviewsNotWrites() = runBlocking {
        for (capability in AssistantCapabilities.settings) {
            val before = capability.current(context)
            val value = AssistantPreferences.presets(capability.spec).first { it != before }
            val turn = say(capability.query(capability.spec.type, value))
            assertEquals(before, capability.current(context))
            val action = pending().pending.single() as AssistantAction.SetPreference
            assertEquals(capability.spec.key, action.key)
            assertEquals(value, action.value)
            assertEquals(CapabilityReview.ALWAYS, AssistantCapabilities.forAction(action).review)
            assertTrue(turn.formattedText, turn.formattedText.contains("Proposed"))
            say("cancel")
        }
    }

    @Test fun legacySettingExecuteOnlyOpensTheSameValidatedReviewFlow() = runBlocking {
        val chip = TalkAction.DirectPrefUpdate("soft_lock_duration", "int", "30", "30 seconds", "Change wait")
        assertTrue(chip.execute(context))
        assertEquals("10", AssistantCapabilities.setting(chip.prefKey).current(context))
        val launched = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        val query = launched.getStringExtra("prefill_query")!!
        assertEquals((AssistantCapabilities.reviewedChip(chip) as TalkAction.AskQuery).query, query)
        val preview = say(query)
        assertEquals("10", AssistantCapabilities.setting(chip.prefKey).current(context))
        say(confirm(preview))
        assertEquals("30", AssistantCapabilities.setting(chip.prefKey).current(context))
        say("undo")
        assertEquals("10", AssistantCapabilities.setting(chip.prefKey).current(context))
    }

    @Test fun unknownKeysWrongTypesAndInvalidValuesCannotBypassTheContract() = runBlocking {
        for (chip in listOf(
            TalkAction.DirectPrefUpdate("isSessionActive", "boolean", "true", "", "Bad"),
            TalkAction.DirectPrefUpdate("soft_lock_duration", "boolean", "true", "", "Bad"),
            TalkAction.DirectPrefUpdate("soft_lock_duration", "int", "999", "", "Bad")
        )) {
            assertFalse(chip.execute(context))
            assertFalse(chip.executeAsync(context))
        }
        for (action in listOf(AssistantAction.SetPreference("isSessionActive", "boolean", "true"), AssistantAction.SetPreference("soft_lock_duration", "string", "30"))) {
            assertTrue(runCatching { AssistantActionExecutor.execute(context, action) }.isFailure)
        }
        assertFalse(context.getSharedPreferences("focus_prefs", 0).contains("isSessionActive"))
        assertEquals("10", AssistantCapabilities.setting("soft_lock_duration").current(context))
    }

    @Test fun routineChipUsesStableIdentityAndNeedsConfirmation() = runBlocking {
        val id = addRoutine("Study / odd name")
        val chip = TalkAction.RoutineToggle(id, true, "Ignore this injected display name", "Enable")
        assertTrue(chip.executeAsync(context))
        assertFalse(db.scheduleDao().getScheduleById(id)!!.isEnabled)
        val query = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity.getStringExtra("prefill_query")!!
        assertEquals("/talk enable routine id:$id", query)
        val preview = say(query)
        assertFalse(db.scheduleDao().getScheduleById(id)!!.isEnabled)
        say(confirm(preview))
        assertTrue(db.scheduleDao().getScheduleById(id)!!.isEnabled)
    }

    @Test fun selectingAnAmbiguousRoutineDoesNotSkipItsRequiredReview() = runBlocking {
        val first = addRoutine("Study one"); val second = addRoutine("Study two")
        val choices = say("enable routine Study")
        val choose = choices.actions.filterIsInstance<TalkAction.AskQuery>().first { it.label == "Study two" }.query
        val preview = say(choose)
        assertTrue(preview.formattedText, preview.formattedText.contains("Study two"))
        assertFalse(db.scheduleDao().getScheduleById(first)!!.isEnabled)
        assertFalse(db.scheduleDao().getScheduleById(second)!!.isEnabled)
        say(confirm(preview))
        assertFalse(db.scheduleDao().getScheduleById(first)!!.isEnabled)
        assertTrue(db.scheduleDao().getScheduleById(second)!!.isEnabled)
    }

    @Test fun capabilityHelpDescribesCurrentFeaturesAndNeverMutatesTasks() = runBlocking {
        val id = db.taskDao().insertTask(Task(title = "Keep me"))
        val old = confirm(say("delete all tasks"))
        val help = say("/talk capabilities")
        assertEquals(AssistantCapabilities.domains.size, help.actions.size)
        for (domain in AssistantCapabilities.domains.keys) {
            val reply = say("/talk capabilities $domain")
            assertTrue(reply.formattedText.isNotBlank())
            assertFalse(db.taskDao().getTaskById(id)!!.isTrashed)
            assertNull(pending().confirmationId)
        }
        assertTrue(say("/talk capabilities planning").formattedText.contains("No invented syllabus"))
        assertTrue(say("/talk capabilities learning").formattedText.contains("not arbitrary sentences"))
        say(old)
        assertFalse(db.taskDao().getTaskById(id)!!.isTrashed)
    }

    @Test fun canonicalSettingButtonsStillRejectAnExternalEditAtConfirmation() = runBlocking {
        val capability = AssistantCapabilities.setting("persistent_reminder_interval")
        val preview = say(capability.query("int", "30"))
        context.getSharedPreferences(capability.spec.owner, 0).edit().putInt(capability.spec.key, 45).commit()
        assertTrue(say(confirm(preview)).formattedText.contains("changed"))
        assertEquals("45", capability.current(context))
    }

    @Test fun clearTaskCreationAndSingleTaskEditsKeepTheirExistingBehavior() = runBlocking {
        say("buy milk tomorrow")
        val task = db.taskDao().getAllActiveTasksList().single()
        say("rename it to Buy oat milk")
        assertEquals("Buy oat milk", db.taskDao().getTaskById(task.id)!!.title)
        assertNull(pending().confirmationId)
        assertFalse(AssistantCapabilities.requiresReview(AssistantAction.CreateTask(Task(title = "Milk"))))
        assertTrue(AssistantCapabilities.requiresReview(AssistantAction.TrashTasks(listOf(1, 2))))
    }
    @Test fun usageCapabilityReportsTheActualPermissionPrerequisite() = runBlocking {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        shadowOf(ops).setMode(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName, android.app.AppOpsManager.MODE_ERRORED)
        assertEquals(false, AssistantCapabilities.availability(context, "usage.read").available)
        assertTrue(say("/talk capabilities device").formattedText.contains("Usage Access is missing"))
        shadowOf(ops).setMode(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName, android.app.AppOpsManager.MODE_ALLOWED)
        assertEquals(true, AssistantCapabilities.availability(context, "usage.read").available)
        assertFalse(say("/talk capabilities device").formattedText.contains("Usage Access is missing"))
    }

    @Test fun unavailableFeaturesHaveNoExecutionAuthorityAndSpeechLimitsAreHonest() {
        assertEquals(true, AssistantCapabilities.availability(context, "planning.projects").available)
        assertEquals(false, AssistantCapabilities.availability(context, "unknown.write").available)
        val speech = AssistantCapabilities.availability(context, "transcript.capture")
        assertEquals(false, speech.available)
        assertTrue(speech.reason.contains("Android 12"))
        assertEquals(true, AssistantCapabilities.availability(context, "transcript.open").available)
    }

    @Test fun contextualSettingReadsAndChangeBackUseLiveOwnerAndMatchingUndo() = runBlocking {
        val setting = AssistantCapabilities.setting("persistent_reminder_interval")
        val before = setting.current(context)
        val proposed = if (before == "30") "45" else "30"
        say(confirm(say(setting.query("int", proposed))))
        assertTrue(say("what is it set to now").formattedText.contains(proposed))
        assertTrue(say("what does that do").formattedText.isNotBlank())
        say("change it back"); assertEquals(before, setting.current(context))
        val id = db.taskDao().insertTask(Task(title = "Keep task"))
        say("show my tasks"); say("/talk explain reminder interval"); say("change it back")
        assertFalse(db.taskDao().getTaskById(id)!!.isTrashed)
    }

    @Test fun executorRechecksReviewedTaskInsideTransaction() = runBlocking {
        val id = db.taskDao().insertTask(Task(title = "Original"))
        val before = db.taskDao().getTaskById(id)!!
        db.taskDao().updateTask(before.copy(title = "Changed externally"))
        try {
            AssistantActionExecutor.execute(context, AssistantAction.TrashTasks(listOf(id)), mapOf("task:$id" to TaskJson.encode(before).toString()))
            fail("Expected stale write rejection")
        } catch (_: IllegalArgumentException) {}
        assertFalse(db.taskDao().getTaskById(id)!!.isTrashed)
        assertEquals("Changed externally", db.taskDao().getTaskById(id)!!.title)
    }
    @Test fun routineToggleHasGuardedUndoAndRefusesExternalChanges() = runBlocking {
        val id = addRoutine("Reading")
        say(confirm(say("enable routine id:$id")))
        assertTrue(db.scheduleDao().getScheduleById(id)!!.isEnabled)
        say("undo"); assertFalse(db.scheduleDao().getScheduleById(id)!!.isEnabled)
        val preview = say("enable routine id:$id")
        db.scheduleDao().insertSchedule(db.scheduleDao().getScheduleById(id)!!.copy(name = "Updated outside chat"))
        say(confirm(preview)); assertFalse(db.scheduleDao().getScheduleById(id)!!.isEnabled)
    }

}
