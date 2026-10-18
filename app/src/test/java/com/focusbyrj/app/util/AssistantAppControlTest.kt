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
class AssistantAppControlTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    private val launchers = mutableListOf<ResolveInfo>()
    private val instagram = "com.instagram.android"
    private val whatsapp = "com.whatsapp"
    private val reader = "com.example.reader"
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        for (name in listOf("focus_prefs", "bubble_prefs", "ayva_action_receipts", "ayva_personalization", "custom_categories_prefs")) context.getSharedPreferences(name, 0).edit().clear().commit()
        AssistantSessionStore(context).clear(); CustomCategoryManager.init(context); FocusSessionManager.stop(context)
        install(instagram, "Instagram"); install(whatsapp, "WhatsApp"); install(reader, "Reader")
        install("com.android.settings", "Settings"); install("com.android.dialer", "Phone"); install(context.packageName, "Ayva")
        register()
    }
    private fun install(pkg: String, label: String) {
        val info = ApplicationInfo().apply { packageName = pkg; enabled = true; nonLocalizedLabel = label }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = info })
        launchers += ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Main"; enabled = true; applicationInfo = info } }
    }
    @Suppress("DEPRECATION") private fun register() {
        shadowOf(context.packageManager).setResolveInfosForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), launchers)
    }
    @After fun cleanup() { AssistantSessionStore(context).clear(); FocusSessionManager.stop(context); db.close() }
    private suspend fun say(text: String) = OfflineAssistant.respond(text, context) ?: error("Not handled: $text")
    private fun state() = AssistantSessionStore(context).load()
    private fun confirm(response: AyvaTalkEngine.TalkResponse) = response.actions.filterIsInstance<TalkAction.AskQuery>().firstOrNull { it.query.startsWith("/ayva confirm ") }?.query ?: error("Expected confirmation: ${response.formattedText}")
    private fun undo(response: AyvaTalkEngine.TalkResponse) = response.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva undo ") }.query
    private suspend fun rules() = db.appRestrictionDao().getAllRestrictionsSync()

    @Test fun lockAllIncludesAppsWithoutAnySavedRuleAndExcludesSystemControls() = runBlocking {
        assertEquals(setOf(instagram, whatsapp, reader), AppCatalog.load(context).map { it.packageName }.toSet())
        db.appRestrictionDao().insertRestriction(AppRestriction(reader, "Reader", isRestricted = false))
        val preview = say("lock all except Phone")
        assertEquals(3, (state().pending.single() as AssistantAction.SetAppRules).rules.size)
        assertEquals(1, rules().size); assertFalse(rules().single().isRestricted)
        val saved = say(confirm(preview))
        assertEquals(setOf(instagram, whatsapp, reader), rules().filter { it.isRestricted }.map { it.packageName }.toSet())
        assertTrue(rules().all { it.restrictionMode == "SIMPLE" })
        say(undo(saved))
        assertEquals(1, rules().size); assertFalse(rules().single().isRestricted)
        assertFalse(state().featureState?.has("appSelection") == true)
        db.appRestrictionDao().insertRestriction(AppRestriction(reader, "Reader", isRestricted = true))
        say("show locked apps")
        say(confirm(say("unlock them")))
        assertTrue(rules().none { it.isRestricted })
    }
    @Test fun exceptionsAndPendingFollowUpsReviseThePreviewWithoutSaving() = runBlocking {
        say("lock all except WhatsApp")
        assertEquals(setOf(instagram, reader), (state().pending.single() as AssistantAction.SetAppRules).rules.map { it.draft.packageName }.toSet())
        say("include WhatsApp"); say("except Instagram"); say("also lock Reader")
        assertTrue(say("which ones").formattedText.contains("still need confirmation"))
        assertEquals("confirm", state().expected)
        val preview = say("make them soft")
        assertTrue(rules().isEmpty())
        say(confirm(preview))
        assertEquals(setOf(whatsapp, reader), rules().map { it.packageName }.toSet())
        assertTrue(rules().all { it.mode == "SOFT" && it.isRestricted })
        say("add habit Reading")
        assertTrue(state().pending.single() is AssistantAction.CreateHabit)
        say("cancel"); say("lock all")
        say("add buy milk")
        assertEquals("Buy milk", db.taskDao().getAllActiveTasksList().single().title)
    }
    @Test fun committedSelectionCanExcludeAndReincludeAnApp() = runBlocking {
        say(confirm(say("lock all")))
        val exception = say("except WhatsApp")
        assertTrue(db.appRestrictionDao().getRestriction(whatsapp)!!.isRestricted)
        say(confirm(exception))
        assertFalse(db.appRestrictionDao().getRestriction(whatsapp)!!.isRestricted)
        assertTrue(db.appRestrictionDao().getRestriction(instagram)!!.isRestricted)
        say(confirm(say("include WhatsApp")))
        assertTrue(rules().all { it.isRestricted })
    }
    @Test fun unlockAllExceptAndKeepLockedFollowUpsUseCurrentManualRules() = runBlocking {
        say(confirm(say("lock all")))
        say(confirm(say("unlock all except Instagram")))
        assertTrue(db.appRestrictionDao().getRestriction(instagram)!!.isRestricted)
        assertFalse(db.appRestrictionDao().getRestriction(reader)!!.isRestricted)
        val preview = say("keep WhatsApp locked")
        say(confirm(preview))
        assertTrue(db.appRestrictionDao().getRestriction(whatsapp)!!.isRestricted)
        assertFalse(db.appRestrictionDao().getRestriction(reader)!!.isRestricted)
        say(confirm(say("unlock Instagram")))
        assertFalse(db.appRestrictionDao().getRestriction(instagram)!!.isRestricted)
        install("com.example.background", "Background app"); launchers.removeAt(launchers.lastIndex); register()
        db.appRestrictionDao().insertRestriction(AppRestriction("com.example.background", "Background app", isRestricted = true))
        db.appRestrictionDao().insertRestriction(AppRestriction("com.android.dialer", "Phone", isRestricted = true))
        say(confirm(say("unlock all")))
        assertTrue(rules().none { it.isRestricted })
    }
    @Test fun unknownExceptionInvalidatesOldPreviewAndCannotAccidentallyLockEverything() = runBlocking {
        val old = say("lock all")
        val error = say("lock all except Imaginary App")
        assertTrue(error.formattedText.contains("couldn't uniquely"))
        assertNull(state().expected)
        say(confirm(old)); say("yes")
        assertTrue(rules().isEmpty())
        install("com.example.clone", "WhatsApp"); register()
        assertTrue(say("lock all except WhatsApp").formattedText.contains("multiple apps"))
        assertNull(state().expected); assertTrue(rules().isEmpty())
    }
    @Test fun changedRuleRejectsTheWholeBatchAndUndoAlsoChecksEveryTarget() = runBlocking {
        val preview = say("lock all")
        db.appRestrictionDao().insertRestriction(AppRestriction(reader, "Reader", isRestricted = false, mode = "SOFT"))
        assertTrue(say(confirm(preview)).formattedText.contains("changed"))
        assertEquals(1, rules().size)
        val saved = say(confirm(say("lock all")))
        val altered = db.appRestrictionDao().getRestriction(reader)!!.copy(mode = "SOFT")
        db.appRestrictionDao().insertRestriction(altered)
        val response = say(undo(saved))
        assertTrue(response.formattedText.contains("changed"))
        assertEquals(altered, db.appRestrictionDao().getRestriction(reader))
        assertTrue(db.appRestrictionDao().getRestriction(instagram)!!.isRestricted)
    }
    @Test fun strictRoutinePreventsBulkUnlockWithoutPartiallyChangingOtherApps() = runBlocking {
        say(confirm(say("lock all")))
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        db.scheduleDao().insertSchedule(FocusSchedule(name = "Protected", startHour = (hour + 23) % 24, startMinute = 0, endHour = (hour + 1) % 24, endMinute = 0, daysOfWeek = "1,2,3,4,5,6,7", mode = "HARD", appsToBlock = instagram))
        val response = say(confirm(say("unlock all")))
        assertTrue(response.formattedText.contains("protect"))
        assertTrue(rules().all { it.isRestricted })
    }
    @Test fun categorySelectionsSharePickerTaxonomyAndBulkDoesNotTruncateAtFifty() = runBlocking {
        val preview = say("block social apps except WhatsApp")
        assertEquals(setOf(instagram), (state().pending.single() as AssistantAction.SetAppRules).rules.map { it.draft.packageName }.toSet())
        say("cancel")
        for (i in 1..60) install("com.example.app$i", "App $i")
        register()
        val all = say("lock all in the app")
        assertEquals(63, (state().pending.single() as AssistantAction.SetAppRules).rules.size)
        say(confirm(all))
        assertEquals(63, rules().count { it.isRestricted })
    }
}
