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
            // Self-heal: repair the preference type in-place
            this.edit().putInt(key, longVal.toInt()).apply()
            longVal.toInt()
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
            val strVal = this.getString(key, null)
            val parsed = strVal?.toBooleanStrictOrNull() ?: defValue
            this.edit().putBoolean(key, parsed).apply()
            parsed
        } catch (_: Exception) {
            defValue
        }
    }
}
