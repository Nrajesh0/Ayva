package com.focusbyrj.app.util.assistant

import org.json.JSONArray
import org.json.JSONObject

/** Authored action decomposition. The original stable step becomes its final check, preserving dependents. */
internal object PlanBreakdown {
    data class Method(val match: Regex, val parts: List<String>, val reason: String)
    private val methods = listOf(
        Method(Regex("(?i)arrival|venue|reporting|location"), listOf("Check the official location and required arrival time", "Check the final transfer to the destination", "Allow an explicit delay margin"), "The whole route must meet the actual arrival requirement."),
        Method(Regex("(?i)transport|tickets?|train|flight|bus"), listOf("Collect actual departure, arrival and fare details", "Compare the complete routes against the arrival constraint", "Choose an option after checking its conditions"), "Choosing transport depends on actual timings, total cost and the final transfer."),
        Method(Regex("(?i)accommodation|hotel|stay"), listOf("Check whether arrival and rest require a nearby stay", "Compare supplied lodging costs and location", "Decide after checking the journey alternative"), "A stay is conditional on the journey and the rest you need."),
        Method(Regex("(?i)essentials|pack|documents|admit card"), listOf("Check the official document and item requirements", "Gather the required items", "Check the essentials before departure"), "Gathering and checking are different stages; official requirements must come from a real source."),
        Method(Regex("(?i)topics|starting level|syllabus"), listOf("List the actual topics to cover", "Try a small diagnostic on those topics", "Choose the first gap to work on"), "Use your actual syllabus and starting point rather than an invented topic list."),
        Method(Regex("(?i)practi[cs]e|questions|mock"), listOf("Choose a small relevant question set", "Attempt it before checking answers", "Record and explain the errors"), "The attempt and its review reveal different parts of the learning gap."),
        Method(Regex("(?i)errors?|mistakes|difficult topics"), listOf("Identify the cause of one error", "Work through the correct approach", "Try a similar question without the answer"), "A missing concept and an execution slip need different practice."),
        Method(Regex("(?i)revisit|revision|remember|retriev"), listOf("Choose material from an earlier session", "Retrieve the key ideas without looking", "Check the gaps and choose the next interval"), "The next review interval depends on actual recall; it is not fixed by this outline."),
        Method(Regex("(?i)scope|outcome|requirements"), listOf("Describe the smallest useful finished result", "Separate essential requirements from optional additions", "Write a concrete completion check"), "A clear boundary makes scope changes visible."),
        Method(Regex("(?i)resources|prerequisites|dependencies"), listOf("List the inputs the work needs", "Check which inputs are already available", "Identify the next unresolved dependency"), "Available inputs determine which work can begin."),
        Method(Regex("(?i)build|version|implement|prototype"), listOf("Choose one small usable part", "Build that part using the known requirements", "Check it against the intended use"), "A small usable result provides concrete feedback before wider work."),
        Method(Regex("(?i)review|check|verify"), listOf("Choose the acceptance conditions", "Compare the actual result with those conditions", "Record the remaining changes"), "A review needs explicit conditions and an actual result."),
        Method(Regex("(?i)people|responsibilities|owners|event|venue"), listOf("List the essential arrangements", "Confirm who owns each arrangement", "Check the unresolved handoffs"), "A clear owner and handoff can prevent work being left between people."),
        Method(Regex("(?i)routine|habit|repeatable|cue"), listOf("Choose a recognizable starting cue", "Try the smallest useful action", "Review what made the attempt easier or harder"), "A routine needs feedback from actual attempts."),
        Method(Regex("(?i)errands|locations|shopping|stops"), listOf("Separate essentials from optional stops", "Check actual locations and opening times", "Choose an order that fits the available time"), "Opening times and locations are inputs, not facts I can invent.")
    )
    private val generic = Method(Regex(".*"), listOf("Identify the first concrete input", "Do a small verifiable part", "Check the result against the intended outcome"), "This is an organizational scaffold. The subject-specific procedure must come from you.")
    fun method(title: String): Method = methods.firstOrNull { it.match.containsMatchIn(title) } ?: generic
    fun answer(plan: PlanWorkspace, input: String): PlanningDialogue.Turn? {
        val text = ConversationLanguage.normalize(input).trimEnd('.', '!', '?'); val lower = text.lowercase(); val d = plan.dialogue
        if (lower in setOf("reject that breakdown", "discard breakdown", "not that breakdown"))
            return PlanningDialogue.Turn(plan.copy(dialogue = d.copy(breakdown = null)), "Left that breakdown aside. The draft is unchanged.")
        if (lower in setOf("use that breakdown", "use this breakdown", "add those smaller steps")) {
            val proposal = d.breakdown ?: return PlanningDialogue.Turn(plan, "There isn't a current breakdown to use. Which step should we break down?")
            require(proposal.getInt("revision") == plan.revision) { "The draft changed since that breakdown. Ask for a fresh breakdown before using it." }
            if (d.stance == "listen") return PlanningDialogue.Turn(plan, "We can keep it as an idea. Say you want to organize the draft before adding the breakdown.")
            val targets = proposal.getJSONArray("targets"); val steps = plan.steps.toMutableList(); val groups = plan.milestones.toMutableList()
            for (i in 0 until targets.length()) {
                val item = targets.getJSONObject(i); val index = steps.indexOfFirst { it.id == item.getString("id") }; require(index >= 0)
                val original = steps[index]; eligible(plan, original)
                val titles = item.getJSONArray("parts").let { a -> (0 until a.length()).map { a.getString(it) } }
                val children = titles.map { title ->
                    val label = "$title — ${original.title}"
                    require(label.length <= 200 && steps.none { it.title.equals(label, true) }) { "A proposed step name is too long or already exists. Rename the outcome or choose a different breakdown." }
                    PlanStep(title = label, parentId = original.parentId, prerequisites = original.prerequisites, deadline = original.deadline,
                        earliestStart = original.earliestStart, workReminders = original.workReminders, source = "accepted breakdown")
                }
                val finalTitle = "Check completion: ${original.title}"
                require(finalTitle.length <= 200 && steps.none { it.id != original.id && it.title.equals(finalTitle, true) })
                steps[index] = original.copy(title = finalTitle, prerequisites = original.prerequisites + children.map { it.id }, minutes = null, maximumEstimate = null, source = "breakdown verification")
                steps += children
                val owners = groups.filter { original.id in it.stepIds }
                if (owners.isEmpty()) groups += PlanMilestone(title = original.title, stepIds = (children.map { it.id } + original.id).toSet(),
                    facts = original.minutes?.let { mapOf("original effort estimate" to "$it minutes; smaller steps still need estimates") }.orEmpty())
                else groups.indices.forEach { n -> if (original.id in groups[n].stepIds) groups[n] = groups[n].copy(stepIds = groups[n].stepIds + children.map { it.id }) }
            }
            val next = plan.changed(steps = steps, milestones = groups).copy(dialogue = d.copy(breakdown = null, changes = (d.changes + "Accepted a smaller-step breakdown; original outcomes remain final checks with stable references.").takeLast(20)))
            next.validate()
            return PlanningDialogue.Turn(next, "Added the smaller draft actions and kept each original outcome as its final completion check. Existing dependents still point to that check. I kept deadlines and prerequisites; the new actions need their own effort estimates. Saved tasks haven't changed.")
        }
        val request = Regex("(?i)^(?:break down|breakdown|help me break down) (.+)$").matchEntire(text)
        val contextual = lower in setOf("break this down", "break it down", "make this more detailed", "make it more detailed")
        if (request == null && !contextual) return null
        val ref = request?.groupValues?.get(1)
        val group = if (ref == null || ref in setOf("this", "it")) plan.milestones.firstOrNull { it.id == d.focusMilestone }
            else plan.milestones.singleOrNull { it.title.equals(ref, true) || it.method.equals(ref, true) }
        val targets = if (group != null) plan.steps.filter { it.id in group.stepIds && it.workItem && !it.completed && it.source != "breakdown verification" && it.source != "accepted breakdown" }.take(4)
            else PlanLanguage.references(plan, ref ?: "it")
        if (targets.isEmpty()) return PlanningDialogue.Turn(plan, "Which concrete step should we make smaller? ${plan.steps.filter { it.workItem && !it.completed }.take(4).joinToString { it.title }}")
        require(group != null || targets.size == 1) { "That could refer to several steps. Name one outcome to break down." }
        targets.forEach { eligible(plan, it) }
        val items = targets.map { step -> JSONObject().put("id", step.id).put("parts", JSONArray(method(step.title).parts)) }
        val proposal = JSONObject().put("revision", plan.revision).put("targets", JSONArray(items))
        return PlanningDialogue.Turn(plan.copy(dialogue = d.copy(breakdown = proposal)), "A smaller-step proposal:\n" + targets.joinToString("\n\n") { step ->
            val method = method(step.title)
            "${step.title}:\n" + method.parts.joinToString("\n") { "• $it" } + "\n${method.reason}"
        } + "\nThese are suggestions. We can use that breakdown or change it first; no dates or effort have been invented.")
    }
    private fun eligible(plan: PlanWorkspace, step: PlanStep) {
        require(step.workItem && !step.completed && !step.protected && !step.paused && step.start == null) { "Choose an unfinished, unprotected step without a work time. Resolve its current commitment before changing its structure." }
        require(step.source != "breakdown verification" && plan.steps.none { it.parentId == step.id }) { "That outcome is already broken into work. Choose one of its smaller actions." }
        require(step.title.length <= 100) { "Give that outcome a shorter name before creating named smaller actions." }
    }
    fun validate(proposal: JSONObject?) {
        if (proposal == null) return
        require(proposal.toString().toByteArray().size <= 12_000 && proposal.getInt("revision") > 0)
        val targets = proposal.getJSONArray("targets"); require(targets.length() in 1..4)
        for (i in 0 until targets.length()) {
            val item = targets.getJSONObject(i); require(item.getString("id").length in 1..80)
            val parts = item.getJSONArray("parts"); require(parts.length() in 1..4)
            for (n in 0 until parts.length()) require(parts.getString(n).length in 1..100)
        }
    }
}
