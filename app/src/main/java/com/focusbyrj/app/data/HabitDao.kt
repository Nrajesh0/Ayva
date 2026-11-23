/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReward(row: EarnedReward): Long

    @Query("SELECT COALESCE(MAX(ordinal), 0) FROM earned_rewards WHERE kind = 'HABIT_LOG' AND sourceId = :id AND period = :date")
    suspend fun rewardedCount(id: Long, date: String): Int

    @Transaction
    suspend fun incrementWithReward(id: Long, date: String, now: Long): HabitLog {
        val habit = requireNotNull(getHabitById(id)) { "Habit not found" }
        require(!habit.isPaused && !habit.isArchived) { "Resume this habit before logging progress" }
        val period = "$date@${habit.createdAt}"
        insertOrIgnoreLog(HabitLog(habitId = id, date = date, completedCount = 0, targetCount = habit.targetPerDay, lastCompletedTimestamp = now))
        atomicIncrementLog(id, date, now)
        val log = checkNotNull(getLogForHabitAndDate(id, date))
        val target = log.targetCount.takeIf { it > 0 } ?: habit.targetPerDay
        if (log.completedCount <= target && log.completedCount > rewardedCount(id, period)) {
            insertReward(EarnedReward(kind = "HABIT_LOG", sourceId = id, period = period, ordinal = log.completedCount, xp = 10, gold = 5))
        }
        if (log.completedCount >= target) {
            insertReward(EarnedReward(kind = "HABIT_GOAL", sourceId = id, period = period, xp = 15, gold = 10))
        }
        return log
    }

    @Transaction
    suspend fun logStepsChecked(id: Long, date: String, steps: Int, now: Long): HabitStepResult {
        require(steps in 1..100)
        val habit = requireNotNull(getHabitById(id)) { "Habit not found" }
        require(!habit.isPaused && !habit.isArchived) { "Resume this habit before logging progress" }
        val previous = getLogForHabitAndDate(id, date)
        val before = previous?.completedCount ?: 0
        val target = previous?.targetCount?.takeIf { it > 0 } ?: habit.targetPerDay
        var log = previous ?: HabitLog(habitId = id, date = date, completedCount = 0, targetCount = target)
        repeat(minOf(steps, (target - before).coerceAtLeast(0))) { log = incrementWithReward(id, date, now) }
        return HabitStepResult(before, log)
    }

    /** Manually entered/imported history is settled, never retroactively awarded. */
    @Transaction
    suspend fun settleLog(log: HabitLog, settleWholeDay: Boolean = false) {
        val habit = requireNotNull(getHabitById(log.habitId)) { "Habit not found" }
        val period = "${log.date}@${habit.createdAt}"
        val count = if (settleWholeDay) maxOf(log.completedCount, log.targetCount) else log.completedCount
        if (count > rewardedCount(log.habitId, period)) {
            insertReward(EarnedReward(kind = "HABIT_LOG", sourceId = log.habitId, period = period, ordinal = count, state = "APPLIED"))
        }
        if (settleWholeDay || count >= log.targetCount) {
            insertReward(EarnedReward(kind = "HABIT_GOAL", sourceId = log.habitId, period = period, state = "APPLIED"))
        }
    }

    @Transaction
    suspend fun writeManualLog(log: HabitLog) { insertOrUpdateLog(log); settleLog(log) }


    @Transaction
    suspend fun decrementChecked(id: Long, date: String): HabitLog? {
        val habit = requireNotNull(getHabitById(id)) { "Habit not found" }
        require(!habit.isPaused && !habit.isArchived) { "Resume this habit before logging progress" }
        atomicDecrementLog(id, date)
        return getLogForHabitAndDate(id, date)
    }

    @Transaction
    suspend fun setProgressChecked(id: Long, date: String, count: Int, now: Long): HabitLog {
        val habit = requireNotNull(getHabitById(id)) { "Habit not found" }
        require(!habit.isPaused && !habit.isArchived) { "Resume this habit before logging progress" }
        val previous = getLogForHabitAndDate(id, date)
        val log = previous?.copy(completedCount = count.coerceAtLeast(0), lastCompletedTimestamp = now)
            ?: HabitLog(habitId = id, date = date, completedCount = count.coerceAtLeast(0), targetCount = habit.targetPerDay, lastCompletedTimestamp = now)
        writeManualLog(log)
        return log
    }

    @Query("SELECT * FROM habits")
    suspend fun getAllHabitsSync(): List<Habit>

    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY createdAt ASC")
    fun getAllActiveHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :habitId LIMIT 1")
    suspend fun getHabitById(habitId: Long): Habit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: Habit): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabits(habits: List<Habit>)

    @Update
    suspend fun updateHabit(habit: Habit): Int

    @Delete
    suspend fun deleteHabit(habit: Habit)

    @androidx.room.Transaction
    suspend fun setPaused(id: Long, paused: Boolean): Habit? {
        val habit = getHabitById(id) ?: return null
        if (habit.isArchived) return habit
        val updated = habit.withPaused(paused)
        updateHabit(updated)
        return updated
    }

    @Query("UPDATE habits SET isArchived = 1 WHERE id = :habitId")
    suspend fun archiveHabit(habitId: Long)

    // Habit Logs
    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun getLogForHabitAndDate(habitId: Long, date: String): HabitLog?

    @Query("SELECT * FROM habit_logs WHERE date = :date")
    fun getLogsForDate(date: String): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY date DESC")
    fun getLogsForHabit(habitId: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY date ASC")
    suspend fun getAllLogsForHabitSync(habitId: Long): List<HabitLog>

    @Query("SELECT * FROM habit_logs ORDER BY date ASC")
    suspend fun getAllLogsSync(): List<HabitLog>

    @Query("SELECT * FROM habit_logs WHERE date = :date")
    suspend fun getAllLogsForDateSync(date: String): List<HabitLog>

    @Query("SELECT * FROM habit_logs WHERE date >= :startDate AND date <= :endDate")
    fun getLogsBetweenDates(startDate: String, endDate: String): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getLogsForHabitBetweenDates(habitId: Long, startDate: String, endDate: String): Flow<List<HabitLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLog(log: HabitLog): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnoreLog(log: HabitLog): Long

    @Query("UPDATE habit_logs SET completedCount = completedCount + 1, lastCompletedTimestamp = :now WHERE habitId = :habitId AND date = :date")
    suspend fun atomicIncrementLog(habitId: Long, date: String, now: Long): Int

    @Query("UPDATE habit_logs SET completedCount = CASE WHEN completedCount > 0 THEN completedCount - 1 ELSE 0 END WHERE habitId = :habitId AND date = :date")
    suspend fun atomicDecrementLog(habitId: Long, date: String): Int

    @androidx.room.Transaction
    suspend fun deleteHabitWithLogsTransactional(habitId: Long, habit: Habit) {
        deleteLogsForHabit(habitId)
        deleteHabit(habit)
    }

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteLogsForHabit(habitId: Long)

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()

    @Query("DELETE FROM habit_logs")
    suspend fun deleteAllLogs()
}

data class HabitStepResult(val before: Int, val log: HabitLog)
