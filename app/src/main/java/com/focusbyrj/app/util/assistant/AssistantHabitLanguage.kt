package com.focusbyrj.app.util.assistant

/** Count habit events, never silently convert volume or time into a user's daily target. */
object AssistantHabitLanguage {
    fun report(input: String, state: AssistantSession): Pair<String, Int>? {
        val text = input.lowercase().trimEnd('.', '!', '?')
        val counts = mapOf("a" to 1, "one" to 1, "another" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5)
        Regex("^(?:i )?(?:just )?(?:drank|had|finished) (a|one|another|two|three|four|five|\\d+) glass(?:es)? of water$").matchEntire(text)?.let {
            val count = counts[it.groupValues[1]] ?: it.groupValues[1].toIntOrNull() ?: return null
            if (count in 1..100) return "water" to count
        }
        if (text in setOf("another glass of water", "had another glass", "another glass")) return "water" to 1
        if (text in setOf("one more", "log one more", "another one") && state.topic == "habits" && state.habitId != null) return "id:${state.habitId}" to 1
        Regex("^(?:i (?:just )?(?:did|finished)|check off) my (.+?) habit$").matchEntire(text)?.let { return it.groupValues[1] to 1 }
        return null
    }
}
