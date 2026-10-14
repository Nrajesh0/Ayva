package com.focusbyrj.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitType
import com.focusbyrj.app.data.HabitRepository
import com.focusbyrj.app.ui.screens.notes.ArticleExporterHelper
import com.focusbyrj.app.util.diagnostics.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Date

/**
 * Work Package 7 Security & Concurrency Verification Suite
 *
 * Verifies:
 * - F12: Exact habit counts under concurrent updates (no lost updates / race conditions).
 * - F12: Transactional habit and log deletion prevents orphaned log records.
 * - F11: Unpredictable and collision-free export filenames for shared notes.
 * - F11: Stale export cache pruning removes plaintext export residue older than 1 hour.
 * - F11: PII, PIN, and mnemonic recovery phrase sanitization in diagnostic logging.
 */
@RunWith(RobolectricTestRunner::class)
class WorkPackage7SecurityTest {

    private lateinit var context: Context
    private lateinit var app: FocusApplication
    private lateinit var habitRepository: HabitRepository
    private lateinit var tempExportDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = context.applicationContext as FocusApplication
        habitRepository = app.habitRepository

        runBlocking {
            app.database.habitDao().deleteAllLogs()
            app.database.habitDao().deleteAllHabits()
        }

        tempExportDir = File(context.cacheDir, "test_exports").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        tempExportDir.deleteRecursively()
        runBlocking {
            app.database.habitDao().deleteAllLogs()
            app.database.habitDao().deleteAllHabits()
        }
    }

    @Test
    fun testF12_habitConcurrentIncrements_noLostUpdates() = runBlocking {
        val habit = Habit(
            id = 0,
            title = "Hydration Habit",
            targetPerDay = 50,
            type = HabitType.ONCE_DAILY
        )
        val habitId = habitRepository.insertHabit(habit)
        assertTrue(habitId > 0)

        // Concurrently increment 30 times across multiple parallel coroutines
        val concurrentTasks = (1..30).map {
            async(Dispatchers.Default) {
                habitRepository.incrementHabitProgress(habitId)
            }
        }
        concurrentTasks.awaitAll()

        // Read log from database
        val today = habitRepository.getTodayDateString()
        val finalLog = app.database.habitDao().getLogForHabitAndDate(habitId, today)
        assertNotNull(finalLog)
        assertEquals("Concurrent atomic increments must yield exact count of 30 without lost updates", 30, finalLog!!.completedCount)

        // Decrement 5 times
        repeat(5) {
            habitRepository.decrementHabitProgress(habitId)
        }
        val decrementedLog = app.database.habitDao().getLogForHabitAndDate(habitId, today)
        assertEquals(25, decrementedLog?.completedCount)
    }

    @Test
    fun testF12_habitTransactionalDeletion_removesHabitAndLogsAtomically() = runBlocking {
        val habit = Habit(
            id = 0,
            title = "Meditation",
            targetPerDay = 1,
            type = HabitType.ONCE_DAILY
        )
        val habitId = habitRepository.insertHabit(habit)
        habitRepository.incrementHabitProgress(habitId)

        val insertedHabit = app.database.habitDao().getHabitById(habitId)
        assertNotNull(insertedHabit)

        val today = habitRepository.getTodayDateString()
        val logBefore = app.database.habitDao().getLogForHabitAndDate(habitId, today)
        assertNotNull(logBefore)

        // Delete habit transactionally
        habitRepository.deleteHabit(insertedHabit!!)

        // Verify both habit and logs are gone
        val habitAfter = app.database.habitDao().getHabitById(habitId)
        val logAfter = app.database.habitDao().getLogForHabitAndDate(habitId, today)
        assertNull("Habit must be deleted", habitAfter)
        assertNull("Logs must be deleted atomically with habit", logAfter)
    }

    @Test
    fun testF11_shareFileName_unpredictableAndCollisionResistant() {
        val date = Date(1775460000000L) // Fixed timestamp
        val name1 = ArticleExporterHelper.generateShareFileName("Confidential Note", "txt", date)
        val name2 = ArticleExporterHelper.generateShareFileName("Confidential Note", "txt", date)

        // Despite identical title, date, and extension, names must not collide
        assertNotEquals("Share file names must be randomized with entropy to prevent overwrite races", name1, name2)
        assertTrue(name1.startsWith("Confidential_Note_"))
        assertTrue(name1.endsWith(".txt"))
        assertTrue(name2.startsWith("Confidential_Note_"))
        assertTrue(name2.endsWith(".txt"))

        // Handles reserved filenames safely
        val reservedName = ArticleExporterHelper.generateShareFileName("CON", "pdf", date)
        assertTrue("Reserved system names must be replaced with safe fallback", reservedName.startsWith("Focus_Note_"))
        assertTrue(reservedName.endsWith(".pdf"))
    }

    @Test
    fun testF11_staleExportCachePruning_removesOldFilesAndRetainsFreshFiles() {
        val now = System.currentTimeMillis()
        val staleFile = File(tempExportDir, "stale_export_note.txt").apply {
            writeText("stale plaintext content")
            setLastModified(now - 7200_000L) // 2 hours old
        }
        val freshFile = File(tempExportDir, "fresh_export_note.txt").apply {
            writeText("fresh plaintext content")
            setLastModified(now - 300_000L) // 5 minutes old
        }

        assertTrue(staleFile.exists())
        assertTrue(freshFile.exists())

        // Prune older than 1 hour (3,600,000 ms)
        val deletedCount = ArticleExporterHelper.pruneStaleExportFiles(tempExportDir, maxAgeMs = 3600_000L, now = now)

        assertEquals("Exactly 1 stale file must be purged", 1, deletedCount)
        assertFalse("Stale export file must be deleted to prevent disk residue", staleFile.exists())
        assertTrue("Fresh export file within retention window must be retained", freshFile.exists())
    }

    @Test
    fun testF11_diagnosticLogging_sanitizesSecretsAndMnemonic() {
        AppLogger.init(context)

        // Verify BIP-39 phrase pattern detection and PIN regex in AppLogger
        val mnemonic = "abandon ability able about above absent absorb abstract absurd abuse access accident"
        val sensitiveLog = "User entered recovery phrase: $mnemonic with passcode=987654"

        // Check recent logs or message sanitization
        AppLogger.i("TestTag", sensitiveLog)
        val snapshot = AppLogger.getRecentLogsSnapshot().joinToString("\n")

        assertFalse("Raw 12-word mnemonic phrase must not appear in log output", snapshot.contains(mnemonic))
        assertFalse("Raw passcode must not appear in log output", snapshot.contains("987654"))
    }
}
