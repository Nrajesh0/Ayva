package com.focusbyrj.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.DatabaseKeyProvider
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pre-release contract: current formats round-trip, static seeds and plaintext fail closed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34], application = Application::class)
class WorkPackage3SecurityTest {
    @Test fun fixedLegacySeedCannotDecryptDatabaseKeyOrMedia() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val iv = ByteArray(12) { it.toByte() }
        fun encrypt(seed: String): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(seed.toByteArray()), "AES"), GCMParameterSpec(128, iv))
            doFinal("Synthetic private value".toByteArray())
        }
        assertNull(DatabaseKeyProvider.decryptPassphrase(encrypt("focus_notes_sqlcipher_software_seed_v1"), iv))
        val file = File(context.cacheDir, "unsupported-media.bin")
        val bytes = "FOC_ENC_V1:".toByteArray() + iv + encrypt("focus_media_storage_software_seed_v1")
        file.writeBytes(bytes)
        assertNull(EncryptedMediaStorage.readDecryptedBytes(file))
        assertNull(EncryptedMediaStorage.openDecryptedInputStream(file))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun plaintextMediaIsRejectedAndPreserved() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = File(context.cacheDir, "unsupported-plaintext.bin").apply { writeText("Synthetic private value") }
        assertNull(EncryptedMediaStorage.readDecryptedBytes(file))
        assertNull(EncryptedMediaStorage.openDecryptedInputStream(file))
        assertEquals("Synthetic private value", file.readText())
    }

    @Test fun currentMediaRoundTripsAndTamperingCannotExposeAnyStream() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = File(context.cacheDir, "authenticated.bin")
        EncryptedMediaStorage.openEncryptedOutputStream(file).use { it.write("Synthetic private value".toByteArray()) }
        assertEquals("Synthetic private value", EncryptedMediaStorage.openDecryptedInputStream(file)!!.use { it.readBytes().decodeToString() })
        val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
        assertNull(EncryptedMediaStorage.openDecryptedInputStream(file))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun pinVerifierCannotBeUsedAsAnUnwrappingKey() {
        val kek = ByteArray(32) { (it + 10).toByte() }
        val vdk = ByteArray(32) { (it + 20).toByte() }
        val envelope = ArchiveVaultSecurity.wrapVaultDataKey(vdk, kek)
        assertArrayEquals(vdk, ArchiveVaultSecurity.unwrapVaultDataKey(envelope.first, envelope.second, kek))
        try {
            ArchiveVaultSecurity.unwrapVaultDataKey(envelope.first, envelope.second, ArchiveVaultSecurity.pinVerifier(kek))
            fail("The persisted verifier must never unwrap the vault")
        } catch (_: javax.crypto.AEADBadTagException) { }
    }
}
