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

package com.focusbyrj.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.focusbyrj.app.R
import com.focusbyrj.app.data.AppRestriction
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.util.FocusQuotes
import com.focusbyrj.app.util.TemporaryUnlockManager
import com.focusbyrj.app.util.UsageBreakTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object FocusExitTracker {
    @Volatile
    var lastExitedPackage: String? = null
    @Volatile
    var exitTimestamp: Long = 0L

    fun notifyExited(packageName: String?) {
        lastExitedPackage = packageName
        exitTimestamp = System.currentTimeMillis()
    }

    fun isExitSuppressed(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName != lastExitedPackage) return false
        return (System.currentTimeMillis() - exitTimestamp) < 25000L
    }

    fun onNewForegroundAppDetected(packageName: String) {
        if (packageName != lastExitedPackage && packageName.isNotBlank() && packageName != "com.android.systemui") {
            lastExitedPackage = null
            exitTimestamp = 0L
        }
    }
}

class FocusBlockerService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var db: FocusDatabase

    private var currentForegroundPackage: String? = null
    private var monitoringJob: kotlinx.coroutines.Job? = null
    private var isScreenOn = true

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    UsageBreakTracker.onScreenOff()
                    monitoringJob?.cancel()
                    monitoringJob = null
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    UsageBreakTracker.onScreenOn()
                    if (!isScreenOn || intent?.action == Intent.ACTION_USER_PRESENT) {
                        isScreenOn = true
                        startAppMonitoringLoop()
                    }
                }
            }
        }
    }

    private val packageReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == Intent.ACTION_PACKAGE_REMOVED || action == Intent.ACTION_PACKAGE_FULLY_REMOVED) {
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing) {
                    val packageName = intent.data?.schemeSpecificPart
                    if (!packageName.isNullOrBlank()) {
                        scope.launch {
                            (application as? com.focusbyrj.app.FocusApplication)?.repository?.removePackageFromAll(packageName)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        db = (application as com.focusbyrj.app.FocusApplication).database

        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        val pkgFilter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, pkgFilter)

        scope.launch {
            (application as? com.focusbyrj.app.FocusApplication)?.repository?.cleanUninstalledPackages(packageManager)
        }

        startForegroundServiceNotification()
        startRestrictionMonitorLoop()
        startRoutineMonitorLoop()
        startAppMonitoringLoop()
    }

    private fun startForegroundServiceNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Clean up legacy noisy notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            kotlin.runCatching {
                notificationManager.deleteNotificationChannel("focus_blocker_channel")
            }
        }

        val channelId = "focus_guard_silent"
        val channelName = "Focus Protection"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Focus session & app blocker service"
                setSound(null, null)
                setShowBadge(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val silentNotification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Focus Protection Active")
            .setContentText("Focus session & app limits running")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setNotificationSilent()
            .setOngoing(true)
            .build()

        kotlin.runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, silentNotification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, silentNotification)
            }
        }
    }

    private fun startAppMonitoringLoop() {
        if (monitoringJob?.isActive == true) return
        monitoringJob = scope.launch {
            while (isActive) {
                kotlin.runCatching {
                    val currentPackage = getForegroundPackage()
                    if (!currentPackage.isNullOrBlank()) {
                        checkPermissionDialogState(currentPackage)
                        checkAndBlockApp(currentPackage)
                        UsageBreakTracker.onForegroundPackageChecked(
                            context = applicationContext,
                            foregroundPackage = currentPackage,
                            isHomeScreenOrSystem = isIgnoredPackage(currentPackage)
                        )
                    } else if (isPermissionDialogInForeground) {
                        checkPermissionDialogState("")
                    }
                }
                delay(350L)
            }
        }
    }

    private var isPermissionDialogInForeground = false

    private fun isPermissionOrInstallerPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val lower = packageName.lowercase()
        return lower == "com.google.android.permissioncontroller" ||
               lower == "com.android.permissioncontroller" ||
               lower == "com.android.packageinstaller" ||
               lower == "com.google.android.packageinstaller" ||
               lower == "com.samsung.android.permissioncontroller" ||
               lower == "com.samsung.android.packageinstaller" ||
               lower == "com.oplus.securitypermission" ||
               lower == "com.coloros.safecenter" ||
               lower == "com.miui.securitycenter" ||
               lower == "com.lbe.security.miui" ||
               lower.endsWith(".permissioncontroller") ||
               lower.endsWith(".packageinstaller") ||
               lower.contains(".permissioncontroller") ||
               lower.contains(".packageinstaller")
    }

    private fun checkPermissionDialogState(currentPackage: String) {
        val isPermission = isPermissionOrInstallerPackage(currentPackage)
        if (isPermission != isPermissionDialogInForeground) {
            isPermissionDialogInForeground = isPermission
            val action = if (isPermission) {
                BubbleService.ACTION_HIDE_FOR_PERMISSION
            } else {
                BubbleService.ACTION_RESTORE_FROM_PERMISSION
            }
            sendBroadcast(Intent(action).setPackage(packageName))
        }
    }

    data class ActiveRoutineRule(
        val scheduleName: String,
        val restrictionMode: String,
        val timeLimitMinutes: Int,
        val clickLimitCount: Int,
        val appMode: String
    )

    @Volatile
    private var activeRoutineRules = mapOf<String, ActiveRoutineRule>()

    @Volatile
    private var cachedRestrictions = mapOf<String, AppRestriction>()

    private var lastTrackedPackage: String? = null
    private var lastUsageQueryTime: Long = 0L
    private var cachedUsageMinutes: Int = 0
    private var cachedLaunchCount: Int = 0

    private fun startRestrictionMonitorLoop() {
        scope.launch {
            db.appRestrictionDao().getAllRestrictions().collect { list ->
                cachedRestrictions = list.associateBy { it.packageName }
            }
        }
    }

    private fun getTrackedUsageMinutes(packageName: String, timeLimitMinutes: Int): Int {
        val now = System.currentTimeMillis()
        val isPackageSwitch = (packageName != lastTrackedPackage)
        val timeSinceLastQuery = now - lastUsageQueryTime

        // If package switched or user is nearing/exceeding limit, query immediately or every 1s
        val queryThreshold = if (isPackageSwitch) {
            0L
        } else if (timeLimitMinutes > 0 && (cachedUsageMinutes >= timeLimitMinutes || (timeLimitMinutes - cachedUsageMinutes) <= 1)) {
            1000L
        } else {
            3000L
        }

        if (timeSinceLastQuery >= queryThreshold || isPackageSwitch) {
            cachedUsageMinutes = com.focusbyrj.app.util.UsageStatsHelper.getTodayUsageMinutesForPackage(applicationContext, packageName)
            lastUsageQueryTime = now
            if (isPackageSwitch) {
                lastTrackedPackage = packageName
                cachedLaunchCount = com.focusbyrj.app.util.UsageStatsHelper.getTodayLaunchCountForPackage(applicationContext, packageName)
            }
        }
        return cachedUsageMinutes
    }

    private fun getTrackedLaunchCount(packageName: String): Int {
        val isPackageSwitch = (packageName != lastTrackedPackage)
        if (isPackageSwitch || lastUsageQueryTime == 0L) {
            cachedLaunchCount = com.focusbyrj.app.util.UsageStatsHelper.getTodayLaunchCountForPackage(applicationContext, packageName)
            lastTrackedPackage = packageName
            lastUsageQueryTime = System.currentTimeMillis()
            cachedUsageMinutes = com.focusbyrj.app.util.UsageStatsHelper.getTodayUsageMinutesForPackage(applicationContext, packageName)
        }
        return cachedLaunchCount
    }

    private var activeRoutines = mutableMapOf<String, com.focusbyrj.app.data.FocusSchedule>()
    
    private fun startRoutineMonitorLoop() {
        scope.launch {
            db.scheduleDao().getAllSchedules().collect {
                kotlin.runCatching {
                    checkRoutinesAndNotify()
                }
            }
        }
        scope.launch {
            delay(2000L)
            while (isActive) {
                kotlin.runCatching {
                    checkRoutinesAndNotify()
                }
                delay(60000L)
            }
        }
    }

    private suspend fun checkRoutinesAndNotify() {
        val prefs = applicationContext.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        val notifyEnabled = prefs.getBoolean("routine_notifications", true)
        
        val schedules = db.scheduleDao().getAllSchedulesSync()
        val calendar = java.util.Calendar.getInstance()
        val currentDay = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(java.util.Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute
        
        val currentlyActive = mutableMapOf<String, com.focusbyrj.app.data.FocusSchedule>()
        
        for (schedule in schedules) {
            if (!schedule.isEnabled) continue
            if (schedule.isActiveAt(calendar)) {
                currentlyActive[schedule.id.toString()] = schedule
                if (!activeRoutines.containsKey(schedule.id.toString())) {
                    if (notifyEnabled) {
                        sendRoutineNotification("Routine Started", "${schedule.name} is now active.")
                    }
                }
                // Log ongoing routine follow activity
                com.focusbyrj.app.util.FocusStatsManager.addRoutineActivity(applicationContext, 1L)
            }
        }
        
        for (activeId in activeRoutines.keys) {
            if (!currentlyActive.containsKey(activeId)) {
                if (notifyEnabled) {
                    val scheduleName = activeRoutines[activeId]?.name ?: "Routine"
                    sendRoutineNotification("Routine Ended", "$scheduleName has ended.")
                    com.focusbyrj.app.util.FocusEconomyManager.addRewards(100, 25)
                }
                // Reward routine completion activity
                com.focusbyrj.app.util.FocusStatsManager.addRoutineActivity(applicationContext, 15L)
            }
        }
        
        activeRoutines.clear()
        activeRoutines.putAll(currentlyActive)

        // Pre-parse routine block rules for O(1) instantaneous package matching without string allocations
        val newRules = mutableMapOf<String, ActiveRoutineRule>()
        for (schedule in currentlyActive.values) {
            val appsStr = schedule.appsToBlock
            if (appsStr.isBlank()) continue
            val entries = appsStr.split(",")
            for (entry in entries) {
                val trimmed = entry.trim()
                if (trimmed.isEmpty()) continue
                val parts = trimmed.split("|")
                val pkg = parts[0].trim()
                if (pkg.isEmpty()) continue
                val mode = if (parts.size > 1 && parts[1].isNotBlank()) parts[1].trim() else schedule.mode
                newRules[pkg] = ActiveRoutineRule(
                    scheduleName = schedule.name,
                    restrictionMode = schedule.restrictionMode,
                    timeLimitMinutes = schedule.timeLimitMinutes,
                    clickLimitCount = schedule.clickLimitCount,
                    appMode = mode
                )
            }
        }
        activeRoutineRules = newRules
    }

    private fun sendRoutineNotification(title: String, message: String) {
        val prefs = applicationContext.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        val notifyEnabled = prefs.getBoolean("routine_notifications", true)
        if (!notifyEnabled) return

        kotlin.runCatching {
            val category = com.focusbyrj.app.util.AyvaAlertCategory.infer(
                text = "$title $message"
            )
            if (!category.isEnabled(this)) return

            val channelId = category.channelId
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, category.channelName, NotificationManager.IMPORTANCE_DEFAULT)
                notificationManager.createNotificationChannel(channel)
            }
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_app_logo)
                .setColor(category.getParsedNotificationAccent(this))
                .setColorized(false)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(ROUTINE_ALERT_NOTIFICATION_ID, notification)
        }
    }

    private val homePackages = mutableSetOf<String>()
    private var lastHomePackagesCheck = 0L

    private fun refreshHomePackages() {
        val now = System.currentTimeMillis()
        if (now - lastHomePackagesCheck < 30000L && homePackages.isNotEmpty()) return
        lastHomePackagesCheck = now
        
        kotlin.runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val list = packageManager.queryIntentActivities(homeIntent, 0)
            for (info in list) {
                info.activityInfo?.packageName?.let { homePackages.add(it) }
            }
        }
    }

    private fun isIgnoredPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return true
        if (packageName == applicationContext.packageName || packageName == "com.focusbyrj.app") return true
        if (packageName == "com.android.settings" || packageName == "com.android.systemui" || packageName == "android") return true
        if (isPermissionOrInstallerPackage(packageName)) return true
        
        refreshHomePackages()
        if (homePackages.contains(packageName)) return true

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

    private fun getForegroundPackage(): String? {
        return kotlin.runCatching {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val now = System.currentTimeMillis()

            val events = usm.queryEvents(now - 1000 * 10, now) ?: return null
            val event = UsageEvents.Event()
            var latestPackage: String? = null
            var latestTime = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (event.timeStamp > latestTime) {
                        latestTime = event.timeStamp
                        latestPackage = event.packageName
                    }
                }
            }

            if (latestPackage != null) {
                if (latestPackage == FocusExitTracker.lastExitedPackage) {
                    // Ignore ghost resume events that happen exactly when the overlay is removed
                    if (latestTime <= FocusExitTracker.exitTimestamp + 2000L) {
                        return null
                    } else {
                        FocusExitTracker.onNewForegroundAppDetected(latestPackage)
                    }
                } else {
                    FocusExitTracker.onNewForegroundAppDetected(latestPackage)
                }
                currentForegroundPackage = latestPackage
                return latestPackage
            }

            if (FocusExitTracker.isExitSuppressed(currentForegroundPackage)) {
                // User just exited to home, clear the cached package so we don't get stuck
                currentForegroundPackage = null
                return null
            }
            currentForegroundPackage
        }.getOrNull()
    }

    private var lastBubbleCheckTime = 0L

    private suspend fun checkAndBlockApp(packageName: String) {
        // Ensure Ayva Floating Bubble remains alive if enabled (throttled check)
        val now = System.currentTimeMillis()
        if (now - lastBubbleCheckTime > 5000L) {
            lastBubbleCheckTime = now
            try {
                val bubblePrefs = getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
                val isBubbleEnabled = bubblePrefs.getBoolean("bubble_enabled", false)
                val isSnoozed = BubbleService.isSnoozed(this)
                if (isBubbleEnabled && !isSnoozed && android.provider.Settings.canDrawOverlays(this)) {
                    if (!BubbleService.isRunning) {
                        val bubbleIntent = Intent(this, BubbleService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(bubbleIntent)
                        } else {
                            startService(bubbleIntent)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (isIgnoredPackage(packageName)) {
            lastTrackedPackage = null
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                BlockOverlayManager.hideOverlay(this)
            }
            return
        }

        if (FocusExitTracker.isExitSuppressed(packageName)) {
            return
        }

        if (TemporaryUnlockManager.isUnlocked(applicationContext, packageName)) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                BlockOverlayManager.hideOverlay(this)
            }
            return
        }

        var shouldBlock = false
        var blockQuote = ""
        var blockMode = "HARD"

        val restriction = cachedRestrictions[packageName] ?: db.appRestrictionDao().getRestriction(packageName)
        if (restriction != null && restriction.isRestricted) {
            when (restriction.restrictionMode) {
                "TIME_LIMIT" -> {
                    if (restriction.timeLimitMinutes > 0) {
                        val usageMins = getTrackedUsageMinutes(packageName, restriction.timeLimitMinutes)
                        if (usageMins >= restriction.timeLimitMinutes) {
                            shouldBlock = true
                            blockQuote = FocusQuotes.getQuoteOrDefault(restriction.customQuote)
                            blockMode = restriction.mode
                        }
                    }
                }
                "CLICK_LIMIT" -> {
                    if (restriction.clickLimitCount > 0) {
                        val launches = getTrackedLaunchCount(packageName)
                        if (launches > restriction.clickLimitCount) {
                            shouldBlock = true
                            blockQuote = FocusQuotes.getQuoteOrDefault(restriction.customQuote)
                            blockMode = restriction.mode
                        }
                    }
                }
                else -> {
                    shouldBlock = true
                    blockQuote = FocusQuotes.getQuoteOrDefault(restriction.customQuote)
                    blockMode = restriction.mode
                }
            }
        }

        if (!shouldBlock) {
            val routineRule = activeRoutineRules[packageName]
            if (routineRule != null) {
                when (routineRule.restrictionMode) {
                    "TIME_LIMIT" -> {
                        if (routineRule.timeLimitMinutes > 0) {
                            val usageMins = getTrackedUsageMinutes(packageName, routineRule.timeLimitMinutes)
                            if (usageMins >= routineRule.timeLimitMinutes) {
                                shouldBlock = true
                                blockQuote = "Routine '${routineRule.scheduleName}' time limit exceeded."
                                blockMode = routineRule.appMode
                            }
                        }
                    }
                    "CLICK_LIMIT" -> {
                        if (routineRule.clickLimitCount > 0) {
                            val launches = getTrackedLaunchCount(packageName)
                            if (launches > routineRule.clickLimitCount) {
                                shouldBlock = true
                                blockQuote = "Routine '${routineRule.scheduleName}' open limit exceeded."
                                blockMode = routineRule.appMode
                            }
                        }
                    }
                    else -> {
                        shouldBlock = true
                        blockQuote = "Routine '${routineRule.scheduleName}' is active."
                        blockMode = routineRule.appMode
                    }
                }
            }
        }

        val prefs = applicationContext.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        val isSessionActive = prefs.getBoolean("isSessionActive", false)
        if (!shouldBlock && isSessionActive && restriction != null) {
            shouldBlock = true
            blockQuote = FocusQuotes.getQuoteOrDefault(restriction.customQuote)
            blockMode = restriction.mode
        }

        if (shouldBlock) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                BlockOverlayManager.showBlockScreen(this@FocusBlockerService, packageName, blockQuote, blockMode)
            }
        } else if (BlockOverlayManager.isShowing) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                BlockOverlayManager.hideOverlay(this)
            }
        }
    }


    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val prefs = applicationContext.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("isSessionActive", false).apply()
        com.focusbyrj.app.util.DndHelper.setDndMode(applicationContext, false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        lastTrackedPackage = null
        lastUsageQueryTime = 0L
        UsageBreakTracker.reset()
        val prefs = applicationContext.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("isSessionActive", false).apply()
        com.focusbyrj.app.util.DndHelper.setDndMode(applicationContext, false)
        kotlin.runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
        }
        kotlin.runCatching { unregisterReceiver(screenReceiver) }
        kotlin.runCatching { unregisterReceiver(packageReceiver) }
        job.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val ROUTINE_ALERT_NOTIFICATION_ID = 4001
        const val ACTION_UPDATE_NOTIFICATION = "com.focusbyrj.app.ACTION_UPDATE_NOTIFICATION"

        fun startService(context: Context) {
            kotlin.runCatching {
                val intent = Intent(context, FocusBlockerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun updateNotificationState(context: Context) {
            kotlin.runCatching {
                val intent = Intent(context, FocusBlockerService::class.java).apply {
                    action = ACTION_UPDATE_NOTIFICATION
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
