package com.focusbyrj.app.util

import com.focusbyrj.app.util.assistant.GeneralDialoguePhrases
import org.junit.Assert.*
import org.junit.Test

class GeneralDialoguePhrasesTest {
    @Test fun preservesTheTwelveUsefulNativeMeaningsWithoutAWorker() {
        val samples = mapOf(
            "Could you possibly recommend something relaxing?" to "what could I try",
            "Why would that help?" to "why that",
            "Could you elaborate a little?" to "tell me more",
            "Can you give an example?" to "give me an example",
            "Could you make it shorter?" to "shorter",
            "Recap our conversation" to "what do we have so far",
            "Another option please" to "something else",
            "Crowds exhaust me" to "crowds wear me out",
            "I enjoy peaceful evenings" to "I prefer quiet evenings",
            "I am anxious about studying" to "I am worried about studying",
            "I feel overwhelmed at work" to "I am stressed about work",
            "I feel exhausted" to "I feel tired")
        samples.forEach { (input, expected) -> assertEquals(input, expected, GeneralDialoguePhrases.interpret(input)) }
    }
    @Test fun doesNotFlattenNegationUncertaintyQuotationsOrCommands() {
        listOf("I am not worried about exams", "I might feel exhausted", "My friend says I feel exhausted",
            "I feel exhausted but happy", "I enjoy quiet or crowded places", "I feel weary and anxious",
            "I feel tired\n:quit", "/plan trip", ":system ls", "delete all tasks", "I said I feel weary",
            "I feel \"exhausted\"", "can zebras juggle purple cucumbers", "x".repeat(2001)).forEach {
            assertNull(it, GeneralDialoguePhrases.interpret(it))
        }
    }
}
