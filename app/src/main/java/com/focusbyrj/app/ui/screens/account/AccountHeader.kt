/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.R
import com.focusbyrj.app.util.*
import java.util.*

import com.focusbyrj.app.ui.screens.*

@Composable
internal fun EnclaveTopBar(
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

// 2. IDENTITY HERO CARD COMPONENT

@Composable
internal fun EnclaveIdentityHeroCard(
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

// 3. QUICK STATS ROW: [GOLD] [LEVEL] [VAULT]

@Composable
internal fun EnclaveQuickStatsRow(
    colors: EnclaveColors,
    gold: Int,
    level: Int
) {
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
            icon = Icons.Filled.MilitaryTech,
            iconTint = colors.accentCyan
        )

        EnclaveStatCard(
            colors = colors,
            modifier = Modifier.weight(1f),
            value = "LOCAL",
            label = "VAULT",
            icon = Icons.Filled.Lock,
            iconTint = colors.accentGreen
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
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val valueFontSize = when {
                        value.any { it.isLetter() } && value.length >= 5 -> 13.sp
                        value.any { it.isLetter() } -> 14.sp
                        value.length >= 5 -> 14.sp
                        else -> 16.sp
                    }
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = valueFontSize
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.textPrimary
                    )
                    if (unit != null) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = colors.textMuted,
                            modifier = Modifier.padding(bottom = 1.5.dp)
                        )
                    }
                }
                if (icon != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                ),
                color = colors.textMuted
            )
        }
    }
}

// 4. NAVIGATION TABS ROW: Overview | Achievements

@Composable
internal fun EnclaveTabsRow(
    colors: EnclaveColors,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("Overview", "Achievements")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Faded baseline track separating top tabs from bottom content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            colors.textMuted.copy(alpha = 0.35f),
                            colors.textMuted.copy(alpha = 0.25f),
                            colors.textMuted.copy(alpha = 0.12f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            tabs.forEachIndexed { index, tabTitle ->
                val isSelected = selectedTabIndex == index
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .clickable { onTabSelected(index) }
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tabTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.5.sp
                        ),
                        color = if (isSelected) colors.textPrimary else colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Active solid underline indicator connecting directly onto the baseline
                    Box(
                        modifier = Modifier
                            .width(if (isSelected) 48.dp else 0.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(if (isSelected) colors.textPrimary else Color.Transparent)
                    )
                }
            }
        }
    }
}

// 5. OVERVIEW TAB COMPLETE SCROLLING LAYOUT
