package com.focusbyrj.app.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.*
import com.focusbyrj.app.ui.screens.TodosScreen
import com.focusbyrj.app.ui.viewmodels.TaskViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskUndoFlowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun leavingAndReturningDoesNotCommitDeletionOrShortenAnotherTasksUndo() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db =
            Room.inMemoryDatabaseBuilder(app, FocusDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val store = ViewModelStore()
        try {
            val a = Task(id = 81, title = "First task")
            val b = Task(id = 82, title = "Second task")
            runBlocking {
                db.taskDao().insertTask(a)
                db.taskDao().insertTask(b)
            }
            val vm = TaskViewModel(TaskRepository(db.taskDao()), app)
            store.put("undo", vm)
            val visible = mutableStateOf(true)
            compose.setContent { MaterialTheme { if (visible.value) TodosScreen(vm) } }
            compose.runOnIdle { vm.requestDelete(a) }
            val firstDeadline = vm.pendingDeletions.value.single().deadline
            compose.runOnIdle { visible.value = false }
            assertNotNull(runBlocking { db.taskDao().getTaskById(a.id) })
            assertEquals(firstDeadline, vm.pendingDeletions.value.single().deadline)
            compose.runOnIdle {
                vm.requestDelete(b)
                visible.value = true
            }
            assertEquals(2, vm.pendingDeletions.value.size)
            assertNotNull(runBlocking { db.taskDao().getTaskById(a.id) })
            compose.runOnIdle {
                vm.undoDelete(a.id)
                vm.undoDelete(b.id)
            }
            assertTrue(vm.pendingDeletions.value.isEmpty())
            assertEquals(2, runBlocking { db.taskDao().getAllTasksList().size })
        } finally {
            store.clear()
            db.close()
        }
    }
}
