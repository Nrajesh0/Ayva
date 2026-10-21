package com.focusbyrj.app.util.assistant

import java.util.Calendar
import java.util.TimeZone

/** Timestamped observations, not invented explanations. Kept in the existing bounded dialogue history. */
internal object PlanChangeTrail {
    fun record(before: PlanWorkspace?, after: PlanWorkspace): PlanWorkspace {
        if (before == null) return after
        val changes = mutableListOf<String>()
        val old = before.steps.associateBy { it.id }; val current = after.steps.associateBy { it.id }
        (current.keys - old.keys).takeIf { it.isNotEmpty() }?.let { changes += "Added ${it.size} draft step(s): ${it.take(3).joinToString { id -> current.getValue(id).title }}" }
        (old.keys - current.keys).takeIf { it.isNotEmpty() }?.let { changes += "Removed ${it.size} draft step(s): ${it.take(3).joinToString { id -> old.getValue(id).title }}" }
        current.values.forEach { step -> old[step.id]?.let { prior ->
            if (prior.title != step.title) changes += "Renamed ‘${prior.title}’ to ‘${step.title}’"
            if (prior.workSessions != step.workSessions) changes += "Work times for ‘${step.title}’: ${prior.start?.let { PlanClock.format(it, before.timezone) } ?: "unscheduled"} → ${step.start?.let { PlanClock.format(it, after.timezone) } ?: "open for replanning"}"
            if (prior.deadline != step.deadline) changes += "Deadline for ‘${step.title}’: ${prior.deadline?.let { PlanClock.format(it, before.timezone) } ?: "open"} → ${step.deadline?.let { PlanClock.format(it, after.timezone) } ?: "open"}"
            if (prior.prerequisites != step.prerequisites) changes += "Updated the recorded prerequisites for ‘${step.title}’"
            if (prior.minutes != step.minutes) changes += "Effort for ‘${step.title}’: ${prior.minutes?.toString() ?: "unknown"} → ${step.minutes?.toString() ?: "needs an estimate"} minutes"
            if (prior.workState != step.workState) changes += "‘${step.title}’ is ${step.workState.name.lowercase()}"
        } }
        (before.facts.keys + after.facts.keys).distinct().filter { before.facts[it] != after.facts[it] }.forEach { changes += "$it: ${after.facts[it] ?: "left open"}" }
        if (before.milestones != after.milestones) changes += "Updated milestone grouping or scoped constraints"
        if (before.goal != after.goal) changes += "Goal: ${after.goal}"
        if (before.savedRevision != after.savedRevision && after.savedRevision == after.revision) changes += "Applied the reviewed draft to saved tasks"
        if (changes.isEmpty()) return after
        val summary = changes.take(4).joinToString("; ").take(550) + if (changes.size > 4) "; ${changes.size - 4} further detail change(s)" else ""
        val event = "${System.currentTimeMillis()}|${summary.take(570)}"
        return after.copy(dialogue = after.dialogue.copy(changes = (after.dialogue.changes + event).takeLast(20)))
    }
    fun describe(plan: PlanWorkspace, yesterday: Boolean = false): String {
        val now = System.currentTimeMillis()
        val cutoff = Calendar.getInstance(TimeZone.getTimeZone(plan.timezone)).apply {
            timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        val dated = plan.dialogue.changes.mapNotNull { line ->
            val at = line.substringBefore('|').toLongOrNull() ?: return@mapNotNull null
            if (at > now || yesterday && at < cutoff) null else PlanClock.format(at, plan.timezone) + ": " + line.substringAfter('|')
        }
        return dated.takeLast(4).joinToString("\n").ifBlank {
            if (yesterday) "No timestamped draft changes are recorded since yesterday. Older un-timestamped notes aren't presented as yesterday's changes."
            else plan.dialogue.changes.takeLast(4).joinToString("\n").ifBlank { "No draft changes are recorded yet." }
        }
    }
}
