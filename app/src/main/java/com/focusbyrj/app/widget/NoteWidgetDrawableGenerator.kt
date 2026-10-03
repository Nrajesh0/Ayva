/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.focusbyrj.app.ui.screens.notes.KeepColorPalette

object NoteWidgetDrawableGenerator {

    fun getWidgetColors(
        context: Context,
        config: NoteWidgetConfig,
        noteColorKey: String?,
        isSystemDark: Boolean
    ): BackgroundColors {
        val hasKeepColor = config.matchNoteColor && !noteColorKey.isNullOrEmpty() && noteColorKey != "default"
        val keepTheme = if (hasKeepColor) KeepColorPalette.getColor(noteColorKey!!) else null
        val isDark = if (hasKeepColor) isSystemDark else config.theme.isDark

        val bgColorInt: Int
        val borderColorInt: Int
        val primaryTextInt: Int
        val secondaryTextInt: Int
        val pillBgInt: Int
        val pillBorderInt: Int
        val accentColorInt: Int

        val alpha = ((config.opacityPercent / 100f) * 255).toInt().coerceIn(0, 255)

        if (hasKeepColor && keepTheme != null) {
            val bgCompose = keepTheme.getBackgroundColor(isDark)
            val borderCompose = keepTheme.getBorderColor(isDark)
            val textCompose = keepTheme.getTextColor(isDark)

            bgColorInt = Color.argb(
                alpha,
                (bgCompose.red * 255).toInt(),
                (bgCompose.green * 255).toInt(),
                (bgCompose.blue * 255).toInt()
            )
            borderColorInt = Color.argb(
                (borderCompose.alpha * 255).toInt().coerceAtLeast(35),
                (borderCompose.red * 255).toInt(),
                (borderCompose.green * 255).toInt(),
                (borderCompose.blue * 255).toInt()
            )
            primaryTextInt = Color.argb(
                (textCompose.alpha * 255).toInt(),
                (textCompose.red * 255).toInt(),
                (textCompose.green * 255).toInt(),
                (textCompose.blue * 255).toInt()
            )
            secondaryTextInt = if (isDark) Color.parseColor("#9AA0A6") else Color.parseColor("#5F6368")
            pillBgInt = if (isDark) Color.argb(25, 255, 255, 255) else Color.argb(18, 0, 0, 0)
            pillBorderInt = if (isDark) Color.argb(35, 255, 255, 255) else Color.argb(28, 0, 0, 0)
            accentColorInt = config.accentColorInt
        } else {
            val baseColor = Color.parseColor(config.theme.baseColorHex)
            bgColorInt = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))

            borderColorInt = if (isDark) {
                if (config.theme == WidgetTheme.OLED) Color.parseColor("#26292B") else Color.parseColor("#2E3338")
            } else {
                Color.parseColor("#D6D9DC")
            }

            primaryTextInt = if (isDark) Color.parseColor("#F0F2F5") else Color.parseColor("#1F1F24")
            secondaryTextInt = if (isDark) Color.parseColor("#9AA0A6") else Color.parseColor("#6B7280")
            pillBgInt = if (isDark) Color.argb(25, 255, 255, 255) else Color.argb(16, 0, 0, 0)
            pillBorderInt = if (isDark) Color.argb(35, 255, 255, 255) else Color.argb(25, 0, 0, 0)
            accentColorInt = config.accentColorInt
        }

        // Create a minimal placeholder bitmap (recycled immediately — actual background bitmap is
        // built by createWidgetBackground which always replaces this field). BATCH-9-P2-012.
        val emptyBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val result = BackgroundColors(
            bitmap = emptyBitmap,
            bgColor = bgColorInt,
            borderColor = borderColorInt,
            primaryTextColor = primaryTextInt,
            secondaryTextColor = secondaryTextInt,
            pillBgColor = pillBgInt,
            pillBorderColor = pillBorderInt,
            accentColor = accentColorInt
        )
        emptyBitmap.recycle()
        return result
    }

    fun createWidgetBackground(
        context: Context,
        config: NoteWidgetConfig,
        noteColorKey: String?,
        isSystemDark: Boolean,
        targetWidthDp: Int = 320,
        targetHeightDp: Int = 200
    ): BackgroundColors {
        val colors = getWidgetColors(context, config, noteColorKey, isSystemDark)

        // Crisp, borderless background bitmap with anti-aliasing
        val w = targetWidthDp.coerceIn(240, 480)
        val h = targetHeightDp.coerceIn(160, 360)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.bgColor
            style = Paint.Style.FILL
        }

        val cornerRadiusPx = config.cornerRadiusDp.toFloat().coerceAtLeast(0f)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())

        if (cornerRadiusPx <= 0f) {
            canvas.drawRect(rect, fillPaint)
        } else {
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, fillPaint)
        }

        return colors.copy(bitmap = bitmap)
    }

    private data class CheckboxKey(
        val isChecked: Boolean,
        val accentColor: Int,
        val secondaryColor: Int,
        val isDark: Boolean
    )

    // Bounded LRU cache for checkbox bitmaps — max 32 distinct style combinations (BATCH-9-P2-013)
    private val checkboxCache: MutableMap<CheckboxKey, Bitmap> = object : java.util.LinkedHashMap<CheckboxKey, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CheckboxKey, Bitmap>?): Boolean {
            if (size > 32) {
                eldest?.value?.takeIf { !it.isRecycled }?.recycle()
                return true
            }
            return false
        }
    }

    fun createCheckboxBitmap(
        isChecked: Boolean,
        accentColorInt: Int,
        secondaryTextColorInt: Int,
        isDark: Boolean
    ): Bitmap {
        val key = CheckboxKey(isChecked, accentColorInt, secondaryTextColorInt, isDark)
        val cached = checkboxCache[key]
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val margin = size * 0.18f
        val rect = RectF(margin, margin, size - margin, size - margin)
        val rx = size * 0.12f

        if (isChecked) {
            // Filled rounded square with checkmark
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColorInt
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(rect, rx, rx, fillPaint)

            val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDark) Color.parseColor("#121516") else Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 3.2f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            val path = Path().apply {
                moveTo(size * 0.32f, size * 0.50f)
                lineTo(size * 0.44f, size * 0.63f)
                lineTo(size * 0.68f, size * 0.37f)
            }
            canvas.drawPath(path, checkPaint)
        } else {
            // Unchecked outline rounded square
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryTextColorInt
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            canvas.drawRoundRect(rect, rx, rx, strokePaint)
        }

        checkboxCache[key] = bitmap
        return bitmap
    }

    data class BackgroundColors(
        val bitmap: Bitmap,
        val bgColor: Int,
        val borderColor: Int,
        val primaryTextColor: Int,
        val secondaryTextColor: Int,
        val pillBgColor: Int,
        val pillBorderColor: Int,
        val accentColor: Int
    )
}
