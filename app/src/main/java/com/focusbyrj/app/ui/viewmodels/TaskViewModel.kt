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

    fun addTask(task: Task) {
        viewModelScope.launch {
            val id = repository.insertTask(task)
            TaskReminderHelper.scheduleReminder(getApplication(), task.copy(id = id))
            TodoWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
            TaskReminderHelper.scheduleReminder(getApplication(), task)
            TodoWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            TaskReminderHelper.cancelReminder(getApplication(), task)
            TodoWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun toggleTaskCompletion(task: Task) {
        TaskReminderHelper.toggleTaskById(getApplication(), task.id)
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
