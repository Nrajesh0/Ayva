package com.focusbyrj.app.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

object DndHelper {
    fun hasDndPermission(context: Context): Boolean {
        return kotlin.runCatching {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.isNotificationPolicyAccessGranted == true
        }.getOrDefault(false)
    }

    fun requestDndPermission(context: Context) {
        kotlin.runCatching {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun setDndMode(context: Context, enable: Boolean) {
        kotlin.runCatching {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager?.isNotificationPolicyAccessGranted == true) {
                val filter = if (enable) {
                    NotificationManager.INTERRUPTION_FILTER_NONE
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                }
                notificationManager.setInterruptionFilter(filter)
            }
        }
    }
}
