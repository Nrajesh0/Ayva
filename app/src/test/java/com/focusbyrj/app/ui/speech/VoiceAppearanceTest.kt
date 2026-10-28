package com.focusbyrj.app.ui.speech

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VoiceAppearanceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private var previousScale = 1f
    @Before fun setup() {
        previousScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        context.getSharedPreferences("focus_prefs", 0).edit().remove(VoiceStyle.PREFERENCE).commit()
    }
    @After fun cleanup() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousScale)
        context.getSharedPreferences("focus_prefs", 0).edit().remove(VoiceStyle.PREFERENCE).commit()
    }

    @Test fun everyStylePaintsNativePixelsOnLightAndOledSurfaces() {
        val style = mutableStateOf(VoiceStyle.LIQUID)
        val scheme = mutableStateOf(darkColorScheme(primary = Color(0xff9ae6c7), surface = Color.Black))
        lateinit var view: View
        compose.setContent { view = LocalView.current; MaterialTheme(colorScheme = scheme.value) {
            Surface { VoiceCaptureContent(style.value, .7f, 18, "Pick up milk on the way home.\nAnd call Mum…", true, true, false,
                "Listening…", null, {}, {}, {}, {}) }
        } }
        val folder = File("build/reports/voice-styles").apply { mkdirs() }
        for ((label, colors) in listOf("oled" to darkColorScheme(primary = Color(0xff9ae6c7), surface = Color.Black),
            "light" to lightColorScheme(primary = Color(0xff6550a4)))) {
            val fingerprints = mutableSetOf<Int>()
            for (choice in VoiceStyle.entries) {
                compose.runOnIdle { style.value = choice; scheme.value = colors }
                compose.onNodeWithTag("voice_visual_${choice.id}").assertIsDisplayed()
                compose.onNodeWithTag("voice_record_done_btn").assertIsEnabled()
                val bounds = compose.onNodeWithTag("voice_visual_${choice.id}").fetchSemanticsNode().boundsInRoot
                compose.runOnIdle {
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val bg = bitmap.getPixel(bounds.left.toInt() + 2, bounds.top.toInt() + 2)
                    var painted = 0
                    for (y in bounds.top.toInt() until bounds.bottom.toInt() step 2) for (x in bounds.left.toInt() until bounds.right.toInt() step 2)
                        if (bitmap.getPixel(x, y) != bg) painted++
                    assertTrue("${choice.id} must draw its own visible signal in $label", painted > 25)
                    val signalPixels = IntArray(bounds.width.toInt() * bounds.height.toInt())
                    bitmap.getPixels(signalPixels, 0, bounds.width.toInt(), bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
                    fingerprints += signalPixels.contentHashCode()
                    File(folder, "${choice.id}-$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            assertEquals("All ten styles must paint distinct listening visuals in $label", 10, fingerprints.size)
        }
    }

    @Test fun controlsStayReachableAtLargeFontAndUseTextIsDisabledForEmptySpeech() {
        val text = mutableStateOf("")
        var done = 0; var paused = 0; var cancelled = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                MaterialTheme { Surface {
                    VoiceCaptureContent(VoiceStyle.HALO, .5f, 4, text.value, true, true, false, "Listening", null,
                        { cancelled++ }, { done++ }, { paused++ }, {})
                } }
            }
        }
        compose.onNodeWithTag("voice_record_done_btn").assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle { text.value = "Captured words ".repeat(150) }
        compose.onNodeWithTag("voice_record_done_btn").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("voice_record_pause_btn").assertIsDisplayed().performClick()
        compose.onNodeWithTag("voice_record_cancel_btn").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, done); assertEquals(1, paused); assertEquals(1, cancelled) }
    }

    @Test fun waitingAndErrorsDoNotClaimToListenAndCapturedTextRemainsUsable() {
        val error = mutableStateOf<String?>(null)
        compose.setContent { MaterialTheme { Surface {
            VoiceCaptureContent(VoiceStyle.PARTICLES, .9f, 6, "Keep these words", error.value == null, false, false,
                "Waiting for the speech service…", error.value, {}, {}, {}, {})
        } } }
        compose.onNodeWithText("Connecting…").assertIsDisplayed()
        compose.onNodeWithText("Listening", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { error.value = "The speech service is limiting requests. Wait before retrying." }
        compose.onNodeWithText("Keep these words").assertIsDisplayed()
        compose.onNodeWithTag("voice_record_done_btn").assertIsEnabled()
        compose.onNodeWithTag("voice_record_retry").assertIsDisplayed()
        compose.onNodeWithText("Voice settings").assertDoesNotExist()
    }

    @Test fun pickerPreviewsWithoutOpeningMicrophoneAndAppliesOnlyAnExplicitChoice() {
        compose.setContent { MaterialTheme { VoiceStyleSettingsRow() } }
        compose.onNodeWithTag("voice_style_settings").performClick()
        compose.onNodeWithTag("voice_preview_liquid").performClick()
        compose.onNodeWithText("Liquid Core · Sample preview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause live transcription").performClick()
        compose.onNodeWithText("Paused").assertIsDisplayed()
        compose.onNodeWithTag("voice_record_cancel_btn").performClick()
        assertEquals(VoiceStyle.LIQUID, VoiceStyle.read(context))
        compose.onNodeWithTag("voice_style_halo").performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(VoiceStyle.HALO, VoiceStyle.read(context))
    }

    @Test fun stylePreferenceRoundTripsStableIdsAndRecoversFromUnknownValues() {
        assertEquals(10, VoiceStyle.entries.size)
        assertEquals(10, VoiceStyle.entries.map { it.id }.toSet().size)
        for (style in VoiceStyle.entries) { VoiceStyle.save(context, style); assertEquals(style, VoiceStyle.read(context)) }
        context.getSharedPreferences("focus_prefs", 0).edit().putString(VoiceStyle.PREFERENCE, "old-or-invalid-style").commit()
        assertEquals(VoiceStyle.LIQUID, VoiceStyle.read(context))
    }
    @Test fun animationRespondsToFramesAndFreezesWhenPaused() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        compose.mainClock.autoAdvance = false
        val active = mutableStateOf(true)
        lateinit var view: View
        compose.setContent { view = LocalView.current; MaterialTheme { Surface {
            VoiceVisualization(VoiceStyle.ORBITS, .8f, active.value,
                Modifier.fillMaxWidth().height(156.dp))
        } } }
        fun frames() { repeat(20) {
            compose.mainClock.advanceTimeByFrame()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(34))
        } }
        fun pixels(): IntArray {
            lateinit var result: IntArray
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                result = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(result, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                bitmap.recycle()
            }
            return result
        }
        frames(); val first = pixels(); frames(); val second = pixels()
        assertFalse("The signal should move while microphone capture is active", first.contentEquals(second))
        compose.runOnIdle { active.value = false }
        frames(); val stopped = pixels(); frames()
        assertTrue("Paused visuals must stop requesting animation frames", stopped.contentEquals(pixels()))
        compose.runOnIdle { active.value = true }
        frames()
        compose.runOnIdle { Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) }
        frames(); val reducedMotion = pixels(); frames()
        assertTrue("System reduced motion freezes an active visualization", reducedMotion.contentEquals(pixels()))
    }

}
