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
import android.util.Base64
import android.util.Log
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskDao
import com.focusbyrj.app.data.TaskType
import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.data.note.NoteDao
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.sync.VaultCryptoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
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
 * Pattern 1 "Hardened Notesnook-Lite" enforcement:
 * 1. Every item is encrypted with an ephemeral CEK (envelope encryption via VaultCryptoEngine).
 * 2. The CEK is wrapped with the user's HKDF-derived UserKey (DEK). The wrapped_key is stored
 *    in its own dedicated Supabase column — NOT mixed into the salt column as "ENV:" prefixed text.
 * 3. Every uploaded item carries an HMAC-SHA256 signature bound to (id, seq, isDeleted, ciphertext).
 *    On pull, items with invalid or missing signatures are rejected. This prevents a compromised
 *    Supabase backend from silently swapping or rolling back note ciphertexts.
 * 4. The 'type' metadata (NOTE/TASK) is encrypted INSIDE the payload JSON, not stored as a
 *    cleartext column. New items write 'OPAQUE' to the type column; old items are backward-compatible.
 * 5. client_seq_num increments monotonically per item per device, preventing replay attacks.
 */
object SupabaseSyncEngine {

    private const val TAG = "SupabaseSyncEngine"
    private const val DELETIONS_PREFS = "focus_supabase_deletions"
    private const val TASK_TIMESTAMPS_PREFS = "focus_task_sync_timestamps"
    private const val SYNC_MAP_PREFS = "focus_supabase_sync_id_mapping"

    private val syncMutex = Mutex()

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
     * Unbinds a local SQLite ID and remote cloud UUID from the sync map upon permanent deletion.
     * Prevents recycled SQLite rowids from colliding with deleted cloud UUIDs.
     *
     * [performOrphanScan]: B2-P3-005 — When true (default), performs an O(n) scan of prefs.all
     * to purge any orphaned forward-keys pointing to cloudSyncId. Set to false in the hot sync
     * path where localId is already known, to avoid O(n²) behaviour with many tombstones.
     */
    fun unbindSyncId(
        context: Context,
        userId: String,
        type: String,
        localId: Long?,
        cloudSyncId: String,
        performOrphanScan: Boolean = true
    ) {
        if (userId.isBlank() || cloudSyncId.isBlank()) return
        val prefs = context.getSharedPreferences(SYNC_MAP_PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit().remove("$userId:$type:rev:$cloudSyncId")
        val resolvedLocalId = if (localId != null && localId > 0L) localId else getLocalIdForSyncId(context, userId, type, cloudSyncId)
        if (resolvedLocalId != null && resolvedLocalId > 0L) {
            editor.remove("$userId:$type:$resolvedLocalId")
        }
        // O(n) orphan scan: only perform when callers do not already have the localId.
        // Skip (performOrphanScan=false) in hot-path tombstone loops to prevent O(n²) complexity.
        if (performOrphanScan) {
            val prefix = "$userId:$type:"
            val revPrefix = "$userId:$type:rev:"
            for ((k, v) in prefs.all) {
                if (k.startsWith(prefix) && !k.startsWith(revPrefix) && v == cloudSyncId) {
                    editor.remove(k)
                }
            }
        }
        editor.commit()
    }

    /**
     * Retrieves the mapped local SQLite ID for a given cloud UUID.
     * Includes self-healing fallback that scans forward keys if the reverse lookup was desynced.
     */
    fun getLocalIdForSyncId(context: Context, userId: String, type: String, cloudSyncId: String): Long? {
        if (userId.isBlank() || cloudSyncId.isBlank()) return null
        val prefs = context.getSharedPreferences(SYNC_MAP_PREFS, Context.MODE_PRIVATE)
        val localId = prefs.getLong("$userId:$type:rev:$cloudSyncId", -1L)
        if (localId > 0L) return localId

        // Resilient fallback: scan forward mapping keys in case reverse key was desynced
        val prefix = "$userId:$type:"
        val revPrefix = "$userId:$type:rev:"
        for ((k, v) in prefs.all) {
            if (k.startsWith(prefix) && !k.startsWith(revPrefix) && v == cloudSyncId) {
                val parsedLocalId = k.removePrefix(prefix).toLongOrNull()
                if (parsedLocalId != null && parsedLocalId > 0L) {
                    prefs.edit().putLong("$userId:$type:rev:$cloudSyncId", parsedLocalId).apply()
                    return parsedLocalId
                }
            }
        }
        return null
    }

    /**
     * Generates or retrieves a stable non-deterministic UUIDv4 sync ID mapped to the local item.
     * Prevents metadata leakage and preserves multi-device pairing.
     */
    fun getExistingSyncId(context: Context, userId: String, type: String, localId: Long): String? {
        val prefs = context.getSharedPreferences(SYNC_MAP_PREFS, Context.MODE_PRIVATE)
        return prefs.getString("$userId:$type:$localId", null)
    }

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

        // Predicate check must be strictly read-only — do NOT generate new random UUIDs during search!
        val existingSyncId = getExistingSyncId(context, userId, type, localId)
        if (existingSyncId != null && cloudId == existingSyncId) return true

        // Backward compatibility fallback for legacy deterministic scoped IDs
        val legacyScoped = if (userId.isNotBlank()) UUID.nameUUIDFromBytes("$userId:$type:$localId".toByteArray(Charsets.UTF_8)).toString() else ""
        if (legacyScoped.isNotBlank() && cloudId == legacyScoped) return true

        return false
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

    /**
     * Checks if note content has diverged between local note and incoming cloud JSON.
     * Evaluates title, content, checklist, images, audio memos, labels, and trash state.
     */
    @androidx.annotation.VisibleForTesting
    internal fun hasNoteContentDiverged(cleanMatch: NoteEntity, root: JSONObject): Boolean {
        return cleanMatch.content != root.optString("content", "") ||
                cleanMatch.title != root.optString("title", "") ||
                cleanMatch.checklistJson != root.optString("checklistJson", "[]") ||
                cleanMatch.imageUrisJson != root.optString("imageUrisJson", "[]") ||
                cleanMatch.audioUrisJson != root.optString("audioUrisJson", "[]") ||
                cleanMatch.labelsJson != root.optString("labelsJson", "[]") ||
                cleanMatch.isTrashed != root.optBoolean("isTrashed", false)
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
     *
     * B2-P3-009 FIX: Only records deletions for items that have already been synced at least
     * once (have an existing cloud UUID). Items created and deleted without ever syncing have
     * no cloud counterpart, so creating a UUID for them would push orphan tombstones that
     * waste bandwidth and pollute the cloud with records that no device can match.
     */
    fun recordLocalDeletion(context: Context, type: String, localId: Long) {
        val session = SupabaseKeyManager.getSessionState(context)
        val userId = session.userId?.takeIf { it.isNotBlank() }
            ?: context.getSharedPreferences("focus_supabase_zk_prefs", Context.MODE_PRIVATE).getString("user_id", null)?.takeIf { it.isNotBlank() }
            ?: ""
        if (userId.isBlank() || localId <= 0L) return

        // B2-P3-009: Use getExistingSyncId() to avoid creating a new UUID for never-synced items.
        // If the item has never been pushed to the cloud, there is nothing on the server to tombstone.
        val syncId = getExistingSyncId(context, userId, type, localId) ?: return

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
        if (!syncMutex.tryLock()) {
            Log.d(TAG, "Sync already in progress, skipping duplicate trigger.")
            return@withContext Result.failure(Exception("Sync already in progress"))
        }

        isSyncInProgress = true
        try {
            val session = SupabaseKeyManager.getSessionState(context)
            if (!session.isSignedIn) {
                return@withContext Result.failure(Exception("User is not signed in to Cloud Vault."))
            }

            if (session.isOfflineMode) {
                Log.d(TAG, "Sync skipped: Vault is in offline mode.")
                return@withContext Result.failure(Exception("Cloud Vault is currently in Offline Mode. Sync is paused."))
            }

            val userId = session.userId ?: ""
            val token = SupabaseAuthManager.getValidAccessToken(context) ?: session.accessToken
            if (token.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Session expired or invalid. Please sign in again."))
            }

            val dataKey = SupabaseKeyManager.getDataEncryptionKey(context)
                ?: return@withContext Result.failure(Exception("Cryptographic key not initialized. Please sign in again."))

            // The HMAC key may be null for sessions established before Pattern 1 update.
            // Such sessions fall back to unsigned envelope format (legacy decryption path) and
            // skip signature verification. Users must sign out/in to obtain an hmacKey.
            val hmacKey = SupabaseKeyManager.getHmacKey(context)

            Log.d(TAG, "Starting Zero-Knowledge Cloud Sync...")

            // Pre-sync snapshot: record local counts so we can detect catastrophic
            // data loss caused by a bug in the sync engine (e.g., mass tombstone application).
            val preSyncNoteCount = try { noteDao.getAllNotesList().size } catch (_: Exception) { -1 }
            val preSyncTaskCount = try { taskDao.getAllTasksList().size } catch (_: Exception) { -1 }

            // Write pre-sync safety snapshot before any pull/tombstone/deletion operations
            val focusDb = (context.applicationContext as? com.focusbyrj.app.FocusApplication)?.database
            com.focusbyrj.app.util.backup.DataSafetyManager.writePreOpSnapshot(context, noteDao, "pre_sync", focusDb)

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

            // B2-P3-007: Track IDs of [Conflict]/[Restored] copies created during this pull phase.
            // The push phase skips these IDs to prevent them from being immediately re-synced
            // to the cloud and triggering recursive duplication on other devices.
            val sameCycleConflictIds = mutableSetOf<Long>()

            // B2-P3-004 FIX: Pre-deletion anomaly guard.
            // Count projected tombstone deletions BEFORE applying any to SQLite.
            // If >40% of local data would be deleted in one cycle, abort without touching the DB.
            if (session.lastSyncedTime > 0L && (preSyncNoteCount > 5 || preSyncTaskCount > 5)) {
                var projectedNoteDeletes = 0
                var projectedTaskDeletes = 0
                cloudItems.filter { it.isDeleted }.forEach { tombstone ->
                    val resolvedType = if (tombstone.type == "OPAQUE" || tombstone.type.isBlank()) {
                        when {
                            getLocalIdForSyncId(context, userId, "NOTE", tombstone.id) != null -> "NOTE"
                            getLocalIdForSyncId(context, userId, "TASK", tombstone.id) != null -> "TASK"
                            else -> ""
                        }
                    } else tombstone.type
                    when (resolvedType) {
                        "NOTE" -> if (getLocalIdForSyncId(context, userId, "NOTE", tombstone.id) != null) projectedNoteDeletes++
                        "TASK" -> if (getLocalIdForSyncId(context, userId, "TASK", tombstone.id) != null) projectedTaskDeletes++
                    }
                }
                val noteDropPct = if (preSyncNoteCount > 5) projectedNoteDeletes.toFloat() / preSyncNoteCount else 0f
                val taskDropPct = if (preSyncTaskCount > 5) projectedTaskDeletes.toFloat() / preSyncTaskCount else 0f
                if (noteDropPct > 0.40f || taskDropPct > 0.40f) {
                    Log.e(TAG, "PRE-DELETION ANOMALY GUARD: projected $projectedNoteDeletes note deletes / $preSyncNoteCount " +
                        "(${(noteDropPct * 100).toInt()}%), $projectedTaskDeletes task deletes / $preSyncTaskCount. " +
                        "Aborting sync BEFORE any deletions to prevent data loss.")
                    com.focusbyrj.app.util.backup.DataSafetyManager.writeEmergencySnapshot(
                        context, noteDao, (context.applicationContext as? com.focusbyrj.app.FocusApplication)?.database)
                    return@withContext Result.failure(Exception(
                        "Sync anomaly detected: $projectedNoteDeletes notes / $projectedTaskDeletes tasks " +
                        "would be deleted (>${(noteDropPct.coerceAtLeast(taskDropPct) * 100).toInt()}% of local data). " +
                        "Sync aborted for safety. An emergency snapshot was created."
                    ))
                }
            }

            // 2. Process Cloud-to-Local (PULL SYNC)
            cloudItems.forEach { cloudItem ->
                // Check sequence number monotonicity: reject stale or replayed items
                val storedSeqNum = SupabaseKeyManager.getSequenceNumber(context, cloudItem.id)
                if (cloudItem.clientSeqNum > 0L && cloudItem.clientSeqNum < storedSeqNum) {
                    Log.w(TAG, "Rejecting stale cloud item ${cloudItem.id}: received seq ${cloudItem.clientSeqNum} < stored $storedSeqNum")
                    return@forEach
                }

                // HMAC signature verification (Pattern 1): reject items with invalid signatures
                // Enforce signature verification for BOTH active items and tombstones (ciphertext = "")
                val signatureValid = if (hmacKey != null && cloudItem.signature.isNotBlank()) {
                    SupabaseKeyManager.verifySignature(
                        itemId = cloudItem.id,
                        clientSeqNum = cloudItem.clientSeqNum,
                        isDeleted = cloudItem.isDeleted,
                        ciphertext = cloudItem.ciphertext,
                        hmacKey = hmacKey,
                        signatureBase64 = cloudItem.signature
                    )
                } else {
                    if (hmacKey != null && cloudItem.signature.isBlank()) {
                        // Legacy item — no signature, allow but log
                        Log.w(TAG, "Cloud item ${cloudItem.id} has no signature (legacy). Allowing with warning.")
                    }
                    true // accept unsigned legacy items
                }

                if (!signatureValid) {
                    Log.e(TAG, "SECURITY: Cloud item ${cloudItem.id} failed HMAC signature check — REJECTING. " +
                            "Possible backend tampering or key mismatch.")
                    return@forEach // Never write or delete tampered data locally
                }

                // Update sequence number monotonically upon valid verification
                if (cloudItem.clientSeqNum > 0L) {
                    SupabaseKeyManager.updateSequenceNumberIfHigher(context, cloudItem.id, cloudItem.clientSeqNum)
                }

                if (cloudItem.isDeleted) {
                    // ── First-sync tombstone guard ──────────────────────────────────────────
                    // If this device has NEVER successfully synced (lastSyncedTime == 0), do
                    // NOT apply any remote deletions. The remote could have stale tombstones
                    // from a previous device that would mass-delete local-only content on a
                    // fresh install. Upload everything local first; apply deletions on the
                    // second sync once we have a stable lastSyncedTime baseline.
                    if (session.lastSyncedTime == 0L) {
                        Log.w(TAG, "First-sync: skipping tombstone for ${cloudItem.id} — local content protected until baseline established.")
                        return@forEach
                    }

                    // Resolve item type from sync map lookup for OPAQUE tombstones
                    val resolvedType = if (cloudItem.type == "OPAQUE" || cloudItem.type.isBlank()) {
                        val noteLocalId = getLocalIdForSyncId(context, userId, "NOTE", cloudItem.id)
                        val taskLocalId = getLocalIdForSyncId(context, userId, "TASK", cloudItem.id)
                        when {
                            noteLocalId != null -> "NOTE"
                            taskLocalId != null -> "TASK"
                            else -> cloudItem.type
                        }
                    } else cloudItem.type

                    if (resolvedType == "NOTE") {
                        val localId = getLocalIdForSyncId(context, userId, "NOTE", cloudItem.id)
                        val match = if (localId != null) {
                            noteDao.getNoteByIdSync(localId)
                        } else {
                            noteDao.getAllNotesList().find { isMatchingItem(context, cloudItem.id, userId, "NOTE", it.id) }
                        }
                        if (match != null) {
                            if (match.updatedAt > cloudItem.updatedAt) {
                                // Note was edited locally after cloud deletion timestamp: revive item under fresh sync ID
                                Log.w(TAG, "Note ${match.id} was edited locally after remote deletion. Reviving item.")
                                unbindSyncId(context, userId, "NOTE", match.id, cloudItem.id, performOrphanScan = false)
                            } else {
                                val cleanNote = if (match.isArchived || com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(match)) {
                                    com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.decryptNotePayload(match)
                                } else {
                                    match
                                }
                                var hadRestoredCopy = false
                                // If note was edited offline since last sync, preserve content as restored copy
                                if (session.lastSyncedTime > 0L && match.updatedAt > session.lastSyncedTime) {
                                    var restoredCopy = cleanNote.copy(
                                        id = 0L,
                                        title = if (cleanNote.title.startsWith("[Restored]")) cleanNote.title else "[Restored] ${cleanNote.title.ifBlank { "Untitled" }}",
                                        updatedAt = cleanNote.updatedAt
                                    )
                                    if (restoredCopy.isArchived) {
                                        restoredCopy = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(restoredCopy)
                                    }
                                    val restoredId = noteDao.insertNote(restoredCopy)
                                    // B2-P3-007: Mark this copy so the push phase does not re-sync it this cycle
                                    if (restoredId > 0L) sameCycleConflictIds.add(restoredId)
                                    hadRestoredCopy = true
                                    Log.w(TAG, "Preserved offline edit for remotely deleted note ${match.id} as restored copy.")
                                }
                                if (!hadRestoredCopy) {
                                    com.focusbyrj.app.data.note.NoteMediaManager.deleteNoteMediaFiles(cleanNote)
                                }
                                noteDao.deleteNote(match)
                                unbindSyncId(context, userId, "NOTE", match.id, cloudItem.id, performOrphanScan = false)
                                downloaded++
                            }
                        }
                    } else if (resolvedType == "TASK") {
                        val localId = getLocalIdForSyncId(context, userId, "TASK", cloudItem.id)
                        val match = if (localId != null) {
                            taskDao.getTaskById(localId)
                        } else {
                            taskDao.getAllTasksList().find { isMatchingItem(context, cloudItem.id, userId, "TASK", it.id) }
                        }
                        if (match != null) {
                            val matchFingerprint = "${match.title}|${match.details}|${match.dueDate}|${match.isCompleted}|${match.type}|${match.recurrence}|${match.isPersistent}|${match.isPriority}|${match.isTrashed}|${match.trashedAt}|${match.deletedAt}|${match.subtasksJson}"
                            val localUpdatedAt = getOrUpdateTaskTimestamp(context, match.id, matchFingerprint, match.completedAt)
                            if (localUpdatedAt > cloudItem.updatedAt) {
                                Log.w(TAG, "Task ${match.id} was edited locally after remote deletion. Reviving item.")
                                unbindSyncId(context, userId, "TASK", match.id, cloudItem.id, performOrphanScan = false)
                            } else {
                                if (session.lastSyncedTime > 0L && localUpdatedAt > session.lastSyncedTime) {
                                    val restoredCopy = match.copy(
                                        id = 0L,
                                        title = if (match.title.startsWith("[Restored]")) match.title else "[Restored] ${match.title.ifBlank { "Untitled Task" }}"
                                    )
                                    val restoredId = taskDao.insertTask(restoredCopy)
                                    // B2-P3-007: Mark restored task copy to skip push this cycle
                                    if (restoredId > 0L) sameCycleConflictIds.add(restoredId)
                                    Log.w(TAG, "Preserved offline edit for remotely deleted task ${match.id} as restored copy.")
                                }
                                taskDao.deleteTask(match)
                                unbindSyncId(context, userId, "TASK", match.id, cloudItem.id, performOrphanScan = false)
                                val taskTsPrefs = context.getSharedPreferences(TASK_TIMESTAMPS_PREFS, Context.MODE_PRIVATE)
                                taskTsPrefs.edit().remove("fp_${match.id}").remove("ts_${match.id}").apply()
                                downloaded++
                            }
                        }
                    }
                } else {
                    // Decrypt and merge active item
                    try {
                        val decJson = decryptPayload(cloudItem, dataKey)
                        if (decJson != null) {
                            val root = JSONObject(decJson)
                            // Resolve type from inside payload (Pattern 1) or from cloud field (legacy)
                            val itemType = root.optString("type", cloudItem.type)
                                .let { if (it == "OPAQUE" || it.isBlank()) cloudItem.type else it }

                            if (itemType == "NOTE") {
                                val localId = getLocalIdForSyncId(context, userId, "NOTE", cloudItem.id)
                                val match = if (localId != null) {
                                    noteDao.getNoteByIdSync(localId)
                                } else {
                                    val cloudCreatedAt = root.optLong("createdAt", 0L)
                                    val cloudTitle = root.optString("title", "")
                                    noteDao.getAllNotesList().find { localNote ->
                                        isMatchingItem(context, cloudItem.id, userId, "NOTE", localNote.id) ||
                                        (cloudCreatedAt > 0L && localNote.createdAt == cloudCreatedAt && localNote.title == cloudTitle)
                                    }
                                }

                                val isCloudNoteArchived = root.optBoolean("isArchived", false)
                                val isVaultLocked = com.focusbyrj.app.data.note.ArchiveVaultSecurity.getVaultStatus(context) == com.focusbyrj.app.data.note.ArchiveVaultSecurity.VaultStatus.ENABLED &&
                                        com.focusbyrj.app.data.note.ArchiveVaultSecurity.getActiveVaultSubKey() == null

                                // If note is archived in cloud, but local vault is currently locked, defer update to avoid writing cleartext to SQLite or corrupting vault subkey encryption
                                if (isCloudNoteArchived && isVaultLocked) {
                                    Log.w(TAG, "Secret Vault is locked. Deferring sync pull for archived note ${cloudItem.id} until vault is unlocked.")
                                    return@forEach
                                }

                                val cloudUpdatedAt = cloudItem.updatedAt
                                val localUpdatedAt = match?.updatedAt ?: 0L

                                // True 3-Way Concurrent Conflict Detection:
                                // A concurrent conflict occurs if BOTH this device edited locally since lastSyncedTime
                                // AND the cloud item was edited on another device since lastSyncedTime.
                                val isConcurrentConflict = match != null && session.lastSyncedTime > 0L &&
                                        localUpdatedAt > session.lastSyncedTime &&
                                        cloudUpdatedAt > session.lastSyncedTime

                                if (isConcurrentConflict) {
                                    val cleanMatch = if (match!!.isArchived || com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(match)) {
                                        com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.decryptNotePayload(match)
                                    } else {
                                        match
                                    }
                                    val hasContentDiverged = hasNoteContentDiverged(cleanMatch, root)

                                    if (hasContentDiverged) {
                                        // Both sides made offline edits. Preserve the local version as a distinct [Conflict] note
                                        // so that neither user's work is silently overwritten or discarded.
                                        var conflictCopy = cleanMatch.copy(
                                            id = 0L,
                                            title = if (cleanMatch.title.startsWith("[Conflict]")) cleanMatch.title else "[Conflict] ${cleanMatch.title.ifBlank { "Untitled" }}",
                                            updatedAt = cleanMatch.updatedAt
                                        )
                                        if (conflictCopy.isArchived) {
                                            conflictCopy = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(conflictCopy)
                                        }
                                        val conflictRowId = noteDao.insertNote(conflictCopy)
                                        Log.w(TAG, "True 3-Way Conflict: preserved concurrent local edit for note ${match.id} as [Conflict] copy (row $conflictRowId).")
                                    }
                                }

                                if (match == null || cloudUpdatedAt > localUpdatedAt || isConcurrentConflict) {
                                    val pullToken = SupabaseAuthManager.getValidAccessToken(context) ?: token
                                    val cloudImageUris = try {
                                        val arr = JSONArray(root.optString("imageUrisJson", "[]"))
                                        val list = mutableListOf<String>()
                                        for (i in 0 until arr.length()) list.add(arr.getString(i))
                                        list
                                    } catch (e: Exception) { emptyList() }

                                    val localImagePaths = mutableListOf<String>()
                                    for (item in cloudImageUris) {
                                        val clean = item.trim()
                                        if (clean.isBlank()) continue
                                        if (clean.endsWith(".enc") || clean.contains("/")) {
                                            val downloadedMedia = SupabaseStorageEngine.downloadMedia(
                                                context = context,
                                                cloudPath = clean,
                                                subDirName = "keep_images",
                                                accessToken = pullToken,
                                                dataKey = dataKey
                                            )
                                            if (downloadedMedia != null) {
                                                localImagePaths.add(downloadedMedia)
                                            } else {
                                                // Retain cloud path so attachment is not permanently lost on temporary network drop
                                                localImagePaths.add(clean)
                                            }
                                        } else {
                                            localImagePaths.add(clean)
                                        }
                                    }

                                    val cloudAudioUris = try {
                                        val arr = JSONArray(root.optString("audioUrisJson", "[]"))
                                        val list = mutableListOf<String>()
                                        for (i in 0 until arr.length()) list.add(arr.getString(i))
                                        list
                                    } catch (e: Exception) { emptyList() }

                                    val localAudioPaths = mutableListOf<String>()
                                    for (item in cloudAudioUris) {
                                        val clean = item.trim()
                                        if (clean.isBlank()) continue
                                        if (clean.endsWith(".enc") || clean.contains("/")) {
                                            val downloadedMedia = SupabaseStorageEngine.downloadMedia(
                                                context = context,
                                                cloudPath = clean,
                                                subDirName = "keep_audio",
                                                accessToken = pullToken,
                                                dataKey = dataKey
                                            )
                                            if (downloadedMedia != null) {
                                                localAudioPaths.add(downloadedMedia)
                                            } else {
                                                // Retain cloud path so audio memo is not permanently lost on temporary network drop
                                                localAudioPaths.add(clean)
                                            }
                                        } else {
                                            localAudioPaths.add(clean)
                                        }
                                    }

                                    val isTrashed = root.optBoolean("isTrashed", false)
                                    val trashedAt = if (root.has("trashedAt") && !root.isNull("trashedAt")) root.optLong("trashedAt").takeIf { it > 0L } else null
                                    val deletedAt = if (root.has("deletedAt") && !root.isNull("deletedAt")) root.optLong("deletedAt").takeIf { it > 0L } else null

                                    var note = NoteEntity(
                                        id = match?.id ?: 0L,
                                        title = root.optString("title", ""),
                                        content = root.optString("content", ""),
                                        isChecklist = root.optBoolean("isChecklist", false),
                                        checklistJson = root.optString("checklistJson", "[]"),
                                        colorKey = root.optString("colorKey", "default"),
                                        fontKey = root.optString("fontKey", "default"),
                                        isPinned = root.optBoolean("isPinned", false),
                                        isArchived = root.optBoolean("isArchived", false),
                                        isTrashed = isTrashed,
                                        trashedAt = trashedAt,
                                        deletedAt = deletedAt,
                                        labelsJson = root.optString("labelsJson", "[]"),
                                        imageUrisJson = JSONArray(localImagePaths).toString(),
                                        audioUrisJson = JSONArray(localAudioPaths).toString(),
                                        createdAt = root.optLong("createdAt", cloudUpdatedAt),
                                        updatedAt = cloudUpdatedAt
                                    )
                                    // Re-encrypt if archived to enforce local vault security in SQLite
                                    if (note.isArchived) {
                                        note = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(note)
                                    }
                                    val newInsertedId = noteDao.insertNote(note)
                                    val finalId = if (match != null) match.id else newInsertedId
                                    bindSyncId(context, userId, "NOTE", finalId, cloudItem.id)
                                    downloaded++
                                } else {
                                    bindSyncId(context, userId, "NOTE", match.id, cloudItem.id)
                                }
                            } else if (itemType == "TASK") {
                                val localId = getLocalIdForSyncId(context, userId, "TASK", cloudItem.id)
                                val match = if (localId != null) {
                                    taskDao.getTaskById(localId)
                                } else {
                                    val cloudDueDate = if (root.isNull("dueDate")) null else root.optLong("dueDate").takeIf { it > 0L }
                                    val cloudTitle = root.optString("title", "")
                                    taskDao.getAllTasksList().find { localTask ->
                                        isMatchingItem(context, cloudItem.id, userId, "TASK", localTask.id) ||
                                        (cloudTitle.isNotBlank() && localTask.title == cloudTitle && localTask.dueDate == cloudDueDate)
                                    }
                                }

                                val cloudUpdatedAt = cloudItem.updatedAt
                                val matchFingerprint = match?.let { "${it.title}|${it.details}|${it.dueDate}|${it.isCompleted}|${it.type}|${it.recurrence}|${it.isPersistent}|${it.isPriority}|${it.isTrashed}|${it.trashedAt}|${it.deletedAt}|${it.subtasksJson}" } ?: ""
                                val localUpdatedAt = if (match != null) getOrUpdateTaskTimestamp(context, match.id, matchFingerprint, match.completedAt) else 0L

                                val isTaskConcurrentConflict = match != null && session.lastSyncedTime > 0L &&
                                        localUpdatedAt > session.lastSyncedTime &&
                                        cloudUpdatedAt > session.lastSyncedTime

                                if (isTaskConcurrentConflict) {
                                    val hasTaskDiverged = match!!.title != root.optString("title", "") ||
                                            match.details != root.optString("details", "") ||
                                            match.dueDate != (if (root.isNull("dueDate")) null else root.optLong("dueDate").takeIf { it > 0L }) ||
                                            match.isCompleted != root.optBoolean("isCompleted", false) ||
                                            match.subtasksJson != root.optString("subtasksJson", "[]") ||
                                            match.isTrashed != root.optBoolean("isTrashed", false)
                                    if (hasTaskDiverged) {
                                        val conflictTask = match.copy(
                                            id = 0L,
                                            title = if (match.title.startsWith("[Conflict]")) match.title else "[Conflict] ${match.title.ifBlank { "Untitled Task" }}"
                                        )
                                        val conflictTaskId = taskDao.insertTask(conflictTask)
                                        Log.w(TAG, "True 3-Way Conflict: preserved concurrent local edit for task ${match.id} as [Conflict] copy (row $conflictTaskId).")
                                    }
                                }

                                if (match == null || cloudUpdatedAt > localUpdatedAt || isTaskConcurrentConflict) {
                                    val typeName = root.optString("taskType", root.optString("type", TaskType.TASK.name))
                                    val recName = root.optString("recurrence", RecurrencePattern.NONE.name)
                                    val isTrashed = root.optBoolean("isTrashed", false)
                                    val trashedAt = if (root.has("trashedAt") && !root.isNull("trashedAt")) root.optLong("trashedAt").takeIf { it > 0L } else null
                                    val deletedAt = if (root.has("deletedAt") && !root.isNull("deletedAt")) root.optLong("deletedAt").takeIf { it > 0L } else null
                                    val subtasksJson = root.optString("subtasksJson", "[]")

                                    val task = Task(
                                        id = match?.id ?: 0L,
                                        title = root.optString("title", ""),
                                        details = root.optString("details", ""),
                                        dueDate = if (root.isNull("dueDate")) null else root.optLong("dueDate").takeIf { it > 0L },
                                        isCompleted = root.optBoolean("isCompleted", false),
                                        type = try { TaskType.valueOf(typeName.trim().uppercase()) } catch (e: Exception) { TaskType.TASK },
                                        recurrence = try { RecurrencePattern.valueOf(recName.trim().uppercase()) } catch (e: Exception) { RecurrencePattern.NONE },
                                        isPersistent = root.optBoolean("isPersistent", false),
                                        isPriority = root.optBoolean("isPriority", false),
                                        completedAt = if (root.isNull("completedAt")) null else root.optLong("completedAt").takeIf { it > 0L },
                                        updatedAt = cloudUpdatedAt,
                                        isTrashed = isTrashed,
                                        trashedAt = trashedAt,
                                        deletedAt = deletedAt,
                                        subtasksJson = subtasksJson
                                    )
                                    val newTaskId = taskDao.insertTask(task)
                                    val finalTaskId = if (match != null) match.id else newTaskId
                                    bindSyncId(context, userId, "TASK", finalTaskId, cloudItem.id)
                                    val newFingerprint = "${task.title}|${task.details}|${task.dueDate}|${task.isCompleted}|${task.type}|${task.recurrence}|${task.isPersistent}|${task.isPriority}|${task.isTrashed}|${task.trashedAt}|${task.deletedAt}|${task.subtasksJson}"
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
            localNotes.forEach { rawNote ->
                // B2-P3-007: Skip notes inserted during this pull phase ([Conflict]/[Restored] copies)
                // to prevent them from being immediately re-synced as NEW items, which would trigger
                // recursive duplication across other devices on the next sync cycle.
                if (rawNote.id in sameCycleConflictIds) return@forEach
                // Decrypt archived vault notes so cloud receives clean payload instead of local subkey ciphertext
                val note = if (rawNote.isArchived || com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(rawNote)) {
                    com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.decryptNotePayload(rawNote)
                } else {
                    rawNote
                }
                val syncId = getOrCreateSyncId(context, userId, "NOTE", note.id)
                val legacySyncId = if (userId.isNotBlank()) UUID.nameUUIDFromBytes("$userId:NOTE:${note.id}".toByteArray(Charsets.UTF_8)).toString() else ""
                val legacyUnscoped = UUID.nameUUIDFromBytes("NOTE:${note.id}".toByteArray(Charsets.UTF_8)).toString()
                val cloudMatch = cloudItemMap[syncId] ?: (if (legacySyncId.isNotBlank()) cloudItemMap[legacySyncId] else null) ?: cloudItemMap[legacyUnscoped]

                if (cloudMatch == null || note.updatedAt > cloudMatch.updatedAt) {
                    var mediaUploadFailed = false

                    // Upload any local image attachments to Supabase Storage
                    val cloudImagePaths = mutableListOf<String>()
                    note.getImageUris().forEach { path ->
                        val clean = path.trim()
                        if (clean.isBlank()) return@forEach

                        val isLocalPath = clean.startsWith("/") ||
                                          clean.startsWith("file:") ||
                                          clean.startsWith("content:") ||
                                          clean.contains("keep_images") ||
                                          clean.contains("keep_audio") ||
                                          clean.contains("com.focusbyrj.app") ||
                                          clean.contains("/data/")

                        if (!isLocalPath && clean.contains("/") && clean.endsWith(".enc")) {
                            // Already an anonymized cloud reference (e.g. "$userId/<uuid>.enc"), preserve directly
                            cloudImagePaths.add(clean)
                        } else {
                            val file = java.io.File(clean.removePrefix("file://"))
                            if (file.exists() && file.length() > 0L) {
                                val existingUuid = SupabaseStorageEngine.getCloudUuid(context, userId, clean)
                                    ?: SupabaseStorageEngine.getCloudUuid(context, userId, file.absolutePath)
                                    ?: if (file.name.endsWith(".enc")) file.name.removeSuffix(".enc") else null

                                if (existingUuid != null) {
                                    SupabaseStorageEngine.bindCloudUuid(context, userId, file.absolutePath, existingUuid)
                                    cloudImagePaths.add("$userId/$existingUuid.enc")
                                } else {
                                    val uploadedPath = SupabaseStorageEngine.uploadMedia(
                                        context = context,
                                        localPath = file.absolutePath,
                                        userId = userId,
                                        accessToken = currentToken,
                                        dataKey = dataKey
                                    )
                                    if (uploadedPath != null) {
                                        cloudImagePaths.add(uploadedPath)
                                    } else {
                                        Log.w(TAG, "Failed uploading image attachment $clean; deferring note upload to prevent data loss")
                                        mediaUploadFailed = true
                                    }
                                }
                            } else {
                                val fileName = file.name
                                if (fileName.endsWith(".enc")) {
                                    // Corrupted path self-healing: use cloud UUID from filename
                                    cloudImagePaths.add("$userId/$fileName")
                                } else if (!clean.startsWith("/") && clean.endsWith(".enc")) {
                                    val cloudRef = if (clean.startsWith("$userId/")) clean else "$userId/$clean"
                                    cloudImagePaths.add(cloudRef)
                                } else if (clean.isNotBlank()) {
                                    cloudImagePaths.add(clean)
                                }
                            }
                        }
                    }

                    // Upload any local audio attachments to Supabase Storage
                    val cloudAudioPaths = mutableListOf<String>()
                    note.getAudioUris().forEach { path ->
                        val clean = path.trim()
                        if (clean.isBlank()) return@forEach

                        val isLocalPath = clean.startsWith("/") ||
                                          clean.startsWith("file:") ||
                                          clean.startsWith("content:") ||
                                          clean.contains("keep_images") ||
                                          clean.contains("keep_audio") ||
                                          clean.contains("com.focusbyrj.app") ||
                                          clean.contains("/data/")

                        if (!isLocalPath && clean.contains("/") && clean.endsWith(".enc")) {
                            // Already an anonymized cloud reference (e.g. "$userId/<uuid>.enc"), preserve directly
                            cloudAudioPaths.add(clean)
                        } else {
                            val file = java.io.File(clean.removePrefix("file://"))
                            if (file.exists() && file.length() > 0L) {
                                val existingUuid = SupabaseStorageEngine.getCloudUuid(context, userId, clean)
                                    ?: SupabaseStorageEngine.getCloudUuid(context, userId, file.absolutePath)
                                    ?: if (file.name.endsWith(".enc")) file.name.removeSuffix(".enc") else null

                                if (existingUuid != null) {
                                    SupabaseStorageEngine.bindCloudUuid(context, userId, file.absolutePath, existingUuid)
                                    cloudAudioPaths.add("$userId/$existingUuid.enc")
                                } else {
                                    val uploadedPath = SupabaseStorageEngine.uploadMedia(
                                        context = context,
                                        localPath = file.absolutePath,
                                        userId = userId,
                                        accessToken = currentToken,
                                        dataKey = dataKey
                                    )
                                    if (uploadedPath != null) {
                                        cloudAudioPaths.add(uploadedPath)
                                    } else {
                                        Log.w(TAG, "Failed uploading audio attachment $clean; deferring note upload to prevent data loss")
                                        mediaUploadFailed = true
                                    }
                                }
                            } else {
                                val fileName = file.name
                                if (fileName.endsWith(".enc")) {
                                    cloudAudioPaths.add("$userId/$fileName")
                                } else if (!clean.startsWith("/") && clean.endsWith(".enc")) {
                                    val cloudRef = if (clean.startsWith("$userId/")) clean else "$userId/$clean"
                                    cloudAudioPaths.add(cloudRef)
                                } else if (clean.isNotBlank()) {
                                    cloudAudioPaths.add(clean)
                                }
                            }
                        }
                    }

                    if (mediaUploadFailed) {
                        Log.w(TAG, "Skipping upload of note ${note.id} because attachments failed to upload. Will retry next sync.")
                        return@forEach
                    }

                    // Embed 'type' INSIDE the encrypted JSON — Supabase column receives 'OPAQUE'
                    val payload = JSONObject().apply {
                        put("type", "NOTE")           // item type inside envelope (Pattern 1)
                        put("title", note.title)
                        put("content", note.content)
                        put("isChecklist", note.isChecklist)
                        put("checklistJson", note.checklistJson)
                        put("colorKey", note.colorKey)
                        put("fontKey", note.fontKey)
                        put("isPinned", note.isPinned)
                        put("isArchived", note.isArchived)
                        put("isTrashed", note.isTrashed)
                        put("trashedAt", note.trashedAt ?: JSONObject.NULL)
                        put("deletedAt", note.deletedAt ?: JSONObject.NULL)
                        put("labelsJson", note.labelsJson)
                        put("imageUrisJson", JSONArray(cloudImagePaths).toString())
                        put("audioUrisJson", JSONArray(cloudAudioPaths).toString())
                        put("createdAt", note.createdAt)
                    }

                    val enc = VaultCryptoEngine.encryptEnvelope(payload.toString(), dataKey)
                    val uploadId = cloudMatch?.id ?: syncId
                    val seqNum = SupabaseKeyManager.nextSequenceNumber(context, uploadId)
                    val signature = if (hmacKey != null) {
                        SupabaseKeyManager.generateSignature(
                            itemId = uploadId,
                            clientSeqNum = seqNum,
                            isDeleted = false,
                            ciphertext = enc.ciphertextBase64,
                            hmacKey = hmacKey
                        )
                    } else ""

                    val success = uploadCloudItem(
                        context = context,
                        endpoint = activeEndpoint,
                        typeColumn = activeTypeColumn,
                        accessToken = currentToken,
                        userId = userId,
                        id = uploadId,
                        type = "OPAQUE",
                        iv = enc.contentIvBase64,
                        salt = enc.keyIvBase64,
                        ciphertext = enc.ciphertextBase64,
                        wrappedKey = enc.wrappedKeyBase64,
                        signature = signature,
                        clientSeqNum = seqNum,
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
                // B2-P3-007: Skip tasks inserted during this pull phase ([Restored] copies)
                if (task.id in sameCycleConflictIds) return@forEach
                val syncId = getOrCreateSyncId(context, userId, "TASK", task.id)
                val legacySyncId = if (userId.isNotBlank()) UUID.nameUUIDFromBytes("$userId:TASK:${task.id}".toByteArray(Charsets.UTF_8)).toString() else ""
                val legacyUnscoped = UUID.nameUUIDFromBytes("TASK:${task.id}".toByteArray(Charsets.UTF_8)).toString()
                val cloudMatch = cloudItemMap[syncId] ?: (if (legacySyncId.isNotBlank()) cloudItemMap[legacySyncId] else null) ?: cloudItemMap[legacyUnscoped]

                val taskFingerprint = "${task.title}|${task.details}|${task.dueDate}|${task.isCompleted}|${task.type}|${task.recurrence}|${task.isPersistent}|${task.isPriority}|${task.isTrashed}|${task.trashedAt}|${task.deletedAt}|${task.subtasksJson}"
                val localUpdatedAt = getOrUpdateTaskTimestamp(context, task.id, taskFingerprint, task.completedAt)
                if (cloudMatch == null || localUpdatedAt > cloudMatch.updatedAt) {
                    // Embed 'type' INSIDE the encrypted JSON — Supabase column receives 'OPAQUE'
                    val payload = JSONObject().apply {
                        put("type", "TASK")           // item type inside envelope (Pattern 1)
                        put("taskType", task.type.name) // granular task type
                        put("title", task.title)
                        put("details", task.details)
                        put("dueDate", task.dueDate ?: JSONObject.NULL)
                        put("isCompleted", task.isCompleted)
                        put("recurrence", task.recurrence.name)
                        put("isPersistent", task.isPersistent)
                        put("isPriority", task.isPriority)
                        put("completedAt", task.completedAt ?: JSONObject.NULL)
                        put("isTrashed", task.isTrashed)
                        put("trashedAt", task.trashedAt ?: JSONObject.NULL)
                        put("deletedAt", task.deletedAt ?: JSONObject.NULL)
                        put("subtasksJson", task.subtasksJson)
                    }

                    val enc = VaultCryptoEngine.encryptEnvelope(payload.toString(), dataKey)
                    val uploadId = cloudMatch?.id ?: syncId
                    val seqNum = SupabaseKeyManager.nextSequenceNumber(context, uploadId)
                    val signature = if (hmacKey != null) {
                        SupabaseKeyManager.generateSignature(
                            itemId = uploadId,
                            clientSeqNum = seqNum,
                            isDeleted = false,
                            ciphertext = enc.ciphertextBase64,
                            hmacKey = hmacKey
                        )
                    } else ""

                    val success = uploadCloudItem(
                        context = context,
                        endpoint = activeEndpoint,
                        typeColumn = activeTypeColumn,
                        accessToken = currentToken,
                        userId = userId,
                        id = uploadId,
                        type = "OPAQUE",
                        iv = enc.contentIvBase64,
                        salt = enc.keyIvBase64,
                        ciphertext = enc.ciphertextBase64,
                        wrappedKey = enc.wrappedKeyBase64,
                        signature = signature,
                        clientSeqNum = seqNum,
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
                val parts = record.split(":", limit = 2)
                if (parts.size >= 2) {
                    val type = parts[0]
                    val syncId = parts[1]
                    val seqNum = SupabaseKeyManager.nextSequenceNumber(context, syncId)
                    // Tombstone signature uses empty ciphertext sentinel
                    val tombstoneSig = if (hmacKey != null) {
                        SupabaseKeyManager.generateSignature(
                            itemId = syncId,
                            clientSeqNum = seqNum,
                            isDeleted = true,
                            ciphertext = "",
                            hmacKey = hmacKey
                        )
                    } else ""

                    val success = uploadCloudItem(
                        context = context,
                        endpoint = activeEndpoint,
                        typeColumn = activeTypeColumn,
                        accessToken = currentToken,
                        userId = userId,
                        id = syncId,
                        type = "OPAQUE",
                        iv = "",
                        salt = "",
                        ciphertext = "",
                        wrappedKey = "",
                        signature = tombstoneSig,
                        clientSeqNum = seqNum,
                        updatedAt = System.currentTimeMillis(),
                        isDeleted = true
                    )
                    if (success) {
                        successfullySyncedDeletions.add(record)
                        val mappedLocalId = getLocalIdForSyncId(context, userId, type, syncId)
                        unbindSyncId(context, userId, type, mappedLocalId, syncId)
                        if (mappedLocalId != null && type == "TASK") {
                            val taskTsPrefs = context.getSharedPreferences(TASK_TIMESTAMPS_PREFS, Context.MODE_PRIVATE)
                            taskTsPrefs.edit().remove("fp_$mappedLocalId").remove("ts_$mappedLocalId").apply()
                        }
                        uploaded++
                    }
                }
            }

            if (successfullySyncedDeletions.isNotEmpty()) {
                // B2-P3-012 FIX: Re-read the current set before writing back to pick up any
                // new deletion entries added concurrently by the UI thread during this sync cycle.
                // This prevents a read-modify-write race that would silently drop concurrent additions.
                val currentDeletions = deletionPrefs.getStringSet("pending_deletions", emptySet())?.toMutableSet() ?: mutableSetOf()
                currentDeletions.removeAll(successfullySyncedDeletions)
                deletionPrefs.edit().putStringSet("pending_deletions", currentDeletions).commit()
            }

            // 4.1 Process Pending Media Deletions (Photos & Audio Memos in Supabase Storage)
            val pendingMedia = SupabaseStorageEngine.getPendingMediaDeletions(context, userId)
            if (pendingMedia.isNotEmpty() && userId.isNotBlank()) {
                val cloudPathsToDelete = pendingMedia.map { fileName ->
                    if (fileName.contains("/")) fileName else "$userId/$fileName"
                }.filter { it.startsWith("$userId/") }
                if (cloudPathsToDelete.isNotEmpty()) {
                    val mediaDeleteSuccess = SupabaseStorageEngine.deleteMediaBatch(cloudPathsToDelete, currentToken)
                    if (mediaDeleteSuccess) {
                        SupabaseStorageEngine.clearPendingMediaDeletions(context, pendingMedia, userId)
                        Log.d(TAG, "Successfully purged ${pendingMedia.size} deleted media attachments from Supabase Storage.")
                    }
                }
            }

            // 5. Save last sync timestamp
            SupabaseKeyManager.setLastSyncedTime(context, System.currentTimeMillis())

            // Post-sync anomaly check: if more than 40% of notes or tasks vanished in
            // a single sync cycle, something went catastrophically wrong. Abort with an
            // error so the user can investigate rather than silently losing data.
            val postSyncNoteCount = try { noteDao.getAllNotesList().size } catch (_: Exception) { preSyncNoteCount }
            val postSyncTaskCount = try { taskDao.getAllTasksList().size } catch (_: Exception) { preSyncTaskCount }
            val noteDropPct = if (preSyncNoteCount > 5) (preSyncNoteCount - postSyncNoteCount).toFloat() / preSyncNoteCount else 0f
            val taskDropPct = if (preSyncTaskCount > 5) (preSyncTaskCount - postSyncTaskCount).toFloat() / preSyncTaskCount else 0f
            if (noteDropPct > 0.40f || taskDropPct > 0.40f) {
                Log.e(TAG, "SYNC_ANOMALY: Pre-sync notes=$preSyncNoteCount tasks=$preSyncTaskCount, " +
                        "post-sync notes=$postSyncNoteCount tasks=$postSyncTaskCount. " +
                        "Drop exceeds 40% threshold — aborting sync result and rolling back lastSyncedTime.")
                // Capture emergency pre-op snapshot before aborting so user never loses state
                val emergencyFocusDb = (context.applicationContext as? com.focusbyrj.app.FocusApplication)?.database
                com.focusbyrj.app.util.backup.DataSafetyManager.writeEmergencySnapshot(context, noteDao, emergencyFocusDb)
                // Roll back lastSyncedTime so the next sync re-evaluates from the previous safe baseline
                SupabaseKeyManager.setLastSyncedTime(context, session.lastSyncedTime)
                return@withContext Result.failure(
                    Exception("Sync anomaly detected: more than 40% of local data was removed in a single sync. " +
                            "Sync has been paused for safety and an emergency snapshot was created. Please check your data and retry.")
                )
            }

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
            syncMutex.unlock()
        }
    }

    /**
     * Models a raw encrypted item fetched from Supabase.
     *
     * Pattern 1 additions:
     *   - wrappedKey:    The CEK (Content Encryption Key) wrapped with the user's DEK. Null for legacy items.
     *   - signature:     HMAC-SHA256 anti-tamper tag. Empty string for legacy items.
     *   - clientSeqNum:  Monotonically increasing per-item replay-prevention counter.
     */
    data class CloudItem(
        val id: String,
        val type: String,
        val iv: String,
        val salt: String,
        val ciphertext: String,
        val wrappedKey: String?,     // null = legacy item (no envelope wrapping)
        val signature: String,       // empty = legacy item (no HMAC signature)
        val clientSeqNum: Long,      // 0 = legacy item
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
                val refreshedToken = SupabaseAuthManager.refreshSession(context, force = true).getOrNull()
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
        var offset = 0
        val pageSize = 1000

        while (true) {
            var conn: HttpsURLConnection? = null
            try {
                val url = URL("$endpoint?select=*&limit=$pageSize&offset=$offset&order=id.asc")
                conn = (url.openConnection() as HttpsURLConnection).apply {
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
                    val count = array.length()
                    for (i in 0 until count) {
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
                                iv = obj.optString("iv", ""),
                                salt = obj.optString("salt", ""),
                                ciphertext = obj.optString("ciphertext", ""),
                                wrappedKey = obj.optString("wrapped_key", "").takeIf { it.isNotBlank() },
                                signature = obj.optString("signature", ""),
                                clientSeqNum = obj.optLong("client_seq_num", 0L),
                                updatedAt = run {
                                    val raw = obj.opt("updated_at")
                                    when (raw) {
                                        is Number -> raw.toLong()
                                        is String -> raw.toLongOrNull() ?: try {
                                            java.time.Instant.parse(raw).toEpochMilli()
                                        } catch (_: Throwable) {
                                            try {
                                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                                                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                }
                                                sdf.parse(raw.substringBefore("."))?.time ?: System.currentTimeMillis()
                                            } catch (_: Throwable) {
                                                System.currentTimeMillis()
                                            }
                                        }
                                        else -> System.currentTimeMillis()
                                    }
                                },
                                isDeleted = obj.optBoolean("is_deleted", false)
                            )
                        )
                    }
                    // If received less than pageSize, we have fetched all records
                    if (count < pageSize) {
                        break
                    }
                    offset += pageSize
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
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }
        return Result.success(list)
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
        wrappedKey: String = "",
        signature: String = "",
        clientSeqNum: Long = 0L,
        updatedAt: Long,
        isDeleted: Boolean
    ): Boolean {
        val body = JSONObject().apply {
            put("id", id)
            if (!userId.isNullOrBlank()) put("user_id", userId)
            put(typeColumn, type)            // 'OPAQUE' for new items; legacy type for old items
            put("iv", iv)
            put("salt", salt)               // keyIv for new items; plain salt / ENV: for legacy
            put("ciphertext", ciphertext)
            put("wrapped_key", wrappedKey)  // CEK wrapped with DEK (Pattern 1)
            put("signature", signature)     // HMAC-SHA256 anti-tamper tag (Pattern 1)
            put("client_seq_num", clientSeqNum) // replay prevention counter (Pattern 1)
            put("updated_at", updatedAt)
            put("is_deleted", isDeleted)
        }

        var token = accessToken
        var success = executeUpload(endpoint, token, body)
        if (success) return true

        // Try token refresh if unauthorized/forbidden
        val refreshed = SupabaseAuthManager.refreshSession(context, force = true).getOrNull()
        if (refreshed != null) {
            token = refreshed
            success = executeUpload(endpoint, token, body)
        }
        return success
    }

    private fun executeUpload(endpoint: String, token: String, body: JSONObject): Boolean {
        var conn: HttpsURLConnection? = null
        try {
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpsURLConnection).apply {
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
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Decrypts a cloud item payload back to plaintext JSON.
     *
     * Detection order (Pattern 1 backward compatibility):
     *   1. NEW (Pattern 1): wrappedKey is non-null and non-empty → use wrapped_key column + salt as keyIv
     *   2. LEGACY ENV: salt starts with "ENV:" → parse wrappedKey and keyIv from the prefix
     *   3. LEGACY DIRECT: fall back to raw key encryption (no envelope)
     */
    private fun decryptPayload(item: CloudItem, key: ByteArray): String? {
        return try {
            // Pattern 1: new dedicated wrapped_key column
            if (!item.wrappedKey.isNullOrBlank()) {
                val envelopeRes = VaultCryptoEngine.decryptEnvelope(
                    wrappedKeyBase64 = item.wrappedKey,
                    keyIvBase64 = item.salt,       // keyIv is stored in salt column for new items
                    contentIvBase64 = item.iv,
                    ciphertextBase64 = item.ciphertext,
                    dek = key
                )
                if (envelopeRes.isSuccess) return envelopeRes.getOrNull()
            }

            // Legacy: ENV: prefix encoded wrappedKey and keyIv in salt column
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
                    if (envelopeRes.isSuccess) return envelopeRes.getOrNull()
                }
            }

            // Legacy direct encryption with raw key (oldest items)
            if (item.salt.isBlank() || item.salt == "DELETED") return null
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
