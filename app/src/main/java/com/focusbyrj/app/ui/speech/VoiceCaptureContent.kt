package com.focusbyrj.app.ui.speech

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale

/** Shared by actual capture and reference previews. Preview never instantiates a speech recognizer. */
@Composable
fun VoiceCaptureContent(
    style: VoiceStyle, amplitude: Float, elapsedSeconds: Int, liveTranscript: String,
    isRecording: Boolean, isListening: Boolean, isPaused: Boolean,
    statusMessage: String, errorMessage: String?, onCancel: () -> Unit, onDone: () -> Unit,
    onPause: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier,
    onVoiceSettings: (() -> Unit)? = null, doneLabel: String = "Use text"
) {
    val colors = MaterialTheme.colorScheme
    val config = LocalConfiguration.current
    val maxHeight = (config.screenHeightDp * .86f).dp
    Column(modifier.fillMaxWidth().heightIn(max = maxHeight).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Live transcription", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(String.format(Locale.getDefault(), "%02d:%02d", elapsedSeconds.coerceAtLeast(0) / 60, elapsedSeconds.coerceAtLeast(0) % 60),
                style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            VoiceVisualization(style, amplitude, isRecording && isListening,
                Modifier.fillMaxWidth().height(if (config.screenHeightDp < 520) 76.dp else 156.dp).testTag("voice_visual_${style.id}"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if (errorMessage != null) colors.error else if (isListening) colors.primary else colors.onSurfaceVariant, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(when { errorMessage != null -> "Transcription paused"; isPaused -> "Paused"; isListening -> "Listening"; else -> "Connecting…" },
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.testTag("voice_record_mode_badge"))
            }
            Spacer(Modifier.height(16.dp))
            TranscriptText(liveTranscript, if (isListening) "Speak naturally. Your words appear here." else if (isPaused) "Resume whenever you're ready." else statusMessage)
            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.testTag("voice_record_error"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (onVoiceSettings != null && errorMessage.contains("language", true)) TextButton(onClick = onVoiceSettings) { Text("Voice settings") }
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("voice_record_retry")) { Text("Retry") }
                }
            } else if (isRecording && !isListening && statusMessage.isNotBlank() && liveTranscript.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onCancel, colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.onSurface.copy(alpha = .07f), contentColor = colors.onSurface), modifier = Modifier.size(48.dp).testTag("voice_record_cancel_btn")) {
                Icon(Icons.Default.Close, "Cancel live transcription")
            }
            if (isRecording || isPaused) {
                FilledTonalIconButton(onClick = if (isPaused) onRetry else onPause,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.onSurface.copy(alpha = .07f), contentColor = colors.onSurface),
                    modifier = Modifier.size(48.dp).testTag("voice_record_pause_btn")) {
                    Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        if (isPaused) "Resume live transcription" else "Pause live transcription")
                }
            }
            Button(onClick = onDone, enabled = liveTranscript.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary,
                    contentColor = if (colors.primary.luminance() > .179f) Color(0xFF111111) else Color.White), shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("voice_record_done_btn")) {
                Icon(Icons.Default.Done, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(doneLabel)
            }
        }
    }
}

@Composable
private fun TranscriptText(text: String, emptyMessage: String) {
    val scroll = rememberScrollState()
    var followTail by remember { mutableStateOf(true) }
    val nearEndPx = with(LocalDensity.current) { 32.dp.roundToPx() }
    // New captions follow the end only while the reader has not scrolled up.
    LaunchedEffect(scroll) {
        snapshotFlow { if (scroll.isScrollInProgress) scroll.maxValue - scroll.value <= nearEndPx else null }
            .distinctUntilChanged().collect { if (it != null) followTail = it }
    }
    LaunchedEffect(text, followTail) {
        if (followTail) snapshotFlow { scroll.maxValue }.distinctUntilChanged().collect { scroll.scrollTo(it) }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, .035f),
        modifier = Modifier.fillMaxWidth().heightIn(min = 86.dp, max = 192.dp)) {
        SelectionContainer {
            Text(text.ifBlank { emptyMessage }, modifier = Modifier.fillMaxWidth().verticalScroll(scroll).padding(14.dp).testTag("voice_record_transcript"),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
                color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        }
    }
}
