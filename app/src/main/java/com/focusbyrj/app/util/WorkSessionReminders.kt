package com.focusbyrj.app.util

import androidx.room.withTransaction
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.MainActivity
import com.focusbyrj.app.R
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.WorkSession
import com.focusbyrj.app.data.planning
import com.focusbyrj.app.service.WorkSessionReminderReceiver
import com.focusbyrj.app.util.backup.EncryptedSnapshotFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Session alarms have separate identities from deadline alarms and never complete a task. */
object WorkSessionReminders {
    private val lock = Any()
    fun retry(context: Context) {
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("ayva-work-reminder-reconcile",
            androidx.work.ExistingWorkPolicy.REPLACE,
            androidx.work.OneTimeWorkRequestBuilder<com.focusbyrj.app.service.WorkReminderReconcileWorker>()
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS).build())
    }
    fun availability(context: Context): String? {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return "Work times are saved, but Android notifications are disabled for Ayva. Enable them in Android settings to receive reminders."
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) return "Android exact alarms are disabled; work reminders use the approximate timing Android allows."
        return null
    }
    private const val CHANNEL = "planned_work"
    private fun file(context: Context) = File(context.filesDir, "work_reminder_ledger.enc")
    private fun read(context: Context): JSONObject = file(context).let {
        if (!it.exists()) JSONObject() else {
            require(it.length() <= 2_000_000)
            JSONObject(EncryptedSnapshotFile.read(it))
        }
    }
    private fun alarm(context: Context, taskId: Long, sessionId: String, start: Long = 0): PendingIntent {
        val intent = Intent(context, WorkSessionReminderReceiver::class.java).apply {
            data = Uri.Builder().scheme("ayvawork").authority("task").appendPath(taskId.toString()).appendPath(sessionId).build()
            putExtra("taskId", taskId); putExtra("sessionId", sessionId); putExtra("sessionStart", start)
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun reconcile(context: Context, task: Task, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val all = read(context)
        val key = task.id.toString()
        val prior = all.optJSONArray(key) ?: JSONArray()
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Keep previous IDs durable until all cancellations and registrations have succeeded.
        for (i in 0 until prior.length()) {
            val id = prior.getJSONObject(i).getString("id")
            manager.cancel(alarm(context, task.id, id))
            notifications.cancel("ayva.work.${task.id}.$id", 0)
        }
        val enabled = !task.isCompleted && !task.isTrashed && task.deletedAt == null && task.planning?.workReminders == true
        val sessions = if (enabled) task.planning!!.workSessions.filter { it.start > now } else emptyList()
        // Persist the union first: a crash midway through registration leaves every alarm cancellable.
        val registrations = (0 until prior.length()).map { prior.getJSONObject(it) } + sessions.map { it.json() }
        all.put(key, JSONArray(registrations.distinctBy { it.getString("id") }))
        EncryptedSnapshotFile.write(file(context), all.toString())
        sessions.forEach { session ->
            val pending = alarm(context, task.id, session.id, session.start)
            if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.start, pending)
            } else manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.start, pending)
        }
        if (sessions.isEmpty()) all.remove(key) else all.put(key, JSONArray(sessions.map { it.json() }))
        require(all.toString().toByteArray().size <= 2_000_000)
        EncryptedSnapshotFile.write(file(context), all.toString())
    }
    fun cancel(context: Context, taskId: Long) = reconcile(context, Task(id = taskId, title = "", isTrashed = true))

    /** Full reconciliation also cancels alarms for tasks removed by replacement restore or purge. */
    suspend fun reconcileAll(context: Context) {
        val app = context.applicationContext as? FocusApplication ?: return
        val tasks = app.database.taskDao().getAllTasksList()
        val ids = synchronized(lock) { read(context).keys().asSequence().mapNotNull(String::toLongOrNull).toList() }
        ids.filterNot { id -> tasks.any { it.id == id } }.forEach { cancel(context, it) }
        tasks.forEach { reconcile(context, it) }
    }
    fun currentSession(task: Task?, sessionId: String, expectedStart: Long, now: Long): WorkSession? {
        if (task == null || task.isCompleted || task.isTrashed || task.deletedAt != null || task.planning?.workReminders != true) return null
        return task.planning!!.workSessions.singleOrNull {
            it.id == sessionId && it.start == expectedStart && now >= it.start && now < it.end
        }
    }
    suspend fun deliver(context: Context, taskId: Long, sessionId: String, start: Long, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext as? FocusApplication ?: return
        app.database.withTransaction {
        val task = app.database.taskDao().getTaskById(taskId) ?: return@withTransaction
        val session = currentSession(task, sessionId, start, now) ?: return@withTransaction
        synchronized(lock) {
        val ledger = read(context)
        val delivered = ledger.optJSONObject("_delivered") ?: JSONObject()
        val deliveryKey = "$taskId:$sessionId:$start"
        if (delivered.has(deliveryKey)) return@synchronized
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Planned work", NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(context, MainActivity::class.java).putExtra("navigate_to", "todos").apply {
            data = Uri.Builder().scheme("ayvawork").authority("open").appendPath(taskId.toString()).appendPath(sessionId).build()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val content = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify("ayva.work.$taskId.$sessionId", 0, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_app_logo).setContentTitle("Time to work on ${task.title}")
            .setContentText("${session.minutes} minute work session").setContentIntent(content)
            .setOnlyAlertOnce(true).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
        delivered.put(deliveryKey, now)
        delivered.keys().asSequence().toList().filter { now - delivered.optLong(it) > 14 * 86_400_000L }.forEach(delivered::remove)
        while (delivered.length() > 2000) delivered.remove(delivered.keys().next())
        ledger.put("_delivered", delivered); EncryptedSnapshotFile.write(file(context), ledger.toString())
        }
        }
    }
}
