package com.focusbyrj.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    var isPreparing by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingRestorePassword by remember { mutableStateOf<String?>(null) }
    var pendingRestoreCleanRestore by remember { mutableStateOf(false) }
    var showConfigureVaultPinDialog by remember { mutableStateOf(false) }
    var exportErrorAlert by remember { mutableStateOf<String?>(null) }
    var exportSuccessMetadata by remember { mutableStateOf<BackupRestoreManager.BackupMetadata?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pendingExportPassword
        if (uri != null && password != null) {
            isExporting = true
            coroutineScope.launch {
                val result = try {
                    BackupRestoreManager.createEncryptedBackup(context, uri, password)
                } finally {
                    isExporting = false
                    pendingExportPassword = null
                }
                if (result.isSuccess) {
                    exportSuccessMetadata = result.getOrNull()
                } else {
                    exportErrorAlert = result.exceptionOrNull()?.message ?: "Backup export failed."
                }
            }
        } else {
            pendingExportPassword = null
            isExporting = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestorePasswordDialog = true
        }
    }


    val busy = isPreparing || isExporting || isRestoring || showConfigureVaultPinDialog ||
        showExportPasswordDialog || showRestorePasswordDialog || pendingExportPassword != null
    content(BackupActions(
        export = {
            if (!busy) {
                isPreparing = true
                coroutineScope.launch {
                    try {
                        val (allowed, reason) = BackupRestoreManager.canCreateBackup(context)
                        if (allowed) showExportPasswordDialog = true
                        else exportErrorAlert = reason ?: "Unlock your private notes vault before creating a backup."
                    } finally { isPreparing = false }
                }
            }
        },
        restore = {
            if (!busy) runCatching { importLauncher.launch(arrayOf("*/*")) }.onFailure {
                Toast.makeText(context, "No file picker is available to select a backup.", Toast.LENGTH_LONG).show()
            }
        },
        busy = busy
    ))

    if (showExportPasswordDialog) {
        ExportBackupPasswordDialog(
            isExporting = isExporting,
            onDismiss = {
                showExportPasswordDialog = false
                pendingExportPassword = null
            },
            onConfirm = { password ->
                showExportPasswordDialog = false
                pendingExportPassword = password
                val suggestedName = BackupRestoreManager.generateBackupFileName()
                runCatching { exportLauncher.launch(suggestedName) }.onFailure {
                    pendingExportPassword = null
                    exportErrorAlert = "No file picker is available to save the backup."
                }
            }
        )
    }

    if (isExporting) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { /* Modal: cannot dismiss while writing */ }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(
                            "Exporting Backup...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Encrypting databases and media. Please wait...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (exportSuccessMetadata != null) {
        val meta = exportSuccessMetadata!!
        AlertDialog(
            onDismissRequest = { exportSuccessMetadata = null },
            icon = { Icon(Icons.Filled.Security, contentDescription = null, tint = Color(0xFF10B981)) },
            title = { Text("Backup Saved Successfully") },
            text = {
                Text("Your encrypted backup has been saved and its authenticated archive read back successfully.\n\n• Notes: ${meta.noteCount}\n• Tasks: ${meta.taskCount}\n• Plans: ${meta.planCount}\n• Habits: ${meta.habitCount}\n• Drill Sessions: ${meta.drillCount}\n• App Restrictions: ${meta.restrictionCount}")
            },
            confirmButton = {
                Button(onClick = { exportSuccessMetadata = null }) {
                    Text("Done")
                }
            }
        )
    }

    if (exportErrorAlert != null) {
        AlertDialog(
            onDismissRequest = { exportErrorAlert = null },
            icon = { Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Backup Not Created") },
            text = {
                Text(exportErrorAlert ?: "Unknown backup error.")
            },
            confirmButton = {
                Button(onClick = { exportErrorAlert = null }) {
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
