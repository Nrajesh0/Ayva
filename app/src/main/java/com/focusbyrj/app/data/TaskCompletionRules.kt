package com.focusbyrj.app.data

/** Shared by native and assistant completion; restore preserves historical completed records. */
object TaskCompletionRules {
    fun validate(task: Task, tasks: List<Task>? = null) {
        if (tasks != null) {
            val p = task.planning
            val waiting = p?.prerequisiteTaskIds.orEmpty().filter { id -> tasks.none { it.id == id && it.isCompleted && !it.isTrashed } }
            require(waiting.isEmpty() && (p?.missingPrerequisites ?: 0) == 0) { "This task is waiting for prerequisites. Finish them or remove the Waiting for links in the task editor." }
        }
        if (task.planning?.requireSubtasks == true) {
            val remaining = task.subtasks.count { !it.isDone }
            require(remaining == 0) { "Finish the $remaining remaining subtasks before completing this task" }
        }
    }
}
