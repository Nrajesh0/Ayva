package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.util.SmartDateParser

/** Questions about the conversation are reads, not new intents or answers to a data-entry slot. */
internal object AssistantConversationSupport {
    suspend fun answer(context: Context, app: FocusApplication, state: AssistantSession, input: String): String? {
        val text = input.lowercase().trimEnd('.', '!', '?')
        if (text in setOf("no that's not what i meant", "that's wrong", "you misunderstood", "not what i meant", "that isn't what i meant")) {
            val guidance = when {
                state.topic?.startsWith("setting:") == true -> "Name the setting and the value you want, or ask what values it supports."
                state.topic == "habits" -> "Name the habit and what should change: its goal, reminder time, interval, reminder toggle or name."
                state.topic == "routines" -> "Name the routine and what should change: its days, time window, apps, mode or name."
                state.topic == "blocking" -> "Name the apps you want locked or unlocked, and any exceptions."
                state.topic == "tasks" || state.topic == "planning" -> "Tell me which task and what should change: its title, time, priority, details or repeat schedule."
                else -> "Tell me the feature or saved item you meant, and what should be different."
            }
            state.clearPending(); state.featureState?.remove("routineDraft"); state.featureState?.remove("appSelection")
            return "Let's correct it. $guidance The previous preview is cancelled." + if (state.undoId != null) " Say ‘undo’ to reverse my last reversible saved change." else " Saved items are unchanged."
        }
        if (text in setOf("repeat that", "say that again", "repeat your answer", "what did you say"))
            return state.lastReply ?: "There's no earlier reply in this session. What would you like help with?"
        val preview = text in setOf("what will change", "what would change", "what are you going to change", "show the preview", "show my pending changes", "what am i confirming")
        val recap = text in setOf("where were we", "what are we doing", "what are we working on", "what is pending", "what's pending", "what are you waiting for")
        val explain = text in setOf("why", "why do you need that", "why are you asking", "why do i need to confirm", "what do you need from me", "what should i say", "what are my options", "what now", "continue", "go on")
        if (!preview && !recap && !(explain && state.expected != null)) return null
        if (state.expected == "confirm" && state.pending.isNotEmpty()) {
            val lines = state.pending.take(12).map { action -> describe(context, app, action) }
            return "These are proposed changes; nothing in this preview has been applied:\n" + lines.joinToString("\n") { "• $it" } +
                (if (state.pending.size > 12) "\nShowing 12 of ${state.pending.size} actions; review the original preview for the full list." else "") +
                "\nConfirm to apply the preview, revise it, or cancel. Confirmation rechecks the saved items and any active protections."
        }
        if (state.choices.isNotEmpty()) return "I need you to choose one of the displayed options. Say its number or exact label; I won't guess which item you mean."
        if (state.expected != null) {
            val reason = when (state.expected) {
                "task_title" -> "I have an unfinished reminder, but still need its title."
                "task_date", "correction_date" -> "I need an unambiguous day and time for the task change."
                "habit_title" -> "I still need a name for the new habit."
                "habit_edit_value" -> state.pending.filterIsInstance<AssistantAction.EditHabit>().singleOrNull()?.let { "We're editing habit **${it.expected.title}**; I still need its ${it.field.name.lowercase().replace('_', ' ')}." }.orEmpty()
                "setting_value" -> state.pending.filterIsInstance<AssistantAction.SetPreference>().singleOrNull()?.let { edit ->
                    AssistantPreferences.specs.find { it.key == edit.key }?.let { "We're changing **${it.title}**. Current: ${AssistantPreferences.display(it, AssistantPreferences.read(context, it))}. ${AssistantAppKnowledge.hint(it)}" }
                }.orEmpty()
                "feature_value" -> "We're filling in a routine; I won't save it until the required fields are complete and you confirm."
                "plan_budget" -> "I need your available time to build a short plan from saved tasks. This doesn't move their deadlines."
                else -> "We're filling in a task change. I still need a specific value."
            }
            return "$reason\n${AssistantReplyStyle.expectedHint(state)}\nYou can answer, cancel, or ask about another feature and return later."
        }
        if (preview) return "There is no pending change to confirm. Tell me what you want to change first."
        val focus = when {
            state.topic?.startsWith("setting:") == true -> AssistantPreferences.specs.find { it.key == state.topic?.removePrefix("setting:") }?.let { "We're discussing **${it.title}**, currently ${AssistantPreferences.display(it, AssistantPreferences.read(context, it))}. You can ask how it works or request a change." }
            state.topic == "tasks" && state.taskId != null -> app.database.taskDao().getTaskById(state.taskId!!)?.takeUnless { it.isTrashed || it.deletedAt != null }?.let { "We're working on task **${it.title}**. You can change its time, title, priority, details or repeat schedule." }
            state.topic == "habits" && state.habitId != null -> app.database.habitDao().getHabitById(state.habitId!!)?.let { "We're discussing habit **${it.title}**. You can check its schedule, log progress or request a goal/reminder change." }
            state.topic == "routines" -> "We're discussing routines. Name a routine to view it, or say ‘edit routine’ followed by its name."
            state.topic == "blocking" -> "We're discussing manual app locks. You can show locked apps or request a lock/unlock selection with exceptions."
            else -> null
        }
        val unfinished = listOfNotNull(state.suspendedRequest).plus(state.olderQuestions).filter { System.currentTimeMillis() - it.optLong("savedAt") in 0..900_000L }
        val resume = unfinished.firstOrNull()?.optString("expected")?.let { when {
            it.startsWith("habit_") -> "back to the habit"
            it == "setting_value" -> "back to the setting"
            it == "feature_value" -> "back to the routine"
            it == "plan_budget" -> "back to planning"
            else -> "back to that reminder"
        } }
        return (focus ?: "There is no pending question or preview.") + (resume?.let { "\nThere is also an unfinished request. Say ‘$it’ to return to it; old confirmations are never restored." } ?: "")
    }

    internal suspend fun describe(context: Context, app: FocusApplication, action: AssistantAction): String {
        suspend fun tasks(ids: List<Long>) = ids.take(4).map { id -> app.database.taskDao().getTaskById(id)?.title ?: "unavailable task #$id" }.joinToString { "‘$it’" } + if (ids.size > 4) " and ${ids.size - 4} more" else ""
        return when (action) {
            is AssistantAction.SetPreference -> AssistantPreferences.specs.find { it.key == action.key }?.let { "${it.title}: current ${AssistantPreferences.display(it, AssistantPreferences.read(context, it))} → proposed ${AssistantPreferences.display(it, action.value)}" } ?: "Change an app setting"
            is AssistantAction.CreateTask -> "Add task ‘${action.draft.title}’${action.draft.dueDate?.let { " · ${SmartDateParser.formatDueDate(it)}" }.orEmpty()}"
            is AssistantAction.EditTasks -> {
                val value = when (action.field) {
                    TaskField.DUE -> if (action.value == "none") "no due date" else action.value.toLongOrNull()?.let(SmartDateParser::formatDueDate) ?: action.value
                    TaskField.PRIORITY -> if (action.value == "true") "high priority" else "normal priority"
                    TaskField.PERSISTENT -> if (action.value == "true") "On" else "Off"
                    TaskField.RECURRENCE -> action.value.lowercase()
                    TaskField.REOPEN -> "pending"
                    else -> action.value
                }
                "Change ${action.field.name.lowercase().replace('_', ' ')} for ${tasks(action.ids)} to $value"
            }
            is AssistantAction.CompleteTasks -> "Complete ${tasks(action.ids)}"
            is AssistantAction.TrashTasks -> "Move ${tasks(action.ids)} to Trash"
            is AssistantAction.CreateHabit -> "Create habit ‘${action.title}’, target ${action.target} per day"
            is AssistantAction.LogHabit -> "Log ${action.count} for habit ‘${app.database.habitDao().getHabitById(action.id)?.title ?: "no longer available"}’"
            is AssistantAction.EditHabit -> "Change ${action.field.name.lowercase().replace('_', ' ')} for habit ‘${action.expected.title}’ to ${AssistantHabitConversation.proposedValue(action)}"
            is AssistantAction.SaveRoutine -> "${if (action.expected == null) "Create" else "Edit"} routine ‘${action.draft.name}’ · ${AssistantRoutineConversation.summary(action.draft)}"
            is AssistantAction.SetRoutine -> "${if (action.enabled) "Enable" else "Disable"} routine ‘${app.database.scheduleDao().getScheduleById(action.id)?.name ?: "#${action.id}"}’ using its saved schedule"
            is AssistantAction.SetAppRule -> "${if (action.draft.isRestricted) "Lock" else "Unlock"} ${action.draft.appName} (${action.draft.packageName})"
            is AssistantAction.SetAppRules -> "Update ${action.rules.size} manual app rules: ${action.rules.count { it.draft.isRestricted }} locked, ${action.rules.count { !it.draft.isRestricted }} unlocked. Targets: " + action.rules.take(8).joinToString { "${it.draft.appName} (${it.draft.packageName})" } + if (action.rules.size > 8) "; see the original preview for all targets" else ""
            is AssistantAction.BookmarkWord -> "${if (action.bookmarked) "Bookmark" else "Unbookmark"} saved ${action.type} #${action.id}"
            is AssistantAction.StartFocus -> "Start a ${action.minutes}-minute focus session"
            AssistantAction.StopFocus -> "Stop the current focus countdown"
        }
    }
}
