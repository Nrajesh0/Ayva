package com.focusbyrj.app.util.assistant

import org.json.JSONObject

internal object PlanDiff {
    fun describe(plan: PlanWorkspace): String {
        val raw = plan.undoDrafts.lastOrNull() ?: plan.previousDraft ?: return "No earlier draft checkpoint is retained. Say ‘saved history’ for local task commits."
        val previous = PlanWorkspace.read(JSONObject(raw))
        val old = previous.steps.associateBy { it.id }; val current = plan.steps.associateBy { it.id }
        val lines = mutableListOf<String>()
        plan.steps.filter { it.id !in old }.forEach { lines += "Added: ${it.title}" }
        previous.steps.filter { it.id !in current }.forEach { lines += "Removed from draft: ${it.title}" }
        plan.steps.forEach { step ->
            val before = old[step.id] ?: return@forEach
            if (before.title != step.title) lines += "Renamed: ${before.title} → ${step.title}"
            if (before.workSessions != step.workSessions) lines += "${step.title}: work changed from ${before.workSessions.size} to ${step.workSessions.size} session(s)"
            if (before.deadline != step.deadline) lines += "${step.title}: deadline changed"
            if (before.minutes != step.minutes || before.maximumEstimate != step.maximumEstimate) lines += "${step.title}: estimate changed"
            if (before.parentId != step.parentId) lines += "${step.title}: parent changed"
            if (before.prerequisites != step.prerequisites) lines += "${step.title}: prerequisites changed"
            if (before.workReminders != step.workReminders) lines += "${step.title}: work-start reminders ${if (step.workReminders) "on" else "off"}"
        }
        if (previous.policy != plan.policy) lines += "Scheduling constraints changed"
        if (previous.facts != plan.facts) lines += "Updated facts: " + (previous.facts.keys + plan.facts.keys).filter { previous.facts[it] != plan.facts[it] }.joinToString()
        return lines.joinToString("\n").ifBlank { "No task content changed in the retained checkpoint." } + "\nThese are draft changes; review and apply before saved tasks change."
    }
}
