package com.focusbyrj.app.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

data class UserProfile(
    val name: String,
    val xp: Int,
    val gold: Int,
    val level: Int,
    val maxLevel: Int,
    val maxGold: Int,
    val avatarTier: Int,
    val selectedAvatar: String,
    val purchasedAvatars: Set<String>,
    val currentStreak: Int,
    val longestStreak: Int,
    val pendingXp: Int = 0,
    val pendingGold: Int = 0,
    val lifetimeFocusMins: Int = 0,
    val lifetimeResists: Int = 0,
    val lifetimeTasksCompleted: Int = 0,
    val streakFreezes: Int = 1
) {
    fun xpCurrentLevelBase(): Int = FocusEconomyManager.requiredXpForLevel(level)
    fun xpNextLevelThreshold(): Int = FocusEconomyManager.requiredXpForLevel(level + 1)
    fun xpGainedInCurrentLevel(): Int = max(0, xp - xpCurrentLevelBase())
    fun xpNeededForNextLevel(): Int = max(1, xpNextLevelThreshold() - xpCurrentLevelBase())
    fun levelProgressFraction(): Float = (xpGainedInCurrentLevel().toFloat() / xpNeededForNextLevel().toFloat()).coerceIn(0f, 1f)
}


sealed class EconomyEvent {
    data class AchievementUnlocked(val title: String, val iconRes: Int? = null) : EconomyEvent()
    data class RewardsEarned(val xp: Int, val gold: Int, val source: String) : EconomyEvent()
}

object FocusEconomyManager {
    private const val PREFS_NAME = "focus_economy_prefs"
    
    private val _economyEvents = MutableSharedFlow<EconomyEvent>(extraBufferCapacity = 10)
    val economyEvents = _economyEvents.asSharedFlow()

    private fun emitEvent(event: EconomyEvent) {
        _economyEvents.tryEmit(event)
    }

    private val _profileFlow = MutableStateFlow(
        UserProfile("Focus Warrior", 0, 0, 1, 1, 0, 1, "tier_1", emptySet(), 0, 0, 0, 0, 0, 0, 0, 1)
    )
    val profileFlow: StateFlow<UserProfile> = _profileFlow.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadProfile()
    }

    private fun loadProfile() {
        val oldProfile = _profileFlow.value
        prefs?.let {
            val name = it.getString("user_name", "Focus Warrior") ?: "Focus Warrior"
            val xp = it.getInt("xp", 0)
            val gold = it.getInt("gold", 0)
            val level = calculateLevel(xp)
            val maxLevel = it.getInt("max_level", level)
            
            if (level > maxLevel) {
                it.edit().putInt("max_level", level).apply()
            }
            
            val finalMaxLevel = max(level, maxLevel)
            val maxGold = max(gold, it.getInt("max_gold", 0))
            if (gold > it.getInt("max_gold", 0)) {
                it.edit().putInt("max_gold", maxGold).apply()
            }
            
            val purchased = it.getStringSet("purchased_avatars", emptySet()) ?: emptySet()
            val unlockedTier = calculateUnlockedAvatarTier(maxGold)
            val selectedAvatar = it.getString("selected_avatar", "tier_$unlockedTier") ?: "tier_$unlockedTier"
            
            _profileFlow.value = UserProfile(
                name = name,
                xp = xp,
                gold = gold,
                level = level,
                maxLevel = finalMaxLevel,
                maxGold = maxGold,
                avatarTier = unlockedTier,
                selectedAvatar = selectedAvatar,
                purchasedAvatars = purchased,
                currentStreak = it.getInt("current_streak", 0),
                longestStreak = it.getInt("longest_streak", 0),
                pendingXp = it.getInt("pending_xp", 0),
                pendingGold = it.getInt("pending_gold", 0),
                lifetimeFocusMins = it.getInt("lifetime_focus_mins", 0),
                lifetimeResists = it.getInt("lifetime_resists", 0),
                lifetimeTasksCompleted = it.getInt("lifetime_tasks_completed", 0),
                streakFreezes = it.getInt("streak_freezes", 1)
            )
        }
        if (oldProfile.xp > 0 || oldProfile.gold > 0 || oldProfile.lifetimeFocusMins > 0 || oldProfile.lifetimeTasksCompleted > 0) {
            checkAchievements(oldProfile, _profileFlow.value)
        }
    }

    private fun checkAchievements(old: UserProfile, new: UserProfile) {
        if (old.longestStreak < 3 && new.longestStreak >= 3) emitEvent(EconomyEvent.AchievementUnlocked("3-Day Streak"))
        if (old.longestStreak < 7 && new.longestStreak >= 7) emitEvent(EconomyEvent.AchievementUnlocked("7-Day Streak"))
        if (old.longestStreak < 30 && new.longestStreak >= 30) emitEvent(EconomyEvent.AchievementUnlocked("30-Day Streak"))
        if (old.longestStreak < 60 && new.longestStreak >= 60) emitEvent(EconomyEvent.AchievementUnlocked("60-Day Streak"))
        if (old.longestStreak < 100 && new.longestStreak >= 100) emitEvent(EconomyEvent.AchievementUnlocked("100-Day Streak"))
        if (old.longestStreak < 365 && new.longestStreak >= 365) emitEvent(EconomyEvent.AchievementUnlocked("365-Day Streak"))

        if (old.gold < 100 && new.gold >= 100) emitEvent(EconomyEvent.AchievementUnlocked("Piggy Bank"))
        if (old.gold < 1000 && new.gold >= 1000) emitEvent(EconomyEvent.AchievementUnlocked("Savings"))
        if (old.gold < 5000 && new.gold >= 5000) emitEvent(EconomyEvent.AchievementUnlocked("Wealthy"))
        if (old.gold < 10000 && new.gold >= 10000) emitEvent(EconomyEvent.AchievementUnlocked("Hoarder"))
        if (old.gold < 50000 && new.gold >= 50000) emitEvent(EconomyEvent.AchievementUnlocked("Midas Touch"))
        if (old.gold < 100000 && new.gold >= 100000) emitEvent(EconomyEvent.AchievementUnlocked("Treasury"))
        
        if (old.lifetimeFocusMins < 60 && new.lifetimeFocusMins >= 60) emitEvent(EconomyEvent.AchievementUnlocked("Getting Started"))
        if (old.lifetimeFocusMins < 600 && new.lifetimeFocusMins >= 600) emitEvent(EconomyEvent.AchievementUnlocked("Flow State"))
        if (old.lifetimeFocusMins < 3000 && new.lifetimeFocusMins >= 3000) emitEvent(EconomyEvent.AchievementUnlocked("Zone In"))
        if (old.lifetimeFocusMins < 30000 && new.lifetimeFocusMins >= 30000) emitEvent(EconomyEvent.AchievementUnlocked("Monk Mode"))
        if (old.lifetimeFocusMins < 60000 && new.lifetimeFocusMins >= 60000) emitEvent(EconomyEvent.AchievementUnlocked("Time Lord"))
        if (old.lifetimeFocusMins < 120000 && new.lifetimeFocusMins >= 120000) emitEvent(EconomyEvent.AchievementUnlocked("Master of Time"))
        
        if (old.lifetimeResists < 1 && new.lifetimeResists >= 1) emitEvent(EconomyEvent.AchievementUnlocked("First Temptation"))
        if (old.lifetimeResists < 10 && new.lifetimeResists >= 10) emitEvent(EconomyEvent.AchievementUnlocked("Iron Will"))
        if (old.lifetimeResists < 50 && new.lifetimeResists >= 50) emitEvent(EconomyEvent.AchievementUnlocked("Willpower"))
        if (old.lifetimeResists < 100 && new.lifetimeResists >= 100) emitEvent(EconomyEvent.AchievementUnlocked("Dopamine Detox"))
        if (old.lifetimeResists < 1000 && new.lifetimeResists >= 1000) emitEvent(EconomyEvent.AchievementUnlocked("Zen Mind"))

        // Task Completion Achievements
        if (old.lifetimeTasksCompleted < 1 && new.lifetimeTasksCompleted >= 1) emitEvent(EconomyEvent.AchievementUnlocked("Task Starter"))
        if (old.lifetimeTasksCompleted < 10 && new.lifetimeTasksCompleted >= 10) emitEvent(EconomyEvent.AchievementUnlocked("Productive Flow"))
        if (old.lifetimeTasksCompleted < 50 && new.lifetimeTasksCompleted >= 50) emitEvent(EconomyEvent.AchievementUnlocked("Task Master"))
        if (old.lifetimeTasksCompleted < 100 && new.lifetimeTasksCompleted >= 100) emitEvent(EconomyEvent.AchievementUnlocked("Unstoppable Finisher"))
        
        if (old.avatarTier < 1 && new.avatarTier >= 1) emitEvent(EconomyEvent.AchievementUnlocked("Scholar"))
        if (old.avatarTier < 2 && new.avatarTier >= 2) emitEvent(EconomyEvent.AchievementUnlocked("Knight"))
        if (old.avatarTier < 3 && new.avatarTier >= 3) emitEvent(EconomyEvent.AchievementUnlocked("Noble"))
        if (old.avatarTier < 4 && new.avatarTier >= 4) emitEvent(EconomyEvent.AchievementUnlocked("Emperor"))
    }

    fun updateName(newName: String) {
        prefs?.edit()?.putString("user_name", newName)?.apply()
        loadProfile()
    }

    fun purchaseAvatar(avatarId: String, cost: Int): Boolean {
        prefs?.let { p ->
            val currentProfile = _profileFlow.value
            if (currentProfile.gold >= cost && !currentProfile.purchasedAvatars.contains(avatarId)) {
                val newPurchased = currentProfile.purchasedAvatars.toMutableSet().apply { add(avatarId) }
                p.edit()
                    .putInt("gold", currentProfile.gold - cost)
                    .putStringSet("purchased_avatars", newPurchased)
                    .putString("selected_avatar", avatarId)
                    .apply()
                loadProfile()
                return true
            }
        }
        return false
    }

    fun equipAvatar(avatarId: String) {
        prefs?.edit()?.putString("selected_avatar", avatarId)?.apply()
        loadProfile()
    }
    
    fun calculateLevel(xp: Int): Int {
        var level = 1
        while (level < 200 && requiredXpForLevel(level + 1) <= xp) {
            level++
        }
        return level
    }

    fun requiredXpForLevel(level: Int): Int {
        if (level <= 1) return 0
        val baseAt100 = 99 * 100 + (99 * 99) * 25
        val constantDeltaAfter100 = (99 * 100 + 99 * 99 * 25) - (98 * 100 + 98 * 98 * 25)
        
        return if (level <= 100) {
            (level - 1) * 100 + ((level - 1) * (level - 1)) * 25
        } else {
            baseAt100 + (level - 100) * constantDeltaAfter100
        }
    }
    
    fun getGoldMultiplier(level: Int): Float {
        return when (level) {
            in 1..10 -> 1.0f
            in 11..25 -> 1.2f
            in 26..50 -> 1.5f
            in 51..75 -> 2.0f
            in 76..100 -> 2.5f
            in 101..150 -> 3.0f
            in 151..199 -> 3.5f
            else -> 4.0f
        }
    }
    
    fun applySoftUnlockPenalty() {
        prefs?.let { p ->
            val currentProfile = _profileFlow.value
            val currentLevel = currentProfile.level
            
            val penalty = when (currentLevel) {
                in 1..5 -> 25
                in 6..15 -> 100
                in 16..30 -> (currentProfile.xp * 0.05f).toInt()
                else -> (currentProfile.xp * 0.10f).toInt()
            }
            
            var newXp = max(0, currentProfile.xp - penalty)
            val minAllowedLevel = max(1, currentProfile.maxLevel - 5)
            val minAllowedXp = requiredXpForLevel(minAllowedLevel)
            
            if (newXp < minAllowedXp) {
                newXp = minAllowedXp
            }
            
            p.edit().putInt("xp", newXp).apply()
            loadProfile()
        }
    }

    fun claimPendingRewards() {
        prefs?.let { p ->
            val pXp = _profileFlow.value.pendingXp
            val pGold = _profileFlow.value.pendingGold
            
            p.edit()
                .putInt("xp", _profileFlow.value.xp + pXp)
                .putInt("gold", _profileFlow.value.gold + pGold)
                .putInt("pending_xp", 0)
                .putInt("pending_gold", 0)
                .apply()
            loadProfile()
            if (pXp > 0 || pGold > 0) {
                emitEvent(EconomyEvent.RewardsEarned(pXp, pGold, "Claimed Rewards"))
            }
        }
    }
    
    fun spendGold(amount: Int): Boolean {
        val p = prefs ?: return false
        val currentGold = _profileFlow.value.gold
        if (currentGold >= amount) {
            p.edit().putInt("gold", currentGold - amount).apply()
            loadProfile()
            return true
        }
        return false
    }

    fun recoverXp(goldCost: Int, xpGain: Int) {
        prefs?.let { p ->
            val currentGold = _profileFlow.value.gold
            if (currentGold >= goldCost) {
                p.edit()
                    .putInt("gold", currentGold - goldCost)
                    .putInt("xp", _profileFlow.value.xp + xpGain)
                    .apply()
                loadProfile()
            }
        }
    }
    
    fun addExactRewards(exactXp: Int, exactGold: Int) {
        prefs?.let { p ->
            val pXp = _profileFlow.value.pendingXp
            val pGold = _profileFlow.value.pendingGold
            
            p.edit()
                .putInt("pending_xp", pXp + exactXp)
                .putInt("pending_gold", pGold + exactGold)
                .apply()
            loadProfile()
        }
    }
    
    fun addRewards(baseXp: Int, baseGold: Int) {
        prefs?.let { p ->
            val currentLevel = _profileFlow.value.level
            val goldMultiplier = getGoldMultiplier(currentLevel)
            val finalGold = (baseGold * goldMultiplier).toInt()
            
            val pXp = _profileFlow.value.pendingXp
            val pGold = _profileFlow.value.pendingGold
            
            p.edit()
                .putInt("pending_xp", pXp + baseXp)
                .putInt("pending_gold", pGold + finalGold)
                .apply()
            loadProfile()
        }
    }

    fun addDurationBasedRewards(totalMinsFocusedSoFar: Int) {
        prefs?.let { p ->
            val currentLevel = _profileFlow.value.level
            val goldMultiplier = getGoldMultiplier(currentLevel)
            
            val baseXp = 50
            val baseGold = when(totalMinsFocusedSoFar) {
                5 -> 10
                10 -> 25
                15 -> 45
                20 -> 70
                25 -> 100
                30 -> 140
                35 -> 190
                40 -> 250
                45 -> 320
                50 -> 400
                55 -> 490
                60 -> 600
                else -> 600 + ((totalMinsFocusedSoFar - 60) / 5) * 120
            }
            
            val finalGold = (baseGold * goldMultiplier).toInt()
            
            val pXp = _profileFlow.value.pendingXp
            val pGold = _profileFlow.value.pendingGold
            
            p.edit()
                .putInt("pending_xp", pXp + baseXp)
                .putInt("pending_gold", pGold + finalGold)
                .apply()
            loadProfile()
        }
    }

    fun completeTaskReward(
        taskTitle: String,
        isPriority: Boolean,
        type: com.focusbyrj.app.data.TaskType = com.focusbyrj.app.data.TaskType.TASK
    ): Pair<Int, Int>? {
        val p = prefs ?: return null

        val baseXp = when {
            isPriority -> 60
            type != com.focusbyrj.app.data.TaskType.TASK -> 40
            else -> 30
        }
        val baseGold = when {
            isPriority -> 30
            type != com.focusbyrj.app.data.TaskType.TASK -> 20
            else -> 15
        }

        val currentLevel = _profileFlow.value.level
        val goldMultiplier = getGoldMultiplier(currentLevel)
        val finalGold = max(1, (baseGold * goldMultiplier).toInt())

        val pXp = _profileFlow.value.pendingXp
        val pGold = _profileFlow.value.pendingGold
        val currentTasks = _profileFlow.value.lifetimeTasksCompleted

        p.edit()
            .putInt("pending_xp", pXp + baseXp)
            .putInt("pending_gold", pGold + finalGold)
            .putInt("lifetime_tasks_completed", currentTasks + 1)
            .apply()

        loadProfile()
        return Pair(baseXp, finalGold)
    }

    fun uncompleteTaskReward(
        isPriority: Boolean,
        type: com.focusbyrj.app.data.TaskType = com.focusbyrj.app.data.TaskType.TASK
    ) {
        val p = prefs ?: return

        val baseXp = when {
            isPriority -> 60
            type != com.focusbyrj.app.data.TaskType.TASK -> 40
            else -> 30
        }
        val baseGold = when {
            isPriority -> 30
            type != com.focusbyrj.app.data.TaskType.TASK -> 20
            else -> 15
        }

        val currentLevel = _profileFlow.value.level
        val goldMultiplier = getGoldMultiplier(currentLevel)
        val finalGold = max(1, (baseGold * goldMultiplier).toInt())

        val pXp = _profileFlow.value.pendingXp
        val pGold = _profileFlow.value.pendingGold
        val currentTasks = _profileFlow.value.lifetimeTasksCompleted

        p.edit()
            .putInt("pending_xp", max(0, pXp - baseXp))
            .putInt("pending_gold", max(0, pGold - finalGold))
            .putInt("lifetime_tasks_completed", max(0, currentTasks - 1))
            .apply()

        loadProfile()
    }

    fun addResist() {
        prefs?.let { p ->
            val current = _profileFlow.value.lifetimeResists
            p.edit().putInt("lifetime_resists", current + 1).apply()
            loadProfile()
        }
    }
    
    fun addLifetimeFocusMins(mins: Int) {
        prefs?.let { p ->
            val current = _profileFlow.value.lifetimeFocusMins
            p.edit().putInt("lifetime_focus_mins", current + mins).apply()
            loadProfile()
        }
    }
        
    fun syncStreaks(current: Int, longest: Int) {
        prefs?.let { p ->
            val oldGold = _profileFlow.value.gold
            var newGold = oldGold
            
            val oldLongest = _profileFlow.value.longestStreak
            if (longest > oldLongest) {
                if (longest >= 3 && oldLongest < 3) newGold += 100
                if (longest >= 7 && oldLongest < 7) newGold += 500
                if (longest >= 30 && oldLongest < 30) newGold += 5000
            }
            
            
            val earnedGold = newGold - oldGold
            val finalGold = oldGold // We don't add to real gold yet
            val pGold = _profileFlow.value.pendingGold
            
            p.edit()
                .putInt("current_streak", current)
                .putInt("longest_streak", longest)
                .putInt("pending_gold", pGold + earnedGold)
                .apply()
            loadProfile()
        }
    }

    fun incrementStreak() {
        prefs?.let { p ->
            val newStreak = _profileFlow.value.currentStreak + 1
            val oldLongest = _profileFlow.value.longestStreak
            val newLongest = maxOf(oldLongest, newStreak)
            
            var goldBonus = 0
            if (newLongest > oldLongest) {
                if (newLongest >= 3 && oldLongest < 3) goldBonus += 100
                if (newLongest >= 7 && oldLongest < 7) goldBonus += 500
                if (newLongest >= 30 && oldLongest < 30) goldBonus += 5000
            }
            
            val pGold = _profileFlow.value.pendingGold
            
            p.edit()
                .putInt("current_streak", newStreak)
                .putInt("longest_streak", newLongest)
                .putInt("pending_gold", pGold + goldBonus)
                .apply()
            loadProfile()
        }
    }
    
    fun resetStreak() {
        prefs?.edit()?.putInt("current_streak", 0)?.apply()
        loadProfile()
    }
    
    fun unlockProMax() {
        prefs?.let { p ->
            p.edit()
                .putInt("xp", 260000)
                .putInt("gold", 999999)
                .putInt("current_streak", 30)
                .putInt("longest_streak", 30)
                .apply()
            loadProfile()
        }
    }

    fun buyStreakFreeze(cost: Int = 150): Boolean {
        val p = prefs ?: return false
        val currentGold = _profileFlow.value.gold
        if (currentGold >= cost) {
            val currentFreezes = _profileFlow.value.streakFreezes
            p.edit()
                .putInt("gold", currentGold - cost)
                .putInt("streak_freezes", currentFreezes + 1)
                .apply()
            loadProfile()
            emitEvent(EconomyEvent.RewardsEarned(0, 0, "Streak Freeze Shield Acquired 🛡️"))
            return true
        }
        return false
    }

    fun useStreakFreeze(): Boolean {
        val p = prefs ?: return false
        val currentFreezes = _profileFlow.value.streakFreezes
        if (currentFreezes > 0) {
            p.edit()
                .putInt("streak_freezes", currentFreezes - 1)
                .apply()
            loadProfile()
            emitEvent(EconomyEvent.RewardsEarned(0, 0, "Streak Freeze Activated ❄️"))
            return true
        }
        return false
    }

    fun calculateUnlockedAvatarTier(gold: Int): Int {
        return when {
            gold >= 50000 -> 5 
            gold >= 10000 -> 4 
            gold >= 2000 -> 3 
            gold >= 500 -> 2 
            else -> 1 
        }
    }
}
