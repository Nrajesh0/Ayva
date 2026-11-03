package com.focusbyrj.app.util.assistant

/** Small, read-only paraphrase fallback. Original wording still supplies conversational facts. */
internal object GeneralDialoguePhrases {
    fun interpret(input: String): String? {
        if (input.toByteArray(Charsets.UTF_8).size !in 1..2000 ||
            input.any { it in "\n\r\u0000\"“`" } ||
            !Regex("^(could|can|would|any|some|what|where|why|how|explain|elaborate|sum|summarize|recap|something|anything|another|different|crowded|crowds|peaceful|quiet|calm|i)\\b", RegexOption.IGNORE_CASE).containsMatchIn(input.trim()) ||
            Regex("\\b(not|no|never|don't|doesn't|didn't|can't|cannot|without|maybe|perhaps|might|if|suppose|imagine|actually|instead|but|and|or|he|she|they|friend|said|says)\\b", RegexOption.IGNORE_CASE).containsMatchIn(input.replace('’', '\''))) return null
        val text = input.trim().lowercase(java.util.Locale.ROOT).trimEnd('.', '!', '?')
        fun matches(pattern: String) = Regex(pattern).matches(text)
        return when {
            matches("(?:(?:could|can|would) you .*?(?:suggest|recommend).*|(?:any|some) .*?(?:idea|suggestion|recommendation).*|what .*?(?:suggest|recommend).*|what .*?help me (?:unwind|relax|recharge).*)") -> "what could I try"
            matches("(?:explain|elaborate|why|how) .*?\\b(?:that|it)\\b.*") -> "why that"
            matches("(?:can|could) you .*?(?:explain|elaborate).*") -> "tell me more"
            matches("(?:can|could) you .*?(?:example|illustrate).*") -> "give me an example"
            matches("(?:can|could) you .*?(?:simpler|shorter|concise).*") -> "shorter"
            matches("(?:sum|summarize|recap)\\b.*|(?:what|where) .*?(?:discussed|discussing|conversation).*") -> "what do we have so far"
            matches("(?:something|anything) (?:different|else).*|(?:another|different) (?:option|idea|suggestion).*") -> "something else"
            matches("(?:crowded|crowds) .*?(?:drain|exhaust|tire|overwhelm)\\w* .*?(?:me|my).*") -> "crowds wear me out"
            matches("(?:peaceful|quiet|calm) evenings? .*?(?:me|my).*|i (?:like|love|prefer|enjoy) .*?(?:peaceful|tranquil).*") -> "I prefer quiet evenings"
            matches("i .*?(?:anxious|nervous|worried) .*?(?:studying|study|exam|revision).*|i .*?(?:studying|study|exam|revision) .*?(?:anxious|nervous|worried).*") -> "I am worried about studying"
            matches("i .*?(?:frustrated|stressed|overwhelmed) .*?(?:job|work|boss).*|i .*?(?:job|work|boss) .*?(?:frustrated|stressed|overwhelmed).*") -> "I am stressed about work"
            matches("i (?:am|feel) .*?(?:exhausted|drained|weary).*") -> "I feel tired"
            else -> null
        }
    }
}
