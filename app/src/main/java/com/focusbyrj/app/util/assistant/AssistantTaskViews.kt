package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.util.AyvaTalkEngine
import com.focusbyrj.app.util.SmartDateParser
import java.util.Calendar
import java.util.Locale

/** Composable read filters. A displayed ordinal always refers to the displayed IDs. */
internal object AssistantTaskViews {
    fun answer(state: AssistantSession, input: String, tasks: List<Task>): AyvaTalkEngine.TalkResponse? {
        val text = input.lowercase(Locale.ROOT).trimEnd('?', '.', '!')
        if (!Regex("^(?:(?:show|list|find|search|view) (?:me )?)?(?:my )?(?:(?:all|pending|completed|priority|high priority|overdue|unscheduled|undated|recurring|repeating)\\s+)*tasks\\b").containsMatchIn(text)) return null
        if (Regex("\\b(?:overdue|priority|completed|pending|today|tomorrow)\\b").findAll(text).count() < 2 && !Regex("\\b(?:unscheduled|undated|without (?:a )?due date|no due date|recurring|repeating|this week|next week|containing|about|matching)\\b").containsMatchIn(text)) return null
        // Reject unrecognized suffixes instead of silently dropping a requested constraint.
        val search = Regex("\\b(?:containing|about|matching) (.+)$").find(text)?.groupValues?.get(1)?.trim('"')
        val filter = if (search == null) text else text.substringBeforeLast(Regex("\\b(?:containing|about|matching) ").find(text)!!.value)
        val unknown = filter.replace(Regex("\\b(?:show|list|find|search|view|me|my|all|pending|completed|priority|high|overdue|tasks|and|due|for|unscheduled|undated|without a due date|without due date|no due date|recurring|repeating|this week|next week|today|tomorrow)\\b"), "").trim()
        if (unknown.isNotEmpty()) return null
        val now = System.currentTimeMillis()
        val week = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); if (filter.contains("next week")) add(Calendar.WEEK_OF_YEAR, 1) }
        val end = (week.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, 1) }.timeInMillis
        val day = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); if (filter.contains("tomorrow")) add(Calendar.DAY_OF_YEAR, 1) }
        val dayEnd = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        val selected = tasks.filter { task ->
            !task.isTrashed && task.deletedAt == null && task.isCompleted == filter.contains("completed") &&
                (!filter.contains("priority") || task.isPriority) &&
                (!filter.contains("overdue") || task.dueDate?.let { it < now } == true) &&
                (!Regex("unscheduled|undated|without|no due date").containsMatchIn(filter) || task.dueDate == null) &&
                (!Regex("recurring|repeating").containsMatchIn(filter) || task.recurrence != RecurrencePattern.NONE) &&
                (!(filter.contains("today") || filter.contains("tomorrow")) || task.dueDate?.let { it in day.timeInMillis until dayEnd } == true) &&
                (!filter.contains("week") || task.dueDate?.let { it in week.timeInMillis until end } == true) &&
                (search == null || task.title.lowercase(Locale.ROOT).contains(search))
        }.sortedWith(compareByDescending<Task> { it.isPriority }.thenBy { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.id })
        val shown = selected.take(20)
        state.clearPending(); state.switchTopic("tasks"); state.rememberTaskList(shown.map { it.id })
        val body = if (selected.isEmpty()) "No tasks match those filters." else shown.mapIndexed { i, task -> "${i + 1}. **${task.title}** — ${task.dueDate?.let(SmartDateParser::formatDueDate) ?: "no due date"}" }.joinToString("\n")
        val actions = emptyList<com.focusbyrj.app.util.TalkAction>()
        return AssistantResponseContent.taskList(AyvaTalkEngine.TalkResponse(body, actions, "tasks"), selected, state.taskListAt)
    }
}
