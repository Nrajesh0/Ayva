package com.focusbyrj.app.util.backup

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.data.chat.*
import com.focusbyrj.app.data.note.*
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FocusApplication::class)
class RestoreIntegrityRegressionTest {
    private val app
        get() = ApplicationProvider.getApplicationContext<FocusApplication>()

    private val notes
        get() = NoteDatabase.getInstance(app).noteDao()

    private val pass = "synthetic-audit-password"

    @Before
    fun setup() = runBlocking {
        notes.deleteAllNotes()
        app.database.taskDao().deleteAllTasks()
        app.database.habitDao().deleteAllLogs()
        app.database.habitDao().deleteAllHabits()
        ArchiveVaultSecurity.lockVault()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        AyvaChatDatabase.getDatabase(app).chatDao().clearAllMessages()
        app.getSharedPreferences("productivity_library", 0).edit().clear().commit()
        RestoreNotice.acknowledge(app)
    }

    @After
    fun clean() {
        RestoreJournal.checkpointHook = null
    }

    private suspend fun archive() =
        File(app.cacheDir, "${UUID.randomUUID()}.enc").also {
            BackupRestoreManager.createEncryptedBackup(app, Uri.fromFile(it), pass).getOrThrow()
        }

    @Test
    fun importedChatCardsPointToTheImportedTask() = runBlocking {
        val old = app.database.taskDao().insertTask(Task(title = "Backed up task"))
        val chat = AyvaChatDatabase.getDatabase(app).chatDao()
        chat.insertMessages(
            listOf(
                AyvaChatMessageEntity(
                    id = "audit-card",
                    text = "Tasks",
                    isUser = false,
                    isTaskSummary = true,
                    taskSummaryJson =
                        JSONArray()
                            .put(JSONObject().put("id", old).put("title", "Backed up task"))
                            .toString(),
                )
            )
        )
        val file = archive()
        chat.clearAllMessages()
        BackupRestoreManager.restoreEncryptedBackup(
                app,
                Uri.fromFile(file),
                pass,
                cleanRestore = false,
            )
            .getOrThrow()
        val tasks = app.database.taskDao().getAllTasksList()
        assertEquals(2, tasks.size)
        val card = chat.getMessageById("audit-card")!!
        val linked = JSONArray(card.taskSummaryJson).getJSONObject(0).getLong("id")
        assertNotEquals(old, linked)
        assertEquals(tasks.single { it.id != old }.id, linked)
    }

    @Test
    fun orphanHabitLogsAreRejectedWithoutChangingLocalProgress() = runBlocking {
        val file = archive()
        val root =
            BackupRestoreManager.prepareRestore(app, Uri.fromFile(file), pass).getOrThrow().use {
                JSONObject(it.manifest.toString())
            }
        val local = app.database.habitDao().insertHabit(Habit(title = "Unrelated local habit"))
        root.put(
            "habitLogs",
            JSONArray()
                .put(
                    JSONObject()
                        .put("habitId", local)
                        .put("date", "2026-11-15")
                        .put("completedCount", 9)
                ),
        )
        val edited = File(app.cacheDir, "${UUID.randomUUID()}.enc")
        edited.outputStream().use { raw ->
            CryptoBackupEngine.openEncryptingStream(raw, pass.toCharArray()).use { enc ->
                java.util.zip.ZipOutputStream(enc).use { zip ->
                    zip.putNextEntry(java.util.zip.ZipEntry("data.json"))
                    zip.write(root.toString().toByteArray())
                    zip.closeEntry()
                }
            }
        }
        assertTrue(BackupRestoreManager.prepareRestore(app, Uri.fromFile(edited), pass).isFailure)
        assertTrue(app.database.habitDao().getAllLogsForHabitSync(local).isEmpty())
    }

    @Test
    fun lateFailureRemovesImportedFilesAndAllowsSafeRetry() = runBlocking {
        val folder = File(app.filesDir, "keep_images").apply { mkdirs() }
        val image = File(folder, "audit-${UUID.randomUUID()}.jpg")
        EncryptedMediaStorage.writeEncryptedBytes(image, "synthetic image bytes".toByteArray())
        val id =
            notes.insertNote(
                NoteEntity(
                    title = "Original",
                    imageUrisJson = JSONArray().put(image.absolutePath).toString(),
                )
            )
        val file = archive()
        val before = folder.listFiles()!!.map { it.canonicalPath }.toSet()
        RestoreJournal.checkpointHook = {
            if (it == "media") error("Synthetic failure after file installation")
        }
        val result =
            BackupRestoreManager.restoreEncryptedBackup(
                app,
                Uri.fromFile(file),
                pass,
                cleanRestore = true,
            )
        RestoreJournal.checkpointHook = null
        assertTrue(result.isFailure)
        assertEquals(
            image.absolutePath,
            JSONArray(notes.getNoteByIdSync(id)!!.imageUrisJson).getString(0),
        )
        val extra = folder.listFiles()!!.map { it.canonicalPath }.toSet() - before
        assertTrue(extra.isEmpty())
        assertEquals("recovered", RestoreNotice.lastOutcome(app))
        BackupRestoreManager.restoreEncryptedBackup(
                app,
                Uri.fromFile(file),
                pass,
                cleanRestore = true,
            )
            .getOrThrow()
        assertEquals(1, notes.getAllNotesList().size)
        assertEquals(
            "synthetic image bytes",
            EncryptedMediaStorage.readDecryptedBytes(
                    File(notes.getAllNotesList().single().getImageUris().single())
                )!!
                .decodeToString(),
        )
    }

    @Test fun processDeathBeforeCommitRecoversFilesAndRecords() = interrupted("before_commit")

    @Test fun processDeathAfterCommitKeepsFilesAndReportsCompletion() = interrupted("committed")

    private fun interrupted(phase: String) = runBlocking {
        val image = File(app.filesDir, "keep_images/audit-${UUID.randomUUID()}.jpg")
        EncryptedMediaStorage.writeEncryptedBytes(image, "complete bytes".toByteArray())
        val id =
            notes.insertNote(
                NoteEntity(
                    title = "Backed up",
                    imageUrisJson = JSONArray().put(image.path).toString(),
                )
            )
        val source = archive()
        notes.updateNote(notes.getNoteByIdSync(id)!!.copy(title = "Before restore"))
        val folder = image.parentFile!!
        val before = folder.listFiles()!!.map { it.path }.toSet()
        RestoreJournal.checkpointHook = { if (it == phase) throw SimulatedProcessDeath() }
        try {
            BackupRestoreManager.restoreEncryptedBackup(
                app,
                Uri.fromFile(source),
                pass,
                cleanRestore = true,
            )
            fail("Expected process death")
        } catch (_: SimulatedProcessDeath) {} finally {
            RestoreJournal.checkpointHook = null
        }
        assertTrue(MaintenanceGate.recoveryRequired)
        RestoreJournal.recover(app)
        RestoreNotice.onStartup(app)
        assertFalse(MaintenanceGate.recoveryRequired)
        val committed = phase == "committed"
        assertEquals(
            if (committed) "Backed up" else "Before restore",
            notes.getNoteByIdSync(id)!!.title,
        )
        assertEquals(
            if (committed) 1 else 0,
            (folder.listFiles()!!.map { it.path }.toSet() - before).size,
        )
        assertTrue(
            RestoreNotice.pending(app)!!
                .first
                .contains(if (committed) "completed" else "recovered", true)
        )
        RestoreJournal.recover(app) // Startup recovery is idempotent.
        assertEquals(1, notes.getAllNotesList().size)
        BackupRestoreManager.restoreEncryptedBackup(
                app,
                Uri.fromFile(source),
                pass,
                cleanRestore = true,
            )
            .getOrThrow()
        assertEquals(1, notes.getAllNotesList().size)
        assertEquals(
            "complete bytes",
            EncryptedMediaStorage.readDecryptedBytes(
                    File(notes.getAllNotesList().single().getImageUris().single())
                )!!
                .decodeToString(),
        )
    }

    private class SimulatedProcessDeath : Error("Synthetic process death")

    @Test
    fun mergePreservesLocalChatAndReportsTheSkippedMessage() = runBlocking {
        val chat = AyvaChatDatabase.getDatabase(app).chatDao()
        chat.insertMessages(
            listOf(AyvaChatMessageEntity(id = "conflict", text = "Older", isUser = false))
        )
        val source = archive()
        chat.insertMessages(
            listOf(AyvaChatMessageEntity(id = "conflict", text = "Newer local", isUser = false))
        )
        val result =
            BackupRestoreManager.restoreEncryptedBackup(
                    app,
                    Uri.fromFile(source),
                    pass,
                    cleanRestore = false,
                )
                .getOrThrow()
        assertEquals("Newer local", chat.getMessageById("conflict")!!.text)
        assertEquals(0, result.chatMessageCount)
        assertTrue(result.warnings.any { it.contains("chat") && it.contains("skipped") })
    }

    @Test
    fun missingRoutineHabitsAreReportedInsteadOfSilentlyDiscarded() = runBlocking {
        app.getSharedPreferences("productivity_library", 0)
            .edit()
            .putString(
                "chain_fixture",
                JSONObject()
                    .put("kind", "chain")
                    .put("name", "Routine")
                    .put("habits", JSONArray().put(987654321L))
                    .toString(),
            )
            .commit()
        val source = archive()
        val result =
            BackupRestoreManager.restoreEncryptedBackup(
                    app,
                    Uri.fromFile(source),
                    pass,
                    cleanRestore = true,
                )
                .getOrThrow()
        assertTrue(result.warnings.any { it.contains("routine") && it.contains("skipped") })
    }

    @Test
    fun malformedInteractiveCardCannotKeepNumericLinks() {
        val warnings = mutableListOf<String>()
        assertEquals("[]", RestoreImportPolicy.remapCards("not json", mapOf(1L to 2L), warnings))
        assertEquals(1, warnings.size)
        warnings.clear()
        assertEquals(
            "[]",
            RestoreImportPolicy.remapCards(
                JSONArray().put(JSONObject().put("id", 99)).toString(),
                mapOf(1L to 2L),
                warnings,
            ),
        )
        assertEquals(1, warnings.size)
    }

    @Test
    fun duplicateArchiveIdentitiesAreRejectedDuringPreflight() {
        val method =
            BackupRestoreManager::class
                .java
                .getDeclaredMethod("validateRestoreManifest", JSONObject::class.java)
                .apply { isAccessible = true }
        for ((name, key) in listOf("chatMessages" to "id", "drillSessions" to "sessionId")) {
            val root =
                JSONObject()
                    .put("version", 2)
                    .put(
                        name,
                        JSONArray()
                            .put(JSONObject().put(key, "same"))
                            .put(JSONObject().put(key, "same")),
                    )
            val error =
                assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
                    method.invoke(BackupRestoreManager, root)
                }
            assertTrue(error.cause!!.message!!.contains("Duplicate"))
        }
        val root =
            JSONObject()
                .put("version", 2)
                .put("habits", JSONArray().put(JSONObject().put("id", 1).put("title", "Habit")))
                .put(
                    "habitLogs",
                    JSONArray()
                        .put(JSONObject().put("id", 2).put("habitId", 1).put("date", "2026-11-15"))
                        .put(JSONObject().put("id", 2).put("habitId", 1).put("date", "2026-11-16")),
                )
        val error =
            assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
                method.invoke(BackupRestoreManager, root)
            }
        assertTrue(error.cause!!.message!!.contains("Duplicate habit log ID"))
    }

    @Test
    fun mergePreservesLocalDrillResultsAndReportsConflict() = runBlocking {
        val dao = com.focusbyrj.app.data.drill.DrillDatabase.getDatabase(app).drillSessionDao()
        dao.deleteAllSessions()
        val original =
            com.focusbyrj.app.data.drill.DrillSessionEntity(
                sessionId = "audit-drill",
                title = "Older",
                totalQuestions = 1,
                correctCount = 0,
                timeSpentSeconds = 1,
                xpEarned = 0,
                isBlitz = false,
                isClaimed = true,
                summaryJson = "{}",
            )
        dao.insertSession(original)
        val source = archive()
        dao.insertSession(original.copy(title = "Newer local", correctCount = 1))
        val result =
            BackupRestoreManager.restoreEncryptedBackup(
                    app,
                    Uri.fromFile(source),
                    pass,
                    cleanRestore = false,
                )
                .getOrThrow()
        assertEquals("Newer local", dao.getSessionById("audit-drill")!!.title)
        assertEquals(0, result.drillCount)
        assertTrue(result.warnings.any { it.contains("drill") && it.contains("skipped") })
    }

    @Test
    fun interruptedCheckingReportsNoRunningRestoreAndAcknowledgementPersists() {
        RestoreNotice.outcome(app, "checking")
        RestoreNotice.onStartup(app)
        assertTrue(RestoreNotice.pending(app)!!.second.contains("No restore is running"))
        RestoreNotice.acknowledge(app)
        RestoreNotice.onStartup(app)
        assertNull(RestoreNotice.pending(app))
    }

    @Test
    fun damagedOutcomeReportDoesNotPreventOpeningTheApp() {
        for (value in
            listOf(
                "not-json",
                JSONObject().put("pending", true).toString(),
                JSONObject()
                    .put("pending", true)
                    .put("title", JSONArray())
                    .put("message", JSONObject())
                    .toString(),
            )) {
            File(app.noBackupFilesDir, "restore_outcome.json").writeText(value)
            RestoreNotice.onStartup(app)
            assertTrue(RestoreNotice.pending(app)!!.first.contains("unavailable"))
            RestoreNotice.acknowledge(app)
        }
    }

    @Test
    fun foregroundCompletionDoesNotCreateASecondStartupDialogUntilRestart() {
        RestoreNotice.acknowledge(app)
        RestoreNotice.onStartup(app)
        RestoreNotice.outcome(app, "completed")
        assertNull(RestoreNotice.forStartup())
        RestoreNotice.onStartup(app) // A new application startup, not a configuration change.
        assertTrue(RestoreNotice.forStartup()!!.first.contains("completed"))
        RestoreNotice.acknowledge(app)
        assertNull(RestoreNotice.forStartup())
    }

    @Test
    fun journalPreparationFailureCannotRollBackEditsMadeAfterTheFailure() = runBlocking {
        val id = notes.insertNote(NoteEntity(title = "Current original"))
        val source = archive()
        RestoreJournal.checkpointHook = {
            if (it == "journal_written")
                throw java.io.IOException("Synthetic journal preparation failure")
        }
        val result =
            BackupRestoreManager.restoreEncryptedBackup(
                app,
                Uri.fromFile(source),
                pass,
                cleanRestore = true,
            )
        RestoreJournal.checkpointHook = null
        assertTrue(result.isFailure)
        assertFalse(MaintenanceGate.recoveryRequired)
        assertFalse(File(app.filesDir, "restore_before_state.enc").exists())
        notes.updateNote(notes.getNoteByIdSync(id)!!.copy(title = "Newer edit after failure"))
        RestoreJournal.recover(app)
        assertEquals("Newer edit after failure", notes.getNoteByIdSync(id)!!.title)
    }
}
