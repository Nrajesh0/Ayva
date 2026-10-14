package com.focusbyrj.app

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.DatabaseKeyProvider
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.data.note.NoteRepository
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
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Work Package 4 Security & Data Integrity Verification Suite
 *
 * Verifies:
 * - F01/F03: Stable Vault Data Key (VDK) architecture replaces fragile PIN-as-data-key.
 * - Credential rotation (PIN changes) executes with ZERO note-ciphertext rewrites.
 * - Mnemonic emergency recovery resets credentials with ZERO note-ciphertext rewrites.
 * - Transparent upgrade from legacy PIN-as-data-key vaults to VDK.
 * - Vault disabling safely restores encrypted notes back to plaintext.
 * - Domain separation: General tasks/habits and ordinary notes operate independently of vault locks.
 */
@RunWith(RobolectricTestRunner::class)
class WorkPackage4SecurityTest {

    private lateinit var context: Context
    private lateinit var noteDb: NoteDatabase
    private lateinit var repository: NoteRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        org.robolectric.shadows.ShadowLog.stream = System.out
        noteDb = NoteDatabase.getInstance(context)
        repository = NoteRepository(noteDb.noteDao(), context)
        runBlocking { noteDb.noteDao().deleteAllNotes() }
        ArchiveVaultSecurity.disablePasscode(context)
        DatabaseKeyProvider.clearCachedPassphrase()
    }

    @After
    fun tearDown() {
        runBlocking { noteDb.noteDao().deleteAllNotes() }
        ArchiveVaultSecurity.disablePasscode(context)
        DatabaseKeyProvider.clearCachedPassphrase()
    }

    // =========================================================================
    // 1. Stable VDK: Zero Note-Ciphertext Rewrite on PIN Rotation (F03 Gate)
    // =========================================================================

    @Test
    fun f03_pinRotation_zeroNoteCiphertextRewrite() = runBlocking {
        val initialPin = "123456"
        val rotatedPin = "654321"

        // 1. Configure vault
        val configured = ArchiveVaultSecurity.setPasscode(context, initialPin)
        assertTrue("Vault configuration must succeed", configured)
        assertEquals(ArchiveVaultSecurity.VaultStatus.ENABLED, ArchiveVaultSecurity.getVaultStatus(context))

        val vdk = ArchiveVaultSecurity.getActiveVaultSubKey()
        assertNotNull("Active VDK must be present in memory", vdk)

        // 2. Insert secret note into vault
        val originalTitle = "Top Secret Strategy 2026"
        val originalContent = "Confidential architectural designs and security keys."
        val note = NoteEntity(
            id = 42,
            title = originalTitle,
            content = originalContent,
            isArchived = true
        )
        val encryptedNote = VaultPayloadEncryptor.encryptNotePayload(note, vdk)
        noteDb.noteDao().insertNote(encryptedNote)

        // Record persisted ciphertext in database prior to PIN rotation
        val noteBeforeRotation = noteDb.noteDao().getNoteByIdSync(42)!!
        assertTrue("Stored note must be vault-encrypted", VaultPayloadEncryptor.isVaultEncrypted(noteBeforeRotation))
        val rawCiphertextBefore = noteBeforeRotation.content

        // 3. Rotate PIN
        val rotationSuccess = ArchiveVaultSecurity.setPasscode(context, rotatedPin)
        assertTrue("PIN rotation must succeed", rotationSuccess)

        // 4. VERIFY ZERO NOTE REWRITE: Ciphertext in database must be 100% byte-for-byte untouched
        val noteAfterRotation = noteDb.noteDao().getNoteByIdSync(42)!!
        assertEquals(
            "WP4 Invariant: Persisted note ciphertext MUST NOT be rewritten during PIN rotation",
            rawCiphertextBefore,
            noteAfterRotation.content
        )

        // 5. Lock vault
        ArchiveVaultSecurity.lockVault()
        assertNull("VDK must be wiped on lock", ArchiveVaultSecurity.getActiveVaultSubKey())

        // Old PIN must fail
        val oldPinResult = ArchiveVaultSecurity.verifyPasscode(context, initialPin)
        assertTrue("Old PIN must be rejected after rotation", oldPinResult is ArchiveVaultSecurity.VerifyResult.Incorrect)

        // New PIN must succeed
        val newPinResult = ArchiveVaultSecurity.verifyPasscode(context, rotatedPin)
        assertTrue("New PIN must unlock vault", newPinResult is ArchiveVaultSecurity.VerifyResult.Success)

        // Unwrapped VDK must cleanly decrypt the untouched note
        val activeKey = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        val decryptedResult = VaultPayloadEncryptor.tryDecryptNotePayload(noteAfterRotation, activeKey)
        assertTrue("Note must decrypt cleanly under rotated PIN", decryptedResult is VaultPayloadEncryptor.DecryptionResult.Success)
        val decryptedNote = (decryptedResult as VaultPayloadEncryptor.DecryptionResult.Success).note
        assertEquals(originalTitle, decryptedNote.title)
        assertEquals(originalContent, decryptedNote.content)
    }

    // =========================================================================
    // 2. Mnemonic Recovery with Zero Note Rewrite (F03)
    // =========================================================================

    @Test
    fun f03_mnemonicRecovery_resetsCredentialsWithZeroNoteRewrite() = runBlocking {
        val pin1 = "112233"
        val pin2 = "998877"
        val mnemonic = VaultCryptoEngine.generate12WordMnemonic()

        // 1. Setup vault with PIN and mnemonic recovery phrase
        val setupSuccess = ArchiveVaultSecurity.setPasscode(context, pin1, mnemonic, isPhraseBackedUp = true)
        assertTrue("Setup must succeed", setupSuccess)
        assertTrue("Recovery must be configured", ArchiveVaultSecurity.isRecoveryConfigured(context))

        // 2. Add an encrypted vault note
        val originalTitle = "Mnemonic Recovery Document"
        val originalContent = "Important credentials recovered via BIP-39 mnemonic."
        val note = NoteEntity(
            id = 77,
            title = originalTitle,
            content = originalContent,
            isArchived = true
        )
        val encryptedNote = VaultPayloadEncryptor.encryptNotePayload(note, ArchiveVaultSecurity.getActiveVaultSubKey())
        noteDb.noteDao().insertNote(encryptedNote)

        val rawCiphertextBefore = noteDb.noteDao().getNoteByIdSync(77)!!.content

        // 3. Lock vault
        ArchiveVaultSecurity.lockVault()

        // 4. Perform emergency recovery using 12-word mnemonic
        val recovered = ArchiveVaultSecurity.recoverVaultWithMnemonic(context, mnemonic, pin2)
        assertTrue("Recovery with mnemonic phrase must succeed", recovered)

        // 5. VERIFY ZERO NOTE REWRITE: Ciphertext in database was NOT rewritten
        val noteAfterRecovery = noteDb.noteDao().getNoteByIdSync(77)!!
        assertEquals(
            "WP4 Invariant: Persisted note ciphertext MUST NOT be rewritten during mnemonic recovery",
            rawCiphertextBefore,
            noteAfterRecovery.content
        )

        // 6. Decrypt note with unwrapped VDK
        val activeSubKey = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        val decryptedResult = VaultPayloadEncryptor.tryDecryptNotePayload(noteAfterRecovery, activeSubKey)
        assertTrue("Recovered note must decrypt cleanly", decryptedResult is VaultPayloadEncryptor.DecryptionResult.Success)
        val decryptedNote = (decryptedResult as VaultPayloadEncryptor.DecryptionResult.Success).note
        assertEquals(originalTitle, decryptedNote.title)
        assertEquals(originalContent, decryptedNote.content)

        // 7. Recovery phrase must still be accessible
        val exportedPhrase = ArchiveVaultSecurity.getStoredRecoveryPhrase(context)
        assertEquals("Mnemonic phrase must be intact after recovery", mnemonic, exportedPhrase)
    }

    // =========================================================================
    // 3. Transparent Upgrade from Legacy PIN-as-data-key Vault to VDK
    // =========================================================================

    @Test
    fun f01_f03_transparentUpgradeFromLegacyPinKeyToVdk() = runBlocking {
        val legacyPin = "334455"
        val salt = ByteArray(16) { (it + 5).toByte() }

        // Derive legacy key using PBKDF2
        val keySpec = javax.crypto.spec.PBEKeySpec(legacyPin.toCharArray(), salt, 100_000, 256)
        val legacyKey = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded

        // Simulate legacy SharedPreferences state (no KEY_WRAPPED_VAULT_DATA_KEY)
        val prefs = context.getSharedPreferences("focus_notes_archive_vault_security", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("vault_status", "enabled")
            .putString("enc_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("enc_hash", Base64.encodeToString(legacyKey, Base64.NO_WRAP))
            .putString("kdf_type", "pbkdf2")
            .remove(ArchiveVaultSecurity.KEY_WRAPPED_VAULT_DATA_KEY)
            .remove(ArchiveVaultSecurity.KEY_WRAPPED_VAULT_DATA_KEY_IV)
            .commit()

        assertFalse("Legacy vault must not have VDK before first unlock", prefs.contains(ArchiveVaultSecurity.KEY_WRAPPED_VAULT_DATA_KEY))

        // Create legacy note encrypted directly with legacy PIN key
        val originalTitle = "Legacy Encrypted Note"
        val originalContent = "Note originally encrypted with legacy PIN key."
        val note = NoteEntity(id = 88, title = originalTitle, content = originalContent, isArchived = true)
        val encryptedNote = VaultPayloadEncryptor.encryptNotePayload(note, legacyKey)
        noteDb.noteDao().insertNote(encryptedNote)

        // First unlock with legacy PIN
        val unlockResult = ArchiveVaultSecurity.verifyPasscode(context, legacyPin)
        assertTrue("Unlock with legacy PIN must succeed", unlockResult is ArchiveVaultSecurity.VerifyResult.Success)

        // Must have transparently upgraded to VDK in SharedPreferences
        assertTrue("Vault must now have KEY_WRAPPED_VAULT_DATA_KEY after unlock", prefs.contains(ArchiveVaultSecurity.KEY_WRAPPED_VAULT_DATA_KEY))
        assertTrue("Vault must now have KEY_WRAPPED_VAULT_DATA_KEY_IV", prefs.contains(ArchiveVaultSecurity.KEY_WRAPPED_VAULT_DATA_KEY_IV))

        // Note must decrypt cleanly
        val activeKey = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        val decryptedResult = VaultPayloadEncryptor.tryDecryptNotePayload(noteDb.noteDao().getNoteByIdSync(88)!!, activeKey)
        assertTrue("Upgraded note must decrypt cleanly", decryptedResult is VaultPayloadEncryptor.DecryptionResult.Success)
        assertEquals(originalTitle, (decryptedResult as VaultPayloadEncryptor.DecryptionResult.Success).note.title)

        // Subsequent PIN rotation must now execute with zero note rewrites!
        val rawCiphertextBefore = noteDb.noteDao().getNoteByIdSync(88)!!.content
        val newPin = "778899"
        val rotationSuccess = ArchiveVaultSecurity.setPasscode(context, newPin)
        assertTrue("PIN rotation on upgraded vault must succeed", rotationSuccess)
        val rawCiphertextAfter = noteDb.noteDao().getNoteByIdSync(88)!!.content
        assertEquals("Subsequent rotation on upgraded vault must have zero note rewrites", rawCiphertextBefore, rawCiphertextAfter)
    }

    // =========================================================================
    // 4. Vault Disabling Safely Restores Plaintext Notes
    // =========================================================================

    @Test
    fun f03_disablePasscode_restoresPlaintextAndWipesVdk() = runBlocking {
        val pin = "556677"
        ArchiveVaultSecurity.setPasscode(context, pin)

        val note = NoteEntity(id = 99, title = "Private Text", content = "Restored on disable", isArchived = true)
        val encryptedNote = VaultPayloadEncryptor.encryptNotePayload(note, ArchiveVaultSecurity.getActiveVaultSubKey())
        noteDb.noteDao().insertNote(encryptedNote)

        assertTrue(VaultPayloadEncryptor.isVaultEncrypted(noteDb.noteDao().getNoteByIdSync(99)!!))

        // Disable passcode
        val disabled = ArchiveVaultSecurity.disablePasscode(context)
        assertTrue("disablePasscode must succeed", disabled)
        assertEquals(ArchiveVaultSecurity.VaultStatus.DISABLED, ArchiveVaultSecurity.getVaultStatus(context))

        // Note in DB must be restored to plaintext
        val restoredNote = noteDb.noteDao().getNoteByIdSync(99)!!
        assertFalse("Note must no longer be vault-encrypted", VaultPayloadEncryptor.isVaultEncrypted(restoredNote))
        assertEquals("Private Text", restoredNote.title)
        assertEquals("Restored on disable", restoredNote.content)

        // VDK must be wiped from memory and preferences
        assertNull("Active subkey must be null", ArchiveVaultSecurity.getActiveVaultSubKey())
        val prefs = context.getSharedPreferences("focus_notes_archive_vault_security", Context.MODE_PRIVATE)
        assertFalse("VDK must be removed from prefs", prefs.contains(ArchiveVaultSecurity.KEY_WRAPPED_VAULT_DATA_KEY))
        assertFalse("VDK IV must be removed from prefs", prefs.contains(ArchiveVaultSecurity.KEY_WRAPPED_VAULT_DATA_KEY_IV))
    }

    // =========================================================================
    // 5. Domain Separation: General Data Operates Independently of Vault
    // =========================================================================

    @Test
    fun domainSeparation_ordinaryNotesAndDatabaseIndependentOfVaultLock() = runBlocking {
        // Vault is ENABLED and LOCKED
        ArchiveVaultSecurity.setPasscode(context, "123456")
        ArchiveVaultSecurity.lockVault()
        assertTrue("Vault must be locked", ArchiveVaultSecurity.isVaultLocked(context))

        // Ordinary (unarchived) notes can be created, saved, and read normally
        val ordinaryNote = NoteEntity(
            id = 100,
            title = "Ordinary Work Meeting",
            content = "Discuss quarterly goals and action items.",
            isArchived = false
        )
        noteDb.noteDao().insertNote(ordinaryNote)

        val retrieved = noteDb.noteDao().getNoteByIdSync(100)!!
        assertEquals("Ordinary Work Meeting", retrieved.title)
        assertEquals("Discuss quarterly goals and action items.", retrieved.content)
        assertFalse("Ordinary note is not archived", retrieved.isArchived)
        assertFalse("Ordinary note is not vault-encrypted", VaultPayloadEncryptor.isVaultEncrypted(retrieved))
    }
}
