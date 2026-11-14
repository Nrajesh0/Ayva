package com.focusbyrj.app.util

import android.content.Context

/** Automatic delivery only. Explicit replies/results and saved history stay available. */
object AssistantUpdateDelivery {
    fun category(message: PersistedChatMessage): AyvaAlertCategory = when {
        message.isWelcome -> AyvaAlertCategory.GENERAL
        message.isMorningBrief || message.isEveningBrief || message.isVocabBrief -> AyvaAlertCategory.BRIEFING
        message.id.startsWith("auto_reward_") -> AyvaAlertCategory.REWARDS
        message.isStreakPrompt -> AyvaAlertCategory.DRILL_PRACTICE
        else -> AyvaAlertCategory.infer(
        message.text, message.isMorningBrief, message.isEveningBrief,
        message.isArithmetic || message.isDrillSummary, message.isStreakPrompt, message.id)
    }

    fun allows(context: Context, message: PersistedChatMessage): Boolean {
        if (message.isUser) return true
        val category = category(message)
        if (!category.isEnabled(context)) return false
        if (category == AyvaAlertCategory.DRILL_PRACTICE) {
            val prefs = context.getSharedPreferences("bubble_prefs", 0)
            if (!prefs.getBoolean("streak_notification_enabled", prefs.getBoolean("random_drills_notification_enabled", true)) ||
                prefs.getBoolean("vacation_mode", false)) return false
        }
        return true
    }
}
