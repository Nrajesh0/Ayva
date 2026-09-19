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
