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
