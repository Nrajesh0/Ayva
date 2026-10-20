package com.focusbyrj.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

/** Canonical plan metadata. References are task IDs, independent of chat/workspace lifetime.
 * Deleting a parent never implicitly deletes its children. Unavailable references stay visible;
 * importing a partial archive explicitly marks them missing instead of binding unrelated IDs.
 * A deadline remains Task.dueDate. A work interval never creates a deadline alarm.
 */
data class TaskPlanning(
    val goal: String = "",
    val parentTaskId: Long? = null,
    val prerequisiteTaskIds: Set<Long> = emptySet(),
    val minutes: Int? = null,
    val start: Long? = null,
    val timezone: String = TimeZone.getDefault().id,
    val position: Int = 0,
    val parentMissing: Boolean = false,
    val missingPrerequisites: Int = 0,
    val sessions: List<WorkSession> = emptyList(),
    val workReminders: Boolean = true,
    val afterMinutes: Int = 0
) {
    val workSessions: List<WorkSession> get() = sessions.ifEmpty { start?.let { listOf(WorkSession("primary", it, minutes!!)) }.orEmpty() }
    val end: Long? get() = workSessions.maxOfOrNull { it.end }
    fun withSessions(values: List<WorkSession>) = copy(sessions = values.sortedBy { it.start }, start = values.minOfOrNull { it.start })
    fun validate(taskId: Long? = null) {
        require(afterMinutes in 0..10080)
        require(goal.length <= 500 && position in 0..10_000)
        require(parentTaskId == null || parentTaskId > 0 && parentTaskId != taskId)
        require(prerequisiteTaskIds.size <= 100 && prerequisiteTaskIds.all { it > 0 && it != taskId })
        require(!parentMissing || parentTaskId == null)
        require(missingPrerequisites in 0..100)
        require(minutes == null || minutes in 1..1440)
        require(timezone in availableTimezones) { "Unknown work-session time zone" }
        WorkSession.validateList(sessions)
        require(sessions.isEmpty() || start == sessions.minOf { it.start }) { "The first session must match the work start" }
        require(start == null || minutes != null) { "A work session needs a duration" }
        require(start == null || end!! > start) { "Invalid work interval" }
    }
    fun json(): String {
        validate()
        return JSONObject().put("version", 2).put("goal", goal)
            .put("parent", parentTaskId ?: JSONObject.NULL).put("prerequisites", JSONArray(prerequisiteTaskIds.sorted()))
            .put("minutes", minutes ?: JSONObject.NULL).put("start", start ?: JSONObject.NULL)
            .put("timezone", timezone).put("position", position).put("parentMissing", parentMissing)
            .put("missingPrerequisites", missingPrerequisites).put("sessions", JSONArray(sessions.map { it.json() }))
            .put("workReminders", workReminders).put("afterMinutes", afterMinutes).toString()
    }
    fun remap(taskIds: Map<Long, Long>): TaskPlanning = copy(
        parentTaskId = parentTaskId?.let(taskIds::get),
        prerequisiteTaskIds = prerequisiteTaskIds.mapNotNull(taskIds::get).toSet(),
        parentMissing = parentMissing || parentTaskId != null && parentTaskId !in taskIds,
        missingPrerequisites = (missingPrerequisites + prerequisiteTaskIds.count { it !in taskIds }).coerceAtMost(100)
    )
    companion object {
        private val availableTimezones = TimeZone.getAvailableIDs().toSet()
        fun read(raw: String): TaskPlanning? {
            if (raw.isEmpty()) return null // schema 13 and older
            require(raw.toByteArray(Charsets.UTF_8).size <= 16_000) { "Task planning data is too large" }
            val j = JSONObject(raw)
            require(j.getInt("version") in 1..2) { "Unsupported task planning version" }
            val a = j.getJSONArray("prerequisites")
            require(a.length() <= 100)
            val refs = (0 until a.length()).map { a.getLong(it) }
            require(refs.distinct().size == refs.size)
            fun long(key: String) = if (j.isNull(key)) null else j.getLong(key)
            val duration = long("minutes")
            require(duration == null || duration in 1..1440)
            return TaskPlanning(j.getString("goal"), long("parent"), refs.toSet(), duration?.toInt(), long("start"),
                j.getString("timezone"), j.getInt("position"), j.optBoolean("parentMissing"), j.optInt("missingPrerequisites"),
                WorkSession.readList(j.optJSONArray("sessions")), j.optBoolean("workReminders", false), j.optInt("afterMinutes"))
                .also { it.validate() }
        }
        fun remap(raw: String, taskIds: Map<Long, Long>): String = read(raw)?.remap(taskIds)?.json().orEmpty()

        /** Known edges must be acyclic; unavailable edges are deliberately not guessed. */
        fun validateGraph(tasks: List<Task>) {
            require(tasks.all { it.planningJson.isEmpty() || it.id > 0 }) { "Imported planned tasks need stable positive IDs" }
            val ids = tasks.map { it.id }.filter { it > 0 }
            require(ids.distinct().size == ids.size) { "Duplicate task IDs" }
            val metadata = tasks.associate { it.id to read(it.planningJson)?.also { p -> p.validate(it.id) } }
            // Iterative topological validation also handles deeply nested imported graphs.
            val edges = metadata.mapValues { (_, p) ->
                (p?.prerequisiteTaskIds.orEmpty() + listOfNotNull(p?.parentTaskId)).filter { it in metadata }.toSet()
            }
            val counts = edges.mapValues { it.value.size }.toMutableMap()
            val dependents = mutableMapOf<Long, MutableList<Long>>()
            edges.forEach { (id, refs) -> refs.forEach { dependents.getOrPut(it) { mutableListOf() }.add(id) } }
            val ready = java.util.ArrayDeque<Long>()
            counts.filterValues { it == 0 }.keys.forEach(ready::add)
            var visited = 0
            while (ready.isNotEmpty()) {
                val id = ready.removeFirst(); visited++
                dependents[id].orEmpty().forEach { dependent ->
                    val left = counts.getValue(dependent) - 1; counts[dependent] = left
                    if (left == 0) ready.add(dependent)
                }
            }
            require(visited == metadata.size) { "Task planning references create a cycle" }
        }
    }
}

/** Invalid imported metadata must never crash native task rendering. Restore codecs validate strictly. */
val Task.planning: TaskPlanning?
    get() = runCatching { TaskPlanning.read(planningJson) }.getOrNull()

/** First explicit commitment, shared by the native task list and widget. */
val Task.agendaAt: Long?
    get() = listOfNotNull(dueDate, planning?.start).minOrNull()

fun Task.isInTodayAgenda(endExclusive: Long): Boolean = agendaAt?.let { it < endExclusive } ?: (type == TaskType.TASK)
fun Task.isInUpcomingAgenda(endExclusive: Long): Boolean = type == TaskType.TASK && (agendaAt?.let { it >= endExclusive } == true)
