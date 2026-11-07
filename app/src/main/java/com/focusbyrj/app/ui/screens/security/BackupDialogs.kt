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
                    "Protects this backup, including private notes. Keep the password safe; it is separate from your vault PIN.",
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestoreBackupPasswordDialog(
    isRestoring: Boolean,
    onDismiss: () -> Unit,
    automaticDefault: Boolean = false,
    hasSavedKey: Boolean = false,
    onSavedKey: ((Boolean) -> Unit)? = null,
    onConfirm: (password: String, cleanRestore: Boolean) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var automatic by remember { mutableStateOf(automaticDefault) }
    var savedKey by remember { mutableStateOf(hasSavedKey) }
    var replace by remember { mutableStateOf(true) }
    var advanced by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                try {
                    password = withContext(Dispatchers.IO) { BackupRecoveryDocuments.read(context, uri).use { it.credential() } }
                    automatic = true; savedKey = false; error = null
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "Could not open that recovery file. Choose your saved PDF or .runkey file." }
                finally { importing = false }
            }
        }
    }
    AlertDialog(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { if (!isRestoring && !importing) onDismiss() },
        title = { Text("Open backup") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(automatic, { automatic = true; error = null }, label = { Text("Automatic") })
                FilterChip(!automatic, { automatic = false; error = null }, label = { Text("Manual") })
            }
            if (automatic && savedKey && hasSavedKey && onSavedKey != null) {
                Text("Use the recovery key saved on this phone.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { savedKey = false }) { Text("Use another recovery key") }
            } else {
                if (automatic) {
                    FilledTonalButton(enabled = !importing && !isRestoring, onClick = {
                        runCatching { picker.launch(arrayOf("*/*")) }.onFailure { error = "No file picker is available." }
                    }) { Text(if (importing) "Reading recovery file…" else "Choose recovery file") }
                    Text("Your saved PDF or .runkey file.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(password, { password = it; error = null },
                    label = { Text(if (automatic) "Or enter recovery key" else "Backup password") },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show or hide credential")
                    } }, enabled = !importing && !isRestoring, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (!automatic) Text("Use the password you chose when saving this backup.", style = MaterialTheme.typography.bodySmall)
            }
            Text(if (replace) "Restore snapshot · no extra copies" else "Import as extra copies", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide restore options" else "Other restore options") }
            if (advanced) {
                Row(Modifier.fillMaxWidth().testTag("restore_replace_mode").toggleable(replace, role = Role.Checkbox, onValueChange = { replace = it }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(replace, onCheckedChange = null)
                    Text("Restore snapshot", Modifier.weight(1f))
                }
                Text(if (replace) "Current data will be replaced after confirmation." else "Adds extra records and applies backed-up settings.", style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(enabled = !isRestoring && !importing, onClick = {
            if (automatic && savedKey && hasSavedKey && onSavedKey != null) onSavedKey(replace)
            else try {
                require(password.isNotBlank()) { if (automatic) "Choose your recovery file or enter its key." else "Enter the backup password." }
                val secret = if (automatic) BackupRecoveryKey.parse(password).use { it.credential() } else password
                onConfirm(secret, replace)
            } catch (e: Exception) { error = e.message ?: "Check your recovery key." }
        }) { Text("Check backup") } },
        dismissButton = { TextButton(enabled = !importing && !isRestoring, onClick = onDismiss) { Text("Cancel") } }
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
                "Protect private notes",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    "Create a 6-digit vault PIN for the restored private notes.",
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
                    label = { Text("New vault PIN") },
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
                    label = { Text("Confirm vault PIN") },
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
