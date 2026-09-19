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

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusbyrj.app.data.note.ChecklistItem
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.data.note.NoteImageHelper
import com.focusbyrj.app.data.note.NoteMediaManager
import com.focusbyrj.app.data.note.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import com.focusbyrj.app.data.note.ArchiveVaultSecurity

enum class NoteFolder(val title: String) {
    NOTES("Notes"),
    ARCHIVE("Archive"),
    TRASH("Trash")
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NoteRepository
    private val prefs = application.getSharedPreferences("keep_notes_prefs", Context.MODE_PRIVATE)
    private val persistMutex = Mutex()
    private var autoSaveJob: Job? = null

    // Secret Archive Vault Security State
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _vaultStatus = MutableStateFlow(ArchiveVaultSecurity.getVaultStatus(application))
    val vaultStatus: StateFlow<ArchiveVaultSecurity.VaultStatus> = _vaultStatus.asStateFlow()

    fun refreshVaultStatus() {
        _vaultStatus.value = ArchiveVaultSecurity.getVaultStatus(getApplication())
    }

    fun verifyVaultPasscode(pin: String): ArchiveVaultSecurity.VerifyResult {
        val result = ArchiveVaultSecurity.verifyPasscode(getApplication(), pin)
        if (result is ArchiveVaultSecurity.VerifyResult.Success) {
            _isVaultUnlocked.value = true
            _currentFolder.value = NoteFolder.ARCHIVE
            refreshVaultStatus()
        }
        return result
    }

    fun setVaultPasscode(pin: String): Boolean {
        val success = ArchiveVaultSecurity.setPasscode(getApplication(), pin)
        if (success) {
            _isVaultUnlocked.value = true
            _currentFolder.value = NoteFolder.ARCHIVE
            refreshVaultStatus()
        }
        return success
    }

    fun skipVaultPasscode() {
        ArchiveVaultSecurity.skipPasscodeSetup(getApplication())
        _isVaultUnlocked.value = true
        _currentFolder.value = NoteFolder.ARCHIVE
        refreshVaultStatus()
    }

    fun disableVaultPasscode(): Boolean {
        val success = ArchiveVaultSecurity.disablePasscode(getApplication())
        if (success) {
            refreshVaultStatus()
        }
        return success
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
        if (_currentFolder.value == NoteFolder.ARCHIVE) {
            _currentFolder.value = NoteFolder.NOTES
        }
    }

    fun getRemainingLockoutSeconds(): Long {
        return ArchiveVaultSecurity.getRemainingLockoutSeconds(getApplication())
    }

    companion object {
        val latestNotesCache = ConcurrentHashMap<Long, NoteEntity>()
    }

    init {
        val db = NoteDatabase.getInstance(application)
        repository = NoteRepository(db.noteDao())

        viewModelScope.launch(Dispatchers.IO) {
            repository.getActiveNotes().collect {
                com.focusbyrj.app.widget.NoteWidgetProvider.updateAllWidgets(application)
            }
        }

        // Clean any orphaned media files asynchronously on startup
        viewModelScope.launch(Dispatchers.IO) {
            NoteMediaManager.cleanOrphanedMedia(application, db.noteDao())
        }
    }

    // Current Active Folder
    private val _currentFolder = MutableStateFlow(NoteFolder.NOTES)
    val currentFolder: StateFlow<NoteFolder> = _currentFolder.asStateFlow()

    fun setFolder(folder: NoteFolder) {
        if (folder != NoteFolder.ARCHIVE) {
            _isVaultUnlocked.value = false
        }
        _currentFolder.value = folder
        _selectedColorFilter.value = null
        _selectedLabelFilter.value = null
        clearSelection()
    }

    // Multi-Select Mode (Google Keep selection action bar)
    private val _selectedNoteIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedNoteIds: StateFlow<Set<Long>> = _selectedNoteIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedNoteIds.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleNoteSelection(noteId: Long) {
        val current = _selectedNoteIds.value
        _selectedNoteIds.value = if (current.contains(noteId)) current - noteId else current + noteId
    }

    fun selectAllNotes(notes: List<NoteEntity>) {
        _selectedNoteIds.value = notes.map { it.id }.toSet()
    }

    fun selectAllNotes() {
        _selectedNoteIds.value = displayedNotes.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedNoteIds.value = emptySet()
    }

    fun reorderNotes(reorderedNotes: List<NoteEntity>) {
        if (reorderedNotes.size <= 1) return
        reorderedNotes.forEach { latestNotesCache.remove(it.id) }
        viewModelScope.launch(Dispatchers.IO) {
            val baseTime = System.currentTimeMillis()
            reorderedNotes.forEachIndexed { index, note ->
                val newTimestamp = baseTime - (index * 1000L)
                if (note.updatedAt != newTimestamp) {
                    repository.updateNoteOrder(note.id, newTimestamp)
                }
            }
        }
    }

    fun pinSelectedNotes() {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isEmpty()) return
        ids.forEach { latestNotesCache.remove(it) }
        viewModelScope.launch(Dispatchers.IO) {
            val notes = displayedNotes.value.filter { it.id in ids }
            val anyUnpinned = notes.any { !it.isPinned }
            notes.forEach { note ->
                repository.setPinned(note.id, anyUnpinned)
            }
        }
        clearSelection()
    }

    fun archiveSelectedNotes() {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isEmpty()) return
        ids.forEach { latestNotesCache.remove(it) }
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id -> repository.setArchived(id, true) }
        }
        clearSelection()
    }

    fun unarchiveSelectedNotes() {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isEmpty()) return
        ids.forEach { latestNotesCache.remove(it) }
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id -> repository.setArchived(id, false) }
        }
        clearSelection()
    }

    fun trashSelectedNotes() {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isEmpty()) return
        ids.forEach { latestNotesCache.remove(it) }
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id -> repository.moveToTrash(id) }
        }
        clearSelection()
    }

    fun restoreSelectedNotes() {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isEmpty()) return
        ids.forEach { latestNotesCache.remove(it) }
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id -> repository.restoreFromTrash(id) }
        }
        clearSelection()
    }

    fun deleteSelectedNotesPermanently() {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isEmpty()) return
        ids.forEach { latestNotesCache.remove(it) }
        val notes = displayedNotes.value.filter { it.id in ids }
        viewModelScope.launch(Dispatchers.IO) {
            notes.forEach { note ->
                deleteNoteMediaFiles(note)
                repository.deletePermanently(note)
            }
        }
        clearSelection()
    }

    fun duplicateSelectedNotes() {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isEmpty()) return
        val notes = displayedNotes.value.filter { it.id in ids }
        if (notes.isEmpty()) return
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            notes.forEach { note ->
                val checklistJson = if (note.isChecklist) {
                    val items = note.getChecklistItems().map { it.copy(id = UUID.randomUUID().toString()) }
                    ChecklistItem.listToJson(items)
                } else {
                    note.checklistJson
                }
                val newImages = note.getImageUris().mapNotNull { path ->
                    NoteImageHelper.copyImageFile(context, path) ?: path
                }
                val newAudios = note.getAudioUris().mapNotNull { path ->
                    AudioMemoManager.copyAudioFile(context, path) ?: path
                }
                repository.saveNote(
                    note.copy(
                        id = 0,
                        title = if (note.title.isNotBlank()) "${note.title} (Copy)" else "",
                        checklistJson = checklistJson,
                        imageUrisJson = JSONArray(newImages).toString(),
                        audioUrisJson = JSONArray(newAudios).toString(),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        clearSelection()
    }

    fun toggleLabelForSelectedNotes(label: String) {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isEmpty()) return
        val notes = displayedNotes.value.filter { it.id in ids }
        viewModelScope.launch(Dispatchers.IO) {
            notes.forEach { note ->
                val currentLabels = note.getLabels().toMutableSet()
                if (currentLabels.contains(label)) {
                    currentLabels.remove(label)
                } else {
                    currentLabels.add(label)
                }
                val updated = note.copy(
                    labelsJson = org.json.JSONArray(currentLabels.toList()).toString(),
                    updatedAt = System.currentTimeMillis()
                )
                repository.saveNote(updated)
                latestNotesCache[updated.id] = updated
            }
            try {
                com.focusbyrj.app.widget.NoteWidgetProvider.updateAllWidgets(getApplication())
            } catch (_: Exception) {}
        }
    }

    fun setSelectedNotesColor(colorKey: String) {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isEmpty()) return
        ids.forEach { latestNotesCache.remove(it) }
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id -> repository.setColor(id, colorKey) }
        }
        clearSelection()
    }

    // Grid Layout Mode (2-column masonry grid vs 1-column list)
    private val _isGridView = MutableStateFlow(prefs.getBoolean("is_grid_view", true))
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    fun toggleLayoutMode() {
        val next = !_isGridView.value
        _isGridView.value = next
        prefs.edit().putBoolean("is_grid_view", next).apply()
    }

    // Search and Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedColorFilter = MutableStateFlow<String?>(null)
    val selectedColorFilter: StateFlow<String?> = _selectedColorFilter.asStateFlow()

    private val _selectedLabelFilter = MutableStateFlow<String?>(null)
    val selectedLabelFilter: StateFlow<String?> = _selectedLabelFilter.asStateFlow()

    private val _customLabels = MutableStateFlow<Set<String>>(
        prefs.getStringSet("custom_labels", emptySet()) ?: emptySet()
    )

    fun addCustomLabel(label: String) {
        val trimmed = label.trim()
        if (trimmed.isBlank()) return
        val updated = _customLabels.value + trimmed
        _customLabels.value = updated
        prefs.edit().putStringSet("custom_labels", updated).apply()
    }

    fun renameCustomLabel(oldLabel: String, newLabel: String) {
        val trimmedOld = oldLabel.trim()
        val trimmedNew = newLabel.trim()
        if (trimmedNew.isBlank() || trimmedOld.equals(trimmedNew, ignoreCase = true)) return
        val updated = _customLabels.value.map { if (it.equals(trimmedOld, ignoreCase = true)) trimmedNew else it }.toSet()
        _customLabels.value = updated
        prefs.edit().putStringSet("custom_labels", updated).apply()
        if (_selectedLabelFilter.value.equals(trimmedOld, ignoreCase = true)) {
            _selectedLabelFilter.value = trimmedNew
        }
        latestNotesCache.clear()
        viewModelScope.launch(Dispatchers.IO) {
            repository.renameLabel(trimmedOld, trimmedNew)
            try {
                com.focusbyrj.app.widget.NoteWidgetProvider.updateAllWidgets(getApplication())
            } catch (_: Exception) {}
        }
    }

    fun deleteCustomLabel(label: String) {
        val trimmed = label.trim()
        val updated = _customLabels.value.filter { !it.equals(trimmed, ignoreCase = true) }.toSet()
        _customLabels.value = updated
        prefs.edit().putStringSet("custom_labels", updated).apply()
        if (_selectedLabelFilter.value.equals(trimmed, ignoreCase = true)) {
            _selectedLabelFilter.value = null
        }
        latestNotesCache.clear()
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteLabel(trimmed)
            try {
                com.focusbyrj.app.widget.NoteWidgetProvider.updateAllWidgets(getApplication())
            } catch (_: Exception) {}
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setColorFilter(colorKey: String?) {
        _selectedColorFilter.value = if (_selectedColorFilter.value == colorKey) null else colorKey
    }

    fun setLabelFilter(label: String?) {
        _selectedLabelFilter.value = if (_selectedLabelFilter.value == label) null else label
    }

    // Notes stream based on folder and multi-token ranked search query
    private val rawNotesFlow = combine(_currentFolder, _searchQuery, _isVaultUnlocked) { folder, query, isUnlocked ->
        Triple(folder, query, isUnlocked)
    }.flatMapLatest { (folder, query, isUnlocked) ->
        val folderFlow = when (folder) {
            NoteFolder.NOTES -> repository.getActiveNotes()
            NoteFolder.ARCHIVE -> {
                val status = ArchiveVaultSecurity.getVaultStatus(getApplication())
                if (status == ArchiveVaultSecurity.VaultStatus.ENABLED && !isUnlocked) {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                } else {
                    repository.getArchivedNotes()
                }
            }
            NoteFolder.TRASH -> repository.getTrashedNotes()
        }
        if (query.isBlank()) {
            folderFlow
        } else {
            folderFlow.map { notes ->
                val tokens = query.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
                if (tokens.isEmpty()) return@map notes

                notes.mapNotNull { note ->
                    val titleLower = note.title.lowercase()
                    val contentLower = note.content.lowercase()
                    val labels = note.getLabels().map { it.lowercase() }
                    val checklistItems = if (note.isChecklist) note.getChecklistItems().map { it.text.lowercase() } else emptyList()

                    // Check if all search tokens match somewhere in the note (title, content, labels, checklist)
                    val allTokensMatch = tokens.all { token ->
                        titleLower.contains(token) ||
                        contentLower.contains(token) ||
                        labels.any { it.contains(token) } ||
                        checklistItems.any { it.contains(token) }
                    }

                    if (!allTokensMatch) null
                    else {
                        // Compute smart relevance score
                        var score = 0
                        tokens.forEach { token ->
                            if (titleLower.contains(token)) score += 12
                            if (titleLower.startsWith(token)) score += 8
                            if (labels.any { it.equals(token, ignoreCase = true) }) score += 10
                            if (labels.any { it.contains(token) }) score += 5
                            if (checklistItems.any { it.contains(token) }) score += 4
                            if (contentLower.contains(token)) score += 2
                        }
                        Pair(note, score)
                    }
                }
                .sortedWith(
                    compareByDescending<Pair<NoteEntity, Int>> { it.first.isPinned }
                        .thenByDescending { it.second }
                        .thenByDescending { it.first.updatedAt }
                )
                .map { it.first }
            }
        }
    }

    val displayedNotes: StateFlow<List<NoteEntity>> = combine(
        rawNotesFlow,
        _currentFolder,
        _selectedColorFilter,
        _selectedLabelFilter
    ) { notes, _, colorFilter, labelFilter ->
        notes.filter { note ->
            val matchesColor = colorFilter == null || note.colorKey.equals(colorFilter, ignoreCase = true)
            val matchesLabel = labelFilter == null || note.getLabels().any { it.equals(labelFilter, ignoreCase = true) }
            matchesColor && matchesLabel
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // All available labels across all notes & custom labels
    val allLabels: StateFlow<List<String>> = combine(
        repository.getActiveNotes(),
        repository.getArchivedNotes(),
        _customLabels
    ) { active, archived, custom ->
        val set = linkedSetOf<String>()
        custom.forEach { if (it.isNotBlank()) set.add(it) }
        active.forEach { note -> set.addAll(note.getLabels()) }
        archived.forEach { note -> set.addAll(note.getLabels()) }
        set.toList().sorted()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ==========================================
    // EDITOR STATE (Live editing in full-screen)
    // ==========================================
    data class EditingNoteState(
        val originalId: Long = 0L,
        val title: String = "",
        val content: String = "",
        val isChecklist: Boolean = false,
        val checklistItems: List<ChecklistItem> = emptyList(),
        val colorKey: String = "default",
        val fontKey: String = "default",
        val isPinned: Boolean = false,
        val isArchived: Boolean = false,
        val isTrashed: Boolean = false,
        val labels: List<String> = emptyList(),
        val imageUris: List<String> = emptyList(),
        val audioUris: List<String> = emptyList(),
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    )

    private data class UndoSnapshot(
        val title: String,
        val content: String,
        val isChecklist: Boolean,
        val checklistItems: List<ChecklistItem>
    )

    private val undoStack = java.util.ArrayDeque<UndoSnapshot>()
    private val redoStack = java.util.ArrayDeque<UndoSnapshot>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private fun resetUndoHistory() {
        undoStack.clear()
        redoStack.clear()
        _canUndo.value = false
        _canRedo.value = false
    }

    private fun pushUndoSnapshot() {
        val current = _editingState.value ?: return
        undoStack.push(
            UndoSnapshot(
                title = current.title,
                content = current.content,
                isChecklist = current.isChecklist,
                checklistItems = current.checklistItems.map { it.copy() }
            )
        )
        if (undoStack.size > 25) {
            undoStack.removeLast()
        }
        redoStack.clear()
        _canUndo.value = true
        _canRedo.value = false
    }

    fun undo() {
        val current = _editingState.value ?: return
        if (undoStack.isEmpty()) return
        redoStack.push(
            UndoSnapshot(
                title = current.title,
                content = current.content,
                isChecklist = current.isChecklist,
                checklistItems = current.checklistItems.map { it.copy() }
            )
        )
        val snap = undoStack.pop()
        _editingState.value = current.copy(
            title = snap.title,
            content = snap.content,
            isChecklist = snap.isChecklist,
            checklistItems = snap.checklistItems,
            updatedAt = System.currentTimeMillis()
        )
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = true
        persistCurrentEditorState()
    }

    fun redo() {
        val current = _editingState.value ?: return
        if (redoStack.isEmpty()) return
        undoStack.push(
            UndoSnapshot(
                title = current.title,
                content = current.content,
                isChecklist = current.isChecklist,
                checklistItems = current.checklistItems.map { it.copy() }
            )
        )
        val snap = redoStack.pop()
        _editingState.value = current.copy(
            title = snap.title,
            content = snap.content,
            isChecklist = snap.isChecklist,
            checklistItems = snap.checklistItems,
            updatedAt = System.currentTimeMillis()
        )
        _canUndo.value = true
        _canRedo.value = redoStack.isNotEmpty()
        persistCurrentEditorState()
    }

    private val _editingState = MutableStateFlow<EditingNoteState?>(null)
    val editingState: StateFlow<EditingNoteState?> = _editingState.asStateFlow()
    private var initialSnapshot: EditingNoteState? = null

    private fun isNoteModified(current: EditingNoteState, initial: EditingNoteState?): Boolean {
        if (initial == null) return true
        if (current.originalId == 0L) return true
        return current.title != initial.title ||
                current.content != initial.content ||
                current.isChecklist != initial.isChecklist ||
                current.checklistItems != initial.checklistItems ||
                current.colorKey != initial.colorKey ||
                current.fontKey != initial.fontKey ||
                current.isPinned != initial.isPinned ||
                current.isArchived != initial.isArchived ||
                current.isTrashed != initial.isTrashed ||
                current.labels != initial.labels ||
                current.imageUris != initial.imageUris ||
                current.audioUris != initial.audioUris
    }

    fun openNewNote(asChecklist: Boolean = false) {
        audioMemoManager.stopPlayback()
        audioMemoManager.cancelRecording()
        autoSaveJob?.cancel()
        resetUndoHistory()
        initialSnapshot = null
        val initialItems = if (asChecklist) listOf(ChecklistItem(text = "", isChecked = false)) else emptyList()
        _editingState.value = EditingNoteState(
            originalId = 0L,
            title = "",
            content = "",
            isChecklist = asChecklist,
            checklistItems = initialItems,
            colorKey = "default",
            fontKey = "default",
            isPinned = false,
            isArchived = false,
            isTrashed = false
        )
    }

    fun openExistingNote(note: NoteEntity) {
        audioMemoManager.stopPlayback()
        audioMemoManager.cancelRecording()
        autoSaveJob?.cancel()
        resetUndoHistory()
        val cached = latestNotesCache[note.id]
        val resolvedNote = if (cached != null && cached.updatedAt >= note.updatedAt) cached else note

        val state = EditingNoteState(
            originalId = resolvedNote.id,
            title = resolvedNote.title,
            content = resolvedNote.content,
            isChecklist = resolvedNote.isChecklist,
            checklistItems = resolvedNote.getChecklistItems(),
            colorKey = resolvedNote.colorKey,
            fontKey = resolvedNote.fontKey,
            isPinned = resolvedNote.isPinned,
            isArchived = resolvedNote.isArchived,
            isTrashed = resolvedNote.isTrashed,
            labels = resolvedNote.getLabels(),
            imageUris = resolvedNote.getImageUris(),
            audioUris = resolvedNote.getAudioUris(),
            createdAt = resolvedNote.createdAt,
            updatedAt = resolvedNote.updatedAt
        )
        _editingState.value = state
        initialSnapshot = state

        if (resolvedNote.id != 0L) {
            viewModelScope.launch(Dispatchers.IO) {
                val fresh = repository.getNoteByIdSync(resolvedNote.id)
                if (fresh != null && fresh.updatedAt > resolvedNote.updatedAt) {
                    latestNotesCache[fresh.id] = fresh
                    withContext(Dispatchers.Main) {
                        val curr = _editingState.value
                        if (curr != null && curr.originalId == fresh.id) {
                            val freshState = curr.copy(
                                title = fresh.title,
                                content = fresh.content,
                                isChecklist = fresh.isChecklist,
                                checklistItems = fresh.getChecklistItems(),
                                colorKey = fresh.colorKey,
                                fontKey = fresh.fontKey,
                                isPinned = fresh.isPinned,
                                isArchived = fresh.isArchived,
                                isTrashed = fresh.isTrashed,
                                labels = fresh.getLabels(),
                                imageUris = fresh.getImageUris(),
                                audioUris = fresh.getAudioUris(),
                                updatedAt = fresh.updatedAt
                            )
                            _editingState.value = freshState
                            initialSnapshot = freshState
                        }
                    }
                }
            }
        }
    }

    fun openNoteById(noteId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val fresh = repository.getNoteByIdSync(noteId)
            val cached = latestNotesCache[noteId]
            val noteToOpen = when {
                fresh != null && cached != null -> if (cached.updatedAt >= fresh.updatedAt) cached else fresh
                fresh != null -> fresh
                cached != null -> cached
                else -> null
            }
            if (noteToOpen != null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    openExistingNote(noteToOpen)
                }
            }
        }
    }

    private fun processAndSaveImage(context: Context, contentUri: android.net.Uri): String? {
        return com.focusbyrj.app.data.note.NoteImageHelper.processAndSaveImage(context, contentUri)
    }

    fun addImageUriToEditor(contentUri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val localPath = processAndSaveImage(context, contentUri) ?: return@launch
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                val current = _editingState.value ?: return@withContext
                val updated = current.imageUris + localPath
                _editingState.value = current.copy(imageUris = updated, updatedAt = System.currentTimeMillis())
                persistCurrentEditorState()
            }
        }
    }

    fun addDrawingToEditor(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val imagesDir = java.io.File(context.filesDir, "keep_images").apply { if (!exists()) mkdirs() }
                val file = java.io.File(imagesDir, "sketch_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.png")
                file.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                val localPath = file.absolutePath
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val current = _editingState.value ?: return@withContext
                    val updated = current.imageUris + localPath
                    _editingState.value = current.copy(imageUris = updated, updatedAt = System.currentTimeMillis())
                    persistCurrentEditorState()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeImageFromEditor(imageUri: String) {
        val current = _editingState.value ?: return
        val updated = current.imageUris.filter { it != imageUri }
        _editingState.value = current.copy(imageUris = updated, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState()
        NoteMediaManager.secureDeleteMediaFile(imageUri)
    }

    fun updateEditorTitle(title: String) {
        pushUndoSnapshot()
        _editingState.value = _editingState.value?.copy(title = title, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState()
    }

    fun updateEditorContent(content: String) {
        pushUndoSnapshot()
        _editingState.value = _editingState.value?.copy(content = content, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState()
    }

    fun toggleEditorPin() {
        val current = _editingState.value ?: return
        _editingState.value = current.copy(isPinned = !current.isPinned, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState()
    }

    fun updateEditorColor(colorKey: String) {
        _editingState.value = _editingState.value?.copy(colorKey = colorKey, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState()
    }

    fun updateEditorFont(fontKey: String) {
        _editingState.value = _editingState.value?.copy(fontKey = fontKey, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState()
    }

    fun toggleChecklistItem(index: Int) {
        val current = _editingState.value ?: return
        if (index !in current.checklistItems.indices) return
        pushUndoSnapshot()
        val updated = current.checklistItems.toMutableList()
        val item = updated[index]
        updated[index] = item.copy(isChecked = !item.isChecked)
        _editingState.value = current.copy(checklistItems = updated, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState(immediate = true)
    }

    fun updateChecklistItemText(index: Int, text: String) {
        val current = _editingState.value ?: return
        if (index !in current.checklistItems.indices) return
        val updated = current.checklistItems.toMutableList()
        updated[index] = updated[index].copy(text = text)
        _editingState.value = current.copy(checklistItems = updated, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState(immediate = false)
    }

    fun addChecklistItem(afterIndex: Int? = null, initialText: String = "", customId: String? = null): String {
        val current = _editingState.value ?: return ""
        pushUndoSnapshot()
        val updated = current.checklistItems.toMutableList()
        val itemId = customId ?: UUID.randomUUID().toString()
        val newItem = ChecklistItem(id = itemId, text = initialText, isChecked = false)
        if (afterIndex != null && afterIndex in updated.indices) {
            updated.add(afterIndex + 1, newItem)
        } else {
            val firstChecked = updated.indexOfFirst { it.isChecked }
            if (firstChecked != -1) {
                updated.add(firstChecked, newItem)
            } else {
                updated.add(newItem)
            }
        }
        _editingState.value = current.copy(checklistItems = updated, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState(immediate = true)
        return itemId
    }

    fun removeChecklistItem(index: Int) {
        val current = _editingState.value ?: return
        if (index !in current.checklistItems.indices) return
        pushUndoSnapshot()
        val updated = current.checklistItems.toMutableList()
        updated.removeAt(index)
        if (updated.isEmpty()) {
            updated.add(ChecklistItem(text = "", isChecked = false))
        }
        _editingState.value = current.copy(checklistItems = updated, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState(immediate = true)
    }

    fun toggleChecklistMode() {
        val current = _editingState.value ?: return
        pushUndoSnapshot()
        if (current.isChecklist) {
            // Convert checklist to plain text
            val text = current.checklistItems.joinToString("\n") { it.text }
            _editingState.value = current.copy(
                isChecklist = false,
                content = text,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            // Convert plain text to checklist
            val lines = current.content.lines().filter { it.isNotBlank() }
            val items = if (lines.isEmpty()) {
                listOf(ChecklistItem(text = "", isChecked = false))
            } else {
                lines.map { ChecklistItem(text = it, isChecked = false) }
            }
            _editingState.value = current.copy(
                isChecklist = true,
                checklistItems = items,
                updatedAt = System.currentTimeMillis()
            )
        }
        persistCurrentEditorState()
    }

    fun duplicateCurrentNote() {
        val current = _editingState.value ?: return
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val copyTitle = if (current.title.isBlank()) "Note (copy)" else "${current.title} (copy)"
            val newImages = current.imageUris.mapNotNull { path ->
                NoteImageHelper.copyImageFile(context, path) ?: path
            }
            val newAudios = current.audioUris.mapNotNull { path ->
                AudioMemoManager.copyAudioFile(context, path) ?: path
            }
            val newChecklistItems = current.checklistItems.map { it.copy(id = UUID.randomUUID().toString()) }
            val newNote = NoteEntity(
                title = copyTitle,
                content = current.content,
                isChecklist = current.isChecklist,
                checklistJson = ChecklistItem.listToJson(newChecklistItems),
                colorKey = current.colorKey,
                fontKey = current.fontKey,
                isPinned = false,
                isArchived = false,
                isTrashed = false,
                labelsJson = JSONArray(current.labels).toString(),
                imageUrisJson = JSONArray(newImages).toString(),
                audioUrisJson = JSONArray(newAudios).toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val newId = repository.saveNote(newNote)
            val savedEntity = newNote.copy(id = newId)
            latestNotesCache[newId] = savedEntity
            _editingState.value = current.copy(
                originalId = newId,
                title = copyTitle,
                checklistItems = newChecklistItems,
                imageUris = newImages,
                audioUris = newAudios,
                isPinned = false,
                isArchived = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            resetUndoHistory()
        }
    }

    fun shareCurrentNote(context: Context) {
        val current = _editingState.value ?: return
        val title = current.title.trim()
        val body = if (current.isChecklist) {
            current.checklistItems.joinToString("\n") { item ->
                val box = if (item.isChecked) "☑" else "☐"
                "$box ${item.text}"
            }
        } else {
            current.content.trim()
        }
        val fullText = buildString {
            if (title.isNotEmpty()) {
                appendLine(title)
                appendLine()
            }
            append(body)
        }.trim()

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title.ifEmpty { "Shared Note" })
            putExtra(Intent.EXTRA_TEXT, fullText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, "Share note via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun copyCurrentNoteToClipboard(context: Context) {
        val current = _editingState.value ?: return
        val title = current.title.trim()
        val body = if (current.isChecklist) {
            current.checklistItems.joinToString("\n") { item ->
                val box = if (item.isChecked) "☑" else "☐"
                "$box ${item.text}"
            }
        } else {
            current.content.trim()
        }
        val fullText = buildString {
            if (title.isNotEmpty()) {
                appendLine(title)
                appendLine()
            }
            append(body)
        }.trim()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Note", fullText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Note copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun shareSelectedNotes(context: Context) {
        val selectedIds = _selectedNoteIds.value
        if (selectedIds.isEmpty()) return
        val notes = displayedNotes.value.filter { it.id in selectedIds }
        if (notes.isEmpty()) return

        val fullText = notes.joinToString("\n\n---\n\n") { note ->
            buildString {
                if (note.title.isNotBlank()) {
                    appendLine("📌 ${note.title.trim()}")
                }
                if (note.isChecklist) {
                    val items = note.getChecklistItems()
                    items.forEach { item ->
                        val box = if (item.isChecked) "☑" else "☐"
                        appendLine("$box ${item.text}")
                    }
                } else if (note.content.isNotBlank()) {
                    appendLine(note.content.trim())
                }
            }.trim()
        }.trim()

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, if (notes.size == 1) notes.first().title.ifBlank { "Shared Note" } else "${notes.size} Notes")
            putExtra(Intent.EXTRA_TEXT, fullText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, "Share notes via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun copySelectedNotesToClipboard(context: Context) {
        val selectedIds = _selectedNoteIds.value
        if (selectedIds.isEmpty()) return
        val notes = displayedNotes.value.filter { it.id in selectedIds }
        if (notes.isEmpty()) return

        val fullText = notes.joinToString("\n\n---\n\n") { note ->
            buildString {
                if (note.title.isNotBlank()) {
                    appendLine("📌 ${note.title.trim()}")
                }
                if (note.isChecklist) {
                    val items = note.getChecklistItems()
                    items.forEach { item ->
                        val box = if (item.isChecked) "☑" else "☐"
                        appendLine("$box ${item.text}")
                    }
                } else if (note.content.isNotBlank()) {
                    appendLine(note.content.trim())
                }
            }.trim()
        }.trim()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Notes", fullText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "${notes.size} note${if (notes.size > 1) "s" else ""} copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    // ==========================================
    // AUDIO RECORDING & PLAYBACK
    // ==========================================
    val audioMemoManager = AudioMemoManager(getApplication<Application>())
    val recordingState = audioMemoManager.recordingState
    val playbackState = audioMemoManager.playbackState

    fun startVoiceRecording() {
        audioMemoManager.startRecording()
    }

    fun stopVoiceRecordingAndAttach() {
        val (audioPath, transcript) = audioMemoManager.stopRecording()
        val current = _editingState.value
        if (current == null) {
            // Started voice memo from main screen FAB
            openNewNote(asChecklist = false)
            val updated = _editingState.value ?: return
            val audios = if (audioPath != null) listOf(audioPath) else emptyList()
            _editingState.value = updated.copy(
                content = transcript.trim(),
                audioUris = audios,
                updatedAt = System.currentTimeMillis()
            )
            persistCurrentEditorState()
        } else {
            // In note editor
            val updatedAudios = if (audioPath != null) current.audioUris + audioPath else current.audioUris
            if (current.isChecklist && transcript.isNotBlank()) {
                val newItems = current.checklistItems.toMutableList()
                newItems.add(ChecklistItem(id = UUID.randomUUID().toString(), text = transcript.trim(), isChecked = false))
                _editingState.value = current.copy(
                    checklistItems = newItems,
                    audioUris = updatedAudios,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                val newContent = if (current.content.isBlank()) {
                    transcript.trim()
                } else if (transcript.isNotBlank()) {
                    "${current.content}\n${transcript.trim()}"
                } else {
                    current.content
                }
                _editingState.value = current.copy(
                    content = newContent,
                    audioUris = updatedAudios,
                    updatedAt = System.currentTimeMillis()
                )
            }
            persistCurrentEditorState()
        }
    }

    fun cancelVoiceRecording() {
        audioMemoManager.cancelRecording()
    }

    fun removeAudioFromEditor(audioUri: String) {
        val current = _editingState.value ?: return
        val updated = current.audioUris.filter { it != audioUri }
        _editingState.value = current.copy(audioUris = updated, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState()
        if (playbackState.value.currentPath == audioUri) {
            audioMemoManager.stopPlayback()
        }
        NoteMediaManager.secureDeleteMediaFile(audioUri)
    }

    fun toggleAudioPlayback(audioUri: String) {
        audioMemoManager.playOrToggle(audioUri)
    }

    fun seekAudio(positionMs: Int) {
        audioMemoManager.seekTo(positionMs)
    }

    fun setAudioPlaybackSpeed(speed: Float) {
        audioMemoManager.setPlaybackSpeed(speed)
    }

    fun skipAudioPlayback(deltaMs: Int) {
        audioMemoManager.skip(deltaMs)
    }

    fun stopAudioPlayback() {
        audioMemoManager.stopPlayback()
    }

    fun appendVoiceTranscription(transcription: String) {
        val current = _editingState.value ?: return
        if (transcription.isBlank()) return
        pushUndoSnapshot()
        if (current.isChecklist) {
            val updated = current.checklistItems.toMutableList()
            updated.add(ChecklistItem(id = UUID.randomUUID().toString(), text = transcription.trim(), isChecked = false))
            _editingState.value = current.copy(checklistItems = updated, updatedAt = System.currentTimeMillis())
        } else {
            val newContent = if (current.content.isBlank()) {
                transcription.trim()
            } else {
                "${current.content}\n${transcription.trim()}"
            }
            _editingState.value = current.copy(content = newContent, updatedAt = System.currentTimeMillis())
        }
        persistCurrentEditorState()
    }

    fun openNewNoteWithVoice(transcription: String) {
        resetUndoHistory()
        _editingState.value = EditingNoteState(
            originalId = 0L,
            title = "",
            content = transcription.trim(),
            isChecklist = false,
            colorKey = "default",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun addCapturedPhoto(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val imagesDir = java.io.File(context.filesDir, "keep_images").apply { if (!exists()) mkdirs() }
                val file = java.io.File(imagesDir, "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                val localPath = file.absolutePath
                val current = _editingState.value
                if (current != null) {
                    val updated = current.imageUris + localPath
                    _editingState.value = current.copy(imageUris = updated, updatedAt = System.currentTimeMillis())
                    persistCurrentEditorState()
                } else {
                    _editingState.value = EditingNoteState(
                        originalId = 0L,
                        title = "",
                        content = "",
                        imageUris = listOf(localPath),
                        colorKey = "default",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun openNewNoteWithPhoto(bitmap: android.graphics.Bitmap) {
        openNewNote(asChecklist = false)
        addCapturedPhoto(bitmap)
    }

    fun addLabelToEditor(label: String) {
        val trimmed = label.trim()
        if (trimmed.isBlank()) return
        val current = _editingState.value ?: return
        if (!current.labels.contains(trimmed)) {
            val updated = current.labels + trimmed
            _editingState.value = current.copy(labels = updated, updatedAt = System.currentTimeMillis())
            persistCurrentEditorState()
        }
    }

    fun removeLabelFromEditor(label: String) {
        val current = _editingState.value ?: return
        val updated = current.labels.filter { it != label }
        _editingState.value = current.copy(labels = updated, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState()
    }

    fun moveChecklistItem(fromIndex: Int, toIndex: Int) {
        val current = _editingState.value ?: return
        val items = current.checklistItems.toMutableList()
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return
        val item = items.removeAt(fromIndex)
        items.add(toIndex, item)
        _editingState.value = current.copy(checklistItems = items, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState(immediate = true)
    }

    fun restoreNote(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreFromTrash(note.id)
        }
    }

    fun unarchiveNote(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setArchived(note.id, false)
        }
    }

    private fun deleteNoteMediaFiles(note: NoteEntity) {
        NoteMediaManager.deleteNoteMediaFiles(note)
    }

    fun deletePermanently(note: NoteEntity) {
        latestNotesCache.remove(note.id)
        viewModelScope.launch(Dispatchers.IO) {
            deleteNoteMediaFiles(note)
            repository.deletePermanently(note)
        }
    }

    fun emptyTrash() {
        latestNotesCache.clear()
        viewModelScope.launch(Dispatchers.IO) {
            val trashedNotes = repository.getTrashedNotesSync()
            trashedNotes.forEach { note ->
                deleteNoteMediaFiles(note)
            }
            repository.emptyTrash()
        }
    }

    fun deleteCurrentNote() {
        audioMemoManager.stopPlayback()
        audioMemoManager.cancelRecording()
        autoSaveJob?.cancel()
        val current = _editingState.value ?: return
        _editingState.value = null
        if (current.originalId != 0L) {
            latestNotesCache.remove(current.originalId)
            viewModelScope.launch(Dispatchers.IO) {
                persistMutex.withLock {
                    repository.moveToTrash(current.originalId)
                }
            }
        }
    }

    fun archiveCurrentNote() {
        audioMemoManager.stopPlayback()
        audioMemoManager.cancelRecording()
        autoSaveJob?.cancel()
        val current = _editingState.value ?: return
        _editingState.value = null
        if (current.originalId != 0L) {
            latestNotesCache.remove(current.originalId)
            viewModelScope.launch(Dispatchers.IO) {
                persistMutex.withLock {
                    repository.setArchived(current.originalId, true)
                }
            }
        }
    }

    fun openNewNoteWithDrawing(bitmap: android.graphics.Bitmap) {
        openNewNote(asChecklist = false)
        addDrawingToEditor(bitmap)
    }

    fun openNewNoteWithImages(uris: List<android.net.Uri>) {
        openNewNote(asChecklist = false)
        uris.forEach { uri ->
            addImageUriToEditor(uri)
        }
    }

    fun closeEditor() {
        audioMemoManager.stopPlayback()
        audioMemoManager.cancelRecording()
        autoSaveJob?.cancel()
        val current = _editingState.value ?: return
        _editingState.value = null

        val initial = initialSnapshot
        initialSnapshot = null
        val modified = isNoteModified(current, initial)

        // If existing note is unmodified, do NOT overwrite timestamp or re-save
        if (!modified && current.originalId != 0L) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            persistMutex.withLock {
                val entity = buildEntityFromState(current, isModified = modified)
                if (entity.isEmptyNote()) {
                    if (entity.id != 0L) {
                        latestNotesCache.remove(entity.id)
                        deleteNoteMediaFiles(entity)
                        repository.deletePermanently(entity)
                    }
                } else {
                    val savedId = repository.saveNote(entity)
                    val finalEntity = if (entity.id == 0L) entity.copy(id = savedId) else entity
                    latestNotesCache[savedId] = finalEntity
                }
            }
        }
    }

    private fun persistCurrentEditorState(immediate: Boolean = false) {
        val current = _editingState.value ?: return
        val isModified = isNoteModified(current, initialSnapshot)
        if (!isModified && current.originalId != 0L) return

        val entityCandidate = buildEntityFromState(current, isModified = true)
        if (entityCandidate.isEmptyNote() && current.originalId == 0L) return

        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            if (!immediate) {
                delay(300L)
            }
            persistMutex.withLock {
                val latest = _editingState.value ?: return@withLock
                val latestModified = isNoteModified(latest, initialSnapshot)
                if (!latestModified && latest.originalId != 0L) return@withLock

                val entity = buildEntityFromState(latest, isModified = true)
                if (entity.isEmptyNote() && latest.originalId == 0L) return@withLock

                val savedId = repository.saveNote(entity)
                val finalEntity = if (entity.id == 0L) entity.copy(id = savedId) else entity
                latestNotesCache[savedId] = finalEntity

                if (latest.originalId == 0L && savedId != 0L) {
                    withContext(Dispatchers.Main) {
                        _editingState.value = _editingState.value?.copy(originalId = savedId)
                    }
                }
            }
        }
    }

    private fun buildEntityFromState(state: EditingNoteState, isModified: Boolean = true): NoteEntity {
        val labelsArray = JSONArray()
        state.labels.forEach { labelsArray.put(it) }

        val imagesArray = JSONArray()
        state.imageUris.forEach { imagesArray.put(it) }

        val audiosArray = JSONArray()
        state.audioUris.forEach { audiosArray.put(it) }

        val finalUpdatedAt = if (isModified || state.originalId == 0L) {
            if (state.updatedAt > state.createdAt) state.updatedAt else System.currentTimeMillis()
        } else {
            state.updatedAt
        }

        return NoteEntity(
            id = state.originalId,
            title = state.title.trim(),
            content = state.content,
            isChecklist = state.isChecklist,
            checklistJson = if (state.isChecklist) ChecklistItem.listToJson(state.checklistItems) else "[]",
            colorKey = state.colorKey,
            fontKey = state.fontKey,
            isPinned = state.isPinned,
            isArchived = state.isArchived,
            isTrashed = state.isTrashed,
            labelsJson = labelsArray.toString(),
            imageUrisJson = imagesArray.toString(),
            audioUrisJson = audiosArray.toString(),
            createdAt = state.createdAt,
            updatedAt = finalUpdatedAt
        )
    }

    // Direct card quick actions
    fun togglePin(note: NoteEntity) {
        latestNotesCache.remove(note.id)
        viewModelScope.launch(Dispatchers.IO) {
            repository.togglePin(note.id, note.isPinned)
        }
    }

    fun archiveNote(note: NoteEntity) {
        latestNotesCache.remove(note.id)
        viewModelScope.launch(Dispatchers.IO) {
            repository.setArchived(note.id, true)
        }
    }

    fun trashNote(note: NoteEntity) {
        latestNotesCache.remove(note.id)
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveToTrash(note.id)
        }
    }

    fun setNoteColor(note: NoteEntity, colorKey: String) {
        latestNotesCache.remove(note.id)
        viewModelScope.launch(Dispatchers.IO) {
            repository.setColor(note.id, colorKey)
        }
    }

    fun setNoteFont(note: NoteEntity, fontKey: String) {
        latestNotesCache.remove(note.id)
        viewModelScope.launch(Dispatchers.IO) {
            repository.setFont(note.id, fontKey)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioMemoManager.release()
    }
}
