package com.focusbyrj.app.util.assistant

enum class AssistantActionGuard { NEGATED, SUGGESTED }

/** Shared, anchored request grammar. Contents of task titles are never rewritten. */
object AssistantLanguage {
    private val gerunds = mapOf("moving" to "move", "rescheduling" to "reschedule", "adding" to "add",
        "creating" to "create", "reminding" to "remind", "showing" to "show", "listing" to "list",
        "starting" to "start", "stopping" to "stop", "logging" to "log", "renaming" to "rename",
        "marking" to "mark", "completing" to "complete", "deleting" to "delete", "setting" to "set",
        "changing" to "change", "opening" to "open", "checking" to "check", "enabling" to "enable", "disabling" to "disable",
        "explaining" to "explain", "telling" to "tell", "helping" to "help", "archiving" to "archive",
        "restoring" to "restore", "focusing" to "focus")
    private val commands = setOf("move", "reschedule", "add", "create", "remind", "show", "list", "start", "stop",
        "log", "rename", "mark", "complete", "delete", "set", "change", "open", "check", "enable", "disable",
        "buy", "call", "email", "repeat", "reopen", "block", "unblock", "remove", "clear", "trash", "edit", "update", "fix",
        "archive", "restore", "unarchive", "focus")

    fun unwrap(input: String): String {
        if (input.trimStart().startsWith('/') || input.trimStart().startsWith('"')) return input
        var text = input.trim()
        var request = false
        repeat(3) {
            val withoutPlease = text.replaceFirst(Regex("(?i)^please\\s+"), "")
            if (withoutPlease != text) { text = withoutPlease; request = true }
            val mind = Regex("(?i)^would you mind\\s+(.+)$").matchEntire(text)
            val wrapper = Regex("(?i)^(?:(?:can|could|would|will) you(?: please)?|i(?:'d| would) like you to|i want you to)\\s+(.+)$").matchEntire(text)
            val body = mind?.groupValues?.get(1) ?: wrapper?.groupValues?.get(1)
            if (body != null) {
                text = body; request = true
                if (mind != null) {
                    val first = text.substringBefore(' ')
                    gerunds[first.lowercase()]?.let { text = it + text.removePrefix(first) }
                }
            }
        }
        if (request && text.substringBefore(' ').lowercase() in commands) text = text.trimEnd('?').trimEnd()
        text = text.replaceFirst(Regex("(?i)^(?:help me|show(?: me)? how to|tell me how to)\\s+(?=(?:delete|remove|move|reschedule|rename|complete|block|unblock|edit|set|change|add|create|archive|restore|unarchive)\\b)"), "how do I ")
        // A qualified yes is a new request, never permission to run the old preview first.
        text = text.replaceFirst(Regex("(?i)^(?:yes|okay|ok)[, ]+but\\s+(?=(?:move|reschedule|rename|make|change|delete|add|start|stop)\\b)"), "")
        text = text.replaceFirst(Regex("(?i)^no[, ]+make (?:that|it)\\s+"), "actually ")
        val politeReply = Regex("(?i)^(yes|yep|yeah|sure|okay|ok|go ahead|do it|confirm|no|nope)[, ]+(?:please|thanks|thank you)[.!?]*$").matchEntire(text)
        return politeReply?.groupValues?.get(1) ?: text
    }

    fun actionGuard(text: String): AssistantActionGuard? {
        val lower = text.lowercase().trim()
        val negated = Regex("^(?:don't|do not|never|not|i don't want (?:you )?to|i do not want (?:you )?to)\\s+(?:you to )?(\\w+)\\b").find(lower)
        if (negated != null && (gerunds[negated.groupValues[1]] ?: negated.groupValues[1]) in commands) return AssistantActionGuard.NEGATED
        val suggested = Regex("^(?:maybe|perhaps|i might|i could|i(?:'m| am) thinking (?:of|about))\\s+(\\w+)\\b").find(lower)
        if (suggested != null && (gerunds[suggested.groupValues[1]] ?: suggested.groupValues[1]) in commands) return AssistantActionGuard.SUGGESTED
        return null
    }

    fun acknowledgement(text: String) = text.lowercase().trimEnd('.', '!', '?') in setOf(
        "thanks", "thank you", "thx", "thanks ayva", "thanks, ayva", "thank you ayva", "thank you, ayva",
        "thanks a lot", "thank you so much", "thanks for your help", "thanks for the help", "okay thank you",
        "okay thanks", "ok thanks", "got it", "understood", "cool", "great", "perfect", "nice")
}
