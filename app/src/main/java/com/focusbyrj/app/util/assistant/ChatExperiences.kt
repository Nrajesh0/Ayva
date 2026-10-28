package com.focusbyrj.app.util.assistant

/** Only implemented experiences belong here. Shared by chat help and the discovery UI. */
internal object ChatExperiences {
    data class Capability(val id: String, val group: String, val title: String, val promise: String, val request: String) {
        val version: Int get() = 1
        val owner: String get() = when (id) {
            "joke", "riddle", "reading", "music", "cooking" -> "social"
            "unwind", "study" -> "general"
            "vocabulary" -> "learning"
            else -> "focused"
        }
        val followups: List<String> get() = when (id) {
            "chain", "anagram", "riddle" -> listOf("hint", "skip", "stop", "resume activity")
            "facts" -> listOf("why", "example", "source", "related fact", "stop")
            "joke" -> listOf("another", "explain that", "less silly", "stop")
            "lines" -> listOf("another", "calm", "resilient", "stop")
            "story" -> listOf("first", "second", "pause story", "resume story", "stop")
            "vocabulary" -> listOf("practice saved words", "my last result")
            else -> listOf("tell me more", "recap", "no advice")
        }
        val available: Boolean get() = when (id) {
            "anagram" -> ExperienceContent.puzzles.isNotEmpty()
            "facts" -> ExperienceContent.facts.isNotEmpty()
            "story" -> ExperienceContent.stories.isNotEmpty()
            "lines" -> ExperienceContent.lines.values.any { it.isNotEmpty() }
            else -> true
        }
    }
    val capabilities = listOf(
        Capability("chain", "Play", "Word chain", "Link dictionary words; hints, scoring and short rounds.", "play word chain"),
        Capability("anagram", "Play", "Anagrams", "Unscramble a word with progressive hints.", "play anagrams"),
        Capability("riddle", "Play", "Riddles", "Guess, ask for a hint, or reveal the explanation.", "give me a riddle"),
        Capability("vocabulary", "Discover", "Vocabulary practice", "Open the existing word-learning and saved-word quizzes.", "practice vocabulary"),
        Capability("facts", "Discover", "Fact trails", "Explore a small collection of sourced facts and follow-ups.", "explore facts"),
        Capability("joke", "Take a break", "A little humour", "Short jokes with explanations and an honest end to the collection.", "tell me a joke"),
        Capability("lines", "Take a break", "A little encouragement", "Original Run lines: calm, disciplined, resilient or battle-inspired.", "give me a motivational line"),
        Capability("story", "Take a break", "Tiny adventures", "Short choice-based stories with different endings.", "play a tiny story"),
        Capability("unwind", "Talk a little", "Find a way to unwind", "Explore setting, energy and company preferences.", "I want to unwind"),
        Capability("study", "Talk a little", "Study difficulties", "Work through distraction or feeling overwhelmed; no syllabus expertise.", "I am stressed about studying"),
        Capability("reading", "Talk a little", "Reading tastes", "Explore characters, worlds and writing; not arbitrary book plots.", "I like reading"),
        Capability("music", "Talk a little", "Music tastes", "Explore rhythm, lyrics and atmosphere; no song catalogue.", "I enjoy music"),
        Capability("cooking", "Talk a little", "Cooking preferences", "Talk about process and preferences; not a recipe database.", "I like cooking")
    ).filter { it.available && it.group != "Talk a little" }
    fun unsupportedTopic(input: String): String? {
        val match = Regex("^(?:let'?s (?:talk|chat) about|can we (?:talk|chat) about|i want to talk about|i would like to talk about|talk about) (.{1,100})$", RegexOption.IGNORE_CASE).matchEntire(input.trim()) ?: return null
        val topic = match.groupValues[1].lowercase()
        if (Regex("\\b(?:read(?:ing)?|books?|novels?|music|songs?|cook(?:ing)?|baking|food|films?|movies?|coding|programming|software|walking|walks|nature|photography|unwind|relax|quiet|feelings?|study|studying|exam|work|job|stress|decisions?|options?)\\b").containsMatchIn(topic)) return null
        return "I don't have a reliable conversation pack for that subject yet. We can try a word game, riddle or short story. ‘Explore Ayva’ shows what's available."
    }
    fun help(): String = "Here are a few things we can do well offline:\n\n" + capabilities.groupBy { it.group }.entries.joinToString("\n\n") { (group, items) ->
        "$group\n" + items.joinToString("\n") { "• ${it.title}: ${it.promise}" }
    } + "\n\nYou can type an example or choose an activity. Tasks, habits and Plan still use their existing commands."
}
