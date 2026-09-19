package com.focusbyrj.app.util

import com.focusbyrj.app.data.RecurrencePattern
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ParseResult(
    val cleanText: String,
    val timestamp: Long?,
    val hasTime: Boolean,
    val recurrence: RecurrencePattern = RecurrencePattern.NONE,
    val note: String? = null
)

object SmartDateParser {

    fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    fun formatDueDate(timestamp: Long?): String {
        if (timestamp == null) return ""
        val dueCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance()
        val pattern = if (dueCal.get(Calendar.YEAR) != nowCal.get(Calendar.YEAR)) {
            "MMM dd, yyyy, h:mm a"
        } else {
            "MMM dd, h:mm a"
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
    }

    fun formatDueDateFull(timestamp: Long?): String {
        if (timestamp == null) return "No Due Date"
        val dueCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance()
        val pattern = if (dueCal.get(Calendar.YEAR) != nowCal.get(Calendar.YEAR)) {
            "EEE, MMM dd, yyyy • h:mm a"
        } else {
            "EEE, MMM dd • h:mm a"
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
    }

    fun parse(input: String): ParseResult {
        var text = input.trim()
        val cal = Calendar.getInstance()
        val nowCal = Calendar.getInstance()
        var hasExplicitDate = false
        var hasExplicitTime = false
        var detectedRecurrence = RecurrencePattern.NONE
        var detectedNote: String? = null

        fun applyDate(year: Int, month0Based: Int, day: Int) {
            val targetYear = year
            val targetMonth = month0Based
            var targetDay = day

            if (month0Based == Calendar.FEBRUARY && day >= 29) {
                if (!isLeapYear(targetYear)) {
                    targetDay = 28
                    detectedNote = "Note: Event is on Feb 29th (reminded on Feb 28th in non-leap years)"
                } else {
                    targetDay = 29
                    detectedNote = "Note: Event is on Feb 29th"
                }
            } else {
                cal.set(Calendar.YEAR, targetYear)
                cal.set(Calendar.MONTH, targetMonth)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                targetDay = targetDay.coerceAtMost(maxDay)
            }

            cal.set(Calendar.YEAR, targetYear)
            cal.set(Calendar.MONTH, targetMonth)
            cal.set(Calendar.DAY_OF_MONTH, targetDay)
        }

        fun removeMatch(match: MatchResult): String {
            return text.removeRange(match.range).trim().replace("\\s+".toRegex(), " ")
        }

        // 0. Clean task command prefixes like "task ,", "task:", "task -", "todo:", "remind me to", "add task", "remember to", "reschedule ... to", "postpone ... to"
        val prefixRegex = Regex("(?i)^\\s*(?:(?:add|new|create)\\s+(?:task|todo)|task|todo|please\\s+remind\\s+me\\s+to|remind\\s+me\\s+to|remember\\s+to|need\\s+to|don't\\s+forget\\s+to|have\\s+to|reschedule(?:\\s+(?:the|my)?\\s*task)?(?:\\s+to)?|postpone(?:\\s+(?:the|my)?\\s*task)?(?:\\s+to)?|move(?:\\s+(?:the|my)?\\s*task)?(?:\\s+to)?|push(?:\\s+(?:the|my)?\\s*task)?(?:\\s+to)?|delay(?:\\s+(?:the|my)?\\s*task)?(?:\\s+to)?|bump(?:\\s+(?:the|my)?\\s*task)?(?:\\s+to)?|change(?:\\s+(?:the|my)?\\s*(?:due\\s+)?(?:date|time))?(?:\\s+to)?|set(?:\\s+(?:the|my)?\\s*(?:due\\s+)?(?:date|time))?(?:\\s+to)?)\\s*[,:\\-]?\\s*")
        prefixRegex.find(text)?.let { match ->
            text = removeMatch(match)
        }

        // 1. Recurrence pattern detection
        val monthlyRegex = Regex("(?i)\\b(?:of\\s+every\\s+month|every\\s+month|each\\s+month|monthly)\\b")
        monthlyRegex.find(text)?.let { match ->
            detectedRecurrence = RecurrencePattern.MONTHLY
            text = removeMatch(match)
        }

        val dailyRegex = Regex("(?i)\\b(?:every\\s+day|each\\s+day|daily|everyday)\\b")
        dailyRegex.find(text)?.let { match ->
            detectedRecurrence = RecurrencePattern.DAILY
            text = removeMatch(match)
        }

        val weeklyRegex = Regex("(?i)\\b(?:every\\s+week|each\\s+week|weekly)\\b")
        weeklyRegex.find(text)?.let { match ->
            detectedRecurrence = RecurrencePattern.WEEKLY
            text = removeMatch(match)
        }

        val yearlyRegex = Regex("(?i)\\b(?:every\\s+year|each\\s+year|yearly|annually)\\b")
        yearlyRegex.find(text)?.let { match ->
            detectedRecurrence = RecurrencePattern.YEARLY
            text = removeMatch(match)
        }

        // Auto-detect yearly recurrence if birthday or anniversary is mentioned and no other recurrence pattern was found
        if (detectedRecurrence == RecurrencePattern.NONE && Regex("(?i)\\b(?:birthday|anniversary|bday)\\b").containsMatchIn(text)) {
            detectedRecurrence = RecurrencePattern.YEARLY
        }

        val everyDowRegex = Regex("(?i)\\b(?:every)\\s+(mon|tue|wed|thu|fri|sat|sun)(?:day|nes)?(?:day)?\\b")
        everyDowRegex.find(text)?.let { match ->
            detectedRecurrence = RecurrencePattern.WEEKLY
            val dayStr = match.groupValues[1].lowercase()
            val targetDay = when (dayStr) {
                "sun" -> Calendar.SUNDAY
                "mon" -> Calendar.MONDAY
                "tue" -> Calendar.TUESDAY
                "wed" -> Calendar.WEDNESDAY
                "thu" -> Calendar.THURSDAY
                "fri" -> Calendar.FRIDAY
                "sat" -> Calendar.SATURDAY
                else -> Calendar.MONDAY
            }
            val currentDay = cal.get(Calendar.DAY_OF_WEEK)
            var diff = targetDay - currentDay
            if (diff < 0) diff += 7
            cal.add(Calendar.DAY_OF_YEAR, diff)
            hasExplicitDate = true
            text = removeMatch(match)
        }

        // 2. "in X time" (e.g. in 30 mins, in 2 hours, in 3 days, in 2 months, in 5 years)
        val inRegex = Regex("(?i)\\b(in)\\s+(\\d+)\\s+(m|min|mins|minutes?|h|hr|hrs|hours?|d|days?|w|wks|weeks?|mo|mos|months?|y|yr|yrs|years?)\\b")
        inRegex.find(text)?.let { match ->
            val amount = match.groupValues[2].toIntOrNull() ?: 0
            val unit = match.groupValues[3].lowercase()
            if (unit.startsWith("mo") || unit.contains("month")) {
                cal.add(Calendar.MONTH, amount)
                hasExplicitDate = true
            } else if (unit.startsWith("y") || unit.contains("year")) {
                cal.add(Calendar.YEAR, amount)
                hasExplicitDate = true
            } else if (unit.startsWith("m")) {
                cal.add(Calendar.MINUTE, amount)
                hasExplicitTime = true
                hasExplicitDate = true
            } else if (unit.startsWith("h")) {
                cal.add(Calendar.HOUR, amount)
                hasExplicitTime = true
                hasExplicitDate = true
            } else if (unit.startsWith("d")) {
                cal.add(Calendar.DAY_OF_YEAR, amount)
                hasExplicitDate = true
            } else if (unit.startsWith("w")) {
                cal.add(Calendar.WEEK_OF_YEAR, amount)
                hasExplicitDate = true
            }
            text = removeMatch(match)
        }

        // 3. Named relative days: "today", "tomorrow", "tmrw", "tmr", "tonight", "next year"
        if (!hasExplicitDate) {
            val dayRegex = Regex("(?i)\\b(today|tomorrow|tmrw|tmr|tonight|next\\s+year)\\b")
            dayRegex.find(text)?.let { match ->
                val word = match.groupValues[1].lowercase()
                if (word == "tomorrow" || word == "tmrw" || word == "tmr") {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    hasExplicitDate = true
                } else if (word == "tonight") {
                    cal.set(Calendar.HOUR_OF_DAY, 20)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    hasExplicitTime = true
                    hasExplicitDate = true
                } else if (word.contains("year")) {
                    cal.add(Calendar.YEAR, 1)
                    hasExplicitDate = true
                } else if (word == "today") {
                    hasExplicitDate = true
                }
                text = removeMatch(match)
            }
        }

        // 4. ISO & Numeric dates (e.g. 2026-05-20, 2026/05/20, 20/05/2026, 05/20/2026)
        // Note: Avoid matching 1-2 digit time-like expressions like 9.30 or 09.30 with period by requiring slash/dash or 4-digit year.
        if (!hasExplicitDate) {
            val isoRegex = Regex("(?i)\\b((?:19|20|21)\\d{2})[-/.](0?[1-9]|1[0-2])[-/.](0?[1-9]|[12]\\d|3[01])\\b")
            isoRegex.find(text)?.let { match ->
                val year = match.groupValues[1].toIntOrNull()
                val month = match.groupValues[2].toIntOrNull()
                val day = match.groupValues[3].toIntOrNull()
                if (year != null && month != null && day != null) {
                    applyDate(year, month - 1, day)
                    hasExplicitDate = true
                    text = removeMatch(match)
                }
            } ?: run {
                val dmyRegex = Regex("(?i)\\b(?:on\\s+)?(0?[1-9]|[12]\\d|3[01])[-/](0?[1-9]|[12]\\d|3[01])[-/]((?:19|20|21)\\d{2})\\b")
                dmyRegex.find(text)?.let { match ->
                    val num1 = match.groupValues[1].toIntOrNull() ?: 1
                    val num2 = match.groupValues[2].toIntOrNull() ?: 1
                    val year = match.groupValues[3].toIntOrNull()
                    if (year != null) {
                        if (num1 > 12) {
                            applyDate(year, (num2 - 1).coerceIn(0, 11), num1)
                        } else if (num2 > 12) {
                            applyDate(year, (num1 - 1).coerceIn(0, 11), num2)
                        } else {
                            applyDate(year, (num1 - 1).coerceIn(0, 11), num2)
                        }
                        hasExplicitDate = true
                        text = removeMatch(match)
                    }
                }
            }
        }

        // 5. Month + Day (e.g., "may 20 2026", "may 20th", "15th of January", "Jan 15")
        if (!hasExplicitDate) {
            val monthNames = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
            val monthRegex = Regex("(?i)\\b(?:on\\s+)?(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s+(\\d{1,2})(?:\\s*(?:st|nd|rd|th))?(?:[\\s,]+((?:19|20|21)\\d{2}))?\\b")
            monthRegex.find(text)?.let { match ->
                val monthStr = match.groupValues[1].lowercase()
                val day = match.groupValues[2].toIntOrNull() ?: 1
                val yearStr = match.groupValues[3]
                val monthIdx = monthNames.indexOf(monthStr)
                if (monthIdx != -1) {
                    var targetYear = yearStr.toIntOrNull() ?: cal.get(Calendar.YEAR)
                    if (yearStr.isBlank()) {
                        val testCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, targetYear)
                            set(Calendar.MONTH, monthIdx)
                            set(Calendar.DAY_OF_MONTH, if (monthIdx == Calendar.FEBRUARY && day >= 29 && !isLeapYear(targetYear)) 28 else day)
                        }
                        if (testCal.timeInMillis < System.currentTimeMillis() - 86400000L) {
                            targetYear += 1
                        }
                    }
                    applyDate(targetYear, monthIdx, day)
                    hasExplicitDate = true
                    text = removeMatch(match)
                }
            } ?: run {
                val dayMonthRegex = Regex("(?i)\\b(?:on\\s+)?(\\d{1,2})(?:\\s*(?:st|nd|rd|th))?\\s+(?:of\\s+)?(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*(?:[\\s,]+((?:19|20|21)\\d{2}))?\\b")
                dayMonthRegex.find(text)?.let { match ->
                    val day = match.groupValues[1].toIntOrNull() ?: 1
                    val monthStr = match.groupValues[2].lowercase()
                    val yearStr = match.groupValues[3]
                    val monthIdx = monthNames.indexOf(monthStr)
                    if (monthIdx != -1) {
                        var targetYear = yearStr.toIntOrNull() ?: cal.get(Calendar.YEAR)
                        if (yearStr.isBlank()) {
                            val testCal = Calendar.getInstance().apply {
                                set(Calendar.YEAR, targetYear)
                                set(Calendar.MONTH, monthIdx)
                                set(Calendar.DAY_OF_MONTH, if (monthIdx == Calendar.FEBRUARY && day >= 29 && !isLeapYear(targetYear)) 28 else day)
                            }
                            if (testCal.timeInMillis < System.currentTimeMillis() - 86400000L) {
                                targetYear += 1
                            }
                        }
                        applyDate(targetYear, monthIdx, day)
                        hasExplicitDate = true
                        text = removeMatch(match)
                    }
                }
            }
        }

        // 6. Day of week (e.g. "on monday", "next friday", "this sunday")
        if (!hasExplicitDate) {
            val dowRegex = Regex("(?i)\\b(?:on\\s+|next\\s+|this\\s+)?(mon|tue|wed|thu|fri|sat|sun)(?:day|nes)?(?:day)?\\b")
            dowRegex.find(text)?.let { match ->
                val dayStr = match.groupValues[1].lowercase()
                val targetDay = when (dayStr) {
                    "sun" -> Calendar.SUNDAY
                    "mon" -> Calendar.MONDAY
                    "tue" -> Calendar.TUESDAY
                    "wed" -> Calendar.WEDNESDAY
                    "thu" -> Calendar.THURSDAY
                    "fri" -> Calendar.FRIDAY
                    "sat" -> Calendar.SATURDAY
                    else -> Calendar.MONDAY
                }
                val currentDay = cal.get(Calendar.DAY_OF_WEEK)
                var diff = targetDay - currentDay
                if (diff <= 0) diff += 7

                val isNext = match.value.lowercase().contains("next")
                if (isNext) diff += 7

                cal.add(Calendar.DAY_OF_YEAR, diff)
                hasExplicitDate = true
                text = removeMatch(match)
            }
        }

        // 7. Ordinal day with mandatory "st/nd/rd/th" OR strict "on [the] <day>"
        // (CRITICAL: Do NOT match lone digits without "on" or ordinal suffix!)
        if (!hasExplicitDate) {
            val ordinalDayRegex = Regex("(?i)\\b(?:on\\s+(?:the\\s+)?|the\\s+)?(\\d{1,2})\\s*(?:st|nd|rd|th)\\b")
            val strictOnDayRegex = Regex("(?i)\\bon\\s+(?:the\\s+)?(\\d{1,2})\\b")

            val match = ordinalDayRegex.find(text) ?: strictOnDayRegex.find(text)
            match?.let { m ->
                val day = m.groupValues[1].toIntOrNull() ?: 1
                if (day in 1..31) {
                    val nowDay = nowCal.get(Calendar.DAY_OF_MONTH)
                    var targetMonth = nowCal.get(Calendar.MONTH)
                    var targetYear = nowCal.get(Calendar.YEAR)
                    if (day < nowDay) {
                        targetMonth++
                        if (targetMonth > Calendar.DECEMBER) {
                            targetMonth = Calendar.JANUARY
                            targetYear++
                        }
                    }
                    applyDate(targetYear, targetMonth, day)
                    hasExplicitDate = true
                    text = removeMatch(m)
                }
            }
        }

        // 8. Time parsing: "9:30", "9.30", "at 9.30", "at 9:30 pm", "9:30pm", "to 5pm", "to 5:30", "at 5", "by 6pm", "9 o'clock"
        if (!hasExplicitTime) {
            // Check time with AM/PM (supporting ':' or '.' as separator, e.g. "9:30 pm", "9.30am", "9pm", "at 9:30 am", "to 5pm", "for 6pm")
            val timeAmPmRegex = Regex("(?i)\\b(?:at|by|to|for|around)?\\s*(\\d{1,2})(?:[:.](\\d{1,2}))?\\s*(am|pm)\\b")
            timeAmPmRegex.find(text)?.let { match ->
                var hour = match.groupValues[1].toIntOrNull() ?: 12
                val minute = match.groupValues[2].toIntOrNull() ?: 0
                val ampm = match.groupValues[3].lowercase()

                if (ampm == "pm" && hour < 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0

                if (hour in 0..23 && minute in 0..59) {
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, minute)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    hasExplicitTime = true
                    text = removeMatch(match)
                }
            } ?: run {
                // Check time with explicit ':' or '.' separator (e.g. "9:30", "9.30", "at 9:30", "to 5:30", "at 14:30", "14.30", "21:15")
                val timeSepRegex = Regex("(?i)\\b(?:at|by|to|for|around)?\\s*(\\d{1,2})[:.](\\d{2})\\b")
                timeSepRegex.find(text)?.let { match ->
                    val rawHour = match.groupValues[1].toIntOrNull() ?: 12
                    val minute = match.groupValues[2].toIntOrNull() ?: 0
                    if (rawHour in 0..23 && minute in 0..59) {
                        var hour = rawHour
                        // Check if followed or preceded by contextual time indicators like "in the morning", "in the evening", "in the afternoon", "tonight"
                        val lowerContext = text.lowercase()
                        val isEveningContext = lowerContext.contains("evening") || lowerContext.contains("night") || lowerContext.contains("pm")
                        val isMorningContext = lowerContext.contains("morning") || lowerContext.contains("am")
                        val isAfternoonContext = lowerContext.contains("afternoon")

                        if (isEveningContext && hour in 1..11) {
                            hour += 12
                        } else if (isAfternoonContext && hour in 1..6) {
                            hour += 12
                        } else if (isMorningContext && hour == 12) {
                            hour = 0
                        } else if (!isMorningContext && hour in 1..11) {
                            // Smart heuristic: if user specifies a small hour like 1..7 (e.g., 2:30, 5:30), assume afternoon/evening (14:30, 17:30)
                            // If user specifies 8..11 (e.g., 9:30, 10:30), check current time:
                            val nowHour = nowCal.get(Calendar.HOUR_OF_DAY)
                            if (hour in 1..7) {
                                hour += 12
                            } else if (nowHour in 12..23 && (hour + 12) > nowHour) {
                                hour += 12
                            }
                        }
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, minute)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        hasExplicitTime = true
                        text = removeMatch(match)
                    }
                } ?: run {
                    // Check "at <hour>", "to <hour>", "by <hour>", "for <hour>", or "<hour> o'clock" (e.g. "at 9", "to 5", "by 6", "9 o'clock", "5 oclock")
                    val timeAtRegex = Regex("(?i)\\b(?:(?:at|by|to|for|around)\\s+(\\d{1,2})|(\\d{1,2})\\s*o'?clock)\\b")
                    timeAtRegex.find(text)?.let { match ->
                        val h1 = match.groupValues[1].toIntOrNull()
                        val h2 = match.groupValues[2].toIntOrNull()
                        var hour = h1 ?: h2 ?: 12
                        if (hour in 0..23) {
                            val lowerContext = text.lowercase()
                            val isEveningContext = lowerContext.contains("evening") || lowerContext.contains("night") || lowerContext.contains("tonight")
                            val isAfternoonContext = lowerContext.contains("afternoon")
                            val isMorningContext = lowerContext.contains("morning")

                            if (isEveningContext && hour in 1..11) {
                                hour += 12
                            } else if (isAfternoonContext && hour in 1..6) {
                                hour += 12
                            } else if (!isMorningContext && hour in 1..11) {
                                val nowHour = nowCal.get(Calendar.HOUR_OF_DAY)
                                if (hour in 1..7) {
                                    hour += 12
                                } else if (nowHour in 12..23 && (hour + 12) > nowHour) {
                                    hour += 12
                                }
                            }
                            cal.set(Calendar.HOUR_OF_DAY, hour)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            hasExplicitTime = true
                            text = removeMatch(match)
                        }
                    } ?: run {
                        // Named time shortcuts: "noon", "midnight", "morning", "afternoon", "evening", "tonight"
                        val namedTimeRegex = Regex("(?i)\\b(noon|midnight|in\\s+the\\s+morning|in\\s+the\\s+afternoon|in\\s+the\\s+evening|at\\s+night|morning|afternoon|evening|tonight)\\b")
                        namedTimeRegex.find(text)?.let { match ->
                            val word = match.groupValues[1].lowercase()
                            when {
                                word == "noon" -> cal.set(Calendar.HOUR_OF_DAY, 12)
                                word == "midnight" -> cal.set(Calendar.HOUR_OF_DAY, 0)
                                word.contains("morning") -> cal.set(Calendar.HOUR_OF_DAY, 9)
                                word.contains("afternoon") -> cal.set(Calendar.HOUR_OF_DAY, 14)
                                word.contains("evening") -> cal.set(Calendar.HOUR_OF_DAY, 18)
                                word.contains("night") || word == "tonight" -> cal.set(Calendar.HOUR_OF_DAY, 20)
                            }
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            hasExplicitTime = true
                            text = removeMatch(match)
                        }
                    }
                }
            }
        }

        // Clean any leftover period-of-day phrases like "in the morning", "in the afternoon", "in the evening", "tonight" if not already removed
        val leftoverTimeOfDay = Regex("(?i)\\b(?:in\\s+the\\s+morning|in\\s+the\\s+afternoon|in\\s+the\\s+evening|at\\s+night)\\b")
        leftoverTimeOfDay.find(text)?.let { match ->
            text = removeMatch(match)
        }

        // 9. If time was set but NO explicit date was set:
        // If the parsed time is already past today, roll over to tomorrow!
        // Otherwise keep today!
        if (hasExplicitTime && !hasExplicitDate) {
            if (cal.timeInMillis < System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            hasExplicitDate = true
        }

        // 10. Clean leftover commas, punctuation, and prepositions
        var clean = text.trim()
            .replace(Regex("^[,:\\- ]+"), "")
            .replace(Regex("[,:\\- ]+$"), "")
            .replace(Regex("(?i)\\b(on|at|every|the|for|by|in)\\s*$"), "")
            .replace(Regex("^\\s*(on|at|every|the|for|by|in)\\b(?i)"), "")
            .replace(Regex("^[,:\\- ]+"), "")
            .replace(Regex("[,:\\- ]+$"), "")
            .trim()
            .replace("\\s+".toRegex(), " ")

        if (clean.isBlank()) {
            clean = input.trim()
        }

        if (hasExplicitDate && !hasExplicitTime) {
            cal.set(Calendar.HOUR_OF_DAY, 8)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        } else if (!hasExplicitDate && !hasExplicitTime && detectedRecurrence == RecurrencePattern.NONE) {
            return ParseResult(input.trim(), null, false, RecurrencePattern.NONE)
        } else if (!hasExplicitDate && detectedRecurrence != RecurrencePattern.NONE) {
            if (!hasExplicitTime) {
                cal.set(Calendar.HOUR_OF_DAY, 8)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis < System.currentTimeMillis()) {
                when (detectedRecurrence) {
                    RecurrencePattern.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                    RecurrencePattern.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                    RecurrencePattern.MONTHLY -> cal.add(Calendar.MONTH, 1)
                    RecurrencePattern.YEARLY -> cal.add(Calendar.YEAR, 1)
                    else -> {}
                }
            }
            hasExplicitDate = true
        }

        if (detectedNote == null && Regex("(?i)\\b(?:feb(?:ruary)?\\s*29(?:th)?|29(?:th)?\\s*(?:of\\s*)?feb(?:ruary)?)\\b").containsMatchIn(input)) {
            val curYear = cal.get(Calendar.YEAR)
            detectedNote = if (isLeapYear(curYear)) "Note: Event is on Feb 29th" else "Note: Event is on Feb 29th (reminded on Feb 28th in non-leap years)"
        }

        return ParseResult(clean, cal.timeInMillis, hasExplicitTime, detectedRecurrence, detectedNote)
    }
}
