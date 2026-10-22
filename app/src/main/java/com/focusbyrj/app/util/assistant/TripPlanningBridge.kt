package com.focusbyrj.app.util.assistant

import java.time.Instant
import java.time.ZoneId

/** Lossless ownership changes: rich graphs stay graphs; simple unsaved lists can become checklists. */
internal object TripPlanningBridge {
    fun advancedRequest(text: String): Boolean = Regex(
        "(?i)^(?:advanced planning|use advanced planning|suggest milestones|use (?:those|suggested) milestones|focus on |add subtask |split |merge plan |unlink |for (?:travel|study|exam),|schedule work|auto.?schedule|set availability|availability |give .+ (?:minutes?|hours?|half an hour)|.* (?:depends on|must follow) |(?:add|include) .+ for .*(?:minutes?|hours?))"
    ).containsMatchIn(text)

    fun migrate(plan: PlanWorkspace, now: Long): PlanWorkspace {
        if (plan.domain != "trip" || plan.trip != null || plan.facts["planning style"] == "advanced") return plan
        // A migration cannot collapse linked tasks, scheduling, hierarchy, pending edits or history.
        if (plan.pending != null || plan.milestones.isNotEmpty() || plan.removed.isNotEmpty() ||
            plan.steps.any { it.taskSnapshot != null || it.parentId != null || it.prerequisites.isNotEmpty() ||
                it.minutes != null || it.start != null || it.sessions.isNotEmpty() || it.checklist.isNotEmpty() ||
                it.protected || it.earliestStart != null || it.workState != PlanWorkState.READY })
            return plan.changed(facts = plan.facts + ("planning style" to "advanced"), pending = plan.pending)
        val opened = TripChecklistConversation.open(plan, plan.goal, now)?.plan ?: return plan
        val travel = plan.facts["travel date"]?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        val root = opened.steps.single().copy(title = plan.goal,
            deadline = travel?.atTime(23, 59)?.atZone(ZoneId.of(plan.timezone))?.toInstant()?.toEpochMilli())
        val items = plan.steps.map { step -> TripChecklistItem(id = step.id, title = step.title,
            date = step.deadline?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(plan.timezone)).toLocalDate().toString() }) }
        val dated = plan.steps.filter { it.deadline != null }.map { it.copy(parentId = root.id) }
        val checks = items.filter { it.date == null }.map { com.focusbyrj.app.data.Subtask(it.id, it.title, false) }
        return plan.changed(steps = listOf(root.copy(checklist = checks)) + dated, facts = opened.facts + plan.facts,
            selected = null).copy(trip = TripChecklistState(root.id, items, plan.selected?.takeIf { id -> items.any { it.id == id } }), dialogue = opened.dialogue)
    }

    fun promote(plan: PlanWorkspace): PlanWorkspace {
        val trip = plan.trip ?: return plan
        require(plan.pending == null) { "Answer or cancel the pending checklist change before advanced planning." }
        val root = plan.steps.single { it.id == trip.rootId }
        val retainRoot = root.taskSnapshot != null || root.taskLinkMissing || root.checklist.any { it.isDone }
        val checks = root.checklist.filterNot { it.isDone }.map { item -> PlanStep(id = item.id, title = item.title,
            parentId = root.id.takeIf { retainRoot }, workReminders = false) }
        val existing = plan.steps.filter { it.id != root.id }.map { it.copy(parentId = it.parentId.takeUnless { id -> id == root.id && !retainRoot }) }
        val byId = (existing + checks).associateBy { it.id }
        val ordered = trip.items.mapNotNull { byId[it.id] }
        return plan.changed(steps = (if (retainRoot) listOf(root.copy(checklist = root.checklist.filter { it.isDone })) else emptyList()) + ordered,
            facts = plan.facts + ("planning style" to "advanced"), selected = null).copy(trip = null,
            dialogue = plan.dialogue.copy(question = ""))
    }

    /** Rebuild semantic state from an explicitly refreshed native projection, preserving checklist completion. */
    fun reconcile(plan: PlanWorkspace, steps: List<PlanStep>): PlanWorkspace {
        val trip = plan.trip ?: return plan.changed(steps = steps)
        val root = steps.single { it.id == trip.rootId }
        val old = trip.items.associateBy { it.id }
        val checks = root.checklist.map { sub -> (old[sub.id] ?: TripChecklistItem(id = sub.id, title = sub.title))
            .copy(title = sub.title, date = null, offset = null) }
        val travel = root.deadline?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(plan.timezone)).toLocalDate() }
        val dated = steps.filter { it.id != root.id }.map { step ->
            val date = step.deadline?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(plan.timezone)).toLocalDate().toString() }
            require(date != null && step.parentId == root.id) { "A linked trip item lost its date or parent. Use advanced planning to reconcile its new structure." }
            val prior = old[step.id]
            val offset = prior?.offset?.takeIf { travel?.plusDays(it.toLong())?.toString() == date }
            (prior ?: TripChecklistItem(id = step.id, title = step.title)).copy(title = step.title, date = date.takeIf { offset == null }, offset = offset)
        }
        val facts = plan.facts.toMutableMap().apply { put("main task title", root.title) }
        root.deadline?.let { facts["travel date"] = Instant.ofEpochMilli(it).atZone(ZoneId.of(plan.timezone)).toLocalDate().toString() }
            ?: facts.remove("travel date")
        val items = checks + dated
        return plan.changed(steps = steps, facts = facts, goal = root.title, pending = plan.pending).copy(trip = trip.copy(items = items,
            selected = trip.selected?.takeIf { id -> items.any { it.id == id } }))
    }
}
