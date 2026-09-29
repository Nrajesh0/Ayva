/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.util

import android.content.SharedPreferences

/**
 * Robust SharedPreferences reading extensions with in-place self-healing.
 * Protects against ClassCastException caused by JSON serialization or external restore
 * where numbers may have been stored as Int instead of Long, or Float instead of Double/Int.
 */
fun SharedPreferences.getSafeLong(key: String, defValue: Long): Long {
    return try {
        this.getLong(key, defValue)
    } catch (_: ClassCastException) {
        try {
            val intVal = this.getInt(key, defValue.toInt())
            // Self-heal: repair the preference type in-place
            this.edit().putLong(key, intVal.toLong()).apply()
            intVal.toLong()
        } catch (_: Exception) {
            try {
                val strVal = this.getString(key, null)
                val parsed = strVal?.toLongOrNull() ?: defValue
                this.edit().putLong(key, parsed).apply()
                parsed
            } catch (_: Exception) {
                defValue
            }
        }
    }
}

fun SharedPreferences.getSafeInt(key: String, defValue: Int): Int {
    return try {
        this.getInt(key, defValue)
    } catch (_: ClassCastException) {
        try {
            val longVal = this.getLong(key, defValue.toLong())
            if (longVal in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                val intVal = longVal.toInt()
                // Self-heal: repair the preference type in-place
                this.edit().putInt(key, intVal).apply()
                intVal
            } else {
                defValue
            }
        } catch (_: Exception) {
            try {
                val strVal = this.getString(key, null)
                val parsed = strVal?.toIntOrNull() ?: defValue
                this.edit().putInt(key, parsed).apply()
                parsed
            } catch (_: Exception) {
                defValue
            }
        }
    }
}

fun SharedPreferences.getSafeFloat(key: String, defValue: Float): Float {
    return try {
        this.getFloat(key, defValue)
    } catch (_: ClassCastException) {
        try {
            val intVal = this.getInt(key, defValue.toInt())
            // Self-heal: repair the preference type in-place
            this.edit().putFloat(key, intVal.toFloat()).apply()
            intVal.toFloat()
        } catch (_: Exception) {
            try {
                val longVal = this.getLong(key, defValue.toLong())
                this.edit().putFloat(key, longVal.toFloat()).apply()
                longVal.toFloat()
            } catch (_: Exception) {
                try {
                    val strVal = this.getString(key, null)
                    val parsed = strVal?.toFloatOrNull() ?: defValue
                    this.edit().putFloat(key, parsed).apply()
                    parsed
                } catch (_: Exception) {
                    defValue
                }
            }
        }
    }
}

fun SharedPreferences.getSafeBoolean(key: String, defValue: Boolean): Boolean {
    return try {
        this.getBoolean(key, defValue)
    } catch (_: ClassCastException) {
        try {
            val intVal = this.getInt(key, if (defValue) 1 else 0)
            val parsed = intVal != 0
            this.edit().putBoolean(key, parsed).apply()
            parsed
        } catch (_: Exception) {
            try {
                val longVal = this.getLong(key, if (defValue) 1L else 0L)
                val parsed = longVal != 0L
                this.edit().putBoolean(key, parsed).apply()
                parsed
            } catch (_: Exception) {
                try {
                    val strVal = this.getString(key, null)?.trim()?.lowercase()
                    val parsed = when (strVal) {
                        "true", "1", "yes", "on" -> true
                        "false", "0", "no", "off" -> false
                        else -> defValue
                    }
                    this.edit().putBoolean(key, parsed).apply()
                    parsed
                } catch (_: Exception) {
                    defValue
                }
            }
        }
    }
}
