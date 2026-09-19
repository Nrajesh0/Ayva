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

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Google Keep style vector background art illustration.
 * Renders subtle, elegant decorative theme art onto notes cards and the note editor.
 * Scales dynamically using relative canvas dimensions and coordinates with light/dark palettes.
 */
@Composable
fun KeepThemeIllustration(
    themeType: KeepThemeType,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (themeType == KeepThemeType.NONE) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        when (themeType) {
            KeepThemeType.NONE -> { /* No-op */ }
            KeepThemeType.GROCERIES -> drawGroceriesTheme(w, h, isDark)
            KeepThemeType.FOOD -> drawFoodTheme(w, h, isDark)
            KeepThemeType.MUSIC -> drawMusicTheme(w, h, isDark)
            KeepThemeType.RECIPES -> drawRecipesTheme(w, h, isDark)
            KeepThemeType.NOTES -> drawNotesTheme(w, h, isDark)
            KeepThemeType.PLACES -> drawPlacesTheme(w, h, isDark)
            KeepThemeType.CELEBRATION -> drawCelebrationTheme(w, h, isDark)
            KeepThemeType.STUDY -> drawStudyTheme(w, h, isDark)
            KeepThemeType.FOCUS -> drawFocusTheme(w, h, isDark)
        }
    }
}

// -------------------------------------------------------------------------
// 1. GROCERIES: Shopping basket, apple with leaf, fresh carrot/baguette
// -------------------------------------------------------------------------
private fun DrawScope.drawGroceriesTheme(w: Float, h: Float, isDark: Boolean) {
    val strokeColor = if (isDark) Color(0xFFA5D6A7).copy(alpha = 0.32f) else Color(0xFF2E7D32).copy(alpha = 0.22f)
    val fillColor = if (isDark) Color(0xFF81C784).copy(alpha = 0.14f) else Color(0xFF4CAF50).copy(alpha = 0.10f)
    val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Shopping basket body
    val basketPath = Path().apply {
        moveTo(w * 0.22f, h * 0.52f)
        lineTo(w * 0.78f, h * 0.52f)
        lineTo(w * 0.70f, h * 0.88f)
        lineTo(w * 0.30f, h * 0.88f)
        close()
    }
    drawPath(basketPath, fillColor)
    drawPath(basketPath, strokeColor, style = stroke)

    // Basket handle arch
    drawArc(
        color = strokeColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.34f, h * 0.32f),
        size = Size(w * 0.32f, h * 0.38f),
        style = stroke
    )

    // Basket grid lines
    drawLine(strokeColor, Offset(w * 0.44f, h * 0.52f), Offset(w * 0.42f, h * 0.88f), strokeWidth = 1.8f)
    drawLine(strokeColor, Offset(w * 0.56f, h * 0.52f), Offset(w * 0.58f, h * 0.88f), strokeWidth = 1.8f)
    drawLine(strokeColor, Offset(w * 0.26f, h * 0.70f), Offset(w * 0.74f, h * 0.70f), strokeWidth = 1.8f)

    // Fresh Apple sitting on top of basket
    val appleCenter = Offset(w * 0.38f, h * 0.44f)
    val appleRadius = w * 0.10f
    drawCircle(fillColor, radius = appleRadius, center = appleCenter)
    drawCircle(strokeColor, radius = appleRadius, center = appleCenter, style = stroke)

    // Apple stem & little leaf
    val leafPath = Path().apply {
        moveTo(appleCenter.x, appleCenter.y - appleRadius)
        quadraticBezierTo(appleCenter.x + w * 0.08f, appleCenter.y - appleRadius - h * 0.06f, appleCenter.x + w * 0.06f, appleCenter.y - appleRadius - h * 0.10f)
    }
    drawPath(leafPath, strokeColor, style = stroke)

    // Baguette / carrot peeking out right
    val baguettePath = Path().apply {
        moveTo(w * 0.58f, h * 0.52f)
        lineTo(w * 0.74f, h * 0.30f)
        lineTo(w * 0.82f, h * 0.36f)
        lineTo(w * 0.66f, h * 0.55f)
        close()
    }
    drawPath(baguettePath, fillColor)
    drawPath(baguettePath, strokeColor, style = stroke)
}

// -------------------------------------------------------------------------
// 2. FOOD: Chef hat, cooking pan with rising steam curls
// -------------------------------------------------------------------------
private fun DrawScope.drawFoodTheme(w: Float, h: Float, isDark: Boolean) {
    val strokeColor = if (isDark) Color(0xFFFFCC80).copy(alpha = 0.32f) else Color(0xFFE65100).copy(alpha = 0.22f)
    val fillColor = if (isDark) Color(0xFFFFB74D).copy(alpha = 0.14f) else Color(0xFFFF9800).copy(alpha = 0.10f)
    val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Chef hat puffs
    val hatPath = Path().apply {
        moveTo(w * 0.28f, h * 0.64f)
        lineTo(w * 0.28f, h * 0.52f)
        cubicTo(w * 0.18f, h * 0.46f, w * 0.20f, h * 0.28f, w * 0.36f, h * 0.28f)
        cubicTo(w * 0.42f, h * 0.16f, w * 0.58f, h * 0.16f, w * 0.64f, h * 0.28f)
        cubicTo(w * 0.80f, h * 0.28f, w * 0.82f, h * 0.46f, w * 0.72f, h * 0.52f)
        lineTo(w * 0.72f, h * 0.64f)
        close()
    }
    drawPath(hatPath, fillColor)
    drawPath(hatPath, strokeColor, style = stroke)

    // Hat band
    val bandPath = Path().apply {
        moveTo(w * 0.28f, h * 0.64f)
        lineTo(w * 0.72f, h * 0.64f)
        lineTo(w * 0.72f, h * 0.74f)
        lineTo(w * 0.28f, h * 0.74f)
        close()
    }
    drawPath(bandPath, strokeColor, style = stroke)

    // Crossed Fork and Spoon below or steam above
    val steam1 = Path().apply {
        moveTo(w * 0.42f, h * 0.16f)
        cubicTo(w * 0.38f, h * 0.10f, w * 0.46f, h * 0.06f, w * 0.42f, h * 0.02f)
    }
    val steam2 = Path().apply {
        moveTo(w * 0.58f, h * 0.16f)
        cubicTo(w * 0.54f, h * 0.10f, w * 0.62f, h * 0.06f, w * 0.58f, h * 0.02f)
    }
    drawPath(steam1, strokeColor, style = stroke)
    drawPath(steam2, strokeColor, style = stroke)

    // Fork and knife accents
    drawLine(strokeColor, Offset(w * 0.32f, h * 0.82f), Offset(w * 0.68f, h * 0.82f), strokeWidth = 2f, cap = StrokeCap.Round)
}

// -------------------------------------------------------------------------
// 3. MUSIC: Headphones, vinyl groove record, floating musical notes
// -------------------------------------------------------------------------
private fun DrawScope.drawMusicTheme(w: Float, h: Float, isDark: Boolean) {
    val strokeColor = if (isDark) Color(0xFFCE93D8).copy(alpha = 0.34f) else Color(0xFF6A1B9A).copy(alpha = 0.22f)
    val fillColor = if (isDark) Color(0xFFBA68C8).copy(alpha = 0.14f) else Color(0xFF8E24AA).copy(alpha = 0.10f)
    val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Headphones headband arc
    drawArc(
        color = strokeColor,
        startAngle = 175f,
        sweepAngle = 190f,
        useCenter = false,
        topLeft = Offset(w * 0.20f, h * 0.18f),
        size = Size(w * 0.60f, h * 0.58f),
        style = Stroke(width = 3.2f, cap = StrokeCap.Round)
    )

    // Left earcup
    drawRoundRect(
        color = fillColor,
        topLeft = Offset(w * 0.14f, h * 0.48f),
        size = Size(w * 0.14f, h * 0.32f),
        cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
    )
    drawRoundRect(
        color = strokeColor,
        topLeft = Offset(w * 0.14f, h * 0.48f),
        size = Size(w * 0.14f, h * 0.32f),
        cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
        style = stroke
    )

    // Right earcup
    drawRoundRect(
        color = fillColor,
        topLeft = Offset(w * 0.72f, h * 0.48f),
        size = Size(w * 0.14f, h * 0.32f),
        cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
    )
    drawRoundRect(
        color = strokeColor,
        topLeft = Offset(w * 0.72f, h * 0.48f),
        size = Size(w * 0.14f, h * 0.32f),
        cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
        style = stroke
    )

    // Center floating eighth note with beam
    val note1Center = Offset(w * 0.42f, h * 0.68f)
    val note2Center = Offset(w * 0.58f, h * 0.60f)
    val noteRadius = w * 0.055f

    drawCircle(fillColor, radius = noteRadius, center = note1Center)
    drawCircle(strokeColor, radius = noteRadius, center = note1Center, style = stroke)

    drawCircle(fillColor, radius = noteRadius, center = note2Center)
    drawCircle(strokeColor, radius = noteRadius, center = note2Center, style = stroke)

    // Note stems & beam
    drawLine(strokeColor, Offset(note1Center.x + noteRadius, note1Center.y), Offset(note1Center.x + noteRadius, h * 0.44f), strokeWidth = 2f)
    drawLine(strokeColor, Offset(note2Center.x + noteRadius, note2Center.y), Offset(note2Center.x + noteRadius, h * 0.36f), strokeWidth = 2f)
    drawLine(strokeColor, Offset(note1Center.x + noteRadius, h * 0.44f), Offset(note2Center.x + noteRadius, h * 0.36f), strokeWidth = 3.2f, cap = StrokeCap.Round)
}

// -------------------------------------------------------------------------
// 4. RECIPES: Open cookbook with pages, wooden spoon & whisk
// -------------------------------------------------------------------------
private fun DrawScope.drawRecipesTheme(w: Float, h: Float, isDark: Boolean) {
    val strokeColor = if (isDark) Color(0xFFFFAB91).copy(alpha = 0.32f) else Color(0xFFD84315).copy(alpha = 0.22f)
    val fillColor = if (isDark) Color(0xFFFF8A65).copy(alpha = 0.14f) else Color(0xFFFF5722).copy(alpha = 0.10f)
    val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Open book left page
    val leftPage = Path().apply {
        moveTo(w * 0.50f, h * 0.78f)
        cubicTo(w * 0.40f, h * 0.74f, w * 0.28f, h * 0.74f, w * 0.16f, h * 0.78f)
        lineTo(w * 0.16f, h * 0.42f)
        cubicTo(w * 0.28f, h * 0.38f, w * 0.40f, h * 0.38f, w * 0.50f, h * 0.42f)
        close()
    }
    drawPath(leftPage, fillColor)
    drawPath(leftPage, strokeColor, style = stroke)

    // Open book right page
    val rightPage = Path().apply {
        moveTo(w * 0.50f, h * 0.78f)
        cubicTo(w * 0.60f, h * 0.74f, w * 0.72f, h * 0.74f, w * 0.84f, h * 0.78f)
        lineTo(w * 0.84f, h * 0.42f)
        cubicTo(w * 0.72f, h * 0.38f, w * 0.60f, h * 0.38f, w * 0.50f, h * 0.42f)
        close()
    }
    drawPath(rightPage, fillColor)
    drawPath(rightPage, strokeColor, style = stroke)

    // Center spine line
    drawLine(strokeColor, Offset(w * 0.50f, h * 0.42f), Offset(w * 0.50f, h * 0.80f), strokeWidth = 2.5f)

    // Recipe line accents on left page
    drawLine(strokeColor, Offset(w * 0.22f, h * 0.48f), Offset(w * 0.44f, h * 0.48f), strokeWidth = 1.5f)
    drawLine(strokeColor, Offset(w * 0.22f, h * 0.56f), Offset(w * 0.44f, h * 0.56f), strokeWidth = 1.5f)
    drawLine(strokeColor, Offset(w * 0.22f, h * 0.64f), Offset(w * 0.38f, h * 0.64f), strokeWidth = 1.5f)

    // Spoon crossing over top right
    drawLine(strokeColor, Offset(w * 0.60f, h * 0.34f), Offset(w * 0.88f, h * 0.16f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    drawOval(fillColor, topLeft = Offset(w * 0.54f, h * 0.30f), size = Size(w * 0.10f, h * 0.12f))
    drawOval(strokeColor, topLeft = Offset(w * 0.54f, h * 0.30f), size = Size(w * 0.10f, h * 0.12f), style = stroke)
}

// -------------------------------------------------------------------------
// 5. NOTES: Spiral binder notepad, angled pencil, paper clip
// -------------------------------------------------------------------------
private fun DrawScope.drawNotesTheme(w: Float, h: Float, isDark: Boolean) {
    val strokeColor = if (isDark) Color(0xFFFFF59D).copy(alpha = 0.34f) else Color(0xFFF57F17).copy(alpha = 0.24f)
    val fillColor = if (isDark) Color(0xFFFFF176).copy(alpha = 0.14f) else Color(0xFFFFEE58).copy(alpha = 0.10f)
    val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Notepad sheet
    val notepad = Path().apply {
        moveTo(w * 0.22f, h * 0.24f)
        lineTo(w * 0.68f, h * 0.24f)
        lineTo(w * 0.68f, h * 0.82f)
        lineTo(w * 0.22f, h * 0.82f)
        close()
    }
    drawPath(notepad, fillColor)
    drawPath(notepad, strokeColor, style = stroke)

    // Binder spiral loops on top/left
    listOf(0.34f, 0.46f, 0.58f, 0.70f).forEach { yFrac ->
        drawCircle(strokeColor, radius = w * 0.025f, center = Offset(w * 0.22f, h * yFrac), style = stroke)
    }

    // Ruled lines on notepad
    listOf(0.36f, 0.48f, 0.60f, 0.72f).forEach { yFrac ->
        drawLine(strokeColor, Offset(w * 0.28f, h * yFrac), Offset(w * 0.62f, h * yFrac), strokeWidth = 1.4f)
    }

    // Angled pencil on the right
    val pencilPath = Path().apply {
        moveTo(w * 0.72f, h * 0.30f)
        lineTo(w * 0.86f, h * 0.44f)
        lineTo(w * 0.74f, h * 0.78f)
        lineTo(w * 0.66f, h * 0.84f)
        lineTo(w * 0.64f, h * 0.76f)
        close()
    }
    drawPath(pencilPath, fillColor)
    drawPath(pencilPath, strokeColor, style = stroke)
}

// -------------------------------------------------------------------------
// 6. PLACES: Twin mountain peaks, rising warm sun, compass star
// -------------------------------------------------------------------------
private fun DrawScope.drawPlacesTheme(w: Float, h: Float, isDark: Boolean) {
    val strokeColor = if (isDark) Color(0xFF80DEEA).copy(alpha = 0.34f) else Color(0xFF00838F).copy(alpha = 0.24f)
    val fillColor = if (isDark) Color(0xFF4DD0E1).copy(alpha = 0.14f) else Color(0xFF00ACC1).copy(alpha = 0.10f)
    val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Half sun rising behind peaks
    drawArc(
        color = strokeColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(w * 0.38f, h * 0.28f),
        size = Size(w * 0.24f, h * 0.24f),
        style = stroke
    )
    // Sun rays
    listOf(210f, 240f, 270f, 300f, 330f).forEach { angle ->
        val rad = Math.toRadians(angle.toDouble())
        val startX = (w * 0.50f + Math.cos(rad) * (w * 0.14f)).toFloat()
        val startY = (h * 0.40f + Math.sin(rad) * (h * 0.14f)).toFloat()
        val endX = (w * 0.50f + Math.cos(rad) * (w * 0.20f)).toFloat()
        val endY = (h * 0.40f + Math.sin(rad) * (h * 0.20f)).toFloat()
        drawLine(strokeColor, Offset(startX, startY), Offset(endX, endY), strokeWidth = 1.8f, cap = StrokeCap.Round)
    }

    // Background small mountain
    val bgMountain = Path().apply {
        moveTo(w * 0.42f, h * 0.84f)
        lineTo(w * 0.68f, h * 0.44f)
        lineTo(w * 0.90f, h * 0.84f)
        close()
    }
    drawPath(bgMountain, fillColor)
    drawPath(bgMountain, strokeColor, style = stroke)

    // Foreground main mountain
    val mainMountain = Path().apply {
        moveTo(w * 0.12f, h * 0.86f)
        lineTo(w * 0.44f, h * 0.34f)
        lineTo(w * 0.74f, h * 0.86f)
        close()
    }
    drawPath(mainMountain, fillColor)
    drawPath(mainMountain, strokeColor, style = stroke)

    // Snow ridge lines on main peak
    val ridgePath = Path().apply {
        moveTo(w * 0.44f, h * 0.34f)
        lineTo(w * 0.42f, h * 0.52f)
        lineTo(w * 0.48f, h * 0.66f)
        lineTo(w * 0.46f, h * 0.86f)
    }
    drawPath(ridgePath, strokeColor, style = stroke)
}

// -------------------------------------------------------------------------
// 7. CELEBRATION: Floating party balloons with ribbons, popper confetti
// -------------------------------------------------------------------------
private fun DrawScope.drawCelebrationTheme(w: Float, h: Float, isDark: Boolean) {
    val strokeColor = if (isDark) Color(0xFFF48FB1).copy(alpha = 0.34f) else Color(0xFFAD1457).copy(alpha = 0.24f)
    val fillColor = if (isDark) Color(0xFFF06292).copy(alpha = 0.14f) else Color(0xFFE91E63).copy(alpha = 0.10f)
    val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Balloon 1 (Left higher)
    val b1Center = Offset(w * 0.34f, h * 0.36f)
    val b1Size = Size(w * 0.22f, h * 0.30f)
    drawOval(fillColor, topLeft = Offset(b1Center.x - b1Size.width / 2, b1Center.y - b1Size.height / 2), size = b1Size)
    drawOval(strokeColor, topLeft = Offset(b1Center.x - b1Size.width / 2, b1Center.y - b1Size.height / 2), size = b1Size, style = stroke)

    // Balloon 1 ribbon tail
    val tail1 = Path().apply {
        moveTo(b1Center.x, b1Center.y + b1Size.height / 2)
        cubicTo(b1Center.x - w * 0.06f, h * 0.60f, b1Center.x + w * 0.06f, h * 0.72f, b1Center.x - w * 0.02f, h * 0.86f)
    }
    drawPath(tail1, strokeColor, style = stroke)

    // Balloon 2 (Right lower)
    val b2Center = Offset(w * 0.62f, h * 0.46f)
    val b2Size = Size(w * 0.20f, h * 0.26f)
    drawOval(fillColor, topLeft = Offset(b2Center.x - b2Size.width / 2, b2Center.y - b2Size.height / 2), size = b2Size)
    drawOval(strokeColor, topLeft = Offset(b2Center.x - b2Size.width / 2, b2Center.y - b2Size.height / 2), size = b2Size, style = stroke)

    // Balloon 2 ribbon tail
    val tail2 = Path().apply {
        moveTo(b2Center.x, b2Center.y + b2Size.height / 2)
        cubicTo(b2Center.x + w * 0.06f, h * 0.68f, b2Center.x - w * 0.04f, h * 0.78f, b2Center.x + w * 0.02f, h * 0.88f)
    }
    drawPath(tail2, strokeColor, style = stroke)

    // Floating confetti stars & circles
    drawCircle(strokeColor, radius = w * 0.02f, center = Offset(w * 0.18f, h * 0.22f))
    drawCircle(fillColor, radius = w * 0.028f, center = Offset(w * 0.52f, h * 0.18f))
    drawCircle(strokeColor, radius = w * 0.018f, center = Offset(w * 0.78f, h * 0.26f))
    drawCircle(strokeColor, radius = w * 0.022f, center = Offset(w * 0.84f, h * 0.60f))
}

// -------------------------------------------------------------------------
// 8. STUDY: Stack of books, graduation cap with tassel
// -------------------------------------------------------------------------
private fun DrawScope.drawStudyTheme(w: Float, h: Float, isDark: Boolean) {
    val strokeColor = if (isDark) Color(0xFF9FA8DA).copy(alpha = 0.34f) else Color(0xFF283593).copy(alpha = 0.24f)
    val fillColor = if (isDark) Color(0xFF7986CB).copy(alpha = 0.14f) else Color(0xFF3F51B5).copy(alpha = 0.10f)
    val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Book 1 (Bottom)
    drawRoundRect(
        color = fillColor,
        topLeft = Offset(w * 0.16f, h * 0.72f),
        size = Size(w * 0.68f, h * 0.14f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = strokeColor,
        topLeft = Offset(w * 0.16f, h * 0.72f),
        size = Size(w * 0.68f, h * 0.14f),
        cornerRadius = CornerRadius(4f, 4f),
        style = stroke
    )
    drawLine(strokeColor, Offset(w * 0.26f, h * 0.72f), Offset(w * 0.26f, h * 0.86f), strokeWidth = 1.8f)

    // Book 2 (Middle)
    drawRoundRect(
        color = fillColor,
        topLeft = Offset(w * 0.22f, h * 0.58f),
        size = Size(w * 0.58f, h * 0.14f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = strokeColor,
        topLeft = Offset(w * 0.22f, h * 0.58f),
        size = Size(w * 0.58f, h * 0.14f),
        cornerRadius = CornerRadius(4f, 4f),
        style = stroke
    )
    drawLine(strokeColor, Offset(w * 0.32f, h * 0.58f), Offset(w * 0.32f, h * 0.72f), strokeWidth = 1.8f)

    // Graduation mortarboard diamond cap
    val capDiamond = Path().apply {
        moveTo(w * 0.50f, h * 0.24f)
        lineTo(w * 0.78f, h * 0.36f)
        lineTo(w * 0.50f, h * 0.48f)
        lineTo(w * 0.22f, h * 0.36f)
        close()
    }
    drawPath(capDiamond, fillColor)
    drawPath(capDiamond, strokeColor, style = stroke)

    // Cap skullcap base & tassel
    val capBase = Path().apply {
        moveTo(w * 0.34f, h * 0.42f)
        lineTo(w * 0.34f, h * 0.52f)
        cubicTo(w * 0.42f, h * 0.56f, w * 0.58f, h * 0.56f, w * 0.66f, h * 0.52f)
        lineTo(w * 0.66f, h * 0.42f)
    }
    drawPath(capBase, strokeColor, style = stroke)

    // Tassel string hanging down
    drawLine(strokeColor, Offset(w * 0.50f, h * 0.36f), Offset(w * 0.78f, h * 0.46f), strokeWidth = 1.8f)
    drawCircle(strokeColor, radius = w * 0.02f, center = Offset(w * 0.78f, h * 0.48f))
}

// -------------------------------------------------------------------------
// 9. FOCUS: Zen stone balance stack, blooming lotus flower, water ripples
// -------------------------------------------------------------------------
private fun DrawScope.drawFocusTheme(w: Float, h: Float, isDark: Boolean) {
    val strokeColor = if (isDark) Color(0xFF80CBC4).copy(alpha = 0.34f) else Color(0xFF00695C).copy(alpha = 0.24f)
    val fillColor = if (isDark) Color(0xFF4DB6AC).copy(alpha = 0.14f) else Color(0xFF00897B).copy(alpha = 0.10f)
    val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Bottom ripple rings
    drawArc(
        color = strokeColor,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.12f, h * 0.74f),
        size = Size(w * 0.76f, h * 0.18f),
        style = Stroke(width = 1.4f)
    )

    // Zen stone 1 (Bottom large)
    drawOval(
        color = fillColor,
        topLeft = Offset(w * 0.24f, h * 0.68f),
        size = Size(w * 0.52f, h * 0.16f)
    )
    drawOval(
        color = strokeColor,
        topLeft = Offset(w * 0.24f, h * 0.68f),
        size = Size(w * 0.52f, h * 0.16f),
        style = stroke
    )

    // Zen stone 2 (Middle medium)
    drawOval(
        color = fillColor,
        topLeft = Offset(w * 0.32f, h * 0.54f),
        size = Size(w * 0.36f, h * 0.15f)
    )
    drawOval(
        color = strokeColor,
        topLeft = Offset(w * 0.32f, h * 0.54f),
        size = Size(w * 0.36f, h * 0.15f),
        style = stroke
    )

    // Zen stone 3 (Top small)
    drawOval(
        color = fillColor,
        topLeft = Offset(w * 0.38f, h * 0.42f),
        size = Size(w * 0.24f, h * 0.13f)
    )
    drawOval(
        color = strokeColor,
        topLeft = Offset(w * 0.38f, h * 0.42f),
        size = Size(w * 0.24f, h * 0.13f),
        style = stroke
    )

    // Blooming lotus petals at the very top
    val centerPetal = Path().apply {
        moveTo(w * 0.50f, h * 0.42f)
        cubicTo(w * 0.44f, h * 0.32f, w * 0.46f, h * 0.20f, w * 0.50f, h * 0.16f)
        cubicTo(w * 0.54f, h * 0.20f, w * 0.56f, h * 0.32f, w * 0.50f, h * 0.42f)
    }
    drawPath(centerPetal, fillColor)
    drawPath(centerPetal, strokeColor, style = stroke)

    val leftPetal = Path().apply {
        moveTo(w * 0.50f, h * 0.42f)
        cubicTo(w * 0.40f, h * 0.36f, w * 0.34f, h * 0.26f, w * 0.38f, h * 0.22f)
        cubicTo(w * 0.44f, h * 0.26f, w * 0.48f, h * 0.34f, w * 0.50f, h * 0.42f)
    }
    drawPath(leftPetal, strokeColor, style = stroke)

    val rightPetal = Path().apply {
        moveTo(w * 0.50f, h * 0.42f)
        cubicTo(w * 0.60f, h * 0.36f, w * 0.66f, h * 0.26f, w * 0.62f, h * 0.22f)
        cubicTo(w * 0.56f, h * 0.26f, w * 0.52f, h * 0.34f, w * 0.50f, h * 0.42f)
    }
    drawPath(rightPetal, strokeColor, style = stroke)
}
