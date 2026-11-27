/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.notes

import androidx.room.withTransaction
import kotlinx.coroutines.flow.update

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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.util.Log
import com.focusbyrj.app.util.backup.DataSafetyManager
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import com.focusbyrj.app.util.crypto.VaultArchiveDeposit

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

    /**
     * Holds the DB-assigned ID for a new note that has been persisted by [persistCurrentEditorState]
     * but whose [EditingNoteState.originalId] update may not yet have propagated back to Main.
     * [closeEditor] reads this to avoid a second INSERT that would produce a duplicate.
     * Reset to 0L at the start of every new-note session.
     */
    @Volatile private var pendingNewNoteId: Long = 0L
    @Volatile private var pendingVaultDraftInMemory: NoteEntity? = null

    /** Debounce job for undo snapshot pushes during continuous text typing. */
    private var undoPushJob: Job? = null

    // Secret Archive Vault Security State
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    // Navigation return route when note editor closes (e.g. returning to Profile/Account tab)
    private val _returnRouteOnClose = MutableStateFlow<String?>(null)
    val returnRouteOnClose: StateFlow<String?> = _returnRouteOnClose.asStateFlow()

    fun setReturnRouteOnClose(route: String?) {
        _returnRouteOnClose.value = route
    }

    fun consumeReturnRoute(): String? {
        val route = _returnRouteOnClose.value
        _returnRouteOnClose.value = null
        return route
    }

    private val _vaultStatus = MutableStateFlow(ArchiveVaultSecurity.getVaultStatus(application))
    val vaultStatus: StateFlow<ArchiveVaultSecurity.VaultStatus> = _vaultStatus.asStateFlow()
    private val _archiveWithoutPin = MutableStateFlow(false)
    val archiveWithoutPin: StateFlow<Boolean> = _archiveWithoutPin.asStateFlow()
    data class ArchiveOptionState(val busy: Boolean = false, val message: String? = null, val error: Boolean = false)
    private val _archiveOption = MutableStateFlow(ArchiveOptionState())
    val archiveOption: StateFlow<ArchiveOptionState> = _archiveOption.asStateFlow()

    /**
     * Set to a non-null note ID when [VaultPayloadEncryptor.tryDecryptNotePayload] returns
     * [VaultPayloadEncryptor.DecryptionResult.KeyMismatch] for that note.
     * The UI should show a "Re-enter vault PIN" prompt when this is non-null.
     * Reset to null after the user re-unlocks the vault or dismisses the prompt.
     */
    private val _vaultKeyMismatchNoteId = MutableStateFlow<Long?>(null)
    val vaultKeyMismatchNoteId: StateFlow<Long?> = _vaultKeyMismatchNoteId.asStateFlow()

    private val _isRecoveryPhraseBackedUp = MutableStateFlow(false)
    val isRecoveryPhraseBackedUp: StateFlow<Boolean> = _isRecoveryPhraseBackedUp.asStateFlow()

    fun clearVaultKeyMismatch() {
        _vaultKeyMismatchNoteId.value = null
    }

    fun refreshVaultStatus() {
        val status = ArchiveVaultSecurity.getVaultStatus(getApplication())
        _vaultStatus.value = status
        _archiveWithoutPin.value = VaultArchiveDeposit.readCapability(getApplication()) != null
        _isRecoveryPhraseBackedUp.value = ArchiveVaultSecurity.isRecoveryPhraseBackedUp(getApplication())
        _isVaultUnlocked.value = (status == ArchiveVaultSecurity.VaultStatus.ENABLED && !ArchiveVaultSecurity.isVaultLocked(getApplication()))
    }

    fun verifyArchiveOptionPin(pin: String): ArchiveVaultSecurity.VerifyResult =
        ArchiveVaultSecurity.verifyPasscode(getApplication(), pin)

    suspend fun verifyArchiveOptionPinAsync(pin: String): ArchiveVaultSecurity.VerifyResult =
        ArchiveVaultSecurity.verifyPasscodeAsync(getApplication(), pin)

    fun dismissArchiveOptionMessage() {
        if (!_archiveOption.value.busy) _archiveOption.value = ArchiveOptionState()
    }

    fun setArchiveWithoutPin(enabled: Boolean) {
        if (_archiveOption.value.busy) return
        val authorization = try { if (enabled) VaultArchiveDeposit.captureAuthorization(getApplication()) else null }
        catch (error: Exception) {
            _archiveOption.value = ArchiveOptionState(message = error.message ?: "Confirm your vault PIN first.", error = true)
            return
        }
        try { lockVault() } catch (error: Exception) {
            authorization?.close()
            _archiveOption.value = ArchiveOptionState(message = "The vault could not be locked. Your option was not changed.", error = true)
            return
        }
        _archiveOption.value = ArchiveOptionState(busy = true)
        viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    withContext(Dispatchers.IO) {
                        if (enabled) VaultArchiveDeposit.enable(getApplication(), checkNotNull(authorization))
                        else VaultArchiveDeposit.disable(getApplication())
                    }
                    refreshVaultStatus()
                    check(_archiveWithoutPin.value == enabled) { "The archive option could not be verified. Try again." }
                    _archiveOption.value = ArchiveOptionState(message = if (enabled)
                        "Archiving no longer asks for a PIN. Viewing and unarchiving still do."
                        else "Archiving will ask for your PIN.")
                } catch (error: Exception) {
                    _archiveOption.value = ArchiveOptionState(message = error.message ?: "The archive option could not be changed.", error = true)
                } finally {
                    authorization?.close()
                    ArchiveVaultSecurity.lockVault()
                    latestNotesCache.clear()
                    refreshVaultStatus()
                }
            }
        }
    }

    private fun triggerOrphanMediaCleanup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = NoteDatabase.getInstance(getApplication())
                NoteMediaManager.cleanOrphanedMedia(getApplication(), db.noteDao())
            } catch (e: Exception) {
                Log.e("NotesViewModel", "Failed to run orphaned media cleanup", e)
            }
        }
    }

    fun verifyVaultPasscode(pin: String): ArchiveVaultSecurity.VerifyResult =
        acceptVaultVerification(ArchiveVaultSecurity.verifyPasscode(getApplication(), pin))

    suspend fun verifyVaultPasscodeAsync(pin: String): ArchiveVaultSecurity.VerifyResult =
        acceptVaultVerification(ArchiveVaultSecurity.verifyPasscodeAsync(getApplication(), pin))

    private fun acceptVaultVerification(result: ArchiveVaultSecurity.VerifyResult): ArchiveVaultSecurity.VerifyResult {
        if (result is ArchiveVaultSecurity.VerifyResult.Success) {
            _isVaultUnlocked.value = true
            if (_currentFolder.value != NoteFolder.TRASH) _currentFolder.value = NoteFolder.ARCHIVE
            refreshVaultStatus()
            recoverPendingVaultDraft()
            triggerOrphanMediaCleanup()
        }
        return result
    }

    fun setVaultPasscode(
        pin: String,
        mnemonicWords: List<String>? = null,
        isPhraseBackedUp: Boolean = true
    ): Boolean {
        val success = ArchiveVaultSecurity.setPasscode(getApplication(), pin, mnemonicWords, isPhraseBackedUp)
        if (success) {
            _isVaultUnlocked.value = true
            _currentFolder.value = NoteFolder.ARCHIVE
            refreshVaultStatus()
            recoverPendingVaultDraft()
            triggerOrphanMediaCleanup()
        }
        return success
    }

    fun getOrConfigureRecoveryPhrase(): List<String>? {
        val words = ArchiveVaultSecurity.getOrConfigureRecoveryPhrase(getApplication())
        refreshVaultStatus()
        return words
    }

    fun markRecoveryPhraseBackedUp() {
        ArchiveVaultSecurity.markRecoveryPhraseBackedUp(getApplication())
        refreshVaultStatus()
    }

    fun isVaultRecoveryConfigured(): Boolean {
        return ArchiveVaultSecurity.isRecoveryConfigured(getApplication())
    }

    fun recoverVaultWithMnemonic(words: List<String>, newPin: String): Boolean {
        val success = ArchiveVaultSecurity.recoverVaultWithMnemonic(getApplication(), words, newPin)
        if (success) {
            _isVaultUnlocked.value = true
            _currentFolder.value = NoteFolder.ARCHIVE
            refreshVaultStatus()
            recoverPendingVaultDraft()
            triggerOrphanMediaCleanup()
        }
        return success
    }

    fun verifyMnemonicOnly(words: List<String>): Boolean {
        return ArchiveVaultSecurity.verifyMnemonicOnly(getApplication(), words)
    }

    fun skipVaultPasscode() {
        ArchiveVaultSecurity.skipPasscodeSetup(getApplication())
        _isVaultUnlocked.value = true
        _currentFolder.value = NoteFolder.ARCHIVE
        refreshVaultStatus()
        recoverPendingVaultDraft()
    }

    fun disableVaultPasscode(): Boolean {
        val success = synchronized(vaultDraftWriteLock) {
            if (hasPendingVaultDraftFile() || pendingVaultDraftInMemory != null || pendingPlainVaultDraft != null ||
                backgroundVaultSave?.isActive == true) return false
            ArchiveVaultSecurity.disablePasscode(getApplication())
        }
        if (success) {
            refreshVaultStatus()
        }
        return success
    }

    fun lockVault() {
        lockVaultInternal(false)
    }

    internal fun lockVaultForBackground() { lockVaultInternal(true) }

    private fun lockVaultInternal(preserveMediaReturn: Boolean) {
        val current = _editingState.value
        if (current != null && current.isArchived && _archiveMove.value.phase == ArchiveMovePhase.SAVING) {
            pendingPlainVaultDraft = current
            _editingState.value = null
        } else if (current != null && current.isArchived) {
            if (isNoteModified(current, initialSnapshot) || current.originalId == 0L) pendingPlainVaultDraft = current
            autoSaveJob?.cancel()
            undoPushJob?.cancel()
            audioMemoManager.stopPlayback()
            audioMemoManager.cancelRecording()

            val subKey = ArchiveVaultSecurity.getActiveVaultSubKey()
            if (subKey != null) {
                val modified = isNoteModified(current, initialSnapshot)
                if (modified || current.originalId == 0L) {
                    val effectiveId = if (current.originalId == 0L && pendingNewNoteId != 0L) pendingNewNoteId else current.originalId
                    val resolvedCurrent = if (effectiveId != current.originalId) current.copy(originalId = effectiveId) else current
                    val entity = buildEntityFromState(resolvedCurrent, isModified = modified)
                    if (!entity.isEmptyNote()) {
                        try {
                            val encrypted = VaultPayloadEncryptor.encryptNotePayload(entity, vaultSubKey = subKey, context = getApplication())
                            persistPendingVaultDraft(encrypted, requireEmpty = true)
                            pendingPlainVaultDraft = null
                            backgroundVaultSave = viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    persistMutex.withLock { saveEditorEntity(encrypted, resolvedCurrent) }
                                    synchronized(vaultDraftWriteLock) {
                                        androidx.core.util.AtomicFile(pendingVaultDraftFile()).delete()
                                        androidx.core.util.AtomicFile(pendingVaultFallbackFile()).delete()
                                    }
                                } catch (error: Exception) {
                                    Log.e("NotesViewModel", "Encrypted draft retained for recovery", error)
                                }
                            }
                        } catch (e: Exception) {
                            // Keep the failed draft hidden in memory; never discard it on lock.
                            pendingPlainVaultDraft = current
                            _saveStatus.value = NoteSaveStatus.Error("Your vault draft is retained in memory. Unlock and free storage before closing Run.", current)
                            Log.e("NotesViewModel", "Vault draft persistence deferred; hidden draft retained", e)
                        }
                    }
                }
            }
            subKey?.fill(0)
            if (pendingPlainVaultDraft != null) {
                val message = "Vault locked. An unsaved draft is retained in memory; keep Run open, free storage and unlock to recover it."
                _saveStatus.value = NoteSaveStatus.Error(message, pendingPlainVaultDraft)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
                }
            }
            _editingState.value = null
            resetUndoHistory()
            initialSnapshot = null
            pendingNewNoteId = 0L
        }

        if (preserveMediaReturn) ArchiveVaultSecurity.lockForBackground() else ArchiveVaultSecurity.lockVault()
        latestNotesCache.clear()
        _isVaultUnlocked.value = false
        if (_currentFolder.value == NoteFolder.ARCHIVE) {
            _currentFolder.value = NoteFolder.NOTES
        }
        refreshVaultStatus()
    }

    @Volatile private var pendingPlainVaultDraft: EditingNoteState? = null
    private fun pendingVaultFallbackFile(): File = File(getApplication<Application>().filesDir, "pending_vault_draft.vault")
    private fun pendingVaultDraftFile(): File = File(getApplication<Application>().filesDir, "pending_vault_draft.enc")

    internal var pendingDraftWriteHook: (() -> Unit)? = null
    private fun hasPendingVaultDraftFile(): Boolean = listOf(pendingVaultDraftFile(), pendingVaultFallbackFile()).any { file ->
        listOf(file, File(file.path + ".bak"), File(file.path + ".new")).any { it.exists() }
    }

    private fun persistPendingVaultDraft(note: NoteEntity, requireEmpty: Boolean = false) = synchronized(vaultDraftWriteLock) {
        check(!requireEmpty || !hasPendingVaultDraftFile()) { "An earlier private draft was kept. Unlock Archive to recover it before retrying." }
        pendingDraftWriteHook?.invoke()
        val json = JSONObject().apply {
            put("id", note.id); put("title", note.title); put("content", note.content)
            put("isChecklist", note.isChecklist); put("checklistJson", note.checklistJson)
            put("colorKey", note.colorKey); put("fontKey", note.fontKey); put("isPinned", note.isPinned)
            put("isArchived", note.isArchived); put("isTrashed", note.isTrashed); put("labelsJson", note.labelsJson)
            put("imageUrisJson", note.imageUrisJson); put("audioUrisJson", note.audioUrisJson)
            put("createdAt", note.createdAt); put("updatedAt", note.updatedAt)
            put("trashedAt", note.trashedAt ?: JSONObject.NULL); put("deletedAt", note.deletedAt ?: JSONObject.NULL)
        }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        try {
            com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(pendingVaultDraftFile(), bytes)
        } catch (error: Exception) {
            // The payload is already authenticated VDK ciphertext. A device-key outage
            // must not prevent preserving it; no private plaintext is written here.
            check(VaultPayloadEncryptor.isVaultEncrypted(note))
            val atomic = androidx.core.util.AtomicFile(pendingVaultFallbackFile())
            val output = atomic.startWrite()
            try { output.write(bytes); atomic.finishWrite(output) }
            catch (failure: Throwable) { atomic.failWrite(output); throw failure }
        }
    }

    @Volatile private var backgroundVaultSave: kotlinx.coroutines.Job? = null

    private fun recoverPendingVaultDraft() {
        viewModelScope.launch(Dispatchers.IO) {
            backgroundVaultSave?.join()
            val file = pendingVaultDraftFile()
            if (!hasPendingVaultDraftFile() && pendingVaultDraftInMemory == null && pendingPlainVaultDraft == null) return@launch
            try {
                val memoryDraft = pendingVaultDraftInMemory ?: pendingPlainVaultDraft?.let {
                    VaultPayloadEncryptor.encryptNotePayload(buildEntityFromState(it), context = getApplication())
                }
                val note = memoryDraft ?: run {
                    // Recover an interrupted AtomicFile replacement before authenticating its bytes.
                    val bytes = runCatching {
                        androidx.core.util.AtomicFile(file).openRead().close()
                        com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(file)
                    }.getOrNull() ?: runCatching {
                        androidx.core.util.AtomicFile(pendingVaultFallbackFile()).openRead().use { it.readBytes() }
                    }.getOrNull() ?: throw java.io.IOException("Pending vault draft could not be decrypted")
                    val obj = JSONObject(bytes.toString(Charsets.UTF_8))
                    NoteEntity(
                    id = obj.optLong("id", 0L), title = obj.optString("title", ""), content = obj.optString("content", ""),
                    isChecklist = obj.optBoolean("isChecklist"), checklistJson = obj.optString("checklistJson", "[]"),
                    colorKey = obj.optString("colorKey", "default"), fontKey = obj.optString("fontKey", "default"),
                    isPinned = obj.optBoolean("isPinned"), isArchived = true, isTrashed = obj.optBoolean("isTrashed"),
                    labelsJson = obj.optString("labelsJson", "[]"), imageUrisJson = obj.optString("imageUrisJson", "[]"),
                    audioUrisJson = obj.optString("audioUrisJson", "[]"), createdAt = obj.optLong("createdAt"),
                    updatedAt = obj.optLong("updatedAt"),
                    trashedAt = if (obj.isNull("trashedAt")) null else obj.optLong("trashedAt"),
                    deletedAt = if (obj.isNull("deletedAt")) null else obj.optLong("deletedAt")
                    )
                }
                check(VaultPayloadEncryptor.isVaultEncrypted(note)) { "Unprotected recovery payload was rejected" }
                // Recovery never overwrites edits made since the failed save. Identical
                // ciphertext makes retry after an interrupted insert idempotent.
                val present = repository.getAllNotesListOrThrow().any { it.copy(id = 0) == note.copy(id = 0) }
                if (!present) repository.saveNote(note.copy(id = 0))
                pendingVaultDraftInMemory = null
                pendingPlainVaultDraft = null
                androidx.core.util.AtomicFile(file).delete()
                androidx.core.util.AtomicFile(pendingVaultFallbackFile()).delete()
            } catch (e: Exception) {
                Log.e("NotesViewModel", "Failed to recover pending vault draft; retaining it for retry", e)
            }
        }
    }

    fun getRemainingLockoutSeconds(): Long {
        return ArchiveVaultSecurity.getRemainingLockoutSeconds(getApplication())
    }

    companion object {
        private val vaultDraftWriteLock = Any()
        val latestNotesCache: MutableMap<Long, NoteEntity> =
            com.focusbyrj.app.data.note.LatestNotesCache.entries
    }

    private val vaultPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshVaultStatus()
    }

    private val displayReads = NoteReadMonitor()
    private val labelReads = NoteReadMonitor()
    private val widgetReads = NoteReadMonitor()
    val noteReadFailed = combine(displayReads.error, labelReads.error, widgetReads.error) { display, labels, widgets ->
        display || labels || widgets
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun retryNoteReads() {
        displayReads.retry()
        labelReads.retry()
        widgetReads.retry()
    }

    init {
        try {
            val vaultPrefs = application.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, Context.MODE_PRIVATE)
            vaultPrefs.registerOnSharedPreferenceChangeListener(vaultPrefsListener)
        } catch (_: Exception) {}

        val db = NoteDatabase.getInstance(application)
        repository = NoteRepository(db.noteDao(), application)

        viewModelScope.launch {
            ArchiveVaultSecurity.keyAvailable.collect { available ->
                if (!available) _isVaultUnlocked.value = false
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            widgetReads.observe { repository.getActiveNotes() }.collect {
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
            lockVault()
        } else {
            refreshVaultStatus()
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

    fun selectNote(noteId: Long) {
        _selectedNoteIds.value = _selectedNoteIds.value + noteId
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
        val generation = com.focusbyrj.app.data.note.NotesStorageVersion.current()
        noteOperation {
            val db = NoteDatabase.getInstance(getApplication())
            val base = System.currentTimeMillis()
            db.withTransaction {
                check(generation == com.focusbyrj.app.data.note.NotesStorageVersion.current()) { "Notes were restored. Retry the reorder." }
                reorderedNotes.forEachIndexed { index, note ->
                    val stored = db.noteDao().getNoteByIdSync(note.id)
                    check(stored?.updatedAt == note.updatedAt) { "The list changed. Retry the reorder." }
                    db.noteDao().updateNoteOrder(note.id, base - index)
                }
            }
        }
    }

    private fun selectedRows() = displayedNotes.value.filter { it.id in _selectedNoteIds.value }

    private fun updateReviewedNotes(
        notes: List<NoteEntity>, clearAfterSave: Boolean = false, transform: (NoteEntity) -> NoteEntity
    ) {
        if (notes.isEmpty()) return
        val generation = com.focusbyrj.app.data.note.NotesStorageVersion.current()
        noteOperation {
            NoteDatabase.getInstance(getApplication()).withTransaction {
                check(generation == com.focusbyrj.app.data.note.NotesStorageVersion.current()) { "Notes were restored. Review your selection again." }
                for (note in notes) {
                    check(!VaultPayloadEncryptor.isVaultEncrypted(note)) { "Unlock this note before editing it." }
                    check(repository.saveNoteResult(transform(note).copy(updatedAt = System.currentTimeMillis()), note.updatedAt) is NoteRepository.SaveResult.Saved) { "Could not save the selection. Review the latest notes and retry." }
                }
            }
            notes.forEach { latestNotesCache.remove(it.id) }
            if (clearAfterSave) clearSelection()
            triggerAutoSync()
        }
    }

    fun pinSelectedNotes() {
        val notes = selectedRows()
        val pin = notes.any { !it.isPinned }
        updateReviewedNotes(notes, true) { it.copy(isPinned = pin) }
    }

    fun archiveSelectedNotes() = requestArchiveMove(true, _selectedNoteIds.value.toList())

    fun unarchiveSelectedNotes() = requestArchiveMove(false, _selectedNoteIds.value.toList())

    fun trashSelectedNotes() = updateReviewedNotes(selectedRows(), true) {
        it.copy(isTrashed = true, isPinned = false, trashedAt = System.currentTimeMillis())
    }

    fun restoreSelectedNotes() = updateReviewedNotes(selectedRows(), true) {
        it.copy(isTrashed = false, isPinned = false, trashedAt = null)
    }

    fun deleteSelectedNotesPermanently() {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isEmpty()) return
        ids.forEach { latestNotesCache.remove(it) }
        deleteReviewedTrash(displayedNotes.value.filter { it.id in ids })
    }

    fun duplicateSelectedNotes() {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isEmpty()) return
        val notes = displayedNotes.value.filter { it.id in ids }
        if (notes.isEmpty()) return
        val generation = com.focusbyrj.app.data.note.NotesStorageVersion.current()
        noteOperation {
            val db = NoteDatabase.getInstance(getApplication())
            db.withTransaction {
                check(generation == com.focusbyrj.app.data.note.NotesStorageVersion.current()) { "Review the restored notes before copying." }
                notes.forEach { source ->
                    val live = db.noteDao().getNoteByIdSync(source.id)
                    check(live?.updatedAt == source.updatedAt && live.isArchived == source.isArchived) { "A selected note changed. Review the selection again." }
                    duplicateRow(source)
                }
            }
            withContext(Dispatchers.Main) { clearSelection() }
        }
    }

    private fun noteOperation(action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try { action() }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), error.message ?: "Could not update notes.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun duplicateRow(source: NoteEntity): NoteEntity {
        val context = getApplication<Application>()
        check(!VaultPayloadEncryptor.isVaultEncrypted(source)) { "Unlock the note before copying it." }
        val files = mutableMapOf<String, String>()
        fun copy(path: String, audio: Boolean = false): String = files.getOrPut(path) {
            checkNotNull(if (audio) AudioMemoManager.copyAudioFile(context, path)
                else NoteImageHelper.copyImageFile(context, path)) { "An attachment could not be copied." }
        }
        try {
            val now = System.currentTimeMillis()
            val duplicate = source.copy(id = 0, sortPosition = null, title = source.title.ifBlank { "Note" } + " (copy)",
                isPinned = false, createdAt = now, updatedAt = now,
                checklistJson = if (source.isChecklist) ChecklistItem.listToJson(source.getChecklistItems().map {
                    it.copy(id = UUID.randomUUID().toString())
                }) else source.checklistJson,
                imageUrisJson = JSONArray(source.getImageUris().map { copy(it) }).toString(),
                audioUrisJson = JSONArray(source.getAudioUris().map { copy(it, true) }).toString(),
                content = com.focusbyrj.app.data.note.NoteMediaInventory.remapContent(source.content) { copy(it) })
            return duplicate.copy(id = repository.saveNote(duplicate))
        } catch (error: Exception) {
            com.focusbyrj.app.data.note.MediaRetirement.retire(files.values.toSet())
            throw error
        }
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
                when (val saved = repository.saveNoteResult(updated, note.updatedAt)) {
                    is NoteRepository.SaveResult.Saved -> latestNotesCache[updated.id] = updated.copy(updatedAt = saved.updatedAt)
                    else -> _saveStatus.value = NoteSaveStatus.Error("A selected note changed elsewhere. Please retry the label change.")
                }
            }
            try {
                com.focusbyrj.app.widget.NoteWidgetProvider.updateAllWidgets(getApplication())
            } catch (_: Exception) {}
            triggerAutoSync()
        }
    }

    fun setSelectedNotesColor(colorKey: String) = updateReviewedNotes(selectedRows(), true) { it.copy(colorKey = colorKey) }

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
    private val privateRowsAllowed = combine(_vaultStatus, _isVaultUnlocked, ArchiveVaultSecurity.keyAvailable) { status, authorized, key ->
        status != ArchiveVaultSecurity.VaultStatus.ENABLED || authorized && key
    }
    private val _loadingNotes = MutableStateFlow(true)
    val loadingNotes = _loadingNotes.asStateFlow()
    private val rawNotesFlow = combine(_currentFolder, _searchQuery, privateRowsAllowed) { folder, query, privateAllowed ->
        Triple(folder, query, privateAllowed)
    }.flatMapLatest { (folder, query, privateAllowed) ->
        val folderFlow = displayReads.observe {
            when (folder) {
                NoteFolder.NOTES -> repository.getActiveNotes()
                NoteFolder.ARCHIVE -> if (privateAllowed) repository.getArchivedNotes() else kotlinx.coroutines.flow.flowOf(emptyList())
                NoteFolder.TRASH -> repository.getTrashedNotes().map { notes ->
                    if (privateAllowed) notes else notes.filterNot { it.isArchived }
                }
            }
        }.map { rows -> _loadingNotes.value = false; rows }.onStart { _loadingNotes.value = true; emit(emptyList()) }
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
        privateRowsAllowed,
        _selectedColorFilter,
        _selectedLabelFilter
    ) { notes, privateAllowed, colorFilter, labelFilter ->
        notes.filter { note ->
            val matchesColor = colorFilter == null || note.colorKey.equals(colorFilter, ignoreCase = true)
            val matchesLabel = labelFilter == null || note.getLabels().any { it.equals(labelFilter, ignoreCase = true) }
            (privateAllowed || !note.isArchived) && matchesColor && matchesLabel
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // All available labels across active notes & custom labels (never leaks vault metadata)
    val allLabels: StateFlow<List<String>> = combine(
        labelReads.observe { repository.getActiveNotes() },
        _customLabels
    ) { active, custom ->
        val set = linkedSetOf<String>()
        custom.forEach { if (it.isNotBlank()) set.add(it) }
        active.forEach { note -> set.addAll(note.getLabels()) }
        set.toList().sorted()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ==========================================
    // EDITOR STATE (Live editing in full-screen)
    // ==========================================
    sealed class NoteSaveStatus {
        object Idle : NoteSaveStatus()
        object Saving : NoteSaveStatus()
        data class Saved(val noteId: Long) : NoteSaveStatus()
        data class Error(val message: String, val failedDraft: EditingNoteState? = null) : NoteSaveStatus()
    }

    private val _saveStatus = MutableStateFlow<NoteSaveStatus>(NoteSaveStatus.Idle)
    val saveStatus: StateFlow<NoteSaveStatus> = _saveStatus.asStateFlow()

    data class EditingNoteState(
        val sessionId: String = java.util.UUID.randomUUID().toString(),
        val originalId: Long = 0L,
        val storageRevision: Long? = null,
        val storageGeneration: Long = com.focusbyrj.app.data.note.NotesStorageVersion.current(),
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

    internal fun pushUndoSnapshot() {
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
            sessionId = UUID.randomUUID().toString(),
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
            sessionId = UUID.randomUUID().toString(),
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
    @Volatile private var initialSnapshot: EditingNoteState? = null

    private fun isNoteModified(current: EditingNoteState, initial: EditingNoteState?): Boolean {
        if (initial == null) return true
        if (current.originalId == 0L) {
            // For brand-new unsaved notes, only consider "modified" (worth saving) if there
            // is actual user content. An empty new note tapped and immediately closed should
            // never produce a DB row or a duplicate on close.
            return current.title.isNotBlank() ||
                    current.content.isNotBlank() ||
                    current.checklistItems.any { it.text.isNotBlank() } ||
                    current.imageUris.isNotEmpty() ||
                    current.audioUris.isNotEmpty()
        }
        return editorContentChanged(current, initial)
    }

    /** Compare draft content independently of whether a new row has received its ID yet. */
    private fun editorContentChanged(current: EditingNoteState, initial: EditingNoteState): Boolean =
        current.title != initial.title ||
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

    private var noteOpenGeneration = 0L

    fun openNewNote(asChecklist: Boolean = false, returnRoute: String? = null) {
        noteOpenGeneration++
        _returnRouteOnClose.value = returnRoute
        audioMemoManager.stopPlayback()
        audioMemoManager.cancelRecording()
        autoSaveJob?.cancel()
        undoPushJob?.cancel()
        pendingNewNoteId = 0L
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
        noteOpenGeneration++
        if (note.isArchived && ArchiveVaultSecurity.getVaultStatus(getApplication()) == ArchiveVaultSecurity.VaultStatus.ENABLED &&
            ArchiveVaultSecurity.isVaultLocked(getApplication())) return
        audioMemoManager.stopPlayback()
        audioMemoManager.cancelRecording()
        autoSaveJob?.cancel()
        undoPushJob?.cancel()
        pendingNewNoteId = 0L
        resetUndoHistory()
        val cached = latestNotesCache[note.id]
        val resolvedNote = if (cached != null && cached.updatedAt >= note.updatedAt) cached else note
        if (resolvedNote.isArchived && ArchiveVaultSecurity.getVaultStatus(getApplication()) == ArchiveVaultSecurity.VaultStatus.ENABLED &&
            ArchiveVaultSecurity.isVaultLocked(getApplication())) return

        // Safely check and decrypt vault-encrypted note payloads
        val decryptedNote = if (VaultPayloadEncryptor.isVaultEncrypted(resolvedNote)) {
            when (val dec = VaultPayloadEncryptor.tryDecryptNotePayload(resolvedNote)) {
                is VaultPayloadEncryptor.DecryptionResult.Success -> dec.note
                is VaultPayloadEncryptor.DecryptionResult.KeyMismatch -> {
                    _vaultKeyMismatchNoteId.value = resolvedNote.id
                    return
                }
                is VaultPayloadEncryptor.DecryptionResult.VaultLocked -> {
                    return
                }
                is VaultPayloadEncryptor.DecryptionResult.Corrupted -> {
                    Log.e("NotesViewModel", "Corrupted vault envelope for note ${resolvedNote.id}: ${dec.reason}")
                    Toast.makeText(getApplication(), "This note could not be decrypted. Its saved data has been preserved.", Toast.LENGTH_LONG).show()
                    return
                }
                is VaultPayloadEncryptor.DecryptionResult.NotEncrypted -> resolvedNote
            }
        } else {
            resolvedNote
        }

        com.focusbyrj.app.data.note.MediaRetirement.protect(decryptedNote)
        val state = EditingNoteState(
            originalId = decryptedNote.id,
            storageRevision = decryptedNote.updatedAt,
            title = decryptedNote.title,
            content = decryptedNote.content,
            isChecklist = decryptedNote.isChecklist,
            checklistItems = decryptedNote.getChecklistItems(),
            colorKey = decryptedNote.colorKey,
            fontKey = decryptedNote.fontKey,
            isPinned = decryptedNote.isPinned,
            isArchived = decryptedNote.isArchived,
            isTrashed = decryptedNote.isTrashed,
            labels = decryptedNote.getLabels(),
            imageUris = decryptedNote.getImageUris(),
            audioUris = decryptedNote.getAudioUris(),
            createdAt = decryptedNote.createdAt,
            updatedAt = decryptedNote.updatedAt
        )
        _editingState.value = state
        initialSnapshot = state

        if (decryptedNote.id != 0L) {
            viewModelScope.launch(Dispatchers.IO) {
                val fresh = try {
                    repository.getNoteByIdSync(decryptedNote.id)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        if (_editingState.value?.originalId == decryptedNote.id) {
                            Toast.makeText(getApplication(), "Could not refresh this note. Your draft is kept.", Toast.LENGTH_LONG).show()
                        }
                    }
                    return@launch
                }
                if (fresh != null && fresh.updatedAt > decryptedNote.updatedAt) {
                    val freshDecrypted = if (VaultPayloadEncryptor.isVaultEncrypted(fresh)) {
                        when (val dec = VaultPayloadEncryptor.tryDecryptNotePayload(fresh)) {
                            is VaultPayloadEncryptor.DecryptionResult.Success -> dec.note
                            is VaultPayloadEncryptor.DecryptionResult.KeyMismatch -> {
                                withContext(Dispatchers.Main) {
                                    _vaultKeyMismatchNoteId.value = fresh.id
                                }
                                return@launch
                            }
                            else -> return@launch // Never replace the editor with an unreadable envelope.
                        }
                    } else {
                        fresh
                    }
                    com.focusbyrj.app.data.note.MediaRetirement.protect(freshDecrypted)
                    withContext(Dispatchers.Main) {
                        if (freshDecrypted.isArchived && ArchiveVaultSecurity.getVaultStatus(getApplication()) == ArchiveVaultSecurity.VaultStatus.ENABLED &&
                            ArchiveVaultSecurity.isVaultLocked(getApplication())) return@withContext
                        latestNotesCache[freshDecrypted.id] = freshDecrypted
                        val curr = _editingState.value
                        // Only refresh from DB if the user has NOT yet made
                        // any edits. If the user started typing in the narrow window between
                        // openExistingNote() and the completion of this IO fetch, their edits
                        // would be silently overwritten. Guard with isNoteModified() check.
                        val userHasEdited = isNoteModified(curr ?: return@withContext, initialSnapshot)
                        if (curr.originalId == freshDecrypted.id && !userHasEdited) {
                            val freshState = curr.copy(
                                title = freshDecrypted.title,
                                content = freshDecrypted.content,
                                isChecklist = freshDecrypted.isChecklist,
                                checklistItems = freshDecrypted.getChecklistItems(),
                                colorKey = freshDecrypted.colorKey,
                                fontKey = freshDecrypted.fontKey,
                                isPinned = freshDecrypted.isPinned,
                                isArchived = freshDecrypted.isArchived,
                                isTrashed = freshDecrypted.isTrashed,
                                labels = freshDecrypted.getLabels(),
                                imageUris = freshDecrypted.getImageUris(),
                                audioUris = freshDecrypted.getAudioUris(),
                                updatedAt = freshDecrypted.updatedAt,
                                storageRevision = freshDecrypted.updatedAt
                            )
                            _editingState.value = freshState
                            initialSnapshot = freshState
                        }
                    }
                }
            }
        }
    }

    fun openNoteById(noteId: Long, returnRoute: String? = null) {
        if (_editingState.value != null || recordingState.value.isSessionOpen) {
            if (_editingState.value?.originalId != noteId) Toast.makeText(getApplication(),
                "Finish your current note or recording before opening another note.", Toast.LENGTH_LONG).show()
            return
        }
        val generation = ++noteOpenGeneration
        val storageGeneration = com.focusbyrj.app.data.note.NotesStorageVersion.current()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fresh = repository.getNoteByIdSync(noteId)
                val cached = latestNotesCache[noteId]
                val noteToOpen = when {
                    fresh != null && cached != null -> if (cached.updatedAt >= fresh.updatedAt) cached else fresh
                    fresh != null -> fresh
                    // A deleted row must never be revived from the presentation cache.
                    else -> null
                }
                withContext(Dispatchers.Main) {
                    if (generation != noteOpenGeneration || _editingState.value != null || recordingState.value.isSessionOpen ||
                        storageGeneration != com.focusbyrj.app.data.note.NotesStorageVersion.current()) return@withContext
                    if (noteToOpen == null) {
                        Toast.makeText(getApplication(), "This note is no longer available.", Toast.LENGTH_LONG).show()
                        return@withContext
                    }
                    _returnRouteOnClose.value = returnRoute
                    if (noteToOpen.isArchived && ArchiveVaultSecurity.getVaultStatus(getApplication()) == ArchiveVaultSecurity.VaultStatus.ENABLED && !_isVaultUnlocked.value) {
                        setFolder(NoteFolder.ARCHIVE)
                    } else openExistingNote(noteToOpen)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    if (generation == noteOpenGeneration) Toast.makeText(getApplication(),
                        "Could not open this note. Your saved data is kept; try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun processAndSaveImage(context: Context, contentUri: android.net.Uri): String? {
        return com.focusbyrj.app.data.note.NoteImageHelper.processAndSaveImage(context, contentUri)
    }

    /** Capture identity only, then seal/close the private editor before another app opens. */
    internal suspend fun preparePrivateMediaInput(expectedSession: String): String {
        val current = checkNotNull(_editingState.value) { "Open the note again." }
        check(current.sessionId == expectedSession) { "The note changed. Choose media again." }
        check(current.isArchived && current.originalId > 0L) { "Save and reopen this note before choosing media." }
        check(current.storageGeneration == com.focusbyrj.app.data.note.NotesStorageVersion.current()) { "Your data changed. Reopen the note." }
        check(!ArchiveVaultSecurity.isVaultLocked(getApplication())) { "Unlock the note first." }
        val nonce = UUID.randomUUID().toString()
        ArchiveVaultSecurity.beginMediaReturn(getApplication(), nonce, current.originalId, current.storageGeneration)
        return try {
            lockVaultForBackground()
            setFolder(NoteFolder.ARCHIVE)
            check(pendingPlainVaultDraft == null && !hasPendingVaultDraftFile()) { "Your draft needs recovery. Unlock the vault before choosing media." }
            withContext(Dispatchers.IO) {
                val row = checkNotNull(NoteDatabase.getInstance(getApplication()).noteDao().getNoteByIdSync(current.originalId)) { "This note was removed." }
                check(row.isArchived && !row.isTrashed && VaultPayloadEncryptor.isVaultEncrypted(row)) { "This private note is unavailable." }
                val hash = privateMediaHash(row)
                ArchiveVaultSecurity.bindMediaReturn(nonce, hash)
                org.json.JSONObject().put("id", row.id).put("hash", hash)
                    .put("epoch", current.storageGeneration).put("created", android.os.SystemClock.elapsedRealtime())
                    .put("nonce", nonce).toString()
            }
        } catch (error: Throwable) { ArchiveVaultSecurity.cancelMediaReturn(nonce); throw error }
    }

    private fun privateMediaHash(row: NoteEntity): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(row.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    internal fun unlockPrivateMediaInputIfRecent(target: String): Boolean {
        val request = org.json.JSONObject(target)
        if (_editingState.value != null) return false
        val unlocked = ArchiveVaultSecurity.consumeMediaReturn(getApplication(), request.getString("nonce"),
            request.getLong("id"), request.getLong("epoch"), request.getString("hash"))
        if (unlocked) refreshVaultStatus()
        return unlocked
    }

    /** Requires a live scoped return grant or a fresh PIN; the saved token itself grants no access. */
    internal suspend fun resumePrivateMediaInput(target: String, ownedImages: List<String>): Boolean {
        val request = org.json.JSONObject(target)
        check(android.os.SystemClock.elapsedRealtime() - request.getLong("created") in 0..86_400_000L) { "Media selection expired. Choose it again." }
        check(ArchiveVaultSecurity.getVaultStatus(getApplication()) == ArchiveVaultSecurity.VaultStatus.ENABLED &&
            !ArchiveVaultSecurity.isVaultLocked(getApplication())) { "Confirm your vault PIN first." }
        check(_editingState.value == null) { "Another note is open. Finish it before adding these files." }
        val id = request.getLong("id")
        val row = withContext(Dispatchers.IO) {
            ownedImages.forEach { path ->
                val file = File(path)
                check(com.focusbyrj.app.data.note.NoteMediaManager.isAllowedMediaFile(file, getApplication())) { "Selected media is unavailable. Choose it again." }
                checkNotNull(com.focusbyrj.app.util.crypto.EncryptedMediaStorage.openDecryptedInputStream(file)) { "Selected media could not be verified. Your saved data was kept." }.close()
            }
            checkNotNull(NoteDatabase.getInstance(getApplication()).noteDao().getNoteByIdSync(id)) { "This note was removed." }
        }
        check(request.getLong("epoch") == com.focusbyrj.app.data.note.NotesStorageVersion.current()) { "Your data was restored. Choose media again." }
        check(row.isArchived && !row.isTrashed && !ArchiveVaultSecurity.isVaultLocked(getApplication())) { "Unlock this private note again." }
        if (privateMediaHash(row) != request.getString("hash")) {
            // A restored Activity can replay an already committed selection. Never append it twice.
            val key = checkNotNull(ArchiveVaultSecurity.getActiveVaultSubKey())
            val already = try {
                val result = VaultPayloadEncryptor.tryDecryptNotePayload(row, key)
                result is VaultPayloadEncryptor.DecryptionResult.Success && ownedImages.isNotEmpty() &&
                    result.note.getImageUris().containsAll(ownedImages)
            } finally { key.fill(0) }
            check(already) { "This note changed. Your saved data was kept; choose media again." }
            return false
        }
        check(_editingState.value == null) { "Another note is open. Your draft was kept." }
        latestNotesCache.remove(id)
        setFolder(NoteFolder.ARCHIVE)
        openExistingNote(row)
        val opened = checkNotNull(_editingState.value) { "The private note could not be opened." }
        check(opened.originalId == id && opened.isArchived) { "The private note changed. Choose media again." }
        if (ownedImages.isNotEmpty()) {
            _editingState.value = opened.copy(imageUris = (opened.imageUris + ownedImages).distinct(), updatedAt = System.currentTimeMillis())
            persistCurrentEditorState()
        }
        return true
    }

    fun addImportedCapture(path: String) {
        _editingState.update { current -> current?.copy(imageUris = current.imageUris + path, updatedAt = System.currentTimeMillis()) }
        persistCurrentEditorState()
    }

    fun addImageUriToEditor(contentUri: android.net.Uri) {
        val ownerSession = _editingState.value?.sessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val localPath = processAndSaveImage(context, contentUri) ?: run {
                withContext(Dispatchers.Main) { android.widget.Toast.makeText(context, "Image could not be imported. Try again, or attach the original as a file.", android.widget.Toast.LENGTH_LONG).show() }
                return@launch
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                val current = _editingState.value
                if (current == null || current.sessionId != ownerSession) {
                    java.io.File(localPath).delete()
                    return@withContext
                }
                val updated = current.imageUris + localPath
                _editingState.value = current.copy(imageUris = updated, updatedAt = System.currentTimeMillis())
                persistCurrentEditorState()
            }
        }
    }

    fun addAttachmentToEditor(contentUri: android.net.Uri) {
        val ownerSession = _editingState.value?.sessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                var fileName = "attachment"
                var fileSize = "0 KB"
                var sizeBytes = 0L

                val cursor = context.contentResolver.query(contentUri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (nameIndex != -1) {
                            val name = it.getString(nameIndex)
                            if (!name.isNullOrBlank()) fileName = name
                        }
                        if (sizeIndex != -1) {
                            sizeBytes = it.getLong(sizeIndex)
                            fileSize = when {
                                sizeBytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", sizeBytes.toDouble() / (1024 * 1024))
                                sizeBytes >= 1024 -> "${sizeBytes / 1024} KB"
                                else -> "$sizeBytes B"
                            }
                        }
                    }
                }

                // Reject attachments exceeding 50 MB before any heap allocation.
                // readBytes() with no limit causes OOM on low-memory devices.
                val maxAttachmentBytes = 50L * 1024 * 1024
                if (sizeBytes > maxAttachmentBytes) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Attachment too large. Maximum file size is 50 MB.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                val imagesDir = java.io.File(context.filesDir, "keep_images").apply { if (!exists()) mkdirs() }
                val ext = fileName.substringAfterLast('.', "")
                    .lowercase(java.util.Locale.ROOT)
                    .filter { it.isLetterOrDigit() }
                    .take(10)
                    .ifEmpty { "bin" }
                val safeFileName = "att_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext"
                val destFile = java.io.File(imagesDir, safeFileName)

                try {
                    val inputStream = context.contentResolver.openInputStream(contentUri)
                        ?: throw java.io.IOException("Could not open the selected attachment")
                    inputStream.use { input ->
                        com.focusbyrj.app.util.crypto.EncryptedMediaStorage.openEncryptedOutputStream(destFile).use { output ->
                            val buffer = ByteArray(8192)
                            var actualBytes = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                actualBytes += read
                                if (actualBytes > maxAttachmentBytes) {
                                    throw java.io.IOException("Attachment exceeds the 50 MB limit")
                                }
                                output.write(buffer, 0, read)
                            }
                            sizeBytes = actualBytes
                            fileSize = when {
                                actualBytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", actualBytes.toDouble() / (1024 * 1024))
                                actualBytes >= 1024 -> "${actualBytes / 1024} KB"
                                else -> "$actualBytes B"
                            }
                        }
                    }
                } catch (t: Throwable) {
                    destFile.delete()
                    throw t
                }

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val current = _editingState.value
                    if (current == null || current.sessionId != ownerSession) {
                        destFile.delete()
                        return@withContext
                    }
                    val blocks = NotesnookBlockManager.parse(current.content).toMutableList()
                    blocks.add(
                        NotesnookBlock.Attachment(
                            uri = destFile.absolutePath,
                            fileName = fileName,
                            fileSize = fileSize
                        )
                    )
                    val newContent = NotesnookBlockManager.serialize(blocks, NotesnookBlockManager.isArticleMode(current.content), NotesnookBlockManager.getSubtitle(current.content))
                    _editingState.value = current.copy(content = newContent, updatedAt = System.currentTimeMillis())
                    persistCurrentEditorState()
                }
            } catch (e: Exception) {
                Log.e("NotesViewModel", "Attachment import failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        e.message ?: "Could not import attachment",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun addDrawingToEditor(bitmap: android.graphics.Bitmap) {
        val ownerSession = _editingState.value?.sessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val localPath = com.focusbyrj.app.data.note.NoteImageHelper.processAndSaveBitmap(context, bitmap, "sketch") ?: return@launch
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val current = _editingState.value
                    if (current == null || current.sessionId != ownerSession) {
                        java.io.File(localPath).delete()
                        return@withContext
                    }
                    val updated = current.imageUris + localPath
                    _editingState.value = current.copy(imageUris = updated, updatedAt = System.currentTimeMillis())
                    persistCurrentEditorState()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    if (!bitmap.isRecycled) bitmap.recycle()
                } catch (_: Throwable) {}
            }
        }
    }

    fun addPhotoToEditor(bitmap: android.graphics.Bitmap) {
        val ownerSession = _editingState.value?.sessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val localPath = com.focusbyrj.app.data.note.NoteImageHelper.processAndSaveBitmap(context, bitmap, "photo") ?: return@launch
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val current = _editingState.value
                    if (current == null || current.sessionId != ownerSession) {
                        java.io.File(localPath).delete()
                        return@withContext
                    }
                    val updated = current.imageUris + localPath
                    _editingState.value = current.copy(imageUris = updated, updatedAt = System.currentTimeMillis())
                    persistCurrentEditorState()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    if (!bitmap.isRecycled) bitmap.recycle()
                } catch (_: Throwable) {}
            }
        }
    }

    fun removeImageFromEditor(imageUri: String) {
        val current = _editingState.value ?: return
        val updated = current.imageUris.filter { it != imageUri }
        _editingState.value = current.copy(imageUris = updated, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState()
        // Do NOT delete physical file immediately — preserve for undo, draft recovery, and save error recovery.
        triggerAutoSync()
    }

    fun updateEditorTitle(title: String) {
        scheduleDebouncedUndoSnapshot()
        _editingState.value = _editingState.value?.copy(title = title, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState()
    }

    fun updateEditorContent(content: String) {
        scheduleDebouncedUndoSnapshot()
        _editingState.value = _editingState.value?.copy(content = content, updatedAt = System.currentTimeMillis())
        persistCurrentEditorState()
    }

    /**
     * Debounced undo snapshot — only pushes a snapshot after the user pauses typing for
     * 800 ms. This gives paragraph-level undo granularity instead of per-keystroke undo,
     * which is what users expect from a production notes app.
     */
    private fun scheduleDebouncedUndoSnapshot() {
        undoPushJob?.cancel()
        undoPushJob = viewModelScope.launch {
            delay(800L)
            pushUndoSnapshot()
        }
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

    fun saveFailedDraftAsCopy() {
        val current = _editingState.value ?: return
        val error = _saveStatus.value as? NoteSaveStatus.Error ?: return
        if (current.sessionId != error.failedDraft?.sessionId) return
        noteOperation {
            val db = NoteDatabase.getInstance(getApplication())
            val saved = db.withTransaction {
                val source = buildEntityFromState(current)
                val stored = db.noteDao().getNoteByIdSync(source.id)
                duplicateRow(source.copy(isArchived = source.isArchived || stored?.isArchived == true))
            }
            withContext(Dispatchers.Main) {
                if (_editingState.value?.let { sameArchiveDraft(it, current) } == true) {
                    openExistingNote(saved)
                    _saveStatus.value = NoteSaveStatus.Saved(saved.id)
                }
            }
        }
    }

    fun duplicateCurrentNote() {
        val current = _editingState.value ?: return
        noteOperation {
            val source = persistMutex.withLock {
                val active = _editingState.value ?: return@noteOperation
                if (!sameArchiveDraft(active, current)) return@noteOperation
                val entity = buildEntityFromState(active)
                if (isNoteModified(active, initialSnapshot) || active.originalId == 0L) {
                    saveEditorEntity(entity, active)
                } else entity
            }
            val copy = duplicateRow(source)
            withContext(Dispatchers.Main) {
                val active = _editingState.value
                if (active != null && sameArchiveDraft(active, current)) openExistingNote(copy)
            }
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
            NotesnookBlockManager.fullText(current.content).trim()
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
            NotesnookBlockManager.fullText(current.content).trim()
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
        if (current.isArchived) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
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
                    appendLine(NotesnookBlockManager.fullText(note.content).trim())
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
                    appendLine(NotesnookBlockManager.fullText(note.content).trim())
                }
            }.trim()
        }.trim()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Notes", fullText)
        if (notes.any { it.isArchived }) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
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
        if (recordingState.value.isSessionOpen && !recordingState.value.isRecording) {
            audioMemoManager.retryRecording()
        } else {
            audioMemoManager.startRecording()
        }
    }

    fun stopVoiceRecordingAndAttach() {
        val (audioPath, transcript) = audioMemoManager.stopRecording()
        if (audioPath == null && transcript.isBlank()) return
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
        // Do NOT delete physical file immediately — preserve for undo, draft recovery, and save error recovery.
        triggerAutoSync()
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

    private fun canCreateDockNote(): Boolean {
        val allowed = _editingState.value == null && !recordingState.value.isSessionOpen &&
            !com.focusbyrj.app.util.backup.MaintenanceGate.busy.value
        if (!allowed) Toast.makeText(getApplication(),
            "Finish the current note or operation, then choose this option again. Your work is kept.",
            Toast.LENGTH_LONG).show()
        return allowed
    }

    fun openNewNoteWithVoice(transcription: String) {
        if (!canCreateDockNote()) return
        noteOpenGeneration++
        audioMemoManager.stopPlayback()
        audioMemoManager.cancelRecording()
        autoSaveJob?.cancel()
        undoPushJob?.cancel()
        pendingNewNoteId = 0L
        resetUndoHistory()
        initialSnapshot = null
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
        val ownerSession = _editingState.value?.sessionId
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val localPath = com.focusbyrj.app.data.note.NoteImageHelper.processAndSaveBitmap(context, bitmap, "photo") ?: return@launch
                withContext(Dispatchers.Main) {
                    val current = _editingState.value
                    if (current?.sessionId != ownerSession) {
                        java.io.File(localPath).delete()
                        return@withContext
                    }
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    if (!bitmap.isRecycled) bitmap.recycle()
                } catch (_: Throwable) {}
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

    fun restoreNote(note: NoteEntity) = updateReviewedNotes(listOf(note)) { it.copy(isTrashed = false, trashedAt = null, isPinned = false) }

    fun unarchiveNote(note: NoteEntity) = requestArchiveMove(false, listOf(note.id))

    fun deletePermanently(note: NoteEntity) = deleteReviewedTrash(listOf(note))

    fun emptyTrash(reviewed: List<NoteEntity> = displayedNotes.value, generation: Long = com.focusbyrj.app.data.note.NotesStorageVersion.current()) = deleteReviewedTrash(reviewed, generation)

    private fun deleteReviewedTrash(notes: List<NoteEntity>, generation: Long = com.focusbyrj.app.data.note.NotesStorageVersion.current()) {
        if (notes.isEmpty()) return
        noteOperation {
            val db = NoteDatabase.getInstance(getApplication())
            checkNotNull(DataSafetyManager.writePreOpSnapshot(getApplication(), db.noteDao(), "trash_delete"))
            db.withTransaction {
                check(generation == com.focusbyrj.app.data.note.NotesStorageVersion.current())
                notes.forEach { repository.deletePermanently(it) }
            }
            notes.forEach { latestNotesCache.remove(it.id); com.focusbyrj.app.data.note.MediaRetirement.beforeChange(it, null) }
            clearSelection()
            triggerOrphanMediaCleanup()
        }
    }

    fun deleteCurrentNote() {
        val draft = _editingState.value ?: return
        autoSaveJob?.cancel()
        audioMemoManager.stopPlayback()
        audioMemoManager.cancelRecording()
        noteOperation {
            persistMutex.withLock {
                val active = _editingState.value ?: return@withLock
                if (active.sessionId != draft.sessionId) return@withLock
                val entity = buildEntityFromState(active)
                if (entity.id != 0L || !entity.isEmptyNote()) {
                    val saved = saveEditorEntity(entity.copy(isPinned = false, isTrashed = true,
                        trashedAt = System.currentTimeMillis()), active)
                    latestNotesCache.remove(saved.id)
                }
                if (!_editingState.compareAndSet(active, null)) {
                    _editingState.update { later ->
                        if (later?.sessionId == active.sessionId) later.copy(isTrashed = true, isPinned = false,
                            originalId = sessionIds[active.sessionId] ?: active.originalId,
                            storageRevision = sessionRevisions[active.sessionId]) else later
                    }
                }
            }
        }
    }

    fun archiveCurrentNote() = requestArchiveMove(true, editor = _editingState.value)

    fun unarchiveCurrentNote() = requestArchiveMove(false, editor = _editingState.value)

    fun toggleArchiveCurrentNote() {
        val current = _editingState.value ?: return
        if (current.isArchived) {
            unarchiveCurrentNote()
        } else {
            archiveCurrentNote()
        }
    }

    fun openNewNoteWithDrawing(bitmap: android.graphics.Bitmap) {
        if (!canCreateDockNote()) return
        openNewNote(asChecklist = false)
        addDrawingToEditor(bitmap)
    }

    fun openNewNoteWithImages(uris: List<android.net.Uri>) {
        if (!canCreateDockNote()) return
        openNewNote(asChecklist = false)
        uris.forEach { uri ->
            addImageUriToEditor(uri)
        }
    }

    private val sessionIds = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val sessionRevisions = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val closingSessions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private suspend fun saveEditorEntity(entity: NoteEntity, state: EditingNoteState): NoteEntity {
        check(state.storageGeneration == com.focusbyrj.app.data.note.NotesStorageVersion.current()) {
            "Notes were restored while this editor was open. Your draft is retained; reopen the latest note before merging."
        }
        val expected = sessionRevisions[state.sessionId] ?: state.storageRevision
        val resolved = if (entity.id == 0L) entity.copy(id = sessionIds[state.sessionId] ?: 0L) else entity
        return when (val result = repository.saveNoteResult(resolved, expected)) {
            is NoteRepository.SaveResult.Saved -> {
                sessionIds[state.sessionId] = result.id
                sessionRevisions[state.sessionId] = result.updatedAt
                resolved.copy(id = result.id, updatedAt = result.updatedAt)
            }
            else -> throw IllegalStateException(when (result) {
                is NoteRepository.SaveResult.Conflict -> result.message
                is NoteRepository.SaveResult.Locked -> result.message
                else -> "Could not save note. Your draft has been retained."
            })
        }
    }

    fun closeEditor() {
        audioMemoManager.stopPlayback()
        audioMemoManager.cancelRecording()
        autoSaveJob?.cancel()
        undoPushJob?.cancel()
        val current = _editingState.value ?: return

        val initial = initialSnapshot
        val modified = isNoteModified(current, initial)

        // If existing note is unmodified, do NOT overwrite timestamp or re-save
        if (!modified && !persistMutex.isLocked && current.originalId != 0L && !buildEntityFromState(current).isEmptyNote()) {
            _editingState.value = null
            initialSnapshot = null
            pendingNewNoteId = 0L
            return
        }

        // If a brand-new note was not modified at all, discard immediately without saving
        if (!modified && !persistMutex.isLocked && current.originalId == 0L && pendingNewNoteId == 0L) {
            _editingState.value = null
            initialSnapshot = null
            return
        }

        if (!closingSessions.add(current.sessionId)) return
        _saveStatus.value = NoteSaveStatus.Saving
        viewModelScope.launch(Dispatchers.IO) {
            withContext(kotlinx.coroutines.NonCancellable) { persistMutex.withLock {
                val effectiveId = sessionIds[current.sessionId] ?: current.originalId
                val resolvedCurrent = if (effectiveId != current.originalId) current.copy(originalId = effectiveId) else current
                val entity = buildEntityFromState(resolvedCurrent, isModified = modified)

                try {
                    var storedId = entity.id
                    if (entity.isEmptyNote()) {
                        if (entity.id != 0L) {
                            val noteDao = NoteDatabase.getInstance(getApplication()).noteDao()
                            checkNotNull(DataSafetyManager.writePreOpSnapshot(getApplication(), noteDao, "empty_note_soft_delete")) { "Could not create a recovery snapshot. Your note was kept." }
                            latestNotesCache.remove(entity.id)
                            val expected = sessionRevisions[current.sessionId] ?: resolvedCurrent.storageRevision
                            val stored = noteDao.getNoteByIdSync(entity.id) ?: throw IllegalStateException("This note was removed elsewhere")
                            check(expected == null || stored.updatedAt == expected) { "This note changed elsewhere. Your draft is retained." }
                            check(noteDao.compareAndSave(stored.copy(isTrashed = true, isPinned = false, trashedAt = System.currentTimeMillis()), stored.updatedAt) != null) {
                                "This note changed elsewhere. Your draft is retained."
                            }
                            Log.i("NotesViewModel", "Existing note ${entity.id} was cleared to empty — moved to Trash safely (soft-delete).")
                        }
                    } else {
                        val finalEntity = saveEditorEntity(entity, resolvedCurrent)
                        storedId = finalEntity.id
                        latestNotesCache[finalEntity.id] = finalEntity
                    }
                    val active = _editingState.value
                    if (active?.sessionId == current.sessionId && !editorContentChanged(active, current) &&
                        _editingState.compareAndSet(active, null)) {
                        pendingNewNoteId = 0L
                        initialSnapshot = null
                        _saveStatus.value = NoteSaveStatus.Saved(storedId)
                    } else closingSessions.remove(current.sessionId)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("NotesViewModel", "Failed to persist note on close", e)
                    closingSessions.remove(current.sessionId)
                    _saveStatus.value = NoteSaveStatus.Error(e.message ?: "Failed to save note", failedDraft = resolvedCurrent)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(getApplication(), e.message ?: "Could not save note", Toast.LENGTH_LONG).show()
                    }
                }
            } }
            triggerAutoSync()
        }
    }

    private fun persistCurrentEditorState(immediate: Boolean = false) {
        val current = _editingState.value ?: return
        val isModified = isNoteModified(current, initialSnapshot)
        if (!isModified && !persistMutex.isLocked && current.originalId != 0L) return

        val entityCandidate = buildEntityFromState(current, isModified = true)
        if (entityCandidate.isEmptyNote() && current.originalId == 0L) return

        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            if (!immediate) {
                delay(300L)
            }
            _saveStatus.value = NoteSaveStatus.Saving
            withContext(kotlinx.coroutines.NonCancellable) { persistMutex.withLock {
                val latest = _editingState.value ?: return@withLock
                // Session epoch guard: reject stale writes if editor session switched
                if (latest.sessionId != current.sessionId) {
                    Log.d("NotesViewModel", "Stale autosave rejected: session changed")
                    return@withLock
                }

                val effectiveId = sessionIds[latest.sessionId] ?: latest.originalId
                val resolvedLatest = if (effectiveId != latest.originalId) latest.copy(originalId = effectiveId) else latest

                val latestModified = isNoteModified(resolvedLatest, initialSnapshot)
                if (!latestModified && resolvedLatest.originalId != 0L) return@withLock

                val entity = buildEntityFromState(resolvedLatest, isModified = true)
                if (entity.isEmptyNote() && resolvedLatest.originalId == 0L) return@withLock

                try {
                    val finalEntity = saveEditorEntity(entity, resolvedLatest)
                    val savedId = finalEntity.id
                    if (_editingState.value?.sessionId == latest.sessionId) initialSnapshot = resolvedLatest.copy(originalId = savedId)
                    latestNotesCache[savedId] = finalEntity
                    _editingState.update { active ->
                        if (active?.sessionId == latest.sessionId) active.copy(originalId = savedId, storageRevision = finalEntity.updatedAt)
                        else active
                    }
                    _saveStatus.value = NoteSaveStatus.Saved(savedId)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("NotesViewModel", "Failed to autosave note", e)
                    _saveStatus.value = NoteSaveStatus.Error(e.message ?: "Failed to autosave note", failedDraft = resolvedLatest)
                }
            } }
            triggerAutoSync()
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

    enum class ArchiveMovePhase { IDLE, UNLOCK, SAVING, SUCCESS, ERROR }
    data class ArchiveMoveState(
        val phase: ArchiveMovePhase = ArchiveMovePhase.IDLE,
        val archived: Boolean = true,
        val message: String = "",
        val editorClosed: Boolean = false
    )
    private data class ArchiveMoveRequest(
        val archived: Boolean, val ids: List<Long>, val editor: EditingNoteState?,
        val generation: Long = com.focusbyrj.app.data.note.NotesStorageVersion.current()
    )
    private val _archiveMove = MutableStateFlow(ArchiveMoveState())
    val archiveMove: StateFlow<ArchiveMoveState> = _archiveMove.asStateFlow()
    private var pendingArchiveMove: ArchiveMoveRequest? = null

    private fun sameArchiveDraft(a: EditingNoteState, b: EditingNoteState): Boolean =
        a.copy(originalId = b.originalId, storageRevision = b.storageRevision, updatedAt = b.updatedAt) == b

    private fun requestArchiveMove(archived: Boolean, ids: List<Long> = emptyList(), editor: EditingNoteState? = null) {
        if (_archiveMove.value.phase in setOf(ArchiveMovePhase.UNLOCK, ArchiveMovePhase.SAVING)) return
        if (ids.isEmpty() && editor == null) return
        if (editor != null && buildEntityFromState(editor).isEmptyNote()) {
            _archiveMove.value = ArchiveMoveState(ArchiveMovePhase.ERROR, archived, "Add something to the note before moving it.")
            return
        }
        if (hasPendingVaultDraftFile() || pendingVaultDraftInMemory != null || pendingPlainVaultDraft != null) {
            _archiveMove.value = ArchiveMoveState(ArchiveMovePhase.ERROR, archived,
                "An earlier private draft needs recovery. Open Archive and unlock it before moving another note.")
            return
        }
        pendingArchiveMove = ArchiveMoveRequest(archived, ids.distinct(), editor)
        refreshVaultStatus()
        if (_vaultStatus.value == ArchiveVaultSecurity.VaultStatus.ENABLED && ArchiveVaultSecurity.isVaultLocked(getApplication()) &&
            !(archived && VaultArchiveDeposit.readCapability(getApplication()) != null)) {
            _archiveMove.value = ArchiveMoveState(ArchiveMovePhase.UNLOCK, archived)
        } else continueArchiveMove()
    }

    fun verifyArchiveMovePin(pin: String): ArchiveVaultSecurity.VerifyResult {
        if (pendingArchiveMove == null || _archiveMove.value.phase != ArchiveMovePhase.UNLOCK)
            return ArchiveVaultSecurity.VerifyResult.Error("This request expired. Start the move again.")
        // Do not navigate to Archive or start unrelated recovery jobs while authorizing this move.
        return ArchiveVaultSecurity.verifyPasscode(getApplication(), pin)
    }

    suspend fun verifyArchiveMovePinAsync(pin: String): ArchiveVaultSecurity.VerifyResult {
        if (pendingArchiveMove == null || _archiveMove.value.phase != ArchiveMovePhase.UNLOCK)
            return ArchiveVaultSecurity.VerifyResult.Error("This request expired. Start the move again.")
        return ArchiveVaultSecurity.verifyPasscodeAsync(getApplication(), pin)
    }

    fun cancelArchiveMoveAuthorization() {
        if (_archiveMove.value.phase != ArchiveMovePhase.UNLOCK) return
        pendingArchiveMove = null
        ArchiveVaultSecurity.lockVault()
        latestNotesCache.clear()
        refreshVaultStatus()
        _archiveMove.value = ArchiveMoveState()
    }

    fun dismissArchiveMoveResult() {
        if (_archiveMove.value.phase in setOf(ArchiveMovePhase.SUCCESS, ArchiveMovePhase.ERROR))
            _archiveMove.value = ArchiveMoveState()
    }

    private fun requireArchiveSaved(result: NoteRepository.SaveResult): NoteRepository.SaveResult.Saved = when (result) {
        is NoteRepository.SaveResult.Saved -> result
        is NoteRepository.SaveResult.Locked -> throw IllegalStateException(result.message)
        is NoteRepository.SaveResult.Conflict -> throw IllegalStateException(result.message)
        is NoteRepository.SaveResult.Corrupt -> throw IllegalStateException(result.message)
        else -> throw IllegalStateException("Could not move your notes. Check phone storage and try again.")
    }

    fun continueArchiveMove() {
        val queued = pendingArchiveMove ?: return
        if (_archiveMove.value.phase == ArchiveMovePhase.SAVING) return
        val latestEditor = _editingState.value
        if (queued.editor != null && (latestEditor == null || latestEditor.sessionId != queued.editor.sessionId)) {
            pendingArchiveMove = null
            ArchiveVaultSecurity.lockVault()
            latestNotesCache.clear()
            refreshVaultStatus()
            _archiveMove.value = ArchiveMoveState(ArchiveMovePhase.ERROR, queued.archived,
                "The open note changed. Review your note and start the move again.")
            return
        }
        // Include edits/attachment callbacks received while the PIN sheet was open.
        val request = if (queued.editor != null) queued.copy(editor = latestEditor) else queued
        val (key, vaultSettings) = synchronized(ArchiveVaultSecurity) {
            ArchiveVaultSecurity.getActiveVaultSubKey() to getApplication<Application>()
                .getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, Context.MODE_PRIVATE).all
        }
        val deposit = if (request.archived && key == null) VaultArchiveDeposit.readCapability(getApplication()) else null
        if (ArchiveVaultSecurity.getVaultStatus(getApplication()) == ArchiveVaultSecurity.VaultStatus.ENABLED && key == null && deposit == null) {
            _archiveMove.value = ArchiveMoveState(ArchiveMovePhase.UNLOCK, request.archived)
            return
        }
        // Consume one authorization and keep the global vault locked during the write.
        ArchiveVaultSecurity.lockVault()
        latestNotesCache.clear()
        refreshVaultStatus()
        _archiveMove.value = ArchiveMoveState(ArchiveMovePhase.SAVING, request.archived)
        audioMemoManager.stopPlayback()
        audioMemoManager.cancelRecording()
        if (request.editor != null) { autoSaveJob?.cancel(); undoPushJob?.cancel() }
        viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            // Finish the authorized write/recovery and wipe its key even if this screen is destroyed.
            withContext(kotlinx.coroutines.NonCancellable) {
                var recoverySaved = false
                try {
                    var savedEditor: NoteEntity? = null
                    withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                        persistMutex.withLock {
                            check(request.generation == com.focusbyrj.app.data.note.NotesStorageVersion.current()) {
                                "Notes were restored. Your draft was kept; reopen the latest note before moving it."
                            }
                            check(getApplication<Application>().getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, Context.MODE_PRIVATE).all == vaultSettings) {
                                "Vault settings changed. Start the move again; your draft was kept."
                            }
                            val editor = request.editor
                            if (editor != null) {
                                val active = _editingState.value
                                check(active != null && sameArchiveDraft(active, editor)) { "The draft changed. Review it and try again." }
                                val entity = buildEntityFromState(editor).copy(isArchived = request.archived, isPinned = false)
                                check(!entity.isEmptyNote()) { "Add something to the note before moving it." }
                                val stored = when {
                                    request.archived && key == null && deposit != null -> VaultArchiveDeposit.encrypt(getApplication(), entity, deposit)
                                    request.archived -> VaultPayloadEncryptor.encryptNotePayload(entity, key, getApplication())
                                    else -> entity
                                }
                                savedEditor = saveEditorEntity(stored, editor)
                            } else {
                                val db = NoteDatabase.getInstance(getApplication())
                                // A batch either completes in full or keeps every selected note unchanged.
                                db.withTransaction {
                                    check(request.generation == com.focusbyrj.app.data.note.NotesStorageVersion.current()) { "Notes were restored. Refresh and try again." }
                                    request.ids.forEach { requireArchiveSaved(repository.setArchivedResult(it, request.archived, key, deposit)) }
                                }
                            }
                        }
                    }
                    val editor = request.editor
                    var editorClosed = false
                    if (editor != null) {
                        val saved = checkNotNull(savedEditor)
                        val active = _editingState.value
                        if (active?.sessionId == editor.sessionId && sameArchiveDraft(active, editor)) {
                            _editingState.value = null
                            initialSnapshot = null
                            pendingNewNoteId = 0L
                            resetUndoHistory()
                            editorClosed = true
                        } else if (active?.sessionId == editor.sessionId) {
                            // A late attachment callback must not discard edits made after the snapshot.
                            val later = active.copy(originalId = saved.id, storageRevision = saved.updatedAt,
                                isArchived = request.archived, isPinned = false)
                            if (request.archived) {
                                pendingPlainVaultDraft = later
                                _editingState.value = null
                                withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                                    val encrypted = if (key == null && deposit != null)
                                        VaultArchiveDeposit.encrypt(getApplication(), buildEntityFromState(later), deposit)
                                    else VaultPayloadEncryptor.encryptNotePayload(buildEntityFromState(later), key, getApplication())
                                    pendingVaultDraftInMemory = encrypted
                                    persistPendingVaultDraft(encrypted, requireEmpty = true)
                                    pendingVaultDraftInMemory = null
                                    pendingPlainVaultDraft = null
                                }
                                recoverySaved = true
                                throw IllegalStateException("Note archived. Later changes were saved as a recovery draft. Unlock Archive to recover them.")
                            } else _editingState.value = later
                        }
                        if (pendingPlainVaultDraft?.sessionId == editor.sessionId &&
                            pendingPlainVaultDraft?.let { sameArchiveDraft(it, editor) } == true) pendingPlainVaultDraft = null
                        latestNotesCache.remove(saved.id)
                        _saveStatus.value = NoteSaveStatus.Saved(saved.id)
                    } else {
                        request.ids.forEach { latestNotesCache.remove(it) }
                        clearSelection()
                    }
                    if (!request.archived && _currentFolder.value == NoteFolder.ARCHIVE) _currentFolder.value = NoteFolder.NOTES
                    _archiveMove.value = ArchiveMoveState(ArchiveMovePhase.SUCCESS, request.archived,
                        if (request.archived) "Moved to Archive" else "Moved to Notes", editorClosed)
                    triggerAutoSync()
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    var message = error.message ?: "Could not move your notes. Your draft and saved notes were kept."
                    if (request.editor != null && !recoverySaved && (request.editor.isArchived ||
                        pendingPlainVaultDraft?.let { it.sessionId == request.editor.sessionId && it.isArchived } == true)) {
                        // On background/lock or a failed unarchive, preserve private edits under encryption.
                        val draft = pendingPlainVaultDraft?.takeIf { it.sessionId == request.editor.sessionId }
                            ?: _editingState.value?.takeIf { it.sessionId == request.editor.sessionId } ?: request.editor
                        pendingPlainVaultDraft = draft
                        _editingState.value = _editingState.value?.takeUnless { it.sessionId == draft.sessionId }
                        try {
                            withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                                val encrypted = if (key == null && deposit != null)
                                    VaultArchiveDeposit.encrypt(getApplication(), buildEntityFromState(draft), deposit)
                                else VaultPayloadEncryptor.encryptNotePayload(buildEntityFromState(draft), key, getApplication())
                                pendingVaultDraftInMemory = encrypted
                                persistPendingVaultDraft(encrypted, requireEmpty = true)
                                pendingVaultDraftInMemory = null
                                pendingPlainVaultDraft = null
                            }
                            message += " Your private draft was saved for recovery; unlock Archive to recover it."
                        } catch (_: Exception) {
                            message += " Your private draft is retained in memory. Keep Run open and unlock Archive to recover it."
                        }
                    }
                    _archiveMove.value = ArchiveMoveState(ArchiveMovePhase.ERROR, request.archived, message)
                    if (request.editor != null) _saveStatus.value = NoteSaveStatus.Error(message, request.editor)
                } finally {
                    key?.fill(0)
                    pendingArchiveMove = null
                    ArchiveVaultSecurity.lockVault()
                    latestNotesCache.clear()
                    refreshVaultStatus()
                }
            }
        }
    }

    // Direct card quick actions
    fun togglePin(note: NoteEntity) = updateReviewedNotes(listOf(note)) { it.copy(isPinned = !it.isPinned) }

    fun archiveNote(note: NoteEntity) = requestArchiveMove(true, listOf(note.id))

    fun trashNote(note: NoteEntity) = updateReviewedNotes(listOf(note)) {
        it.copy(isTrashed = true, trashedAt = System.currentTimeMillis(), isPinned = false)
    }

    fun setNoteColor(note: NoteEntity, colorKey: String) = updateReviewedNotes(listOf(note)) { it.copy(colorKey = colorKey) }

    fun setNoteFont(note: NoteEntity, fontKey: String) = updateReviewedNotes(listOf(note)) { it.copy(fontKey = fontKey) }

    private fun triggerAutoSync() {
        try {
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        if (_editingState.value?.isArchived == true) lockVault()
        super.onCleared()
        try {
            val vaultPrefs = getApplication<android.app.Application>().getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, Context.MODE_PRIVATE)
            vaultPrefs.unregisterOnSharedPreferenceChangeListener(vaultPrefsListener)
        } catch (_: Exception) {}
        ArchiveVaultSecurity.lockVault()
        latestNotesCache.clear()
        audioMemoManager.release()
    }
}
