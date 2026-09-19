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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Google Keep style Compact Audio Player for note cards in the notes grid.
 * Rendered as a pill-shaped chip with play/pause circular button, subtle audio waveform icon,
 * and duration label. Blends cleanly into any note card background.
 */
@Composable
fun AudioPlayerCardCompact(
    audioUri: String,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val durationMs = remember(audioUri) {
        AudioMemoManager.getAudioDurationMs(audioUri)
    }
    val durationText = AudioMemoManager.formatDuration(durationMs)

    val transition = rememberInfiniteTransition(label = "wave")
    val waveScale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveScale"
    )

    Surface(
        shape = CircleShape,
        color = textColor.copy(alpha = 0.08f),
        border = BorderStroke(0.8.dp, textColor.copy(alpha = 0.18f)),
        contentColor = textColor,
        modifier = modifier
            .clip(CircleShape)
            .clickable { onTogglePlay() }
            .testTag("audio_card_compact")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
        ) {
            // Google Keep style play/pause round button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause voice memo" else "Play voice memo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(17.dp)
                )
            }

            // Google Keep style audio wave indicator
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = if (isPlaying) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )

            // Duration text
            Text(
                text = if (durationMs > 0) durationText else "Voice Memo",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    letterSpacing = 0.3.sp
                ),
                color = textColor.copy(alpha = 0.85f)
            )
        }
    }
}

/**
 * Pixel & Keep-inspired Audio Player item for the Note Editor.
 * Features:
 * - Rounded pill card matching note background with border outline
 * - Speed toggle (1.0x, 1.5x, 2.0x)
 * - Quick seek: -5s and +5s buttons
 * - Large circular Play/Pause button
 * - Precise seekable track with custom thumb
 * - Current position and total duration display
 * - Discreet Delete button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerEditorItem(
    audioUri: String,
    isPlaying: Boolean,
    currentPositionMs: Int,
    durationMs: Int,
    playbackSpeed: Float = 1.0f,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit = {},
    onSkip: (Int) -> Unit = {},
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val estimatedDuration = remember(audioUri) {
        if (durationMs > 0) durationMs.toLong() else AudioMemoManager.getAudioDurationMs(audioUri)
    }
    val actualDuration = if (durationMs > 0) durationMs.toLong() else estimatedDuration
    val currentPos = currentPositionMs.toLong()

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragProgressFraction by remember { mutableFloatStateOf(0f) }

    val displayProgress = if (isDraggingSlider) {
        dragProgressFraction
    } else if (actualDuration > 0) {
        (currentPos.toFloat() / actualDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val displayPosMs = if (isDraggingSlider) {
        (dragProgressFraction * actualDuration).toLong()
    } else {
        currentPos
    }

    val currentText = AudioMemoManager.formatDuration(displayPosMs)
    val totalText = AudioMemoManager.formatDuration(actualDuration)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = textColor.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.15f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_player_editor_item")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top status and secondary controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = if (isPlaying) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = "Voice Memo",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = textColor.copy(alpha = 0.85f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Playback Speed pill button (1.0x -> 1.5x -> 2.0x)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = textColor.copy(alpha = 0.08f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val nextSpeed = when (playbackSpeed) {
                                    1.0f -> 1.5f
                                    1.5f -> 2.0f
                                    else -> 1.0f
                                }
                                onSpeedChange(nextSpeed)
                            }
                            .testTag("audio_speed_btn")
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = textColor.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete voice memo",
                            tint = textColor.copy(alpha = 0.55f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Keep Style Track Slider
            Slider(
                value = displayProgress,
                onValueChange = { frac ->
                    isDraggingSlider = true
                    dragProgressFraction = frac
                },
                onValueChangeFinished = {
                    val targetMs = (dragProgressFraction * actualDuration).toInt()
                    onSeek(targetMs)
                    isDraggingSlider = false
                },
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(4.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = textColor.copy(alpha = 0.18f)
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            )

            // Timestamps and Playback Control Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Elapsed timestamp
                Text(
                    text = currentText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = textColor.copy(alpha = 0.65f)
                )

                // Playback Transport buttons: [-5s] [Play/Pause] [+5s]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Rewind 5s
                    IconButton(
                        onClick = { onSkip(-5000) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Replay5,
                            contentDescription = "Rewind 5 seconds",
                            tint = textColor.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Play/Pause circle
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTogglePlay() }
                            .testTag("audio_editor_play_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Forward 5s
                    IconButton(
                        onClick = { onSkip(5000) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Forward5,
                            contentDescription = "Forward 5 seconds",
                            tint = textColor.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Total duration timestamp
                Text(
                    text = totalText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = textColor.copy(alpha = 0.65f)
                )
            }
        }
    }
}
