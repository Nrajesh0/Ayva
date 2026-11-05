package com.focusbyrj.app.util.backup

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class)
class BackupSecondAuditTest {
    private val app get()=ApplicationProvider.getApplicationContext<Context>()
    @Test fun aQueuedRequestCannotWriteIntoAChangedBackupSetup() = runBlocking {
        val store=AutomaticBackupStore(app,TestBackupWrapper())
        BackupRecoveryKey.generate().use{store.install(it)}
        store.save(store.config().copy(enabled=true,tree="content://test/tree/old",keyExported=true,keyAcknowledged=true))
        val old=BackupRequestScope(store.config().stream,store.config().keyId,store.requestEpoch())
        store.save(store.config().copy(enabled=false,tree="content://test/tree/new",stream=UUID.randomUUID().toString()))
        val before=app.getSharedPreferences("automatic_full_backups_v1",0).all.toMap()
        var touched=false
        val create:suspend(Context,Uri,String,ByteArray?)->Result<BackupRestoreManager.BackupMetadata> = {_,uri,_,_->
            File(uri.path!!).writeText("new encrypted archive fixture")
            Result.success(BackupRestoreManager.BackupMetadata(2,System.currentTimeMillis(),"test",0,0,0,0,0,0))
        }
        assertTrue(AutomaticBackups.run(app,true,store,{touched=true;MemoryBackupDestination()},create,expectedScope=old))
        assertFalse("A stale queued request touched the new destination",touched)
        assertEquals(before,app.getSharedPreferences("automatic_full_backups_v1",0).all)
    }
    @Test @Config(application=com.focusbyrj.app.FocusApplication::class)
    fun plaintextArchivedNotesAfterDisablingVaultRemainFullyBackedUp() = runBlocking {
        val vault=com.focusbyrj.app.data.note.ArchiveVaultSecurity;val notes=com.focusbyrj.app.data.note.NoteDatabase.getInstance(app).noteDao()
        notes.deleteAllNotes();vault.lockVault();app.getSharedPreferences(vault.PREFS_NAME,0).edit().putString(vault.KEY_STATUS,"disabled").commit()
        try {
            notes.insertNote(com.focusbyrj.app.data.note.NoteEntity(id=8401,title="Archived without a passcode",content="Still must be backed up",isArchived=true))
            val file=File(app.cacheDir,"disabled-vault-backup.focusbackup")
            val result=BackupRestoreManager.createEncryptedBackup(app,Uri.fromFile(file),"manual-test-password")
            assertTrue(result.exceptionOrNull()?.toString(),result.isSuccess)
            assertTrue(BackupRestoreManager.verifyEncryptedBackup(app,Uri.fromFile(file),"manual-test-password").isSuccess)
        } finally {notes.deleteAllNotes();vault.lockVault();app.getSharedPreferences(vault.PREFS_NAME,0).edit().clear().commit()}
    }
    @Test fun pausingOrReimportingInvalidatesARequestEvenWhenItsKeyAndFolderAreUnchanged() = runBlocking {
        val store=AutomaticBackupStore(app,TestBackupWrapper());BackupRecoveryKey.generate().use{store.install(it)}
        store.save(store.config().copy(tree="content://test/tree/root",keyExported=true,keyAcknowledged=true,enabled=true))
        val request=BackupRequestScope(store.config().stream,store.config().keyId,store.requestEpoch())
        store.invalidateRequests()
        assertFalse(request.matches(store.config(),store.requestEpoch()))
        var touched=false
        assertTrue(AutomaticBackups.run(app,true,store,{touched=true;MemoryBackupDestination()},expectedScope=request))
        assertFalse(touched)
        val current=BackupRequestScope(store.config().stream,store.config().keyId,store.requestEpoch())
        store.key().use{store.install(it,resetVaultBinding=true)}
        assertFalse(current.matches(store.config(),store.requestEpoch()))
    }
    @Test fun repairAndRevocationCannotSilentlyTransferPrivateKeyOwnership() {
        val store=AutomaticBackupStore(app,TestBackupWrapper());val originalVault=ByteArray(32){it.toByte()};val replacementVault=ByteArray(32){(it+1).toByte()}
        BackupRecoveryKey.generate().use { master ->
            store.install(master);store.authorizeVault(originalVault);store.revokeVault()
            assertThrows(SecurityException::class.java){store.requireVaultIdentity(null)}
            assertThrows(SecurityException::class.java){store.authorizeVault(replacementVault)}
            app.getSharedPreferences("automatic_full_backups_v1",0).edit().putString("config","broken configuration").commit()
            store.storedKey().use{store.install(it)}
            assertThrows(SecurityException::class.java){store.authorizeVault(replacementVault)}
            // Explicit possession of the portable credential is the recovery/transfer path.
            store.install(master,resetVaultBinding=true);store.authorizeVault(replacementVault)
            store.requireVaultIdentity(replacementVault)
            store.key().use{assertEquals(master.credential(),it.credential())}
            assertThrows(SecurityException::class.java){store.requireVaultIdentity(originalVault)}
        }
    }
    @Test @Config(application=com.focusbyrj.app.FocusApplication::class)
    fun legacyPlaintextInsideAnEnabledLockedVaultStillRequiresAuthentication() = runBlocking {
        val vault=com.focusbyrj.app.data.note.ArchiveVaultSecurity;val notes=com.focusbyrj.app.data.note.NoteDatabase.getInstance(app).noteDao()
        notes.deleteAllNotes();vault.lockVault();app.getSharedPreferences(vault.PREFS_NAME,0).edit().putString(vault.KEY_STATUS,"enabled").commit()
        try {
            notes.insertNote(com.focusbyrj.app.data.note.NoteEntity(id=8402,title="Private legacy plaintext",isArchived=true))
            assertFalse(BackupRestoreManager.canCreateBackup(app).first)
            val file=File(app.cacheDir,"locked-legacy-plaintext.focusbackup")
            assertTrue(BackupRestoreManager.createEncryptedBackup(app,Uri.fromFile(file),"manual-test-password").isFailure)
        } finally {notes.deleteAllNotes();vault.lockVault();app.getSharedPreferences(vault.PREFS_NAME,0).edit().clear().commit()}
    }
    @Test fun overNestedLocalConfigIsRejectedBeforeRecursiveParsing() {
        val raw="{\"unused\":"+"[".repeat(5000)+"0"+"]".repeat(5000)+"}"
        assertThrows(IllegalArgumentException::class.java){AutomaticBackupConfig.read(raw)}
    }
    @Test fun overNestedLocalStageIsRejectedBeforeRecursiveParsing() {
        val store=AutomaticBackupStore(app,TestBackupWrapper())
        app.getSharedPreferences("automatic_full_backups_v1",0).edit().putString("stage","{\"unused\":"+"[".repeat(5000)+"0"+"]".repeat(5000)+"}").commit()
        assertThrows(IllegalArgumentException::class.java){store.staged()}
    }
    @Test fun overNestedOwnershipRecordsAreRejectedBeforeRecursiveParsing() {
        val store=AutomaticBackupStore(app,TestBackupWrapper())
        app.getSharedPreferences("automatic_full_backups_v1",0).edit().putString("records","[".repeat(5000)+"0"+"]".repeat(5000)).commit()
        assertThrows(IllegalArgumentException::class.java){store.records()}
    }
}
