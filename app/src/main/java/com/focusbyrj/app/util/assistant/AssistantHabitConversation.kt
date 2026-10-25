package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitType

/** Habit-only follow-ups never borrow a task or global-setting reference. */
internal object AssistantHabitConversation {
    fun proposedValue(action: AssistantAction.EditHabit): String = when (action.field) {
        HabitField.REMINDER_TIME -> action.value.removePrefix("@").toIntOrNull()?.let { "%02d:%02d (device local time)".format(it / 60, it % 60) } ?: "a new reminder time"
        HabitField.WINDOW -> action.value.split(',').mapNotNull(String::toIntOrNull).takeIf { it.size == 2 }?.let { AssistantFeatureLanguage.timeText(it[0], it[1]) + " (device local time)" } ?: "a new reminder window"
        HabitField.INTERVAL -> "every ${action.value} minutes"
        HabitField.TARGET -> "${action.value} per day"
        HabitField.REMINDER -> if (action.value == "true") "On" else "Off"
        HabitField.PAUSED -> if (action.value == "true") "paused" else "active"
        HabitField.ARCHIVED -> if (action.value == "true") "archived" else "active"
        HabitField.TITLE -> action.value
    }
    fun schedule(habit: Habit) = if (habit.type == HabitType.INTERVAL_WINDOW)
        "Every ${habit.totalIntervalMinutes} minutes, ${AssistantFeatureLanguage.timeText(habit.windowStartHour * 60 + habit.windowStartMinute, habit.windowEndHour * 60 + habit.windowEndMinute)}"
    else "Once daily at ${"%02d:%02d".format(habit.fixedReminderHour, habit.fixedReminderMinute)}"

    suspend fun information(app: FocusApplication, state: AssistantSession, input: String): String? {
        if (state.topic != "habits") return null
        val text = input.lowercase().trimEnd('.', '!', '?')
        if (text !in setOf("what is its goal", "what's its goal", "what is its target", "what is its schedule", "what's its schedule", "when does it remind me", "when will it remind me", "what is its interval", "what's its interval", "are its reminders on", "is its reminder on", "show that habit")) return null
        val habit = state.habitId?.let { app.database.habitDao().getHabitById(it) }
            ?: return "Which habit do you mean? Say ‘show habit’ followed by its name."
        return "**${habit.title}**\nSaved daily target: ${habit.targetPerDay}. Reminders ${if (habit.isReminderEnabled) "on" else "off"}.\nSaved schedule: ${schedule(habit)} (device local time).${if (habit.isArchived) " This habit is archived." else if (habit.isPaused) " This habit is paused; reminders are suspended." else ""}\nThis is the configured schedule; Android permissions and background restrictions affect delivery." +
            if (state.expected == "confirm") "\nYour proposed edit is still waiting for confirmation; these are the currently saved values." else ""
    }

    suspend fun revise(app: FocusApplication, state: AssistantSession, input: String): FeatureTurn? {
        if (state.topic != "habits" || state.expected != "confirm") return null
        val pending = state.pending.singleOrNull() as? AssistantAction.EditHabit ?: return null
        val match = Regex("(?i)^(?:actually[, ]+|instead[, ]+|no[, ]+|make (?:it|that) )(.+)$").matchEntire(input.trimEnd('.', '!', '?')) ?: return null
        val valueText = match.groupValues[1]
        if (AssistantHabitEditing.request(valueText, state) != null || com.focusbyrj.app.util.OfflineNluEngine.isQuestion(valueText) ||
            com.focusbyrj.app.util.OfflineNluEngine.isExplicitCreation(valueText) || AssistantFeatureLanguage.isRequest(valueText)) return null
        val actual = app.database.habitDao().getHabitById(pending.id)
        if (actual != pending.expected) { state.clearPending(); return FeatureTurn.Reply("That habit changed while we were talking. Check it again before requesting another edit.") }
        val value = AssistantHabitEditing.value(pending.field, valueText)
        if (value == null) {
            state.clearPending(); state.expected = "habit_edit_value"; state.pending = listOf(pending.copy(value = ""))
            return FeatureTurn.Reply("For **${pending.expected.title}**: ${AssistantHabitEditing.hint(pending.field)}")
        }
        // Rendering goes through the same preview and executor as an explicit habit edit.
        return FeatureTurn.Preview(listOf(pending.copy(value = value)), "Revised ${pending.field.name.lowercase().replace('_', ' ')} for habit **${pending.expected.title}** to **$value**? Nothing has changed yet.")
    }
}
