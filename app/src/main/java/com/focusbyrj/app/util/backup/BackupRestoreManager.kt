/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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
import com.focusbyrj.app.data.chat.AyvaChatDatabase
import com.focusbyrj.app.data.chat.AyvaChatMessageEntity
import com.focusbyrj.app.data.drill.DrillDatabase
import com.focusbyrj.app.data.drill.DrillSessionEntity
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import com.focusbyrj.app.ui.screens.notes.NotesViewModel
import com.focusbyrj.app.ui.screens.notes.NotesnookBlock
import com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager
import com.focusbyrj.app.widget.NoteWidgetProvider
import com.focusbyrj.app.widget.TodoWidgetProvider
import com.focusbyrj.app.util.AptitudeManager
import com.focusbyrj.app.util.BubbleChatManager
import com.focusbyrj.app.util.DailyQuestManager
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.FocusStatsManager
import com.focusbyrj.app.util.HabitAlarmScheduler
import com.focusbyrj.app.util.TaskReminderHelper
import com.focusbyrj.app.util.assistant.AssistantPlanningArchive
import com.focusbyrj.app.util.assistant.AssistantStateLock
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.security.MessageDigest
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

typealias RequiresVaultSetupException = BackupRestoreManager.RequiresVaultSetupException

/**
 * Orchestrates full backup creation and restoration across all app components:
 * - Room Databases (Notes, Restrictions, Schedules, Tasks, Habits & Logs, Drill Sessions, Vocab, Ayva Chat Messages)
 * - SharedPreferences (Focus Settings, Economy & Avatars, Bubble Chat, Stats & Quests, Categories, Theme, Identity, Streaks, License, Widgets)
 * - Media Storage (Keep images, Keep voice memos, Keep drawings)
 */
object BackupRestoreManager {

    /**
     * Exception thrown when a backup archive contains private vault notes, but the current
     * device has no vault configured, prompting the user to establish a 6-digit Vault PIN.
     */
    class RequiresVaultSetupException(message: String) : IllegalStateException(message)

    private const val TAG = "BackupRestoreManager"
    private const val BACKUP_VERSION = 2

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
        "summary_quotes_deck",
        "enclave_identity_prefs",
        "streak_preferences",
        "focus_license_prefs",
        "note_widget_prefs",
        "todo_widget_prefs",
        "todo_widget_theme_prefs"
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
        val drillCount: Int,
        val chatMessageCount: Int = 0,
        val planCount: Int = 0
    )

    private data class MediaManifestEntry(
        val relativePath: String,
        val plaintextSize: Long,
        val sha256: String
    )

    private data class RestoreRollbackState(
        val notes: List<NoteEntity>,
        val restrictions: List<AppRestriction>,
        val schedules: List<FocusSchedule>,
        val tasks: List<Task>,
        val habits: List<Habit>,
        val habitLogs: List<HabitLog>,
        val drillSessions: List<DrillSessionEntity>,
        val idioms: List<Idiom>,
        val ows: List<Ows>,
        val chatMessages: List<AyvaChatMessageEntity>,
        val preferences: Map<String, Map<String, *>>,
        val assistantRecovery: AssistantPlanningArchive.Recovery
    )

    /**
     * Generates a suggested filename for export.
     */
    fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
        return "FocusBackup_${dateFormat.format(Date())}.focusbackup"
    }

    /**
     * Pre-checks whether a backup can be created without failures.
     * Returns false with a user-facing explanation if e.g. the Secret Archive Vault is locked.
     */
    suspend fun canCreateBackup(context: Context): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as FocusApplication
            val noteDb = NoteDatabase.getInstance(app)
            val notes = noteDb.noteDao().getAllNotesList()
            val subKey = ArchiveVaultSecurity.getActiveVaultSubKey()
            val hasLockedVaultNotes = notes.any { VaultPayloadEncryptor.isVaultEncrypted(it) } && subKey == null
            if (hasLockedVaultNotes) {
                Pair(false, "Your Secret Archive Vault is locked. Please unlock your Secret Archive Vault with your PIN or biometrics before creating a backup, so all notes can be secured.")
            } else {
                Pair(true, null)
            }
        } catch (_: Exception) {
            Pair(true, null)
        }
    }

    /**
     * Exports all databases, preferences, and media into an encrypted archive written to [destinationUri].
     */
    suspend fun createEncryptedBackup(
        context: Context,
        destinationUri: Uri,
        password: String
    ): Result<BackupMetadata> = AssistantStateLock.mutex.withLock {
        createEncryptedBackupLocked(context, destinationUri, password)
    }

    private suspend fun createEncryptedBackupLocked(
        context: Context, destinationUri: Uri, password: String
    ): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        try {
            if (password.isBlank()) {
                throw IllegalArgumentException("Backup password cannot be empty or blank.")
            }
            val app = context.applicationContext as FocusApplication
            val focusDb = app.database
            val noteDb = NoteDatabase.getInstance(app)
            val drillDb = DrillDatabase.getDatabase(app)
            val vocabDb = app.vocabDatabase

            // 1. Gather all database entities
            val restrictions = focusDb.appRestrictionDao().getAllRestrictionsSync()
            val schedules = focusDb.scheduleDao().getAllSchedulesSync()
            val tasks = focusDb.taskDao().getAllTasksList()
            val assistantPlanning = AssistantPlanningArchive.export(app)
            val habits = focusDb.habitDao().getAllHabitsSync()
            val habitLogs = focusDb.habitDao().getAllLogsSync()
            val notes = noteDb.noteDao().getAllNotesList()
            val subKey = ArchiveVaultSecurity.getActiveVaultSubKey()
            val hasLockedVaultNotes = notes.any { VaultPayloadEncryptor.isVaultEncrypted(it) } && subKey == null
            if (hasLockedVaultNotes) {
                throw IllegalStateException("Cannot create backup: Secret Archive Vault is locked. Please unlock your secret vault first.")
            }
            val processedNotes = notes.map { rawNote ->
                if (rawNote.isArchived || VaultPayloadEncryptor.isVaultEncrypted(rawNote)) {
                    val key = subKey ?: throw IllegalStateException(
                        "Cannot create backup: Secret Archive Vault is locked."
                    )
                    when (val result = VaultPayloadEncryptor.tryDecryptNotePayload(rawNote, key)) {
                        is VaultPayloadEncryptor.DecryptionResult.Success -> result.note
                        else -> throw SecurityException(
                            "Cannot create a complete backup because private note ${rawNote.id} could not be decrypted ($result)."
                        )
                    }
                } else {
                    rawNote
                }
            }
            val mediaManifest = buildMediaManifest(app, processedNotes)
            val mediaManifestPaths = mediaManifest.mapTo(hashSetOf()) { it.relativePath }
            val drillSessions = drillDb.drillSessionDao().getAllSessionsSync()
            val learnedIdioms = vocabDb.vocabDao().getAllLearnedIdioms()
            val learnedOws = vocabDb.vocabDao().getAllLearnedOws()
            val chatDb = AyvaChatDatabase.getDatabase(app)
            val chatMessages = chatDb.chatDao().getAllMessagesSync()

            val metadata = BackupMetadata(
                version = BACKUP_VERSION,
                createdAt = System.currentTimeMillis(),
                appVersion = "1.0.0",
                noteCount = processedNotes.size,
                taskCount = tasks.size,
                habitCount = habits.size,
                restrictionCount = restrictions.size,
                scheduleCount = schedules.size,
                drillCount = drillSessions.size,
                chatMessageCount = chatMessages.size,
                planCount = assistantPlanning.getJSONArray("plans").length()
            )

            // 2. Build JSON Payload
            val rootJson = JSONObject().apply {
                put("version", BACKUP_VERSION)
                put("createdAt", metadata.createdAt)
                put("appVersion", metadata.appVersion)

                // Manifest
                val metaObj = JSONObject().apply {
                    put("notes", processedNotes.size)
                    put("tasks", tasks.size)
                    put("habits", habits.size)
                    put("restrictions", restrictions.size)
                    put("schedules", schedules.size)
                    put("drillSessions", drillSessions.size)
                    put("chatMessages", chatMessages.size)
                    put("planWorkspaces", metadata.planCount)
                }
                put("meta", metaObj)

                // Notes
                val notesArray = JSONArray()
                processedNotes.forEach { note ->
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
                        put("fontKey", note.fontKey)
                        put("isPinned", note.isPinned)
                        put("isArchived", note.isArchived)
                        put("isTrashed", note.isTrashed)
                        if (note.trashedAt != null) put("trashedAt", note.trashedAt)
                        if (note.deletedAt != null) put("deletedAt", note.deletedAt)
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
                        put("subtasksJson", t.subtasksJson)
                        put("planningJson", t.planningJson)
                        put("updatedAt", t.updatedAt)
                        put("isTrashed", t.isTrashed)
                        put("trashedAt", t.trashedAt ?: JSONObject.NULL)
                        put("deletedAt", t.deletedAt ?: JSONObject.NULL)
                    })
                }
                put("tasks", taskArray)
                put("assistantPlanning", assistantPlanning)

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

                // Ayva Chat Messages
                val chatArray = JSONArray()
                chatMessages.forEach { msg ->
                    chatArray.put(JSONObject().apply {
                        put("id", msg.id)
                        put("text", msg.text)
                        put("isUser", msg.isUser)
                        put("timestamp", msg.timestamp)
                        put("firstViewedTimestamp", msg.firstViewedTimestamp)
                        put("isArithmetic", msg.isArithmetic)
                        if (msg.arithmeticJson != null) put("arithmeticJson", msg.arithmeticJson)
                        put("isDrillSummary", msg.isDrillSummary)
                        if (msg.drillSummaryJson != null) put("drillSummaryJson", msg.drillSummaryJson)
                        put("isAptitudeProfile", msg.isAptitudeProfile)
                        put("isStreakPrompt", msg.isStreakPrompt)
                        if (msg.streakPromptJson != null) put("streakPromptJson", msg.streakPromptJson)
                        put("isTaskSummary", msg.isTaskSummary)
                        if (msg.taskSummaryJson != null) put("taskSummaryJson", msg.taskSummaryJson)
                        put("isTalkAction", msg.isTalkAction)
                        if (msg.talkActionJson != null) put("talkActionJson", msg.talkActionJson)
                        if (msg.pendingActionJson != null) put("pendingActionJson", msg.pendingActionJson)
                        put("isDailyQuests", msg.isDailyQuests)
                        put("isMysteryBox", msg.isMysteryBox)
                        put("isMorningBrief", msg.isMorningBrief)
                        put("isEveningBrief", msg.isEveningBrief)
                        put("isStreakFreezeSkipped", msg.isStreakFreezeSkipped)
                        put("isVocabBrief", msg.isVocabBrief)
                        put("isVocabHub", msg.isVocabHub)
                        if (msg.vocabJson != null) put("vocabJson", msg.vocabJson)
                        put("isWelcome", msg.isWelcome)
                        put("isHabitsSummary", msg.isHabitsSummary)
                        if (msg.habitsSummaryJson != null) put("habitsSummaryJson", msg.habitsSummaryJson)
                    })
                }
                put("chatMessages", chatArray)

                // Preferences
                val prefsObj = JSONObject()
                val prefTypesObj = JSONObject()
                PREF_FILES.forEach { prefName ->
                    val sp = app.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    val map = sp.all
                    val pObj = JSONObject()
                    val tObj = JSONObject()
                    map.forEach { (k, v) ->
                        when (v) {
                            is Boolean -> {
                                pObj.put(k, v)
                                tObj.put(k, "bool")
                            }
                            is Int -> {
                                pObj.put(k, v)
                                tObj.put(k, "int")
                            }
                            is Long -> {
                                pObj.put(k, v)
                                tObj.put(k, "long")
                            }
                            is Float -> {
                                pObj.put(k, v.toDouble())
                                tObj.put(k, "float")
                            }
                            is Set<*> -> {
                                pObj.put(k, JSONArray(v))
                                tObj.put(k, "set")
                            }
                            is String -> {
                                pObj.put(k, v)
                                tObj.put(k, "string")
                            }
                            else -> {
                                pObj.put(k, v ?: JSONObject.NULL)
                            }
                        }
                    }
                    prefsObj.put(prefName, pObj)
                    prefTypesObj.put(prefName, tObj)
                }
                put("preferences", prefsObj)
                put("preferenceTypes", prefTypesObj)

                val mediaArray = JSONArray()
                mediaManifest.forEach { media ->
                    mediaArray.put(JSONObject().apply {
                        put("path", media.relativePath)
                        put("size", media.plaintextSize)
                        put("sha256", media.sha256)
                    })
                }
                put("mediaManifest", mediaArray)
            }

            // 3. Stream unencrypted entries directly into encrypted output stream
            withContext(NonCancellable) {
                app.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
                    CryptoBackupEngine.openEncryptingStream(outStream, password.toCharArray()).use { encryptingStream ->
                        ZipOutputStream(BufferedOutputStream(encryptingStream)).use { zipOut ->
                            // Write data.json
                            zipOut.putNextEntry(ZipEntry("data.json"))
                            zipOut.write(rootJson.toString().toByteArray(Charsets.UTF_8))
                            zipOut.closeEntry()

                            // Package media files: stream decrypted bytes from EncryptedMediaStorage
                            // so that media is encrypted by the backup password and portable across devices.
                            val buffer = ByteArray(32 * 1024)
                            MEDIA_FOLDERS.forEach { folderName ->
                                val mediaDir = File(app.filesDir, folderName)
                                if (mediaDir.exists() && mediaDir.isDirectory) {
                                    mediaDir.listFiles()?.forEach { file ->
                                        if (file.isFile && file.length() > 0 && "$folderName/${file.name}" in mediaManifestPaths) {
                                            if (EncryptedMediaStorage.isEncrypted(file)) {
                                                val mediaStream = EncryptedMediaStorage.openDecryptedInputStream(file)
                                                if (mediaStream != null) {
                                                    zipOut.putNextEntry(ZipEntry("media/$folderName/${file.name}"))
                                                    mediaStream.use { input ->
                                                        var read = input.read(buffer)
                                                        while (read != -1) {
                                                            zipOut.write(buffer, 0, read)
                                                            read = input.read(buffer)
                                                        }
                                                    }
                                                    zipOut.closeEntry()
                                                } else throw IOException("Cannot create complete backup: media file ${file.name} is unreadable")
                                            } else {
                                                zipOut.putNextEntry(ZipEntry("media/$folderName/${file.name}"))
                                                FileInputStream(file).use { input ->
                                                    var read = input.read(buffer)
                                                    while (read != -1) {
                                                        zipOut.write(buffer, 0, read)
                                                        read = input.read(buffer)
                                                    }
                                                }
                                                zipOut.closeEntry()
                                            }
                                        }
                                    }
                                }
                            }
                            zipOut.flush()
                        }
                    }
                } ?: throw IOException("Could not open destination file stream.")
            }

            // 4. Verify & Authenticate created backup before reporting success (WP5 requirement 7)
            app.contentResolver.openInputStream(destinationUri)?.use { verifyInStream ->
                CryptoBackupEngine.openDecryptingStream(verifyInStream, password.toCharArray()).use { decryptedStream ->
                    ZipInputStream(BufferedInputStream(decryptedStream)).use { zipIn ->
                        var foundManifest = false
                        val verifiedMedia = mutableMapOf<String, Pair<Long, String>>()
                        val verifyBuffer = ByteArray(32 * 1024)
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            if (entry.name == "data.json") {
                                foundManifest = true
                            } else if (entry.name.startsWith("media/")) {
                                val digest = MessageDigest.getInstance("SHA-256")
                                var size = 0L
                                var read = zipIn.read(verifyBuffer)
                                while (read != -1) {
                                    digest.update(verifyBuffer, 0, read)
                                    size += read
                                    read = zipIn.read(verifyBuffer)
                                }
                                verifiedMedia[entry.name.removePrefix("media/")] = size to digest.digest().toHex()
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                        if (!foundManifest) {
                            throw IOException("Backup verification failed: data.json manifest missing from created archive.")
                        }
                        mediaManifest.forEach { expected ->
                            val actual = verifiedMedia[expected.relativePath]
                            if (actual == null || actual.first != expected.plaintextSize || actual.second != expected.sha256) {
                                throw IOException("Backup verification failed for media ${expected.relativePath}")
                            }
                        }
                    }
                }
            } ?: throw IOException("Could not open destination backup stream for post-creation verification.")

            context.getSharedPreferences("ayva_backup_verification", Context.MODE_PRIVATE).edit()
                .putLong("last_verified_export_ms", System.currentTimeMillis()).apply()
            Log.i(TAG, "Encrypted backup created and verified successfully: ${metadata.noteCount} notes, ${metadata.taskCount} tasks.")
            Result.success(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted backup", e)
            try {
                android.provider.DocumentsContract.deleteDocument(context.contentResolver, destinationUri)
            } catch (_: Exception) {}
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
        cleanRestore: Boolean = false,
        vaultPin: String? = null
    ): Result<BackupMetadata> = AssistantStateLock.mutex.withLock {
        restoreEncryptedBackupLocked(context, sourceUri, password, cleanRestore, vaultPin)
    }

    private suspend fun restoreEncryptedBackupLocked(
        context: Context, sourceUri: Uri, password: String, cleanRestore: Boolean, vaultPin: String?
    ): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        val app = context.applicationContext as FocusApplication
        val stagingDir = File(app.cacheDir, "restore_media_stage_${System.currentTimeMillis()}").apply { mkdirs() }
        val mediaDestinations = linkedMapOf<String, File>()
        var preparedMedia: List<Pair<File, File>> = emptyList()
        var rollbackState: RestoreRollbackState? = null
        var mutationStarted = false
        try {
            if (password.isBlank()) {
                throw IllegalArgumentException("Backup password cannot be empty or blank.")
            }
            val focusDb = app.database
            val noteDb = NoteDatabase.getInstance(app)
            val drillDb = DrillDatabase.getDatabase(app)
            val vocabDb = app.vocabDatabase
            val chatDb = AyvaChatDatabase.getDatabase(app)
            val inStream = app.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("Could not open backup source stream.")

            var jsonDataStr: String? = null

            // 1 & 2. Decrypt & Unpack ZIP into staging directory on-the-fly
            var totalDecompressedMediaBytes = 0L
            val maxSingleEntrySize = 250L * 1024L * 1024L // streamed; bounded against decompression abuse
            val maxTotalMediaSize = 500L * 1024L * 1024L // 500MB total media archive decompression limit

            inStream.use { rawInStream ->
                CryptoBackupEngine.openDecryptingStream(rawInStream, password.toCharArray()).use { decryptedStream ->
                    ZipInputStream(BufferedInputStream(decryptedStream)).use { zipIn ->
                        val buffer = ByteArray(32 * 1024)
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                if (entry.name == "data.json") {
                                    val maxManifestSize = 50L * 1024L * 1024L
                                    var totalManifestBytes = 0L
                                    val baos = ByteArrayOutputStream()
                                    var read = zipIn.read(buffer)
                                    while (read != -1) {
                                        totalManifestBytes += read
                                        if (totalManifestBytes > maxManifestSize) {
                                            throw SecurityException("Backup manifest exceeds maximum allowed size (50MB).")
                                        }
                                        baos.write(buffer, 0, read)
                                        read = zipIn.read(buffer)
                                    }
                                    jsonDataStr = baos.toString(Charsets.UTF_8.name())
                                } else if (entry.name.startsWith("media/")) {
                                    val normalized = entry.name.removePrefix("media/").replace('\\', '/').trimStart('/')
                                    val topFolder = normalized.substringBefore('/')
                                    if (topFolder !in MEDIA_FOLDERS || normalized.contains("..") || normalized.isEmpty()) {
                                        throw SecurityException("Invalid or disallowed media path in backup archive: $normalized. Must reside in: $MEDIA_FOLDERS")
                                    }
                                    val targetDir = File(stagingDir, topFolder).canonicalFile
                                    val targetFile = File(stagingDir, normalized).canonicalFile
                                    if (!targetFile.toPath().startsWith(targetDir.toPath())) {
                                        throw SecurityException("Zip Slip directory traversal detected in backup media entry: $normalized")
                                    }
                                    targetFile.parentFile?.mkdirs()
                                    val importedName = if (cleanRestore) {
                                        targetFile.name
                                    } else {
                                        "import_${UUID.randomUUID()}_${targetFile.name}"
                                    }
                                    mediaDestinations[normalized] = File(File(app.filesDir, topFolder), importedName)
                                    var fileBytesWritten = 0L
                                    FileOutputStream(targetFile).use { fos ->
                                        var read = zipIn.read(buffer)
                                        while (read != -1) {
                                            fileBytesWritten += read
                                            totalDecompressedMediaBytes += read
                                            if (fileBytesWritten > maxSingleEntrySize) {
                                                throw SecurityException("Backup media entry exceeds maximum allowed size (250MB): $normalized")
                                            }
                                            if (totalDecompressedMediaBytes > maxTotalMediaSize) {
                                                throw SecurityException("Backup archive exceeds maximum allowable total media size (500MB).")
                                            }
                                            fos.write(buffer, 0, read)
                                            read = zipIn.read(buffer)
                                        }
                                    }
                                }
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                }
            }

            if (jsonDataStr == null) {
                throw IllegalArgumentException("Backup archive does not contain data.json manifest.")
            }

            val rootJson = JSONObject(jsonDataStr!!)
            val version = rootJson.optInt("version", BACKUP_VERSION)
            val createdAt = rootJson.optLong("createdAt", System.currentTimeMillis())
            val appVersion = rootJson.optString("appVersion", "1.0.0")

            // Parse and validate the entire manifest before changing vault settings, databases,
            // preferences, or live media. This prevents a late malformed entity from producing
            // a successful partial restore.
            validateRestoreManifest(rootJson)
            val assistantPlanning = if (rootJson.has("assistantPlanning")) rootJson.getJSONObject("assistantPlanning") else null
            AssistantPlanningArchive.preflight(app, assistantPlanning, cleanRestore)
            validateStagedMedia(rootJson, stagingDir)
            checkNotNull(DataSafetyManager.writePreOpSnapshot(app, noteDb.noteDao(), "pre_encrypted_restore", focusDb)) {
                "Could not create the required pre-restore safety snapshot. No data was changed."
            }
            rollbackState = captureRollbackState(app, noteDb, focusDb, drillDb, vocabDb, chatDb)

            // 4. Validate Secret Vault State and Build NoteEntities
            val notesArray = rootJson.optJSONArray("notes") ?: JSONArray()
            val hasArchivedNotes = (0 until notesArray.length()).any {
                notesArray.getJSONObject(it).optBoolean("isArchived", false)
            }
            if (hasArchivedNotes) {
                val vaultStatus = ArchiveVaultSecurity.getVaultStatus(app)
                if (vaultStatus == ArchiveVaultSecurity.VaultStatus.ENABLED) {
                    if (ArchiveVaultSecurity.isVaultLocked(app)) {
                        if (vaultPin != null && vaultPin.length == 6) {
                            val verifyRes = ArchiveVaultSecurity.verifyPasscode(app, vaultPin)
                            if (verifyRes !is ArchiveVaultSecurity.VerifyResult.Success) {
                                throw SecurityException("Incorrect Vault PIN provided for private notes.")
                            }
                        } else {
                            throw IllegalStateException("Cannot restore backup: Secret Archive Vault is locked. Please unlock your secret vault first so private notes can be restored securely.")
                        }
                    }
                } else {
                    // Vault is NOT configured or disabled on this device!
                    if (vaultPin != null && vaultPin.length == 6) {
                        mutationStarted = true // setPasscode persists credentials.
                        val setSuccess = ArchiveVaultSecurity.setPasscode(app, vaultPin)
                        if (!setSuccess) {
                            throw IllegalStateException("Failed to configure Secret Archive Vault with provided PIN.")
                        }
                    } else {
                        throw RequiresVaultSetupException("This backup contains private Secret Archive Vault notes. Please set a 6-digit Vault PIN to encrypt and restore them securely on this device.")
                    }
                }
            }

            val noteEntities = mutableListOf<NoteEntity>()
            val subKey = ArchiveVaultSecurity.getActiveVaultSubKey()
            for (i in 0 until notesArray.length()) {
                val obj = notesArray.getJSONObject(i)
                var note = NoteEntity(
                    id = if (cleanRestore) obj.optLong("id", 0L) else 0L,
                    title = obj.optString("title", ""),
                    content = remapBlockMedia(obj.optString("content", ""), mediaDestinations),
                    isChecklist = obj.optBoolean("isChecklist", false),
                    checklistJson = obj.optString("checklistJson", "[]"),
                    labelsJson = obj.optString("labelsJson", "[]"),
                    imageUrisJson = remapMediaJson(obj.optString("imageUrisJson", "[]"), mediaDestinations),
                    audioUrisJson = remapMediaJson(obj.optString("audioUrisJson", "[]"), mediaDestinations),
                    colorKey = obj.optString("colorKey", "default"),
                    fontKey = obj.optString("fontKey", "default"),
                    isPinned = obj.optBoolean("isPinned", false),
                    isArchived = obj.optBoolean("isArchived", false),
                    isTrashed = obj.optBoolean("isTrashed", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    trashedAt = if (obj.has("trashedAt") && !obj.isNull("trashedAt")) obj.optLong("trashedAt") else null,
                    deletedAt = if (obj.has("deletedAt") && !obj.isNull("deletedAt")) obj.optLong("deletedAt") else null
                )
                if (note.isArchived) {
                    if (subKey != null) {
                        if (!VaultPayloadEncryptor.isVaultEncrypted(note)) {
                            note = VaultPayloadEncryptor.encryptNotePayload(note, subKey)
                        }
                    } else {
                        // Fail-closed security: NEVER allow an archived note into the database without encryption
                        throw SecurityException("Security violation: Vault Sub-Key missing. Refusing to store private note in plaintext.")
                    }
                }
                noteEntities.add(note)
            }

            // Complete all expensive/fallible media decryption and device-key encryption before
            // any live database is changed. Installation later is rename-only and rollback-safe.
            preparedMedia = prepareRestoredMedia(stagingDir, mediaDestinations)

            NotesViewModel.latestNotesCache.clear()

            // Safe transactional swap:
            // Insert all restored notes FIRST, then prune rows not in the backup — all in one transaction.
            // If anything throws, Room rolls back and existing data is completely untouched.
            mutationStarted = true
            if (cleanRestore) {
                noteDb.withTransaction {
                    noteEntities.forEach { note -> noteDb.noteDao().insertNote(note) }
                    val restoredIds = noteEntities.map { it.id }.filter { it > 0L }
                    if (restoredIds.isNotEmpty()) {
                        noteDb.noteDao().deleteNotesNotIn(restoredIds)
                    } else {
                        // Backup had no notes with stable IDs (merge mode) — clear all and re-insert
                        noteDb.noteDao().deleteAllNotes()
                        noteEntities.forEach { note -> noteDb.noteDao().insertNote(note) }
                    }
                }
            } else {
                noteEntities.forEach { note -> noteDb.noteDao().insertNote(note) }
            }

            try {
                NoteWidgetProvider.updateAllWidgets(app)
            } catch (_: Exception) {}

            // 5. Restore Restrictions, Schedules, Tasks, and Habits atomically
            val restrArray = rootJson.optJSONArray("restrictions") ?: JSONArray()
            val schedArray = rootJson.optJSONArray("schedules") ?: JSONArray()
            val taskArray = rootJson.optJSONArray("tasks") ?: JSONArray()
            val habitArray = rootJson.optJSONArray("habits") ?: JSONArray()
            val taskIdMapping = mutableMapOf<Long, Long>()

            focusDb.withTransaction {
                if (cleanRestore) {
                    focusDb.appRestrictionDao().deleteAllRestrictions()
                    focusDb.scheduleDao().deleteAllSchedules()
                    focusDb.taskDao().deleteAllTasks()
                    focusDb.habitDao().deleteAllLogs()
                    focusDb.habitDao().deleteAllHabits()
                }

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
                        isPriority = obj.optBoolean("isPriority", false),
                        subtasksJson = obj.optString("subtasksJson", "[]"),
                        planningJson = obj.optString("planningJson", ""),
                        updatedAt = if (obj.has("updatedAt") && !obj.isNull("updatedAt")) obj.optLong("updatedAt") else System.currentTimeMillis(),
                        isTrashed = obj.optBoolean("isTrashed", false),
                        trashedAt = if (obj.has("trashedAt") && !obj.isNull("trashedAt")) obj.optLong("trashedAt") else null,
                        deletedAt = if (obj.has("deletedAt") && !obj.isNull("deletedAt")) obj.optLong("deletedAt") else null
                    )
                    val restoredId = focusDb.taskDao().insertTask(task)
                    if (obj.optLong("id", 0L) > 0) taskIdMapping[obj.getLong("id")] = restoredId
                }

                for (id in taskIdMapping.values) {
                    val task = focusDb.taskDao().getTaskById(id)!!
                    if (task.planningJson.isNotEmpty()) focusDb.taskDao().updateTask(task.copy(
                        planningJson = com.focusbyrj.app.data.TaskPlanning.remap(task.planningJson, taskIdMapping)))
                }

                // 8. Restore Habits
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
            }

            // 9. Restore Drill Sessions
            val drillArray = rootJson.optJSONArray("drillSessions") ?: JSONArray()
            drillDb.withTransaction {
                if (cleanRestore) {
                    drillDb.drillSessionDao().deleteAllSessions()
                }
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
            }

            // 10. Restore Vocab
            val idiomsArray = rootJson.optJSONArray("learnedIdioms") ?: JSONArray()
            val owsArray = rootJson.optJSONArray("learnedOws") ?: JSONArray()
            if (idiomsArray.length() > 0 || owsArray.length() > 0) {
                vocabDb.withTransaction {
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
                }
            }

            // 11. Restore Ayva Chat Messages
            val chatArray = rootJson.optJSONArray("chatMessages") ?: JSONArray()
            if (chatArray.length() > 0) {
                val chatEntities = mutableListOf<AyvaChatMessageEntity>()
                for (i in 0 until chatArray.length()) {
                    val obj = chatArray.getJSONObject(i)
                    chatEntities.add(
                        AyvaChatMessageEntity(
                            id = obj.getString("id"),
                            text = obj.optString("text", ""),
                            isUser = obj.optBoolean("isUser", false),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            firstViewedTimestamp = obj.optLong("firstViewedTimestamp", 0L),
                            isArithmetic = obj.optBoolean("isArithmetic", false),
                            arithmeticJson = if (obj.has("arithmeticJson") && !obj.isNull("arithmeticJson")) obj.optString("arithmeticJson") else null,
                            isDrillSummary = obj.optBoolean("isDrillSummary", false),
                            drillSummaryJson = if (obj.has("drillSummaryJson") && !obj.isNull("drillSummaryJson")) obj.optString("drillSummaryJson") else null,
                            isAptitudeProfile = obj.optBoolean("isAptitudeProfile", false),
                            isStreakPrompt = obj.optBoolean("isStreakPrompt", false),
                            streakPromptJson = if (obj.has("streakPromptJson") && !obj.isNull("streakPromptJson")) obj.optString("streakPromptJson") else null,
                            isTaskSummary = obj.optBoolean("isTaskSummary", false),
                            taskSummaryJson = if (obj.has("taskSummaryJson") && !obj.isNull("taskSummaryJson")) obj.optString("taskSummaryJson") else null,
                            isTalkAction = obj.optBoolean("isTalkAction", false),
                            talkActionJson = if (obj.has("talkActionJson") && !obj.isNull("talkActionJson")) obj.optString("talkActionJson") else null,
                            pendingActionJson = if (obj.has("pendingActionJson") && !obj.isNull("pendingActionJson")) obj.optString("pendingActionJson") else null,
                            isDailyQuests = obj.optBoolean("isDailyQuests", false),
                            isMysteryBox = obj.optBoolean("isMysteryBox", false),
                            isMorningBrief = obj.optBoolean("isMorningBrief", false),
                            isEveningBrief = obj.optBoolean("isEveningBrief", false),
                            isStreakFreezeSkipped = obj.optBoolean("isStreakFreezeSkipped", false),
                            isVocabBrief = obj.optBoolean("isVocabBrief", false),
                            isVocabHub = obj.optBoolean("isVocabHub", false),
                            vocabJson = if (obj.has("vocabJson") && !obj.isNull("vocabJson")) obj.optString("vocabJson") else null,
                            isWelcome = obj.optBoolean("isWelcome", false),
                            isHabitsSummary = obj.optBoolean("isHabitsSummary", false),
                            habitsSummaryJson = if (obj.has("habitsSummaryJson") && !obj.isNull("habitsSummaryJson")) obj.optString("habitsSummaryJson") else null
                        )
                    )
                }
                chatDb.withTransaction {
                    if (cleanRestore) {
                        chatDb.chatDao().clearAllMessages()
                    }
                    chatDb.chatDao().insertMessages(chatEntities)
                }
            }

            // 12. Restore SharedPreferences
            val prefsObj = rootJson.optJSONObject("preferences")
            val prefTypesObj = rootJson.optJSONObject("preferenceTypes")
            if (prefsObj != null) {
                val keys = prefsObj.keys()
                while (keys.hasNext()) {
                    val prefName = keys.next()
                    if (prefName !in PREF_FILES) {
                        Log.w(TAG, "Skipping disallowed preference file from archive: $prefName")
                        continue
                    }
                    val pObj = prefsObj.getJSONObject(prefName)
                    val tObj = prefTypesObj?.optJSONObject(prefName)
                    val sp = app.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    val editor = sp.edit()
                    if (cleanRestore) {
                        editor.clear()
                    }
                    val innerKeys = pObj.keys()
                    while (innerKeys.hasNext()) {
                        val k = innerKeys.next()
                        val v = pObj.get(k)
                        val explicitType = if (tObj?.has(k) == true) tObj.getString(k) else null

                        if (explicitType != null) {
                            when (explicitType) {
                                "bool" -> editor.putBoolean(k, pObj.optBoolean(k))
                                "int" -> editor.putInt(k, pObj.optInt(k))
                                "long" -> editor.putLong(k, pObj.optLong(k))
                                "float" -> editor.putFloat(k, pObj.optDouble(k).toFloat())
                                "string" -> editor.putString(k, pObj.optString(k))
                                "set" -> {
                                    val arr = pObj.optJSONArray(k)
                                    val set = mutableSetOf<String>()
                                    if (arr != null) {
                                        for (s in 0 until arr.length()) {
                                            set.add(arr.getString(s))
                                        }
                                    }
                                    editor.putStringSet(k, set)
                                }
                            }
                        } else {
                            // Smart type resolution for legacy backups without explicit types
                            if (isKnownLongPreference(prefName, k) && v is Number) {
                                editor.putLong(k, v.toLong())
                            } else if (isKnownFloatPreference(prefName, k) && v is Number) {
                                editor.putFloat(k, v.toFloat())
                            } else {
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
                        }
                    }
                    check(editor.commit()) { "Failed to persist restored preferences for $prefName" }
                }
            }

            // 13. Commit Staged Media Files to Internal Storage (re-encrypting with this device's hardware KeyStore key)
            installPreparedMedia(preparedMedia)
            AssistantPlanningArchive.restore(app, assistantPlanning, taskIdMapping, cleanRestore)
            com.focusbyrj.app.util.AyvaTalkEngine.resetConversation()

            // Reload In-Memory Managers
            try { FocusEconomyManager.init(app) } catch (_: Exception) {}
            try { FocusStatsManager.init(app) } catch (_: Exception) {}
            try { BubbleChatManager.init(app) } catch (_: Exception) {}
            try { AptitudeManager.init(app) } catch (_: Exception) {}
            try { DailyQuestManager.init(app) } catch (_: Exception) {}

            // 14. Reschedule Alarms & Update Widgets
            try {
                HabitAlarmScheduler.rescheduleAllHabits(app)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reschedule habit reminders post-restore", e)
            }

            try {
                val now = System.currentTimeMillis()
                val activeTasks = focusDb.taskDao().getAllTasksList()
                com.focusbyrj.app.util.WorkSessionReminders.reconcileAll(app)
                activeTasks.filter { !it.isCompleted && !it.isTrashed }
                    .forEach { TaskReminderHelper.scheduleReminder(app, it) }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reschedule task reminders post-restore", e)
            }

            try {
                NoteWidgetProvider.updateAllWidgets(app)
            } catch (_: Exception) {}
            try {
                TodoWidgetProvider.updateAllWidgets(app)
            } catch (_: Exception) {}

            // Lock Secret Archive Vault post-restore so newly restored private notes are immediately protected
            if (ArchiveVaultSecurity.getVaultStatus(app) == ArchiveVaultSecurity.VaultStatus.ENABLED) {
                ArchiveVaultSecurity.lockVault()
            }

            val metadata = BackupMetadata(
                version = version,
                createdAt = createdAt,
                appVersion = appVersion,
                noteCount = noteEntities.size,
                taskCount = taskArray.length(),
                habitCount = habitArray.length(),
                restrictionCount = restrArray.length(),
                scheduleCount = schedArray.length(),
                drillCount = drillArray.length(),
                chatMessageCount = chatArray.length(),
                planCount = assistantPlanning?.getJSONArray("plans")?.length() ?: 0
            )

            Log.i(TAG, "Encrypted backup restored successfully.")
            Result.success(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore encrypted backup", e)
            if (mutationStarted && rollbackState != null) {
                try {
                    restoreRollbackState(app, rollbackState!!)
                    Log.w(TAG, "Restore failed after mutation; all captured stores were rolled back")
                } catch (rollbackError: Exception) {
                    Log.e(TAG, "CRITICAL: automatic restore rollback failed", rollbackError)
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Restore failed and automatic rollback could not be completed. Use the pre-restore safety snapshot before continuing.",
                            rollbackError
                        )
                    )
                }
            }
            val friendlyException = when {
                e is RequiresVaultSetupException -> e
                e is SecurityException -> e
                e is java.util.zip.ZipException -> SecurityException("Failed to restore backup: Incorrect password or corrupted backup file.", e)
                e is IllegalArgumentException && e.message?.contains("data.json") == true -> SecurityException("Failed to restore backup: Incorrect password or corrupted backup file.", e)
                else -> e
            }
            Result.failure(friendlyException)
        } finally {
            preparedMedia.forEach { it.second.delete() }
            try {
                stagingDir.deleteRecursively()
            } catch (_: Exception) {}
            if (ArchiveVaultSecurity.getVaultStatus(app) == ArchiveVaultSecurity.VaultStatus.ENABLED) {
                ArchiveVaultSecurity.lockVault()
            }
        }
    }

    private fun isKnownLongPreference(prefName: String, key: String): Boolean {
        if (key.startsWith("focus_day_")) return true
        if (key.startsWith("drill_day_")) return true
        if (key.startsWith("unlock_")) return true
        if (key.endsWith("_timestamp") || key.endsWith("_expiry") || key.endsWith("_time") || key.endsWith("_ms")) {
            return true
        }
        if (prefName == "focus_stats_prefs" && (key == "app_install_timestamp" || key.contains("streak_last_date_epoch"))) return true
        if (prefName == "aptitude_economy_prefs" && key == "xp_boost_expiry_timestamp") return true
        if (prefName == "usage_break_tracker_prefs" && key.contains("alert_time")) return true
        if (prefName == "bubble_chat_prefs" && key == "last_chat_activity_timestamp") return true
        if (prefName == "auto_backup_scheduler_prefs" && key == "last_backup_timestamp_ms") return true
        return false
    }

    private fun validateRestoreManifest(root: JSONObject) {
        fun requireObjects(arrayName: String, requiredFields: List<String> = emptyList()) {
            val array = root.optJSONArray(arrayName) ?: return
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                    ?: throw IllegalArgumentException("$arrayName[$i] must be an object")
                requiredFields.forEach { field ->
                    if (!obj.has(field) || obj.isNull(field)) {
                        throw IllegalArgumentException("$arrayName[$i] is missing required field '$field'")
                    }
                }
            }
        }

        requireObjects("notes")
        requireObjects("restrictions", listOf("packageName"))
        requireObjects("schedules")
        requireObjects("tasks", listOf("title"))
        requireObjects("habits", listOf("title"))
        requireObjects("habitLogs", listOf("habitId", "date"))
        requireObjects("drillSessions", listOf("sessionId"))
        requireObjects("learnedIdioms", listOf("id"))
        requireObjects("learnedOws", listOf("id"))
        requireObjects("chatMessages", listOf("id"))

        fun each(arrayName: String, block: (JSONObject) -> Unit) {
            val array = root.optJSONArray(arrayName) ?: return
            for (i in 0 until array.length()) block(array.getJSONObject(i))
        }
        each("restrictions") { it.getString("packageName") }
        each("tasks") { it.getString("title") }
        val planningTasks = root.optJSONArray("tasks")?.let { a -> (0 until a.length()).map { i ->
            val obj = a.getJSONObject(i)
            Task(id = obj.optLong("id", 0L), title = obj.getString("title"), planningJson = obj.optString("planningJson", ""))
        } }.orEmpty()
        com.focusbyrj.app.data.TaskPlanning.validateGraph(planningTasks)
        val taskIds = root.optJSONArray("tasks")?.let { array ->
            (0 until array.length()).mapNotNull { array.getJSONObject(it).optLong("id", 0L).takeIf { id -> id > 0 } }
        }.orEmpty()
        require(taskIds.distinct().size == taskIds.size) { "Backup contains duplicate task IDs" }
        if (root.has("assistantPlanning")) {
            com.focusbyrj.app.util.assistant.validateDocument(root.getJSONObject("assistantPlanning"))
        }
        each("habits") { it.getString("title") }
        each("habitLogs") { it.getLong("habitId"); it.getString("date") }
        each("drillSessions") { it.getString("sessionId") }
        each("learnedIdioms") { it.getInt("id") }
        each("learnedOws") { it.getInt("id") }
        each("chatMessages") { it.getString("id") }

        root.optJSONObject("meta")?.let { meta ->
            if (meta.has("planWorkspaces")) require(meta.getInt("planWorkspaces") == (root.optJSONObject("assistantPlanning")?.getJSONArray("plans")?.length() ?: 0)) {
                "Backup manifest count mismatch for plan workspaces"
            }
            mapOf(
                "notes" to "notes", "tasks" to "tasks", "habits" to "habits",
                "restrictions" to "restrictions", "schedules" to "schedules",
                "drillSessions" to "drillSessions", "chatMessages" to "chatMessages"
            ).forEach { (metaKey, arrayKey) ->
                if (meta.has(metaKey)) {
                    val actual = root.optJSONArray(arrayKey)?.length() ?: 0
                    require(meta.optInt(metaKey, -1) == actual) {
                        "Backup manifest count mismatch for $arrayKey"
                    }
                }
            }
        }

        val prefs = root.optJSONObject("preferences")
        prefs?.keys()?.forEach { name ->
            require(prefs.optJSONObject(name) != null) { "Preference file '$name' must be an object" }
        }
    }

    private suspend fun captureRollbackState(
        context: Context,
        noteDb: NoteDatabase,
        focusDb: FocusDatabase,
        drillDb: DrillDatabase,
        vocabDb: VocabDatabase,
        chatDb: AyvaChatDatabase
    ): RestoreRollbackState {
        val preferenceNames = (PREF_FILES + ArchiveVaultSecurity.PREFS_NAME + "ayva_composer").distinct()
        return RestoreRollbackState(
            notes = noteDb.noteDao().getAllNotesList(),
            restrictions = focusDb.appRestrictionDao().getAllRestrictionsSync(),
            schedules = focusDb.scheduleDao().getAllSchedulesSync(),
            tasks = focusDb.taskDao().getAllTasksList(),
            habits = focusDb.habitDao().getAllHabitsSync(),
            habitLogs = focusDb.habitDao().getAllLogsSync(),
            drillSessions = drillDb.drillSessionDao().getAllSessionsSync(),
            idioms = vocabDb.vocabDao().getAllIdioms(),
            ows = vocabDb.vocabDao().getAllOws(),
            chatMessages = chatDb.chatDao().getAllMessagesSync(),
            assistantRecovery = AssistantPlanningArchive.captureRecovery(context),
            preferences = preferenceNames.associateWith { name ->
                HashMap(context.getSharedPreferences(name, Context.MODE_PRIVATE).all)
            }
        )
    }

    private suspend fun restoreRollbackState(context: Context, state: RestoreRollbackState) {
        val app = context.applicationContext as FocusApplication
        val noteDb = NoteDatabase.getInstance(app)
        noteDb.withTransaction {
            noteDb.noteDao().deleteAllNotes()
            if (state.notes.isNotEmpty()) noteDb.noteDao().insertNotes(state.notes)
        }

        val focusDb = app.database
        focusDb.withTransaction {
            focusDb.habitDao().deleteAllLogs()
            focusDb.habitDao().deleteAllHabits()
            focusDb.taskDao().deleteAllTasks()
            focusDb.scheduleDao().deleteAllSchedules()
            focusDb.appRestrictionDao().deleteAllRestrictions()
            if (state.restrictions.isNotEmpty()) focusDb.appRestrictionDao().insertRestrictions(state.restrictions)
            if (state.schedules.isNotEmpty()) focusDb.scheduleDao().insertSchedules(state.schedules)
            if (state.tasks.isNotEmpty()) focusDb.taskDao().insertTasks(state.tasks)
            if (state.habits.isNotEmpty()) focusDb.habitDao().insertHabits(state.habits)
            state.habitLogs.forEach { focusDb.habitDao().insertOrUpdateLog(it) }
        }

        val drillDb = DrillDatabase.getDatabase(app)
        drillDb.withTransaction {
            drillDb.drillSessionDao().deleteAllSessions()
            state.drillSessions.forEach { drillDb.drillSessionDao().insertSession(it) }
        }

        val vocabDb = app.vocabDatabase
        vocabDb.withTransaction {
            state.idioms.forEach { vocabDb.vocabDao().updateIdiom(it) }
            state.ows.forEach { vocabDb.vocabDao().updateOws(it) }
        }

        AyvaChatDatabase.getDatabase(app).chatDao().syncAllMessages(state.chatMessages)
        state.preferences.forEach { (name, values) ->
            val editor = app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            values.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            check(editor.commit()) { "Could not roll back preferences '$name'" }
        }
        AssistantPlanningArchive.recover(app, state.assistantRecovery)
        ArchiveVaultSecurity.lockVault()
        NotesViewModel.latestNotesCache.clear()
    }

    private fun validateStagedMedia(root: JSONObject, stagingDir: File) {
        val manifest = root.optJSONArray("mediaManifest")
        if (manifest == null) {
            require(root.optInt("version", 1) < 2) { "Backup v2 is missing its media inventory" }
            return // legacy backup
        }
        val expectedPaths = mutableSetOf<String>()
        for (i in 0 until manifest.length()) {
            val obj = manifest.getJSONObject(i)
            val relative = obj.getString("path").replace('\\', '/').trimStart('/')
            require(relative.substringBefore('/') in MEDIA_FOLDERS && !relative.contains("..")) {
                "Invalid media manifest path: $relative"
            }
            require(expectedPaths.add(relative)) { "Duplicate media manifest path: $relative" }
            val file = File(stagingDir, relative).canonicalFile
            require(file.toPath().startsWith(stagingDir.canonicalFile.toPath()) && file.isFile) {
                "Backup is incomplete: media '$relative' is missing"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            FileInputStream(file).use { input ->
                val buffer = ByteArray(32 * 1024)
                var read = input.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    size += read
                    read = input.read(buffer)
                }
            }
            require(size == obj.getLong("size") && digest.digest().toHex() == obj.getString("sha256")) {
                "Backup media verification failed for '$relative'"
            }
        }
        val stagedPaths = MEDIA_FOLDERS.flatMap { folder ->
            File(stagingDir, folder).listFiles()?.filter { it.isFile }?.map { "$folder/${it.name}" } ?: emptyList()
        }.toSet()
        require(stagedPaths == expectedPaths) { "Backup media inventory does not match the archive" }

        val notes = root.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until notes.length()) {
            val obj = notes.getJSONObject(i)
            val note = NoteEntity(
                content = obj.optString("content", ""),
                imageUrisJson = obj.optString("imageUrisJson", "[]"),
                audioUrisJson = obj.optString("audioUrisJson", "[]")
            )
            collectNoteMediaPaths(note).forEach { path ->
                val normalized = path.replace('\\', '/')
                val folder = MEDIA_FOLDERS.firstOrNull {
                    normalized.contains("/$it/") || normalized.startsWith("$it/")
                } ?: throw IOException("Backup contains an unsupported attachment path: $path")
                val relative = "$folder/${normalized.substringAfterLast('/')}"
                require(relative in expectedPaths) { "Backup is missing referenced media '$relative'" }
            }
        }
    }

    private fun buildMediaManifest(context: Context, notes: List<NoteEntity>): List<MediaManifestEntry> {
        val referenced = notes.flatMap { collectNoteMediaPaths(it) }.toSet()
        referenced.forEach { path ->
            val normalized = path.replace('\\', '/')
            val folder = MEDIA_FOLDERS.firstOrNull { normalized.contains("/$it/") || normalized.startsWith("$it/") }
                ?: throw IOException("Cannot create complete backup: unsupported attachment path '$path'")
            val file = File(path)
            val allowedRoot = File(context.filesDir, folder).canonicalFile
            val canonical = file.canonicalFile
            if (!canonical.toPath().startsWith(allowedRoot.toPath()) || !canonical.isFile) {
                throw IOException("Cannot create complete backup: referenced media is missing or outside app storage: $path")
            }
        }

        val result = mutableListOf<MediaManifestEntry>()
        val uniqueReferencedFiles = linkedMapOf<String, File>()
        referenced.forEach { path ->
            val normalized = path.replace('\\', '/')
            val folder = MEDIA_FOLDERS.first { normalized.contains("/$it/") || normalized.startsWith("$it/") }
            uniqueReferencedFiles["$folder/${normalized.substringAfterLast('/')}"] = File(path)
        }
        uniqueReferencedFiles.forEach { (relativePath, file) ->
                val stream = if (EncryptedMediaStorage.isEncrypted(file)) {
                    EncryptedMediaStorage.openDecryptedInputStream(file)
                } else FileInputStream(file)
                stream ?: throw IOException("Cannot create complete backup: media file ${file.name} is unreadable")
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                stream.use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var read = input.read(buffer)
                    while (read != -1) {
                        digest.update(buffer, 0, read)
                        size += read
                        read = input.read(buffer)
                    }
                }
                result += MediaManifestEntry(relativePath, size, digest.digest().toHex())
        }
        return result
    }

    private fun collectNoteMediaPaths(note: NoteEntity): List<String> {
        val result = mutableListOf<String>()
        result += note.getImageUris()
        result += note.getAudioUris()
        if (note.content.contains(NotesnookBlockManager.BLOCKS_PREFIX)) {
            NotesnookBlockManager.parse(note.content).forEach { block ->
                when (block) {
                    is NotesnookBlock.Image -> if (block.uri.isNotBlank()) result += block.uri
                    is NotesnookBlock.Attachment -> if (block.uri.isNotBlank()) result += block.uri
                    else -> Unit
                }
            }
        }
        return result
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

    private fun remapMediaPath(path: String, destinations: Map<String, File>): String {
        val normalized = path.replace('\\', '/')
        val folder = MEDIA_FOLDERS.firstOrNull { normalized.contains("/$it/") || normalized.startsWith("$it/") }
            ?: return path
        val key = "$folder/${normalized.substringAfterLast('/')}"
        return destinations[key]?.absolutePath ?: path
    }

    private fun remapMediaJson(json: String, destinations: Map<String, File>): String {
        val input = try { JSONArray(json) } catch (_: Exception) { return json }
        val output = JSONArray()
        for (i in 0 until input.length()) {
            if (input.isNull(i)) output.put(JSONObject.NULL)
            else output.put(remapMediaPath(input.optString(i, ""), destinations))
        }
        return output.toString()
    }

    private fun remapBlockMedia(content: String, destinations: Map<String, File>): String {
        if (!content.contains(NotesnookBlockManager.BLOCKS_PREFIX)) return content
        val blocks = NotesnookBlockManager.parse(content)
        var changed = false
        blocks.forEach { block ->
            when (block) {
                is NotesnookBlock.Image -> {
                    val remapped = remapMediaPath(block.uri, destinations)
                    if (remapped != block.uri) { block.uri = remapped; changed = true }
                }
                is NotesnookBlock.Attachment -> {
                    val remapped = remapMediaPath(block.uri, destinations)
                    if (remapped != block.uri) { block.uri = remapped; changed = true }
                }
                else -> Unit
            }
        }
        return if (changed) {
            NotesnookBlockManager.serialize(
                blocks,
                isArticle = NotesnookBlockManager.isArticleMode(content),
                subtitle = NotesnookBlockManager.getSubtitle(content)
            )
        } else content
    }

    private fun prepareRestoredMedia(
        stagingDir: File,
        destinations: Map<String, File>
    ): List<Pair<File, File>> {
        val prepared = mutableListOf<Pair<File, File>>()
        val buffer = ByteArray(32 * 1024)
        try {
            MEDIA_FOLDERS.forEach { folder ->
                File(stagingDir, folder).listFiles()?.filter { it.isFile && it.length() > 0L }?.forEach { staged ->
                    val relative = "$folder/${staged.name}"
                    val destination = checkNotNull(destinations[relative]) {
                        "No destination allocated for restored media $relative"
                    }
                    destination.parentFile?.mkdirs()
                    val temp = File(destination.parentFile, ".${destination.name}.${UUID.randomUUID()}.restore")
                    FileInputStream(staged).use { input ->
                        EncryptedMediaStorage.openEncryptedOutputStream(temp).use { output ->
                            var read = input.read(buffer)
                            while (read != -1) {
                                output.write(buffer, 0, read)
                                read = input.read(buffer)
                            }
                        }
                    }
                    prepared += destination to temp
                }
            }
            return prepared
        } catch (e: Exception) {
            prepared.forEach { it.second.delete() }
            throw e
        }
    }

    private fun installPreparedMedia(prepared: List<Pair<File, File>>) {
        val installed = mutableListOf<Pair<File, File?>>()
        try {
            prepared.forEach { (destination, temp) ->
                val backup = if (destination.exists()) {
                    File(destination.parentFile, ".${destination.name}.${UUID.randomUUID()}.old")
                } else null
                if (backup != null && !destination.renameTo(backup)) {
                    throw IOException("Could not stage existing media ${destination.name} for replacement")
                }
                if (!temp.renameTo(destination)) {
                    backup?.renameTo(destination)
                    throw IOException("Could not atomically install restored media ${destination.name}")
                }
                installed += destination to backup
            }
            installed.forEach { it.second?.delete() }
        } catch (e: Exception) {
            installed.asReversed().forEach { (destination, backup) ->
                destination.delete()
                backup?.renameTo(destination)
            }
            throw e
        } finally {
            prepared.forEach { it.second.delete() }
        }
    }

    private fun isKnownFloatPreference(prefName: String, key: String): Boolean {
        if (key == "xp_boost_multiplier" || key == "chat_font_size_sp") return true
        if (key.endsWith("_multiplier") || key.endsWith("_scale") || key.endsWith("_sp") || key.endsWith("_fraction")) {
            return true
        }
        return false
    }
}
