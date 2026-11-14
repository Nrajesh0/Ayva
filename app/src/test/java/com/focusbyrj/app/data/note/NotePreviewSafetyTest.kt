package com.focusbyrj.app.data.note

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import com.focusbyrj.app.util.crypto.NoteThumbnails
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FocusApplication::class, shadows = [])
class NotePreviewSafetyTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusApplication>()

    @Test fun transparentImagesRemainLosslessAndThumbnailsAreEncryptedAndRejectSourceCorruption() = runBlocking {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        bitmap.setPixel(20, 20, Color.RED)
        val path = requireNotNull(NoteImageHelper.processAndSaveBitmap(app, bitmap))
        val source = File(path)
        assertTrue(path.endsWith(".png"))
        val original = requireNotNull(EncryptedMediaStorage.readDecryptedBytes(source))
        EncryptedMediaStorage.readAuthenticatedMedia(source).use { snapshot ->
            assertArrayEquals(original, snapshot.bytes)
            assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256").digest(source.readBytes()), snapshot.cipherSha256)
        }
        val restored = BitmapFactory.decodeByteArray(original, 0, original.size)
        assertEquals(0, Color.alpha(restored.getPixel(0, 0)))
        val a = NoteThumbnails.bytes(source, app)
        val b = NoteThumbnails.bytes(source, app)
        assertArrayEquals(a, b)
        val thumbnail = BitmapFactory.decodeByteArray(a, 0, a.size)
        assertTrue(maxOf(thumbnail.width, thumbnail.height) <= 480)
        val cache = File(app.cacheDir, "encrypted_note_previews").listFiles()!!.filter { it.extension == "enc" }
        assertTrue(cache.isNotEmpty())
        assertTrue(cache.all { EncryptedMediaStorage.isEncrypted(it) })
        val damaged = source.readBytes(); damaged[damaged.size / 2] = (damaged[damaged.size / 2].toInt() xor 1).toByte(); source.writeBytes(damaged)
        assertTrue(runCatching { NoteThumbnails.bytes(source, app) }.isFailure)
        source.delete(); bitmap.recycle(); restored.recycle(); thumbnail.recycle()
        original.fill(0); a.fill(0); b.fill(0)
    }

    @Test fun cameraImportUsesRealEncodingDespiteAnIncorrectProviderMimeAndCleansTheTemporaryFile() {
        val original = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.TRANSPARENT)
        val file = File(File(app.cacheDir, "note_capture").apply { mkdirs() }, "${java.util.UUID.randomUUID()}.jpg")
        file.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val saved = requireNotNull(NoteImageHelper.processAndSaveImage(app, uri))
        assertTrue(saved.endsWith(".png")); assertFalse(file.exists())
        val bytes = requireNotNull(EncryptedMediaStorage.readDecryptedBytes(File(saved)))
        assertEquals("image/png", BitmapFactory.Options().apply { inJustDecodeBounds = true }.let { options -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options); options.outMimeType })
        bytes.fill(0); File(saved).delete(); original.recycle()
    }

    @Test fun abandonedCaptureCleanupNeverTouchesRecentCapturesOrSavedMedia() {
        val dir = File(app.cacheDir, "note_capture").apply { mkdirs() }
        val old = File(dir, "${java.util.UUID.randomUUID()}.jpg").apply { writeBytes(byteArrayOf(1)); setLastModified(System.currentTimeMillis() - 90_000_000L) }
        val recent = File(dir, "${java.util.UUID.randomUUID()}.jpg").apply { writeBytes(byteArrayOf(2)) }
        val saved = File(app.filesDir, "keep_images/saved.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(3)) }
        CaptureCleanupWorker.clean(app)
        assertFalse(old.exists()); assertTrue(recent.exists()); assertTrue(saved.exists())
        recent.delete(); saved.delete()
    }

    @Test fun aMissingCameraOutputDoesNotCrashOrInsertAnExternalUri() {
        val uri = android.net.Uri.parse("content://${app.packageName}.fileprovider/note_capture/${java.util.UUID.randomUUID()}.jpg")
        assertNull(NoteImageHelper.processAndSaveImage(app, uri))
    }

    @Test fun reflectedOrientationTransformsKeepCornerIdentity() {
        val original = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        original.setPixel(0, 0, Color.RED)
        original.setPixel(1, 2, Color.BLUE)
        val transpose = NoteImageHelper.transformBitmap(original, 270f, true)
        assertEquals(3, transpose.width); assertEquals(2, transpose.height)
        assertEquals(Color.RED, transpose.getPixel(0, 0))
        assertEquals(Color.BLUE, transpose.getPixel(2, 1))
        val transverse = NoteImageHelper.transformBitmap(original, 90f, true)
        assertEquals(Color.RED, transverse.getPixel(2, 1))
        assertEquals(Color.BLUE, transverse.getPixel(0, 0))
        transpose.recycle(); transverse.recycle(); original.recycle()
    }
}
