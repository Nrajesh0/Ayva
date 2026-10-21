package com.focusbyrj.app.util.assistant

import org.json.JSONObject

/** Explicit links make shared-date propagation reviewable. Unrelated constraints never inherit a change. */
internal object SharedPlanConstraints {
    fun validate(change: JSONObject?) {
        if (change == null) return
        require(change.toString().toByteArray().size <= 3000 && change.getInt("revision") > 0)
        require(change.getString("key") in keys && change.getString("value").length in 1..500 && change.getLong("at") > 0)
    }
    private val keys = setOf("shared exam date", "shared event date", "shared arrival requirement")
    private fun group(plan: PlanWorkspace, name: String) = plan.milestones.filter { it.title.equals(name, true) || it.method.equals(name, true) || it.domain.equals(name, true) }.let {
        require(it.size == 1) { "Which milestone should use that shared constraint?" }; it.single()
    }
    fun answer(plan: PlanWorkspace, input: String, now: Long): PlanningDialogue.Turn? {
        val text = ConversationLanguage.normalize(input).trimEnd('.', '!', '?'); val lower = text.lowercase()
        if (lower in setOf("discard shared change", "keep the old shared date")) return PlanningDialogue.Turn(plan.copy(dialogue = plan.dialogue.copy(sharedChange = null)), "Kept the current shared constraint and draft dates.")
        if (lower in setOf("apply shared change", "use the new shared date", "reschedule affected work")) {
            val change = plan.dialogue.sharedChange ?: return PlanningDialogue.Turn(plan, "There isn't a proposed shared-date change to apply.")
            require(change.getInt("revision") == plan.revision) { "The draft changed after that proposal. State the shared date again so we can recompute its impact." }
            val key = change.getString("key"); val value = change.getString("value"); val at = change.getLong("at")
            require(at > now) { "That proposed date has passed. Choose a future shared date." }
            val next = apply(plan, key, value, at, lower == "reschedule affected work")
            return PlanningDialogue.Turn(next.copy(dialogue = next.dialogue.copy(sharedChange = null)), "Updated the linked draft deadlines and shared context. ${if (lower == "reschedule affected work") "Affected proposed sessions were cleared, including dependent work that needs replanning. Give availability to arrange the remaining work." else "Existing work times still fit the new deadlines."} Unrelated deadlines and saved tasks are unchanged until review.")
        }
        Regex("(?i)^use (shared (?:exam date|event date|arrival requirement)) as (?:the )?deadline for (.+)$").matchEntire(text)?.let { m ->
            val key = m.groupValues[1].lowercase(); val value = plan.facts[key] ?: return PlanningDialogue.Turn(plan, "We haven't recorded $key yet. What date should it use?")
            val date = PlanLanguage.date(value, now, plan.timezone, deadline = true)
            if (date.at == null) return PlanningDialogue.Turn(plan, date.question ?: "Which future date should that shared requirement use?")
            val target = group(plan, m.groupValues[2])
            val linked = plan.copy(milestones = plan.milestones.map { if (it.id == target.id) it.copy(bindings = it.bindings + ("deadline" to key)) else it })
            val next = apply(linked, key, value, date.at, false, history = plan)
            return PlanningDialogue.Turn(next, "Linked ${target.title}'s deadline to $key in our draft. Future changes will show the affected work before we use them.")
        }
        Regex("(?i)^unlink shared deadline from (.+)$").matchEntire(text)?.let { m ->
            val target = group(plan, m.groupValues[1])
            return PlanningDialogue.Turn(plan.changed(milestones = plan.milestones.map { if (it.id == target.id) it.copy(bindings = it.bindings - "deadline") else it }), "Removed that shared link. The current draft deadline is retained as a local constraint.")
        }
        Regex("(?i)^clear milestone deadline for (.+)$").matchEntire(text)?.let { m ->
            val target = group(plan, m.groupValues[1])
            return PlanningDialogue.Turn(plan.changed(milestones = plan.milestones.map { if (it.id == target.id) it.copy(bindings = emptyMap(), boundDeadline = null, facts = it.facts - "deadline") else it }), "Cleared the milestone's overall deadline constraint. Its individual steps keep their dates until you change them.")
        }
        Regex("(?i)^shared (exam date|event date|arrival requirement)(?: is|:) (.{1,500})$").matchEntire(text)?.let { m ->
            val key = "shared ${m.groupValues[1].lowercase()}"; val value = m.groupValues[2]
            val affected = plan.milestones.filter { key in it.bindings.values }
            if (affected.isEmpty()) return PlanningDialogue.Turn(plan.changed(facts = plan.facts + (key to value)), "Kept $key as shared context. No work dates are linked to it yet.")
            if (Regex("(?i)\\b(?:not|maybe|perhaps|if|might)\\b").containsMatchIn(value)) return PlanningDialogue.Turn(plan, "That sounds tentative. What settled date should the linked milestones actually use?")
            val date = PlanLanguage.date(value, now, plan.timezone, deadline = true)
            if (date.at == null) return PlanningDialogue.Turn(plan, date.question ?: "Which future date should this shared constraint use?")
            val proposal = JSONObject().put("revision", plan.revision).put("key", key).put("value", value).put("at", date.at)
            val changed = runCatching { apply(plan, key, value, date.at, false) }
            val explanation = changed.exceptionOrNull()?.message
            return PlanningDialogue.Turn(plan.copy(dialogue = plan.dialogue.copy(sharedChange = proposal)), "That would affect ${affected.joinToString { it.title }}. ${if (explanation == null) "Their linked deadlines can move while keeping the recorded work times. We can use the new shared date." else "$explanation. We can resolve that commitment first, or explicitly reschedule affected work if it is unprotected."} The current shared date and draft work haven't changed.")
        }
        return null
    }
    private fun apply(plan: PlanWorkspace, key: String, value: String, at: Long, clear: Boolean, history: PlanWorkspace = plan): PlanWorkspace {
        val groups = plan.milestones.filter { it.bindings["deadline"] == key }
        val affected = groups.flatMap { it.stepIds }.toSet()
        val deadlines = plan.steps.associate { step ->
            val originalBounds = groups.filter { step.id in it.stepIds }.mapNotNull { it.boundDeadline }
            val priorDerived = originalBounds.any { it == step.deadline }
            step.id to if (step.id in affected && !step.completed && step.workItem) {
                if (step.deadline == null || priorDerived) minOf(at, plan.goalDeadline ?: at) else minOf(at, step.deadline)
            } else step.deadline
        }
        require(plan.steps.none { it.id in affected && it.protected && deadlines[it.id] != it.deadline }) { "A protected step would need a different deadline" }
        val cleared = mutableSetOf<String>()
        if (clear) {
            plan.steps.filter { it.id in affected && it.end?.let { end -> deadlines[it.id]?.let { end > it } } == true }.forEach { cleared += it.id }
            repeat(plan.steps.size) { plan.steps.filter { step -> step.start != null && step.prerequisites.any(cleared::contains) }.forEach { cleared += it.id } }
            require(plan.steps.none { it.id in cleared && (it.protected || it.completed || !it.workItem) }) { "A protected or read-only dependent commitment cannot be cleared" }
        }
        val steps = plan.steps.map { step ->
            val changed = step.copy(deadline = deadlines[step.id])
            if (step.id in cleared) changed.copy(start = null, sessions = emptyList()) else changed
        }
        val milestones = plan.milestones.map { if (it in groups) it.copy(boundDeadline = at, facts = it.facts + ("deadline" to PlanClock.format(at, plan.timezone))) else it }
        val next = history.changed(steps = steps, facts = plan.facts + (key to value), milestones = milestones)
        next.validate(); return next
    }
}
