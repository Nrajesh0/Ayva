package com.focusbyrj.app.util

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.FocusSchedule
import com.focusbyrj.app.widget.TodoWidgetProvider

/**
 * AyvaTalkEngine: Atomic, context-aware, state-diagnostic, deep-link capable knowledge & action engine.
 * 
 * Features:
 * 1. Live Context & State Diagnostics: Inspects live Android permissions and settings directly.
 * 2. Interactive Direct Deep-Linking & Inline Action Chips: Generates action chips that directly launch screens or change settings.
 * 3. Interactive Follow-Up & Session Context: Resolves pronouns ('it', 'change it', 'why') based on previous query context.
 * 4. Fuzzy Semantic Search & Typo Tolerance: Levenshtein distance matching for misspelled commands and settings.
 * 6. Inline Setting Adjustment Toggles: Allows fast inline updates right from chat (e.g. setting reminder interval or toggle switches).
 */
object AyvaTalkEngine {

    // In-memory conversation context for pronouns / follow-ups
    @Volatile
    var lastQueriedTopicId: String? = null

    // Sliding window conversational memory (stores last 12 queries & responses for follow-up support)
    data class ChatHistoryItem(val query: String, val topicId: String?, val timestamp: Long = System.currentTimeMillis())
    private val conversationHistory = java.util.concurrent.ConcurrentLinkedDeque<ChatHistoryItem>()

    fun recordTurn(query: String, topicId: String?) {
        conversationHistory.addLast(ChatHistoryItem(query, topicId))
        while (conversationHistory.size > 12) {
            conversationHistory.pollFirst()
        }
    }

    fun getRecentHistory(): List<ChatHistoryItem> = conversationHistory.toList()

    // --- CONVERSATION SESSION STATE (SLOT-FILLING) ---
    data class ConversationSession(
        val expectedPrefKey: String,
        val expectedPrefType: String,
        val topicTitle: String
    )
    
    @Volatile
    var activeSession: ConversationSession? = null

    // --- NLP SEMANTIC DICTIONARY & STEMMING ---
    private val synonyms = mapOf(
        "alarm" to listOf("chime", "beep", "ring", "alert", "notification", "remind", "sound"),
        "delete" to listOf("remove", "uninstall", "clear", "trash", "erase"),
        "stop" to listOf("block", "prevent", "quit", "disable", "turn off", "halt"),
        "start" to listOf("enable", "turn on", "activate", "begin", "launch"),
        "time" to listOf("duration", "timer", "interval", "countdown", "length", "minutes", "seconds"),
        "app" to listOf("application", "game"),
        "change" to listOf("modify", "edit", "set", "adjust", "update", "switch")
    )

    private fun stem(word: String): String {
        var w = word.lowercase().trim()
        if (w.endsWith("ing") && w.length > 4) return w.removeSuffix("ing")
        if (w.endsWith("ed") && w.length > 3) return w.removeSuffix("ed")
        if (w.endsWith("s") && w.length > 3 && !w.endsWith("ss")) return w.removeSuffix("s")
        return w
    }

    private fun expandTokens(query: String): List<String> {
        val words = query.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }
        val expanded = mutableListOf<String>()
        for (w in words) {
            expanded.add(w)
            val stemmed = stem(w)
            expanded.add(stemmed)
            synonyms.forEach { (key, list) ->
                if (key == w || key == stemmed || list.contains(w) || list.contains(stemmed)) {
                    expanded.add(key)
                    expanded.addAll(list)
                }
            }
        }
        return expanded.distinct()
    }

    data class TalkResponse(
        val formattedText: String,
        val actions: List<TalkAction> = emptyList(),
        val topicId: String? = null,
        val jsonPayload: String? = null,
        val cliCommandRedirect: String? = null
    )

    data class SpecificTopic(
        val id: String,
        val title: String,
        val emoji: String,
        val directExplanation: String,
        val whereToGo: String,
        val whatToClick: List<String>,
        val toggleOrRange: String? = null,
        val whyOrTip: String? = null,
        val keywords: List<String>,
        val route: String? = null,
        val systemSettingAction: String? = null,
        val isPackageSettingUri: Boolean = false,
        val prefKey: String? = null,
        val prefType: String? = null,
        val diagnosticKey: String? = null
    ) {
        fun formatResponse(context: Context? = null): String {
            val sb = StringBuilder()
            sb.append("*${title}*\n\n")

            if (context != null && diagnosticKey != null) {
                val diagnostic = resolveDiagnostic(diagnosticKey, context)
                if (!diagnostic.isNullOrBlank()) {
                    sb.append("*Current Live Status:*\n").append(diagnostic).append("\n\n")
                }
            }

            sb.append(directExplanation).append("\n\n")

            if (whereToGo != null) {
                sb.append("*Where to go:*\n")
                sb.append("→ ").append(whereToGo).append("\n\n")
            }

            if (!whatToClick.isNullOrEmpty()) {
                whatToClick.forEach { step ->
                    sb.append("• ").append(step).append("\n")
                }
                sb.append("\n")
            }

            if (!toggleOrRange.isNullOrBlank()) {
                sb.append("*Setting Range:*\n")
                sb.append(toggleOrRange).append("\n\n")
            }

            if (!whyOrTip.isNullOrBlank()) {
                sb.append("*Context:*\n")
                sb.append(whyOrTip).append("\n")
            }

            return sb.toString().trimEnd()
        }

        fun getActions(context: Context?): List<TalkAction> {
            val list = mutableListOf<TalkAction>()

            if (route != null) {
                val routeName = when (route) {
                    "preferences_hub" -> "Preferences Hub ⚙️"
                    "settings" -> "App Settings 🎨"
                    "security" -> "Security 🔒"
                    "dashboard" -> "Focus 🎯"
                    "todos" -> "Tasks 📋"
                    "schedules" -> "Routines 📅"
                    "time" -> "Time Stats 📊"
                    "account" -> "Account & Profile 👤"
                    "bubble_settings" -> "Bubble Settings 🫧"
                    "subscription" -> "Pro Subscription ⭐"
                    "empty" -> "Notes 📝"
                    else -> "Open Screen 🚀"
                }
                list.add(TalkAction.NavigateAppScreen(route, routeName, "🚀"))
            }

            if (systemSettingAction != null) {
                val label = when (systemSettingAction) {
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION -> "Overlay ⚙️"
                    Settings.ACTION_USAGE_ACCESS_SETTINGS -> "Usage Access ⚙️"
                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> "Battery ⚡"
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS -> "Notifications 🔔"
                    else -> "Android Settings ⚙️"
                }
                list.add(TalkAction.OpenSystemSetting(systemSettingAction, label, "⚙️", isPackageSettingUri))
            }

            if (context != null && diagnosticKey != null) {
                list.addAll(resolveCustomActions(diagnosticKey, context))
            }

            return list
        }
    }

    private fun resolveDiagnostic(key: String, ctx: Context): String? {
        val prefs = ctx.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        return when (key) {
            "vacation_mode" -> {
                val isVacation = AptitudeManager.isVacationMode(ctx)
                val currentStreak = AptitudeManager.profileFlow.value.currentStreak
                if (isVacation) {
                    "• Status: *ACTIVE (Streak Frozen ❄️)*\n• Frozen Streak: *$currentStreak days*\n• Practice Alerts: *PAUSED 🔕*"
                } else {
                    "• Status: *DISABLED (Normal Daily Tests ⚡)*\n• Current Streak: *$currentStreak days*\n• Practice Alerts: *ACTIVE 🔔*"
                }
            }
            "persistent_reminders" -> {
                val current = prefs.getInt("persistent_reminder_interval", 15)
                val display = if (current >= 60) "${current / 60}h" else "${current}m"
                "• Your interval is currently set to: *$display*"
            }
            "soft_mode_wait_timer" -> {
                val current = prefs.getInt("soft_lock_duration", 10)
                "• Current wait delay before unblocking: *${current}s*"
            }
            "soft_mode_relief_duration" -> {
                val current = prefs.getInt("soft_unlock_duration", 5)
                "• Current temporary unlock window: *${current} minutes*"
            }
            "routine_notifications" -> {
                val enabled = prefs.getBoolean("routine_notifications", true)
                val stateStr = if (enabled) "ENABLED (ON)" else "DISABLED (OFF)"
                "• Routine alerts are currently: *$stateStr*"
            }
            "task_notification_style" -> {
                val style = prefs.getString("task_notification_style", "Both") ?: "Both"
                "• Current display style: *$style*"
            }
            else -> null
        }
    }

    private fun resolveCustomActions(key: String, ctx: Context): List<TalkAction> {
        return when (key) {
            "vacation_mode" -> {
                val isVacation = AptitudeManager.isVacationMode(ctx)
                if (isVacation) {
                    listOf(
                        TalkAction.DirectPrefUpdate("vacation_mode", "boolean", "false", "Turn Off", "Disable ⚡", "⚡")
                    )
                } else {
                    listOf(
                        TalkAction.DirectPrefUpdate("vacation_mode", "boolean", "true", "Turn On", "Enable 🏖️", "❄️")
                    )
                }
            }
            "persistent_reminders" -> listOf(
                TalkAction.DirectPrefUpdate("persistent_reminder_interval", "int", "5", "5m", "5m", "⚡"),
                TalkAction.DirectPrefUpdate("persistent_reminder_interval", "int", "15", "15m", "15m", "⚡"),
                TalkAction.DirectPrefUpdate("persistent_reminder_interval", "int", "30", "30m", "30m", "⚡"),
                TalkAction.DirectPrefUpdate("persistent_reminder_interval", "int", "60", "1h", "1h", "⚡")
            )
            "soft_mode_wait_timer" -> listOf(
                TalkAction.DirectPrefUpdate("soft_lock_duration", "int", "5", "5s", "5s Delay", "⏱️"),
                TalkAction.DirectPrefUpdate("soft_lock_duration", "int", "10", "10s", "10s Delay", "⏱️"),
                TalkAction.DirectPrefUpdate("soft_lock_duration", "int", "20", "20s", "20s Delay", "⏱️")
            )
            "soft_mode_relief_duration" -> listOf(
                TalkAction.DirectPrefUpdate("soft_unlock_duration", "int", "2", "2m", "2m Relief", "⏱️"),
                TalkAction.DirectPrefUpdate("soft_unlock_duration", "int", "5", "5m", "5m Relief", "⏱️"),
                TalkAction.DirectPrefUpdate("soft_unlock_duration", "int", "10", "10m", "10m Relief", "⏱️")
            )
            else -> emptyList()
        }
    }

    // --- LEVENSHTEIN FUZZY DISTANCE HELPER ---
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun isFuzzyMatchWord(word: String, target: String): Boolean {
        if (word == target) return true
        if (word.length < 3 || target.length < 3) return false
        val maxDist = if (target.length > 5) 2 else 1
        return levenshteinDistance(word, target) <= maxDist
    }

    // --- ATOMIC KNOWLEDGE BASE (JSON ASSET-DRIVEN) ---
    @Volatile
    private var _topicsDatabase: List<SpecificTopic>? = null

    fun warmUp(context: Context) {
        if (_topicsDatabase == null) {
            getTopicsDatabase(context)
        }
    }

    private fun getTopicsDatabase(context: Context?): List<SpecificTopic> {
        if (_topicsDatabase != null) return _topicsDatabase!!
        if (context == null) return emptyList()

        try {
            val jsonString = context.assets.open("ayva_knowledge_base.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<SpecificTopic>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id")
                val title = obj.optString("title")
                val emoji = obj.optString("emoji")
                val directExplanation = obj.optString("directExplanation")
                val whereToGo = obj.optString("whereToGo")
                
                val whatToClickArray = obj.optJSONArray("whatToClick")
                val whatToClick = mutableListOf<String>()
                if (whatToClickArray != null) {
                    for (j in 0 until whatToClickArray.length()) {
                        whatToClick.add(whatToClickArray.getString(j))
                    }
                }
                
                val keywordsArray = obj.optJSONArray("keywords")
                val keywords = mutableListOf<String>()
                if (keywordsArray != null) {
                    for (j in 0 until keywordsArray.length()) {
                        keywords.add(keywordsArray.getString(j))
                    }
                }

                val toggleOrRange = if (obj.has("toggleOrRange")) obj.getString("toggleOrRange") else null
                val whyOrTip = if (obj.has("whyOrTip")) obj.getString("whyOrTip") else null
                val route = if (obj.has("route")) obj.getString("route") else null
                val systemSettingAction = if (obj.has("systemSettingAction")) obj.getString("systemSettingAction") else null
                val isPackageSettingUri = obj.optBoolean("isPackageSettingUri", false)
                val prefKey = if (obj.has("prefKey")) obj.getString("prefKey") else null
                val prefType = if (obj.has("prefType")) obj.getString("prefType") else null
                val diagnosticKey = if (obj.has("diagnosticKey")) obj.getString("diagnosticKey") else null

                list.add(
                    SpecificTopic(
                        id = id,
                        title = title,
                        emoji = emoji,
                        directExplanation = directExplanation,
                        whereToGo = whereToGo,
                        whatToClick = whatToClick,
                        toggleOrRange = toggleOrRange,
                        whyOrTip = whyOrTip,
                        keywords = keywords,
                        route = route,
                        systemSettingAction = systemSettingAction,
                        isPackageSettingUri = isPackageSettingUri,
                        prefKey = prefKey,
                        prefType = prefType,
                        diagnosticKey = diagnosticKey
                    )
                )
            }
            _topicsDatabase = list
            return list
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }
    /**
     * Answers a /talk query with Context & State awareness, follow-ups, and interactive actions.
     */
    suspend fun answerTalkQueryWithActions(query: String, context: Context? = null): TalkResponse {
        val trimmed = query.trim()
        val lower = trimmed.lowercase()

        // Clean query prefix if user typed /talk, /ask, /guide, /how, /faq, /help, etc.
        val cleanQuery = lower
            .removePrefix("/talk").trim()
            .removePrefix("/ask").trim()
            .removePrefix("/guide").trim()
            .removePrefix("/how").trim()
            .removePrefix("/faq").trim()
            .removePrefix("/help").trim()
            .removePrefix("/info").trim()
            .removePrefix("/").trim()
            .replace("?", "")
            .replace("!", "")
            .replace(",", "")
            .replace("\"", "")
            .replace("'", "")
            .trim()

        val isQuestionQuery = lower.startsWith("how") || lower.startsWith("why") ||
            lower.startsWith("what") || lower.startsWith("where") ||
            lower.startsWith("who") || lower.startsWith("can") ||
            lower.startsWith("is ") || lower.startsWith("are ") ||
            lower.startsWith("explain") || lower.startsWith("tell me") ||
            lower.startsWith("which") || lower.startsWith("does") ||
            lower.startsWith("do ") || lower.contains("?") ||
            lower.contains("how to") || lower.contains("how do") ||
            lower.contains("why is") || lower.contains("what is")

        val isSettingsLocationQuery = cleanQuery in listOf(
            "where is settings", "where are settings", "where is preferences", "where did the menu go",
            "where is the menu", "where did the sidebar go", "where is sidebar", "where is the sidebar",
            "where is drawer", "how to open settings", "how to access settings", "settings location",
            "open settings", "preferences hub", "settings hub", "preferences", "menu", "sidebar", "side bar", "drawer"
        ) || (cleanQuery.contains("where") && (cleanQuery.contains("setting") || cleanQuery.contains("menu") || cleanQuery.contains("sidebar") || cleanQuery.contains("drawer") || cleanQuery.contains("preference")))

        if (isSettingsLocationQuery) {
            val settingsActions = listOf(
                TalkAction.NavigateAppScreen("preferences_hub", "Preferences Hub ⚙️", "⚙️"),
                TalkAction.NavigateAppScreen("settings", "App Settings 🎨", "🎨"),
                TalkAction.NavigateAppScreen("security", "Security & Permissions 🔒", "🔒"),
                TalkAction.NavigateAppScreen("bubble_settings", "Bubble Settings 🫧", "🫧"),
                TalkAction.NavigateAppScreen("account", "Account & Profile 👤", "👤")
            )
            val json = serializeActionsJson("preferences_hub_navigation", settingsActions)
            return TalkResponse(
                formattedText = "⚙️ **Preferences & Settings Hub**\n\nThe settings menu has moved to the **Preferences & Settings Hub**!\n\n**How to access it:**\n1. Look at the top-right corner of the top header bar on the home screen.\n2. Tap your **Profile Avatar Ring**.\n3. This opens your centralized hub for:\n• **Security & Permissions** — Permissions, Tamper Defense, Anti-Screenshot & Backups\n• **App Settings** — Themes, Accent Colors, Heatmaps, Soft Delays & Reminders\n• **Bubble Settings** — Floating Ayva Bubble & Video Call Auto-Hide\n• **Account & Profile** — Avatar customizer, XP level, Gold economy & Trophies\n• **Subscription** — Lifetime Pro & Cloud Sync\n• **Setup Guide** — Step-by-step permissions onboarding\n\n_Tap any quick button below to jump straight to the screen!_",
                actions = settingsActions,
                topicId = "preferences_hub_navigation",
                jsonPayload = json
            )
        }

        val isGeneralHelp = cleanQuery.isEmpty() || cleanQuery in listOf(
            "help", "commands", "guide", "info", "what can you do", "features", "options", "talk"
        )
        if (isGeneralHelp) {
            val defaultActions = listOf(
                TalkAction.AskQuery("/status", "⚡ Focus Status"),
                TalkAction.AskQuery("/focus 25", "⏱️ 25m Focus Sprint"),
                TalkAction.AskQuery("/breathe", "🫁 Guided Breathing"),
                TalkAction.AskQuery("/drill", "⚡ Math Drill"),
                TalkAction.AskQuery("push overdue to tomorrow", "🧹 Clean Overdue"),
                TalkAction.NavigateAppScreen("preferences_hub", "Preferences Hub ⚙️", "⚙️"),
                TalkAction.AskQuery("vacation mode", "🏖️ Vacation Mode"),
                TalkAction.AskQuery("/summary", "📊 Daily Summary")
            )
            val json = serializeActionsJson("help", defaultActions)
            return TalkResponse(
                formattedText = "💬 **Ayva Assistant** is ready to help!\n\nYou can ask me questions about any feature, diagnose settings, or manage your focus:\n\n• *\"/status\"* (Comprehensive focus posture briefing)\n• *\"/focus 25\"* or *\"start pomodoro\"* (Deep work countdown sprint)\n• *\"/breathe\"* (Box breathing & physiological sigh protocol)\n• *\"/drill\"* (Neural warm-up math arithmetic)\n• *\"push overdue to tomorrow\"* or *\"clean overdue tasks\"* (Smart overdue triage)\n• *\"where is settings?\"* (Preferences Hub location & guide)\n• *\"vacation mode on\"* (freeze streak & pause alerts)\n• *\"why are apps not blocking?\"* (troubleshooting permissions)",
                actions = defaultActions,
                topicId = "help",
                jsonPayload = json
            )
        }

        // --- GREETINGS & CASUAL INTERACTION ---
        val isGreeting = cleanQuery in listOf(
            "hi", "hello", "hey", "hey ayva", "hello ayva", "hi ayva", "good morning", "good afternoon", "good evening", "howdy", "sup"
        )
        if (isGreeting) {
            val actions = listOf(
                TalkAction.NavigateAppScreen("preferences_hub", "Preferences Hub ⚙️", "⚙️"),
                TalkAction.AskQuery("/summary", "📊 Today's Summary"),
                TalkAction.AskQuery("streak", "⚡ Streak Status"),
                TalkAction.AskQuery("/drill", "⚡ Math Drill"),
                TalkAction.AskQuery("vacation mode", "🏖️ Vacation Mode")
            )
            return TalkResponse(
                formattedText = "👋 **Hello!** I'm Ayva, your intelligent focus companion.\n\nHow can I support your productivity today? You can check your daily summary, customize preferences, test your mental agility with a math drill, or ask me about any app settings.",
                actions = actions,
                topicId = "greeting",
                jsonPayload = serializeActionsJson("greeting", actions)
            )
        }

        val isThankYou = cleanQuery in listOf("thank you", "thanks", "thx", "appreciate it", "great thanks", "thanks ayva")
        if (isThankYou) {
            return TalkResponse("😊 You're very welcome! Stay focused and let me know whenever you need assistance.")
        }

        val isWhoAreYou = cleanQuery in listOf(
            "who are you", "what are you", "who made you", "who created you", "tell me about yourself", "about ayva", "about focusbyrj"
        )
        if (isWhoAreYou) {
            val actions = listOf(
                TalkAction.NavigateAppScreen("preferences_hub", "Preferences Hub ⚙️", "⚙️"),
                TalkAction.AskQuery("/summary", "📊 Daily Summary"),
                TalkAction.AskQuery("features", "✨ Features Guide")
            )
            return TalkResponse(
                formattedText = "🤖 **I am Ayva**, your on-device AI productivity and focus assistant for **FocusByRj**.\n\n• **Preferences Hub**: Manage all settings, security, and bubble controls from the top header avatar.\n• **App Blocker Engine**: Direct window overlay protection for Soft & Strict modes.\n• **Task Manager**: Smart natural language due dates with persistent alarms.\n• **Cognitive Agility**: Daily arithmetic drills and streak tracking.\n• **Floating Bubble**: Omnipresent task radar that intelligently auto-hides during video calls and landscape gaming.",
                actions = actions,
                topicId = "about",
                jsonPayload = serializeActionsJson("about", actions)
            )
        }

        if (query.trim().startsWith("/") || isGeneralHelp || isQuestionQuery || isGreeting) {
            activeSession = null
        }

        // --- 0. ACTIVE SESSION (SLOT-FILLING) INTERCEPT ---
        val currentSession = activeSession
        if (currentSession != null) {
            val isCancel = cleanQuery.contains("cancel") || cleanQuery.contains("stop") || cleanQuery.contains("nevermind") || cleanQuery.startsWith("/")
            if (isCancel) {
                activeSession = null
                if (cleanQuery.startsWith("/")) {
                    // Let the command proceed normally
                } else {
                    return TalkResponse("✅ Cancelled updating ${currentSession.topicTitle}.")
                }
            } else if (currentSession.expectedPrefType == "int") {
                val inputNum = cleanQuery.replace(Regex("[^0-9]"), "").toIntOrNull()
                if (inputNum != null && context != null) {
                    context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
                        .edit().putInt(currentSession.expectedPrefKey, inputNum).apply()
                    activeSession = null
                    return TalkResponse("✅ Updated ${currentSession.topicTitle} to $inputNum.")
                } else {
                    // If not a number, clear activeSession so user isn't stuck
                    activeSession = null
                }
            } else if (currentSession.expectedPrefType == "boolean") {
                val isTrue = cleanQuery.contains("yes") || cleanQuery.contains("on") || cleanQuery.contains("true")
                val isFalse = cleanQuery.contains("no") || cleanQuery.contains("off") || cleanQuery.contains("false")
                if (isTrue || isFalse) {
                    val finalVal = isTrue
                    context?.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
                        ?.edit()?.putBoolean(currentSession.expectedPrefKey, finalVal)?.apply()
                    activeSession = null
                    return TalkResponse("✅ ${currentSession.topicTitle} turned ${if (finalVal) "ON" else "OFF"}.")
                } else {
                    activeSession = null
                }
            } else {
                activeSession = null
            }
        }

        // --- 0.2 PERMISSIONS & DIAGNOSTIC SELF-HEALING CHECK ---
        val isPermissionOrDiagnosticQuery = cleanQuery.contains("permission") || cleanQuery.contains("permissions") ||
                cleanQuery == "check permissions" || cleanQuery == "permissions check" ||
                cleanQuery.contains("why aren't apps blocking") || cleanQuery.contains("why aren't apps blocked") ||
                cleanQuery.contains("not blocking") || cleanQuery.contains("app not blocking") ||
                cleanQuery.contains("why didn't i get my alarm") || cleanQuery.contains("alarm didn't go off") ||
                cleanQuery.contains("alarm not working") || cleanQuery.contains("reminder not working") ||
                cleanQuery.contains("diagnose") || cleanQuery == "diagnostics" || cleanQuery == "/diagnose"

        if (context != null && isPermissionOrDiagnosticQuery) {
            val missingPermissions = mutableListOf<String>()
            val actions = mutableListOf<TalkAction>()
            
            if (!PermissionUtils.hasUsageStatsPermission(context)) {
                missingPermissions.add("Usage Access (App Detection & Telemetry)")
                actions.add(TalkAction.OpenSystemSetting(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS, "Open Usage Access", "⚙️", false))
            }
            if (!PermissionUtils.hasOverlayPermission(context)) {
                missingPermissions.add("Display Over Other Apps (Blocker Screen)")
                actions.add(TalkAction.OpenSystemSetting(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "Open Overlay Settings", "⚙️", true))
            }
            if (!PermissionUtils.isIgnoringBatteryOptimizations(context)) {
                missingPermissions.add("Battery Optimization (Background Reliability)")
                actions.add(TalkAction.OpenSystemSetting(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "Battery Optimization", "⚙️", true))
            }
            if (!PermissionUtils.hasNotificationPermission(context)) {
                missingPermissions.add("Notifications (Reminders & Alarms)")
                actions.add(TalkAction.OpenSystemSetting(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, "Notification Settings", "⚙️", true))
            }
            if (!PermissionUtils.hasExactAlarmPermission(context)) {
                missingPermissions.add("Alarms & Reminders (Precise Task Timing)")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    actions.add(TalkAction.OpenSystemSetting(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "Exact Alarm Settings", "⏰", true))
                }
            }
            
            val isAlarmSpecific = cleanQuery.contains("alarm") || cleanQuery.contains("reminder")
            val isBlockSpecific = cleanQuery.contains("block")

            val text = when {
                missingPermissions.isEmpty() -> {
                    "🔒 **Security & Permissions Diagnostic: All Permissions Granted!**\n\n• Usage Access: Granted\n• Display Overlay: Granted\n• Battery Optimization Exemption: Active\n• Notifications: Allowed\n• Exact Alarms: Allowed\n\nEverything is properly configured for instant app shielding and on-time task alarms. ⚡"
                }
                isAlarmSpecific -> {
                    "⏰ **Task Reminder Diagnostic:**\nTo ensure your alarms and task notifications trigger right on time without being delayed by system battery savers, the following need attention:\n\n" +
                    missingPermissions.joinToString("\n") { "• **$it**" } +
                    "\n\n_Tap the buttons below to enable them directly:_"
                }
                isBlockSpecific -> {
                    "🛡️ **App Shielding Diagnostic:**\nApps won't be blocked reliably unless both **Usage Access** and **Display Over Other Apps** permissions are allowed. Missing:\n\n" +
                    missingPermissions.joinToString("\n") { "• **$it**" } +
                    "\n\n_Tap the buttons below to open the setup screen directly:_"
                }
                else -> {
                    "🔒 **Security & System Diagnostics:**\nHere are the system permissions required for FocusByRj to function smoothly:\n\n" +
                    missingPermissions.joinToString("\n") { "• **$it**" } +
                    "\n\n_Tap the buttons below to enable missing items:_"
                }
            }
            val json = serializeActionsJson("permissions", actions)
            recordTurn(cleanQuery, "permissions")
            return TalkResponse(text, actions, "permissions", json)
        }

        // --- 0.5 DATA INTROSPECTION (LIVE CONTEXT & DATABASE INJECTION) ---
        if (context != null) {
            try {
                val app = context.applicationContext as? com.focusbyrj.app.FocusApplication
                if (app != null) {
                    val isSummaryContextQuery = cleanQuery in listOf(
                        "summary", "today", "today's summary", "summary today", "daily summary", "daily progress",
                        "today tasks", "tasks today", "what's on today", "what do i have today", "how is my day",
                        "hit list", "overview", "briefing", "my day", "recap", "today summary", "progress"
                    ) || cleanQuery.startsWith("summary ") || cleanQuery.startsWith("what's on ") || cleanQuery.startsWith("what is on ")

                    if (isSummaryContextQuery) {
                        lastQueriedTopicId = "summary"
                        val now = System.currentTimeMillis()
                        val startOfDay = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val endOfDay = startOfDay + 86400000L - 1

                        val allTasks = app.taskRepository.allTasks.firstOrNull() ?: emptyList()
                        val completedToday = allTasks.filter { it.isCompleted && it.completedAt != null && it.completedAt >= startOfDay }
                        val pendingTasks = allTasks.filter { !it.isCompleted }
                        val todayTasks = pendingTasks.filter { it.dueDate == null || (it.dueDate in startOfDay..endOfDay) || it.dueDate < startOfDay }
                        val overdueCount = todayTasks.count { it.dueDate != null && it.dueDate < now }
                        val sortedTasks = todayTasks.sortedWith(compareByDescending<com.focusbyrj.app.data.Task> { it.isPriority }.thenBy { it.dueDate ?: Long.MAX_VALUE })

                        val totalToday = completedToday.size + todayTasks.size
                        val percent = if (totalToday > 0) (completedToday.size * 100) / totalToday else 100
                        val filledBlocks = (percent / 10).coerceIn(0, 10)
                        val emptyBlocks = 10 - filledBlocks
                        val progressBar = "█".repeat(filledBlocks) + "░".repeat(emptyBlocks)

                        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        val dayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
                        val greeting = com.focusbyrj.app.util.AyvaDialogueEngine.getSummaryGreeting(context, false, hour, dayOfWeek)

                        val sb = StringBuilder()
                        sb.append(greeting).append("\n\n")

                        if (completedToday.isNotEmpty()) {
                            sb.append("✅ *__Crushed Today__* *(${completedToday.size})*:\n")
                            completedToday.forEach { sb.append("• _${it.title}_\n") }
                            sb.append("\n")
                        }

                        if (sortedTasks.isEmpty()) {
                            sb.append("🎉 You're all caught up for today! No pending tasks.\n")
                            val nextTask = pendingTasks.filter { it.dueDate != null && it.dueDate > endOfDay }.minByOrNull { it.dueDate!! }
                            if (nextTask != null) {
                                sb.append("\n🗓️ *__Up Next On The Horizon__*: *${nextTask.title}* _(${SmartDateParser.formatDueDate(nextTask.dueDate)})_\n")
                            }
                        } else {
                            sb.append("⚡ *__On Today's Hit List__* *(${sortedTasks.size})*")
                            if (overdueCount > 0) sb.append(" *[⚠️ $overdueCount Overdue]*")
                            sb.append(":\n")
                            sortedTasks.forEachIndexed { idx, t ->
                                val p = if (t.isPriority) "🔥 " else ""
                                val d = if (t.dueDate != null) " _(${SmartDateParser.formatDueDate(t.dueDate)})_" else ""
                                sb.append("${idx + 1}. $p*${t.title}*$d\n")
                            }
                        }

                        sb.append("\n📊 *__Daily Progress__*:\n")
                        sb.append("`[$progressBar]` *$percent%*")

                        // Habits overview in summary
                        val activeHabits = app.habitRepository.getAllActiveHabits().firstOrNull() ?: emptyList()
                        if (activeHabits.isNotEmpty()) {
                            val todayDateStr = app.habitRepository.getTodayDateString()
                            val todayHabitLogs = app.habitRepository.getTodayLogs().firstOrNull() ?: emptyList()
                            val completedHabitIds = todayHabitLogs.filter { it.date == todayDateStr }.map { it.habitId }.toSet()
                            val completedHabitsCount = activeHabits.count { completedHabitIds.contains(it.id) }
                            val habitPct = (completedHabitsCount * 100) / activeHabits.size
                            sb.append("\n🌱 *__Daily Habits__*: $completedHabitsCount / ${activeHabits.size} checked in ($habitPct%)")
                        }

                        val quote = if (hour < 15) {
                            com.focusbyrj.app.util.SummaryQuotes.getNextMorningQuote(context)
                        } else {
                            com.focusbyrj.app.util.SummaryQuotes.getNextEveningQuote(context)
                        }
                        sb.append("\n\n💡 _\"$quote\"_")

                        val actions = listOf(
                            TalkAction.AskQuery("/summary", "📋 Full Summary Card"),
                            TalkAction.AskQuery("/tasks", "⚡ Hit List"),
                            TalkAction.AskQuery("/tasks all", "⏳ All Tasks (${pendingTasks.size})"),
                            TalkAction.NavigateAppScreen("dashboard", "Open Dashboard", "📋")
                        )
                        return TalkResponse(sb.toString().trimEnd(), actions, "summary", serializeActionsJson("summary", actions))
                    }

                    val isExplicitTasksQuery = cleanQuery in listOf(
                        "tasks", "my tasks", "pending tasks", "list tasks", "show tasks", "todo list", "overdue", "overdue tasks", "priority tasks", "next task", "what's next"
                    )
                    if (isExplicitTasksQuery) {
                        lastQueriedTopicId = "tasks"
                        val tasks = app.taskRepository.allTasks.firstOrNull() ?: emptyList()
                        val pending = tasks.filter { !it.isCompleted }
                        val actions = listOf(
                            TalkAction.AskQuery("/summary", "📊 Daily Summary"),
                            TalkAction.AskQuery("/tasks all", "⏳ All Tasks"),
                            TalkAction.AskQuery("/advice", "💡 Focus Advice"),
                            TalkAction.NavigateAppScreen("dashboard", "Open Dashboard", "📋")
                        )
                        val text = if (pending.isEmpty()) {
                            "🎉 You have **no pending tasks** right now! All clear."
                        } else {
                            val overdueCount = pending.count { it.dueDate != null && it.dueDate < System.currentTimeMillis() }
                            val overdueNotice = if (overdueCount > 0) " *(⚠️ $overdueCount overdue)*" else ""
                            "⚡ You have **${pending.size} pending task(s)**$overdueNotice:\n\n" +
                            pending.take(6).mapIndexed { idx, t ->
                                val priority = if (t.isPriority) "🔥 " else ""
                                val due = if (t.dueDate != null) " _(${SmartDateParser.formatDueDate(t.dueDate)})_" else ""
                                "${idx + 1}. $priority*${t.title}*$due"
                            }.joinToString("\n") +
                            (if (pending.size > 6) "\n_...and ${pending.size - 6} more._" else "")
                        }
                        recordTurn(cleanQuery, "tasks")
                        return TalkResponse(text, actions, "tasks", serializeActionsJson("tasks", actions))
                    }

                    // --- CONTEXT-AWARE FOCUS COACHING & SCREEN TIME ANALYSIS ---
                    val isAdviceQuery = cleanQuery in listOf(
                        "advice", "/advice", "focus advice", "how to focus", "help me focus", "coach", "coaching",
                        "give me advice", "i can't focus", "distracted", "i am distracted", "procrastinating", "tips",
                        "productivity tip", "focus tip", "focus tips", "stuck", "i'm stuck", "im stuck",
                        "tired", "i'm tired", "im tired", "exhausted", "overwhelmed", "i'm overwhelmed", "im overwhelmed",
                        "can't do this", "too tired"
                    ) || cleanQuery.contains("help me focus") || cleanQuery.contains("can't focus") ||
                            cleanQuery.contains("i'm stuck") || cleanQuery.contains("im stuck") ||
                            cleanQuery.contains("i am stuck") || cleanQuery.contains("i'm tired") ||
                            cleanQuery.contains("im tired") || cleanQuery.contains("i am tired") ||
                            cleanQuery.contains("overwhelmed")
                    if (isAdviceQuery) {
                        lastQueriedTopicId = "advice"
                        val tasks = app.taskRepository.allTasks.firstOrNull() ?: emptyList()
                        val pending = tasks.filter { !it.isCompleted }
                        val now = System.currentTimeMillis()
                        val overdueCount = pending.count { it.dueDate != null && it.dueDate < now }
                        
                        val totalScreenTimeMins = if (UsageStatsHelper.hasUsageStatsPermission(context)) {
                            val usage = UsageStatsHelper.getTodayUsageStats(context)
                            (usage.sumOf { it.timeInForegroundMs } / 60000L).toInt()
                        } else {
                            0
                        }

                        // Habits data for contextual advice
                        val habits = app.habitRepository.getAllActiveHabits().firstOrNull() ?: emptyList()
                        val todayDateStr = app.habitRepository.getTodayDateString()
                        val todayHabitLogs = app.habitRepository.getTodayLogs().firstOrNull() ?: emptyList()
                        val completedHabitIds = todayHabitLogs.filter { it.date == todayDateStr }.map { it.habitId }.toSet()
                        val habitsCompletedCount = habits.count { completedHabitIds.contains(it.id) }

                        val adviceText = AyvaDialogueEngine.getContextualFocusAdvice(
                            context = context,
                            totalScreenTimeMins = totalScreenTimeMins,
                            pendingTasksCount = pending.size,
                            overdueCount = overdueCount,
                            habitsCount = habits.size,
                            habitsCompletedCount = habitsCompletedCount
                        )

                        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        val isLateNight = currentHour >= 22 || currentHour < 5

                        val actions = mutableListOf<TalkAction>()
                        if (overdueCount > 0) {
                            actions.add(TalkAction.AskQuery("/tasks", "⚡ View Overdue ($overdueCount)"))
                            actions.add(TalkAction.AskQuery("/tasks postpone all", "🌙 Push to Tomorrow"))
                        }
                        if (isLateNight) {
                            actions.add(TalkAction.AskQuery("/breathe", "🫁 Calm Breathing"))
                            actions.add(TalkAction.AskQuery("/screentime", "📱 Screen Time"))
                        } else {
                            actions.add(TalkAction.AskQuery("/breathe", "🫁 Guided Breathing"))
                            if (totalScreenTimeMins > 120) {
                                actions.add(TalkAction.AskQuery("/screentime", "📱 Screen Time"))
                            }
                            actions.add(TalkAction.AskQuery("/tasks", "📋 My Tasks"))
                        }

                        recordTurn(cleanQuery, "advice")
                        return TalkResponse(
                            formattedText = "💡 **Ayva Focus Coach**\n\n$adviceText",
                            actions = actions,
                            topicId = "advice",
                            jsonPayload = serializeActionsJson("advice", actions)
                        )
                    }

                    // --- SCREEN TIME & APP USAGE TELEMETRY QUERY ---
                    val isScreenTimeQuery = cleanQuery in listOf(
                        "screentime", "/screentime", "screen time", "my screen time", "usage", "app usage",
                        "how much screen time", "today usage", "screen usage", "phone usage", "stats today"
                    ) || cleanQuery.contains("screen time") || cleanQuery.contains("app usage")
                    if (isScreenTimeQuery) {
                        lastQueriedTopicId = "screentime"
                        if (!UsageStatsHelper.hasUsageStatsPermission(context)) {
                            val actions = listOf(
                                TalkAction.DirectPrefUpdate("usage_permission", "special", "usage", "Grant Usage Access", "Allow usage access in Settings", "⚙️")
                            )
                            return TalkResponse(
                                formattedText = "📊 **Usage Telemetry Locked**\n\nTo view your app usage stats and mindful screen time analysis, please grant **Usage Access** permission.",
                                actions = actions,
                                topicId = "screentime",
                                jsonPayload = serializeActionsJson("screentime", actions)
                            )
                        }

                        val stats = UsageStatsHelper.getTodayUsageStats(context)
                        val totalMs = stats.sumOf { it.timeInForegroundMs }
                        val totalHrs = totalMs / 3600000L
                        val totalMins = (totalMs % 3600000L) / 60000L

                        val topApps = stats.take(4).joinToString("\n") {
                            val mins = it.timeInForegroundMs / 60000L
                            val hrs = mins / 60
                            val remMins = mins % 60
                            val timeStr = if (hrs > 0) "${hrs}h ${remMins}m" else "${remMins}m"
                            "• **${it.appName}**: $timeStr"
                        }

                        val formatted = "📱 **Today's Screen Time Summary**\n\n" +
                                "⏱️ **Total Foreground Time**: **${totalHrs}h ${totalMins}m**\n\n" +
                                "🔝 **Top Apps Used Today**:\n$topApps"

                        val actions = listOf(
                            TalkAction.AskQuery("/advice", "💡 Focus Advice"),
                            TalkAction.AskQuery("/breathe", "🫁 1-Min Recharge"),
                            TalkAction.NavigateAppScreen("dashboard", "Dashboard", "📋")
                        )

                        recordTurn(cleanQuery, "screentime")
                        return TalkResponse(
                            formattedText = formatted,
                            actions = actions,
                            topicId = "screentime",
                            jsonPayload = serializeActionsJson("screentime", actions)
                        )
                    }

                    // --- GUIDED BREATHING & RAPID RESET ---
                    val isBreathingQuery = cleanQuery in listOf(
                        "breathe", "/breathe", "breathing", "calm down", "reset", "stress", "stressed", "anxiety",
                        "take a breath", "deep breath", "relax", "mindful break"
                    ) || cleanQuery.contains("breathe") || cleanQuery.contains("relax")
                    if (isBreathingQuery) {
                        lastQueriedTopicId = "breathe"
                        val guide = AyvaDialogueEngine.getBreathingGuidance(context)
                        val actions = listOf(
                            TalkAction.AskQuery("/advice", "💡 Focus Advice"),
                            TalkAction.AskQuery("/tasks", "📋 My Tasks"),
                            TalkAction.AskQuery("/drill", "⚡ Math Drill")
                        )
                        recordTurn(cleanQuery, "breathe")
                        return TalkResponse(
                            formattedText = guide,
                            actions = actions,
                            topicId = "breathe",
                            jsonPayload = serializeActionsJson("breathe", actions)
                        )
                    }

                    // --- MICRO-DRILL & MENTAL WARM-UP ---
                    val isDrillQuery = cleanQuery in listOf(
                        "drill", "/drill", "math drill", "mental drill", "mind drill", "arithmetic drill",
                        "warm up", "brain warm up", "quick math", "math challenge"
                    ) || cleanQuery.startsWith("/drill") || cleanQuery.startsWith("drill")
                    if (isDrillQuery) {
                        lastQueriedTopicId = "drill"
                        val intro = AyvaDialogueEngine.getMicroDrillPrompt(context)
                        
                        // Deterministically generate a fast 2-term arithmetic challenge based on current time
                        val seed = (System.currentTimeMillis() / 10000L).toInt()
                        val a = 12 + (Math.abs(seed) % 35)
                        val b = 7 + (Math.abs(seed * 3) % 28)
                        val isMultiplication = (seed % 3 == 0)
                        val (problemText, correctVal) = if (isMultiplication) {
                            val smA = 6 + (Math.abs(seed) % 7)
                            val smB = 6 + (Math.abs(seed * 2) % 8)
                            Pair("$smA × $smB", smA * smB)
                        } else {
                            Pair("$a + $b", a + b)
                        }

                        // Generate 3 plausible options
                        val opt1 = correctVal
                        val opt2 = correctVal + (if (seed % 2 == 0) 2 else -2)
                        val opt3 = correctVal + (if (seed % 2 == 0) -4 else 5)
                        val options = listOf(opt1, opt2, opt3).shuffled()

                        val formatted = "$intro\n\n🎯 **Question**: What is **$problemText**?"
                        val actions = options.map { opt ->
                            if (opt == correctVal) {
                                TalkAction.AskQuery("/drill_answer correct $problemText = $opt", "⚡ $opt")
                            } else {
                                TalkAction.AskQuery("/drill_answer incorrect $problemText = $opt (Answer: $correctVal)", "⚡ $opt")
                            }
                        } + listOf(
                            TalkAction.AskQuery("/focus 25", "⏱️ 25m Focus Block"),
                            TalkAction.AskQuery("/tasks", "📋 My Tasks")
                        )

                        recordTurn(cleanQuery, "drill")
                        return TalkResponse(formatted, actions, "drill", serializeActionsJson("drill", actions))
                    }

                    // Micro-drill answer resolution
                    if (cleanQuery.startsWith("/drill_answer") || cleanQuery.startsWith("drill_answer")) {
                        val isCorrect = cleanQuery.contains("correct")
                        val praise = if (isCorrect) {
                            AyvaDialogueEngine.getDrillFastCorrectPraise(context)
                        } else {
                            AyvaDialogueEngine.getDrillMissComfort(context)
                        }
                        val feedback = if (isCorrect) {
                            "🎯 **Correct!** $praise\n\nYour neural focus is now primed. Channel this dopamine into your next deep work block!"
                        } else {
                            "💡 **Close!** $praise\n\nCognitive cobwebs shaken off. You're ready to dive into work."
                        }
                        val actions = listOf(
                            TalkAction.AskQuery("/focus 25", "⏱️ 25m Sprint"),
                            TalkAction.AskQuery("/drill", "⚡ Another Drill"),
                            TalkAction.AskQuery("/tasks", "📋 Tasks")
                        )
                        recordTurn(cleanQuery, "drill")
                        return TalkResponse(feedback, actions, "drill", serializeActionsJson("drill", actions))
                    }

                    // --- QUICK FOCUS & POMODORO ACTIVATION ---
                    val isFocusSprintQuery = cleanQuery in listOf(
                        "focus", "/focus", "pomodoro", "/pomodoro", "sprint", "deep work", "start pomodoro",
                        "start focus", "focus block", "timer", "25m focus", "focus session"
                    ) || cleanQuery.startsWith("/focus") || cleanQuery.startsWith("focus ") || cleanQuery.startsWith("pomodoro ")
                    if (isFocusSprintQuery) {
                        lastQueriedTopicId = "focus_sprint"
                        var minutes = 25
                        val numMatch = Regex("\\b(\\d+)\\b").find(cleanQuery)
                        if (numMatch != null) {
                            minutes = numMatch.groupValues[1].toIntOrNull()?.coerceIn(5, 120) ?: 25
                        }

                        // Check if there is an active top priority task
                        val pending = app.taskRepository.allTasks.firstOrNull()?.filter { !it.isCompleted } ?: emptyList()
                        val topTask = pending.find { it.isPriority } ?: pending.firstOrNull()
                        val guidance = AyvaDialogueEngine.getFocusSprintGuidance(context, minutes, topTask?.title)

                        val actions = mutableListOf<TalkAction>()
                        actions.add(TalkAction.NavigateAppScreen("dashboard", "⚡ Go to Focus Dashboard", "⏱️"))
                        if (topTask != null) {
                            actions.add(TalkAction.AskQuery("complete ${topTask.title}", "✅ Finish '${topTask.title.take(20)}'"))
                        }
                        actions.add(TalkAction.AskQuery("/breathe", "🫁 1-Min Breath"))

                        recordTurn(cleanQuery, "focus_sprint")
                        return TalkResponse(guidance, actions, "focus_sprint", serializeActionsJson("focus_sprint", actions))
                    }

                    // --- COMPREHENSIVE FOCUS POSTURE BRIEFING ---
                    val isFocusStatusQuery = cleanQuery in listOf(
                        "status", "/status", "focus status", "how am i doing", "check in", "checkin", "briefing",
                        "posture", "report", "my status", "overview", "focus posture", "daily report"
                    ) || cleanQuery.contains("focus status") || cleanQuery.contains("how am i doing")
                    if (isFocusStatusQuery) {
                        lastQueriedTopicId = "status"
                        val tasks = app.taskRepository.allTasks.firstOrNull() ?: emptyList()
                        val pending = tasks.filter { !it.isCompleted }
                        val now = System.currentTimeMillis()
                        val overdueCount = pending.count { it.dueDate != null && it.dueDate < now }
                        
                        // Completed today
                        val cal = java.util.Calendar.getInstance()
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        cal.set(java.util.Calendar.MINUTE, 0)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        val startOfDay = cal.timeInMillis
                        val completedTodayCount = tasks.count { it.isCompleted && (it.completedAt ?: 0L) >= startOfDay }

                        // Telemetry
                        var totalScreenTimeMins = 0
                        var topAppName: String? = null
                        var topAppMins = 0
                        if (UsageStatsHelper.hasUsageStatsPermission(context)) {
                            val stats = UsageStatsHelper.getTodayUsageStats(context)
                            totalScreenTimeMins = (stats.sumOf { it.timeInForegroundMs } / 60000L).toInt()
                            val top = stats.firstOrNull()
                            if (top != null) {
                                topAppName = top.appName
                                topAppMins = (top.timeInForegroundMs / 60000L).toInt()
                            }
                        }

                        // Routines
                        val allSchedules = app.database.scheduleDao().getAllSchedulesSync()
                        val currentCal = java.util.Calendar.getInstance()
                        val curHour = currentCal.get(java.util.Calendar.HOUR_OF_DAY)
                        val curMin = currentCal.get(java.util.Calendar.MINUTE)
                        val curTimeMins = curHour * 60 + curMin
                        val activeSchedule = allSchedules.find {
                            if (!it.isEnabled) return@find false
                            val startMins = it.startHour * 60 + it.startMinute
                            val endMins = it.endHour * 60 + it.endMinute
                            if (startMins <= endMins) {
                                curTimeMins in startMins until endMins
                            } else {
                                curTimeMins >= startMins || curTimeMins < endMins
                            }
                        }
                        val routineEndsAt = activeSchedule?.let { String.format("%02d:%02d", it.endHour, it.endMinute) }

                        // Restrictions
                        val restrictions = app.database.appRestrictionDao().getAllRestrictions().firstOrNull() ?: emptyList()
                        val restrictedCount = restrictions.count { it.isRestricted }
                        val isStrictMode = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE).getString("block_mode", "HARD") == "HARD"

                        // Aptitude / Streak
                        val isVacation = AptitudeManager.isVacationMode(context)
                        val profile = AptitudeManager.profileFlow.value

                        // Habits Data
                        val activeHabits = app.habitRepository.getAllActiveHabits().firstOrNull() ?: emptyList()
                        val todayDateStr = app.habitRepository.getTodayDateString()
                        val todayHabitLogs = app.habitRepository.getTodayLogs().firstOrNull() ?: emptyList()
                        val completedHabitIds = todayHabitLogs.filter { it.date == todayDateStr }.map { it.habitId }.toSet()
                        val completedHabitsCount = activeHabits.count { completedHabitIds.contains(it.id) }

                        // Notes Data
                        val activeNotes = kotlin.runCatching {
                            com.focusbyrj.app.data.note.NoteDatabase.getInstance(context).noteDao().getAllActiveNotesSync()
                        }.getOrNull() ?: emptyList()
                        val pinnedNotesCount = activeNotes.count { it.isPinned }

                        // Battery Context
                        val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                        val batteryLevel = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                        val batteryScale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                        val batteryPct = if (batteryLevel >= 0 && batteryScale > 0) (batteryLevel * 100) / batteryScale else null
                        val batteryStatus = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                        val isCharging = batteryStatus == android.os.BatteryManager.BATTERY_STATUS_CHARGING || batteryStatus == android.os.BatteryManager.BATTERY_STATUS_FULL

                        val briefing = AyvaDialogueEngine.getFocusStatusBriefing(
                            context = context,
                            totalScreenTimeMins = totalScreenTimeMins,
                            topAppName = topAppName,
                            topAppMins = topAppMins,
                            activeRoutineName = activeSchedule?.name,
                            routineEndsAt = routineEndsAt,
                            restrictedAppsCount = restrictedCount,
                            isStrictMode = isStrictMode,
                            streak = profile.currentStreak,
                            isVacation = isVacation,
                            pendingCount = pending.size,
                            completedTodayCount = completedTodayCount,
                            overdueCount = overdueCount,
                            totalHabitsCount = activeHabits.size,
                            completedHabitsCount = completedHabitsCount,
                            notesCount = activeNotes.size,
                            pinnedNotesCount = pinnedNotesCount,
                            batteryPct = batteryPct,
                            isCharging = isCharging
                        )

                        val actions = mutableListOf<TalkAction>()
                        if (overdueCount > 0) {
                            actions.add(TalkAction.AskQuery("push overdue to tomorrow", "🧹 Clean Overdue ($overdueCount)"))
                        }
                        actions.add(TalkAction.AskQuery("/focus 25", "⏱️ 25m Focus Sprint"))
                        actions.add(TalkAction.AskQuery("/tasks", "📋 My Tasks"))
                        actions.add(TalkAction.AskQuery("/habits", "🌱 Habits (${completedHabitsCount}/${activeHabits.size})"))
                        actions.add(TalkAction.AskQuery("/notes", "📝 Notes (${activeNotes.size})"))
                        actions.add(TalkAction.AskQuery("/drill", "⚡ Mind Drill"))

                        recordTurn(cleanQuery, "status")
                        return TalkResponse(briefing, actions, "status", serializeActionsJson("status", actions))
                    }

                    val isStreakOrVacationQuery = cleanQuery in listOf(
                        "streak", "my streak", "current streak", "frozen", "is my streak frozen", "is my streak frozen?",
                        "vacation mode", "freeze streak", "unfreeze streak", "my points", "xp", "level", "aptitude",
                        "stats", "profile", "how is my streak", "streak status", "what is my streak"
                    ) || (cleanQuery.contains("streak") && !isQuestionQuery) || (cleanQuery.contains("vacation") && !isQuestionQuery)
                    if (isStreakOrVacationQuery) {
                        lastQueriedTopicId = "vacation_mode"
                        val isVacation = AptitudeManager.isVacationMode(context)
                        val profile = AptitudeManager.profileFlow.value
                        val streakText = if (isVacation) {
                            "❄️ **Vacation Mode is ACTIVE**\n• Current Streak: **${profile.currentStreak} days (Frozen 🛡️)**\n• Practice Alerts: **Paused**\n• Level: **${profile.level}** (${profile.xp} XP)"
                        } else {
                            "⚡ **Current Streak: ${profile.currentStreak} days**\n• Status: **Active (Daily tests required)**\n• Level: **${profile.level}** (${profile.xp} XP)\n• Accuracy: **${profile.accuracy.toInt()}%**"
                        }
                        val actions = mutableListOf<TalkAction>()
                        if (isVacation) {
                            actions.add(TalkAction.DirectPrefUpdate("vacation_mode", "boolean", "false", "⚡ Unfreeze", "Unfreeze Streak (Resume)", "⚡"))
                        } else {
                            actions.add(TalkAction.DirectPrefUpdate("vacation_mode", "boolean", "true", "❄️ Freeze", "Freeze Streak (Vacation)", "🏖️"))
                        }
                        actions.add(TalkAction.AskQuery("/profile", "🏆 View Profile"))
                        actions.add(TalkAction.AskQuery("/drill", "⚡ Math Drill"))
                        return TalkResponse(streakText, actions, "vacation_mode", serializeActionsJson("vacation_mode", actions))
                    }

                    val isBlockedAppsQuery = cleanQuery in listOf(
                        "what is blocked", "what apps are blocked", "blocked apps", "locked apps",
                        "active restrictions", "current restrictions", "is anything blocked", "restricted apps",
                        "how many apps blocked", "which apps are locked"
                    ) || cleanQuery.contains("blocked apps") || cleanQuery.contains("locked apps")
                    if (isBlockedAppsQuery) {
                        lastQueriedTopicId = "lock_protocol"
                        val restrictions = app.database.appRestrictionDao().getAllRestrictions().firstOrNull() ?: emptyList()
                        val active = restrictions.filter { it.isRestricted }
                        val text = if (active.isEmpty()) {
                            "🔓 **No applications are currently restricted.** All apps are free to access."
                        } else {
                            "🔒 **Currently Restricted Apps (${active.size}):**\n\n" +
                            active.take(8).joinToString("\n") {
                                "• **${it.appName.ifBlank { it.packageName }}** _[${it.mode} Mode]_"
                            } + (if (active.size > 8) "\n_...and ${active.size - 8} more._" else "")
                        }
                        val actions = mutableListOf<TalkAction>()
                        if (active.isNotEmpty()) {
                            actions.add(TalkAction.AskQuery("unblock all", "🔓 Unlock All"))
                        }
                        actions.add(TalkAction.NavigateAppScreen("dashboard", "Manage Blocks", "🛡️"))
                        return TalkResponse(text, actions, "lock_protocol", serializeActionsJson("lock_protocol", actions))
                    }

                    // --- HABITS QUERIES & ACTIONS ---
                    val isHabitsQuery = cleanQuery in listOf(
                        "habit", "habits", "my habits", "show habits", "list habits",
                        "habit tracker", "daily habits", "track habit", "track habits",
                        "open habits", "habits list", "check habits"
                    ) || cleanQuery.startsWith("habit ") || cleanQuery.startsWith("habits ") ||
                       cleanQuery.startsWith("log habit") || cleanQuery.startsWith("done habit") ||
                       cleanQuery.startsWith("complete habit") || cleanQuery.startsWith("check habit") ||
                       cleanQuery.startsWith("finished habit") || cleanQuery.startsWith("did my habit") ||
                       cleanQuery.startsWith("i drank water") || cleanQuery.startsWith("drank water") ||
                       cleanQuery.startsWith("i completed") || cleanQuery.startsWith("i finished")
                    if (isHabitsQuery) {
                        lastQueriedTopicId = "habits"
                        val activeHabits = app.habitRepository.getAllActiveHabits().firstOrNull() ?: emptyList()
                        val todayDateStr = app.habitRepository.getTodayDateString()
                        val todayHabitLogs = app.habitRepository.getTodayLogs().firstOrNull() ?: emptyList()
                        val completedHabitIds = todayHabitLogs.filter { it.date == todayDateStr }.map { it.habitId }.toSet()
                        val completedCount = activeHabits.count { completedHabitIds.contains(it.id) }

                        // Check if user is asking to complete or check off a specific habit
                        val completeMatch = Regex("(?i)^(?:complete|done|check|log|mark|finished|completed)\\s+(?:habit\\s+)?(.+)$").find(cleanQuery)
                            ?: Regex("(?i)^(?:i\\s+)?(?:finished|completed|did|logged)\\s+(?:my\\s+)?(?:habit\\s+)?(.+)$").find(cleanQuery)
                            ?: Regex("(?i)^habit\\s+(?:done|complete|check|log)\\s+(.+)$").find(cleanQuery)
                        if (completeMatch != null) {
                            var targetHabitTitle = completeMatch.groupValues[1].trim().lowercase()
                            targetHabitTitle = targetHabitTitle.replace(Regex("(?i)^(?:today|my|the)\\s+"), "").replace(Regex("(?i)\\s+(?:today|done)$"), "").trim()
                            val matchedHabit = activeHabits.find {
                                val t = it.title.trim().lowercase()
                                t == targetHabitTitle || t.contains(targetHabitTitle) || targetHabitTitle.contains(t) ||
                                (targetHabitTitle.contains("water") && t.contains("water")) ||
                                (targetHabitTitle.contains("read") && t.contains("read")) ||
                                (targetHabitTitle.contains("workout") && (t.contains("workout") || t.contains("gym") || t.contains("exercise"))) ||
                                (targetHabitTitle.contains("meditat") && t.contains("meditat"))
                            }
                            if (matchedHabit != null) {
                                val log = app.habitRepository.incrementHabitProgress(matchedHabit.id)
                                val praise = AyvaDialogueEngine.getHabitCompletedPraise(context, matchedHabit.title, log.completedCount)
                                val actions = listOf(
                                    TalkAction.AskQuery("/habits", "🌱 View Habits"),
                                    TalkAction.NavigateAppScreen("habit", "Open Habit Tracker", "🌱")
                                )
                                recordTurn(cleanQuery, "habits")
                                return TalkResponse(praise, actions, "habits", serializeActionsJson("habits", actions))
                            }
                        }

                        val intro = AyvaDialogueEngine.getHabitListIntro(context, activeHabits.size, completedCount)
                        val sb = StringBuilder(intro)
                        if (activeHabits.isNotEmpty()) {
                            sb.append("\n\n")
                            activeHabits.forEachIndexed { idx, h ->
                                val isDone = completedHabitIds.contains(h.id)
                                val status = if (isDone) "✅" else "⭕"
                                sb.append("${idx + 1}. $status **${h.title}**\n")
                            }
                        }
                        val actions = mutableListOf<TalkAction>()
                        actions.add(TalkAction.NavigateAppScreen("habit", "Open Habit Tracker", "🌱"))
                        val uncompleted = activeHabits.filter { !completedHabitIds.contains(it.id) }
                        uncompleted.take(2).forEach { h ->
                            actions.add(TalkAction.AskQuery("/habit done ${h.title}", "✅ Log ${h.title.take(18)}"))
                        }
                        actions.add(TalkAction.AskQuery("/status", "⚡ Focus Status"))
                        recordTurn(cleanQuery, "habits")
                        return TalkResponse(sb.toString().trimEnd(), actions, "habits", serializeActionsJson("habits", actions))
                    }

                    // --- NOTES QUERIES (LIST, SEARCH, CREATE) ---
                    val isNotesListQuery = cleanQuery in listOf(
                        "notes", "my notes", "show notes", "list notes", "keep notes",
                        "open notes", "view notes", "all notes", "notes list"
                    )
                    val searchNoteMatch = Regex("(?i)^(?:search\\s+notes?(?:\\s+for)?|find\\s+notes?(?:\\s+about)?|look\\s+for\\s+notes?)\\s+(.+)$").find(cleanQuery)
                    val createNoteMatch = Regex("(?i)^(?:create\\s+note|add\\s+note|new\\s+note|take\\s+a?\\s*note|write\\s+note|jot\\s+down)\\s+(.+)$").find(cleanQuery)

                    if (isNotesListQuery || searchNoteMatch != null || createNoteMatch != null) {
                        lastQueriedTopicId = "notes"
                        val noteDb = com.focusbyrj.app.data.note.NoteDatabase.getInstance(context)
                        val noteDao = noteDb.noteDao()

                        if (createNoteMatch != null) {
                            val content = createNoteMatch.groupValues[1].trim()
                            val parts = content.split(Regex("[:\\-]"), 2)
                            val title = parts[0].trim().replaceFirstChar { it.uppercase() }
                            val body = if (parts.size > 1) parts[1].trim() else ""
                            val newNote = com.focusbyrj.app.data.note.NoteEntity(
                                title = title,
                                content = body,
                                isPinned = false,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            noteDao.insertNote(newNote)
                            val quip = AyvaDialogueEngine.getNoteCreatedQuip(context, title)
                            val actions = listOf(
                                TalkAction.AskQuery("/notes", "📝 View Notes"),
                                TalkAction.NavigateAppScreen("notes", "Open Keep Notes", "📝")
                            )
                            recordTurn(cleanQuery, "notes")
                            return TalkResponse(quip, actions, "notes", serializeActionsJson("notes", actions))
                        }

                        if (searchNoteMatch != null) {
                            val searchQuery = searchNoteMatch.groupValues[1].trim()
                            val foundNotes: List<com.focusbyrj.app.data.note.NoteEntity> = noteDao.searchNotesSync(searchQuery)
                            val intro = AyvaDialogueEngine.getNotesSearchIntro(context, searchQuery, foundNotes.size)
                            val sb = StringBuilder(intro)
                            if (foundNotes.isNotEmpty()) {
                                sb.append("\n\n")
                                foundNotes.take(6).forEachIndexed { idx, n ->
                                    val pin = if (n.isPinned) "📌 " else ""
                                    val snippet = if (n.content.isNotBlank()) " — _${n.content.take(45)}..._" else ""
                                    sb.append("${idx + 1}. $pin**${n.title}**$snippet\n")
                                }
                            }
                            val actions = listOf(
                                TalkAction.AskQuery("/notes", "📝 All Notes"),
                                TalkAction.NavigateAppScreen("notes", "Open Keep Notes", "📝")
                            )
                            recordTurn(cleanQuery, "notes")
                            return TalkResponse(sb.toString().trimEnd(), actions, "notes", serializeActionsJson("notes", actions))
                        }

                        // Notes List
                        val allActiveNotes: List<com.focusbyrj.app.data.note.NoteEntity> = noteDao.getAllActiveNotesSync()
                        val pinnedCount = allActiveNotes.count { it.isPinned }
                        val intro = AyvaDialogueEngine.getNotesListIntro(context, allActiveNotes.size, pinnedCount)
                        val sb = StringBuilder(intro)
                        if (allActiveNotes.isNotEmpty()) {
                            sb.append("\n\n")
                            allActiveNotes.take(6).forEachIndexed { idx, n ->
                                val pin = if (n.isPinned) "📌 " else ""
                                val snippet = if (n.content.isNotBlank()) " — _${n.content.take(40)}..._" else ""
                                sb.append("${idx + 1}. $pin**${n.title}**$snippet\n")
                            }
                            if (allActiveNotes.size > 6) {
                                sb.append("• _...and ${allActiveNotes.size - 6} more in Keep Notes._\n")
                            }
                        }
                        val actions = listOf(
                            TalkAction.NavigateAppScreen("notes", "Open Keep Notes", "📝"),
                            TalkAction.AskQuery("/tasks", "📋 Tasks"),
                            TalkAction.AskQuery("/status", "⚡ Focus Status")
                        )
                        recordTurn(cleanQuery, "notes")
                        return TalkResponse(sb.toString().trimEnd(), actions, "notes", serializeActionsJson("notes", actions))
                    }

                    // Check if performing imperative task operations (ONLY IF NOT A QUESTION)
                    if (!isQuestionQuery) {
                        val nluResult = OfflineNluEngine.parse(cleanQuery, app.taskRepository.allTasks.firstOrNull()?.filter { !it.isCompleted } ?: emptyList())
                        if (nluResult.intent != NluIntent.UNKNOWN) {
                            val tasks = app.taskRepository.allTasks.firstOrNull() ?: emptyList()
                            val pending = tasks.filter { !it.isCompleted }

                            if (nluResult.intent == NluIntent.CONFLICT) {
                                val actions = nluResult.conflictOptions.map { opt ->
                                    TalkAction.AskQuery(opt.command, opt.label, opt.emoji)
                                }
                                return TalkResponse(
                                    formattedText = nluResult.conflictPrompt ?: "🤔 **Conflict Detected:** Did you mean to update an existing task or create a new one?",
                                    actions = actions,
                                    topicId = "conflict",
                                    jsonPayload = serializeActionsJson("conflict", actions)
                                )
                            }

                            if (nluResult.intent == NluIntent.CREATE_TASK) {
                                val (extractedTitle, extractedDueDate) = OfflineNluEngine.extractTaskCreationDetails(cleanQuery)
                                val finalTitle = nluResult.createdTaskTitle?.takeIf { it.isNotBlank() } ?: extractedTitle
                                val finalDueDate = nluResult.targetDateMs ?: extractedDueDate
                                val newTask = com.focusbyrj.app.data.Task(
                                    title = finalTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                    isPriority = false,
                                    isPersistent = false,
                                    dueDate = finalDueDate
                                )
                                val newId = app.taskRepository.insertTask(newTask)
                                TaskReminderHelper.scheduleReminder(context, newTask.copy(id = newId))
                                TodoWidgetProvider.updateAllWidgets(context)

                                val dateStr = if (finalDueDate != null) " (Due: ${SmartDateParser.formatDueDate(finalDueDate)})" else ""
                                val actions = listOf(
                                    TalkAction.AskQuery("/tasks", "📋 View Tasks"),
                                    TalkAction.AskQuery("/reschedule $newId", "⏰ Change Time")
                                )
                                return TalkResponse(
                                    formattedText = "✅ **Added to your radar:**\n• **${newTask.title}**$dateStr",
                                    actions = actions,
                                    topicId = "tasks",
                                    jsonPayload = serializeActionsJson("tasks", actions)
                                )
                            }

                            if (nluResult.intent == NluIntent.RESCHEDULE || nluResult.intent == NluIntent.COMPLETE || nluResult.intent == NluIntent.DELETE) {
                                val now = System.currentTimeMillis()
                                val isOverdueTriage = cleanQuery.contains("overdue")

                                if (isOverdueTriage) {
                                    val overdueTasks = pending.filter { it.dueDate != null && it.dueDate < now }
                                    if (overdueTasks.isEmpty()) {
                                        val actions = listOf(
                                            TalkAction.AskQuery("/tasks", "📋 View Tasks"),
                                            TalkAction.AskQuery("/status", "⚡ Focus Status")
                                        )
                                        return TalkResponse(
                                            formattedText = "✨ **Radar is pristine!** You have zero overdue tasks right now. Great job keeping on schedule!",
                                            actions = actions,
                                            topicId = "tasks",
                                            jsonPayload = serializeActionsJson("tasks", actions)
                                        )
                                    }

                                    if (nluResult.intent == NluIntent.DELETE) {
                                        // User wants to clean/clear overdue tasks
                                        overdueTasks.forEach { 
                                            app.taskRepository.deleteTask(it)
                                            TaskReminderHelper.cancelReminder(context, it)
                                        }
                                        TodoWidgetProvider.updateAllWidgets(context)
                                        val praise = AyvaDialogueEngine.getOverdueCleanPraise(context, overdueTasks.size)
                                        val actions = listOf(
                                            TalkAction.AskQuery("/tasks", "📋 View Remaining Tasks"),
                                            TalkAction.AskQuery("/focus 25", "⏱️ 25m Focus Block")
                                        )
                                        return TalkResponse(praise, actions, "tasks", serializeActionsJson("tasks", actions))
                                    } else {
                                        // User wants to push/reschedule overdue tasks
                                        val targetDate = nluResult.targetDateMs ?: (now + 86400000L)
                                        overdueTasks.forEach {
                                            val updated = it.copy(dueDate = targetDate)
                                            app.taskRepository.updateTask(updated)
                                            TaskReminderHelper.scheduleReminder(context, updated)
                                        }
                                        TodoWidgetProvider.updateAllWidgets(context)
                                        val timeStr = SmartDateParser.formatDueDate(targetDate)
                                        val praise = AyvaDialogueEngine.getOverdueTriagePraise(context, overdueTasks.size, timeStr)
                                        val actions = listOf(
                                            TalkAction.AskQuery("/tasks", "📋 View Tasks"),
                                            TalkAction.AskQuery("/focus 25", "⏱️ 25m Focus Block")
                                        )
                                        return TalkResponse(praise, actions, "tasks", serializeActionsJson("tasks", actions))
                                    }
                                }

                                if (nluResult.isAllTasks) {
                                    if (nluResult.intent == NluIntent.RESCHEDULE) {
                                        val newDate = nluResult.targetDateMs ?: (System.currentTimeMillis() + 86400000L)
                                        pending.forEach { 
                                            val updated = it.copy(dueDate = newDate)
                                            app.taskRepository.updateTask(updated)
                                            TaskReminderHelper.scheduleReminder(context, updated)
                                        }
                                        TodoWidgetProvider.updateAllWidgets(context)
                                        val dateStr = SmartDateParser.formatDueDate(newDate)
                                        return TalkResponse("⏰ **Rescheduled all ${pending.size} tasks** to $dateStr.")
                                    } else if (nluResult.intent == NluIntent.COMPLETE) {
                                        pending.forEach { 
                                            app.taskRepository.updateTask(it.copy(isCompleted = true, completedAt = System.currentTimeMillis()))
                                            TaskReminderHelper.cancelReminder(context, it)
                                        }
                                        TodoWidgetProvider.updateAllWidgets(context)
                                        val actions = listOf(TalkAction.AskQuery("/summary", "📊 Daily Summary"), TalkAction.AskQuery("/advice", "💡 Focus Advice"))
                                        return TalkResponse("🎉 **All ${pending.size} tasks marked complete!** Entire radar is clear. Outstanding work!", actions, "tasks", serializeActionsJson("tasks", actions))
                                    } else if (nluResult.intent == NluIntent.DELETE) {
                                        pending.forEach { 
                                            app.taskRepository.deleteTask(it)
                                            TaskReminderHelper.cancelReminder(context, it)
                                        }
                                        TodoWidgetProvider.updateAllWidgets(context)
                                        return TalkResponse("🗑️ **Deleted all ${pending.size} pending tasks.**")
                                    }
                                } else if (nluResult.targetTask != null) {
                                    val targetTask = nluResult.targetTask
                                    if (nluResult.intent == NluIntent.COMPLETE) {
                                        val completed = targetTask.copy(isCompleted = true, completedAt = System.currentTimeMillis())
                                        app.taskRepository.updateTask(completed)
                                        TaskReminderHelper.cancelReminder(context, targetTask)
                                        TodoWidgetProvider.updateAllWidgets(context)
                                        val remaining = pending.size - 1
                                        val praise = AyvaDialogueEngine.getTaskCompletedPraise(context, targetTask.title, remaining)
                                        val actions = listOf(
                                            TalkAction.AskQuery("/tasks", "📋 Tasks ($remaining)"),
                                            TalkAction.AskQuery("/advice", "💡 Focus Advice")
                                        )
                                        return TalkResponse(praise, actions, "tasks", serializeActionsJson("tasks", actions))
                                    } else if (nluResult.intent == NluIntent.DELETE) {
                                        app.taskRepository.deleteTask(targetTask)
                                        TaskReminderHelper.cancelReminder(context, targetTask)
                                        TodoWidgetProvider.updateAllWidgets(context)
                                        val remaining = pending.size - 1
                                        val actions = listOf(
                                            TalkAction.AskQuery("/tasks", "📋 View Tasks ($remaining)")
                                        )
                                        return TalkResponse("🗑️ **Deleted '${targetTask.title}'**\nTask removed from your radar.", actions, "tasks", serializeActionsJson("tasks", actions))
                                    } else if (nluResult.intent == NluIntent.RESCHEDULE) {
                                        if (nluResult.targetDateMs != null) {
                                            // If user did not specify an explicit time of day, but the task already had a time, preserve that time
                                            val newDate = if (!nluResult.hasExplicitTimeSpecified && targetTask.dueDate != null) {
                                                val prevCal = java.util.Calendar.getInstance().apply { timeInMillis = targetTask.dueDate }
                                                val targetCal = java.util.Calendar.getInstance().apply { timeInMillis = nluResult.targetDateMs }
                                                targetCal.set(java.util.Calendar.HOUR_OF_DAY, prevCal.get(java.util.Calendar.HOUR_OF_DAY))
                                                targetCal.set(java.util.Calendar.MINUTE, prevCal.get(java.util.Calendar.MINUTE))
                                                targetCal.set(java.util.Calendar.SECOND, 0)
                                                targetCal.set(java.util.Calendar.MILLISECOND, 0)
                                                targetCal.timeInMillis
                                            } else {
                                                nluResult.targetDateMs
                                            }
                                            val updated = targetTask.copy(dueDate = newDate)
                                            app.taskRepository.updateTask(updated)
                                            TaskReminderHelper.scheduleReminder(context, updated)
                                            TodoWidgetProvider.updateAllWidgets(context)
                                            val dateStr = SmartDateParser.formatDueDate(newDate)
                                            val actions = listOf(
                                                TalkAction.AskQuery("/tasks", "📋 View Tasks"),
                                                TalkAction.AskQuery("/talk complete ${targetTask.title}", "✅ Mark Complete")
                                            )
                                            return TalkResponse("⏰ **Rescheduled '${targetTask.title}'**\nNew due date: **$dateStr**", actions, "tasks", serializeActionsJson("tasks", actions))
                                        } else {
                                            // No date or time specified, prompt user with quick reschedule chips
                                            val actions = listOf(
                                                TalkAction.AskQuery("/talk reschedule ${targetTask.title} tomorrow at 9am", "⏰ Tomorrow 9am"),
                                                TalkAction.AskQuery("/talk reschedule ${targetTask.title} tomorrow at 5pm", "⏰ Tomorrow 5pm"),
                                                TalkAction.AskQuery("/talk reschedule ${targetTask.title} tonight at 8pm", "⏰ Tonight 8pm"),
                                                TalkAction.AskQuery("/talk reschedule ${targetTask.title} in 2 hours", "⏰ In 2 Hours")
                                            )
                                            return TalkResponse(
                                                formattedText = "⏰ **When would you like to reschedule '${targetTask.title}' to?**\n\n_Tap an option below or specify: `tomorrow at 5pm`, `in 30 mins`, or `friday 10am`._",
                                                actions = actions,
                                                topicId = "tasks",
                                                jsonPayload = serializeActionsJson("tasks", actions)
                                            )
                                        }
                                    }
                                } else {
                                    if (pending.isEmpty()) {
                                        return TalkResponse("🎯 **No Pending Tasks!**\nYou don't have any active tasks on your radar right now.", listOf(TalkAction.AskQuery("/tasks", "📋 Task History")), "tasks", null)
                                    } else {
                                        val verb = if (nluResult.intent == NluIntent.COMPLETE) "complete" else if (nluResult.intent == NluIntent.DELETE) "delete" else "reschedule"
                                        val candidateList = if (nluResult.matchingTasks.isNotEmpty()) nluResult.matchingTasks else pending
                                        val hasQuery = !nluResult.filterQuery.isNullOrBlank()
                                        val whenSuffix = if (nluResult.targetDateMs != null) " " + SmartDateParser.formatDueDate(nluResult.targetDateMs) else ""

                                        if (hasQuery && nluResult.matchingTasks.isEmpty()) {
                                            // User searched for a keyword that did not match any task
                                            val actions = pending.take(3).mapIndexed { idx, t ->
                                                TalkAction.AskQuery("/talk $verb ${t.title}$whenSuffix", "⏰ ${idx + 1}. ${t.title.take(30)}")
                                            }
                                            val listText = pending.take(5).mapIndexed { idx, t -> "${idx + 1}. **${t.title}**" }.joinToString("\n")
                                            return TalkResponse(
                                                formattedText = "🤔 **No active tasks found matching '${nluResult.filterQuery}'.**\n\nHere are your current active tasks:\n\n$listText\n\n_Tap an option below or specify: `/talk $verb [number or task name]`._",
                                                actions = actions,
                                                topicId = "tasks",
                                                jsonPayload = serializeActionsJson("tasks", actions)
                                            )
                                        }

                                        val headerText = if (hasQuery && nluResult.matchingTasks.size > 1) {
                                            "🤔 **Found ${nluResult.matchingTasks.size} tasks matching '${nluResult.filterQuery}':**"
                                        } else {
                                            "🤔 **Which task would you like to $verb?**"
                                        }

                                        val actions = when (nluResult.intent) {
                                            NluIntent.COMPLETE -> candidateList.take(3).mapIndexed { idx, t ->
                                                TalkAction.AskQuery("/talk complete ${t.title}", "✅ ${idx + 1}. ${t.title.take(30)}")
                                            } + listOf(TalkAction.AskQuery("/talk complete all", "✅ Complete All"))
                                            NluIntent.RESCHEDULE -> candidateList.take(3).mapIndexed { idx, t ->
                                                val targetWhen = if (whenSuffix.isNotBlank()) whenSuffix else " tomorrow"
                                                TalkAction.AskQuery("/talk reschedule ${t.title}$targetWhen", "⏰ ${idx + 1}. ${t.title.take(30)}")
                                            } + listOf(TalkAction.AskQuery("/talk reschedule all${if (whenSuffix.isNotBlank()) whenSuffix else " tomorrow"}", "⏰ All Tomorrow"))
                                            NluIntent.DELETE -> candidateList.take(3).mapIndexed { idx, t ->
                                                TalkAction.AskQuery("/talk delete ${t.title}", "🗑️ ${idx + 1}. ${t.title.take(30)}")
                                            }
                                            else -> emptyList()
                                        }
                                        val listText = candidateList.take(5).mapIndexed { idx, t -> "${idx + 1}. **${t.title}**" }.joinToString("\n")
                                        return TalkResponse(
                                            formattedText = "$headerText\n\n$listText\n\n_Tap an option below or specify: `/talk $verb [number or task name]`._",
                                            actions = actions,
                                            topicId = "tasks",
                                            jsonPayload = serializeActionsJson("tasks", actions)
                                        )
                                    }
                                }
                            }
                            
                            if (nluResult.intent == NluIntent.START_ROUTINE || nluResult.intent == NluIntent.STOP_ROUTINE || nluResult.intent == NluIntent.LIST_ROUTINES) {
                                val isStart = (nluResult.intent == NluIntent.START_ROUTINE)
                                val isStop = (nluResult.intent == NluIntent.STOP_ROUTINE)
                                val isList = (nluResult.intent == NluIntent.LIST_ROUTINES)
                                val allSchedules = app.database.scheduleDao().getAllSchedulesSync()
                                
                                if (allSchedules.isEmpty()) {
                                    val actions = listOf(TalkAction.NavigateAppScreen("schedules", "Create Routine", "📅"))
                                    return TalkResponse(
                                        formattedText = "🗓️ **No Routines Found**\n\nYou haven't created any focus routines yet. Set up scheduled routines to automatically block distracting apps during specific hours.",
                                        actions = actions,
                                        topicId = "schedules",
                                        jsonPayload = serializeActionsJson("schedules", actions)
                                    )
                                }

                                if (isStart) {
                                    val target = nluResult.targetRoutineName?.trim()
                                    val isAll = nluResult.isAllTasks || target == "all" || cleanQuery.contains("all routines") || cleanQuery.contains("all schedules")
                                    
                                    if (isAll) {
                                        allSchedules.forEach {
                                            app.database.scheduleDao().insertSchedule(it.copy(isEnabled = true))
                                        }
                                        val actions = listOf(
                                            TalkAction.AskQuery("/talk stop all routines", "⏹️ Stop All"),
                                            TalkAction.NavigateAppScreen("schedules", "View Routines", "📅")
                                        )
                                        val routineListText = allSchedules.mapIndexed { idx, it ->
                                            val startStr = String.format("%02d:%02d", it.startHour, it.startMinute)
                                            val endStr = String.format("%02d:%02d", it.endHour, it.endMinute)
                                            "${idx + 1}. **${it.name}** ($startStr - $endStr) • *Enabled*"
                                        }.joinToString("\n")
                                        return TalkResponse(
                                            formattedText = "▶️ **All Routines Started (${allSchedules.size})**\n\nAll focus routines are now active and enabled:\n$routineListText",
                                            actions = actions,
                                            topicId = "schedules",
                                            jsonPayload = serializeActionsJson("schedules", actions)
                                        )
                                    } else if (!target.isNullOrBlank()) {
                                        val matched = allSchedules.find { it.name.equals(target, ignoreCase = true) }
                                            ?: allSchedules.find { it.name.contains(target, ignoreCase = true) || target.contains(it.name, ignoreCase = true) }
                                            ?: allSchedules.minByOrNull { OfflineNluEngine.levenshtein(target.lowercase(), it.name.lowercase()) }
                                                ?.takeIf { OfflineNluEngine.levenshtein(target.lowercase(), it.name.lowercase()) <= 2 }

                                        if (matched != null) {
                                            app.database.scheduleDao().insertSchedule(matched.copy(isEnabled = true))
                                            val startStr = String.format("%02d:%02d", matched.startHour, matched.startMinute)
                                            val endStr = String.format("%02d:%02d", matched.endHour, matched.endMinute)
                                            val appCount = if (matched.appsToBlock.isNotBlank()) matched.appsToBlock.split(",").filter { s -> s.isNotBlank() }.size else 0
                                            val appSummary = if (appCount > 0) "$appCount apps restricted" else "No apps restricted"
                                            
                                            val actions = listOf(
                                                TalkAction.AskQuery("/talk stop routine ${matched.name}", "⏹️ Stop Routine"),
                                                TalkAction.NavigateAppScreen("schedules", "View Routines", "📅")
                                            )
                                            return TalkResponse(
                                                formattedText = "▶️ **Routine Started: ${matched.name}**\n\n• **Schedule**: $startStr - $endStr\n• **Restrictions**: $appSummary\n• **Mode**: ${matched.mode} Mode\n• **Status**: **Enabled & Active** ⚡",
                                                actions = actions,
                                                topicId = "schedules",
                                                jsonPayload = serializeActionsJson("schedules", actions)
                                            )
                                        } else {
                                            val actions = allSchedules.map {
                                                TalkAction.AskQuery("/talk start routine ${it.name}", "▶️ Start ${it.name}")
                                            } + listOf(TalkAction.NavigateAppScreen("schedules", "Open Routines", "📅"))
                                            return TalkResponse(
                                                formattedText = "🤔 Couldn't find routine *\"$target\"*. Which routine would you like to start?",
                                                actions = actions,
                                                topicId = "schedules",
                                                jsonPayload = serializeActionsJson("schedules", actions)
                                            )
                                        }
                                    } else {
                                        val actions = allSchedules.map {
                                            TalkAction.AskQuery("/talk start routine ${it.name}", "▶️ Start ${it.name}")
                                        } + listOf(
                                            TalkAction.AskQuery("/talk start all routines", "▶️ Start All"),
                                            TalkAction.NavigateAppScreen("schedules", "Open Routines", "📅")
                                        )
                                        val routineListText = allSchedules.mapIndexed { idx, it ->
                                            val startStr = String.format("%02d:%02d", it.startHour, it.startMinute)
                                            val endStr = String.format("%02d:%02d", it.endHour, it.endMinute)
                                            val statusStr = if (it.isEnabled) "⚡ Active" else "⏸️ Paused"
                                            "${idx + 1}. **${it.name}** ($startStr - $endStr) — $statusStr"
                                        }.joinToString("\n")
                                        return TalkResponse(
                                            formattedText = "🗓️ **Select a Routine to Start:**\n\n$routineListText\n\n_Tap a button below or type `/talk start routine [name]`._",
                                            actions = actions,
                                            topicId = "schedules",
                                            jsonPayload = serializeActionsJson("schedules", actions)
                                        )
                                    }
                                }

                                if (isStop) {
                                    val target = nluResult.targetRoutineName?.trim()
                                    val isAll = nluResult.isAllTasks || target == "all" || cleanQuery.contains("all routines") || cleanQuery.contains("all schedules")
                                    
                                    if (isAll) {
                                        allSchedules.forEach {
                                            app.database.scheduleDao().insertSchedule(it.copy(isEnabled = false))
                                        }
                                        val actions = listOf(
                                            TalkAction.AskQuery("/talk start all routines", "▶️ Start All"),
                                            TalkAction.NavigateAppScreen("schedules", "View Routines", "📅")
                                        )
                                        return TalkResponse(
                                            formattedText = "⏹️ **All Routines Stopped (${allSchedules.size})**\n\nAll focus schedules have been disabled and their app restrictions are paused.",
                                            actions = actions,
                                            topicId = "schedules",
                                            jsonPayload = serializeActionsJson("schedules", actions)
                                        )
                                    } else if (!target.isNullOrBlank()) {
                                        val matched = allSchedules.find { it.name.equals(target, ignoreCase = true) }
                                            ?: allSchedules.find { it.name.contains(target, ignoreCase = true) || target.contains(it.name, ignoreCase = true) }
                                            ?: allSchedules.minByOrNull { OfflineNluEngine.levenshtein(target.lowercase(), it.name.lowercase()) }
                                                ?.takeIf { OfflineNluEngine.levenshtein(target.lowercase(), it.name.lowercase()) <= 2 }

                                        if (matched != null) {
                                            app.database.scheduleDao().insertSchedule(matched.copy(isEnabled = false))
                                            val actions = listOf(
                                                TalkAction.AskQuery("/talk start routine ${matched.name}", "▶️ Start Routine"),
                                                TalkAction.NavigateAppScreen("schedules", "View Routines", "📅")
                                            )
                                            return TalkResponse(
                                                formattedText = "⏹️ **Routine Stopped: ${matched.name}**\n\n_The routine has been disabled and its scheduled app blocks are now paused._",
                                                actions = actions,
                                                topicId = "schedules",
                                                jsonPayload = serializeActionsJson("schedules", actions)
                                            )
                                        } else {
                                            val actions = allSchedules.filter { it.isEnabled }.map {
                                                TalkAction.AskQuery("/talk stop routine ${it.name}", "⏹️ Stop ${it.name}")
                                            } + listOf(TalkAction.NavigateAppScreen("schedules", "Open Routines", "📅"))
                                            return TalkResponse(
                                                formattedText = "🤔 Couldn't find routine *\"$target\"*. Which routine would you like to stop?",
                                                actions = actions,
                                                topicId = "schedules",
                                                jsonPayload = serializeActionsJson("schedules", actions)
                                            )
                                        }
                                    } else {
                                        val enabledSchedules = allSchedules.filter { it.isEnabled }
                                        val actions = (if (enabledSchedules.isNotEmpty()) enabledSchedules else allSchedules).map {
                                            TalkAction.AskQuery("/talk stop routine ${it.name}", "⏹️ Stop ${it.name}")
                                        } + listOf(
                                            TalkAction.AskQuery("/talk stop all routines", "⏹️ Stop All"),
                                            TalkAction.NavigateAppScreen("schedules", "Open Routines", "📅")
                                        )
                                        val routineListText = allSchedules.mapIndexed { idx, it ->
                                            val startStr = String.format("%02d:%02d", it.startHour, it.startMinute)
                                            val endStr = String.format("%02d:%02d", it.endHour, it.endMinute)
                                            val statusStr = if (it.isEnabled) "⚡ Active" else "⏸️ Paused"
                                            "${idx + 1}. **${it.name}** ($startStr - $endStr) — $statusStr"
                                        }.joinToString("\n")
                                        return TalkResponse(
                                            formattedText = "🗓️ **Select a Routine to Stop:**\n\n$routineListText\n\n_Tap a button below or type `/talk stop routine [name]`._",
                                            actions = actions,
                                            topicId = "schedules",
                                            jsonPayload = serializeActionsJson("schedules", actions)
                                        )
                                    }
                                }

                                if (isList) {
                                    val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
                                    val actions = mutableListOf<TalkAction>()
                                    allSchedules.forEach {
                                        if (it.isEnabled) {
                                            actions.add(TalkAction.AskQuery("/talk stop routine ${it.name}", "⏹️ Stop ${it.name}"))
                                        } else {
                                            actions.add(TalkAction.AskQuery("/talk start routine ${it.name}", "▶️ Start ${it.name}"))
                                        }
                                    }
                                    actions.add(TalkAction.NavigateAppScreen("schedules", "Manage Routines", "📅"))
                                    
                                    val sb = StringBuilder()
                                    sb.append("🗓️ **Your Focus Routines (${allSchedules.size})**:\n\n")
                                    allSchedules.forEachIndexed { idx, it ->
                                        val startStr = String.format("%02d:%02d", it.startHour, it.startMinute)
                                        val endStr = String.format("%02d:%02d", it.endHour, it.endMinute)
                                        val statusStr = if (it.isEnabled) "⚡ **Enabled**" else "⏸️ *Paused*"
                                        val appCount = if (it.appsToBlock.isNotBlank()) it.appsToBlock.split(",").filter { s -> s.isNotBlank() }.size else 0
                                        val appStr = if (appCount > 0) "$appCount apps restricted" else "No apps restricted"
                                        sb.append("${idx + 1}. **${it.name}** ($startStr - $endStr) — $statusStr\n   └ $appStr [${it.mode} Mode]\n")
                                    }
                                    sb.append("\n_Use `/talk start routine [name]` or `/talk stop routine [name]` to control them._")
                                    return TalkResponse(sb.toString().trimEnd(), actions, "schedules", serializeActionsJson("schedules", actions))
                                }
                            }
                            
                            if (nluResult.intent == NluIntent.BLOCK_APP || nluResult.intent == NluIntent.BLOCK_FILTER || nluResult.intent == NluIntent.UNBLOCK) {
                                val isUnblock = (nluResult.intent == NluIntent.UNBLOCK)
                                val target = nluResult.targetFilterOrAppName ?: ""
                                val mode = nluResult.blockMode ?: "HARD"
                                val dao = app.database.appRestrictionDao()
                                
                                val pm = context.packageManager
                                val installedApps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA).mapNotNull {
                                    val name = pm.getApplicationLabel(it).toString()
                                    if (name.isNotBlank() && it.packageName != context.packageName) {
                                        val category = com.focusbyrj.app.ui.screens.getCategoryForApp(it, it.packageName)
                                        com.focusbyrj.app.ui.screens.AppInfo(name, it.packageName, category)
                                    } else null
                                }
                                
                                val matchedApp = installedApps.filter { appInfo ->
                                    val appNameLower = appInfo.name.lowercase()
                                    val targetLower = target.lowercase()
                                    targetLower.contains(appNameLower) ||
                                    appNameLower.contains(targetLower) ||
                                    OfflineNluEngine.levenshtein(targetLower, appNameLower) <= 1 ||
                                    appNameLower.split(Regex("\\s+")).any { part -> part.length > 2 && (part == targetLower || targetLower.contains(part)) }
                                }.maxByOrNull { it.name.length }
                                val customCats = CustomCategoryManager.getCategories(context)
                                val matchedCustomCat = customCats.find { target.contains(it.name, ignoreCase = true) || OfflineNluEngine.levenshtein(target.lowercase(), it.name.lowercase()) <= 1 }
                                
                                val standardCatMapping = mapOf(
                                    "social" to com.focusbyrj.app.ui.screens.AppCategory.SOCIAL,
                                    "finance" to com.focusbyrj.app.ui.screens.AppCategory.PAYMENT,
                                    "fianance" to com.focusbyrj.app.ui.screens.AppCategory.PAYMENT,
                                    "payment" to com.focusbyrj.app.ui.screens.AppCategory.PAYMENT,
                                    "shopping" to com.focusbyrj.app.ui.screens.AppCategory.SHOPPING,
                                    "game" to com.focusbyrj.app.ui.screens.AppCategory.GAMES,
                                    "games" to com.focusbyrj.app.ui.screens.AppCategory.GAMES,
                                    "utility" to com.focusbyrj.app.ui.screens.AppCategory.UTILITY,
                                    "utilities" to com.focusbyrj.app.ui.screens.AppCategory.UTILITY,
                                    "other" to com.focusbyrj.app.ui.screens.AppCategory.OTHERS,
                                    "others" to com.focusbyrj.app.ui.screens.AppCategory.OTHERS
                                )
                                val matchedStandardCatWord = standardCatMapping.keys.find { target.contains(it, ignoreCase = true) }
                                val matchedStandardCat = matchedStandardCatWord?.let { standardCatMapping[it] }
                                
                                if (isUnblock) {
                                    if ((cleanQuery.contains("all") || target.isEmpty()) && !cleanQuery.contains("filter")) {
                                        dao.deleteAllRestrictions()
                                        return TalkResponse("🔓 **Lock Protocol Lifted**\n_All applications have been unlocked._")
                                    }
                                    
                                    if (matchedStandardCat != null) {
                                        val targetApps = installedApps.filter { it.category == matchedStandardCat }
                                        dao.deleteRestrictions(targetApps.map { it.packageName })
                                        return TalkResponse("🔓 **Lock Protocol Lifted**\n_Unlocked all ${matchedStandardCat.title} apps._")
                                    } else if (matchedCustomCat != null) {
                                        val targetApps = installedApps.filter { matchedCustomCat.packages.contains(it.packageName) }
                                        dao.deleteRestrictions(targetApps.map { it.packageName })
                                        return TalkResponse("🔓 **Lock Protocol Lifted**\n_Unlocked all apps in '${matchedCustomCat.name}' filter._")
                                    } else if (matchedApp != null) {
                                        dao.deleteRestriction(matchedApp.packageName)
                                        return TalkResponse("🔓 **Lock Protocol Lifted**\n_Unlocked ${matchedApp.name}._")
                                    } else {
                                        val activeRestrictions = dao.getAllRestrictions().firstOrNull()?.filter { it.isRestricted } ?: emptyList()
                                        if (activeRestrictions.isEmpty()) {
                                            return TalkResponse("🔓 **No apps are currently restricted.** Everything is already unlocked.")
                                        } else {
                                            val actions = activeRestrictions.take(4).map {
                                                TalkAction.AskQuery("/talk unblock ${it.appName}", "🔓 Unlock ${it.appName}")
                                            } + listOf(TalkAction.AskQuery("/talk unblock all", "🔓 Unlock All"))
                                            val listText = activeRestrictions.take(6).joinToString("\n") { "• **${it.appName}**" }
                                            return TalkResponse(
                                                formattedText = "🤔 Couldn't find a locked app matching *\"$target\"*.\n\nCurrently locked apps:\n$listText\n\n_Tap below or type `/talk unblock [app name]`._",
                                                actions = actions,
                                                topicId = "lock_protocol",
                                                jsonPayload = serializeActionsJson("lock_protocol", actions)
                                            )
                                        }
                                    }
                                } else {
                                    if ((cleanQuery.contains("all") || target.isEmpty()) && !cleanQuery.contains("filter")) {
                                        val targetApps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA).filter {
                                            (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName
                                        }.map {
                                            val name = pm.getApplicationLabel(it).toString()
                                            com.focusbyrj.app.data.AppRestriction(it.packageName, name, isRestricted = true, mode = mode, restrictionMode = "SIMPLE")
                                        }
                                        dao.insertRestrictions(targetApps)
                                        return TalkResponse("🔒 **Lock Protocol Active**\n_Locked all non-system apps [$mode]._")
                                    } else if (matchedStandardCat != null) {
                                        val targetApps = installedApps.filter { it.category == matchedStandardCat }
                                        val restrictions = targetApps.map {
                                            com.focusbyrj.app.data.AppRestriction(it.packageName, it.name, isRestricted = true, mode = mode, restrictionMode = "SIMPLE")
                                        }
                                        dao.insertRestrictions(restrictions)
                                        return TalkResponse("🔒 **Lock Protocol Active**\n_Locked all ${matchedStandardCat.title} apps [$mode]._")
                                    } else if (matchedCustomCat != null) {
                                        val targetApps = installedApps.filter { matchedCustomCat.packages.contains(it.packageName) }
                                        val restrictions = targetApps.map {
                                            com.focusbyrj.app.data.AppRestriction(it.packageName, it.name, isRestricted = true, mode = mode, restrictionMode = "SIMPLE")
                                        }
                                        dao.insertRestrictions(restrictions)
                                        return TalkResponse("🔒 **Lock Protocol Active**\n_Locked all apps in '${matchedCustomCat.name}' filter [$mode]._")
                                    } else if (matchedApp != null) {
                                        dao.insertRestriction(
                                            com.focusbyrj.app.data.AppRestriction(matchedApp.packageName, matchedApp.name, isRestricted = true, mode = mode, restrictionMode = "SIMPLE")
                                        )
                                        return TalkResponse("🔒 **Lock Protocol Active**\n_Locked ${matchedApp.name} [$mode]._")
                                    } else {
                                        val suggestedApps = installedApps.take(4)
                                        val actions = suggestedApps.map {
                                            TalkAction.AskQuery("/talk block ${it.name}", "🔒 Block ${it.name}")
                                        } + listOf(TalkAction.NavigateAppScreen("dashboard", "Manage Blocks", "🛡️"))
                                        val promptStr = if (target.isNotBlank()) "Couldn't find an installed app matching *\"$target\"*." else "Which app would you like to restrict?"
                                        return TalkResponse(
                                            formattedText = "📱 $promptStr\n\nHere are some apps installed on your device that you can lock:\n" + suggestedApps.joinToString("\n") { "• ${it.name}" } + "\n\n_Tap a button below or type `/talk block [app name]`._",
                                            actions = actions,
                                            topicId = "lock_protocol",
                                            jsonPayload = serializeActionsJson("lock_protocol", actions)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // --- 1. Follow-up & Pronoun Resolution ('it', 'how to change it', 'where is it', 'why') ---
        val resolvedQuery = if (isFollowUpQuery(cleanQuery)) {
            val prevTopicId = conversationHistory.lastOrNull { it.topicId != null }?.topicId ?: lastQueriedTopicId
            if (prevTopicId != null) {
                val lastTopic = getTopicsDatabase(context).find { it.id == prevTopicId }
                lastTopic?.id ?: cleanQuery
            } else {
                cleanQuery
            }
        } else {
            cleanQuery
        }

        // --- 2. Direct ID match ---
        val idMatch = getTopicsDatabase(context).find { it.id.equals(resolvedQuery, ignoreCase = true) }
        if (idMatch != null) {
            lastQueriedTopicId = idMatch.id
            recordTurn(cleanQuery, idMatch.id)
            val actions = idMatch.getActions(context)
            val json = serializeActionsJson(idMatch.id, actions)
            return TalkResponse(idMatch.formatResponse(context), actions, idMatch.id, json)
        }

        // --- 3. Stop-Word Filtered Semantic & Fuzzy Scoring ---
        val stopWords = setOf(
            "the", "a", "an", "is", "are", "was", "were", "in", "on", "at", "to", "for", "of", "with",
            "how", "why", "what", "where", "who", "which", "do", "does", "did", "can", "could", "should",
            "would", "i", "you", "my", "your", "me", "it", "its", "app", "mode", "screen", "setting", "settings"
        )

        val scoredTopics = mutableListOf<Pair<SpecificTopic, Int>>()
        val queryWords = expandTokens(cleanQuery)
        val meaningfulQueryWords = queryWords.filter { !stopWords.contains(it) && it.length >= 3 }

        for (topic in getTopicsDatabase(context)) {
            var score = 0
            val titleLower = topic.title.lowercase()
            val idLower = topic.id.lowercase()

            if (cleanQuery.contains(titleLower) || titleLower.contains(cleanQuery)) {
                score += 90
            }

            for (kw in topic.keywords) {
                val kwLower = kw.lowercase()
                if (cleanQuery == kwLower || cleanQuery.contains(kwLower)) {
                    score += 60
                } else if (kwLower.contains(cleanQuery) && cleanQuery.length >= 4) {
                    score += 40
                } else {
                    for (qWord in meaningfulQueryWords) {
                        for (kwWord in kwLower.split(" ")) {
                            if (!stopWords.contains(kwWord)) {
                                if (qWord == kwWord) {
                                    score += 25
                                } else if (isFuzzyMatchWord(qWord, kwWord)) {
                                    score += 15
                                }
                            }
                        }
                    }
                }
            }

            for (word in meaningfulQueryWords) {
                if (titleLower.contains(word)) score += 20
                if (idLower.contains(word)) score += 15
                if (isFuzzyMatchWord(word, idLower)) score += 10
            }

            if (score > 0) {
                scoredTopics.add(Pair(topic, score))
            }
        }
        
        scoredTopics.sortByDescending { it.second }
        var bestTopic = scoredTopics.firstOrNull()?.first
        var highestScore = scoredTopics.firstOrNull()?.second ?: 0
        
        // --- 3.5 Disambiguation (Only if candidate scores are close and significant) ---
        if (bestTopic != null && highestScore in 25..55 && scoredTopics.size > 1) {
            val secondScore = scoredTopics[1].second
            if (highestScore - secondScore < 10) {
                val suggestions = scoredTopics.take(3).map { it.first }
                val actions = suggestions.map { TalkAction.AskQuery(it.title, it.title) }
                val json = serializeActionsJson("disambiguation", actions)
                return TalkResponse(
                    formattedText = "🤔 I found a few topics that might match your question. Which one would you like to explore?",
                    actions = actions,
                    topicId = "disambiguation",
                    jsonPayload = json
                )
            }
        }

        // --- 4. INTENT EXTRACTION (e.g. "set wait timer to 15", "freeze streak", "turn on vacation mode") ---
        val hasIntentPrefix = cleanQuery.startsWith("set ") || cleanQuery.startsWith("change ") || 
            cleanQuery.startsWith("turn ") || cleanQuery.startsWith("toggle ") || 
            cleanQuery.startsWith("enable ") || cleanQuery.startsWith("disable ") || 
            cleanQuery.startsWith("freeze ") || cleanQuery.startsWith("unfreeze ") || 
            cleanQuery.startsWith("activate ") || cleanQuery.startsWith("deactivate ") ||
            cleanQuery.contains("set ") || cleanQuery.contains("turn ") || 
            cleanQuery.contains("change ") || cleanQuery.contains("toggle ") ||
            cleanQuery.contains("freeze") || cleanQuery.contains("unfreeze") ||
            cleanQuery.contains("vacation")

        if (bestTopic != null && hasIntentPrefix && context != null && !isQuestionQuery) {
            val isBooleanTurnOn = cleanQuery.contains(" on") || cleanQuery.contains("enable") || cleanQuery.contains("true") || cleanQuery.contains("activate") || cleanQuery.contains("freeze") || cleanQuery.startsWith("freeze")
            val isBooleanTurnOff = cleanQuery.contains(" off") || cleanQuery.contains("disable") || cleanQuery.contains("false") || cleanQuery.contains("deactivate") || cleanQuery.contains("unfreeze") || cleanQuery.startsWith("unfreeze")
            
            var num = cleanQuery.replace(Regex("[^0-9]"), "").toIntOrNull()
            var stringVal: String? = null
            
            if (bestTopic.prefType == "string") {
                val timeRegex = Regex("([0-9]{1,2})[:.]([0-9]{2})\\s*(am|pm)?", RegexOption.IGNORE_CASE)
                val timeMatch = timeRegex.find(cleanQuery)
                if (timeMatch != null) {
                    val hr = timeMatch.groupValues[1].toIntOrNull() ?: 12
                    val min = timeMatch.groupValues[2]
                    val ampm = (timeMatch.groupValues[3].uppercase().takeIf { it.isNotBlank() } ?: "AM")
                    stringVal = String.format("%02d:%s %s", hr, min, ampm)
                } else if (cleanQuery.contains("dark")) stringVal = "dark"
                else if (cleanQuery.contains("light")) stringVal = "light"
                else if (cleanQuery.contains("system")) stringVal = "system"
                else if (cleanQuery.contains("banner") || cleanQuery.contains("pop")) stringVal = "Banner"
                else if (cleanQuery.contains("silent")) stringVal = "Silent"
                else if (cleanQuery.contains("both")) stringVal = "Both"
                else if (cleanQuery.contains("dashboard")) stringVal = "dashboard"
                else if (cleanQuery.contains("time") || cleanQuery.contains("analytics")) stringVal = "time"
                else if (cleanQuery.contains("account") || cleanQuery.contains("profile")) stringVal = "account"
                else if (cleanQuery.contains("schedule")) stringVal = "schedules"
            }
            
            if (num != null && bestTopic.prefKey != null && bestTopic.prefType == "int") {
                if ((cleanQuery.contains("hour") || cleanQuery.contains("hr")) && num < 24) {
                    num *= 60
                }
                val displayVal = if (num >= 60 && num % 60 == 0) "${num / 60} hour(s)" else if (bestTopic.id.contains("minute")) "$num minute(s)" else if (bestTopic.id.contains("hour")) "$num hour(s)" else "$num"
                
                val pendingJson = JSONObject().apply {
                    put("topicId", bestTopic!!.id)
                    put("prefKey", bestTopic!!.prefKey)
                    put("prefType", "int")
                    put("value", num.toString())
                    put("title", bestTopic!!.title)
                    put("displayVal", displayVal)
                    put("status", "pending")
                }.toString()
                
                return TalkResponse(
                    formattedText = "Do you want to change *${bestTopic.title}* to *$displayVal*?",
                    jsonPayload = pendingJson
                )
            } else if ((isBooleanTurnOn || isBooleanTurnOff) && bestTopic.prefKey != null && bestTopic.prefType == "boolean") {
                val finalBool = isBooleanTurnOn
                val actionStr = if (finalBool) "Enable" else "Disable"
                
                val pendingJson = JSONObject().apply {
                    put("topicId", bestTopic!!.id)
                    put("prefKey", bestTopic!!.prefKey)
                    put("prefType", "boolean")
                    put("value", finalBool.toString())
                    put("title", bestTopic!!.title)
                    put("displayVal", if(finalBool) "Enabled" else "Disabled")
                    put("status", "pending")
                }.toString()
                
                return TalkResponse(
                    formattedText = "Do you want to *$actionStr* *${bestTopic.title}*?",
                    jsonPayload = pendingJson
                )
            } else if (stringVal != null && bestTopic.prefKey != null && bestTopic.prefType == "string") {
                val pendingJson = JSONObject().apply {
                    put("topicId", bestTopic!!.id)
                    put("prefKey", bestTopic!!.prefKey)
                    put("prefType", "string")
                    put("value", stringVal)
                    put("title", bestTopic!!.title)
                    put("displayVal", stringVal)
                    put("status", "pending")
                }.toString()
                
                return TalkResponse(
                    formattedText = "Do you want to change *${bestTopic.title}* to *$stringVal*?",
                    jsonPayload = pendingJson
                )
            }
        }

        if (bestTopic != null && highestScore >= 12) {
            lastQueriedTopicId = bestTopic.id
            recordTurn(cleanQuery, bestTopic.id)
            val actions = bestTopic.getActions(context)
            val json = serializeActionsJson(bestTopic.id, actions)
            return TalkResponse(bestTopic.formatResponse(context), actions, bestTopic.id, json)
        }

        // --- 5. SMART CONVERSATIONAL FALLBACK (ALWAYS ANSWERS CONVERSATIONALLY) ---
        recordTurn(cleanQuery, "fallback")
        val isConversationalQuery = cleanQuery in listOf(
            "hi", "hello", "hey", "ayva", "hey ayva", "hi ayva", "good morning", "good evening", "good afternoon",
            "how are you", "how're you", "how are you doing", "what's up", "whats up",
            "thank you", "thanks", "thanks ayva", "thank you ayva",
            "who are you", "what can you do", "what are you", "help me", "joke", "tell me a joke",
            "tired", "i am tired", "exhausted", "burnout", "stressed", "overwhelmed", "motivate me", "inspire me",
            "quote", "pomodoro", "how to focus", "adhd", "sleep", "insomnia", "habit", "routine"
        ) || cleanQuery.startsWith("hi ") || cleanQuery.startsWith("hello ") || cleanQuery.contains("thank") ||
           cleanQuery.contains("tired") || cleanQuery.contains("exhaust") || cleanQuery.contains("stress") ||
           cleanQuery.contains("overwhelm") || cleanQuery.contains("motivat") || cleanQuery.contains("pomodoro") ||
           cleanQuery.contains("adhd") || cleanQuery.contains("distract") || cleanQuery.contains("sleep") ||
           cleanQuery.contains("insomnia") || cleanQuery.contains("habit") || cleanQuery.contains("routine") ||
           cleanQuery.contains("joke")
        
        if (isConversationalQuery) {
            val replyText = AyvaDialogueEngine.getConversationalChatReply(context, cleanQuery)
            val actions = listOf(
                TalkAction.AskQuery("/status", "⚡ Focus Status"),
                TalkAction.AskQuery("/tasks", "📋 My Tasks"),
                TalkAction.AskQuery("/habits", "🌱 Habits"),
                TalkAction.AskQuery("/notes", "📝 Notes")
            )
            val json = serializeActionsJson("chat", actions)
            return TalkResponse(replyText, actions, "chat", json)
        }

        val fallbackActions = listOf(
            TalkAction.AskQuery("/status", "⚡ Focus Status"),
            TalkAction.AskQuery("/tasks", "📋 My Tasks"),
            TalkAction.AskQuery("/habits", "🌱 Daily Habits"),
            TalkAction.AskQuery("/notes", "📝 Keep Notes"),
            TalkAction.NavigateAppScreen("preferences_hub", "Preferences Hub ⚙️", "⚙️"),
            TalkAction.AskQuery("vacation mode", "🏖️ Vacation Mode"),
            TalkAction.AskQuery("why apps not blocking", "🛡️ Troubleshoot")
        )
        val json = serializeActionsJson("fallback", fallbackActions)
        val conversationalGreeting = AyvaDialogueEngine.getConversationalChatReply(context, cleanQuery)
        val fullText = "$conversationalGreeting\n\nI'm ready to help! You can ask me to:\n• **Add / search tasks** (e.g. *\"create finish presentation tomorrow 4pm\"*)\n• **Review notes** (e.g. *\"search notes meeting\"* or *\"add note Ideas: new project\"*)\n• **Track daily habits** (e.g. *\"log habit reading\"* or *\"my habits\"*)\n• **Check focus status** (e.g. *\"status\"* or *\"today's summary\"*)\n• **Lock distracting apps** (e.g. *\"lock instagram in strict mode\"*)"
        return TalkResponse(
            formattedText = fullText,
            actions = fallbackActions,
            topicId = "fallback",
            jsonPayload = json
        )
    }

    /**
     * Backward-compatible simple string response
     */
    suspend fun answerTalkQuery(query: String, context: Context? = null): String {
        return answerTalkQueryWithActions(query, context).formattedText
    }

    private fun isFollowUpQuery(query: String): Boolean {
        val lower = query.lowercase().trim()
        return lower == "it" ||
                lower == "why" ||
                lower == "where" ||
                lower == "how" ||
                lower == "tell me more" ||
                lower == "explain" ||
                lower == "more" ||
                lower == "details" ||
                lower == "what next" ||
                lower.contains("change it") ||
                lower.contains("how to change it") ||
                lower.contains("where is it") ||
                lower.contains("what about it") ||
                lower.contains("how do i change it") ||
                lower.contains("how to adjust it") ||
                lower.contains("tell me more about it") ||
                lower.contains("how does it work")
    }

    fun serializeActionsJson(topicId: String, actions: List<TalkAction>): String? {
        if (actions.isEmpty()) return null
        return try {
            val obj = JSONObject()
            obj.put("topicId", topicId)
            val arr = JSONArray()
            actions.forEach { act ->
                val actObj = JSONObject()
                actObj.put("label", act.label)
                actObj.put("emoji", act.emoji)
                when (act) {
                    is TalkAction.NavigateAppScreen -> {
                        actObj.put("type", "navigate")
                        actObj.put("route", act.route)
                    }
                    is TalkAction.AskQuery -> {
                        actObj.put("type", "ask_query")
                        actObj.put("query", act.query)
                    }
                    is TalkAction.OpenSystemSetting -> {
                        actObj.put("type", "system_setting")
                        actObj.put("action", act.intentAction)
                        actObj.put("packageUri", act.packageUri)
                    }
                    is TalkAction.DirectPrefUpdate -> {
                        actObj.put("type", "pref_update")
                        actObj.put("prefKey", act.prefKey)
                        actObj.put("prefType", act.prefType)
                        actObj.put("targetValue", act.targetValue)
                        actObj.put("displayValue", act.displayValue)
                    }
                    is TalkAction.RoutineToggle -> {
                        actObj.put("type", "routine_toggle")
                        actObj.put("scheduleId", act.scheduleId)
                        actObj.put("isEnabled", act.isEnabled)
                        actObj.put("routineName", act.routineName)
                    }
                }
                arr.put(actObj)
            }
            obj.put("actions", arr)
            obj.toString()
        } catch (e: Exception) {
            null
        }
    }
}
