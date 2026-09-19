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

package com.focusbyrj.app.util

import android.content.Context

object TemporaryUnlockManager {
    private const val PREF_NAME = "temporary_unlock_prefs"

    fun grantUnlock(context: Context, packageName: String, minutes: Int = 5) {
        if (packageName.isBlank() || packageName == "Unknown") return
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val expiryTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
        prefs.edit().putLong("unlock_$packageName", expiryTime).apply()
    }

    fun isUnlocked(context: Context, packageName: String): Boolean {
        if (packageName.isBlank() || packageName == "Unknown") return false
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val expiryTime = prefs.getLong("unlock_$packageName", 0L)
        return System.currentTimeMillis() < expiryTime
    }

    fun revokeUnlock(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("unlock_$packageName").apply()
    }
}
