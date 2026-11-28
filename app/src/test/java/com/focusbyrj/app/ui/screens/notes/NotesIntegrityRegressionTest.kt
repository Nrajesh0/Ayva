package com.focusbyrj.app.ui.screens.notes

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.note.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FocusApplication::class)
class NotesIntegrityRegressionTest {
    val app get() = ApplicationProvider.getApplicationContext<FocusApplication>()
    val dao get() = NoteDatabase.getInstance(app).noteDao()
    lateinit var vm: NotesViewModel
    lateinit var store: androidx.lifecycle.ViewModelStore
    @Before fun init() {
        runBlocking { dao.deleteAllNotes() }
        NotesViewModel.latestNotesCache.clear()
        vm = NotesViewModel(app)
        store = androidx.lifecycle.ViewModelStore().also { it.put("audit", vm) }
    }
    @After fun finish() { store.clear() }
    fun waitUntil(check: () -> Boolean) {
        val end = System.nanoTime() + 12_000_000_000
        while (!check() && System.nanoTime() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue(check())
    }
    fun open(): NoteEntity {
        val n = NoteEntity(id=501, title="Original", content="First", createdAt=100, updatedAt=101)
        runBlocking { dao.insertNote(n) }
        vm.openExistingNote(n)
        return n
    }
    @Test fun revertingPersistsOriginalText() {
        val n = open()
        vm.updateEditorContent("Intermediate")
        waitUntil { vm.saveStatus.value is NotesViewModel.NoteSaveStatus.Saved }
        vm.updateEditorContent("First")
        vm.closeEditor()
        waitUntil { vm.editingState.value == null }
        assertEquals("First", runBlocking { dao.getNoteByIdSync(n.id)!!.content })
    }
    @Test fun trashIncludesLatestText() {
        val n = open()
        vm.updateEditorContent("Last edit")
        vm.deleteCurrentNote()
        waitUntil { runBlocking { dao.getNoteByIdSync(n.id)!!.isTrashed } }
        assertEquals("Last edit", runBlocking { dao.getNoteByIdSync(n.id)!!.content })
    }
    @Test fun duplicateHasIndependentRevision() {
        val n = open()
        vm.duplicateCurrentNote()
        waitUntil { vm.editingState.value?.originalId != n.id }
        val id = vm.editingState.value!!.originalId
        assertEquals(runBlocking { dao.getNoteByIdSync(id)!!.updatedAt },vm.editingState.value!!.storageRevision)
        vm.updateEditorContent("Edited duplicate")
        waitUntil { runBlocking { dao.getNoteByIdSync(id)!!.content == "Edited duplicate" } }
        assertEquals("Edited duplicate",runBlocking { dao.getNoteByIdSync(id)!!.content })
    }
    @Test fun staleDeletePreservesRestoredNote() {
        val n = open()
        runBlocking {
            dao.updateTrashStatus(n.id,true)
            val trash = dao.getNoteByIdSync(n.id)!!
            dao.updateTrashStatus(n.id,false)
            assertTrue(runCatching { NoteRepository(dao,app).deletePermanently(trash) }.isFailure)
            assertFalse(dao.getNoteByIdSync(n.id)!!.isTrashed)
        }
    }
    @Test fun articleCopyRetainsMetadata() {
        val content = NotesnookBlockManager.serialize(listOf(NotesnookBlock.Text(text="Paragraph")), isArticle=true,subtitle="Original subtitle")
        val n = NoteEntity(id=501, title="Original", content=content)
        runBlocking { dao.insertNote(n) }
        vm.openExistingNote(n)
        vm.duplicateCurrentNote()
        waitUntil { vm.editingState.value?.originalId != n.id }
        assertEquals("Original subtitle", NotesnookBlockManager.getSubtitle(vm.editingState.value!!.content))
    }
    @Test fun legacyBlockCanBeInventoriedForBackup() {
        val raw = """<!--NOTESNOOK_BLOCKS:{"blocks":[{"id":"x","type":"text","text":"Paragraph"}]}-->"""
        assertEquals("Paragraph", NotesnookBlockManager.toPlainText(raw))
        assertTrue(runCatching { NoteMediaInventory.paths(NoteEntity(content=raw)) }.isSuccess)
    }
    @Test fun quotedDelimitersSurviveLegacyBackupRemapping() {
        val text = """<!--NOTESNOOK_BLOCKS:{"blocks":[{"type":"text","text":"keep --> text"}]}-->"""
        val image = """<!--NOTESNOOK_BLOCKS:{"blocks":[{"type":"image","uri":"old"}]}:BLOCKS_END-->"""
        val content = text + image
        val remapped = NoteMediaInventory.remapContent(content) { "new" }
        assertEquals(text + image.replace("old", "new"), remapped)
        com.focusbyrj.app.util.backup.BackupArchiveLimits.checkNoteContent(remapped)
    }

    @Test fun fullCopyIncludesEntireCodeBlock() {
        val code = "line of text ".repeat(16) + "final line"
        val raw = NotesnookBlockManager.serialize(listOf(NotesnookBlock.Code(language="text",code=code)))
        vm.openExistingNote(NoteEntity(title="Source",content=raw))
        vm.copyCurrentNoteToClipboard(app)
        val clipboard = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val text = clipboard.primaryClip!!.getItemAt(0).text.toString()
        assertTrue(text.contains(code.take(120)))
        assertTrue(text.contains("final line"))
    }
    @Test fun deleteCopyPreservesSource() {
        val n=open()
        vm.updateEditorContent("Recent edit")
        waitUntil { vm.saveStatus.value is NotesViewModel.NoteSaveStatus.Saved }
        vm.duplicateCurrentNote()
        waitUntil { vm.editingState.value?.originalId != n.id }
        val copyId=vm.editingState.value!!.originalId
        vm.deleteCurrentNote()
        waitUntil {runBlocking { dao.getNoteByIdSync(copyId)!!.isTrashed } }
        assertFalse(runBlocking { dao.getNoteByIdSync(n.id)!!.isTrashed })
    }
    @Test fun revertingPinAfterAutosaveClearsPin() {
        val n = open()
        vm.toggleEditorPin()
        waitUntil { vm.saveStatus.value is NotesViewModel.NoteSaveStatus.Saved }
        vm.toggleEditorPin()
        vm.closeEditor()
        waitUntil { vm.editingState.value == null }
        assertFalse(runBlocking { dao.getNoteByIdSync(n.id)!!.isPinned })
    }
    @Test fun subtitleOnlyArticleIsRetained() {
        val raw = NotesnookBlockManager.serialize(listOf(NotesnookBlock.Text()),isArticle=true,subtitle="Only subtitle")
        assertFalse(NoteEntity(content=raw).isEmptyNote())
    }
    @Test fun snapshotPreservesUnrelatedRowSharingId() = runBlocking {
        val old = NoteEntity(id=501,title="Earlier lineage",createdAt=50)
        dao.insertNote(old)
        val snap = requireNotNull(com.focusbyrj.app.util.backup.DataSafetyManager.writePreOpSnapshot(app,dao,"audit"))
        val other = NoteEntity(id=501,title="Different note",createdAt=90)
        dao.replaceAllNotes(listOf(other))
        assertEquals(1, com.focusbyrj.app.util.backup.DataSafetyManager.restoreSnapshot(app,snap,dao,null).getOrThrow())
        assertEquals(other,dao.getNoteByIdSync(old.id))
        assertTrue(dao.getAllNotesList().any { it.id != other.id && it.title == old.title })
    }
    @Test fun manualOrderDoesNotInvalidateEditorRevision() = runBlocking {
        val original = open()
        dao.updateNoteOrder(original.id, 300)
        assertEquals(original.updatedAt, dao.getNoteByIdSync(original.id)!!.updatedAt)
        val saved = dao.compareAndSave(original.copy(title = "Edited"), original.updatedAt)
        assertNotNull(saved)
        assertEquals(300L, saved!!.sortPosition)
    }

    @Test fun historicalArchiveSurvivesPinRemoval() = runBlocking {
        assertTrue(ArchiveVaultSecurity.setPasscode(app, "123456"))
        val safety = com.focusbyrj.app.util.backup.DataSafetyManager
        val obsoleteKey = ByteArray(32).also(java.security.SecureRandom()::nextBytes)
        val obsolete = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(
            NoteEntity(title = "Earlier vault", isArchived = true), obsoleteKey
        )
        obsoleteKey.fill(0)
        dao.insertNote(obsolete)
        val earlier = requireNotNull(safety.writePreOpSnapshot(app, dao, "earlier_pin_history"))
        val earlierBytes = java.io.File(earlier).readBytes()
        dao.deleteAllNotes()

        val note = NoteEntity(title = "Historical", isArchived = true)
        val id = NoteRepository(dao, app).saveNote(note)
        val snapshot = requireNotNull(safety.writePreOpSnapshot(app, dao, "pin_history"))
        assertTrue(ArchiveVaultSecurity.disablePasscode(app))
        assertArrayEquals(earlierBytes, java.io.File(earlier).readBytes())
        dao.deleteAllNotes()
        assertFalse(safety.snapshotNeedsVault(app, snapshot, dao))
        safety.restoreSnapshot(app, snapshot, dao, null).getOrThrow()
        assertEquals("Historical", dao.getNoteByIdSync(id)!!.title)
        assertTrue(dao.getNoteByIdSync(id)!!.isArchived)
    }

    @Test fun versionSixArticleSurvivesRoomUpgrade() {
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(app)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("""CREATE TABLE keep_notes (id INTEGER PRIMARY KEY, title TEXT, content TEXT,
                        isChecklist INTEGER, checklistJson TEXT, colorKey TEXT, fontKey TEXT, isPinned INTEGER,
                        isArchived INTEGER, isTrashed INTEGER, labelsJson TEXT, imageUrisJson TEXT,
                        audioUrisJson TEXT, createdAt INTEGER, updatedAt INTEGER, trashedAt INTEGER, deletedAt INTEGER)""")
                    NoteDatabase.MIGRATION_5_6.migrate(db)
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, old: Int, new: Int) = Unit
            }).build()
        androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("""INSERT INTO keep_notes VALUES (1, 'Essay', 'Body', 0, '[]', 'default', 'default',
                0, 0, 0, '[]', '[]', '[]', 10, 11, NULL, NULL, 1, 'Subtitle', '/cover.jpg', 1, 12, 3)""")
            val key = ByteArray(32).also(java.security.SecureRandom()::nextBytes)
            val crypto = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
            val encrypted = crypto.encryptNotePayload(NoteEntity(content = "Private body", isArchived = true), key)
            db.execSQL("""INSERT INTO keep_notes VALUES (2, 'Private', ?, 0, '[]', 'default', 'default',
                0, 1, 0, '[]', '[]', '[]', 10, 11, NULL, NULL, 1, 'Private subtitle', '/private.jpg', 0, NULL, 3)""",
                arrayOf(encrypted.content))
            NoteDatabase.MIGRATION_6_7.migrate(db)
            NoteDatabase.MIGRATION_7_8.migrate(db)
            db.query("SELECT content, legacyArticleMetadata FROM keep_notes WHERE id = 2").use {
                assertTrue(it.moveToFirst())
                assertEquals(encrypted.content, it.getString(0))
                val stored = encrypted.copy(legacyArticleMetadata = it.getString(1))
                val recovered = crypto.decryptNotePayload(stored, key)
                assertEquals("Private subtitle", NotesnookBlockManager.getSubtitle(recovered.content))
                assertEquals(listOf("/private.jpg"), recovered.getImageUris())
                assertNull(recovered.legacyArticleMetadata)
            }
            key.fill(0)
            db.query("SELECT content, imageUrisJson, updatedAt, sortPosition FROM keep_notes WHERE id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals("Subtitle", NotesnookBlockManager.getSubtitle(it.getString(0)))
                assertTrue(NotesnookBlockManager.isArticleMode(it.getString(0)))
                assertEquals("Body", NotesnookBlockManager.toPlainText(it.getString(0)))
                assertEquals("/cover.jpg", org.json.JSONArray(it.getString(1)).getString(0))
                assertEquals(11L, it.getLong(2))
                assertTrue(it.isNull(3))
            }
        }
    }

}
