package com.focusbyrj.app.ui.screens

import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Alarm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import com.focusbyrj.app.ui.components.settingsSearchTarget
import com.focusbyrj.app.ui.components.SettingsMenuRow
import com.focusbyrj.app.ui.components.SettingsMenuSectionHeader
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.service.FocusDeviceAdminReceiver
import com.focusbyrj.app.ui.screens.notes.ArchiveVaultFirstTimeDialog
import com.focusbyrj.app.ui.screens.notes.ArchiveVaultMnemonicRecoveryDialog
import com.focusbyrj.app.ui.screens.notes.ArchiveVaultUnlockDialog
import com.focusbyrj.app.ui.screens.notes.ArchiveVaultExportPhraseDialog
import com.focusbyrj.app.ui.screens.security.ExportBackupPasswordDialog
import com.focusbyrj.app.ui.screens.security.RestoreBackupPasswordDialog
import com.focusbyrj.app.ui.screens.security.ConfigureRestoredVaultPinDialog
import com.focusbyrj.app.ui.theme.*
import com.focusbyrj.app.util.PermissionUtils
import com.focusbyrj.app.util.backup.BackupRestoreManager
import com.focusbyrj.app.util.backup.DataSafetyManager
import com.focusbyrj.app.util.diagnostics.DiagnosticManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SecurityScreen(navController: NavController) {
    val context = LocalContext.current
    val permissions by com.focusbyrj.app.ui.components.rememberDevicePermissions()
    val prefs = remember { context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE) }
    var secureRecents by com.focusbyrj.app.ui.components.rememberSetting(prefs, "secure_recents", false)
    val dpm = context.getSystemService(DevicePolicyManager::class.java)
    val admin = ComponentName(context, FocusDeviceAdminReceiver::class.java)
    var adminEnabled by remember { mutableStateOf(runCatching { dpm?.isAdminActive(admin) == true }.getOrDefault(false)) }
    var legacyLockAccess by remember { mutableStateOf(false) }
    var adminUpdating by remember { mutableStateOf(false) }
    var adminError by remember { mutableStateOf<String?>(null) }
    val adminScope = rememberCoroutineScope()
    val adminSupported = remember(context) {
        dpm != null && context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_DEVICE_ADMIN)
    }
    fun refreshAdminState() {
        try {
            val active = dpm?.isAdminActive(admin) == true
            val legacy = active && dpm?.hasGrantedPolicy(admin, android.app.admin.DeviceAdminInfo.USES_POLICY_FORCE_LOCK) == true
            adminEnabled = active
            legacyLockAccess = legacy
            adminError = null
        } catch (_: Exception) {
            adminError = "Android could not read device-admin access. Check device-admin apps in Android Settings."
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshAdminState() }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshAdminState()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    com.focusbyrj.app.ui.components.SettingsMenuPage("Security & permissions", navController) {
        SettingsMenuSectionHeader("DEVICE PERMISSIONS")
        com.focusbyrj.app.util.DevicePermission.entries.filter { it.applicable }.forEach { permission ->
            val icon = when(permission) {
                com.focusbyrj.app.util.DevicePermission.USAGE -> Icons.Default.QueryStats
                com.focusbyrj.app.util.DevicePermission.OVERLAY -> Icons.Default.Layers
                com.focusbyrj.app.util.DevicePermission.BATTERY -> Icons.Default.BatteryFull
                com.focusbyrj.app.util.DevicePermission.NOTIFICATIONS -> Icons.Default.Notifications
                com.focusbyrj.app.util.DevicePermission.ALARMS -> Icons.Default.Alarm
            }
            SettingsMenuRow(icon, permission.title, Modifier.settingsSearchTarget(permission.settingId),
                onClick = if (permission == com.focusbyrj.app.util.DevicePermission.NOTIFICATIONS) ({ permission.request(context) }) else null) {
                if (permission in permissions.granted) GrantedBadge("Allowed") else GrantButton { permission.request(context) }
            }
            HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
        }
        Text("Usage access and overlays support app blocking. Battery exemption is optional. Notifications show reminders; without alarm access, Android may deliver them later.", Modifier.padding(horizontal = 6.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsMenuSectionHeader("PRIVACY & PROTECTION")
        SettingsMenuRow(Icons.Default.Security, "Uninstall protection", Modifier.settingsSearchTarget("privacy.uninstall")) {
            GrantButton(text = if (adminUpdating) "Updating…" else if (adminEnabled) "Disable" else "Enable",
                enabled = !adminUpdating && (adminSupported || adminEnabled)) {
                if (!adminUpdating) {
                    adminUpdating = true
                    adminError = null
                    adminScope.launch {
                        try {
                            val manager = checkNotNull(dpm) { "Device-admin service is unavailable" }
                            if (adminEnabled) {
                                manager.removeActiveAdmin(admin)
                                // Removal can complete asynchronously. Keep the displayed state
                                // tied to Android's result rather than claiming it is already off.
                                kotlinx.coroutines.withTimeoutOrNull(2_000L) {
                                    while (manager.isAdminActive(admin)) kotlinx.coroutines.delay(100)
                                }
                                refreshAdminState()
                                if (adminEnabled) adminError = "Android is still updating device-admin access. Reopen this screen to check the result."
                            } else {
                                launcher.launch(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Optional uninstall protection. You can turn it off here or in Android Settings.")
                                })
                            }
                        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                        catch (_: Exception) {
                            adminError = "Android could not change device-admin access. Check device-admin apps in Android Settings."
                        } finally { adminUpdating = false }
                    }
                }
            }
        }
        Text(if (adminSupported || adminEnabled)
            "Optional device-admin access. Android may require you to disable it before uninstalling. You can turn it off here or in Android Settings."
            else "Device-admin access is unavailable on this device. Other app features remain available.",
            Modifier.padding(horizontal = 6.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (legacyLockAccess) Text(
            "Earlier administrator access may still include screen-lock authority. Disable and re-enable uninstall protection to apply the reduced access. Your saved data is kept.",
            Modifier.padding(horizontal = 6.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        adminError?.let { Text(it, Modifier.padding(horizontal = 6.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        SettingsMenuRow(Icons.Default.VisibilityOff, "Screenshot protection", Modifier.settingsSearchTarget("privacy.screenshots")) {
            Switch(checked = secureRecents, onCheckedChange = { enabled ->
                secureRecents = enabled
                com.focusbyrj.app.ui.components.writeSetting(context, "secure_recents", enabled)
                var current: Context? = context
                while (current is android.content.ContextWrapper && current !is android.app.Activity) current = current.baseContext
                if (current is com.focusbyrj.app.MainActivity) current.updateSecureWindowFlags()
                else (current as? android.app.Activity)?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            })
        }
        Text("Screenshot protection covers this app's screens and previews. Your launcher controls home-screen widgets.", Modifier.padding(horizontal = 6.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        com.focusbyrj.app.ui.components.SettingsMenuLink(Icons.Default.Lock, "Private notes vault", "vault.home", navController)
    }
}

@Composable
fun GrantedBadge(text: String = "Granted ✓", color: Color = MaterialTheme.colorScheme.tertiary) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GrantButton(
    text: String = "Grant",
    containerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetySnapshotsBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var snapshots by remember { mutableStateOf(DataSafetyManager.listAvailableSnapshots(context)) }
    var selectedSnapshotForRestore by remember { mutableStateOf<DataSafetyManager.SnapshotInfo?>(null) }
    var isRestoring by remember { mutableStateOf(false) }
    var isCreatingSnapshot by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy · HH:mm:ss", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Safety Snapshots",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Device-only records; deleted attachments are not included",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = {
                        isCreatingSnapshot = true
                        coroutineScope.launch {
                            val db = NoteDatabase.getInstance(context)
                            // CONFLICT-4 fix: pass focusDb so tasks, habits, schedules &
                            // restrictions are included in the manually triggered snapshot.
                            val focusDb = (context.applicationContext as? com.focusbyrj.app.FocusApplication)?.database
                            val ok = DataSafetyManager.writeDailyBackup(context, db.noteDao(), focusDb)
                            isCreatingSnapshot = false
                            if (ok) {
                                snapshots = DataSafetyManager.listAvailableSnapshots(context)
                                Toast.makeText(context, "Safety snapshot created", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to create snapshot", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isCreatingSnapshot,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(if (isCreatingSnapshot) "Creating..." else "Snapshot Now", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (snapshots.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No safety snapshots yet",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Snapshots are created automatically every 24 hours\nand before destructive actions like emptying trash.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(snapshots, key = { it.absolutePath }) { snap ->
                        // BUG-2 + BUG-7 fix: note/task counts are now pre-cached in SnapshotInfo
                        // by listAvailableSnapshots() — no per-item file reads needed here.
                        val formattedDate = remember(snap.createdAtMs) {
                            dateFormat.format(Date(snap.createdAtMs))
                        }
                        val sizeKb = (snap.sizeBytes / 1024L).coerceAtLeast(1L)
                        // Build a human-readable content summary from pre-cached counts.
                        val contentSummary = remember(snap.noteCount, snap.taskCount) {
                            buildString {
                                if (snap.noteCount >= 0) append("${snap.noteCount} notes")
                                if (snap.taskCount >= 0) {
                                    if (isNotEmpty()) append(", ")
                                    append("${snap.taskCount} tasks")
                                }
                                if (isEmpty()) append("Snapshot")
                                append(" · ${sizeKb} KB")
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val (badgeBg, badgeFg) = when {
                                            snap.tag.startsWith("Daily") ->
                                                MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                                            snap.tag.contains("Emergency") ->
                                                MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                                            else ->
                                                MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = badgeBg
                                        ) {
                                            Text(
                                                text = snap.tag,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = badgeFg,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Text(
                                            text = formattedDate,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = contentSummary,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Button(
                                    onClick = { selectedSnapshotForRestore = snap },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text("Restore", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedSnapshotForRestore?.let { snap ->
        AlertDialog(
            onDismissRequest = { if (!isRestoring) selectedSnapshotForRestore = null },
            icon = {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Restore Snapshot?") },
            text = {
                // Build a content summary from cached counts for the confirm dialog.
                val parts = mutableListOf<String>()
                if (snap.noteCount >= 0) parts.add("${snap.noteCount} notes")
                if (snap.taskCount >= 0) parts.add("${snap.taskCount} tasks")
                val contentDesc = if (parts.isNotEmpty()) parts.joinToString(", ") else "data"
                Text(
                    "This will restore $contentDesc from '${snap.fileName}'. " +
                    "A safety snapshot of your current database will be saved first. " +
                    "Snapshots restore records only; missing attachments need a full encrypted backup."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isRestoring = true
                        coroutineScope.launch {
                            val db = NoteDatabase.getInstance(context)
                            // CONFLICT-5 fix: pass focusDb so tasks, habits, schedules &
                            // restrictions in the snapshot are also restored, not just notes.
                            val focusDb = (context.applicationContext as? com.focusbyrj.app.FocusApplication)?.database
                            val result = DataSafetyManager.restoreSnapshot(context, snap.absolutePath, db.noteDao(), focusDb)
                            isRestoring = false
                            selectedSnapshotForRestore = null
                            if (result.isSuccess) {
                                Toast.makeText(
                                    context,
                                    "Successfully restored ${result.getOrNull()} items!",
                                    Toast.LENGTH_LONG
                                ).show()
                                snapshots = DataSafetyManager.listAvailableSnapshots(context)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Restore failed: ${result.exceptionOrNull()?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isRestoring
                ) {
                    Text(if (isRestoring) "Restoring..." else "Confirm Restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedSnapshotForRestore = null },
                    enabled = !isRestoring
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
