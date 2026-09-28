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
import com.focusbyrj.app.data.chat.ActiveDrillStateEntity
import com.focusbyrj.app.data.chat.AyvaChatDatabase
import com.focusbyrj.app.data.chat.toEntity
import com.focusbyrj.app.data.chat.toPersistedChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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

    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

    private val _unreadCountFlow = MutableStateFlow(0)
    val unreadCountFlow: StateFlow<Int> = _unreadCountFlow.asStateFlow()

    private val _messagesFlow = MutableStateFlow<List<PersistedChatMessage>>(emptyList())
    val messagesFlow: StateFlow<List<PersistedChatMessage>> = _messagesFlow.asStateFlow()

    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _unreadCountFlow.value = prefs.getInt(KEY_UNREAD_COUNT, 0)

        val database = AyvaChatDatabase.getDatabase(appContext)

        scope.launch {
            try {
                val count = database.chatDao().getMessageCount()
                if (count == 0) {
                    // Check if legacy messages exist in SharedPreferences
                    val legacyJson = prefs.getString(KEY_MESSAGES, null)
                    if (!legacyJson.isNullOrBlank()) {
                        val legacyList = parseLegacyJson(legacyJson)
                        if (legacyList.isNotEmpty()) {
                            database.chatDao().insertMessages(legacyList.map { it.toEntity() })
                        }
                        prefs.edit().remove(KEY_MESSAGES).apply()
                    }
                }

                // Initial read
                val initial = database.chatDao().getAllMessagesSync().map { it.toPersistedChatMessage() }
                if (!isInitialized) {
                    _messagesFlow.value = initial
                    isInitialized = true
                }
            } catch (e: Exception) {
                android.util.Log.e("BubbleChatManager", "Error initializing chat DB", e)
                isInitialized = true
            }
        }
    }

    private fun JSONObject.optNullableString(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name) else null
    }

    private fun parseLegacyJson(jsonStr: String): List<PersistedChatMessage> {
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
                        arithmeticJson = obj.optNullableString("arithmeticJson"),
                        isDrillSummary = obj.optBoolean("isDrillSummary", false),
                        drillSummaryJson = obj.optNullableString("drillSummaryJson"),
                        isAptitudeProfile = obj.optBoolean("isAptitudeProfile", false),
                        isStreakPrompt = obj.optBoolean("isStreakPrompt", false),
                        streakPromptJson = obj.optNullableString("streakPromptJson"),
                        isTaskSummary = obj.optBoolean("isTaskSummary", false),
                        taskSummaryJson = obj.optNullableString("taskSummaryJson"),
                        isTalkAction = obj.optBoolean("isTalkAction", false),
                        talkActionJson = obj.optNullableString("talkActionJson"),
                        pendingActionJson = obj.optNullableString("pendingActionJson"),
                        isDailyQuests = obj.optBoolean("isDailyQuests", false),
                        isMysteryBox = obj.optBoolean("isMysteryBox", false),
                        isMorningBrief = obj.optBoolean("isMorningBrief", false) || obj.optString("id", "").startsWith("morning_"),
                        isEveningBrief = obj.optBoolean("isEveningBrief", false) || obj.optString("id", "").startsWith("evening_"),
                        isStreakFreezeSkipped = obj.optBoolean("isStreakFreezeSkipped", false) || obj.optString("id", "").startsWith("angry_freeze_"),
                        isVocabBrief = obj.optBoolean("isVocabBrief", false),
                        isVocabHub = obj.optBoolean("isVocabHub", false) || obj.optString("id", "").startsWith("vocab_hub_"),
                        vocabJson = obj.optNullableString("vocabJson"),
                        isWelcome = obj.optBoolean("isWelcome", false) || obj.optString("id", "").startsWith("welcome_"),
                        isHabitsSummary = obj.optBoolean("isHabitsSummary", false) || obj.optString("id", "").startsWith("habits_"),
                        habitsSummaryJson = obj.optNullableString("habitsSummaryJson")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
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
        val lastActivity = prefs.getSafeLong(KEY_LAST_ACTIVITY, 0L)
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
        val current = _messagesFlow.value.toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            _messagesFlow.value = current
            scope.launch {
                try {
                    val db = AyvaChatDatabase.getDatabase(context.applicationContext)
                    db.chatDao().deleteMessage(id)
                } catch (_: Exception) {}
            }
            val intent = Intent(ACTION_MESSAGES_CHANGED).apply { setPackage(context.packageName) }
            context.sendBroadcast(intent)
        }
    }

    fun getMessages(context: Context): List<PersistedChatMessage> {
        if (isInitialized) {
            return _messagesFlow.value
        }
        val cached = _messagesFlow.value
        if (cached.isNotEmpty()) return cached
        
        // If not initialized yet, query synchronously once to guarantee callers have messages
        return try {
            val db = AyvaChatDatabase.getDatabase(context.applicationContext)
            runBlocking(Dispatchers.IO) {
                val list = db.chatDao().getAllMessagesSync().map { it.toPersistedChatMessage() }
                _messagesFlow.value = list
                isInitialized = true
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMessages(context: Context, messages: List<PersistedChatMessage>, updateActivityTimestamp: Boolean = true) {
        val appContext = context.applicationContext
        val trimmed = if (messages.size > 50) messages.takeLast(50) else messages
        _messagesFlow.value = trimmed
        isInitialized = true

        if (updateActivityTimestamp) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply()
        }

        scope.launch {
            try {
                val db = AyvaChatDatabase.getDatabase(appContext)
                db.chatDao().syncAllMessages(trimmed.map { it.toEntity() })
            } catch (e: Exception) {
                android.util.Log.e("BubbleChatManager", "Failed to persist chat messages to Room", e)
            }
        }

        val intent = Intent(ACTION_MESSAGES_CHANGED).apply { setPackage(appContext.packageName) }
        appContext.sendBroadcast(intent)
    }

    fun addMessage(context: Context, message: PersistedChatMessage, incrementBadge: Boolean = false, updateActivity: Boolean = !incrementBadge) {
        val appContext = context.applicationContext
        val current = _messagesFlow.value.toMutableList()
        current.add(message)
        val trimmed = if (current.size > 50) current.takeLast(50) else current
        _messagesFlow.value = trimmed

        if (updateActivity) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply()
        }

        scope.launch {
            try {
                val db = AyvaChatDatabase.getDatabase(appContext)
                db.chatDao().insertMessage(message.toEntity())
                val count = db.chatDao().getMessageCount()
                if (count > 50) {
                    db.chatDao().deleteOldestMessages(count - 50)
                }
            } catch (e: Exception) {
                android.util.Log.e("BubbleChatManager", "Failed to add chat message to Room", e)
            }
        }

        if (incrementBadge) {
            incrementUnread(appContext)
        }

        val intent = Intent(ACTION_MESSAGES_CHANGED).apply { setPackage(appContext.packageName) }
        appContext.sendBroadcast(intent)
    }

    fun updateMessage(context: Context, updatedMessage: PersistedChatMessage) {
        val appContext = context.applicationContext
        val current = _messagesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == updatedMessage.id }
        if (index != -1) {
            current[index] = updatedMessage
            _messagesFlow.value = current
            scope.launch {
                try {
                    val db = AyvaChatDatabase.getDatabase(appContext)
                    db.chatDao().insertMessage(updatedMessage.toEntity())
                } catch (e: Exception) {
                    android.util.Log.e("BubbleChatManager", "Failed to update chat message in Room", e)
                }
            }
            val intent = Intent(ACTION_MESSAGES_CHANGED).apply { setPackage(appContext.packageName) }
            appContext.sendBroadcast(intent)
        }
    }

    fun clearMessages(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_MESSAGES).putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply()
        _messagesFlow.value = emptyList()
        isInitialized = true
        clearUnread(appContext)

        scope.launch {
            try {
                val db = AyvaChatDatabase.getDatabase(appContext)
                db.chatDao().clearAllMessages()
            } catch (e: Exception) {
                android.util.Log.e("BubbleChatManager", "Failed to clear chat messages from Room", e)
            }
        }

        val intent = Intent(ACTION_MESSAGES_CHANGED).apply { setPackage(appContext.packageName) }
        appContext.sendBroadcast(intent)
    }

    // --- In-Flight Drill State Persistence ---

    fun saveActiveDrill(context: Context, drill: ActiveDrillStateEntity) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val db = AyvaChatDatabase.getDatabase(appContext)
                db.chatDao().saveActiveDrill(drill)
            } catch (e: Exception) {
                android.util.Log.e("BubbleChatManager", "Failed to save active drill state", e)
            }
        }
    }

    suspend fun getLatestActiveDrill(context: Context): ActiveDrillStateEntity? = withContext(Dispatchers.IO) {
        try {
            val db = AyvaChatDatabase.getDatabase(context.applicationContext)
            db.chatDao().getLatestActiveDrill()
        } catch (_: Exception) {
            null
        }
    }

    fun clearActiveDrills(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val db = AyvaChatDatabase.getDatabase(appContext)
                db.chatDao().clearActiveDrills()
            } catch (e: Exception) {
                android.util.Log.e("BubbleChatManager", "Failed to clear active drill states", e)
            }
        }
    }
}
