/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.drill

object DrillTopBarHelper {
    /**
     * Formats remaining blitz seconds into MM:SS or Xs, safely coercing negative seconds to 0.
     */
    fun formatBlitzTimer(blitzSecondsRemaining: Int): String {
        val safeSeconds = blitzSecondsRemaining.coerceAtLeast(0)
        val mins = safeSeconds / 60
        val secs = safeSeconds % 60
        return if (mins > 0) String.format("%d:%02d", mins, secs) else "${secs}s"
    }

    /**
     * Calculates blitz elapsed progress fraction between 0.03f and 1f.
     */
    fun calculateBlitzProgress(blitzSecondsRemaining: Int, totalTime: Float = 300f): Float {
        val safeRemaining = blitzSecondsRemaining.coerceAtLeast(0)
        val elapsedSeconds = (totalTime.toInt() - safeRemaining).coerceAtLeast(0)
        return (elapsedSeconds.toFloat() / totalTime).coerceIn(0.03f, 1f)
    }
}
