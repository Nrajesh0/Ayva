package com.focusbyrj.app.widget

import android.content.Context
import android.graphics.Color

enum class WidgetTheme(val displayName: String, val baseColorHex: String, val isDark: Boolean) {
    DARK("Dark Minimal", "#121516", true),
    OLED("Pitch Black", "#000000", true),
    GRAPHITE("Graphite Black", "#18181B", true),
    OBSIDIAN("Obsidian Black", "#0A0A0A", true),
    LIGHT("Clean Light", "#FFFFFF", false),
    FROST("Frost White", "#F1F5F9", false),
    PAPER("Paper White", "#FBFBF9", false),
    WARM("Warm Sepia", "#F6F3EE", false),
    IVORY("Ivory White", "#FAFAF7", false)
}

enum class WidgetAccent(val displayName: String, val hex: String) {
    NEON_GREEN("Emerald Green", "#2EE59D"),
    CYAN("Cyan Glow", "#00E5FF"),
    GOLD("Amber Gold", "#FFD166"),
    CORAL("Sunset Coral", "#FF6B6B"),
    PURPLE("Electric Violet", "#A78BFA"),
    BLUE("Sky Blue", "#38BDF8"),
    ROSE("Rose Pink", "#F472B6"),
    MONOCHROME("Clean White", "#E2E8F0")
}

enum class WidgetTextSize(val spValue: Float, val displayName: String) {
    SIZE_12(12f, "12 sp (Small)"),
    SIZE_14(14f, "14 sp (Regular)"),
    SIZE_16(16f, "16 sp (Medium)"),
    SIZE_18(18f, "18 sp (Large)"),
    SIZE_20(20f, "20 sp (Extra Large)"),
    SIZE_22(22f, "22 sp (Huge)"),
    SIZE_24(24f, "24 sp (Larger)"),
    SIZE_26(26f, "26 sp (Max)");

    companion object {
        fun fromNameOrDefault(name: String?): WidgetTextSize {
            if (name == null) return SIZE_14
            return try {
                WidgetTextSize.valueOf(name)
            } catch (_: Exception) {
                when (name) {
                    // Legacy enum name aliases
                    "TINY", "COMPACT", "SMALL" -> SIZE_12
                    "STANDARD", "REGULAR"      -> SIZE_14
                    "MEDIUM"                   -> SIZE_16
                    "LARGE"                    -> SIZE_18
                    "EXTRA_LARGE"              -> SIZE_20
                    "HUGE"                     -> SIZE_22
                    // NoteWidgetTextSize names that may appear if prefs were ever cross-read;
                    // map to nearest available Todo widget size instead of silently defaulting to SIZE_14
                    "SIZE_28", "SIZE_30"       -> SIZE_26
                    "SIZE_32", "SIZE_34",
                    "SIZE_36"                  -> SIZE_26
                    else                       -> SIZE_14
                }
            }
        }
    }
}


data class WidgetConfig(
    val theme: WidgetTheme = WidgetTheme.DARK,
    val accent: WidgetAccent = WidgetAccent.NEON_GREEN,
    val opacityPercent: Int = 95, // 0 - 100
    val cornerRadiusDp: Int = 0,
    val textSize: WidgetTextSize = WidgetTextSize.SIZE_14
) {
    val backgroundColorInt: Int
        get() {
            val base = Color.parseColor(theme.baseColorHex)
            val alpha = ((opacityPercent / 100f) * 255).toInt().coerceIn(0, 255)
            return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
        }

    val itemBackgroundColorInt: Int
        get() {
            return if (theme.isDark) {
                val alpha = ((opacityPercent / 100f) * 35).toInt().coerceIn(10, 80)
                Color.argb(alpha, 255, 255, 255)
            } else {
                val alpha = ((opacityPercent / 100f) * 30).toInt().coerceIn(10, 60)
                Color.argb(alpha, 0, 0, 0)
            }
        }

    val accentColorInt: Int
        get() = Color.parseColor(accent.hex)

    val primaryTextColorInt: Int
        get() = if (theme.isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#121516")

    val secondaryTextColorInt: Int
        get() = if (theme.isDark) Color.parseColor("#8E9992") else Color.parseColor("#6B7280")
}

object WidgetConfigHelper {
    private const val PREFS_NAME = "todo_widget_theme_prefs"
    private const val KEY_THEME = "theme_"
    private const val KEY_ACCENT = "accent_"
    private const val KEY_OPACITY = "opacity_"
    private const val KEY_CORNER = "corner_"
    private const val KEY_TEXT_SIZE = "text_size_"
    private const val KEY_DEFAULT_SUFFIX = "default"

    fun getConfig(context: Context, appWidgetId: Int): WidgetConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Read each key independently: prefer widget-specific value, fall back to "default" suffix.
        // Previously a single keySuffix was chosen based solely on whether KEY_THEME was present,
        // causing all other keys (including text_size) to silently resolve to the wrong suffix
        // whenever prefs were partially written (e.g. after a restore, OTA, or cache clear).
        fun getStr(key: String, default: String): String {
            return try {
                if (appWidgetId > 0 && prefs.contains(key + appWidgetId)) {
                    prefs.getString(key + appWidgetId, default) ?: default
                } else {
                    prefs.getString(key + KEY_DEFAULT_SUFFIX, default) ?: default
                }
            } catch (_: Exception) {
                default
            }
        }
        fun getInt(key: String, default: Int): Int {
            return try {
                if (appWidgetId > 0 && prefs.contains(key + appWidgetId)) {
                    prefs.getInt(key + appWidgetId, default)
                } else {
                    prefs.getInt(key + KEY_DEFAULT_SUFFIX, default)
                }
            } catch (_: Exception) {
                default
            }
        }

        val themeName    = getStr(KEY_THEME,     WidgetTheme.DARK.name)
        val accentName   = getStr(KEY_ACCENT,    WidgetAccent.NEON_GREEN.name)
        val opacity      = getInt(KEY_OPACITY,   95)
        val corner       = getInt(KEY_CORNER,    0)
        val textSizeName = getStr(KEY_TEXT_SIZE, WidgetTextSize.SIZE_14.name)

        val theme    = runCatching { WidgetTheme.valueOf(themeName) }.getOrDefault(WidgetTheme.DARK)
        val accent   = runCatching { WidgetAccent.valueOf(accentName) }.getOrDefault(WidgetAccent.NEON_GREEN)
        val textSize = WidgetTextSize.fromNameOrDefault(textSizeName)

        return WidgetConfig(theme, accent, opacity, corner, textSize)
    }

    fun saveConfig(context: Context, appWidgetId: Int, config: WidgetConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        fun writeForSuffix(suffix: String) {
            editor
                .putString(KEY_THEME + suffix, config.theme.name)
                .putString(KEY_ACCENT + suffix, config.accent.name)
                .putInt(KEY_OPACITY + suffix, config.opacityPercent)
                .putInt(KEY_CORNER + suffix, config.cornerRadiusDp)
                .putString(KEY_TEXT_SIZE + suffix, config.textSize.name)
        }

        if (appWidgetId > 0) {
            writeForSuffix(appWidgetId.toString())
        } else {
            writeForSuffix(KEY_DEFAULT_SUFFIX)
        }
        editor.apply()
    }
}
