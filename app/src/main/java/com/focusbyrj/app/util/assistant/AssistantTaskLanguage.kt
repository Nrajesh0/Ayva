package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.util.ChatTaskDraft
import com.focusbyrj.app.util.OfflineNluEngine
import com.focusbyrj.app.util.SmartDateParser

data class AssistantTaskRequest(val operation: String, val subject: String, val value: String? = null, val preserveClock: Boolean = false)
data class AssistantTaskSelection(val tasks: List<Task>, val group: Boolean = false, val clarification: String? = null)

/** Bounded grammar: requests are interpreted into data, never evaluated as code or split into CLI text. */
object AssistantTaskLanguage {
    fun normalize(input: String): String = OfflineNluEngine.normalizeRequest(input)
        .replace(Regex("(?i)\\b(?:tommorow|tommorrow|tomorow|tmrw|tmr)\\b"), "tomorrow")
        .replace(Regex("(?i)\\b(?:priorty|prioirty)\\b"), "priority")
        .replace(Regex("(?i)\\b(?:reshedule|rescedule)\\b"), "reschedule")
        .replace(Regex("(?i)\\b(?:plz|pls)\\s+"), "")
        .trim()

    fun request(input: String): AssistantTaskRequest? {
        var text = normalize(input).trimEnd('.', '!', '?')
        Regex("(?i)^mark\\s+(.+?)\\s+(?:as\\s+)?(?:done|complete|completed)$").matchEntire(text)?.let {
            text = "complete ${it.groupValues[1]}"
        }
        fun match(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).matchEntire(text)
        match("^(?:rename|change the name of)\\s+(.+?)\\s+(?:to|as)\\s+(.+)$")?.let { return AssistantTaskRequest("title", it.groupValues[1], it.groupValues[2]) }
        match("^call\\s+(it|that task|this task)\\s+(.+)$")?.let { return AssistantTaskRequest("title", it.groupValues[1], it.groupValues[2]) }
        match("^(?:add|set|change)\\s+(?:details|description)\\s+(?:for|of|to)\\s+(.+?):\\s*(.+)$")?.let { return AssistantTaskRequest("details", it.groupValues[1], it.groupValues[2]) }
        match("^(?:remove|clear)\\s+(?:the\\s+)?(?:due date|deadline|reminder time)\\s+(?:from|for|of)\\s+(.+)$")?.let { return AssistantTaskRequest("due", it.groupValues[1], "none") }
        match("^(?:make|mark|set)\\s+(.+?)\\s+(?:as\\s+)?(?:(?:a|the)\\s+)?(not priority|normal priority|low priority|high priority|priority|important|urgent)$")?.let {
            return AssistantTaskRequest("priority", it.groupValues[1], (!it.groupValues[2].lowercase().let { v -> v.startsWith("not") || v.startsWith("normal") || v.startsWith("low") }).toString())
        }
        match("^(?:prioritize|prioritise)\\s+(.+)$")?.let { return AssistantTaskRequest("priority", it.groupValues[1], "true") }
        match("^(?:remove priority from|deprioritize|deprioritise)\\s+(.+)$")?.let { return AssistantTaskRequest("priority", it.groupValues[1], "false") }
        match("^(?:make|set)\\s+(.+?)\\s+(not persistent|persistent)$")?.let { return AssistantTaskRequest("persistent", it.groupValues[1], (it.groupValues[2].lowercase() == "persistent").toString()) }
        match("^(?:turn|switch)\\s+(on|off)\\s+persistent reminders?\\s+for\\s+(.+)$")?.let { return AssistantTaskRequest("persistent", it.groupValues[2], (it.groupValues[1].lowercase() == "on").toString()) }
        match("^(?:repeat|make)\\s+(.+?)\\s+(daily|weekly|monthly|yearly|every day|every week|every month|every year|nonrecurring|not recurring)$")?.let {
            val recurrence = when (it.groupValues[2].lowercase()) {
                "daily", "every day" -> RecurrencePattern.DAILY
                "weekly", "every week" -> RecurrencePattern.WEEKLY
                "monthly", "every month" -> RecurrencePattern.MONTHLY
                "yearly", "every year" -> RecurrencePattern.YEARLY
                else -> RecurrencePattern.NONE
            }
            return AssistantTaskRequest("recurrence", it.groupValues[1], recurrence.name)
        }
        match("^(?:complete|completed|finish|finished|check off|done with|finished with|mark done)(?:\\s+(.*))?$")?.let { return AssistantTaskRequest("complete", it.groupValues[1]) }
        match("^(?:clean|clear)\\s+(?:all\\s+)?overdue(?:\\s+tasks?)?$")?.let { return AssistantTaskRequest("trash", "overdue tasks") }
        match("^(?:delete|remove|trash|cancel)(?:\\s+(.*))?$")?.let {
            if (!Regex("(?i)^(?:notes?|habits?|routines?|focus|timer|session)\\b").containsMatchIn(it.groupValues[1])) return AssistantTaskRequest("trash", it.groupValues[1])
        }
        match("^(?:reopen|uncomplete|mark undone)(?:\\s+(.*))?$")?.let { return AssistantTaskRequest("reopen", it.groupValues[1], "true") }
        match("^remind me (?:about|of) (it|that|this task)(?:\\s+(.+))?$")?.let {
            val date = SmartDateParser.parse(it.groupValues[2])
            return AssistantTaskRequest("due", it.groupValues[1], date.timestamp?.toString(), date.timestamp != null && !date.hasTime)
        }
        match("^(?:reschedule|postpone|move|push|delay|change the time of)(?:\\s+(.*))?$")?.let {
            val raw = it.groupValues[1]
            val parsed = SmartDateParser.parse(raw)
            val subject = if (parsed.timestamp != null && !parsed.hasTaskText) "it" else parsed.cleanText.replace(Regex("(?i)\\s+(?:to|until)$"), "").trim()
            return AssistantTaskRequest("due", subject, parsed.timestamp?.toString(), preserveClock = parsed.timestamp != null && !parsed.hasTime)
        }
        return null
    }

    fun select(subject: String, tasks: List<Task>, state: AssistantSession): AssistantTaskSelection {
        val raw = subject.trim().trim('"', '\'')
        Regex("(?i)^id:(\\d+)$").matchEntire(raw)?.let { id -> return AssistantTaskSelection(tasks.filter { it.id == id.groupValues[1].toLongOrNull() }) }
        val lower = raw.lowercase().removePrefix("the ").replace(Regex("\\s+"), " ")
        AssistantTaskReferences.select(lower, tasks, state)?.let { return it }
        if (lower in setOf("it", "that", "this", "that task", "this task", "the task", "that one", "this one")) return AssistantTaskSelection(tasks.filter { it.id == state.taskId })
        val number = Regex("(?i)^(?:(?:task|number|no\\.)\\s*#?\\s*)?(\\d+)(?:st|nd|rd|th)?(?:\\s+(?:task|one))?$").matchEntire(lower)?.groupValues?.get(1)?.toIntOrNull()
            ?: when (lower.removePrefix("the ").removeSuffix(" one").removeSuffix(" task")) { "first" -> 1; "second" -> 2; "third" -> 3; "fourth" -> 4; "fifth" -> 5; "last" -> state.candidates.size; else -> null }
        if (number != null) {
            val order = state.candidates
            return AssistantTaskSelection(tasks.filter { it.id == order.getOrNull(number - 1) })
        }
        val now = System.currentTimeMillis()
        if (Regex("^(?:all |my |the )?(?:overdue tasks?|overdue)$").matches(lower)) return AssistantTaskSelection(tasks.filter { it.dueDate?.let { due -> due < now } == true }, true)
        if (lower in setOf("all", "everything", "all tasks", "all my tasks", "all the tasks", "all todos")) return AssistantTaskSelection(tasks, true)
        if (lower in setOf("priority tasks", "all priority tasks", "important tasks")) return AssistantTaskSelection(tasks.filter { it.isPriority }, true)
        val keyword = lower.replace(Regex("^(?:the |my |task )"), "").trim()
        if (keyword.isBlank()) return AssistantTaskSelection(emptyList())
        val exact = tasks.filter { it.title.equals(keyword, true) }
        if (exact.isNotEmpty()) return AssistantTaskSelection(exact)
        val descriptive = keyword.replace(Regex("\\s+(?:task|reminder)$"), "")
        val tokens = descriptive.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }
            .map { if (it == "reading") "read" else it }
        if (tokens.isEmpty()) return AssistantTaskSelection(emptyList())
        val matches = tasks.filter { task ->
            val title = task.title.lowercase()
            val words = title.split(Regex("[^\\p{L}\\p{N}]+"))
            tokens.all { token -> words.any { word -> token == word || (token.length >= 4 && word.startsWith(token)) || (token.length >= 5 && word.length >= 5 && OfflineNluEngine.levenshtein(token, word) <= 1) } }
        }
        return AssistantTaskSelection(matches)
    }

    fun draft(input: String, priority: Boolean, persistent: Boolean): Task? {
        var text = normalize(input)
        var important = priority
        var keepReminding = persistent
        if (Regex("(?i)^(?:urgent|important|priority)[: ,]+?").containsMatchIn(text)) { important = true; text = text.replaceFirst(Regex("(?i)^(?:urgent|important|priority)[: ,]+"), "") }
        if (Regex("(?i)[, ]+(?:as (?:a )?|make (?:it|this) )?(?:high priority|priority|important|urgent)$").containsMatchIn(text)) {
            important = true; text = text.replace(Regex("(?i)[, ]+(?:as (?:a )?|make (?:it|this) )?(?:high priority|priority|important|urgent)$"), "")
        }
        if (Regex("(?i)[, ]+(?:with persistent reminders|persistent)$").containsMatchIn(text)) { keepReminding = true; text = text.replace(Regex("(?i)[, ]+(?:with persistent reminders|persistent)$"), "") }
        return ChatTaskDraft.parse(text, important, keepReminding)
    }

    fun action(request: AssistantTaskRequest, ids: List<Long>): AssistantAction? = when (request.operation) {
        "complete" -> AssistantAction.CompleteTasks(ids)
        "trash" -> AssistantAction.TrashTasks(ids)
        else -> request.value?.let { AssistantAction.EditTasks(ids, when (request.operation) {
            "title" -> TaskField.TITLE; "due" -> TaskField.DUE; "priority" -> TaskField.PRIORITY
            "persistent" -> TaskField.PERSISTENT; "recurrence" -> TaskField.RECURRENCE; "details" -> TaskField.DETAILS
            "reopen" -> TaskField.REOPEN; else -> return null
        }, it) }
    }
}
