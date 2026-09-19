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
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

enum class HeatmapTheme(
    val id: String,
    val displayName: String,
    val colors: List<Color>
) {
    EMERALD(
        "emerald",
        "Emerald Aurora",
        listOf(
            Color.Transparent,
            Color(0xFF10B981).copy(alpha = 0.28f),
            Color(0xFF10B981).copy(alpha = 0.52f),
            Color(0xFF10B981).copy(alpha = 0.76f),
            Color(0xFF10B981)
        )
    ),
    VIOLET(
        "violet",
        "Electric Violet",
        listOf(
            Color.Transparent,
            Color(0xFF8B5CF6).copy(alpha = 0.28f),
            Color(0xFF8B5CF6).copy(alpha = 0.52f),
            Color(0xFF8B5CF6).copy(alpha = 0.76f),
            Color(0xFF8B5CF6)
        )
    ),
    CYAN(
        "cyan",
        "Cyan Ocean",
        listOf(
            Color.Transparent,
            Color(0xFF06B6D4).copy(alpha = 0.28f),
            Color(0xFF06B6D4).copy(alpha = 0.52f),
            Color(0xFF06B6D4).copy(alpha = 0.76f),
            Color(0xFF06B6D4)
        )
    ),
    AMBER(
        "amber",
        "Amber Sunset",
        listOf(
            Color.Transparent,
            Color(0xFFF59E0B).copy(alpha = 0.28f),
            Color(0xFFF59E0B).copy(alpha = 0.52f),
            Color(0xFFF59E0B).copy(alpha = 0.76f),
            Color(0xFFF59E0B)
        )
    ),
    OBSIDIAN(
        "obsidian",
        "Obsidian Silver",
        listOf(
            Color.Transparent,
            Color(0xFF94A3B8).copy(alpha = 0.28f),
            Color(0xFF94A3B8).copy(alpha = 0.52f),
            Color(0xFF94A3B8).copy(alpha = 0.76f),
            Color(0xFF94A3B8)
        )
    ),
    ROSE(
        "rose",
        "Rose Quartz",
        listOf(
            Color.Transparent,
            Color(0xFFF43F5E).copy(alpha = 0.28f),
            Color(0xFFF43F5E).copy(alpha = 0.52f),
            Color(0xFFF43F5E).copy(alpha = 0.76f),
            Color(0xFFF43F5E)
        )
    );

    companion object {
        fun fromId(id: String?): HeatmapTheme {
            return entries.find { it.id == id } ?: EMERALD
        }
    }
}

data class FocusStats(
    val currentStreak: Int,
    val longestStreak: Int,
    val dailyFocusMinutes: Map<Int, Long> // dayOfYear -> milliseconds of focus
)

object FocusStatsManager {
    private const val PREFS_NAME = "focus_stats_prefs"
    private const val KEY_INSTALL_TIME = "app_install_timestamp"
    private const val KEY_HEATMAP_THEME = "heatmap_gradient_theme"
    private const val KEY_LONGEST_STREAK = "saved_longest_streak"

    private val _statsFlow = MutableStateFlow(FocusStats(0, 0, emptyMap()))
    val statsFlow: StateFlow<FocusStats> = _statsFlow.asStateFlow()

    private val _themeFlow = MutableStateFlow(HeatmapTheme.EMERALD)
    val themeFlow: StateFlow<HeatmapTheme> = _themeFlow.asStateFlow()

    private val _interceptionsFlow = MutableStateFlow(0)
    val interceptionsFlow: StateFlow<Int> = _interceptionsFlow.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_INSTALL_TIME)) {
            prefs.edit()
                .putLong(KEY_INSTALL_TIME, System.currentTimeMillis())
                .putInt(KEY_LONGEST_STREAK, 0)
                .apply()
        }

        val themeId = prefs.getString(KEY_HEATMAP_THEME, HeatmapTheme.EMERALD.id)
        _themeFlow.value = HeatmapTheme.fromId(themeId)
        
        val interceptionKey = getDailyInterceptionKey(Calendar.getInstance())
        _interceptionsFlow.value = prefs.getInt(interceptionKey, 0)

        refreshStats(context)
    }

    fun addInterception(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = getDailyInterceptionKey(Calendar.getInstance())
        val currentCount = prefs.getInt(key, 0)
        prefs.edit().putInt(key, currentCount + 1).apply()
        _interceptionsFlow.value = currentCount + 1
        FocusEconomyManager.addResist()
    }

    fun setHeatmapTheme(context: Context, theme: HeatmapTheme) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HEATMAP_THEME, theme.id).apply()
        _themeFlow.value = theme
    }

    fun addFocusSessionTime(context: Context, seconds: Long) {
        if (seconds <= 0) return
        addDailyActivity(context, seconds * 1000L, triggerFullRefresh = true)
    }

    fun addRoutineActivity(context: Context, minutes: Long = 15L) {
        if (minutes <= 0) return
        addDailyActivity(context, minutes * 60 * 1000L, triggerFullRefresh = false)
    }

    fun addAppRestrictionActivity(context: Context, count: Int = 1) {
        if (count <= 0) return
        // Give 10 minutes of activity score per app restricted
        addDailyActivity(context, count * 10 * 60 * 1000L, triggerFullRefresh = true)
    }

    private fun addDailyActivity(context: Context, deltaMs: Long, triggerFullRefresh: Boolean = true) {
        if (deltaMs <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cal = Calendar.getInstance()
        val key = getDailyKey(cal)
        val currentMs = prefs.getLong(key, 0L)
        val newMs = currentMs + deltaMs
        prefs.edit().putLong(key, newMs).apply()

        if (triggerFullRefresh) {
            refreshStats(context)
        } else {
            // Lightweight update for periodic background routine ticks: update today's minutes without 30-day recalculation loop
            val currentStats = _statsFlow.value
            val todayDayOfYear = cal.get(Calendar.DAY_OF_YEAR)
            val updatedMap = currentStats.dailyFocusMinutes.toMutableMap()
            updatedMap[todayDayOfYear] = newMs
            _statsFlow.value = currentStats.copy(dailyFocusMinutes = updatedMap)
        }
    }

    fun refreshStats(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val installTime = prefs.getLong(KEY_INSTALL_TIME, System.currentTimeMillis())
        val installCal = Calendar.getInstance().apply { timeInMillis = installTime }
        
        installCal.set(Calendar.HOUR_OF_DAY, 0)
        installCal.set(Calendar.MINUTE, 0)
        installCal.set(Calendar.SECOND, 0)
        installCal.set(Calendar.MILLISECOND, 0)

        val dailyMap = mutableMapOf<Int, Long>()
        val cal = Calendar.getInstance()

        for (i in 0 downTo -30) {
            val dayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
            val dayOfYear = dayCal.get(Calendar.DAY_OF_YEAR)

            if (dayCal.before(installCal) && !isSameDay(dayCal, installCal)) {
                dailyMap[dayOfYear] = 0L
            } else {
                val key = getDailyKey(dayCal)
                val focusMs = prefs.getLong(key, 0L)
                dailyMap[dayOfYear] = focusMs
            }
        }

        var currentStreak = 0
        val todayCal = Calendar.getInstance()
        val todayMs = dailyMap[todayCal.get(Calendar.DAY_OF_YEAR)] ?: 0L
        val minActiveMs = 1 * 60 * 1000L // 1 minute focus counts towards streak

        var startIndex = 0
        if (todayMs < minActiveMs) {
            val yestCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yestMs = dailyMap[yestCal.get(Calendar.DAY_OF_YEAR)] ?: 0L
            if (yestMs >= minActiveMs && !yestCal.before(installCal)) {
                startIndex = -1
            } else {
                startIndex = -999 // Broken / 0 streak
            }
        }

        if (startIndex != -999) {
            for (i in startIndex downTo -30) {
                val checkCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
                if (checkCal.before(installCal) && !isSameDay(checkCal, installCal)) {
                    break
                }
                val ms = dailyMap[checkCal.get(Calendar.DAY_OF_YEAR)] ?: 0L
                if (ms >= minActiveMs) {
                    currentStreak++
                } else {
                    break
                }
            }
        }

        var maxStreak = prefs.getInt(KEY_LONGEST_STREAK, 0)
        var runningStreak = 0
        for (i in -30..0) {
            val checkCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
            if (checkCal.before(installCal) && !isSameDay(checkCal, installCal)) {
                runningStreak = 0
                continue
            }
            val ms = dailyMap[checkCal.get(Calendar.DAY_OF_YEAR)] ?: 0L
            if (ms >= minActiveMs) {
                runningStreak++
                if (runningStreak > maxStreak) {
                    maxStreak = runningStreak
                }
            } else {
                runningStreak = 0
            }
        }

        if (currentStreak > maxStreak) {
            maxStreak = currentStreak
        }

        prefs.edit().putInt(KEY_LONGEST_STREAK, maxStreak).apply()

        FocusEconomyManager.syncStreaks(currentStreak, maxStreak)
        _statsFlow.value = FocusStats(
            currentStreak = currentStreak,
            longestStreak = maxStreak,
            dailyFocusMinutes = dailyMap
        )
    }

    private fun getDailyKey(cal: Calendar): String {
        return "focus_day_${cal.get(Calendar.YEAR)}_${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun getDailyInterceptionKey(cal: Calendar): String {
        return "interceptions_day_${cal.get(Calendar.YEAR)}_${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
