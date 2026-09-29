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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

@Composable
fun NotesSpeedDialFab(
    onNewTextNote: () -> Unit,
    onNewChecklist: () -> Unit,
    onNewDrawing: () -> Unit,
    onNewImage: () -> Unit,
    onNewAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Close on system back when speed dial is open
    BackHandler(enabled = isExpanded) {
        isExpanded = false
    }

    // Icon rotation: 0 deg (+) to 135 deg (x)
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 135f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "fab_rotation"
    )

    Box(modifier = modifier) {
        // Immersive modal Scrim background when expanded (Google Keep style)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.zIndex(10f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isExpanded = false
                    }
            )
        }

        // Floating Action Buttons (Aligned to bottom-end, elevated above scrim)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp)
                .zIndex(20f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Speed Dial Options (Google Keep Pill Menu)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(180)) + slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                ),
                exit = fadeOut(tween(140)) + slideOutVertically(
                    targetOffsetY = { it / 3 },
                    animationSpec = tween(140)
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Drawing
                    SpeedDialOption(
                        icon = Icons.Outlined.Brush,
                        label = "Drawing",
                        testTag = "notes_fab_drawing",
                        isDark = isDark,
                        onClick = {
                            isExpanded = false
                            onNewDrawing()
                        }
                    )

                    // Image
                    SpeedDialOption(
                        icon = Icons.Outlined.Image,
                        label = "Image",
                        testTag = "notes_fab_image",
                        isDark = isDark,
                        onClick = {
                            isExpanded = false
                            onNewImage()
                        }
                    )

                    // Live transcription (renamed from Audio as requested)
                    SpeedDialOption(
                        icon = Icons.Outlined.Mic,
                        label = "Live transcription",
                        testTag = "notes_fab_audio",
                        isDark = isDark,
                        onClick = {
                            isExpanded = false
                            onNewAudio()
                        }
                    )

                    // Checklist
                    SpeedDialOption(
                        icon = Icons.Outlined.CheckBox,
                        label = "Checklist",
                        testTag = "notes_fab_checklist",
                        isDark = isDark,
                        onClick = {
                            isExpanded = false
                            onNewChecklist()
                        }
                    )

                    // Text Note
                    SpeedDialOption(
                        icon = Icons.Outlined.Description,
                        label = "Note",
                        testTag = "notes_fab_text_note",
                        isDark = isDark,
                        onClick = {
                            isExpanded = false
                            onNewTextNote()
                        }
                    )
                }
            }

            // Main Primary FAB Button (matches active app theme, Google Keep style)
            FloatingActionButton(
                onClick = { isExpanded = !isExpanded },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 10.dp
                ),
                modifier = Modifier
                    .size(56.dp)
                    .testTag("notes_main_fab")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = if (isExpanded) "Close note options" else "Add note",
                    modifier = Modifier
                        .size(26.dp)
                        .rotate(rotationAngle)
                )
            }
        }
    }
}

@Composable
private fun SpeedDialOption(
    icon: ImageVector,
    label: String,
    testTag: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isDark) Color(0xFF25262A) else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) Color(0xFF3E4046) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .testTag(testTag)
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = if (isDark) Color.White.copy(alpha = 0.95f) else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
