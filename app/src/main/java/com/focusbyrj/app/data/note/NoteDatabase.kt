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
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(entities = [NoteEntity::class], version = 4, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure keep_notes table has imageUrisJson and audioUrisJson if migrating from v1
                try {
                    db.execSQL("ALTER TABLE keep_notes ADD COLUMN imageUrisJson TEXT NOT NULL DEFAULT '[]'")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE keep_notes ADD COLUMN audioUrisJson TEXT NOT NULL DEFAULT '[]'")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure imageUrisJson and audioUrisJson exist before copying
                try {
                    db.execSQL("ALTER TABLE keep_notes ADD COLUMN imageUrisJson TEXT NOT NULL DEFAULT '[]'")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE keep_notes ADD COLUMN audioUrisJson TEXT NOT NULL DEFAULT '[]'")
                } catch (_: Exception) {}

                // Remove reminderTimestamp column safely across all SQLite versions
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS keep_notes_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            title TEXT NOT NULL,
                            content TEXT NOT NULL,
                            isChecklist INTEGER NOT NULL,
                            checklistJson TEXT NOT NULL,
                            colorKey TEXT NOT NULL,
                            isPinned INTEGER NOT NULL,
                            isArchived INTEGER NOT NULL,
                            isTrashed INTEGER NOT NULL,
                            labelsJson TEXT NOT NULL,
                            imageUrisJson TEXT NOT NULL,
                            audioUrisJson TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        INSERT INTO keep_notes_new (
                            id, title, content, isChecklist, checklistJson, colorKey,
                            isPinned, isArchived, isTrashed, labelsJson, imageUrisJson, audioUrisJson,
                            createdAt, updatedAt
                        )
                        SELECT id, title, content, isChecklist, checklistJson, colorKey,
                               isPinned, isArchived, isTrashed, labelsJson, imageUrisJson, audioUrisJson,
                               createdAt, updatedAt
                        FROM keep_notes
                    """.trimIndent())

                    db.execSQL("DROP TABLE keep_notes")
                    db.execSQL("ALTER TABLE keep_notes_new RENAME TO keep_notes")
                } catch (e: Exception) {
                    android.util.Log.e("NoteDatabase", "Error executing MIGRATION_2_3", e)
                }
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE keep_notes ADD COLUMN fontKey TEXT NOT NULL DEFAULT 'default'")
                } catch (e: Exception) {
                    android.util.Log.e("NoteDatabase", "Error executing MIGRATION_3_4", e)
                }
            }
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
            }
        }

        val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        fun getInstance(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                if (INSTANCE != null) return INSTANCE!!

                val appContext = context.applicationContext
                try {
                    SQLiteDatabase.loadLibs(appContext)
                } catch (t: Throwable) {
                    android.util.Log.e("NoteDatabase", "Failed to load SQLCipher native libs", t)
                }

                val instance = try {
                    val passphrase = DatabaseKeyProvider.getOrCreatePassphrase(appContext)
                    val factory = SupportFactory(passphrase)

                    Room.databaseBuilder(
                        appContext,
                        NoteDatabase::class.java,
                        NoteDatabaseMigrationHelper.getEncryptedDatabaseName()
                    )
                        .openHelperFactory(factory)
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_1_3, MIGRATION_1_4, MIGRATION_2_4)
                        .fallbackToDestructiveMigration()
                        .build()
                } catch (t: Throwable) {
                    android.util.Log.e("NoteDatabase", "Failed to initialize SQLCipher NoteDatabase, building fallback Room database", t)
                    Room.databaseBuilder(
                        appContext,
                        NoteDatabase::class.java,
                        "keep_notes_fallback.db"
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_1_3, MIGRATION_1_4, MIGRATION_2_4)
                        .fallbackToDestructiveMigration()
                        .build()
                }

                // Check and migrate legacy unencrypted notes if any exist
                try {
                    NoteDatabaseMigrationHelper.checkAndMigrateIfLegacyPlaintextExists(appContext, instance)
                } catch (t: Throwable) {
                    android.util.Log.e("NoteDatabase", "Legacy plaintext migration failed gracefully", t)
                }

                INSTANCE = instance
                instance
            }
        }
    }
}
