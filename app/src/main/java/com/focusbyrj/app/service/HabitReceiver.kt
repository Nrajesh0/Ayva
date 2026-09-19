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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.MainActivity
import com.focusbyrj.app.R
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitType
import com.focusbyrj.app.util.HabitAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HabitReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "habit_nudges"
        const val CHANNEL_NAME = "Habit Nudges"
        const val NOTIFICATION_ID_OFFSET = 500_000
        const val HABITS_GROUP_KEY = "com.focusbyrj.app.HABITS_GROUP"
        const val HABITS_SUMMARY_ID = 999_901

        fun getNotificationId(habitId: Long): Int {
            return (NOTIFICATION_ID_OFFSET + (habitId % 400_000L)).toInt()
        }

        fun createHabitNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Gentle, habit-forming reminders and streak tracking"
                    enableLights(true)
                    lightColor = Color.parseColor("#38BDF8")
                    enableVibration(true)
                    // Crisp double-tap haptic distinct from alarms
                    vibrationPattern = longArrayOf(0, 70, 80, 90)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun getActionLabelForHabit(habit: Habit): String {
            val titleLower = habit.title.lowercase()
            return when {
                habit.iconEmoji == "💧" || titleLower.contains("water") || titleLower.contains("hydrat") -> "💧 Drank (+1)"
                habit.iconEmoji == "💊" || titleLower.contains("med") || titleLower.contains("vitamin") || titleLower.contains("pill") -> "💊 Taken"
                habit.iconEmoji == "📖" || titleLower.contains("read") || titleLower.contains("book") -> "📖 Read (+1)"
                habit.iconEmoji == "🧘" || titleLower.contains("meditat") || titleLower.contains("breath") -> "🧘 Done"
                habit.iconEmoji == "🧍" || titleLower.contains("stretch") || titleLower.contains("posture") -> "🧍 Stretched"
                else -> "${habit.iconEmoji} Done (+1)"
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(HabitAlarmScheduler.EXTRA_HABIT_ID, -1L)
        if (habitId == -1L) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Temporary wake lock for guaranteed delivery
                val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "FocusByRJ:HabitReminderWakeLock"
                )
                try {
                    wakeLock?.acquire(3000L)
                } catch (_: Exception) {}

                val app = appContext as? FocusApplication ?: return@launch
                val habit = app.habitRepository.getHabitById(habitId) ?: return@launch

                if (!habit.isReminderEnabled || habit.isArchived) {
                    HabitAlarmScheduler.cancelHabitReminder(appContext, habitId)
                    return@launch
                }

                // Check today's progress
                val todayStr = app.habitRepository.getTodayDateString()
                val todayLog = app.database.habitDao().getLogForHabitAndDate(habitId, todayStr)
                val completedCount = todayLog?.completedCount ?: 0
                val targetCount = habit.targetPerDay

                // Calculate current habit streak
                val currentStreak = try {
                    app.habitRepository.calculateStreaks(habitId, targetCount, todayLog).first
                } catch (_: Exception) {
                    0
                }

                // Natural, contextual non-repeating micro-copy quote
                val naturalQuote = com.focusbyrj.app.util.HabitMicroCopyProvider.getNaturalQuote(
                    context = appContext,
                    habit = habit,
                    completedCount = completedCount,
                    targetCount = targetCount,
                    streakDays = currentStreak
                )

                // If daily target is already completed today, don't interrupt the user with popups/alarms!
                if (completedCount >= targetCount) {
                    // Reschedule for next day
                    HabitAlarmScheduler.scheduleHabitReminder(appContext, habit, isGoalCompletedToday = true)
                    return@launch
                }

                // Check preferences for top notifications (default false: floating window is primary)
                val prefs = appContext.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
                val enableTopNotifications = prefs.getBoolean("habit_top_notifications", false)
                val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.provider.Settings.canDrawOverlays(appContext)
                } else true

                if (enableTopNotifications || !hasOverlayPermission) {
                    createHabitNotificationChannel(appContext)
                    val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    val notificationId = getNotificationId(habit.id)

                    // 1. Content Intent (Tapping the notification opens the app into the habits dashboard)
                    val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("navigate_to", "habits")
                        putExtra("habit_id", habit.id)
                    }
                    val openAppPendingIntent = PendingIntent.getActivity(
                        appContext,
                        notificationId,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // 2. Action: 1-Tap Log (+1)
                    val logActionIntent = Intent(appContext, HabitActionReceiver::class.java).apply {
                        action = HabitActionReceiver.ACTION_INCREMENT_HABIT
                        putExtra(HabitActionReceiver.EXTRA_HABIT_ID, habit.id)
                        putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                    }
                    val logPendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        notificationId + 1000,
                        logActionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // 3. Action: Snooze (+30m)
                    val snoozeActionIntent = Intent(appContext, HabitActionReceiver::class.java).apply {
                        action = HabitActionReceiver.ACTION_SNOOZE_HABIT
                        putExtra(HabitActionReceiver.EXTRA_HABIT_ID, habit.id)
                        putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                    }
                    val snoozePendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        notificationId + 2000,
                        snoozeActionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val progressText = if (habit.type == HabitType.INTERVAL_WINDOW) {
                        "$completedCount of $targetCount completed today"
                    } else {
                        if (completedCount >= targetCount) "Goal reached for today! 🎉" else "Target: $targetCount today"
                    }

                    val actionLabel = getActionLabelForHabit(habit)
                    val habitColor = try {
                        Color.parseColor(habit.colorHex)
                    } catch (_: Exception) {
                        Color.parseColor("#0284C7")
                    }

                    // 2026 Custom RemoteViews for Notification
                    val collapsedRemoteViews = android.widget.RemoteViews(appContext.packageName, R.layout.notification_habit_custom).apply {
                        setTextViewText(R.id.notif_emoji, habit.iconEmoji)
                        setTextViewText(R.id.notif_title, habit.title)
                        setTextViewText(R.id.notif_subtitle, progressText)
                        setTextViewText(R.id.notif_action_btn, actionLabel)
                        setOnClickPendingIntent(R.id.notif_action_btn, logPendingIntent)
                        setOnClickPendingIntent(R.id.notification_root, openAppPendingIntent)
                    }

                    val expandedRemoteViews = android.widget.RemoteViews(appContext.packageName, R.layout.notification_habit_expanded).apply {
                        setTextViewText(R.id.notif_exp_emoji, habit.iconEmoji)
                        setTextViewText(R.id.notif_exp_title, habit.title)
                        setTextViewText(R.id.notif_exp_subtitle, "$progressText • $naturalQuote")
                        if (habit.description.isNotBlank()) {
                            setTextViewText(R.id.notif_exp_notes, habit.description)
                            setViewVisibility(R.id.notif_exp_notes, android.view.View.VISIBLE)
                        } else {
                            setViewVisibility(R.id.notif_exp_notes, android.view.View.GONE)
                        }
                        if (targetCount > 0) {
                            setProgressBar(R.id.notif_exp_progress, targetCount, completedCount, false)
                            setViewVisibility(R.id.notif_exp_progress, android.view.View.VISIBLE)
                        } else {
                            setViewVisibility(R.id.notif_exp_progress, android.view.View.GONE)
                        }
                        setTextViewText(R.id.notif_exp_action_btn, actionLabel)
                        setOnClickPendingIntent(R.id.notif_exp_action_btn, logPendingIntent)
                        setOnClickPendingIntent(R.id.notif_exp_snooze_btn, snoozePendingIntent)
                        setOnClickPendingIntent(R.id.notification_expanded_root, openAppPendingIntent)
                    }

                    val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_app_logo)
                        .setContentTitle("${habit.iconEmoji} ${habit.title}")
                        .setContentText(progressText)
                        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                        .setCustomContentView(collapsedRemoteViews)
                        .setCustomBigContentView(expandedRemoteViews)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setContentIntent(openAppPendingIntent)
                        .setAutoCancel(true)
                        .setColor(habitColor)
                        .setColorized(true)
                        .setGroup(HABITS_GROUP_KEY)
                        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                        .addAction(0, actionLabel, logPendingIntent)
                        .addAction(0, "⏳ +30m", snoozePendingIntent)

                    notificationManager.notify(notificationId, builder.build())

                    // Clean group summary for expandable notification drawer
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        val activeNotifs = notificationManager.activeNotifications ?: emptyArray()
                        val activeHabits = activeNotifs.filter {
                            it.id != HABITS_SUMMARY_ID && it.notification.group == HABITS_GROUP_KEY
                        }
                        if (activeHabits.size >= 2) {
                            val inboxStyle = NotificationCompat.InboxStyle().setSummaryText("Habit Reminders")
                            activeHabits.take(5).forEach {
                                val t = it.notification.extras?.getCharSequence(NotificationCompat.EXTRA_TITLE)
                                if (!t.isNullOrBlank()) {
                                    inboxStyle.addLine(t)
                                }
                            }
                            val summaryNotification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                                .setSmallIcon(R.drawable.ic_app_logo)
                                .setContentTitle("Habit Reminders")
                                .setContentText("${activeHabits.size} habits scheduled")
                                .setStyle(inboxStyle)
                                .setGroup(HABITS_GROUP_KEY)
                                .setGroupSummary(true)
                                .setAutoCancel(true)
                                .setPriority(NotificationCompat.PRIORITY_LOW)
                                .build()
                            notificationManager.notify(HABITS_SUMMARY_ID, summaryNotification)
                        } else {
                            notificationManager.cancel(HABITS_SUMMARY_ID)
                        }
                    }
                }

                // 2026 Floating Dynamic Island Window Overlay
                HabitFloatingOverlayManager.showHabitOverlay(
                    context = appContext,
                    habit = habit,
                    completedCount = completedCount,
                    targetCount = targetCount,
                    currentStreak = currentStreak
                )

                // Automatically schedule the next interval / occurrence
                HabitAlarmScheduler.scheduleHabitReminder(appContext, habit, todayLog?.lastCompletedTimestamp)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
