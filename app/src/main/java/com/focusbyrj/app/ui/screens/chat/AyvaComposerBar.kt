package com.focusbyrj.app.ui.screens.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.focusbyrj.app.util.assistant.ChatMode

data class QuickActionCommand(val label: String, val commandText: String)
data class Suggestion(val displayText: String, val replacementText: String)

class CommandVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val input = text.text
        val builder = AnnotatedString.Builder(input)
        if (input.startsWith("/")) {
            val firstSpace = input.indexOf(' ')
            val cmdEnd = if (firstSpace != -1) firstSpace else input.length
            builder.addStyle(
                SpanStyle(color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold),
                0,
                cmdEnd
            )
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun AyvaComposerBar(
    inputTextFieldValue: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    quickActionCommands: List<QuickActionCommand>,
    onFillCommand: (String) -> Unit,
    suggestions: List<Suggestion>,
    onSuggestionClick: (Suggestion) -> Unit,
    isHighPriority: Boolean,
    onToggleHighPriority: () -> Unit,
    isPersistent: Boolean,
    onTogglePersistent: () -> Unit,
    parsedDueDateText: String?,
    onSend: () -> Unit,
    isLiveTranscribing: Boolean = false,
    onLiveTranscriptionClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    selectedMode: ChatMode = ChatMode.CHAT,
    onModeChange: (ChatMode) -> Unit = {}
) {
    val inputText = inputTextFieldValue.text
    val modeBlue = Color(0xFF3B82F6)
    var showModes by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    val suggestedMode = ChatMode.suggested(inputText)?.takeIf { it != selectedMode }

    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        AnimatedVisibility(visible = suggestedMode != null,
            enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            TextButton(onClick = { suggestedMode?.let(onModeChange) },
                colors = ButtonDefaults.textButtonColors(contentColor = modeBlue)) {
                Text("Use ${suggestedMode?.label.orEmpty()} mode", style = MaterialTheme.typography.labelMedium)
            }
        }
        AnimatedVisibility(visible = suggestions.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp).padding(bottom = 6.dp)
                .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                reverseLayout = true) {
                items(suggestions) { suggestion ->
                    Text(suggestion.displayText, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clickable { onSuggestionClick(suggestion) }
                            .padding(horizontal = 16.dp, vertical = 14.dp))
                }
            }
        }
        Surface(shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))) {
            Column {
                AnimatedVisibility(visible = selectedMode != ChatMode.CHAT,
                    enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Text(selectedMode.label + " mode", color = modeBlue,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp))
                }
                TextField(value = inputTextFieldValue, onValueChange = onInputChange,
                    placeholder = { Text(when (selectedMode) {
                        ChatMode.PLAN -> "What would you like to plan?"
                        ChatMode.TALK -> "What's on your mind?"
                        ChatMode.DRILL -> "Choose a difficulty"
                        ChatMode.CHAT -> "Message Ayva"
                    }) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp), maxLines = 4,
                    visualTransformation = CommandVisualTransformation(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (inputText.isNotBlank()) onSend() }))
                if (parsedDueDateText != null) Text("Setting due: $parsedDueDateText",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { showOptions = true }) {
                            Icon(Icons.Filled.MoreHoriz, "Chat options", Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false },
                            modifier = Modifier.heightIn(max = 360.dp)) {
                            DropdownMenuItem(text = { Text("High priority") },
                                trailingIcon = { Checkbox(checked = isHighPriority, onCheckedChange = null) },
                                onClick = { onToggleHighPriority(); showOptions = false })
                            DropdownMenuItem(text = { Text("Persistent reminder") },
                                trailingIcon = { Checkbox(checked = isPersistent, onCheckedChange = null) },
                                onClick = { onTogglePersistent(); showOptions = false })
                            HorizontalDivider()
                            quickActionCommands.filter { ChatMode.suggested(it.commandText) == null }.forEach { action ->
                                DropdownMenuItem(text = { Text(action.label) },
                                    onClick = { showOptions = false; onFillCommand(action.commandText) })
                            }
                        }
                    }
                    if (isHighPriority) Icon(Icons.Filled.Whatshot, "High priority enabled",
                        Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    if (isPersistent) Icon(Icons.Filled.NotificationsActive, "Persistent reminder enabled",
                        Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Box {
                        TextButton(onClick = { showModes = true },
                            modifier = Modifier.semantics { contentDescription = "Choose conversation mode" },
                            colors = ButtonDefaults.textButtonColors(contentColor =
                                if (selectedMode == ChatMode.CHAT) MaterialTheme.colorScheme.onSurfaceVariant else modeBlue),
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            AnimatedContent(targetState = selectedMode, label = "composer mode") { mode ->
                                Text(if (mode == ChatMode.CHAT) "Modes" else mode.label,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            Icon(Icons.Filled.ExpandMore, null, Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = showModes, onDismissRequest = { showModes = false }) {
                            ChatMode.entries.forEach { mode ->
                                DropdownMenuItem(text = { Text(mode.label) },
                                    trailingIcon = { if (mode == selectedMode) Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) },
                                    onClick = { showModes = false; onModeChange(mode) })
                            }
                        }
                    }
                    if (selectedMode != ChatMode.CHAT) IconButton(onClick = { onModeChange(ChatMode.CHAT) }) {
                        Icon(Icons.Filled.Close, "Exit ${selectedMode.label} mode", Modifier.size(16.dp), tint = modeBlue)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onLiveTranscriptionClick,
                        colors = IconButtonDefaults.iconButtonColors(contentColor =
                            if (isLiveTranscribing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) {
                        Icon(Icons.Filled.Mic, if (isLiveTranscribing) "Stop live transcription" else "Live transcription", Modifier.size(20.dp))
                    }
                    FilledIconButton(onClick = onSend, enabled = inputText.isNotBlank(),
                        modifier = Modifier.size(48.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send", Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
