package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.util.assistant.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Complete turns through the real coordinator: context, authority and planning isolation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GeneralDialogueIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        AssistantSessionStore(context).clear(); ConversationPlanStore.clearAll(context)
        for (name in listOf("focus_prefs", "bubble_prefs", "ayva_personalization")) context.getSharedPreferences(name, 0).edit().clear().commit()
        ChatModeStore.set(context, ChatMode.CHAT)
    }
    @After fun cleanup() { AssistantSessionStore(context).clear(); ConversationPlanStore.clearAll(context); ChatModeStore.set(context, ChatMode.CHAT); db.close() }
    private suspend fun say(text: String): String = (OfflineAssistant.respond(text, context) ?: error("Unhandled: $text")).formattedText
    private fun frame() = AssistantSessionStore(context).load().featureState!!.getJSONObject("generalDialogue")

    @Test fun preferencesCorrectionsAndFollowupsStayConversationalAcrossReloads() = runBlocking {
        say("/talk I prefer quiet evenings")
        say("/talk Crowds wear me out")
        assertTrue(say("/talk What could I try?").contains("reading", true))
        assertTrue(say("/talk Why that?").contains("crowds", true))
        say("/talk Actually outdoors")
        assertTrue(say("/talk What could I try?").contains("walk", true))
        say("/talk Actually indoors")
        val recap = say("/talk What do we have so far?")
        assertTrue(recap, recap.contains("indoors")); assertFalse(recap, recap.contains("outdoors"))
        assertTrue(recap, recap.contains("avoid crowds")); assertTrue(recap, recap.contains("quiet"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        assertNull(ConversationPlanStore(context).active())
    }

    @Test fun knowledgeFollowupsReferToAssistantAnswerAndUnknownQuestionsDoNotCreateTasks() = runBlocking {
        say("/talk explain active recall")
        assertTrue(say("/talk give me an example").contains("close", true))
        assertEquals("Try remembering first, then check.", say("/talk shorter"))
        assertTrue(say("/talk who won the latest championship?").contains("live information"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun newEngineCannotConsumePendingNativeInputOrChangeAPlan() = runBlocking {
        say("/plan trip to Goa on 26 July 2029")
        say("/plan pack clothes")
        val before = ConversationPlanStore(context).active()!!.json().toString()
        say("/talk I prefer quiet evenings")
        say("/talk what could I try?")
        assertEquals(before, ConversationPlanStore(context).active()!!.json().toString())
        say("/create")
        val state = AssistantSessionStore(context).load()
        assertNotNull(state.expected)
        val response = GeneralConversation.answer(context, state, "I prefer quiet evenings")
        assertNull(response)
        assertNotNull(state.expected)
        assertEquals(before, ConversationPlanStore(context).active()!!.json().toString())
    }

    @Test fun extendedRecallDoesNotReviveExpiredActionAuthorityAndClearErasesIt() = runBlocking {
        say("/talk I prefer quiet evenings")
        val state = AssistantSessionStore(context).load()
        state.featureState!!.getJSONObject("generalDialogue").put("at", System.currentTimeMillis() - 3_600_000)
        state.taskId = 123L; state.referenceAt = System.currentTimeMillis() - 3_600_000
        AssistantSessionStore(context).save(state)
        assertTrue(say("/talk what do we have so far?").contains("quiet"))
        assertNull(AssistantSessionStore(context).load().taskId)
        say("/talk forget this conversation")
        assertNull(AssistantSessionStore(context).load().featureState?.optJSONObject("generalDialogue"))
    }

    @Test fun unsupportedCorrectionsDoNotEraseFactsAndEngineDoesNotMutateInput() {
        val style = GeneralDialogue.Style(true, false, false)
        val first = GeneralDialogue.answer("I prefer quiet evenings", null, style)!!
        val original = first.frame.toString()
        val correction = GeneralDialogue.answer("actually something different", first.frame, style)!!
        assertEquals(original, first.frame.toString())
        assertTrue(correction.text.contains("Which earlier detail"))
        val recap = GeneralDialogue.answer("what do we have so far", JSONObject(correction.frame.toString()), style)!!
        assertTrue(recap.text.contains("quiet"))
    }

    @Test fun tentativeIdeasRejectionsAndNegationDoNotBecomeFirmPreferences() = runBlocking {
        say("/talk I prefer quiet evenings indoors")
        say("/talk maybe outdoors")
        val recap = say("/talk what do we have so far?")
        assertTrue(recap, recap.contains("indoors")); assertTrue(recap, recap.contains("outdoors (tentative)"))
        assertTrue(say("/talk what could I try?").contains("reading"))
        assertTrue(say("/talk I don't like reading").contains("album"))
        assertTrue(say("/talk why that?").contains("music"))
        val noOutdoorGuess = say("/talk indoors or outdoors")
        assertTrue(noOutdoorGuess, noOutdoorGuess.contains("both"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun topicReflectionDoesNotRepeatAndLatestExplanationMatchesLatestReply() = runBlocking {
        val replies = listOf(say("/talk I'm worried about studying"), say("/talk I keep forgetting things"),
            say("/talk I keep forgetting things"), say("/talk I keep forgetting things"))
        assertEquals(replies.size, replies.distinct().size)
        assertTrue(say("/talk why do you say that?").contains("recall"))
        say("/talk just listen")
        val listening = say("/talk I feel tired")
        assertFalse(listening, listening.contains("?"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun questionsAndOtherPeopleCannotChangePreferencesAndTentativeChoicesCanBeResolved() = runBlocking {
        say("/talk I prefer quiet evenings indoors")
        say("/talk why outdoors?")
        assertTrue(say("/talk what do we have so far?").contains("setting: indoors"))
        say("/talk My friend prefers outdoors")
        assertTrue(say("/talk what do we have so far?").contains("setting: indoors"))
        say("/talk I don't want outdoors")
        assertTrue(say("/talk what do we have so far?").contains("setting: indoors"))
        say("/talk maybe outdoors")
        say("/talk use that preference")
        val confirmed = say("/talk what do we have so far?")
        assertTrue(confirmed, confirmed.contains("setting: outdoors"))
        assertFalse(confirmed, confirmed.contains("tentative"))
        assertTrue(confirmed, confirmed.contains("quiet"))
        say("/talk maybe indoors")
        say("/talk forget that possibility")
        assertFalse(say("/talk what do we have so far?").contains("indoors"))
        say("/talk who won the latest championship?")
        assertTrue(say("/talk why that?").contains("Which detail"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun focusedActivitySurvivesExplicitTaskInterruptionWithoutCreatingTasksFromAnswers() = runBlocking {
        assertTrue(say("explore Ayva").contains("Word chain"))
        assertTrue(say("play anagrams").contains("Unscramble"))
        assertTrue(say("planet").contains("Correct"))
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        say("buy apples tomorrow")
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
        assertTrue(say("resume anagrams").contains("Correct"))
        assertTrue(say("next").contains("Unscramble"))
        assertTrue(say("let's talk about love").contains("don't have a reliable conversation pack"))
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }

    @Test fun discussionPauseInterruptionResumeAndChoicesKeepTheirOwner() = runBlocking {
        say("/talk I prefer quiet evenings")
        say("/talk crowds wear me out")
        val recommendation = say("/talk Could you possibly recommend something relaxing?")
        assertTrue(recommendation, recommendation.contains("reading", true))
        say("/talk why that?")
        say("/talk actually outdoors")
        say("/talk what could I try?")
        assertTrue(say("pause discussion").contains("Paused"))
        say("buy apples tomorrow")
        say("resume discussion")
        assertTrue(say("/talk what do we have so far?").contains("outdoors"))
        say("/talk no advice")
        say("/talk stop")
        assertTrue(say("resume discussion").contains("isn't a recent"))
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun readingTasteCorrectionAndTentativePreferenceSurviveAFullSession() = runBlocking {
        say("I like reading")
        say("fantasy")
        say("tell me more")
        say("give me an example")
        assertTrue(say("I don't like fantasy").contains("set world aside"))
        say("maybe characters")
        assertTrue(say("recap").contains("tentative"))
        say("pause activity")
        say("resume activity")
        assertTrue(say("recap").contains("world: avoid"))
        say("stop")
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun stampedActivityButtonsUseTheirOwnRouteWithoutChangingChatModeOrTasks() = runBlocking {
        val response = OfflineAssistant.respond("play anagrams", context)!!
        val hint = (response.actions.first() as TalkAction.AskQuery).query
        assertTrue(say(hint).contains("orbiting"))
        assertTrue(say(hint).contains("expired"))
        assertEquals(ChatMode.CHAT, ChatModeStore.get(context))
        say("play anagrams")
        say("forget this conversation")
        assertTrue(say("resume anagrams").contains("isn't a recent"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun offeredStudyAnswerUpdatesTheRelevantDimension() = runBlocking {
        say("/talk I am stressed about studying")
        val answer = say("/talk remembering it")
        assertTrue(answer.contains("Forgetting"))
        assertTrue(say("/talk what do we have so far").contains("recalling material"))
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }
    @Test fun riddlePauseAndExplicitTaskInterruptionResumeWithoutAdvancingTheGuess() = runBlocking {
        say("give me a riddle")
        val before = AssistantSessionStore(context).load().featureState!!.getJSONObject("social").optInt("tries")
        assertTrue(say("pause activity").contains("Paused"))
        assertTrue(say("resume activity").contains("keys"))
        assertEquals(before, AssistantSessionStore(context).load().featureState!!.getJSONObject("social").optInt("tries"))
        say("buy apples tomorrow")
        assertTrue(say("resume riddles").contains("keys"))
        assertTrue(say("piano").contains("You got it"))
        assertTrue(say("next").contains("wetter"))
        assertTrue(say("skip").contains("face"))
        assertTrue(say("reveal").contains("clock"))
        assertTrue(say("stop").contains("Finished"))
        assertTrue(say("resume riddles").contains("isn't a recent matching"))
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
}
