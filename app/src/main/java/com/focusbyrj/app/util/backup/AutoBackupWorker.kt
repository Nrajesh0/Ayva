/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.focusbyrj.app.util.backup

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.focusbyrj.app.data.note.NoteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Production-grade background worker for daily automatic backups.
 * Managed by Android WorkManager, ensuring reliable daily execution even if the app process
 * is killed or the device restarts.
 */
class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting WorkManager scheduled daily auto-backup...")
            val noteDao = NoteDatabase.getInstance(applicationContext).noteDao()
            val focusDb = (applicationContext as? com.focusbyrj.app.FocusApplication)?.database
            val taskDao = focusDb?.taskDao()

            val backupSuccess = DataSafetyManager.writeDailyBackup(applicationContext, noteDao, focusDb)
            if (backupSuccess) {
                val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putLong(KEY_LAST_BACKUP_MS, System.currentTimeMillis()).apply()
                Log.i(TAG, "WorkManager daily auto-backup completed successfully")
            } else {
                Log.w(TAG, "WorkManager daily auto-backup returned false")
            }

            // 30-day trash auto-purge: permanently deletes notes & tasks that have been in trash > 30 days
            DataSafetyManager.purgeExpiredTrash(
                context = applicationContext,
                noteDao = noteDao,
                taskDao = taskDao,
                retentionDays = 30,
                focusDb = focusDb
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager daily auto-backup encountered an error", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        const val WORK_NAME = "AyvaDailyAutoBackup"
        private const val PREFS_NAME = "auto_backup_scheduler_prefs"
        private const val KEY_LAST_BACKUP_MS = "last_daily_backup_ms"

        /**
         * Schedules recurring 24-hour auto-backup with battery-not-low constraints.
         * Enqueued with [ExistingPeriodicWorkPolicy.KEEP] to preserve the active schedule across app launches.
         */
        fun enqueue(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
                Log.i(TAG, "Enqueued periodic WorkManager daily auto-backup (24h interval)")
            } catch (e: Throwable) {
                Log.w(TAG, "WorkManager initialization skipped or failed: ${e.message}")
            }
        }

        /**
         * Cancels the recurring daily auto-backup work.
         */
        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                Log.i(TAG, "Cancelled WorkManager daily auto-backup")
            } catch (e: Throwable) {
                Log.w(TAG, "WorkManager cancel skipped or failed: ${e.message}")
            }
        }
    }
}
