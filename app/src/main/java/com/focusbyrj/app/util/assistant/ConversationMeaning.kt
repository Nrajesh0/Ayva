package com.focusbyrj.app.util.assistant

/** Literal-aware clause boundaries and speech acts, shared by conversation and planning. */
internal object ConversationMeaning {
    enum class Kind { QUESTION, POSSIBILITY, CORRECTION, NEED, COMMAND, STATEMENT }
    data class Clause(val text: String, val kind: Kind)
    fun clauses(input: String): List<Clause> {
        require(input.length <= 4000)
        val result = mutableListOf<String>()
        var start = 0; var quote: Char? = null; var i = 0
        val boundary = Regex("(?i)(?:,\\s*|\\s+(?:and then|then|and)\\s+)(?=(?:add|remove|drop|rename|move|schedule|give|make|set|keep|leave|don't|do not|we need|i need|why|what about)\\b)")
        while (i < input.length) {
            val c = input[i]
            if (quote != null) { if (c == quote || quote == '“' && c == '”') quote = null; i++; continue }
            if (c == '"' || c == '“' || c == '`') { quote = c; i++; continue }
            val m = boundary.matchAt(input, i)
            if (c == ';' || m != null) {
                val piece = input.substring(start, i).trim()
                if (piece.isNotEmpty()) result += piece
                i += if (c == ';') 1 else m!!.value.length
                start = i
            } else i++
        }
        input.substring(start).trim().takeIf { it.isNotEmpty() }?.let(result::add)
        require(result.size <= 12) { "Let's work through at most twelve parts in one message" }
        return result.map { Clause(it, kind(it)) }
    }
    fun kind(text: String): Kind {
        val lower = PlanLanguage.normalize(text).lowercase()
        return when {
            Regex("^(?:maybe|perhaps|suppose|imagine|what if|i might|we might|we could|i could|if )\\b").containsMatchIn(lower) -> Kind.POSSIBILITY
            Regex("^(?:actually|instead|i meant|no[, ])").containsMatchIn(lower) -> Kind.CORRECTION
            lower.endsWith('?') || Regex("^(?:why|what|how|could|would|should|can i|do we)\\b").containsMatchIn(lower) -> Kind.QUESTION
            Regex("^(?:we need|i need|we should add|let'?s add)\\b").containsMatchIn(lower) -> Kind.NEED
            Regex("^(?:add|remove|drop|rename|move|shift|schedule|unschedule|give|allocate|make|set|split|protect|save|apply|complete|delete|keep|leave|unlink|cancel|deadline|due|availability)\\b").containsMatchIn(lower) -> Kind.COMMAND
            else -> Kind.STATEMENT
        }
    }
}
