package com.focusbyrj.app.util

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.Uri

/** Full identities are shared by delivery, feedback, snooze and cancellation. */
internal object HabitNotifications {
    const val ID = 0
    fun tag(habitId: Long) = "run.habit.$habitId"
    fun actionUri(habitId: Long, action: String): Uri = Uri.Builder()
        .scheme("runhabit").authority("notification").appendPath(habitId.toString()).appendPath(action).build()
    fun post(context: Context, manager: NotificationManager, habitId: Long, notification: Notification) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        manager.notify(tag(habitId), ID, notification)
    }
    fun cancel(manager: NotificationManager, habitId: Long) = manager.cancel(tag(habitId), ID)
    fun cancel(context: Context, habitId: Long) {
        context.getSystemService(NotificationManager::class.java)?.let { cancel(it, habitId) }
    }
}
