package com.focusbyrj.app.util.assistant

import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

enum class ChatMode(val label: String, val command: String) {
    CHAT("Chat", ""), PLAN("Plan", "/plan"), TALK("Talk", "/talk"), DRILL("Drill", "/drill");

    companion object {
        fun suggested(text: String): ChatMode? {
            val trimmed = text.trim().lowercase()
            return entries.firstOrNull { it != CHAT && (trimmed == it.label.lowercase() || trimmed.startsWith(it.label.lowercase() + " ") || trimmed == it.command || trimmed.startsWith(it.command + " ")) }
        }

        /** Remove a command token only; preserve content and selection on both sides. */
        fun activate(value: TextFieldValue, mode: ChatMode): TextFieldValue {
            val prefix = Regex("(?i)^\\s*" + Regex.escape(mode.command) + "(?:\\s+|$)").find(value.text)
                ?: return if (value.text.trim().equals(mode.label, true)) TextFieldValue("") else value
            val count = prefix.range.last + 1
            val text = value.text.drop(count)
            return TextFieldValue(text, TextRange((value.selection.start - count).coerceIn(0, text.length), (value.selection.end - count).coerceIn(0, text.length)))
        }
    }
}

object ChatModeStore {
    fun get(context: Context): ChatMode = runCatching {
        ChatMode.valueOf(context.getSharedPreferences("ayva_composer", 0).getString("mode", "CHAT")!!)
    }.getOrDefault(ChatMode.CHAT)
    fun set(context: Context, mode: ChatMode) {
        check(context.getSharedPreferences("ayva_composer", 0).edit().putString("mode", mode.name).commit())
    }
}
