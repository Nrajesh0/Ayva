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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM keep_notes WHERE isTrashed = 0 AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM keep_notes WHERE isTrashed = 0 AND isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM keep_notes WHERE isTrashed = 1 ORDER BY updatedAt DESC")
    fun getTrashedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM keep_notes WHERE id = :id")
    fun getNoteById(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM keep_notes WHERE id = :id")
    suspend fun getNoteByIdSync(id: Long): NoteEntity?

    @Query("SELECT * FROM keep_notes WHERE isTrashed = 0 AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAllActiveNotesSync(): List<NoteEntity>

    @Query("SELECT * FROM keep_notes WHERE isTrashed = 0 AND isArchived = 0 AND isChecklist = 1 ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getChecklistNotesSync(): List<NoteEntity>

    @Query("SELECT * FROM keep_notes WHERE isTrashed = 0 AND isArchived = 0 AND isChecklist = 0 ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getTextNotesSync(): List<NoteEntity>

    @Query("SELECT * FROM keep_notes WHERE isTrashed = 0 AND isArchived = 0 AND isPinned = 1 ORDER BY updatedAt DESC")
    suspend fun getPinnedNotesSync(): List<NoteEntity>

    @Query("""
        SELECT * FROM keep_notes 
        WHERE isTrashed = 0 AND isArchived = 0 
        AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR checklistJson LIKE '%' || :query || '%' OR labelsJson LIKE '%' || :query || '%')
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Query("""
        SELECT * FROM keep_notes 
        WHERE isTrashed = 0 AND isArchived = 0 
        AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR checklistJson LIKE '%' || :query || '%' OR labelsJson LIKE '%' || :query || '%')
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    suspend fun searchNotesSync(query: String): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("UPDATE keep_notes SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateNoteOrder(id: Long, updatedAt: Long)

    @Query("UPDATE keep_notes SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePinStatus(id: Long, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE keep_notes SET isArchived = :isArchived, isPinned = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateArchiveStatus(id: Long, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE keep_notes SET isTrashed = :isTrashed, isPinned = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTrashStatus(id: Long, isTrashed: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE keep_notes SET colorKey = :colorKey, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateColor(id: Long, colorKey: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE keep_notes SET fontKey = :fontKey, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFont(id: Long, fontKey: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM keep_notes WHERE isTrashed = 1")
    suspend fun getTrashedNotesSync(): List<NoteEntity>

    @Query("DELETE FROM keep_notes WHERE isTrashed = 1")
    suspend fun emptyTrash()

    @Query("SELECT * FROM keep_notes")
    suspend fun getAllNotesList(): List<NoteEntity>

    @Query("DELETE FROM keep_notes")
    suspend fun deleteAllNotes()
}
