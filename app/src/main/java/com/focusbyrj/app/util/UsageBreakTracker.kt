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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.focusbyrj.app.service.BubbleService
import java.util.Calendar

/**
 * Monitors continuous application usage and total continuous screen time to gently
 * suggest well-timed breaks or healthy sleep in a supportive, calm tone.
 *
 * Rules:
 * 1. Night (10:30 PM - 5:00 AM):
 *    If single app continuous usage reaches 45 minutes, gently remind the user to sleep
 *    in a calm, natural tone highlighting the benefits of restorative rest.
 *
 * 2. Day (5:00 AM - 10:00 PM):
 *    - If single app continuous usage reaches 90 minutes (allowing momentary 2-3 minute interruptions
 *      such as quick text replies without resetting the counter), suggest taking a walk or mindful break.
 *    - If continuous total screen time reaches 2 hours (120 minutes), suggest a break regardless of app switches.
 *
 * 3. Extreme battery & resource efficiency:
 *    - No independent polling loop or background alarms.
 *    - Checked periodically (every ~10-15 seconds) inside the existing foreground check loop.
 *    - Emits notifications through the floating bubble preview pill and persistent chat log.
 */
object UsageBreakTracker {

    private const val PREFS_NAME = "usage_break_tracker_prefs"
    private const val KEY_LAST_NIGHT_ALERT_TIME = "last_night_sleep_alert_time"
    private const val KEY_LAST_DAY_SINGLE_ALERT_TIME = "last_day_single_alert_time"
    private const val KEY_LAST_DAY_TOTAL_ALERT_TIME = "last_day_total_alert_time"

    // Throttle repeat alerts of the same type for at least 30 minutes
    private const val ALERT_COOLDOWN_MS = 30 * 60 * 1000L

    // Thresholds
    private const val NIGHT_APP_USAGE_THRESHOLD_MS = 45 * 60 * 1000L // 45 mins
    private const val DAY_APP_USAGE_THRESHOLD_MS = 90 * 60 * 1000L   // 90 mins
    private const val DAY_TOTAL_SCREEN_THRESHOLD_MS = 120 * 60 * 1000L // 2 hours

    // Allowed brief diversion before resetting continuous single-app usage (e.g. 3 mins replying to a message)
    private const val MAX_APP_SWITCH_GRACE_MS = 3 * 60 * 1000L

    // Allowed screen-off gap before resetting continuous total screen time (e.g. quick screen lock < 2 mins)
    private const val MAX_SCREEN_OFF_GRACE_MS = 2 * 60 * 1000L

    // State tracking
    @Volatile
    private var trackedAppPackage: String? = null
    @Volatile
    private var trackedAppStartTime: Long = 0L
    @Volatile
    private var trackedAppLastActiveTime: Long = 0L

    // For temporary diversion (e.g., checking WhatsApp for 2 minutes)
    @Volatile
    private var diversionAppPackage: String? = null
    @Volatile
    private var diversionStartTime: Long = 0L

    @Volatile
    private var screenSessionStartTime: Long = 0L
    @Volatile
    private var screenLastActiveTime: Long = 0L

    @Volatile
    private var lastEvaluationTime: Long = 0L

    /**
     * Called whenever screen turns off.
     */
    fun onScreenOff() {
        val now = System.currentTimeMillis()
        screenLastActiveTime = now
        trackedAppLastActiveTime = now
    }

    /**
     * Called whenever screen turns on or user is present.
     */
    fun onScreenOn() {
        val now = System.currentTimeMillis()
        if (screenSessionStartTime == 0L || (now - screenLastActiveTime) > MAX_SCREEN_OFF_GRACE_MS) {
            screenSessionStartTime = now
            trackedAppStartTime = now
            trackedAppLastActiveTime = now
            diversionAppPackage = null
            diversionStartTime = 0L
        }
        screenLastActiveTime = now
    }

    /**
     * Periodic evaluation called from FocusBlockerService's foreground loop.
     * Evaluated roughly once every 10 seconds to conserve battery while maintaining high precision.
     */
    fun onForegroundPackageChecked(
        context: Context,
        foregroundPackage: String,
        isHomeScreenOrSystem: Boolean
    ) {
        val now = System.currentTimeMillis()

        // Throttle evaluation to at most once every 10 seconds to eliminate CPU/battery strain
        if (now - lastEvaluationTime < 10_000L) return
        lastEvaluationTime = now

        // Initialize total screen session if not yet started
        if (screenSessionStartTime == 0L) {
            screenSessionStartTime = now
        }
        screenLastActiveTime = now

        // 1. Update continuous single-app tracking
        if (isHomeScreenOrSystem || foregroundPackage.isBlank()) {
            // User is momentarily on home screen or system dialog; allow grace period
            if (diversionStartTime == 0L) {
                diversionStartTime = now
            } else if (now - diversionStartTime > MAX_APP_SWITCH_GRACE_MS) {
                // Extended time on launcher; reset tracked single app
                trackedAppPackage = null
                trackedAppStartTime = 0L
                diversionStartTime = 0L
            }
        } else {
            if (foregroundPackage == trackedAppPackage) {
                // Continued usage of primary tracked app (or returned within grace period)
                trackedAppLastActiveTime = now
                diversionAppPackage = null
                diversionStartTime = 0L
            } else if (trackedAppPackage == null) {
                // Start tracking new primary app
                trackedAppPackage = foregroundPackage
                trackedAppStartTime = now
                trackedAppLastActiveTime = now
                diversionAppPackage = null
                diversionStartTime = 0L
            } else {
                // Different app opened (e.g., switched to WhatsApp to read/reply to a text)
                if (diversionAppPackage != foregroundPackage) {
                    diversionAppPackage = foregroundPackage
                    diversionStartTime = now
                } else {
                    val diversionDuration = now - diversionStartTime
                    if (diversionDuration > MAX_APP_SWITCH_GRACE_MS) {
                        // Exceeded the 2-3 minute momentary threshold; user has genuinely switched apps
                        trackedAppPackage = foregroundPackage
                        trackedAppStartTime = diversionStartTime
                        trackedAppLastActiveTime = now
                        diversionAppPackage = null
                        diversionStartTime = 0L
                    }
                }
            }
        }

        // Calculate continuous durations
        val continuousAppUsageMs = if (trackedAppPackage != null && trackedAppStartTime > 0L) {
            now - trackedAppStartTime
        } else 0L

        val continuousTotalScreenMs = if (screenSessionStartTime > 0L) {
            now - screenSessionStartTime
        } else 0L

        // Determine current time period
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val timeInMinutes = hour * 60 + minute

        // Night time window: 10:30 PM (22:30 = 1350 mins) to 5:00 AM (300 mins)
        val isNightTime = timeInMinutes >= 1350 || timeInMinutes < 300

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (isNightTime) {
            // Night rule: 45 continuous minutes of single app usage
            if (continuousAppUsageMs >= NIGHT_APP_USAGE_THRESHOLD_MS) {
                val lastAlert = prefs.getLong(KEY_LAST_NIGHT_ALERT_TIME, 0L)
                if (now - lastAlert >= ALERT_COOLDOWN_MS) {
                    val appName = getAppFriendlyName(context, trackedAppPackage)
                    val message = AyvaDialogueEngine.getNightSleepSuggestion(context, appName)
                    sendBubbleReminder(context, message)
                    prefs.edit().putLong(KEY_LAST_NIGHT_ALERT_TIME, now).apply()
                }
            }
        } else {
            // Day rule 1: 90 continuous minutes on a single app (with grace period for quick messages)
            if (continuousAppUsageMs >= DAY_APP_USAGE_THRESHOLD_MS) {
                val lastAlert = prefs.getLong(KEY_LAST_DAY_SINGLE_ALERT_TIME, 0L)
                if (now - lastAlert >= ALERT_COOLDOWN_MS) {
                    val appName = getAppFriendlyName(context, trackedAppPackage)
                    val message = AyvaDialogueEngine.getDaySingleAppBreakSuggestion(context, appName)
                    sendBubbleReminder(context, message)
                    prefs.edit().putLong(KEY_LAST_DAY_SINGLE_ALERT_TIME, now).apply()
                }
            }

            // Day rule 2: 2 continuous hours (120 mins) total screen time across any apps
            if (continuousTotalScreenMs >= DAY_TOTAL_SCREEN_THRESHOLD_MS) {
                val lastAlert = prefs.getLong(KEY_LAST_DAY_TOTAL_ALERT_TIME, 0L)
                if (now - lastAlert >= ALERT_COOLDOWN_MS) {
                    val message = AyvaDialogueEngine.getDayTotalScreenBreakSuggestion(context)
                    sendBubbleReminder(context, message)
                    prefs.edit().putLong(KEY_LAST_DAY_TOTAL_ALERT_TIME, now).apply()
                }
            }
        }
    }

    /**
     * Emits the gentle break reminder directly into the floating bubble preview pill and chat log.
     */
    private fun sendBubbleReminder(context: Context, text: String) {
        val chatMessage = PersistedChatMessage(
            id = "usage_reminder_${System.currentTimeMillis()}",
            text = text,
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        // Add message to chat repository and increment unread badge so bubble previews it
        BubbleChatManager.addMessage(context, chatMessage, incrementBadge = true, updateActivity = false)

        // Make sure BubbleService is running and displays the notification preview
        BubbleService.startIfEnabled(context, ignoreSnooze = false)

        // Also broadcast trigger directly to BubbleService to immediately pop the preview pill
        val intent = Intent(BubbleService.ACTION_SHOW_ALERT_PREVIEW).apply {
            setPackage(context.packageName)
            putExtra(BubbleService.EXTRA_ALERT_TEXT, text)
        }
        context.sendBroadcast(intent)
    }

    private fun getAppFriendlyName(context: Context, packageName: String?): String {
        if (packageName.isNullOrBlank()) return "this app"
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    fun reset() {
        trackedAppPackage = null
        trackedAppStartTime = 0L
        trackedAppLastActiveTime = 0L
        diversionAppPackage = null
        diversionStartTime = 0L
        screenSessionStartTime = 0L
        screenLastActiveTime = 0L
        lastEvaluationTime = 0L
    }
}
