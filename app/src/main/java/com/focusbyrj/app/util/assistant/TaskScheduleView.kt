package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.planning
import com.focusbyrj.app.util.SmartDateParser

/** Read views distinguish a deadline from explicit work; mutation filters retain deadline semantics. */
internal object TaskScheduleView {
    fun matches(task: Task, start: Long, end: Long, deadlinesOnly: Boolean = false): Boolean =
        (task.planning?.availableFrom?.let { it < end } != false || deadlinesOnly) &&
            (task.dueDate?.let { it in start until end } == true || !deadlinesOnly &&
                (task.planning?.workSessions.orEmpty().any { it.start < end && start < it.end } ||
                    task.dueDate == null && task.planning?.availableFrom?.let { it < end } == true))
    fun describe(task: Task, start: Long? = null, end: Long? = null): String = buildList {
        task.planning?.availableFrom?.let { add("available ${SmartDateParser.formatDueDate(it)}") }
        task.dueDate?.let { add("due ${SmartDateParser.formatDueDate(it)}") }
        val sessions = task.planning?.workSessions.orEmpty().filter { start == null || end == null || it.start < end && start < it.end }
        sessions.take(3).forEach { add("work ${SmartDateParser.formatDueDate(it.start)} · ${it.minutes} min") }
        if (sessions.size > 3) add("${sessions.size - 3} more work sessions")
        if (isEmpty()) add("no date or work session")
    }.joinToString(" · ")
}
