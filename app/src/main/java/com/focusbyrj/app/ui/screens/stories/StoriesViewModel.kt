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

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.data.note.NoteImageHelper
import com.focusbyrj.app.data.note.NoteRepository
import com.focusbyrj.app.ui.screens.notes.DocumentMetricsCalculator
import com.focusbyrj.app.ui.screens.notes.NotesnookBlock
import com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager
import com.focusbyrj.app.ui.screens.notes.RichSpan
import com.focusbyrj.app.ui.screens.notes.RichTextEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

enum class StoriesTab(val label: String) {
    DRAFTS("Drafts"),
    PUBLISHED("Published"),
    ALL("All Stories")
}

data class EditingStoryState(
    val sessionId: String = UUID.randomUUID().toString(),
    val originalId: Long = 0L,
    val title: String = "",
    val subtitle: String = "",
    val content: String = "",
    val coverImageUri: String? = null,
    val isPublished: Boolean = false,
    val publishedAt: Long? = null,
    val readingTimeMinutes: Int = 0,
    val fontKey: String = "serif",
    val colorKey: String = "default",
    val isPinned: Boolean = false,
    val labelsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

class StoriesViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "StoriesViewModel"
    private val repository: NoteRepository
    private val persistMutex = Mutex()
    private var autoSaveJob: Job? = null

    // Tabs and Filtering
    private val _selectedTab = MutableStateFlow(StoriesTab.DRAFTS)
    val selectedTab: StateFlow<StoriesTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Editing State (null = dashboard, non-null = Medium editor open)
    private val _editingStory = MutableStateFlow<EditingStoryState?>(null)
    val editingStory: StateFlow<EditingStoryState?> = _editingStory.asStateFlow()

    // Reader View State (null = not reading, non-null = reading published article)
    private val _readingStory = MutableStateFlow<NoteEntity?>(null)
    val readingStory: StateFlow<NoteEntity?> = _readingStory.asStateFlow()

    init {
        val db = NoteDatabase.getInstance(application)
        repository = NoteRepository(db.noteDao(), application)
    }

    val allStories = repository.getAllArticles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val draftStories = repository.getDraftArticles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val publishedStories = repository.getPublishedArticles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val displayedStories: StateFlow<List<NoteEntity>> = combine(
        allStories,
        _selectedTab,
        _searchQuery
    ) { stories, tab, query ->
        val filteredByTab = when (tab) {
            StoriesTab.DRAFTS -> stories.filter { !it.isPublished }
            StoriesTab.PUBLISHED -> stories.filter { it.isPublished }
            StoriesTab.ALL -> stories
        }

        if (query.isBlank()) {
            filteredByTab
        } else {
            val q = query.trim().lowercase()
            filteredByTab.filter {
                it.title.lowercase().contains(q) ||
                it.subtitle.lowercase().contains(q) ||
                it.content.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedTab(tab: StoriesTab) {
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // ==========================================
    // STORY CREATION & EDITOR LIFECYCLE
    // ==========================================

    fun openNewStory() {
        _readingStory.value = null
        _editingStory.value = EditingStoryState(
            sessionId = UUID.randomUUID().toString(),
            originalId = 0L,
            title = "",
            subtitle = "",
            content = "",
            coverImageUri = null,
            isPublished = false,
            publishedAt = null,
            readingTimeMinutes = 1,
            fontKey = "serif"
        )
    }

    fun openStoryForEditing(story: NoteEntity) {
        _readingStory.value = null
        _editingStory.value = EditingStoryState(
            sessionId = UUID.randomUUID().toString(),
            originalId = story.id,
            title = story.title,
            subtitle = story.subtitle,
            content = story.content,
            coverImageUri = story.coverImageUri,
            isPublished = story.isPublished,
            publishedAt = story.publishedAt,
            readingTimeMinutes = story.readingTimeMinutes.coerceAtLeast(1),
            fontKey = story.fontKey.ifBlank { "serif" },
            colorKey = story.colorKey,
            isPinned = story.isPinned,
            labelsJson = story.labelsJson,
            createdAt = story.createdAt,
            updatedAt = story.updatedAt
        )
    }

    fun openStoryReader(story: NoteEntity) {
        _editingStory.value = null
        _readingStory.value = story
    }

    fun closeStoryReader() {
        _readingStory.value = null
    }

    fun updateTitle(newTitle: String) {
        _editingStory.value = _editingStory.value?.copy(
            title = newTitle,
            updatedAt = System.currentTimeMillis()
        )
        scheduleAutoSave()
    }

    fun updateSubtitle(newSubtitle: String) {
        _editingStory.value = _editingStory.value?.copy(
            subtitle = newSubtitle,
            updatedAt = System.currentTimeMillis()
        )
        scheduleAutoSave()
    }

    fun updateContent(newContent: String) {
        val readTime = calculateReadingTime(newContent)
        _editingStory.value = _editingStory.value?.copy(
            content = newContent,
            readingTimeMinutes = readTime,
            updatedAt = System.currentTimeMillis()
        )
        scheduleAutoSave()
    }

    fun setCoverImage(uri: Uri?) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val permanentUri = uri?.let { NoteImageHelper.copyImageFile(app, it.toString()) ?: it.toString() }
            withContext(Dispatchers.Main) {
                _editingStory.value = _editingStory.value?.copy(
                    coverImageUri = permanentUri,
                    updatedAt = System.currentTimeMillis()
                )
                persistCurrentStory()
            }
        }
    }

    fun removeCoverImage() {
        _editingStory.value = _editingStory.value?.copy(
            coverImageUri = null,
            updatedAt = System.currentTimeMillis()
        )
        persistCurrentStory()
    }

    fun setFont(fontKey: String) {
        _editingStory.value = _editingStory.value?.copy(
            fontKey = fontKey,
            updatedAt = System.currentTimeMillis()
        )
        persistCurrentStory()
    }

    fun togglePublishStatus() {
        val current = _editingStory.value ?: return
        val nextPublish = !current.isPublished
        val publishedTime = if (nextPublish) System.currentTimeMillis() else null
        _editingStory.value = current.copy(
            isPublished = nextPublish,
            publishedAt = publishedTime,
            updatedAt = System.currentTimeMillis()
        )
        persistCurrentStory()
    }

    fun publishStory() {
        val current = _editingStory.value ?: return
        _editingStory.value = current.copy(
            isPublished = true,
            publishedAt = current.publishedAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        persistCurrentStory()
    }

    fun unpublishStory() {
        val current = _editingStory.value ?: return
        _editingStory.value = current.copy(
            isPublished = false,
            updatedAt = System.currentTimeMillis()
        )
        persistCurrentStory()
    }

    fun togglePin() {
        val current = _editingStory.value ?: return
        _editingStory.value = current.copy(
            isPinned = !current.isPinned,
            updatedAt = System.currentTimeMillis()
        )
        persistCurrentStory()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            persistCurrentStoryInternal()
        }
    }

    fun persistCurrentStory() {
        viewModelScope.launch(Dispatchers.IO) {
            persistCurrentStoryInternal()
        }
    }

    private suspend fun persistCurrentStoryInternal() {
        val state = _editingStory.value ?: return
        if (state.title.isBlank() && state.subtitle.isBlank() && state.content.isBlank() && state.coverImageUri == null) {
            return
        }

        persistMutex.withLock {
            val entity = NoteEntity(
                id = state.originalId,
                title = state.title,
                subtitle = state.subtitle,
                content = state.content,
                coverImageUri = state.coverImageUri,
                isArticle = true,
                isPublished = state.isPublished,
                publishedAt = state.publishedAt,
                readingTimeMinutes = state.readingTimeMinutes.coerceAtLeast(1),
                fontKey = state.fontKey,
                colorKey = state.colorKey,
                isPinned = state.isPinned,
                labelsJson = state.labelsJson,
                createdAt = state.createdAt,
                updatedAt = System.currentTimeMillis()
            )

            val assignedId = repository.saveNote(entity)
            if (state.originalId == 0L && assignedId > 0L) {
                withContext(Dispatchers.Main) {
                    _editingStory.value = _editingStory.value?.copy(originalId = assignedId)
                }
            }
        }
    }

    fun closeStoryEditor() {
        autoSaveJob?.cancel()
        val state = _editingStory.value
        _editingStory.value = null

        if (state != null) {
            viewModelScope.launch(Dispatchers.IO) {
                if (state.title.isBlank() && state.subtitle.isBlank() && state.content.isBlank() && state.coverImageUri == null) {
                    if (state.originalId != 0L) {
                        repository.deletePermanently(NoteEntity(id = state.originalId))
                    }
                } else {
                    persistCurrentStoryInternal()
                }
            }
        }
    }

    // ==========================================
    // STORY ACTIONS FROM DASHBOARD
    // ==========================================

    fun deleteStory(story: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePermanently(story)
        }
        if (_readingStory.value?.id == story.id) {
            _readingStory.value = null
        }
        if (_editingStory.value?.originalId == story.id) {
            _editingStory.value = null
        }
    }

    fun togglePublishFromCard(story: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val newPublished = !story.isPublished
            repository.updateArticlePublishStatus(
                id = story.id,
                isPublished = newPublished,
                publishedAt = if (newPublished) System.currentTimeMillis() else null
            )
        }
    }

    fun duplicateStory(story: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val copiedCover = story.coverImageUri?.let { NoteImageHelper.copyImageFile(app, it) ?: it }
            val copy = story.copy(
                id = 0L,
                title = if (story.title.isNotBlank()) "${story.title} (Copy)" else "Untitled Copy",
                coverImageUri = copiedCover,
                isPublished = false,
                publishedAt = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.saveNote(copy)
        }
    }

    fun calculateReadingTime(content: String): Int {
        val wordCount = content.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
        // Standard reading speed is ~200-220 words per minute
        return (wordCount / 200).coerceAtLeast(1)
    }
}
