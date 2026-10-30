package com.focusbyrj.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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

@Composable fun DataRecoveryTools(onSnapshots: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    TextButton(onClick = { show = true; inventory() }) { Text("Data & recovery centre") }
    if (show) AlertDialog(onDismissRequest = { if (!busy) show = false }, title = { Text("Data & recovery") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recovery", style = MaterialTheme.typography.titleSmall)
            Text("$drafts saved editor drafts. Reopen the matching task, habit, or chat editor to resume. Drafts are kept until saved or discarded.", style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !busy && drafts > 0, onClick = { showDrafts = true }) { Text("Review saved drafts") }
            TextButton(enabled = !busy, onClick = onSnapshots) { Text("Review safety snapshots") }
            Text("Snapshots show their scope before restore. Full encrypted backups include attachments; metadata-only snapshots do not.", style = MaterialTheme.typography.bodySmall)
            if (MaintenanceGate.recoveryRequired) TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try { withContext(Dispatchers.IO) { RestoreJournal.recover(context) }; result = "Interrupted operation recovered. Your original data was restored." }
                    catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; result = "Recovery could not finish: ${e.message}. Original recovery files are retained." }
                    finally { busy = false; inventory() }
                }
            }) { Text("Retry interrupted recovery") }
            else Text("No interrupted restore is awaiting recovery.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) { Text("Verify an encrypted backup") }
            Text("Checks password, authenticated contents, structure, and attachment hashes without changing saved data.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Storage on this device", style = MaterialTheme.typography.titleSmall)
            storage?.forEach { (name, bytes) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, Modifier.weight(1f)); Text(android.text.format.Formatter.formatFileSize(context, bytes))
            } } ?: Text("Calculating…")
            Text("Backups saved through the file picker live outside this app's storage and are not included. Attachments may be shared or locked; none are treated as unused.", style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !busy, onClick = { cleanup = true }) { Text("Clean expired temporary exports") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = { show = false }) { Text("Close") } })
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
