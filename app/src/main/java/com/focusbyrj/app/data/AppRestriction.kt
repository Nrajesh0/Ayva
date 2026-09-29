/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_restrictions")
data class AppRestriction(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isRestricted: Boolean = false,
    val mode: String = "HARD",
    val restrictionMode: String = "SIMPLE",
    val timeLimitMinutes: Int = 0,
    val clickLimitCount: Int = 0,
    val customQuote: String = "Is this urgent, or are you chasing cheap dopamine?"
) {
    @androidx.room.Ignore
    var isFromRoutine: Boolean = false
}
