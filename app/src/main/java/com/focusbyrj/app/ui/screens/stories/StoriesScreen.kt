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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.screens.notes.ArticleExportBottomSheet
import com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager

@Composable
fun StoriesScreen(
    viewModel: StoriesViewModel = viewModel()
) {
    val isDark = isSystemInDarkTheme()
    val displayedStories by viewModel.displayedStories.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val allStories by viewModel.allStories.collectAsState()
    val draftStories by viewModel.draftStories.collectAsState()
    val publishedStories by viewModel.publishedStories.collectAsState()

    val editingStory by viewModel.editingStory.collectAsState()
    val readingStory by viewModel.readingStory.collectAsState()

    var showSearchField by remember { mutableStateOf(false) }
    var storyToExport by remember { mutableStateOf<NoteEntity?>(null) }
    var storyToDelete by remember { mutableStateOf<NoteEntity?>(null) }

    // 1. If Editor is active, render full-screen Medium Editor
    if (editingStory != null) {
        val currentEdit = editingStory!!
        StoryEditorScreen(
            state = currentEdit,
            onTitleChange = viewModel::updateTitle,
            onSubtitleChange = viewModel::updateSubtitle,
            onContentChange = viewModel::updateContent,
            onSetCoverImage = viewModel::setCoverImage,
            onRemoveCoverImage = viewModel::removeCoverImage,
            onSetFont = viewModel::setFont,
            onPublish = viewModel::publishStory,
            onUnpublish = viewModel::unpublishStory,
            onPreview = {
                val entity = NoteEntity(
                    id = currentEdit.originalId,
                    title = currentEdit.title,
                    subtitle = currentEdit.subtitle,
                    content = currentEdit.content,
                    coverImageUri = currentEdit.coverImageUri,
                    isArticle = true,
                    isPublished = currentEdit.isPublished,
                    publishedAt = currentEdit.publishedAt,
                    readingTimeMinutes = currentEdit.readingTimeMinutes,
                    fontKey = currentEdit.fontKey
                )
                viewModel.openStoryReader(entity)
            },
            onClose = viewModel::closeStoryEditor
        )
        return
    }

    // 2. If Reader View is active, render full-screen Reader
    if (readingStory != null) {
        val currentRead = readingStory!!
        StoryReaderScreen(
            story = currentRead,
            onEdit = {
                viewModel.openStoryForEditing(currentRead)
            },
            onClose = viewModel::closeStoryReader
        )
        return
    }

    // 3. Otherwise, render Stories Dashboard
    val bgColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .testTag("stories_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Stories",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            fontFamily = FontFamily.Serif
                        ),
                        color = textColor
                    )
                    Text(
                        text = "Long-form editorial & blog studio",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = subtitleColor
                    )
                }

                IconButton(
                    onClick = {
                        showSearchField = !showSearchField
                        if (!showSearchField) viewModel.setSearchQuery("")
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = if (showSearchField) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search Stories",
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // SEARCH BAR
            AnimatedVisibility(visible = showSearchField) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    placeholder = { Text("Search by title, subtitle, or story text...") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            }

            // FILTER SEGMENTED PILLS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StoriesFilterChip(
                    label = "Drafts (${draftStories.size})",
                    isSelected = selectedTab == StoriesTab.DRAFTS,
                    onClick = { viewModel.setSelectedTab(StoriesTab.DRAFTS) }
                )
                StoriesFilterChip(
                    label = "Published (${publishedStories.size})",
                    isSelected = selectedTab == StoriesTab.PUBLISHED,
                    onClick = { viewModel.setSelectedTab(StoriesTab.PUBLISHED) }
                )
                StoriesFilterChip(
                    label = "All (${allStories.size})",
                    isSelected = selectedTab == StoriesTab.ALL,
                    onClick = { viewModel.setSelectedTab(StoriesTab.ALL) }
                )
            }

            // STORIES FEED OR EMPTY STATE
            if (displayedStories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching stories" else when (selectedTab) {
                                StoriesTab.DRAFTS -> "No drafts in progress"
                                StoriesTab.PUBLISHED -> "No published stories yet"
                                StoriesTab.ALL -> "Your writer's desk is quiet"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = textColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (searchQuery.isNotBlank()) "Try searching for different keywords or clear your query." else when (selectedTab) {
                                StoriesTab.DRAFTS -> "Start drafting your next article, essay, or blog post here."
                                StoriesTab.PUBLISHED -> "When you finish a draft, hit 'Publish' to save it to your published magazine."
                                StoriesTab.ALL -> "Capture deep thoughts, long-form articles, and publication-ready essays."
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = subtitleColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { viewModel.openNewStory() },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Write a Story", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = displayedStories,
                        key = { it.id }
                    ) { story ->
                        StoryCard(
                            story = story,
                            onClick = {
                                if (story.isPublished) {
                                    viewModel.openStoryReader(story)
                                } else {
                                    viewModel.openStoryForEditing(story)
                                }
                            },
                            onEdit = {
                                viewModel.openStoryForEditing(story)
                            },
                            onTogglePublish = {
                                viewModel.togglePublishFromCard(story)
                            },
                            onDuplicate = {
                                viewModel.duplicateStory(story)
                            },
                            onExport = {
                                storyToExport = story
                            },
                            onDelete = {
                                storyToDelete = story
                            }
                        )
                    }
                }
            }
        }

        // FLOATING ACTION BUTTON: WRITE NEW STORY
        FloatingActionButton(
            onClick = { viewModel.openNewStory() },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("write_story_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Write Story"
            )
        }

        // EXPORT BOTTOM SHEET
        if (storyToExport != null) {
            val exp = storyToExport!!
            val blocks = remember(exp.content) {
                NotesnookBlockManager.parse(exp.content)
            }
            ArticleExportBottomSheet(
                title = exp.title,
                blocks = blocks,
                content = exp.content,
                isChecklist = false,
                checklistItems = emptyList(),
                isDark = isDark,
                onDismiss = { storyToExport = null }
            )
        }

        // DELETE CONFIRMATION DIALOG
        if (storyToDelete != null) {
            val del = storyToDelete!!
            AlertDialog(
                onDismissRequest = { storyToDelete = null },
                title = { Text("Delete Story") },
                text = { Text("Are you sure you want to permanently delete \"${del.title.ifBlank { "Untitled Story" }}\"? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteStory(del)
                            storyToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { storyToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun StoriesFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        if (isDark) Color(0xFF1F2430) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.5.sp
            ),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
