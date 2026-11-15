/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.util

/** Parses stored reminder times, preserving the existing 12/24-hour format and bounds. */
internal object TimeOfDayParser {
    fun parse(value: String, defaultHour: Int, defaultMinute: Int): Pair<Int, Int> {
        try {
            val cleaned = value.trim()
            if (
                cleaned.contains("AM", ignoreCase = true) ||
                    cleaned.contains("PM", ignoreCase = true)
            ) {
                val parts = cleaned.split(":", " ").filter { it.isNotBlank() }
                if (parts.size >= 3) {
                    var hour = parts[0].toInt()
                    val minute = parts[1].toInt()
                    val amPm = parts[2]
                    if (amPm.equals("PM", ignoreCase = true) && hour != 12) hour += 12
                    if (amPm.equals("AM", ignoreCase = true) && hour == 12) hour = 0
                    return hour.coerceIn(0, 23) to minute.coerceIn(0, 59)
                }
            } else {
                val parts = cleaned.split(":")
                if (parts.size >= 2) {
                    return parts[0].toInt().coerceIn(0, 23) to parts[1].toInt().coerceIn(0, 59)
                }
            }
        } catch (_: Exception) {
            // Invalid stored values use the caller's existing fallback.
        }
        return defaultHour.coerceIn(0, 23) to defaultMinute.coerceIn(0, 59)
    }
}
