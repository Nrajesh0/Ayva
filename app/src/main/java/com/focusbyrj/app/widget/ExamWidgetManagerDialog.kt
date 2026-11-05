package com.focusbyrj.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Launcher instances and portable saved layouts remain independent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamWidgetManagerDialog(onDismiss: () -> Unit, onEdit: (Int) -> Unit, onCreate: (ExamCountdown?) -> Unit) {
    val context = LocalContext.current
    val store = remember { ExamCountdownStore(context) }
    val instances = remember { AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ExamCountdownProvider::class.java)).toList() }
    var layouts by remember { mutableStateOf(store.all().filterNot { store.inUse(it.first) }) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<Pair<String, ExamCountdown>?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).padding(horizontal = 20.dp)) {
            Text("Exam widgets", style = MaterialTheme.typography.titleLarge)
            Text("Your schedule, at a glance.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("On home screen" to instances.size, "Saved layouts" to layouts.size).forEachIndexed { index, (title, count) ->
                    FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text("$title · $count") })
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tab == 0) {
                    if (instances.isEmpty()) ManagerEmpty("Make room for what’s next", "Create a widget with one exam or your whole schedule.")
                    instances.forEach { id ->
                        val value = store.get(id)
                        ManagerEntry(value?.widgetName?.ifBlank { value.name } ?: "Set up this widget",
                            "${value?.exams()?.size ?: 0} exams · ${value?.let { ExamWidgetPresentation.label(it.design) } ?: "Not configured"}",
                            onClick = { onEdit(id) }, menu = { close ->
                                DropdownMenuItem(text = { Text("Edit widget") }, onClick = { close(); onEdit(id) })
                                if (value != null) DropdownMenuItem(text = { Text("Duplicate") }, onClick = { close(); onCreate(value) })
                            })
                    }
                } else {
                    if (layouts.isEmpty()) ManagerEmpty("Keep a layout you love", "Save a layout in the editor to reuse its exams and design.")
                    layouts.forEach { entry ->
                        val (id, value) = entry
                        val inUse = store.inUse(id)
                        ManagerEntry(value.widgetName.ifBlank { value.name },
                            "${value.exams().size} exams · ${ExamWidgetPresentation.label(value.design)}${if (inUse) " · In use" else ""}",
                            onClick = { onCreate(value) }, menu = { close ->
                                DropdownMenuItem(text = { Text("Use layout") }, onClick = { close(); onCreate(value) })
                                DropdownMenuItem(text = { Text("Delete layout") }, enabled = !inUse, onClick = { close(); deleting = entry })
                            })
                    }
                }
            }
            if (tab == 0 && instances.isNotEmpty()) Text("To remove a home widget, long-press it in your launcher.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
            Button(onClick = { onCreate(null) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(50.dp)) { Text("Create widget") }
        }
    }
    deleting?.let { (id, value) -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete saved layout?") },
        text = { Text("Remove '${value.widgetName.ifBlank { value.name }}' from saved layouts? Home-screen widgets and other layouts are kept.") },
        confirmButton = { TextButton(onClick = {
            if (!store.deleteTemplate(id)) Toast.makeText(context, "This layout is now in use and was kept.", Toast.LENGTH_LONG).show()
            layouts = store.all().filterNot { store.inUse(it.first) }; deleting = null
        }) { Text("Delete layout") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
}

@Composable
private fun ManagerEmpty(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ManagerEntry(title: String, detail: String, onClick: () -> Unit,
                         menu: @Composable ColumnScope.(() -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f))) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, "Options for $title") }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) { menu { expanded = false } }
            }
        }
    }
}
