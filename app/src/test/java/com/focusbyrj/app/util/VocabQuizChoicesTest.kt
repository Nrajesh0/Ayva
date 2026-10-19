package com.focusbyrj.app.util

import org.junit.Assert.*
import org.junit.Test

class VocabQuizChoicesTest {
    @Test fun targetAndVisuallyDuplicateMeaningsCannotBeDistractors() {
        val choices = VocabQuizChoices.build("Not eager", listOf("Not eager", " not\u00a0EAGER ", "Willing", "willing", "Careful", "Quick"))
        assertEquals(setOf("Not eager", "Willing", "Careful", "Quick"), choices.toSet())
        assertEquals(4, choices.size)
    }
    @Test fun tooFewDistinctMeaningsCannotProduceAnAutomaticCorrectQuiz() {
        assertTrue(VocabQuizChoices.build("Correct", listOf("Correct", "Other", "other", "")).isEmpty())
        assertTrue(VocabQuizChoices.build("", listOf("One", "Two", "Three")).isEmpty())
    }
}
