package com.focusbyrj.app.ui.security

import android.content.SharedPreferences
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Apply before content is shown; keep open windows in sync with the user's preference. */
fun ComponentActivity.installScreenCaptureProtection() {
    val preferences = getSharedPreferences("focus_prefs", android.content.Context.MODE_PRIVATE)
    // Do not remove stronger protection installed by the caller for a sensitive surface.
    val alreadyProtected = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    fun refresh() {
        if (alreadyProtected || preferences.getBoolean("secure_recents", false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "secure_recents" || key == null) runOnUiThread { refresh() }
    }
    refresh()
    preferences.registerOnSharedPreferenceChangeListener(listener)
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) = refresh()
        override fun onDestroy(owner: LifecycleOwner) {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            lifecycle.removeObserver(this)
        }
    })
}
