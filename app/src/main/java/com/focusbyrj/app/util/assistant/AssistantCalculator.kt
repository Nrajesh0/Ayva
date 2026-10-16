package com.focusbyrj.app.util.assistant

import java.math.BigDecimal
import java.math.MathContext

/** Arithmetic parser with a small grammar and explicit input/depth limits; no evaluation APIs. */
object AssistantCalculator {
    fun answer(input: String): String? {
        val original = input.trim().lowercase().trimEnd('?', '!')
        if (!Regex("^(?:calculate|what is|what's|solve)\\s+").containsMatchIn(original)) return null
        val expression = original.replaceFirst(Regex("^(?:calculate|what is|what's|solve)\\s+"), "")
            .replace("multiplied by", "*").replace("divided by", "/").replace("times", "*").replace("plus", "+").replace("minus", "-").replace('×', '*').replace('÷', '/')
        if (!expression.any(Char::isDigit) || expression.any { !it.isDigit() && !it.isWhitespace() && it !in ".+-*/%()" }) return null
        require(expression.length <= 120) { "Keep arithmetic expressions under 120 characters" }
        val parser = Parser(expression)
        val value = parser.expression(0)
        parser.space()
        require(parser.done()) { "I couldn't read the complete expression" }
        return "${input.substringAfter(' ', input).trimEnd('?')} = **${value.stripTrailingZeros().toPlainString()}**"
    }

    private class Parser(private val text: String) {
        var index = 0
        private val precision = MathContext(12)
        fun done() = index == text.length
        fun space() { while (index < text.length && text[index].isWhitespace()) index++ }
        private fun take(char: Char): Boolean { space(); return if (index < text.length && text[index] == char) { index++; true } else false }
        fun expression(depth: Int): BigDecimal {
            var result = term(depth)
            while (true) result = when { take('+') -> result.add(term(depth), precision); take('-') -> result.subtract(term(depth), precision); else -> return result }
        }
        private fun term(depth: Int): BigDecimal {
            var result = atom(depth)
            while (true) result = when { take('*') -> result.multiply(atom(depth), precision); take('/') -> result.divide(atom(depth), precision); take('%') -> result.remainder(atom(depth), precision); else -> return result }
        }
        private fun atom(depth: Int): BigDecimal {
            require(depth <= 8) { "Use fewer nested parentheses" }
            if (take('+')) return atom(depth + 1)
            if (take('-')) return atom(depth + 1).negate()
            if (take('(')) { val value = expression(depth + 1); require(take(')')) { "A closing parenthesis is missing" }; return value }
            space(); val start = index
            while (index < text.length && (text[index].isDigit() || text[index] == '.')) index++
            require(index > start && index - start <= 24) { "Use numbers with at most 24 digits" }
            return text.substring(start, index).toBigDecimalOrNull()?.round(precision) ?: error("That number isn't valid")
        }
    }
}
