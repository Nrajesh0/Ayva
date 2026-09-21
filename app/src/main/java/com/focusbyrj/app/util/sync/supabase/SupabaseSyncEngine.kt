/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.focusbyrj.app.util.sync.supabase

import android.content.Context
import android.util.Log
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskDao
import com.focusbyrj.app.data.TaskType
import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.data.note.NoteDao
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.sync.VaultCryptoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

/**
 * Robust Zero-Knowledge Cloud Synchronization Engine using Supabase.
 *
 * Zero-Knowledge Rules:
 * 1. Data is encrypted using AES-256-GCM BEFORE being sent to the cloud.
 * 2. The cloud ONLY sees encrypted ciphertext, initialization vectors, and salt.
 * 3. Synchronization uses deterministic name-based UUIDs for local items, ensuring
 *    perfect cloud-to-local translation without requiring SQLite schema alterations.
 */
object SupabaseSyncEngine {

    private const val TAG = "SupabaseSyncEngine"
    private const val DELETIONS_PREFS = "focus_supabase_deletions"
    private const val TASK_TIMESTAMPS_PREFS = "focus_task_sync_timestamps"
    private const val SYNC_MAP_PREFS = "focus_supabase_sync_id_mapping"

    @Volatile
    var isSyncInProgress: Boolean = false
        private set

    data class SyncResult(
        val success: Boolean,
        val uploadedCount: Int,
        val downloadedCount: Int,
        val message: String
    )

    /**
     * Binds a local SQLite ID to a remote cloud UUID in both directions.
     */
    fun bindSyncId(context: Context, userId: String, type: String, localId: Long, cloudSyncId: String) {
        if (userId.isBlank() || cloudSyncId.isBlank() || localId <= 0L) return
        val prefs = context.getSharedPreferences(SYNC_MAP_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("$userId:$type:$localId", cloudSyncId)
            .putLong("$userId:$type:rev:$cloudSyncId", localId)
            .commit()
    }

    /**
     * Retrieves the mapped local SQLite ID for a given cloud UUID.
     */
    fun getLocalIdForSyncId(context: Context, userId: String, type: String, cloudSyncId: String): Long? {
        if (userId.isBlank() || cloudSyncId.isBlank()) return null
        val prefs = context.getSharedPreferences(SYNC_MAP_PREFS, Context.MODE_PRIVATE)
        val localId = prefs.getLong("$userId:$type:rev:$cloudSyncId", -1L)
        return if (localId > 0L) localId else null
    }

    /**
     * Generates or retrieves a stable non-deterministic UUIDv4 sync ID mapped to the local item.
     * Prevents metadata leakage and preserves multi-device pairing.
     */
    fun getOrCreateSyncId(context: Context, userId: String, type: String, localId: Long): String {
        val prefs = context.getSharedPreferences(SYNC_MAP_PREFS, Context.MODE_PRIVATE)
        val key = "$userId:$type:$localId"
        val existing = prefs.getString(key, null)
        if (!existing.isNullOrBlank()) return existing

        val randomId = UUID.randomUUID().toString()
        prefs.edit()
            .putString(key, randomId)
            .putLong("$userId:$type:rev:$randomId", localId)
            .commit()
        return randomId
    }

    private fun isMatchingItem(context: Context, cloudId: String, userId: String, type: String, localId: Long): Boolean {
        val mappedLocalId = getLocalIdForSyncId(context, userId, type, cloudId)
        if (mappedLocalId != null && mappedLocalId == localId) return true

        val syncId = getOrCreateSyncId(context, userId, type, localId)
        if (cloudId == syncId) return true

        // Backward compatibility fallback for legacy deterministic IDs
        val legacyScoped = if (userId.isNotBlank()) UUID.nameUUIDFromBytes("$userId:$type:$localId".toByteArray(Charsets.UTF_8)).toString() else ""
        if (legacyScoped.isNotBlank() && cloudId == legacyScoped) return true
        return cloudId == UUID.nameUUIDFromBytes("$type:$localId".toByteArray(Charsets.UTF_8)).toString()
    }

    /**
     * Determines or updates a stable timestamp for a task to prevent redundant re-uploads.
     */
    @androidx.annotation.VisibleForTesting
    internal fun getOrUpdateTaskTimestamp(
        context: Context,
        taskId: Long,
        fingerprint: String,
        explicitCompletedAt: Long?
    ): Long {
        val prefs = context.getSharedPreferences(TASK_TIMESTAMPS_PREFS, Context.MODE_PRIVATE)
        val storedFingerprint = prefs.getString("fp_$taskId", null)
        val storedTimestamp = prefs.getLong("ts_$taskId", 0L)
        val now = System.currentTimeMillis()

        return if (storedFingerprint == fingerprint && storedTimestamp > 0L) {
            storedTimestamp
        } else {
            val tsToSave = if (storedTimestamp == 0L && explicitCompletedAt != null && explicitCompletedAt > 0L) {
                explicitCompletedAt
            } else {
                now
            }
            prefs.edit()
                .putString("fp_$taskId", fingerprint)
                .putLong("ts_$taskId", tsToSave)
                .commit()
            tsToSave
        }
    }

    private fun recordTaskTimestamp(context: Context, taskId: Long, fingerprint: String, timestamp: Long) {
        val prefs = context.getSharedPreferences(TASK_TIMESTAMPS_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("fp_$taskId", fingerprint)
            .putLong("ts_$taskId", timestamp)
            .commit()
    }

    /**
     * Records a local deletion event so that it can be synced as a tombstone to the cloud.
     */
    fun recordLocalDeletion(context: Context, type: String, localId: Long) {
        val session = SupabaseKeyManager.getSessionState(context)
        val userId = session.userId ?: ""
        val syncId = getOrCreateSyncId(context, userId, type, localId)
        val prefs = context.getSharedPreferences(DELETIONS_PREFS, Context.MODE_PRIVATE)
        val currentDeletions = prefs.getStringSet("pending_deletions", mutableSetOf()) ?: mutableSetOf()
        val newDeletions = currentDeletions.toMutableSet()
        newDeletions.add("$type:$syncId")
        prefs.edit().putStringSet("pending_deletions", newDeletions).commit()
    }

    /**
     * Executes a complete two-way cloud sync cycle.
     */
    suspend fun performSync(
        context: Context,
        noteDao: NoteDao,
        taskDao: TaskDao
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        if (isSyncInProgress) {
            Log.d(TAG, "Sync already in progress, skipping duplicate trigger.")
            return@withContext Result.failure(Exception("Sync already in progress"))
        }

        val session = SupabaseKeyManager.getSessionState(context)
        if (!session.isSignedIn) {
            return@withContext Result.failure(Exception("User is not signed in to Cloud Vault."))
        }

        val userId = session.userId ?: ""
        val token = SupabaseAuthManager.getValidAccessToken(context) ?: session.accessToken
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(Exception("Session expired or invalid. Please sign in again."))
        }

        val dataKey = SupabaseKeyManager.getDataEncryptionKey(context)
            ?: return@withContext Result.failure(Exception("Cryptographic key not initialized. Please sign in again."))

        isSyncInProgress = true
        try {
            Log.d(TAG, "Starting Zero-Knowledge Cloud Sync...")

            // 1. Fetch Cloud Vault items
            val fetchResult = fetchCloudItems(context, token)
            if (fetchResult.isFailure) {
                val err = fetchResult.exceptionOrNull()
                val errMsg = err?.message ?: "Unknown error"
                Log.e(TAG, "Cloud sync aborted due to fetch failure: $errMsg")
                return@withContext Result.failure(
                    Exception(
                        if (errMsg.contains("403") || errMsg.contains("401") || errMsg.contains("PGRST205") || errMsg.contains("404")) {
                            "Supabase connection error: $errMsg. Please ensure the SQL setup script has been executed in your Supabase SQL Editor."
                        } else {
                            "Sync failed: $errMsg"
                        }
                    )
                )
            }

            val fetchSuccess = fetchResult.getOrThrow()
            val cloudItems = fetchSuccess.items
            val activeEndpoint = fetchSuccess.activeEndpoint
            val activeTypeColumn = fetchSuccess.typeColumn
            val cloudItemMap = cloudItems.associateBy { it.id }

            var uploaded = 0
            var downloaded = 0

            // 2. Process Cloud-to-Local (PULL SYNC)
            cloudItems.forEach { cloudItem ->
                if (cloudItem.isDeleted) {
                    // Process Cloud deletion
                    if (cloudItem.type == "NOTE") {
                        val localId = getLocalIdForSyncId(context, userId, "NOTE", cloudItem.id)
                        val match = if (localId != null) {
                            noteDao.getNoteByIdSync(localId)
                        } else {
                            noteDao.getAllNotesList().find { isMatchingItem(context, cloudItem.id, userId, "NOTE", it.id) }
                        }
                        if (match != null) {
                            com.focusbyrj.app.data.note.NoteMediaManager.deleteNoteMediaFiles(match)
                            noteDao.deleteNote(match)
                            downloaded++
                        }
                    } else if (cloudItem.type == "TASK") {
                        val localId = getLocalIdForSyncId(context, userId, "TASK", cloudItem.id)
                        val match = if (localId != null) {
                            taskDao.getTaskById(localId)
                        } else {
                            taskDao.getAllTasksList().find { isMatchingItem(context, cloudItem.id, userId, "TASK", it.id) }
                        }
                        if (match != null) {
                            taskDao.deleteTask(match)
                            downloaded++
                        }
                    }
                } else {
                    // Decrypt and merge active item
                    try {
                        val decJson = decryptPayload(cloudItem, dataKey)
                        if (decJson != null) {
                            val root = JSONObject(decJson)
                            if (cloudItem.type == "NOTE") {
                                val localId = getLocalIdForSyncId(context, userId, "NOTE", cloudItem.id)
                                val match = if (localId != null) {
                                    noteDao.getNoteByIdSync(localId)
                                } else {
                                    noteDao.getAllNotesList().find { isMatchingItem(context, cloudItem.id, userId, "NOTE", it.id) }
                                }

                                val cloudUpdatedAt = cloudItem.updatedAt
                                val localUpdatedAt = match?.updatedAt ?: 0L

                                if (match == null || cloudUpdatedAt > localUpdatedAt) {
                                    // Hydrate and download any cloud-synced images
                                    val cloudImageUris = try {
                                        val arr = JSONArray(root.optString("imageUrisJson", "[]"))
                                        val list = mutableListOf<String>()
                                        for (i in 0 until arr.length()) list.add(arr.getString(i))
                                        list
                                    } catch (_: Exception) { emptyList() }

                                    val localImagePaths = mutableListOf<String>()
                                    for (item in cloudImageUris) {
                                        if (item.contains("/")) {
                                            val downloadedMedia = SupabaseStorageEngine.downloadMedia(
                                                context = context,
                                                cloudPath = item,
                                                subDirName = "keep_images",
                                                accessToken = token,
                                                dataKey = dataKey
                                            )
                                            if (downloadedMedia != null) {
                                                localImagePaths.add(downloadedMedia)
                                            }
                                        } else {
                                            localImagePaths.add(item)
                                        }
                                    }

                                    // Hydrate and download any cloud-synced audio memos
                                    val cloudAudioUris = try {
                                        val arr = JSONArray(root.optString("audioUrisJson", "[]"))
                                        val list = mutableListOf<String>()
                                        for (i in 0 until arr.length()) list.add(arr.getString(i))
                                        list
                                    } catch (_: Exception) { emptyList() }

                                    val localAudioPaths = mutableListOf<String>()
                                    for (item in cloudAudioUris) {
                                        if (item.contains("/")) {
                                            val downloadedMedia = SupabaseStorageEngine.downloadMedia(
                                                context = context,
                                                cloudPath = item,
                                                subDirName = "keep_audio",
                                                accessToken = token,
                                                dataKey = dataKey
                                            )
                                            if (downloadedMedia != null) {
                                                localAudioPaths.add(downloadedMedia)
                                            }
                                        } else {
                                            localAudioPaths.add(item)
                                        }
                                    }

                                    val note = NoteEntity(
                                        id = match?.id ?: 0L,
                                        title = root.optString("title", ""),
                                        content = root.optString("content", ""),
                                        isChecklist = root.optBoolean("isChecklist", false),
                                        checklistJson = root.optString("checklistJson", "[]"),
                                        colorKey = root.optString("colorKey", "default"),
                                        fontKey = root.optString("fontKey", "default"),
                                        isPinned = root.optBoolean("isPinned", false),
                                        isArchived = root.optBoolean("isArchived", false),
                                        isTrashed = root.optBoolean("isTrashed", false),
                                        labelsJson = root.optString("labelsJson", "[]"),
                                        imageUrisJson = JSONArray(localImagePaths).toString(),
                                        audioUrisJson = JSONArray(localAudioPaths).toString(),
                                        createdAt = root.optLong("createdAt", cloudUpdatedAt),
                                        updatedAt = cloudUpdatedAt
                                    )
                                    val newInsertedId = noteDao.insertNote(note)
                                    val finalId = if (match != null) match.id else newInsertedId
                                    bindSyncId(context, userId, "NOTE", finalId, cloudItem.id)
                                    downloaded++
                                } else {
                                    bindSyncId(context, userId, "NOTE", match.id, cloudItem.id)
                                }
                            } else if (cloudItem.type == "TASK") {
                                val localId = getLocalIdForSyncId(context, userId, "TASK", cloudItem.id)
                                val match = if (localId != null) {
                                    taskDao.getTaskById(localId)
                                } else {
                                    taskDao.getAllTasksList().find { isMatchingItem(context, cloudItem.id, userId, "TASK", it.id) }
                                }

                                val cloudUpdatedAt = cloudItem.updatedAt
                                val matchFingerprint = match?.let { "${it.title}|${it.details}|${it.dueDate}|${it.isCompleted}|${it.type}|${it.recurrence}|${it.isPersistent}|${it.isPriority}" } ?: ""
                                val localUpdatedAt = if (match != null) getOrUpdateTaskTimestamp(context, match.id, matchFingerprint, match.completedAt) else 0L

                                if (match == null || cloudUpdatedAt > localUpdatedAt) {
                                    val typeName = root.optString("type", TaskType.TASK.name)
                                    val recName = root.optString("recurrence", RecurrencePattern.NONE.name)

                                    val task = Task(
                                        id = match?.id ?: 0L,
                                        title = root.optString("title", ""),
                                        details = root.optString("details", ""),
                                        dueDate = if (root.isNull("dueDate")) null else root.optLong("dueDate"),
                                        isCompleted = root.optBoolean("isCompleted", false),
                                        type = try { TaskType.valueOf(typeName) } catch (_: Exception) { TaskType.TASK },
                                        recurrence = try { RecurrencePattern.valueOf(recName) } catch (_: Exception) { RecurrencePattern.NONE },
                                        isPersistent = root.optBoolean("isPersistent", false),
                                        isPriority = root.optBoolean("isPriority", false),
                                        completedAt = if (root.isNull("completedAt")) null else root.optLong("completedAt")
                                    )
                                    val newTaskId = taskDao.insertTask(task)
                                    val finalTaskId = if (match != null) match.id else newTaskId
                                    bindSyncId(context, userId, "TASK", finalTaskId, cloudItem.id)
                                    val newFingerprint = "${task.title}|${task.details}|${task.dueDate}|${task.isCompleted}|${task.type}|${task.recurrence}|${task.isPersistent}|${task.isPriority}"
                                    recordTaskTimestamp(context, finalTaskId, newFingerprint, cloudUpdatedAt)
                                    downloaded++
                                } else {
                                    bindSyncId(context, userId, "TASK", match.id, cloudItem.id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed decrypting cloud item ${cloudItem.id}", e)
                    }
                }
            }

            // 3. Process Local-to-Cloud (PUSH SYNC)
            val currentToken = SupabaseAuthManager.getValidAccessToken(context) ?: token
            if (userId.isBlank()) {
                return@withContext Result.failure(Exception("User ID missing from active session"))
            }

            val localNotes = noteDao.getAllNotesList()
            localNotes.forEach { note ->
                val syncId = getOrCreateSyncId(context, userId, "NOTE", note.id)
                val legacySyncId = if (userId.isNotBlank()) UUID.nameUUIDFromBytes("$userId:NOTE:${note.id}".toByteArray(Charsets.UTF_8)).toString() else ""
                val legacyUnscoped = UUID.nameUUIDFromBytes("NOTE:${note.id}".toByteArray(Charsets.UTF_8)).toString()
                val cloudMatch = cloudItemMap[syncId] ?: (if (legacySyncId.isNotBlank()) cloudItemMap[legacySyncId] else null) ?: cloudItemMap[legacyUnscoped]

                if (cloudMatch == null || note.updatedAt > cloudMatch.updatedAt) {
                    // Upload any local image attachments to Supabase Storage
                    val cloudImagePaths = mutableListOf<String>()
                    note.getImageUris().forEach { path ->
                        val file = java.io.File(path)
                        if (file.exists() && file.length() > 0L) {
                            val uploadedPath = SupabaseStorageEngine.uploadMedia(
                                context = context,
                                localPath = path,
                                userId = userId,
                                accessToken = currentToken,
                                dataKey = dataKey
                            )
                            if (uploadedPath != null) {
                                cloudImagePaths.add(uploadedPath)
                            } else if (path.startsWith("media/")) {
                                cloudImagePaths.add(path)
                            }
                        } else if (path.startsWith("media/")) {
                            cloudImagePaths.add(path)
                        }
                    }

                    // Upload any local audio attachments to Supabase Storage
                    val cloudAudioPaths = mutableListOf<String>()
                    note.getAudioUris().forEach { path ->
                        val file = java.io.File(path)
                        if (file.exists() && file.length() > 0L) {
                            val uploadedPath = SupabaseStorageEngine.uploadMedia(
                                context = context,
                                localPath = path,
                                userId = userId,
                                accessToken = currentToken,
                                dataKey = dataKey
                            )
                            if (uploadedPath != null) {
                                cloudAudioPaths.add(uploadedPath)
                            } else if (path.startsWith("media/")) {
                                cloudAudioPaths.add(path)
                            }
                        } else if (path.startsWith("media/")) {
                            cloudAudioPaths.add(path)
                        }
                    }

                    val payload = JSONObject().apply {
                        put("title", note.title)
                        put("content", note.content)
                        put("isChecklist", note.isChecklist)
                        put("checklistJson", note.checklistJson)
                        put("colorKey", note.colorKey)
                        put("fontKey", note.fontKey)
                        put("isPinned", note.isPinned)
                        put("isArchived", note.isArchived)
                        put("isTrashed", note.isTrashed)
                        put("labelsJson", note.labelsJson)
                        put("imageUrisJson", JSONArray(cloudImagePaths).toString())
                        put("audioUrisJson", JSONArray(cloudAudioPaths).toString())
                        put("createdAt", note.createdAt)
                    }

                    val enc = VaultCryptoEngine.encryptEnvelope(payload.toString(), dataKey)
                    val uploadId = cloudMatch?.id ?: syncId
                    val success = uploadCloudItem(
                        context = context,
                        endpoint = activeEndpoint,
                        typeColumn = activeTypeColumn,
                        accessToken = currentToken,
                        userId = userId,
                        id = uploadId,
                        type = "NOTE",
                        iv = enc.contentIvBase64,
                        salt = "ENV:${enc.wrappedKeyBase64}:${enc.keyIvBase64}",
                        ciphertext = enc.ciphertextBase64,
                        updatedAt = note.updatedAt,
                        isDeleted = false
                    )
                    if (success) {
                        bindSyncId(context, userId, "NOTE", note.id, uploadId)
                        uploaded++
                    }
                }
            }

            val localTasks = taskDao.getAllTasksList()
            localTasks.forEach { task ->
                val syncId = getOrCreateSyncId(context, userId, "TASK", task.id)
                val legacySyncId = if (userId.isNotBlank()) UUID.nameUUIDFromBytes("$userId:TASK:${task.id}".toByteArray(Charsets.UTF_8)).toString() else ""
                val legacyUnscoped = UUID.nameUUIDFromBytes("TASK:${task.id}".toByteArray(Charsets.UTF_8)).toString()
                val cloudMatch = cloudItemMap[syncId] ?: (if (legacySyncId.isNotBlank()) cloudItemMap[legacySyncId] else null) ?: cloudItemMap[legacyUnscoped]

                val taskFingerprint = "${task.title}|${task.details}|${task.dueDate}|${task.isCompleted}|${task.type}|${task.recurrence}|${task.isPersistent}|${task.isPriority}"
                val localUpdatedAt = getOrUpdateTaskTimestamp(context, task.id, taskFingerprint, task.completedAt)
                if (cloudMatch == null || localUpdatedAt > cloudMatch.updatedAt) {
                    val payload = JSONObject().apply {
                        put("title", task.title)
                        put("details", task.details)
                        put("dueDate", task.dueDate ?: JSONObject.NULL)
                        put("isCompleted", task.isCompleted)
                        put("type", task.type.name)
                        put("recurrence", task.recurrence.name)
                        put("isPersistent", task.isPersistent)
                        put("isPriority", task.isPriority)
                        put("completedAt", task.completedAt ?: JSONObject.NULL)
                    }

                    val enc = VaultCryptoEngine.encryptEnvelope(payload.toString(), dataKey)
                    val uploadId = cloudMatch?.id ?: syncId
                    val success = uploadCloudItem(
                        context = context,
                        endpoint = activeEndpoint,
                        typeColumn = activeTypeColumn,
                        accessToken = currentToken,
                        userId = userId,
                        id = uploadId,
                        type = "TASK",
                        iv = enc.contentIvBase64,
                        salt = "ENV:${enc.wrappedKeyBase64}:${enc.keyIvBase64}",
                        ciphertext = enc.ciphertextBase64,
                        updatedAt = localUpdatedAt,
                        isDeleted = false
                    )
                    if (success) {
                        bindSyncId(context, userId, "TASK", task.id, uploadId)
                        uploaded++
                    }
                }
            }

            // 4. Push local deletions (Tombstones)
            val deletionPrefs = context.getSharedPreferences(DELETIONS_PREFS, Context.MODE_PRIVATE)
            val pendingDeletions = deletionPrefs.getStringSet("pending_deletions", emptySet()) ?: emptySet()
            val successfullySyncedDeletions = mutableSetOf<String>()

            pendingDeletions.forEach { record ->
                val parts = record.split(":")
                if (parts.size >= 2) {
                    val type = parts[0]
                    val syncId = parts[1]

                    val success = uploadCloudItem(
                        context = context,
                        endpoint = activeEndpoint,
                        typeColumn = activeTypeColumn,
                        accessToken = currentToken,
                        userId = userId,
                        id = syncId,
                        type = type,
                        iv = "DELETED",
                        salt = "DELETED",
                        ciphertext = "DELETED",
                        updatedAt = System.currentTimeMillis(),
                        isDeleted = true
                    )
                    if (success) {
                        successfullySyncedDeletions.add(record)
                        uploaded++
                    }
                }
            }

            if (successfullySyncedDeletions.isNotEmpty()) {
                val updatedDeletions = pendingDeletions.toMutableSet()
                updatedDeletions.removeAll(successfullySyncedDeletions)
                deletionPrefs.edit().putStringSet("pending_deletions", updatedDeletions).commit()
            }

            // 4.1 Process Pending Media Deletions (Photos & Audio Memos in Supabase Storage)
            val pendingMedia = SupabaseStorageEngine.getPendingMediaDeletions(context)
            if (pendingMedia.isNotEmpty() && userId.isNotBlank()) {
                val cloudPathsToDelete = pendingMedia.map { fileName ->
                    if (fileName.contains("/")) fileName else "$userId/$fileName"
                }
                val mediaDeleteSuccess = SupabaseStorageEngine.deleteMediaBatch(cloudPathsToDelete, currentToken)
                if (mediaDeleteSuccess) {
                    SupabaseStorageEngine.clearPendingMediaDeletions(context, pendingMedia)
                    Log.d(TAG, "Successfully purged ${pendingMedia.size} deleted media attachments from Supabase Storage.")
                }
            }

            // 5. Save last sync timestamp
            SupabaseKeyManager.setLastSyncedTime(context, System.currentTimeMillis())

            Result.success(
                SyncResult(
                    success = true,
                    uploadedCount = uploaded,
                    downloadedCount = downloaded,
                    message = "Cloud sync successful! Uploaded $uploaded items, downloaded $downloaded items."
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sync process failed", e)
            Result.failure(e)
        } finally {
            isSyncInProgress = false
        }
    }

    /**
     * Models a raw encrypted item stored on Supabase.
     */
    data class CloudItem(
        val id: String,
        val type: String,
        val iv: String,
        val salt: String,
        val ciphertext: String,
        val updatedAt: Long,
        val isDeleted: Boolean
    )

    data class FetchCloudResult(
        val items: List<CloudItem>,
        val activeEndpoint: String,
        val typeColumn: String
    )

    private suspend fun fetchCloudItems(context: Context, initialToken: String): Result<FetchCloudResult> {
        val endpoints = listOf(
            Pair(SupabaseConfig.REST_VAULT_ITEMS, "type"),
            Pair(SupabaseConfig.REST_SYNC_ITEMS, "item_type")
        )
        var token = initialToken
        val errors = mutableListOf<String>()

        for ((endpoint, typeCol) in endpoints) {
            val result = queryCloudEndpoint(endpoint, typeCol, token)
            if (result.isSuccess) {
                return Result.success(FetchCloudResult(result.getOrDefault(emptyList()), endpoint, typeCol))
            }

            val err = result.exceptionOrNull()
            val errMsg = err?.message ?: ""
            errors.add("$endpoint: $errMsg")

            // If 401 or 403 due to expired token, try refreshing once
            if (errMsg.contains("401") || errMsg.contains("403") || errMsg.contains("expired")) {
                val refreshedToken = SupabaseAuthManager.refreshSession(context).getOrNull()
                if (refreshedToken != null) {
                    token = refreshedToken
                    val retryResult = queryCloudEndpoint(endpoint, typeCol, token)
                    if (retryResult.isSuccess) {
                        return Result.success(FetchCloudResult(retryResult.getOrDefault(emptyList()), endpoint, typeCol))
                    }
                }
            }
        }

        // Return detailed error if all endpoints failed
        return Result.failure(Exception(errors.joinToString(" | ")))
    }

    private fun queryCloudEndpoint(endpoint: String, defaultTypeCol: String, token: String): Result<List<CloudItem>> {
        val list = mutableListOf<CloudItem>()
        try {
            val url = URL("$endpoint?select=*")
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val respText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val array = JSONArray(respText)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val itemType = when {
                        obj.has("type") -> obj.getString("type")
                        obj.has("item_type") -> obj.getString("item_type")
                        else -> "NOTE"
                    }
                    list.add(
                        CloudItem(
                            id = obj.getString("id"),
                            type = itemType,
                            iv = obj.getString("iv"),
                            salt = obj.getString("salt"),
                            ciphertext = obj.getString("ciphertext"),
                            updatedAt = obj.optLong("updated_at", System.currentTimeMillis()),
                            isDeleted = obj.optBoolean("is_deleted", false)
                        )
                    )
                }
                return Result.success(list)
            } else {
                val errBody = try {
                    BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
                } catch (_: Exception) { "" }
                Log.e(TAG, "Query $endpoint failed with HTTP $responseCode: $errBody")
                return Result.failure(Exception("HTTP $responseCode: $errBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query $endpoint network error", e)
            return Result.failure(e)
        }
    }

    private suspend fun uploadCloudItem(
        context: Context,
        endpoint: String,
        typeColumn: String,
        accessToken: String,
        userId: String?,
        id: String,
        type: String,
        iv: String,
        salt: String,
        ciphertext: String,
        updatedAt: Long,
        isDeleted: Boolean
    ): Boolean {
        val body = JSONObject().apply {
            put("id", id)
            if (!userId.isNullOrBlank()) put("user_id", userId)
            put(typeColumn, type)
            put("iv", iv)
            put("salt", salt)
            put("ciphertext", ciphertext)
            put("updated_at", updatedAt)
            put("is_deleted", isDeleted)
        }

        var token = accessToken
        var success = executeUpload(endpoint, token, body)
        if (success) return true

        // Try token refresh if unauthorized/forbidden
        val refreshed = SupabaseAuthManager.refreshSession(context).getOrNull()
        if (refreshed != null) {
            token = refreshed
            success = executeUpload(endpoint, token, body)
        }
        return success
    }

    private fun executeUpload(endpoint: String, token: String, body: JSONObject): Boolean {
        try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Prefer", "resolution=merge-duplicates")
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                return true
            } else {
                val errBody = try {
                    BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
                } catch (_: Exception) { "" }
                Log.e(TAG, "Upload to $endpoint failed HTTP $responseCode: $errBody")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload to $endpoint network error", e)
            return false
        }
    }

    private fun decryptPayload(item: CloudItem, key: ByteArray): String? {
        return try {
            if (item.salt.startsWith("ENV:")) {
                val parts = item.salt.removePrefix("ENV:").split(":")
                if (parts.size >= 2) {
                    val wrappedKeyB64 = parts[0]
                    val keyIvB64 = parts[1]
                    val envelopeRes = VaultCryptoEngine.decryptEnvelope(
                        wrappedKeyBase64 = wrappedKeyB64,
                        keyIvBase64 = keyIvB64,
                        contentIvBase64 = item.iv,
                        ciphertextBase64 = item.ciphertext,
                        dek = key
                    )
                    if (envelopeRes.isSuccess) {
                        return envelopeRes.getOrNull()
                    }
                }
            }
            // Fallback for legacy items encrypted directly with master key
            val pkg = VaultCryptoEngine.EncryptedPackage(
                saltBase64 = item.salt,
                ivBase64 = item.iv,
                ciphertextBase64 = item.ciphertext
            )
            VaultCryptoEngine.decryptWithRawKey(pkg, key).getOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt payload failed for ${item.id}", e)
            null
        }
    }
}
