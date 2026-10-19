package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.util.OfflineNluEngine

enum class UtteranceKind { REQUEST, QUESTION, CONVERSATION, NEGATED, HYPOTHETICAL, QUOTED, CORRECTION, AMBIGUOUS }

data class Interpretation(val kind: UtteranceKind, val text: String, val clarification: String? = null) {
    val permitsMutation get() = kind == UtteranceKind.REQUEST || kind == UtteranceKind.CORRECTION
    val blocksDispatch get() = kind in setOf(UtteranceKind.NEGATED, UtteranceKind.HYPOTHETICAL, UtteranceKind.QUOTED) || clarification != null
}

/** Shared safety arbitration before domain parsers. Never fuzzy-correct an executable command. */
object AssistantInterpretation {
    private val verbs = setOf("delete", "complete", "reschedule", "rename", "unlock", "disable", "enable", "archive", "restore", "create")

    fun interpret(raw: String): Interpretation {
        val text = OfflineNluEngine.normalizeRequest(raw)
        val lower = text.lowercase().trimEnd('.', '!', '?')
        if (lower.startsWith('/')) return Interpretation(UtteranceKind.REQUEST, text)
        val guard = AssistantLanguage.actionGuard(text)
        if (guard != null) return Interpretation(if (guard == AssistantActionGuard.NEGATED) UtteranceKind.NEGATED else UtteranceKind.HYPOTHETICAL, text)
        // Quoting an example or describing someone else's command grants no authority.
        if (Regex("^[\"“`].*[\"”`]$").matches(text.trim()) ||
            Regex("(?i)^(?:for example|example|suppose|imagine|hypothetically|what if|if i|if you|someone said|they said|my friend said|the app said|i said)\\b").containsMatchIn(text))
            return Interpretation(UtteranceKind.QUOTED, text)
        if (AssistantGuidedPlanning.isRequest(text)) return Interpretation(UtteranceKind.CONVERSATION, text)
        if (Regex("(?i)^(?:i (?:might|may|could|should|would)|we (?:might|could|should)|let's say|lets say)\\b").containsMatchIn(text))
            return Interpretation(UtteranceKind.HYPOTHETICAL, text)
        if (Regex("(?i)^(?:actually|instead|i meant|no[, ])\\b").containsMatchIn(text)) return Interpretation(UtteranceKind.CORRECTION, text)
        if (OfflineNluEngine.isExplicitCreation(text)) return Interpretation(UtteranceKind.REQUEST, text)
        if (OfflineNluEngine.isQuestion(text)) return Interpretation(UtteranceKind.QUESTION, text)
        if (AssistantDictionary.isLookup(text)) return Interpretation(UtteranceKind.QUESTION, text)
        if (OfflineNluEngine.isConversationalStatement(text) ||
            Regex("(?i)^(?:we should talk|let's chat|lets chat|talk to me|tell me a story|tell me a joke|just listen|be serious|i've been|i have been|i had|my day|my exam|my trip)\\b").containsMatchIn(text))
            return Interpretation(UtteranceKind.CONVERSATION, text)
        if (lower in setOf("archived habits", "active habits")) return Interpretation(UtteranceKind.REQUEST, text)
        val direct = Regex("^(?:(?:soft|hard|strict) )?(?:complete|completed|finish|finished|mark|check off|done with|delete|remove|trash|cancel|clear|clean|wipe|reset|move|reschedule|postpone|delay|push|bump|triage|rename|edit|update|fix|reopen|set|change|turn|enable|disable|start|stop|pause|resume|begin|launch|halt|end|quit|activate|deactivate|lock|unlock|block|unblock|restrict|allow|shield|unshield|archive|restore|unarchive|focus|log)\\b").containsMatchIn(lower)
        if (direct) return Interpretation(UtteranceKind.REQUEST, text)
        if (Regex("\\b(?:delete|deleting|complete|completing|reschedule|rescheduling|unlock|unlocking|disable|disabling|enable|enabling|archive|archiving|restore|restoring)\\b").containsMatchIn(lower))
            return Interpretation(UtteranceKind.AMBIGUOUS, text, "Are you describing an action, asking about it, or requesting a change? Use /talk for feature help, or state the action directly. I haven't changed anything.")
        val first = lower.substringBefore(' ')
        if (first.length in 5..12 && first !in verbs && lower.contains(' ')) {
            val match = verbs.filter { OfflineNluEngine.levenshtein(first, it) == 1 || (first.length == it.length && (0 until first.lastIndex).any { i -> first.substring(0, i) + first[i + 1] + first[i] + first.substring(i + 2) == it }) }.singleOrNull()
            if (match != null) return Interpretation(UtteranceKind.AMBIGUOUS, text,
                "Did you mean ‘$match’? Please send the corrected request so I can check its target before changing anything.")
        }
        return Interpretation(UtteranceKind.AMBIGUOUS, text)
    }

    fun explanation(result: Interpretation): String = result.clarification ?: when (result.kind) {
        UtteranceKind.NEGATED -> "Okay. I won't apply that request. Tell me what you'd like to do instead."
        UtteranceKind.QUOTED -> "I'll treat that as an example, not an instruction. Are you asking how it works? You can use /talk for feature help."
        else -> "Are you asking how that works, or thinking it through? If you want a change, state the action directly; I haven't applied anything."
    }
}
