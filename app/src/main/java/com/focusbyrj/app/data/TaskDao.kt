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

    /**
     * Hard-deletes a task row. Only call this from emptyTrash, 30-day auto-purge,
     * or backup restore transactional cleanup. UI flows should use [softDeleteTask] or [updateTrashStatus].
     */
    @Delete
    suspend fun deleteTask(task: Task)
    
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

    @Query("DELETE FROM tasks WHERE isCompleted = 1 AND (completedAt < :threshold OR completedAt IS NULL)")
    suspend fun deleteCompletedTasksBefore(threshold: Long)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
}
