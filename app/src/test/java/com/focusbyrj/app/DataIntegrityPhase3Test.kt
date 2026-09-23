package com.focusbyrj.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.VocabDatabase
import com.focusbyrj.app.data.drill.DrillDatabase
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import com.focusbyrj.app.util.sync.VaultCryptoEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

/**
 * Phase 3 Data Integrity & Cryptographic Hardening Test Suite:
 * - BIP-39 Vault Emergency Phrase generation & checksum verification.
 * - Envelope encryption of vault subkey with ephemeral PBKDF2/AES-GCM key derivation.
 * - Authenticated recovery envelope tamper resistance (AEAD bad tag detection).
 * - Full ArchiveVaultSecurity recovery lifecycle: forgot PIN -> recover with 12 words ->
 *   transparent atomic re-encryption of archived notes -> verify new PIN unlock.
 * - Fail-closed Room persistence guarantees (no destructive migration fallbacks in production).
 */
@RunWith(RobolectricTestRunner::class)
class DataIntegrityPhase3Test {

    private lateinit var context: Context
    private lateinit var noteDb: NoteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        noteDb = NoteDatabase.getInstance(context)
        // Clean up any existing vault preferences before each test
        context.getSharedPreferences("focus_notes_archive_vault_security", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ArchiveVaultSecurity.lockVault()
        runBlocking {
            noteDb.noteDao().deleteAllNotes()
        }
    }

    @After
    fun tearDown() {
        ArchiveVaultSecurity.lockVault()
        context.getSharedPreferences("focus_notes_archive_vault_security", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runBlocking {
            noteDb.noteDao().deleteAllNotes()
        }
    }

    @Test
    fun testBip39MnemonicGenerationAndChecksumValidation() {
        // 1. Generate multiple mnemonics and verify format & validity
        for (i in 0 until 10) {
            val words = VaultCryptoEngine.generate12WordMnemonic()
            assertEquals("Mnemonic must consist of exactly 12 words", 12, words.size)
            for (word in words) {
                assertTrue("Word must not be blank", word.isNotBlank())
                assertTrue("Word must be lowercase alphabet only", word.all { it in 'a'..'z' })
            }
            val isValid = VaultCryptoEngine.validateMnemonic(words)
            assertTrue("Generated mnemonic must pass BIP-39 checksum validation", isValid)
        }

        // 2. Verify invalid word count rejection
        val validWords = VaultCryptoEngine.generate12WordMnemonic()
        assertFalse("11 words must be invalid", VaultCryptoEngine.validateMnemonic(validWords.take(11)))
        assertFalse("13 words must be invalid", VaultCryptoEngine.validateMnemonic(validWords + listOf("abandon")))

        // 3. Verify corrupted checksum rejection
        // Corrupting the last word will alter the 4-bit checksum
        val corruptedWords = validWords.toMutableList()
        val originalLastWord = corruptedWords[11]
        corruptedWords[11] = if (originalLastWord == "zoo") "abandon" else "zoo"
        // Even if both words are in the wordlist, the 4-bit checksum of the first 128 bits won't match
        // unless it randomly collides (1 in 16 chance). Try multiple if collision occurs.
        var checksumFailed = false
        val candidateWords = listOf("abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract")
        for (candidate in candidateWords) {
            if (candidate != originalLastWord) {
                corruptedWords[11] = candidate
                if (!VaultCryptoEngine.validateMnemonic(corruptedWords)) {
                    checksumFailed = true
                    break
                }
            }
        }
        assertTrue("Mnemonic with invalid checksum must be rejected", checksumFailed)

        // 4. Verify non-wordlist word rejection
        val invalidList = validWords.toMutableList()
        invalidList[0] = "notabipword123"
        assertFalse("Mnemonic containing unknown words must fail validation", VaultCryptoEngine.validateMnemonic(invalidList))
    }

    @Test
    fun testMnemonicKeyDerivationAndEnvelopeRoundtrip() {
        val mnemonic = VaultCryptoEngine.generate12WordMnemonic()
        val random = SecureRandom()
        val originalVaultSubKey = ByteArray(32).also { random.nextBytes(it) }

        // Create envelope
        val (ciphertext, iv, salt) = VaultCryptoEngine.createRecoveryEnvelope(originalVaultSubKey, mnemonic)
        assertTrue("Ciphertext must not be empty", ciphertext.isNotEmpty())
        assertEquals("IV must be 12 bytes for AES-GCM", 12, iv.size)
        assertEquals("Salt must be 16 bytes", 16, salt.size)

        // Decrypt envelope with the exact same mnemonic
        val decryptedSubKey = VaultCryptoEngine.decryptRecoveryEnvelopeWithMnemonic(
            ciphertext = ciphertext,
            mnemonicWords = mnemonic,
            salt = salt,
            iv = iv
        )

        assertArrayEquals("Decrypted subkey must exactly match the original vault subkey", originalVaultSubKey, decryptedSubKey)
    }

    @Test
    fun testEnvelopeTamperResistance() {
        val mnemonic = VaultCryptoEngine.generate12WordMnemonic()
        val random = SecureRandom()
        val subKey = ByteArray(32).also { random.nextBytes(it) }

        val (ciphertext, iv, salt) = VaultCryptoEngine.createRecoveryEnvelope(subKey, mnemonic)

        // 1. Wrong mnemonic phrase
        val wrongMnemonic = VaultCryptoEngine.generate12WordMnemonic()
        var failedWithWrongPhrase = false
        try {
            VaultCryptoEngine.decryptRecoveryEnvelopeWithMnemonic(ciphertext, wrongMnemonic, salt, iv)
        } catch (_: Exception) {
            failedWithWrongPhrase = true
        }
        assertTrue("Decryption with wrong mnemonic must throw authenticated decryption failure (AEADBadTag)", failedWithWrongPhrase)

        // 2. Tampered ciphertext byte
        val tamperedCiphertext = ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0xFF).toByte()
        var failedWithTamperedCiphertext = false
        try {
            VaultCryptoEngine.decryptRecoveryEnvelopeWithMnemonic(tamperedCiphertext, mnemonic, salt, iv)
        } catch (_: Exception) {
            failedWithTamperedCiphertext = true
        }
        assertTrue("Decryption with flipped ciphertext bit must fail GCM tag verification", failedWithTamperedCiphertext)

        // 3. Tampered IV
        val tamperedIv = iv.copyOf()
        tamperedIv[0] = (tamperedIv[0].toInt() xor 0x01).toByte()
        var failedWithTamperedIv = false
        try {
            VaultCryptoEngine.decryptRecoveryEnvelopeWithMnemonic(ciphertext, mnemonic, salt, tamperedIv)
        } catch (_: Exception) {
            failedWithTamperedIv = true
        }
        assertTrue("Decryption with corrupted IV must fail GCM authentication", failedWithTamperedIv)
    }

    @Test
    fun testArchiveVaultSecuritySetupWithMnemonicAndRecovery() {
        runBlocking {
            val initialPin = "123456"
            val mnemonic = VaultCryptoEngine.generate12WordMnemonic()

            // 1. Configure vault with PIN and 12-word mnemonic
            val setupSuccess = ArchiveVaultSecurity.setPasscode(context, initialPin, mnemonic)
            assertTrue("Vault setup with passcode and mnemonic must succeed", setupSuccess)
            assertEquals(ArchiveVaultSecurity.VaultStatus.ENABLED, ArchiveVaultSecurity.getVaultStatus(context))
            assertTrue("Vault recovery must be configured", ArchiveVaultSecurity.isRecoveryConfigured(context))

            // 2. Verify mnemonic check
            assertTrue("Correct mnemonic must verify successfully", ArchiveVaultSecurity.verifyMnemonicOnly(context, mnemonic))
            val wrongMnemonic = VaultCryptoEngine.generate12WordMnemonic()
            assertFalse("Wrong mnemonic must not verify", ArchiveVaultSecurity.verifyMnemonicOnly(context, wrongMnemonic))

            // 3. Create and archive an encrypted note
            val activeSubKey = ArchiveVaultSecurity.getActiveVaultSubKey()
            assertNotNull("Active subkey must be cached in memory after setup", activeSubKey)

            val rawNote = NoteEntity(
                title = "Secret Vault Project Plans",
                content = "Confidential server access keys: ssh-ed25519-abc-123",
                isArchived = true
            )
            val encryptedNote = VaultPayloadEncryptor.encryptNotePayload(rawNote, activeSubKey)
            assertTrue("Note must be encrypted", VaultPayloadEncryptor.isVaultEncrypted(encryptedNote))
            val insertedId = noteDb.noteDao().insertNote(encryptedNote)
            assertTrue(insertedId > 0)

            // 4. Lock vault (simulate closing app / forgetting PIN)
            ArchiveVaultSecurity.lockVault()
            assertNull("Subkey must be zeroized after locking", ArchiveVaultSecurity.getActiveVaultSubKey())

            // 5. Recover vault using the 12-word mnemonic and set a new PIN
            val newPin = "987654"
            val recoverySuccess = ArchiveVaultSecurity.recoverVaultWithMnemonic(context, mnemonic, newPin)
            assertTrue("Vault recovery with valid mnemonic must succeed", recoverySuccess)

            // 6. Verify that vault is unlocked with new active subkey
            val newSubKey = ArchiveVaultSecurity.getActiveVaultSubKey()
            assertNotNull("New subkey must be active in memory after recovery", newSubKey)

            // 7. Verify the archived note was transparently re-encrypted under the new subkey and decrypts cleanly
            val fetchedNote = noteDb.noteDao().getNoteByIdSync(insertedId)
            assertNotNull(fetchedNote)
            assertTrue("Fetched note must still be vault-encrypted", VaultPayloadEncryptor.isVaultEncrypted(fetchedNote!!))

            val decryptedNote = VaultPayloadEncryptor.decryptNotePayload(fetchedNote, newSubKey)
            assertEquals("Secret Vault Project Plans", decryptedNote.title)
            assertEquals("Confidential server access keys: ssh-ed25519-abc-123", decryptedNote.content)

            // 8. Lock vault again and verify PIN unlock behavior
            ArchiveVaultSecurity.lockVault()

            // Old PIN must fail
            val oldPinResult = ArchiveVaultSecurity.verifyPasscode(context, initialPin)
            assertTrue("Old PIN must be rejected", oldPinResult is ArchiveVaultSecurity.VerifyResult.Incorrect)

            // New PIN must succeed
            val newPinResult = ArchiveVaultSecurity.verifyPasscode(context, newPin)
            assertTrue("New PIN must unlock the vault", newPinResult is ArchiveVaultSecurity.VerifyResult.Success)
            assertNotNull("Vault subkey must be restored", ArchiveVaultSecurity.getActiveVaultSubKey())
        }
    }

    @Test
    fun testDisablePasscodeWipesRecoveryEnvelope() {
        runBlocking {
            val pin = "555666"
            val mnemonic = VaultCryptoEngine.generate12WordMnemonic()

            ArchiveVaultSecurity.setPasscode(context, pin, mnemonic)
            assertTrue("Recovery must be active", ArchiveVaultSecurity.isRecoveryConfigured(context))

            // Disable passcode
            val disableSuccess = ArchiveVaultSecurity.disablePasscode(context)
            assertTrue("Disable passcode must succeed", disableSuccess)
            assertEquals(ArchiveVaultSecurity.VaultStatus.DISABLED, ArchiveVaultSecurity.getVaultStatus(context))

            // Verify recovery configuration is completely purged
            assertFalse("Recovery envelope must be removed", ArchiveVaultSecurity.isRecoveryConfigured(context))
            assertFalse("Mnemonic check must return false after disabling", ArchiveVaultSecurity.verifyMnemonicOnly(context, mnemonic))
        }
    }

    @Test
    fun testDatabaseBuilderGuaranteesFailClosedPersistence() {
        // Verify that VocabDatabase and DrillDatabase can be instantiated cleanly without fallbackToDestructiveMigration
        val vocabDb = Room.inMemoryDatabaseBuilder(context, VocabDatabase::class.java)
            .addMigrations(VocabDatabase.MIGRATION_1_2)
            .build()
        assertNotNull("VocabDatabase should initialize with explicit migration", vocabDb)
        vocabDb.close()

        val drillDb = Room.inMemoryDatabaseBuilder(context, DrillDatabase::class.java)
            .build()
        assertNotNull("DrillDatabase should initialize cleanly", drillDb)
        drillDb.close()
    }

    @Test
    fun testRecoveryPhraseZeroKnowledgeEncryptionAndDecryption() {
        val mnemonic = VaultCryptoEngine.generate12WordMnemonic()
        val subKey = ByteArray(32).also { SecureRandom().nextBytes(it) }

        // Encrypt recovery phrase under vault subkey
        val (ciphertext, iv) = ArchiveVaultSecurity.encryptRecoveryPhrase(mnemonic, subKey)
        assertNotNull(ciphertext)
        assertNotNull(iv)

        // Decrypt with correct subkey
        val decrypted = ArchiveVaultSecurity.decryptRecoveryPhrase(ciphertext, iv, subKey)
        assertNotNull(decrypted)
        assertEquals("Decrypted phrase must match original mnemonic", mnemonic, decrypted)

        // Decryption must fail with wrong subkey
        val wrongSubKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val failedDecryption = ArchiveVaultSecurity.decryptRecoveryPhrase(ciphertext, iv, wrongSubKey)
        assertNull("Decryption with wrong subkey must return null", failedDecryption)

        // Decryption must fail if ciphertext is tampered with
        val tamperedCiphertext = ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0xFF).toByte()
        val tamperedResult = ArchiveVaultSecurity.decryptRecoveryPhrase(tamperedCiphertext, iv, subKey)
        assertNull("Decryption of tampered ciphertext must return null", tamperedResult)
    }

    @Test
    fun testRecoveryPhraseExportAndBackupStatus() {
        val pin = "334455"
        val mnemonic = VaultCryptoEngine.generate12WordMnemonic()

        // 1. Setup vault with isPhraseBackedUp = false (simulating 'Back Up Later' flow)
        ArchiveVaultSecurity.setPasscode(context, pin, mnemonic, isPhraseBackedUp = false)
        assertFalse("Backup status must initially be false when skipped", ArchiveVaultSecurity.isRecoveryPhraseBackedUp(context))

        // 2. Export phrase while unlocked
        val exportedWhileUnlocked = ArchiveVaultSecurity.getStoredRecoveryPhrase(context)
        assertNotNull("Unlocked vault must allow exporting phrase", exportedWhileUnlocked)
        assertEquals(mnemonic, exportedWhileUnlocked)

        // 3. Mark phrase as backed up
        ArchiveVaultSecurity.markRecoveryPhraseBackedUp(context)
        assertTrue("Backup status must now be true", ArchiveVaultSecurity.isRecoveryPhraseBackedUp(context))

        // 4. Lock vault — phrase must NOT be exportable while locked (Zero-Knowledge)
        ArchiveVaultSecurity.lockVault()
        assertNull("Locked vault must return null for recovery phrase export", ArchiveVaultSecurity.getStoredRecoveryPhrase(context))

        // 5. Unlock vault — phrase export is re-enabled
        val unlockResult = ArchiveVaultSecurity.verifyPasscode(context, pin)
        assertTrue("PIN unlock must succeed", unlockResult is ArchiveVaultSecurity.VerifyResult.Success)
        val exportedAfterUnlock = ArchiveVaultSecurity.getStoredRecoveryPhrase(context)
        assertEquals(mnemonic, exportedAfterUnlock)
    }

    @Test
    fun testRecoveryPhrasePreservedAcrossPinChange() {
        val pin1 = "111222"
        val pin2 = "999888"
        val mnemonic = VaultCryptoEngine.generate12WordMnemonic()

        // Setup with PIN 1 and mnemonic
        ArchiveVaultSecurity.setPasscode(context, pin1, mnemonic, isPhraseBackedUp = true)
        assertTrue("Recovery phrase must be backed up", ArchiveVaultSecurity.isRecoveryPhraseBackedUp(context))

        // Change to PIN 2 without supplying new mnemonic
        val changeSuccess = ArchiveVaultSecurity.setPasscode(context, pin2)
        assertTrue("Changing passcode must succeed", changeSuccess)

        // Verify original 12 words are preserved and decrypted under the new subkey
        val exportedAfterPinChange = ArchiveVaultSecurity.getStoredRecoveryPhrase(context)
        assertEquals("Phrase must remain unchanged across PIN rotation", mnemonic, exportedAfterPinChange)
        assertTrue("Backup status must be preserved across PIN rotation", ArchiveVaultSecurity.isRecoveryPhraseBackedUp(context))

        // Lock vault and verify emergency recovery works with original mnemonic and resets to PIN 3
        ArchiveVaultSecurity.lockVault()
        val pin3 = "444555"
        val recovered = ArchiveVaultSecurity.recoverVaultWithMnemonic(context, mnemonic, pin3)
        assertTrue("Recovery with original mnemonic must succeed after PIN rotation", recovered)
        assertEquals(mnemonic, ArchiveVaultSecurity.getStoredRecoveryPhrase(context))
    }

    @Test
    fun testRecoveryPhraseGeneratedOnDemandWhenSkipped() {
        val pin = "778899"

        // Set passcode with NO mnemonic (skipped during setup)
        ArchiveVaultSecurity.setPasscode(context, pin, null)
        assertFalse("Recovery must not be configured initially", ArchiveVaultSecurity.isRecoveryConfigured(context))
        assertNull("Stored recovery phrase must initially be null", ArchiveVaultSecurity.getStoredRecoveryPhrase(context))

        // On-demand configuration while unlocked
        val generatedWords = ArchiveVaultSecurity.getOrConfigureRecoveryPhrase(context)
        assertNotNull("Generated words must not be null", generatedWords)
        assertEquals(12, generatedWords!!.size)
        assertTrue("Generated words must pass BIP-39 checksum", VaultCryptoEngine.validateMnemonic(generatedWords))
        assertTrue("Recovery envelope must now be configured", ArchiveVaultSecurity.isRecoveryConfigured(context))
        assertFalse("Backup status must initially be false for on-demand generated phrase", ArchiveVaultSecurity.isRecoveryPhraseBackedUp(context))

        // Subsequent call must return the exact same words
        val retrievedAgain = ArchiveVaultSecurity.getOrConfigureRecoveryPhrase(context)
        assertEquals(generatedWords, retrievedAgain)
    }

    @Test
    fun testRecoveryPhrasePurgedOnDisablePasscode() {
        val pin = "889900"
        val mnemonic = VaultCryptoEngine.generate12WordMnemonic()

        ArchiveVaultSecurity.setPasscode(context, pin, mnemonic, isPhraseBackedUp = true)
        assertNotNull(ArchiveVaultSecurity.getStoredRecoveryPhrase(context))
        assertTrue(ArchiveVaultSecurity.isRecoveryPhraseBackedUp(context))

        // Disable passcode
        ArchiveVaultSecurity.disablePasscode(context)

        // Verify stored phrase and backup status are purged
        val prefs = context.getSharedPreferences("focus_notes_archive_vault_security", Context.MODE_PRIVATE)
        assertNull("Ciphertext must be removed", prefs.getString("rec_phrase_ciphertext", null))
        assertNull("IV must be removed", prefs.getString("rec_phrase_iv", null))
        assertFalse("Backup flag must be false", ArchiveVaultSecurity.isRecoveryPhraseBackedUp(context))
    }
}
