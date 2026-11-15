/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.util.backup

import com.focusbyrj.app.data.*
import com.focusbyrj.app.data.chat.AyvaChatMessageEntity
import com.focusbyrj.app.data.drill.DrillSessionEntity
import com.focusbyrj.app.data.note.NoteEntity
import org.json.JSONObject

/**
 * Record encoding only; authorization, storage admission and commit ordering stay in the
 * coordinator.
 */
internal object BackupRecordCodec {
    fun note(note: NoteEntity): JSONObject =
        JSONObject().apply {
            put("id", note.id)
            put("title", note.title)
            put("content", note.content)
            put("isChecklist", note.isChecklist)
            put("checklistJson", note.checklistJson)
            put("labelsJson", note.labelsJson)
            put("imageUrisJson", note.imageUrisJson)
            put("audioUrisJson", note.audioUrisJson)
            put("colorKey", note.colorKey)
            put("fontKey", note.fontKey)
            put("isPinned", note.isPinned)
            put("isArchived", note.isArchived)
            put("isTrashed", note.isTrashed)
            if (note.trashedAt != null) put("trashedAt", note.trashedAt)
            if (note.deletedAt != null) put("deletedAt", note.deletedAt)
            put("createdAt", note.createdAt)
            put("updatedAt", note.updatedAt)
        }

    fun restriction(res: AppRestriction): JSONObject =
        JSONObject().apply {
            put("packageName", res.packageName)
            put("appName", res.appName)
            put("isRestricted", res.isRestricted)
            put("mode", res.mode)
            put("restrictionMode", res.restrictionMode)
            put("timeLimitMinutes", res.timeLimitMinutes)
            put("clickLimitCount", res.clickLimitCount)
            put("customQuote", res.customQuote)
        }

    fun schedule(sc: FocusSchedule): JSONObject =
        JSONObject().apply {
            put("id", sc.id)
            put("name", sc.name)
            put("startHour", sc.startHour)
            put("startMinute", sc.startMinute)
            put("endHour", sc.endHour)
            put("endMinute", sc.endMinute)
            put("daysOfWeek", sc.daysOfWeek)
            put("mode", sc.mode)
            put("restrictionMode", sc.restrictionMode)
            put("timeLimitMinutes", sc.timeLimitMinutes)
            put("clickLimitCount", sc.clickLimitCount)
            put("appsToBlock", sc.appsToBlock)
            put("isEnabled", sc.isEnabled)
        }

    fun task(t: Task): JSONObject =
        JSONObject().apply {
            put("id", t.id)
            put("title", t.title)
            put("details", t.details)
            put("dueDate", t.dueDate ?: JSONObject.NULL)
            put("isCompleted", t.isCompleted)
            put("completedAt", t.completedAt ?: JSONObject.NULL)
            put("type", t.type.name)
            put("recurrence", t.recurrence.name)
            put("isPersistent", t.isPersistent)
            put("isPriority", t.isPriority)
            put("subtasksJson", t.subtasksJson)
            put("planningJson", t.planningJson)
            put("updatedAt", t.updatedAt)
            put("isTrashed", t.isTrashed)
            put("trashedAt", t.trashedAt ?: JSONObject.NULL)
            put("deletedAt", t.deletedAt ?: JSONObject.NULL)
        }

    fun habit(h: Habit): JSONObject =
        JSONObject().apply {
            put("id", h.id)
            put("title", h.title)
            put("description", h.description)
            put("iconEmoji", h.iconEmoji)
            put("colorHex", h.colorHex)
            put("type", h.type.name)
            put("targetPerDay", h.targetPerDay)
            put("intervalHours", h.intervalHours)
            put("intervalMinutes", h.intervalMinutes)
            put("windowStartHour", h.windowStartHour)
            put("windowStartMinute", h.windowStartMinute)
            put("windowEndHour", h.windowEndHour)
            put("windowEndMinute", h.windowEndMinute)
            put("fixedReminderHour", h.fixedReminderHour)
            put("fixedReminderMinute", h.fixedReminderMinute)
            put("isReminderEnabled", h.isReminderEnabled)
            put("reminderSound", h.reminderSound)
            put("createdAt", h.createdAt)
            put("isArchived", h.isArchived)
            put("pausedAt", h.pausedAt ?: JSONObject.NULL)
            put("pauseHistoryJson", h.pauseHistoryJson)
            put("frequencyJson", h.frequencyJson)
        }

    fun habitLog(l: HabitLog): JSONObject =
        JSONObject().apply {
            put("id", l.id)
            put("habitId", l.habitId)
            put("date", l.date)
            put("completedCount", l.completedCount)
            put("targetCount", l.targetCount)
            put("lastCompletedTimestamp", l.lastCompletedTimestamp ?: JSONObject.NULL)
        }

    fun drill(d: DrillSessionEntity): JSONObject =
        JSONObject().apply {
            put("sessionId", d.sessionId)
            put("title", d.title)
            put("totalQuestions", d.totalQuestions)
            put("correctCount", d.correctCount)
            put("timeSpentSeconds", d.timeSpentSeconds)
            put("xpEarned", d.xpEarned)
            put("isBlitz", d.isBlitz)
            put("isClaimed", d.isClaimed)
            put("timestamp", d.timestamp)
            put("summaryJson", d.summaryJson)
        }

    fun idiomProgress(idm: Idiom): JSONObject =
        JSONObject().apply {
            put("id", idm.id ?: 0)
            put("learned_at", idm.learnedAt ?: 0L)
            put("is_mastered", idm.isMastered ?: 0)
            put("is_bookmarked", idm.isBookmarked ?: 0)
        }

    fun owsProgress(ow: Ows): JSONObject =
        JSONObject().apply {
            put("id", ow.id ?: 0)
            put("learned_at", ow.learnedAt ?: 0L)
            put("is_mastered", ow.isMastered ?: 0)
            put("is_bookmarked", ow.isBookmarked ?: 0)
        }

    fun chat(msg: AyvaChatMessageEntity): JSONObject =
        JSONObject().apply {
            put("id", msg.id)
            put("text", msg.text)
            put("isUser", msg.isUser)
            put("timestamp", msg.timestamp)
            put("firstViewedTimestamp", msg.firstViewedTimestamp)
            put("isArithmetic", msg.isArithmetic)
            if (msg.arithmeticJson != null) put("arithmeticJson", msg.arithmeticJson)
            put("isDrillSummary", msg.isDrillSummary)
            if (msg.drillSummaryJson != null) put("drillSummaryJson", msg.drillSummaryJson)
            put("isAptitudeProfile", msg.isAptitudeProfile)
            put("isStreakPrompt", msg.isStreakPrompt)
            if (msg.streakPromptJson != null) put("streakPromptJson", msg.streakPromptJson)
            put("isTaskSummary", msg.isTaskSummary)
            if (msg.taskSummaryJson != null) put("taskSummaryJson", msg.taskSummaryJson)
            put("isTalkAction", msg.isTalkAction)
            if (msg.talkActionJson != null) put("talkActionJson", msg.talkActionJson)
            if (msg.pendingActionJson != null) put("pendingActionJson", msg.pendingActionJson)
            put("isDailyQuests", msg.isDailyQuests)
            put("isMysteryBox", msg.isMysteryBox)
            put("isMorningBrief", msg.isMorningBrief)
            put("isEveningBrief", msg.isEveningBrief)
            put("isStreakFreezeSkipped", msg.isStreakFreezeSkipped)
            put("isVocabBrief", msg.isVocabBrief)
            put("isVocabHub", msg.isVocabHub)
            if (msg.vocabJson != null) put("vocabJson", msg.vocabJson)
            put("isWelcome", msg.isWelcome)
            put("isHabitsSummary", msg.isHabitsSummary)
            if (msg.habitsSummaryJson != null) put("habitsSummaryJson", msg.habitsSummaryJson)
        }
}
