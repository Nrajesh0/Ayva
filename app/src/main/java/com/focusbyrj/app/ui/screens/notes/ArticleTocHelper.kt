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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.List
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TocItem(
    val id: String,
    val title: String,
    val level: Int, // 1 for H1, 2 for H2, 3 for H3, etc.
    val blockIndex: Int
)

object ArticleTocHelper {

    fun extractToc(
        title: String,
        blocks: List<NotesnookBlock>,
        fallbackContent: String
    ): List<TocItem> {
        val list = mutableListOf<TocItem>()

        if (title.isNotBlank()) {
            list.add(TocItem(id = "title", title = title, level = 1, blockIndex = 0))
        }

        if (blocks.size <= 1 && (blocks.isEmpty() || blocks[0] is NotesnookBlock.Text)) {
            val text = if (blocks.isNotEmpty()) (blocks[0] as NotesnookBlock.Text).text else fallbackContent
            val spans = if (blocks.isNotEmpty()) (blocks[0] as NotesnookBlock.Text).spans else emptyList()
            
            // Check spans for headings
            val headingSpans = spans.filter {
                it.type in setOf(
                    RichSpanType.HEADING_1, RichSpanType.HEADING_2, RichSpanType.HEADING_3,
                    RichSpanType.HEADING_4, RichSpanType.HEADING_5, RichSpanType.HEADING_6
                )
            }.sortedBy { it.start }

            headingSpans.forEachIndexed { idx, span ->
                val s = span.start.coerceIn(0, text.length)
                val e = span.end.coerceIn(s, text.length)
                val headingText = text.substring(s, e).trim()
                if (headingText.isNotBlank()) {
                    val lvl = when (span.type) {
                        RichSpanType.HEADING_1 -> 1
                        RichSpanType.HEADING_2 -> 2
                        RichSpanType.HEADING_3 -> 3
                        RichSpanType.HEADING_4 -> 4
                        RichSpanType.HEADING_5 -> 5
                        else -> 6
                    }
                    list.add(TocItem(id = "span_$idx", title = headingText, level = lvl, blockIndex = 0))
                }
            }

            // Also check markdown lines in raw text if no spans
            if (headingSpans.isEmpty() && text.isNotBlank()) {
                val lines = text.split("\n")
                lines.forEachIndexed { lineIdx, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#")) {
                        val hashCount = trimmed.takeWhile { it == '#' }.length
                        val headerText = trimmed.removePrefix("#".repeat(hashCount)).trim()
                        if (headerText.isNotBlank()) {
                            list.add(TocItem(id = "line_$lineIdx", title = headerText, level = hashCount.coerceIn(1, 6), blockIndex = 0))
                        }
                    }
                }
            }

            return list
        }

        blocks.forEachIndexed { index, block ->
            when (block) {
                is NotesnookBlock.Text -> {
                    val headingSpan = block.spans.firstOrNull {
                        it.type in setOf(
                            RichSpanType.HEADING_1, RichSpanType.HEADING_2, RichSpanType.HEADING_3,
                            RichSpanType.HEADING_4, RichSpanType.HEADING_5, RichSpanType.HEADING_6
                        )
                    }
                    if (headingSpan != null && block.text.isNotBlank()) {
                        val lvl = when (headingSpan.type) {
                            RichSpanType.HEADING_1 -> 1
                            RichSpanType.HEADING_2 -> 2
                            RichSpanType.HEADING_3 -> 3
                            RichSpanType.HEADING_4 -> 4
                            RichSpanType.HEADING_5 -> 5
                            else -> 6
                        }
                        list.add(TocItem(id = block.id, title = block.text, level = lvl, blockIndex = index))
                    } else if (block.text.trim().startsWith("#")) {
                        val trimmed = block.text.trim()
                        val hashCount = trimmed.takeWhile { it == '#' }.length
                        val headerText = trimmed.removePrefix("#".repeat(hashCount)).trim()
                        if (headerText.isNotBlank()) {
                            list.add(TocItem(id = block.id, title = headerText, level = hashCount.coerceIn(1, 6), blockIndex = index))
                        }
                    }
                }
                is NotesnookBlock.OutlineItem -> {
                    if (block.text.isNotBlank()) {
                        list.add(TocItem(id = block.id, title = block.text, level = block.level + 2, blockIndex = index))
                    }
                }
                else -> {}
            }
        }

        return list
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleTocBottomSheet(
    tocItems: List<TocItem>,
    isDark: Boolean,
    onSelectTocItem: (TocItem) -> Unit,
    onDismiss: () -> Unit
) {
    val containerBg = if (isDark) Color(0xFF14161B) else MaterialTheme.colorScheme.surfaceContainerHigh
    val cardBg = if (isDark) Color(0xFF1E222A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                .testTag("article_toc_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.FormatListNumbered,
                        contentDescription = null,
                        tint = accentColor
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Table of Contents",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                        Text(
                            text = "${tocItems.size} section headings found",
                            style = MaterialTheme.typography.bodySmall,
                            color = subTextColor
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = subTextColor)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = subTextColor.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(12.dp))

            if (tocItems.isEmpty()) {
                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Outlined.List, contentDescription = null, tint = subTextColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No headings found",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = textColor
                        )
                        Text(
                            text = "Add H1, H2, or H3 headings to auto-generate document navigation",
                            style = MaterialTheme.typography.bodySmall,
                            color = subTextColor
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tocItems) { item ->
                        val indentDp = ((item.level - 1) * 16).dp
                        val isH1 = item.level == 1
                        val fontSize = if (isH1) 15.sp else 14.sp
                        val fontWeight = if (isH1) FontWeight.Bold else FontWeight.Medium

                        Surface(
                            color = cardBg,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = indentDp)
                                .clickable {
                                    onSelectTocItem(item)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "H${item.level}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = accentColor,
                                    modifier = Modifier.width(28.dp)
                                )
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = fontSize,
                                        fontWeight = fontWeight
                                    ),
                                    color = textColor,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
