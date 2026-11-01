package com.focusbyrj.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.focusbyrj.app.BuildConfig
import com.focusbyrj.app.ui.components.*
import com.focusbyrj.app.util.DevicePermission
import com.focusbyrj.app.util.diagnostics.DiagnosticManager
import kotlinx.coroutines.launch

@Composable
fun HelpSettingsScreen(nav: NavController, onSetup: (() -> Unit)? = null) {
    val context = LocalContext.current
    val permissions by rememberDevicePermissions()
    var showSetup by remember { mutableStateOf(false) }
    fun setup() { if (onSetup != null) onSetup() else showSetup = true }
    val target = SettingsSearchCatalog.id(LocalSettingsSearchTarget.current)
    LaunchedEffect(target) { if (target == "help.setup") setup() }
    SettingsMenuPage("Help & about", nav) {
        SettingsMenuSectionHeader("HELP")
        SettingsMenuRow(Icons.Default.Info, "Permission setup", Modifier.settingsSearchTarget("help.setup"), onClick = ::setup) { Text("Open", style = MaterialTheme.typography.labelMedium) }
        SettingsMenuLink(Icons.Default.QueryStats, "Diagnostics", "help.diagnostics", nav)
        SettingsMenuSectionHeader("ABOUT")
        SettingsMenuRow(Icons.Default.Info, "App version", Modifier.settingsSearchTarget("help.version")) { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.labelMedium) }
        SettingsMenuRow(Icons.Default.Code, "GitHub", Modifier.settingsSearchTarget("help.github"), onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(VIZAG_GITHUB_URL))) }
                .onFailure { Toast.makeText(context, "No app available to open GitHub.", Toast.LENGTH_SHORT).show() }
        }) { Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp)) }
    }
    if (showSetup) SetupPermissionsDialog(
        DevicePermission.USAGE in permissions.granted, DevicePermission.OVERLAY in permissions.granted,
        DevicePermission.BATTERY in permissions.granted, DevicePermission.NOTIFICATIONS in permissions.granted,
        hasExactAlarms = DevicePermission.ALARMS in permissions.granted, onDismiss = { showSetup = false })
}

@Composable
fun DiagnosticsSettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recording by remember { mutableStateOf(DiagnosticManager.isRecordingEnabled()) }
    var exporting by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var storage by remember { mutableStateOf(DiagnosticManager.getFormattedDiagnosticStorage(context)) }
    SettingsMenuPage("Diagnostics", nav) {
        SettingsMenuSectionHeader("TROUBLESHOOTING")
        SettingsMenuRow(Icons.Default.QueryStats, "Record diagnostics", Modifier.settingsSearchTarget("diagnostics.record")) {
            Switch(recording, { enabled -> recording = enabled; DiagnosticManager.setRecordingEnabled(enabled) })
        }
        Text("Diagnostic recording captures app events for troubleshooting. Export creates a shareable log bundle; it is separate from your encrypted data backup.", Modifier.padding(horizontal = 6.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsMenuRow(Icons.Default.Share, "Export diagnostic logs", Modifier.settingsSearchTarget("diagnostics.export")) {
            GrantButton(if (exporting) "Exporting…" else "Export", enabled = !exporting, onClick = { scope.launch {
                exporting = true
                try {
                    DiagnosticManager.createDiagnosticZip(context).fold(
                        onSuccess = { DiagnosticManager.shareDiagnosticBundle(context, it) },
                        onFailure = { Toast.makeText(context, "Could not export diagnostics: ${it.message}", Toast.LENGTH_LONG).show() })
                    storage = DiagnosticManager.getFormattedDiagnosticStorage(context)
                } finally { exporting = false }
            } })
        }
        SettingsMenuRow(Icons.Default.Delete, "Clear diagnostic logs", Modifier.settingsSearchTarget("diagnostics.clear")) {
            GrantButton("Clear · $storage", enabled = !exporting, onClick = { clearing = true })
        }
    }
    if (clearing) AlertDialog(onDismissRequest = { clearing = false }, title = { Text("Clear diagnostic logs?") },
        text = { Text("Recorded diagnostic logs and their exported bundles will be removed. Notes, tasks, attachments and data backups are kept.") },
        confirmButton = { TextButton(onClick = { clearing = false; DiagnosticManager.clearAllLogs(context); storage = DiagnosticManager.getFormattedDiagnosticStorage(context) }) { Text("Clear logs") } },
        dismissButton = { TextButton(onClick = { clearing = false }) { Text("Cancel") } })
}
