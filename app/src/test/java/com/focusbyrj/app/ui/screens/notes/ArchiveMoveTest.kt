package com.focusbyrj.app.ui.screens.notes

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.note.*
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import kotlinx.coroutines.runBlocking
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
class ArchiveMoveTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusApplication>()
    private val dao get() = NoteDatabase.getInstance(app).noteDao()
    private lateinit var model: NotesViewModel
    private lateinit var store: androidx.lifecycle.ViewModelStore
    @Before fun setup() = runBlocking {
        dao.deleteAllNotes()
        NotesViewModel.latestNotesCache.clear()
        ArchiveVaultSecurity.lockVault()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        model = NotesViewModel(app)
        store = androidx.lifecycle.ViewModelStore().also { it.put("archive-test", model) }
    }
    @After fun cleanup() = runBlocking {
        model.cancelArchiveMoveAuthorization()
        store.clear()
        NotesViewModel.latestNotesCache.clear()
        for (name in listOf("pending_vault_draft.enc", "pending_vault_draft.vault"))
            for (suffix in listOf("", ".bak", ".new")) java.io.File(app.filesDir, name + suffix).delete()
        ArchiveVaultSecurity.lockVault()
        dao.deleteAllNotes()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        Unit
    }
    private fun awaitFinished() {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (model.archiveMove.value.phase == NotesViewModel.ArchiveMovePhase.SAVING && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10)
        }
        assertNotEquals("Archive move timed out", NotesViewModel.ArchiveMovePhase.SAVING, model.archiveMove.value.phase)
    }
    private fun enableLockedVault() {
        assertTrue(ArchiveVaultSecurity.setPasscode(app, "123456")); ArchiveVaultSecurity.lockVault()
    }
    private fun authorize() {
        assertTrue(model.verifyArchiveMovePin("123456") is ArchiveVaultSecurity.VerifyResult.Success)
        model.continueArchiveMove()
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        awaitFinished()
    }

    @Test fun lockedCardMoveAsksInlineAndEncryptsOnlyAfterCorrectPin() = runBlocking {
        val note = NoteEntity(id = 81001, title = "Private title", content = "Private content", isPinned = true)
        dao.insertNote(note); enableLockedVault()
        model.archiveNote(note)
        assertEquals(NotesViewModel.ArchiveMovePhase.UNLOCK, model.archiveMove.value.phase)
        assertEquals(note, dao.getNoteByIdSync(note.id))
        assertTrue(model.verifyArchiveMovePin("111111") is ArchiveVaultSecurity.VerifyResult.Incorrect)
        assertEquals(note, dao.getNoteByIdSync(note.id))
        authorize()
        assertEquals(NotesViewModel.ArchiveMovePhase.SUCCESS, model.archiveMove.value.phase)
        val stored = dao.getNoteByIdSync(note.id)!!
        assertTrue(stored.isArchived); assertFalse(stored.isPinned)
        assertTrue(VaultPayloadEncryptor.isVaultEncrypted(stored))
        assertNotEquals(note.content, stored.content)
        assertEquals(NoteFolder.NOTES, model.currentFolder.value)
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "123456") is ArchiveVaultSecurity.VerifyResult.Success)
        val result = VaultPayloadEncryptor.tryDecryptNotePayload(stored) as VaultPayloadEncryptor.DecryptionResult.Success
        assertEquals(note.title, result.note.title); assertEquals(note.content, result.note.content)
    }

    @Test fun cancellingAuthorizationKeepsSelectionAndOriginalData() = runBlocking {
        val note = NoteEntity(id = 81002, title = "Keep me")
        dao.insertNote(note); enableLockedVault(); model.selectNote(note.id)
        model.archiveSelectedNotes()
        assertTrue(model.verifyArchiveMovePin("123456") is ArchiveVaultSecurity.VerifyResult.Success)
        model.cancelArchiveMoveAuthorization()
        model.continueArchiveMove() // A stale sheet callback cannot use the cancelled authorization.
        assertEquals(note, dao.getNoteByIdSync(note.id))
        assertEquals(setOf(note.id), model.selectedNoteIds.value)
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
    }

    @Test fun newEditorNoteArchivesLatestDraftOnceBeforeAutosaveDebounce() = runBlocking {
        model.openNewNote()
        model.updateEditorTitle("Brand new")
        model.updateEditorContent("Latest unsaved content")
        model.archiveCurrentNote()
        assertNotNull(model.editingState.value)
        awaitFinished()
        assertEquals(NotesViewModel.ArchiveMovePhase.SUCCESS, model.archiveMove.value.phase)
        assertNull(model.editingState.value)
        val rows = dao.getAllNotesList()
        assertEquals(1, rows.size)
        assertTrue(rows.single().isArchived)
        assertEquals("Brand new", rows.single().title)
        assertEquals("Latest unsaved content", rows.single().content)
        model.archiveCurrentNote()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, dao.getAllNotesList().size)
    }

    @Test fun protectedEditorMoveKeepsDraftUntilAuthorizationAndSaveComplete() = runBlocking {
        val original = NoteEntity(id = 81003, title = "Original", content = "Before")
        dao.insertNote(original); enableLockedVault()
        model.openExistingNote(original); model.updateEditorContent("Newest private draft")
        model.archiveCurrentNote()
        assertNotNull(model.editingState.value)
        assertEquals(NotesViewModel.ArchiveMovePhase.UNLOCK, model.archiveMove.value.phase)
        model.updateEditorContent("Newest draft received during PIN entry")
        authorize()
        assertEquals(NotesViewModel.ArchiveMovePhase.SUCCESS, model.archiveMove.value.phase)
        assertNull(model.editingState.value)
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "123456") is ArchiveVaultSecurity.VerifyResult.Success)
        val stored = dao.getNoteByIdSync(original.id)!!
        val plain = (VaultPayloadEncryptor.tryDecryptNotePayload(stored) as VaultPayloadEncryptor.DecryptionResult.Success).note
        assertEquals("Newest draft received during PIN entry", plain.content)
        assertEquals(1, dao.getAllNotesList().size)
    }

    @Test fun editorConflictKeepsLatestLocalDraftAndDoesNotOverwriteOtherWriter() = runBlocking {
        val original = NoteEntity(id = 81004, title = "Original", content = "Before")
        dao.insertNote(original); model.openExistingNote(original)
        model.updateEditorContent("Keep this unsaved draft")
        dao.updateNote(original.copy(content = "Changed elsewhere"))
        model.archiveCurrentNote(); awaitFinished()
        assertEquals(NotesViewModel.ArchiveMovePhase.ERROR, model.archiveMove.value.phase)
        assertEquals("Keep this unsaved draft", model.editingState.value!!.content)
        assertEquals("Changed elsewhere", dao.getNoteByIdSync(original.id)!!.content)
        assertFalse(dao.getNoteByIdSync(original.id)!!.isArchived)
        assertFalse(model.archiveMove.value.editorClosed)
    }

    @Test fun selectedBatchRollsBackIfAnyTargetIsMissing() = runBlocking {
        val note = NoteEntity(id = 81005, title = "Keep original", isPinned = true)
        dao.insertNote(note)
        model.selectNote(note.id); model.selectNote(99999)
        model.archiveSelectedNotes(); awaitFinished()
        assertEquals(NotesViewModel.ArchiveMovePhase.ERROR, model.archiveMove.value.phase)
        assertEquals(note, dao.getNoteByIdSync(note.id))
        assertEquals(setOf(note.id, 99999L), model.selectedNoteIds.value)
    }

    @Test fun restoreInvalidatesPendingMoveWithoutChangingNotesOrDiscardingDraft() = runBlocking {
        val note = NoteEntity(id = 81006, title = "Existing")
        dao.insertNote(note); enableLockedVault()
        model.openExistingNote(note); model.updateEditorContent("Retained draft")
        model.archiveCurrentNote(); NotesStorageVersion.replaced()
        authorize()
        assertEquals(NotesViewModel.ArchiveMovePhase.ERROR, model.archiveMove.value.phase)
        assertFalse(dao.getNoteByIdSync(note.id)!!.isArchived)
        assertEquals("Retained draft", model.editingState.value!!.content)
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
    }

    @Test fun unarchiveEditorSavesRecentEditsToSameNoteAndRelocksVault() = runBlocking {
        assertTrue(ArchiveVaultSecurity.setPasscode(app, "123456"))
        val private = NoteEntity(id = 81007, title = "Private", content = "Old", isArchived = true)
        val encrypted = VaultPayloadEncryptor.encryptNotePayload(private, context = app)
        dao.insertNote(encrypted); model.openExistingNote(encrypted)
        model.updateEditorContent("Latest public content")
        model.unarchiveCurrentNote(); awaitFinished()
        assertEquals(NotesViewModel.ArchiveMovePhase.SUCCESS, model.archiveMove.value.phase)
        assertNull(model.editingState.value)
        val stored = dao.getNoteByIdSync(private.id)!!
        assertFalse(stored.isArchived)
        assertEquals("Latest public content", stored.content)
        assertEquals(1, dao.getAllNotesList().size)
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
    }

    @Test fun repositoryReportsLockedAndCorruptFailuresWithoutChangingCiphertext() = runBlocking {
        val repo = NoteRepository(dao, app)
        val note = NoteEntity(id = 81008, title = "Keep data")
        dao.insertNote(note); enableLockedVault()
        assertTrue(repo.setArchived(note.id, true) is NoteRepository.SaveResult.Locked)
        assertEquals(note, dao.getNoteByIdSync(note.id))
        val corrupt = note.copy(isArchived = true, content = "ENC_VAULT_V1:broken")
        dao.updateNote(corrupt)
        val original = dao.getNoteByIdSync(note.id)!!
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "123456") is ArchiveVaultSecurity.VerifyResult.Success)
        assertTrue(repo.setArchived(note.id, false) is NoteRepository.SaveResult.Corrupt)
        assertEquals(original, dao.getNoteByIdSync(note.id))
    }

    @Test fun unsupportedCredentialFormatReportsErrorAndNeverResetsVault() = runBlocking {
        val note = NoteEntity(id = 81009, title = "Keep original")
        dao.insertNote(note); enableLockedVault()
        val prefs = app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0)
        prefs.edit().putString("kdf_type", "argon2id").commit()
        val originalPrefs = prefs.all
        model.archiveNote(note)
        assertTrue(model.verifyArchiveMovePin("123456") is ArchiveVaultSecurity.VerifyResult.Error)
        assertEquals(NotesViewModel.ArchiveMovePhase.UNLOCK, model.archiveMove.value.phase)
        assertEquals(note, dao.getNoteByIdSync(note.id))
        assertEquals(originalPrefs, prefs.all)
    }
    @Test fun anEarlierRecoveryDraftCannotBeOverwrittenByANewMove() = runBlocking {
        val note = NoteEntity(id = 81010, title = "Keep note")
        dao.insertNote(note)
        val recovery = java.io.File(app.filesDir, "pending_vault_draft.enc")
        val bytes = "Synthetic earlier encrypted recovery file".toByteArray()
        recovery.writeBytes(bytes)
        model.archiveNote(note)
        assertEquals(NotesViewModel.ArchiveMovePhase.ERROR, model.archiveMove.value.phase)
        assertEquals(note, dao.getNoteByIdSync(note.id))
        assertArrayEquals(bytes, recovery.readBytes())
    }

    @Test fun aStalePlaintextPrivateCardCannotOpenAfterRelocking() = runBlocking {
        assertTrue(ArchiveVaultSecurity.setPasscode(app, "123456"))
        val plain = NoteEntity(id = 81011, title = "Private cached title", isArchived = true)
        val encrypted = VaultPayloadEncryptor.encryptNotePayload(plain, context = app)
        dao.insertNote(encrypted)
        NotesViewModel.latestNotesCache[plain.id] = plain
        ArchiveVaultSecurity.lockVault()
        model.openExistingNote(plain)
        assertNull(model.editingState.value)
        model.openExistingNote(plain.copy(isArchived = false)) // A stale active card must not bypass a newer private cache entry.
        assertNull(model.editingState.value)
    }

    @Test fun repositoryWithNoSecurityContextCannotWriteUnprotectedArchive() = runBlocking {
        val note = NoteEntity(id = 81012, title = "Keep protection")
        dao.insertNote(note)
        assertTrue(NoteRepository(dao).setArchived(note.id, true) is NoteRepository.SaveResult.Locked)
        assertEquals(note, dao.getNoteByIdSync(note.id))
    }

    private fun persistenceMutex(): kotlinx.coroutines.sync.Mutex = model.javaClass.getDeclaredField("persistMutex").run {
        isAccessible = true; get(model) as kotlinx.coroutines.sync.Mutex
    }

    @Test fun screenDestructionDuringNewNoteMoveFinishesOneDurableSave() = runBlocking {
        model.openNewNote(); model.updateEditorContent("Keep after screen closes")
        val mutex = persistenceMutex(); mutex.lock()
        try { model.archiveCurrentNote(); store.clear() } finally { mutex.unlock() }
        awaitFinished()
        assertEquals(NotesViewModel.ArchiveMovePhase.SUCCESS, model.archiveMove.value.phase)
        val rows = dao.getAllNotesList()
        assertEquals(1, rows.size); assertTrue(rows.single().isArchived)
        assertEquals("Keep after screen closes", rows.single().content)
    }

    @Test fun screenDestructionDuringPrivateMoveSavesEncryptedRecoveryBeforeWipingKey() = runBlocking {
        assertTrue(ArchiveVaultSecurity.setPasscode(app, "123456"))
        val original = VaultPayloadEncryptor.encryptNotePayload(NoteEntity(id = 81013,
            title = "Private", content = "Old body", isArchived = true), context = app)
        dao.insertNote(original); model.openExistingNote(original)
        model.updateEditorContent("Private unsaved draft after screen closes")
        val mutex = persistenceMutex(); mutex.lock()
        try { model.unarchiveCurrentNote(); store.clear() } finally { mutex.unlock() }
        awaitFinished()
        assertEquals(NotesViewModel.ArchiveMovePhase.ERROR, model.archiveMove.value.phase)
        assertEquals(original, dao.getNoteByIdSync(original.id))
        assertNull(model.editingState.value); assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        val recovery = java.io.File(app.filesDir, "pending_vault_draft.enc")
        assertTrue(recovery.isFile)
        assertFalse(recovery.readBytes().decodeToString().contains("Private unsaved draft"))
        val bytes = com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(recovery)!!
        try {
            val payload = org.json.JSONObject(bytes.decodeToString())
            assertTrue(payload.getString("content").startsWith("ENC_VAULT_V1:"))
            assertFalse(bytes.decodeToString().contains("Private unsaved draft"))
        } finally { bytes.fill(0) }
    }

    @Test fun lateEditorChangeBeforeWriteRejectsMoveAndKeepsNewerDraft() = runBlocking {
        model.openNewNote(); model.updateEditorContent("Initial snapshot")
        val mutex = persistenceMutex(); mutex.lock()
        try {
            model.archiveCurrentNote()
            model.updateEditorContent("A later change that must survive")
        } finally { mutex.unlock() }
        awaitFinished()
        assertEquals(NotesViewModel.ArchiveMovePhase.ERROR, model.archiveMove.value.phase)
        assertEquals("A later change that must survive", model.editingState.value!!.content)
        assertTrue(dao.getAllNotesList().none { it.isArchived })
    }

    @Test fun anInterruptedAtomicRecoveryWriteCanRecoverBeforeAnotherMove() = runBlocking {
        assertTrue(ArchiveVaultSecurity.setPasscode(app, "123456"))
        val privateDraft = VaultPayloadEncryptor.encryptNotePayload(NoteEntity(title = "Recover earlier draft",
            content = "Earlier body", isArchived = true), context = app)
        val json = org.json.JSONObject().put("id", 0).put("title", privateDraft.title)
            .put("content", privateDraft.content).put("isArchived", true).put("createdAt", 1).put("updatedAt", 1)
        val file = java.io.File(app.filesDir, "pending_vault_draft.enc")
        com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(file, json.toString().toByteArray())
        val backup = java.io.File(file.path + ".bak")
        assertTrue(file.renameTo(backup)); ArchiveVaultSecurity.lockVault()
        assertTrue(model.verifyVaultPasscode("123456") is ArchiveVaultSecurity.VerifyResult.Success)
        val deadline = System.nanoTime() + 30_000_000_000L
        while ((backup.exists() || file.exists() || dao.getAllNotesList().isEmpty()) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10)
        }
        assertFalse(backup.exists()); assertFalse(file.exists())
        val rows = dao.getAllNotesList()
        assertEquals(1, rows.size)
        val recovered = VaultPayloadEncryptor.tryDecryptNotePayload(rows.single()) as VaultPayloadEncryptor.DecryptionResult.Success
        assertEquals("Recover earlier draft", recovered.note.title)
    }

}
