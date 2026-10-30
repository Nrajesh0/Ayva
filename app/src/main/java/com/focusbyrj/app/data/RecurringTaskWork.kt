package com.focusbyrj.app.data

import java.util.Calendar
import java.util.TimeZone

/** Repeat local work times with the occurrence, without retaining the previous task's graph links. */
fun TaskPlanning.forNextOccurrence(oldDue: Long, nextDue: Long): TaskPlanning {
    val zone = TimeZone.getTimeZone(timezone)
    fun localDay(at: Long): Long {
        val local = Calendar.getInstance(zone).apply { timeInMillis = at }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    val days = (localDay(nextDue) - localDay(oldDue)) / 86_400_000L
    val nextAvailability = availableFrom?.let { at -> Calendar.getInstance(zone).apply { timeInMillis = at; add(Calendar.DAY_OF_YEAR, days.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()) }.timeInMillis }
    val detached = copy(availableFrom = nextAvailability, parentTaskId = null, prerequisiteTaskIds = emptySet(), parentMissing = false, missingPrerequisites = 0, afterMinutes = 0)
    if (days !in 1..Int.MAX_VALUE.toLong()) return detached.withSessions(emptyList())
    val shifted = runCatching { workSessions.map { session ->
        val source = Calendar.getInstance(zone).apply { timeInMillis = session.start }
        val target = Calendar.getInstance(zone).apply {
            clear(); set(source.get(Calendar.YEAR), source.get(Calendar.MONTH), source.get(Calendar.DAY_OF_MONTH) + days.toInt(),
                source.get(Calendar.HOUR_OF_DAY), source.get(Calendar.MINUTE), source.get(Calendar.SECOND))
            set(Calendar.MILLISECOND, source.get(Calendar.MILLISECOND)); isLenient = false
        }
        // Set a valid target date independently before applying the requested wall-clock time.
        val date = (source.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 12); add(Calendar.DAY_OF_YEAR, days.toInt()) }
        target.set(Calendar.YEAR, date.get(Calendar.YEAR)); target.set(Calendar.MONTH, date.get(Calendar.MONTH)); target.set(Calendar.DAY_OF_MONTH, date.get(Calendar.DAY_OF_MONTH))
        val at = target.timeInMillis // rejects a DST gap
        val offsetChange = zone.getOffset(at - 86_400_000L) - zone.getOffset(at + 86_400_000L)
        if (offsetChange != 0) for (other in listOf(at - offsetChange, at + offsetChange)) {
            if (other == at) continue
            val alternate = Calendar.getInstance(zone).apply { timeInMillis = other }
            require(listOf(Calendar.YEAR, Calendar.MONTH, Calendar.DAY_OF_MONTH, Calendar.HOUR_OF_DAY, Calendar.MINUTE).any { alternate.get(it) != target.get(it) }) {
                "The recurring work time is ambiguous after a clock change"
            }
        }
        session.copy(start = at)
    }.also { sessions -> require(sessions.all { it.end <= nextDue }) { "Recurring work would finish after its deadline" } } }.getOrNull()
    // An invalid/ambiguous occurrence remains explicitly unscheduled rather than choosing an offset.
    return if (shifted == null) detached.withSessions(emptyList()) else detached.withSessions(shifted)
}
