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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilePresent
import androidx.compose.material.icons.outlined.Html
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.data.note.ChecklistItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleExportBottomSheet(
    title: String,
    blocks: List<NotesnookBlock>,
    content: String,
    labels: List<String> = emptyList(),
    isChecklist: Boolean = false,
    checklistItems: List<ChecklistItem> = emptyList(),
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val containerBg = if (isDark) Color(0xFF14161B) else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (isDark) Color.White else Color(0xFF1A1C20)
    val subTextColor = if (isDark) Color(0xFF9AA0A6) else Color(0xFF5F6368)
    val accentColor = Color(0xFF22C55E)

    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }

    val markdownContent = remember(title, blocks, content, isChecklist, checklistItems) {
        ArticleExporter.exportToMarkdown(title, blocks, content, isChecklist, checklistItems)
    }

    val frontmatterContent = remember(title, blocks, content, labels, isChecklist, checklistItems) {
        ArticleExporter.exportToMarkdownWithFrontmatter(
            title = title,
            blocks = blocks,
            fallbackContent = content,
            labels = labels,
            isChecklist = isChecklist,
            checklistItems = checklistItems
        )
    }

    val htmlContent = remember(title, blocks, content, isChecklist, checklistItems) {
        ArticleExporter.exportToHtml(title, blocks, content, isChecklist, checklistItems)
    }

    val plainTextContent = remember(title, blocks, content, isChecklist, checklistItems) {
        buildString {
            if (title.isNotBlank()) append(title).append("\n\n")
            if (isChecklist && checklistItems.isNotEmpty()) {
                checklistItems.forEach { item ->
                    append(if (item.isChecked) "[x] " else "[ ] ").append(item.text).append("\n")
                }
            } else {
                append(content)
            }
        }.trim()
    }

    val activeTextPreview = when (selectedFormat) {
        ExportFormat.MARKDOWN -> markdownContent
        ExportFormat.MARKDOWN_FRONTMATTER -> frontmatterContent
        ExportFormat.HTML -> htmlContent
        ExportFormat.PLAIN_TEXT -> plainTextContent
        else -> null
    }

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
                .testTag("article_export_sheet")
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Publish,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Export Document",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                        Text(
                            text = "Export as PDF, Word DOCX, Markdown, or HTML",
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

            // Format Selection Chips (Horizontal Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val formats = listOf(
                    Triple(ExportFormat.PDF, "PDF (.pdf)", Icons.Outlined.PictureAsPdf),
                    Triple(ExportFormat.MARKDOWN, "Markdown (.md)", Icons.Outlined.Description),
                    Triple(ExportFormat.MARKDOWN_FRONTMATTER, "MD + Frontmatter", Icons.Outlined.FilePresent),
                    Triple(ExportFormat.DOCX, "Word (.docx)", Icons.Outlined.Description),
                    Triple(ExportFormat.HTML, "HTML (.html)", Icons.Outlined.Html),
                    Triple(ExportFormat.PLAIN_TEXT, "Plain Text", Icons.Outlined.TextFields)
                )

                formats.forEach { (fmt, label, icon) ->
                    val isSelected = selectedFormat == fmt
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFormat = fmt },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) accentColor else subTextColor
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.15f),
                            selectedLabelColor = accentColor,
                            containerColor = if (isDark) Color(0xFF1E222A) else Color(0xFFF1F5F9),
                            labelColor = textColor
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) accentColor else Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("export_chip_${fmt.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Preview Box
            if (activeTextPreview != null) {
                // Text Preview (MD, Frontmatter, HTML, Text)
                Surface(
                    color = if (isDark) Color(0xFF0F1115) else Color(0xFFF3F4F6),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(1.dp, subTextColor.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = activeTextPreview.ifBlank { "No content to preview" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp
                            ),
                            color = textColor
                        )
                    }
                }
            } else {
                // Binary Document Card Preview (PDF & DOCX)
                Surface(
                    color = if (isDark) Color(0xFF0F1115) else Color(0xFFF3F4F6),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(1.dp, subTextColor.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val icon: ImageVector
                        val formatTitle: String
                        val formatDesc: String
                        val iconTint: Color

                        if (selectedFormat == ExportFormat.PDF) {
                            icon = Icons.Outlined.PictureAsPdf
                            formatTitle = "Standard A4 Vector PDF Document"
                            formatDesc = "Crisp typography, styled headings, checklists, callouts & tables with page pagination"
                            iconTint = Color(0xFFEF4444)
                        } else {
                            icon = Icons.Outlined.Description
                            formatTitle = "Microsoft Word Document (.docx)"
                            formatDesc = "OpenXML compatible document for Microsoft Word, Google Docs & LibreOffice"
                            iconTint = Color(0xFF2563EB)
                        }

                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(iconTint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = formatTitle,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = formatDesc,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = subTextColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons: Save to Downloads, Share File, Copy (if text)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Save to Downloads / Storage Button
                Button(
                    onClick = {
                        val success = ArticleExporter.saveToDownloads(
                            context = context,
                            format = selectedFormat,
                            title = title,
                            blocks = blocks,
                            fallbackContent = content,
                            labels = labels,
                            isChecklist = isChecklist,
                            checklistItems = checklistItems
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_save_button")
                ) {
                    Icon(imageVector = Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Download File", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Share File Button
                OutlinedButton(
                    onClick = {
                        ArticleExporter.shareExportedFile(
                            context = context,
                            format = selectedFormat,
                            title = title,
                            blocks = blocks,
                            fallbackContent = content,
                            labels = labels,
                            isChecklist = isChecklist,
                            checklistItems = checklistItems
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_share_button")
                ) {
                    Icon(imageVector = Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Share File", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            // Copy Text option for Markdown / HTML / PlainText
            if (activeTextPreview != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Exported ${selectedFormat.displayName}", activeTextPreview)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied ${selectedFormat.displayName} to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_copy_button")
                ) {
                    Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Copy Code to Clipboard", fontSize = 12.5.sp)
                }
            }
        }
    }
}
