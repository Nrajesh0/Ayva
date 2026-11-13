package com.focusbyrj.app.util.backup

import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.screens.ManualBackupPhase
import com.focusbyrj.app.ui.screens.ManualBackupViewModel
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ManualBackupIntegrationTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusApplication>()
    private val notes get() = NoteDatabase.getInstance(app).noteDao()
    private val folder get() = File(app.noBackupFilesDir, "manual_backup_staging")
    private val password = "separate-manual-backup-password"

    @Before fun setup() = runBlocking {
        notes.deleteAllNotes()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        app.getSharedPreferences("ayva_backup_verification", 0).edit().clear().commit()
        ArchiveVaultSecurity.lockVault()
        assertTrue(ArchiveVaultSecurity.setPasscode(app, "123456"))
        val key = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        try { notes.insertNote(VaultPayloadEncryptor.encryptNotePayload(NoteEntity(id = 90210,
            title = "Private backup fixture", content = "private original content", isArchived = true), key)) }
        finally { key.fill(0) }
        Unit
    }
    @After fun cleanup() = runBlocking {
        ArchiveVaultSecurity.lockVault()
        notes.deleteAllNotes()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        folder.deleteRecursively()
        Unit
    }

    @Test fun preparationReportsLiveProgressAndCancelledWorkCannotOverwriteTheNextMessage() = runBlocking {
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finished = kotlinx.coroutines.CompletableDeferred<Unit>()
        val model = ManualBackupViewModel(app) { _, _, _ ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                try {
                    BackupProgress.report("Checking attachment 1 of 2")
                    release.await()
                    BackupProgress.report("Late progress from cancelled work")
                    throw kotlinx.coroutines.CancellationException("Synthetic cancelled preparation")
                } finally { finished.complete(Unit) }
            }
        }
        try {
            model.prepare(password)
            await { model.state.value.message == "Checking attachment 1 of 2" }
            assertEquals(ManualBackupPhase.PREPARING, model.state.value.phase)
            model.cancel("Preparation cancelled")
            release.complete(Unit)
            await { finished.isCompleted }
            assertEquals("Preparation cancelled", model.state.value.message)
            assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        } finally { release.complete(Unit); model.cancel() }
    }

    @Test fun aOneTimeAuthorizationPreparesPrivateNotesBeforeThePickerWithoutChangingLiveData() = runBlocking {
        val original = notes.getNoteByIdSync(90210)!!
        val key = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        ArchiveVaultSecurity.lockVault()
        val prepared = try { ManualBackupFiles.prepare(app, password, key) } finally { key.fill(0) }
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        assertEquals(original, notes.getNoteByIdSync(90210))
        assertFalse(prepared.file.readBytes().toString(Charsets.ISO_8859_1).contains("private original content"))
        assertEquals(0L, app.getSharedPreferences("ayva_backup_verification", 0).getLong("last_verified_export_ms", 0))
        assertTrue(BackupRestoreManager.verifyEncryptedBackup(app, Uri.fromFile(prepared.file), password).isSuccess)
        val plaintext = CryptoBackupEngine.decrypt(prepared.file.inputStream(), password.toCharArray())
        try {
            ZipInputStream(plaintext.inputStream()).use { zip ->
                var entry = zip.nextEntry; var manifest = ""
                while (entry != null) { if (entry.name == "data.json") manifest = zip.readBytes().decodeToString(); zip.closeEntry(); entry = zip.nextEntry }
                assertTrue(manifest.contains("private original content"))
            }
        } finally { plaintext.fill(0); ManualBackupFiles.discard(prepared) }
    }

    @Test fun automaticBackupsCannotBorrowAnUnlockedVaultWithoutExplicitAuthorization() = runBlocking {
        assertNotNull(ArchiveVaultSecurity.getActiveVaultSubKey()?.also { it.fill(0) })
        val destination = File(app.cacheDir, "unauthorized-auto.focusbackup")
        assertTrue(BackupRestoreManager.createAutomaticEncryptedBackup(app, Uri.fromFile(destination), password, null).isFailure)
        assertTrue(VaultPayloadEncryptor.isVaultEncrypted(notes.getNoteByIdSync(90210)!!))
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (!condition() && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        assertTrue("Timed out waiting for backup state", condition())
    }

    @Test fun ciphertextSurvivesPickerBackgroundingButPickerCancellationDiscardsIt() {
        val model = ManualBackupViewModel(app)
        try {
            model.prepare(password)
            assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
            await { model.state.value.phase != ManualBackupPhase.PREPARING }
            assertEquals(ManualBackupPhase.READY, model.state.value.phase)
            val id = model.chooseLocation()!!
            model.onBackground()
            assertEquals(ManualBackupPhase.PICKER, model.state.value.phase)
            assertTrue(folder.listFiles().orEmpty().isNotEmpty())
            model.save(id, null)
            assertEquals(ManualBackupPhase.MESSAGE, model.state.value.phase)
            val cancelled = model.state.value
            model.save(id, Uri.parse("content://stale-result/ignored"))
            assertEquals(cancelled, model.state.value)
            assertTrue(folder.listFiles().orEmpty().isEmpty())
            assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        } finally { model.cancel() }
    }

    @Test fun repeatedPreparationKeepsTheOriginalAuthorizationAndPreparedArchive() = runBlocking {
        val original = notes.getNoteByIdSync(90210)!!
        val model = ManualBackupViewModel(app)
        try {
            model.prepare(password)
            model.prepare("ignored-second-password")
            await { model.state.value.phase != ManualBackupPhase.PREPARING }
            val ready = model.state.value
            assertEquals(ready.message, ManualBackupPhase.READY, ready.phase)
            model.prepare("ignored-ready-password")
            assertEquals(ready, model.state.value)
            val files = folder.listFiles().orEmpty()
            assertEquals(1, files.size)
            assertTrue(BackupRestoreManager.verifyEncryptedBackup(app, Uri.fromFile(files.single()), password).isSuccess)
            val id = model.chooseLocation()!!
            val picker = model.state.value
            model.prepare("ignored-picker-password")
            assertEquals(picker, model.state.value)
            assertEquals(original, notes.getNoteByIdSync(90210))
            assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
            model.save(id, null)
            assertTrue(folder.listFiles().orEmpty().isEmpty())
        } finally { model.cancel() }
    }

    @Test fun aPickerResultAfterProcessStateLossCannotSaveOrReportSuccess() {
        val recreated = ManualBackupViewModel(app)
        recreated.save(UUID.randomUUID().toString(), Uri.parse("content://not-used/backup"))
        assertEquals(ManualBackupPhase.MESSAGE, recreated.state.value.phase)
        assertNull(recreated.state.value.metadata)
        recreated.cancel()
    }

    @Test fun savingThroughADocumentProviderVerifiesTheArchiveBeforeSuccessAndIgnoresDuplicateResults() = runBlocking {
        val destination = File(app.cacheDir, "manual-provider-destination.focusbackup")
        var writes = 0
        val provider = object : android.content.ContentProvider() {
            override fun onCreate() = true
            override fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor {
                if (mode.contains('w')) writes++
                return android.os.ParcelFileDescriptor.open(destination, android.os.ParcelFileDescriptor.parseMode(mode))
            }
            override fun getType(uri: Uri) = "application/octet-stream"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): android.database.Cursor? = null
            override fun insert(uri: Uri, values: android.content.ContentValues?): Uri? = null
            override fun update(uri: Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        provider.attachInfo(app, android.content.pm.ProviderInfo().apply { authority = "run.manual.tests" })
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("run.manual.tests", provider)
        val model = ManualBackupViewModel(app)
        try {
            model.prepare(password)
            await { model.state.value.phase != ManualBackupPhase.PREPARING }
            assertEquals(ManualBackupPhase.READY, model.state.value.phase)
            assertEquals(0L, app.getSharedPreferences("ayva_backup_verification", 0).getLong("last_verified_export_ms", 0))
            val id = model.chooseLocation()!!
            val uri = Uri.parse("content://run.manual.tests/backup")
            model.save(id, uri)
            await { model.state.value.phase != ManualBackupPhase.SAVING }
            assertEquals(model.state.value.message, ManualBackupPhase.SAVED, model.state.value.phase)
            assertTrue(app.getSharedPreferences("ayva_backup_verification", 0).getLong("last_verified_export_ms", 0) > 0)
            assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
            assertTrue(folder.listFiles().orEmpty().isEmpty())
            assertTrue(BackupRestoreManager.verifyEncryptedBackup(app, uri, password).isSuccess)
            val success = model.state.value
            model.save(id, uri)
            assertEquals(success, model.state.value)
            assertEquals(1, writes)
        } finally { model.cancel(); destination.delete() }
    }

    @Test fun anUnexpectedPreparationCancellationCannotLeaveTheScreenBusyOrKeepItsKey() {
        var keyReference: ByteArray? = null
        val model = ManualBackupViewModel(app) { _, _, key ->
            keyReference = key
            throw CancellationException("provider operation cancelled")
        }
        model.prepare(password)
        assertEquals(ManualBackupPhase.MESSAGE, model.state.value.phase)
        assertNotNull(keyReference)
        assertTrue(keyReference!!.all { it == 0.toByte() })
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        model.cancel()
    }

    @Test fun backgroundCancellationWipesTheOneTimeKeyAndDiscardsALatePreparationResult() {
        val release = CompletableDeferred<Unit>(); var keyReference: ByteArray? = null
        val file = File(folder.apply { mkdirs() }, "${UUID.randomUUID()}.enc")
        val model = ManualBackupViewModel(app) { _, _, key ->
            keyReference = key
            withContext(NonCancellable) {
                release.await()
                file.writeText("opaque encrypted fixture")
                PreparedManualBackup(UUID.randomUUID().toString(), file,
                    BackupRestoreManager.BackupMetadata(4, 1, "test", 1, 0, 0, 0, 0, 0),
                    file.inputStream().use { digestBackup(it) }, SystemClock.elapsedRealtime())
            }
        }
        model.prepare(password)
        assertNotNull(keyReference)
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        model.onBackground()
        release.complete(Unit)
        await { keyReference!!.all { it == 0.toByte() } }
        assertEquals(ManualBackupPhase.MESSAGE, model.state.value.phase)
        assertFalse(file.exists())
        assertNull(model.state.value.metadata)
        model.cancel()
    }
}
