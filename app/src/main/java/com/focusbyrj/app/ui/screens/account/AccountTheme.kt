/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.focusbyrj.app.util.*
import java.util.*

import com.focusbyrj.app.ui.screens.*

@Composable
internal fun rememberEnclaveColors(): EnclaveColors {
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

internal data class EnclaveColors(
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
