package com.focusbyrj.app.util

import org.junit.Test
import org.junit.Assert.*
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import com.focusbyrj.app.ui.screens.*
import com.focusbyrj.app.util.*

@RunWith(RobolectricTestRunner::class)
class DrillCrashTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testDrillCreation() {
        val session = createDrillSessionWithQuestions("easy", 10)
        assertNotNull(session)
        assertEquals(10, session.preGeneratedQuestions.size)
        
        val medSession = createDrillSessionWithQuestions("medium", 10)
        assertNotNull(medSession)
        
        val hardSession = createDrillSessionWithQuestions("hard", 10)
        assertNotNull(hardSession)
    }

    @Test
    fun testBubbleChatActivityDrillIntent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, BubbleChatActivity::class.java).apply {
            putExtra("EXTRA_START_DRILL", true)
        }
        val controller = Robolectric.buildActivity(BubbleChatActivity::class.java, intent).setup()
        assertNotNull(controller.get())
    }

    @Test
    fun testDrillSummaryHelperGeneration() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        FocusEconomyManager.init(context)
        AptitudeManager.init(context)
        DailyQuestManager.init(context)

        val session = createDrillSessionWithQuestions("easy", 10)
        session.correct = 8
        session.total = 10
        session.maxCombo = 5
        session.gold = 50
        
        val summaryMsg = DrillSummaryHelper.generateSummaryMessage(session)
        assertNotNull(summaryMsg)
        assertTrue(summaryMsg.isDrillSummary)
        assertNotNull(summaryMsg.drillSummaryJson)
    }

    @Test
    fun testFullscreenDrillViewInteractive() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        FocusEconomyManager.init(context)
        AptitudeManager.init(context)
        DailyQuestManager.init(context)

        val session = createDrillSessionWithQuestions("easy", 10)
        val currentJson = session.preGeneratedQuestions.firstOrNull()
        val latestQuestionMessage = ChatMessage(
            id = "drill_active_test",
            text = "Arithmetic Drill",
            isUser = false,
            isArithmetic = true,
            arithmeticJson = currentJson
        )

        composeTestRule.setContent {
            FullscreenDrillView(
                activeSession = session,
                latestQuestionMessage = latestQuestionMessage,
                allQuestions = emptyList(),
                onNextQuestion = {},
                onAnswerSubmitted = { _, _ -> },
                onEndSession = {}
            )
        }
        composeTestRule.waitForIdle()

        // Try clicking option containing 1.
        composeTestRule.onAllNodes(hasText("1.", substring = true))[1].performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testChatInterfaceDrillFlow() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        FocusEconomyManager.init(context)
        AptitudeManager.init(context)
        DailyQuestManager.init(context)

        composeTestRule.setContent {
            ChatInterface()
        }
        composeTestRule.waitForIdle()

        // Find the quick action button for drill or type command
        val drillNodes = composeTestRule.onAllNodes(hasText("drill", substring = true))
        if (drillNodes.fetchSemanticsNodes().isNotEmpty()) {
            drillNodes[0].performClick()
        } else {
            composeTestRule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("/drill easy 10")
        }
        composeTestRule.waitForIdle()

        // Click Send button
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        
        // Wait for IO coroutine to finish and post to Main
        Thread.sleep(1500)
        composeTestRule.waitForIdle()

        // Check if FullscreenDrillView or Drill option appears
        val optionNodes = composeTestRule.onAllNodes(hasText("1.", substring = true))
        if (optionNodes.fetchSemanticsNodes().size > 1) {
            optionNodes[1].performClick()
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun testDrillSummaryInactivityExpiration() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        BubbleChatManager.clearMessages(context)

        val session = createDrillSessionWithQuestions("easy", 10)
        session.correct = 8
        session.total = 10
        val summaryMsg = DrillSummaryHelper.generateSummaryMessage(session)

        // Save summary with a timestamp from 11 minutes ago
        val elevenMinutesAgo = System.currentTimeMillis() - (11 * 60 * 1000L)
        val oldSummaryMsg = summaryMsg.copy(
            timestamp = elevenMinutesAgo,
            firstViewedTimestamp = elevenMinutesAgo
        )
        BubbleChatManager.saveMessages(context, listOf(oldSummaryMsg.toPersistedChatMessage()), updateActivityTimestamp = false)

        // Check if messages exist
        assertEquals(1, BubbleChatManager.getMessages(context).size)

        // Run cleanup
        val cleaned = BubbleChatManager.checkAndClearIfInactive(context)
        assertTrue(cleaned)
        assertEquals(0, BubbleChatManager.getMessages(context).size)
    }

    @Test
    fun testDrillSummaryCardDismissal() {
        val session = createDrillSessionWithQuestions("easy", 10)
        session.correct = 10
        session.total = 10
        val summaryMsg = DrillSummaryHelper.generateSummaryMessage(session)

        var dismissed = false
        composeTestRule.setContent {
            DrillSummaryCard(
                message = summaryMsg,
                fontSizeSp = 15f,
                onDismiss = { dismissed = true }
            )
        }
        composeTestRule.waitForIdle()

        // Find dismiss button
        composeTestRule.onNodeWithContentDescription("Dismiss Drill Summary").performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun testAlertDoesNotExpireBeforeChatOpened() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        BubbleChatManager.clearMessages(context)

        val morningAlert = PersistedChatMessage(
            id = "morning_test",
            text = "Good morning! Here is your plan.",
            isUser = false,
            timestamp = System.currentTimeMillis() - (60 * 60 * 1000L), // 1 hour ago
            firstViewedTimestamp = 0L, // Never opened/viewed
            isMorningBrief = true
        )
        BubbleChatManager.saveMessages(context, listOf(morningAlert), updateActivityTimestamp = false)

        // Run cleanup - should NOT clear because chat was not opened
        val cleaned = BubbleChatManager.checkAndClearIfInactive(context)
        assertFalse(cleaned)
        assertEquals(1, BubbleChatManager.getMessages(context).size)

        // User opens the chat window now
        BubbleChatManager.markAllAsViewed(context)
        val viewedMsg = BubbleChatManager.getMessages(context).first()
        assertTrue(viewedMsg.firstViewedTimestamp > 0L)

        // Right after viewing, cleanup should still keep it
        val cleanedAfterOpen = BubbleChatManager.checkAndClearIfInactive(context)
        assertFalse(cleanedAfterOpen)
        assertEquals(1, BubbleChatManager.getMessages(context).size)
    }

    @Test
    fun testCasualChatMessagesStayFor10MinutesOnceViewed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        BubbleChatManager.clearMessages(context)

        val now = System.currentTimeMillis()
        // Message viewed 5 minutes ago should stay
        val fiveMinMsg = PersistedChatMessage(
            id = "chat_1",
            text = "Added task: Read a book",
            isUser = false,
            timestamp = now - (5 * 60 * 1000L),
            firstViewedTimestamp = now - (5 * 60 * 1000L)
        )
        // Message viewed 11 minutes ago should expire
        val elevenMinMsg = PersistedChatMessage(
            id = "chat_2",
            text = "Old query",
            isUser = true,
            timestamp = now - (11 * 60 * 1000L),
            firstViewedTimestamp = now - (11 * 60 * 1000L)
        )
        BubbleChatManager.saveMessages(context, listOf(fiveMinMsg, elevenMinMsg), updateActivityTimestamp = false)
        assertEquals(2, BubbleChatManager.getMessages(context).size)

        val cleaned = BubbleChatManager.checkAndClearIfInactive(context)
        assertTrue(cleaned)
        val remaining = BubbleChatManager.getMessages(context)
        assertEquals(1, remaining.size)
        assertEquals("chat_1", remaining[0].id)
    }

    @Test
    fun testActiveDrillQuestionsNeverExpireWhileActive() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        BubbleChatManager.clearMessages(context)

        val now = System.currentTimeMillis()
        // Question displayed 15 minutes ago during an ongoing drill
        val activeQuestionMsg = PersistedChatMessage(
            id = "arithmetic_question_1",
            text = "What is 12 x 12?",
            isUser = false,
            timestamp = now - (15 * 60 * 1000L),
            firstViewedTimestamp = now - (15 * 60 * 1000L),
            isArithmetic = true
        )
        BubbleChatManager.saveMessages(context, listOf(activeQuestionMsg), updateActivityTimestamp = false)

        // While drill is active, it must NOT be cleared even if 15 mins elapsed
        val cleanedWhileActive = BubbleChatManager.checkAndClearIfInactive(context, isDrillOrQuizActive = true)
        assertFalse(cleanedWhileActive)
        assertEquals(1, BubbleChatManager.getMessages(context).size)

        // Even with default isDrillOrQuizActive, active arithmetic question is recognized and protected
        val cleanedDefault = BubbleChatManager.checkAndClearIfInactive(context)
        assertFalse(cleanedDefault)
        assertEquals(1, BubbleChatManager.getMessages(context).size)
    }
}
