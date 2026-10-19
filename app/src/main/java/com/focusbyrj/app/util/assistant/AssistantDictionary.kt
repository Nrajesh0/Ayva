package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.util.TalkAction
import org.json.JSONObject

/** Source-tied senses, not generated definitions. Unsupported sentence reasoning stays explicit. */
object AssistantDictionary {
    private val lookup = Regex("(?i)^(?:what does (.+?) mean|what is the meaning of (.+)|meaning of (.+)|define (.+)|(?:look up|lookup) (.+)|explain (?:the word|the phrase|word|phrase) (.+))$")
    fun isLookup(text: String) = lookup.matches(text.trim().trimEnd('?', '.', '!')) || text.startsWith("explain sentence", true)
    private fun features(state: AssistantSession) = state.featureState ?: JSONObject().also { state.featureState = it }
    private fun frame(state: AssistantSession) = state.featureState?.optJSONObject("dictionary")?.takeIf { AssistantContext.live(it.optLong("at")) }
    private fun plain(context: Context, id: String) = JSONObject(context.assets.open("dictionary/plain_english.json").bufferedReader().use { it.readText() }).optJSONObject(id)

    suspend fun answer(context: Context, app: FocusApplication, state: AssistantSession, input: String): FeatureTurn? {
        var request = input
        if (input.startsWith("/ayva dictionary ")) {
            val parts = input.split(' '); val active = frame(state)
            if (parts.size != 5 || active == null || state.topic != "dictionary") return FeatureTurn.Reply("That dictionary control expired. Look the word up again.")
            val key = runCatching { String(android.util.Base64.decode(parts[2], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP), Charsets.UTF_8) }.getOrNull()
            if (key != active.optString("key") || parts[3] != active.optString("sense")) return FeatureTurn.Reply("That word or meaning is no longer selected. Look it up again before using its controls.")
            request = when (parts[4]) { "simple" -> "simpler"; "example" -> "give an example"; "save" -> "save this word"; "next" -> "other meaning"; else -> return FeatureTurn.Reply("Unknown dictionary control.") }
        }
        val text = request.trim().trimEnd('?', '.', '!'); val lower = text.lowercase(java.util.Locale.ROOT)
        if (lower in setOf("dictionary credits", "dictionary license", "dictionary sources")) {
            state.clearPending(); state.switchTopic("dictionary")
            return FeatureTurn.Reply(context.assets.open("dictionary/LICENSE.txt").bufferedReader().use { it.readText() })
        }
        if (lower == "what sentences can you explain") return FeatureTurn.Reply("Supported sentence patterns: ‘I would [not] have gone had I known’, ‘I used to …’, ‘I am used to …’, and ‘I am [not] feeling under the weather’. I can explain known word/phrase meanings separately. I can't reliably paraphrase arbitrary sentences or infer sarcasm.")
        if (lower.startsWith("explain sentence")) {
            state.clearPending(); state.switchTopic("dictionary"); features(state).remove("dictionary")
            val sentence = text.replaceFirst(Regex("(?i)^explain sentence[: ]+"), "").trim('"', '\'')
            return FeatureTurn.Reply(explainSentence(sentence))
        }
        val compare = Regex("(?i)^what(?:'s| is) the difference between (.+?) and (.+)$").matchEntire(text)
        if (compare != null) {
            val a = compare.groupValues[1].trim('"', '\''); val b = compare.groupValues[2].trim('"', '\'')
            val first = AssistantDictionaryStore.lookup(context, a); val second = AssistantDictionaryStore.lookup(context, b)
            if (first.isEmpty() || second.isEmpty()) return null // Settings/features own their comparisons.
            state.clearPending(); state.switchTopic("dictionary"); features(state).remove("dictionary")
            return FeatureTurn.Reply("Source meanings to compare:\n**$a**: ${first.first().definition}\n**$b**: ${second.first().definition}\nWords can have other senses; give the word and context if these aren't the ones you mean. Source: Wordset (dictionary credits).")
        }
        val match = lookup.matchEntire(text)
        val previous = frame(state)
        val followUp = lower in setOf("simpler", "more simply", "explain in simple english", "another example", "give an example", "use it in a sentence", "other meaning", "the other meaning", "another meaning", "next meaning", "more meanings", "save this word", "save that word", "bookmark this word", "quiz me on it", "back to the dictionary") || Regex("^meaning \\d+$").matches(lower)
        if (match == null && (!followUp || previous == null || (state.topic != "dictionary" && lower != "back to the dictionary"))) return null
        val key = match?.groupValues?.drop(1)?.firstOrNull(String::isNotBlank)?.trim('"', '\'', '“', '”', '‘', '’')?.let(AssistantDictionaryStore::normalize) ?: previous!!.getString("key")
        if (key.length !in 1..120) return FeatureTurn.Reply("Give one word or phrase of up to 120 characters.")
        // Preserve the existing idiom/OWS lookup and bookmark dialogue when using its original command.
        if (match != null && Regex("(?i)^(?:look up|lookup) ").containsMatchIn(text)) {
            val dao = app.vocabDatabase.vocabDao()
            if (dao.findIdioms(key).isNotEmpty() || dao.findOws(key).isNotEmpty()) return AssistantVocabularyFeatures.answer(app, state, input)
        }
        val senses = AssistantDictionaryStore.lookup(context, key)
        state.clearPending(); state.switchTopic("dictionary")
        if (senses.isEmpty()) {
            features(state).remove("dictionary")
            val forms = AssistantDictionaryStore.relatedForms(context, key)
            return FeatureTurn.Reply("I don't have an entry for **$key** in the bundled dictionary. I won't invent a meaning.${if (forms.isNotEmpty()) " These related forms may help; choose one if intended: ${forms.joinToString()}." else " Check the spelling or ask for a shorter phrase."}", forms.map { TalkAction.AskQuery("define $it", it) })
        }
        var index = if (match == null) senses.indexOfFirst { it.id == previous?.optString("sense") }.coerceAtLeast(0) else 0
        if (lower in setOf("other meaning", "the other meaning", "another meaning", "next meaning", "more meanings")) index = (index + 1) % senses.size
        if (Regex("^meaning \\d+$").matches(lower)) {
            index = lower.substringAfter(' ').toIntOrNull()?.minus(1) ?: -1
            if (index !in senses.indices) return FeatureTurn.Reply("Choose a sense number from 1–${senses.size}.")
        }
        val sense = senses[index]
        features(state).put("dictionary", JSONObject().put("key", key).put("sense", sense.id).put("at", System.currentTimeMillis()))
        if (lower in setOf("save this word", "save that word", "bookmark this word")) {
            val dao = app.vocabDatabase.vocabDao()
            val link = app.vocabDatabase.dictionaryBookmarkDao().get(sense.id)
            val current = link?.let { dao.getOwsById(it.owsId) } ?: dao.findOws(key).singleOrNull { it.term == sense.word && it.definition == sense.definition }
            if (current?.isBookmarked == 1) return FeatureTurn.Reply("**${sense.word}** is already in your vocabulary bookmarks.")
            return FeatureTurn.Preview(listOf(AssistantAction.SaveDictionaryWord(key, sense.id, current?.id, current?.isBookmarked ?: current?.let { 0 })),
                "Save this meaning of **${sense.word}** to vocabulary bookmarks?\n${sense.definition}\nIt won't be marked learned or stored in Notes.")
        }
        if (lower == "quiz me on it") {
            val link = app.vocabDatabase.dictionaryBookmarkDao().get(sense.id)
            val row = link?.let { app.vocabDatabase.vocabDao().getOwsById(it.owsId) }
            return if (row?.isBookmarked == 1) FeatureTurn.Reply("Opening the native quiz with your bookmarked vocabulary, using this saved sense.", redirect = "/vocab_quiz saved ows:${row.id}")
                else FeatureTurn.Reply("Save this sense to vocabulary bookmarks first, then ask for a quiz. I haven't saved or launched anything.", listOf(TalkAction.AskQuery("save this word", "Review saving this word")))
        }
        val editorial = plain(context, sense.id)
        val simple = lower in setOf("simpler", "more simply", "explain in simple english")
        val exampleRequest = lower in setOf("another example", "give an example", "use it in a sentence")
        val body = when {
            simple -> editorial?.optString("simple")?.takeIf(String::isNotBlank)?.let { "In simple English: $it" }
                ?: "Source definition: ${sense.definition}\nI don't have a separately written simpler wording for this sense. I can explain another word in the definition."
            exampleRequest -> {
                val examples = editorial?.optJSONArray("examples")
                if (examples != null && examples.length() > 0) {
                    val n = if (lower == "another example") ((previous?.optInt("example", -1) ?: -1) + 1) % examples.length() else 0
                    features(state).getJSONObject("dictionary").put("example", n)
                    "Ayva example for this sense: ${examples.getString(n)}"
                } else if (sense.example.isNotBlank()) "Source example: ${sense.example}\nThis sense has one bundled example; I won't invent a second source example."
                else "This source sense has no example. Try another meaning or ask about a different word."
            }
            else -> sense.definition + if (sense.example.isNotBlank()) "\nSource example: ${sense.example}" else ""
        }
        val alternatives = if (match != null && senses.size > 1) "\nOther source senses (not ranked by frequency):\n" + senses.take(6).mapIndexed { i, s -> "${i + 1}. ${s.part}: ${s.definition}" }.joinToString("\n") + if (senses.size > 6) "\n${senses.size} senses total. Say ‘meaning 7’, or ‘other meaning’." else "" else ""
        val labels = sense.labels.takeIf { it.isNotEmpty() }?.joinToString()?.let { " · $it" }.orEmpty()
        return FeatureTurn.Reply("**${sense.word}** · ${sense.part}$labels · meaning ${index + 1}/${senses.size}\n$body$alternatives\nSource: Wordset / WordNet. Say ‘dictionary credits’ for attribution and license.",
            listOf(TalkAction.AskQuery(control(key, sense.id, "simple"), "Simpler"), TalkAction.AskQuery(control(key, sense.id, "example"), "Example"), TalkAction.AskQuery(control(key, sense.id, "save"), "Save word")) + if (senses.size > 1) listOf(TalkAction.AskQuery(control(key, sense.id, "next"), "Other meaning")) else emptyList())
    }

    private fun control(key: String, sense: String, op: String) = "/ayva dictionary " + android.util.Base64.encodeToString(key.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP) + " $sense $op"

    internal fun explainSentence(raw: String): String {
        val text = raw.trim().trimEnd('.', '!', '?').replace('’', '\'')
        Regex("(?i)^i would (not )?have gone had i known$").matchEntire(text)?.let {
            return "‘Had I known’ means ‘if I had known’. In simpler order: ‘If I had known, I would ${it.groupValues[1]}have gone.’ This imagines a different past; it doesn't by itself confirm every event that actually happened."
        }
        Regex("(?i)^i (?:am|'m) (not )?(?:feeling )?under the weather$").matchEntire(text.replace("I'm", "I 'm", true))?.let {
            return if (it.groupValues[1].isBlank()) "The speaker says they feel somewhat unwell. ‘Under the weather’ is an idiom here, not a weather report." else "The speaker says they do not feel unwell. ‘Not’ reverses the idiom ‘under the weather’."
        }
        if (Regex("(?i)^i used to .+$").matches(text)) return "‘Used to’ describes a past habit or state, usually suggesting it is different now. The rest of your sentence is kept as written: ‘${text.substring(10)}’. I haven't inferred why it changed."
        if (Regex("(?i)^i am used to .+$").matches(text)) return "‘Am used to’ means the speaker is familiar or comfortable with something. It does not mean the activity stopped. This differs from ‘I used to …’, which describes the past."
        return "I can't reliably explain that whole sentence with my supported offline patterns, including sarcasm or implied meanings. I can look up a particular word or known phrase. Try ‘what does reluctant mean’, or ask ‘what sentences can you explain’."
    }
}
