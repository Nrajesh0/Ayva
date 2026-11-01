package com.focusbyrj.app.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import com.focusbyrj.app.util.assistant.AssistantPreferences

/** UI and chat use the same validation/side-effect path, retaining legacy storage mirrors. */
internal fun writeSetting(context: Context, key: String, value: Any) {
    val spec = AssistantPreferences.specs.first { it.key == key }
    AssistantPreferences.apply(context, key, spec.type, value.toString())
}

@Suppress("UNCHECKED_CAST")
@Composable
internal fun <T> rememberSetting(prefs: SharedPreferences, key: String, default: T): MutableState<T> {
    fun read(): T = when (default) {
        is Boolean -> prefs.getBoolean(key, default)
        is Int -> prefs.getInt(key, default)
        is String -> prefs.getString(key, default) ?: default
        else -> error("Unsupported preference")
    } as T
    val state = remember(prefs, key) { mutableStateOf(read()) }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key || changed == null) state.value = read()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
