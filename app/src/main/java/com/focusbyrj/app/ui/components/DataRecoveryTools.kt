package com.focusbyrj.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.focusbyrj.app.util.backup.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable fun DataRecoveryTools(entry: @Composable (onOpen: (String) -> Unit) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("backup") }
    var show by remember { mutableStateOf(false) }
    var storage by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var recoveryKeyMode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var showDrafts by remember { mutableStateOf(false) }
    var cleanup by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source = it; password = ""; recoveryKeyMode = false }
    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { busy = true; scope.launch {
            try { password = withContext(Dispatchers.IO) { BackupRecoveryDocuments.read(context, uri).use { it.credential() } }; recoveryKeyMode = true }
            catch(e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; result = e.message ?: "Could not read recovery key." }
            finally { busy = false }
        } }
    }
    fun inventory() { scope.launch {
        storage = withContext(Dispatchers.IO) { StorageInventory.read(context) }
    } }
    fun chooseBackup() {
        runCatching { picker.launch(arrayOf("*/*")) }.onFailure { result = "No file picker is available to select a backup." }
    }
    fun openPage(destination: String) {
        if (busy) return
        result = null
        if (destination == "drafts") showDrafts = true
        else { show = true; page = destination; inventory() }
    }
    val target = SettingsSearchCatalog.id(LocalSettingsSearchTarget.current)
    LaunchedEffect(target) {
        when (target) {
            "data.verify" -> { openPage("backup"); chooseBackup() }
            "data.repair" -> openPage("recovery")
            "data.drafts" -> openPage("drafts")
            "data.storage" -> openPage("storage")
            "data.cleanup" -> { openPage("storage"); cleanup = true }
        }
    }
    entry(::openPage)
    if (show) FeatureSheet(when (page) { "recovery" -> "Interrupted restore"; "backup" -> "Backup verification"; else -> "Storage" },
        "", { if (!busy) show = false }) {
        when(page) {
            "recovery" -> {
                FeatureGroup("Restore your work") {
                    if (MaintenanceGate.recoveryRequired) FeatureRow("Interrupted restore", "Retry recovery using the retained original data", Icons.Default.Restore, enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try { withContext(Dispatchers.IO) { RestoreJournal.recover(context) }; result = "Interrupted operation recovered." }
                            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; result = "Recovery could not finish: ${e.message}. Original recovery files are retained." }
                            finally { busy = false; inventory() }
                        }
                    })
                }
                Text(if (MaintenanceGate.recoveryRequired) "Original recovery files are kept until repair succeeds." else "No interrupted restore needs repair. Your saved data is unchanged.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "backup" -> {
                FeatureGroup("Check an archive") {
                    FeatureRow("Choose encrypted backup", "Use its password or exported backup recovery key", Icons.Default.FolderOpen, enabled = !busy, onClick = { chooseBackup() })
                }
                Text("Verification checks the authenticated contents, structure and attachment hashes. It does not restore data or replace anything in the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "storage" -> {
                FeatureGroup("On this device") {
                    storage?.forEach { (name, bytes) -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(android.text.format.Formatter.formatFileSize(context, bytes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } } ?: Text("Calculating…", Modifier.padding(16.dp))
                }
                FeatureGroup("Safe cleanup") {
                    FeatureRow("Expired temporary exports", "Remove shared files older than one hour", Icons.Default.CleaningServices, enabled = !busy, onClick = { cleanup = true })
                }
                Text("External file-picker backups are not included. Cleanup keeps attachments, databases, drafts and recovery points.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        result?.let { Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .4f)) { Text(it, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall) } }
    }
    if (showDrafts) DraftRecoveryDialog { showDrafts = false }
    source?.let { uri -> AlertDialog(onDismissRequest = { if (!busy) { source = null; password = "" } }, title = { Text("Verify backup") }, text = {
        Column { OutlinedTextField(password, { password = it }, enabled = !busy, label = { Text(if (recoveryKeyMode) "Backup recovery key" else "Backup password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(recoveryKeyMode, { recoveryKeyMode = it }, enabled = !busy)
                Text("Recovery key", style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !busy, onClick = { runCatching { keyPicker.launch(arrayOf("*/*")) }.onFailure { result = "No file picker is available." } }) { Text("Import") }
            }
            Text("The file is read only. No data is restored.", style = MaterialTheme.typography.bodySmall) }
    }, confirmButton = { TextButton(enabled = !busy && password.isNotBlank(), onClick = {
        val parsed = runCatching { if (recoveryKeyMode) BackupRecoveryKey.parse(password).use { it.credential() } else password }
        if (parsed.isFailure) { result = parsed.exceptionOrNull()?.message; return@TextButton }
        val secret = parsed.getOrThrow(); password = ""; busy = true
        scope.launch {
            try { val report = BackupRestoreManager.verifyEncryptedBackup(context, uri, secret)
                result = report.fold({ it }, { "Verification failed: ${it.message}. No saved data changed." })
            } finally { source = null; busy = false; inventory() }
        }
    }) { Text(if (busy) "Checking…" else "Verify") } }, dismissButton = { TextButton(enabled = !busy, onClick = { source = null; password = "" }) { Text("Cancel") } }) }
    if (cleanup) AlertDialog(onDismissRequest = { cleanup = false }, title = { Text("Clean expired exports?") }, text = { Text("Only temporary shared exports older than the app's expiry period will be removed. Recent shares, attachments, drafts, and backups are kept.") }, confirmButton = {
        TextButton(onClick = { cleanup = false; busy = true; scope.launch {
            try { withContext(Dispatchers.IO) { com.focusbyrj.app.ui.screens.notes.ArticleExporterHelper.pruneStaleExportFiles(File(context.cacheDir, "exports")) }; result = "Expired temporary exports cleaned." }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; result = e.message }
            finally { busy = false; inventory() }
        } }) { Text("Clean") }
    }, dismissButton = { TextButton(onClick = { cleanup = false }) { Text("Cancel") } })
}

internal object StorageInventory {
    /** Do not follow symlinks or decode private attachments to estimate storage. */
    fun size(root: File): Long {
        if (!root.exists() || java.nio.file.Files.isSymbolicLink(root.toPath())) return 0
        if (root.isFile) return root.length()
        return root.listFiles().orEmpty().sumOf { size(it) }
    }
    fun read(context: android.content.Context): List<Pair<String, Long>> = listOf(
        "Databases" to size(File(context.applicationInfo.dataDir, "databases")),
        "Attachments" to listOf("keep_images", "keep_audio", "keep_drawings").sumOf { size(File(context.filesDir, it)) },
        "Recovery snapshots" to size(File(context.filesDir, "auto_backups")),
        "Editor drafts" to size(File(context.filesDir, "editor_drafts")),
        "Temporary exports" to size(File(context.cacheDir, "exports")),
        "Other cache" to (size(context.cacheDir) - size(File(context.cacheDir, "exports"))).coerceAtLeast(0)
    )
}

@Composable private fun DraftRecoveryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) {
            File(context.filesDir, "editor_drafts").listFiles().orEmpty().filter { it.extension == "enc" }.sortedByDescending { it.lastModified() }.map { file ->
                runCatching {
                    require(file.length() <= 260_000)
                    val j = org.json.JSONObject(EncryptedSnapshotFile.read(file))
                    val title = j.optString("title").ifBlank { "Saved editor draft" }
                    val parts = listOf("title", "details", "description", "text", "input", "draft", "newStepText").mapNotNull { key ->
                        j.optString(key).takeIf { it.isNotBlank() }
                    }.toMutableList()
                    if (j.has("subtasks")) com.focusbyrj.app.data.Subtask.listFromJson(j.getString("subtasks")).forEach { parts.add("• ${it.title}") }
                    title to parts.joinToString("\n\n").ifBlank { "This draft contains editor settings. Reopen its original editor to resume it." }
                }.getOrElse { "Unreadable draft" to "This draft could not be authenticated or read. Its original file has been kept; no data was changed." }
            }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Saved drafts") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            Text("Review and copy text to recover it without replacing current data. Original drafts are kept.", style = MaterialTheme.typography.bodySmall)
            entries?.forEach { item -> TextButton(onClick = { selected = item }) { Text(item.first) } } ?: Text("Reading encrypted drafts…")
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
    selected?.let { item -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(item.first) }, text = {
        Text(item.second, Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()))
    }, confirmButton = { TextButton(onClick = {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Recovered draft", item.second).apply {
            if (android.os.Build.VERSION.SDK_INT >= 33) description.extras = android.os.PersistableBundle().apply { putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true) }
        })
        selected = null
    }) { Text("Copy text") } }, dismissButton = { TextButton(onClick = { selected = null }) { Text("Keep") } }) }
}
