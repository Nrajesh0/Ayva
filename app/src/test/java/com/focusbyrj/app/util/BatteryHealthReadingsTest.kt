package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 36], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatteryHealthReadingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun reading(voltage: Int = 3900, condition: Int = BatteryManager.BATTERY_HEALTH_GOOD) =
        Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 47)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_VOLTAGE, voltage)
            putExtra(BatteryManager.EXTRA_HEALTH, condition)
            putExtra(BatteryManager.EXTRA_TEMPERATURE, -50)
        }

    @Test fun conditionAndVoltageNeverInventCapacityOrPerformanceManagement() {
        shadowOf(context.getSystemService(BatteryManager::class.java)).setIntProperty(10, Int.MIN_VALUE)
        val good = DeviceStatsHelper.parseBatteryInfo(context, reading())
        val hot = DeviceStatsHelper.parseBatteryInfo(context, reading(4200, BatteryManager.BATTERY_HEALTH_OVERHEAT))
        assertEquals(47, good.rawChargePercentage)
        assertEquals("Good", good.healthStatusLabel)
        assertEquals("Overheated", hot.healthStatusLabel)
        assertNull(good.maxCapacityHealthPercent)
        assertNull(hot.maxCapacityHealthPercent)
        assertNull(good.realRemainingCapacityPercent)
        assertEquals(-5f, good.temperatureCelsius!!, 0f)
        assertFalse(hot.peakPerformanceStatus.contains("has been applied"))
    }

    @Test fun measuredHealthIsOnlyReadOnAndroid16AndOnlyForValidValues() {
        val battery = shadowOf(context.getSystemService(BatteryManager::class.java))
        battery.setIntProperty(10, 87)
        val info = DeviceStatsHelper.parseBatteryInfo(context, reading())
        if (Build.VERSION.SDK_INT >= 36) {
            assertEquals(87, info.maxCapacityHealthPercent)
            assertEquals(40.89f, info.realRemainingCapacityPercent!!, 0.001f)
        } else {
            assertNull(info.maxCapacityHealthPercent)
        }
        for (invalid in listOf(Int.MIN_VALUE, -1, 0, 101, Int.MAX_VALUE)) {
            battery.setIntProperty(10, invalid)
            assertNull(DeviceStatsHelper.parseBatteryInfo(context, reading()).maxCapacityHealthPercent)
        }
    }

    @Test fun absentMissingAndOverflowReadingsRemainHonest() {
        val empty = DeviceStatsHelper.parseBatteryInfo(context, Intent())
        assertNull(empty.rawChargePercentage)
        assertNull(empty.temperatureCelsius)
        assertNull(empty.voltageMv)
        assertEquals("Unavailable", empty.healthStatusLabel)
        val absent = DeviceStatsHelper.parseBatteryInfo(context, reading().putExtra(BatteryManager.EXTRA_PRESENT, false))
        assertNull(absent.rawChargePercentage)
        assertNull(absent.maxCapacityHealthPercent)
        assertEquals("No battery", absent.healthStatusLabel)
        val big = DeviceStatsHelper.parseBatteryInfo(context, reading().apply {
            putExtra(BatteryManager.EXTRA_LEVEL, Int.MAX_VALUE)
            putExtra(BatteryManager.EXTRA_SCALE, Int.MAX_VALUE)
        })
        assertEquals(100, big.rawChargePercentage)
    }
}
