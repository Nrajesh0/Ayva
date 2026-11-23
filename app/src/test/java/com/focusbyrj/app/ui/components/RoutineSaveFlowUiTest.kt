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
import com.focusbyrj.app.ui.screens.*
import com.focusbyrj.app.ui.viewmodels.FocusViewModel
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
class RoutineSaveFlowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app
        get() = ApplicationProvider.getApplicationContext<Application>()

    private lateinit var db: FocusDatabase
    private val models = ViewModelStore()
    private val original =
        FocusSchedule(
            id = 92,
            name = "Stored routine",
            startHour = 9,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            daysOfWeek = "2,3,4,5,6",
        )

    @Before
    fun setup() {
        db =
            Room.inMemoryDatabaseBuilder(app, FocusDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        java.io.File(app.filesDir, "editor_drafts").deleteRecursively()
    }

    @After
    fun cleanup() {
        models.clear()
        db.close()
    }

    @Test
    fun liveChangesCannotReplaceUnsavedRoutineText() {
        val current = mutableStateOf(original)
        compose.setContent {
            MaterialTheme { CreateRoutineScreen(current.value, {}, { _, _, _ -> }) }
        }
        compose.onNodeWithText("Stored routine").performTextReplacement("My unfinished routine")
        compose.runOnIdle { current.value = original.copy(name = "Updated elsewhere") }
        compose.onNodeWithText("My unfinished routine").assertIsDisplayed()
        compose.onNodeWithText("Save Routine").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Review changed routine").performClick()
        compose.onNodeWithText("Reload saved routine").performClick()
        compose.onNodeWithText("Updated elsewhere").assertIsDisplayed()
    }

    @Test
    fun failedDatabaseWriteKeepsRoutineFormAndEncryptedDraft() {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_routine BEFORE INSERT ON focus_schedules BEGIN SELECT RAISE(ABORT, 'Synthetic storage failure'); END"
        )
        val vm = FocusViewModel(AppRepository(db), app)
        models.put("routine", vm)
        compose.setContent { MaterialTheme { SchedulesScreen(vm) } }
        compose.onNodeWithContentDescription("Add Schedule").performClick()
        compose.onNodeWithText("Deep Focus").performTextReplacement("Keep routine after failure")
        compose.onNodeWithText("Save Routine").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            // Semantics access drains Room's main-thread completion in paused Robolectric.
            compose.onAllNodesWithText("Saving routine…").fetchSemanticsNodes()
            !vm.savingSchedule.value
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("Keep routine after failure").assertIsDisplayed()
        compose.onNodeWithText("Save Routine").performScrollTo().assertIsEnabled()
        assertTrue(runBlocking { db.scheduleDao().getAllSchedulesSync().isEmpty() })
    }

    @Test
    fun savingUnchangedSelectionsPreservesTheRunningRoutineSnapshot() {
        val unsorted =
            original.copy(daysOfWeek = "6,2", appsToBlock = "synthetic.z|SOFT,synthetic.a|HARD")
        var stored: FocusSchedule? = null
        compose.setContent {
            MaterialTheme { CreateRoutineScreen(unsorted, {}, { value, _, _ -> stored = value }) }
        }
        compose.onNodeWithText("Save Routine").performScrollTo().performClick()
        assertEquals(unsorted, stored)
    }

    @Test
    fun staleRoutineUpdateCannotRecreateDeletedRowOrOverwriteNewerRow() = runBlocking {
        db.scheduleDao().insertSchedule(original)
        val changed = original.copy(name = "External update")
        db.scheduleDao().insertSchedule(changed)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                db.scheduleDao().updateChecked(original.copy(name = "Stale draft"), original)
            }
        }
        assertEquals(changed, db.scheduleDao().getScheduleById(original.id))
        db.scheduleDao().deleteSchedule(changed)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { db.scheduleDao().updateChecked(original, original) }
        }
        assertNull(db.scheduleDao().getScheduleById(original.id))
    }
}
