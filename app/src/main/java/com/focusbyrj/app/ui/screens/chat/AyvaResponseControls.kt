package com.focusbyrj.app.ui.screens.chat

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.TalkAction
import com.focusbyrj.app.util.assistant.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Open numbered rows. Selecting changes conversational focus, never the saved task. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AyvaSelectableTaskList(
    list: AssistantResponseContent.TaskList, liveTasks: List<Task>?, reference: AssistantItemReference?,
    font: Float, onSelect: ((AssistantItemReference?) -> Unit)?, onQuery: ((String) -> Unit)?
) {
    var selectedId by remember(list) { mutableStateOf<Long?>(null) }
    var now by remember(list) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(list) { while (true) { kotlinx.coroutines.delay(5_000); now = System.currentTimeMillis() } }
    val fresh = now - list.at in 0..900_000L
    val tasks = liveTasks?.associateBy { it.id }
    val selected = selectedId?.let { tasks?.get(it) }?.takeIf { !it.isTrashed && it.deletedAt == null && reference?.kind == "task" && reference.id == it.id }
    LaunchedEffect(reference) { if (selectedId != null && reference?.id != selectedId) selectedId = null }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${list.total} ${if (list.total == 1) "task" else "tasks"}", color = MaterialTheme.colorScheme.onSurface, fontSize = font.sp, fontWeight = FontWeight.Medium)
        list.rows.forEachIndexed { index, row ->
            val current = tasks?.get(row.id)
            val available = fresh && current != null && !current.isTrashed && current.deletedAt == null
            val active = available && selected?.id == row.id
            val title = current?.title ?: row.title
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surface.copy(alpha = 0f), RoundedCornerShape(10.dp))
                .selectable(selected = active, enabled = available && onSelect != null, role = Role.RadioButton, onClick = {
                    selectedId = if (active) null else row.id
                    onSelect?.invoke(if (active) null else AssistantItemReference("task", row.id, title, AssistantPresentation.due(current?.dueDate)))
                }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(32.dp).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f), RoundedCornerShape(2.dp)))
                Text("${index + 1}", Modifier.width(32.dp).padding(start = 8.dp), color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = (font * .85f).sp)
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = font.sp)
                    Text(if (!available) "No longer available · request a fresh list" else AssistantPresentation.due(current?.dueDate) + if (current?.isCompleted == true) " · completed" else if (current?.isPriority == true) " · priority" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = (font * .82f).coerceAtLeast(12f).sp)
                }
                if (active) Icon(Icons.Default.Check, "Selected task", Modifier.padding(end = 10.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (list.total > list.rows.size) Text("Showing ${list.rows.size} of ${list.total}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (selected != null && fresh && onQuery != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onQuery("${if (selected.isCompleted) "reopen" else "complete"} id:${selected.id}") }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (selected.isCompleted) "Reopen" else "Mark complete") }
                OutlinedButton(onClick = { onQuery("reschedule id:${selected.id}") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Change time") }
            }
        } else Text(if (fresh) "Select a task to act" else "This list has expired. Ask for a fresh list.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

/** Current value is live; choosing any replacement only sends a validated preview request. */
@Composable
internal fun AyvaInlineSetting(spec: AssistantPreferenceSpec, onQuery: ((String) -> Unit)?) {
    val context = LocalContext.current
    val prefs = remember(context, spec.owner) { context.getSharedPreferences(spec.owner, 0) }
    var value by remember(spec.key) { mutableStateOf(AssistantPreferences.read(context, spec)) }
    DisposableEffect(prefs, spec.key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == spec.key || key == "random_drills_notification_enabled") value = AssistantPreferences.read(context, spec) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var menu by remember(spec.key) { mutableStateOf(false) }
    var custom by remember(spec.key) { mutableStateOf(false) }
    var draft by remember(spec.key) { mutableStateOf("") }
    var error by remember(spec.key) { mutableStateOf<String?>(null) }
    fun preview(raw: String) {
        runCatching { AssistantPreferences.previewQuery(spec, raw) }.fold(onSuccess = { menu = false; custom = false; error = null; onQuery?.invoke(it) }, onFailure = { error = it.message })
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(spec.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { menu = true }, enabled = onQuery != null, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Current: ${AssistantPreferences.display(spec, value)}", Modifier.weight(1f, fill = false))
                Icon(Icons.Default.ExpandMore, "Choose ${spec.title}", Modifier.padding(start = 8.dp).size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                AssistantPreferences.presets(spec).forEach { option ->
                    DropdownMenuItem(text = { Text(AssistantPreferences.display(spec, option)) },
                        trailingIcon = { if (value == option) Icon(Icons.Default.Check, "Current value") },
                        onClick = { if (value == option) menu = false else preview(option) })
                }
                if (spec.type == "int" || spec.unit == "clock") DropdownMenuItem(text = { Text("Custom…") }, onClick = { menu = false; draft = if (spec.unit == "clock") value else value; error = null; custom = true })
            }
        }
        Text("Changes need confirmation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (custom) AlertDialog(onDismissRequest = { custom = false }, title = { Text("Change ${spec.title}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(AssistantAppKnowledge.hint(spec), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = draft, onValueChange = { draft = it; error = null }, label = { Text("Proposed value") }, singleLine = true, isError = error != null)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        val parsed = runCatching { AssistantPreferences.extractValue(spec, draft) }.getOrElse { error = it.message; null }
        if (parsed == null) { if (error == null) error = AssistantAppKnowledge.hint(spec) } else preview(parsed)
    }) { Text("Review change") } }, dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancel") } })
}

/** Legacy mutation chips become canonical previews; no preference write originates in this renderer. */
internal fun responseActions(payload: String): List<TalkAction> = runCatching {
    val array = JSONObject(payload).optJSONArray("actions") ?: return emptyList()
    (0 until minOf(array.length(), 30)).mapNotNull { i -> runCatching {
        val a = array.getJSONObject(i); val label = com.focusbyrj.app.ui.components.cleanLabelAndEmoji(a.optString("label"), "").first
        when (a.optString("type")) {
            "ask_query" -> TalkAction.AskQuery(a.getString("query"), label)
            "navigate" -> TalkAction.NavigateAppScreen(a.getString("route"), label)
            "system_setting" -> TalkAction.OpenSystemSetting(a.getString("action"), label, packageUri = a.optBoolean("packageUri"))
            "pref_update" -> AssistantCapabilities.reviewedChip(TalkAction.DirectPrefUpdate(a.getString("prefKey"), a.getString("prefType"), a.getString("targetValue"), "", label))
            "routine_toggle" -> AssistantCapabilities.reviewedChip(TalkAction.RoutineToggle(a.getInt("scheduleId"), a.getBoolean("isEnabled"), a.optString("routineName"), label))
            else -> null
        }
    }.getOrNull() }.distinctBy { when (it) { is TalkAction.AskQuery -> it.query; else -> it.label } }
}.getOrDefault(emptyList())

@Composable
internal fun AyvaInfoActions(payload: String, onQuery: ((String) -> Unit)?) {
    val actions = remember(payload) { responseActions(payload) }.filter { it !is TalkAction.AskQuery || onQuery != null }
    if (actions.isEmpty()) return
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var more by remember(payload) { mutableStateOf(false) }
    fun run(action: TalkAction) {
        more = false
        if (action is TalkAction.AskQuery) onQuery?.invoke(action.query)
        else scope.launch { if (!action.executeAsync(context)) Toast.makeText(context, "Couldn't open that screen", Toast.LENGTH_SHORT).show() }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SuggestionChip(onClick = { run(actions.first()) }, label = { Text(actions.first().label, style = MaterialTheme.typography.labelMedium) }, modifier = Modifier.weight(1f, fill = false).heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp))
        if (actions.size > 1) Box {
            TextButton(onClick = { more = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("More"); Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp)) }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                actions.drop(1).forEach { action -> DropdownMenuItem(text = { Text(action.label) }, onClick = { run(action) }) }
            }
        }
    }
}
