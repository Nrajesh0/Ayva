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

package com.focusbyrj.app.ui.screens.stories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.focusbyrj.app.data.note.NoteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StoryCard(
    story: NoteEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onTogglePublish: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    var showMenu by remember { mutableStateOf(false) }

    val cardBg = if (isDark) Color(0xFF161A22) else MaterialTheme.colorScheme.surface
    val borderColor = if (isDark) Color(0xFF282F3D) else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val textColor = if (isDark) Color(0xFFE6EDF3) else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (isDark) Color(0xFF8B949E) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

    val formattedDate = remember(story.updatedAt, story.publishedAt, story.isPublished) {
        val timestamp = if (story.isPublished && story.publishedAt != null) story.publishedAt else story.updatedAt
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        sdf.format(Date(timestamp))
    }

    val wordCount = remember(story.content) {
        story.content.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
    }

    val readingTime = remember(story.readingTimeMinutes, wordCount) {
        if (story.readingTimeMinutes > 0) story.readingTimeMinutes else (wordCount / 200).coerceAtLeast(1)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("story_card_${story.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // HERO COVER IMAGE
            if (!story.coverImageUri.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(story.coverImageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Cover Image for ${story.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Subtle bottom gradient for readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.45f)
                                    )
                                )
                            )
                    )

                    // Publication Status Badge overlay
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (story.isPublished) Color(0xFF10B981) else Color(0xCC1F2937),
                        contentColor = Color.White
                    ) {
                        Text(
                            text = if (story.isPublished) "Published" else "Draft",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            } else {
                // Typographic Banner Header for articles without a cover image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(
                            Brush.linearGradient(
                                colors = if (isDark) {
                                    listOf(Color(0xFF1E2638), Color(0xFF161A22))
                                } else {
                                    listOf(Color(0xFFEDE9FE), Color(0xFFF3F4F6))
                                }
                            )
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFFA5B4FC) else Color(0xFF6366F1),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "STORY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.5.sp
                                ),
                                color = if (isDark) Color(0xFFA5B4FC) else Color(0xFF6366F1)
                            )
                        }

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (story.isPublished) Color(0xFF10B981).copy(alpha = 0.2f) else if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB),
                            border = BorderStroke(1.dp, if (story.isPublished) Color(0xFF10B981) else Color.Transparent)
                        ) {
                            Text(
                                text = if (story.isPublished) "Published" else "Draft",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = if (story.isPublished) Color(0xFF10B981) else mutedColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // STORY CONTENT INFO
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Title
                Text(
                    text = story.title.ifBlank { "Untitled Story" },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        lineHeight = 25.sp,
                        fontFamily = if (story.fontKey == "serif") FontFamily.Serif else FontFamily.Default
                    ),
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtitle
                if (story.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = story.subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp
                        ),
                        color = mutedColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metadata & Options Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Reading info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = mutedColor
                        )
                        Text(text = "•", color = mutedColor, fontSize = 12.sp)
                        Text(
                            text = "$readingTime min read",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = mutedColor
                        )
                        if (wordCount > 0) {
                            Text(text = "•", color = mutedColor, fontSize = 12.sp)
                            Text(
                                text = "$wordCount words",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = mutedColor
                            )
                        }
                    }

                    // Action buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Quick Edit Icon
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Story",
                                tint = mutedColor,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // More options
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Story Options",
                                    tint = mutedColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Open in Reader") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.MenuBook, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit Story") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onEdit()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (story.isPublished) "Move to Drafts" else "Publish Story") },
                                    leadingIcon = {
                                        Icon(
                                            if (story.isPublished) Icons.Default.PublicOff else Icons.Default.Public,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onTogglePublish()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export & Share") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onExport()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDuplicate()
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete Story", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
