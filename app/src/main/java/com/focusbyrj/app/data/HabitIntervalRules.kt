package com.focusbyrj.app.data

/** Reminder interval rules shared by the habit editor and chat creation form. */
internal object HabitIntervalRules {
    enum class WindowPolicy {
        EDITOR,
        CHAT,
    }

    fun windowMinutes(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        policy: WindowPolicy = WindowPolicy.EDITOR,
    ): Int {
        val start = startHour * 60 + startMinute
        val end = endHour * 60 + endMinute
        // Preserve each form's existing short-window policy rather than changing saved schedules.
        val minimum =
            when {
                policy == WindowPolicy.EDITOR -> 30
                end > start -> 0
                else -> 15
            }
        return (if (end > start) end - start else 24 * 60 - start + end).coerceAtLeast(minimum)
    }

    fun intervalForTarget(windowMinutes: Int, target: Int): Int =
        (windowMinutes / target.coerceAtLeast(1)).coerceAtLeast(5)

    fun stepInterval(currentMinutes: Int, direction: Int, windowMinutes: Int): Int {
        val step = if (currentMinutes <= 90) 15 else 30
        val next =
            if (direction > 0) {
                ((currentMinutes / step) + 1) * step
            } else {
                (((currentMinutes - 1) / step) * step).coerceAtLeast(15)
            }
        // A short window must never invert coerceIn's bounds and crash the form.
        return next.coerceIn(15, windowMinutes.coerceAtLeast(15))
    }

    fun targetForInterval(windowMinutes: Int, intervalMinutes: Int): Int =
        (windowMinutes / intervalMinutes).coerceIn(1, 24)
}
