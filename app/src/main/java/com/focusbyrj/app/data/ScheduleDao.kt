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

    @Delete
    suspend fun deleteSchedule(schedule: FocusSchedule)

    @Query("DELETE FROM focus_schedules")
    suspend fun deleteAllSchedules()
}
