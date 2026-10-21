package com.focusbyrj.app.util.assistant

/** Normalize conversational scaffolding while treating quoted values as opaque user data. */
internal object ConversationLanguage {
    fun space(input: String): String {
        val out = StringBuilder(); var quote: Char? = null; var space = false
        for (c in input.trim()) {
            if (quote != null) { out.append(c); if (c == quote || quote == '“' && c == '”') quote = null; continue }
            if (c == '"' || c == '“' || c == '`') { if (space && out.isNotEmpty()) out.append(' '); space = false; quote = c; out.append(c) }
            else if (c.isWhitespace()) space = true else { if (space && out.isNotEmpty()) out.append(' '); space = false; out.append(if (c == '’') '\'' else c) }
        }
        return out.toString()
    }
    fun normalize(input: String): String = space(input)
        .replaceFirst(Regex("(?i)^let us "), "let's ")
        .replaceFirst(Regex("(?i)^im (?=(?:tired|sad|happy|thinking|working|worried|excited|not)\\b)"), "I'm ")
        .replaceFirst(Regex("(?i)^i dont "), "I don't ")
    fun unquote(input: String): String = input.trim().let { if (it.length >= 2 && (it.first() == '"' && it.last() == '"' || it.first() == '“' && it.last() == '”' || it.first() == '`' && it.last() == '`')) it.substring(1, it.lastIndex) else it }
    fun list(input: String): List<String> {
        val pieces = mutableListOf<String>(); var start = 0; var quote: Char? = null
        input.forEachIndexed { i, c ->
            if (quote != null) { if (c == quote || quote == '“' && c == '”') quote = null }
            else if (c in setOf('"', '“', '`')) quote = c
            else if (c == ',' || c == ';') { input.substring(start, i).trim().takeIf(String::isNotBlank)?.let(pieces::add); start = i + 1 }
        }
        input.substring(start).trim().takeIf(String::isNotBlank)?.let(pieces::add)
        require(pieces.size <= 100) { "A plan can contain at most 100 items" }
        return pieces
    }
}
