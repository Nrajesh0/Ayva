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
import com.focusbyrj.app.util.sync.VaultCryptoEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * High-performance, zero-knowledge media storage engine for Supabase Storage.
 * Handles uploading compressed media attachments (images, audio memos, sketches)
 * with client-side AES-256-GCM encryption, and transparent on-demand downloading.
 */
object SupabaseStorageEngine {

    private const val TAG = "SupabaseStorageEngine"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MEDIA_MANIFEST_PREFS = "focus_media_cloud_manifest"

    /**
     * Binds a local path or filename to a cloud UUID in the local manifest upon successful upload.
     */
    fun bindCloudUuid(context: Context, userId: String, localPath: String, cloudUuid: String) {
        if (userId.isBlank() || localPath.isBlank() || cloudUuid.isBlank()) return
        val prefs = context.getSharedPreferences(MEDIA_MANIFEST_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString("$userId:$localPath", cloudUuid).commit()
    }

    /**
     * Returns the cloud UUID previously mapped to this local path or filename, or null if never uploaded.
     * Performs direct lookup first, and falls back to filename suffix matching to resolve discrepancies.
     */
    fun getCloudUuid(context: Context, userId: String, localPathOrFileName: String): String? {
        if (userId.isBlank() || localPathOrFileName.isBlank()) return null
        val prefs = context.getSharedPreferences(MEDIA_MANIFEST_PREFS, Context.MODE_PRIVATE)
        val direct = prefs.getString("$userId:$localPathOrFileName", null)
        if (!direct.isNullOrBlank()) return direct

        val clean = localPathOrFileName.trim()
        val queryUuid = clean.removeSuffix(".enc")
        val fileName = File(clean).name
        for ((k, v) in prefs.all) {
            if (k.startsWith("$userId:")) {
                if (v is String && v.isNotBlank()) {
                    if (v == queryUuid || v == clean) return v
                    if (k.endsWith("/$fileName") || k.endsWith(":$fileName") || k == "$userId:$clean") {
                        return v
                    }
                }
            }
        }
        return null
    }

    /**
     * Uploads a local media file to the user's private Supabase Storage vault folder.
     * Encrypts the payload with the user's derived raw key before uploading.
     *
     * PATTERN 1: The cloud filename is anonymized — stored as "{UUIDv4}.enc" instead of
     * the original filename. This prevents file type inference and filename-based metadata leakage.
     * A local manifest maps each local path to its stable cloud UUID only after successful upload.
     *
     * @return The cloud relative path (e.g. "userId/{uuid}.enc") or null on failure.
     */
    fun uploadMedia(
        context: Context,
        localPath: String,
        userId: String,
        accessToken: String,
        dataKey: ByteArray?
    ): String? {
        val file = File(localPath)
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "Local file does not exist or is empty: $localPath")
            return null
        }

        if (dataKey == null) {
            Log.e(TAG, "Aborting media upload: zero-knowledge data encryption key is not initialized.")
            return null
        }

        // Anonymised cloud path: reuse existing confirmed UUID or generate fresh candidate (not saved until upload succeeds)
        val cloudUuid = getCloudUuid(context, userId, localPath) ?: UUID.randomUUID().toString()
        val cloudPath = "$userId/$cloudUuid.enc"
        val endpointUrl = "${SupabaseConfig.STORAGE_OBJECT_URL}/${SupabaseConfig.STORAGE_BUCKET}/$cloudPath"

        var rawBytes: ByteArray? = null
        var payloadBytes: ByteArray? = null
        var conn: HttpURLConnection? = null
        return try {
            rawBytes = com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(file) ?: file.readBytes()
            // Fail-closed: Never upload unencrypted media under any circumstances
            payloadBytes = VaultCryptoEngine.encryptBytesWithRawKey(rawBytes, dataKey)

            val url = URL(endpointUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setFixedLengthStreamingMode(payloadBytes.size)
                setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("x-upsert", "true")
            }

            conn.outputStream.use { it.write(payloadBytes) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                bindCloudUuid(context, userId, localPath, cloudUuid)
                Log.d(TAG, "Uploaded encrypted media as anonymized blob: $cloudPath (${payloadBytes.size} bytes)")
                cloudPath
            } else if (responseCode == 409) {
                // Retry with PUT method if POST was rejected with Conflict by storage gateway
                var putConn: HttpURLConnection? = null
                try {
                    putConn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "PUT"
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = READ_TIMEOUT_MS
                        doOutput = true
                        setFixedLengthStreamingMode(payloadBytes.size)
                        setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                        setRequestProperty("Authorization", "Bearer $accessToken")
                        setRequestProperty("Content-Type", "application/octet-stream")
                        setRequestProperty("x-upsert", "true")
                    }
                    putConn.outputStream.use { it.write(payloadBytes) }
                    if (putConn.responseCode in 200..299) {
                        bindCloudUuid(context, userId, localPath, cloudUuid)
                        Log.d(TAG, "Uploaded encrypted media via PUT fallback: $cloudPath")
                        cloudPath
                    } else {
                        val putErr = putConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        Log.e(TAG, "PUT fallback failed for $cloudPath with HTTP ${putConn.responseCode}: $putErr")
                        null
                    }
                } finally {
                    try { putConn?.disconnect() } catch (_: Exception) {}
                }
            } else {
                val errorStream = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Upload failed for $cloudPath with HTTP $responseCode: $errorStream")
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exception during encrypted media upload for $localPath", t)
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
            rawBytes?.let { java.util.Arrays.fill(it, 0.toByte()) }
            payloadBytes?.let { java.util.Arrays.fill(it, 0.toByte()) }
        }
    }

    /**
     * Downloads and decrypts a media attachment from Supabase Storage into the specified
     * local directory (e.g. "keep_images" or "keep_audio").
     *
     * @return The local file's absolute path, or null on failure.
     */
    fun downloadMedia(
        context: Context,
        cloudPath: String,
        subDirName: String,
        accessToken: String,
        dataKey: ByteArray?
    ): String? {
        val clean = cloudPath.trim()
        val rawFileName = clean.substringAfterLast('/')
        val safeFileName = File(rawFileName).name.replace("..", "_").ifBlank { "media_${UUID.randomUUID()}.enc" }
        val targetDir = File(context.filesDir, subDirName).apply { if (!exists()) mkdirs() }
        val targetFile = File(targetDir, safeFileName)

        val session = SupabaseKeyManager.getSessionState(context)
        val userId = session.userId ?: ""

        // If file already exists locally with non-zero size, reuse immediately and ensure manifest is populated
        if (targetFile.exists() && targetFile.length() > 0L) {
            val cloudUuid = if (safeFileName.endsWith(".enc")) safeFileName.removeSuffix(".enc") else null
            if (!cloudUuid.isNullOrBlank() && userId.isNotBlank()) {
                bindCloudUuid(context, userId, targetFile.absolutePath, cloudUuid)
            }
            return targetFile.absolutePath
        }

        // Normalize cloud path: Supabase Storage bucket `vault_media` stores objects under "$userId/$safeFileName".
        // Strip any leaked local Android paths (e.g. /data/user/0/..., keep_images/) to restore the valid cloud object key.
        val normalizedCloudPath = if (userId.isNotBlank()) {
            "$userId/$safeFileName"
        } else if (!clean.startsWith("/") && clean.contains("/") && !clean.contains("keep_images") && !clean.contains("data/")) {
            clean
        } else {
            safeFileName
        }

        val endpointUrl = "${SupabaseConfig.STORAGE_OBJECT_URL}/authenticated/${SupabaseConfig.STORAGE_BUCKET}/$normalizedCloudPath"
        val fallbackUrl = "${SupabaseConfig.STORAGE_OBJECT_URL}/${SupabaseConfig.STORAGE_BUCKET}/$normalizedCloudPath"

        fun attemptDownload(urlStr: String): ByteArray? {
            var conn: HttpURLConnection? = null
            return try {
                val url = URL(urlStr)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { it.readBytes() }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }

        val downloadedBytes = attemptDownload(endpointUrl)
            ?: attemptDownload(fallbackUrl)
            ?: if (normalizedCloudPath != clean && !clean.startsWith("/") && !clean.contains("keep_images")) {
                attemptDownload("${SupabaseConfig.STORAGE_OBJECT_URL}/authenticated/${SupabaseConfig.STORAGE_BUCKET}/$clean")
            } else null
        if (downloadedBytes == null || downloadedBytes.isEmpty()) {
            Log.w(TAG, "Could not download media file: $cloudPath")
            return null
        }

        if (dataKey == null) {
            Log.e(TAG, "Cannot decrypt downloaded media: missing zero-knowledge data key")
            return null
        }

        return try {
            val decResult = VaultCryptoEngine.decryptBytesWithRawKey(downloadedBytes, dataKey)
            if (decResult.isFailure) {
                Log.e(TAG, "Decryption authentication failed for downloaded media: $cloudPath")
                return null
            }
            val decryptedBytes = decResult.getOrThrow()

            try {
                com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(targetFile, decryptedBytes)
                // Bind downloaded file path to cloud UUID in manifest to avoid re-uploading duplicate copies on sync
                val cloudUuid = if (safeFileName.endsWith(".enc")) safeFileName.removeSuffix(".enc") else null
                if (!cloudUuid.isNullOrBlank()) {
                    val session = SupabaseKeyManager.getSessionState(context)
                    val userId = session.userId ?: ""
                    if (userId.isNotBlank()) {
                        val manifestPrefs = context.getSharedPreferences(MEDIA_MANIFEST_PREFS, Context.MODE_PRIVATE)
                        manifestPrefs.edit().putString("$userId:${targetFile.absolutePath}", cloudUuid).apply()
                    }
                }
                Log.d(TAG, "Successfully downloaded and decrypted media to encrypted storage: ${targetFile.absolutePath}")
                targetFile.absolutePath
            } finally {
                java.util.Arrays.fill(decryptedBytes, 0.toByte())
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed writing decrypted media file", t)
            try { if (targetFile.exists()) targetFile.delete() } catch (_: Exception) {}
            null
        } finally {
            java.util.Arrays.fill(downloadedBytes, 0.toByte())
        }
    }

    private const val MEDIA_DELETIONS_PREFS = "focus_supabase_media_deletions"
    private const val KEY_PENDING_MEDIA = "pending_media_deletions"

    /**
     * Records a deleted media file path or cloud reference into the pending queue for cloud storage cleanup.
     * Resolves local paths to their anonymized cloud path ("$userId/$uuid.enc") to prevent storage leakage.
     */
    fun recordPendingMediaDeletion(context: Context, pathOrFileName: String, explicitUserId: String? = null) {
        val clean = pathOrFileName.trim()
        if (clean.isBlank()) return
        val cleanName = clean.substringAfterLast('/')
        val session = SupabaseKeyManager.getSessionState(context)
        val userId = explicitUserId?.takeIf { it.isNotBlank() } ?: session.userId ?: ""

        val isLocalPath = clean.startsWith("/") || clean.startsWith("file:") || clean.startsWith("content:")
        val cloudPath = when {
            // Already a relative cloud path formatted as "$userId/uuid.enc"
            !isLocalPath && userId.isNotBlank() && clean.startsWith("$userId/") && clean.endsWith(".enc") -> clean
            // Already a relative cloud path with another user subpath: "someUser/uuid.enc"
            !isLocalPath && clean.contains("/") && clean.endsWith(".enc") -> clean
            // Raw cloud filename without userId prefix: "uuid.enc"
            !isLocalPath && userId.isNotBlank() && !clean.contains("/") && clean.endsWith(".enc") -> "$userId/$clean"
            // Local file path or local filename: perform manifest lookup if user is signed in
            userId.isNotBlank() -> {
                val mappedUuid = getCloudUuid(context, userId, clean)
                if (mappedUuid != null) {
                    "$userId/$mappedUuid.enc"
                } else if (cleanName.endsWith(".enc")) {
                    "$userId/$cleanName"
                } else {
                    cleanName
                }
            }
            else -> cleanName
        }

        val prefs = context.getSharedPreferences(MEDIA_DELETIONS_PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_PENDING_MEDIA, emptySet()) ?: emptySet()
        val updated = current.toMutableSet().apply { add(cloudPath) }
        prefs.edit().putStringSet(KEY_PENDING_MEDIA, updated).commit()
    }

    /**
     * Records all media attachments (images and audio) associated with a note for cloud storage deletion.
     */
    fun recordNoteMediaDeletions(context: Context, note: com.focusbyrj.app.data.note.NoteEntity) {
        val session = SupabaseKeyManager.getSessionState(context)
        val userId = session.userId ?: ""
        val images = note.getImageUris()
        val audio = note.getAudioUris()
        if (images.isEmpty() && audio.isEmpty()) return

        images.forEach { path ->
            recordPendingMediaDeletion(context, path, userId)
        }
        audio.forEach { path ->
            recordPendingMediaDeletion(context, path, userId)
        }
    }

    /**
     * Retrieves all media file names pending deletion from Supabase Storage.
     */
    fun getPendingMediaDeletions(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(MEDIA_DELETIONS_PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PENDING_MEDIA, emptySet()) ?: emptySet()
    }

    /**
     * Clears successfully deleted media files from the pending deletion queue.
     */
    fun clearPendingMediaDeletions(context: Context, successfullyDeleted: Set<String>) {
        if (successfullyDeleted.isEmpty()) return
        val prefs = context.getSharedPreferences(MEDIA_DELETIONS_PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_PENDING_MEDIA, emptySet()) ?: emptySet()
        val updated = current.toMutableSet().apply { removeAll(successfullyDeleted) }
        prefs.edit().putStringSet(KEY_PENDING_MEDIA, updated).commit()
    }

    /**
     * Removes multiple objects from Supabase Storage in a single batch request.
     * Includes fallback for Android runtimes where HttpURLConnection DELETE does not permit writing request bodies.
     */
    fun deleteMediaBatch(
        cloudPaths: List<String>,
        accessToken: String
    ): Boolean {
        if (cloudPaths.isEmpty()) return true
        val endpointUrl = "${SupabaseConfig.STORAGE_OBJECT_URL}/${SupabaseConfig.STORAGE_BUCKET}"
        val body = JSONObject().apply {
            val prefixes = JSONArray().apply {
                cloudPaths.distinct().forEach { put(it) }
            }
            put("prefixes", prefixes)
        }
        val bodyBytes = body.toString().toByteArray(Charsets.UTF_8)

        fun executeDelete(method: String, overrideHeader: Boolean): Boolean {
            var conn: HttpURLConnection? = null
            return try {
                val url = URL(endpointUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    if (overrideHeader) {
                        setRequestProperty("X-HTTP-Method-Override", "DELETE")
                    }
                }

                conn.outputStream.use { it.write(bodyBytes) }
                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    Log.d(TAG, "Successfully deleted batch cloud media: $cloudPaths")
                    true
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.e(TAG, "Batch delete failed with HTTP $responseCode: $err")
                    false
                }
            } catch (e: java.net.ProtocolException) {
                if (method == "DELETE" && !overrideHeader) {
                    Log.w(TAG, "HttpURLConnection DELETE does not support body on this runtime; retrying with POST + X-HTTP-Method-Override: DELETE")
                    executeDelete("POST", true)
                } else {
                    Log.e(TAG, "ProtocolException deleting batch cloud media $cloudPaths", e)
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception deleting batch cloud media $cloudPaths", e)
                false
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }

        return executeDelete("DELETE", false)
    }

    /**
     * Removes an object from Supabase Storage when a user permanently deletes an attachment.
     */
    fun deleteMedia(
        cloudPath: String,
        accessToken: String
    ): Boolean {
        return deleteMediaBatch(listOf(cloudPath), accessToken)
    }
}
