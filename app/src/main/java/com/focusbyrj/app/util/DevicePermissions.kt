package com.focusbyrj.app.util

import android.content.Context
import android.os.Build

/** One capability model backs onboarding, settings and readiness indicators. */
enum class DevicePermission(val settingId: String, val title: String, val explanation: String) {
    USAGE("permissions.usage", "Usage access", "Recognize when a distracting app is opened."),
    OVERLAY("permissions.overlay", "Display over other apps", "Show the blocking screen and floating reminders."),
    BATTERY("permissions.battery", "Battery & background access", "Optional: improve background reliability. Android and manufacturer restrictions can still stop or delay work."),
    NOTIFICATIONS("permissions.notifications", "Notifications", "Keep task, habit and routine alerts available in the notification bar."),
    ALARMS("permissions.alarms", "Alarms & reminders", "Allow precise reminders. Without this access, Android may deliver reminders later.");

    fun granted(context: Context): Boolean = when (this) {
        USAGE -> PermissionUtils.hasUsageStatsPermission(context)
        OVERLAY -> PermissionUtils.hasOverlayPermission(context)
        BATTERY -> PermissionUtils.isIgnoringBatteryOptimizations(context)
        NOTIFICATIONS -> PermissionUtils.hasNotificationPermission(context)
        ALARMS -> PermissionUtils.hasExactAlarmPermission(context)
    }
    fun request(context: Context) = when (this) {
        USAGE -> PermissionUtils.requestUsageStatsPermission(context)
        OVERLAY -> PermissionUtils.requestOverlayPermission(context)
        BATTERY -> PermissionUtils.requestIgnoreBatteryOptimizations(context)
        NOTIFICATIONS -> PermissionUtils.requestNotificationPermission(context)
        ALARMS -> PermissionUtils.requestExactAlarmPermission(context)
    }
    val applicable: Boolean get() = this != ALARMS || Build.VERSION.SDK_INT >= 31
}

data class DevicePermissionState(val granted: Set<DevicePermission>) {
    val blockingReady get() = setOf(DevicePermission.USAGE, DevicePermission.OVERLAY).all { it in granted }
    val remindersReady get() = DevicePermission.NOTIFICATIONS in granted
    val preciseRemindersReady get() = remindersReady && DevicePermission.ALARMS in granted
    val backgroundReliabilityRecommended get() = DevicePermission.BATTERY !in granted
    val ready get() = blockingReady && remindersReady
    companion object {
        fun read(context: Context) = DevicePermissionState(DevicePermission.entries.filter { !it.applicable || it.granted(context) }.toSet())
    }
}
