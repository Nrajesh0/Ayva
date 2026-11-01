package com.focusbyrj.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.focusbyrj.app.ui.components.*

@Composable
fun DataRecoveryScreen(nav: NavController, onRestored: () -> Unit = {}) {
    var snapshots by remember { mutableStateOf(false) }
    val target = SettingsSearchCatalog.id(LocalSettingsSearchTarget.current)
    LaunchedEffect(target) { if (target == "data.snapshots") snapshots = true }
    SettingsMenuPage("Data & recovery", nav) {
        BackupFlow(onRestored) { actions ->
            SettingsMenuSectionHeader("ENCRYPTED BACKUPS")
            SettingsMenuRow(Icons.Default.CloudUpload, "Create encrypted backup", Modifier.settingsSearchTarget("data.export")) {
                GrantButton("Create", enabled = !actions.busy, onClick = actions.export)
            }
            SettingsMenuRow(Icons.Default.CloudDownload, "Restore encrypted backup", Modifier.settingsSearchTarget("data.restore")) {
                GrantButton("Restore", enabled = !actions.busy, onClick = actions.restore)
            }
            DataRecoveryTools { open ->
                SettingsMenuRow(Icons.Default.VerifiedUser, "Verify a backup", Modifier.settingsSearchTarget("data.verify")) { GrantButton("Verify") { open("backup") } }
                SettingsMenuSectionHeader("RECOVER YOUR WORK")
                SettingsMenuRow(Icons.Default.History, "Safety snapshots", Modifier.settingsSearchTarget("data.snapshots")) { GrantButton("Open") { snapshots = true } }
                if (com.focusbyrj.app.util.backup.MaintenanceGate.recoveryRequired || target == "data.repair")
                    SettingsMenuRow(Icons.Default.Restore, "Interrupted restore", Modifier.settingsSearchTarget("data.repair")) { GrantButton("Review") { open("recovery") } }
                SettingsMenuRow(Icons.Default.Description, "Saved drafts", Modifier.settingsSearchTarget("data.drafts")) { GrantButton("Review") { open("drafts") } }
                SettingsMenuSectionHeader("ON THIS DEVICE")
                SettingsMenuRow(Icons.Default.Storage, "Storage", Modifier.settingsSearchTarget("data.storage")) { GrantButton("Manage") { open("storage") } }
                Text("A full encrypted backup includes records and media. Local safety snapshots restore records; deleted attachments require a full backup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (snapshots) SafetySnapshotsBottomSheet { snapshots = false }
}
