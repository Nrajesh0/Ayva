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

@Composable fun DataRecoveryTools(onSnapshots: () -> Unit, entry: @Composable (onOpen: () -> Unit) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("home") }
    var show by remember { mutableStateOf(false) }
    var storage by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showDrafts by remember { mutableStateOf(false) }
    var drafts by remember { mutableIntStateOf(0) }
    var cleanup by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source = it; password = "" }
    fun inventory() { scope.launch {
        storage = withContext(Dispatchers.IO) { StorageInventory.read(context) }
        drafts = withContext(Dispatchers.IO) { File(context.filesDir, "editor_drafts").listFiles()?.count { it.extension == "enc" } ?: 0 }
    } }
    entry { show = true; page = "home"; inventory() }
    if (show) FeatureSheet(if (page == "home") "Data & recovery" else when(page) { "recovery" -> "Recovery"; "backup" -> "Backup verification"; else -> "Storage" },
        if (page == "home") "Keep your data safe and understand what's stored." else "", { if (!busy) show = false }) {
        if (page != "home") TextButton(enabled = !busy, onClick = { page = "home" }) { Icon(Icons.Default.ArrowBack, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Data & recovery") }
        when(page) {
            "home" -> FeatureGroup("") {
                FeatureRow("Recovery", "$drafts saved drafts · safety snapshots", Icons.Default.Restore, onClick = { page = "recovery" })
                FeatureRow("Verify a backup", "Check an encrypted archive before relying on it", Icons.Default.VerifiedUser, onClick = { page = "backup" })
                FeatureRow("Storage", "Usage breakdown & safe temporary-file cleanup", Icons.Default.Storage, onClick = { page = "storage" })
            }
            "recovery" -> {
                FeatureGroup("Restore your work") {
                    FeatureRow("Saved drafts", "$drafts drafts · review and recover text", Icons.Default.Description, enabled = !busy && drafts > 0, onClick = { showDrafts = true })
                    FeatureRow("Safety snapshots", "Review recovery points and what each contains", Icons.Default.History, enabled = !busy, onClick = onSnapshots)
                    if (MaintenanceGate.recoveryRequired) FeatureRow("Interrupted restore", "Retry recovery using the retained original data", Icons.Default.Restore, enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try { withContext(Dispatchers.IO) { RestoreJournal.recover(context) }; result = "Interrupted operation recovered." }
                            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; result = "Recovery could not finish: ${e.message}. Original recovery files are retained." }
                            finally { busy = false; inventory() }
                        }
                    })
                }
                Text("Drafts remain until saved or discarded. Metadata snapshots do not include attachments; full encrypted backups do.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "backup" -> {
                FeatureGroup("Check an archive") {
                    FeatureRow("Choose encrypted backup", "You’ll need the password used when it was created", Icons.Default.FolderOpen, enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) })
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
        Column { OutlinedTextField(password, { password = it }, enabled = !busy, label = { Text("Backup password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Text("The file is read only. No data is restored.", style = MaterialTheme.typography.bodySmall) }
    }, confirmButton = { TextButton(enabled = !busy && password.isNotBlank(), onClick = {
        val secret = password; password = ""; busy = true
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
