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
     * Forensically overwrites file with zeros before deleting to ensure privacy and security.
     */
    fun secureDeleteMediaFile(file: File) {
        if (!file.exists() || !file.isFile) return
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
    fun secureDeleteMediaFile(path: String) {
        try {
            val file = File(path)
            secureDeleteMediaFile(file)
        } catch (_: Exception) {}
    }

    /**
     * Securely deletes all media files attached to a note.
     */
    fun deleteNoteMediaFiles(note: NoteEntity) {
        try {
            note.getImageUris().forEach { path ->
                secureDeleteMediaFile(path)
            }
            note.getAudioUris().forEach { path ->
                secureDeleteMediaFile(path)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting note media files for note ${note.id}", e)
        }
    }

    /**
     * Scans internal app storage (keep_images and keep_audio) and securely wipes any orphaned
     * media files that no longer correspond to any active, archived, or trashed note in Room.
     */
    suspend fun cleanOrphanedMedia(context: Context, noteDao: NoteDao) {
        try {
            val allNotes = noteDao.getAllNotesList()
            val validImagePaths = HashSet<String>()
            val validAudioPaths = HashSet<String>()

            allNotes.forEach { note ->
                note.getImageUris().forEach { validImagePaths.add(it) }
                note.getAudioUris().forEach { validAudioPaths.add(it) }
            }

            // Clean orphaned images
            val imagesDir = File(context.filesDir, "keep_images")
            if (imagesDir.exists() && imagesDir.isDirectory) {
                imagesDir.listFiles()?.forEach { file ->
                    if (file.isFile && !validImagePaths.contains(file.absolutePath)) {
                        // Allow 5 minutes grace period for recently captured or drafted images
                        val ageMs = System.currentTimeMillis() - file.lastModified()
                        if (ageMs > 300_000L) {
                            Log.i(TAG, "Removing orphaned image: ${file.name}")
                            secureDeleteMediaFile(file)
                        }
                    }
                }
            }

            // Clean orphaned audio
            val audioDir = File(context.filesDir, "keep_audio")
            if (audioDir.exists() && audioDir.isDirectory) {
                audioDir.listFiles()?.forEach { file ->
                    if (file.isFile && !validAudioPaths.contains(file.absolutePath)) {
                        val ageMs = System.currentTimeMillis() - file.lastModified()
                        if (ageMs > 300_000L) {
                            Log.i(TAG, "Removing orphaned audio: ${file.name}")
                            secureDeleteMediaFile(file)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean orphaned media", e)
        }
    }
}
