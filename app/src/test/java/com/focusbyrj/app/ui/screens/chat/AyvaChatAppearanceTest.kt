package com.focusbyrj.app.ui.screens.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.focusbyrj.app.ui.screens.ChatMessage
import com.focusbyrj.app.util.assistant.AssistantChatState
import com.focusbyrj.app.util.assistant.AssistantItemReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AyvaChatAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun headerAndContextControlsStayVisibleAcrossDarkAndLightSurfaces() {
        val scheme = mutableStateOf(darkColorScheme(surface = Color(0xFF0A0A0A)))
        lateinit var chatView: View
        compose.setContent {
            chatView = LocalView.current
            MaterialTheme(colorScheme = scheme.value) {
                // Reproduce an Activity without a parent Surface: content defaults
                // to black even though the Material color scheme is dark.
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                    AyvaCompactHeader({}, {}, {}, {}, {}, {})
                    AyvaActiveContext(AssistantItemReference("task", 1, "Buy milk"), {}, {})
                }
            }
        }
        for (colors in listOf(
            darkColorScheme(surface = Color(0xFF0A0A0A)),
            darkColorScheme(surface = Color(0xFF151921)),
            darkColorScheme(surface = Color(0xFF1B1F27)),
            darkColorScheme(surface = Color(0xFF12151B)),
            lightColorScheme(surface = Color(0xFFF8F9FA))
        )) {
            compose.runOnIdle { scheme.value = colors }
            assertTextColor("Ayva", colors.onSurface)
            // Draw the real Compose view directly: PixelCopy's frame callback is
            // unavailable in this headless Robolectric window.
            lateinit var bitmap: Bitmap
            compose.runOnIdle {
                bitmap = Bitmap.createBitmap(chatView.width, chatView.height, Bitmap.Config.ARGB_8888)
                chatView.draw(Canvas(bitmap))
            }
            for (description in listOf("Close chat", "Search conversation", "Chat controls", "Release active reference")) {
                val bounds = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
                var visible = 0
                for (y in bounds.top.toInt() until bounds.bottom.toInt()) for (x in bounds.left.toInt() until bounds.right.toInt()) {
                    val color = Color(bitmap.getPixel(x, y))
                    val foreground = if (description == "Release active reference") colors.onSurfaceVariant else colors.onSurface
                    if (kotlin.math.abs(color.red - foreground.red) < 0.08f &&
                        kotlin.math.abs(color.green - foreground.green) < 0.08f &&
                        kotlin.math.abs(color.blue - foreground.blue) < 0.08f) visible++
                }
                assertTrue("$description must paint a visible icon on ${colors.surface}", visible > 3)
            }
            bitmap.recycle()
        }
    }

    @Test fun tintedReceiptAndQuoteUseReadableForegroundWithoutParentSurface() {
        val scheme = mutableStateOf(darkColorScheme(surface = Color(0xFF0A0A0A)))
        val payload = """{"presentation":{"version":1,"receipt":{"status":"saved","items":[{"title":"Buy milk","detail":"Tomorrow at 8 AM"}]}}}"""
        compose.setContent {
            MaterialTheme(colorScheme = scheme.value) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                    AyvaConversationMessage(ChatMessage("saved", "Added.", false, talkActionJson = payload), 15f, AssistantChatState(), null, null)
                    AyvaReplyPreview(ChatMessage("quote", "Earlier reply", false), {})
                    AyvaConversationMessage(ChatMessage("user", "My message", true), 15f, AssistantChatState(), null, null)
                }
            }
        }
        for (colors in listOf(darkColorScheme(surface = Color(0xFF0A0A0A)), lightColorScheme())) {
            compose.runOnIdle { scheme.value = colors }
            for (text in listOf("Buy milk", "Earlier reply", "My message")) assertTextColor(text, colors.onSurface)
        }
    }

    private fun assertTextColor(text: String, expected: Color) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals("Text color for $text", expected, results.single().layoutInput.style.color)
    }
}
