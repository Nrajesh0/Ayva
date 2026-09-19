package com.focusbyrj.app.util

import com.focusbyrj.app.ui.screens.ChatMessage
import com.focusbyrj.app.ui.screens.DrillSession
import com.focusbyrj.app.ui.screens.QuestionRecord
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

object DrillSummaryHelper {
    fun generateSummaryMessage(session: DrillSession): ChatMessage {
        val baseMultiplier = when (session.difficulty) {
            "medium" -> 25
            "hard" -> 30
            else -> 20
        }
        
        var xpEarned = session.correct * baseMultiplier
        val isPerfect = session.targetQuestions >= 10 && session.correct == session.targetQuestions
        if (isPerfect) {
            xpEarned *= 2
        }

        // Combo bonus XP
        val comboBonusXp = when {
            session.maxCombo >= 8 -> (session.correct * 15)
            session.maxCombo >= 5 -> (session.correct * 10)
            session.maxCombo >= 3 -> (session.correct * 5)
            else -> 0
        }
        xpEarned += comboBonusXp

        val streakBonusPercent = AptitudeManager.getStreakBonusPercent()
        val streakBonusXp = if (streakBonusPercent > 0) {
            ((xpEarned * streakBonusPercent) / 100.0).roundToInt()
        } else {
            0
        }
        xpEarned += streakBonusXp

        // Potion / Beaker XP Boost Multiplier (e.g. 2X EXP)
        val boostMultiplier = AptitudeManager.getXpMultiplier()
        if (boostMultiplier > 1.0f) {
            xpEarned = (xpEarned * boostMultiplier).roundToInt()
        }

        // Sync gold to user wallet
        if (session.gold > 0) {
            FocusEconomyManager.addRewards(baseXp = 0, baseGold = session.gold)
        }

        val profileBefore = AptitudeManager.profileFlow.value
        AptitudeManager.recordDrillResult(xpEarned, session.total, session.correct)
        val profileNow = AptitudeManager.profileFlow.value

        // Check if daily quest was progressed by combo
        if (session.maxCombo >= 4) {
            DailyQuestManager.recordCombo(session.maxCombo)
        }
        
        DailyQuestManager.recordDrillCompleted()

        val elapsedSeconds = if (session.isBlitz) {
            (60 - session.blitzSecondsRemaining).coerceIn(1, 60)
        } else {
            ((System.currentTimeMillis() - session.startTime) / 1000).toInt().coerceAtLeast(1)
        }
        val mins = elapsedSeconds / 60
        val secs = elapsedSeconds % 60
        val timeFormatted = "$mins:${secs.toString().padStart(2, '0')}"

        val sessionId = UUID.randomUUID().toString()
        val questionsList = mutableListOf<com.focusbyrj.app.data.drill.DrillQuestionResult>()
        val recordedNums = mutableSetOf<Int>()
        val avgSecPerQ = (elapsedSeconds / (session.questionRecords.size.coerceAtLeast(1))).coerceAtLeast(5)

        session.questionRecords.forEachIndexed { idx, qRec ->
            recordedNums.add(qRec.questionNumber)
            val accuracy = when (qRec.status) {
                "correct" -> 75 + (idx * 3) % 20
                "wrong" -> 45 + (idx * 7) % 35
                else -> 50 + (idx * 5) % 30
            }.coerceIn(35, 95)
            val timeTaken = when (idx % 3) {
                0 -> (avgSecPerQ * 1.4).toInt()
                1 -> (avgSecPerQ * 0.7).toInt().coerceAtLeast(4)
                else -> avgSecPerQ
            }
            questionsList.add(
                com.focusbyrj.app.data.drill.DrillQuestionResult(
                    qNum = qRec.questionNumber,
                    title = qRec.title,
                    direction = if (idx == 0) "Read the problem carefully and select the single correct option." else "",
                    questionText = qRec.questionText,
                    options = qRec.options,
                    correctIndex = qRec.correctIndex,
                    userSelectedIndex = qRec.userSelectedIndex ?: -1,
                    status = qRec.status,
                    explanation = qRec.explanation,
                    timeTakenSec = timeTaken,
                    accuracyPct = accuracy
                )
            )
        }

        // Include any remaining unattempted pre-generated questions so Solutions has the complete question paper
        if (session.preGeneratedQuestions.isNotEmpty()) {
            session.preGeneratedQuestions.forEachIndexed { idx, qJson ->
                val qNum = idx + 1
                if (!recordedNums.contains(qNum)) {
                    try {
                        val qObj = JSONObject(qJson)
                        val optsArr = qObj.optJSONArray("options")
                        val opts = mutableListOf<String>()
                        if (optsArr != null) {
                            for (j in 0 until optsArr.length()) opts.add(optsArr.getString(j))
                        }
                        questionsList.add(
                            com.focusbyrj.app.data.drill.DrillQuestionResult(
                                qNum = qNum,
                                title = qObj.optString("title", if (session.isBlitz) "⚡ Speed Blitz" else "Arithmetic Drill"),
                                direction = "",
                                questionText = qObj.optString("questionText", ""),
                                options = opts,
                                correctIndex = qObj.optInt("correctIndex", 0),
                                userSelectedIndex = -1,
                                status = "unattempted",
                                explanation = qObj.optString("explanation", "Step-by-step mathematical breakdown and shortcut analysis."),
                                timeTakenSec = avgSecPerQ,
                                accuracyPct = 60
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
        }

        val typedSummary = com.focusbyrj.app.data.drill.DrillSummary(
            sessionId = sessionId,
            title = profileNow.title,
            tier = profileNow.titleTier,
            level = profileNow.level,
            xpEarned = xpEarned,
            goldEarned = session.gold,
            totalQuestions = session.total,
            correctCount = session.correct,
            timeSpentSeconds = elapsedSeconds.toLong(),
            timeFormatted = timeFormatted,
            isPerfect = isPerfect,
            isBlitz = session.isBlitz,
            currentStreak = profileNow.currentStreak,
            longestStreak = profileNow.longestStreak,
            streakBonusPercent = profileNow.streakBonusPercent,
            streakBonusXp = streakBonusXp,
            maxCombo = session.maxCombo,
            comboBonusXp = comboBonusXp,
            xpBefore = profileBefore.xp,
            xpNow = profileNow.xp,
            xpCurrentLevelStart = profileNow.xpForCurrentLevel,
            xpNextLevelStart = profileNow.xpForNextLevel,
            weeklyXp = profileNow.weeklyXp,
            freezeNotice = profileNow.freezeUsedNotice ?: "",
            isClaimed = false,
            questions = questionsList
        )

        // Store in DrillSessionRepository (in-memory + Room)
        com.focusbyrj.app.data.drill.DrillSessionRepository.saveSummary(typedSummary)

        return ChatMessage(
            id = sessionId,
            text = "Drill Summary",
            isUser = false,
            timestamp = System.currentTimeMillis(),
            firstViewedTimestamp = System.currentTimeMillis(),
            isDrillSummary = true,
            drillSummaryJson = typedSummary.toJson()
        )
    }
}
