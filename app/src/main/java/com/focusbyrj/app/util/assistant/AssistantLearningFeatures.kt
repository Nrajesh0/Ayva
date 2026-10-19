package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.data.drill.*
import com.focusbyrj.app.util.TalkAction
import org.json.JSONObject

/** Coaching uses persisted results, never estimates masquerading as measurements. */
object AssistantLearningFeatures {
    internal fun validSummary(row: DrillSessionEntity): DrillSummary? = runCatching {
        val raw = JSONObject(row.summaryJson)
        require(raw.getString("sessionId") == row.sessionId)
        require(row.totalQuestions > 0 && row.correctCount in 0..row.totalQuestions && row.timeSpentSeconds >= 0)
        require(raw.getInt("total") == row.totalQuestions && raw.getInt("correct") == row.correctCount)
        require(raw.getLong("timeSpentSeconds") == row.timeSpentSeconds)
        val summary = DrillSummary.fromJson(row.summaryJson)
        require(summary.questions.size <= 1000)
        summary.copy(questions = summary.questions.filter { q ->
            q.questionText.isNotBlank() && q.correctIndex in q.options.indices &&
                (q.status != "wrong" || q.userSelectedIndex in q.options.indices && q.userSelectedIndex != q.correctIndex)
        })
    }.getOrNull()
    suspend fun answer(context: Context, state: AssistantSession, input: String): FeatureTurn? {
        val text = input.lowercase().trim().trimEnd('.', '!', '?')
        val lastResult = text in setOf("how did i do last time", "how did i do on my last test", "show my last result", "last test result")
        val compare = text in setOf("compare my last two tests", "compare my last two drills", "compare my last tests", "compare my last two blitz rounds")
        val coach = text in setOf("what should i practise after that test", "what should i practice after that test", "what should i practise next", "what should i practice next")
        val mistakes = text in setOf("review my mistakes", "show my last mistakes", "show my test mistakes", "where am i slow", "which questions were slow")
        if (!lastResult && !compare && !coach && !mistakes) return null
        val rows = DrillDatabase.getDatabase(context).drillSessionDao().getRecentSessions(50)
        val valid = rows.mapNotNull { row -> validSummary(row)?.let { row to it } }
        val requestedKind = if (text.contains("blitz")) "blitz" else if (text.contains("drills")) "drill" else null
        val latest = valid.firstOrNull { requestedKind == null || it.second.practiceKind == requestedKind }
            ?: return FeatureTurn.Reply("No matching valid saved test result is available. Finish a test first; incomplete or malformed records are not used for coaching.")
        val (row, summary) = latest
        state.switchTopic("learning")
        val kind = summary.practiceKind
        val kindText = when (kind) { "drill" -> "arithmetic drill"; "blitz" -> "Speed Blitz"; "vocabulary" -> "vocabulary quiz"; else -> "test (older record; feature and difficulty not recorded)" }
        fun score(r: DrillSessionEntity) = "${r.correctCount}/${r.totalQuestions} attempted correct (${r.correctCount * 100L / r.totalQuestions}%), ${r.timeSpentSeconds}s total"
        if (lastResult) return FeatureTurn.Reply("Last $kindText: ${score(row)}. This is the latest valid saved result, not a prediction of ability.")
        if (compare) {
            if (kind == "unknown") return FeatureTurn.Reply("The latest older result does not record its practice type. I can't reliably compare it with another test. Its saved score: ${score(row)}.")
            val previous = valid.dropWhile { it.first.sessionId != row.sessionId }.drop(1).firstOrNull { it.second.practiceKind == kind }
                ?: return FeatureTurn.Reply("I need two saved results of the same practice type. Latest $kindText: ${score(row)}.")
            val comparable = summary.difficulty != null && summary.difficulty == previous.second.difficulty && row.totalQuestions == previous.first.totalQuestions
            return FeatureTurn.Reply("Latest $kindText: ${score(row)}.\nPrevious $kindText: ${score(previous.first)}.\n" +
                if (comparable) "Same recorded difficulty and attempted count. These are two sessions, so they don't establish a long-term trend. Per-question timing was not measured."
                else "Difficulty or attempted counts differ, or difficulty was not recorded. Treat this as a score comparison; it doesn't establish a speed improvement. Per-question timing was not measured.")
        }
        if (text in setOf("where am i slow", "which questions were slow")) return FeatureTurn.Reply("The last $kindText took ${row.timeSpentSeconds}s across ${row.totalQuestions} attempted questions. Per-question timing was not measured, so I can't identify slow questions from this record.")
        val wrong = summary.questions.filter { it.status == "wrong" }
        val detail = wrong.take(8).joinToString("\n\n") { q ->
            val selected = q.options.getOrNull(q.userSelectedIndex) ?: "not recorded"
            val correct = q.options.getOrNull(q.correctIndex) ?: "not recorded"
            "Q${q.qNum}: ${q.questionText}\nYour answer: $selected\nSaved correct answer: $correct\n${q.explanation.ifBlank { "An explanation was not recorded." }}"
        }
        val actions = mutableListOf<TalkAction>()
        if (kind == "vocabulary") actions += TalkAction.AskQuery("practice missed words", "Practice saved missed words")
        else if (kind == "blitz") actions += TalkAction.AskQuery("/blitz", "Another Blitz")
        else if (kind == "drill" && summary.difficulty in setOf("easy", "medium", "hard")) actions += TalkAction.AskQuery("/drill ${summary.difficulty} 10", "Practise 10 at this difficulty")
        else actions += TalkAction.AskQuery("start a drill", "Choose a new drill")
        val result = "Last $kindText: ${score(row)}.\n" + if (wrong.isEmpty()) {
            if (row.correctCount == row.totalQuestions) "No wrong attempts in this result. Unattempted questions are not wrong attempts."
            else "Wrong-question details were not recorded, so I can't attribute the mistakes to a topic."
        } else "${wrong.size} saved wrong attempts${if (wrong.size > 8) " (showing 8)" else ""}:\n$detail"
        return FeatureTurn.Reply(result + if (coach) "\nReview these saved answers, then try a short practice round. I can't infer a general weakness from one test or fabricate question timings." else "", actions)
    }
}
