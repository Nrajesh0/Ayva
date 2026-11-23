package com.focusbyrj.app.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.*
import com.focusbyrj.app.ui.screens.AddTaskDialog
import com.focusbyrj.app.ui.screens.TodosScreen
import com.focusbyrj.app.ui.viewmodels.TaskViewModel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real storage and UI regressions from the independent flow audit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskSaveFlowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app
        get() = ApplicationProvider.getApplicationContext<Application>()

    private lateinit var db: FocusDatabase
    private val models = ViewModelStore()
    private val release = CompletableDeferred<Unit>()

    @Before
    fun setup() {
        db =
            Room.inMemoryDatabaseBuilder(app, FocusDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        app.getSharedPreferences("app_tours", 0).edit().clear().commit()
        java.io.File(app.filesDir, "editor_drafts").deleteRecursively()
    }

    @After
    fun cleanup() {
        release.complete(Unit)
        models.clear()
        db.close()
    }

    @Test
    fun saveClaimsStorageBeforeAnotherTapCanCreateACopy() {
        val real = db.taskDao()
        val calls = AtomicInteger()
        val dao =
            object : TaskDao by real {
                override suspend fun insertTaskChecked(task: Task): Long {
                    calls.incrementAndGet()
                    release.await()
                    return real.insertTaskChecked(task)
                }
            }
        val vm = TaskViewModel(TaskRepository(dao), app)
        models.put("review", vm)
        compose.setContent { MaterialTheme { TodosScreen(vm, initialOpenAdd = true) } }
        compose.onNodeWithText("Title", substring = false).performTextInput("Review duplicate task")
        compose.onNodeWithText("Save", substring = false).performClick()
        compose.waitUntil(10_000) { calls.get() == 1 }
        compose.onNodeWithText("Saving task…").assertIsDisplayed()
        compose.runOnIdle { vm.addTask(Task(title = "Review duplicate task")) }
        assertEquals(1, calls.get())
        compose.runOnIdle { release.complete(Unit) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("New Task").fetchSemanticsNodes()
            runBlocking {
                real.getAllTasksList().count { it.title == "Review duplicate task" } == 1
            }
        }
        assertEquals(1, runBlocking { real.getAllTasksList().size })
    }

    @Test
    fun deletedEditorCannotSilentlyCreateAnotherTask() {
        val missing = mutableStateOf(false)
        var saved: Task? = null
        val original = Task(id = 91, title = "Original", updatedAt = 100L)
        compose.setContent {
            MaterialTheme {
                AddTaskDialog(
                    initialTask = original,
                    recordMissing = missing.value,
                    onDismiss = {},
                    onSave = { task, _ -> saved = task },
                )
            }
        }
        compose.onNodeWithText("Original").performTextReplacement("Uncommitted text")
        compose.runOnIdle { missing.value = true }
        compose.onNodeWithText("Save", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Keep as a new task").performClick()
        compose.onNodeWithText("Save copy").performClick()
        assertEquals(0L, saved?.id)
        assertEquals("Uncommitted text", saved?.title)
    }

    @Test
    fun failedSaveRetainsEditableForm() {
        val real = db.taskDao()
        val dao =
            object : TaskDao by real {
                override suspend fun insertTaskChecked(task: Task): Long {
                    throw android.database.sqlite.SQLiteFullException("Storage is full")
                }
            }
        val vm = TaskViewModel(TaskRepository(dao), app)
        models.put("failure", vm)
        compose.setContent { MaterialTheme { TodosScreen(vm, initialOpenAdd = true) } }
        compose.onNodeWithText("Title").performTextInput("Retain after failure")
        compose.onNodeWithText("Save", substring = false).performClick()
        compose.waitUntil { !vm.saving.value }
        compose.onNodeWithText("Retain after failure").assertIsDisplayed()
        compose.onNodeWithText("Save", substring = false).assertIsEnabled()
        assertEquals(0, runBlocking { real.getAllTasksList().size })
    }

    @Test
    fun reopeningFromAnotherTabRestoresTheDraftAndItsOriginalRevision() {
        val original = Task(id = 92, title = "Saved task", updatedAt = 100L)
        val row = mutableStateOf(original)
        val shown = mutableStateOf(true)
        val default = mutableStateOf(TaskType.TASK)
        compose.setContent {
            MaterialTheme {
                if (shown.value)
                    AddTaskDialog(
                        initialTask = row.value,
                        defaultType = default.value,
                        onDismiss = {},
                        onSave = { _, _ -> },
                    )
            }
        }
        compose.onNodeWithText("Saved task").performTextReplacement("Keep across navigation")
        compose.runOnIdle { shown.value = false }
        compose.runOnIdle {
            row.value = original.copy(title = "Updated elsewhere")
            default.value = TaskType.BIRTHDAY
            shown.value = true
        }
        compose.onNodeWithText("Keep across navigation").assertIsDisplayed()
        compose.onNodeWithText("Draft restored").assertIsDisplayed()
        compose.onNodeWithText("Save", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Reload saved task").performClick()
        compose.onNodeWithText("Updated elsewhere").assertIsDisplayed()
    }

    @Test
    fun choosingATemplateDoesNotReplaceAnUnrelatedNewTaskDraft() {
        EditorDraftStore(app, "task:new:TASK")
            .save(org.json.JSONObject().put("title", "Unrelated unfinished task"))
        val initial = mutableStateOf<Task?>(Task(title = "Selected template"))
        val shown = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (shown.value)
                    AddTaskDialog(
                        initialTask = initial.value,
                        draftKeyOverride = "task:template-copy:synthetic-session",
                        onDismiss = {},
                        onSave = { _, _ -> },
                    )
            }
        }
        compose
            .onNodeWithText("Selected template")
            .assertIsDisplayed()
            .performTextReplacement("Edited template copy")
        compose.runOnIdle { shown.value = false }
        compose.runOnIdle {
            initial.value = null
            shown.value = true
        }
        compose.onNodeWithText("Edited template copy").assertIsDisplayed()
        assertEquals(
            "Unrelated unfinished task",
            EditorDraftStore(app, "task:new:TASK").read().getString("title"),
        )
    }

    @Test
    fun externalRowRefreshKeepsTypedTextAndRequiresConflictChoice() {
        val original = Task(id = 7001, title = "Stored original title", updatedAt = 100L)
        val row = mutableStateOf(original)
        compose.setContent {
            MaterialTheme {
                AddTaskDialog(initialTask = row.value, onDismiss = {}, onSave = { _, _ -> })
            }
        }
        compose
            .onNodeWithText("Stored original title")
            .performTextReplacement("My unsaved local edits")
        compose.onNodeWithText("My unsaved local edits").assertIsDisplayed()
        compose.runOnIdle {
            row.value = original.copy(details = "Changed from another surface", updatedAt = 101L)
        }
        compose.onNodeWithText("My unsaved local edits").assertIsDisplayed()
        compose.onNodeWithText("Save", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Reload saved task").performClick()
        compose.onNodeWithText("Stored original title").assertIsDisplayed()
    }
}
