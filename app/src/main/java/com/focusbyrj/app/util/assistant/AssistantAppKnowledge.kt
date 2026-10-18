package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.util.TalkAction
import com.focusbyrj.app.util.OfflineNluEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Offline app help. Read-only matching is deliberately separate from validated setting writes. */
object AssistantAppKnowledge {
    private fun clean(text: String) = text.lowercase(Locale.ROOT).replace('-', ' ').replace("'", "").replace(Regex("[^\\p{L}\\p{N} :%]"), " ").replace(Regex("\\s+"), " ").trim()
    private fun contains(text: String, phrase: String) = " ${clean(text)} ".contains(" ${clean(phrase)} ")
    @Volatile private var catalogue: List<JSONObject>? = null
    private fun topics(context: Context): List<JSONObject> {
        catalogue?.let { return it }
        val array = JSONArray(context.assets.open("ayva_knowledge_base.json").bufferedReader().use { it.readText() })
        return (0 until array.length()).map { array.getJSONObject(it) }.also { catalogue = it }
    }
    private val followUps = setOf("why", "where", "how", "more", "tell me more", "explain that", "explain it", "how to change it", "how do i change it", "where is it", "where can i find it", "how does it work", "what does it do", "what is it", "what are the options", "what options", "what range", "what is the range", "what is the default", "is it on", "is it off", "what is it set to", "what about it", "what is my current value")
    private val writeStart = Regex("(?i)^(?:(?:please|can you|could you|would you)\\s+)?(?:set|change|switch|turn|enable|disable|activate|deactivate|freeze|unfreeze|make|reset)\\b")
    private fun isWrite(text: String) = writeStart.containsMatchIn(text) && !Regex("(?i)\\b(?:explain|tell me|show me|how|why|whether|if)\\b").containsMatchIn(text)

    // Preserve established direct feature dispatch; questions about these features still use help.
    fun legacyFeature(input: String) = clean(input) in setOf("breathe", "breathing", "calm down", "relax", "take a breath", "deep breath", "mindful break", "screentime", "screen time", "my screen time", "usage", "app usage", "status", "focus status", "how am i doing", "check in", "checkin", "briefing", "posture", "report", "my status", "overview", "focus posture", "daily report")

    fun answer(context: Context, state: AssistantSession, input: String, appMode: Boolean = false): FeatureTurn? {
        if (AssistantCompoundParser.split(input).size > 1) return null
        val text = input.trim().trimEnd('?', '.', '!'); val lower = clean(text)
        if (OfflineNluEngine.isExplicitCreation(text) && !isWrite(text) && AssistantPreferences.find(text)?.aliases?.any { clean(it) == lower } != true) return null
        if (legacyFeature(text) || AssistantLearningLanguage.parse(text, state) != null || lower in setOf("habits", "my habits", "show habits", "show my habits", "routines", "my routines", "list routines", "show routines", "active routines", "which routine is active")) return null
        if (Regex("(?i)^(?:show|check) habit\\s+\\S").containsMatchIn(text)) return null
        if (AssistantFeatureLanguage.isRequest(text) || AssistantTaskLanguage.request(text)?.operation == "persistent") return null
        if (lower in setOf("check device health", "diagnose reminders", "why didnt my reminder arrive", "why are apps not blocking", "diagnose blocking", "check permissions", "backup status", "show backup status", "show my usage", "show my screen time", "explain my streak", "timer status", "focus timer", "time remaining")) return null
        AssistantCapabilities.help(context, state, text)?.let { return it }
        val groups = AssistantSettingGroup.entries.associate { group ->
            group.id to AssistantCapabilities.settings.filter { it.spec.group == group }.map { it.spec.key }
        }
        val group = Regex("^settings (blocking|reminders|bubble|appearance|privacy)$").matchEntire(lower)?.groupValues?.get(1)
        if (lower in setOf("settings", "all settings", "show settings", "show my settings", "what settings can you change", "which settings can you change") || group != null) {
            state.clearPending(); state.switchTopic("app_help")
            if (group == null) return FeatureTurn.Reply("I can read current values and preview changes in these groups: blocking, reminders, bubble, appearance and privacy. Android permissions, vault PINs and backup passwords use their dedicated screens. Which group would you like?", groups.keys.map { TalkAction.AskQuery("/talk settings $it", it.replaceFirstChar(Char::uppercaseChar)) })
            val entries = groups.getValue(group).map { key -> AssistantPreferences.specs.first { it.key == key } }
            return FeatureTurn.Reply("**${group.replaceFirstChar(Char::uppercaseChar)} settings**\n" + entries.joinToString("\n") { "• ${it.title}: ${AssistantPreferences.display(it, AssistantCapabilities.setting(it.key).current(context))}" } + "\nChoose a setting to see what it does and review a change.", entries.map { TalkAction.AskQuery("/talk explain ${it.aliases.first()}", it.title) })
        }
        if (appMode && lower in setOf("help", "features", "what can you do", "what can you help with", "show features", "what else")) {
            state.clearPending(); state.switchTopic("app_help")
            return FeatureTurn.Reply("Use /talk to understand features, check current settings, or request a setting change. I can explain blocking and routines, task reminders, habits, learning tests and vocabulary, notes privacy, transcription, backups and permissions. Follow-ups such as ‘where is it?’, ‘what is the range?’ and ‘change it’ stay with the setting we just discussed. Task creation stays in ordinary messages or /create.", listOf(TalkAction.AskQuery("/talk settings", "Explore settings"), TalkAction.AskQuery("/talk capabilities", "Supported capabilities"), TalkAction.AskQuery("check device health", "Check permissions"), TalkAction.AskQuery("/talk live transcription", "Transcription help"), TalkAction.AskQuery("/talk notes vault pin", "Notes and privacy")))
        }
        val all = topics(context)
        val pinned = Regex("(?i)^explain feature ([a-z_]+)$").matchEntire(text)?.groupValues?.get(1)
        val contextual = state.topic?.let { topic ->
            if (topic.startsWith("setting:")) AssistantPreferences.specs.find { it.key == topic.removePrefix("setting:") }
            else if (topic.startsWith("faq:")) all.firstOrNull { it.optString("id") == topic.removePrefix("faq:") }?.optString("prefKey")?.let { key -> AssistantPreferences.specs.find { it.key == key } }
            else null
        }
        // Prefer the most specific phrase. Generic “theme” and “bubble” must not shadow an accent or delay.
        val diagnosticTopic = all.filter { it.optString("id").startsWith("troubleshoot_") }.mapNotNull { item ->
            aliases(item).filter { contains(text, it) && (clean(it) == lower || Regex("\\b(?:why|cant|cannot|not|missing|disappear|disappeared|gone|stopped|disabled|reset|lost|broken|zero)\\b").containsMatchIn(lower)) }.maxOfOrNull { clean(it).length }?.let { it to item }
        }.maxByOrNull { it.first }?.second
        val named = AssistantPreferences.find(text)
        val mentions = AssistantPreferences.specs.flatMap { item -> item.aliases.filter { contains(text, it) }.map { it to item } }
        val mentioned = mentions.filter { (alias, _) -> mentions.none { (longer, _) -> longer.length > alias.length && contains(longer, alias) } }.map { it.second }.distinctBy { it.key }
        if (isWrite(text) && mentioned.size > 1) {
            state.clearPending(); state.switchTopic("app_help")
            return FeatureTurn.Reply("You named more than one setting. Choose one to review first; nothing has changed.", mentioned.take(6).map { TalkAction.AskQuery("/talk explain ${it.aliases.first()}", it.title) })
        }
        if (Regex("(?i)\\b(?:compare|difference|versus|vs)\\b").containsMatchIn(text)) {
            val matches = mentioned
            if (matches.size >= 2) {
                state.clearPending(); state.switchTopic("app_help")
                return FeatureTurn.Reply(matches.take(4).joinToString("\n\n") { item ->
                    val description = all.firstOrNull { it.optString("prefKey") == item.key }?.optString("directExplanation").orEmpty()
                    "**${item.title}** · ${AssistantPreferences.display(item, AssistantCapabilities.setting(item.key).current(context))}\n$description\n${hint(item)}"
                })
            }
        }
        val question = Regex("(?i)^(?:what|how|why|where|when|can|could|does|do|is|are|explain|tell|show|check|current|compare)\\b").containsMatchIn(text)
        val followWrite = contextual != null && named == null && Regex("(?i)^(?:(?:change|set|make|turn|switch|enable|disable|reset) (?:it|that|this)\\b|(?:actually|instead)\\b)").containsMatchIn(text)
        val newIntent = Regex("(?i)^(?:start|stop|open|show|list|create|add|log|review|back|resume|cancel|undo|complete|delete|reschedule|focus|my habits|my routines)\\b").containsMatchIn(text)
        val slotValue = contextual != null && state.expected == "setting_value" && !question && !newIntent && named == null
        val numericFollow = contextual != null && Regex("(?i)^\\d+(?:\\s*(?:seconds?|secs?|minutes?|mins?|hours?|%|am|pm))?$").matches(text)
        // A long task title merely containing a setting name is not an information request.
        val namedRead = named != null && (appMode || question || named.aliases.any { clean(it) == lower })
        val spec = named?.takeIf { namedRead || isWrite(text) } ?: contextual?.takeIf { lower in followUps || followWrite || slotValue || numericFollow }
        if (spec != null && pinned == null && diagnosticTopic == null) {
            val catalogue = all.firstOrNull { it.optString("prefKey") == spec.key }
            val change = isWrite(text) || followWrite || slotValue || numericFollow || (spec.type == "boolean" && named != null && Regex("(?i)\\b(?:on|off)$").containsMatchIn(text) && !question)
            if (change) {
                val parsed = runCatching { if (lower.startsWith("reset ")) spec.default else AssistantPreferences.extractValue(spec, text) }
                val value = parsed.getOrNull()
                AssistantConversation.suspendQuestion(state); state.clearPending(); state.switchTopic("setting:${spec.key}")
                if (value == null) {
                    state.expected = "setting_value"; state.pending = listOf(AssistantAction.SetPreference(spec.key, spec.type, ""))
                    return FeatureTurn.Reply("${parsed.exceptionOrNull()?.message?.let { "$it. " }.orEmpty()}What should **${spec.title}** be? ${hint(spec)}", settingKey = spec.key, topicId = catalogue?.optString("id"))
                }
                return AssistantCapabilities.setting(spec.key).preview(context, value)
            }
            AssistantConversation.suspendQuestion(state); state.clearPending(); state.switchTopic("setting:${spec.key}")
            val current = AssistantPreferences.display(spec, AssistantCapabilities.setting(spec.key).current(context))
            val explanation = catalogue?.optString("directExplanation").orEmpty()
            val path = catalogue?.optString("whereToGo")?.takeIf { it.isNotBlank() } ?: "Open ${AssistantPreferences.route(spec).replace('_', ' ')}."
            val detail = when {
                lower.startsWith("where") || lower.contains("change it") -> "$path\n${hint(spec)}"
                lower.contains("default") -> "Default: ${AssistantPreferences.display(spec, spec.default)}. ${hint(spec)}"
                else -> "$explanation\n${hint(spec)}"
            }
            return FeatureTurn.Reply("**${catalogue?.optString("title") ?: spec.title}**\nCurrently **$current**.\n$detail".trim(),
                listOf(TalkAction.NavigateAppScreen(AssistantPreferences.route(spec), "Open ${if (spec.owner == "bubble_prefs") "Bubble Settings" else if (spec.key == "secure_recents") "Security" else "App Settings"}")),
                settingKey = spec.key, topicId = catalogue?.optString("id"))
        }
        // Explanations never execute feature commands, even when they contain verbs such as “block”.
        val exactTopic = all.firstOrNull { it.optString("id") == pinned }
        val previous = state.topic?.removePrefix("faq:")?.takeIf { state.topic?.startsWith("faq:") == true }
        val topic = exactTopic ?: diagnosticTopic ?: if (lower in followUps) all.firstOrNull { it.optString("id") == previous } else {
            if (!appMode && !question && all.none { topic -> aliases(topic).any { clean(it) == lower } }) return null
            val ranked = all.mapNotNull { item ->
                aliases(item).filter { contains(text, it) }.maxOfOrNull { clean(it).length }?.let { it to item }
            }.sortedByDescending { it.first }
            ranked.firstOrNull()?.second ?: if (appMode && !isWrite(text)) fuzzyTopic(all, lower) else null
        } ?: return null
        // Explicit value questions for catalogue-only aliases still use the authoritative preference schema.
        topic.optString("prefKey").takeIf { it.isNotBlank() }?.let { key ->
            val target = AssistantPreferences.specs.find { it.key == key }
            if (target != null) return answer(context, state, if (pinned != null) "explain ${target.aliases.first()}" else text.replaceFirst(Regex(Regex.escape(aliases(topic).filter { contains(text, it) }.maxByOrNull { it.length } ?: text), RegexOption.IGNORE_CASE), target.aliases.first()), true)
        }
        if (isWrite(text)) return null // Never turn a fuzzy feature match into an action.
        AssistantConversation.suspendQuestion(state); state.clearPending(); state.switchTopic("faq:${topic.getString("id")}")
        val body = when {
            lower.startsWith("where") -> topic.optString("whereToGo")
            lower == "how" || lower.contains("change it") -> topic.optJSONArray("whatToClick")?.let { steps -> (0 until steps.length()).joinToString("\n") { "${it + 1}. ${steps.getString(it)}" } }.orEmpty()
            lower == "why" -> topic.optString("whyOrTip").ifBlank { topic.optString("directExplanation") }
            else -> topic.optString("directExplanation")
        }
        val actions = buildList<TalkAction> {
            val native = when (topic.getString("id")) {
                "arithmetic_drills" -> "/drill" to "Open arithmetic test"
                "box_breathing" -> "/breathe" to "Start breathing"
                "live_transcription" -> "/transcribe" to "Open transcription"
                "vocabulary_help" -> "/vocab" to "Open vocabulary"
                else -> null
            }
            if (native != null) add(TalkAction.AskQuery(native.first, native.second))
            topic.optString("route").takeIf(String::isNotBlank)?.let { add(TalkAction.NavigateAppScreen(it, "Open ${topic.optString("title")}")) }
            val diagnostic = when (topic.getString("id")) {
                "troubleshoot_why_not_blocking", "troubleshoot_why_no_notifications", "security_and_permissions", "battery_optimization", "troubleshoot_why_bubble_missing" -> "check device health"
                "backup_and_restore" -> "backup status"
                "screentime_tracking" -> "show my usage"
                "troubleshoot_why_streak_reset" -> "explain my streak"
                else -> null
            }
            if (diagnostic != null) add(0, TalkAction.AskQuery(diagnostic, "Check current status"))
            topic.optString("systemSettingAction").takeIf(String::isNotBlank)?.let { add(TalkAction.OpenSystemSetting(it, "Android settings", packageUri = topic.optBoolean("isPackageSettingUri"))) }
        }
        return FeatureTurn.Reply("**${topic.getString("title")}**\n$body", actions, topicId = topic.getString("id"))
    }

    fun hint(spec: AssistantPreferenceSpec): String = AssistantCapabilities.setting(spec.key).hint()
    private fun aliases(topic: JSONObject): List<String> {
        val a = topic.optJSONArray("keywords") ?: JSONArray()
        return listOf(topic.optString("title")) + (0 until a.length()).map { a.getString(it) }
    }
    /** Small read-only typo allowance; a tie asks for clarification rather than guessing an action. */
    private fun fuzzyTopic(topics: List<JSONObject>, text: String): JSONObject? {
        if (text.split(' ').size !in 1..5) return null
        fun near(a: String, b: String): Boolean {
            if (a == b) return true
            if (a.length < 5 || b.length < 5 || kotlin.math.abs(a.length - b.length) > 2) return false
            var row = IntArray(b.length + 1) { it }
            a.forEachIndexed { i, c -> val next = IntArray(b.length + 1); next[0] = i + 1; for (j in b.indices) next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (c == b[j]) 0 else 1); row = next }
            return row.last() <= 2
        }
        return topics.filter { t -> aliases(t).any { alias -> val words = clean(alias).split(' '); val asked = text.split(' '); words.size == asked.size && words.zip(asked).all { near(it.first, it.second) } } }.singleOrNull()
    }
}
