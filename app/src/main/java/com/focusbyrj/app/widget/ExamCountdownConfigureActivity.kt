package com.focusbyrj.app.widget

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
        val initial = store.get(widgetId) ?: copied ?: ExamCountdown.newWidget()
        setContent { FocusByRjTheme {
            ExamCountdownConfigScreen(initial, store.all().filterNot { store.inUse(it.first) }.map { it.second },
                saveLabel = if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) "Add widget" else "Apply",
                widgetSize = if(widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) ExamWidgetSizes.current(this,manager.getAppWidgetOptions(widgetId)).let{it.width.toInt() to it.height.toInt()} else null,
                initialActive = store.activeExam(widgetId),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExamCountdownConfigScreen(
    initialConfig: ExamCountdown,
    savedCountdowns: List<ExamCountdown>,
    onSave: (ExamCountdown) -> Unit,
    onCancel: () -> Unit,
    saveLabel: String = "Apply",
    widgetSize: Pair<Int,Int>? = null,
    initialActive: String = ""
) {
    val context = LocalContext.current
    var draft by rememberSaveable(stateSaver = CountdownDraftSaver) { mutableStateOf(initialConfig) }
    var previewMode by rememberSaveable { mutableStateOf(if(widgetSize!=null) "Actual" else "Wide") }
    var previewActive by rememberSaveable { mutableStateOf(initialActive) }
    var lightWallpaper by rememberSaveable { mutableStateOf(false) }
    val previewSize = when(previewMode) { "Actual" -> widgetSize ?: (340 to 180); "Square" -> 280 to 280; "Tall" -> 280 to 360; else -> 340 to 180 }
    var selectedId by rememberSaveable { mutableStateOf("primary") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val zones = remember { listOf(ZoneId.systemDefault().id, "Asia/Kolkata", "UTC", "Asia/Dubai", "Asia/Singapore", "Asia/Tokyo", "Europe/London", "Europe/Paris", "America/New_York", "America/Los_Angeles", "Australia/Sydney").distinct() }
    var customZone by rememberSaveable { mutableStateOf(initialConfig.zone !in zones) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<ExamCountdown?>(null) }
    val exam = draft.exams().firstOrNull { it.id == selectedId } ?: draft.event()
    fun change(next: ExamCountdown) {
        if(next.exams()!=draft.exams() || next.displayMode!=draft.displayMode || next.sortOrder!=draft.sortOrder || next.hidePastExams!=draft.hidePastExams) previewActive=""
        draft = next; error = null
    }
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
    ExamEditor(onCancel, onSave = { runCatching { onSave(prepared()) }.onFailure { error = it.message ?: "Check your settings." } },
        saveLabel = saveLabel, error = error, preview = { CountdownEditorPreview(draft,previewSize.first,previewSize.second,previewMode,widgetSize!=null,{previewMode=it},previewActive,{previewActive=it},lightWallpaper,{lightWallpaper=it}) }) { section ->
        when (section) {
            0 -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your exams", style = MaterialTheme.typography.titleMedium)
                        Text("${draft.exams().size} of 8 · tap an exam to edit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(enabled = draft.exams().size < 8, onClick = {
                        val id = UUID.randomUUID().toString()
                        val date = runCatching { LocalDate.parse(exam.date).plusDays(7).toString() }.getOrDefault(LocalDate.now().plusDays(30).toString())
                        change(draft.copy(extraExams = draft.extraExams + ExamEvent(id, "Exam ${draft.exams().size + 1}", "", date, "09:00", exam.zone, minOf(LocalDate.now(), LocalDate.parse(date)).toString()),
                            displayMode = if (draft.displayMode == "SINGLE") "LIST" else draft.displayMode))
                        selectedId = id; editing = true
                    }) { Text("Add exam") }
                }
                ExamGroup("Schedule") {
                    draft.exams().forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth().testTag("exam_entry_${item.id}").clickable { selectedId = item.id; editing = true }
                            .padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .1f)) {
                                Text("%02d".format(index + 1), Modifier.padding(10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.name.ifBlank { "Untitled exam" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(runCatching { LocalDate.parse(item.date).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")) }.getOrDefault(item.date),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Edit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (draft.exams().size > 1) ExamGroup("How it appears") {
                    WidgetChoiceRow("Show", draft.displayMode, ExamCountdown.DISPLAY_MODES, label, { change(draft.copy(displayMode = it)) })
                    if (draft.displayMode != "SINGLE") {
                        WidgetChoiceRow("Order", draft.sortOrder, listOf("CUSTOM", "DATE"), { if (it == "CUSTOM") "Your order" else "Soonest first" }, { change(draft.copy(sortOrder = it)) })
                        if (draft.displayMode in listOf("LIST", "PAGED")) WidgetToggleRow("Hide finished exams", draft.hidePastExams) { change(draft.copy(hidePastExams = it)) }
                    }
                }
                ExamMore("Saved layouts") {
                    OutlinedTextField(draft.widgetName, { change(draft.copy(widgetName = it.take(60))) }, label = { Text("Layout name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { runCatching { ExamCountdownStore(context).saveTemplate(prepared()) }
                        .onSuccess { Toast.makeText(context, "Layout saved in Exam widgets.", Toast.LENGTH_LONG).show() }
                        .onFailure { error = it.message ?: "Check your settings." } }) { Text("Save this layout") }
                    if (savedCountdowns.isNotEmpty()) WidgetChoiceRow("Load a layout", draft, savedCountdowns,
                        { it.widgetName.ifBlank { it.name } }, { replacing = it }, selectedLabel = "Choose")
                }
            }
            1 -> {
                ExamDesignPicker(draft, ::change,previewSize.first,previewSize.second,previewActive,lightWallpaper)
                ExamPalettePicker(draft, ::change)
                ExamGroup("Surface") {
                    WidgetToggleRow("Transparent background", draft.transparent) { change(draft.copy(transparent = it)) }
                    if (!draft.transparent) WidgetSliderRow("Background opacity", draft.opacity, "%", 20..100) { change(draft.copy(opacity = it)) }
                }
                ExamMore("Fine-tune appearance") {
                    WidgetChoiceRow("All colors", draft.palette, ExamCountdown.PALETTES, label, { change(draft.copy(palette = it)) })
                    if (draft.palette == "CUSTOM") {
                        OutlinedTextField(draft.background, { change(draft.copy(background = it)) }, label = { Text("Background · #RRGGBB") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(draft.foreground, { change(draft.copy(foreground = it)) }, label = { Text("Text · #RRGGBB") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(draft.accent, { change(draft.copy(accent = it)) }, label = { Text("Accent · #RRGGBB") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    WidgetSliderRow("Corners", draft.radius, " dp", 0..40) { change(draft.copy(radius = it)) }
                    WidgetToggleRow("Outline", draft.border) { change(draft.copy(border = it)) }
                    WidgetChoiceRow("Background detail", draft.decoration, listOf("NONE", "GRADIENT", "ORBIT", "STRIPES"), label, { change(draft.copy(decoration = it)) })
                }
            }
            2 -> {
                ExamTextControls(draft,::change)
                ExamVisibleDetails(draft,::change)
                ExamMore("Preparation timeline") {
                    WidgetToggleRow("Show timeline",draft.showProgress){change(draft.copy(showProgress=it))}
                    if(draft.showProgress) {
                        Text("Measures time in your preparation window, not study completion.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        WidgetToggleRow("Show percentage",draft.showPercent){change(draft.copy(showPercent=it))}
                        WidgetToggleRow("Show time remaining",draft.remainingProgress){change(draft.copy(remainingProgress=it))}
                    }
                }
                ExamMore("After the exam") {
                    WidgetToggleRow("Count days since exam", draft.countUp) { change(draft.copy(countUp = it)) }
                    if (!draft.countUp) OutlinedTextField(draft.endedText, { change(draft.copy(endedText = it.take(100))) }, label = { Text("Finished message") }, modifier = Modifier.fillMaxWidth())
                }
                ExamMore("Widget interactions") {
                    WidgetToggleRow("Edit shortcut", draft.showEdit) { change(draft.copy(showEdit = it)) }
                    WidgetChoiceRow("On tap", draft.tapAction, listOf("EDIT", "WIDGETS", "NONE"), label, { change(draft.copy(tapAction = it)) })
                    WidgetToggleRow("Deadline colors", draft.urgency) { change(draft.copy(urgency = it)) }
                    if (draft.urgency) {
                        WidgetSliderRow("Urgent within", draft.urgentDays, " days", 0..90) { change(draft.copy(urgentDays = it, warningDays = maxOf(it, draft.warningDays))) }
                        WidgetSliderRow("Warning within", draft.warningDays, " days", draft.urgentDays..365) { change(draft.copy(warningDays = it)) }
                    }
                }
                Text("Resizes to fit your home screen. Refreshes quietly; exam reminders are managed separately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (editing) ModalBottomSheet(onDismissRequest = { editing = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Exam details", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { editing = false }) { Text("Done") }
            }
            OutlinedTextField(exam.name, { edit(exam.copy(name = it.take(100))) }, label = { Text("Exam name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            WidgetEditorRow("Date", exam.date, onClick = { pickDate(false) })
            WidgetEditorRow("Time", exam.time, onClick = {
                val time = runCatching { LocalTime.parse(exam.time) }.getOrDefault(LocalTime.of(9, 0))
                TimePickerDialog(context, { _, hour, minute -> edit(exam.copy(time = LocalTime.of(hour, minute).toString())) }, time.hour, time.minute,
                    android.text.format.DateFormat.is24HourFormat(context)).show()
            })
            ExamMore("More exam details") {
                OutlinedTextField(exam.venue, { edit(exam.copy(venue = it.take(200))) }, label = { Text("Venue") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(exam.badge, { edit(exam.copy(badge = it.take(40))) }, label = { Text("Label · e.g. Prelims") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(exam.message, { edit(exam.copy(message = it.take(200))) }, label = { Text("Personal message") }, modifier = Modifier.fillMaxWidth())
                WidgetEditorRow("Preparation starts", exam.startDate, onClick = { pickDate(true) })
                WidgetChoiceRow("Time zone", exam.zone, zones + "OTHER", { if (it == "OTHER") "Other…" else it.replace('_', ' ') },
                    { if (it == "OTHER") customZone = true else { customZone = false; edit(exam.copy(zone = it)) } })
                if (customZone || exam.zone !in zones) OutlinedTextField(exam.zone, { edit(exam.copy(zone = it.take(100))) }, label = { Text("Custom time zone") }, modifier = Modifier.fillMaxWidth())
                if (exam.id != "primary") TextButton(onClick = { change(draft.makePrimary(exam.id)); selectedId = "primary" }) { Text("Set as primary exam") }
                val index = draft.extraExams.indexOfFirst { it.id == exam.id }
                if (draft.sortOrder == "CUSTOM" && index >= 0 && draft.extraExams.size > 1) Row {
                    TextButton(enabled = index > 0, onClick = { val entries = draft.extraExams.toMutableList(); java.util.Collections.swap(entries, index, index - 1); change(draft.copy(extraExams = entries)) }) { Text("Move up") }
                    TextButton(enabled = index < draft.extraExams.lastIndex, onClick = { val entries = draft.extraExams.toMutableList(); java.util.Collections.swap(entries, index, index + 1); change(draft.copy(extraExams = entries)) }) { Text("Move down") }
                }
            }
            if (draft.exams().size > 1) TextButton(onClick = { removing = true }) { Text("Remove exam", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (removing) AlertDialog(onDismissRequest = { removing = false }, title = { Text("Remove ${exam.name.ifBlank { "this exam" }}?") },
        text = { Text("It will be removed from this widget's draft. Other widgets are unchanged. Apply to save the change.") },
        confirmButton = { TextButton(onClick = { change(draft.removeExam(exam.id)); selectedId = "primary"; removing = false; editing = false }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text("Cancel") } })
    replacing?.let { saved -> AlertDialog(onDismissRequest = { replacing = null }, title = { Text("Load saved layout?") },
        text = { Text("This replaces the current draft with the saved exams and appearance. Apply to update this widget.") },
        confirmButton = { TextButton(onClick = { change(saved); selectedId = "primary"; replacing = null }) { Text("Load") } },
        dismissButton = { TextButton(onClick = { replacing = null }) { Text("Cancel") } }) }
}
