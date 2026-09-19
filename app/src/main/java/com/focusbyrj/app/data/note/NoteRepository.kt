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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

class NoteRepository(private val noteDao: NoteDao) {

    private val TAG = "NoteRepository"

    fun getActiveNotes(): Flow<List<NoteEntity>> = noteDao.getAllActiveNotes().catch { e ->
        Log.e(TAG, "Error collecting active notes", e)
        emit(emptyList())
    }

    fun getArchivedNotes(): Flow<List<NoteEntity>> = noteDao.getArchivedNotes().catch { e ->
        Log.e(TAG, "Error collecting archived notes", e)
        emit(emptyList())
    }

    fun getTrashedNotes(): Flow<List<NoteEntity>> = noteDao.getTrashedNotes().catch { e ->
        Log.e(TAG, "Error collecting trashed notes", e)
        emit(emptyList())
    }

    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(query).catch { e ->
        Log.e(TAG, "Error searching notes", e)
        emit(emptyList())
    }

    fun getNoteById(id: Long): Flow<NoteEntity?> = noteDao.getNoteById(id).catch { e ->
        Log.e(TAG, "Error getting note by id $id", e)
        emit(null)
    }

    suspend fun getNoteByIdSync(id: Long): NoteEntity? = try {
        noteDao.getNoteByIdSync(id)
    } catch (e: Exception) {
        Log.e(TAG, "Error in getNoteByIdSync $id", e)
        null
    }

    suspend fun saveNote(note: NoteEntity): Long = try {
        if (note.id == 0L) {
            noteDao.insertNote(note)
        } else {
            noteDao.updateNote(note)
            note.id
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
            noteDao.updateArchiveStatus(id, isArchived)
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
        noteDao.getTrashedNotesSync()
    } catch (e: Exception) {
        Log.e(TAG, "Error in getTrashedNotesSync", e)
        emptyList()
    }

    suspend fun emptyTrash() {
        try {
            noteDao.emptyTrash()
        } catch (e: Exception) {
            Log.e(TAG, "Error in emptyTrash", e)
        }
    }

    suspend fun renameLabel(oldLabel: String, newLabel: String) {
        try {
            val allNotes = noteDao.getAllNotesList()
            allNotes.forEach { note ->
                val labels = note.getLabels().toMutableList()
                val index = labels.indexOfFirst { it.equals(oldLabel, ignoreCase = true) }
                if (index != -1) {
                    labels[index] = newLabel.trim()
                    val array = org.json.JSONArray()
                    labels.distinct().forEach { array.put(it) }
                    noteDao.updateNote(note.copy(labelsJson = array.toString(), updatedAt = System.currentTimeMillis()))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in renameLabel", e)
        }
    }

    suspend fun deleteLabel(label: String) {
        try {
            val allNotes = noteDao.getAllNotesList()
            allNotes.forEach { note ->
                val labels = note.getLabels().toMutableList()
                val removed = labels.removeAll { it.equals(label, ignoreCase = true) }
                if (removed) {
                    val array = org.json.JSONArray()
                    labels.distinct().forEach { array.put(it) }
                    noteDao.updateNote(note.copy(labelsJson = array.toString(), updatedAt = System.currentTimeMillis()))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in deleteLabel", e)
        }
    }
}
