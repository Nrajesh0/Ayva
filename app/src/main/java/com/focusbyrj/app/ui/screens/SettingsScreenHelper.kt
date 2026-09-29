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
}
