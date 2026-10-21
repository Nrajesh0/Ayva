package com.focusbyrj.app.ui

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingPolicies
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.focusbyrj.app.ui.screens.AddTaskDialog
import com.focusbyrj.app.ui.screens.BubbleChatActivity
import com.focusbyrj.app.ui.screens.QuickAddTaskActivity
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

internal fun prepareKeyboard() {
    IdlingPolicies.setMasterPolicyTimeout(120, TimeUnit.SECONDS)
    IdlingPolicies.setIdlingResourceTimeout(120, TimeUnit.SECONDS)
    listOf("svc power stayon true", "input keyevent 224", "wm dismiss-keyguard", "settings put system screen_off_timeout 2147483647", "settings put secure show_ime_with_hard_keyboard 1").forEach { command ->
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }
}

internal fun imeVisible(): Boolean = WindowInspector.getGlobalWindowViews()
    .any { ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) == true }

internal fun screenshot(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
    val destination = File(instrumentation.targetContext.getExternalFilesDir(null), name)
    destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
}

/** Requires a booted device with an actual IME; Robolectric cannot establish this behavior. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class TaskSheetBackDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun prepare() = prepareKeyboard()

    @Test fun firstBackHidesKeyboardAndSecondBackDismissesTheSheet() {
        var open by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (open) AddTaskDialog(onDismiss = { open = false }, onSave = { _, _ -> })
                else Text("Editor closed")
            }
        }
        val field = compose.onAllNodes(hasSetTextAction()).onFirst()
        field.performClick()
        field.performTextClearance()
        field.performTextInput("Device sheet draft")
        compose.waitUntil(60_000) { imeVisible() }
        Espresso.pressBack()
        compose.waitUntil(60_000) { !imeVisible() }
        compose.onNodeWithText("Device sheet draft").assertIsDisplayed()
        screenshot("task-sheet-after-keyboard-back.png")
        Espresso.pressBack()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Editor closed").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Editor closed").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class QuickAddBackDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<QuickAddTaskActivity>()
    @Before fun prepare() = prepareKeyboard()

    @Test fun keyboardBackKeepsTheEditorAndRecreationRecoversItsDraft() {
        val field = compose.onAllNodes(hasSetTextAction()).onFirst()
        field.performClick()
        field.performTextClearance()
        field.performTextInput("Device quick add draft")
        compose.waitUntil(60_000) { imeVisible() }
        Espresso.pressBack()
        compose.waitUntil(60_000) { !imeVisible() }
        assertFalse(compose.activity.isFinishing)
        compose.onNodeWithText("Device quick add draft").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Device quick add draft").assertIsDisplayed()
        screenshot("quick-add-restored-draft.png")
    }
}

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ChatModeDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<BubbleChatActivity>()
    @Before fun prepare() = prepareKeyboard()

    @Test fun modeSelectionAndRecreationPreserveUnsentText() {
        listOf("Plan", "Talk", "Drill").forEach { mode ->
            if (compose.onAllNodesWithContentDescription("Exit $mode mode").fetchSemanticsNodes().isNotEmpty())
                compose.onNodeWithContentDescription("Exit $mode mode").performClick()
        }
        val field = compose.onAllNodes(hasSetTextAction()).onFirst()
        field.performClick(); field.performTextClearance(); field.performTextInput("/plan")
        compose.waitUntil(60_000) { imeVisible() }
        compose.onNodeWithText("Use plan mode").assertIsDisplayed()
        screenshot("chat-plan-suggestion-keyboard-open.png")
        compose.onNodeWithText("Use plan mode").performClick()
        compose.onNodeWithContentDescription("Exit Plan mode").assertIsDisplayed()
        field.performTextInput("Device planning draft\nKeep this second line")
        compose.onNodeWithContentDescription("Send").assertIsDisplayed()
        compose.onNodeWithContentDescription("Talk to Ayva").assertIsDisplayed()
        screenshot("chat-composer-keyboard-open.png")
        Espresso.pressBack()
        compose.waitUntil(60_000) { !imeVisible() }
        assertFalse(compose.activity.isFinishing)
        compose.onNodeWithText("Device planning draft\nKeep this second line").assertIsDisplayed()
        compose.onNodeWithContentDescription("Talk to Ayva").performClick()
        compose.onNodeWithContentDescription("Exit Talk mode").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Exit Talk mode").assertIsDisplayed()
        compose.onNodeWithText("Device planning draft\nKeep this second line").assertIsDisplayed()
        screenshot("chat-mode-restored-draft.png")
        val reopened = compose.activity
        compose.waitUntil(60_000) { !imeVisible() }
        Espresso.pressBackUnconditionally()
        compose.waitUntil(60_000) { reopened.isFinishing }
    }
}
