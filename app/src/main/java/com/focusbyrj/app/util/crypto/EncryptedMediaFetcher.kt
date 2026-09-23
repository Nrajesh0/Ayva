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

package com.focusbyrj.app.util.crypto

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import java.io.File

/**
 * Custom Coil Fetcher that decrypts encrypted local images on-the-fly in memory.
 * This guarantees that plain image bytes never touch flash storage.
 */
class EncryptedMediaFetcher(
    private val data: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val decryptedBytes = EncryptedMediaStorage.readDecryptedBytes(data) ?: return null
        return try {
            val mimeType = detectMimeType(decryptedBytes, data.name)
            val buffer = Buffer().write(decryptedBytes)
            val imageSource = ImageSource(buffer, options.context)

            SourceResult(
                source = imageSource,
                mimeType = mimeType,
                dataSource = DataSource.DISK
            )
        } finally {
            java.util.Arrays.fill(decryptedBytes, 0.toByte())
        }
    }

    private fun detectMimeType(bytes: ByteArray, filename: String): String {
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "image/jpeg"
        }
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
            return "image/png"
        }
        if (bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            return "image/webp"
        }
        if (bytes.size >= 4 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == '8'.code.toByte()) {
            return "image/gif"
        }
        return when {
            filename.endsWith(".png", ignoreCase = true) -> "image/png"
            filename.endsWith(".webp", ignoreCase = true) -> "image/webp"
            filename.endsWith(".gif", ignoreCase = true) -> "image/gif"
            else -> "image/jpeg"
        }
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.absolutePath.contains("keep_images") || EncryptedMediaStorage.isEncrypted(data)) {
                return EncryptedMediaFetcher(data, options)
            }
            return null
        }
    }
}
