package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.WorkSession
import android.content.Context
import com.focusbyrj.app.data.planning
import com.focusbyrj.app.util.AyvaTalkEngine
import com.focusbyrj.app.util.SmartDateParser
import com.focusbyrj.app.util.TalkAction
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** Plan owns its input. Unsupported conversation never falls through to task creation. */
internal object ConversationPlanner {
    private const val UNRECOGNIZED = "unrecognized planning turn"
    private val process = UUID.randomUUID().toString()
    private val originalCommands = setOf("back to original", "back to the original", "show original", "return to original", "back to active plan")
    private data class Edit(val plan: PlanWorkspace, val message: String, val recognized: Boolean = true)

    suspend fun respond(context: Context, input: String, state: AssistantSession, now: Long = System.currentTimeMillis()): AyvaTalkEngine.TalkResponse? {
        val raw = PlanLanguage.command(input)
        // Outside Plan mode, time budgets retain the saved-task planning owner.
        if (ChatModeStore.get(context) != ChatMode.PLAN && AssistantPlanning.isDirectRequest(raw)) return null
        val command = raw.substringBefore(' ').lowercase(Locale.ROOT)
        val handoffText = if (command in setOf("/plan", "/talk", "/ask"))
            raw.substringAfter(' ', "") else raw
        val discussionRequest = if (command == "/plan" && handoffText.isBlank()) "/plan" else handoffText
        AssistantDiscussion.handoffProblem(state, discussionRequest)?.let {
            AssistantDiscussion.clearMeasurementQuestion(state)
            ChatModeStore.set(context, ChatMode.TALK)
            return reply(it)
        }
        val handoff = AssistantDiscussion.handoff(state, discussionRequest) ?: GeneralConversation.handoff(state, discussionRequest) ?: ConversationReasoning.handoff(state, discussionRequest)
        val nativeHandoff = if (handoff == null && state.topic == "tasks" && state.expected == null && AssistantContext.live(state.referenceAt) &&
            (AssistantDiscussion.requestsHandoff(handoffText) || command == "/plan" && handoffText.isBlank()))
            state.taskId ?: state.selectedTaskIds.singleOrNull() else null
        // Textual undo uses the same version-aware action gateway as the optional Undo chip.
        if (raw.lowercase(Locale.ROOT) in setOf("undo", "undo that", "undo last action", "undo plan")) return null
        if (command in setOf("/talk", "/ask", "/drill", "/blitz")) {
            ChatModeStore.set(context, if (command in setOf("/talk", "/ask")) ChatMode.TALK else ChatMode.DRILL)
            if (handoff == null && nativeHandoff == null) {
                if (AssistantDiscussion.requestsHandoff(handoffText) && state.expected == null) return missingDiscussionGoal()
                return null
            }
        }
        // Explicit native-task completion keeps its existing authoritative owner.
        if (Regex("(?i)^(?:complete task |mark task )").containsMatchIn(raw)) return null
        if (raw.startsWith("/") && command != "/plan" && handoff == null && nativeHandoff == null) return null
        if (Regex("(?i)^(?:let'?s talk about|can we talk about|change the subject to) ").containsMatchIn(raw)) {
            ChatModeStore.set(context, ChatMode.TALK)
            return null // Discussion is an explicit interruption; the durable plan stays intact.
        }
        val explicit = command == "/plan"
        if (AssistantDiscussion.requestsHandoff(handoffText) && handoff == null && nativeHandoff == null && !PlanLanguage.startsPlanning(raw) && state.expected == null)
            return missingDiscussionGoal()
        val explicitGoal = command == "/plan" && Regex("(?i)^(?:a |an |my |the )?(?:trip|holiday|vacation|exam|study|project|event|routine|errands)\\b").containsMatchIn(handoffText)
        val start = PlanLanguage.startsPlanning(raw) || explicitGoal || handoff != null || nativeHandoff != null
        val resume = Regex("(?i)^(?:back to (?:my |the )?(?:trip|exam|study|project|plan)(?: plan)?|resume plan .+)$").matches(raw)
        if (!explicit && !start && !resume && ChatModeStore.get(context) != ChatMode.PLAN) return null
        if (raw.equals("/plan discard all plan drafts", true)) {
            state.clearPending(); state.switchTopic("conversation")
            ConversationPlanStore.clearAll(context); ChatModeStore.set(context, ChatMode.CHAT)
            return reply("Discarded all plan workspaces. Saved tasks are unchanged.")
        }
        val store = ConversationPlanStore(context)
        var plan = store.active()
        if (resume && !explicit && plan == null && store.all().isEmpty()) return null // compatibility with the old short-lived planner
        val text = if (explicit || handoff != null) handoffText else raw
        val lower = text.lowercase(Locale.ROOT).trimEnd('.', '!', '?')
        val applyControl = Regex("^apply revision ([A-Za-z0-9-]{1,80}) (\\d+) ([A-Za-z0-9:-]{1,160})$").matchEntire(text)
        if (lower == "retry plan reminders") return reply("Retried saved plan reminder/widget updates. ${AssistantPlanStore.retryEffects(context)} receipt(s) still need a retry.")
        ChatModeStore.set(context, ChatMode.PLAN)
        state.clearPending(); state.switchTopic("plan_workspace")

        if (TripChecklistConversation.pause(lower) || lower in setOf("exit", "exit plan", "pause", "pause planning", "cancel planning", "leave plan mode")) {
            ChatModeStore.set(context, ChatMode.CHAT)
            return reply("Plan paused. Your draft is saved; use /plan to return.")
        }
        if (lower in setOf("discard plan", "discard this plan", "cancel this plan", "cancel the plan")) {
            plan?.let { store.remove(it.id) }
            ChatModeStore.set(context, ChatMode.CHAT)
            return reply("Discarded this plan workspace. Saved tasks were not deleted.")
        }
        if (lower in setOf("my plans", "list plans", "show plans")) {
            val plans = store.all()
            return reply(if (plans.isEmpty()) "No plan workspaces yet. What would you like to work toward?" else
                plans.mapIndexed { i, p -> "${i + 1}. ${p.goal.ifBlank { "Untitled plan" }}${if (p.archived) " — archived" else if (p.id == plan?.id) " — active" else ""}" }.joinToString("\n") + "\nSay ‘resume plan 2’ or use its title.")
        }
        if (lower.startsWith("resume plan ") || lower.startsWith("back to ") && lower !in originalCommands) {
            val ref = text.drop(if (lower.startsWith("resume plan ")) 12 else 8).replaceFirst(Regex("(?i)^(?:my|the) "), "").trim()
            val plans = store.all()
            val domainRef = ref.removeSuffix(" plan")
            val matches = if (ref.equals("plan", true)) listOfNotNull(plan) else ref.toIntOrNull()?.let { listOfNotNull(plans.getOrNull(it - 1)) }
                ?: plans.filter { it.goal.contains(ref, true) || it.domain.equals(domainRef, true) || domainRef.equals("exam", true) && it.domain == "study" }
            if (matches.size == 1) { plan = matches.single().copy(hypotheticalView = false, archived = false); store.put(plan); return render(plan, "Welcome back. Your draft and decisions are still here. I'll check saved tasks again before applying changes." + pendingPrompt(plan, now)) }
            return reply("Which plan? Say ‘list plans’ to see their names.")
        }
        if (nativeHandoff != null) {
            val base = PlanningLibrary(context).newPlan("Plan selected work", emptyMap(), "general")
            val adopted = ExistingTaskPlanning.adopt(context, base, "id:$nativeHandoff"); store.put(adopted)
            return render(adopted, "Started a separate draft for your selected saved task, preserving its related context. Existing records remain unchanged until review and apply.")
        }
        Regex("(?i)^(?:use|link|plan) (?:an? )?existing task (.+)$").matchEntire(text)?.let {
            val base = if (plan == null || text.startsWith("plan existing", true)) PlanningLibrary(context).newPlan("Plan saved work", emptyMap(), "general") else plan
            val adopted = ExistingTaskPlanning.adopt(context, if (base.trip != null) TripPlanningBridge.promote(base) else base, it.groupValues[1]); store.put(adopted)
            return render(adopted, "Linked the saved task and its parent/prerequisite context into this draft. Completed tasks and occasions stay read-only. Existing records are unchanged until review and apply.")
        }
        Regex("(?i)^save (?:this )?plan as template (.+)$").matchEntire(text)?.let {
            PlanningLibrary(context).saveTemplate(it.groupValues[1].trim(), plan ?: throw IllegalArgumentException("Start a plan first"))
            return reply("Saved a reusable template with the steps, hierarchy, dependencies and duration estimates. Dates, task links and old confirmations are not included.")
        }
        Regex("(?i)^(?:use|start from) template (.+)$").matchEntire(text)?.let {
            val next = PlanningLibrary(context).useTemplate(it.groupValues[1].trim()); store.put(next)
            return render(next, "Started a separate draft from the template. Give dates or availability when you're ready; nothing has been saved as tasks.")
        }
        if (lower == "remember planning preferences") {
            PlanningLibrary(context).rememberPreferences(plan ?: throw IllegalArgumentException("Set up a plan's preferences first"))
            return reply("Remembered this plan's scheduling preferences and any daily availability window for future plans. One-off dates and today's window were not remembered.")
        }
        if (plan == null && lower in setOf("hi", "hey", "hello")) return reply("Hi. What would you like to work toward? We can talk it through first.")
        if (plan != null && !start) {
            val migrated = TripPlanningBridge.migrate(plan, now)
            if (migrated != plan) { plan = migrated; store.put(plan) }
        }
        if (plan?.trip != null && TripPlanningBridge.advancedRequest(text)) {
            plan = TripPlanningBridge.promote(plan); store.put(plan)
            if (lower in setOf("advanced planning", "use advanced planning"))
                return render(plan, "Advanced planning is ready. Checklist items retain their identities as draft steps; saved tasks change only after review and apply.")
        }
        // The composer prefixes every Plan-mode reply. Give the active trip first
        // refusal on typed edits; /plan alone does not mean "replace this goal".
        if (plan?.trip != null && start && handoff == null && !lower.startsWith("new ")) {
            TripChecklistConversation.answer(plan, text, now)?.let { turn ->
                turn.plan.validate(); store.put(turn.plan); return reply(turn.text)
            }
        }
        if (handoff != null || plan == null || plan.goal.isBlank() && PlanLanguage.domain(text) == "trip" || start && nativeHandoff == null || lower.startsWith("new plan ") || lower.startsWith("new ")) {
            val goal = handoff?.goal ?: text.removePrefix("advanced ").replaceFirst(Regex("(?i)^new (?:plan )?"), "").takeUnless {
                it.lowercase(Locale.ROOT) in setOf("let's plan together", "lets plan together", "plan together", "help me plan")
            }.orEmpty()
            require(goal.length <= 500) { "Keep the planning goal under 500 characters; we can add details in later messages" }
            fun signature(value: String) = PlanningDialogue.goal(value).lowercase(Locale.ROOT).replaceFirst(Regex("^(?:a|an|my|the) "), "").trim()
            val matching = if (handoff == null && start) store.all().singleOrNull { signature(it.goal) == signature(goal) && signature(goal).isNotBlank() } else null
            if (matching != null) {
                store.put(matching.copy(archived = false, hypotheticalView = false, reviewedRevision = null, reviewedAt = 0, reviewProcess = null))
                return reply("We're already working on ${matching.goal}. Your details and steps are still here. What would you like to work through next?")
            }
            if (handoff == null && signature(goal).isBlank() && plan != null && plan.goal.isNotBlank())
                return reply("We can continue ${plan.goal}. What would you like to work through next?")
            val emptyGreeting = plan?.takeIf { it.steps.isEmpty() && it.facts.isEmpty() && it.goal.lowercase() in setOf("hi", "hey", "hello") }
            plan = PlanningLibrary(context).newPlan(goal, PlanLanguage.facts(goal) + handoff?.facts.orEmpty(), PlanLanguage.domain(goal))
            if (handoff == null) plan = plan.copy(goal = PlanningDialogue.goal(plan.goal), id = emptyGreeting?.id ?: plan.id)
            if (handoff != null && PlanLanguage.domain(goal) == "trip" || lower.startsWith("advanced ") || Regex("(?i)prepare for (?:an? )?exam trip").containsMatchIn(goal)) plan = plan.copy(facts = plan.facts + ("planning style" to "advanced"))
            val conversation = TripChecklistConversation.open(plan, text, now) ?: PlanningDialogue.opening(plan)
            plan = conversation.plan
            store.put(plan)
            return reply(if (handoff != null) "Carried your discussion into a new plan draft, including your details and any current choice. " + opening(plan)
                else conversation.text)
        }
        if (plan.trip != null && (text.isBlank() || TripChecklistConversation.summaryRequest(text)))
            return reply(TripChecklistConversation.summary(plan))
        val tripFinish = plan.trip != null && TripChecklistConversation.finish(text)
        if (plan.trip != null && !tripFinish) {
            TripChecklistConversation.answer(plan, text, now)?.let { turn ->
                turn.plan.validate(); store.put(turn.plan); return reply(turn.text)
            }
        }
        if (text.isBlank() || TripChecklistConversation.summaryRequest(text) || lower in originalCommands || lower in setOf("show plan", "show my plan", "review plan", "what did we decide", "what are we planning", "what's left") || plan.hypotheticalView && lower in setOf("cancel", "no", "not that")) {
            if (plan.hypotheticalView) { plan = plan.copy(hypotheticalView = false); store.put(plan) }
            return render(plan, (if (plan.goal.isBlank()) "What goal should this plan work toward?" else "Here's the active draft. No changes are applied to saved tasks until you review and apply them.") + pendingPrompt(plan, now))
        }
        PlanCollaboration.answer(context, plan, text, now)?.let { turn ->
            turn.plan.validate(); ConversationPlanStore(context).put(turn.plan)
            return reply(turn.text)
        }
        if (plan.hypotheticalView && !lower.startsWith("what if "))
            return reply("That was a hypothetical alternative. Say ‘show plan’ to return to your active draft, then describe the change you want to make. Nothing has been applied to saved tasks.")

        if (applyControl != null && (applyControl.groupValues[1] != plan.id || applyControl.groupValues[2].toIntOrNull() != plan.revision || applyControl.groupValues[3] != plan.reviewProcess))
            return reply("That review control belongs to an older plan or revision. Review the active plan again; nothing was saved.")
        if (tripFinish || applyControl != null || lower in setOf("save", "save plan", "save this plan", "save the agreed tasks", "okay save the agreed tasks", "save these tasks", "apply", "apply plan", "apply those changes", "yes save", "confirm plan")) {
            PlanningDialogue.saveProblem(plan)?.let { return reply(it) }
            if (plan.pending != null) return if (plan.trip != null) reply("There is an unanswered change. " + plan.pending.optString("question", "Please resolve it before saving.")) else render(plan, "There's an unanswered plan change. Answer it or say ‘cancel that change’ before reviewing a save; saved tasks are unchanged.")
            if (plan.savedRevision == plan.revision) return render(plan, "This revision is already saved. It won't create duplicate tasks.")
            if (plan.steps.none { it.workItem && !it.completed } && plan.removed.none { it.taskSnapshot != null }) return render(plan, "Add an active work step before applying. Context and facts are already retained in this workspace.")
            if (tripFinish) {
                plan = plan.copy(reviewedRevision = plan.revision, reviewedAt = now, reviewProcess = "$process:${UUID.randomUUID()}", reviewDeviceTimezone = java.util.TimeZone.getDefault().id)
                store.put(plan)
            }
            if (plan.reviewedRevision == plan.revision && plan.reviewProcess?.startsWith("$process:") == true && now - plan.reviewedAt in 0..900_000L && lower !in setOf("save", "save plan", "save this plan", "save the agreed tasks", "okay save the agreed tasks", "save these tasks")) {
                val result = ConversationPlanCommitter.commit(context, plan, now, process)
                state.undo = emptyList(); state.undoId = null
                if (result.before.size == result.after.size) {
                    state.undo = result.after.mapIndexed { i, task -> JSONObject().put("kind", "task").put("before", result.before[i]?.let(TaskJson::encode) ?: JSONObject.NULL).put("after", TaskJson.encode(task)) }
                    state.undoId = UUID.randomUUID().toString()
                }
                store.put(result.plan.copy(savedUndoId = state.undoId))
                state.focusTasks(result.after.filterNot { it.isTrashed }.map { it.id })
                val actions = state.undoId?.let { listOf(TalkAction.AskQuery("/ayva undo $it", "Undo")) }.orEmpty()
                if (result.plan.trip != null) return reply("Saved **${result.plan.goal}** with ${result.plan.trip.items.size} checklist item(s) and subtasks." +
                    (if (result.effectsPending) " Tasks are saved; reminder/widget refresh needs a retry. Say ‘retry plan reminders’." else ""), actions)
                return reply("Saved ${result.plan.steps.size} plan step(s) as tasks${if (plan.removed.isNotEmpty()) "; removed saved steps were moved to Trash" else ""}. Hierarchy and work-session times are saved with the tasks; due dates remain deadlines. Work-start reminders follow each task’s reminder setting.  " +
                    if (result.effectsPending) "Tasks are saved; reminder/widget refresh needs a retry. Say ‘retry plan reminders’." else "You can keep refining this plan here." + com.focusbyrj.app.util.WorkSessionReminders.availability(context)?.let { " $it" }.orEmpty(), actions)
            }
            val reviewed = plan.copy(reviewedRevision = plan.revision, reviewedAt = now, reviewProcess = "$process:${UUID.randomUUID()}", reviewDeviceTimezone = java.util.TimeZone.getDefault().id)
            store.put(reviewed)
            return render(reviewed, "Review this draft: ${plan.steps.count { it.taskSnapshot == null }} new task(s), ${plan.steps.count { it.taskSnapshot != null }} linked task(s), ${plan.removed.count { it.taskSnapshot != null }} task(s) to move to Trash. Say ‘apply plan’ to save this revision.",
                listOf(TalkAction.AskQuery("/plan apply revision ${reviewed.id} ${reviewed.revision} ${reviewed.reviewProcess}", "Apply plan")))
        }
        if (plan.trip != null && lower !in setOf("refresh saved tasks", "reconcile saved tasks", "what changed", "show changes", "what did you change") &&
            !lower.startsWith("what if "))
            return reply("I haven't changed the checklist. You can describe an item to include, ask what's on the list, or tell me which item or date to change.")
        if (lower in setOf("refresh saved tasks", "reconcile saved tasks")) {
            val app = context.applicationContext as com.focusbyrj.app.FocusApplication
            val byTaskId = plan.steps.mapNotNull { step -> step.taskSnapshot?.let { raw -> TaskJson.decode(JSONObject(raw)).id to step.id } }.toMap()
            val updated = plan.steps.map { step ->
                val prior = step.taskSnapshot?.let { TaskJson.decode(JSONObject(it)) } ?: return@map step
                val current = app.database.taskDao().getTaskById(prior.id)
                require(!step.taskLinkMissing && current != null && !current.isTrashed && current.deletedAt == null) { "‘${step.title}’ is unavailable. Remove it or say ‘unlink ${step.title}’ to keep it as a new draft task before saving." }
                val metadata = current.planning
                require(metadata == null || !metadata.parentMissing && metadata.missingPrerequisites == 0 &&
                    (metadata.prerequisiteTaskIds + listOfNotNull(metadata.parentTaskId)).all { it in byTaskId }) {
                    "‘${current.title}’ has a parent or prerequisite outside this draft. The draft was left unchanged. Say ‘unlink ${step.title}’ to make a separate draft copy, then rename and reschedule it before saving. The original task will stay unchanged."
                }
                step.copy(title = current.title, deadline = current.dueDate, checklist = com.focusbyrj.app.data.Subtask.listFromJson(current.subtasksJson), taskSnapshot = TaskJson.encode(current).toString(),
                    parentId = if (metadata == null) step.parentId else metadata.parentTaskId?.let(byTaskId::getValue),
                    prerequisites = if (metadata == null) step.prerequisites else metadata.prerequisiteTaskIds.map(byTaskId::getValue).toSet(),
                    minutes = if (metadata == null) step.minutes else metadata.minutes,
                    start = if (metadata == null) step.start else metadata.start,
                    sessions = if (metadata == null) step.sessions else metadata.sessions, workReminders = metadata?.workReminders ?: step.workReminders, afterMinutes = metadata?.afterMinutes ?: step.afterMinutes)
            }
            plan = TripPlanningBridge.reconcile(plan, updated); store.put(plan)
            return render(plan, "Refreshed linked titles, deadlines, hierarchy and work sessions from current saved tasks. Review again before applying.")
        }
        if (lower in setOf("what changed", "show changes", "what did you change")) return reply(PlanDiff.describe(plan))
        if (lower in setOf("what are you assuming", "what do you know", "show constraints")) return reply(
            "Goal: ${plan.goal}. Facts: ${plan.facts.entries.joinToString { "${it.key}: ${it.value}" }.ifBlank { "none supplied" }}.\n" +
            "Scheduling uses the availability and estimates you've supplied, plus any preferences you explicitly saved. Ranges reserve their upper estimate. " +
            "${plan.policy.bufferMinutes}-minute buffer; working days ${plan.policy.dayLabels}; session limit ${plan.policy.maximumSession ?: "not set"}; daily budget ${plan.policy.dailyMinutes ?: "not set"}. " +
            "Only work recorded in Ayva is checked; deadlines are not busy intervals. Saved changes always need a current review.")
        (if (PlanLanguage.clauses(text).size == 1) PlanningConversation.answer(plan, text, now) else null)?.let { turn ->
            turn.plan.validate()
            store.put(turn.plan.copy(reviewedRevision = null, reviewedAt = 0, reviewProcess = null))
            return reply(turn.text)
        }
        if (lower in setOf("suggest steps", "suggest a plan", "help me break it down", "what should i prepare")) {
            val titles = template(plan.domain)
            val question = if (plan.domain == "trip" && plan.facts["destination"] == null) "Do you have a destination in mind, or are we deciding that too?" else "Which of these fit your goal? Say ‘use suggested steps’, or tell me your own steps."
            return reply("Optional starting points:\n" + titles.mapIndexed { i, title -> "${i + 1}. $title" }.joinToString("\n") + "\n\n$question")
        }
        if (lower in setOf("use suggested steps", "use those steps", "add those steps")) {
            val titles = template(plan.domain).filter { title -> plan.steps.none { it.title.equals(title, true) } }
            val updated = plan.changed(steps = plan.steps + titles.map { PlanStep(title = it, source = "optional template", minutes = plan.facts["default task minutes"]?.toIntOrNull()) })
            store.put(updated); return render(updated, "Added these optional suggestions to the draft. Change or remove any that don't fit.")
        }
        if (lower in setOf("what is a buffer", "what's a buffer", "explain buffer")) return reply("A buffer is spare time between work sessions, so a small delay doesn't push everything else back. In this planner, set an explicit availability window and task durations before scheduling.")
        if (lower in setOf("cancel", "cancel that change", "never mind", "nevermind") || plan.pending != null && lower in setOf("no", "nope", "not that")) {
            val cancelled = plan.copy(pending = null, editFocus = null, reviewedRevision = null, reviewedAt = 0, reviewProcess = null)
            store.put(cancelled)
            return render(cancelled, "Cancelled the unanswered change and save review. Your draft steps and saved tasks are unchanged.")
        }
        if (plan.pending != null && lower in setOf("not sure", "i don't know", "dates aren't fixed", "skip", "leave it open")) {
            val open = plan.copy(pending = null, editFocus = null, reviewedRevision = null, reviewedAt = 0, reviewProcess = null)
            store.put(open)
            return render(open, "We can leave that detail open. I haven't applied the unanswered change; your previous draft values are retained. We can return to it whenever you're ready.")
        }
        if (lower in setOf("why", "why this order", "explain the plan", "why did you ask")) return explain(plan, lower in setOf("why", "why did you ask"))
        if (lower in setOf("hi", "hello", "hey")) return reply("Hi. We're working on ${plan.goal.ifBlank { "a new plan" }}. We can continue, or switch to Talk.")
        if (lower in setOf("thanks", "thank you", "okay", "ok", "yes", "no", "not sure", "i don't know", "dates aren't fixed", "skip"))
            return render(plan, "We can leave unknown details open. Tell me a step, ask for suggestions, or say what you'd like to change. Saved tasks haven't changed.")
        if (lower.startsWith("what if ")) {
            plan = plan.copy(hypotheticalView = true, reviewedRevision = null, reviewedAt = 0, reviewProcess = null)
            store.put(plan)
            val hypothetical = runCatching { edit(plan, text.drop(8), now, occupiedWork(context, plan, store)).also { it.plan.validate() } }.getOrElse { return reply("For that alternative: ${it.message}. Your active draft is unchanged.") }
            val explanation = hypothetical.plan.pending?.let { pending -> when (pending.optString("kind")) {
                "work_alternative" -> "That work would overlap another session. A later feasible slot would start ${PlanClock.format(pending.getLong("at"), plan.timezone)} for ${pending.getInt("minutes")} minutes."
                "duration" -> "That alternative needs a duration before its work interval can be checked."
                "date" -> "That alternative needs an unambiguous future day and time."
                "reference" -> "That alternative needs an unambiguous step reference."
                "time_kind" -> "That alternative needs a choice between work time and deadline."
                "availability" -> "That alternative needs a choice between today's and daily availability."
                else -> "That alternative has an unresolved detail."
            } } ?: hypothetical.message
            val branch = hypothetical.plan.copy(hypotheticalView = false, alternative = null).checkpoint()
            if (branch.toByteArray().size <= 200_000) store.put(plan.copy(alternative = branch))
            return render(hypothetical.plan, "Alternative only: $explanation Your active draft is unchanged. We can compare the alternative, use this alternative, or return to the active plan.")
        }

        // An attempted edit cancels earlier save authority even if that edit needs repair.
        plan = plan.copy(reviewedRevision = null, reviewedAt = 0, reviewProcess = null)
        store.put(plan)
        val occupied = occupiedWork(context, plan, store)
        val clauses = PlanLanguage.clauses(text)
        var candidate: PlanWorkspace = plan
        val messages = mutableListOf<String>()
        try {
            for (clause in clauses) {
                // Free-form answers cannot absorb an unknown operation in an atomic edit batch.
                val conversationalView = if (clauses.size > 1 && candidate.dialogue.question in setOf("outcome", "topics") && ConversationMeaning.kind(clause) == ConversationMeaning.Kind.STATEMENT)
                    candidate.copy(dialogue = candidate.dialogue.copy(question = "")) else candidate
                val conversation = PlanningConversation.answer(conversationalView, clause, now)
                val result = conversation?.let { Edit(it.plan, it.text) } ?: edit(candidate, clause, now, occupied)
                if (clauses.size > 1 && !result.recognized)
                    return reply("I couldn't understand ‘$clause’ as a plan change. No part of that compound request was applied. Send the changes separately so we can clarify it.")
                candidate = result.plan; messages.add(result.message)
                // Clarification belongs to the whole request; do not partly apply compound turns.
                if (candidate.pending != null && clauses.size > 1) return reply("Let's resolve one change first: ${result.message} No part of that compound request was applied. Send the clarified changes separately.")
            }
            candidate.validate()
            if (candidate.revision != plan.revision) candidate = candidate.copy(previousDraft = plan.checkpoint().takeIf { it.toByteArray().size <= 200_000 },
                undoDrafts = PlanWorkspace.boundedHistory(plan.undoDrafts + plan.checkpoint()), redoDrafts = emptyList())
            store.put(candidate)
            if (messages.size == 1 && messages.single() == UNRECOGNIZED) {
                val repaired = MilestonePlanning.repair(candidate, text)
                store.put(repaired.plan)
                return reply(repaired.text)
            }
            return reply(messages.joinToString(" "))
        } catch (error: IllegalArgumentException) {
            return render(plan, "${error.message ?: "That edit isn't valid"}. The draft is unchanged. Tell me which constraint or step to change.")
        }
    }

    private fun missingDiscussionGoal() = reply("What would you like to plan? Tell me the goal with /plan, or start a discussion with ‘let's talk through …’.")

    private fun edit(plan: PlanWorkspace, input: String, now: Long, occupied: List<PlanStep>): Edit {
        val normalized = PlanLanguage.normalize(input).trimEnd('.', '!', '?')
        val text = if (plan.dialogue.question == "availability_hours" && Regex("(?i)^(?:from )?\\d{1,2}(?::\\d{2})?\\s*(?:am|pm) (?:to|-) \\d{1,2}(?::\\d{2})?\\s*(?:am|pm)$").matches(normalized))
            "I'm free from ${normalized.removePrefix("from ")} today" else normalized
        val lower = text.lowercase(Locale.ROOT)
        if (Regex("(?i)^(?:don't|do not|not|leave|keep)\\b").containsMatchIn(text))
            return Edit(plan, "I left the draft unchanged. To protect a session, keep its explicit time; automatic scheduling preserves existing sessions.")

        // A clarification stores an operation and stable candidates, never executable display text.
        plan.pending?.let { pending ->
            if (pending.optString("kind") == "work_alternative") {
                val step = plan.steps.firstOrNull { it.id == pending.optString("id") }
                if (step != null) {
                    if (lower in setOf("use that time", "after that session", "after that", "that works"))
                        return applyOperation(plan.copy(pending = null), step, "schedule_exact",
                            JSONObject().put("at", pending.getLong("at")).put("minutes", pending.getInt("minutes")).toString(), now, occupied)
                    val answer = PlanLanguage.correctionValue(text) ?: text
                    val duration = PlanLanguage.duration(answer)
                    if (duration != null) return applyOperation(plan.copy(pending = null), step, "schedule_exact",
                        JSONObject().put("at", pending.getLong("requestedAt")).put("minutes", duration).toString(), now, occupied)
                    val expression = PlanTemporalLanguage.expression(answer, pending.getLong("requestedAt"), plan.timezone)
                    if (expression != null) {
                        val date = PlanLanguage.date(expression, now, plan.timezone, referenceTime = pending.optLong("referenceTime", now))
                        if (date.at != null) return applyOperation(plan.copy(pending = null), step, "schedule_exact",
                            JSONObject().put("at", date.at).put("minutes", pending.getInt("minutes")).toString(), now, occupied)
                        return Edit(plan.copy(pending = JSONObject().put("kind", "date").put("id", step.id).put("operation", "schedule")
                            .put("value", expression).put("referenceTime", pending.optLong("referenceTime", now)).put("minutes", pending.getInt("minutes"))), date.question!!)
                    }
                }
            }
            if (pending.optString("kind") == "time_kind" && lower in setOf("work time", "work session", "schedule", "deadline", "due date")) {
                val step = plan.steps.firstOrNull { it.id == pending.optString("id") }
                if (step != null) return applyOperation(plan.copy(pending = null), step,
                    if (lower in setOf("deadline", "due date")) "deadline" else "schedule", pending.getString("value"), now, occupied, pending.optLong("referenceTime", now))
            }
            if (pending.optString("kind") == "availability" && lower in setOf("today", "daily", "every day"))
                return arrange(plan, now, occupied, pending.getInt("from"), pending.getInt("until"), if (lower == "today") 1 else 28)
            if (pending.optString("kind") == "reference") {
                val candidates = pending.getJSONArray("ids").let { a -> (0 until a.length()).map { a.getString(it) } }
                val match = PlanLanguage.references(plan, text).filter { it.id in candidates }
                if (match.size == 1) {
                    return applyOperation(plan.copy(pending = null), match.single(), pending.getString("operation"), pending.getString("value"), now, occupied, pending.optLong("referenceTime", now))
                }
            }
            if (pending.optString("kind") == "duration") {
                PlanLanguage.duration(PlanLanguage.correctionValue(text) ?: text)?.let { minutes ->
                    val step = plan.steps.firstOrNull { it.id == pending.optString("id") }
                    if (step != null) {
                        val at = pending.getLong("at")
                        require(at > now) { "The requested work time has passed. Choose a new day and time" }
                        val result = applyOperation(plan.copy(pending = null), step, "schedule_exact",
                            JSONObject().put("at", at).put("minutes", minutes).toString(), now, occupied)
                        return if (result.plan.pending == null) result.copy(message = "Set the duration and retained your requested work time.") else result
                    }
                }
            }
            if (pending.optString("kind") == "date") {
                val original = pending.optString("value")
                val combined = if (lower in setOf("am", "pm")) "$original $text" else original.replace(Regex("(?i)\\s+at\\s+.*$"), "") + " at " + text
                val direct = PlanLanguage.date(text, now, plan.timezone, pending.optString("operation") == "deadline")
                val referenceTime = pending.optLong("referenceTime", now)
                val result = direct.takeIf { it.at != null }
                    ?: PlanLanguage.date(combined, now, plan.timezone, pending.optString("operation") == "deadline", referenceTime)
                if (result.at != null) {
                    val step = plan.steps.firstOrNull { it.id == pending.optString("id") }
                    if (step != null) {
                        val effective = if (direct.at != null) text else combined
                        if (pending.has("minutes")) return applyOperation(plan.copy(pending = null), step, "schedule_exact",
                            JSONObject().put("at", result.at).put("minutes", pending.getInt("minutes")).toString(), now, occupied)
                        return applyOperation(plan.copy(pending = null), step, pending.getString("operation"), effective, now, occupied, if (direct.at != null) now else referenceTime)
                    }
                }
            }
        }
        if (plan.goal.isBlank()) {
            require(text.length <= 500) { "Keep the goal under 500 characters; add the remaining details in another message" }
            val goal = plan.changed(goal = text, facts = plan.facts + PlanLanguage.facts(text)).copy(domain = PlanLanguage.domain(text))
            return Edit(goal, opening(goal))
        }

        AdvancedPlanLanguage.edit(plan, text, now, occupied)?.let { return Edit(it.plan, it.message) }
        when (val repair = PlanConversationRepairs.parse(plan, text)) {
            is PlanRepair.Change -> return target(plan, repair.reference, repair.operation.key, repair.value, now, occupied)
            is PlanRepair.TimeKind -> return target(plan, repair.reference, "choose_time_kind", repair.value, now, occupied)
            null -> Unit
        }

        if (lower in setOf("cancel pending removals", "keep all removed tasks"))
            return if (plan.removed.isEmpty()) Edit(plan, "There are no pending task removals.") else
                Edit(plan.changed(removed = emptyList()), "Cancelled the pending moves to Trash. Those tasks stay outside this draft and their saved state is unchanged.")
        Regex("(?i)^cancel (?:the )?removal (?:of|for) (.+)$").matchEntire(text)?.let { match ->
            val reference = match.groupValues[1].trim('"', '\'')
            val removed = plan.removed.filter { it.title.equals(reference, true) || it.id == reference }
            require(removed.size == 1) { "Name one removed step exactly, or say ‘cancel pending removals’ to keep all their saved tasks" }
            return Edit(plan.changed(removed = plan.removed.filterNot { it.id == removed.single().id }),
                "Cancelled moving ‘${removed.single().title}’ to Trash. Its saved state is unchanged and it stays outside this draft.")
        }

        Regex("(?i)^(?:name|goal)(?: is|:| to)? (.+)$").matchEntire(text)?.let { return Edit(plan.changed(goal = it.groupValues[1].take(500)), "Updated the goal.") }
        Regex("(?i)^unlink (.+)$").matchEntire(text)?.let { return target(plan, it.groupValues[1], "unlink", "", now, occupied) }
        Regex("(?i)^add subtask (.+?) (?:under|to) (.+)$").matchEntire(text)?.let { m ->
            val parent = PlanLanguage.references(plan, m.groupValues[2]); require(parent.size == 1) { "Which parent step should hold that subtask?" }
            val step = PlanStep(title = m.groupValues[1], parentId = parent.single().id)
            return Edit(plan.changed(steps = plan.steps + step, selected = step.id), "Added a subtask under ‘${parent.single().title}’.")
        }
        Regex("(?i)^(?:add|include|steps:|tasks:)\\s+(.+)$").matchEntire(text)?.let { m ->
            val pieces = ConversationLanguage.list(m.groupValues[1])
            val added = pieces.map { title ->
                val duration = Regex("(?i)\\s+for (.+)$").find(title)
                val minutes = duration?.let { PlanLanguage.duration(it.groupValues[1]) }
                require(duration == null || minutes != null || !Regex("(?i)(?:minutes?|mins?|hours?|hrs?)$").containsMatchIn(duration.groupValues[1])) {
                    "Use a whole duration between 1 minute and 24 hours for that step"
                }
                PlanStep(title = ConversationLanguage.unquote(if (minutes != null) title.substring(0, duration!!.range.first).trim() else title), minutes = minutes ?: plan.facts["default task minutes"]?.toIntOrNull())
            }
            require(added.map { it.title.lowercase(Locale.ROOT) }.distinct().size == added.size) { "That request repeats the same step title. Give each new step a distinct name" }
            require(added.none { next -> plan.steps.any { it.title.equals(next.title, true) } }) { "That step is already in this draft" }
            return Edit(plan.changed(steps = plan.steps + added, selected = added.last().id), "Added ${added.size} step(s) to the draft.")
        }
        Regex("(?i)^(?:rename|call) (.+?) (?:to|as) (.+)$").matchEntire(text)?.let { return target(plan, it.groupValues[1], "rename", it.groupValues[2], now, occupied) }
        Regex("(?i)^(?:remove|drop|delete) (.+)$").matchEntire(text)?.let { return target(plan, it.groupValues[1], "remove", "", now, occupied) }
        Regex("(?i)^(?:duration(?: for)?|time for) (.+?) (?:is|to|:) (.+)$").matchEntire(text)?.let { return target(plan, it.groupValues[1], "duration", it.groupValues[2], now, occupied) }
        Regex("(?i)^(?:deadline(?: for)?|due date for) (.+?) (?:is|to|:) (.+)$").matchEntire(text)?.let { return target(plan, it.groupValues[1], "deadline", it.groupValues[2], now, occupied) }
        Regex("(?i)^(?:move|schedule|put) (.+?) ((?:today|tomorrow|(?:next |this )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|\\d{4}-\\d{2}-\\d{2})\\b.*)$").matchEntire(text)?.let { match ->
            val ref = match.groupValues[1]
            // A date word inside a quoted title is literal, not a scheduling boundary.
            val explicitTime = Regex("(?i)^(?:move|schedule|put) (.+?) (?:to|on|at) (.+)$").matchEntire(text)
            val explicitTargetExists = explicitTime?.groupValues?.get(1)?.let { PlanLanguage.references(plan, it).isNotEmpty() } == true
            if (!explicitTargetExists && ref.count { it == '\"' || it == '“' || it == '”' } % 2 == 0)
                return target(plan, ref, "schedule", match.groupValues[2], now, occupied)
        }
        Regex("(?i)^(?:move|schedule|put) (.+?) (?:to|on|at) (.+)$").matchEntire(text)?.let {
            val ref = it.groupValues[1]; val value = it.groupValues[2]
            return target(plan, ref, "schedule", value, now, occupied)
        }
        Regex("(?i)^move (.+?) (before|after) (.+)$").matchEntire(text)?.let { m ->
            val a = PlanLanguage.references(plan, m.groupValues[1]); val b = PlanLanguage.references(plan, m.groupValues[3])
            require(a.size == 1 && b.size == 1 && a.single().id != b.single().id) { "Name two different, unambiguous steps" }
            val items = plan.steps.filterNot { it.id == a.single().id }.toMutableList()
            val at = items.indexOfFirst { it.id == b.single().id } + if (m.groupValues[2].equals("after", true)) 1 else 0
            items.add(at, a.single())
            return Edit(plan.changed(steps = items, selected = a.single().id), "Reordered the draft. Existing work times and dependencies are unchanged.")
        }
        Regex("(?i)^(.+?) (depends on|must follow|after) (.+)$").matchEntire(text)?.let { m ->
            val a = PlanLanguage.references(plan, m.groupValues[1]); val b = PlanLanguage.references(plan, m.groupValues[3])
            // “Only after work” is availability, not a pair of task references.
            if (m.groupValues[2].equals("after", true) && (a.size != 1 || b.size != 1)) return@let
            require(a.size == 1 && b.size == 1) { "Which two steps should depend on each other? Use their names or numbers" }
            val step = a.single().copy(prerequisites = a.single().prerequisites + b.single().id)
            return Edit(plan.changed(steps = plan.steps.map { if (it.id == step.id) step else it }, selected = step.id), "‘${step.title}’ now depends on ‘${b.single().title}’.")
        }
        Regex("(?i)^add subtask (.+?) (?:under|to) (.+)$").matchEntire(text)?.let { m ->
            val parent = PlanLanguage.references(plan, m.groupValues[2]); require(parent.size == 1) { "Which parent step should hold that subtask?" }
            val step = PlanStep(title = m.groupValues[1], parentId = parent.single().id)
            return Edit(plan.changed(steps = plan.steps + step, selected = step.id), "Added a subtask under ‘${parent.single().title}’.")
        }
        Regex("(?i)^(?:i(?:'m| am) free|availability|schedule everything|schedule the plan) (?:from )?(\\d{1,2})(am|pm)? (?:to|-) (\\d{1,2})(am|pm)?(?: (today|daily|every day))?$").matchEntire(text)?.let { m ->
            fun hour(n: String, suffix: String): Int { val h = n.toInt(); return if (suffix.isBlank()) h else h % 12 + if (suffix.equals("pm", true)) 12 else 0 }
            val from = hour(m.groupValues[1], m.groupValues[2]); val until = hour(m.groupValues[3], m.groupValues[4])
            require(m.groupValues[2].isNotBlank() || from == 0 || from > 12) { "Include AM or PM for the start of your availability" }
            require(m.groupValues[4].isNotBlank() || until > 12) { "Include AM or PM for the end of your availability" }
            require(from in 0..23 && until in 1..24 && from < until) { "Choose a same-day window with its end after its start" }
            val scope = m.groupValues[5].lowercase(Locale.ROOT)
            if (scope.isBlank()) return Edit(plan.copy(pending = JSONObject().put("kind", "availability").put("from", from).put("until", until)),
                "Is that availability for today, or every day? Reply ‘today’ or ‘daily’. I haven't scheduled anything yet.")
            return arrange(plan, now, occupied, from, until, if (scope == "today") 1 else 28)
        }
        val facts = PlanLanguage.facts(text)
        if (facts.isNotEmpty()) {
            val destination = if (plan.domain == "trip" && plan.facts["destination"] == null)
                Regex("^([\\p{L}][\\p{L} -]{1,45}),").find(text)?.groupValues?.get(1)?.let { mapOf("destination" to it) }.orEmpty() else emptyMap()
            return Edit(plan.changed(facts = plan.facts + facts + destination), "Noted ${(facts.keys + destination.keys).joinToString()}. " + if (plan.steps.isEmpty()) "What needs preparing? Ask for suggested steps if you'd like a starting point." else "Which step should we work through next?")
        }
        // A bare destination only answers the currently unresolved trip question, never changes a task.
        if (plan.domain == "trip" && plan.facts["destination"] == null && plan.steps.isEmpty() && text.matches(Regex("[\\p{L}][\\p{L} -]{1,45}")))
            return Edit(plan.changed(facts = plan.facts + ("destination" to text)), "Destination noted. Do you have dates in mind, or should we work out the preparation first?")
        return Edit(plan, UNRECOGNIZED, recognized = false)
    }

    private fun target(plan: PlanWorkspace, phrase: String, operation: String, value: String, now: Long, occupied: List<PlanStep>): Edit {
        val matches = PlanLanguage.references(plan, phrase)
        if (matches.size == 1) return applyOperation(plan, matches.single(), operation, value, now, occupied)
        val ids = if (matches.isEmpty()) plan.steps else matches
        return Edit(plan.copy(pending = JSONObject().put("kind", "reference").put("operation", operation).put("value", value).put("referenceTime", now).put("ids", JSONArray(ids.map { it.id }))),
            "Which step do you mean by ‘$phrase’? Use its name or number. No edit was applied.")
    }
    private fun arrange(plan: PlanWorkspace, now: Long, occupied: List<PlanStep>, from: Int, until: Int, days: Int): Edit {
        val result = PlanScheduler.arrange(plan, now, days = days, startHour = from, endHour = until, occupied = occupied)
        val scope = if (days == 1) "today" else "daily (next 28 days)"
        return Edit(plan.changed(steps = result.steps, facts = plan.facts + ("availability" to "$from:00–$until:00 $scope")),
            "Scheduled into your $scope window, preserving existing sessions. ${result.unscheduled.size} step(s) remain unscheduled (missing duration, prerequisites, or feasible time). These checks cover saved task sessions and Plan workspaces, not an external calendar." +
                result.reasons.entries.joinToString(prefix = if (result.reasons.isEmpty()) "" else "\n", separator = "\n") { (id, reason) -> "${plan.steps.first { it.id == id }.title}: $reason" })
    }
    private fun applyOperation(plan: PlanWorkspace, step: PlanStep, operation: String, value: String, now: Long, occupied: List<PlanStep>, referenceTime: Long = now): Edit {
        if ((!step.workItem || step.completed) && operation == "remove") {
            require(plan.steps.none { it.parentId == step.id || step.id in it.prerequisites }) { "Other steps still reference this context" }
            return Edit(plan.changed(steps = plan.steps.filterNot { it.id == step.id }, selected = null), "Removed the context from this draft. The saved record is unchanged.")
        }
        require(step.workItem && !step.completed || operation == "unlink") { "This completed task or occasion is read-only context. Use the native editor, or unlink a separate draft copy" }
        require(!step.protected || operation in setOf("rename", "clear_deadline", "unlink")) { "‘${step.title}’ is protected. Say ‘unlock ${step.title}’ before changing its work or removing it" }
        var changed = step
        when (operation) {
            "schedule_exact" -> {
                val exact = JSONObject(value)
                val at = exact.getLong("at")
                require(at > now) { "That offered work time has passed. Choose a future day and time" }
                changed = step.shifted(at).copy(minutes = exact.getInt("minutes"))
            }
            "choose_time_kind" -> return Edit(plan.copy(selected = step.id, pending = JSONObject().put("kind", "time_kind").put("id", step.id).put("value", value).put("referenceTime", referenceTime)),
                "For ‘${step.title}’, is that the work time or the deadline? Reply ‘work time’ or ‘deadline’.")
            "unschedule" -> changed = step.copy(start = null, sessions = emptyList())
            "clear_deadline" -> changed = step.copy(deadline = null)
            "shift" -> {
                val start = step.start ?: throw IllegalArgumentException("Choose a work day and time for ‘${step.title}’ before moving it earlier or later")
                val minutes = value.toIntOrNull()?.takeIf { it in -1440..1440 && it != 0 } ?: throw IllegalArgumentException("Use a shift between 1 minute and 24 hours")
                val shifted = start + minutes * 60_000L
                require(shifted > now) { "That would move the work time into the past. Choose a future time" }
                changed = step.shifted(shifted)
            }
            "unlink" -> {
                require(step.taskLinkMissing || step.taskSnapshot != null) { "This step is already a separate draft task" }
                return Edit(plan.changed(steps = plan.steps.map { if (it.id == step.id) it.copy(taskSnapshot = null, taskLinkMissing = false) else it }, selected = step.id),
                    "Unlinked ‘${step.title}’ from its saved task. The original task is unchanged. This is now a draft copy; give it a distinct name and work time before saving.")
            }
            "rename" -> changed = step.copy(title = value)
            "remove" -> {
                require(plan.steps.none { step.id in it.prerequisites || it.parentId == step.id }) { "Other steps depend on or belong to ‘${step.title}’. Reassign or remove those first" }
                return Edit(plan.changed(steps = plan.steps.filterNot { it.id == step.id }, selected = null,
                    removed = if (step.taskSnapshot != null && !step.taskLinkMissing) plan.removed + step else plan.removed), "Removed ‘${step.title}’ from the draft. Linked saved tasks change only after review and apply.")
            }
            "duration" -> changed = step.copy(minutes = PlanLanguage.duration(value) ?: throw IllegalArgumentException("How many minutes or hours should that take?"))
            "schedule", "deadline" -> {
                val date = PlanLanguage.date(value, now, plan.timezone, operation == "deadline", referenceTime)
                if (date.at == null) return Edit(plan.copy(selected = step.id, editFocus = PlanEditFocus(step.id, if (operation == "deadline") PlanEditField.DEADLINE else PlanEditField.WORK_TIME),
                    pending = JSONObject().put("kind", "date").put("id", step.id).put("operation", operation).put("value", value).put("referenceTime", referenceTime)), date.question!!)
                if (operation == "deadline") changed = step.copy(deadline = date.at)
                else {
                    if (step.minutes == null) return Edit(plan.copy(selected = step.id, editFocus = PlanEditFocus(step.id, PlanEditField.WORK_TIME), pending = JSONObject().put("kind", "duration").put("id", step.id).put("at", date.at)), "How long should ‘${step.title}’ take? A work session needs a duration; your requested time is retained.")
                    changed = step.shifted(date.at)
                }
            }
            else -> throw IllegalArgumentException("That planning operation is unavailable. Describe the change again")
        }
        if (operation in setOf("duration", "shift", "schedule", "schedule_exact") && changed.start != null) {
            val conflict = (plan.steps.filterNot { it.id == step.id } + occupied).firstOrNull { PlanWorkspace.conflicts(changed, it, plan.policy.bufferMinutes) }
            if (conflict != null) {
                val alternative = PlanWorkAlternatives.laterOnSameDay(plan, changed, occupied, now)
                if (alternative != null) return Edit(plan.copy(selected = step.id, editFocus = PlanEditFocus(step.id, PlanEditField.WORK_TIME), pending = JSONObject().put("kind", "work_alternative")
                    .put("id", step.id).put("at", alternative.start).put("minutes", alternative.minutes).put("requestedAt", changed.start).put("referenceTime", now)),
                    "That overlaps ‘${conflict.title}’. ‘${step.title}’ could start ${SmartDateParser.formatDueDate(alternative.start!!)} for ${alternative.minutes} minutes without moving other sessions. Say ‘use that time’, choose another time, or cancel that change. I haven't moved it yet.")
                throw IllegalArgumentException("That overlaps ‘${conflict.title}’, and I couldn't fit a later slot on that day within the current constraints. Choose another time or change the duration or deadline")
            }
        }
        val field = when (operation) {
            "duration" -> PlanEditField.DURATION
            "schedule", "shift", "schedule_exact" -> PlanEditField.WORK_TIME
            "deadline" -> PlanEditField.DEADLINE
            "rename" -> PlanEditField.NAME
            else -> null
        }
        val updated = plan.changed(steps = plan.steps.map { if (it.id == step.id) changed else it }, selected = step.id,
            editFocus = field?.let { PlanEditFocus(step.id, it) })
        updated.validate()
        return Edit(updated, "Updated ‘${changed.title}’ in the draft.")
    }
    private fun opening(plan: PlanWorkspace): String = when (plan.domain) {
        "trip" -> if (plan.facts["destination"] == null) "Let's work it out. Do you have a destination in mind, or are we deciding that too?" else "Let's work through the preparation for ${plan.facts["destination"]}. Do you have dates in mind, or should we leave them open?"
        "study" -> "What subjects or topics are you working toward, and is there an exam date? You can leave uncertain details open."
        else -> "What outcome would make this plan successful? Tell me what you already know; we can break it into steps together."
    }
    private fun pendingPrompt(plan: PlanWorkspace, now: Long): String {
        val pending = plan.pending ?: return ""
        val step = plan.steps.firstOrNull { it.id == pending.optString("id") }?.title ?: "that step"
        return "\n\nUnanswered change: " + when (pending.optString("kind")) {
            "work_alternative" -> if (pending.getLong("at") > now)
                "‘$step’ could start ${PlanClock.format(pending.getLong("at"), plan.timezone)} for ${pending.getInt("minutes")} minutes. Say ‘use that time’, choose another time, or cancel that change."
                else "The offered time for ‘$step’ has passed. Choose a future time or cancel that change."
            "duration" -> "How many minutes or hours should ‘$step’ take?"
            "date" -> "Which future day and time did you mean for ‘$step’? Include AM or PM."
            "reference" -> "Which step did you mean? Use its name or number."
            "time_kind" -> "For ‘$step’, did you mean work time or deadline?"
            "availability" -> "Is the availability for today or daily?"
            "decompose" -> "Review the suggested child steps, then say ‘use that breakdown’ or cancel that change."
            else -> "Answer the pending planning detail or cancel that change."
        }
    }
    private fun template(domain: String) = when (domain) {
        "trip" -> listOf("Choose travel dates", "Research transport", "Arrange accommodation", "Prepare packing list")
        "study" -> listOf("List topics to cover", "Review difficult topics", "Practice questions", "Review mistakes")
        "project" -> listOf("Define the outcome", "Identify prerequisites", "Build a first version", "Check the result")
        "event" -> listOf("Decide the event outline", "Confirm guests and availability", "Arrange essentials", "Check final arrangements")
        "errands" -> listOf("List the errands", "Check locations and opening hours", "Group nearby stops", "Review what remains")
        "routine" -> listOf("Choose a realistic starting cue", "List the essential actions", "Try a small version", "Review what works")
        else -> listOf("Define the outcome", "Gather what is needed", "Complete the main work", "Review the result")
    }
    private fun explain(plan: PlanWorkspace, question: Boolean = false): AyvaTalkEngine.TalkResponse {
        if (question && plan.pending != null) {
            val reason = when (plan.pending.optString("kind")) {
                "reference" -> "More than one step could fit that reference, so I need its name or number before changing the draft."
                "time_kind" -> "A work time reserves time to do a step; a deadline is when it must be finished. I need to know which you meant."
                "date" -> "I need an unambiguous future day and time. I keep the originally requested day while we clarify its clock time."
                "duration" -> "A work session needs a duration so I can check its end, overlaps, and dependencies. The requested start time is retained."
                "availability" -> "A one-day window and a daily window would produce different schedules, so I haven't assumed which one you meant."
                "work_alternative" -> "The requested work overlaps another session. I kept both steps in place and offered a later slot that fits the current plan; I'll check it again if you choose it."
                else -> "I need to resolve the pending planning detail before changing saved tasks."
            }
            return reply(reason)
        }
        val reasons = plan.steps.filter { it.prerequisites.isNotEmpty() }.map { step ->
            "‘${step.title}’ follows ${step.prerequisites.joinToString { id -> "‘${plan.steps.first { it.id == id }.title}’" }} because you set that dependency."
        }
        return reply((reasons.ifEmpty { listOf("The draft currently follows your chosen order; I haven't inferred dependencies or deadlines.") } +
            "Work sessions use your explicit durations and availability. A due date is a deadline, not an occupied calendar interval.").joinToString("\n"))
    }
    private fun render(plan: PlanWorkspace, message: String, actions: List<TalkAction> = emptyList()): AyvaTalkEngine.TalkResponse = reply(buildString {
        append(message)
        if (plan.goal.isNotBlank()) append("\n\n**${plan.goal}**")
        if (plan.facts.isNotEmpty()) append("\n" + plan.facts.entries.joinToString(" · ") { "${it.key}: ${it.value}" })
        if (plan.milestones.isNotEmpty()) append("\nMilestones: " + plan.milestones.joinToString { it.title })
        plan.milestones.firstOrNull { it.id == plan.dialogue.focusMilestone }?.let { append("\nWorking on: ${it.title}") }
        val visibleSteps = if (actions.isNotEmpty()) plan.steps else plan.steps.take(15)
        visibleSteps.forEachIndexed { index, step ->
            append("\n${index + 1}. ${step.title}")
            plan.milestones.filter { step.id in it.stepIds }.takeIf { it.isNotEmpty() }?.let { append(" · " + it.joinToString { group -> group.title }) }
            if (step.taskLinkMissing) append(" · saved task link missing; remove or unlink this step")
            step.parentId?.let { parent -> append(" — under ${plan.steps.first { it.id == parent }.title}") }
            step.minutes?.let { append(" · $it${step.maximumEstimate?.let { maximum -> "–$maximum" }.orEmpty()} min estimated") }
            if (step.completed) append(" · completed context") else if (!step.workItem) append(" · occasion context")
            if (!step.completed && step.workState != PlanWorkState.READY) append(" · ${step.workState.name.lowercase()}${step.blockReason?.let { ": $it" }.orEmpty()}")
            if (step.protected) append(" · protected")
            if (step.start != null && step.workItem && !step.completed) append(" · work reminders ${if (step.workReminders) "on" else "off"}")
            if (step.workSessions.size > 1) {
                append(" · ${step.workSessions.size} sessions")
                step.workSessions.forEachIndexed { n, session -> append("\n   ${n + 1}) ${PlanClock.format(session.start, plan.timezone)} · ${session.minutes} min") }
            }
            if (step.workSessions.size <= 1) step.start?.let { append(" · work ${PlanClock.format(it, plan.timezone)}") }
            step.deadline?.let { append(" · due ${PlanClock.format(it, plan.timezone)}") }
            step.checklist.forEach { append("\n   ${if (it.isDone) "✓" else "•"} ${it.title}") }
        }
        if (plan.removed.isNotEmpty() && actions.isNotEmpty()) append("\nMove to Trash: " + plan.removed.filter { it.taskSnapshot != null }.joinToString { it.title })
        if (plan.steps.size > visibleSteps.size) append("\n…and ${plan.steps.size - visibleSteps.size} more. Use a step's name to select it; saving shows the complete review.")
    }, actions)
    private fun reply(text: String, actions: List<TalkAction> = emptyList()) = AyvaTalkEngine.TalkResponse(text, actions = actions, topicId = "plan_workspace", jsonPayload = AyvaTalkEngine.serializeActionsJson("plan_workspace", actions))
}
