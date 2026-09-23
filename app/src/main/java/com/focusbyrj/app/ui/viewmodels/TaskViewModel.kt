package com.focusbyrj.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskRepository
import com.focusbyrj.app.data.TaskType
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
            com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(getApplication())
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
            TaskReminderHelper.scheduleReminder(getApplication(), task)
            TodoWidgetProvider.updateAllWidgets(getApplication())
            com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(getApplication())
        }
    }

    /**
     * Directly and permanently deletes the task (standard production To-Do model).
     * Queues cloud tombstone for Supabase, cancels reminders, and updates widgets.
     */
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            com.focusbyrj.app.util.sync.supabase.SupabaseSyncEngine.recordLocalDeletion(getApplication(), "TASK", task.id)
            repository.deletePermanently(task)
            TaskReminderHelper.cancelReminder(getApplication(), task)
            TodoWidgetProvider.updateAllWidgets(getApplication())
            com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(getApplication())
        }
    }

    fun restoreTask(task: Task) {
        viewModelScope.launch {
            repository.restoreFromTrash(task.id)
            if (!task.isCompleted && task.dueDate != null && task.dueDate > System.currentTimeMillis()) {
                TaskReminderHelper.scheduleReminder(getApplication(), task.copy(isTrashed = false))
            }
            TodoWidgetProvider.updateAllWidgets(getApplication())
            com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(getApplication())
        }
    }

    fun deletePermanently(task: Task) {
        deleteTask(task)
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val currentTrashed = trashedTasks.value
            currentTrashed.forEach { t ->
                com.focusbyrj.app.util.sync.supabase.SupabaseSyncEngine.recordLocalDeletion(getApplication(), "TASK", t.id)
            }
            repository.emptyTrash()
            TodoWidgetProvider.updateAllWidgets(getApplication())
            com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(getApplication())
        }
    }

    fun toggleTaskCompletion(task: Task) {
        TaskReminderHelper.toggleTaskById(getApplication(), task.id)
        com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(getApplication())
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
