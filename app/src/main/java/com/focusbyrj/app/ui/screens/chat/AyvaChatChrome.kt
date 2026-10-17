package com.focusbyrj.app.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.R
import com.focusbyrj.app.ui.screens.ChatMessage
import com.focusbyrj.app.util.assistant.AssistantItemReference
import java.text.DateFormat
import java.util.Date

@Composable
fun AyvaCompactHeader(onClose: () -> Unit, onSearch: () -> Unit, onColors: () -> Unit,
                      onTextSize: () -> Unit, onClear: () -> Unit, onSettings: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close chat", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface) }
        Image(painterResource(R.drawable.app_icon), null, Modifier.size(25.dp).clip(CircleShape))
        Text("Ayva", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 8.dp))
        IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search conversation", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "Chat controls", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Notification Colors") }, leadingIcon = { Icon(Icons.Default.AutoFixHigh, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { menu = false; onColors() })
                DropdownMenuItem(text = { Text("Text Size") }, leadingIcon = { Icon(Icons.Default.FormatSize, null) }, onClick = { menu = false; onTextSize() })
                DropdownMenuItem(text = { Text("Clear History", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onClear() })
                DropdownMenuItem(text = { Text("Open Settings") }, onClick = { menu = false; onSettings() })
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
}

@Composable
fun AyvaActiveContext(reference: AssistantItemReference?, onSwitch: () -> Unit, onRelease: () -> Unit) {
    if (reference == null) return
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onSwitch, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)), modifier = Modifier.weight(1f, fill = false)) {
            Text("Talking about · ${reference.title}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
        }
        IconButton(onClick = onRelease) { Icon(Icons.Default.Close, "Release active reference", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun AyvaReplyPreview(message: ChatMessage, onCancel: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f), contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text("Replying to Ayva", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(parseRichFormattedText(message.text), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel reply") }
        }
    }
}

/** Search the visible transcript locally, then jump to the real message instead of replacing native cards. */
@Composable
fun AyvaConversationSearch(messages: List<ChatMessage>, onSelect: (ChatMessage) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(messages, query) { if (query.isBlank()) emptyList() else messages.asReversed().filter { it.text.contains(query.trim(), ignoreCase = true) }.take(100) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Search conversation") }, text = {
        Column {
            OutlinedTextField(query, { query = it.take(200) }, label = { Text("Find a message") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            if (results.isEmpty()) Text(if (query.isBlank()) "Search messages stored on this device." else "No matching messages.", style = MaterialTheme.typography.bodySmall)
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(results, key = { it.id }) { message ->
                    Column(Modifier.fillMaxWidth().clickable { onSelect(message) }.padding(vertical = 12.dp)) {
                        Text(if (message.isUser) "You" else "Ayva", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(parseRichFormattedText(message.text), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(message.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
fun AyvaReferencePicker(references: List<AssistantItemReference>, onSelect: (AssistantItemReference) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(references, query) { references.filter { it.title.contains(query, ignoreCase = true) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("What are we talking about?") }, text = {
        Column {
            OutlinedTextField(query, { query = it.take(200) }, label = { Text("Find a task or habit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (filtered.isEmpty()) Text("No matching tasks or habits.", modifier = Modifier.padding(top = 12.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(filtered, key = { "${it.kind}:${it.id}" }) { item ->
                    Column(Modifier.fillMaxWidth().clickable { onSelect(item) }.padding(vertical = 12.dp)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium)
                        Text(listOf(item.kind.replaceFirstChar { it.uppercase() }, item.detail).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
