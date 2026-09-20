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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.LineWeight
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class LineSpacingPreset(val label: String, val leadingFactor: Float, val lineHeightSp: Float) {
    COMPACT("Compact", 1.2f, 20f),
    COMFORTABLE("Comfortable", 1.5f, 26f),
    SPACIOUS("Spacious", 1.8f, 32f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleEditorialBottomSheet(
    currentLineSpacing: LineSpacingPreset,
    onSelectLineSpacing: (LineSpacingPreset) -> Unit,
    onInsertPullQuote: () -> Unit,
    onInsertFootnote: () -> Unit,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val containerBg = if (isDark) Color(0xFF14161B) else MaterialTheme.colorScheme.surfaceContainerHigh
    val cardBg = if (isDark) Color(0xFF1E222A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val textColor = if (isDark) Color.White else Color(0xFF1A1C20)
    val subTextColor = if (isDark) Color(0xFF9AA0A6) else Color(0xFF5F6368)
    val accentColor = Color(0xFF22C55E)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = containerBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("article_editorial_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FormatLineSpacing,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Editorial & Typography",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                        Text(
                            text = "Reading line height & publishing elements",
                            style = MaterialTheme.typography.bodySmall,
                            color = subTextColor
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = subTextColor)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Line Spacing Presets
            Text(
                text = "LINE SPACING (READING LEADING)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                color = subTextColor
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LineSpacingPreset.values().forEach { preset ->
                    val isSelected = currentLineSpacing == preset
                    Surface(
                        color = if (isSelected) accentColor.copy(alpha = 0.15f) else cardBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) accentColor else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectLineSpacing(preset) }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) accentColor else textColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${preset.leadingFactor}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) accentColor else subTextColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = subTextColor.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(16.dp))

            // Article Elements
            Text(
                text = "ARTICLE ELEMENTS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                color = subTextColor
            )
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = cardBg,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    // Pull Quote option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onInsertPullQuote()
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Outlined.FormatQuote, contentDescription = null, tint = accentColor)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Insert Pull Quote",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = textColor
                            )
                            Text(
                                text = "Editorial large-font quote block for emphasis",
                                style = MaterialTheme.typography.bodySmall,
                                color = subTextColor
                            )
                        }
                    }

                    HorizontalDivider(color = subTextColor.copy(alpha = 0.15f))

                    // Footnote option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onInsertFootnote()
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Outlined.Notes, contentDescription = null, tint = accentColor)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Insert Footnote Reference",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = textColor
                            )
                            Text(
                                text = "Appends [^1] citation mark & footnote entry",
                                style = MaterialTheme.typography.bodySmall,
                                color = subTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}
