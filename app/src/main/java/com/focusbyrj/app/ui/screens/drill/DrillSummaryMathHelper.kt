/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.drill

import kotlin.math.roundToInt

object DrillSummaryMathHelper {
    /**
     * Calculates accuracy percentage clamped strictly between 0 and 100.
     * Prevents negative percentages or > 100% percentages from malformed inputs.
     */
    fun calculateAccuracy(correct: Int, total: Int, fallbackZero: Boolean = true): Int {
        if (total <= 0) return if (fallbackZero) 0 else 100
        val clampedCorrect = correct.coerceIn(0, total)
        return ((clampedCorrect.toFloat() / total) * 100).roundToInt().coerceIn(0, 100)
    }
}
