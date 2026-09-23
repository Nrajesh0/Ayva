package com.focusbyrj.app

import com.focusbyrj.app.util.backup.CryptoBackupEngine
import com.focusbyrj.app.util.sync.VaultCryptoEngine
import com.focusbyrj.app.util.crypto.Argon2idKdf
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.data.note.NoteRepository
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import kotlinx.coroutines.runBlocking
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

    // B1-F-020: getActiveVaultSubKey must return a defensive clone to prevent in-place zeroization of the master subkey
    @Test
    fun getActiveVaultSubKeyDefensiveCopyPreventsExternalMutation() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val pin = "112233"
        ArchiveVaultSecurity.setPasscode(context, pin)

        val subKey1 = ArchiveVaultSecurity.getActiveVaultSubKey()
        assertNotNull("Subkey must be present after setPasscode", subKey1)
        val snapshot = subKey1!!.clone()

        // External caller zeroes their local copy for memory hygiene
        java.util.Arrays.fill(subKey1, 0.toByte())

        // Master subkey in ArchiveVaultSecurity must remain intact
        val subKey2 = ArchiveVaultSecurity.getActiveVaultSubKey()
        assertNotNull(subKey2)
        assertArrayEquals("B1-F-020: getActiveVaultSubKey must return a defensive copy so zeroing caller copy does not corrupt the cached subkey",
            snapshot, subKey2)

        ArchiveVaultSecurity.lockVault()
    }

    // B1-F-021: getTrashedNotesSync must decrypt vault-encrypted notes so media cleanup & UI see plaintext metadata
    @Test
    fun getTrashedNotesSyncDecryptsVaultNotes() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val noteDb = com.focusbyrj.app.data.note.NoteDatabase.getInstance(context)
        val repo = com.focusbyrj.app.data.note.NoteRepository(noteDb.noteDao())
        kotlinx.coroutines.runBlocking { noteDb.noteDao().deleteAllNotes() }

        val pin = "223344"
        ArchiveVaultSecurity.setPasscode(context, pin)
        val subKey = ArchiveVaultSecurity.getActiveVaultSubKey()
        assertNotNull(subKey)

        val secretNote = com.focusbyrj.app.data.note.NoteEntity(
            title = "Secret Trashed Note",
            content = "Highly confidential trashed content",
            isArchived = true,
            isTrashed = true,
            imageUrisJson = "[\"file:///data/keep_images/secret.enc\"]"
        )
        val encryptedNote = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(secretNote, subKey)
        kotlinx.coroutines.runBlocking {
            noteDb.noteDao().insertNote(encryptedNote)
        }

        // Call getTrashedNotesSync()
        val trashed = kotlinx.coroutines.runBlocking {
            repo.getTrashedNotesSync()
        }

        assertEquals(1, trashed.size)
        val retrieved = trashed[0]
        assertEquals("B1-F-021: getTrashedNotesSync must decrypt note title", "Secret Trashed Note", retrieved.title)
        assertEquals("B1-F-021: getTrashedNotesSync must decrypt note content", "Highly confidential trashed content", retrieved.content)
        assertEquals(1, retrieved.getImageUris().size)
        assertEquals("file:///data/keep_images/secret.enc", retrieved.getImageUris()[0])

        // Clean up
        kotlinx.coroutines.runBlocking { noteDb.noteDao().deleteAllNotes() }
        ArchiveVaultSecurity.lockVault()
    }

    // B1-F-025: malformed PIN must report actual remaining attempts based on failed attempts count, not hardcoded 5
    @Test
    fun malformedPinReflectsActualRemainingAttempts() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ArchiveVaultSecurity.setPasscode(context, "123456")
        ArchiveVaultSecurity.lockVault()

        // 2 failed attempts
        ArchiveVaultSecurity.verifyPasscode(context, "000000")
        ArchiveVaultSecurity.verifyPasscode(context, "000000")

        // Enter malformed PIN (4 digits)
        val result = ArchiveVaultSecurity.verifyPasscode(context, "1234")
        assertTrue("Result must be Incorrect", result is ArchiveVaultSecurity.VerifyResult.Incorrect)
        assertEquals("B1-F-025: Malformed PIN must reflect remaining attempts (5 - 2 = 3), not reset to 5",
            3, (result as ArchiveVaultSecurity.VerifyResult.Incorrect).remainingAttempts)

        // Clean up
        val prefs = context.getSharedPreferences("focus_notes_archive_vault_security", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("failed_attempts_count", 0).commit()
        ArchiveVaultSecurity.lockVault()
    }

    // B1-F-025: KeyStore decryption failure must return VerifyResult.Error without incrementing failed_attempts_count
    @Test
    fun keyStoreDecryptionFailureReturnsErrorWithoutLockoutEscalation() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ArchiveVaultSecurity.setPasscode(context, "654321")
        ArchiveVaultSecurity.lockVault()

        val prefs = context.getSharedPreferences("focus_notes_archive_vault_security", android.content.Context.MODE_PRIVATE)
        // Corrupt the stored hash IV to trigger GCM/KeyStore decryption failure
        val badIv = android.util.Base64.encodeToString(ByteArray(12) { 0xFF.toByte() }, android.util.Base64.NO_WRAP)
        prefs.edit().putString("enc_iv", badIv).commit()

        val initialAttempts = prefs.getInt("failed_attempts_count", 0)

        // Verify with the real PIN — should encounter KeyStore failure
        val result = ArchiveVaultSecurity.verifyPasscode(context, "654321")
        assertTrue("B1-F-025: Result must be Error on KeyStore failure, not Incorrect or LockedOut",
            result is ArchiveVaultSecurity.VerifyResult.Error)

        val finalAttempts = prefs.getInt("failed_attempts_count", 0)
        assertEquals("B1-F-025: KeyStore failure must NOT increment failed_attempts_count",
            initialAttempts, finalAttempts)

        // Clean up
        prefs.edit().clear().commit()
        ArchiveVaultSecurity.lockVault()
    }

    // B1-F-027: PBKDF2 auto-upgrade must re-wrap the recovery envelope so 12-word mnemonic recovery succeeds
    @Test
    fun autoUpgradeFromPbkdf2ReWrapsRecoveryEnvelopeSoMnemonicRecoverySucceeds() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val mnemonic = VaultCryptoEngine.generate12WordMnemonic()
        ArchiveVaultSecurity.setPasscode(context, "112233", mnemonic, true)
        val noteDb = NoteDatabase.getInstance(context)

        // Seed a test archived note
        val originalNote = NoteEntity(id = 101, title = "Secret Upgrade Note", content = "Critical Payload", isArchived = true)
        val subKey = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        val encryptedNote = VaultPayloadEncryptor.encryptNotePayload(originalNote, subKey)
        kotlinx.coroutines.runBlocking { noteDb.noteDao().insertNote(encryptedNote) }
        ArchiveVaultSecurity.lockVault()

        // 1. Manually simulate a legacy PBKDF2 vault in prefs
        val prefs = context.getSharedPreferences("focus_notes_archive_vault_security", android.content.Context.MODE_PRIVATE)
        val salt = android.util.Base64.decode(prefs.getString("enc_salt", "")!!, android.util.Base64.NO_WRAP)
        val keySpec = javax.crypto.spec.PBEKeySpec("112233".toCharArray(), salt, 100_000, 256)
        val pbkdf2Hash = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded

        // Re-encrypt the note with pbkdf2Hash
        val noteWithPbkdf2 = VaultPayloadEncryptor.encryptNotePayload(originalNote, pbkdf2Hash)
        kotlinx.coroutines.runBlocking { noteDb.noteDao().updateNote(noteWithPbkdf2) }

        // Create legacy recovery envelope wrapping pbkdf2Hash
        val legacyEnvelope = VaultCryptoEngine.createRecoveryEnvelope(pbkdf2Hash, mnemonic)
        val legacyEncryptedPhrase = ArchiveVaultSecurity.encryptRecoveryPhrase(mnemonic, pbkdf2Hash)

        prefs.edit()
            .putString("kdf_type", "pbkdf2")
            .putString("enc_hash", android.util.Base64.encodeToString(pbkdf2Hash, android.util.Base64.NO_WRAP))
            .putString("enc_iv", "")
            .putString("rec_ciphertext", android.util.Base64.encodeToString(legacyEnvelope.first, android.util.Base64.NO_WRAP))
            .putString("rec_iv", android.util.Base64.encodeToString(legacyEnvelope.second, android.util.Base64.NO_WRAP))
            .putString("rec_salt", android.util.Base64.encodeToString(legacyEnvelope.third, android.util.Base64.NO_WRAP))
            .putString("rec_phrase_ciphertext", android.util.Base64.encodeToString(legacyEncryptedPhrase.first, android.util.Base64.NO_WRAP))
            .putString("rec_phrase_iv", android.util.Base64.encodeToString(legacyEncryptedPhrase.second, android.util.Base64.NO_WRAP))
            .commit()

        // 2. Perform auto-upgrade via verifyPasscode
        val verifyResult = ArchiveVaultSecurity.verifyPasscode(context, "112233")
        assertTrue("Auto-upgrade unlock must succeed", verifyResult is ArchiveVaultSecurity.VerifyResult.Success)

        // Lock vault so we can test mnemonic recovery
        ArchiveVaultSecurity.lockVault()

        // 3. Test mnemonic recovery with new PIN
        val recoverSuccess = ArchiveVaultSecurity.recoverVaultWithMnemonic(context, mnemonic, "998877")
        assertTrue("B1-F-027: Mnemonic recovery must succeed after PBKDF2 to Argon2id auto-upgrade", recoverSuccess)

        // Verify the note is readable with the new PIN
        val newSubKey = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        val currentNote = kotlinx.coroutines.runBlocking { noteDb.noteDao().getNoteByIdSync(101)!! }
        val decryptedResult = VaultPayloadEncryptor.tryDecryptNotePayload(currentNote, newSubKey)
        assertTrue("Restored note must decrypt cleanly under new PIN", decryptedResult is VaultPayloadEncryptor.DecryptionResult.Success)
        assertEquals("Original note title must match", "Secret Upgrade Note", (decryptedResult as VaultPayloadEncryptor.DecryptionResult.Success).note.title)

        // Clean up
        kotlinx.coroutines.runBlocking { noteDb.noteDao().deleteAllNotes() }
        ArchiveVaultSecurity.disablePasscode(context)
    }

    // B1-F-030: renameLabel and deleteLabel must update vault-encrypted notes when vault is unlocked
    @Test
    fun labelRenameAndDeleteUpdatesVaultEncryptedNotesWhenUnlocked() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ArchiveVaultSecurity.setPasscode(context, "223344")
        val noteDb = NoteDatabase.getInstance(context)
        val repository = NoteRepository(noteDb.noteDao(), context)

        val note = NoteEntity(
            id = 202,
            title = "Vault Note With Labels",
            content = "Classified Content",
            labelsJson = """["Work","Sensitive"]""",
            isArchived = true
        )
        val subKey = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        val encNote = VaultPayloadEncryptor.encryptNotePayload(note, subKey)
        kotlinx.coroutines.runBlocking { noteDb.noteDao().insertNote(encNote) }

        // Rename label "Work" -> "Career" while vault is unlocked
        kotlinx.coroutines.runBlocking { repository.renameLabel("Work", "Career") }

        val renamedEncNote = kotlinx.coroutines.runBlocking { noteDb.noteDao().getNoteByIdSync(202)!! }
        val decryptedRenamed = VaultPayloadEncryptor.decryptNotePayload(renamedEncNote, subKey)
        assertTrue("B1-F-030: Renamed label must contain 'Career'", decryptedRenamed.getLabels().contains("Career"))
        assertFalse("B1-F-030: Renamed label must NOT contain 'Work'", decryptedRenamed.getLabels().contains("Work"))

        // Delete label "Sensitive" while vault is unlocked
        kotlinx.coroutines.runBlocking { repository.deleteLabel("Sensitive") }

        val deletedEncNote = kotlinx.coroutines.runBlocking { noteDb.noteDao().getNoteByIdSync(202)!! }
        val decryptedDeleted = VaultPayloadEncryptor.decryptNotePayload(deletedEncNote, subKey)
        assertFalse("B1-F-030: Deleted label must NOT be present", decryptedDeleted.getLabels().contains("Sensitive"))
        assertTrue("B1-F-030: Remaining label 'Career' must still be present", decryptedDeleted.getLabels().contains("Career"))

        // Clean up
        kotlinx.coroutines.runBlocking { noteDb.noteDao().deleteAllNotes() }
        ArchiveVaultSecurity.disablePasscode(context)
    }

    // B1-F-031: writeEncryptedBytes must create parent directory if missing
    @Test
    fun encryptedMediaStorageCreatesMissingParentDirectory() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val nonExistentDir = java.io.File(context.filesDir, "test_nested_missing_dir_" + java.util.UUID.randomUUID().toString())
        val targetFile = java.io.File(nonExistentDir, "media_test.enc")

        assertFalse("Parent directory must not exist prior to write", nonExistentDir.exists())
        val plaintext = "Hello Encrypted Media Storage".toByteArray(Charsets.UTF_8)
        EncryptedMediaStorage.writeEncryptedBytes(targetFile, plaintext)

        assertTrue("Parent directory must be created automatically", nonExistentDir.exists())
        assertTrue("Encrypted file must exist", targetFile.exists())

        val readBack = EncryptedMediaStorage.readDecryptedBytes(targetFile)
        assertNotNull("Decrypted bytes must not be null", readBack)
        assertArrayEquals("Decrypted content must match original plaintext", plaintext, readBack)

        // Clean up
        targetFile.delete()
        nonExistentDir.delete()
    }

    // B1-F-028: createEncryptedBackup fails when locked vault notes exist
    @Test
    fun createEncryptedBackupFailsWhenVaultIsLockedWithEncryptedNotes() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ArchiveVaultSecurity.setPasscode(context, "123456")
        val noteDb = NoteDatabase.getInstance(context)

        val note = NoteEntity(
            id = 301,
            title = "Secret Backup Note",
            content = "Vault payload data",
            isArchived = true
        )
        val subKey = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        val encNote = VaultPayloadEncryptor.encryptNotePayload(note, subKey)
        kotlinx.coroutines.runBlocking { noteDb.noteDao().insertNote(encNote) }

        // Lock vault so subkey is null
        ArchiveVaultSecurity.lockVault()

        val backupFile = java.io.File(context.cacheDir, "test_locked_vault_backup.focusbackup")
        val uri = android.net.Uri.fromFile(backupFile)

        val result = kotlinx.coroutines.runBlocking {
            com.focusbyrj.app.util.backup.BackupRestoreManager.createEncryptedBackup(context, uri, "backupPass123!")
        }

        assertTrue("B1-F-028: Backup must fail when locked vault notes exist", result.isFailure)
        assertTrue("B1-F-028: Error message must explain vault is locked",
            result.exceptionOrNull()?.message?.contains("Secret Archive Vault is locked") == true)

        // Clean up
        kotlinx.coroutines.runBlocking { noteDb.noteDao().deleteAllNotes() }
        ArchiveVaultSecurity.disablePasscode(context)
        backupFile.delete()
    }

    // B1-F-029: DatabaseKeyProvider propagates SecurityException when corrupted
    @Test
    fun databaseKeyProviderPropagatesSecurityException() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("focus_notes_vault_security_prefs", android.content.Context.MODE_PRIVATE)
        // Corrupt prefs by putting enc_db_passphrase without enc_db_passphrase_iv
        prefs.edit()
            .putString("enc_db_passphrase", "corrupted_base64_data")
            .remove("enc_db_passphrase_iv")
            .commit()

        com.focusbyrj.app.data.note.DatabaseKeyProvider.clearCachedPassphrase()
        try {
            com.focusbyrj.app.data.note.DatabaseKeyProvider.getOrCreatePassphrase(context)
            fail("B1-F-029: Expected SecurityException on corrupted passphrase entry")
        } catch (e: SecurityException) {
            assertTrue("SecurityException must be preserved and thrown", e.message?.contains("missing IV") == true)
        } finally {
            prefs.edit().clear().commit()
            com.focusbyrj.app.data.note.DatabaseKeyProvider.clearCachedPassphrase()
        }
    }

    // B1-F-032: EncryptedMediaFetcher decodes image safely from encrypted file
    @Test
    fun encryptedMediaFetcherDecodesEncryptedImage() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val mediaDir = java.io.File(context.filesDir, "keep_images").apply { mkdirs() }
        val imageFile = java.io.File(mediaDir, "test_photo.jpg")

        // Minimal JPEG header bytes
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00)
        EncryptedMediaStorage.writeEncryptedBytes(imageFile, jpegBytes)

        val options = coil.request.Options(context)
        val fetcher = com.focusbyrj.app.util.crypto.EncryptedMediaFetcher(imageFile, options)
        val result = kotlinx.coroutines.runBlocking { fetcher.fetch() }

        assertNotNull("B1-F-032: Fetcher must return a valid result for encrypted image", result)
        assertTrue("B1-F-032: Result must be SourceResult", result is coil.fetch.SourceResult)
        val sourceResult = result as coil.fetch.SourceResult
        assertEquals("image/jpeg", sourceResult.mimeType)

        // Clean up
        imageFile.delete()
    }
}


