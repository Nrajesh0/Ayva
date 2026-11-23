package com.focusbyrj.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.focusbyrj.app.ui.screens.BackupElapsedLabel
import com.focusbyrj.app.util.backup.MaintenanceGate
import com.focusbyrj.app.util.backup.RestoreJournal
import com.focusbyrj.app.util.backup.RestoreNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One input barrier for journaled writes and failed recovery. The editor stays composed behind it.
 */
@Composable
internal fun DataMaintenanceSurface(
    maintaining: Boolean,
    recoveryBlocked: Boolean,
    onRecovered: () -> Unit,
    onClose: () -> Unit,
) {
    if (!maintaining && !recoveryBlocked) return
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val progress by MaintenanceGate.progress.collectAsState()
    var retrying by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                securePolicy = SecureFlagPolicy.SecureOn,
                usePlatformDefaultWidth = !recoveryBlocked,
            ),
    ) {
        Surface(
            modifier = if (recoveryBlocked) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                Modifier.padding(28.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    if (recoveryBlocked) "Recovery needs attention" else "Protecting your data",
                    style = MaterialTheme.typography.titleLarge,
                )
                if (maintaining || retrying) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(progress)
                    BackupElapsedLabel()
                    Text(
                        "Keep the app open until this finishes.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (recoveryBlocked) {
                    Text(
                        if (failed) "Recovery could not finish. Check free storage, then retry."
                        else "An interrupted restore must be recovered before continuing."
                    )
                    Text(
                        "Your encrypted recovery journal and backup were kept. Do not clear app data.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        enabled = !maintaining && !retrying,
                        onClick = {
                            retrying = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        RestoreJournal.consistent(context) {
                                            RestoreNotice.onStartup(context)
                                        }
                                    }
                                    failed = false
                                    onRecovered()
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    failed = true
                                } finally {
                                    retrying = false
                                }
                            }
                        },
                    ) {
                        Text("Retry recovery")
                    }
                    TextButton(enabled = !maintaining && !retrying, onClick = onClose) {
                        Text("Close app")
                    }
                }
            }
        }
    }
}
