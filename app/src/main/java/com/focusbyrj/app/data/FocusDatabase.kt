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

package com.focusbyrj.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AppRestriction::class, FocusSchedule::class, Task::class, Habit::class, HabitLog::class], version = 9, exportSchema = false)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun appRestrictionDao(): AppRestrictionDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `focus_schedules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `startHour` INTEGER NOT NULL,
                        `startMinute` INTEGER NOT NULL,
                        `endHour` INTEGER NOT NULL,
                        `endMinute` INTEGER NOT NULL,
                        `daysOfWeek` TEXT NOT NULL,
                        `mode` TEXT NOT NULL DEFAULT 'HARD',
                        `restrictionMode` TEXT NOT NULL DEFAULT 'SIMPLE',
                        `timeLimitMinutes` INTEGER NOT NULL DEFAULT 0,
                        `clickLimitCount` INTEGER NOT NULL DEFAULT 0,
                        `appsToBlock` TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure columns exist on app_restrictions
                try { db.execSQL("ALTER TABLE `app_restrictions` ADD COLUMN `mode` TEXT NOT NULL DEFAULT 'HARD'") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE `app_restrictions` ADD COLUMN `restrictionMode` TEXT NOT NULL DEFAULT 'SIMPLE'") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE `app_restrictions` ADD COLUMN `timeLimitMinutes` INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE `app_restrictions` ADD COLUMN `clickLimitCount` INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE `app_restrictions` ADD COLUMN `customQuote` TEXT NOT NULL DEFAULT 'Is this urgent, or are you chasing cheap dopamine?'") } catch (_: Exception) {}

                // Ensure columns exist on focus_schedules
                try { db.execSQL("ALTER TABLE `focus_schedules` ADD COLUMN `mode` TEXT NOT NULL DEFAULT 'HARD'") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE `focus_schedules` ADD COLUMN `restrictionMode` TEXT NOT NULL DEFAULT 'SIMPLE'") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE `focus_schedules` ADD COLUMN `timeLimitMinutes` INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE `focus_schedules` ADD COLUMN `clickLimitCount` INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE `focus_schedules` ADD COLUMN `appsToBlock` TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `details` TEXT NOT NULL DEFAULT '',
                        `dueDate` INTEGER,
                        `isCompleted` INTEGER NOT NULL DEFAULT 0,
                        `type` TEXT NOT NULL DEFAULT 'TASK',
                        `recurrence` TEXT NOT NULL DEFAULT 'NONE',
                        `isPersistent` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
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
                try { db.execSQL("ALTER TABLE `tasks` ADD COLUMN `isPriority` INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
            }
        }

        val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }
                val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `tasks` ADD COLUMN `completedAt` INTEGER") } catch (_: Exception) {}
            }
        }
        
        val MIGRATION_1_6 = object : Migration(1, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `focus_schedules` ADD COLUMN `isEnabled` INTEGER NOT NULL DEFAULT 1") } catch (_: Exception) {}
            }
        }

        val MIGRATION_1_7 = object : Migration(1, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `habits` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `iconEmoji` TEXT NOT NULL DEFAULT '✨',
                        `colorHex` TEXT NOT NULL DEFAULT '#3B82F6',
                        `type` TEXT NOT NULL DEFAULT 'ONCE_DAILY',
                        `targetPerDay` INTEGER NOT NULL DEFAULT 1,
                        `intervalHours` INTEGER NOT NULL DEFAULT 2,
                        `intervalMinutes` INTEGER NOT NULL DEFAULT 0,
                        `windowStartHour` INTEGER NOT NULL DEFAULT 8,
                        `windowStartMinute` INTEGER NOT NULL DEFAULT 0,
                        `windowEndHour` INTEGER NOT NULL DEFAULT 20,
                        `windowEndMinute` INTEGER NOT NULL DEFAULT 0,
                        `fixedReminderHour` INTEGER NOT NULL DEFAULT 9,
                        `fixedReminderMinute` INTEGER NOT NULL DEFAULT 0,
                        `isReminderEnabled` INTEGER NOT NULL DEFAULT 1,
                        `reminderSound` TEXT NOT NULL DEFAULT 'ZEN',
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        `isArchived` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `habit_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `habitId` INTEGER NOT NULL,
                        `date` TEXT NOT NULL,
                        `completedCount` INTEGER NOT NULL DEFAULT 0,
                        `targetCount` INTEGER NOT NULL DEFAULT 1,
                        `lastCompletedTimestamp` INTEGER
                    )
                """.trimIndent())

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_logs_habitId_date` ON `habit_logs` (`habitId`, `date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_logs_date` ON `habit_logs` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_logs_habitId` ON `habit_logs` (`habitId`)")
            }
        }

        val MIGRATION_1_8 = object : Migration(1, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_7.migrate(db)
                MIGRATION_7_8.migrate(db)
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `habits` ADD COLUMN `intervalMinutes` INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_7_9 = object : Migration(7, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_8.migrate(db)
                MIGRATION_8_9.migrate(db)
            }
        }

        val MIGRATION_1_9 = object : Migration(1, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_8.migrate(db)
                MIGRATION_8_9.migrate(db)
            }
        }
    }
}

