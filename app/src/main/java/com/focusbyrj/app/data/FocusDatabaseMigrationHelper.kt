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

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream

/**
 * Handles seamless one-time migration of tasks, habits, schedules, and restrictions
 * from legacy unencrypted SQLite database ("focus_database") to SQLCipher encrypted database ("focus_database_vault.db").
 */
object FocusDatabaseMigrationHelper {

    private const val TAG = "FocusDbMigration"
    private const val PLAINTEXT_DB_NAME = "focus_database"
    private const val ENCRYPTED_DB_NAME = "focus_database_vault.db"
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun getEncryptedDatabaseName(): String = ENCRYPTED_DB_NAME

    fun isPlaintextSqliteFile(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val header = ByteArray(16)
        try {
            FileInputStream(file).use { stream ->
                val bytesRead = stream.read(header)
                if (bytesRead < 16) return false
            }
            return header.contentEquals(SQLITE_HEADER)
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading database header", e)
            return false
        }
    }

    /**
     * Checks if a legacy unencrypted database exists and migrates all data to the encrypted database.
     */
    fun checkAndMigrateIfLegacyPlaintextExists(context: Context, encryptedDb: FocusDatabase) {
        try {
            val dbFile = context.getDatabasePath(PLAINTEXT_DB_NAME)
            if (!dbFile.exists() || dbFile.length() == 0L) {
                return
            }

            if (!isPlaintextSqliteFile(dbFile)) {
                return
            }

            Log.i(TAG, "Legacy unencrypted FocusDatabase detected. Starting migration to SQLCipher vault...")

            val rawDb = try {
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.path,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open legacy database with raw SQLite", e)
                null
            } ?: return

            runBlocking(Dispatchers.IO) {
                // 1. Migrate AppRestrictions
                try {
                    val cursor = rawDb.rawQuery("SELECT * FROM app_restrictions", null)
                    val restrictions = mutableListOf<AppRestriction>()
                    cursor.use { c ->
                        val pkgIdx = c.getColumnIndex("packageName")
                        val appNameIdx = c.getColumnIndex("appName")
                        val isRestrictedIdx = c.getColumnIndex("isRestricted")
                        val modeIdx = c.getColumnIndex("mode")
                        val restrictionModeIdx = c.getColumnIndex("restrictionMode")
                        val timeLimitIdx = c.getColumnIndex("timeLimitMinutes")
                        val clickLimitIdx = c.getColumnIndex("clickLimitCount")
                        val customQuoteIdx = c.getColumnIndex("customQuote")

                        while (c.moveToNext()) {
                            if (pkgIdx != -1) {
                                val pkg = c.getString(pkgIdx) ?: continue
                                val appName = if (appNameIdx != -1) c.getString(appNameIdx) ?: "" else ""
                                val isRestricted = if (isRestrictedIdx != -1) c.getInt(isRestrictedIdx) == 1 else false
                                val mode = if (modeIdx != -1) c.getString(modeIdx) ?: "HARD" else "HARD"
                                val rMode = if (restrictionModeIdx != -1) c.getString(restrictionModeIdx) ?: "SIMPLE" else "SIMPLE"
                                val timeLimit = if (timeLimitIdx != -1) c.getInt(timeLimitIdx) else 0
                                val clickLimit = if (clickLimitIdx != -1) c.getInt(clickLimitIdx) else 0
                                val customQuote = if (customQuoteIdx != -1) c.getString(customQuoteIdx) ?: "" else ""

                                restrictions.add(
                                    AppRestriction(
                                        packageName = pkg,
                                        appName = appName,
                                        isRestricted = isRestricted,
                                        mode = mode,
                                        restrictionMode = rMode,
                                        timeLimitMinutes = timeLimit,
                                        clickLimitCount = clickLimit,
                                        customQuote = customQuote.ifBlank { "Is this urgent, or are you chasing cheap dopamine?" }
                                    )
                                )
                            }
                        }
                    }
                    if (restrictions.isNotEmpty()) {
                        encryptedDb.appRestrictionDao().insertRestrictions(restrictions)
                        Log.i(TAG, "Migrated ${restrictions.size} app restrictions to encrypted vault.")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "App restrictions table migration skipped or failed", e)
                }

                // 2. Migrate FocusSchedules
                try {
                    val cursor = rawDb.rawQuery("SELECT * FROM focus_schedules", null)
                    cursor.use { c ->
                        val idIdx = c.getColumnIndex("id")
                        val nameIdx = c.getColumnIndex("name")
                        val startHIdx = c.getColumnIndex("startHour")
                        val startMIdx = c.getColumnIndex("startMinute")
                        val endHIdx = c.getColumnIndex("endHour")
                        val endMIdx = c.getColumnIndex("endMinute")
                        val daysIdx = c.getColumnIndex("daysOfWeek")
                        val modeIdx = c.getColumnIndex("mode")
                        val rModeIdx = c.getColumnIndex("restrictionMode")
                        val timeLimitIdx = c.getColumnIndex("timeLimitMinutes")
                        val clickLimitIdx = c.getColumnIndex("clickLimitCount")
                        val appsToBlockIdx = c.getColumnIndex("appsToBlock")
                        val isEnabledIdx = c.getColumnIndex("isEnabled")

                        while (c.moveToNext()) {
                            val schedule = FocusSchedule(
                                id = if (idIdx != -1) c.getInt(idIdx) else 0,
                                name = if (nameIdx != -1) c.getString(nameIdx) ?: "Schedule" else "Schedule",
                                startHour = if (startHIdx != -1) c.getInt(startHIdx) else 9,
                                startMinute = if (startMIdx != -1) c.getInt(startMIdx) else 0,
                                endHour = if (endHIdx != -1) c.getInt(endHIdx) else 17,
                                endMinute = if (endMIdx != -1) c.getInt(endMIdx) else 0,
                                daysOfWeek = if (daysIdx != -1) c.getString(daysIdx) ?: "" else "",
                                mode = if (modeIdx != -1) c.getString(modeIdx) ?: "HARD" else "HARD",
                                restrictionMode = if (rModeIdx != -1) c.getString(rModeIdx) ?: "SIMPLE" else "SIMPLE",
                                timeLimitMinutes = if (timeLimitIdx != -1) c.getInt(timeLimitIdx) else 0,
                                clickLimitCount = if (clickLimitIdx != -1) c.getInt(clickLimitIdx) else 0,
                                appsToBlock = if (appsToBlockIdx != -1) c.getString(appsToBlockIdx) ?: "" else "",
                                isEnabled = if (isEnabledIdx != -1) c.getInt(isEnabledIdx) == 1 else true
                            )
                            encryptedDb.scheduleDao().insertSchedule(schedule)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Focus schedules table migration skipped or failed", e)
                }

                // 3. Migrate Tasks
                try {
                    val cursor = rawDb.rawQuery("SELECT * FROM tasks", null)
                    cursor.use { c ->
                        val idIdx = c.getColumnIndex("id")
                        val titleIdx = c.getColumnIndex("title")
                        val detailsIdx = c.getColumnIndex("details")
                        val dueDateIdx = c.getColumnIndex("dueDate")
                        val isCompletedIdx = c.getColumnIndex("isCompleted")
                        val typeIdx = c.getColumnIndex("type")
                        val recurrenceIdx = c.getColumnIndex("recurrence")
                        val isPersistentIdx = c.getColumnIndex("isPersistent")
                        val isPriorityIdx = c.getColumnIndex("isPriority")
                        val completedAtIdx = c.getColumnIndex("completedAt")

                        while (c.moveToNext()) {
                            val title = if (titleIdx != -1) c.getString(titleIdx) ?: "" else ""
                            if (title.isBlank()) continue
                            val task = Task(
                                id = if (idIdx != -1) c.getLong(idIdx) else 0L,
                                title = title,
                                details = if (detailsIdx != -1) c.getString(detailsIdx) ?: "" else "",
                                dueDate = if (dueDateIdx != -1 && !c.isNull(dueDateIdx)) c.getLong(dueDateIdx) else null,
                                isCompleted = if (isCompletedIdx != -1) c.getInt(isCompletedIdx) == 1 else false,
                                type = try {
                                    if (typeIdx != -1 && !c.isNull(typeIdx)) TaskType.valueOf(c.getString(typeIdx)) else TaskType.TASK
                                } catch (_: Exception) { TaskType.TASK },
                                recurrence = try {
                                    if (recurrenceIdx != -1 && !c.isNull(recurrenceIdx)) RecurrencePattern.valueOf(c.getString(recurrenceIdx)) else RecurrencePattern.NONE
                                } catch (_: Exception) { RecurrencePattern.NONE },
                                isPersistent = if (isPersistentIdx != -1) c.getInt(isPersistentIdx) == 1 else false,
                                isPriority = if (isPriorityIdx != -1) c.getInt(isPriorityIdx) == 1 else false,
                                completedAt = if (completedAtIdx != -1 && !c.isNull(completedAtIdx)) c.getLong(completedAtIdx) else null
                            )
                            encryptedDb.taskDao().insertTask(task)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Tasks table migration skipped or failed", e)
                }

                // 4. Migrate Habits
                try {
                    val cursor = rawDb.rawQuery("SELECT * FROM habits", null)
                    cursor.use { c ->
                        val idIdx = c.getColumnIndex("id")
                        val titleIdx = c.getColumnIndex("title")
                        val descIdx = c.getColumnIndex("description")
                        val iconIdx = c.getColumnIndex("iconEmoji")
                        val colorIdx = c.getColumnIndex("colorHex")
                        val typeIdx = c.getColumnIndex("type")
                        val targetIdx = c.getColumnIndex("targetPerDay")
                        val intHIdx = c.getColumnIndex("intervalHours")
                        val intMIdx = c.getColumnIndex("intervalMinutes")
                        val winSHIdx = c.getColumnIndex("windowStartHour")
                        val winSMIdx = c.getColumnIndex("windowStartMinute")
                        val winEHIdx = c.getColumnIndex("windowEndHour")
                        val winEMIdx = c.getColumnIndex("windowEndMinute")
                        val fixHIdx = c.getColumnIndex("fixedReminderHour")
                        val fixMIdx = c.getColumnIndex("fixedReminderMinute")
                        val isRemIdx = c.getColumnIndex("isReminderEnabled")
                        val soundIdx = c.getColumnIndex("reminderSound")
                        val createdIdx = c.getColumnIndex("createdAt")
                        val archivedIdx = c.getColumnIndex("isArchived")

                        while (c.moveToNext()) {
                            val title = if (titleIdx != -1) c.getString(titleIdx) ?: "" else ""
                            if (title.isBlank()) continue
                            val habit = Habit(
                                id = if (idIdx != -1) c.getLong(idIdx) else 0L,
                                title = title,
                                description = if (descIdx != -1) c.getString(descIdx) ?: "" else "",
                                iconEmoji = if (iconIdx != -1) c.getString(iconIdx) ?: "✨" else "✨",
                                colorHex = if (colorIdx != -1) c.getString(colorIdx) ?: "#3B82F6" else "#3B82F6",
                                type = try {
                                    if (typeIdx != -1 && !c.isNull(typeIdx)) HabitType.valueOf(c.getString(typeIdx)) else HabitType.ONCE_DAILY
                                } catch (_: Exception) { HabitType.ONCE_DAILY },
                                targetPerDay = if (targetIdx != -1) c.getInt(targetIdx) else 1,
                                intervalHours = if (intHIdx != -1) c.getInt(intHIdx) else 2,
                                intervalMinutes = if (intMIdx != -1) c.getInt(intMIdx) else 0,
                                windowStartHour = if (winSHIdx != -1) c.getInt(winSHIdx) else 8,
                                windowStartMinute = if (winSMIdx != -1) c.getInt(winSMIdx) else 0,
                                windowEndHour = if (winEHIdx != -1) c.getInt(winEHIdx) else 20,
                                windowEndMinute = if (winEMIdx != -1) c.getInt(winEMIdx) else 0,
                                fixedReminderHour = if (fixHIdx != -1) c.getInt(fixHIdx) else 9,
                                fixedReminderMinute = if (fixMIdx != -1) c.getInt(fixMIdx) else 0,
                                isReminderEnabled = if (isRemIdx != -1) c.getInt(isRemIdx) == 1 else true,
                                reminderSound = if (soundIdx != -1) c.getString(soundIdx) ?: "ZEN" else "ZEN",
                                createdAt = if (createdIdx != -1) c.getLong(createdIdx) else System.currentTimeMillis(),
                                isArchived = if (archivedIdx != -1) c.getInt(archivedIdx) == 1 else false
                            )
                            encryptedDb.habitDao().insertHabit(habit)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Habits table migration skipped or failed", e)
                }

                // 5. Migrate Habit Logs
                try {
                    val cursor = rawDb.rawQuery("SELECT * FROM habit_logs", null)
                    cursor.use { c ->
                        val idIdx = c.getColumnIndex("id")
                        val habitIdIdx = c.getColumnIndex("habitId")
                        val dateIdx = c.getColumnIndex("date")
                        val completedCountIdx = c.getColumnIndex("completedCount")
                        val targetCountIdx = c.getColumnIndex("targetCount")
                        val lastCompletedIdx = c.getColumnIndex("lastCompletedTimestamp")

                        while (c.moveToNext()) {
                            if (habitIdIdx != -1 && dateIdx != -1) {
                                val habitId = c.getLong(habitIdIdx)
                                val date = c.getString(dateIdx) ?: continue
                                val log = HabitLog(
                                    id = if (idIdx != -1) c.getLong(idIdx) else 0L,
                                    habitId = habitId,
                                    date = date,
                                    completedCount = if (completedCountIdx != -1) c.getInt(completedCountIdx) else 0,
                                    targetCount = if (targetCountIdx != -1) c.getInt(targetCountIdx) else 1,
                                    lastCompletedTimestamp = if (lastCompletedIdx != -1 && !c.isNull(lastCompletedIdx)) c.getLong(lastCompletedIdx) else null
                                )
                                encryptedDb.habitDao().insertOrUpdateLog(log)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Habit logs migration skipped or failed", e)
                }
            }

            rawDb.close()

            // Safely rename migrated plaintext database so it is never re-processed
            try {
                val backupFile = File(dbFile.parentFile, "$PLAINTEXT_DB_NAME.migrated")
                dbFile.renameTo(backupFile)
                File(dbFile.parentFile, "$PLAINTEXT_DB_NAME-wal").delete()
                File(dbFile.parentFile, "$PLAINTEXT_DB_NAME-shm").delete()
                Log.i(TAG, "Successfully migrated legacy FocusDatabase to SQLCipher vault and archived plaintext database.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to archive old database file after migration", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkAndMigrateIfLegacyPlaintextExists", e)
        }
    }
}
