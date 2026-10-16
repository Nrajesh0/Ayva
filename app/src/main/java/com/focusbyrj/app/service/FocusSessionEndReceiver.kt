package com.focusbyrj.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusbyrj.app.util.FocusSessionManager
import kotlinx.coroutines.*

class FocusSessionEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { FocusSessionManager.restore(context.applicationContext) } finally { result.finish() }
        }
    }
}
