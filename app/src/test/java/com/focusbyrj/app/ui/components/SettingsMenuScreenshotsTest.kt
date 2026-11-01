package com.focusbyrj.app.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.ui.screens.PreferencesHubScreen
import com.focusbyrj.app.ui.screens.SecurityScreen
import com.focusbyrj.app.ui.screens.*
import com.focusbyrj.app.ui.screens.notes.NotesViewModel
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import com.focusbyrj.app.util.AppThemeColor
import com.focusbyrj.app.util.AppThemeManager
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.ThemeMode
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Captures the production composables for visual review, rather than drawn mockups. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsMenuScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var view: View

    @Before fun prepare() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("focus_prefs", "bubble_prefs", "focus_app_prefs").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        AppThemeManager.init(app)
        AppThemeManager.setThemeMode(app, ThemeMode.OLED)
        AppThemeManager.setTheme(app, AppThemeColor.PLATINUM)
        FocusEconomyManager.init(app)
        FocusEconomyManager.updateName("Rj")
        app.getSharedPreferences("vizag_footer", 0).edit().clear().commit()
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun render(content: @Composable () -> Unit) {
        compose.setContent {
            view = LocalView.current
            FocusByRjTheme { content() }
        }
        compose.waitForIdle()
    }

    @Test fun mainSettings() {
        render { PreferencesHubScreen(rememberNavController()) }
        compose.onNodeWithContentDescription("Search settings").assertIsDisplayed()
        capture("main-settings")
        compose.onNodeWithText("By Rj").performScrollTo().assertIsDisplayed()
        capture("main-settings-footer")
    }

    @Test fun security() {
        render { SecurityScreen(rememberNavController()) }
        compose.onNodeWithText("Usage access").assertIsDisplayed()
        capture("security-top")
        compose.onNodeWithText("Private notes vault").performScrollTo().assertIsDisplayed()
        capture("security-bottom")
    }

    @Test fun appSettingsReference() {
        render { SettingsScreen(rememberNavController()) }
        compose.onNodeWithText("App theme").assertIsDisplayed()
        capture("app-settings")
    }

    @Test fun focus() {
        render { SettingsScreen(rememberNavController(), SettingsCategory.FOCUS) }
        compose.onNodeWithText("Soft-block delay").assertIsDisplayed()
        capture("focus")
    }

    @Test fun notifications() {
        render { SettingsScreen(rememberNavController(), SettingsCategory.NOTIFICATIONS) }
        compose.onNodeWithText("Task reminder delivery").assertIsDisplayed()
        capture("notifications")
    }

    @Test fun dataRecovery() {
        render { DataRecoveryScreen(rememberNavController()) }
        compose.onNodeWithText("Restore encrypted backup").assertIsDisplayed()
        capture("data-recovery")
    }

    @Test fun help() {
        render { HelpSettingsScreen(rememberNavController()) }
        capture("help")
    }

    @Test fun widgets() {
        render { WidgetsSettingsScreen(rememberNavController()) }
        capture("widgets")
    }

    @Test fun diagnostics() {
        render { DiagnosticsSettingsScreen(rememberNavController()) }
        capture("diagnostics")
    }

    @Test fun unconfiguredVaultMakesPrerequisitesVisibleAndKeepsActionsDisabled() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = NotesViewModel(app)
        render { PrivateVaultSettingsScreen(rememberNavController(), vm) }
        compose.onNodeWithText("Set up").assertIsEnabled()
        compose.onNodeWithText("Change").assertIsNotEnabled()
        compose.onNodeWithText("Disable").assertIsNotEnabled()
        compose.onNodeWithText("Recover").assertIsNotEnabled()
        capture("private-vault")
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle { view.rootView.invalidate() }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(34))
        compose.runOnIdle {
        val bitmap = Bitmap.createBitmap(view.rootView.width, view.rootView.height, Bitmap.Config.ARGB_8888)
        view.rootView.draw(Canvas(bitmap))
        val output = File("build/reports/settings-menu/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        }
    }
}
