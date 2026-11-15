/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.account

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.focusbyrj.app.util.*
import java.util.*

import com.focusbyrj.app.ui.screens.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditProfileModal(
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
internal fun AvatarStoreModal(
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
internal fun ShareProfileCardModal(
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
