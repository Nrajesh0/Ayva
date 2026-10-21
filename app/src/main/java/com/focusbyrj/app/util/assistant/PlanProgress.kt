package com.focusbyrj.app.util.assistant

/** Explicit draft status; it never marks an app task complete or infers elapsed work. */
internal object PlanProgress {
    fun answer(plan: PlanWorkspace, input: String): PlanningDialogue.Turn? {
        val text = input.trim().trimEnd('.', '!', '?')
        val lower = text.lowercase()
        val focused = plan.milestones.firstOrNull { it.id == plan.dialogue.focusMilestone }
        val scopedSteps = if (focused == null) plan.steps else plan.steps.filter { it.id in focused.stepIds }
        val unfinished = scopedSteps.filter { it.workItem && !it.completed }
        if (lower in setOf("how are we doing", "plan progress", "how is the plan going", "what's blocking us", "what is blocking us")) {
            val completed = scopedSteps.count { it.workItem && it.completed }
            val paused = unfinished.filter { it.paused }
            val active = unfinished.filter { it.workState == PlanWorkState.ACTIVE }
            return PlanningDialogue.Turn(plan, buildString {
                append("$completed completed saved step(s), ${unfinished.size} unfinished in this draft.")
                if (active.isNotEmpty()) append(" In progress: ${active.joinToString { it.title }}.")
                paused.forEach { append("\n${it.title}: ${it.workState.name.lowercase()}${it.blockReason?.let { reason -> " — $reason" }.orEmpty()}.") }
                if (paused.isEmpty()) append(" No explicit blockers are recorded.")
                append(" I only count completion reported by the saved tasks; I don't infer it from time passing.")
            })
        }
        if (lower in setOf("what can i do now", "what should i do first", "what's the next step", "what is the next step")) {
            val ready = unfinished.filter { step -> !step.paused && step.prerequisites.all { id -> plan.steps.first { it.id == id }.completed } }
            val candidate = ready.sortedWith(compareBy<PlanStep> { if (it.workState == PlanWorkState.ACTIVE) 0 else 1 }.thenBy { it.deadline ?: Long.MAX_VALUE }).firstOrNull()
            return PlanningDialogue.Turn(plan, if (candidate == null) {
                if (unfinished.isEmpty()) "There isn't unfinished draft work to choose from yet. We can decide the next useful step together."
                else "The remaining steps are paused or depend on unfinished work. Let's resolve a blocker or a prerequisite before adding another commitment."
            } else buildString {
                append("${if (candidate.workState == PlanWorkState.ACTIVE) "Continue" else "Start with"} ‘${candidate.title}’")
                candidate.minutes?.let { append(" — $it minutes of estimated remaining work") }
                append(". Its recorded prerequisites are complete")
                if (candidate.deadline != null) append(" and it has a deadline of ${PlanClock.format(candidate.deadline, plan.timezone)}")
                append(". This is a suggestion, not a new schedule.")
            })
        }
        var reference: String? = null
        var status: PlanWorkState? = null
        var reason: String? = null
        Regex("(?i)^(.+?) is blocked(?: (?:because|by|until|on) (.+))?$").matchEntire(text)?.let {
            reference = it.groupValues[1]; status = PlanWorkState.BLOCKED; reason = it.groupValues[2].ifBlank { null }
        }
        Regex("(?i)^(?:block|pause) (.+?)(?: because (.+))?$").matchEntire(text)?.let {
            reference = it.groupValues[1]; status = PlanWorkState.BLOCKED; reason = it.groupValues[2].ifBlank { null }
        }
        Regex("(?i)^skip (.+?)(?: for now)?$").matchEntire(text)?.let {
            reference = it.groupValues[1]; status = PlanWorkState.SKIPPED
        }
        Regex("(?i)^(?:unblock|unskip|resume work on|continue work on) (.+)$").matchEntire(text)?.let {
            reference = it.groupValues[1]; status = PlanWorkState.READY
        }
        Regex("(?i)^(.+?) is ready again$").matchEntire(text)?.let { reference = it.groupValues[1]; status = PlanWorkState.READY }
        Regex("(?i)^(?:i(?:'m| am)|we(?:'re| are)) working on (.+)$").matchEntire(text)?.let { reference = it.groupValues[1]; status = PlanWorkState.ACTIVE }
        val target = status ?: return null
        require(ConversationMeaning.clauses(input).size == 1) { "Let's change the work status separately from those other edits. The combined request hasn't changed the draft." }
        val matches = PlanLanguage.references(plan, reference!!)
        require(matches.size == 1) { "Which draft step do you mean by ‘$reference’?" }
        val step = matches.single()
        require(step.workItem && !step.completed && !step.protected) { "That step is protected, completed or read-only. Its work was left unchanged." }
        require(reason == null || reason!!.length <= 500) { "Keep the blocker description under 500 characters." }
        val affected = mutableSetOf(step.id)
        val pausing = target in setOf(PlanWorkState.BLOCKED, PlanWorkState.SKIPPED)
        if (pausing) {
            repeat(plan.steps.size) { plan.steps.filter { it.prerequisites.any(affected::contains) }.forEach { affected += it.id } }
            require(plan.steps.none { it.id in affected && it.id != step.id && it.start != null && (it.protected || it.completed || !it.workItem) }) {
                "A protected or read-only dependent has a work time. Resolve that commitment before pausing its prerequisite; nothing changed."
            }
        }
        val moved = plan.steps.filter { pausing && it.id in affected && it.start != null }.map { it.title }
        val next = plan.changed(steps = plan.steps.map { item ->
            val updated = if (item.id == step.id) item.copy(workState = target, blockReason = reason) else item
            if (pausing && item.id in affected && !item.completed) updated.copy(start = null, sessions = emptyList()) else updated
        }, selected = step.id)
        next.validate()
        return PlanningDialogue.Turn(next, buildString {
            append("‘${step.title}’ is ${when (target) { PlanWorkState.READY -> "ready again"; PlanWorkState.ACTIVE -> "in progress"; PlanWorkState.BLOCKED -> "blocked"; PlanWorkState.SKIPPED -> "skipped for now" }} in our draft.")
            reason?.let { append(" Blocker: $it.") }
            if (moved.isNotEmpty()) append(" Cleared proposed work times for ${moved.joinToString()} so dependent work cannot run ahead of it.")
            append(" Saved tasks are unchanged until review.")
            if (pausing) append(" The step remains in the plan and can be resumed; skipping does not delete it or satisfy a prerequisite.")
        })
    }
}
