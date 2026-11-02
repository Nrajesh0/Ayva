package com.focusbyrj.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.focusbyrj.app.ui.components.*
import com.focusbyrj.app.widget.*

private enum class WidgetKind(val title: String, val id: String, val provider: Class<*>, val configure: Class<*>) {
    TASKS("Tasks widget", "widgets.tasks", TodoWidgetProvider::class.java, TodoWidgetConfigureActivity::class.java),
    NOTES("Notes widget", "widgets.notes", NoteWidgetProvider::class.java, NoteWidgetConfigureActivity::class.java),
    EXAM("Exam countdown", "widgets.exam", ExamCountdownProvider::class.java, ExamCountdownConfigureActivity::class.java)
}

@Composable
fun WidgetsSettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    var choosing by remember { mutableStateOf<WidgetKind?>(null) }
    val target = SettingsSearchCatalog.id(LocalSettingsSearchTarget.current)
    LaunchedEffect(target) { choosing = WidgetKind.entries.firstOrNull { it.id == target } }
    SettingsMenuPage("Widgets", nav) {
        SettingsMenuSectionHeader("HOME SCREEN")
        WidgetKind.entries.forEach { kind ->
            val icon = when (kind) {
                WidgetKind.TASKS -> Icons.Default.Checklist
                WidgetKind.NOTES -> Icons.Default.StickyNote2
                WidgetKind.EXAM -> Icons.Default.Event
            }
            SettingsMenuRow(icon, kind.title, modifier = Modifier.settingsSearchTarget(kind.id), onClick = { choosing = kind }) {
                Text("Manage", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    choosing?.let { kind ->
        val instances = remember(kind) { manager.getAppWidgetIds(ComponentName(context, kind.provider)).toList() }
        fun configure(id: Int?) {
            choosing = null
            context.startActivity(Intent(context, kind.configure).apply {
                if (id != null) putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            })
        }
        AlertDialog(onDismissRequest = { choosing = null }, title = { Text(kind.title) }, text = {
            Column {
                if (instances.isEmpty()) Text("No widgets added yet.", style = MaterialTheme.typography.bodySmall)
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    itemsIndexed(instances, key = { _, id -> id }) { index, id ->
                        val detail = when (kind) {
                            WidgetKind.TASKS -> WidgetConfigHelper.getConfig(context, id).let { "${it.theme.displayName} · ${it.textSize.spValue.toInt()} sp" }
                            WidgetKind.NOTES -> NoteWidgetConfigHelper.getConfig(context, id).let { "${it.filterMode.displayName} · ${it.textSize.displayName}" }
                            WidgetKind.EXAM -> ExamCountdownStore(context).get(id)?.let { "${it.name} · ${it.date}" }.orEmpty()
                        }
                        Column(Modifier.fillMaxWidth().clickable { configure(id) }.padding(vertical = 10.dp)) {
                            Text("Edit widget ${index + 1}", style = MaterialTheme.typography.bodyMedium)
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (kind != WidgetKind.EXAM) TextButton(onClick = { configure(0) }) { Text("Default style") }
            }
        }, confirmButton = {
            TextButton(onClick = {
                if (kind == WidgetKind.EXAM) configure(null)
                else {
                    choosing = null
                    val accepted = manager.isRequestPinAppWidgetSupported && runCatching {
                        manager.requestPinAppWidget(ComponentName(context, kind.provider), null, null)
                    }.getOrDefault(false)
                    if (!accepted) Toast.makeText(context, "Long-press your home screen and choose Widgets.", Toast.LENGTH_LONG).show()
                }
            }) { Text(if (kind == WidgetKind.EXAM) "Create countdown" else "Add widget") }
        }, dismissButton = { TextButton(onClick = { choosing = null }) { Text("Close") } })
    }
}
