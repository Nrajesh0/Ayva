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

object DrillPaletteHelper {
    /**
     * Clamps total questions count to >= 0, preventing Compose LazyVerticalGrid
     * IllegalArgumentException when count < 0.
     */
    fun getSafeCount(totalQuestions: Int): Int {
        return totalQuestions.coerceAtLeast(0)
    }
}
