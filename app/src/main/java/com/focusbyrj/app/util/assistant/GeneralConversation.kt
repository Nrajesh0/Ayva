package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.util.AyvaTalkEngine
import org.json.JSONArray
import org.json.JSONObject

/** Bounded semantic conversation; does not obtain repositories or execute actions. */
internal object GeneralConversation {
    private const val KEY = "generalDialogue"
    private fun root(state: AssistantSession) = state.featureState ?: JSONObject().also { state.featureState = it }
    private fun current(state: AssistantSession): JSONObject? = state.featureState?.optJSONObject(KEY)
        ?.takeIf { it.optInt("version") == 1 && AssistantContext.live(it.optLong("at")) }
    private fun strings(j: JSONObject, key: String) = j.optJSONArray(key)?.let { a -> (0 until minOf(a.length(), 20)).map { a.optString(it).take(500) } }.orEmpty()
    fun archive(state: AssistantSession): JSONObject {
        require(state.topic == "conversation" && state.expected == null && state.featureState?.optString("activeDialogue") == "general") { "Resume the conversation before saving it" }
        val frame = current(state) ?: throw IllegalArgumentException("Start a conversation before saving it")
        return AssistantDiscussion.sanitizeArchive(JSONObject().put("topic", frame.getString("topic"))
            .put("notes", JSONArray(strings(frame, "notes").takeLast(10).map { JSONObject().put("key", "context").put("text", it.take(400)) }))
            .put("options", JSONArray()).put("question", "").put("stance", if (frame.optString("stance") == "listen") "listen" else "think"))
    }
    fun handoff(state: AssistantSession, input: String): AssistantDiscussion.Seed? {
        if (!AssistantDiscussion.requestsHandoff(input) && input != "/plan" || state.topic != "conversation" || state.expected != null || state.featureState?.optString("activeDialogue") != "general") return null
        val frame = current(state) ?: return null
        return AssistantDiscussion.Seed(frame.getString("topic"), strings(frame, "notes").takeLast(10).mapIndexed { i, value -> "discussion ${i + 1}" to value }.toMap())
    }
    private val followups = setOf("why", "why is that", "how", "tell me more", "more", "more detail", "simpler", "shorter", "give me an example", "for example", "i disagree", "that's not what i meant", "what did we decide", "what were we discussing")
    fun answer(context: Context, state: AssistantSession, input: String, fallback: Boolean = false): AyvaTalkEngine.TalkResponse? {
        val text = ConversationLanguage.normalize(input).trimEnd('.', '!', '?')
        val lower = text.lowercase()
        val memory = PlanningLibrary(context)
        // Explicit memory is inspectable and backed up with the existing encrypted library.
        Regex("(?i)^remember that (.{1,500})$").matchEntire(text)?.let { m ->
            val value = m.groupValues[1]
            val separator = Regex("(?i)\\s+(?:means|is)\\s+").find(value)
            if (separator == null) return say(state, "memory", "Give that memory a name, for example ‘remember that my project means the garden redesign’. It will be saved only when you explicitly ask.")
            val name = value.substring(0, separator.range.first).trim()
            memory.rememberFact(name, value.substring(separator.range.last + 1).trim())
            return say(state, "memory", "Remembered ‘$name’ as information you supplied. You can ask what I remember or tell me to forget it.")
        }
        Regex("(?i)^correct remembered (.{1,80}?) to (.{1,500})$").matchEntire(text)?.let { m ->
            memory.correctFact(m.groupValues[1], m.groupValues[2])
            return say(state, "memory", "Updated your saved ‘${m.groupValues[1]}’ to ‘${m.groupValues[2]}’. That corrects the memory; existing plans and tasks keep their own details.")
        }
        if (lower in setOf("show conversation memories", "what facts do you remember")) return say(state, "memory", memory.describeFacts())
        Regex("(?i)^forget (?:the memory |that )?(.+)$").matchEntire(text)?.let { m ->
            if (memory.forgetFact(m.groupValues[1])) return say(state, "memory", "Forgot that saved fact and its stored definition.")
        }
        if (lower == "forget conversation memories") { memory.forgetFacts(); root(state).remove(KEY); root(state).remove("generalTopics"); return response("Forgot explicitly saved conversation facts and current general-chat context.") }
        Regex("(?i)^what (?:does (.+) mean|do you remember about (.+))$").matchEntire(text)?.let { m ->
            val name = m.groupValues[1].ifBlank { m.groupValues[2] }
            memory.fact(name)?.let { return say(state, name, "You told me: $it") }
        }
        if (state.expected != null) return null
        ConversationReasoning.general(context, state, text)?.let { return it }
        ConversationStories.answer(state, text)?.let { return response(it) }
        if (!ConversationRouting.permits(ConversationRouting.Owner.GENERAL, text, state)) return null
        val previous = current(state)
        val active = previous != null && state.topic == "conversation" && state.featureState?.optString("activeDialogue") == "general"
        Regex("(?i)^back to (.+)$").matchEntire(text)?.let { m ->
            val topics = state.featureState?.optJSONArray("generalTopics") ?: JSONArray()
            val matches = (0 until topics.length()).map { topics.getJSONObject(it) }.filter { it.optString("topic").contains(m.groupValues[1], true) }
            if (matches.size == 1) return say(state, matches.single().optString("topic"), recap(matches.single()), matches.single())
        }
        if (lower in setOf("back to our chat", "back to that topic", "resume our chat")) {
            if (previous == null && ChatModeStore.get(context) != ChatMode.PLAN) {
                GeneralDialogue.recalled(root(state).optJSONObject(KEY))?.takeIf { it.has("dialogueV2") }?.let {
                    generalTurn(context, state, "what do we have so far", it)?.let { response -> return response }
                }
            }
            val topic = previous?.optString("topic") ?: return response("Which subject would you like to return to?")
            return say(state, topic, recap(previous), previous)
        }
        if (lower in setOf("forget this conversation", "forget this topic")) {
            listOf("focusedChat", "focusedHistory", "pausedSocial", "pausedGeneral", "social", "experienceSeen", "seenJokes", "seenRiddles", "dislikedJokes", "jokeCategory", "experienceDiagnostic", "unsupportedCount").forEach { root(state).remove(it) }
            root(state).put("activeDialogue", "")
            root(state).remove(KEY); root(state).remove("generalTopics"); return response("Forgot this general-chat topic. Explicitly saved memories and plans remain available.") }
        if (lower in setOf("just listen", "no advice", "i don't want advice", "ask fewer questions", "don't ask questions")) {
            // Existing structured discussion retains its listening owner.
            if ((!active || AssistantDiscussion.hasContext(state)) && lower !in setOf("ask fewer questions", "don't ask questions")) return null
            val frame = previous ?: frame("what's on your mind")
            frame.put("stance", "listen").put("questions", false)
            frame.optJSONObject("dialogueV2")?.remove("answer")
            return say(state, frame.optString("topic"), "I'll follow your lead and leave advice aside.", frame)
        }
        if (lower in setOf("give me advice", "help me think", "ask me questions", "be more curious")) {
            if (lower == "give me advice" && previous?.optJSONObject("dialogueV2") != null) {
                generalTurn(context, state, text, previous)?.let { return it }
            }
            val frame = previous ?: frame("what's on your mind")
            frame.put("stance", "explore").put("questions", true)
            frame.optJSONObject("dialogueV2")?.remove("answer")
            return say(state, frame.optString("topic"), "We can work through it. What part would be most useful to untangle?", frame)
        }
        // This engine cannot see action capabilities. Keep shared planner helpers and the
        // legacy archive/handoff representation intact; only general conversation opts in.
        if (ChatModeStore.get(context) != ChatMode.PLAN && state.topic != "planning" && !AssistantDiscussion.hasContext(state) && !AssistantSocial.hasInterestOpening(lower)) {
            val conversational = state.topic == "conversation" && state.featureState?.optString("activeDialogue") == "general"
            val recall = if (conversational) GeneralDialogue.recalled(root(state).optJSONObject(KEY)) else null
            generalTurn(context, state, text, recall)?.let { return it }
        }
        if (lower in setOf("what did we decide", "what were we discussing") && active) return say(state, previous!!.optString("topic"), recap(previous), previous)
        val concept = ConversationKnowledge.match(text)
        val asksExplanation = Regex("(?i)^(?:what|why|how|explain|tell me about|can you explain|could you explain)\\b").containsMatchIn(text)
        if (concept != null && asksExplanation || active && lower in followups && previous!!.optString("concept").isNotBlank()) {
            val id = concept?.id ?: previous!!.optString("concept")
            val found = concept ?: ConversationKnowledge.find(id) ?: return null
            val frame = if (active) JSONObject(previous!!.toString()) else frame(found.aliases.first())
            frame.put("concept", id)
            var answer = ConversationKnowledge.explain(text, id)!!
            if (lower == "i disagree") answer = "Which part doesn't fit your experience? The explanation is general; we can examine your example without treating it as a universal rule."
            return say(state, found.aliases.first(), answer, frame)
        }
        if (active && lower in setOf("how is that different", "what's the difference", "compare them")) {
            val ids = strings(previous!!, "concepts").distinct().takeLast(2)
            val pair = ids.mapNotNull(ConversationKnowledge::find)
            if (pair.size == 2) return say(state, previous.optString("topic"), "${pair[0].simple} ${pair[1].simple} Those are different operations; they can be used together.", previous)
            return say(state, previous.optString("topic"), "Which two ideas should I compare?", previous)
        }
        // Do not steal existing typed comparison, game, dictionary, task or pending-question owners.
        if (AssistantDiscussion.hasContext(state)) return null
        if (lower in setOf("hi", "hello", "hey", "thanks", "thank you", "bye", "tell me a joke", "tell me a story", "give me a riddle", "play a game")) return null
        val accomplishment = Regex("(?i)^i (?:(?:finally|just|have) )?(?:finished|completed|passed|got|made|managed) (.+)$").matchEntire(text)
        if (accomplishment != null) {
            val subject = accomplishment.groupValues[1].take(200)
            return say(state, subject, "That sounds like a meaningful moment — $subject. ${if (AyvaPersonality.tone(context, state) == "direct") "Well done." else "What part are you happiest with?"}", note = text)
        }
        val explicitTopic = Regex("(?i)^(?:let'?s (?:chat|talk) about|can we (?:chat|talk) about|tell me about) (.+)$").matchEntire(text)
        if (explicitTopic != null && !Regex("(?i)\\b(?:settings?|permissions?|backup|notifications?|dictionary|habits?|routines?)\\b").containsMatchIn(text)) {
            val topic = explicitTopic.groupValues[1].take(300)
            if (topic.lowercase() in setOf("reading", "books", "music", "cooking", "films", "coding", "walking")) return null
            val known = ConversationKnowledge.match(topic)
            return say(state, topic, known?.explanation ?: "We can talk about $topic. What interests you about it?", note = text)
        }
        val sharing = Regex("(?i)^(?:i(?:'m| am| feel| have| had| like| love| enjoy| think| believe| prefer| wish| don't| can't| have been|'ve been)|my (?:day|friend|family|work|week)|because|but|actually|it (?:was|is)|that (?:was|is))\\b").containsMatchIn(text)
        if (sharing && (fallback || active) && (active || !Regex("(?i)\\b(?:task|habit|routine|setting|remind|plan|exam|trip)\\b").containsMatchIn(text))) {
            val frame = if (active) previous!! else frame(text.take(180))
            if (lower.startsWith("actually ") || lower.startsWith("i meant ")) {
                val notes = strings(frame, "notes").dropLast(1) + text
                frame.put("notes", JSONArray(notes.takeLast(10)))
                return say(state, frame.optString("topic"), "Thanks for clarifying. I'll use ‘${text.removePrefix("actually ").take(220)}’ for this discussion.", frame)
            }
            val listening = frame.optString("stance") == "listen"
            val feeling = Regex("(?i)\\b(tired|exhausted|lonely|overwhelmed|sad|frustrated|anxious|stressed)\\b").find(text)?.value
            val body = when {
                listening -> "I'm following. ${text.take(220)}${if (text.endsWith('.')) "" else "."}"
                feeling != null -> "You said you're $feeling. ${if ((frame.optBoolean("questions", true) && ConversationVoice.questions(context, state))) "Would it help to talk about what's been weighing on you, or would you prefer I just listen?" else "We can leave solutions aside."}"
                lower.startsWith("because") -> "That helps explain what matters to you here. ${text.removePrefix("because").trim().replaceFirstChar { it.uppercase() }}."
                lower.startsWith("but") -> "That's a complication worth keeping in view: ${text.removePrefix("but").trim()}."
                else -> {
                    val n = strings(frame, "notes").size % 3
                    val prompt = listOf("What stands out most to you about that?", "Is that how you expected it to go?", "What part would you like to explore?")[n]
                    "${text.take(220)} — ${if ((frame.optBoolean("questions", true) && ConversationVoice.questions(context, state))) prompt else "I'm following your point."}"
                }
            }
            return say(state, frame.optString("topic"), body, frame, text)
        }
        if (active && lower in followups) return say(state, previous!!.optString("topic"), when (lower) {
            "i disagree" -> "Tell me which part you see differently. I'll keep your view separate from the assumptions we've been using."
            "that's not what i meant" -> "What should I correct? I'll keep the rest of our discussion."
            "simpler", "shorter" -> strings(previous, "notes").lastOrNull()?.let { "Your main point was: $it" } ?: "We're discussing ${previous.optString("topic")}."
            else -> "We were discussing ${previous.optString("topic")}. ${strings(previous, "notes").lastOrNull()?.let { "You said: $it. " }.orEmpty()}Which part would you like me to explain?"
        }, previous)
        if (fallback && active && previous!!.optString("stance") == "listen")
            return say(state, previous.optString("topic"), "I hear you — ${text.take(220)}. We can leave advice aside.", previous, text)
        if (fallback && active && !Regex("(?i)\\b(?:tasks?|habits?|routine|settings?|permission|vocabulary|word|focus|drill)\\b").containsMatchIn(text))
            return say(state, previous!!.optString("topic"), "How does ‘${text.take(180)}’ connect to ${previous.optString("topic")}? I don't want to guess the connection.", previous)
        return null
    }
    private fun generalTurn(context: Context, state: AssistantSession, text: String, prior: JSONObject?): AyvaTalkEngine.TalkResponse? {
        if (state.expected != null || ChatModeStore.get(context) == ChatMode.PLAN || state.topic == "planning") return null
        val style = GeneralDialogue.Style(
            ConversationVoice.questions(context, state) && prior?.optBoolean("questions", true) != false,
            prior?.optString("stance") == "listen", AyvaPersonality.tone(context, state) == "playful")
        val direct = GeneralDialogue.answer(text, prior, style)
        val interpreted = if (direct == null || direct.rule == "unknown") {
            NativeGeneralDialogue.interpret(context, text)?.let { meaning ->
                GeneralDialogue.answer(meaning, prior, style, sourceText = text)
            }
        } else null
        val turn = interpreted ?: direct ?: return null
        val response = say(state, turn.topic, turn.text, turn.frame)
        root(state).put("generalDialogueRule", turn.rule)
        return response
    }
    private fun frame(topic: String) = JSONObject().put("version", 1).put("topic", topic.take(300)).put("notes", JSONArray())
        .put("stance", "explore").put("questions", true)
    private fun recap(j: JSONObject) = "We were discussing ${j.optString("topic")}." + strings(j, "notes").takeLast(3).joinToString("", prefix = "\n") { "• $it\n" }
    private fun say(state: AssistantSession, topic: String, text: String, prior: JSONObject? = null, note: String? = null): AyvaTalkEngine.TalkResponse {
        if (state.expected != null) return response(text + "\n\n" + AssistantReplyStyle.expectedHint(state))
        val previous = current(state)
        if (previous != null && !previous.optString("topic").equals(topic, true)) {
            val archive = root(state).optJSONArray("generalTopics") ?: JSONArray()
            val kept = (0 until archive.length()).map { archive.getJSONObject(it) }.filterNot { it.optString("topic") == previous.optString("topic") }
            root(state).put("generalTopics", JSONArray((kept + JSONObject(previous.toString())).takeLast(3)))
        }
        val j = prior ?: frame(topic)
        j.put("topic", topic.take(300)).put("at", System.currentTimeMillis())
        if (note != null) j.put("notes", JSONArray((strings(j, "notes") + note.take(500)).takeLast(10)))
        val concept = j.optString("concept")
        if (concept.isNotBlank()) j.put("concepts", JSONArray((strings(j, "concepts") + concept).takeLast(6)))
        val recent = strings(j, "replies")
        val body = if (text in recent && note != null) "I'm keeping that detail in view. ${note.take(220)}" else text
        j.put("replies", JSONArray((recent + body.take(500)).takeLast(6)))
        state.clearPending(); state.switchTopic("conversation"); root(state).put(KEY, j).put("activeDialogue", "general")
        return response(body)
    }
    private fun response(text: String) = AyvaTalkEngine.TalkResponse(text, topicId = "conversation")
}
