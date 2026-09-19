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

package com.focusbyrj.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitType
import com.focusbyrj.app.service.HabitReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Calendar

object HabitAlarmScheduler {

    const val EXTRA_HABIT_ID = "extra_habit_id"
    const val ACTION_HABIT_REMINDER = "com.focusbyrj.app.ACTION_HABIT_REMINDER"

    private fun getPendingIntent(context: Context, habitId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, HabitReceiver::class.java).apply {
            action = ACTION_HABIT_REMINDER
            putExtra(EXTRA_HABIT_ID, habitId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    /**
     * Unique request code generator based on habit ID to avoid collision with task reminder codes.
     * Habit request codes use the 500,000 range.
     */
    fun getRequestCode(habitId: Long): Int {
        return (500_000L + (habitId % 400_000L)).toInt()
    }

    fun scheduleHabitReminder(
        context: Context,
        habit: Habit,
        lastCompletedTimestamp: Long? = null,
        isGoalCompletedToday: Boolean = false
    ) {
        if (!habit.isReminderEnabled || habit.isArchived) {
            cancelHabitReminder(context, habit.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val nextTriggerTime = calculateNextTriggerTime(habit, lastCompletedTimestamp, isGoalCompletedToday) ?: return

        val pendingIntent = getPendingIntent(context, habit.id, getRequestCode(habit.id))

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTriggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTriggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTriggerTime,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun snoozeHabit(context: Context, habitId: Long, snoozeMinutes: Int = 30) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        val pendingIntent = getPendingIntent(context, habitId, getRequestCode(habitId))

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelHabitReminder(context: Context, habitId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = getPendingIntent(context, habitId, getRequestCode(habitId))
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAllHabits(context: Context) {
        val app = context.applicationContext as? FocusApplication ?: return
        val habitRepo = app.habitRepository
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val habits = habitRepo.getAllActiveHabits().firstOrNull() ?: emptyList()
                habits.filter { it.isReminderEnabled && !it.isArchived }.forEach { habit ->
                    scheduleHabitReminder(context, habit)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Computes next timestamp in epoch milliseconds.
     */
    fun calculateNextTriggerTime(
        habit: Habit,
        lastCompletedTimestamp: Long? = null,
        isGoalCompletedToday: Boolean = false
    ): Long? {
        val now = System.currentTimeMillis()

        return when (habit.type) {
            HabitType.ONCE_DAILY -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, habit.fixedReminderHour)
                    set(Calendar.MINUTE, habit.fixedReminderMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis <= now || isGoalCompletedToday) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                cal.timeInMillis
            }

            HabitType.INTERVAL_WINDOW -> {
                val todayStartCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, habit.windowStartHour)
                    set(Calendar.MINUTE, habit.windowStartMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val todayEndCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, habit.windowEndHour)
                    set(Calendar.MINUTE, habit.windowEndMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val intervalMs = (habit.totalIntervalMinutes.coerceAtLeast(5) * 60 * 1000L)
                val isOvernight = habit.windowEndHour < habit.windowStartHour ||
                        (habit.windowEndHour == habit.windowStartHour && habit.windowEndMinute <= habit.windowStartMinute)

                // If daily goal is already achieved today, roll over to the next day's active window start
                if (isGoalCompletedToday) {
                    if (!isOvernight) {
                        todayStartCal.add(Calendar.DAY_OF_YEAR, 1)
                        return todayStartCal.timeInMillis
                    } else {
                        val tomorrowStart = Calendar.getInstance().apply {
                            if (now >= todayStartCal.timeInMillis) {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                            set(Calendar.HOUR_OF_DAY, habit.windowStartHour)
                            set(Calendar.MINUTE, habit.windowStartMinute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        return tomorrowStart.timeInMillis
                    }
                }

                // If user logged recently within today's window, anchor next interval from that completion
                val anchorTime = if (lastCompletedTimestamp != null && lastCompletedTimestamp in (now - intervalMs)..now) {
                    lastCompletedTimestamp
                } else {
                    now
                }

                if (!isOvernight) {
                    when {
                        // Before window starts today
                        now < todayStartCal.timeInMillis -> {
                            todayStartCal.timeInMillis
                        }
                        // Inside active window
                        now in todayStartCal.timeInMillis..todayEndCal.timeInMillis -> {
                            val nextInterval = (anchorTime + intervalMs).coerceAtLeast(now + 60_000L)
                            if (nextInterval <= todayEndCal.timeInMillis) {
                                nextInterval
                            } else {
                                // Next interval would exceed window end, roll over to tomorrow's window start
                                todayStartCal.add(Calendar.DAY_OF_YEAR, 1)
                                todayStartCal.timeInMillis
                            }
                        }
                        // After window ends today
                        else -> {
                            todayStartCal.add(Calendar.DAY_OF_YEAR, 1)
                            todayStartCal.timeInMillis
                        }
                    }
                } else {
                    // Overnight window: e.g. 22:00 to 06:00
                    when {
                        // Currently in the early morning portion of the window (e.g. 03:00, before 06:00)
                        now <= todayEndCal.timeInMillis -> {
                            val nextInterval = now + intervalMs
                            if (nextInterval <= todayEndCal.timeInMillis) {
                                nextInterval
                            } else {
                                // Exceeds morning window end, rolls to today's start at 22:00
                                todayStartCal.timeInMillis
                            }
                        }
                        // Currently in daytime gap (e.g. 14:00, between 06:00 and 22:00)
                        now < todayStartCal.timeInMillis -> {
                            todayStartCal.timeInMillis
                        }
                        // Currently in evening portion of the window (e.g. 23:00, after 22:00)
                        else -> {
                            val tomorrowEndCal = (todayEndCal.clone() as Calendar).apply {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                            val nextInterval = now + intervalMs
                            if (nextInterval <= tomorrowEndCal.timeInMillis) {
                                nextInterval
                            } else {
                                todayStartCal.add(Calendar.DAY_OF_YEAR, 1)
                                todayStartCal.timeInMillis
                            }
                        }
                    }
                }
            }
        }
    }
}
