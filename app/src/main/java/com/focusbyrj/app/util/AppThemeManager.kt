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
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeColor(
    val id: String,
    val displayName: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val glowColor: Color,
    val description: String,
    val swatch: List<Color>
) {
    EMERALD(
        id = "emerald",
        displayName = "Calm Sage",
        primary = Color(0xFF6E9987),
        secondary = Color(0xFF8EAF9F),
        tertiary = Color(0xFF557C6C),
        glowColor = Color(0x1F6E9987),
        description = "Subtle eucalyptus & serene botanical calm",
        swatch = listOf(Color(0xFF557C6C), Color(0xFF6E9987), Color(0xFF8EAF9F))
    ),
    CYAN(
        id = "cyan",
        displayName = "Arctic Slate",
        primary = Color(0xFF6B8EA8),
        secondary = Color(0xFF8BA9C0),
        tertiary = Color(0xFF53748C),
        glowColor = Color(0x1F6B8EA8),
        description = "Soft misty fjord & calming slate blue",
        swatch = listOf(Color(0xFF53748C), Color(0xFF6B8EA8), Color(0xFF8BA9C0))
    ),
    VIOLET(
        id = "violet",
        displayName = "Dusk Heather",
        primary = Color(0xFF8A82A5),
        secondary = Color(0xFFA59EBE),
        tertiary = Color(0xFF6F678A),
        glowColor = Color(0x1F8A82A5),
        description = "Muted lavender twilight & twilight dusk",
        swatch = listOf(Color(0xFF6F678A), Color(0xFF8A82A5), Color(0xFFA59EBE))
    ),
    ROSE(
        id = "rose",
        displayName = "Dusty Rose",
        primary = Color(0xFFB57E82),
        secondary = Color(0xFFCF9AA0),
        tertiary = Color(0xFF986266),
        glowColor = Color(0x1FB57E82),
        description = "Muted dried petal & warm clay rose",
        swatch = listOf(Color(0xFF986266), Color(0xFFB57E82), Color(0xFFCF9AA0))
    ),
    AMBER(
        id = "amber",
        displayName = "Warm Sand",
        primary = Color(0xFFBA976B),
        secondary = Color(0xFFD6B58A),
        tertiary = Color(0xFF9C7A4E),
        glowColor = Color(0x1FBA976B),
        description = "Subtle desert ochre & calming amber",
        swatch = listOf(Color(0xFF9C7A4E), Color(0xFFBA976B), Color(0xFFD6B58A))
    ),
    COBALT(
        id = "cobalt",
        displayName = "Coastal Slate",
        primary = Color(0xFF627D98),
        secondary = Color(0xFF829AB1),
        tertiary = Color(0xFF486581),
        glowColor = Color(0x1F627D98),
        description = "Deep coastal ocean & calm navy haze",
        swatch = listOf(Color(0xFF486581), Color(0xFF627D98), Color(0xFF829AB1))
    ),
    LIME(
        id = "lime",
        displayName = "Muted Matcha",
        primary = Color(0xFF829465),
        secondary = Color(0xFFA1B384),
        tertiary = Color(0xFF66784A),
        glowColor = Color(0x1F829465),
        description = "Calm organic tea leaf & soft matcha",
        swatch = listOf(Color(0xFF66784A), Color(0xFF829465), Color(0xFFA1B384))
    ),
    RUBY(
        id = "ruby",
        displayName = "Desert Clay",
        primary = Color(0xFFA86862),
        secondary = Color(0xFFC48680),
        tertiary = Color(0xFF8C4C46),
        glowColor = Color(0x1FA86862),
        description = "Subdued earthenware & warm terra",
        swatch = listOf(Color(0xFF8C4C46), Color(0xFFA86862), Color(0xFFC48680))
    ),
    CORAL(
        id = "coral",
        displayName = "Muted Terracotta",
        primary = Color(0xFFBA846F),
        secondary = Color(0xFFD49F8B),
        tertiary = Color(0xFF9E6853),
        glowColor = Color(0x1FBA846F),
        description = "Soft Mediterranean brick & warm linen",
        swatch = listOf(Color(0xFF9E6853), Color(0xFFBA846F), Color(0xFFD49F8B))
    ),
    LAVENDER(
        id = "lavender",
        displayName = "Lilac Ash",
        primary = Color(0xFF9688A6),
        secondary = Color(0xFFB3A6C2),
        tertiary = Color(0xFF7A6B8B),
        glowColor = Color(0x1F9688A6),
        description = "Quiet pastel mist & gentle lilac",
        swatch = listOf(Color(0xFF7A6B8B), Color(0xFF9688A6), Color(0xFFB3A6C2))
    ),
    TEAL(
        id = "teal",
        displayName = "Mineral Teal",
        primary = Color(0xFF659691),
        secondary = Color(0xFF84B5B0),
        tertiary = Color(0xFF4B7B76),
        glowColor = Color(0x1F659691),
        description = "Subdued mineral spring & calm jade",
        swatch = listOf(Color(0xFF4B7B76), Color(0xFF659691), Color(0xFF84B5B0))
    ),
    PLATINUM(
        id = "platinum",
        displayName = "Pure Minimal",
        primary = Color(0xFF94A3B8),
        secondary = Color(0xFFCBD5E1),
        tertiary = Color(0xFF64748B),
        glowColor = Color(0x1F94A3B8),
        description = "Understated monochromatic titanium",
        swatch = listOf(Color(0xFF64748B), Color(0xFF94A3B8), Color(0xFFCBD5E1))
    );

    companion object {
        fun fromId(id: String): AppThemeColor = entries.find { it.id == id } ?: EMERALD
    }
}

enum class ThemeMode(val id: String, val displayName: String, val isDarkTheme: Boolean?) {
    SYSTEM("system", "System Default", null),
    DARK("dark", "Dark Minimal", true),
    OLED("oled", "Pitch Black", true),
    GRAPHITE("graphite", "Graphite Black", true),
    OBSIDIAN("obsidian", "Obsidian Black", true),
    LIGHT("light", "Clean Light", false),
    FROST("frost", "Frost White", false),
    PAPER("paper", "Paper White", false),
    WARM("warm", "Warm Sepia", false),
    IVORY("ivory", "Ivory White", false);

    companion object {
        fun fromId(id: String): ThemeMode = entries.find { it.id == id } ?: SYSTEM
    }
}

object AppThemeManager {
    private val _themeFlow = MutableStateFlow(AppThemeColor.EMERALD)
    val themeFlow: StateFlow<AppThemeColor> = _themeFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()
    private val _overlayThemeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    val overlayThemeModeFlow: StateFlow<ThemeMode> = _overlayThemeModeFlow.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        val savedId = prefs.getString("app_theme_color", AppThemeColor.EMERALD.id) ?: AppThemeColor.EMERALD.id
        _themeFlow.value = AppThemeColor.fromId(savedId)
        val savedModeId = prefs.getString("app_theme_mode", ThemeMode.SYSTEM.id) ?: ThemeMode.SYSTEM.id
        _themeModeFlow.value = ThemeMode.fromId(savedModeId)
        val savedOverlayModeId = prefs.getString("overlay_theme_mode", ThemeMode.SYSTEM.id) ?: ThemeMode.SYSTEM.id
        _overlayThemeModeFlow.value = ThemeMode.fromId(savedOverlayModeId)
    }

    fun setTheme(context: Context, theme: AppThemeColor) {
        _themeFlow.value = theme
        val prefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_theme_color", theme.id).apply()
    }

    fun setOverlayThemeMode(context: Context, mode: ThemeMode) {
        _overlayThemeModeFlow.value = mode
        val prefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("overlay_theme_mode", mode.id).apply()
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeModeFlow.value = mode
        val prefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_theme_mode", mode.id).apply()
    }
}
