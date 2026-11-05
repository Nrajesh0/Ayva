package com.focusbyrj.app.widget

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Shared presentation rules for the editor, launcher and tests. */
internal object ExamWidgetPresentation {
    data class Page(val entries: List<ExamEvent>, val all: List<ExamEvent>, val start: Int, val capacity: Int) {
        val canPage get() = all.size > capacity
        fun adjacentId(forward: Boolean): String {
            val index = if (forward) { if (start + capacity >= all.size) 0 else start + capacity }
                else if (start == 0) ((all.size - 1) / capacity) * capacity else (start - capacity).coerceAtLeast(0)
            return all[index].id
        }
        val label get() = if (entries.size == 1) "${start + 1} of ${all.size}" else "${start + 1}–${start + entries.size} of ${all.size}"
    }
    fun page(config: ExamCountdown, activeId: String, height: Int, now: Instant = Instant.now(), fontScale: Float = 1f, width: Int = 340): Page {
        val scale=fontScale.coerceAtLeast(1f)
        if (config.displayMode == "SINGLE") return Page(listOf(config.event()), listOf(config.event()), 0, 1)
        val all = config.visibleExams(now)
        if (config.displayMode == "NEXT") return Page(listOf(all.first()), all, 0, 1)
        val capacity = if (config.displayMode == "LIST") {
            val row = ExamListTypography.forWidget(config,height,width,scale,all).height(scale)
            val padding = minOf(config.padding, if (height < 150) 10 else 14)
            val toolbar = if (height < 150) 0 else if (config.showEdit) 36 else if (config.widgetName.isNotBlank()) (18 * scale).toInt() else 0
            val available = height - padding * 2 - toolbar
            val withoutNavigation = (available / row).coerceAtLeast(1).coerceAtMost(config.maxRows)
            (if (all.size > withoutNavigation) (available - 36) / row else withoutNavigation)
                .coerceAtLeast(1).coerceAtMost(config.maxRows)
        } else 1
        val start = (all.indexOfFirst { it.id == activeId }.coerceAtLeast(0) / capacity) * capacity
        return Page(all.drop(start).take(capacity), all, start, capacity)
    }
    fun date(config: ExamCountdown, is24Hour: Boolean): String = buildList {
        if (config.showDate) add(when (config.dateStyle) {
            "SHORT" -> LocalDate.parse(config.date).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
            "LONG" -> LocalDate.parse(config.date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
            else -> config.date
        })
        if (config.showTime) add(LocalTime.parse(config.time).format(DateTimeFormatter.ofPattern(
            if (config.clockStyle == "24" || config.clockStyle == "SYSTEM" && is24Hour) "HH:mm" else "h:mm a", Locale.getDefault())))
    }.joinToString(" · ")
    fun progress(config: ExamCountdown, now: Instant = Instant.now()) = config.progress(now).let { if (config.remainingProgress) 100 - it else it }
    /** Separate the value from its unit so the widget has one clear focal point. */
    fun counter(config: ExamCountdown, now: Instant): Pair<String, String> {
        val headline = config.headline(now)
        // A custom finished message may itself end in "left"; it is still a message.
        if (!now.isBefore(config.target().toInstant()) && !config.countUp) return headline to ""
        val match = Regex("^(.*) (left|since)$").matchEntire(headline) ?: return headline to ""
        val prefix = match.groupValues[1]
        val suffix = if (match.groupValues[2] == "since") "since exam" else "to go"
        val days = Regex("^(\\d+) (day|days|weekdays)$").matchEntire(prefix)
        return if (days != null) days.groupValues[1] to "${days.groupValues[2]} $suffix" else prefix to suffix
    }
    fun label(value: String) = when (value) {
        "SINGLE" -> "Primary exam"; "NEXT" -> "Next upcoming exam"; "LIST" -> "Exam list"; "PAGED" -> "One exam per page"
        "HERO" -> "Focus"; "CLASSIC" -> "Balance"; "TICKET" -> "Split"; "TIMELINE" -> "Timeline"
        "DASHBOARD" -> "Planner"; "MINIMAL" -> "Quiet"; "THEME" -> "Follow app"
        "POSTER" -> "Poster"; "ORBITAL" -> "Orbit"; "COLORBLOCK" -> "Colorblock"
        "AURORA" -> "Aurora"; "BENTO" -> "Bento"; "SIGNAL" -> "Signal"
        "FROST" -> "Frost"; "PRISM" -> "Prism"; "LAYERS" -> "Layers"
        "HOURS" -> "Days + hours"; "WEEKS" -> "Weeks + days"; "MONTHS" -> "Months + days"; "WEEKDAYS" -> "Weekdays"
        "CUSTOM" -> "Custom"; "DATE" -> "Exam date"; "SANS" -> "Sans serif"; "SERIF" -> "Serif"; "MONO" -> "Monospace"
        "ISO" -> "Year-month-day"; "SHORT" -> "Weekday & date"; "LONG" -> "Full date"; "SYSTEM" -> "Device format"
        "12" -> "12-hour"; "24" -> "24-hour"; "EDIT" -> "Edit widget"; "WIDGETS" -> "Open widget settings"; "NONE" -> "None"
        else -> value.lowercase().replaceFirstChar(Char::uppercase)
    }
}
