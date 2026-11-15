/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.account

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.R
import com.focusbyrj.app.util.*
import java.util.*

import com.focusbyrj.app.ui.screens.*

@Composable
internal fun AchievementsEnclaveTab(
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

// 7. SYSTEM ENCLAVE LOGS (TAB 2)


// 8. INTERACTIVE MODALS & DIALOGS

// A. Edit Profile Modal (Redesigned matching screenshot)
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
