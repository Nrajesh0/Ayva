/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.focusbyrj.app.ui.screens.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.focusbyrj.app.data.TaskDao
import com.focusbyrj.app.data.note.NoteDao
import com.focusbyrj.app.ui.navigation.Screen
import com.focusbyrj.app.util.sync.supabase.AutoSyncManager
import com.focusbyrj.app.util.sync.supabase.SupabaseAuthManager
import com.focusbyrj.app.util.sync.supabase.SupabaseConfig
import com.focusbyrj.app.util.sync.supabase.SupabaseKeyManager
import com.focusbyrj.app.util.sync.supabase.SupabaseSyncEngine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class SyncDiagnosticLog(
    val timestamp: Long,
    val isSuccess: Boolean,
    val summary: String,
    val detailedError: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSyncScreen(
    navController: NavController,
    noteDao: NoteDao,
    taskDao: TaskDao
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sessionState by remember { mutableStateOf(SupabaseKeyManager.getSessionState(context)) }
    var isAutoSyncEnabled by remember { mutableStateOf(AutoSyncManager.isAutoSyncEnabled(context)) }
    var isSyncingNow by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var latestDiagnostic by remember { mutableStateOf<SyncDiagnosticLog?>(null) }
    var showSqlSetupModal by remember { mutableStateOf(false) }

    fun refreshState() {
        sessionState = SupabaseKeyManager.getSessionState(context)
        isAutoSyncEnabled = AutoSyncManager.isAutoSyncEnabled(context)
    }

    LaunchedEffect(Unit) {
        refreshState()
    }

    fun executeManualSync() {
        if (isSyncingNow) return
        isSyncingNow = true
        scope.launch {
            try {
                val result = SupabaseSyncEngine.performSync(context, noteDao, taskDao)
                val now = System.currentTimeMillis()
                result.onSuccess { res ->
                    latestDiagnostic = SyncDiagnosticLog(
                        timestamp = now,
                        isSuccess = true,
                        summary = res.message
                    )
                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    val errMsg = err.message ?: "Sync error"
                    latestDiagnostic = SyncDiagnosticLog(
                        timestamp = now,
                        isSuccess = false,
                        summary = errMsg,
                        detailedError = err.stackTraceToString().take(500)
                    )
                    Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                latestDiagnostic = SyncDiagnosticLog(
                    timestamp = System.currentTimeMillis(),
                    isSuccess = false,
                    summary = e.message ?: "Sync exception",
                    detailedError = e.stackTraceToString().take(500)
                )
            } finally {
                isSyncingNow = false
                refreshState()
            }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign Out", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Text(
                    "Your local data remains safely on this device. Cloud synchronization will pause until you sign back in.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            SupabaseAuthManager.signOut(context)
                            showSignOutConfirm = false
                            latestDiagnostic = null
                            refreshState()
                            Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .testTag("btn_sync_back")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Cloud Sync",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                IconButton(
                    onClick = { refreshState() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (sessionState.isSignedIn) {
                    // ==================== SIGNED IN STATE ====================
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sessionState.userEmail ?: "Connected",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val lastSyncedStr = if (sessionState.lastSyncedTime > 0) {
                                        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(sessionState.lastSyncedTime))
                                    } else {
                                        "Never synced"
                                    }
                                    Text(
                                        text = "Last synced: $lastSyncedStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF2E7D32).copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF2E7D32),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = { executeManualSync() },
                                enabled = !isSyncingNow,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("btn_manual_sync_now")
                            ) {
                                if (isSyncingNow) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync Now", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Preferences
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto-sync",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Sync immediately when changes occur",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                Switch(
                                    checked = isAutoSyncEnabled,
                                    onCheckedChange = { enabled ->
                                        isAutoSyncEnabled = enabled
                                        AutoSyncManager.setAutoSyncEnabled(context, enabled)
                                        if (enabled) {
                                            AutoSyncManager.triggerDebouncedSync(context, delayMs = 500L)
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        navController.navigate(Screen.CloudAuth.route) { launchSingleTop = true }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Switch Account", fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = { showSignOutConfirm = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Sign Out", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                } else {
                    // ==================== NOT SIGNED IN STATE ====================
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Encrypted Cloud Vault",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Sign in to securely sync your notes and tasks across all your devices.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    navController.navigate(Screen.CloudAuth.route) {
                                        launchSingleTop = true
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_open_cloud_auth")
                            ) {
                                Text("Sign In or Create Account", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }

                // Diagnostics Banner (If any)
                latestDiagnostic?.let { diag ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (diag.isSuccess) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        border = BorderStroke(
                            1.dp,
                            if (diag.isSuccess) MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (diag.isSuccess) "Sync Status" else "Sync Issue",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (diag.isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(diag.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = diag.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (diag.isSuccess) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Minimalist Setup SQL expander
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSqlSetupModal = !showSqlSetupModal },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Supabase Database Setup",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                if (showSqlSetupModal) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(visible = showSqlSetupModal) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Text(
                                    text = "Run the setup script in your Supabase SQL Editor to configure tables, indexes, and Row Level Security.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Supabase SQL", SupabaseConfig.RECOMMENDED_SQL.trim())
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "SQL copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy SQL Script", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Ultra Minimal Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "End-to-end encrypted with zero-knowledge keys",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}
