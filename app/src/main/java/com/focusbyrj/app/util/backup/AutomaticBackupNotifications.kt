package com.focusbyrj.app.util.backup

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.focusbyrj.app.MainActivity
import com.focusbyrj.app.R

internal enum class BackupNotificationOption(val channel: String, val title: String, val default: Boolean, val importance: Int) {
    PROGRESS("run_backup_progress", "Backup progress", true, NotificationManager.IMPORTANCE_LOW),
    COMPLETION("run_backup_complete", "Backup completion", false, NotificationManager.IMPORTANCE_LOW),
    FAILURE("run_backup_health", "Backup failures", true, NotificationManager.IMPORTANCE_DEFAULT)
}

internal class AutomaticBackupNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val preferences = context.getSharedPreferences("ayva_backup_notifications", Context.MODE_PRIVATE)

    fun isEnabled(option: BackupNotificationOption): Boolean =
        preferences.getBoolean(option.channel, option.default)

    fun setEnabled(option: BackupNotificationOption, enabled: Boolean) {
        preferences.edit().putBoolean(option.channel, enabled).apply()
        if (!enabled && option != BackupNotificationOption.PROGRESS) manager.cancel(alertId(option))
    }

    fun isPermissionMissing(): Boolean = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun isBlocked(option: BackupNotificationOption): Boolean =
        isPermissionMissing() || !manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(option.channel)?.importance == NotificationManager.IMPORTANCE_NONE

    fun progress(id: Int, message: String): Notification {
        val detailed = isEnabled(BackupNotificationOption.PROGRESS)
        val builder = builder(id, BackupNotificationOption.PROGRESS)
            .setContentTitle("Creating encrypted backup")
            .setContentText(if (detailed) message else "Automatic backup is running.")
            .setOngoing(true).setSilent(true)
        if (detailed) {
            val count = attachmentProgress(message)
            if (count == null) builder.setProgress(0, 0, true)
            else builder.setProgress(count.second, count.first, false)
        }
        return builder.build()
    }

    fun updateProgress(id: Int, message: String) {
        if (isBlocked(BackupNotificationOption.PROGRESS)) return
        runCatching { manager.notify(id, progress(id, message)) }
    }

    fun complete(retentionWarning: Boolean) {
        runCatching {
            manager.cancel(alertId(BackupNotificationOption.FAILURE))
            val option = if (retentionWarning && (!isEnabled(BackupNotificationOption.COMPLETION) || isBlocked(BackupNotificationOption.COMPLETION))) {
                BackupNotificationOption.FAILURE
            } else BackupNotificationOption.COMPLETION
            post(
                option,
                if (retentionWarning) "Backup verified with a cleanup warning" else "Automatic backup verified",
                if (retentionWarning) "Some older copies were kept. Open backup settings for details."
                else "Your encrypted backup was saved and verified."
            )
        }
    }

    fun failure() {
        runCatching {
            post(BackupNotificationOption.FAILURE, "Automatic backup needs attention",
                "Open backup settings to see the problem and retry.")
        }
    }

    private fun post(option: BackupNotificationOption, title: String, summary: String) {
        if (!isEnabled(option) || isBlocked(option)) return
        val id = alertId(option)
        manager.notify(id, builder(id, option).setContentTitle(title).setContentText(summary).setAutoCancel(true).build())
    }

    private fun builder(id: Int, option: BackupNotificationOption): NotificationCompat.Builder {
        manager.createNotificationChannel(NotificationChannel(option.channel, option.title, option.importance))
        val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java)
            .putExtra("navigate_to", "settings_automatic_backups"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, option.channel)
            .setSmallIcon(R.mipmap.ic_launcher).setContentIntent(open).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    }

    private fun alertId(option: BackupNotificationOption): Int =
        if (option == BackupNotificationOption.FAILURE) 8407 else 8409

    companion object {
        private val attachmentCount = Regex("""\battachment (\d+) of (\d+)$""")

        internal fun attachmentProgress(message: String): Pair<Int, Int>? {
            val match = attachmentCount.find(message) ?: return null
            val current = match.groupValues[1].toIntOrNull() ?: return null
            val total = match.groupValues[2].toIntOrNull() ?: return null
            return if (total > 0 && current in 0..total) current to total else null
        }
    }
}
