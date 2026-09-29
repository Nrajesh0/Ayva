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
