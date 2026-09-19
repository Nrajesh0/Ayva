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
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream

/**
 * Handles seamless one-time migration from an unencrypted Room database to an encrypted SQLCipher database.
 */
object NoteDatabaseMigrationHelper {

    private const val TAG = "NoteDbMigration"
    private const val PLAINTEXT_DB_NAME = "keep_notes.db"
    private const val ENCRYPTED_DB_NAME = "keep_notes_vault.db"
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun getEncryptedDatabaseName(): String = ENCRYPTED_DB_NAME

    /**
     * Checks if a legacy unencrypted database exists and migrates its notes to the encrypted database.
     */
    fun checkAndMigrateIfLegacyPlaintextExists(context: Context, encryptedDb: NoteDatabase) {
        try {
            val dbFile = context.getDatabasePath(PLAINTEXT_DB_NAME)
            if (!dbFile.exists() || dbFile.length() == 0L) {
                return
            }

            // Check if file starts with plaintext SQLite header
            if (!isPlaintextSqliteFile(dbFile)) {
                return
            }

            Log.i(TAG, "Legacy unencrypted notes database detected. Starting raw SQLite migration to SQLCipher vault...")

            val rawDb = try {
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.path,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open legacy database with raw SQLite", e)
                null
            } ?: return

            val legacyNotes = mutableListOf<NoteEntity>()
            try {
                val cursor = rawDb.rawQuery("SELECT * FROM keep_notes", null)
                cursor.use { c ->
                    val idIdx = c.getColumnIndex("id")
                    val titleIdx = c.getColumnIndex("title")
                    val contentIdx = c.getColumnIndex("content")
                    val isChecklistIdx = c.getColumnIndex("isChecklist")
                    val checklistJsonIdx = c.getColumnIndex("checklistJson")
                    val colorKeyIdx = c.getColumnIndex("colorKey")
                    val isPinnedIdx = c.getColumnIndex("isPinned")
                    val isArchivedIdx = c.getColumnIndex("isArchived")
                    val isTrashedIdx = c.getColumnIndex("isTrashed")
                    val labelsJsonIdx = c.getColumnIndex("labelsJson")
                    val imageUrisJsonIdx = c.getColumnIndex("imageUrisJson")
                    val audioUrisJsonIdx = c.getColumnIndex("audioUrisJson")
                    val createdAtIdx = c.getColumnIndex("createdAt")
                    val updatedAtIdx = c.getColumnIndex("updatedAt")

                    while (c.moveToNext()) {
                        val id = if (idIdx != -1) c.getLong(idIdx) else 0L
                        val title = if (titleIdx != -1) c.getString(titleIdx) ?: "" else ""
                        val content = if (contentIdx != -1) c.getString(contentIdx) ?: "" else ""
                        val isChecklist = if (isChecklistIdx != -1) c.getInt(isChecklistIdx) == 1 else false
                        val checklistJson = if (checklistJsonIdx != -1) c.getString(checklistJsonIdx) ?: "[]" else "[]"
                        val colorKey = if (colorKeyIdx != -1) c.getString(colorKeyIdx) ?: "default" else "default"
                        val isPinned = if (isPinnedIdx != -1) c.getInt(isPinnedIdx) == 1 else false
                        val isArchived = if (isArchivedIdx != -1) c.getInt(isArchivedIdx) == 1 else false
                        val isTrashed = if (isTrashedIdx != -1) c.getInt(isTrashedIdx) == 1 else false
                        val labelsJson = if (labelsJsonIdx != -1) c.getString(labelsJsonIdx) ?: "[]" else "[]"
                        val imageUrisJson = if (imageUrisJsonIdx != -1) c.getString(imageUrisJsonIdx) ?: "[]" else "[]"
                        val audioUrisJson = if (audioUrisJsonIdx != -1) c.getString(audioUrisJsonIdx) ?: "[]" else "[]"
                        val createdAt = if (createdAtIdx != -1) c.getLong(createdAtIdx) else System.currentTimeMillis()
                        val updatedAt = if (updatedAtIdx != -1) c.getLong(updatedAtIdx) else System.currentTimeMillis()

                        legacyNotes.add(
                            NoteEntity(
                                id = id,
                                title = title,
                                content = content,
                                isChecklist = isChecklist,
                                checklistJson = checklistJson,
                                colorKey = colorKey,
                                isPinned = isPinned,
                                isArchived = isArchived,
                                isTrashed = isTrashed,
                                labelsJson = labelsJson,
                                imageUrisJson = imageUrisJson,
                                audioUrisJson = audioUrisJson,
                                createdAt = createdAt,
                                updatedAt = updatedAt
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying legacy keep_notes table via raw SQLite", e)
            } finally {
                try { rawDb.close() } catch (_: Exception) {}
            }

            if (legacyNotes.isNotEmpty()) {
                var insertSuccessCount = 0
                runBlocking(Dispatchers.IO) {
                    for (note in legacyNotes) {
                        try {
                            encryptedDb.noteDao().insertNote(note)
                            insertSuccessCount++
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed inserting migrated note ${note.id}", e)
                        }
                    }
                }
                Log.i(TAG, "Successfully migrated $insertSuccessCount of ${legacyNotes.size} legacy notes into encrypted SQLCipher database.")
            }

            // Securely wipe and delete legacy plaintext db files after migration
            val walFile = context.getDatabasePath("${PLAINTEXT_DB_NAME}-wal")
            val shmFile = context.getDatabasePath("${PLAINTEXT_DB_NAME}-shm")
            secureWipeAndDelete(dbFile)
            secureWipeAndDelete(walFile)
            secureWipeAndDelete(shmFile)

            Log.i(TAG, "Plaintext database secure zero-wipe and cleanup completed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during plaintext to encrypted notes migration", e)
        }
    }

    /**
     * Overwrites file contents with zeros before unlinking/deleting, preventing forensic recovery.
     */
    private fun secureWipeAndDelete(file: File) {
        if (!file.exists()) return
        try {
            val length = file.length()
            if (length > 0) {
                java.io.RandomAccessFile(file, "rws").use { raf ->
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
            Log.w(TAG, "Failed to zero-overwrite file ${file.name} before deletion", e)
        } finally {
            file.delete()
        }
    }

    private fun isPlaintextSqliteFile(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(16)
                val read = input.read(buffer)
                if (read == 16) {
                    return buffer.contentEquals(SQLITE_HEADER)
                }
            }
        } catch (_: Exception) {}
        return false
    }
}
