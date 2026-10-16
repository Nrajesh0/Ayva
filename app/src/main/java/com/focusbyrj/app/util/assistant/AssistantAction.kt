package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.data.TaskType
import org.json.JSONArray
import org.json.JSONObject

enum class TaskField { TITLE, DUE, PRIORITY, PERSISTENT, RECURRENCE, DETAILS, REOPEN }

sealed class AssistantAction {
    data class CreateTask(val draft: Task) : AssistantAction()
    data class EditTasks(val ids: List<Long>, val field: TaskField, val value: String) : AssistantAction()
    data class CompleteTasks(val ids: List<Long>) : AssistantAction()
    data class TrashTasks(val ids: List<Long>) : AssistantAction()
    data class CreateHabit(val title: String, val target: Int = 1) : AssistantAction()
    data class LogHabit(val id: Long, val count: Int = 1) : AssistantAction()
    data class SetRoutine(val id: Int, val enabled: Boolean) : AssistantAction()
    data class SetPreference(val key: String, val type: String, val value: String) : AssistantAction()
    data class StartFocus(val minutes: Int) : AssistantAction()
    data object StopFocus : AssistantAction()

    fun toJson(): JSONObject = JSONObject().apply {
        when (val action = this@AssistantAction) {
            is CreateTask -> { put("kind", "create"); put("task", TaskJson.encode(action.draft)) }
            is EditTasks -> { put("kind", "edit"); put("ids", JSONArray(action.ids)); put("field", action.field.name); put("value", action.value) }
            is CompleteTasks -> { put("kind", "complete"); put("ids", JSONArray(action.ids)) }
            is TrashTasks -> { put("kind", "trash"); put("ids", JSONArray(action.ids)) }
            is CreateHabit -> { put("kind", "create_habit"); put("title", action.title); put("target", action.target) }
            is LogHabit -> { put("kind", "log_habit"); put("id", action.id); put("count", action.count) }
            is SetRoutine -> { put("kind", "routine"); put("id", action.id); put("enabled", action.enabled) }
            is SetPreference -> { put("kind", "pref"); put("key", action.key); put("type", action.type); put("value", action.value) }
            is StartFocus -> { put("kind", "focus"); put("minutes", action.minutes) }
            StopFocus -> put("kind", "stop_focus")
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AssistantAction = when (json.getString("kind")) {
            "create" -> CreateTask(TaskJson.decode(json.getJSONObject("task")))
            "edit" -> EditTasks(json.getJSONArray("ids").longs(), TaskField.valueOf(json.getString("field")), json.getString("value"))
            "complete" -> CompleteTasks(json.getJSONArray("ids").longs())
            "trash" -> TrashTasks(json.getJSONArray("ids").longs())
            "create_habit" -> CreateHabit(json.getString("title"), json.getInt("target"))
            "log_habit" -> LogHabit(json.getLong("id"), json.getInt("count"))
            "routine" -> SetRoutine(json.getInt("id"), json.getBoolean("enabled"))
            "pref" -> SetPreference(json.getString("key"), json.getString("type"), json.getString("value"))
            "focus" -> StartFocus(json.getInt("minutes"))
            "stop_focus" -> StopFocus
            else -> error("Unknown assistant action")
        }
    }
}

internal fun JSONArray.longs(): List<Long> = (0 until length()).map { getLong(it) }

/** Preserve every task field when saving a pending draft or reversible edit. */
internal object TaskJson {
    fun encode(task: Task): JSONObject = JSONObject().apply {
        put("id", task.id); put("title", task.title); put("details", task.details)
        put("due", task.dueDate ?: JSONObject.NULL); put("completed", task.isCompleted)
        put("type", task.type.name); put("recurrence", task.recurrence.name)
        put("persistent", task.isPersistent); put("priority", task.isPriority)
        put("completedAt", task.completedAt ?: JSONObject.NULL); put("updatedAt", task.updatedAt)
        put("trashed", task.isTrashed); put("trashedAt", task.trashedAt ?: JSONObject.NULL)
        put("deletedAt", task.deletedAt ?: JSONObject.NULL); put("subtasks", task.subtasksJson)
    }
    fun decode(json: JSONObject): Task = Task(
        id = json.getLong("id"), title = json.getString("title"), details = json.getString("details"),
        dueDate = json.nullableLong("due"), isCompleted = json.getBoolean("completed"),
        type = TaskType.valueOf(json.getString("type")), recurrence = RecurrencePattern.valueOf(json.getString("recurrence")),
        isPersistent = json.getBoolean("persistent"), isPriority = json.getBoolean("priority"),
        completedAt = json.nullableLong("completedAt"), updatedAt = json.getLong("updatedAt"),
        isTrashed = json.getBoolean("trashed"), trashedAt = json.nullableLong("trashedAt"),
        deletedAt = json.nullableLong("deletedAt"), subtasksJson = json.getString("subtasks")
    )
    private fun JSONObject.nullableLong(key: String) = if (isNull(key)) null else getLong(key)
}
