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

    fun getAllArticles(): Flow<List<NoteEntity>> = noteDao.getAllArticles().catch { e ->
        Log.e(TAG, "Error collecting articles", e)
        emit(emptyList())
    }

    fun getDraftArticles(): Flow<List<NoteEntity>> = noteDao.getDraftArticles().catch { e ->
        Log.e(TAG, "Error collecting draft articles", e)
        emit(emptyList())
    }

    fun getPublishedArticles(): Flow<List<NoteEntity>> = noteDao.getPublishedArticles().catch { e ->
        Log.e(TAG, "Error collecting published articles", e)
        emit(emptyList())
    }

    fun searchArticles(query: String): Flow<List<NoteEntity>> = noteDao.searchArticles(query).catch { e ->
        Log.e(TAG, "Error searching articles", e)
        emit(emptyList())
    }

    suspend fun updateArticlePublishStatus(id: Long, isPublished: Boolean, publishedAt: Long? = if (isPublished) System.currentTimeMillis() else null) {
        try {
            noteDao.updateArticlePublishStatus(id, isPublished, publishedAt)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating publish status for article $id", e)
        }
    }

    suspend fun convertNoteToArticle(id: Long, isArticle: Boolean) {
        try {
            noteDao.updateArticleType(id, isArticle)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling article type for note $id", e)
        }
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

    suspend fun saveNote(note: NoteEntity): Long = try {
        val entityToSave = if (note.isArchived && !VaultPayloadEncryptor.isVaultEncrypted(note)) {
            VaultPayloadEncryptor.encryptNotePayload(note)
        } else {
            note
        }

        if (entityToSave.id == 0L) {
            noteDao.insertNote(entityToSave)
        } else {
            noteDao.updateNote(entityToSave)
            entityToSave.id
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error in saveNote", e)
        0L
    }

    suspend fun updateNoteOrder(id: Long, updatedAt: Long) {
        try {
            noteDao.updateNoteOrder(id, updatedAt)
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateNoteOrder", e)
        }
    }

    suspend fun deletePermanently(note: NoteEntity) {
        try {
            noteDao.deleteNote(note)
        } catch (e: Exception) {
            Log.e(TAG, "Error in deletePermanently", e)
        }
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

    suspend fun setArchived(id: Long, isArchived: Boolean) {
        try {
            val existing = noteDao.getNoteByIdSync(id)
            if (existing != null) {
                if (isArchived && !VaultPayloadEncryptor.isVaultEncrypted(existing)) {
                    val encrypted = VaultPayloadEncryptor.encryptNotePayload(existing.copy(isArchived = true))
                    noteDao.updateNote(encrypted)
                } else if (!isArchived && VaultPayloadEncryptor.isVaultEncrypted(existing)) {
                    // B1-F-019 FIX: Only unarchive if decryption succeeds. Never leak ciphertext to active notes.
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
        try {
            val ctx = context ?: this.context
            if (ctx != null) {
                DataSafetyManager.writePreOpSnapshot(ctx, noteDao, "emptyTrash")
            }
            noteDao.emptyTrash()
        } catch (e: Exception) {
            Log.e(TAG, "Error in emptyTrash", e)
        }
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
