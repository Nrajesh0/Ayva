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
        val safeMinutes = minutes.coerceIn(1, 1440)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val expiryTime = System.currentTimeMillis() + (safeMinutes * 60 * 1000L)
        prefs.edit().putLong("unlock_$packageName", expiryTime).commit()
    }

    fun isUnlocked(context: Context, packageName: String): Boolean {
        if (packageName.isBlank() || packageName == "Unknown") return false
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val expiryTime = prefs.getSafeLong("unlock_$packageName", 0L)
        return System.currentTimeMillis() < expiryTime
    }

    fun revokeUnlock(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("unlock_$packageName").commit()
    }

    fun pruneExpiredUnlocks(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val editor = prefs.edit()
        var changed = false
        for ((key, value) in prefs.all) {
            if (key.startsWith("unlock_") && value is Long && value < now) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) {
            editor.commit()
        }
    }
}
