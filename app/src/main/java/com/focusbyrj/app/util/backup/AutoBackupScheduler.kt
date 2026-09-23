/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.util.backup

import android.content.Context
import android.util.Log
import com.focusbyrj.app.data.note.NoteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Production-grade scheduler for daily automatic rolling backups.
 * Utilizes Android WorkManager ([AutoBackupWorker]) for guaranteed execution even if
 * the app process is terminated or the device reboots.
 *
 * The backup is an unencrypted JSON snapshot of notes written to app-private internal storage
 * (see [DataSafetyManager.writeDailyBackup]). Keeps 7 rolling daily snapshots.
 */
object AutoBackupScheduler {

    private const val TAG = "AutoBackupScheduler"
    private const val PREFS_NAME = "auto_backup_scheduler_prefs"
    private const val KEY_LAST_BACKUP_MS = "last_daily_backup_ms"

    /** 24 hours in milliseconds */
    private const val INTERVAL_MS = 24L * 60L * 60L * 1000L

    /**
     * Initializes the daily backup scheduler.
     * Enqueues the persistent [AutoBackupWorker] via WorkManager and checks if an immediate
     * catch-up backup is needed if more than 24 hours have elapsed.
     *
     * Call this once from [com.focusbyrj.app.FocusApplication.onCreate].
     */
    fun schedule(context: Context, scope: CoroutineScope? = null) {
        val appContext = context.applicationContext
        // 1. Enqueue persistent WorkManager periodic job
        AutoBackupWorker.enqueue(appContext)

        // 2. Check if last backup is older than 24 hours (catch-up on app open)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastBackupMs = prefs.getLong(KEY_LAST_BACKUP_MS, 0L)
        val now = System.currentTimeMillis()
        if (now - lastBackupMs > INTERVAL_MS) {
            val executionScope = scope ?: CoroutineScope(Dispatchers.IO)
            executionScope.launch(Dispatchers.IO) {
                runDailyBackup(appContext, prefs)
            }
        }
        Log.i(TAG, "AutoBackupScheduler initialized with WorkManager")
    }

    /** Cancel the scheduled daily auto-backup (e.g., in tests or shutdown). */
    fun cancel(context: Context) {
        AutoBackupWorker.cancel(context)
    }

    /**
     * Manually triggers a daily backup immediately (e.g., from Settings → "Run backup now").
     * Updates the last-backup timestamp on success.
     */
    suspend fun runNow(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        runDailyBackup(appContext, prefs)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun runDailyBackup(
        context: Context,
        prefs: android.content.SharedPreferences
    ) {
        try {
            val noteDao = NoteDatabase.getInstance(context).noteDao()
            val focusDb = (context.applicationContext as? com.focusbyrj.app.FocusApplication)?.database
            val taskDao = focusDb?.taskDao()

            // Write daily rolling snapshot first
            val success = DataSafetyManager.writeDailyBackup(context, noteDao, focusDb)
            if (success) {
                prefs.edit().putLong(KEY_LAST_BACKUP_MS, System.currentTimeMillis()).apply()
                Log.i(TAG, "Daily auto-backup completed successfully")
            } else {
                Log.w(TAG, "Daily auto-backup returned false (non-fatal, will retry tomorrow)")
            }

            // 30-day trash auto-purge: permanently delete notes & tasks that have been in trash
            // for more than 30 days. Uses DataSafetyManager with pre-op safety snapshot.
            DataSafetyManager.purgeExpiredTrash(
                context = context,
                noteDao = noteDao,
                taskDao = taskDao,
                retentionDays = 30,
                focusDb = focusDb
            )
        } catch (e: Exception) {
            Log.e(TAG, "Daily auto-backup/purge threw unexpectedly", e)
            // Do not update lastBackupMs on failure — retry sooner on next app start
        }
    }
}
