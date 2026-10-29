/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.data.note

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * Utility for handling secure media deletion and orphan cleanup across notes.
 */
object NoteMediaManager {

    private const val TAG = "NoteMediaManager"

    /**
     * Verifies that the target file resides within legitimate media storage directories
     * (keep_images, keep_audio, or cache) and is not traversing into sensitive database or preference folders.
     */
    fun isAllowedMediaFile(file: File, context: Context? = null): Boolean {
        return try {
            val canonical = file.canonicalFile
            val path = canonical.absolutePath

            // Strict blacklist against databases, shared_prefs, or system files
            if (path.contains("/databases") || path.contains("\\databases") ||
                path.contains("/shared_prefs") || path.contains("\\shared_prefs") ||
                path.endsWith(".db") || path.endsWith(".db-wal") || path.endsWith(".db-shm") ||
                path.endsWith(".xml")) {
                return false
            }

            if (context != null) {
                val filesCanonical = context.filesDir.canonicalFile
                val cacheCanonical = context.cacheDir.canonicalFile
                val keepImagesDir = File(filesCanonical, "keep_images").canonicalPath
                val keepAudioDir = File(filesCanonical, "keep_audio").canonicalPath
                val cachePath = cacheCanonical.canonicalPath

                val isUnderImages = path.startsWith(keepImagesDir + File.separator) || path == keepImagesDir
                val isUnderAudio = path.startsWith(keepAudioDir + File.separator) || path == keepAudioDir
                val isUnderCache = path.startsWith(cachePath + File.separator) || path == cachePath

                if (!isUnderImages && !isUnderAudio && !isUnderCache) {
                    return false
                }
            } else {
                val parentName = canonical.parentFile?.name ?: return false
                if (parentName != "keep_images" && parentName != "keep_audio") {
                    return false
                }
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Forensically overwrites file with zeros before deleting to ensure privacy and security.
     */
    fun secureDeleteMediaFile(file: File, context: Context? = null): Unit = com.focusbyrj.app.util.backup.MaintenanceGate.write {
        if (!file.exists() || !file.isFile) return@write
        if (!isAllowedMediaFile(file, context)) {
            Log.w(TAG, "Refusing to delete unverified or restricted media path: ${file.path}")
            return@write
        }
        try {
            val length = file.length()
            if (length > 0) {
                RandomAccessFile(file, "rws").use { raf ->
                    val buffer = ByteArray(4096)
                    var remaining = length
                    while (remaining > 0) {
                        val toWrite = minOf(remaining, buffer.size.toLong()).toInt()
                        raf.write(buffer, 0, toWrite)
                        remaining -= toWrite
                    }
                    raf.fd.sync()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Zero-overwrite failed for file ${file.name}, proceeding with unlink", e)
        } finally {
            file.delete()
        }
    }

    /**
     * Deletes media by file path with secure wipe.
     */
    fun secureDeleteMediaFile(path: String, context: Context? = null) {
        try {
            val file = File(path)
            secureDeleteMediaFile(file, context)
        } catch (_: Exception) {}
    }

    /**
     * Securely deletes all media files attached to a note, including header images, voice memos,
     * and embedded Notesnook block images/attachments.
     */
    fun deleteNoteMediaFiles(note: NoteEntity, allNotes: List<NoteEntity>? = null): Boolean {
        return try {
            // Physical deletion is safe only with a complete, readable reference set.
            if (allNotes == null) {
                Log.w(TAG, "Refusing media deletion for note ${note.id}: complete note set was not supplied")
                return false
            }
            val target = decryptForReferenceScan(note) ?: return false
            val otherReferencedPaths = HashSet<String>()
            allNotes.filter { it.id != note.id }.forEach { rawOther ->
                val other = decryptForReferenceScan(rawOther) ?: run {
                    Log.w(TAG, "Refusing media deletion: note ${rawOther.id} references cannot be read")
                    return false
                }
                collectReferences(other).forEach { addPathVariants(otherReferencedPaths, it) }
            }

            collectReferences(target).forEach { path ->
                val variants = HashSet<String>().also { addPathVariants(it, path) }
                if (variants.none { it in otherReferencedPaths }) secureDeleteMediaFile(path)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting note media files for note ${note.id}", e)
            false
        }
    }

    private fun decryptForReferenceScan(note: NoteEntity): NoteEntity? {
        if (!com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(note)) return note
        val key = ArchiveVaultSecurity.getActiveVaultSubKey() ?: return null
        return when (val result = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.tryDecryptNotePayload(note, key)) {
            is com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.DecryptionResult.Success -> result.note
            else -> {
                Log.w(TAG, "Could not decrypt note ${note.id} while building media reference set: $result")
                null
            }
        }
    }

    private fun collectReferences(note: NoteEntity): Set<String> {
        val paths = linkedSetOf<String>()
        paths.addAll(note.getImageUris())
        paths.addAll(note.getAudioUris())
        if (note.content.contains(com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager.BLOCKS_PREFIX)) {
            com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager.parse(note.content).forEach { block ->
                when (block) {
                    is com.focusbyrj.app.ui.screens.notes.NotesnookBlock.Image -> block.uri.takeIf { it.isNotBlank() }?.let(paths::add)
                    is com.focusbyrj.app.ui.screens.notes.NotesnookBlock.Attachment -> block.uri.takeIf { it.isNotBlank() }?.let(paths::add)
                    else -> Unit
                }
            }
        }
        return paths
    }

    private fun addPathVariants(paths: MutableSet<String>, path: String) {
        paths.add(path)
        try { paths.add(File(path).canonicalPath) } catch (_: Exception) {}
    }

    /**
     * Scans internal app storage (keep_images and keep_audio) and securely wipes any orphaned
     * media files that no longer correspond to any active, archived, or trashed note in Room.
     */
    suspend fun cleanOrphanedMedia(context: Context, noteDao: NoteDao) {
        try {
            // Guard: If Secret Vault is enabled and currently locked, we cannot decrypt note
            // envelopes to read their media attachments. Abort cleanup completely to prevent
            // falsely destroying photos and voice recordings that belong to secret notes.
            val vaultStatus = ArchiveVaultSecurity.getVaultStatus(context)
            val activeSubKey = ArchiveVaultSecurity.getActiveVaultSubKey()
            if (vaultStatus == ArchiveVaultSecurity.VaultStatus.ENABLED && activeSubKey == null) {
                Log.w(TAG, "Secret Vault is locked. Aborting orphaned media cleanup to protect encrypted vault attachments.")
                return
            }

            val allNotes = noteDao.getAllNotesList()
            val validImagePaths = HashSet<String>()
            val validAudioPaths = HashSet<String>()

            for (rawNote in allNotes) {
                val note = if (com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(rawNote)) {
                    if (activeSubKey != null) {
                        val decrypted = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.decryptNotePayload(rawNote, activeSubKey)
                        if (com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(decrypted)) {
                            Log.w(TAG, "Note ${rawNote.id} payload could not be decrypted. Aborting orphan cleanup to protect attachments.")
                            return
                        }
                        decrypted
                    } else {
                        Log.w(TAG, "Unreadable note ${rawNote.id} without active subkey. Aborting orphan cleanup.")
                        return
                    }
                } else {
                    rawNote
                }
                note.getImageUris().forEach { path ->
                    validImagePaths.add(path)
                    try { validImagePaths.add(File(path).canonicalPath) } catch (_: Exception) {}
                }
                note.getAudioUris().forEach { path ->
                    validAudioPaths.add(path)
                    try { validAudioPaths.add(File(path).canonicalPath) } catch (_: Exception) {}
                }

                // Collect media referenced inside embedded Notesnook blocks
                if (note.content.contains(com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager.BLOCKS_PREFIX)) {
                    try {
                        val blocks = com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager.parse(note.content)
                        for (b in blocks) {
                            when (b) {
                                is com.focusbyrj.app.ui.screens.notes.NotesnookBlock.Image -> {
                                    if (b.uri.isNotBlank()) {
                                        validImagePaths.add(b.uri)
                                        try { validImagePaths.add(File(b.uri).canonicalPath) } catch (_: Exception) {}
                                    }
                                }
                                is com.focusbyrj.app.ui.screens.notes.NotesnookBlock.Attachment -> {
                                    if (b.uri.isNotBlank()) {
                                        validImagePaths.add(b.uri)
                                        validAudioPaths.add(b.uri)
                                        try {
                                            val c = File(b.uri).canonicalPath
                                            validImagePaths.add(c)
                                            validAudioPaths.add(c)
                                        } catch (_: Exception) {}
                                    }
                                }
                                else -> {}
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Clean orphaned images
            val imagesDir = File(context.filesDir, "keep_images")
            if (imagesDir.exists() && imagesDir.isDirectory) {
                imagesDir.listFiles()?.forEach { file ->
                    val abs = file.absolutePath
                    val can = try { file.canonicalPath } catch (_: Exception) { abs }
                    if (file.isFile && !validImagePaths.contains(abs) && !validImagePaths.contains(can)) {
                        // Allow 5 minutes grace period for recently captured or drafted images
                        val ageMs = System.currentTimeMillis() - file.lastModified()
                        if (ageMs > 300_000L) {
                            Log.i(TAG, "Removing orphaned image: ${file.name}")
                            secureDeleteMediaFile(file, context)
                        }
                    }
                }
            }

            // Clean orphaned audio
            val audioDir = File(context.filesDir, "keep_audio")
            if (audioDir.exists() && audioDir.isDirectory) {
                audioDir.listFiles()?.forEach { file ->
                    val abs = file.absolutePath
                    val can = try { file.canonicalPath } catch (_: Exception) { abs }
                    if (file.isFile && !validAudioPaths.contains(abs) && !validAudioPaths.contains(can)) {
                        val ageMs = System.currentTimeMillis() - file.lastModified()
                        if (ageMs > 300_000L) {
                            Log.i(TAG, "Removing orphaned audio: ${file.name}")
                            secureDeleteMediaFile(file, context)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean orphaned media", e)
        }
    }
}
