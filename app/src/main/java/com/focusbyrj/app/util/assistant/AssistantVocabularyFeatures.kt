package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.util.TalkAction
import org.json.JSONArray
import org.json.JSONObject

object AssistantVocabularyFeatures {
    private data class Word(val type: String, val id: Int, val name: String, val meaning: String, val bookmark: Int)
    private fun features(state: AssistantSession): JSONObject = state.featureState ?: JSONObject().also { state.featureState = it }
    private suspend fun get(app: FocusApplication, type: String, id: Int): Word? {
        val dao = app.vocabDatabase.vocabDao()
        return if (type == "idiom") dao.getIdiomById(id)?.let { Word(type, id, it.idiom, it.meaning, it.isBookmarked ?: 0) }
        else if (type == "ows") dao.getOwsById(id)?.let { Word(type, id, it.term, it.definition, it.isBookmarked ?: 0) } else null
    }
    suspend fun recordCard(app: FocusApplication, state: AssistantSession, json: String, timestamp: Long) {
        if (System.currentTimeMillis() - timestamp !in 0..900_000L) return
        if (timestamp < (state.featureState?.optJSONObject("wordRefs")?.optLong("at") ?: 0L)) return
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
        val refs = JSONArray()
        for ((key, type) in listOf("idiom" to "idiom", "ows" to "ows", "rev_idiom" to "idiom", "rev_ows" to "ows")) {
            val id = obj.optJSONObject(key)?.optInt("id", -1) ?: continue
            if (get(app, type, id) != null) refs.put(JSONObject().put("type", type).put("id", id))
        }
        if (refs.length() == 0) return
        AssistantConversation.suspendQuestion(state); state.clearPending(); state.switchTopic("vocabulary")
        features(state).put("wordRefs", JSONObject().put("items", refs).put("at", timestamp))
    }
    suspend fun answer(app: FocusApplication, state: AssistantSession, input: String): FeatureTurn? {
        val text = input.trim().trimEnd('.', '!', '?'); val lower = text.lowercase()
        if (lower in setOf("review words i missed", "review my missed words", "review words", "quiz me on missed words", "practice missed words")) {
            val dao = app.vocabDatabase.vocabDao()
            val idioms = dao.getMissedIdioms(10); val words = dao.getMissedOws(10)
            state.switchTopic("vocabulary")
            if (idioms.isEmpty() && words.isEmpty()) return FeatureTurn.Reply("No saved missed-word flags are available. Unmastered words alone do not prove a wrong answer.", listOf(TalkAction.AskQuery("/vocab_quiz", "Practice learned words")))
            if (lower.startsWith("quiz") || lower.startsWith("practice")) return FeatureTurn.Reply("Opening a quiz from saved missed words.", redirect = "/vocab_quiz missed")
            return FeatureTurn.Reply("Saved missed-word review (up to 20):\n" + (idioms.map { "• ${it.idiom} — ${it.meaning}" } + words.map { "• ${it.term} — ${it.definition}" }).joinToString("\n") + "\nThese are the current missed-word flags, not a complete historical mistake log.", listOf(TalkAction.AskQuery("practice missed words", "Quiz these words")))
        }
        val match = Regex("(?i)^(look up|lookup|explain|show|bookmark|unbookmark) (?:(idiom|word|ows) )?(.+)$").matchEntire(text) ?: return null
        val op = match.groupValues[1].lowercase(); val typeHint = match.groupValues[2].lowercase(); val target = match.groupValues[3].removeSurrounding("\"")
        val pronoun = target.lowercase() in setOf("it", "that", "this", "that word", "that idiom", "this word", "this idiom")
        if (op in setOf("show", "explain") && typeHint.isEmpty() && !pronoun) return null
        val type = when { typeHint == "idiom" || target.endsWith("idiom", true) && pronoun -> "idiom"; typeHint in setOf("word", "ows") -> "ows"; else -> null }
        val dao = app.vocabDatabase.vocabDao()
        val candidates: List<Word> = when {
            pronoun -> {
                val refs = state.featureState?.optJSONObject("wordRefs")?.takeIf { state.topic == "vocabulary" && System.currentTimeMillis() - it.optLong("at") in 0..900_000L }?.optJSONArray("items")
                if (refs == null) emptyList() else (0 until refs.length()).mapNotNull { i -> val r = refs.getJSONObject(i); get(app, r.getString("type"), r.getInt("id")) }.filter { type == null || it.type == type }.distinctBy { it.type to it.id }
            }
            target.startsWith("id:", true) -> target.substringAfter(':').toIntOrNull()?.let { id -> listOfNotNull(if (type != "ows") get(app, "idiom", id) else null, if (type != "idiom") get(app, "ows", id) else null) }.orEmpty()
            else -> (if (type != "ows") dao.getAllIdioms().filter { it.idiom.equals(target, true) }.mapNotNull { it.id?.let { id -> Word("idiom", id, it.idiom, it.meaning, it.isBookmarked ?: 0) } } else emptyList()) +
                (if (type != "idiom") dao.getAllOws().filter { it.term.equals(target, true) }.mapNotNull { it.id?.let { id -> Word("ows", id, it.term, it.definition, it.isBookmarked ?: 0) } } else emptyList())
        }
        if (candidates.size != 1) { state.switchTopic("vocabulary"); features(state).remove("wordRefs") }
        if (candidates.isEmpty()) return FeatureTurn.Reply("I can't identify that word from saved vocabulary. Name it exactly, or show a fresh vocabulary card first. I won't invent a definition.")
        if (candidates.size != 1) return FeatureTurn.Reply("Which saved word do you mean?", candidates.take(8).map { TalkAction.AskQuery("$op ${it.type} id:${it.id}", it.name) })
        val word = candidates.single()
        state.switchTopic("vocabulary")
        features(state).put("wordRefs", JSONObject().put("at", System.currentTimeMillis()).put("items", JSONArray().put(JSONObject().put("type", word.type).put("id", word.id))))
        if (op in setOf("bookmark", "unbookmark")) {
            val wanted = op == "bookmark"
            if ((word.bookmark == 1) == wanted) return FeatureTurn.Reply("**${word.name}** is already ${if (wanted) "bookmarked" else "not bookmarked"}.")
            return FeatureTurn.Preview(listOf(AssistantAction.BookmarkWord(word.type, word.id, wanted, word.bookmark)), "${if (wanted) "Bookmark" else "Remove the bookmark from"} **${word.name}**?")
        }
        return FeatureTurn.Reply("**${word.name}**\n${word.meaning}\nThis is the meaning stored in Ayva's vocabulary library.", listOf(TalkAction.AskQuery("${if (word.bookmark == 1) "unbookmark" else "bookmark"} ${word.type} id:${word.id}", if (word.bookmark == 1) "Remove bookmark" else "Bookmark")))
    }
}
