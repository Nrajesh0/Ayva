package com.focusbyrj.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focusbyrj.app.data.Subtask
import com.focusbyrj.app.data.subtasks
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskRepository
import com.focusbyrj.app.data.TaskType
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.util.backup.DataSafetyManager
import com.focusbyrj.app.util.TaskReminderHelper
import com.focusbyrj.app.widget.TodoWidgetProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(
    private val repository: TaskRepository, 
    application: Application
) : AndroidViewModel(application) {

    init {
        viewModelScope.launch {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)
            try {
                val expiredIds = repository.getCompletedTaskIdsBefore(thirtyDaysAgo)
                if (expiredIds.isNotEmpty()) {
                    repository.deleteCompletedTasksBefore(thirtyDaysAgo)
                }
            } catch (e: Exception) {
                android.util.Log.e("TaskViewModel", "Failed to purge completed tasks", e)
            }
        }
    }

    val allTasks: StateFlow<List<Task>> = repository.allTasks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val trashedTasks: StateFlow<List<Task>> = repository.trashedTasks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addTask(task: Task) {
        viewModelScope.launch {
            val id = repository.insertTask(task)
            TaskReminderHelper.scheduleReminder(getApplication(), task.copy(id = id))
            TodoWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            val updatedTask = task.copy(updatedAt = System.currentTimeMillis())
            repository.updateTask(updatedTask)
            TaskReminderHelper.scheduleReminder(getApplication(), updatedTask)
            TodoWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    /**
     * Directly and permanently deletes the task (standard production To-Do model).
     * Cancels reminders and updates widgets.
     */
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deletePermanently(task)
            TaskReminderHelper.cancelReminder(getApplication(), task)
            TodoWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun restoreTask(task: Task) {
        viewModelScope.launch {
            repository.restoreFromTrash(task.id)
            if (!task.isCompleted && task.dueDate != null && task.dueDate > System.currentTimeMillis()) {
                TaskReminderHelper.scheduleReminder(getApplication(), task.copy(isTrashed = false))
            }
            TodoWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun deletePermanently(task: Task) {
        deleteTask(task)
    }

    fun emptyTrash() {
        viewModelScope.launch {
            try {
                val app = getApplication<FocusApplication>()
                val noteDb = NoteDatabase.getInstance(app)
                DataSafetyManager.writePreOpSnapshot(app, noteDb.noteDao(), "emptyTrash_unified", app.database)
            } catch (_: Exception) {}
            repository.emptyTrash()
            TodoWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun toggleTaskCompletion(task: Task) {
        TaskReminderHelper.toggleTaskById(getApplication(), task.id)
    }

    fun toggleSubtask(task: Task, subtaskId: String) {
        val updated = task.subtasks.map {
            if (it.id == subtaskId) it.copy(isDone = !it.isDone) else it
        }
        updateTask(task.copy(subtasksJson = Subtask.listToJson(updated)))
    }

    fun updateSubtasks(task: Task, subtasks: List<Subtask>) {
        updateTask(task.copy(subtasksJson = Subtask.listToJson(subtasks)))
    }
}

class TaskViewModelFactory(
    private val repository: TaskRepository, 
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
