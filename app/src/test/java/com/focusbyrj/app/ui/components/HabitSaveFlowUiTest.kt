package com.focusbyrj.app.ui.components

import android.app.Application
import android.database.sqlite.SQLiteFullException
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.*
import com.focusbyrj.app.ui.screens.HabitsScreen
import com.focusbyrj.app.ui.viewmodels.HabitViewModel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HabitSaveFlowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app
        get() = ApplicationProvider.getApplicationContext<Application>()

    private lateinit var db: FocusDatabase
    private lateinit var store: ViewModelStore

    @Before
    fun prepare() {
        db =
            Room.inMemoryDatabaseBuilder(app, FocusDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        store = ViewModelStore()
        EditorDraftStore(app, "habit:0:0").discard()
        app.getSharedPreferences("app_tours", 0).edit().clear().commit()
    }

    @After
    fun close() {
        store.clear()
        db.close()
    }

    private fun show(dao: HabitDao) {
        val vm = HabitViewModel(HabitRepository(dao), app)
        store.put("habit-save", vm)
        compose.setContent { MaterialTheme { HabitsScreen(vm, initialOpenCreate = true) } }
        compose.onNodeWithText("Title").performTextInput("Keep this habit draft")
    }

    @Test
    fun failedStorageKeepsTheEditorAndDraftAvailableForRetry() {
        val real = db.habitDao()
        val calls = AtomicInteger()
        val failing =
            object : HabitDao by real {
                override suspend fun insertHabit(habit: Habit): Long {
                    calls.incrementAndGet()
                    throw SQLiteFullException("Fixture storage failure")
                }
            }
        show(failing)
        compose.onNodeWithText("Create", substring = false).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Saving habit").fetchSemanticsNodes()
            calls.get() == 1 &&
                compose.onAllNodesWithText("Saving habit").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("New Habit").assertIsDisplayed()
        compose.onNodeWithText("Keep this habit draft").assertIsDisplayed()
        compose.onNodeWithText("Create", substring = false).assertIsEnabled().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Saving habit").fetchSemanticsNodes()
            calls.get() == 2
        }
        assertTrue(runBlocking { real.getAllActiveHabits().first().isEmpty() })
    }

    @Test
    fun queuedSaveClicksCreateOneHabitAndCloseOnlyAfterCommit() {
        val real = db.habitDao()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val waiting =
            object : HabitDao by real {
                override suspend fun insertHabit(habit: Habit): Long {
                    calls.incrementAndGet()
                    release.await()
                    return real.insertHabit(habit)
                }
            }
        show(waiting)
        compose.onNodeWithText("Create", substring = false).performClick()
        compose.waitUntil(10_000) { calls.get() == 1 }
        compose.onNodeWithText("Saving habit").assertIsDisplayed()
        // A second already-queued click must also be rejected by the owner.
        compose.onNodeWithText("Create", substring = false).performClick()
        assertEquals(1, calls.get())
        compose.runOnIdle { release.complete(Unit) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("New Habit").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(1, runBlocking { real.getAllActiveHabits().first().size })
    }
}
