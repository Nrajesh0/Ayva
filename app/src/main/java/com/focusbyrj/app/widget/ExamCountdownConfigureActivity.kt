package com.focusbyrj.app.widget

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import com.focusbyrj.app.ui.security.installScreenCaptureProtection
import java.time.LocalDate
import java.time.LocalTime

class ExamCountdownConfigureActivity : ComponentActivity() {
    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installScreenCaptureProtection()
        setResult(RESULT_CANCELED)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager = AppWidgetManager.getInstance(this)
        // Reject IDs belonging to another provider; a launcher may enter without an ID for preview.
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && manager.getAppWidgetInfo(widgetId)?.provider?.className != ExamCountdownProvider::class.java.name) { finish(); return }
        val store = ExamCountdownStore(this)
        setContent { FocusByRjTheme {
            var raw by rememberSaveable { mutableStateOf((store.get(widgetId) ?: ExamCountdown()).json()) }
            var value by remember { mutableStateOf(ExamCountdown.read(raw)) }
            var error by remember { mutableStateOf<String?>(null) }
            var advanced by rememberSaveable { mutableStateOf(false) }
            var zone by rememberSaveable { mutableStateOf(value.zone) }
            var bg by rememberSaveable { mutableStateOf(value.background) }
            var fg by rememberSaveable { mutableStateOf(value.foreground) }
            var accent by rememberSaveable { mutableStateOf(value.accent) }
            fun change(next: ExamCountdown) { value = next; runCatching { raw = next.json() }; error = null }
            fun pickDate(start: Boolean) {
                val d = LocalDate.parse(if (start) value.startDate else value.date)
                DatePickerDialog(this, { _, year, month, day ->
                    val selected = LocalDate.of(year, month + 1, day).toString()
                    change(if (start) value.copy(startDate = selected) else value.copy(date = selected))
                }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Exam countdown", style = MaterialTheme.typography.headlineSmall)
                    Text("Preview · tap the finished widget to edit it", style = MaterialTheme.typography.bodySmall)
                    val preview = value.copy(background = bg.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: value.background,
                        foreground = fg.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: value.foreground,
                        accent = accent.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: value.accent)
                    val colors = ExamCountdownProvider.colors(this@ExamCountdownConfigureActivity, preview)
                    val textAlign = when(value.align) { "CENTER" -> androidx.compose.ui.text.style.TextAlign.Center; "END" -> androidx.compose.ui.text.style.TextAlign.End; else -> androidx.compose.ui.text.style.TextAlign.Start }
                    Column(Modifier.fillMaxWidth().background(Color(colors[0]).copy(alpha = value.opacity / 100f), RoundedCornerShape(value.radius.dp)).padding(20.dp)) {
                        Text(value.name, color = Color(colors[1]), modifier = Modifier.fillMaxWidth(), textAlign = textAlign)
                        Text(runCatching { value.headline() }.getOrDefault("Check the date"), fontSize = value.textSize.sp, color = Color(colors[2]), modifier = Modifier.fillMaxWidth(), textAlign = textAlign)
                        if (value.showDate) Text("${value.date} · ${value.time} · ${value.zone}", color = Color(colors[1]), style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), textAlign = textAlign)
                        if (value.showProgress) LinearProgressIndicator(progress = { runCatching { value.progress() / 100f }.getOrDefault(0f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Color(colors[2]))
                        Text(value.subtitle, color = Color(colors[1]), modifier = Modifier.fillMaxWidth(), textAlign = textAlign)
                    }
                    OutlinedTextField(value.name, { change(value.copy(name = it.take(100))) }, label = { Text("Exam name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value.subtitle, { change(value.copy(subtitle = it.take(200))) }, label = { Text("Personal message") }, modifier = Modifier.fillMaxWidth())
                    Row { TextButton(onClick = { pickDate(false) }) { Text("Date: ${value.date}") }
                        TextButton(onClick = { val t = LocalTime.parse(value.time); TimePickerDialog(this@ExamCountdownConfigureActivity, { _, h, m -> change(value.copy(time = LocalTime.of(h, m).toString())) }, t.hour, t.minute, android.text.format.DateFormat.is24HourFormat(this@ExamCountdownConfigureActivity)).show() }) { Text(value.time) } }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("DAYS" to "Days", "WEEKS" to "Weeks + days", "HOURS" to "Days + hours").forEach { (id, label) -> FilterChip(value.format == id, { change(value.copy(format = id)) }, label = { Text(label) }) } }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("THEME", "DARK", "LIGHT", "CUSTOM").forEach { id -> FilterChip(value.palette == id, { change(value.copy(palette = id)) }, label = { Text(id.lowercase().replaceFirstChar(Char::uppercase)) }) } }
                    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide customization" else "More customization") }
                    if (advanced) {
                        OutlinedTextField(zone, { zone = it }, label = { Text("Time zone (e.g. Asia/Kolkata)") }, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { pickDate(true) }) { Text("Progress starts: ${value.startDate}") }
                        if (value.palette == "CUSTOM") {
                            OutlinedTextField(bg, { bg = it }, label = { Text("Background · #RRGGBB") })
                            OutlinedTextField(fg, { fg = it }, label = { Text("Text · #RRGGBB") })
                            OutlinedTextField(accent, { accent = it }, label = { Text("Accent · #RRGGBB") })
                        }
                        Text("Background opacity: ${value.opacity}%")
                        Slider(value.opacity.toFloat(), { change(value.copy(opacity = it.toInt())) }, valueRange = 20f..100f)
                        Text("Rounded corners: ${value.radius}")
                        Slider(value.radius.toFloat(), { change(value.copy(radius = it.toInt())) }, valueRange = 0f..40f)
                        Text("Countdown text: ${value.textSize}sp")
                        Slider(value.textSize.toFloat(), { change(value.copy(textSize = it.toInt())) }, valueRange = 24f..64f)
                        FlowRow { listOf("START", "CENTER", "END").forEach { id -> FilterChip(value.align == id, { change(value.copy(align = id)) }, label = { Text(id.lowercase()) }) } }
                        Row { Checkbox(value.showDate, { change(value.copy(showDate = it)) }); Text("Show exam date and time") }
                        Row { Checkbox(value.showProgress, { change(value.copy(showProgress = it)) }); Text("Show preparation progress") }
                        Row { Checkbox(value.countUp, { change(value.copy(countUp = it)) }); Text("Count elapsed days after the exam") }
                        if (!value.countUp) OutlinedTextField(value.endedText, { change(value.copy(endedText = it.take(100))) }, label = { Text("Message when time is up") })
                        Text("Small widget sizes hide secondary details. Home-screen updates may be delayed while the phone sleeps.", style = MaterialTheme.typography.bodySmall)
                        if (store.all().isNotEmpty()) {
                            Text("Reuse a saved countdown", style = MaterialTheme.typography.titleSmall)
                            store.all().forEach { (_, saved) -> TextButton(onClick = { change(saved); zone = saved.zone; bg = saved.background; fg = saved.foreground; accent = saved.accent }) { Text("${saved.name} · ${saved.date}") } }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        runCatching {
                            val final = value.copy(zone = zone.trim(), background = bg.trim(), foreground = fg.trim(), accent = accent.trim()); final.validate()
                            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                store.save(widgetId, final); ExamCountdownProvider.updateAll(this@ExamCountdownConfigureActivity)
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish()
                            } else if (manager.isRequestPinAppWidgetSupported) {
                                val callback = Intent(this@ExamCountdownConfigureActivity, ExamCountdownProvider::class.java)
                                    .setAction(ExamCountdownProvider.PINNED).setData(android.net.Uri.parse("ayva://exam-pin/${java.util.UUID.randomUUID()}"))
                                    .putExtra("configuration", final.json())
                                val pending = android.app.PendingIntent.getBroadcast(this@ExamCountdownConfigureActivity, 0, callback, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE)
                                check(manager.requestPinAppWidget(android.content.ComponentName(this@ExamCountdownConfigureActivity, ExamCountdownProvider::class.java), null, pending)) { "Your launcher couldn't add the widget" }
                            } else error = "Long-press your home screen, choose Widgets, then Ayva Exam countdown."
                        }.onFailure { error = it.message ?: "Check your settings" }
                    }) { Text(if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) "Add to home screen" else "Save widget") }
                    TextButton(onClick = { finish() }) { Text("Cancel") }
                }
            }
        } }
    }
}
