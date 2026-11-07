package com.focusbyrj.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.ui.screens.notes.ArchiveVaultUnlockDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.focusbyrj.app.ui.screens.security.ConfigureRestoredVaultPinDialog
import com.focusbyrj.app.ui.screens.security.ExportBackupPasswordDialog
import com.focusbyrj.app.ui.screens.security.RestoreBackupPasswordDialog
import com.focusbyrj.app.util.backup.BackupRestoreManager
import kotlinx.coroutines.launch

internal data class BackupActions(val export: () -> Unit, val restore: () -> Unit, val busy: Boolean, val restoreFile: (android.net.Uri) -> Unit)

/** The only UI owner of full encrypted export/restore; all other entry points navigate here. */
@Composable
internal fun BackupFlow(onRestored: () -> Unit = {}, automaticRestore: Boolean = false, content: @Composable (BackupActions) -> Unit) {
    val context = LocalContext.current
    // Backup & Restore State
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    val restore: RestoreBackupViewModel = viewModel()
    val restoreState by restore.state.collectAsStateWithLifecycle()
    val isRestoring = restoreState.phase in setOf(RestorePhase.CHECKING, RestorePhase.APPLYING)
    var restoreAutomaticHint by remember { mutableStateOf(automaticRestore) }
    val backupRevision by com.focusbyrj.app.util.backup.AutomaticBackupStore.revision.collectAsStateWithLifecycle()
    val hasSavedRecoveryKey = remember(backupRevision) { com.focusbyrj.app.util.backup.AutomaticBackupStore(context).hasKey() }
    var requireRecoveryFile by remember { mutableStateOf(false) }
    var showExportVaultPin by remember { mutableStateOf(false) }
    var pendingPickerId by rememberSaveable { mutableStateOf<String?>(null) }
    val manual: ManualBackupViewModel = viewModel()
    val exportState by manual.state.collectAsStateWithLifecycle()
    val isPreparing = exportState.phase == ManualBackupPhase.PREPARING
    val isExporting = exportState.phase == ManualBackupPhase.SAVING
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportSuccessMetadata = exportState.takeIf { it.phase == ManualBackupPhase.SAVED }?.metadata
    val coroutineScope = rememberCoroutineScope()

    fun finishExport() { pendingPickerId = null; manual.cancel() }

    // Each prepared archive owns a separate ActivityResult registration. Reading
    // the latest pending ID in a shared callback could misattribute an older result
    // to a newer archive. The restored marker only identifies a lost session.
    val registeredSessionId = exportState.id ?: pendingPickerId
    val exportLauncher = key(registeredSessionId) {
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            // Retain this non-secret marker through copying to report process loss.
            if (uri == null && pendingPickerId == registeredSessionId) pendingPickerId = null
            manual.save(registeredSessionId, uri)
        }
    }

    // No credential or vault key enters SavedState. A restored picker can only use a
    // still-live verified ciphertext session; process death requires starting again.
    LaunchedEffect(exportState.phase) {
        if (exportState.phase in setOf(ManualBackupPhase.READY, ManualBackupPhase.SAVED, ManualBackupPhase.MESSAGE)) pendingPickerId = null
        if (exportState.phase == ManualBackupPhase.IDLE && pendingPickerId != null) {
            pendingPickerId = null
            manual.interruptedPicker()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, manual, restore) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if ((context as? android.app.Activity)?.isChangingConfigurations != true) restore.onBackground()
                if (showExportVaultPin || showExportPasswordDialog) {
                    showExportVaultPin = false; showExportPasswordDialog = false
                    ArchiveVaultSecurity.lockVault()
                    manual.cancel("Backup entry was interrupted. Start again; your vault is locked and no backup was saved.")
                }
                manual.onBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            manual.onBackground()
            if (showExportVaultPin || showExportPasswordDialog) ArchiveVaultSecurity.lockVault()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            requireRecoveryFile = false
            restoreAutomaticHint = automaticRestore || hasSavedRecoveryKey || uri.toString().contains("RUN-Auto-")
            showRestorePasswordDialog = true
        }
    }


    val busy = isPreparing || isExporting || restoreState.phase != RestorePhase.IDLE ||
        showExportVaultPin || showExportPasswordDialog || showRestorePasswordDialog ||
        exportState.phase in setOf(ManualBackupPhase.READY, ManualBackupPhase.PICKER)
    content(BackupActions(
        export = {
            if (!busy) {
                finishExport()
                if (ArchiveVaultSecurity.getVaultStatus(context) == ArchiveVaultSecurity.VaultStatus.ENABLED)
                    showExportVaultPin = true
                else showExportPasswordDialog = true
            }
        },
        restore = {
            if (!busy) runCatching { importLauncher.launch(arrayOf("*/*")) }.onFailure {
                Toast.makeText(context, "No file picker is available to select a backup.", Toast.LENGTH_LONG).show()
            }
        },
        busy = busy,
        restoreFile = { uri ->
            if (!busy) {
                pendingRestoreUri = uri
                restoreAutomaticHint = true
                requireRecoveryFile = !hasSavedRecoveryKey
                if (hasSavedRecoveryKey) restore.prepare(uri, null, true)
                else showRestorePasswordDialog = true
            }
        }
    ))

    if (showExportVaultPin) {
        ArchiveVaultUnlockDialog(
            onDismiss = { showExportVaultPin = false; ArchiveVaultSecurity.lockVault() },
            onVerify = { ArchiveVaultSecurity.verifyPasscode(context, it) },
            onSuccess = { showExportVaultPin = false; showExportPasswordDialog = true },
            title = "Unlock for this backup",
            description = "Enter your vault PIN here to include private notes. You do not need to leave this screen or disable the vault. This does not authorize automatic backups."
        )
    }
    if (showExportPasswordDialog) {
        ExportBackupPasswordDialog(
            isExporting = false,
            onDismiss = { showExportPasswordDialog = false; ArchiveVaultSecurity.lockVault() },
            onConfirm = { password ->
                // A second queued click can arrive before the dialog recomposes.
                if (showExportPasswordDialog) {
                    showExportPasswordDialog = false
                    manual.prepare(password)
                }
            }
        )
    }
    if (isPreparing || isExporting) {
        AlertDialog(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = {},
            title = { Text(if (isPreparing) "Preparing encrypted backup" else "Saving encrypted backup") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (isPreparing) "If you use the vault, it remains locked. Saved records and attachments are being encrypted and verified before you choose a location."
                    else "Only the encrypted archive is being copied. The saved file will be read back and checked before success is confirmed.")
            } },
            confirmButton = {},
            dismissButton = { TextButton(onClick = {
                pendingPickerId = null
                manual.cancel(if (isExporting) "Saving stopped. The destination may contain an incomplete file. No saved app data was changed."
                    else "Preparation cancelled. No saved app data was changed; the vault remains locked.")
            }) { Text("Cancel") } }
        )
    }
    if (exportState.phase == ManualBackupPhase.READY) {
        AlertDialog(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { finishExport() },
            title = { Text("Encrypted backup ready") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                exportState.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("If you use the vault, it remains locked. Choose where to save the verified encrypted archive. You will need your manual backup password to restore it.")
                Text("This temporary encrypted copy expires after 10 minutes. Cancelling discards it without changing your saved data.", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { Button(onClick = {
                val request = manual.chooseLocation()
                if (request != null) {
                    pendingPickerId = request
                    runCatching { exportLauncher.launch(BackupRestoreManager.generateBackupFileName()) }.onFailure {
                        pendingPickerId = null; manual.pickerUnavailable()
                    }
                }
            }) { Text("Choose save location") } },
            dismissButton = { TextButton(onClick = { finishExport() }) { Text("Discard") } }
        )
    }

    if (exportSuccessMetadata != null) {
        val meta = exportSuccessMetadata!!
        AlertDialog(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { finishExport() },
            icon = { Icon(Icons.Filled.Security, contentDescription = null, tint = Color(0xFF10B981)) },
            title = { Text("Backup Saved Successfully") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Your backup was saved and read back successfully, matching the verified encrypted archive. Restore it with your manual backup password, not your vault PIN. Keep that password safe.\n\n• Notes: ${meta.noteCount}\n• Tasks: ${meta.taskCount}\n• Plans: ${meta.planCount}\n• Habits: ${meta.habitCount}\n• Drill Sessions: ${meta.drillCount}\n• App Restrictions: ${meta.restrictionCount}")
                }
            },
            confirmButton = {
                Button(onClick = { finishExport() }) {
                    Text("Done")
                }
            }
        )
    }

    val manualError = exportState.takeIf { it.phase == ManualBackupPhase.MESSAGE }?.message
    if (manualError != null) {
        AlertDialog(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { finishExport() },
            icon = { Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Backup Not Created") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) { Text(manualError.orEmpty()) }
            },
            confirmButton = {
                Button(onClick = { finishExport() }) {
                    Text("OK")
                }
            }
        )
    }

    if (showRestorePasswordDialog && pendingRestoreUri != null) {
        val uri = pendingRestoreUri!!
        fun start(credential: String?, replace: Boolean) {
            if (!showRestorePasswordDialog) return
            showRestorePasswordDialog = false
            restore.prepare(uri, credential, replace)
        }
        RestoreBackupPasswordDialog(
            isRestoring = isRestoring,
            onDismiss = { showRestorePasswordDialog = false; pendingRestoreUri = null },
            automaticDefault = restoreAutomaticHint,
            hasSavedKey = hasSavedRecoveryKey && !requireRecoveryFile,
            onSavedKey = { start(null, it) },
            onConfirm = { credential, replace -> start(credential, replace) }
        )
    }
    LaunchedEffect(restoreState.phase) {
        if (restoreState.phase == RestorePhase.SUCCESS) onRestored()
    }
    when (restoreState.phase) {
        RestorePhase.CHECKING, RestorePhase.APPLYING -> AlertDialog(
            onDismissRequest = {},
            title = { Text(if (restoreState.phase == RestorePhase.CHECKING) "Checking backup" else "Restoring data") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (restoreState.phase == RestorePhase.CHECKING) "Checking the key and every saved file." else "Protecting current data and saving the restored records.")
                Text("Keep the app open until this finishes.", style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = {},
            dismissButton = { if (restoreState.phase == RestorePhase.CHECKING) TextButton(onClick = { restore.cancel() }) { Text("Cancel") } }
        )
        RestorePhase.REVIEW -> RestoreReviewDialog(restoreState, restore::acceptReview) { restore.cancel() }
        RestorePhase.UNLOCK_SAVED_KEY, RestorePhase.UNLOCK -> {
            var verifiedPin by remember { mutableStateOf<String?>(null) }
            ArchiveVaultUnlockDialog(
                onDismiss = { verifiedPin = null; restore.cancel(); ArchiveVaultSecurity.lockVault() },
                onVerify = { pin -> ArchiveVaultSecurity.verifyPasscode(context, pin).also {
                    if (it is ArchiveVaultSecurity.VerifyResult.Success) verifiedPin = pin
                } },
                onSuccess = { val pin = verifiedPin; verifiedPin = null
                    if (restoreState.phase == RestorePhase.UNLOCK_SAVED_KEY) restore.authorizeSavedKey() else restore.confirm(pin)
                },
                title = "Confirm your vault PIN",
                description = if (restoreState.phase == RestorePhase.UNLOCK_SAVED_KEY) "Use this phone’s saved recovery key. Vault protection stays on." else "Restore private data here. Vault protection stays on."
            )
        }
        RestorePhase.SETUP_PIN -> ConfigureRestoredVaultPinDialog(
            isRestoring = false, onDismiss = { restore.cancel() }, onConfirm = { restore.confirm(it) }
        )
        RestorePhase.SUCCESS -> AlertDialog(
            onDismissRequest = { pendingRestoreUri = null; restore.cancel() },
            title = { Text("Restore complete") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (restoreState.replace) "Your data now matches the backup." else "Additional copies were imported.")
                restoreState.metadata?.let { Text("${it.noteCount} notes · ${it.taskCount} tasks · ${it.habitCount} habits") }
                Text("Your backup file was kept.", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { Button(onClick = { pendingRestoreUri = null; restore.cancel() }) { Text("Done") } }
        )
        RestorePhase.ERROR -> {
            var details by remember { mutableStateOf(false) }
            AlertDialog(onDismissRequest = { restore.cancel() }, title = { Text("Restore not completed") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (com.focusbyrj.app.util.backup.MaintenanceGate.recoveryRequired) "Restart the app to finish recovery. Keep your backup file."
                        else if (restoreState.needsBackupFile) "Choose the backup file again. Its folder may be unavailable."
                        else if (restoreState.needsRecoveryFile) "This backup needs its original recovery file."
                        else "Check the recovery key and available storage, then try again.")
                    TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "Details") }
                    if (details) Text(restoreState.message.orEmpty(), style = MaterialTheme.typography.bodySmall)
                } },
                confirmButton = { TextButton(onClick = {
                    val chooseFile = restoreState.needsBackupFile
                    requireRecoveryFile = restoreState.needsRecoveryFile
                    restore.cancel()
                    if (chooseFile) {
                        pendingRestoreUri = null
                        runCatching { importLauncher.launch(arrayOf("*/*")) }.onFailure {
                            Toast.makeText(context, "No backup file picker is available.", Toast.LENGTH_LONG).show()
                        }
                    } else if (pendingRestoreUri != null) showRestorePasswordDialog = true
                }) { Text(if (restoreState.needsBackupFile) "Choose backup" else "Try again") } },
                dismissButton = { TextButton(onClick = { pendingRestoreUri = null; restore.cancel() }) { Text("Close") } }
            )
        }
        RestorePhase.IDLE -> Unit
    }
}

/** Shared confirmation surface: opening/checking a file never authorizes replacement. */
@Composable
internal fun RestoreReviewDialog(state: RestoreState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text(if (state.replace) "Restore this snapshot?" else "Import extra copies?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Backup: ${backupTime(state.createdAt)}")
                Text("${state.notes} notes · ${state.tasks} tasks")
                Text(if (state.replace) "Current records and settings will match this backup. Later changes will be replaced."
                    else "Adds extra records and applies backed-up settings.")
                Text("Current notes and tasks get a safety snapshot first.", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { Button(onClick = onConfirm) { Text(if (state.replace) "Restore backup" else "Import copies") } },
            dismissButton = { TextButton(onClick = { onDismiss() }) { Text("Cancel") } }
        )
}
