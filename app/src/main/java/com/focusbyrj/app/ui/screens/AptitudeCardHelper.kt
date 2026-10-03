/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens

import kotlin.math.roundToInt

object AptitudeCardHelper {
    /**
     * Clamps accuracy percentage strictly between 0 and 100 to prevent negative or >100% metrics.
     */
    fun clampAccuracy(accuracy: Float): Int {
        if (accuracy.isNaN() || accuracy < 0f) return 0
        return accuracy.roundToInt().coerceIn(0, 100)
    }

    /**
     * Clamps streak freeze count within valid inventory capacity (0..3).
     */
    fun clampStreakFreezes(count: Int, maxCapacity: Int = 3): Int {
        return count.coerceIn(0, maxCapacity)
    }
}
