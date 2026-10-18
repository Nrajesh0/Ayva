package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.FocusSchedule
import org.json.JSONObject

object AssistantRoutineDialogue {
    fun hasDraft(state: AssistantSession) = state.expected == "feature_value" && state.featureState?.optJSONObject("routineDraft") != null
    suspend fun answer(context: Context, app: FocusApplication, state: AssistantSession, input: String, newIntent: Boolean = false): FeatureTurn? {
        val text = input.trim(); val lower = text.lowercase().trimEnd('.', '!', '?')
        if (hasDraft(state)) {
            if (lower == "show apps") return AssistantFeatures.appList(context)
            if (newIntent) return null
            val draft = state.featureState!!.getJSONObject("routineDraft")
            val field = draft.getString("field")
            val schedule = RoutineJson.decode(draft.getJSONObject("schedule"))
            val changed = when (field) {
                "name" -> text.removeSurrounding("\"").takeIf { it.isNotBlank() && it.length <= 80 && it.none(Char::isISOControl) }?.let { schedule.copy(name = it) }
                "days" -> AssistantFeatureLanguage.days(text)?.let { schedule.copy(daysOfWeek = it) }
                "time" -> AssistantFeatureLanguage.window(text)?.let { schedule.copy(startHour = it.first / 60, startMinute = it.first % 60, endHour = it.second / 60, endMinute = it.second % 60) }
                "apps" -> AssistantFeatureLanguage.resolveApps(context, text)?.takeIf { it.isNotEmpty() }?.let { schedule.copy(appsToBlock = it.joinToString(",")) }
                "mode" -> when (lower) { "hard", "strict" -> schedule.copy(mode = "HARD"); "soft" -> schedule.copy(mode = "SOFT"); else -> null }
                "field" -> {
                    val selected = when (lower) { "name", "title" -> "name"; "days", "weekdays" -> "days"; "time", "hours" -> "time"; "apps", "applications" -> "apps"; "mode" -> "mode"; else -> return FeatureTurn.Reply(hint("field")) }
                    draft.put("field", selected); return FeatureTurn.Reply(hint(selected))
                }
                else -> null
            } ?: return FeatureTurn.Reply(hint(field))
            draft.put("schedule", RoutineJson.encode(changed))
            val rest = draft.optString("remaining").split(',').filter { it.isNotBlank() }
            if (rest.isNotEmpty()) {
                draft.put("field", rest.first()).put("remaining", rest.drop(1).joinToString(","))
                return FeatureTurn.Reply(hint(rest.first()))
            }
            state.featureState!!.remove("routineDraft")
            val before = draft.optJSONObject("before")?.let(RoutineJson::decode)
            val overlaps = app.database.scheduleDao().getAllSchedulesSync().filter { it.id != changed.id && AssistantFeatureLanguage.overlaps(changed, it) }
            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            return FeatureTurn.Preview(listOf(AssistantAction.SaveRoutine(changed, before)), "Save routine **${changed.name}**?\nDays: ${changed.daysOfWeek.split(',').map { dayNames[it.toInt() - 1] }.joinToString(", ")}\nTime: ${AssistantFeatureLanguage.timeText(changed.startHour * 60 + changed.startMinute, changed.endHour * 60 + changed.endMinute)} (device local time)\nEnabled: ${changed.isEnabled}. Mode: ${changed.mode}; apps: ${changed.appsToBlock}\n${if (changed.mode == "HARD") "An active strict routine cannot be edited or undone through chat.\n" else ""}${if (overlaps.isEmpty()) "No overlapping enabled routines." else "Overlaps: " + overlaps.joinToString { it.name } + ". Both sets of rules can apply."}")
        }
        val create = Regex("(?i)^(?:create|add) (?:a )?routine(?: (.+))?$").matchEntire(text)
        val edit = Regex("(?i)^(?:edit|change) routine (.+)$").matchEntire(text)
        if (create == null && edit == null) return null
        var before: FocusSchedule? = null
        if (edit != null) {
            val target = edit.groupValues[1]
            val matches = app.database.scheduleDao().getAllSchedulesSync().filter { if (target.startsWith("id:")) it.id == target.substringAfter(':').toIntOrNull() else it.name.equals(target, true) }
            if (matches.size != 1) return FeatureTurn.Reply("Name one exact routine or use its id. Nothing has changed.")
            before = matches.single()
            if (!AssistantActionExecutor.canDisableRoutine(context, before)) return FeatureTurn.Reply("An active strict routine or focus session cannot be edited from chat.")
        }
        val name = create?.groupValues?.get(1).orEmpty().trim()
        if (name.length > 80 || name.any(Char::isISOControl)) return FeatureTurn.Reply("Use a routine name of 1–80 characters without control characters.")
        val schedule = before ?: FocusSchedule(name = name, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0, daysOfWeek = "2,3,4,5,6", appsToBlock = "")
        val fields = if (before != null) listOf("field") else if (name.isBlank()) listOf("name", "days", "time", "apps", "mode") else listOf("days", "time", "apps", "mode")
        state.clearPending(); state.switchTopic("routines"); state.expected = "feature_value"
        if (state.featureState == null) state.featureState = JSONObject()
        state.featureState!!.put("routineDraft", JSONObject().put("schedule", RoutineJson.encode(schedule)).put("before", before?.let(RoutineJson::encode) ?: JSONObject.NULL).put("field", fields.first()).put("remaining", fields.drop(1).joinToString(",")))
        return FeatureTurn.Reply(hint(fields.first()))
    }
    fun hint(field: String) = when (field) {
        "name" -> "What should the routine be called?"
        "days" -> "Which days? Say ‘weekdays’, ‘weekends’, ‘every day’, or list days such as ‘Monday, Wednesday’."
        "time" -> "What time window? Use ‘7pm to 9pm’ or ‘22:00 to 06:00’. Overnight windows are supported."
        "apps" -> "Which installed apps? Separate exact names or package names with commas. Say ‘show apps’ to find package names."
        "mode" -> "Should this routine use HARD (strict) or SOFT mode?"
        else -> "Which field should change: name, days, time, apps or mode?"
    }
}
