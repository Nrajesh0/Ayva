package com.focusbyrj.app.data

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

/** Portable user-authored structures. No Notes content or live completion state is stored. */
class ProductivityLibrary(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun entries(kind: String): List<Pair<String, JSONObject>> = prefs.all.entries.filter { it.key.startsWith("${kind}_") }
        .mapNotNull { (id, raw) -> runCatching { id to JSONObject(raw as String).also(::validate) }.getOrNull() }.sortedBy { it.second.getString("name") }
    fun put(kind: String, value: JSONObject, id: String = "${kind}_${UUID.randomUUID()}"): String {
        require(id.startsWith("${kind}_")); validate(value); require(kind == value.getString("kind"))
        check(prefs.edit().putString(id, value.toString()).commit()) { "Couldn't save; please retry" }; return id
    }
    fun delete(id: String) { check(prefs.edit().remove(id).commit()) { "Couldn't delete" } }
    companion object {
        const val PREFS = "productivity_library"
        fun validate(j: JSONObject) {
            require(j.toString().length <= 40_000)
            require(j.getString("name").isNotBlank() && j.getString("name").length <= 500)
            when (j.getString("kind")) {
                "template" -> { require(j.optString("details").length <= 10_000); val a = j.getJSONArray("steps"); require(a.length() <= 100); for (i in 0 until a.length()) require(a.getString(i).length in 1..500)
                    require(j.optInt("templateVersion", 1) in 1..2)
                    if (j.optInt("templateVersion", 1) == 2) {
                        TaskType.valueOf(j.getString("type")); RecurrencePattern.valueOf(j.getString("recurrence")); j.getBoolean("persistent")
                        val planning = TaskPlanning.read(j.getString("planningJson"))
                        java.time.ZoneId.of(j.getString("timezone")); require(j.getLong("savedAt") > 0)
                        if (!j.isNull("dueDate")) {
                            val due = j.getLong("dueDate"); require(due > 0)
                            require(planning?.availableFrom == null || planning.availableFrom <= due)
                            require(planning?.end == null || planning.end!! <= due)
                        }
                    }
                }
                "chain" -> { val a = j.getJSONArray("habits"); require(a.length() in 1..100); val ids = (0 until a.length()).map { a.getLong(it) }; require(ids.all { it > 0 } && ids.distinct().size == ids.size) }
                else -> error("Unknown library item")
            }
        }
        fun template(task: Task, now: Long = System.currentTimeMillis()): JSONObject = JSONObject().put("kind", "template").put("name", task.title)
            .put("details", task.details).put("steps", JSONArray(task.subtasks.map { it.title }))
            .put("requireSubtasks", task.planning?.requireSubtasks == true).put("priority", task.isPriority)
            .put("templateVersion", 2).put("type", task.type.name).put("recurrence", task.recurrence.name)
            .put("persistent", task.isPersistent).put("dueDate", task.dueDate ?: JSONObject.NULL)
            .put("planningJson", task.planningJson).put("savedAt", now)
            .put("timezone", java.util.TimeZone.getTimeZone(task.planning?.timezone ?: java.util.TimeZone.getDefault().id).toZoneId().id)

        fun instantiate(j: JSONObject, now: Long = System.currentTimeMillis()): Task {
            validate(j); require(j.getString("kind") == "template")
            val steps = j.getJSONArray("steps")
            val modern = j.optInt("templateVersion", 1) == 2
            var planning = if (modern) TaskPlanning.read(j.getString("planningJson")) else
                if (j.optBoolean("requireSubtasks")) TaskPlanning(requireSubtasks = true) else null
            val zone = java.time.ZoneId.of(if (modern) j.getString("timezone") else java.util.TimeZone.getDefault().id)
            val days = if (modern && TaskType.valueOf(j.getString("type")) == TaskType.TASK) java.time.temporal.ChronoUnit.DAYS.between(
                java.time.Instant.ofEpochMilli(j.getLong("savedAt")).atZone(zone).toLocalDate(),
                java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) else 0L
            fun shift(at: Long?): Long? = at?.let {
                val local = java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().plusDays(days)
                require(zone.rules.getValidOffsets(local).size == 1) { "A template time falls in a clock change. Review the template on a different date." }
                local.atZone(zone).toInstant().toEpochMilli()
            }
            planning = planning?.let { p -> p.copy(availableFrom = shift(p.availableFrom), start = shift(p.start),
                sessions = p.sessions.map { it.copy(id = UUID.randomUUID().toString(), start = shift(it.start)!!) }) }
            return Task(title = j.getString("name"), details = j.optString("details"), isPriority = j.optBoolean("priority"),
                type = if (modern) TaskType.valueOf(j.getString("type")) else TaskType.TASK,
                recurrence = if (modern) RecurrencePattern.valueOf(j.getString("recurrence")) else RecurrencePattern.NONE,
                isPersistent = modern && j.getBoolean("persistent"),
                dueDate = if (modern && !j.isNull("dueDate")) shift(j.getLong("dueDate")) else null,
                subtasksJson = Subtask.listToJson((0 until steps.length()).map { Subtask(title = steps.getString(it)) }),
                planningJson = planning?.json().orEmpty())
        }
        fun builtIns(): List<JSONObject> = listOf(
            "Trip checklist" to listOf("Choose dates and budget", "Book transport", "Arrange accommodation", "Check documents", "Pack essentials"),
            "Exam preparation" to listOf("List topics", "Review weak areas", "Practice questions", "Take a mock test", "Prepare exam essentials"),
            "Weekly reset" to listOf("Review unfinished tasks", "Choose priorities", "Plan work sessions", "Make time for rest")
        ).map { (name, steps) -> template(Task(title = name, subtasksJson = Subtask.listToJson(steps.map { Subtask(title = it) }))) }
        /** A merged backup never binds a routine to an unrelated local habit ID. */
        fun remapChain(raw: String, mapping: Map<Long, Long>, taskMapping: Map<Long, Long> = emptyMap()): String? {
            val j = JSONObject(raw); validate(j)
            if (j.getString("kind") == "template") {
                if (j.has("planningJson")) j.put("planningJson", TaskPlanning.remap(j.getString("planningJson"), taskMapping))
                return j.toString()
            }
            val a = j.getJSONArray("habits"); val ids = (0 until a.length()).mapNotNull { mapping[a.getLong(it)] }
            return if (ids.isEmpty()) null else j.put("habits", JSONArray(ids)).toString()
        }
    }
}
