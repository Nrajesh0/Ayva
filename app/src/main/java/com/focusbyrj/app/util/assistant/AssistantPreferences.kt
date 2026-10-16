package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.util.AppThemeColor
import com.focusbyrj.app.util.AppThemeManager
import com.focusbyrj.app.util.ThemeMode
import com.focusbyrj.app.util.AptitudeManager
import java.util.Locale

data class AssistantPreferenceSpec(
    val key: String, val type: String, val title: String, val aliases: List<String>,
    val default: String, val unit: String = "", val range: IntRange? = null, val values: List<String> = emptyList(),
    val owner: String = "focus_prefs"
)

/** All chat preference writes, including legacy chips, use this validation and side-effect path. */
object AssistantPreferences {
    val specs = listOf(
        AssistantPreferenceSpec("soft_lock_duration", "int", "Soft-mode wait", listOf("soft mode wait timer", "soft mode wait", "wait timer", "wait time", "wait delay"), "10", "seconds", 5..60),
        AssistantPreferenceSpec("soft_unlock_duration", "int", "Soft-mode relief", listOf("soft mode relief", "relief duration", "relief time", "soft unlock duration"), "5", "minutes", 1..60),
        AssistantPreferenceSpec("persistent_reminder_interval", "int", "Reminder interval", listOf("persistent reminder interval", "persistent reminders", "reminder interval"), "15", "minutes", 1..1440),
        AssistantPreferenceSpec("auto_hide_duration_sec", "int", "Bubble auto-hide delay", listOf("bubble auto hide", "auto hide delay", "auto-hide delay", "auto hide duration"), "3", "seconds", 1..300, owner = "bubble_prefs"),
        AssistantPreferenceSpec("app_theme_mode", "string", "App theme", listOf("app theme", "theme mode", "theme"), "system", values = ThemeMode.entries.map { it.id }),
        AssistantPreferenceSpec("app_theme_color", "string", "Accent color", listOf("accent color", "accent colour", "theme color", "theme colour"), "emerald", values = AppThemeColor.entries.map { it.id }),
        AssistantPreferenceSpec("overlay_theme_mode", "string", "Overlay theme", listOf("overlay theme", "block screen theme"), "system", values = ThemeMode.entries.map { it.id }),
        AssistantPreferenceSpec("task_notification_style", "string", "Task notification style", listOf("task notification style", "notification style"), "Both", values = listOf("Banner", "Silent", "Both")),
        AssistantPreferenceSpec("default_start_tab", "string", "Default launch tab", listOf("default launch tab", "default tab", "start tab", "launch tab"), "dashboard", values = listOf("dashboard", "time", "account", "schedules")),
        AssistantPreferenceSpec("bubble_enabled", "boolean", "Floating bubble", listOf("floating bubble", "bubble"), "false", owner = "bubble_prefs"),
        AssistantPreferenceSpec("hide_in_landscape", "boolean", "Hide bubble in landscape", listOf("hide in landscape", "hide bubble in landscape", "landscape hiding"), "true", owner = "bubble_prefs"),
        AssistantPreferenceSpec("vacation_mode", "boolean", "Vacation mode", listOf("vacation mode", "vacation", "freeze streak", "unfreeze streak"), "false"),
        AssistantPreferenceSpec("secure_recents", "boolean", "Screenshot protection", listOf("screenshot protection", "anti screenshot", "secure recents"), "false"),
        AssistantPreferenceSpec("routine_notifications", "boolean", "Routine notifications", listOf("routine notifications", "routine alerts"), "true"),
        AssistantPreferenceSpec("streak_notification_enabled", "boolean", "Practice reminders", listOf("practice reminders", "practice alerts", "streak reminders"), "true"),
        AssistantPreferenceSpec("streak_notification_time", "string", "Practice reminder time", listOf("practice reminder time", "practice alert time", "streak notification time"), "09:00 AM", unit = "clock"),
        AssistantPreferenceSpec("morning_brief_time", "string", "Morning briefing time", listOf("morning briefing time", "morning brief time"), "08:00 AM", unit = "clock"),
        AssistantPreferenceSpec("evening_brief_time", "string", "Evening briefing time", listOf("evening briefing time", "evening brief time"), "08:00 PM", unit = "clock")
    )
    val types = specs.associate { it.key to it.type }
    fun find(query: String): AssistantPreferenceSpec? = specs.flatMap { s -> s.aliases.map { it to s } }
        .filter { (alias, _) -> Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(alias)}(?![\\p{L}\\p{N}])").containsMatchIn(query) }
        .maxByOrNull { it.first.length }?.second

    fun read(context: Context, spec: AssistantPreferenceSpec): String {
        val prefs = context.getSharedPreferences(spec.owner, Context.MODE_PRIVATE)
        return when (spec.type) {
            "boolean" -> prefs.getBoolean(spec.key, spec.default.toBoolean()).toString()
            "int" -> prefs.getInt(spec.key, spec.default.toInt()).toString()
            else -> prefs.getString(spec.key, spec.default) ?: spec.default
        }
    }

    fun validate(key: String, type: String, value: String): String {
        val spec = specs.find { it.key == key } ?: error("That setting isn't editable from chat")
        require(type == spec.type) { "Invalid setting type" }
        return when (type) {
            "boolean" -> value.toBooleanStrictOrNull()?.toString() ?: error("Use on or off")
            "int" -> {
                val number = value.toIntOrNull() ?: error("Use a whole number")
                require(spec.range?.contains(number) == true) { "Use ${spec.range?.first}–${spec.range?.last} ${spec.unit}" }
                number.toString()
            }
            else -> if (spec.unit == "clock") parseClock(value) ?: error("Use a time such as 9:30am or 18:00")
                else spec.values.find { it.equals(value, true) } ?: error("Choose ${spec.values.joinToString()}")
        }
    }

    fun extractValue(spec: AssistantPreferenceSpec, query: String): String? {
        val lower = query.lowercase(Locale.ROOT).trim().trimEnd('.', '?', '!')
        if (spec.type == "boolean") {
            if (Regex("\\b(?:on|enable|activate|true|freeze)\\b").containsMatchIn(lower) &&
                Regex("\\b(?:off|disable|deactivate|false|unfreeze)\\b").containsMatchIn(lower)) return null
            if (Regex("\\b(?:off|disable|deactivate|false|unfreeze)\\b|\\b(?:don't|do not) (?:enable|activate)\\b").containsMatchIn(lower)) return "false"
            if (Regex("\\b(?:on|enable|activate|true|freeze)\\b").containsMatchIn(lower)) return "true"
            if (lower in setOf("yes", "yep", "sure")) return "true"
            if (lower in setOf("no", "nope")) return "false"
            return null
        }
        if (spec.unit == "clock") return parseClock(lower)
        if (spec.type == "string") return spec.values.filter { Regex("\\b${Regex.escape(it.lowercase())}\\b").containsMatchIn(lower) }.singleOrNull()
        val matches = Regex("(?<![\\p{L}\\p{N}])-?\\d+(?:\\.\\d+)?\\s*(?:seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)?\\b").findAll(lower).toList()
        if (matches.size != 1) return null
        val match = matches.single().value
        val number = Regex("-?\\d+(?:\\.\\d+)?").find(match)?.value?.toDoubleOrNull() ?: return null
        val suppliedUnit = match.removePrefix(Regex("-?\\d+(?:\\.\\d+)?").find(match)!!.value).trim()
        val factor = when {
            suppliedUnit.startsWith("h") -> 3600.0
            suppliedUnit.startsWith("m") -> 60.0
            suppliedUnit.startsWith("s") -> 1.0
            spec.unit == "minutes" -> 60.0
            else -> 1.0
        }
        val converted = number * factor / if (spec.unit == "minutes") 60.0 else 1.0
        require(converted.isFinite() && converted % 1.0 == 0.0 && converted in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) { "Use a whole number of ${spec.unit}" }
        return validate(spec.key, spec.type, converted.toInt().toString())
    }

    fun apply(context: Context, key: String, type: String, value: String): Boolean {
        val validated = validate(key, type, value)
        // Mirror established legacy preference locations, with one validated value and common side effects.
        for (name in listOf("focus_prefs", "bubble_prefs")) {
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            when (type) {
                "boolean" -> editor.putBoolean(key, validated.toBooleanStrict())
                "int" -> editor.putInt(key, validated.toInt())
                else -> editor.putString(key, validated)
            }
            check(editor.commit()) { "Couldn't save setting" }
        }
        when (key) {
            "app_theme_color", "app_theme_mode", "overlay_theme_mode" -> AppThemeManager.init(context)
            "vacation_mode" -> AptitudeManager.setVacationMode(context, validated.toBooleanStrict())
            "bubble_enabled" -> if (validated == "true") {
                com.focusbyrj.app.service.BubbleService.clearSnooze(context)
                com.focusbyrj.app.service.BubbleService.startIfEnabled(context, ignoreSnooze = true)
            } else context.stopService(android.content.Intent(context, com.focusbyrj.app.service.BubbleService::class.java))
            "streak_notification_time", "streak_notification_enabled" -> com.focusbyrj.app.service.AptitudeReminderReceiver.scheduleDrillReminders(context)
            "morning_brief_time", "evening_brief_time" -> com.focusbyrj.app.service.DailySummaryReceiver.scheduleDailySummaries(context)
        }
        return true
    }

    private fun parseClock(input: String): String? {
        if (Regex("-\\s*\\d").containsMatchIn(input)) return null
        val matches = Regex("(?i)(?<!\\d)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?(?![\\d\\p{L}])").findAll(input).toList()
        if (matches.size != 1) return null
        val match = matches.single()
        var hour = match.groupValues[1].toInt(); val minute = match.groupValues[2].ifBlank { "0" }.toInt()
        val meridiem = match.groupValues[3].lowercase()
        if (minute !in 0..59 || (meridiem.isEmpty() && hour !in 0..23) || (meridiem.isNotEmpty() && hour !in 1..12)) return null
        if (meridiem.isNotEmpty()) hour = hour % 12 + if (meridiem == "pm") 12 else 0
        return String.format(Locale.US, "%02d:%02d %s", if (hour % 12 == 0) 12 else hour % 12, minute, if (hour < 12) "AM" else "PM")
    }
}
