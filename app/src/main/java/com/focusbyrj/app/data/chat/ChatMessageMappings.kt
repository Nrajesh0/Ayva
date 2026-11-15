package com.focusbyrj.app.data.chat

import com.focusbyrj.app.util.PersistedChatMessage

fun PersistedChatMessage.toChatMessage(): ChatMessage {
    return ChatMessage(
        id = id,
        text = text,
        isUser = isUser,
        timestamp = timestamp,
        firstViewedTimestamp = firstViewedTimestamp,
        isArithmetic = isArithmetic,
        arithmeticJson = arithmeticJson,
        isDrillSummary = isDrillSummary,
        drillSummaryJson = drillSummaryJson,
        isAptitudeProfile = isAptitudeProfile,
        isStreakPrompt = isStreakPrompt,
        streakPromptJson = streakPromptJson,
        isTaskSummary = isTaskSummary,
        taskSummaryJson = taskSummaryJson,
        isTalkAction = isTalkAction,
        talkActionJson = talkActionJson,
        pendingActionJson = pendingActionJson,
        isDailyQuests = isDailyQuests,
        isMysteryBox = isMysteryBox || id.startsWith("mystery_box_"),
        isMorningBrief = isMorningBrief || id.startsWith("morning_"),
        isEveningBrief = isEveningBrief || id.startsWith("evening_"),
        isStreakFreezeSkipped = isStreakFreezeSkipped || id.startsWith("angry_freeze_"),
        isVocabBrief = isVocabBrief,
        isVocabHub = isVocabHub || id.startsWith("vocab_hub_"),
        vocabJson = vocabJson,
        isWelcome =
            isWelcome ||
                id.startsWith("welcome_") ||
                text.contains("Hey! Ayva is on deck", ignoreCase = true) ||
                text.contains("ready for action", ignoreCase = true) ||
                text.contains("Ayva here!", ignoreCase = true) ||
                text.contains("I'm Ayva", ignoreCase = true) ||
                text.contains("anti-procrastination", ignoreCase = true),
        isHabitsSummary = isHabitsSummary || id.startsWith("habits_"),
        habitsSummaryJson = habitsSummaryJson,
    )
}

fun ChatMessage.toPersistedChatMessage(markViewedIfActive: Boolean = true): PersistedChatMessage {
    val viewedTs =
        if (firstViewedTimestamp > 0L) {
            firstViewedTimestamp
        } else if (markViewedIfActive) {
            System.currentTimeMillis()
        } else {
            0L
        }
    return PersistedChatMessage(
        id = id,
        text = text,
        isUser = isUser,
        timestamp = timestamp,
        firstViewedTimestamp = viewedTs,
        isArithmetic = isArithmetic,
        arithmeticJson = arithmeticJson,
        isDrillSummary = isDrillSummary,
        drillSummaryJson = drillSummaryJson,
        isAptitudeProfile = isAptitudeProfile,
        isStreakPrompt = isStreakPrompt,
        streakPromptJson = streakPromptJson,
        isTaskSummary = isTaskSummary,
        taskSummaryJson = taskSummaryJson,
        isTalkAction = isTalkAction,
        talkActionJson = talkActionJson,
        pendingActionJson = pendingActionJson,
        isDailyQuests = isDailyQuests,
        isMysteryBox = isMysteryBox,
        isMorningBrief = isMorningBrief,
        isEveningBrief = isEveningBrief,
        isStreakFreezeSkipped = isStreakFreezeSkipped,
        isVocabBrief = isVocabBrief,
        isVocabHub = isVocabHub,
        vocabJson = vocabJson,
        isWelcome = isWelcome,
        isHabitsSummary = isHabitsSummary,
        habitsSummaryJson = habitsSummaryJson,
    )
}
