package com.focusbyrj.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.content.res.ColorStateList
import android.graphics.*
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.StaticLayout
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.view.Gravity
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.focusbyrj.app.R
import java.time.*
import java.time.temporal.ChronoUnit

class ExamCountdownProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { update(context, it) }; schedule(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) { update(context, id) }
    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { ExamCountdownStore(context).removeWidget(it) }; schedule(context) }
    override fun onDisabled(context: Context) { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(tick(context)) }
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        oldWidgetIds.zip(newWidgetIds).forEach { (old, new) -> ExamCountdownStore(context).rebind(old, new) }; updateAll(context)
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val ours = id != AppWidgetManager.INVALID_APPWIDGET_ID && manager.getAppWidgetInfo(id)?.provider?.className == javaClass.name
        if (intent.action == PINNED && ours) {
            runCatching { ExamCountdownStore(context).save(id, ExamCountdown.read(intent.getStringExtra("configuration") ?: return)) }
            configurationChanged(context, id)
        }
        if (intent.action in setOf(NEXT, PREVIOUS) && ours) {
            val store = ExamCountdownStore(context)
            store.get(id)?.let { value ->
                val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
                val page = ExamWidgetPresentation.page(value, store.activeExam(id), height, fontScale = context.resources.configuration.fontScale)
                store.setActiveExam(id, page.adjacentId(intent.action == NEXT))
                update(context, id)
            }
        }
        if (intent.action in setOf(TICK, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) updateAll(context)
    }
    companion object {
        const val PINNED = "com.focusbyrj.app.EXAM_COUNTDOWN_PINNED"
        const val NEXT = "com.focusbyrj.app.EXAM_COUNTDOWN_NEXT"
        const val PREVIOUS = "com.focusbyrj.app.EXAM_COUNTDOWN_PREVIOUS"
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
            val next = ids.toList().mapNotNull { ExamCountdownStore(context).get(it) }.flatMap { config ->
                config.exams().flatMap { event ->
                    val midnight = now.atZone(ZoneId.of(event.zone)).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of(event.zone)).toInstant()
                    val target = event.target().toInstant()
                    val delta = kotlin.math.abs(Duration.between(now, target).seconds)
                    val hourDelta = if (now.isBefore(target)) (delta % 3600).takeIf { it > 0 } ?: 3600 else 3600 - delta % 3600
                    listOf(midnight, target, now.plusSeconds(hourDelta))
                }
            }.filter { it.isAfter(now) }.minOrNull() ?: now.plusSeconds(1800)
            // Non-wakeup, inexact refresh. Countdown visuals never schedule exam notifications.
            alarm.set(AlarmManager.RTC, minOf(next.toEpochMilli(), now.plusSeconds(1800).toEpochMilli()), tick(context))
        }
        fun colors(context: Context, value: ExamCountdown): IntArray = when (value.palette) {
            "LIGHT" -> palette("#F5F7F5", "#17241F", "#246B50")
            "CUSTOM" -> palette(value.background, value.foreground, value.accent)
            "OCEAN" -> palette("#101E30", "#E6F1FF", "#87C5FF")
            "FOREST" -> palette("#10251D", "#E7F4EC", "#91D6AE")
            "ROSE" -> palette("#2A1821", "#FBEAF1", "#F0A7C0")
            "AMBER" -> palette("#292111", "#FFF3D9", "#EBC478")
            "LAVENDER" -> palette("#211C31", "#F0EBFF", "#BFB0EF")
            "SLATE" -> palette("#1B2028", "#E9EDF4", "#ADBDD3")
            "THEME" -> {
                val mode = com.focusbyrj.app.util.AppThemeManager.themeModeFlow.value
                val dark = mode.isDarkTheme ?: (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                val primary = com.focusbyrj.app.util.AppThemeManager.themeFlow.value.primary.toArgb()
                if (dark) intArrayOf(if (mode == com.focusbyrj.app.util.ThemeMode.OLED) Color.BLACK else Color.parseColor("#101615"), Color.parseColor("#E8F4EE"), primary)
                else intArrayOf(Color.parseColor("#F6F8F6"), Color.parseColor("#14231D"), primary)
            }
            else -> palette("#101615", "#E8F4EE", "#8DC6B1")
        }
        private fun palette(bg: String, fg: String, accent: String) = intArrayOf(Color.parseColor(bg), Color.parseColor(fg), Color.parseColor(accent))
        internal fun accent(config: ExamCountdown, base: Int, now: Instant, background: Int = Color.BLACK): Int {
            if (!config.urgency || !now.isBefore(config.target().toInstant())) return base
            val days = ChronoUnit.DAYS.between(now.atZone(ZoneId.of(config.zone)).toLocalDate(), java.time.LocalDate.parse(config.date))
            val light = ColorUtils.calculateLuminance(background) > .5
            return when { days <= config.urgentDays -> if (light) Color.rgb(169, 34, 48) else Color.rgb(255, 139, 139)
                days <= config.warningDays -> if (light) Color.rgb(130, 83, 10) else Color.rgb(236, 191, 109)
                else -> base }
        }
        internal fun background(config: ExamCountdown, c: IntArray, width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width.coerceIn(140, 600) * 2, height.coerceIn(110, 600) * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap); val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val alpha = config.opacity * 255 / 100
            if (!config.transparent) {
                paint.color = c[0]; paint.alpha = alpha
                if (config.decoration == "GRADIENT") paint.shader = LinearGradient(0f, 0f, w, h,
                    ColorUtils.blendARGB(c[0], c[2], .18f), c[0], Shader.TileMode.CLAMP)
                canvas.drawRoundRect(0f, 0f, w, h, config.radius * 2f, config.radius * 2f, paint)
                paint.shader = null
                canvas.save(); val clip = Path().apply { addRoundRect(0f, 0f, w, h, config.radius * 2f, config.radius * 2f, Path.Direction.CW) }; canvas.clipPath(clip)
                paint.color = c[2]; paint.alpha = 20 * config.opacity / 100; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                if (config.decoration == "ORBIT") repeat(3) { canvas.drawCircle(w * .95f, h * .15f, h * (.45f + it * .25f), paint) }
                if (config.decoration == "STRIPES") repeat(12) { val x = it * w / 8; canvas.drawLine(x, h, x + h, 0f, paint) }
                canvas.restore()
            }
            if (config.border) {
                paint.shader = null; paint.color = c[2]; paint.alpha = 105; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                canvas.drawRoundRect(1f, 1f, w - 1, h - 1, config.radius * 2f, config.radius * 2f, paint)
            }
            return bitmap
        }
        private fun text(value: String, font: String, bold: Boolean = false): CharSequence = SpannableString(value).apply {
            if (isEmpty()) return@apply
            setSpan(TypefaceSpan(when(font) { "SERIF" -> "serif"; "MONO" -> "monospace"; else -> "sans-serif" }), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (bold) setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        internal fun render(context: Context, id: Int, value: ExamCountdown?, width: Int, height: Int,
                            activeId: String = "", now: Instant = Instant.now()): RemoteViews {
            val config = value ?: ExamCountdown()
            val layout = when (config.design) {
                "HERO" -> R.layout.widget_exam_hero; "TICKET" -> R.layout.widget_exam_ticket
                "MINIMAL" -> R.layout.widget_exam_minimal; "TIMELINE" -> R.layout.widget_exam_timeline
                "DASHBOARD" -> R.layout.widget_exam_dashboard; else -> R.layout.widget_exam_countdown
            }
            val views = RemoteViews(context.packageName, layout)
            val page = ExamWidgetPresentation.page(config, activeId, height, now, context.resources.configuration.fontScale)
            val shown = config.withEvent(page.entries.first())
            val c = colors(context, config); val highlight = accent(shown, c[2], now, c[0])
            views.setImageViewBitmap(R.id.exam_background, background(config, c, width, height))
            val padding = minOf(config.padding, if (height < 150) 10 else 28)
            val density = context.resources.displayMetrics.density
            views.setViewPadding(R.id.exam_content, (padding * density).toInt(), (padding * density).toInt(), (padding * density).toInt(), (padding * density).toInt())
            fun visible(view: Int, show: Boolean) = views.setViewVisibility(view, if (show) View.VISIBLE else View.GONE)
            val listing = config.displayMode == "LIST" && value != null
            val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
            val toolbar = height >= 150 && (config.showEdit || config.showBadge && (shown.badge.isNotBlank() || listing))
            val headerSpace = if (toolbar) 36 else 0
            val canPage = config.displayMode in setOf("LIST", "PAGED") && page.canPage
            val dateVisible = value != null && height >= 150 * fontScale && (config.showDate || config.showTime || config.showZone)
            val messageVisible = value != null && config.showMessage && height >= (170 + headerSpace) * fontScale && shown.subtitle.isNotBlank()
            val venueVisible = value != null && config.showVenue && height >= (190 + headerSpace) * fontScale && shown.venue.isNotBlank()
            val progressVisible = value != null && config.showProgress && height >= (140 + headerSpace) * fontScale
            val percentVisible = progressVisible && config.showPercent && height >= 240 * fontScale
            visible(R.id.exam_primary, !listing); visible(R.id.exam_list, listing)
            visible(R.id.exam_toolbar, toolbar)
            views.setTextViewText(R.id.exam_badge, text(if (listing) config.widgetName.ifBlank { "${page.all.size} EXAMS" } else shown.badge, config.font, true))
            views.setTextColor(R.id.exam_badge, highlight); visible(R.id.exam_badge, config.showBadge)
            visible(R.id.exam_edit, config.showEdit); views.setTextColor(R.id.exam_edit, c[1])
            views.setTextViewText(R.id.exam_name, text(if (value == null) "Set up exam countdown" else shown.name, config.font, true))
            views.setTextViewText(R.id.exam_number, text(if (value == null) "Tap to configure" else shown.headline(now), config.font, true))
            val innerHeight = height - padding * 2 - headerSpace - if (canPage) 36 else 0
            val titleSize = minOf(config.titleSize, (innerHeight / (4 * fontScale)).toInt().coerceAtLeast(10))
            val family = when(config.font) { "SERIF" -> "serif"; "MONO" -> "monospace"; else -> "sans-serif" }
            fun textHeight(value: String, size: Int, lines: Int, widthDp: Float, bold: Boolean = false): Float {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size * context.resources.displayMetrics.scaledDensity; typeface = Typeface.create(family, if (bold) Typeface.BOLD else Typeface.NORMAL) }
                return StaticLayout.Builder.obtain(value, 0, value.length, paint, (widthDp * density).toInt().coerceAtLeast(1))
                    .setMaxLines(lines).setIncludePad(true).build().height / density
            }
            val contentWidth = (width - padding * 2).toFloat()
            val dateText = ExamWidgetPresentation.date(shown, android.text.format.DateFormat.is24HourFormat(context))
            val outside = (if (messageVisible) textHeight(shown.subtitle, 12, 2, contentWidth) + 4 else 0f) +
                (if (progressVisible) 14 else 0) + (if (percentVisible) textHeight("100% of preparation window elapsed", 11, 2, contentWidth) else 0f)
            val labels = (if (config.showTitle || value == null) textHeight(shown.name, titleSize, 1, contentWidth, true) else 0f) +
                (if (dateVisible) textHeight(dateText, 12, 2, contentWidth) else 0f) +
                (if (venueVisible) textHeight(shown.venue, 12, 2, contentWidth) else 0f)
            val counterSpace = (innerHeight - outside - if (config.design == "TICKET") 0f else labels).coerceAtLeast(24f)
            val fullHeadline = if (value == null) "Tap to configure" else shown.headline(now)
            val headline = if (width < 240) fullHeadline.replace(Regex("(\\d+) days?"), "$1d") else fullHeadline
            views.setTextViewText(R.id.exam_number, text(headline, config.font, true))
            views.setContentDescription(R.id.exam_number, fullHeadline)
            val counterWidth = contentWidth * if (config.design == "TICKET") .45f else 1f
            val numberLines = if (textHeight(headline, 12, 2, counterWidth, true) > counterSpace) 1 else 2
            var size = minOf(config.textSize, if (width < 200 || height < 150) 28 else if (config.design == "TICKET") 34 else 64)
            if (!listing) while (size > 12 && textHeight(headline, size, numberLines, counterWidth, true) > counterSpace) size--
            views.setInt(R.id.exam_number, "setMaxLines", numberLines)
            views.setInt(R.id.exam_name, "setMaxLines", 1)
            views.setTextViewTextSize(R.id.exam_number, android.util.TypedValue.COMPLEX_UNIT_SP, size.toFloat())
            views.setTextViewTextSize(R.id.exam_name, android.util.TypedValue.COMPLEX_UNIT_SP, titleSize.toFloat())
            views.setTextViewText(R.id.exam_date, text(ExamWidgetPresentation.date(shown, android.text.format.DateFormat.is24HourFormat(context)), config.font))
            views.setTextViewText(R.id.exam_subtitle, text(shown.subtitle, config.font))
            views.setTextViewText(R.id.exam_venue, text(shown.venue, config.font))
            val progress = ExamWidgetPresentation.progress(shown, now)
            views.setTextViewText(R.id.exam_percent, text("$progress% ${if (config.remainingProgress) "remaining" else "of preparation window elapsed"}", config.font))
            listOf(R.id.exam_name, R.id.exam_date, R.id.exam_subtitle, R.id.exam_venue, R.id.exam_percent).forEach { views.setTextColor(it, c[1]) }
            views.setTextColor(R.id.exam_number, highlight)
            val gravity = when { config.design == "HERO" -> Gravity.CENTER; config.align == "CENTER" -> Gravity.CENTER; config.align == "END" -> Gravity.END; else -> Gravity.START }
            listOf(R.id.exam_name, R.id.exam_number, R.id.exam_date, R.id.exam_subtitle, R.id.exam_venue, R.id.exam_percent).forEach { views.setInt(it, "setGravity", gravity) }
            visible(R.id.exam_name, config.showTitle || value == null)
            visible(R.id.exam_date, dateVisible)
            visible(R.id.exam_subtitle, messageVisible)
            visible(R.id.exam_venue, venueVisible)
            visible(R.id.exam_progress, progressVisible)
            visible(R.id.exam_percent, percentVisible)
            setProgress(views, progress, highlight)
            views.removeAllViews(R.id.exam_list)
            if (listing) page.entries.forEach { event ->
                val rowLayout = when(config.design) {
                    "HERO" -> R.layout.widget_exam_list_hero; "TICKET" -> R.layout.widget_exam_list_ticket
                    "MINIMAL" -> R.layout.widget_exam_list_minimal; "TIMELINE" -> R.layout.widget_exam_list_timeline
                    "DASHBOARD" -> R.layout.widget_exam_list_dashboard; else -> R.layout.widget_exam_list_row
                }
                val row = RemoteViews(context.packageName, rowLayout)
                val rowConfig = config.withEvent(event); val color = accent(rowConfig, c[2], now, c[0])
                val rowTitle = listOfNotNull(event.badge.takeIf { config.showBadge && it.isNotBlank() }, event.name.takeIf { config.showTitle }).joinToString(" · ")
                row.setTextViewText(R.id.exam_name, text(rowTitle, config.font, config.design != "MINIMAL"))
                row.setViewVisibility(R.id.exam_name, if (rowTitle.isNotBlank()) View.VISIBLE else View.GONE)
                row.setTextViewText(R.id.exam_subtitle, text(event.message, config.font))
                row.setViewVisibility(R.id.exam_subtitle, if (config.showMessage && height >= 360 && event.message.isNotBlank()) View.VISIBLE else View.GONE)
                row.setTextColor(R.id.exam_subtitle, c[1])
                val rowHeadline = rowConfig.headline(now)
                row.setTextViewText(R.id.exam_number, text(if (width < 240) rowHeadline.replace(Regex("(\\d+) days?"), "$1d") else rowHeadline, config.font, true))
                row.setContentDescription(R.id.exam_number, rowHeadline)
                row.setTextViewTextSize(R.id.exam_name, android.util.TypedValue.COMPLEX_UNIT_SP, config.listTextSize.toFloat())
                row.setTextViewTextSize(R.id.exam_number, android.util.TypedValue.COMPLEX_UNIT_SP, (config.listTextSize + 1).toFloat())
                listOf(R.id.exam_date, R.id.exam_subtitle, R.id.exam_venue, R.id.exam_percent).forEach { row.setTextViewTextSize(it, android.util.TypedValue.COMPLEX_UNIT_SP, (config.listTextSize - 3).coerceAtLeast(10).toFloat()) }
                val rowProgress = ExamWidgetPresentation.progress(rowConfig, now)
                row.setTextViewText(R.id.exam_percent, text("$rowProgress% ${if (config.remainingProgress) "remaining" else "elapsed"}", config.font))
                row.setTextColor(R.id.exam_percent, c[1])
                row.setViewVisibility(R.id.exam_percent, if (config.showProgress && config.showPercent && height >= 280) View.VISIBLE else View.GONE)
                row.setTextViewText(R.id.exam_date, text(ExamWidgetPresentation.date(rowConfig, android.text.format.DateFormat.is24HourFormat(context)), config.font))
                row.setTextViewText(R.id.exam_venue, text(event.venue, config.font))
                row.setTextColor(R.id.exam_name, c[1]); row.setTextColor(R.id.exam_date, c[1]); row.setTextColor(R.id.exam_venue, c[1]); row.setTextColor(R.id.exam_number, color)
                row.setViewVisibility(R.id.exam_date, if (config.showDate || config.showTime || config.showZone) View.VISIBLE else View.GONE)
                row.setViewVisibility(R.id.exam_venue, if (config.showVenue && height >= 320 && event.venue.isNotBlank()) View.VISIBLE else View.GONE)
                row.setViewVisibility(R.id.exam_progress, if (config.showProgress && height >= 280) View.VISIBLE else View.GONE)
                setProgress(row, ExamWidgetPresentation.progress(rowConfig, now), color)
                row.setContentDescription(R.id.exam_row, "${event.name}, ${rowConfig.headline(now)}, ${ExamWidgetPresentation.date(rowConfig, true)}")
                views.addView(R.id.exam_list, row)
            }
            visible(R.id.exam_navigation, canPage)
            views.setTextViewText(R.id.exam_page, page.label)
            listOf(R.id.exam_previous, R.id.exam_next, R.id.exam_page).forEach { views.setTextColor(it, c[1]) }
            fun navigation(action: String) = PendingIntent.getBroadcast(context, id, Intent(context, ExamCountdownProvider::class.java)
                .setAction(action).setData(android.net.Uri.parse("ayva://exam-widget/$id/$action"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.exam_previous, navigation(PREVIOUS)); views.setOnClickPendingIntent(R.id.exam_next, navigation(NEXT))
            val edit = PendingIntent.getActivity(context, id, Intent(context, ExamCountdownConfigureActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).setData(android.net.Uri.parse("ayva://exam-widget/$id")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.exam_edit, edit)
            if (config.tapAction == "EDIT" || value == null) views.setOnClickPendingIntent(R.id.exam_root, edit)
            if (config.tapAction == "WIDGETS") views.setOnClickPendingIntent(R.id.exam_root, PendingIntent.getActivity(context, id,
                Intent(context, com.focusbyrj.app.MainActivity::class.java).setAction("open-exam-$id").putExtra("navigate_to", "settings_widgets"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return views
        }
        private fun setProgress(views: RemoteViews, progress: Int, color: Int) {
            views.setProgressBar(R.id.exam_progress, 100, progress, false)
            views.setColorStateList(R.id.exam_progress, "setProgressTintList", ColorStateList.valueOf(color))
            views.setColorStateList(R.id.exam_progress, "setProgressBackgroundTintList", ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 35)))
        }
        fun configurationChanged(context: Context, id: Int) { update(context, id); schedule(context) }
        fun update(context: Context, id: Int) {
            val manager = AppWidgetManager.getInstance(context); val options = manager.getAppWidgetOptions(id)
            manager.updateAppWidget(id, render(context, id, ExamCountdownStore(context).get(id),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240).coerceIn(140, 600),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180).coerceIn(110, 600), ExamCountdownStore(context).activeExam(id)))
        }
    }
}
