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

@Database(entities = [NoteEntity::class], version = 6, exportSchema = false)
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE keep_notes ADD COLUMN trashedAt INTEGER")
                } catch (e: Exception) {
                    android.util.Log.e("NoteDatabase", "MIGRATION_4_5: trashedAt column may already exist", e)
                }
                try {
                    db.execSQL("ALTER TABLE keep_notes ADD COLUMN deletedAt INTEGER")
                } catch (e: Exception) {
                    android.util.Log.e("NoteDatabase", "MIGRATION_4_5: deletedAt column may already exist", e)
                }
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE keep_notes ADD COLUMN isArticle INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    android.util.Log.e("NoteDatabase", "MIGRATION_5_6: isArticle column may already exist", e)
                }
                try {
                    db.execSQL("ALTER TABLE keep_notes ADD COLUMN subtitle TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    android.util.Log.e("NoteDatabase", "MIGRATION_5_6: subtitle column may already exist", e)
                }
                try {
                    db.execSQL("ALTER TABLE keep_notes ADD COLUMN coverImageUri TEXT")
                } catch (e: Exception) {
                    android.util.Log.e("NoteDatabase", "MIGRATION_5_6: coverImageUri column may already exist", e)
                }
                try {
                    db.execSQL("ALTER TABLE keep_notes ADD COLUMN isPublished INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    android.util.Log.e("NoteDatabase", "MIGRATION_5_6: isPublished column may already exist", e)
                }
                try {
                    db.execSQL("ALTER TABLE keep_notes ADD COLUMN publishedAt INTEGER")
                } catch (e: Exception) {
                    android.util.Log.e("NoteDatabase", "MIGRATION_5_6: publishedAt column may already exist", e)
                }
                try {
                    db.execSQL("ALTER TABLE keep_notes ADD COLUMN readingTimeMinutes INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    android.util.Log.e("NoteDatabase", "MIGRATION_5_6: readingTimeMinutes column may already exist", e)
                }
            }
        }

        val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }

        val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }

        val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }

        val MIGRATION_1_6 = object : Migration(1, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_2_6 = object : Migration(2, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_3_6 = object : Migration(3, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
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
                    val factory = SupportFactory(passphrase, null, false)

                    Room.databaseBuilder(
                        appContext,
                        NoteDatabase::class.java,
                        NoteDatabaseMigrationHelper.getEncryptedDatabaseName()
                    )
                        .openHelperFactory(factory)
                        .addMigrations(
                            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                            MIGRATION_1_3, MIGRATION_1_4, MIGRATION_2_4,
                            MIGRATION_1_5, MIGRATION_2_5, MIGRATION_3_5,
                            MIGRATION_1_6, MIGRATION_2_6, MIGRATION_3_6, MIGRATION_4_6
                        )
                        .build()
                } catch (t: Throwable) {
                    val isRobolectric = try {
                        android.os.Build.FINGERPRINT.startsWith("robolectric") ||
                        Class.forName("org.robolectric.Robolectric") != null
                    } catch (_: Throwable) {
                        false
                    }
                    if (isRobolectric) {
                        android.util.Log.w("NoteDatabase", "Robolectric test environment detected; utilizing in-memory test database", t)
                        Room.inMemoryDatabaseBuilder(appContext, NoteDatabase::class.java)
                            .fallbackToDestructiveMigration()
                            .build()
                    } else {
                        android.util.Log.e("NoteDatabase", "FATAL: Failed to securely initialize SQLCipher NoteDatabase", t)
                        throw SecurityException("Database encryption failure: unable to securely initialize SQLCipher database. Fail-closed security enforced.", t)
                    }
                }

                // Delete any insecure legacy fallback database if one existed
                try {
                    val fallbackDb = appContext.getDatabasePath("keep_notes_fallback.db")
                    if (fallbackDb.exists()) {
                        fallbackDb.delete()
                    }
                } catch (_: Exception) {}

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
