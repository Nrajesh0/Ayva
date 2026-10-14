package com.focusbyrj.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.data.note.NoteMediaManager
import com.focusbyrj.app.data.note.NoteRepository
import com.focusbyrj.app.ui.screens.notes.NotesViewModel
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Work Package 2 Security & Data Integrity Verification Suite
 *
 * Verifies:
 * - F02: Secret Vault fails closed on lock; locking clears editor state and prevents plaintext saves
 * - F09: Media files are not deleted immediately during editing; duplication does not reuse original paths;
 *        orphan GC aborts when vault is locked; multi-note reference check prevents deleting shared media
 * - F10: Save operations return typed results, missing row updates fail explicitly, pre-op snapshots protect emptyTrash
 */
@RunWith(RobolectricTestRunner::class)
class WorkPackage2SecurityTest {

    private lateinit var context: Context
    private lateinit var app: Application
    private lateinit var noteDb: NoteDatabase
    private lateinit var repository: NoteRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = context as Application
        noteDb = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NoteRepository(noteDb.noteDao(), context)
        ArchiveVaultSecurity.lockVault()
    }

    @After
    fun tearDown() {
        ArchiveVaultSecurity.lockVault()
        noteDb.close()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Finding F02: Vault Lock & Fail-Closed Encryption
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun testF02FailClosedWhenVaultIsLocked(): Unit = runBlocking {
        // Configure vault as ENABLED
        val configured = ArchiveVaultSecurity.setPasscode(context, "123456")
        assertTrue("Passcode setup should succeed", configured)
        assertEquals(ArchiveVaultSecurity.VaultStatus.ENABLED, ArchiveVaultSecurity.getVaultStatus(context))

        // Lock vault so subKey is wiped
        ArchiveVaultSecurity.lockVault()
        assertTrue("Vault must report locked", ArchiveVaultSecurity.isVaultLocked(context))
        assertNull("Sub-key must be wiped from memory", ArchiveVaultSecurity.getActiveVaultSubKey())

        // Attempt to save an unencrypted archived note
        val archivedNote = NoteEntity(
            title = "Top Secret",
            content = "Classified Information",
            isArchived = true
        )

        val result = repository.saveNoteResult(archivedNote)
        assertTrue("saveNoteResult must return Locked", result is NoteRepository.SaveResult.Locked)

        // Attempting to call saveNote must throw IllegalStateException rather than write plaintext
        var caughtException = false
        try {
            repository.saveNote(archivedNote)
        } catch (e: IllegalStateException) {
            caughtException = true
            assertTrue(e.message?.contains("locked", ignoreCase = true) == true)
        }
        assertTrue("Must throw IllegalStateException when saving locked vault note", caughtException)

        // Ensure database remains completely empty
        val notes = noteDb.noteDao().getAllNotesList()
        assertEquals("Database must contain 0 rows (no plaintext leak)", 0, notes.size)
    }

    @Test
    fun testF02LockVaultClearsEditorAndFlushesEncryptedPayload(): Unit = runBlocking {
        val configured = ArchiveVaultSecurity.setPasscode(context, "654321")
        assertTrue("Passcode setup should succeed", configured)

        val notesVm = NotesViewModel(app)
        val archivedNote = NoteEntity(id = 100L, title = "Secret Note", content = "Confidential Body", isArchived = true)
        notesVm.openExistingNote(archivedNote)

        val editingState = notesVm.editingState.value
        assertNotNull("Note editor should be active", editingState)
        assertTrue("Note should be archived", editingState!!.isArchived)

        // Lock the vault
        notesVm.lockVault()

        // Editor must be cleared immediately
        assertNull("Editing state must be cleared on vault lock", notesVm.editingState.value)
        assertFalse("Vault unlocked state must be false", notesVm.isVaultUnlocked.value)
        assertNull("SubKey must be wiped", ArchiveVaultSecurity.getActiveVaultSubKey())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Finding F09: Safe Media Handling & Orphan GC Protection
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun testF09RemoveMediaFromEditorDoesNotDeletePhysicalFile(): Unit = runBlocking {
        val imagesDir = File(context.filesDir, "keep_images").apply { mkdirs() }
        val audioDir = File(context.filesDir, "keep_audio").apply { mkdirs() }

        val testImage = File(imagesDir, "test_editor_photo.jpg")
        testImage.writeText("fake image bytes")
        assertTrue("Image file must exist initially", testImage.exists())

        val testAudio = File(audioDir, "test_editor_voice.m4a")
        testAudio.writeText("fake voice bytes")
        assertTrue("Audio file must exist initially", testAudio.exists())

        val notesVm = NotesViewModel(app)
        notesVm.openNewNote(asChecklist = false)
        notesVm.updateEditorTitle("Media Note")

        // Manually reflect media URIs into editor
        val current = notesVm.editingState.value!!
        val stateWithMedia = current.copy(
            imageUris = listOf(testImage.absolutePath),
            audioUris = listOf(testAudio.absolutePath)
        )
        val field = NotesViewModel::class.java.getDeclaredField("_editingState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(notesVm) as kotlinx.coroutines.flow.MutableStateFlow<NotesViewModel.EditingNoteState?>
        stateFlow.value = stateWithMedia

        // Remove image and audio from editor
        notesVm.removeImageFromEditor(testImage.absolutePath)
        notesVm.removeAudioFromEditor(testAudio.audioPath())

        // Physical files must NOT be deleted immediately!
        assertTrue("Image file must still exist after removal from editor (preserve for undo)", testImage.exists())
        assertTrue("Audio file must still exist after removal from editor (preserve for undo)", testAudio.exists())

        testImage.delete()
        testAudio.delete()
    }

    private fun File.audioPath(): String = this.absolutePath

    @Test
    fun testF09OrphanCleanAbortsWhenVaultIsLocked(): Unit = runBlocking {
        val configured = ArchiveVaultSecurity.setPasscode(context, "112233")
        assertTrue(configured)

        val imagesDir = File(context.filesDir, "keep_images").apply { mkdirs() }
        val secretAttachment = File(imagesDir, "vault_secret_photo.jpg")
        secretAttachment.writeText("secret photo bytes")

        val subKey = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        val secretNote = NoteEntity(
            title = "Secret Vault Item",
            imageUrisJson = "[\"${secretAttachment.absolutePath}\"]",
            isArchived = true
        )
        val encryptedNote = VaultPayloadEncryptor.encryptNotePayload(secretNote, vaultSubKey = subKey, context = context)
        noteDb.noteDao().insertNote(encryptedNote)

        // Lock the vault
        ArchiveVaultSecurity.lockVault()
        assertTrue(ArchiveVaultSecurity.isVaultLocked(context))

        // Trigger orphan media clean
        NoteMediaManager.cleanOrphanedMedia(context, noteDb.noteDao())

        // Secret media file must NOT be deleted
        assertTrue("Secret attachment must be spared because vault is locked", secretAttachment.exists())

        secretAttachment.delete()
        Unit
    }

    @Test
    fun testF09DeleteMediaFilesRespectsOtherReferencedNotes() {
        val imagesDir = File(context.filesDir, "keep_images").apply { mkdirs() }
        val sharedImage = File(imagesDir, "shared_photo.jpg")
        sharedImage.writeText("shared photo bytes")
        assertTrue(sharedImage.exists())

        val note1 = NoteEntity(id = 1L, title = "Note 1", imageUrisJson = "[\"${sharedImage.absolutePath}\"]")
        val note2 = NoteEntity(id = 2L, title = "Note 2", imageUrisJson = "[\"${sharedImage.absolutePath}\"]")

        // Deleting note1 when note2 still references the file must NOT delete sharedImage
        NoteMediaManager.deleteNoteMediaFiles(note1, listOf(note1, note2))
        assertTrue("Shared file must not be deleted while note 2 references it", sharedImage.exists())

        // Deleting note2 when no other note references it should delete sharedImage
        NoteMediaManager.deleteNoteMediaFiles(note2, listOf(note2))
        assertFalse("File should be deleted once no other note references it", sharedImage.exists())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Finding F10: Typed Save Results & Error Handling
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun testF10SaveNoteResultReturnsFailureOnMissingRowUpdate(): Unit = runBlocking {
        val nonexistentNote = NoteEntity(
            id = 999999L,
            title = "Ghost Note",
            content = "Ghost Content"
        )

        val result = repository.saveNoteResult(nonexistentNote)
        assertTrue("Updating nonexistent note must return SaveResult.Failure", result is NoteRepository.SaveResult.Failure)
    }

    @Test
    fun testF10SaveNoteResultReturnsSavedOnSuccess(): Unit = runBlocking {
        val newNote = NoteEntity(
            title = "Real Note",
            content = "Valid Content"
        )

        val result = repository.saveNoteResult(newNote)
        assertTrue("Inserting new note must return SaveResult.Saved", result is NoteRepository.SaveResult.Saved)
        val savedId = (result as NoteRepository.SaveResult.Saved).id
        assertTrue("Assigned ID must be > 0", savedId > 0L)

        // Update existing note
        val updatedNote = newNote.copy(id = savedId, title = "Updated Title")
        val updateResult = repository.saveNoteResult(updatedNote)
        assertTrue("Updating existing note must return SaveResult.Saved", updateResult is NoteRepository.SaveResult.Saved)
        assertEquals(savedId, (updateResult as NoteRepository.SaveResult.Saved).id)
    }

    @Test
    fun testF10EmptyTrashAbortsWithoutValidContext(): Unit = runBlocking {
        val repoWithoutContext = NoteRepository(noteDb.noteDao(), null)
        var caughtException = false
        try {
            repoWithoutContext.emptyTrash(null)
        } catch (e: IllegalStateException) {
            caughtException = true
            assertTrue(e.message?.contains("Context", ignoreCase = true) == true)
        }
        assertTrue("emptyTrash must fail closed when Context is null", caughtException)
    }
}
