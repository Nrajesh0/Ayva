package com.focusbyrj.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

object WidgetDrawableGenerator {

    /**
     * Small, fast bitmap (160x160) stretched with fitXY.
     * Keeps IPC parcel size minimal (< 100KB) while rendering crisp rounded corners and stroke.
     */
    fun createWidgetBackground(context: Context, config: WidgetConfig): Bitmap {
        val size = 160
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.backgroundColorInt
            style = Paint.Style.FILL
        }

        // Clean, solid thin line border (crisp and polished, not thick or transparent)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (config.theme.isDark) {
                if (config.theme == WidgetTheme.OLED) Color.parseColor("#26292B") else Color.parseColor("#2E3338")
            } else {
                Color.parseColor("#D6D9DC")
            }
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }

        // Scale dp to bitmap dimensions (160px canvas representing ~360dp widget)
        val radius = (config.cornerRadiusDp.toFloat() * 0.45f).coerceIn(0f, 24f)
        val rect = RectF(0.6f, 0.6f, size - 0.6f, size - 0.6f)

        if (radius <= 0f) {
            canvas.drawRect(rect, paint)
        } else {
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        return bitmap
    }

    /**
     * Clean, flat item row with a subtle indented bottom divider.
     * Removes the chunky filled card lozenges.
     */
    fun createItemBackground(context: Context, config: WidgetConfig): Bitmap {
        val width = 120
        val height = 40
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Subtle indented bottom divider for a professional list appearance
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val dividerAlpha = if (config.theme.isDark) 18 else 24
            val col = if (config.theme.isDark) 255 else 0
            color = Color.argb(dividerAlpha, col, col, col)
            style = Paint.Style.FILL
        }
        // Indented past the checkbox
        canvas.drawRect(34f, height - 1f, width.toFloat(), height.toFloat(), dividerPaint)

        return bitmap
    }

    /**
     * '+' Add Button Bitmap (48x48)
     */
    fun createAddButton(context: Context, config: WidgetConfig): Bitmap {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.accentColorInt
            style = Paint.Style.FILL
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, bgPaint)

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (config.accent == WidgetAccent.MONOCHROME && !config.theme.isDark) Color.WHITE else Color.parseColor("#121516")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
        }
        val center = radius
        val length = 8f
        canvas.drawLine(center - length, center, center + length, center, iconPaint)
        canvas.drawLine(center, center - length, center, center + length, iconPaint)

        return bitmap
    }

    fun createActiveTabPill(context: Context, config: WidgetConfig): Bitmap {
        val width = 80
        val height = 32
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.accentColorInt
            style = Paint.Style.FILL
        }
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = 8f
        canvas.drawRoundRect(rect, radius, radius, paint)

        return bitmap
    }

    fun createInactiveTabPill(context: Context, config: WidgetConfig): Bitmap {
        val width = 80
        val height = 32
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val alpha = if (config.theme.isDark) 24 else 35
            color = Color.argb(alpha, 128, 128, 128)
            style = Paint.Style.FILL
        }
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = 8f
        canvas.drawRoundRect(rect, radius, radius, paint)

        return bitmap
    }

    fun createCountBadge(context: Context, config: WidgetConfig): Bitmap {
        val width = 40
        val height = 24
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.TRANSPARENT // No glassy background, minimal text only
            style = Paint.Style.FILL
        }
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = 6f
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        return bitmap
    }

    fun createCheckbox(context: Context, config: WidgetConfig, isChecked: Boolean): Bitmap {
        val size = 36
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size / 2f

        if (isChecked) {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = config.accentColorInt
                style = Paint.Style.FILL
            }
            canvas.drawCircle(radius, radius, radius - 2f, fillPaint)

            val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (config.accent == WidgetAccent.MONOCHROME && !config.theme.isDark) Color.WHITE else Color.parseColor("#121516")
                style = Paint.Style.STROKE
                strokeWidth = 3.2f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = android.graphics.Path().apply {
                moveTo(radius - 5.5f, radius)
                lineTo(radius - 1.5f, radius + 4f)
                lineTo(radius + 6f, radius - 4f)
            }
            canvas.drawPath(path, checkPaint)
        } else {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val borderAlpha = if (config.theme.isDark) 100 else 130
                val col = if (config.theme.isDark) 200 else 110
                color = Color.argb(borderAlpha, col, col, col)
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawCircle(radius, radius, radius - 2.5f, borderPaint)
        }

        return bitmap
    }
}
