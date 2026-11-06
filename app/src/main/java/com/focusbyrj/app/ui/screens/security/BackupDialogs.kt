/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.focusbyrj.app.util.backup.BackupRecoveryDocuments
import com.focusbyrj.app.util.backup.BackupRecoveryKey
import kotlinx.coroutines.*

@Composable
fun ExportBackupPasswordDialog(
    isExporting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { if (!isExporting) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Set manual backup password",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    "This password protects this manual backup, including private notes. It is separate from your vault PIN and automatic backup recovery key. Use a long, unique password and keep it safe. The encrypted backup is prepared before you choose a save location; you never need to disable vault protection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Backup Password") },
                    singleLine = true,
                    enabled = !isExporting,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (password.isNotEmpty()) {
                    val strengthHint = when {
                        password.length < 8 -> "Too short (min 8 characters required)"
                        password.length >= 12 && password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() } -> "Strong password 🔥"
                        password.length >= 8 && password.any { it.isDigit() } -> "Good password 👍"
                        else -> "Moderate (add numbers & symbols for higher security)"
                    }
                    val hintColor = if (password.length < 8) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(strengthHint, style = MaterialTheme.typography.labelSmall, color = hintColor)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    enabled = !isExporting,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (isExporting) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Encrypting & packaging data...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.trim().length < 8) {
                        errorMessage = "Password must contain at least 8 non-whitespace characters."
                    } else if (password != confirmPassword) {
                        errorMessage = "Passwords do not match."
                    } else {
                        onConfirm(password)
                    }
                },
                enabled = !isExporting,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Prepare encrypted backup")
            }
        },
        dismissButton = {
            if (!isExporting) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun RestoreBackupPasswordDialog(
    isRestoring: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String, cleanRestore: Boolean) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var cleanRestore by remember { mutableStateOf(false) }
    var confirmReplacement by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var recoveryKeyMode by remember { mutableStateOf(false) }
    var importingKey by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importingKey = true
            scope.launch {
                try {
                    password = withContext(Dispatchers.IO) { BackupRecoveryDocuments.read(context, uri).use { it.credential() } }
                    recoveryKeyMode = true; errorMessage = null
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { errorMessage = e.message ?: "Could not read recovery key." }
                finally { importingKey = false }
            }
        }
    }
    fun credential() = if (recoveryKeyMode) BackupRecoveryKey.parse(password).use { it.credential() } else password

    if (!confirmReplacement) AlertDialog(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { if (!isRestoring) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                Icons.Filled.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Restore Encrypted Backup",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    "Use the password for a manual backup, or the exported backup recovery key for an automatic backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text(if (recoveryKeyMode) "Backup recovery key" else "Backup Password") },
                    singleLine = true,
                    enabled = !isRestoring,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(recoveryKeyMode, enabled = !isRestoring && !importingKey, onCheckedChange = { recoveryKeyMode = it; errorMessage = null })
                    Text("Use recovery key", style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !isRestoring && !importingKey, onClick = {
                        runCatching { keyImport.launch(arrayOf("*/*")) }.onFailure { errorMessage = "No file picker is available." }
                    }) { Text(if (importingKey) "Reading…" else "Import PDF/key") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().testTag("restore_replace_mode")
                        .toggleable(value = cleanRestore, enabled = !isRestoring, role = Role.Checkbox, onValueChange = { cleanRestore = it }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = cleanRestore,
                        onCheckedChange = null,
                        enabled = !isRestoring
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Replace current data with this backup", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Records absent from the backup can be removed. Leave off to add records instead.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (isRestoring) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Decrypting & restoring data...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.isEmpty()) {
                        errorMessage = "Please enter the password."
                    } else {
                        try {
                            val secret = credential()
                            if (cleanRestore) confirmReplacement = true else onConfirm(secret, false)
                        } catch (e: Exception) { errorMessage = e.message ?: "Check the recovery key." }
                    }
                },
                enabled = !isRestoring && !importingKey,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Decrypt & Restore")
            }
        },
        dismissButton = {
            if (!isRestoring) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
    if (confirmReplacement) AlertDialog(
        onDismissRequest = { confirmReplacement = false },
        title = { Text("Replace current app data?") },
        text = { Text("This restores the backup's records and settings and can remove current records that are not in it, including changes made after the backup. Check that you chose the correct backup before continuing. A vault recovery phrase cannot replace this backup's password.") },
        confirmButton = { TextButton(enabled = !isRestoring, onClick = {
            confirmReplacement = false
            try { onConfirm(credential(), true) } catch (e: Exception) { errorMessage = e.message ?: "Check the recovery key." }
        }) { Text("Replace data & restore") } },
        dismissButton = { TextButton(onClick = { confirmReplacement = false }) { Text("Go back") } }
    )

}

@Composable
fun ConfigureRestoredVaultPinDialog(
    isRestoring: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (pin: String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isRestoring) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Secure Restored Vault Notes",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    "This backup contains private notes from the Secret Archive Vault. Set a 6-digit PIN to securely encrypt and initialize the vault on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        val digits = it.filter { char -> char.isDigit() }.take(6)
                        pin = digits
                        errorMessage = null
                    },
                    label = { Text("6-Digit Vault PIN") },
                    singleLine = true,
                    enabled = !isRestoring,
                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                if (pinVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        val digits = it.filter { char -> char.isDigit() }.take(6)
                        confirmPin = digits
                        errorMessage = null
                    },
                    label = { Text("Confirm 6-Digit PIN") },
                    singleLine = true,
                    enabled = !isRestoring,
                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (isRestoring) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Configuring vault & importing private notes...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length != 6) {
                        errorMessage = "PIN must be exactly 6 digits."
                    } else if (pin != confirmPin) {
                        errorMessage = "PINs do not match."
                    } else {
                        onConfirm(pin)
                    }
                },
                enabled = !isRestoring,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Set PIN & Restore")
            }
        },
        dismissButton = {
            if (!isRestoring) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
