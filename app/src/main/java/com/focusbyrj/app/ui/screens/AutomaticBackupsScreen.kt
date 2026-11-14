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
import androidx.compose.ui.platform.testTag
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomaticBackupsScreen(nav: NavController, onRestored: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { AutomaticBackupStore(context) }
    val revision by AutomaticBackupStore.revision.collectAsStateWithLifecycle()
    var config by remember { mutableStateOf(AutomaticBackupConfig()) }
    var configurationLoaded by remember { mutableStateOf(false) }
    var hasKey by remember { mutableStateOf(false) }
    var vaultAuthorized by remember { mutableStateOf(false) }
    var vaultConfigured by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var keyExportConsent by remember { mutableStateOf(false) }
    var authorizeWithExport by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(0) }
    var recentBackups by remember { mutableStateOf<List<ManagedBackup>>(emptyList()) }
    var showBackups by remember { mutableStateOf(false) }
    var ownershipLimit by remember { mutableStateOf(false) }
    var configurationDamaged by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val backgroundBusy by AutomaticBackups.running.collectAsStateWithLifecycle()
    val backupProgress by AutomaticBackups.progress.collectAsStateWithLifecycle()
    val pendingBackup by remember(context) { AutomaticBackups.pendingNow(context) }.collectAsStateWithLifecycle(initialValue = false)
    var error by remember { mutableStateOf<String?>(null) }
    var reconnectRecovery by remember { mutableStateOf(false) }
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
        configurationLoaded = false
        hasKey = store.hasKey()
        vaultConfigured = ArchiveVaultSecurity.getVaultStatus(context) == ArchiveVaultSecurity.VaultStatus.ENABLED
        try {
            val loaded = withContext(Dispatchers.IO) { store.config() }
            val records = withContext(Dispatchers.IO) { store.records().filter { it.stream == loaded.stream && it.keyId == loaded.keyId } }
            val ownership = records.size
            recentBackups = records.asReversed().take(30)
            config = loaded; count = ownership; vaultAuthorized = store.hasVaultAuthorization(); ownershipLimit = store.ownershipLimitReached(); configurationDamaged = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { configurationDamaged = true; error = "Backup setup needs repair. Existing backup files are kept." }
        configurationLoaded = true
    }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; info = null; reconnectRecovery = false
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { reconnectRecovery = e is BackupVaultChangedException; info = null; error = e.message ?: "The operation could not be completed. Existing backups were kept." }
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
    fun saveKeyExport(uri: Uri, keyId: String, asPdf: Boolean, relockVault: Boolean = false, authorize: Boolean = false) {
        if (busy) {
            if (relockVault) ArchiveVaultSecurity.lockVault()
            return
        }
        perform {
            try {
                if (authorize) AutomaticBackups.authorizeVault(context)
                AutomaticBackups.exportKey(context, uri, asPdf, keyId)
                if (createdKeyId == keyId) createdKeyId = null
                info = "Recovery file saved and checked. Keep it somewhere safe."
            } finally { if (relockVault) ArchiveVaultSecurity.lockVault() }
        }
    }
    fun requestKeyExport(uri: Uri, keyId: String, asPdf: Boolean) {
        if (ArchiveVaultSecurity.getVaultStatus(context) == ArchiveVaultSecurity.VaultStatus.ENABLED) {
            pendingExportUri = uri.toString(); pendingExportKeyId = keyId; pendingExportAsPdf = asPdf
            authorizeWithExport = !store.hasVaultAuthorization()
            keyExportConsent = authorizeWithExport
        } else saveKeyExport(uri, keyId, asPdf)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) perform {
            info = "Checking folder…"
            AutomaticBackups.destination(context, uri)
            if (!store.hasKey()) AutomaticBackups.installKey(context)
            info = "Folder ready. Save your recovery file next."
        }
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

    BackupFlow(onRestored, automaticRestore = true) { backupActions ->
      SettingsMenuPage("Automatic backups", nav) {
        val controlsEnabled = configurationLoaded && !busy && !backgroundBusy && !backupActions.busy
        val ready = hasKey && config.tree.isNotBlank() && config.keyExported && config.keyAcknowledged && (!vaultConfigured || vaultAuthorized)
        val step = when { config.tree.isBlank() -> 1; !hasKey || !config.keyExported -> 2; else -> 3 }
        Surface(shape = MaterialTheme.shapes.large,
            color = if (config.problem.isNotBlank()) MaterialTheme.colorScheme.errorContainer.copy(alpha = .4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag(if (configurationLoaded) "automatic_backup_loaded" else "automatic_backup_loading")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (backgroundBusy) BackupElapsedLabel()
                Text(if (!configurationLoaded) "Loading backup settings" else if (backgroundBusy) backupProgress else if (config.problem.isNotBlank()) "Needs attention" else if (pendingBackup) "Backup queued" else if (!ready) "Set up automatic backups" else backupHealth(config, now), style = MaterialTheme.typography.titleMedium)
                Text("Encrypted · Saved data and attachments", style = MaterialTheme.typography.bodySmall)
                if (configurationLoaded) Text("Last backup: ${backupTime(config.lastSuccess)}", style = MaterialTheme.typography.bodySmall)
                if (config.tree.isNotBlank()) Text("Folder: ${Uri.parse(config.tree).lastPathSegment.orEmpty().substringAfterLast('/').substringAfter(':')}", style = MaterialTheme.typography.bodySmall)
                if (config.enabled) Text("Next backup: ${backupTime(config.nextDue)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (busy || backgroundBusy || pendingBackup) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)) }
        if (reconnectRecovery) TextButton(enabled = controlsEnabled, onClick = { launchPicker { importPicker.launch(arrayOf("*/*")) } }) { Text("Reconnect recovery file") }
        info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)) }
        if (!configurationLoaded) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (configurationDamaged) {
            Text("Backup setup needs repair. Existing backup files are kept.")
            if (hasKey) Button(enabled = controlsEnabled, onClick = { perform { AutomaticBackups.repairConfiguration(context) } }) { Text("Repair setup") }
        } else if (!ready) {
            SettingsMenuSectionHeader("STEP $step OF 3")
            when (step) {
                1 -> {
                    Text("Choose where backups go", style = MaterialTheme.typography.titleMedium)
                    Text("Use a folder you can access outside the app.", style = MaterialTheme.typography.bodySmall)
                    Button(enabled = controlsEnabled, onClick = { launchPicker { folderPicker.launch(null) } }) { Text("Choose folder") }
                }
                2 -> {
                    Text("Save your recovery file", style = MaterialTheme.typography.titleMedium)
                    Text("It opens your backups after reinstalling or changing phones. Keep it safe and separate from backups.", style = MaterialTheme.typography.bodySmall)
                    Button(enabled = controlsEnabled, onClick = {
                        if (hasKey) {
                            pendingFileKeyId = config.keyId
                            launchPicker { keyPicker.launch("RUN-Recovery-Key-${config.keyId}.runkey") }
                        } else perform {
                            val id = AutomaticBackups.installKey(context)
                            pendingFileKeyId = id
                            launchPicker { keyPicker.launch("RUN-Recovery-Key-$id.runkey") }
                        }
                    }) { Text("Save recovery file") }
                }
                else -> {
                    Text("Ready to turn on", style = MaterialTheme.typography.titleMedium)
                    Text("${backupFrequencyLabel(config.frequency)} · Keep the latest ${config.keep} copies", style = MaterialTheme.typography.bodySmall)
                    Text("Your recovery file was saved and checked. Keep it safe.", style = MaterialTheme.typography.bodySmall)
                    if (vaultConfigured && !vaultAuthorized) {
                        Text("Include private notes with a one-time PIN confirmation.", style = MaterialTheme.typography.bodySmall)
                        FilledTonalButton(enabled = controlsEnabled, onClick = { vaultConsent = true }) { Text("Include private notes") }
                    }
                    Button(enabled = controlsEnabled && (!vaultConfigured || vaultAuthorized), onClick = { perform {
                        AutomaticBackups.acknowledgeKey(context, true, config.keyId)
                        AutomaticBackups.enable(context, true)
                        info = "Automatic backups are on. The first backup will run when Android allows it."
                    } }) { Text("Turn on automatic backups") }
                }
            }
        } else {
            SettingsMenuRow(Icons.Default.CloudUpload, "Automatic backups") {
                Switch(config.enabled, enabled = controlsEnabled, onCheckedChange = { enabled -> perform { AutomaticBackups.enable(context, enabled) } })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                Button(enabled = controlsEnabled && (!pendingBackup || config.problem.isNotBlank()), onClick = { perform {
                    AutomaticBackups.requestNow(context)
                    info = "Backup requested. Android will start it when storage is available."
                } }) { Text(if (config.problem.isNotBlank()) "Retry now" else if (pendingBackup) "Waiting to start" else "Back up now") }
                OutlinedButton(enabled = controlsEnabled, onClick = { if (recentBackups.isNotEmpty()) showBackups = true else backupActions.restore() }) { Text("Restore backup") }
            }
            Text(if (vaultConfigured) "Private notes included · Vault stays locked" else "All saved notes included", style = MaterialTheme.typography.bodySmall)
            SettingsMenuSectionHeader("HOW OFTEN")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(config.frequency.unit == "DAYS" && config.frequency.every == 1,
                    onClick = { perform { AutomaticBackups.configure(context, frequency = config.frequency.copy(unit = "DAYS", every = 1)) } },
                    enabled = controlsEnabled, label = { Text("Daily") })
                FilterChip(config.frequency.unit == "WEEKS" && config.frequency.every == 1,
                    onClick = { perform { AutomaticBackups.configure(context, frequency = config.frequency.copy(unit = "WEEKS", every = 1)) } },
                    enabled = controlsEnabled, label = { Text("Weekly") })
                TextButton(enabled = controlsEnabled, onClick = { showSchedule = true }) { Text("Custom") }
            }
            if (!(config.frequency.every == 1 && config.frequency.unit in setOf("DAYS", "WEEKS")))
                Text(backupFrequencyLabel(config.frequency), style = MaterialTheme.typography.bodySmall)
            Text("Android may delay a scheduled backup for battery or storage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ready) OutlinedButton(enabled = controlsEnabled, onClick = backupActions.restore, modifier = Modifier.padding(top = 16.dp)) { Text("Restore a backup instead") }
        TextButton(onClick = { advanced = !advanced }, modifier = Modifier.padding(vertical = 12.dp)) { Text(if (advanced) "Hide advanced options" else "Advanced options") }
        if (advanced) {
            SettingsMenuSectionHeader("BACKUP OPTIONS")
            SettingsMenuRow(Icons.Default.Folder, "Backup folder") {
                TextButton(enabled = controlsEnabled, onClick = { launchPicker { folderPicker.launch(config.tree.takeIf(String::isNotBlank)?.let(Uri::parse)) } }) { Text("Change") }
            }
            if (config.tree.isNotBlank()) Text(Uri.parse(config.tree).lastPathSegment.orEmpty().substringAfterLast('/'), style = MaterialTheme.typography.bodySmall)
            SettingsMenuRow(Icons.Default.History, "Keep ${config.keep} backups") { TextButton(modifier = Modifier.testTag("automatic_retention"), enabled = controlsEnabled, onClick = { showRetention = true }) { Text("Change") } }
            SettingsMenuRow(Icons.Default.BatteryChargingFull, "Only while charging") { Switch(config.chargingOnly, enabled = controlsEnabled, onCheckedChange = { perform { AutomaticBackups.configure(context, charging = it) } }) }
            SettingsMenuRow(Icons.Default.Schedule, "Custom schedule") { TextButton(enabled = controlsEnabled, onClick = { showSchedule = true }) { Text("Edit") } }
            Text("Old backups are removed only after a new backup verifies. Manual backups are kept.", style = MaterialTheme.typography.bodySmall)
            SettingsMenuSectionHeader("RECOVERY FILE")
            SettingsMenuRow(Icons.Default.FileOpen, "Use an existing recovery key") {
                Row {
                    TextButton(enabled = controlsEnabled, onClick = { launchPicker { importPicker.launch(arrayOf("*/*")) } }) { Text("Choose file") }
                    TextButton(enabled = controlsEnabled, onClick = { showKeyInput = true }) { Text("Enter key") }
                }
            }
            if (hasKey) {
                SettingsMenuRow(Icons.Default.PictureAsPdf, "Save recovery PDF") { TextButton(modifier = Modifier.testTag("automatic_recovery_pdf"), enabled = controlsEnabled, onClick = {
                    pendingPdfKeyId = config.keyId; launchPicker { pdfPicker.launch("RUN-Recovery-Key-${config.keyId}.pdf") }
                }) { Text("Save") } }
                SettingsMenuRow(Icons.Default.Description, "Save recovery key file") { TextButton(enabled = controlsEnabled, onClick = {
                    pendingFileKeyId = config.keyId; launchPicker { keyPicker.launch("RUN-Recovery-Key-${config.keyId}.runkey") }
                }) { Text("Save") } }
            }
            SettingsMenuRow(Icons.Default.Key, if (hasKey) "Replace recovery key" else "Create recovery key") {
                TextButton(enabled = controlsEnabled, onClick = { if (hasKey) newKeyConfirmation = true else createKey() }) { Text(if (hasKey) "Replace" else "Create") }
            }
            if (vaultConfigured || vaultAuthorized) {
                SettingsMenuSectionHeader("PRIVATE NOTES")
                Text("Your recovery file can open private notes in the backup. Keep it safe.", style = MaterialTheme.typography.bodySmall)
                SettingsMenuRow(Icons.Default.Lock, if (vaultAuthorized) "Private notes included" else "Private notes need permission") {
                    Row {
                        TextButton(enabled = controlsEnabled && hasKey, onClick = { vaultConsent = true }) { Text(if (vaultAuthorized) "Renew" else "Include") }
                        if (vaultAuthorized) TextButton(enabled = controlsEnabled, onClick = { confirmRevoke = true }) { Text("Revoke") }
                    }
                }
            }
            if (config.detail.isNotBlank()) Text(config.detail, style = MaterialTheme.typography.bodySmall)
            Text("$count verified backups managed here.", style = MaterialTheme.typography.bodySmall)
            if (ownershipLimit) Text("Some older copies were kept for manual review.", style = MaterialTheme.typography.bodySmall)
            Text("Keep a second copy off this phone. Unsaved text and local recovery snapshots are separate.", style = MaterialTheme.typography.bodySmall)
            SettingsMenuRow(Icons.Default.Notifications, "Failure notifications") { TextButton(onClick = {
                runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) }
            }) { Text("System") } }
        }
      }
      if (showBackups) AlertDialog(
        onDismissRequest = { showBackups = false },
        title = { Text("Choose backup") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recentBackups.forEachIndexed { index, backup ->
                OutlinedButton(enabled = !busy && !backgroundBusy && !backupActions.busy,
                    modifier = Modifier.fillMaxWidth().testTag("automatic_restore_${backup.created}"),
                    onClick = { showBackups = false; backupActions.restoreFile(Uri.parse(backup.uri)) }) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(backupTime(backup.created))
                        Text((if (index == 0) "Latest · " else "") + android.text.format.Formatter.formatShortFileSize(context, backup.size), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } },
        confirmButton = { TextButton(enabled = !busy && !backgroundBusy && !backupActions.busy, onClick = { showBackups = false; backupActions.restore() }) { Text("Choose another file") } },
        dismissButton = { TextButton(onClick = { showBackups = false }) { Text("Cancel") } }
      )
    }
    if (keyExportConsent) AlertDialog(
        onDismissRequest = { keyExportConsent = false; pendingExportUri = null; pendingExportKeyId = null; authorizeWithExport = false },
        title = { Text("Include private notes?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scheduled backups can include private notes while the vault stays locked.")
            Text("The recovery file opens these notes. Keep it safe.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(onClick = { keyExportConsent = false }) { Text("Include and continue") } },
        dismissButton = { TextButton(onClick = { keyExportConsent = false; pendingExportUri = null; pendingExportKeyId = null; authorizeWithExport = false }) { Text("Cancel") } }
    )
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
    if (exportUri != null && exportKeyId != null && !keyExportConsent) com.focusbyrj.app.ui.screens.notes.ArchiveVaultUnlockDialog(
        onDismiss = {
            pendingExportUri = null; pendingExportKeyId = null
            ArchiveVaultSecurity.lockVault()
            error = "Key export cancelled. No recovery key was written."
        },
        title = "Confirm vault PIN", description = "Save the recovery file. Vault protection stays on.",
        onVerify = { ArchiveVaultSecurity.verifyPasscode(context, it) },
        onSuccess = {
            val asPdf = pendingExportAsPdf
            pendingExportUri = null; pendingExportKeyId = null
            val includePrivate = authorizeWithExport
            authorizeWithExport = false
            saveKeyExport(Uri.parse(exportUri), exportKeyId, asPdf, relockVault = true, authorize = includePrivate)
        }
    )
    if (vaultConsent) AlertDialog(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { vaultConsent = false }, title = { Text("Authorize private-note backups?") },
        text = { Text("Allow scheduled backups to include private notes while the vault stays locked. The recovery file opens these notes. You can revoke access here.") },
        confirmButton = { TextButton(onClick = {
            vaultConsent = false
            val active = ArchiveVaultSecurity.getActiveVaultSubKey()
            if (active != null) { active.fill(0); perform { try { AutomaticBackups.authorizeVault(context) } finally { ArchiveVaultSecurity.lockVault() } } }
            else if (ArchiveVaultSecurity.getVaultStatus(context) == ArchiveVaultSecurity.VaultStatus.ENABLED) showVaultPin = true
            else error = "No private vault is configured. Ordinary saved notes are already included."
        }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { vaultConsent = false }) { Text("Cancel") } })
    if (showVaultPin) com.focusbyrj.app.ui.screens.notes.ArchiveVaultUnlockDialog(
        onDismiss = { showVaultPin = false }, onVerify = { ArchiveVaultSecurity.verifyPasscode(context, it) },
        onSuccess = { showVaultPin = false; perform { try { AutomaticBackups.authorizeVault(context); info = "Private-note backup access authorized on this phone. The vault is locked again. Keep the backup recovery key separate from your backup files." } finally { ArchiveVaultSecurity.lockVault() } } },
        title = "Authorize automatic backups",
        description = "One-time permission for private-note backups. Vault protection stays on.")
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

internal fun backupFrequencyLabel(value: BackupFrequency): String = when {
    value.every != 1 -> "Every ${value.every} ${value.unit.lowercase(java.util.Locale.ROOT)}"
    value.unit == "DAYS" -> "Daily"
    value.unit == "WEEKS" -> "Weekly"
    value.unit == "HOURS" -> "Hourly"
    else -> "Monthly"
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
