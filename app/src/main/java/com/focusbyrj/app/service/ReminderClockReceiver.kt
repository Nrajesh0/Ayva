package com.focusbyrj.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/** Recompute local-calendar alarms without shifting saved task/session timestamps. */
class ReminderClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            val pending = goAsync()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    ReminderReconcileWorker.enqueue(context.applicationContext, restoreAbsolute = false)
                        .result.get(8, java.util.concurrent.TimeUnit.SECONDS)
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    android.util.Log.w("ReminderClockReceiver", "Could not queue calendar reconciliation", error)
                } finally { pending?.finish() }
            }
        }
    }
}
