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
                "template" -> { require(j.optString("details").length <= 10_000); val a = j.getJSONArray("steps"); require(a.length() <= 100); for (i in 0 until a.length()) require(a.getString(i).length in 1..500) }
                "chain" -> { val a = j.getJSONArray("habits"); require(a.length() in 1..100); val ids = (0 until a.length()).map { a.getLong(it) }; require(ids.all { it > 0 } && ids.distinct().size == ids.size) }
                else -> error("Unknown library item")
            }
        }
        fun template(task: Task): JSONObject = JSONObject().put("kind", "template").put("name", task.title)
            .put("details", task.details).put("steps", JSONArray(task.subtasks.map { it.title })).put("requireSubtasks", task.planning?.requireSubtasks == true).put("priority", task.isPriority)
        fun instantiate(j: JSONObject): Task {
            validate(j); require(j.getString("kind") == "template")
            val steps = j.getJSONArray("steps")
            return Task(title = j.getString("name"), details = j.optString("details"), isPriority = j.optBoolean("priority"),
                subtasksJson = Subtask.listToJson((0 until steps.length()).map { Subtask(title = steps.getString(it)) }),
                planningJson = if (j.optBoolean("requireSubtasks")) TaskPlanning(requireSubtasks = true).json() else "")
        }
        fun builtIns(): List<JSONObject> = listOf(
            "Trip checklist" to listOf("Choose dates and budget", "Book transport", "Arrange accommodation", "Check documents", "Pack essentials"),
            "Exam preparation" to listOf("List topics", "Review weak areas", "Practice questions", "Take a mock test", "Prepare exam essentials"),
            "Weekly reset" to listOf("Review unfinished tasks", "Choose priorities", "Plan work sessions", "Make time for rest")
        ).map { (name, steps) -> template(Task(title = name, subtasksJson = Subtask.listToJson(steps.map { Subtask(title = it) }))) }
        /** A merged backup never binds a routine to an unrelated local habit ID. */
        fun remapChain(raw: String, mapping: Map<Long, Long>): String? {
            val j = JSONObject(raw); validate(j)
            if (j.getString("kind") != "chain") return raw
            val a = j.getJSONArray("habits"); val ids = (0 until a.length()).mapNotNull { mapping[a.getLong(it)] }
            return if (ids.isEmpty()) null else j.put("habits", JSONArray(ids)).toString()
        }
    }
}
