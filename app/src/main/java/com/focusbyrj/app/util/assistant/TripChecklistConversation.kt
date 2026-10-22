package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.Subtask
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Durable semantic state. Checklist identity is stable across edits, saves, and process restarts. */
internal data class TripChecklistItem(
    val id: String = UUID.randomUUID().toString(), val title: String, val phase: String = "Before leaving",
    val date: String? = null, val offset: Int? = null
) {
    fun json() = JSONObject().put("id", id).put("title", title).put("phase", phase)
        .put("date", date ?: JSONObject.NULL).put("offset", offset ?: JSONObject.NULL)
}
internal data class TripChecklistState(val rootId: String, val items: List<TripChecklistItem> = emptyList(), val selected: String? = null) {
    fun json() = JSONObject().put("version", 1).put("root", rootId).put("items", JSONArray(items.map { it.json() }))
        .put("selected", selected ?: JSONObject.NULL)
    fun validate(plan: PlanWorkspace) {
        require(rootId.length in 1..80 && plan.steps.any { it.id == rootId && it.parentId == null })
        require(items.size <= 99 && items.map { it.id }.distinct().size == items.size)
        require(selected == null || items.any { it.id == selected })
        val root = plan.steps.single { it.id == rootId }
        val dated = plan.steps.filter { it.id != rootId }
        require(root.checklist.map { it.id }.toSet().intersect(dated.map { it.id }.toSet()).isEmpty())
        require((root.checklist.map { it.id } + dated.map { it.id }).toSet() == items.map { it.id }.toSet()) { "Trip checklist projection is inconsistent" }
        items.forEach { item ->
            val check = root.checklist.singleOrNull { it.id == item.id }
            val step = dated.singleOrNull { it.id == item.id }
            require(check?.title == item.title || step?.title == item.title && step.parentId == rootId && step.deadline != null)
            val expected = item.date?.let(LocalDate::parse) ?: item.offset?.let { offset ->
                plan.facts["travel date"]?.let { LocalDate.parse(it).plusDays(offset.toLong()) }
            }
            if (expected != null) require(step?.deadline?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(plan.timezone)).toLocalDate() } == expected) { "Trip item date and native projection disagree" }
            require(item.date == null || item.offset == null)
            require(if (item.date != null || item.offset != null && plan.facts["travel date"] != null) step != null else check != null)
        }
        items.forEach {
            require(it.id != rootId && it.id.length in 1..80 && it.title.length in 1..200 && it.phase in phases)
            require(it.offset == null || it.offset in -365..365)
            it.date?.let(LocalDate::parse)
        }
    }
    companion object {
        val phases = setOf("Before leaving", "During the trip", "After returning")
        fun read(j: JSONObject): TripChecklistState {
            require(j.getInt("version") == 1)
            val a = j.getJSONArray("items"); require(a.length() <= 99)
            return TripChecklistState(j.getString("root"), (0 until a.length()).map { i ->
                val item = a.getJSONObject(i)
                TripChecklistItem(item.getString("id"), item.getString("title"), item.getString("phase"), item.nullableString("date"), item.nullableLong("offset")?.toInt())
            }, j.nullableString("selected"))
        }
    }
}

/** One turn interpreter for trip checklists. Controls and typed facts precede free-text answers. */
internal object TripChecklistConversation {
    private fun clean(s: String) = PlanLanguage.normalize(s).replace('’', '\'').trim().trimEnd('.', '!', '?', ',')
    private fun rx(s: String) = Regex(s, RegexOption.IGNORE_CASE)
    private val tripWord = rx("\\b(?:trip|holiday|vacation|journey)\\b")
    private val action = "(?:buy|book|pack|bring|take|download|print|carry|check|arrange|visit|explore|rent|reserve|collect|prepare|charge|call|pay|renew|get|keep|research|confirm|purchase|pick up)"
    fun starts(input: String): Boolean = rx("^(?:i(?:'m| am) planning(?: for)?|i (?:want|would like) to plan(?: for)?) .+").containsMatchIn(clean(input)) && tripWord.containsMatchIn(input)
    fun summaryRequest(input: String): Boolean {
        val text = clean(input).lowercase(Locale.ROOT)
        return text in setOf("show plan", "show my plan", "review plan", "summary", "summarize", "summarise", "summarize the plan", "what did we decide", "where are we", "what's on the list", "show the list", "show my list", "show checklist", "what have we added", "what are we planning") ||
            rx("^(?:what (?:do we have|have we got|we (?:have|got)|have we planned)|show (?:me )?(?:what we have|the plan|the checklist))(?: (?:so far|till now|until now|up to now))?$").matches(text)
    }
    fun finish(input: String) = clean(input).lowercase(Locale.ROOT) in setOf("that's it", "thats it", "that's all", "thats all", "that's everything", "thats everything", "done", "all done", "we're done", "we are done", "save it", "save this", "finish planning", "finish and save", "okay save it", "ok save it", "yes save it")
    fun pause(input: String) = clean(input).lowercase(Locale.ROOT) in setOf("end", "stop", "stop planning", "pause", "pause planning", "exit", "exit plan", "leave plan mode", "let's continue later", "lets continue later", "continue later", "cancel planning")
    fun active(plan: PlanWorkspace) = plan.trip != null

    fun open(plan: PlanWorkspace, input: String, now: Long): PlanningDialogue.Turn? {
        if (plan.domain != "trip") return null
        if (plan.facts["planning style"] == "advanced") return null
        val destination = destination(input)
        val purpose = purpose(input)
        val name = if (purpose != null) "${purpose.replaceFirstChar { it.uppercase() }} trip" else "Trip" + destination?.let { " to $it" }.orEmpty()
        val root = PlanStep(title = name, source = "trip main task", workReminders = false)
        var next = plan.copy(goal = name, steps = listOf(root), facts = plan.facts - "destination" +
            listOfNotNull(destination?.let { "destination" to it }, purpose?.let { "purpose" to it }).toMap(),
            trip = TripChecklistState(root.id), dialogue = PlanningDialogueState(question = if (destination == null) "destination" else "date"))
        val date = rx("\\b(?:on|leaving|departing|travel(?:ling|ing)? on) (.+?)(?=,|;|$)").find(input)?.groupValues?.get(1)
        var dateLine = ""
        if (date != null && TripDate.read(date, next, now) != null) {
            val role = if (rx("exam(?: is)? on ").containsMatchIn(input)) "exam date" else "travel date"
            val turn = setDate(next, date, role, now)
            next = turn.plan; dateLine = " ${turn.text}"
        }
        val question = if (dateLine.isNotEmpty()) "" else if (destination == null) {
            if (purpose != null) " Which city is the exam in?" else " Where would you like to go?"
        } else " What dates are you travelling? You can leave the date open."
        return PlanningDialogue.Turn(next, "I'll build **$name** as your main task with a checklist as we talk. Say ‘that's it’ when you're ready to save.$dateLine$question")
    }

    private fun destination(input: String): String? = rx("\\b(?:trip|holiday|vacation|journey|going|travel(?:ling|ing)?) to ([\\p{L}][\\p{L} .'-]*?)(?= on | for | in | next | tomorrow|,|;|$)")
        .find(clean(input))?.groupValues?.get(1)?.trim()?.takeIf { it.length <= 80 }
    private fun purpose(input: String): String? = rx("\\b((?:[a-z0-9-]+ )?exam)\\b").find(input)?.value?.let {
        it.replace(rx("^(?:an?|the|my|for) "), "").replace(rx("\\bibps\\b"), "IBPS")
    }
    private fun children(plan: PlanWorkspace) = plan.trip!!.items
    private fun root(plan: PlanWorkspace) = plan.steps.first { it.id == plan.trip!!.rootId }
    private fun day(plan: PlanWorkspace, key: String) = plan.facts[key]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    private fun timestamp(date: LocalDate, zone: String) = date.atTime(23, 59).atZone(ZoneId.of(zone)).toInstant().toEpochMilli()
    private fun display(date: String) = runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH)) }.getOrDefault(date)

    /** Project the conversation into one native checklist plus independently dated child tasks. */
    private fun update(plan: PlanWorkspace, items: List<TripChecklistItem> = children(plan), facts: Map<String, String> = plan.facts,
                       selected: String? = plan.trip!!.selected, pending: JSONObject? = plan.pending, question: String = plan.dialogue.question): PlanWorkspace {
        val trip = plan.trip!!.copy(items = items, selected = selected?.takeIf { id -> items.any { it.id == id } })
        val travel = facts["travel date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val title = facts["main task title"] ?: if (facts["purpose"] != null) "${facts.getValue("purpose").replaceFirstChar { it.uppercase() }} trip" + facts["destination"]?.let { " to $it" }.orEmpty()
            else "Trip" + facts["destination"]?.let { " to $it" }.orEmpty()
        val scheduled = items.mapNotNull { item ->
            val date = item.date?.let(LocalDate::parse) ?: item.offset?.let { travel?.plusDays(it.toLong()) } ?: return@mapNotNull null
            val previous = (plan.steps + plan.removed).firstOrNull { it.id == item.id }
            (previous ?: PlanStep(id = item.id, title = item.title, workReminders = false)).copy(title = item.title,
                parentId = trip.rootId, deadline = timestamp(date, plan.timezone), source = "trip ${item.phase}")
        }
        val old = root(plan)
        val oldChecks = old.checklist.associateBy { it.id }
        val parent = old.copy(title = title, deadline = travel?.let { timestamp(it, plan.timezone) }, checklist = items.filter { item -> scheduled.none { it.id == item.id } }.map {
            Subtask(it.id, it.title, oldChecks[it.id]?.isDone ?: false)
        })
        val steps = listOf(parent) + scheduled
        val removed = (plan.removed + plan.steps.filter { previous -> previous.taskSnapshot != null && steps.none { it.id == previous.id } }).distinctBy { it.id }.filter { old -> steps.none { it.id == old.id } }
        val revised = plan.changed(steps = steps, facts = facts, selected = null, pending = pending, removed = removed, goal = title)
        return revised.copy(trip = trip, dialogue = plan.dialogue.copy(question = question))
    }

    fun summary(plan: PlanWorkspace): String = buildString {
        append("**${root(plan).title}**")
        for (key in listOf("travel date", "exam date", "return date", "destination", "purpose")) plan.facts[key]?.let { append("\n${key.replaceFirstChar { c -> c.uppercase() }}: ${display(it)}") }
        if (plan.facts["travel date"] == null) append("\nTravel date: not set")
        var number = 0
        TripChecklistState.phases.forEach { phase ->
            val items = children(plan).filter { it.phase == phase }
            if (items.isNotEmpty()) {
                append("\n\n$phase")
                items.forEach { item ->
                    append("\n${++number}. ${item.title}")
                    item.date?.let { append(" — ${display(it)}") }
                    item.offset?.let { append(if (it < 0) " — ${-it} day(s) before travel" else " — $it day(s) after travel") }
                }
            }
        }
        if (children(plan).isEmpty()) append("\nNo checklist items yet.")
        append(if (plan.savedRevision == plan.revision) "\n\nSaved." else "\n\nDraft — say ‘that's it’ to save.")
        plan.pending?.takeIf { it.optString("kind").startsWith("trip_") }?.let { append("\n${it.optString("question")}") }
    }
    fun warnings(plan: PlanWorkspace, now: Long): String {
        val travel = day(plan, "travel date") ?: return ""
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.of(plan.timezone)).toLocalDate()
        val warnings = children(plan).mapNotNull { item ->
            val date = item.date?.let(LocalDate::parse) ?: item.offset?.let { travel.plusDays(it.toLong()) }
            when {
                date != null && item.phase == "Before leaving" && date > travel -> "${item.title} is dated after departure."
                date != null && item.offset != null && date < today -> "${item.title} now falls in the past."
                else -> null
            }
        }
        return if (warnings.isEmpty()) "" else "\nPlease check: " + warnings.joinToString(" ")
    }
    fun details(plan: PlanWorkspace): String = buildString {
        for ((key, value) in plan.facts) append("${key.replaceFirstChar { it.uppercase() }}: ${display(value)}\n")
        TripChecklistState.phases.forEach { phase ->
            val items = children(plan).filter { it.phase == phase }
            if (items.isNotEmpty()) append("\n$phase:\n" + items.joinToString("\n") { "• ${it.title}" } + "\n")
        }
    }.trim()

    fun answer(plan: PlanWorkspace, input: String, now: Long): PlanningDialogue.Turn? {
        if (!active(plan) || plan.hypotheticalView) return null
        val text = clean(input)
        val lower = text.lowercase(Locale.ROOT)
        if (lower.startsWith("what if ")) return PlanningDialogue.Turn(plan,
            "That is only an alternative; I haven't changed your checklist. For work schedules and dependency alternatives, say ‘use advanced planning’ first.")
        if (summaryRequest(text)) return PlanningDialogue.Turn(plan, summary(plan))
        if (lower in setOf("cancel", "cancel that change", "never mind", "nevermind")) return PlanningDialogue.Turn(withoutReview(plan, plan.pending?.optJSONObject("suspended")), "Cancelled that change. The checklist is unchanged.")
        // Canonical graph operations remain available; do not let free text consume them.
        if (lower in setOf("undo", "undo that", "redo", "undo saved changes", "redo saved changes", "save", "save plan", "apply plan", "show changes", "refresh saved tasks") || lower.startsWith("apply revision ")) return null
        if (plan.pending?.optString("kind") == "trip_reference") {
            val pending = plan.pending
            val matches = references(plan, text).filter { it.id in (0 until pending.getJSONArray("ids").length()).map { i -> pending.getJSONArray("ids").getString(i) } }
            if (matches.size == 1) return itemEdit(plan.copy(pending = pending.optJSONObject("suspended")), matches.single(), pending.getString("operation"), pending.optString("value"), now)
        }
        if (plan.pending?.optString("kind") == "trip_item_date") {
            val pending = plan.pending
            val date = TripDate.complete(pending, text) ?: text
            if (TripDate.read(date, plan, now)?.date != null) {
                val item = children(plan).singleOrNull { it.id == pending.optString("item") }
                if (item != null) return itemEdit(plan.copy(pending = pending.optJSONObject("suspended")), item, "date", date, now)
            }
        }
        val datePending = plan.pending?.takeIf { it.optString("kind") == "trip_date" }
        if (datePending != null) {
            val combined = TripDate.complete(datePending, text)
            if (combined != null) return setDate(plan, combined, datePending.optString("role", "travel date"), now)
        }
        if (plan.dialogue.question == "destination" && text.contains(',')) {
            val city = text.substringBefore(',').trim()
            val facts = PlanLanguage.facts(text) - "destination"
            if (facts.isNotEmpty() && rx("[\\p{L}][\\p{L} .'-]{1,60}").matches(city) &&
                !rx("\\b(?:i|we|why|what|where|when|how|can|could|should|yes|no)\\b").containsMatchIn(city))
                return PlanningDialogue.Turn(update(plan, facts = plan.facts + facts + ("destination" to city), question = ""),
                    "Noted: destination $city; " + facts.entries.joinToString { "${it.key}: ${it.value}" } + ".")
        }
        val clauses = text.split(rx("\\s*;\\s*|(?:,\\s*|\\s+and(?: then)?\\s+)(?=(?:remove|drop|delete|add|change|make|move|rename|replace|buy|book|pack|bring|take|download|print|carry|check|arrange|visit|explore|i (?:want|need)|we need|next |this |(?:the |my )?(?:exam|return|travel) |(?:[0-3]?\\d)(?:st|nd|rd|th)? [a-z])\\b)"))
        require(clauses.size <= 12) { "Let's work through at most twelve changes at a time" }
        if (clauses.size > 1) {
            var draft = plan
            val replies = mutableListOf<String>()
            for (clause in clauses) {
                val turn = single(draft, clause, now, validateDates = false) ?: return PlanningDialogue.Turn(withoutReview(plan, plan.pending), "I couldn't place ‘$clause’ yet. None of those changes were applied; please clarify that part.")
                if (turn.plan.revision == draft.revision && turn.plan.pending == draft.pending && !turn.text.startsWith("Already on"))
                    return PlanningDialogue.Turn(withoutReview(plan, plan.pending), "${turn.text} None of those changes were applied.")
                if (turn.plan.pending != null && turn.plan.pending != draft.pending) return PlanningDialogue.Turn(withoutReview(plan, plan.pending), "${turn.text} None of those changes were applied yet; send that detail first, then the changes together.")
                draft = turn.plan; replies += turn.text
            }
            dateConflict(draft.facts)?.let { return conflict(plan, "$it None of those changes were applied.") }
            // One conversational request is one undo operation, even with several changes.
            return PlanningDialogue.Turn(draft.copy(previousDraft = plan.checkpoint(), undoDrafts = PlanWorkspace.boundedHistory(plan.undoDrafts + plan.checkpoint())), replies.joinToString(" "))
        }
        single(plan, text, now)?.let { return it }
        if (lower in setOf("hi", "hey", "hello")) return PlanningDialogue.Turn(plan, "Hi. We can keep working on ${root(plan).title}.")
        if (lower in setOf("thanks", "thank you", "okay", "ok", "yes")) return PlanningDialogue.Turn(plan, "You're welcome. The checklist is here whenever you're ready.")
        if (lower in setOf("skip", "leave it open", "not sure", "i don't know", "dates aren't fixed")) return PlanningDialogue.Turn(withoutReview(plan, null).copy(dialogue = plan.dialogue.copy(question = "")), "We can leave that open and keep building the checklist.")
        if (lower in setOf("just listen", "just discuss", "just talk it through first", "don't add anything yet")) return PlanningDialogue.Turn(plan.copy(dialogue = plan.dialogue.copy(stance = "listen")), "Of course. We'll discuss it first; I won't add checklist items until you ask.")
        if (lower in setOf("help me organize it", "let's make the tasks", "lets make the tasks")) return PlanningDialogue.Turn(plan.copy(dialogue = plan.dialogue.copy(stance = "organize")), "Ready. Tell me what you want on the checklist.")
        if (lower in setOf("suggest steps", "what should i prepare", "give me ideas", "what am i missing", "what are we missing")) {
            val suggestions = if (plan.facts["purpose"]?.contains("exam", true) == true)
                listOf("Download admit card", "Check the exam's document requirements", "Bring required photos", "Pack pens", "Book transport")
            else listOf("Book transport", "Arrange accommodation", "Pack clothes", "Check travel documents")
            val available = suggestions.filter { title -> children(plan).none { key(it.title) == key(title) } }
            return PlanningDialogue.Turn(plan.copy(dialogue = plan.dialogue.copy(proposals = available)), if (available.isEmpty()) "Those essentials are already on your checklist." else "You could include:\n" + available.joinToString("\n") { "• $it" } + "\nSay ‘add those’ if they fit. These are suggestions, not booking or exam requirements I've verified.")
        }
        if (lower in setOf("add those", "use those", "use suggested steps", "add those steps") && plan.dialogue.proposals.isNotEmpty()) {
            val turn = add(plan.copy(dialogue = plan.dialogue.copy(stance = "organize")), plan.dialogue.proposals.joinToString(";"), true, now)
            return turn.copy(plan = turn.plan.copy(dialogue = turn.plan.dialogue.copy(proposals = emptyList())))
        }
        if (plan.dialogue.discussion != null || rx("^(?:options (?:are|:)|i(?:'m| am) (?:choosing|deciding) between) ").containsMatchIn(text)) {
            PlanningDialogue.answer(plan, text)?.let { return it }
        }
        if (ConversationMeaning.kind(text) == ConversationMeaning.Kind.POSSIBILITY) return PlanningDialogue.Turn(plan, "We can consider that. I haven't added it to the checklist. Tell me if you'd like to include it.")
        if (rx("^(?:why|how|what|could|should|can|do|is)\\b").containsMatchIn(text)) {
            val discussion = PlanningDialogue.answer(plan.copy(dialogue = plan.dialogue.copy(question = "")), text)
            return discussion?.copy(plan = plan) ?: PlanningDialogue.Turn(plan, "I haven't settled that detail. We can keep it open while we build the rest of the trip.")
        }
        return null
    }

    private fun single(plan: PlanWorkspace, input: String, now: Long, validateDates: Boolean = true): PlanningDialogue.Turn? {
        val text = clean(input).replaceFirst(rx("^(?:actually|no|also|and|please)[, ]+"), "")
        val lower = text.lowercase(Locale.ROOT)
        rx("^(?:yes[,]? |use |choose |go with |let's )(?:travel )?overnight$").matchEntire(text)?.let {
            return PlanningDialogue.Turn(update(plan, facts = plan.facts + ("travel choice" to "overnight travel (arrival time still needs checking)")), "Noted overnight travel; check the actual arrival time before booking.")
        }
        rx("^(?:i |we )?(?:need to |must )?(?:arrive|be there|report)(?: there)? (?:by|at) ([0-9: .]+(?:am|pm))$").matchEntire(text)?.let {
            return PlanningDialogue.Turn(update(plan, facts = plan.facts + ("arrive by" to it.groupValues[1])), "Arrival needed by ${it.groupValues[1]}.")
        }
        val dateEdit = rx("^(?:(?:change|move|set|make|shift) (?:the |my )?(?:travel date|trip date|date|trip|it|that)(?: (?:to|for|on))? |(?:i(?:'m| am) (?:thinking|leaving|travelling|traveling)|we(?:'re| are) leaving|i leave|we leave|it(?:'s|s| is)|travel date(?: is|:)?|leaving|departing)(?: on)? )(.+)$").matchEntire(text)
        val roleEdit = rx("^(?:the |my )?(exam(?: date)?|return(?: date)?)(?: is|:| on)? (.+)$").matchEntire(text)
        if (roleEdit != null && TripDate.read(roleEdit.groupValues[2], plan, now) != null)
            return setDate(plan, roleEdit.groupValues[2], if (roleEdit.groupValues[1].startsWith("exam", true)) "exam date" else "return date", now, validateDates)
        if (dateEdit != null && TripDate.read(dateEdit.groupValues[1], plan, now) != null) return setDate(plan, dateEdit.groupValues[1], "travel date", now, validateDates)
        if (TripDate.read(text, plan, now) != null) return setDate(plan, text, "travel date", now, validateDates)

        rx("^(.+?) is (?:the |my )?(exam|travel|return)(?: date)?$").matchEntire(text)?.let {
            if (TripDate.read(it.groupValues[1], plan, now) != null)
                return setDate(plan, it.groupValues[1], "${it.groupValues[2].lowercase(Locale.ROOT)} date", now, validateDates)
        }
        // An exam name is purpose, never a destination, even when the city question is pending.
        purpose(text)?.takeIf { !rx("^(?:add|download|print|check|book|bring|take)\\b").containsMatchIn(text) }?.let { purpose ->
            val rest = rx("\\b(?:exam(?: is)?|exam centre(?: is)?) in (.+)$").find(text)?.groupValues?.get(1)
            val facts = plan.facts + ("purpose" to purpose) + rest?.let { mapOf("destination" to it) }.orEmpty()
            val next = update(plan, facts = facts, question = if (facts["destination"] == null) "destination" else plan.dialogue.question)
            return PlanningDialogue.Turn(next, "Noted: the trip is for your $purpose." + if (facts["destination"] == null) " Which city is the exam in?" else "")
        }

        rx("^(?:remove|delete|drop|take off) (.+?)(?: from (?:the |my )?(?:list|checklist|plan))?$").matchEntire(text)?.let {
            return target(plan, it.groupValues[1], "remove", "", now)
        }
        rx("^(?:rename|change|replace) (.+?) (?:to|with) (.+)$").matchEntire(text)?.let {
            return target(plan, it.groupValues[1], "rename", it.groupValues[2], now)
        }
        rx("^(?:make that|make it) (.+)$").matchEntire(text)?.let {
            if (plan.trip!!.selected != null) return target(plan, "that", "quantity", it.groupValues[1], now)
        }
        rx("^(.+?) instead of (.+)$").matchEntire(text)?.let {
            val replacement = it.groupValues[1].replaceFirst(rx("^(?:let'?s |we should |i want to )"), "")
            return target(plan, it.groupValues[2], "rename", replacement, now)
        }
        rx("^(?:move|schedule|do) (.+?) (?:on|to|for) (.+)$").matchEntire(text)?.let {
            return target(plan, it.groupValues[1], "date", it.groupValues[2], now)
        }
        val explicitAdd = rx("^(?:add|include|put)(?: to (?:the |my )?(?:list|checklist))? (.+)$").matchEntire(text)
        val natural = rx("^(?:(?:i|we) (?:also )?(?:want|need|would like)(?: to)?|let'?s(?: also)?|we should(?: also)?) (.+)$").matchEntire(text)
        val body = explicitAdd?.groupValues?.get(1) ?: natural?.groupValues?.get(1) ?: text.takeIf { rx("^$action\\b").containsMatchIn(it) }
        if (body != null && !rx("\\b(?:maybe|perhaps|might|don't|not|if)\\b|[\"“”]").containsMatchIn(body)) return add(plan, body, explicitAdd != null, now, nounAllowed = rx("^(?:i|we) (?:also )?need").containsMatchIn(text))

        (rx("^destination(?: is|:)? (.+)$").matchEntire(text)?.groupValues?.get(1) ?: destination("trip $text"))?.let {
            return PlanningDialogue.Turn(update(plan, facts = plan.facts + ("destination" to it), question = "date"), "Destination set to $it." + if (plan.facts["travel date"] == null) " When are you travelling?" else "")
        }
        if (plan.dialogue.question == "destination" && rx("[\\p{L}][\\p{L} .'-]{1,60}").matches(text) &&
            !rx("\\b(?:i|we|it|is|end|stop|done|thanks|hello|yes|no|maybe|plan|want|need|should|skip|not|later|give|ideas|options|use|just|discuss|talk|listen|why|what|where|when|how|could|can|would|which|who|help|save|apply|cancel|summary|review|undo|redo|trip|holiday|vacation)\\b").containsMatchIn(text)) {
            return PlanningDialogue.Turn(update(plan, facts = plan.facts + ("destination" to text), question = "date"), "Destination set to $text." + if (plan.facts["travel date"] == null) " When are you travelling?" else "")
        }
        return null
    }

    private fun add(plan: PlanWorkspace, body: String, explicit: Boolean, now: Long, nounAllowed: Boolean = false): PlanningDialogue.Turn {
        if (plan.dialogue.stance == "listen" && !explicit) return PlanningDialogue.Turn(plan, "We can discuss that first. It hasn't been added; say ‘add …’ when you've decided.")
        val parts = body.split(if (nounAllowed) rx(";|,\\s*|\\s+and\\s+") else rx(";|,\\s*|\\s+and\\s+(?=(?:$action)\\b)")).filter { it.isNotBlank() }
        require(parts.size <= 12) { "Let's add at most twelve items at once" }
        val added = mutableListOf<TripChecklistItem>()
        val existing = children(plan).toMutableList()
        for (part in parts) {
            var title = part.trim().replaceFirst(rx("^(?:also |to )"), "")
            if (rx("^(?:somewhere|a place) to stay$").matches(title)) title = "Arrange accommodation"
            val atDestination = plan.facts["destination"]?.let { title.endsWith(" in $it", true) } == true
            val city = plan.facts["destination"]?.let(Regex::escape)
            val afterCity = city != null && rx("after (?:returning|coming back)(?: from $city)?$").containsMatchIn(title)
            val duringCity = city != null && rx("(?:during (?:my |the )?(?:stay|trip) in|while in|when in) $city$").containsMatchIn(title)
            val phase = when {
                afterCity -> "After returning"
                duringCity -> "During the trip"
                rx("\\b(?:after returning|after (?:the |my )?trip|when (?:i|we) (?:get|come) back)\\b").containsMatchIn(title) -> "After returning"
                rx("\\b(?:during (?:the |my )?trip|while (?:i'm|we're) there|there)\\b").containsMatchIn(title) || rx("^(?:visit|explore)\\b").containsMatchIn(title) || atDestination -> "During the trip"
                else -> "Before leaving"
            }
            val offsetMatch = rx("\\s+(?:the )?(day|\\d+ days?) before (?:leaving|travel|(?:the |my )?trip)$").find(title)
            val offset = offsetMatch?.groupValues?.get(1)?.let { if (it.equals("day", true)) -1 else -it.substringBefore(' ').toInt() }
            title = title.removeSuffix(offsetMatch?.value.orEmpty()).replace(rx("\\s+(?:before (?:leaving|going)|during (?:the |my )?trip|while (?:i'm|we're) there|after returning|there)$"), "").trim()
            if (city != null) title = title.replace(rx("\\s+(?:before (?:leaving for|going to)|after (?:returning|coming back) from|during (?:my |the )?(?:stay|trip) in|while in|when in) $city$"), "").trim()
            if (atDestination && title.endsWith(" in ${plan.facts.getValue("destination")}", true)) title = title.dropLast(" in ${plan.facts.getValue("destination")}".length).trim()
            require(title.isNotBlank() && title.length <= 200) { "Keep each checklist item between 1 and 200 characters" }
            if (!explicit && !nounAllowed && !rx("^$action\\b").containsMatchIn(title)) return PlanningDialogue.Turn(plan, "Should ‘$title’ be a checklist item? Say ‘add $title’ to include it.")
            title = title.replaceFirstChar { it.uppercase() }
            if (existing.none { key(it.title) == key(title) }) {
                val restored = plan.removed.singleOrNull { key(it.title) == key(title) }
                val item = TripChecklistItem(id = restored?.id ?: UUID.randomUUID().toString(), title = title, phase = phase,
                    date = restored?.deadline?.takeIf { offset == null }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(plan.timezone)).toLocalDate().toString() }, offset = offset)
                existing += item; added += item
            }
        }
        if (added.isEmpty()) return PlanningDialogue.Turn(plan, "Already on the checklist; I haven't added duplicates.")
        val next = update(plan, items = existing, selected = added.last().id)
        val message = "Added ${added.joinToString { "**${it.title}**" }} to ${if (added.all { it.phase == "Before leaving" }) "the preparation checklist" else "the trip checklist"}."
        return PlanningDialogue.Turn(next, message + warnings(next, now) + if (added.any { it.offset != null } && day(plan, "travel date") == null) " I'll date the relative items when you choose the travel date." else "")
    }
    private fun key(title: String) = clean(title).lowercase(Locale.ROOT).replace(rx("^(?:buying|booking|packing|bringing|taking|downloading|visiting)\\b")) { m ->
        mapOf("buying" to "buy", "booking" to "book", "packing" to "pack", "bringing" to "bring", "taking" to "take", "downloading" to "download", "visiting" to "visit").getValue(m.value.lowercase(Locale.ROOT))
    }.replace(rx("\\b(?:the|some|a|an)\\s+"), "")
    private fun references(plan: PlanWorkspace, reference: String): List<TripChecklistItem> {
        val items = TripChecklistState.phases.flatMap { phase -> children(plan).filter { it.phase == phase } }
        val text = key(reference).replace(rx("^(?:flying|fly|flight)$"), "flights").removePrefix("item ").removePrefix("step ").removeSuffix(" one").removeSuffix(" from list").removePrefix("the ").removeSuffix(" one")
        if (text in setOf("it", "that", "this", "last added")) return items.filter { it.id == plan.trip!!.selected }
        val n = text.toIntOrNull() ?: mapOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "last" to items.size)[text]
        if (n != null) return listOfNotNull(items.getOrNull(n - 1))
        val exact = items.filter { key(it.title) == text }; if (exact.isNotEmpty()) return exact
        val words = text.split(' ').filter { it.length > 1 }
        return if (words.isEmpty()) emptyList() else items.filter { item -> words.all { word -> rx("\\b${Regex.escape(word)}\\b").containsMatchIn(key(item.title)) } }
    }
    private fun target(original: PlanWorkspace, ref: String, op: String, value: String, now: Long): PlanningDialogue.Turn {
        val plan = withoutReview(original, original.pending)
        val matches = references(plan, ref)
        if (matches.size == 1) return itemEdit(plan, matches.single(), op, value, now)
        if (matches.isEmpty()) return PlanningDialogue.Turn(plan, "I couldn't find ‘$ref’ on the checklist. Which item did you mean?")
        val question = "Which one: ${matches.joinToString { it.title }}?"
        return PlanningDialogue.Turn(withoutReview(plan, JSONObject().put("kind", "trip_reference").put("ids", JSONArray(matches.map { it.id }))
            .put("operation", op).put("value", value).put("question", question).put("suspended", plan.pending ?: JSONObject.NULL)), question)
    }
    private fun itemEdit(plan: PlanWorkspace, item: TripChecklistItem, op: String, value: String, now: Long): PlanningDialogue.Turn {
        if (op == "remove") return PlanningDialogue.Turn(update(plan, items = children(plan).filterNot { it.id == item.id }, pending = plan.pending?.takeUnless { it.optString("kind") == "trip_reference" }), "Removed **${item.title}** from the checklist.")
        var changed = item
        when (op) {
            "rename" -> changed = item.copy(title = value.replaceFirstChar { it.uppercase() })
            "quantity" -> {
                val noun = item.title.replaceFirst(rx("^$action\\s+"), "").replaceFirst(rx("^\\d+\\s+"), "")
                if (!value.contains(noun, true)) return PlanningDialogue.Turn(plan, "Do you mean changing **${item.title}**? Tell me the new item wording.")
                changed = item.copy(title = item.title.substringBefore(' ') + " " + value)
            }
            "date" -> {
                val date = TripDate.read(value, plan, now)
                if (date?.date == null) return PlanningDialogue.Turn(withoutReview(plan, JSONObject().put("kind", "trip_item_date").put("item", item.id).put("day", date?.day ?: 0).put("suspended", plan.pending ?: JSONObject.NULL).put("question", "Which date should ${item.title} have? Include the month.")), "Which date should **${item.title}** have? Include the month, for example ‘move ${item.title} to 25 July 2027’.")
                changed = item.copy(date = date.date.toString(), offset = null)
            }
        }
        require(changed.title.length in 1..200)
        if (children(plan).any { it.id != item.id && key(it.title) == key(changed.title) }) return PlanningDialogue.Turn(plan, "**${changed.title}** is already on the checklist. I haven't created a duplicate.")
        return PlanningDialogue.Turn(update(plan, items = children(plan).map { if (it.id == item.id) changed else it }, selected = item.id, pending = plan.pending?.takeUnless { it.optString("kind") == "trip_reference" }),
            if (op == "date") "Set **${item.title}** for ${display(changed.date!!)} as a dated subtask." + warnings(update(plan, items = children(plan).map { if (it.id == item.id) changed else it }), now) else "Changed **${item.title}** to **${changed.title}**.")
    }
    private fun withoutReview(plan: PlanWorkspace, pending: JSONObject?) = plan.copy(
        pending = pending, reviewedRevision = null, reviewedAt = 0, reviewProcess = null, reviewDeviceTimezone = null)

    private fun conflict(plan: PlanWorkspace, question: String) = PlanningDialogue.Turn(
        withoutReview(plan, JSONObject().put("kind", "trip_conflict").put("question", question)), question)
    private fun dateConflict(facts: Map<String, String>): String? {
        fun date(key: String) = facts[key]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val travel = date("travel date"); val exam = date("exam date"); val back = date("return date")
        return when {
            travel != null && exam != null && travel > exam -> "That puts travel after the exam. Should we change the travel date or the exam date?"
            travel != null && back != null && back < travel -> "That puts the return before departure. Which date should we change?"
            else -> null
        }
    }
    private fun setDate(plan: PlanWorkspace, value: String, role: String, now: Long, validateDates: Boolean = true): PlanningDialogue.Turn {
        val result = TripDate.read(value, plan, now) ?: return PlanningDialogue.Turn(plan, "Which date did you mean?")
        if (result.date == null) {
            val pending = JSONObject().put("kind", "trip_date").put("role", role).put("value", value).put("day", result.day ?: 0).put("question", result.question)
            return PlanningDialogue.Turn(withoutReview(plan, pending), result.question ?: "Which month?")
        }
        val date = result.date
        if (validateDates) dateConflict(plan.facts + (role to date.toString()))?.let { return conflict(plan, it) }
        val next = update(plan, facts = plan.facts + (role to date.toString()), pending = plan.pending?.takeUnless { it.optString("kind") == "trip_conflict" || it.optString("kind") == "trip_date" && it.optString("role") == role },
            question = if (plan.facts["destination"] == null) "destination" else "")
        return PlanningDialogue.Turn(next, "${role.replaceFirstChar { it.uppercase() }} ${if (plan.facts[role] == null) "set to" else "changed to"} ${display(date.toString())}." +
            (if (children(plan).isEmpty() && role == "travel date") " What would you like to prepare or do during the trip?" else "") + warnings(next, now))
    }
}

/** Calendar dates, not work-session times. A bare ordinal never silently chooses a month. */
internal object TripDate {
    data class Result(val date: LocalDate? = null, val day: Int? = null, val question: String? = null)
    private val months = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    fun complete(pending: JSONObject, text: String): String? {
        val raw = text.lowercase(Locale.ROOT).trim().removePrefix("in ")
        val month = months.indexOfFirst { raw == it || raw == it.take(3) || raw.startsWith("$it ") }
        return if (month >= 0 && pending.optInt("day") > 0) "${pending.getInt("day")} $raw" else null
    }
    fun read(input: String, plan: PlanWorkspace, now: Long): Result? {
        val text = input.lowercase(Locale.ROOT).trim().replace(Regex("^(?:on |the )"), "").trimEnd('.', '?', '!')
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.of(plan.timezone)).toLocalDate()
        fun checked(date: LocalDate?): Result = when {
            date == null -> Result(question = "That date doesn't exist. Which date did you mean?")
            date < today -> Result(question = "That date has passed. Which future date should we use?")
            else -> Result(date)
        }
        if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(text)) return checked(runCatching { LocalDate.parse(text) }.getOrNull())
        if (text in setOf("today", "tomorrow", "day after tomorrow")) return Result(today.plusDays(if (text == "today") 0 else if (text == "tomorrow") 1 else 2))
        Regex("in (\\d{1,3}) days?").matchEntire(text)?.let { return Result(today.plusDays(it.groupValues[1].toLong())) }
        val weekday = Regex("(?:(next|this) )?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)").matchEntire(text)
        if (weekday != null) {
            val target = java.time.DayOfWeek.valueOf(weekday.groupValues[2].uppercase(Locale.ROOT))
            var date = today.with(java.time.temporal.TemporalAdjusters.next(target))
            if (weekday.groupValues[1] == "this") date = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(target))
            return Result(date)
        }
        val bare = Regex("(\\d{1,2})(?:st|nd|rd|th)?").matchEntire(text)
        if (bare != null) {
            val n = bare.groupValues[1].toInt()
            if (n !in 1..31) return Result(question = "Which day of the month did you mean?")
            val prior = plan.facts["travel date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            return if (prior == null) Result(day = n, question = "The ${bare.value} of which month?") else checked(runCatching { prior.withDayOfMonth(n) }.getOrNull())
        }
        val monthPattern = months.joinToString("|") { "$it|${it.take(3)}" }
        val forward = Regex("(\\d{1,2})(?:st|nd|rd|th)?(?: of)? ($monthPattern)(?: (\\d{4}))?").matchEntire(text)
        val reverse = Regex("($monthPattern) (\\d{1,2})(?:st|nd|rd|th)?(?:,? (\\d{4}))?").matchEntire(text)
        val match = forward ?: reverse ?: return null
        val day = match.groupValues[if (forward != null) 1 else 2].toInt()
        val word = match.groupValues[if (forward != null) 2 else 1]
        val month = months.indexOfFirst { it == word || it.take(3) == word } + 1
        val suppliedYear = match.groupValues[3].toIntOrNull()
        var year = suppliedYear ?: today.year
        if (suppliedYear == null && month < today.monthValue || suppliedYear == null && month == today.monthValue && day < today.dayOfMonth) year++
        return checked(runCatching { YearMonth.of(year, month).atDay(day) }.getOrNull())
    }
}
