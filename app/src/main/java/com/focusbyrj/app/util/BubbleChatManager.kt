/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val stateLock = Any()
    private val persistenceMutex = Mutex()
    private val beforeLoad = mutableListOf<(List<PersistedChatMessage>) -> List<PersistedChatMessage>>()
    private var revision = 0L
    private var persistedRevision = -1L
    @Volatile internal var generation = 0L
        private set
    private val _transcriptGeneration = MutableStateFlow(0L)
    val transcriptGeneration: StateFlow<Long> = _transcriptGeneration.asStateFlow()
    @Volatile private var isInitialized = false
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private val initLatch = java.util.concurrent.atomic.AtomicBoolean(false)

    fun init(context: Context) {
        if (!initLatch.compareAndSet(false, true)) return
        synchronized(stateLock) {
            _unreadCountFlow.value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_UNREAD_COUNT, 0)
        }
        persist(context)
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

    /** Concurrent edits are a delta against what that editor actually saw. */
    internal fun mergeChanges(
        current: List<PersistedChatMessage>,
        before: List<PersistedChatMessage>,
        after: List<PersistedChatMessage>
    ): List<PersistedChatMessage> {
        val previous = before.associateBy { it.id }
        val incoming = after.associateBy { it.id }
        val result = current.associateByTo(linkedMapOf()) { it.id }
        (previous.keys - incoming.keys).forEach(result::remove)
        after.forEach { message ->
            if (previous[message.id] != message) result[message.id] = message
        }
        return result.values.toList().takeLast(50)
    }

    private fun trim(messages: List<PersistedChatMessage>) = messages.distinctBy { it.id }.takeLast(50)

    // All Room reads/writes share this mutex, including the initial read. Memory
    // mutations use stateLock and are replayed over the initial DB snapshot.
    private suspend fun loadLocked(context: Context) {
        if (isInitialized) return
        val db = AyvaChatDatabase.getDatabase(context.applicationContext)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (db.chatDao().getMessageCount() == 0) {
            prefs.getString(KEY_MESSAGES, null)?.takeIf { it.isNotBlank() }?.let { json ->
                val legacy = parseLegacyJson(json)
                if (legacy.isNotEmpty()) db.chatDao().insertMessages(legacy.map { it.toEntity() })
                prefs.edit().remove(KEY_MESSAGES).apply()
            }
        }
        val original = db.chatDao().getAllMessagesSync().map { it.toPersistedChatMessage() }
        synchronized(stateLock) {
            val merged = trim(beforeLoad.fold(original) { list, change -> change(list) })
            if (beforeLoad.isEmpty() && merged == original) persistedRevision = revision
            beforeLoad.clear()
            _messagesFlow.value = merged
            isInitialized = true
            _loaded.value = true
        }
    }

    private suspend fun persistLocked(context: Context) {
        val snapshot = synchronized(stateLock) {
            if (persistedRevision == revision) null else revision to _messagesFlow.value
        } ?: return
        AyvaChatDatabase.getDatabase(context.applicationContext).chatDao()
            .syncAllMessages(snapshot.second.map { it.toEntity() })
        synchronized(stateLock) { persistedRevision = snapshot.first }
    }

    private fun persist(context: Context) {
        val app = context.applicationContext
        scope.launch {
            repeat(3) { attempt ->
                // Suspend during maintenance rather than poll or let a write escape
                // the restore interval. Retain dirty memory if a disk error persists.
                com.focusbyrj.app.util.backup.MaintenanceGate.busy.first { !it }
                try {
                    persistenceMutex.withLock { loadLocked(app); persistLocked(app) }
                    return@launch
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    if (attempt == 2) {
                        android.util.Log.e("BubbleChatManager", "Chat changes remain pending for retry", error)
                        if (!isInitialized) initLatch.set(false)
                    } else delay(250L * (attempt + 1))
                }
            }
        }
    }

    suspend fun awaitInitialized(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            com.focusbyrj.app.util.backup.MaintenanceGate.busy.first { !it }
            persistenceMutex.withLock { loadLocked(context.applicationContext); persistLocked(context.applicationContext) }
            true
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            android.util.Log.w("BubbleChatManager", "Chat is not ready yet", error)
            initLatch.set(false)
            false
        }
    }

    /** Called inside restore's maintenance interval, before queued UI writes resume. */
    internal suspend fun reloadAfterRestore(context: Context) = withContext(Dispatchers.IO) {
        persistenceMutex.withLock {
            synchronized(stateLock) {
                beforeLoad.clear()
                generation++
                _transcriptGeneration.value = generation
                revision++
                persistedRevision = -1L
                isInitialized = false
                _loaded.value = false
                _messagesFlow.value = emptyList()
                initLatch.set(true)
            }
            try { loadLocked(context.applicationContext) }
            catch (error: Exception) { initLatch.set(false); throw error }
            val unread = _messagesFlow.value.count { !it.isUser && it.firstViewedTimestamp == 0L }
            changeUnread(context) { unread }
        }
        context.sendBroadcast(Intent(ACTION_MESSAGES_CHANGED).setPackage(context.packageName))
        context.sendBroadcast(Intent(ACTION_UNREAD_COUNT_CHANGED).setPackage(context.packageName))
    }

    private fun mutate(context: Context, updateActivity: Boolean = false, expectedGeneration: Long? = null,
                       change: (List<PersistedChatMessage>) -> List<PersistedChatMessage>) {
        init(context)
        synchronized(stateLock) {
            if (expectedGeneration != null && expectedGeneration != generation) return
            if (!isInitialized) beforeLoad.add(change)
            val next = trim(change(_messagesFlow.value))
            if (next != _messagesFlow.value || !isInitialized) {
                _messagesFlow.value = next
                revision++
            }
        }
        if (updateActivity) updateLastActivityTime(context)
        persist(context)
        context.sendBroadcast(Intent(ACTION_MESSAGES_CHANGED).setPackage(context.packageName))
    }

    fun getUnreadCount(context: Context): Int = synchronized(stateLock) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_UNREAD_COUNT, 0)
            .coerceAtLeast(0).also { _unreadCountFlow.value = it }
    }

    private fun changeUnread(context: Context, change: (Int) -> Int) {
        synchronized(stateLock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val next = change(prefs.getInt(KEY_UNREAD_COUNT, 0)).coerceAtLeast(0)
            prefs.edit().putInt(KEY_UNREAD_COUNT, next).apply()
            _unreadCountFlow.value = next
        }
        context.sendBroadcast(Intent(ACTION_UNREAD_COUNT_CHANGED).setPackage(context.packageName))
    }

    fun setUnreadCount(context: Context, count: Int) = changeUnread(context) { count }
    fun incrementUnread(context: Context) = changeUnread(context) { if (it == Int.MAX_VALUE) it else it + 1 }
    fun clearUnread(context: Context) = setUnreadCount(context, 0)

    fun updateLastActivityTime(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply()
    }

    fun isInactiveTimeout(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getSafeLong(KEY_LAST_ACTIVITY, 0L)
        return last != 0L && System.currentTimeMillis() - last > INACTIVITY_TIMEOUT_MS
    }

    fun markAllAsViewed(context: Context) {
        val now = System.currentTimeMillis()
        mutate(context, updateActivity = true) { list ->
            list.map { if (it.firstViewedTimestamp == 0L) it.copy(firstViewedTimestamp = now) else it }
        }
    }

    /** Unviewed items and active questions keep the existing ten-minute policy. */
    fun checkAndClearIfInactive(context: Context, isDrillOrQuizActive: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val before = _messagesFlow.value
        mutate(context) { list ->
            list.filter { msg ->
                msg.isWelcome || msg.id.startsWith("welcome_") ||
                    msg.text.contains("Hey! Ayva is on deck", true) || msg.text.contains("ready for action", true) ||
                    msg.text.contains("Ayva here!", true) || msg.text.contains("I'm Ayva", true) ||
                    msg.text.contains("anti-procrastination", true) || isDrillOrQuizActive ||
                    msg.isActiveDrillOrQuizQuestion || msg.firstViewedTimestamp == 0L ||
                    now - msg.firstViewedTimestamp < CHAT_LIFETIME_MS
            }
        }
        return _messagesFlow.value.size < before.size
    }

    fun getMessages(context: Context): List<PersistedChatMessage> {
        init(context)
        return _messagesFlow.value
    }

    /** Authoritative replacement; UI edits should use applyMessageChanges instead. */
    fun saveMessages(context: Context, messages: List<PersistedChatMessage>, updateActivityTimestamp: Boolean = true) {
        val snapshot = messages.toList()
        mutate(context, updateActivityTimestamp) { snapshot }
    }

    fun applyMessageChanges(context: Context, before: List<PersistedChatMessage>, after: List<PersistedChatMessage>,
                            updateActivityTimestamp: Boolean = true, expectedGeneration: Long? = null) {
        val prior = before.toList()
        val next = after.toList()
        mutate(context, updateActivityTimestamp, expectedGeneration) { current -> mergeChanges(current, prior, next) }
    }

    fun ensureWelcomeIfEmpty(context: Context, welcome: PersistedChatMessage) = mutate(context) { list ->
        if (!AssistantUpdateDelivery.allows(context, welcome)) return@mutate list
        if (list.isEmpty()) listOf(welcome) else list
    }

    fun addMessage(context: Context, message: PersistedChatMessage, incrementBadge: Boolean = false,
                   updateActivity: Boolean = !incrementBadge, expectedGeneration: Long? = null): Boolean {
        synchronized(stateLock) {
            if (expectedGeneration != null && expectedGeneration != generation) return false
            if (incrementBadge && !AssistantUpdateDelivery.allows(context, message)) return false
            mutate(context, updateActivity) { list -> list.filterNot { it.id == message.id } + message }
            if (incrementBadge) incrementUnread(context)
        }
        return true
    }

    fun updateMessage(context: Context, updatedMessage: PersistedChatMessage) = mutate(context) { list ->
        list.map { if (it.id == updatedMessage.id) updatedMessage else it }
    }

    fun deleteMessage(context: Context, id: String) = mutate(context) { list -> list.filterNot { it.id == id } }

    fun clearMessages(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_MESSAGES).apply()
        mutate(context, updateActivity = true) { emptyList() }
        clearUnread(context)
    }

    // --- In-Flight Drill State Persistence ---

    fun saveActiveDrill(context: Context, drill: ActiveDrillStateEntity) {
        val appContext = context.applicationContext
        val expectedGeneration = generation
        scope.launch {
            try {
                com.focusbyrj.app.util.backup.RestoreJournal.consistent(appContext) {
                    if (expectedGeneration == generation) AyvaChatDatabase.getDatabase(appContext).chatDao().saveActiveDrill(drill)
                }
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
        val expectedGeneration = generation
        scope.launch {
            try {
                com.focusbyrj.app.util.backup.RestoreJournal.consistent(appContext) {
                    if (expectedGeneration == generation) AyvaChatDatabase.getDatabase(appContext).chatDao().clearActiveDrills()
                }
            } catch (e: Exception) {
                android.util.Log.e("BubbleChatManager", "Failed to clear active drill states", e)
            }
        }
    }
}
