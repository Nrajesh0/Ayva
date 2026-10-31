package com.focusbyrj.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusbyrj.app.data.*

@Composable fun TaskDependencyEditor(id: Long, value: TaskPlanning, tasks: List<Task>, onChange: (TaskPlanning) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val names = value.prerequisiteTaskIds.map { ref -> tasks.find { it.id == ref }?.title ?: "Unavailable task" }
    FeatureRow("Waiting for", if (names.isEmpty() && value.missingPrerequisites == 0) "No prerequisite · task can be completed independently" else names.joinToString().ifBlank { "Unavailable task" }, Icons.Default.Link, onClick = { open = true })
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Waiting for") }, text = {
        Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
            Text("Choose a prerequisite. Advanced planning supports multiple links.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onChange(value.copy(prerequisiteTaskIds = emptySet(), missingPrerequisites = 0)); open = false }) { Text("None") }
            tasks.filter { it.id != id && !it.isTrashed && it.deletedAt == null }.forEach { task ->
                TextButton(onClick = {
                    val next = value.copy(prerequisiteTaskIds = setOf(task.id), missingPrerequisites = 0)
                    runCatching {
                        if (id > 0) TaskPlanning.validateGraph(tasks.map { if (it.id == id) it.copy(planningJson = next.json()) else it })
                        onChange(next); open = false
                    }.onFailure { error = "That link creates a cycle. Choose another task." }
                }) { Text(task.title + if (task.isCompleted) " · Completed" else "") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
}
