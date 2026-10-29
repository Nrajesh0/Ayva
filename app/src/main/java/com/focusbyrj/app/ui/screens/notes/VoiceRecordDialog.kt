package com.focusbyrj.app.ui.screens.notes

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusbyrj.app.ui.speech.VoiceCaptureContent
import com.focusbyrj.app.ui.speech.VoiceStyle

/** Phone speech capture popup shared by chat and Notes. It receives only the current microphone transcript. */
@Composable
fun VoiceRecordDialog(
    elapsedSeconds: Int, amplitude: Float, liveTranscript: String,
    statusMessage: String = "Listening...", isRecording: Boolean = true,
    errorMessage: String? = null, onRetry: () -> Unit = {},
    onCancel: () -> Unit, onDone: () -> Unit,
    isListening: Boolean = isRecording, isPaused: Boolean = false, onPause: () -> Unit = {},
    retryAfterSeconds: Int = 0, diagnosticInfo: String = "", onTranscriptEdit: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val style = remember { VoiceStyle.read(context) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp, modifier = Modifier.testTag("voice_record_dialog")) {
            VoiceCaptureContent(style, amplitude, elapsedSeconds, liveTranscript, isRecording, isListening, isPaused,
                statusMessage, errorMessage, onCancel, onDone, onPause, onRetry,
                retryAfterSeconds = retryAfterSeconds, onTranscriptEdit = onTranscriptEdit,
                onCopyDiagnostics = if (diagnosticInfo.isBlank()) null else ({
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Ayva speech details", diagnosticInfo))
                    Toast.makeText(context, "Speech details copied. No transcript or audio included.", Toast.LENGTH_SHORT).show()
                }),
                onVoiceSettings = {
                    try { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
                    catch (_: Exception) { Toast.makeText(context, "Open Voice settings to check offline speech languages.", Toast.LENGTH_LONG).show() }
                })
        }
    }
}
