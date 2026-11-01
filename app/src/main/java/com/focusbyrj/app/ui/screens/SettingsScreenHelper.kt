/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent

object SettingsScreenHelper {
    /** Open an explicitly selected task-widget instance, or its default style (ID 0). */
    fun createWidgetConfigureIntent(context: Context, widgetId: Int = 0): Intent =
        Intent(context, com.focusbyrj.app.widget.TodoWidgetConfigureActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun getNextReminderInterval(current: Int, isIncrement: Boolean): Int =
        com.focusbyrj.app.util.SettingsPreferenceRules.nextReminderInterval(current, isIncrement)

    fun coerceSoftLockDuration(seconds: Int): Int = seconds.coerceIn(com.focusbyrj.app.util.SettingsPreferenceRules.softWait)
    fun coerceSoftUnlockDuration(minutes: Int): Int = minutes.coerceIn(com.focusbyrj.app.util.SettingsPreferenceRules.softRelief)
}
