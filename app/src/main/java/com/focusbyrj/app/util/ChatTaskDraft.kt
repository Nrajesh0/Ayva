package com.focusbyrj.app.util

import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskType

/** One task representation for typed tasks, natural requests, and task-choice buttons. */
object ChatTaskDraft {
    fun parse(input: String, isPriority: Boolean = false, isPersistent: Boolean = false): Task? {
        val content = input.trim().replace(Regex("^/(?:task|create|add|todo|talk|ask)\\b\\s*", RegexOption.IGNORE_CASE), "")
        val normalized = OfflineNluEngine.normalizeRequest(content)
        val parsed = SmartDateParser.parse(normalized)
        val title = OfflineNluEngine.extractTaskCreationDetails(normalized).first
        if (title.isBlank()) return null
        val type = when {
            Regex("\\b(?:birthday|bday)\\b", RegexOption.IGNORE_CASE).containsMatchIn(title) -> TaskType.BIRTHDAY
            Regex("\\banniversary\\b", RegexOption.IGNORE_CASE).containsMatchIn(title) -> TaskType.ANNIVERSARY
            else -> TaskType.TASK
        }
        val recurrence = if (parsed.recurrence == RecurrencePattern.NONE && type != TaskType.TASK) {
            RecurrencePattern.YEARLY
        } else parsed.recurrence
        return Task(
            title = title, details = parsed.note.orEmpty(), dueDate = parsed.timestamp,
            type = type, recurrence = recurrence, isPriority = isPriority, isPersistent = isPersistent
        )
    }
}
