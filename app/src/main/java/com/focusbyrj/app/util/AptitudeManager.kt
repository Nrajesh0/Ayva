package com.focusbyrj.app.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AptitudeProfile(
    val xp: Int,
    val level: Int,
    val title: String,
    val titleTier: Int, // 1 to 6 for UI styling
    val xpForNextLevel: Int,
    val xpForCurrentLevel: Int,
    val totalQuestions: Int = 0,
    val correctQuestions: Int = 0,
    val totalDrills: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val streakBonusPercent: Int = 0,
    val isVacationMode: Boolean = false,
    val streakFreezesCount: Int = 1,
    val freezeUsedNotice: String? = null,
    val weeklyXp: Int = 0,
    val isWagerActive: Boolean = false,
    val wagerDaysCompleted: Int = 0,
    val isXpBoostActive: Boolean = false,
    val xpBoostRemainingMinutes: Int = 0,
    val dailyDrillCounts: Map<Int, Long> = emptyMap() // DayOfYear -> drill count for heatmap
) {
    val accuracy: Float
        get() = if (totalQuestions > 0) (correctQuestions.toFloat() / totalQuestions) * 100f else 0f
}

object AptitudeManager {
    private const val PREFS_NAME = "aptitude_economy_prefs"
    private const val KEY_APTITUDE_XP = "aptitude_xp"
    private const val KEY_TOTAL_QUESTIONS = "total_questions"
    private const val KEY_CORRECT_QUESTIONS = "correct_questions"
    private const val KEY_TOTAL_DRILLS = "total_drills"
    private const val KEY_CURRENT_STREAK = "current_streak"
    private const val KEY_LONGEST_STREAK = "longest_streak"
    private const val KEY_LAST_ACTIVE_DATE = "last_active_date"
    const val KEY_VACATION_MODE = "vacation_mode"

    // Gamification Extensions (Duolingo Style)
    private const val KEY_STREAK_FREEZES = "streak_freezes_count"
    private const val KEY_LAST_FREEZE_USED_DATE = "last_freeze_used_date"
    private const val KEY_WEEKLY_XP = "weekly_aptitude_xp"
    private const val KEY_WEEK_KEY = "weekly_week_key"
    private const val KEY_WAGER_ACTIVE = "wager_active"
    private const val KEY_WAGER_DAYS = "wager_days"
    private const val KEY_WAGER_LAST_DATE = "wager_last_date"
    private const val KEY_XP_BOOST_EXPIRY = "xp_boost_expiry_timestamp"
    private const val KEY_XP_BOOST_MULTIPLIER = "xp_boost_multiplier"

    private var prefs: SharedPreferences? = null

    private val _profileFlow = MutableStateFlow(
        calculateProfile(
            xp = 0, tQ = 0, cQ = 0, tD = 0, savedStreak = 0, longestStreak = 0,
            lastDate = "", isVacationMode = false, streakFreezes = 1, freezeUsedNotice = null,
            weeklyXp = 0, isWagerActive = false, wagerDays = 0,
            isXpBoostActive = false, xpBoostRemainingMins = 0,
            dailyDrillCounts = emptyMap()
        )
    )
    val profileFlow: StateFlow<AptitudeProfile> = _profileFlow.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val bubblePrefs = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
        // Sync vacation mode if present in bubble_prefs
        if (bubblePrefs.contains(KEY_VACATION_MODE)) {
            val vm = bubblePrefs.getBoolean(KEY_VACATION_MODE, false)
            prefs?.edit()?.putBoolean(KEY_VACATION_MODE, vm)?.apply()
        }

        // Initialize starter streak freeze if first run
        prefs?.let {
            if (!it.contains(KEY_STREAK_FREEZES)) {
                it.edit().putInt(KEY_STREAK_FREEZES, 1).apply()
            }
        }

        refreshProfile()

        // Sync past drill sessions from Room database into daily heatmap counts
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = com.focusbyrj.app.data.drill.DrillDatabase.getDatabase(context)
                val sessions = db.drillSessionDao().getAllSessionsSync()
                if (sessions.isNotEmpty()) {
                    val p = prefs ?: return@launch
                    var modified = false
                    val cal = Calendar.getInstance()
                    val editor = p.edit()
                    val thirtyDaysAgo = System.currentTimeMillis() - (31L * 24 * 60 * 60 * 1000L)
                    val dayCounts = mutableMapOf<String, Long>()

                    for (session in sessions) {
                        if (session.timestamp >= thirtyDaysAgo) {
                            cal.timeInMillis = session.timestamp
                            val key = getDailyDrillKey(cal)
                            dayCounts[key] = (dayCounts[key] ?: 0L) + 1L
                        }
                    }

                    for ((key, count) in dayCounts) {
                        val currentVal = p.getLong(key, 0L)
                        if (count > currentVal) {
                            editor.putLong(key, count)
                            modified = true
                        }
                    }

                    if (modified) {
                        editor.apply()
                        withContext(Dispatchers.Main) {
                            refreshProfile()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun isVacationMode(context: Context? = null): Boolean {
        if (context != null) {
            val bubblePrefs = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
            if (bubblePrefs.contains(KEY_VACATION_MODE)) {
                return bubblePrefs.getBoolean(KEY_VACATION_MODE, false)
            }
        }
        return prefs?.getBoolean(KEY_VACATION_MODE, false) ?: false
    }

    fun setVacationMode(context: Context, enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_VACATION_MODE, enabled)?.apply()
        context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VACATION_MODE, enabled)
            .apply()
        context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VACATION_MODE, enabled)
            .apply()

        // When disabling vacation mode, set last active date to yesterday so today's drill seamlessly continues the streak
        if (!enabled) {
            val current = _profileFlow.value.currentStreak
            if (current > 0) {
                val yesterday = getYesterdayDateString()
                prefs?.edit()?.putString(KEY_LAST_ACTIVE_DATE, yesterday)?.apply()
            }
            com.focusbyrj.app.service.AptitudeReminderReceiver.scheduleDrillReminders(context)
        } else {
            com.focusbyrj.app.service.AptitudeReminderReceiver.cancelAllReminders(context)
        }

        refreshProfile()
    }

    fun getDailyDrillKey(cal: Calendar): String {
        return "drill_day_${cal.get(Calendar.YEAR)}_${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun getYesterdayDateString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    private fun getDaysBetween(fromDateStr: String, toDateStr: String): Int {
        if (fromDateStr.isEmpty() || toDateStr.isEmpty()) return 999
        if (fromDateStr == toDateStr) return 0
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val fromDate = sdf.parse(fromDateStr) ?: return 999
            val toDate = sdf.parse(toDateStr) ?: return 999
            val diffMs = toDate.time - fromDate.time
            (diffMs / (24 * 60 * 60 * 1000L)).toInt()
        } catch (_: Exception) {
            999
        }
    }

    private fun getCurrentWeekKey(): String {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return SimpleDateFormat("yyyy-'W'ww", Locale.US).format(cal.time)
    }

    fun getStreakFreezesCount(): Int {
        return prefs?.getInt(KEY_STREAK_FREEZES, 1) ?: 1
    }

    fun addStreakFreezes(amount: Int) {
        val p = prefs ?: return
        val current = p.getInt(KEY_STREAK_FREEZES, 1)
        val updated = minOf(3, current + amount)
        p.edit().putInt(KEY_STREAK_FREEZES, updated).apply()
        refreshProfile()
    }

    fun buyStreakFreeze(cost: Int = 1000): Boolean {
        val p = prefs ?: return false
        val currentFreezes = p.getInt(KEY_STREAK_FREEZES, 1)
        if (currentFreezes >= 3) return false // Max 3 shields

        val success = FocusEconomyManager.spendGold(cost)
        if (success) {
            p.edit().putInt(KEY_STREAK_FREEZES, currentFreezes + 1).apply()
            refreshProfile()
            return true
        }
        return false
    }

    fun useStreakFreezeToSkipDay(cost: Int = 1000): Boolean {
        val p = prefs ?: return false
        val currentGold = FocusEconomyManager.profileFlow.value.gold
        if (currentGold < cost) return false

        val success = FocusEconomyManager.spendGold(cost)
        if (!success) return false

        val today = getTodayDateString()
        val yesterday = getYesterdayDateString()
        var savedStreak = p.getInt(KEY_CURRENT_STREAK, 0)
        var longestStreak = p.getInt(KEY_LONGEST_STREAK, 0)
        val lastDate = p.getString(KEY_LAST_ACTIVE_DATE, "") ?: ""

        val newStreak = when {
            lastDate == today -> if (savedStreak <= 0) 1 else savedStreak
            lastDate == yesterday -> savedStreak + 1
            else -> 1
        }
        val newLongest = maxOf(longestStreak, newStreak)

        p.edit()
            .putString(KEY_LAST_ACTIVE_DATE, today)
            .putInt(KEY_CURRENT_STREAK, newStreak)
            .putInt(KEY_LONGEST_STREAK, newLongest)
            .putString(KEY_LAST_FREEZE_USED_DATE, today)
            .apply()

        refreshProfile()
        return true
    }

    fun activateXpBoost(durationMinutes: Int = 15, multiplier: Float = 2.0f) {
        val p = prefs ?: return
        val expiry = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        p.edit()
            .putLong(KEY_XP_BOOST_EXPIRY, expiry)
            .putFloat(KEY_XP_BOOST_MULTIPLIER, multiplier)
            .apply()
        refreshProfile()
    }

    fun getXpMultiplier(): Float {
        val p = prefs ?: return 1.0f
        val expiry = p.getLong(KEY_XP_BOOST_EXPIRY, 0L)
        val now = System.currentTimeMillis()
        return if (now < expiry) {
            p.getFloat(KEY_XP_BOOST_MULTIPLIER, 2.0f)
        } else {
            1.0f
        }
    }

    fun isXpBoostActive(): Boolean {
        val p = prefs ?: return false
        val expiry = p.getLong(KEY_XP_BOOST_EXPIRY, 0L)
        return System.currentTimeMillis() < expiry
    }

    fun getXpBoostRemainingMinutes(): Int {
        val p = prefs ?: return 0
        val expiry = p.getLong(KEY_XP_BOOST_EXPIRY, 0L)
        val remainingMillis = expiry - System.currentTimeMillis()
        return if (remainingMillis > 0) {
            (remainingMillis / (60 * 1000L)).toInt() + 1
        } else {
            0
        }
    }

    fun startWager(goldStake: Int = 5000): Boolean {
        val p = prefs ?: return false
        val isWagerActive = p.getBoolean(KEY_WAGER_ACTIVE, false)
        if (isWagerActive) return false

        val success = FocusEconomyManager.spendGold(goldStake)
        if (success) {
            val today = getTodayDateString()
            p.edit()
                .putBoolean(KEY_WAGER_ACTIVE, true)
                .putInt(KEY_WAGER_DAYS, 0)
                .putString(KEY_WAGER_LAST_DATE, "")
                .apply()
            refreshProfile()
            return true
        }
        return false
    }

    fun refreshProfile() {
        val p = prefs ?: return
        val savedXp = p.getInt(KEY_APTITUDE_XP, 0)
        val tQ = p.getInt(KEY_TOTAL_QUESTIONS, 0)
        val cQ = p.getInt(KEY_CORRECT_QUESTIONS, 0)
        val tD = p.getInt(KEY_TOTAL_DRILLS, 0)
        var savedStreak = p.getInt(KEY_CURRENT_STREAK, 0)
        val longestStreak = p.getInt(KEY_LONGEST_STREAK, 0)
        var lastDate = p.getString(KEY_LAST_ACTIVE_DATE, "") ?: ""
        val vacation = p.getBoolean(KEY_VACATION_MODE, false)
        var freezes = p.getInt(KEY_STREAK_FREEZES, 1)

        val today = getTodayDateString()
        val yesterday = getYesterdayDateString()
        var freezeNotice: String? = null

        val daysBetween = if (lastDate.isNotEmpty()) getDaysBetween(lastDate, today) else 999

        // Streak check and freeze protection logic
        if (!vacation && lastDate.isNotEmpty() && savedStreak > 0) {
            if (daysBetween == 2) {
                // Exactly 1 day was missed (yesterday). Can be protected by a streak freeze!
                if (freezes > 0) {
                    freezes -= 1
                    lastDate = yesterday
                    p.edit()
                        .putInt(KEY_STREAK_FREEZES, freezes)
                        .putString(KEY_LAST_ACTIVE_DATE, yesterday)
                        .putString(KEY_LAST_FREEZE_USED_DATE, today)
                        .apply()
                    freezeNotice = "🛡️ Streak Freeze preserved your $savedStreak-day streak!"
                } else {
                    // No freeze available: streak is broken
                    savedStreak = 0
                    p.edit().putInt(KEY_CURRENT_STREAK, 0).apply()
                }
            } else if (daysBetween > 2) {
                // More than 1 day missed: streak is broken
                savedStreak = 0
                p.edit().putInt(KEY_CURRENT_STREAK, 0).apply()
            }
        }

        // Weekly XP check
        val currentWeek = getCurrentWeekKey()
        val savedWeek = p.getString(KEY_WEEK_KEY, "") ?: ""
        var weeklyXp = p.getInt(KEY_WEEKLY_XP, 0)
        if (savedWeek != currentWeek) {
            weeklyXp = 0
            p.edit().putString(KEY_WEEK_KEY, currentWeek).putInt(KEY_WEEKLY_XP, 0).apply()
        }

        val isWagerActive = p.getBoolean(KEY_WAGER_ACTIVE, false)
        val wagerDays = p.getInt(KEY_WAGER_DAYS, 0)
        val xpBoostActive = isXpBoostActive()
        val xpBoostRemaining = getXpBoostRemainingMinutes()

        // Build 30-day drill counts map for the heatmap
        val dailyDrillMap = mutableMapOf<Int, Long>()
        for (i in 0 downTo -30) {
            val dayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
            val dayOfYear = dayCal.get(Calendar.DAY_OF_YEAR)
            val key = getDailyDrillKey(dayCal)
            val count = p.getLong(key, 0L)
            dailyDrillMap[dayOfYear] = count
        }

        _profileFlow.value = calculateProfile(
            xp = savedXp,
            tQ = tQ,
            cQ = cQ,
            tD = tD,
            savedStreak = savedStreak,
            longestStreak = longestStreak,
            lastDate = lastDate,
            isVacationMode = vacation,
            streakFreezes = freezes,
            freezeUsedNotice = freezeNotice,
            weeklyXp = weeklyXp,
            isWagerActive = isWagerActive,
            wagerDays = wagerDays,
            isXpBoostActive = xpBoostActive,
            xpBoostRemainingMins = xpBoostRemaining,
            dailyDrillCounts = dailyDrillMap
        )
    }

    fun addAptitudeXp(amount: Int) {
        val p = prefs ?: return
        val currentXp = _profileFlow.value.xp
        val newXp = currentXp + amount
        val currentWeekly = p.getInt(KEY_WEEKLY_XP, 0)

        p.edit()
            .putInt(KEY_APTITUDE_XP, newXp)
            .putInt(KEY_WEEKLY_XP, currentWeekly + amount)
            .apply()

        DailyQuestManager.recordXpEarned(amount)
        refreshProfile()
    }

    fun getStreakBonusPercent(): Int {
        val p = _profileFlow.value
        val effectiveStreak = p.currentStreak
        val bonusDays = effectiveStreak.coerceIn(0, 7)
        return bonusDays * 5
    }

    fun recordDrillResult(xpEarned: Int, questions: Int, correct: Int) {
        val p = _profileFlow.value
        val today = getTodayDateString()
        val yesterday = getYesterdayDateString()
        var lastDate = prefs?.getString(KEY_LAST_ACTIVE_DATE, "") ?: ""
        var savedStreak = prefs?.getInt(KEY_CURRENT_STREAK, 0) ?: 0
        val savedLongest = prefs?.getInt(KEY_LONGEST_STREAK, 0) ?: 0
        val vacation = prefs?.getBoolean(KEY_VACATION_MODE, false) ?: false
        var freezes = prefs?.getInt(KEY_STREAK_FREEZES, 1) ?: 1
        var freezeNotice: String? = null

        val daysBetween = if (lastDate.isNotEmpty()) getDaysBetween(lastDate, today) else 999

        // Auto Streak Freeze protection if yesterday was missed
        if (!vacation && lastDate.isNotEmpty() && daysBetween == 2 && savedStreak > 0) {
            if (freezes > 0) {
                freezes -= 1
                lastDate = yesterday
                prefs?.edit()?.putInt(KEY_STREAK_FREEZES, freezes)?.apply()
                freezeNotice = "🛡️ Streak Freeze preserved your $savedStreak-day streak!"
            }
        }

        val newStreak = when {
            vacation -> if (daysBetween == 0) (if (savedStreak <= 0) 1 else savedStreak) else savedStreak + 1
            lastDate == today || daysBetween == 0 -> if (savedStreak <= 0) 1 else savedStreak
            lastDate == yesterday || daysBetween == 1 -> savedStreak + 1
            else -> 1
        }
        val newLongest = maxOf(savedLongest, newStreak)

        val newXp = p.xp + xpEarned
        val newTQ = p.totalQuestions + questions
        val newCQ = p.correctQuestions + correct
        val newTD = p.totalDrills + 1

        // Track daily drill count for heatmap
        val cal = Calendar.getInstance()
        val dailyKey = getDailyDrillKey(cal)
        val currentDailyDrills = prefs?.getLong(dailyKey, 0L) ?: 0L
        val updatedDailyDrills = currentDailyDrills + 1L

        // Weekly XP
        val currentWeek = getCurrentWeekKey()
        val savedWeek = prefs?.getString(KEY_WEEK_KEY, "") ?: ""
        var weeklyXp = prefs?.getInt(KEY_WEEKLY_XP, 0) ?: 0
        if (savedWeek != currentWeek) {
            weeklyXp = xpEarned
        } else {
            weeklyXp += xpEarned
        }

        // 7-Day Wager handling
        var wagerActive = prefs?.getBoolean(KEY_WAGER_ACTIVE, false) ?: false
        var wagerDays = prefs?.getInt(KEY_WAGER_DAYS, 0) ?: 0
        val wagerLastDate = prefs?.getString(KEY_WAGER_LAST_DATE, "") ?: ""

        if (wagerActive) {
            if (daysBetween > 1 && !vacation && freezeNotice == null) {
                // Streak broken and no freeze -> Wager failed
                wagerActive = false
                wagerDays = 0
            } else if (wagerLastDate != today) {
                wagerDays += 1
                prefs?.edit()?.putString(KEY_WAGER_LAST_DATE, today)?.apply()
                if (wagerDays >= 7) {
                    FocusEconomyManager.addRewards(baseXp = 100, baseGold = 10000)
                    wagerActive = false
                    wagerDays = 0
                }
            }
        }

        prefs?.edit()?.apply {
            putInt(KEY_APTITUDE_XP, newXp)
            putInt(KEY_TOTAL_QUESTIONS, newTQ)
            putInt(KEY_CORRECT_QUESTIONS, newCQ)
            putInt(KEY_TOTAL_DRILLS, newTD)
            putInt(KEY_CURRENT_STREAK, newStreak)
            putInt(KEY_LONGEST_STREAK, newLongest)
            putString(KEY_LAST_ACTIVE_DATE, today)
            putLong(dailyKey, updatedDailyDrills)
            putString(KEY_WEEK_KEY, currentWeek)
            putInt(KEY_WEEKLY_XP, weeklyXp)
            putBoolean(KEY_WAGER_ACTIVE, wagerActive)
            putInt(KEY_WAGER_DAYS, wagerDays)
        }

        // Sync streak rewards to economy
        FocusEconomyManager.syncStreaks(newStreak, newLongest)

        // Report to DailyQuestManager
        DailyQuestManager.recordXpEarned(xpEarned)

        val xpBoostActive = isXpBoostActive()
        val xpBoostRemaining = getXpBoostRemainingMinutes()

        // Build updated daily counts map
        val updatedMap = p.dailyDrillCounts.toMutableMap()
        updatedMap[cal.get(Calendar.DAY_OF_YEAR)] = updatedDailyDrills

        _profileFlow.value = calculateProfile(
            xp = newXp,
            tQ = newTQ,
            cQ = newCQ,
            tD = newTD,
            savedStreak = newStreak,
            longestStreak = newLongest,
            lastDate = today,
            isVacationMode = vacation,
            streakFreezes = freezes,
            freezeUsedNotice = freezeNotice,
            weeklyXp = weeklyXp,
            isWagerActive = wagerActive,
            wagerDays = wagerDays,
            isXpBoostActive = xpBoostActive,
            xpBoostRemainingMins = xpBoostRemaining,
            dailyDrillCounts = updatedMap
        )
    }

    private fun getLevelForXp(xp: Int): Int {
        return (sqrt(xp / 100.0)).toInt() + 1
    }

    private fun getXpForLevel(level: Int): Int {
        return (level - 1) * (level - 1) * 100
    }

    private fun calculateProfile(
        xp: Int,
        tQ: Int,
        cQ: Int,
        tD: Int,
        savedStreak: Int,
        longestStreak: Int,
        lastDate: String,
        isVacationMode: Boolean,
        streakFreezes: Int,
        freezeUsedNotice: String?,
        weeklyXp: Int,
        isWagerActive: Boolean,
        wagerDays: Int,
        isXpBoostActive: Boolean,
        xpBoostRemainingMins: Int,
        dailyDrillCounts: Map<Int, Long> = emptyMap()
    ): AptitudeProfile {
        val level = getLevelForXp(xp)
        val currentLevelXp = getXpForLevel(level)
        val nextLevelXp = getXpForLevel(level + 1)

        val (title, tier) = when {
            level >= 76 -> "Aptitude Grandmaster" to 6
            level >= 51 -> "Human Calculator" to 5
            level >= 31 -> "Quant Mastermind" to 4
            level >= 16 -> "Speed Math Specialist" to 3
            level >= 6 -> "Arithmetic Scholar" to 2
            else -> "Novice Number-Cruncher" to 1
        }

        val today = getTodayDateString()
        val yesterday = getYesterdayDateString()
        val daysBetween = if (lastDate.isNotEmpty()) getDaysBetween(lastDate, today) else 999
        val currentStreak = when {
            isVacationMode -> savedStreak
            lastDate.isEmpty() -> 0
            daysBetween <= 1 || lastDate == today || lastDate == yesterday -> savedStreak
            else -> 0
        }

        val streakBonusPercent = minOf(currentStreak, 7) * 5

        return AptitudeProfile(
            xp = xp,
            level = level,
            title = title,
            titleTier = tier,
            xpForNextLevel = nextLevelXp,
            xpForCurrentLevel = currentLevelXp,
            totalQuestions = tQ,
            correctQuestions = cQ,
            totalDrills = tD,
            currentStreak = currentStreak,
            longestStreak = maxOf(longestStreak, currentStreak),
            streakBonusPercent = streakBonusPercent,
            isVacationMode = isVacationMode,
            streakFreezesCount = streakFreezes,
            freezeUsedNotice = freezeUsedNotice,
            weeklyXp = weeklyXp,
            isWagerActive = isWagerActive,
            wagerDaysCompleted = wagerDays,
            isXpBoostActive = isXpBoostActive,
            xpBoostRemainingMinutes = xpBoostRemainingMins,
            dailyDrillCounts = dailyDrillCounts
        )
    }
}
