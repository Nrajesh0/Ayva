package com.focusbyrj.app.util.assistant

import org.json.JSONObject

/** Small authored story grammar; carries characters and choices rather than restarting each turn. */
internal object ConversationStories {
    fun answer(state: AssistantSession, input: String): String? {
        if (!ConversationRouting.permits(ConversationRouting.Owner.CREATIVE, input, state)) return null
        val text = PlanLanguage.normalize(input).trimEnd('.', '!', '?')
        val start = Regex("(?i)^(?:tell|write|make)(?: me)? (?:a |a short |a tiny )?story about (.{1,120})$").matchEntire(text)
        val features = state.featureState ?: JSONObject().also { state.featureState = it }
        val old = features.optJSONObject("creativeStory")?.takeIf { AssistantContext.live(it.optLong("at")) }
        val story = if (start != null) JSONObject().put("theme", start.groupValues[1]).put("stage", 0).put("character", "Mira") else old ?: return null
        val lower = text.lowercase()
        val style = Regex("(?i)^make the story (funny|gentle|mysterious)$").matchEntire(text)
        val explicitResume = lower in setOf("continue the story", "continue story")
        if (start == null && !explicitResume && state.featureState?.optString("activeDialogue") != "creative") return null
        if (start == null && style == null && lower !in setOf("continue the story", "continue story", "what happens next", "shorter story", "stop the story") && !lower.startsWith("name the character ")) return null
        if (lower == "stop the story") { features.remove("creativeStory"); return "We'll leave the story there." }
        if (lower.startsWith("name the character ")) {
            val name = text.substringAfter("character ").trim()
            require(name.length in 1..40 && name.all { it.isLetter() || it == ' ' || it == '-' }) { "Use a short character name" }
            story.put("character", name)
        }
        if (style != null) story.put("style", style.groupValues[1].lowercase())
        val name = story.optString("character", "Mira"); val theme = story.getString("theme")
        val stage = if (start != null) 0 else story.optInt("stage") + if (explicitResume || lower == "what happens next") 1 else 0
        val body = when (stage.coerceAtMost(3)) {
            0 -> "$name found a little box labelled ‘$theme’. Inside was a folded map with a single instruction: bring one question, and leave one answer. Rather than rush away, $name sat on the doorstep and thought about which question mattered."
            1 -> "At the place marked on the map, $name met a keeper who collected unfinished questions. ‘You don't have to solve everything,’ the keeper said. ‘Choose one thing you can look at closely.’ $name unfolded the map again and noticed a path drawn on its reverse."
            2 -> "The path led back to the doorstep. The box was still there, but its label now had room for someone else's words. $name wrote one useful answer, left space for a better question, and put the map inside. The next visitor would have a place to begin."
            else -> "That's the end of this little story. We can change the theme or leave it there."
        }
        state.clearPending(); state.switchTopic("conversation"); features.put("activeDialogue", "creative")
        story.put("stage", stage.coerceAtMost(3)).put("at", System.currentTimeMillis()); features.put("creativeStory", story)
        val narration = when (story.optString("style")) {
            "funny" -> body + " The keeper's only official rule was written on a biscuit. Nobody had checked whether it was still there."
            "gentle" -> body + " There was no hurry. The question could wait until the morning light reached the doorstep."
            "mysterious" -> body + " Beneath the map was a second mark that hadn't been there before."
            else -> body
        }
        return if (lower == "shorter story") "$name followed a map about $theme and discovered that a useful question could be a gift to the next person." else narration
    }
}
