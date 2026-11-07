package com.focusbyrj.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.focusbyrj.app.ui.components.*

@Composable
fun DataRecoveryScreen(nav: NavController, onRestored: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val revision by com.focusbyrj.app.util.backup.AutomaticBackupStore.revision.collectAsState()
    val automaticStatus = remember(revision) { runCatching {
        val c = com.focusbyrj.app.util.backup.AutomaticBackupStore(context).config()
        when { c.problem.isNotBlank() -> "Needs attention"; c.keyId.isBlank() -> "Set up"; !c.enabled -> "Paused"; c.lastSuccess == 0L -> "No backup yet"
            c.nextDue > 0 && System.currentTimeMillis() > c.nextDue + 3_600_000L -> "Overdue"; else -> "Scheduled" }
    }.getOrDefault("Needs attention") }
    var snapshots by remember { mutableStateOf(false) }
    var recoveryTools by remember { mutableStateOf(false) }
    val target = SettingsSearchCatalog.id(LocalSettingsSearchTarget.current)
    LaunchedEffect(target) {
        if (target == "data.snapshots") snapshots = true
        if (target in setOf("data.verify", "data.snapshots", "data.repair", "data.drafts", "data.storage")) recoveryTools = true
    }
    SettingsMenuPage("Data & recovery", nav) {
        BackupFlow(onRestored) { actions ->
            SettingsMenuSectionHeader("ENCRYPTED BACKUPS")
            SettingsMenuLink(Icons.Default.Update, "Automatic backups", "data.automatic", nav, automaticStatus)
            SettingsMenuRow(Icons.Default.CloudUpload, "Create manual backup", Modifier.settingsSearchTarget("data.export")) {
                GrantButton("Create", enabled = !actions.busy, onClick = actions.export)
            }
            SettingsMenuRow(Icons.Default.CloudDownload, "Restore encrypted backup", Modifier.settingsSearchTarget("data.restore")) {
                GrantButton("Restore", enabled = !actions.busy, onClick = actions.restore)
            }
            Text("Automatic: recovery file. Manual: chosen password.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            TextButton(onClick = { recoveryTools = !recoveryTools }) { Text(if (recoveryTools) "Hide recovery tools" else "More recovery tools") }
            DataRecoveryTools { open ->
              if (com.focusbyrj.app.util.backup.MaintenanceGate.recoveryRequired && !recoveryTools)
                SettingsMenuRow(Icons.Default.Restore, "Finish interrupted recovery") { GrantButton("Review") { open("recovery") } }
              if (recoveryTools) {
                SettingsMenuRow(Icons.Default.VerifiedUser, "Verify a backup", Modifier.settingsSearchTarget("data.verify")) { GrantButton("Verify") { open("backup") } }
                SettingsMenuSectionHeader("RECOVER YOUR WORK")
                SettingsMenuRow(Icons.Default.History, "Safety snapshots", Modifier.settingsSearchTarget("data.snapshots")) { GrantButton("Open") { snapshots = true } }
                if (com.focusbyrj.app.util.backup.MaintenanceGate.recoveryRequired || target == "data.repair")
                    SettingsMenuRow(Icons.Default.Restore, "Interrupted restore", Modifier.settingsSearchTarget("data.repair")) { GrantButton("Review") { open("recovery") } }
                SettingsMenuRow(Icons.Default.Description, "Saved drafts", Modifier.settingsSearchTarget("data.drafts")) { GrantButton("Review") { open("drafts") } }
                SettingsMenuSectionHeader("ON THIS DEVICE")
                SettingsMenuRow(Icons.Default.Storage, "Storage", Modifier.settingsSearchTarget("data.storage")) { GrantButton("Manage") { open("storage") } }
                Text("Safety snapshots recover records. Full backups also recover attachments.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
        }
    }
    if (snapshots) SafetySnapshotsBottomSheet { snapshots = false }
}
