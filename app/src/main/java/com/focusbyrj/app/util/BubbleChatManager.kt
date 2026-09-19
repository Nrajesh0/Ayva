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

package com.focusbyrj.app.util

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class PersistedChatMessage(
    val id: String,
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
) {
    /**
     * Determines if this message is a completed drill summary / solutions / claim XP card.
     */
    val isDrillSummaryCard: Boolean
        get() = isDrillSummary || id.startsWith("drill_summary_")

    /**
     * Determines if this message is an active, ongoing drill or quiz question.
     * Active questions must NEVER expire while the user is actively working on them.
     */
    val isActiveDrillOrQuizQuestion: Boolean
        get() = !isDrillSummary && (isArithmetic || id.startsWith("arithmetic_") || id.startsWith("drill_q_") || id.startsWith("quiz_q_"))

    /**
     * Determines if this message is a persistent/valuable learning or status card.
     */
    val isImportantCard: Boolean
        get() = isDrillSummaryCard || isAptitudeProfile || isStreakPrompt || 
                isDailyQuests || isMysteryBox || isMorningBrief || isEveningBrief || 
                isStreakFreezeSkipped || isVocabHub || isWelcome || isHabitsSummary || (isTaskSummary && !taskSummaryJson.isNullOrBlank()) ||
                id.startsWith("drill_summary_") || id.startsWith("morning_") || 
                id.startsWith("evening_") || id.startsWith("streak_prompt_") || id.startsWith("mystery_box_") || id.startsWith("vocab_hub_") || id.startsWith("welcome_") || id.startsWith("habits_")

    /**
     * Ephemeral messages are quick commands, casual talk, setting toggles, task additions,
     * and short-lived bot confirmations that now also enjoy a full 10-minute lifetime once viewed.
     */
    val isEphemeral: Boolean
        get() = !isImportantCard && !isArithmetic
}

object BubbleChatManager {
    private const val PREFS_NAME = "bubble_chat_prefs"
    private const val KEY_MESSAGES = "chat_messages_json"
    private const val KEY_UNREAD_COUNT = "unread_message_count"
    private const val KEY_LAST_ACTIVITY = "last_chat_activity_timestamp"
    const val CHAT_LIFETIME_MS = 10 * 60 * 1000L // 10 minutes for all opened/viewed chat items
    private const val INACTIVITY_TIMEOUT_MS = CHAT_LIFETIME_MS
    private const val EPHEMERAL_TIMEOUT_MS = CHAT_LIFETIME_MS

    const val ACTION_UNREAD_COUNT_CHANGED = "com.focusbyrj.app.UNREAD_COUNT_CHANGED"
    const val ACTION_MESSAGES_CHANGED = "com.focusbyrj.app.CHAT_MESSAGES_CHANGED"

    private val _unreadCountFlow = MutableStateFlow(0)
    val unreadCountFlow: StateFlow<Int> = _unreadCountFlow.asStateFlow()

    private val _messagesFlow = MutableStateFlow<List<PersistedChatMessage>>(emptyList())
    val messagesFlow: StateFlow<List<PersistedChatMessage>> = _messagesFlow.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _unreadCountFlow.value = prefs.getInt(KEY_UNREAD_COUNT, 0)
        _messagesFlow.value = getMessages(context)
    }

    fun getUnreadCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_UNREAD_COUNT, 0)
        _unreadCountFlow.value = count
        return count
    }

    fun setUnreadCount(context: Context, count: Int) {
        val safeCount = count.coerceAtLeast(0)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_UNREAD_COUNT, safeCount).apply()
        _unreadCountFlow.value = safeCount
        val intent = Intent(ACTION_UNREAD_COUNT_CHANGED).apply { setPackage(context.packageName) }
        context.sendBroadcast(intent)
    }

    fun incrementUnread(context: Context) {
        val current = getUnreadCount(context)
        setUnreadCount(context, current + 1)
    }

    fun clearUnread(context: Context) {
        setUnreadCount(context, 0)
    }

    fun updateLastActivityTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply()
    }

    fun isInactiveTimeout(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastActivity = prefs.getLong(KEY_LAST_ACTIVITY, 0L)
        if (lastActivity == 0L) return false
        return (System.currentTimeMillis() - lastActivity) > INACTIVITY_TIMEOUT_MS
    }

    /**
     * Marks all unviewed messages as viewed now when the user opens the chat window.
     */
    fun markAllAsViewed(context: Context) {
        val now = System.currentTimeMillis()
        val allMessages = getMessages(context)
        var modified = false
        val updated = allMessages.map { msg ->
            if (msg.firstViewedTimestamp == 0L) {
                modified = true
                msg.copy(firstViewedTimestamp = now)
            } else {
                msg
            }
        }
        if (modified) {
            saveMessages(context, updated, updateActivityTimestamp = true)
        }
    }

    /**
     * Periodically cleans up the chat stream:
     * - Welcome greeting card NEVER disappears on a timer.
     * - Active interactive drills and quizzes NEVER expire while the user is actively working on them.
     * - Unread / unviewed alerts and reminders NEVER expire before the user opens the chat window.
     * - Once opened and viewed, all chat history (queries, bot answers, task/habit addition confirmations,
     *   briefings, completed quiz/drill summaries, solutions, and claim XP cards) stays for exactly 10 minutes.
     * - Clearing chat messages has ZERO effect on saved tasks, habits, XP, drill stats, or routines.
     */
    fun checkAndClearIfInactive(context: Context, isDrillOrQuizActive: Boolean = false): Boolean {
        val now = System.currentTimeMillis()

        val allMessages = getMessages(context)
        if (allMessages.isEmpty()) return false

        val filtered = allMessages.filter { msg ->
            // 1. Welcome greeting card NEVER expires on a timer
            if (msg.isWelcome || msg.id.startsWith("welcome_") ||
                msg.text.contains("Hey! Ayva is on deck", ignoreCase = true) ||
                msg.text.contains("ready for action", ignoreCase = true) ||
                msg.text.contains("Ayva here!", ignoreCase = true) ||
                msg.text.contains("I'm Ayva", ignoreCase = true) ||
                msg.text.contains("anti-procrastination", ignoreCase = true)) {
                return@filter true
            }

            // 2. Active interactive drill or quiz questions NEVER expire while the user is actively working on them!
            if (isDrillOrQuizActive || msg.isActiveDrillOrQuizQuestion) {
                return@filter true
            }

            // 3. Unread / unviewed messages NEVER expire before the user opens the chat window to view them!
            if (msg.firstViewedTimestamp == 0L) {
                return@filter true
            }

            // 4. Completed drill summaries, solutions, claim XP cards, and all viewed queries/alerts stay for exactly 10 minutes after being viewed/completed!
            val timeSinceViewed = now - msg.firstViewedTimestamp
            timeSinceViewed < CHAT_LIFETIME_MS
        }

        if (filtered.size < allMessages.size) {
            if (filtered.isEmpty()) {
                clearMessages(context)
            } else {
                saveMessages(context, filtered, updateActivityTimestamp = false)
            }
            return true
        }

        return false
    }

    fun deleteMessage(context: Context, id: String) {
        val current = getMessages(context).toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            if (current.isEmpty()) {
                clearMessages(context)
            } else {
                saveMessages(context, current, updateActivityTimestamp = false)
            }
        }
    }

    fun getMessages(context: Context): List<PersistedChatMessage> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        val list = mutableListOf<PersistedChatMessage>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    PersistedChatMessage(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        text = obj.optString("text", ""),
                        isUser = obj.optBoolean("isUser", false),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        firstViewedTimestamp = obj.optLong("firstViewedTimestamp", 0L),
                        isArithmetic = obj.optBoolean("isArithmetic", false),
                        arithmeticJson = if (obj.has("arithmeticJson") && !obj.isNull("arithmeticJson")) obj.optString("arithmeticJson", null) else null,
                        isDrillSummary = obj.optBoolean("isDrillSummary", false),
                        drillSummaryJson = if (obj.has("drillSummaryJson") && !obj.isNull("drillSummaryJson")) obj.optString("drillSummaryJson", null) else null,
                        isAptitudeProfile = obj.optBoolean("isAptitudeProfile", false),
                        isStreakPrompt = obj.optBoolean("isStreakPrompt", false),
                        streakPromptJson = if (obj.has("streakPromptJson") && !obj.isNull("streakPromptJson")) obj.optString("streakPromptJson", null) else null,
                        isTaskSummary = obj.optBoolean("isTaskSummary", false),
                        taskSummaryJson = if (obj.has("taskSummaryJson") && !obj.isNull("taskSummaryJson")) obj.optString("taskSummaryJson", null) else null,
                        isTalkAction = obj.optBoolean("isTalkAction", false),
                        talkActionJson = if (obj.has("talkActionJson") && !obj.isNull("talkActionJson")) obj.optString("talkActionJson", null) else null,
                        pendingActionJson = if (obj.has("pendingActionJson") && !obj.isNull("pendingActionJson")) obj.optString("pendingActionJson", null) else null,
                        isDailyQuests = obj.optBoolean("isDailyQuests", false),
                        isMysteryBox = obj.optBoolean("isMysteryBox", false),
                        isMorningBrief = obj.optBoolean("isMorningBrief", false) || obj.optString("id", "").startsWith("morning_"),
                        isEveningBrief = obj.optBoolean("isEveningBrief", false) || obj.optString("id", "").startsWith("evening_"),
                        isStreakFreezeSkipped = obj.optBoolean("isStreakFreezeSkipped", false) || obj.optString("id", "").startsWith("angry_freeze_"),
                        isVocabBrief = obj.optBoolean("isVocabBrief", false),
                        isVocabHub = obj.optBoolean("isVocabHub", false) || obj.optString("id", "").startsWith("vocab_hub_"),
                        vocabJson = if (obj.has("vocabJson") && !obj.isNull("vocabJson")) obj.optString("vocabJson", null) else null,
                        isWelcome = obj.optBoolean("isWelcome", false) || obj.optString("id", "").startsWith("welcome_"),
                        isHabitsSummary = obj.optBoolean("isHabitsSummary", false) || obj.optString("id", "").startsWith("habits_"),
                        habitsSummaryJson = if (obj.has("habitsSummaryJson") && !obj.isNull("habitsSummaryJson")) obj.optString("habitsSummaryJson", null) else null
                    )
                )
            }
        } catch (_: Exception) {}
        _messagesFlow.value = list
        return list
    }

    fun saveMessages(context: Context, messages: List<PersistedChatMessage>, updateActivityTimestamp: Boolean = true) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val jsonArray = JSONArray()
            // Keep last 50 messages to keep storage small and snappy
            val trimmed = if (messages.size > 50) messages.takeLast(50) else messages
            trimmed.forEach { msg ->
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("text", msg.text)
                    put("isUser", msg.isUser)
                    put("timestamp", msg.timestamp)
                    put("firstViewedTimestamp", msg.firstViewedTimestamp)
                    put("isArithmetic", msg.isArithmetic)
                    put("arithmeticJson", msg.arithmeticJson)
                    put("isDrillSummary", msg.isDrillSummary)
                    put("drillSummaryJson", msg.drillSummaryJson)
                    put("isAptitudeProfile", msg.isAptitudeProfile)
                    put("isStreakPrompt", msg.isStreakPrompt)
                    put("streakPromptJson", msg.streakPromptJson)
                    put("isTaskSummary", msg.isTaskSummary)
                    put("taskSummaryJson", msg.taskSummaryJson)
                    put("isTalkAction", msg.isTalkAction)
                    put("talkActionJson", msg.talkActionJson)
                    put("pendingActionJson", msg.pendingActionJson)
                    put("isDailyQuests", msg.isDailyQuests)
                    put("isMysteryBox", msg.isMysteryBox)
                    put("isMorningBrief", msg.isMorningBrief)
                    put("isEveningBrief", msg.isEveningBrief)
                    put("isStreakFreezeSkipped", msg.isStreakFreezeSkipped)
                    put("isVocabBrief", msg.isVocabBrief)
                    put("isVocabHub", msg.isVocabHub)
                    put("vocabJson", msg.vocabJson)
                    put("isWelcome", msg.isWelcome || msg.id.startsWith("welcome_"))
                    put("isHabitsSummary", msg.isHabitsSummary)
                    put("habitsSummaryJson", msg.habitsSummaryJson)
                }
                jsonArray.put(obj)
            }
            val editor = prefs.edit().putString(KEY_MESSAGES, jsonArray.toString())
            if (updateActivityTimestamp) {
                editor.putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
            }
            editor.apply()
            _messagesFlow.value = trimmed
            val intent = Intent(ACTION_MESSAGES_CHANGED).apply { setPackage(context.packageName) }
            context.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    fun addMessage(context: Context, message: PersistedChatMessage, incrementBadge: Boolean = false, updateActivity: Boolean = !incrementBadge) {
        val current = getMessages(context).toMutableList()
        current.add(message)
        saveMessages(context, current, updateActivityTimestamp = updateActivity)
        if (incrementBadge) {
            incrementUnread(context)
        }
    }

    fun updateMessage(context: Context, updatedMessage: PersistedChatMessage) {
        val current = getMessages(context).toMutableList()
        val index = current.indexOfFirst { it.id == updatedMessage.id }
        if (index != -1) {
            current[index] = updatedMessage
            saveMessages(context, current)
        }
    }

    fun clearMessages(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_MESSAGES).putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply()
        _messagesFlow.value = emptyList()
        clearUnread(context)
        val intent = Intent(ACTION_MESSAGES_CHANGED).apply { setPackage(context.packageName) }
        context.sendBroadcast(intent)
    }
}
