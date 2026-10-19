package com.focusbyrj.app.util.assistant

import org.json.JSONObject

/** Only app-supported test configurations can be dispatched to the existing feature windows. */
data class AssistantLearningContext(
    val kind: String,
    val difficulty: String = "easy",
    val count: Int = 10,
    val startedAt: Long = System.currentTimeMillis(),
    val total: Int? = null,
    val correct: Int? = null
) {
    fun command(): String = when (kind) {
        "blitz" -> "/blitz"
        "vocab" -> "/vocab_quiz"
        else -> "/drill $difficulty ${if (count == -1) "unlimited" else count}"
    }

    fun toJson() = JSONObject().put("kind", kind).put("difficulty", difficulty).put("count", count)
        .put("started", startedAt).put("total", total ?: JSONObject.NULL).put("correct", correct ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject): AssistantLearningContext {
            val kind = json.getString("kind"); val difficulty = json.getString("difficulty"); val count = json.getInt("count")
            require(kind in setOf("drill", "blitz", "vocab") && difficulty in setOf("easy", "medium", "hard", "vocab"))
            require(count in setOf(-1, 10, 20) || (kind == "vocab" && count in 1..20))
            val total = if (json.isNull("total")) null else json.getInt("total")
            val correct = if (json.isNull("correct")) null else json.getInt("correct")
            require(total == null || (total >= 0 && correct != null && correct in 0..total))
            return AssistantLearningContext(kind, difficulty, count, json.getLong("started"), total, correct)
        }
    }
}

sealed class LearningRequest {
    data class Launch(val configuration: AssistantLearningContext) : LearningRequest()
    data class Reply(val text: String, val focusLearning: Boolean = false) : LearningRequest()
    data class Open(val command: String) : LearningRequest()
}

object AssistantLearningLanguage {
    fun command(input: String): AssistantLearningContext? {
        val parts = input.trim().lowercase().split(Regex("\\s+"))
        return when (parts[0]) {
            "/drill", "/math", "/quiz" -> AssistantLearningContext("drill", parts.getOrNull(1)?.takeIf { it in setOf("easy", "medium", "hard") } ?: "easy",
                when (parts.getOrNull(2)) { "20" -> 20; "unlimited", "infinite", "endless" -> -1; else -> 10 })
            "/blitz", "/speed" -> AssistantLearningContext("blitz", count = -1)
            "/vocab_quiz" -> AssistantLearningContext("vocab", "vocab")
            else -> null
        }
    }

    fun parse(input: String, state: AssistantSession): LearningRequest? {
        val text = input.lowercase().trim().trimEnd('.', '!', '?')
            .replace(Regex("^(?:let's|lets|can we|could we|i'd like to|i want to)\\s+"), "")
            .replace(Regex("\\bten\\b"), "10").replace(Regex("\\btwenty\\b"), "20")
        if (text in setOf("how did i do", "how was my test", "how was my drill", "my last result", "show my last drill result", "what was my score")) {
            val last = state.learning
            return LearningRequest.Reply(if (last?.total == null) "I don't have a completed test result in this conversation yet. Finish a Drill, Blitz or vocabulary quiz first."
                else "You answered **${last.correct} of ${last.total} attempted questions correctly**${if (last.total > 0) " (${last.correct!! * 100L / last.total}% accuracy)" else ""}. ${if (last.kind == "drill") "That was a ${last.difficulty} arithmetic drill." else if (last.kind == "blitz") "That was Speed Blitz." else "That was a vocabulary quiz."}", focusLearning = last?.total != null)
        }
        if (text in setOf("vocab", "vocabulary", "teach me a word", "learn a word", "vocabulary practice", "practice vocabulary", "practice my vocabulary", "open vocabulary", "show my vocabulary")) return LearningRequest.Open("/vocab")
        if (text in setOf("learn more words", "teach me more words", "learn another word", "learn an idiom")) return LearningRequest.Open("/vocab learn_more")
        if (text in setOf("quiz me on words i saved", "quiz me on saved words", "quiz my bookmarks", "practice saved words")) return LearningRequest.Open("/vocab_quiz saved")
        if (text in setOf("vocabulary quiz", "start vocabulary quiz", "vocab quiz", "quiz me on vocabulary", "test my vocabulary", "review my vocabulary", "practice my learned words")) return LearningRequest.Launch(AssistantLearningContext("vocab", "vocab"))
        if (text in setOf("blitz", "start blitz", "speed drill", "start speed drill", "do a blitz", "start speed blitz", "a speed round", "give me a speed round")) return LearningRequest.Launch(AssistantLearningContext("blitz", count = -1))

        val followUp = text.replace(Regex("^(?:give me (?:a )?|do (?=(?:something |a little )?(?:harder|easier))|try (?:a )?)"), "").replace(Regex("\\b(harder|easier) round$"), "$1")
            .replace(Regex("[, ]+(?:but )?(?:keep )?(?:the )?same (?:number of )?questions"), ", same questions")
        val repeat = Regex("^(?:again|same again|do it again|another round|same test again|repeat (?:the |my )?(?:last )?(?:drill|test|quiz)|(?:make it |a little |something )?(?:harder|easier)(?:[, ]+(?:same (?:number of )?questions|same count|same length))?)$").matches(followUp)
        val adjust = Regex("^(?:(?:make it|try|switch to|give me) )?(?:(?:easy|medium|hard)(?:[, ]+)?|(?:10|20|unlimited) questions?(?:[, ]+)?)(?:(?:easy|medium|hard)|(?:10|20|unlimited) questions?)?$").matches(text)
        if (repeat || (adjust && state.topic == "learning")) {
            val previous = state.learning
            if (previous == null || (state.topic != "learning" && !Regex("\\b(?:drill|test|quiz)\\b").containsMatchIn(text))) return LearningRequest.Reply("Which feature should I repeat? Try ‘start a hard drill with 20 questions’.")
            if (previous.kind != "drill" && text.contains("drill")) return LearningRequest.Reply("Your last practice was ${if (previous.kind == "vocab") "vocabulary" else "Blitz"}. Say ‘start a drill’ for an arithmetic test.")
            if (previous.kind != "drill" && Regex("\\b(?:harder|easier|easy|medium|hard|10|20|unlimited)\\b").containsMatchIn(text)) return LearningRequest.Reply("${if (previous.kind == "vocab") "Vocabulary quizzes use your learned words" else "Blitz uses its timed mode"}. For arithmetic difficulty and length, say ‘start a hard drill with 20 questions’.")
            val levels = listOf("easy", "medium", "hard")
            val difficulty = when {
                text.contains("harder") -> levels[(levels.indexOf(previous.difficulty) + 1).coerceAtMost(2)]
                text.contains("easier") -> levels[(levels.indexOf(previous.difficulty) - 1).coerceAtLeast(0)]
                else -> levels.find { Regex("\\b$it\\b").containsMatchIn(text) } ?: previous.difficulty
            }
            val count = when { text.contains("unlimited") -> -1; Regex("\\b20\\b").containsMatchIn(text) -> 20; Regex("\\b10\\b").containsMatchIn(text) -> 10; else -> previous.count }
            return LearningRequest.Launch(previous.copy(difficulty = difficulty, count = count, startedAt = System.currentTimeMillis(), total = null, correct = null))
        }
        val arithmetic = Regex("^(?:(?:start|begin|run|do|give me|practice)\\s+)?(?:(?:a|an|some|another)\\s+)?(?:(?:easy|medium|hard)\\s+)?(?:(?:math|arithmetic)\\s+)?(?:drill|quiz|practice|math|arithmetic)(?:\\s+(?:with|of|on|easy|medium|hard|\\d+|questions?|unlimited|please))*$").matches(text) ||
            Regex("^(?:(?:give me|do|practice) )?(?:10|20) (?:easy |medium |hard )?(?:math|arithmetic) questions$").matches(text)
        if (!arithmetic) return null
        val numbers = Regex("\\b\\d+\\b").findAll(text).map { it.value.toIntOrNull() }.toList()
        val levels = listOf("easy", "medium", "hard").filter { Regex("\\b$it\\b").containsMatchIn(text) }
        if (numbers.size > 1 || numbers.any { it !in setOf(10, 20) } || levels.size > 1 || (text.contains("unlimited") && numbers.isNotEmpty())) return LearningRequest.Reply("Choose easy, medium or hard, with 10, 20 or unlimited questions.")
        return LearningRequest.Launch(AssistantLearningContext("drill", levels.singleOrNull() ?: "easy", if (text.contains("unlimited")) -1 else numbers.singleOrNull() ?: 10))
    }
}
