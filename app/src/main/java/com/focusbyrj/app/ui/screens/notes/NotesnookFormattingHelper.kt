/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.ui.screens.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class NoteStats(
    val wordCount: Int,
    val charCount: Int,
    val paragraphCount: Int,
    val readingTimeMinutes: Int
)

object NotesnookFormattingHelper {

    private val greekAlphabet = listOf("α", "β", "γ", "δ", "ε", "ζ", "η", "θ", "ι", "κ", "λ", "μ", "ν", "ξ", "ο", "π", "ρ", "σ", "τ", "υ", "φ", "χ", "ψ", "ω")

    fun applyInlineWrap(
        tfv: TextFieldValue,
        prefix: String,
        suffix: String = prefix
    ): TextFieldValue {
        val text = tfv.text
        val sel = tfv.selection
        val minSel = min(sel.start, sel.end)
        val maxSel = max(sel.start, sel.end)

        if (minSel != maxSel) {
            val selectedText = text.substring(minSel, maxSel)
            // Check if already wrapped -> unwrap
            if (selectedText.startsWith(prefix) && selectedText.endsWith(suffix) && selectedText.length >= prefix.length + suffix.length) {
                val unwrapped = selectedText.substring(prefix.length, selectedText.length - suffix.length)
                val newText = text.substring(0, minSel) + unwrapped + text.substring(maxSel)
                return tfv.copy(
                    text = newText,
                    selection = TextRange(minSel, minSel + unwrapped.length)
                )
            }
            // Otherwise wrap
            val wrapped = "$prefix$selectedText$suffix"
            val newText = text.substring(0, minSel) + wrapped + text.substring(maxSel)
            return tfv.copy(
                text = newText,
                selection = TextRange(minSel, minSel + wrapped.length)
            )
        } else {
            // Collapsed selection
            val pos = minSel
            val inserted = "$prefix$suffix"
            val newText = text.substring(0, pos) + inserted + text.substring(pos)
            val newCursor = pos + prefix.length
            return tfv.copy(
                text = newText,
                selection = TextRange(newCursor, newCursor)
            )
        }
    }

    fun applyLinePrefix(
        tfv: TextFieldValue,
        targetPrefix: String
    ): TextFieldValue {
        val text = tfv.text
        val sel = tfv.selection
        val cursor = min(sel.start, sel.end)

        val lineStart = text.lastIndexOf('\n', startIndex = max(0, cursor - 1)).let {
            if (it == -1) 0 else it + 1
        }
        val lineEnd = text.indexOf('\n', startIndex = cursor).let {
            if (it == -1) text.length else it
        }

        val currentLine = text.substring(lineStart, lineEnd)

        // Find existing prefix
        val knownStaticPrefixes = listOf(
            "### ", "## ", "# ", "> [!NOTE] ", "> 💡 Note: ", "> ⚠️ Warning: ", "> 📌 Important: ",
            "> ", "- [ ] ", "- [x] ", "• ", "- ", "* "
        )

        var existingPrefix = knownStaticPrefixes.firstOrNull { currentLine.startsWith(it) }
        if (existingPrefix == null) {
            val dynamicMatch = Regex("^(\\d+|[a-zA-Z]|[ivxIVX]+|[α-ωΑ-Ω])\\.\\s+").find(currentLine)
            if (dynamicMatch != null) {
                existingPrefix = dynamicMatch.value
            }
        }

        val (newLine, cursorDelta) = when {
            existingPrefix == targetPrefix -> {
                // Toggle off
                val stripped = currentLine.removePrefix(targetPrefix)
                Pair(stripped, -targetPrefix.length)
            }
            existingPrefix != null -> {
                // Replace prefix
                val stripped = currentLine.removePrefix(existingPrefix)
                val withNew = "$targetPrefix$stripped"
                Pair(withNew, targetPrefix.length - existingPrefix.length)
            }
            else -> {
                // Add prefix
                val withNew = "$targetPrefix$currentLine"
                Pair(withNew, targetPrefix.length)
            }
        }

        val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        val newCursor = (cursor + cursorDelta).coerceIn(0, newText.length)

        return tfv.copy(
            text = newText,
            selection = TextRange(newCursor, newCursor)
        )
    }

    /**
     * Smart Auto-Continuation for Bullet, Numbered, Lettered, Roman, and Greek Lists when Enter is pressed.
     * Also automatically cleans and exits list if Enter is pressed on an empty bullet line!
     */
    fun handleEnterKey(oldTfv: TextFieldValue, newTfv: TextFieldValue): TextFieldValue? {
        val oldText = oldTfv.text
        val newText = newTfv.text
        val oldSel = oldTfv.selection
        val newSel = newTfv.selection

        // Check if user inserted a newline `\n` at the cursor position
        if (newText.length == oldText.length + 1 && newSel.start == oldSel.start + 1 && newText.getOrNull(oldSel.start) == '\n') {
            val cursorBeforeEnter = oldSel.start
            val lineStart = oldText.lastIndexOf('\n', startIndex = max(0, cursorBeforeEnter - 1)).let { if (it == -1) 0 else it + 1 }
            val lineEnd = cursorBeforeEnter
            val previousLine = oldText.substring(lineStart, lineEnd)

            // 1. Check if previous line is ONLY an empty bullet / list marker -> Exit list gracefully
            val emptyStaticBullets = listOf("- [ ] ", "- [x] ", "• ", "- ", "* ")
            for (prefix in emptyStaticBullets) {
                if (previousLine.trim() == prefix.trim() || previousLine == prefix) {
                    val cleanText = oldText.substring(0, lineStart) + oldText.substring(lineEnd)
                    return TextFieldValue(cleanText, TextRange(lineStart, lineStart))
                }
            }

            val emptyNumberedMatch = Regex("^(\\d+|[a-zA-Z]|[ivxIVX]+|[α-ωΑ-Ω])\\.\\s*$").matchEntire(previousLine)
            if (emptyNumberedMatch != null) {
                val cleanText = oldText.substring(0, lineStart) + oldText.substring(lineEnd)
                return TextFieldValue(cleanText, TextRange(lineStart, lineStart))
            }

            // 2. Check for Checkbox list
            if (previousLine.startsWith("- [ ] ") || previousLine.startsWith("- [x] ")) {
                val nextPrefix = "- [ ] "
                val insertedText = newText.substring(0, newSel.start) + nextPrefix + newText.substring(newSel.start)
                val newPos = newSel.start + nextPrefix.length
                return TextFieldValue(insertedText, TextRange(newPos, newPos))
            }

            // 3. Check for Bullet list (- , • , * )
            val bulletPrefix = listOf("• ", "- ", "* ").firstOrNull { previousLine.startsWith(it) }
            if (bulletPrefix != null) {
                val insertedText = newText.substring(0, newSel.start) + bulletPrefix + newText.substring(newSel.start)
                val newPos = newSel.start + bulletPrefix.length
                return TextFieldValue(insertedText, TextRange(newPos, newPos))
            }

            // 4. Check for Decimal Numbered list (1. , 2. , 3. )
            val decimalMatch = Regex("^(\\d+)\\.\\s+").find(previousLine)
            if (decimalMatch != null) {
                val num = decimalMatch.groupValues[1].toIntOrNull() ?: 1
                val nextPrefix = "${num + 1}. "
                val insertedText = newText.substring(0, newSel.start) + nextPrefix + newText.substring(newSel.start)
                val newPos = newSel.start + nextPrefix.length
                return TextFieldValue(insertedText, TextRange(newPos, newPos))
            }

            // 5. Check for Uppercase Alphabetical list (A. , B. , C. )
            val upperAlphaMatch = Regex("^([A-Z])\\.\\s+").find(previousLine)
            if (upperAlphaMatch != null) {
                val ch = upperAlphaMatch.groupValues[1][0]
                val nextChar = if (ch in 'A'..'Y') (ch + 1) else 'Z'
                val nextPrefix = "$nextChar. "
                val insertedText = newText.substring(0, newSel.start) + nextPrefix + newText.substring(newSel.start)
                val newPos = newSel.start + nextPrefix.length
                return TextFieldValue(insertedText, TextRange(newPos, newPos))
            }

            // 6. Check for Lowercase Alphabetical list (a. , b. , c. )
            val lowerAlphaMatch = Regex("^([a-z])\\.\\s+").find(previousLine)
            if (lowerAlphaMatch != null) {
                val ch = lowerAlphaMatch.groupValues[1][0]
                val nextChar = if (ch in 'a'..'y') (ch + 1) else 'z'
                val nextPrefix = "$nextChar. "
                val insertedText = newText.substring(0, newSel.start) + nextPrefix + newText.substring(newSel.start)
                val newPos = newSel.start + nextPrefix.length
                return TextFieldValue(insertedText, TextRange(newPos, newPos))
            }

            // 7. Check for Roman numerals (I. , II. , III. , i. , ii. )
            val upperRomanMatch = Regex("^([IVXLCDM]+)\\.\\s+").find(previousLine)
            if (upperRomanMatch != null) {
                val roman = upperRomanMatch.groupValues[1]
                val value = parseRomanNumeral(roman)
                if (value != null) {
                    val nextPrefix = "${intToRomanNumeral(value + 1, false)}. "
                    val insertedText = newText.substring(0, newSel.start) + nextPrefix + newText.substring(newSel.start)
                    val newPos = newSel.start + nextPrefix.length
                    return TextFieldValue(insertedText, TextRange(newPos, newPos))
                }
            }

            val lowerRomanMatch = Regex("^([ivxlcdm]+)\\.\\s+").find(previousLine)
            if (lowerRomanMatch != null) {
                val roman = lowerRomanMatch.groupValues[1]
                val value = parseRomanNumeral(roman)
                if (value != null) {
                    val nextPrefix = "${intToRomanNumeral(value + 1, true)}. "
                    val insertedText = newText.substring(0, newSel.start) + nextPrefix + newText.substring(newSel.start)
                    val newPos = newSel.start + nextPrefix.length
                    return TextFieldValue(insertedText, TextRange(newPos, newPos))
                }
            }

            // 8. Check for Greek letters (α. , β. , γ. )
            val greekMatch = Regex("^([α-ωΑ-Ω])\\.\\s+").find(previousLine)
            if (greekMatch != null) {
                val gChar = greekMatch.groupValues[1]
                val idx = greekAlphabet.indexOf(gChar.lowercase())
                val nextGreek = if (idx != -1 && idx + 1 < greekAlphabet.size) greekAlphabet[idx + 1] else "•"
                val nextPrefix = "$nextGreek. "
                val insertedText = newText.substring(0, newSel.start) + nextPrefix + newText.substring(newSel.start)
                val newPos = newSel.start + nextPrefix.length
                return TextFieldValue(insertedText, TextRange(newPos, newPos))
            }
        }
        return null
    }

    private fun intToRomanNumeral(num: Int, lower: Boolean = false): String {
        val map = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
            100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
            10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
        )
        var n = num
        val sb = StringBuilder()
        for ((value, roman) in map) {
            while (n >= value) {
                sb.append(roman)
                n -= value
            }
        }
        val res = sb.toString().ifEmpty { "I" }
        return if (lower) res.lowercase() else res
    }

    private fun parseRomanNumeral(roman: String): Int? {
        val clean = roman.uppercase()
        val values = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
        var total = 0
        var prev = 0
        for (i in clean.length - 1 downTo 0) {
            val curr = values[clean[i]] ?: return null
            if (curr < prev) total -= curr else total += curr
            prev = curr
        }
        return if (total > 0) total else null
    }


    fun insertTimestamp(tfv: TextFieldValue): TextFieldValue {
        val sdf = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault())
        val formattedDate = sdf.format(Date())
        val text = tfv.text
        val pos = min(tfv.selection.start, tfv.selection.end)
        val newText = text.substring(0, pos) + formattedDate + text.substring(pos)
        val newCursor = pos + formattedDate.length
        return tfv.copy(
            text = newText,
            selection = TextRange(newCursor, newCursor)
        )
    }

    fun insertLinkTemplate(tfv: TextFieldValue): TextFieldValue {
        val text = tfv.text
        val sel = tfv.selection
        val minSel = min(sel.start, sel.end)
        val maxSel = max(sel.start, sel.end)

        if (minSel != maxSel) {
            val title = text.substring(minSel, maxSel)
            val template = "🔗 $title: https://"
            val newText = text.substring(0, minSel) + template + text.substring(maxSel)
            val urlStart = minSel + template.length
            return tfv.copy(
                text = newText,
                selection = TextRange(urlStart, urlStart)
            )
        } else {
            val template = "🔗 Link: https://"
            val newText = text.substring(0, minSel) + template + text.substring(minSel)
            val urlStart = minSel + template.length
            return tfv.copy(
                text = newText,
                selection = TextRange(urlStart, urlStart)
            )
        }
    }

    fun indent(tfv: TextFieldValue, isOutdent: Boolean = false): TextFieldValue {
        val text = tfv.text
        val sel = tfv.selection
        val cursor = min(sel.start, sel.end)

        val lineStart = text.lastIndexOf('\n', startIndex = max(0, cursor - 1)).let {
            if (it == -1) 0 else it + 1
        }
        val lineEnd = text.indexOf('\n', startIndex = cursor).let {
            if (it == -1) text.length else it
        }

        val currentLine = text.substring(lineStart, lineEnd)

        val (newLine, cursorDelta) = if (!isOutdent) {
            Pair("    $currentLine", 4)
        } else {
            if (currentLine.startsWith("    ")) {
                Pair(currentLine.substring(4), -4)
            } else if (currentLine.startsWith("  ")) {
                Pair(currentLine.substring(2), -2)
            } else if (currentLine.startsWith(" ")) {
                Pair(currentLine.substring(1), -1)
            } else if (currentLine.startsWith("\t")) {
                Pair(currentLine.substring(1), -1)
            } else {
                Pair(currentLine, 0)
            }
        }

        val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        val newCursor = (cursor + cursorDelta).coerceIn(0, newText.length)

        return tfv.copy(
            text = newText,
            selection = TextRange(newCursor, newCursor)
        )
    }

    fun clearFormatting(tfv: TextFieldValue): TextFieldValue {
        val text = tfv.text
        val sel = tfv.selection
        val minSel = min(sel.start, sel.end)
        val maxSel = max(sel.start, sel.end)

        val targetRange = if (minSel != maxSel) {
            Pair(minSel, maxSel)
        } else {
            val lineStart = text.lastIndexOf('\n', startIndex = max(0, minSel - 1)).let { if (it == -1) 0 else it + 1 }
            val lineEnd = text.indexOf('\n', startIndex = minSel).let { if (it == -1) text.length else it }
            Pair(lineStart, lineEnd)
        }

        val targetText = text.substring(targetRange.first, targetRange.second)
        // Clean markdown marks
        val cleaned = targetText
            .replace("**", "")
            .replace("__", "")
            .replace("~~", "")
            .replace("==", "")
            .replace("<u>", "")
            .replace("</u>", "")
            .replace("`", "")
            .replace(Regex("^#{1,6}\\s+"), "")
            .replace(Regex("^>\\s+"), "")
            .replace(Regex("^-\\s+\\[[ xX]\\]\\s+"), "")
            .replace(Regex("^[•*\\-]\\s+"), "")
            .replace(Regex("^(\\d+|[a-zA-Z]|[ivxIVX]+|[α-ωΑ-Ω])\\.\\s+"), "")

        val newText = text.substring(0, targetRange.first) + cleaned + text.substring(targetRange.second)
        val newCursor = targetRange.first + cleaned.length

        return tfv.copy(
            text = newText,
            selection = TextRange(newCursor, newCursor)
        )
    }

    fun calculateStats(content: String): NoteStats {
        if (content.isBlank()) {
            return NoteStats(
                wordCount = 0,
                charCount = 0,
                paragraphCount = 0,
                readingTimeMinutes = 0
            )
        }
        val words = content.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordCount = words.size
        val charCount = content.length
        val paragraphs = content.split("\n\n").filter { it.isNotBlank() }.size.coerceAtLeast(1)
        val readingTime = (wordCount / 200).coerceAtLeast(if (wordCount > 0) 1 else 0)

        return NoteStats(
            wordCount = wordCount,
            charCount = charCount,
            paragraphCount = paragraphs,
            readingTimeMinutes = readingTime
        )
    }
}
