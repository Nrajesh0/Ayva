package com.focusbyrj.app.util

import java.text.Normalizer
import java.util.Locale

/** A bookmarked word may still be unlearned: it must not reappear as its own distractor. */
object VocabQuizChoices {
    private val whitespace = Regex("[\\s\\p{Z}]+")
    private fun key(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().lowercase(Locale.ROOT).replace(whitespace, " ")

    fun build(correct: String, candidates: List<String>): List<String> {
        val answer = key(correct)
        if (answer.isBlank()) return emptyList()
        val alternatives = candidates.filter { key(it).isNotBlank() && key(it) != answer }.distinctBy(::key).take(3)
        return if (alternatives.size == 3) (alternatives + correct).shuffled() else emptyList()
    }
}
