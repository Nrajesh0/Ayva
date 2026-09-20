package com.focusbyrj.app.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.focusbyrj.app.service.FocusDeviceAdminReceiver
import com.focusbyrj.app.ui.screens.security.ExportBackupPasswordDialog
import com.focusbyrj.app.ui.screens.security.RestoreBackupPasswordDialog
import com.focusbyrj.app.ui.theme.*
import com.focusbyrj.app.util.PermissionUtils
import com.focusbyrj.app.util.backup.BackupRestoreManager
import kotlinx.coroutines.launch

@Composable
fun SecurityScreen(navController: NavController) {
    val context = LocalContext.current
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val prefs = remember { context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE) }
    var secureRecents by remember { mutableStateOf(prefs.getBoolean("secure_recents", true)) }
    val adminComponent = ComponentName(context, FocusDeviceAdminReceiver::class.java)
    var isUninstallProtectionEnabled by remember { mutableStateOf(dpm.isAdminActive(adminComponent)) }
    
    var hasUsageStats by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    var isBatteryUnrestricted by remember { mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context)) }
    var hasNotifications by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }
    var showBatteryInfoDialog by remember { mutableStateOf(false) }
    
    // Backup & Restore State
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pendingExportPassword
        if (uri != null && password != null) {
            isExporting = true
            coroutineScope.launch {
                val result = BackupRestoreManager.createEncryptedBackup(context, uri, password)
                isExporting = false
                pendingExportPassword = null
                if (result.isSuccess) {
                    val meta = result.getOrNull()
                    Toast.makeText(
                        context,
                        "Backup encrypted & saved successfully! (${meta?.noteCount ?: 0} notes, ${meta?.taskCount ?: 0} tasks)",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Failed to create backup: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            pendingExportPassword = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestorePasswordDialog = true
        }
    }

    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isUninstallProtectionEnabled = dpm.isAdminActive(adminComponent)
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME || event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                isUninstallProtectionEnabled = dpm.isAdminActive(adminComponent)
                hasUsageStats = PermissionUtils.hasUsageStatsPermission(context)
                hasOverlay = PermissionUtils.hasOverlayPermission(context)
                isBatteryUnrestricted = PermissionUtils.isIgnoringBatteryOptimizations(context)
                hasNotifications = PermissionUtils.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "Security",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            SecuritySectionHeader("SYSTEM PERMISSIONS")
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SecurityActionRow(
                        icon = Icons.Filled.QueryStats,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Usage Access",
                        subtitle = "Detect foreground apps to block.",
                        action = {
                            if (hasUsageStats) {
                                GrantedBadge()
                            } else {
                                GrantButton { PermissionUtils.requestUsageStatsPermission(context) }
                            }
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    
                    SecurityActionRow(
                        icon = Icons.Filled.Layers,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = "Display Over Apps",
                        subtitle = "Show mindful pause & lock screens.",
                        action = {
                            if (hasOverlay) {
                                GrantedBadge()
                            } else {
                                GrantButton { PermissionUtils.requestOverlayPermission(context) }
                            }
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    
                    SecurityActionRow(
                        icon = Icons.Filled.BatteryFull,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = "Battery Restrictions",
                        subtitle = "Prevent Android from killing the blocker.",
                        action = {
                            if (isBatteryUnrestricted) {
                                GrantedBadge("Unrestricted ✓", MaterialTheme.colorScheme.tertiary)
                            } else {
                                GrantButton(text = "Enable", containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) { 
                                    showBatteryInfoDialog = true 
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SecuritySectionHeader("APP INTEGRITY & TAMPER DEFENSE")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SecurityActionRow(
                        icon = Icons.Filled.Security,
                        iconTint = Color(0xFFFFB74D),
                        title = "Uninstall Protection",
                        subtitle = "Prevent accidental app deletion.",
                        action = {
                            Button(
                                onClick = {
                                    if (isUninstallProtectionEnabled) {
                                        dpm.removeActiveAdmin(adminComponent)
                                        isUninstallProtectionEnabled = false
                                    } else {
                                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Prevent accidental app deletion during deep focus sessions.")
                                        }
                                        adminLauncher.launch(intent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(if (isUninstallProtectionEnabled) "Disable" else "Enable", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    
                    SecuritySwitchRow(
                        icon = Icons.Filled.VisibilityOff,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Anti-Screenshot Protection",
                        subtitle = "Prevents screenshots of the lock screen.",
                        checked = secureRecents,
                        onCheckedChange = { checked ->
                            secureRecents = checked
                            prefs.edit().putBoolean("secure_recents", checked).apply()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SecuritySectionHeader("DATA VAULT & BACKUP")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SecurityActionRow(
                        icon = Icons.Filled.CloudUpload,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Export Encrypted Backup",
                        subtitle = "AES-256 encrypted archive of all notes, tasks, habits, drills & settings.",
                        action = {
                            Button(
                                onClick = {
                                    showExportPasswordDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Export", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SecurityActionRow(
                        icon = Icons.Filled.CloudDownload,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        title = "Restore from Backup",
                        subtitle = "Decrypt and restore your data using your backup password.",
                        action = {
                            Button(
                                onClick = {
                                    importLauncher.launch(arrayOf("*/*"))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Restore", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }

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
                exportLauncher.launch(suggestedName)
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
            },
            onConfirm = { password, cleanRestore ->
                isRestoring = true
                coroutineScope.launch {
                    val result = BackupRestoreManager.restoreEncryptedBackup(context, uri, password, cleanRestore)
                    isRestoring = false
                    showRestorePasswordDialog = false
                    pendingRestoreUri = null
                    if (result.isSuccess) {
                        val meta = result.getOrNull()
                        Toast.makeText(
                            context,
                            "Restore completed! Restored ${meta?.noteCount ?: 0} notes, ${meta?.taskCount ?: 0} tasks, ${meta?.habitCount ?: 0} habits.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Restore failed: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }
    
    if (showBatteryInfoDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Why 'No Restrictions' on Battery?")
                }
            },
            text = {
                Column {
                    Text(
                        "Modern Android enforces aggressive background limits on apps when battery optimization is enabled (Optimized or Restricted).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Without 'No Restrictions' / 'Unrestricted', Android will freeze or kill the blocker background service, causing locks to stop working.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Setting this to Unrestricted allows RuN to guard your boundaries 24/7 without consuming significant battery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatteryInfoDialog = false
                        PermissionUtils.requestIgnoreBatteryOptimizations(context)
                    }
                ) {
                    Text("Set Unrestricted", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryInfoDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun GrantedBadge(text: String = "Granted ✓", color: Color = MaterialTheme.colorScheme.tertiary) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .border(0.8.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GrantButton(
    text: String = "Grant",
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}


@Composable
private fun SecuritySectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SecurityActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconTint.copy(alpha = 0.14f))
                .border(1.dp, iconTint.copy(alpha = 0.3f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        action()
    }
}

@Composable
private fun SecuritySwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SecurityActionRow(
        icon = icon,
        iconTint = iconTint,
        title = title,
        subtitle = subtitle,
        action = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    )
}
