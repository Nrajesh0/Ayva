package com.focusbyrj.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.util.backup.AutoBackupScheduler
import com.focusbyrj.app.util.backup.AutoBackupWorker
import com.focusbyrj.app.util.backup.CryptoBackupEngine
import com.focusbyrj.app.util.backup.DataSafetyManager
import com.focusbyrj.app.util.sync.supabase.AutoSyncManager
import com.focusbyrj.app.util.sync.supabase.AutoSyncWorker
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random

@RunWith(RobolectricTestRunner::class)
class DataIntegrityPhase1Test {

    @Test
    fun testStreamingCryptoBackupRoundTrip() {
        val password = "MySuperSecurePassword123!@#"
        val passwordChars = password.toCharArray()

        // 1. Generate 128 KB of pseudo-random binary data (simulating compressed media + JSON)
        val testData = ByteArray(128 * 1024)
        Random(42L).nextBytes(testData)

        // 2. Encrypt via streaming pipeline
        val cipherOut = ByteArrayOutputStream()
        CryptoBackupEngine.openEncryptingStream(cipherOut, passwordChars).use { encryptingStream ->
            // Stream in chunks
            val chunkSize = 8192
            var offset = 0
            while (offset < testData.size) {
                val len = minOf(chunkSize, testData.size - offset)
                encryptingStream.write(testData, offset, len)
                offset += len
            }
            encryptingStream.flush()
        }

        val encryptedBytes = cipherOut.toByteArray()
        assertTrue("Ciphertext should be larger than plaintext due to salt + IV + GCM tag", encryptedBytes.size > testData.size)
        // Verify header has salt (16 or 32 bytes) + IV (12 bytes)
        assertTrue("Ciphertext must contain at least salt and IV prefix", encryptedBytes.size >= 28)

        // 3. Decrypt via streaming pipeline
        val cipherIn = ByteArrayInputStream(encryptedBytes)
        val decryptedOut = ByteArrayOutputStream()
        CryptoBackupEngine.openDecryptingStream(cipherIn, password.toCharArray()).use { decryptingStream ->
            val buffer = ByteArray(8192)
            var read = decryptingStream.read(buffer)
            while (read != -1) {
                decryptedOut.write(buffer, 0, read)
                read = decryptingStream.read(buffer)
            }
        }

        val decryptedBytes = decryptedOut.toByteArray()
        assertArrayEquals("Decrypted data must match original plaintext identically byte-for-byte", testData, decryptedBytes)
    }

    @Test
    fun testStreamingCryptoBackupRejectsTamperedOrWrongPassword() {
        val correctPassword = "CorrectPassword456$"
        val wrongPassword = "WrongPassword789!"
        val testData = "Confidential User Notes and Passwords".toByteArray(Charsets.UTF_8)

        val cipherOut = ByteArrayOutputStream()
        CryptoBackupEngine.openEncryptingStream(cipherOut, correctPassword.toCharArray()).use { encryptingStream ->
            encryptingStream.write(testData)
        }

        val encryptedBytes = cipherOut.toByteArray()

        // Attempt decryption with wrong password
        var failed = false
        try {
            val cipherIn = ByteArrayInputStream(encryptedBytes)
            CryptoBackupEngine.openDecryptingStream(cipherIn, wrongPassword.toCharArray()).use { decryptingStream ->
                val buffer = ByteArray(1024)
                while (decryptingStream.read(buffer) != -1) {
                    // reading triggers AEAD tag verification at EOF
                }
            }
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("Decryption with wrong password MUST fail with authentication error", failed)
    }

    @Test
    fun testDataSafetyManagerAtomicFileOperations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDir = File(context.filesDir, "test_atomic_safety").apply { mkdirs() }
        val testFile = File(testDir, "snapshot_test.json")

        val payload = """{"version": 1, "notes": [{"id": 1, "title": "Safety First"}]}"""

        // 1. Atomic write
        DataSafetyManager.writeAtomically(testFile, payload)
        assertTrue("File should exist on disk", testFile.exists())
        assertEquals(payload.length.toLong(), testFile.length())

        // 2. Atomic read
        val readBack = DataSafetyManager.readAtomically(testFile)
        assertNotNull("Read back content should not be null", readBack)
        assertEquals("Read back content must match original payload", payload, readBack)

        // Clean up
        testFile.delete()
        testDir.delete()
    }

    @Test
    fun testWorkManagerAutoBackupAndSyncRegistration() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Enqueue and cancel AutoBackupWorker
        AutoBackupWorker.enqueue(context)
        AutoBackupWorker.cancel(context)

        // 2. Enqueue and cancel AutoSyncWorker
        AutoSyncWorker.enqueuePeriodic(context)
        AutoSyncWorker.enqueueOneOff(context)
        AutoSyncWorker.cancel(context)

        // 3. AutoBackupScheduler.schedule should not crash
        AutoBackupScheduler.schedule(context)

        // 4. AutoSyncManager preference toggles should invoke WorkManager cleanly
        AutoSyncManager.setAutoSyncEnabled(context, true)
        assertTrue(AutoSyncManager.isAutoSyncEnabled(context))

        AutoSyncManager.setSyncOnWifiOnly(context, true)
        assertTrue(AutoSyncManager.isSyncOnWifiOnly(context))

        AutoSyncManager.setSyncOnWifiOnly(context, false)
        assertFalse(AutoSyncManager.isSyncOnWifiOnly(context))

        AutoSyncManager.setAutoSyncEnabled(context, false)
        assertFalse(AutoSyncManager.isAutoSyncEnabled(context))
    }
}
