/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.notes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import kotlinx.coroutines.delay
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun KeepOverflowGridItem(
    icon: ImageVector,
    label: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isActive) {
                        if (isDark) Color(0xFF2C323D) else Color(0xFFE0E7FF)
                    } else {
                        if (isDark) Color(0xFF212328) else Color(0xFFF1F3F5)
                    }
                )
                .border(
                    1.dp,
                    if (isActive) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    } else {
                        if (isDark) Color(0xFF2B2E35) else Color(0xFFE2E4E9)
                    },
                    RoundedCornerShape(12.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label.replace("\n", " "),
                tint = tint ?: if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    if (isDark) Color(0xFFC7CAD2) else Color(0xFF374151)
                },
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                if (isDark) Color(0xFFA0A3AD) else Color(0xFF4B5563)
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun KeepAddOptionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChecklistRow(
    item: com.focusbyrj.app.data.note.ChecklistItem,
    textColor: Color,
    fontFamily: FontFamily = FontFamily.Default,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isTargetFocus: Boolean,
    onFocused: () -> Unit,
    onToggle: () -> Unit,
    onTextChange: (String) -> Unit,
    onEnterPressed: (String) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val textFieldBringIntoViewRequester = remember { BringIntoViewRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val rowThresholdPx = remember(density) { with(density) { 44.dp.toPx() } }

    val currentCanMoveUp by rememberUpdatedState(canMoveUp)
    val currentCanMoveDown by rememberUpdatedState(canMoveDown)
    val currentOnMoveUp by rememberUpdatedState(onMoveUp)
    val currentOnMoveDown by rememberUpdatedState(onMoveDown)

    LaunchedEffect(isTargetFocus) {
        if (isTargetFocus) {
            bringIntoViewRequester.bringIntoView()
            textFieldBringIntoViewRequester.bringIntoView()
            kotlinx.coroutines.delay(40)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
                onFocused()
            } catch (e: Exception) {
                kotlinx.coroutines.delay(80)
                runCatching {
                    bringIntoViewRequester.bringIntoView()
                    textFieldBringIntoViewRequester.bringIntoView()
                    focusRequester.requestFocus()
                    keyboardController?.show()
                    onFocused()
                }
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .zIndex(if (isDragging) 10f else 1f)
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .background(
                color = if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(vertical = 2.dp)
    ) {
        // Drag Handle with Touch / Pointer Drag Gesture
        Box(
            modifier = Modifier
                .size(34.dp)
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
                                if (currentCanMoveDown) {
                                    currentOnMoveDown()
                                    dragOffsetY -= rowThresholdPx
                                    try {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } catch (_: Exception) {}
                                }
                            } else if (dragOffsetY < -rowThresholdPx) {
                                if (currentCanMoveUp) {
                                    currentOnMoveUp()
                                    dragOffsetY += rowThresholdPx
                                    try {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = if (isDragging) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }

        // Square Checkbox
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (item.isChecked) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (item.isChecked) "Completed" else "Incomplete",
                tint = if (item.isChecked) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Checklist Text Field (Multi-line wrapping with full content display)
        BasicTextField(
            value = item.text,
            onValueChange = { newText ->
                if (newText.contains('\n')) {
                    val split = newText.split('\n', limit = 2)
                    onTextChange(split[0])
                    val nextItemText = if (split.size > 1) split[1] else ""
                    onEnterPressed(nextItemText)
                } else {
                    onTextChange(newText)
                }
                coroutineScope.launch {
                    textFieldBringIntoViewRequester.bringIntoView()
                }
            },
            singleLine = false,
            maxLines = 20,
            modifier = Modifier
                .weight(1f)
                .bringIntoViewRequester(textFieldBringIntoViewRequester)
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
            textStyle = TextStyle(
                color = if (item.isChecked) textColor.copy(alpha = 0.45f) else textColor,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                fontFamily = fontFamily
            ),
            cursorBrush = SolidColor(textColor),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { onEnterPressed("") },
                onDone = { onEnterPressed("") }
            ),
            decorationBox = { innerTextField ->
                if (item.text.isEmpty()) {
                    Text(
                        text = "List item",
                        style = TextStyle(
                            color = textColor.copy(alpha = 0.35f),
                            fontSize = 16.sp,
                            fontFamily = fontFamily
                        )
                    )
                }
                innerTextField()
            }
        )

        // Delete 'x' icon
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Delete item",
                tint = textColor.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
