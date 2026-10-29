package com.focusbyrj.app.util.backup

import android.content.SharedPreferences

/** Application managers and workers share the same admission barrier as SQLite writes. */
internal class MaintenancePreferences(private val prefs: SharedPreferences) : SharedPreferences by prefs {
    override fun edit(): SharedPreferences.Editor = Editor(prefs.edit())
    private class Editor(private val editor: SharedPreferences.Editor) : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { editor.putString(key, value) }
        override fun putStringSet(key: String, values: Set<String>?) = apply { editor.putStringSet(key, values) }
        override fun putInt(key: String, value: Int) = apply { editor.putInt(key, value) }
        override fun putLong(key: String, value: Long) = apply { editor.putLong(key, value) }
        override fun putFloat(key: String, value: Float) = apply { editor.putFloat(key, value) }
        override fun putBoolean(key: String, value: Boolean) = apply { editor.putBoolean(key, value) }
        override fun remove(key: String) = apply { editor.remove(key) }
        override fun clear() = apply { editor.clear() }
        override fun commit(): Boolean = MaintenanceGate.write { editor.commit() }
        // Finish persistence inside admission: an asynchronous disk write must not
        // escape into the restore interval after its caller has released the barrier.
        override fun apply() { MaintenanceGate.write { editor.commit() } }
    }
}
