package com.focusbyrj.app.util.backup

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.crypto.Argon2idKdf
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Run terminateDuringRestore, then verifyStartupRecovery in a new instrumentation process. */
@RunWith(AndroidJUnit4::class)
class RestoreRecoveryDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FocusApplication
    private fun fingerprint(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private val fixture get() = File(app.noBackupFilesDir, "native_restore_test.json")

    @Test fun terminateDuringRestore() = runBlocking {
        check(!fixture.exists()) { "A prior recovery fixture must be verified first" }
        val notes = NoteDatabase.getInstance(app).noteDao()
        val tasks = app.database.taskDao()
        val noteId = notes.insertNote(NoteEntity(title = "Audit recovery fixture", content = "Before termination"))
        val taskId = tasks.insertTask(Task(title = "Audit recovery fixture"))
        app.getSharedPreferences("focus_prefs", 0).edit().putString("audit_recovery", "before").commit()
        fixture.writeText(JSONObject().put("noteId", noteId).put("taskId", taskId)
            .put("notes", fingerprint(notes.getAllNotesList().sortedBy { it.id }.toString()))
            .put("tasks", fingerprint(tasks.getAllTasksList().sortedBy { it.id }.toString())).toString())
        File(app.cacheDir, "restore_media_stage_native_interrupted/keep_images/incomplete.enc").apply {
            parentFile!!.mkdirs(); writeBytes(byteArrayOf())
        }
        RestoreJournal.checkpointHook = { phase ->
            if (phase == "before_commit") {
                val proof = JSONObject(fixture.readText()).put("terminatedAt", phase)
                fixture.outputStream().use { output -> output.write(proof.toString().toByteArray()); output.fd.sync() }
                Process.killProcess(Process.myPid())
            }
        }
        RestoreJournal.mutate(app) {
            notes.updateNote(notes.getNoteByIdSync(noteId)!!.copy(content = "Interrupted new value"))
            tasks.updateTask(tasks.getTaskById(taskId)!!.copy(title = "Interrupted new value"))
            app.getSharedPreferences("focus_prefs", 0).edit().putString("audit_recovery", "partial").commit()
            Result.success(Unit)
        }
        fail("The process should have terminated before commit")
    }

    @Test fun verifyStartupRecovery() = runBlocking {
        val saved = JSONObject(fixture.readText())
        assertEquals("before_commit", saved.getString("terminatedAt"))
        val notes = NoteDatabase.getInstance(app).noteDao()
        val tasks = app.database.taskDao()
        assertEquals(saved.getString("notes"), fingerprint(notes.getAllNotesList().sortedBy { it.id }.toString()))
        assertEquals(saved.getString("tasks"), fingerprint(tasks.getAllTasksList().sortedBy { it.id }.toString()))
        assertEquals("before", app.getSharedPreferences("focus_prefs", 0).getString("audit_recovery", null))
        assertFalse(File(app.filesDir, "restore_before_state.enc").exists())
        val interruptedStage = File(app.cacheDir, "restore_media_stage_native_interrupted/keep_images/incomplete.enc")
        assertTrue("Unowned interrupted staging must not block startup or be silently imported/deleted", interruptedStage.exists())
        File(app.cacheDir, "restore_media_stage_native_interrupted").deleteRecursively()
        notes.getNoteByIdSync(saved.getLong("noteId"))?.let { notes.deleteNote(it) }
        tasks.getTaskById(saved.getLong("taskId"))?.let { tasks.deleteTask(it) }
        app.getSharedPreferences("focus_prefs", 0).edit().remove("audit_recovery").commit()
        fixture.delete()
        Unit
    }

    @Test fun snapshotRestoreKeepsVaultEnabledAndDoesNotDuplicate() = runBlocking<Unit> {
        val vault = com.focusbyrj.app.data.note.ArchiveVaultSecurity
        check(vault.getVaultStatus(app) != com.focusbyrj.app.data.note.ArchiveVaultSecurity.VaultStatus.ENABLED) { "Use a clean emulator fixture" }
        val notes = NoteDatabase.getInstance(app).noteDao()
        check(notes.getAllNotesList().none { it.isArchived }) { "Use a clean fixture without private notes" }
        val pin = "729461"
        val media = File(app.filesDir, "keep_images/native-restore-${java.util.UUID.randomUUID()}.bin")
        EncryptedMediaStorage.writeEncryptedBytes(media, "synthetic-native-restore-media".toByteArray())
        val publicId = notes.insertNote(NoteEntity(title = "Native restore public fixture", imageUrisJson = org.json.JSONArray(listOf(media.path)).toString()))
        var privateId = 0L
        val archive = File(app.cacheDir, "native-snapshot-restore.focusbackup")
        val ownedMedia = mutableSetOf(media)
        try {
            assertTrue(vault.setPasscode(app, pin))
            val key = vault.getActiveVaultSubKey()!!
            try { privateId = notes.insertNote(com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(
                NoteEntity(title = "Native restore private fixture", isArchived = true), key)) }
            finally { key.fill(0) }
            BackupRestoreManager.createEncryptedBackup(app, android.net.Uri.fromFile(archive), "synthetic-native-password").getOrThrow()
            vault.lockVault()
            repeat(2) {
                val prepared = BackupRestoreManager.prepareRestore(app, android.net.Uri.fromFile(archive), "synthetic-native-password").getOrThrow()
                assertTrue(BackupRestoreManager.applyPreparedRestore(app, prepared).exceptionOrNull() is BackupRestoreManager.RequiresVaultUnlockException)
                if (it == 0) {
                    assertTrue(vault.verifyPasscode(app, pin) is com.focusbyrj.app.data.note.ArchiveVaultSecurity.VerifyResult.Success)
                    val authorization = BackupRestoreManager.captureRestoreAuthorization(app)
                    assertTrue(vault.isVaultLocked(app))
                    try { BackupRestoreManager.applyPreparedRestore(app, prepared, authorization = authorization).getOrThrow() }
                    finally { authorization.close() }
                } else BackupRestoreManager.applyPreparedRestore(app, prepared, vaultPin = pin).getOrThrow()
                assertEquals(com.focusbyrj.app.data.note.ArchiveVaultSecurity.VaultStatus.ENABLED, vault.getVaultStatus(app))
                assertTrue(vault.isVaultLocked(app))
                assertEquals(1, notes.getAllNotesList().count { note -> note.id == privateId })
                assertEquals(1, notes.getAllNotesList().count { note -> note.id == publicId })
                assertTrue(com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(notes.getNoteByIdSync(privateId)!!))
                val restoredMedia = File(notes.getNoteByIdSync(publicId)!!.getImageUris().single())
                ownedMedia.add(restoredMedia)
                assertEquals("synthetic-native-restore-media", EncryptedMediaStorage.readDecryptedBytes(restoredMedia)!!.decodeToString())
            }
        } finally {
            if (vault.getVaultStatus(app) == com.focusbyrj.app.data.note.ArchiveVaultSecurity.VaultStatus.ENABLED) {
                check(vault.verifyPasscode(app, pin) is com.focusbyrj.app.data.note.ArchiveVaultSecurity.VerifyResult.Success)
                check(vault.disablePasscode(app))
            }
            notes.getNoteByIdSync(privateId)?.let { notes.deleteNote(it) }
            notes.getNoteByIdSync(publicId)?.let { notes.deleteNote(it) }
            ownedMedia.forEach { it.delete() }
            archive.delete(); vault.lockVault()
        }
    }

    @Test fun nativeKdfAndAndroidKeyStoreRejectTampering() {
        assertTrue("Native Argon2id must load from the installed APK", Argon2idKdf.isNativeAvailable())
        val salt = ByteArray(16) { it.toByte() }
        val first = Argon2idKdf.deriveKey("synthetic-fixture".toCharArray(), salt)
        assertArrayEquals(first, Argon2idKdf.deriveKey("synthetic-fixture".toCharArray(), salt))
        val file = File(app.cacheDir, "native-crypto-fixture.enc")
        EncryptedMediaStorage.writeEncryptedBytes(file, "synthetic-private-bytes".toByteArray())
        assertEquals("synthetic-private-bytes", EncryptedMediaStorage.readDecryptedBytes(file)!!.decodeToString())
        val tampered = file.readBytes(); tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
        file.writeBytes(tampered)
        assertNull(EncryptedMediaStorage.readDecryptedBytes(file))
        file.delete()
        val stage = File(app.cacheDir, "restore_media_stage_native_fixture/keep_images/old.bin")
        stage.parentFile!!.mkdirs(); stage.writeText("synthetic-legacy-media")
        assertNull("Unsupported plaintext stages must not be imported", EncryptedMediaStorage.openDecryptedInputStream(stage))
        assertEquals("synthetic-legacy-media", stage.readText())
        File(app.cacheDir, "restore_media_stage_native_fixture").deleteRecursively()
    }
}
