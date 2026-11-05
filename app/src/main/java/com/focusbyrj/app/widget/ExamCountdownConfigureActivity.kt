package com.focusbyrj.app.widget

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import com.focusbyrj.app.ui.security.installScreenCaptureProtection
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class ExamCountdownConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installScreenCaptureProtection()
        setResult(RESULT_CANCELED)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager = AppWidgetManager.getInstance(this)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
            manager.getAppWidgetInfo(widgetId)?.provider?.className != ExamCountdownProvider::class.java.name) { finish(); return }
        val store = ExamCountdownStore(this)
        val copied = if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID)
            intent.getStringExtra(INITIAL_CONFIG)?.let { runCatching { ExamCountdown.read(it) }.getOrNull() } else null
        val initial = store.get(widgetId) ?: copied ?: ExamCountdown()
        setContent { FocusByRjTheme {
            ExamCountdownConfigScreen(initial, store.all().map { it.second },
                saveLabel = if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) "Add widget" else "Apply",
                onCancel = { finish() }, onSave = { final ->
                    if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        store.save(widgetId, final)
                        ExamCountdownProvider.configurationChanged(this, widgetId)
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        finish()
                    } else {
                        check(manager.isRequestPinAppWidgetSupported) { "Long-press your home screen, choose Widgets, then Exam countdown." }
                        val callback = Intent(this, ExamCountdownProvider::class.java).setAction(ExamCountdownProvider.PINNED)
                            .setData(Uri.parse("ayva://exam-pin/${UUID.randomUUID()}"))
                            .putExtra("configuration", final.json())
                        val pending = PendingIntent.getBroadcast(this, 0, callback, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                        check(manager.requestPinAppWidget(ComponentName(this, ExamCountdownProvider::class.java), null, pending)) {
                            "Your launcher couldn't add the widget. Add it from your home screen's Widgets menu."
                        }
                        Toast.makeText(this, "Confirm the widget on your home screen.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                })
        } }
    }
    companion object { const val INITIAL_CONFIG = "exam_initial_configuration" }
}

// Draft encoding deliberately skips validation: unfinished names/colors/zones survive rotation.
private val CountdownDraftSaver = Saver<ExamCountdown, String>(save = { it.draftJson() }, restore = { runCatching { ExamCountdown.readDraft(it) }.getOrNull() })

@Composable
internal fun ExamCountdownConfigScreen(
    initialConfig: ExamCountdown,
    savedCountdowns: List<ExamCountdown>,
    onSave: (ExamCountdown) -> Unit,
    onCancel: () -> Unit,
    saveLabel: String = "Apply"
) {
    val context = LocalContext.current
    var draft by rememberSaveable(stateSaver = CountdownDraftSaver) { mutableStateOf(initialConfig) }
    var selectedId by rememberSaveable { mutableStateOf("primary") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val zones = remember { listOf(ZoneId.systemDefault().id, "Asia/Kolkata", "UTC", "Asia/Dubai", "Asia/Singapore", "Asia/Tokyo", "Europe/London", "Europe/Paris", "America/New_York", "America/Los_Angeles", "Australia/Sydney").distinct() }
    var customZone by rememberSaveable { mutableStateOf(initialConfig.zone !in zones) }
    var removing by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<ExamCountdown?>(null) }
    val exam = draft.exams().firstOrNull { it.id == selectedId } ?: draft.event()
    fun change(next: ExamCountdown) { draft = next; error = null }
    fun edit(next: ExamEvent) { change(draft.updateExam(next)) }
    fun pickDate(start: Boolean) {
        val date = runCatching { LocalDate.parse(if (start) exam.startDate else exam.date) }.getOrDefault(LocalDate.now())
        DatePickerDialog(context, { _, year, month, day ->
            val selected = LocalDate.of(year, month + 1, day).toString()
            edit(if (start) exam.copy(startDate = selected) else exam.copy(date = selected, startDate = minOf(LocalDate.parse(exam.startDate), LocalDate.parse(selected)).toString()))
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }
    fun prepared(): ExamCountdown {
        fun clean(e: ExamEvent) = e.copy(name = e.name.trim(), zone = e.zone.trim(), venue = e.venue.trim(), badge = e.badge.trim())
        return draft.withEvent(clean(draft.event())).copy(extraExams = draft.extraExams.map(::clean), widgetName = draft.widgetName.trim(),
            background = draft.background.trim(), foreground = draft.foreground.trim(), accent = draft.accent.trim()).also { it.validate() }
    }
    val label: (String) -> String = ExamWidgetPresentation::label
    WidgetEditor("Exam countdown", listOf("Exams", "Style", "Layout", "Behavior"),
        "${draft.widgetName.ifBlank { draft.name }} · ${draft.exams().size} ${if (draft.exams().size == 1) "exam" else "exams"}",
        onSave = { runCatching { onSave(prepared()) }.onFailure { error = it.message ?: "Check your settings." } },
        onCancel = onCancel, saveLabel = saveLabel, error = error,
        preview = { CountdownEditorPreview(draft) }) { section ->
        when (section) {
            0 -> {
                OutlinedTextField(draft.widgetName, { change(draft.copy(widgetName = it.take(60))) },
                    label = { Text("Widget name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("EXAMS IN THIS WIDGET · ${draft.exams().size}/8", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                draft.exams().forEachIndexed { index, item ->
                    val selected = item.id == exam.id
                    Surface(shape = RoundedCornerShape(14.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .2f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).testTag("exam_entry_${item.id}").clickable { selectedId = item.id }) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected, onClick = { selectedId = item.id })
                            Column(Modifier.weight(1f)) {
                                Text(item.name.ifBlank { "Exam ${index + 1}" }, style = MaterialTheme.typography.bodyMedium)
                                Text("${item.date} · ${item.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = draft.exams().size < 8, onClick = {
                        val id = UUID.randomUUID().toString()
                        val date = runCatching { LocalDate.parse(exam.date).plusDays(7).toString() }.getOrDefault(LocalDate.now().plusDays(30).toString())
                        change(draft.copy(extraExams = draft.extraExams + ExamEvent(id, "Exam ${draft.exams().size + 1}", "", date, "09:00", exam.zone, minOf(LocalDate.now(), LocalDate.parse(date)).toString()),
                            displayMode = if (draft.displayMode == "SINGLE") "LIST" else draft.displayMode))
                        selectedId = id
                    }) { Text("Add exam") }
                    TextButton(enabled = draft.exams().size > 1, onClick = { removing = true }) { Text("Remove exam") }
                }
                if (draft.exams().size > 1) {
                    WidgetChoiceRow("Display", draft.displayMode, ExamCountdown.DISPLAY_MODES, label, { change(draft.copy(displayMode = it)) })
                    if (draft.displayMode == "NEXT") Text("Shows the next future exam automatically. Once all exams finish, the most recent exam remains visible.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WidgetChoiceRow("Exam order", draft.sortOrder, listOf("CUSTOM", "DATE"), { if (it == "CUSTOM") "Your order" else "Soonest first" }, { change(draft.copy(sortOrder = it)) })
                    if (exam.id != "primary") TextButton(onClick = { change(draft.makePrimary(exam.id)); selectedId = "primary" }) { Text("Set as primary exam") }
                    if (draft.sortOrder == "CUSTOM" && exam.id != "primary" && draft.extraExams.size > 1) Row {
                        TextButton(enabled = exam.id != "primary" && draft.extraExams.indexOf(exam) > 0, onClick = {
                            val entries = draft.extraExams.toMutableList(); val i = entries.indexOf(exam)
                            java.util.Collections.swap(entries, i, i - 1); change(draft.copy(extraExams = entries))
                        }) { Text("Move up") }
                        TextButton(enabled = exam.id != "primary" && draft.extraExams.indexOf(exam) < draft.extraExams.lastIndex, onClick = {
                            val entries = draft.extraExams.toMutableList(); val i = entries.indexOf(exam)
                            java.util.Collections.swap(entries, i, i + 1); change(draft.copy(extraExams = entries))
                        }) { Text("Move down") }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                }
                Text("EDIT SELECTED EXAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(exam.name, { edit(exam.copy(name = it.take(100))) }, label = { Text("Exam name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(exam.message, { edit(exam.copy(message = it.take(200))) }, label = { Text("Personal message") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(exam.venue, { edit(exam.copy(venue = it.take(200))) }, label = { Text("Venue / location") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(exam.badge, { edit(exam.copy(badge = it.take(40))) }, label = { Text("Label · e.g. Prelims, Final") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                WidgetEditorRow("Exam date", exam.date, onClick = { pickDate(false) })
                WidgetEditorRow("Exam time", exam.time, onClick = {
                    val time = LocalTime.parse(exam.time)
                    TimePickerDialog(context, { _, hour, minute -> edit(exam.copy(time = LocalTime.of(hour, minute).toString())) }, time.hour, time.minute,
                        android.text.format.DateFormat.is24HourFormat(context)).show()
                })
                WidgetEditorRow("Progress starts", exam.startDate, onClick = { pickDate(true) })
                WidgetChoiceRow("Time zone", exam.zone, zones + "OTHER", { zone ->
                    if (zone == "OTHER") "Other time zone…" else zone.replace('_', ' ')
                }, { zone -> if (zone == "OTHER") customZone = true else { customZone = false; edit(exam.copy(zone = zone)) } })
                if (customZone || exam.zone !in zones) OutlinedTextField(exam.zone, { edit(exam.copy(zone = it.take(100))) }, label = { Text("Custom time zone") }, singleLine = true,
                    placeholder = { Text("Asia/Kolkata") }, modifier = Modifier.fillMaxWidth())
                if (savedCountdowns.isNotEmpty()) WidgetChoiceRow("Reuse saved countdown", draft, savedCountdowns,
                    { "${it.widgetName.ifBlank { it.name }} · ${it.exams().size} exams" }, { replacing = it }, selectedLabel = "Choose")
            }
            1 -> {
                WidgetChoiceRow("Design", draft.design, ExamCountdown.DESIGNS, label, { change(draft.copy(design = it)) },
                    detail = { when(it) { "HERO" -> "Centered, prominent countdown"; "TICKET" -> "Two-column exam pass"; "TIMELINE" -> "Progress first"; "MINIMAL" -> "Quiet, reduced layout"; "DASHBOARD" -> "Date-led study card"; else -> "Balanced title and countdown" } })
                WidgetChoiceRow("Theme", draft.palette, ExamCountdown.PALETTES, label, { change(draft.copy(palette = it)) },
                    swatch = { Color(runCatching { ExamCountdownProvider.colors(context, draft.copy(palette = it))[0] }.getOrDefault(android.graphics.Color.DKGRAY)) })
                if (draft.palette == "CUSTOM") {
                    OutlinedTextField(draft.background, { change(draft.copy(background = it)) }, label = { Text("Background · #RRGGBB") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draft.foreground, { change(draft.copy(foreground = it)) }, label = { Text("Text · #RRGGBB") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draft.accent, { change(draft.copy(accent = it)) }, label = { Text("Accent · #RRGGBB") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                WidgetChoiceRow("Background detail", draft.decoration, listOf("NONE", "GRADIENT", "ORBIT", "STRIPES"), label, { change(draft.copy(decoration = it)) })
                WidgetToggleRow("Transparent background", draft.transparent) { change(draft.copy(transparent = it)) }
                WidgetToggleRow("Outline border", draft.border) { change(draft.copy(border = it)) }
                WidgetSliderRow("Opacity", draft.opacity, "%", 20..100) { change(draft.copy(opacity = it)) }
                WidgetSliderRow("Corner radius", draft.radius, " dp", 0..40) { change(draft.copy(radius = it)) }
                TextButton(onClick = { runCatching { ExamCountdownStore(context).saveTemplate(prepared()) }
                    .onSuccess { Toast.makeText(context, "Layout saved. Find it in Widgets > Exam countdown > Saved layouts.", Toast.LENGTH_LONG).show() }
                    .onFailure { error = it.message ?: "Check your settings." } }) { Text("Save as reusable layout") }
            }
            2 -> {
                WidgetChoiceRow("Countdown format", draft.format, ExamCountdown.FORMATS, label, { change(draft.copy(format = it)) })
                if (draft.format == "WEEKDAYS") Text("Counts Monday–Friday, excluding today and including the exam date. Public holidays are not excluded.", style = MaterialTheme.typography.bodySmall)
                WidgetChoiceRow("Typeface", draft.font, listOf("SANS", "SERIF", "MONO"), label, { change(draft.copy(font = it)) })
                if (draft.displayMode == "LIST") WidgetSliderRow("Exam list text", draft.listTextSize, " sp", 11..20) { change(draft.copy(listTextSize = it)) }
                else {
                    WidgetSliderRow("Font size", draft.textSize, " sp", 24..64) { change(draft.copy(textSize = it)) }
                    WidgetSliderRow("Title size", draft.titleSize, " sp", 12..22) { change(draft.copy(titleSize = it)) }
                }
                WidgetSliderRow("Inner spacing", draft.padding, " dp", 8..28) { change(draft.copy(padding = it)) }
                if (draft.displayMode != "LIST") WidgetChoiceRow("Alignment", draft.align, listOf("START", "CENTER", "END"), label, { change(draft.copy(align = it)) })
                WidgetChoiceRow("Date style", draft.dateStyle, listOf("ISO", "SHORT", "LONG"), label, { change(draft.copy(dateStyle = it)) })
                WidgetChoiceRow("Clock format", draft.clockStyle, listOf("SYSTEM", "12", "24"), label, { change(draft.copy(clockStyle = it)) })
                if (draft.displayMode == "LIST") WidgetSliderRow("Maximum visible exams", draft.maxRows, "", 1..6) { change(draft.copy(maxRows = it)) }
                Text("Small widgets reduce details to fit. List and page modes show navigation when more exams are available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            3 -> {
                WidgetToggleRow("Show exam name", draft.showTitle) { change(draft.copy(showTitle = it)) }
                WidgetToggleRow("Show personal message", draft.showMessage) { change(draft.copy(showMessage = it)) }
                WidgetToggleRow("Show date", draft.showDate) { change(draft.copy(showDate = it)) }
                WidgetToggleRow("Show time", draft.showTime) { change(draft.copy(showTime = it)) }
                WidgetToggleRow("Show time zone", draft.showZone) { change(draft.copy(showZone = it)) }
                WidgetToggleRow("Show venue", draft.showVenue) { change(draft.copy(showVenue = it)) }
                WidgetToggleRow("Show label", draft.showBadge) { change(draft.copy(showBadge = it)) }
                WidgetToggleRow("Show preparation progress", draft.showProgress) { change(draft.copy(showProgress = it)) }
                if (draft.showProgress) {
                    WidgetToggleRow("Show progress percentage", draft.showPercent) { change(draft.copy(showPercent = it)) }
                    WidgetToggleRow("Show remaining progress", draft.remainingProgress) { change(draft.copy(remainingProgress = it)) }
                    Text("Progress measures calendar time in your preparation window, not study completion.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (draft.displayMode in setOf("LIST", "PAGED")) WidgetToggleRow("Hide finished exams", draft.hidePastExams) { change(draft.copy(hidePastExams = it)) }
                WidgetToggleRow("Count elapsed days after exam", draft.countUp) { change(draft.copy(countUp = it)) }
                if (!draft.countUp) OutlinedTextField(draft.endedText, { change(draft.copy(endedText = it.take(100))) }, label = { Text("Message when time is up") }, modifier = Modifier.fillMaxWidth())
                WidgetToggleRow("Urgency colors", draft.urgency) { change(draft.copy(urgency = it)) }
                if (draft.urgency) {
                    WidgetSliderRow("Urgent within", draft.urgentDays, " days", 0..90) { change(draft.copy(urgentDays = it, warningDays = maxOf(it, draft.warningDays))) }
                    WidgetSliderRow("Warning within", draft.warningDays, " days", draft.urgentDays..365) { change(draft.copy(warningDays = it)) }
                }
                WidgetToggleRow("Show edit shortcut", draft.showEdit) { change(draft.copy(showEdit = it)) }
                WidgetChoiceRow("Tap widget to", draft.tapAction, listOf("EDIT", "WIDGETS", "NONE"), label, { change(draft.copy(tapAction = it)) })
                Text("Countdowns refresh without waking your phone. This widget does not create exam notification alarms.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (removing) AlertDialog(onDismissRequest = { removing = false }, title = { Text("Remove ${exam.name.ifBlank { "this exam" }}?") },
        text = { Text("It will be removed from this widget's draft. Other widgets are unchanged. Apply to save the change.") },
        confirmButton = { TextButton(onClick = { change(draft.removeExam(exam.id)); selectedId = "primary"; removing = false }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text("Cancel") } })
    replacing?.let { saved -> AlertDialog(onDismissRequest = { replacing = null }, title = { Text("Load saved layout?") },
        text = { Text("This replaces the current draft with the saved exams and appearance. Apply to update this widget.") },
        confirmButton = { TextButton(onClick = { change(saved); selectedId = "primary"; replacing = null }) { Text("Load") } },
        dismissButton = { TextButton(onClick = { replacing = null }) { Text("Cancel") } }) }
}

/** Preview uses the same RemoteViews renderer as the home screen. */
@Composable
internal fun CountdownEditorPreview(draft: ExamCountdown) {
    val context = LocalContext.current
    var height by rememberSaveable { mutableIntStateOf(240) }
    var active by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(draft.exams(), draft.displayMode, draft.sortOrder, draft.hidePastExams) { active = "" }
    val safeZone = { zone: String -> runCatching { ZoneId.of(zone.trim()).id }.getOrDefault(ZoneId.systemDefault().id) }
    val safeColor = { color: String, fallback: String -> color.trim().takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: fallback }
    val safe = draft.copy(zone = safeZone(draft.zone), extraExams = draft.extraExams.map { it.copy(zone = safeZone(it.zone)) },
        background = safeColor(draft.background, "#101615"), foreground = safeColor(draft.foreground, "#E8F4EE"), accent = safeColor(draft.accent, "#8DC6B1"))
    WidgetChoiceRow("Preview height", height, listOf(140, 240, 360), { "${it} dp" }, { height = it })
    BoxWithConstraints(Modifier.fillMaxWidth().height(height.dp).testTag("exam_preview")) {
        val width = maxWidth.value.toInt().coerceIn(140, 600)
        AndroidView(factory = { FrameLayout(it) }, modifier = Modifier.fillMaxSize(), update = { host ->
            host.removeAllViews()
            runCatching {
                val rendered = ExamCountdownProvider.render(context, -99, safe, width, height, active)
                val view = rendered.apply(context, host)
                host.addView(view, FrameLayout.LayoutParams(-1, -1))
                // Preview navigation stays in the draft; it never sends a launcher broadcast.
                val page = ExamWidgetPresentation.page(safe, active, height, fontScale = context.resources.configuration.fontScale)
                view.findViewById<android.view.View>(com.focusbyrj.app.R.id.exam_next)?.setOnClickListener {
                    active = page.adjacentId(true)
                }
                view.findViewById<android.view.View>(com.focusbyrj.app.R.id.exam_previous)?.setOnClickListener {
                    active = page.adjacentId(false)
                }
                view.findViewById<android.view.View>(com.focusbyrj.app.R.id.exam_root)?.setOnClickListener(null)
                view.findViewById<android.view.View>(com.focusbyrj.app.R.id.exam_edit)?.setOnClickListener(null)
            }.onFailure { host.addView(android.widget.TextView(context).apply { text = "Complete the exam details to preview." }) }
        })
    }
}
