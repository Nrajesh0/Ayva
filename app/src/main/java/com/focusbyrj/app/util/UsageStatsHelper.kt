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

package com.focusbyrj.app.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Calendar

data class AppUsageData(
    val appName: String,
    val packageName: String,
    val timeInForegroundMs: Long
)

object UsageStatsHelper {
    @Volatile
    private var cachedUsageMap: Map<String, Long>? = null
    @Volatile
    private var lastUsageMapFetchTime: Long = 0L
    private const val USAGE_MAP_CACHE_TTL_MS = 2500L

    private val homePackagesCache = mutableSetOf<String>()
    @Volatile
    private var lastHomePackagesRefreshTime = 0L

    private fun isSystemOrLauncherPackage(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return true
        if (packageName == context.packageName || packageName == "com.focusbyrj.app") return true
        if (packageName == "com.android.settings" || packageName == "com.android.systemui" || packageName == "android") return true

        val now = System.currentTimeMillis()
        if (now - lastHomePackagesRefreshTime > 60_000L || homePackagesCache.isEmpty()) {
            lastHomePackagesRefreshTime = now
            homePackagesCache.clear()
            kotlin.runCatching {
                val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
                val resolves = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                for (info in resolves) {
                    info.activityInfo?.packageName?.let { homePackagesCache.add(it) }
                }
            }
        }

        if (homePackagesCache.contains(packageName)) return true

        val lower = packageName.lowercase()
        return lower.contains("launcher") ||
                lower.contains("quickstep") ||
                lower.contains("trebuchet") ||
                lower.contains("nexuslauncher") ||
                lower.contains("miui.home") ||
                lower.contains("sec.android.app.launcher") ||
                lower.contains("huawei.android.launcher") ||
                lower.contains("oppo.launcher") ||
                lower.contains("vivo.launcher") ||
                lower.contains("transsion.home") ||
                lower.contains("motorola.launcher") ||
                lower.contains("oneplus.launcher")
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageStatsPermission(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun getTodayUsageMap(context: Context, forceRefresh: Boolean = false): Map<String, Long> {
        val nowMs = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = cachedUsageMap
            if (cached != null && (nowMs - lastUsageMapFetchTime) < USAGE_MAP_CACHE_TTL_MS) {
                return cached
            }
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyMap()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageMap = mutableMapOf<String, Long>()

        // 1. Android OS System-level aggregated usage stats
        kotlin.runCatching {
            val aggregated = usm.queryAndAggregateUsageStats(startTime, endTime)
            for ((pkg, stats) in aggregated) {
                if (stats.totalTimeInForeground > 0L && !isSystemOrLauncherPackage(context, pkg)) {
                    usageMap[pkg] = stats.totalTimeInForeground
                }
            }
        }

        // 2. High-precision chronological event delta reconstruction
        kotlin.runCatching {
            val events = usm.queryEvents(startTime, endTime)
            val event = android.app.usage.UsageEvents.Event()
            var currentForegroundPkg: String? = null
            var currentForegroundStartTime = 0L
            val eventUsageMap = mutableMapOf<String, Long>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if (isSystemOrLauncherPackage(context, pkg)) continue
                val type = event.eventType
                val time = event.timeStamp

                when (type) {
                    android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                    android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if (currentForegroundPkg != null && currentForegroundPkg != pkg) {
                            if (currentForegroundStartTime > 0L && time >= currentForegroundStartTime) {
                                val duration = time - currentForegroundStartTime
                                eventUsageMap[currentForegroundPkg!!] = (eventUsageMap[currentForegroundPkg!!] ?: 0L) + duration
                            }
                            currentForegroundPkg = pkg
                            currentForegroundStartTime = time
                        } else if (currentForegroundPkg == null) {
                            currentForegroundPkg = pkg
                            currentForegroundStartTime = time
                        }
                    }
                    android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                    android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED,
                    android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (currentForegroundPkg == pkg) {
                            if (currentForegroundStartTime > 0L && time >= currentForegroundStartTime) {
                                val duration = time - currentForegroundStartTime
                                eventUsageMap[pkg] = (eventUsageMap[pkg] ?: 0L) + duration
                            }
                            currentForegroundPkg = null
                            currentForegroundStartTime = 0L
                        }
                    }
                }
            }

            if (currentForegroundPkg != null && currentForegroundStartTime > 0L && endTime >= currentForegroundStartTime) {
                val duration = endTime - currentForegroundStartTime
                eventUsageMap[currentForegroundPkg!!] = (eventUsageMap[currentForegroundPkg!!] ?: 0L) + duration
            }

            for ((pkg, timeMs) in eventUsageMap) {
                val currentMax = usageMap[pkg] ?: 0L
                usageMap[pkg] = kotlin.math.max(currentMax, timeMs)
            }
        }

        cachedUsageMap = usageMap
        lastUsageMapFetchTime = nowMs
        return usageMap
    }

    fun getTodayUsageStats(context: Context): List<AppUsageData> {
        val usageMap = getTodayUsageMap(context)
        val pm = context.packageManager
        val resultMap = mutableMapOf<String, AppUsageData>()
        
        for ((pkg, timeMs) in usageMap) {
            if (timeMs > 0) {
                val appName = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg
                }
                resultMap[pkg] = AppUsageData(
                    appName = appName,
                    packageName = pkg,
                    timeInForegroundMs = timeMs
                )
            }
        }
        return resultMap.values.sortedByDescending { it.timeInForegroundMs }
    }

    fun getLast30DaysUsageStats(context: Context): Map<Int, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -30)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: emptyList()

        val dayUsageMap = mutableMapOf<Int, Long>()
        val cal = Calendar.getInstance()

        for (usage in stats) {
            cal.timeInMillis = usage.firstTimeStamp
            val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
            val current = dayUsageMap[dayOfYear] ?: 0L
            dayUsageMap[dayOfYear] = current + usage.totalTimeInForeground
        }

        return dayUsageMap
    }

    fun getTodayUsageMinutesForPackage(context: Context, packageName: String): Int {
        val usageMap = getTodayUsageMap(context)
        val timeMs = usageMap[packageName] ?: 0L
        return (timeMs / (1000 * 60)).toInt()
    }

    fun getTodayLaunchCountForPackage(context: Context, packageName: String): Int {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        var count = 0
        var lastResumeTimestamp = 0L
        kotlin.runCatching {
            val events = usm.queryEvents(startTime, endTime)
            val event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName == packageName && 
                    (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ||
                     event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND)) {
                    if (event.timeStamp - lastResumeTimestamp > 2500L) {
                        count++
                        lastResumeTimestamp = event.timeStamp
                    }
                }
            }
        }
        return count
    }
}
