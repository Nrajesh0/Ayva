package com.focusbyrj.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusbyrj.app.util.WorkSessionReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WorkSessionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("taskId", -1)
        val session = intent.getStringExtra("sessionId") ?: return
        if (id <= 0 || session.length !in 1..80) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { WorkSessionReminders.deliver(context.applicationContext, id, session, intent.getLongExtra("sessionStart", -1)) }
            catch (e: Exception) { android.util.Log.e("WorkReminder", "Couldn't deliver planned work reminder", e) }
            finally { pending.finish() }
        }
    }
}
