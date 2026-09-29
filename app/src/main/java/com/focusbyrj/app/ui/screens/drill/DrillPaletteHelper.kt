/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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
