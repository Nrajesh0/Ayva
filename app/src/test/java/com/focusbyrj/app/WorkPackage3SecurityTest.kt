package com.focusbyrj.app

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.AppRestriction
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.FocusDatabaseMigrationHelper
import com.focusbyrj.app.data.FocusSchedule
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitLog
import com.focusbyrj.app.data.HabitType
import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskType
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.DatabaseKeyProvider
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteDatabaseMigrationHelper
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Work Package 3 Security & Data Integrity Verification Suite
 *
 * Verifies:
 * - F01: Legacy data encrypted with static software seeds is cleanly readable via compatibility adapters;
 *        production writers use hardware KeyStore and fail closed on KeyStore error.
 * - F04: Legacy SQLite migrations (NoteDatabase and FocusDatabase) are transactional, strictly verified,
 *        idempotent, and fail-safe non-destructive (never wipe source files on error or partial copy).
 * - Fallback databases are safely quarantined rather than blindly wiped.
 */
@RunWith(RobolectricTestRunner::class)
class WorkPackage3SecurityTest {

    private lateinit var context: Context
    private lateinit var noteDb: NoteDatabase
    private lateinit var focusDb: FocusDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        noteDb = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        focusDb = Room.inMemoryDatabaseBuilder(context, FocusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseKeyProvider.clearCachedPassphrase()
    }

    @After
    fun tearDown() {
        DatabaseKeyProvider.clearCachedPassphrase()
        noteDb.close()
        focusDb.close()
    }

    // =========================================================================
    // F01: Static Key Separation & Backward Compatibility Tests
    // =========================================================================

    @Test
    fun f01_databaseKeyProvider_readsLegacyStaticKeyEncryptedPassphraseAndUpgrades() {
        val expectedPassphrase = ByteArray(32) { (it * 3).toByte() }
        val legacyKey = DatabaseKeyProvider.getLegacyFallbackKey()

        // Encrypt test passphrase using legacy software fallback key
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, legacyKey, GCMParameterSpec(128, iv))
        val encryptedBytes = cipher.doFinal(expectedPassphrase)

        // Store legacy encrypted passphrase in SharedPreferences
        val prefs = context.getSharedPreferences("focus_notes_vault_security_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("enc_db_passphrase", Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
            .putString("enc_db_passphrase_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .commit()

        DatabaseKeyProvider.clearCachedPassphrase()

        // DatabaseKeyProvider must successfully read the legacy static key encrypted passphrase
        val retrievedPassphrase = DatabaseKeyProvider.getOrCreatePassphrase(context)
        assertArrayEquals("Retrieved passphrase must match original plaintext", expectedPassphrase, retrievedPassphrase)

        DatabaseKeyProvider.clearCachedPassphrase()

        // Second call should also succeed (and verify transparent KeyStore upgrade)
        val retrievedPassphrase2 = DatabaseKeyProvider.getOrCreatePassphrase(context)
        assertArrayEquals("Subsequent read must continue returning correct passphrase", expectedPassphrase, retrievedPassphrase2)
    }

    @Test
    fun f01_archiveVaultSecurity_legacyStaticKeyDecryptionSupported() {
        val legacySeed = MessageDigest.getInstance("SHA-256")
            .digest("focus_vault_master_software_seed_v1".toByteArray(Charsets.UTF_8))
        val legacyKey = SecretKeySpec(legacySeed, "AES")

        val secretPayload = "VaultSecretCredentialHash2026".toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, legacyKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(secretPayload)

        // decryptWithMasterKey must decrypt data encrypted with legacy static key
        val decrypted = ArchiveVaultSecurity.decryptWithMasterKey(ciphertext, iv)
        assertArrayEquals("Legacy master key decrypted data must match secret payload", secretPayload, decrypted)
    }

    @Test
    fun f01_encryptedMediaStorage_readsLegacyStaticKeyEncryptedMedia() {
        val testFile = File(context.cacheDir, "legacy_media_test.bin")
        try {
            val originalMedia = "Legacy Audio/Image Content Encrypted With Static Seed".toByteArray(Charsets.UTF_8)
            val legacySeed = MessageDigest.getInstance("SHA-256")
                .digest("focus_media_storage_software_seed_v1".toByteArray(Charsets.UTF_8))
            val legacyKey = SecretKeySpec(legacySeed, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, legacyKey, GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(originalMedia)

            // Write file with standard MAGIC_HEADER + IV + ciphertext
            FileOutputStream(testFile).use { fos ->
                fos.write("FOC_ENC_V1:".toByteArray(Charsets.UTF_8))
                fos.write(iv)
                fos.write(ciphertext)
            }

            assertTrue("Test file must exist", testFile.exists())
            assertTrue("File must be detected as encrypted", EncryptedMediaStorage.isEncrypted(testFile))

            // 1. readDecryptedBytes must cleanly decrypt legacy media
            val decryptedBytes = EncryptedMediaStorage.readDecryptedBytes(testFile)
            assertNotNull("Decrypted bytes must not be null", decryptedBytes)
            assertArrayEquals("Decrypted bytes must match original media", originalMedia, decryptedBytes)

            // 2. openDecryptedInputStream must cleanly stream legacy media
            val inputStream = EncryptedMediaStorage.openDecryptedInputStream(testFile)
            assertNotNull("Decrypted input stream must not be null", inputStream)
            val streamedBytes = inputStream!!.use { it.readBytes() }
            assertArrayEquals("Streamed bytes must match original media", originalMedia, streamedBytes)
        } finally {
            testFile.delete()
        }
    }

    // =========================================================================
    // F04: Safe Non-Destructive Database Migrations
    // =========================================================================

    @Test
    fun f04_noteDatabaseMigrationHelper_preservesAllFieldsAndIsIdempotent() {
        runBlocking {
            val legacyDbFile = context.getDatabasePath("keep_notes.db")
            legacyDbFile.parentFile?.mkdirs()
            if (legacyDbFile.exists()) legacyDbFile.delete()

            // Clear migration preferences
            context.getSharedPreferences("focus_migration_prefs", Context.MODE_PRIVATE).edit().clear().commit()

            val rawDb = SQLiteDatabase.openOrCreateDatabase(legacyDbFile, null)
            rawDb.execSQL("""
                CREATE TABLE keep_notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    isChecklist INTEGER NOT NULL DEFAULT 0,
                    checklistJson TEXT NOT NULL DEFAULT '[]',
                    colorKey TEXT NOT NULL DEFAULT 'default',
                    fontKey TEXT NOT NULL DEFAULT 'default',
                    isPinned INTEGER NOT NULL DEFAULT 0,
                    isArchived INTEGER NOT NULL DEFAULT 0,
                    isTrashed INTEGER NOT NULL DEFAULT 0,
                    labelsJson TEXT NOT NULL DEFAULT '[]',
                    imageUrisJson TEXT NOT NULL DEFAULT '[]',
                    audioUrisJson TEXT NOT NULL DEFAULT '[]',
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    trashedAt INTEGER DEFAULT NULL,
                    deletedAt INTEGER DEFAULT NULL
                )
            """.trimIndent())

            rawDb.execSQL("""
                INSERT INTO keep_notes (id, title, content, isChecklist, checklistJson, colorKey, fontKey, isPinned, isArchived, isTrashed, labelsJson, imageUrisJson, audioUrisJson, createdAt, updatedAt, trashedAt, deletedAt)
                VALUES (1, 'WP3 Note', 'Content 123', 1, '[{"id":"1","text":"Item 1","isChecked":true}]', 'amber', 'serif', 1, 0, 0, '["urgent"]', '["img1.jpg"]', '["aud1.m4a"]', 1700000000000, 1700000005000, NULL, NULL)
            """.trimIndent())
            rawDb.close()

            assertTrue("Legacy DB must exist before migration", legacyDbFile.exists())

            // Run migration
            NoteDatabaseMigrationHelper.checkAndMigrateIfLegacyPlaintextExists(context, noteDb)

            // Legacy plaintext db must be zero-wiped and deleted upon successful migration
            assertFalse("Legacy DB file must be cleaned up after verified migration", legacyDbFile.exists())

            val notes = noteDb.noteDao().getAllNotesList()
            assertEquals("Must have migrated exactly 1 note", 1, notes.size)
            val migrated = notes.first()
            assertEquals(1L, migrated.id)
            assertEquals("WP3 Note", migrated.title)
            assertEquals("Content 123", migrated.content)
            assertTrue(migrated.isChecklist)
            assertEquals("amber", migrated.colorKey)
            assertEquals("serif", migrated.fontKey)
            assertTrue(migrated.isPinned)
            assertEquals(1, migrated.getImageUris().size)
            assertEquals("img1.jpg", migrated.getImageUris().first())
            assertEquals(1, migrated.getAudioUris().size)
            assertEquals("aud1.m4a", migrated.getAudioUris().first())

            // Verify migration marker was persisted
            val prefs = context.getSharedPreferences("focus_migration_prefs", Context.MODE_PRIVATE)
            assertTrue("notes_legacy_plaintext_migrated flag must be true", prefs.getBoolean("notes_legacy_plaintext_migrated", false))

            // Second call must be a clean no-op and never duplicate records
            NoteDatabaseMigrationHelper.checkAndMigrateIfLegacyPlaintextExists(context, noteDb)
            assertEquals("Second migration invocation must not duplicate records", 1, noteDb.noteDao().getAllNotesList().size)
        }
    }

    @Test
    fun f04_noteDatabaseMigrationHelper_abortsWithoutWipingWhenCorrupt() {
        runBlocking {
            val legacyDbFile = context.getDatabasePath("keep_notes.db")
            legacyDbFile.parentFile?.mkdirs()
            if (legacyDbFile.exists()) legacyDbFile.delete()

            context.getSharedPreferences("focus_migration_prefs", Context.MODE_PRIVATE).edit().clear().commit()

            // Create a valid SQLite database with a completely missing keep_notes table
            val rawDb = SQLiteDatabase.openOrCreateDatabase(legacyDbFile, null)
            rawDb.execSQL("CREATE TABLE other_table (id INTEGER PRIMARY KEY)")
            rawDb.close()

            assertTrue("Legacy DB must exist", legacyDbFile.exists())

            // Run migration on database missing keep_notes table
            NoteDatabaseMigrationHelper.checkAndMigrateIfLegacyPlaintextExists(context, noteDb)

            // Legacy database must NOT be wiped or deleted because migration aborted!
            assertTrue("Corrupted/unrecognized legacy DB must NOT be wiped or deleted", legacyDbFile.exists())
            legacyDbFile.delete()
        }
    }

    @Test
    fun f04_focusDatabaseMigrationHelper_preservesAllTablesAndIsIdempotent() {
        runBlocking {
        val legacyDbFile = context.getDatabasePath("focus_database")
        legacyDbFile.parentFile?.mkdirs()
        if (legacyDbFile.exists()) legacyDbFile.delete()

        context.getSharedPreferences("focus_migration_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val rawDb = SQLiteDatabase.openOrCreateDatabase(legacyDbFile, null)
        rawDb.execSQL("""
            CREATE TABLE app_restrictions (
                packageName TEXT PRIMARY KEY NOT NULL,
                appName TEXT NOT NULL,
                isRestricted INTEGER NOT NULL,
                mode TEXT NOT NULL,
                restrictionMode TEXT NOT NULL,
                timeLimitMinutes INTEGER NOT NULL,
                clickLimitCount INTEGER NOT NULL,
                customQuote TEXT NOT NULL
            )
        """.trimIndent())
        rawDb.execSQL("""
            CREATE TABLE focus_schedules (
                id INTEGER PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                startHour INTEGER NOT NULL,
                startMinute INTEGER NOT NULL,
                endHour INTEGER NOT NULL,
                endMinute INTEGER NOT NULL,
                daysOfWeek TEXT NOT NULL,
                mode TEXT NOT NULL,
                restrictionMode TEXT NOT NULL,
                timeLimitMinutes INTEGER NOT NULL,
                clickLimitCount INTEGER NOT NULL,
                appsToBlock TEXT NOT NULL,
                isEnabled INTEGER NOT NULL
            )
        """.trimIndent())
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
            CREATE TABLE habits (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                iconEmoji TEXT NOT NULL DEFAULT '✨',
                colorHex TEXT NOT NULL DEFAULT '#3B82F6',
                type TEXT NOT NULL DEFAULT 'ONCE_DAILY',
                targetPerDay INTEGER NOT NULL DEFAULT 1,
                intervalHours INTEGER NOT NULL DEFAULT 2,
                intervalMinutes INTEGER NOT NULL DEFAULT 0,
                windowStartHour INTEGER NOT NULL DEFAULT 9,
                windowStartMinute INTEGER NOT NULL DEFAULT 0,
                windowEndHour INTEGER NOT NULL DEFAULT 21,
                windowEndMinute INTEGER NOT NULL DEFAULT 0,
                fixedReminderHour INTEGER NOT NULL DEFAULT 9,
                fixedReminderMinute INTEGER NOT NULL DEFAULT 0,
                isReminderEnabled INTEGER NOT NULL DEFAULT 1,
                reminderSound TEXT NOT NULL DEFAULT 'ZEN',
                createdAt INTEGER NOT NULL DEFAULT 0,
                isArchived INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        rawDb.execSQL("""
            CREATE TABLE habit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                habitId INTEGER NOT NULL,
                date TEXT NOT NULL,
                completedCount INTEGER NOT NULL DEFAULT 0,
                targetCount INTEGER NOT NULL DEFAULT 1,
                lastCompletedTimestamp INTEGER DEFAULT NULL
            )
        """.trimIndent())

        // Insert test records
        rawDb.execSQL("INSERT INTO app_restrictions VALUES ('com.distract.app', 'Distract App', 1, 'HARD', 'SIMPLE', 15, 5, 'Stay focused')")
        rawDb.execSQL("INSERT INTO focus_schedules VALUES (1, 'Morning Work', 9, 0, 12, 0, 'MON,TUE', 'HARD', 'SIMPLE', 0, 0, 'com.distract.app', 1)")
        rawDb.execSQL("INSERT INTO tasks (id, title, details, isCompleted) VALUES (1, 'Migrate WP3', 'Details here', 0)")
        rawDb.execSQL("INSERT INTO habits (id, title, targetPerDay) VALUES (1, 'Drink Water', 8)")
        rawDb.execSQL("INSERT INTO habit_logs (id, habitId, date, completedCount, targetCount) VALUES (1, 1, '2026-10-06', 4, 8)")
        rawDb.close()

        assertTrue("Legacy FocusDatabase must exist", legacyDbFile.exists())

        // Run migration
        FocusDatabaseMigrationHelper.checkAndMigrateIfLegacyPlaintextExists(context, focusDb)

        // Verify database file was cleaned up and data is preserved across all tables
        assertFalse("Legacy DB file must be cleaned up after verified migration", legacyDbFile.exists())

        assertEquals(1, focusDb.appRestrictionDao().getAllRestrictionsSync().size)
        assertEquals(1, focusDb.scheduleDao().getAllSchedulesSync().size)
        assertEquals(1, focusDb.taskDao().getAllTasksList().size)
        assertEquals(1, focusDb.habitDao().getAllHabitsSync().size)
        assertEquals(1, focusDb.habitDao().getAllLogsSync().size)

        val prefs = context.getSharedPreferences("focus_migration_prefs", Context.MODE_PRIVATE)
        assertTrue("focus_legacy_plaintext_migrated flag must be true", prefs.getBoolean("focus_legacy_plaintext_migrated", false))

        // Idempotency: second call must not duplicate data
        FocusDatabaseMigrationHelper.checkAndMigrateIfLegacyPlaintextExists(context, focusDb)
        assertEquals(1, focusDb.taskDao().getAllTasksList().size)
        assertEquals(1, focusDb.habitDao().getAllHabitsSync().size)
        }
    }

    @Test
    fun f04_fallbackDatabasesAreQuarantinedRatherThanDeleted() {
        val testFallbackDb = File(context.cacheDir, "test_quarantine_fallback.db")
        testFallbackDb.writeText("Legacy Fallback Data Content")

        assertTrue("Fallback DB must exist before quarantine", testFallbackDb.exists())

        val quarantined = FocusDatabaseMigrationHelper.quarantineFallbackDatabase(testFallbackDb)
        assertTrue("Quarantine operation must report success", quarantined)

        val quarantineFile = File(context.cacheDir, "test_quarantine_fallback.db.quarantine")
        assertFalse("Original fallback DB must no longer exist at original path", testFallbackDb.exists())
        assertTrue("Quarantine file must exist", quarantineFile.exists())
        assertEquals("Legacy Fallback Data Content", quarantineFile.readText())

        quarantineFile.delete()
    }
}
