package com.focusbyrj.app.util.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36])
class PortableAutomaticBackupTest {
    @After fun cleanup() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>() as FocusApplication
        NoteDatabase.getInstance(app).noteDao().deleteAllNotes()
        ArchiveVaultSecurity.lockVault()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME,0).edit().clear().commit()
        Unit
    }
    @Test fun anEncryptedButUnrestorableManifestCannotBePublishedOrTriggerRetention() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>() as FocusApplication
        NoteDatabase.getInstance(app).noteDao().deleteAllNotes()
        app.getSharedPreferences("exam_countdowns",0).edit().putString("invalid-persisted-widget","malformed widget configuration").commit()
        try {
            val store=AutomaticBackupStore(app,TestBackupWrapper())
            BackupRecoveryKey.generate().use{store.install(it)}
            store.save(store.config().copy(enabled=true,tree="content://test/tree/root",keyExported=true,keyAcknowledged=true,keep=1))
            val d=MemoryBackupDestination();val older=d.create("older-recoverable.focusbackup")
            d.write(older.uri).use{it.write("existing protected archive".toByteArray())}
            assertFalse(AutomaticBackups.run(app,true,store,{d}))
            assertEquals(setOf(older.uri),d.files.keys);assertEquals(0L,store.config().lastSuccess)
            assertTrue(store.records().isEmpty())
        } finally {app.getSharedPreferences("exam_countdowns",0).edit().remove("invalid-persisted-widget").commit()}
    }
    @Test fun corruptedEncryptedArchivesAndWrongRecoveryKeysCannotMutateSavedData() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>() as FocusApplication
        val notes=NoteDatabase.getInstance(app).noteDao();notes.deleteAllNotes()
        val attachment=File(app.filesDir,"keep_images/authentication-fixture.bin").apply{parentFile!!.mkdirs()}
        val originalMedia="protected original attachment".toByteArray();EncryptedMediaStorage.writeEncryptedBytes(attachment,originalMedia)
        val note=NoteEntity(id=7301,title="Original data must survive",content="Current saved text",imageUrisJson=JSONArray().put(attachment.absolutePath).toString())
        notes.insertNote(note)
        BackupRecoveryKey.generate().use { key ->
            val original=File(app.cacheDir,"authentication-original.focusbackup")
            assertTrue(BackupRestoreManager.createAutomaticEncryptedBackup(app,Uri.fromFile(original),key.credential(),null).isSuccess)
            val bytes=original.readBytes()
            val variants=listOf(bytes.copyOf().apply{this[0]=(this[0].toInt() xor 1).toByte()},
                bytes.copyOf().apply{this[40]=(this[40].toInt() xor 1).toByte()},
                bytes.copyOf().apply{this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()},bytes.copyOf(bytes.size-12),bytes+byteArrayOf(0))
            for ((index,variant) in variants.withIndex()) {
                val file=File(app.cacheDir,"authentication-corrupt-$index.focusbackup").apply{writeBytes(variant)}
                assertTrue("verification accepted corrupt variant $index",BackupRestoreManager.verifyEncryptedBackup(app,Uri.fromFile(file),key.credential()).isFailure)
                assertTrue("restore accepted corrupt variant $index",BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(file),key.credential(),cleanRestore=true).isFailure)
                assertEquals(note,notes.getNoteByIdSync(note.id));assertArrayEquals(originalMedia,EncryptedMediaStorage.readDecryptedBytes(attachment))
            }
            BackupRecoveryKey.generate().use { wrong ->
                assertTrue(BackupRestoreManager.verifyEncryptedBackup(app,Uri.fromFile(original),wrong.credential()).isFailure)
                assertTrue(BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(original),wrong.credential(),cleanRestore=true).isFailure)
                assertEquals(note,notes.getNoteByIdSync(note.id));assertArrayEquals(originalMedia,EncryptedMediaStorage.readDecryptedBytes(attachment))
            }
        }
        assertFalse(MaintenanceGate.recoveryRequired)
    }
    @Test fun authenticatedOverNestedJsonIsRejectedBeforeAnyRestoreMutation() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>() as FocusApplication
        val notes=NoteDatabase.getInstance(app).noteDao();notes.deleteAllNotes()
        val original=NoteEntity(id=7302,title="Keep current data",content="Survives malformed input")
        notes.insertNote(original)
        val nested="{\"unused\":"+"[".repeat(3000)+"0"+"]".repeat(3000)+"}"
        val nativeBlocks="<!--NOTESNOOK_BLOCKS:"+nested+":BLOCKS_END-->"
        fun base()=org.json.JSONObject().put("version",2).put("mediaManifest",JSONArray()).put("notes",JSONArray())
        val manifests=listOf(
            "{\"version\":2,\"mediaManifest\":[],\"notes\":[],\"unused\":"+"[".repeat(10000)+"0"+"]".repeat(10000)+"}",
            base().put("preferences",org.json.JSONObject().put("exam_countdowns",org.json.JSONObject().put("nested",nested))).toString(),
            base().put("tasks",JSONArray().put(org.json.JSONObject().put("id",99).put("title","Unsafe task metadata").put("planningJson",nested))).toString(),
            base().put("notes",JSONArray().put(org.json.JSONObject().put("id",99).put("imageUrisJson","[".repeat(3000)+"0"+"]".repeat(3000)))).toString(),
            base().put("notes",JSONArray().put(org.json.JSONObject().put("id",99).put("content",nativeBlocks))).toString()
        )
        BackupRecoveryKey.generate().use { key ->
            for ((index,manifest) in manifests.withIndex()) {
                val file=File(app.cacheDir,"nested-authenticated-$index.focusbackup")
                file.outputStream().use {out ->CryptoBackupEngine.openEncryptingStream(out,key.credential().toCharArray()).use {encrypted ->
                    java.util.zip.ZipOutputStream(encrypted).use{zip ->zip.putNextEntry(java.util.zip.ZipEntry("data.json"));zip.write(manifest.toByteArray());zip.closeEntry()}
                }}
                assertTrue("Unsafe manifest $index was verified",BackupRestoreManager.verifyEncryptedBackup(app,Uri.fromFile(file),key.credential()).isFailure)
                assertTrue("Unsafe manifest $index was restored",BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(file),key.credential(),cleanRestore=true).isFailure)
                assertEquals(original,notes.getNoteByIdSync(original.id))
            }
        }
        assertEquals(original,notes.getNoteByIdSync(original.id));assertFalse(MaintenanceGate.recoveryRequired)
    }
    @Test fun corruptedLiveAttachmentCannotReplaceTheLastHealthyAutomaticBackup() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>() as FocusApplication
        val notes=NoteDatabase.getInstance(app).noteDao();notes.deleteAllNotes()
        val attachment=File(app.filesDir,"keep_images/live-corruption-fixture.bin").apply{parentFile!!.mkdirs()}
        EncryptedMediaStorage.writeEncryptedBytes(attachment,"recoverable previous attachment".toByteArray())
        notes.insertNote(NoteEntity(id=7303,title="Keep healthy backup",imageUrisJson=JSONArray().put(attachment.absolutePath).toString()))
        val store=AutomaticBackupStore(app,TestBackupWrapper());BackupRecoveryKey.generate().use{store.install(it)}
        store.save(store.config().copy(enabled=true,tree="content://test/tree/root",keyExported=true,keyAcknowledged=true,keep=1))
        val d=MemoryBackupDestination()
        assertTrue(AutomaticBackups.run(app,true,store,{d}));val before=store.config();val record=store.records().single()
        attachment.writeBytes(attachment.readBytes().apply{this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()})
        assertFalse(AutomaticBackups.run(app,true,store,{d}))
        assertEquals(before.lastSuccess,store.config().lastSuccess);assertEquals(listOf(record),store.records())
        assertTrue(d.files.containsKey(record.uri))
    }
    @Test fun exportedRecoveryKeyRestoresRecordsPrivateNotesAndMediaWithoutAutomaticDeviceCredentials() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>() as FocusApplication
        val notes=NoteDatabase.getInstance(app).noteDao()
        notes.deleteAllNotes()
        val attachment=File(app.filesDir,"keep_images/portable-fixture.bin").apply{parentFile!!.mkdirs()}
        EncryptedMediaStorage.writeEncryptedBytes(attachment,"original attachment bytes".toByteArray())
        notes.insertNote(NoteEntity(id=7201,title="Portable ordinary note",content="Saved text",imageUrisJson=JSONArray().put(attachment.absolutePath).toString()))
        app.database.taskDao().insertTask(Task(id=7203,title="Portable task"))
        assertTrue(ArchiveVaultSecurity.setPasscode(app,"123456"))
        val vault=ArchiveVaultSecurity.getActiveVaultSubKey()!!
        notes.insertNote(VaultPayloadEncryptor.encryptNotePayload(NoteEntity(id=7202,title="Private title",content="Private saved text",isArchived=true),vault))
        val store=AutomaticBackupStore(app,TestBackupWrapper())
        val recoveryDocument=BackupRecoveryKey.generate().use {key -> store.install(key);key.document()}
        store.authorizeVault(vault);vault.fill(0);ArchiveVaultSecurity.lockVault()
        store.save(store.config().copy(enabled=true,tree="content://test/tree/root",keyExported=true,keyAcknowledged=true))
        val manual=File(app.cacheDir,"locked-manual.focusbackup")
        assertTrue(BackupRestoreManager.createEncryptedBackup(app,Uri.fromFile(manual),"manual-password").isFailure)
        val destination=MemoryBackupDestination()
        assertTrue(AutomaticBackups.run(app,true,store,{destination}))
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        val record=store.records().single()
        val externalArchive=destination.read(record.uri).readBytes()
        // Independently decrypt the ZIP with only the portable credential: attachment
        // entries must be raw original bytes, not ciphertext requiring the old media key.
        BackupRecoveryKey.parse(recoveryDocument).use { key ->
            val plain=CryptoBackupEngine.decrypt(externalArchive.inputStream(),key.credential().toCharArray())
            try {
                var checked=false
                java.util.zip.ZipInputStream(plain.inputStream()).use {zip ->
                    var entry=zip.nextEntry
                    while(entry!=null){if(entry.name=="media/keep_images/portable-fixture.bin"){assertArrayEquals("original attachment bytes".toByteArray(),zip.readBytes());checked=true};zip.closeEntry();entry=zip.nextEntry}
                }
                assertTrue(checked)
            } finally {plain.fill(0)}
        }
        assertThrows(SecurityException::class.java){store.authorizeVault(ByteArray(32){it.toByte()})}
        // The store rejects a different vault before replacing authorization. The shared
        // exporter must still reject an explicitly supplied wrong vault key as a second barrier.
        val invalid=File(app.cacheDir,"wrong-authorized-vault.focusbackup")
        assertTrue(BackupRestoreManager.createAutomaticEncryptedBackup(app,Uri.fromFile(invalid),BackupRecoveryKey.parse(recoveryDocument).use{it.credential()},ByteArray(32){it.toByte()}).isFailure)
        assertEquals(listOf(record),store.records());assertTrue(destination.files.containsKey(record.uri))
        val backup=File(app.cacheDir,"portable-restore.focusbackup").apply{writeBytes(externalArchive)}
        // Simulate losing device-local automatic-backup credentials. Only the external document remains.
        app.getSharedPreferences("automatic_full_backups_v1",0).edit().clear().commit()
        notes.deleteAllNotes();app.database.taskDao().deleteAllTasks();attachment.delete()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME,0).edit().clear().commit();ArchiveVaultSecurity.lockVault()
        BackupRecoveryKey.parse(recoveryDocument).use { recovered ->
            val verified=BackupRestoreManager.verifyEncryptedBackup(app,Uri.fromFile(backup),recovered.credential())
            assertTrue(verified.exceptionOrNull()?.stackTraceToString(),verified.isSuccess)
            val restored=BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(backup),recovered.credential(),cleanRestore=true,vaultPin="654321")
            assertTrue(restored.exceptionOrNull()?.stackTraceToString(),restored.isSuccess)
        }
        assertEquals("Portable task",app.database.taskDao().getTaskById(7203)!!.title)
        val ordinary=notes.getNoteByIdSync(7201)!!
        assertEquals("Saved text",ordinary.content)
        val path=JSONArray(ordinary.imageUrisJson).getString(0)
        assertEquals("original attachment bytes",EncryptedMediaStorage.readDecryptedBytes(File(path))!!.decodeToString())
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app,"654321") is ArchiveVaultSecurity.VerifyResult.Success)
        val key=ArchiveVaultSecurity.getActiveVaultSubKey()!!
        try {
            val result=VaultPayloadEncryptor.tryDecryptNotePayload(notes.getNoteByIdSync(7202)!!,key)
            assertTrue(result is VaultPayloadEncryptor.DecryptionResult.Success)
            assertEquals("Private saved text",(result as VaultPayloadEncryptor.DecryptionResult.Success).note.content)
        } finally {key.fill(0);ArchiveVaultSecurity.lockVault()}
        assertFalse(app.getSharedPreferences("automatic_full_backups_v1",0).contains("wrapped_key"))
    }
}
