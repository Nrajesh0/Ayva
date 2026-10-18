package com.focusbyrj.app.util.assistant

/** Missing edit fields become questions about the already selected IDs. */
object AssistantEditDialogue {
    val slots = setOf("task_edit_field", "task_new_title", "task_priority", "task_details", "task_recurrence", "task_persistent")
    fun request(input: String, state: AssistantSession): AssistantTaskRequest? {
        val match = Regex("(?i)^(change|edit|update|fix)\\s+(.+)$").matchEntire(input.trimEnd('.', '!', '?')) ?: return null
        if (AssistantPreferences.find(input) != null) return null
        val subject = match.groupValues[2]
        val explicit = Regex("(?i)\\btasks?\\b").containsMatchIn(subject)
        val reference = Regex("(?i)^(?:it|that|this|them|both|these|those|(?:the )?(?:first|second|third|last)(?: one)?)$").matches(subject)
        if (!explicit && (!reference || state.topic in setOf("habits", "focus", "routines") || state.topic?.startsWith("setting:") == true)) return null
        return AssistantTaskRequest("edit", subject.replace(Regex("(?i)^(?:my|the) task$"), "it"))
    }
    fun field(input: String): TaskField? = when (input.lowercase().trimEnd('.', '!', '?').removePrefix("the ")) {
        "date", "time", "due date", "deadline", "reminder time" -> TaskField.DUE
        "title", "name", "task name" -> TaskField.TITLE
        "priority", "importance" -> TaskField.PRIORITY
        "details", "description" -> TaskField.DETAILS
        "repeat", "recurrence", "repeating", "schedule" -> TaskField.RECURRENCE
        "persistent", "persistent reminders" -> TaskField.PERSISTENT
        else -> null
    }
    fun priority(input: String): Boolean? = when (input.lowercase().trimEnd('.', '!', '?')) {
        "yes", "yep", "yeah", "high", "high priority", "priority", "important", "urgent" -> true
        "no", "nope", "normal", "normal priority", "low", "low priority", "not priority" -> false
        else -> null
    }
    fun toggle(input: String): Boolean? = when (input.lowercase().trimEnd('.', '!', '?')) {
        "yes", "on", "enable", "enabled" -> true
        "no", "off", "disable", "disabled" -> false
        else -> null
    }
    fun recurrence(input: String): String? = when (input.lowercase().trimEnd('.', '!', '?')) {
        "none", "never", "off", "no repeat", "stop repeating" -> "NONE"
        "daily", "every day" -> "DAILY"
        "weekly", "every week" -> "WEEKLY"
        "monthly", "every month" -> "MONTHLY"
        "yearly", "annually", "every year" -> "YEARLY"
        else -> null
    }
}
