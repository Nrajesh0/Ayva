package com.focusbyrj.app.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.BatteryManager
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.R
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
class BatteryHealthWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun broadcast(level: Int) {
        context.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, level)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
        })
    }

    @Test fun actualWidgetSeparatesCapacityFromChargeAndTapRefreshesEveryInstance() {
        shadowOf(context.getSystemService(BatteryManager::class.java)).setIntProperty(10, Int.MIN_VALUE)
        broadcast(63)
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        val ids = manager.createWidgets(BatteryHealthWidgetProvider::class.java, R.layout.widget_battery_health, 2)
        ids.forEach { id ->
            val view = manager.getViewFor(id)
            assertEquals("Unavailable", view.findViewById<TextView>(R.id.battery_capacity).text.toString())
            assertEquals("Charge 63%", view.findViewById<TextView>(R.id.battery_charge).text.toString())
            assertEquals("Condition: Good", view.findViewById<TextView>(R.id.battery_condition).text.toString())
        }
        broadcast(42)
        manager.getViewFor(ids.first()).findViewById<View>(R.id.battery_widget_root).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        ids.forEach { id ->
            assertEquals("Charge 42%", manager.getViewFor(id).findViewById<TextView>(R.id.battery_charge).text.toString())
        }
        context.resources.getXml(R.xml.battery_health_widget_info).use { xml ->
            while (xml.eventType != org.xmlpull.v1.XmlPullParser.START_TAG &&
                xml.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) xml.next()
            assertEquals(1800000, xml.getAttributeIntValue("http://schemas.android.com/apk/res/android", "updatePeriodMillis", 0))
        }
    }

    @Test @Config(sdk = [36])
    fun exposedSystemCapacityIsDisplayedIndependentlyOfCharge() {
        shadowOf(context.getSystemService(BatteryManager::class.java)).setIntProperty(10, 89)
        broadcast(63)
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        val id = manager.createWidget(BatteryHealthWidgetProvider::class.java, R.layout.widget_battery_health)
        val view = manager.getViewFor(id)
        assertEquals("89%", view.findViewById<TextView>(R.id.battery_capacity).text.toString())
        assertEquals("Charge 63%", view.findViewById<TextView>(R.id.battery_charge).text.toString())
        assertTrue(view.findViewById<View>(R.id.battery_widget_root).contentDescription.toString().contains("89%"))
    }

    @Test fun widgetHasReadableLightAndDarkResources() {
        val config = Configuration(context.resources.configuration)
        config.uiMode = Configuration.UI_MODE_NIGHT_YES
        val dark = context.createConfigurationContext(config)
        config.uiMode = Configuration.UI_MODE_NIGHT_NO
        val light = context.createConfigurationContext(config)
        assertNotEquals(dark.getColor(R.color.battery_widget_surface), light.getColor(R.color.battery_widget_surface))
        assertNotEquals(dark.getColor(R.color.battery_widget_primary), light.getColor(R.color.battery_widget_primary))
    }
}
