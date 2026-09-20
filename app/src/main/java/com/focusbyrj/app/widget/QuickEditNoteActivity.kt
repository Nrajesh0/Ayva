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
import android.content.res.Configuration
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.focusbyrj.app.data.note.ChecklistItem
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.screens.notes.NotesViewModel
import com.focusbyrj.app.ui.screens.notes.KeepColorPalette
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class QuickEditNoteActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_CREATE_NEW = "extra_create_new"
        const val EXTRA_AUTO_VOICE = "extra_auto_voice"
        const val EXTRA_IS_CHECKLIST = "extra_is_checklist"
        const val EXTRA_TARGET_ITEM_ID = "extra_target_item_id"
        const val EXTRA_APPWIDGET_ID = "extra_appwidget_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L).takeIf { it != -1L }
        val createNew = intent.getBooleanExtra(EXTRA_CREATE_NEW, false)
        val autoVoice = intent.getBooleanExtra(EXTRA_AUTO_VOICE, false)
        val isChecklist = intent.getBooleanExtra(EXTRA_IS_CHECKLIST, false)
        val targetItemId = intent.getStringExtra(EXTRA_TARGET_ITEM_ID)
        val appWidgetId = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID).takeIf { it != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID }
            ?: intent.getIntExtra(EXTRA_APPWIDGET_ID, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID).takeIf { it != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID }

        setContent {
            FocusByRjTheme {
                QuickEditNoteOverlay(
                    noteId = noteId,
                    createNew = createNew || (noteId == null),
                    autoVoice = autoVoice,
                    initialIsChecklist = isChecklist,
                    initialTargetItemId = targetItemId,
                    appWidgetId = appWidgetId,
                    onDismiss = {
                        finish()
                    },
                    onSaved = {
                        NoteWidgetProvider.updateAllWidgets(this@QuickEditNoteActivity.applicationContext)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun QuickEditNoteOverlay(
    noteId: Long?,
    createNew: Boolean,
    autoVoice: Boolean,
    initialIsChecklist: Boolean,
    initialTargetItemId: String? = null,
    appWidgetId: Int? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appContext = remember { context.applicationContext }
    val scope = rememberCoroutineScope()
    val noteDao = remember { NoteDatabase.getInstance(appContext).noteDao() }
    val isSystemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    var loadedNote by remember { mutableStateOf<NoteEntity?>(null) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var isChecklistMode by remember { mutableStateOf(initialIsChecklist) }
    val checklistItems = remember { mutableStateListOf<ChecklistItem>() }
    var colorKey by remember { mutableStateOf("default") }
    var isPinned by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var targetFocusItemId by remember { mutableStateOf<String?>(initialTargetItemId) }
    val checklistListState = rememberLazyListState()

    val titleFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val addItemFocusRequester = remember { FocusRequester() }

    // Load existing note
    LaunchedEffect(noteId) {
        if (noteId != null && noteId > 0) {
            withContext(Dispatchers.IO) {
                val cached = NotesViewModel.latestNotesCache[noteId]
                val dbNote = noteDao.getNoteByIdSync(noteId)
                val entity = when {
                    cached != null && dbNote != null -> if (cached.updatedAt >= dbNote.updatedAt) cached else dbNote
                    dbNote != null -> dbNote
                    cached != null -> cached
                    else -> null
                }
                if (entity != null) {
                    loadedNote = entity
                    title = entity.title
                    content = entity.content
                    isChecklistMode = entity.isChecklist
                    colorKey = entity.colorKey
                    isPinned = entity.isPinned
                    checklistItems.clear()
                    checklistItems.addAll(entity.getChecklistItems())
                }
            }
        }
    }

    // Voice dictation launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                if (isChecklistMode) {
                    val firstChecked = checklistItems.indexOfFirst { it.isChecked }
                    val insIdx = if (firstChecked != -1) firstChecked else checklistItems.size
                    val newId = UUID.randomUUID().toString()
                    targetFocusItemId = newId
                    checklistItems.add(insIdx, ChecklistItem(id = newId, text = spoken, isChecked = false))
                    scope.launch {
                        checklistListState.animateScrollToItem(insIdx)
                    }
                } else {
                    content = if (content.isBlank()) spoken else "$content $spoken"
                }
            }
        }
    }

    fun startVoiceDictation() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to edit note...")
            }
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Voice input not supported", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (autoVoice) {
            startVoiceDictation()
        }
    }

    fun saveNote(andFinish: Boolean = true) {
        scope.launch(Dispatchers.IO) {
            val trimmedTitle = title.trim()
            val trimmedContent = content.trim()
            val hasChecklistItems = checklistItems.any { it.text.isNotBlank() }

            val jsonChecklist = ChecklistItem.listToJson(checklistItems.toList())
            val candidate = loadedNote?.copy(
                title = trimmedTitle,
                content = trimmedContent,
                isChecklist = isChecklistMode,
                checklistJson = jsonChecklist,
                colorKey = colorKey,
                isPinned = isPinned,
                updatedAt = System.currentTimeMillis()
            ) ?: NoteEntity(
                title = trimmedTitle,
                content = trimmedContent,
                isChecklist = isChecklistMode,
                checklistJson = jsonChecklist,
                colorKey = colorKey,
                isPinned = isPinned,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            if (candidate.isEmptyNote()) {
                // If it was already in DB and completely empty (no text, checklist, images, audio, labels), delete it
                if (loadedNote != null && loadedNote!!.id > 0) {
                    val toDelete = loadedNote!!
                    com.focusbyrj.app.data.note.NoteMediaManager.deleteNoteMediaFiles(toDelete)
                    noteDao.deleteNote(toDelete)
                    NotesViewModel.latestNotesCache.remove(toDelete.id)
                    loadedNote = null
                }
            } else {
                if (loadedNote != null && loadedNote!!.id > 0) {
                    noteDao.updateNote(candidate)
                    loadedNote = candidate
                    NotesViewModel.latestNotesCache[candidate.id] = candidate
                } else {
                    val newId = noteDao.insertNote(candidate)
                    val inserted = candidate.copy(id = newId)
                    loadedNote = inserted
                    NotesViewModel.latestNotesCache[newId] = inserted
                }
            }
            if (appWidgetId != null && appWidgetId != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
                val finalId = loadedNote?.id
                if (finalId != null && finalId > 0) {
                    NoteWidgetConfigHelper.setCurrentNoteId(appContext, appWidgetId, finalId)
                    NoteWidgetConfigHelper.setCurrentIndex(appContext, appWidgetId, 0)
                }
            }
            try {
                NoteWidgetProvider.updateAllWidgets(appContext)
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                if (andFinish) {
                    onSaved()
                }
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                saveNote(andFinish = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler {
        if (showColorPicker) {
            showColorPicker = false
        } else {
            saveNote(andFinish = true)
        }
    }

    // Determine card background color
    val themeItem = KeepColorPalette.getColor(colorKey)
    val cardBackground = if (colorKey == "default") {
        if (isSystemDark) Color(0xFF1E2024) else Color(0xFFFFFFFF)
    } else {
        themeItem.getBackgroundColor(isSystemDark)
    }
    val primaryTextColor = if (colorKey == "default") {
        if (isSystemDark) Color(0xFFE8EAED) else Color(0xFF202124)
    } else {
        themeItem.getTextColor(isSystemDark)
    }
    val secondaryTextColor = primaryTextColor.copy(alpha = 0.7f)
    val borderColor = if (isSystemDark) Color(0xFF333538) else Color(0xFFE0E0E0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    saveNote(andFinish = true)
                }
            )
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 580.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardBackground
            ),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Top Header Row: Title & Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isChecklistMode) Icons.Default.FormatListBulleted else Icons.Default.Notes,
                            contentDescription = null,
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (loadedNote != null) "Edit Note" else "New Note",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = secondaryTextColor
                        )
                    }

                    // Pin toggle
                    IconButton(
                        onClick = { isPinned = !isPinned },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin Note",
                            tint = if (isPinned) Color(0xFF8AB4F8) else secondaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Color palette toggle
                    IconButton(
                        onClick = { showColorPicker = !showColorPicker },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Theme Color",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Switch checklist / plain note
                    IconButton(
                        onClick = {
                            if (isChecklistMode) {
                                // Convert checklist to plain text
                                val converted = checklistItems.map { it.text }.filter { it.isNotBlank() }.joinToString("\n")
                                content = converted
                                checklistItems.clear()
                                isChecklistMode = false
                            } else {
                                // Convert plain text to checklist
                                val lines = content.lines().filter { it.isNotBlank() }
                                checklistItems.clear()
                                if (lines.isNotEmpty()) {
                                    checklistItems.addAll(lines.map { ChecklistItem(text = it, isChecked = false) })
                                } else {
                                    checklistItems.add(ChecklistItem(text = "", isChecked = false))
                                }
                                content = ""
                                isChecklistMode = true
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isChecklistMode) Icons.Default.Notes else Icons.Default.FormatListBulleted,
                            contentDescription = "Switch Note Type",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Close / Dismiss
                    IconButton(
                        onClick = { saveNote(andFinish = true) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close & Save",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Color palette row
                AnimatedVisibility(visible = showColorPicker) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KeepColorPalette.allColors.take(8).forEach { colorItem ->
                                val itemBg = colorItem.getBackgroundColor(isSystemDark)
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(itemBg)
                                        .border(
                                            width = if (colorKey == colorItem.key) 2.dp else 1.dp,
                                            color = if (colorKey == colorItem.key) Color(0xFF8AB4F8) else borderColor,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            colorKey = colorItem.key
                                            showColorPicker = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (colorKey == colorItem.key) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = colorItem.getTextColor(isSystemDark),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Editable Title Field
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    ),
                    cursorBrush = SolidColor(if (isSystemDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8)),
                    decorationBox = { innerTextField ->
                        Box {
                            if (title.isEmpty()) {
                                Text(
                                    text = "Title",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = secondaryTextColor.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { contentFocusRequester.requestFocus() }),
                    singleLine = true
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    thickness = 0.8.dp,
                    color = borderColor
                )

                // Body content area: Text note or Checklist
                if (!isChecklistMode) {
                    // Plain Text Note Body
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(min = 120.dp, max = 320.dp)
                            .fillMaxWidth()
                    ) {
                        BasicTextField(
                            value = content,
                            onValueChange = { content = it },
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = primaryTextColor
                            ),
                            cursorBrush = SolidColor(if (isSystemDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8)),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (content.isEmpty()) {
                                        Text(
                                            text = "Note text...",
                                            fontSize = 15.sp,
                                            color = secondaryTextColor.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(contentFocusRequester)
                        )
                    }
                } else {
                    // Checklist Items
                    var completedExpanded by remember { mutableStateOf(true) }
                    val uncompletedItems = checklistItems.filter { !it.isChecked }
                    val completedItems = checklistItems.filter { it.isChecked }

                    LazyColumn(
                        state = checklistListState,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(min = 140.dp, max = 380.dp)
                            .fillMaxWidth()
                    ) {
                        items(
                            items = uncompletedItems,
                            key = { it.id }
                        ) { item ->
                            QuickEditChecklistRow(
                                item = item,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                isTargetFocus = item.id == targetFocusItemId,
                                onFocused = { if (targetFocusItemId == item.id) targetFocusItemId = null },
                                onMoveUp = {
                                    val idx = checklistItems.indexOfFirst { it.id == item.id }
                                    if (idx > 0) {
                                        val moved = checklistItems.removeAt(idx)
                                        checklistItems.add(idx - 1, moved)
                                        saveNote(andFinish = false)
                                    }
                                },
                                onMoveDown = {
                                    val idx = checklistItems.indexOfFirst { it.id == item.id }
                                    if (idx != -1 && idx < checklistItems.size - 1) {
                                        val moved = checklistItems.removeAt(idx)
                                        checklistItems.add(idx + 1, moved)
                                        saveNote(andFinish = false)
                                    }
                                },
                                onToggle = {
                                    val idx = checklistItems.indexOfFirst { it.id == item.id }
                                    if (idx != -1) {
                                        checklistItems[idx] = item.copy(isChecked = true)
                                        val uncompleted = checklistItems.filter { !it.isChecked }
                                        val completed = checklistItems.filter { it.isChecked }
                                        checklistItems.clear()
                                        checklistItems.addAll(uncompleted + completed)
                                        saveNote(andFinish = false)
                                    }
                                },
                                onTextChange = { newTxt ->
                                    val idx = checklistItems.indexOfFirst { it.id == item.id }
                                    if (idx != -1) {
                                        checklistItems[idx] = item.copy(text = newTxt)
                                    }
                                },
                                onEnterPressed = { extraText ->
                                    val newId = UUID.randomUUID().toString()
                                    targetFocusItemId = newId
                                    val currentIdx = checklistItems.indexOfFirst { it.id == item.id }
                                    val uncompletedCount = checklistItems.count { !it.isChecked }
                                    val insertIdx = if (currentIdx != -1) (currentIdx + 1).coerceAtMost(uncompletedCount) else uncompletedCount
                                    checklistItems.add(
                                        insertIdx,
                                        ChecklistItem(
                                            id = newId,
                                            text = extraText,
                                            isChecked = false
                                        )
                                    )
                                    saveNote(andFinish = false)
                                },
                                onDelete = {
                                    val idx = checklistItems.indexOfFirst { it.id == item.id }
                                    if (idx != -1) {
                                        if (checklistItems.size > 1) {
                                            checklistItems.removeAt(idx)
                                            saveNote(andFinish = false)
                                        } else if (checklistItems.size == 1) {
                                            checklistItems[0] = checklistItems[0].copy(text = "")
                                            saveNote(andFinish = false)
                                        }
                                    }
                                }
                            )
                        }

                        item(key = "add_list_item_button") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val uncompletedCount = checklistItems.count { !it.isChecked }
                                        val newId = UUID.randomUUID().toString()
                                        targetFocusItemId = newId
                                        checklistItems.add(
                                            uncompletedCount,
                                            ChecklistItem(
                                                id = newId,
                                                text = "",
                                                isChecked = false
                                            )
                                        )
                                        saveNote(andFinish = false)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add list item",
                                    tint = primaryTextColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "List item",
                                    fontSize = 15.sp,
                                    color = primaryTextColor.copy(alpha = 0.6f)
                                )
                            }
                        }

                        if (completedItems.isNotEmpty()) {
                            item(key = "completed_section_header") {
                                Column {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(thickness = 0.8.dp, color = borderColor)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { completedExpanded = !completedExpanded }
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (completedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (completedExpanded) "Collapse completed" else "Expand completed",
                                            tint = secondaryTextColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${completedItems.size} Completed items",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = secondaryTextColor
                                        )
                                    }
                                }
                            }

                            if (completedExpanded) {
                                items(
                                    items = completedItems,
                                    key = { it.id }
                                ) { item ->
                                    QuickEditChecklistRow(
                                        item = item,
                                        primaryTextColor = primaryTextColor,
                                        secondaryTextColor = secondaryTextColor,
                                        isTargetFocus = item.id == targetFocusItemId,
                                        onFocused = { if (targetFocusItemId == item.id) targetFocusItemId = null },
                                        onMoveUp = {
                                            val idx = checklistItems.indexOfFirst { it.id == item.id }
                                            if (idx > 0) {
                                                val moved = checklistItems.removeAt(idx)
                                                checklistItems.add(idx - 1, moved)
                                                saveNote(andFinish = false)
                                            }
                                        },
                                        onMoveDown = {
                                            val idx = checklistItems.indexOfFirst { it.id == item.id }
                                            if (idx != -1 && idx < checklistItems.size - 1) {
                                                val moved = checklistItems.removeAt(idx)
                                                checklistItems.add(idx + 1, moved)
                                                saveNote(andFinish = false)
                                            }
                                        },
                                        onToggle = {
                                            val idx = checklistItems.indexOfFirst { it.id == item.id }
                                            if (idx != -1) {
                                                checklistItems[idx] = item.copy(isChecked = false)
                                                val uncompleted = checklistItems.filter { !it.isChecked }
                                                val completed = checklistItems.filter { it.isChecked }
                                                checklistItems.clear()
                                                checklistItems.addAll(uncompleted + completed)
                                                saveNote(andFinish = false)
                                            }
                                        },
                                        onTextChange = { newTxt ->
                                            val idx = checklistItems.indexOfFirst { it.id == item.id }
                                            if (idx != -1) {
                                                checklistItems[idx] = item.copy(text = newTxt)
                                            }
                                        },
                                        onEnterPressed = { extraText ->
                                            val newId = UUID.randomUUID().toString()
                                            targetFocusItemId = newId
                                            val uncompletedCount = checklistItems.count { !it.isChecked }
                                            checklistItems.add(
                                                uncompletedCount,
                                                ChecklistItem(
                                                    id = newId,
                                                    text = extraText,
                                                    isChecked = false
                                                )
                                            )
                                            saveNote(andFinish = false)
                                        },
                                        onDelete = {
                                            val idx = checklistItems.indexOfFirst { it.id == item.id }
                                            if (idx != -1) {
                                                if (checklistItems.size > 1) {
                                                    checklistItems.removeAt(idx)
                                                    saveNote(andFinish = false)
                                                } else if (checklistItems.size == 1) {
                                                    checklistItems[0] = checklistItems[0].copy(text = "")
                                                    saveNote(andFinish = false)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Bar: Mic Dictation & Save Done Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { startVoiceDictation() },
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = if (isSystemDark) Color(0xFF282A2E) else Color(0xFFF1F3F4),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Dictate",
                            tint = Color(0xFF8AB4F8),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { onDismiss() }
                        ) {
                            Text("Discard", color = secondaryTextColor)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                saveNote(andFinish = true)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8AB4F8),
                                contentColor = Color(0xFF131314)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Done",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun QuickEditChecklistRow(
    item: ChecklistItem,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isTargetFocus: Boolean,
    onFocused: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: () -> Unit,
    onTextChange: (String) -> Unit,
    onEnterPressed: (String) -> Unit,
    onDelete: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val haptic = LocalHapticFeedback.current

    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val rowThresholdPx = remember(density) { with(density) { 44.dp.toPx() } }

    val currentOnMoveUp by rememberUpdatedState(onMoveUp)
    val currentOnMoveDown by rememberUpdatedState(onMoveDown)

    LaunchedEffect(isTargetFocus) {
        if (isTargetFocus) {
            kotlinx.coroutines.delay(40)
            try {
                bringIntoViewRequester.bringIntoView()
                focusRequester.requestFocus()
                onFocused()
            } catch (e: Exception) {
                kotlinx.coroutines.delay(80)
                runCatching {
                    bringIntoViewRequester.bringIntoView()
                    focusRequester.requestFocus()
                    onFocused()
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .offset { IntOffset(0, dragOffsetY.toInt()) }
            .zIndex(if (isDragging) 10f else 0f)
            .background(
                if (isDragging) primaryTextColor.copy(alpha = 0.08f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(vertical = 3.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag Indicator Handle with interactive vertical drag gestures
        Box(
            modifier = Modifier
                .size(36.dp)
                .pointerInput(item.id) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragOffsetY = 0f
                            try {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } catch (_: Exception) {}
                        },
                        onDragEnd = {
                            isDragging = false
                            dragOffsetY = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            dragOffsetY = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount

                            if (dragOffsetY > rowThresholdPx) {
                                currentOnMoveDown()
                                dragOffsetY -= rowThresholdPx
                                try {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } catch (_: Exception) {}
                            } else if (dragOffsetY < -rowThresholdPx) {
                                currentOnMoveUp()
                                dragOffsetY += rowThresholdPx
                                try {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } catch (_: Exception) {}
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = if (isDragging) Color(0xFF8AB4F8) else secondaryTextColor.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(2.dp))

        // Square Checkbox
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = if (item.isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (item.isChecked) "Completed" else "Incomplete",
                tint = if (item.isChecked) Color(0xFF8AB4F8) else secondaryTextColor.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Item text (Multi-line enabled so large text wraps)
        BasicTextField(
            value = item.text,
            onValueChange = { newTxt ->
                if (newTxt.contains('\n')) {
                    val split = newTxt.split('\n', limit = 2)
                    onTextChange(split[0])
                    val nextItemText = if (split.size > 1) split[1] else ""
                    onEnterPressed(nextItemText)
                } else {
                    onTextChange(newTxt)
                }
            },
            textStyle = TextStyle(
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = if (item.isChecked) secondaryTextColor.copy(alpha = 0.5f) else primaryTextColor,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null
            ),
            cursorBrush = SolidColor(Color(0xFF8AB4F8)),
            singleLine = false,
            maxLines = 20,
            decorationBox = { inner ->
                Box {
                    if (item.text.isEmpty()) {
                        Text(
                            text = "List item",
                            fontSize = 15.sp,
                            color = secondaryTextColor.copy(alpha = 0.4f)
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        if (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) {
                            onEnterPressed("")
                            true
                        } else if (keyEvent.key == Key.Backspace && item.text.isEmpty()) {
                            onDelete()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { onEnterPressed("") },
                onDone = { onEnterPressed("") }
            )
        )

        // Delete 'x' icon
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(26.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete item",
                tint = secondaryTextColor.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
