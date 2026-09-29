/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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
