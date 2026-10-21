package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.util.OfflineNluEngine

/** Read-only candidates. A route selects an owner; it never supplies execution authority. */
internal object ConversationRouting {
    enum class Owner { NATIVE, DICTIONARY, KNOWLEDGE, DISCUSSION, CREATIVE, GENERAL, SOCIAL, REASONING }
    data class Candidate(val owner: Owner, val priority: Int, val evidence: String)
    private val action = Regex("(?i)^(?:/|add |create |remind |delete |remove |save |apply |complete |mark |rename |move |reschedule |start |stop |enable |disable |lock |unlock |log |set |pause |resume |archive |restore |reopen |block |unblock |show my |list my )")
    private val appQuestion = Regex("(?i)^(?:show|list|what are|what is|how (?:many|much)|do i have) (?:my |the )?(?:pending |overdue |today'?s |active )?(?:tasks?|habits?|routines?|focus sessions?|settings?)\\b")
    private val storyStart = Regex("(?i)^(?:tell|write|make)(?: me)? (?:a |a short |a tiny )?story(?: about .+)?$")
    private val discussionStart = Regex("(?i)^(?:options (?:are|:)|i(?:'m| am) (?:choosing|deciding) between|let'?s (?:talk|think) through|help me (?:think|decide))\\b")
    private val topicStart = Regex("(?i)^(?:let'?s (?:chat|talk) about|can we (?:chat|talk) about|tell me about) .+")
    fun nativeRequest(input: String): Boolean {
        val text = PlanLanguage.normalize(input)
        if (storyStart.matches(text.trimEnd('.', '!', '?')) || text.equals("give me an example", true)) return false
        return action.containsMatchIn(text) || appQuestion.containsMatchIn(text) || (Regex("(?i)^(?:show|list|how|what|why|is|are|can|do|set|change|turn|enable|disable|open|check|log|start|stop|pause|resume)\\b").containsMatchIn(text) && (AssistantFeatureLanguage.isRequest(text) || AssistantPreferences.find(text) != null)) || OfflineNluEngine.isExplicitCreation(text)
    }
    fun candidates(input: String, state: AssistantSession): List<Candidate> {
        val text = PlanLanguage.normalize(input).trimEnd('.', '!', '?'); val lower = text.lowercase()
        val candidates = mutableListOf<Candidate>()
        if (nativeRequest(text)) candidates += Candidate(Owner.NATIVE, 110, "explicit app request")
        if (AssistantDictionary.isLookup(text)) candidates += Candidate(Owner.DICTIONARY, 100, "explicit lookup")
        if (ConversationKnowledge.match(text) != null && Regex("(?i)^(?:what|why|how|explain|tell me about|can you explain|could you explain)\\b").containsMatchIn(text))
            candidates += Candidate(Owner.KNOWLEDGE, 90, "supported concept explanation")
        if (ConversationReasoning.explicit(text)) candidates += Candidate(Owner.REASONING, 125, "explicit premises or conditional reasoning")
        val social = text in setOf("tell me a story", "tell me a short story", "short story", "tell me a joke", "make me laugh", "give me a riddle", "play a game", "play word chain", "word chain", "stop the game") ||
            state.topic == "conversation" && state.featureState?.optJSONObject("social")?.optString("family") in setOf("riddle", "chain", "story") && lower == "stop"
        if (social) candidates += Candidate(Owner.SOCIAL, 130, "explicit game or authored social request")
        if (discussionStart.containsMatchIn(text)) candidates += Candidate(Owner.DISCUSSION, 95, "new explicit discussion")
        if (storyStart.matches(text) && !social || lower in setOf("continue the story", "continue story", "shorter story", "stop the story") ||
            Regex("(?i)^(?:make the story |name the character )").containsMatchIn(text)) candidates += Candidate(Owner.CREATIVE, 120, "explicit creative request")
        if (text in setOf("just listen", "no advice", "i don't want advice") || Regex("(?i)^i (?:feel|had a|have been) ").containsMatchIn(text)) candidates += Candidate(Owner.GENERAL, 85, "explicit conversational stance or sharing")
        if (topicStart.matches(text)) candidates += Candidate(Owner.GENERAL, 80, "new explicit topic")
        if (state.expected == null && state.topic == "conversation") {
            val owner = when (state.featureState?.optString("activeDialogue")) {
                "reasoning" -> Owner.REASONING; "general" -> Owner.GENERAL; "creative" -> Owner.CREATIVE; "discussion" -> Owner.DISCUSSION; else -> if (state.featureState?.optJSONObject("social")?.optString("family") in setOf("riddle", "riddle_done", "joke", "story", "chain", "interest", "interest_done")) Owner.SOCIAL else Owner.GENERAL
            }
            candidates += Candidate(owner, 20, "current conversation continuation")
        }
        return candidates.sortedByDescending { it.priority }
    }
    fun winner(input: String, state: AssistantSession): Owner? = candidates(input, state).firstOrNull()?.owner
    fun permits(owner: Owner, input: String, state: AssistantSession): Boolean {
        val winner = winner(input, state)
        return winner == null || winner == owner || owner == Owner.GENERAL && winner == Owner.KNOWLEDGE
    }
}
