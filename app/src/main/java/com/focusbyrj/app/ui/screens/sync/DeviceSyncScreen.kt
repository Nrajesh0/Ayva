/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.ui.screens.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.focusbyrj.app.data.TaskDao
import com.focusbyrj.app.data.note.NoteDao
import com.focusbyrj.app.util.sync.LocalWifiSyncEngine
import com.focusbyrj.app.util.sync.QrDecoder
import com.focusbyrj.app.util.sync.VaultCryptoEngine
import com.focusbyrj.app.util.sync.VaultSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class SyncTab {
    LINK_DEVICE,
    ENCRYPTED_BACKUP,
    RECOVERY_PHRASE,
    SECURITY_SPECS
}

enum class LinkMode {
    SCAN_PC_QR,
    SERVER_QR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSyncScreen(
    navController: NavController,
    noteDao: NoteDao,
    taskDao: TaskDao
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(SyncTab.LINK_DEVICE) }
    var linkMode by remember { mutableStateOf(LinkMode.SCAN_PC_QR) }

    // PC QR Scanner / Client Connection State
    var pcSyncUrl by remember { mutableStateOf("") }
    var pcSyncPassphrase by remember { mutableStateOf("FocusSecureSync2026") }
    var pcSessionToken by remember { mutableStateOf("") }
    var isConnectingToPc by remember { mutableStateOf(false) }
    var clientSyncSummary by remember { mutableStateOf<String?>(null) }

    // Image Picker for scanning QR from photo/screenshot
    val pickQrImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = ImageDecoder.createSource(context.contentResolver, uri)
                            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                decoder.isMutableRequired = true
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        }
                    }
                    val decodedText = QrDecoder.decodeFromBitmap(bitmap)
                    if (decodedText != null) {
                        val parsed = LocalWifiSyncEngine.parseQrPayload(decodedText)
                        if (parsed.url != null) pcSyncUrl = parsed.url
                        if (parsed.passphrase != null) pcSyncPassphrase = parsed.passphrase
                        if (parsed.key != null) pcSessionToken = parsed.key
                        Toast.makeText(context, "QR Code decoded successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Could not find a valid QR code in the selected image.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to read image: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Wi-Fi Local Sync & Server State
    var isServerActive by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf<LocalWifiSyncEngine.ServerStatus?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var serverPassphrase by remember { mutableStateOf("FocusSecureSync2026") }
    var serverSyncSuccessSummary by remember { mutableStateOf<String?>(null) }

    // Backup & Restore State
    var exportPassphrase by remember { mutableStateOf("") }
    var importPassphrase by remember { mutableStateOf("") }
    var importJsonPayload by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var exportResultJson by remember { mutableStateOf<String?>(null) }
    var isExportCopied by remember { mutableStateOf(false) }

    // Mnemonic State
    var generatedWords by remember { mutableStateOf<List<String>>(emptyList()) }

    // On dispose, clean up server
    DisposableEffect(Unit) {
        onDispose {
            LocalWifiSyncEngine.stopServer()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Laptop,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "PC Sync & Security",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Zero-Knowledge AES-256-GCM",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("device_sync_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Scrollable Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 0.dp,
                divider = {},
                containerColor = MaterialTheme.colorScheme.background,
                indicator = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                SyncTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { selectedTab = tab }
                    ) {
                        Text(
                            text = when (tab) {
                                SyncTab.LINK_DEVICE -> "Link to PC"
                                SyncTab.ENCRYPTED_BACKUP -> "Encrypted Backup"
                                SyncTab.RECOVERY_PHRASE -> "12-Word Phrase"
                                SyncTab.SECURITY_SPECS -> "Architecture"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (selectedTab) {
                SyncTab.LINK_DEVICE -> {
                    // Mode Selector (Scan PC QR vs Show Phone QR)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { linkMode = LinkMode.SCAN_PC_QR },
                            color = if (linkMode == LinkMode.SCAN_PC_QR) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QrCodeScanner,
                                    contentDescription = null,
                                    tint = if (linkMode == LinkMode.SCAN_PC_QR) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Scan PC QR",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (linkMode == LinkMode.SCAN_PC_QR) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { linkMode = LinkMode.SERVER_QR },
                            color = if (linkMode == LinkMode.SERVER_QR) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QrCode,
                                    contentDescription = null,
                                    tint = if (linkMode == LinkMode.SERVER_QR) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Host Phone QR",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (linkMode == LinkMode.SERVER_QR) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (linkMode == LinkMode.SCAN_PC_QR) {
                        // MODE 1: Scan PC QR / Connect directly to PC Web App
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.QrCodeScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Scan PC QR & Sync Directly",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Open the Web App on your PC, click 'Link Device' to display its QR code. Scan or paste the connection URL below to sync your notes & tasks over your local Wi-Fi.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Quick QR Image Scan Button
                                OutlinedButton(
                                    onClick = { pickQrImageLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Scan QR from Photo / Screenshot")
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = pcSyncUrl,
                                    onValueChange = { pcSyncUrl = it },
                                    label = { Text("PC Pairing URL / IP (e.g. http://192.168.1.5:8998)") },
                                    placeholder = { Text("http://192.168.x.x:8998") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = pcSyncPassphrase,
                                    onValueChange = { pcSyncPassphrase = it },
                                    label = { Text("E2EE Sync Passphrase") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Dual Action: Pull from PC vs Push to PC
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (pcSyncUrl.isBlank()) {
                                                Toast.makeText(context, "Please enter or scan PC URL", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            scope.launch {
                                                isConnectingToPc = true
                                                try {
                                                    val fetchResult = LocalWifiSyncEngine.fetchVaultFromPc(pcSyncUrl.trim(), pcSessionToken.ifBlank { null })
                                                    val encryptedPayload = fetchResult.getOrThrow()
                                                    val pkg = VaultCryptoEngine.EncryptedPackage.fromJsonString(encryptedPayload)
                                                        ?: throw Exception("Invalid vault format received from PC")
                                                    val decryptResult = VaultCryptoEngine.decrypt(pkg, pcSyncPassphrase.toCharArray())
                                                    val rawJson = decryptResult.getOrThrow()
                                                    val restoreResult = VaultSyncManager.restoreVaultFromJson(rawJson, noteDao, taskDao)
                                                    val counts = restoreResult.getOrThrow()
                                                    clientSyncSummary = "Successfully pulled & imported ${counts.first} notes and ${counts.second} tasks from PC!"
                                                    Toast.makeText(context, clientSyncSummary, Toast.LENGTH_LONG).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                } finally {
                                                    isConnectingToPc = false
                                                }
                                            }
                                        },
                                        enabled = !isConnectingToPc && pcSyncUrl.isNotBlank(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isConnectingToPc) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                        } else {
                                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text("Pull from PC", fontSize = 13.sp)
                                    }

                                    Button(
                                        onClick = {
                                            if (pcSyncUrl.isBlank()) {
                                                Toast.makeText(context, "Please enter or scan PC URL", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            scope.launch {
                                                isConnectingToPc = true
                                                try {
                                                    val rawJson = VaultSyncManager.createVaultJson(noteDao, taskDao)
                                                    val encPkg = VaultCryptoEngine.encrypt(rawJson, pcSyncPassphrase.toCharArray())
                                                    val pushResult = LocalWifiSyncEngine.pushVaultToPc(pcSyncUrl.trim(), encPkg.toJsonString(), pcSessionToken.ifBlank { null })
                                                    pushResult.getOrThrow()
                                                    clientSyncSummary = "Successfully pushed your Android notes & tasks to PC!"
                                                    Toast.makeText(context, clientSyncSummary, Toast.LENGTH_LONG).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Push failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                } finally {
                                                    isConnectingToPc = false
                                                }
                                            }
                                        },
                                        enabled = !isConnectingToPc && pcSyncUrl.isNotBlank(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Push to PC", fontSize = 13.sp)
                                    }
                                }

                                clientSyncSummary?.let { summary ->
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = summary,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(10.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // MODE 2: Host Server on Android & Show QR
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isServerActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isServerActive) Icons.Filled.WifiTethering else Icons.Filled.QrCode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Host Local Phone Server",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Starts an encrypted local server on this Android device and generates a pairing QR code for your PC browser to connect.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                if (isServerActive && qrBitmap != null && serverStatus != null) {
                                    // Live QR Code Display
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color.White,
                                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .size(240.dp)
                                            .padding(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                bitmap = qrBitmap!!.asImageBitmap(),
                                                contentDescription = "Sync QR Code",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(12.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Direct Browser URL:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        cm.setPrimaryClip(ClipData.newPlainText("URL", serverStatus?.pairingUrl ?: ""))
                                                        Toast.makeText(context, "URL copied!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.ContentCopy,
                                                        contentDescription = "Copy URL",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = serverStatus?.pairingUrl ?: "",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Passphrase: $serverPassphrase",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    serverSyncSuccessSummary?.let { summary ->
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = summary,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(10.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            LocalWifiSyncEngine.stopServer()
                                            isServerActive = false
                                            qrBitmap = null
                                            serverStatus = null
                                            Toast.makeText(context, "Sync Server stopped", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Stop Wi-Fi Server")
                                    }
                                } else {
                                    // Start Wi-Fi Server Controls
                                    OutlinedTextField(
                                        value = serverPassphrase,
                                        onValueChange = { serverPassphrase = it },
                                        label = { Text("E2EE Sync Passphrase") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val status = LocalWifiSyncEngine.startServer(
                                                    passphrase = serverPassphrase,
                                                    onVaultReceived = { encryptedPayload ->
                                                        withContext(Dispatchers.IO) {
                                                            try {
                                                                val pkg = VaultCryptoEngine.EncryptedPackage.fromJsonString(encryptedPayload)
                                                                    ?: return@withContext Pair(0, 0)
                                                                val decryptResult = VaultCryptoEngine.decrypt(
                                                                    pkg,
                                                                    serverPassphrase.toCharArray()
                                                                )
                                                                val rawJson = decryptResult.getOrNull() ?: return@withContext Pair(0, 0)
                                                                val restoreResult = VaultSyncManager.restoreVaultFromJson(rawJson, noteDao, taskDao)
                                                                val counts = restoreResult.getOrElse { Pair(0, 0) }
                                                                withContext(Dispatchers.Main) {
                                                                    serverSyncSuccessSummary = "Successfully merged ${counts.first} notes & ${counts.second} tasks from PC!"
                                                                }
                                                                counts
                                                            } catch (e: Exception) {
                                                                Pair(0, 0)
                                                            }
                                                        }
                                                    },
                                                    provideCurrentVault = {
                                                        withContext(Dispatchers.IO) {
                                                            val raw = VaultSyncManager.createVaultJson(noteDao, taskDao)
                                                            val enc = VaultCryptoEngine.encrypt(raw, serverPassphrase.toCharArray())
                                                            enc.toJsonString()
                                                        }
                                                    }
                                                )

                                                if (status.isRunning && status.pairingUrl != null) {
                                                    serverStatus = status
                                                    val payloadJson = JSONObject().apply {
                                                        put("type", "focus_sync_pair")
                                                        put("url", status.pairingUrl)
                                                        put("ip", status.localIp)
                                                        put("port", status.port)
                                                        put("key", status.sessionKey)
                                                        put("passphrase", serverPassphrase)
                                                    }.toString()

                                                    val bmp = LocalWifiSyncEngine.generateQrBitmap(payloadJson, 512, 512)
                                                    qrBitmap = bmp
                                                    isServerActive = true
                                                    Toast.makeText(context, "Wi-Fi Sync Server started on port ${status.port}!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Could not start server. Please ensure Wi-Fi is connected.", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Generate Sync QR & Start Server", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                SyncTab.ENCRYPTED_BACKUP -> {
                    // Export Section
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Export Encrypted Vault (.vault.json)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Bundles all notes & tasks into an AES-256-GCM encrypted package matching the Web app format.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = exportPassphrase,
                                onValueChange = { exportPassphrase = it },
                                label = { Text("Set backup passphrase...") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (exportPassphrase.length < 4) {
                                        Toast.makeText(context, "Passphrase must be at least 4 characters", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    scope.launch {
                                        isExporting = true
                                        try {
                                            val rawJson = VaultSyncManager.createVaultJson(noteDao, taskDao)
                                            val encPkg = VaultCryptoEngine.encrypt(rawJson, exportPassphrase.toCharArray())
                                            exportResultJson = encPkg.toJsonString()
                                            Toast.makeText(context, "Vault encrypted successfully!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isExporting = false
                                        }
                                    }
                                },
                                enabled = !isExporting && exportPassphrase.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Generate Encrypted Vault")
                            }

                            exportResultJson?.let { jsonStr ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Encrypted Package Ready",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Button(
                                                onClick = {
                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cm.setPrimaryClip(ClipData.newPlainText("EncryptedVault", jsonStr))
                                                    isExportCopied = true
                                                    Toast.makeText(context, "Copied encrypted vault JSON to clipboard!", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(if (isExportCopied) Icons.Filled.Check else Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (isExportCopied) "Copied!" else "Copy JSON", fontSize = 12.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = jsonStr.take(160) + "...",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Import Section
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Import & Restore Vault",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Paste encrypted vault JSON from web app and enter passphrase to restore.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = importJsonPayload,
                                onValueChange = { importJsonPayload = it },
                                label = { Text("Paste Encrypted Vault JSON...") },
                                maxLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = importPassphrase,
                                onValueChange = { importPassphrase = it },
                                label = { Text("Enter Passphrase...") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (importJsonPayload.isBlank() || importPassphrase.isBlank()) {
                                        Toast.makeText(context, "Please paste JSON and enter passphrase", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    scope.launch {
                                        isImporting = true
                                        try {
                                            val pkg = VaultCryptoEngine.EncryptedPackage.fromJsonString(importJsonPayload.trim())
                                            if (pkg == null) {
                                                Toast.makeText(context, "Invalid vault JSON format", Toast.LENGTH_SHORT).show()
                                                return@launch
                                            }
                                            val decryptResult = VaultCryptoEngine.decrypt(pkg, importPassphrase.toCharArray())
                                            val rawJson = decryptResult.getOrNull()
                                            if (rawJson == null) {
                                                Toast.makeText(context, "Decryption failed: Incorrect passphrase", Toast.LENGTH_LONG).show()
                                                return@launch
                                            }
                                            val result = VaultSyncManager.restoreVaultFromJson(rawJson, noteDao, taskDao)
                                            val counts = result.getOrElse { Pair(0, 0) }
                                            Toast.makeText(
                                                context,
                                                "Restored ${counts.first} notes & ${counts.second} tasks successfully!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            importJsonPayload = ""
                                            importPassphrase = ""
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Decryption failed: Incorrect password or invalid package", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isImporting = false
                                        }
                                    }
                                },
                                enabled = !isImporting && importJsonPayload.isNotBlank() && importPassphrase.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Decrypt & Restore Database")
                            }
                        }
                    }
                }

                SyncTab.RECOVERY_PHRASE -> {
                    // BIP-39 Mnemonic Phrase
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "12-Word Emergency Recovery Phrase",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Write down these 12 words in order. Never share them with anyone. They allow zero-knowledge disaster recovery across both Web and Android apps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            if (generatedWords.isEmpty()) {
                                Button(
                                    onClick = {
                                        generatedWords = VaultCryptoEngine.generate12WordMnemonic()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generate 12-Word Phrase")
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (i in 0 until 4) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            for (j in 0 until 3) {
                                                val idx = i * 3 + j
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "${idx + 1}.",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Text(
                                                            text = generatedWords[idx],
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("RecoveryPhrase", generatedWords.joinToString(" ")))
                                            Toast.makeText(context, "Recovery phrase copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Copy Recovery Phrase")
                                    }
                                }
                            }
                        }
                    }
                }

                SyncTab.SECURITY_SPECS -> {
                    // Security architecture description
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Zero-Knowledge Security Architecture",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            SpecItem(title = "Local Wi-Fi Direct Peer-to-Peer", detail = "Direct device-to-device communication on your local network. No external servers or telemetry.")
                            SpecItem(title = "Cipher", detail = "AES-256-GCM authenticated encryption with 128-bit integrity tag.")
                            SpecItem(title = "Initialization Vector (IV)", detail = "Unique 96-bit cryptographically secure IV generated per packet.")
                            SpecItem(title = "Key Derivation", detail = "PBKDF2 with HMAC-SHA-256, 100,000 iterations, 128-bit cryptographic salt.")
                            SpecItem(title = "Web / Android Interoperability", detail = "Direct parity with WebCrypto (window.crypto.subtle) standard.")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SpecItem(title: String, detail: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
