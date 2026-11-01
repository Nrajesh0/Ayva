package com.focusbyrj.app.ui.components

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.ui.navigation.settingsDestinations
import com.focusbyrj.app.ui.screens.*
import com.focusbyrj.app.ui.screens.security.RestoreBackupPasswordDialog
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import com.focusbyrj.app.util.*
import com.focusbyrj.app.util.assistant.AssistantPreferences
import com.focusbyrj.app.widget.TodoWidgetProvider
import com.focusbyrj.app.widget.TodoWidgetConfigureActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsNavigationRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var nav: NavHostController

    @Before fun prepare() {
        listOf("focus_prefs", "bubble_prefs", "focus_app_prefs").forEach { app.getSharedPreferences(it,0).edit().clear().commit() }
        AppThemeManager.init(app); AppThemeManager.setThemeMode(app, ThemeMode.OLED)
        FocusEconomyManager.init(app)
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }
    private fun graph() {
        compose.setContent { FocusByRjTheme {
            nav = rememberNavController()
            NavHost(nav, startDestination = "preferences_hub") {
                settingsDestinations(nav, vaultContent = { Text("Private vault destination") })
                composable("account") { Text("Profile") }
                composable("dashboard") { Text("App boundaries") }
            }
        } }
    }
    private fun search(query: String, result: String) {
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.onNodeWithTag("settings_search_query").performTextInput(query)
        compose.onNodeWithTag("setting_result_$result").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
    }

    @Test fun allSevenRootCategoriesHaveWorkingDestinationsAndBackReturnsToSettings() {
        graph()
        val pages = listOf("Appearance" to "settings_appearance", "Focus & behavior" to "settings_focus",
            "Notifications & reminders" to "settings_notifications", "Security & permissions" to "security",
            "Data & recovery" to "settings_data", "Bubble Settings" to "bubble_settings", "Help & about" to "settings_help")
        pages.forEach { (title, route) ->
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.runOnIdle { assertEquals(route, nav.currentDestination?.route) }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.runOnIdle { assertEquals("preferences_hub", nav.currentDestination?.route) }
        }
        compose.onNodeWithText("Subscription").assertDoesNotExist()
        compose.onNodeWithText("Security Vault").assertDoesNotExist()
    }

    @Test fun oldSearchLabelOpensItsNewPageAndKeepsAnExistingLongWaitValue() {
        app.getSharedPreferences("focus_prefs", 0).edit().putInt("soft_lock_duration", 120).commit()
        graph()
        search("Soft Mode Wait Timer", "Soft-block delay")
        compose.onNodeWithText("Soft-block delay").assertIsDisplayed()
        compose.onNodeWithText("120s").assertIsDisplayed()
        compose.runOnIdle { assertEquals("settings_focus", nav.currentDestination?.route) }
        compose.runOnIdle { AssistantPreferences.apply(app, "soft_lock_duration", "int", "80") }
        compose.onNodeWithText("80s").assertIsDisplayed()
    }

    @Test fun aPausedConditionalPracticeSettingIsReachableWithoutTurningAlertsOn() {
        app.getSharedPreferences("bubble_prefs", 0).edit().putBoolean("streak_notification_enabled", false).commit()
        graph()
        search("Practice Alert Time", "Practice Alert Time")
        compose.onNodeWithText("Practice Alert Time").assertIsDisplayed().performClick()
        compose.onNodeWithText("Streak Notification Timing").assertDoesNotExist()
        assertFalse(app.getSharedPreferences("bubble_prefs", 0).getBoolean("streak_notification_enabled", true))
    }

    @Test fun bubbleReminderControlsReflectChatEditsWithoutReopening() {
        app.getSharedPreferences("bubble_prefs", 0).edit().putBoolean("streak_notification_enabled", false).commit()
        compose.setContent { FocusByRjTheme { BubbleSettingsScreen(rememberNavController()) } }
        compose.onNodeWithText("Paused").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { AssistantPreferences.apply(app, "streak_notification_enabled", "boolean", "true") }
        compose.onNodeWithText("Random (9am-8pm)").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { AssistantPreferences.apply(app, "streak_notification_time", "string", "10:30 AM") }
        compose.onNodeWithText("10:30 AM").assertIsDisplayed()
    }

    @Test fun privateVaultShortcutGoesToTheManagerRatherThanGenericSecurity() {
        graph()
        search("Security Vault", "Private notes vault")
        compose.onNodeWithText("Private vault destination").assertIsDisplayed()
        compose.runOnIdle { assertEquals("settings_vault", nav.currentDestination?.route) }
    }

    @Test fun nestedBackupVerificationOpensTheReadOnlyPickerWithoutRestoringData() {
        app.getSharedPreferences("focus_prefs", 0).edit().putString("sentinel", "keep").commit()
        graph()
        search("verify backup", "Verify a backup")
        compose.onNodeWithText("Backup verification").assertIsDisplayed()
        val intent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals("keep", app.getSharedPreferences("focus_prefs", 0).getString("sentinel", null))
    }

    @Test fun restoredDayLongRepeatIntervalIsShownWithoutTruncation() {
        app.getSharedPreferences("focus_prefs", 0).edit().putInt("persistent_reminder_interval",1440).putString("task_notification_style","Floating Bar").commit()
        compose.setContent { MaterialTheme { SettingsScreen(rememberNavController(), SettingsCategory.NOTIFICATIONS) } }
        compose.onNodeWithText("24h").assertIsDisplayed()
        compose.onNodeWithText("Notification + popup").assertIsDisplayed()
        assertEquals("Floating Bar", app.getSharedPreferences("focus_prefs",0).getString("task_notification_style",null))
    }

    @Test fun widgetChooserEditsTheSelectedInstanceRatherThanTheFirstWidget() {
        val manager = AppWidgetManager.getInstance(app)
        val provider = ComponentName(app, TodoWidgetProvider::class.java)
        shadowOf(manager).addInstalledProvider(AppWidgetProviderInfo().apply { this.provider = provider })
        shadowOf(manager).bindAppWidgetId(101, provider)
        shadowOf(manager).bindAppWidgetId(202, provider)
        compose.setContent { MaterialTheme { WidgetsSettingsScreen(rememberNavController()) } }
        compose.onNodeWithText("Tasks widget").performClick()
        compose.onNodeWithText("Edit widget 2").performClick()
        val intent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(TodoWidgetConfigureActivity::class.java.name, intent.component?.className)
        assertEquals(202, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }

    @Test fun defaultWidgetStyleDoesNotSelectAnInstalledInstance() {
        val manager = AppWidgetManager.getInstance(app)
        val provider = ComponentName(app, TodoWidgetProvider::class.java)
        shadowOf(manager).addInstalledProvider(AppWidgetProviderInfo().apply { this.provider = provider })
        shadowOf(manager).bindAppWidgetId(101, provider)
        compose.setContent { MaterialTheme { WidgetsSettingsScreen(rememberNavController()) } }
        compose.onNodeWithText("Tasks widget").performClick()
        compose.onNodeWithText("Default style").performClick()
        assertEquals(0, shadowOf(compose.activity).nextStartedActivity.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }

    @Test @Config(sdk=[34]) fun replacementRestoreRequiresAnExplicitSecondConfirmation() {
        val calls = mutableListOf<Pair<String,Boolean>>()
        // Match the laid-out host on the production recovery page.
        compose.setContent { FocusByRjTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
            RestoreBackupPasswordDialog(false, {}, { p, replace -> calls += p to replace })
        } } }
        compose.onNodeWithText("Backup Password").performTextInput("password123")
        compose.onNodeWithTag("restore_replace_mode").performClick()
        compose.onNodeWithText("Decrypt & Restore").performClick()
        compose.runOnIdle { assertTrue(calls.isEmpty()) }
        compose.onNodeWithText("Replace current app data?").assertIsDisplayed()
        compose.onNodeWithText("Go back").performClick()
        compose.runOnIdle { assertTrue(calls.isEmpty()) }
        compose.onNodeWithText("Decrypt & Restore").performClick()
        compose.onNodeWithText("Replace data & restore").performClick()
        compose.runOnIdle { assertEquals(listOf("password123" to true), calls) }
    }

    @Test @Config(sdk=[34]) fun defaultRestoreModeStaysNonReplacementAndMissingPasswordCannotProceed() {
        val calls = mutableListOf<Pair<String,Boolean>>()
        // Match the laid-out host on the production recovery page.
        compose.setContent { FocusByRjTheme { Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
            RestoreBackupPasswordDialog(false, {}, { p, replace -> calls += p to replace })
        } } }
        compose.onNodeWithText("Decrypt & Restore").performClick()
        compose.runOnIdle { assertTrue(calls.isEmpty()) }
        compose.onNodeWithText("Backup Password").performTextInput("password123")
        compose.onNodeWithText("Decrypt & Restore").performClick()
        compose.runOnIdle { assertEquals(listOf("password123" to false), calls) }
        compose.onNodeWithText("Replace current app data?").assertDoesNotExist()
    }

    @Test fun aNonAlignedTemporaryUnlockValueStopsAtTheMaximum() {
        app.getSharedPreferences("focus_prefs", 0).edit().putInt("soft_unlock_duration", 59).commit()
        compose.setContent { FocusByRjTheme { SettingsScreen(rememberNavController(), SettingsCategory.FOCUS) } }
        compose.onNodeWithText("59m").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Increase")[1].performClick()
        compose.onNodeWithText("60m").assertIsDisplayed()
        assertEquals(60, app.getSharedPreferences("focus_prefs", 0).getInt("soft_unlock_duration", -1))
    }

    @Test fun screenshotToggleCannotClearSensitiveScreenProtection() {
        app.getSharedPreferences("focus_prefs", 0).edit().putBoolean("secure_recents", true).commit()
        compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        compose.setContent { FocusByRjTheme { SecurityScreen(rememberNavController()) } }
        compose.onNode(isToggleable()).performClick()
        compose.runOnIdle {
            assertFalse(app.getSharedPreferences("focus_prefs", 0).getBoolean("secure_recents", true))
            assertTrue(compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }

    @Test fun notesWidgetHasAnIndependentConfigurationDestination() {
        compose.setContent { FocusByRjTheme { WidgetsSettingsScreen(rememberNavController()) } }
        compose.onNodeWithText("Notes widget").performClick()
        compose.onNodeWithText("Default style").performClick()
        val intent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(com.focusbyrj.app.widget.NoteWidgetConfigureActivity::class.java.name, intent.component?.className)
        assertEquals(0, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }

    @Test
    @Config(sdk=[34])
    fun securityExposesNotificationsAndExactAlarmAccess() {
        compose.setContent { MaterialTheme { SecurityScreen(rememberNavController()) } }
        compose.onNodeWithText("Notifications").assertIsDisplayed()
        compose.onNodeWithText("Alarms & reminders").assertIsDisplayed()
        compose.onNodeWithText("Notifications").performClick()
        assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, shadowOf(compose.activity).nextStartedActivity.action)
    }
}
