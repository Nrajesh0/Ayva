package com.focusbyrj.app.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.ui.screens.ChatMessage
import com.focusbyrj.app.util.AyvaTalkEngine
import com.focusbyrj.app.util.TalkAction
import com.focusbyrj.app.util.assistant.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AyvaResponseControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun selectingRowShowsOnlyScopedActionsAndNeverSendsAMutation() {
        val tasks = listOf(Task(id = 41, title = "Alpha"), Task(id = 99, title = "Beta"))
        val response = AssistantResponseContent.taskList(AyvaTalkEngine.TalkResponse("Tasks"), tasks, System.currentTimeMillis())
        val list = AssistantResponseContent.tasks(response.jsonPayload)!!
        var reference by mutableStateOf<AssistantItemReference?>(null)
        val sent = mutableListOf<String>()
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) { AyvaSelectableTaskList(list, tasks, reference, 15f, { reference = it }, { sent.add(it) }) } }
        compose.onNodeWithText("Mark complete").assertDoesNotExist()
        compose.onNodeWithText("Beta").performClick()
        compose.onNodeWithText("Mark complete").assertExists()
        assertTrue(sent.isEmpty()); assertEquals(99L, reference?.id)
        compose.onNodeWithText("Change time").performClick()
        assertEquals(listOf("reschedule id:99"), sent)
        compose.onNodeWithText("Beta").performClick()
        compose.onNodeWithText("Mark complete").assertDoesNotExist()
        assertNull(reference)
    }
    @Test fun removedAndExpiredListRowsCannotOfferMutationButtons() {
        val task = Task(id = 7, title = "Expired")
        val response = AssistantResponseContent.taskList(AyvaTalkEngine.TalkResponse("Tasks"), listOf(task), System.currentTimeMillis() - 900_001)
        compose.setContent { MaterialTheme { AyvaSelectableTaskList(AssistantResponseContent.tasks(response.jsonPayload)!!, listOf(task), null, 15f, { error("Expired selection") }, { error("Expired action") }) } }
        compose.onNodeWithText("Expired").performClick()
        compose.onNodeWithText("Mark complete").assertDoesNotExist()
        compose.onNodeWithText("This list has expired. Ask for a fresh list.").assertExists()
    }
    @Test fun informationPromotesOneActionAndRevealsSecondaryActionsInMore() {
        val payload = AyvaTalkEngine.serializeActionsJson("help", listOf(TalkAction.AskQuery("/talk settings", "Explore settings"), TalkAction.AskQuery("check device health", "Check permissions")))!!
        val sent = mutableListOf<String>()
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) { AyvaInfoActions(payload) { sent.add(it) } } }
        compose.onNodeWithText("Explore settings").assertExists()
        compose.onNodeWithText("Check permissions").assertDoesNotExist()
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Check permissions").performClick()
        assertEquals(listOf("check device health"), sent)
    }
    @Test fun inlineSettingSendsPreviewAndKeepsItsCurrentValue() {
        val spec = AssistantPreferences.find("reminder interval")!!
        val sent = mutableListOf<String>()
        compose.setContent { MaterialTheme { AyvaInlineSetting(spec) { sent.add(it) } } }
        compose.onNodeWithText("Current: 15 minutes").performClick()
        compose.onNodeWithText("30 minutes").performClick()
        assertEquals(listOf("/talk set persistent reminder interval to 30"), sent)
        compose.onNodeWithText("Current: 15 minutes").assertExists()
    }
    @Test fun confirmationAndCancelRemainVisibleTogether() {
        val payload = AyvaTalkEngine.serializeActionsJson("confirmation", listOf(TalkAction.AskQuery("/ayva confirm nonce", "Confirm"), TalkAction.AskQuery("/ayva cancel nonce", "Cancel")))!!
        compose.setContent { MaterialTheme { AyvaConversationMessage(ChatMessage("preview", "Change interval?", false, isTalkAction = true, talkActionJson = payload), 15f, AssistantChatState(confirmationId = "nonce", expiresAt = System.currentTimeMillis() + 60_000), null, {}) } }
        compose.onNodeWithText("Confirm").assertExists()
        compose.onNodeWithText("Cancel").assertExists()
        compose.onNodeWithText("More").assertDoesNotExist()
    }
    @Test fun legacyPreferenceButtonsAreConvertedToPreviewQueries() {
        val payload = AyvaTalkEngine.serializeActionsJson("legacy", listOf(TalkAction.DirectPrefUpdate("soft_lock_duration", "int", "30", "30 sec", "30 seconds")))!!
        val action = responseActions(payload).single() as TalkAction.AskQuery
        assertEquals("/talk set soft mode wait timer to 30", action.query)
        val invalid = AyvaTalkEngine.serializeActionsJson("legacy", listOf(TalkAction.DirectPrefUpdate("soft_lock_duration", "int", "900", "900 sec", "900 seconds")))!!
        assertTrue(responseActions(invalid).isEmpty())
        val wrongType = AyvaTalkEngine.serializeActionsJson("legacy", listOf(TalkAction.DirectPrefUpdate("soft_lock_duration", "boolean", "true", "On", "Wrong type")))!!
        assertTrue(responseActions(wrongType).isEmpty())
    }
}
