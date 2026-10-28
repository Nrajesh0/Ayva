package com.focusbyrj.app.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.util.assistant.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FocusedChatIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    @org.junit.Before fun clearRotation() { context.getSharedPreferences("ayva_content_rotation", 0).edit().clear().commit() }
    @Test fun anagramHintsAnswersResumeAndExhaustionRemainCoherent() {
        ChatModeStore.set(context, ChatMode.CHAT)
        val state = AssistantSession()
        fun say(text: String) = FocusedChat.answer(context, state, text)!!.formattedText
        assertTrue(say("play anagrams").contains("Unscramble"))
        assertTrue(say("hint").contains("orbiting"))
        assertTrue(say("planet").contains("Correct"))
        assertTrue(say("planet").contains("finished"))
        say("next"); say("pause")
        assertTrue(say("resume activity").contains("Unscramble"))
        assertTrue(say("garden").contains("Correct"))
        repeat(ExperienceContent.puzzles.size) { say("next") }
        assertTrue(say("next").contains("end of this puzzle collection"))
        assertTrue(say("restart").contains("Unscramble"))
        say("harder"); say("longer")
        assertTrue(state.featureState!!.getJSONObject("focusedChat").getBoolean("hard"))
        assertEquals(15, state.featureState!!.getJSONObject("focusedChat").getInt("limit"))
        say("next")
        assertTrue(state.featureState!!.getJSONObject("focusedChat").getString("word").length >= 7)
        say("pause")
        assertTrue(say("stop").contains("Finished"))
        assertNull(state.featureState?.optJSONObject("focusedChat"))
    }
    @Test fun oldButtonsCannotAdvanceANewRoundAndRelatedFactsStayInTheirTrail() {
        ChatModeStore.set(context, ChatMode.CHAT)
        val state = AssistantSession()
        val first = FocusedChat.answer(context, state, "play anagrams")!!
        val oldHint = (first.actions.first() as TalkAction.AskQuery).query
        assertTrue(FocusedChat.answer(context, state, oldHint)!!.formattedText.contains("orbiting"))
        assertTrue(FocusedChat.answer(context, state, oldHint)!!.formattedText.contains("expired"))
        FocusedChat.answer(context, state, "explore facts")
        repeat(8) { FocusedChat.answer(context, state, "related fact") }
        val id = state.featureState!!.getJSONObject("focusedChat").getString("fact")
        assertEquals("space", ExperienceContent.facts.first { it.id == id }.category)
        assertTrue(ExperienceContent.chainByLetter.values.flatten().containsAll(ExperienceContent.chainWords))
        assertTrue(EntertainmentPack.jokes.map { it.id }.distinct().size == EntertainmentPack.jokes.size)
        assertTrue(ExperienceContent.facts.all { f -> f.relatedIds.all { id -> ExperienceContent.facts.any { it.id == id && it.category == f.category } } })
    }
    @Test fun clearedSessionsRotateAndStoryWordingAdvancesTheBranch() {
        ChatModeStore.set(context, ChatMode.CHAT)
        val first = AssistantSession()
        FocusedChat.answer(context, first, "play anagrams")
        val second = AssistantSession()
        FocusedChat.answer(context, second, "play anagrams")
        assertNotEquals(first.featureState!!.getJSONObject("focusedChat").getString("word"), second.featureState!!.getJSONObject("focusedChat").getString("word"))
        val story = AssistantSession()
        FocusedChat.answer(context, story, "play a tiny story")
        assertTrue(FocusedChat.answer(context, story, "I want to carry the lantern home")!!.formattedText.contains("tiny map"))
        assertTrue(FocusedChat.answer(context, story, "keep the lantern")!!.formattedText.contains("The end"))
        val nextStory = FocusedChat.answer(context, AssistantSession(), "play a tiny story")!!.formattedText
        assertTrue(nextStory.contains("travelling garden"))
        assertFalse(ChatExperiences.capabilities.any { it.group == "Talk a little" })
        val riddle = AssistantSocial.answer(context, AssistantSession(), "give me a riddle")!!.formattedText
        assertNotEquals(riddle, AssistantSocial.answer(context, AssistantSession(), "give me a riddle")!!.formattedText)
    }
    @Test fun factsHaveSpecificFollowupsAndDeclineUnsupportedQuestions() {
        ChatModeStore.set(context, ChatMode.CHAT)
        val state = AssistantSession()
        fun say(text: String) = FocusedChat.answer(context, state, text)!!.formattedText
        assertTrue(say("explore facts").contains("Venus"))
        assertTrue(say("why").contains("243"))
        assertTrue(say("example").contains("Earth"))
        assertTrue(say("source").contains("nasa.gov"))
        assertTrue(say("do aliens live there").contains("don't have a reliable"))
        assertTrue(say("nature").contains("octopus"))
        assertNull(FocusedChat.answer(context, state, "add task buy milk"))
        assertTrue(say("resume activity").contains("octopus"))
        ChatModeStore.set(context, ChatMode.PLAN)
        assertNull(FocusedChat.answer(context, state, "explore facts"))
        ChatModeStore.set(context, ChatMode.CHAT)
    }
    @Test fun storiesEndAndAllPackReferencesAreValid() {
        for (story in ExperienceContent.stories) {
            assertTrue(story.nodes.containsKey("start"))
            val reachable = mutableSetOf<String>()
            fun visit(node: String) { if (!reachable.add(node)) return; story.nodes.getValue(node).choices.values.forEach(::visit) }
            visit("start")
            assertEquals(story.nodes.keys, reachable)
            assertTrue(story.nodes.values.count { it.choices.isEmpty() } >= 2)
        }
        assertEquals(30, ExperienceContent.facts.size)
        assertEquals(80, ExperienceContent.lines.values.sumOf { it.size })
        assertEquals(52, EntertainmentPack.jokes.size)
        assertEquals(36, EntertainmentPack.riddles.size)
        assertEquals(ExperienceContent.facts.size, ExperienceContent.facts.map { it.id }.distinct().size)
        assertTrue(ExperienceContent.facts.all { it.source.contains("https://") && it.why != it.claim && it.example != it.why })
        ChatModeStore.set(context, ChatMode.CHAT)
        val state = AssistantSession()
        FocusedChat.answer(context, state, "play a tiny story")
        FocusedChat.answer(context, state, "first")
        assertTrue(FocusedChat.answer(context, state, "first")!!.formattedText.contains("The end"))
        assertTrue(FocusedChat.answer(context, state, "next")!!.formattedText.contains("travelling garden"))
    }
}
