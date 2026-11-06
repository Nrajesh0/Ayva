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

internal data class BackupActions(val export: () -> Unit, val restore: () -> Unit, val busy: Boolean)

/** The only UI owner of full encrypted export/restore; all other entry points navigate here. */
@Composable
internal fun BackupFlow(onRestored: () -> Unit = {}, content: @Composable (BackupActions) -> Unit) {
    val context = LocalContext.current
    // Backup & Restore State
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var showExportVaultPin by remember { mutableStateOf(false) }
    var pendingPickerId by rememberSaveable { mutableStateOf<String?>(null) }
    val manual: ManualBackupViewModel = viewModel()
    val exportState by manual.state.collectAsStateWithLifecycle()
    val isPreparing = exportState.phase == ManualBackupPhase.PREPARING
    val isExporting = exportState.phase == ManualBackupPhase.SAVING
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingRestorePassword by remember { mutableStateOf<String?>(null) }
    var pendingRestoreCleanRestore by remember { mutableStateOf(false) }
    var showConfigureVaultPinDialog by remember { mutableStateOf(false) }
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
    DisposableEffect(lifecycleOwner, manual) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
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
            showRestorePasswordDialog = true
        }
    }


    val busy = isPreparing || isExporting || isRestoring || showConfigureVaultPinDialog ||
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
        busy = busy
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
        RestoreBackupPasswordDialog(
            isRestoring = isRestoring,
            onDismiss = {
                showRestorePasswordDialog = false
                pendingRestoreUri = null
                pendingRestorePassword = null
            },
            onConfirm = { password, cleanRestore ->
                isRestoring = true
                pendingRestorePassword = password
                pendingRestoreCleanRestore = cleanRestore
                coroutineScope.launch {
                    val result = try {
                        BackupRestoreManager.restoreEncryptedBackup(context, uri, password, cleanRestore)
                    } finally { isRestoring = false }
                    val ex = result.exceptionOrNull()
                    if (ex is BackupRestoreManager.RequiresVaultSetupException) {
                        showRestorePasswordDialog = false
                        showConfigureVaultPinDialog = true
                    } else {
                        showRestorePasswordDialog = false
                        pendingRestoreUri = null
                        pendingRestorePassword = null
                        if (result.isSuccess) {
                            val meta = result.getOrNull()

                            onRestored()
                            Toast.makeText(
                                context,
                                "Restore completed! Restored ${meta?.noteCount ?: 0} notes, ${meta?.taskCount ?: 0} tasks, ${meta?.habitCount ?: 0} habits and ${meta?.planCount ?: 0} plans.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Restore failed: ${ex?.message ?: "Unknown error"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        )
    }

    if (showConfigureVaultPinDialog && pendingRestoreUri != null && pendingRestorePassword != null) {
        val uri = pendingRestoreUri!!
        val password = pendingRestorePassword!!
        val cleanRestore = pendingRestoreCleanRestore
        ConfigureRestoredVaultPinDialog(
            isRestoring = isRestoring,
            onDismiss = {
                showConfigureVaultPinDialog = false
                pendingRestoreUri = null
                pendingRestorePassword = null
            },
            onConfirm = { pin ->
                isRestoring = true
                coroutineScope.launch {
                    val result = try {
                        BackupRestoreManager.restoreEncryptedBackup(
                            context = context,
                            sourceUri = uri,
                            password = password,
                            cleanRestore = cleanRestore,
                            vaultPin = pin
                        )
                    } finally { isRestoring = false }
                    showConfigureVaultPinDialog = false
                    pendingRestoreUri = null
                    pendingRestorePassword = null
                    if (result.isSuccess) {
                        val meta = result.getOrNull()

                        onRestored()
                        Toast.makeText(
                            context,
                            "Restore completed! Restored ${meta?.noteCount ?: 0} notes, ${meta?.taskCount ?: 0} tasks, ${meta?.habitCount ?: 0} habits and ${meta?.planCount ?: 0} plans.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Restore failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }
}
