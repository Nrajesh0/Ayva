/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.drill

object DrillStreakHelper {
    /**
     * Calculates the 1-based cycle day in a 7-day streak bonus cycle (1..7).
     * Ensures negative or corrupt streak values never return negative numbers or zero.
     */
    fun calculateCycleDay(streak: Int): Int {
        val safeStreak = streak.coerceAtLeast(0)
        return if (safeStreak == 0) 1 else ((safeStreak - 1) % 7) + 1
    }
}
