package com.focusbyrj.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import com.focusbyrj.app.R
import com.focusbyrj.app.util.BatteryHealthInfo
import com.focusbyrj.app.util.DeviceStatsHelper
import java.util.Date

/** Snapshot widget: periodic OS updates and explicit refresh, with no ongoing service or wakelock. */
class BatteryHealthWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in setOf(ACTION_REFRESH, Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED, Intent.ACTION_CONFIGURATION_CHANGED)) {
            refreshAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.focusbyrj.app.widget.BATTERY_REFRESH"

        internal fun capacityText(context: Context, info: BatteryHealthInfo): String =
            info.maxCapacityHealthPercent?.let { context.getString(R.string.battery_widget_percent, it) }
                ?: context.getString(R.string.battery_widget_unavailable)

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BatteryHealthWidgetProvider::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val info = DeviceStatsHelper.readBatteryStats(context)
            val primary = context.getColor(R.color.battery_widget_primary)
            val secondary = context.getColor(R.color.battery_widget_secondary)
            val charge = info.rawChargePercentage?.let { context.getString(R.string.battery_widget_percent, it) }
                ?: context.getString(R.string.battery_widget_unavailable)
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_battery_health)
                views.setTextViewText(R.id.battery_capacity, capacityText(context, info))
                val chargeText = context.getString(R.string.battery_widget_charge, charge) +
                    if (info.isCharging) context.getString(R.string.battery_widget_charging) else ""
                val conditionText = context.getString(R.string.battery_widget_condition, info.healthStatusLabel)
                val updatedText = context.getString(R.string.battery_widget_updated, DateFormat.getTimeFormat(context).format(Date()))
                views.setTextViewText(R.id.battery_charge, chargeText)
                views.setTextViewText(R.id.battery_condition, conditionText)
                views.setTextViewText(R.id.battery_updated, updatedText)
                views.setContentDescription(R.id.battery_widget_root, listOf(
                    context.getString(R.string.battery_widget_title),
                    context.getString(R.string.battery_widget_capacity_label), capacityText(context, info),
                    chargeText, conditionText, updatedText).joinToString(". "))
                listOf(R.id.battery_capacity, R.id.battery_title).forEach { views.setTextColor(it, primary) }
                listOf(R.id.battery_charge, R.id.battery_condition, R.id.battery_updated, R.id.battery_label)
                    .forEach { views.setTextColor(it, secondary) }
                val refresh = PendingIntent.getBroadcast(context, 0,
                    Intent(context, BatteryHealthWidgetProvider::class.java).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.battery_widget_root, refresh)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
