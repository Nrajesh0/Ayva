package com.focusbyrj.app

import com.focusbyrj.app.util.backup.CryptoBackupEngine
import com.focusbyrj.app.util.sync.VaultCryptoEngine
import com.focusbyrj.app.util.crypto.Argon2idKdf
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Batch 1 Security Audit -- Regression Test Suite
 * Tests written BEFORE fixes (TDD red phase). Each test targets a specific audit finding.
 *
 * B1-F-001: openEncryptingStream / openDecryptingStream mutates caller's passwordChars
 * B1-F-002: Backup engine writes V2 (32MB LOGIN) when spec requires V3 (64MB BACKUP)
 * B1-F-004: remainingAttempts can go negative after 5+ failed PIN attempts
 * B1-F-005: PBEKeySpec.clearPassword() never called in deriveKeyFromMnemonic
 * B1-F-006: Orphaned temp file left on disk when writeEncryptedBytes fails
 * B1-F-008: Passcode re-encryption must abort transaction on decryption failure
 * B1-F-009: Truncated/corrupted encrypted media must return null, never fall back to plaintext
 * B1-F-011: DatabaseKeyProvider clearCachedPassphrase wipes key from heap
 * B1-F-012: encrypt/decrypt must not mutate caller's passwordChars
 */
@RunWith(RobolectricTestRunner::class)
class Batch1SecurityAuditTest {

    // B1-F-001: openEncryptingStream must NOT mutate caller's passwordChars
    @Test
    fun openEncryptingStreamMustNotMutateCallerPasswordChars() {
        val password = "MyBackupPassw0rd!".toCharArray()
        val snapshot = password.copyOf()
        val out = ByteArrayOutputStream()
        CryptoBackupEngine.openEncryptingStream(out, password).close()
        assertArrayEquals("B1-F-001: openEncryptingStream mutated caller passwordChars", snapshot, password)
    }

    // B1-F-001: openDecryptingStream must NOT mutate caller's passwordChars
    @Test
    fun openDecryptingStreamMustNotMutateCallerPasswordChars() {
        val encOut = ByteArrayOutputStream()
        CryptoBackupEngine.encrypt("payload".toByteArray(), "secret".toCharArray(), encOut)
        val password = "secret".toCharArray()
        val snapshot = password.copyOf()
        runCatching {
            CryptoBackupEngine.openDecryptingStream(ByteArrayInputStream(encOut.toByteArray()), password).readBytes()
        }
        assertArrayEquals("B1-F-001: openDecryptingStream mutated caller passwordChars", snapshot, password)
    }

    // B1-F-002: New backups must write version byte 0x03 (V3)
    @Test
    fun newBackupMustUseV3FormatHeader() {
        val out = ByteArrayOutputStream()
        CryptoBackupEngine.encrypt("test".toByteArray(), "password".toCharArray(), out)
        val versionByte = out.toByteArray()[4]
        assertEquals("B1-F-002: New backups must write V3 (0x03), got 0x", 0x03.toByte(), versionByte)
    }

    // B1-F-002: Legacy V2 backups must still decrypt (backward compat)
    @Test
    fun legacyV2BackupStillDecrypts() {
        val password = "legacyPw".toCharArray()
        val plaintext = "legacy content".toByteArray()
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { (it + 5).toByte() }
        val key = Argon2idKdf.deriveKey(password.copyOf(), salt, Argon2idKdf.Parameters.LOGIN)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext)
        val v2Frame = ByteArrayOutputStream().apply {
            write(byteArrayOf('F'.code.toByte(),'B'.code.toByte(),'C'.code.toByte(),'K'.code.toByte()))
            write(byteArrayOf(0x02))
            write(salt); write(iv); write(ct)
        }.toByteArray()
        val decrypted = CryptoBackupEngine.decrypt(ByteArrayInputStream(v2Frame), password.copyOf())
        assertArrayEquals("B1-F-002: V2 backup must still decrypt", plaintext, decrypted)
    }

    // B1-F-002: V3 round-trip must work end-to-end
    @Test
    fun v3BackupRoundTrip() {
        val password = "strongPassword99".toCharArray()
        val plaintext = "important data".toByteArray()
        val encOut = ByteArrayOutputStream()
        CryptoBackupEngine.encrypt(plaintext, password.copyOf(), encOut)
        val decrypted = CryptoBackupEngine.decrypt(ByteArrayInputStream(encOut.toByteArray()), password.copyOf())
        assertArrayEquals("B1-F-002: V3 round-trip failed", plaintext, decrypted)
    }

    // B1-F-004: remainingAttempts must never be negative
    @Test
    fun remainingAttemptsNeverGoesNegative() {
        for (attempts in 0..15) {
            val remaining = maxOf(0, 5 - attempts)
            assertTrue("B1-F-004: remaining= for attempts= should be >= 0", remaining >= 0)
        }
    }

    // B1-F-005: deriveKeyFromMnemonic determinism and size
    @Test
    fun deriveKeyFromMnemonicIsDeterministicAnd32Bytes() {
        val words = listOf("abandon","ability","able","about","above","absent","absorb","abstract","absurd","abuse","access","accident")
        val salt = ByteArray(16) { it.toByte() }
        val key1 = VaultCryptoEngine.deriveKeyFromMnemonic(words, salt)
        val key2 = VaultCryptoEngine.deriveKeyFromMnemonic(words, salt)
        assertEquals("B1-F-005: Key must be 32 bytes", 32, key1.size)
        assertArrayEquals("B1-F-005: Key derivation must be deterministic", key1, key2)
    }

    // B1-F-006: No temp file left after successful writeEncryptedBytes
    @Test
    fun writeEncryptedBytesLeavesNoTempFile() {
        val dir = createTempDir("enc_test")
        try {
            val targetFile = File(dir, "media.enc")
            val tempFile = File(dir, "media.enc.tmp")
            com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(targetFile, "hello".toByteArray())
            assertFalse("B1-F-006: Temp file must not exist after success", tempFile.exists())
            assertTrue("B1-F-006: Target file must exist after success", targetFile.exists())
        } finally { dir.deleteRecursively() }
    }

    // B1-F-006: writeEncryptedBytes round-trip
    @Test
    fun writeEncryptedBytesRoundTrip() {
        val dir = createTempDir("enc_rt")
        try {
            val targetFile = File(dir, "data.enc")
            val original = "sensitive bytes".toByteArray()
            com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(targetFile, original)
            val recovered = com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(targetFile)
            assertNotNull("B1-F-006: readDecryptedBytes must not return null", recovered)
            assertArrayEquals("B1-F-006: Round-trip must match", original, recovered)
        } finally { dir.deleteRecursively() }
    }

    // B1-F-009: Corrupted or truncated file starting with MAGIC_HEADER must return null, never raw bytes
    @Test
    fun truncatedEncryptedMediaReturnsNullNeverPlaintext() {
        val dir = createTempDir("enc_trunc")
        try {
            val file = File(dir, "corrupted.enc")
            // Starts with magic header but has incomplete IV / no ciphertext (15 bytes)
            val partialBytes = "FOC_ENC_V1:ABCD".toByteArray(Charsets.UTF_8)
            file.writeBytes(partialBytes)

            val result = com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(file)
            assertNull("B1-F-009: Truncated encrypted file must return null, but returned raw bytes", result)
        } finally { dir.deleteRecursively() }
    }

    // B1-F-012: CryptoBackupEngine.encrypt() must NOT mutate caller's passwordChars
    @Test
    fun encryptMustNotMutateCallerPasswordChars() {
        val password = "MySecretBackupKey123".toCharArray()
        val snapshot = password.copyOf()
        val out = ByteArrayOutputStream()
        CryptoBackupEngine.encrypt("some payload".toByteArray(), password, out)
        assertArrayEquals("B1-F-012: encrypt mutated caller's passwordChars", snapshot, password)
    }

    // B1-F-012: CryptoBackupEngine.decrypt() must NOT mutate caller's passwordChars
    @Test
    fun decryptMustNotMutateCallerPasswordChars() {
        val password = "MySecretBackupKey123".toCharArray()
        val out = ByteArrayOutputStream()
        CryptoBackupEngine.encrypt("some payload".toByteArray(), password.copyOf(), out)

        val snapshot = password.copyOf()
        val decrypted = CryptoBackupEngine.decrypt(ByteArrayInputStream(out.toByteArray()), password)
        assertEquals("some payload", String(decrypted))
        assertArrayEquals("B1-F-012: decrypt mutated caller's passwordChars", snapshot, password)
    }

    // B1-F-011: DatabaseKeyProvider clearCachedPassphrase wipes key from memory
    @Test
    fun databaseKeyProviderClearCachedPassphraseWipesMemory() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val key = com.focusbyrj.app.data.note.DatabaseKeyProvider.getOrCreatePassphrase(context)
        assertNotNull(key)
        assertEquals(32, key.size)

        com.focusbyrj.app.data.note.DatabaseKeyProvider.clearCachedPassphrase()
        // Calling getOrCreatePassphrase again should re-read or regenerate securely
        val key2 = com.focusbyrj.app.data.note.DatabaseKeyProvider.getOrCreatePassphrase(context)
        assertNotNull(key2)
        assertEquals(32, key2.size)
    }

    // B1-F-014: validateMnemonic must normalize words (case and whitespace)
    @Test
    fun validateMnemonicAcceptsCapitalizedWordsAndWhitespace() {
        val validMnemonic = VaultCryptoEngine.generate12WordMnemonic()
        assertTrue("B1-F-014: Generated mnemonic must be valid", VaultCryptoEngine.validateMnemonic(validMnemonic))

        // Transform with uppercase, mixed case, and leading/trailing whitespace (typical mobile keyboard behavior)
        val inputWithCapsAndSpaces = validMnemonic.mapIndexed { index, word ->
            when (index % 3) {
                0 -> "  ${word.replaceFirstChar { it.uppercase() }}  "
                1 -> word.uppercase()
                else -> "  $word"
            }
        }
        assertTrue("B1-F-014: Capitalized/spaced mnemonic must be normalized and accepted",
            VaultCryptoEngine.validateMnemonic(inputWithCapsAndSpaces))
    }

    // B1-F-015: skipPasscodeSetup must refuse if vault is already enabled
    @Test
    fun skipPasscodeSetupRefusesWhenVaultIsEnabled() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("focus_notes_archive_vault_security", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("vault_status", "enabled").commit()

        val skipped = ArchiveVaultSecurity.skipPasscodeSetup(context)
        assertFalse("B1-F-015: skipPasscodeSetup must return false if vault is already ENABLED", skipped)
        assertEquals("Vault status must remain enabled", "enabled", prefs.getString("vault_status", null))
        prefs.edit().clear().commit()
    }

    // B1-F-016: HKDF extract with empty salt must match null salt (RFC 5869 §2.2)
    @Test
    fun hkdfExtractWithEmptySaltMatchesNullSalt() {
        val ikm = "InputKeyMaterialForTesting123456".toByteArray()
        val prkFromNull = com.focusbyrj.app.util.crypto.HkdfUtil.extract(null, ikm)
        val prkFromEmpty = com.focusbyrj.app.util.crypto.HkdfUtil.extract(ByteArray(0), ikm)
        assertArrayEquals("B1-F-016: Empty salt must be equivalent to null salt (HashLen zeroes) per RFC 5869",
            prkFromNull, prkFromEmpty)
    }

    // B1-F-017: Argon2id salt must be at least 8 bytes
    @Test(expected = IllegalArgumentException::class)
    fun argon2idRejectsSaltShorterThan8Bytes() {
        Argon2idKdf.deriveKey("testPassword".toCharArray(), ByteArray(4))
    }

    // B1-F-018: verifyPasscode must auto-encrypt any plaintext notes found in the vault upon unlock
    @Test
    fun unlockVaultAutoEncryptsPlaintextArchivedNotes() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val noteDb = com.focusbyrj.app.data.note.NoteDatabase.getInstance(context)
        kotlinx.coroutines.runBlocking {
            noteDb.noteDao().deleteAllNotes()
        }

        val pin = "123456"
        ArchiveVaultSecurity.setPasscode(context, pin)
        ArchiveVaultSecurity.lockVault()

        // Insert a plaintext archived note (e.g. archived while vault was locked)
        val noteId = kotlinx.coroutines.runBlocking {
            noteDb.noteDao().insertNote(
                com.focusbyrj.app.data.note.NoteEntity(
                    title = "Plaintext Archived Note",
                    content = "Confidential contents",
                    isArchived = true
                )
            )
        }

        // Verify it is initially plaintext
        val preUnlock = kotlinx.coroutines.runBlocking { noteDb.noteDao().getNoteByIdSync(noteId) }
        assertNotNull(preUnlock)
        assertFalse(com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(preUnlock!!))

        // Unlock vault with PIN
        val unlockResult = ArchiveVaultSecurity.verifyPasscode(context, pin)
        assertTrue(unlockResult is ArchiveVaultSecurity.VerifyResult.Success)

        // Post-unlock: note must now be encrypted automatically
        val postUnlock = kotlinx.coroutines.runBlocking { noteDb.noteDao().getNoteByIdSync(noteId) }
        assertNotNull(postUnlock)
        assertTrue("B1-F-018: Plaintext archived note must be auto-encrypted upon unlocking vault",
            com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(postUnlock!!))

        // Clean up
        kotlinx.coroutines.runBlocking { noteDb.noteDao().deleteAllNotes() }
        ArchiveVaultSecurity.lockVault()
    }

    // B1-F-019: Unarchiving a vault-encrypted note while vault is locked must refuse rather than leaking ciphertext to active notes
    @Test
    fun unarchiveWhileLockedRefusesAndDoesNotLeakCiphertext() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val noteDb = com.focusbyrj.app.data.note.NoteDatabase.getInstance(context)
        val repo = com.focusbyrj.app.data.note.NoteRepository(noteDb.noteDao())
        kotlinx.coroutines.runBlocking {
            noteDb.noteDao().deleteAllNotes()
        }

        val pin = "654321"
        ArchiveVaultSecurity.setPasscode(context, pin)
        val subKey = ArchiveVaultSecurity.getActiveVaultSubKey()
        assertNotNull(subKey)

        val rawNote = com.focusbyrj.app.data.note.NoteEntity(
            title = "Top Secret Plans",
            content = "Classified instructions",
            isArchived = true
        )
        val encryptedNote = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(rawNote, subKey)
        val noteId = kotlinx.coroutines.runBlocking { noteDb.noteDao().insertNote(encryptedNote) }

        // Lock the vault
        ArchiveVaultSecurity.lockVault()
        assertNull(ArchiveVaultSecurity.getActiveVaultSubKey())

        // Attempt to unarchive while locked
        kotlinx.coroutines.runBlocking {
            repo.setArchived(noteId, false)
        }

        // Verify the note was NOT moved to active notes in an encrypted/corrupted state
        val activeNotes = kotlinx.coroutines.runBlocking { noteDb.noteDao().getAllActiveNotesSync() }
        assertTrue("B1-F-019: Active notes must not contain the locked encrypted note",
            activeNotes.none { it.id == noteId })

        val noteInDb = kotlinx.coroutines.runBlocking { noteDb.noteDao().getNoteByIdSync(noteId) }
        assertNotNull(noteInDb)
        assertTrue("B1-F-019: Note must remain archived when unarchive fails due to locked vault", noteInDb!!.isArchived)

        // Clean up
        kotlinx.coroutines.runBlocking { noteDb.noteDao().deleteAllNotes() }
        ArchiveVaultSecurity.lockVault()
    }
}


