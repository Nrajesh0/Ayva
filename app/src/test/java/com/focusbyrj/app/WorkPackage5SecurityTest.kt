package com.focusbyrj.app

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.DatabaseKeyProvider
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.backup.BackupRestoreManager
import com.focusbyrj.app.util.backup.CryptoBackupEngine
import com.focusbyrj.app.util.backup.DataSafetyManager
import com.focusbyrj.app.util.backup.SnapshotCryptoEngine
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Work Package 5 Security & Data Integrity Verification Suite
 *
 * Verifies:
 * - F05: Internal snapshots (daily, pre-op, emergency) are encrypted at rest with hardware-backed AES-256-GCM.
 *   Raw snapshot files contain zero readable note titles or content.
 * - F05: Legacy unencrypted JSON snapshots are transparently migrated to encrypted containers.
 * - F05: Locked vault automatic snapshots preserve opaque authenticated ciphertext without leaking plaintext.
 * - F06: Restoring a historical snapshot never overwrites working live notes with obsolete undecryptable ciphertext.
 * - F14: Preference restore strictly enforces PREF_FILES allowlist and rejects disallowed security preferences.
 */
@RunWith(RobolectricTestRunner::class)
class WorkPackage5SecurityTest {

    private lateinit var context: Context
    private lateinit var noteDb: NoteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        org.robolectric.shadows.ShadowLog.stream = System.out
        noteDb = NoteDatabase.getInstance(context)
        runBlocking { noteDb.noteDao().deleteAllNotes() }
        ArchiveVaultSecurity.disablePasscode(context)
        DatabaseKeyProvider.clearCachedPassphrase()

        // Clean auto_backups directories
        File(context.filesDir, "auto_backups").deleteRecursively()
    }

    @After
    fun tearDown() {
        runBlocking { noteDb.noteDao().deleteAllNotes() }
        ArchiveVaultSecurity.disablePasscode(context)
        DatabaseKeyProvider.clearCachedPassphrase()
        File(context.filesDir, "auto_backups").deleteRecursively()
    }

    // =========================================================================
    // 1. F05: Snapshots Are Encrypted at Rest (Zero Plaintext Note Content)
    // =========================================================================

    @Test
    fun f05_snapshotsAreEncryptedAtRest_noReadableNoteContent() = runBlocking {
        val secretTitle = "Ultra Confidential Financial Plan"
        val secretContent = "Cryptographic keys and account numbers stored securely."
        val note = NoteEntity(
            id = 101,
            title = secretTitle,
            content = secretContent,
            isArchived = false
        )
        noteDb.noteDao().insertNote(note)

        // Write pre-op snapshot
        val snapshotPath = DataSafetyManager.writePreOpSnapshot(context, noteDb.noteDao(), "f05_test")
        assertNotNull("Snapshot path must not be null", snapshotPath)
        val snapshotFile = File(snapshotPath!!)
        assertTrue("Snapshot file must exist on disk", snapshotFile.exists())

        // 1. Raw file inspection: MUST be an encrypted container
        assertTrue("Snapshot file must have SnapshotCryptoEngine encryption header", SnapshotCryptoEngine.isEncrypted(snapshotFile))

        val rawBytes = FileInputStream(snapshotFile).use { it.readBytes() }
        assertTrue("Raw snapshot must have valid encrypted size", rawBytes.size > 33)

        // Ensure NO plaintext note title or content appears anywhere in the raw bytes on disk!
        val rawText = String(rawBytes, Charsets.ISO_8859_1)
        assertFalse("Raw snapshot MUST NOT leak note title in plaintext", rawText.contains(secretTitle))
        assertFalse("Raw snapshot MUST NOT leak note content in plaintext", rawText.contains(secretContent))
        assertFalse("Raw snapshot MUST NOT leak unencrypted JSON metadata", rawText.contains("\"snapshotVersion\""))

        // 2. DataSafetyManager can transparently read and decrypt the snapshot
        val noteCount = DataSafetyManager.readSnapshotNoteCount(snapshotPath)
        assertEquals("Decrypted snapshot note count must be 1", 1, noteCount)

        val snapshotsList = DataSafetyManager.listAvailableSnapshots(context)
        assertEquals(1, snapshotsList.size)
        assertEquals(1, snapshotsList.first().noteCount)

        // 3. Clear database and restore from encrypted snapshot
        noteDb.noteDao().deleteAllNotes()
        assertEquals(0, noteDb.noteDao().getAllNotesList().size)

        val restoreResult = DataSafetyManager.restoreSnapshot(context, snapshotPath, noteDb.noteDao())
        assertTrue("Restore from encrypted snapshot must succeed", restoreResult.isSuccess)

        val restoredNotes = noteDb.noteDao().getAllNotesList()
        assertEquals(1, restoredNotes.size)
        assertEquals(secretTitle, restoredNotes.first().title)
        assertEquals(secretContent, restoredNotes.first().content)
    }

    // =========================================================================
    // 2. F05: Legacy Plaintext Snapshots Transparently Migrated
    // =========================================================================

    @Test
    fun f05_legacyPlaintextSnapshotsTransparentlyMigratedToEncrypted() = runBlocking {
        val backupDir = File(context.filesDir, "auto_backups").apply { mkdirs() }
        val legacyFile = File(backupDir, "daily_20260101.json")

        // Manually write an old unencrypted JSON snapshot as previously produced
        val legacyJson = JSONObject().apply {
            put("snapshotVersion", 2)
            put("createdAt", System.currentTimeMillis())
            put("noteCount", 1)
            val arr = JSONArray()
            arr.put(JSONObject().apply {
                put("id", 202L)
                put("title", "Legacy Plaintext Title")
                put("content", "Legacy Plaintext Content")
                put("isArchived", false)
                put("isTrashed", false)
                put("createdAt", 1000L)
                put("updatedAt", 2000L)
            })
            put("notes", arr)
        }
        legacyFile.writeText(legacyJson.toString(), Charsets.UTF_8)

        // Verify it was raw plaintext initially
        assertFalse("Legacy snapshot file is not encrypted initially", SnapshotCryptoEngine.isEncrypted(legacyFile))
        assertTrue("Legacy snapshot file contains plaintext title initially", legacyFile.readText().contains("Legacy Plaintext Title"))

        // Run migration
        val migratedCount = DataSafetyManager.migrateLegacyPlaintextSnapshots(context)
        assertEquals("Exactly 1 legacy snapshot must be migrated", 1, migratedCount)

        // Verify the file on disk is NOW encrypted!
        assertTrue("Migrated snapshot file must now be encrypted", SnapshotCryptoEngine.isEncrypted(legacyFile))
        val rawBytes = FileInputStream(legacyFile).use { it.readBytes() }
        val rawText = String(rawBytes, Charsets.ISO_8859_1)
        assertFalse("Migrated snapshot must no longer have plaintext note title on disk", rawText.contains("Legacy Plaintext Title"))

        // Verify it can still be restored cleanly
        val restoreResult = DataSafetyManager.restoreSnapshot(context, legacyFile.absolutePath, noteDb.noteDao())
        assertTrue("Restore from migrated snapshot must succeed", restoreResult.isSuccess)

        val restored = noteDb.noteDao().getNoteByIdSync(202L)
        assertNotNull(restored)
        assertEquals("Legacy Plaintext Title", restored!!.title)
        assertEquals("Legacy Plaintext Content", restored.content)
    }

    // =========================================================================
    // 3. F06: Snapshot Restore Does Not Overwrite Working Live Notes with Obsolete Ciphertext
    // =========================================================================

    @Test
    fun f06_snapshotRestoreDoesNotOverwriteWorkingLiveNoteWithObsoleteCiphertext() = runBlocking {
        // Setup vault with modern PIN
        ArchiveVaultSecurity.setPasscode(context, "112233")
        val activeSubKey = ArchiveVaultSecurity.getActiveVaultSubKey()!!

        // Create working live note in database encrypted with activeSubKey
        val liveNote = NoteEntity(
            id = 303,
            title = "Current Working Private Note",
            content = "Accessible with current PIN 112233",
            isArchived = true
        )
        val encryptedLiveNote = VaultPayloadEncryptor.encryptNotePayload(liveNote, activeSubKey)
        noteDb.noteDao().insertNote(encryptedLiveNote)

        // Construct a snapshot containing note 303 encrypted with an OBSOLETE key
        val obsoleteKey = ByteArray(32) { 99.toByte() }
        val obsoleteEncryptedNote = NoteEntity(
            id = 303,
            title = "Current Working Private Note",
            content = "Encrypted with obsolete key from before rotation",
            isArchived = true
        )
        val obsoletePayloadNote = VaultPayloadEncryptor.encryptNotePayload(obsoleteEncryptedNote, obsoleteKey)

        val snapshotDir = File(context.filesDir, "auto_backups/pre_op").apply { mkdirs() }
        val snapshotFile = File(snapshotDir, "pre_op_obsolete_test.json")

        val snapshotJson = JSONObject().apply {
            put("snapshotVersion", 3)
            put("createdAt", System.currentTimeMillis())
            put("noteCount", 1)
            val arr = JSONArray()
            arr.put(JSONObject().apply {
                put("id", obsoletePayloadNote.id)
                put("title", obsoletePayloadNote.title)
                put("content", obsoletePayloadNote.content) // Obsolete ciphertext!
                put("isArchived", true)
                put("isTrashed", false)
                put("createdAt", obsoletePayloadNote.createdAt)
                put("updatedAt", obsoletePayloadNote.updatedAt)
            })
            put("notes", arr)
        }
        DataSafetyManager.writeAtomically(snapshotFile, snapshotJson.toString())

        // Execute restore
        val restoreResult = DataSafetyManager.restoreSnapshot(context, snapshotFile.absolutePath, noteDb.noteDao())
        assertTrue("Restore executes safely", restoreResult.isSuccess)

        // F06 INVARIANT: The live note in the database must NOT have been corrupted/replaced with obsolete ciphertext!
        val preservedLiveNote = noteDb.noteDao().getNoteByIdSync(303)!!
        val decResult = VaultPayloadEncryptor.tryDecryptNotePayload(preservedLiveNote, activeSubKey)
        assertTrue("Live note must remain fully decryptable with current key", decResult is VaultPayloadEncryptor.DecryptionResult.Success)
        assertEquals("Accessible with current PIN 112233", (decResult as VaultPayloadEncryptor.DecryptionResult.Success).note.content)
    }

    // =========================================================================
    // 4. F05: Locked Vault Snapshots Preserve Opaque Ciphertext Without Plaintext Leak
    // =========================================================================

    @Test
    fun f05_lockedVaultSnapshotsPreserveOpaqueCiphertextWithoutPlaintextLeak() = runBlocking {
        val pin = "654321"
        ArchiveVaultSecurity.setPasscode(context, pin)
        val activeKey = ArchiveVaultSecurity.getActiveVaultSubKey()!!

        val secretNote = NoteEntity(
            id = 404,
            title = "Locked Vault Secret",
            content = "Sensitive content that must stay encrypted during lock",
            isArchived = true
        )
        val encryptedNote = VaultPayloadEncryptor.encryptNotePayload(secretNote, activeKey)
        noteDb.noteDao().insertNote(encryptedNote)

        // Lock the vault
        ArchiveVaultSecurity.lockVault()
        assertTrue("Vault must be locked", ArchiveVaultSecurity.isVaultLocked(context))

        // Take snapshot while vault is locked (e.g. daily automatic backup)
        val success = DataSafetyManager.writeDailyBackup(context, noteDb.noteDao())
        assertTrue("Daily backup must succeed while vault is locked", success)

        val dailyDir = File(context.filesDir, "auto_backups")
        val dailyFiles = dailyDir.listFiles { f -> f.name.startsWith("daily_") } ?: emptyArray()
        assertEquals(1, dailyFiles.size)
        val dailyFile = dailyFiles.first()

        // Raw file on disk must be encrypted
        assertTrue(SnapshotCryptoEngine.isEncrypted(dailyFile))
        val rawText = String(FileInputStream(dailyFile).use { it.readBytes() }, Charsets.ISO_8859_1)
        assertFalse("Must not leak locked note title in plaintext", rawText.contains("Locked Vault Secret"))

        // Unlock vault and verify restored note decrypts cleanly
        val unlockResult = ArchiveVaultSecurity.verifyPasscode(context, pin)
        assertTrue(unlockResult is ArchiveVaultSecurity.VerifyResult.Success)

        noteDb.noteDao().deleteAllNotes()
        DataSafetyManager.restoreSnapshot(context, dailyFile.absolutePath, noteDb.noteDao())

        val restored = noteDb.noteDao().getNoteByIdSync(404)!!
        val decrypted = VaultPayloadEncryptor.tryDecryptNotePayload(restored, ArchiveVaultSecurity.getActiveVaultSubKey()!!)
        assertTrue("Restored locked note must decrypt after unlocking vault", decrypted is VaultPayloadEncryptor.DecryptionResult.Success)
        assertEquals("Locked Vault Secret", (decrypted as VaultPayloadEncryptor.DecryptionResult.Success).note.title)
    }

    // =========================================================================
    // 5. F14: Preference Restore Strictly Enforces PREF_FILES Allowlist
    // =========================================================================

    @Test
    fun f14_backupRestoreRejectsDisallowedPreferenceFiles() = runBlocking {
        // Set a legitimate vault status in focus_notes_archive_vault_security
        val vaultPrefs = context.getSharedPreferences("focus_notes_archive_vault_security", Context.MODE_PRIVATE)
        vaultPrefs.edit().putString("vault_status", "legitimate_enabled").commit()

        // Construct a backup archive containing a malicious/disallowed preference file:
        // "focus_notes_archive_vault_security" attempting to overwrite security credentials!
        val rootJson = JSONObject().apply {
            put("version", 1)
            put("createdAt", System.currentTimeMillis())
            put("notes", JSONArray())
            put("restrictions", JSONArray())
            put("schedules", JSONArray())
            put("tasks", JSONArray())
            put("habits", JSONArray())
            put("drillSessions", JSONArray())

            val prefsObj = JSONObject()
            // Disallowed security preference file
            val maliciousObj = JSONObject().apply {
                put("vault_status", "attacker_spoofed_status")
            }
            prefsObj.put("focus_notes_archive_vault_security", maliciousObj)

            // Allowed preference file
            val allowedObj = JSONObject().apply {
                put("dark_mode", true)
            }
            prefsObj.put("focus_prefs", allowedObj)

            put("preferences", prefsObj)
        }

        // Package into password-protected backup file
        val backupFile = File(context.cacheDir, "malicious_prefs_backup.focusbackup")
        val password = "StrongPassword123!"
        FileOutputStream(backupFile).use { fos ->
            CryptoBackupEngine.openEncryptingStream(fos, password.toCharArray()).use { cos ->
                ZipOutputStream(BufferedOutputStream(cos)).use { zos ->
                    zos.putNextEntry(ZipEntry("data.json"))
                    zos.write(rootJson.toString().toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
        }

        // Restore backup
        val restoreResult = BackupRestoreManager.restoreEncryptedBackup(
            context = context,
            sourceUri = Uri.fromFile(backupFile),
            password = password
        )
        assertTrue("Restore executes successfully", restoreResult.isSuccess)

        // Verify the disallowed preference was REJECTED and NOT overwritten
        assertEquals(
            "Security preference file must NOT be modified by restore (allowlist enforcement)",
            "legitimate_enabled",
            vaultPrefs.getString("vault_status", null)
        )

        // Verify allowed preference was restored
        val focusPrefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        assertTrue("Allowed preference file must be restored", focusPrefs.getBoolean("dark_mode", false))
    }
}
