package com.focusbyrj.app.util

import android.app.Application
import androidx.room.Room
import com.focusbyrj.app.data.chat.AyvaChatDatabase
import com.focusbyrj.app.data.chat.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class ChatPersistenceConcurrencyTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AyvaChatDatabase
    private val queryExecutor = Executors.newSingleThreadExecutor()
    private val transactionExecutor = Executors.newSingleThreadExecutor()
    private val blockQueries = AtomicBoolean(false)
    private val queryEntered = CountDownLatch(1)
    private val releaseQueries = CountDownLatch(1)
    private fun message(id: String, text: String = id) = PersistedChatMessage(id, text, true)
    private val jobs get() = ReflectionHelpers.getStaticField<CoroutineScope>(BubbleChatManager::class.java, "scope")
        .coroutineContext[Job]!!

    private fun resetMemory() {
        ReflectionHelpers.getStaticField<java.util.concurrent.atomic.AtomicBoolean>(BubbleChatManager::class.java, "initLatch").set(false)
        ReflectionHelpers.setStaticField(BubbleChatManager::class.java, "isInitialized", false)
        ReflectionHelpers.setStaticField(BubbleChatManager::class.java, "revision", 0L)
        ReflectionHelpers.setStaticField(BubbleChatManager::class.java, "persistedRevision", -1L)
        ReflectionHelpers.getStaticField<MutableList<*>>(BubbleChatManager::class.java, "beforeLoad").clear()
        ReflectionHelpers.getStaticField<MutableStateFlow<List<PersistedChatMessage>>>(BubbleChatManager::class.java, "_messagesFlow").value = emptyList()
        ReflectionHelpers.getStaticField<MutableStateFlow<Boolean>>(BubbleChatManager::class.java, "_loaded").value = false
    }

    @Before fun prepare() = runBlocking {
        withTimeout(10_000) { jobs.children.toList().joinAll() }
        resetMemory()
        context.getSharedPreferences("bubble_chat_prefs", 0).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AyvaChatDatabase::class.java)
            .setQueryExecutor { action -> queryExecutor.execute {
                if (blockQueries.get()) { queryEntered.countDown(); check(releaseQueries.await(10, TimeUnit.SECONDS)) }
                action.run()
            } }
            .setTransactionExecutor(transactionExecutor).build()
        ReflectionHelpers.setStaticField(AyvaChatDatabase::class.java, "INSTANCE", db)
    }

    @After fun cleanup() = runBlocking {
        blockQueries.set(false); releaseQueries.countDown()
        withTimeout(10_000) { jobs.children.toList().joinAll() }
        db.close()
        ReflectionHelpers.setStaticField(AyvaChatDatabase::class.java, "INSTANCE", null)
        resetMemory()
        queryExecutor.shutdownNow(); transactionExecutor.shutdownNow()
        Unit
    }

    @Test fun coldStartReplaysNewMessagesOverStoredHistory() = runBlocking {
        db.chatDao().insertMessage(message("stored").toEntity())
        blockQueries.set(true)
        BubbleChatManager.init(context)
        assertTrue(queryEntered.await(5, TimeUnit.SECONDS))
        BubbleChatManager.applyMessageChanges(context, emptyList(), listOf(message("typed")))
        BubbleChatManager.addMessage(context, message("alert"), incrementBadge = true)
        blockQueries.set(false); releaseQueries.countDown()
        assertTrue(BubbleChatManager.awaitInitialized(context))
        assertEquals(setOf("stored", "typed", "alert"), db.chatDao().getAllMessagesSync().map { it.id }.toSet())
        assertEquals(1, BubbleChatManager.getUnreadCount(context))
    }

    @Test fun concurrentAlertsKeepAllMessagesAndAllBadgeIncrements() = runBlocking {
        assertTrue(BubbleChatManager.awaitInitialized(context))
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = (0 until 40).map { index -> pool.submit {
                BubbleChatManager.addMessage(context, message("message-$index"), incrementBadge = true)
            } }
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
            assertTrue(BubbleChatManager.awaitInitialized(context))
            assertEquals(40, BubbleChatManager.messagesFlow.value.size)
            assertEquals(40, db.chatDao().getMessageCount())
            assertEquals(40, BubbleChatManager.getUnreadCount(context))
        } finally { pool.shutdownNow() }
    }

    @Test fun staleEditorPreservesUnseenAlertsAndNewerMessageEdits() = runBlocking {
        val old = message("first")
        BubbleChatManager.saveMessages(context, listOf(old))
        assertTrue(BubbleChatManager.awaitInitialized(context))
        val alert = message("alert")
        BubbleChatManager.addMessage(context, alert)
        BubbleChatManager.updateMessage(context, old.copy(text = "newer edit"))
        val typed = message("typed")
        BubbleChatManager.applyMessageChanges(context, listOf(old), listOf(old, typed))
        assertTrue(BubbleChatManager.awaitInitialized(context))
        assertEquals(3, db.chatDao().getMessageCount())
        assertEquals("newer edit", db.chatDao().getMessageById("first")!!.text)
        BubbleChatManager.applyMessageChanges(context, listOf(old, typed), listOf(typed))
        assertTrue(BubbleChatManager.awaitInitialized(context))
        assertEquals(setOf("alert", "typed"), db.chatDao().getAllMessagesSync().map { it.id }.toSet())
    }

    @Test fun clearDoesNotRaceALaterAppendBackOutOfTheDatabase() = runBlocking {
        BubbleChatManager.saveMessages(context, listOf(message("old")))
        BubbleChatManager.clearMessages(context)
        BubbleChatManager.addMessage(context, message("after-clear"))
        assertTrue(BubbleChatManager.awaitInitialized(context))
        assertEquals(listOf("after-clear"), db.chatDao().getAllMessagesSync().map { it.id })
    }
    @Test fun restoreInvalidatesQueuedOldSnapshotsAndStaleEditorChanges() = runBlocking {
        val old = message("before-restore")
        BubbleChatManager.saveMessages(context, listOf(old))
        assertTrue(BubbleChatManager.awaitInitialized(context))
        val oldGeneration = BubbleChatManager.generation
        com.focusbyrj.app.util.backup.MaintenanceGate.exclusively {
            db.chatDao().syncAllMessages(listOf(message("restored").toEntity()))
            BubbleChatManager.addMessage(context, message("queued-old"))
            BubbleChatManager.reloadAfterRestore(context)
        }
        BubbleChatManager.applyMessageChanges(context, listOf(old), listOf(old.copy(text = "stale edit")),
            expectedGeneration = oldGeneration)
        assertTrue(BubbleChatManager.awaitInitialized(context))
        assertEquals(listOf("restored"), db.chatDao().getAllMessagesSync().map { it.id })
        assertEquals(listOf("restored"), BubbleChatManager.messagesFlow.value.map { it.id })
    }

}
