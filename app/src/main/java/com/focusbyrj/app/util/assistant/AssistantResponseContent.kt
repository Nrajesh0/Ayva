package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.AyvaTalkEngine
import org.json.JSONArray
import org.json.JSONObject

/** Bounded display data. Commands are constructed by the UI and revalidated by the coordinator. */
object AssistantResponseContent {
    data class TaskRow(val id: Long, val title: String, val detail: String, val completed: Boolean)
    data class TaskList(val rows: List<TaskRow>, val total: Int, val at: Long)
    private fun attach(response: AyvaTalkEngine.TalkResponse, key: String, value: JSONObject): AyvaTalkEngine.TalkResponse {
        val root = JSONObject(response.jsonPayload ?: "{}")
        val display = root.optJSONObject("presentation") ?: JSONObject().put("version", 1)
        return response.copy(jsonPayload = root.put("presentation", display.put(key, value)).toString())
    }
    fun setting(response: AyvaTalkEngine.TalkResponse, key: String) = attach(response, "setting", JSONObject().put("key", key))
    fun taskList(response: AyvaTalkEngine.TalkResponse, tasks: List<Task>, at: Long) = attach(response, "taskList",
        JSONObject().put("at", at).put("total", tasks.size).put("items", JSONArray(tasks.take(20).map {
            JSONObject().put("id", it.id).put("title", it.title).put("detail", AssistantPresentation.due(it.dueDate) + if (it.isPriority) " · priority" else "").put("completed", it.isCompleted)
        })))
    fun tasks(payload: String?): TaskList? = runCatching {
        val list = AssistantPresentation.data(payload)?.optJSONObject("taskList") ?: return null
        val items = list.getJSONArray("items"); require(items.length() in 1..20)
        val rows = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index); val id = item.getLong("id"); require(id > 0)
            TaskRow(id, item.getString("title").take(500), item.optString("detail").take(500), item.optBoolean("completed"))
        }
        require(rows.map { it.id }.distinct().size == rows.size)
        TaskList(rows, list.optInt("total", rows.size).coerceAtLeast(rows.size), list.getLong("at"))
    }.getOrNull()
    fun settingSpec(payload: String?) = AssistantPresentation.data(payload)?.optJSONObject("setting")?.optString("key")?.let { key -> runCatching { AssistantCapabilities.setting(key).spec }.getOrNull() }
}
