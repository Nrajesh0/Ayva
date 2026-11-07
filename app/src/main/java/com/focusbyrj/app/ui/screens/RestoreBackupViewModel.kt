package com.focusbyrj.app.ui.screens

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.util.backup.AutomaticBackupStore
import com.focusbyrj.app.util.backup.BackupRestoreManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class RestorePhase { IDLE, CHECKING, REVIEW, UNLOCK_SAVED_KEY, UNLOCK, SETUP_PIN, APPLYING, SUCCESS, ERROR }
internal data class RestoreState(
    val phase: RestorePhase = RestorePhase.IDLE,
    val notes: Int = 0, val tasks: Int = 0, val replace: Boolean = true, val createdAt: Long = 0,
    val metadata: BackupRestoreManager.BackupMetadata? = null,
    val message: String? = null, val needsRecoveryFile: Boolean = false, val needsBackupFile: Boolean = false
)

/** Credentials never enter saved state. Verified preparation and one-use authorization stay in this session. */
internal class RestoreBackupViewModel(
    application: Application,
    private val storeFactory: (Context) -> AutomaticBackupStore
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, { AutomaticBackupStore(it) })
    private val mutable = MutableStateFlow(RestoreState())
    val state = mutable.asStateFlow()
    private var prepared: BackupRestoreManager.PreparedRestore? = null
    private var authorization: BackupRestoreManager.RestoreAuthorization? = null
    private var operation: Job? = null
    private var expiration: Job? = null
    private var pendingSource: Uri? = null
    private var replace = true
    private var generation = 0L

    fun prepare(uri: Uri, credential: String?, replaceCurrent: Boolean) {
        if (mutable.value.phase !in setOf(RestorePhase.IDLE, RestorePhase.ERROR, RestorePhase.SUCCESS)) return
        reset(); replace = replaceCurrent
        if (credential == null && ArchiveVaultSecurity.getVaultStatus(getApplication()) == ArchiveVaultSecurity.VaultStatus.ENABLED) {
            pendingSource = uri
            mutable.value = RestoreState(RestorePhase.UNLOCK_SAVED_KEY)
        } else prepareChecked(uri, credential)
    }

    /** Called only after the vault dialog verifies its PIN. The key is captured, then the global vault locks. */
    fun authorizeSavedKey() {
        if (mutable.value.phase != RestorePhase.UNLOCK_SAVED_KEY) return
        val source = pendingSource ?: return
        try {
            authorization = BackupRestoreManager.captureRestoreAuthorization(getApplication())
            pendingSource = null
            prepareChecked(source, null)
        } catch (error: Exception) {
            ArchiveVaultSecurity.lockVault()
            mutable.value = RestoreState(RestorePhase.ERROR, message = error.message, needsRecoveryFile = true)
        }
    }

    private fun prepareChecked(uri: Uri, credential: String?) {
        val request = generation
        val capability = authorization
        mutable.value = RestoreState(RestorePhase.CHECKING)
        operation = viewModelScope.launch {
            var owned: BackupRestoreManager.PreparedRestore? = null
            var keyReady = credential != null
            try {
                val secret = credential ?: withContext(Dispatchers.IO) {
                    val vaultKey = capability?.copyKey(getApplication())
                    try {
                        val store = storeFactory(getApplication())
                        // A new/disabled vault must never bypass the key's prior private-vault binding.
                        store.requireVaultIdentity(vaultKey)
                        store.key().use { it.credential() }
                    } finally { vaultKey?.fill(0) }
                }
                keyReady = true
                owned = BackupRestoreManager.prepareRestore(getApplication(), uri, secret).getOrThrow()
                ensureActive()
                if (request != generation) return@launch
                prepared = owned; owned = null
                val value = checkNotNull(prepared)
                mutable.value = RestoreState(RestorePhase.REVIEW, value.noteCount, value.taskCount, replace,
                    value.manifest?.optLong("createdAt", 0) ?: 0)
                expiration = viewModelScope.launch {
                    delay(600_000)
                    if (request == generation && mutable.value.phase in setOf(RestorePhase.REVIEW, RestorePhase.UNLOCK, RestorePhase.SETUP_PIN))
                        cancel("Restore expired. Choose the backup again; your data was not changed.")
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                capability?.close()
                if (request != generation) return@launch
                if (authorization === capability) authorization = null
                mutable.value = RestoreState(RestorePhase.ERROR,
                    message = error.message ?: "Could not check this backup. Your data was not changed.",
                    needsRecoveryFile = credential == null && (!keyReady || error is SecurityException),
                    needsBackupFile = error is BackupRestoreManager.BackupSourceUnavailableException)
            } finally { owned?.close() }
        }
    }

    fun acceptReview() {
        if (mutable.value.phase != RestorePhase.REVIEW) return
        val value = prepared ?: return
        when {
            authorization != null -> startApply(null)
            ArchiveVaultSecurity.getVaultStatus(getApplication()) == ArchiveVaultSecurity.VaultStatus.ENABLED ->
                mutable.value = mutable.value.copy(phase = RestorePhase.UNLOCK)
            value.hasPrivateNotes -> mutable.value = mutable.value.copy(phase = RestorePhase.SETUP_PIN)
            else -> startApply(null)
        }
    }

    fun onBackground() {
        if (mutable.value.phase in setOf(RestorePhase.CHECKING, RestorePhase.REVIEW, RestorePhase.UNLOCK_SAVED_KEY, RestorePhase.UNLOCK, RestorePhase.SETUP_PIN))
            cancel("Restore paused. Choose the backup again; your data was not changed.")
    }

    fun confirm(pin: String? = null) {
        if (mutable.value.phase !in setOf(RestorePhase.UNLOCK, RestorePhase.SETUP_PIN)) return
        startApply(pin)
    }
    private fun startApply(pin: String?) {
        mutable.value = mutable.value.copy(phase = RestorePhase.APPLYING)
        operation = viewModelScope.launch { apply(pin) }
    }

    private suspend fun apply(pin: String?) {
        val value = prepared ?: return
        val capability = authorization
        val request = generation
        expiration?.cancel()
        try {
            val metadata = BackupRestoreManager.applyPreparedRestore(getApplication(), value, replace, pin, capability).getOrThrow()
            if (request == generation) mutable.value = RestoreState(RestorePhase.SUCCESS, replace = replace, metadata = metadata)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (request == generation) mutable.value = RestoreState(RestorePhase.ERROR, message = error.message ?: "Restore did not finish. Your previous data is protected by recovery.")
        } finally {
            value.close()
            if (prepared === value) prepared = null
            capability?.close()
            if (authorization === capability) authorization = null
            ArchiveVaultSecurity.lockVault()
        }
    }

    fun cancel(message: String? = null) {
        reset()
        mutable.value = RestoreState(if (message == null) RestorePhase.IDLE else RestorePhase.ERROR, message = message)
    }

    /** Active writes finish rollback before their encrypted stages are discarded. */
    private fun reset() {
        generation++
        expiration?.cancel(); expiration = null
        pendingSource = null
        authorization?.close(); authorization = null
        val task = operation; operation = null
        val value = prepared; prepared = null
        task?.cancel()
        if (task?.isCompleted == false) task.invokeOnCompletion { value?.close() } else value?.close()
        ArchiveVaultSecurity.lockVault()
    }
    override fun onCleared() { reset(); super.onCleared() }
}
