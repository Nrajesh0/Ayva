package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.SmartDateParser
import java.util.Calendar

data class AssistantDateRevision(val actions: List<AssistantAction> = emptyList(), val clarification: String? = null, val stale: Boolean = false)

/** Revise the data in a live date preview; a revision never executes its actions. */
object AssistantTaskDateRepair {
    fun repair(input: String, state: AssistantSession, tasks: List<Task>): AssistantDateRevision? {
        val original = input.lowercase().trimEnd('.', '!', '?').replaceFirst(Regex("^(?:actually|instead)[, ]+"), "")
        val negated = Regex("^not .+?[,—–]\\s*(.+)$").matchEntire(original)
        val text = negated?.groupValues?.get(1) ?: original
        val keepTime = Regex("^(?:keep|use) (?:their|the|its|both|our)(?: original| existing| same)? times?$").matches(text) || text == "same time"
        if (state.expected != "confirm") return if (keepTime) AssistantDateRevision(clarification = "Ask me to move a task or selection first, then we can keep its original time.") else null
        val edits = state.pending.filterIsInstance<AssistantAction.EditTasks>()
        if (edits.isEmpty() || edits.size != state.pending.size || edits.any { it.field != TaskField.DUE || it.ids.size != 1 || it.value.toLongOrNull() == null }) return null
        val ids = edits.flatMap { it.ids }
        val originals = tasks.associateBy { it.id }
        fun unclear(message: String) = AssistantDateRevision(clarification = message)
        val except = Regex("^(?:except|leave out|skip) (.+)$").matchEntire(text)?.groupValues?.get(1)
            ?: Regex("^(?:leave|keep) (.+?) (?:unchanged|as it is|as they are|where it is|where they are)$").matchEntire(text)?.groupValues?.get(1)
        val request = AssistantTaskLanguage.request(text.replaceFirst(Regex("^(?:leave|keep) (.+?) (?:on |at )?(tomorrow|today|monday|tuesday|wednesday|thursday|friday|saturday|sunday)(.*)$"), "move $1 to $2$3"))
        val bareDate = SmartDateParser.parse(text)
        if (!keepTime && except == null && request?.operation != "due" && (bareDate.timestamp == null || bareDate.hasTaskText))
            return if (original.startsWith("not ")) unclear("What day and time should replace the date in this preview? The other details are still available.") else null
        if (ids.any { id -> originals[id]?.let { it.isCompleted || it.isTrashed || it.updatedAt != state.versions[id] } != false })
            return AssistantDateRevision(clarification = "A selected task changed while we were talking. Please ask for the date change again; nothing in this preview was applied.", stale = true)
        if (keepTime) {
            if (ids.any { originals[it]?.dueDate == null }) return unclear("At least one selected task has no original time. Tell me a time to use, such as ‘actually Friday at 8am’.")
            return AssistantDateRevision(edits.map { it.copy(value = preserveTime(it.value.toLong(), originals.getValue(it.ids.single()).dueDate!!).toString()) })
        }
        val subject = except ?: request?.takeIf { it.operation == "due" }?.subject
        val selected = if (subject == null || subject == "it" && ids.size == 1) ids else {
            val selection = AssistantTaskLanguage.select(subject, tasks, state)
            selection.clarification?.let { return unclear(it) }
            if (selection.tasks.isEmpty() || selection.tasks.size > 1 && !selection.group) return unclear("Which task in this preview should change? Use its list number or full name.")
            selection.tasks.map { it.id }
        }
        if (selected.any { it !in ids }) {
            val correction = Regex("^(?:actually|instead|leave|keep|except|skip)\\b").containsMatchIn(input.lowercase())
            if (!correction && except == null && request != null) return null
            return unclear("That task isn't in this preview. Choose an item from this preview, or send a separate request.")
        }
        if (except != null) return AssistantDateRevision(edits.filterNot { it.ids.single() in selected })
        val value = request?.takeIf { it.operation == "due" }?.value?.toLongOrNull() ?: bareDate.timestamp
            ?: return unclear("What day and time should I use? The other details in this preview are still available.")
        val hasTime = if (request?.operation == "due") !request.preserveClock else bareDate.hasTime
        return AssistantDateRevision(edits.map { edit ->
            if (edit.ids.single() !in selected) edit else {
                // A date-only repair keeps the clock already shown in the preview.
                edit.copy(value = (if (hasTime) value else preserveTime(value, edit.value.toLong())).toString())
            }
        })
    }

    private fun preserveTime(date: Long, clock: Long): Long {
        val previous = Calendar.getInstance().apply { timeInMillis = clock }
        return Calendar.getInstance().apply {
            timeInMillis = date
            set(Calendar.HOUR_OF_DAY, previous.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, previous.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
