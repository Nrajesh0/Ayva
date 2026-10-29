/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.data.note

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
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
     * Hardened against data loss:
     * - Headroom check ensures disk space before starting.
     * - WAL checkpoint flushes uncommitted frames.
     * - Transactional insert ensures all-or-nothing atomic migration.
     * - Verification compares source vs destination records.
     * - Aborts safely on any failure WITHOUT wiping legacy database.
     */
    fun checkAndMigrateIfLegacyPlaintextExists(context: Context, encryptedDb: NoteDatabase) {
        val prefs = context.getSharedPreferences("focus_migration_prefs", Context.MODE_PRIVATE)
        val dbFile = context.getDatabasePath(PLAINTEXT_DB_NAME)
        val walFile = context.getDatabasePath("${PLAINTEXT_DB_NAME}-wal")
        val shmFile = context.getDatabasePath("${PLAINTEXT_DB_NAME}-shm")

        if (!dbFile.exists() || dbFile.length() == 0L) {
            return
        }

        // If migration was already durably recorded as completed, securely clean residual plaintext files
        if (prefs.getBoolean("notes_legacy_plaintext_migrated", false)) {
            Log.i(TAG, "Legacy notes migration already recorded as completed. Cleaning residual files.")
            secureWipeAndDelete(dbFile)
            secureWipeAndDelete(walFile)
            secureWipeAndDelete(shmFile)
            return
        }

        // Check if file starts with plaintext SQLite header
        if (!isPlaintextSqliteFile(dbFile)) {
            return
        }

        // 1. Check disk headroom (require at least 2x source size + 10MB margin)
        val totalSourceBytes = dbFile.length() + (if (walFile.exists()) walFile.length() else 0L)
        val freeSpace = dbFile.freeSpace
        val minRequiredSpace = (totalSourceBytes * 2) + (10 * 1024 * 1024L)
        if (freeSpace in 1 until minRequiredSpace) {
            Log.e(TAG, "Insufficient disk headroom for safe notes migration: free=$freeSpace, required=$minRequiredSpace. Aborting migration without modifying files.")
            return
        }

        // 2. Safely checkpoint WAL before opening read-only
        if (walFile.exists() && walFile.length() > 0L) {
            try {
                val rwDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.path,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                )
                try {
                    rwDb.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
                } finally {
                    rwDb.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "WAL checkpoint before migration failed or skipped (proceeding read-only)", e)
            }
        }

        Log.i(TAG, "Legacy unencrypted notes database detected. Starting verified migration to SQLCipher vault...")

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
        var sourceCount = 0
        try {
            val tableCheck = rawDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='keep_notes'", null
            )
            val hasTable = tableCheck.use { it.moveToFirst() }
            if (!hasTable) {
                Log.w(TAG, "Legacy database does not contain keep_notes table.")
                return
            }

            val countCursor = rawDb.rawQuery("SELECT COUNT(*) FROM keep_notes", null)
            countCursor.use { if (it.moveToFirst()) sourceCount = it.getInt(0) }

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
                val fontKeyIdx = c.getColumnIndex("fontKey")
                val trashedAtIdx = c.getColumnIndex("trashedAt")
                val deletedAtIdx = c.getColumnIndex("deletedAt")

                while (c.moveToNext()) {
                    val id = if (idIdx != -1) c.getLong(idIdx) else 0L
                    val title = if (titleIdx != -1) c.getString(titleIdx) ?: "" else ""
                    val content = if (contentIdx != -1) c.getString(contentIdx) ?: "" else ""
                    val isChecklist = if (isChecklistIdx != -1) c.getInt(isChecklistIdx) == 1 else false
                    val checklistJson = if (checklistJsonIdx != -1) c.getString(checklistJsonIdx) ?: "[]" else "[]"
                    val colorKey = if (colorKeyIdx != -1) c.getString(colorKeyIdx) ?: "default" else "default"
                    val fontKey = if (fontKeyIdx != -1 && !c.isNull(fontKeyIdx)) c.getString(fontKeyIdx) ?: "default" else "default"
                    val isPinned = if (isPinnedIdx != -1) c.getInt(isPinnedIdx) == 1 else false
                    val isArchived = if (isArchivedIdx != -1) c.getInt(isArchivedIdx) == 1 else false
                    val isTrashed = if (isTrashedIdx != -1) c.getInt(isTrashedIdx) == 1 else false
                    val trashedAt = if (trashedAtIdx != -1 && !c.isNull(trashedAtIdx)) c.getLong(trashedAtIdx) else null
                    val deletedAt = if (deletedAtIdx != -1 && !c.isNull(deletedAtIdx)) c.getLong(deletedAtIdx) else null
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
                            fontKey = fontKey,
                            isPinned = isPinned,
                            isArchived = isArchived,
                            isTrashed = isTrashed,
                            trashedAt = trashedAt,
                            deletedAt = deletedAt,
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
            Log.e(TAG, "Error querying legacy keep_notes table. Aborting migration to prevent data loss.", e)
            return
        } finally {
            try { rawDb.close() } catch (_: Exception) {}
        }

        if (legacyNotes.size != sourceCount) {
            Log.e(TAG, "Read count mismatch: read ${legacyNotes.size} of $sourceCount expected. Aborting migration.")
            return
        }

        try {
            if (legacyNotes.isNotEmpty()) {
                runBlocking(Dispatchers.IO + com.focusbyrj.app.util.backup.MaintenanceGate.inheritedContext()) {
                    encryptedDb.withTransaction {
                        for (note in legacyNotes) {
                            val existing = encryptedDb.noteDao().getNoteByIdSync(note.id)
                            if (existing == null) {
                                encryptedDb.noteDao().insertNote(note)
                            } else if (note.updatedAt > existing.updatedAt) {
                                encryptedDb.noteDao().updateNote(note)
                            }
                        }
                        // Verification: ensure all legacy note IDs are present in encryptedDb
                        for (note in legacyNotes) {
                            val verified = encryptedDb.noteDao().getNoteByIdSync(note.id)
                            if (verified == null) {
                                throw IllegalStateException("Note ID ${note.id} missing from encrypted database after insert")
                            }
                        }
                    }
                }
                Log.i(TAG, "Successfully migrated and verified ${legacyNotes.size} legacy notes in encrypted SQLCipher database.")
            }

            // Durably record migration completion BEFORE cleaning legacy files
            val committed = prefs.edit()
                .putBoolean("notes_legacy_plaintext_migrated", true)
                .putLong("notes_migrated_at", System.currentTimeMillis())
                .commit()
            if (!committed) {
                Log.e(TAG, "Failed to commit migration flag to SharedPreferences. Aborting file cleanup.")
                return
            }

            // Securely wipe and delete legacy plaintext db files after verified migration
            secureWipeAndDelete(dbFile)
            secureWipeAndDelete(walFile)
            secureWipeAndDelete(shmFile)

            Log.i(TAG, "Plaintext database secure zero-wipe and cleanup completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during plaintext to encrypted notes migration. Legacy database preserved intact.", e)
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
