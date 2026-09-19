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
    val completedAt: Long? = null
)
