package com.focusbyrj.app.data.note

import android.content.Context
import androidx.work.*
import java.io.File
import java.util.concurrent.TimeUnit

/** Only abandoned temporary camera originals; never saved notes/attachments. */
class CaptureCleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        clean(applicationContext)
        if (File(applicationContext.cacheDir, "note_capture").listFiles().orEmpty().any { it.isFile && it.name.matches(Regex("[a-f0-9-]{36}\\.jpg")) }) Result.retry() else Result.success()
    } catch (_: Exception) { Result.retry() }
    companion object {
        fun clean(context: Context) {
            val now = System.currentTimeMillis()
            File(context.cacheDir, "note_capture").listFiles()?.filter {
                it.name.matches(Regex("[a-f0-9-]{36}\\.jpg")) && now - it.lastModified() >= TimeUnit.HOURS.toMillis(24)
            }?.forEach { check(it.delete() || !it.exists()) }
        }
        fun enqueue(context: Context) {
            runCatching { WorkManager.getInstance(context).enqueueUniqueWork("temporary-camera-cleanup", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CaptureCleanupWorker>().setInitialDelay(24, TimeUnit.HOURS).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS).build()) }
        }
    }
}
