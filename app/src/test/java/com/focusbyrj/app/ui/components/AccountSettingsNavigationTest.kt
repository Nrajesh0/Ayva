package com.focusbyrj.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.ui.navigation.settingsDestinations
import com.focusbyrj.app.ui.screens.AccountScreen
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import com.focusbyrj.app.util.AppThemeManager
import com.focusbyrj.app.util.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=FocusApplication::class, qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountSettingsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun profileShowsTheActualThemeAndUsesCanonicalSettingsDestinations() {
        val app = ApplicationProvider.getApplicationContext<FocusApplication>()
        AppThemeManager.setThemeMode(app, ThemeMode.WARM)
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        lateinit var nav: NavHostController
        lateinit var view: android.view.View
        compose.setContent { FocusByRjTheme {
            view = LocalView.current
            nav = rememberNavController()
            NavHost(nav, startDestination="account") {
                composable("account") { AccountScreen(nav) }
                settingsDestinations(nav, vaultContent={})
            }
        } }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Overview").assertExists()
        compose.onNodeWithText("Achievements").assertExists()
        compose.onNodeWithText("Logs").assertDoesNotExist()
        compose.onNodeWithText("Biometric login & vaults").assertDoesNotExist()
        compose.onNodeWithText("Warm Sepia").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            val output = File("build/reports/settings-menu/profile-shortcuts.png").apply { parentFile!!.mkdirs() }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap)); output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        compose.onNodeWithText("Appearance", useUnmergedTree=true).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals("settings_appearance", nav.currentDestination?.route) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Manual Export (Encrypted Backup)").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals("settings_data", nav.currentDestination?.route) }
        compose.onNodeWithText("Create encrypted backup").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Restore from Encrypted Backup").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals("settings_data", nav.currentDestination?.route) }
    }
}
