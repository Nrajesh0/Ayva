package com.focusbyrj.app.data.note

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.ui.screens.notes.NotesViewModel
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34], application = FocusApplication::class)
class PrivateMediaContinuationTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusApplication>()
    private val dao get() = NoteDatabase.getInstance(app).noteDao()
    private val pin = "729461"
    private lateinit var vm: NotesViewModel
    private val viewModels = androidx.lifecycle.ViewModelStore()
    private var id = 0L
    @Before fun setup() = runBlocking {
        dao.deleteAllNotes()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        ArchiveVaultSecurity.lockVault()
        assertTrue(ArchiveVaultSecurity.setPasscode(app, pin))
        val key = requireNotNull(ArchiveVaultSecurity.getActiveVaultSubKey())
        id = try { dao.insertNote(VaultPayloadEncryptor.encryptNotePayload(NoteEntity(title = "Private title", content = "Preserve this body exactly", isArchived = true), key)) }
            finally { key.fill(0) }
        vm = androidx.lifecycle.ViewModelProvider(viewModels, androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app))[NotesViewModel::class.java]
        vm.openExistingNote(requireNotNull(dao.getNoteByIdSync(id)))
    }
    @After fun cleanup() { viewModels.clear() }
    private fun image(): String = File(app.filesDir, "keep_images/${java.util.UUID.randomUUID()}.jpg").also {
        EncryptedMediaStorage.writeEncryptedBytes(it, "Synthetic new image bytes".toByteArray())
    }.path
    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + 30_000_000_000L
        while (!condition() && System.nanoTime() < until) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        assertTrue(condition())
    }
    @Test fun externalInputKeepsVaultLockedAndRequiresNewPinThenPreservesBodyAndRejectsReplay() = runBlocking {
        vm.updateEditorTitle("Latest private draft")
        val target = vm.preparePrivateMediaInput(vm.editingState.value!!.sessionId)
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app)); assertNull(vm.editingState.value)
        assertFalse(target.contains("Latest private draft")); assertFalse(org.json.JSONObject(target).has("pin"))
        val path = image()
        assertTrue(runCatching { vm.resumePrivateMediaInput(target, listOf(path)) }.isFailure)
        assertTrue(vm.verifyVaultPasscode("111111") is ArchiveVaultSecurity.VerifyResult.Incorrect)
        assertNull(vm.editingState.value)
        assertTrue(vm.verifyVaultPasscode(pin) is ArchiveVaultSecurity.VerifyResult.Success)
        assertTrue(vm.resumePrivateMediaInput(target, listOf(path)))
        assertEquals("Latest private draft", vm.editingState.value!!.title)
        assertEquals("Preserve this body exactly", vm.editingState.value!!.content)
        assertTrue(vm.editingState.value!!.isArchived)
        assertEquals(listOf(path), vm.editingState.value!!.imageUris)
        await { vm.saveStatus.value is NotesViewModel.NoteSaveStatus.Saved }
        vm.lockVault()
        assertTrue(vm.verifyVaultPasscode(pin) is ArchiveVaultSecurity.VerifyResult.Success)
        assertFalse("Saved state replay cannot attach the same file twice", vm.resumePrivateMediaInput(target, listOf(path)))
        val key = requireNotNull(ArchiveVaultSecurity.getActiveVaultSubKey())
        try { assertEquals(listOf(path), VaultPayloadEncryptor.decryptNotePayload(requireNotNull(dao.getNoteByIdSync(id)), key).getImageUris()) }
        finally { key.fill(0) }
    }
    @Test fun anOldEditorCallbackCannotStartAnInputForAnotherPrivateNote() = runBlocking {
        val oldSession = vm.editingState.value!!.sessionId
        val key = requireNotNull(ArchiveVaultSecurity.getActiveVaultSubKey())
        val other = try { dao.insertNote(VaultPayloadEncryptor.encryptNotePayload(NoteEntity(title = "Other private note", isArchived = true), key)) }
            finally { key.fill(0) }
        vm.openExistingNote(requireNotNull(dao.getNoteByIdSync(other)))
        val session = vm.editingState.value!!.sessionId
        assertTrue(runCatching { vm.preparePrivateMediaInput(oldSession) }.isFailure)
        assertEquals(session, vm.editingState.value!!.sessionId)
        assertEquals(other, vm.editingState.value!!.originalId)
    }

    @Test fun changedNoteAndReplacedStorageCannotReceiveAnOlderSelection() = runBlocking {
        val target = vm.preparePrivateMediaInput(vm.editingState.value!!.sessionId)
        val path = image()
        assertTrue(vm.verifyVaultPasscode(pin) is ArchiveVaultSecurity.VerifyResult.Success)
        val row = requireNotNull(dao.getNoteByIdSync(id))
        dao.insertNote(row.copy(updatedAt = row.updatedAt + 50))
        assertTrue(runCatching { vm.resumePrivateMediaInput(target, listOf(path)) }.isFailure)
        assertNull(vm.editingState.value)
        NotesStorageVersion.replaced()
        assertTrue(runCatching { vm.resumePrivateMediaInput(target, listOf(path)) }.isFailure)
        assertNull(vm.editingState.value)
        assertTrue(File(path).exists())
    }

    @Test fun aQuickScopedReturnUnlocksOnlyOnceAndPreservesThePrivateNote() = runBlocking {
        val target=vm.preparePrivateMediaInput(vm.editingState.value!!.sessionId)
        vm.lockVaultForBackground()
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        assertTrue(vm.unlockPrivateMediaInputIfRecent(target))
        val path=image()
        assertTrue(vm.resumePrivateMediaInput(target,listOf(path)))
        assertEquals(id,vm.editingState.value!!.originalId)
        assertEquals("Preserve this body exactly",vm.editingState.value!!.content)
        vm.lockVault()
        assertFalse(vm.unlockPrivateMediaInputIfRecent(target))
    }

    @Test fun timeoutWipesThePendingKeyAndRequiresThePin() = runBlocking {
        val target=vm.preparePrivateMediaInput(vm.editingState.value!!.sessionId)
        val field=ArchiveVaultSecurity::class.java.getDeclaredField("mediaReturn").apply { isAccessible=true }
        val lease=checkNotNull(field.get(null))
        val bytes=lease.javaClass.getDeclaredField("key").apply { isAccessible=true }.get(lease) as ByteArray
        assertTrue(bytes.any { it != 0.toByte() })
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(ArchiveVaultSecurity.MEDIA_RETURN_TIMEOUT_MS))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(bytes.all { it == 0.toByte() })
        assertFalse(vm.unlockPrivateMediaInputIfRecent(target))
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        assertTrue(vm.verifyVaultPasscode(pin) is ArchiveVaultSecurity.VerifyResult.Success)
        assertTrue(vm.resumePrivateMediaInput(target,emptyList()))
    }

    @Test fun screenOffRevokesTheReturnGrant() = runBlocking {
        val target=vm.preparePrivateMediaInput(vm.editingState.value!!.sessionId)
        app.sendBroadcast(android.content.Intent(android.content.Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(vm.unlockPrivateMediaInputIfRecent(target))
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
    }

    @Test fun changedIdentityAndRestoredStorageCannotUseTheReturnGrant() = runBlocking {
        val target=vm.preparePrivateMediaInput(vm.editingState.value!!.sessionId)
        val altered=org.json.JSONObject(target).put("id",id+1).toString()
        assertFalse(vm.unlockPrivateMediaInputIfRecent(altered))
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        assertTrue(vm.verifyVaultPasscode(pin) is ArchiveVaultSecurity.VerifyResult.Success)
        assertTrue(vm.resumePrivateMediaInput(target,emptyList()))
        val next=vm.preparePrivateMediaInput(vm.editingState.value!!.sessionId)
        NotesStorageVersion.replaced()
        assertFalse(vm.unlockPrivateMediaInputIfRecent(next))
    }
}
