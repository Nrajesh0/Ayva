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
    val target = SettingsSearchCatalog.id(LocalSettingsSearchTarget.current)
    LaunchedEffect(target) { if (target == "data.snapshots") snapshots = true }
    SettingsMenuPage("Data & recovery", nav) {
        BackupFlow(onRestored) { actions ->
            SettingsMenuSectionHeader("ENCRYPTED BACKUPS")
            Text("Manual backups use a password you choose. Automatic backups use an exported backup recovery key. Your vault PIN only unlocks private notes inside RUN; keep each backup's own password or key to restore it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp))
            SettingsMenuLink(Icons.Default.Update, "Automatic backups", "data.automatic", nav, automaticStatus)
            SettingsMenuRow(Icons.Default.CloudUpload, "Create manual backup", Modifier.settingsSearchTarget("data.export")) {
                GrantButton("Create", enabled = !actions.busy, onClick = actions.export)
            }
            Text("Private notes? Unlock here when asked—do not disable the vault. Preparation happens first, then you choose where to save the encrypted file. Only saved records and attachments are included.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp))
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
