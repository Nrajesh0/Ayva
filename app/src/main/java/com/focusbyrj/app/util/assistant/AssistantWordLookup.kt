package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication

/** Native vocabulary lookup and authored sentence patterns; no general dictionary assets. */
object AssistantWordLookup {
    private val lookup = Regex("(?i)^(?:what does (.+?) mean|what is the meaning of (.+)|meaning of (.+)|define (.+)|(?:look up|lookup) (.+)|explain (?:the word|the phrase|word|phrase) (.+))$")
    fun isLookup(text: String) = lookup.matches(text.trim().trimEnd('?', '.', '!')) || text.startsWith("explain sentence", true)
    suspend fun answer(context: Context, app: FocusApplication, state: AssistantSession, input: String): FeatureTurn? {
        val text = input.trim().trimEnd('?', '.', '!')
        if (text.startsWith("/ayva dictionary ") || text.equals("back to the dictionary", true)) {
            state.clearPending(); state.featureState?.remove("dictionary"); state.switchTopic("vocabulary")
            return FeatureTurn.Reply("The full dictionary has been removed. Your saved vocabulary and learning progress are still available.")
        }
        if (text.lowercase() in setOf("dictionary credits", "dictionary license", "dictionary sources")) {
            state.clearPending()
            return FeatureTurn.Reply("The full dictionary is no longer bundled. This source notice is retained for previously saved entries:\n" + context.assets.open("licenses/Wordset-NOTICE.txt").bufferedReader().use { it.readText() })
        }
        if (text.equals("what sentences can you explain", true)) return FeatureTurn.Reply("Supported sentence patterns: ‘I would [not] have gone had I known’, ‘I used to …’, ‘I am used to …’, and ‘I am [not] feeling under the weather’. These are fixed offline explanations, not arbitrary sentence paraphrasing.")
        if (text.startsWith("explain sentence", true)) {
            state.clearPending(); state.switchTopic("language")
            return FeatureTurn.Reply(explainSentence(text.replaceFirst(Regex("(?i)^explain sentence[: ]+"), "").trim('"', '\'')))
        }
        val match = lookup.matchEntire(text) ?: return null
        val target = match.groupValues.drop(1).firstOrNull(String::isNotBlank)?.trim('"', '\'', '“', '”', '‘', '’') ?: return null
        state.clearPending(); state.featureState?.remove("dictionary")
        return AssistantVocabularyFeatures.answer(app, state, "look up $target")
    }

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
        return "I can't reliably explain that whole sentence with my supported offline patterns, including sarcasm or implied meanings. You can ask about an entry in your vocabulary library, or ask ‘what sentences can you explain’."
    }
}
