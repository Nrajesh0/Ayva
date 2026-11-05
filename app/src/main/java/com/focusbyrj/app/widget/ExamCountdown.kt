package com.focusbyrj.app.widget

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Event identity is portable; launcher widget IDs are deliberately device-local. */
data class ExamCountdown(
    val name: String = "My exam", val subtitle: String = "One step at a time",
    val date: String = LocalDate.now().plusDays(30).toString(), val time: String = "09:00",
    val zone: String = ZoneId.systemDefault().id, val startDate: String = LocalDate.now().toString(),
    val format: String = "DAYS", val palette: String = "THEME", val background: String = "#101615",
    val foreground: String = "#E8F4EE", val accent: String = "#8DC6B1",
    val opacity: Int = 100, val radius: Int = 24, val textSize: Int = 38,
    val align: String = "START", val showDate: Boolean = true, val showProgress: Boolean = true,
    val countUp: Boolean = false, val endedText: String = "Exam day is here",
    val venue: String = "", val badge: String = "", val extraExams: List<ExamEvent> = emptyList(),
    val design: String = "CLASSIC", val displayMode: String = "SINGLE", val sortOrder: String = "CUSTOM",
    val hidePastExams: Boolean = false, val maxRows: Int = 3,
    val font: String = "SANS", val titleSize: Int = 16, val padding: Int = 18,
    val showTitle: Boolean = true, val showMessage: Boolean = true, val showTime: Boolean = true,
    val showZone: Boolean = true, val showVenue: Boolean = true, val showBadge: Boolean = true,
    val showPercent: Boolean = false, val remainingProgress: Boolean = false,
    val dateStyle: String = "ISO", val clockStyle: String = "SYSTEM",
    val transparent: Boolean = false, val border: Boolean = false, val decoration: String = "NONE",
    val urgency: Boolean = false, val urgentDays: Int = 7, val warningDays: Int = 30,
    val showEdit: Boolean = true, val tapAction: String = "EDIT", val widgetName: String = "", val listTextSize: Int = 14
) {
    fun target(): ZonedDateTime = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(ZoneId.of(zone))
    fun validate() {
        require(name.isNotBlank()) { "Enter an exam name." }
        require(name.length <= 100 && subtitle.length <= 200 && endedText.length <= 100) { "An exam name or message is too long." }
        require(format in FORMATS && palette in PALETTES)
        require(align in setOf("START", "CENTER", "END"))
        require(opacity in 20..100 && radius in 0..40 && textSize in 24..64)
        listOf(background, foreground, accent).forEach { require(it.matches(Regex("#[0-9a-fA-F]{6}"))) }
        require(design in DESIGNS && displayMode in DISPLAY_MODES && sortOrder in setOf("CUSTOM", "DATE"))
        require(font in setOf("SANS", "SERIF", "MONO") && titleSize in 12..22 && listTextSize in 11..20 && padding in 8..28 && maxRows in 1..6)
        require(dateStyle in setOf("ISO", "SHORT", "LONG") && clockStyle in setOf("SYSTEM", "12", "24"))
        require(decoration in setOf("NONE", "GRADIENT", "ORBIT", "STRIPES") && tapAction in setOf("EDIT", "WIDGETS", "NONE"))
        require(urgentDays in 0..90 && warningDays in urgentDays..365) { "The warning threshold must be at least the urgent threshold." }
        require(extraExams.size <= 7 && venue.length <= 200 && badge.length <= 40 && widgetName.length <= 60)
        require(extraExams.map { it.id }.distinct().size == extraExams.size && extraExams.none { it.id == "primary" })
        extraExams.forEach { it.validate() }
        val local = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
        require(ZoneId.of(zone).rules.getValidOffsets(local).size == 1) { "This time is skipped or repeated by a clock change. Choose a different time." }
        require(!LocalDate.parse(startDate).isAfter(LocalDate.parse(date))) { "Progress start date cannot be after the exam date" }
    }
    fun headline(now: Instant = Instant.now()): String {
        val target = target(); val current = now.atZone(target.zone)
        if (!now.isBefore(target.toInstant()) && !countUp) return endedText
        val elapsed = !now.isBefore(target.toInstant())
        val days = kotlin.math.abs(ChronoUnit.DAYS.between(current.toLocalDate(), target.toLocalDate()))
        val suffix = if (elapsed) "since" else "left"
        return when (format) {
            "WEEKS" -> "${days / 7}w ${days % 7}d $suffix"
            "MONTHS" -> {
                val a = if (elapsed) target.toLocalDate() else current.toLocalDate()
                val b = if (elapsed) current.toLocalDate() else target.toLocalDate()
                val period = Period.between(a, b)
                "${period.years * 12 + period.months}mo ${period.days}d $suffix"
            }
            "WEEKDAYS" -> {
                val a = minOf(current.toLocalDate(), target.toLocalDate())
                val b = maxOf(current.toLocalDate(), target.toLocalDate())
                val weeks = ChronoUnit.DAYS.between(a, b) / 7
                val remainder = ChronoUnit.DAYS.between(a, b) % 7
                val workdays = weeks * 5 + (1..remainder).count { a.plusDays(it).dayOfWeek.value <= 5 }
                "$workdays weekdays $suffix"
            }
            "HOURS" -> { val hours = kotlin.math.abs(Duration.between(now, target.toInstant()).toHours()); "${hours / 24}d ${hours % 24}h $suffix" }
            else -> if (days == 0L && !elapsed) "Today" else "$days ${if (days == 1L) "day" else "days"} $suffix"
        }
    }
    fun progress(now: Instant = Instant.now()): Int {
        val start = LocalDate.parse(startDate).atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli()
        val end = target().toInstant().toEpochMilli()
        return if (end <= start) 100 else (((now.toEpochMilli() - start).toDouble() / (end - start)) * 100).toInt().coerceIn(0, 100)
    }
    fun event(): ExamEvent = ExamEvent("primary", name, subtitle, date, time, zone, startDate, venue, badge)
    fun exams(): List<ExamEvent> = listOf(event()) + extraExams
    fun withEvent(event: ExamEvent): ExamCountdown = copy(name = event.name, subtitle = event.message, date = event.date,
        time = event.time, zone = event.zone, startDate = event.startDate, venue = event.venue, badge = event.badge)
    fun updateExam(event: ExamEvent): ExamCountdown = if (event.id == "primary") withEvent(event)
        else copy(extraExams = extraExams.map { if (it.id == event.id) event else it })
    fun makePrimary(id: String): ExamCountdown {
        val selected = extraExams.firstOrNull { it.id == id } ?: return this
        return withEvent(selected).copy(extraExams = extraExams.map { if (it.id == id) event().copy(id = id) else it })
    }
    fun removeExam(id: String): ExamCountdown = if (id == "primary" && extraExams.isNotEmpty())
        withEvent(extraExams.first()).copy(extraExams = extraExams.drop(1)) else copy(extraExams = extraExams.filterNot { it.id == id })
    fun visibleExams(now: Instant = Instant.now()): List<ExamEvent> {
        val all = exams().let { if (sortOrder == "DATE" || displayMode == "NEXT") it.sortedBy { e -> e.target().toInstant() } else it }
        val filtered = if (hidePastExams || displayMode == "NEXT") all.filter { now.isBefore(it.target().toInstant()) } else all
        return filtered.ifEmpty { listOf(all.maxBy { it.target().toInstant() }) }
    }
    fun json(): String { validate(); return draftJson() }
    internal fun draftJson(): String = JSONObject().apply {
        put("name", name); put("subtitle", subtitle); put("date", date); put("time", time); put("zone", zone); put("start", startDate)
        put("format", format); put("palette", palette); put("background", background); put("foreground", foreground); put("accent", accent)
        put("opacity", opacity); put("radius", radius); put("textSize", textSize); put("align", align)
        put("showDate", showDate); put("showProgress", showProgress); put("countUp", countUp); put("endedText", endedText)
        put("venue", venue); put("badge", badge); put("exams", JSONArray(extraExams.map { it.json() }))
        put("design", design); put("displayMode", displayMode); put("sortOrder", sortOrder); put("hidePastExams", hidePastExams); put("maxRows", maxRows)
        put("font", font); put("titleSize", titleSize); put("padding", padding)
        put("showTitle", showTitle); put("showMessage", showMessage); put("showTime", showTime); put("showZone", showZone)
        put("showVenue", showVenue); put("showBadge", showBadge); put("showPercent", showPercent); put("remainingProgress", remainingProgress)
        put("dateStyle", dateStyle); put("clockStyle", clockStyle); put("transparent", transparent); put("border", border); put("decoration", decoration)
        put("urgency", urgency); put("urgentDays", urgentDays); put("warningDays", warningDays); put("showEdit", showEdit); put("tapAction", tapAction); put("widgetName", widgetName); put("listTextSize", listTextSize)
    }.toString()
    companion object {
        val FORMATS = listOf("DAYS", "WEEKS", "HOURS", "MONTHS", "WEEKDAYS")
        val PALETTES = listOf("THEME", "DARK", "LIGHT", "OCEAN", "FOREST", "ROSE", "AMBER", "LAVENDER", "SLATE", "CUSTOM")
        val DESIGNS = listOf("CLASSIC", "HERO", "TICKET", "MINIMAL", "TIMELINE", "DASHBOARD")
        val DISPLAY_MODES = listOf("SINGLE", "NEXT", "LIST", "PAGED")
        fun read(raw: String): ExamCountdown {
            return readDraft(raw).also { it.validate() }
        }
        internal fun readDraft(raw: String): ExamCountdown {
            require(raw.length <= 32000); val j = JSONObject(raw)
            return ExamCountdown(j.getString("name"), j.getString("subtitle"), j.getString("date"), j.getString("time"), j.getString("zone"), j.getString("start"),
                j.getString("format"), j.getString("palette"), j.getString("background"), j.getString("foreground"), j.getString("accent"),
                j.getInt("opacity"), j.getInt("radius"), j.getInt("textSize"), j.getString("align"), j.getBoolean("showDate"), j.getBoolean("showProgress"), j.getBoolean("countUp"), j.getString("endedText"),
                j.optString("venue", ""), j.optString("badge", ""), j.optJSONArray("exams")?.let { a ->
                    require(a.length() <= 7); (0 until a.length()).map { ExamEvent.read(a.getJSONObject(it)) }
                }.orEmpty(), j.optString("design", "CLASSIC"), j.optString("displayMode", "SINGLE"), j.optString("sortOrder", "CUSTOM"),
                j.optBoolean("hidePastExams", false), j.optInt("maxRows", 3), j.optString("font", "SANS"), j.optInt("titleSize", 16), j.optInt("padding", 18),
                j.optBoolean("showTitle", true), j.optBoolean("showMessage", true), j.optBoolean("showTime", j.getBoolean("showDate")), j.optBoolean("showZone", j.getBoolean("showDate")),
                j.optBoolean("showVenue", true), j.optBoolean("showBadge", true), j.optBoolean("showPercent", false), j.optBoolean("remainingProgress", false),
                j.optString("dateStyle", "ISO"), j.optString("clockStyle", "SYSTEM"), j.optBoolean("transparent", false), j.optBoolean("border", false),
                j.optString("decoration", "NONE"), j.optBoolean("urgency", false), j.optInt("urgentDays", 7), j.optInt("warningDays", 30),
                j.optBoolean("showEdit", false), j.optString("tapAction", "EDIT"), j.optString("widgetName", ""), j.optInt("listTextSize", 14))
        }
    }
}
class ExamCountdownStore(context: Context) {
    private val app = context.applicationContext
    private val events = app.getSharedPreferences("exam_countdowns", Context.MODE_PRIVATE)
    private val bindings = app.getSharedPreferences("exam_widget_bindings", Context.MODE_PRIVATE)
    fun all(): List<Pair<String, ExamCountdown>> = events.all.mapNotNull { (id, raw) -> runCatching { id to ExamCountdown.read(raw as String) }.getOrNull() }.sortedBy { it.second.date }
    fun eventId(widget: Int): String? = bindings.getString(widget.toString(), null)
    fun get(widget: Int): ExamCountdown? = eventId(widget)?.let { id -> events.getString(id, null)?.let { runCatching { ExamCountdown.read(it) }.getOrNull() } }
    fun save(widget: Int, value: ExamCountdown): String {
        // Editing a widget creates its own event: another instance never changes unexpectedly.
        val previous = get(widget)
        val id = eventId(widget) ?: UUID.randomUUID().toString()
        check(events.edit().putString(id, value.json()).commit())
        val binding = bindings.edit().putString(widget.toString(), id)
        if (previous == null || previous.exams() != value.exams() || previous.sortOrder != value.sortOrder ||
            previous.displayMode != value.displayMode || previous.hidePastExams != value.hidePastExams) binding.remove("active_$widget")
        check(binding.commit()); return id
    }
    fun saveTemplate(value: ExamCountdown): String = UUID.randomUUID().toString().also { id ->
        check(events.edit().putString(id, value.json()).commit())
    }
    fun inUse(id: String): Boolean = bindings.all.any { (key, value) -> key.toIntOrNull() != null && value == id }
    fun deleteTemplate(id: String): Boolean = !inUse(id) && events.edit().remove(id).commit()
    fun activeExam(widget: Int): String = bindings.getString("active_$widget", "") ?: ""
    fun setActiveExam(widget: Int, event: String) { bindings.edit().putString("active_$widget", event).apply() }
    fun removeWidget(widget: Int) { bindings.edit().remove(widget.toString()).remove("active_$widget").apply() }
    fun rebind(old: Int, new: Int) { eventId(old)?.let { bindings.edit().putString(new.toString(), it).putString("active_$new", activeExam(old)).remove(old.toString()).remove("active_$old").apply() } }
}

/** One exam in a widget; visual settings belong to the widget, not this event. */
data class ExamEvent(
    val id: String, val name: String, val message: String, val date: String, val time: String,
    val zone: String, val startDate: String, val venue: String = "", val badge: String = ""
) {
    fun target(): ZonedDateTime = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(ZoneId.of(zone))
    fun validate() {
        require(id.isNotBlank() && id.length <= 100)
        ExamCountdown(name = name, subtitle = message, date = date, time = time, zone = zone,
            startDate = startDate, venue = venue, badge = badge).validate()
    }
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("message", message).put("date", date)
        .put("time", time).put("zone", zone).put("start", startDate).put("venue", venue).put("badge", badge)
    companion object {
        fun read(j: JSONObject) = ExamEvent(j.getString("id"), j.getString("name"), j.getString("message"), j.getString("date"),
            j.getString("time"), j.getString("zone"), j.getString("start"), j.optString("venue"), j.optString("badge"))
    }
}
