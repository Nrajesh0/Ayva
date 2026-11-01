package com.focusbyrj.app.ui.components

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.ui.navigation.SettingsRoutes
import com.focusbyrj.app.ui.screens.SettingsScreenHelper
import com.focusbyrj.app.util.SettingsPreferenceRules
import com.focusbyrj.app.util.ChatTaskDraft
import com.focusbyrj.app.util.assistant.AssistantPreferences
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SettingsOwnershipTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clear() {
        listOf("focus_prefs", "bubble_prefs", "focus_app_prefs").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
    }

    @Test fun everyOldIndexedSettingStillResolvesToOneStableDestination() {
        val old = JSONArray(javaClass.classLoader!!.getResourceAsStream("settings-legacy-titles.json")!!.bufferedReader().readText())
        assertEquals(48, old.length())
        for (i in 0 until old.length()) {
            val label = old.getString(i)
            val resolved = SettingsSearchCatalog.resolve(label)
            assertNotNull("Lost old setting: $label", resolved)
            assertTrue(resolved!!.route in SettingsRoutes.all + setOf("account", "dashboard"))
        }
        assertEquals(SettingsSearchCatalog.entries.size, SettingsSearchCatalog.entries.map { it.id }.distinct().size)
    }

    @Test fun chatPreferencesAndMenusAgreeOnTheirCanonicalHomes() {
        AssistantPreferences.specs.forEach { spec ->
            val id = SettingsPreferenceRules.settingIds[spec.key]
            assertNotNull("Unmapped chat preference ${spec.key}", id)
            assertEquals(spec.key, AssistantPreferences.route(spec), SettingsSearchCatalog.resolve(id)!!.route)
        }
        assertEquals("settings_widgets", SettingsSearchCatalog.resolve("appearance.widgets")!!.route)
        assertEquals("settings_vault", SettingsSearchCatalog.resolve("Security Vault")!!.route)
        assertEquals("settings_diagnostics", SettingsSearchCatalog.resolve("help.diagnostics")!!.route)
    }

    @Test fun oldHighValuesRemainSupportedAndUiUsesTheSameLimits() {
        assertEquals("120", AssistantPreferences.validate("soft_lock_duration", "int", "120"))
        assertEquals(120, SettingsScreenHelper.coerceSoftLockDuration(120))
        assertEquals("300", AssistantPreferences.validate("auto_hide_duration_sec", "int", "300"))
        assertEquals("1440", AssistantPreferences.validate("persistent_reminder_interval", "int", "1440"))
        assertEquals(1440, SettingsScreenHelper.getNextReminderInterval(1440, true))
        assertEquals(720, SettingsScreenHelper.getNextReminderInterval(1440, false))
        assertThrows(IllegalArgumentException::class.java) { AssistantPreferences.validate("soft_lock_duration", "int", "121") }
    }

    @Test fun legacyFloatingBarMapsToExistingPopupBehaviorAndLaunchTabWritesStayInSync() {
        assertEquals("Both", AssistantPreferences.validate("task_notification_style", "string", "Floating Bar"))
        assertEquals("Both", AssistantPreferences.validate("task_notification_style", "string", "Notification + popup"))
        assertEquals("Notification Only", AssistantPreferences.validate("task_notification_style", "string", "Notification bar"))
        AssistantPreferences.apply(app, "default_start_tab", "string", "todos")
        assertEquals("todos", app.getSharedPreferences("focus_prefs", 0).getString("default_start_tab", null))
        assertEquals("todos", app.getSharedPreferences("focus_app_prefs", 0).getString("default_start_tab", null))
    }

    @Test fun datePreviewSwitchHasAnEffectWithoutDisablingExplicitTaskParsing() {
        val text = "call Mum tomorrow at 6pm"
        assertNull(SettingsPreferenceRules.datePreview(text, false))
        assertNotNull(SettingsPreferenceRules.datePreview(text, true)?.timestamp)
        app.getSharedPreferences("bubble_prefs", 0).edit().putBoolean("smart_regex_enabled", false).commit()
        assertNotNull(ChatTaskDraft.parse(text)?.dueDate)
    }

    @Test fun aliasesFindNestedActionsWithoutMakingEverySecurityItemMatchPrivacy() {
        assertTrue(SettingsSearchCatalog.resolve("Secret Vault Passcode")!!.matches("vault pin"))
        assertTrue(SettingsSearchCatalog.resolve("privacy.screenshots")!!.matches("privacy screenshot"))
        assertTrue(SettingsSearchCatalog.resolve("data.verify")!!.matches("verify backup"))
        assertFalse(SettingsSearchCatalog.resolve("diagnostics.clear")!!.matches("privacy"))
    }
}
