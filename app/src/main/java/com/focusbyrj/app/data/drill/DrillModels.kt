package com.focusbyrj.app.data.drill

import org.json.JSONArray
import org.json.JSONObject

data class DrillQuestionResult(
    val qNum: Int,
    val title: String,
    val direction: String = "",
    val questionText: String,
    val options: List<String>,
    val correctIndex: Int,
    val userSelectedIndex: Int, // -1 if unattempted
    val status: String, // "correct", "wrong", "unattempted"
    val explanation: String,
    val timeTakenSec: Int = 24,
    val accuracyPct: Int = 80,
    val positiveMarks: Double = 1.0,
    val negativeMarks: Double = 0.25
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("qNum", qNum)
        obj.put("title", title)
        obj.put("direction", direction)
        obj.put("questionText", questionText)
        val optsArr = JSONArray()
        options.forEach { optsArr.put(it) }
        obj.put("options", optsArr)
        obj.put("correctIndex", correctIndex)
        obj.put("userSelectedIndex", userSelectedIndex)
        obj.put("status", status)
        obj.put("explanation", explanation)
        obj.put("timeTakenSec", timeTakenSec)
        obj.put("accuracyPct", accuracyPct)
        obj.put("positiveMarks", positiveMarks)
        obj.put("negativeMarks", negativeMarks)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject, defaultAvgSec: Int = 20, index: Int = 0): DrillQuestionResult {
            val optsArr = obj.optJSONArray("options")
            val opts = mutableListOf<String>()
            if (optsArr != null) {
                for (j in 0 until optsArr.length()) {
                    opts.add(optsArr.getString(j))
                }
            }
            val status = obj.optString("status", "unattempted")
            val timeTaken = if (obj.has("timeTakenSec")) {
                obj.optInt("timeTakenSec", defaultAvgSec)
            } else {
                when (index % 3) {
                    0 -> (defaultAvgSec * 1.4).toInt()
                    1 -> (defaultAvgSec * 0.7).toInt().coerceAtLeast(4)
                    else -> defaultAvgSec
                }
            }
            val accuracy = if (obj.has("accuracyPct")) {
                obj.optInt("accuracyPct", 80)
            } else {
                when (status) {
                    "correct" -> 75 + (index * 3) % 20
                    "wrong" -> 45 + (index * 7) % 35
                    else -> 50 + (index * 5) % 30
                }.coerceIn(35, 95)
            }

            return DrillQuestionResult(
                qNum = obj.optInt("qNum", index + 1),
                title = obj.optString("title", "Arithmetic Drill"),
                direction = obj.optString("direction", ""),
                questionText = obj.optString("questionText", ""),
                options = opts,
                correctIndex = obj.optInt("correctIndex", 0),
                userSelectedIndex = obj.optInt("userSelectedIndex", -1),
                status = status,
                explanation = obj.optString("explanation", "Step-by-step mathematical breakdown and shortcut analysis."),
                timeTakenSec = timeTaken,
                accuracyPct = accuracy,
                positiveMarks = obj.optDouble("positiveMarks", 1.0),
                negativeMarks = obj.optDouble("negativeMarks", 0.25)
            )
        }
    }
}

data class DrillSummary(
    val sessionId: String,
    val title: String,
    val tier: Int = 1,
    val level: Int = 1,
    val xpEarned: Int = 0,
    val goldEarned: Int = 0,
    val totalQuestions: Int = 0,
    val correctCount: Int = 0,
    val timeSpentSeconds: Long = 0,
    val timeFormatted: String = "0:00",
    val isPerfect: Boolean = false,
    val isBlitz: Boolean = false,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val streakBonusPercent: Int = 0,
    val streakBonusXp: Int = 0,
    val maxCombo: Int = 0,
    val comboBonusXp: Int = 0,
    val xpBefore: Int = 0,
    val xpNow: Int = 0,
    val xpCurrentLevelStart: Int = 0,
    val xpNextLevelStart: Int = 100,
    val weeklyXp: Int = 0,
    val freezeNotice: String = "",
    val isClaimed: Boolean = false,
    val questions: List<DrillQuestionResult> = emptyList()
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("sessionId", sessionId)
        obj.put("title", title)
        obj.put("tier", tier)
        obj.put("level", level)
        obj.put("xpEarned", xpEarned)
        obj.put("goldEarned", goldEarned)
        obj.put("total", totalQuestions)
        obj.put("correct", correctCount)
        obj.put("timeSpentSeconds", timeSpentSeconds)
        obj.put("elapsedSeconds", timeSpentSeconds.toInt())
        obj.put("timeFormatted", timeFormatted)
        obj.put("isPerfect", isPerfect)
        obj.put("isBlitz", isBlitz)
        obj.put("currentStreak", currentStreak)
        obj.put("longestStreak", longestStreak)
        obj.put("streakBonusPercent", streakBonusPercent)
        obj.put("streakBonusXp", streakBonusXp)
        obj.put("maxCombo", maxCombo)
        obj.put("comboBonusXp", comboBonusXp)
        obj.put("xpBefore", xpBefore)
        obj.put("xpNow", xpNow)
        obj.put("xpCurrentLevelStart", xpCurrentLevelStart)
        obj.put("xpNextLevelStart", xpNextLevelStart)
        obj.put("weeklyXp", weeklyXp)
        obj.put("freezeNotice", freezeNotice)
        obj.put("isClaimed", isClaimed)

        val questionsArr = JSONArray()
        questions.forEach { questionsArr.put(it.toJson()) }
        obj.put("questions", questionsArr)

        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): DrillSummary {
            return try {
                val obj = JSONObject(jsonStr)
                val elapsedSec = obj.optLong("timeSpentSeconds", obj.optLong("elapsedSeconds", 60L))
                val qArr = obj.optJSONArray("questions")
                val questionsList = mutableListOf<DrillQuestionResult>()
                val count = qArr?.length() ?: 0
                val avgSec = (elapsedSec / count.coerceAtLeast(1)).toInt().coerceAtLeast(5)

                if (qArr != null) {
                    for (i in 0 until count) {
                        val qObj = qArr.getJSONObject(i)
                        questionsList.add(DrillQuestionResult.fromJson(qObj, avgSec, i))
                    }
                }

                DrillSummary(
                    sessionId = obj.optString("sessionId", java.util.UUID.randomUUID().toString()),
                    title = obj.optString("title", "XP Olympian"),
                    tier = obj.optInt("tier", 1),
                    level = obj.optInt("level", 1),
                    xpEarned = obj.optInt("xpEarned", 0),
                    goldEarned = obj.optInt("goldEarned", 0),
                    totalQuestions = obj.optInt("total", questionsList.size),
                    correctCount = obj.optInt("correct", 0),
                    timeSpentSeconds = elapsedSec,
                    timeFormatted = obj.optString("timeFormatted", "1:00"),
                    isPerfect = obj.optBoolean("isPerfect", false),
                    isBlitz = obj.optBoolean("isBlitz", false),
                    currentStreak = obj.optInt("currentStreak", 0),
                    longestStreak = obj.optInt("longestStreak", 0),
                    streakBonusPercent = obj.optInt("streakBonusPercent", 0),
                    streakBonusXp = obj.optInt("streakBonusXp", 0),
                    maxCombo = obj.optInt("maxCombo", 0),
                    comboBonusXp = obj.optInt("comboBonusXp", 0),
                    xpBefore = obj.optInt("xpBefore", 0),
                    xpNow = obj.optInt("xpNow", 0),
                    xpCurrentLevelStart = obj.optInt("xpCurrentLevelStart", 0),
                    xpNextLevelStart = obj.optInt("xpNextLevelStart", 100),
                    weeklyXp = obj.optInt("weeklyXp", 0),
                    freezeNotice = obj.optString("freezeNotice", ""),
                    isClaimed = obj.optBoolean("isClaimed", false),
                    questions = questionsList
                )
            } catch (_: Exception) {
                DrillSummary(
                    sessionId = java.util.UUID.randomUUID().toString(),
                    title = "Arithmetic Drill"
                )
            }
        }
    }
}
