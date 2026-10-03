/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object SettingsScreenHelper {
    /**
     * Safely constructs the widget configuration intent with FLAG_ACTIVITY_NEW_TASK,
     * preventing AndroidRuntimeException when invoked from non-Activity contexts.
     */
    fun createWidgetConfigureIntent(context: Context): Intent {
        return Intent(context, com.focusbyrj.app.widget.TodoWidgetConfigureActivity::class.java).apply {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, com.focusbyrj.app.widget.TodoWidgetProvider::class.java))
                val id = if (ids != null && ids.isNotEmpty()) ids[0] else 0
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            } catch (_: Exception) {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private val VALID_INTERVALS = listOf(5, 10, 15, 30, 60, 120, 180, 240, 300, 360)

    /**
     * Computes the next valid reminder interval on increment or decrement.
     * If the current value is not in VALID_INTERVALS (e.g. corrupted prefs), snaps to the nearest valid interval.
     */
    fun getNextReminderInterval(current: Int, isIncrement: Boolean): Int {
        val idx = VALID_INTERVALS.indexOf(current)
        return if (idx != -1) {
            if (isIncrement) {
                if (idx < VALID_INTERVALS.size - 1) VALID_INTERVALS[idx + 1] else VALID_INTERVALS[idx]
            } else {
                if (idx > 0) VALID_INTERVALS[idx - 1] else VALID_INTERVALS[idx]
            }
        } else {
            // Snap to nearest valid interval
            if (isIncrement) {
                VALID_INTERVALS.firstOrNull { it > current } ?: 360
            } else {
                VALID_INTERVALS.lastOrNull { it < current } ?: 5
            }
        }
    }

    /**
     * Coerces soft lock duration in seconds within 5..60.
     */
    fun coerceSoftLockDuration(seconds: Int): Int = seconds.coerceIn(5, 60)

    /**
     * Coerces soft unlock relief duration in minutes within 1..60.
     */
    fun coerceSoftUnlockDuration(minutes: Int): Int = minutes.coerceIn(1, 60)
}

