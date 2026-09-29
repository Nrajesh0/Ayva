/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.util.TaskReminderHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            FocusBlockerService.startService(context)
            com.focusbyrj.app.service.BubbleService.clearSnooze(context)
            com.focusbyrj.app.service.BubbleService.startIfEnabled(context)
            DailySummaryReceiver.scheduleDailySummaries(context)
            AptitudeReminderReceiver.scheduleRandomDrillReminders(context)
            val app = context.applicationContext as? FocusApplication
            if (app == null) {
                pendingResult.finish()
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 1. Reschedule all habit reminders
                    try {
                        val habits = app.habitRepository.getAllActiveHabits().firstOrNull() ?: emptyList()
                        habits.filter { it.isReminderEnabled && !it.isArchived }.forEach { habit ->
                            com.focusbyrj.app.util.HabitAlarmScheduler.scheduleHabitReminder(context, habit)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // 2. Reschedule all task reminders on boot or update
                    try {
                        val tasks = app.taskRepository.allTasks.firstOrNull() ?: emptyList()
                        tasks.filter { !it.isCompleted && !it.isTrashed && it.dueDate != null }.forEach { task ->
                            TaskReminderHelper.scheduleReminder(context, task)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}