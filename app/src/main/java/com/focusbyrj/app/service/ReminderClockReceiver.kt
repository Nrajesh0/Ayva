package com.focusbyrj.app.service

import android.content.BroadcastReceiver
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/** Recover changed alarm access and local-calendar alarms without shifting saved deadlines. */
class ReminderClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmAccessChanged = intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        if (alarmAccessChanged || intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            val pending = goAsync()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    if (alarmAccessChanged) {
                        com.focusbyrj.app.util.AlarmPermissionRecovery.reconcile(context)
                    } else {
                        ReminderReconcileWorker.enqueue(context.applicationContext, restoreAbsolute = false)
                            .result.get(8, java.util.concurrent.TimeUnit.SECONDS)
                    }
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    android.util.Log.w("ReminderClockReceiver", "Could not queue alarm reconciliation", error)
                } finally { pending?.finish() }
            }
        }
    }
}
