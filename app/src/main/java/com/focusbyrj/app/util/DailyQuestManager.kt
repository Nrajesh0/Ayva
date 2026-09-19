// Daily Quest Manager - v1.4.0
package com.focusbyrj.app.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyQuest(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val currentProgress: Int,
    val target: Int,
    val isCompleted: Boolean,
    val rewardXp: Int,
    val rewardGold: Int
) {
    val progressFraction: Float
        get() = (currentProgress.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

data class MysteryReward(
    val xp: Int,
    val gold: Int,
    val streakFreezeAwarded: Boolean,
    val bonusGoldInsteadOfFreeze: Int = 0,
    val rarityTitle: String = "QUANTUM",
    val freezesCount: Int = 1
)

data class DailyQuestState(
    val dateKey: String,
    val quests: List<DailyQuest>,
    val allCompleted: Boolean,
    val morningChestClaimed: Boolean,
    val eveningChestClaimed: Boolean,
    val earlyBirdEarned: Boolean = false,
    val isEarlyBirdAvailable: Boolean = false,
    val hoursUntilEarlyBird: Int = 0,
    val nightOwlEarned: Boolean = false,
    val isNightOwlAvailable: Boolean = false,
    val isMorningAvailable: Boolean = false,
    val isEveningAvailable: Boolean = false,
    val lastReward: MysteryReward? = null
)

object DailyQuestManager {
    private const val PREFS_NAME = "daily_learning_quests_prefs"
    private const val KEY_DATE = "quest_date"
    private const val KEY_DRILLS_COMPLETED = "quest_drills_completed"
    private const val KEY_MORNING_CHEST_CLAIMED = "quest_morning_chest_claimed"
    private const val KEY_EVENING_CHEST_CLAIMED = "quest_evening_chest_claimed"
    private const val KEY_QUEST_REWARD_CLAIMED = "quest_reward_claimed"
    private const val KEY_EARLY_BIRD_EARNED_DATE = "quest_early_bird_earned_date"
    private const val KEY_NIGHT_OWL_EARNED_DATE = "quest_early_bird_earned_date_night"

    private const val TARGET_DRILLS = 1

    private var prefs: SharedPreferences? = null
    private val _stateFlow = MutableStateFlow(
        DailyQuestState(
            dateKey = "",
            quests = emptyList(),
            allCompleted = false,
            morningChestClaimed = false,
            eveningChestClaimed = false,
            earlyBirdEarned = false,
            isEarlyBirdAvailable = false,
            hoursUntilEarlyBird = 0,
            nightOwlEarned = false,
            isNightOwlAvailable = false,
            isMorningAvailable = false,
            isEveningAvailable = false
        )
    )
    val stateFlow: StateFlow<DailyQuestState> = _stateFlow.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        refreshState()
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun refreshState() {
        val p = prefs ?: return
        val today = getTodayDateString()
        val savedDate = p.getString(KEY_DATE, "") ?: ""

        if (savedDate != today) {
            p.edit()
                .putString(KEY_DATE, today)
                .putInt(KEY_DRILLS_COMPLETED, 0)
                .putBoolean(KEY_MORNING_CHEST_CLAIMED, false)
                .putBoolean(KEY_EVENING_CHEST_CLAIMED, false)
                .putBoolean(KEY_QUEST_REWARD_CLAIMED, false)
                .apply()
        }

        val drills = p.getInt(KEY_DRILLS_COMPLETED, 0)
        val morningClaimed = p.getBoolean(KEY_MORNING_CHEST_CLAIMED, false)
        val eveningClaimed = p.getBoolean(KEY_EVENING_CHEST_CLAIMED, false)
        val questClaimed = p.getBoolean(KEY_QUEST_REWARD_CLAIMED, false)

        val ebEarnedDate = p.getString(KEY_EARLY_BIRD_EARNED_DATE, "") ?: ""
        val noEarnedDate = p.getString(KEY_NIGHT_OWL_EARNED_DATE, "") ?: ""

        val isCompleted = drills >= TARGET_DRILLS
        if (isCompleted && !questClaimed) {
            p.edit().putBoolean(KEY_QUEST_REWARD_CLAIMED, true).apply()
            AptitudeManager.addAptitudeXp(100)
            FocusEconomyManager.addDirectGold(50)
        }

        val quest = DailyQuest(
            id = "quest_daily_drill",
            title = "Focus Continuation",
            description = "Attempt a test daily and continue your streak",
            icon = "🎯",
            currentProgress = minOf(drills, TARGET_DRILLS),
            target = TARGET_DRILLS,
            isCompleted = isCompleted,
            rewardXp = 100,
            rewardGold = 50
        )

        val quests = listOf(quest)
        val allDone = quests.all { it.isCompleted }

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY) // 0..23

        // Early Bird: Complete test in daytime (6am - 6pm) -> Ready to claim immediately!
        val earlyBirdEarnedToday = (ebEarnedDate == today)
        val isEarlyBirdAvailable = earlyBirdEarnedToday && !morningClaimed

        // Night Owl: Complete test in evening/night -> Ready to claim immediately!
        val nightOwlEarnedToday = (noEarnedDate == today)
        val isNightOwlAvailable = nightOwlEarnedToday && !eveningClaimed

        _stateFlow.value = DailyQuestState(
            dateKey = today,
            quests = quests,
            allCompleted = allDone,
            morningChestClaimed = morningClaimed,
            eveningChestClaimed = eveningClaimed,
            earlyBirdEarned = earlyBirdEarnedToday,
            isEarlyBirdAvailable = isEarlyBirdAvailable,
            hoursUntilEarlyBird = 0,
            nightOwlEarned = nightOwlEarnedToday,
            isNightOwlAvailable = isNightOwlAvailable,
            isMorningAvailable = isEarlyBirdAvailable,
            isEveningAvailable = isNightOwlAvailable
        )
    }

    fun recordDrillCompleted() {
        val p = prefs ?: return
        refreshState()
        val today = getTodayDateString()
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        val current = p.getInt(KEY_DRILLS_COMPLETED, 0)
        val editor = p.edit().putInt(KEY_DRILLS_COMPLETED, current + 1)

        // Track test completion time for chest unlocks
        if (hour in 6..17) {
            editor.putString(KEY_EARLY_BIRD_EARNED_DATE, today)
        } else {
            editor.putString(KEY_NIGHT_OWL_EARNED_DATE, today)
        }
        editor.apply()

        refreshState()
    }
    
    fun recordCorrectAnswer() {}
    fun recordCombo(combo: Int) {}
    fun recordXpEarned(xp: Int) {}

    fun markMorningChestClaimed() {
        prefs?.edit()?.putBoolean(KEY_MORNING_CHEST_CLAIMED, true)?.apply()
        refreshState()
    }

    fun markEveningChestClaimed() {
        prefs?.edit()?.putBoolean(KEY_EVENING_CHEST_CLAIMED, true)?.apply()
        refreshState()
    }
    
    fun claimMysteryChest(): MysteryReward? {
        val state = _stateFlow.value
        if (state.isEarlyBirdAvailable) {
            markMorningChestClaimed()
        } else if (state.isNightOwlAvailable) {
            markEveningChestClaimed()
        } else {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            if (hour in 6..17) {
                markMorningChestClaimed()
            } else {
                markEveningChestClaimed()
            }
        }
        return null
    }

    fun isAnyChestAvailable(): Boolean {
        refreshState()
        val s = _stateFlow.value
        return s.isEarlyBirdAvailable || s.isNightOwlAvailable
    }
}
