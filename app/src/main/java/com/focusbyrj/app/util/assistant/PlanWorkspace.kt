package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.WorkSession
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

internal enum class PlanEditField { DURATION, WORK_TIME, DEADLINE, NAME }
internal data class PlanEditFocus(val stepId: String, val field: PlanEditField) {
    fun json() = JSONObject().put("step", stepId).put("field", field.name)
    companion object {
        fun read(j: JSONObject) = PlanEditFocus(j.getString("step"), PlanEditField.valueOf(j.getString("field")))
    }
}

internal enum class PlanWorkState { READY, ACTIVE, BLOCKED, SKIPPED }

internal data class PlanStep(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val parentId: String? = null,
    val prerequisites: Set<String> = emptySet(),
    val minutes: Int? = null,
    val deadline: Long? = null,
    val start: Long? = null,
    val taskSnapshot: String? = null,
    val source: String = "user",
    val taskLinkMissing: Boolean = false,
    val sessions: List<WorkSession> = emptyList(),
    val workReminders: Boolean = true,
    val earliestStart: Long? = null,
    val protected: Boolean = false,
    val minimumChunk: Int = 15,
    val maximumEstimate: Int? = null,
    val afterMinutes: Int = 0,
    val workState: PlanWorkState = PlanWorkState.READY,
    val blockReason: String? = null
) {
    private val snapshotInfo = taskSnapshot?.let(::JSONObject)
    val completed: Boolean = snapshotInfo?.optBoolean("completed") == true
    val workItem: Boolean = snapshotInfo?.optString("type", "TASK")?.let { it == "TASK" } ?: true
    val paused: Boolean get() = workState in setOf(PlanWorkState.BLOCKED, PlanWorkState.SKIPPED)
    val workSessions: List<WorkSession> get() = sessions.ifEmpty { start?.let { at -> minutes?.let { listOf(WorkSession("primary", at, it)) } }.orEmpty() }
    val end: Long? get() = workSessions.maxOfOrNull { it.end }
    fun withSessions(values: List<WorkSession>): PlanStep = copy(sessions = values.sortedBy { it.start }, start = values.minOfOrNull { it.start })
    fun shifted(to: Long): PlanStep {
        val old = start ?: return copy(start = to)
        val delta = Math.subtractExact(to, old)
        return if (sessions.isEmpty()) copy(start = to) else withSessions(sessions.map { it.copy(start = Math.addExact(it.start, delta)) })
    }
    fun json() = JSONObject().put("id", id).put("title", title).put("parent", parentId ?: JSONObject.NULL)
        .put("prerequisites", JSONArray(prerequisites.toList())).put("minutes", minutes ?: JSONObject.NULL)
        .put("deadline", deadline ?: JSONObject.NULL).put("start", start ?: JSONObject.NULL)
        .put("task", taskSnapshot ?: JSONObject.NULL).put("source", source).put("taskLinkMissing", taskLinkMissing)
        .put("sessions", JSONArray(sessions.map { it.json() })).put("workReminders", workReminders)
        .put("earliestStart", earliestStart ?: JSONObject.NULL).put("protected", protected).put("minimumChunk", minimumChunk)
        .put("maximumEstimate", maximumEstimate ?: JSONObject.NULL).put("afterMinutes", afterMinutes)
        .apply { if (workState != PlanWorkState.READY) put("workState", workState.name); blockReason?.let { put("blockReason", it) } }
    companion object {
        fun read(j: JSONObject) = PlanStep(j.getString("id"), j.getString("title"), j.nullableString("parent"),
            j.getJSONArray("prerequisites").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
            j.nullableLong("minutes")?.toInt(), j.nullableLong("deadline"), j.nullableLong("start"), j.nullableString("task"), j.optString("source", "user"), j.optBoolean("taskLinkMissing"),
            WorkSession.readList(j.optJSONArray("sessions")), j.optBoolean("workReminders", false), j.nullableLong("earliestStart"),
            j.optBoolean("protected"), j.optInt("minimumChunk", 15), j.nullableLong("maximumEstimate")?.toInt(), j.optInt("afterMinutes"),
            PlanWorkState.valueOf(j.optString("workState", "READY")), j.nullableString("blockReason"))
    }
}

internal data class PlanWorkspace(
    val id: String = UUID.randomUUID().toString(),
    val revision: Int = 1,
    val goal: String = "",
    val domain: String = "general",
    val timezone: String = TimeZone.getDefault().id,
    val steps: List<PlanStep> = emptyList(),
    val facts: Map<String, String> = emptyMap(),
    val selected: String? = null,
    val pending: JSONObject? = null,
    val removed: List<PlanStep> = emptyList(),
    val reviewedRevision: Int? = null,
    val reviewedAt: Long = 0,
    val reviewProcess: String? = null,
    val savedRevision: Int? = null,
    val previousDraft: String? = null,
    val savedUndoId: String? = null,
    val editFocus: PlanEditFocus? = null,
    val hypotheticalView: Boolean = false,
    val policy: PlanPolicy = PlanPolicy(),
    val undoDrafts: List<String> = emptyList(),
    val redoDrafts: List<String> = emptyList(),
    val reviewDeviceTimezone: String? = null,
    val dialogue: PlanningDialogueState = PlanningDialogueState(),
    val alternative: String? = null,
    val archived: Boolean = false,
    val milestones: List<PlanMilestone> = emptyList(),
    val goalDeadline: Long? = null
) {
    fun changed(steps: List<PlanStep> = this.steps, facts: Map<String, String> = this.facts, selected: String? = this.selected,
                pending: JSONObject? = null, removed: List<PlanStep> = this.removed, goal: String = this.goal,
                editFocus: PlanEditFocus? = null, milestones: List<PlanMilestone> = this.milestones, goalDeadline: Long? = this.goalDeadline): PlanWorkspace {
        val groups = PlanMilestone.reconcile(milestones, this.steps, steps, dialogue.focusMilestone)
        val previous = this.milestones.associateBy { it.id }
        val newIds = steps.map { it.id }.toSet() - this.steps.map { it.id }.toSet()
        val constrained = steps.map { step ->
            val deadlines = groups.filter { group -> step.id in group.stepIds && (step.id in newIds || step.id !in previous[group.id]?.stepIds.orEmpty()) }.mapNotNull { it.boundDeadline } + listOfNotNull(goalDeadline.takeIf { step.id in newIds })
            val deadline = deadlines.minOrNull()
            if (deadline == null || step.completed || !step.workItem) step else {
                require(!step.protected || step.deadline != null && step.deadline <= deadline) { "A protected commitment conflicts with that goal or milestone deadline" }
                step.copy(deadline = minOf(step.deadline ?: deadline, deadline))
            }
        }
        return copy(milestones = groups, goalDeadline = goalDeadline,
            revision = revision + 1, steps = constrained, facts = facts, selected = selected, pending = pending, removed = removed,
            goal = goal, editFocus = editFocus, hypotheticalView = false, alternative = null, reviewedRevision = null, reviewedAt = 0, reviewProcess = null, reviewDeviceTimezone = null,
            previousDraft = checkpoint().takeIf { it.toByteArray().size <= 200_000 },
            undoDrafts = boundedHistory(undoDrafts + checkpoint()), redoDrafts = emptyList())
    }

    fun checkpoint(): String = copy(previousDraft = null, undoDrafts = emptyList(), redoDrafts = emptyList(),
        reviewedRevision = null, reviewedAt = 0, reviewProcess = null, reviewDeviceTimezone = null, savedUndoId = null, pending = null, hypotheticalView = false, alternative = null).json().toString()
    fun historyMove(redo: Boolean): PlanWorkspace {
        val source = if (redo) redoDrafts else undoDrafts.ifEmpty { listOfNotNull(previousDraft) }
        require(source.isNotEmpty()) { if (redo) "There is no draft edit to redo" else "There is no earlier draft edit to undo" }
        val target = read(JSONObject(source.last()))
        return target.copy(id = id, revision = revision + 1, previousDraft = null, savedRevision = null, savedUndoId = null,
            pending = null, reviewedRevision = null, reviewedAt = 0, reviewProcess = null,
            undoDrafts = if (redo) boundedHistory(undoDrafts + checkpoint()) else source.dropLast(1),
            redoDrafts = if (redo) source.dropLast(1) else boundedHistory(redoDrafts + checkpoint()))
    }

    fun validate() {
        policy.validate()
        PlanningDialogueState.read(dialogue.json())
        alternative?.let { raw ->
            require(raw.toByteArray().size <= 200_000)
            val j = JSONObject(raw)
            require(j.isNull("alternative") || !j.has("alternative")) { "Alternatives cannot contain nested alternatives" }
            require(j.getString("id") == id)
            PlanWorkspace.read(j)
        }
        require(id.length in 1..80 && revision > 0 && goal.length <= 500)
        require(timezone in TimeZone.getAvailableIDs()) { "Unknown plan time zone" }
        require(undoDrafts.size <= 20 && redoDrafts.size <= 20)
        require((undoDrafts + redoDrafts).all { it.toByteArray().size <= 200_000 })
        require(steps.size <= 100 && removed.size <= 100 && facts.size <= 30)
        require(facts.all { (key, value) -> key.length <= 60 && value.length <= 2000 })
        require(steps.sumOf { it.workSessions.size } <= 500) { "Keep each workspace under 500 work sessions" }
        val ids = steps.map { it.id }.toSet()
        require(milestones.size <= 12 && milestones.map { it.id }.distinct().size == milestones.size)
        require(milestones.map { it.title.lowercase(java.util.Locale.ROOT) }.distinct().size == milestones.size) { "Use distinct milestone names" }
        require(goalDeadline == null || goalDeadline > 0)
        milestones.forEach { group ->
            group.validate(ids)
            require(group.bindings.values.all { it in facts }) { "A milestone's shared constraint is missing" }
            require(steps.filter { it.id in group.stepIds && it.workItem && !it.completed }.all { step -> group.boundDeadline == null || step.deadline != null && step.deadline <= group.boundDeadline }) { "A step exceeds its milestone deadline; clear or change that constraint explicitly" }
        }
        require(steps.filter { it.workItem && !it.completed }.all { goalDeadline == null || it.deadline != null && it.deadline <= goalDeadline }) { "A step exceeds the whole-plan deadline" }
        dialogue.reasoning?.keys()?.asSequence()?.forEach { require(it == "whole" || milestones.any { m -> m.id == it }) { "Unknown reasoning scope" } }

        require(dialogue.focusMilestone == null || milestones.any { it.id == dialogue.focusMilestone }) { "The focused milestone is unavailable" }
        require(ids.size == steps.size)
        require(selected == null || selected in ids)
        require(editFocus == null || editFocus.stepId in ids) { "The conversational edit refers to an unavailable step" }
        val visiting = mutableSetOf<String>(); val visited = mutableSetOf<String>()
        fun visit(id: String) {
            require(visiting.add(id)) { "Those dependencies would create a cycle" }
            val step = steps.first { it.id == id }
            (step.prerequisites + listOfNotNull(step.parentId)).forEach { dependency ->
                require(dependency in ids && dependency != id) { "Unknown or self-referencing step" }
                if (dependency !in visited) visit(dependency)
            }
            visiting.remove(id); visited.add(id)
        }
        steps.forEach {
            require(it.id.length in 1..80 && it.title.isNotBlank() && it.title.length <= 200)
            require(it.minutes == null || it.minutes in 1..1440) { "Use a duration between 1 minute and 24 hours" }
            require(it.blockReason == null || it.workState == PlanWorkState.BLOCKED && it.blockReason.length in 1..500) { "Invalid draft blocker" }
            require(!it.paused || it.completed || it.workSessions.isEmpty()) { "Unblock or resume paused work before assigning a work time" }
            WorkSession.validateList(it.sessions)
            require(it.sessions.isEmpty() || it.start == it.sessions.minOf { session -> session.start }) { "The first session must match the work start" }
            require(it.minimumChunk in 1..1440 && it.afterMinutes in 0..10080)
            require(it.maximumEstimate == null || it.minutes != null && it.maximumEstimate in it.minutes..1440)
            require(it.start == null || it.earliestStart == null || it.start >= it.earliestStart) { "The work begins before its earliest allowed start" }
            require(it.start == null || it.minutes != null) { "A scheduled session needs a duration" }
            require(it.start == null || it.end!! > it.start) { "The work interval exceeds the supported time range" }
            require(it.completed || !it.workItem || it.workSessions.all { session -> policy.allows(session, timezone) }) { "Work falls on an excluded day" }
            require(it.end == null || it.deadline == null || it.end!! <= it.deadline) { "The work session finishes after its deadline" }
            if (it.id !in visited) visit(it.id)
        }
        val orderedSessions = steps.filterNot { it.completed }.flatMap { it.workSessions }.sortedBy { it.start }
        if (policy.bufferMinutes > 0) orderedSessions.zipWithNext().forEach { (a, b) ->
            require(a.end + policy.bufferMinutes * 60_000L <= b.start) { "Work sessions need the requested ${policy.bufferMinutes}-minute buffer" }
        }
        steps.filter { it.start != null && !it.completed }.forEach { step ->
            require(steps.none { it.id != step.id && overlaps(step, it) }) { "Those work sessions overlap" }
            step.prerequisites.forEach { id ->
                val prerequisite = steps.first { it.id == id }
                require(prerequisite.completed || prerequisite.end != null && prerequisite.end!! + step.afterMinutes * 60_000L <= step.start!!) { "Schedule the prerequisite before this step" }
            }
        }
    }

    fun json() = JSONObject().put("version", 2).put("id", id).put("revision", revision).put("goal", goal)
        .put("domain", domain).put("timezone", timezone).put("steps", JSONArray(steps.map { it.json() }))
        .put("facts", JSONObject(facts)).put("selected", selected ?: JSONObject.NULL).put("pending", pending ?: JSONObject.NULL)
        .put("removed", JSONArray(removed.map { it.json() })).put("reviewedRevision", reviewedRevision ?: JSONObject.NULL)
        .put("reviewedAt", reviewedAt).put("process", reviewProcess ?: JSONObject.NULL).put("savedRevision", savedRevision ?: JSONObject.NULL)
        .put("previousDraft", previousDraft ?: JSONObject.NULL).put("savedUndo", savedUndoId ?: JSONObject.NULL)
        .put("editFocus", editFocus?.json() ?: JSONObject.NULL)
        .put("hypotheticalView", hypotheticalView).put("policy", policy.json())
        .put("undoDrafts", JSONArray(undoDrafts)).put("redoDrafts", JSONArray(redoDrafts)).put("reviewDeviceTimezone", reviewDeviceTimezone ?: JSONObject.NULL).put("dialogue", dialogue.json()).put("alternative", alternative ?: JSONObject.NULL).put("archived", archived).apply { if (milestones.isNotEmpty()) put("milestones", JSONArray(milestones.map { it.json() })); goalDeadline?.let { put("goalDeadline", it) } }

    companion object {
        private fun history(j: JSONObject, key: String): List<String> {
            val a = j.optJSONArray(key) ?: return emptyList()
            require(a.length() <= 20)
            return (0 until a.length()).map { a.getString(it).also { raw ->
                require(raw.toByteArray().size <= 200_000)
                val checkpoint = JSONObject(raw)
                require((checkpoint.optJSONArray("undoDrafts")?.length() ?: 0) == 0 && (checkpoint.optJSONArray("redoDrafts")?.length() ?: 0) == 0)
                require(checkpoint.nullableString("previousDraft") == null) { "History cannot contain nested history" }
            } }
        }
        fun boundedHistory(values: List<String>): List<String> {
            val result = values.takeLast(20).filter { it.toByteArray().size <= 200_000 }.toMutableList()
            while (result.sumOf { it.toByteArray().size } > 400_000) result.removeAt(0)
            return result
        }
        fun read(j: JSONObject): PlanWorkspace {
            require(j.getInt("version") in 1..2)
            fun list(key: String) = j.getJSONArray(key).let { a -> require(a.length() <= 100); (0 until a.length()).map { PlanStep.read(a.getJSONObject(it)) } }
            val f = j.getJSONObject("facts")
            return PlanWorkspace(j.getString("id"), j.getInt("revision"), j.getString("goal"), j.getString("domain"), j.getString("timezone"),
                list("steps"), f.keys().asSequence().associateWith { f.getString(it) }, j.nullableString("selected"), j.optJSONObject("pending"),
                list("removed"), j.nullableLong("reviewedRevision")?.toInt(), j.optLong("reviewedAt"), j.nullableString("process"), j.nullableLong("savedRevision")?.toInt(), j.nullableString("previousDraft"), j.nullableString("savedUndo"),
                j.optJSONObject("editFocus")?.let(PlanEditFocus::read), j.optBoolean("hypotheticalView"), PlanPolicy.read(j.optJSONObject("policy")), history(j, "undoDrafts"), history(j, "redoDrafts"), j.nullableString("reviewDeviceTimezone"), PlanningDialogueState.read(j.optJSONObject("dialogue")), j.nullableString("alternative"), j.optBoolean("archived"), PlanMilestone.readList(j.optJSONArray("milestones")), j.nullableLong("goalDeadline")).also { it.validate() }
        }
        fun overlaps(a: PlanStep, b: PlanStep) = conflicts(a, b)
        fun conflicts(a: PlanStep, b: PlanStep, bufferMinutes: Int = 0) = !a.completed && !b.completed && a.workSessions.any { x ->
            b.workSessions.any { y -> x.start < y.end + bufferMinutes * 60_000L && y.start < x.end + bufferMinutes * 60_000L }
        }
    }
}

internal fun JSONObject.nullableString(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
internal fun JSONObject.nullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)

/** Explicit intervals only: task deadlines are never treated as occupied appointments. */
internal object PlanScheduler {
    data class Result(val steps: List<PlanStep>, val unscheduled: List<String>, val reasons: Map<String, String> = emptyMap())
    fun arrange(plan: PlanWorkspace, from: Long, days: Int = 28, startHour: Int, endHour: Int,
                occupied: List<PlanStep> = emptyList(), targets: Set<String>? = null): Result {
        require(startHour in 0..23 && endHour in 1..24 && startHour < endHour && days in 1..90)
        plan.validate()
        val placed = plan.steps.filter { it.start != null && !it.completed }.toMutableList()
        val remaining = plan.steps.filter { it.start == null && it.workItem && !it.completed && (targets == null || it.id in targets) }.toMutableList()
        val reasons = mutableMapOf<String, String>()
        val zone = TimeZone.getTimeZone(plan.timezone)
        val buffer = plan.policy.bufferMinutes * 60_000L
        for (pass in plan.steps.indices) {
            var progress = false
            remaining.toList().forEach { step ->
                if (step.paused) { reasons[step.id] = if (step.workState == PlanWorkState.BLOCKED) "blocked: ${step.blockReason ?: "waiting for a prerequisite or input"}" else "skipped for now"; return@forEach }
                if (step.minutes == null) { reasons[step.id] = "duration is missing"; return@forEach }
                if (step.protected) { reasons[step.id] = "protected without a work time; unlock it to schedule"; return@forEach }
                if (step.prerequisites.any { id -> plan.steps.first { it.id == id }.let { !it.completed } && placed.none { it.id == id && it.end != null } }) {
                    reasons[step.id] = "a prerequisite has no completed schedule"; return@forEach
                }
                val earliest = maxOf(from, step.earliestStart ?: from,
                    step.prerequisites.mapNotNull { id -> placed.find { it.id == id }?.end?.plus(step.afterMinutes * 60_000L) }.maxOrNull() ?: from)
                var need = step.maximumEstimate ?: step.minutes
                val sessions = mutableListOf<WorkSession>()
                val date = Calendar.getInstance(zone).apply { timeInMillis = from }
                for (day in 0 until days) {
                    if (!plan.policy.allows(date)) { date.add(Calendar.DAY_OF_YEAR, 1); continue }
                    val floor = (date.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, startHour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); isLenient = false }
                    val ceiling = (date.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, if (endHour == 24) 0 else endHour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        if (endHour == 24) add(Calendar.DAY_OF_YEAR, 1)
                        isLenient = false
                    }
                    val opening = PlanClock.boundary(floor)
                    val closing = PlanClock.boundary(ceiling)
                    if (opening == null || closing == null) { date.add(Calendar.DAY_OF_YEAR, 1); continue }
                    val limit = minOf(closing, step.deadline ?: closing)
                    var at = ((maxOf(earliest, opening) + 59_999L) / 60_000L) * 60_000L
                    val occupiedSessions = (placed + occupied).flatMap { it.workSessions }.sortedBy { it.start }
                    val midnight = (date.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                    val tomorrow = (date.clone() as Calendar).apply { timeInMillis = midnight; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                    var dailyAvailable = plan.policy.budget(date)?.minus((occupiedSessions + sessions).sumOf { ((minOf(it.end, tomorrow) - maxOf(it.start, midnight)).coerceAtLeast(0) / 60_000L).toInt() }) ?: 1440
                    while (need > 0 && at < limit && sessions.size < 100) {
                        val blocking = occupiedSessions.firstOrNull { it.end + buffer > at && it.start - buffer < limit }
                        if (blocking != null && blocking.start - buffer <= at) { at = blocking.end + buffer; continue }
                        val gapEnd = minOf(limit, blocking?.let { it.start - buffer } ?: limit)
                        val free = ((gapEnd - at) / 60_000L).toInt().coerceAtLeast(0)
                        val maximum = plan.policy.maximumSession ?: need
                        val chunk = minOf(need, maximum, free, dailyAvailable)
                        val split = plan.policy.maximumSession != null
                        if (chunk <= 0 || !split && chunk < need || split && chunk < minOf(need, step.minimumChunk)) {
                            if (blocking == null) break
                            at = blocking.end + buffer; continue
                        }
                        val id = UUID.nameUUIDFromBytes("${step.id}:$at:$chunk".toByteArray(Charsets.UTF_8)).toString()
                        sessions.add(WorkSession(id, at, chunk)); need -= chunk; dailyAvailable -= chunk
                        at += chunk * 60_000L + buffer
                    }
                    if (need == 0) break
                    date.add(Calendar.DAY_OF_YEAR, 1)
                }
                if (need == 0 && placed.sumOf { it.workSessions.size } + sessions.size <= 500) {
                    val result = if (sessions.size == 1 && step.maximumEstimate == null && plan.policy.maximumSession == null)
                        step.copy(start = sessions.single().start) else step.withSessions(sessions)
                    placed.add(result); remaining.remove(step); reasons.remove(step.id); progress = true
                } else reasons[step.id] = "${step.maximumEstimate ?: step.minutes} minutes cannot fit the allowed windows, existing work, buffers and deadline; $need minutes remain"
            }
            if (!progress) break
        }
        val result = plan.steps.map { original -> placed.find { it.id == original.id } ?: original }
        plan.copy(steps = result).validate()
        return Result(result, remaining.map { it.id }, reasons)
    }
}
