package com.focusbyrj.app.ui.screens.chat

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.R
import com.focusbyrj.app.ui.components.TalkActionChips
import com.focusbyrj.app.ui.screens.ChatMessage
import com.focusbyrj.app.util.assistant.AssistantChatState
import com.focusbyrj.app.util.assistant.AssistantPresentation
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Ordinary turns only. Welcome/cat, brief, test and native feature cards keep their existing renderer. */
@Composable
fun AyvaConversationMessage(
    message: ChatMessage,
    fontSizeSp: Float,
    chatState: AssistantChatState,
    onReply: ((ChatMessage) -> Unit)?,
    onQuery: ((String) -> Unit)?,
    onMessageUpdate: (ChatMessage) -> Unit = {}
) {
    val font = fontSizeSp.takeIf { it.isFinite() }?.coerceIn(12f, 24f) ?: 15f
    val display = remember(message.talkActionJson) { AssistantPresentation.data(message.talkActionJson) }
    val reference = remember(message.talkActionJson) { AssistantPresentation.reference(message.talkActionJson) }
    val receipt = display?.optJSONObject("receipt")
    val quote = display?.optJSONObject("reply")
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showMenu by remember(message.id) { mutableStateOf(false) }
    var showDetails by remember(message.id) { mutableStateOf(false) }
    var drag by remember(message.id) { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val maxDrag = with(LocalDensity.current) { 38.dp.toPx() }
    val canReply = onReply != null && !message.isUser
    val latestReply by rememberUpdatedState(onReply)
    val latestMessage by rememberUpdatedState(message)
    val gesture = if (canReply) Modifier.pointerInput(message.id, threshold, maxDrag) {
        detectHorizontalDragGestures(
            onDragStart = { drag = 0f },
            onHorizontalDrag = { _, amount -> drag = (drag + amount).coerceIn(0f, threshold * 1.5f) },
            onDragCancel = { drag = 0f },
            onDragEnd = { if (drag >= threshold) latestReply?.invoke(latestMessage); drag = 0f }
        )
    }.semantics { customActions = listOf(CustomAccessibilityAction("Reply to message") { latestReply?.invoke(latestMessage); true }) } else Modifier

    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start) {
        if (message.isUser) {
            Surface(
                shape = RoundedCornerShape(17.dp, 17.dp, 4.dp, 17.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (quote != null) {
                        Text("Replying to Ayva", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(parseRichFormattedText(quote.optString("text")), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                    }
                    SelectionContainer { Text(parseRichFormattedText(message.text), fontSize = font.sp, lineHeight = (font * 1.6f).sp) }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Image(painterResource(R.drawable.app_icon), null, Modifier.size(20.dp).clip(CircleShape))
                Text("AYVA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            if (reference != null && receipt == null) {
                val same = chatState.reference?.let { it.kind == reference.kind && it.id == reference.id } == true
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Text(listOfNotNull(reference.title.takeUnless { same }, reference.detail.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "About this ${reference.kind}" },
                            fontSize = (font * 0.78f).coerceAtLeast(11f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Box(gesture.fillMaxWidth()) {
                if (drag > 0) Icon(Icons.AutoMirrored.Filled.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterStart).size(18.dp))
                SelectionContainer(Modifier.offset { IntOffset((drag * 0.45f).coerceAtMost(maxDrag).roundToInt(), 0) }) {
                    Text(parseRichFormattedText(message.text), fontSize = font.sp, lineHeight = (font * 1.6f).sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (receipt != null) {
                Spacer(Modifier.height(6.dp))
                AyvaActionReceipt(receipt, message.talkActionJson, font, chatState, onQuery)
            }
            if (!message.pendingActionJson.isNullOrBlank()) {
                PendingActionCard(message, font, onMessageUpdate)
            }
            if (message.isTalkAction && !message.talkActionJson.isNullOrBlank()) {
                val actionsOnly = remember(message.talkActionJson, chatState) { filteredActions(message.talkActionJson, chatState, receipt != null) }
                if (actionsOnly != null) {
                    Spacer(Modifier.height(6.dp))
                    AyvaConversationActions(actionsOnly, font, onQuery)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.SpaceBetween) {
            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp)),
                fontSize = (font * 0.72f).coerceIn(10f, 14f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!message.isUser) Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreHoriz, "Message actions", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                        showMenu = false; clipboard.setText(AnnotatedString(message.text))
                        Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                    })
                    if (onReply != null) DropdownMenuItem(text = { Text("Reply") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) }, onClick = { showMenu = false; onReply(message) })
                    DropdownMenuItem(text = { Text("Details") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { showMenu = false; showDetails = true })
                }
            }
        }
    }
    if (showDetails) AlertDialog(onDismissRequest = { showDetails = false }, title = { Text("Message details") },
        text = { Text("Ayva · ${DateFormat.getDateTimeInstance().format(Date(message.timestamp))}") },
        confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Close") } })
}

/** Tokens only control availability; the executor still validates them and task versions on every click. */
internal fun filteredActions(payload: String?, state: AssistantChatState, withoutUndo: Boolean): String? = runCatching {
    val root = JSONObject(payload ?: return null)
    val actions = root.optJSONArray("actions") ?: return null
    val kept = org.json.JSONArray()
    for (i in 0 until actions.length()) {
        val action = actions.getJSONObject(i)
        val query = action.optString("query")
        if (action.optString("type") == "ask_query" && (!state.allows(query) || withoutUndo && query.startsWith("/ayva undo "))) continue
        kept.put(action)
    }
    if (kept.length() == 0) null else root.put("actions", kept).toString()
}.getOrNull()

@Composable
private fun AyvaActionReceipt(receipt: JSONObject, payload: String?, font: Float, state: AssistantChatState, onQuery: ((String) -> Unit)?) {
    val items = receipt.optJSONArray("items") ?: return
    val undoQuery = remember(payload) {
        runCatching { val actions = JSONObject(payload ?: "{}").optJSONArray("actions") ?: return@runCatching null
            (0 until actions.length()).map { actions.getJSONObject(it) }.firstOrNull { it.optString("query").startsWith("/ayva undo ") }?.optString("query") }.getOrNull()
    }
    Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Check, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if (receipt.optString("status") == "partial") "Partially saved" else "Saved", fontSize = (font * 0.75f).coerceAtLeast(11f).sp, color = MaterialTheme.colorScheme.primary)
            }
            for (i in 0 until minOf(items.length(), 10)) {
                val item = items.optJSONObject(i) ?: continue
                Text(item.optString("title"), fontSize = (font * 0.9f).coerceAtLeast(12f).sp, fontWeight = FontWeight.Medium)
                Text(item.optString("detail"), fontSize = (font * 0.85f).coerceAtLeast(12f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.optString("previous").takeIf { it.isNotBlank() }?.let {
                    Text("Previously $it", fontSize = (font * 0.75f).coerceAtLeast(11f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (items.length() > 10) Text("And ${items.length() - 10} more changes", style = MaterialTheme.typography.bodySmall)
            if (undoQuery != null && state.allows(undoQuery) && onQuery != null) TextButton(onClick = { onQuery(undoQuery) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Undo") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AyvaConversationActions(payload: String, font: Float, onQuery: ((String) -> Unit)?) {
    val queries = remember(payload) {
        runCatching {
            val all = JSONObject(payload).getJSONArray("actions")
            val entries = (0 until all.length()).map { all.getJSONObject(it) }
            if (entries.all { it.optString("type") == "ask_query" }) entries.map { it.optString("label") to it.optString("query") } else null
        }.getOrNull()
    }
    if (queries == null || onQuery == null) {
        TalkActionChips(payload, font, onQueryClick = onQuery)
    } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        queries.forEach { (label, query) ->
            AssistChip(onClick = { onQuery(query) }, label = { Text(label, fontSize = (font * 0.85f).coerceAtLeast(12f).sp) })
        }
    }
}

/** Shared boundary for layout spacing; native cards, cats and briefs keep their existing treatment. */
internal fun ChatMessage.isOrdinaryConversationTurn(): Boolean {
    if (isUser) return true
    if (isArithmetic || isDrillSummary || isAptitudeProfile || isStreakPrompt || isTaskSummary || isDailyQuests || isMysteryBox || isMorningBrief || isEveningBrief || isStreakFreezeSkipped || isVocabBrief || isVocabHub || isWelcome || isHabitsSummary) return false
    if (listOf("morning_", "evening_", "welcome_", "create_", "conflict_", "angry_freeze_", "mystery_box_", "habits_", "vocab_hub_").any { id.startsWith(it) }) return false
    return listOf("Added to your radar", "Conflict Detected", "Hey! Ayva is on deck", "ready for action", "Ayva here!", "I'm Ayva", "Hey there! I'm Ayva", "anti-procrastination", "cognitive mastery", "focus & learning companion").none { text.contains(it, ignoreCase = true) }
}
