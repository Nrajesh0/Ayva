/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 */
package com.focusbyrj.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            fun attempt(label: String, action: () -> Unit) {
                try { action() }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    android.util.Log.w("BootReceiver", "Could not restore $label", error)
                }
            }
            try {
                attempt("focus timer") { com.focusbyrj.app.util.FocusSessionManager.restore(app) }
                attempt("focus protection") { FocusBlockerService.startService(app) }
                // A rejected optional bubble start cannot prevent alarms being rearmed.
                // Keep an unexpired snooze across reboot/update rather than showing early.
                attempt("bubble") { BubbleService.startIfEnabled(app) }
                attempt("reminder recovery") {
                    ReminderReconcileWorker.enqueue(app, restoreAbsolute = true).result.get(8, TimeUnit.SECONDS)
                }
            } finally {
                pending?.finish()
            }
        }
    }
}
