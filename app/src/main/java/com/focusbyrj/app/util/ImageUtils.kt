package com.focusbyrj.app.util

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object ImageUtils {
    private val iconCache = LruCache<String, ImageBitmap>(250)

    fun getAppIcon(pm: PackageManager, packageName: String): ImageBitmap? {
        if (packageName.isBlank()) return null
        iconCache.get(packageName)?.let { return it }

        return try {
            val drawable = pm.getApplicationIcon(packageName)
            val bitmap = drawableToImageBitmap(drawable)
            if (bitmap != null) {
                iconCache.put(packageName, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun drawableToImageBitmap(drawable: Drawable?): ImageBitmap? {
        if (drawable == null) return null
        try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                val b = drawable.bitmap
                if (b.config != Bitmap.Config.HARDWARE && !b.isRecycled) {
                    b.prepareToDraw()
                    return b.asImageBitmap()
                }
            }

            val targetWidth = (if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96).coerceAtMost(96)
            val targetHeight = (if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96).coerceAtMost(96)
            
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, targetWidth, targetHeight)
            drawable.draw(canvas)
            bitmap.prepareToDraw()
            return bitmap.asImageBitmap()
        } catch (e: Exception) {
            return null
        }
    }
}


