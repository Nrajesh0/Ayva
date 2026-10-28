package com.focusbyrj.app.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Checklist
import com.focusbyrj.app.util.assistant.ChatMode
import com.focusbyrj.app.util.assistant.ChatExperiences
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.ui.components.AyvaIcon

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
    onModeChange: (ChatMode) -> Unit = {},
    onExploreRequest: (String) -> Unit = {}
) {
    var showExplore by rememberSaveable { mutableStateOf(false) }
    if (showExplore && selectedMode != ChatMode.PLAN) {
        AlertDialog(
            onDismissRequest = { showExplore = false },
            title = { Text("Explore Ayva") },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("Small offline activities, with hints and clear endings. Your draft stays here while you explore.") }
                    ChatExperiences.capabilities.groupBy { it.group }.forEach { (group, entries) ->
                        item { Text(group, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                        items(entries, key = { it.id }) { capability ->
                            Surface(onClick = { showExplore = false; onExploreRequest(capability.request) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(capability.title, fontWeight = FontWeight.Medium)
                                    Text(capability.promise, style = MaterialTheme.typography.bodySmall)
                                    Text("Try: ${capability.request}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    item { Text("Use Run: your task, habit and Plan commands remain available in the function row.", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { showExplore = false }) { Text("Close") } }
        )
    }
    val inputText = inputTextFieldValue.text
    val candidateMode = ChatMode.suggested(inputText)?.takeIf { it != selectedMode }
    var dismissedMode by rememberSaveable { mutableStateOf<String?>(null) }
    // Dismiss for this mode/input episode, not just until the next keystroke.
    LaunchedEffect(candidateMode) { if (candidateMode == null) dismissedMode = null }
    val suggestedMode = candidateMode?.takeUnless { it.name == dismissedMode }
    val modeBlue = Color(0xFF3B6EF5)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Horizontal scrollable quick action commands floating above icons
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedMode != ChatMode.PLAN) item {
                Surface(onClick = { showExplore = true }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                    Text("Explore Ayva", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                }
            }
            items(quickActionCommands) { action ->
                Surface(
                    onClick = { val mode = ChatMode.suggested(action.commandText); if (mode != null && action.commandText.trim() == mode.command) onModeChange(mode) else onFillCommand(action.commandText) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                    )
                ) {
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }

        AnimatedVisibility(visible = suggestedMode != null,
            enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF101064),
                modifier = Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp)) {
                    Icon(when (suggestedMode) { ChatMode.TALK -> AyvaIcon; ChatMode.DRILL -> Icons.Filled.Bolt; else -> Icons.Filled.Checklist }, null, tint = Color(0xFFACBCFF), modifier = Modifier.size(20.dp))
                    Text(if (suggestedMode == ChatMode.PLAN) "Create a plan" else suggestedMode?.label.orEmpty(),
                        color = Color(0xFFE0E5FF), style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp))
                    Button(onClick = { suggestedMode?.let(onModeChange) },
                        shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = modeBlue),
                        modifier = Modifier.defaultMinSize(minHeight = 36.dp)) {
                        Text("Use ${suggestedMode?.label?.lowercase().orEmpty()} mode", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { dismissedMode = candidateMode?.name }) {
                        Icon(Icons.Filled.Close, "Dismiss mode suggestion", tint = Color(0xFFACBCFF), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        AnimatedVisibility(visible = suggestions.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                reverseLayout = true
            ) {
                items(suggestions) { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestionClick(suggestion) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = suggestion.displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Toggles Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ayva Talk Quick Action
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                        .clickable {
                            onModeChange(ChatMode.TALK)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AyvaIcon,
                        contentDescription = "Talk to Ayva",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Live Transcription Quick Action
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isLiveTranscribing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            if (isLiveTranscribing) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .clickable { onLiveTranscriptionClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Live transcription",
                        tint = if (isLiveTranscribing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Math Drill Quick Action
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                        .clickable {
                            onModeChange(ChatMode.DRILL)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "Drill",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Priority Toggle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isHighPriority) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            if (isHighPriority) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .clickable { onToggleHighPriority() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Whatshot,
                        contentDescription = "Priority",
                        tint = if (isHighPriority) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Persistent Toggle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isPersistent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            if (isPersistent) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .clickable { onTogglePersistent() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = "Persistent",
                        tint = if (isPersistent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            AnimatedVisibility(visible = parsedDueDateText != null) {
                Text(
                    text = "Setting due: ${parsedDueDateText ?: ""}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputTextFieldValue,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                label = if (selectedMode == ChatMode.CHAT) null else ({
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedMode.label, color = modeBlue, style = MaterialTheme.typography.labelSmall)
                        IconButton(onClick = { onModeChange(ChatMode.CHAT) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, "Exit ${selectedMode.label} mode", tint = modeBlue, modifier = Modifier.size(14.dp))
                        }
                    }
                }),
                visualTransformation = CommandVisualTransformation(),
                trailingIcon = {
                    Row(
                        modifier = Modifier.padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isLiveTranscribing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onLiveTranscriptionClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Live transcription",
                                tint = if (isLiveTranscribing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .clickable {
                                    val cur = inputTextFieldValue.text
                                    if (!cur.startsWith("/")) {
                                        val newText = "/$cur"
                                        onInputChange(
                                            TextFieldValue(
                                                text = newText,
                                                selection = TextRange(newText.length)
                                            )
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "/",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (inputText.isNotBlank()) onSend() })
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(enabled = inputText.isNotBlank()) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
