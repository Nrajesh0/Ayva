package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.util.AyvaTalkEngine
import com.focusbyrj.app.util.TalkAction
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Activity ownership only; no task, habit or planning execution capabilities. */
internal object FocusedChat {
    private const val KEY = "focusedChat"
    private val help = setOf("explore ayva", "what can we talk about", "what can you do", "conversation capabilities", "what conversation topics do you support")
    private val next = setOf("another", "another one", "one more", "another please", "give me another", "next", "skip", "something else", "something different", "different one", "same category")
    private val explain = setOf("why", "why that", "explain", "explain that", "tell me more", "how", "why is that")
    private val hints = setOf("hint", "help", "give me a hint")
    private val stop = setOf("stop", "stop the game", "stop the story", "end game", "that's it", "thats it", "quit")
    private val start = mapOf("play word chain" to "chain", "word chain" to "chain", "play anagrams" to "anagram", "anagrams" to "anagram",
        "explore facts" to "facts", "tell me a fact" to "facts", "give me a fact" to "facts", "interesting fact" to "facts", "give me a motivational line" to "lines",
        "motivate me" to "lines", "war quote" to "lines", "play a tiny story" to "story", "tiny adventure" to "story")
    private fun values(j: JSONObject, key: String): List<String> = j.optJSONArray(key)?.let { a -> (0 until minOf(a.length(), 100)).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }.orEmpty()
    private fun response(text: String, choices: List<Pair<String, String>> = emptyList()): AyvaTalkEngine.TalkResponse {
        val actions = choices.map { (label, query) -> TalkAction.AskQuery(query, label) }
        return AyvaTalkEngine.TalkResponse(text, actions, "conversation", AyvaTalkEngine.serializeActionsJson("conversation", actions))
    }
    /** Preserve social progress before an earlier operational owner can consume a turn. */
    fun rememberActivity(state: AssistantSession) {
        val root = state.featureState ?: return
        if (state.topic == "conversation" && root.optString("activeDialogue") == "general") {
            root.optJSONObject("generalDialogue")?.let { root.put("pausedGeneral", JSONObject(it.toString()).put("pausedAt", System.currentTimeMillis())) }
        }
        val social = root.optJSONObject("social") ?: return
        if (state.topic == "conversation" && root.optString("activeDialogue") == "social" &&
            social.optString("family") in setOf("joke", "riddle", "riddle_done", "story", "story_done", "chain", "interest", "interest_done")) {
            root.put("pausedSocial", JSONObject(social.toString()).put("pausedAt", System.currentTimeMillis()))
        }
    }
    fun answer(context: Context, state: AssistantSession, input: String): AyvaTalkEngine.TalkResponse? {
        if (ChatModeStore.get(context) == ChatMode.PLAN || state.topic == "planning" || state.expected != null || state.pending.isNotEmpty()) return null
        var text = input.trim().lowercase(Locale.ROOT).replace('’', '\'').trimEnd('.', '!', '?')
        val root = state.featureState ?: JSONObject().also { state.featureState = it }
        text = text.replace(Regex("^/ayva experience "), "experience-choice ")
        if (text.startsWith("experience-choice ")) {
            val parts = text.split(' ', limit = 4)
            val current = root.optJSONObject(KEY)
            if (parts.size != 4 || current == null || current.optInt("version") != 1 || System.currentTimeMillis() - current.optLong("at") !in 0..604_800_000L || root.optString("activeDialogue") != "focused" ||
                state.topic != "conversation" || current.optBoolean("paused") ||
                parts[1] != current.optString("id") || parts[2].toIntOrNull() != current.optInt("revision") || parts[3] !in values(current, "choices"))
                return response("That activity choice has expired. Use the latest choices or type what you'd like to do.")
            text = parts[3]
        }
        fun boundedNotice(message: String): AyvaTalkEngine.TalkResponse {
            val count = root.optInt("unsupportedCount") + 1
            root.put("unsupportedCount", count.coerceAtMost(2))
            return response(if (count == 1) message else "That topic is outside my offline packs. Choose an activity, or tell me which supported topic you'd like.", listOf("Explore" to "explore ayva"))
        }
        if (text in setOf("play a game", "let's play a game", "lets play a game")) return response("Choose a short game. You can ask for hints, pause, or stop at any time.", ChatExperiences.capabilities.filter { it.group == "Play" }.map { it.title to it.request })
        ChatExperiences.unsupportedTopic(text)?.let { return boundedNotice(it) }
        root.remove("unsupportedCount")
        if (text in help) return response(ChatExperiences.help(), ChatExperiences.capabilities.take(7).map { it.title to it.request })
        var frame = root.optJSONObject(KEY)?.takeIf { it.optInt("version") == 1 && System.currentTimeMillis() - it.optLong("at") in 0..604_800_000L }
        val general = root.optJSONObject("generalDialogue")
        val generalActive = state.topic == "conversation" && root.optString("activeDialogue") == "general"
        val savedGeneral = root.optJSONObject("pausedGeneral")?.takeIf { GeneralDialogue.recalled(it) != null }
        if (generalActive && text in setOf("pause", "pause activity", "pause discussion")) {
            general?.let { root.put("pausedGeneral", JSONObject(it.toString()).put("pausedAt", System.currentTimeMillis())) }
            root.put("activeDialogue", "paused_general")
            return response("Paused. Say ‘resume discussion’ when you're ready.", listOf("Resume" to "resume discussion"))
        }
        if (text == "resume discussion" || text == "resume activity" && savedGeneral != null &&
            savedGeneral.optLong("pausedAt") > maxOf(frame?.optLong("at") ?: 0, root.optJSONObject("pausedSocial")?.optLong("pausedAt") ?: 0)) {
            if (savedGeneral == null) return response("There isn't a recent discussion to resume.", listOf("Explore" to "explore ayva"))
            val restored = JSONObject(savedGeneral.toString()).put("at", System.currentTimeMillis())
            root.put("generalDialogue", restored).put("activeDialogue", "general").remove("pausedGeneral")
            state.switchTopic("conversation")
            return response(restored.optJSONObject("dialogueV2")?.optJSONObject("answer")?.optString("short")?.takeIf { it.isNotBlank() }
                ?: "We're back to ${restored.optString("topic", "our discussion")}. Ask for a recap or tell me what changed.")
        }
        if (text in stop && (generalActive || root.optString("activeDialogue") == "paused_general")) {
            root.remove("pausedGeneral"); root.remove("generalDialogue"); root.put("activeDialogue", "")
            return response("We'll leave that discussion there.", listOf("Explore" to "explore ayva"))
        }
        val social = root.optJSONObject("social")
        val socialActive = state.topic == "conversation" && root.optString("activeDialogue") == "social" && social?.optString("family") in setOf("joke", "riddle", "riddle_done", "story", "story_done", "chain", "interest", "interest_done")
        val savedSocial = root.optJSONObject("pausedSocial")?.takeIf { System.currentTimeMillis() - it.optLong("pausedAt") in 0..604_800_000L }
        val pauseRequest = text in setOf("pause", "pause game", "pause activity", "pause story", "pause jokes", "pause riddles")
        if (socialActive && pauseRequest) {
            root.put("pausedSocial", JSONObject(social!!.toString()).put("pausedAt", System.currentTimeMillis())).put("activeDialogue", "paused_social")
            return response("Paused. Say ‘resume activity’ whenever you're ready.", listOf("Resume" to "resume activity"))
        }
        val socialResume = text in setOf("resume jokes", "resume riddles") || text in setOf("resume activity", "resume game") && savedSocial != null && (frame == null || savedSocial.optLong("pausedAt") >= frame.optLong("at"))
        if (socialResume && savedSocial != null && (text != "resume jokes" || savedSocial.optString("family") == "joke") && (text != "resume riddles" || savedSocial.optString("family") in setOf("riddle", "riddle_done"))) {
            val restored = JSONObject(savedSocial.toString()).put("at", System.currentTimeMillis()).put("id", java.util.UUID.randomUUID().toString()).put("revision", savedSocial.optInt("revision") + 1)
            root.put("social", restored).put("activeDialogue", "social").remove("pausedSocial")
            state.switchTopic("conversation")
            return response(restored.optString("prompt", "We can continue. Ask for a hint, another, or stop."))
        }
        if (text in setOf("resume jokes", "resume riddles")) return response("There isn't a recent matching activity to resume.", listOf("Explore" to "explore ayva"))
        if (text in stop && (socialActive || root.optString("activeDialogue") == "paused_social")) {
            root.remove("pausedSocial"); root.remove("social"); root.put("activeDialogue", "")
            return response("Finished. Explore Ayva whenever you want another activity.", listOf("Explore" to "explore ayva"))
        }
        if (socialActive && (ConversationRouting.nativeRequest(text) || ChatExperiences.capabilities.any { it.request.lowercase(Locale.ROOT) == text })) {
            root.put("pausedSocial", JSONObject(social!!.toString()).put("pausedAt", System.currentTimeMillis()))
        }
        val active = frame != null && root.optString("activeDialogue") == "focused" && state.topic == "conversation" && !frame.optBoolean("paused")
        fun archived(): List<JSONObject> = root.optJSONArray("focusedHistory")?.let { a -> (0 until minOf(a.length(), 3)).mapNotNull { a.optJSONObject(it) }.filter { it.optInt("version") == 1 && System.currentTimeMillis() - it.optLong("at") in 0..604_800_000L } }.orEmpty()
        fun archive(value: JSONObject?) {
            if (value == null) return
            val kept = archived().filterNot { it.optString("kind") == value.optString("kind") }
            root.put("focusedHistory", JSONArray((kept + JSONObject(value.toString()).put("paused", true)).takeLast(3)))
        }
        val resumeKind = mapOf("resume word chain" to "chain", "resume anagrams" to "anagram", "resume facts" to "facts", "resume story" to "story", "resume encouragement" to "lines")[text]
        if (resumeKind != null || text in setOf("resume game", "resume activity", "back to the game")) {
            val chosen = if (resumeKind == null) frame ?: archived().lastOrNull() else frame?.takeIf { it.optString("kind") == resumeKind } ?: archived().lastOrNull { it.optString("kind") == resumeKind }
            if (chosen == null) return response("There isn't a recent matching activity to resume. Explore Ayva to choose one.", listOf("Explore" to "explore ayva"))
            if (chosen !== frame) archive(frame)
            val restored = JSONObject(chosen.toString()).put("paused", false).put("revision", chosen.optInt("revision") + 1).put("at", System.currentTimeMillis())
            frame = restored
            root.put(KEY, restored).put("activeDialogue", "focused"); state.switchTopic("conversation")
            root.put("focusedHistory", JSONArray(archived().filterNot { it.optString("kind") == restored.optString("kind") }))
            return response(restored.optString("prompt", "Say ‘next’ to continue."), listOf("Help" to "help", "Stop" to "stop"))
        }
        if (active && text in setOf("pause", "pause game", "pause activity", "pause story")) {
            frame!!.put("paused", true); root.put("activeDialogue", "")
            return response("Paused. Say ‘resume activity’ when you're ready.", listOf("Resume" to "resume activity"))
        }
        if ((active || frame?.optBoolean("paused") == true && state.topic == "conversation") && text in stop) {
            val score = frame!!.optInt("score")
            root.remove(KEY); root.put("activeDialogue", "")
            return response("Finished. ${if (frame.optString("kind") in setOf("chain", "anagram")) "$score correct ${if (score == 1) "answer" else "answers"}. " else ""}Thanks for spending a little time here.", listOf("Explore" to "explore ayva"))
        }
        val request = text.removePrefix("can we ").removePrefix("let's ").removePrefix("lets ").removePrefix("i want to ")
        val kind = start[request]
        val otherExperience = kind == null && (ChatExperiences.capabilities.any { it.request.lowercase(Locale.ROOT) == text } || AssistantSocial.hasInterestOpening(text) ||
            text in setOf("tell me a story", "tell me a joke", "give me a riddle", "just listen", "no advice", "hi", "hello") ||
            Regex("^(?:forget|clear|reset|let'?s talk|talk about|i feel|i am stressed|i prefer)\\b").containsMatchIn(text))
        if (active && otherExperience) {
            frame!!.put("paused", true)
            root.put("activeDialogue", "")
            return null
        }
        if (kind == null && !active) return null
        // Explicit app requests leave the activity untouched so it can be resumed later.
        if (kind == null && text !in setOf("start over", "new round") && (ConversationRouting.nativeRequest(text) || AssistantDictionary.isLookup(text) || ConversationReasoning.explicit(text))) return null
        if (kind != null) {
            if (frame?.optString("kind") != kind) archive(frame)
            if (socialActive) root.put("pausedSocial", JSONObject(social!!.toString()).put("pausedAt", System.currentTimeMillis()))
            frame = JSONObject().put("version", 1).put("id", java.util.UUID.randomUUID().toString()).put("revision", 0).put("kind", kind).put("at", System.currentTimeMillis())
                .put("index", 0).put("score", 0).put("seen", root.optJSONObject("experienceSeen")?.optJSONArray(kind)?.let { JSONArray(it.toString()) } ?: JSONArray()).put("category", if (text == "war quote") "battle-inspired" else "all")
            root.put(KEY, frame)
        }
        val f = frame ?: return null
        root.put("activeDialogue", "focused"); state.switchTopic("conversation"); f.put("at", System.currentTimeMillis())
        fun say(body: String, options: List<Pair<String, String>> = listOf("Next" to "next", "Stop" to "stop")): AyvaTalkEngine.TalkResponse {
            f.put("prompt", body).put("revision", f.optInt("revision") + 1).put("choices", JSONArray(options.map { it.second }))
            if (f.optString("id").isBlank()) f.put("id", java.util.UUID.randomUUID().toString())
            f.put("phase", if (f.optBoolean("finished")) "finished" else if (f.optBoolean("solved")) "answered" else "active")
                .put("expectedAnswer", when (f.optString("kind")) { "chain", "anagram" -> "word"; "story" -> "choice"; else -> "followup" })
                .put("focusItem", f.optString("fact", f.optString("word", f.optString("node"))))
            root.put("lastExperienceOwner", f.optString("kind"))
            val history = root.optJSONObject("experienceSeen") ?: JSONObject().also { root.put("experienceSeen", it) }
            history.put(f.optString("kind"), JSONArray(values(f, "seen").takeLast(100)))
            root.put("experienceDiagnostic", JSONObject().put("owner", f.optString("kind")).put("phase", f.optString("phase")))
            return response(body, options.map { (label, query) -> label to "/ayva experience ${f.optString("id")} ${f.optInt("revision")} $query" })
        }
        if (text in setOf("restart", "start over", "new round")) {
            root.optJSONObject("experienceSeen")?.remove(f.optString("kind"))
            return answer(context, state, start.entries.first { it.value == f.optString("kind") }.key)
        }
        if (text in setOf("easy", "harder", "hard", "longer", "shorter") && f.optString("kind") in setOf("chain", "anagram")) {
            if (text in setOf("easy", "hard", "harder")) f.put("hard", text != "easy")
            if (text in setOf("longer", "shorter")) f.put("limit", if (text == "longer") 15 else 5)
            if (f.optString("kind") == "anagram") return say("${if (f.optBoolean("hard")) "The next puzzle will use at least seven letters." else "The next puzzle can use any length in this collection."} Finish this puzzle or say ‘next’.")
            return say("Okay—${if (f.optBoolean("hard")) "longer words" else "standard difficulty"}, ${f.optInt("limit", 5)} rounds. ${f.optString("prompt")}")
        }
        if (f.optBoolean("finished")) return say("This round is finished. Restart for a new round or stop.", listOf("Restart" to "restart", "Stop" to "stop"))
        val current = f.optString("kind")
        if (text == "help" && current !in setOf("chain", "anagram")) return say("You can ask ‘why’, ‘example’, ‘next’, ‘source’, ‘pause’ or ‘stop’. Stories use the choices shown in the current scene.")
        when (current) {
            "chain" -> {
                val used = values(f, "used").toMutableList()
                if (kind != null) { used += "cat"; f.put("used", JSONArray(used)).put("word", "cat"); return say("Word chain: start with **cat**'s last letter, T. Five rounds; dictionary words only, no repeats. Say ‘hint’, ‘longer’ or ‘stop’.", listOf("Hint" to "hint", "Stop" to "stop")) }
                val last = f.optString("word", "cat").takeIf { it.matches(Regex("[a-z]{2,24}")) } ?: return say("This saved round is incompatible. Restart for a fresh game.", listOf("Restart" to "restart"))
                if (text == "rules") return say(ExperienceContent.WORD_RULES)
                if (text in setOf("score", "progress")) return say("${f.optInt("score")} of ${f.optInt("limit", 5)} valid words. Your next letter is ${last.last().uppercaseChar()}.")
                val candidates = ExperienceContent.chainByLetter[last.last()].orEmpty().filter { it !in used && (!f.optBoolean("hard") || it.length >= 5) }
                if (text in setOf("meaning", "definition", "what does it mean")) {
                    val meaning = runCatching { AssistantDictionaryStore.lookup(context, last).firstOrNull()?.definition }.getOrNull()
                    return say(meaning?.let { "**$last**: $it" } ?: "I don't have a stored definition for this word.", listOf("Hint" to "hint", "Skip" to "skip"))
                }
                if (text in hints) {
                    val level = f.optInt("hints") + 1; f.put("hints", level)
                    return say(candidates.firstOrNull()?.let { if (level == 1) "Try a ${it.length}-letter word beginning ${it.take(2).uppercase()}." else "You could use **$it**. Type it to play, or skip without scoring." } ?: "No hint is available for this letter. You can skip or stop.", listOf("Skip" to "skip", "Stop" to "stop"))
                }
                if (text in next) {
                    val word = ExperienceContent.chainWords.firstOrNull { it !in used } ?: return say("We've exhausted this round's bot vocabulary. Stop or restart.")
                    used += word; f.put("hints", 0).put("word", word).put("used", JSONArray(used.takeLast(100)))
                    return say("Skipped without a penalty. My word: **$word**. Your letter: ${word.last().uppercaseChar()}.", listOf("Hint" to "hint", "Stop" to "stop"))
                }
                if (!text.matches(Regex("[a-z]{2,24}"))) return say("For this game, use one alphabetic word, without spaces or hyphens. Your letter is ${last.last().uppercaseChar()}.")
                if (text.first() != last.last()) return say("Start with ${last.last().uppercaseChar()}, the last letter of ‘$last’.", listOf("Hint" to "hint", "Skip" to "skip"))
                if (text in used) return say("We already used ‘$text’. Try another word.")
                val senses = runCatching { AssistantDictionaryStore.lookup(context, text) }.getOrDefault(emptyList())
                if (senses.isEmpty() || senses.all { sense -> sense.labels.any { it.contains("proper", true) } }) return say("‘$text’ isn't in my game dictionary. You can try another word or skip; I won't count it as a wrong answer.", listOf("Skip" to "skip", "Hint" to "hint"))
                if (f.optBoolean("hard") && text.length < 5) return say("Hard mode uses words of at least five letters. Try again or say ‘easy’.")
                used += text; f.put("score", f.optInt("score") + 1)
                if (f.optInt("score") >= f.optInt("limit", 5)) { f.put("finished", true); return say("Round complete: ${f.optInt("score")} valid words. Nicely done. Restart for a fresh game or stop.", listOf("Restart" to "restart", "Stop" to "stop")) }
                val word = ExperienceContent.chainByLetter[text.last()].orEmpty().firstOrNull { it !in used && (!f.optBoolean("hard") || it.length >= 5) }
                if (word == null) { f.put("finished", true); return say("‘$text’ counts. I have no unused reply in my curated vocabulary—you win this round.", listOf("Restart" to "restart", "Stop" to "stop")) }
                used += word; f.put("hints", 0).put("used", JSONArray(used.takeLast(100))).put("word", word)
                return say("‘$text’ counts. My word: **$word**. Your letter: ${word.last().uppercaseChar()}.", listOf("Hint" to "hint", "Stop" to "stop"))
            }
            "anagram" -> {
                val seen = values(f, "seen").toMutableList()
                var word = f.optString("word")
                if (kind != null || text in next || f.optBoolean("solved") && text == "continue") {
                    val puzzle = ExperienceContent.puzzles.firstOrNull { it.word !in seen && (!f.optBoolean("hard") || it.word.length >= 7) }
                        ?: return say("You've reached the end of this puzzle collection. Restart to replay or explore another activity.", listOf("Restart" to "restart", "Explore" to "explore ayva"))
                    word = puzzle.word; seen += word
                    f.put("word", word).put("seen", JSONArray(seen)).put("hints", 0).put("solved", false)
                    val scrambled = word.drop(2) + word.take(2)
                    return say("Unscramble **${scrambled.uppercase()}**. Use every letter exactly once. Alternative dictionary words count.", listOf("Hint" to "hint", "Skip" to "skip", "Stop" to "stop"))
                }
                if (f.optBoolean("solved")) return say("That round is finished. Say ‘next’ for another.")
                val puzzle = ExperienceContent.puzzles.firstOrNull { it.word == word } ?: return say("This puzzle changed. Say ‘restart’ for a fresh one.")
                if (text in hints) { val n = f.optInt("hints") + 1; f.put("hints", n); return say(if (n == 1) puzzle.clue else "Starts with ${word.take(minOf(n, word.length - 1)).uppercase()}.", listOf("Reveal" to "reveal", "Skip" to "skip")) }
                if (text in setOf("reveal", "answer", "show answer")) { f.put("solved", true); return say("One answer is **$word**: ${puzzle.clue}.") }
                if (text.toList().sorted() != word.toList().sorted()) return say("Use exactly the puzzle's letters, including repeated letters. You can ask for a hint.", listOf("Hint" to "hint", "Reveal" to "reveal"))
                val senses = runCatching { AssistantDictionaryStore.lookup(context, text) }.getOrDefault(emptyList())
                if (text != word && senses.isEmpty()) return say("That arrangement isn't in my game dictionary. Try another or reveal the stored answer.")
                f.put("solved", true).put("score", f.optInt("score") + 1)
                val streak = if (f.optInt("hints") == 0) f.optInt("unassisted") + 1 else 0
                f.put("unassisted", streak)
                val offerHarder = streak >= 3 && !f.optBoolean("hard")
                return say("Correct: **$text**. ${senses.firstOrNull()?.definition ?: puzzle.clue}" + if (offerHarder) " Three without hints—try longer words?" else "",
                    if (offerHarder) listOf("Harder" to "harder", "Next" to "next", "Stop" to "stop") else listOf("Next" to "next", "Stop" to "stop"))
            }
            "facts" -> {
                if (text in setOf("space", "nature", "language")) { f.put("category", text); f.remove("fact") }
                val pool = ExperienceContent.facts.filter { f.optString("category") == "all" || it.category == f.optString("category") }
                var fact = pool.firstOrNull { it.id == f.optString("fact") }
                if (kind != null || text in next || fact == null || text == "related fact") {
                    val seen = values(f, "seen")
                    val relatedCategory = fact?.category
                    val candidates = if (text == "related fact" && relatedCategory != null) pool.filter { it.id in fact!!.relatedIds } else pool
                    fact = candidates.firstOrNull { it.id !in seen } ?: return say("That's the end of this fact collection. Choose another category or restart to revisit it.", listOf("Space" to "space", "Nature" to "nature", "Language" to "language", "Restart" to "restart"))
                    f.put("fact", fact.id).put("seen", JSONArray((seen + fact.id).takeLast(100)))
                    return say(fact.claim, listOf("Why?" to "why", "Example" to "example", "Next" to "next", "Source" to "source"))
                }
                if (text in explain) return say(fact.why, listOf("Example" to "example", "Related fact" to "related fact", "Source" to "source"))
                if (text in setOf("example", "give me an example", "compare", "comparison", "compared with what")) return say(fact.example)
                if (text in setOf("source", "where did that come from", "is that true")) return say("${fact.source}\nContent pack ${ExperienceContent.VERSION}; editorial date ${ExperienceContent.REVIEW_DATE}. Offline reference, without live updates.")
                return say("I don't have a reliable explanation of that follow-up in this fact pack. I can explain this fact or offer another.", listOf("Explain" to "explain", "Next" to "next", "Stop" to "stop"))
            }
            "lines" -> {
                if (text in ExperienceContent.lines.keys) f.put("category", text)
                val category = f.optString("category").takeIf { it in ExperienceContent.lines } ?: "calm"
                val seen = values(f, "seen")
                if (kind == null && text !in next && text !in ExperienceContent.lines.keys) return say("These are original Run lines, not attributed quotations. Choose calm, disciplined, resilient or battle-inspired.", ExperienceContent.lines.keys.map { it to it })
                val line = ExperienceContent.lines.getValue(category).withIndex().firstOrNull { "$category:${it.index}" !in seen }
                    ?: return say("You've seen this style's current collection. Try a different style or restart to replay.", ExperienceContent.lines.keys.map { it to it })
                f.put("seen", JSONArray((seen + "$category:${line.index}").takeLast(100)))
                return say("${line.value}\n— Original Run line", listOf("Another" to "another", "Calm" to "calm", "Resilient" to "resilient", "Battle-inspired" to "battle-inspired"))
            }
            "story" -> {
                var index = f.optInt("index")
                if (kind == null && text in next) { index++; if (index >= ExperienceContent.stories.size) return say("You've played all three stories. Restart to explore different endings.", listOf("Restart" to "restart", "Stop" to "stop")); f.put("index", index).put("node", "start") }
                val story = ExperienceContent.stories[index.coerceIn(ExperienceContent.stories.indices)]
                var node = f.optString("node", "start")
                var scene = story.nodes.getValue(node)
                if (kind == null && text !in next) {
                    val number = when(text) { "first", "first one", "the first one", "1" -> 0; "second", "second one", "the second one", "2" -> 1; else -> -1 }
                    val choiceText = text.removePrefix("i would like to ").removePrefix("i want to ").removePrefix("i'll ").removePrefix("let's ").removePrefix("choose ")
                    val choice = scene.choices.entries.firstOrNull { it.key == choiceText } ?: scene.choices.entries.toList().getOrNull(number)
                    if (choice == null) return say(if (scene.choices.isEmpty()) "That story has ended. Choose ‘next’ for another or ‘restart’ to explore a different ending." else "I can follow the choices in this short story. ${scene.text}", scene.choices.keys.map { it to it } + ("Stop" to "stop"))
                    node = choice.value; f.put("node", node); scene = story.nodes.getValue(node)
                }
                return say("${story.title}\n\n${scene.text}", if (scene.choices.isEmpty()) listOf("Next story" to "next", "Stop" to "stop") else scene.choices.keys.map { it to it } + ("Pause" to "pause story"))
            }
        }
        return null
    }
}
