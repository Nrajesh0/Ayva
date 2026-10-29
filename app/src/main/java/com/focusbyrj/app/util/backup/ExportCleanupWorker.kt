package com.focusbyrj.app.util.backup

import android.content.Context
import androidx.work.*
import com.focusbyrj.app.ui.screens.notes.ArticleExporterHelper
import java.io.File
import java.util.concurrent.TimeUnit

/** Android scheduling is best effort; fresh viewer handoffs retain their one-hour lease. */
class ExportCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ArticleExporterHelper.pruneStaleExportFiles(File(applicationContext.cacheDir, "exports"))
        return Result.success()
    }
    companion object {
        fun schedule(context: Context) {
            ArticleExporterHelper.pruneStaleExportFiles(File(context.cacheDir, "exports"))
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("run_export_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ExportCleanupWorker>(15, TimeUnit.MINUTES).build())
        }
    }
}
