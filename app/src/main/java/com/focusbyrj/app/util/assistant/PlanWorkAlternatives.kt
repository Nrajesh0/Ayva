package com.focusbyrj.app.util.assistant

import java.util.Calendar
import java.util.TimeZone

/** Offer a feasible later slot on the requested day without moving any other commitment. */
internal object PlanWorkAlternatives {
    fun laterOnSameDay(plan: PlanWorkspace, requested: PlanStep, occupied: List<PlanStep>, now: Long): PlanStep? {
        var at = requested.start ?: return null
        if (at <= now || requested.minutes == null) return null
        val zone = TimeZone.getTimeZone(plan.timezone)
        val day = Calendar.getInstance(zone).apply { timeInMillis = at; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val tomorrow = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        val others = plan.steps.filterNot { it.id == requested.id } + occupied
        repeat(others.size + 1) {
            val candidate = requested.shifted(at)
            val conflicts = others.filter { PlanWorkspace.overlaps(candidate, it) }
            if (conflicts.isEmpty()) {
                if (candidate.end!! > tomorrow || !withinAvailability(plan, candidate, now)) return null
                return candidate.takeIf {
                    runCatching { plan.copy(steps = plan.steps.map { if (it.id == candidate.id) candidate else it }).validate() }.isSuccess
                }
            }
            at = conflicts.maxOf { it.end!! }
            if (at >= tomorrow) return null
        }
        return null
    }

    private fun withinAvailability(plan: PlanWorkspace, step: PlanStep, now: Long): Boolean {
        val window = plan.facts["availability"] ?: return true
        val match = Regex("^(\\d{1,2}):00–(\\d{1,2}):00 (today|daily.*)$").matchEntire(window) ?: return false
        val date = Calendar.getInstance(TimeZone.getTimeZone(plan.timezone)).apply { timeInMillis = step.start!! }
        if (match.groupValues[3] == "today") {
            val today = Calendar.getInstance(date.timeZone).apply { timeInMillis = now }
            if (date.get(Calendar.YEAR) != today.get(Calendar.YEAR) || date.get(Calendar.DAY_OF_YEAR) != today.get(Calendar.DAY_OF_YEAR)) return false
        }
        val opening = (date.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, match.groupValues[1].toInt()); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val endHour = match.groupValues[2].toInt()
        val closing = (opening.clone() as Calendar).apply {
            if (endHour == 24) { set(Calendar.HOUR_OF_DAY, 0); add(Calendar.DAY_OF_YEAR, 1) } else set(Calendar.HOUR_OF_DAY, endHour)
        }
        opening.isLenient = false; closing.isLenient = false
        val start = runCatching { opening.timeInMillis }.getOrNull() ?: return false
        val end = runCatching { closing.timeInMillis }.getOrNull() ?: return false
        return step.start!! >= start && step.end!! <= end
    }
}
