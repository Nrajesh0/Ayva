package com.focusbyrj.app.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focusbyrj.app.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ProfileTabNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var nav: NavHostController

    private fun show(start: Screen) {
        compose.setContent {
            nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            Column {
                FocusBottomBar(listOf(Screen.Dashboard, Screen.Account), entry?.destination, nav)
                NavHost(nav, startDestination = start.route) {
                    composable(Screen.Dashboard.route) { Text("Focus page") }
                    composable(Screen.Account.route) { Text("Profile page") }
                    composable("preferences_hub") { Text("Settings page") }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun profileOpensWhenItIsTheLaunchTab() {
        show(Screen.Account)
        compose.onNodeWithTag("tab_dashboard").performClick()
        compose.onNodeWithTag("tab_account").performClick()
        compose.runOnIdle { assertEquals(Screen.Account.route, nav.currentDestination?.route) }
    }

    @Test fun profileOpensInsteadOfRestoringItsSettingsPage() {
        show(Screen.Dashboard)
        compose.onNodeWithTag("tab_account").performClick()
        compose.runOnIdle { nav.navigate("preferences_hub") }
        compose.onNodeWithTag("tab_dashboard").performClick()
        compose.onNodeWithTag("tab_account").performClick()
        compose.runOnIdle { assertEquals(Screen.Account.route, nav.currentDestination?.route) }
    }
}
