/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.data.note.ChecklistItem
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.screens.notes.NotesViewModel
import com.focusbyrj.app.ui.screens.notes.KeepColorPalette
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class QuickAddNoteItemActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TARGET_NOTE_ID = "extra_target_note_id"
        const val EXTRA_AUTO_START_VOICE = "extra_auto_start_voice"
        const val EXTRA_CREATE_NEW_NOTE = "extra_create_new_note"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetNoteId = intent.getLongExtra(EXTRA_TARGET_NOTE_ID, -1L).takeIf { it != -1L }
        val autoStartVoice = intent.getBooleanExtra(EXTRA_AUTO_START_VOICE, false)
        val createNewNote = intent.getBooleanExtra(EXTRA_CREATE_NEW_NOTE, false)

        setContent {
            FocusByRjTheme {
                QuickAddNoteItemDialog(
                    targetNoteId = targetNoteId,
                    autoStartVoice = autoStartVoice,
                    createNewNote = createNewNote,
                    onDismiss = { finish() },
                    onItemAdded = {
                        NoteWidgetProvider.updateAllWidgets(this)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun QuickAddNoteItemDialog(
    targetNoteId: Long?,
    autoStartVoice: Boolean,
    createNewNote: Boolean,
    onDismiss: () -> Unit,
    onItemAdded: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val noteDao = remember { NoteDatabase.getInstance(context).noteDao() }

    var targetNote by remember { mutableStateOf<NoteEntity?>(null) }
    var textInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Load target note details if appending
    LaunchedEffect(targetNoteId) {
        if (targetNoteId != null) {
            withContext(Dispatchers.IO) {
                targetNote = noteDao.getNoteByIdSync(targetNoteId)
            }
        }
    }

    // Speech Recognizer launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                textInput = if (textInput.isBlank()) spokenText else "$textInput $spokenText"
            }
        }
    }

    fun startVoiceDictation() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to add to note...")
            }
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (autoStartVoice) {
            startVoiceDictation()
        }
        // Focus keyboard
        kotlinx.coroutines.delay(150)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    fun performSubmit() {
        val trimmed = textInput.trim()
        if (trimmed.isBlank() || isSubmitting) return
        isSubmitting = true

        scope.launch(Dispatchers.IO) {
            try {
                if (createNewNote || targetNoteId == null) {
                    // Create fresh checklist note
                    val newNote = NoteEntity(
                        title = trimmed,
                        isChecklist = true,
                        checklistJson = "[]",
                        colorKey = "default",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    val newId = noteDao.insertNote(newNote)
                    NotesViewModel.latestNotesCache[newId] = newNote.copy(id = newId)
                } else {
                    val note = targetNote ?: NotesViewModel.latestNotesCache[targetNoteId] ?: noteDao.getNoteByIdSync(targetNoteId)
                    if (note != null) {
                        val updatedNote = if (note.isChecklist) {
                            val items = note.getChecklistItems().toMutableList()
                            items.add(ChecklistItem(id = UUID.randomUUID().toString(), text = trimmed, isChecked = false))
                            note.copy(
                                checklistJson = ChecklistItem.listToJson(items),
                                updatedAt = System.currentTimeMillis()
                            )
                        } else {
                            val newContent = if (note.content.isBlank()) trimmed else "${note.content}\n$trimmed"
                            note.copy(content = newContent, updatedAt = System.currentTimeMillis())
                        }
                        noteDao.updateNote(updatedNote)
                        NotesViewModel.latestNotesCache[updatedNote.id] = updatedNote
                    }
                }
                withContext(Dispatchers.Main) {
                    onItemAdded()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isSubmitting = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E2024)
            ),
            border = BorderStroke(1.dp, Color(0xFF333538))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (createNewNote) "New Note" else if (targetNote != null) "Add to: ${targetNote!!.title.ifBlank { "Checklist" }}" else "Quick Add",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFE8EAED),
                            maxLines = 1
                        )
                        Text(
                            text = if (targetNote?.isChecklist == true) "Adds new checklist item" else "Appends to note",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9AA0A6)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF9AA0A6)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Text Input
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            text = if (targetNote?.isChecklist == true) "Enter checklist item..." else "Type note text...",
                            color = Color(0xFF80868B)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = false,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { performSubmit() }),
                    trailingIcon = {
                        IconButton(
                            onClick = { startVoiceDictation() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Dictation",
                                tint = Color(0xFF8AB4F8)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFE8EAED),
                        unfocusedTextColor = Color(0xFFE8EAED),
                        focusedBorderColor = Color(0xFF8AB4F8),
                        unfocusedBorderColor = Color(0xFF353940),
                        cursorColor = Color(0xFF8AB4F8),
                        focusedContainerColor = Color(0xFF131314),
                        unfocusedContainerColor = Color(0xFF131314)
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("Cancel", color = Color(0xFF9AA0A6))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { performSubmit() },
                        enabled = textInput.isNotBlank() && !isSubmitting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8AB4F8),
                            contentColor = Color(0xFF131314)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
