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

data class WidgetConfig(
    val theme: WidgetTheme = WidgetTheme.DARK,
    val accent: WidgetAccent = WidgetAccent.NEON_GREEN,
    val opacityPercent: Int = 95, // 0 - 100
    val cornerRadiusDp: Int = 0
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

    fun getConfig(context: Context, appWidgetId: Int): WidgetConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeName = prefs.getString(KEY_THEME + appWidgetId, WidgetTheme.DARK.name) ?: WidgetTheme.DARK.name
        val accentName = prefs.getString(KEY_ACCENT + appWidgetId, WidgetAccent.NEON_GREEN.name) ?: WidgetAccent.NEON_GREEN.name
        val opacity = prefs.getInt(KEY_OPACITY + appWidgetId, 95)
        val corner = prefs.getInt(KEY_CORNER + appWidgetId, 0)

        val theme = runCatching { WidgetTheme.valueOf(themeName) }.getOrDefault(WidgetTheme.DARK)
        val accent = runCatching { WidgetAccent.valueOf(accentName) }.getOrDefault(WidgetAccent.NEON_GREEN)

        return WidgetConfig(theme, accent, opacity, corner)
    }

    fun saveConfig(context: Context, appWidgetId: Int, config: WidgetConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_THEME + appWidgetId, config.theme.name)
            .putString(KEY_ACCENT + appWidgetId, config.accent.name)
            .putInt(KEY_OPACITY + appWidgetId, config.opacityPercent)
            .putInt(KEY_CORNER + appWidgetId, config.cornerRadiusDp)
            .apply()
    }
}
