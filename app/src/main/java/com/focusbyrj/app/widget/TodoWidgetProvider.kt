package com.focusbyrj.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.MainActivity
import com.focusbyrj.app.R
import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.TaskReminderHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class TodoWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SET_TAB = "com.focusbyrj.app.widget.ACTION_SET_TAB"
        const val ACTION_TOGGLE_TASK = "com.focusbyrj.app.widget.ACTION_TOGGLE_TASK"
        const val ACTION_ADD_TASK = "com.focusbyrj.app.widget.ACTION_ADD_TASK"
        const val ACTION_REFRESH = "com.focusbyrj.app.widget.ACTION_REFRESH"
        const val ACTION_OPEN_APP = "com.focusbyrj.app.widget.ACTION_OPEN_APP"

        const val EXTRA_TAB_INDEX = "extra_tab_index"
        const val EXTRA_TASK_ID = "extra_task_id"

        private const val PREFS_NAME = "todo_widget_prefs"
        private const val KEY_TAB_PREFIX = "tab_index_"

        fun getSelectedTab(context: Context, appWidgetId: Int): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_TAB_PREFIX + appWidgetId, 0)
        }

        fun setSelectedTab(context: Context, appWidgetId: Int, tabIndex: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_TAB_PREFIX + appWidgetId, tabIndex).apply()
        }

        fun updateAllWidgets(context: Context) {
            kotlin.runCatching {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, TodoWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    for (widgetId in appWidgetIds) {
                        updateWidget(context, appWidgetManager, widgetId)
                    }
                    kotlin.runCatching {
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view)
                    }
                }
            }
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            kotlin.runCatching {
                val selectedTab = getSelectedTab(context, appWidgetId)
                val config = WidgetConfigHelper.getConfig(context, appWidgetId)
                val views = RemoteViews(context.packageName, R.layout.widget_todo_layout)

                // Background Bitmap
                val bgBitmap = WidgetDrawableGenerator.createWidgetBackground(context, config)
                views.setImageViewBitmap(R.id.widget_bg_image, bgBitmap)

                // Colors
                views.setTextColor(R.id.widget_title, config.primaryTextColorInt)
                views.setInt(R.id.widget_btn_refresh, "setColorFilter", config.secondaryTextColorInt)
                views.setInt(R.id.widget_btn_settings, "setColorFilter", config.secondaryTextColorInt)
                views.setInt(R.id.widget_app_icon, "setColorFilter", config.accentColorInt)
                views.setTextColor(R.id.widget_empty_text, config.secondaryTextColorInt)
                views.setTextColor(R.id.widget_empty_subtext, config.accentColorInt)

                // Tab backgrounds and click actions
                val activeTabBitmap = WidgetDrawableGenerator.createActiveTabPill(context, config)
                val inactiveTabBitmap = WidgetDrawableGenerator.createInactiveTabPill(context, config)
                val tabTextColorActive = if (config.accent == WidgetAccent.MONOCHROME && !config.theme.isDark) Color.WHITE else Color.parseColor("#121516")
                val tabTextColorInactive = config.secondaryTextColorInt

                val tabContainers = intArrayOf(R.id.widget_tab_today_container, R.id.widget_tab_upcoming_container, R.id.widget_tab_all_container)
                val tabBgs = intArrayOf(R.id.widget_tab_today_bg, R.id.widget_tab_upcoming_bg, R.id.widget_tab_all_bg)
                val tabTexts = intArrayOf(R.id.widget_tab_today, R.id.widget_tab_upcoming, R.id.widget_tab_all)

                for (i in 0..2) {
                    val tabIntent = Intent(context, TodoWidgetProvider::class.java).apply {
                        action = ACTION_SET_TAB
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        putExtra(EXTRA_TAB_INDEX, i)
                        data = Uri.parse("widget://$appWidgetId/tab/$i")
                    }
                    val tabPendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 10 + i,
                        tabIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    views.setOnClickPendingIntent(tabContainers[i], tabPendingIntent)
                    if (selectedTab == i) {
                        views.setImageViewBitmap(tabBgs[i], activeTabBitmap)
                        views.setTextColor(tabTexts[i], tabTextColorActive)
                    } else {
                        views.setImageViewBitmap(tabBgs[i], inactiveTabBitmap)
                        views.setTextColor(tabTexts[i], tabTextColorInactive)
                    }
                }

                // Count Badge
                val countBg = WidgetDrawableGenerator.createCountBadge(context, config)
                views.setImageViewBitmap(R.id.widget_task_count_bg, countBg)
                views.setTextColor(R.id.widget_task_count, config.accentColorInt)

                // Add button '+'
                val addBtnBitmap = WidgetDrawableGenerator.createAddButton(context, config)
                views.setImageViewBitmap(R.id.widget_btn_add_bg, addBtnBitmap)
                views.setViewVisibility(R.id.widget_btn_add, View.GONE)

                // Quick Add '+' Button Intent (Opens floating dialog)
                val addIntent = Intent(context, com.focusbyrj.app.ui.screens.QuickAddTaskActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val addPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId * 100 + 1,
                    addIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_add_container, addPendingIntent)

                // Header Title Click (Opens App Todos)
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "todos")
                }
                val openAppPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId * 100 + 2,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_header_title_layout, openAppPendingIntent)

                // Refresh Button Intent
                val refreshIntent = Intent(context, TodoWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 100 + 3,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

                // Settings Button Intent (Opens Widget Customization)
                val settingsIntent = Intent(context, TodoWidgetConfigureActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val settingsPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId * 100 + 4,
                    settingsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_settings, settingsPendingIntent)

                // ListView Adapter Setup
                val serviceIntent = Intent(context, TodoWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(EXTRA_TAB_INDEX, selectedTab)
                    data = Uri.parse("widget://todo/$appWidgetId/$selectedTab")
                }
                views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
                views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_view)

                // Template PendingIntent for List Item interactions
                val listClickIntent = Intent(context, TodoWidgetProvider::class.java).apply {
                    action = ACTION_TOGGLE_TASK
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val listClickPendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 1000,
                    listClickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setPendingIntentTemplate(R.id.widget_list_view, listClickPendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)

                // Calculate task count asynchronously
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = context.applicationContext as FocusApplication
                        val allTasks = app.database.taskDao().getAllTasks().first()

                        val now = Calendar.getInstance()
                        val todayStart = now.apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val todayEnd = todayStart + 86400000L - 1L

                        val uncompleted = allTasks.filter { !it.isCompleted }

                        val count = when (selectedTab) {
                            0 -> uncompleted.count {
                                (it.dueDate != null && it.dueDate <= todayEnd) ||
                                (it.dueDate == null && it.type == com.focusbyrj.app.data.TaskType.TASK)
                            }
                            1 -> uncompleted.count {
                                it.type == com.focusbyrj.app.data.TaskType.TASK && it.dueDate != null && it.dueDate > todayEnd
                            }
                            2 -> uncompleted.size
                            else -> uncompleted.size
                        }

                        val countViews = RemoteViews(context.packageName, R.layout.widget_todo_layout)
                        countViews.setTextViewText(R.id.widget_task_count, count.toString())
                        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, countViews)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)

        when (intent.action) {
            ACTION_SET_TAB -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val tabIndex = intent.getIntExtra(EXTRA_TAB_INDEX, 0)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    setSelectedTab(context, appWidgetId, tabIndex)
                    updateWidget(context, appWidgetManager, appWidgetId)
                    kotlin.runCatching {
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
                    }
                }
            }

            ACTION_REFRESH -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    updateWidget(context, appWidgetManager, appWidgetId)
                    kotlin.runCatching {
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
                    }
                } else {
                    updateAllWidgets(context)
                }
            }

            ACTION_TOGGLE_TASK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
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
