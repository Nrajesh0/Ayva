package com.focusbyrj.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isTrashed = 0 ORDER BY dueDate ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun getTrashedTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    suspend fun getTrashedTasksSync(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)

    @Update
    suspend fun updateTask(task: Task)

    @Transaction
    suspend fun insertTaskChecked(task: Task): Long {
        val id = insertTask(task)
        updateTaskChecked(task.copy(id = id), checkWork = true)
        return id
    }

    @Transaction
    suspend fun updateTaskChecked(task: Task, expectedUpdatedAt: Long? = null, checkWork: Boolean = false) {
        val old = getTaskById(task.id) ?: error("This task is no longer available")
        require(expectedUpdatedAt == null || old.updatedAt == expectedUpdatedAt) { "This task changed while you were editing. Reopen it; your draft is retained." }
        val all = getAllTasksList()
        TaskPlanning.validateGraph(all.map { if (it.id == task.id) task else it })
        val planning = task.planning
        require(planning?.end == null || task.dueDate == null || planning.end!! <= task.dueDate) { "The work finishes after the deadline" }
        if ((checkWork || planning?.workSessions != old.planning?.workSessions || planning?.prerequisiteTaskIds != old.planning?.prerequisiteTaskIds || planning?.afterMinutes != old.planning?.afterMinutes) && !task.isCompleted && !task.isTrashed && task.deletedAt == null) {
            if (planning?.start != null) {
                require(planning.missingPrerequisites == 0) { "Resolve missing prerequisites before scheduling this work" }
                planning.prerequisiteTaskIds.forEach { id ->
                    val prerequisite = all.firstOrNull { it.id == id }
                    require(prerequisite != null && (prerequisite.isCompleted || prerequisite.planning?.end?.let { it + planning.afterMinutes * 60_000L <= planning.start } == true)) { "Schedule prerequisite work before this task" }
                }
            }
            val other = all.filter { it.id != task.id && !it.isCompleted && !it.isTrashed && it.deletedAt == null }
                .flatMap { it.planning?.workSessions.orEmpty() }
            require(planning?.workSessions.orEmpty().none { a -> other.any { b -> a.start < b.end && b.start < a.end } }) { "The work overlaps another saved task" }
        }
        updateTask(task)
    }

    /** Insert an imported graph and rebind references only to rows from that import. */
    @Transaction
    suspend fun restoreTasks(tasks: List<Task>, replace: Boolean): Map<Long, Long> {
        TaskPlanning.validateGraph(tasks)
        val positiveIds = tasks.map { it.id }.filter { it > 0 }
        require(positiveIds.distinct().size == positiveIds.size) { "Duplicate task IDs" }
        if (replace) deleteAllTasks()
        val ids = mutableMapOf<Long, Long>()
        val inserted = tasks.map { task ->
            val id = insertTask(task.copy(id = if (replace) task.id else 0))
            if (task.id > 0) ids[task.id] = id
            task.copy(id = id)
        }
        inserted.forEach { task ->
            if (task.planningJson.isNotEmpty()) updateTask(task.copy(planningJson = TaskPlanning.remap(task.planningJson, ids)))
        }
        return ids
    }

    /**
     * Hard-deletes a task row. Only call this from emptyTrash, 30-day auto-purge,
     * or backup restore transactional cleanup. UI flows should use [softDeleteTask] or [updateTrashStatus].
     */
    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long)
    
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Long): Task?
    
    @Query("SELECT * FROM tasks WHERE isTrashed = 0 AND isCompleted = 0 AND dueDate IS NOT NULL AND dueDate > :currentTime ORDER BY dueDate ASC")
    suspend fun getActivePendingTasks(currentTime: Long): List<Task>

    @Query("SELECT * FROM tasks ORDER BY dueDate ASC")
    suspend fun getAllTasksList(): List<Task>

    @Query("SELECT * FROM tasks WHERE isTrashed = 0 ORDER BY dueDate ASC")
    suspend fun getAllActiveTasksList(): List<Task>

    /**
     * Soft-delete: moves task to trash with a [trashedAt] timestamp.
     * Safe primary delete path for all UI-triggered deletions.
     */
    @Query("UPDATE tasks SET isTrashed = 1, trashedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteTask(id: Long, now: Long = System.currentTimeMillis())

    /**
     * Updates trash status and records [trashedAt] when moving to trash,
     * or clears [trashedAt] when restoring from trash.
     */
    @Query("UPDATE tasks SET isTrashed = :isTrashed, updatedAt = :updatedAt, trashedAt = CASE WHEN :isTrashed = 1 THEN :updatedAt ELSE NULL END WHERE id = :id")
    suspend fun updateTrashStatus(id: Long, isTrashed: Boolean, updatedAt: Long = System.currentTimeMillis())

    /**
     * Returns trashed tasks older than [cutoffMs] for 30-day auto-purge.
     */
    @Query("SELECT * FROM tasks WHERE isTrashed = 1 AND trashedAt IS NOT NULL AND trashedAt < :cutoffMs")
    suspend fun getExpiredTrashedTasks(cutoffMs: Long): List<Task>

    /**
     * Hard-deletes tasks whose IDs are in [ids]. Used by 30-day auto-purge and batch delete.
     */
    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun hardDeleteTasksByIds(ids: List<Long>)

    @Query("DELETE FROM tasks WHERE isTrashed = 1")
    suspend fun emptyTrash()

    /**
     * Used by safe backup restore: deletes rows NOT present in the restored set
     * inside a withTransaction block.
     */
    @Query("DELETE FROM tasks WHERE id NOT IN (:ids)")
    suspend fun deleteTasksNotIn(ids: List<Long>)

    @Query("SELECT id FROM tasks WHERE isCompleted = 1 AND ((completedAt IS NOT NULL AND completedAt < :threshold) OR (completedAt IS NULL AND updatedAt < :threshold))")
    suspend fun getCompletedTaskIdsBefore(threshold: Long): List<Long>

    @Query("DELETE FROM tasks WHERE isCompleted = 1 AND ((completedAt IS NOT NULL AND completedAt < :threshold) OR (completedAt IS NULL AND updatedAt < :threshold))")
    suspend fun deleteCompletedTasksBefore(threshold: Long)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
}
