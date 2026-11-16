/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens

import com.focusbyrj.app.ui.tours.*

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.ui.navigation.Screen
import com.focusbyrj.app.ui.components.SettingsNavigation
import com.focusbyrj.app.util.*
import kotlinx.coroutines.launch
import java.util.*

import com.focusbyrj.app.ui.screens.account.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    navController: NavController? = null,
    onOpenNote: ((Long) -> Unit)? = null,
    onLockPrivateVault: (() -> Unit)? = null
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
    DisposableEffect(pinnedPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == null || key == "profile_pinned_ids") {
                profilePinnedIds = prefs.getStringSet("profile_pinned_ids", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
            }
        }
        pinnedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { pinnedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // Navigation & Tab state
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: Achievements

    // Dialog & sheet states
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAvatarStoreSheet by remember { mutableStateOf(false) }
    var showShareProfileDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showEnclaveSystemDialog by remember { mutableStateOf(false) }
    var showStreakDialog by remember { mutableStateOf(false) }
    var showChoosePinnedNotesDialog by remember { mutableStateOf(false) }

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
    }

    val profileTour = rememberSpotlightTour(AppTours.profile)
    SpotlightTour(profileTour, enabled = !showEditProfileDialog && !showAvatarStoreSheet && !showShareProfileDialog && !showOptionsMenu && !showRulesDialog && !showEnclaveSystemDialog && !showStreakDialog && !showChoosePinnedNotesDialog)
    Box(
        modifier = Modifier
            .pauseTourOnInteraction(profileTour)
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

            // QUICK STATS ROW: [GOLD] [LEVEL] [VAULT]
            Box(Modifier.spotlightTarget(profileTour, "profile_stats")) { EnclaveQuickStatsRow(
                colors = enclaveColors,
                gold = effectiveGold,
                level = effectiveLevel
            ) }

            Spacer(modifier = Modifier.height(14.dp))

            // NAVIGATION TABS: Overview | Achievements
            Box(Modifier.spotlightTarget(profileTour, "profile_tabs")) { EnclaveTabsRow(
                colors = enclaveColors,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            ) }

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
                        onClaimRewards = {
                            FocusEconomyManager.claimPendingRewards()
                            Toast.makeText(context, "Rewards claimed into your balance!", Toast.LENGTH_SHORT).show()
                        },
                        onFocusSettingsClick = { navController?.let { SettingsNavigation.open(it, "focus.home") } },
                        onAppearanceClick = { navController?.let { SettingsNavigation.open(it, "appearance.theme") } },
                        onSecurityClick = { navController?.let { SettingsNavigation.open(it, "security.home") } },
                        onProLicenseClick = { navController?.let { SettingsNavigation.open(it, "profile.pro") } },
                        onManualExport = { navController?.let { SettingsNavigation.open(it, "data.export") } },
                        onRestoreBackup = { navController?.let { SettingsNavigation.open(it, "data.restore") } },
                        onLockAppImmediately = {
                            if (onLockPrivateVault != null) onLockPrivateVault() else ArchiveVaultSecurity.lockVault()
                            Toast.makeText(context, "Private notes vault locked.", Toast.LENGTH_SHORT).show()
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
                        },
                        onHabitsClick = {
                            navController?.navigate(Screen.Habits.route) { launchSingleTop = true }
                        }
                    )
                }
                1 -> {
                    // TAB 1: ACHIEVEMENTS
                    AchievementsEnclaveTab(colors = enclaveColors, profile = profile, stats = stats)
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


}

// ─────────────────────────────────────────────────────────────────────────────
// 1. TOP BAR COMPONENT
// ─────────────────────────────────────────────────────────────────────────────
