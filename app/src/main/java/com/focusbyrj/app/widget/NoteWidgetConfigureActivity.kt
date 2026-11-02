/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.widget

import com.focusbyrj.app.ui.security.installScreenCaptureProtection

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.screens.notes.KeepColorPalette
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NoteWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installScreenCaptureProtection()
        setResult(RESULT_CANCELED)

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(this, NoteWidgetProvider::class.java))
            appWidgetId = if (ids != null && ids.isNotEmpty()) ids[0] else 0
        }

        val initialConfig = NoteWidgetConfigHelper.getConfig(this, appWidgetId)

        setContent {
            FocusByRjTheme {
                NoteWidgetConfigScreen(
                    initialConfig = initialConfig,
                    onSave = { newConfig ->
                        NoteWidgetConfigHelper.saveConfig(this, appWidgetId, newConfig)
                        val appWidgetManager = AppWidgetManager.getInstance(this)
                        // Only update the specific widget being configured if a valid ID was supplied.
                        // If no specific ID exists (appWidgetId == 0), update all note widgets.
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && appWidgetId != 0) {
                            NoteWidgetProvider.updateWidget(this, appWidgetManager, appWidgetId)
                        } else {
                            NoteWidgetProvider.updateAllWidgets(this)
                        }

                        val resultValue = Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        setResult(Activity.RESULT_OK, resultValue)
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}

@Composable
fun NoteWidgetConfigScreen(
    initialConfig: NoteWidgetConfig,
    onSave: (NoteWidgetConfig) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val noteDao = remember { NoteDatabase.getInstance(context).noteDao() }
    var theme by rememberSaveable { mutableStateOf(initialConfig.theme) }
    var accent by rememberSaveable { mutableStateOf(initialConfig.accent) }
    var opacity by rememberSaveable { mutableIntStateOf(initialConfig.opacityPercent) }
    var radius by rememberSaveable { mutableIntStateOf(initialConfig.cornerRadiusDp) }
    var matchNoteColor by rememberSaveable { mutableStateOf(initialConfig.matchNoteColor) }
    var filter by rememberSaveable { mutableStateOf(initialConfig.filterMode) }
    var noteId by rememberSaveable { mutableStateOf(initialConfig.specificNoteId) }
    var sort by rememberSaveable { mutableStateOf(initialConfig.sortBy) }
    var textSize by rememberSaveable { mutableStateOf(initialConfig.textSize) }
    var padding by rememberSaveable { mutableStateOf(initialConfig.padding) }
    var quickAdd by rememberSaveable { mutableStateOf(initialConfig.showQuickAddBar) }
    var actions by rememberSaveable { mutableStateOf(initialConfig.showActionButtons) }
    var navigation by rememberSaveable { mutableStateOf(initialConfig.showNavHeader) }
    var title by rememberSaveable { mutableStateOf(initialConfig.showTitle) }
    var adaptive by rememberSaveable { mutableStateOf(initialConfig.adaptiveLayout) }
    var notes by remember { mutableStateOf<List<NoteEntity>>(emptyList()) }
    LaunchedEffect(noteDao) { notes = withContext(Dispatchers.IO) { noteDao.getAllActiveNotesSync() } }
    val notesById = remember(notes) { notes.associateBy { it.id } }
    val selectedNote = notesById[noteId]
    val draft = NoteWidgetConfig(theme, accent, opacity, radius, matchNoteColor, filter, noteId,
        sort, textSize, padding, title, quickAdd, actions, navigation, adaptive)
    WidgetEditor("Notes widget", listOf("Content", "Style", "Layout"), "${filter.displayName} · ${textSize.displayName}",
        onSave = { onSave(draft) }, onCancel = onCancel,
        preview = { NoteWidgetLivePreview(draft, selectedNote ?: notes.firstOrNull()) }) { section ->
        when (section) {
            0 -> {
                WidgetChoiceRow("Show", filter, NoteWidgetFilterMode.entries, { it.displayName }, {
                    filter = it
                    if (it != NoteWidgetFilterMode.SPECIFIC) noteId = null
                    else if (noteId == null) noteId = notes.firstOrNull()?.id
                })
                if (filter == NoteWidgetFilterMode.SPECIFIC) {
                    WidgetChoiceRow("Selected note", noteId, notes.map { it.id },
                        { id -> notesById[id]?.title?.ifBlank { "Untitled note" } ?: "Choose a note" },
                        { noteId = it },
                        detail = { id -> notesById[id]?.let {
                            if (it.isChecklist) "${it.getChecklistItems().size} items" else it.content.take(50).replace('\n', ' ')
                        } },
                        selectedLabel = selectedNote?.title?.ifBlank { "Untitled note" }
                            ?: if (notes.isEmpty()) "No notes available" else "Choose a note")
                }
                WidgetChoiceRow("Sort order", sort, NoteWidgetSortBy.entries, { it.displayName }, { sort = it })
            }
            1 -> {
                WidgetChoiceRow("Theme", theme, WidgetTheme.entries, { it.displayName }, { theme = it },
                    swatch = { Color(android.graphics.Color.parseColor(it.baseColorHex)) })
                WidgetChoiceRow("Accent", accent, WidgetAccent.entries, { it.displayName }, { accent = it },
                    swatch = { Color(android.graphics.Color.parseColor(it.hex)) })
                WidgetToggleRow("Match note color", matchNoteColor) { matchNoteColor = it }
                WidgetSliderRow("Opacity", opacity, "%", 20..100) { opacity = it }
                WidgetSliderRow("Corner radius", radius, " dp", 0..28) { radius = it }
            }
            2 -> {
                WidgetChoiceRow("Font size", textSize, NoteWidgetTextSize.entries, { it.displayName }, { textSize = it })
                WidgetChoiceRow("Content padding", padding, NoteWidgetPadding.entries, { it.displayName }, { padding = it })
                WidgetToggleRow("Note title", title) { title = it }
                WidgetToggleRow("Quick-edit bar", quickAdd) { quickAdd = it }
                WidgetToggleRow("Voice & new note buttons", actions) { actions = it }
                WidgetToggleRow("Navigation & page controls", navigation) { navigation = it }
                WidgetToggleRow("Adaptive layout", adaptive) { adaptive = it }
            }
        }
    }
}

@Composable
fun NoteWidgetLivePreview(
    config: NoteWidgetConfig,
    sampleNote: NoteEntity?
) {
    val isSystemDark = MaterialTheme.colorScheme.background.let {
        (it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.5f
    }

    val noteColorTheme = if (config.matchNoteColor && sampleNote?.colorKey != null && sampleNote.colorKey != "default") {
        KeepColorPalette.getColor(sampleNote.colorKey)
    } else {
        null
    }

    val baseColor = if (noteColorTheme != null) {
        noteColorTheme.getBackgroundColor(isSystemDark)
    } else {
        Color(android.graphics.Color.parseColor(config.theme.baseColorHex))
    }

    val alpha = (config.opacityPercent / 100f).coerceIn(0.2f, 1f)
    val bgColor = baseColor.copy(alpha = alpha)

    val borderColor = if (config.theme.isDark) {
        if (config.theme == WidgetTheme.OLED) Color(0xFF242729) else Color(0xFF2E3338)
    } else {
        Color(0xFFD6D9DC)
    }

    val primaryTextColor = if (noteColorTheme != null) {
        noteColorTheme.getTextColor(isSystemDark)
    } else if (config.theme.isDark) Color(0xFFF0F2F5) else Color(0xFF1F1F24)

    val secondaryTextColor = primaryTextColor.copy(alpha = 0.65f)
    val accentColor = Color(config.accentColorInt)

    Surface(
        shape = RoundedCornerShape(config.cornerRadiusDp.dp),
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .height(185.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (sampleNote?.isChecklist == true) Icons.Default.CheckBox else Icons.Default.Notes,
                        contentDescription = null,
                        tint = primaryTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    if (config.showTitle) {
                        Text(
                            text = sampleNote?.title?.ifBlank { "Project Roadmap" } ?: "Project Roadmap",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = (config.textSize.spValue + 2f).sp
                            ),
                            color = primaryTextColor,
                            maxLines = 1
                        )
                    }
                    if (sampleNote?.isPinned == true) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Header Controls (Pill & Settings)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (config.showNavHeader) {
                        // Carousel Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = primaryTextColor.copy(alpha = 0.08f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "1/3",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = secondaryTextColor
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = config.filterMode.shortLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = secondaryTextColor
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Body Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) {
                if (sampleNote?.isChecklist == true) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckBox,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Review Q3 product sprint milestones",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = config.textSize.spValue.sp
                                ),
                                color = primaryTextColor,
                                maxLines = 1
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = secondaryTextColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Sync with engineering team on widget design",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = config.textSize.spValue.sp
                                ),
                                color = primaryTextColor,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    Text(
                        text = sampleNote?.content?.ifBlank { "Deep work sessions planned for this week. Focus on core architecture." }
                            ?: "Deep work sessions planned for this week. Focus on core architecture.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = config.textSize.spValue.sp,
                            lineHeight = (config.textSize.spValue * 1.35f).sp
                        ),
                        color = primaryTextColor,
                        maxLines = 3
                    )
                }
            }

            // Bottom Micro-Action Bar
            if (config.showQuickAddBar) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Tap to edit note pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = primaryTextColor.copy(alpha = 0.08f),
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Tap to edit note directly...",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = secondaryTextColor,
                                maxLines = 1
                            )
                        }
                    }

                    if (config.showActionButtons) {
                        // Mic Icon Circle
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(primaryTextColor.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = primaryTextColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // New Note Circle
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(primaryTextColor.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notes,
                                contentDescription = null,
                                tint = primaryTextColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
