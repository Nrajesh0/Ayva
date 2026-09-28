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

package com.focusbyrj.app.data.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_drill_state")
data class ActiveDrillStateEntity(
    @PrimaryKey val sessionId: String,
    val difficulty: String,
    val targetQuestions: Int = -1,
    val correct: Int = 0,
    val total: Int = 0,
    val xp: Int = 0,
    val gold: Int = 0,
    val combo: Int = 0,
    val maxCombo: Int = 0,
    val isBlitz: Boolean = false,
    val blitzSecondsRemaining: Int = 60,
    val startTime: Long = System.currentTimeMillis(),
    val preGeneratedQuestionsJson: String = "[]",
    val questionRecordsJson: String = "[]",
    val attemptedIndicesJson: String = "[]",
    val markedForReviewJson: String = "[]",
    val highestSeenIndex: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
