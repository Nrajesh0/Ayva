package com.focusbyrj.app.util.crypto

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.note.MediaRetirement
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34], application = Application::class)
class EncryptedMediaReadSafetyTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var directory: File
    @Before fun setup() {
        MediaRetirement.initialize(app)
        directory = File(app.cacheDir, "authenticated-read-${UUID.randomUUID()}").apply { mkdirs() }
    }
    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun authenticatedReadsPreserveBytesAndNeverCreatePlaintextFiles() {
        val file = File(directory, "image.enc")
        val original = ByteArray(300_000) { (it * 17).toByte() }
        EncryptedMediaStorage.writeEncryptedBytes(file, original)
        val encrypted = file.readBytes()
        assertArrayEquals(original, EncryptedMediaStorage.readDecryptedBytes(file))
        EncryptedMediaStorage.openDecryptedInputStream(file)!!.use { assertArrayEquals(original, it.readBytes()) }
        assertArrayEquals(encrypted, file.readBytes())
        assertEquals(setOf(file.name), directory.list()!!.toSet())
    }

    @Test fun damagedHeaderIvPayloadTagTruncationAndAppendCannotExposePlaintext() {
        val file = File(directory, "tamper.enc")
        EncryptedMediaStorage.writeEncryptedBytes(file, ByteArray(4000) { it.toByte() })
        val original = file.readBytes()
        val variants = listOf(0, 14, 100, original.lastIndex).map { index ->
            original.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
        } + listOf(original.copyOf(8), original.copyOf(original.size - 1), original + byteArrayOf(0))
        for (damaged in variants) {
            file.writeBytes(damaged)
            assertNull(EncryptedMediaStorage.readDecryptedBytes(file))
            assertNull("Authentication must finish before a stream is returned", EncryptedMediaStorage.openDecryptedInputStream(file))
            assertArrayEquals(damaged, file.readBytes())
            assertEquals(setOf(file.name), directory.list()!!.toSet())
        }
    }

    @Test fun smallAuthenticatedSnapshotSurvivesSourceRemovalAndRejectsReadsAfterClose() {
        val file = File(directory, "snapshot.enc")
        val original = "Synthetic authenticated image bytes".toByteArray()
        EncryptedMediaStorage.writeEncryptedBytes(file, original)
        val stream = EncryptedMediaStorage.openDecryptedInputStream(file)!!
        assertTrue(file.delete())
        assertArrayEquals(original, stream.readBytes())
        stream.close(); stream.close()
        try { stream.read(); fail("Closed media cannot expose its old buffer") } catch (_: IOException) { }
        try { stream.read(ByteArray(10)); fail("Closed bulk reads must fail") } catch (_: IOException) { }
    }

    @Test fun largeStreamingSnapshotSurvivesAtomicReplacementAndRejectsTamperingBeforeRead() {
        val file = File(directory, "large.enc")
        val original = ByteArray(9 * 1024 * 1024) { (it * 23).toByte() }
        EncryptedMediaStorage.writeEncryptedBytes(file, original)
        val encrypted = file.readBytes()
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        file.writeBytes(encrypted)
        assertNull(EncryptedMediaStorage.openDecryptedInputStream(file))
        EncryptedMediaStorage.writeEncryptedBytes(file, original)
        val stream = EncryptedMediaStorage.openDecryptedInputStream(file)!!
        val replacement = "Replacement synthetic image".toByteArray()
        EncryptedMediaStorage.writeEncryptedBytes(file, replacement)
        stream.use { assertArrayEquals(original, it.readBytes()) }
        assertArrayEquals(replacement, EncryptedMediaStorage.readDecryptedBytes(file))
    }

    @Test fun emptyAuthenticatedMediaIsValidButOversizedByteLoadsAreRefused() {
        val file = File(directory, "bounds.enc")
        EncryptedMediaStorage.writeEncryptedBytes(file, byteArrayOf())
        assertArrayEquals(byteArrayOf(), EncryptedMediaStorage.readDecryptedBytes(file))
        EncryptedMediaStorage.openDecryptedInputStream(file)!!.use { assertEquals(-1, it.read()) }
        RandomAccessFile(file, "rw").use { it.setLength(EncryptedMediaStorage.MAX_DECRYPT_BYTES_LIMIT + 1) }
        assertNull(EncryptedMediaStorage.readDecryptedBytes(file))
        assertEquals(EncryptedMediaStorage.MAX_DECRYPT_BYTES_LIMIT + 1, file.length())
    }
}
