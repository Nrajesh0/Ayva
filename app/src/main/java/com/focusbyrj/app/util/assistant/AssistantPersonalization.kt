package com.focusbyrj.app.util.assistant

import android.content.Context

data class PersonalizationReply(val text: String, val forgetConversation: Boolean = false)

/** Only explicit, allowlisted preferences persist. No arbitrary facts or inferred habits. */
object AssistantPersonalization {
    private fun prefs(context: Context) = context.getSharedPreferences("ayva_personalization", Context.MODE_PRIVATE)
    fun tone(context: Context) = (prefs(context).all["tone"] as? String)?.takeIf { it in setOf("warm", "playful", "direct") } ?: "warm"
    fun quiet(context: Context) = prefs(context).getBoolean("quiet", false)
    fun suggestions(context: Context) = prefs(context).getBoolean("suggestions", false) && !quiet(context)
    fun focusMinutes(context: Context) = (prefs(context).all["focus_minutes"] as? Int)?.takeIf { it in 1..240 } ?: 25
    fun planSize(context: Context) = (prefs(context).all["plan_size"] as? Int)?.takeIf { it in 1..5 } ?: 3
    fun planOrder(context: Context) = (prefs(context).all["plan_order"] as? String)?.takeIf { it in setOf("priority", "due") } ?: "priority"

    fun handle(context: Context, raw: String): PersonalizationReply? {
        val text = raw.trim().trimEnd('.', '!', '?')
        val lower = text.lowercase()
        val prefs = prefs(context)
        if (lower in setOf("forget me", "forget my preferences", "forget what i told you", "forget all preferences", "forget everything you remember about me")) {
            check(context.getSharedPreferences("ayva_suggestion_receipts", 0).edit().clear().commit())
            check(prefs.edit().clear().commit()) { "Couldn't clear saved preferences" }
            return PersonalizationReply("I've cleared my conversational context and your chat preferences. Saved tasks, habits and notes are unchanged.", true)
        }
        if (lower in setOf("what do you remember about me", "what have you remembered about me", "show my chat preferences", "show my preferences", "review my preferences", "what are my chat preferences")) {
            val values = prefs.all
            val saved = buildList {
                (values["name"] as? String)?.let { add("Name: $it") }
                (values["concise"] as? Boolean)?.let { add("Reply detail: ${if (it) "brief" else "more context"}") }
                (values["emoji"] as? Boolean)?.let { add("Help/suggestion emoji: ${if (it) "on" else "off"}") }
                (values["tone"] as? String)?.let { add("Conversation tone: $it") }
                (values["quiet"] as? Boolean)?.let { add("Quiet conversation: ${if (it) "on" else "off"}") }
                (values["suggestions"] as? Boolean)?.let { add("Optional task suggestions: ${if (it) "on" else "off"}") }
                if (values["focus_minutes"] is Int) add("Default focus duration: ${focusMinutes(context)} minutes")
                if (values["plan_size"] is Int) add("Plan size: up to ${planSize(context)} tasks")
                if (values["plan_order"] is String) add("Planning order: ${if (planOrder(context) == "due") "due dates first" else "priorities first"}")
            }
            return PersonalizationReply(if (saved.isEmpty()) "You have no saved chat preferences. Defaults: 25-minute focus sessions, plans of up to 3 tasks, priorities first."
                else "Saved chat preferences:\n" + saved.joinToString("\n") { "• $it" } + "\nOnly preferences you explicitly set are kept. Say ‘forget my focus preference’, ‘forget my planning preferences’ or ‘forget my preferences’ to remove them.")
        }
        val keysToRemove = when (lower) {
            "forget my name" -> listOf("name")
            "forget my focus preference", "reset my focus preference" -> listOf("focus_minutes")
            "forget my planning preferences", "reset my planning preferences" -> listOf("plan_size", "plan_order")
            "forget my reply preferences", "reset my reply preferences" -> listOf("concise", "emoji", "tone", "quiet", "suggestions")
            else -> null
        }
        if (keysToRemove != null) {
            check(prefs.edit().apply { keysToRemove.forEach { remove(it) } }.commit()) { "Couldn't clear saved preferences" }
            return PersonalizationReply("Removed those saved preferences. Their defaults apply to future requests; running timers and saved items are unchanged.")
        }
        Regex("(?i)^(?:call me|my name is)\\s+(.+)$").matchEntire(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.isBlank() || name.length > 40 || name.any(Char::isISOControl)) return PersonalizationReply("Use a name of 1–40 characters without control characters.")
            check(prefs.edit().putString("name", name).commit()) { "Couldn't save your name" }
            return PersonalizationReply("I'll call you $name. Say ‘forget my name’ to remove it.")
        }
        val explicit = Regex("(?i)^(?:remember(?: that)?|save (?:this|my) preference:?|set my default)\\s+(.+)$").matchEntire(text) ?: return null
        // ‘Remember to…’ remains an explicit task request, never profile storage.
        if (lower.startsWith("remember to ")) return null
        val body = explicit.groupValues[1].replaceFirst(Regex("(?i)^(?:i prefer|i like|my preferred|my default)\\s+"), "")
        val tone = Regex("(?i)^(?:a )?(warm|playful|direct) (?:conversation )?tone$").matchEntire(body)?.groupValues?.get(1)?.lowercase()
        if (tone != null) {
            check(prefs.edit().putString("tone", tone).commit())
            return PersonalizationReply("Saved a $tone tone for future conversations. ‘Be serious’ or ‘just listen’ can still change this exchange. Say ‘forget my reply preferences’ to reset it.")
        }
        val quiet = when (body.lowercase()) { "quiet conversation", "quiet mode", "no unsolicited questions" -> true; "normal conversation", "quiet mode off" -> false; else -> null }
        if (quiet != null) {
            check(prefs.edit().putBoolean("quiet", quiet).commit())
            return PersonalizationReply("Saved: quiet conversation ${if (quiet) "on" else "off"}. This only changes optional chat prompts; it doesn't mute app reminders.")
        }
        val suggestions = when (body.lowercase()) { "task suggestions", "optional task suggestions", "task suggestions on" -> true; "no suggestions", "no task suggestions", "task suggestions off" -> false; else -> null }
        if (suggestions != null) {
            check(prefs.edit().putBoolean("suggestions", suggestions).commit())
            return PersonalizationReply("Saved: optional task suggestions ${if (suggestions) "on" else "off"}. These appear only in chat, with a cooldown, and never change tasks or send notifications.")
        }
        val focus = Regex("(?i)^(?:(?:focus(?: sessions?| duration)?(?: (?:is|of|to))? )?(\\d+)\\s*(?:-\\s*)?(?:minutes?|mins?)(?: (?:focus sessions?|focus blocks?|of focus))?|focus(?: sessions?| duration)?(?: (?:is|of|to))? (\\d+))$").matchEntire(body)
        if (focus != null) {
            val minutes = focus.groupValues[1].ifBlank { focus.groupValues[2] }.toIntOrNull()
            if (minutes == null || minutes !in 1..240) return PersonalizationReply("Choose a default focus duration from 1–240 whole minutes. I haven't changed it.")
            check(prefs.edit().putInt("focus_minutes", minutes).commit())
            return PersonalizationReply("Saved: use $minutes minutes when you start focus without a duration. Explicit durations still take precedence. Say ‘forget my focus preference’ to reset it.")
        }
        Regex("(?i)^(?:up to )?(\\d+) tasks? (?:in|per|for) (?:my |a |each )?plans?$").matchEntire(body)?.let {
            val count = it.groupValues[1].toIntOrNull()
            if (count == null || count !in 1..5) return PersonalizationReply("Choose 1–5 tasks per plan. I haven't changed that preference.")
            check(prefs.edit().putInt("plan_size", count).commit())
            return PersonalizationReply("Saved: show up to $count tasks in a new plan. Say ‘forget my planning preferences’ to reset it.")
        }
        val order = when (body.lowercase()) {
            "due dates first", "deadlines first", "due dates first in my plans", "deadlines first in my plans" -> "due"
            "priorities first", "priority first", "priorities first in my plans" -> "priority"
            else -> null
        }
        if (order != null) {
            check(prefs.edit().putString("plan_order", order).commit())
            return PersonalizationReply("Saved: ${if (order == "due") "due dates first" else "priorities first"} in new plans. Overdue tasks are highlighted. Say ‘forget my planning preferences’ to reset it.")
        }
        val style = body.replaceFirst(Regex("(?i)^(?:that )?"), "").lowercase()
        val styleCommand = when (style) { "brief replies", "short replies", "short answers" -> "be concise"; "detailed replies", "more detail" -> "more detail"; "no emoji", "no emojis" -> "no emoji"; "emoji", "emojis" -> "use emojis"; else -> null }
        if (styleCommand != null) return PersonalizationReply(AssistantReplyStyle.preference(context, styleCommand)!!)
        return PersonalizationReply("I can remember your name, reply style/tone, quiet conversation, optional task suggestions, default focus duration, plan size and planning order. For example: ‘remember that I prefer 15-minute focus sessions’. I haven't saved that as a preference. Notes, passwords and arbitrary personal facts aren't stored in this profile.")
    }
}
