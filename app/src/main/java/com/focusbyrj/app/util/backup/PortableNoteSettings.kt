package com.focusbyrj.app.util.backup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Logical note links are portable; Android widget host IDs belong to one installation. */
internal object PortableNoteSettings {
    const val PROFILE_PINS = "profile_pinned_notes_prefs"
    private const val ORIGIN_PREFS = "widget_binding_origin"
    fun validate(root: JSONObject) {
        if (root.has("widgetBindingOrigin")) {
            require(root.opt("widgetBindingOrigin") is String)
            UUID.fromString(root.getString("widgetBindingOrigin"))
        }
        val prefs = root.optJSONObject("preferences") ?: return
        prefs.optJSONObject(PROFILE_PINS)?.opt("profile_pinned_ids")?.let { value ->
            require(value is JSONArray && value.length() <= 100_000) { "Invalid profile pin selections" }
            for (i in 0 until value.length()) require(value.opt(i) is String && (value.getString(i).toLongOrNull() ?: 0L) > 0L)
        }
        prefs.optJSONObject("note_widget_prefs")?.let { widget -> widget.keys().forEach { key ->
            val value = widget.get(key)
            if (isBinding(key)) require(value is Int || value is Long) { "Invalid widget note ID" }
            else require(value is Boolean || value is Number || value is String || value is JSONArray) { "Invalid widget setting" }
            if (value is JSONArray) for (i in 0 until value.length()) require(value.opt(i) is String)
        } }
        val types = root.optJSONObject("preferenceTypes")
        fun checkValue(store: String, key: String, type: String, valid: (Any) -> Boolean) {
            val values = prefs.optJSONObject(store) ?: return
            if (!values.has(key)) return
            require(valid(values.get(key))) { "Invalid saved preference: $key" }
            val declared = types?.optJSONObject(store)
            require(declared?.has(key) != true || declared.optString(key) == type) { "Invalid preference type: $key" }
        }
        val personalization = "ayva_personalization"
        for (key in listOf("concise", "emoji", "quiet", "questions", "suggestions")) checkValue(personalization, key, "bool") { it is Boolean }
        checkValue(personalization, "name", "string") { it is String && it.length in 1..40 && it.none(Char::isISOControl) }
        checkValue(personalization, "tone", "string") { it in setOf("warm", "playful", "direct") }
        checkValue(personalization, "plan_order", "string") { it in setOf("priority", "due") }
        checkValue(personalization, "focus_minutes", "int") { it is Int && it in 1..240 }
        checkValue(personalization, "plan_size", "int") { it is Int && it in 1..5 }
        val alerts = com.focusbyrj.app.util.AyvaAlertCategory.PREFS_NAME
        prefs.optJSONObject(alerts)?.keys()?.forEach { key ->
            if (key == "chat_updates_enabled" || key.startsWith("alert_enabled_")) checkValue(alerts, key, "bool") { it is Boolean }
        }
    }
    fun origin(context: Context): String {
        val prefs = context.getSharedPreferences(ORIGIN_PREFS, 0)
        val stored = prefs.all["id"] as? String
        if (stored != null && runCatching { UUID.fromString(stored) }.isSuccess) return stored
        return UUID.randomUUID().toString().also {
            check(prefs.edit().putString("id", it).commit()) { "Widget identity could not be saved" }
        }
    }

    fun restorePins(context: Context, payload: JSONObject?, clean: Boolean,
                    noteIds: Map<Long, Long>, publicNoteIds: Set<Long>) {
        val prefs = context.getSharedPreferences(PROFILE_PINS, 0)
        val kept = if (clean) mutableSetOf<String>() else prefs.getStringSet("profile_pinned_ids", emptySet()).orEmpty().toMutableSet()
        val incoming = payload?.optJSONArray("profile_pinned_ids") ?: JSONArray()
        for (i in 0 until incoming.length()) {
            val id = noteIds[incoming.getString(i).toLongOrNull()] ?: continue
            if (id in publicNoteIds) kept += id.toString()
        }
        check(prefs.edit().putStringSet("profile_pinned_ids", kept).commit()) { "Profile pins could not be restored" }
    }

    fun restoreWidgets(context: Context, payload: JSONObject, clean: Boolean,
                       noteIds: Map<Long, Long>, sourceOrigin: String?): Boolean {
        val prefs = context.getSharedPreferences("note_widget_prefs", 0)
        val sameInstallation = sourceOrigin != null && sourceOrigin == origin(context)
        val editor = prefs.edit()
        var disconnected = false
        if (clean && sameInstallation) editor.clear()
        if (clean && !sameInstallation) {
            // Existing host widgets must not display unrelated restored rows sharing an ID.
            prefs.all.keys.filter(::isBinding).forEach { key ->
                disconnected = true
                if (key.startsWith("note_current_id_")) editor.remove(key) else editor.putLong(key, -1L)
            }
        }
        payload.keys().forEach { key ->
            if (!sameInstallation && Regex(".*_\\d+$").matches(key)) { disconnected = true; return@forEach }
            if (isBinding(key)) {
                val id = noteIds[payload.getLong(key)] ?: -1L
                if (key.startsWith("note_current_id_") && id < 0) editor.remove(key) else editor.putLong(key, id)
            } else when (val value = payload.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Number -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
                is JSONArray -> editor.putStringSet(key, (0 until value.length()).map { value.getString(it) }.toSet())
                else -> error("Unsupported widget setting")
            }
        }
        check(editor.commit()) { "Widget settings could not be restored" }
        return disconnected
    }
    private fun isBinding(key: String) = key.startsWith("note_specific_id_") || key.startsWith("note_current_id_")
}
