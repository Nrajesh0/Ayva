package com.focusbyrj.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.note.DatabaseKeyProvider
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.backup.BackupRestoreManager
import com.focusbyrj.app.util.backup.CryptoBackupEngine
import com.focusbyrj.app.util.backup.DataSafetyManager
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class DataPreservationAuditTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun truncatedEncryptedMediaIsNeverExportedAsPlaintext() {
        val file = File(context.cacheDir, "truncated-media")
        file.writeBytes("FOC_ENC_V1:".toByteArray() + ByteArray(12))
        assertTrue(EncryptedMediaStorage.isEncrypted(file))
        assertNull(EncryptedMediaStorage.readDecryptedBytes(file))
        assertNull(EncryptedMediaStorage.openDecryptedInputStream(file))
    }

    @Test fun atomicMediaOverwriteRoundTripsAndKeepsLegacyPlaintextReadable() {
        val file = File(context.cacheDir, "atomic-media")
        file.writeText("legacy")
        assertEquals("legacy", EncryptedMediaStorage.openDecryptedInputStream(file)!!.use { it.readBytes().decodeToString() })
        EncryptedMediaStorage.writeEncryptedBytes(file, "first".toByteArray())
        EncryptedMediaStorage.writeEncryptedBytes(file, "replacement".toByteArray())
        assertEquals("replacement", EncryptedMediaStorage.readDecryptedBytes(file)!!.decodeToString())
        assertFalse(File(file.path + ".new").exists())
    }

    @Test fun missingCredentialsNeverCreateReplacementForExistingDatabase() {
        DatabaseKeyProvider.clearCachedPassphrase()
        context.getSharedPreferences("focus_notes_vault_security_prefs", 0).edit().clear().commit()
        for (databaseName in listOf("keep_notes_vault.db", "focus_database_vault.db")) {
        val file = context.getDatabasePath(databaseName)
        file.parentFile!!.mkdirs()
        file.writeText("synthetic-existing-encrypted-database")
        try {
            DatabaseKeyProvider.getOrCreatePassphrase(context)
            fail("Existing database must block new key creation")
        } catch (_: IllegalStateException) {
            assertEquals("synthetic-existing-encrypted-database", file.readText())
            assertFalse(context.getSharedPreferences("focus_notes_vault_security_prefs", 0).contains("enc_db_passphrase"))
        } finally { file.delete(); DatabaseKeyProvider.clearCachedPassphrase() }
        }
    }

    @Test fun purgeKeepsExpiredNotesWhenSnapshotCannotBeWritten() = runBlocking {
        val dao = NoteDatabase.getInstance(context).noteDao()
        dao.deleteAllNotes()
        val note = NoteEntity(id = 901, title = "Must survive", isTrashed = true, trashedAt = 1L)
        dao.insertNote(note)
        val directory = File(context.filesDir, "auto_backups/pre_op")
        directory.deleteRecursively()
        directory.parentFile!!.mkdirs()
        directory.writeText("blocks snapshot directory creation")
        try {
            assertEquals(0, DataSafetyManager.purgeExpiredTrash(context, dao))
            assertEquals(note, dao.getNoteByIdSync(901))
        } finally { directory.delete() }
    }

    @Test fun corruptVaultNoteCannotBeOpenedForEditing() {
        val security = com.focusbyrj.app.data.note.ArchiveVaultSecurity
        assertTrue(security.setPasscode(context, "123456"))
        val vm = com.focusbyrj.app.ui.screens.notes.NotesViewModel(context as android.app.Application)
        try {
            vm.openExistingNote(NoteEntity(id = 903, isArchived = true, content = "ENC_VAULT_V1:broken"))
            assertNull(vm.editingState.value)
        } finally { security.lockVault() }
    }

    @Test fun cleanRestoreRemapsMediaAndPreservesExistingAttachmentBytes() = runBlocking {
        val dao = NoteDatabase.getInstance(context).noteDao()
        dao.deleteAllNotes()
        val media = File(context.filesDir, "keep_images/fixture.bin")
        EncryptedMediaStorage.writeEncryptedBytes(media, "backup bytes".toByteArray())
        dao.insertNote(NoteEntity(id = 904, title = "Attachment owner",
            imageUrisJson = org.json.JSONArray().put(media.absolutePath).toString()))
        val habitDao = (context.applicationContext as FocusApplication).database.habitDao()
        habitDao.deleteAllLogs()
        habitDao.deleteAllHabits()
        val pausedHabit = com.focusbyrj.app.data.Habit(id = 905, title = "Paused habit", pausedAt = 123456L,
            pauseHistoryJson = "[{\"start\":1,\"end\":2}]")
        habitDao.insertHabit(pausedHabit)
        val backup = File(context.cacheDir, "roundtrip.fbck")
        val created = BackupRestoreManager.createEncryptedBackup(context, Uri.fromFile(backup), "audit-fixture")
        assertTrue(created.exceptionOrNull().toString(), created.isSuccess)
        EncryptedMediaStorage.writeEncryptedBytes(media, "live bytes".toByteArray())
        val restored = BackupRestoreManager.restoreEncryptedBackup(context, Uri.fromFile(backup), "audit-fixture", cleanRestore = true)
        assertTrue(restored.exceptionOrNull().toString(), restored.isSuccess)
        val note = dao.getNoteByIdSync(904)!!
        assertEquals("Attachment owner", note.title)
        val restoredFile = File(note.getImageUris().single())
        assertNotEquals(media.absolutePath, restoredFile.absolutePath)
        assertEquals("backup bytes", EncryptedMediaStorage.readDecryptedBytes(restoredFile)!!.decodeToString())
        assertEquals("live bytes", EncryptedMediaStorage.readDecryptedBytes(media)!!.decodeToString())
        assertEquals(pausedHabit, habitDao.getHabitById(905))
    }

    @Test fun cleanRestoreKeepsLegacyNotesWithAssignedIdsAlongsideStableIds() = runBlocking {
        val dao = NoteDatabase.getInstance(context).noteDao()
        dao.deleteAllNotes()
        dao.insertNote(NoteEntity(id = 999, title = "Replace me"))
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use {
            it.putNextEntry(ZipEntry("data.json"))
            it.write("""{"version":1,"notes":[{"title":"Assigned"},{"id":1,"title":"Stable"}]}""".toByteArray())
            it.closeEntry()
        }
        val file = File(context.cacheDir, "mixed-ids.fbck")
        file.outputStream().use { CryptoBackupEngine.encrypt(zip.toByteArray(), "audit-fixture".toCharArray(), it) }
        val result = BackupRestoreManager.restoreEncryptedBackup(context, Uri.fromFile(file), "audit-fixture", cleanRestore = true)
        assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
        assertEquals(setOf("Stable", "Assigned"), dao.getAllNotesList().map { it.title }.toSet())
    }

    private suspend fun rejectedManifestPreservesLiveData(manifest: String, tamperTag: Boolean = false) {
        val dao = NoteDatabase.getInstance(context).noteDao()
        dao.deleteAllNotes()
        val original = NoteEntity(id = 902, title = "Original", content = "unchanged")
        dao.insertNote(original)
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { it.putNextEntry(ZipEntry("data.json")); it.write(manifest.toByteArray()); it.closeEntry() }
        val encrypted = ByteArrayOutputStream()
        CryptoBackupEngine.encrypt(zip.toByteArray(), "audit-fixture".toCharArray(), encrypted)
        val bytes = encrypted.toByteArray()
        if (tamperTag) bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        val file = File(context.cacheDir, "audit-backup.fbck").apply { writeBytes(bytes) }
        val result = BackupRestoreManager.restoreEncryptedBackup(context, Uri.fromFile(file), "audit-fixture", cleanRestore = true)
        assertTrue(result.exceptionOrNull().toString(), result.isFailure)
        assertEquals(listOf(original), dao.getAllNotesList())
    }

    @Test fun futureManifestVersionIsRejectedBeforeMutation() = runBlocking {
        rejectedManifestPreservesLiveData("""{"version":999,"notes":[],"mediaManifest":[]}""")
    }
    @Test fun wrongArrayTypeIsRejectedBeforeMutation() = runBlocking {
        rejectedManifestPreservesLiveData("""{"version":2,"notes":"damaged","mediaManifest":[]}""")
    }
    @Test fun duplicateNotesCannotSilentlyOverwriteEachOther() = runBlocking {
        rejectedManifestPreservesLiveData("""{"version":2,"notes":[{"id":1,"title":"a"},{"id":1,"title":"b"}],"mediaManifest":[]}""")
    }
    @Test fun damagedPayloadTagCannotCommitEvenWhenZipLooksComplete() = runBlocking {
        rejectedManifestPreservesLiveData("""{"version":2,"notes":[],"mediaManifest":[]}""", tamperTag = true)
    }
}
