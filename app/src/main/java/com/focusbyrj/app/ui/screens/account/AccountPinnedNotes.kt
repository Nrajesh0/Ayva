/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.*
import com.focusbyrj.app.ui.screens.notes.RichTextEngine
import java.util.*
import kotlin.math.abs
import kotlin.random.Random

import com.focusbyrj.app.ui.screens.*

private fun getCleanPreviewText(content: String): String {
    if (content.isBlank()) return "Note record in enclave storage..."
    val (parsedText, _) = try {
        RichTextEngine.parse(content)
    } catch (_: Exception) {
        content to emptyList()
    }
    val clean = parsedText
        .replace(Regex("<!--[\\s\\S]*?-->"), "")
        .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("\\*(.*?)\\*"), "$1")
        .replace(Regex("~~(.*?)~~"), "$1")
        .replace(Regex("`{1,3}(.*?)`{1,3}"), "$1")
        .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
        .replace(Regex("^[>\\-*+]\\s*", RegexOption.MULTILINE), "")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .trim()
    return if (clean.isBlank()) "Note record in enclave storage..." else clean
}

@Composable
internal fun EnclavePinnedNotesSection(
    colors: EnclaveColors,
    activeNotes: List<NoteEntity>,
    profilePinnedIds: Set<Long>,
    onNoteClick: (Long) -> Unit,
    onChoosePinnedClick: () -> Unit
) {
    val realPinned = remember(activeNotes, profilePinnedIds) {
        activeNotes.filter { profilePinnedIds.contains(it.id) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.GridView,
                    contentDescription = "Pinned Notes",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "PINNED NOTES",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = colors.textPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button to choose / pin notes
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.subCardBg,
                    border = BorderStroke(1.dp, colors.subCardBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onChoosePinnedClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pin notes",
                            tint = colors.accentCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Pin Notes",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textPrimary
                        )
                    }
                }

                Text(
                    text = "${realPinned.size} WORKS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = colors.textMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Pinned Notes List
        if (realPinned.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                realPinned.forEach { note ->
                    val uri = note.getImageUris().firstOrNull()
                    val timeAgo = formatTimeAgo(note.updatedAt)
                    val tags = remember(note) { note.getLabels() }
                    val cleanPreview = remember(note.content) { getCleanPreviewText(note.content) }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.cardBg,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onNoteClick(note.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Generative procedural / Image Thumbnail Preview
                            CyberNoteThumbnail(
                                colors = colors,
                                seed = note.id.toInt(),
                                imageUri = uri,
                                modifier = Modifier.size(62.dp)
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            // Note Info Column
                            Column(modifier = Modifier.weight(1f)) {
                                // Title & Time Ago
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = note.title.ifBlank { "Untitled Note" },
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        ),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Updated $timeAgo",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = colors.textMuted,
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                // Content Description (Cleaned of raw markdown and Notesnook block markup)
                                Text(
                                    text = cleanPreview,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (tags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    // Tags row - Only rendered if user actually assigned labels
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        tags.take(3).forEach { tag ->
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = colors.subCardBg,
                                                border = BorderStroke(1.dp, colors.subCardBorder)
                                            ) {
                                                Text(
                                                    text = tag.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.5.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    color = colors.textSecondary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Empty Pinned Notes Card with Call to Action
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.cardBg,
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onChoosePinnedClick() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = colors.accentCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No Notes Currently Pinned",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pin quick reference notes, protocols, or journals from your Notes library.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onChoosePinnedClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentPrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Select Notes to Pin", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}

/**
 * Procedural generative spatial canvas thumbnail
 */
@Composable
private fun CyberNoteThumbnail(
    colors: EnclaveColors,
    seed: Int,
    imageUri: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.subCardBg)
            .border(1.dp, colors.subCardBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUri.isNullOrBlank()) {
            AsyncImage(
                model = if (imageUri.startsWith("/")) com.focusbyrj.app.util.crypto.NoteThumbnail(java.io.File(imageUri)) else imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val strokeColor = colors.textMuted.copy(alpha = 0.4f)
            val dotColor = colors.textPrimary.copy(alpha = 0.8f)
            val accentDot = colors.accentCyan

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val rnd = Random(seed.toLong())

                // Technical grid lines
                val step = w / 4f
                for (i in 0..4) {
                    val x = i * step
                    drawLine(color = strokeColor.copy(alpha = 0.2f), start = Offset(x, 0f), end = Offset(x, h), strokeWidth = 1f)
                    drawLine(color = strokeColor.copy(alpha = 0.2f), start = Offset(0f, x), end = Offset(w, x), strokeWidth = 1f)
                }

                // Generative procedural geometric shapes
                when (abs(seed) % 3) {
                    0 -> {
                        for (i in 0..8) {
                            val cx = rnd.nextFloat() * (w - 12f) + 6f
                            val cy = rnd.nextFloat() * (h - 12f) + 6f
                            val radius = rnd.nextFloat() * 3.5f + 1.5f
                            drawCircle(color = dotColor, center = Offset(cx, cy), radius = radius)
                        }
                        drawLine(color = strokeColor, start = Offset(w * 0.2f, h * 0.75f), end = Offset(w * 0.8f, h * 0.35f), strokeWidth = 1.2f)
                    }
                    1 -> {
                        drawCircle(color = strokeColor, center = Offset(w * 0.5f, h * 0.5f), radius = w * 0.35f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                        drawCircle(color = accentDot, center = Offset(w * 0.5f, h * 0.5f), radius = 3.5f)
                    }
                    else -> {
                        val path = Path().apply {
                            moveTo(w * 0.15f, h * 0.6f)
                            lineTo(w * 0.45f, h * 0.6f)
                            lineTo(w * 0.65f, h * 0.3f)
                            lineTo(w * 0.85f, h * 0.3f)
                        }
                        drawPath(path = path, color = dotColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f))
                        drawCircle(color = accentDot, center = Offset(w * 0.85f, h * 0.3f), radius = 3.5f)
                    }
                }
            }
        }
    }
}

// D. SYSTEM PREFERENCES COMPONENT

@Composable
internal fun ChoosePinnedNotesDialog(
    colors: EnclaveColors,
    activeNotes: List<NoteEntity>,
    pinnedNoteIds: Set<Long>,
    onTogglePin: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredNotes = remember(activeNotes, searchQuery) {
        if (searchQuery.isBlank()) activeNotes
        else activeNotes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.content.contains(searchQuery, ignoreCase = true)
        }
    }
    val pinnedCount = activeNotes.count { pinnedNoteIds.contains(it.id) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PINNED NOTES",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textPrimary
                        )
                        Text(
                            text = "$pinnedCount notes currently pinned to Identity",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = colors.textMuted
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = colors.textMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter notes...", fontSize = 13.sp, color = colors.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.accentCyan,
                        unfocusedBorderColor = colors.subCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Notes List with checkboxes
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredNotes.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (activeNotes.isEmpty()) "No active notes in your library." else "No notes match filter.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                            }
                        }
                    } else {
                        items(filteredNotes, key = { it.id }) { note ->
                            val isPinnedInProfile = pinnedNoteIds.contains(note.id)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPinnedInProfile) colors.accentPrimary.copy(alpha = 0.1f) else colors.subCardBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (isPinnedInProfile) colors.accentCyan.copy(alpha = 0.5f) else colors.subCardBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onTogglePin(note.id, !isPinnedInProfile) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isPinnedInProfile,
                                        onCheckedChange = { onTogglePin(note.id, it) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = colors.accentCyan,
                                            checkmarkColor = colors.bg
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = note.title.ifBlank { "Untitled Note" },
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val cleanSnippet = remember(note.content) { getCleanPreviewText(note.content) }
                                        Text(
                                            text = cleanSnippet,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = colors.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// D. Share Profile Card Modal
private fun formatTimeAgo(timeMs: Long): String {
    val diff = System.currentTimeMillis() - timeMs
    val mins = diff / (60 * 1000L)
    if (mins < 1) return "Just now"
    if (mins < 60) return "${mins}m ago"
    val hours = mins / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    return "${days}d ago"
}
