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
interface ScheduleDao {
    @Query("SELECT * FROM focus_schedules WHERE id = :id")
    fun observeSchedule(id: Int): Flow<FocusSchedule?>

    @Query(
        "UPDATE earned_rewards SET state = CASE WHEN endAt <= :now THEN 'PENDING' ELSE 'CANCELLED' END WHERE kind = 'ROUTINE' AND sourceId = :id AND state = 'RUNNING'"
    )
    suspend fun settleRun(id: Long, now: Long = System.currentTimeMillis())

    @Update suspend fun updateRow(schedule: FocusSchedule): Int

    @Transaction
    suspend fun updateChecked(schedule: FocusSchedule, expected: FocusSchedule) {
        require(schedule.id == expected.id && getScheduleById(expected.id) == expected) {
            "Routine changed or was deleted. Your draft is kept; reload the saved routine or save a copy."
        }
        if (schedule != expected) settleRun(schedule.id.toLong())
        check(updateRow(schedule) == 1)
    }

    @Transaction
    suspend fun deleteChecked(schedule: FocusSchedule) {
        require(getScheduleById(schedule.id) == schedule) {
            "Routine changed. Review it before deleting."
        }
        deleteSchedule(schedule)
    }

    @Query("SELECT * FROM focus_schedules") fun getAllSchedules(): Flow<List<FocusSchedule>>

    @Query("SELECT * FROM focus_schedules") suspend fun getAllSchedulesSync(): List<FocusSchedule>

    @Query("SELECT * FROM focus_schedules WHERE id = :id")
    suspend fun getScheduleById(id: Int): FocusSchedule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRow(schedule: FocusSchedule): Long

    @Transaction
    suspend fun insertSchedule(schedule: FocusSchedule): Long {
        if (schedule.id > 0 && getScheduleById(schedule.id) != schedule)
            settleRun(schedule.id.toLong())
        return insertRow(schedule)
    }

    @Transaction
    suspend fun insertSchedules(schedules: List<FocusSchedule>) {
        for (schedule in schedules) insertSchedule(schedule)
    }

    @Delete suspend fun deleteRow(schedule: FocusSchedule)

    @Transaction
    suspend fun deleteSchedule(schedule: FocusSchedule) {
        settleRun(schedule.id.toLong())
        deleteRow(schedule)
    }

    @Query("DELETE FROM focus_schedules") suspend fun deleteRows()

    @Transaction
    suspend fun deleteAllSchedules() {
        for (schedule in getAllSchedulesSync()) settleRun(schedule.id.toLong())
        deleteRows()
    }
}
