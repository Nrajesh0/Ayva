package com.focusbyrj.app.util.assistant

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Small compositional grammar. Unknown clauses are repaired, never executed as tasks. */
internal object PlanLanguage {
    fun normalize(text: String) = ConversationLanguage.space(text)
    fun command(text: String): String {
        val raw = normalize(text)
        val prefix = if (raw.startsWith("/plan ", true)) "/plan " else ""
        var body = if (prefix.isEmpty()) raw else raw.substringAfter(' ')
        body = body.replaceFirst(Regex("(?i)^(?:please |could you |can you )"), "")
        body = body.replaceFirst(Regex("(?i)^i need to (?=(?:move|reschedule|schedule|rename)\\b)"), "")
        val aliases = mapOf("schudle" to "schedule", "schdule" to "schedule", "schedual" to "schedule", "scheduel" to "schedule", "reschudle" to "move")
        val first = body.substringBefore(' ')
        aliases[first.lowercase(Locale.ROOT)]?.let { body = it + body.removePrefix(first) }
        return prefix + body
    }
    fun startsPlanning(text: String) = TripChecklistConversation.starts(text) || Regex("""(?i)^(?:i (?:want|would like) to (?:go on|plan) (?:a )?(?:trip|holiday|vacation)\b|(?:let'?s|can we|could we|help me) (?:plan|organize|organise)(?: for)? (?!it\b|that\b|this\b).+)""").containsMatchIn(normalize(text))
    fun domain(text: String) = when {
        Regex("(?i)\\b(trip|holiday|vacation|travel)\\b").containsMatchIn(text) -> "trip"
        Regex("(?i)\\b(exam|study|revision|studying)\\b").containsMatchIn(text) -> "study"
        Regex("(?i)\\b(project|launch|build|release)\\b").containsMatchIn(text) -> "project"
        Regex("(?i)\\b(event|party|wedding|celebration|conference)\\b").containsMatchIn(text) -> "event"
        Regex("(?i)\\b(errand|errands|shopping|groceries)\\b").containsMatchIn(text) -> "errands"
        Regex("(?i)\\b(routine|morning|evening|weekly review)\\b").containsMatchIn(text) -> "routine"
        else -> "general"
    }
    fun duration(text: String): Int? {
        val value = normalize(text).lowercase(Locale.ROOT).removePrefix("for ").removePrefix("duration: ").removePrefix("duration ")
        if (value.length > 150) return null
        val units = Regex("(.+?)\\s*(minutes?|mins?|m|hours?|hrs?|h)\\b").findAll(value).toList()
        if (units.isEmpty()) return null
        var total = 0.0
        var end = 0
        var lastScale = 1
        for (unit in units) {
            if (unit.range.first != end) return null
            val words = unit.groupValues[1].trim().removePrefix("and ").trim()
            val quantity = quantity(words) ?: return null
            lastScale = if (unit.groupValues[2].startsWith("h")) 60 else 1
            total += quantity * lastScale
            end = unit.range.last + 1
        }
        val tail = value.drop(end).trim()
        if (tail.isNotEmpty()) {
            if (!tail.startsWith("and ")) return null
            val fraction = quantity(tail.removePrefix("and "))?.takeIf { it > 0 && it < 1 } ?: return null
            total += fraction * lastScale
        }
        return total.takeIf { it.isFinite() && it in 1.0..1440.0 && it == it.toInt().toDouble() }?.toInt()
    }
    private fun quantity(input: String): Double? {
        val words = input.replace(Regex("(?<=\\p{L})-(?=\\p{L})"), " ").trim()
        if (words.matches(Regex("(?:\\d+(?:\\.\\d+)?|\\.\\d+)")))
            return words.toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }
        if (words in setOf("a", "an")) return 1.0
        if (words in setOf("half", "a half", "half a", "half an")) return 0.5
        if (words in setOf("quarter", "a quarter", "quarter of an", "a quarter of an")) return 0.25
        Regex("^(.+) and (a half|a quarter)$").matchEntire(words)?.let {
            return quantity(it.groupValues[1])?.plus(quantity(it.groupValues[2]) ?: return null)
        }
        val small = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
        small.indexOf(words).takeIf { it >= 0 }?.let { return it.toDouble() }
        val tens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)
        val parts = words.split(' ')
        val ten = tens[parts.first()] ?: return null
        if (parts.size == 1) return ten.toDouble()
        return if (parts.size == 2) small.indexOf(parts[1]).takeIf { it in 1..9 }?.let { (ten + it).toDouble() } else null
    }

    /** Split a reference from a complete duration expression, rather than guessing a title boundary. */
    fun durationSuffix(text: String): Pair<String, String>? {
        if (text.length > 400) return null
        for (index in text.indices.filter { text[it].isWhitespace() }) {
            val value = text.substring(index).trim()
            if (duration(value) != null) {
                val reference = text.substring(0, index).trim().replace(Regex("(?i) (?:take|for)$"), "")
                if (reference.endsWith('-') || reference.endsWith('+')) return null
                return reference to value
            }
        }
        return null
    }
    fun correctionValue(text: String): String? = Regex("(?i)^(?:actually|no|instead|i meant|make that),?\\s+(.+)$").matchEntire(normalize(text))?.groupValues?.get(1)
    fun clauses(text: String): List<String> = ConversationMeaning.clauses(normalize(text)).map { it.text }
    fun references(plan: PlanWorkspace, phrase: String): List<PlanStep> {
        val text = normalize(phrase).lowercase(Locale.ROOT).trim('"', '\'').removePrefix("the ")
            .replace(Regex("^(?:step|item|task)\\s+"), "").removeSuffix(" task").removeSuffix(" step")
        if (text in setOf("it", "that", "that one", "this", "this one")) return plan.steps.filter { it.id == plan.selected }
        val ordinal = mapOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "last" to plan.steps.size)
        val number = text.toIntOrNull() ?: ordinal[text.removeSuffix(" one")]
        if (number != null) return listOfNotNull(plan.steps.getOrNull(number - 1))
        val exact = plan.steps.filter { it.title.equals(text, true) || it.id == text }
        if (exact.isNotEmpty()) return exact
        val words = text.split(' ').filter { it.length > 2 }.toSet()
        if (words.isEmpty()) return emptyList()
        return plan.steps.filter { step -> words.all { word -> Regex("(?i)\\b" + Regex.escape(word) + "\\b").containsMatchIn(step.title) } }
    }
    fun facts(text: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        Regex("(?i)\\b(?:trip to|going to|destination(?: is|:)?|to) ([\\p{L}][\\p{L} -]{1,50}?)(?=[,;.!]| for | next | in |$)").find(text)?.let { result["destination"] = it.groupValues[1].trim() }
        Regex("(?i)\\b(\\d+|one|two|three|four|five|six|seven|ten) days?\\b").find(text)?.let { result["trip length"] = it.value }
        Regex("(?i)(?:₹|\\$|€|£|rs\\.?\\s*|inr\\s*|budget(?: is|:)?\\s*)([\\d,]+)").find(text)?.let { result["budget"] = it.value }
        Regex("(?i)\\b(?:next month|next week|this weekend|dates (?:aren't|are not) fixed)\\b").find(text)?.let { result["timing"] = it.value }
        if (Regex("(?i)\\bafter work\\b").containsMatchIn(text)) result["availability note"] = "after work (hours not set)"
        Regex("(?i)\\bfrom ([\\p{L}][\\p{L} -]{1,40}?)(?=[,;.!]| for | next |$)").find(text)?.let { result["departure city"] = it.groupValues[1].trim() }
        return result
    }

    data class DateResult(val at: Long? = null, val question: String? = null)
    fun date(text: String, now: Long, zone: String, deadline: Boolean = false, referenceTime: Long = now): DateResult {
        val value = normalize(text).lowercase(Locale.ROOT)
        val cal = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { timeInMillis = referenceTime }
        val days = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
        val named = days.firstOrNull { Regex("\\b$it\\b").containsMatchIn(value) }
        val iso = Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b").find(value)
        when {
            iso != null -> { cal.set(Calendar.YEAR, iso.groupValues[1].toInt()); cal.set(Calendar.MONTH, iso.groupValues[2].toInt() - 1); cal.set(Calendar.DAY_OF_MONTH, iso.groupValues[3].toInt()) }
            "day after tomorrow" in value -> cal.add(Calendar.DAY_OF_YEAR, 2)
            "tomorrow" in value -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "today" in value || "tonight" in value -> Unit
            named != null -> {
                var delta = ((days.indexOf(named) + 1) - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                if (delta == 0) delta = 7
                if ("next " in value) delta += 7
                cal.add(Calendar.DAY_OF_YEAR, delta)
            }
            else -> return DateResult(question = "Which day? You can say tomorrow, Saturday, or an exact date such as 2027-03-18.")
        }
        var timed = value
        val numbers = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
        numbers.forEachIndexed { i, word -> timed = timed.replace(Regex("\\bat $word\\b"), "at ${i + 1}") }
        val time = Regex("(?:\\bat\\s+|\\b)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b|\\bat\\s+(\\d{1,2})(?::(\\d{2}))?\\b").find(timed)
        var hour: Int; var minute = 0
        if (time != null) {
            val meridiem = time.groupValues[3]
            hour = (time.groupValues[1].ifEmpty { time.groupValues[4] }).toInt()
            minute = time.groupValues[2].ifEmpty { time.groupValues[5] }.ifEmpty { "0" }.toInt()
            if (meridiem.isNotEmpty()) {
                if (hour !in 1..12) return DateResult(question = "Use an hour from 1 to 12 with AM or PM.")
                hour = hour % 12 + if (meridiem == "pm") 12 else 0
            } else if (hour in 1..12) return DateResult(question = "Do you mean ${hour}:${minute.toString().padStart(2, '0')} AM or PM? Please include the day again.")
        } else when {
            "morning" in value -> hour = 9
            "afternoon" in value -> hour = 13
            "evening" in value || "tonight" in value -> hour = 18
            deadline -> { hour = 23; minute = 59 }
            else -> return DateResult(question = "What time on that day? Include AM or PM; ‘Saturday morning’ uses 9 AM.")
        }
        if (hour !in 0..23 || minute !in 0..59) return DateResult(question = "That time isn't valid. Which day and time did you mean?")
        cal.isLenient = false
        cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val at = runCatching { cal.timeInMillis }.getOrNull() ?: return DateResult(question = "That local date/time does not exist in $zone. Choose another time.")
        if (at <= now) return DateResult(question = "That time has already passed. Which future day and time should I use?")
        val repeated = listOf(at - 3_600_000L, at + 3_600_000L).any { other ->
            val c = Calendar.getInstance(cal.timeZone).apply { timeInMillis = other }
            c.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) && c.get(Calendar.YEAR) == cal.get(Calendar.YEAR) && c.get(Calendar.HOUR_OF_DAY) == hour && c.get(Calendar.MINUTE) == minute
        }
        return if (repeated) DateResult(question = "That clock time occurs twice during a time-zone change. Choose an unambiguous time outside that repeated hour.") else DateResult(at)
    }
}
