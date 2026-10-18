package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.SmartDateParser
import org.json.JSONArray
import org.json.JSONObject

data class AssistantCorrection(val actions: List<AssistantAction>, val needsDate: Boolean = false, val clarification: String? = null, val preserveClock: Boolean = false)

/** Bounded conversational repairs. References and suspended questions never contain executable CLI text. */
object AssistantConversation {
    private val taskQuestions = setOf("task_date", "correction_date") + AssistantEditDialogue.slots
    private val resumable = setOf("task_title", "habit_title", "habit_edit_value", "setting_value", "plan_budget", "feature_value") + taskQuestions

    private fun questions(state: AssistantSession) = listOfNotNull(state.suspendedRequest) + state.olderQuestions
    private fun saveQuestions(state: AssistantSession, questions: List<JSONObject>) {
        state.suspendedRequest = questions.firstOrNull()
        state.olderQuestions = questions.drop(1).take(2)
    }

    fun suspendQuestion(state: AssistantSession) {
        if (state.expected !in resumable) return
        val taskQuestion = state.expected in taskQuestions
        val saved = JSONObject().put("expected", state.expected).put("savedAt", System.currentTimeMillis())
            .put("task", state.taskId.takeIf { taskQuestion } ?: JSONObject.NULL)
            .put("habit", state.habitId.takeIf { state.expected == "habit_edit_value" } ?: JSONObject.NULL)
            .put("routineDraft", state.featureState?.optJSONObject("routineDraft")?.takeIf { state.expected == "feature_value" }?.let { JSONObject(it.toString()) } ?: JSONObject.NULL)
            .put("planning", state.planning?.takeIf { state.expected == "plan_budget" }?.toJson() ?: JSONObject.NULL)
            .put("selected", JSONArray(state.selectedTaskIds.takeIf { taskQuestion }.orEmpty()))
            .put("list", JSONArray(state.taskListIds.takeIf { taskQuestion }.orEmpty()))
            .put("listAt", if (taskQuestion) state.taskListAt else 0L)
            .put("listComplete", state.taskListComplete)
            .put("topic", state.topic ?: JSONObject.NULL)
            .put("priority", state.pendingPriority).put("persistent", state.pendingPersistent)
            .put("pending", JSONArray(state.pending.map { it.toJson() }))
            .put("versions", JSONObject().apply { state.versions.forEach { (id, version) -> put(id.toString(), version) } })
        val others = questions(state).filterNot {
            it.optString("expected") == state.expected && it.optString("pending") == saved.optString("pending") && it.optString("task") == saved.optString("task")
        }
        saveQuestions(state, (listOf(saved) + others).take(3))
    }

    fun resumeQuestion(state: AssistantSession, tasks: List<Task>, input: String = "back to that"): Boolean {
        val kind = when {
            input.contains("routine") -> setOf("feature_value")
            input.contains("planning") -> setOf("plan_budget")
            input.contains("habit") -> setOf("habit_title", "habit_edit_value")
            input.contains("setting") -> setOf("setting_value")
            input.contains("reminder") || input.contains("task") -> setOf("task_title") + taskQuestions
            else -> resumable
        }
        val savedQuestions = questions(state)
        val index = savedQuestions.indexOfFirst { it.optString("expected") in kind }
        if (index < 0) return false
        val saved = savedQuestions[index]
        saveQuestions(state, savedQuestions.filterIndexed { i, _ -> i != index })
        val expected = saved.optString("expected")
        if (expected !in resumable || System.currentTimeMillis() - saved.optLong("savedAt") !in 0..900_000L) return false
        val versions = saved.optJSONObject("versions") ?: JSONObject()
        if (versions.keys().asSequence().any { key -> tasks.none { it.id == key.toLongOrNull() && it.updatedAt == versions.getLong(key) && !it.isCompleted && !it.isTrashed } }) return false
        val taskId = if (saved.isNull("task")) null else saved.getLong("task")
        if (taskId != null && tasks.none { it.id == taskId && !it.isCompleted && !it.isTrashed }) return false
        val pending = saved.getJSONArray("pending")
        val actions = (0 until pending.length()).map { AssistantAction.fromJson(pending.getJSONObject(it)) }
        // Preserve a different live question before returning to this one. A
        // confirmation is never in this stack and its token is never restored.
        if (state.expected != expected || JSONArray(state.pending.map { it.toJson() }).toString() != pending.toString()) suspendQuestion(state)
        state.clearPending()
        state.pending = actions
        state.expected = expected
        if (expected == "feature_value") {
            if (state.featureState == null) state.featureState = JSONObject()
            state.featureState!!.put("routineDraft", saved.getJSONObject("routineDraft"))
        }
        if (expected == "plan_budget") state.planning = saved.optJSONObject("planning")?.let(AssistantPlanContext::fromJson)
        state.pendingPriority = saved.optBoolean("priority"); state.pendingPersistent = saved.optBoolean("persistent")
        state.versions = versions.keys().asSequence().associate { it.toLong() to versions.getLong(it) }
        val selected = saved.optJSONArray("selected")?.longs().orEmpty()
        if (taskId != null) state.focusTask(taskId)
        else if (selected.isNotEmpty()) state.focusTasks(selected)
        else state.switchTopic(saved.optString("topic").takeUnless { it.isBlank() || it == "null" }
            ?: if (expected.startsWith("habit_")) "habits" else if (expected == "setting_value") "settings" else "tasks")
        if (expected == "habit_edit_value" && !saved.isNull("habit")) state.habitId = saved.getLong("habit")
        if (expected in taskQuestions) {
            state.taskListIds = saved.optJSONArray("list")?.longs().orEmpty().take(100)
            state.taskListAt = saved.optLong("listAt")
            state.taskListComplete = saved.optBoolean("listComplete", true)
            state.candidates = state.taskListIds
        }
        return true
    }

    fun wantsResume(input: String) = input.lowercase().trimEnd('.', '!', '?') in setOf(
        "back to the routine", "back to that routine", "continue the routine", "back to that", "back to the reminder", "back to that reminder", "continue the reminder", "continue that request", "resume that request", "back to the question",
        "back to the setting", "back to that setting", "continue the setting", "back to the habit", "back to that habit", "continue the habit", "resume the last request", "back to planning", "back to the planning question"
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
