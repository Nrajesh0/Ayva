package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.SmartDateParser
import org.json.JSONArray
import org.json.JSONObject

data class AssistantCorrection(val actions: List<AssistantAction>, val needsDate: Boolean = false, val clarification: String? = null, val preserveClock: Boolean = false)

/** Bounded conversational repairs. References and suspended questions never contain executable CLI text. */
object AssistantConversation {
    private val resumable = setOf("task_title", "task_date", "habit_title", "setting_value", "correction_date")

    fun suspendQuestion(state: AssistantSession) {
        if (state.expected !in resumable) return
        state.suspendedRequest = JSONObject().put("expected", state.expected).put("savedAt", System.currentTimeMillis())
            .put("task", state.taskId ?: JSONObject.NULL).put("priority", state.pendingPriority).put("persistent", state.pendingPersistent)
            .put("pending", JSONArray(state.pending.map { it.toJson() }))
            .put("versions", JSONObject().apply { state.versions.forEach { (id, version) -> put(id.toString(), version) } })
    }

    fun resumeQuestion(state: AssistantSession, tasks: List<Task>): Boolean {
        val saved = state.suspendedRequest ?: return false
        state.suspendedRequest = null
        val expected = saved.optString("expected")
        if (expected !in resumable || System.currentTimeMillis() - saved.optLong("savedAt") !in 0..900_000L) return false
        val versions = saved.optJSONObject("versions") ?: JSONObject()
        if (versions.keys().asSequence().any { key -> tasks.none { it.id == key.toLongOrNull() && it.updatedAt == versions.getLong(key) } }) return false
        val taskId = if (saved.isNull("task")) null else saved.getLong("task")
        if (taskId != null && tasks.none { it.id == taskId && !it.isCompleted }) return false
        val pending = saved.getJSONArray("pending")
        state.clearPending()
        state.pending = (0 until pending.length()).map { AssistantAction.fromJson(pending.getJSONObject(it)) }
        state.expected = expected
        state.pendingPriority = saved.optBoolean("priority"); state.pendingPersistent = saved.optBoolean("persistent")
        state.versions = versions.keys().asSequence().associate { it.toLong() to versions.getLong(it) }
        if (taskId != null) state.focusTask(taskId) else state.switchTopic(if (expected == "habit_title") "habits" else if (expected == "setting_value") "settings" else "tasks")
        return true
    }

    fun wantsResume(input: String) = input.lowercase().trimEnd('.', '!', '?') in setOf(
        "back to that", "back to the reminder", "back to that reminder", "continue the reminder", "continue that request", "resume that request", "back to the question"
    )

    fun isTopicFollowUp(input: String) = input.lowercase().trimEnd('.', '!', '?') in setOf(
        "why", "where", "how", "more", "tell me more", "explain that", "how to change it", "how do i change it", "where is it", "how does it work"
    )

    fun reference(input: String): String? {
        val text = input.trimEnd('.', '!', '?')
        if (Regex("(?i)^(?:back to|return to) (?:the |that |my )?(?:task|reminder)$").matches(text)) return "__previous__"
        if (Regex("(?i)^(?:no[, ]+)?(?:not that(?: one| task)?[, ]+)?(?:the )?other (?:one|task|reading one)$").matches(text)) return "__other__"
        Regex("(?i)^(?:let's work on|lets work on|let's talk about|lets talk about|work on|select task|about task|back to task)\\s+(.+)$").matchEntire(text)?.let { return it.groupValues[1] }
        Regex("(?i)^no[, ]+(?:not that(?: one)?[, ]+)?(?:the )?((?:first|second|third|fourth|fifth|last|\\d+)(?: one| task)?)$").matchEntire(text)?.let { return it.groupValues[1] }
        return null
    }

    fun correction(input: String, task: Task): AssistantCorrection? {
        val raw = input.trimEnd('.', '!', '?')
        val prefixed = Regex("(?i)^(?:actually[, ]+|no[, ]+|i meant |make that )(.+)$").matchEntire(raw)
        val text = (prefixed?.groupValues?.get(1) ?: raw.takeIf { it.startsWith("not ", true) } ?: return null)
            .replace('—', ',').replace('–', ',').trim()
        val replacement = Regex("(?i)^(.+?)\\s+instead of\\s+(.+?)(?:\\s*(?:,\\s*(?:and\\s+)?|\\s+and\\s+)\\s*(.+))?$").matchEntire(text)
        if (replacement != null) {
            val new = replacement.groupValues[1].trim().trim('"', '\'')
            val old = replacement.groupValues[2].trim().trim('"', '\'')
            val token = Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(old)}(?![\\p{L}\\p{N}])")
            if (new.isBlank() || token.findAll(task.title).count() != 1) return AssistantCorrection(emptyList(), clarification = "Which part of **${task.title}** should I replace? Say ‘rename it to’ followed by the full title.")
            val actions = listOf(AssistantAction.EditTasks(listOf(task.id), TaskField.TITLE, task.title.replace(token) { new }))
            val time = replacement.groupValues[3].trim()
            if (time.isBlank()) return AssistantCorrection(actions)
            val date = SmartDateParser.parse(time)
            if (date.timestamp == null || date.hasTaskText) return AssistantCorrection(actions + AssistantAction.EditTasks(listOf(task.id), TaskField.DUE, ""), needsDate = true)
            return AssistantCorrection(actions + AssistantAction.EditTasks(listOf(task.id), TaskField.DUE, date.timestamp.toString()), preserveClock = !date.hasTime)
        }
        Regex("(?i)^not (.+?),\\s*(.+)$").matchEntire(text)?.let {
            val dateText = it.groupValues[2].replace(Regex("(?i)[,. ]+(?:and )?(?:keep (?:the|its) time|same time)$"), "")
            val date = SmartDateParser.parse(dateText)
            if (date.timestamp != null && !date.hasTaskText) return AssistantCorrection(listOf(AssistantAction.EditTasks(listOf(task.id), TaskField.DUE, date.timestamp.toString())), preserveClock = !date.hasTime)
            return AssistantCorrection(emptyList(), clarification = "What exact day and time should **${task.title}** move to?")
        }
        return null
    }
}
