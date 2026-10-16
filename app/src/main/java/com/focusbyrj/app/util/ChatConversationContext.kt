package com.focusbyrj.app.util

import com.focusbyrj.app.data.Task

/** Short-lived, chat-local task reference. IDs never depend on the current task-list ordering. */
class ChatConversationContext(private val clock: () -> Long = System::currentTimeMillis) {
    private var taskId: Long? = null
    private var touchedAt = 0L
    private var awaitingDate = false

    fun focusTask(id: Long, requestDate: Boolean = false) {
        taskId = id
        touchedAt = clock()
        awaitingDate = requestDate
    }

    fun clear() {
        taskId = null
        awaitingDate = false
    }

    fun resolve(input: String, pendingTasks: List<Task>): String {
        val text = OfflineNluEngine.normalizeRequest(input)
        if (text.lowercase() in setOf("cancel", "never mind", "nevermind", "forget it")) {
            clear()
            return text
        }
        val id = taskId ?: return text
        if (clock() - touchedAt !in 0..600_000L || pendingTasks.none { it.id == id && !it.isCompleted && !it.isTrashed }) {
            clear()
            return text
        }
        // Explicit commands and newly named tasks always take precedence over conversational memory.
        if (text.startsWith("/") || OfflineNluEngine.isExplicitCreation(text)) return text
        val lower = text.lowercase().trimEnd('.', '!', '?')
        val pronounAction = Regex("^(?:reschedule|postpone|move|push|delay|complete|finish|delete|remove|mark)\\s+(?:it|that|that task|this task)\\b")
        if (pronounAction.containsMatchIn(lower)) {
            touchedAt = clock()
            awaitingDate = false
            return lower.replaceFirst(Regex("\\b(?:that task|this task|it|that)\\b"), "id:$id")
                .replaceFirst(Regex("^(?:move|push|delay|postpone)\\b"), "reschedule")
        }
        if (lower in setOf("done", "done with it", "finished it", "i finished it", "i completed it")) {
            return "complete id:$id"
        }
        val timeReply = lower.replace(Regex("^(?:actually\\s+|make (?:it|that)\\s+|instead\\s+)"), "")
        val parsed = SmartDateParser.parse(timeReply)
        if ((awaitingDate || timeReply != lower) && parsed.timestamp != null && !parsed.hasTaskText) {
            touchedAt = clock()
            awaitingDate = false
            return "reschedule id:$id $timeReply"
        }
        if (OfflineNluEngine.isQuestion(text) ||
            Regex("\\b(?:settings?|permissions?|status|habits?|routines?|notes?|advice|stressed|tired|overwhelmed)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            clear()
        }
        return text
    }
}
