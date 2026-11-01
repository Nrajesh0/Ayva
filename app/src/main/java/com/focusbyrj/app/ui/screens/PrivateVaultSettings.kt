package com.focusbyrj.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.ui.components.*
import com.focusbyrj.app.ui.screens.notes.*

@Composable
fun PrivateVaultSettingsScreen(nav: NavController, viewModel: NotesViewModel) {
    SettingsMenuPage("Private notes vault", nav) { PrivateVaultSettingsPanel(viewModel) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateVaultSettingsSheet(viewModel: NotesViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Private notes vault", style = MaterialTheme.typography.titleLarge)
            PrivateVaultSettingsPanel(viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The same guarded controller is used from Settings and Notes > Archive. */
@Composable
private fun PrivateVaultSettingsPanel(viewModel: NotesViewModel) {
    val context = LocalContext.current
    val status by viewModel.vaultStatus.collectAsStateWithLifecycle()
    val unlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val backedUp by viewModel.isRecoveryPhraseBackedUp.collectAsStateWithLifecycle()
    val configured = status == ArchiveVaultSecurity.VaultStatus.ENABLED
    var recoveryAvailable by remember { mutableStateOf(viewModel.isVaultRecoveryConfigured()) }
    var setup by remember { mutableStateOf(false) }
    var changePin by remember { mutableStateOf(false) }
    var disable by remember { mutableStateOf(false) }
    var recover by remember { mutableStateOf(false) }
    var unlockingForPhrase by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf<List<String>?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun refresh() { viewModel.refreshVaultStatus(); recoveryAvailable = viewModel.isVaultRecoveryConfigured() }
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
            if (event == Lifecycle.Event.ON_STOP) {
                phrase = null
                setup = false
                changePin = false
                disable = false
                recover = false
                unlockingForPhrase = false
            }
        }
        lifecycle.addObserver(observer); refresh()
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun exportPhrase() {
        val words = viewModel.getOrConfigureRecoveryPhrase()
        if (words != null) phrase = words
        else Toast.makeText(context, "Unlock the vault to view its recovery phrase.", Toast.LENGTH_LONG).show()
    }
    SettingsMenuSectionHeader("PASSCODE & ACCESS")
    SettingsMenuRow(Icons.Default.Lock, "Vault PIN", Modifier.settingsSearchTarget("vault.pin")) {
        if (configured) GrantedBadge("Enabled") else GrantButton("Set up") { setup = true }
    }
    SettingsMenuRow(Icons.Default.Password, "Change vault PIN", Modifier.settingsSearchTarget("vault.change_pin")) {
        GrantButton("Change", enabled = configured, onClick = { changePin = true })
    }
    SettingsMenuRow(Icons.Default.Lock, "Lock private notes vault", Modifier.settingsSearchTarget("vault.lock")) {
        GrantButton(if (unlocked) "Lock" else "Locked", enabled = configured && unlocked, onClick = { viewModel.lockVault(); refresh() })
    }
    SettingsMenuSectionHeader("RECOVERY")
    SettingsMenuRow(Icons.Default.Key, "Back up recovery phrase", Modifier.settingsSearchTarget("vault.phrase")) {
        GrantButton(if (backedUp) "View" else "Back up", enabled = configured, onClick = { if (unlocked) exportPhrase() else unlockingForPhrase = true })
    }
    SettingsMenuRow(Icons.Default.Restore, "Recover vault access", Modifier.settingsSearchTarget("vault.recover")) {
        GrantButton("Recover", enabled = configured && recoveryAvailable, onClick = { recover = true })
    }
    Text(if (configured) "The 12-word phrase recovers this private-notes vault. Full encrypted backups use their own backup password."
        else "Set up a vault PIN to enable private-note locking and recovery. A PIN protects the notes vault, not the entire app.",
        Modifier.padding(horizontal = 6.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SettingsMenuSectionHeader("PROTECTION")
    SettingsMenuRow(Icons.Default.LockOpen, "Disable vault PIN", Modifier.settingsSearchTarget("vault.disable")) {
        GrantButton("Disable", enabled = configured, onClick = { disable = true })
    }
    if (setup) ArchiveVaultFirstTimeDialog(
        onDismiss = { setup = false }, onSkip = { setup = false },
        onPasscodeSet = { pin, words, confirmed ->
            if (viewModel.setVaultPasscode(pin, words, confirmed)) { setup = false; refresh() }
            else Toast.makeText(context, "The vault PIN could not be saved. Your existing data is unchanged.", Toast.LENGTH_LONG).show()
        })
    if (changePin) ArchiveVaultChangePasscodeDialog(onDismiss = { changePin = false },
        onVerifyCurrentPin = viewModel::verifyVaultPasscode, onSaveNewPin = { viewModel.setVaultPasscode(it, isPhraseBackedUp = backedUp) },
        onSuccess = { changePin = false; refresh() })
    if (disable) ArchiveVaultDisableConfirmDialog(onDismiss = { disable = false },
        onVerifyCurrentPin = viewModel::verifyVaultPasscode, onDisableConfirmed = {
            if (viewModel.disableVaultPasscode()) { disable = false; refresh() }
            else Toast.makeText(context, "Vault protection could not be disabled.", Toast.LENGTH_LONG).show()
        })
    if (unlockingForPhrase) ArchiveVaultUnlockDialog(onDismiss = { unlockingForPhrase = false },
        onVerify = viewModel::verifyVaultPasscode, onSuccess = { unlockingForPhrase = false; refresh(); exportPhrase() },
        initialLockoutSeconds = ArchiveVaultSecurity.getRemainingLockoutSeconds(context),
        onEmergencyRecoveryClick = { unlockingForPhrase = false; recover = true })
    phrase?.let { words -> ArchiveVaultExportPhraseDialog(words, backedUp,
        onDismiss = { phrase = null }, onMarkBackedUp = { viewModel.markRecoveryPhraseBackedUp(); phrase = null; refresh() }) }
    if (recover) ArchiveVaultMnemonicRecoveryDialog(onDismiss = { recover = false },
        onRecover = viewModel::recoverVaultWithMnemonic, onSuccess = { recover = false; refresh() })
}
