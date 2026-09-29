/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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

    @Query("DELETE FROM ayva_chat_messages WHERE id IN (SELECT id FROM ayva_chat_messages ORDER BY timestamp ASC LIMIT MAX(0, :count))")
    suspend fun deleteOldestMessagesInternal(count: Int)

    suspend fun deleteOldestMessages(count: Int) {
        if (count <= 0) return
        deleteOldestMessagesInternal(count)
    }

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
