package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.util.AyvaTalkEngine
import com.focusbyrj.app.util.TalkAction
import org.json.JSONObject
import java.util.UUID

/** Explicit opt-in, in-chat suggestions only. Receipts contain timestamps, never task text. */
internal object AssistantInitiative {
    private fun prefs(context: Context) = context.getSharedPreferences("ayva_suggestion_receipts", 0)
    fun dismiss(context: Context, state: AssistantSession, input: String): AyvaTalkEngine.TalkResponse? {
        val active = state.featureState?.optJSONObject("suggestion") ?: return null
        val token = active.optString("token")
        val typed = input.lowercase().trimEnd('.', '!', '?') in setOf("not now", "no suggestions right now") && state.topic == "conversation"
        if (input != "/ayva suggestion dismiss $token" && !typed) return null
        if (!AssistantContext.live(active.optLong("at"))) return AyvaTalkEngine.TalkResponse("That suggestion expired.")
        check(prefs(context).edit().putLong("after", System.currentTimeMillis() + 86_400_000L).commit())
        state.featureState?.remove("suggestion")
        return AyvaTalkEngine.TalkResponse("Okay. I'll leave optional task suggestions quiet for the next day. We can still chat.", topicId = "conversation")
    }
    suspend fun attach(context: Context, app: FocusApplication, state: AssistantSession, input: String, response: AyvaTalkEngine.TalkResponse): AyvaTalkEngine.TalkResponse {
        if (!AssistantPersonalization.suggestions(context) || input.lowercase().trimEnd('.', '!', '?') !in setOf("hi", "hello", "hey", "good morning", "good evening")) return response
        val now = System.currentTimeMillis()
        if (now < prefs(context).getLong("after", 0)) return response
        val task = app.database.taskDao().getAllActiveTasksList().filter { !it.isCompleted && !it.isTrashed && it.deletedAt == null && (it.isPriority || it.dueDate?.let { due -> due < now } == true) }
            .sortedWith(compareByDescending<com.focusbyrj.app.data.Task> { it.isPriority }.thenBy { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.id }).firstOrNull() ?: return response
        check(prefs(context).edit().putLong("after", now + 1_800_000L).commit())
        if (state.featureState == null) state.featureState = JSONObject()
        val token = UUID.randomUUID().toString()
        state.featureState!!.put("suggestion", JSONObject().put("token", token).put("at", now))
        val actions = response.actions + listOf(TalkAction.AskQuery("show my tasks", "Show tasks"), TalkAction.AskQuery("/ayva suggestion dismiss $token", "Not now"))
        val why = if (task.isPriority) "you marked it as priority" else "its saved due date has passed"
        return response.copy(formattedText = response.formattedText + "\n\nOptional: **${task.title}** is available to work on because $why. This suggestion hasn't changed it.",
            actions = actions, jsonPayload = AyvaTalkEngine.serializeActionsJson("conversation", actions))
    }
}
