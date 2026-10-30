package com.focusbyrj.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.graphics.*
import android.os.Bundle
import android.view.View
import android.view.Gravity
import android.widget.RemoteViews
import com.focusbyrj.app.R
import java.time.*
import androidx.compose.ui.graphics.toArgb

class ExamCountdownProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { update(context, it) }; schedule(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) { update(context, id) }
    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { ExamCountdownStore(context).removeWidget(it) }; schedule(context) }
    override fun onDisabled(context: Context) { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(tick(context)) }
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) { oldWidgetIds.zip(newWidgetIds).forEach { (old, new) -> ExamCountdownStore(context).rebind(old, new) }; updateAll(context) }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == PINNED) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className == javaClass.name) {
                runCatching { ExamCountdownStore(context).save(id, ExamCountdown.read(intent.getStringExtra("configuration") ?: return)) }
                updateAll(context)
            }
        }
        if (intent.action in setOf(TICK, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) updateAll(context)
    }
    companion object {
        const val PINNED = "com.focusbyrj.app.EXAM_COUNTDOWN_PINNED"
        private const val TICK = "com.focusbyrj.app.EXAM_COUNTDOWN_TICK"
        private fun tick(context: Context) = PendingIntent.getBroadcast(context, 0, Intent(context, ExamCountdownProvider::class.java).setAction(TICK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, ExamCountdownProvider::class.java)).forEach { update(context, it) }
            schedule(context)
        }
        private fun schedule(context: Context) {
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ExamCountdownProvider::class.java))
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (ids.isEmpty()) { alarm.cancel(tick(context)); return }
            val now = Instant.now()
            val next = ids.toList().mapNotNull { ExamCountdownStore(context).get(it) }.flatMap { value ->
                val midnight = now.atZone(ZoneId.of(value.zone)).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of(value.zone)).toInstant()
                listOf(midnight, value.target().toInstant()).filter { it.isAfter(now) }
            }.minOrNull() ?: now.plusSeconds(3600)
            // Inexact, non-wakeup refresh; Android may defer it while asleep. No exact-alarm permission.
            alarm.set(AlarmManager.RTC, minOf(next.toEpochMilli(), now.plusSeconds(1800).toEpochMilli()), tick(context))
        }
        fun colors(context: Context, value: ExamCountdown): IntArray {
            return when(value.palette) {
                "LIGHT" -> intArrayOf(Color.parseColor("#F5F7F5"), Color.parseColor("#17241F"), Color.parseColor("#246B50"))
                "CUSTOM" -> intArrayOf(Color.parseColor(value.background), Color.parseColor(value.foreground), Color.parseColor(value.accent))
                "THEME" -> {
                    val mode = com.focusbyrj.app.util.AppThemeManager.themeModeFlow.value
                    val dark = mode.isDarkTheme ?: (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                    val primary = com.focusbyrj.app.util.AppThemeManager.themeFlow.value.primary.toArgb()
                    if (dark) intArrayOf(if (mode == com.focusbyrj.app.util.ThemeMode.OLED) Color.BLACK else Color.parseColor("#101615"), Color.parseColor("#E8F4EE"), primary)
                    else intArrayOf(Color.parseColor("#F5F7F5"), Color.parseColor("#17241F"), primary)
                }
                else -> intArrayOf(Color.parseColor("#101615"), Color.parseColor("#E8F4EE"), Color.parseColor("#8DC6B1"))
            }
        }
        fun update(context: Context, id: Int) {
            val manager = AppWidgetManager.getInstance(context)
            val value = ExamCountdownStore(context).get(id)
            val views = RemoteViews(context.packageName, R.layout.widget_exam_countdown)
            val config = value ?: ExamCountdown()
            val c = colors(context, config)
            val options = manager.getAppWidgetOptions(id)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240).coerceIn(140, 600)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180).coerceIn(110, 600)
            val bitmap = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawRoundRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), config.radius * 2f, config.radius * 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c[0]; alpha = config.opacity * 255 / 100 })
            views.setImageViewBitmap(R.id.exam_background, bitmap)
            views.setTextViewText(R.id.exam_name, value?.name ?: "Set up exam countdown")
            views.setTextViewText(R.id.exam_number, value?.headline() ?: "Tap to configure")
            views.setTextViewTextSize(R.id.exam_number, android.util.TypedValue.COMPLEX_UNIT_SP, minOf(config.textSize, if (width < 200) 28 else config.textSize).toFloat())
            views.setTextViewText(R.id.exam_date, "${config.date} · ${config.time} · ${config.zone}")
            views.setTextViewText(R.id.exam_subtitle, config.subtitle)
            listOf(R.id.exam_name, R.id.exam_date, R.id.exam_subtitle).forEach { views.setTextColor(it, c[1]) }
            views.setTextColor(R.id.exam_number, c[2])
            val gravity = when(config.align) { "CENTER" -> Gravity.CENTER; "END" -> Gravity.END; else -> Gravity.START }
            listOf(R.id.exam_name, R.id.exam_number, R.id.exam_date, R.id.exam_subtitle).forEach { views.setInt(it, "setGravity", gravity) }
            views.setViewVisibility(R.id.exam_date, if (config.showDate && height >= 150) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.exam_subtitle, if (height >= 170 && config.subtitle.isNotBlank()) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.exam_progress, if (config.showProgress && height >= 140) View.VISIBLE else View.GONE)
            views.setProgressBar(R.id.exam_progress, 100, config.progress(), false)
            val launch = Intent(context, ExamCountdownConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(android.net.Uri.parse("ayva://exam-widget/$id"))
            views.setOnClickPendingIntent(R.id.exam_root, PendingIntent.getActivity(context, id, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
        }
    }
}
