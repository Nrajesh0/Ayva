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
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.TextFields
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.data.note.ChecklistItem

data class DocumentMetrics(
    val words: Int,
    val characters: Int,
    val charactersNoSpaces: Int,
    val paragraphs: Int,
    val sentences: Int,
    val readingTimeMinutes: Int,
    val speakingTimeMinutes: Int,
    val textBlocksCount: Int = 0,
    val tablesCount: Int = 0,
    val codeBlocksCount: Int = 0,
    val otherBlocksCount: Int = 0
)

object DocumentMetricsCalculator {
    fun calculate(
        title: String,
        content: String,
        blocks: List<NotesnookBlock>,
        checklistItems: List<ChecklistItem> = emptyList(),
        isChecklist: Boolean = false
    ): DocumentMetrics {
        val allText = buildString {
            if (title.isNotBlank()) append(title).append("\n\n")
            if (isChecklist) {
                checklistItems.forEach { item ->
                    append(item.text).append("\n")
                }
            } else if (blocks.isNotEmpty()) {
                blocks.forEach { b ->
                    when (b) {
                        is NotesnookBlock.Text -> append(b.text).append("\n\n")
                        is NotesnookBlock.Table -> {
                            b.data.forEach { row ->
                                row.forEach { cell ->
                                    append(cell).append(" ")
                                }
                                append("\n")
                            }
                        }
                        is NotesnookBlock.Code -> append(b.code).append("\n\n")
                        is NotesnookBlock.Callout -> append(b.text).append("\n\n")
                        is NotesnookBlock.Quote -> append(b.text).append("\n\n")
                        is NotesnookBlock.OutlineItem -> append(b.text).append("\n")
                        is NotesnookBlock.MathFormula -> append(b.formula).append("\n")
                        else -> {}
                    }
                }
            } else {
                append(content)
            }
        }.trim()

        val chars = allText.length
        val charsNoSpaces = allText.count { !it.isWhitespace() }
        val wordsList = allText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordCount = wordsList.size

        val paragraphs = if (allText.isBlank()) 0 else allText.split(Regex("\n+")).filter { it.isNotBlank() }.size
        val sentences = if (allText.isBlank()) 0 else allText.split(Regex("[.!?]+\\s*")).filter { it.isNotBlank() }.size

        val readingMinutes = if (wordCount == 0) 0 else if (wordCount <= 200) 1 else kotlin.math.ceil(wordCount / 200.0).toInt()
        val speakingMinutes = if (wordCount == 0) 0 else if (wordCount <= 130) 1 else kotlin.math.ceil(wordCount / 130.0).toInt()

        val textBlocks = blocks.count { it is NotesnookBlock.Text }
        val tables = blocks.count { it is NotesnookBlock.Table }
        val codeBlocks = blocks.count { it is NotesnookBlock.Code }
        val otherBlocks = blocks.size - (textBlocks + tables + codeBlocks)

        return DocumentMetrics(
            words = wordCount,
            characters = chars,
            charactersNoSpaces = charsNoSpaces,
            paragraphs = paragraphs,
            sentences = sentences,
            readingTimeMinutes = readingMinutes,
            speakingTimeMinutes = speakingMinutes,
            textBlocksCount = textBlocks,
            tablesCount = tables,
            codeBlocksCount = codeBlocks,
            otherBlocksCount = maxOf(0, otherBlocks)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentStatsBottomSheet(
    metrics: DocumentMetrics,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val containerBg = if (isDark) Color(0xFF14161B) else MaterialTheme.colorScheme.surfaceContainerHigh
    val cardBg = if (isDark) Color(0xFF1E222A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val textColor = if (isDark) Color.White else Color(0xFF1A1C20)
    val subTextColor = if (isDark) Color(0xFF9AA0A6) else Color(0xFF5F6368)
    val accentColor = Color(0xFF22C55E) // Notesnook Green

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
                .testTag("document_stats_sheet")
        ) {
            // Header Row
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
                            imageVector = Icons.Outlined.Article,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Document Statistics",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = textColor
                        )
                        Text(
                            text = "Live article & reading analysis",
                            style = MaterialTheme.typography.bodySmall,
                            color = subTextColor
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = subTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2x2 Grid of Key Metrics Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Words",
                    value = String.format(java.util.Locale.US, "%,d", metrics.words),
                    icon = Icons.Outlined.TextFields,
                    color = Color(0xFF3B82F6),
                    bg = cardBg,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Reading Time",
                    value = if (metrics.readingTimeMinutes <= 1 && metrics.words > 0) "~1 min" else if (metrics.words == 0) "0 min" else "~${metrics.readingTimeMinutes} mins",
                    icon = Icons.Outlined.AccessTime,
                    color = Color(0xFF10B981),
                    bg = cardBg,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Characters",
                    value = String.format(java.util.Locale.US, "%,d", metrics.characters),
                    subtitle = "${String.format(java.util.Locale.US, "%,d", metrics.charactersNoSpaces)} no spaces",
                    icon = Icons.Outlined.GraphicEq,
                    color = Color(0xFFF59E0B),
                    bg = cardBg,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Speaking Time",
                    value = if (metrics.speakingTimeMinutes <= 1 && metrics.words > 0) "~1 min" else if (metrics.words == 0) "0 min" else "~${metrics.speakingTimeMinutes} mins",
                    subtitle = "at 130 wpm",
                    icon = Icons.Outlined.RecordVoiceOver,
                    color = Color(0xFF8B5CF6),
                    bg = cardBg,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = subTextColor.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(16.dp))

            // Detailed Section
            Text(
                text = "CONTENT BREAKDOWN",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontSize = 11.sp
                ),
                color = subTextColor
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = cardBg,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow(label = "Paragraphs", value = "${metrics.paragraphs}", textColor = textColor, subTextColor = subTextColor)
                    DetailRow(label = "Sentences", value = "${metrics.sentences}", textColor = textColor, subTextColor = subTextColor)
                    val avgWordLen = if (metrics.words > 0) {
                        String.format(java.util.Locale.US, "%.1f chars", metrics.charactersNoSpaces.toFloat() / metrics.words)
                    } else "0 chars"
                    DetailRow(label = "Avg. Word Length", value = avgWordLen, textColor = textColor, subTextColor = subTextColor)

                    if (metrics.textBlocksCount + metrics.tablesCount + metrics.codeBlocksCount + metrics.otherBlocksCount > 1) {
                        val blocksDesc = buildList {
                            if (metrics.textBlocksCount > 0) add("${metrics.textBlocksCount} text")
                            if (metrics.tablesCount > 0) add("${metrics.tablesCount} table")
                            if (metrics.codeBlocksCount > 0) add("${metrics.codeBlocksCount} code")
                            if (metrics.otherBlocksCount > 0) add("${metrics.otherBlocksCount} other")
                        }.joinToString(", ")
                        DetailRow(label = "Document Blocks", value = blocksDesc, textColor = textColor, subTextColor = subTextColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    color: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = color
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    textColor: Color,
    subTextColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = subTextColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = textColor
        )
    }
}
