package com.focusbyrj.app.util.assistant

import org.json.JSONArray
import org.json.JSONObject

/** Conversation data has no executable commands or saved-task authority. */
internal data class PlanningDialogueState(
    val question: String = "", val stance: String = "explore", val proposals: List<String> = emptyList(),
    val rejected: List<String> = emptyList(), val notes: List<String> = emptyList(), val changes: List<String> = emptyList(), val discussion: JSONObject? = null,
    val focusMilestone: String? = null, val outlineMethods: List<String> = emptyList(), val outlineRevision: Int? = null,
    val reasoning: JSONObject? = null, val breakdown: JSONObject? = null, val sharedChange: JSONObject? = null
) {
    fun json() = JSONObject().put("version", 1).put("question", question).put("stance", stance)
        .put("proposals", JSONArray(proposals)).put("rejected", JSONArray(rejected))
        .put("notes", JSONArray(notes)).put("changes", JSONArray(changes)).put("discussion", discussion ?: JSONObject.NULL)
        .put("focusMilestone", focusMilestone ?: JSONObject.NULL).put("outlineMethods", JSONArray(outlineMethods)).put("outlineRevision", outlineRevision ?: JSONObject.NULL)
        .put("reasoning", reasoning ?: JSONObject.NULL).put("breakdown", breakdown ?: JSONObject.NULL).put("sharedChange", sharedChange ?: JSONObject.NULL)
    companion object {
        fun read(j: JSONObject?): PlanningDialogueState {
            if (j == null) return PlanningDialogueState()
            require(j.optInt("version", 1) == 1)
            fun strings(key: String, max: Int, length: Int): List<String> {
                val a = j.optJSONArray(key) ?: return emptyList()
                require(a.length() <= max)
                return (0 until a.length()).map { a.getString(it).also { v -> require(v.length <= length) } }
            }
            return PlanningDialogueState(j.optString("question"), j.optString("stance", "explore"),
                strings("proposals", 12, 500), strings("rejected", 30, 500), strings("notes", 20, 500), strings("changes", 20, 600), j.optJSONObject("discussion")?.also { require(it.toString().toByteArray().size <= 30_000); AssistantDiscussion.sanitizeArchive(it) },
                j.nullableString("focusMilestone"), strings("outlineMethods", 8, 40), j.nullableLong("outlineRevision")?.also { require(it in 1..Int.MAX_VALUE.toLong()) }?.toInt(), j.optJSONObject("reasoning"), j.optJSONObject("breakdown"), j.optJSONObject("sharedChange"))
                .also { PlanningReasoning.validate(it.reasoning); PlanBreakdown.validate(it.breakdown); SharedPlanConstraints.validate(it.sharedChange); require(it.outlineMethods.distinct().size == it.outlineMethods.size); require((it.focusMilestone == null || it.focusMilestone.length in 1..80) && it.question.length <= 60 && it.stance in setOf("explore", "listen", "organize")) }
        }
    }
}

/** Shared goal discussion, suggestions and natural draft additions. Canonical scheduling stays in its owner. */
internal object PlanningDialogue {
    data class Turn(val plan: PlanWorkspace, val text: String)
    fun goal(input: String) = PlanLanguage.normalize(input).removePrefix("/plan ")
        .replaceFirst(Regex("(?i)^(?:(?:let'?s|can we|could we|help me|i want to|i would like to)\\s+)?(?:plan|organize|organise)(?:\\s+for)?\\s+"), "")
        .replaceFirst(Regex("(?i)^i (?:want|would like) to go on\\s+"), "")
        .replace(Regex("(?i)\\s+together[.!]?$"), "").trim().take(500)
    fun opening(plan: PlanWorkspace): Turn {
        val question = when {
            plan.goal.isBlank() -> "goal"
            plan.domain == "trip" && plan.facts["destination"] == null -> "destination"
            plan.domain == "trip" -> "date"
            plan.domain == "study" -> "topics"
            else -> "outcome"
        }
        val line = when (question) {
            "goal" -> "What would you like to work toward? We can talk it through before making a task list."
            "destination" -> if (Regex("(?i)exam|ibps").containsMatchIn(plan.goal)) "Let's work out the exam journey. Which city is the exam in?" else "Let's work it out together. Where are you thinking of going?"
            "date" -> "Let's work through the preparation for ${plan.facts["destination"]}. Do you have dates in mind, or should we leave them open?"
            "topics" -> "Let's build this around what you need to learn. Which topics are you working on?"
            else -> "Let's work through ${plan.goal}. What would a useful result look like for you?"
        }
        return Turn(plan.copy(dialogue = PlanningDialogueState(question = question)), line)
    }
    private fun next(plan: PlanWorkspace): Pair<String, String> = when {
        plan.domain == "trip" && plan.facts["destination"] == null -> "destination" to "Which city or place should we plan around?"
        plan.domain == "trip" && plan.facts["travel date"] == null -> "date" to "Do you have a travel or arrival date in mind? We can leave it open."
        plan.domain == "study" && plan.facts["topics"] == null -> "topics" to "Which topics would you like to start with?"
        plan.steps.isEmpty() && plan.dialogue.proposals.isEmpty() -> "suggest" to "I can suggest a small starting outline, or we can work through what you already have in mind."
        else -> "" to ""
    }
    private fun respond(plan: PlanWorkspace, message: String, facts: Map<String, String> = plan.facts,
                        steps: List<PlanStep> = plan.steps, removed: List<PlanStep> = plan.removed,
                        dialogue: PlanningDialogueState = plan.dialogue, changed: Boolean = false): Turn {
        val updated = if (changed || facts != plan.facts || steps != plan.steps || removed != plan.removed)
            plan.changed(facts = facts, steps = steps, removed = removed, selected = plan.selected?.takeIf { id -> steps.any { it.id == id } }) else plan
        return Turn(updated.copy(dialogue = dialogue.copy(changes = dialogue.changes.map { it.take(600) })), message)
    }
    private fun discussionSession(plan: PlanWorkspace) = AssistantSession(topic = "conversation", featureState = JSONObject().put("social", JSONObject()
        .put("family", "discussion").put("at", System.currentTimeMillis()).put("discussion", plan.dialogue.discussion ?: JSONObject())))
    fun saveProblem(plan: PlanWorkspace): String? = if (plan.dialogue.discussion == null) null else
        AssistantDiscussion.handoffProblem(discussionSession(plan), "plan it")
    private fun discussion(plan: PlanWorkspace, input: String): Turn? {
        val lower = input.lowercase()
        val starts = Regex("(?i)^(?:options (?:are|:)|i(?:'m| am) (?:choosing|deciding) between) ").containsMatchIn(input)
        val old = plan.dialogue.discussion
        val names = old?.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        val relevant = starts || old != null && (names.any { name -> Regex("(?i)(?<![\\p{L}])${Regex.escape(name)}(?![\\p{L}])").containsMatchIn(input) } ||
            Regex("(?i)^(?:options |compare|which |why|weights? |clear weights|remove weights|cost limit|time limit|travel time limit|maximum |i (?:choose|prefer|care most about)|prioriti[sz]e|first one|second one|third one|yes$|no$)").containsMatchIn(input) ||
            old.optString("question") == "measurement")
        if (!relevant) return null
        val state = if (old == null) AssistantSession(topic = "conversation") else discussionSession(plan)
        if (old == null) {
            val frame = AssistantDiscussion.answer(state, "let's talk through ${plan.goal.take(350)}")?.frame ?: return null
            AssistantDiscussion.resumeArchive(state, frame)
        }
        val turn = AssistantDiscussion.answer(state, input) ?: return null
        val nextFrame = turn.frame ?: return null
        val facts = plan.facts.toMutableMap()
        // This is a provisional discussion choice; comparison requirements can still block saving.
        nextFrame.optString("choice").takeUnless { it.isBlank() || it == "null" }?.let { facts["current discussion choice"] = it }
        if (nextFrame.isNull("choice")) facts.remove("current discussion choice")
        return respond(plan, turn.text, facts = facts, dialogue = plan.dialogue.copy(discussion = nextFrame))
    }
    fun answer(plan: PlanWorkspace, input: String): Turn? {
        val text = PlanLanguage.normalize(input).trimEnd('.', '!', '?')
        val lower = text.lowercase()
        val d = plan.dialogue
        if (input.startsWith('/') || plan.pending != null || plan.hypotheticalView || lower.startsWith("what if ")) return null
        // A known step's relationship belongs to the graph owner, even during an outcome question.
        val relation = Regex("(?i)^(.+?) (depends on|must follow|after) (.+)$").matchEntire(text)
        if (relation != null && PlanLanguage.references(plan, relation.groupValues[1]).isNotEmpty() &&
            (relation.groupValues[2].lowercase() != "after" || PlanLanguage.references(plan, relation.groupValues[3]).isNotEmpty())) return null
        val conversationalCommands = setOf("make it simpler", "give me the overview", "give me the full picture", "summarize the plan", "use suggested steps", "use those steps", "add those steps", "give me ideas", "use those", "use that outline", "use overnight")
        if (ConversationMeaning.kind(text) == ConversationMeaning.Kind.COMMAND && lower !in conversationalCommands) return null

        if (lower in setOf("plan details", "show plan details")) return Turn(plan, "${plan.goal}\nTime zone: ${plan.timezone}\nDraft revision: ${plan.revision}\n" + plan.facts.entries.joinToString("\n") { "${it.key}: ${it.value}" })
        if (lower in setOf("just talk it through first", "just discuss", "don't add anything yet", "just listen", "no advice", "i don't want advice"))
            return respond(plan, "We can stay with the discussion. I'll keep your draft as it is and leave suggestions aside.", dialogue = d.copy(stance = "listen", question = "", discussion = d.discussion?.let(AssistantDiscussion::sanitizeArchive)?.put("stance", "listen")))
        if (lower in setOf("help me organize it", "let's make the tasks", "lets make the tasks", "help me plan it"))
            return respond(plan, "Let's turn the agreed parts into manageable steps. ${next(plan).second}", dialogue = d.copy(stance = "organize"))
        discussion(plan, text)?.let { return it }
        if (lower in setOf("hi", "hey", "hello")) return respond(plan, "Hi. We can pick up ${plan.goal.ifBlank { "your plan" }} whenever you're ready.")
        if (lower in setOf("thanks", "thank you", "that's all", "thats all", "bye")) return respond(plan, "You're welcome. Your draft will be here when you want to continue.")
        if (lower in setOf("what changed", "what changed since yesterday", "why did you change that", "what did you change"))
            return respond(plan, PlanChangeTrail.describe(plan, lower == "what changed since yesterday"))
        if (Regex("(?i)^why did you (?:move|reschedule|change|remove|rename) ").containsMatchIn(lower))
            return respond(plan, "The recorded draft changes were:\n" + PlanChangeTrail.describe(plan))
        if (lower in setOf("what are we missing", "what's missing", "what should i do first", "what next", "what's next")) {
            val unfinished = plan.steps.filter { it.workItem && !it.completed }
            val first = unfinished.firstOrNull { step -> step.prerequisites.all { id -> plan.steps.any { it.id == id && it.completed } } }
            val missing = next(plan)
            return respond(plan, if (lower.contains("first") && first != null) "A useful next step is ‘${first.title}’. ${PlanningMethods.reason(plan.goal, first.title).orEmpty()}"
                else if (missing.second.isNotBlank()) missing.second else "The draft has ${unfinished.size} unfinished steps. ${unfinished.count { it.minutes == null }} still have no effort estimate, so I can't give them precise work slots yet.", dialogue = d.copy(question = missing.first))
        }
        if (lower in setOf("make it simpler", "give me the overview", "give me the full picture", "summarize the plan", "what did we decide")) {
            return respond(plan, buildString {
                append("We're working toward ${plan.goal}.")
                if (plan.facts.isNotEmpty()) append("\n" + plan.facts.entries.joinToString("; ") { "${it.key}: ${it.value}" })
                if (plan.steps.isNotEmpty()) append("\n" + plan.steps.filter { it.parentId == null }.take(6).joinToString("\n") { "• ${it.title}" })
                if (d.proposals.isNotEmpty()) append("\nStill only suggested: " + d.proposals.joinToString())
            })
        }
        if (lower in setOf("why this order", "explain the plan")) return null
        if (Regex("^(?:why|how|could|would|what about|is it|do we need|can i|should i)\\b").containsMatchIn(lower)) {
            if (Regex("overnight|hotel|accommodation").containsMatchIn(lower)) return respond(plan,
                "Overnight travel could avoid a hotel, but the important trade-offs are arrival reliability, rest and the time needed to reach the venue. A nearby stay gives more buffer but adds cost. We need actual arrival information before calling either option feasible. Neither choice has been added as a settled decision.")
            PlanningMethods.reason(plan.goal, lower)?.let { return respond(plan, it) }
            ConversationKnowledge.explain(text, d.notes.lastOrNull())?.let { return respond(plan, it) }
            if (lower == "why" || lower == "why did you ask") return respond(plan, when (d.question) {
                "destination" -> "The destination determines which journey and arrival arrangements are relevant. We can still discuss the preparation before choosing it."
                "date" -> "The date helps us distinguish flexible preparation from a fixed arrival deadline. I won't invent one if it isn't known."
                "topics" -> "Your actual topics let us focus the plan instead of guessing an exam syllabus."
                else -> "I want the plan to fit your outcome and constraints, rather than add a generic checklist. You can skip details that aren't useful yet." })
            // Existing explicit scheduling/what-if commands retain their own owner.
            if (lower.startsWith("what if ")) return null
            return respond(plan, "I can work through that using what we know about ${plan.goal}. Which part of ‘${text.take(180)}’ matters most to your decision?", dialogue = d.copy(question = "clarify"))
        }
        if (lower in setOf("suggest steps", "suggest a plan", "help me break it down", "what should i prepare", "what would you suggest", "give me ideas", "break it down", "i don't know where to start")) {
            val proposals = PlanningMethods.forGoal(plan.goal + " " + plan.facts["purpose"].orEmpty()).filter { method ->
                method.title !in d.rejected && plan.steps.none { it.title.equals(method.title, true) }
            }.take(6)
            return respond(plan, if (proposals.isEmpty()) "We've already covered those starting points. We can refine an existing step or discuss what's blocking you." else
                "A starting outline could be:\n" + proposals.joinToString("\n") { "• ${it.title} — ${it.reason}" } + "\nWhich parts fit? They're suggestions until you choose them.",
                dialogue = d.copy(question = "proposals", proposals = proposals.map { it.title }))
        }
        if (d.proposals.isNotEmpty() && lower in setOf("yes", "yes please", "use those", "that sounds good", "use suggested steps", "use those steps", "add those steps", "use that outline")) {
            if (d.stance == "listen") return respond(plan, "Would you like these added to the draft, or should we keep discussing them?")
            val additions = d.proposals.filter { title -> plan.steps.none { it.title.equals(title, true) } }.map { PlanStep(title = it, source = "accepted authored suggestion") }
            return respond(plan, "Added ${additions.joinToString { "‘${it.title}’" }} to the draft. We can work through one at a time.", steps = plan.steps + additions,
                dialogue = d.copy(proposals = emptyList(), question = "", changes = (d.changes + "Accepted ${additions.size} suggested steps.").takeLast(20)))
        }
        if (d.proposals.isNotEmpty() && lower in setOf("no", "not those", "skip those", "no thanks", "i don't like those"))
            return respond(plan, "We'll leave those out. We can use your own steps or start with the obstacle you want to solve.", dialogue = d.copy(proposals = emptyList(), rejected = (d.rejected + d.proposals).distinct().takeLast(30), question = ""))
        if (lower in setOf("i don't know", "not sure", "skip", "leave it open", "dates aren't fixed"))
            return respond(plan, "We can leave that open. One useful starting point is to identify what you already know and the next thing you need to find out.", dialogue = d.copy(question = "", notes = (d.notes + "Left ${d.question.ifBlank { "current detail" }} open").takeLast(20)))

        // Natural additions are draft-only and accept explicit needs, not speculation/quoted instructions.
        val need = Regex("(?i)^(?:we (?:also )?need(?: to)?|i (?:also )?need to|let'?s (?:also )?add|we should (?:also )?add) (.+)$").matchEntire(text)
        if (need != null && !Regex("(?i)\\b(?:arrive|be there|report) (?:by|at)\\b").containsMatchIn(text) && !Regex("(?i)\\b(?:maybe|perhaps|if|might|not|don't)\\b|[\"“”]").containsMatchIn(need.groupValues[1])) {
            if (d.stance == "listen") return respond(plan, "That sounds relevant. I'll keep it as discussion for now; say you want it added when you're ready.", dialogue = d.copy(notes = (d.notes + text.take(500)).takeLast(20)))
            val titles = need.groupValues[1].split(Regex("(?i)\\s+and\\s+|,\\s*" )).map { phrase ->
                phrase.trim().let { when {
                    Regex("(?i)^(?:somewhere|a place) to stay$").matches(it) -> "Decide accommodation"
                    else -> it.replaceFirstChar { c -> c.uppercase() }
                } }
            }.filter { it.isNotBlank() }.distinct()
            require(titles.size <= 8 && titles.all { it.length <= 200 }) { "Let's keep this to eight clear draft additions at a time" }
            val additions = titles.filter { title -> plan.steps.none { it.title.equals(title, true) } }.map { PlanStep(title = it, source = "user stated need") }
            return respond(plan, if (additions.isEmpty()) "Those steps are already in the draft." else "Added ${additions.joinToString { "‘${it.title}’" }} to our draft. We can refine them as we talk.", steps = plan.steps + additions,
                dialogue = d.copy(question = "", changes = (d.changes + "Added: ${additions.joinToString { it.title }}").takeLast(20)))
        }
        val facts = linkedMapOf<String, String>()
        if (Regex("(?i)^(?:yes[,]? |let'?s |we'?ll |i'?ll |choose |use |go with )(?:travel )?overnight$").matches(text)) facts["travel choice"] = "overnight travel (arrival time still needs checking)"
        Regex("(?i)^(?:actually[,]? )?(?:only |just )?(\\d+|one|two|three|four|five|six|seven|ten) days?$").matchEntire(text)?.let { facts["trip length"] = it.value }

        Regex("(?i)^(?:actually[,]? )?(destination|travel date|budget|arrive by|topics|success|trip length)(?: is|:| should be) (.+)$").matchEntire(text)?.let { facts[it.groupValues[1].lowercase()] = it.groupValues[2].take(500) }
        var domain = plan.domain
        if (Regex("(?i)^(?:it'?s|it is|this is|the trip is) for (?:my |an? )?(.+exam.*)$").matches(text)) {
            facts["purpose"] = text.substringAfter("for ").take(500); domain = "trip"
        }
        Regex("(?i)^(?:actually[,]? )?(?:we'?re going to|i'?m going to|the (?:exam|event) is in|destination(?: is|:)?|to) ([\\p{L}][\\p{L} .'-]{1,80})(?:,|$)").find(text)?.let { facts["destination"] = it.groupValues[1].trim() }
        if (d.question == "destination" && facts.isEmpty() && Regex("[\\p{L}][\\p{L} .'-]{1,60}(?:,.*)?").matches(text) && !Regex("(?i)\\b(?:we|i|maybe|perhaps|plan|want|need|option|not|should)\\b").containsMatchIn(text))
            facts["destination"] = text.substringBefore(',').trim()
        val dateText = text.replace(Regex("(?i)\\bnot (?:this |next )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b"), "")
        Regex("(?i)\\b(?:today|tomorrow|(?:next |this )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|\\d{4}-\\d{2}-\\d{2})\\b").find(dateText)?.takeUnless { Regex("(?i)\\b(?:can't|cannot|won't|don't)\\b").containsMatchIn(dateText) }?.let { facts[when (plan.domain) { "trip" -> "travel date"; "study" -> "study date"; else -> "target date" }] = it.value }
        Regex("(?i)\\b(?:arrive|be there|report)(?: there)? (?:by|at) ([0-9: .]+(?:am|pm))\\b").find(text)?.let { facts["arrive by"] = it.groupValues[1].trim() }
        Regex("(?i)\\b(?:budget|spend up to|at most) (?:is |of |: )?([\\p{Sc}\\d][\\d,. ]*(?:rupees|dollars|euros|inr|usd|eur)?)").find(text)?.let { facts["budget"] = it.groupValues[1].trim() }
        if (d.question in setOf("topics", "outcome") && facts.isEmpty() && !Regex("(?i)^(?:add|remove|rename|move|schedule|save|apply|delete|complete|set|split|protect|use)\\b").containsMatchIn(text)) facts[if (d.question == "topics") "topics" else "success"] = text.take(500)
        if (Regex("(?i)^(?:i prefer|we prefer|i want|we want|i can't|we can't|i don't want|we don't want|keep .* free|only have)\\b").containsMatchIn(text)) facts["constraint ${plan.facts.keys.count { it.startsWith("constraint") } + 1}"] = text.take(500)
        if (facts.isNotEmpty()) {
            val combined = plan.facts + facts
            require(combined.size <= 30) { "This plan's fact memory is full. Let's review the existing details before adding more." }
            val updated = plan.copy(domain = domain)
            val follow = next(updated.copy(facts = combined))
            val description = facts.entries.joinToString("; ") { "${it.key}: ${it.value}" }
            return respond(updated, "Got it — $description." + if (follow.second.isBlank() || d.stance == "listen") "" else " ${follow.second}", facts = combined,
                dialogue = d.copy(question = follow.first, changes = (d.changes + "Noted $description").takeLast(20)))
        }
        if (Regex("(?i)^(?:maybe|perhaps|we could|i could|what if|i think|i feel|i'm|i am|because|but|it's|it is)\\b").containsMatchIn(text)) {
            val body = if (d.stance == "listen") "I'm following. ${text.take(220)} — we can stay with that before deciding what to do."
                else "Let's keep ‘${text.take(220)}’ in mind as a possibility. What would make that work better for you than the alternative?"
            return respond(plan, body, dialogue = d.copy(notes = (d.notes + text.take(500)).takeLast(20), question = ""))
        }
        return null
    }
    fun repair(plan: PlanWorkspace, input: String): Turn {
        val d = plan.dialogue
        val repeat = d.notes.lastOrNull() == input.take(500)
        return respond(plan, if (repeat) "I'm still missing the meaning of that detail. We can leave it open and work on another part of ${plan.goal.ifBlank { "the plan" }}."
            else "How does ‘${input.take(160)}’ fit into ${plan.goal.ifBlank { "what we're planning" }} — is it something to do, a constraint, or an option you're considering?",
            dialogue = d.copy(question = "clarify", notes = (d.notes + input.take(500)).takeLast(20)))
    }
}
