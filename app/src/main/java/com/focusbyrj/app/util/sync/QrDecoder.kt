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

package com.focusbyrj.app.util.sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * QR Code Scanner and Image Decoder utility.
 * Decodes QR code payloads from Bitmaps or InputStreams using ZXing.
 */
object QrDecoder {

    suspend fun decodeFromBitmap(bitmap: Bitmap): String? = withContext(Dispatchers.Default) {
        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = QRCodeReader()
            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
            )
            val result = reader.decode(binaryBitmap, hints)
            result.text
        } catch (_: Exception) {
            null
        }
    }

    suspend fun decodeFromStream(inputStream: InputStream): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return@withContext null
            decodeFromBitmap(bitmap)
        } catch (_: Exception) {
            null
        }
    }
}
