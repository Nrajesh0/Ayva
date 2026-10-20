package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.util.OfflineNluEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Bounded, user-authored discussion evidence. No domain records or inferred personal profile. */
internal object AssistantDiscussion {
    data class Turn(val text: String, val frame: JSONObject?)
    data class Seed(val goal: String, val facts: Map<String, String>)
    private data class Note(val key: String, val text: String)
    private data class Frame(
        val topic: String,
        val notes: List<Note> = emptyList(),
        val options: List<String> = emptyList(),
        val choice: String? = null,
        val question: String = "",
        val comparison: DiscussionComparison = DiscussionComparison(),
        val stance: String = "think"
    ) {
        fun json() = JSONObject().put("topic", topic).put("notes", JSONArray(notes.map { JSONObject().put("key", it.key).put("text", it.text) }))
            .put("options", JSONArray(options)).put("choice", choice ?: JSONObject.NULL).put("question", question).put("comparison", comparison.json()).put("stance", stance)
    }
    private fun clean(text: String) = PlanLanguage.normalize(text).trimEnd('.', '!', '?')
    private fun lower(text: String) = text.lowercase(Locale.ROOT)
    private fun strings(array: JSONArray?) = (0 until (array?.length() ?: 0)).map { array!!.getString(it) }
    /** Reinterpret retained assertions only; never replay a stored command or short answer. */
    private fun withOptions(frame: Frame, options: List<String>): Pair<Frame, String> {
        var comparison = frame.comparison.retain(options)
        val issues = mutableListOf<String>()
        val remaining = frame.notes.filter { note ->
            val result = comparison.criteria.answer(note.text, options) ?: return@filter true
            if (!result.accepted) { issues += result.text; return@filter true }
            val retainsSource = result.state.rules.any { it.source == note.text } || result.state.evidence.any { it.source == note.text }
            val grows = result.state.rules.size >= comparison.criteria.rules.size && result.state.evidence.size >= comparison.criteria.evidence.size
            if (retainsSource && grows && result.state.pending == null) {
                comparison = comparison.copy(criteria = result.state, priority = if (result.setsPriority) null else comparison.priority)
                false
            } else true
        }
        return frame.copy(options = options, notes = remaining, choice = null, comparison = comparison) to issues.distinct().take(2).joinToString(" ")
    }
    private fun remainingAssertions(notes: List<Note>, comparison: DiscussionComparison, options: List<String>) = notes.filter { note ->
        val result = comparison.criteria.copy(pending = null).answer(note.text, options) ?: return@filter true
        !result.accepted || (result.state.rules.none { it.source == note.text } && result.state.evidence.none { it.source == note.text })
    }
    private fun current(state: AssistantSession): Frame? {
        if (state.topic != "conversation" || state.expected != null) return null
        return stored(state)
    }
    private fun stored(state: AssistantSession): Frame? {
        val social = state.featureState?.optJSONObject("social") ?: return null
        if (social.optString("family") != "discussion" || !AssistantContext.live(social.optLong("at"))) return null
        val json = social.optJSONObject("discussion") ?: return null
        return runCatching {
            val notes = json.getJSONArray("notes")
            val options = strings(json.optJSONArray("options"))
            Frame(json.getString("topic"), (0 until notes.length()).map {
                val note = notes.getJSONObject(it); Note(note.getString("key"), note.getString("text"))
            }, options, json.optString("choice").takeUnless { it.isBlank() || it == "null" }, json.optString("question"), DiscussionComparison.read(json.optJSONObject("comparison"), options), json.optString("stance", "think"))
                .takeIf { f -> f.stance in setOf("think", "listen", "act") && f.topic.length in 1..400 && f.notes.size <= 12 && f.options.size <= 6 &&
                    f.notes.all { it.key.length <= 30 && it.text.length in 1..400 } && f.options.all { it.length in 1..100 } }
        }.getOrNull()
    }

    fun archive(state: AssistantSession): JSONObject = (current(state) ?: throw IllegalArgumentException("Start or resume a discussion first"))
         .let { frame ->
            val note = frame.comparison.unresolvedCost()?.let { Note("context", it) }
            val notes = if (note != null && frame.notes.none { it.text == note.text }) frame.notes + note else frame.notes
            require(notes.size <= 12) { "This summary is full. Correct or remove a discussion detail before saving another supplied figure" }
            frame.copy(notes = notes, question = "", comparison = frame.comparison.withoutQuestions()).json()
        }
    fun sanitizeArchive(json: JSONObject): JSONObject {
        val state = AssistantSession(topic = "conversation", featureState = JSONObject().put("social", JSONObject()
            .put("family", "discussion").put("at", System.currentTimeMillis()).put("discussion", json)))
        return archive(state)
    }
    fun resumeArchive(state: AssistantSession, json: JSONObject) {
        val frame = sanitizeArchive(json)
        state.clearPending(); state.clearSuspended(); state.switchTopic("conversation")
        val features = state.featureState ?: JSONObject().also { state.featureState = it }
        features.put("social", JSONObject().put("family", "discussion").put("at", System.currentTimeMillis()).put("discussion", frame))
    }

    fun requestsHandoff(input: String) = lower(clean(input)) in setOf(
        "let's plan it", "lets plan it", "let's plan that", "lets plan that", "plan this", "plan that", "plan it",
        "turn this into a plan", "turn that into a plan", "let's plan together", "lets plan together"
    )
    fun hasContext(state: AssistantSession) = current(state) != null
    fun requestsResume(input: String) = lower(clean(input)) in setOf(
        "back to our discussion", "back to the discussion", "back to that discussion", "resume our discussion", "resume the discussion"
    )
    fun handoffProblem(state: AssistantSession, input: String): String? {
        if (!requestsHandoff(input) && lower(clean(input)) != "/plan") return null
        val frame = current(state) ?: return null
        frame.notes.firstNotNullOfOrNull { note ->
            frame.comparison.criteria.copy(pending = null).answer(note.text, frame.options)?.takeIf { it.needsCorrection }?.text
        }?.let {
            state.featureState?.optJSONObject("social")?.put("discussion", frame.copy(comparison = frame.comparison.withoutQuestions()).json())
            return "An earlier detail still needs clarification. $it"
        }
        val choice = frame.choice ?: return null
        frame.comparison.choiceProblem(choice)?.let { problem ->
            state.featureState?.optJSONObject("social")?.put("discussion", frame.copy(comparison = frame.comparison.withoutQuestions()).json())
            return problem
        }
        val question = frame.comparison.criteria.missingRequirement(choice) ?: return null
        val comparison = frame.comparison.withoutQuestions().copy(criteria = frame.comparison.criteria.copy(pending = question))
        state.featureState?.optJSONObject("social")?.put("discussion", frame.copy(comparison = comparison, question = "measurement").json())
        return "Before planning $choice, let's check your requirement. " + comparison.openQuestion()
    }
    fun clearMeasurementQuestion(state: AssistantSession) {
        val frame = current(state) ?: return
        state.featureState?.optJSONObject("social")?.put("discussion", frame.copy(comparison = frame.comparison.copy(pending = null, view = null)).json())
    }
    fun handoff(state: AssistantSession, input: String): Seed? {
        if (!requestsHandoff(input) && lower(clean(input)) != "/plan") return null
        val frame = current(state) ?: return null
        val facts = linkedMapOf("discussion topic" to frame.topic)
        frame.notes.forEachIndexed { i, note ->
            facts["discussion ${i + 1} (${note.key})"] = note.text
            if (note.key in setOf("budget", "timing", "destination"))
                facts[note.key] = PlanLanguage.facts(note.text)[note.key] ?: note.text
        }
        if (frame.options.isNotEmpty()) facts["options discussed"] = frame.options.joinToString(" / ")
        frame.choice?.let { facts["current discussion choice"] = it }
        facts.putAll(frame.comparison.planFacts(frame.options))
        // Only typed user statements become plan facts; questions and assistant suggestions never do.
        return Seed(frame.topic, facts)
    }

    fun answer(state: AssistantSession, input: String): Turn? {
        val text = clean(input)
        val key = lower(text)
        val previous = current(state)
        if (previous == null && key in setOf("just listen", "i just need to vent", "i want to vent"))
            return Turn("I'm listening. What's on your mind?", Frame(topic = "What's on your mind", stance = "listen").json())
        // Reading a dormant frame grants no task authority. Only this explicit request resumes it.
        if (requestsResume(text)) {
            val saved = stored(state) ?: return Turn("There isn't a recent discussion to resume. What would you like to talk through?", null)
            return Turn("Back to our discussion. ${summary(saved)}\n" + (saved.comparison.openQuestion() ?: "We can continue from those details, correct them, or use /plan for the next steps."), saved.json())
        }
        val opening = Regex("(?i)^(?:let'?s (?:talk (?:about|through)|think through)|(?:can|could) we (?:talk (?:about|through)|think through)|talk (?:about|through)|help me (?:think through|decide about)) (.+)$").matchEntire(text)
        val decision = Regex("(?i)^(?:i(?:'m| am) (?:deciding|choosing) between|i (?:need|want) to decide between) (.+)$").matchEntire(text)
        val decisionGoal = Regex("(?i)^i (?:need|want|would like) to (?:decide|think through|talk through|talk about) (.+)$").matchEntire(text)
        val occasion = Regex("(?i)^i (?:have|am planning|'m planning) (?:an? |my )?(?:exam|trip)\\b.*$").matches(text)
        if (opening != null || previous == null && (decision != null || decisionGoal != null || occasion)) {
            val topic = opening?.groupValues?.get(1) ?: if (decision != null) "Choosing between ${decision.groupValues[1]}" else decisionGoal?.groupValues?.get(1) ?: text
            if (topic.length > 400) return Turn("Give me a shorter topic first, then send the details in separate messages.", previous?.json())
            val options = decision?.groupValues?.get(1)?.let(::options).orEmpty()
            val frame = Frame(topic, options = options)
            return follow(frame, "Let's talk through $topic.")
        }
        val frame = previous ?: return null
        if (key in setOf("forget this discussion", "forget our discussion")) return Turn("I've cleared this discussion context. What would you like to talk about?", null)
        if (key in setOf("cancel", "never mind", "nevermind", "forget it", "stop that"))
            return Turn("Okay, we'll leave that discussion there. What's on your mind?", null)
        if (key in setOf("undo", "undo that", "undo last action"))
            return Turn("This discussion hasn't changed saved tasks. Correct a detail with ‘replace [old detail] with [new detail]’. To undo a saved app change, use /undo.", frame.json())
        if (key in setOf("summarize", "summarise", "summarize that", "recap our discussion", "what have we discussed", "what did i tell you", "what do we know so far", "what did we decide", "what did we agree", "where were we"))
            return Turn(summary(frame), frame.json())
        if (key in setOf("why", "why are you asking", "why that question", "what do you need from me")) {
            if (!frame.comparison.hasQuestion() && (frame.comparison.observations.isNotEmpty() || frame.comparison.criteria.hasData())) {
                val result = frame.comparison.explain(frame.options)
                return Turn(result.text, frame.copy(comparison = result.state, question = if (result.state.hasQuestion()) "measurement" else "tradeoff").json())
            }
            val reason = when (frame.question) {
                "options" -> "I need your possible options to compare them, rather than inventing choices for you."
                "priority" -> "Your priorities determine the trade-off; I can't assume that cost, time or comfort matters most."
                "tradeoff" -> "A missing fact could change the choice. We can mark it as unknown instead of guessing."
                "measurement" -> "One detail about an option is still unknown. " + frame.comparison.openQuestion().orEmpty()
                else -> "I'm asking what you want from this discussion so the next step fits your situation."
            }
            return Turn("$reason We're discussing ${frame.topic}.", frame.json())
        }
        if (key in setOf("just listen", "i just need to vent", "i want to vent", "listen for now"))
            return Turn("I'm listening. Tell me what's on your mind; I'll hold off on planning questions.", frame.copy(stance = "listen").json())
        if (key in setOf("help me think", "let's think it through", "help me decide"))
            return follow(frame.copy(stance = "think", question = ""), "Let's work through the details you've given.")
        if (frame.stance == "listen" && Regex("(?i)^i(?:'m| am| feel| need| want| have| can't| cannot| don't)\\b").containsMatchIn(text)) {
            val notes = (frame.notes + Note(category(text), text.take(400))).takeLast(12)
            return Turn("I hear you. ${text.take(400)}", frame.copy(notes = notes, question = "").json())
        }
        frame.comparison.answer(input, frame.options)?.let { result ->
            val conflict = frame.choice?.let(result.state::choiceProblem)
            val updated = frame.copy(notes = remainingAssertions(frame.notes, result.state, frame.options), comparison = result.state, question = if (result.state.hasQuestion()) "measurement" else "tradeoff")
            return Turn(result.text + conflict?.let { "\nYour current choice needs another look: $it" }.orEmpty(), updated.json())
        }
        if (key in setOf("i don't know", "not sure", "i'm not sure", "i am not sure"))
            return Turn("We can leave that open. ${summary(frame)}\nYou can describe one thing you want to avoid, add an option, or ask to compare what we have.", frame.json())
        val replacement = Regex("(?i)^replace (?:the detail )?[\"']?(.+?)[\"']? with [\"']?(.+?)[\"']?$").matchEntire(text)
        if (replacement != null) {
            val old = replacement.groupValues[1]; val new = replacement.groupValues[2]
            val matches = frame.notes.indices.filter { frame.notes[it].text.contains(old, true) }
            if (matches.size != 1 || new.length > 400) return Turn("Which detail should I replace? Use a unique part of its text, followed by ‘with’ and the correction.\n${summary(frame)}", frame.json())
            val index = matches.single()
            val updated = frame.notes.mapIndexed { i, note -> if (i == index) {
                val value = note.text.replace(Regex(Regex.escape(old), RegexOption.IGNORE_CASE)) { new }
                Note(category(value), value)
            } else note }
            if (updated[index].text.length > 400) return Turn("Keep the corrected detail under 400 characters.", frame.json())
            if (updated[index].key !in setOf("context", "priority", "constraint") && updated.indices.any { it != index && updated[it].key == updated[index].key })
                return Turn("That correction would give us two ${updated[index].key} details. Replace the existing ${updated[index].key} detail instead.\n${summary(frame)}", frame.json())
            return follow(frame.copy(notes = updated), "Updated that discussion detail.")
        }
        val optionInput = Regex("(?i)^(?:the )?options (?:are|:)\\s*(.+)$").matchEntire(text)?.groupValues?.get(1)
            ?: Regex("(?i)^(?:i(?:'m| am) (?:deciding|choosing) between) (.+)$").matchEntire(text)?.groupValues?.get(1)
        if (optionInput != null) {
            val choices = options(optionInput)
            if (choices.size < 2) return Turn("Tell me at least two options, separated by ‘or’, ‘and’, or a semicolon.", frame.json())
            val (updated, issues) = withOptions(frame, choices)
            return follow(updated, "Options: ${choices.joinToString(" / ")}. " + issues)
        }
        val extraOption = Regex("(?i)^what about (.+)$").matchEntire(text)?.groupValues?.get(1)
        if (extraOption != null && frame.options.isNotEmpty() && !interrupts(input)) {
            if (extraOption.length > 100 || frame.options.size >= 6) return Turn("Let's keep this comparison to six short options. You can replace the options by saying ‘options are …’.", frame.json())
            val updated = (frame.options + extraOption).distinctBy(::lower)
            val (next, issues) = withOptions(frame, updated)
            return follow(next.copy(choice = frame.choice), "We can include $extraOption as another possibility. " + issues)
        }
        val choiceInput = Regex("(?i)^(?:i (?:choose|prefer)|let'?s go with|go with|choose) (.+)$").matchEntire(text)?.groupValues?.get(1)
        if (choiceInput != null && frame.options.isNotEmpty()) {
            val ref = lower(choiceInput).removePrefix("the ").removeSuffix(" one")
            val index = listOf("first", "second", "third", "fourth", "fifth", "sixth").indexOf(ref)
            val chosen = frame.options.getOrNull(index) ?: frame.options.singleOrNull { it.equals(choiceInput, true) }
            if (chosen != null) {
                frame.comparison.choiceProblem(chosen)?.let {
                    return Turn(it, frame.copy(choice = null, comparison = frame.comparison.withoutQuestions()).json())
                }
                frame.comparison.criteria.missingRequirement(chosen)?.let { question ->
                    val comparison = frame.comparison.withoutQuestions().copy(criteria = frame.comparison.criteria.copy(pending = question))
                    return Turn("$chosen is your tentative choice. Let's confirm the requirement first. " + comparison.openQuestion(), frame.copy(choice = chosen, comparison = comparison, question = "measurement").json())
                }
                return follow(frame.copy(choice = chosen, comparison = frame.comparison.withoutQuestions()), "$chosen is your current choice in this discussion. We can still revisit it.")
            }
            if (!key.startsWith("i prefer ")) return Turn("Which option do you mean: ${frame.options.joinToString(" / ")}?", frame.json())
        }
        if (key in setOf("compare them", "compare the options", "compare those", "what are the trade-offs", "what are the tradeoffs", "which is better", "which would you choose")) {
            if (frame.comparison.observations.isNotEmpty() || frame.comparison.limits.isNotEmpty() || frame.comparison.priority != null || frame.comparison.criteria.hasData()) {
                val result = frame.comparison.compare(frame.options)
                return Turn((if (frame.notes.isEmpty()) "" else evidence(frame) + "\n") + result.text, frame.copy(comparison = result.state, question = if (result.state.hasQuestion()) "measurement" else "tradeoff").json())
            }
            val body = if (frame.options.size < 2) "We need at least two options before comparing. Say ‘options are A or B’." else
                "Options: ${frame.options.joinToString(" / ")}.\n${evidence(frame)}\nWe can compare each against your priorities using the details you supply. Which option fits your most important priority, and what is still unknown?"
            return Turn(body, frame.copy(question = if (frame.options.size < 2) "options" else "tradeoff").json())
        }
        // Explicit app operations, read requests and unanswered fields retain their existing owners.
        if (interrupts(input)) return null
        val correction = PlanLanguage.correctionValue(text)
        val clauses = (correction ?: text).split(Regex(";\\s*|(?<=[.!])\\s+")).filter { it.isNotBlank() }
        if (clauses.size > 6 || clauses.any { it.length > 400 }) return Turn("Send up to six short discussion details at a time, each under 400 characters.", frame.json())
        if (OfflineNluEngine.isQuestion(input)) return Turn("We're discussing ${frame.topic}. ${evidence(frame)}\nI can help compare these details or turn them into a plan. What information would help you decide?", frame.json())
        if (key in setOf("yes", "no", "okay", "ok", "sure", "continue", "tell me more", "more"))
            return Turn("${summary(frame)}\nWe can add details, compare options, or use /plan when you're ready to plan the next steps.", frame.json())
        var notes = frame.notes
        for (clause in clauses) {
            val note = Note(category(clause), clause.trim())
            val sameKey = notes.indices.filter { notes[it].key == note.key && note.key !in setOf("context", "priority", "constraint") }
            if (correction != null && (clauses.size != 1 || sameKey.size != 1))
                return Turn("Which detail are you correcting? Say ‘replace [old detail] with [new detail]’ so I keep the rest.\n${summary(frame)}", frame.json())
            if (sameKey.size == 1 && !notes[sameKey.single()].text.equals(note.text, true) && correction == null)
                return Turn("You already gave me this detail: ${notes[sameKey.single()].text}. Is ‘${note.text}’ a correction? Say ‘actually, ${note.text}’ to replace it.", frame.json())
            notes = if (sameKey.size == 1) notes.mapIndexed { i, old -> if (i == sameKey.single()) note else old }
                else if (notes.any { it.text.equals(note.text, true) }) notes else notes + note
        }
        if (notes.size > 12) return Turn("This discussion already has twelve details. Correct an existing detail or use /plan to carry them into a planning workspace.", frame.json())
        return follow(frame.copy(notes = notes, comparison = frame.comparison.withoutQuestions()), "Noted: ${clauses.joinToString("; ")}.")
    }

    private fun category(text: String): String = when {
        Regex("(?i)^(?:my |the )?budget\\b").containsMatchIn(text) -> "budget"
        Regex("(?i)^(?:my |the )?(?:dates?|timing|time window)\\b").containsMatchIn(text) -> "timing"
        Regex("(?i)^(?:my |the )?destination\\b").containsMatchIn(text) -> "destination"
        Regex("(?i)^(?:what matters|most important|my priority|i (?:prefer|care about|value))\\b").containsMatchIn(text) -> "priority"
        Regex("(?i)^(?:i (?:can only|can't|cannot|need|don't want|must)|we (?:can only|can't|cannot|need))\\b").containsMatchIn(text) -> "constraint"
        else -> "context"
    }
    private fun options(text: String): List<String> = text.split(Regex(";\\s*|,\\s*|\\s+(?:or|and)\\s+", RegexOption.IGNORE_CASE))
        .map { it.trim() }.filter { it.isNotBlank() }.distinctBy(::lower).takeIf { it.size in 2..6 && it.all { option -> option.length <= 100 } }.orEmpty()
    private fun evidence(frame: Frame) = if (frame.notes.isEmpty()) "You haven't supplied comparison details yet." else
        "You told me:\n" + frame.notes.joinToString("\n") { "• ${it.text}" }
    private fun summary(frame: Frame) = "Topic: ${frame.topic}." +
        (if (frame.options.isEmpty()) "" else "\nOptions: ${frame.options.joinToString(" / ")}.") +
        (frame.choice?.let { "\nCurrent choice: $it." } ?: "") + "\n${evidence(frame)}" +
        frame.comparison.planFacts(frame.options).entries.joinToString("") { "\n${it.key}: ${it.value}" }
    private fun follow(frame: Frame, prefix: String): Turn {
        if (frame.stance == "listen") return Turn("I hear you. ${frame.notes.lastOrNull()?.text.orEmpty()}", frame.copy(question = "").json())
        val question = when {
            frame.choice != null -> "next"
            frame.options.size >= 2 && frame.notes.none { it.key == "priority" } -> "priority"
            frame.options.size >= 2 -> "tradeoff"
            frame.notes.isEmpty() -> "context"
            else -> "options"
        }
        val prompt = if (frame.question == question) "We can keep exploring, recap the discussion, or use /plan for next steps." else when (question) {
            "priority" -> "What matters most to you in this choice?"
            "tradeoff" -> "What is still uncertain about these options?"
            "options" -> "Are there options you'd like to compare, or would you rather keep talking it through?"
            "next" -> "Would you like to explore that choice further or turn it into a plan?"
            else -> "What would a good outcome look like for you?"
        }
        return Turn("$prefix $prompt", frame.copy(question = question).json())
    }
    private fun interrupts(text: String): Boolean {
        val key = lower(AssistantTaskLanguage.normalize(text)).trimEnd('.', '!', '?')
        // Within an explicit discussion these are requirements, not implicit task-creation authority.
        if (Regex("^i (?:need|have|want|would like) to\\b").containsMatchIn(key)) return false
        return text.startsWith("/") || OfflineNluEngine.isExplicitCreation(text) || AssistantFeatureLanguage.isRequest(text) ||
            AssistantDictionary.isLookup(text) || AssistantPreferences.find(text) != null || AssistantTaskLanguage.request(text) != null ||
            AssistantDialogue.isClockRequest(text) || AssistantAppKnowledge.legacyFeature(text) ||
            key in setOf("help", "settings", "features", "capabilities", "permissions", "status", "routines", "habits", "my tasks", "my habits", "my agenda", "what do i have today", "what do i have tomorrow", "timer status", "time remaining", "cancel", "never mind", "nevermind", "forget it", "undo", "undo that") ||
            Regex("^(?:show|list|set|change|complete|delete|remove|reschedule|lock|unlock|block|unblock|open|start|stop|log|edit|rename|pause|resume|enable|disable|focus|remind me|drill|blitz|quiz|practice|calculate|solve|convert|how many \\d+|what(?: is|'s) \\d+)\\b").containsMatchIn(key) ||
            OfflineNluEngine.isQuestion(text) && Regex("\\b(?:tasks?|habits?|routines?|reminders?|permissions?|backup|screen time|app|ayva|focus|rewards?|streak|xp|agenda|schedule)\\b").containsMatchIn(key)
    }
}
