package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.util.AyvaTalkEngine
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** Inert display snapshots stored in the existing encrypted transcript payload, never action authority. */
data class AssistantItemReference(val kind: String, val id: Long, val title: String, val detail: String = "") {
    fun toJson() = JSONObject().put("kind", kind).put("id", id).put("title", title).put("detail", detail)
    companion object {
        fun fromJson(json: JSONObject?): AssistantItemReference? = runCatching {
            json ?: return null
            val kind = json.getString("kind"); require(kind in setOf("task", "habit"))
            AssistantItemReference(kind, json.getLong("id"), json.getString("title").take(500), json.optString("detail").take(500))
        }.getOrNull()
    }
}

data class AssistantChatState(
    val reference: AssistantItemReference? = null,
    val confirmationId: String? = null,
    val undoId: String? = null,
    val expiresAt: Long = 0L
) {
    fun allows(query: String): Boolean {
        if (!query.startsWith("/ayva ")) return true
        if (System.currentTimeMillis() >= expiresAt) return false
        val parts = query.split(Regex("\\s+"))
        return when (parts.getOrNull(1)) {
            "confirm", "choose", "cancel" -> confirmationId != null && parts.getOrNull(2) == confirmationId
            "undo" -> undoId != null && parts.getOrNull(2) == undoId
            else -> false
        }
    }
}

object AssistantPresentation {
    fun data(payload: String?): JSONObject? = runCatching {
        if (payload == null || payload.length > 1_000_000) return null
        JSONObject(payload).optJSONObject("presentation")?.takeIf { it.optInt("version") == 1 }
    }.getOrNull()
    fun actionLabel(payload: String?, query: String): String? = runCatching {
        val actions = JSONObject(payload ?: return null).optJSONArray("actions") ?: return null
        (0 until actions.length()).map { actions.getJSONObject(it) }
            .firstOrNull { it.optString("type") == "ask_query" && it.optString("query") == query }
            ?.optString("label")?.takeIf { it.isNotBlank() }?.take(200)
    }.getOrNull()

    fun reference(payload: String?) = AssistantItemReference.fromJson(data(payload)?.optJSONObject("reference"))
    fun replyReference(payload: String?) = data(payload)?.optJSONObject("reply")
    fun replyTarget(payload: String?) = AssistantItemReference.fromJson(data(payload)?.optJSONObject("replyTarget")) ?: reference(payload)
    fun withReplyTarget(response: AyvaTalkEngine.TalkResponse, reference: AssistantItemReference?): AyvaTalkEngine.TalkResponse {
        if (reference == null) return response
        val root = runCatching { JSONObject(response.jsonPayload ?: "{}") }.getOrDefault(JSONObject())
        val display = root.optJSONObject("presentation") ?: JSONObject().put("version", 1)
        return response.copy(jsonPayload = root.put("presentation", display.put("replyTarget", reference.toJson())).toString())
    }
    fun withReply(message: String, reference: AssistantItemReference?, messageId: String): String = JSONObject().put("presentation",
        JSONObject().put("version", 1).put("reply", JSONObject().put("text", message.take(1000)).put("messageId", messageId)
            .apply { reference?.let { put("reference", it.toJson()) } })).toString()
    fun attach(response: AyvaTalkEngine.TalkResponse, reference: AssistantItemReference? = null, receipt: JSONObject? = null): AyvaTalkEngine.TalkResponse {
        if (reference == null && receipt == null) return response
        val root = runCatching { JSONObject(response.jsonPayload ?: "{}") }.getOrDefault(JSONObject())
        val display = root.optJSONObject("presentation") ?: JSONObject().put("version", 1)
        reference?.let { display.put("reference", it.toJson()) }
        receipt?.let { display.put("receipt", it) }
        return response.copy(jsonPayload = root.put("presentation", display).toString())
    }
    fun due(timestamp: Long?): String = timestamp?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) } ?: "No due date"
    fun taskReceipt(records: List<JSONObject>, partial: Boolean): JSONObject? {
        val items = records.filter { it.optString("kind") == "task" }.mapNotNull { record -> runCatching {
            val after = TaskJson.decode(record.getJSONObject("after"))
            val before = record.optJSONObject("before")?.let(TaskJson::decode)
            val result = buildList {
                add(when { after.isTrashed -> "Moved to trash"; after.isCompleted -> "Completed"; before?.isCompleted == true -> "Reopened · ${due(after.dueDate)}"; else -> due(after.dueDate) })
                if ((before == null && after.isPriority) || (before != null && before.isPriority != after.isPriority)) add(if (after.isPriority) "High priority" else "Normal priority")
                if ((before == null && after.isPersistent) || (before != null && before.isPersistent != after.isPersistent)) add(if (after.isPersistent) "Persistent reminder" else "Standard reminder")
                if (before != null && before.recurrence != after.recurrence) add("Repeats: ${after.recurrence.name.lowercase().replace('_', ' ')}")
                if (before != null && before.details != after.details) add("Details updated")
            }.joinToString(" · ")
            val previous = when {
                before == null -> null
                before.dueDate != after.dueDate -> due(before.dueDate)
                before.title != after.title -> before.title
                before.isPriority != after.isPriority -> if (before.isPriority) "High priority" else "Normal priority"
                before.isPersistent != after.isPersistent -> if (before.isPersistent) "Persistent reminder" else "Standard reminder"
                else -> null
            }
            JSONObject().put("title", after.title).put("detail", result).put("reference", AssistantItemReference("task", after.id, after.title, due(after.dueDate)).toJson())
                .apply { previous?.let { put("previous", it) } }
        }.getOrNull() }
        return items.takeIf { it.isNotEmpty() }?.let { JSONObject().put("status", if (partial) "partial" else "saved").put("items", JSONArray(it)) }
    }
}
