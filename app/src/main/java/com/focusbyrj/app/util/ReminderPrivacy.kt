package com.focusbyrj.app.util

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.focusbyrj.app.R
import com.focusbyrj.app.service.HabitReceiver
import com.focusbyrj.app.service.TaskReminderReceiver

internal object ReminderPrivacy {
    const val KEY = "notification_lock_screen_content"
    fun allowsContent(context: Context) = context.getSharedPreferences("focus_prefs", 0).getBoolean(KEY, false)
    fun visibility(context: Context) = if (allowsContent(context)) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE
    fun publicVersion(context: Context, channel: String): Notification = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_app_logo).setContentTitle(context.getString(R.string.reminder_private_title, context.getString(R.string.app_name)))
        .setContentText(context.getString(R.string.reminder_private_body, context.getString(R.string.app_name))).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
    fun configure(builder: NotificationCompat.Builder, context: Context, channel: String): NotificationCompat.Builder =
        builder.setVisibility(visibility(context)).setPublicVersion(publicVersion(context, channel))

    fun setAllowsContent(context: Context, enabled: Boolean) {
        check(context.getSharedPreferences("focus_prefs", 0).edit().putBoolean(KEY, enabled).commit()) {
            "Could not save reminder privacy"
        }
        refreshActive(context)
    }

    /** Reconcile persisted policy after restart or a restore, including stale public copies. */
    fun refreshActive(context: Context) {
        val enabled = allowsContent(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val active = try { manager.activeNotifications } catch (_: Exception) {
            android.util.Log.w("ReminderPrivacy", "Notification privacy refresh deferred")
            return
        }
        active.filter {
            it.notification.group in setOf(HabitReceiver.HABITS_GROUP_KEY, TaskReminderReceiver.TASKS_GROUP_KEY) ||
                it.notification.channelId == "planned_work"
        }.forEach { entry ->
            if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (!enabled) runCatching { manager.cancel(entry.tag, entry.id) }
                return@forEach
            }
            try {
                val updated = Notification.Builder.recoverBuilder(context, entry.notification)
                    .setVisibility(visibility(context)).setPublicVersion(publicVersion(context, entry.notification.channelId)).build()
                manager.notify(entry.tag, entry.id, updated)
            } catch (_: Exception) {
                // An old public notification must not remain visible when redaction is requested.
                if (!enabled) runCatching { manager.cancel(entry.tag, entry.id) }
            }
        }
    }
}
