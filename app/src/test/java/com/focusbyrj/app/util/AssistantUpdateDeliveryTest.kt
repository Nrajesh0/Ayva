package com.focusbyrj.app.util

import android.app.Application
import androidx.room.Room
import com.focusbyrj.app.data.chat.AyvaChatDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31,34],application=Application::class)
class AssistantUpdateDeliveryTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var db:AyvaChatDatabase
    private val jobs get()=ReflectionHelpers.getStaticField<CoroutineScope>(BubbleChatManager::class.java,"scope").coroutineContext[Job]!!
    private fun resetMemory() {
        ReflectionHelpers.getStaticField<java.util.concurrent.atomic.AtomicBoolean>(BubbleChatManager::class.java,"initLatch").set(false)
        ReflectionHelpers.setStaticField(BubbleChatManager::class.java,"isInitialized",false)
        ReflectionHelpers.setStaticField(BubbleChatManager::class.java,"revision",0L)
        ReflectionHelpers.setStaticField(BubbleChatManager::class.java,"persistedRevision",-1L)
        ReflectionHelpers.getStaticField<MutableList<*>>(BubbleChatManager::class.java,"beforeLoad").clear()
        ReflectionHelpers.getStaticField<MutableStateFlow<List<PersistedChatMessage>>>(BubbleChatManager::class.java,"_messagesFlow").value=emptyList()
        ReflectionHelpers.getStaticField<MutableStateFlow<Boolean>>(BubbleChatManager::class.java,"_loaded").value=false
    }
    @Before fun prepare()=runBlocking {
        withTimeout(10_000) { jobs.children.toList().joinAll() }
        resetMemory()
        listOf(AyvaAlertCategory.PREFS_NAME,"bubble_prefs","bubble_chat_prefs").forEach { context.getSharedPreferences(it,0).edit().clear().commit() }
        db=Room.inMemoryDatabaseBuilder(context,AyvaChatDatabase::class.java).build()
        ReflectionHelpers.setStaticField(AyvaChatDatabase::class.java,"INSTANCE",db)
    }
    @After fun cleanup()=runBlocking {
        withTimeout(10_000) { jobs.children.toList().joinAll() }
        db.close();ReflectionHelpers.setStaticField(AyvaChatDatabase::class.java,"INSTANCE",null);resetMemory()
    }
    @Test fun disabledBriefingsNeverEnterChatOrIncreaseUnreadEvenBeforeInitialLoad()=runBlocking {
        AyvaAlertCategory.BRIEFING.setEnabled(context,false)
        assertFalse(BubbleChatManager.addMessage(context,PersistedChatMessage("morning_new","Vocabulary card",false,isMorningBrief=true),true))
        assertTrue(BubbleChatManager.awaitInitialized(context))
        assertTrue(db.chatDao().getAllMessagesSync().isEmpty())
        assertTrue(BubbleChatManager.messagesFlow.value.isEmpty())
        assertEquals(0,BubbleChatManager.getUnreadCount(context))
    }
    @Test fun masterOffBlocksEveryAutomaticStreamButKeepsDirectReplies()=runBlocking {
        AyvaAlertCategory.setUpdatesEnabled(context,false)
        val messages=listOf(
            PersistedChatMessage("morning_new","Morning",false,isMorningBrief=true),
            PersistedChatMessage("evening_new","Evening",false,isEveningBrief=true),
            PersistedChatMessage("streak_prompt_new","Practice",false,isStreakPrompt=true),
            PersistedChatMessage("usage_reminder_new","Take a quick screen break",false),
            PersistedChatMessage("bedtime_new","Past your bedtime",false),
            PersistedChatMessage("auto_reward_new","Quest ready",false,isMysteryBox=true),
            PersistedChatMessage("critical_new","App limit reached",false))
        messages.forEach { assertFalse(BubbleChatManager.addMessage(context,it,true)) }
        BubbleChatManager.ensureWelcomeIfEmpty(context,PersistedChatMessage("welcome_new","Try a drill",false,isWelcome=true))
        assertTrue(BubbleChatManager.awaitInitialized(context))
        assertTrue(db.chatDao().getAllMessagesSync().isEmpty())
        assertTrue(BubbleChatManager.addMessage(context,PersistedChatMessage("direct_reply","Here is the information you requested",false)))
        assertTrue(BubbleChatManager.awaitInitialized(context))
        assertEquals(listOf("direct_reply"),db.chatDao().getAllMessagesSync().map { it.id })
        assertEquals(0,BubbleChatManager.getUnreadCount(context))
    }
    @Test fun switchingOffKeepsHistoryAndDoesNotCreatePhantomUnreadCounts()=runBlocking {
        assertTrue(BubbleChatManager.addMessage(context,PersistedChatMessage("morning_old","Previous briefing",false,isMorningBrief=true),true))
        assertTrue(BubbleChatManager.awaitInitialized(context))
        AyvaAlertCategory.BRIEFING.setEnabled(context,false)
        assertFalse(BubbleChatManager.addMessage(context,PersistedChatMessage("morning_new","New briefing",false,isMorningBrief=true),true))
        assertTrue(BubbleChatManager.awaitInitialized(context))
        assertEquals(listOf("morning_old"),db.chatDao().getAllMessagesSync().map { it.id })
        assertEquals(1,BubbleChatManager.getUnreadCount(context))
        BubbleChatManager.clearUnread(context)
        assertFalse(BubbleChatManager.addMessage(context,PersistedChatMessage("evening_new","New briefing",false,isEveningBrief=true),true))
        assertEquals(0,BubbleChatManager.getUnreadCount(context))
    }
    @Test fun practiceControlsStayConsistentAndMasterPauseKeepsIndividualChoices() {
        AyvaAlertCategory.DRILL_PRACTICE.setEnabled(context,false)
        val prefs=context.getSharedPreferences("bubble_prefs",0)
        assertFalse(prefs.getBoolean("streak_notification_enabled",true))
        AyvaAlertCategory.setUpdatesEnabled(context,false)
        AyvaAlertCategory.setUpdatesEnabled(context,true)
        assertFalse(AyvaAlertCategory.DRILL_PRACTICE.isEnabled(context))
        AyvaAlertCategory.DRILL_PRACTICE.setEnabled(context,true)
        assertTrue(prefs.getBoolean("streak_notification_enabled",false))
        assertTrue(prefs.getBoolean("random_drills_notification_enabled",false))
        prefs.edit().putBoolean("vacation_mode",true).commit()
        assertFalse(AssistantUpdateDelivery.allows(context,PersistedChatMessage("streak_prompt_new","Practice",false,isStreakPrompt=true)))
    }
    @Test fun structuredAutomaticKindsTakePriorityOverKeywords() {
        assertEquals(AyvaAlertCategory.GENERAL,AssistantUpdateDelivery.category(PersistedChatMessage("welcome_new","Welcome! Try a drill",false,isWelcome=true)))
        assertEquals(AyvaAlertCategory.REWARDS,AssistantUpdateDelivery.category(PersistedChatMessage("auto_reward_new","Drill quest completed",false,isMysteryBox=true)))
        assertEquals(AyvaAlertCategory.BRIEFING,AssistantUpdateDelivery.category(PersistedChatMessage("morning_new","Quiz vocabulary",false,isVocabBrief=true)))
        assertEquals(AyvaAlertCategory.BEDTIME_SLEEP,AssistantUpdateDelivery.category(PersistedChatMessage("usage_reminder_bedtime_sleep_1","A quieter moment would help",false)))
        assertEquals(AyvaAlertCategory.EXCESSIVE_USAGE,AssistantUpdateDelivery.category(PersistedChatMessage("usage_reminder_excessive_usage_1","Time for fresh air",false)))
    }
    @Test fun aProducerFromAnOlderRestoredGenerationCannotAppendOrIncreaseUnread()=runBlocking {
        val stale=BubbleChatManager.generation-1
        assertFalse(BubbleChatManager.addMessage(context,PersistedChatMessage("morning_stale","Old vocabulary",false,isMorningBrief=true),true,expectedGeneration=stale))
        assertTrue(BubbleChatManager.awaitInitialized(context))
        assertTrue(db.chatDao().getAllMessagesSync().isEmpty())
        assertEquals(0,BubbleChatManager.getUnreadCount(context))
    }
}
