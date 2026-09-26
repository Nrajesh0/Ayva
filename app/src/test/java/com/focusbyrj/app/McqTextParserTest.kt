package com.focusbyrj.app

import com.focusbyrj.app.ui.screens.notes.McqTextParser
import org.junit.Assert.*
import org.junit.Test

class McqTextParserTest {

    @Test
    fun testMessySingleLineMcq() {
        val input = "Q12. Which organelle is known as the powerhouse of the cell? a) Nucleus b) Mitochondria c) Ribosome d) Golgi apparatus Answer: B. Explanation: Mitochondria produces ATP."
        assertTrue(McqTextParser.isLikelyMcq(input))

        val parsed = McqTextParser.parse(input)
        assertNotNull(parsed)
        assertEquals("Q12. Which organelle is known as the powerhouse of the cell?", parsed!!.question)
        assertEquals(4, parsed.options.size)
        assertEquals("Nucleus", parsed.options[0].second)
        assertEquals("Mitochondria", parsed.options[1].second)
        assertEquals("Ribosome", parsed.options[2].second)
        assertEquals("Golgi apparatus", parsed.options[3].second)
        assertEquals("B.", parsed.answer)
        assertEquals("Mitochondria produces ATP.", parsed.explanation)

        val formatted = parsed.toFormattedText("ABCD")
        println("FORMATTED:\n$formatted")
        assertTrue(formatted.contains("**Q12.** Which organelle is known as the powerhouse of the cell?"))
        assertTrue(formatted.contains("(A) Nucleus"))
        assertTrue(formatted.contains("(B) Mitochondria"))
        assertTrue(formatted.contains("(C) Ribosome"))
        assertTrue(formatted.contains("(D) Golgi apparatus"))
        assertTrue(formatted.contains("💡 **Answer:** (B) Mitochondria"))
        assertTrue(formatted.contains("📝 **Explanation:** Mitochondria produces ATP."))
    }

    @Test
    fun testStandardNumberedOptions() {
        val input = """
            What is the capital of France?
            1) Berlin
            2) Madrid
            3) Paris
            4) Rome
            Ans: 3
        """.trimIndent()

        assertTrue(McqTextParser.isLikelyMcq(input))
        val parsed = McqTextParser.parse(input)
        assertNotNull(parsed)
        assertEquals(4, parsed!!.options.size)
        assertEquals("Paris", parsed.options[2].second)

        val formatted = parsed.toFormattedText("ABCD")
        assertTrue(formatted.contains("(C) Paris"))
        assertTrue(formatted.contains("💡 **Answer:** (C) Paris"))
    }

    @Test
    fun testPeriodOptionsWithoutAns() {
        val input = """
            1. Which of the following is a prime number?
            A. 4
            B. 6
            C. 7
            D. 9
        """.trimIndent()

        assertTrue(McqTextParser.isLikelyMcq(input))
        val parsed = McqTextParser.parse(input)
        assertNotNull(parsed)
        assertEquals(4, parsed!!.options.size)
        assertEquals("7", parsed.options[2].second)
    }

    @Test
    fun testRegularTextIsNotMcq() {
        val input = "Today I went to the store and bought apples and oranges. Meeting at 4pm."
        assertFalse(McqTextParser.isLikelyMcq(input))
        assertNull(McqTextParser.parse(input))
    }
}
