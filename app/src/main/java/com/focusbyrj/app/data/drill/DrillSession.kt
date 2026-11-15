package com.focusbyrj.app.data.drill

data class QuestionRecord(
    val questionNumber: Int,
    val title: String,
    val questionText: String,
    val options: List<String>,
    val correctIndex: Int,
    val userSelectedIndex: Int?,
    val status: String,
    val explanation: String,
    val vocabType: String? = null,
    val vocabId: Int? = null,
)

data class DrillSession(
    val difficulty: String,
    val targetQuestions: Int = -1,
    var correct: Int = 0,
    var total: Int = 0,
    var xp: Int = 0,
    var gold: Int = 0,
    var combo: Int = 0,
    var maxCombo: Int = 0,
    val isBlitz: Boolean = false,
    var blitzSecondsRemaining: Int = 60,
    val startTime: Long = System.currentTimeMillis(),
    val questionRecords: MutableList<QuestionRecord> = mutableListOf(),
    val preGeneratedQuestions: MutableList<String> = mutableListOf(),
    val attemptedIndices: MutableSet<Int> = mutableSetOf(),
    val markedForReview: MutableSet<Int> = mutableSetOf(),
    var highestSeenIndex: Int = 0,
    val isSettled: java.util.concurrent.atomic.AtomicBoolean =
        java.util.concurrent.atomic.AtomicBoolean(false),
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val storageGeneration: Long =
        com.focusbyrj.app.data.drill.DrillSessionRepository.storageGeneration,
)

fun createDrillSessionWithQuestions(difficulty: String, targetQuestions: Int): DrillSession {
    val diffEnum =
        when (difficulty) {
            "hard" -> com.focusbyrj.app.util.ArithmeticDifficulty.HARD
            "medium" -> com.focusbyrj.app.util.ArithmeticDifficulty.MEDIUM
            else -> com.focusbyrj.app.util.ArithmeticDifficulty.EASY
        }
    val count = if (targetQuestions <= 0) 10 else targetQuestions
    val generated =
        (0 until count)
            .map {
                try {
                    val q = com.focusbyrj.app.util.ArithmeticEngine.generateQuestion(diffEnum)
                    org.json
                        .JSONObject()
                        .apply {
                            put("title", q.title)
                            put("questionText", q.questionText)
                            val arr = org.json.JSONArray()
                            q.options.forEach { arr.put(it) }
                            put("options", arr)
                            put("correctIndex", q.correctIndex)
                            put("explanation", q.explanation)
                        }
                        .toString()
                } catch (e: Exception) {
                    org.json
                        .JSONObject()
                        .apply {
                            put("title", "Mental Arithmetic")
                            put("questionText", "What is 12 + 15?")
                            val arr = org.json.JSONArray()
                            listOf("25", "27", "29", "31").forEach { arr.put(it) }
                            put("options", arr)
                            put("correctIndex", 1)
                            put("explanation", "12 + 15 = 27")
                        }
                        .toString()
                }
            }
            .toMutableList()
    return DrillSession(
        difficulty = difficulty,
        targetQuestions = targetQuestions,
        preGeneratedQuestions = generated,
    )
}
