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

package com.focusbyrj.app.ui.screens.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.focusbyrj.app.MainActivity
import com.focusbyrj.app.data.note.ChecklistItem
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.data.note.NoteImageHelper
import com.focusbyrj.app.ui.navigation.Screen
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import com.focusbyrj.app.widget.NoteWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class ShareToNoteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val parsed = KeepNoteShareParser.parseIntent(intent)

        setContent {
            FocusByRjTheme {
                ShareToNoteOverlay(
                    initialParsedNote = parsed,
                    onDismiss = { finish() },
                    onNoteSaved = { savedId, openInApp ->
                        if (openInApp) {
                            val mainIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra("navigate_to", Screen.Empty.route)
                                putExtra("open_note_id", savedId)
                            }
                            startActivity(mainIntent)
                        }
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun ShareToNoteOverlay(
    initialParsedNote: ParsedSharedNote,
    onDismiss: () -> Unit,
    onNoteSaved: (savedId: Long, openInApp: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(initialParsedNote.title) }
    var content by remember { mutableStateOf(initialParsedNote.content) }
    var isChecklist by remember { mutableStateOf(initialParsedNote.isChecklist) }
    val checklistItems = remember { mutableStateListOf<ChecklistItem>().apply { addAll(initialParsedNote.checklistItems) } }
    val imageUris = remember { mutableStateListOf<Uri>().apply { addAll(initialParsedNote.imageUris) } }

    var selectedColorKey by remember { mutableStateOf("default") }
    var showColorPicker by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val colorTheme = KeepColorPalette.getColor(selectedColorKey)
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val cardBg = colorTheme.resolveBackgroundColor(isDark)
    val textColor = colorTheme.resolveTextColor(isDark)
    val secondaryText = colorTheme.resolveSecondaryTextColor(isDark)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .imePadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* consume clicks */ }
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, colorTheme.resolveBorderColor(isDark)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (imageUris.isNotEmpty()) Icons.Filled.Image else Icons.Filled.FormatListBulleted,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Import to Focus Notes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                text = if (imageUris.isNotEmpty()) "Shared Note • ${imageUris.size} Image${if (imageUris.size > 1) "s" else ""}" else "Shared from Google Keep",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryText
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = secondaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Title field
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Note Title", color = secondaryText) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = textColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = secondaryText.copy(alpha = 0.2f)
                    )

                    // Attached Images Horizontal Carousel
                    if (imageUris.isNotEmpty()) {
                        Text(
                            text = "Attached Images (${imageUris.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryText,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(imageUris) { uri ->
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, secondaryText.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(uri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Shared Image",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    // Remove button on each image thumbnail
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.65f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(22.dp)
                                            .clickable { imageUris.remove(uri) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Remove image",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Checklist or Plain Note Content
                    if (isChecklist) {
                        Text(
                            text = "Checklist Items (${checklistItems.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryText,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            checklistItems.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            checklistItems[index] = item.copy(isChecked = !item.isChecked)
                                        }
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (item.isChecked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                        contentDescription = null,
                                        tint = if (item.isChecked) MaterialTheme.colorScheme.primary else secondaryText,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (item.isChecked) secondaryText else textColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            placeholder = { Text("Note content...", color = secondaryText) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 220.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    }

                    // Color Palette Selector
                    AnimatedVisibility(visible = showColorPicker) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text(
                                text = "Choose Note Color",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = secondaryText,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(KeepColorPalette.allColors) { paletteItem ->
                                    val isSelected = paletteItem.key.equals(selectedColorKey, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(paletteItem.swatchColor)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else secondaryText.copy(alpha = 0.3f),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                selectedColorKey = paletteItem.key
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected",
                                                tint = Color.Black.copy(alpha = 0.8f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = secondaryText.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Color Picker toggle button
                    IconButton(
                        onClick = { showColorPicker = !showColorPicker },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = "Change color",
                            tint = secondaryText
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            enabled = !isSaving
                        ) {
                            Text("Cancel", color = secondaryText)
                        }

                        // Save Note silently (stays in Keep)
                        Button(
                            onClick = {
                                if (isSaving) return@Button
                                isSaving = true
                                scope.launch(Dispatchers.IO) {
                                    val savedId = saveImportedNote(
                                        context = context,
                                        title = title,
                                        content = content,
                                        isChecklist = isChecklist,
                                        checklistItems = checklistItems,
                                        imageUris = imageUris,
                                        colorKey = selectedColorKey
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Note imported to Focus Notes ✅", Toast.LENGTH_SHORT).show()
                                        onNoteSaved(savedId, false)
                                    }
                                }
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save")
                            }
                        }

                        // Save and open in app
                        Button(
                            onClick = {
                                if (isSaving) return@Button
                                isSaving = true
                                scope.launch(Dispatchers.IO) {
                                    val savedId = saveImportedNote(
                                        context = context,
                                        title = title,
                                        content = content,
                                        isChecklist = isChecklist,
                                        checklistItems = checklistItems,
                                        imageUris = imageUris,
                                        colorKey = selectedColorKey
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Note imported to Focus Notes ✅", Toast.LENGTH_SHORT).show()
                                        onNoteSaved(savedId, true)
                                    }
                                }
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open")
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun saveImportedNote(
    context: Context,
    title: String,
    content: String,
    isChecklist: Boolean,
    checklistItems: List<ChecklistItem>,
    imageUris: List<Uri>,
    colorKey: String
): Long {
    // 1. Process & securely copy images into internal storage
    val localImagePaths = NoteImageHelper.processAndSaveMultipleImages(context, imageUris)
    val imagesJson = JSONArray().apply {
        localImagePaths.forEach { put(it) }
    }.toString()

    // 2. Format checklist JSON
    val checklistJson = if (isChecklist) ChecklistItem.listToJson(checklistItems) else "[]"

    // 3. Fallback title if completely empty
    val finalTitle = title.trim().ifBlank {
        if (isChecklist && checklistItems.isNotEmpty()) {
            checklistItems.first().text.take(30)
        } else if (content.isNotBlank()) {
            content.lines().firstOrNull { it.isNotBlank() }?.take(30) ?: "Imported Note"
        } else if (localImagePaths.isNotEmpty()) {
            "Photo Note"
        } else {
            "Imported Note"
        }
    }

    val noteEntity = NoteEntity(
        title = finalTitle,
        content = if (!isChecklist) content else "",
        isChecklist = isChecklist,
        checklistJson = checklistJson,
        colorKey = colorKey,
        isPinned = false,
        isArchived = false,
        isTrashed = false,
        labelsJson = "[]",
        imageUrisJson = imagesJson,
        audioUrisJson = "[]",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    val db = NoteDatabase.getInstance(context)
    val savedId = db.noteDao().insertNote(noteEntity)
    NotesViewModel.latestNotesCache[savedId] = noteEntity.copy(id = savedId)

    // Notify widgets
    NoteWidgetProvider.updateAllWidgets(context)

    return savedId
}
