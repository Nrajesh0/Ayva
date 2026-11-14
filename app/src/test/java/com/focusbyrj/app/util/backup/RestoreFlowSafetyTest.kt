package com.focusbyrj.app.util.backup

import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.data.note.*
import com.focusbyrj.app.ui.screens.RestoreBackupViewModel
import com.focusbyrj.app.ui.screens.RestorePhase
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34], application = FocusApplication::class)
class RestoreFlowSafetyTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusApplication>()
    private val notes get() = NoteDatabase.getInstance(app).noteDao()
    private val password = "synthetic-restore-flow-password"
    private val pin = "729461"

    @Before fun setup() = runBlocking {
        notes.deleteAllNotes()
        app.database.taskDao().deleteAllTasks()
        app.database.habitDao().deleteAllLogs()
        app.database.habitDao().deleteAllHabits()
        app.database.scheduleDao().deleteAllSchedules()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        ArchiveVaultSecurity.lockVault()
    }
    @After fun cleanup() { RestoreJournal.checkpointHook = null; ArchiveVaultSecurity.lockVault() }
    private suspend fun archive(): File = File(app.cacheDir, "synthetic-restore.focusbackup").also {
        BackupRestoreManager.createEncryptedBackup(app, Uri.fromFile(it), password).getOrThrow()
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (!condition() && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        assertTrue("Timed out waiting for restore state", condition())
    }

    @Test fun repeatedPortableRestoresWithLongOriginalNamesKeepAttachmentNamesBoundedAndBytesIntact() = runBlocking {
        val bytes = "Synthetic original attachment bytes".toByteArray()
        val original = File(app.filesDir, "keep_images/${"x".repeat(220)}.jpg")
        EncryptedMediaStorage.writeEncryptedBytes(original, bytes)
        notes.insertNote(NoteEntity(title = "Long attachment", imageUrisJson = JSONArray(listOf(original.path)).toString()))
        repeat(3) {
            val file = archive()
            BackupRestoreManager.restoreEncryptedBackup(app, Uri.fromFile(file), password, cleanRestore = true).getOrThrow()
            val restored = File(JSONArray(notes.getAllNotesList().single().imageUrisJson).getString(0))
            assertTrue("Restore must not accumulate the old basename", restored.name.length < 80)
            assertArrayEquals(bytes, EncryptedMediaStorage.readDecryptedBytes(restored))
        }
    }

    @Test fun emptyChatSnapshotClearsCurrentChatsOnlyInReplacementMode() = runBlocking {
        val chat = com.focusbyrj.app.data.chat.AyvaChatDatabase.getDatabase(app).chatDao()
        chat.clearAllMessages()
        var verificationOpenings = 0
        val file = kotlinx.coroutines.withContext(BackupProgress { if (it == "Checking backup password") verificationOpenings++ }) { archive() }
        assertEquals("One full verifier must replace the two previous passes", 1, verificationOpenings)
        assertEquals(com.focusbyrj.app.BuildConfig.VERSION_NAME,
            BackupRestoreManager.prepareRestore(app, Uri.fromFile(file), password).getOrThrow().use { it.manifest!!.getString("appVersion") })
        chat.insertMessages(listOf(com.focusbyrj.app.data.chat.AyvaChatMessageEntity(id = "later", text = "Keep for merge", isUser = true)))
        BackupRestoreManager.restoreEncryptedBackup(app, Uri.fromFile(file), password, cleanRestore = false).getOrThrow()
        assertEquals(1, chat.getAllMessagesSync().size)
        repeat(2) {
            BackupRestoreManager.restoreEncryptedBackup(app, Uri.fromFile(file), password, cleanRestore = true).getOrThrow()
            assertTrue(chat.getAllMessagesSync().isEmpty())
        }
    }

    @Test fun snapshotRestoreRepeatedOnTheSameDataDoesNotDuplicateRecordsOrRelationships() = runBlocking {
        val note = notes.insertNote(NoteEntity(title = "Original note"))
        val task = app.database.taskDao().insertTask(Task(title = "Original task"))
        val habit = app.database.habitDao().insertHabit(Habit(title = "Original habit", isReminderEnabled = false))
        app.database.habitDao().insertOrUpdateLog(HabitLog(habitId = habit, date = "2026-11-07", completedCount = 1, targetCount = 1))
        val file = archive()
        notes.insertNote(NoteEntity(title = "Later note"))
        repeat(2) {
            val prepared = BackupRestoreManager.prepareRestore(app, Uri.fromFile(file), password).getOrThrow()
            BackupRestoreManager.applyPreparedRestore(app, prepared).getOrThrow()
            assertEquals(note, notes.getAllNotesList().single().id)
            assertEquals(task, app.database.taskDao().getAllTasksList().single().id)
            assertEquals(habit, app.database.habitDao().getAllHabitsSync().single().id)
            assertEquals(habit, app.database.habitDao().getAllLogsSync().single().habitId)
        }
    }

    @Test fun vaultPinCanBeSuppliedHereWithoutDisablingVaultOrReadingTheArchiveTwice() = runBlocking {
        assertTrue(ArchiveVaultSecurity.setPasscode(app, pin))
        val key = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        val id = try { notes.insertNote(VaultPayloadEncryptor.encryptNotePayload(NoteEntity(title = "Private original", isArchived = true), key)) }
            finally { key.fill(0) }
        val file = archive()
        ArchiveVaultSecurity.lockVault()
        val prepared = BackupRestoreManager.prepareRestore(app, Uri.fromFile(file), password).getOrThrow()
        assertTrue(file.delete()) // All subsequent PIN/retry work must use the authenticated preparation.
        val before = notes.getAllNotesList()
        val snapshots = DataSafetyManager.listAvailableSnapshots(app).map { it.absolutePath }.toSet()
        var snapshotsWritten = 0
        RestoreJournal.checkpointHook = { if (it == "prepared") snapshotsWritten++ }
        assertTrue(BackupRestoreManager.applyPreparedRestore(app, prepared).exceptionOrNull() is BackupRestoreManager.RequiresVaultUnlockException)
        assertTrue(BackupRestoreManager.applyPreparedRestore(app, prepared, vaultPin = "111111").isFailure)
        assertEquals(0, snapshotsWritten)
        assertEquals(snapshots, DataSafetyManager.listAvailableSnapshots(app).map { it.absolutePath }.toSet())
        assertEquals(before, notes.getAllNotesList())
        RestoreJournal.checkpointHook = { if (it == "notes") assertNull("The global vault must remain locked", ArchiveVaultSecurity.getActiveVaultSubKey()) }
        BackupRestoreManager.applyPreparedRestore(app, prepared, vaultPin = pin).getOrThrow()
        assertEquals(ArchiveVaultSecurity.VaultStatus.ENABLED, ArchiveVaultSecurity.getVaultStatus(app))
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        assertEquals(id, notes.getAllNotesList().single().id)
        assertTrue(VaultPayloadEncryptor.isVaultEncrypted(notes.getAllNotesList().single()))
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app, pin) is ArchiveVaultSecurity.VerifyResult.Success)
        val restoredKey = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        try { assertEquals("Private original", VaultPayloadEncryptor.decryptNotePayload(notes.getAllNotesList().single(), restoredKey).title) }
        finally { restoredKey.fill(0) }
    }

    @Test fun newVaultSetupReusesTheVerifiedPreparationAndProtectsRestoredPrivateNotes() = runBlocking {
        assertTrue(ArchiveVaultSecurity.setPasscode(app, pin))
        val key = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        try { notes.insertNote(VaultPayloadEncryptor.encryptNotePayload(NoteEntity(title = "New-device private", isArchived = true), key)) }
        finally { key.fill(0) }
        val file = archive()
        notes.deleteAllNotes()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        ArchiveVaultSecurity.lockVault()
        val prepared = BackupRestoreManager.prepareRestore(app, Uri.fromFile(file), password).getOrThrow()
        file.delete()
        assertTrue(BackupRestoreManager.applyPreparedRestore(app, prepared).exceptionOrNull() is BackupRestoreManager.RequiresVaultSetupException)
        assertTrue(notes.getAllNotesList().isEmpty())
        BackupRestoreManager.applyPreparedRestore(app, prepared, vaultPin = "418632").getOrThrow()
        assertTrue(VaultPayloadEncryptor.isVaultEncrypted(notes.getAllNotesList().single()))
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "418632") is ArchiveVaultSecurity.VerifyResult.Success)
    }

    @Test fun aFailureAfterMutationRollsBackThroughTheDurableJournalAndRetainsOriginalMedia() = runBlocking {
        notes.insertNote(NoteEntity(id = 31, title = "Backup note"))
        app.database.taskDao().insertTask(Task(id = 31, title = "Backup task"))
        val file = archive()
        notes.deleteAllNotes(); app.database.taskDao().deleteAllTasks()
        val image = File(app.filesDir, "keep_images/live.jpg")
        EncryptedMediaStorage.writeEncryptedBytes(image, "Original attachment".toByteArray())
        val original = NoteEntity(id = 41, title = "Current note", imageUrisJson = JSONArray(listOf(image.path)).toString())
        notes.insertNote(original)
        val task = Task(id = 41, title = "Current task")
        app.database.taskDao().insertTask(task)
        val bytes = image.readBytes()
        RestoreJournal.checkpointHook = { if (it == "focus") throw java.io.IOException("Synthetic interrupted apply") }
        val result = BackupRestoreManager.restoreEncryptedBackup(app, Uri.fromFile(file), password, cleanRestore = true)
        assertTrue(result.isFailure)
        assertEquals(listOf(original), notes.getAllNotesList())
        assertEquals(listOf(task), app.database.taskDao().getAllTasksList())
        assertArrayEquals(bytes, image.readBytes())
        assertFalse(MaintenanceGate.recoveryRequired)
        assertFalse(File(app.filesDir, "restore_before_state.enc").exists())
    }

    @Test fun stagedAttachmentTamperingFailsBeforeReplacingLiveData() = runBlocking {
        val image = File(app.filesDir, "keep_images/source.jpg")
        EncryptedMediaStorage.writeEncryptedBytes(image, "Backed up attachment".toByteArray())
        notes.insertNote(NoteEntity(title = "Backup image", imageUrisJson = JSONArray(listOf(image.path)).toString()))
        val file = archive()
        val prepared = BackupRestoreManager.prepareRestore(app, Uri.fromFile(file), password).getOrThrow()
        val staged = File(prepared.stagingDir, "keep_images").listFiles()!!.single()
        val damaged = staged.readBytes(); damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte(); staged.writeBytes(damaged)
        notes.deleteAllNotes(); val current = NoteEntity(id = 51, title = "Keep current")
        notes.insertNote(current)
        assertTrue(BackupRestoreManager.applyPreparedRestore(app, prepared).isFailure)
        assertEquals(listOf(current), notes.getAllNotesList())
        assertEquals("Backed up attachment", EncryptedMediaStorage.readDecryptedBytes(image)!!.decodeToString())
    }

    @Test fun reviewRequiresConfirmationAndBackgroundingDiscardsPreparationWithoutChangingData() {
        val file = runBlocking { notes.insertNote(NoteEntity(id = 61, title = "Snapshot")); archive().also { notes.insertNote(NoteEntity(id = 62, title = "Later")) } }
        val model = RestoreBackupViewModel(app)
        try {
            model.prepare(Uri.fromFile(file), password, true)
            await { model.state.value.phase != RestorePhase.CHECKING }
            assertEquals(RestorePhase.REVIEW, model.state.value.phase)
            runBlocking { assertEquals(2, notes.getAllNotesList().size) }
            model.onBackground()
            assertEquals(RestorePhase.ERROR, model.state.value.phase)
            assertTrue(app.cacheDir.listFiles().orEmpty().none { it.name.startsWith("restore_media_stage_") })
            runBlocking { assertEquals(2, notes.getAllNotesList().size) }
            model.prepare(Uri.fromFile(file), password, true)
            await { model.state.value.phase != RestorePhase.CHECKING }
            assertEquals(RestorePhase.REVIEW, model.state.value.phase)
            model.acceptReview()
            await { model.state.value.phase != RestorePhase.APPLYING }
            assertEquals(model.state.value.message, RestorePhase.SUCCESS, model.state.value.phase)
            runBlocking { assertEquals(listOf(61L), notes.getAllNotesList().map { it.id }) }
        } finally { model.cancel() }
    }
    @Test fun unrelatedDamagedTextFormattingCannotHideOrDropBackedUpBlockAttachments() = runBlocking {
        val image = File(app.filesDir, "keep_images/block-media.bin")
        EncryptedMediaStorage.writeEncryptedBytes(image, "Block attachment survives".toByteArray())
        val root = org.json.JSONObject().put("customMetadata", "retain me").put("blocks", JSONArray()
            .put(org.json.JSONObject().put("type", "text").put("text", "Original text").put("spans", JSONArray().put(org.json.JSONObject.NULL)))
            .put(org.json.JSONObject().put("type", "image").put("uri", image.path).put("caption", "Original caption")))
        val prefix = com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager.BLOCKS_PREFIX
        val suffix = com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager.BLOCKS_SUFFIX
        notes.insertNote(NoteEntity(title = "Block document", content = "Leading text" + prefix + root + suffix + "Trailing text"))
        val file = archive()
        assertTrue(image.delete()) // Simulate restoring on a phone without the original attachment path.
        BackupRestoreManager.restoreEncryptedBackup(app, Uri.fromFile(file), password, cleanRestore = true).getOrThrow()
        val content = notes.getAllNotesList().single().content
        assertTrue(content.startsWith("Leading text")); assertTrue(content.endsWith("Trailing text"))
        val restored = org.json.JSONObject(content.substringAfter(prefix).substringBefore(suffix))
        assertEquals("retain me", restored.getString("customMetadata"))
        assertTrue(restored.getJSONArray("blocks").getJSONObject(0).getJSONArray("spans").isNull(0))
        val block = restored.getJSONArray("blocks").getJSONObject(1)
        assertEquals("Original caption", block.getString("caption"))
        val restoredFile = File(block.getString("uri"))
        assertNotEquals(image.path, restoredFile.path)
        assertEquals("Block attachment survives", EncryptedMediaStorage.readDecryptedBytes(restoredFile)!!.decodeToString())
    }

    @Test fun aMissingBackupRequestsTheFileAgainWithoutMisdiagnosingTheRecoveryKey() {
        val model = RestoreBackupViewModel(app)
        try {
            model.prepare(Uri.fromFile(File(app.cacheDir, "does-not-exist.focusbackup")), password, true)
            await { model.state.value.phase != RestorePhase.CHECKING }
            assertEquals(RestorePhase.ERROR, model.state.value.phase)
            assertTrue(model.state.value.needsBackupFile)
            assertFalse(model.state.value.needsRecoveryFile)
            assertFalse(File(app.filesDir, "restore_before_state.enc").exists())
        } finally { model.cancel() }
    }

    @Test fun aSavedKeyNeedsOnePinAndReusesItsScopedAuthorizationThroughReview() {
        val store = AutomaticBackupStore(app, TestBackupWrapper())
        val file = runBlocking {
            assertTrue(ArchiveVaultSecurity.setPasscode(app, pin))
            BackupRecoveryKey.generate().use { store.install(it) }
            val key = ArchiveVaultSecurity.getActiveVaultSubKey()!!
            try {
                store.authorizeVault(key)
                notes.insertNote(VaultPayloadEncryptor.encryptNotePayload(NoteEntity(title = "Saved-key private", isArchived = true), key))
                File(app.cacheDir, "saved-key-current.focusbackup").also {
                    store.key().use { recovery -> BackupRestoreManager.createAutomaticEncryptedBackup(app, Uri.fromFile(it), recovery.credential(), key).getOrThrow() }
                }
            } finally { key.fill(0) }
        }
        val model = RestoreBackupViewModel(app) { store }
        try {
            model.prepare(Uri.fromFile(file), null, true)
            assertEquals(RestorePhase.UNLOCK_SAVED_KEY, model.state.value.phase)
            assertTrue(ArchiveVaultSecurity.verifyPasscode(app, pin) is ArchiveVaultSecurity.VerifyResult.Success)
            model.authorizeSavedKey()
            assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
            await { model.state.value.phase != RestorePhase.CHECKING }
            assertEquals(model.state.value.message, RestorePhase.REVIEW, model.state.value.phase)
            assertTrue(file.delete())
            model.acceptReview()
            assertEquals("A second PIN prompt must not be needed", RestorePhase.APPLYING, model.state.value.phase)
            await { model.state.value.phase != RestorePhase.APPLYING }
            assertEquals(model.state.value.message, RestorePhase.SUCCESS, model.state.value.phase)
            assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
            runBlocking { assertTrue(VaultPayloadEncryptor.isVaultEncrypted(notes.getAllNotesList().single())) }
        } finally { model.cancel() }
    }

    @Test fun aNewVaultPinCannotUseTheSavedKeyForAnEarlierVaultButAnExternalKeyStillWorks() {
        val store = AutomaticBackupStore(app, TestBackupWrapper())
        var recoveryCredential = ""
        val file = runBlocking {
            assertTrue(ArchiveVaultSecurity.setPasscode(app, pin))
            BackupRecoveryKey.generate().use { store.install(it); recoveryCredential = it.credential() }
            val key = ArchiveVaultSecurity.getActiveVaultSubKey()!!
            try {
                store.authorizeVault(key)
                notes.insertNote(VaultPayloadEncryptor.encryptNotePayload(NoteEntity(title = "Earlier private data", isArchived = true), key))
                File(app.cacheDir, "earlier-vault.focusbackup").also {
                    BackupRestoreManager.createAutomaticEncryptedBackup(app, Uri.fromFile(it), recoveryCredential, key).getOrThrow()
                }
            } finally { key.fill(0) }
        }
        val oldProtectedKey = app.getSharedPreferences("automatic_full_backups_v1", 0).getString("wrapped_key", null)
        assertTrue(ArchiveVaultSecurity.disablePasscode(app))
        runBlocking { notes.deleteAllNotes() }
        val newPin = "418632"
        assertTrue(ArchiveVaultSecurity.setPasscode(app, newPin))
        val newKey = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        val current = VaultPayloadEncryptor.encryptNotePayload(NoteEntity(id = 987, title = "Current private data", isArchived = true), newKey)
        runBlocking { notes.insertNote(current) }
        val model = RestoreBackupViewModel(app) { store }
        try {
            model.prepare(Uri.fromFile(file), null, true)
            assertEquals(RestorePhase.UNLOCK_SAVED_KEY, model.state.value.phase)
            assertTrue(ArchiveVaultSecurity.verifyPasscode(app, newPin) is ArchiveVaultSecurity.VerifyResult.Success)
            model.authorizeSavedKey()
            await { model.state.value.phase != RestorePhase.CHECKING }
            assertEquals(RestorePhase.ERROR, model.state.value.phase)
            assertTrue(model.state.value.needsRecoveryFile)
            assertTrue(model.state.value.message.orEmpty().contains("Vault protection changed"))
            runBlocking { assertEquals(listOf(current), notes.getAllNotesList()) }
            assertEquals(oldProtectedKey, app.getSharedPreferences("automatic_full_backups_v1", 0).getString("wrapped_key", null))
            // Supplying the actual exported recovery credential is an independent, valid capability.
            model.prepare(Uri.fromFile(file), recoveryCredential, true)
            await { model.state.value.phase != RestorePhase.CHECKING }
            assertEquals(RestorePhase.REVIEW, model.state.value.phase)
            model.acceptReview()
            assertEquals(RestorePhase.UNLOCK, model.state.value.phase)
            model.confirm(newPin)
            await { model.state.value.phase != RestorePhase.APPLYING }
            assertEquals(model.state.value.message, RestorePhase.SUCCESS, model.state.value.phase)
            runBlocking { assertEquals("Earlier private data", VaultPayloadEncryptor.decryptNotePayload(notes.getAllNotesList().single(), newKey).title) }
            assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        } finally { model.cancel(); newKey.fill(0) }
    }

    @Test fun disablingTheVaultDoesNotBypassTheSavedKeysEarlierPrivateBinding() {
        val store = AutomaticBackupStore(app, TestBackupWrapper())
        assertTrue(ArchiveVaultSecurity.setPasscode(app, pin))
        BackupRecoveryKey.generate().use { store.install(it) }
        val key = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        try { store.authorizeVault(key) } finally { key.fill(0) }
        assertTrue(ArchiveVaultSecurity.disablePasscode(app))
        val model = RestoreBackupViewModel(app) { store }
        try {
            model.prepare(Uri.fromFile(File(app.cacheDir, "missing-earlier-vault.focusbackup")), null, true)
            await { model.state.value.phase != RestorePhase.CHECKING }
            assertEquals(RestorePhase.ERROR, model.state.value.phase)
            assertTrue(model.state.value.needsRecoveryFile)
            assertFalse("Origin binding must reject before reading the source", model.state.value.needsBackupFile)
            assertTrue(model.state.value.message.orEmpty().contains("Vault protection changed"))
        } finally { model.cancel() }
    }

}
