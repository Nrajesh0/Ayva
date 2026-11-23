/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_schedules")
data class FocusSchedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val daysOfWeek: String, // Comma separated e.g., "1,2,3,4,5" (Mon-Fri)
    val mode: String = "HARD",
    val restrictionMode: String = "SIMPLE",
    val timeLimitMinutes: Int = 0,
    val clickLimitCount: Int = 0,
    val appsToBlock: String = "", // Comma separated package names
    val isEnabled: Boolean = true
) {
    /**
     * Accurately determines if this schedule is active at a given Calendar point in time,
     * correctly supporting overnight schedules crossing midnight (e.g. 22:00 to 06:00).
     */
    fun isActiveAt(calendar: java.util.Calendar = java.util.Calendar.getInstance()): Boolean {
        if (!isEnabled) return false
        val activeDays = daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        if (activeDays.isEmpty()) return false

        val currentDay = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        val currentTotalMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)

        val startTotalMinutes = startHour * 60 + startMinute
        val endTotalMinutes = endHour * 60 + endMinute

        return if (startTotalMinutes <= endTotalMinutes) {
            // Standard same-day window
            activeDays.contains(currentDay) && currentTotalMinutes in startTotalMinutes..endTotalMinutes
        } else {
            // Overnight window: spans across midnight into next day
            // Case 1: started today before midnight (e.g. 22:00..23:59) on an active day
            val matchesTodayEvening = activeDays.contains(currentDay) && currentTotalMinutes >= startTotalMinutes
            
            // Case 2: continued from yesterday into early morning today (e.g. 00:00..06:00)
            val yesterday = if (currentDay == java.util.Calendar.SUNDAY) java.util.Calendar.SATURDAY else currentDay - 1
            val matchesYesterdayNight = activeDays.contains(yesterday) && currentTotalMinutes <= endTotalMinutes

            matchesTodayEvening || matchesYesterdayNight
        }
    }
}

/** Used only inside encrypted drafts; includes the original row for conflict detection. */
internal object ScheduleJson {
    fun encode(row: FocusSchedule) = org.json.JSONObject().apply {
        put("id", row.id); put("name", row.name)
        put("startHour", row.startHour); put("startMinute", row.startMinute)
        put("endHour", row.endHour); put("endMinute", row.endMinute)
        put("days", row.daysOfWeek); put("mode", row.mode); put("restriction", row.restrictionMode)
        put("minutes", row.timeLimitMinutes); put("clicks", row.clickLimitCount)
        put("apps", row.appsToBlock); put("enabled", row.isEnabled)
    }
    fun decode(value: org.json.JSONObject) = FocusSchedule(
        id = value.getInt("id"), name = value.getString("name"), startHour = value.getInt("startHour"),
        startMinute = value.getInt("startMinute"), endHour = value.getInt("endHour"), endMinute = value.getInt("endMinute"),
        daysOfWeek = value.getString("days"), mode = value.getString("mode"), restrictionMode = value.getString("restriction"),
        timeLimitMinutes = value.getInt("minutes"), clickLimitCount = value.getInt("clicks"),
        appsToBlock = value.getString("apps"), isEnabled = value.getBoolean("enabled")
    )
}
