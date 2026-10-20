package com.focusbyrj.app.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusbyrj.app.util.WorkSessionReminders

class WorkReminderReconcileWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        WorkSessionReminders.reconcileAll(applicationContext)
        Result.success()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Result.retry()
    }
}
