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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRestrictionDao {
    @Query("SELECT * FROM app_restrictions ORDER BY appName ASC")
    fun getAllRestrictions(): Flow<List<AppRestriction>>

    @Query("SELECT * FROM app_restrictions")
    suspend fun getAllRestrictionsSync(): List<AppRestriction>

    @Query("SELECT * FROM app_restrictions WHERE packageName = :packageName")
    suspend fun getRestriction(packageName: String): AppRestriction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestriction(restriction: AppRestriction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestrictions(restrictions: List<AppRestriction>)

    @Update
    suspend fun updateRestriction(restriction: AppRestriction)

    @Query("DELETE FROM app_restrictions WHERE packageName = :packageName")
    suspend fun deleteRestriction(packageName: String)

    @Query("DELETE FROM app_restrictions WHERE packageName IN (:packageNames)")
    suspend fun deleteRestrictions(packageNames: List<String>)

    @Query("DELETE FROM app_restrictions")
    suspend fun deleteAllRestrictions()
}
