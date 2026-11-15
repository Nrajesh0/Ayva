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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.data.AppRestriction
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.*
import java.util.*

import com.focusbyrj.app.ui.screens.*

@Composable
internal fun OverviewTabContent(
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
    onClaimRewards: () -> Unit,
    onFocusSettingsClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onProLicenseClick: () -> Unit,
    onManualExport: () -> Unit,
    onRestoreBackup: () -> Unit,
    onLockAppImmediately: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onChoosePinnedNotesClick: () -> Unit,
    onManageAppBlockingClick: () -> Unit,
    onHabitsClick: (() -> Unit)? = null
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

        // ── SECTION 2: DISCIPLINE & SHIELDS ───────────────────────────────────
        EnclaveAppBlockingSection(
            colors = colors,
            restrictions = restrictions,
            dailyResisted = dailyResisted,
            lifetimeResists = profile.lifetimeResists,
            onManageClick = onManageAppBlockingClick,
            onHabitsClick = onHabitsClick
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
            onFocusSettingsClick = onFocusSettingsClick,
            themeMode = themeMode,
            onAppearanceClick = onAppearanceClick,
            onSecurityClick = onSecurityClick,
            onProLicenseClick = onProLicenseClick
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

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onRestoreBackup,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(25.dp),
            border = BorderStroke(1.dp, colors.cardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = colors.textPrimary
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Restore from Encrypted Backup",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = colors.textPrimary
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
                contentDescription = "Lock private notes vault",
                tint = colors.textMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Lock private notes vault",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = colors.textMuted
            )
        }
    }
}

// A. MATRIX // 365D COMPONENT
