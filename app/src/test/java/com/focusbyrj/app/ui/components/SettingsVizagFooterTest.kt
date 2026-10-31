package com.focusbyrj.app.ui.components

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AnimatedImageDrawable
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.ui.screens.PreferencesHubScreen
import com.focusbyrj.app.ui.navigation.Screen
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsVizagFooterTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun prepare() {
        app.getSharedPreferences("vizag_footer", Context.MODE_PRIVATE).edit().clear().commit()
        // Static screenshot mode also exercises the app's reduced-motion handling.
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }
    @After fun cleanUp() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test fun successiveVisitsCycleAllNineWithoutAnImmediateRepeatAndPersistProgress() {
        val first = (0 until 9).map { VizagSceneRotation.claimNext(app) }
        assertEquals((0 until 9).toList(), first)
        val second = (0 until 9).map { VizagSceneRotation.claimNext(app.applicationContext) }
        assertEquals(first, second)
        assertTrue((first + second).zipWithNext().all { (a, b) -> a != b })
        assertEquals(0, app.getSharedPreferences("vizag_footer", 0).getInt("next_scene", -1))
    }

    @Test fun sceneSurvivesThemeChangesAndSavedStateRestorationButNewVisitAdvances() {
        val state = StateRestorationTester(compose)
        var show by mutableStateOf(true)
        var light by mutableStateOf(false)
        state.setContent {
            MaterialTheme(colorScheme = if (light) lightColorScheme() else darkColorScheme()) {
                if (show) SettingsVizagFooter(rememberVizagScene())
            }
        }
        compose.onNodeWithText("RK Beach promenade").assertIsDisplayed()
        compose.runOnIdle { light = true }
        state.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("RK Beach promenade").assertIsDisplayed()
        assertEquals(1, app.getSharedPreferences("vizag_footer", 0).getInt("next_scene", -1))
        compose.runOnIdle { show = false }
        compose.runOnIdle { show = true }
        compose.onNodeWithText("Kailasagiri").assertIsDisplayed()
    }

    @Test fun allBundledAnimationsDecodeNativelyWithTransparentArtwork() {
        for (scene in VizagScene.entries) {
            val drawable = decodeVizagArtwork(app, scene).drawable
            assertTrue("${scene.placeName} decodes as an animation", drawable is AnimatedImageDrawable)
            drawable as AnimatedImageDrawable
            assertTrue(drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0)
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("${scene.placeName} paints artwork", pixels.count { it ushr 24 > 0 } > 100)
            assertTrue("${scene.placeName} has a transparent background", pixels.any { it ushr 24 == 0 })
            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            drawable.start()
            assertTrue(drawable.isRunning)
            drawable.stop()
            assertFalse(drawable.isRunning)
            bitmap.recycle()
        }
    }

    @Test fun nativePlaybackStopsWhenHiddenReducedMotionIsEnabledOrViewIsReleased() {
        val artwork = decodeVizagArtwork(app, VizagScene.FISHING_HARBOUR)
        val drawable = artwork.drawable as AnimatedImageDrawable
        var show by mutableStateOf(true)
        lateinit var animationView: VizagAnimationView
        compose.setContent {
            if (show) AndroidView(
                factory = { VizagAnimationView(it).also { view -> animationView = view; view.setArtwork(artwork) } },
                onRelease = { it.release() }
            )
        }
        compose.runOnIdle {
            Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            animationView.setPlaybackEnabled(true)
            assertTrue(drawable.isRunning)
            animationView.setPlaybackEnabled(false)
            assertFalse(drawable.isRunning)
            animationView.setPlaybackEnabled(true)
            assertTrue(drawable.isRunning)
            Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            animationView.setPlaybackEnabled(true)
            assertFalse(drawable.isRunning)
            Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            animationView.setPlaybackEnabled(true)
            show = false
        }
        compose.runOnIdle { assertFalse(drawable.isRunning) }
    }

    @Test fun returningFromAChildSettingsPagePlaysTheNextScene() {
        compose.setContent {
            MaterialTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = Screen.PreferencesHub.route) {
                    composable(Screen.PreferencesHub.route) { PreferencesHubScreen(nav) }
                    composable(Screen.Settings.route) {
                        Button(onClick = { nav.popBackStack() }) { Text("Return to Settings") }
                    }
                }
            }
        }
        compose.onNodeWithText("RK Beach promenade").assertExists()
        compose.onNodeWithText("App Settings").performScrollTo().performClick()
        compose.onNodeWithText("Return to Settings").performClick()
        compose.onNodeWithText("Kailasagiri").assertExists()
    }

    @Test fun footerHierarchyLinkAndLargeTextWorkAcrossLightDarkOledAndAccentThemes() {
        var scheme by mutableStateOf(darkColorScheme(background = Color.Black))
        var fontScale by mutableFloatStateOf(1f)
        var scene by mutableStateOf(VizagScene.RK_BEACH)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme(colorScheme = scheme) {
                    Box(Modifier.fillMaxWidth().background(scheme.background)) {
                        SettingsVizagFooter(scene)
                    }
                }
            }
        }
        val modes = listOf(
            "oled" to darkColorScheme(background = Color.Black, primary = Color(0xFF9ABFAE)),
            "dark" to darkColorScheme(background = Color(0xFF171B20), primary = Color(0xFFB8A2DB)),
            "light" to lightColorScheme(background = Color(0xFFFAF8F1), primary = Color(0xFF426B59))
        )
        for ((name, colors) in modes) {
            compose.runOnIdle { scheme = colors }
            compose.onNodeWithText("Made with ❤️ in Vizag").assertIsDisplayed()
            compose.onNodeWithText("By Rj").assertIsDisplayed().assertHasClickAction()
            val place = compose.onNodeWithTag("settings_vizag_place").fetchSemanticsNode().boundsInRoot
            val made = compose.onNodeWithText("Made with ❤️ in Vizag").fetchSemanticsNode().boundsInRoot
            val signature = compose.onNodeWithTag("settings_vizag_signature").fetchSemanticsNode().boundsInRoot
            assertTrue(place.bottom <= made.top)
            assertTrue(made.bottom <= signature.top)
            capture(view, "footer-$name")
        }
        compose.onNodeWithText("By Rj").performClick()
        val intent = Shadows.shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(VIZAG_GITHUB_URL, intent.dataString)
        compose.runOnIdle { fontScale = 1.6f; scene = VizagScene.DOLPHINS_NOSE }
        compose.onNodeWithText("Dolphin’s Nose Lighthouse").assertIsDisplayed()
        compose.onNodeWithText("Made with ❤️ in Vizag").assertIsDisplayed()
        compose.onNodeWithText("By Rj").assertIsDisplayed()
        capture(view, "footer-large-text")
    }

    @Test fun settingsHubShowsFooterAfterSetupGuideAndCanScrollToSignature() {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black)) {
                PreferencesHubScreen(rememberNavController())
            }
        }
        compose.onNodeWithText("Setup Guide").assertExists()
        compose.onNodeWithText("By Rj").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Made with ❤️ in Vizag").assertIsDisplayed()
        compose.onNodeWithText("RuN", substring = true).assertDoesNotExist()
        capture(view, "settings-footer-oled")
    }

    private fun capture(view: View, name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File("build/reports/vizag-footer/$name.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
