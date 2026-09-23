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

package com.focusbyrj.app.util.backup

import android.content.Context
import android.util.Log
import com.focusbyrj.app.data.AppRestriction
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.FocusSchedule
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitType
import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskDao
import com.focusbyrj.app.data.TaskType
import com.focusbyrj.app.data.note.NoteDao
import com.focusbyrj.app.data.note.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Production-grade data safety manager providing three complementary layers of protection
 * against accidental data loss:
 *
 * ## 1. Rolling Daily Auto-Backup (7-day retention)
 * Writes a lightweight JSON snapshot of all notes, tasks, habits, schedules, and restrictions
 * to internal storage (`files/auto_backups/`).
 * These are NOT encrypted with a user password — they use the device's internal storage
 * isolation as protection. They are never user-visible. Keeps the last 7 daily snapshots,
 * rotating the oldest automatically.
 *
 * ## 2. Pre-Destructive-Operation Snapshots
 * Before any bulk delete (empty trash, clean restore, 30-day trash purge, sync mass-delete anomaly),
 * a timestamped snapshot is written to `files/auto_backups/pre_op/`. This gives a deterministic recovery
 * point that can be inspected manually or used by a recovery tool.
 *
 * ## 3. Sync Anomaly Snapshots
 * When the sync engine detects a >40% drop in local data (SYNC_ANOMALY), an emergency
 * snapshot is written immediately before the sync result is rejected. This captures the
 * "last known good" state.
 *
 * ## Recovery
 * All snapshots are plain JSON (NOT encrypted) for simplicity and resilience —
 * if the encryption key is the problem, you still need the backup. The internal storage
 * directory is app-private and inaccessible to other apps on non-rooted devices.
 *
 * For full encrypted backups (with user password), see [BackupRestoreManager].
 */
object DataSafetyManager {

    private const val TAG = "DataSafetyManager"
    private const val AUTO_BACKUP_DIR = "auto_backups"
    private const val PRE_OP_DIR = "auto_backups/pre_op"
    private const val DAILY_PREFIX = "daily_"
    private const val PRE_OP_PREFIX = "pre_op_"
    private const val MAX_DAILY_BACKUPS = 7
    private const val MAX_PRE_OP_BACKUPS = 10

    // ──────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Writes a daily rolling multi-table snapshot to internal storage.
     * Rotates the oldest backup if more than [MAX_DAILY_BACKUPS] exist.
     *
     * Call this from [AutoBackupWorker] (WorkManager, once per day).
     */
    suspend fun writeDailyBackup(
        context: Context,
        noteDao: NoteDao,
        focusDb: FocusDatabase? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = ensureDir(context, AUTO_BACKUP_DIR)
            val notes = noteDao.getAllNotesList()
            val payload = buildMultiTableSnapshot(notes, focusDb)

            val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val file = File(dir, "${DAILY_PREFIX}${dateStamp}.json")
            writeAtomically(file, payload)

            // Rotate: remove oldest if over limit
            rotateDailyBackups(dir)

            Log.i(TAG, "Daily auto-backup written: ${notes.size} notes → ${file.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write daily auto-backup", e)
            false
        }
    }

    /**
     * Overload for backward-compatibility with note-only callers.
     */
    suspend fun writeDailyBackup(context: Context, noteDao: NoteDao): Boolean =
        writeDailyBackup(context, noteDao, null)

    /**
     * Writes a pre-destructive-operation snapshot capturing notes and FocusDatabase entities.
     * Call this BEFORE any of: emptyTrash, cleanRestore, bulk sync delete, 30-day trash purge.
     *
     * @param operationTag Human-readable tag identifying the operation, e.g. "emptyTrash", "cleanRestore"
     * @return The snapshot file path on success, null on failure
     */
    suspend fun writePreOpSnapshot(
        context: Context,
        noteDao: NoteDao,
        operationTag: String,
        focusDb: FocusDatabase? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val dir = ensureDir(context, PRE_OP_DIR)
            val notes = noteDao.getAllNotesList()
            val payload = buildMultiTableSnapshot(notes, focusDb)

            val ts = System.currentTimeMillis()
            val safeTag = operationTag.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val file = File(dir, "${PRE_OP_PREFIX}${safeTag}_${ts}.json")
            writeAtomically(file, payload)

            // Rotate: keep only the most recent MAX_PRE_OP_BACKUPS
            rotatePreOpBackups(dir)

            Log.i(TAG, "Pre-op snapshot written for '$operationTag': ${notes.size} notes → ${file.name}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write pre-op snapshot for '$operationTag'", e)
            null
        }
    }

    /**
     * Overload for backward-compatibility with note-only callers.
     */
    suspend fun writePreOpSnapshot(
        context: Context,
        noteDao: NoteDao,
        operationTag: String
    ): String? = writePreOpSnapshot(context, noteDao, operationTag, null)

    /**
     * Writes an emergency snapshot when a sync anomaly is detected (>40% data drop).
     * This is called by [SupabaseSyncEngine] before aborting the sync result.
     */
    suspend fun writeEmergencySnapshot(
        context: Context,
        noteDao: NoteDao,
        focusDb: FocusDatabase? = null
    ): String? = writePreOpSnapshot(context, noteDao, "SYNC_ANOMALY_EMERGENCY", focusDb)

    suspend fun writeEmergencySnapshot(context: Context, noteDao: NoteDao): String? =
        writeEmergencySnapshot(context, noteDao, null)

    /**
     * Returns a list of all auto-backup files (daily + pre-op) sorted newest first.
     * Useful for displaying a recovery list in settings UI.
     */
    fun listAvailableSnapshots(context: Context): List<SnapshotInfo> {
        return try {
            val dailyDir = File(context.filesDir, AUTO_BACKUP_DIR)
            val preOpDir = File(context.filesDir, PRE_OP_DIR)
            val files = mutableListOf<File>()
            if (dailyDir.exists()) files.addAll(dailyDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray())
            if (preOpDir.exists()) files.addAll(preOpDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray())
            files.sortByDescending { it.lastModified() }
            files.map { file ->
                val tag = when {
                    file.name.startsWith(DAILY_PREFIX) -> "Daily"
                    file.name.contains("SYNC_ANOMALY") -> "Emergency (Sync Anomaly)"
                    else -> "Pre-Op"
                }
                SnapshotInfo(
                    tag = tag,
                    fileName = file.name,
                    absolutePath = file.absolutePath,
                    createdAtMs = file.lastModified(),
                    sizeBytes = file.length()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list snapshots", e)
            emptyList()
        }
    }

    /**
     * Reads the note count from a snapshot file for quick validation.
     * Returns -1 on failure.
     */
    fun readSnapshotNoteCount(snapshotPath: String): Int {
        return try {
            val text = readAtomically(File(snapshotPath))
            val obj = JSONObject(text)
            obj.optInt("noteCount", -1)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read snapshot note count: $snapshotPath", e)
            -1
        }
    }

    /**
     * Reads the task count from a snapshot file.
     * Returns -1 on failure or if not present.
     */
    fun readSnapshotTaskCount(snapshotPath: String): Int {
        return try {
            val text = readAtomically(File(snapshotPath))
            val obj = JSONObject(text)
            obj.optInt("taskCount", -1)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read snapshot task count: $snapshotPath", e)
            -1
        }
    }

    /**
     * Purges notes and tasks that have been in the trash for longer than [retentionDays] (default: 30 days).
     * If any expired items are found, takes a pre-op safety snapshot first before permanently deleting.
     *
     * @return Total number of permanently deleted expired items (notes + tasks).
     */
    suspend fun purgeExpiredTrash(
        context: Context,
        noteDao: NoteDao,
        taskDao: TaskDao? = null,
        retentionDays: Int = 30,
        focusDb: FocusDatabase? = null
    ): Int = withContext(Dispatchers.IO) {
        try {
            val cutoffMs = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L)
            val expiredNotes = noteDao.getExpiredTrashedNotes(cutoffMs)
            val expiredTasks = taskDao?.getExpiredTrashedTasks(cutoffMs) ?: emptyList()

            if (expiredNotes.isNotEmpty() || expiredTasks.isNotEmpty()) {
                writePreOpSnapshot(context, noteDao, "auto_purge_30d_trash", focusDb)
                
                var purgedCount = 0
                if (expiredNotes.isNotEmpty()) {
                    val expiredNoteIds = expiredNotes.map { it.id }
                    noteDao.hardDeleteNotesByIds(expiredNoteIds)
                    purgedCount += expiredNoteIds.size
                    Log.i(TAG, "30-day trash purge: permanently removed ${expiredNoteIds.size} notes (older than $retentionDays days)")
                }

                if (expiredTasks.isNotEmpty()) {
                    val expiredTaskIds = expiredTasks.map { it.id }
                    taskDao?.hardDeleteTasksByIds(expiredTaskIds)
                    purgedCount += expiredTaskIds.size
                    Log.i(TAG, "30-day trash purge: permanently removed ${expiredTaskIds.size} tasks (older than $retentionDays days)")
                }

                purgedCount
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to purge expired trash", e)
            0
        }
    }

    suspend fun purgeExpiredTrash(
        context: Context,
        noteDao: NoteDao,
        retentionDays: Int = 30
    ): Int = purgeExpiredTrash(context, noteDao, null, retentionDays, null)

    /**
     * Restores notes and FocusDatabase entities from a snapshot file into the database.
     * Takes an emergency/pre-restore snapshot of the current state before applying the restore.
     *
     * @param snapshotPath Absolute path to the snapshot JSON file
     * @param focusDb Optional FocusDatabase to restore tasks, habits, schedules, and restrictions
     * @return Result containing the total number of restored items on success, or an Exception on failure
     */
    suspend fun restoreSnapshot(
        context: Context,
        snapshotPath: String,
        noteDao: NoteDao,
        focusDb: FocusDatabase? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val file = File(snapshotPath)
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Snapshot file not found: $snapshotPath"))
            }

            // Always capture pre-restore snapshot as fail-safe
            writePreOpSnapshot(context, noteDao, "pre_restore", focusDb)

            val jsonStr = readAtomically(file)
            val root = JSONObject(jsonStr)

            var totalRestored = 0

            // 1. Restore Notes
            val notesArr = root.optJSONArray("notes")
            if (notesArr != null && notesArr.length() > 0) {
                val restoredNotes = mutableListOf<NoteEntity>()
                for (i in 0 until notesArr.length()) {
                    val obj = notesArr.getJSONObject(i)
                    val note = NoteEntity(
                        id = obj.optLong("id", 0L),
                        title = obj.optString("title", ""),
                        content = obj.optString("content", ""),
                        isChecklist = obj.optBoolean("isChecklist", false),
                        checklistJson = obj.optString("checklistJson", "[]"),
                        labelsJson = obj.optString("labelsJson", "[]"),
                        imageUrisJson = obj.optString("imageUrisJson", "[]"),
                        audioUrisJson = obj.optString("audioUrisJson", "[]"),
                        colorKey = obj.optString("colorKey", "default"),
                        isPinned = obj.optBoolean("isPinned", false),
                        isArchived = obj.optBoolean("isArchived", false),
                        isTrashed = obj.optBoolean("isTrashed", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        trashedAt = if (obj.has("trashedAt") && !obj.isNull("trashedAt")) obj.getLong("trashedAt") else null
                    )
                    restoredNotes.add(note)
                }

                if (restoredNotes.isNotEmpty()) {
                    noteDao.insertNotes(restoredNotes)
                    totalRestored += restoredNotes.size
                    Log.i(TAG, "Restored ${restoredNotes.size} notes from snapshot: ${file.name}")
                }
            }

            // 2. Restore FocusDatabase entities if provided
            if (focusDb != null) {
                // Tasks
                val tasksArr = root.optJSONArray("tasks")
                if (tasksArr != null && tasksArr.length() > 0) {
                    val restoredTasks = mutableListOf<Task>()
                    for (i in 0 until tasksArr.length()) {
                        val obj = tasksArr.getJSONObject(i)
                        restoredTasks.add(
                            Task(
                                id = obj.optLong("id", 0L),
                                title = obj.getString("title"),
                                details = obj.optString("details", ""),
                                dueDate = if (obj.has("dueDate") && !obj.isNull("dueDate")) obj.optLong("dueDate") else null,
                                isCompleted = obj.optBoolean("isCompleted", false),
                                completedAt = if (obj.has("completedAt") && !obj.isNull("completedAt")) obj.optLong("completedAt") else null,
                                type = try { TaskType.valueOf(obj.optString("type", "TASK")) } catch (_: Exception) { TaskType.TASK },
                                recurrence = try { RecurrencePattern.valueOf(obj.optString("recurrence", "NONE")) } catch (_: Exception) { RecurrencePattern.NONE },
                                isPersistent = obj.optBoolean("isPersistent", false),
                                isPriority = obj.optBoolean("isPriority", false),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                                isTrashed = obj.optBoolean("isTrashed", false),
                                trashedAt = if (obj.has("trashedAt") && !obj.isNull("trashedAt")) obj.optLong("trashedAt") else null,
                                deletedAt = if (obj.has("deletedAt") && !obj.isNull("deletedAt")) obj.optLong("deletedAt") else null
                            )
                        )
                    }
                    if (restoredTasks.isNotEmpty()) {
                        focusDb.taskDao().insertTasks(restoredTasks)
                        totalRestored += restoredTasks.size
                        Log.i(TAG, "Restored ${restoredTasks.size} tasks from snapshot")
                    }
                }

                // Habits
                val habitsArr = root.optJSONArray("habits")
                if (habitsArr != null && habitsArr.length() > 0) {
                    val restoredHabits = mutableListOf<Habit>()
                    for (i in 0 until habitsArr.length()) {
                        val obj = habitsArr.getJSONObject(i)
                        restoredHabits.add(
                            Habit(
                                id = obj.optLong("id", 0L),
                                title = obj.getString("title"),
                                description = obj.optString("description", ""),
                                iconEmoji = obj.optString("iconEmoji", "✨"),
                                colorHex = obj.optString("colorHex", "#3B82F6"),
                                type = try { HabitType.valueOf(obj.optString("type", "ONCE_DAILY")) } catch (_: Exception) { HabitType.ONCE_DAILY },
                                targetPerDay = obj.optInt("targetPerDay", 1),
                                intervalHours = obj.optInt("intervalHours", 2),
                                intervalMinutes = obj.optInt("intervalMinutes", 0),
                                windowStartHour = obj.optInt("windowStartHour", 8),
                                windowStartMinute = obj.optInt("windowStartMinute", 0),
                                windowEndHour = obj.optInt("windowEndHour", 20),
                                windowEndMinute = obj.optInt("windowEndMinute", 0),
                                fixedReminderHour = obj.optInt("fixedReminderHour", 9),
                                fixedReminderMinute = obj.optInt("fixedReminderMinute", 0),
                                isReminderEnabled = obj.optBoolean("isReminderEnabled", false),
                                reminderSound = obj.optString("reminderSound", "default"),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                isArchived = obj.optBoolean("isArchived", false)
                            )
                        )
                    }
                    if (restoredHabits.isNotEmpty()) {
                        focusDb.habitDao().insertHabits(restoredHabits)
                        totalRestored += restoredHabits.size
                        Log.i(TAG, "Restored ${restoredHabits.size} habits from snapshot")
                    }
                }

                // Schedules
                val schedulesArr = root.optJSONArray("schedules")
                if (schedulesArr != null && schedulesArr.length() > 0) {
                    val restoredSchedules = mutableListOf<FocusSchedule>()
                    for (i in 0 until schedulesArr.length()) {
                        val obj = schedulesArr.getJSONObject(i)
                        restoredSchedules.add(
                            FocusSchedule(
                                id = obj.optInt("id", 0),
                                name = obj.getString("name"),
                                startHour = obj.optInt("startHour", 9),
                                startMinute = obj.optInt("startMinute", 0),
                                endHour = obj.optInt("endHour", 17),
                                endMinute = obj.optInt("endMinute", 0),
                                daysOfWeek = obj.optString("daysOfWeek", "1,2,3,4,5"),
                                mode = obj.optString("mode", "HARD"),
                                restrictionMode = obj.optString("restrictionMode", "SIMPLE"),
                                timeLimitMinutes = obj.optInt("timeLimitMinutes", 0),
                                clickLimitCount = obj.optInt("clickLimitCount", 0),
                                appsToBlock = obj.optString("appsToBlock", ""),
                                isEnabled = obj.optBoolean("isEnabled", true)
                            )
                        )
                    }
                    if (restoredSchedules.isNotEmpty()) {
                        focusDb.scheduleDao().insertSchedules(restoredSchedules)
                        totalRestored += restoredSchedules.size
                        Log.i(TAG, "Restored ${restoredSchedules.size} schedules from snapshot")
                    }
                }

                // Restrictions
                val restrictionsArr = root.optJSONArray("restrictions")
                if (restrictionsArr != null && restrictionsArr.length() > 0) {
                    val restoredRestrictions = mutableListOf<AppRestriction>()
                    for (i in 0 until restrictionsArr.length()) {
                        val obj = restrictionsArr.getJSONObject(i)
                        restoredRestrictions.add(
                            AppRestriction(
                                packageName = obj.getString("packageName"),
                                appName = obj.optString("appName", "Unknown App"),
                                isRestricted = obj.optBoolean("isRestricted", false),
                                mode = obj.optString("mode", "HARD"),
                                restrictionMode = obj.optString("restrictionMode", "SIMPLE"),
                                timeLimitMinutes = obj.optInt("timeLimitMinutes", 0),
                                clickLimitCount = obj.optInt("clickLimitCount", 0),
                                customQuote = obj.optString("customQuote", "Is this urgent, or are you chasing cheap dopamine?")
                            )
                        )
                    }
                    if (restoredRestrictions.isNotEmpty()) {
                        focusDb.appRestrictionDao().insertRestrictions(restoredRestrictions)
                        totalRestored += restoredRestrictions.size
                        Log.i(TAG, "Restored ${restoredRestrictions.size} restrictions from snapshot")
                    }
                }
            }

            Result.success(totalRestored)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore snapshot: $snapshotPath", e)
            Result.failure(e)
        }
    }

    suspend fun restoreSnapshot(
        context: Context,
        snapshotPath: String,
        noteDao: NoteDao
    ): Result<Int> = restoreSnapshot(context, snapshotPath, noteDao, null)

    // ──────────────────────────────────────────────────────────────────────
    // Data classes
    // ──────────────────────────────────────────────────────────────────────

    data class SnapshotInfo(
        val tag: String,
        val fileName: String,
        val absolutePath: String,
        val createdAtMs: Long,
        val sizeBytes: Long
    )

    // ──────────────────────────────────────────────────────────────────────
    // Internals
    // ──────────────────────────────────────────────────────────────────────

    internal fun writeAtomically(file: File, content: String) {
        val atomicFile = androidx.core.util.AtomicFile(file)
        val fos = atomicFile.startWrite()
        try {
            fos.write(content.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(fos)
        } catch (e: Exception) {
            atomicFile.failWrite(fos)
            throw e
        }
    }

    internal fun readAtomically(file: File): String {
        val atomicFile = androidx.core.util.AtomicFile(file)
        return atomicFile.openRead().use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        }
    }

    private fun ensureDir(context: Context, relPath: String): File {
        val dir = File(context.filesDir, relPath)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private suspend fun buildMultiTableSnapshot(
        notes: List<NoteEntity>,
        focusDb: FocusDatabase?
    ): String {
        val root = JSONObject().apply {
            put("snapshotVersion", 2)
            put("createdAt", System.currentTimeMillis())
            put("noteCount", notes.size)
            
            val notesArr = JSONArray()
            notes.forEach { note ->
                notesArr.put(JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("content", note.content)
                    put("isChecklist", note.isChecklist)
                    put("checklistJson", note.checklistJson)
                    put("labelsJson", note.labelsJson)
                    put("imageUrisJson", note.imageUrisJson)
                    put("audioUrisJson", note.audioUrisJson)
                    put("colorKey", note.colorKey)
                    put("isPinned", note.isPinned)
                    put("isArchived", note.isArchived)
                    put("isTrashed", note.isTrashed)
                    put("createdAt", note.createdAt)
                    put("updatedAt", note.updatedAt)
                    if (note.trashedAt != null) put("trashedAt", note.trashedAt)
                })
            }
            put("notes", notesArr)

            if (focusDb != null) {
                // Tasks
                val tasks = focusDb.taskDao().getAllTasksList()
                put("taskCount", tasks.size)
                val tasksArr = JSONArray()
                tasks.forEach { t ->
                    tasksArr.put(JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("details", t.details)
                        if (t.dueDate != null) put("dueDate", t.dueDate)
                        put("isCompleted", t.isCompleted)
                        if (t.completedAt != null) put("completedAt", t.completedAt)
                        put("type", t.type.name)
                        put("recurrence", t.recurrence.name)
                        put("isPersistent", t.isPersistent)
                        put("isPriority", t.isPriority)
                        put("updatedAt", t.updatedAt)
                        put("isTrashed", t.isTrashed)
                        if (t.trashedAt != null) put("trashedAt", t.trashedAt)
                        if (t.deletedAt != null) put("deletedAt", t.deletedAt)
                    })
                }
                put("tasks", tasksArr)

                // Habits
                val habits = focusDb.habitDao().getAllHabitsSync()
                put("habitCount", habits.size)
                val habitsArr = JSONArray()
                habits.forEach { h ->
                    habitsArr.put(JSONObject().apply {
                        put("id", h.id)
                        put("title", h.title)
                        put("description", h.description)
                        put("iconEmoji", h.iconEmoji)
                        put("colorHex", h.colorHex)
                        put("type", h.type.name)
                        put("targetPerDay", h.targetPerDay)
                        put("intervalHours", h.intervalHours)
                        put("intervalMinutes", h.intervalMinutes)
                        put("windowStartHour", h.windowStartHour)
                        put("windowStartMinute", h.windowStartMinute)
                        put("windowEndHour", h.windowEndHour)
                        put("windowEndMinute", h.windowEndMinute)
                        put("fixedReminderHour", h.fixedReminderHour)
                        put("fixedReminderMinute", h.fixedReminderMinute)
                        put("isReminderEnabled", h.isReminderEnabled)
                        put("reminderSound", h.reminderSound)
                        put("createdAt", h.createdAt)
                        put("isArchived", h.isArchived)
                    })
                }
                put("habits", habitsArr)

                // Schedules
                val schedules = focusDb.scheduleDao().getAllSchedulesSync()
                put("scheduleCount", schedules.size)
                val schedulesArr = JSONArray()
                schedules.forEach { s ->
                    schedulesArr.put(JSONObject().apply {
                        put("id", s.id)
                        put("name", s.name)
                        put("startHour", s.startHour)
                        put("startMinute", s.startMinute)
                        put("endHour", s.endHour)
                        put("endMinute", s.endMinute)
                        put("daysOfWeek", s.daysOfWeek)
                        put("mode", s.mode)
                        put("restrictionMode", s.restrictionMode)
                        put("timeLimitMinutes", s.timeLimitMinutes)
                        put("clickLimitCount", s.clickLimitCount)
                        put("appsToBlock", s.appsToBlock)
                        put("isEnabled", s.isEnabled)
                    })
                }
                put("schedules", schedulesArr)

                // Restrictions
                val restrictions = focusDb.appRestrictionDao().getAllRestrictionsSync()
                put("restrictionCount", restrictions.size)
                val restrictionsArr = JSONArray()
                restrictions.forEach { r ->
                    restrictionsArr.put(JSONObject().apply {
                        put("packageName", r.packageName)
                        put("appName", r.appName)
                        put("isRestricted", r.isRestricted)
                        put("mode", r.mode)
                        put("restrictionMode", r.restrictionMode)
                        put("timeLimitMinutes", r.timeLimitMinutes)
                        put("clickLimitCount", r.clickLimitCount)
                        put("customQuote", r.customQuote)
                    })
                }
                put("restrictions", restrictionsArr)
            }
        }
        return root.toString()
    }

    private fun rotateDailyBackups(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith(DAILY_PREFIX) }
            ?.sortedByDescending { it.lastModified() } ?: return
        if (files.size > MAX_DAILY_BACKUPS) {
            files.drop(MAX_DAILY_BACKUPS).forEach { old ->
                old.delete()
                Log.d(TAG, "Rotated old daily backup: ${old.name}")
            }
        }
    }

    private fun rotatePreOpBackups(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith(PRE_OP_PREFIX) }
            ?.sortedByDescending { it.lastModified() } ?: return
        if (files.size > MAX_PRE_OP_BACKUPS) {
            files.drop(MAX_PRE_OP_BACKUPS).forEach { old ->
                old.delete()
                Log.d(TAG, "Rotated old pre-op snapshot: ${old.name}")
            }
        }
    }
}
