package com.focusbyrj.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.*
import com.focusbyrj.app.data.note.*
import com.focusbyrj.app.util.backup.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RestoreRecoveryRepairTest {
    private val app: FocusApplication get() = ApplicationProvider.getApplicationContext<Context>() as FocusApplication
    private class SimulatedTermination : Error()

    @Test fun restartRecoveryRestoresEveryStoreAndIsIdempotent() = runBlocking {
        val notes = NoteDatabase.getInstance(app).noteDao()
        notes.deleteAllNotes()
        val original = NoteEntity(id = 1101, title = "Before", content = "Original Unicode Ω")
        notes.insertNote(original)
        val tasks = app.database.taskDao()
        tasks.deleteAllTasks()
        tasks.insertTask(Task(id = 1101, title = "Before task"))
        val preferences = app.getSharedPreferences("focus_prefs", 0)
        preferences.edit().putString("fixture", "before").commit()
        val assistant = File(app.filesDir, "ayva_conversation_state.enc")
        assistant.writeBytes(byteArrayOf(1, 3, 5, 7))
        RestoreJournal.checkpointHook = { if (it == "before_commit") throw SimulatedTermination() }
        try {
            RestoreJournal.mutate(app) {
                notes.deleteAllNotes()
                notes.insertNote(NoteEntity(id = 2202, title = "Partial restore"))
                tasks.deleteAllTasks()
                tasks.insertTask(Task(id = 2202, title = "Partial task"))
                preferences.edit().putString("fixture", "partial").commit()
                assistant.writeBytes(byteArrayOf(9))
                Result.success(Unit)
            }
            fail("Termination must interrupt the operation")
        } catch (_: SimulatedTermination) { }
        finally { RestoreJournal.checkpointHook = null }
        assertTrue(MaintenanceGate.recoveryRequired)
        RestoreJournal.recover(app)
        RestoreJournal.recover(app)
        assertEquals(listOf(original), notes.getAllNotesList())
        assertEquals("Before task", tasks.getAllTasksList().single().title)
        assertEquals("before", preferences.getString("fixture", null))
        assertArrayEquals(byteArrayOf(1,3,5,7), assistant.readBytes())
        assertFalse(MaintenanceGate.recoveryRequired)
        assertFalse(File(app.filesDir, "restore_before_state.enc").exists())
    }

    @Test fun terminationAfterCommitMarkerKeepsNewData() = runBlocking {
        val dao = NoteDatabase.getInstance(app).noteDao()
        dao.deleteAllNotes()
        dao.insertNote(NoteEntity(id = 1101, title = "Before"))
        RestoreJournal.checkpointHook = { if(it == "committed") throw SimulatedTermination() }
        try {
            RestoreJournal.mutate(app) {
                dao.deleteAllNotes(); dao.insertNote(NoteEntity(id = 2202, title = "Committed"))
                Result.success(Unit)
            }
            fail("Termination expected")
        } catch (_: SimulatedTermination) { }
        finally { RestoreJournal.checkpointHook = null }
        RestoreJournal.recover(app)
        assertEquals("Committed", dao.getAllNotesList().single().title)
    }

    @Test fun failedOperationRollsBackNewVocabularyRowsAndSettings() = runBlocking {
        val dao = app.vocabDatabase.vocabDao()
        val before = dao.findOws("audit-word").size
        val preferences = app.getSharedPreferences("focus_prefs", 0)
        preferences.edit().putInt("fixture", 7).commit()
        val result = RestoreJournal.mutate<Unit>(app) {
            dao.insertDictionaryOws(Ows(term = "audit-word", definition = "temporary", isBookmarked = 1))
            preferences.edit().putInt("fixture", 9).commit()
            Result.failure(IllegalStateException("Synthetic failure"))
        }
        assertTrue(result.isFailure)
        assertEquals(before, dao.findOws("audit-word").size)
        assertEquals(7, preferences.getInt("fixture", 0))
    }

    @Test fun staleEditorIsRejectedAfterWidgetMutationEvenWithinSameMillisecond() = runBlocking {
        val dao = NoteDatabase.getInstance(app).noteDao()
        val original = NoteEntity(id = 1101, title = "Original", updatedAt = 500,
            isChecklist = true, checklistJson = ChecklistItem.listToJson(listOf(ChecklistItem(id = "a", text = "Pack"))))
        dao.insertNote(original)
        val widget = dao.editChecklistItem(1101, "a", false)!!
        assertTrue(widget.getChecklistItems().single().isChecked)
        assertNull(dao.compareAndSave(original.copy(title = "Stale editor"), original.updatedAt))
        assertEquals(widget, dao.getNoteByIdSync(1101))
        val saved = dao.compareAndSave(widget.copy(title = "Fresh editor"), widget.updatedAt)!!
        assertTrue(saved.updatedAt > widget.updatedAt)
    }

    @Test fun ordinaryWriterCannotMutateDuringMaintenanceAndCanRetry() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val dao = NoteDatabase.getInstance(app).noteDao()
        val maintenance = async(Dispatchers.IO) {
            MaintenanceGate.exclusively {
                entered.countDown(); check(release.await(10, TimeUnit.SECONDS))
            }
        }
        assertTrue(withContext(Dispatchers.IO) { entered.await(10, TimeUnit.SECONDS) })
        try {
            try {
                dao.insertNote(NoteEntity(title = "Blocked writer"))
                fail("Write must be rejected during maintenance")
            } catch (_: android.database.sqlite.SQLiteException) { }
        } finally { release.countDown() }
        maintenance.await()
        assertTrue(dao.insertNote(NoteEntity(title = "Retried writer")) > 0)
    }

    @Test fun actualRestoreInterruptionsPreserveOriginalMediaAndAllRecords() = runBlocking {
        val notes = NoteDatabase.getInstance(app).noteDao()
        notes.deleteAllNotes()
        val media = File(app.filesDir, "keep_images/phase-fixture.bin")
        com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(media, "backup attachment".toByteArray())
        notes.insertNote(NoteEntity(id = 3303, title = "Incoming", imageUrisJson = JSONArray().put(media.path).toString()))
        val backup = File(app.cacheDir, "phases.fbck")
        val created = BackupRestoreManager.createEncryptedBackup(app, android.net.Uri.fromFile(backup), "phase-fixture")
        assertTrue(created.exceptionOrNull().toString(), created.isSuccess)
        notes.deleteAllNotes()
        val original = NoteEntity(id = 4404, title = "Live original", content = "Preserve full content")
        notes.insertNote(original)
        com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(media, "live attachment".toByteArray())
        for (phase in listOf("prepared", "notes", "focus", "preferences", "media", "before_commit")) {
            var reached = false
            RestoreJournal.checkpointHook = { current ->
                if (current == phase) {
                    reached = true
                    app.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("restore_media_stage_") }.forEach { stage ->
                        stage.walkTopDown().filter { it.isFile }.forEach { staged ->
                            assertTrue("Staging must be encrypted", com.focusbyrj.app.util.crypto.EncryptedMediaStorage.isEncrypted(staged))
                            assertFalse(staged.readBytes().decodeToString().contains("backup attachment"))
                        }
                    }
                    throw SimulatedTermination()
                }
            }
            try {
                BackupRestoreManager.restoreEncryptedBackup(app, android.net.Uri.fromFile(backup), "phase-fixture", cleanRestore = true)
                fail("Termination expected at $phase")
            } catch (_: SimulatedTermination) { }
            finally { RestoreJournal.checkpointHook = null }
            assertTrue("Phase $phase was reached", reached)
            RestoreJournal.recover(app)
            assertEquals(listOf(original), notes.getAllNotesList())
            assertEquals("live attachment", com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(media)!!.decodeToString())
        }
    }

    @Test fun failedAtomicInstallationIsReportedWithoutDestroyingExistingFiles() {
        val target = File(app.cacheDir, "blocked-snapshot").apply { mkdirs() }
        val retained = File(target, "retained").apply { writeText("keep") }
        try {
            EncryptedSnapshotFile.write(target, "{\"fixture\":true}")
            fail("Installing over a nonempty directory must report failure")
        } catch (_: Exception) { }
        assertEquals("keep", retained.readText())
        try {
            com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(target, "private".toByteArray())
            fail("Media installation must report failure")
        } catch (_: Exception) { }
        assertEquals("keep", retained.readText())
    }

    @Test fun vaultLockRetainsFailedDraftAndRecoveryDoesNotOverwriteNewerNote() = runBlocking {
        assertTrue(ArchiveVaultSecurity.setPasscode(app, "123456"))
        val dao = NoteDatabase.getInstance(app).noteDao()
        val original = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(
            NoteEntity(id = 7777, title = "Original", content = "Original body", isArchived = true, updatedAt = 100), context = app)
        dao.insertNote(original)
        val vm = com.focusbyrj.app.ui.screens.notes.NotesViewModel(app)
        vm.openExistingNote(original)
        vm.updateEditorTitle("Retained draft")
        val newer = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(
            NoteEntity(id = 7777, title = "Newer elsewhere", content = "Original body", isArchived = true, updatedAt = 200), context = app)
        dao.updateNote(newer)
        vm.pendingDraftWriteHook = { throw java.io.IOException("Synthetic storage failure") }
        vm.lockVault()
        assertNull(vm.editingState.value)
        assertNull(ArchiveVaultSecurity.getActiveVaultSubKey())
        val field = vm.javaClass.getDeclaredField("pendingPlainVaultDraft").apply { isAccessible = true }
        assertNotNull(field.get(vm))
        vm.pendingDraftWriteHook = null
        assertTrue(vm.verifyVaultPasscode("123456") is ArchiveVaultSecurity.VerifyResult.Success)
        withTimeout(10_000) {
            while (dao.getAllNotesList().map { com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.decryptNotePayload(it) }.none { it.title == "Retained draft" }) delay(20)
        }
        assertEquals("Newer elsewhere", com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.decryptNotePayload(dao.getNoteByIdSync(7777)!!).title)
        ArchiveVaultSecurity.lockVault()
    }

    @Test fun vocabularyRecoveryUsesIdentityAndPreservesUnlearnedBookmarks() = runBlocking {
        val database = app.vocabDatabase
        val dao = database.vocabDao()
        val existingId = dao.insertDictionaryOws(Ows(term = "Local unrelated", definition = "Retain me")).toInt()
        val foreign = JSONObject().put("version", 1).put("idioms", JSONArray())
            .put("ows", JSONArray().put(JSONObject().put("id", existingId).put("term", "Imported meaning")
                .put("definition", "Correct definition").put("part_of_speech", "noun").put("is_bookmarked", 1)
                .put("is_mastered", 0).put("learned_at", JSONObject.NULL)))
            .put("links", JSONArray().put(JSONObject().put("senseId", "stable-sense").put("wordKey", "imported meaning").put("owsId", existingId)))
        VocabularyArchive.restore(database, foreign, clean = false)
        VocabularyArchive.restore(database, foreign, clean = false)
        assertEquals("Local unrelated", dao.getOwsById(existingId)!!.term)
        val link = database.dictionaryBookmarkDao().get("stable-sense")!!
        assertNotEquals(existingId, link.owsId)
        val imported = dao.getOwsById(link.owsId)!!
        assertEquals("Correct definition", imported.definition)
        assertEquals(1, imported.isBookmarked)
        assertNull(imported.learnedAt)
        assertEquals(1, dao.findOws("Imported meaning").size)
        val exported = VocabularyArchive.export(database).getJSONArray("ows")
        assertTrue((0 until exported.length()).any { exported.getJSONObject(it).getString("term") == "Imported meaning" })
    }
}
