package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication

/** Games use existing authored words and vocabulary, without a general dictionary. */
internal object AssistantGameWords {
    private suspend fun savedMeaning(context: Context, word: String): String? {
        val app = context.applicationContext as? FocusApplication ?: return null
        return app.vocabDatabase.vocabDao().findOws(word).firstOrNull { it.term.equals(word, true) }?.definition
    }
    suspend fun accepts(context: Context, word: String): Boolean = word.matches(Regex("[a-z]{2,24}")) &&
        (word in ExperienceContent.chainWords || savedMeaning(context, word) != null)
    suspend fun meaning(context: Context, word: String): String? =
        ExperienceContent.puzzles.firstOrNull { it.word == word }?.clue ?: savedMeaning(context, word)
}
