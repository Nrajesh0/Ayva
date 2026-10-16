package com.focusbyrj.app.util.assistant

/** Split only explicit action boundaries outside quoted titles; ordinary conjunctions remain text. */
object AssistantCompoundParser {
    fun split(input: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0; var index = 0; var quoted = false
        val boundary = Regex("(?i)(?:\\s+(?:and then|then)\\s+|\\s+and\\s+(?=(?:add|create|remind|buy|call|email|complete|delete|reschedule|make|start|stop|rename|log)\\b))")
        while (index < input.length) {
            val char = input[index]
            if (char == '"' && (index == 0 || input[index - 1] != '\\')) quoted = !quoted
            if (!quoted) {
                val separator = if (char == ';' || char == '\n') 1 else boundary.matchAt(input, index)?.value?.length ?: 0
                if (separator > 0) {
                    input.substring(start, index).trim().takeIf { it.isNotBlank() }?.let(result::add)
                    index += separator; start = index; continue
                }
            }
            index++
        }
        input.substring(start).trim().takeIf { it.isNotBlank() }?.let(result::add)
        return result.ifEmpty { listOf(input) }
    }
}
