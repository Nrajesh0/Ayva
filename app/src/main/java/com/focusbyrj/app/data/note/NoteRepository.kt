/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.data.note

import android.util.Log
import com.focusbyrj.app.util.backup.DataSafetyManager
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class NoteRepository(
    private val noteDao: NoteDao,
    private val context: android.content.Context? = null
) {

    private val TAG = "NoteRepository"

    fun getActiveNotes(): Flow<List<NoteEntity>> = noteDao.getAllActiveNotes().catch { e ->
        Log.e(TAG, "Error collecting active notes", e)
        emit(emptyList())
    }


    fun getArchivedNotes(): Flow<List<NoteEntity>> = noteDao.getArchivedNotes()
        .map { notes ->
            notes.map { note ->
                if (VaultPayloadEncryptor.isVaultEncrypted(note)) {
                    VaultPayloadEncryptor.decryptNotePayload(note)
                } else {
                    note
                }
            }
        }
        .catch { e ->
            Log.e(TAG, "Error collecting archived notes", e)
            emit(emptyList())
        }

    fun getTrashedNotes(): Flow<List<NoteEntity>> = noteDao.getTrashedNotes()
        .map { notes ->
            notes.map { note ->
                if (VaultPayloadEncryptor.isVaultEncrypted(note)) {
                    VaultPayloadEncryptor.decryptNotePayload(note)
                } else {
                    note
                }
            }
        }
        .catch { e ->
            Log.e(TAG, "Error collecting trashed notes", e)
            emit(emptyList())
        }

    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(query).catch { e ->
        Log.e(TAG, "Error searching notes", e)
        emit(emptyList())
    }

    fun getNoteById(id: Long): Flow<NoteEntity?> = noteDao.getNoteById(id)
        .map { note ->
            if (note != null && VaultPayloadEncryptor.isVaultEncrypted(note)) {
                VaultPayloadEncryptor.decryptNotePayload(note)
            } else {
                note
            }
        }
        .catch { e ->
            Log.e(TAG, "Error getting note by id $id", e)
            emit(null)
        }

    suspend fun getNoteByIdSync(id: Long): NoteEntity? = try {
        val note = noteDao.getNoteByIdSync(id)
        if (note != null && VaultPayloadEncryptor.isVaultEncrypted(note)) {
            VaultPayloadEncryptor.decryptNotePayload(note)
        } else {
            note
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error in getNoteByIdSync $id", e)
        null
    }

    sealed class SaveResult {
        data class Saved(val id: Long) : SaveResult()
        data class Locked(val message: String) : SaveResult()
        data class StorageUnavailable(val cause: Throwable) : SaveResult()
        data class Conflict(val message: String) : SaveResult()
        data class Corrupt(val message: String, val cause: Throwable? = null) : SaveResult()
        data class Failure(val message: String, val cause: Throwable? = null) : SaveResult()
    }

    suspend fun saveNoteResult(note: NoteEntity): SaveResult = try {
        if (note.isArchived && !VaultPayloadEncryptor.isVaultEncrypted(note)) {
            val isLocked = if (context != null) {
                ArchiveVaultSecurity.getVaultStatus(context) == ArchiveVaultSecurity.VaultStatus.ENABLED &&
                        ArchiveVaultSecurity.getActiveVaultSubKey() == null
            } else {
                false
            }
            if (isLocked) {
                SaveResult.Locked("Cannot save archived note in plaintext: Secret Archive Vault is enabled and locked.")
            } else {
                val entityToSave = try {
                    VaultPayloadEncryptor.encryptNotePayload(note, context = context)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    return SaveResult.Corrupt("Failed to encrypt vault note payload", e)
                }
                doSaveEntity(entityToSave)
            }
        } else {
            doSaveEntity(note)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: android.database.sqlite.SQLiteFullException) {
        Log.e(TAG, "Storage full in saveNoteResult", e)
        SaveResult.StorageUnavailable(e)
    } catch (e: Exception) {
        Log.e(TAG, "Error in saveNoteResult", e)
        SaveResult.Failure(e.message ?: "Failed to save note", e)
    }

    private suspend fun doSaveEntity(entity: NoteEntity): SaveResult {
        return if (entity.id == 0L) {
            val newId = noteDao.insertNote(entity)
            if (newId <= 0L) {
                SaveResult.Failure("Failed to insert note into database")
            } else {
                SaveResult.Saved(newId)
            }
        } else {
            val rows = noteDao.updateNote(entity)
            if (rows <= 0) {
                SaveResult.Failure("Note ${entity.id} was not found to update")
            } else {
                SaveResult.Saved(entity.id)
            }
        }
    }

    suspend fun saveNote(note: NoteEntity): Long {
        return when (val result = saveNoteResult(note)) {
            is SaveResult.Saved -> result.id
            is SaveResult.Locked -> throw IllegalStateException(result.message)
            is SaveResult.StorageUnavailable -> throw result.cause
            is SaveResult.Corrupt -> throw IllegalStateException(result.message, result.cause)
            is SaveResult.Conflict -> throw IllegalStateException(result.message)
            is SaveResult.Failure -> throw IllegalStateException(result.message, result.cause)
        }
    }

    suspend fun updateNoteOrder(id: Long, updatedAt: Long) {
        try {
            noteDao.updateNoteOrder(id, updatedAt)
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateNoteOrder", e)
        }
    }

    suspend fun deletePermanently(note: NoteEntity) {
        noteDao.deleteNote(note)
    }

    suspend fun togglePin(id: Long, currentPinned: Boolean) {
        try {
            noteDao.updatePinStatus(id, !currentPinned)
        } catch (e: Exception) {
            Log.e(TAG, "Error in togglePin", e)
        }
    }

    suspend fun setPinned(id: Long, isPinned: Boolean) {
        try {
            noteDao.updatePinStatus(id, isPinned)
        } catch (e: Exception) {
            Log.e(TAG, "Error in setPinned", e)
        }
    }

    suspend fun getAllNotesList(): List<NoteEntity> = try {
        noteDao.getAllNotesList()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.e(TAG, "Error in getAllNotesList", e)
        emptyList()
    }

    /** Destructive cleanup callers must fail closed when the reference set cannot be read. */
    suspend fun getAllNotesListOrThrow(): List<NoteEntity> = noteDao.getAllNotesList()

    suspend fun setArchived(id: Long, isArchived: Boolean) {
        try {
            val existing = noteDao.getNoteByIdSync(id)
            if (existing != null) {
                if (isArchived && !VaultPayloadEncryptor.isVaultEncrypted(existing)) {
                    val encrypted = VaultPayloadEncryptor.encryptNotePayload(existing.copy(isArchived = true), context = context)
                    noteDao.updateNote(encrypted)
                } else if (!isArchived && VaultPayloadEncryptor.isVaultEncrypted(existing)) {
                    // Only unarchive if decryption succeeds. Never leak ciphertext to active notes.
                    val res = VaultPayloadEncryptor.tryDecryptNotePayload(existing)
                    if (res is VaultPayloadEncryptor.DecryptionResult.Success) {
                        noteDao.updateNote(res.note.copy(isArchived = false))
                    } else {
                        Log.w(TAG, "Cannot unarchive note $id: vault is locked or decryption failed ($res)")
                    }
                } else {
                    noteDao.updateArchiveStatus(id, isArchived)
                }
            } else {
                noteDao.updateArchiveStatus(id, isArchived)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setArchived", e)
        }
    }

    suspend fun moveToTrash(id: Long) {
        try {
            noteDao.updateTrashStatus(id, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error in moveToTrash", e)
        }
    }

    suspend fun restoreFromTrash(id: Long) {
        try {
            noteDao.updateTrashStatus(id, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error in restoreFromTrash", e)
        }
    }

    suspend fun setColor(id: Long, colorKey: String) {
        try {
            noteDao.updateColor(id, colorKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error in setColor", e)
        }
    }

    suspend fun setFont(id: Long, fontKey: String) {
        try {
            noteDao.updateFont(id, fontKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error in setFont", e)
        }
    }

    suspend fun getTrashedNotesSync(): List<NoteEntity> = try {
        noteDao.getTrashedNotesSync().map { note ->
            if (VaultPayloadEncryptor.isVaultEncrypted(note)) {
                VaultPayloadEncryptor.decryptNotePayload(note)
            } else {
                note
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error in getTrashedNotesSync", e)
        emptyList()
    }

    suspend fun emptyTrash(context: android.content.Context? = null) {
        val ctx = context ?: this.context
        if (ctx != null) {
            val snapshotPath = DataSafetyManager.writePreOpSnapshot(ctx, noteDao, "emptyTrash")
            if (snapshotPath == null) {
                throw IllegalStateException("Pre-operation snapshot failed; aborting emptyTrash to prevent data loss.")
            }
        } else {
            throw IllegalStateException("emptyTrash requires a valid Context for pre-op safety snapshot.")
        }
        noteDao.emptyTrash()
    }

    suspend fun renameLabel(oldLabel: String, newLabel: String) {
        try {
            val subKey = ArchiveVaultSecurity.getActiveVaultSubKey()
            val allNotes = noteDao.getAllNotesList()
            allNotes.forEach { rawNote ->
                val isEncrypted = VaultPayloadEncryptor.isVaultEncrypted(rawNote)
                val note = if (isEncrypted && subKey != null) {
                    VaultPayloadEncryptor.decryptNotePayload(rawNote, subKey)
                } else {
                    rawNote
                }
                val labels = note.getLabels().toMutableList()
                val index = labels.indexOfFirst { it.equals(oldLabel, ignoreCase = true) }
                if (index != -1) {
                    labels[index] = newLabel.trim()
                    val array = org.json.JSONArray()
                    labels.distinct().forEach { array.put(it) }
                    val updatedPlain = note.copy(labelsJson = array.toString(), updatedAt = System.currentTimeMillis())
                    val finalNote = if (isEncrypted && subKey != null) {
                        VaultPayloadEncryptor.encryptNotePayload(updatedPlain, subKey)
                    } else {
                        updatedPlain
                    }
                    noteDao.updateNote(finalNote)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in renameLabel", e)
        }
    }

    suspend fun deleteLabel(label: String) {
        try {
            val subKey = ArchiveVaultSecurity.getActiveVaultSubKey()
            val allNotes = noteDao.getAllNotesList()
            allNotes.forEach { rawNote ->
                val isEncrypted = VaultPayloadEncryptor.isVaultEncrypted(rawNote)
                val note = if (isEncrypted && subKey != null) {
                    VaultPayloadEncryptor.decryptNotePayload(rawNote, subKey)
                } else {
                    rawNote
                }
                val labels = note.getLabels().toMutableList()
                val removed = labels.removeAll { it.equals(label, ignoreCase = true) }
                if (removed) {
                    val array = org.json.JSONArray()
                    labels.distinct().forEach { array.put(it) }
                    val updatedPlain = note.copy(labelsJson = array.toString(), updatedAt = System.currentTimeMillis())
                    val finalNote = if (isEncrypted && subKey != null) {
                        VaultPayloadEncryptor.encryptNotePayload(updatedPlain, subKey)
                    } else {
                        updatedPlain
                    }
                    noteDao.updateNote(finalNote)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in deleteLabel", e)
        }
    }
}
