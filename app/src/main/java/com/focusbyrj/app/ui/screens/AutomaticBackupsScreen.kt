package com.focusbyrj.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.ui.components.*
import com.focusbyrj.app.util.backup.*
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun AutomaticBackupsScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { AutomaticBackupStore(context) }
    val revision by AutomaticBackupStore.revision.collectAsStateWithLifecycle()
    var config by remember { mutableStateOf(AutomaticBackupConfig()) }
    var hasKey by remember { mutableStateOf(false) }
    var vaultAuthorized by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(0) }
    var ownershipLimit by remember { mutableStateOf(false) }
    var configurationDamaged by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val backgroundBusy by AutomaticBackups.running.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var showSchedule by remember { mutableStateOf(false) }
    var showRetention by remember { mutableStateOf(false) }
    var showKeyInput by remember { mutableStateOf(false) }
    var newKeyConfirmation by remember { mutableStateOf(false) }
    // Incoming credentials must never enter SavedState. Rotation cancels this confirmation.
    var importedKeyConfirmation by remember { mutableStateOf<String?>(null) }
    var vaultConsent by remember { mutableStateOf(false) }
    var showVaultPin by remember { mutableStateOf(false) }
    var confirmRevoke by remember { mutableStateOf(false) }
    var pendingPdfKeyId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFileKeyId by rememberSaveable { mutableStateOf<String?>(null) }
    var createdKeyId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportKeyId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportAsPdf by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(60_000) } }
    LaunchedEffect(revision) {
        hasKey = store.hasKey()
        try {
            val loaded = withContext(Dispatchers.IO) { store.config() }
            val ownership = withContext(Dispatchers.IO) { store.records().count { it.stream == loaded.stream && it.keyId == loaded.keyId } }
            config = loaded; count = ownership; vaultAuthorized = store.hasVaultAuthorization(); ownershipLimit = store.ownershipLimitReached(); configurationDamaged = false
        } catch (_: Exception) { configurationDamaged = true; error = "Automatic backup settings could not be read. Existing backup files were not changed. Repair setup using the protected key, or import your exported key." }
    }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; info = null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "The operation could not be completed. Existing backups were kept." }
            finally { busy = false }
        }
    }
    fun launchPicker(action: () -> Unit) { runCatching(action).onFailure { error = "No compatible file picker is available. Existing setup was kept." } }
    fun createKey(replace: Boolean = false) = perform {
        createdKeyId = AutomaticBackups.installKey(context, replace = replace)
    }
    suspend fun importKey(credential: String, confirmed: Boolean = false) {
        val incomingId = BackupRecoveryKey.parse(credential).use { it.id }
        val currentId = runCatching { store.config().keyId }.getOrDefault("")
        if (!confirmed && store.hasKey() && incomingId != currentId) {
            importedKeyConfirmation = credential
            return
        }
        AutomaticBackups.installKey(context, credential)
        info = "Recovery key imported. Export a copy and confirm it before enabling automatic backups. Private vault authorization must be renewed."
    }
    fun saveKeyExport(uri: Uri, keyId: String, asPdf: Boolean, relockVault: Boolean = false) {
        if (busy) {
            if (relockVault) ArchiveVaultSecurity.lockVault()
            return
        }
        perform {
            try {
                AutomaticBackups.exportKey(context, uri, asPdf, keyId)
                if (createdKeyId == keyId) createdKeyId = null
                info = if (asPdf) "Recovery PDF saved and read back successfully. Keep it separate from backups."
                    else "Recovery key file saved and read back successfully."
            } finally { if (relockVault) ArchiveVaultSecurity.lockVault() }
        }
    }
    fun requestKeyExport(uri: Uri, keyId: String, asPdf: Boolean) {
        if (ArchiveVaultSecurity.isVaultLocked(context)) {
            pendingExportUri = uri.toString(); pendingExportKeyId = keyId; pendingExportAsPdf = asPdf
        } else saveKeyExport(uri, keyId, asPdf)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) perform { AutomaticBackups.destination(context, uri); info = "Folder checked. Enable the schedule when setup is complete." }
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val expected = pendingPdfKeyId; pendingPdfKeyId = null
        if (uri != null && expected != null) requestKeyExport(uri, expected, true)
    }
    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val expected = pendingFileKeyId; pendingFileKeyId = null
        if (uri != null && expected != null) requestKeyExport(uri, expected, false)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform {
            val credential = withContext(Dispatchers.IO) { BackupRecoveryDocuments.read(context, uri).use { it.credential() } }
            importKey(credential)
        }
    }

    SettingsMenuPage("Automatic backups", nav) {
        val status = backupHealth(config, now)
        Surface(shape = MaterialTheme.shapes.large, color = if (config.problem.isNotBlank()) MaterialTheme.colorScheme.errorContainer.copy(alpha = .4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (backgroundBusy) "Creating and verifying backup" else status, style = MaterialTheme.typography.titleMedium)
                Text("Saved records and media · full encrypted backup", style = MaterialTheme.typography.bodySmall)
                Text("Last verified full backup: ${backupTime(config.lastSuccess)}", style = MaterialTheme.typography.bodySmall)
                if (config.lastAttempt > 0) Text("Last attempt: ${backupTime(config.lastAttempt)}", style = MaterialTheme.typography.bodySmall)
                Text(if (vaultAuthorized) "Private-note backup access: Authorized" else "Private-note backup access: Not authorized", style = MaterialTheme.typography.bodySmall)
                if (config.enabled) Text("Next due: ${backupTime(config.nextDue)}", style = MaterialTheme.typography.bodySmall)
                if (config.detail.isNotBlank()) Text(config.detail, style = MaterialTheme.typography.bodySmall)
                if (config.lastFile.isNotBlank()) Text(config.lastFile, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (busy || backgroundBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 10.dp)) }
        if (configurationDamaged && hasKey) TextButton(enabled = !busy && !backgroundBusy, onClick = { perform { AutomaticBackups.repairConfiguration(context); info = "Setup repaired using the same recovery key. Choose a folder and confirm your exported key before enabling backups." } }) { Text("Repair automatic backup setup") }
        info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 10.dp)) }
        SettingsMenuSectionHeader("RECOVERY KEY")
        Text("A recovery key unlocks these backups after reinstalling or changing phones. No password is needed for each scheduled run. Anyone with the key and backup can read the data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        SettingsMenuRow(Icons.Default.Key, if (hasKey && configurationDamaged) "Protected key retained" else if (hasKey) "Key · ${config.keyId.take(16)}…" else "Create recovery key") {
            TextButton(enabled = !busy && !backgroundBusy, onClick = { if (hasKey) newKeyConfirmation = true else createKey() }) { Text(if (hasKey) "New key" else "Create") }
        }
        SettingsMenuRow(Icons.Default.FileOpen, "Use an existing key") {
            Row { TextButton(enabled = !busy && !backgroundBusy, onClick = { launchPicker { importPicker.launch(arrayOf("*/*")) } }) { Text("Import") }
                TextButton(enabled = !busy && !backgroundBusy, onClick = { showKeyInput = true }) { Text("Enter") } }
        }
        if (hasKey) {
            SettingsMenuRow(Icons.Default.PictureAsPdf, "Export recovery PDF") { TextButton(enabled = !busy && !backgroundBusy, onClick = { pendingPdfKeyId = config.keyId; launchPicker { pdfPicker.launch("RUN-Recovery-Key-${config.keyId}.pdf") } }) { Text("Export") } }
            SettingsMenuRow(Icons.Default.Description, "Export recovery key file") { TextButton(enabled = !busy && !backgroundBusy, onClick = { pendingFileKeyId = config.keyId; launchPicker { keyPicker.launch("RUN-Recovery-Key-${config.keyId}.runkey") } }) { Text("Export") } }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Checkbox(config.keyAcknowledged, enabled = config.keyExported && !busy && !backgroundBusy, onCheckedChange = { checked -> perform { AutomaticBackups.acknowledgeKey(context, checked, config.keyId) } })
                Text("I have saved the exported key somewhere I can access after uninstalling. I will keep it separate from the backup folder.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
        SettingsMenuSectionHeader("DESTINATION & SCHEDULE")
        SettingsMenuRow(Icons.Default.Folder, if (config.tree.isBlank()) "Choose backup folder" else "Backup folder selected") {
            TextButton(enabled = !busy && !backgroundBusy, onClick = { launchPicker { folderPicker.launch(config.tree.takeIf(String::isNotBlank)?.let(Uri::parse)) } }) { Text(if (config.tree.isBlank()) "Choose" else "Change") }
        }
        if (config.tree.isNotBlank()) Text(Uri.parse(config.tree).lastPathSegment.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsMenuRow(Icons.Default.Schedule, "Every ${config.frequency.every} ${config.frequency.unit.lowercase().let { if (config.frequency.every == 1) it.removeSuffix("s") else it }}") { TextButton(enabled = !busy && !backgroundBusy, onClick = { showSchedule = true }) { Text("Set") } }
        SettingsMenuRow(Icons.Default.History, "Keep ${config.keep} verified backups") { TextButton(enabled = !busy && !backgroundBusy, onClick = { showRetention = true }) { Text("Set") } }
        SettingsMenuRow(Icons.Default.BatteryChargingFull, "Wait for charging") { Switch(config.chargingOnly, enabled = !busy && !backgroundBusy, onCheckedChange = { value -> perform { AutomaticBackups.configure(context, charging = value) } }) }
        SettingsMenuRow(Icons.Default.CloudUpload, "Automatic backups") {
            Switch(config.enabled, enabled = !busy && !backgroundBusy && hasKey && config.keyExported && config.keyAcknowledged && config.tree.isNotBlank(), onCheckedChange = { value -> perform { AutomaticBackups.enable(context, value) } })
        }
        SettingsMenuRow(Icons.Default.Backup, "Back up now") {
            TextButton(enabled = !busy && !backgroundBusy && config.keyAcknowledged && config.tree.isNotBlank(), onClick = { perform { AutomaticBackups.requestNow(context); info = "Backup requested. Check Last verified full backup to confirm completion." } }) { Text("Run") }
        }
        Text("Enabling schedules the first backup as soon as battery, storage and charging requirements allow. Later jobs follow your schedule.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$count verified backups managed for this setup. Older copies are removed only after a new archive verifies. Manual backups, other folders and other installations are kept. Failed deletions can leave more than the chosen limit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
        if (ownershipLimit) Text("Some older files are no longer automatically managed and were kept in their original folder. Review them manually if you need to free space.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        SettingsMenuSectionHeader("PRIVATE VAULT")
        Text("To include locked private notes unattended, authorize a protected backup-only copy of the vault key. It does not unlock the vault for normal app use. Your exported backup recovery key can read private notes in these archives without the original vault PIN. Without authorization, a full backup containing private notes fails rather than skipping them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsMenuRow(Icons.Default.Lock, if (vaultAuthorized) "Private vault authorized" else "Authorize private vault") {
            Row { TextButton(enabled = !busy && !backgroundBusy && hasKey, onClick = { vaultConsent = true }) { Text(if (vaultAuthorized) "Renew" else "Authorize") }
                if (vaultAuthorized) TextButton(enabled = !busy && !backgroundBusy, onClick = { confirmRevoke = true }) { Text("Revoke") } }
        }
        SettingsMenuSectionHeader("BACKUP HEALTH")
        Text("Android may delay jobs for battery, storage, force-stop or device restrictions. After reboot, unlock the phone once. Cloud folders depend on their provider, account and sync availability. Uninstalling stops future jobs; files already saved outside the app remain.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Saved records and attachments are included. Unsaved editor text and local safety snapshots are separate. Keep a second copy of important backups off this phone; a local folder does not survive device loss or a factory reset.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
        SettingsMenuRow(Icons.Default.Notifications, "Failure notifications") { TextButton(onClick = {
            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) }
        }) { Text("System") } }
    }
    if (confirmRevoke) AlertDialog(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { confirmRevoke = false },
        title = { Text("Revoke private-note backup access?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("The saved vault authorization and pending encrypted copies on this phone will be removed. Future full backups containing private notes will need authorization again. Existing backup files will be kept and can still be opened with their backup recovery key. Revoking access cannot revoke those exported files.") } },
        confirmButton = { TextButton(onClick = {
            confirmRevoke = false
            perform {
                val remaining = AutomaticBackups.revokeVault(context)
                info = if (remaining == 0) "Private-note backup access revoked. Pending copies were discarded; existing saved backups were kept."
                    else "Access revoked and pending requests invalidated. $remaining encrypted temporary copies could not be removed yet; cleanup will retry. Existing saved backups were kept."
            }
        }) { Text("Revoke access") } },
        dismissButton = { TextButton(onClick = { confirmRevoke = false }) { Text("Keep access") } }
    )
    if (showSchedule) BackupScheduleDialog(config.frequency, onDismiss = { showSchedule = false }) { frequency ->
        showSchedule = false; perform { AutomaticBackups.configure(context, frequency = frequency) }
    }
    if (showRetention) {
        var keep by remember { mutableStateOf(config.keep.toString()) }
        AlertDialog(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { showRetention = false }, title = { Text("Maximum backups") }, text = {
            Column { OutlinedTextField(keep, { keep = it.filter(Char::isDigit).take(3) }, label = { Text("Copies to keep · 1–365") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("Lowering the limit takes effect after the next verified backup. At least one copy is always kept.", style = MaterialTheme.typography.bodySmall) }
        }, confirmButton = { TextButton(enabled = keep.toIntOrNull() in 1..365, onClick = { showRetention = false; perform { AutomaticBackups.configure(context, keep = keep.toInt()) } }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showRetention = false }) { Text("Cancel") } })
    }
    if (showKeyInput) RecoveryKeyEntryDialog(onDismiss = { showKeyInput = false }) { key ->
        showKeyInput = false; perform { importKey(key) }
    }
    importedKeyConfirmation?.let { credential ->
        AlertDialog(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { importedKeyConfirmation = null },
            title = { Text("Use a different backup key?") },
            text = { Text("This replaces the current key and pauses automatic backups. Existing backups still need the current recovery document. Cancel and export it first if you have not kept a copy. Backup files will be kept, and private vault authorization must be renewed.") },
            confirmButton = { TextButton(enabled = !busy && !backgroundBusy, onClick = {
                importedKeyConfirmation = null; perform { importKey(credential, confirmed = true) }
            }) { Text("Use imported key") } },
            dismissButton = { TextButton(onClick = { importedKeyConfirmation = null }) { Text("Cancel") } })
    }
    if (newKeyConfirmation) AlertDialog(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { newKeyConfirmation = false }, title = { Text("Create a new backup key?") },
        text = { Text("Automatic backups will pause. Export the new key before enabling them again. Old backups still need the old key and will be kept. Keep every recovery document you still need. Private vault authorization must be renewed.") },
        confirmButton = { TextButton(onClick = { newKeyConfirmation = false; createKey(replace = true) }) { Text("Create new key") } },
        dismissButton = { TextButton(onClick = { newKeyConfirmation = false }) { Text("Cancel") } })
    createdKeyId?.takeIf { pendingExportUri == null }?.let { keyId ->
        RecoveryKeyExportDialog(
            busy = busy || backgroundBusy, error = error,
            onExportPdf = { pendingPdfKeyId = keyId; launchPicker { pdfPicker.launch("RUN-Recovery-Key-$keyId.pdf") } },
            onExportFile = { pendingFileKeyId = keyId; launchPicker { keyPicker.launch("RUN-Recovery-Key-$keyId.runkey") } },
            onDismiss = { createdKeyId = null }
        )
    }
    val exportUri = pendingExportUri
    val exportKeyId = pendingExportKeyId
    if (exportUri != null && exportKeyId != null) com.focusbyrj.app.ui.screens.notes.ArchiveVaultUnlockDialog(
        onDismiss = {
            pendingExportUri = null; pendingExportKeyId = null
            error = "Key export cancelled. No recovery key was written."
        },
        onVerify = { ArchiveVaultSecurity.verifyPasscode(context, it) },
        onSuccess = {
            val asPdf = pendingExportAsPdf
            pendingExportUri = null; pendingExportKeyId = null
            saveKeyExport(Uri.parse(exportUri), exportKeyId, asPdf, relockVault = true)
        }
    )
    if (vaultConsent) AlertDialog(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { vaultConsent = false }, title = { Text("Authorize private-note backups?") },
        text = { Text("RUN will keep an Android Keystore-protected backup copy of the vault key. Scheduled full backups can then include private notes while the vault is locked. Anyone with an archive and its exported backup recovery key can read those notes. Revoke this authorization here at any time.") },
        confirmButton = { TextButton(onClick = {
            vaultConsent = false
            val active = ArchiveVaultSecurity.getActiveVaultSubKey()
            if (active != null) { active.fill(0); perform { AutomaticBackups.authorizeVault(context) } }
            else if (ArchiveVaultSecurity.getVaultStatus(context) == ArchiveVaultSecurity.VaultStatus.ENABLED) showVaultPin = true
            else error = "No private vault is configured. Ordinary saved notes are already included."
        }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { vaultConsent = false }) { Text("Cancel") } })
    if (showVaultPin) com.focusbyrj.app.ui.screens.notes.ArchiveVaultUnlockDialog(
        onDismiss = { showVaultPin = false }, onVerify = { ArchiveVaultSecurity.verifyPasscode(context, it) },
        onSuccess = { showVaultPin = false; perform { try { AutomaticBackups.authorizeVault(context); info = "Private-note backup access authorized on this phone. The vault is locked again. Keep the backup recovery key separate from your backup files." } finally { ArchiveVaultSecurity.lockVault() } } },
        title = "Authorize automatic backups",
        description = "Enter your vault PIN to allow scheduled private-note backups while the vault stays locked. Your backup recovery key will unlock these archives.")
}

@Composable
internal fun RecoveryKeyExportDialog(
    busy: Boolean, error: String?, onExportPdf: () -> Unit, onExportFile: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Default.Key, contentDescription = null) },
        title = { Text("Save your recovery key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your new key is ready. Export it now so you can recover your backups after reinstalling. Keep it separate from your backup folder.")
                Text("Automatic backups stay off until you save and confirm the key.", style = MaterialTheme.typography.bodySmall)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onExportPdf) { Text("Export PDF") } },
        dismissButton = {
            Row {
                TextButton(enabled = !busy, onClick = onExportFile) { Text("Key file") }
                TextButton(enabled = !busy, onClick = onDismiss) { Text("Later") }
            }
        }
    )
}

internal fun backupTime(value: Long): String = if (value <= 0) "None yet" else Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm"))
internal fun backupHealth(config: AutomaticBackupConfig, now: Long = System.currentTimeMillis()): String = when {
    config.problem.isNotBlank() -> "Needs attention"
    config.keyId.isBlank() -> "Not configured"
    !config.enabled -> "Schedule paused"
    config.lastSuccess == 0L -> "Waiting for first verified backup"
    config.nextDue > 0 && now > config.nextDue + 3_600_000L -> "Backup overdue · run now"
    else -> "Automatic backups scheduled"
}

@Composable
internal fun RecoveryKeyEntryDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = onDismiss, title = { Text("Backup recovery key") }, text = {
        Column { OutlinedTextField(value, { value = it.take(512); error = null }, label = { Text("RUN-BK1 key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Enter the key from your exported recovery document. Spaces and line breaks are accepted. This is separate from a private vault recovery phrase.", style = MaterialTheme.typography.bodySmall) }
    }, confirmButton = { TextButton(onClick = { try { BackupRecoveryKey.parse(value).use { onConfirm(it.credential()) }; value = "" } catch(e: Exception) { error = e.message } }) { Text("Use key") } }, dismissButton = { TextButton(onClick = { value = ""; onDismiss() }) { Text("Cancel") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackupScheduleDialog(initial: BackupFrequency, onDismiss: () -> Unit, onSave: (BackupFrequency) -> Unit) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(initial) }; var every by remember { mutableStateOf(initial.every.toString()) }
    val max = when(value.unit) { "HOURS" -> 24; "DAYS" -> 365; "WEEKS" -> 52; else -> 12 }
    AlertDialog(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = onDismiss, title = { Text("Backup frequency") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("HOURS", "DAYS", "WEEKS", "MONTHS").forEach { unit ->
                FilterChip(value.unit == unit, { value = value.copy(unit = unit); every = "1" }, label = { Text(unit.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall) })
            } }
            OutlinedTextField(every, { every = it.filter(Char::isDigit).take(3) }, label = { Text("Every · 1–$max") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (value.unit != "HOURS") {
                val date = Instant.ofEpochMilli(value.anchor).atZone(ZoneOffset.UTC).toLocalDate()
                TextButton(onClick = { DatePickerDialog(context, { _, y,m,d -> value = value.copy(anchor = LocalDate.of(y,m+1,d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) }, date.year,date.monthValue-1,date.dayOfMonth).show() }) { Text("Starting date · $date") }
                TextButton(onClick = { TimePickerDialog(context, { _,h,m -> value = value.copy(hour=h,minute=m) },value.hour,value.minute,true).show() }) { Text("Preferred time · %02d:%02d".format(value.hour,value.minute)) }
                Text("Uses device time zone. Monthly schedules keep this day; shorter months use their last day.", style = MaterialTheme.typography.bodySmall)
            }
            Text("Android scheduling is approximate. A delayed job creates one backup, rather than a burst of missed copies.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = every.toIntOrNull() in 1..max, onClick = { onSave(value.copy(every = every.toInt(), anchor = if (value.unit == "HOURS" && value != initial) System.currentTimeMillis() else value.anchor)) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
