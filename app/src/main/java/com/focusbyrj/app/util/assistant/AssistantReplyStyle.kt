package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.util.AyvaTalkEngine
import com.focusbyrj.app.util.TalkAction

/** Format supported responses without truncating results, titles or confirmation details. */
object AssistantReplyStyle {
    private fun prefs(context: Context) = context.getSharedPreferences("ayva_personalization", Context.MODE_PRIVATE)
    fun concise(context: Context) = prefs(context).getBoolean("concise", false)
    fun emoji(context: Context) = prefs(context).getBoolean("emoji", true)

    fun preference(context: Context, text: String): String? {
        val input = text.lowercase().trimEnd('.', '!', '?')
        val brief = when (input) {
            "be concise", "short answers", "less detail", "keep it short" -> true
            "more detail", "detailed answers", "explain more" -> false
            else -> null
        }
        if (brief != null) {
            check(prefs(context).edit().putBoolean("concise", brief).commit())
            return if (brief) "I'll keep my own replies brief." else "I'll include more context when it's useful."
        }
        val emojis = when (input) {
            "less emoji", "no emoji", "emoji off", "no emojis" -> false
            "use emoji", "use emojis", "emoji on", "more emoji" -> true
            else -> null
        }
        if (emojis != null) {
            check(prefs(context).edit().putBoolean("emoji", emojis).commit())
            return if (emojis) "I'll use emoji in my help and suggestions." else "I'll leave emoji out of my help and suggestions."
        }
        return null
    }

    fun expectedHint(state: AssistantSession) = when (state.expected) {
        "task_date", "correction_date" -> "What day and time should I use?"
        "task_title" -> "What should I remind you about?"
        "habit_title" -> "What habit would you like to track?"
        "feature_value" -> AssistantRoutineDialogue.hint(state.featureState?.optJSONObject("routineDraft")?.optString("field").orEmpty())
        "plan_budget" -> "How much time do you have? Use 1–480 whole minutes, for example ‘30 minutes’."
        "task_edit_field" -> "Which part should change—date, title, priority, details, repeat schedule or persistent reminders?"
        "task_new_title" -> "What should the new title be?"
        "task_priority" -> "Should it have high or normal priority?"
        "task_details" -> "What details should I save? Say ‘clear details’ to remove them, or quote literal text."
        "task_recurrence" -> "Should it repeat daily, weekly, monthly, yearly, or never?"
        "task_persistent" -> "Should persistent reminders be on or off?"
        "habit_edit_value" -> state.pending.filterIsInstance<AssistantAction.EditHabit>().singleOrNull()?.let { AssistantHabitEditing.hint(it.field) } ?: "What should change about that habit?"
        "choice", "legacy_choice" -> "Which numbered option do you mean?"
        "setting_value" -> "What value should I use for that setting?"
        "confirm" -> "The preview is still waiting for your confirmation."
        else -> "Tell me what you'd like to do."
    }

    fun acknowledgement(context: Context, state: AssistantSession): String {
        val opening = if (state.lastReply?.startsWith("You're welcome.") == true) "Anytime." else "You're welcome."
        return when {
            state.expected != null -> "$opening ${expectedHint(state)}"
            concise(context) -> opening
            state.taskId != null || state.selectedTaskIds.isNotEmpty() -> "$opening We can keep working on that task${if (state.selectedTaskIds.size > 1) " selection" else ""}."
            else -> opening
        }
    }

    fun help(context: Context): String {
        val icon = if (emoji(context)) "💬 " else ""
        val examples = if (concise(context)) listOf("Buy milk tomorrow at 6pm", "Move the first two to Friday", "Change it", "I have 30 minutes")
            else listOf("Buy milk tomorrow at 6pm", "Show tomorrow's tasks → Move the first two to Friday", "Change it → details, repeat schedule or persistent reminders", "Keep their original times → Except the second one", "Add habit reading / Log habit reading", "Change habit reading goal to 3 / Archive habit reading", "Change reminder interval → 30 minutes → yes", "I have 30 minutes → Skip that one → Why this order", "Remember that I prefer 15-minute focus sessions", "What do you remember about me / Forget my preferences", "Focus on it for 25 minutes", "Start a hard drill with 20 questions / Review my mistakes", "Create routine Study / Edit routine Study", "Set habit Water interval to 90 minutes", "Look up break the ice / Bookmark that idiom / Practice missed words", "Why is Reader blocked / Compare my screen time", "Check device health / Backup status / What can I claim", "Make tasks from my transcript → Drop transcript item 2", "Back to that reminder / Back to the habit / Back to planning / Undo")
        return "${icon}You can speak directly:\n" + examples.joinToString("\n") { "• $it" } +
            if (concise(context)) "\nI work offline. Notes stay outside chat." else "\n\nI'll ask when a target or value is unclear. Previews wait for confirmation. I work offline; notes and vault contents stay outside chat."
    }

    fun guard(reason: AssistantActionGuard) = if (reason == AssistantActionGuard.NEGATED)
        "Okay. I won't apply that request. Tell me what you'd like to do instead."
        else "Are you asking how that works, or do you want to make a change? Please state the action directly if you want me to apply it."

    fun unknown(context: Context?, input: String, state: AssistantSession = AssistantSession()): AyvaTalkEngine.TalkResponse {
        val lower = input.lowercase()
        val unfinished = state.suspendedRequest?.takeIf { System.currentTimeMillis() - it.optLong("savedAt") in 0..900_000L }?.optString("expected")
        val resume = when (unfinished) { "habit_title", "habit_edit_value" -> "back to the habit"; "setting_value" -> "back to the setting"; "feature_value" -> AssistantRoutineDialogue.hint(state.featureState?.optJSONObject("routineDraft")?.optString("field").orEmpty())
        "plan_budget" -> "back to planning"; null -> null; else -> "back to that reminder" }
        val repeated = state.recentInputs.lastOrNull()?.equals(input.take(500), true) == true
        val question = when {
            resume != null -> "Would you like to continue the unfinished ${if (unfinished == "setting_value") "setting question" else if (unfinished == "plan_budget") "planning question" else if (unfinished?.startsWith("habit_") == true) "habit question" else "task request"}, or start something else?"
            Regex("\\b(?:habits?)\\b").containsMatchIn(lower) -> "Would you like to add a habit, log progress, or view your habits?"
            Regex("\\b(?:tasks?|reminders?|todos?)\\b").containsMatchIn(lower) -> "Are you adding a task, changing one, or looking for your task list?"
            Regex("\\b(?:focus|timer|pomodoro)\\b").containsMatchIn(lower) -> "Would you like to start a focus timer or check the one that's running?"
            repeated -> "Try one specific request, such as ‘show my tasks’ or ‘buy milk tomorrow at 6pm’. What would you like to do?"
            else -> "I didn't catch what you'd like to do. Are you adding something, changing an item, or asking about Ayva?"
        }
        val icon = if (context != null && emoji(context)) "💬" else ""
        val actions = if (resume != null) listOf(TalkAction.AskQuery(resume, "Continue the request", icon), TalkAction.AskQuery("help", "Show examples", icon))
            else listOf(TalkAction.AskQuery("help", "Show examples", icon), TalkAction.AskQuery("/tasks", "My tasks", icon))
        return AyvaTalkEngine.TalkResponse(question, actions, "fallback", AyvaTalkEngine.serializeActionsJson("fallback", actions))
    }
}
