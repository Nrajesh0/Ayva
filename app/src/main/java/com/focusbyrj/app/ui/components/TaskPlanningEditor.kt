package com.focusbyrj.app.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focusbyrj.app.data.*
import com.focusbyrj.app.util.SmartDateParser
import java.util.Calendar

/** Optional native controls for the same task metadata used by chat. Save remains owned by the editor. */
@Composable
fun TaskPlanningEditor(taskId: Long, value: TaskPlanning, tasks: List<Task>, onChange: (TaskPlanning) -> Unit, onOpenTask: ((Task) -> Unit)? = null, onValidity: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    var chooser by remember { mutableStateOf<String?>(null) }
    val errors = remember { mutableStateMapOf<String, String>() }
    SideEffect { onValidity(errors.isEmpty()) }
    fun change(next: TaskPlanning, field: String = "model") {
        try { next.validate(); errors.remove(field); onChange(next) }
        catch (e: IllegalArgumentException) { errors[field] = e.message ?: "Check the work times" }
    }
    fun pick(session: WorkSession?) {
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone(value.timezone)).apply { timeInMillis = session?.start ?: System.currentTimeMillis() }
        DatePickerDialog(context, { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                val at = Calendar.getInstance(java.util.TimeZone.getTimeZone(value.timezone)).apply {
                    clear(); set(year, month, day, hour, minute); isLenient = false
                }
                val start = com.focusbyrj.app.util.assistant.PlanClock.boundary(at)
                if (start == null || start <= System.currentTimeMillis()) errors["picker"] = "Choose a valid future work time"
                else {
                    val replacement = (session ?: WorkSession(start = start, minutes = minOf(value.minutes ?: 30, 1440))).copy(start = start)
                    val sessions = value.workSessions.filterNot { it.id == session?.id } + replacement
                    change(value.withSessions(sessions).copy(minutes = value.minutes ?: replacement.minutes), "picker")
                }
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Planning · ${value.timezone}", style = MaterialTheme.typography.labelLarge)
        val parent = tasks.firstOrNull { it.id == value.parentTaskId }
        Row {
            TextButton(onClick = { chooser = "parent" }) { Text(parent?.let { "Parent: ${it.title}" } ?: if (value.parentTaskId != null || value.parentMissing) "Parent unavailable" else "Choose parent") }
            if (parent != null && onOpenTask != null) TextButton(onClick = { onOpenTask(parent) }) { Text("Open") }
        }
        TextButton(onClick = { chooser = "prerequisites" }) { Text("Prerequisites (${value.prerequisiteTaskIds.size + value.missingPrerequisites})") }
        var estimate by remember(value.minutes) { mutableStateOf(value.minutes?.toString().orEmpty()) }
        OutlinedTextField(estimate, { input ->
            estimate = input
            val minutes = input.toIntOrNull()?.takeIf { it in 1..1440 }
            if (minutes == null) errors["estimate"] = "Use an estimate from 1 to 1440 minutes" else change(value.copy(minutes = minutes), "estimate")
        }, label = { Text("Estimated minutes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        value.workSessions.forEachIndexed { index, session ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    TextButton(onClick = { pick(session) }) { Text("${index + 1}. ${com.focusbyrj.app.util.assistant.PlanClock.format(session.start, value.timezone)}") }
                    var duration by remember(session) { mutableStateOf(session.minutes.toString()) }
                    OutlinedTextField(duration, { input ->
                        duration = input
                        val minutes = input.toIntOrNull()?.takeIf { it in 1..1440 }
                        if (minutes == null) errors[session.id] = "Use a session length from 1 to 1440 minutes"
                        else change(value.withSessions(value.workSessions.map { if (it.id == session.id) it.copy(minutes = minutes) else it }), session.id)
                    }, label = { Text("Session minutes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = { errors.remove(session.id); change(value.withSessions(value.workSessions.filterNot { it.id == session.id })) }) { Text("Remove") }
            }
        }
        if (value.workSessions.size < 100) TextButton(onClick = { pick(null) }) { Text("Add work session") }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Remind when work starts", modifier = Modifier.weight(1f))
            Switch(checked = value.workReminders, onCheckedChange = { change(value.copy(workReminders = it)) })
        }
        errors.values.firstOrNull()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    chooser?.let { kind ->
        var selected by remember(kind) { mutableStateOf(value.prerequisiteTaskIds) }
        val choices = tasks.filter { it.id != taskId && !it.isTrashed && it.deletedAt == null }
        AlertDialog(onDismissRequest = { chooser = null }, title = { Text(if (kind == "parent") "Parent task" else "Prerequisites") },
            text = { Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                if (kind == "parent") TextButton(onClick = { change(value.copy(parentTaskId = null, parentMissing = false)); chooser = null }) { Text("No parent") }
                choices.forEach { task ->
                    if (kind == "parent") TextButton(onClick = { change(value.copy(parentTaskId = task.id, parentMissing = false)); chooser = null }) { Text(task.title) }
                    else Row { Checkbox(checked = task.id in selected, onCheckedChange = { selected = if (it) selected + task.id else selected - task.id }); Text(task.title) }
                }
            } }, confirmButton = { TextButton(onClick = {
                if (kind == "prerequisites") change(value.copy(prerequisiteTaskIds = selected, missingPrerequisites = 0))
                chooser = null
            }) { Text("Done") } })
    }
}
