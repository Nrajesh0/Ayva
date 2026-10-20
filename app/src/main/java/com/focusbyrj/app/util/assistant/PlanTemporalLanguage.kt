package com.focusbyrj.app.util.assistant

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Whole temporal answers only: mentioning a date inside another sentence is not a scheduling request. */
internal object PlanTemporalLanguage {
    private val number = "(?:\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"
    private val time = "$number(?::\\d{2})?(?:\\s*(?:am|pm))?"
    private val day = "(?:day after tomorrow|tomorrow|today|tonight|(?:(?:next|this) )?(?:sunday|monday|tuesday|wednesday|thursday|friday|saturday)|\\d{4}-\\d{2}-\\d{2})"
    private val fullDay = Regex("^(?:(?:on|by) )?$day(?: (?:at $time|$time|(?:in the )?(?:morning|afternoon|evening)))?$", RegexOption.IGNORE_CASE)
    private val clock = Regex("^(?:at $time|$number(?::\\d{2})?\\s*(?:am|pm))$", RegexOption.IGNORE_CASE)

    fun expression(text: String, anchor: Long?, zone: String): String? {
        val value = PlanLanguage.normalize(text)
        if (fullDay.matches(value)) return value
        if (!clock.matches(value)) return null
        if (anchor == null) return value
        val iso = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone(zone) }.format(anchor)
        return "$iso at ${value.replaceFirst(Regex("(?i)^at "), "")}"
    }
}
