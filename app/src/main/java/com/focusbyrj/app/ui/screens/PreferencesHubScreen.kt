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

package com.focusbyrj.app.ui.screens

import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.focusbyrj.app.BuildConfig
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.ui.components.SetupPermissionsDialog
import com.focusbyrj.app.ui.navigation.Screen
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.PermissionUtils
import com.focusbyrj.app.util.ProfileAvatarManager
import com.focusbyrj.app.util.sync.supabase.SupabaseKeyManager
import com.focusbyrj.app.util.sync.supabase.SupabaseSyncEngine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesHubScreen(
    navController: NavController,
    onOpenSetupGuide: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val economyProfile by FocusEconomyManager.profileFlow.collectAsStateWithLifecycle()

    var sessionState by remember { mutableStateOf(SupabaseKeyManager.getSessionState(context)) }
    var isSyncingNow by remember { mutableStateOf(false) }
    var syncErrorMessage by remember { mutableStateOf<String?>(null) }
    var syncSuccessMessage by remember { mutableStateOf<String?>(null) }

    val noteDao = remember { NoteDatabase.getInstance(context).noteDao() }
    val taskDao = remember { (context.applicationContext as FocusApplication).database.taskDao() }

    fun triggerManualSync() {
        if (isSyncingNow) return
        isSyncingNow = true
        syncErrorMessage = null
        syncSuccessMessage = null
        scope.launch {
            try {
                val result = SupabaseSyncEngine.performSync(context, noteDao, taskDao)
                result.onSuccess { res ->
                    syncSuccessMessage = res.message
                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    syncErrorMessage = err.message ?: "Sync failed"
                    Toast.makeText(context, "Sync failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                syncErrorMessage = e.message ?: "Sync error"
            } finally {
                isSyncingNow = false
                sessionState = SupabaseKeyManager.getSessionState(context)
            }
        }
    }

    var hasUsageStats by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    var isBatteryUnrestricted by remember { mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context)) }
    var hasNotifications by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME || event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                hasUsageStats = PermissionUtils.hasUsageStatsPermission(context)
                hasOverlay = PermissionUtils.hasOverlayPermission(context)
                isBatteryUnrestricted = PermissionUtils.isIgnoringBatteryOptimizations(context)
                hasNotifications = PermissionUtils.hasNotificationPermission(context)
                sessionState = SupabaseKeyManager.getSessionState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allConfigured = hasUsageStats && hasOverlay && isBatteryUnrestricted && hasNotifications
    var showSetupDialog by remember { mutableStateOf(false) }

    if (showSetupDialog) {
        SetupPermissionsDialog(
            hasUsageStats = hasUsageStats,
            hasOverlay = hasOverlay,
            isBatteryUnrestricted = isBatteryUnrestricted,
            hasNotifications = hasNotifications,
            onDismiss = { showSetupDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("preferences_hub_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Profile & Cloud Account Header
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            if (sessionState.isSignedIn) {
                                navController.navigate(Screen.DeviceSync.route) {
                                    launchSingleTop = true
                                }
                            } else {
                                navController.navigate(Screen.CloudAuth.route) {
                                    launchSingleTop = true
                                }
                            }
                        }
                        .testTag("preferences_profile_card"),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val avatarRes = ProfileAvatarManager.getAvatarImageRes(
                                economyProfile.selectedAvatar,
                                economyProfile.avatarTier
                            )
                            val avatarBorder = ProfileAvatarManager.getAvatarBorderColor(
                                economyProfile.selectedAvatar,
                                economyProfile.avatarTier
                            )
                            val rankTitle = ProfileAvatarManager.getAvatarTitle(
                                economyProfile.selectedAvatar,
                                economyProfile.avatarTier
                            )

                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = avatarRes),
                                    contentDescription = "User Avatar",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (sessionState.isSignedIn && !sessionState.userEmail.isNullOrBlank()) {
                                        sessionState.userEmail!!
                                    } else {
                                        economyProfile.name.ifBlank { "Focus Warrior" }
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 17.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (sessionState.isSignedIn) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                            border = BorderStroke(0.8.dp, Color(0xFF2E7D32).copy(alpha = 0.3f))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF2E7D32))
                                                )
                                                Text(
                                                    text = "E2EE ACTIVE",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.sp
                                                    ),
                                                    color = Color(0xFF2E7D32)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "• Synced",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    } else {
                                        Text(
                                            text = "Tap to Sign In / Sync",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "View Profile",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Account Profile Options: Actions conditioned on sign-in state
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (sessionState.isSignedIn) {
                                // Primary Sync Action Button (Only when signed in)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .clickable(enabled = !isSyncingNow) { triggerManualSync() },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
                                    ) {
                                        if (isSyncingNow) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(15.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Syncing", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        } else {
                                            Icon(Icons.Filled.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("Sync Now", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }

                                // Vault Action Button
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                        .clickable {
                                            navController.navigate(Screen.DeviceSync.route) {
                                                launchSingleTop = true
                                            }
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Vault", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            } else {
                                // Sign In Action Button (When not signed in)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .clickable {
                                            navController.navigate(Screen.CloudAuth.route) {
                                                launchSingleTop = true
                                            }
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Filled.Login, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sign In to Sync", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            // Stats Button
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    .clickable {
                                        navController.navigate(Screen.Account.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Transparent,
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Filled.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Stats", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        // Sync Error Inspector
                        syncErrorMessage?.let { errText ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.ErrorOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                "Sync Error Detected",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        IconButton(
                                            onClick = { syncErrorMessage = null },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Dismiss",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = errText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- PREFERENCES SECTION ---
                SettingsSectionHeader(title = "PREFERENCES")
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Security & Permissions
                    SettingsNavigationRow(
                        icon = Icons.Filled.Security,
                        title = "Security & Permissions",
                        subtitle = "Device shield & lock access",
                        testTag = "menu_security_permissions",
                        statusText = if (allConfigured) "Active" else "Setup",
                        isStatusError = !allConfigured,
                        onClick = {
                            navController.navigate(Screen.Security.route) {
                                launchSingleTop = true
                            }
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        modifier = Modifier.padding(start = 66.dp, end = 8.dp)
                    )

                    // App Settings
                    SettingsNavigationRow(
                        icon = Icons.Filled.Palette,
                        title = "App Settings",
                        subtitle = "Theme, sounds & preferences",
                        testTag = "menu_app_settings",
                        onClick = {
                            navController.navigate(Screen.Settings.route) {
                                launchSingleTop = true
                            }
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        modifier = Modifier.padding(start = 66.dp, end = 8.dp)
                    )

                    // Bubble Settings
                    SettingsNavigationRow(
                        icon = Icons.Filled.Chat,
                        title = "Bubble Settings",
                        subtitle = "Floating timer & quick dock",
                        testTag = "menu_bubble_settings",
                        onClick = {
                            navController.navigate(Screen.BubbleSettings.route) {
                                launchSingleTop = true
                            }
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        modifier = Modifier.padding(start = 66.dp, end = 8.dp)
                    )

                    // Subscription
                    SettingsNavigationRow(
                        icon = Icons.Filled.Star,
                        title = "Subscription",
                        subtitle = "Unlock Pro & cloud sync",
                        testTag = "menu_subscription",
                        badgeText = "PRO",
                        onClick = {
                            navController.navigate(Screen.Subscription.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- ASSISTANCE SECTION ---
                SettingsSectionHeader(title = "ASSISTANCE")
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SettingsNavigationRow(
                        icon = Icons.Filled.Info,
                        title = "Setup Guide",
                        subtitle = "Tour & permissions guide",
                        testTag = "menu_setup_guide",
                        onClick = {
                            if (onOpenSetupGuide != null) {
                                onOpenSetupGuide()
                            } else {
                                showSetupDialog = true
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Footer Branding
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "RuN • v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Stay present. Guard your mind.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            fontSize = 12.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.padding(start = 10.dp, top = 26.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    statusText: String? = null,
    isStatusError: Boolean = false,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (statusText != null) {
                val statusBg = if (isStatusError) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                val statusFg = if (isStatusError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Surface(
                    shape = CircleShape,
                    color = statusBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(statusFg)
                        )
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = statusFg
                        )
                    }
                }
            }

            if (badgeText != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
