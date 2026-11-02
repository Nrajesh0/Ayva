package com.focusbyrj.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.focusbyrj.app.ui.components.*
import com.focusbyrj.app.widget.*

private enum class WidgetKind(val title: String, val provider: Class<*>, val configure: Class<*>?) {
    TASKS("Tasks widget", TodoWidgetProvider::class.java, TodoWidgetConfigureActivity::class.java),
    NOTES("Notes widget", NoteWidgetProvider::class.java, NoteWidgetConfigureActivity::class.java),
    EXAM("Exam countdown", ExamCountdownProvider::class.java, ExamCountdownConfigureActivity::class.java),
    BATTERY("Battery health", BatteryHealthWidgetProvider::class.java, null)
}

@Composable
fun WidgetsSettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    var choosing by remember { mutableStateOf<WidgetKind?>(null) }
    val target = SettingsSearchCatalog.id(LocalSettingsSearchTarget.current)
    LaunchedEffect(target) {
        choosing = when (target) { "widgets.tasks" -> WidgetKind.TASKS; "widgets.notes" -> WidgetKind.NOTES; "widgets.exam" -> WidgetKind.EXAM; "widgets.battery" -> WidgetKind.BATTERY; else -> null }
    }
    SettingsMenuPage("Widgets", nav) {
        SettingsMenuSectionHeader("HOME SCREEN")
        WidgetKind.entries.forEach { kind ->
            val icon = when (kind) { WidgetKind.TASKS -> Icons.Default.Checklist; WidgetKind.NOTES -> Icons.Default.StickyNote2; WidgetKind.EXAM -> Icons.Default.Event; WidgetKind.BATTERY -> Icons.Default.BatteryFull }
            SettingsMenuRow(icon, kind.title, modifier = androidx.compose.ui.Modifier.settingsSearchTarget(
                when(kind) { WidgetKind.TASKS -> "widgets.tasks"; WidgetKind.NOTES -> "widgets.notes"; WidgetKind.EXAM -> "widgets.exam"; WidgetKind.BATTERY -> "widgets.battery" }), onClick = { choosing = kind }) {
                Text("Manage", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    choosing?.let { kind ->
        val instances = remember(kind) { manager.getAppWidgetIds(ComponentName(context, kind.provider)).toList() }
        fun configure(id: Int?) {
            val activity = kind.configure ?: return
            choosing = null
            context.startActivity(Intent(context, activity).apply {
                if (id != null) putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            })
        }
        AlertDialog(onDismissRequest = { choosing = null }, title = { Text(kind.title) }, text = {
            androidx.compose.foundation.layout.Column {
                if (kind == WidgetKind.BATTERY) {
                    Text("Shows maximum capacity when Android exposes it. Otherwise it shows Unavailable, with actual charge and battery condition. Good condition does not mean 100% capacity.\n\nRefreshes about every 30 minutes, when Android allows, or when tapped. No continuous monitoring.", style = MaterialTheme.typography.bodySmall)
                    if (instances.isNotEmpty()) TextButton(onClick = { BatteryHealthWidgetProvider.refreshAll(context) }) { Text("Refresh widgets") }
                } else {
                    Text(if (instances.isEmpty()) "No widgets added yet. Choose a default style or add one to your home screen." else "Choose the widget to edit. Each widget keeps its own settings.", style = MaterialTheme.typography.bodySmall)
                    instances.forEachIndexed { index, id -> TextButton(onClick = { configure(id) }) { Text("Edit widget ${index + 1}") } }
                    if (kind != WidgetKind.EXAM) TextButton(onClick = { configure(0) }) { Text("Default style") }
                    else TextButton(onClick = { configure(null) }) { Text("Create countdown") }
                }
            }
        }, confirmButton = {
            if (kind != WidgetKind.EXAM) TextButton(onClick = {
                choosing = null
                if (Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported) {
                    val accepted = runCatching { manager.requestPinAppWidget(ComponentName(context, kind.provider), null, null) }.getOrDefault(false)
                    if (!accepted) Toast.makeText(context, "Add this widget from your home screen's Widgets menu.", Toast.LENGTH_LONG).show()
                } else Toast.makeText(context, "Long-press your home screen and choose Widgets.", Toast.LENGTH_LONG).show()
            }) { Text("Add to home screen") }
        }, dismissButton = { TextButton(onClick = { choosing = null }) { Text("Close") } })
    }
}
