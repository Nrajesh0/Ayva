/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.data

import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val appRestrictionDao: AppRestrictionDao,
    private val scheduleDao: ScheduleDao
) {
    val allRestrictions: Flow<List<AppRestriction>> = appRestrictionDao.getAllRestrictions()
    val allSchedules: Flow<List<FocusSchedule>> = scheduleDao.getAllSchedules()

    suspend fun getRestriction(packageName: String): AppRestriction? {
        return appRestrictionDao.getRestriction(packageName)
    }

    suspend fun toggleRestriction(app: AppRestriction) {
        appRestrictionDao.insertRestriction(app.copy(isRestricted = !app.isRestricted))
    }
    
    suspend fun updateMode(app: AppRestriction, mode: String) {
        appRestrictionDao.insertRestriction(app.copy(mode = mode))
    }
    
    suspend fun saveApp(app: AppRestriction) {
         appRestrictionDao.insertRestriction(app)
    }

    suspend fun deleteRestriction(app: AppRestriction) {
        appRestrictionDao.deleteRestriction(app.packageName)
    }

    suspend fun getScheduleById(id: Int): FocusSchedule? {
        return scheduleDao.getScheduleById(id)
    }

    suspend fun insertSchedule(schedule: FocusSchedule) {
        scheduleDao.insertSchedule(schedule)
    }

    suspend fun updateSchedule(schedule: FocusSchedule) {
        scheduleDao.insertSchedule(schedule)
    }

    suspend fun deleteSchedule(schedule: FocusSchedule) {
        scheduleDao.deleteSchedule(schedule)
    }

    suspend fun removePackageFromAll(packageName: String) {
        try {
            appRestrictionDao.deleteRestriction(packageName)
            val schedules = scheduleDao.getAllSchedulesSync()
            for (sched in schedules) {
                val apps = sched.appsToBlock.split(",").filter { it.isNotBlank() }
                val filtered = apps.filter { entry ->
                    val pkg = entry.split("|")[0].trim()
                    pkg.isNotEmpty() && pkg != packageName
                }
                if (filtered.size != apps.size) {
                    scheduleDao.insertSchedule(sched.copy(appsToBlock = filtered.joinToString(",")))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun cleanUninstalledPackages(pm: android.content.pm.PackageManager) {
        try {
            val restrictions = appRestrictionDao.getAllRestrictionsSync()
            val uninstalledRestrictions = restrictions.filter {
                try {
                    pm.getApplicationInfo(it.packageName, 0)
                    false
                } catch (e: Exception) {
                    true
                }
            }
            if (uninstalledRestrictions.isNotEmpty()) {
                appRestrictionDao.deleteRestrictions(uninstalledRestrictions.map { it.packageName })
            }

            val schedules = scheduleDao.getAllSchedulesSync()
            for (sched in schedules) {
                val apps = sched.appsToBlock.split(",").filter { it.isNotBlank() }
                val filtered = apps.filter { entry ->
                    val pkg = entry.split("|")[0].trim()
                    if (pkg.isEmpty()) false
                    else {
                        try {
                            pm.getApplicationInfo(pkg, 0)
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }
                }
                if (filtered.size != apps.size) {
                    scheduleDao.insertSchedule(sched.copy(appsToBlock = filtered.joinToString(",")))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
