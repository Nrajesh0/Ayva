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

package com.focusbyrj.app.ui.screens.notes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class KeepThemeType {
    NONE,
    GROCERIES,
    FOOD,
    MUSIC,
    RECIPES,
    NOTES,
    PLACES,
    CELEBRATION,
    STUDY,
    FOCUS
}

data class KeepColorTheme(
    val key: String,
    val name: String,
    val lightBg: Color,
    val darkBg: Color,
    val lightBorder: Color,
    val darkBorder: Color,
    val lightText: Color,
    val darkText: Color,
    val swatchColor: Color,
    val isIllustratedTheme: Boolean = false,
    val themeType: KeepThemeType = KeepThemeType.NONE,
    val icon: ImageVector? = null
) {
    fun getBackgroundColor(isDark: Boolean): Color = if (isDark) darkBg else lightBg
    fun getBorderColor(isDark: Boolean): Color = if (isDark) darkBorder else lightBorder
    fun getTextColor(isDark: Boolean): Color = if (isDark) darkText else lightText

    @Composable
    @ReadOnlyComposable
    fun resolveBackgroundColor(isDark: Boolean): Color {
        return if (key.equals("default", ignoreCase = true)) {
            if (isDark) darkBg else lightBg
        } else {
            getBackgroundColor(isDark)
        }
    }

    @Composable
    @ReadOnlyComposable
    fun resolveBorderColor(isDark: Boolean): Color {
        return if (key.equals("default", ignoreCase = true)) {
            if (isDark) darkBorder else lightBorder
        } else {
            getBorderColor(isDark)
        }
    }

    @Composable
    @ReadOnlyComposable
    fun resolveTextColor(isDark: Boolean): Color {
        return if (key.equals("default", ignoreCase = true)) {
            if (isDark) darkText else lightText
        } else {
            getTextColor(isDark)
        }
    }

    @Composable
    @ReadOnlyComposable
    fun resolveSecondaryTextColor(isDark: Boolean): Color {
        return if (key.equals("default", ignoreCase = true)) {
            if (isDark) darkText.copy(alpha = 0.70f) else lightText.copy(alpha = 0.70f)
        } else {
            getTextColor(isDark).copy(alpha = 0.75f)
        }
    }
}

object KeepColorPalette {
    // =========================================================================
    // GOOGLE KEEP SOLID COLOR PALETTE (12 Authentic Pastel Colors)
    // =========================================================================
    val Default = KeepColorTheme(
        key = "default",
        name = "Default",
        lightBg = Color(0xFFFFFFFF),
        darkBg = Color(0xFF131314),
        lightBorder = Color(0xFFE0E0E0),
        darkBorder = Color(0xFF333538),
        lightText = Color(0xFF202124),
        darkText = Color(0xFFE8EAED),
        swatchColor = Color(0xFF9AA0A6),
        icon = Icons.Filled.NotInterested
    )

    val Coral = KeepColorTheme(
        key = "coral",
        name = "Coral",
        lightBg = Color(0xFFFFEBEE),
        darkBg = Color(0xFF4C1D24),
        lightBorder = Color(0xFFE57373).copy(alpha = 0.45f),
        darkBorder = Color(0xFF7A2E3A),
        lightText = Color(0xFF371E22),
        darkText = Color(0xFFFCE8E6),
        swatchColor = Color(0xFFEF9A9A)
    )

    val Peach = KeepColorTheme(
        key = "peach",
        name = "Peach",
        lightBg = Color(0xFFFFF3E0),
        darkBg = Color(0xFF4E2A18),
        lightBorder = Color(0xFFFFB74D).copy(alpha = 0.45f),
        darkBorder = Color(0xFF7E4224),
        lightText = Color(0xFF382316),
        darkText = Color(0xFFFEF0E4),
        swatchColor = Color(0xFFFFCC80)
    )

    val Sand = KeepColorTheme(
        key = "sand",
        name = "Sand",
        lightBg = Color(0xFFFFFDE7),
        darkBg = Color(0xFF483E15),
        lightBorder = Color(0xFFFFF176).copy(alpha = 0.5f),
        darkBorder = Color(0xFF74641E),
        lightText = Color(0xFF363212),
        darkText = Color(0xFFFEFBE8),
        swatchColor = Color(0xFFFFF59D)
    )

    val Mint = KeepColorTheme(
        key = "mint",
        name = "Mint",
        lightBg = Color(0xFFE8F8F0),
        darkBg = Color(0xFF1B3D2F),
        lightBorder = Color(0xFF81C784).copy(alpha = 0.45f),
        darkBorder = Color(0xFF2C5E4A),
        lightText = Color(0xFF142F24),
        darkText = Color(0xFFE6F4EA),
        swatchColor = Color(0xFFA5D6A7)
    )

    val Sage = KeepColorTheme(
        key = "sage",
        name = "Sage",
        lightBg = Color(0xFFE0F2F1),
        darkBg = Color(0xFF133E3B),
        lightBorder = Color(0xFF4DB6AC).copy(alpha = 0.45f),
        darkBorder = Color(0xFF205E59),
        lightText = Color(0xFF102E2B),
        darkText = Color(0xFFE4F7FB),
        swatchColor = Color(0xFF80CBC4)
    )

    val Fog = KeepColorTheme(
        key = "fog",
        name = "Fog",
        lightBg = Color(0xFFE1F5FE),
        darkBg = Color(0xFF17384A),
        lightBorder = Color(0xFF4FC3F7).copy(alpha = 0.45f),
        darkBorder = Color(0xFF255773),
        lightText = Color(0xFF122C3A),
        darkText = Color(0xFFE8F4FD),
        swatchColor = Color(0xFF90CAF9)
    )

    val Storm = KeepColorTheme(
        key = "storm",
        name = "Storm",
        lightBg = Color(0xFFE8EAF6),
        darkBg = Color(0xFF212B47),
        lightBorder = Color(0xFF7986CB).copy(alpha = 0.45f),
        darkBorder = Color(0xFF33436F),
        lightText = Color(0xFF1A223B),
        darkText = Color(0xFFE8EAF6),
        swatchColor = Color(0xFF9FA8DA)
    )

    val Dusk = KeepColorTheme(
        key = "dusk",
        name = "Dusk",
        lightBg = Color(0xFFF3E5F5),
        darkBg = Color(0xFF382346),
        lightBorder = Color(0xFFBA68C8).copy(alpha = 0.45f),
        darkBorder = Color(0xFF5A3970),
        lightText = Color(0xFF2B1838),
        darkText = Color(0xFFF3E8FD),
        swatchColor = Color(0xFFCE93D8)
    )

    val Blossom = KeepColorTheme(
        key = "blossom",
        name = "Blossom",
        lightBg = Color(0xFFFCE4EC),
        darkBg = Color(0xFF491E32),
        lightBorder = Color(0xFFF06292).copy(alpha = 0.45f),
        darkBorder = Color(0xFF752F50),
        lightText = Color(0xFF381525),
        darkText = Color(0xFFFCE8F0),
        swatchColor = Color(0xFFF48FB1)
    )

    val Clay = KeepColorTheme(
        key = "clay",
        name = "Clay",
        lightBg = Color(0xFFEFEBE9),
        darkBg = Color(0xFF3E322E),
        lightBorder = Color(0xFFA1887F).copy(alpha = 0.45f),
        darkBorder = Color(0xFF614E48),
        lightText = Color(0xFF332723),
        darkText = Color(0xFFF1EFEA),
        swatchColor = Color(0xFFBCAAA4)
    )

    val Chalk = KeepColorTheme(
        key = "chalk",
        name = "Chalk",
        lightBg = Color(0xFFECEFF1),
        darkBg = Color(0xFF263238),
        lightBorder = Color(0xFF90A4AE).copy(alpha = 0.45f),
        darkBorder = Color(0xFF3E4E56),
        lightText = Color(0xFF1E282C),
        darkText = Color(0xFFECEFF1),
        swatchColor = Color(0xFFB0BEC5)
    )

    // =========================================================================
    // GOOGLE KEEP ILLUSTRATED BACKGROUND THEMES (9 Signature Visual Themes)
    // =========================================================================
    val ThemeGroceries = KeepColorTheme(
        key = "theme_groceries",
        name = "Groceries",
        lightBg = Color(0xFFF1F8E9),
        darkBg = Color(0xFF1E3320),
        lightBorder = Color(0xFF81C784).copy(alpha = 0.5f),
        darkBorder = Color(0xFF2E5E35),
        lightText = Color(0xFF1B381E),
        darkText = Color(0xFFE8F5E9),
        swatchColor = Color(0xFFAED581),
        isIllustratedTheme = true,
        themeType = KeepThemeType.GROCERIES,
        icon = Icons.Filled.ShoppingCart
    )

    val ThemeFood = KeepColorTheme(
        key = "theme_food",
        name = "Food",
        lightBg = Color(0xFFFFF3E0),
        darkBg = Color(0xFF3E2723),
        lightBorder = Color(0xFFFFB74D).copy(alpha = 0.5f),
        darkBorder = Color(0xFF6D3B1E),
        lightText = Color(0xFF381F12),
        darkText = Color(0xFFFFF3E0),
        swatchColor = Color(0xFFFFB74D),
        isIllustratedTheme = true,
        themeType = KeepThemeType.FOOD,
        icon = Icons.Filled.Restaurant
    )

    val ThemeMusic = KeepColorTheme(
        key = "theme_music",
        name = "Music",
        lightBg = Color(0xFFEDE7F6),
        darkBg = Color(0xFF251B38),
        lightBorder = Color(0xFFB39DDB).copy(alpha = 0.5f),
        darkBorder = Color(0xFF4A346E),
        lightText = Color(0xFF231538),
        darkText = Color(0xFFEDE7F6),
        swatchColor = Color(0xFFB39DDB),
        isIllustratedTheme = true,
        themeType = KeepThemeType.MUSIC,
        icon = Icons.Filled.Headphones
    )

    val ThemeRecipes = KeepColorTheme(
        key = "theme_recipes",
        name = "Recipes",
        lightBg = Color(0xFFFBE9E7),
        darkBg = Color(0xFF3E221B),
        lightBorder = Color(0xFFFF8A65).copy(alpha = 0.5f),
        darkBorder = Color(0xFF6E3628),
        lightText = Color(0xFF3A1A14),
        darkText = Color(0xFFFBE9E7),
        swatchColor = Color(0xFFFFAB91),
        isIllustratedTheme = true,
        themeType = KeepThemeType.RECIPES,
        icon = Icons.Filled.MenuBook
    )

    val ThemeNotes = KeepColorTheme(
        key = "theme_notes",
        name = "Notes",
        lightBg = Color(0xFFFFFDE7),
        darkBg = Color(0xFF363219),
        lightBorder = Color(0xFFFFF176).copy(alpha = 0.55f),
        darkBorder = Color(0xFF665E24),
        lightText = Color(0xFF332F11),
        darkText = Color(0xFFFFFDE7),
        swatchColor = Color(0xFFFFF59D),
        isIllustratedTheme = true,
        themeType = KeepThemeType.NOTES,
        icon = Icons.Filled.EditNote
    )

    val ThemePlaces = KeepColorTheme(
        key = "theme_places",
        name = "Places",
        lightBg = Color(0xFFE0F7FA),
        darkBg = Color(0xFF13363B),
        lightBorder = Color(0xFF4DD0E1).copy(alpha = 0.5f),
        darkBorder = Color(0xFF225B63),
        lightText = Color(0xFF0F2E33),
        darkText = Color(0xFFE0F7FA),
        swatchColor = Color(0xFF80DEEA),
        isIllustratedTheme = true,
        themeType = KeepThemeType.PLACES,
        icon = Icons.Filled.Landscape
    )

    val ThemeCelebration = KeepColorTheme(
        key = "theme_celebration",
        name = "Celebration",
        lightBg = Color(0xFFFCE4EC),
        darkBg = Color(0xFF3E1C2B),
        lightBorder = Color(0xFFF48FB1).copy(alpha = 0.5f),
        darkBorder = Color(0xFF6E2847),
        lightText = Color(0xFF381323),
        darkText = Color(0xFFFCE4EC),
        swatchColor = Color(0xFFF48FB1),
        isIllustratedTheme = true,
        themeType = KeepThemeType.CELEBRATION,
        icon = Icons.Filled.Celebration
    )

    val ThemeStudy = KeepColorTheme(
        key = "theme_study",
        name = "Study",
        lightBg = Color(0xFFE8EAF6),
        darkBg = Color(0xFF1E2640),
        lightBorder = Color(0xFF9FA8DA).copy(alpha = 0.5f),
        darkBorder = Color(0xFF35426E),
        lightText = Color(0xFF1B2138),
        darkText = Color(0xFFE8EAF6),
        swatchColor = Color(0xFF9FA8DA),
        isIllustratedTheme = true,
        themeType = KeepThemeType.STUDY,
        icon = Icons.Filled.School
    )

    val ThemeFocus = KeepColorTheme(
        key = "theme_focus",
        name = "Focus",
        lightBg = Color(0xFFE0F2F1),
        darkBg = Color(0xFF143834),
        lightBorder = Color(0xFF80CBC4).copy(alpha = 0.5f),
        darkBorder = Color(0xFF225852),
        lightText = Color(0xFF102D2A),
        darkText = Color(0xFFE0F2F1),
        swatchColor = Color(0xFF80CBC4),
        isIllustratedTheme = true,
        themeType = KeepThemeType.FOCUS,
        icon = Icons.Filled.SelfImprovement
    )

    val allColors: List<KeepColorTheme> = listOf(
        Default, Coral, Peach, Sand, Mint, Sage, Fog, Storm, Dusk, Blossom, Clay, Chalk
    )

    val allThemes: List<KeepColorTheme> = listOf(
        ThemeGroceries, ThemeFood, ThemeMusic, ThemeRecipes, ThemeNotes,
        ThemePlaces, ThemeCelebration, ThemeStudy, ThemeFocus
    )

    val allItems: List<KeepColorTheme> = allColors + allThemes

    fun getColor(key: String?): KeepColorTheme {
        if (key.isNullOrBlank()) return Default
        return allItems.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: Default
    }
}
