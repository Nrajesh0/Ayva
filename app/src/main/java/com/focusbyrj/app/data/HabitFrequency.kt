package com.focusbyrj.app.data

import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/** ISO weekdays; weekly targets count distinct successful days, never individual taps. */
data class HabitFrequency(val mode: String = "DAILY", val days: Set<Int> = (1..7).toSet(), val weeklyTarget: Int = 3, val since: String = LocalDate.now().toString()) {
    fun validate() {
        require(mode in setOf("DAILY", "DAYS", "WEEKLY"))
        require(days.isNotEmpty() && days.all { it in 1..7 })
        require(weeklyTarget in 1..7)
        LocalDate.parse(since)
    }
    fun json(): String { validate(); return JSONObject().put("mode", mode).put("days", JSONArray(days.sorted())).put("target", weeklyTarget).put("since", since).toString() }
    fun scheduled(date: LocalDate): Boolean = date.toString() < since || mode != "DAYS" || date.dayOfWeek.value in days
    fun weekCount(logs: List<HabitLog>, today: LocalDate = LocalDate.now()): Int {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return logs.filter { it.date >= monday.toString() && it.date <= today.toString() && it.completedCount >= it.targetCount.coerceAtLeast(1) }.map { it.date }.distinct().size
    }
    fun weeklyStreaks(logs: List<HabitLog>, today: LocalDate = LocalDate.now()): Pair<Int, Int> {
        val counts = logs.filter { it.date >= since && it.date <= today.toString() && it.completedCount >= it.targetCount.coerceAtLeast(1) }
            .map { LocalDate.parse(it.date) }.distinct().groupingBy { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.eachCount()
        val weeks = counts.filterValues { it >= weeklyTarget }.keys.sorted()
        var best = 0; var run = 0; var previous: LocalDate? = null
        weeks.forEach { week -> run = if (previous?.plusWeeks(1) == week) run + 1 else 1; best = maxOf(best, run); previous = week }
        var week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (week !in weeks) week = week.minusWeeks(1)
        var current = 0
        while (week in weeks) { current++; week = week.minusWeeks(1) }
        return current to best
    }
    val label: String get() = when(mode) { "WEEKLY" -> "$weeklyTarget days per week"; "DAYS" -> days.sorted().joinToString(" · ") { DayOfWeek.of(it).name.take(3).lowercase().replaceFirstChar(Char::uppercase) }; else -> "Every day" }
    companion object {
        fun read(raw: String): HabitFrequency {
            if (raw.isBlank()) return HabitFrequency(since = "1970-01-01")
            require(raw.length <= 2000)
            val j = JSONObject(raw); val a = j.getJSONArray("days")
            return HabitFrequency(j.getString("mode"), (0 until a.length()).map { a.getInt(it) }.toSet(), j.getInt("target"), j.getString("since")).also { it.validate() }
        }
    }
}
val Habit.frequency: HabitFrequency get() = HabitFrequency.read(frequencyJson)
