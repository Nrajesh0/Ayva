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
        notes.getNoteByIdSync(saved.getLong("noteId"))?.let { notes.deleteNote(it) }
        tasks.getTaskById(saved.getLong("taskId"))?.let { tasks.deleteTask(it) }
        app.getSharedPreferences("focus_prefs", 0).edit().remove("audit_recovery").commit()
        fixture.delete()
        Unit
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
        RestoreStaging.protectLegacyStages(app)
        assertTrue(EncryptedMediaStorage.isEncrypted(stage))
        assertEquals("synthetic-legacy-media", EncryptedMediaStorage.readDecryptedBytes(stage)!!.decodeToString())
        File(app.cacheDir, "restore_media_stage_native_fixture").deleteRecursively()
    }
}
