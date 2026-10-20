package com.focusbyrj.app.util.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

internal data class PlanPolicy(
    val bufferMinutes: Int = 0,
    val workingDays: Set<Int> = (1..7).toSet(),
    val excludedDates: Set<String> = emptySet(),
    val maximumSession: Int? = null,
    val dailyMinutes: Int? = null
) {
    val dayLabels get() = workingDays.sorted().joinToString { listOf("", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")[it] }
    fun validate() {
        require(bufferMinutes in 0..240 && workingDays.isNotEmpty() && workingDays.all { it in 1..7 })
        require(excludedDates.size <= 100 && excludedDates.all { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) })
        require(maximumSession == null || maximumSession in 1..1440)
        require(dailyMinutes == null || dailyMinutes in 1..1440)
    }
    fun json() = JSONObject().put("buffer", bufferMinutes).put("days", JSONArray(workingDays.sorted()))
        .put("excludedDates", JSONArray(excludedDates.sorted())).put("maximumSession", maximumSession ?: JSONObject.NULL)
        .put("dailyMinutes", dailyMinutes ?: JSONObject.NULL)
    fun allows(day: Calendar): Boolean {
        val date = "%04d-%02d-%02d".format(java.util.Locale.ROOT, day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH))
        return day.get(Calendar.DAY_OF_WEEK) in workingDays && date !in excludedDates
    }
    fun allows(session: com.focusbyrj.app.data.WorkSession, timezone: String): Boolean {
        val day = Calendar.getInstance(java.util.TimeZone.getTimeZone(timezone)).apply {
            timeInMillis = session.start; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        repeat(3) {
            if (!allows(day)) return false
            day.add(Calendar.DAY_OF_YEAR, 1)
            if (session.end <= day.timeInMillis) return true
        }
        return false
    }
    companion object {
        fun read(j: JSONObject?): PlanPolicy {
            if (j == null) return PlanPolicy()
            val dates = j.getJSONArray("excludedDates"); require(dates.length() <= 100)
            return PlanPolicy(j.getInt("buffer"), j.getJSONArray("days").let { a -> require(a.length() <= 7); (0 until a.length()).map { a.getInt(it) }.toSet() },
                (0 until dates.length()).map { dates.getString(it) }.toSet(), j.nullableLong("maximumSession")?.toInt(), j.nullableLong("dailyMinutes")?.toInt()).also { it.validate() }
        }
    }
}
