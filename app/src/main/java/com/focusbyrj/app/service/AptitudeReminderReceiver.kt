package com.focusbyrj.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focusbyrj.app.util.AptitudeManager
import com.focusbyrj.app.util.ArithmeticDifficulty
import com.focusbyrj.app.util.ArithmeticEngine
import com.focusbyrj.app.util.BubbleChatManager
import com.focusbyrj.app.util.PersistedChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.random.Random

class AptitudeReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_EXACT_DRILL = "com.focusbyrj.app.ACTION_EXACT_DRILL"
        const val ACTION_RANDOM_DRILL_1 = "com.focusbyrj.app.ACTION_RANDOM_DRILL_1"
        const val ACTION_RANDOM_DRILL_2 = "com.focusbyrj.app.ACTION_RANDOM_DRILL_2"
        
        private const val REQUEST_CODE_EXACT = 3000
        private const val REQUEST_CODE_1 = 3001
        private const val REQUEST_CODE_2 = 3002

        fun scheduleDrillReminders(context: Context) {
            val prefs = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("streak_notification_enabled", prefs.getBoolean("random_drills_notification_enabled", true))
            val isVacationMode = AptitudeManager.isVacationMode(context)
            
            if (!isEnabled || isVacationMode) {
                cancelAllReminders(context)
                return
            }

            val exactTime = prefs.getString("streak_notification_time", "") ?: ""
            if (exactTime.isNotBlank() && !exactTime.equals("Random", ignoreCase = true)) {
                // Exact time configured by user: cancel random slots and schedule exact daily alarm
                cancelRandomDrillReminders(context)
                scheduleExactSlot(context, exactTime)
            } else {
                // No exact time set -> schedule 2 randomized streak alerts a day between 9 AM and 8 PM
                cancelExactDrillReminder(context)
                // Slot 1: Random time between 9:00 AM (9) and 1:59 PM (13)
                scheduleRandomSlot(context, minHour = 9, maxHour = 13, action = ACTION_RANDOM_DRILL_1, requestCode = REQUEST_CODE_1)
                // Slot 2: Random time between 2:00 PM (14) and 7:59 PM (19) (within 8 PM)
                scheduleRandomSlot(context, minHour = 14, maxHour = 19, action = ACTION_RANDOM_DRILL_2, requestCode = REQUEST_CODE_2)
            }
        }

        // Backward-compatibility alias
        fun scheduleRandomDrillReminders(context: Context) {
            scheduleDrillReminders(context)
        }

        fun cancelAllReminders(context: Context) {
            cancelExactDrillReminder(context)
            cancelRandomDrillReminders(context)
        }

        fun cancelExactDrillReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, AptitudeReminderReceiver::class.java).apply { action = ACTION_EXACT_DRILL }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pi = PendingIntent.getBroadcast(context, REQUEST_CODE_EXACT, intent, flags)
            try {
                alarmManager.cancel(pi)
            } catch (_: Exception) {}
        }

        fun cancelRandomDrillReminders(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            
            val intent1 = Intent(context, AptitudeReminderReceiver::class.java).apply { action = ACTION_RANDOM_DRILL_1 }
            val intent2 = Intent(context, AptitudeReminderReceiver::class.java).apply { action = ACTION_RANDOM_DRILL_2 }
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pi1 = PendingIntent.getBroadcast(context, REQUEST_CODE_1, intent1, flags)
            val pi2 = PendingIntent.getBroadcast(context, REQUEST_CODE_2, intent2, flags)

            try {
                alarmManager.cancel(pi1)
                alarmManager.cancel(pi2)
            } catch (_: Exception) {}
        }

        private fun scheduleExactSlot(context: Context, timeStr: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            
            var hour = 18
            var minute = 0
            try {
                val cleaned = timeStr.trim()
                if (cleaned.contains("AM", ignoreCase = true) || cleaned.contains("PM", ignoreCase = true)) {
                    val parts = cleaned.split(":", " ").filter { it.isNotBlank() }
                    if (parts.size >= 3) {
                        var h = parts[0].toInt()
                        val m = parts[1].toInt()
                        val amPm = parts[2]
                        if (amPm.equals("PM", ignoreCase = true) && h != 12) h += 12
                        if (amPm.equals("AM", ignoreCase = true) && h == 12) h = 0
                        hour = h
                        minute = m
                    }
                } else {
                    val parts = cleaned.split(":")
                    if (parts.size >= 2) {
                        hour = parts[0].toInt()
                        minute = parts[1].toInt()
                    }
                }
            } catch (_: Exception) {}

            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val intent = Intent(context, AptitudeReminderReceiver::class.java).apply {
                action = ACTION_EXACT_DRILL
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_EXACT, intent, flags)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                }
            } catch (_: Exception) {}
        }

        private fun scheduleRandomSlot(
            context: Context,
            minHour: Int,
            maxHour: Int,
            action: String,
            requestCode: Int
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val now = Calendar.getInstance()
            
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)

            // If we are currently within or before today's window, prefer scheduling for remaining slots today
            val (chosenHour, chosenMinute, scheduleForTomorrow) = if (currentHour < maxHour) {
                val startHour = kotlin.math.max(minHour, currentHour)
                if (startHour == currentHour) {
                    val minMin = (currentMinute + 5).coerceAtMost(59)
                    if (minMin >= 55 && currentHour + 1 <= maxHour) {
                        // Move to next hour slot today
                        Triple(Random.nextInt(currentHour + 1, maxHour + 1), Random.nextInt(0, 60), false)
                    } else if (minMin < 55) {
                        Triple(currentHour, Random.nextInt(minMin, 60), false)
                    } else {
                        // Current hour exhausted and at maxHour -> schedule for tomorrow
                        Triple(Random.nextInt(minHour, maxHour + 1), Random.nextInt(0, 60), true)
                    }
                } else {
                    Triple(Random.nextInt(startHour, maxHour + 1), Random.nextInt(0, 60), false)
                }
            } else {
                // Today's window has already passed; pick random slot for tomorrow
                Triple(Random.nextInt(minHour, maxHour + 1), Random.nextInt(0, 60), true)
            }

            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, chosenHour)
                set(Calendar.MINUTE, chosenMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (scheduleForTomorrow || timeInMillis <= now.timeInMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val intent = Intent(context, AptitudeReminderReceiver::class.java).apply {
                this.action = action
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "FocusByRJ:AptitudeWakeLock")
        try { wakeLock?.acquire(5000L) } catch (_: Exception) {}

        try {
            val prefs = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("streak_notification_enabled", prefs.getBoolean("random_drills_notification_enabled", true))
            val isVacationMode = AptitudeManager.isVacationMode(context)
            
            if (!isEnabled || isVacationMode) return

        val profile = AptitudeManager.profileFlow.value
        val streakText = if (profile.currentStreak > 0) "🔥 *${profile.currentStreak}-Day Streak Active!*\n" else ""

        val titles = listOf(
            "⚡ *Quick Mental Math Challenge!*",
            "🧠 *Daily Aptitude Sprint Ready*",
            "🔥 *Keep Your Focus Streak Alive!*",
            "🎯 *60-Second Brain Workout*",
            "💎 *Arithmetic Practice Time*"
        )
        val selectedTitle = titles.random()

        val promptJson = JSONObject().apply {
            put("streak", profile.currentStreak)
            put("longestStreak", profile.longestStreak)
            put("bonusPercent", profile.streakBonusPercent)
            put("level", profile.level)
            put("title", profile.title)
            put("titleTier", profile.titleTier)
            put("promptTitle", selectedTitle)
        }.toString()

        val headline = if (profile.currentStreak > 0) {
            "🔥 *Don't miss out on your ${profile.currentStreak}-day streak!*"
        } else {
            "⚡ *Build your daily focus streak today!*"
        }

        val message = PersistedChatMessage(
            id = "streak_prompt_${System.currentTimeMillis()}",
            text = headline,
            isUser = false,
            timestamp = System.currentTimeMillis(),
            isStreakPrompt = true,
            streakPromptJson = promptJson
        )

        // Post directly into chat bubble and increment bubble unread badge (like morning briefings)
        BubbleChatManager.addMessage(context, message, incrementBadge = true)

        // Reschedule next occurrence
        val exactTime = prefs.getString("streak_notification_time", "") ?: ""
        if (action == ACTION_EXACT_DRILL && exactTime.isNotBlank() && !exactTime.equals("Random", ignoreCase = true)) {
            scheduleExactSlot(context, exactTime)
        } else if (action == ACTION_RANDOM_DRILL_1) {
            scheduleRandomSlot(context, minHour = 9, maxHour = 13, action = ACTION_RANDOM_DRILL_1, requestCode = REQUEST_CODE_1)
        } else if (action == ACTION_RANDOM_DRILL_2) {
            scheduleRandomSlot(context, minHour = 14, maxHour = 19, action = ACTION_RANDOM_DRILL_2, requestCode = REQUEST_CODE_2)
        }
        } finally {
            try { if (wakeLock != null && wakeLock.isHeld) wakeLock.release() } catch (_: Exception) {}
        }
    }
}
