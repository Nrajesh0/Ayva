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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
                // IMPORTANT: NEVER use fallbackToDestructiveMigration() here.
                // A version bump without an explicit Migration object silently wipes all
                // chat history, drill states, and vocab records (BATCH-9-P2-001).
                // Add a new Migration(x, x+1) object for every schema change.
                Room.databaseBuilder(
                    context.applicationContext,
                    AyvaChatDatabase::class.java,
                    "ayva_chat.db"
                )
                .addMigrations(
                    // Reserve slot for future migrations. Example:
                    // object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { ... } }
                )
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
