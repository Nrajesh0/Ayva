package com.focusbyrj.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.focusbyrj.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Direct navigation action target from Ayva talk / guidance responses.
 */
sealed class TalkAction(
    val label: String,
    val emoji: String,
    val description: String? = null
) {
    data class NavigateAppScreen(
        val route: String,
        val buttonLabel: String,
        val iconEmoji: String = "🚀"
    ) : TalkAction(buttonLabel, iconEmoji)

    data class OpenSystemSetting(
        val intentAction: String,
        val buttonLabel: String,
        val iconEmoji: String = "⚙️",
        val packageUri: Boolean = false
    ) : TalkAction(buttonLabel, iconEmoji)

    data class AskQuery(
        val query: String,
        val buttonLabel: String,
        val iconEmoji: String = "💬"
    ) : TalkAction(buttonLabel, iconEmoji)

    data class DirectPrefUpdate(
        val prefKey: String,
        val prefType: String, // "int", "boolean", "string"
        val targetValue: String,
        val displayValue: String,
        val buttonLabel: String,
        val iconEmoji: String = "⚡"
    ) : TalkAction(buttonLabel, iconEmoji)

    data class RoutineToggle(
        val scheduleId: Int,
        val isEnabled: Boolean,
        val routineName: String,
        val buttonLabel: String,
        val iconEmoji: String = "📅"
    ) : TalkAction(buttonLabel, iconEmoji)

    fun execute(context: Context): Boolean {
        return try {
            when (this) {
                is NavigateAppScreen -> {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        putExtra("navigate_to", route)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(intent)
                    true
                }
                is OpenSystemSetting -> {
                    val intent = Intent(intentAction).apply {
                        if (packageUri) {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
                is AskQuery -> {
                    val intent = Intent(context, com.focusbyrj.app.ui.screens.BubbleChatActivity::class.java).apply {
                        putExtra("prefill_query", query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(intent)
                    true
                }
                is RoutineToggle -> {
                    val app = context.applicationContext as? com.focusbyrj.app.FocusApplication
                    if (app != null) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            val sched = app.database.scheduleDao().getScheduleById(scheduleId)
                            if (sched != null) {
                                app.database.scheduleDao().insertSchedule(sched.copy(isEnabled = isEnabled))
                            }
                        }
                        true
                    } else {
                        false
                    }
                }
                is DirectPrefUpdate -> {
                    val prefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
                    val bubblePrefs = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
                    when (prefType) {
                        "int" -> {
                            val v = targetValue.toIntOrNull() ?: 0
                            prefs.edit().putInt(prefKey, v).apply()
                            bubblePrefs.edit().putInt(prefKey, v).apply()
                        }
                        "boolean" -> {
                            val b = targetValue.toBooleanStrictOrNull() ?: false
                            prefs.edit().putBoolean(prefKey, b).apply()
                            bubblePrefs.edit().putBoolean(prefKey, b).apply()
                        }
                        "string" -> {
                            prefs.edit().putString(prefKey, targetValue).apply()
                            bubblePrefs.edit().putString(prefKey, targetValue).apply()
                        }
                    }
                    if (prefKey == "vacation_mode") {
                        val b = targetValue.toBooleanStrictOrNull() ?: false
                        AptitudeManager.setVacationMode(context, b)
                    } else if (prefKey == "streak_notification_time" || prefKey == "streak_notification_enabled") {
                        com.focusbyrj.app.service.AptitudeReminderReceiver.scheduleDrillReminders(context)
                    } else if (prefKey == "morning_brief_time" || prefKey == "evening_brief_time") {
                        com.focusbyrj.app.service.DailySummaryReceiver.scheduleDailySummaries(context)
                    }
                    true
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
