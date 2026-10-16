package com.focusbyrj.app.util.command

/** Preserve imperative verbs when forwarding slash aliases into the talk/action engine. */
object AyvaChatCommands {
    fun talkQuery(command: String): String {
        val parts = command.trim().split(Regex("\\s+"), limit = 2)
        val name = parts[0].lowercase().removePrefix("/")
        val argument = parts.getOrNull(1).orEmpty()
        return when (name) {
            "talk", "ask", "guide", "how", "help", "faq", "info", "menu" -> argument.ifBlank { name }
            "routine" -> argument.ifBlank { "routines" }.replace(Regex("^(start|stop)\\s+", RegexOption.IGNORE_CASE), "$1 routine ")
            else -> listOf(name, argument).filter { it.isNotBlank() }.joinToString(" ")
        }
    }
}
