package com.focusbyrj.app.ui.tours

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Local UI hints only. Never reads or writes notes, credentials, or backup preferences. */
internal class TourPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("app_tours", Context.MODE_PRIVATE)
    internal var revision by mutableIntStateOf(0)

    private inline fun <T> read(value: () -> T): T {
        revision
        return value()
    }

    val welcomePending
        get() = read { prefs.getBoolean("welcome_pending", false) }

    val automatic
        get() = read { prefs.getBoolean("automatic", false) }

    val replayId
        get() = read { prefs.getString("replay", null) }

    val replaySerial
        get() = read { prefs.getInt("replay_serial", 0) }

    fun initialize(existingInstall: Boolean) {
        if (!prefs.contains("initialized"))
            prefs
                .edit()
                .putBoolean("initialized", true)
                .putBoolean("welcome_pending", !existingInstall)
                .putBoolean("automatic", false)
                .apply()
    }

    fun chooseWelcome(show: Boolean) {
        prefs
            .edit()
            .putBoolean("welcome_pending", false)
            .putBoolean("automatic", show)
            .putBoolean("done_navigation", !show)
            .apply()
    }

    fun setAutomatic(enabled: Boolean) {
        // Enabling contextual tips from Help does not unexpectedly launch an introduction.
        prefs
            .edit()
            .putBoolean("automatic", enabled)
            .putBoolean("welcome_pending", false)
            .putBoolean("done_navigation", true)
            .apply()
    }

    fun eligible(tour: AppTour): Boolean =
        replayId == tour.id ||
            (replayId == null &&
                automatic &&
                !welcomePending &&
                !done(tour.id) &&
                (tour == AppTours.navigation || done(AppTours.navigation.id)))

    fun done(id: String) = read { prefs.getBoolean("done_$id", false) }

    fun finish(tour: AppTour) {
        prefs
            .edit()
            .putBoolean("done_${tour.id}", true)
            .apply {
                if (replayId == tour.id) remove("replay")
                // The Focus hint is redundant immediately after the first navigation tour.
                if (tour == AppTours.navigation) putBoolean("done_focus", true)
            }
            .apply()
    }

    fun replay(tour: AppTour) {
        prefs
            .edit()
            .putBoolean("welcome_pending", false)
            .putString("replay", tour.id)
            .putInt("replay_serial", replaySerial + 1)
            .apply {
                if (tour == AppTours.tasks) remove("done_task_dates")
                if (tour == AppTours.focus) {
                    remove("done_blocking")
                    remove("done_blocking_modes")
                }
            }
            .apply()
    }

    fun cancelReplay() {
        prefs.edit().remove("replay").apply()
    }

    fun observe(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unobserve(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
}

@Composable
internal fun rememberTourPreferences(): TourPreferences {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { TourPreferences(context) }
    DisposableEffect(store) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> store.revision++ }
        store.observe(listener)
        onDispose { store.unobserve(listener) }
    }
    return store
}
