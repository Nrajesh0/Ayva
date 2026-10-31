package com.focusbyrj.app.ui.speech

import com.focusbyrj.app.ui.components.settingsSearchTarget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.sin

@Composable
fun VoiceStyleSettingsRow() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(VoiceStyle.read(context)) }
    var showPicker by remember { mutableStateOf(false) }
    ListItem(headlineContent = { Text("Live transcription style") }, supportingContent = { Text("${selected.title} · Choose and preview") },
        leadingContent = { Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Default.Visibility, "Choose and preview voice styles") },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.fillMaxWidth().settingsSearchTarget("Live transcription style").clickable { showPicker = true }.testTag("voice_style_settings"))
    if (showPicker) VoiceStylePicker(selected, onDismiss = { showPicker = false }, onApply = {
        VoiceStyle.save(context, it); selected = it; showPicker = false
    })
}

@Composable
fun VoiceStylePicker(selected: VoiceStyle, onDismiss: () -> Unit, onApply: (VoiceStyle) -> Unit) {
    var choice by remember(selected) { mutableStateOf(selected) }
    var preview by remember { mutableStateOf<VoiceStyle?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Transcription appearance") },
        text = {
            Column {
                Text("Ten listening visuals, using your app theme. Preview uses sample audio levels; your microphone stays off.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(VoiceStyle.entries, key = { it.id }) { style ->
                        Row(Modifier.fillMaxWidth().clickable { choice = style }.padding(vertical = 5.dp).testTag("voice_style_${style.id}"), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = choice == style, onClick = { choice = style })
                            Column(Modifier.weight(1f)) { Text(style.title, style = MaterialTheme.typography.bodyMedium); Text(style.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            IconButton(onClick = { preview = style }, modifier = Modifier.testTag("voice_preview_${style.id}")) { Icon(Icons.Default.Visibility, "Preview ${style.title}") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(choice) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
    preview?.let { style -> VoiceStylePreview(style, onDismiss = { preview = null }, onChoose = { choice = style; preview = null }) }
}

@Composable
fun VoiceStylePreview(style: VoiceStyle, onDismiss: () -> Unit, onChoose: () -> Unit) {
    var paused by remember(style) { mutableStateOf(false) }
    var sample by remember(style) { mutableFloatStateOf(.2f) }
    val motion = rememberVoiceMotionAllowed()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(paused, style, motion, lifecycle) {
        if (!motion || paused) { sample = 0f; return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        var tick = 0
        while (!paused) {
            sample = (.1f + .7f * kotlin.math.abs(sin(tick * .17f)) * (.6f + .4f * sin(tick * .07f))).coerceIn(0f, 1f)
            tick++; delay(60)
        }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column {
                Text("${style.title} · Sample preview", Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                VoiceCaptureContent(style, sample, 18, "Pick up milk on the way home.\nAnd call Mum…",
                    !paused, !paused, paused, "Preview paused", null, onDismiss, onChoose, { paused = true }, { paused = false }, doneLabel = "Choose style")
            }
        }
    }
}
