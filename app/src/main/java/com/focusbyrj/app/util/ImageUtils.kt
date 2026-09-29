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

    fun clearCache() {
        iconCache.evictAll()
    }

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
            val targetWidth = (if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96).coerceIn(1, 96)
            val targetHeight = (if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96).coerceIn(1, 96)

            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                val b = drawable.bitmap
                if (!b.isRecycled) {
                    if (b.config == Bitmap.Config.HARDWARE) {
                        val softwareCopy = try {
                            b.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (_: Throwable) {
                            null
                        }
                        if (softwareCopy != null) {
                            val scaled = if (softwareCopy.width > 96 || softwareCopy.height > 96) {
                                Bitmap.createScaledBitmap(softwareCopy, targetWidth, targetHeight, true)
                            } else {
                                softwareCopy
                            }
                            scaled.prepareToDraw()
                            return scaled.asImageBitmap()
                        }
                    } else if (b.width <= 96 && b.height <= 96) {
                        b.prepareToDraw()
                        return b.asImageBitmap()
                    } else {
                        val scaled = Bitmap.createScaledBitmap(b, targetWidth, targetHeight, true)
                        scaled.prepareToDraw()
                        return scaled.asImageBitmap()
                    }
                }
            }

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


