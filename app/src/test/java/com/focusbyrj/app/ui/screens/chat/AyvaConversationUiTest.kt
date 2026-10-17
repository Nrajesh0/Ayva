package com.focusbyrj.app.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import com.focusbyrj.app.ui.screens.ChatMessage
import com.focusbyrj.app.util.assistant.AssistantChatState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AyvaConversationUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun messageMenuReplySelectsAQuoteWithoutSending() {
        var quoted: ChatMessage? = null
        var sent = false
        val message = ChatMessage("assistant", "Today at 3 PM.", false)
        compose.setContent { MaterialTheme { AyvaConversationMessage(message, 15f, AssistantChatState(), { quoted = it }, { sent = true }) } }
        compose.onNodeWithContentDescription("Message actions").performClick()
        compose.onNodeWithText("Reply").performClick()
        compose.runOnIdle { assertEquals(message, quoted); assertFalse(sent) }
    }
    @Test fun swipeReplyAndVerticalScrollingAreDistinct() {
        var replies = 0
        compose.setContent { MaterialTheme { AyvaConversationMessage(ChatMessage("assistant", "Today at 3 PM.", false), 15f, AssistantChatState(), { replies++ }, {}) } }
        compose.onNodeWithText("Today at 3 PM.").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0, replies) }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(1, replies) }
    }
    @Test fun expiredReceiptKeepsHistoryButRemovesUndo() {
        val state = mutableStateOf(AssistantChatState(undoId = "valid", expiresAt = Long.MAX_VALUE))
        val payload = """{"actions":[{"type":"ask_query","label":"Undo","query":"/ayva undo valid"}],"presentation":{"version":1,"receipt":{"status":"saved","items":[{"title":"Proposal","detail":"Tomorrow at 10 AM","previous":"Today at 3 PM"}]}}}"""
        var sent: String? = null
        compose.setContent { MaterialTheme { AyvaConversationMessage(ChatMessage("saved", "Updated.", false, talkActionJson = payload), 15f, state.value, null, { sent = it }) } }
        compose.onNodeWithText("Undo").assertExists()
        compose.runOnIdle { state.value = AssistantChatState() }
        compose.onNodeWithText("Undo").assertDoesNotExist()
        compose.onNodeWithText("Proposal").assertExists()
        compose.onNodeWithText("Previously Today at 3 PM").assertExists()
        compose.runOnIdle { assertNull(sent) }
    }
    @Test fun headerKeepsExistingControlsAndSeparateSearch() {
        var searched = false; var textSize = false
        compose.setContent { MaterialTheme { AyvaCompactHeader({}, { searched = true }, {}, { textSize = true }, {}, {}) } }
        compose.onNodeWithContentDescription("Search conversation").performClick()
        compose.runOnIdle { assertTrue(searched) }
        compose.onNodeWithContentDescription("Chat controls").performClick()
        for (label in listOf("Notification Colors", "Text Size", "Clear History", "Open Settings")) compose.onNodeWithText(label).assertExists()
        compose.onNodeWithText("Text Size").performClick()
        compose.runOnIdle { assertTrue(textSize) }
    }
    @Test fun searchFindsAndSelectsRealMessagesLocally() {
        val original = ChatMessage("old", "Review the proposal tomorrow", false)
        var selected: ChatMessage? = null
        compose.setContent { MaterialTheme { AyvaConversationSearch(listOf(original, ChatMessage("other", "Drink water", true)), { selected = it }, {}) } }
        compose.onNodeWithText("Find a message").performTextInput("proposal")
        compose.onNodeWithText(original.text).performClick()
        compose.runOnIdle { assertEquals(original, selected) }
    }
    @Test fun replyPreviewCanBeCancelledWithoutChangingDraft() {
        val quote = mutableStateOf<ChatMessage?>(ChatMessage("old", "An earlier reply", false))
        compose.setContent { MaterialTheme { Column { quote.value?.let { AyvaReplyPreview(it) { quote.value = null } } } } }
        compose.onNodeWithContentDescription("Cancel reply").performClick()
        compose.runOnIdle { assertNull(quote.value) }
        compose.onNodeWithText("Replying to Ayva").assertDoesNotExist()
    }
}
