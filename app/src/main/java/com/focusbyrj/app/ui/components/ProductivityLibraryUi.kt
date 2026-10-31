package com.focusbyrj.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focusbyrj.app.data.*
import org.json.JSONObject
import org.json.JSONArray

@Composable fun TemplatePicker(onDismiss: () -> Unit, onPick: (Task) -> Unit) {
    val context = LocalContext.current
    val library = remember { ProductivityLibrary(context) }
    var mine by remember { mutableStateOf(library.entries("template")) }
    var error by remember { mutableStateOf<String?>(null) }
    var remove by remember { mutableStateOf<String?>(null) }
    FeatureSheet("Templates", "Choose a task template, then review its settings before saving.", onDismiss) {
        FeatureGroup("Your templates") {
            if (mine.isEmpty()) Text("No templates yet. Use + → Create template to make your first.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            mine.forEach { (id, j) -> FeatureRow(j.getString("name"), "${j.getJSONArray("steps").length()} steps", Icons.Default.ContentCopy,
                onClick = { runCatching { ProductivityLibrary.instantiate(j) }.onSuccess(onPick).onFailure { error = it.message } }, trailing = { IconButton(onClick = { remove = id }) { Icon(Icons.Default.Delete, "Delete ${j.getString("name")}") } }) }
        }
        FeatureGroup("Ready-made") {
            ProductivityLibrary.builtIns().forEach { j -> FeatureRow(j.getString("name"), "${j.getJSONArray("steps").length()} steps · customize before saving", Icons.Default.Checklist, onClick = { runCatching { ProductivityLibrary.instantiate(j) }.onSuccess(onPick).onFailure { error = it.message } }) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    remove?.let { id -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Delete template?") }, text = { Text("Tasks already created from it are kept.") }, confirmButton = {
        TextButton(onClick = { runCatching { library.delete(id); mine = library.entries("template") }.onFailure { error = it.message }; remove = null }) { Text("Delete") }
    }, dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } }) }
}

@Composable fun HabitChains(habits: List<HabitWithProgress>, onIncrement: (Habit, () -> Unit) -> Unit) {
    val context = LocalContext.current
    val library = remember { ProductivityLibrary(context) }
    var chains by remember { mutableStateOf(library.entries("chain")) }
    var editor by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var ids by remember { mutableStateOf(emptyList<Long>()) }
    var active by remember { mutableStateOf<List<Long>?>(null) }
    var position by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    FeatureGroup("Habit routines") {
        FeatureRow("Create routine", "Link habits into a sequence you can follow", Icons.Default.PlaylistAdd, onClick = { editingId = null; name = ""; ids = emptyList(); editor = true })
        chains.forEach { (id, j) -> FeatureRow(j.getString("name"), "${j.getJSONArray("habits").length()} habits · tap to start", Icons.Default.PlayArrow, onClick = {
                val a = j.getJSONArray("habits"); active = (0 until a.length()).map { a.getLong(it) }; position = 0
            }, trailing = { IconButton(onClick = { editingId = id; name = j.getString("name"); val a = j.getJSONArray("habits"); ids = (0 until a.length()).map { a.getLong(it) }; editor = true }) { Icon(Icons.Default.Edit, "Edit ${j.getString("name")}") } }) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (editor) AlertDialog(onDismissRequest = { editor = false }, title = { Text("Habit routine") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(name, { name = it.take(500) }, label = { Text("Name") })
            Text("Select steps, then reorder. Habits keep their own targets and history.", style = MaterialTheme.typography.bodySmall)
            ids.forEachIndexed { index, id -> Row {
                Text("${index + 1}. ${habits.find { it.habit.id == id }?.habit?.title ?: "Unavailable habit"}", Modifier.weight(1f))
                if (index > 0) TextButton(onClick = { ids = ids.toMutableList().apply { add(index - 1, removeAt(index)) } }) { Text("Up") }
                TextButton(onClick = { ids = ids - id }) { Text("Remove") }
            } }
            habits.filter { it.habit.id !in ids }.forEach { item -> TextButton(onClick = { ids = ids + item.habit.id }) { Text("Add ${item.habit.title}") } }
            if (editingId != null) TextButton(onClick = {
                runCatching { library.delete(editingId!!); chains = library.entries("chain"); editor = false }.onFailure { error = it.message }
            }) { Text("Delete routine (keep habits)") }
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && ids.isNotEmpty(), onClick = {
        runCatching {
            val j = JSONObject().put("kind", "chain").put("name", name.trim()).put("habits", JSONArray(ids))
            if (editingId == null) library.put("chain", j) else library.put("chain", j, editingId!!)
            chains = library.entries("chain"); editor = false
        }.onFailure { error = it.message }
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = { editor = false }) { Text("Cancel") } })
    active?.let { steps ->
        val item = steps.getOrNull(position)?.let { id -> habits.find { it.habit.id == id } }
        AlertDialog(onDismissRequest = { if (!busy) active = null }, title = { Text(if (position >= steps.size) "Routine finished" else "Step ${position + 1} of ${steps.size}") }, text = {
            Column {
                Text(item?.habit?.title ?: if (position >= steps.size) "Your progress is saved. Skipped steps stay unchanged." else "This habit is unavailable. You can skip it.")
                item?.let { Text("${it.completedToday}/${it.targetToday} today" + if (it.habit.isPaused) " · Paused" else "") }
            }
        }, confirmButton = {
            if (position >= steps.size) TextButton(onClick = { active = null }) { Text("Done") }
            else if (item?.isCompletedToday == true) TextButton(onClick = { position++ }) { Text("Next") }
            else TextButton(enabled = item != null && !item.habit.isPaused && !busy, onClick = { item?.let { busy = true; onIncrement(it.habit) { busy = false } } }) { Text("Log +1") }
        }, dismissButton = { Row {
            if (position < steps.size) TextButton(enabled = !busy, onClick = { position++ }) { Text("Skip") }
            TextButton(enabled = !busy, onClick = { active = null }) { Text("Stop") }
        } })
    }
}
