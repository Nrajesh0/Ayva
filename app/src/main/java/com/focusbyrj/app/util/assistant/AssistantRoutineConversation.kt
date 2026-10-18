package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.FocusSchedule
import org.json.JSONObject

/** Single-routine references and editable drafts, never reusable confirmation commands. */
internal object AssistantRoutineConversation {
    fun remember(state: AssistantSession, routine: FocusSchedule) {
        if (state.featureState == null) state.featureState = JSONObject()
        state.featureState!!.put("routineRef", JSONObject().put("at", System.currentTimeMillis()).put("routine", RoutineJson.encode(routine)))
    }
    private fun reference(state: AssistantSession): FocusSchedule? = state.featureState?.optJSONObject("routineRef")
        ?.takeIf { state.topic == "routines" && System.currentTimeMillis() - it.optLong("at") in 0..900_000L }
        ?.optJSONObject("routine")?.let(RoutineJson::decode)

    fun summary(s: FocusSchedule): String {
        val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val names = s.daysOfWeek.split(',').mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 1..7 }?.let { n -> days[n - 1] } }
        val restriction = when (s.restrictionMode) { "TIME_LIMIT" -> "usage limit ${s.timeLimitMinutes} minutes"; "CLICK_LIMIT" -> "launch limit ${s.clickLimitCount}"; else -> "block during its scheduled window" }
        return "${names.joinToString(", ")} · ${AssistantFeatureLanguage.timeText(s.startHour * 60 + s.startMinute, s.endHour * 60 + s.endMinute)} (device local time) · ${s.mode} · ${if (s.isEnabled) "enabled" else "disabled"} · $restriction · apps: ${s.appsToBlock}"
    }
    private data class Change(val subject: String?, val field: String, val value: String)
    private fun change(text: String, contextual: Boolean): Change? {
        Regex("(?i)^(?:set|change) routine (.+?)(?:'s)? (days|time|hours|window|apps|applications|mode|name)(?: to (.*))?$").matchEntire(text)?.let {
            return Change(it.groupValues[1], field(it.groupValues[2]), it.groupValues[3])
        }
        Regex("(?i)^rename routine (.+?) to (.*)$").matchEntire(text)?.let { return Change(it.groupValues[1], "name", it.groupValues[2]) }
        if (!contextual) return null
        val raw = text.replaceFirst(Regex("(?i)^(?:actually|instead)[, ]+"), "")
        Regex("(?i)^(?:set|change)(?: (?:its|the))? (days|time|hours|window|apps|applications|mode|name)(?: to (.*))?$").matchEntire(raw)?.let {
            return Change(null, field(it.groupValues[1]), it.groupValues[2])
        }
        Regex("(?i)^rename (?:it|that|this routine) to (.*)$").matchEntire(raw)?.let { return Change(null, "name", it.groupValues[1]) }
        val value = raw.replaceFirst(Regex("(?i)^(?:make|set|change|switch) (?:it|that|this routine)(?: to)?\\s+"), "").replace(Regex("(?i) only$"), "")
        if (value != text || raw != text) {
            if (value.lowercase() in setOf("soft", "hard", "strict", "soft mode", "hard mode", "strict mode")) return Change(null, "mode", value)
            if (AssistantFeatureLanguage.days(value) != null) return Change(null, "days", value)
            if (AssistantFeatureLanguage.window(value) != null) return Change(null, "time", value)
        }
        return null
    }
    private fun field(value: String) = when (value.lowercase()) { "hours", "window" -> "time"; "applications" -> "apps"; else -> value.lowercase() }

    suspend fun answer(context: Context, app: FocusApplication, state: AssistantSession, input: String): FeatureTurn? {
        val text = input.trim().trimEnd('.', '!', '?')
        val ref = reference(state)
        val proposed = state.pending.singleOrNull()?.takeIf { state.expected == "confirm" && state.topic == "routines" }
        val pending = when (proposed) {
            is AssistantAction.SaveRoutine -> proposed
            is AssistantAction.SetRoutine -> ref?.takeIf { it.id == proposed.id }?.let { AssistantAction.SaveRoutine(it.copy(isEnabled = proposed.enabled), it) }
            else -> null
        }
        val draft = state.featureState?.optJSONObject("routineDraft")?.takeIf { AssistantRoutineDialogue.hasDraft(state) && state.topic == "routines" }
        val contextual = state.topic == "routines" && (pending != null || draft != null || ref != null)
        val read = Regex("(?i)^(?:show|check) routine (.+)$").matchEntire(text)
        val readFollowUp = text.lowercase() in setOf("show that routine", "show this routine", "show it", "when does it run", "what apps does it block", "what is its mode", "is it enabled", "is it active")
        val editFollowUp = text.lowercase() in setOf("edit it", "change it", "edit that routine")
        val toggleFollowUp = Regex("(?i)^(enable|disable|pause|resume) (?:it|that routine)$").matchEntire(text)
        val unboundChange = change(text, true)?.takeIf { it.subject == null }
        if (state.topic == "routines" && !contextual && (readFollowUp || editFollowUp || toggleFollowUp != null || unboundChange != null)) {
            state.clearPending()
            return FeatureTurn.Reply("Which routine do you mean? Say ‘show routine’ followed by its exact name for a fresh reference.")
        }
        val contextRead = contextual && readFollowUp
        val editIt = contextual && editFollowUp
        val toggle = toggleFollowUp?.takeIf { contextual }
        val change = change(text, contextual)
        if (read == null && !contextRead && !editIt && toggle == null && change == null) return null

        val named = read?.groupValues?.get(1) ?: change?.subject
        val current = if (named != null) {
            app.database.scheduleDao().getAllSchedulesSync().filter { if (named.startsWith("id:", true)) it.id == named.substringAfter(':').toIntOrNull() else it.name.equals(named, true) }.singleOrNull()
                ?: run { state.clearPending(); state.featureState?.remove("routineRef"); state.featureState?.remove("routineDraft"); return FeatureTurn.Reply("Name one exact routine or use its id. Nothing has changed.") }
        } else null
        val sameDraft = named == null || pending?.draft?.id?.let { it > 0 && it == current?.id } == true || draft?.getJSONObject("schedule")?.optInt("id")?.let { it > 0 && it == current?.id } == true
        val selected = when {
            sameDraft && pending != null -> pending.draft
            sameDraft && draft != null -> RoutineJson.decode(draft.getJSONObject("schedule"))
            current != null -> current
            else -> ref ?: return FeatureTurn.Reply("Which routine do you mean? Say ‘show routine’ followed by its exact name.")
        }
        val before = when {
            sameDraft && pending != null -> pending.expected
            sameDraft && draft != null -> draft.optJSONObject("before")?.let(RoutineJson::decode)
            current != null -> current
            else -> ref
        }
        val saved = selected.id.takeIf { it > 0 }?.let { app.database.scheduleDao().getScheduleById(it) }
        if (read != null && current != null && !sameDraft) {
            AssistantConversation.suspendQuestion(state); state.clearPending(); state.featureState?.remove("routineDraft")
        }
        if (read != null || contextRead) {
            val isDraft = sameDraft && (pending != null || draft != null)
            if (!isDraft) {
                val actual = saved ?: run { state.clearPending(); state.featureState?.remove("routineRef"); return FeatureTurn.Reply("That routine is no longer available. Name another routine.") }
                state.switchTopic("routines"); remember(state, actual)
                return FeatureTurn.Reply("**${actual.name}**\n${summary(actual)}\n${if (actual.isActiveAt()) "Its saved schedule is active now." else "Its saved schedule is not active now."} This does not prove an overlay is visible. You can say ‘change its days to weekends’ or ‘edit it’.")
            }
            return FeatureTurn.Reply("**${selected.name.ifBlank { "New routine" }} · draft, not saved**\n${summary(selected)}\n" + (if (pending != null) "The proposed changes still need confirmation." else AssistantReplyStyle.expectedHint(state)))
        }
        if (before != null && saved != before) {
            state.clearPending(); state.featureState?.remove("routineDraft"); state.featureState?.remove("routineRef")
            return FeatureTurn.Reply("That routine changed while we were talking. Say ‘show routine ${before.name}’ to review its current values before editing again.")
        }
        if (before != null && !AssistantActionExecutor.canDisableRoutine(context, before)) {
            state.clearPending(); state.featureState?.remove("routineDraft")
            return FeatureTurn.Reply("An active strict routine or focus session cannot be edited from chat. Nothing changed.")
        }
        if (toggle != null) {
            if (pending != null || draft != null || selected.id <= 0) return FeatureTurn.Reply("Finish or cancel the routine draft first, then request enabling or disabling its saved schedule. The draft is still here; nothing changed.")
            return FeatureTurn.Preview(listOf(AssistantAction.SetRoutine(selected.id, toggle.groupValues[1].lowercase() in setOf("enable", "resume"))), "${if (toggle.groupValues[1].lowercase() in setOf("enable", "resume")) "Enable" else "Disable"} routine **${selected.name}** using its saved schedule?")
        }
        if (editIt && draft != null) return FeatureTurn.Reply(AssistantReplyStyle.expectedHint(state))
        val key = change?.field ?: "field"
        val value = change?.value.orEmpty()
        val revised = when (key) {
            "name" -> value.removeSurrounding("\"").takeIf { it.isNotBlank() && it.length <= 80 && it.none(Char::isISOControl) }?.let { selected.copy(name = it) }
            "days" -> AssistantFeatureLanguage.days(value)?.let { selected.copy(daysOfWeek = it) }
            "time" -> AssistantFeatureLanguage.window(value)?.let { selected.copy(startHour = it.first / 60, startMinute = it.first % 60, endHour = it.second / 60, endMinute = it.second % 60) }
            "apps" -> AssistantFeatureLanguage.resolveApps(context, value)?.takeIf { it.isNotEmpty() }?.let { selected.copy(appsToBlock = it.joinToString(",")) }
            "mode" -> when (value.lowercase().removeSuffix(" mode")) { "soft" -> selected.copy(mode = "SOFT"); "hard", "strict" -> selected.copy(mode = "HARD"); else -> null }
            else -> null
        }
        if (draft == null || !sameDraft) {
            AssistantConversation.suspendQuestion(state); state.clearPending(); state.switchTopic("routines")
            if (before != null) remember(state, before)
        }
        if (revised == null) {
            if (state.featureState == null) state.featureState = JSONObject()
            state.clearPending(); state.expected = "feature_value"
            val missing = if (sameDraft && draft != null) (listOf(draft.getString("field")) + draft.optString("remaining").split(',')).filter { it.isNotBlank() && it != "field" && it != key }.distinct() else emptyList()
            state.featureState!!.put("routineDraft", JSONObject().put("schedule", RoutineJson.encode(selected)).put("before", before?.let(RoutineJson::encode) ?: JSONObject.NULL).put("field", key).put("remaining", missing.joinToString(",")))
            return FeatureTurn.Reply("For **${selected.name.ifBlank { "the new routine" }}**: ${AssistantRoutineDialogue.hint(key)}")
        }
        if (draft != null && sameDraft) {
            draft.put("schedule", RoutineJson.encode(revised))
            var remaining = draft.optString("remaining").split(',').filter { it.isNotBlank() && it != key }
            if (draft.getString("field") == "field") {
                state.featureState!!.remove("routineDraft")
                return AssistantRoutineDialogue.preview(app, revised, before)
            }
            if (draft.getString("field") == key) {
                if (remaining.isEmpty()) {
                    state.featureState!!.remove("routineDraft")
                    return AssistantRoutineDialogue.preview(app, revised, before)
                }
                draft.put("field", remaining.first()); remaining = remaining.drop(1)
            }
            draft.put("remaining", remaining.joinToString(","))
            return FeatureTurn.Reply("Updated the draft's $key. ${AssistantReplyStyle.expectedHint(state)}")
        }
        state.featureState?.remove("routineDraft")
        return AssistantRoutineDialogue.preview(app, revised, before)
    }
}
