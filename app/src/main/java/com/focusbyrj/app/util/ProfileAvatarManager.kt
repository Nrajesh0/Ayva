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

package com.focusbyrj.app.util

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.focusbyrj.app.R

enum class AvatarCategory(val displayName: String) {
    VIP_TIERS("VIP Milestones"),
    STORE("Store Avatars"),
    SPECIAL("Ascendant Avatars")
}

data class ProfileAvatar(
    val id: String,
    val title: String,
    val subtitle: String,
    @DrawableRes val imageRes: Int,
    val borderColor: Color,
    val category: AvatarCategory,
    val tier: Int = 1,
    val cost: Int = 0,
    val requiredGold: Int = 0,
    val description: String = ""
)

object ProfileAvatarManager {
    val tierAvatars = listOf(
        ProfileAvatar(
            id = "tier_1",
            title = "The Wanderer",
            subtitle = "Tier 1 • Explorer",
            imageRes = R.drawable.avatar_wanderer,
            borderColor = Color(0xFF9E9E9E),
            category = AvatarCategory.VIP_TIERS,
            tier = 1,
            requiredGold = 0,
            description = "The beginning of your disciplined journey."
        ),
        ProfileAvatar(
            id = "tier_2",
            title = "The Scholar",
            subtitle = "Tier 2 • Knowledge",
            imageRes = R.drawable.avatar_scholar,
            borderColor = Color(0xFF10B981),
            category = AvatarCategory.VIP_TIERS,
            tier = 2,
            requiredGold = 500,
            description = "Possesses deep focus and mental clarity."
        ),
        ProfileAvatar(
            id = "tier_3",
            title = "The Knight",
            subtitle = "Tier 3 • Iron Will",
            imageRes = R.drawable.avatar_knight,
            borderColor = Color(0xFF94A3B8),
            category = AvatarCategory.VIP_TIERS,
            tier = 3,
            requiredGold = 2000,
            description = "Shielded against distractions and temptations."
        ),
        ProfileAvatar(
            id = "tier_4",
            title = "The Noble",
            subtitle = "Tier 4 • High Prestige",
            imageRes = R.drawable.avatar_noble,
            borderColor = Color(0xFF00E5FF),
            category = AvatarCategory.VIP_TIERS,
            tier = 4,
            requiredGold = 10000,
            description = "A revered figure of unyielding productivity."
        ),
        ProfileAvatar(
            id = "tier_5",
            title = "The Emperor",
            subtitle = "Tier 5 • Sovereign",
            imageRes = R.drawable.avatar_emperor,
            borderColor = Color(0xFFFFD700),
            category = AvatarCategory.VIP_TIERS,
            tier = 5,
            requiredGold = 50000,
            description = "The supreme master of attention and focus."
        )
    )

    val storeAvatars = listOf(
        ProfileAvatar(
            id = "store_companion",
            title = "Companion",
            subtitle = "Warmth & Care",
            imageRes = R.drawable.avatar_companion,
            borderColor = Color(0xFFE91E63),
            category = AvatarCategory.STORE,
            cost = 1000,
            description = "A warm, steadfast heart protecting your attention."
        ),
        ProfileAvatar(
            id = "store_inferno",
            title = "Inferno",
            subtitle = "Blazing Will",
            imageRes = R.drawable.avatar_inferno,
            borderColor = Color(0xFFFF5722),
            category = AvatarCategory.STORE,
            cost = 2500,
            description = "An unstoppable fire burning through procrastination."
        ),
        ProfileAvatar(
            id = "store_champion",
            title = "Champion",
            subtitle = "Trophy Winner",
            imageRes = R.drawable.avatar_champion,
            borderColor = Color(0xFFFFD700),
            category = AvatarCategory.STORE,
            cost = 5000,
            description = "The standard of elite time management."
        ),
        ProfileAvatar(
            id = "store_prestige",
            title = "Prestige",
            subtitle = "Elite Honors",
            imageRes = R.drawable.avatar_prestige,
            borderColor = Color(0xFFE040FB),
            category = AvatarCategory.STORE,
            cost = 10000,
            description = "Awarded for exceptional milestones and perseverance."
        ),
        ProfileAvatar(
            id = "store_crown",
            title = "Crown",
            subtitle = "Imperial Gem",
            imageRes = R.drawable.avatar_crown,
            borderColor = Color(0xFFF59E0B),
            category = AvatarCategory.STORE,
            cost = 25000,
            description = "The royal emblem of uninterrupted focus."
        ),
        ProfileAvatar(
            id = "store_phoenix",
            title = "Phoenix",
            subtitle = "Eternal Rebirth",
            imageRes = R.drawable.avatar_phoenix,
            borderColor = Color(0xFFEF4444),
            category = AvatarCategory.STORE,
            cost = 35000,
            description = "Rising stronger after every broken habit."
        )
    )

    val allAvatars: List<ProfileAvatar> = tierAvatars + storeAvatars

    fun getAvatar(avatarId: String, fallbackTier: Int = 1): ProfileAvatar {
        allAvatars.find { it.id == avatarId }?.let { return it }
        if (avatarId.startsWith("tier_")) {
            val t = avatarId.removePrefix("tier_").toIntOrNull() ?: fallbackTier
            tierAvatars.find { it.tier == t }?.let { return it }
        }
        return tierAvatars.find { it.tier == fallbackTier } ?: tierAvatars.first()
    }

    fun getAvatarImageRes(avatarId: String, fallbackTier: Int = 1): Int {
        return getAvatar(avatarId, fallbackTier).imageRes
    }

    fun getAvatarBorderColor(avatarId: String, fallbackTier: Int = 1): Color {
        return getAvatar(avatarId, fallbackTier).borderColor
    }

    fun getAvatarTitle(avatarId: String, fallbackTier: Int = 1): String {
        return getAvatar(avatarId, fallbackTier).title
    }
}
