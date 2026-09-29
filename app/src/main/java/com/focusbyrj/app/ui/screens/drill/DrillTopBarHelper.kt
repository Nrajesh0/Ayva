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
