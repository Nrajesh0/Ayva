/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Missing readings stay null. Battery condition is not a capacity-health percentage. */
data class BatteryHealthInfo(
    val rawChargePercentage: Int?,
    val maxCapacityHealthPercent: Int?,
    val realRemainingCapacityPercent: Float?,
    val temperatureCelsius: Float?,
    val voltageMv: Int?,
    val healthStatusLabel: String,
    val peakPerformanceStatus: String = "Performance management information is not available to RUN.",
    val isCharging: Boolean
)

object DeviceStatsHelper {
    // Public, flagged Android 16 API. Kept locally while compiling against API 35.
    // AOSP BatteryManager.BATTERY_PROPERTY_STATE_OF_HEALTH; unsupported devices return MIN_VALUE.
    private const val PROPERTY_STATE_OF_HEALTH = 10

    fun readBatteryStats(context: Context): BatteryHealthInfo = parseBatteryInfo(context,
        runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
            .getOrNull() ?: Intent(Intent.ACTION_BATTERY_CHANGED))

    fun getBatteryStats(context: Context): Flow<BatteryHealthInfo> = callbackFlow {
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    trySend(parseBatteryInfo(app, intent))
                }
            }
        }
        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            } else {
                app.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
        }.isSuccess
        trySend(readBatteryStats(app))
        awaitClose { if (registered) runCatching { app.unregisterReceiver(receiver) } }
    }

    internal fun validCapacityHealth(value: Int?): Int? = value?.takeIf { it in 1..100 }

    private fun readCapacityHealth(context: Context?): Int? {
        if (Build.VERSION.SDK_INT < 36 || context == null) return null
        return validCapacityHealth(runCatching {
            context.getSystemService(BatteryManager::class.java)?.getIntProperty(PROPERTY_STATE_OF_HEALTH)
        }.getOrNull())
    }

    internal fun parseBatteryInfo(context: Context?, intent: Intent): BatteryHealthInfo {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
        val charge = if (present && scale > 0 && level in 0..scale) {
            (level.toLong() * 100L / scale).toInt()
        } else null
        val capacity = if (present) readCapacityHealth(context) else null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val condition = if (!present) "No battery" else when (
            intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        ) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Service required"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Reported failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unavailable"
        }
        return BatteryHealthInfo(
            rawChargePercentage = charge,
            maxCapacityHealthPercent = capacity,
            realRemainingCapacityPercent = if (charge != null && capacity != null) charge * capacity / 100f else null,
            temperatureCelsius = if (present && intent.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) {
                intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            } else null,
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0).takeIf { present && it > 0 },
            healthStatusLabel = condition,
            isCharging = present && (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        )
    }
}
