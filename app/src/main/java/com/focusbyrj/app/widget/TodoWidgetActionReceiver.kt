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

package com.focusbyrj.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusbyrj.app.MainActivity
import com.focusbyrj.app.R
import com.focusbyrj.app.util.TaskReminderHelper

/**
 * Unexported BroadcastReceiver dedicated to handling internal interactive clicks
 * from the To-Do home screen widget. Segregating these actions from the exported
 * TodoWidgetProvider blocks arbitrary third-party apps from injecting unauthorized
 * task toggles or tab modifications.
 */
class TodoWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        when (intent.action) {
            TodoWidgetProvider.ACTION_SET_TAB -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val tabIndex = intent.getIntExtra(TodoWidgetProvider.EXTRA_TAB_INDEX, 0)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    TodoWidgetProvider.setSelectedTab(context, appWidgetId, tabIndex)
                    TodoWidgetProvider.updateWidget(context, appWidgetManager, appWidgetId)
                    kotlin.runCatching {
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
                    }
                }
            }

            TodoWidgetProvider.ACTION_REFRESH -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    TodoWidgetProvider.updateWidget(context, appWidgetManager, appWidgetId)
                    kotlin.runCatching {
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
                    }
                } else {
                    TodoWidgetProvider.updateAllWidgets(context)
                }
            }

            TodoWidgetProvider.ACTION_TOGGLE_TASK -> {
                val taskId = intent.getLongExtra(TodoWidgetProvider.EXTRA_TASK_ID, -1L)
                val actionType = intent.getStringExtra("action_type") ?: "toggle"

                if (actionType == "open_app") {
                    val mainIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("navigate_to", "todos")
                    }
                    context.startActivity(mainIntent)
                    return
                }

                if (taskId != -1L) {
                    val pendingResult = goAsync()
                    TaskReminderHelper.toggleTaskById(context, taskId) {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
