/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.drill

object FullscreenDrillHelper {
    /**
     * Updates combo count and max combo safely.
     * On correct answer: increments combo, and updates maxCombo if exceeded.
     * On wrong answer: resets combo to 0.
     * Returns Pair(newCombo, newMaxCombo).
     */
    fun updateCombo(currentCombo: Int, currentMaxCombo: Int, isCorrect: Boolean): Pair<Int, Int> {
        val safeCombo = currentCombo.coerceAtLeast(0)
        val safeMax = currentMaxCombo.coerceAtLeast(safeCombo)
        return if (isCorrect) {
            val next = safeCombo + 1
            Pair(next, maxOf(safeMax, next))
        } else {
            Pair(0, safeMax)
        }
    }

    /**
     * Bounds correctIndex within 0 until optionsCount.
     * Returns 0 if optionsCount <= 0.
     */
    fun safeCorrectIndex(correctIndex: Int, optionsCount: Int): Int {
        if (optionsCount <= 0) return 0
        return correctIndex.coerceIn(0, optionsCount - 1)
    }
}
