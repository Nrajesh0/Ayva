package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.Subtask
import com.focusbyrj.app.data.Task
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PlanningItem(val id: String = UUID.randomUUID().toString(), val title: String, val source: String = "user", val dueDate: Long? = null, val inheritDeadline: Boolean = true) {
    fun json() = JSONObject().put("id", id).put("title", title).put("source", source).put("due", dueDate ?: JSONObject.NULL).put("inheritDeadline", inheritDeadline)
}

/** No executable text, private-data references or implicit calendar availability. */
data class PlanningDraft(
    val id: String = UUID.randomUUID().toString(),
    val revision: Int = 1,
    val at: Long = System.currentTimeMillis(),
    val domain: String,
    val question: String = "items",
    val items: List<PlanningItem> = emptyList(),
    val deadline: Long? = null,
    val budget: Int? = null,
    val effort: Int? = null,
    val checklist: Boolean = false,
    val nonce: String? = null,
    val reviewedAt: Long = 0,
    val timezone: String = java.util.TimeZone.getDefault().id,
    val paused: Boolean = false,
    val saved: Boolean = false,
    val title: String? = null,
    val goal: String? = null
) {
    fun json() = JSONObject().put("version", 1).put("id", id).put("revision", revision).put("at", at).put("domain", domain)
        .put("question", question).put("items", JSONArray(items.map { it.json() })).put("deadline", deadline ?: JSONObject.NULL)
        .put("budget", budget ?: JSONObject.NULL).put("effort", effort ?: JSONObject.NULL).put("checklist", checklist)
        .put("nonce", nonce ?: JSONObject.NULL).put("reviewedAt", reviewedAt).put("timezone", timezone).put("paused", paused).put("saved", saved).put("title", title ?: JSONObject.NULL).put("goal", goal ?: JSONObject.NULL)
    fun tasks(): List<Task> {
        require(!checklist || items.none { !it.inheritDeadline && it.dueDate != deadline }) { "Checklist items cannot have independent deadlines. Choose separate tasks" }
        return if (checklist) listOf(Task(details = goal.orEmpty(), title = title ?: when (domain) { "exam" -> "Exam preparation"; "trip" -> "Trip preparation"; else -> "Day plan" }, dueDate = deadline,
            subtasksJson = Subtask.listToJson(items.map { Subtask(id = it.id, title = it.title) })))
        else items.map { Task(title = it.title, details = goal.orEmpty(), dueDate = if (it.inheritDeadline) deadline else it.dueDate) }
    }
    companion object {
        fun read(j: JSONObject): PlanningDraft {
            require(j.getInt("version") == 1)
            val a = j.getJSONArray("items"); require(a.length() <= 20)
            val items = (0 until a.length()).map { i -> val item = a.getJSONObject(i); PlanningItem(item.getString("id"), item.getString("title"), item.getString("source"), item.optLong("due").takeUnless { item.isNull("due") }, item.optBoolean("inheritDeadline", true)) }
            require(items.all { it.title.isNotBlank() && it.title.length <= 200 && it.id.length <= 80 && it.source in setOf("user", "optional template") })
            require(items.map { it.id }.distinct().size == items.size)
            return PlanningDraft(j.getString("id").also { require(it.length in 1..80) }, j.getInt("revision").also { require(it > 0) }, j.getLong("at"),
                j.getString("domain").also { require(it in setOf("day", "exam", "trip")) }, j.getString("question").also { require(it in setOf("items", "deadline", "budget", "review")) }, items,
                j.optLong("deadline").takeUnless { j.isNull("deadline") }, j.optInt("budget").takeUnless { j.isNull("budget") }?.also { require(it in 1..480) },
                j.optInt("effort").takeUnless { j.isNull("effort") }?.also { require(it in 1..10080) }, j.optBoolean("checklist"),
                j.optString("nonce").takeUnless { it == "null" || it.isBlank() }, j.optLong("reviewedAt"), j.getString("timezone"), j.optBoolean("paused"), j.optBoolean("saved"), j.optString("title").takeUnless { it == "null" || it.isBlank() }?.also { require(it.length <= 200) }, j.optString("goal").takeUnless { it == "null" || it.isBlank() }?.also { require(it.length <= 500) })
        }
    }
}
