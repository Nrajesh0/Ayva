/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.focusbyrj.app.ui.components.SettingsMenuRow
import com.focusbyrj.app.util.*
import java.util.*

import com.focusbyrj.app.ui.screens.*

@Composable
internal fun EnclaveSystemPreferencesSection(
    colors: EnclaveColors,
    onFocusSettingsClick: () -> Unit,
    themeMode: ThemeMode,
    onAppearanceClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onProLicenseClick: () -> Unit
) {
    Text("SETTINGS SHORTCUTS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textMuted)
    Spacer(Modifier.height(10.dp))
    Surface(shape = RoundedCornerShape(18.dp), color = colors.cardBg, border = BorderStroke(1.dp, colors.cardBorder)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            SettingsMenuRow(Icons.Default.Palette, "Appearance", onClick = onAppearanceClick) {
                Text(themeMode.displayName, fontSize = 12.sp, color = colors.textMuted)
            }
            SettingsMenuRow(Icons.Default.Lock, "Focus & behavior", onClick = onFocusSettingsClick) {
                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
            }
            SettingsMenuRow(Icons.Default.Security, "Security & permissions", onClick = onSecurityClick) {
                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
            }
            SettingsMenuRow(Icons.Default.Star, "Pro license", onClick = onProLicenseClick) {
                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
            }
        }
    }
}

// 6. ACHIEVEMENTS TAB (TAB 1)

@Composable
internal fun EnclaveOverflowMenu(
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
                EnclaveMenuItem(colors = colors, icon = Icons.Filled.Settings, label = "Settings", onClick = onPreferencesHub)
                EnclaveMenuItem(colors = colors, icon = Icons.Filled.Security, label = "Security & permissions", onClick = onSecurityVaults)
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
internal fun EnclaveRulesDialog(colors: EnclaveColors, onDismiss: () -> Unit) {
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
internal fun EnclaveSystemIdentityDialog(
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
internal fun EnclaveStreakDialog(
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

// 9. UTILITY HELPERS & REUSABLE BADGES
