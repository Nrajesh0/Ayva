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
                val isUnderDrawings = path.startsWith(File(filesCanonical, "keep_drawings").canonicalPath + File.separator)
                val isUnderCache = path.startsWith(cachePath + File.separator) || path == cachePath

                if (!isUnderImages && !isUnderAudio && !isUnderDrawings && !isUnderCache) {
                    return false
                }
            } else {
                val parentName = canonical.parentFile?.name ?: return false
                if (parentName != "keep_images" && parentName != "keep_audio" && parentName != "keep_drawings") {
                    return false
                }
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Best-effort overwrite for explicit deletion. Flash storage and retained backups
     * prevent a guarantee of forensic erasure.
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
        val key = ArchiveVaultSecurity.getActiveVaultSubKey()
        return try {
            if (allNotes == null) return false
            val referencedElsewhere = allNotes.filter { it.id != note.id }
                .flatMap { MediaRetirement.references(it, key) }.toSet()
            // This call schedules proven retirements. Destruction only happens in the
            // fresh transactional inventory below, never from a caller-owned snapshot.
            MediaRetirement.retire(MediaRetirement.references(note, key) - referencedElsewhere)
        } catch (_: Exception) {
            Log.w(TAG, "Media retirement deferred because references are incomplete")
            false
        } finally {
            key?.fill(0)
        }
    }

    /**
     * Reclaims only explicitly retired authenticated media after a complete transactional
     * reference check. Arbitrary files are never classified as garbage by age.
     */
    suspend fun cleanOrphanedMedia(context: Context, noteDao: NoteDao) {
        // Capture authorization before opening a DB transaction: credential
        // changes can hold the vault monitor while waiting for the DB writer.
        val key = ArchiveVaultSecurity.getActiveVaultSubKey()
        try {
            MediaRetirement.initialize(context)
            // Pending recovery can reference media not yet represented by a committed row.
            if (listOf("pending_vault_draft.enc", "pending_vault_draft.vault").any { name ->
                    listOf("", ".bak", ".new").any { suffix -> File(context.filesDir, name + suffix).exists() }
                }) return
            val candidates = MediaRetirement.candidates(context)
            if (candidates.isEmpty()) return
            noteDao.withMediaReferenceSnapshot { notes ->
                val references = notes.flatMap { MediaRetirement.references(it, key) }.toSet()
                for (path in candidates) {
                    val file = File(path).canonicalFile
                    if (!isAllowedMediaFile(file, context)) continue
                    if (file.path in references) {
                        MediaRetirement.forget(path)
                        continue
                    }
                    // Files without a supported authenticated container remain available
                    // for explicit recovery/review; they are never guessed to be disposable.
                    if (file.exists() && !com.focusbyrj.app.util.crypto.EncryptedMediaStorage.isEncrypted(file)) continue
                    if (MediaRetirement.reclaimIfUnused(file)) MediaRetirement.forget(path)
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            Log.w(TAG, "Media cleanup paused; inventory or references could not be verified. Files retained.")
        } finally {
            key?.fill(0)
        }
    }
}
