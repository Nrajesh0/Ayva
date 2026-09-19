package com.focusbyrj.app.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.service.TaskReminderReceiver
import com.focusbyrj.app.widget.TodoWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

object TaskReminderHelper {

    fun scheduleReminder(context: Context, task: Task) {
        if (task.dueDate == null || task.isCompleted) return

        val now = System.currentTimeMillis()
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            data = android.net.Uri.parse("focusreminder://task/${task.id}")
            putExtra("taskId", task.id)
            putExtra("taskTitle", task.title)
            putExtra("taskDetails", task.details)
            putExtra("taskDueDate", task.dueDate)
            putExtra("taskType", task.type.name)
            putExtra("taskRecurrence", task.recurrence.name)
            putExtra("isPersistent", task.isPersistent)
            putExtra("isPriority", task.isPriority)
        }

        // If the task due time has arrived or passed recently (within 30 mins), trigger immediately
        if (task.dueDate <= now) {
            if (now - task.dueDate <= 30 * 60 * 1000L) {
                kotlin.runCatching {
                    context.sendBroadcast(intent)
                }
            }
            return
        }

        val triggerTime = task.dueDate

        kotlin.runCatching {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                task.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }.onFailure {
            // Safe fallback
            kotlin.runCatching {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    task.id.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }
    }

    fun scheduleAllPendingReminders(context: Context) {
        val app = context.applicationContext as? FocusApplication ?: return
        CoroutineScope(Dispatchers.IO).launch {
            kotlin.runCatching {
                val now = System.currentTimeMillis()
                val tasks = app.database.taskDao().getActivePendingTasks(now)
                tasks.forEach { task ->
                    scheduleReminder(context, task)
                }
            }
        }
    }

    fun cancelReminder(context: Context, task: Task) {
        cancelReminderById(context, task.id)
    }

    fun cancelReminderById(context: Context, taskId: Long) {
        kotlin.runCatching {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            
            // 1. Cancel with Data URI (new standard)
            val intentWithData = Intent(context, TaskReminderReceiver::class.java).apply {
                data = android.net.Uri.parse("focusreminder://task/${taskId}")
            }
            val pendingWithData = PendingIntent.getBroadcast(
                context,
                taskId.toInt(),
                intentWithData,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingWithData)
            
            // 2. Cancel without Data URI (legacy fallback for any older lingering alarms)
            val intentLegacy = Intent(context, TaskReminderReceiver::class.java).apply {
                putExtra("taskId", taskId)
                putExtra("isPersistent", true)
            }
            val pendingLegacy = PendingIntent.getBroadcast(
                context,
                taskId.toInt(),
                intentLegacy,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingLegacy)

            // 3. Clear notification for this task and remove orphaned summary notification if no task notifications left
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(taskId.toInt())
            cleanUpTaskSummaryNotification(context, taskId)
        }
    }

    fun cleanUpTaskSummaryNotification(context: Context, dismissedTaskId: Long? = null) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNotifs = notificationManager.activeNotifications ?: emptyArray()
            val remainingTaskNotifs = activeNotifs.filter {
                it.id != TaskReminderReceiver.TASKS_SUMMARY_ID &&
                (dismissedTaskId == null || it.id != dismissedTaskId.toInt()) &&
                it.notification.group == TaskReminderReceiver.TASKS_GROUP_KEY
            }
            if (remainingTaskNotifs.size < 2) {
                notificationManager.cancel(TaskReminderReceiver.TASKS_SUMMARY_ID)
            }
        } else {
            notificationManager.cancel(TaskReminderReceiver.TASKS_SUMMARY_ID)
        }
    }

    fun completeTask(context: Context, taskId: Long, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext as? FocusApplication ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(taskId.toInt())
        cancelReminderById(context, taskId)
        com.focusbyrj.app.service.TaskReminderOverlayManager.hideOverlay()

        CoroutineScope(Dispatchers.IO).launch {
            kotlin.runCatching {
                val taskDao = app.database.taskDao()
                val existing = taskDao.getTaskById(taskId)
                if (existing != null && !existing.isCompleted) {
                    val updated = existing.copy(isCompleted = true, completedAt = System.currentTimeMillis())
                    taskDao.updateTask(updated)
                    FocusEconomyManager.completeTaskReward(existing.title, existing.isPriority, existing.type)
                    if (updated.recurrence != RecurrencePattern.NONE) {
                        val nextTask = generateNextRecurringTask(updated)
                        val newId = taskDao.insertTask(nextTask)
                        scheduleReminder(context, nextTask.copy(id = newId))
                    }
                    TodoWidgetProvider.updateAllWidgets(context)
                }
            }
            if (onDone != null) {
                withContext(Dispatchers.Main) {
                    kotlin.runCatching { onDone.invoke() }
                }
            }
        }
    }

    fun rescheduleTask(context: Context, taskId: Long, newDueDate: Long, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext as? FocusApplication ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(taskId.toInt())
        cancelReminderById(context, taskId)
        com.focusbyrj.app.service.TaskReminderOverlayManager.hideOverlay()

        CoroutineScope(Dispatchers.IO).launch {
            kotlin.runCatching {
                val taskDao = app.database.taskDao()
                val existing = taskDao.getTaskById(taskId)
                if (existing != null) {
                    val updated = existing.copy(dueDate = newDueDate, isCompleted = false, completedAt = null)
                    taskDao.updateTask(updated)
                    scheduleReminder(context, updated)
                    TodoWidgetProvider.updateAllWidgets(context)
                }
            }
            if (onDone != null) {
                withContext(Dispatchers.Main) {
                    kotlin.runCatching { onDone.invoke() }
                }
            }
        }
    }

    fun toggleTaskById(context: Context, taskId: Long, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext as? FocusApplication ?: return
        CoroutineScope(Dispatchers.IO).launch {
            kotlin.runCatching {
                val taskDao = app.database.taskDao()
                val existing = taskDao.getTaskById(taskId)
                if (existing != null) {
                    val newStatus = !existing.isCompleted
                    val updated = existing.copy(
                        isCompleted = newStatus,
                        completedAt = if (newStatus) System.currentTimeMillis() else null
                    )
                    taskDao.updateTask(updated)

                    if (updated.isCompleted) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        notificationManager?.cancel(taskId.toInt())
                        cancelReminderById(context, taskId)
                        FocusEconomyManager.completeTaskReward(existing.title, existing.isPriority, existing.type)
                        if (updated.recurrence != RecurrencePattern.NONE) {
                            val nextTask = generateNextRecurringTask(updated)
                            val newId = taskDao.insertTask(nextTask)
                            scheduleReminder(context, nextTask.copy(id = newId))
                        }
                    } else {
                        FocusEconomyManager.uncompleteTaskReward(existing.isPriority, existing.type)
                        scheduleReminder(context, updated)
                    }
                    TodoWidgetProvider.updateAllWidgets(context)
                }
            }
            if (onDone != null) {
                withContext(Dispatchers.Main) {
                    kotlin.runCatching { onDone.invoke() }
                }
            }
        }
    }

    fun ignoreTask(context: Context, taskId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(taskId.toInt())
        cancelReminderById(context, taskId)
        com.focusbyrj.app.service.TaskReminderOverlayManager.hideOverlay()
    }

    fun parseSmartDateTime(input: String): Long? {
        val lowerInput = input.lowercase(java.util.Locale.getDefault()).trim()
        val cal = Calendar.getInstance()
        
        // Pattern 1: Relative time ("in 2 hours", "15 mins")
        val relativePattern = java.util.regex.Pattern.compile("(?:in\\s+)?(\\d+)\\s*(m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days)")
        val relMatcher = relativePattern.matcher(lowerInput)
        if (relMatcher.find()) {
            val amount = relMatcher.group(1)?.toIntOrNull() ?: return null
            val unitStr = relMatcher.group(2) ?: ""
            when {
                unitStr.startsWith("m") -> cal.add(Calendar.MINUTE, amount)
                unitStr.startsWith("h") -> cal.add(Calendar.HOUR, amount)
                unitStr.startsWith("d") -> cal.add(Calendar.DAY_OF_YEAR, amount)
            }
            return cal.timeInMillis
        }

        // Pattern 2: Tomorrow absolute time ("tomorrow 5pm", "tomorrow at 10")
        val tomorrowPattern = java.util.regex.Pattern.compile("tomorrow(?:\\s+(?:at\\s+)?(\\d+)(?::(\\d+))?\\s*(am|pm)?)?")
        val tomMatcher = tomorrowPattern.matcher(lowerInput)
        if (tomMatcher.find()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val hourStr = tomMatcher.group(1)
            if (hourStr != null) {
                var hour = hourStr.toIntOrNull() ?: 9
                val min = tomMatcher.group(2)?.toIntOrNull() ?: 0
                val ampm = tomMatcher.group(3)
                if (ampm == "pm" && hour < 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, min)
                cal.set(Calendar.SECOND, 0)
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 9)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
            }
            return cal.timeInMillis
        }
        
        // Pattern 3: Absolute time today/tomorrow ("5pm", "at 10:30", "15:00", "at 8")
        val timePattern = java.util.regex.Pattern.compile("(?:^|\\s)(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?(?:$|\\s)")
        val timeMatcher = timePattern.matcher(lowerInput)
        if (timeMatcher.find()) {
            val hourRaw = timeMatcher.group(1)?.toIntOrNull() ?: return null
            val minRaw = timeMatcher.group(2)?.toIntOrNull() ?: 0
            val ampm = timeMatcher.group(3)

            val hasColon = timeMatcher.group(2) != null
            val hasAt = lowerInput.contains("at " + hourRaw)
            val isExact = lowerInput == hourRaw.toString() || lowerInput == "$hourRaw:$minRaw"
            if (ampm == null && !hasColon && !hasAt && !isExact) {
                return null
            }
            if (hourRaw !in 0..23 || minRaw !in 0..59) return null

            var hour = hourRaw
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            
            // Smart default for am/pm if omitted
            if (ampm == null && hour < 12) {
                val currentHour24 = cal.get(Calendar.HOUR_OF_DAY)
                if (currentHour24 >= 12 && hour + 12 > currentHour24) {
                    hour += 12 // Assumed PM
                }
            }
            
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minRaw)
            cal.set(Calendar.SECOND, 0)
            
            // if time has already passed today, schedule for tomorrow
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }
        
        return null
    }

    fun generateNextRecurringTask(completedTask: Task): Task {
        val baseDueDate = completedTask.dueDate ?: System.currentTimeMillis()
        
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = baseDueDate
        val now = System.currentTimeMillis()

        when (completedTask.recurrence) {
            RecurrencePattern.DAILY -> {
                do {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                } while (calendar.timeInMillis <= now)
            }
            RecurrencePattern.WEEKLY -> {
                do {
                    calendar.add(Calendar.WEEK_OF_YEAR, 1)
                } while (calendar.timeInMillis <= now)
            }
            RecurrencePattern.MONTHLY -> {
                val originalDay = calendar.get(Calendar.DAY_OF_MONTH)
                val originalHour = calendar.get(Calendar.HOUR_OF_DAY)
                val originalMin = calendar.get(Calendar.MINUTE)
                val originalSec = calendar.get(Calendar.SECOND)
                
                var targetMonth = calendar.get(Calendar.MONTH)
                var targetYear = calendar.get(Calendar.YEAR)
                
                do {
                    targetMonth++
                    if (targetMonth > Calendar.DECEMBER) {
                        targetMonth = Calendar.JANUARY
                        targetYear++
                    }
                    
                    calendar.set(Calendar.YEAR, targetYear)
                    calendar.set(Calendar.MONTH, targetMonth)
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    val maxDayInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                    calendar.set(Calendar.DAY_OF_MONTH, originalDay.coerceAtMost(maxDayInMonth))
                    calendar.set(Calendar.HOUR_OF_DAY, originalHour)
                    calendar.set(Calendar.MINUTE, originalMin)
                    calendar.set(Calendar.SECOND, originalSec)
                    calendar.set(Calendar.MILLISECOND, 0)
                } while (calendar.timeInMillis <= now)
            }
            RecurrencePattern.YEARLY -> {
                val wasFeb29 = (calendar.get(Calendar.MONTH) == Calendar.FEBRUARY && calendar.get(Calendar.DAY_OF_MONTH) == 29) ||
                        (calendar.get(Calendar.MONTH) == Calendar.FEBRUARY && calendar.get(Calendar.DAY_OF_MONTH) == 28 && 
                         (completedTask.details.contains("29") || completedTask.title.contains("29", ignoreCase = true)))

                val originalHour = calendar.get(Calendar.HOUR_OF_DAY)
                val originalMin = calendar.get(Calendar.MINUTE)
                val originalSec = calendar.get(Calendar.SECOND)

                do {
                    val nextYear = calendar.get(Calendar.YEAR) + 1
                    if (wasFeb29) {
                        calendar.set(Calendar.YEAR, nextYear)
                        calendar.set(Calendar.MONTH, Calendar.FEBRUARY)
                        if (SmartDateParser.isLeapYear(nextYear)) {
                            calendar.set(Calendar.DAY_OF_MONTH, 29)
                        } else {
                            calendar.set(Calendar.DAY_OF_MONTH, 28)
                        }
                        calendar.set(Calendar.HOUR_OF_DAY, originalHour)
                        calendar.set(Calendar.MINUTE, originalMin)
                        calendar.set(Calendar.SECOND, originalSec)
                        calendar.set(Calendar.MILLISECOND, 0)
                    } else {
                        calendar.add(Calendar.YEAR, 1)
                    }
                } while (calendar.timeInMillis <= now)
            }
            RecurrencePattern.NONE -> return completedTask
        }

        val updatedDetails = if ((completedTask.recurrence == RecurrencePattern.YEARLY) && 
            ((calendar.get(Calendar.MONTH) == Calendar.FEBRUARY && (calendar.get(Calendar.DAY_OF_MONTH) == 28 || calendar.get(Calendar.DAY_OF_MONTH) == 29))) &&
            (completedTask.details.contains("29") || completedTask.title.contains("29", ignoreCase = true)) &&
            !completedTask.details.contains("reminded on Feb 28th in non-leap years") &&
            !SmartDateParser.isLeapYear(calendar.get(Calendar.YEAR))
        ) {
            if (completedTask.details.isBlank()) "Note: Event is on Feb 29th (reminded on Feb 28th in non-leap years)" else "${completedTask.details}\nNote: Event is on Feb 29th (reminded on Feb 28th in non-leap years)"
        } else {
            completedTask.details
        }

        return completedTask.copy(
            id = 0, // new task
            isCompleted = false,
            completedAt = null,
            details = updatedDetails,
            dueDate = calendar.timeInMillis
        )
    }

    fun scheduleNaggingReminder(context: Context, taskId: Long, title: String, intervalMins: Int) {
        val triggerTime = System.currentTimeMillis() + (intervalMins * 60 * 1000L)
        kotlin.runCatching {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, TaskReminderReceiver::class.java).apply {
                data = android.net.Uri.parse("focusreminder://task/${taskId}")
                putExtra("taskId", taskId)
                putExtra("taskTitle", title)
                putExtra("isPersistent", true)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }.onFailure {
            kotlin.runCatching {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, TaskReminderReceiver::class.java).apply {
                    putExtra("taskId", taskId)
                    putExtra("taskTitle", title)
                    putExtra("isPersistent", true)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    taskId.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }
    }
}
