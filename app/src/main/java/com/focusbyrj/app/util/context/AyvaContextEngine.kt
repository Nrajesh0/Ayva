/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.util.context

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.FocusStatsManager
import com.focusbyrj.app.util.StreakManager
import java.util.Calendar

enum class CircadianPhase {
    MORNING,    // 05:00 - 11:59
    AFTERNOON,  // 12:00 - 16:59
    EVENING,    // 17:00 - 21:59
    NIGHT       // 22:00 - 04:59
}

data class AyvaContextSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val circadianPhase: CircadianPhase,
    val hourOfDay: Int,
    val batteryPct: Int = -1,
    val isCharging: Boolean = false,
    val overdueTasksCount: Int = 0,
    val pendingTasksCount: Int = 0,
    val priorityTasksCount: Int = 0,
    val nextDueTaskTitle: String? = null,
    val nextDueTimestamp: Long? = null,
    val habitsCompletedToday: Int = 0,
    val totalHabitsCount: Int = 0,
    val currentStreak: Int = 0,
    val screenTimeMinutesToday: Long = 0L,
    val isStrictFocusActive: Boolean = false
) {
    val isHighPressure: Boolean
        get() = overdueTasksCount >= 2 || (pendingTasksCount >= 5 && hourOfDay >= 18)

    val greetingPrompt: String
        get() = when (circadianPhase) {
            CircadianPhase.MORNING -> "Good morning! Let's lock in today's priorities."
            CircadianPhase.AFTERNOON -> "Good afternoon! Staying on track?"
            CircadianPhase.EVENING -> "Good evening! Wrapping up remaining targets?"
            CircadianPhase.NIGHT -> "Late session detected. Protect your sleep schedule!"
        }
}

object AyvaContextEngine {

    fun determineCircadianPhase(hourOfDay: Int): CircadianPhase {
        return when (hourOfDay) {
            in 5..11 -> CircadianPhase.MORNING
            in 12..16 -> CircadianPhase.AFTERNOON
            in 17..21 -> CircadianPhase.EVENING
            else -> CircadianPhase.NIGHT
        }
    }

    fun captureSnapshot(
        context: Context,
        tasks: List<Task> = emptyList(),
        habitsCompleted: Int = 0,
        habitsTotal: Int = 0
    ): AyvaContextSnapshot {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val hourOfDay = cal.get(Calendar.HOUR_OF_DAY)
        val phase = determineCircadianPhase(hourOfDay)

        // Battery state
        var batteryPct = -1
        var isCharging = false
        try {
            val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, iFilter)
            if (batteryStatus != null) {
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPct = (level * 100) / scale
                }
                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (_: Exception) {}

        // Task pressure metrics
        val pending = tasks.filter { !it.isCompleted }
        val overdue = pending.filter { it.dueDate != null && it.dueDate < now }
        val priority = pending.filter { it.isPriority }
        val nextDue = pending
            .filter { it.dueDate != null && it.dueDate >= now }
            .minByOrNull { it.dueDate!! }

        // Streak & Screen time
        val streak = try {
            val sm = StreakManager.getActiveCurrentStreak()
            if (sm > 0) sm else FocusStatsManager.statsFlow.value.currentStreak
        } catch (_: Exception) {
            0
        }

        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val screenTimeMinutes = try {
            val dailyMap = FocusStatsManager.statsFlow.value.dailyFocusMinutes
            (dailyMap[dayOfYear] ?: 0L) / (1000L * 60L)
        } catch (_: Exception) {
            0L
        }

        return AyvaContextSnapshot(
            timestamp = now,
            circadianPhase = phase,
            hourOfDay = hourOfDay,
            batteryPct = batteryPct,
            isCharging = isCharging,
            overdueTasksCount = overdue.size,
            pendingTasksCount = pending.size,
            priorityTasksCount = priority.size,
            nextDueTaskTitle = nextDue?.title,
            nextDueTimestamp = nextDue?.dueDate,
            habitsCompletedToday = habitsCompleted,
            totalHabitsCount = habitsTotal,
            currentStreak = streak,
            screenTimeMinutesToday = screenTimeMinutes
        )
    }
}
