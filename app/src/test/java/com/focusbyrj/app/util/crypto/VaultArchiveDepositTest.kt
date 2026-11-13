package com.focusbyrj.app.util.crypto

import android.net.Uri
import android.os.Looper
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.note.*
import com.focusbyrj.app.ui.screens.notes.NotesViewModel
import com.focusbyrj.app.util.backup.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34], application = FocusApplication::class)
class VaultArchiveDepositTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusApplication>()
    private val dao get() = NoteDatabase.getInstance(app).noteDao()
    private lateinit var model: NotesViewModel
    private lateinit var store: androidx.lifecycle.ViewModelStore
    @Before fun setup() = runBlocking {
        dao.deleteAllNotes(); NotesViewModel.latestNotesCache.clear()
        VaultArchiveDeposit.disable(app)
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        assertTrue(ArchiveVaultSecurity.setPasscode(app, "123456")); ArchiveVaultSecurity.lockVault()
        model = NotesViewModel(app)
        store = androidx.lifecycle.ViewModelStore().also { it.put("deposit-test", model) }
    }
    @After fun cleanup() = runBlocking {
        model.cancelArchiveMoveAuthorization(); store.clear()
        ArchiveVaultSecurity.lockVault(); VaultArchiveDeposit.disable(app); NotesViewModel.latestNotesCache.clear()
        dao.deleteAllNotes()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        File(app.cacheDir, "sealed-test.focusbackup").delete()
        Unit
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (!condition() && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        assertTrue("Timed out; option=${model.archiveOption.value}, move=${model.archiveMove.value}", condition())
    }
    private fun enable() {
        assertTrue(model.verifyArchiveOptionPin("123456") is ArchiveVaultSecurity.VerifyResult.Success)
        model.setArchiveWithoutPin(true)
        await { !model.archiveOption.value.busy }
        assertFalse(model.archiveOption.value.message, model.archiveOption.value.error)
        assertTrue(model.archiveWithoutPin.value); assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
    }
    private fun authorizeMove() {
        assertTrue(model.verifyArchiveMovePin("123456") is ArchiveVaultSecurity.VerifyResult.Success)
        model.continueArchiveMove()
        await { model.archiveMove.value.phase != NotesViewModel.ArchiveMovePhase.SAVING }
        assertEquals(model.archiveMove.value.message, NotesViewModel.ArchiveMovePhase.SUCCESS, model.archiveMove.value.phase)
    }
    private fun privateNote(capability: VaultArchiveDeposit.WriteCapability, id: Long = 83001): NoteEntity =
        VaultArchiveDeposit.encrypt(app, NoteEntity(id = id, title = "Secret title", content = "Secret body", isArchived = true,
            labelsJson = "[\"private label\"]", colorKey = "coral", fontKey = "serif"), capability)

    @Test fun bothMethodsRequirePinToViewOrUnarchiveAndKeepAllFields() = runBlocking {
        for (withoutPin in listOf(false, true)) {
            if (withoutPin) enable()
            val note = NoteEntity(id = if (withoutPin) 83002 else 83003, title = "Private title", content = "Private content",
                labelsJson = "[\"Family\"]", colorKey = "coral", fontKey = "serif", isPinned = true)
            dao.insertNote(note); model.dismissArchiveMoveResult()
            model.archiveNote(note)
            if (!withoutPin) {
                assertEquals(NotesViewModel.ArchiveMovePhase.UNLOCK, model.archiveMove.value.phase); authorizeMove()
            } else {
                assertNotEquals(NotesViewModel.ArchiveMovePhase.UNLOCK, model.archiveMove.value.phase)
                await { model.archiveMove.value.phase != NotesViewModel.ArchiveMovePhase.SAVING }
                assertEquals(NotesViewModel.ArchiveMovePhase.SUCCESS, model.archiveMove.value.phase)
            }
            val stored = dao.getNoteByIdSync(note.id)!!
            assertTrue(VaultPayloadEncryptor.isVaultEncrypted(stored)); assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
            assertEquals(VaultPayloadEncryptor.DecryptionResult.VaultLocked, VaultPayloadEncryptor.tryDecryptNotePayload(stored))
            model.openExistingNote(stored); assertNull(model.editingState.value)
            model.dismissArchiveMoveResult(); model.unarchiveNote(stored)
            assertEquals(NotesViewModel.ArchiveMovePhase.UNLOCK, model.archiveMove.value.phase)
            assertTrue(model.verifyArchiveMovePin("111111") is ArchiveVaultSecurity.VerifyResult.Incorrect)
            assertEquals(stored, dao.getNoteByIdSync(note.id)); authorizeMove()
            val restored = dao.getNoteByIdSync(note.id)!!
            assertFalse(restored.isArchived); assertFalse(restored.isPinned)
            assertEquals(note.title, restored.title); assertEquals(note.content, restored.content)
            assertEquals(note.labelsJson, restored.labelsJson); assertEquals(note.colorKey, restored.colorKey)
            assertEquals(note.fontKey, restored.fontKey); assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        }
    }

    @Test fun enablingRequiresAuthorizationAndNeverPersistsAnUnlockedVaultKey() {
        model.setArchiveWithoutPin(true)
        assertTrue(model.archiveOption.value.error); assertNull(VaultArchiveDeposit.readCapability(app))
        enable()
        val config = File(app.noBackupFilesDir, "vault_archive_deposit.enc")
        val bytes = EncryptedMediaStorage.readDecryptedBytes(config)!!
        try {
            val json = org.json.JSONObject(bytes.decodeToString())
            assertEquals(setOf("version", "binding", "public", "iv", "wrapped"), json.keys().asSequence().toSet())
            assertEquals(48, Base64.decode(json.getString("wrapped"), Base64.NO_WRAP).size)
        } finally { bytes.fill(0) }
        assertNull(ArchiveVaultSecurity.getActiveVaultSubKey())
    }

    @Test fun everyEnvelopeComponentIsAuthenticatedAndWrongKeyCannotReadIt() {
        enable(); val sealed = privateNote(VaultArchiveDeposit.readCapability(app)!!)
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "123456") is ArchiveVaultSecurity.VerifyResult.Success)
        val key = ArchiveVaultSecurity.getActiveVaultSubKey()!!; ArchiveVaultSecurity.lockVault()
        try {
            val prefix = sealed.content.substringBefore("sealed-v1:") + "sealed-v1:"
            val parts = sealed.content.substringAfter("sealed-v1:").split(":")
            for (index in parts.indices) {
                val changed = parts.toMutableList()
                val bytes = Base64.decode(changed[index], Base64.NO_WRAP)
                bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
                changed[index] = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val damaged = sealed.copy(content = prefix + changed.joinToString(":"))
                assertTrue("Part $index must authenticate", VaultPayloadEncryptor.tryDecryptNotePayload(damaged, key) is VaultPayloadEncryptor.DecryptionResult.Corrupted)
            }
            assertTrue(VaultPayloadEncryptor.tryDecryptNotePayload(sealed, ByteArray(32) { 7 }) is VaultPayloadEncryptor.DecryptionResult.Corrupted)
            assertTrue(VaultPayloadEncryptor.tryDecryptNotePayload(sealed.copy(content = sealed.content.dropLast(10)), key) is VaultPayloadEncryptor.DecryptionResult.Corrupted)
            val opened = VaultPayloadEncryptor.tryDecryptNotePayload(sealed, key) as VaultPayloadEncryptor.DecryptionResult.Success
            assertEquals("Secret body", opened.note.content)
        } finally { key.fill(0) }
    }

    @Test fun disablingOrRecreatingTheOptionNeverStrandsEarlierNotes() {
        enable(); val first = privateNote(VaultArchiveDeposit.readCapability(app)!!)
        model.setArchiveWithoutPin(false); await { !model.archiveOption.value.busy }
        assertNull(VaultArchiveDeposit.readCapability(app))
        enable(); val second = privateNote(VaultArchiveDeposit.readCapability(app)!!, 83004)
        assertNotEquals(first.content, second.content)
        VaultArchiveDeposit.disable(app)
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "123456") is ArchiveVaultSecurity.VerifyResult.Success)
        for (note in listOf(first, second)) {
            val result = VaultPayloadEncryptor.tryDecryptNotePayload(note) as VaultPayloadEncryptor.DecryptionResult.Success
            assertEquals("Secret title", result.note.title); assertEquals("Secret body", result.note.content)
        }
    }

    @Test fun tamperedConfigurationFallsBackToPinAndRetainsExistingNotes() = runBlocking {
        enable(); val original = privateNote(VaultArchiveDeposit.readCapability(app)!!)
        dao.insertNote(original)
        val config = File(app.noBackupFilesDir, "vault_archive_deposit.enc")
        val damaged = config.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        config.writeBytes(damaged)
        assertNull(VaultArchiveDeposit.readCapability(app)); model.refreshVaultStatus()
        assertFalse(model.archiveWithoutPin.value)
        val active = NoteEntity(id = 83005, title = "Keep active")
        dao.insertNote(active); model.archiveNote(active)
        assertEquals(NotesViewModel.ArchiveMovePhase.UNLOCK, model.archiveMove.value.phase)
        assertEquals(active, dao.getNoteByIdSync(active.id)); model.cancelArchiveMoveAuthorization()
        assertArrayEquals(damaged, config.readBytes())
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "123456") is ArchiveVaultSecurity.VerifyResult.Success)
        assertTrue(VaultPayloadEncryptor.tryDecryptNotePayload(original) is VaultPayloadEncryptor.DecryptionResult.Success)
    }

    @Test fun changingPinKeepsEarlierNotesReadableAndSafelyRequiresReenablingOption() {
        enable(); val original = privateNote(VaultArchiveDeposit.readCapability(app)!!)
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "123456") is ArchiveVaultSecurity.VerifyResult.Success)
        assertTrue(model.setVaultPasscode("654321")); model.lockVault()
        assertNull(VaultArchiveDeposit.readCapability(app))
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "654321") is ArchiveVaultSecurity.VerifyResult.Success)
        assertTrue(VaultPayloadEncryptor.tryDecryptNotePayload(original) is VaultPayloadEncryptor.DecryptionResult.Success)
    }

    @Test fun staleSetupAuthorizationCannotPublishKeysForChangedCredentials() {
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "123456") is ArchiveVaultSecurity.VerifyResult.Success)
        val authorization = VaultArchiveDeposit.captureAuthorization(app)
        try {
            assertTrue(ArchiveVaultSecurity.setPasscode(app, "654321"))
            try { VaultArchiveDeposit.enable(app, authorization); fail("Stale authorization must fail") }
            catch (_: IllegalStateException) { }
            assertNull(VaultArchiveDeposit.readCapability(app))
        } finally { authorization.close() }
        assertTrue(authorization.key.all { it == 0.toByte() })
    }

    @Test fun pinFreeNewEditorNoteSavesLatestDraftOnceWithoutUnlocking() = runBlocking {
        enable(); model.openNewNote(); model.updateEditorContent("Newest PIN-free draft")
        model.archiveCurrentNote(); await { model.archiveMove.value.phase != NotesViewModel.ArchiveMovePhase.SAVING }
        assertEquals(NotesViewModel.ArchiveMovePhase.SUCCESS, model.archiveMove.value.phase)
        assertNull(model.editingState.value); assertEquals(1, dao.getAllNotesList().size)
        val stored = dao.getAllNotesList().single()
        assertTrue(stored.content.contains("sealed-v1:")); assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "123456") is ArchiveVaultSecurity.VerifyResult.Success)
        assertEquals("Newest PIN-free draft", (VaultPayloadEncryptor.tryDecryptNotePayload(stored) as VaultPayloadEncryptor.DecryptionResult.Success).note.content)
    }

    @Test fun sealedNotesAndAttachmentsBackupAndRestoreWithoutDuplicateOrConfigurationDependency() = runBlocking {
        enable()
        val image = File(app.filesDir, "keep_images/sealed-roundtrip.bin")
        EncryptedMediaStorage.writeEncryptedBytes(image, "Original attachment".toByteArray())
        val plain = NoteEntity(id = 83006, title = "Portable private note", content = "Portable private content", isArchived = true,
            imageUrisJson = JSONArray(listOf(image.path)).toString(), labelsJson = "[\"Keep label\"]")
        val stored = VaultArchiveDeposit.encrypt(app, plain, VaultArchiveDeposit.readCapability(app)!!)
        dao.insertNote(stored)
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "123456") is ArchiveVaultSecurity.VerifyResult.Success)
        val key = ArchiveVaultSecurity.getActiveVaultSubKey()!!; ArchiveVaultSecurity.lockVault()
        val prepared = try { ManualBackupFiles.prepare(app, "backup-password", key) } finally { key.fill(0) }
        try {
            assertTrue(BackupRestoreManager.verifyEncryptedBackup(app, Uri.fromFile(prepared.file), "backup-password").isSuccess)
            VaultArchiveDeposit.disable(app)
            repeat(2) {
                BackupRestoreManager.restoreEncryptedBackup(app, Uri.fromFile(prepared.file), "backup-password",
                    cleanRestore = true, vaultPin = "123456").getOrThrow()
                assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
                assertEquals(1, dao.getAllNotesList().size)
            }
            assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "123456") is ArchiveVaultSecurity.VerifyResult.Success)
            val restored = (VaultPayloadEncryptor.tryDecryptNotePayload(dao.getAllNotesList().single()) as VaultPayloadEncryptor.DecryptionResult.Success).note
            assertEquals(plain.title, restored.title); assertEquals(plain.content, restored.content)
            assertEquals(plain.labelsJson, restored.labelsJson)
            assertEquals("Original attachment", EncryptedMediaStorage.readDecryptedBytes(File(restored.getImageUris().single()))!!.decodeToString())
        } finally { ManualBackupFiles.discard(prepared); image.delete() }
    }
    @Test fun automaticBackupNeedsSeparateReadAuthorizationAndNeverOmitsSealedNotes() = runBlocking {
        enable(); val original = privateNote(VaultArchiveDeposit.readCapability(app)!!)
        dao.insertNote(original)
        val file = File(app.cacheDir, "sealed-test.focusbackup")
        val denied = BackupRestoreManager.createAutomaticEncryptedBackup(app, Uri.fromFile(file), "backup-password", null)
        assertTrue(denied.isFailure); assertEquals(original, dao.getNoteByIdSync(original.id))
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "123456") is ArchiveVaultSecurity.VerifyResult.Success)
        val key = ArchiveVaultSecurity.getActiveVaultSubKey()!!; ArchiveVaultSecurity.lockVault()
        try { BackupRestoreManager.createAutomaticEncryptedBackup(app, Uri.fromFile(file), "backup-password", key).getOrThrow() }
        finally { key.fill(0) }
        assertTrue(BackupRestoreManager.verifyEncryptedBackup(app, Uri.fromFile(file), "backup-password").isSuccess)
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app)); assertEquals(original, dao.getNoteByIdSync(original.id))
    }

    @Test fun pinFreeBatchAndFailedUnarchiveAreAtomicAndKeepSelection() = runBlocking {
        enable()
        val one = NoteEntity(id = 83007, title = "First private note")
        val two = NoteEntity(id = 83008, title = "Second private note")
        dao.insertNotes(listOf(one, two)); model.selectNote(one.id); model.selectNote(two.id)
        model.archiveSelectedNotes(); await { model.archiveMove.value.phase != NotesViewModel.ArchiveMovePhase.SAVING }
        assertEquals(NotesViewModel.ArchiveMovePhase.SUCCESS, model.archiveMove.value.phase)
        val first = dao.getNoteByIdSync(one.id)!!
        val second = dao.getNoteByIdSync(two.id)!!
        assertTrue(first.content.contains("sealed-v1:")); assertTrue(second.content.contains("sealed-v1:"))
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app)); assertTrue(model.selectedNoteIds.value.isEmpty())
        dao.updateNote(second.copy(content = second.content.dropLast(10)))
        val damaged = dao.getNoteByIdSync(two.id)!!
        model.selectNote(one.id); model.selectNote(two.id); model.unarchiveSelectedNotes()
        assertEquals(NotesViewModel.ArchiveMovePhase.UNLOCK, model.archiveMove.value.phase)
        assertTrue(model.verifyArchiveMovePin("123456") is ArchiveVaultSecurity.VerifyResult.Success)
        model.continueArchiveMove(); await { model.archiveMove.value.phase != NotesViewModel.ArchiveMovePhase.SAVING }
        assertEquals(NotesViewModel.ArchiveMovePhase.ERROR, model.archiveMove.value.phase)
        assertEquals(first, dao.getNoteByIdSync(one.id)); assertEquals(damaged, dao.getNoteByIdSync(two.id))
        assertEquals(setOf(one.id, two.id), model.selectedNoteIds.value)
        assertTrue(dao.getAllActiveNotesSync().isEmpty()); assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
    }

    @Test fun changedOrUnsupportedAuthenticationMetadataCannotAcceptPinFreeWrites() = runBlocking {
        enable()
        val prefs = app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0)
        val originalKdf = prefs.getString("kdf_type", null)
        val verifier = prefs.getString("pin_verifier_v2", null)
        val note = NoteEntity(id = 83009, title = "Keep unarchived")
        dao.insertNote(note)
        prefs.edit().putString("kdf_type", "argon2id").commit()
        assertNull(VaultArchiveDeposit.readCapability(app))
        model.archiveNote(note)
        assertEquals(NotesViewModel.ArchiveMovePhase.UNLOCK, model.archiveMove.value.phase)
        assertTrue(model.verifyArchiveMovePin("123456") is ArchiveVaultSecurity.VerifyResult.Error)
        assertEquals(note, dao.getNoteByIdSync(note.id)); model.cancelArchiveMoveAuthorization()
        prefs.edit().putString("kdf_type", originalKdf).putString("pin_verifier_v2", "changed-verifier").commit()
        assertNull(VaultArchiveDeposit.readCapability(app))
        prefs.edit().putString("pin_verifier_v2", verifier).commit()
        assertNotNull(VaultArchiveDeposit.readCapability(app))
    }

}
