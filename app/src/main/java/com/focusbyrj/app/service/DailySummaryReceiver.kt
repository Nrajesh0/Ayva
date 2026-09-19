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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.util.BubbleChatManager
import com.focusbyrj.app.util.FocusQuotes
import com.focusbyrj.app.util.PersistedChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class DailySummaryReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MORNING_SUMMARY = "com.focusbyrj.app.ACTION_MORNING_SUMMARY"
        const val ACTION_EVENING_SUMMARY = "com.focusbyrj.app.ACTION_EVENING_SUMMARY"
        private const val REQUEST_CODE_MORNING = 2001
        private const val REQUEST_CODE_EVENING = 2002

        fun scheduleDailySummaries(context: Context) {
            val prefs = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
            val morningTimeStr = prefs.getString("morning_brief_time", "08:00 AM") ?: "08:00 AM"
            val eveningTimeStr = prefs.getString("evening_brief_time", "09:00 PM") ?: "09:00 PM"

            scheduleSingleSummary(context, morningTimeStr, ACTION_MORNING_SUMMARY, REQUEST_CODE_MORNING)
            scheduleSingleSummary(context, eveningTimeStr, ACTION_EVENING_SUMMARY, REQUEST_CODE_EVENING)
        }

        private fun scheduleSingleSummary(
            context: Context,
            timeStr: String,
            action: String,
            requestCode: Int
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val triggerTime = computeNextTriggerTime(timeStr)

            val intent = Intent(context, DailySummaryReceiver::class.java).apply {
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
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } catch (_: Exception) {}
        }

        private fun computeNextTriggerTime(timeStr: String): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance()

            var hour = 8
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

            target.set(Calendar.HOUR_OF_DAY, hour)
            target.set(Calendar.MINUTE, minute)
            target.set(Calendar.SECOND, 0)
            target.set(Calendar.MILLISECOND, 0)

            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = context.applicationContext as? FocusApplication ?: return
        
        val pendingResult = goAsync()

        // Acquire WakeLock to prevent CPU from sleeping while generating summary
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "FocusByRJ:SummaryWakeLock")
        try {
            wakeLock?.acquire(10000L) // 10 seconds to generate and post summary
        } catch (_: Exception) {}

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_MORNING_SUMMARY -> {
                        handleMorningSummary(context, app)
                        // Reschedule next morning
                        scheduleDailySummaries(context)
                    }
                    ACTION_EVENING_SUMMARY -> {
                        handleEveningSummary(context, app)
                        // Reschedule next evening
                        scheduleDailySummaries(context)
                    }
                }
            } finally {
                try {
                    if (wakeLock != null && wakeLock.isHeld) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleMorningSummary(context: Context, app: FocusApplication) {
        val vocabRepo = app.vocabRepository
        val newIdiom = vocabRepo.getNextIdiomToLearn()
        val newOws = vocabRepo.getNextOwsToLearn()
        val revIdiom = vocabRepo.getRevisionIdiom(excludeId = newIdiom?.id)
        val revOws = vocabRepo.getRevisionOws(excludeId = newOws?.id)
        
        val vocabObj = org.json.JSONObject()
        if (newIdiom != null) {
            vocabObj.put("idiom", org.json.JSONObject().apply {
                put("id", newIdiom.id ?: -1)
                put("idiom", newIdiom.idiom)
                put("meaning", newIdiom.meaning)
            })
        }
        if (newOws != null) {
            vocabObj.put("ows", org.json.JSONObject().apply {
                put("id", newOws.id ?: -1)
                put("term", newOws.term)
                put("definition", newOws.definition)
            })
        }
        if (revIdiom != null) {
            vocabObj.put("rev_idiom", org.json.JSONObject().apply {
                put("id", revIdiom.id ?: -1)
                put("idiom", revIdiom.idiom)
                put("meaning", revIdiom.meaning)
            })
        }
        if (revOws != null) {
            vocabObj.put("rev_ows", org.json.JSONObject().apply {
                put("id", revOws.id ?: -1)
                put("term", revOws.term)
                put("definition", revOws.definition)
            })
        }

        val message = com.focusbyrj.app.util.PersistedChatMessage(
            id = "morning_${System.currentTimeMillis()}",
            text = "☀️ Good morning! Let's build your vocabulary today.",
            isUser = false,
            timestamp = System.currentTimeMillis(),
            isTaskSummary = false,
            isMorningBrief = true,
            isVocabBrief = true,
            vocabJson = vocabObj.toString()
        )
        com.focusbyrj.app.util.BubbleChatManager.addMessage(context, message, incrementBadge = true)
    }

    private suspend fun handleEveningSummary(context: Context, app: FocusApplication) {
        val vocabRepo = app.vocabRepository
        val newIdiom = vocabRepo.getNextIdiomToLearn()
        val newOws = vocabRepo.getNextOwsToLearn()
        val revIdiom = vocabRepo.getRevisionIdiom(excludeId = newIdiom?.id)
        val revOws = vocabRepo.getRevisionOws(excludeId = newOws?.id)
        
        val vocabObj = org.json.JSONObject()
        if (newIdiom != null) {
            vocabObj.put("idiom", org.json.JSONObject().apply {
                put("id", newIdiom.id ?: -1)
                put("idiom", newIdiom.idiom)
                put("meaning", newIdiom.meaning)
            })
        }
        if (newOws != null) {
            vocabObj.put("ows", org.json.JSONObject().apply {
                put("id", newOws.id ?: -1)
                put("term", newOws.term)
                put("definition", newOws.definition)
            })
        }
        if (revIdiom != null) {
            vocabObj.put("rev_idiom", org.json.JSONObject().apply {
                put("id", revIdiom.id ?: -1)
                put("idiom", revIdiom.idiom)
                put("meaning", revIdiom.meaning)
            })
        }
        if (revOws != null) {
            vocabObj.put("rev_ows", org.json.JSONObject().apply {
                put("id", revOws.id ?: -1)
                put("term", revOws.term)
                put("definition", revOws.definition)
            })
        }

        val message = com.focusbyrj.app.util.PersistedChatMessage(
            id = "evening_${System.currentTimeMillis()}",
            text = "🌙 Good evening! Time for your nightly vocab drip.",
            isUser = false,
            timestamp = System.currentTimeMillis(),
            isTaskSummary = false,
            isEveningBrief = true,
            isVocabBrief = true,
            vocabJson = vocabObj.toString()
        )
        com.focusbyrj.app.util.BubbleChatManager.addMessage(context, message, incrementBadge = true)
    }
}
