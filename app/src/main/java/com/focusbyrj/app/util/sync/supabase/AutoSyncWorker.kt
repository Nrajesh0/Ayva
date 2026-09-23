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
import android.util.Log
import androidx.work.*
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.note.NoteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Production-grade background worker for cloud synchronization.
 * Managed by Android WorkManager, ensuring reliable sync execution with network constraints
 * even when the app is backgrounded or swiped away.
 */
class AutoSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? FocusApplication ?: return@withContext Result.failure()

        // Verify user is authenticated and auto-sync is enabled
        val session = SupabaseKeyManager.getSessionState(app)
        if (!session.isSignedIn || session.isOfflineMode || !AutoSyncManager.isAutoSyncEnabled(app)) {
            Log.d(TAG, "Skipping WorkManager sync: user not signed in, offline mode active, or auto-sync disabled")
            return@withContext Result.success()
        }

        try {
            Log.i(TAG, "Starting WorkManager background cloud sync...")
            val noteDao = NoteDatabase.getInstance(app).noteDao()
            val taskDao = app.database.taskDao()

            val syncResult = SupabaseSyncEngine.performSync(app, noteDao, taskDao)
            if (syncResult.isSuccess) {
                Log.i(TAG, "WorkManager background cloud sync completed successfully")
                Result.success()
            } else {
                val errorMsg = syncResult.exceptionOrNull()?.message ?: "Unknown sync error"
                Log.w(TAG, "WorkManager background cloud sync failed: $errorMsg")
                // Retry if network error, otherwise succeed to avoid infinite retry loops on auth errors
                if (errorMsg.contains("network", ignoreCase = true) || errorMsg.contains("timeout", ignoreCase = true)) {
                    Result.retry()
                } else {
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager sync threw an unhandled exception", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AutoSyncWorker"
        const val PERIODIC_WORK_NAME = "AyvaPeriodicSync"
        const val ONE_OFF_WORK_NAME = "AyvaOneOffSync"

        /**
         * Enqueues recurring periodic background sync (every 15 minutes) with network constraints.
         */
        fun enqueuePeriodic(context: Context) {
            try {
                val isWifiOnly = AutoSyncManager.isSyncOnWifiOnly(context)
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(if (isWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<AutoSyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
                Log.d(TAG, "Enqueued periodic WorkManager cloud sync (15m interval)")
            } catch (e: Throwable) {
                Log.w(TAG, "WorkManager periodic sync enqueue skipped: ${e.message}")
            }
        }

        /**
         * Enqueues a one-off background sync when local data changes occur, ensuring
         * changes are synced even if the user exits the app immediately after editing.
         */
        fun enqueueOneOff(context: Context) {
            try {
                val isWifiOnly = AutoSyncManager.isSyncOnWifiOnly(context)
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(if (isWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<AutoSyncWorker>()
                    .setConstraints(constraints)
                    .setInitialDelay(2, TimeUnit.SECONDS) // short debounce delay
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONE_OFF_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
                Log.d(TAG, "Enqueued one-off WorkManager cloud sync")
            } catch (e: Throwable) {
                Log.w(TAG, "WorkManager one-off sync enqueue skipped: ${e.message}")
            }
        }

        /**
         * Cancels all scheduled WorkManager cloud sync tasks.
         */
        fun cancel(context: Context) {
            try {
                val wm = WorkManager.getInstance(context)
                wm.cancelUniqueWork(PERIODIC_WORK_NAME)
                wm.cancelUniqueWork(ONE_OFF_WORK_NAME)
                Log.d(TAG, "Cancelled WorkManager cloud sync")
            } catch (e: Throwable) {
                Log.w(TAG, "WorkManager cancel skipped: ${e.message}")
            }
        }
    }
}
