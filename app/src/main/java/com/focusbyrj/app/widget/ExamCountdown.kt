package com.focusbyrj.app.widget

import android.content.Context
import org.json.JSONObject
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
    val countUp: Boolean = false, val endedText: String = "Exam day is here"
) {
    fun target(): ZonedDateTime = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(ZoneId.of(zone))
    fun validate() {
        require(name.isNotBlank() && name.length <= 100 && subtitle.length <= 200 && endedText.length <= 100)
        require(format in setOf("DAYS", "WEEKS", "HOURS") && palette in setOf("THEME", "DARK", "LIGHT", "CUSTOM"))
        require(align in setOf("START", "CENTER", "END"))
        require(opacity in 20..100 && radius in 0..40 && textSize in 24..64)
        listOf(background, foreground, accent).forEach { require(it.matches(Regex("#[0-9a-fA-F]{6}"))) }
        val local = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
        require(ZoneId.of(zone).rules.getValidOffsets(local).size == 1) { "This time is skipped or repeated by a clock change. Choose a different time." }
        require(!LocalDate.parse(startDate).isAfter(LocalDate.parse(date))) { "Progress start date must be before the exam" }
    }
    fun headline(now: Instant = Instant.now()): String {
        val target = target(); val current = now.atZone(target.zone)
        if (!now.isBefore(target.toInstant()) && !countUp) return endedText
        val elapsed = !now.isBefore(target.toInstant())
        val days = kotlin.math.abs(ChronoUnit.DAYS.between(current.toLocalDate(), target.toLocalDate()))
        val suffix = if (elapsed) "since" else "left"
        return when (format) {
            "WEEKS" -> "${days / 7}w ${days % 7}d $suffix"
            "HOURS" -> { val hours = kotlin.math.abs(Duration.between(now, target.toInstant()).toHours()); "${hours / 24}d ${hours % 24}h $suffix" }
            else -> if (days == 0L && !elapsed) "Today" else "$days ${if (days == 1L) "day" else "days"} $suffix"
        }
    }
    fun progress(now: Instant = Instant.now()): Int {
        val start = LocalDate.parse(startDate).atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli()
        val end = target().toInstant().toEpochMilli()
        return if (end <= start) 100 else (((now.toEpochMilli() - start).toDouble() / (end - start)) * 100).toInt().coerceIn(0, 100)
    }
    fun json(): String { validate(); return JSONObject().apply {
        put("name", name); put("subtitle", subtitle); put("date", date); put("time", time); put("zone", zone); put("start", startDate)
        put("format", format); put("palette", palette); put("background", background); put("foreground", foreground); put("accent", accent)
        put("opacity", opacity); put("radius", radius); put("textSize", textSize); put("align", align)
        put("showDate", showDate); put("showProgress", showProgress); put("countUp", countUp); put("endedText", endedText)
    }.toString() }
    companion object {
        fun read(raw: String): ExamCountdown {
            require(raw.length <= 5000); val j = JSONObject(raw)
            return ExamCountdown(j.getString("name"), j.getString("subtitle"), j.getString("date"), j.getString("time"), j.getString("zone"), j.getString("start"),
                j.getString("format"), j.getString("palette"), j.getString("background"), j.getString("foreground"), j.getString("accent"),
                j.getInt("opacity"), j.getInt("radius"), j.getInt("textSize"), j.getString("align"), j.getBoolean("showDate"), j.getBoolean("showProgress"), j.getBoolean("countUp"), j.getString("endedText")).also { it.validate() }
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
        val id = eventId(widget) ?: UUID.randomUUID().toString()
        check(events.edit().putString(id, value.json()).commit())
        check(bindings.edit().putString(widget.toString(), id).commit()); return id
    }
    fun removeWidget(widget: Int) { bindings.edit().remove(widget.toString()).apply() }
    fun rebind(old: Int, new: Int) { eventId(old)?.let { bindings.edit().putString(new.toString(), it).remove(old.toString()).apply() } }
}
