package com.focusbyrj.app.util.assistant

import android.content.Context
import android.util.Base64
import com.focusbyrj.app.util.AyvaTalkEngine
import com.focusbyrj.app.util.SmartDateParser
import com.focusbyrj.app.util.TalkAction
import com.focusbyrj.app.util.OfflineNluEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Bounded planning dialogue. Sheets and typed replies enter this same dispatcher. */
internal object AssistantGuidedPlanning {
    private fun get(state: AssistantSession) = state.featureState?.optJSONObject("guidedPlan")?.let { runCatching { PlanningDraft.read(it) }.getOrNull() }
    private data class StartRequest(val domain: String?, val detail: String)
    private val requestWrapper = Regex("(?i)^(?:(?:can|could|shall|would) we(?: please)?|let's|lets|help (?:me|us)|i (?:want|would like) to|i'd like to)\\s+")
    private val requestVerb = Regex("(?i)^(?:plan\\b|prepare for\\b|(?:make|create|build|put together) (?:a |my |our )?plan\\b)\\s*(.*)$")
    private val domainPattern = Regex("(?i)^(?:(?:my|our|the|a|an|next) )?(trip|trips|vacation|holiday|exam|exams|day|today)(?:\\s+(.*))?$")
    private fun domain(value: String) = when (value.lowercase(java.util.Locale.ROOT)) {
        "trip", "trips", "vacation", "holiday" -> "trip"
        "exam", "exams" -> "exam"
        else -> "day"
    }
    /** One request grammar is shared by interpretation, routing and the draft owner. */
    private fun startRequest(input: String): StartRequest? {
        var body = OfflineNluEngine.normalizeRequest(input).trimEnd('?', '.', '!')
        repeat(3) { body = body.replaceFirst(requestWrapper, "") }
        val request = requestVerb.matchEntire(body) ?: return null
        val scope = request.groupValues[1].replaceFirst(Regex("(?i)^(?:(?:some )?(?:things|tasks|steps|preparations?|a checklist) )?for\\s+"), "").trim()
        val target = domainPattern.matchEntire(scope) ?: return StartRequest(null, scope)
        val detail = target.groupValues[2].trim()
        if (target.groupValues[1].lowercase(java.util.Locale.ROOT) in setOf("vacation", "holiday") && Regex("(?i)^mode\\b").containsMatchIn(detail)) return null
        return StartRequest(domain(target.groupValues[1]), detail)
    }
    fun isRequest(input: String) = startRequest(input) != null
    fun acceptsLiteral(state: AssistantSession) = state.topic == "guided_planning" && get(state)?.let { !it.saved && !it.paused && AssistantContext.live(it.at) } == true
    private fun put(state: AssistantSession, draft: PlanningDraft) {
        if (state.featureState == null) state.featureState = JSONObject()
        state.featureState!!.put("guidedPlan", draft.json())
    }
    private fun own(state: AssistantSession, draft: PlanningDraft) { state.clearPending(); state.switchTopic("guided_planning"); put(state, draft) }
    fun question(draft: PlanningDraft) = when (draft.question) {
        "items" -> when (draft.domain) {
            "exam" -> "Which subjects or topics do you want to study? Separate them with commas or semicolons. I'll use your topics, not invent a syllabus."
            "trip" -> "What preparation steps do you want? Separate them with commas or semicolons, or choose the optional trip checklist. I can't check current visas, weather, prices or bookings."
            else -> "What would you like to get done? Separate your steps with commas or semicolons. This starts an unsaved draft."
        }
        "deadline" -> "Should these tasks share a deadline? Give a day and time, or say ‘skip’ for an undated plan. A deadline is not a scheduled work interval."
        "budget" -> "How much time is available for this plan? Give 1–480 minutes, or ‘skip’ for a checklist without a time budget. I won't assume how long your steps take."
        else -> "Review the titles and shared deadline below. Choose one checklist or separate tasks; you can remove, rename or reorder an entry before saving."
    }
    fun command(id: String, revision: Int, value: String) = "/ayva plan $id $revision " + Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)

    suspend fun answer(context: Context, state: AssistantSession, input: String): AyvaTalkEngine.TalkResponse? {
        val text = input.trim().trimEnd('?', '.', '!')
        val lower = text.lowercase(java.util.Locale.ROOT)
        val choosingScope = state.topic == "planning_scope" && state.expected == "planning_domain"
        if (choosingScope && lower in setOf("cancel planning", "discard plan", "cancel")) {
            state.clearPending(); state.switchTopic("conversation")
            return reply("Cancelled planning. Your saved tasks are unchanged.")
        }
        val scopeAnswer = if (choosingScope) startRequest("plan $text")?.takeIf { it.domain != null } else null
        val start = startRequest(text) ?: scopeAnswer?.let { if (it.domain == "day") it.copy(detail = "from scratch ${it.detail}".trim()) else it }
        val guidedStart = start?.domain != null && (start.domain != "day" || Regex("(?i)^(?:together|from scratch)\\b").containsMatchIn(start.detail))
        var draft = get(state)
        if (guidedStart) {
            if (draft != null && !draft.saved && AssistantContext.live(draft.at)) return render(state, draft, "An unfinished ${draft.domain} plan is still here. Say ‘cancel planning’ before starting another, or ‘back to my plan’ to continue.")
            AssistantConversation.suspendQuestion(state)
            val detail = start!!.detail.replaceFirst(Regex("(?i)^(?:together|from scratch)[: ]*"), "").trim()
            draft = PlanningDraft(domain = start.domain!!, goal = detail.takeIf { it.length in 1..500 })
            own(state, draft)
            return render(state, draft, if (detail.length > 500) "Keep goal details under 500 characters. You can set them with ‘goal: …’; give concrete steps separately." else "")
        }
        if (lower == "retry plan reminders") return reply("Plan reminder reconciliation requested. ${AssistantPlanStore.retryEffects(context)} saved plan(s) still need a retry. Android delivery still depends on permissions and device settings.")
        if (start != null && !AssistantPlanning.isDirectRequest(text) && draft != null && !draft.saved && AssistantContext.live(draft.at))
            return render(state, draft, "An unfinished ${draft.domain} plan is still here. Say ‘cancel planning’ before starting another, or ‘back to my plan’ to continue.")
        if (start != null && !AssistantPlanning.isDirectRequest(text)) {
            AssistantConversation.suspendQuestion(state); state.clearPending(); state.switchTopic("planning_scope"); state.expected = "planning_domain"
            return reply("Would you like an order for saved tasks, or a guided checklist for a day, exam or trip? I haven't created a task from that planning request.", listOf(
                TalkAction.AskQuery("plan my day", "Use saved tasks"), TalkAction.AskQuery("plan my day from scratch", "Build a checklist"), TalkAction.AskQuery("plan my trip", "Trip preparation")))
        }
        if (draft == null) {
            return if (text.startsWith("/ayva plan")) reply("That planning control is no longer available. Start a fresh plan.") else null
        }
        if (!AssistantContext.live(draft.at)) {
            state.featureState?.remove("guidedPlan")
            return if (text.startsWith("/ayva plan") || lower in setOf("back to my plan", "show my draft", "review plan")) reply("That draft expired. Start a fresh plan; saved tasks are unchanged.") else null
        }
        if (lower in setOf("cancel planning", "discard plan")) { state.clearPending(); state.featureState?.remove("guidedPlan"); state.switchTopic("conversation"); return reply("Discarded the unsaved plan. Saved tasks are unchanged.") }
        if (text.startsWith("/ayva plan-save ")) {
            val parts = text.split(' ')
            if (parts.size != 5 || parts[2] != draft.id || parts[3].toIntOrNull() != draft.revision || parts[4] != draft.nonce || draft.paused || draft.saved || state.topic != "guided_planning") return reply("That save control is stale. Review the current plan again.")
            val saved = AssistantPlanStore.save(context, draft)
            put(state, draft.copy(saved = true, nonce = null)); state.clearPending()
            state.undo = saved.undo(); state.undoId = UUID.randomUUID().toString(); state.focusTasks(saved.tasks.map { it.id }); state.rememberTaskList(saved.tasks.map { it.id })
            val message = "${if (saved.repeated) "This plan was already saved" else "Added ${saved.tasks.size} task(s)"}${if (draft.checklist) " with ${draft.items.size} checklist items" else ""}." +
                if (saved.effectsPending) " Tasks are saved, but reminder/widget refresh needs a retry. Say ‘retry plan reminders’." else ""
            return AssistantPresentation.attach(reply(message, listOf(TalkAction.AskQuery("/ayva undo ${state.undoId}", "Undo plan"))),
                saved.tasks.singleOrNull()?.let { AssistantItemReference("task", it.id, it.title, AssistantPresentation.due(it.dueDate)) }, AssistantPresentation.taskReceipt(state.undo, saved.effectsPending))
        }
        if (lower in setOf("what are we planning", "what plan are we working on", "show the planning goal")) return if (draft.saved) reply("That plan is already saved. Show your tasks to inspect or change it.") else render(state, draft)
        var answer = if (Regex("(?i)^(?:goal|name|steps|topics):").containsMatchIn(input.trim())) input.trim() else text
        val fromControl = text.startsWith("/ayva plan ")
        if (fromControl) {
            val parts = text.split(' ')
            if (parts.size != 5 || parts[2] != draft.id || parts[3].toIntOrNull() != draft.revision || draft.saved) return reply("That question or review changed. Open the current plan again.")
            answer = runCatching { String(Base64.decode(parts[4], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8) }.getOrElse { return reply("I couldn't read that planning answer.") }
            if (answer.length > 2000) return reply("Keep planning answers under 2,000 characters.")
        }
        if (lower in setOf("back to my plan", "back to my trip plan", "back to my exam plan", "show my draft", "review plan")) {
            if (draft.saved) return reply("That plan has already been saved. Ask for your tasks to change the saved items.")
            draft = draft.copy(paused = false, nonce = null, revision = draft.revision + 1, timezone = java.util.TimeZone.getDefault().id)
            own(state, draft)
            return render(state, draft)
        }
        if (draft.saved || (!fromControl && (state.topic != "guided_planning" || draft.paused))) return null
        val value = answer.lowercase(java.util.Locale.ROOT)
        // Explicit scope edits apply to the draft; ordinary app commands interrupt it.
        val editing = Regex("^(?:remove|drop|rename|move) (?:item |step )?\\d+\\b|^(?:deadline(?: \\d+)?|budget|effort|steps|topics|name|goal):").containsMatchIn(value) || value in setOf("one checklist", "separate tasks", "use optional trip checklist", "skip", "not sure", "review plan", "keep editing", "suggest study steps")
        if (!fromControl && !editing && (AssistantInterpretation.interpret(answer).kind in setOf(UtteranceKind.CONVERSATION, UtteranceKind.QUESTION) ||
                (draft.question != "items" && com.focusbyrj.app.util.OfflineNluEngine.isExplicitCreation(answer)) || AssistantPreferences.find(answer) != null || Regex("(?i)^(?:read|search|find) (?:my )?notes?\\b").containsMatchIn(answer) || Regex("(?i)^(?:show|list|lock|unlock|set|change|complete|delete|start|stop|open|look up|define|remind me|add task|create task|/)").containsMatchIn(answer))) {
            put(state, draft.copy(paused = true, nonce = null)); state.clearPending(); return null
        }
        if (value == "cancel planning") { state.clearPending(); state.featureState?.remove("guidedPlan"); state.switchTopic("conversation"); return reply("Discarded the unsaved plan. Saved tasks are unchanged.") }
        if (value in setOf("yes", "no", "okay", "ok", "confirm", "do it", "not sure", "skip") && draft.question == "items") return render(state, draft, "Name your own steps first. I haven't added anything to the draft.")
        if (value == "keep editing") return render(state, draft.copy(nonce = null), "Type ‘rename 2 to …’, ‘remove 2’, ‘move 2 before 1’, ‘deadline: Friday at 6pm’, ‘deadline 2: tomorrow at 4pm’, ‘name: Trip checklist’, ‘one checklist’ or ‘separate tasks’.")
        var changed = draft.copy(revision = draft.revision + 1, nonce = null, reviewedAt = 0, paused = false)
        val remove = Regex("(?i)^(?:remove|drop) (?:item |step )?(\\d+)$").matchEntire(answer)
        val rename = Regex("(?i)^rename (?:item |step )?(\\d+) to (.+)$").matchEntire(answer)
        val itemDeadline = Regex("(?i)^deadline (\\d+): (.+)$").matchEntire(answer)
        val move = Regex("(?i)^move (?:item |step )?(\\d+) before (\\d+)$").matchEntire(answer)
        when {
            value.startsWith("goal:") -> {
                val goal = answer.substringAfter(':').trim()
                if (goal.length !in 1..500) return render(state, draft, "Use goal details of 1–500 characters.")
                changed = changed.copy(goal = goal)
            }
            value.startsWith("name:") -> {
                val name = answer.substringAfter(':').trim()
                if (name.length !in 1..200) return render(state, draft, "Use a plan name of 1–200 characters.")
                changed = changed.copy(title = name)
            }
            itemDeadline != null -> {
                val index = itemDeadline.groupValues[1].toIntOrNull()?.minus(1)
                if (index == null || index !in draft.items.indices) return render(state, draft, "Choose a displayed item number for the deadline.")
                val field = itemDeadline.groupValues[2].trim()
                val inherit = field.equals("shared", true)
                val none = field.lowercase() in setOf("none", "no deadline", "skip")
                val date = if (none || inherit) null else SmartDateParser.parse(field)
                if (!none && !inherit && (date?.timestamp == null || date.hasTaskText)) return render(state, draft, "Give an exact date/time, ‘none’, or ‘shared’ for that item's deadline.")
                changed = changed.copy(items = draft.items.mapIndexed { i, item -> if (i == index) item.copy(dueDate = date?.timestamp, inheritDeadline = inherit) else item })
            }
            value == "suggest study steps" && draft.domain == "exam" -> {
                if (draft.items.size > 6 || draft.items.any { it.source == "optional template" }) return render(state, draft, "Expand up to six user-supplied topics once, or edit the steps yourself. This won't invent a syllabus or study durations.")
                changed = changed.copy(items = draft.items.flatMap { topic -> listOf("Learn", "Practise", "Revise").map { step -> PlanningItem(title = "$step: ${topic.title}", source = "optional template", dueDate = topic.dueDate, inheritDeadline = topic.inheritDeadline) } })
                if (changed.items.any { it.title.length > 200 }) return render(state, draft, "Shorten the topic titles before adding study steps.")
            }
            remove != null -> {
                val index = remove.groupValues[1].toIntOrNull()?.minus(1)
                if (index == null || index !in draft.items.indices) return render(state, draft, "Choose a displayed item number.")
                changed = changed.copy(items = draft.items.filterIndexed { i, _ -> i != index })
            }
            rename != null -> {
                val index = rename.groupValues[1].toIntOrNull()?.minus(1); val title = rename.groupValues[2].trim()
                if (index == null || index !in draft.items.indices || title.length !in 1..200) return render(state, draft, "Choose a displayed item and a title of 1–200 characters.")
                changed = changed.copy(items = draft.items.mapIndexed { i, item -> if (i == index) item.copy(title = title, source = "user") else item })
            }
            move != null -> {
                val a = move.groupValues[1].toIntOrNull()?.minus(1); val b = move.groupValues[2].toIntOrNull()?.minus(1)
                if (a == null || b == null || a !in draft.items.indices || b !in draft.items.indices || a == b) return render(state, draft, "Choose two different displayed item numbers.")
                val list = draft.items.toMutableList(); val item = list.removeAt(a); list.add(if (a < b) b - 1 else b, item); changed = changed.copy(items = list)
            }
            value in setOf("one checklist", "separate tasks") -> changed = changed.copy(checklist = value == "one checklist")
            value.startsWith("effort:") -> {
                val effort = AssistantPlanning.minutes(value.substringAfter(':').trim()) ?: return render(state, draft, "Give total estimated effort in 1–480 minutes, for example ‘effort: 120 minutes’.")
                changed = changed.copy(effort = effort)
            }
            value.startsWith("deadline:") || draft.question == "deadline" -> {
                val field = answer.substringAfter(':', answer).trim()
                val skipped = field.lowercase() in setOf("skip", "not sure", "none", "no deadline")
                val date = if (skipped) null else SmartDateParser.parse(field)
                if (!skipped && (date?.timestamp == null || date.hasTaskText)) return render(state, draft, "Give an exact day/time, or skip. Other answers are retained.")
                changed = changed.copy(deadline = date?.timestamp, question = if (draft.question == "deadline") "budget" else draft.question)
            }
            value.startsWith("budget:") || draft.question == "budget" -> {
                val field = answer.substringAfter(':', answer).trim()
                val minutes = if (field.lowercase() in setOf("skip", "not sure", "none")) null else AssistantPlanning.minutes(field)
                if (minutes == null && field.lowercase() !in setOf("skip", "not sure", "none")) return render(state, draft, "Give 1–480 whole minutes, or skip. Other answers are retained.")
                changed = changed.copy(budget = minutes, question = "review")
            }
            draft.question == "items" || value.startsWith("steps:") || value.startsWith("topics:") -> {
                val template = value == "use optional trip checklist" && draft.domain == "trip"
                val titles = if (template) listOf("Check travel documents and current requirements", "Arrange transport", "Prepare packing checklist", "Check bookings and emergency contacts")
                    else answer.substringAfter(':', answer).split(Regex("\\s*[;,]\\s*")).map(String::trim).filter(String::isNotEmpty)
                if (titles.isEmpty() || titles.size > 20 || titles.any { it.length > 200 }) return render(state, draft, "Use 1–20 steps of up to 200 characters each, separated with commas or semicolons.")
                if (titles.map { it.lowercase() }.distinct().size != titles.size) return render(state, draft, "There are duplicate step titles. Give each step a distinct name.")
                changed = changed.copy(items = titles.map { PlanningItem(title = it, source = if (template) "optional template" else "user") }, question = if (draft.question == "items") "deadline" else draft.question)
            }
            else -> return render(state, draft, "I haven't changed the draft. Use a displayed choice or an explicit edit such as ‘rename 2 to …’. To chat about something else, say it directly.")
        }
        if (changed.items.isEmpty()) changed = changed.copy(question = "items")
        own(state, changed)
        return render(state, changed)
    }

    private fun render(state: AssistantSession, original: PlanningDraft, prefix: String = ""): AyvaTalkEngine.TalkResponse {
        val checklistConflict = original.checklist && original.items.any { !it.inheritDeadline && it.dueDate != original.deadline }
        val conflict = original.effort != null && original.budget != null && original.effort > original.budget
        val draft = if (original.question == "review" && !original.paused && !original.saved && !conflict && !checklistConflict) original.copy(nonce = UUID.randomUUID().toString(), reviewedAt = System.currentTimeMillis()) else original.copy(nonce = null)
        put(state, draft)
        val summary = draft.items.mapIndexed { index, item -> "${index + 1}. ${item.title}${(if (item.inheritDeadline) draft.deadline else item.dueDate)?.let { " — ${SmartDateParser.formatDueDate(it)}" } ?: " — no due date"}${if (item.source == "optional template") " · optional suggestion" else ""}" }.joinToString("\n")
        val dates = "Deadline: ${draft.deadline?.let(SmartDateParser::formatDueDate) ?: "none"} · ${draft.timezone}." + (draft.budget?.let { " Available: $it minutes for the whole plan." } ?: "") + (draft.effort?.let { " Your estimated total effort: $it minutes." } ?: "")
        val warning = if (checklistConflict) "\nChecklist items cannot have independent deadlines. Choose separate tasks, or reset the individual deadlines to ‘shared’." else if (conflict) "\nYour stated effort exceeds the available time. Remove work, change ‘effort:’ or ‘budget:’ before saving. Nothing is scheduled automatically." else if (draft.budget != null && draft.effort == null) "\nCompletion time is unknown; the budget doesn't prove these steps fit." else ""
        val actions = mutableListOf<TalkAction>()
        if (draft.question == "items" && draft.domain == "trip") actions += TalkAction.AskQuery(command(draft.id, draft.revision, "use optional trip checklist"), "Use optional trip checklist")
        if (draft.question in setOf("deadline", "budget")) actions += TalkAction.AskQuery(command(draft.id, draft.revision, "skip"), "Skip for now")
        if (draft.question == "review") {
            if (draft.domain == "exam" && draft.items.size <= 6 && draft.items.none { it.source == "optional template" }) actions += TalkAction.AskQuery(command(draft.id, draft.revision, "suggest study steps"), "Suggest study steps")
            actions += TalkAction.AskQuery(command(draft.id, draft.revision, if (draft.checklist) "separate tasks" else "one checklist"), if (draft.checklist) "Separate tasks" else "One checklist")
            if (draft.nonce != null) actions += TalkAction.AskQuery("/ayva plan-save ${draft.id} ${draft.revision} ${draft.nonce}", "Add ${if (draft.checklist) 1 else draft.items.size} task(s)")
        }
        actions += TalkAction.AskQuery(command(draft.id, draft.revision, "cancel planning"), "Cancel planning")
        val goal = draft.goal?.let { "Goal: $it" + if (draft.question == "items") "\nI'll keep these details in the saved task descriptions." else "" }.orEmpty()
        val response = reply(listOf(prefix, "Planning · ${draft.title ?: draft.domain}", goal, question(draft), summary, dates + warning, "Nothing is saved until you use Add tasks in this review.").filter(String::isNotBlank).joinToString("\n\n"), actions)
        val root = JSONObject(response.jsonPayload ?: "{}")
        val display = root.optJSONObject("presentation") ?: JSONObject().put("version", 1)
        display.put("plan", JSONObject().put("id", draft.id).put("revision", draft.revision).put("at", draft.at).put("question", draft.question).put("goal", draft.goal ?: JSONObject.NULL).put("prompt", question(draft)).put("items", JSONArray(draft.items.map { it.json().put("resolvedDue", (if (it.inheritDeadline) draft.deadline else it.dueDate) ?: JSONObject.NULL) })))
        return response.copy(jsonPayload = root.put("presentation", display).toString())
    }
    private fun reply(text: String, actions: List<TalkAction> = emptyList()) = AyvaTalkEngine.TalkResponse(text, actions, "guided_planning", AyvaTalkEngine.serializeActionsJson("guided_planning", actions))
}
