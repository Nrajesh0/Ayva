package com.focusbyrj.app.util.assistant

/** The same dialogue grammar runs against an explicit milestone view, then publishes into the root draft. */
internal object MilestonePlanning {
    private fun matches(plan: PlanWorkspace, ref: String): List<PlanMilestone> {
        val name = PlanLanguage.normalize(ref).lowercase().removePrefix("the ").removeSuffix(" milestone")
        val exact = plan.milestones.filter { it.id == ref || it.title.equals(name, true) || it.method.equals(name, true) || it.domain.equals(name, true) }
        return exact.ifEmpty { plan.milestones.filter { it.title.contains(name, true) } }
    }
    private fun scope(plan: PlanWorkspace, ref: String): PlanMilestone {
        val found = matches(plan, ref)
        require(found.size == 1) { "Which milestone do you mean by ‘$ref’? ${plan.milestones.joinToString { it.title }}" }
        return found.single()
    }
    private fun overview(plan: PlanWorkspace, group: PlanMilestone? = null): String {
        val groups = group?.let(::listOf) ?: plan.milestones
        if (groups.isEmpty()) return "There aren't milestone groups yet. We can suggest an outline or create one around the outcome you want."
        return groups.joinToString("\n\n") { item ->
            val steps = plan.steps.filter { it.id in item.stepIds }
            buildString {
                append("${item.title}: ${steps.count { it.completed }} completed, ${steps.count { !it.completed && it.workItem }} unfinished step(s).")
                if (item.facts.isNotEmpty()) append("\n" + item.facts.entries.joinToString("; ") { "${it.key}: ${it.value}" })
                steps.take(8).forEach { append("\n• ${it.title}${if (it.paused) " (${it.workState.name.lowercase()})" else ""}") }
                if (steps.size > 8) append("\n…and ${steps.size - 8} more steps.")
            }
        }
    }
    fun answer(plan: PlanWorkspace, input: String, now: Long): PlanningDialogue.Turn? {
        val text = PlanLanguage.normalize(input).trimEnd('.', '!', '?'); val lower = text.lowercase(); val d = plan.dialogue
        if (plan.pending != null || plan.hypotheticalView || text.startsWith('/') || lower.startsWith("what if ")) return null
        if (lower in setOf("show milestones", "milestones", "show the milestones", "give me the overview") && plan.milestones.isNotEmpty())
            return PlanningDialogue.Turn(plan, overview(plan))
        if (lower in setOf("back to the whole plan", "back to whole plan", "focus on the whole plan", "leave milestone"))
            return PlanningDialogue.Turn(plan.copy(dialogue = d.copy(focusMilestone = null, question = "", proposals = emptyList())), "Back to the whole plan. The milestone details are still here.")
        Regex("(?i)^(?:focus on|let'?s work on|show milestone) (.+)$").matchEntire(text)?.let { m ->
            val group = scope(plan, m.groupValues[1])
            val opening = PlanningDialogue.opening(view(plan, group))
            val focused = plan.copy(dialogue = d.copy(focusMilestone = group.id, question = if (group.facts.isEmpty()) opening.plan.dialogue.question else "", proposals = emptyList(), outlineMethods = emptyList(), outlineRevision = null))
            return PlanningDialogue.Turn(focused, "Let's work on ${group.title}.\n" + overview(plan, group) + "\nDetails we discuss here belong to this milestone. You can return to the whole plan at any time." + if (group.facts.isEmpty()) " ${opening.text}" else "")
        }
        Regex("(?i)^(?:add milestone|create milestone|make a milestone for) (.{1,200})$").matchEntire(text)?.let { m ->
            val name = m.groupValues[1]
            require(plan.milestones.none { it.title.equals(name, true) }) { "That milestone already exists. We can focus on it instead." }
            val group = PlanMilestone(title = name, domain = PlanLanguage.domain(name))
            val next = plan.changed(milestones = plan.milestones + group).copy(dialogue = d.copy(focusMilestone = group.id, question = "outcome", proposals = emptyList(), outlineMethods = emptyList(), outlineRevision = null))
            return PlanningDialogue.Turn(next, "Created the draft milestone ‘$name’. What would a useful outcome look like? It isn't a new app task.")
        }
        if (lower in setOf("suggest milestones", "organize into milestones", "organise into milestones", "break the plan into milestones")) {
            val methods = PlanningMethods.groups(plan.goal + " " + plan.facts["purpose"].orEmpty()).take(8)
            val next = plan.copy(dialogue = d.copy(question = "milestone_outline", outlineMethods = methods.map { it.id }, outlineRevision = plan.revision, proposals = emptyList()))
            return PlanningDialogue.Turn(next, "We could organize it around:\n" + methods.joinToString("\n") { "• ${PlanningMethods.title(it.id)} — ${it.steps.first().reason}" } + "\nThese are suggestions. We can use those milestones or change the outline first.")
        }
        if (d.outlineMethods.isNotEmpty() && lower in setOf("use those milestones", "use the milestones", "use this outline", "use that outline", "yes", "yes please")) {
            if (d.stance == "listen") return PlanningDialogue.Turn(plan, "Should we organize the draft now, or keep discussing the outline? Say ‘help me organize it’ when you're ready.")
            if (d.outlineRevision != plan.revision) return PlanningDialogue.Turn(plan.copy(dialogue = d.copy(outlineMethods = emptyList(), outlineRevision = null)), "The draft changed since that outline. Let's suggest milestones again using the current details.")
            val steps = plan.steps.toMutableList(); val groups = plan.milestones.toMutableList()
            d.outlineMethods.forEach { id ->
                val method = PlanningMethods.method(id) ?: throw IllegalArgumentException("That suggested method is unavailable. Ask for a fresh outline.")
                val index = groups.indexOfFirst { it.method == id }
                val group = groups.getOrNull(index) ?: PlanMilestone(title = PlanningMethods.title(id), domain = PlanningMethods.domain(id), method = id)
                val ids = method.steps.map { suggestion ->
                    val existing = steps.singleOrNull { it.title.equals(suggestion.title, true) }
                    (existing ?: PlanStep(title = suggestion.title, source = "accepted milestone outline").also { steps.add(it) }).id
                }.toSet()
                val updated = group.copy(stepIds = group.stepIds + ids)
                if (index < 0) groups += updated else groups[index] = updated
            }
            val next = plan.changed(steps = steps, milestones = groups).copy(dialogue = d.copy(question = "", outlineMethods = emptyList(), outlineRevision = null,
                changes = (d.changes + "Organized accepted work into ${groups.size} milestone(s).").takeLast(20)))
            next.validate()
            return PlanningDialogue.Turn(next, "Organized our draft into milestones and linked the agreed starting steps. We haven't assumed durations, dates or bookings.\n" + overview(next))
        }
        if (d.outlineMethods.isNotEmpty() && lower in setOf("not those milestones", "reject that outline", "no thanks", "no"))
            return PlanningDialogue.Turn(plan.copy(dialogue = d.copy(outlineMethods = emptyList(), outlineRevision = null, question = "")), "We'll leave that outline aside. We can create a milestone using your own words.")
        Regex("(?i)^(?:include|put) (.+?) in (?:milestone )?(.+)$").matchEntire(text)?.let { m ->
            val found = PlanLanguage.references(plan, m.groupValues[1]); require(found.size == 1) { "Which draft step should belong to that milestone?" }
            val group = scope(plan, m.groupValues[2])
            val next = plan.changed(milestones = plan.milestones.map { if (it.id == group.id) it.copy(stepIds = it.stepIds + found.single().id) else it })
            return PlanningDialogue.Turn(next, "Linked ‘${found.single().title}’ to ${group.title}. Shared work stays one step; no duplicate task was created.")
        }
        Regex("(?i)^shared (exam date|event date|arrival requirement)(?: is|:) (.{1,500})$").matchEntire(text)?.let { m ->
            val key = "shared ${m.groupValues[1].lowercase()}"
            return PlanningDialogue.Turn(plan.changed(facts = plan.facts + (key to m.groupValues[2])), "Kept $key as shared context for the milestones. It doesn't assign a work time or deadline by itself.")
        }
        Regex("(?i)^for (.+?), (?:the )?deadline(?: is|:) (.+)$").matchEntire(text)?.let { m ->
            val group = scope(plan, m.groupValues[1])
            if (Regex("(?i)\\b(?:not|maybe|perhaps|if|might|cannot|don't)\\b|[\"“”]").containsMatchIn(m.groupValues[2]))
                return PlanningDialogue.Turn(plan, "I haven't treated that as a settled deadline. Which date should this milestone actually be due?")
            val date = PlanLanguage.date(m.groupValues[2], now, plan.timezone, deadline = true)
            if (date.at == null) return PlanningDialogue.Turn(plan, date.question ?: "Which future deadline do you mean?")
            val affected = plan.steps.filter { it.id in group.stepIds && it.workItem && !it.completed }
            require(affected.none { it.protected && it.deadline != date.at }) { "That milestone contains a protected deadline. Resolve that commitment first; nothing changed." }
            val overlap = plan.milestones.filter { it.id != group.id && it.stepIds.any(group.stepIds::contains) }
            require(overlap.isEmpty()) { "That milestone shares work with ${overlap.joinToString { it.title }}. Set the shared step's deadline explicitly so its effect on both milestones is clear." }
            val next = plan.changed(steps = plan.steps.map { if (it in affected) it.copy(deadline = minOf(it.deadline ?: date.at, date.at, plan.goalDeadline ?: date.at)) else it }, milestones = plan.milestones.map {
                if (it.id == group.id) it.copy(facts = it.facts + ("deadline" to PlanClock.format(date.at, plan.timezone)), bindings = it.bindings - "deadline", boundDeadline = date.at) else it
            })
            next.validate()
            return PlanningDialogue.Turn(next, "Set the draft deadline for ${group.title}'s unfinished steps to ${PlanClock.format(date.at, plan.timezone)}. Other milestones and saved tasks are unchanged.")
        }
        return null
    }
    private fun view(plan: PlanWorkspace, group: PlanMilestone) = plan.copy(goal = group.title, domain = group.domain,
        facts = plan.facts.filterKeys { it.startsWith("shared ") } + group.facts, milestones = emptyList(),
        dialogue = plan.dialogue.copy(focusMilestone = null, outlineMethods = emptyList(), outlineRevision = null))
    private fun publish(plan: PlanWorkspace, group: PlanMilestone, result: PlanningDialogue.Turn): PlanningDialogue.Turn {
        val candidate = result.plan
        val additions = candidate.steps.map { it.id }.toSet() - plan.steps.map { it.id }.toSet()
        val updated = group.copy(facts = candidate.facts.filterKeys { !it.startsWith("shared ") }, domain = candidate.domain, stepIds = group.stepIds + additions)
        val changed = updated != group || candidate.steps != plan.steps || candidate.removed != plan.removed
        val next = if (changed) plan.changed(steps = candidate.steps, removed = candidate.removed, milestones = plan.milestones.map { if (it.id == group.id) updated else it }) else plan
        return PlanningDialogue.Turn(next.copy(dialogue = candidate.dialogue.copy(focusMilestone = plan.dialogue.focusMilestone,
            outlineMethods = plan.dialogue.outlineMethods, outlineRevision = plan.dialogue.outlineRevision)), "${group.title}: ${result.text}")
    }
    fun converse(plan: PlanWorkspace, input: String): PlanningDialogue.Turn? {
        val explicit = Regex("(?i)^for (.+?), (.+)$").matchEntire(input)
        val group = if (explicit != null) scope(plan, explicit.groupValues[1]) else plan.milestones.firstOrNull { it.id == plan.dialogue.focusMilestone } ?: return null
        val body = explicit?.groupValues?.get(2) ?: input
        if (body.lowercase().trimEnd('.', '!', '?') in setOf("show this milestone", "make it simpler", "give me the full picture", "summarize the plan"))
            return PlanningDialogue.Turn(plan, overview(plan, group))
        val result = PlanningDialogue.answer(view(plan, group), body) ?: return null
        return publish(plan, group, result)
    }
    fun repair(plan: PlanWorkspace, input: String): PlanningDialogue.Turn {
        val group = plan.milestones.firstOrNull { it.id == plan.dialogue.focusMilestone } ?: return PlanningDialogue.repair(plan, input)
        return publish(plan, group, PlanningDialogue.repair(view(plan, group), input))
    }
}

internal object PlanningConversation {
    fun answer(plan: PlanWorkspace, input: String, now: Long): PlanningDialogue.Turn? {
        if (plan.pending != null || plan.hypotheticalView || input.startsWith('/')) return null
        PlanningReasoning.answer(plan, input)?.let { return it }
        if (input.startsWith("what if ", true)) return null
        SharedPlanConstraints.answer(plan, input, now)?.let { return it }
        PlanBreakdown.answer(plan, input)?.let { return it }
        return MilestonePlanning.answer(plan, input, now) ?: MilestonePlanning.converse(plan, input) ?: PlanningDialogue.answer(plan, input)
    }
}
