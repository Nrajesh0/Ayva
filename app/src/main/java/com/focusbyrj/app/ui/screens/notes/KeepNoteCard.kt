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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import com.focusbyrj.app.data.note.NoteEntity

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun KeepNoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onTogglePin: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    isDragging: Boolean = false,
    dragOffset: Offset = Offset.Zero,
    onStartDrag: ((Offset) -> Unit)? = null,
    onDrag: ((Offset) -> Unit)? = null,
    onEndDrag: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    onDeletePermanently: (() -> Unit)? = null,
    onUnarchive: (() -> Unit)? = null,
    activePlayingAudioPath: String? = null,
    isAudioPlaying: Boolean = false,
    onToggleAudioPlay: ((String) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()
    val theme = KeepColorPalette.getColor(note.colorKey)
    val fontStyle = KeepFontPalette.getFont(note.fontKey)
    val cardBg = theme.resolveBackgroundColor(isDark)
    val baseBorderColor = theme.resolveBorderColor(isDark)
    val isBeingDragged = isDragging
    val cardBorder = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else if (isBeingDragged) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
    } else {
        BorderStroke(1.dp, baseBorderColor)
    }
    val textColor = theme.resolveTextColor(isDark)

    val currentIsSelectionMode by rememberUpdatedState(isSelectionMode)
    val currentIsSelected by rememberUpdatedState(isSelected)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnToggleSelect by rememberUpdatedState(onToggleSelect)
    val currentOnStartDrag by rememberUpdatedState(onStartDrag)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnEndDrag by rememberUpdatedState(onEndDrag)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    val animatedElevation by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isBeingDragged) 12.dp else if (isSelected) 3.dp else if (note.colorKey == "default") 1.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "cardElevation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isBeingDragged) 100f else if (isSelected) 2f else 1f)
            .graphicsLayer {
                if (isBeingDragged) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    shadowElevation = 14.dp.toPx()
                }
            }
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(note.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    var isLongPress = false
                    val upOrCancel = try {
                        withTimeout(longPressTimeout) {
                            waitForUpOrCancellation()
                        }
                    } catch (e: Exception) {
                        isLongPress = true
                        null
                    }

                    if (!isLongPress && upOrCancel != null) {
                        upOrCancel.consume()
                        if (currentIsSelectionMode) {
                            currentOnToggleSelect()
                        } else {
                            currentOnClick()
                        }
                    } else if (isLongPress) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (currentIsSelectionMode) {
                            if (!currentIsSelected) {
                                currentOnToggleSelect()
                            }
                        }
                        currentOnLongClick?.invoke()
                        currentOnStartDrag?.invoke(down.position)
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val dragAmount = change.positionChange()
                            if (dragAmount != Offset.Zero) {
                                change.consume()
                                currentOnDrag?.invoke(dragAmount)
                            }
                        }
                        currentOnEndDrag?.invoke()
                    }
                }
            }
            .testTag("note_card_${note.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(
            defaultElevation = animatedElevation,
            pressedElevation = 2.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Google Keep style background decorative illustration motif
            if (theme.isIllustratedTheme) {
                KeepThemeIllustration(
                    themeType = theme.themeType,
                    isDark = isDark,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(width = 100.dp, height = 85.dp)
                        .padding(end = 4.dp, bottom = 4.dp)
                )
            }

            // Selection subtle colored highlight overlay (replaces checkmarks)
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.16f else 0.08f))
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
            val images = note.getImageUris()
            if (images.isNotEmpty()) {
                KeepCardImageCollage(imageUris = images)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
            // Top Row: Title + Pin Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (note.title.isNotBlank()) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontFamily = fontStyle.fontFamily
                        ),
                        color = textColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (note.isPinned && onTogglePin != null) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(start = 4.dp)
                            .testTag("note_pin_${note.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Unpin note",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Body: Plain text or Checklist items
            if (note.isChecklist) {
                val rawItems = note.getChecklistItems()
                val (uncompleted, completed) = rawItems.partition { !it.isChecked }
                val items = uncompleted + completed
                val visibleItems = items.take(5)
                val remainingCount = items.size - visibleItems.size

                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        visibleItems.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = if (item.isChecked) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (item.isChecked) {
                                        textColor.copy(alpha = 0.4f)
                                    } else {
                                        textColor.copy(alpha = 0.7f)
                                    },
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = item.text.ifBlank { "List item" },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.5.sp,
                                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                                        fontFamily = fontStyle.fontFamily
                                    ),
                                    color = if (item.isChecked) textColor.copy(alpha = 0.45f) else textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (remainingCount > 0) {
                            Text(
                                text = "+ $remainingCount more items",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    fontFamily = fontStyle.fontFamily
                                ),
                                color = textColor.copy(alpha = 0.55f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            } else if (note.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                val primaryColor = MaterialTheme.colorScheme.primary
                val formattedContent = remember(note.content, textColor, primaryColor, isDark) {
                    val parsed = RichTextEngine.parse(note.content)
                    RichTextEngine.toAnnotatedString(
                        plainText = parsed.first,
                        spans = parsed.second,
                        textColor = textColor.copy(alpha = 0.85f),
                        accentColor = primaryColor,
                        isDark = isDark,
                        baseFontSizeSp = 14f
                    )
                }
                Text(
                    text = formattedContent,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontFamily = fontStyle.fontFamily
                    ),
                    color = textColor.copy(alpha = 0.85f),
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Audio Memos
            val audioUris = note.getAudioUris()
            if (audioUris.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    audioUris.forEach { uri ->
                        val isThisPlaying = isAudioPlaying && activePlayingAudioPath == uri
                        AudioPlayerCardCompact(
                            audioUri = uri,
                            isPlaying = isThisPlaying,
                            onTogglePlay = { onToggleAudioPlay?.invoke(uri) },
                            textColor = textColor
                        )
                    }
                }
            }

            // Labels Badges
            val labels = note.getLabels()
            if (labels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    labels.forEach { label ->
                        Surface(
                            shape = CircleShape,
                            color = textColor.copy(alpha = 0.08f),
                            border = BorderStroke(0.6.dp, textColor.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = textColor.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Quick actions for Trash or Archive
            if (onRestore != null || onDeletePermanently != null || onUnarchive != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onUnarchive != null) {
                        IconButton(
                            onClick = onUnarchive,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Unarchive,
                                contentDescription = "Unarchive",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    if (onRestore != null) {
                        IconButton(
                            onClick = onRestore,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Restore,
                                contentDescription = "Restore",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    if (onDeletePermanently != null) {
                        IconButton(
                            onClick = onDeletePermanently,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteForever,
                                contentDescription = "Delete permanently",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(17.dp)
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
