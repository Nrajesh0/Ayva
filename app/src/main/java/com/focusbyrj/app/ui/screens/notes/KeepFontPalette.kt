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

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.focusbyrj.app.R

data class KeepFontStyle(
    val key: String,
    val name: String,
    val description: String,
    val fontFamily: FontFamily,
    val previewSample: String = "Aa"
)

object KeepFontPalette {
    val RobotoSerifFamily = FontFamily(
        Font(R.font.roboto_serif, FontWeight.Normal),
        Font(R.font.roboto_serif, FontWeight.Bold),
        Font(R.font.roboto_serif, FontWeight.Medium),
        Font(R.font.roboto_serif, FontWeight.SemiBold)
    )

    val RobotoSlabFamily = FontFamily(
        Font(R.font.roboto_slab, FontWeight.Normal),
        Font(R.font.roboto_slab, FontWeight.Bold),
        Font(R.font.roboto_slab, FontWeight.Medium),
        Font(R.font.roboto_slab, FontWeight.SemiBold)
    )

    val CaveatFamily = FontFamily(
        Font(R.font.caveat, FontWeight.Normal),
        Font(R.font.caveat, FontWeight.Bold),
        Font(R.font.caveat, FontWeight.Medium),
        Font(R.font.caveat, FontWeight.SemiBold)
    )

    val RobotoMonoFamily = FontFamily(
        Font(R.font.roboto_mono, FontWeight.Normal),
        Font(R.font.roboto_mono, FontWeight.Bold),
        Font(R.font.roboto_mono, FontWeight.Medium),
        Font(R.font.roboto_mono, FontWeight.SemiBold)
    )

    val NunitoFamily = FontFamily(
        Font(R.font.nunito, FontWeight.Normal),
        Font(R.font.nunito, FontWeight.Bold),
        Font(R.font.nunito, FontWeight.Medium),
        Font(R.font.nunito, FontWeight.SemiBold)
    )

    val Basic = KeepFontStyle(
        key = "default",
        name = "Basic",
        description = "Standard sans-serif",
        fontFamily = FontFamily.Default,
        previewSample = "Aa"
    )

    val Serif = KeepFontStyle(
        key = "serif",
        name = "Serif",
        description = "Classic editorial serif",
        fontFamily = RobotoSerifFamily,
        previewSample = "Aa"
    )

    val Slab = KeepFontStyle(
        key = "slab",
        name = "Slab",
        description = "Modern slab serif",
        fontFamily = RobotoSlabFamily,
        previewSample = "Aa"
    )

    val Script = KeepFontStyle(
        key = "script",
        name = "Script",
        description = "Handwritten journal",
        fontFamily = CaveatFamily,
        previewSample = "Aa"
    )

    val Mono = KeepFontStyle(
        key = "mono",
        name = "Mono",
        description = "Fixed-width typewriter & code",
        fontFamily = RobotoMonoFamily,
        previewSample = "Aa"
    )

    val Casual = KeepFontStyle(
        key = "casual",
        name = "Casual",
        description = "Friendly rounded type",
        fontFamily = NunitoFamily,
        previewSample = "Aa"
    )

    val allFonts: List<KeepFontStyle> = listOf(
        Basic, Serif, Slab, Script, Mono, Casual
    )

    fun getFont(key: String?): KeepFontStyle {
        if (key.isNullOrBlank()) return Basic
        return allFonts.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: Basic
    }
}
