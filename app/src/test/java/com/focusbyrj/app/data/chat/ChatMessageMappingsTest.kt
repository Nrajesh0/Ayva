package com.focusbyrj.app.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageMappingsTest {
    @Test
    fun everyChatPayloadSurvivesStorageAndPresentationRoundTrip() {
        val stored =
            AyvaChatMessageEntity(
                id = "round-trip",
                text = "Synthetic message",
                isUser = true,
                timestamp = 123L,
                firstViewedTimestamp = 456L,
                isArithmetic = true,
                arithmeticJson = "arithmetic",
                isDrillSummary = true,
                drillSummaryJson = "drill",
                isAptitudeProfile = true,
                isStreakPrompt = true,
                streakPromptJson = "streak",
                isTaskSummary = true,
                taskSummaryJson = "tasks",
                isTalkAction = true,
                talkActionJson = "talk",
                pendingActionJson = "pending",
                isDailyQuests = true,
                isMysteryBox = true,
                isMorningBrief = true,
                isEveningBrief = true,
                isStreakFreezeSkipped = true,
                isVocabBrief = true,
                isVocabHub = true,
                vocabJson = "vocabulary",
                isWelcome = true,
                isHabitsSummary = true,
                habitsSummaryJson = "habits",
            )
        fun roundTrip(entity: AyvaChatMessageEntity) =
            entity
                .toPersistedChatMessage()
                .toChatMessage()
                .toPersistedChatMessage(markViewedIfActive = false)
                .toEntity()

        assertEquals(stored, roundTrip(stored))
        val plain = AyvaChatMessageEntity("plain", "Plain message", false, timestamp = 789L)
        assertEquals(plain, roundTrip(plain))
    }

    @Test
    fun legacyPresentationHintsAndUnreadTimestampPolicyArePreserved() {
        val legacy =
            AyvaChatMessageEntity("morning_123", "Morning", false, timestamp = 123L)
                .toPersistedChatMessage()
                .toChatMessage()
        assertTrue(legacy.isMorningBrief)
        assertEquals(
            0L,
            legacy.toPersistedChatMessage(markViewedIfActive = false).firstViewedTimestamp,
        )
        assertTrue(legacy.toPersistedChatMessage().firstViewedTimestamp > 0L)
        val viewed = legacy.copy(firstViewedTimestamp = 456L)
        assertEquals(456L, viewed.toPersistedChatMessage().firstViewedTimestamp)
    }
}
