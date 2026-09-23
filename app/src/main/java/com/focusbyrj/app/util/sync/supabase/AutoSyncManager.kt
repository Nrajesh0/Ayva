/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.focusbyrj.app.util.sync.supabase

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.room.InvalidationTracker
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.note.NoteDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Intelligent background synchronization coordinator.
 * Automatically synchronizes notes and tasks in real-time when changes occur,
 * while respecting zero-knowledge encryption, network conditions, and battery constraints.
 */
object AutoSyncManager {

    private const val TAG = "AutoSyncManager"
    private const val PREFS_NAME = "focus_autosync_prefs"
    private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    private const val KEY_SYNC_ON_WIFI_ONLY = "sync_on_wifi_only"

    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        data class Success(val message: String, val timestamp: Long) : SyncState()
        data class Error(val error: String) : SyncState()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debouncedJob: Job? = null
    private val isInitialized = AtomicBoolean(false)
    @Volatile
    private var lastSyncCompletedTime: Long = 0L

    /**
     * Initializes background sync listeners:
     * 1. Room InvalidationTracker on notes and tasks tables.
     * 2. ConnectivityManager listener for network reconnection.
     * 3. Periodic stale-sync verifier.
     */
    fun init(application: FocusApplication) {
        if (!isInitialized.compareAndSet(false, true)) return

        // Register periodic background sync with Android WorkManager
        if (isAutoSyncEnabled(application)) {
            AutoSyncWorker.enqueuePeriodic(application)
        }

        scope.launch {
            try {
                // 1. Observe Note table changes
                val noteTracker = NoteDatabase.getInstance(application).invalidationTracker
                noteTracker.addObserver(object : InvalidationTracker.Observer("keep_notes") {
                    override fun onInvalidated(tables: Set<String>) {
                        if (!SupabaseSyncEngine.isSyncInProgress) {
                            Log.d(TAG, "Local notes database invalidated. Scheduling debounced auto-sync...")
                            scheduleAdaptiveDebouncedSync(application)
                        }
                    }
                })

                // 2. Observe Task table changes
                val taskTracker = application.database.invalidationTracker
                taskTracker.addObserver(object : InvalidationTracker.Observer("tasks") {
                    override fun onInvalidated(tables: Set<String>) {
                        if (!SupabaseSyncEngine.isSyncInProgress) {
                            Log.d(TAG, "Local tasks database invalidated. Scheduling debounced auto-sync...")
                            scheduleAdaptiveDebouncedSync(application)
                        }
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "Could not attach Room database invalidation observers", e)
            }

            // 3. Register network callback to automatically sync when device reconnects
            try {
                val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (cm != null) {
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            Log.d(TAG, "Network restored. Checking stale sync...")
                            checkAndSyncIfStale(application, staleThresholdMs = 30_000L)
                        }
                    })
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not register network callback for auto-sync", e)
            }

            // 4. Initial check
            checkAndSyncIfStale(application, staleThresholdMs = 60_000L)

            // 5. Periodic background check while process lives
            while (isActive) {
                delay(15 * 60 * 1000L)
                try {
                    checkAndSyncIfStale(application, staleThresholdMs = 15 * 60 * 1000L)
                } catch (e: Exception) {
                    Log.w(TAG, "Periodic sync check failed", e)
                }
            }
        }
    }

    fun isAutoSyncEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SYNC_ENABLED, true)
    }

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SYNC_ENABLED, enabled)
            .apply()

        if (enabled) {
            AutoSyncWorker.enqueuePeriodic(context)
        } else {
            AutoSyncWorker.cancel(context)
        }
    }

    fun isSyncOnWifiOnly(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SYNC_ON_WIFI_ONLY, false)
    }

    fun setSyncOnWifiOnly(context: Context, wifiOnly: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SYNC_ON_WIFI_ONLY, wifiOnly)
            .apply()

        if (isAutoSyncEnabled(context)) {
            AutoSyncWorker.enqueuePeriodic(context)
        }
    }

    private fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Schedules a debounced sync taking into account the time elapsed since the last completed sync.
     * Ensures edits performed immediately after a sync cycle are gracefully delayed rather than dropped.
     */
    fun scheduleAdaptiveDebouncedSync(context: Context) {
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastSyncCompletedTime
        val delayMs = if (timeSinceLast < 4000L) {
            (4000L - timeSinceLast) + 2500L
        } else {
            3000L
        }
        triggerDebouncedSync(context, delayMs = delayMs)
    }

    fun triggerDebouncedSync(context: Context, delayMs: Long = 2500L) {
        if (!isAutoSyncEnabled(context)) return

        val session = SupabaseKeyManager.getSessionState(context)
        if (!session.isSignedIn || SupabaseKeyManager.isOfflineMode(context)) return

        // Enqueue WorkManager one-off job as persistent safety net if process dies
        AutoSyncWorker.enqueueOneOff(context)

        debouncedJob?.cancel()
        debouncedJob = scope.launch {
            delay(delayMs)
            performAutoSync(context.applicationContext, isManual = false)
        }
    }

    /**
     * Resets the sync coordinator state to Idle (e.g. on sign out).
     */
    fun resetState() {
        debouncedJob?.cancel()
        _syncState.value = SyncState.Idle
    }

    /**
     * Executes immediate sync (e.g. on Pull-to-refresh or "Sync Now" tap).
     */
    fun triggerImmediateSync(context: Context, onComplete: ((Result<SupabaseSyncEngine.SyncResult>) -> Unit)? = null) {
        scope.launch {
            if (!SupabaseSyncEngine.isSyncInProgress) {
                debouncedJob?.cancel()
            } else {
                debouncedJob?.join()
            }
            val result = performAutoSync(context.applicationContext, isManual = true)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(result)
            }
        }
    }

    /**
     * Runs sync if last successful sync was more than [staleThresholdMs] ago.
     */
    fun checkAndSyncIfStale(context: Context, staleThresholdMs: Long = 15 * 60 * 1000L) {
        if (!isAutoSyncEnabled(context)) return

        val session = SupabaseKeyManager.getSessionState(context)
        if (!session.isSignedIn || SupabaseKeyManager.isOfflineMode(context)) return

        val elapsed = System.currentTimeMillis() - session.lastSyncedTime
        if (elapsed > staleThresholdMs) {
            triggerDebouncedSync(context, delayMs = 1000L)
        }
    }

    private suspend fun performAutoSync(appContext: Context, isManual: Boolean = false): Result<SupabaseSyncEngine.SyncResult> {
        val session = SupabaseKeyManager.getSessionState(appContext)
        if (!session.isSignedIn || SupabaseKeyManager.isOfflineMode(appContext)) {
            _syncState.value = SyncState.Idle
            return Result.failure(Exception("Cloud Vault is not connected or in offline mode."))
        }

        if (!isManual && isSyncOnWifiOnly(appContext) && !isWifiConnected(appContext)) {
            Log.d(TAG, "Skipping automated sync: Wi-Fi only sync is enabled and Wi-Fi is not active.")
            _syncState.value = SyncState.Idle
            return Result.failure(Exception("Sync skipped: Wi-Fi connection required."))
        }

        _syncState.value = SyncState.Syncing
        return try {
            val noteDao = NoteDatabase.getInstance(appContext).noteDao()
            val focusApp = appContext.applicationContext as? FocusApplication
            val taskDao = focusApp?.database?.taskDao()
            if (taskDao == null) {
                Log.e(TAG, "Cannot perform auto-sync: FocusApplication database unavailable")
                _syncState.value = SyncState.Error("Application database unavailable")
                return Result.failure(IllegalStateException("FocusApplication database unavailable"))
            }

            val syncResult = SupabaseSyncEngine.performSync(appContext, noteDao, taskDao)
            lastSyncCompletedTime = System.currentTimeMillis()
            syncResult.onSuccess { res ->
                _syncState.value = SyncState.Success(res.message, System.currentTimeMillis())
                Log.d(TAG, "Auto-sync successful: ${res.message}")
                try {
                    androidx.work.WorkManager.getInstance(appContext).cancelUniqueWork(AutoSyncWorker.ONE_OFF_WORK_NAME)
                } catch (_: Exception) {}
            }.onFailure { err ->
                _syncState.value = SyncState.Error(err.localizedMessage ?: "Sync encountered an error.")
                Log.w(TAG, "Auto-sync failed: ${err.message}")
            }
            syncResult
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Unexpected error in auto-sync", e)
            _syncState.value = SyncState.Error(e.localizedMessage ?: "Sync crashed.")
            Result.failure(e)
        }
    }
}
