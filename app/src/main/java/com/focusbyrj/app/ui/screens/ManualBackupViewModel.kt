package com.focusbyrj.app.ui.screens

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.util.backup.BackupRestoreManager
import com.focusbyrj.app.util.backup.BackupProgress
import com.focusbyrj.app.util.backup.ManualBackupFiles
import com.focusbyrj.app.util.backup.PreparedManualBackup
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class ManualBackupPhase { IDLE, PREPARING, READY, PICKER, SAVING, SAVED, MESSAGE }
internal data class ManualBackupState(
    val phase: ManualBackupPhase = ManualBackupPhase.IDLE,
    val id: String? = null,
    val message: String? = null,
    val metadata: BackupRestoreManager.BackupMetadata? = null
)

/** Secrets never enter saved state. Rotation can retain only a completed encrypted archive. */
internal class ManualBackupViewModel(
    application: Application,
    private val prepareArchive: suspend (Context, String, ByteArray?) -> PreparedManualBackup
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, { context, password, key -> ManualBackupFiles.prepare(context, password, key) })
    private val mutable = MutableStateFlow(ManualBackupState())
    val state = mutable.asStateFlow()
    private var prepared: PreparedManualBackup? = null
    private var operation: Job? = null
    private var expiry: Job? = null
    private var generation = 0L

    fun prepare(password: String) {
        // Consume one entry attempt. Repeated input must not cancel a preparation
        // and try to recapture the vault key after the first call relocked it.
        when (mutable.value.phase) {
            ManualBackupPhase.IDLE, ManualBackupPhase.MESSAGE, ManualBackupPhase.SAVED -> Unit
            else -> return
        }
        cancel()
        // Capture a single-use capability synchronously, then immediately restore the global lock.
        // The key exists only inside the preparation job, never in UI/saved state or preferences.
        val vaultKey = ArchiveVaultSecurity.getActiveVaultSubKey()
        ArchiveVaultSecurity.lockVault()
        val request = ++generation
        mutable.value = ManualBackupState(ManualBackupPhase.PREPARING, message = "Reading saved data")
        operation = viewModelScope.launch(BackupProgress { message ->
            mutable.update { if (request == generation && it.phase == ManualBackupPhase.PREPARING) it.copy(message = message) else it }
        }, start = CoroutineStart.UNDISPATCHED) {
            var result: PreparedManualBackup? = null
            try {
                result = prepareArchive(getApplication(), password, vaultKey)
                ensureActive()
                vaultKey?.fill(0)
                if (request != generation) return@launch
                prepared = result
                mutable.value = ManualBackupState(ManualBackupPhase.READY, result.id, metadata = result.metadata)
                result = null // Ownership has moved to this ViewModel.
                expiry = viewModelScope.launch {
                    delay(ManualBackupFiles.LIFETIME_MS)
                    if (request == generation && mutable.value.phase in setOf(ManualBackupPhase.READY, ManualBackupPhase.PICKER))
                        this@ManualBackupViewModel.cancel("The prepared backup expired. Create it again; the vault remains locked.")
                }
            } catch (error: CancellationException) {
                if (request == generation) mutable.value = ManualBackupState(ManualBackupPhase.MESSAGE,
                    message = "Backup preparation was interrupted. Create it again; no save was confirmed.")
                throw error
            }
            catch (error: Exception) {
                if (request == generation) mutable.value = ManualBackupState(ManualBackupPhase.MESSAGE,
                    message = "Backup could not be prepared. ${error.message ?: "Check phone storage and try again."} No saved data was changed.")
            } finally {
                vaultKey?.fill(0)
                ManualBackupFiles.discard(result)
            }
        }
    }

    fun chooseLocation(): String? {
        val value = prepared ?: return null
        if (ManualBackupFiles.expired(value)) { cancel("The prepared backup expired. Please create it again."); return null }
        if (mutable.value.phase != ManualBackupPhase.READY) return null
        mutable.value = mutable.value.copy(phase = ManualBackupPhase.PICKER)
        return value.id
    }

    fun save(id: String?, uri: Uri?) {
        val value = prepared
        if (value == null || value.id != id || mutable.value.phase != ManualBackupPhase.PICKER) {
            // A duplicated/late ActivityResult must not replace a confirmed success,
            // cancellation message or a newer session. Only a lost idle session needs recovery.
            if (value == null && mutable.value.phase == ManualBackupPhase.IDLE && id != null)
                cancel("This backup session was interrupted. Create a new backup; no completed save was confirmed.")
            else if (value != null && mutable.value.phase == ManualBackupPhase.PICKER && id == null)
                cancel("The save request could not be matched to this backup. Create it again; no save was confirmed.")
            return
        }
        if (uri == null) { cancel("Save cancelled. The temporary encrypted backup was discarded; your saved data was not changed."); return }
        val request = generation
        mutable.value = mutable.value.copy(phase = ManualBackupPhase.SAVING, message = "Saving backup file")
        operation = viewModelScope.launch(BackupProgress { message ->
            mutable.update { if (request == generation && it.phase == ManualBackupPhase.SAVING) it.copy(message = message) else it }
        }) {
            try {
                ManualBackupFiles.save(getApplication(), value, uri)
                ensureActive()
                if (request == generation) {
                    ManualBackupFiles.discard(value); prepared = null; expiry?.cancel()
                    mutable.value = ManualBackupState(ManualBackupPhase.SAVED, metadata = value.metadata)
                }
            } catch (error: CancellationException) {
                if (request == generation) this@ManualBackupViewModel.cancel("Saving was interrupted. The destination may contain an incomplete file. Create a new backup; no save was confirmed.")
                throw error
            }
            catch (error: Exception) {
                if (request == generation) {
                    if (ManualBackupFiles.expired(value)) this@ManualBackupViewModel.cancel("The backup could not be saved and has expired. Create it again. The destination may contain an incomplete file.")
                    else mutable.value = ManualBackupState(ManualBackupPhase.READY, value.id,
                        "Save was not confirmed. ${error.message ?: "Check the destination and try another location."} The destination may contain an incomplete file. Your verified encrypted copy is available briefly for retry.", value.metadata)
                }
            }
        }
    }

    fun onBackground() {
        if (mutable.value.phase == ManualBackupPhase.PREPARING)
            cancel("Backup preparation was interrupted when the app left the foreground. Create it again; the vault is locked.")
    }

    fun pickerUnavailable() {
        val value = prepared ?: return
        mutable.value = ManualBackupState(ManualBackupPhase.READY, value.id,
            "No compatible file picker is available. The encrypted copy is kept briefly so you can retry.", value.metadata)
    }

    fun interruptedPicker() {
        if (prepared == null) cancel("The backup session was interrupted. Create a new backup; no completed save was confirmed.")
    }

    fun cancel(message: String? = null) {
        generation++
        operation?.cancel(); operation = null
        expiry?.cancel(); expiry = null
        ManualBackupFiles.discard(prepared); prepared = null
        mutable.value = ManualBackupState(if (message == null) ManualBackupPhase.IDLE else ManualBackupPhase.MESSAGE, message = message)
    }

    override fun onCleared() { cancel(); super.onCleared() }
}
