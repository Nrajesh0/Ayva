package com.focusbyrj.app.ui.tours

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
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
class SpotlightTourUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: TourPreferences

    @Before
    fun prepare() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("app_tours", 0).edit().clear().commit()
        store = TourPreferences(app)
        store.replay(AppTours.tasks)
    }

    private fun show(
        enabled: State<Boolean> = mutableStateOf(true),
        target: State<Boolean> = mutableStateOf(true),
        onTap: () -> Unit = {},
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale)
            ) {
                MaterialTheme {
                    val state = rememberSpotlightTour(AppTours.tasks)
                    Box(Modifier.fillMaxSize()) {
                        if (target.value)
                            Text(
                                "Tabs",
                                Modifier.align(Alignment.TopCenter)
                                    .padding(top = 60.dp)
                                    .spotlightTarget(state, "task_tabs")
                                    .clickable(onClick = onTap),
                            )
                        Button(
                            onClick = onTap,
                            modifier =
                                Modifier.align(Alignment.BottomEnd)
                                    .padding(24.dp)
                                    .spotlightTarget(state, "task_add"),
                        ) {
                            Text("Create")
                        }
                        SpotlightTour(state, enabled = enabled.value)
                    }
                }
            }
        }
        settle()
    }

    private fun settle() {
        repeat(3) {
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofMillis(34))
        }
    }

    @Test
    fun nextAndFinishPersistAndNeverExecuteTheUnderlyingControl() {
        var taps = 0
        show(onTap = { taps++ })
        compose.onNodeWithText("Find the right tasks").assertIsDisplayed()
        val output =
            java.io.File("build/reports/app-tours/task-spotlight.png").apply { parentFile.mkdirs() }
        compose.runOnIdle {
            val decor = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
            val bitmap =
                android.graphics.Bitmap.createBitmap(
                    decor.width,
                    decor.height,
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
            decor.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        compose.onNodeWithTag("spotlight_next").performClick()
        compose.onNodeWithText("Create a task").assertIsDisplayed()
        compose.onNodeWithTag("spotlight_next").performClick()
        compose.onNodeWithTag("spotlight_overlay").assertDoesNotExist()
        assertTrue(store.done(AppTours.tasks.id))
        assertNull(store.replayId)
        assertEquals(0, taps)
    }

    @Test
    fun skipRemovesTheWholeTour() {
        show()
        compose.onNodeWithTag("spotlight_skip").performClick()
        settle()
        compose.onNodeWithTag("spotlight_overlay").assertDoesNotExist()
        assertTrue(store.done(AppTours.tasks.id))
    }

    @Test
    fun anUnavailableTargetDoesNotProduceAMisleadingOverlayOrMarkCompletion() {
        val target = mutableStateOf(false)
        show(target = target)
        compose.onNodeWithTag("spotlight_overlay").assertDoesNotExist()
        assertFalse(store.done(AppTours.tasks.id))
        compose.runOnIdle { target.value = true }
        settle()
        compose.onNodeWithText("Find the right tasks").assertIsDisplayed()
    }

    @Test
    fun aSensitiveOperationPausesAndResumesTheSameStep() {
        val enabled = mutableStateOf(true)
        show(enabled = enabled)
        compose.onNodeWithTag("spotlight_next").performClick()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("spotlight_overlay").assertDoesNotExist()
        assertFalse(store.done(AppTours.tasks.id))
        compose.runOnIdle { enabled.value = true }
        settle()
        compose.onNodeWithText("Create a task").assertIsDisplayed()
    }

    @Test
    fun dismissingWithBackSkipsTheTourRatherThanLeavingTheScreen() {
        show()
        compose.runOnIdle {
            (org.robolectric.shadows.ShadowDialog.getLatestDialog()
                    as androidx.activity.ComponentDialog)
                .onBackPressedDispatcher
                .onBackPressed()
        }
        settle()
        compose.onNodeWithTag("spotlight_overlay").assertDoesNotExist()
        assertTrue(store.done(AppTours.tasks.id))
    }

    @Test
    fun replayStartsAtTheFirstStepEvenAfterAnEarlierTourEnded() {
        show()
        compose.onNodeWithTag("spotlight_next").performClick()
        compose.onNodeWithTag("spotlight_skip").performClick()
        compose.runOnIdle { store.replay(AppTours.tasks) }
        settle()
        compose.onNodeWithText("Find the right tasks").assertIsDisplayed()
    }

    @Test
    fun removingTheTargetPausesWithoutMarkingTheTourFinished() {
        val target = mutableStateOf(true)
        show(target = target)
        compose.runOnIdle { target.value = false }
        compose.onNodeWithTag("spotlight_overlay").assertDoesNotExist()
        assertFalse(store.done(AppTours.tasks.id))
    }

    @Test
    fun secureScreensKeepTheirScreenshotProtectionInTheTourWindow() {
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        show()
        compose.runOnIdle {
            val window = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!
            assertTrue(
                window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0
            )
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun smallScreenAndLargeTextKeepTheActionsReachable() {
        show(fontScale = 2f)
        compose.onNodeWithText("Find the right tasks").assertExists()
        compose.onNodeWithTag("spotlight_next").assertIsDisplayed().performClick()
        compose.onNodeWithTag("spotlight_skip").assertIsDisplayed().performClick()
        compose.onNodeWithTag("spotlight_overlay").assertDoesNotExist()
    }

    @Test
    fun interactingBeforeATipAppearsPreventsALateInterruption() {
        val enabled = mutableStateOf(false)
        var taps = 0
        show(enabled = enabled, onTap = { taps++ })
        compose.onNodeWithText("Tabs").performTouchInput { click() }
        assertEquals(1, taps)
        compose.runOnIdle { enabled.value = true }
        settle()
        compose.onNodeWithTag("spotlight_overlay").assertDoesNotExist()
        assertFalse(store.done(AppTours.tasks.id))
    }

    @Test
    fun tappingTheHighlightCannotActivateTheControlBehindIt() {
        var taps = 0
        show(onTap = { taps++ })
        val target = compose.onNodeWithText("Tabs").fetchSemanticsNode().boundsInWindow.center
        compose.onNodeWithTag("spotlight_overlay").performTouchInput { click(target) }
        assertEquals(0, taps)
        compose.onNodeWithText("Find the right tasks").assertIsDisplayed()
    }

    @Test
    fun anOversizedTargetCannotTrapTheUserBehindAnUnusableCard() {
        compose.setContent {
            MaterialTheme {
                val state = rememberSpotlightTour(AppTours.tasks)
                Box(Modifier.fillMaxSize().spotlightTarget(state, "task_tabs"))
                SpotlightTour(state)
            }
        }
        settle()
        compose.onNodeWithTag("spotlight_overlay").assertDoesNotExist()
        assertFalse(store.done(AppTours.tasks.id))
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun eachStepStartsAtItsHeadingAfterScrollingALongExplanation() {
        show(fontScale = 3f)
        compose.onNodeWithText(AppTours.tasks.steps.first().explanation).performScrollTo()
        compose.onNodeWithTag("spotlight_next").assertIsDisplayed().performClick()
        compose.onNodeWithText("Create a task").assertIsDisplayed()
        compose.onNodeWithTag("spotlight_skip").assertIsDisplayed()
    }
}
