/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
            com.focusbyrj.app.util.HabitAlarmScheduler.rescheduleAllHabits(context)
            
            // Reschedule all task reminders on boot or update
            val app = context.applicationContext as FocusApplication
            val repository = app.taskRepository
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val tasks = repository.allTasks.firstOrNull() ?: emptyList()
                    tasks.filter { !it.isCompleted && it.dueDate != null }.forEach { task ->
                        TaskReminderHelper.scheduleReminder(context, task)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}