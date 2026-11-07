package com.focusbyrj.app.util.backup

import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.drill.DrillDatabase
import com.focusbyrj.app.data.drill.DrillSessionRepository
import com.focusbyrj.app.data.note.NotesStorageVersion
import com.focusbyrj.app.ui.screens.DrillSession
import com.focusbyrj.app.util.AptitudeManager
import com.focusbyrj.app.util.DailyQuestManager
import com.focusbyrj.app.util.DrillSummaryHelper
import com.focusbyrj.app.util.FocusEconomyManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34], application = FocusApplication::class)
class DrillDurabilityTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusApplication>()
    @org.junit.Before fun initializeManagersBeforeTakingBaselines() {
        val context = app
        listOf("focus_economy_prefs", "aptitude_economy_prefs", "daily_learning_quests_prefs").forEach {
            check(context.getSharedPreferences(it, 0).edit().clear().commit())
        }
        runBlocking { DrillDatabase.getDatabase(context).drillSessionDao().deleteAllSessions() }
        DrillSessionRepository.invalidateCache()
        FocusEconomyManager.init(context)
        AptitudeManager.init(context)
        DailyQuestManager.init(context)
        DrillSessionRepository.init(context)
    }
    @After fun clearHook() { RestoreJournal.checkpointHook = null }

    @Test fun duplicateFinishUsesOneDurableIdentityAndAwardsOnceWithoutInvalidatingNoteEditors() = runBlocking {
        val session = DrillSession(difficulty = "easy", targetQuestions = 10, correct = 8, total = 10, gold = 40)
        val before = FocusEconomyManager.profileFlow.value.gold
        val storage = NotesStorageVersion.current()
        val first = DrillSummaryHelper.generateSummaryMessage(session)
        val after = FocusEconomyManager.profileFlow.value.gold
        // The first daily drill also earns the existing 50-gold daily quest bonus.
        assertEquals(before + 40 + 50, after)
        val repeated = DrillSummaryHelper.generateSummaryMessage(session.copy())
        assertEquals(first.id, repeated.id)
        assertEquals(after, FocusEconomyManager.profileFlow.value.gold)
        assertEquals(storage, NotesStorageVersion.current())
        assertNotNull(DrillDatabase.getDatabase(app).drillSessionDao().getSessionById(session.sessionId))
        assertTrue(DrillSessionRepository.markClaimed(session.sessionId).isSuccess)
        DrillSessionRepository.invalidateCache()
        assertTrue(DrillSessionRepository.getSummary(session.sessionId)!!.isClaimed)
    }

    @Test fun failureBeforeCommitRollsBackRewardsAndSummaryAndAllowsOneRetry() = runBlocking {
        val session = DrillSession(difficulty = "easy", targetQuestions = 10, correct = 7, total = 10, gold = 50)
        val storage = NotesStorageVersion.current()
        val unrelated = DrillSession(difficulty = "easy", targetQuestions = 1, correct = 1, total = 1)
        DrillSummaryHelper.generateSummaryMessage(unrelated)
        val untouched = DrillDatabase.getDatabase(app).drillSessionDao().getSessionById(unrelated.sessionId)
        val before = FocusEconomyManager.profileFlow.value.gold
        val xp = AptitudeManager.profileFlow.value.xp
        RestoreJournal.checkpointHook = { phase ->
            if (phase == "prepared") {
                val journal = org.json.JSONObject(EncryptedSnapshotFile.read(java.io.File(app.filesDir, "restore_before_state.enc")))
                assertEquals(session.sessionId, journal.getString("drillSessionId"))
                val tables = journal.getJSONArray("databases").getJSONObject(0).getJSONArray("tables")
                assertEquals(0, tables.getJSONObject(0).getJSONArray("rows").length())
            }
            if (phase == "before_commit") throw java.io.IOException("Synthetic durability failure")
        }
        try { DrillSummaryHelper.generateSummaryMessage(session); fail("Injected failure must surface") }
        catch (_: java.io.IOException) { }
        assertEquals(before, FocusEconomyManager.profileFlow.value.gold)
        assertEquals(xp, AptitudeManager.profileFlow.value.xp)
        assertNull(DrillDatabase.getDatabase(app).drillSessionDao().getSessionById(session.sessionId))
        assertEquals(untouched, DrillDatabase.getDatabase(app).drillSessionDao().getSessionById(unrelated.sessionId))
        assertFalse(session.isSettled.get())
        assertNotNull(DrillSessionRepository.saveError.value)
        assertEquals(storage, NotesStorageVersion.current())
        RestoreJournal.checkpointHook = null
        DrillSummaryHelper.generateSummaryMessage(session)
        assertEquals(before + 50, FocusEconomyManager.profileFlow.value.gold)
        assertTrue(session.isSettled.get())
    }
    @Test fun interruptedFirstDrillRollsBackItsDailyQuestBonusBeforeRetry() = runBlocking {
        val session = DrillSession(difficulty = "easy", targetQuestions = 1, correct = 1, total = 1, gold = 15)
        val goldBefore = FocusEconomyManager.profileFlow.value.gold
        val xpBefore = AptitudeManager.profileFlow.value.xp
        RestoreJournal.checkpointHook = { if (it == "before_commit") throw java.io.IOException("Synthetic first-drill failure") }
        try { DrillSummaryHelper.generateSummaryMessage(session); fail("Failure must surface") }
        catch (_: java.io.IOException) { }
        assertEquals(goldBefore, FocusEconomyManager.profileFlow.value.gold)
        assertEquals(xpBefore, AptitudeManager.profileFlow.value.xp)
        assertNull(DrillDatabase.getDatabase(app).drillSessionDao().getSessionById(session.sessionId))
        RestoreJournal.checkpointHook = null
        DrillSummaryHelper.generateSummaryMessage(session)
        assertEquals(goldBefore + 15 + 50, FocusEconomyManager.profileFlow.value.gold)
        val settled = FocusEconomyManager.profileFlow.value.gold
        DrillSummaryHelper.generateSummaryMessage(session)
        assertEquals(settled, FocusEconomyManager.profileFlow.value.gold)
    }

    @Test fun submittedResultDoesNotChangeWhileJournalIsWriting() = runBlocking {
        val options = mutableListOf("one", "two")
        val session = DrillSession(difficulty = "easy", targetQuestions = 2, correct = 1, total = 1, gold = 20)
        session.questionRecords.add(com.focusbyrj.app.ui.screens.QuestionRecord(
            1, "Original", "First question", options, 0, 0, "correct", "Original answer"))
        val goldBefore = FocusEconomyManager.profileFlow.value.gold
        RestoreJournal.checkpointHook = { phase ->
            if (phase == "prepared") {
                session.correct = 99
                session.total = 100
                session.gold = 999
                options[0] = "changed after submission"
                session.questionRecords.clear()
                session.preGeneratedQuestions.add("""{"title":"Late question","questionText":"Too late","options":["late"]}""")
            }
        }
        DrillSummaryHelper.generateSummaryMessage(session)
        val saved = DrillSessionRepository.getSummary(session.sessionId)!!
        assertEquals(1, saved.correctCount)
        assertEquals(1, saved.totalQuestions)
        assertEquals(20, saved.goldEarned)
        assertEquals(1, saved.questions.size)
        assertEquals("one", saved.questions.single().options.first())
        assertEquals(goldBefore + 20 + 50, FocusEconomyManager.profileFlow.value.gold)
    }

    @Test fun queuedVocabularyAnswerWaitsForSettlementWithoutLosingProgress() = runBlocking {
        val dao = app.vocabDatabase.vocabDao()
        val id = requireNotNull(dao.getAllIdioms().first().id)
        dao.setIdiomMastery(id, 0, 0L)
        val writer = com.focusbyrj.app.data.VocabQuizWriter(app)
        writer.enqueue("idiom", id, true)
        val prepared = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        RestoreJournal.checkpointHook = { phase ->
            if (phase == "prepared") {
                prepared.countDown()
                check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
            }
        }
        val finishing = async(Dispatchers.IO) {
            DrillSummaryHelper.generateSummaryMessage(DrillSession(difficulty = "easy", total = 1, correct = 1))
        }
        try {
            withContext(Dispatchers.IO) { assertTrue(prepared.await(10, java.util.concurrent.TimeUnit.SECONDS)) }
            val feedback = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { writer.flush() }
            assertFalse("The answer must await admission before dispatching to Room", feedback.isCompleted)
            release.countDown()
            finishing.await()
            assertTrue(feedback.await().isSuccess)
            assertEquals(1, dao.getAllIdioms().first { it.id == id }.isMastered)
        } finally { release.countDown() }
    }

    @Test fun failedVocabularyAnswerRemainsPendingUntilSuccessfulRetry() = runBlocking {
        val dao = app.vocabDatabase.vocabDao()
        val id = requireNotNull(dao.getAllIdioms().first().id)
        dao.setIdiomMastery(id, 0, 0L)
        val db = app.vocabDatabase.openHelper.writableDatabase
        val writer = com.focusbyrj.app.data.VocabQuizWriter(app)
        writer.enqueue("idiom", id, true)
        db.execSQL("CREATE TRIGGER synthetic_vocab_failure BEFORE UPDATE ON idioms BEGIN SELECT RAISE(ABORT, 'Synthetic vocabulary write failure'); END")
        try {
            assertTrue(writer.flush().isFailure)
            assertEquals(0, dao.getAllIdioms().first { it.id == id }.isMastered)
        } finally { db.execSQL("DROP TRIGGER synthetic_vocab_failure") }
        assertTrue(writer.flush().isSuccess)
        assertEquals(1, dao.getAllIdioms().first { it.id == id }.isMastered)
        // An acknowledged answer must not be replayed by a later empty flush.
        dao.setIdiomMastery(id, 0, 0L)
        assertTrue(writer.flush().isSuccess)
        assertEquals(0, dao.getAllIdioms().first { it.id == id }.isMastered)
    }

}
