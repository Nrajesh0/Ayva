package com.focusbyrj.app

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.VocabDatabase
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.data.note.NoteMediaManager
import com.focusbyrj.app.util.backup.BackupRestoreManager
import com.focusbyrj.app.util.backup.CryptoBackupEngine
import com.focusbyrj.app.util.backup.DataSafetyManager
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SecurityRemediationRegressionTest {
    private lateinit var context: Context
    private lateinit var noteDb: NoteDatabase
    private var previousNoteDb: Any? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        noteDb = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val field = NoteDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        previousNoteDb = field.get(null)
        field.set(null, noteDb)
        context.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        ArchiveVaultSecurity.lockVault()
    }

    @After
    fun tearDown() {
        ArchiveVaultSecurity.lockVault()
        context.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val field = NoteDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        field.set(null, previousNoteDb)
        noteDb.close()
        File(context.filesDir, "auto_backups").deleteRecursively()
    }

    @Test
    fun historicalPlaintextSnapshotCannotDowngradeLockedVaultNote() = runBlocking {
        assertTrue(ArchiveVaultSecurity.setPasscode(context, "123456"))
        val key = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        val original = NoteEntity(id = 101, title = "Historical", content = "private now")
        noteDb.noteDao().insertNote(original)
        val snapshot = DataSafetyManager.writePreOpSnapshot(context, noteDb.noteDao(), "before_archive")!!
        val protected = VaultPayloadEncryptor.encryptNotePayload(original.copy(isArchived = true), key)
        noteDb.noteDao().updateNote(protected)
        ArchiveVaultSecurity.lockVault()

        val result = DataSafetyManager.restoreSnapshot(context, snapshot, noteDb.noteDao())

        assertTrue(result.isFailure)
        val preserved = noteDb.noteDao().getNoteByIdSync(101)!!
        assertTrue(preserved.isArchived)
        assertTrue(VaultPayloadEncryptor.tryDecryptNotePayload(preserved, key) is VaultPayloadEncryptor.DecryptionResult.Success)
    }

    @Test
    fun encryptedRemainingReferencePreventsSharedMediaDeletion() {
        assertTrue(ArchiveVaultSecurity.setPasscode(context, "123456"))
        val key = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        val file = File(context.filesDir, "keep_images/shared-remediation.bin").apply {
            parentFile!!.mkdirs()
            writeText("shared")
        }
        val paths = JSONArray().put(file.absolutePath).toString()
        val deleted = NoteEntity(id = 201, imageUrisJson = paths)
        val remaining = VaultPayloadEncryptor.encryptNotePayload(
            NoteEntity(id = 202, isArchived = true, imageUrisJson = paths), key
        )

        assertTrue(NoteMediaManager.deleteNoteMediaFiles(deleted, listOf(remaining)))
        assertTrue("Encrypted note still references this media", file.exists())
        file.delete()
    }

    @Test
    fun malformedManifestIsRejectedBeforeCleanRestoreMutatesNotes() = runBlocking {
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply {
            isAccessible = true
            invoke(app, context)
        }
        val focusDb = Room.inMemoryDatabaseBuilder(context, FocusDatabase::class.java)
            .allowMainThreadQueries().build()
        val vocabDb = Room.inMemoryDatabaseBuilder(context, VocabDatabase::class.java)
            .allowMainThreadQueries().build()
        fun setLazy(name: String, value: Any) {
            app.javaClass.getDeclaredField("$name\$delegate").apply {
                isAccessible = true
                set(app, lazyOf(value))
            }
        }
        setLazy("database", focusDb)
        setLazy("vocabDatabase", vocabDb)
        val wrapper = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = app
        }
        try {
            noteDb.noteDao().insertNote(NoteEntity(id = 301, title = "Original", content = "keep"))
            val manifest = JSONObject()
                .put("notes", JSONArray().put(JSONObject().put("id", 301).put("title", "Replacement")))
                .put("tasks", JSONArray().put(JSONObject()))
            val backup = File(context.cacheDir, "malformed-remediation.focusbackup")
            backup.outputStream().use { raw ->
                CryptoBackupEngine.openEncryptingStream(raw, "password".toCharArray()).use { encrypted ->
                    ZipOutputStream(encrypted).use { zip ->
                        zip.putNextEntry(ZipEntry("data.json"))
                        zip.write(manifest.toString().toByteArray())
                        zip.closeEntry()
                    }
                }
            }

            val result = BackupRestoreManager.restoreEncryptedBackup(
                wrapper, Uri.fromFile(backup), "password", cleanRestore = true
            )

            assertTrue(result.isFailure)
            assertEquals("Original", noteDb.noteDao().getNoteByIdSync(301)!!.title)
        } finally {
            focusDb.close()
            vocabDb.close()
        }
    }
}
