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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

enum class RichSpanType {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    HIGHLIGHT,
    CODE,
    SUBSCRIPT,
    SUPERSCRIPT,
    HEADING_1,
    HEADING_2,
    HEADING_3,
    HEADING_4,
    HEADING_5,
    HEADING_6,
    LINK,
    QUOTE,
    TEXT_COLOR
}

data class RichSpan(
    val type: RichSpanType,
    val start: Int,
    val end: Int,
    val payload: String? = null
) {
    fun isValid(maxLen: Int): Boolean = start < end && start >= 0 && end <= maxLen
}

data class ActiveStyles(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isHighlight: Boolean = false,
    val isCode: Boolean = false,
    val isSubscript: Boolean = false,
    val isSuperscript: Boolean = false,
    val headingLevel: Int = 0, // 0 = Paragraph, 1-6 = H1-H6
    val isBullet: Boolean = false,
    val isNumbered: Boolean = false,
    val isQuote: Boolean = false
)

object RichTextEngine {

    /**
     * Parses serialized markdown or HTML content into clean plain text and a list of RichSpans.
     */
    fun parse(raw: String): Pair<String, List<RichSpan>> {
        if (raw.isEmpty()) return Pair("", emptyList())

        if (raw.contains(NotesnookBlockManager.BLOCKS_PREFIX)) {
            val blocks = NotesnookBlockManager.parse(raw)
            val sb = StringBuilder()
            val spans = mutableListOf<RichSpan>()
            for (block in blocks) {
                when (block) {
                    is NotesnookBlock.Text -> {
                        if (block.text.isNotEmpty()) {
                            if (sb.isNotEmpty()) sb.append('\n')
                            val offset = sb.length
                            sb.append(block.text)
                            for (s in block.spans) {
                                spans.add(s.copy(start = s.start + offset, end = s.end + offset))
                            }
                        }
                    }
                    is NotesnookBlock.Table -> {
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append("📊 [Table ${block.rows}×${block.cols}]")
                    }
                    is NotesnookBlock.Code -> {
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append("💻 [${block.language}] ${block.code.take(80)}")
                    }
                    is NotesnookBlock.Callout -> {
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append("💡 ${block.text}")
                    }
                    is NotesnookBlock.MathFormula -> {
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append("∑ ${block.formula}")
                    }
                    is NotesnookBlock.HorizontalRule -> {
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append("────────")
                    }
                    is NotesnookBlock.Quote -> {
                        if (sb.isNotEmpty()) sb.append('\n')
                        val offset = sb.length
                        sb.append(block.text)
                        spans.add(RichSpan(RichSpanType.QUOTE, offset, offset + block.text.length))
                    }
                    is NotesnookBlock.OutlineItem -> {
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append("• ${block.text}")
                    }
                    is NotesnookBlock.Embed -> {
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append("▶️ ${block.title.ifBlank { block.url }}")
                    }
                    is NotesnookBlock.Attachment -> {
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append("📎 ${block.fileName}")
                    }
                    is NotesnookBlock.Image -> {
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append("🖼️ ${block.caption.ifBlank { "Image" }}")
                    }
                }
            }
            return Pair(sb.toString(), spans)
        }

        val spans = mutableListOf<RichSpan>()
        val sb = StringBuilder()
        val lines = raw.split('\n')

        for ((lineIndex, rawLine) in lines.withIndex()) {
            if (lineIndex > 0) sb.append('\n')
            val lineStartInText = sb.length

            var processedLine = rawLine

            // Line-level: Headings & Quote
            var headingType: RichSpanType? = null
            if (processedLine.startsWith("###### ")) {
                headingType = RichSpanType.HEADING_6
                processedLine = processedLine.substring(7)
            } else if (processedLine.startsWith("##### ")) {
                headingType = RichSpanType.HEADING_5
                processedLine = processedLine.substring(6)
            } else if (processedLine.startsWith("#### ")) {
                headingType = RichSpanType.HEADING_4
                processedLine = processedLine.substring(5)
            } else if (processedLine.startsWith("### ")) {
                headingType = RichSpanType.HEADING_3
                processedLine = processedLine.substring(4)
            } else if (processedLine.startsWith("## ")) {
                headingType = RichSpanType.HEADING_2
                processedLine = processedLine.substring(3)
            } else if (processedLine.startsWith("# ")) {
                headingType = RichSpanType.HEADING_1
                processedLine = processedLine.substring(2)
            } else if (processedLine.startsWith("> ")) {
                headingType = RichSpanType.QUOTE
                processedLine = processedLine.substring(2)
            }

            // Inline markdown & HTML parsing for this line
            var i = 0
            while (i < processedLine.length) {
                if (processedLine.startsWith("<sub>", i, ignoreCase = true)) {
                    val closeIdx = processedLine.indexOf("</sub>", i + 5, ignoreCase = true)
                    if (closeIdx != -1) {
                        val inner = processedLine.substring(i + 5, closeIdx)
                        val spanStart = sb.length
                        sb.append(inner)
                        val spanEnd = sb.length
                        if (spanEnd > spanStart) {
                            spans.add(RichSpan(RichSpanType.SUBSCRIPT, spanStart, spanEnd))
                        }
                        i = closeIdx + 6
                        continue
                    }
                } else if (processedLine.startsWith("<sup>", i, ignoreCase = true)) {
                    val closeIdx = processedLine.indexOf("</sup>", i + 5, ignoreCase = true)
                    if (closeIdx != -1) {
                        val inner = processedLine.substring(i + 5, closeIdx)
                        val spanStart = sb.length
                        sb.append(inner)
                        val spanEnd = sb.length
                        if (spanEnd > spanStart) {
                            spans.add(RichSpan(RichSpanType.SUPERSCRIPT, spanStart, spanEnd))
                        }
                        i = closeIdx + 6
                        continue
                    }
                } else if (processedLine.startsWith("**", i)) {
                    val closeIdx = processedLine.indexOf("**", i + 2)
                    if (closeIdx != -1) {
                        val inner = processedLine.substring(i + 2, closeIdx)
                        val spanStart = sb.length
                        sb.append(inner)
                        val spanEnd = sb.length
                        if (spanEnd > spanStart) {
                            spans.add(RichSpan(RichSpanType.BOLD, spanStart, spanEnd))
                        }
                        i = closeIdx + 2
                        continue
                    }
                } else if (processedLine.startsWith("~~", i)) {
                    val closeIdx = processedLine.indexOf("~~", i + 2)
                    if (closeIdx != -1) {
                        val inner = processedLine.substring(i + 2, closeIdx)
                        val spanStart = sb.length
                        sb.append(inner)
                        val spanEnd = sb.length
                        if (spanEnd > spanStart) {
                            spans.add(RichSpan(RichSpanType.STRIKETHROUGH, spanStart, spanEnd))
                        }
                        i = closeIdx + 2
                        continue
                    }
                } else if (processedLine.startsWith("==", i)) {
                    val closeIdx = processedLine.indexOf("==", i + 2)
                    if (closeIdx != -1) {
                        val inner = processedLine.substring(i + 2, closeIdx)
                        val spanStart = sb.length
                        sb.append(inner)
                        val spanEnd = sb.length
                        if (spanEnd > spanStart) {
                            spans.add(RichSpan(RichSpanType.HIGHLIGHT, spanStart, spanEnd))
                        }
                        i = closeIdx + 2
                        continue
                    }
                } else if (processedLine.startsWith("<u>", i, ignoreCase = true)) {
                    val closeIdx = processedLine.indexOf("</u>", i + 3, ignoreCase = true)
                    if (closeIdx != -1) {
                        val inner = processedLine.substring(i + 3, closeIdx)
                        val spanStart = sb.length
                        sb.append(inner)
                        val spanEnd = sb.length
                        if (spanEnd > spanStart) {
                            spans.add(RichSpan(RichSpanType.UNDERLINE, spanStart, spanEnd))
                        }
                        i = closeIdx + 4
                        continue
                    }
                } else if (processedLine.startsWith("*", i) && !processedLine.startsWith("**", i)) {
                    val closeIdx = processedLine.indexOf("*", i + 1)
                    if (closeIdx != -1 && closeIdx > i + 1) {
                        val inner = processedLine.substring(i + 1, closeIdx)
                        val spanStart = sb.length
                        sb.append(inner)
                        val spanEnd = sb.length
                        if (spanEnd > spanStart) {
                            spans.add(RichSpan(RichSpanType.ITALIC, spanStart, spanEnd))
                        }
                        i = closeIdx + 1
                        continue
                    }
                } else if (processedLine.startsWith("`", i) && !processedLine.startsWith("```", i)) {
                    val closeIdx = processedLine.indexOf("`", i + 1)
                    if (closeIdx != -1 && closeIdx > i + 1) {
                        val inner = processedLine.substring(i + 1, closeIdx)
                        val spanStart = sb.length
                        sb.append(inner)
                        val spanEnd = sb.length
                        if (spanEnd > spanStart) {
                            spans.add(RichSpan(RichSpanType.CODE, spanStart, spanEnd))
                        }
                        i = closeIdx + 1
                        continue
                    }
                }

                sb.append(processedLine[i])
                i++
            }

            val lineEndInText = sb.length
            if (headingType != null && lineEndInText > lineStartInText) {
                spans.add(RichSpan(headingType, lineStartInText, lineEndInText))
            }
        }

        return Pair(sb.toString(), spans)
    }

    /**
     * Serializes clean plain text + RichSpans back into clean Markdown/HTML string for persistent storage.
     */
    fun serialize(plainText: String, spans: List<RichSpan>): String {
        if (plainText.isEmpty()) return ""
        if (spans.isEmpty()) return plainText

        val validSpans = spans.filter { it.isValid(plainText.length) }
        if (validSpans.isEmpty()) return plainText

        val sb = StringBuilder()
        val lines = plainText.split('\n')
        var currentOffset = 0

        for ((lineIdx, line) in lines.withIndex()) {
            if (lineIdx > 0) sb.append('\n')
            val lineStart = currentOffset
            val lineEnd = lineStart + line.length

            val lineSpans = validSpans.filter {
                (it.type == RichSpanType.HEADING_1 || it.type == RichSpanType.HEADING_2 ||
                        it.type == RichSpanType.HEADING_3 || it.type == RichSpanType.HEADING_4 ||
                        it.type == RichSpanType.HEADING_5 || it.type == RichSpanType.HEADING_6 ||
                        it.type == RichSpanType.QUOTE) &&
                        it.start <= lineStart && it.end >= lineEnd
            }

            if (lineSpans.any { it.type == RichSpanType.HEADING_1 }) {
                sb.append("# ")
            } else if (lineSpans.any { it.type == RichSpanType.HEADING_2 }) {
                sb.append("## ")
            } else if (lineSpans.any { it.type == RichSpanType.HEADING_3 }) {
                sb.append("### ")
            } else if (lineSpans.any { it.type == RichSpanType.HEADING_4 }) {
                sb.append("#### ")
            } else if (lineSpans.any { it.type == RichSpanType.HEADING_5 }) {
                sb.append("##### ")
            } else if (lineSpans.any { it.type == RichSpanType.HEADING_6 }) {
                sb.append("###### ")
            } else if (lineSpans.any { it.type == RichSpanType.QUOTE }) {
                sb.append("> ")
            }

            // Inline characters
            for (charIdx in line.indices) {
                val absIdx = lineStart + charIdx
                // Check opens
                for (span in validSpans) {
                    if (span.start == absIdx) {
                        when (span.type) {
                            RichSpanType.BOLD -> sb.append("**")
                            RichSpanType.ITALIC -> sb.append("*")
                            RichSpanType.UNDERLINE -> sb.append("<u>")
                            RichSpanType.STRIKETHROUGH -> sb.append("~~")
                            RichSpanType.HIGHLIGHT -> sb.append("==")
                            RichSpanType.CODE -> sb.append("`")
                            RichSpanType.SUBSCRIPT -> sb.append("<sub>")
                            RichSpanType.SUPERSCRIPT -> sb.append("<sup>")
                            else -> {}
                        }
                    }
                }

                sb.append(line[charIdx])

                // Check closes
                for (span in validSpans) {
                    if (span.end == absIdx + 1) {
                        when (span.type) {
                            RichSpanType.BOLD -> sb.append("**")
                            RichSpanType.ITALIC -> sb.append("*")
                            RichSpanType.UNDERLINE -> sb.append("</u>")
                            RichSpanType.STRIKETHROUGH -> sb.append("~~")
                            RichSpanType.HIGHLIGHT -> sb.append("==")
                            RichSpanType.CODE -> sb.append("`")
                            RichSpanType.SUBSCRIPT -> sb.append("</sub>")
                            RichSpanType.SUPERSCRIPT -> sb.append("</sup>")
                            else -> {}
                        }
                    }
                }
            }

            currentOffset = lineEnd + 1
        }

        return sb.toString()
    }

    /**
     * Toggles a span over the given selection range.
     */
    fun toggleSpan(
        spans: List<RichSpan>,
        type: RichSpanType,
        selection: TextRange,
        textLength: Int
    ): List<RichSpan> {
        val start = min(selection.start, selection.end).coerceIn(0, textLength)
        val end = max(selection.start, selection.end).coerceIn(0, textLength)
        if (start == end) return spans

        val targetRangeSpans = spans.filter { it.type == type && it.start < end && it.end > start }
        val otherSpans = spans.filter { !(it.type == type && it.start < end && it.end > start) }.toMutableList()

        if (targetRangeSpans.isNotEmpty()) {
            // Already contains this style -> Remove / split style in this range
            for (span in targetRangeSpans) {
                if (span.start < start) {
                    otherSpans.add(span.copy(end = start))
                }
                if (span.end > end) {
                    otherSpans.add(span.copy(start = end))
                }
            }
        } else {
            // Add style on this range and merge with adjacent spans of the same type
            var newStart = start
            var newEnd = end
            val toRemove = mutableListOf<RichSpan>()
            for (span in otherSpans) {
                if (span.type == type) {
                    if (span.end == newStart) {
                        newStart = span.start
                        toRemove.add(span)
                    } else if (span.start == newEnd) {
                        newEnd = span.end
                        toRemove.add(span)
                    }
                }
            }
            otherSpans.removeAll(toRemove)
            otherSpans.add(RichSpan(type, newStart, newEnd))
        }

        return otherSpans.sortedBy { it.start }
    }

    /**
     * Toggles line prefix / heading on the current line.
     */
    fun toggleLineStyle(
        spans: List<RichSpan>,
        type: RichSpanType,
        cursor: Int,
        text: String
    ): List<RichSpan> {
        val lineStart = text.lastIndexOf('\n', startIndex = max(0, cursor - 1)).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', startIndex = cursor).let { if (it == -1) text.length else it }
        if (lineEnd <= lineStart) return spans

        val isAlreadySet = spans.any { it.type == type && it.start <= lineStart && it.end >= lineEnd }
        val filtered = spans.filter {
            !(it.start >= lineStart && it.end <= lineEnd &&
                    (it.type == RichSpanType.HEADING_1 || it.type == RichSpanType.HEADING_2 ||
                            it.type == RichSpanType.HEADING_3 || it.type == RichSpanType.HEADING_4 ||
                            it.type == RichSpanType.HEADING_5 || it.type == RichSpanType.HEADING_6 ||
                            it.type == RichSpanType.QUOTE))
        }.toMutableList()

        if (!isAlreadySet) {
            filtered.add(RichSpan(type, lineStart, lineEnd))
        }

        return filtered.sortedBy { it.start }
    }

    /**
     * Determines which styles are currently active at the cursor or selection.
     */
    fun getActiveStyles(
        spans: List<RichSpan>,
        selection: TextRange,
        text: String,
        pendingTypingTypes: Set<RichSpanType> = emptySet()
    ): ActiveStyles {
        val len = text.length
        val start = min(selection.start, selection.end).coerceIn(0, len)
        val end = max(selection.start, selection.end).coerceIn(0, len)

        val isCollapsed = start == end
        fun hasInline(type: RichSpanType): Boolean {
            if (pendingTypingTypes.contains(type)) return true
            return if (isCollapsed) {
                spans.any { it.type == type && ((it.start <= start && it.end >= start) || (start > 0 && it.start < start && it.end >= start)) }
            } else {
                spans.any { it.type == type && it.start < end && it.end > start }
            }
        }

        val lineStart = if (len == 0) 0 else text.lastIndexOf('\n', startIndex = max(0, start - 1)).let { if (it == -1) 0 else it + 1 }
        val lineEnd = if (len == 0) 0 else text.indexOf('\n', startIndex = start).let { if (it == -1) len else it }
        val safeLineStart = lineStart.coerceIn(0, len)
        val safeLineEnd = lineEnd.coerceIn(safeLineStart, len)
        val currentLine = if (safeLineEnd > safeLineStart && safeLineEnd <= len) text.substring(safeLineStart, safeLineEnd) else ""

        val isH1 = spans.any { it.type == RichSpanType.HEADING_1 && it.start <= lineStart && it.end >= lineEnd }
        val isH2 = spans.any { it.type == RichSpanType.HEADING_2 && it.start <= lineStart && it.end >= lineEnd }
        val isH3 = spans.any { it.type == RichSpanType.HEADING_3 && it.start <= lineStart && it.end >= lineEnd }
        val isH4 = spans.any { it.type == RichSpanType.HEADING_4 && it.start <= lineStart && it.end >= lineEnd }
        val isH5 = spans.any { it.type == RichSpanType.HEADING_5 && it.start <= lineStart && it.end >= lineEnd }
        val isH6 = spans.any { it.type == RichSpanType.HEADING_6 && it.start <= lineStart && it.end >= lineEnd }
        val isQuote = spans.any { it.type == RichSpanType.QUOTE && it.start <= lineStart && it.end >= lineEnd } || currentLine.startsWith("> ")

        val isBullet = currentLine.startsWith("- ") || currentLine.startsWith("• ") || currentLine.startsWith("* ")
        val isNumbered = Regex("^(\\d+|[a-zA-Z]|[ivxIVX]+|[α-ωΑ-Ω])\\.\\s+").containsMatchIn(currentLine)

        val headingLevel = when {
            isH1 -> 1
            isH2 -> 2
            isH3 -> 3
            isH4 -> 4
            isH5 -> 5
            isH6 -> 6
            else -> 0
        }

        return ActiveStyles(
            isBold = hasInline(RichSpanType.BOLD),
            isItalic = hasInline(RichSpanType.ITALIC),
            isUnderline = hasInline(RichSpanType.UNDERLINE),
            isStrikethrough = hasInline(RichSpanType.STRIKETHROUGH),
            isHighlight = hasInline(RichSpanType.HIGHLIGHT),
            isCode = hasInline(RichSpanType.CODE),
            isSubscript = hasInline(RichSpanType.SUBSCRIPT),
            isSuperscript = hasInline(RichSpanType.SUPERSCRIPT),
            headingLevel = headingLevel,
            isBullet = isBullet,
            isNumbered = isNumbered,
            isQuote = isQuote
        )
    }

    /**
     * Adjusts span offsets during user typing/editing in real time.
     */
    fun updateSpansOnTextChange(
        oldText: String,
        newText: String,
        spans: List<RichSpan>,
        pendingTypes: Set<RichSpanType> = emptySet()
    ): List<RichSpan> {
        val delta = newText.length - oldText.length
        if (delta == 0 && oldText == newText) return spans

        var startChange = 0
        while (startChange < oldText.length && startChange < newText.length && oldText[startChange] == newText[startChange]) {
            startChange++
        }

        val updated = mutableListOf<RichSpan>()
        val textLength = newText.length

        for (span in spans) {
            if (span.end <= startChange) {
                if (span.end <= textLength) updated.add(span)
            } else if (span.start >= startChange + max(0, -delta)) {
                val newStart = (span.start + delta).coerceIn(0, textLength)
                val newEnd = (span.end + delta).coerceIn(0, textLength)
                if (newEnd > newStart) {
                    updated.add(span.copy(start = newStart, end = newEnd))
                }
            } else {
                val newStart = span.start.coerceIn(0, textLength)
                val newEnd = (span.end + delta).coerceIn(0, textLength)
                if (newEnd > newStart) {
                    updated.add(span.copy(start = newStart, end = newEnd))
                }
            }
        }

        if (delta > 0 && pendingTypes.isNotEmpty()) {
            for (pType in pendingTypes) {
                val spanStart = startChange
                val spanEnd = startChange + delta
                if (spanEnd > spanStart) {
                    updated.add(RichSpan(pType, spanStart, spanEnd))
                }
            }
        }

        return updated.filter { it.isValid(textLength) }.sortedBy { it.start }
    }

    /**
     * Builds VisualTransformation with SpanStyles for pure WYSIWYG editing.
     * ZERO raw markdown symbols are shown.
     */
    fun createVisualTransformation(
        spans: List<RichSpan>,
        textColor: Color,
        accentColor: Color,
        isDark: Boolean,
        baseFontSizeSp: Float
    ): VisualTransformation {
        val highlightBg = if (isDark) Color(0xFF63520A) else Color(0xFFFFF176)
        val highlightFg = if (isDark) Color(0xFFFFFAEB) else Color(0xFF212121)
        val codeBg = if (isDark) Color(0xFF282C34) else Color(0xFFE4E8EE)
        val codeFg = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1F2328)
        val dividerColor = if (isDark) Color(0xFF3B4252) else Color(0xFFD8DEE9)
        val tableBg = if (isDark) Color(0xFF1E222A) else Color(0xFFF1F5F9)
        val calloutBlueBg = if (isDark) Color(0xFF132F4C) else Color(0xFFE0F2FE)
        val calloutAmberBg = if (isDark) Color(0xFF3E2723) else Color(0xFFFEF3C7)
        val calloutRedBg = if (isDark) Color(0xFF3B1219) else Color(0xFFFEE2E2)
        val calloutGreenBg = if (isDark) Color(0xFF0F3822) else Color(0xFFDCFCE7)
        val calloutPurpleBg = if (isDark) Color(0xFF2E1065) else Color(0xFFF3E8FF)

        return VisualTransformation { text ->
            val raw = text.text
            val len = raw.length
            val annotated = buildAnnotatedString {
                append(raw)

                // 1. Line-level modern notes styling
                var lineStart = 0
                val lines = raw.split('\n')
                for (line in lines) {
                    val lineEnd = lineStart + line.length
                    val trimmed = line.trim()
                    when {
                        // Horizontal divider line
                        trimmed.startsWith("───") || trimmed.startsWith("━━━") || trimmed.startsWith("⎯⎯⎯") ||
                                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                            addStyle(
                                SpanStyle(
                                    color = dividerColor,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                lineStart,
                                lineEnd
                            )
                        }
                        // Callout boxes
                        trimmed.startsWith("💡 Note:") || trimmed.startsWith("💡") -> {
                            addStyle(
                                SpanStyle(
                                    background = calloutBlueBg,
                                    color = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1),
                                    fontWeight = FontWeight.Medium
                                ),
                                lineStart,
                                lineEnd
                            )
                        }
                        trimmed.startsWith("⚠️ Warning:") || trimmed.startsWith("⚠️") -> {
                            addStyle(
                                SpanStyle(
                                    background = calloutAmberBg,
                                    color = if (isDark) Color(0xFFFDE68A) else Color(0xFFB45309),
                                    fontWeight = FontWeight.Medium
                                ),
                                lineStart,
                                lineEnd
                            )
                        }
                        trimmed.startsWith("📌 Important:") || trimmed.startsWith("📌") -> {
                            addStyle(
                                SpanStyle(
                                    background = calloutRedBg,
                                    color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                                    fontWeight = FontWeight.Medium
                                ),
                                lineStart,
                                lineEnd
                            )
                        }
                        trimmed.startsWith("🚀 Tip:") || trimmed.startsWith("🚀") || trimmed.startsWith("✅ Success:") || trimmed.startsWith("✅") -> {
                            addStyle(
                                SpanStyle(
                                    background = calloutGreenBg,
                                    color = if (isDark) Color(0xFF86EFAC) else Color(0xFF15803D),
                                    fontWeight = FontWeight.Medium
                                ),
                                lineStart,
                                lineEnd
                            )
                        }
                        trimmed.startsWith("ℹ️ Info:") || trimmed.startsWith("ℹ️") -> {
                            addStyle(
                                SpanStyle(
                                    background = calloutPurpleBg,
                                    color = if (isDark) Color(0xFFD8B4FE) else Color(0xFF7E22CE),
                                    fontWeight = FontWeight.Medium
                                ),
                                lineStart,
                                lineEnd
                            )
                        }
                        // Tables
                        trimmed.startsWith("┌") || trimmed.startsWith("│") || trimmed.startsWith("├") || trimmed.startsWith("└") -> {
                            addStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (baseFontSizeSp * 0.88f).sp,
                                    background = tableBg,
                                    color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
                                ),
                                lineStart,
                                lineEnd
                            )
                        }
                        // Code Blocks
                        trimmed.startsWith("┌── Code:") || trimmed.startsWith("└────") || trimmed.startsWith("```") -> {
                            addStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (baseFontSizeSp * 0.90f).sp,
                                    background = codeBg,
                                    color = accentColor,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                lineStart,
                                lineEnd
                            )
                        }
                        // Math Formulas
                        trimmed.startsWith("∑ Formula:") || trimmed.startsWith("∑") -> {
                            addStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontStyle = FontStyle.Italic,
                                    color = if (isDark) Color(0xFFF472B6) else Color(0xFFDB2777),
                                    fontWeight = FontWeight.Medium
                                ),
                                lineStart,
                                lineEnd
                            )
                        }
                        // Embed Links
                        trimmed.startsWith("▶️ YouTube:") || trimmed.startsWith("🎵 Audio:") || trimmed.startsWith("🎨 Figma:") ||
                                trimmed.startsWith("💻 CodePen:") || trimmed.startsWith("🔗") -> {
                            addStyle(
                                SpanStyle(
                                    color = accentColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                ),
                                lineStart,
                                lineEnd
                            )
                        }
                    }
                    lineStart = lineEnd + 1
                }

                // 2. Explicit Spans styling
                for (span in spans) {
                    val s = span.start.coerceIn(0, len)
                    val e = span.end.coerceIn(0, len)
                    if (s >= e) continue

                    val style = when (span.type) {
                        RichSpanType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                        RichSpanType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                        RichSpanType.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                        RichSpanType.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                        RichSpanType.HIGHLIGHT -> SpanStyle(background = highlightBg, color = highlightFg)
                        RichSpanType.CODE -> SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontStyle = FontStyle.Italic,
                            background = codeBg,
                            color = codeFg
                        )
                        RichSpanType.SUBSCRIPT -> SpanStyle(
                            baselineShift = BaselineShift.Subscript,
                            fontSize = (baseFontSizeSp * 0.78f).sp
                        )
                        RichSpanType.SUPERSCRIPT -> SpanStyle(
                            baselineShift = BaselineShift.Superscript,
                            fontSize = (baseFontSizeSp * 0.78f).sp
                        )
                        RichSpanType.HEADING_1 -> SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = (baseFontSizeSp * 1.35f).sp,
                            color = accentColor
                        )
                        RichSpanType.HEADING_2 -> SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = (baseFontSizeSp * 1.25f).sp,
                            color = accentColor
                        )
                        RichSpanType.HEADING_3 -> SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = (baseFontSizeSp * 1.15f).sp,
                            color = accentColor
                        )
                        RichSpanType.HEADING_4 -> SpanStyle(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (baseFontSizeSp * 1.08f).sp,
                            color = accentColor
                        )
                        RichSpanType.HEADING_5 -> SpanStyle(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (baseFontSizeSp * 1.02f).sp,
                            color = accentColor
                        )
                        RichSpanType.HEADING_6 -> SpanStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = (baseFontSizeSp * 0.95f).sp,
                            color = accentColor
                        )
                        RichSpanType.QUOTE -> SpanStyle(
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.85f)
                        )
                        RichSpanType.LINK -> SpanStyle(
                            color = accentColor,
                            textDecoration = TextDecoration.Underline
                        )
                        RichSpanType.TEXT_COLOR -> SpanStyle(
                            color = accentColor
                        )
                    }
                    addStyle(style, s, e)
                }
            }

            TransformedText(annotated, OffsetMapping.Identity)
        }
    }

    /**
     * Builds an AnnotatedString from plain text + spans for Note Cards & Previews.
     */
    fun toAnnotatedString(
        plainText: String,
        spans: List<RichSpan>,
        textColor: Color,
        accentColor: Color,
        isDark: Boolean,
        baseFontSizeSp: Float = 14f
    ): AnnotatedString {
        if (plainText.isEmpty()) return AnnotatedString("")
        val highlightBg = if (isDark) Color(0xFF63520A) else Color(0xFFFFF176)
        val highlightFg = if (isDark) Color(0xFFFFFAEB) else Color(0xFF212121)
        val codeBg = if (isDark) Color(0xFF282C34) else Color(0xFFE4E8EE)
        val codeFg = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1F2328)
        val dividerColor = if (isDark) Color(0xFF3B4252) else Color(0xFFD8DEE9)
        val tableBg = if (isDark) Color(0xFF1E222A) else Color(0xFFF1F5F9)
        val calloutBlueBg = if (isDark) Color(0xFF132F4C) else Color(0xFFE0F2FE)
        val calloutAmberBg = if (isDark) Color(0xFF3E2723) else Color(0xFFFEF3C7)
        val calloutRedBg = if (isDark) Color(0xFF3B1219) else Color(0xFFFEE2E2)
        val calloutGreenBg = if (isDark) Color(0xFF0F3822) else Color(0xFFDCFCE7)
        val calloutPurpleBg = if (isDark) Color(0xFF2E1065) else Color(0xFFF3E8FF)

        val len = plainText.length
        return buildAnnotatedString {
            append(plainText)

            // 1. Line-level styling for preview
            var lineStart = 0
            val lines = plainText.split('\n')
            for (line in lines) {
                val lineEnd = lineStart + line.length
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("───") || trimmed.startsWith("━━━") || trimmed.startsWith("⎯⎯⎯") ||
                            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                        addStyle(
                            SpanStyle(
                                color = dividerColor,
                                fontWeight = FontWeight.Bold
                            ),
                            lineStart,
                            lineEnd
                        )
                    }
                    trimmed.startsWith("💡 Note:") || trimmed.startsWith("💡") -> {
                        addStyle(
                            SpanStyle(
                                background = calloutBlueBg,
                                color = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1),
                                fontWeight = FontWeight.Medium
                            ),
                            lineStart,
                            lineEnd
                        )
                    }
                    trimmed.startsWith("⚠️ Warning:") || trimmed.startsWith("⚠️") -> {
                        addStyle(
                            SpanStyle(
                                background = calloutAmberBg,
                                color = if (isDark) Color(0xFFFDE68A) else Color(0xFFB45309),
                                fontWeight = FontWeight.Medium
                            ),
                            lineStart,
                            lineEnd
                        )
                    }
                    trimmed.startsWith("📌 Important:") || trimmed.startsWith("📌") -> {
                        addStyle(
                            SpanStyle(
                                background = calloutRedBg,
                                color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                                fontWeight = FontWeight.Medium
                            ),
                            lineStart,
                            lineEnd
                        )
                    }
                    trimmed.startsWith("🚀 Tip:") || trimmed.startsWith("🚀") || trimmed.startsWith("✅ Success:") || trimmed.startsWith("✅") -> {
                        addStyle(
                            SpanStyle(
                                background = calloutGreenBg,
                                color = if (isDark) Color(0xFF86EFAC) else Color(0xFF15803D),
                                fontWeight = FontWeight.Medium
                            ),
                            lineStart,
                            lineEnd
                        )
                    }
                    trimmed.startsWith("ℹ️ Info:") || trimmed.startsWith("ℹ️") -> {
                        addStyle(
                            SpanStyle(
                                background = calloutPurpleBg,
                                color = if (isDark) Color(0xFFD8B4FE) else Color(0xFF7E22CE),
                                fontWeight = FontWeight.Medium
                            ),
                            lineStart,
                            lineEnd
                        )
                    }
                    trimmed.startsWith("┌") || trimmed.startsWith("│") || trimmed.startsWith("├") || trimmed.startsWith("└") -> {
                        addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = (baseFontSizeSp * 0.88f).sp,
                                background = tableBg,
                                color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
                            ),
                            lineStart,
                            lineEnd
                        )
                    }
                    trimmed.startsWith("┌── Code:") || trimmed.startsWith("└────") || trimmed.startsWith("```") -> {
                        addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = (baseFontSizeSp * 0.90f).sp,
                                background = codeBg,
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold
                            ),
                            lineStart,
                            lineEnd
                        )
                    }
                    trimmed.startsWith("∑ Formula:") || trimmed.startsWith("∑") -> {
                        addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontStyle = FontStyle.Italic,
                                color = if (isDark) Color(0xFFF472B6) else Color(0xFFDB2777),
                                fontWeight = FontWeight.Medium
                            ),
                            lineStart,
                            lineEnd
                        )
                    }
                }
                lineStart = lineEnd + 1
            }

            for (span in spans) {
                val s = span.start.coerceIn(0, len)
                val e = span.end.coerceIn(0, len)
                if (s >= e) continue

                val style = when (span.type) {
                    RichSpanType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    RichSpanType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    RichSpanType.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                    RichSpanType.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    RichSpanType.HIGHLIGHT -> SpanStyle(background = highlightBg, color = highlightFg)
                    RichSpanType.CODE -> SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontStyle = FontStyle.Italic,
                        background = codeBg,
                        color = codeFg
                    )
                    RichSpanType.SUBSCRIPT -> SpanStyle(
                        baselineShift = BaselineShift.Subscript,
                        fontSize = (baseFontSizeSp * 0.78f).sp
                    )
                    RichSpanType.SUPERSCRIPT -> SpanStyle(
                        baselineShift = BaselineShift.Superscript,
                        fontSize = (baseFontSizeSp * 0.78f).sp
                    )
                    RichSpanType.HEADING_1 -> SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = (baseFontSizeSp * 1.3f).sp,
                        color = accentColor
                    )
                    RichSpanType.HEADING_2 -> SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = (baseFontSizeSp * 1.2f).sp,
                        color = accentColor
                    )
                    RichSpanType.HEADING_3 -> SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = (baseFontSizeSp * 1.12f).sp,
                        color = accentColor
                    )
                    RichSpanType.HEADING_4 -> SpanStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (baseFontSizeSp * 1.05f).sp,
                        color = accentColor
                    )
                    RichSpanType.HEADING_5 -> SpanStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (baseFontSizeSp * 1.0f).sp,
                        color = accentColor
                    )
                    RichSpanType.HEADING_6 -> SpanStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = (baseFontSizeSp * 0.92f).sp,
                        color = accentColor
                    )
                    RichSpanType.QUOTE -> SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = textColor.copy(alpha = 0.85f)
                    )
                    RichSpanType.LINK -> SpanStyle(
                        color = accentColor,
                        textDecoration = TextDecoration.Underline
                    )
                    RichSpanType.TEXT_COLOR -> SpanStyle(
                        color = accentColor
                    )
                }
                addStyle(style, s, e)
            }
        }
    }
}
