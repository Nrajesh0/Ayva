package com.focusbyrj.app.util.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.chat.AyvaChatDatabase
import com.focusbyrj.app.data.drill.DrillDatabase
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.DatabaseKeyProvider
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.util.CompletedTaskHistoryManager
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuxiliaryEncryptionDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FocusApplication

    @Test fun allFiveUserDatabasesUseNativeCipherAndVocabularySeedsWithoutUserProgress() = runBlocking {
        val stores = listOf(
            NoteDatabase.getInstance(app) to "keep_notes_vault.db", app.database to "focus_database_vault.db",
            AyvaChatDatabase.getDatabase(app) to "ayva_chat.db", DrillDatabase.getDatabase(app) to "drill_sessions.db",
            app.vocabDatabase to "vocab.db")
        stores.forEach { (room, name) ->
            room.openHelper.writableDatabase.query("PRAGMA cipher_version").use { cursor ->
                assertTrue("$name must execute native SQLCipher", cursor.moveToFirst())
                assertTrue(cursor.getString(0).isNotBlank())
            }
            val header = ByteArray(16).also { bytes -> java.io.DataInputStream(app.getDatabasePath(name).inputStream()).use { it.readFully(bytes) } }
            assertFalse("$name must not have a plaintext SQLite header", header.decodeToString().startsWith("SQLite format 3"))
        }
        val idioms = app.vocabDatabase.vocabDao().getAllIdioms()
        val words = app.vocabDatabase.vocabDao().getAllOws()
        assertEquals(1773, idioms.size); assertEquals(2027, words.size)
        assertTrue(idioms.all { it.isMastered == 0 && it.isBookmarked == 0 && it.learnedAt == null })
        assertTrue(words.all { it.isMastered == 0 && it.isBookmarked == 0 && it.learnedAt == null })
    }

    @Test fun historyIsEncryptedAtRestAndPortableEncodingIsValidated() {
        val prefs = app.getSharedPreferences(CompletedTaskHistoryManager.PREFS_NAME, 0)
        val before = prefs.getString(CompletedTaskHistoryManager.KEY_HISTORY, null)
        try {
            CompletedTaskHistoryManager.recordCompletedTask(app, Task(id = 987654321, title = "SYNTHETIC_PRIVATE_HISTORY"))
            val stored = prefs.getString(CompletedTaskHistoryManager.KEY_HISTORY, null)!!
            assertFalse(stored.contains("SYNTHETIC_PRIVATE_HISTORY"))
            val portable = CompletedTaskHistoryManager.portable(app)
            assertTrue(portable.toString().contains("SYNTHETIC_PRIVATE_HISTORY"))
            CompletedTaskHistoryManager.restorePortable(app, portable, true, emptyMap())
            assertTrue(CompletedTaskHistoryManager.getTodayCompletedTasks(app).any { it.title == "SYNTHETIC_PRIVATE_HISTORY" })
        } finally { check(prefs.edit().putString(CompletedTaskHistoryManager.KEY_HISTORY, before).commit()) }
    }

    @Test fun pinChangeRetainsStableVaultKeyWithoutPersistingRecoverableKek() {
        val prefs = app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0)
        check(ArchiveVaultSecurity.getVaultStatus(app) != ArchiveVaultSecurity.VaultStatus.ENABLED) { "Use a clean emulator fixture" }
        try {
            assertTrue(ArchiveVaultSecurity.setPasscode(app, "729461"))
            val original = ArchiveVaultSecurity.getActiveVaultSubKey()!!
            assertFalse(prefs.contains("enc_hash")); assertFalse(prefs.contains("enc_iv"))
            assertEquals(32, SnapshotCryptoEngine.decryptSnapshot(android.util.Base64.decode(prefs.getString("pin_verifier_v2", ""), 0)).size)
            ArchiveVaultSecurity.lockVault()
            assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "729461") is ArchiveVaultSecurity.VerifyResult.Success)
            assertArrayEquals(original, ArchiveVaultSecurity.getActiveVaultSubKey())
            assertTrue(ArchiveVaultSecurity.setPasscode(app, "418632"))
            ArchiveVaultSecurity.lockVault()
            assertTrue(ArchiveVaultSecurity.verifyPasscode(app, "418632") is ArchiveVaultSecurity.VerifyResult.Success)
            assertArrayEquals(original, ArchiveVaultSecurity.getActiveVaultSubKey())
            original.fill(0)
            assertTrue(ArchiveVaultSecurity.disablePasscode(app))
        } finally { ArchiveVaultSecurity.lockVault() }
    }
    private fun fixture(name: String, encrypted: Boolean): com.focusbyrj.app.data.drill.DrillDatabase {
        val builder = androidx.room.Room.databaseBuilder(app, com.focusbyrj.app.data.drill.DrillDatabase::class.java, name)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
        if (encrypted) builder.openHelperFactory(com.focusbyrj.app.data.SecureRoomFactory.create(app))
        return builder.build()
    }

    @Test fun unsupportedPlaintextDatabaseIsRejectedWithoutDeletingItsBytes() = runBlocking {
        val name = "native-owned-plaintext-guard.db"
        app.deleteDatabase(name)
        fixture(name, false).also { room ->
            room.drillSessionDao().insertSession(com.focusbyrj.app.data.drill.DrillSessionEntity("synthetic", "Retained fixture", 1, 1, 1, 1, false, summaryJson = "{}"))
            room.close()
        }
        val file = app.getDatabasePath(name); val before = file.readBytes()
        val encrypted = fixture(name, true)
        try {
            var rejected = false
            try { encrypted.drillSessionDao().getAllSessionsSync() } catch (_: Exception) { rejected = true }
            assertTrue("Plaintext development stores must fail closed", rejected)
            assertTrue(file.exists()); assertArrayEquals(before, file.readBytes())
        } finally { encrypted.close(); app.deleteDatabase(name) }
    }

    @Test fun damagedEncryptedUserPageDoesNotTriggerDestructiveRecovery() = runBlocking {
        val name = "native-owned-encrypted-guard.db"
        app.deleteDatabase(name)
        val room = fixture(name, true)
        room.drillSessionDao().insertSession(com.focusbyrj.app.data.drill.DrillSessionEntity("synthetic", "Retained fixture", 1, 1, 1, 1, false, summaryJson = "{}"))
        val db = room.openHelper.writableDatabase
        val rootPage = db.query("SELECT rootpage FROM sqlite_master WHERE name='drill_sessions'").use { it.moveToFirst(); it.getLong(0) }
        val pageSize = db.query("PRAGMA page_size").use { it.moveToFirst(); it.getLong(0) }
        room.close()
        val file = app.getDatabasePath(name)
        java.io.RandomAccessFile(file, "rw").use { bytes ->
            val offset = (rootPage - 1) * pageSize + 100
            bytes.seek(offset); val value = bytes.readByte(); bytes.seek(offset); bytes.writeByte(value.toInt() xor 1); bytes.fd.sync()
        }
        val damaged = file.readBytes()
        val reopened = fixture(name, true)
        try {
            var rejected = false
            try { reopened.drillSessionDao().getAllSessionsSync() } catch (_: Exception) { rejected = true }
            assertTrue("Authenticated-page damage must surface", rejected)
            assertTrue(file.exists()); assertArrayEquals(damaged, file.readBytes())
        } finally { reopened.close(); app.deleteDatabase(name) }
    }

}
