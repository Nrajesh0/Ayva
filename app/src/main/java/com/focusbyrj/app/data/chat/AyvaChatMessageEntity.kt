/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.data.chat

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.focusbyrj.app.util.PersistedChatMessage

@Entity(
    tableName = "ayva_chat_messages",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["firstViewedTimestamp"])
    ]
)
data class AyvaChatMessageEntity(
    @PrimaryKey val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val firstViewedTimestamp: Long = 0L,
    val isArithmetic: Boolean = false,
    val arithmeticJson: String? = null,
    val isDrillSummary: Boolean = false,
    val drillSummaryJson: String? = null,
    val isAptitudeProfile: Boolean = false,
    val isStreakPrompt: Boolean = false,
    val streakPromptJson: String? = null,
    val isTaskSummary: Boolean = false,
    val taskSummaryJson: String? = null,
    val isTalkAction: Boolean = false,
    val talkActionJson: String? = null,
    val pendingActionJson: String? = null,
    val isDailyQuests: Boolean = false,
    val isMysteryBox: Boolean = false,
    val isMorningBrief: Boolean = false,
    val isEveningBrief: Boolean = false,
    val isStreakFreezeSkipped: Boolean = false,
    val isVocabBrief: Boolean = false,
    val isVocabHub: Boolean = false,
    val vocabJson: String? = null,
    val isWelcome: Boolean = false,
    val isHabitsSummary: Boolean = false,
    val habitsSummaryJson: String? = null
)

fun AyvaChatMessageEntity.toPersistedChatMessage(): PersistedChatMessage {
    return PersistedChatMessage(
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
        isMysteryBox = isMysteryBox,
        isMorningBrief = isMorningBrief,
        isEveningBrief = isEveningBrief,
        isStreakFreezeSkipped = isStreakFreezeSkipped,
        isVocabBrief = isVocabBrief,
        isVocabHub = isVocabHub,
        vocabJson = vocabJson,
        isWelcome = isWelcome,
        isHabitsSummary = isHabitsSummary,
        habitsSummaryJson = habitsSummaryJson
    )
}

fun PersistedChatMessage.toEntity(): AyvaChatMessageEntity {
    return AyvaChatMessageEntity(
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
        isMysteryBox = isMysteryBox,
        isMorningBrief = isMorningBrief,
        isEveningBrief = isEveningBrief,
        isStreakFreezeSkipped = isStreakFreezeSkipped,
        isVocabBrief = isVocabBrief,
        isVocabHub = isVocabHub,
        vocabJson = vocabJson,
        isWelcome = isWelcome,
        isHabitsSummary = isHabitsSummary,
        habitsSummaryJson = habitsSummaryJson
    )
}
