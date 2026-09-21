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

/**
 * High-performance, zero-knowledge media storage engine for Supabase Storage.
 * Handles uploading compressed media attachments (images, audio memos, sketches)
 * with client-side AES-256-GCM encryption, and transparent on-demand downloading.
 */
object SupabaseStorageEngine {

    private const val TAG = "SupabaseStorageEngine"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /**
     * Uploads a local media file to the user's private Supabase Storage vault folder.
     * Encrypts the payload with the user's derived raw key before uploading so that
     * zero raw image data is stored unencrypted on the cloud.
     *
     * @return The cloud relative path (e.g. "userId/img_12345.jpg") or null on failure.
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

        val fileName = file.name
        val cloudPath = "$userId/$fileName"
        val endpointUrl = "${SupabaseConfig.STORAGE_OBJECT_URL}/${SupabaseConfig.STORAGE_BUCKET}/$cloudPath"

        return try {
            val rawBytes = com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(file) ?: file.readBytes()
            // Fail-closed: Never upload unencrypted media under any circumstances
            val payloadBytes = VaultCryptoEngine.encryptBytesWithRawKey(rawBytes, dataKey)

            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("x-upsert", "true")
            }

            conn.outputStream.use { it.write(payloadBytes) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                Log.d(TAG, "Successfully uploaded encrypted media attachment: $cloudPath (${payloadBytes.size} bytes)")
                cloudPath
            } else {
                val errorStream = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Upload failed for $cloudPath with HTTP $responseCode: $errorStream")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during encrypted media upload for $localPath", e)
            null
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
        val fileName = cloudPath.substringAfterLast('/')
        val targetDir = File(context.filesDir, subDirName).apply { if (!exists()) mkdirs() }
        val targetFile = File(targetDir, fileName)

        // If file already exists locally with non-zero size, reuse immediately
        if (targetFile.exists() && targetFile.length() > 0L) {
            return targetFile.absolutePath
        }

        val endpointUrl = "${SupabaseConfig.STORAGE_OBJECT_URL}/authenticated/${SupabaseConfig.STORAGE_BUCKET}/$cloudPath"
        val fallbackUrl = "${SupabaseConfig.STORAGE_OBJECT_URL}/${SupabaseConfig.STORAGE_BUCKET}/$cloudPath"

        fun attemptDownload(urlStr: String): ByteArray? {
            return try {
                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
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
            } catch (_: Exception) {
                null
            }
        }

        val downloadedBytes = attemptDownload(endpointUrl) ?: attemptDownload(fallbackUrl)
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

            com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(targetFile, decryptedBytes)
            Log.d(TAG, "Successfully downloaded and decrypted media to encrypted storage: ${targetFile.absolutePath}")
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing decrypted media file", e)
            null
        }
    }

    /**
     * Removes an object from Supabase Storage when a user permanently deletes an attachment.
     */
    fun deleteMedia(
        cloudPath: String,
        accessToken: String
    ): Boolean {
        val endpointUrl = "${SupabaseConfig.STORAGE_OBJECT_URL}/${SupabaseConfig.STORAGE_BUCKET}"
        return try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject().apply {
                val prefixes = JSONArray().apply { put(cloudPath) }
                put("prefixes", prefixes)
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Exception deleting cloud media $cloudPath", e)
            false
        }
    }
}
