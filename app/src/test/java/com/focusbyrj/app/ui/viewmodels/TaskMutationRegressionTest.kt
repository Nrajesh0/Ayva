package com.focusbyrj.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.*
import com.focusbyrj.app.data.note.NotesStorageVersion
import com.focusbyrj.app.util.backup.MaintenanceGate
import com.focusbyrj.app.util.backup.MaintenanceOpenHelperFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TaskMutationRegressionTest {
    private val app
        get() = ApplicationProvider.getApplicationContext<Application>()

    private val scheduler = TestCoroutineScheduler()
    private val models = ViewModelStore()
    private val viewModels = mutableListOf<TaskViewModel>()
    private val releases = mutableListOf<CompletableDeferred<Unit>>()
    private lateinit var db: FocusDatabase

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        db =
            Room.inMemoryDatabaseBuilder(app, FocusDatabase::class.java)
                .openHelperFactory(MaintenanceOpenHelperFactory())
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun cleanup() {
        releases.forEach { it.complete(Unit) }
        models.clear()
        try {
            awaitState { viewModels.all { it.viewModelScope.coroutineContext[Job]!!.isCompleted } }
        } finally {
            Dispatchers.resetMain()
            db.close()
        }
    }

    private fun model(dao: TaskDao = db.taskDao()): TaskViewModel {
        val isolated =
            object : TaskDao by dao {
                override suspend fun getCompletedTaskIdsBefore(threshold: Long) = emptyList<Long>()
            }
        return TaskViewModel(TaskRepository(isolated), app).also {
            models.put("model-${viewModels.size}", it)
            viewModels += it
            scheduler.runCurrent()
        }
    }

    private fun release() = CompletableDeferred<Unit>().also { releases += it }

    private fun awaitState(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        do {
            scheduler.runCurrent()
            if (condition()) return
            Thread.sleep(5)
        } while (System.nanoTime() < deadline)
        fail("Timed out waiting for the task mutation to finish")
    }

    private fun stored(id: Long) = runBlocking { db.taskDao().getTaskById(id) }

    private fun insert(vararg tasks: Task) = runBlocking {
        tasks.forEach { db.taskDao().insertTask(it) }
    }

    @Test
    fun pendingDeleteCannotDeleteAnIdenticalRestoredTask() {
        val original = Task(id = 71, title = "Keep in restored snapshot", updatedAt = 100L)
        insert(original)
        val real = db.taskDao()
        val deletes = AtomicInteger()
        val dao =
            object : TaskDao by real {
                override suspend fun deleteWithDependencies(task: Task, detach: Boolean) {
                    deletes.incrementAndGet()
                    real.deleteWithDependencies(task, detach)
                }
            }
        val vm = model(dao)
        var discarded = false
        vm.requestDelete(original) { discarded = true }
        scheduler.runCurrent()
        runBlocking {
            MaintenanceGate.exclusively {
                real.restoreTasks(listOf(original), replace = true)
                NotesStorageVersion.replaced()
            }
        }
        scheduler.advanceTimeBy(4_001)
        awaitState { vm.pendingDeletions.value.isEmpty() && vm.hiddenTaskIds.value.isEmpty() }
        assertEquals(original, stored(original.id))
        assertEquals(0, deletes.get())
        assertFalse(discarded)
        assertTrue(ShadowToast.getTextOfLatestToast().contains("Deletion cancelled"))
    }

    @Test
    fun backupReadKeepsTheOriginalDeleteDeadlineAndCommitsNormally() {
        val original = Task(id = 72, title = "Delete after grace period")
        insert(original)
        val vm = model()
        var discarded = false
        vm.requestDelete(original) { discarded = true }
        scheduler.runCurrent()
        val deadline = vm.pendingDeletions.value.single().deadline
        runBlocking { MaintenanceGate.exclusively { assertEquals(original, stored(original.id)) } }
        assertEquals(deadline, vm.pendingDeletions.value.single().deadline)
        scheduler.advanceTimeBy(4_001)
        awaitState { discarded }
        assertNull(stored(original.id))
    }

    @Test
    fun undoStillKeepsTheTaskAndItsDraft() {
        val original = Task(id = 73, title = "Undo deletion")
        insert(original)
        val vm = model()
        var discarded = false
        vm.requestDelete(original) { discarded = true }
        scheduler.runCurrent()
        vm.undoDelete(original.id)
        scheduler.advanceTimeBy(4_001)
        assertEquals(original, stored(original.id))
        assertTrue(vm.pendingDeletions.value.isEmpty())
        assertFalse(discarded)
    }

    @Test
    fun restoreWaitsForAnAlreadyAdmittedDeleteAndThenKeepsItsSnapshot() {
        val original = Task(id = 74, title = "Restored after admitted deletion")
        insert(original)
        val real = db.taskDao()
        val entered = CompletableDeferred<Unit>()
        val release = release()
        val dao =
            object : TaskDao by real {
                override suspend fun deleteWithDependencies(task: Task, detach: Boolean) {
                    entered.complete(Unit)
                    release.await()
                    real.deleteWithDependencies(task, detach)
                }
            }
        val vm = model(dao)
        vm.deleteTask(original)
        awaitState { entered.isCompleted }
        val restored = CompletableDeferred<Unit>()
        runBlocking {
            val restore =
                launch(Dispatchers.IO) {
                    MaintenanceGate.exclusively {
                        real.restoreTasks(listOf(original), replace = true)
                        NotesStorageVersion.replaced()
                        restored.complete(Unit)
                    }
                }
            try {
                withTimeout(10_000) { while (!MaintenanceGate.isBusy) delay(5) }
                assertFalse(restored.isCompleted)
                release.complete(Unit)
                withTimeout(10_000) { restore.join() }
            } finally {
                release.complete(Unit)
                restore.cancelAndJoin()
            }
        }
        assertEquals(original, stored(original.id))
    }

    @Test
    fun subtaskTapPersistsWhileAnotherTaskSaveIsWaiting() {
        val a = Task(id = 81, title = "Task A", updatedAt = 200L)
        val step = Subtask(id = "step-b", title = "Do B")
        val b =
            Task(
                id = 82,
                title = "Task B",
                updatedAt = 201L,
                subtasksJson = Subtask.listToJson(listOf(step)),
            )
        insert(a, b)
        val real = db.taskDao()
        val release = release()
        val dao =
            object : TaskDao by real {
                override suspend fun updateTaskChecked(
                    task: Task,
                    expectedUpdatedAt: Long?,
                    checkWork: Boolean,
                ) {
                    if (task.id == a.id) release.await()
                    real.updateTaskChecked(task, expectedUpdatedAt, checkWork)
                }
            }
        val vm = model(dao)
        vm.updateTask(a.copy(details = "Edited"))
        scheduler.runCurrent()
        assertTrue(vm.saving.value)
        vm.toggleSubtask(b, step.id)
        awaitState { stored(b.id)!!.subtasks.single().isDone }
        assertTrue(vm.saving.value)
        release.complete(Unit)
        awaitState { !vm.saving.value }
        assertEquals("Edited", stored(a.id)!!.details)
        assertTrue(stored(b.id)!!.subtasks.single().isDone)
    }

    @Test
    fun rapidTapsOnDifferentStepsPreserveBothAndUnrelatedEdits() {
        val first = Subtask(id = "first", title = "First")
        val second = Subtask(id = "second", title = "Second")
        val original =
            Task(
                id = 83,
                title = "Two steps",
                updatedAt = 300L,
                subtasksJson = Subtask.listToJson(listOf(first, second)),
            )
        insert(original.copy(details = "Edited elsewhere", updatedAt = 301L))
        val vm = model()
        vm.toggleSubtask(original, first.id)
        vm.toggleSubtask(original, second.id)
        awaitState { stored(original.id)!!.subtasks.all { it.isDone } }
        assertEquals("Edited elsewhere", stored(original.id)!!.details)
    }

    @Test
    fun rapidDoubleTapOnTheSameStepAppliesBothToggles() {
        val step = Subtask(id = "twice", title = "Toggle twice")
        val original =
            Task(
                id = 84,
                title = "Repeated step",
                updatedAt = System.currentTimeMillis() + 60_000L,
                subtasksJson = Subtask.listToJson(listOf(step)),
            )
        insert(original)
        val vm = model()
        vm.toggleSubtask(original, step.id)
        vm.toggleSubtask(original, step.id)
        awaitState { stored(original.id)!!.updatedAt >= original.updatedAt + 2 }
        assertFalse(stored(original.id)!!.subtasks.single().isDone)
    }

    @Test
    fun queuedSubtaskTapCannotCrossSnapshotReplacement() {
        val step = Subtask(id = "old-step", title = "Old queued tap")
        val original =
            Task(id = 85, title = "Restored task", subtasksJson = Subtask.listToJson(listOf(step)))
        insert(original)
        val vm = model()
        vm.toggleSubtask(original, step.id)
        runBlocking {
            MaintenanceGate.exclusively {
                db.taskDao().restoreTasks(listOf(original), replace = true)
                NotesStorageVersion.replaced()
            }
        }
        awaitState { ShadowToast.getTextOfLatestToast()?.contains("Data was restored") == true }
        assertEquals(original, stored(original.id))
    }
}
