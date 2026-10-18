package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.Task

/** References point to displayed IDs, never a freshly sorted database query. */
object AssistantTaskReferences {
    private val counts = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10)
    private val ordinals = mapOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
        "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9, "tenth" to 10)

    fun select(subject: String, tasks: List<Task>, state: AssistantSession): AssistantTaskSelection? {
        val explicit = Regex("\\b(?:tasks?|task list)\\b").containsMatchIn(subject)
        val text = subject.removeSuffix(" from that list").removeSuffix(" from the list")
            .removePrefix("the ").replace(Regex("^(?:tasks?|number|no\\.)\\s*#?\\s*"), "")
            .replace(Regex("\\s+(?:tasks?|ones?)$"), "").trim()
        val fresh = System.currentTimeMillis() - state.taskListAt in 0..900_000L
        val list = if (fresh) state.taskListIds else if (state.taskListIds.isEmpty()) state.candidates else emptyList()
        fun unclear(message: String) = AssistantTaskSelection(emptyList(), clarification = message)
        fun resolve(ids: List<Long>, group: Boolean): AssistantTaskSelection {
            if (state.topic !in setOf("tasks", "planning") && !explicit)
                return unclear("Which tasks do you mean? Say ‘show my tasks’ or name the tasks first.")
            if (ids.isEmpty()) return unclear("I need a current task list or selection first. Say ‘show my tasks’.")
            val byId = tasks.associateBy { it.id }
            if (ids.any { it !in byId }) return unclear("An item in that selection is no longer available for this action. Ask me to show the task list again; nothing has changed.")
            return AssistantTaskSelection(ids.distinct().map { byId.getValue(it) }, group)
        }
        val countWord = "(?:\\d+|one|two|three|four|five|six|seven|eight|nine|ten)"
        val group = Regex("^(?:both|both of them|them|those|these|all of them|all of those|selected|selected tasks|(?:those|these) $countWord)$").matches(text)
        if (group) {
            if (state.selectedTaskIds.size > 100) return unclear("That selection is too large for a group follow-up. Choose up to 100 tasks by name or from a smaller task view.")
            if (state.selectedTaskIds.isEmpty() && !state.taskListComplete) return unclear("That task list is too long for a group reference. Name the tasks or ask for a smaller task view first.")
            val ids = state.selectedTaskIds.ifEmpty { list }
            val count = if (text.startsWith("both")) 2 else text.substringAfter(' ', "").let { counts[it] ?: it.toIntOrNull() }
            if ((text.startsWith("those ") || text.startsWith("these ")) && count == null)
                return unclear("Choose between 1 and 100 tasks using their names or list numbers.")
            if (count != null && ids.size != count) return unclear("Which $count tasks do you mean? Select them by their names or list numbers first.")
            return resolve(ids, true)
        }
        Regex("^(first|last) (\\d+|one|two|three|four|five|six|seven|eight|nine|ten)$").matchEntire(text)?.let {
            if (it.groupValues[1] == "last" && !state.taskListComplete) return unclear("I don't have the end of that long task list in context. Use the task names or a smaller task view.")
            val count = counts[it.groupValues[2]] ?: it.groupValues[2].toIntOrNull() ?: return unclear("How many tasks do you mean?")
            if (count !in 1..list.size) return unclear("That many items aren't available in the last displayed task list. Say ‘show my tasks’ to review it.")
            return resolve(if (it.groupValues[1] == "first") list.take(count) else list.takeLast(count), count > 1)
        }
        val positions = text.split(Regex("\\s*(?:,\\s*(?:and\\s+)?|\\s+and\\s+)\\s*"))
        if (!state.taskListComplete && positions.any { it.removePrefix("the ").removeSuffix(" task").removeSuffix(" one") == "last" })
            return unclear("I don't have the end of that long task list in context. Use the task names or a smaller task view.")
        fun position(raw: String): Int? {
            val clean = raw.removePrefix("the ").removePrefix("task ").removeSuffix(" one").removeSuffix(" task")
            return ordinals[clean] ?: if (clean == "last") list.size else Regex("^(\\d+)(?:st|nd|rd|th)?$").matchEntire(clean)?.groupValues?.get(1)?.toIntOrNull()
        }
        val numbers = positions.map { position(it) }
        if (numbers.all { it != null }) {
            if (numbers.any { it!! !in 1..list.size }) return unclear("That number isn't in the last displayed task list. Say ‘show my tasks’ to review it.")
            return resolve(numbers.map { list[it!! - 1] }, positions.size > 1)
        }
        return null
    }
}
