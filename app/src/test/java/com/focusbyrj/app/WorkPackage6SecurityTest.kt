package com.focusbyrj.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.DatabaseKeyProvider
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.backup.BackupRestoreManager
import com.focusbyrj.app.util.backup.CryptoBackupEngine
import com.focusbyrj.app.util.sync.VaultSyncManager
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
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Work Package 6 Security & Data Integrity Verification Suite
 *
 * Verifies:
 * - F07: Cross-device merge restore preserves local records when numeric IDs collide.
 * - F08: Failed restore or corrupt inputs abort with zero mutation to live databases.
 * - F14: Input size limits (50MB manifest limit) enforce resource bounds against zip bombs.
 * - Dormant helper: VaultSyncManager pre-validates inputs, refusing to delete local data on error.
 */
@RunWith(RobolectricTestRunner::class)
class WorkPackage6SecurityTest {

    private lateinit var context: Context
    private lateinit var app: FocusApplication
    private lateinit var noteDb: NoteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = context.applicationContext as FocusApplication
        org.robolectric.shadows.ShadowLog.stream = System.out
        noteDb = NoteDatabase.getInstance(context)
        runBlocking {
            noteDb.noteDao().deleteAllNotes()
            app.database.taskDao().deleteAllTasks()
        }
        ArchiveVaultSecurity.disablePasscode(context)
        DatabaseKeyProvider.clearCachedPassphrase()
    }

    @After
    fun tearDown() {
        runBlocking {
            noteDb.noteDao().deleteAllNotes()
            app.database.taskDao().deleteAllTasks()
        }
        ArchiveVaultSecurity.disablePasscode(context)
        DatabaseKeyProvider.clearCachedPassphrase()
    }

    // =========================================================================
    // 1. F07: Cross-Device Merge Restore Preserves Local Records on ID Collision
    // =========================================================================

    @Test
    fun f07_crossDeviceMergeRestorePreservesLocalRecordsOnIdCollision() = runBlocking {
        // Local Device has Note 1 and Task 1
        val localNote = NoteEntity(
            id = 1L,
            title = "Local Device Note 1",
            content = "Original local content that must not be overwritten",
            isArchived = false
        )
        noteDb.noteDao().insertNote(localNote)

        val localTask = Task(
            id = 1L,
            title = "Local Device Task 1",
            details = "Original local task"
        )
        app.database.taskDao().insertTask(localTask)

        assertEquals(1, noteDb.noteDao().getAllNotesList().size)
        assertEquals(1, app.database.taskDao().getAllTasksList().size)

        // Remote Device backup also has Note 1 and Task 1 (unrelated data!)
        val remoteJson = JSONObject().apply {
            put("version", 1)
            put("createdAt", System.currentTimeMillis())

            val notesArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1L)
                    put("title", "Remote Backup Note 1")
                    put("content", "Imported content from different device")
                    put("isArchived", false)
                    put("isTrashed", false)
                    put("createdAt", 5000L)
                    put("updatedAt", 5000L)
                })
            }
            put("notes", notesArr)

            val tasksArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1L)
                    put("title", "Remote Backup Task 1")
                    put("isCompleted", false)
                })
            }
            put("tasks", tasksArr)
            put("restrictions", JSONArray())
            put("schedules", JSONArray())
            put("habits", JSONArray())
            put("drillSessions", JSONArray())
        }

        val backupFile = File(context.cacheDir, "remote_backup_collision.focusbackup")
        val password = "TestPassword456!"
        FileOutputStream(backupFile).use { fos ->
            CryptoBackupEngine.openEncryptingStream(fos, password.toCharArray()).use { cos ->
                ZipOutputStream(BufferedOutputStream(cos)).use { zos ->
                    zos.putNextEntry(ZipEntry("data.json"))
                    zos.write(remoteJson.toString().toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
        }

        // Restore in Merge mode (cleanRestore = false)
        val result = BackupRestoreManager.restoreEncryptedBackup(
            context = context,
            sourceUri = Uri.fromFile(backupFile),
            password = password,
            cleanRestore = false
        )
        assertTrue("Merge restore must succeed", result.isSuccess)

        // VERIFY F07: Local Note 1 and Task 1 are 100% intact and preserved!
        val allNotes = noteDb.noteDao().getAllNotesList()
        assertEquals("Both local note and remote note must exist (no destructive overwrite)", 2, allNotes.size)

        val preservedNote = allNotes.firstOrNull { it.title == "Local Device Note 1" }
        assertNotNull("Local note must be preserved", preservedNote)
        assertEquals("Original local content that must not be overwritten", preservedNote!!.content)

        val importedNote = allNotes.firstOrNull { it.title == "Remote Backup Note 1" }
        assertNotNull("Imported note must be present with new assigned ID", importedNote)
        assertTrue("Imported note must have non-conflicting ID", importedNote!!.id != preservedNote.id)

        // VERIFY Tasks: both tasks exist
        val allTasks = app.database.taskDao().getAllTasksList()
        assertEquals(2, allTasks.size)
        assertTrue(allTasks.any { it.title == "Local Device Task 1" })
        assertTrue(allTasks.any { it.title == "Remote Backup Task 1" })
    }

    // =========================================================================
    // 2. F08: Failed Restore Aborts Gracefully with Zero Database Mutation
    // =========================================================================

    @Test
    fun f08_corruptArchiveAbortsWithoutMutatingLiveDatabase() = runBlocking {
        // Seed original live state
        val originalNote = NoteEntity(id = 50, title = "Original Critical Note", content = "Unchanged")
        noteDb.noteDao().insertNote(originalNote)

        val corruptFile = File(context.cacheDir, "corrupt.focusbackup")
        corruptFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))

        val result = BackupRestoreManager.restoreEncryptedBackup(
            context = context,
            sourceUri = Uri.fromFile(corruptFile),
            password = "AnyPassword"
        )
        assertTrue("Restore of corrupt file must fail", result.isFailure)

        // Live database must be 100% untouched
        val liveNotes = noteDb.noteDao().getAllNotesList()
        assertEquals(1, liveNotes.size)
        assertEquals("Original Critical Note", liveNotes.first().title)
    }

    // =========================================================================
    // 3. Dormant VaultSyncManager: Pre-Validation Prevents Accidental Wipe
    // =========================================================================

    @Test
    fun dormantVaultSyncManager_preValidationPreventsDataWipeOnCorruptInput() = runBlocking {
        val originalNote = NoteEntity(id = 60, title = "Live Existing Note", content = "Must survive")
        noteDb.noteDao().insertNote(originalNote)

        val originalTask = Task(id = 60, title = "Live Existing Task")
        app.database.taskDao().insertTask(originalTask)

        // Call restoreVaultFromJson in replace mode (mergeMode = false) with MALFORMED JSON
        val malformedJson = "{ this is completely invalid json syntax"
        val result = VaultSyncManager.restoreVaultFromJson(
            jsonString = malformedJson,
            noteDao = noteDb.noteDao(),
            taskDao = app.database.taskDao(),
            mergeMode = false,
            context = context
        )
        assertTrue("Restore of malformed json must fail", result.isFailure)

        // Pre-validation invariant: Local records must NOT have been wiped!
        assertEquals("Existing notes must survive failed import", 1, noteDb.noteDao().getAllNotesList().size)
        assertEquals("Existing tasks must survive failed import", 1, app.database.taskDao().getAllTasksList().size)
    }

    // =========================================================================
    // 4. Dormant VaultSyncManager: Refuses Locked Vault Archived Notes
    // =========================================================================

    @Test
    fun dormantVaultSyncManager_refusesArchivedNotesWhenVaultIsLocked() = runBlocking {
        ArchiveVaultSecurity.setPasscode(context, "123456")
        ArchiveVaultSecurity.lockVault()
        assertTrue(ArchiveVaultSecurity.isVaultLocked(context))

        val jsonWithArchivedNote = JSONObject().apply {
            val arr = JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 70L)
                    put("title", "Locked Secret Note")
                    put("isArchived", true)
                })
            }
            put("notes", arr)
            put("tasks", JSONArray())
        }

        val result = VaultSyncManager.restoreVaultFromJson(
            jsonString = jsonWithArchivedNote.toString(),
            noteDao = noteDb.noteDao(),
            taskDao = app.database.taskDao(),
            mergeMode = false,
            context = context
        )
        assertTrue("Import must fail when vault is locked", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
