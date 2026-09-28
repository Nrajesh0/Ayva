package com.focusbyrj.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.backup.BackupRestoreManager
import com.focusbyrj.app.util.backup.CryptoBackupEngine
import com.focusbyrj.app.util.crypto.Argon2idKdf
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.Arrays
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
class BackupRestoreHardeningTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Format V4 Header Layout & Signature Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testV4HeaderFormatAndLength() {
        val password = "SuperSecretPassword123!".toCharArray()
        val outStream = ByteArrayOutputStream()
        val cipherOut = CryptoBackupEngine.openEncryptingStream(outStream, password)
        cipherOut.close()

        val headerBytes = outStream.toByteArray()
        // Header layout:
        // Magic (4B) + Version (1B) + Salt (16B) + Verifier IV (12B) + Verifier Block (33B) + Payload IV (12B) = 78 bytes
        // Note: cipherOut.close() will also write the 16-byte GCM tag for the empty payload (total 94 bytes).
        assertTrue("V4 header must start with FBCK magic", headerBytes.size >= 78)
        assertEquals('F'.code.toByte(), headerBytes[0])
        assertEquals('B'.code.toByte(), headerBytes[1])
        assertEquals('C'.code.toByte(), headerBytes[2])
        assertEquals('K'.code.toByte(), headerBytes[3])
        assertEquals(CryptoBackupEngine.FORMAT_VERSION_V4, headerBytes[4])
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Instant Password Verification Canary Tests (Fast Rejection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testV4CanaryInstantRejectionOnWrongPassword() {
        val correctPassword = "CorrectPassword2026!".toCharArray()
        val wrongPassword = "WrongPassword999!".toCharArray()
        val payload = "Important user data to be encrypted in backup".toByteArray(Charsets.UTF_8)

        val encOut = ByteArrayOutputStream()
        CryptoBackupEngine.openEncryptingStream(encOut, correctPassword).use { stream ->
            stream.write(payload)
        }

        val encryptedBytes = encOut.toByteArray()

        // Decrypting with wrong password must throw SecurityException immediately during openDecryptingStream,
        // BEFORE reading any payload bytes from the stream!
        var securityExceptionThrown = false
        try {
            CryptoBackupEngine.openDecryptingStream(ByteArrayInputStream(encryptedBytes), wrongPassword)
        } catch (e: SecurityException) {
            securityExceptionThrown = true
            assertTrue("Exception message should indicate incorrect password", e.message?.contains("Incorrect backup password") == true)
        }

        assertTrue("Instant Canary rejection must throw SecurityException on bad password", securityExceptionThrown)
    }

    @Test
    fun testV4CorrectPasswordRoundtrip() {
        val password = "MyUltraSecureBackupPassword456$".toCharArray()
        val originalPayload = ByteArray(64 * 1024) { (it % 251).toByte() }

        val encOut = ByteArrayOutputStream()
        CryptoBackupEngine.openEncryptingStream(encOut, password).use { stream ->
            stream.write(originalPayload)
        }

        val encryptedBytes = encOut.toByteArray()

        val decIn = CryptoBackupEngine.openDecryptingStream(ByteArrayInputStream(encryptedBytes), password)
        val decryptedPayload = decIn.readBytes()
        decIn.close()

        assertArrayEquals("Decrypted payload must match original payload exactly", originalPayload, decryptedPayload)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Header Tampering & AAD Integrity Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testV4HeaderTamperingRejectedByCanary() {
        val password = "TamperDetectionPassword789#".toCharArray()
        val payload = "Tamper test payload".toByteArray(Charsets.UTF_8)

        val encOut = ByteArrayOutputStream()
        CryptoBackupEngine.openEncryptingStream(encOut, password).use { stream ->
            stream.write(payload)
        }

        val encryptedBytes = encOut.toByteArray()
        // Tamper with byte 10 (inside the salt)
        encryptedBytes[10] = (encryptedBytes[10].toInt() xor 0xFF).toByte()

        var caught = false
        try {
            CryptoBackupEngine.openDecryptingStream(ByteArrayInputStream(encryptedBytes), password)
        } catch (e: SecurityException) {
            caught = true
        }

        assertTrue("Tampered header salt must trigger verification failure", caught)
    }

    @Test
    fun testV4VerifierBlockTamperingRejected() {
        val password = "TamperDetectionPassword789#".toCharArray()
        val payload = "Tamper test payload".toByteArray(Charsets.UTF_8)

        val encOut = ByteArrayOutputStream()
        CryptoBackupEngine.openEncryptingStream(encOut, password).use { stream ->
            stream.write(payload)
        }

        val encryptedBytes = encOut.toByteArray()
        // Tamper with byte 40 (inside the verifier ciphertext block)
        encryptedBytes[40] = (encryptedBytes[40].toInt() xor 0xAA).toByte()

        var caught = false
        try {
            CryptoBackupEngine.openDecryptingStream(ByteArrayInputStream(encryptedBytes), password)
        } catch (e: SecurityException) {
            caught = true
        }

        assertTrue("Tampered verifier block must trigger verification failure", caught)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Backward Compatibility with V1, V2, V3
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testLegacyV3DecryptionCompatibility() {
        val password = "LegacyV3Password!".toCharArray()
        val payload = "Legacy V3 archive payload".toByteArray(Charsets.UTF_8)

        // Construct a genuine V3 archive:
        // [FBCK] + [0x03] + [16B salt] + [12B IV] + [AES-GCM ciphertext]
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val keyBytes = Argon2idKdf.deriveKey(password, salt, Argon2idKdf.Parameters.BACKUP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(payload)

        val v3Stream = ByteArrayOutputStream()
        v3Stream.write("FBCK".toByteArray(Charsets.US_ASCII))
        v3Stream.write(0x03)
        v3Stream.write(salt)
        v3Stream.write(iv)
        v3Stream.write(ciphertext)

        val decIn = CryptoBackupEngine.openDecryptingStream(ByteArrayInputStream(v3Stream.toByteArray()), password)
        val decrypted = decIn.readBytes()
        decIn.close()

        assertArrayEquals("Legacy V3 archive must decrypt faithfully", payload, decrypted)
    }

    @Test
    fun testLegacyV2DecryptionCompatibility() {
        val password = "LegacyV2Password!".toCharArray()
        val payload = "Legacy V2 archive payload".toByteArray(Charsets.UTF_8)

        // Construct a genuine V2 archive:
        // [FBCK] + [0x02] + [16B salt] + [12B IV] + [AES-GCM ciphertext with LOGIN params]
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val keyBytes = Argon2idKdf.deriveKey(password, salt, Argon2idKdf.Parameters.LOGIN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(payload)

        val v2Stream = ByteArrayOutputStream()
        v2Stream.write("FBCK".toByteArray(Charsets.US_ASCII))
        v2Stream.write(0x02)
        v2Stream.write(salt)
        v2Stream.write(iv)
        v2Stream.write(ciphertext)

        val decIn = CryptoBackupEngine.openDecryptingStream(ByteArrayInputStream(v2Stream.toByteArray()), password)
        val decrypted = decIn.readBytes()
        decIn.close()

        assertArrayEquals("Legacy V2 archive must decrypt faithfully", payload, decrypted)
    }

    @Test
    fun testLegacyV1DecryptionCompatibility() {
        val password = "LegacyV1Password!".toCharArray()
        val payload = "Legacy V1 archive payload".toByteArray(Charsets.UTF_8)

        // Construct a genuine V1 archive:
        // [FBCK] + [0x01] + [16B salt] + [12B IV] + [AES-GCM ciphertext with PBKDF2]
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, 100_000, 256)
        val keyBytes = factory.generateSecret(spec).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(payload)

        val v1Stream = ByteArrayOutputStream()
        v1Stream.write("FBCK".toByteArray(Charsets.US_ASCII))
        v1Stream.write(0x01)
        v1Stream.write(salt)
        v1Stream.write(iv)
        v1Stream.write(ciphertext)

        val decIn = CryptoBackupEngine.openDecryptingStream(ByteArrayInputStream(v1Stream.toByteArray()), password)
        val decrypted = decIn.readBytes()
        decIn.close()

        assertArrayEquals("Legacy V1 archive must decrypt faithfully", payload, decrypted)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Media Staging, Zip Slip & Extraction Safeguard Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testZipSlipDirectoryTraversalRejected() {
        val password = "ZipSlipTestPassword123!".toCharArray()
        val zipBaos = ByteArrayOutputStream()

        ZipOutputStream(zipBaos).use { zos ->
            // Normal metadata
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write("{}".toByteArray())
            zos.closeEntry()

            // Malicious media entry with Zip Slip traversal
            zos.putNextEntry(ZipEntry("media/audio/../../malicious.txt"))
            zos.write("evil payload".toByteArray())
            zos.closeEntry()
        }

        val encBaos = ByteArrayOutputStream()
        CryptoBackupEngine.openEncryptingStream(encBaos, password).use { stream ->
            stream.write(zipBaos.toByteArray())
        }

        val tempBackupFile = File(context.cacheDir, "malicious_backup.fbck")
        tempBackupFile.writeBytes(encBaos.toByteArray())
        val uri = android.net.Uri.fromFile(tempBackupFile)

        val result = runBlocking {
            BackupRestoreManager.restoreEncryptedBackup(context, uri, password.concatToString(), cleanRestore = false)
        }

        assertTrue("Restore must fail on Zip Slip attempt", result.isFailure)
        assertTrue(
            "Exception message must mention invalid media path or Zip Slip",
            result.exceptionOrNull()?.message?.contains("Invalid or disallowed media path") == true ||
            result.exceptionOrNull()?.message?.contains("Zip Slip") == true
        )

        tempBackupFile.delete()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Fail-Closed Secret Archive Vault Clean-Restore Protection Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testVaultCleanRestoreRequiresSetupWhenVaultNotConfigured() {
        val password = "VaultSecurityPassword123!".toCharArray()

        // Ensure vault is locked or disabled
        ArchiveVaultSecurity.lockVault()
        ArchiveVaultSecurity.disablePasscode(context)

        // Create backup JSON containing an archived note
        val jsonPayload = """
            {
                "schemaVersion": 1,
                "notes": [
                    {
                        "id": 101,
                        "title": "Secret Note",
                        "content": "Secret Content",
                        "isArchived": true
                    }
                ],
                "folders": [],
                "tasks": [],
                "habits": [],
                "habitLogs": [],
                "habitCategories": [],
                "drillSessions": [],
                "appRestrictions": [],
                "profiles": [],
                "chatMessages": [],
                "chatSessions": []
            }
        """.trimIndent()

        val zipBaos = ByteArrayOutputStream()
        ZipOutputStream(zipBaos).use { zos ->
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(jsonPayload.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val encBaos = ByteArrayOutputStream()
        CryptoBackupEngine.openEncryptingStream(encBaos, password).use { stream ->
            stream.write(zipBaos.toByteArray())
        }

        val backupFile = File(context.cacheDir, "vault_notes_backup.fbck")
        backupFile.writeBytes(encBaos.toByteArray())
        val uri = android.net.Uri.fromFile(backupFile)

        // Attempt restore without vault configured and without vaultPin
        val result = runBlocking {
            BackupRestoreManager.restoreEncryptedBackup(context, uri, password.concatToString(), cleanRestore = true)
        }

        assertTrue("Restore must fail and request vault PIN setup", result.isFailure)
        assertTrue(
            "Exception must be RequiresVaultSetupException",
            result.exceptionOrNull() is BackupRestoreManager.RequiresVaultSetupException
        )

        // Assert that the private note was NEVER inserted in cleartext into keep_notes
        val noteDao = NoteDatabase.getInstance(context).noteDao()
        val allNotes = runBlocking { noteDao.getAllNotesList() }
        val leakedNote = allNotes.find { it.title == "Secret Note" }
        assertNull("Private vault note must never be inserted without encryption", leakedNote)

        backupFile.delete()
    }

    @Test
    fun testVaultCleanRestoreSucceedsWhenValidVaultPinProvided() {
        val password = "VaultSecurityPassword123!".toCharArray()
        val vaultPin = "987654"

        // Ensure vault is clean/disabled
        ArchiveVaultSecurity.lockVault()
        ArchiveVaultSecurity.disablePasscode(context)

        // Create backup JSON containing an archived note
        val jsonPayload = """
            {
                "schemaVersion": 1,
                "notes": [
                    {
                        "id": 202,
                        "title": "Private Note With PIN",
                        "content": "Encrypted Confidential Info",
                        "isArchived": true
                    }
                ],
                "folders": [],
                "tasks": [],
                "habits": [],
                "habitLogs": [],
                "habitCategories": [],
                "drillSessions": [],
                "appRestrictions": [],
                "profiles": [],
                "chatMessages": [],
                "chatSessions": []
            }
        """.trimIndent()

        val zipBaos = ByteArrayOutputStream()
        ZipOutputStream(zipBaos).use { zos ->
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(jsonPayload.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val encBaos = ByteArrayOutputStream()
        CryptoBackupEngine.openEncryptingStream(encBaos, password).use { stream ->
            stream.write(zipBaos.toByteArray())
        }

        val backupFile = File(context.cacheDir, "vault_notes_backup_with_pin.fbck")
        backupFile.writeBytes(encBaos.toByteArray())
        val uri = android.net.Uri.fromFile(backupFile)

        // Attempt restore with valid 6-digit vaultPin
        val result = runBlocking {
            BackupRestoreManager.restoreEncryptedBackup(
                context = context,
                sourceUri = uri,
                password = password.concatToString(),
                cleanRestore = true,
                vaultPin = vaultPin
            )
        }

        assertTrue("Restore must succeed when vaultPin is provided: ${result.exceptionOrNull()?.message}", result.isSuccess)

        // Verify note was inserted into database and encrypted
        val noteDao = NoteDatabase.getInstance(context).noteDao()
        val allNotes = runBlocking { noteDao.getAllNotesList() }
        val importedNote = allNotes.find { it.isArchived }
        assertNotNull("Imported vault note should exist in database", importedNote)
        assertTrue("Imported note must remain marked archived", importedNote!!.isArchived)
        assertTrue("Imported note must be encrypted with vault envelope", VaultPayloadEncryptor.isVaultEncrypted(importedNote))
        assertNotEquals("Content must be encrypted, not raw plaintext", "Encrypted Confidential Info", importedNote.content)

        backupFile.delete()
    }
}
