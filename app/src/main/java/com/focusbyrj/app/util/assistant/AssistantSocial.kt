package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.util.AyvaTalkEngine
import com.focusbyrj.app.util.OfflineNluEngine
import com.focusbyrj.app.util.TalkAction
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Finite conversation packs. They return speech/navigation proposals, never domain mutations. */
internal object AssistantSocial {
    private data class Riddle(val question: String, val answers: Set<String>, val hint: String)
    private val jokes = listOf(
        "My calendar tried stand-up comedy. Its timing was excellent, but every joke was dated.",
        "I asked a pencil for advice. It said, ‘You can always draw a new line.’ Very pointed of it.",
        "The cat joined the planning meeting, knocked over a pen, and called it a disruptive strategy.",
        "A bookmark is a very small friend who remembers where you left off and never asks why you stopped.",
        "My to-do list requested a performance review. I told it we both had unfinished business.",
        "Two tabs walked into a browser. Neither remembered why they were there.",
        "I tried to organize a hide-and-seek tournament. Good players are hard to find.",
        "The spreadsheet went on holiday. It needed a change of cells."
    )
    private val jokeExplanations = listOf(
        "‘Timing’ suits both comedy and a calendar. ‘Dated’ can mean marked with dates or no longer fresh.",
        "A pencil has a point; ‘pointed’ can also describe a sharp or direct remark.",
        "‘Disruptive’ is a business buzzword, while a cat literally disrupts the meeting by knocking things over.",
        "The joke treats a bookmark like a patient friend: its job is simply to hold your place.",
        "‘Unfinished business’ means unresolved matters, and a to-do list literally contains unfinished work.",
        "It plays on opening browser tabs and then forgetting why, a familiar small distraction.",
        "‘Hard to find’ means difficult to recruit and, in hide-and-seek, literally hidden.",
        "A spreadsheet contains cells. ‘A change of cells’ echoes ‘a change of scene’ for a holiday."
    )
    private val riddles = listOf(
        Riddle("What has keys but can't open a door?", setOf("piano", "a piano", "keyboard", "a keyboard"), "Think music or typing."),
        Riddle("What gets wetter while it dries?", setOf("towel", "a towel"), "You use it after a shower."),
        Riddle("What has a face and hands but no arms?", setOf("clock", "a clock", "watch", "a watch"), "It helps you keep time."),
        Riddle("What can you break without touching it?", setOf("promise", "a promise", "silence", "the silence"), "It might involve your word.")
    )
    private val chain = listOf("apple", "eagle", "earth", "hat", "tiger", "river", "rain", "night", "tree", "ear", "rabbit", "table", "egg", "garden", "nest", "tea", "ant", "train", "note", "echo", "owl", "leaf", "fish", "house", "elephant", "turtle", "engine", "event", "time", "energy", "yellow", "window", "water", "road", "door", "rose", "east", "tail", "lamp", "paper", "rock", "kite", "exit", "travel", "lake", "easy", "young", "green")
    private data class Interest(val aliases: Set<String>, val opening: String, val continuation: String, val reflection: String)
    private val interests = mapOf(
        "reading" to Interest(setOf("reading", "books", "novels"), "Books can be an escape, a puzzle, or another point of view. What kind of reading do you enjoy?", "Characters, language and worldbuilding can make very different books memorable. Which matters most to you?", "One useful way to explore a story is to ask what changed for a character, and what stayed the same. I can chat about that approach, but I don't know the plot of every book."),
        "music" to Interest(setOf("music", "songs", "singing"), "With music, is it the rhythm, the melody, the words, or the atmosphere that draws you in?", "A familiar track can sound different when you follow just one instrument or voice. Do you listen closely, or enjoy music in the background?", "You could compare two versions of a favourite song and notice what changed in pace or arrangement. That's an optional listening idea, not a task I've saved."),
        "cooking" to Interest(setOf("cooking", "baking", "food"), "Cooking can be practical and creative at once. Do you like following a recipe, or experimenting?", "There's a difference between enjoying the process and enjoying the result. Which part is more satisfying for you?", "A small cooking challenge could be describing a favourite dish using only three words. I can play along with that kind of word game; I don't have a reliable recipe for every dish."),
        "films" to Interest(setOf("films", "movies", "cinema"), "What makes a film stick with you: its characters, atmosphere, story, or something else?", "A quiet scene can matter as much as a big twist. Do you prefer stories that resolve everything, or leave room to wonder?", "We can discuss what you enjoyed in a film using the details you supply. I won't invent a plot, review or current release information."),
        "coding" to Interest(setOf("coding", "programming", "software"), "Building something, solving a puzzle, or understanding how it works: which part of coding interests you most?", "A small project can be a useful way to turn an idea into something concrete. What would you like it to do?", "We can turn your own project steps into an unsaved plan if you ask. I can't execute arbitrary code or design every system from a brief description."),
        "walking" to Interest(setOf("walking", "walks", "nature", "photography"), "Is it the change of scenery, noticing details, or the quiet that you enjoy?", "One small observation can make a familiar place feel different: a sound, a colour, a shape. What tends to catch your attention?", "That can also become a tiny creative exercise: describe a place without naming it. We can keep it playful; I haven't logged an activity or changed a habit.")
    )
    private fun frame(state: AssistantSession) = state.featureState?.optJSONObject("social")?.takeIf { AssistantContext.live(it.optLong("at")) }
    private fun features(state: AssistantSession) = state.featureState ?: JSONObject().also { state.featureState = it }

    fun answer(context: Context, state: AssistantSession, input: String, original: String = input, appMode: Boolean = false): AyvaTalkEngine.TalkResponse? {
        var text = input.lowercase(java.util.Locale.ROOT).trim().trimEnd('.', '!', '?')
        if (appMode && !AssistantDiscussion.hasContext(state) && !AssistantDiscussion.requestsResume(text) && !OfflineNluEngine.isConversationalStatement(text) &&
            !Regex("^(?:tell me (?:a |a short )?(?:joke|story)|give me a riddle|play (?:a game|word chain)|just listen|be serious|less jokes|no advice|i (?:like|enjoy|had)|let'?s (?:talk|think)|(?:can|could) we (?:talk|think)|talk (?:about|through)|help me (?:think|decide))\\b").containsMatchIn(text)) return null
        val previous = frame(state)
        val active = state.topic == "conversation" && previous != null
        if (text.startsWith("/ayva social ")) {
            val parts = text.split(' ')
            if (!active || parts.size != 5 || parts[2] != previous!!.optString("id") || parts[3].toIntOrNull() != previous.optInt("revision")) return simple("That conversation choice has expired. Ask for a fresh joke, game or plan.")
            text = when (parts[4]) {
                "joke" -> "tell me a joke"; "riddle" -> "give me a riddle"; "listen" -> "just listen"; "story" -> "tell me a story"
                "next" -> "another one"; "answer" -> "answer"; "stop" -> "stop"; "open" -> "open it"; "knock" -> "knock"
                "return" -> "return the book"; "keep" -> "keep the book"
                else -> return simple("That conversation choice is unavailable.")
            }
        }
        if (Regex("(?i)^i (?:am going to|plan to|will) (?:kill myself|end my life|hurt myself now)\\b").containsMatchIn(original.trim()))
            return speak(state, "support", "I'm concerned about your immediate safety. Please contact local emergency services or a crisis support service now, and reach out to someone you trust who can stay with you. Move away from anything you could use to hurt yourself. Are you in immediate danger?", tone = "serious")
        if (active && text in setOf("stop", "stop the game", "stop the story", "let's change the subject", "lets change the subject", "change the subject", "just chat"))
            return speak(state, "chat", "Okay, we can leave that there. What's on your mind?")
        if (text in setOf("just listen", "no advice", "i don't want advice", "no advice just listen")) {
            val turn = AssistantDiscussion.answer(state, "just listen")!!
            return speak(state, "discussion", turn.text, tone = "serious", extra = JSONObject().put("discussion", turn.frame))
        }
        if (text in setOf("be serious", "less jokes", "no jokes"))
            return speak(state, "chat", "Understood. I'll keep this conversation straightforward.", tone = "serious")
        if (text in setOf("be playful", "lighten up")) return speak(state, "chat", "A little lighter, then. What are we chatting about?", tone = "playful")
        // A past-tense conversational remark cannot complete an older, unrelated task.
        if (state.topic == "conversation" && Regex("(?i)^i (?:(?:finally|just|have) )?(?:finished|completed|did) it[.!]?$" ).matches(original.trim()))
            return speak(state, "celebration", "That sounds like a satisfying moment. What did you finish? I haven't changed any saved task.")
        if (active && previous!!.optString("family") == "joke" && text in setOf("why", "why is that funny", "explain that", "i don't get it", "tell me more")) {
            val seen = previous.optJSONArray("jokes")
            val index = if (seen != null && seen.length() > 0) seen.getInt(seen.length() - 1) else 0
            return speak(state, "joke", jokeExplanations[index.coerceIn(jokeExplanations.indices)], choices = listOf("next" to "Another joke"))
        }
        if (text in setOf("joke", "tell me a joke", "make me laugh", "no advice, just a joke", "no advice just a joke") || (active && previous!!.optString("family") == "joke" || text == "back to the joke" && previous?.has("jokes") == true) && text in setOf("another one", "another joke", "back to the joke", "that wasn't funny", "not funny")) {
            val seen = previous?.optJSONArray("jokes")?.let { a -> (0 until a.length()).map { a.getInt(it) } }.orEmpty()
            val next = ((seen.lastOrNull() ?: -1) + 1) % jokes.size
            val prefix = if (text in setOf("that wasn't funny", "not funny")) "Fair. A different direction: " else ""
            return speak(state, "joke", prefix + jokes[next], choices = listOf("next" to "Another joke", "riddle" to "Try a riddle"), extra = JSONObject().put("jokes", JSONArray((seen + next).takeLast(8))))
        }
        if (text in setOf("give me a riddle", "riddle", "let's play a game", "lets play a game", "play a game", "just distract me", "distract me") || active && previous!!.optString("family") == "riddle_done" && text == "another one") {
            val n = ((previous?.optInt("riddle", -1) ?: -1) + 1) % riddles.size
            return speak(state, "riddle", riddles[n].question + " You can guess, ask for a hint, or stop.", choices = listOf("answer" to "Show answer", "stop" to "Stop game"), extra = JSONObject().put("riddle", n).put("tries", 0))
        }
        if (active && previous!!.optString("family") == "riddle" && !newRequest(text)) {
            val n = previous.optInt("riddle").coerceIn(riddles.indices); val riddle = riddles[n]
            val tries = previous.optInt("tries") + 1
            val correct = text in riddle.answers
            val reveal = text in setOf("answer", "give up", "i give up", "tell me", "show answer") || tries >= 3
            val body = when { correct -> "You got it: ${riddle.answers.first()}."; reveal -> "One answer is ${riddle.answers.first()}. ${riddle.hint}"; text in setOf("hint", "help") -> riddle.hint; else -> "That isn't one of this riddle's stored answers. ${riddle.hint} You can try again or ask for the answer." }
            return speak(state, if (correct || reveal) "riddle_done" else "riddle", body, choices = if (correct || reveal) listOf("next" to "Another riddle") else listOf("answer" to "Show answer"), extra = JSONObject().put("riddle", n).put("tries", tries))
        }
        if (text in setOf("play word chain", "word chain")) return speak(state, "chain", "Word chain, up to five rounds. Start with the last letter of my word: **cat**. Your word should start with T. Say ‘stop’ to finish.", extra = JSONObject().put("last", "cat").put("round", 0).put("used", JSONArray().put("cat")))
        if (active && previous!!.optString("family") == "chain" && text.matches(Regex("[a-z]{2,24}")) && !newRequest(text)) {
            val last = previous.optString("last"); val used = previous.optJSONArray("used")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            if (text.first() != last.last() || text in used || AssistantDictionaryStore.lookup(context, text).isEmpty()) return speak(state, "chain", "Choose a new dictionary word starting with ${last.last().uppercaseChar()}. No repeats in this game.", extra = previous)
            val next = chain.firstOrNull { it.first() == text.last() && it !in used && it != text }
            val round = previous.optInt("round") + 1
            if (next == null || round >= 5) return speak(state, "chat", "Nice chain. ${if (next == null) "I don't have an unused reply for that letter in this small game pack." else "That's our five-round finish."} Thanks for playing.")
            return speak(state, "chain", "**$next**. Your turn: ${next.last().uppercaseChar()}.", extra = JSONObject().put("last", next).put("round", round).put("used", JSONArray((used + text + next).takeLast(12))))
        }
        if (text in setOf("tell me a story", "short story", "tell me a short story")) {
            val run = ((previous?.optInt("storyRun", -1) ?: -1) + 1) % 2
            val opening = if (run == 0) "A cat found a tiny door beneath the sofa. A sign read: ‘Lost things, second chances.’ Would you open it or knock?" else "At midnight, a clockmaker heard knocking from an unused drawer. A small cat-shaped handle glowed in the dark. Open it or knock back?"
            return speak(state, "story", opening, choices = listOf("open" to "Open it", "knock" to "Knock"), extra = JSONObject().put("stage", 0).put("storyRun", run))
        }
        if (active && previous!!.optString("family") == "story" && text in setOf("open it", "knock", "keep the book", "return the book")) {
            if (previous.optInt("stage") == 0 && text in setOf("open it", "knock")) return speak(state, "story", "${if (text == "knock") "Someone knocked back, then opened the door." else "The door opened into a library of misplaced things."} On a table lay a book titled ‘The Afternoon You Forgot to Rest’. Keep it or return it?", choices = listOf("keep" to "Keep it", "return" to "Return it"), extra = JSONObject().put("stage", 1))
            if (previous.optInt("stage") == 1 && text in setOf("keep the book", "return the book")) return speak(state, "story_done", if (text == "keep the book") "The cat carried the book upstairs. Every page was blank except the last: ‘You may begin again here.’ Then it took a nap on the ending." else "The cat returned the book. In its place appeared a small cushion and a note: ‘Some lost things find their own way home.’ The cat tested the cushion. The end.", choices = listOf("story" to "Another story", "joke" to "A joke"))
        }
        if (text in setOf("hi", "hello", "hey", "howdy", "hi ayva", "hey ayva", "hello ayva", "good morning", "good afternoon", "good evening", "how are you", "how are you doing", "what's up", "whats up", "let's chat", "lets chat", "talk to me")) {
            val name = context.getSharedPreferences("ayva_personalization", 0).getString("name", null)?.let { ", $it" }.orEmpty()
            val variants = listOf("Hi$name. What's on your mind?", "Hello$name. How's your day going?", "Hey$name. We can chat, play a quick game, or work something out together.")
            val n = ((previous?.optInt("greeting", -1) ?: -1) + 1) % variants.size
            return speak(state, "checkin", if (AssistantPersonalization.quiet(context)) "Hello$name. We can chat whenever you like." else if (AssistantPersonalization.tone(context) == "direct") "Hello$name. What would you like to discuss?" else variants[n], tone = AssistantPersonalization.tone(context), extra = JSONObject().put("greeting", n))
        }
        if (text in setOf("bye", "goodbye", "good night", "goodnight", "see you")) return speak(state, "chat", "Take care. We can pick up a fresh conversation whenever you return.")
        if (text in setOf("my to-do list is judging me", "my todo list is judging me")) return speak(state, "banter", "Awfully confident for a list that needs you to do all the work.", choices = listOf("joke" to "Just a joke", "listen" to "Just listen"))
        AssistantDiscussion.answer(state, original)?.let { turn ->
            return speak(state, if (turn.frame == null) "chat" else "discussion", turn.text,
                extra = turn.frame?.let { JSONObject().put("discussion", it) })
        }
        if (Regex("(?i)^(?:i(?:'m| am| feel)|feeling|my day (?:was|is)|i had).*(?:sad|rough|tough|lonely|upset|anxious|frustrated|tired|exhausted|overwhelmed|stressed|bad day)\\b").containsMatchIn(text) || text in setOf("tired", "sad", "stressed", "overwhelmed", "i had a rough day"))
            return speak(state, "empathy", if (active && previous!!.optString("family") == "listening") "That sounds hard. I'm listening; you don't have to turn it into a plan." else "That sounds like a lot to carry. Would you rather talk about it, have a small distraction, or just be heard?", tone = "serious", choices = listOf("listen" to "Just listen", "riddle" to "A distraction"))
        if (text in setOf("i did it", "i passed my exam", "i got the job", "i got promoted", "i finally did it", "good news")) return speak(state, "celebration", "That's good news. What was the best part for you?")
        if (text in setOf("i'm bored", "i am bored", "bored", "something fun")) return speak(state, "boredom", "We could try a riddle, a tiny branching story, or a questionable joke. Pick your kind of fun.", choices = listOf("riddle" to "Riddle", "story" to "Story", "joke" to "Joke"))
        val interestRequest = Regex("^(?:i (?:like|love|enjoy)|let's talk about|lets talk about|talk about) (?:my )?([a-z -]{1,60})$").matchEntire(text)
        val interest = interestRequest?.groupValues?.get(1)?.let { topic -> interests.entries.firstOrNull { topic in it.value.aliases } }
        if (interest != null) return speak(state, "interest", interest.value.opening, extra = JSONObject().put("interest", interest.key).put("stage", 0))
        if (active && previous!!.optString("family") == "interest" && !newRequest(text)) {
            val pack = interests[previous.optString("interest")]
            if (pack != null) {
                val stage = previous.optInt("stage") + 1
                val body = if (stage == 1) pack.continuation else pack.reflection
                return speak(state, if (stage < 2) "interest" else "interest_done", body, extra = JSONObject().put("interest", previous.optString("interest")).put("stage", stage))
            }
        }
        if (text in setOf("what do you like", "do you have feelings", "are you a real person")) return speak(state, "about", "I'm Ayva, an offline assistant, rather than a person with feelings or a private life. I can still help with your app, explain words and keep a small conversation or game going.")
        if (active && previous!!.optString("family") == "listening" && !newRequest(text) && OfflineNluEngine.isConversationalStatement(text)) return speak(state, "listening", "I'm following. You can keep going; I won't jump into advice.", tone = "serious")
        if (active && text in setOf("yes", "yeah", "okay", "ok", "sure")) return speak(state, previous!!.optString("family"), "Tell me which direction you'd like: keep talking, a joke, a story, or a riddle. I haven't changed anything in the app.", choices = listOf("listen" to "Keep talking", "joke" to "Joke", "riddle" to "Riddle"))
        if (active && text in setOf("tell me more", "more", "why", "why is that")) return speak(state, previous!!.optString("family"), when (previous.optString("family")) {
            "about" -> "I use local rules, bundled text and structured context. That works well for supported features, but I can miss nuance, sarcasm and unfamiliar topics. You can correct me or ask for a specific supported action."
            "empathy", "listening" -> "You can tell me what happened, or simply how it landed for you. I won't assume the cause or save it as a preference."
            else -> "Which part would you like to explore? I can follow supported topics, explain words, or keep the conversation light."
        })
        return null
    }

    private fun newRequest(text: String) = OfflineNluEngine.isQuestion(text) || text in setOf("help", "settings", "features", "capabilities", "permissions", "status", "routines", "habits", "my tasks") || AssistantPreferences.find(text) != null || AssistantDictionary.isLookup(text) || OfflineNluEngine.isExplicitCreation(text) || AssistantFeatureLanguage.isRequest(text) || Regex("^(?:show|list|set|change|complete|delete|reschedule|lock|unlock|open|start|stop focus|undo|/talk|/create)\\b").containsMatchIn(text)
    private fun simple(text: String) = AyvaTalkEngine.TalkResponse(text, topicId = "conversation")
    private fun speak(state: AssistantSession, family: String, text: String, tone: String? = null, choices: List<Pair<String, String>> = emptyList(), extra: JSONObject? = null, otherActions: List<TalkAction> = emptyList()): AyvaTalkEngine.TalkResponse {
        val previous = frame(state)
        AssistantConversation.suspendQuestion(state); state.clearPending(); state.switchTopic("conversation")
        val id = UUID.randomUUID().toString(); val revision = (previous?.optInt("revision") ?: 0) + 1
        val next = JSONObject().put("id", id).put("revision", revision).put("at", System.currentTimeMillis()).put("family", family).put("tone", tone ?: previous?.optString("tone") ?: "warm")
        extra?.keys()?.asSequence()?.filter { it !in setOf("id", "revision", "at", "family", "tone") }?.forEach { next.put(it, extra.get(it)) }
        // Preserve only bounded response-family history, never an inferred personal profile.
        if (!next.has("storyRun") && previous?.has("storyRun") == true) next.put("storyRun", previous.getInt("storyRun"))
        if (!next.has("jokes") && previous?.has("jokes") == true) next.put("jokes", previous.getJSONArray("jokes"))
        features(state).put("social", next)
        val actions = choices.map { (code, label) -> TalkAction.AskQuery("/ayva social $id $revision $code", label) } + otherActions
        return AyvaTalkEngine.TalkResponse(text, actions, "conversation", AyvaTalkEngine.serializeActionsJson("conversation", actions))
    }
}
