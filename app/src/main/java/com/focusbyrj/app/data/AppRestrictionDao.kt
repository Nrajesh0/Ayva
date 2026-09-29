/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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
