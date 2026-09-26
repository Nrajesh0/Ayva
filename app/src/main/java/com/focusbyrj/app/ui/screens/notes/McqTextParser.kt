package com.focusbyrj.app.ui.screens.notes

/** Strips common markdown artefacts (**bold**, *italic*, __under__, `code`) from a string. */
private fun String.stripMarkdown(): String =
    replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        .replace(Regex("""\*(.+?)\*"""), "$1")
        .replace(Regex("""__(.+?)__"""), "$1")
        .replace(Regex("""_(.+?)_"""), "$1")
        .replace(Regex("""`(.+?)`"""), "$1")

/**
 * Smart MCQ & Question Parser for Teachers & Students.
 *
 * Handles:
 *  - Multi-line pasted text (each option on its own line)
 *  - Single-line dense pasted text (all options crammed onto one line)
 *  - Multiple questions in one paste (blank-line or "Q1." separated)
 *  - Letter options: A) / (A) / A. / [A]
 *  - Numeric options: 1) / (1) / 1. / [1]
 *  - Answer/solution sections keyed by Ans/Answer/Sol/Solution/Correct
 *  - No markdown output - plain readable text (no ** symbols)
 */
object McqTextParser {

    data class ParsedMcq(
        val question: String,
        val options: List<Pair<String, String>>, // (label, text)
        val answer: String?,
        val explanation: String?,
        val rawOriginal: String
    ) {
        /** Formats the parsed MCQ into clean plain text with no markdown. */
        fun toFormattedText(optionStyle: String = "ABCD"): String {
            val sb = StringBuilder()

            val cleanQ = question.trim().stripMarkdown()
            if (cleanQ.isNotEmpty()) {
                sb.append(cleanQ).append("\n\n")
            }

            options.forEachIndexed { index, pair ->
                val label = when (optionStyle) {
                    "1234"  -> "${index + 1}."
                    "ROMAN" -> "${toRoman(index + 1)}."
                    else    -> "${'A' + index}."
                }
                sb.append(label).append(" ").append(pair.second.trim().stripMarkdown()).append("\n")
            }

            if (!answer.isNullOrBlank() || !explanation.isNullOrBlank()) {
                sb.append("\n")
                if (!answer.isNullOrBlank()) {
                    val cleanAns = resolveAnswer(answer.trim().stripMarkdown(), options, optionStyle)
                    sb.append("Answer: ").append(cleanAns).append("\n")
                }
                if (!explanation.isNullOrBlank()) {
                    sb.append("Explanation: ").append(explanation.trim().stripMarkdown()).append("\n")
                }
            }

            return sb.toString().trimEnd()
        }

        private fun resolveAnswer(raw: String, opts: List<Pair<String, String>>, style: String): String {
            val m = Regex("""^\(?([A-Da-d1-4])\)?[:.)]?\s*(.*)""").find(raw) ?: return raw
            val key = m.groupValues[1].uppercase()
            val rest = m.groupValues[2].trim()
            val idx = when (key) { "A","1" -> 0; "B","2" -> 1; "C","3" -> 2; "D","4" -> 3; else -> -1 }
            val label = when {
                idx < 0 -> "($key)"
                style == "1234"  -> "${idx + 1}."
                style == "ROMAN" -> "${toRoman(idx + 1)}."
                else -> "${'A' + idx}."
            }
            return if (rest.isNotEmpty()) "$label $rest"
            else if (idx in opts.indices) "$label ${opts[idx].second.trim().stripMarkdown()}"
            else label
        }

        private fun toRoman(n: Int) = when (n) {
            1 -> "i"; 2 -> "ii"; 3 -> "iii"; 4 -> "iv"; 5 -> "v"; 6 -> "vi"; else -> n.toString()
        }
    }

    // -------------------------------------------------------------------------
    // Regex patterns
    // -------------------------------------------------------------------------

    private val ANSWER_REGEX = Regex(
        """(?im)(?:^|[\n\r])[ \t]*(?:ans(?:wer)?|correct(?:\s+(?:option|answer|ans))?|key|sol(?:ution)?|right(?:\s+(?:option|answer))?)[ \t]*[:=\-\u2013\u2014][ \t]*(.+?)[ \t]*$"""
    )
    private val EXPLANATION_REGEX = Regex(
        """(?im)(?:^|[\n\r])[ \t]*(?:exp(?:lanation)?|rationale|reason|note)[ \t]*[:=\-\u2013\u2014][ \t]*(.+)$""",
        RegexOption.DOT_MATCHES_ALL
    )

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returns true if text likely contains MCQ options (needs at least A/1 and B/2). */
    fun isLikelyMcq(text: String): Boolean {
        if (text.length < 10) return false
        return collectMarkers(text).size >= 2
    }

    /**
     * Parses raw text and returns a ParsedMcq, or null if not parseable as MCQ.
     * Handles both single-line and multi-line format.
     */
    fun parse(rawText: String): ParsedMcq? {
        val text = rawText.replace("\r\n", "\n").trim()
        if (text.isEmpty()) return null

        var body = text.stripMarkdown()

        // Extract explanation first (greediest match at end)
        var explanationText: String? = null
        val expMatch = EXPLANATION_REGEX.find(body)
        if (expMatch != null) {
            explanationText = expMatch.groupValues[1].trim()
            body = body.substring(0, expMatch.range.first).trim()
        }

        // Extract answer line
        var answerText: String? = null
        val ansMatch = ANSWER_REGEX.find(body)
        if (ansMatch != null) {
            answerText = ansMatch.groupValues[1].trim()
            body = body.substring(0, ansMatch.range.first).trim()
        }

        val markers = collectMarkers(body)
        if (markers.size < 2) return null

        val questionText = body.substring(0, markers.first().startIndex).trim()

        val optionsList = markers.mapIndexed { i, cur ->
            val contentStart = cur.endIndex
            val contentEnd = if (i + 1 < markers.size) markers[i + 1].startIndex else body.length
            val content = if (contentStart < contentEnd) body.substring(contentStart, contentEnd).trim() else ""
            Pair(cur.label, content)
        }

        return ParsedMcq(
            question = questionText,
            options = optionsList,
            answer = answerText,
            explanation = explanationText,
            rawOriginal = rawText
        )
    }

    /**
     * Parses a paste that may contain multiple questions.
     * Returns a list of ParsedMcq, one per question found.
     */
    fun parseAll(rawText: String): List<ParsedMcq> {
        val text = rawText.replace("\r\n", "\n").trim()
        if (text.isEmpty()) return emptyList()
        return splitIntoQuestionBlocks(text).mapNotNull { parse(it) }
    }

    /**
     * Formats raw text if it is an MCQ (or multiple MCQs). Returns rawText untouched if not.
     */
    fun formatIfMcq(rawText: String, style: String = "ABCD"): String {
        val all = parseAll(rawText)
        if (all.isEmpty()) return rawText
        return all.joinToString("\n\n") { it.toFormattedText(style) }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private data class MarkerMatch(val label: String, val startIndex: Int, val endIndex: Int)

    /**
     * Collects sequential option markers from [text].
     * Works on multi-line text, single-line dense text, and formats like "8 (b) 9(c) 10(d) 11"
     * where option A has no label and subsequent options follow digits directly.
     */
    private fun collectMarkers(text: String): List<MarkerMatch> {
        // Letter markers: (A) / A) / A. — can be preceded by start, newline, whitespace,
        // punctuation, OR a digit (e.g. "9(c)" is valid in many MCQ formats).
        // We rely on filterSequential to eliminate false positives.
        val letterRegex = Regex(
            """(?:(?:^)|(?<=[\n\r])|(?<=[ \t])|(?<=[.!?,;:])|(?<=\d))[ \t]*(?:\(([A-Fa-f])\)|([A-Fa-f])[).:])[ \t]*""",
            RegexOption.MULTILINE
        )
        // Numeric markers: (1) / 1) / 1. — same lookbehind
        val numRegex = Regex(
            """(?:(?:^)|(?<=[\n\r])|(?<=[ \t])|(?<=[.!?,;:])|(?<=\d))[ \t]*(?:\(([1-6])\)|([1-6])[).:])[ \t]*""",
            RegexOption.MULTILINE
        )

        val letterCandidates = letterRegex.findAll(text).map { m ->
            val label = (m.groupValues[1].ifEmpty { m.groupValues[2] }).uppercase()
            MarkerMatch(label, m.range.first, m.range.last + 1)
        }.toList()

        val numCandidates = numRegex.findAll(text).map { m ->
            val label = m.groupValues[1].ifEmpty { m.groupValues[2] }
            MarkerMatch(label, m.range.first, m.range.last + 1)
        }.toList()

        // Prefer letter options if they form at least a 2-marker chain
        val letterChain = filterSequential(letterCandidates, isNumeric = false)
        val numChain = filterSequential(numCandidates, isNumeric = true)

        return when {
            letterChain.size >= numChain.size && letterChain.size >= 2 -> letterChain
            numChain.size >= 2 -> numChain
            else -> emptyList()
        }
    }

    /**
     * Finds the longest consecutive sequential chain (A→B→C→D or 1→2→3→4) in [candidates].
     * Unlike before, this tries ALL possible starting labels, not just A/1, so it handles
     * questions where option A's label is missing (e.g. "8 (b) 9 (c) 10 (d) 11").
     */
    private fun filterSequential(candidates: List<MarkerMatch>, isNumeric: Boolean): List<MarkerMatch> {
        if (candidates.isEmpty()) return emptyList()
        val byLabel = candidates.groupBy { it.label }

        // All unique labels present, sorted
        val sortedLabels = byLabel.keys.sortedBy { k ->
            if (isNumeric) k.toIntOrNull() ?: 99 else k.firstOrNull()?.code ?: 99
        }

        var bestChain = emptyList<MarkerMatch>()

        for (startLabel in sortedLabels) {
            val chain = mutableListOf<MarkerMatch>()
            var cur = startLabel
            while (true) {
                val matches = byLabel[cur] ?: break
                val pick = if (chain.isEmpty()) matches.firstOrNull()
                           else matches.firstOrNull { it.startIndex > chain.last().endIndex }
                chain.add(pick ?: break)
                cur = if (isNumeric) {
                    ((cur.toIntOrNull() ?: break) + 1).toString()
                } else {
                    val next = (cur.firstOrNull()?.plus(1)) ?: break
                    next.toString()
                }
            }
            if (chain.size > bestChain.size) bestChain = chain
        }

        return if (bestChain.size >= 2) bestChain else emptyList()
    }


    /**
     * Splits a multi-question paste into individual question strings.
     * Tries: explicit Q-headers -> double blank lines -> single blank lines -> single block.
     */
    private fun splitIntoQuestionBlocks(text: String): List<String> {
        // Explicit question headers: "Q1." / "1." at line start followed by uppercase letter
        val qHeaderRegex = Regex(
            """(?m)(?:^|\n)(?=[ \t]*(?:Q\.?\s*\d+[.:\s]|\d+[.)]\s+[A-Z]|Question\s+\d+\s*[.:]?))""",
            RegexOption.IGNORE_CASE
        )
        val headerSplit = qHeaderRegex.split(text).map { it.trim() }.filter { it.isNotEmpty() }
        if (headerSplit.size >= 2 && headerSplit.all { isLikelyMcq(it) }) return headerSplit

        // Double blank lines
        val doubleSplit = text.split(Regex("""\n[ \t]*\n[ \t]*\n""")).map { it.trim() }.filter { it.isNotEmpty() }
        if (doubleSplit.size >= 2 && doubleSplit.all { isLikelyMcq(it) }) return doubleSplit

        // Single blank line
        val singleSplit = text.split(Regex("""\n[ \t]*\n""")).map { it.trim() }.filter { it.isNotEmpty() }
        if (singleSplit.size >= 2 && singleSplit.all { isLikelyMcq(it) }) return singleSplit

        return listOf(text)
    }
}
