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

package com.focusbyrj.app.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StreakSource(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconEmoji: String
) {
    DRILL(
        "drill",
        "Aptitude Drill Streak",
        "Daily mental math & cognitive drill practice (Default)",
        "🔥"
    ),
    FOCUS(
        "focus",
        "Focus & Routine Streak",
        "Daily focus sessions, routine completions & app blocking",
        "🎯"
    );

    companion object {
        fun fromId(id: String?): StreakSource {
            return entries.find { it.id == id } ?: DRILL
        }
    }
}

object StreakManager {
    private const val PREFS_NAME = "streak_preferences"
    private const val KEY_STREAK_SOURCE = "streak_display_source"

    private val _streakSourceFlow = MutableStateFlow(StreakSource.DRILL)
    val streakSourceFlow: StateFlow<StreakSource> = _streakSourceFlow.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sourceId = prefs.getString(KEY_STREAK_SOURCE, StreakSource.DRILL.id)
        _streakSourceFlow.value = StreakSource.fromId(sourceId)
    }

    fun setStreakSource(context: Context, source: StreakSource) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_STREAK_SOURCE, source.id).apply()
        _streakSourceFlow.value = source
    }

    fun getActiveCurrentStreak(): Int {
        return when (_streakSourceFlow.value) {
            StreakSource.DRILL -> AptitudeManager.profileFlow.value.currentStreak
            StreakSource.FOCUS -> FocusStatsManager.statsFlow.value.currentStreak
        }
    }

    fun getActiveLongestStreak(): Int {
        return when (_streakSourceFlow.value) {
            StreakSource.DRILL -> AptitudeManager.profileFlow.value.longestStreak
            StreakSource.FOCUS -> {
                val focusStats = FocusStatsManager.statsFlow.value
                val profile = FocusEconomyManager.profileFlow.value
                maxOf(focusStats.longestStreak, profile.longestStreak)
            }
        }
    }
}
