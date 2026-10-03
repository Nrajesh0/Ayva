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
    fun parseTimeSafe(currentTime: String, defaultHour: Int = 8, defaultMinute: Int = 0): Pair<Int, Int> {
        try {
            val cleaned = currentTime.trim()
            if (cleaned.contains("AM", ignoreCase = true) || cleaned.contains("PM", ignoreCase = true)) {
                val parts = cleaned.split(":", " ").filter { it.isNotBlank() }
                if (parts.size >= 3) {
                    var h = parts[0].toInt()
                    val m = parts[1].toInt()
                    val amPm = parts[2]
                    if (amPm.equals("PM", ignoreCase = true) && h != 12) h += 12
                    if (amPm.equals("AM", ignoreCase = true) && h == 12) h = 0
                    return Pair(h.coerceIn(0, 23), m.coerceIn(0, 59))
                }
            } else {
                val parts = cleaned.split(":")
                if (parts.size >= 2) {
                    val h = parts[0].toInt()
                    val m = parts[1].toInt()
                    return Pair(h.coerceIn(0, 23), m.coerceIn(0, 59))
                }
            }
        } catch (_: Exception) {}
        return Pair(defaultHour.coerceIn(0, 23), defaultMinute.coerceIn(0, 59))
    }
}
