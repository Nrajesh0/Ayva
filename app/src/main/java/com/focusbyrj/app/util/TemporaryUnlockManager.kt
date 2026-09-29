/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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
        val expiryTime = prefs.getSafeLong("unlock_$packageName", 0L)
        return System.currentTimeMillis() < expiryTime
    }

    fun revokeUnlock(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("unlock_$packageName").apply()
    }
}
