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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.screens.notes.ArticleExportBottomSheet
import com.focusbyrj.app.ui.screens.notes.ArticleTocBottomSheet
import com.focusbyrj.app.ui.screens.notes.ArticleTocHelper
import com.focusbyrj.app.ui.screens.notes.NotesnookBlock
import com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager
import com.focusbyrj.app.ui.screens.notes.NotesnookCalloutWidget
import com.focusbyrj.app.ui.screens.notes.NotesnookCodeBlockWidget
import com.focusbyrj.app.ui.screens.notes.NotesnookHorizontalRuleWidget
import com.focusbyrj.app.ui.screens.notes.NotesnookImageBlockWidget
import com.focusbyrj.app.ui.screens.notes.NotesnookMathWidget
import com.focusbyrj.app.ui.screens.notes.NotesnookQuoteWidget
import com.focusbyrj.app.ui.screens.notes.NotesnookTableWidget
import com.focusbyrj.app.ui.screens.notes.RichTextEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StoryReaderScreen(
    story: NoteEntity,
    onEdit: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val scrollState = rememberScrollState()

    var showExportSheet by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }

    val bgColor = if (isDark) Color(0xFF0D1117) else Color(0xFFFAFAF9) // Clean paper background
    val textColor = if (isDark) Color(0xFFE6EDF3) else Color(0xFF1C1917)
    val subtitleColor = if (isDark) Color(0xFF8B949E) else Color(0xFF57534E)
    val accentColor = Color(0xFF10B981)

    val font = remember(story.fontKey) {
        if (story.fontKey == "serif") FontFamily.Serif else FontFamily.Default
    }

    val blocks = remember(story.content) {
        NotesnookBlockManager.parse(story.content)
    }

    val formattedDate = remember(story.publishedAt, story.updatedAt) {
        val ts = story.publishedAt ?: story.updatedAt
        val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        sdf.format(Date(ts))
    }

    val wordCount = remember(story.content) {
        story.content.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
    }

    val readingTime = remember(story.readingTimeMinutes, wordCount) {
        if (story.readingTimeMinutes > 0) story.readingTimeMinutes else (wordCount / 200).coerceAtLeast(1)
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("story_reader_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP APP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("reader_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textColor
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Table of Contents
                    IconButton(
                        onClick = { showTocSheet = true },
                        modifier = Modifier.testTag("reader_toc_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FormatListNumbered,
                            contentDescription = "Table of contents",
                            tint = textColor.copy(alpha = 0.8f)
                        )
                    }

                    // Share & Export
                    IconButton(
                        onClick = { showExportSheet = true },
                        modifier = Modifier.testTag("reader_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Export and share",
                            tint = textColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // SCROLLABLE ARTICLE BODY (Max width 720dp centered reading canvas)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(bottom = 96.dp)
                ) {
                    // HERO COVER BANNER
                    if (!story.coverImageUri.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(story.coverImageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Article Cover Banner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Gradient vignette
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                bgColor.copy(alpha = 0.85f)
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    // ARTICLE HEADER
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = if (story.coverImageUri.isNullOrBlank()) 16.dp else 8.dp)
                    ) {
                        // Category / Status pill
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (story.isPublished) accentColor.copy(alpha = 0.15f) else Color(0x22888888)
                            ) {
                                Text(
                                    text = if (story.isPublished) "Published Story" else "Draft Preview",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = if (story.isPublished) accentColor else subtitleColor,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Title
                        Text(
                            text = story.title.ifBlank { "Untitled Story" },
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                lineHeight = 40.sp,
                                fontFamily = font
                            ),
                            color = textColor
                        )

                        // Subtitle
                        if (story.subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = story.subtitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 18.sp,
                                    lineHeight = 26.sp,
                                    fontStyle = FontStyle.Italic
                                ),
                                color = subtitleColor
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Author & Reading Stats Byline
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = subtitleColor
                            )
                            Text(text = "•", color = subtitleColor, fontSize = 13.sp)
                            Text(
                                text = "$readingTime min read",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = subtitleColor
                            )
                            if (wordCount > 0) {
                                Text(text = "•", color = subtitleColor, fontSize = 13.sp)
                                Text(
                                    text = "$wordCount words",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = subtitleColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = subtitleColor.copy(alpha = 0.25f))
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // ARTICLE CONTENT BLOCKS
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (blocks.size <= 1 && (blocks.isEmpty() || blocks[0] is NotesnookBlock.Text)) {
                            // Standard Rich Text Single Block
                            val rawContent = if (blocks.isNotEmpty()) (blocks[0] as NotesnookBlock.Text).text else story.content
                            val spans = if (blocks.isNotEmpty()) (blocks[0] as NotesnookBlock.Text).spans else emptyList()
                            val annotated = RichTextEngine.toAnnotatedString(
                                plainText = rawContent,
                                spans = spans,
                                textColor = textColor,
                                accentColor = accentColor,
                                isDark = isDark,
                                baseFontSizeSp = 17f
                            )
                            Text(
                                text = annotated,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 17.5.sp,
                                    lineHeight = 29.sp,
                                    fontFamily = font
                                ),
                                color = textColor
                            )
                        } else {
                            // Multi-block Rich Article (Seamless Tables, Code, Math, Callouts, Quotes)
                            blocks.forEach { block ->
                                when (block) {
                                    is NotesnookBlock.Text -> {
                                        if (block.text.isNotBlank()) {
                                            val annotated = RichTextEngine.toAnnotatedString(
                                                plainText = block.text,
                                                spans = block.spans,
                                                textColor = textColor,
                                                accentColor = accentColor,
                                                isDark = isDark,
                                                baseFontSizeSp = 17f
                                            )
                                            Text(
                                                text = annotated,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = 17.5.sp,
                                                    lineHeight = 29.sp,
                                                    fontFamily = font
                                                ),
                                                color = textColor
                                            )
                                        }
                                    }
                                    is NotesnookBlock.Quote -> {
                                        NotesnookQuoteWidget(
                                            block = block,
                                            onUpdate = {},
                                            onDelete = {},
                                            isDark = isDark,
                                            noteTextColor = textColor
                                        )
                                    }
                                    is NotesnookBlock.Callout -> {
                                        NotesnookCalloutWidget(
                                            block = block,
                                            onUpdate = {},
                                            onDelete = {},
                                            isDark = isDark,
                                            noteTextColor = textColor
                                        )
                                    }
                                    is NotesnookBlock.Code -> {
                                        NotesnookCodeBlockWidget(
                                            block = block,
                                            onUpdate = {},
                                            onDelete = {},
                                            isDark = isDark
                                        )
                                    }
                                    is NotesnookBlock.Table -> {
                                        NotesnookTableWidget(
                                            table = block,
                                            onUpdate = {},
                                            onDelete = {},
                                            isDark = isDark,
                                            textColor = textColor
                                        )
                                    }
                                    is NotesnookBlock.MathFormula -> {
                                        NotesnookMathWidget(
                                            block = block,
                                            onUpdate = {},
                                            onDelete = {},
                                            isDark = isDark,
                                            noteTextColor = textColor
                                        )
                                    }
                                    is NotesnookBlock.HorizontalRule -> {
                                        NotesnookHorizontalRuleWidget(
                                            block = block,
                                            onDelete = {},
                                            isDark = isDark
                                        )
                                    }
                                    is NotesnookBlock.Image -> {
                                        NotesnookImageBlockWidget(
                                            block = block,
                                            onUpdate = {},
                                            onDelete = {},
                                            onClick = {},
                                            isDark = isDark,
                                            noteTextColor = textColor
                                        )
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }
        }

        // FLOATING ACTION BUTTON: EDIT STORY
        FloatingActionButton(
            onClick = onEdit,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("reader_edit_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Story"
            )
        }

        // TABLE OF CONTENTS SHEET
        if (showTocSheet) {
            val tocItems = remember(story.title, story.content, blocks) {
                ArticleTocHelper.extractToc(
                    title = story.title,
                    blocks = blocks,
                    fallbackContent = story.content
                )
            }
            ArticleTocBottomSheet(
                tocItems = tocItems,
                isDark = isDark,
                onSelectTocItem = { _ ->
                    showTocSheet = false
                },
                onDismiss = { showTocSheet = false }
            )
        }

        // EXPORT BOTTOM SHEET
        if (showExportSheet) {
            ArticleExportBottomSheet(
                title = story.title,
                blocks = blocks,
                content = story.content,
                isChecklist = false,
                checklistItems = emptyList(),
                isDark = isDark,
                onDismiss = { showExportSheet = false }
            )
        }
    }
}
