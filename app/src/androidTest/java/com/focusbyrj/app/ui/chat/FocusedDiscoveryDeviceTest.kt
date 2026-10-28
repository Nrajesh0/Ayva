package com.focusbyrj.app.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
import org.junit.Assert.assertTrue
import com.focusbyrj.app.ui.prepareKeyboard
import com.focusbyrj.app.ui.imeVisible
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focusbyrj.app.ui.screens.chat.AyvaComposerBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusedDiscoveryDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun largeTextAndKeyboardBackKeepTheDraft() {
        prepareKeyboard()
        val draft = mutableStateOf(TextFieldValue("keep this draft"))
        compose.runOnUiThread { compose.activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                MaterialTheme {
                    AyvaComposerBar(draft.value, { draft.value = it }, emptyList(), {}, emptyList(), {},
                        false, {}, false, {}, null, {})
                }
            }
        }
        val field = compose.onAllNodes(hasSetTextAction()).onFirst()
        field.performClick()
        field.performTextClearance()
        field.performTextInput("keep this draft")
        try { compose.waitUntil(30_000) { imeVisible() } }
        catch (failure: Throwable) { com.focusbyrj.app.ui.screenshot("focused-keyboard-failure.png"); throw failure }
        compose.onNodeWithText("keep this draft").assertIsDisplayed()
        compose.onNodeWithText("Explore Ayva").performClick()
        compose.onNodeWithText("Word chain").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("Word chain").assertDoesNotExist()
        compose.onNodeWithText("keep this draft").assertIsDisplayed()
        compose.runOnIdle { assertEquals("keep this draft", draft.value.text); assertTrue(!compose.activity.isFinishing) }
    }
    @Test fun discoveryStartsAnActivityWithoutReplacingTheDraft() {
        val draft = mutableStateOf(TextFieldValue("unfinished draft"))
        var requested = ""
        compose.setContent {
            MaterialTheme {
                AyvaComposerBar(draft.value, { draft.value = it }, emptyList(), {}, emptyList(), {},
                    false, {}, false, {}, null, {}, onExploreRequest = { requested = it })
            }
        }
        compose.onNodeWithText("Explore Ayva").performClick()
        compose.onNodeWithText("Word chain").performClick()
        compose.runOnIdle {
            assertEquals("play word chain", requested)
            assertEquals("unfinished draft", draft.value.text)
        }
        compose.onNodeWithText("unfinished draft").assertIsDisplayed()
        compose.onNodeWithText("Explore Ayva").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("unfinished draft").assertIsDisplayed()
    }
}
