package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.util.assistant.*
import com.focusbyrj.app.util.router.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AssistantAppKnowledgeTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        for (name in listOf("focus_prefs", "bubble_prefs", "ayva_action_receipts", "ayva_personalization")) context.getSharedPreferences(name, 0).edit().clear().commit()
        AssistantSessionStore(context).clear(); FocusEconomyManager.init(context)
    }
    @After fun cleanup() { AssistantSessionStore(context).clear(); db.close() }
    private suspend fun say(text: String) = OfflineAssistant.respond(text, context) ?: error("Not handled: $text")
    private fun state() = AssistantSessionStore(context).load()
    private fun confirm(response: AyvaTalkEngine.TalkResponse) = response.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva confirm ") }.query

    @Test fun talkNeverCreatesImplicitTasksButOrdinaryAndExplicitCreationStillWork() = runBlocking {
        for (query in listOf("/talk buy milk tomorrow", "/talk remind me to call mum", "/talk remind me tomorrow", "/talk invent a setting for flying", "/talk can you make coffee", "/talk set wait timer to 30 and delete all tasks")) say(query)
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        assertFalse(state().expected == "task_title")
        assertEquals(RouterDestination.DirectCommand("/talk buy milk"), AyvaIntentRouter.route("/talk buy milk"))
        say("buy milk tomorrow"); say("/create change theme tomorrow")
        assertEquals(2, db.taskDao().getAllActiveTasksList().size)
        say("change reminder interval"); say("buy paper tomorrow")
        assertEquals(3, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun settingInformationClearsOldTaskReferenceAndChangeItUsesTheSetting() = runBlocking {
        say("buy milk tomorrow")
        val task = db.taskDao().getAllActiveTasksList().single()
        val info = say("/talk how does reminder interval work")
        assertEquals("persistent_reminder_interval", AssistantResponseContent.settingSpec(info.jsonPayload)?.key)
        assertNull(state().taskId)
        val preview = say("make it 30 minutes")
        assertEquals(task, db.taskDao().getTaskById(task.id))
        assertEquals("15", AssistantPreferences.read(context, AssistantPreferences.find("reminder interval")!!))
        say(confirm(preview))
        assertEquals("30", AssistantPreferences.read(context, AssistantPreferences.find("reminder interval")!!))
        assertEquals(task, db.taskDao().getTaskById(task.id))
    }
    @Test fun questionsAboutTurningOffAreReadOnlyAndExplicitChangeIsConfirmed() = runBlocking {
        val info = say("/talk how do I turn off practice reminders?")
        assertTrue(info.formattedText.contains("Currently"))
        assertNull(state().expected)
        val preview = say("turn it off")
        assertEquals("true", AssistantPreferences.read(context, AssistantPreferences.find("practice reminders")!!))
        say(confirm(preview))
        assertFalse(context.getSharedPreferences("bubble_prefs", 0).getBoolean("streak_notification_enabled", true))
    }
    @Test fun bubbleValuesUseTheirRealOwnerEvenWhenLegacyCopiesDisagree() = runBlocking {
        val bubble = context.getSharedPreferences("bubble_prefs", 0)
        val focus = context.getSharedPreferences("focus_prefs", 0)
        bubble.edit().putBoolean("vacation_mode", true).putString("morning_brief_time", "07:15 AM").putBoolean("streak_notification_enabled", false).commit()
        focus.edit().putBoolean("vacation_mode", false).putString("morning_brief_time", "11:00 AM").putBoolean("streak_notification_enabled", true).commit()
        assertTrue(say("/talk vacation mode").formattedText.contains("Currently **On**"))
        assertTrue(say("/talk morning briefing time").formattedText.contains("07:15 AM"))
        assertTrue(say("/talk practice reminders").formattedText.contains("Currently **Off**"))
    }
    @Test fun randomPracticeTimeHasNoInventedDefaultAndCanBeRestored() = runBlocking {
        val spec = AssistantPreferences.find("practice reminder time")!!
        assertEquals("", AssistantPreferences.read(context, spec))
        assertTrue(say("/talk practice reminder time").formattedText.contains("Random alerts"))
        say(confirm(say("/talk set practice reminder time to 18:30")))
        assertEquals("06:30 PM", AssistantPreferences.read(context, spec))
        say(confirm(say("/talk set practice reminder time to random")))
        assertEquals("", AssistantPreferences.read(context, spec))
    }
    @Test fun nativeChoicesAndCustomValuesGeneratePreviewsWithoutWriting() = runBlocking {
        val style = AssistantPreferences.find("notification style")!!
        assertEquals(listOf("Notification Only", "Floating Bar", "Both"), style.values)
        assertEquals(listOf("dashboard", "todos", "time", "account"), AssistantPreferences.find("launch tab")!!.values)
        for (key in listOf("soft_lock_duration", "soft_unlock_duration", "auto_hide_duration_sec", "bubble_hidden_opacity", "app_theme_mode")) {
            val spec = AssistantPreferences.specs.first { it.key == key }
            val before = AssistantPreferences.read(context, spec)
            val value = AssistantPreferences.presets(spec).first { it != before }
            val preview = say(AssistantPreferences.previewQuery(spec, value))
            assertEquals(before, AssistantPreferences.read(context, spec))
            assertTrue(preview.formattedText.contains("Proposed"))
            assertEquals(value, (state().pending.single() as AssistantAction.SetPreference).value)
            say("cancel")
        }
    }
    @Test fun featureHelpIsAccurateAndDoesNotExposePrivateNotesOrClaimDeviceState() = runBlocking {
        val vault = say("/talk how to set pin lock").formattedText
        assertTrue(vault.contains("6-digit") && vault.contains("12-word"))
        assertFalse(vault.contains("does not have or use a PIN"))
        assertTrue(say("/talk task notification style").formattedText.contains("Notification Only"))
        val uninstall = say("/talk why can't I uninstall").formattedText
        assertTrue(uninstall, uninstall.contains("can prevent"))
        assertTrue(say("/talk read my notes").formattedText.contains("privacy", ignoreCase = true))
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }
    @Test fun readOnlyTypoHelpAndTopicFollowUpsNeverInventMutations() = runBlocking {
        val help = say("/talk persistant remidners")
        assertTrue(help.formattedText.contains("Persistent Task Reminders"))
        assertEquals("setting:persistent_reminder_interval", state().topic)
        assertTrue(say("where is it?").formattedText.contains("App Settings"))
        assertTrue(say("what is the range?").formattedText.contains("1440"))
        assertNull(state().expected)
        val preview = say("change it")
        assertEquals("setting_value", state().expected)
        assertTrue(preview.formattedText.contains("Reminder interval"))
    }
    @Test fun taskListCarriesStableIdsAndSelectingDoesNotMutateThem() = runBlocking {
        val later = db.taskDao().insertTask(Task(title = "Later", dueDate = System.currentTimeMillis() + 100_000))
        val first = db.taskDao().insertTask(Task(title = "Priority", isPriority = true))
        val reply = say("show me all tasks")
        val list = AssistantResponseContent.tasks(reply.jsonPayload)!!
        assertEquals(listOf(first, later), list.rows.map { it.id })
        assertTrue(reply.actions.isEmpty())
        assertTrue(OfflineAssistant.selectReference(context, AssistantItemReference("task", later, "Later")))
        assertTrue(db.taskDao().getAllActiveTasksList().none { it.isCompleted })
        say("complete id:$later")
        assertTrue(db.taskDao().getTaskById(later)!!.isCompleted)
        assertFalse(db.taskDao().getTaskById(first)!!.isCompleted)
    }
    @Test fun talkDoesNotConfirmOldTaskPreviewAndSettingPreviewDetectsExternalChanges() = runBlocking {
        db.taskDao().insertTask(Task(title = "One")); db.taskDao().insertTask(Task(title = "Two"))
        say("delete all tasks"); say("/talk yes")
        assertEquals(2, db.taskDao().getAllActiveTasksList().size)
        val preview = say("/talk set reminder interval to 30")
        context.getSharedPreferences("focus_prefs", 0).edit().putInt("persistent_reminder_interval", 45).commit()
        assertTrue(say(confirm(preview)).formattedText.contains("changed"))
        assertEquals(45, context.getSharedPreferences("focus_prefs", 0).getInt("persistent_reminder_interval", 0))
    }
    @Test fun eachEditableSettingHasSpecificHelpAndDoesNotReadAsAnUnrelatedFeature() = runBlocking {
        for (spec in AssistantPreferences.specs) {
            val response = say("/talk explain ${spec.aliases.first()}")
            assertEquals(response.formattedText, spec.key, AssistantResponseContent.settingSpec(response.jsonPayload)?.key)
            assertTrue(response.formattedText, response.formattedText.contains("Currently"))
            assertNull(state().expected)
        }
        say("/talk compare soft mode wait and soft mode relief")
        assertNull(state().expected)
        val missing = say("/talk why did the bubble disappear?").formattedText
        assertTrue(missing, missing.contains("permission", true) || missing.contains("landscape", true))
    }
    @Test fun featureQuestionsDoNotStealDirectPracticeAndBreathingCommands() = runBlocking {
        assertEquals("/drill easy 10", say("/talk drill").cliCommandRedirect)
        assertEquals("/vocab", say("/talk vocabulary").cliCommandRedirect)
        assertNull(OfflineAssistant.respond("breathe", context))
        assertNull(OfflineAssistant.respond("/talk status", context))
        assertTrue(say("/talk how does a drill work").formattedText.contains("Arithmetic"))
        assertTrue(say("/talk how do routines work").formattedText.contains("saved schedule"))
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }
    @Test fun androidNotificationLinkTargetsThisPackageWithTheCorrectExtra() {
        assertTrue(TalkAction.OpenSystemSetting(Settings.ACTION_APP_NOTIFICATION_SETTINGS, "Notifications", packageUri = true).execute(context))
        val intent = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertNull(intent.data)
    }
}
