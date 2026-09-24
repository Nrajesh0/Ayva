package com.focusbyrj.app.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    val trashedTasks: Flow<List<Task>> = taskDao.getTrashedTasks()

    suspend fun insertTask(task: Task): Long {
        return taskDao.insertTask(task)
    }

    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task)
    }

    /**
     * Directly deletes a task row from SQLite (standard production To-Do model).
     */
    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
        if (task.id > 0L) {
            taskDao.deleteTaskById(task.id)
        }
    }

    suspend fun moveToTrash(id: Long) {
        taskDao.updateTrashStatus(id, isTrashed = true)
    }

    suspend fun restoreFromTrash(id: Long) {
        taskDao.updateTrashStatus(id, isTrashed = false)
    }

    /**
     * Irrevocably deletes a task row from SQLite.
     * Only call this when explicitly requested by user in Trash ("Delete Forever").
     */
    suspend fun deletePermanently(task: Task) {
        taskDao.deleteTask(task)
        if (task.id > 0L) {
            taskDao.deleteTaskById(task.id)
        }
    }

    suspend fun emptyTrash() {
        taskDao.emptyTrash()
    }
    
    suspend fun getTaskById(taskId: Long): Task? {
        return taskDao.getTaskById(taskId)
    }
    
    suspend fun deleteCompletedTasksBefore(threshold: Long) {
        taskDao.deleteCompletedTasksBefore(threshold)
    }
}
