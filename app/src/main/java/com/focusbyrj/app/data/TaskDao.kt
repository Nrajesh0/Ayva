package com.focusbyrj.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY dueDate ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)
    
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Long): Task?
    
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND dueDate IS NOT NULL AND dueDate > :currentTime ORDER BY dueDate ASC")
    suspend fun getActivePendingTasks(currentTime: Long): List<Task>

    @Query("SELECT * FROM tasks ORDER BY dueDate ASC")
    suspend fun getAllTasksList(): List<Task>

    @Query("DELETE FROM tasks WHERE isCompleted = 1 AND (completedAt < :threshold OR completedAt IS NULL)")
    suspend fun deleteCompletedTasksBefore(threshold: Long)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
}
