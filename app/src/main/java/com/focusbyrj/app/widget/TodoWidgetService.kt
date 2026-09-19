package com.focusbyrj.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.R
import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TodoWidgetRemoteViewsFactory(applicationContext, intent)
    }
}

class TodoWidgetRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private var tasksList: List<Task> = emptyList()
    private var widgetConfig: WidgetConfig = WidgetConfig()

    override fun onCreate() {
        loadTasks()
    }

    override fun onDataSetChanged() {
        loadTasks()
    }

    private fun loadTasks() {
        try {
            widgetConfig = WidgetConfigHelper.getConfig(context, appWidgetId)
            val tabIndex = TodoWidgetProvider.getSelectedTab(context, appWidgetId)
            val app = context.applicationContext as FocusApplication
            val allTasks = runBlocking {
                app.database.taskDao().getAllTasks().first()
            }

            val now = Calendar.getInstance()
            val todayStart = now.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val todayEnd = todayStart + 86400000L - 1L

            val uncompleted = allTasks.filter { !it.isCompleted }

            tasksList = when (tabIndex) {
                0 -> uncompleted.filter {
                    (it.dueDate != null && it.dueDate <= todayEnd) ||
                    (it.dueDate == null && it.type == TaskType.TASK)
                }
                1 -> uncompleted.filter {
                    it.type == TaskType.TASK && it.dueDate != null && it.dueDate > todayEnd
                }
                2 -> uncompleted
                else -> uncompleted
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tasksList = emptyList()
        }
    }

    override fun onDestroy() {
        tasksList = emptyList()
    }

    override fun getCount(): Int = tasksList.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position < 0 || position >= tasksList.size) return null
        val task = tasksList[position]

        val views = RemoteViews(context.packageName, R.layout.widget_todo_item)
        
        // Item Background bitmap
        val itemBgBitmap = WidgetDrawableGenerator.createItemBackground(context, widgetConfig)
        views.setImageViewBitmap(R.id.widget_item_bg_image, itemBgBitmap)

        views.setTextViewText(R.id.widget_item_title, task.title)
        views.setTextColor(R.id.widget_item_title, widgetConfig.primaryTextColorInt)

        // Format due date / badge
        if (task.dueDate != null) {
            views.setViewVisibility(R.id.widget_item_due, View.VISIBLE)
            val dueCalendar = Calendar.getInstance().apply { timeInMillis = task.dueDate }
            val now = Calendar.getInstance()

            val isToday = dueCalendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    dueCalendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

            val tomorrowCalendar = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val isTomorrow = dueCalendar.get(Calendar.YEAR) == tomorrowCalendar.get(Calendar.YEAR) &&
                    dueCalendar.get(Calendar.DAY_OF_YEAR) == tomorrowCalendar.get(Calendar.DAY_OF_YEAR)

            val isDifferentYear = dueCalendar.get(Calendar.YEAR) != now.get(Calendar.YEAR)
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(task.dueDate))

            val dueText = when {
                isToday -> "Today, $timeFormat"
                isTomorrow -> "Tomorrow, $timeFormat"
                isDifferentYear -> SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault()).format(Date(task.dueDate))
                else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(task.dueDate))
            }
            views.setTextViewText(R.id.widget_item_due, dueText)

            val nowMs = System.currentTimeMillis()
            if (task.dueDate < nowMs) {
                views.setTextColor(R.id.widget_item_due, Color.parseColor("#FF7043")) // Overdue Orange
            } else if (isToday) {
                views.setTextColor(R.id.widget_item_due, Color.parseColor("#B388FF")) // Today Purple
            } else {
                views.setTextColor(R.id.widget_item_due, widgetConfig.secondaryTextColorInt) // Future Neutral
            }
        } else {
            if (task.type != TaskType.TASK) {
                views.setViewVisibility(R.id.widget_item_due, View.VISIBLE)
                views.setTextViewText(R.id.widget_item_due, task.type.name.lowercase().replaceFirstChar { it.uppercase() })
                views.setTextColor(R.id.widget_item_due, widgetConfig.accentColorInt)
            } else {
                views.setViewVisibility(R.id.widget_item_due, View.GONE)
            }
        }

        // Recurrence badge
        if (task.recurrence != RecurrencePattern.NONE) {
            views.setViewVisibility(R.id.widget_item_badge, View.VISIBLE)
            views.setTextViewText(R.id.widget_item_badge, "↻ ${task.recurrence.name.lowercase()}")
            views.setTextColor(R.id.widget_item_badge, widgetConfig.secondaryTextColorInt)
        } else if (task.isPersistent) {
            views.setViewVisibility(R.id.widget_item_badge, View.VISIBLE)
            views.setTextViewText(R.id.widget_item_badge, "● persistent")
            views.setTextColor(R.id.widget_item_badge, widgetConfig.secondaryTextColorInt)
        } else {
            views.setViewVisibility(R.id.widget_item_badge, View.GONE)
        }

        // Checkbox icon
        val checkboxBitmap = WidgetDrawableGenerator.createCheckbox(context, widgetConfig, task.isCompleted)
        views.setImageViewBitmap(R.id.widget_item_checkbox, checkboxBitmap)

        // Fill-in Intent for Checkbox (Toggles task)
        val checkFillIntent = Intent().apply {
            putExtra(TodoWidgetProvider.EXTRA_TASK_ID, task.id)
            putExtra("action_type", "toggle")
        }
        views.setOnClickFillInIntent(R.id.widget_item_checkbox, checkFillIntent)

        // Fill-in Intent for Row Body (Opens App)
        val bodyFillIntent = Intent().apply {
            putExtra(TodoWidgetProvider.EXTRA_TASK_ID, task.id)
            putExtra("action_type", "open_app")
        }
        views.setOnClickFillInIntent(R.id.widget_item_text_container, bodyFillIntent)
        views.setOnClickFillInIntent(R.id.widget_item_root, bodyFillIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return if (position in tasksList.indices) tasksList[position].id else position.toLong()
    }

    override fun hasStableIds(): Boolean = true
}
