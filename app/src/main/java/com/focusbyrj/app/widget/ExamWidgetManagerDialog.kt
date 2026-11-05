package com.focusbyrj.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Launcher instances and portable saved layouts are distinct; deleting a layout never removes a home widget. */
@Composable
fun ExamWidgetManagerDialog(onDismiss: () -> Unit, onEdit: (Int) -> Unit, onCreate: (ExamCountdown?) -> Unit) {
    val context = LocalContext.current
    val store = remember { ExamCountdownStore(context) }
    val instances = remember { AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ExamCountdownProvider::class.java)).toList() }
    var layouts by remember { mutableStateOf(store.all()) }
    var tab by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<Pair<String, ExamCountdown>?>(null) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(16.dp)) {
                Text("Exam widgets", style = MaterialTheme.typography.titleLarge)
                TabRow(tab, modifier = Modifier.padding(top = 12.dp)) {
                    listOf("Home screen", "Saved layouts").forEachIndexed { i, title -> Tab(tab == i, onClick = { tab = i }, text = { Text(title) }) }
                }
                if (tab == 0) {
                    if (instances.isEmpty()) Text("No exam widgets added yet. Create one below, or add it from your launcher's Widgets menu.", Modifier.padding(vertical = 20.dp), style = MaterialTheme.typography.bodyMedium)
                    else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                        itemsIndexed(instances, key = { _, id -> id }) { index, id ->
                            val config = store.get(id)
                            Column(Modifier.fillMaxWidth().clickable { onEdit(id) }.padding(vertical = 12.dp)) {
                                Text(config?.widgetName?.ifBlank { config.name } ?: "Unconfigured countdown", style = MaterialTheme.typography.titleSmall)
                                Text("Widget ${index + 1} · ${config?.exams()?.size ?: 0} exams · ${config?.date.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row {
                                    TextButton(onClick = { onEdit(id) }) { Text("Edit") }
                                    if (config != null) TextButton(onClick = { onCreate(config) }) { Text("Duplicate") }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                        }
                    }
                    Text("Each widget has independent exams and appearance. To remove a home-screen widget, long-press it in your launcher.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                } else {
                    if (layouts.isEmpty()) Text("Save a reusable layout from the editor's Style tab. Existing countdowns also appear here.", Modifier.padding(vertical = 20.dp), style = MaterialTheme.typography.bodyMedium)
                    else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                        itemsIndexed(layouts, key = { _, entry -> entry.first }) { _, entry ->
                            val (id, value) = entry
                            val inUse = store.inUse(id)
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Text(value.widgetName.ifBlank { value.name }, style = MaterialTheme.typography.titleSmall)
                                Text("${value.exams().size} exams · ${ExamWidgetPresentation.label(value.design)}${if (inUse) " · In use" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row {
                                    TextButton(onClick = { onCreate(value) }) { Text("Use layout") }
                                    TextButton(enabled = !inUse, onClick = { deleting = entry }) { Text("Delete") }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    TextButton(onClick = { onCreate(null) }) { Text("Create countdown") }
                }
            }
        }
    }
    deleting?.let { (id, value) -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete saved layout?") },
        text = { Text("Remove '${value.widgetName.ifBlank { value.name }}' from saved layouts? Home-screen widgets and other layouts are kept.") },
        confirmButton = { TextButton(onClick = {
            if (!store.deleteTemplate(id)) Toast.makeText(context, "This layout is now in use and was kept.", Toast.LENGTH_LONG).show()
            layouts = store.all(); deleting = null
        }) { Text("Delete layout") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
}
