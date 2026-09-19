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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.data.drill.DrillDatabase
import com.focusbyrj.app.data.drill.DrillSessionEntity
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.screens.notes.NotesViewModel
import com.focusbyrj.app.widget.NoteWidgetProvider
import com.focusbyrj.app.util.AptitudeManager
import com.focusbyrj.app.util.BubbleChatManager
import com.focusbyrj.app.util.DailyQuestManager
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.FocusStatsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Orchestrates full backup creation and restoration across all app components:
 * - Room Databases (Notes, Restrictions, Schedules, Tasks, Habits & Logs, Drill Sessions, Vocab)
 * - SharedPreferences (Focus Settings, Economy & Avatars, Bubble Chat, Stats & Quests, Categories, Theme)
 * - Media Storage (Keep images, Keep voice memos)
 */
object BackupRestoreManager {

    private const val TAG = "BackupRestoreManager"
    private const val BACKUP_VERSION = 1

    private val PREF_FILES = listOf(
        "focus_prefs",
        "bubble_prefs",
        "focus_economy_prefs",
        "focus_stats_prefs",
        "daily_learning_quests_prefs",
        "aptitude_economy_prefs",
        "custom_category_prefs",
        "focus_app_prefs",
        "temporary_unlock_prefs",
        "usage_break_tracker_prefs",
        "summary_quotes_deck"
    )

    private val MEDIA_FOLDERS = listOf(
        "keep_images",
        "keep_drawings",
        "keep_audio"
    )

    data class BackupMetadata(
        val version: Int,
        val createdAt: Long,
        val appVersion: String,
        val noteCount: Int,
        val taskCount: Int,
        val habitCount: Int,
        val restrictionCount: Int,
        val scheduleCount: Int,
        val drillCount: Int
    )

    /**
     * Generates a suggested filename for export.
     */
    fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
        return "FocusBackup_${dateFormat.format(Date())}.focusbackup"
    }

    /**
     * Exports all databases, preferences, and media into an encrypted archive written to [destinationUri].
     */
    suspend fun createEncryptedBackup(
        context: Context,
        destinationUri: Uri,
        password: String
    ): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as FocusApplication
            val focusDb = app.database
            val noteDb = NoteDatabase.getInstance(app)
            val drillDb = DrillDatabase.getDatabase(app)
            val vocabDb = app.vocabDatabase

            // 1. Gather all database entities
            val restrictions = focusDb.appRestrictionDao().getAllRestrictionsSync()
            val schedules = focusDb.scheduleDao().getAllSchedulesSync()
            val tasks = focusDb.taskDao().getAllTasksList()
            val habits = focusDb.habitDao().getAllHabitsSync()
            val habitLogs = focusDb.habitDao().getAllLogsSync()
            val notes = noteDb.noteDao().getAllNotesList()
            val drillSessions = drillDb.drillSessionDao().getAllSessionsSync()
            val learnedIdioms = vocabDb.vocabDao().getAllLearnedIdioms()
            val learnedOws = vocabDb.vocabDao().getAllLearnedOws()

            val metadata = BackupMetadata(
                version = BACKUP_VERSION,
                createdAt = System.currentTimeMillis(),
                appVersion = "1.0.0",
                noteCount = notes.size,
                taskCount = tasks.size,
                habitCount = habits.size,
                restrictionCount = restrictions.size,
                scheduleCount = schedules.size,
                drillCount = drillSessions.size
            )

            // 2. Build JSON Payload
            val rootJson = JSONObject().apply {
                put("version", BACKUP_VERSION)
                put("createdAt", metadata.createdAt)
                put("appVersion", metadata.appVersion)

                // Manifest
                val metaObj = JSONObject().apply {
                    put("notes", notes.size)
                    put("tasks", tasks.size)
                    put("habits", habits.size)
                    put("restrictions", restrictions.size)
                    put("schedules", schedules.size)
                    put("drillSessions", drillSessions.size)
                }
                put("meta", metaObj)

                // Notes
                val notesArray = JSONArray()
                notes.forEach { note ->
                    notesArray.put(JSONObject().apply {
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
                    })
                }
                put("notes", notesArray)

                // Restrictions
                val restrArray = JSONArray()
                restrictions.forEach { res: AppRestriction ->
                    restrArray.put(JSONObject().apply {
                        put("packageName", res.packageName)
                        put("appName", res.appName)
                        put("isRestricted", res.isRestricted)
                        put("mode", res.mode)
                        put("restrictionMode", res.restrictionMode)
                        put("timeLimitMinutes", res.timeLimitMinutes)
                        put("clickLimitCount", res.clickLimitCount)
                        put("customQuote", res.customQuote)
                    })
                }
                put("restrictions", restrArray)

                // Schedules
                val schedArray = JSONArray()
                schedules.forEach { sc: FocusSchedule ->
                    schedArray.put(JSONObject().apply {
                        put("id", sc.id)
                        put("name", sc.name)
                        put("startHour", sc.startHour)
                        put("startMinute", sc.startMinute)
                        put("endHour", sc.endHour)
                        put("endMinute", sc.endMinute)
                        put("daysOfWeek", sc.daysOfWeek)
                        put("mode", sc.mode)
                        put("restrictionMode", sc.restrictionMode)
                        put("timeLimitMinutes", sc.timeLimitMinutes)
                        put("clickLimitCount", sc.clickLimitCount)
                        put("appsToBlock", sc.appsToBlock)
                    })
                }
                put("schedules", schedArray)

                // Tasks
                val taskArray = JSONArray()
                tasks.forEach { t: Task ->
                    taskArray.put(JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("details", t.details)
                        put("dueDate", t.dueDate ?: JSONObject.NULL)
                        put("isCompleted", t.isCompleted)
                        put("completedAt", t.completedAt ?: JSONObject.NULL)
                        put("type", t.type.name)
                        put("recurrence", t.recurrence.name)
                        put("isPersistent", t.isPersistent)
                        put("isPriority", t.isPriority)
                    })
                }
                put("tasks", taskArray)

                // Habits
                val habitArray = JSONArray()
                habits.forEach { h: Habit ->
                    habitArray.put(JSONObject().apply {
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
                put("habits", habitArray)

                // Habit Logs
                val logArray = JSONArray()
                habitLogs.forEach { l: HabitLog ->
                    logArray.put(JSONObject().apply {
                        put("id", l.id)
                        put("habitId", l.habitId)
                        put("date", l.date)
                        put("completedCount", l.completedCount)
                        put("targetCount", l.targetCount)
                        put("lastCompletedTimestamp", l.lastCompletedTimestamp ?: JSONObject.NULL)
                    })
                }
                put("habitLogs", logArray)

                // Drill Sessions
                val drillArray = JSONArray()
                drillSessions.forEach { d: DrillSessionEntity ->
                    drillArray.put(JSONObject().apply {
                        put("sessionId", d.sessionId)
                        put("title", d.title)
                        put("totalQuestions", d.totalQuestions)
                        put("correctCount", d.correctCount)
                        put("timeSpentSeconds", d.timeSpentSeconds)
                        put("xpEarned", d.xpEarned)
                        put("isBlitz", d.isBlitz)
                        put("isClaimed", d.isClaimed)
                        put("timestamp", d.timestamp)
                        put("summaryJson", d.summaryJson)
                    })
                }
                put("drillSessions", drillArray)

                // Vocab learned states
                val idiomsArray = JSONArray()
                learnedIdioms.forEach { idm: Idiom ->
                    idiomsArray.put(JSONObject().apply {
                        put("id", idm.id ?: 0)
                        put("learned_at", idm.learnedAt ?: 0L)
                        put("is_mastered", idm.isMastered ?: 0)
                        put("is_bookmarked", idm.isBookmarked ?: 0)
                    })
                }
                put("learnedIdioms", idiomsArray)

                val owsArray = JSONArray()
                learnedOws.forEach { ow: Ows ->
                    owsArray.put(JSONObject().apply {
                        put("id", ow.id ?: 0)
                        put("learned_at", ow.learnedAt ?: 0L)
                        put("is_mastered", ow.isMastered ?: 0)
                        put("is_bookmarked", ow.isBookmarked ?: 0)
                    })
                }
                put("learnedOws", owsArray)

                // Preferences
                val prefsObj = JSONObject()
                PREF_FILES.forEach { prefName ->
                    val sp = app.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    val map = sp.all
                    val pObj = JSONObject()
                    map.forEach { (k, v) ->
                        if (v is Set<*>) {
                            pObj.put(k, JSONArray(v))
                        } else {
                            pObj.put(k, v ?: JSONObject.NULL)
                        }
                    }
                    prefsObj.put(prefName, pObj)
                }
                put("preferences", prefsObj)
            }

            // 3. Create unencrypted in-memory ZIP
            val byteOut = ByteArrayOutputStream()
            ZipOutputStream(BufferedOutputStream(byteOut)).use { zipOut ->
                // Write data.json
                zipOut.putNextEntry(ZipEntry("data.json"))
                zipOut.write(rootJson.toString().toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // Package media files
                MEDIA_FOLDERS.forEach { folderName ->
                    val mediaDir = File(app.filesDir, folderName)
                    if (mediaDir.exists() && mediaDir.isDirectory) {
                        mediaDir.listFiles()?.forEach { file ->
                            if (file.isFile && file.length() > 0) {
                                zipOut.putNextEntry(ZipEntry("media/$folderName/${file.name}"))
                                FileInputStream(file).use { input ->
                                    input.copyTo(zipOut)
                                }
                                zipOut.closeEntry()
                            }
                        }
                    }
                }
            }

            val zipBytes = byteOut.toByteArray()

            // 4. Encrypt ZIP with AES-256-GCM + PBKDF2 directly to Uri
            app.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
                CryptoBackupEngine.encrypt(zipBytes, password.toCharArray(), outStream)
            } ?: throw IOException("Could not open destination file stream.")

            Log.i(TAG, "Encrypted backup created successfully: ${metadata.noteCount} notes, ${metadata.taskCount} tasks.")
            Result.success(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted backup", e)
            Result.failure(e)
        }
    }

    /**
     * Inspects or restores an encrypted backup file from [sourceUri] using [password].
     */
    suspend fun restoreEncryptedBackup(
        context: Context,
        sourceUri: Uri,
        password: String,
        cleanRestore: Boolean = false
    ): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as FocusApplication
            val inStream = app.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("Could not open backup source stream.")

            // 1. Decrypt ZIP stream
            val decryptedZipBytes = inStream.use { stream ->
                CryptoBackupEngine.decrypt(stream, password.toCharArray())
            }

            var jsonDataStr: String? = null
            val mediaFilesToRestore = mutableListOf<Pair<String, ByteArray>>()

            // 2. Unpack ZIP
            ZipInputStream(ByteArrayInputStream(decryptedZipBytes)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        if (entry.name == "data.json") {
                            jsonDataStr = zipIn.bufferedReader(Charsets.UTF_8).readText()
                        } else if (entry.name.startsWith("media/")) {
                            val relativePath = entry.name.removePrefix("media/")
                            val content = zipIn.readBytes()
                            mediaFilesToRestore.add(Pair(relativePath, content))
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            if (jsonDataStr == null) {
                throw IllegalArgumentException("Backup archive does not contain data.json manifest.")
            }

            val rootJson = JSONObject(jsonDataStr!!)
            val version = rootJson.optInt("version", 1)
            val createdAt = rootJson.optLong("createdAt", System.currentTimeMillis())
            val appVersion = rootJson.optString("appVersion", "1.0.0")

            val focusDb = app.database
            val noteDb = NoteDatabase.getInstance(app)
            val drillDb = DrillDatabase.getDatabase(app)
            val vocabDb = app.vocabDatabase

            // If cleanRestore is requested, wipe existing database contents first
            if (cleanRestore) {
                try { noteDb.noteDao().deleteAllNotes() } catch (_: Exception) {}
                try { focusDb.appRestrictionDao().deleteAllRestrictions() } catch (_: Exception) {}
                try { focusDb.scheduleDao().deleteAllSchedules() } catch (_: Exception) {}
                try { focusDb.taskDao().deleteAllTasks() } catch (_: Exception) {}
                try { focusDb.habitDao().deleteAllHabits() } catch (_: Exception) {}
                try { focusDb.habitDao().deleteAllLogs() } catch (_: Exception) {}
                try { drillDb.drillSessionDao().deleteAllSessions() } catch (_: Exception) {}
            }

            // 3. Restore Media Files
            mediaFilesToRestore.forEach { (relPath, bytes) ->
                val targetFile = File(app.filesDir, relPath)
                targetFile.parentFile?.mkdirs()
                FileOutputStream(targetFile).use { fos ->
                    fos.write(bytes)
                }
            }

            // 4. Restore Notes
            val notesArray = rootJson.optJSONArray("notes") ?: JSONArray()
            val noteEntities = mutableListOf<NoteEntity>()
            for (i in 0 until notesArray.length()) {
                val obj = notesArray.getJSONObject(i)
                noteEntities.add(
                    NoteEntity(
                        id = if (cleanRestore) obj.optLong("id", 0L) else 0L,
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
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            NotesViewModel.latestNotesCache.clear()

            noteEntities.forEach { note ->
                noteDb.noteDao().insertNote(note)
            }
            try {
                NoteWidgetProvider.updateAllWidgets(app)
            } catch (_: Exception) {}

            // 5. Restore Restrictions
            val restrArray = rootJson.optJSONArray("restrictions") ?: JSONArray()
            for (i in 0 until restrArray.length()) {
                val obj = restrArray.getJSONObject(i)
                val restriction = AppRestriction(
                    packageName = obj.getString("packageName"),
                    appName = obj.optString("appName", ""),
                    isRestricted = obj.optBoolean("isRestricted", true),
                    mode = obj.optString("mode", "HARD"),
                    restrictionMode = obj.optString("restrictionMode", "SIMPLE"),
                    timeLimitMinutes = obj.optInt("timeLimitMinutes", 0),
                    clickLimitCount = obj.optInt("clickLimitCount", 0),
                    customQuote = obj.optString("customQuote", "Is this urgent, or are you chasing cheap dopamine?")
                )
                focusDb.appRestrictionDao().insertRestriction(restriction)
            }

            // 6. Restore Schedules
            val schedArray = rootJson.optJSONArray("schedules") ?: JSONArray()
            for (i in 0 until schedArray.length()) {
                val obj = schedArray.getJSONObject(i)
                val schedule = FocusSchedule(
                    id = if (cleanRestore) obj.optInt("id", 0) else 0,
                    name = obj.optString("name", "Focus Schedule"),
                    startHour = obj.optInt("startHour", 9),
                    startMinute = obj.optInt("startMinute", 0),
                    endHour = obj.optInt("endHour", 17),
                    endMinute = obj.optInt("endMinute", 0),
                    daysOfWeek = obj.optString("daysOfWeek", "1,2,3,4,5"),
                    mode = obj.optString("mode", "HARD"),
                    restrictionMode = obj.optString("restrictionMode", "SIMPLE"),
                    timeLimitMinutes = obj.optInt("timeLimitMinutes", 0),
                    clickLimitCount = obj.optInt("clickLimitCount", 0),
                    appsToBlock = obj.optString("appsToBlock", "")
                )
                focusDb.scheduleDao().insertSchedule(schedule)
            }

            // 7. Restore Tasks
            val taskArray = rootJson.optJSONArray("tasks") ?: JSONArray()
            for (i in 0 until taskArray.length()) {
                val obj = taskArray.getJSONObject(i)
                val task = Task(
                    id = if (cleanRestore) obj.optLong("id", 0L) else 0L,
                    title = obj.getString("title"),
                    details = obj.optString("details", ""),
                    dueDate = if (obj.isNull("dueDate")) null else obj.optLong("dueDate"),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    completedAt = if (obj.isNull("completedAt")) null else obj.optLong("completedAt"),
                    type = try { TaskType.valueOf(obj.optString("type", "TASK")) } catch (_: Exception) { TaskType.TASK },
                    recurrence = try { RecurrencePattern.valueOf(obj.optString("recurrence", "NONE")) } catch (_: Exception) { RecurrencePattern.NONE },
                    isPersistent = obj.optBoolean("isPersistent", false),
                    isPriority = obj.optBoolean("isPriority", false)
                )
                focusDb.taskDao().insertTask(task)
            }

            // 8. Restore Habits
            val habitArray = rootJson.optJSONArray("habits") ?: JSONArray()
            val idMapping = mutableMapOf<Long, Long>()
            for (i in 0 until habitArray.length()) {
                val obj = habitArray.getJSONObject(i)
                val origId = obj.optLong("id", 0L)
                val habit = Habit(
                    id = if (cleanRestore) origId else 0L,
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
                    isReminderEnabled = obj.optBoolean("isReminderEnabled", true),
                    reminderSound = obj.optString("reminderSound", "ZEN"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    isArchived = obj.optBoolean("isArchived", false)
                )
                val newId = focusDb.habitDao().insertHabit(habit)
                idMapping[origId] = newId
            }

            val logArray = rootJson.optJSONArray("habitLogs") ?: JSONArray()
            for (i in 0 until logArray.length()) {
                val obj = logArray.getJSONObject(i)
                val origHabitId = obj.optLong("habitId", 0L)
                val targetHabitId = idMapping[origHabitId] ?: origHabitId
                val log = HabitLog(
                    id = if (cleanRestore) obj.optLong("id", 0L) else 0L,
                    habitId = targetHabitId,
                    date = obj.getString("date"),
                    completedCount = obj.optInt("completedCount", 1),
                    targetCount = obj.optInt("targetCount", 1),
                    lastCompletedTimestamp = if (obj.isNull("lastCompletedTimestamp")) null else obj.optLong("lastCompletedTimestamp")
                )
                focusDb.habitDao().insertOrUpdateLog(log)
            }

            // 9. Restore Drill Sessions
            val drillArray = rootJson.optJSONArray("drillSessions") ?: JSONArray()
            for (i in 0 until drillArray.length()) {
                val obj = drillArray.getJSONObject(i)
                val entity = DrillSessionEntity(
                    sessionId = obj.getString("sessionId"),
                    title = obj.optString("title", "Drill Session"),
                    totalQuestions = obj.optInt("totalQuestions", 0),
                    correctCount = obj.optInt("correctCount", 0),
                    timeSpentSeconds = obj.optLong("timeSpentSeconds", 0L),
                    xpEarned = obj.optInt("xpEarned", 0),
                    isBlitz = obj.optBoolean("isBlitz", false),
                    isClaimed = obj.optBoolean("isClaimed", true),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    summaryJson = obj.optString("summaryJson", "{}")
                )
                drillDb.drillSessionDao().insertSession(entity)
            }

            // 10. Restore Vocab
            val idiomsArray = rootJson.optJSONArray("learnedIdioms") ?: JSONArray()
            for (i in 0 until idiomsArray.length()) {
                val obj = idiomsArray.getJSONObject(i)
                val id = obj.getInt("id")
                val learnedAt = obj.optLong("learned_at", System.currentTimeMillis())
                val isMastered = obj.optInt("is_mastered", 0)
                val isBookmarked = obj.optInt("is_bookmarked", 0)
                vocabDb.vocabDao().setIdiomLearned(id, learnedAt)
                vocabDb.vocabDao().setIdiomMastery(id, isMastered, learnedAt)
                vocabDb.vocabDao().setIdiomBookmarked(id, isBookmarked)
            }

            val owsArray = rootJson.optJSONArray("learnedOws") ?: JSONArray()
            for (i in 0 until owsArray.length()) {
                val obj = owsArray.getJSONObject(i)
                val id = obj.getInt("id")
                val learnedAt = obj.optLong("learned_at", System.currentTimeMillis())
                val isMastered = obj.optInt("is_mastered", 0)
                val isBookmarked = obj.optInt("is_bookmarked", 0)
                vocabDb.vocabDao().setOwsLearned(id, learnedAt)
                vocabDb.vocabDao().setOwsMastery(id, isMastered, learnedAt)
                vocabDb.vocabDao().setOwsBookmarked(id, isBookmarked)
            }

            // 11. Restore SharedPreferences
            val prefsObj = rootJson.optJSONObject("preferences")
            if (prefsObj != null) {
                val keys = prefsObj.keys()
                while (keys.hasNext()) {
                    val prefName = keys.next()
                    val pObj = prefsObj.getJSONObject(prefName)
                    val sp = app.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    val editor = sp.edit()
                    val innerKeys = pObj.keys()
                    while (innerKeys.hasNext()) {
                        val k = innerKeys.next()
                        val v = pObj.get(k)
                        when (v) {
                            is Boolean -> editor.putBoolean(k, v)
                            is Int -> editor.putInt(k, v)
                            is Long -> editor.putLong(k, v)
                            is Float -> editor.putFloat(k, v)
                            is Double -> editor.putFloat(k, v.toFloat())
                            is String -> editor.putString(k, v)
                            is JSONArray -> {
                                val set = mutableSetOf<String>()
                                for (s in 0 until v.length()) {
                                    set.add(v.getString(s))
                                }
                                editor.putStringSet(k, set)
                            }
                        }
                    }
                    editor.apply()
                }
            }

            // Reload In-Memory Managers
            try { FocusEconomyManager.init(app) } catch (_: Exception) {}
            try { FocusStatsManager.init(app) } catch (_: Exception) {}
            try { BubbleChatManager.init(app) } catch (_: Exception) {}
            try { AptitudeManager.init(app) } catch (_: Exception) {}
            try { DailyQuestManager.init(app) } catch (_: Exception) {}

            val metadata = BackupMetadata(
                version = version,
                createdAt = createdAt,
                appVersion = appVersion,
                noteCount = noteEntities.size,
                taskCount = taskArray.length(),
                habitCount = habitArray.length(),
                restrictionCount = restrArray.length(),
                scheduleCount = schedArray.length(),
                drillCount = drillArray.length()
            )

            Log.i(TAG, "Encrypted backup restored successfully.")
            Result.success(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore encrypted backup", e)
            Result.failure(e)
        }
    }
}
