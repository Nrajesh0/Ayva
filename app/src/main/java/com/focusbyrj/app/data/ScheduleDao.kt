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
    @Query("SELECT * FROM focus_schedules")
    fun getAllSchedules(): Flow<List<FocusSchedule>>
    
    @Query("SELECT * FROM focus_schedules")
    suspend fun getAllSchedulesSync(): List<FocusSchedule>

    @Query("SELECT * FROM focus_schedules WHERE id = :id")
    suspend fun getScheduleById(id: Int): FocusSchedule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: FocusSchedule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<FocusSchedule>)

    @Delete
    suspend fun deleteSchedule(schedule: FocusSchedule)

    @Query("DELETE FROM focus_schedules")
    suspend fun deleteAllSchedules()
}
