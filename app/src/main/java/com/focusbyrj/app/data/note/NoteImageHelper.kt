/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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

    const val MAX_IMAGE_DIMENSION: Int = 1920
    const val COMPRESSION_QUALITY: Int = 80

    fun processAndSaveImage(context: Context, contentUri: Uri): String? {
        val ownCapture = contentUri.authority == "${context.packageName}.fileprovider" && contentUri.pathSegments.firstOrNull() == "note_capture"
        if (!ownCapture) return processImage(context, contentUri, false)
        val name = contentUri.lastPathSegment ?: return null
        if (!name.matches(Regex("[a-f0-9-]{36}\\.jpg"))) return null
        val file = File(File(context.cacheDir, "note_capture"), name)
        return try { processCapturedImage(context, file) } catch (_: Exception) { null } finally { file.delete() }
    }

    internal fun processCapturedImage(context: Context, file: File): String? {
        val root = File(context.cacheDir, "note_capture").canonicalFile
        require(file.canonicalFile.parentFile == root && file.isFile)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return processImage(context, uri, true)
    }

    @Synchronized
    private fun processImage(context: Context, contentUri: Uri, trustedCapture: Boolean): String? {
        var rawBitmap: Bitmap? = null
        var orientedBitmap: Bitmap? = null
        var finalBitmap: Bitmap? = null
        return try {
            val scheme = contentUri.scheme?.lowercase()
            if (scheme != "content" && scheme != "android.resource") {
                android.util.Log.w("NoteImageHelper", "Rejected unsafe URI scheme: $scheme")
                return null
            }
            val authority = contentUri.authority?.lowercase().orEmpty()
            if (!trustedCapture && authority.contains("focusbyrj") && authority.contains("fileprovider")) {
                android.util.Log.w("NoteImageHelper", "Rejected self-referential FileProvider URI: $contentUri")
                return null
            }
            val mimeType = try {
                context.contentResolver.getType(contentUri)?.lowercase()
            } catch (_: Throwable) { null }
            if (mimeType != null && !mimeType.startsWith("image/") && mimeType != "application/octet-stream") {
                android.util.Log.w("NoteImageHelper", "Rejected non-image MIME type: $mimeType")
                return null
            }
            // Do not silently flatten an animation into a still image.
            if (mimeType == "image/gif") return null
            val descriptorSize = try {
                context.contentResolver.openFileDescriptor(contentUri, "r")?.use { it.statSize } ?: -1L
            } catch (_: Throwable) { -1L }
            if (descriptorSize > 50 * 1024 * 1024L) {
                android.util.Log.w("NoteImageHelper", "Rejected oversized image stream: $descriptorSize bytes")
                return null
            }

            val imagesDir = File(context.filesDir, "keep_images").apply { if (!exists()) mkdirs() }

            // 1. Read EXIF orientation before decoding
            val orientation = try {
                context.contentResolver.openInputStream(contentUri)?.use { input ->
                    val exif = ExifInterface(input)
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Throwable) {
                ExifInterface.ORIENTATION_NORMAL
            }

            // 2. Decode bounds only to calculate sample size
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(contentUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }

            if (boundsOptions.outMimeType == "image/gif") return null
            val lossless = boundsOptions.outMimeType in setOf("image/png", "image/webp")
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0 || origWidth > 12000 || origHeight > 12000) {
                android.util.Log.w("NoteImageHelper", "Rejected image with invalid/excessive dimensions: ${origWidth}x${origHeight}")
                return null
            }
            val maxDim = MAX_IMAGE_DIMENSION

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
            rawBitmap = context.contentResolver.openInputStream(contentUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return null

            // 4. Adjust orientation if required
            val nonNullRaw = rawBitmap!!
            orientedBitmap = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(nonNullRaw, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(nonNullRaw, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(nonNullRaw, 270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(nonNullRaw, horizontal = true)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(nonNullRaw, horizontal = false)
                ExifInterface.ORIENTATION_TRANSPOSE -> transformBitmap(nonNullRaw, 270f, true)
                ExifInterface.ORIENTATION_TRANSVERSE -> transformBitmap(nonNullRaw, 90f, true)
                else -> nonNullRaw
            }

            // Clamp max dimension to 1920 if sampleSize left it slightly larger
            val nonNullOriented = orientedBitmap!!
            finalBitmap = if (nonNullOriented.width > maxDim || nonNullOriented.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / nonNullOriented.width, maxDim.toFloat() / nonNullOriented.height)
                val targetW = (nonNullOriented.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (nonNullOriented.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(nonNullOriented, targetW, targetH, true)
            } else {
                nonNullOriented
            }

            // Photos use JPEG; alpha and PNG/WebP imports use lossless PNG. Both are encrypted.
            val usePng = lossless || finalBitmap!!.hasAlpha()
            val outputFile = File(imagesDir, "img_${UUID.randomUUID()}.${if (usePng) "png" else "jpg"}")
            val byteStream = java.io.ByteArrayOutputStream()
            check(finalBitmap!!.compress(if (usePng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, byteStream))
            val imageBytes = byteStream.toByteArray()

            try {
                com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(outputFile, imageBytes)
            } finally {
                imageBytes.fill(0)
            }

            outputFile.absolutePath
        } catch (t: Throwable) {
            android.util.Log.w("NoteImageHelper", "Image import failed; original retained")
            null
        } finally {
            // Release every owned bitmap once, including the common raw==oriented,
            // resized-final case. Input bitmaps in processAndSaveBitmap remain caller-owned.
            finalBitmap?.recycle()
            if (orientedBitmap !== finalBitmap) orientedBitmap?.recycle()
            if (rawBitmap !== finalBitmap && rawBitmap !== orientedBitmap) rawBitmap?.recycle()
        }
    }

    /**
     * Processes and compresses a raw in-memory Bitmap (e.g., from camera or sketch pad)
     * using the 1920px limit, lossless PNG for alpha or JPEG quality 80 for opaque images,
     * and AES-256-GCM disk encryption.
     */
    @Synchronized
    fun processAndSaveBitmap(context: Context, bitmap: Bitmap, prefix: String = "img"): String? {
        var scaledBitmap: Bitmap? = null
        return try {
            val imagesDir = File(context.filesDir, "keep_images").apply { if (!exists()) mkdirs() }
            val outputFile = File(imagesDir, "${prefix}_${UUID.randomUUID()}.${if (bitmap.hasAlpha()) "png" else "jpg"}")

            val maxDim = MAX_IMAGE_DIMENSION
            val finalBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                scaledBitmap
            } else {
                bitmap
            }

            val byteStream = java.io.ByteArrayOutputStream()
            check(finalBitmap.compress(if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, byteStream))
            val imageBytes = byteStream.toByteArray()

            try {
                com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(outputFile, imageBytes)
            } finally {
                imageBytes.fill(0)
            }

            outputFile.absolutePath
        } catch (t: Throwable) {
            android.util.Log.w("NoteImageHelper", "Image import failed; original retained")
            null
        } finally {
            if (scaledBitmap != null && scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
        }
    }

    internal fun transformBitmap(src: Bitmap, degrees: Float, mirror: Boolean): Bitmap {
        val matrix = Matrix().apply { if (mirror) postScale(-1f, 1f); postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
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
            if (!NoteMediaManager.isAllowedMediaFile(src, context)) {
                android.util.Log.w("NoteImageHelper", "Refusing to copy file from unverified or restricted path: $originalPath")
                return null
            }
            val imagesDir = File(context.filesDir, "keep_images").apply { if (!exists()) mkdirs() }
            val ext = src.extension.ifEmpty { "jpg" }
            val dest = File(imagesDir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext")
            val rawBytes = com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(src) ?: return null
            com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(dest, rawBytes)
            java.util.Arrays.fill(rawBytes, 0.toByte())
            dest.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun processAndSaveMultipleImages(context: Context, uris: List<Uri>): List<String> {
        val paths = mutableListOf<String>()
        for (uri in uris.take(20)) {
            val savedPath = processAndSaveImage(context, uri)
            if (savedPath != null) {
                paths.add(savedPath)
            }
        }
        return paths
    }

    fun loadBitmapForWidget(context: Context, pathOrUri: String): Bitmap? {
        var rawBytes: ByteArray? = null
        return try {
            val maxDim = 260
            rawBytes = if (pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(pathOrUri))?.use { it.readBytes() }
            } else {
                val file = File(pathOrUri)
                if (!file.exists()) null
                else com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(file)
            }
            if (rawBytes == null || rawBytes.isEmpty()) return null

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)

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
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            rawBytes?.fill(0)
        }
    }
}
