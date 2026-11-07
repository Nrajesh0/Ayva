package com.focusbyrj.app.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.focusbyrj.app.ui.screens.TaskReminderPopupActivity

/** A notification tap remains usable when the optional overlay grant is absent. */
internal object TaskReminderTap {
    fun intent(context: Context, taskId: Long, source: Intent, overlay: PendingIntent, reschedule: Boolean = false): PendingIntent {
        if (PermissionUtils.hasOverlayPermission(context)) return overlay
        val activity = Intent(source).setClass(context, TaskReminderPopupActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.Builder().scheme("runtask").authority("notification").appendPath(taskId.toString())
                .appendPath(if (reschedule) "reschedule" else "open").build())
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(TaskReminderPopupActivity.EXTRA_TASK_ID, taskId)
            .putExtra(TaskReminderPopupActivity.EXTRA_OPEN_RESCHEDULE, reschedule)
        return PendingIntent.getActivity(context, 0, activity, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
