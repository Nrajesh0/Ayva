package com.focusbyrj.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusbyrj.app.util.TaskReminderHelper

class TaskActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COMPLETE_TASK = "com.focusbyrj.app.action.COMPLETE_TASK"
        const val ACTION_IGNORE_TASK = "com.focusbyrj.app.action.IGNORE_TASK"
        const val ACTION_SNOOZE_TASK = "com.focusbyrj.app.action.SNOOZE_TASK"
        const val ACTION_OPEN_RESCHEDULE = "com.focusbyrj.app.action.OPEN_RESCHEDULE"
        const val ACTION_SHOW_POPUP = "com.focusbyrj.app.action.SHOW_POPUP"
        const val ACTION_TASK_DISMISSED = "com.focusbyrj.app.action.TASK_DISMISSED"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_SNOOZE_MINUTES = "snoozeMinutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId == -1L) return

        val pendingResult = goAsync()

        when (intent.action) {
            ACTION_TASK_DISMISSED -> {
                TaskReminderHelper.cleanUpTaskSummaryNotification(context, taskId)
                pendingResult.finish()
            }
            ACTION_COMPLETE_TASK -> {
                TaskReminderHelper.completeTask(context, taskId) {
                    pendingResult.finish()
                }
            }
            ACTION_IGNORE_TASK -> {
                TaskReminderHelper.ignoreTask(context, taskId)
                pendingResult.finish()
            }
            ACTION_SNOOZE_TASK -> {
                val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 15)
                val newDueDate = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
                TaskReminderHelper.rescheduleTask(context, taskId, newDueDate) {
                    pendingResult.finish()
                }
            }
            ACTION_OPEN_RESCHEDULE -> {
                val title = intent.getStringExtra("taskTitle") ?: "Task Reminder"
                val details = intent.getStringExtra("taskDetails") ?: ""
                val dueDate = intent.getLongExtra("taskDueDate", System.currentTimeMillis())
                val typeStr = intent.getStringExtra("taskType") ?: "TASK"
                val recurrenceStr = intent.getStringExtra("taskRecurrence") ?: "NONE"
                val isPersistent = intent.getBooleanExtra("isPersistent", false)

                TaskReminderOverlayManager.showReminderOverlay(
                    context = context,
                    taskId = taskId,
                    taskTitle = title,
                    taskDetails = details,
                    taskDueDate = dueDate,
                    taskTypeStr = typeStr,
                    taskRecurrenceStr = recurrenceStr,
                    isPersistent = isPersistent,
                    openRescheduleInitially = true
                )
                pendingResult.finish()
            }
            ACTION_SHOW_POPUP -> {
                val title = intent.getStringExtra("taskTitle") ?: "Task Reminder"
                val details = intent.getStringExtra("taskDetails") ?: ""
                val dueDate = intent.getLongExtra("taskDueDate", System.currentTimeMillis())
                val typeStr = intent.getStringExtra("taskType") ?: "TASK"
                val recurrenceStr = intent.getStringExtra("taskRecurrence") ?: "NONE"
                val isPersistent = intent.getBooleanExtra("isPersistent", false)

                TaskReminderOverlayManager.showReminderOverlay(
                    context = context,
                    taskId = taskId,
                    taskTitle = title,
                    taskDetails = details,
                    taskDueDate = dueDate,
                    taskTypeStr = typeStr,
                    taskRecurrenceStr = recurrenceStr,
                    isPersistent = isPersistent,
                    openRescheduleInitially = false
                )
                pendingResult.finish()
            }
            else -> {
                pendingResult.finish()
            }
        }
    }
}
