package com.focusbyrj.app.data

import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId

/** Calendar days on a break bridge streaks but never create progress or rewards. */
object HabitPause {
    fun isPausedOn(habit: Habit, date: String, now: Long = System.currentTimeMillis()): Boolean {
        val zone = ZoneId.systemDefault()
        fun day(at: Long) = Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toString()
        fun contains(start: Long, end: Long) = date >= day(start) && date <= day(end)
        if (habit.pausedAt?.let { contains(it, maxOf(now, it)) } == true) return true
        return runCatching {
            val ranges = JSONArray(habit.pauseHistoryJson)
            (0 until ranges.length()).any {
                val range = ranges.getJSONArray(it)
                contains(range.getLong(0), range.getLong(1))
            }
        }.getOrDefault(false)
    }
}
