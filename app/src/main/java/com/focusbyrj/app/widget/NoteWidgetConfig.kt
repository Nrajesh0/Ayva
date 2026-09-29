/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.widget

import android.content.Context
import android.graphics.Color

enum class NoteWidgetFilterMode(val displayName: String, val shortLabel: String) {
    ALL("All Notes", "All"),
    NOTES("Notes Only", "Notes"),
    CHECKLISTS("Checklists", "Lists"),
    PINNED("Pinned", "Pin"),
    SPECIFIC("Single Note", "Lock");

    fun next(): NoteWidgetFilterMode {
        return when (this) {
            ALL -> NOTES
            NOTES -> CHECKLISTS
            CHECKLISTS -> PINNED
            PINNED -> ALL
            SPECIFIC -> ALL
        }
    }
}

enum class NoteWidgetSortBy(val displayName: String) {
    RECENTLY_UPDATED("Recently Updated"),
    RECENTLY_CREATED("Date Created"),
    PINNED_FIRST("Pinned First"),
    ALPHABETICAL("Alphabetical (A-Z)")
}

enum class NoteWidgetTextSize(val spValue: Float, val displayName: String) {
    SIZE_12(12f, "12 sp"),
    SIZE_14(14f, "14 sp"),
    SIZE_16(16f, "16 sp"),
    SIZE_18(18f, "18 sp"),
    SIZE_20(20f, "20 sp"),
    SIZE_22(22f, "22 sp"),
    SIZE_24(24f, "24 sp"),
    SIZE_26(26f, "26 sp"),
    SIZE_28(28f, "28 sp"),
    SIZE_30(30f, "30 sp"),
    SIZE_32(32f, "32 sp"),
    SIZE_34(34f, "34 sp"),
    SIZE_36(36f, "36 sp");

    companion object {
        fun fromNameOrDefault(name: String?): NoteWidgetTextSize {
            if (name == null) return SIZE_16
            return try {
                NoteWidgetTextSize.valueOf(name)
            } catch (_: Exception) {
                when (name) {
                    "TINY" -> SIZE_14
                    "COMPACT" -> SIZE_16
                    "STANDARD" -> SIZE_18
                    "LARGE" -> SIZE_22
                    "EXTRA_LARGE" -> SIZE_26
                    else -> SIZE_16
                }
            }
        }
    }
}

enum class NoteWidgetPadding(val dpValue: Int, val displayName: String) {
    TIGHT(6, "Tight"),
    COMPACT(10, "Compact"),
    STANDARD(14, "Standard"),
    SPACIOUS(18, "Spacious")
}

data class NoteWidgetConfig(
    val theme: WidgetTheme = WidgetTheme.DARK,
    val accent: WidgetAccent = WidgetAccent.BLUE,
    val opacityPercent: Int = 95,
    val cornerRadiusDp: Int = 16,
    val matchNoteColor: Boolean = true,
    val filterMode: NoteWidgetFilterMode = NoteWidgetFilterMode.ALL,
    val specificNoteId: Long? = null,
    val sortBy: NoteWidgetSortBy = NoteWidgetSortBy.RECENTLY_UPDATED,
    val textSize: NoteWidgetTextSize = NoteWidgetTextSize.SIZE_16,
    val padding: NoteWidgetPadding = NoteWidgetPadding.STANDARD,
    val showTitle: Boolean = true,
    val showQuickAddBar: Boolean = true,
    val showActionButtons: Boolean = true,
    val showNavHeader: Boolean = true,
    val adaptiveLayout: Boolean = true
) {
    val backgroundColorInt: Int
        get() {
            val base = Color.parseColor(theme.baseColorHex)
            val alpha = ((opacityPercent / 100f) * 255).toInt().coerceIn(0, 255)
            return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
        }

    val accentColorInt: Int
        get() = Color.parseColor(accent.hex)

    val primaryTextColorInt: Int
        get() = if (theme.isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#121516")

    val secondaryTextColorInt: Int
        get() = if (theme.isDark) Color.parseColor("#9AA0A6") else Color.parseColor("#6B7280")
}

object NoteWidgetConfigHelper {
    private const val PREFS_NAME = "note_widget_prefs"
    private const val KEY_THEME = "theme_"
    private const val KEY_ACCENT = "accent_"
    private const val KEY_OPACITY = "opacity_"
    private const val KEY_CORNER = "corner_"
    private const val KEY_MATCH_NOTE = "match_note_color_"
    private const val KEY_FILTER_MODE_PREFIX = "note_filter_mode_"
    private const val KEY_SPECIFIC_ID_PREFIX = "note_specific_id_"
    private const val KEY_INDEX_PREFIX = "note_index_"
    private const val KEY_SORT_BY = "sort_by_"
    private const val KEY_TEXT_SIZE = "text_size_"
    private const val KEY_PADDING = "padding_"
    private const val KEY_SHOW_TITLE = "show_title_"
    private const val KEY_SHOW_QUICK_ADD = "show_quick_add_"
    private const val KEY_SHOW_ACTION_BUTTONS = "show_action_buttons_"
    private const val KEY_SHOW_NAV_HEADER = "show_nav_header_"
    private const val KEY_ADAPTIVE_LAYOUT = "adaptive_layout_"
    private const val KEY_DEFAULT_SUFFIX = "default"

    fun getConfig(context: Context, appWidgetId: Int): NoteWidgetConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun getStringVal(prefix: String, defaultVal: String): String {
            if (appWidgetId > 0 && prefs.contains(prefix + appWidgetId)) {
                return prefs.getString(prefix + appWidgetId, defaultVal) ?: defaultVal
            }
            return prefs.getString(prefix + KEY_DEFAULT_SUFFIX, defaultVal) ?: defaultVal
        }

        fun getBooleanVal(prefix: String, defaultVal: Boolean): Boolean {
            if (appWidgetId > 0 && prefs.contains(prefix + appWidgetId)) {
                return prefs.getBoolean(prefix + appWidgetId, defaultVal)
            }
            return prefs.getBoolean(prefix + KEY_DEFAULT_SUFFIX, defaultVal)
        }

        fun getIntVal(prefix: String, defaultVal: Int): Int {
            if (appWidgetId > 0 && prefs.contains(prefix + appWidgetId)) {
                return prefs.getInt(prefix + appWidgetId, defaultVal)
            }
            return prefs.getInt(prefix + KEY_DEFAULT_SUFFIX, defaultVal)
        }

        val modeStr = getStringVal(KEY_FILTER_MODE_PREFIX, NoteWidgetFilterMode.ALL.name)
        val filterMode = try {
            NoteWidgetFilterMode.valueOf(modeStr)
        } catch (_: Exception) {
            NoteWidgetFilterMode.ALL
        }

        val specificIdRaw = if (appWidgetId > 0 && prefs.contains(KEY_SPECIFIC_ID_PREFIX + appWidgetId)) {
            prefs.getLong(KEY_SPECIFIC_ID_PREFIX + appWidgetId, -1L)
        } else {
            prefs.getLong(KEY_SPECIFIC_ID_PREFIX + KEY_DEFAULT_SUFFIX, -1L)
        }
        val specificNoteId = if (specificIdRaw != -1L) specificIdRaw else null

        val themeName = getStringVal(KEY_THEME, WidgetTheme.DARK.name)
        val accentName = getStringVal(KEY_ACCENT, WidgetAccent.BLUE.name)
        val opacity = getIntVal(KEY_OPACITY, 95)
        val corner = getIntVal(KEY_CORNER, 16)
        val matchNote = getBooleanVal(KEY_MATCH_NOTE, true)

        val sortStr = getStringVal(KEY_SORT_BY, NoteWidgetSortBy.RECENTLY_UPDATED.name)
        val sortBy = runCatching { NoteWidgetSortBy.valueOf(sortStr) }.getOrDefault(NoteWidgetSortBy.RECENTLY_UPDATED)

        val textStr = getStringVal(KEY_TEXT_SIZE, NoteWidgetTextSize.SIZE_16.name)
        val textSize = NoteWidgetTextSize.fromNameOrDefault(textStr)

        val padStr = getStringVal(KEY_PADDING, NoteWidgetPadding.STANDARD.name)
        val padding = runCatching { NoteWidgetPadding.valueOf(padStr) }.getOrDefault(NoteWidgetPadding.STANDARD)

        val showTitle = getBooleanVal(KEY_SHOW_TITLE, true)
        val showQuickAdd = getBooleanVal(KEY_SHOW_QUICK_ADD, true)
        val showActionButtons = getBooleanVal(KEY_SHOW_ACTION_BUTTONS, true)
        val showNavHeader = getBooleanVal(KEY_SHOW_NAV_HEADER, true)
        val adaptiveLayout = getBooleanVal(KEY_ADAPTIVE_LAYOUT, true)

        val theme = runCatching { WidgetTheme.valueOf(themeName) }.getOrDefault(WidgetTheme.DARK)
        val accent = runCatching { WidgetAccent.valueOf(accentName) }.getOrDefault(WidgetAccent.BLUE)

        return NoteWidgetConfig(
            theme = theme,
            accent = accent,
            opacityPercent = opacity,
            cornerRadiusDp = corner,
            matchNoteColor = matchNote,
            filterMode = filterMode,
            specificNoteId = specificNoteId,
            sortBy = sortBy,
            textSize = textSize,
            padding = padding,
            showTitle = showTitle,
            showQuickAddBar = showQuickAdd,
            showActionButtons = showActionButtons,
            showNavHeader = showNavHeader,
            adaptiveLayout = adaptiveLayout
        )
    }

    fun saveConfig(context: Context, appWidgetId: Int, config: NoteWidgetConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        fun writeForSuffix(suffix: String) {
            editor
                .putString(KEY_THEME + suffix, config.theme.name)
                .putString(KEY_ACCENT + suffix, config.accent.name)
                .putInt(KEY_OPACITY + suffix, config.opacityPercent)
                .putInt(KEY_CORNER + suffix, config.cornerRadiusDp)
                .putBoolean(KEY_MATCH_NOTE + suffix, config.matchNoteColor)
                .putString(KEY_FILTER_MODE_PREFIX + suffix, config.filterMode.name)
                .putLong(KEY_SPECIFIC_ID_PREFIX + suffix, config.specificNoteId ?: -1L)
                .putString(KEY_SORT_BY + suffix, config.sortBy.name)
                .putString(KEY_TEXT_SIZE + suffix, config.textSize.name)
                .putString(KEY_PADDING + suffix, config.padding.name)
                .putBoolean(KEY_SHOW_TITLE + suffix, config.showTitle)
                .putBoolean(KEY_SHOW_QUICK_ADD + suffix, config.showQuickAddBar)
                .putBoolean(KEY_SHOW_ACTION_BUTTONS + suffix, config.showActionButtons)
                .putBoolean(KEY_SHOW_NAV_HEADER + suffix, config.showNavHeader)
                .putBoolean(KEY_ADAPTIVE_LAYOUT + suffix, config.adaptiveLayout)

            if (config.filterMode == NoteWidgetFilterMode.SPECIFIC && config.specificNoteId != null && config.specificNoteId > 0) {
                editor.putLong(KEY_CURRENT_NOTE_ID_PREFIX + suffix, config.specificNoteId)
                editor.putInt(KEY_INDEX_PREFIX + suffix, 0)
            }
        }

        if (appWidgetId > 0) {
            writeForSuffix(appWidgetId.toString())
        } else {
            writeForSuffix(KEY_DEFAULT_SUFFIX)
        }

        editor.apply()
    }

    private const val KEY_CURRENT_NOTE_ID_PREFIX = "note_current_id_"

    fun getCurrentIndex(context: Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_INDEX_PREFIX + appWidgetId, 0)
    }

    fun setCurrentIndex(context: Context, appWidgetId: Int, index: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_INDEX_PREFIX + appWidgetId, index.coerceAtLeast(0)).apply()
    }

    fun getCurrentNoteId(context: Context, appWidgetId: Int): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_CURRENT_NOTE_ID_PREFIX + appWidgetId, -1L)
        return if (id != -1L) id else null
    }

    fun setCurrentNoteId(context: Context, appWidgetId: Int, noteId: Long?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (noteId != null && noteId > 0) {
            prefs.edit().putLong(KEY_CURRENT_NOTE_ID_PREFIX + appWidgetId, noteId).apply()
        } else {
            prefs.edit().remove(KEY_CURRENT_NOTE_ID_PREFIX + appWidgetId).apply()
        }
    }

    fun setFilterMode(context: Context, appWidgetId: Int, mode: NoteWidgetFilterMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_FILTER_MODE_PREFIX + appWidgetId, mode.name)
            .putInt(KEY_INDEX_PREFIX + appWidgetId, 0)
            .remove(KEY_CURRENT_NOTE_ID_PREFIX + appWidgetId)
            .apply()
    }
}
