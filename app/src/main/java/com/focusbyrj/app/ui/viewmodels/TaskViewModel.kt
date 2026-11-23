package com.focusbyrj.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.Subtask
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskRepository
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NotesStorageVersion
import com.focusbyrj.app.util.TaskReminderHelper
import com.focusbyrj.app.util.backup.DataSafetyManager
import com.focusbyrj.app.util.backup.MaintenanceGate
import com.focusbyrj.app.widget.TodoWidgetProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(private val repository: TaskRepository, application: Application) :
    AndroidViewModel(application) {

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

    val allTasks: StateFlow<List<Task>> =
        repository.allTasks
            .onEach { rows ->
                val present = rows.mapTo(hashSetOf()) { it.id }
                _hiddenTaskIds.value =
                    _hiddenTaskIds.value.filterTo(hashSetOf()) {
                        it in present || deletionJobs.containsKey(it)
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    val trashedTasks: StateFlow<List<Task>> =
        repository.trashedTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _saving = kotlinx.coroutines.flow.MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    data class EditorRecord(val loaded: Boolean = false, val task: Task? = null)

    fun observeEditor(id: Long) =
        repository.observeTask(id).let { source ->
            source.map { EditorRecord(true, it?.takeUnless { row -> row.isTrashed }) }
        }

    fun addTask(task: Task, onStored: () -> Unit = {}) = saveTask(task, true, onStored)

    fun updateTask(task: Task, onStored: () -> Unit = {}) = saveTask(task, false, onStored)

    private fun saveTask(task: Task, creating: Boolean, onStored: () -> Unit) {
        // Claim before launching: queued taps must not enqueue two inserts.
        if (!_saving.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val stored =
                    try {
                        if (creating) task.copy(id = repository.insertTask(task))
                        else
                            task
                                .copy(
                                    updatedAt =
                                        maxOf(System.currentTimeMillis(), task.updatedAt + 1)
                                )
                                .also { repository.updateTask(it, task.updatedAt) }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        message(error.message ?: "Couldn't save task; draft retained")
                        return@launch
                    }
                onStored()
                try {
                    TaskReminderHelper.scheduleReminder(getApplication(), stored)
                    TodoWidgetProvider.updateAllWidgets(getApplication())
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    message(
                        "Task saved. Reminder or widget refresh failed; check reminder access in Settings."
                    )
                }
            } finally {
                _saving.value = false
            }
        }
    }

    private fun message(text: String) {
        android.widget.Toast.makeText(getApplication(), text, android.widget.Toast.LENGTH_LONG)
            .show()
    }

    data class PendingDeletion(val task: Task, val deadline: Long)

    private val _pendingDeletions =
        kotlinx.coroutines.flow.MutableStateFlow<List<PendingDeletion>>(emptyList())
    val pendingDeletions: StateFlow<List<PendingDeletion>> = _pendingDeletions
    private val _hiddenTaskIds = kotlinx.coroutines.flow.MutableStateFlow<Set<Long>>(emptySet())
    val hiddenTaskIds: StateFlow<Set<Long>> = _hiddenTaskIds
    private val deletionJobs = mutableMapOf<Long, kotlinx.coroutines.Job>()

    fun requestDelete(task: Task, onDeleted: () -> Unit = {}) {
        if (task.id in _hiddenTaskIds.value) return
        val storageVersion = NotesStorageVersion.current()
        val pending = PendingDeletion(task, android.os.SystemClock.elapsedRealtime() + 4000)
        _hiddenTaskIds.value += task.id
        _pendingDeletions.value += pending
        deletionJobs[task.id] =
            viewModelScope.launch {
                kotlinx.coroutines.delay(
                    maxOf(0, pending.deadline - android.os.SystemClock.elapsedRealtime())
                )
                _pendingDeletions.value =
                    _pendingDeletions.value.filterNot { it.task.id == task.id }
                deletionJobs.remove(task.id)
                deleteTaskInStore(task, false, storageVersion, onDeleted)
            }
    }

    fun undoDelete(id: Long) {
        val pending = _pendingDeletions.value.find { it.task.id == id } ?: return
        if (android.os.SystemClock.elapsedRealtime() >= pending.deadline) return
        deletionJobs.remove(id)?.cancel()
        _pendingDeletions.value = _pendingDeletions.value.filterNot { it.task.id == id }
        _hiddenTaskIds.value -= id
    }

    /**
     * Directly and permanently deletes the task (standard production To-Do model). Cancels
     * reminders and updates widgets.
     */
    fun deleteTask(task: Task, detach: Boolean = false, onDeleted: () -> Unit = {}) {
        deleteTaskInStore(task, detach, NotesStorageVersion.current(), onDeleted)
    }

    private fun deleteTaskInStore(
        task: Task,
        detach: Boolean,
        storageVersion: Long,
        onDeleted: () -> Unit,
    ) {
        viewModelScope.launch {
            var refreshFailed = false
            try {
                // Keep the generation check, delete and reminder cleanup in one admitted write.
                MaintenanceGate.shared {
                    check(NotesStorageVersion.current() == storageVersion) {
                        "Deletion cancelled because data was restored. Review the task before deleting it."
                    }
                    repository.deletePermanently(task, detach)
                    try {
                        TaskReminderHelper.cancelReminder(getApplication(), task)
                        TodoWidgetProvider.updateAllWidgets(getApplication())
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        refreshFailed = true
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.widget.Toast.makeText(
                        getApplication(),
                        e.message,
                        android.widget.Toast.LENGTH_LONG,
                    )
                    .show()
                _hiddenTaskIds.value -= task.id
                return@launch
            }
            try {
                if (NotesStorageVersion.current() == storageVersion) onDeleted()
                if (refreshFailed) message("Task deleted. Reminder or widget refresh failed.")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message("Task deleted. Reminder or widget refresh failed.")
            }
        }
    }

    fun restoreTask(task: Task) {
        viewModelScope.launch {
            repository.restoreFromTrash(task.id)
            if (!task.isCompleted) {
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
                DataSafetyManager.writePreOpSnapshot(
                    app,
                    noteDb.noteDao(),
                    "emptyTrash_unified",
                    app.database,
                )
            } catch (_: Exception) {}
            repository.emptyTrash()
            TodoWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun toggleTaskCompletion(task: Task, overrideDependencies: Boolean = false) {
        TaskReminderHelper.toggleTaskById(
            getApplication(),
            task.id,
            overrideDependencies = overrideDependencies,
        )
    }

    fun toggleSubtask(task: Task, subtaskId: String) {
        val storageVersion = NotesStorageVersion.current()
        viewModelScope.launch {
            var refreshFailed = false
            try {
                MaintenanceGate.shared {
                    check(NotesStorageVersion.current() == storageVersion) {
                        "Data was restored. Review the task before changing its steps."
                    }
                    val stored = repository.toggleSubtask(task.id, subtaskId)
                    try {
                        TaskReminderHelper.scheduleReminder(getApplication(), stored)
                        TodoWidgetProvider.updateAllWidgets(getApplication())
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        refreshFailed = true
                    }
                }
                if (refreshFailed) message("Step saved. Reminder or widget refresh failed.")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message(error.message ?: "Couldn't update this step. Try again.")
            }
        }
    }

    fun updateSubtasks(task: Task, subtasks: List<Subtask>) {
        updateTask(task.copy(subtasksJson = Subtask.listToJson(subtasks)))
    }
}

class TaskViewModelFactory(
    private val repository: TaskRepository,
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
