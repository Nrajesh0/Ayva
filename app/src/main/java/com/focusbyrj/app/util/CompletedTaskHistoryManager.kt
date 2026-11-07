/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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
 * while being archived here locally.
 *
 * When the clock strikes 12 AM (midnight), records from previous days are automatically purged.
 */
object CompletedTaskHistoryManager {

    private const val TAG = "CompletedTaskHistory"
    internal const val PREFS_NAME = "focus_completed_tasks_history"
    internal const val KEY_HISTORY = "completed_records_json"

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
            currentList.removeAll { (task.id != 0L && it.id == task.id) || (task.id == 0L && it.title == task.title) }

            val effectiveCompletedAt = task.completedAt ?: completedAt
            val record = CompletedTaskRecord(
                id = task.id,
                title = task.title,
                details = task.details,
                dueDate = task.dueDate,
                completedAt = effectiveCompletedAt,
                type = task.type.name,
                isPriority = task.isPriority
            )
            currentList.add(record)

            // Auto-purge any tasks completed before 12 AM of today
            val startOfDay = getStartOfTodayMs()
            val validToday = currentList.filter { it.completedAt >= startOfDay }

            saveRecords(prefs, validToday)
            Log.d(TAG, "Recorded completed task history")
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

    @Synchronized
    fun removeCompletedTasks(context: Context, ids: Set<Long>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        saveRecords(prefs, loadAllRecords(prefs).filterNot { it.id in ids })
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
        val encoded = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val jsonStr = decode(encoded)
        val list = mutableListOf<CompletedTaskRecord>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
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
            throw IllegalStateException("Completed task history could not be read; existing data retained", e)
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
        check(prefs.edit().putString(KEY_HISTORY, encode(array.toString())).commit()) { "Completed task history was not saved" }
    }
    internal fun encode(json: String): String {
        JSONArray(json) // Validate before touching the installed value.
        return "AYH1:" + android.util.Base64.encodeToString(
            com.focusbyrj.app.util.backup.SnapshotCryptoEngine.encryptSnapshot(json.toByteArray()), android.util.Base64.NO_WRAP)
    }
    internal fun decode(encoded: String): String {
        require(encoded.startsWith("AYH1:")) { "Unsupported completed task history format; file retained" }
        return com.focusbyrj.app.util.backup.SnapshotCryptoEngine.decryptSnapshot(
            android.util.Base64.decode(encoded.substring(5), android.util.Base64.NO_WRAP)).decodeToString()
    }
    /** The outer portable archive provides encryption; device-bound ciphertext must not travel. */
    internal fun portable(context: Context): org.json.JSONObject {
        val encoded = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_HISTORY, null)
        return org.json.JSONObject().apply { if (encoded != null) put(KEY_HISTORY, JSONArray(decode(encoded))) }
    }
    internal fun restorePortable(context: Context, value: org.json.JSONObject, clean: Boolean, ids: Map<Long, Long>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val incoming = value.optJSONArray(KEY_HISTORY) ?: JSONArray()
        val merged = if (clean) JSONArray() else JSONArray(prefs.getString(KEY_HISTORY, null)?.let(::decode) ?: "[]")
        for (i in 0 until incoming.length()) {
            val record = incoming.getJSONObject(i)
            val oldId = record.getLong("id")
            // Completed tasks have no active task mapping. Use a fresh negative identity
            // when merging rather than accidentally replacing an unrelated local record.
            record.put("id", ids[oldId] ?: if (clean) oldId else -java.util.UUID.randomUUID().mostSignificantBits.and(Long.MAX_VALUE).coerceAtLeast(1))
            merged.put(record)
        }
        check(prefs.edit().putString(KEY_HISTORY, encode(merged.toString())).commit())
    }

}
