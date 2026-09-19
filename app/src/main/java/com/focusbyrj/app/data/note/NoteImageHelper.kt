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

package com.focusbyrj.app.data.note

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object NoteImageHelper {

    fun processAndSaveImage(context: Context, contentUri: Uri): String? {
        return try {
            val imagesDir = File(context.filesDir, "keep_images").apply { if (!exists()) mkdirs() }
            val outputFile = File(imagesDir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")

            // 1. Read EXIF orientation before decoding
            val orientation = try {
                context.contentResolver.openInputStream(contentUri)?.use { input ->
                    val exif = ExifInterface(input)
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            // 2. Decode bounds only to calculate sample size
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(contentUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            val maxDim = 2048

            var sampleSize = 1
            if (origWidth > maxDim || origHeight > maxDim) {
                val halfHeight = origHeight / 2
                val halfWidth = origWidth / 2
                while ((halfHeight / sampleSize) >= maxDim || (halfWidth / sampleSize) >= maxDim) {
                    sampleSize *= 2
                }
            }

            // 3. Decode bitmap with inSampleSize
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = context.contentResolver.openInputStream(contentUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return null

            // 4. Adjust orientation if required
            val orientedBitmap = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(rawBitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(rawBitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(rawBitmap, 270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(rawBitmap, horizontal = true)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(rawBitmap, horizontal = false)
                else -> rawBitmap
            }

            // Clamp max dimension to 2048 if sampleSize left it slightly larger
            val finalBitmap = if (orientedBitmap.width > maxDim || orientedBitmap.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / orientedBitmap.width, maxDim.toFloat() / orientedBitmap.height)
                val targetW = (orientedBitmap.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (orientedBitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(orientedBitmap, targetW, targetH, true)
            } else {
                orientedBitmap
            }

            // 5. Save as high-quality compressed JPEG (85%)
            FileOutputStream(outputFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (rawBitmap != orientedBitmap && rawBitmap != finalBitmap) {
                rawBitmap.recycle()
            }
            if (orientedBitmap != finalBitmap) {
                orientedBitmap.recycle()
            }
            finalBitmap.recycle()

            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
        return try {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } catch (_: Exception) {
            src
        }
    }

    private fun flipBitmap(src: Bitmap, horizontal: Boolean): Bitmap {
        return try {
            val matrix = Matrix().apply {
                postScale(if (horizontal) -1f else 1f, if (horizontal) 1f else -1f)
            }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } catch (_: Exception) {
            src
        }
    }

    fun copyImageFile(context: Context, originalPath: String): String? {
        return try {
            val src = File(originalPath)
            if (!src.exists()) return null
            val imagesDir = File(context.filesDir, "keep_images").apply { if (!exists()) mkdirs() }
            val ext = src.extension.ifEmpty { "jpg" }
            val dest = File(imagesDir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext")
            src.copyTo(dest, overwrite = true)
            dest.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun processAndSaveMultipleImages(context: Context, uris: List<Uri>): List<String> {
        val paths = mutableListOf<String>()
        for (uri in uris) {
            val savedPath = processAndSaveImage(context, uri)
            if (savedPath != null) {
                paths.add(savedPath)
            }
        }
        return paths
    }

    fun loadBitmapForWidget(context: Context, pathOrUri: String): Bitmap? {
        return try {
            val maxDim = 260
            fun openStream(): java.io.InputStream? {
                return try {
                    if (pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://")) {
                        context.contentResolver.openInputStream(android.net.Uri.parse(pathOrUri))
                    } else {
                        val file = File(pathOrUri)
                        if (file.exists()) file.inputStream() else null
                    }
                } catch (_: Exception) {
                    null
                }
            }

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream()?.use { BitmapFactory.decodeStream(it, null, boundsOptions) } ?: return null

            var sampleSize = 1
            if (boundsOptions.outWidth > maxDim || boundsOptions.outHeight > maxDim) {
                val halfHeight = boundsOptions.outHeight / 2
                val halfWidth = boundsOptions.outWidth / 2
                while ((halfHeight / sampleSize) >= maxDim || (halfWidth / sampleSize) >= maxDim) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            openStream()?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
