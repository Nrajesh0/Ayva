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

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.R
import com.focusbyrj.app.data.HabitType
import com.focusbyrj.app.util.HabitAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HabitActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INCREMENT_HABIT = "com.focusbyrj.app.ACTION_INCREMENT_HABIT"
        const val ACTION_SNOOZE_HABIT = "com.focusbyrj.app.ACTION_SNOOZE_HABIT"
        const val ACTION_DISMISS_HABIT = "com.focusbyrj.app.ACTION_DISMISS_HABIT"

        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (habitId == -1L) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ensure floating overlay is dismissed when notification action is clicked
                HabitFloatingOverlayManager.dismissOverlay()

                val app = appContext as? FocusApplication ?: return@launch
                val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                when (intent.action) {
                    ACTION_INCREMENT_HABIT -> {
                        val habit = app.habitRepository.getHabitById(habitId) ?: return@launch
                        val updatedLog = app.habitRepository.incrementHabitProgress(habitId)

                        val completed = updatedLog.completedCount
                        val target = habit.targetPerDay
                        val isGoalMet = completed >= target

                        // Reward with economy EXP & Gold
                        val xpReward = if (isGoalMet) 25 else 10
                        val goldReward = if (isGoalMet) 15 else 5
                        com.focusbyrj.app.util.FocusEconomyManager.addRewards(xpReward, goldReward)

                        val feedbackTitle = if (isGoalMet) {
                            "${habit.iconEmoji} Daily Goal Achieved! 🎉 (+${xpReward} XP)"
                        } else {
                            "${habit.iconEmoji} Progress Logged! (+${xpReward} XP)"
                        }

                        val feedbackText = if (habit.type == HabitType.INTERVAL_WINDOW) {
                            "$completed of $target completed today • Great rhythm!"
                        } else {
                            "Completed for today! Streak continued 🔥"
                        }

                        // Update existing notification to visually reward user
                        val updatedNotification = NotificationCompat.Builder(appContext, HabitReceiver.CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_app_logo)
                            .setContentTitle(feedbackTitle)
                            .setContentText(feedbackText)
                            .setProgress(target, completed, false)
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                            .setAutoCancel(true)
                            .setTimeoutAfter(4000L) // Auto dismiss after 4 seconds
                            .apply {
                                try {
                                    color = Color.parseColor(habit.colorHex)
                                } catch (_: Exception) {}
                            }
                            .build()

                        if (notificationId != -1) {
                            notificationManager.notify(notificationId, updatedNotification)
                        }

                        // Reschedule next reminder for interval habit or next day if goal is completed
                        HabitAlarmScheduler.scheduleHabitReminder(
                            appContext,
                            habit,
                            updatedLog.lastCompletedTimestamp,
                            isGoalCompletedToday = isGoalMet
                        )
                    }

                    ACTION_SNOOZE_HABIT -> {
                        if (notificationId != -1) {
                            notificationManager.cancel(notificationId)
                            cleanUpHabitSummary(notificationManager, notificationId)
                        }
                        HabitAlarmScheduler.snoozeHabit(appContext, habitId, 30)
                    }

                    ACTION_DISMISS_HABIT -> {
                        if (notificationId != -1) {
                            notificationManager.cancel(notificationId)
                            cleanUpHabitSummary(notificationManager, notificationId)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun cleanUpHabitSummary(notificationManager: NotificationManager, dismissedNotificationId: Int? = null) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val activeNotifs = notificationManager.activeNotifications ?: emptyArray()
            val activeHabits = activeNotifs.filter {
                it.id != HabitReceiver.HABITS_SUMMARY_ID &&
                (dismissedNotificationId == null || it.id != dismissedNotificationId) &&
                it.notification.group == HabitReceiver.HABITS_GROUP_KEY
            }
            if (activeHabits.size < 2) {
                notificationManager.cancel(HabitReceiver.HABITS_SUMMARY_ID)
            }
        } else {
            notificationManager.cancel(HabitReceiver.HABITS_SUMMARY_ID)
        }
    }
}
