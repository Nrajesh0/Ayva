package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.util.AyvaTalkEngine
import org.json.JSONObject

/** Shared conversational style. Review controls, native results and required questions stay intact. */
internal object ConversationVoice {
    fun questions(context: Context, state: AssistantSession) = state.featureState?.optJSONObject("voice")?.let { if (it.has("questions")) it.getBoolean("questions") else null }
        ?: context.getSharedPreferences("ayva_personalization", 0).getBoolean("questions", true)
    fun concise(context: Context, state: AssistantSession) = state.featureState?.optJSONObject("voice")?.let { if (it.has("concise")) it.getBoolean("concise") else null } ?: AssistantReplyStyle.concise(context)
    fun preference(context: Context, state: AssistantSession, input: String): AyvaTalkEngine.TalkResponse? {
        val text = ConversationLanguage.normalize(input).lowercase().trimEnd('.', '!', '?')
        val saved = text.startsWith("always "); val body = text.removePrefix("always ").removePrefix("for this chat ")
        val pair = when (body) {
            "ask fewer questions", "don't ask questions", "no questions" -> "questions" to false
            "ask me questions", "be more curious" -> "questions" to true
            "be concise", "keep it short", "short answers" -> if (saved || text.startsWith("for this chat ")) "concise" to true else return null
            "include more detail", "detailed answers" -> if (saved || text.startsWith("for this chat ")) "concise" to false else return null
            else -> return null
        }
        val features = state.featureState ?: JSONObject().also { state.featureState = it }
        val voice = features.optJSONObject("voice") ?: JSONObject()
        voice.put(pair.first, pair.second); features.put("voice", voice)
        if (saved) check(context.getSharedPreferences("ayva_personalization", 0).edit().putBoolean(pair.first, pair.second).commit())
        return AyvaTalkEngine.TalkResponse("I'll use ${if (pair.first == "questions") if (pair.second) "optional follow-up questions" else "fewer optional questions" else if (pair.second) "shorter replies" else "more detail"} ${if (saved) "as your saved default" else "for this conversation"}. Required task details still need an answer.", topicId = "conversation")
    }
    fun finish(context: Context, state: AssistantSession, response: AyvaTalkEngine.TalkResponse): AyvaTalkEngine.TalkResponse {
        if (response.topicId !in setOf("conversation", "plan_workspace") || response.actions.isNotEmpty() || state.expected != null) return response
        // Only authored scaffolding is shortened; never truncate facts, user text, reviews or action payloads.
        if (AyvaPersonality.tone(context, state) != "direct" && !concise(context, state)) return response
        val prefixes = listOf("Let's keep that in view. ", "Let's work it out together. ", "Got it — ")
        val prefix = prefixes.firstOrNull { response.formattedText.startsWith(it) } ?: return response
        return response.copy(formattedText = response.formattedText.removePrefix(prefix))
    }
}
