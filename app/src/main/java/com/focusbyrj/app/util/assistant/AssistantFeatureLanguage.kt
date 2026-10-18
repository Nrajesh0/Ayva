package com.focusbyrj.app.util.assistant

import android.content.Context
import android.content.Intent
import com.focusbyrj.app.data.FocusSchedule
import java.util.Calendar

sealed class FeatureTurn {
    data class Reply(val text: String, val actions: List<com.focusbyrj.app.util.TalkAction> = emptyList(), val redirect: String? = null, val settingKey: String? = null, val topicId: String? = null) : FeatureTurn()
    data class Preview(val actions: List<AssistantAction>, val text: String) : FeatureTurn()
}

object AssistantFeatureLanguage {
    fun clock(raw: String): Int? {
        val match = Regex("(?i)^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$").matchEntire(raw.trim()) ?: return null
        var hour = match.groupValues[1].toInt(); val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
        val half = match.groupValues[3].lowercase()
        if (minute !in 0..59 || (half.isEmpty() && hour !in 0..23) || (half.isNotEmpty() && hour !in 1..12)) return null
        if (half.isNotEmpty()) hour = hour % 12 + if (half == "pm") 12 else 0
        return hour * 60 + minute
    }
    fun window(raw: String): Pair<Int, Int>? {
        val parts = raw.trim().removePrefix("from ").split(Regex("(?i)\\s+(?:to|until)\\s+|\\s*[–—]\\s*|\\s+-\\s+"))
        if (parts.size != 2) return null
        val start = clock(parts[0]) ?: return null; val end = clock(parts[1]) ?: return null
        return (start to end).takeIf { start != end }
    }
    fun days(raw: String): String? {
        val text = raw.lowercase().trim()
        val named = mapOf("sunday" to 1, "monday" to 2, "tuesday" to 3, "wednesday" to 4, "thursday" to 5, "friday" to 6, "saturday" to 7,
            "sun" to 1, "mon" to 2, "tue" to 3, "wed" to 4, "thu" to 5, "fri" to 6, "sat" to 7)
        return when (text) {
            "weekdays" -> "2,3,4,5,6"
            "weekends" -> "1,7"
            "daily", "every day", "everyday" -> "1,2,3,4,5,6,7"
            else -> text.split(Regex("\\s*,\\s*|\\s+and\\s+")).map { named[it.trim()] ?: return null }.distinct().sorted().joinToString(",")
        }
    }
    fun timeText(start: Int, end: Int) = "%02d:%02d–%02d:%02d%s".format(start / 60, start % 60, end / 60, end % 60, if (end < start) " (overnight)" else "")
    fun overlaps(a: FocusSchedule, b: FocusSchedule): Boolean {
        fun slots(s: FocusSchedule): Set<Int> {
            val start = s.startHour * 60 + s.startMinute; val end = s.endHour * 60 + s.endMinute
            val length = if (end >= start) end - start else 1440 - start + end
            return s.daysOfWeek.split(',').mapNotNull(String::toIntOrNull).flatMap { day -> (0..length).map { ((day - 1) * 1440 + start + it) % 10080 } }.toSet()
        }
        val other = slots(b)
        return a.isEnabled && b.isEnabled && slots(a).any { it in other }
    }
    fun apps(context: Context): List<Pair<String, String>> {
        return com.focusbyrj.app.util.AppCatalog.load(context).map { it.packageName to it.appName }
    }
    fun resolveApps(context: Context, input: String): List<String>? {
        val catalog = apps(context)
        val pieces = input.split(Regex("\\s*,\\s*|\\s+and\\s+")).map(String::trim)
        if (pieces.isEmpty() || pieces.size > 50) return null
        return pieces.map { part ->
            if (part.contains('.') && runCatching { AssistantFeatureActions.requireSafePackage(context, part) }.isSuccess) part
            else catalog.filter { it.second.equals(part, true) }.singleOrNull()?.first ?: return null
        }.distinct()
    }
    fun isRequest(text: String) = Regex("(?i)^(?:create (?:a )?routine|add (?:a )?routine|edit routine|change routine|show routine|show apps|why is .+ blocked|what blocks |diagnose |check device health|check permissions|why are apps not blocking|why didn.t my reminder arrive|compare today and yesterday|backup status|my backup status|show backup status|compare my screen time|what distracted me|show my usage|restrict that app|set (?:a )?daily limit|look up |lookup |explain (?:idiom|word)|show (?:idiom|word)|bookmark |unbookmark |review (?:my )?missed words|practice missed words|quiz me on missed words|review words|review my mistakes|compare my last|what should i practi[cs]e|show my last mistakes|what can i claim|explain my rewards|my reward balance|make tasks from|preview my transcript|change transcript item|drop transcript item)\\b").containsMatchIn(text)
}
