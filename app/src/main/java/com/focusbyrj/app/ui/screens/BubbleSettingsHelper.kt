/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens

object BubbleSettingsHelper {
    /**
     * Coerces a slider integer value into the valid float range of ProfessionalSlider,
     * preventing IllegalArgumentException in Compose runtime.
     */
    fun coerceSliderValue(value: Int, range: IntRange): Float {
        return value.coerceIn(range.first, range.last).toFloat()
    }

    /**
     * Safely parses a 12-hour or 24-hour time string into (hour, minute),
     * returning hour in 0..23 and minute in 0..59.
     */
    fun parseTimeSafe(currentTime: String, defaultHour: Int = 8, defaultMinute: Int = 0): Pair<Int, Int> =
        com.focusbyrj.app.util.TimeOfDayParser.parse(currentTime, defaultHour, defaultMinute)
}
