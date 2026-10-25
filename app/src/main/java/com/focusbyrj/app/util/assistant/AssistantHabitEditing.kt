package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.Habit
import org.json.JSONObject

data class HabitEditRequest(val subject: String, val field: HabitField, val value: String?)

/** Explicit habit grammar keeps habit edits separate from similarly named tasks. */
object AssistantHabitEditing {
    fun request(text: String, state: AssistantSession): HabitEditRequest? {
        fun match(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).matchEntire(text.trimEnd('.', '!', '?'))
        match("^(?:set|change) habit (.+?) (interval|window|reminder time)(?: to (.+))?$")?.let {
            val field = when (it.groupValues[2].lowercase()) { "interval" -> HabitField.INTERVAL; "window" -> HabitField.WINDOW; else -> HabitField.REMINDER_TIME }
            return HabitEditRequest(it.groupValues[1], field, it.groupValues[3].ifBlank { null })
        }
        match("^(pause|resume|unpause) (?:my )?habit (.+)$")?.let {
            return HabitEditRequest(it.groupValues[2], HabitField.PAUSED, it.groupValues[1].equals("pause", true).toString())
        }
        match("^(pause|resume|unpause) (?:my )?(.+?) habit$")?.let {
            return HabitEditRequest(it.groupValues[2], HabitField.PAUSED, it.groupValues[1].equals("pause", true).toString())
        }
        if (state.topic == "habits") match("^(pause|resume|unpause) (?:it|that)$")?.let {
            return HabitEditRequest("it", HabitField.PAUSED, it.groupValues[1].equals("pause", true).toString())
        }
        match("^rename habit (.+?)(?: to (.+))?$")?.let { return HabitEditRequest(it.groupValues[1], HabitField.TITLE, it.groupValues[2].ifBlank { null }) }
        match("^(?:set|change) (?:the )?(?:daily )?(?:target|goal) (?:of|for) habit (.+?)(?: to (.+))?$")?.let {
            return HabitEditRequest(it.groupValues[1], HabitField.TARGET, it.groupValues[2].ifBlank { null })
        }
        match("^(?:set|change) habit (.+?)(?:'s)? (?:daily )?(?:target|goal)(?: to (.+))?$")?.let {
            return HabitEditRequest(it.groupValues[1], HabitField.TARGET, it.groupValues[2].ifBlank { null })
        }
        match("^(?:turn|switch) (on|off) reminders? for habit (.+)$")?.let {
            return HabitEditRequest(it.groupValues[2], HabitField.REMINDER, (it.groupValues[1].equals("on", true)).toString())
        }
        match("^(enable|disable) reminders? for habit (.+)$")?.let {
            return HabitEditRequest(it.groupValues[2], HabitField.REMINDER, (it.groupValues[1].equals("enable", true)).toString())
        }
        match("^(archive|restore|unarchive) habit (.+)$")?.let {
            return HabitEditRequest(it.groupValues[2], HabitField.ARCHIVED, it.groupValues[1].equals("archive", true).toString())
        }
        if (state.topic == "habits") {
            match("^(?:set|change) (?:its|the) (interval|window|reminder time)(?: to (.+))?$")?.let {
                val field = when (it.groupValues[1].lowercase()) { "interval" -> HabitField.INTERVAL; "window" -> HabitField.WINDOW; else -> HabitField.REMINDER_TIME }
                return HabitEditRequest("it", field, it.groupValues[2].ifBlank { null })
            }
            match("^(?:turn|switch) (?:its|the) reminders? (on|off)$")?.let { return HabitEditRequest("it", HabitField.REMINDER, it.groupValues[1].equals("on", true).toString()) }
            match("^(enable|disable) (?:its )?reminders?$")?.let { return HabitEditRequest("it", HabitField.REMINDER, it.groupValues[1].equals("enable", true).toString()) }
            match("^rename (?:it|that) to (.+)$")?.let { return HabitEditRequest("it", HabitField.TITLE, it.groupValues[1]) }
            match("^(?:set|change) (?:its|the) (?:daily )?(?:target|goal)(?: to (.+))?$")?.let { return HabitEditRequest("it", HabitField.TARGET, it.groupValues[1].ifBlank { null }) }
            match("^(archive|restore|unarchive) (?:it|that)$")?.let { return HabitEditRequest("it", HabitField.ARCHIVED, it.groupValues[1].equals("archive", true).toString()) }
        }
        return null
    }

    fun value(field: HabitField, raw: String): String? {
        val text = raw.trim()
        return when (field) {
            HabitField.INTERVAL -> Regex("(?i)^(?:every )?(\\d+)(?:\\s*(minutes?|mins?|hours?|hrs?))?$").matchEntire(text)?.let {
                val n = it.groupValues[1].toLongOrNull() ?: return null
                if (n !in 1..1440) return null
                val minutes = n * if (it.groupValues[2].lowercase().startsWith("h")) 60 else 1
                minutes.takeIf { m -> m in 5..1440 }?.toString()
            }
            HabitField.WINDOW -> {
                val canonical = Regex("^(\\d{1,4}),(\\d{1,4})$").matchEntire(text)
                if (canonical != null) { val a = canonical.groupValues[1].toInt(); val b = canonical.groupValues[2].toInt(); text.takeIf { a in 0..1439 && b in 0..1439 && a != b } }
                else AssistantFeatureLanguage.window(text)?.let { "${it.first},${it.second}" }
            }
            HabitField.REMINDER_TIME -> if (text.startsWith("@")) text.drop(1).toIntOrNull()?.takeIf { it in 0..1439 }?.let { "@$it" } else AssistantFeatureLanguage.clock(text.replaceFirst(Regex("(?i)^at "), ""))?.let { "@$it" }
            HabitField.TITLE -> text.removeSurrounding("\"").takeIf { it.isNotBlank() && it.length <= 200 }
            HabitField.TARGET -> Regex("(?i)^(\\d+)(?:\\s+times?)?(?:\\s+(?:a|per)\\s+day|\\s+daily)?$").matchEntire(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..100 }?.toString()
            HabitField.REMINDER, HabitField.ARCHIVED, HabitField.PAUSED -> text.lowercase().takeIf { it in setOf("true", "false") }
        }
    }

    fun hint(field: HabitField) = when (field) {
        HabitField.INTERVAL -> "What interval? Use 5–1440 minutes, for example ‘90 minutes’. This switches the habit to interval reminders."
        HabitField.WINDOW -> "What reminder window? Use ‘8am to 8pm’ or ‘22:00 to 06:00’. Overnight windows are supported."
        HabitField.REMINDER_TIME -> "What daily reminder time? Use ‘9am’ or ‘18:30’. This switches the habit to one fixed daily reminder."
        HabitField.TITLE -> "What should the habit's new name be? Use 1–200 characters."
        HabitField.TARGET -> "What daily target should I use? Choose 1–100 repetitions, for example ‘3 times a day’."
        else -> "Please say whether the setting should be on or off."
    }
}

/** Full snapshots preserve every tracker setting and detect external changes. */
internal object HabitJson {
    fun encode(h: Habit): JSONObject = JSONObject().apply {
        put("id", h.id); put("title", h.title); put("description", h.description); put("icon", h.iconEmoji); put("color", h.colorHex)
        put("type", h.type.name); put("target", h.targetPerDay); put("hours", h.intervalHours); put("minutes", h.intervalMinutes)
        put("startHour", h.windowStartHour); put("startMinute", h.windowStartMinute); put("endHour", h.windowEndHour); put("endMinute", h.windowEndMinute)
        put("reminderHour", h.fixedReminderHour); put("reminderMinute", h.fixedReminderMinute); put("reminder", h.isReminderEnabled)
        put("pausedAt", h.pausedAt ?: JSONObject.NULL); put("pauseHistory", h.pauseHistoryJson)
        put("sound", h.reminderSound); put("created", h.createdAt); put("archived", h.isArchived)
    }
    fun decode(j: JSONObject) = Habit(id = j.getLong("id"), title = j.getString("title"), description = j.getString("description"),
        iconEmoji = j.getString("icon"), colorHex = j.getString("color"), type = com.focusbyrj.app.data.HabitType.valueOf(j.getString("type")),
        targetPerDay = j.getInt("target"), intervalHours = j.getInt("hours"), intervalMinutes = j.getInt("minutes"),
        windowStartHour = j.getInt("startHour"), windowStartMinute = j.getInt("startMinute"), windowEndHour = j.getInt("endHour"), windowEndMinute = j.getInt("endMinute"),
        fixedReminderHour = j.getInt("reminderHour"), fixedReminderMinute = j.getInt("reminderMinute"), isReminderEnabled = j.getBoolean("reminder"),
        reminderSound = j.getString("sound"), createdAt = j.getLong("created"), isArchived = j.getBoolean("archived"), pausedAt = if (j.isNull("pausedAt")) null else j.optLong("pausedAt"), pauseHistoryJson = j.optString("pauseHistory", "[]"))
}
