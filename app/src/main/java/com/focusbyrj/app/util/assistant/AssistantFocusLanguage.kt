package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.Task

data class FocusTaskRequest(val subject: String, val duration: String?, val literal: Boolean = false)

/** A task's own ‘for’ is not a duration separator. Quoted titles remain literal. */
object AssistantFocusLanguage {
    fun request(input: String, tasks: List<Task>): FocusTaskRequest? {
        val body = Regex("(?i)^(?:(?:start |help me )?focus on) (.+)$").matchEntire(input.trimEnd('.', '!', '?'))?.groupValues?.get(1) ?: return null
        if (body.startsWith('"')) {
            val end = body.indexOf('"', 1)
            if (end > 0) {
                val tail = body.substring(end + 1).trim()
                if (tail.isEmpty()) return FocusTaskRequest(body.substring(1, end), null, literal = true)
                if (tail.startsWith("for ", true)) return FocusTaskRequest(body.substring(1, end), tail.substring(4).trim(), literal = true)
            }
            // The duration validator will ask for a clear request rather than guessing.
            return FocusTaskRequest(body, "invalid")
        }
        if (body.contains(" for ", true) && tasks.any { !it.isCompleted && !it.isTrashed && it.deletedAt == null && it.title.equals(body, true) })
            return FocusTaskRequest(body, null, literal = true)
        val split = Regex("(?i)^(.+) for (.+)$").matchEntire(body)
        return if (split != null) FocusTaskRequest(split.groupValues[1], split.groupValues[2]) else FocusTaskRequest(body, null)
    }
}
