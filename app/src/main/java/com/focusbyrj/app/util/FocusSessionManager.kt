package com.focusbyrj.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.focusbyrj.app.service.FocusSessionEndReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-owned timer shared by the dashboard and chat; the activity does not own its lifetime. */
object FocusSessionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private var currentContext: Context? = null
    private val activeState = MutableStateFlow(false)
    private val remainingState = MutableStateFlow(25 * 60L)
    private val initialState = MutableStateFlow(25 * 60L)
    val active = activeState.asStateFlow()
    val remainingSeconds = remainingState.asStateFlow()
    val initialSeconds = initialState.asStateFlow()

    @Synchronized fun init(context: Context) {
        currentContext = context.applicationContext
        refresh(context)
        if (activeState.value && ticker?.isActive != true) {
            ticker = scope.launch {
                while (isActive && activeState.value) {
                    delay(1000)
                    refresh(currentContext ?: break)
                }
            }
        }
    }

    @Synchronized fun selectDuration(minutes: Int) {
        require(minutes in 1..240) { "Choose 1–240 minutes" }
        if (!activeState.value) { initialState.value = minutes * 60L; remainingState.value = minutes * 60L }
    }

    @Synchronized fun start(context: Context, minutes: Int) {
        require(minutes in 1..240) { "Choose 1–240 minutes" }
        refresh(context)
        require(!activeState.value) { "A focus session is already running; stop it before starting another" }
        val duration = minutes * 60L
        val prefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        check(prefs.edit().putBoolean("isSessionActive", true)
            .putLong("focus_duration_sec", duration).putLong("focus_accounted_seconds", 0)
            .putLong("focus_end_wall_ms", System.currentTimeMillis() + duration * 1000)
            .putLong("focus_end_elapsed_ms", SystemClock.elapsedRealtime() + duration * 1000)
            .putInt("focus_boot_count", bootCount(context)).commit()) { "Couldn't save focus session" }
        init(context)
        scheduleEnd(context, duration * 1000)
    }

    @Synchronized fun refresh(context: Context) {
        val prefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("isSessionActive", false)) { activeState.value = false; return }
        val duration = prefs.getLong("focus_duration_sec", 25 * 60L)
        val wallEnd = prefs.getLong("focus_end_wall_ms", 0)
        // Older builds persisted only an active flag. Preserve protection while giving that
        // legacy session a bounded, recoverable deadline instead of silently cancelling it.
        if (wallEnd <= 0 || duration !in 60..14_400L) {
            val migratedDuration = duration.takeIf { it in 60..14_400L } ?: 25 * 60L
            check(prefs.edit().putLong("focus_duration_sec", migratedDuration).putLong("focus_accounted_seconds", 0)
                .putLong("focus_end_wall_ms", System.currentTimeMillis() + migratedDuration * 1000)
                .putLong("focus_end_elapsed_ms", SystemClock.elapsedRealtime() + migratedDuration * 1000)
                .putInt("focus_boot_count", bootCount(context)).commit())
            initialState.value = migratedDuration; remainingState.value = migratedDuration; activeState.value = true
            scheduleEnd(context, migratedDuration * 1000)
            return
        }
        val remainingMs = if (prefs.getInt("focus_boot_count", -1) == bootCount(context)) {
            prefs.getLong("focus_end_elapsed_ms", 0) - SystemClock.elapsedRealtime()
        } else wallEnd - System.currentTimeMillis()
        val remaining = ((remainingMs.coerceAtLeast(0) + 999) / 1000).coerceAtMost(duration)
        initialState.value = duration
        remainingState.value = remaining
        activeState.value = remaining > 0
        val elapsed = duration - remaining
        val previous = prefs.getLong("focus_accounted_seconds", 0)
        if (elapsed > previous && (elapsed - previous >= 10 || elapsed / 300 > previous / 300 || remaining == 0L)) {
            // Publish accounting receipt before external reward updates; retries cannot duplicate it.
            check(prefs.edit().putLong("focus_accounted_seconds", elapsed).commit())
            FocusStatsManager.addFocusSessionTime(context, elapsed - previous)
            for (boundary in (previous / 300 + 1)..(elapsed / 300)) {
                FocusEconomyManager.addDurationBasedRewards((boundary * 5).toInt())
                FocusEconomyManager.addLifetimeFocusMins(5)
            }
        }
        if (remaining == 0L) finish(context)
    }

    @Synchronized fun stop(context: Context) {
        refresh(context)
        val prefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        if (activeState.value) {
            val elapsed = initialState.value - remainingState.value
            val previous = prefs.getLong("focus_accounted_seconds", 0)
            if (elapsed > previous) {
                check(prefs.edit().putLong("focus_accounted_seconds", elapsed).commit())
                FocusStatsManager.addFocusSessionTime(context, elapsed - previous)
            }
        }
        finish(context)
        remainingState.value = initialState.value
    }

    @Synchronized fun restore(context: Context) {
        init(context)
        if (activeState.value) scheduleEnd(context, remainingState.value * 1000)
    }

    private fun finish(context: Context) {
        context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE).edit().putBoolean("isSessionActive", false).apply()
        activeState.value = false
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(endIntent(context))
        DndHelper.setDndMode(context, false)
    }
    private fun bootCount(context: Context): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    private fun endIntent(context: Context) = PendingIntent.getBroadcast(
        context, 73019, Intent(context, FocusSessionEndReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    private fun scheduleEnd(context: Context, delayMs: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val due = SystemClock.elapsedRealtime() + delayMs
        try {
            if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, due, endIntent(context))
            else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, due, endIntent(context))
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, due, endIntent(context))
        }
    }
}
