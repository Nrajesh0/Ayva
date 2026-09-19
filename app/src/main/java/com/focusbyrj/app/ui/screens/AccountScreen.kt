package com.focusbyrj.app.ui.screens
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.ui.theme.MidnightBlack

import com.focusbyrj.app.ui.theme.BorderGlass
import com.focusbyrj.app.ui.theme.SurfaceDark
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.FocusStatsManager
import com.focusbyrj.app.util.ProfileAvatar
import com.focusbyrj.app.util.ProfileAvatarManager
import com.focusbyrj.app.util.UserProfile
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountScreen() {
    val context = LocalContext.current
    val profile by FocusEconomyManager.profileFlow.collectAsState()
    val stats by FocusStatsManager.statsFlow.collectAsState()
    val heatmapTheme by FocusStatsManager.themeFlow.collectAsState()
    val isPro by com.focusbyrj.app.util.LicenseManager.isProFlow.collectAsState()
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val achievementsSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    
    var showAvatarSheet by remember { mutableStateOf(false) }
    var showAchievementsSheet by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FocusStatsManager.refreshStats(context)
    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        
        if (profile.pendingXp > 0 || profile.pendingGold > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                    .clickable { FocusEconomyManager.claimPendingRewards() }
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Unclaimed Rewards", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${profile.pendingXp} XP • ${profile.pendingGold} Gold", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFD700))
                    }
                    androidx.compose.material3.Button(
                        onClick = { FocusEconomyManager.claimPendingRewards() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Claim", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ProfileCard(
                profile = profile, 
                onAvatarClick = { showAvatarSheet = true },
                onNameChange = { FocusEconomyManager.updateName(it) },
                onInfoClick = { showRulesDialog = true }
            )
        }

        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            androidx.compose.material3.TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                androidx.compose.material3.Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Overview", style = MaterialTheme.typography.titleSmall) }
                )
                androidx.compose.material3.Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Achievements", style = MaterialTheme.typography.titleSmall) }
                )
            }
            
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> OverviewTab(stats, heatmapTheme, profile)
                    1 -> {
                        val isPro by com.focusbyrj.app.util.LicenseManager.isProFlow.collectAsState()
                        AchievementsPreviewTab(profile, stats, isPro, onViewAllClick = { showAchievementsSheet = true })
                    }
                }
            }
        }
    }

    if (showAchievementsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAchievementsSheet = false },
            sheetState = achievementsSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "All Achievements",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                AchievementsTab(profile, stats, isPro)
            }
        }
    }

    if (showAvatarSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AvatarSelectionSheet(profile = profile)
        }
    }

    if (showRulesDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRulesDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Economy & Rules", style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    RuleSection(
                        title = "Experience (EXP)", 
                        desc = "Earned by completing focus sessions. Every minute of deep focus brings you closer to the next level.",
                        icon = Icons.Filled.Star,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    RuleSection(
                        title = "Gold Coins", 
                        desc = "Earned alongside EXP. Use Gold to unlock exclusive avatars and tiers in the store. Your current Level acts as a multiplier—higher levels yield more Gold!",
                        icon = Icons.Filled.MonetizationOn,
                        color = Color(0xFFFFD700)
                    )
                    Spacer(Modifier.height(16.dp))
                    RuleSection(
                        title = "Task & Occasion Rewards", 
                        desc = "Completing tasks grants EXP and Gold Coins directly to your Unclaimed Rewards:\n• Standard Task: +30 XP • +15 Coins\n• High Priority Task (⭐): +60 XP • +30 Coins\n• Occasion / Reminder: +40 XP • +20 Coins\n• Gold is boosted by your Level Multiplier (Current: ${com.focusbyrj.app.util.FocusEconomyManager.getGoldMultiplier(profile.level)}x)\nClaim them anytime from the Unclaimed Rewards card at the top of your Profile!",
                        icon = Icons.Filled.CheckCircle,
                        color = Color(0xFF00E5FF)
                    )
                    Spacer(Modifier.height(16.dp))
                    RuleSection(
                        title = "The Penalty", 
                        desc = "Discipline is key. Exiting a session prematurely or breaking a lock will incur a steep EXP penalty, setting back your progress.",
                        icon = Icons.Filled.Warning,
                        color = Color(0xFFF43F5E)
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = { showRulesDialog = false },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Understood", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun RuleSection(title: String, desc: String, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun ProfileCard(
    profile: UserProfile,
    onAvatarClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onInfoClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var isEditingName by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(profile.name) }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    val currentLevelXp = FocusEconomyManager.requiredXpForLevel(profile.level)
    val nextLevelXp = FocusEconomyManager.requiredXpForLevel(profile.level + 1)
    val targetXpProgress = if (profile.level >= 200) 1f else ((profile.xp - currentLevelXp).toFloat() / (nextLevelXp - currentLevelXp).toFloat()).coerceIn(0f, 1f)
    
    val xpProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetXpProgress,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 1500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp, 
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), 
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val currentAvatar = ProfileAvatarManager.getAvatar(profile.selectedAvatar, profile.avatarTier)
            
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clickable { onAvatarClick() }
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(3.dp, currentAvatar.borderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = currentAvatar.imageRes),
                        contentDescription = currentAvatar.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
                
                // Edit / Camera Badge indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, currentAvatar.borderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Change Avatar",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                if (isEditingName) {
                    BasicTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                isEditingName = false
                                if (tempName.isNotBlank()) onNameChange(tempName)
                                keyboardController?.hide()
                            }
                        )
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isEditingName = true }) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = currentAvatar.title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    color = currentAvatar.borderColor
                )
            }
            
            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Economy Rules",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Level ${profile.level}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            if (profile.level >= 200) {
                Text("MAX LEVEL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            } else {
                Text("${profile.xp} / $nextLevelXp XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { xpProgress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        
        val unlockedCount = getAchievements(profile, com.focusbyrj.app.util.FocusStats(0,0, emptyMap())).count { it.isUnlocked }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileStatItem(
                icon = androidx.compose.material.icons.Icons.Filled.MonetizationOn, 
                value = "${profile.gold}", 
                label = "Coins", 
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)))
            ProfileStatItem(
                icon = androidx.compose.material.icons.Icons.Filled.LocalFireDepartment, 
                value = "${FocusEconomyManager.getGoldMultiplier(profile.level)}x", 
                label = "Multiplier", 
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)))
            ProfileStatItem(
                icon = androidx.compose.material.icons.Icons.Filled.EmojiEvents, 
                value = "$unlockedCount", 
                label = "Awards", 
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
        
        if (profile.level < profile.maxLevel) {
            Spacer(modifier = Modifier.height(18.dp))
            androidx.compose.material3.Button(
                onClick = { FocusEconomyManager.recoverXp(500, 500) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(androidx.compose.material.icons.Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recover 500 XP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("500", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(androidx.compose.material.icons.Icons.Filled.MonetizationOn, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun ProfileStatItem(icon: ImageVector, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OverviewTab(stats: com.focusbyrj.app.util.FocusStats, heatmapTheme: com.focusbyrj.app.util.HeatmapTheme, profile: UserProfile) {
    val streakSource by com.focusbyrj.app.util.StreakManager.streakSourceFlow.collectAsState()
    val drillProfile by com.focusbyrj.app.util.AptitudeManager.profileFlow.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val isDrillMode = streakSource == com.focusbyrj.app.util.StreakSource.DRILL

    val (activeCurrentStreak, activeLongestStreak) = when (streakSource) {
        com.focusbyrj.app.util.StreakSource.DRILL -> drillProfile.currentStreak to drillProfile.longestStreak
        com.focusbyrj.app.util.StreakSource.FOCUS -> stats.currentStreak to maxOf(stats.longestStreak, profile.longestStreak)
    }

    val streakHeaderTitle = when (streakSource) {
        com.focusbyrj.app.util.StreakSource.DRILL -> "Streaks (Drills)"
        com.focusbyrj.app.util.StreakSource.FOCUS -> "Streaks (Focus)"
    }

    val activeDailyUsage = if (isDrillMode) drillProfile.dailyDrillCounts else stats.dailyFocusMinutes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        // Duolingo-style Daily Quests card
        DailyQuestsCard()

        Spacer(modifier = Modifier.height(16.dp))
        com.focusbyrj.app.ui.components.HeatmapAndStreaksWidget(
            dailyUsage = activeDailyUsage,
            theme = heatmapTheme,
            profile = profile,
            currentStreak = activeCurrentStreak,
            longestStreak = activeLongestStreak,
            streakTypeLabel = streakHeaderTitle,
            isDrillMode = isDrillMode,
            onToggleStreakSource = {
                val nextSource = if (isDrillMode) com.focusbyrj.app.util.StreakSource.FOCUS else com.focusbyrj.app.util.StreakSource.DRILL
                com.focusbyrj.app.util.StreakManager.setStreakSource(context, nextSource)
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        StatsGrid(profile = profile)
        
        Spacer(modifier = Modifier.height(64.dp))
    }
}


@Composable
fun StatsGrid(profile: UserProfile) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Lifetime Stats", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                title = "Total Focus",
                value = formatMinutesToHours(profile.lifetimeFocusMins),
                icon = androidx.compose.material.icons.Icons.Filled.CheckCircle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Apps Resisted",
                value = profile.lifetimeResists.toString(),
                icon = androidx.compose.material.icons.Icons.Filled.Shield,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                title = "Tasks Completed",
                value = profile.lifetimeTasksCompleted.toString(),
                icon = androidx.compose.material.icons.Icons.Filled.CheckCircle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Vault Coins",
                value = profile.gold.toString(),
                icon = androidx.compose.material.icons.Icons.Filled.MonetizationOn,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

fun formatMinutesToHours(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    val mins = minutes % 60
    return if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
}


@Composable
fun AchievementsPreviewTab(profile: UserProfile, stats: com.focusbyrj.app.util.FocusStats, isPro: Boolean, onViewAllClick: () -> Unit) {
    val achievements = getAchievements(profile, stats, isPro)
    val unlockedCount = achievements.count { it.isUnlocked }
    val totalCount = achievements.size
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Achievements", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("$unlockedCount / $totalCount Unlocked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.TextButton(onClick = onViewAllClick) {
                        Text("View All", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val previewList = achievements.sortedByDescending { it.isUnlocked }.take(4)
                    previewList.forEach { ach ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MedievalMedal(iconRes = ach.iconRes, color = ach.color, isUnlocked = ach.isUnlocked, modifier = Modifier.size(56.dp))
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(64.dp))
    }
}

enum class BadgeType { LEVEL, STREAK, GOLD, TIER, FOCUS, RESIST, TASKS }

data class Achievement(val title: String, val description: String, val isUnlocked: Boolean, val type: BadgeType, val color: Color, val iconRes: Int)

fun getAchievements(profile: UserProfile, stats: com.focusbyrj.app.util.FocusStats, isPro: Boolean = false): List<Achievement> {
    return listOf(
        Achievement("First Steps", "Reach Level 2", profile.level >= 2, BadgeType.LEVEL, Color(0xFF00E5FF), com.focusbyrj.app.R.drawable.ic_achievement_first_steps),
        Achievement("Apprentice", "Reach Level 5", profile.level >= 5, BadgeType.LEVEL, Color(0xFF00E5FF), com.focusbyrj.app.R.drawable.ic_achievement_apprentice),
        Achievement("Adept", "Reach Level 10", profile.level >= 10, BadgeType.LEVEL, Color(0xFF00B0FF), com.focusbyrj.app.R.drawable.ic_achievement_adept),
        Achievement("Master", "Reach Level 25", profile.level >= 25, BadgeType.LEVEL, Color(0xFF651FFF), com.focusbyrj.app.R.drawable.ic_achievement_master),
        Achievement("Grandmaster", "Reach Level 50", profile.level >= 50, BadgeType.LEVEL, Color(0xFFD500F9), com.focusbyrj.app.R.drawable.ic_achievement_grandmaster),
        Achievement("Hero", "Reach Level 75", profile.level >= 75, BadgeType.LEVEL, Color(0xFFD500F9), com.focusbyrj.app.R.drawable.ic_achievement_hero),
        Achievement("Legend", "Reach Level 100", profile.level >= 100, BadgeType.LEVEL, Color(0xFFFF1744), com.focusbyrj.app.R.drawable.ic_achievement_legend),
        Achievement("Mythic", "Reach Level 200", profile.level >= 200, BadgeType.LEVEL, Color(0xFFFF1744), com.focusbyrj.app.R.drawable.ic_achievement_mythic),

        Achievement("Task Starter", "Complete 1 Task", profile.lifetimeTasksCompleted >= 1, BadgeType.TASKS, Color(0xFF00E5FF), com.focusbyrj.app.R.drawable.ic_achievement_fortnight_focus),
        Achievement("Productive Flow", "Complete 10 Tasks", profile.lifetimeTasksCompleted >= 10, BadgeType.TASKS, Color(0xFF00B0FF), com.focusbyrj.app.R.drawable.ic_achievement_dragons_hoard),
        Achievement("Task Master", "Complete 50 Tasks", profile.lifetimeTasksCompleted >= 50, BadgeType.TASKS, Color(0xFF651FFF), com.focusbyrj.app.R.drawable.ic_achievement_deep_work_sentinel),
        Achievement("Unstoppable Finisher", "Complete 100 Tasks", profile.lifetimeTasksCompleted >= 100, BadgeType.TASKS, Color(0xFFFFD700), com.focusbyrj.app.R.drawable.ic_achievement_unshatterable),
        
        Achievement("Consistency", "3-Day Streak", profile.longestStreak >= 3, BadgeType.STREAK, Color(0xFFFF9800), com.focusbyrj.app.R.drawable.ic_achievement_consistency),
        Achievement("Dedication", "7-Day Streak", profile.longestStreak >= 7, BadgeType.STREAK, Color(0xFFFF5722), com.focusbyrj.app.R.drawable.ic_achievement_dedication),
        Achievement("Unbreakable", "30-Day Streak", profile.longestStreak >= 30, BadgeType.STREAK, Color(0xFFF44336), com.focusbyrj.app.R.drawable.ic_achievement_unbreakable),
        Achievement("Habit Builder", "60-Day Streak", profile.longestStreak >= 60, BadgeType.STREAK, Color(0xFFF44336), com.focusbyrj.app.R.drawable.ic_achievement_habit_builder),
        Achievement("Century Club", "100-Day Streak", profile.longestStreak >= 100, BadgeType.STREAK, Color(0xFFF44336), com.focusbyrj.app.R.drawable.ic_achievement_century_club),
        Achievement("Year of Focus", "365-Day Streak", profile.longestStreak >= 365, BadgeType.STREAK, Color(0xFFF44336), com.focusbyrj.app.R.drawable.ic_achievement_year_of_focus),
        
        Achievement("Piggy Bank", "100 Gold", profile.gold >= 100, BadgeType.GOLD, Color(0xFFFFC107), com.focusbyrj.app.R.drawable.ic_achievement_piggy_bank),
        Achievement("Savings", "1,000 Gold", profile.gold >= 1000, BadgeType.GOLD, Color(0xFFFFC107), com.focusbyrj.app.R.drawable.ic_achievement_savings),
        Achievement("Wealthy", "5,000 Gold", profile.gold >= 5000, BadgeType.GOLD, Color(0xFFFFB300), com.focusbyrj.app.R.drawable.ic_achievement_wealthy),
        Achievement("Hoarder", "10,000 Gold", profile.gold >= 10000, BadgeType.GOLD, Color(0xFFFFA000), com.focusbyrj.app.R.drawable.ic_achievement_hoarder),
        Achievement("Midas Touch", "50,000 Gold", profile.gold >= 50000, BadgeType.GOLD, Color(0xFFFFA000), com.focusbyrj.app.R.drawable.ic_achievement_midas_touch),
        Achievement("Treasury", "100,000 Gold", profile.gold >= 100000, BadgeType.GOLD, Color(0xFFFFA000), com.focusbyrj.app.R.drawable.ic_achievement_treasury),
        
        Achievement("Getting Started", "1 Hour Focused", profile.lifetimeFocusMins >= 60, BadgeType.FOCUS, Color(0xFF00B0FF), com.focusbyrj.app.R.drawable.ic_achievement_getting_started),
        Achievement("Flow State", "10 Hours Focused", profile.lifetimeFocusMins >= 600, BadgeType.FOCUS, Color(0xFF00B0FF), com.focusbyrj.app.R.drawable.ic_achievement_flow_state),
        Achievement("Zone In", "50 Hours Focused", profile.lifetimeFocusMins >= 3000, BadgeType.FOCUS, Color(0xFF651FFF), com.focusbyrj.app.R.drawable.ic_achievement_zone_in),
        Achievement("Monk Mode", "500 Hours Focused", profile.lifetimeFocusMins >= 30000, BadgeType.FOCUS, Color(0xFFD500F9), com.focusbyrj.app.R.drawable.ic_achievement_monk_mode),
        Achievement("Time Lord", "1000 Hours Focused", profile.lifetimeFocusMins >= 60000, BadgeType.FOCUS, Color(0xFFFF1744), com.focusbyrj.app.R.drawable.ic_achievement_time_lord),
        Achievement("Master of Time", "2,000 Hours Focused", profile.lifetimeFocusMins >= 120000, BadgeType.FOCUS, Color(0xFFFF1744), com.focusbyrj.app.R.drawable.ic_achievement_master_of_time),

        Achievement("First Temptation", "Resist 1 App", profile.lifetimeResists >= 1, BadgeType.RESIST, Color(0xFF4CAF50), com.focusbyrj.app.R.drawable.ic_achievement_first_temptation),
        Achievement("Iron Will", "Resist 10 Apps", profile.lifetimeResists >= 10, BadgeType.RESIST, Color(0xFF4CAF50), com.focusbyrj.app.R.drawable.ic_achievement_iron_will),
        Achievement("Willpower", "Resist 50 Apps", profile.lifetimeResists >= 50, BadgeType.RESIST, Color(0xFF009688), com.focusbyrj.app.R.drawable.ic_achievement_willpower),
        Achievement("Dopamine Detox", "Resist 100 Apps", profile.lifetimeResists >= 100, BadgeType.RESIST, Color(0xFF009688), com.focusbyrj.app.R.drawable.ic_achievement_dopamine_detox),
        Achievement("Zen Mind", "Resist 1,000 Apps", profile.lifetimeResists >= 1000, BadgeType.RESIST, Color(0xFF00BCD4), com.focusbyrj.app.R.drawable.ic_achievement_zen_mind),
        
        Achievement("Scholar", "Unlock Scholar", profile.avatarTier >= 1, BadgeType.TIER, Color(0xFF4CAF50), com.focusbyrj.app.R.drawable.ic_achievement_scholar),
        Achievement("Knight", "Unlock Knight", profile.avatarTier >= 2, BadgeType.TIER, Color(0xFF9C27B0), com.focusbyrj.app.R.drawable.ic_achievement_knight),
        Achievement("Noble", "Unlock Noble", profile.avatarTier >= 3, BadgeType.TIER, Color(0xFF2196F3), com.focusbyrj.app.R.drawable.ic_achievement_noble),
        Achievement("Emperor", "Unlock Emperor", profile.avatarTier >= 4, BadgeType.TIER, Color(0xFFFFD700), com.focusbyrj.app.R.drawable.ic_achievement_emperor)
    )
}


@Composable
fun MedievalMedal(iconRes: Int, color: Color, isUnlocked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isUnlocked) {
            // Ambient colored glow around the badge
            Box(
                modifier = Modifier
                    .fillMaxSize(0.95f)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = 0.45f),
                                color.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Direct contrast pod behind cutout icons
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = color.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Locked badge pod
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = iconRes),
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
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Locked",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AchievementsTab(profile: UserProfile, stats: com.focusbyrj.app.util.FocusStats, isPro: Boolean) {
    val achievements = getAchievements(profile, stats, isPro)
    val context = LocalContext.current
    
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3), 
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(
            achievements,
            key = { it.title },
            contentType = { "Achievement" }
        ) { achievement ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        android.widget.Toast.makeText(context, "${achievement.title}: ${achievement.description}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MedievalMedal(
                    iconRes = achievement.iconRes,
                    color = achievement.color,
                    isUnlocked = achievement.isUnlocked,
                    modifier = Modifier.size(62.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (achievement.isUnlocked) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun AvatarSelectionSheet(profile: UserProfile) {
    val context = LocalContext.current
    var avatarToPurchase by remember { mutableStateOf<ProfileAvatar?>(null) }
    
    val tierAvatars = ProfileAvatarManager.tierAvatars
    val storeAvatars = ProfileAvatarManager.storeAvatars
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 48.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // VIP Tiers Section
        Text("VIP Milestones", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Text("Unlocked as your Discipline & Gold milestone grows.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.heightIn(max = 280.dp)
        ) {
            items(
                tierAvatars,
                key = { it.id },
                contentType = { "TierAvatar" }
            ) { avatar ->
                val isUnlocked = avatar.tier <= profile.avatarTier || profile.avatarTier == 5
                val isCurrent = profile.selectedAvatar == avatar.id || (profile.selectedAvatar == "tier_1" && avatar.id == "tier_1")
                
                Box(
                    modifier = Modifier
                        .aspectRatio(0.85f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isUnlocked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(
                            2.dp, 
                            if (isCurrent) avatar.borderColor else if (isUnlocked) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else Color.Transparent, 
                            RoundedCornerShape(16.dp)
                        )
                        .clickable(enabled = isUnlocked) {
                            FocusEconomyManager.equipAvatar(avatar.id)
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(2.dp, if (isUnlocked) avatar.borderColor else MaterialTheme.colorScheme.outline, CircleShape),
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = avatar.title,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (!isUnlocked) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(avatar.requiredGold.toString(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else if (isCurrent) {
                            Text("Equipped", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = avatar.borderColor)
                        } else {
                            Text("Equip", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(28.dp))
        
        // Store Avatars Section
        Text("Store Avatars", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Text("Purchase & unlock unique visual identities with earned Gold.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.heightIn(max = 420.dp)
        ) {
            items(
                storeAvatars,
                key = { it.id },
                contentType = { "StoreAvatar" }
            ) { storeAvatar ->
                val isPurchased = profile.purchasedAvatars.contains(storeAvatar.id)
                val isCurrent = profile.selectedAvatar == storeAvatar.id
                val canAfford = profile.gold >= storeAvatar.cost
                
                Box(
                    modifier = Modifier
                        .aspectRatio(0.85f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isPurchased) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(
                            2.dp, 
                            if (isCurrent) storeAvatar.borderColor else if (isPurchased) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else Color.Transparent, 
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            if (isPurchased) {
                                FocusEconomyManager.equipAvatar(storeAvatar.id)
                            } else if (canAfford) {
                                avatarToPurchase = storeAvatar
                            } else {
                                android.widget.Toast.makeText(context, "Need ${storeAvatar.cost} Gold to unlock", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(2.dp, if (isPurchased || canAfford) storeAvatar.borderColor else MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = storeAvatar.imageRes),
                                contentDescription = storeAvatar.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .alpha(if (isPurchased || canAfford) 1f else 0.45f)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = storeAvatar.title,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = if (isPurchased || canAfford) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (!isPurchased) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.MonetizationOn, 
                                    contentDescription = null, 
                                    tint = if (canAfford) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant, 
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    "${storeAvatar.cost}", 
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), 
                                    color = if (canAfford) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (isCurrent) {
                            Text("Equipped", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = storeAvatar.borderColor)
                        } else {
                            Text("Equip", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = storeAvatar.borderColor.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }

    avatarToPurchase?.let { avatar ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { avatarToPurchase = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(2.dp, avatar.borderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = avatar.imageRes),
                            contentDescription = avatar.title,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Unlock ${avatar.title}?")
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to purchase the ${avatar.title} avatar? This will cost ${avatar.cost} Gold.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        FocusEconomyManager.purchaseAvatar(avatar.id, avatar.cost)
                        android.widget.Toast.makeText(context, "Purchased ${avatar.title}!", android.widget.Toast.LENGTH_SHORT).show()
                        avatarToPurchase = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Purchase", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { avatarToPurchase = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

fun getTierIcon(tier: Int): ImageVector {
    return when (tier) {
        5 -> Icons.Filled.Star 
        4 -> Icons.Filled.MonetizationOn 
        3 -> Icons.Filled.Shield 
        2 -> Icons.Filled.Person 
        else -> Icons.Filled.Person 
    }
}

fun getTierColor(tier: Int): Color {
    return ProfileAvatarManager.getAvatarBorderColor("tier_$tier", tier)
}

fun getTierTitle(tier: Int): String {
    return ProfileAvatarManager.getAvatarTitle("tier_$tier", tier)
}

fun getAvatarIcon(selectedAvatar: String, tier: Int): ImageVector {
    return getTierIcon(tier)
}

fun getAvatarColor(selectedAvatar: String, tier: Int): Color {
    return ProfileAvatarManager.getAvatarBorderColor(selectedAvatar, tier)
}

fun getAvatarTitle(selectedAvatar: String, tier: Int): String {
    return ProfileAvatarManager.getAvatarTitle(selectedAvatar, tier)
}

