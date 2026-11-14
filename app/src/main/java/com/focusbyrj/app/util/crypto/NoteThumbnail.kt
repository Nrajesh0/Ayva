package com.focusbyrj.app.util.crypto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** Explicit preview model; full-screen requests continue to use original encrypted files. */
data class NoteThumbnail(val file: File, val maxEdge: Int = 480)

internal object NoteThumbnails {
    private val permits = Semaphore(2)
    suspend fun bytes(source: File, context: android.content.Context, maxEdge: Int = 480): ByteArray = permits.withPermit {
        withContext(Dispatchers.IO) {
            require(com.focusbyrj.app.data.note.NoteMediaManager.isAllowedMediaFile(source, context))
            require(maxEdge in setOf(480, 960))
            require(source.isFile && source.length() <= EncryptedMediaStorage.MAX_DECRYPT_BYTES_LIMIT && EncryptedMediaStorage.isEncrypted(source))
            // A complete ciphertext fingerprint detects same-size corruption as well as
            // replacement. Cache hits avoid full decryption/bitmap decoding, not integrity.
            val canonical = source.canonicalPath
            fun keyFor(cipherHash: ByteArray): String = MessageDigest.getInstance("SHA-256").apply {
                update("preview-v2/$maxEdge/$canonical".toByteArray()); update(cipherHash)
            }.digest().joinToString("") { "%02x".format(it) }
            val observedHash = source.inputStream().use { input ->
                val hash = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(32 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
                hash.digest()
            }
            val key = keyFor(observedHash)
            val dir = File(context.cacheDir, "encrypted_note_previews").apply { mkdirs() }
            val cached = File(dir, "$key.enc")
            (if (cached.exists()) EncryptedMediaStorage.readDecryptedBytes(cached) else null)?.let { stored ->
                // The binding is inside the authenticated payload too: swapping valid cache
                // files from different notes cannot substitute another image.
                val prefix = "$key\n".toByteArray()
                try { if (stored.size > prefix.size && stored.copyOfRange(0, prefix.size).contentEquals(prefix))
                    return@withContext stored.copyOfRange(prefix.size, stored.size) }
                finally { stored.fill(0) }
            }
            val snapshot = EncryptedMediaStorage.readAuthenticatedMedia(source)
            val raw = snapshot.bytes
            // The source may have been atomically replaced since the lookup. Bind the
            // generated preview to the bytes actually authenticated, never the earlier hash.
            val authenticatedKey = keyFor(snapshot.cipherSha256)
            val authenticatedCache = File(dir, "$authenticatedKey.enc")
            var decoded: Bitmap? = null
            var resized: Bitmap? = null
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
                require(bounds.outWidth in 1..12000 && bounds.outHeight in 1..12000)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2
                decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Image cannot be decoded")
                val bitmap = checkNotNull(decoded)
                val ratio = minOf(1f, maxEdge.toFloat() / maxOf(bitmap.width, bitmap.height))
                resized = if (ratio < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true) else bitmap
                val bytes = ByteArrayOutputStream().use { out ->
                    check(checkNotNull(resized).compress(if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 85, out)); out.toByteArray()
                }
                val payload = "$authenticatedKey\n".toByteArray() + bytes
                try {
                    // Cache failures must not prevent viewing an authenticated original.
                    runCatching { EncryptedMediaStorage.writeEncryptedBytes(authenticatedCache, payload) }
                    var retainedBytes = 0L
                    dir.listFiles()?.filter { it.extension == "enc" }?.sortedByDescending { it.lastModified() }?.forEachIndexed { index, entry ->
                        retainedBytes += entry.length()
                        if (index >= 128 || retainedBytes > 32L * 1024 * 1024) entry.delete()
                    }
                } finally { payload.fill(0) }
                bytes
            } finally {
                snapshot.close()
                if (resized !== decoded) resized?.recycle()
                decoded?.recycle()
            }
        }
    }
}

class NoteThumbnailFetcher(private val data: NoteThumbnail, private val options: Options) : Fetcher {
    override suspend fun fetch(): SourceResult {
        val bytes = NoteThumbnails.bytes(data.file, options.context, data.maxEdge)
        return try { SourceResult(ImageSource(Buffer().write(bytes), options.context), null, DataSource.DISK) }
        finally { bytes.fill(0) }
    }
    class Factory : Fetcher.Factory<NoteThumbnail> {
        override fun create(data: NoteThumbnail, options: Options, imageLoader: ImageLoader) = NoteThumbnailFetcher(data, options)
    }
}
