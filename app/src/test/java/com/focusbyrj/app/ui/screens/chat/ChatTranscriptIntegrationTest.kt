package com.focusbyrj.app.ui.screens.chat

import android.app.Application
import androidx.activity.ComponentActivity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.chat.AyvaChatDatabase
import com.focusbyrj.app.data.chat.toEntity
import com.focusbyrj.app.ui.screens.ChatInterface
import com.focusbyrj.app.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatTranscriptIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context
    private lateinit var chat: AyvaChatDatabase
    private lateinit var focus: FocusDatabase
    private val queries = Executors.newSingleThreadExecutor()
    private val transactions = Executors.newSingleThreadExecutor()
    private val held = AtomicBoolean(false)
    private val entered = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private var render by mutableStateOf(true)
    private val jobs get() = ReflectionHelpers.getStaticField<CoroutineScope>(BubbleChatManager::class.java, "scope").coroutineContext[Job]!!

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
        val base = RuntimeEnvironment.getApplication()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        focus = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(focus)) }
        context = object : ContextWrapper(compose.activity) { override fun getApplicationContext(): Context = app }
        for (name in listOf("bubble_prefs", "bubble_chat_prefs", "ayva_composer")) context.getSharedPreferences(name, 0).edit().clear().commit()
        FocusEconomyManager.init(context); FocusStatsManager.init(context)
        AptitudeManager.init(context); DailyQuestManager.init(context)
        chat = Room.inMemoryDatabaseBuilder(base, AyvaChatDatabase::class.java)
            .setQueryExecutor { action -> queries.execute {
                if (held.get()) { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
                action.run()
            } }.setTransactionExecutor(transactions).build()
        ReflectionHelpers.setStaticField(AyvaChatDatabase::class.java, "INSTANCE", chat)
        chat.chatDao().insertMessages(listOf(
            PersistedChatMessage("stored-user", "Earlier message", true, firstViewedTimestamp = System.currentTimeMillis()).toEntity(),
            PersistedChatMessage("stored-reply", "Existing response", false, firstViewedTimestamp = System.currentTimeMillis()).toEntity()))
    }
    @After fun cleanup() = runBlocking {
        held.set(false); release.countDown()
        compose.runOnIdle { render = false }
        compose.waitForIdle()
        withTimeout(10_000) { jobs.children.toList().joinAll() }
        chat.close(); focus.close()
        ReflectionHelpers.setStaticField(AyvaChatDatabase::class.java, "INSTANCE", null)
        resetMemory()
        queries.shutdownNow(); transactions.shutdownNow()
        Unit
    }
    @Test fun actualChatRetainsColdHistoryAndCanSaveAfterAnIdenticalRestore() {
        held.set(true)
        compose.setContent { if (render) CompositionLocalProvider(LocalContext provides context) { MaterialTheme { ChatInterface() } } }
        compose.waitForIdle()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        BubbleChatManager.addMessage(context, PersistedChatMessage("during-load", "New alert", false), incrementBadge = true)
        held.set(false); release.countDown()
        compose.waitUntil(10_000) { BubbleChatManager.loaded.value && BubbleChatManager.messagesFlow.value.size >= 3 }
        compose.onNodeWithText("Earlier message").assertExists()
        compose.onNodeWithText("Existing response").assertExists()
        runBlocking {
            assertTrue(BubbleChatManager.awaitInitialized(context))
            // Identical content must still publish a new editor generation.
            com.focusbyrj.app.util.backup.MaintenanceGate.exclusively { BubbleChatManager.reloadAfterRestore(context) }
        }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("hello")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(10_000) { BubbleChatManager.messagesFlow.value.any { it.isUser && it.text == "hello" } }
        runBlocking {
            assertTrue(BubbleChatManager.awaitInitialized(context))
            val rows = chat.chatDao().getAllMessagesSync()
            assertTrue(rows.any { it.text == "hello" && it.isUser })
            assertTrue(rows.map { it.id }.containsAll(listOf("stored-user", "stored-reply", "during-load")))
        }
    }
}
