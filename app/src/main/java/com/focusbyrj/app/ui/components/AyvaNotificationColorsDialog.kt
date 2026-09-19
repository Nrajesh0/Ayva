package com.focusbyrj.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusbyrj.app.util.AyvaAlertCategory
import com.focusbyrj.app.util.AyvaColorPresets

/**
 * Minimal, compact, and organized dialog for configuring Ayva alert category toggles
 * and aesthetic jewel color themes via sleek dropdown selectors.
 */
@Composable
fun AyvaNotificationColorsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = 580.dp)
                .clip(RoundedCornerShape(22.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Notification Themes",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Alert delivery & jewel colors",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(6.dp))

                // Organized Category List
                key(refreshKey) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(AyvaAlertCategory.entries, key = { _, it -> it.categoryKey }) { index, category ->
                            CategoryCompactRow(
                                category = category,
                                onStateChanged = { refreshKey++ }
                            )

                            if (index < AyvaAlertCategory.entries.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(start = 44.dp, end = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            AyvaAlertCategory.entries.forEach { it.resetToDefault(context) }
                            refreshKey++
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset All",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean, single-line compact category configuration row.
 */
@Composable
private fun CategoryCompactRow(
    category: AyvaAlertCategory,
    onStateChanged: () -> Unit
) {
    val context = LocalContext.current
    val isEnabled = category.isEnabled(context)
    val currentBgHex = category.getBgHex(context)

    val currentPreset = remember(currentBgHex) {
        AyvaColorPresets.getPresetByBgHex(currentBgHex)
            ?: AyvaColorPresets.PRESETS.firstOrNull { it.bgHex.equals(category.defaultBgHex, ignoreCase = true) }
            ?: AyvaColorPresets.PRESETS.first()
    }

    val activeColor = remember(currentPreset.bgHex) {
        try {
            Color(android.graphics.Color.parseColor(currentPreset.bgHex))
        } catch (_: Exception) {
            Color(android.graphics.Color.parseColor(category.defaultBgHex))
        }
    }

    var dropdownExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category Icon Badge
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    if (isEnabled) activeColor.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getCategoryIcon(category),
                contentDescription = null,
                tint = if (isEnabled) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Title and Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.channelName.removePrefix("Ayva "),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
            Text(
                text = getCategorySubtitle(category),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isEnabled) 0.7f else 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Dropdown Color Selector Pill
        Box {
            Surface(
                onClick = { if (isEnabled) dropdownExpanded = true },
                enabled = isEnabled,
                shape = RoundedCornerShape(8.dp),
                color = if (isEnabled) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                },
                border = BorderStroke(
                    1.dp,
                    if (isEnabled) activeColor.copy(alpha = 0.35f) else Color.Transparent
                ),
                modifier = Modifier.height(28.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isEnabled) activeColor else Color.Gray.copy(alpha = 0.3f))
                    )

                    Text(
                        text = currentPreset.name.split(" ").firstOrNull() ?: currentPreset.name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )

                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Select Color",
                        tint = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Dropdown Menu for Theme Presets
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .widthIn(min = 160.dp)
            ) {
                AyvaColorPresets.PRESETS.forEach { preset ->
                    val isSelected = preset.bgHex.equals(currentBgHex, ignoreCase = true)
                    val presetColor = try {
                        Color(android.graphics.Color.parseColor(preset.bgHex))
                    } catch (_: Exception) { Color.Gray }

                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(presetColor)
                                )
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        } else null,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp),
                        onClick = {
                            category.setCustomColors(context, preset)
                            dropdownExpanded = false
                            onStateChanged()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Toggle Switch
        Switch(
            checked = isEnabled,
            onCheckedChange = { checked ->
                category.setEnabled(context, checked)
                onStateChanged()
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = activeColor
            ),
            modifier = Modifier.scale(0.72f)
        )
    }
}

private fun getCategoryIcon(category: AyvaAlertCategory): ImageVector {
    return when (category) {
        AyvaAlertCategory.BRIEFING -> Icons.Filled.WbSunny
        AyvaAlertCategory.DRILL_PRACTICE -> Icons.Filled.Psychology
        AyvaAlertCategory.EXCESSIVE_USAGE -> Icons.Filled.HourglassBottom
        AyvaAlertCategory.BEDTIME_SLEEP -> Icons.Filled.Bedtime
        AyvaAlertCategory.CRITICAL_LIMIT -> Icons.Filled.Block
        AyvaAlertCategory.GENERAL -> Icons.Filled.ChatBubble
    }
}

private fun getCategorySubtitle(category: AyvaAlertCategory): String {
    return when (category) {
        AyvaAlertCategory.BRIEFING -> "Morning & evening digests"
        AyvaAlertCategory.DRILL_PRACTICE -> "Flashcards & math challenges"
        AyvaAlertCategory.EXCESSIVE_USAGE -> "Continuous screen nudges"
        AyvaAlertCategory.BEDTIME_SLEEP -> "Late-night & wind-down rest"
        AyvaAlertCategory.CRITICAL_LIMIT -> "Strict locks & app limits"
        AyvaAlertCategory.GENERAL -> "General assistant alerts"
    }
}
