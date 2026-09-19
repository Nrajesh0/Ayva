/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class HabitType {
    ONCE_DAILY,
    INTERVAL_WINDOW
}

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val iconEmoji: String = "✨",
    val colorHex: String = "#3B82F6",
    val type: HabitType = HabitType.ONCE_DAILY,
    val targetPerDay: Int = 1,
    val intervalHours: Int = 2,
    val intervalMinutes: Int = 0,
    val windowStartHour: Int = 8,
    val windowStartMinute: Int = 0,
    val windowEndHour: Int = 20,
    val windowEndMinute: Int = 0,
    val fixedReminderHour: Int = 9,
    val fixedReminderMinute: Int = 0,
    val isReminderEnabled: Boolean = true,
    val reminderSound: String = "ZEN",
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
) {
    val totalIntervalMinutes: Int
        get() {
            val total = intervalHours * 60 + intervalMinutes
            return if (total > 0) total else 60
        }

    val formattedInterval: String
        get() {
            val total = totalIntervalMinutes
            val h = total / 60
            val m = total % 60
            return when {
                h > 0 && m > 0 -> "${h}h ${m}m"
                h > 0 -> "${h}h"
                else -> "${m}m"
            }
        }

    val formattedWindow: String
        get() {
            val startAmPm = if (windowStartHour >= 12) "PM" else "AM"
            val startHr = if (windowStartHour % 12 == 0) 12 else windowStartHour % 12
            val startMin = String.format(java.util.Locale.US, "%02d", windowStartMinute)

            val endAmPm = if (windowEndHour >= 12) "PM" else "AM"
            val endHr = if (windowEndHour % 12 == 0) 12 else windowEndHour % 12
            val endMin = String.format(java.util.Locale.US, "%02d", windowEndMinute)

            return "$startHr:$startMin $startAmPm - $endHr:$endMin $endAmPm"
        }
}

@Entity(
    tableName = "habit_logs",
    indices = [
        Index(value = ["habitId", "date"], unique = true),
        Index(value = ["date"]),
        Index(value = ["habitId"])
    ]
)
data class HabitLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val habitId: Long,
    val date: String, // format "YYYY-MM-DD"
    val completedCount: Int = 0,
    val targetCount: Int = 1,
    val lastCompletedTimestamp: Long? = null
)

data class HabitDaySummary(
    val date: String,          // "yyyy-MM-dd"
    val dayOfWeekLetter: String, // "M", "T", "W", "T", "F", "S", "S"
    val completedCount: Int,
    val targetCount: Int,
    val isCompleted: Boolean,
    val isToday: Boolean
)

data class HabitWithProgress(
    val habit: Habit,
    val todayLog: HabitLog?,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val weeklyHistory: List<HabitDaySummary> = emptyList(),
    val totalCompletionsAllTime: Int = 0,
    val streakFrozenToday: Boolean = false
) {
    val completedToday: Int
        get() = todayLog?.completedCount ?: 0

    val targetToday: Int
        get() = todayLog?.targetCount ?: habit.targetPerDay

    val isCompletedToday: Boolean
        get() = completedToday >= targetToday

    val progressFraction: Float
        get() = if (targetToday > 0) (completedToday.toFloat() / targetToday.toFloat()).coerceIn(0f, 1f) else 0f
}
