package com.focusbyrj.app.ui.tours

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.*
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.ui.navigation.settingsDestinations
import com.focusbyrj.app.ui.screens.AddTaskDialog
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: TourPreferences

    @Before
    fun prepare() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("app_tours", 0).edit().clear().commit()
        com.focusbyrj.app.ui.components.EditorDraftStore(app, "task:0:0:TASK").discard()
        store = TourPreferences(app)
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
    }

    @Test
    fun helpOpensTheReplayPickerAndNavigatesToTheChosenFeature() {
        compose.setContent {
            MaterialTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "settings_help") {
                    settingsDestinations(nav, vaultContent = {})
                    composable("dashboard") { Text("Focus destination") }
                }
            }
        }
        compose.onNodeWithText("App tours").performClick()
        compose.onNodeWithText("Getting around").assertIsDisplayed().performClick()
        compose.onNodeWithText("Focus destination").assertIsDisplayed()
        assertEquals(AppTours.navigation.id, store.replayId)
        assertFalse(store.automatic)
    }

    @Test
    fun theDateTourNeverFillsOrSavesATaskAndNormalSavingStillWorks() {
        store.setAutomatic(true)
        var savedTitle: String? = null
        compose.setContent {
            MaterialTheme {
                AddTaskDialog(
                    onDismiss = {},
                    onSave = { task, completed ->
                        savedTitle = task.title
                        completed()
                    },
                )
            }
        }
        settle()
        compose.onNodeWithText("Dates, in your own words").assertIsDisplayed()
        assertNull(savedTitle)
        compose.onNodeWithTag("spotlight_skip").performClick()
        compose.onNodeWithText("Title").performTextInput("Buy milk tomorrow at 6 pm")
        compose.onNodeWithText("Setting due:", substring = true).assertIsDisplayed()
        assertNull(savedTitle)
        compose.onNodeWithText("Save", substring = false).performClick()
        assertEquals("Buy milk", savedTitle)
    }

    @Test
    fun anExistingDraftIsNeverInterruptedByTheDateTour() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        com.focusbyrj.app.ui.components
            .EditorDraftStore(app, "task:0:0:TASK")
            .save(org.json.JSONObject().put("title", "Keep my work"))
        store.setAutomatic(true)
        compose.setContent { MaterialTheme { AddTaskDialog(onDismiss = {}, onSave = { _, _ -> }) } }
        settle()
        compose.onNodeWithText("Keep my work").assertIsDisplayed()
        compose.onNodeWithTag("spotlight_overlay").assertDoesNotExist()
    }

    @Test
    fun theLabelledTipsRowIsAnAccessibleSwitchAndUpdatesTheSetting() {
        compose.setContent { MaterialTheme { AppToursScreen(rememberNavController()) } }
        val setting = compose.onNodeWithTag("tour_automatic")
        setting.assertIsOff().assert(hasText("Show first-use tips"))
        setting.performClick().assertIsOn()
        assertTrue(store.automatic)
        setting.performClick().assertIsOff()
        assertFalse(store.automatic)
    }
}
