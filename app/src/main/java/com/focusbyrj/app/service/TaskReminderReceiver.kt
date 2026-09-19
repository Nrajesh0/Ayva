package com.focusbyrj.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.focusbyrj.app.R
import com.focusbyrj.app.ui.screens.TaskReminderPopupActivity
import com.focusbyrj.app.util.TaskReminderHelper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.focusbyrj.app.FocusApplication


class TaskReminderReceiver : BroadcastReceiver() {
    companion object {
        const val TASKS_GROUP_KEY = "com.focusbyrj.app.TASKS_GROUP"
        const val TASKS_SUMMARY_ID = 999_902
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("taskId", -1L)
        if (taskId == -1L) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Acquire a temporary WakeLock (5 seconds) to ensure CPU does not sleep during notification dispatch
                val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "FocusByRJ:TaskReminderWakeLock"
                )
                try {
                    wakeLock?.acquire(5000L)
                } catch (_: Exception) {}

                try {
                    val app = appContext as? FocusApplication
                    var dbTitle = intent.getStringExtra("taskTitle") ?: "Task Reminder"
                    var dbDetails = intent.getStringExtra("taskDetails") ?: ""
                    var dbDueDate = intent.getLongExtra("taskDueDate", System.currentTimeMillis())
                    var dbTypeStr = intent.getStringExtra("taskType") ?: "TASK"
                    var dbRecurrenceStr = intent.getStringExtra("taskRecurrence") ?: "NONE"
                    var dbIsPersistent = intent.getBooleanExtra("isPersistent", false)
                    var dbIsPriority = intent.getBooleanExtra("isPriority", false)

                    if (app != null) {
                        val task = app.database.taskDao().getTaskById(taskId)
                        if (task == null || task.isCompleted) {
                            // Task is completed or deleted, stop nagging!
                            TaskReminderHelper.cancelReminderById(appContext, taskId)
                            return@launch
                        }

                        // If the task was rescheduled to a time in the future, dismiss active notification and overlay.
                        // Do NOT cancel the alarm from AlarmManager, because that is the newly scheduled future alarm!
                        if (task.dueDate != null && task.dueDate > System.currentTimeMillis() + 60000L) {
                            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            nm?.cancel(taskId.toInt())
                            TaskReminderHelper.cleanUpTaskSummaryNotification(appContext, taskId)
                            TaskReminderOverlayManager.hideOverlay()
                            return@launch
                        }
                        
                        dbTitle = task.title.trim().ifEmpty { "Task Reminder" }
                        dbDetails = task.details
                        dbDueDate = task.dueDate ?: dbDueDate
                        dbTypeStr = task.type.name
                        dbRecurrenceStr = task.recurrence.name
                        dbIsPersistent = task.isPersistent
                        dbIsPriority = task.isPriority
                    }

                    val title = dbTitle.trim().ifEmpty { "Task Reminder" }
                    val details = dbDetails
                    val dueDate = dbDueDate
                    val typeStr = dbTypeStr
                    val recurrenceStr = dbRecurrenceStr
                    val isPersistent = dbIsPersistent
                    val isPriority = dbIsPriority

                    val prefs = appContext.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
                    val notificationStyle = prefs.getString("task_notification_style", "Both") ?: "Both"
                    val showNotification = true
                    val showFloating = isPriority || notificationStyle == "Both" || notificationStyle == "Floating Bar"

                    val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    
                    val channelId = "task_reminders"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            channelId,
                            "Task Reminders",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Pop up notifications and reminders for your tasks"
                            enableVibration(true)
                            setShowBadge(true)
                            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                        }
                        notificationManager.createNotificationChannel(channel)
                    }

                    // Tap Action Intent (Direct background receiver opening floating modal in the middle of the screen)
                    val popupActionIntent = Intent(appContext, TaskActionReceiver::class.java).apply {
                        action = TaskActionReceiver.ACTION_SHOW_POPUP
                        putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
                        putExtra("taskTitle", title)
                        putExtra("taskDetails", details)
                        putExtra("taskDueDate", dueDate)
                        putExtra("taskType", typeStr)
                        putExtra("taskRecurrence", recurrenceStr)
                        putExtra("isPersistent", isPersistent)
                    }
                    val popupPendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        taskId.toInt(),
                        popupActionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Reschedule Action Intent (Direct background receiver opening reschedule popup on home screen)
                    val rescheduleActionIntent = Intent(appContext, TaskActionReceiver::class.java).apply {
                        action = TaskActionReceiver.ACTION_OPEN_RESCHEDULE
                        putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
                        putExtra("taskTitle", title)
                        putExtra("taskDetails", details)
                        putExtra("taskDueDate", dueDate)
                        putExtra("taskType", typeStr)
                        putExtra("taskRecurrence", recurrenceStr)
                        putExtra("isPersistent", isPersistent)
                    }
                    val reschedulePendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        (taskId + 100000).toInt(),
                        rescheduleActionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Complete Action Intent (Direct background receiver)
                    val completeActionIntent = Intent(appContext, TaskActionReceiver::class.java).apply {
                        action = TaskActionReceiver.ACTION_COMPLETE_TASK
                        putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
                    }
                    val completePendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        (taskId + 200000).toInt(),
                        completeActionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Ignore Action Intent (Direct background receiver)
                    val ignoreActionIntent = Intent(appContext, TaskActionReceiver::class.java).apply {
                        action = TaskActionReceiver.ACTION_IGNORE_TASK
                        putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
                    }
                    val ignorePendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        (taskId + 300000).toInt(),
                        ignoreActionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val deleteIntent = Intent(appContext, TaskActionReceiver::class.java).apply {
                        action = TaskActionReceiver.ACTION_TASK_DISMISSED
                        putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
                    }
                    val deletePendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        (taskId + 400000).toInt(),
                        deleteIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val contentSubtitle = when {
                        details.isNotBlank() -> details
                        isPersistent -> "Persistent Reminder • Tap to manage"
                        else -> "It's time to focus on this task."
                    }

                    val builder = NotificationCompat.Builder(appContext, channelId)
                        .setSmallIcon(R.drawable.ic_app_logo)
                        .setContentTitle(title)
                        .setContentText(contentSubtitle)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(if (details.isNotBlank()) "$details\nTap to view or manage task." else "Scheduled task reminder is due."))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setContentIntent(popupPendingIntent)
                        .setDeleteIntent(deletePendingIntent)
                        .setAutoCancel(!isPersistent)
                        .setGroup(TASKS_GROUP_KEY)
                        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                        .addAction(0, "Ignore", ignorePendingIntent)
                        .addAction(0, "Reschedule", reschedulePendingIntent)
                        .addAction(0, "Complete", completePendingIntent)

                    if (isPersistent) {
                        builder.setOngoing(true)
                    }

                    if (showNotification) {
                        notificationManager.notify(taskId.toInt(), builder.build())

                        // Only post group summary if there are multiple active task notifications
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val activeNotifs = notificationManager.activeNotifications ?: emptyArray()
                            val activeTasks = activeNotifs.filter {
                                it.id != TASKS_SUMMARY_ID && it.notification.group == TASKS_GROUP_KEY
                            }
                            if (activeTasks.size >= 2) {
                                val inboxStyle = NotificationCompat.InboxStyle().setSummaryText("Tasks & To-Dos")
                                val lines = mutableListOf<CharSequence>()
                                activeTasks.take(5).forEach {
                                    val t = it.notification.extras?.getCharSequence(NotificationCompat.EXTRA_TITLE)
                                    if (!t.isNullOrBlank()) {
                                        lines.add(t)
                                    }
                                }
                                if (lines.isEmpty()) {
                                    lines.add(title)
                                }
                                lines.forEach { inboxStyle.addLine(it) }

                                val summaryNotification = NotificationCompat.Builder(appContext, channelId)
                                    .setSmallIcon(R.drawable.ic_app_logo)
                                    .setContentTitle("Tasks & To-Dos")
                                    .setContentText("${activeTasks.size} tasks pending")
                                    .setStyle(inboxStyle)
                                    .setGroup(TASKS_GROUP_KEY)
                                    .setGroupSummary(true)
                                    .setAutoCancel(true)
                                    .setPriority(NotificationCompat.PRIORITY_LOW)
                                    .build()
                                notificationManager.notify(TASKS_SUMMARY_ID, summaryNotification)
                            } else {
                                notificationManager.cancel(TASKS_SUMMARY_ID)
                            }
                        }
                    }

                    if (showFloating) {
                        // Display floating modal pop-up in the middle of the screen via direct overlay
                        TaskReminderOverlayManager.showReminderOverlay(
                            context = appContext,
                            taskId = taskId,
                            taskTitle = title,
                            taskDetails = details,
                            taskDueDate = dueDate,
                            taskTypeStr = typeStr,
                            taskRecurrenceStr = recurrenceStr,
                            isPersistent = isPersistent,
                            openRescheduleInitially = false
                        )
                    }

                    if (isPersistent) {
                        val intervalMins = prefs.getInt("persistent_reminder_interval", 15)
                        TaskReminderHelper.scheduleNaggingReminder(appContext, taskId, title, intervalMins)
                    }

                } finally {
                    try {
                        if (wakeLock != null && wakeLock.isHeld) {
                            wakeLock.release()
                        }
                    } catch (_: Exception) {}
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
