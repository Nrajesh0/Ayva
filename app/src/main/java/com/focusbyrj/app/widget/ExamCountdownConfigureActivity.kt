package com.focusbyrj.app.widget

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import com.focusbyrj.app.ui.security.installScreenCaptureProtection
import java.time.LocalDate
import java.time.LocalTime

class ExamCountdownConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installScreenCaptureProtection()
        setResult(RESULT_CANCELED)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager = AppWidgetManager.getInstance(this)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
            manager.getAppWidgetInfo(widgetId)?.provider?.className != ExamCountdownProvider::class.java.name) {
            finish(); return
        }
        val store = ExamCountdownStore(this)
        val initial = store.get(widgetId) ?: ExamCountdown()
        val saved = store.all().map { it.second }
        setContent { FocusByRjTheme {
            ExamCountdownConfigScreen(initial, saved,
                saveLabel = if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) "Add widget" else "Apply",
                onCancel = { finish() }, onSave = { final ->
                    if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        store.save(widgetId, final)
                        ExamCountdownProvider.updateAll(this)
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        finish()
                    } else {
                        check(manager.isRequestPinAppWidgetSupported) {
                            "Long-press your home screen, choose Widgets, then Exam countdown."
                        }
                        val callback = Intent(this, ExamCountdownProvider::class.java)
                            .setAction(ExamCountdownProvider.PINNED)
                            .setData(Uri.parse("ayva://exam-pin/${java.util.UUID.randomUUID()}"))
                            .putExtra("configuration", final.json())
                        val pending = PendingIntent.getBroadcast(this, 0, callback,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                        check(manager.requestPinAppWidget(ComponentName(this, ExamCountdownProvider::class.java), null, pending)) {
                            "Your launcher couldn't add the widget"
                        }
                    }
                })
        } }
    }
}

// Save draft fields without validate()/json(): partially edited names/colors/zones must survive rotation too.
private val CountdownDraftSaver = listSaver<ExamCountdown, Any>(
    save = { listOf(it.name, it.subtitle, it.date, it.time, it.zone, it.startDate, it.format, it.palette,
        it.background, it.foreground, it.accent, it.opacity, it.radius, it.textSize, it.align,
        it.showDate, it.showProgress, it.countUp, it.endedText) },
    restore = { ExamCountdown(it[0] as String, it[1] as String, it[2] as String, it[3] as String,
        it[4] as String, it[5] as String, it[6] as String, it[7] as String, it[8] as String,
        it[9] as String, it[10] as String, it[11] as Int, it[12] as Int, it[13] as Int,
        it[14] as String, it[15] as Boolean, it[16] as Boolean, it[17] as Boolean, it[18] as String) }
)

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
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    fun change(next: ExamCountdown) { draft = next; error = null }
    fun pickDate(start: Boolean) {
        val date = LocalDate.parse(if (start) draft.startDate else draft.date)
        DatePickerDialog(context, { _, year, month, day ->
            val selected = LocalDate.of(year, month + 1, day).toString()
            change(if (start) draft.copy(startDate = selected) else draft.copy(date = selected))
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }
    WidgetEditor("Exam countdown", listOf("Content", "Style", "Layout"), "${draft.name} · ${draft.date}",
        onSave = {
            runCatching {
                val final = draft.copy(zone = draft.zone.trim(), background = draft.background.trim(),
                    foreground = draft.foreground.trim(), accent = draft.accent.trim())
                require(final.name.isNotBlank()) { "Enter an exam name." }
                final.validate()
                onSave(final)
            }.onFailure { error = it.message ?: "Check your settings." }
        }, onCancel = onCancel, saveLabel = saveLabel, error = error,
        preview = { CountdownEditorPreview(draft) }) { section ->
        when (section) {
            0 -> {
                OutlinedTextField(draft.name, { change(draft.copy(name = it.take(100))) },
                    label = { Text("Exam name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.subtitle, { change(draft.copy(subtitle = it.take(200))) },
                    label = { Text("Personal message") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                WidgetEditorRow("Exam date", draft.date, onClick = { pickDate(false) })
                WidgetEditorRow("Exam time", draft.time, onClick = {
                    val time = LocalTime.parse(draft.time)
                    TimePickerDialog(context, { _, hour, minute -> change(draft.copy(time = LocalTime.of(hour, minute).toString())) },
                        time.hour, time.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
                })
                WidgetChoiceRow("Countdown format", draft.format, listOf("DAYS", "WEEKS", "HOURS"),
                    { when (it) { "WEEKS" -> "Weeks + days"; "HOURS" -> "Days + hours"; else -> "Days" } },
                    { change(draft.copy(format = it)) })
                WidgetEditorRow("Progress starts", draft.startDate, onClick = { pickDate(true) })
                OutlinedTextField(draft.zone, { change(draft.copy(zone = it)) }, singleLine = true,
                    label = { Text("Time zone") }, placeholder = { Text("Asia/Kolkata") }, modifier = Modifier.fillMaxWidth())
                if (savedCountdowns.isNotEmpty()) WidgetChoiceRow("Reuse saved countdown", draft, savedCountdowns,
                    { "${it.name} · ${it.date}" }, { change(it) }, selectedLabel = "Choose")
            }
            1 -> {
                WidgetChoiceRow("Theme", draft.palette, listOf("THEME", "DARK", "LIGHT", "CUSTOM"),
                    { if (it == "THEME") "Follow app" else it.lowercase().replaceFirstChar(Char::uppercase) },
                    { change(draft.copy(palette = it)) })
                if (draft.palette == "CUSTOM") {
                    OutlinedTextField(draft.background, { change(draft.copy(background = it)) }, singleLine = true,
                        label = { Text("Background · #RRGGBB") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draft.foreground, { change(draft.copy(foreground = it)) }, singleLine = true,
                        label = { Text("Text · #RRGGBB") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draft.accent, { change(draft.copy(accent = it)) }, singleLine = true,
                        label = { Text("Accent · #RRGGBB") }, modifier = Modifier.fillMaxWidth())
                }
                WidgetSliderRow("Opacity", draft.opacity, "%", 20..100) { change(draft.copy(opacity = it)) }
                WidgetSliderRow("Corner radius", draft.radius, " dp", 0..40) { change(draft.copy(radius = it)) }
            }
            2 -> {
                WidgetSliderRow("Font size", draft.textSize, " sp", 24..64) { change(draft.copy(textSize = it)) }
                WidgetChoiceRow("Alignment", draft.align, listOf("START", "CENTER", "END"),
                    { it.lowercase().replaceFirstChar(Char::uppercase) }, { change(draft.copy(align = it)) })
                WidgetToggleRow("Show date & time", draft.showDate) { change(draft.copy(showDate = it)) }
                WidgetToggleRow("Show preparation progress", draft.showProgress) { change(draft.copy(showProgress = it)) }
                WidgetToggleRow("Count elapsed days after exam", draft.countUp) { change(draft.copy(countUp = it)) }
                if (!draft.countUp) OutlinedTextField(draft.endedText, { change(draft.copy(endedText = it.take(100))) },
                    label = { Text("Message when time is up") }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CountdownEditorPreview(draft: ExamCountdown) {
    val context = LocalContext.current
    val safe = draft.copy(background = draft.background.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: "#101615",
        foreground = draft.foreground.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: "#E8F4EE",
        accent = draft.accent.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: "#8DC6B1")
    val colors = ExamCountdownProvider.colors(context, safe)
    val align = when (draft.align) { "CENTER" -> TextAlign.Center; "END" -> TextAlign.End; else -> TextAlign.Start }
    Column(Modifier.fillMaxWidth().background(Color(colors[0]).copy(alpha = draft.opacity / 100f),
        RoundedCornerShape(draft.radius.dp)).padding(16.dp)) {
        Text(draft.name, color = Color(colors[1]), modifier = Modifier.fillMaxWidth(), textAlign = align)
        Text(runCatching { draft.headline() }.getOrDefault("Check the date"), fontSize = draft.textSize.sp,
            color = Color(colors[2]), modifier = Modifier.fillMaxWidth(), textAlign = align)
        if (draft.showDate) Text("${draft.date} · ${draft.time} · ${draft.zone}", color = Color(colors[1]),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), textAlign = align)
        if (draft.showProgress) LinearProgressIndicator(progress = { runCatching { draft.progress() / 100f }.getOrDefault(0f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Color(colors[2]))
        Text(draft.subtitle, color = Color(colors[1]), modifier = Modifier.fillMaxWidth(), textAlign = align)
    }
}
