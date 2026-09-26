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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.R
import com.focusbyrj.app.data.AppRestriction
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.navigation.Screen
import com.focusbyrj.app.ui.screens.security.ExportBackupPasswordDialog
import com.focusbyrj.app.util.*
import com.focusbyrj.app.util.backup.BackupRestoreManager
import com.focusbyrj.app.util.sync.supabase.SupabaseKeyManager
import com.focusbyrj.app.util.sync.supabase.SupabaseSyncEngine
import com.focusbyrj.app.ui.screens.notes.RichTextEngine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// SEMANTIC COLOR TOKENS DERIVED ADAPTIVELY FROM MaterialTheme.colorScheme
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun rememberEnclaveColors(): EnclaveColors {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f

    return remember(cs, isDark) {
        EnclaveColors(
            bg = cs.background,
            cardBg = if (isDark) cs.surface else cs.surface,
            cardBorder = if (isDark) cs.outlineVariant.copy(alpha = 0.22f) else cs.outline.copy(alpha = 0.22f),
            subCardBg = if (isDark) cs.surfaceVariant.copy(alpha = 0.5f) else cs.surfaceVariant.copy(alpha = 0.55f),
            subCardBorder = if (isDark) cs.outlineVariant.copy(alpha = 0.16f) else cs.outline.copy(alpha = 0.16f),
            textPrimary = cs.onSurface,
            textSecondary = cs.onSurfaceVariant,
            textMuted = cs.onSurfaceVariant.copy(alpha = 0.75f),
            accentPrimary = cs.primary,
            accentCyan = if (isDark) Color(0xFF00E5FF) else cs.primary,
            accentGold = Color(0xFFFFD700),
            accentGreen = Color(0xFF10B981),
            accentRed = Color(0xFFEF4444),
            isDark = isDark
        )
    }
}

private data class EnclaveColors(
    val bg: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val subCardBg: Color,
    val subCardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentPrimary: Color,
    val accentCyan: Color,
    val accentGold: Color,
    val accentGreen: Color,
    val accentRed: Color,
    val isDark: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    navController: NavController? = null,
    onOpenNote: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val enclaveColors = rememberEnclaveColors()

    val profile by FocusEconomyManager.profileFlow.collectAsStateWithLifecycle()
    val stats by FocusStatsManager.statsFlow.collectAsStateWithLifecycle()
    val streakSource by StreakManager.streakSourceFlow.collectAsStateWithLifecycle()
    val drillProfile by AptitudeManager.profileFlow.collectAsStateWithLifecycle()
    val themeMode by AppThemeManager.themeModeFlow.collectAsStateWithLifecycle()
    val dailyResisted by FocusStatsManager.interceptionsFlow.collectAsStateWithLifecycle()

    // Query active restrictions from Room DB
    val app = context.applicationContext as FocusApplication
    val rawRestrictions by app.database.appRestrictionDao().getAllRestrictions().collectAsState(initial = emptyList())

    // Query notes from Room DB
    val noteDao = remember { NoteDatabase.getInstance(context).noteDao() }
    val allActiveNotes by noteDao.getAllActiveNotes().collectAsState(initial = emptyList())

    // Profile-specific pinned notes (independent from Room DB isPinned!)
    val pinnedPrefs = remember { context.getSharedPreferences("profile_pinned_notes_prefs", Context.MODE_PRIVATE) }
    var profilePinnedIds by remember {
        val initial = pinnedPrefs.getStringSet("profile_pinned_ids", emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        mutableStateOf(initial)
    }

    // Cloud session state
    var sessionState by remember { mutableStateOf(SupabaseKeyManager.getSessionState(context)) }

    // Navigation & Tab state
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: Achievements, 2: Logs

    // Dialog & sheet states
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAvatarStoreSheet by remember { mutableStateOf(false) }
    var showShareProfileDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showEnclaveSystemDialog by remember { mutableStateOf(false) }
    var showStreakDialog by remember { mutableStateOf(false) }
    var showChoosePinnedNotesDialog by remember { mutableStateOf(false) }

    // Backup & Export states
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val password = pendingExportPassword
        if (uri != null && password != null) {
            isExporting = true
            coroutineScope.launch {
                val result = BackupRestoreManager.createEncryptedBackup(context, uri, password)
                isExporting = false
                pendingExportPassword = null
                if (result.isSuccess) {
                    val meta = result.getOrNull()
                    Toast.makeText(
                        context,
                        "Encrypted backup saved! (${meta?.noteCount ?: 0} notes, ${meta?.taskCount ?: 0} tasks)",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Backup failed: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            pendingExportPassword = null
        }
    }

    // Dynamic Year and Month badge
    val cal = remember { Calendar.getInstance() }
    val yearMonthBadge = remember(cal) {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        "$y.$m"
    }

    // Persistent node ID
    val sharedPrefs = remember { context.getSharedPreferences("enclave_identity_prefs", Context.MODE_PRIVATE) }
    val nodeId = remember {
        var existing = sharedPrefs.getString("enclave_node_id", null)
        if (existing == null) {
            existing = "0X71C8A942F4A9"
            sharedPrefs.edit().putString("enclave_node_id", existing).apply()
        }
        existing
    }
    val shortNodeId = remember(nodeId) {
        if (nodeId.length > 8) "${nodeId.take(6)}...${nodeId.takeLast(4)}" else nodeId
    }

    // App blocking enforcement master toggle
    var isAppBlockingEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("app_blocking_enforced", true))
    }

    // Effective streak calculations
    val (activeStreak, longestStreak) = when (streakSource) {
        StreakSource.DRILL -> drillProfile.currentStreak to drillProfile.longestStreak
        StreakSource.FOCUS -> stats.currentStreak to maxOf(stats.longestStreak, profile.longestStreak)
    }

    // Real level & gold
    val effectiveLevel = maxOf(profile.level, drillProfile.level)
    val effectiveGold = profile.gold

    // Refresh profile and stats on screen launch
    LaunchedEffect(Unit) {
        FocusEconomyManager.init(context)
        FocusStatsManager.refreshStats(context)
        sessionState = SupabaseKeyManager.getSessionState(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(enclaveColors.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 90.dp)
        ) {
            // TOP HEADER BAR: App Icon, "Profile" ... [🔥 Streak Pill] [Avatar -> Settings]
            EnclaveTopBar(
                colors = enclaveColors,
                activeStreak = activeStreak,
                avatarRes = ProfileAvatarManager.getAvatarImageRes(profile.selectedAvatar, profile.avatarTier),
                avatarBorderColor = ProfileAvatarManager.getAvatarBorderColor(profile.selectedAvatar, profile.avatarTier),
                onIdentityClick = { showEnclaveSystemDialog = true },
                onStreakClick = { showStreakDialog = true },
                onAvatarClick = {
                    navController?.navigate(Screen.PreferencesHub.route) { launchSingleTop = true }
                        ?: Toast.makeText(context, "Navigating to Settings Hub", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // IDENTITY HERO CARD: Avatar, Name, Role, Bio, Action Buttons
            EnclaveIdentityHeroCard(
                colors = enclaveColors,
                profile = profile,
                onEditProfileClick = { showEditProfileDialog = true },
                onShareCardClick = { showShareProfileDialog = true },
                onMoreOptionsClick = { showOptionsMenu = true },
                onAvatarClick = { showAvatarStoreSheet = true }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // QUICK STATS ROW: [GOLD] [LEVEL] [SYNC]
            EnclaveQuickStatsRow(
                colors = enclaveColors,
                gold = effectiveGold,
                level = effectiveLevel,
                sessionState = sessionState,
                onSyncClick = {
                    navController?.navigate(Screen.PreferencesHub.route) { launchSingleTop = true }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // NAVIGATION TABS: Overview | Achievements | Logs
            EnclaveTabsRow(
                colors = enclaveColors,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // TAB CONTENT
            when (selectedTab) {
                0 -> {
                    // TAB 0: OVERVIEW
                    OverviewTabContent(
                        colors = enclaveColors,
                        profile = profile,
                        activeStreak = activeStreak,
                        longestStreak = longestStreak,
                        effectiveLevel = effectiveLevel,
                        effectiveGold = effectiveGold,
                        dailyUsage = stats.dailyFocusMinutes,
                        restrictions = rawRestrictions,
                        allActiveNotes = allActiveNotes,
                        profilePinnedIds = profilePinnedIds,
                        dailyResisted = dailyResisted,
                        themeMode = themeMode,
                        isAppBlockingEnabled = isAppBlockingEnabled,
                        onClaimRewards = {
                            FocusEconomyManager.claimPendingRewards()
                            Toast.makeText(context, "Rewards claimed into your balance!", Toast.LENGTH_SHORT).show()
                        },
                        onToggleAppBlocking = { enabled ->
                            isAppBlockingEnabled = enabled
                            sharedPrefs.edit().putBoolean("app_blocking_enforced", enabled).apply()
                            Toast.makeText(
                                context,
                                if (enabled) "App Blocking Shields: ACTIVE" else "App Blocking Shields: PAUSED",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onThemeModeSelected = { mode ->
                            AppThemeManager.setThemeMode(context, mode)
                        },
                        onBiometricClick = {
                            navController?.navigate(Screen.Security.route) { launchSingleTop = true }
                                ?: Toast.makeText(context, "Vault & Security is active", Toast.LENGTH_SHORT).show()
                        },
                        onManualExport = {
                            showExportPasswordDialog = true
                        },
                        onLockAppImmediately = {
                            ArchiveVaultSecurity.lockVault()
                            Toast.makeText(context, "Identity Protocol Locked. All vaults secured.", Toast.LENGTH_SHORT).show()
                        },
                        onOpenNote = { noteId ->
                            if (onOpenNote != null) {
                                onOpenNote(noteId)
                            } else {
                                navController?.navigate(Screen.Empty.route) { launchSingleTop = true }
                            }
                        },
                        onChoosePinnedNotesClick = {
                            showChoosePinnedNotesDialog = true
                        },
                        onManageAppBlockingClick = {
                            navController?.navigate(Screen.AddRestriction.route) { launchSingleTop = true }
                        }
                    )
                }
                1 -> {
                    // TAB 1: ACHIEVEMENTS
                    AchievementsEnclaveTab(colors = enclaveColors, profile = profile, stats = stats)
                }
                2 -> {
                    // TAB 2: SYSTEM ENCLAVE LOGS
                    EnclaveLogsTab(
                        colors = enclaveColors,
                        profile = profile,
                        nodeId = nodeId,
                        yearMonth = yearMonthBadge,
                        restrictedCount = rawRestrictions.count { it.isRestricted }
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DIALOGS & MODALS
    // ─────────────────────────────────────────────────────────────────────────

    // 1. Edit Profile Modal
    if (showEditProfileDialog) {
        EditProfileModal(
            colors = enclaveColors,
            profile = profile,
            nodeId = nodeId,
            shortNodeId = shortNodeId,
            onDismiss = { showEditProfileDialog = false },
            onOpenAvatarStore = {
                showEditProfileDialog = false
                showAvatarStoreSheet = true
            },
            onSave = { newName, newRole, newBio ->
                FocusEconomyManager.updateProfileDetails(newName, newRole, newBio)
                showEditProfileDialog = false
                Toast.makeText(context, "Profile identity updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 2. Avatar Selection & Store Sheet
    if (showAvatarStoreSheet) {
        AvatarStoreModal(
            colors = enclaveColors,
            profile = profile,
            onDismiss = { showAvatarStoreSheet = false }
        )
    }

    // 3. Share Profile Card Modal
    if (showShareProfileDialog) {
        ShareProfileCardModal(
            colors = enclaveColors,
            profile = profile,
            nodeId = nodeId,
            yearMonth = yearMonthBadge,
            activeStreak = activeStreak,
            restrictedCount = rawRestrictions.count { it.isRestricted },
            onDismiss = { showShareProfileDialog = false },
            onShare = {
                val shareText = "✦ RUN // IDENTITY PROTOCOL ✦\n" +
                        "Node: $nodeId\n" +
                        "Operator: ${profile.name}\n" +
                        "Protocol: $yearMonthBadge Enclave\n" +
                        "Rank: ${ProfileAvatarManager.getAvatarTitle(profile.selectedAvatar, profile.avatarTier)}\n" +
                        "Level: $effectiveLevel • Gold: $effectiveGold\n" +
                        "Active Streak: ${activeStreak}d\n" +
                        "Shields: ${rawRestrictions.count { it.isRestricted }} Apps Restricted\n" +
                        "Focus by Rj Enclave Systems."
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Identity Card"))
                showShareProfileDialog = false
            }
        )
    }

    // 4. Overflow Menu
    if (showOptionsMenu) {
        EnclaveOverflowMenu(
            colors = enclaveColors,
            onDismiss = { showOptionsMenu = false },
            onAvatarStore = {
                showOptionsMenu = false
                showAvatarStoreSheet = true
            },
            onEconomyRules = {
                showOptionsMenu = false
                showRulesDialog = true
            },
            onPreferencesHub = {
                showOptionsMenu = false
                navController?.navigate(Screen.PreferencesHub.route) { launchSingleTop = true }
            },
            onSecurityVaults = {
                showOptionsMenu = false
                navController?.navigate(Screen.Security.route) { launchSingleTop = true }
            },
            onCopyNodeId = {
                showOptionsMenu = false
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Node ID", nodeId))
                Toast.makeText(context, "Node ID copied: $nodeId", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 5. Economy & Discipline Rules Dialog
    if (showRulesDialog) {
        EnclaveRulesDialog(colors = enclaveColors, onDismiss = { showRulesDialog = false })
    }

    // 6. System Enclave Hardware Identity Dialog
    if (showEnclaveSystemDialog) {
        EnclaveSystemIdentityDialog(
            colors = enclaveColors,
            nodeId = nodeId,
            profile = profile,
            yearMonth = yearMonthBadge,
            onDismiss = { showEnclaveSystemDialog = false }
        )
    }

    // 7. Streak Info Dialog
    if (showStreakDialog) {
        EnclaveStreakDialog(
            colors = enclaveColors,
            activeStreak = activeStreak,
            longestStreak = longestStreak,
            streakFreezes = profile.streakFreezes,
            onDismiss = { showStreakDialog = false }
        )
    }

    // 8. Choose Pinned Notes Dialog
    if (showChoosePinnedNotesDialog) {
        ChoosePinnedNotesDialog(
            colors = enclaveColors,
            activeNotes = allActiveNotes,
            pinnedNoteIds = profilePinnedIds,
            onTogglePin = { noteId, shouldPin ->
                val newSet = if (shouldPin) profilePinnedIds + noteId else profilePinnedIds - noteId
                profilePinnedIds = newSet
                pinnedPrefs.edit().putStringSet("profile_pinned_ids", newSet.map { it.toString() }.toSet()).apply()
            },
            onDismiss = { showChoosePinnedNotesDialog = false }
        )
    }

    // 9. Manual Export Password Dialog
    if (showExportPasswordDialog) {
        ExportBackupPasswordDialog(
            isExporting = isExporting,
            onDismiss = {
                showExportPasswordDialog = false
                pendingExportPassword = null
            },
            onConfirm = { password ->
                showExportPasswordDialog = false
                pendingExportPassword = password
                val suggestedName = BackupRestoreManager.generateBackupFileName()
                exportLauncher.launch(suggestedName)
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. TOP BAR COMPONENT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EnclaveTopBar(
    colors: EnclaveColors,
    activeStreak: Int,
    avatarRes: Int,
    avatarBorderColor: Color,
    onIdentityClick: () -> Unit,
    onStreakClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Icon
        Image(
            painter = painterResource(id = R.drawable.app_icon),
            contentDescription = "RuN Logo",
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(1.dp, colors.subCardBorder, RoundedCornerShape(9.dp))
                .clickable { onIdentityClick() }
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title: Profile
        Text(
            text = "Profile",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 0.3.sp
            ),
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.weight(1f))

        // Streak Pill Button (Replaces static notification bell)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.subCardBg,
            border = BorderStroke(1.dp, colors.subCardBorder),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onStreakClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔥", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "${activeStreak}d",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    color = colors.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Avatar Thumbnail -> Navigates to Settings / Preferences Hub (Perfect 1:1 round circle)
        Box(
            modifier = Modifier
                .size(36.dp)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(colors.subCardBg)
                .border(1.8.dp, avatarBorderColor, CircleShape)
                .clickable { onAvatarClick() },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = avatarRes),
                contentDescription = "Settings Hub",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(CircleShape)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. IDENTITY HERO CARD COMPONENT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EnclaveIdentityHeroCard(
    colors: EnclaveColors,
    profile: UserProfile,
    onEditProfileClick: () -> Unit,
    onShareCardClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val avatarRes = ProfileAvatarManager.getAvatarImageRes(profile.selectedAvatar, profile.avatarTier)
    val avatarBorder = ProfileAvatarManager.getAvatarBorderColor(profile.selectedAvatar, profile.avatarTier)
    var isBioExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(18.dp),
        color = colors.cardBg,
        border = BorderStroke(1.dp, colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Profile Info: Avatar on left, Details on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Picture (Larger size)
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(colors.subCardBg)
                        .border(2.5.dp, avatarBorder, CircleShape)
                        .clickable { onAvatarClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = avatarRes),
                        contentDescription = "Identity Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Name & Details side-by-side with avatar
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Display Name
                    Text(
                        text = profile.name.ifBlank { "Focus Warrior" },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Role / Headline
                    Text(
                        text = profile.role.ifBlank { "Student" },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.5.sp
                        ),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Bio (Expandable multi-line)
                    Text(
                        text = profile.bio.ifBlank { "Be productive" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.5.sp
                        ),
                        color = colors.textMuted,
                        maxLines = if (isBioExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { isBioExpanded = !isBioExpanded }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Actions: [Edit Profile] [Share] [...]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit Profile Button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.subCardBg,
                    border = BorderStroke(1.dp, colors.subCardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onEditProfileClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit Profile",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Edit Profile",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = colors.textPrimary
                        )
                    }
                }

                // Share Button (shortened from "Share Profile card")
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.subCardBg,
                    border = BorderStroke(1.dp, colors.subCardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onShareCardClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = colors.textPrimary
                        )
                    }
                }

                // More Options (...) Button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.subCardBg,
                    border = BorderStroke(1.dp, colors.subCardBorder),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onMoreOptionsClick() }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreHoriz,
                            contentDescription = "More Options",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. QUICK STATS ROW: [GOLD] [LEVEL] [SYNC]
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EnclaveQuickStatsRow(
    colors: EnclaveColors,
    gold: Int,
    level: Int,
    sessionState: SupabaseKeyManager.SessionState,
    onSyncClick: () -> Unit
) {
    val (syncValue, syncUnit, syncLabel) = when {
        !sessionState.isSignedIn -> Triple("LOCAL", null, "SYNC")
        sessionState.isOfflineMode -> Triple("PAUSED", null, "SYNC")
        else -> Triple("100", "%", "SYNC")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EnclaveStatCard(
            colors = colors,
            modifier = Modifier.weight(1f),
            value = "$gold",
            label = "GOLD",
            icon = Icons.Filled.MonetizationOn,
            iconTint = colors.accentGold
        )

        EnclaveStatCard(
            colors = colors,
            modifier = Modifier.weight(1f),
            value = "$level",
            label = "LEVEL",
            icon = Icons.Filled.Star,
            iconTint = colors.accentCyan
        )

        EnclaveStatCard(
            colors = colors,
            modifier = Modifier
                .weight(1f)
                .clickable { onSyncClick() },
            value = syncValue,
            unit = syncUnit,
            label = syncLabel,
            icon = Icons.Filled.Sync,
            iconTint = if (sessionState.isSignedIn && !sessionState.isOfflineMode) colors.accentGreen else colors.textMuted
        )
    }
}

@Composable
private fun EnclaveStatCard(
    colors: EnclaveColors,
    modifier: Modifier = Modifier,
    value: String,
    unit: String? = null,
    label: String,
    icon: ImageVector? = null,
    iconTint: Color = colors.accentCyan
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = colors.cardBg,
        border = BorderStroke(1.dp, colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = colors.textPrimary
                    )
                    if (unit != null) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = colors.textMuted,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                ),
                color = colors.textMuted
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3.5. ACHIEVEMENTS PREVIEW ROW COMPONENT BELOW PROFILE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EnclaveAchievementsPreviewRow(
    colors: EnclaveColors,
    profile: UserProfile,
    stats: FocusStats,
    onViewAllClick: () -> Unit
) {
    val context = LocalContext.current
    val achievements = remember(profile, stats) { getAchievements(profile, stats) }
    val unlockedCount = remember(achievements) { achievements.count { it.isUnlocked } }
    val totalCount = achievements.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = "Achievements",
                    tint = colors.accentGold,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "ACHIEVEMENTS",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = colors.textPrimary
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.subCardBg,
                    border = BorderStroke(1.dp, colors.subCardBorder)
                ) {
                    Text(
                        text = "$unlockedCount/$totalCount",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.accentCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "VIEW ALL >",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = colors.accentCyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onViewAllClick() }
                    .padding(vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal scrolling row of milestone cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            achievements.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.cardBg,
                    border = BorderStroke(
                        1.dp,
                        if (item.isUnlocked) item.color.copy(alpha = 0.5f) else colors.cardBorder
                    ),
                    modifier = Modifier
                        .width(118.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            val st = if (item.isUnlocked) "UNLOCKED" else "LOCKED"
                            Toast.makeText(context, "${item.title} ($st): ${item.description}", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MedievalMedal(
                            iconRes = item.iconRes,
                            color = item.color,
                            isUnlocked = item.isUnlocked,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            ),
                            color = if (item.isUnlocked) colors.textPrimary else colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (item.isUnlocked) item.color.copy(alpha = 0.15f) else colors.subCardBg,
                            border = BorderStroke(1.dp, if (item.isUnlocked) item.color.copy(alpha = 0.35f) else colors.subCardBorder)
                        ) {
                            Text(
                                text = if (item.isUnlocked) "UNLOCKED" else "LOCKED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = if (item.isUnlocked) item.color else colors.textMuted,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. NAVIGATION TABS ROW: Overview | Achievements | Logs
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EnclaveTabsRow(
    colors: EnclaveColors,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("Overview", "Achievements", "Logs")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tabTitle ->
            val isSelected = selectedTabIndex == index
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tabTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 16.sp
                    ),
                    color = if (isSelected) colors.textPrimary else colors.textMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Active solid underline indicator
                Box(
                    modifier = Modifier
                        .width(if (isSelected) 48.dp else 0.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) colors.textPrimary else Color.Transparent)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. OVERVIEW TAB COMPLETE SCROLLING LAYOUT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OverviewTabContent(
    colors: EnclaveColors,
    profile: UserProfile,
    activeStreak: Int,
    longestStreak: Int,
    effectiveLevel: Int,
    effectiveGold: Int,
    dailyUsage: Map<Int, Long>,
    restrictions: List<AppRestriction>,
    allActiveNotes: List<NoteEntity>,
    profilePinnedIds: Set<Long>,
    dailyResisted: Int,
    themeMode: ThemeMode,
    isAppBlockingEnabled: Boolean,
    onClaimRewards: () -> Unit,
    onToggleAppBlocking: (Boolean) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onBiometricClick: () -> Unit,
    onManualExport: () -> Unit,
    onLockAppImmediately: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onChoosePinnedNotesClick: () -> Unit,
    onManageAppBlockingClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // ── UNCLAIMED REWARDS BANNER (If tasks/habits earned rewards) ────────
        if (profile.pendingXp > 0 || profile.pendingGold > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onClaimRewards() },
                shape = RoundedCornerShape(14.dp),
                color = colors.accentPrimary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, colors.accentPrimary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "UNCLAIMED REWARDS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = colors.accentPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "+${profile.pendingXp} XP • +${profile.pendingGold} GOLD",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = colors.accentGold
                        )
                    }

                    Button(
                        onClick = onClaimRewards,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentPrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("CLAIM", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // ── SECTION 1: MATRIX // 365D ──────────────────────────────────────────
        EnclaveMatrixSection(
            colors = colors,
            profile = profile,
            activeStreak = activeStreak,
            longestStreak = longestStreak,
            effectiveLevel = effectiveLevel,
            dailyUsage = dailyUsage
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── SECTION 2: REAL-TIME APP BLOCKING ──────────────────────────────────
        EnclaveAppBlockingSection(
            colors = colors,
            restrictions = restrictions,
            dailyResisted = dailyResisted,
            lifetimeResists = profile.lifetimeResists,
            onManageClick = onManageAppBlockingClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── SECTION 3: PINNED NOTES ────────────────────────────────────────────
        EnclavePinnedNotesSection(
            colors = colors,
            activeNotes = allActiveNotes,
            profilePinnedIds = profilePinnedIds,
            onNoteClick = onOpenNote,
            onChoosePinnedClick = onChoosePinnedNotesClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── SECTION 4: SYSTEM PREFERENCES ──────────────────────────────────────
        EnclaveSystemPreferencesSection(
            colors = colors,
            isAppBlockingEnabled = isAppBlockingEnabled,
            onToggleAppBlocking = onToggleAppBlocking,
            themeMode = themeMode,
            onThemeModeSelected = onThemeModeSelected,
            onBiometricClick = onBiometricClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── SECTION 5: MANUAL EXPORT (FULL BACKUP) BUTTON ─────────────────────
        Button(
            onClick = onManualExport,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.textPrimary,
                contentColor = colors.bg
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = colors.bg,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Manual Export (Encrypted Backup)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = colors.bg
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── SECTION 6: LOCK APP IMMEDIATELY ACTION ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLockAppImmediately() }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = "Lock app",
                tint = colors.textMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Lock app immediately",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = colors.textMuted
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// A. MATRIX // 365D COMPONENT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EnclaveMatrixSection(
    colors: EnclaveColors,
    profile: UserProfile,
    activeStreak: Int,
    longestStreak: Int,
    effectiveLevel: Int,
    dailyUsage: Map<Int, Long>
) {
    val tierTitle = ProfileAvatarManager.getAvatarTitle(profile.selectedAvatar, profile.avatarTier).uppercase()
    val multiplier = FocusEconomyManager.getGoldMultiplier(effectiveLevel)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = "Matrix",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "MATRIX // 365D",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = colors.textPrimary
                )
            }

            // TIER Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.subCardBg,
                border = BorderStroke(1.dp, colors.subCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colors.accentCyan)
                    )
                    Text(
                        text = "TIER: $tierTitle",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Matrix Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Streak Metrics Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Col 1: ACTIVE STREAK
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ACTIVE STREAK",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$activeStreak",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "DAYS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = colors.textMuted,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Adjust,
                                contentDescription = null,
                                tint = colors.accentCyan,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (activeStreak > 0) "+100%" else "+0%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = colors.textSecondary
                            )
                        }
                    }

                    // Col 2: ALL-TIME RECORD
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(
                            text = "ALL-TIME RECORD",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$longestStreak",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "DAYS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = colors.textMuted,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "CONSISTENT DISCIPLINE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.5.sp
                            ),
                            color = colors.textMuted
                        )
                    }

                    // Col 3: FREEZE SHIELD
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FREEZE SHIELD",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${profile.streakFreezes}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = colors.textMuted,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "STREAK SAFEGUARD",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.5.sp
                            ),
                            color = colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Heatmap Grid: M, W, F rows x 18 week columns
                EnclaveHeatmapGrid(colors = colors, dailyUsage = dailyUsage)

                Spacer(modifier = Modifier.height(18.dp))

                // Heatmap Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MULTIPLIER: ${multiplier}x",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textPrimary
                        )
                        Text(
                            text = "GOLD BOOST",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.accentGold
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "LEVEL $effectiveLevel OPERATOR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textMuted
                        )
                        Text(
                            text = "SYSTEM ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.accentGreen
                        )
                    }

                    // LESS [ ][ ][ ][ ] MORE
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "LESS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(1.5.dp)).background(colors.subCardBg))
                        Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(1.5.dp)).background(colors.accentPrimary.copy(alpha = 0.35f)))
                        Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(1.5.dp)).background(colors.accentPrimary.copy(alpha = 0.65f)))
                        Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(1.5.dp)).background(colors.accentPrimary))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "MORE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnclaveHeatmapGrid(colors: EnclaveColors, dailyUsage: Map<Int, Long>) {
    val context = LocalContext.current
    val rowLabels = listOf("M", "", "W", "", "F", "", "")
    val totalCols = 18

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Labels column (M, W, F)
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 1.dp)
        ) {
            for (r in 0..6) {
                Box(
                    modifier = Modifier.size(11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (rowLabels[r].isNotEmpty()) {
                        Text(
                            text = rowLabels[r],
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textMuted
                        )
                    }
                }
            }
        }

        // Columns of squares
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState(0)),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (col in 0 until totalCols) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (row in 0..6) {
                        val daysAgo = (totalCols - 1 - col) * 7 + (6 - row)
                        val targetCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
                        val dayOfYear = targetCal.get(Calendar.DAY_OF_YEAR)
                        val realMinutes = (dailyUsage[dayOfYear] ?: 0L) / (60 * 1000L)

                        val cellColor = when {
                            realMinutes > 45 -> colors.accentPrimary
                            realMinutes > 25 -> colors.accentPrimary.copy(alpha = 0.7f)
                            realMinutes > 10 -> colors.accentPrimary.copy(alpha = 0.45f)
                            realMinutes > 0 -> colors.accentPrimary.copy(alpha = 0.25f)
                            else -> colors.subCardBg
                        }

                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(cellColor)
                                .clickable {
                                    val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(targetCal.time)
                                    val statusStr = if (realMinutes > 0) "Focus: ${realMinutes}m ($dateStr)" else "Rest day ($dateStr)"
                                    Toast.makeText(context, statusStr, Toast.LENGTH_SHORT).show()
                                }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// B. REAL-TIME APP BLOCKING COMPONENT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EnclaveAppBlockingSection(
    colors: EnclaveColors,
    restrictions: List<AppRestriction>,
    dailyResisted: Int,
    lifetimeResists: Int,
    onManageClick: () -> Unit
) {
    val hardApps = restrictions.filter { it.isRestricted && it.mode.equals("HARD", ignoreCase = true) }
    val softApps = restrictions.filter { it.isRestricted && !it.mode.equals("HARD", ignoreCase = true) }
    val totalRestricted = restrictions.count { it.isRestricted }
    val resistedDisplay = if (dailyResisted > 0) dailyResisted else lifetimeResists

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = "App Blocking",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "APP BLOCKING",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = colors.textPrimary
                )
            }

            // ● X APPS RESTRICTED Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.subCardBg,
                border = BorderStroke(1.dp, colors.subCardBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onManageClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (totalRestricted > 0) colors.accentCyan else colors.textMuted)
                    )
                    Text(
                        text = "$totalRestricted APPS RESTRICTED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // 3 Column Metrics Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Col 1: HARD BLOCKED
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HARD BLOCKED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${hardApps.size}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "APPS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = colors.textMuted,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Zero-Access Strict",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.5.sp
                            ),
                            color = colors.textMuted
                        )
                    }

                    // Col 2: SOFT LOCKED
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(
                            text = "SOFT LOCKED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${softApps.size}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "APPS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = colors.textMuted,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Wait Timer Delay",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.5.sp
                            ),
                            color = colors.textMuted
                        )
                    }

                    // Col 3: TIMES RESISTED
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TIMES RESISTED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$resistedDisplay",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "TIMES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = colors.textMuted,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Back-outs at barrier",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.5.sp
                            ),
                            color = colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Row 1: HARD BLOCKED: Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "HARD BLOCKED:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.textMuted
                    )

                    if (hardApps.isNotEmpty()) {
                        hardApps.forEach { app ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.subCardBg,
                                border = BorderStroke(1.dp, colors.subCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = app.appName.ifBlank { app.packageName },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "• Strict",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = colors.textMuted
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.subCardBg,
                            border = BorderStroke(1.dp, colors.subCardBorder),
                            modifier = Modifier.clickable { onManageClick() }
                        ) {
                            Text(
                                text = "None configured (Tap to add)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = colors.textMuted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 2: SOFT LOCKED: Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SOFT LOCKED:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.textMuted
                    )

                    if (softApps.isNotEmpty()) {
                        softApps.forEach { app ->
                            val delayStr = if (app.timeLimitMinutes > 0) "${app.timeLimitMinutes}m Delay" else "30s Delay"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.subCardBg,
                                border = BorderStroke(1.dp, colors.subCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = app.appName.ifBlank { app.packageName },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "• $delayStr",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = colors.textMuted
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.subCardBg,
                            border = BorderStroke(1.dp, colors.subCardBorder),
                            modifier = Modifier.clickable { onManageClick() }
                        ) {
                            Text(
                                text = "None configured (Tap to add)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = colors.textMuted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Footer: [🛡 ENCLAVE APP SHIELDS] [ACTIVE ENFORCEMENT]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = colors.accentGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "ENCLAVE APP SHIELDS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textMuted
                        )
                    }

                    Text(
                        text = "ACTIVE ENFORCEMENT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.accentCyan
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// C. PINNED NOTES COMPONENT WITH REAL NOTES & NOTE SELECTION
// ─────────────────────────────────────────────────────────────────────────────
private fun getCleanPreviewText(content: String): String {
    if (content.isBlank()) return "Note record in enclave storage..."
    val (parsedText, _) = try {
        RichTextEngine.parse(content)
    } catch (_: Exception) {
        content to emptyList()
    }
    val clean = parsedText
        .replace(Regex("<!--[\\s\\S]*?-->"), "")
        .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("\\*(.*?)\\*"), "$1")
        .replace(Regex("~~(.*?)~~"), "$1")
        .replace(Regex("`{1,3}(.*?)`{1,3}"), "$1")
        .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
        .replace(Regex("^[>\\-*+]\\s*", RegexOption.MULTILINE), "")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .trim()
    return if (clean.isBlank()) "Note record in enclave storage..." else clean
}

@Composable
private fun EnclavePinnedNotesSection(
    colors: EnclaveColors,
    activeNotes: List<NoteEntity>,
    profilePinnedIds: Set<Long>,
    onNoteClick: (Long) -> Unit,
    onChoosePinnedClick: () -> Unit
) {
    val realPinned = remember(activeNotes, profilePinnedIds) {
        activeNotes.filter { profilePinnedIds.contains(it.id) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.GridView,
                    contentDescription = "Pinned Notes",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "PINNED NOTES",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = colors.textPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button to choose / pin notes
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.subCardBg,
                    border = BorderStroke(1.dp, colors.subCardBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onChoosePinnedClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pin notes",
                            tint = colors.accentCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Pin Notes",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textPrimary
                        )
                    }
                }

                Text(
                    text = "${realPinned.size} WORKS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = colors.textMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Pinned Notes List
        if (realPinned.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                realPinned.forEach { note ->
                    val uri = note.getImageUris().firstOrNull()
                    val timeAgo = formatTimeAgo(note.updatedAt)
                    val tags = remember(note) { note.getLabels() }
                    val cleanPreview = remember(note.content) { getCleanPreviewText(note.content) }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.cardBg,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onNoteClick(note.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Generative procedural / Image Thumbnail Preview
                            CyberNoteThumbnail(
                                colors = colors,
                                seed = note.id.toInt(),
                                imageUri = uri,
                                modifier = Modifier.size(62.dp)
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            // Note Info Column
                            Column(modifier = Modifier.weight(1f)) {
                                // Title & Time Ago
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = note.title.ifBlank { "Untitled Note" },
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        ),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Updated $timeAgo",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = colors.textMuted,
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                // Content Description (Cleaned of raw markdown and Notesnook block markup)
                                Text(
                                    text = cleanPreview,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (tags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    // Tags row - Only rendered if user actually assigned labels
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        tags.take(3).forEach { tag ->
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = colors.subCardBg,
                                                border = BorderStroke(1.dp, colors.subCardBorder)
                                            ) {
                                                Text(
                                                    text = tag.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.5.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    color = colors.textSecondary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Empty Pinned Notes Card with Call to Action
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.cardBg,
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onChoosePinnedClick() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = colors.accentCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No Notes Currently Pinned",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pin quick reference notes, protocols, or journals from your Notes library.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onChoosePinnedClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentPrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Select Notes to Pin", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}

/**
 * Procedural generative spatial canvas thumbnail
 */
@Composable
private fun CyberNoteThumbnail(
    colors: EnclaveColors,
    seed: Int,
    imageUri: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.subCardBg)
            .border(1.dp, colors.subCardBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUri.isNullOrBlank()) {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val strokeColor = colors.textMuted.copy(alpha = 0.4f)
            val dotColor = colors.textPrimary.copy(alpha = 0.8f)
            val accentDot = colors.accentCyan

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val rnd = Random(seed.toLong())

                // Technical grid lines
                val step = w / 4f
                for (i in 0..4) {
                    val x = i * step
                    drawLine(color = strokeColor.copy(alpha = 0.2f), start = Offset(x, 0f), end = Offset(x, h), strokeWidth = 1f)
                    drawLine(color = strokeColor.copy(alpha = 0.2f), start = Offset(0f, x), end = Offset(w, x), strokeWidth = 1f)
                }

                // Generative procedural geometric shapes
                when (abs(seed) % 3) {
                    0 -> {
                        for (i in 0..8) {
                            val cx = rnd.nextFloat() * (w - 12f) + 6f
                            val cy = rnd.nextFloat() * (h - 12f) + 6f
                            val radius = rnd.nextFloat() * 3.5f + 1.5f
                            drawCircle(color = dotColor, center = Offset(cx, cy), radius = radius)
                        }
                        drawLine(color = strokeColor, start = Offset(w * 0.2f, h * 0.75f), end = Offset(w * 0.8f, h * 0.35f), strokeWidth = 1.2f)
                    }
                    1 -> {
                        drawCircle(color = strokeColor, center = Offset(w * 0.5f, h * 0.5f), radius = w * 0.35f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                        drawCircle(color = accentDot, center = Offset(w * 0.5f, h * 0.5f), radius = 3.5f)
                    }
                    else -> {
                        val path = Path().apply {
                            moveTo(w * 0.15f, h * 0.6f)
                            lineTo(w * 0.45f, h * 0.6f)
                            lineTo(w * 0.65f, h * 0.3f)
                            lineTo(w * 0.85f, h * 0.3f)
                        }
                        drawPath(path = path, color = dotColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f))
                        drawCircle(color = accentDot, center = Offset(w * 0.85f, h * 0.3f), radius = 3.5f)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// D. SYSTEM PREFERENCES COMPONENT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EnclaveSystemPreferencesSection(
    colors: EnclaveColors,
    isAppBlockingEnabled: Boolean,
    onToggleAppBlocking: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onBiometricClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "System Preferences",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "SYSTEM PREFERENCES",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = colors.textPrimary
                )
            }

            Text(
                text = "ENCLAVE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = colors.textMuted
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Preference Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                // Item 1: App blocking
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = "App blocking",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "App blocking shields",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                ),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Native overlay defense against distraction loops",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = colors.textMuted
                            )
                        }
                    }

                    Switch(
                        checked = isAppBlockingEnabled,
                        onCheckedChange = { onToggleAppBlocking(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = colors.accentPrimary,
                            uncheckedThumbColor = colors.textMuted,
                            uncheckedTrackColor = colors.subCardBg,
                            uncheckedBorderColor = colors.subCardBorder
                        )
                    )
                }

                HorizontalDivider(color = colors.cardBorder, thickness = 0.8.dp)

                // Item 2: Biometric login
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onBiometricClick() }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = "Biometric login",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Biometric login & vaults",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            ),
                            color = colors.textPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.accentCyan
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = colors.cardBorder, thickness = 0.8.dp)

                // Item 3: Theme mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = "Theme",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Theme mode",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            ),
                            color = colors.textPrimary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.subCardBg,
                        border = BorderStroke(1.dp, colors.subCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val options = listOf(
                                "Sys" to ThemeMode.SYSTEM,
                                "Light" to ThemeMode.LIGHT,
                                "Dark" to ThemeMode.DARK
                            )
                            options.forEach { (label, mode) ->
                                val isSelected = when (mode) {
                                    ThemeMode.SYSTEM -> themeMode == ThemeMode.SYSTEM
                                    ThemeMode.LIGHT -> themeMode == ThemeMode.LIGHT
                                    ThemeMode.DARK -> themeMode != ThemeMode.SYSTEM && themeMode != ThemeMode.LIGHT
                                    else -> false
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) colors.cardBg else Color.Transparent)
                                        .clickable { onThemeModeSelected(mode) }
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.5.sp
                                        ),
                                        color = if (isSelected) colors.textPrimary else colors.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. ACHIEVEMENTS TAB (TAB 1)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AchievementsEnclaveTab(
    colors: EnclaveColors,
    profile: UserProfile,
    stats: FocusStats
) {
    val context = LocalContext.current
    val achievements = remember(profile, stats) { getAchievements(profile, stats) }
    val unlockedCount = achievements.count { it.isUnlocked }
    val totalCount = achievements.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Milestones & Badges",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "$unlockedCount of $totalCount Unlocked",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = colors.textMuted
                        )
                    }

                    Text(
                        text = "${(unlockedCount.toFloat() / totalCount * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.accentCyan
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { (unlockedCount.toFloat() / totalCount).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = colors.accentCyan,
                    trackColor = colors.subCardBg
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grid of badges
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(achievements, key = { it.title }) { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.cardBg,
                    border = BorderStroke(
                        1.dp,
                        if (item.isUnlocked) item.color.copy(alpha = 0.5f) else colors.cardBorder
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            val st = if (item.isUnlocked) "UNLOCKED" else "LOCKED"
                            Toast.makeText(context, "${item.title} ($st): ${item.description}", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MedievalMedal(
                            iconRes = item.iconRes,
                            color = item.color,
                            isUnlocked = item.isUnlocked,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = if (item.isUnlocked) colors.textPrimary else colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. SYSTEM ENCLAVE LOGS (TAB 2)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EnclaveLogsTab(
    colors: EnclaveColors,
    profile: UserProfile,
    nodeId: String,
    yearMonth: String,
    restrictedCount: Int
) {
    val logItems = listOf(
        Triple("13:07:44", "ZERO-KNOWLEDGE AUDIT", "Memory key sanitization verified on vault background handshake."),
        Triple("12:45:10", "ENCLAVE SHIELD DEFENSE", "Active access boundary defended ($restrictedCount apps restricted)."),
        Triple("11:20:00", "NODE HEARTBEAT", "Node $nodeId verified deterministic state alignment with Enclave layer."),
        Triple("09:15:32", "DISCIPLINE ENGINE", "Focus session checkpoint completed: +45m logged into local ledger."),
        Triple("Genesis", "IDENTITY GENESIS", "${profile.name} $yearMonth protocol active. Hardware cryptographic key live.")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ENCLAVE AUDIT TRAIL",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.textPrimary
                    )
                    Text(
                        text = "NODE VERIFIED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.accentCyan
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                logItems.forEachIndexed { i, (time, tag, msg) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textMuted,
                            modifier = Modifier.width(68.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = colors.accentCyan
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                ),
                                color = colors.textSecondary
                            )
                        }
                    }
                    if (i < logItems.size - 1) {
                        HorizontalDivider(color = colors.cardBorder, thickness = 0.8.dp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 8. INTERACTIVE MODALS & DIALOGS
// ─────────────────────────────────────────────────────────────────────────────

// A. Edit Profile Modal (Redesigned matching screenshot)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileModal(
    colors: EnclaveColors,
    profile: UserProfile,
    nodeId: String,
    shortNodeId: String,
    onDismiss: () -> Unit,
    onOpenAvatarStore: () -> Unit,
    onSave: (name: String, role: String, bio: String) -> Unit
) {
    var name by remember { mutableStateOf(profile.name.ifBlank { "Elena Vance" }) }
    var role by remember { mutableStateOf(profile.role.ifBlank { "System Architect & Generative Artist" }) }
    var bio by remember { mutableStateOf(profile.bio.ifBlank { "Synthesizing deterministic typography with stochastic neural latent spaces. Enclave-first computing enthusiast." }) }
    val context = LocalContext.current
    val avRes = ProfileAvatarManager.getAvatarImageRes(profile.selectedAvatar, profile.avatarTier)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardBg,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle pill
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textMuted.copy(alpha = 0.35f))
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Header: [✕ Cancel]     Edit Profile     [Apply ✓]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.textSecondary
                    )
                }

                Text(
                    text = "Edit Profile",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = colors.textPrimary
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.textPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSave(name, role, bio) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Apply",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = colors.bg
                        )
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Apply",
                            tint = colors.bg,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Avatar with circular Tune button overlay
            Box(
                modifier = Modifier.size(108.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(colors.subCardBg)
                        .clickable { onOpenAvatarStore() },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = avRes),
                        contentDescription = "Profile Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }

                // Small circular tune button at bottom-right of avatar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.subCardBg)
                        .border(2.dp, colors.cardBg, CircleShape)
                        .clickable { onOpenAvatarStore() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Avatar Wardrobe",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Node ID Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.subCardBg,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Node ID", nodeId))
                        Toast.makeText(context, "Node ID copied: $nodeId", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = colors.accentCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "NODE: $shortNodeId",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp
                        ),
                        color = colors.textSecondary
                    )
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy",
                        tint = colors.textMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Form Fields
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Section Header
                Text(
                    text = "01 // PUBLIC SIGNATURE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp
                    ),
                    color = colors.textMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Field 1: Public Alias
                Text(
                    text = "Public Alias",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.subCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(colors.accentCyan),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Verified",
                            tint = colors.accentCyan.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Field 2: Who are you?
                Text(
                    text = "Who are you?",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.subCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Role",
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        BasicTextField(
                            value = role,
                            onValueChange = { role = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(colors.accentCyan),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Field 3: Enclave Manifesto
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enclave Manifesto",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        ),
                        color = colors.textSecondary
                    )
                    Text(
                        text = "${bio.length}/240",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (bio.length > 240) colors.accentRed else colors.textMuted
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.subCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        BasicTextField(
                            value = bio,
                            onValueChange = { if (it.length <= 240) bio = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(colors.accentCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp)
                        )
                    }
                }
            }
        }
    }
}

// B. Full Avatar Store & Selection Modal
@Composable
private fun AvatarStoreModal(
    colors: EnclaveColors,
    profile: UserProfile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var avatarToPurchase by remember { mutableStateOf<ProfileAvatar?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AVATAR VAULT & SHOP",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp
                            ),
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Unlock crests with discipline and Gold coins.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = colors.textMuted
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.subCardBg,
                        border = BorderStroke(1.dp, colors.subCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🪙", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${profile.gold}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = colors.accentGold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ── SECTION 1: VIP MILESTONES ────────────────────────────────
                Text(
                    text = "VIP MILESTONE CRESTS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    ),
                    color = colors.accentCyan
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    items(ProfileAvatarManager.tierAvatars, key = { it.id }) { avatar ->
                        val isUnlocked = avatar.tier <= profile.avatarTier || profile.avatarTier >= 5
                        val isCurrent = profile.selectedAvatar == avatar.id || (profile.selectedAvatar == "tier_1" && avatar.id == "tier_1")

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isUnlocked) colors.subCardBg else colors.subCardBg.copy(alpha = 0.4f),
                            border = BorderStroke(
                                if (isCurrent) 2.dp else 1.dp,
                                if (isCurrent) avatar.borderColor else if (isUnlocked) colors.subCardBorder else Color.Transparent
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = isUnlocked) {
                                    FocusEconomyManager.equipAvatar(avatar.id)
                                    Toast.makeText(context, "Equipped ${avatar.title}", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, if (isUnlocked) avatar.borderColor else colors.subCardBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = avatar.imageRes),
                                        contentDescription = avatar.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .alpha(if (isUnlocked) 1f else 0.4f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = avatar.title,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                    color = if (isUnlocked) colors.textPrimary else colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isCurrent) "Equipped" else if (isUnlocked) "Equip" else "Tier ${avatar.tier}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = if (isCurrent) avatar.borderColor else colors.textMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── SECTION 2: STORE AVATARS (BOUGHT WITH GOLD) ──────────────
                Text(
                    text = "STORE AVATARS (PURCHASE WITH GOLD)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    ),
                    color = colors.accentGold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    items(ProfileAvatarManager.storeAvatars, key = { it.id }) { avatar ->
                        val isPurchased = profile.purchasedAvatars.contains(avatar.id)
                        val isCurrent = profile.selectedAvatar == avatar.id
                        val canAfford = profile.gold >= avatar.cost

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isPurchased) colors.subCardBg else colors.subCardBg.copy(alpha = 0.5f),
                            border = BorderStroke(
                                if (isCurrent) 2.dp else 1.dp,
                                if (isCurrent) avatar.borderColor else if (isPurchased) colors.subCardBorder else Color.Transparent
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isPurchased) {
                                        FocusEconomyManager.equipAvatar(avatar.id)
                                        Toast.makeText(context, "Equipped ${avatar.title}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        avatarToPurchase = avatar
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, if (isPurchased || canAfford) avatar.borderColor else colors.subCardBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = avatar.imageRes),
                                        contentDescription = avatar.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .alpha(if (isPurchased || canAfford) 1f else 0.45f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = avatar.title,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                    color = if (isPurchased || canAfford) colors.textPrimary else colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                if (!isPurchased) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🪙", fontSize = 9.sp)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "${avatar.cost}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                            color = if (canAfford) colors.accentGold else colors.textMuted
                                        )
                                    }
                                } else {
                                    Text(
                                        text = if (isCurrent) "Equipped" else "Equip",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = if (isCurrent) avatar.borderColor else colors.accentCyan
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Purchase confirmation dialog
    avatarToPurchase?.let { avatar ->
        AlertDialog(
            onDismissRequest = { avatarToPurchase = null },
            containerColor = colors.cardBg,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(2.dp, avatar.borderColor, CircleShape)
                    ) {
                        Image(
                            painter = painterResource(id = avatar.imageRes),
                            contentDescription = avatar.title,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Unlock ${avatar.title}?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Text(
                    text = "Do you want to unlock the ${avatar.title} crest? This will cost ${avatar.cost} Gold coins.\n\nYour current balance: ${profile.gold} Gold.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = FocusEconomyManager.purchaseAvatar(avatar.id, avatar.cost)
                        if (success) {
                            Toast.makeText(context, "Unlocked and equipped ${avatar.title}!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Insufficient Gold! Need ${avatar.cost - profile.gold} more Gold.", Toast.LENGTH_SHORT).show()
                        }
                        avatarToPurchase = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Purchase (${avatar.cost}g)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { avatarToPurchase = null }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }
}

// C. Choose Pinned Notes Dialog (Decoupled from Room DB note.isPinned)
@Composable
private fun ChoosePinnedNotesDialog(
    colors: EnclaveColors,
    activeNotes: List<NoteEntity>,
    pinnedNoteIds: Set<Long>,
    onTogglePin: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredNotes = remember(activeNotes, searchQuery) {
        if (searchQuery.isBlank()) activeNotes
        else activeNotes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.content.contains(searchQuery, ignoreCase = true)
        }
    }
    val pinnedCount = activeNotes.count { pinnedNoteIds.contains(it.id) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PINNED NOTES",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textPrimary
                        )
                        Text(
                            text = "$pinnedCount notes currently pinned to Identity",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = colors.textMuted
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = colors.textMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter notes...", fontSize = 13.sp, color = colors.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.accentCyan,
                        unfocusedBorderColor = colors.subCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Notes List with checkboxes
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredNotes.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (activeNotes.isEmpty()) "No active notes in your library." else "No notes match filter.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                            }
                        }
                    } else {
                        items(filteredNotes, key = { it.id }) { note ->
                            val isPinnedInProfile = pinnedNoteIds.contains(note.id)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPinnedInProfile) colors.accentPrimary.copy(alpha = 0.1f) else colors.subCardBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (isPinnedInProfile) colors.accentCyan.copy(alpha = 0.5f) else colors.subCardBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onTogglePin(note.id, !isPinnedInProfile) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isPinnedInProfile,
                                        onCheckedChange = { onTogglePin(note.id, it) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = colors.accentCyan,
                                            checkmarkColor = colors.bg
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = note.title.ifBlank { "Untitled Note" },
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val cleanSnippet = remember(note.content) { getCleanPreviewText(note.content) }
                                        Text(
                                            text = cleanSnippet,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = colors.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// D. Share Profile Card Modal
@Composable
private fun ShareProfileCardModal(
    colors: EnclaveColors,
    profile: UserProfile,
    nodeId: String,
    yearMonth: String,
    activeStreak: Int,
    restrictedCount: Int,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    val avatarRes = ProfileAvatarManager.getAvatarImageRes(profile.selectedAvatar, profile.avatarTier)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Card header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ENCLAVE IDENTITY CARD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        ),
                        color = colors.accentCyan
                    )
                    Text(
                        text = yearMonth,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.textMuted
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Avatar
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(colors.subCardBg)
                        .border(2.dp, colors.accentCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = avatarRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = profile.name.ifBlank { "Focus Warrior" },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = colors.textPrimary
                )

                Text(
                    text = profile.role.ifBlank { "Student" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "NODE: $nodeId",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = colors.textMuted
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Mini stats matrix
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GOLD", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textMuted)
                        Text("${profile.gold}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LEVEL", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textMuted)
                        Text("${profile.level}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("STREAK", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textMuted)
                        Text("${activeStreak}d", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, colors.subCardBorder)
                    ) {
                        Text("Close", color = colors.textPrimary)
                    }
                    Button(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentPrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Share Card", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// E. Overflow Menu
@Composable
private fun EnclaveOverflowMenu(
    colors: EnclaveColors,
    onDismiss: () -> Unit,
    onAvatarStore: () -> Unit,
    onEconomyRules: () -> Unit,
    onPreferencesHub: () -> Unit,
    onSecurityVaults: () -> Unit,
    onCopyNodeId: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "System Actions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                EnclaveMenuItem(colors = colors, icon = Icons.Filled.Face, label = "Avatar Wardrobe & Shop", onClick = onAvatarStore)
                EnclaveMenuItem(colors = colors, icon = Icons.Filled.Info, label = "Economy & Discipline Rules", onClick = onEconomyRules)
                EnclaveMenuItem(colors = colors, icon = Icons.Filled.Settings, label = "Preferences & Sync Hub", onClick = onPreferencesHub)
                EnclaveMenuItem(colors = colors, icon = Icons.Filled.Security, label = "Zero-Knowledge Vaults & Security", onClick = onSecurityVaults)
                EnclaveMenuItem(colors = colors, icon = Icons.Filled.ContentCopy, label = "Copy Identity Node ID", onClick = onCopyNodeId)
            }
        }
    }
}

@Composable
private fun EnclaveMenuItem(
    colors: EnclaveColors,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.accentCyan, modifier = Modifier.size(18.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
    }
}

// F. Economy Rules Dialog
@Composable
private fun EnclaveRulesDialog(colors: EnclaveColors, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Discipline & Economy Rules",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "• Experience (EXP): Earned by completing uninterrupted deep work focus blocks and maintaining discipline streaks.\n\n" +
                            "• Gold Protocol: Multiplied based on your current Level. Gold unlocks higher identity tiers and visual crests in the store.\n\n" +
                            "• App Blocking Enforcement: Shields apps dynamically via native overlay enforcement, preventing distraction loops.\n\n" +
                            "• Freeze Shields: Safeguard your discipline streak if a day is missed.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Understood", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// G. Hardware Identity Dialog
@Composable
private fun EnclaveSystemIdentityDialog(
    colors: EnclaveColors,
    nodeId: String,
    profile: UserProfile,
    yearMonth: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "RUN// SYSTEM ENCLAVE",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = colors.accentCyan
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Node Hardware ID: $nodeId\n" +
                            "Protocol: $yearMonth Enclave\n" +
                            "Operator: ${profile.name}\n" +
                            "Encryption: Argon2id KDF + AES-256-GCM\n" +
                            "BAL Overlay Defense: Active\n" +
                            "Status: Fully Synchronized",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// H. Streak Dialog
@Composable
private fun EnclaveStreakDialog(
    colors: EnclaveColors,
    activeStreak: Int,
    longestStreak: Int,
    streakFreezes: Int,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🔥", fontSize = 24.sp)
                    Text(
                        text = "Discipline Streak",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Active Streak: $activeStreak Days\n" +
                            "All-Time Record: $longestStreak Days\n" +
                            "Freeze Shields: $streakFreezes Active\n\n" +
                            "Maintain your daily focus blocks to keep your streak intact and maximize your Gold Multiplier.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 9. UTILITY HELPERS & REUSABLE BADGES
// ─────────────────────────────────────────────────────────────────────────────
private fun formatTimeAgo(timeMs: Long): String {
    val diff = System.currentTimeMillis() - timeMs
    val mins = diff / (60 * 1000L)
    if (mins < 1) return "Just now"
    if (mins < 60) return "${mins}m ago"
    val hours = mins / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    return "${days}d ago"
}

@Composable
fun MedievalMedal(iconRes: Int, color: Color, isUnlocked: Boolean, modifier: Modifier = Modifier) {
    val colors = rememberEnclaveColors()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isUnlocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.95f)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = 0.45f),
                                color.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.subCardBg)
                    .border(
                        width = 1.dp,
                        color = color.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.subCardBg.copy(alpha = 0.6f))
                    .border(
                        width = 1.dp,
                        color = colors.cardBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.35f),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                        androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
                    )
                )
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Locked",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

enum class BadgeType { LEVEL, STREAK, GOLD, TIER, FOCUS, RESIST, TASKS }

data class Achievement(val title: String, val description: String, val isUnlocked: Boolean, val type: BadgeType, val color: Color, val iconRes: Int)

fun getAchievements(profile: UserProfile, stats: FocusStats, isPro: Boolean = false): List<Achievement> {
    return listOf(
        Achievement("First Steps", "Reach Level 2", profile.level >= 2, BadgeType.LEVEL, Color(0xFF00E5FF), R.drawable.ic_achievement_first_steps),
        Achievement("Apprentice", "Reach Level 5", profile.level >= 5, BadgeType.LEVEL, Color(0xFF00E5FF), R.drawable.ic_achievement_apprentice),
        Achievement("Adept", "Reach Level 10", profile.level >= 10, BadgeType.LEVEL, Color(0xFF00B0FF), R.drawable.ic_achievement_adept),
        Achievement("Master", "Reach Level 25", profile.level >= 25, BadgeType.LEVEL, Color(0xFF651FFF), R.drawable.ic_achievement_master),
        Achievement("Grandmaster", "Reach Level 50", profile.level >= 50, BadgeType.LEVEL, Color(0xFFD500F9), R.drawable.ic_achievement_grandmaster),
        Achievement("Hero", "Reach Level 75", profile.level >= 75, BadgeType.LEVEL, Color(0xFFD500F9), R.drawable.ic_achievement_hero),
        Achievement("Legend", "Reach Level 100", profile.level >= 100, BadgeType.LEVEL, Color(0xFFFF1744), R.drawable.ic_achievement_legend),
        Achievement("Mythic", "Reach Level 200", profile.level >= 200, BadgeType.LEVEL, Color(0xFFFF1744), R.drawable.ic_achievement_mythic),

        Achievement("Task Starter", "Complete 1 Task", profile.lifetimeTasksCompleted >= 1, BadgeType.TASKS, Color(0xFF00E5FF), R.drawable.ic_achievement_fortnight_focus),
        Achievement("Productive Flow", "Complete 10 Tasks", profile.lifetimeTasksCompleted >= 10, BadgeType.TASKS, Color(0xFF00B0FF), R.drawable.ic_achievement_dragons_hoard),
        Achievement("Task Master", "Complete 50 Tasks", profile.lifetimeTasksCompleted >= 50, BadgeType.TASKS, Color(0xFF651FFF), R.drawable.ic_achievement_deep_work_sentinel),
        Achievement("Unstoppable Finisher", "Complete 100 Tasks", profile.lifetimeTasksCompleted >= 100, BadgeType.TASKS, Color(0xFFFFD700), R.drawable.ic_achievement_unshatterable),

        Achievement("Consistency", "3-Day Streak", profile.longestStreak >= 3, BadgeType.STREAK, Color(0xFFFF9800), R.drawable.ic_achievement_consistency),
        Achievement("Dedication", "7-Day Streak", profile.longestStreak >= 7, BadgeType.STREAK, Color(0xFFFF5722), R.drawable.ic_achievement_dedication),
        Achievement("Unbreakable", "30-Day Streak", profile.longestStreak >= 30, BadgeType.STREAK, Color(0xFFF44336), R.drawable.ic_achievement_unbreakable),
        Achievement("Habit Builder", "60-Day Streak", profile.longestStreak >= 60, BadgeType.STREAK, Color(0xFFF44336), R.drawable.ic_achievement_habit_builder),
        Achievement("Century Club", "100-Day Streak", profile.longestStreak >= 100, BadgeType.STREAK, Color(0xFFF44336), R.drawable.ic_achievement_century_club),
        Achievement("Year of Focus", "365-Day Streak", profile.longestStreak >= 365, BadgeType.STREAK, Color(0xFFF44336), R.drawable.ic_achievement_year_of_focus),

        Achievement("Piggy Bank", "100 Gold", profile.gold >= 100, BadgeType.GOLD, Color(0xFFFFC107), R.drawable.ic_achievement_piggy_bank),
        Achievement("Savings", "1,000 Gold", profile.gold >= 1000, BadgeType.GOLD, Color(0xFFFFC107), R.drawable.ic_achievement_savings),
        Achievement("Wealthy", "5,000 Gold", profile.gold >= 5000, BadgeType.GOLD, Color(0xFFFFB300), R.drawable.ic_achievement_wealthy),
        Achievement("Hoarder", "10,000 Gold", profile.gold >= 10000, BadgeType.GOLD, Color(0xFFFFA000), R.drawable.ic_achievement_hoarder),
        Achievement("Midas Touch", "50,000 Gold", profile.gold >= 50000, BadgeType.GOLD, Color(0xFFFFA000), R.drawable.ic_achievement_midas_touch),
        Achievement("Treasury", "100,000 Gold", profile.gold >= 100000, BadgeType.GOLD, Color(0xFFFFA000), R.drawable.ic_achievement_treasury),

        Achievement("Getting Started", "1 Hour Focused", profile.lifetimeFocusMins >= 60, BadgeType.FOCUS, Color(0xFF00B0FF), R.drawable.ic_achievement_getting_started),
        Achievement("Flow State", "10 Hours Focused", profile.lifetimeFocusMins >= 600, BadgeType.FOCUS, Color(0xFF00B0FF), R.drawable.ic_achievement_flow_state),
        Achievement("Zone In", "50 Hours Focused", profile.lifetimeFocusMins >= 3000, BadgeType.FOCUS, Color(0xFF651FFF), R.drawable.ic_achievement_zone_in),
        Achievement("Monk Mode", "500 Hours Focused", profile.lifetimeFocusMins >= 30000, BadgeType.FOCUS, Color(0xFFD500F9), R.drawable.ic_achievement_monk_mode),
        Achievement("Time Lord", "1000 Hours Focused", profile.lifetimeFocusMins >= 60000, BadgeType.FOCUS, Color(0xFFFF1744), R.drawable.ic_achievement_time_lord),
        Achievement("Master of Time", "2,000 Hours Focused", profile.lifetimeFocusMins >= 120000, BadgeType.FOCUS, Color(0xFFFF1744), R.drawable.ic_achievement_master_of_time),

        Achievement("First Temptation", "Resist 1 App", profile.lifetimeResists >= 1, BadgeType.RESIST, Color(0xFF4CAF50), R.drawable.ic_achievement_first_temptation),
        Achievement("Iron Will", "Resist 10 Apps", profile.lifetimeResists >= 10, BadgeType.RESIST, Color(0xFF4CAF50), R.drawable.ic_achievement_iron_will),
        Achievement("Willpower", "Resist 50 Apps", profile.lifetimeResists >= 50, BadgeType.RESIST, Color(0xFF009688), R.drawable.ic_achievement_willpower),
        Achievement("Dopamine Detox", "Resist 100 Apps", profile.lifetimeResists >= 100, BadgeType.RESIST, Color(0xFF009688), R.drawable.ic_achievement_dopamine_detox),
        Achievement("Zen Mind", "Resist 1,000 Apps", profile.lifetimeResists >= 1000, BadgeType.RESIST, Color(0xFF00BCD4), R.drawable.ic_achievement_zen_mind),

        Achievement("Scholar", "Unlock Scholar", profile.avatarTier >= 1, BadgeType.TIER, Color(0xFF4CAF50), R.drawable.ic_achievement_scholar),
        Achievement("Knight", "Unlock Knight", profile.avatarTier >= 2, BadgeType.TIER, Color(0xFF9C27B0), R.drawable.ic_achievement_knight),
        Achievement("Noble", "Unlock Noble", profile.avatarTier >= 3, BadgeType.TIER, Color(0xFF2196F3), R.drawable.ic_achievement_noble),
        Achievement("Emperor", "Unlock Emperor", profile.avatarTier >= 4, BadgeType.TIER, Color(0xFFFFD700), R.drawable.ic_achievement_emperor)
    )
}
