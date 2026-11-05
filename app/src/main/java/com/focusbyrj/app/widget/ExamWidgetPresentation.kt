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
    fun page(config: ExamCountdown, activeId: String, height: Int, now: Instant = Instant.now(), fontScale: Float = 1f): Page {
        if (config.displayMode == "SINGLE") return Page(listOf(config.event()), listOf(config.event()), 0, 1)
        val all = config.visibleExams(now)
        if (config.displayMode == "NEXT") return Page(listOf(all.first()), all, 0, 1)
        val capacity = if (config.displayMode == "LIST") {
            val detailHeight = (config.listTextSize - 3).coerceAtLeast(10) * 1.4f * fontScale
            val row = (12 + (config.listTextSize + 1) * 1.4f * fontScale +
                (if (config.showDate || config.showTime || config.showZone) detailHeight else 0f) +
                (if (config.showVenue && height >= 320 && all.any { it.venue.isNotBlank() }) detailHeight else 0f) +
                (if (config.showMessage && height >= 360 && all.any { it.message.isNotBlank() }) detailHeight else 0f) +
                (if (config.showProgress && height >= 280) 10 else 0) +
                (if (config.showProgress && config.showPercent && height >= 280) detailHeight else 0f) +
                (if (config.design in setOf("HERO", "TIMELINE", "TICKET")) (config.listTextSize + 1) * 1.4f * fontScale else 0f) +
                (if (config.design == "DASHBOARD") 10 else 0)).toInt().coerceAtLeast(40)
            ((height - minOf(config.padding, if (height < 150) 10 else 28) * 2 - 36 - if (height >= 150) 36 else 0) / row)
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
        if (config.showZone) add(config.zone)
    }.joinToString(" · ")
    fun progress(config: ExamCountdown, now: Instant = Instant.now()) = config.progress(now).let { if (config.remainingProgress) 100 - it else it }
    fun label(value: String) = when (value) {
        "SINGLE" -> "Primary exam"; "NEXT" -> "Next upcoming exam"; "LIST" -> "Exam list"; "PAGED" -> "One exam per page"
        "HERO" -> "Big countdown"; "CLASSIC" -> "Classic"; "TICKET" -> "Exam ticket"; "TIMELINE" -> "Progress timeline"
        "DASHBOARD" -> "Study dashboard"; "MINIMAL" -> "Minimal"; "THEME" -> "Follow app"
        "HOURS" -> "Days + hours"; "WEEKS" -> "Weeks + days"; "MONTHS" -> "Months + days"; "WEEKDAYS" -> "Weekdays"
        "CUSTOM" -> "Custom"; "DATE" -> "Exam date"; "SANS" -> "Sans serif"; "SERIF" -> "Serif"; "MONO" -> "Monospace"
        "ISO" -> "Year-month-day"; "SHORT" -> "Weekday & date"; "LONG" -> "Full date"; "SYSTEM" -> "Device format"
        "12" -> "12-hour"; "24" -> "24-hour"; "EDIT" -> "Edit widget"; "WIDGETS" -> "Open widget settings"; "NONE" -> "None"
        else -> value.lowercase().replaceFirstChar(Char::uppercase)
    }
}
