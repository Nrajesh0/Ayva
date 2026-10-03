/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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
        _interceptionsFlow.value = prefs.getSafeInt(interceptionKey, 0)

        refreshStats(context)
    }

    fun addInterception(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = getDailyInterceptionKey(Calendar.getInstance())
        val currentCount = prefs.getSafeInt(key, 0)
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
        val currentMs = prefs.getSafeLong(key, 0L)
        val newMs = currentMs + deltaMs
        prefs.edit().putLong(key, newMs).apply()

        if (triggerFullRefresh) {
            refreshStats(context)
        } else {
            // Lightweight update for periodic background routine ticks: update today's minutes without 30-day recalculation loop
            // Use composite year*1000+dayOfYear key to avoid Jan-1 year-boundary collision (BATCH-9-P2-006)
            val currentStats = _statsFlow.value
            val compositeKey = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
            val updatedMap = currentStats.dailyFocusMinutes.toMutableMap()
            updatedMap[compositeKey] = newMs
            _statsFlow.value = currentStats.copy(dailyFocusMinutes = updatedMap)
        }
    }

    fun refreshStats(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val installTime = prefs.getSafeLong(KEY_INSTALL_TIME, System.currentTimeMillis())
        val installCal = Calendar.getInstance().apply { timeInMillis = installTime }
        
        installCal.set(Calendar.HOUR_OF_DAY, 0)
        installCal.set(Calendar.MINUTE, 0)
        installCal.set(Calendar.SECOND, 0)
        installCal.set(Calendar.MILLISECOND, 0)

        // Use composite year*1000+dayOfYear as map key to avoid Jan-1 year-boundary collision (BATCH-9-P2-005)
        // e.g. Dec 31, 2025 = 2025*1000+365 = 2025365; Jan 1, 2026 = 2026*1000+1 = 2026001 — no collision
        val dailyMap = mutableMapOf<Int, Long>()
        val cal = Calendar.getInstance()

        // Load at least 18 weeks (126 days) of history for the account screen heatmap
        val historyDays = 130
        for (i in 0 downTo -historyDays) {
            val dayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
            val compositeKey = dayCal.get(Calendar.YEAR) * 1000 + dayCal.get(Calendar.DAY_OF_YEAR)

            if (dayCal.before(installCal) && !isSameDay(dayCal, installCal)) {
                dailyMap[compositeKey] = 0L
            } else {
                val prefKey = getDailyKey(dayCal)
                val focusMs = prefs.getSafeLong(prefKey, 0L)
                dailyMap[compositeKey] = focusMs
            }
        }

        var currentStreak = 0
        val todayCal = Calendar.getInstance()
        val todayCompositeKey = todayCal.get(Calendar.YEAR) * 1000 + todayCal.get(Calendar.DAY_OF_YEAR)
        val todayMs = dailyMap[todayCompositeKey] ?: 0L
        val minActiveMs = 1 * 60 * 1000L // 1 minute focus counts towards streak

        var startIndex = 0
        if (todayMs < minActiveMs) {
            val yestCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yestCompositeKey = yestCal.get(Calendar.YEAR) * 1000 + yestCal.get(Calendar.DAY_OF_YEAR)
            val yestMs = dailyMap[yestCompositeKey] ?: 0L
            if (yestMs >= minActiveMs && !yestCal.before(installCal)) {
                startIndex = -1
            } else {
                startIndex = -999 // Broken / 0 streak
            }
        }

        if (startIndex != -999) {
            for (i in startIndex downTo -historyDays) {
                val checkCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
                if (checkCal.before(installCal) && !isSameDay(checkCal, installCal)) {
                    break
                }
                val ck = checkCal.get(Calendar.YEAR) * 1000 + checkCal.get(Calendar.DAY_OF_YEAR)
                val ms = dailyMap[ck] ?: 0L
                if (ms >= minActiveMs) {
                    currentStreak++
                } else {
                    break
                }
            }
        }

        var maxStreak = prefs.getSafeInt(KEY_LONGEST_STREAK, 0)
        var runningStreak = 0
        for (i in -historyDays..0) {
            val checkCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
            if (checkCal.before(installCal) && !isSameDay(checkCal, installCal)) {
                runningStreak = 0
                continue
            }
            val ck = checkCal.get(Calendar.YEAR) * 1000 + checkCal.get(Calendar.DAY_OF_YEAR)
            val ms = dailyMap[ck] ?: 0L
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
