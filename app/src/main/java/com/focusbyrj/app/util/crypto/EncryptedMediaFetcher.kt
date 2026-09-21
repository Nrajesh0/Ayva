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
        val buffer = Buffer().write(decryptedBytes)
        val imageSource = ImageSource(buffer, options.context)

        return SourceResult(
            source = imageSource,
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK
        )
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
