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
        "focus_completed_tasks_history",
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
        "todo_widget_theme_prefs",
        "productivity_library",
        "exam_countdowns"
    )

    internal fun recoveryPreferenceNames(): List<String> = PREF_FILES + ArchiveVaultSecurity.PREFS_NAME + "ayva_composer"

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
            val vaultEnabled = ArchiveVaultSecurity.getVaultStatus(app) == ArchiveVaultSecurity.VaultStatus.ENABLED
            val hasLockedVaultNotes = notes.any { VaultPayloadEncryptor.isVaultEncrypted(it) || vaultEnabled && it.isArchived } && subKey == null
            subKey?.fill(0)
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
    ): Result<BackupMetadata> = createVerifiedEncryptedBackup(context, destinationUri, password, null)

    /** Scheduled jobs use only their saved explicit authorization, never the UI's active vault key. */
    internal suspend fun createAutomaticEncryptedBackup(context: Context, destinationUri: Uri, password: String, authorizedVaultKey: ByteArray?): Result<BackupMetadata> =
        createVerifiedEncryptedBackup(context, destinationUri, password, authorizedVaultKey, allowActiveVaultKey = false, recordExport = false)

    /** A foreground manual session supplies a short-lived key; it is never saved as automatic authorization. */
    internal suspend fun createOneTimeEncryptedBackup(context: Context, destinationUri: Uri, password: String, vaultKey: ByteArray?): Result<BackupMetadata> =
        createVerifiedEncryptedBackup(context, destinationUri, password, vaultKey, allowActiveVaultKey = false, recordExport = false)

    private suspend fun createVerifiedEncryptedBackup(context: Context, destinationUri: Uri, password: String, authorizedVaultKey: ByteArray?, allowActiveVaultKey: Boolean = true, recordExport: Boolean = true): Result<BackupMetadata> =
        AssistantStateLock.mutex.withLock {
            try {
                val result = RestoreJournal.consistent(context) { createEncryptedBackupLocked(context, destinationUri, password, authorizedVaultKey, allowActiveVaultKey) }
                // Encryption authentication alone does not prove the restore reader accepts
                // the manifest and attachments. Never rotate archives on that assumption.
                if (result.isSuccess) {
                    verifyEncryptedBackup(context, destinationUri, password).getOrThrow()
                    if (recordExport) context.getSharedPreferences("ayva_backup_verification", Context.MODE_PRIVATE).edit()
                        .putLong("last_verified_export_ms", System.currentTimeMillis()).apply()
                }
                result
            }
            catch (error: Exception) { if (error is kotlinx.coroutines.CancellationException) throw error; Result.failure(error) }
        }

    private suspend fun createEncryptedBackupLocked(
        context: Context, destinationUri: Uri, password: String, authorizedVaultKey: ByteArray? = null, allowActiveVaultKey: Boolean = true
    ): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        var backupVaultKey: ByteArray? = null
        var manifestBytes: ByteArray? = null
        try {
            if (password.isBlank()) {
                throw IllegalArgumentException("Backup password cannot be empty or blank.")
            }
            val app = context.applicationContext as FocusApplication
            BackupProgress.report("Reading saved data")
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
            backupVaultKey = authorizedVaultKey?.copyOf() ?: if (allowActiveVaultKey) ArchiveVaultSecurity.getActiveVaultSubKey() else null
            val subKey = backupVaultKey
            val vaultEnabled = ArchiveVaultSecurity.getVaultStatus(app) == ArchiveVaultSecurity.VaultStatus.ENABLED
            val hasLockedVaultNotes = notes.any { VaultPayloadEncryptor.isVaultEncrypted(it) || vaultEnabled && it.isArchived } && subKey == null
            if (hasLockedVaultNotes) {
                throw IllegalStateException("Cannot create backup: Secret Archive Vault is locked. Please unlock your secret vault first.")
            }
            val processedNotes = notes.map { rawNote ->
                if (vaultEnabled && rawNote.isArchived || VaultPayloadEncryptor.isVaultEncrypted(rawNote)) {
                    val key = subKey ?: throw IllegalStateException(
                        "Cannot create backup: Secret Archive Vault is locked."
                    )
                    when (val result = VaultPayloadEncryptor.tryDecryptNotePayload(rawNote, key)) {
                        is VaultPayloadEncryptor.DecryptionResult.Success -> result.note
                        is VaultPayloadEncryptor.DecryptionResult.NotEncrypted -> result.note
                        else -> throw SecurityException(
                            "Cannot create a complete backup because private note ${rawNote.id} could not be decrypted ($result)."
                        )
                    }
                } else {
                    rawNote
                }
            }
            BackupProgress.report("Checking attachments")
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
                        put("pausedAt", h.pausedAt ?: JSONObject.NULL)
                        put("pauseHistoryJson", h.pauseHistoryJson)
                        put("frequencyJson", h.frequencyJson)
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
                put("vocabulary", VocabularyArchive.export(vocabDb))

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
                    if (prefName == com.focusbyrj.app.util.CompletedTaskHistoryManager.PREFS_NAME) {
                        prefsObj.put(prefName, com.focusbyrj.app.util.CompletedTaskHistoryManager.portable(app))
                        return@forEach
                    }
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

            val manifestText = rootJson.toString()
            BackupArchiveLimits.checkJsonNesting(manifestText)
            val manifest = manifestText.toByteArray(Charsets.UTF_8)
            manifestBytes = manifest
            BackupArchiveLimits.checkExport(manifest.size.toLong(), mediaManifest.map { it.plaintextSize })
            validateRestoreManifest(rootJson)

            // 3. Stream unencrypted entries directly into encrypted output stream
            withContext(NonCancellable) {
                app.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
                    CryptoBackupEngine.openEncryptingStream(outStream, password.toCharArray()).use { encryptingStream ->
                        ZipOutputStream(BufferedOutputStream(encryptingStream)).use { zipOut ->
                            // Write data.json
                            zipOut.putNextEntry(ZipEntry("data.json"))
                            zipOut.write(manifest)
                            zipOut.closeEntry()

                            // Package media files: stream decrypted bytes from EncryptedMediaStorage
                            // so that media is encrypted by the backup password and portable across devices.
                            val buffer = ByteArray(32 * 1024)
                            var packedMedia = 0
                            MEDIA_FOLDERS.forEach { folderName ->
                                val mediaDir = File(app.filesDir, folderName)
                                if (mediaDir.exists() && mediaDir.isDirectory) {
                                    mediaDir.listFiles()?.forEach { file ->
                                        if (file.isFile && file.length() > 0 && "$folderName/${file.name}" in mediaManifestPaths) {
                                            packedMedia++
                                            BackupProgress.report("Encrypting attachment $packedMedia of ${mediaManifest.size}")
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
                    ZipInputStream(BufferedInputStream(object : java.io.FilterInputStream(decryptedStream) {
                        override fun close() = Unit // Drain/authenticate the cipher after ZIP EOF.
                    })).use { zipIn ->
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
                    drainAuthenticatedPayload(decryptedStream)
                }
            } ?: throw IOException("Could not open destination backup stream for post-creation verification.")

            Log.i(TAG, "Encrypted backup created and verified successfully: ${metadata.noteCount} notes, ${metadata.taskCount} tasks.")
            Result.success(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted backup", e)
            try {
                android.provider.DocumentsContract.deleteDocument(context.contentResolver, destinationUri)
            } catch (_: Exception) {}
            Result.failure(e)
        } finally { backupVaultKey?.fill(0); manifestBytes?.fill(0) }
    }

    /**
     * Inspects or restores an encrypted backup file from [sourceUri] using [password].
     */
    /** Read-only verification, with bounded encrypted staging and no database/preferences mutation. */
    suspend fun verifyEncryptedBackup(context: Context, sourceUri: Uri, password: String): Result<String> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val stagingDir = File(app.cacheDir, "verify_backup_${UUID.randomUUID()}").apply { check(mkdirs()) }
        val mediaDestinations = linkedMapOf<String, File>()
        try {
            require(password.isNotBlank()) { "Enter the backup password" }
            var jsonDataStr: String? = null

            // 1 & 2. Decrypt & Unpack ZIP into staging directory on-the-fly
            var totalDecompressedMediaBytes = 0L
            val maxSingleEntrySize = BackupArchiveLimits.MEDIA_ENTRY_BYTES // streamed; bounded against decompression abuse
            val maxTotalMediaSize = BackupArchiveLimits.TOTAL_MEDIA_BYTES // 500MB total media archive decompression limit

            val inStream = app.contentResolver.openInputStream(sourceUri) ?: throw IOException("Could not read backup")
            inStream.use { rawInStream ->
                CryptoBackupEngine.openDecryptingStream(rawInStream, password.toCharArray()).use { decryptedStream ->
                    ZipInputStream(BufferedInputStream(object : java.io.FilterInputStream(decryptedStream) {
                        override fun close() = Unit // Drain/authenticate the cipher after ZIP EOF.
                    })).use { zipIn ->
                        val buffer = ByteArray(32 * 1024)
                        val entryNames = mutableSetOf<String>()
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            require(entryNames.add(entry.name)) { "Duplicate backup entry: ${entry.name}" }
                            require(entryNames.size <= BackupArchiveLimits.ENTRIES) { "Backup contains too many entries" }
                            require(entry.name == "data.json" || entry.name.startsWith("media/")) {
                                "Unsupported backup entry: ${entry.name}"
                            }
                            if (!entry.isDirectory) {
                                if (entry.name == "data.json") {
                                    val maxManifestSize = BackupArchiveLimits.MANIFEST_BYTES
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
                                    BackupProgress.report("Checking attachment ${mediaDestinations.size + 1}")
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
                                    // Never overwrite live attachments: DB rollback must still reference
                                    // the original bytes if a later store fails or the process dies.
                                    val importedName = "import_${UUID.randomUUID()}_${targetFile.name}"
                                    mediaDestinations[normalized] = File(File(app.filesDir, topFolder), importedName)
                                    var fileBytesWritten = 0L
                                    EncryptedMediaStorage.openEncryptedOutputStream(targetFile).use { fos ->
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
                    drainAuthenticatedPayload(decryptedStream)
                }
            }

            if (jsonDataStr == null) {
                throw IllegalArgumentException("Backup archive does not contain data.json manifest.")
            }

            BackupProgress.report("Checking saved records")
            val rootJson = BackupArchiveLimits.parseManifest(jsonDataStr!!)
            val version = rootJson.optInt("version", BACKUP_VERSION)
            val createdAt = rootJson.optLong("createdAt", System.currentTimeMillis())
            val appVersion = rootJson.optString("appVersion", "1.0.0")

            // Parse and validate the entire manifest before changing vault settings, databases,
            // preferences, or live media. This prevents a late malformed entity from producing
            // a successful partial restore.
            validateRestoreManifest(rootJson)
            validateStagedMedia(rootJson, stagingDir)
            val counts = listOf("notes", "tasks", "habits", "drillSessions", "chatMessages").joinToString(" · ") { "$it: ${rootJson.optJSONArray(it)?.length() ?: 0}" }
            val media = rootJson.optJSONArray("mediaManifest")?.length()
            Result.success("Verified authenticated backup.\n$counts\n" +
                (media?.let { "$it attachments checked against their hashes." } ?: "Legacy archive: no attachment hash inventory is available.") +
                "\nNo saved data changed. This verifies this archive, not compatibility with every future app version.")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        } finally { stagingDir.deleteRecursively() }
    }

    class BackupSourceUnavailableException(cause: Exception) : IOException(
        "The backup file could not be opened. Choose it again or reconnect its folder.", cause
    )

    /** In-memory capability owned by one restore. Its controller clears it on backgrounding. */
    internal class RestoreAuthorization internal constructor(
        private val owner: Context, private var key: ByteArray?,
        internal val credentials: Map<String, *>
    ) : AutoCloseable {
        @Synchronized fun copyKey(context: Context): ByteArray {
            require(owner === context.applicationContext)
            return synchronized(ArchiveVaultSecurity) {
                check(context.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, Context.MODE_PRIVATE).all == credentials) {
                    "Vault settings changed. Confirm your PIN again."
                }
                checkNotNull(key) { "Vault confirmation ended. Start restore again." }.copyOf()
            }
        }
        @Synchronized override fun close() { key?.fill(0); key = null }
    }

    internal fun captureRestoreAuthorization(context: Context): RestoreAuthorization = synchronized(ArchiveVaultSecurity) {
        check(ArchiveVaultSecurity.getVaultStatus(context) == ArchiveVaultSecurity.VaultStatus.ENABLED) { "Vault protection changed. Start restore again." }
        val key = ArchiveVaultSecurity.getActiveVaultSubKey() ?: error("Confirm your vault PIN first.")
        try {
            RestoreAuthorization(context.applicationContext, key,
                HashMap(context.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, Context.MODE_PRIVATE).all))
        } catch (error: Throwable) { key.fill(0); throw error }
        finally { ArchiveVaultSecurity.lockVault() }
    }

    /** Authenticated manifest stays in memory; staged attachments are device-key encrypted. */
    internal class PreparedRestore internal constructor(
        internal val owner: Context,
        internal var manifest: JSONObject?,
        internal val stagingDir: File,
        internal val destinations: Map<String, File>,
        internal val preparedAt: Long = android.os.SystemClock.elapsedRealtime()
    ) : AutoCloseable {
        internal val use = kotlinx.coroutines.sync.Mutex()
        val hasPrivateNotes: Boolean get() = manifest?.optJSONArray("notes")?.let { notes ->
            (0 until notes.length()).any { notes.getJSONObject(it).optBoolean("isArchived", false) }
        } ?: false
        val noteCount: Int get() = manifest?.optJSONArray("notes")?.length() ?: 0
        val taskCount: Int get() = manifest?.optJSONArray("tasks")?.length() ?: 0
        override fun close() { manifest = null; stagingDir.deleteRecursively() }
    }

    internal suspend fun prepareRestore(context: Context, sourceUri: Uri, password: String): Result<PreparedRestore> {
      var completed: PreparedRestore? = null
      try { return withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val stagingDir = File(app.cacheDir, "restore_media_stage_${UUID.randomUUID()}").apply { check(mkdirs()) }
        val mediaDestinations = linkedMapOf<String, File>()
        try {
            require(password.isNotBlank()) { "Enter the backup password or recovery key." }
            val inStream = try {
                BackupProgress.report("Opening backup file")
                app.contentResolver.openInputStream(sourceUri) ?: throw IOException("No backup source stream")
            } catch (error: Exception) { throw BackupSourceUnavailableException(error) }


            var jsonDataStr: String? = null

            // 1 & 2. Decrypt & Unpack ZIP into staging directory on-the-fly
            var totalDecompressedMediaBytes = 0L
            val maxSingleEntrySize = BackupArchiveLimits.MEDIA_ENTRY_BYTES // streamed; bounded against decompression abuse
            val maxTotalMediaSize = BackupArchiveLimits.TOTAL_MEDIA_BYTES // 500MB total media archive decompression limit

            inStream.use { rawInStream ->
                CryptoBackupEngine.openDecryptingStream(rawInStream, password.toCharArray()).use { decryptedStream ->
                    ZipInputStream(BufferedInputStream(object : java.io.FilterInputStream(decryptedStream) {
                        override fun close() = Unit // Drain/authenticate the cipher after ZIP EOF.
                    })).use { zipIn ->
                        val buffer = ByteArray(32 * 1024)
                        val entryNames = mutableSetOf<String>()
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            require(entryNames.add(entry.name)) { "Duplicate backup entry: ${entry.name}" }
                            require(entryNames.size <= BackupArchiveLimits.ENTRIES) { "Backup contains too many entries" }
                            require(entry.name == "data.json" || entry.name.startsWith("media/")) {
                                "Unsupported backup entry: ${entry.name}"
                            }
                            if (!entry.isDirectory) {
                                if (entry.name == "data.json") {
                                    val maxManifestSize = BackupArchiveLimits.MANIFEST_BYTES
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
                                    BackupProgress.report("Checking attachment ${mediaDestinations.size + 1}")
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
                                    // Never overwrite live attachments: DB rollback must still reference
                                    // the original bytes if a later store fails or the process dies.
                                    val importedName = "import_${UUID.randomUUID()}_${targetFile.name}"
                                    mediaDestinations[normalized] = File(File(app.filesDir, topFolder), importedName)
                                    var fileBytesWritten = 0L
                                    EncryptedMediaStorage.openEncryptedOutputStream(targetFile).use { fos ->
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
                    drainAuthenticatedPayload(decryptedStream)
                }
            }

            if (jsonDataStr == null) {
                throw IllegalArgumentException("Backup archive does not contain data.json manifest.")
            }

            BackupProgress.report("Checking saved records")
            val rootJson = BackupArchiveLimits.parseManifest(jsonDataStr!!)
            validateRestoreManifest(rootJson)
            validateStagedMedia(rootJson, stagingDir)
            val result = PreparedRestore(app, rootJson, stagingDir, mediaDestinations)
            completed = result
            return@withContext Result.success(result)
        } catch (error: Exception) {
            stagingDir.deleteRecursively()
            if (error is kotlinx.coroutines.CancellationException) throw error
            Result.failure(error)
        }
      } } catch (error: Throwable) { completed?.close(); throw error }
    }

    suspend fun restoreEncryptedBackup(
        context: Context, sourceUri: Uri, password: String,
        cleanRestore: Boolean = false, vaultPin: String? = null
    ): Result<BackupMetadata> {
        val prepared = prepareRestore(context, sourceUri, password).getOrElse { return Result.failure(it) }
        return try { applyPreparedRestore(context, prepared, cleanRestore, vaultPin) } finally { prepared.close() }
    }

    internal suspend fun applyPreparedRestore(context: Context, prepared: PreparedRestore,
        cleanRestore: Boolean = true, vaultPin: String? = null, authorization: RestoreAuthorization? = null): Result<BackupMetadata> = withContext(Dispatchers.IO) {
      var vaultKey: ByteArray? = null
      var ownedAuthorization: RestoreAuthorization? = null
      try { prepared.use.withLock {
        AssistantStateLock.mutex.withLock locked@ {
            require(prepared.owner === context.applicationContext) { "This restore belongs to another app session." }
            require(android.os.SystemClock.elapsedRealtime() - prepared.preparedAt in 0..600_000) { "Restore preparation expired. Choose the backup again." }
            checkNotNull(prepared.manifest) { "This restore session has ended." }
            val enabled = ArchiveVaultSecurity.getVaultStatus(context) == ArchiveVaultSecurity.VaultStatus.ENABLED
            if (authorization == null && enabled) {
                if (ArchiveVaultSecurity.isVaultLocked(context)) {
                    if (vaultPin == null) return@locked Result.failure(RequiresVaultUnlockException())
                    if (ArchiveVaultSecurity.verifyPasscode(context, vaultPin) !is ArchiveVaultSecurity.VerifyResult.Success)
                        return@locked Result.failure(SecurityException("Vault PIN was not accepted. Your data was not changed."))
                }
                ownedAuthorization = captureRestoreAuthorization(context)
            }
            val capability = authorization ?: ownedAuthorization
            if (capability != null) {
                check(enabled) { "Vault protection changed. Start restore again." }
                vaultKey = capability.copyKey(context)
            }
            if (prepared.hasPrivateNotes && !enabled && vaultPin == null)
                return@locked Result.failure(RequiresVaultSetupException("Create a vault PIN to protect the restored private notes."))
            val vaultBefore = capability?.credentials ?: synchronized(ArchiveVaultSecurity) {
                check(ArchiveVaultSecurity.getVaultStatus(context) != ArchiveVaultSecurity.VaultStatus.ENABLED) { "Vault protection changed. Start restore again." }
                HashMap(context.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, Context.MODE_PRIVATE).all)
            }
            // One-use authorization: the vault UI remains locked throughout the write.
            ArchiveVaultSecurity.lockVault()
            BackupProgress.report("Saving recovery point")
            RestoreJournal.mutate(context) {
                if (context.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, Context.MODE_PRIVATE).all != vaultBefore)
                    Result.failure(SecurityException("Vault settings changed. Start restore again; your data was not changed."))
                else restorePreparedLocked(context, prepared, cleanRestore, vaultPin, vaultKey)
            }
        }
      } } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Result.failure(error)
      } finally { vaultKey?.fill(0); ownedAuthorization?.close(); ArchiveVaultSecurity.lockVault() }
    }

    class RequiresVaultUnlockException : IllegalStateException("Your vault is locked. Enter its PIN here to restore; keep protection on.")

    private suspend fun restorePreparedLocked(context: Context, prepared: PreparedRestore,
        cleanRestore: Boolean, vaultPin: String?, authorizedVaultKey: ByteArray?): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        val app = context.applicationContext as FocusApplication
        val stagingDir = prepared.stagingDir
        val mediaDestinations = prepared.destinations
        var preparedMedia: List<Pair<File, File>> = emptyList()
        val restoredHabitIds = mutableMapOf<Long, Long>()
        var restoreVaultKey = authorizedVaultKey?.copyOf()
        try {
            val focusDb = app.database
            val noteDb = NoteDatabase.getInstance(app)
            val drillDb = DrillDatabase.getDatabase(app)
            val vocabDb = app.vocabDatabase
            val chatDb = AyvaChatDatabase.getDatabase(app)
            val rootJson = checkNotNull(prepared.manifest)
            val version = rootJson.optInt("version", BACKUP_VERSION)
            val createdAt = rootJson.optLong("createdAt", System.currentTimeMillis())
            val appVersion = rootJson.optString("appVersion", "1.0.0")
            val assistantPlanning = rootJson.optJSONObject("assistantPlanning")
            AssistantPlanningArchive.preflight(app, assistantPlanning, cleanRestore)
            BackupProgress.report("Saving note safety snapshot")
            checkNotNull(DataSafetyManager.writePreOpSnapshot(app, noteDb.noteDao(), "pre_encrypted_restore", focusDb)) {
                "Could not create the required pre-restore safety snapshot. No data was changed."
            }

            // 4. Validate Secret Vault State and Build NoteEntities
            BackupProgress.report("Restoring notes")
            val notesArray = rootJson.optJSONArray("notes") ?: JSONArray()
            val hasArchivedNotes = (0 until notesArray.length()).any {
                notesArray.getJSONObject(it).optBoolean("isArchived", false)
            }
            if (hasArchivedNotes) {
                val vaultStatus = ArchiveVaultSecurity.getVaultStatus(app)
                if (vaultStatus == ArchiveVaultSecurity.VaultStatus.ENABLED) {
                    checkNotNull(restoreVaultKey) { "Vault authorization changed. Start restore again." }
                } else {
                    // Vault is NOT configured or disabled on this device!
                    if (vaultPin != null && vaultPin.length == 6) {
                        val setSuccess = ArchiveVaultSecurity.setPasscode(app, vaultPin)
                        if (!setSuccess) {
                            throw IllegalStateException("Failed to configure Secret Archive Vault with provided PIN.")
                        }
                        restoreVaultKey = ArchiveVaultSecurity.getActiveVaultSubKey()
                        ArchiveVaultSecurity.lockVault()
                    } else {
                        throw RequiresVaultSetupException("This backup contains private Secret Archive Vault notes. Please set a 6-digit Vault PIN to encrypt and restore them securely on this device.")
                    }
                }
            }

            val noteEntities = mutableListOf<NoteEntity>()
            val subKey = restoreVaultKey
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
            if (cleanRestore) {
                noteDb.withTransaction {
                    // Include IDs assigned to legacy records that did not carry an ID.
                    val restoredIds = noteEntities.sortedBy { it.id == 0L }.map { noteDb.noteDao().insertNote(it) }
                    if (restoredIds.isNotEmpty()) {
                        noteDb.noteDao().deleteNotesNotIn(restoredIds)
                    } else {
                        noteDb.noteDao().deleteAllNotes()
                    }
                }
            } else {
                noteEntities.forEach { note -> noteDb.noteDao().insertNote(note) }
            }

            RestoreJournal.checkpoint("notes")
            try {
                NoteWidgetProvider.updateAllWidgets(app)
            } catch (_: Exception) {}

            // 5. Restore Restrictions, Schedules, Tasks, and Habits atomically
            BackupProgress.report("Restoring tasks and habits")
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
                val idMapping = restoredHabitIds
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
                        isArchived = obj.optBoolean("isArchived", false),
                        pausedAt = if (obj.isNull("pausedAt")) null else obj.getLong("pausedAt"),
                        pauseHistoryJson = obj.optString("pauseHistoryJson", "[]"),
                        frequencyJson = obj.optString("frequencyJson", "").also { com.focusbyrj.app.data.HabitFrequency.read(it) }
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

            RestoreJournal.checkpoint("focus")
            // 9. Restore Drill Sessions
            BackupProgress.report("Restoring learning history")
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
            if (rootJson.has("vocabulary")) {
                VocabularyArchive.restore(vocabDb, rootJson.getJSONObject("vocabulary"), cleanRestore)
            } else if (idiomsArray.length() > 0 || owsArray.length() > 0) {
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
            BackupProgress.report("Restoring settings")
            val prefTypesObj = rootJson.optJSONObject("preferenceTypes")
            // Completed history was absent from earlier current-format archives.
            // A clean restore must not retain unrelated local history; a merge may.
            if (cleanRestore && prefsObj?.has(com.focusbyrj.app.util.CompletedTaskHistoryManager.PREFS_NAME) != true) {
                check(app.getSharedPreferences(com.focusbyrj.app.util.CompletedTaskHistoryManager.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().clear().commit()) { "Completed task history could not be cleared for restore" }
            }
            if (prefsObj != null) {
                val keys = prefsObj.keys()
                while (keys.hasNext()) {
                    val prefName = keys.next()
                    if (prefName !in PREF_FILES) {
                        Log.w(TAG, "Skipping disallowed preference file from archive: $prefName")
                        continue
                    }
                    val pObj = prefsObj.getJSONObject(prefName)
                    if (prefName == com.focusbyrj.app.util.CompletedTaskHistoryManager.PREFS_NAME) {
                        com.focusbyrj.app.util.CompletedTaskHistoryManager.restorePortable(app, pObj, cleanRestore, taskIdMapping)
                        continue
                    }
                    if (prefName == "exam_countdowns") {
                        val sp = app.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                        val edit = sp.edit(); if (cleanRestore) edit.clear()
                        pObj.keys().forEach { key ->
                            val raw = pObj.getString(key); com.focusbyrj.app.widget.ExamCountdown.read(raw)
                            edit.putString(if (!cleanRestore && sp.contains(key)) UUID.randomUUID().toString() else key, raw)
                        }
                        check(edit.commit()); continue
                    }
                    if (prefName == "productivity_library") {
                        val sp = app.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                        val edit = sp.edit(); if (cleanRestore) edit.clear()
                        pObj.keys().forEach { key ->
                            val raw = com.focusbyrj.app.data.ProductivityLibrary.remapChain(pObj.getString(key), restoredHabitIds, taskIdMapping)
                            if (raw != null) {
                                val kind = JSONObject(raw).getString("kind")
                                val target = if (!cleanRestore && sp.contains(key)) "${kind}_${UUID.randomUUID()}" else key
                                edit.putString(target, raw)
                            }
                        }
                        check(edit.commit()); continue
                    }

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

            RestoreJournal.checkpoint("preferences")
            // 13. Commit Staged Media Files to Internal Storage (re-encrypting with this device's hardware KeyStore key)
            installPreparedMedia(preparedMedia)
            RestoreJournal.checkpoint("media")
            AssistantPlanningArchive.restore(app, assistantPlanning, taskIdMapping, cleanRestore)
            com.focusbyrj.app.util.AyvaTalkEngine.resetConversation()

            // Reload In-Memory Managers
            try { FocusEconomyManager.init(app) } catch (_: Exception) {}
            try { FocusStatsManager.init(app) } catch (_: Exception) {}
            try { BubbleChatManager.reloadAfterRestore(app) } catch (_: Exception) {}
            try { AptitudeManager.init(app) } catch (_: Exception) {}
            try { DailyQuestManager.init(app) } catch (_: Exception) {}

            com.focusbyrj.app.util.ReminderPrivacy.refreshActive(app)

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

            RestoreJournal.checkpoint("notes")
            try {
                NoteWidgetProvider.updateAllWidgets(app)
            } catch (_: Exception) {}
            try {
                TodoWidgetProvider.updateAllWidgets(app)
                com.focusbyrj.app.widget.ExamCountdownProvider.updateAll(app)
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
            val friendlyException = when {
                e is RequiresVaultSetupException -> e
                e is SecurityException -> e
                e is java.util.zip.ZipException -> SecurityException("Failed to restore backup: Incorrect password or corrupted backup file.", e)
                e is IllegalArgumentException && e.message?.contains("data.json") == true -> SecurityException("Failed to restore backup: Incorrect password or corrupted backup file.", e)
                else -> e
            }
            Result.failure(friendlyException)
        } finally {
            restoreVaultKey?.fill(0)
            preparedMedia.forEach { it.second.delete() }
            try {
                prepared.close()
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

    private fun drainAuthenticatedPayload(input: java.io.InputStream) {
        val buffer = ByteArray(32 * 1024)
        while (input.read(buffer) != -1) { /* EOF forces GCM tag verification. */ }
    }

    private fun validateRestoreManifest(root: JSONObject) {
        if (root.has("vocabulary")) VocabularyArchive.validate(root.getJSONObject("vocabulary"))
        require(root.optInt("version", 1) in 1..BACKUP_VERSION) { "Unsupported backup manifest version" }
        val arrays = listOf("notes", "restrictions", "schedules", "tasks", "habits", "habitLogs",
            "drillSessions", "learnedIdioms", "learnedOws", "chatMessages")
        arrays.forEach { name ->
            require(!root.has(name) || root.optJSONArray(name) != null) { "$name must be an array" }
            root.optJSONArray(name)?.let { array ->
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    item.keys().forEach { field ->
                        if (field.endsWith("Json") && item.opt(field) is String) BackupArchiveLimits.checkEmbeddedJson(item.getString(field))
                    }
                    if (name == "notes") BackupArchiveLimits.checkNoteContent(item.optString("content", ""))
                }
            }
        }
        listOf("notes", "habits", "schedules").forEach { name ->
            val array = root.optJSONArray(name) ?: return@forEach
            val ids = mutableSetOf<Long>()
            for (i in 0 until array.length()) {
                val id = array.getJSONObject(i).optLong("id", 0L)
                require(id <= 0L || ids.add(id)) { "Duplicate $name ID: $id" }
            }
        }
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
            Task(id = obj.optLong("id", 0L), title = obj.getString("title"), dueDate = if (obj.isNull("dueDate")) null else obj.getLong("dueDate"), planningJson = obj.optString("planningJson", ""))
        } }.orEmpty()
        com.focusbyrj.app.data.TaskPlanning.validateGraph(planningTasks)
        planningTasks.forEach { require(it.planning?.availableFrom == null || it.dueDate == null || it.planning!!.availableFrom!! <= it.dueDate) { "Start showing is after the deadline" } }
        val taskIds = root.optJSONArray("tasks")?.let { array ->
            (0 until array.length()).mapNotNull { array.getJSONObject(it).optLong("id", 0L).takeIf { id -> id > 0 } }
        }.orEmpty()
        require(taskIds.distinct().size == taskIds.size) { "Backup contains duplicate task IDs" }
        if (root.has("assistantPlanning")) {
            com.focusbyrj.app.util.assistant.validateDocument(root.getJSONObject("assistantPlanning"))
        }
        each("habits") { it.getString("title"); com.focusbyrj.app.data.HabitFrequency.read(it.optString("frequencyJson", "")) }
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
        prefs?.optJSONObject("exam_countdowns")?.let { events -> events.keys().forEach {
            val raw = events.getString(it); BackupArchiveLimits.checkEmbeddedJson(raw); com.focusbyrj.app.widget.ExamCountdown.read(raw)
        } }
        prefs?.optJSONObject("productivity_library")?.let { library -> library.keys().forEach { key ->
            val j = BackupArchiveLimits.parseManifest(library.getString(key)); com.focusbyrj.app.data.ProductivityLibrary.validate(j)
            require(key.startsWith(j.getString("kind") + "_"))
        } }
        prefs?.keys()?.forEach { name ->
            require(prefs.optJSONObject(name) != null) { "Preference file '$name' must be an object" }
        }
    }

    private fun validateStagedMedia(root: JSONObject, stagingDir: File) {
        val manifest = root.optJSONArray("mediaManifest")
        if (manifest == null) {
            require(root.optInt("version", 1) < 2) { "Backup v2 is missing its media inventory" }
            return // legacy backup
        }
        val expectedPaths = mutableSetOf<String>()
        for (i in 0 until manifest.length()) {
            BackupProgress.report("Verifying attachment ${i + 1} of ${manifest.length()}")
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
            (EncryptedMediaStorage.openDecryptedInputStream(file)
                ?: throw IOException("Staged attachment cannot be decrypted")).use { input ->
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
                BackupProgress.report("Checking attachment ${result.size + 1} of ${uniqueReferencedFiles.size}")
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
        BackupArchiveLimits.checkEmbeddedJson(note.imageUrisJson)
        BackupArchiveLimits.checkEmbeddedJson(note.audioUrisJson)
        BackupArchiveLimits.checkNoteContent(note.content)
        return com.focusbyrj.app.data.note.NoteMediaInventory.paths(note).toList()
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

    private fun remapBlockMedia(content: String, destinations: Map<String, File>): String =
        com.focusbyrj.app.data.note.NoteMediaInventory.remapContent(content) { remapMediaPath(it, destinations) }

    private fun prepareRestoredMedia(
        stagingDir: File,
        destinations: Map<String, File>
    ): List<Pair<File, File>> {
        val prepared = mutableListOf<Pair<File, File>>()
        try {
            MEDIA_FOLDERS.forEach { folder ->
                File(stagingDir, folder).listFiles()?.filter { it.isFile }?.forEach { staged ->
                    val relative = "$folder/${staged.name}"
                    val destination = checkNotNull(destinations[relative]) {
                        "No destination allocated for restored media $relative"
                    }
                    destination.parentFile?.mkdirs()
                    val temp = File(destination.parentFile, ".${destination.name}.${UUID.randomUUID()}.restore")
                    prepared += destination to temp
                    BackupProgress.report("Restoring attachment ${prepared.size} of ${destinations.size}")
                    // This authenticates the complete GCM tag before any database change.
                    (EncryptedMediaStorage.openDecryptedInputStream(staged)
                        ?: throw IOException("Staged attachment cannot be decrypted")).close()
                    if (!staged.renameTo(temp)) {
                        val expected = staged.inputStream().use { digestBackup(it) }
                        staged.inputStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
                        check(temp.inputStream().use { digestBackup(it) } == expected) { "Staged media copy did not verify" }
                    }
                    java.io.RandomAccessFile(temp, "rw").use { it.fd.sync() }
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
