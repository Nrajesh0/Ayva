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
import android.util.Log
import com.focusbyrj.app.data.Task
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Manages local-only history of completed tasks for Ayva and daily summaries.
 * When a task is completed, it is immediately removed from the active database
 * and Supabase cloud vault, while being archived here locally.
 *
 * When the clock strikes 12 AM (midnight), records from previous days are automatically purged.
 */
object CompletedTaskHistoryManager {

    private const val TAG = "CompletedTaskHistory"
    private const val PREFS_NAME = "focus_completed_tasks_history"
    private const val KEY_HISTORY = "completed_records_json"

    data class CompletedTaskRecord(
        val id: Long,
        val title: String,
        val details: String = "",
        val dueDate: Long? = null,
        val completedAt: Long = System.currentTimeMillis(),
        val type: String = "TASK",
        val isPriority: Boolean = false
    )

    /**
     * Calculates the timestamp of 12:00:00.000 AM of today in the current time zone.
     */
    fun getStartOfTodayMs(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Records a completed task into the local archive and purges any expired pre-midnight tasks.
     */
    @Synchronized
    fun recordCompletedTask(
        context: Context,
        task: Task,
        completedAt: Long = System.currentTimeMillis()
    ) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentList = loadAllRecords(prefs).toMutableList()

            // Remove if duplicate id exists to avoid multiple records
            currentList.removeAll { it.id == task.id && it.title == task.title }

            val record = CompletedTaskRecord(
                id = task.id,
                title = task.title,
                details = task.details,
                dueDate = task.dueDate,
                completedAt = completedAt,
                type = task.type.name,
                isPriority = task.isPriority
            )
            currentList.add(record)

            // Auto-purge any tasks completed before 12 AM of today
            val startOfDay = getStartOfTodayMs()
            val validToday = currentList.filter { it.completedAt >= startOfDay }

            saveRecords(prefs, validToday)
            Log.d(TAG, "Recorded completed task '${task.title}', total today: ${validToday.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record completed task", e)
        }
    }

    /**
     * Returns all tasks completed today (since 12:00 AM).
     * Automatically purges expired tasks from previous days.
     */
    @Synchronized
    fun getTodayCompletedTasks(context: Context): List<CompletedTaskRecord> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val startOfDay = getStartOfTodayMs()
            val all = loadAllRecords(prefs)
            val todayOnly = all.filter { it.completedAt >= startOfDay }

            // If some expired records were filtered out, update storage
            if (todayOnly.size != all.size) {
                saveRecords(prefs, todayOnly)
            }
            todayOnly
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load today completed tasks", e)
            emptyList()
        }
    }

    /**
     * Returns the count of tasks completed today.
     */
    fun getTodayCompletedCount(context: Context): Int {
        return getTodayCompletedTasks(context).size
    }

    /**
     * Purges all completed task history records older than 12:00 AM of today.
     */
    @Synchronized
    fun purgeExpiredCompletedTasks(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val startOfDay = getStartOfTodayMs()
            val all = loadAllRecords(prefs)
            val todayOnly = all.filter { it.completedAt >= startOfDay }
            saveRecords(prefs, todayOnly)
            Log.d(TAG, "Purged expired completed tasks. Kept ${todayOnly.size} for today.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to purge expired completed tasks", e)
        }
    }

    private fun loadAllRecords(prefs: android.content.SharedPreferences): List<CompletedTaskRecord> {
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<CompletedTaskRecord>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CompletedTaskRecord(
                        id = obj.optLong("id"),
                        title = obj.optString("title"),
                        details = obj.optString("details", ""),
                        dueDate = if (obj.isNull("dueDate")) null else obj.optLong("dueDate"),
                        completedAt = obj.optLong("completedAt", System.currentTimeMillis()),
                        type = obj.optString("type", "TASK"),
                        isPriority = obj.optBoolean("isPriority", false)
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing completed tasks JSON", e)
        }
        return list
    }

    private fun saveRecords(
        prefs: android.content.SharedPreferences,
        records: List<CompletedTaskRecord>
    ) {
        val array = JSONArray()
        records.forEach { r ->
            val obj = JSONObject().apply {
                put("id", r.id)
                put("title", r.title)
                put("details", r.details)
                put("dueDate", r.dueDate ?: JSONObject.NULL)
                put("completedAt", r.completedAt)
                put("type", r.type)
                put("isPriority", r.isPriority)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }
}
