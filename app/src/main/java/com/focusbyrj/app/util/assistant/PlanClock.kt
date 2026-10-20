package com.focusbyrj.app.util.assistant

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal object PlanClock {
    fun format(at: Long, zone: String): String = SimpleDateFormat("EEE, d MMM yyyy h:mm a z", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone(zone)
    }.format(java.util.Date(at))
    /** Invalid or ambiguous wall-clock boundaries require another window rather than a guessed offset. */
    fun boundary(calendar: Calendar): Long? = runCatching {
        val at = calendar.timeInMillis
        val zone = calendar.timeZone
        val change = zone.getOffset(at - 86_400_000L) - zone.getOffset(at + 86_400_000L)
        if (change != 0) for (alternate in listOf(at - change, at + change)) {
            val other = Calendar.getInstance(zone).apply { timeInMillis = alternate }
            require(listOf(Calendar.YEAR, Calendar.MONTH, Calendar.DAY_OF_MONTH, Calendar.HOUR_OF_DAY, Calendar.MINUTE).any { other.get(it) != calendar.get(it) })
        }
        at
    }.getOrNull()
}
