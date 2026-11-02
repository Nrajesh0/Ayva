package com.focusbyrj.app.util

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import com.focusbyrj.app.service.TaskReminderReceiver

/** Notification identity is independent of alarm request codes and service IDs. */
internal object TaskNotifications {
    fun tag(taskId: Long) = "run.task.$taskId"

    fun isChild(notification: StatusBarNotification): Boolean =
        notification.notification.group == TaskReminderReceiver.TASKS_GROUP_KEY &&
            notification.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0

    fun matches(notification: StatusBarNotification, taskId: Long): Boolean =
        (notification.tag == tag(taskId) && notification.id == 0) ||
            (notification.tag == null && notification.id == taskId.toInt() && isChild(notification))

    private fun cancelLegacy(manager: NotificationManager, taskId: Long) {
        // Older releases shared the untagged namespace with foreground services.
        // Only retire an identifiable task child, never the service at that ID.
        manager.activeNotifications.firstOrNull {
            it.tag == null && it.id == taskId.toInt() && isChild(it) &&
                it.notification.flags and Notification.FLAG_FOREGROUND_SERVICE == 0
        }?.let { manager.cancel(it.id) }
    }

    fun post(manager: NotificationManager, taskId: Long, notification: Notification) {
        cancelLegacy(manager, taskId)
        manager.notify(tag(taskId), 0, notification)
    }

    fun cancel(context: Context, taskId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(tag(taskId), 0)
        cancelLegacy(manager, taskId)
    }
}
