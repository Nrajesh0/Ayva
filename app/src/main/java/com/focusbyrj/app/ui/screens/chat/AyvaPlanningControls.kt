package com.focusbyrj.app.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusbyrj.app.util.assistant.AssistantGuidedPlanning
import org.json.JSONObject

/** User-opened scoped sheet. Dismissal/selection has no database side effects. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AyvaPlanningControls(payload: String?, onQuery: ((String) -> Unit)?) {
    val plan = remember(payload) { runCatching { JSONObject(payload ?: "{}").optJSONObject("presentation")?.optJSONObject("plan")?.also {
        require(it.getString("id").length in 1..80 && it.getInt("revision") > 0)
        require(it.optJSONArray("items")?.length()?.let { size -> size <= 20 } == true)
    } }.getOrNull() } ?: return
    val id = plan.getString("id"); val revision = plan.getInt("revision"); val question = plan.getString("question")
    var open by rememberSaveable(id, revision) { mutableStateOf(false) }
    var answer by rememberSaveable(id, revision) { mutableStateOf("") }
    val fresh = System.currentTimeMillis() - plan.optLong("at") in 0..900_000L
    OutlinedButton(onClick = { open = true }, enabled = fresh && onQuery != null, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(if (question == "review") "Review or edit plan" else "Answer planning question")
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (question == "review") "Review plan" else "Plan together", style = MaterialTheme.typography.titleLarge)
            if (!plan.isNull("goal")) Text("Goal details: ${plan.getString("goal")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(plan.optString("prompt"), color = MaterialTheme.colorScheme.onSurface)
            val items = plan.getJSONArray("items")
            for (index in 0 until items.length()) Text("${index + 1}. ${items.getJSONObject(index).optString("title")} — ${if (items.getJSONObject(index).isNull("resolvedDue")) "no due date" else com.focusbyrj.app.util.SmartDateParser.formatDueDate(items.getJSONObject(index).getLong("resolvedDue"))}", color = MaterialTheme.colorScheme.onSurface)
            if (question == "review") Text("Edit: rename 2 to … · remove 2 · move 2 before 1 · deadline: Friday at 6pm · deadline 2: tomorrow at 4pm · name: Trip checklist · budget: 60 minutes · effort: 45 minutes", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = answer, onValueChange = { answer = it.take(2000) }, label = { Text(if (question == "review") "Change the draft" else "Your answer") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button(onClick = { val query = AssistantGuidedPlanning.command(id, revision, answer.trim()); open = false; onQuery?.invoke(query) }, enabled = answer.isNotBlank() && onQuery != null, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (question == "review") "Review this edit" else "Use answer") }
            if (question == "review") {
                val save = remember(payload) { responseActions(payload ?: "{}").filterIsInstance<com.focusbyrj.app.util.TalkAction.AskQuery>().firstOrNull { it.query.startsWith("/ayva plan-save $id $revision ") } }
                if (save != null) FilledTonalButton(onClick = { open = false; onQuery?.invoke(save.query) }, enabled = onQuery != null, modifier = Modifier.heightIn(min = 48.dp)) { Text(save.label) }
            }
            TextButton(onClick = { open = false }) { Text("Keep in chat") }
            Text("Only Add tasks saves this plan. Checklist items share their parent's deadline and have no separate reminders.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
