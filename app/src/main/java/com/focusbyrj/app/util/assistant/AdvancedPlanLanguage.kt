package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.WorkSession
import java.util.Calendar
import java.util.TimeZone

/** Composable plan operations. These alter proposals; canonical writes still require review. */
internal object AdvancedPlanLanguage {
    data class Turn(val plan: PlanWorkspace, val message: String)
    fun edit(plan: PlanWorkspace, text: String, now: Long, occupied: List<PlanStep>): Turn? {
        fun one(ref: String) = PlanLanguage.references(plan, ref).singleOrNull()
            ?: throw IllegalArgumentException("Which step do you mean by ‘$ref’? Use one exact name or number")
        fun publish(next: PlanWorkspace, message: String): Turn {
            next.validate()
            require(next.steps.none { step -> occupied.any { PlanWorkspace.conflicts(step, it, next.policy.bufferMinutes) } }) { "That proposal conflicts with other saved or drafted work; choose another time or availability window" }
            return Turn(next, message)
        }
        fun change(step: PlanStep, replacement: PlanStep, message: String): Turn {
            require(step.workItem && !step.completed) { "This step is read-only context; change the active work instead" }
            return publish(
            plan.changed(steps = plan.steps.map { if (it.id == step.id) replacement else it }, selected = step.id), message)
        }
        fun editable(step: PlanStep) = require(step.workItem && !step.completed && !step.protected) { "‘${step.title}’ is protected. Unlock it before changing its work" }
        fun duration(value: String) = PlanLanguage.duration(value) ?: throw IllegalArgumentException("Use a duration between one minute and 24 hours")
        fun date(value: String): Long {
            val result = PlanLanguage.date(value, now, plan.timezone)
            return result.at ?: throw IllegalArgumentException(result.question ?: "Which future day and time?")
        }
        if (plan.pending?.optString("kind") == "decompose" && text.equals("use that breakdown", true)) {
            val pending = plan.pending
            val parent = plan.steps.firstOrNull { it.id == pending.getString("parent") } ?: error("The parent step is unavailable")
            val titles = pending.getJSONArray("titles"); require(titles.length() in 1..12)
            val added = (0 until titles.length()).map { PlanStep(title = titles.getString(it), parentId = parent.id, source = "user accepted authored breakdown",
                minutes = plan.facts["default task minutes"]?.toIntOrNull()) }
            return publish(plan.changed(steps = plan.steps + added), "Added the accepted child steps. The parent's work is unchanged; child dates and any missing estimates remain open.")
        }
        Regex("(?i)^(?:break down|break) (.+?)(?: down)?$").matchEntire(text)?.let {
            val parent = one(it.groupValues[1])
            val titles = when {
                Regex("(?i)research|compare|choose").containsMatchIn(parent.title) -> listOf("List questions for ${parent.title}", "Gather options for ${parent.title}", "Summarize findings for ${parent.title}")
                Regex("(?i)pack|prepare").containsMatchIn(parent.title) -> listOf("List essentials for ${parent.title}", "Gather items for ${parent.title}", "Check readiness for ${parent.title}")
                Regex("(?i)study|review|learn|practice").containsMatchIn(parent.title) -> listOf("Review notes for ${parent.title}", "Practice examples for ${parent.title}", "Check mistakes for ${parent.title}")
                else -> listOf("Clarify the outcome for ${parent.title}", "Prepare inputs for ${parent.title}", "Do the main work for ${parent.title}", "Check the result for ${parent.title}")
            }.map { it.take(200) }
            return Turn(plan.copy(pending = org.json.JSONObject().put("kind", "decompose").put("parent", parent.id).put("titles", org.json.JSONArray(titles))),
                "Optional breakdown for ‘${parent.title}’:\n" + titles.mapIndexed { index, title -> "${index + 1}. $title" }.joinToString("\n") + "\nSay ‘use that breakdown’, or tell me your own child steps. Nothing was added yet.")
        }
        Regex("(?i)^wait (.+?) after (.+?) before (.+)$").matchEntire(text)?.let {
            val predecessor = one(it.groupValues[2]); val successor = one(it.groupValues[3]); val wait = duration(it.groupValues[1])
            return change(successor, successor.copy(prerequisites = successor.prerequisites + predecessor.id, afterMinutes = wait), "Added a $wait-minute gap after the prerequisite. Existing work must satisfy it too.")
        }
        Regex("(?i)^move (.+?) to (.+?) and (?:replan|reschedule) (?:its |the )?dependents$").matchEntire(text)?.let {
            val step = one(it.groupValues[1]); editable(step); val at = date(it.groupValues[2])
            val window = plan.facts["availability"]?.let { value -> Regex("^(\\d+):00–(\\d+):00 (today|daily.*)$").matchEntire(value) }
                ?: throw IllegalArgumentException("Give an availability window first so I know where dependent work may move")
            val affected = mutableSetOf(step.id)
            repeat(plan.steps.size) { plan.steps.filter { node -> node.prerequisites.any { dependency -> dependency in affected } }.forEach { node -> affected.add(node.id) } }
            val targets = affected - step.id
            val steps = plan.steps.map { node -> when {
                node.id == step.id -> node.shifted(at)
                node.id in targets && !node.protected -> node.copy(start = null, sessions = emptyList())
                else -> node
            } }
            val result = PlanScheduler.arrange(plan.copy(steps = steps), now, if (window.groupValues[3] == "today") 1 else 28,
                window.groupValues[1].toInt(), window.groupValues[2].toInt(), occupied, targets)
            return publish(plan.changed(steps = result.steps, selected = step.id), "Moved ‘${step.title}’ and replanned only its dependent work. Other steps and protected sessions stayed in place. ${result.unscheduled.size} dependent step(s) remain unscheduled.")
        }
        Regex("(?i)^(?:use|set) time ?zone(?: to)? (.+)$").matchEntire(text)?.let {
            val zone = it.groupValues[1].trim()
            require(zone in TimeZone.getAvailableIDs()) { "Use a full time-zone name such as Asia/Kolkata or America/New_York" }
            return publish(plan.changed().copy(timezone = zone), "The draft now uses $zone. Existing work keeps the same instants; new local times and availability use this zone. Review the displayed times before applying.")
        }
        Regex("(?i)^(lock|protect|unlock|unprotect) (.+)$").matchEntire(text)?.let {
            val step = one(it.groupValues[2]); val protected = it.groupValues[1].lowercase() in setOf("lock", "protect")
            return change(step, step.copy(protected = protected), if (protected) "Protected ‘${step.title}’. Replanning will preserve its work." else "Unlocked ‘${step.title}’ for explicit changes.")
        }
        Regex("(?i)^(?:work reminders for|reminders for) (.+?) (on|off)$").matchEntire(text)?.let {
            val step = one(it.groupValues[1]); val enabled = it.groupValues[2].equals("on", true)
            return change(step, step.copy(workReminders = enabled), "Work-start reminders ${if (enabled) "enabled" else "disabled"} in the draft. Review and apply to update saved reminders.")
        }
        Regex("(?i)^remind me when (.+?) starts?$").matchEntire(text)?.let {
            val step = one(it.groupValues[1]); return change(step, step.copy(workReminders = true), "Added work-start reminders to the draft; review and apply to save them.")
        }
        Regex("(?i)^(?:add|schedule) (?:a )?session (?:for|of) (.+?) (?:on|at) (.+?) for (.+)$").matchEntire(text)?.let {
            val step = one(it.groupValues[1]); editable(step)
            val session = WorkSession(start = date(it.groupValues[2]), minutes = duration(it.groupValues[3]))
            return change(step, step.withSessions(step.workSessions + session).copy(minutes = step.minutes ?: session.minutes), "Added a work session to ‘${step.title}’. Other sessions stay in place.")
        }
        Regex("(?i)^(?:move|reschedule) session (\\d+) (?:of|for) (.+?) to (.+)$").matchEntire(text)?.let {
            val step = one(it.groupValues[2]); editable(step)
            val index = it.groupValues[1].toInt() - 1
            require(index in step.workSessions.indices) { "Choose a session number shown in this step" }
            val sessions = step.workSessions.mapIndexed { i, session -> if (i == index) session.copy(start = date(it.groupValues[3])) else session }
            return change(step, step.withSessions(sessions), "Moved just that session. The remaining work stays in place.")
        }
        Regex("(?i)^(?:remove|cancel|unschedule) session (\\d+) (?:of|for) (.+)$").matchEntire(text)?.let {
            val step = one(it.groupValues[2]); editable(step)
            val index = it.groupValues[1].toInt() - 1
            require(index in step.workSessions.indices) { "Which displayed session number should be removed?" }
            return change(step, step.withSessions(step.workSessions.filterIndexed { i, _ -> i != index }), "Removed only that work session from the draft; the task and deadline remain.")
        }
        Regex("(?i)^split (.+?) into (.+?) sessions?$").matchEntire(text)?.let {
            val step = one(it.groupValues[1]); editable(step)
            val size = duration(it.groupValues[2].replace(Regex("(?i)-(minute|hour)"), " $1"))
            require(step.minutes != null) { "Give this step an estimated duration first" }
            if (step.start == null) return publish(plan.changed(steps = plan.steps.map { s -> if (s.id == step.id) s.copy(minimumChunk = minOf(size, s.minutes!!)) else s })
                .copy(policy = plan.policy.copy(maximumSession = size)), "Set a $size-minute session limit. Tell me your availability to schedule the work.")
            require(step.workSessions.size == 1) { "This task already has several sessions. Move or remove a session by number" }
            val original = step.workSessions.single(); var at = original.start; var remaining = original.minutes
            val sessions = mutableListOf<WorkSession>()
            while (remaining > 0) {
                val minutes = minOf(size, remaining)
                sessions.add(WorkSession(start = at, minutes = minutes)); remaining -= minutes
                at += minutes * 60_000L + plan.policy.bufferMinutes * 60_000L
            }
            return change(step, step.withSessions(sessions), "Split the existing work into ${sessions.size} sessions, including your requested buffer. Review the finish time and deadline.")
        }
        Regex("(?i)^(?:estimate|duration)(?: for)? (.+?) (?:is|:) (.+?) (?:to|–|-) (.+)$").matchEntire(text)?.let {
            val step = one(it.groupValues[1]); val unit = Regex("(?i)(minutes?|mins?|hours?|hrs?)$").find(it.groupValues[3])?.value
            val low = PlanLanguage.duration(it.groupValues[2]) ?: duration("${it.groupValues[2]} ${unit ?: "minutes"}"); val high = duration(it.groupValues[3])
            require(high >= low) { "Put the shorter estimate first" }
            return change(step, step.copy(minutes = low, maximumEstimate = high), "Recorded a $low–$high minute estimate. New automatic scheduling reserves the upper estimate; existing sessions stay unchanged.")
        }
        Regex("(?i)^(?:earliest start for|not before) (.+?) (?:is |: |until )?((?:today|tomorrow|\\d{4}-|monday|tuesday|wednesday|thursday|friday|saturday|sunday).+)$").matchEntire(text)?.let {
            val step = one(it.groupValues[1]); return change(step, step.copy(earliestStart = date(it.groupValues[2])), "Recorded the earliest work start. Existing work must satisfy it too.")
        }
        Regex("(?i)^(?:make|move) (.+?) (?:a subtask of|under) (.+)$").matchEntire(text)?.let {
            val step = one(it.groupValues[1]); val parent = one(it.groupValues[2])
            return change(step, step.copy(parentId = parent.id), "Moved ‘${step.title}’ under ‘${parent.title}’ in the draft.")
        }
        Regex("(?i)^(?:make|move) (.+?) (?:top level|to top level)$").matchEntire(text)?.let {
            val step = one(it.groupValues[1]); return change(step, step.copy(parentId = null), "Made ‘${step.title}’ a top-level step.")
        }
        Regex("(?i)^(.+?) (?:no longer depends on|doesn't depend on|does not depend on) (.+)$").matchEntire(text)?.let {
            val step = one(it.groupValues[1]); val dependency = one(it.groupValues[2])
            return change(step, step.copy(prerequisites = step.prerequisites - dependency.id), "Removed that prerequisite from the draft.")
        }
        val lower = text.lowercase()
        var policy: PlanPolicy? = null
        Regex("(?i)^(?:leave|allow|set|use)(?: a)? (.+?) buffer(?: between (?:tasks|sessions))?$").matchEntire(text)?.let { policy = plan.policy.copy(bufferMinutes = duration(it.groupValues[1])) }
        Regex("(?i)^(?:schedule (?:in|as)|use|maximum) (.+?) sessions?$").matchEntire(text)?.let { policy = plan.policy.copy(maximumSession = duration(it.groupValues[1].replace(Regex("(?i)-(minute|hour)"), " $1"))) }
        Regex("(?i)^(?:at most|maximum|work budget)(?: of)? (.+?) (?:a day|per day|daily)$").matchEntire(text)?.let { policy = plan.policy.copy(dailyMinutes = duration(it.groupValues[1])) }
        if (lower in setOf("weekdays only", "only on weekdays", "keep weekends free")) policy = plan.policy.copy(workingDays = (Calendar.MONDAY..Calendar.FRIDAY).toSet())
        if (lower in setOf("weekends only", "only on weekends")) policy = plan.policy.copy(workingDays = setOf(Calendar.SATURDAY, Calendar.SUNDAY))
        Regex("(?i)^keep (sunday|monday|tuesday|wednesday|thursday|friday|saturday)s? free$").matchEntire(text)?.let {
            val day = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday").indexOf(it.groupValues[1].lowercase()) + 1
            policy = plan.policy.copy(workingDays = plan.policy.workingDays - day)
        }
        Regex("(?i)^keep (\\d{4}-\\d{2}-\\d{2}) free$").matchEntire(text)?.let { policy = plan.policy.copy(excludedDates = plan.policy.excludedDates + it.groupValues[1]) }
        if (lower in setOf("clear scheduling constraints", "reset scheduling preferences")) policy = PlanPolicy()
        policy?.let { next ->
            next.validate()
            val cleared = plan.steps.map { step ->
                val allowed = step.workSessions.all { session -> next.allows(session, plan.timezone) }
                if (!allowed) { editable(step); step.copy(start = null, sessions = emptyList()) } else step
            }
            return publish(plan.changed(steps = cleared).copy(policy = next), "Updated scheduling constraints. ${plan.steps.count { it.start != null } - cleared.count { it.start != null }} affected step(s) are now unscheduled; other work is preserved. Give an availability window to place them again.")
        }
        if (lower in setOf("replan", "replan flexible work", "reschedule flexible work", "finish earlier")) {
            val window = plan.facts["availability"]?.let { Regex("^(\\d+):00–(\\d+):00 (today|daily.*)$").matchEntire(it) }
                ?: throw IllegalArgumentException("What hours can I use? Say ‘I'm free from 9am to 5pm daily’ first")
            val flexible = plan.steps.map { if (it.protected) it else it.copy(start = null, sessions = emptyList()) }
            val result = PlanScheduler.arrange(plan.copy(steps = flexible), now, if (window.groupValues[3] == "today") 1 else 28,
                window.groupValues[1].toInt(), window.groupValues[2].toInt(), occupied)
            return publish(plan.changed(steps = result.steps), "Replanned flexible work inside your availability, preserving protected commitments. ${result.unscheduled.size} steps remain unscheduled. Review this draft before applying.")
        }
        if (lower in setOf("what can you plan", "planning help", "what can i say")) return Turn(plan,
            "Tell me the goal, add steps or ask for suggestions. You can set dependencies, deadlines, an estimate range, earliest starts, buffers, free weekdays, daily work limits and session lengths. Add/move/remove a session by number, protect work, or ask to replan flexible work. Save shows a review; apply saves exactly that revision. Undo/redo edit the draft without changing saved tasks.")
        return null
    }
}
