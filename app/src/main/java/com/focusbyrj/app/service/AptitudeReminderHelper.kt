/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.service

object AptitudeReminderHelper {
    /**
     * Parses time string into bounded (hour, minute) with hour in 0..23 and minute in 0..59,
     * preventing Calendar day-rollover and premature/delayed alarm triggers.
     */
    fun parseHourAndMinute(timeStr: String, defaultHour: Int = 18, defaultMinute: Int = 0): Pair<Int, Int> =
        com.focusbyrj.app.util.TimeOfDayParser.parse(timeStr, defaultHour, defaultMinute)
}
