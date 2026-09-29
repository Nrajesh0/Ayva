/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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
