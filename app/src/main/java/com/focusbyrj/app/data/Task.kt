package com.focusbyrj.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskType {
    TASK, BIRTHDAY, ANNIVERSARY
}

enum class RecurrencePattern {
    NONE, DAILY, WEEKLY, MONTHLY, YEARLY
}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val details: String = "",
    val dueDate: Long? = null,
    val isCompleted: Boolean = false,
    val type: TaskType = TaskType.TASK,
    val recurrence: RecurrencePattern = RecurrencePattern.NONE,
    val isPersistent: Boolean = false,
    val isPriority: Boolean = false,
    val completedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val deletedAt: Long? = null,
    val subtasksJson: String = "[]"
)

val Task.subtasks: List<Subtask>
    get() = Subtask.listFromJson(subtasksJson)

data class Subtask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val isDone: Boolean = false
) {
    fun toJsonObject(): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", id)
        put("title", title)
        put("isDone", isDone)
    }

    companion object {
        fun listToJson(items: List<Subtask>): String {
            val arr = org.json.JSONArray()
            items.forEach { arr.put(it.toJsonObject()) }
            return arr.toString()
        }

        fun listFromJson(json: String): List<Subtask> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = org.json.JSONArray(json)
                val list = mutableListOf<Subtask>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val title = if (obj.has("title")) obj.optString("title", "") else obj.optString("text", "")
                    val isDone = when {
                        obj.has("isDone") -> obj.optBoolean("isDone", false)
                        obj.has("isCompleted") -> obj.optBoolean("isCompleted", false)
                        obj.has("isChecked") -> obj.optBoolean("isChecked", false)
                        else -> false
                    }
                    list.add(
                        Subtask(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            title = title,
                            isDone = isDone
                        )
                    )
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
