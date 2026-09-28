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

package com.focusbyrj.app.data.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AyvaChatDao {

    @Query("SELECT * FROM ayva_chat_messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<AyvaChatMessageEntity>>

    @Query("SELECT * FROM ayva_chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesSync(): List<AyvaChatMessageEntity>

    @Query("SELECT * FROM ayva_chat_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): AyvaChatMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AyvaChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<AyvaChatMessageEntity>)

    @Query("DELETE FROM ayva_chat_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM ayva_chat_messages")
    suspend fun clearAllMessages()

    @androidx.room.Transaction
    suspend fun syncAllMessages(messages: List<AyvaChatMessageEntity>) {
        clearAllMessages()
        if (messages.isNotEmpty()) {
            insertMessages(messages)
        }
    }

    @Query("DELETE FROM ayva_chat_messages WHERE id IN (SELECT id FROM ayva_chat_messages ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldestMessages(count: Int)

    @Query("SELECT COUNT(*) FROM ayva_chat_messages")
    suspend fun getMessageCount(): Int

    // In-flight interactive drill queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActiveDrill(drill: ActiveDrillStateEntity)

    @Query("SELECT * FROM active_drill_state ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestActiveDrill(): ActiveDrillStateEntity?

    @Query("SELECT * FROM active_drill_state WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getActiveDrill(sessionId: String): ActiveDrillStateEntity?

    @Query("DELETE FROM active_drill_state WHERE sessionId = :sessionId")
    suspend fun deleteActiveDrill(sessionId: String)

    @Query("DELETE FROM active_drill_state")
    suspend fun clearActiveDrills()
}
