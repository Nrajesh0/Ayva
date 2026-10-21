package com.focusbyrj.app.util.assistant

import android.content.Context
import org.json.JSONObject
import java.util.UUID

/** Operations affect durable drafts only. Native edits still require the normal review gateway. */
internal object PlanCollaboration {
    suspend fun answer(context: Context, plan: PlanWorkspace, text: String, now: Long): PlanningDialogue.Turn? {
        val lower = text.lowercase().trimEnd('.', '!', '?')
        if (plan.hypotheticalView && lower !in setOf("use this alternative", "use that alternative", "accept alternative", "merge alternative", "discard alternative", "forget alternative", "compare alternative", "what changed in the alternative")) return null
        if (plan.pending != null) return null
        PlanProgress.answer(plan, text)?.let { return it }
        val store = ConversationPlanStore(context)
        if (lower in setOf("archive plan", "archive this plan")) return PlanningDialogue.Turn(
            plan.copy(archived = true, reviewedRevision = null, reviewedAt = 0, reviewProcess = null, alternative = null, hypotheticalView = false),
            "Archived ‘${plan.goal}’. The draft is retained and its unsaved times no longer reserve space. Saved tasks keep their own sessions. You can resume the plan by name.")
        Regex("(?i)^(?:we need to |i need to |let'?s )?(?:finish everything|finish this|finish the plan|have this done) by (.+)$").matchEntire(text)?.let { m ->
            val date = PlanLanguage.date(m.groupValues[1], now, plan.timezone, deadline = true)
            if (date.at == null) return PlanningDialogue.Turn(plan, date.question ?: "Which future deadline did you mean?")
            require(plan.steps.none { it.protected && it.workItem && !it.completed && (it.deadline == null || it.deadline > date.at) }) { "A protected commitment would need a tighter deadline. Resolve it first." }
            val next = plan.changed(steps = plan.steps.map { if (it.workItem && !it.completed) it.copy(deadline = minOf(it.deadline ?: date.at, date.at)) else it },
                facts = plan.facts + ("goal deadline" to PlanClock.format(date.at, plan.timezone)), goalDeadline = date.at)
            next.validate()
            return PlanningDialogue.Turn(next, "Set the draft's unfinished work to be done by ${PlanClock.format(date.at, plan.timezone)}. Existing schedules still need to fit that deadline; saved tasks haven't changed.")
        }
        Regex("(?i)^(?:there(?:'s| is) |i have )?(.+?) (?:left|remaining) (?:on|for) (.+)$").matchEntire(text)?.let { m ->
            val minutes = PlanLanguage.duration(m.groupValues[1]) ?: return@let
            val matches = PlanLanguage.references(plan, m.groupValues[2]); require(matches.size == 1) { "Which step has that much work remaining?" }
            val step = matches.single(); require(!step.protected && step.workItem && !step.completed) { "That step is protected or read-only. We can discuss it without changing its work." }
            val changed = step.copy(minutes = minutes, maximumEstimate = null, start = null, sessions = emptyList())
            val next = plan.changed(steps = plan.steps.map { if (it.id == step.id) changed else it }, selected = step.id)
            return PlanningDialogue.Turn(next, "Updated ‘${step.title}’ to $minutes minutes of remaining work in the draft. Its previous proposed sessions are open for replanning; saved work is unchanged until review.")
        }
        if ((Regex("(?i)\\b(?:missed|didn't finish|couldn't finish)\\b").containsMatchIn(text) || Regex("(?i)^(?:i |we )?(?:only )?have .+ today$").matches(text)) && lower.contains("today")) {
            val amount = Regex("(?i)(half an hour|\\d+ minutes?|\\d+ hours?)").find(text)?.value
            val minutes = amount?.let(PlanLanguage::duration)
            if (minutes != null) {
                val day = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(plan.timezone)).apply { timeInMillis = now }
                val date = "%04d-%02d-%02d".format(java.util.Locale.ROOT, day.get(java.util.Calendar.YEAR), day.get(java.util.Calendar.MONTH) + 1, day.get(java.util.Calendar.DAY_OF_MONTH))
                val steps = plan.steps.map { step ->
                    if (!step.protected && !step.completed && step.workSessions.isNotEmpty() && step.workSessions.all { it.end <= now }) step.copy(start = null, sessions = emptyList()) else step
                }
                val base = plan.changed(steps = steps, facts = plan.facts + ("today capacity" to "$minutes minutes on $date"))
                    .copy(policy = plan.policy.copy(dateBudgets = plan.policy.dateBudgets + (date to minutes)))
                val window = plan.facts["availability"]?.let { Regex("^(\\d+):00–(\\d+):00 (today|daily.*)$").matchEntire(it) }
                if (window == null) return PlanningDialogue.Turn(base.copy(dialogue = base.dialogue.copy(question = "availability_hours")), "Let's limit today's work to $minutes minutes. I kept protected and future sessions. Which hours today are available?")
                val occupied = occupiedWork(context, base, store)
                val arranged = PlanScheduler.arrange(base, now, 1, window.groupValues[1].toInt(), window.groupValues[2].toInt(), occupied)
                return PlanningDialogue.Turn(base.copy(steps = arranged.steps), "Reworked today's draft within $minutes minutes, keeping protected and future work. ${arranged.unscheduled.size} step(s) still need time; I haven't squeezed them into the day or changed saved tasks.")
            }
        }
        if (lower in setOf("use this alternative", "use that alternative", "accept alternative", "merge alternative")) {
            val alternative = plan.alternative ?: return PlanningDialogue.Turn(plan, "There isn't a saved alternative to accept yet.")
            val candidate = PlanWorkspace.read(JSONObject(alternative))
            require(candidate.id == plan.id && candidate.revision >= plan.revision) { "That alternative no longer matches the active plan. Let's compare it again." }
            // Revalidate against canonical occupied work when it is scheduled or saved, never replay authority.
            val next = plan.changed(steps = candidate.steps, facts = candidate.facts, removed = candidate.removed, selected = candidate.selected, goal = candidate.goal, milestones = candidate.milestones, goalDeadline = candidate.goalDeadline)
                .copy(policy = candidate.policy, timezone = candidate.timezone, alternative = null, dialogue = candidate.dialogue, hypotheticalView = false)
            next.validate()
            return PlanningDialogue.Turn(next, "Made the alternative our active draft. Saved tasks have not changed; we'll check conflicts again before saving.")
        }
        if (lower in setOf("discard alternative", "forget alternative")) return PlanningDialogue.Turn(plan.copy(alternative = null, hypotheticalView = false), "Left the active draft as it was and discarded the alternative.")
        if (lower in setOf("compare alternative", "what changed in the alternative")) {
            val other = plan.alternative?.let { PlanWorkspace.read(JSONObject(it)) } ?: return PlanningDialogue.Turn(plan, "There isn't an alternative to compare yet.")
            val before = plan.steps.associateBy { it.id }; val after = other.steps.associateBy { it.id }
            val descriptions = mutableListOf<String>()
            after.values.filter { before[it.id] == null }.forEach { descriptions += "Added: ${it.title}" }
            before.values.filter { after[it.id] == null }.forEach { descriptions += "Removed: ${it.title}" }
            after.values.filter { before[it.id] != null && before[it.id] != it }.forEach { descriptions += "Changed: ${it.title}" }
            (plan.facts.keys + other.facts.keys).distinct().filter { plan.facts[it] != other.facts[it] }.forEach { descriptions += "$it: ${other.facts[it] ?: "left open"}" }
            return PlanningDialogue.Turn(plan, descriptions.joinToString("\n").ifBlank { "There are no concrete draft differences yet." })
        }
        Regex("(?i)^rename (?:this |the )?plan to (.+)$").matchEntire(text)?.let { m ->
            require(m.groupValues[1].length in 1..500)
            return PlanningDialogue.Turn(plan.changed(goal = m.groupValues[1]), "Renamed our plan to ‘${m.groupValues[1]}’.")
        }
        Regex("(?i)^split (.+?) into (?:a )?(?:new |separate )?plan(?: called (.+))?$").matchEntire(text)?.let { m ->
            val chosen = PlanLanguage.references(plan, m.groupValues[1]); require(chosen.size == 1) { "Which step should start the separate plan?" }
            val ids = mutableSetOf(chosen.single().id)
            repeat(plan.steps.size) { plan.steps.filter { it.parentId in ids }.forEach { ids += it.id } }
            val selected = plan.steps.filter { it.id in ids }
            require(selected.all { it.taskSnapshot == null }) { "Linked saved tasks need their canonical hierarchy preserved. Unlink a separate draft copy before splitting." }
            require(plan.steps.none { it.id !in ids && it.prerequisites.any(ids::contains) } && selected.none { it.prerequisites.any { id -> id !in ids } }) { "There are dependencies crossing that split. Resolve them first so neither plan loses its prerequisites." }
            val remaining = plan.changed(steps = plan.steps.filterNot { it.id in ids }, selected = null).copy(previousDraft = null, undoDrafts = emptyList(), redoDrafts = emptyList())
            val next = PlanWorkspace(goal = m.groupValues[2].ifBlank { chosen.single().title }, domain = plan.domain, timezone = plan.timezone,
                steps = selected.map { it.copy(parentId = it.parentId?.takeIf(ids::contains)) }, facts = plan.facts, policy = plan.policy, goalDeadline = plan.goalDeadline,
                milestones = plan.milestones.filter { it.stepIds.any(ids::contains) }.map { it.copy(stepIds = it.stepIds.intersect(ids)) },
                dialogue = PlanningDialogueState(reasoning = PlanningReasoning.subset(plan, ids)))
            next.validate(); remaining.validate()
            // One encrypted store write publishes both drafts; no partial split on storage failure.
            store.putTogether(remaining, next)
            return PlanningDialogue.Turn(remaining, "Moved that draft work into ‘${next.goal}’. Both plans are retained; nothing was saved as native tasks.")
        }
        Regex("(?i)^merge (?:this plan with |plan )(.+)$").matchEntire(text)?.let { m ->
            val matches = store.all().filter { it.id != plan.id && it.goal.equals(m.groupValues[1], true) }
            require(matches.size == 1) { "Which other plan should we merge? Use its exact title." }
            val other = matches.single()
            require(!plan.hypotheticalView && !other.hypotheticalView && plan.pending == null && other.pending == null) { "Finish or cancel the open changes before merging." }
            require(plan.timezone == other.timezone && plan.policy == other.policy && plan.goalDeadline == other.goalDeadline) { "These plans have different scheduling rules. Align those before merging." }
            require(other.facts.none { (key, value) -> plan.facts[key]?.let { it != value } == true }) { "The plans have conflicting facts. Resolve those before merging." }
            val linkedIds = plan.steps.mapNotNull { it.taskSnapshot?.let { raw -> TaskJson.decode(JSONObject(raw)).id } }.toSet()
            require(other.steps.none { step -> step.taskSnapshot?.let { TaskJson.decode(JSONObject(it)).id in linkedIds } == true }) { "Both plans refer to the same saved task. Reconcile that shared work before merging." }
            require(other.steps.none { step -> plan.steps.any { it.title.equals(step.title, true) } }) { "The plans have duplicate step names. Reconcile or rename them before merging." }
            val idMap = other.steps.associate { it.id to UUID.randomUUID().toString() }
            val additions = other.steps.map { it.copy(id = idMap.getValue(it.id), parentId = it.parentId?.let(idMap::getValue), prerequisites = it.prerequisites.map(idMap::getValue).toSet()) }
            val mergedMilestones = PlanMilestone.merged(plan.milestones, other.milestones, idMap)
            val next = plan.changed(steps = plan.steps + additions, facts = plan.facts + other.facts, milestones = mergedMilestones).copy(previousDraft = null, undoDrafts = emptyList(), redoDrafts = emptyList())
            val combined = next.copy(dialogue = next.dialogue.copy(reasoning = PlanningReasoning.merged(plan, other, mergedMilestones), notes = (plan.dialogue.notes + other.dialogue.notes).distinct().takeLast(20)))
            combined.validate(); store.replaceAndRemove(combined, other.id)
            return PlanningDialogue.Turn(combined, "Combined the draft work in ‘${plan.goal}’. We preserved dependencies and will recheck saved work before applying.")
        }
        return null
    }
}
