/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.data.chat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AyvaChatMessageEntity::class,
        ActiveDrillStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AyvaChatDatabase : RoomDatabase() {
    abstract fun chatDao(): AyvaChatDao

    companion object {
        @Volatile
        private var INSTANCE: AyvaChatDatabase? = null

        fun getDatabase(context: Context): AyvaChatDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AyvaChatDatabase::class.java,
                    "ayva_chat.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
