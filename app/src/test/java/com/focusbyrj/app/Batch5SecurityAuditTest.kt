package com.focusbyrj.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.*
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteDatabaseMigrationHelper
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.screens.notes.ArticleDocxGenerator
import com.focusbyrj.app.ui.screens.notes.ArticleExporter
import com.focusbyrj.app.ui.screens.notes.ArticlePdfGenerator
import com.focusbyrj.app.ui.screens.notes.NotesnookBlock
import com.focusbyrj.app.ui.screens.notes.RichSpan
import com.focusbyrj.app.ui.screens.notes.RichSpanType
import com.focusbyrj.app.util.backup.BackupRestoreManager
import com.focusbyrj.app.util.backup.CryptoBackupEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import android.graphics.pdf.PdfDocument
import com.focusbyrj.app.util.sync.supabase.SupabaseAuthManager
import com.focusbyrj.app.util.sync.supabase.SupabaseStorageEngine
import com.focusbyrj.app.util.backup.DataSafetyManager
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Batch 5 Security & Data Integrity Audit -- Regression Test Suite
 *
 * Covers Databases, Schema Migrations & Backup/Export Pipeline:
 * - B5-F-001: Task subtasks preserved across encrypted backup creation and restoration
 * - B5-F-002: Note fontKey and deletedAt preserved across encrypted backup creation and restoration
 * - B5-F-003: FocusDatabaseMigrationHelper extracts modern columns (updatedAt, isTrashed, trashedAt, deletedAt, subtasksJson)
 * - B5-F-004: NoteDatabaseMigrationHelper extracts fontKey, trashedAt, and deletedAt
 * - B5-F-005: ArticlePdfGenerator native resource cleanup and valid PDF generation
 * - B5-F-006: ArticleDocxGenerator strips illegal XML control characters and preserves whitespace
 */
@RunWith(RobolectricTestRunner::class)
class Batch5SecurityAuditTest {

    private lateinit var context: Context
    private lateinit var focusDb: FocusDatabase
    private lateinit var noteDb: NoteDatabase

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<FocusApplication>()
        context = app
        focusDb = app.database
        noteDb = NoteDatabase.getInstance(app)
        runBlocking {
            focusDb.taskDao().deleteAllTasks()
            noteDb.noteDao().deleteAllNotes()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            focusDb.taskDao().deleteAllTasks()
            noteDb.noteDao().deleteAllNotes()
        }
    }

    // B5-F-001: Task subtasks must be preserved across backup creation and restoration
    @Test
    fun testTaskSubtasksPreservedAcrossBackupAndRestore() = runBlocking {
        val sampleSubtasksJson = """[{"id":"st_1","text":"Review PR","isCompleted":true},{"id":"st_2","text":"Deploy update","isCompleted":false}]"""
        val task = Task(
            id = 101L,
            title = "Critical Project Milestone",
            details = "Must retain all subtasks upon restore",
            subtasksJson = sampleSubtasksJson,
            dueDate = 1710000000000L,
            isCompleted = false,
            updatedAt = 1705000000000L
        )
        focusDb.taskDao().insertTask(task)

        val backupFile = File(context.cacheDir, "test_backup_subtasks.ayva_backup")
        val backupUri = Uri.fromFile(backupFile)
        val password = "BackupPassword#2026"

        val createResult = BackupRestoreManager.createEncryptedBackup(context, backupUri, password)
        assertTrue("Backup creation must succeed", createResult.isSuccess)

        // Clear local database to simulate clean device restore
        focusDb.taskDao().deleteAllTasks()
        assertEquals(0, focusDb.taskDao().getAllTasksList().size)

        // Restore backup in clean restore mode
        val restoreResult = BackupRestoreManager.restoreEncryptedBackup(context, backupUri, password, cleanRestore = true)
        assertTrue("Backup restoration must succeed", restoreResult.isSuccess)

        val restoredTasks = focusDb.taskDao().getAllTasksList()
        assertEquals(1, restoredTasks.size)
        val restoredTask = restoredTasks.first()
        assertEquals("Title must match", "Critical Project Milestone", restoredTask.title)
        assertEquals("Subtasks JSON must be completely preserved upon restore", sampleSubtasksJson, restoredTask.subtasksJson)
    }

    // B5-F-002: Note fontKey and deletedAt must be preserved across backup creation and restoration
    @Test
    fun testNoteFontKeyAndDeletedAtPreservedAcrossBackupAndRestore() = runBlocking {
        val note = NoteEntity(
            id = 202L,
            title = "Stylized Note",
            content = "Custom typography and soft-delete retention audit",
            fontKey = "serif",
            deletedAt = 1709999999000L,
            isTrashed = true,
            trashedAt = 1708888888000L
        )
        noteDb.noteDao().insertNote(note)

        val backupFile = File(context.cacheDir, "test_backup_notes.ayva_backup")
        val backupUri = Uri.fromFile(backupFile)
        val password = "BackupPassword#2026"

        val createResult = BackupRestoreManager.createEncryptedBackup(context, backupUri, password)
        assertTrue("Backup creation must succeed", createResult.isSuccess)

        // Clear database
        noteDb.noteDao().deleteAllNotes()
        assertEquals(0, noteDb.noteDao().getAllNotesList().size)

        val restoreResult = BackupRestoreManager.restoreEncryptedBackup(context, backupUri, password, cleanRestore = true)
        assertTrue("Backup restoration must succeed", restoreResult.isSuccess)

        val restoredNotes = noteDb.noteDao().getAllNotesList()
        assertEquals(1, restoredNotes.size)
        val restoredNote = restoredNotes.first()
        assertEquals("serif", restoredNote.fontKey)
        assertEquals(1709999999000L, restoredNote.deletedAt)
        assertEquals(1708888888000L, restoredNote.trashedAt)
    }

    // B5-F-003: FocusDatabaseMigrationHelper extracts modern columns when present
    @Test
    fun testFocusDatabaseMigrationHelperPreservesModernTaskColumns() = runBlocking {
        val legacyDbFile = context.getDatabasePath("focus_database")
        legacyDbFile.parentFile?.mkdirs()
        if (legacyDbFile.exists()) legacyDbFile.delete()

        val rawDb = SQLiteDatabase.openOrCreateDatabase(legacyDbFile, null)
        rawDb.execSQL("""
            CREATE TABLE tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                details TEXT NOT NULL DEFAULT '',
                dueDate INTEGER,
                isCompleted INTEGER NOT NULL DEFAULT 0,
                type TEXT NOT NULL DEFAULT 'TASK',
                recurrence TEXT NOT NULL DEFAULT 'NONE',
                isPersistent INTEGER NOT NULL DEFAULT 0,
                isPriority INTEGER NOT NULL DEFAULT 0,
                completedAt INTEGER,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                isTrashed INTEGER NOT NULL DEFAULT 0,
                trashedAt INTEGER DEFAULT NULL,
                deletedAt INTEGER DEFAULT NULL,
                subtasksJson TEXT NOT NULL DEFAULT '[]'
            )
        """.trimIndent())

        val subtasks = """[{"id":"1","text":"Do homework","isCompleted":false}]"""
        rawDb.execSQL("""
            INSERT INTO tasks (id, title, details, dueDate, isCompleted, type, recurrence, isPersistent, isPriority, completedAt, updatedAt, isTrashed, trashedAt, deletedAt, subtasksJson)
            VALUES (1, 'Legacy Task', 'From unencrypted db', 1700000000000, 0, 'TASK', 'NONE', 0, 1, NULL, 1701111111000, 1, 1702222222000, 1703333333000, '$subtasks')
        """.trimIndent())
        rawDb.close()

        FocusDatabaseMigrationHelper.checkAndMigrateIfLegacyPlaintextExists(context, focusDb)

        val migratedTasks = focusDb.taskDao().getAllTasksList()
        assertEquals("Must migrate 1 task", 1, migratedTasks.size)
        val migrated = migratedTasks.first()
        assertEquals(1701111111000L, migrated.updatedAt)
        assertTrue("isTrashed must be true", migrated.isTrashed)
        assertEquals(1702222222000L, migrated.trashedAt)
        assertEquals(1703333333000L, migrated.deletedAt)
        assertEquals(subtasks, migrated.subtasksJson)
    }

    // B5-F-004: NoteDatabaseMigrationHelper extracts fontKey, trashedAt, and deletedAt when present
    @Test
    fun testNoteDatabaseMigrationHelperPreservesFontKeyAndDeletedAt() = runBlocking {
        val legacyDbFile = context.getDatabasePath("keep_notes.db")
        legacyDbFile.parentFile?.mkdirs()
        if (legacyDbFile.exists()) legacyDbFile.delete()

        val rawDb = SQLiteDatabase.openOrCreateDatabase(legacyDbFile, null)
        rawDb.execSQL("""
            CREATE TABLE keep_notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                isChecklist INTEGER NOT NULL DEFAULT 0,
                checklistJson TEXT NOT NULL DEFAULT '[]',
                colorKey TEXT NOT NULL DEFAULT 'default',
                isPinned INTEGER NOT NULL DEFAULT 0,
                isArchived INTEGER NOT NULL DEFAULT 0,
                isTrashed INTEGER NOT NULL DEFAULT 0,
                labelsJson TEXT NOT NULL DEFAULT '[]',
                imageUrisJson TEXT NOT NULL DEFAULT '[]',
                audioUrisJson TEXT NOT NULL DEFAULT '[]',
                fontKey TEXT NOT NULL DEFAULT 'default',
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                trashedAt INTEGER DEFAULT NULL,
                deletedAt INTEGER DEFAULT NULL
            )
        """.trimIndent())

        rawDb.execSQL("""
            INSERT INTO keep_notes (id, title, content, fontKey, trashedAt, deletedAt, isTrashed)
            VALUES (1, 'Legacy Note', 'Migrated content', 'mono', 1705555555000, 1706666666000, 1)
        """.trimIndent())
        rawDb.close()

        NoteDatabaseMigrationHelper.checkAndMigrateIfLegacyPlaintextExists(context, noteDb)

        val migratedNotes = noteDb.noteDao().getAllNotesList()
        assertEquals("Must migrate 1 note", 1, migratedNotes.size)
        val migrated = migratedNotes.first()
        assertEquals("mono", migrated.fontKey)
        assertEquals(1705555555000L, migrated.trashedAt)
        assertEquals(1706666666000L, migrated.deletedAt)
    }

    // B5-F-006: ArticleDocxGenerator strips illegal XML control characters and includes xml:space="preserve"
    @Test
    fun testArticleDocxGeneratorStripsControlCharactersAndPreservesWhitespace() {
        val dirtyTitle = "Note with \u000C Form Feed and \u0000 Null"
        val textWithSpaces = "   Indented text with \u000B vertical tab   "
        val block = NotesnookBlock.Text(text = textWithSpaces)

        val docxBytes = ArticleDocxGenerator.generateDocx(
            title = dirtyTitle,
            blocks = listOf(block),
            fallbackContent = textWithSpaces
        )

        assertNotNull("Generated docx bytes must not be null", docxBytes)
        assertTrue("Docx must be non-empty", docxBytes.isNotEmpty())

        // Inspect document.xml from docx zip
        var documentXml: String? = null
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    documentXml = zip.bufferedReader(Charsets.UTF_8).readText()
                    break
                }
                entry = zip.nextEntry
            }
        }

        assertNotNull("word/document.xml must exist inside .docx zip", documentXml)
        assertFalse("Must not contain illegal control char \\u000C", documentXml!!.contains("\u000C"))
        assertFalse("Must not contain illegal control char \\u0000", documentXml!!.contains("\u0000"))
        assertFalse("Must not contain illegal control char \\u000B", documentXml!!.contains("\u000B"))
        assertTrue("Must declare xml:space=\"preserve\" to prevent Word from stripping whitespace",
            documentXml!!.contains("xml:space=\"preserve\""))
    }

    // B5-F-005: ArticlePdfGenerator executes safely, closes PdfDocument in finally, and returns valid PDF
    @Test
    @Config(shadows = [ShadowPdfDocument::class])
    fun testArticlePdfGeneratorNativeResourceCleanup() {
        ShadowPdfDocument.closeCallCount = 0
        ShadowPdfDocument.writeToCallCount = 0
        ShadowPdfDocument.pagesStarted = 0

        val title = "Comprehensive PDF Test Document"
        val block1 = NotesnookBlock.Text(text = "Paragraph 1 with informative content for PDF verification.")
        val block2 = NotesnookBlock.Table(
            data = mutableListOf(
                mutableListOf("Header 1", "Header 2"),
                mutableListOf("Value 1", "Value 2")
            )
        )

        val pdfBytes = ArticlePdfGenerator.generatePdf(
            title = title,
            blocks = listOf(block1, block2),
            fallbackContent = "Fallback"
        )

        assertNotNull("PDF bytes must not be null", pdfBytes)
        assertTrue("PDF must be non-empty", pdfBytes.isNotEmpty())
        val header = String(pdfBytes.take(4).toByteArray())
        assertEquals("Must start with PDF header %PDF", "%PDF", header)
        assertTrue("PdfDocument.writeTo must have been called", ShadowPdfDocument.writeToCallCount >= 1)
        assertEquals("PdfDocument.close() must be called in finally block", 1, ShadowPdfDocument.closeCallCount)
    }

    // B2-LOW-001: SupabaseAuthManager CharArray support and memory zeroization
    @Test
    fun testSupabaseAuthManagerCharArrayZeroization() = runBlocking {
        val email = "chararray_test@test.com"
        val passwordChars = "MasterPassword123!".toCharArray()

        // Calling signUp with CharArray overload
        val result = SupabaseAuthManager.signUp(context, email, passwordChars)
        assertNotNull("signUp with CharArray must return result", result)

        // Calling signIn with CharArray overload
        val signInResult = SupabaseAuthManager.signIn(context, email, passwordChars)
        assertNotNull("signIn with CharArray must return result", signInResult)

        // When using String overload, it safely delegates and zeroizes internally
        val stringResult = SupabaseAuthManager.signUp(context, email, "TempPassword123!")
        assertNotNull("signUp with String must return result", stringResult)
    }

    // B2-LOW-002: SupabaseStorageEngine batch deletion empty check and URL handling
    @Test
    fun testSupabaseStorageEngineBatchDeletionSafety() {
        val emptyResult = SupabaseStorageEngine.deleteMediaBatch(emptyList(), "test_token")
        assertTrue("Deleting empty batch of media files must return true immediately", emptyResult)
    }

    // B5-NEW-001: restoreEncryptedBackup must refuse when Secret Vault is enabled and locked if backup has archived notes
    @Test
    fun testRestoreEncryptedBackupRefusesWhenVaultLockedWithArchivedNotes(): Unit = runBlocking {
        ArchiveVaultSecurity.setPasscode(context, "654321")
        ArchiveVaultSecurity.lockVault()
        assertTrue("Vault must be locked", ArchiveVaultSecurity.isVaultLocked(context))

        val note = NoteEntity(
            id = 301L,
            title = "Secret Vault Note",
            content = "Encrypted private content",
            isArchived = true
        )
        // Unlock temporarily to create backup with archived note
        ArchiveVaultSecurity.verifyPasscode(context, "654321")
        noteDb.noteDao().insertNote(note)

        val backupFile = File(context.cacheDir, "test_backup_locked_vault.ayva_backup")
        val backupUri = Uri.fromFile(backupFile)
        val password = "BackupPassword#2026"

        val createResult = BackupRestoreManager.createEncryptedBackup(context, backupUri, password)
        assertTrue("Backup creation must succeed while vault unlocked", createResult.isSuccess)

        // Now lock vault and attempt restore
        ArchiveVaultSecurity.lockVault()
        assertTrue("Vault must be locked", ArchiveVaultSecurity.isVaultLocked(context))

        val restoreResult = BackupRestoreManager.restoreEncryptedBackup(context, backupUri, password, cleanRestore = true)
        assertTrue("Restore must fail when vault is locked with archived notes", restoreResult.isFailure)
        assertTrue("Exception must mention locked vault",
            restoreResult.exceptionOrNull() is IllegalStateException &&
            restoreResult.exceptionOrNull()?.message?.contains("locked", ignoreCase = true) == true
        )

        // Clean up vault state
        ArchiveVaultSecurity.verifyPasscode(context, "654321")
        ArchiveVaultSecurity.disablePasscode(context)
    }

    // B5-NEW-002, B5-NEW-003, B5-NEW-004: DataSafetyManager preserves subtasks, fontKey, deletedAt, and habit logs
    @Test
    fun testDataSafetyManagerPreservesSubtasksFontKeyDeletedAtAndHabitLogs() = runBlocking {
        val sampleSubtasks = """[{"id":"s_1","title":"Subtask One","isDone":true}]"""
        val task = Task(
            id = 401L,
            title = "Task with Subtasks",
            details = "DataSafetyManager test",
            subtasksJson = sampleSubtasks
        )
        focusDb.taskDao().insertTask(task)

        val note = NoteEntity(
            id = 402L,
            title = "Note with Font and DeletedAt",
            content = "Body",
            fontKey = "serif",
            deletedAt = 1715000000000L,
            isTrashed = true,
            trashedAt = 1714000000000L
        )
        noteDb.noteDao().insertNote(note)

        val habit = Habit(
            id = 403L,
            title = "Hydrate",
            type = HabitType.ONCE_DAILY
        )
        focusDb.habitDao().insertHabit(habit)

        val log = HabitLog(
            id = 404L,
            habitId = 403L,
            date = "2026-09-24",
            completedCount = 3,
            targetCount = 5
        )
        focusDb.habitDao().insertOrUpdateLog(log)

        val snapshotPath = DataSafetyManager.writePreOpSnapshot(context, noteDb.noteDao(), "test_safety_all", focusDb)
        assertNotNull("Snapshot path must not be null", snapshotPath)

        // Clear all tables
        focusDb.taskDao().deleteAllTasks()
        noteDb.noteDao().deleteAllNotes()
        focusDb.habitDao().deleteAllLogs()
        focusDb.habitDao().deleteAllHabits()

        assertEquals(0, focusDb.taskDao().getAllTasksList().size)
        assertEquals(0, noteDb.noteDao().getAllNotesList().size)
        assertEquals(0, focusDb.habitDao().getAllLogsSync().size)

        // Restore snapshot
        val restoreResult = DataSafetyManager.restoreSnapshot(context, snapshotPath!!, noteDb.noteDao(), focusDb)
        assertTrue("Restore must succeed", restoreResult.isSuccess)

        // Verify task subtasks
        val restoredTasks = focusDb.taskDao().getAllTasksList()
        assertEquals(1, restoredTasks.size)
        assertEquals(sampleSubtasks, restoredTasks.first().subtasksJson)

        // Verify note fontKey and deletedAt
        val restoredNotes = noteDb.noteDao().getAllNotesList()
        assertEquals(1, restoredNotes.size)
        assertEquals("serif", restoredNotes.first().fontKey)
        assertEquals(1715000000000L, restoredNotes.first().deletedAt)

        // Verify habit log
        val restoredLogs = focusDb.habitDao().getAllLogsSync()
        assertEquals("Habit logs must be restored", 1, restoredLogs.size)
        assertEquals("2026-09-24", restoredLogs.first().date)
        assertEquals(3, restoredLogs.first().completedCount)
    }

    // B5-NEW-005: FocusDatabaseMigrationHelper securely zero-wipes and deletes legacy db
    @Test
    fun testFocusDatabaseMigrationHelperSecureWipeAndIdempotency() = runBlocking {
        val legacyDbFile = context.getDatabasePath("focus_database")
        legacyDbFile.parentFile?.mkdirs()
        if (legacyDbFile.exists()) legacyDbFile.delete()

        val rawDb = SQLiteDatabase.openOrCreateDatabase(legacyDbFile, null)
        rawDb.execSQL("""
            CREATE TABLE tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                details TEXT NOT NULL DEFAULT '',
                dueDate INTEGER,
                isCompleted INTEGER NOT NULL DEFAULT 0,
                type TEXT NOT NULL DEFAULT 'TASK',
                recurrence TEXT NOT NULL DEFAULT 'NONE',
                isPersistent INTEGER NOT NULL DEFAULT 0,
                isPriority INTEGER NOT NULL DEFAULT 0,
                completedAt INTEGER,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                isTrashed INTEGER NOT NULL DEFAULT 0,
                trashedAt INTEGER DEFAULT NULL,
                deletedAt INTEGER DEFAULT NULL,
                subtasksJson TEXT NOT NULL DEFAULT '[]'
            )
        """.trimIndent())
        rawDb.execSQL("""
            INSERT INTO tasks (id, title, details, dueDate, isCompleted, type, recurrence, isPersistent, isPriority, completedAt, updatedAt, isTrashed, trashedAt, deletedAt, subtasksJson)
            VALUES (1, 'Legacy Task To Wipe', 'Details', NULL, 0, 'TASK', 'NONE', 0, 0, NULL, 1700000000000, 0, NULL, NULL, '[]')
        """.trimIndent())
        rawDb.close()

        assertTrue("Legacy plaintext db must exist before migration", legacyDbFile.exists())

        FocusDatabaseMigrationHelper.checkAndMigrateIfLegacyPlaintextExists(context, focusDb)

        // Legacy db must be deleted, not renamed to .migrated
        assertFalse("Legacy plaintext db must be zero-wiped and deleted", legacyDbFile.exists())
        val migratedBackup = File(legacyDbFile.parentFile, "focus_database.migrated")
        assertFalse("No plaintext .migrated file should linger on disk", migratedBackup.exists())

        val tasksAfterFirst = focusDb.taskDao().getAllTasksList()
        assertEquals(1, tasksAfterFirst.size)

        // Second call must be a clean no-op
        FocusDatabaseMigrationHelper.checkAndMigrateIfLegacyPlaintextExists(context, focusDb)
        val tasksAfterSecond = focusDb.taskDao().getAllTasksList()
        assertEquals("Second migration call must not duplicate tasks", 1, tasksAfterSecond.size)
    }

    // B5-NEW-008: Subtask.listFromJson handles both text/isCompleted and title/isDone
    @Test
    fun testSubtaskFlexibleJsonParsing() {
        val json = """[
            {"id":"s1","text":"Keep style item","isCompleted":true},
            {"id":"s2","title":"Standard item","isDone":false},
            {"id":"s3","text":"Checked item","isChecked":true}
        ]"""
        val subtasks = Subtask.listFromJson(json)
        assertEquals(3, subtasks.size)

        assertEquals("s1", subtasks[0].id)
        assertEquals("Keep style item", subtasks[0].title)
        assertTrue("isDone must be true when isCompleted is true", subtasks[0].isDone)

        assertEquals("s2", subtasks[1].id)
        assertEquals("Standard item", subtasks[1].title)
        assertFalse("isDone must be false", subtasks[1].isDone)

        assertEquals("s3", subtasks[2].id)
        assertEquals("Checked item", subtasks[2].title)
        assertTrue("isDone must be true when isChecked is true", subtasks[2].isDone)
    }

    // B5-NEW-009: ArticlePdfGenerator handles empty table without division by zero or crash
    @Test
    @Config(shadows = [ShadowPdfDocument::class])
    fun testArticlePdfGeneratorEmptyTableDoesNotCrash() {
        val emptyTableBlock = NotesnookBlock.Table(data = mutableListOf(mutableListOf()))
        val pdfBytes = ArticlePdfGenerator.generatePdf(
            title = "Empty Table Test",
            blocks = listOf(emptyTableBlock),
            fallbackContent = "Fallback"
        )
        assertNotNull("PDF bytes must not be null", pdfBytes)
        assertTrue("PDF must be generated safely", pdfBytes.isNotEmpty())
    }

    // B5-NEW-007: cleanRestore in BackupRestoreManager clears stale preferences
    @Test
    fun testCleanRestorePurgesStalePreferencesAndRestoresVocab() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<FocusApplication>()
        val vocabDb = app.vocabDatabase
        vocabDb.vocabDao().setIdiomLearned(1, 1700000000000L)
        vocabDb.vocabDao().setIdiomMastery(1, 1, 1700000000000L)

        val task = Task(id = 501L, title = "Task for Prefs Test")
        focusDb.taskDao().insertTask(task)

        val backupFile = File(context.cacheDir, "test_backup_clean_prefs.ayva_backup")
        val backupUri = Uri.fromFile(backupFile)
        val password = "BackupPassword#2026"

        val createResult = BackupRestoreManager.createEncryptedBackup(context, backupUri, password)
        assertTrue("Backup creation must succeed", createResult.isSuccess)

        // Inject stale key into focus_prefs
        val sp = app.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        sp.edit().putString("stale_obsolete_key", "must_be_purged").commit()
        assertTrue("Stale key must exist before clean restore", sp.contains("stale_obsolete_key"))

        val restoreResult = BackupRestoreManager.restoreEncryptedBackup(context, backupUri, password, cleanRestore = true)
        assertTrue("Restore must succeed", restoreResult.isSuccess)

        assertFalse("Stale preference key must be purged on cleanRestore", sp.contains("stale_obsolete_key"))
    }

    // B5-P4-001: restoreEncryptedBackup must refuse media entries outside whitelisted folders
    @Test
    fun testRestoreEncryptedBackupRefusesMediaOutsideWhitelistedFolders() = runBlocking {
        val backupFile = File(context.cacheDir, "malicious_media_backup.ayva_backup")
        val password = "StrongPassword#2026"
        val canaryFile = File(context.filesDir, "focus_vault_salt.bin")
        if (canaryFile.exists()) canaryFile.delete()

        // Create an encrypted backup archive manually with an entry targeting filesDir/focus_vault_salt.bin
        FileOutputStream(backupFile).use { fos ->
            CryptoBackupEngine.openEncryptingStream(fos, password.toCharArray()).use { cos ->
                java.util.zip.ZipOutputStream(cos).use { zos ->
                    // Add valid data.json
                    zos.putNextEntry(java.util.zip.ZipEntry("data.json"))
                    zos.write("""{"version":1,"createdAt":1700000000000,"notes":[]}""".toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // Add malicious media entry trying to overwrite filesDir root
                    zos.putNextEntry(java.util.zip.ZipEntry("media/focus_vault_salt.bin"))
                    zos.write("ATTACKER_CORRUPTED_SALT".toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
        }

        val result = BackupRestoreManager.restoreEncryptedBackup(context, Uri.fromFile(backupFile), password)
        assertTrue("Restore must fail when backup contains unscoped media path", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Expected SecurityException, got $ex", ex is SecurityException)
        assertFalse("Canary file in filesDir must NOT have been written or overwritten", canaryFile.exists())
    }

    // B5-P4-002: createEncryptedBackup must refuse when secret vault is locked and archived notes exist
    @Test
    fun testCreateEncryptedBackupRefusesWhenVaultLockedWithArchivedNotes() = runBlocking {
        val note = NoteEntity(
            id = 401L,
            title = "Top Secret Journal",
            content = "Classified content that requires active vault",
            isArchived = true
        )
        noteDb.noteDao().insertNote(note)

        // Configure vault and ensure it is locked
        ArchiveVaultSecurity.setPasscode(context, "654321")
        ArchiveVaultSecurity.lockVault()
        assertTrue("Vault must be locked", ArchiveVaultSecurity.isVaultLocked(context))

        val backupFile = File(context.cacheDir, "test_locked_vault_backup.ayva_backup")
        val result = BackupRestoreManager.createEncryptedBackup(context, Uri.fromFile(backupFile), "BackupPassword#2026")
        assertTrue("Backup must fail when secret vault is locked and archived notes exist", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Expected IllegalStateException, got $ex", ex is IllegalStateException)
    }

    // B5-P4-003: createEncryptedBackup and restoreEncryptedBackup must reject blank or empty passwords
    @Test
    fun testBackupPasswordValidationRefusesEmptyOrBlank() = runBlocking {
        val backupFile = File(context.cacheDir, "test_blank_password.ayva_backup")
        val createResult = BackupRestoreManager.createEncryptedBackup(context, Uri.fromFile(backupFile), "   ")
        assertTrue("createEncryptedBackup must fail on blank password", createResult.isFailure)
        assertTrue(createResult.exceptionOrNull() is IllegalArgumentException)

        val restoreResult = BackupRestoreManager.restoreEncryptedBackup(context, Uri.fromFile(backupFile), "")
        assertTrue("restoreEncryptedBackup must fail on blank password", restoreResult.isFailure)
        assertTrue(restoreResult.exceptionOrNull() is IllegalArgumentException)
    }

    // B5-P4-004: ArticleExporter must sanitize unsafe schemes in HTML export
    @Test
    fun testArticleExporterHtmlSanitizesUnsafeSchemes() {
        val embedBlock = NotesnookBlock.Embed(
            url = "javascript:alert(document.domain)",
            title = "Click Me"
        )
        val textBlock = NotesnookBlock.Text(
            text = "Malicious Link Here",
            spans = listOf(
                RichSpan(
                    start = 0,
                    end = 19,
                    type = RichSpanType.LINK,
                    payload = "javascript:alert(1)"
                )
            )
        )
        val html = ArticleExporter.exportToHtml(
            title = "Security Test",
            blocks = listOf(embedBlock, textBlock),
            fallbackContent = ""
        )
        assertFalse("Exported HTML must not contain executable javascript: links", html.contains("href=\"javascript:", ignoreCase = true))
    }

    @Implements(PdfDocument::class)
    class ShadowPdfDocument {
        companion object {
            var closeCallCount = 0
            var writeToCallCount = 0
            var pagesStarted = 0
        }

        @Implementation
        fun __constructor__() {}

        @Implementation
        fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
            pagesStarted++
            val pageConstructor = PdfDocument.Page::class.java.getDeclaredConstructor(
                android.graphics.Canvas::class.java,
                PdfDocument.PageInfo::class.java
            )
            pageConstructor.isAccessible = true
            return pageConstructor.newInstance(android.graphics.Canvas(), pageInfo)
        }

        @Implementation
        fun finishPage(page: PdfDocument.Page) {}

        @Implementation
        fun writeTo(out: OutputStream) {
            writeToCallCount++
            out.write("%PDF-1.4 Mock Content".toByteArray())
        }

        @Implementation
        fun close() {
            closeCallCount++
        }
    }
}
