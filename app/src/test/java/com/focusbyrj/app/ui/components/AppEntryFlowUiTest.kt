package com.focusbyrj.app.ui.components

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.MainActivity
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.ui.screens.notes.NotesViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FocusApplication::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppEntryFlowUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<FocusApplication>()

    private var controller: ActivityController<MainActivity>? = null
    private lateinit var activity: MainActivity
    private lateinit var notes: NotesViewModel

    @Before
    fun prepare() {
        app.getSharedPreferences("focus_app_prefs", 0)
            .edit()
            .putBoolean("has_seen_permission_onboarding", true)
            .putString("default_start_tab", "dashboard")
            .commit()
        app.getSharedPreferences("app_tours", 0).edit().clear().commit()
    }

    @After
    fun close() {
        controller?.pause()?.stop()?.destroy()
    }

    private fun launch(intent: Intent = Intent(app, MainActivity::class.java)) {
        controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        activity = controller!!.get()
        notes = ViewModelProvider(activity)[NotesViewModel::class.java]
        compose.waitForIdle()
    }

    private fun send(intent: Intent) {
        controller!!.newIntent(intent)
        compose.waitForIdle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
    }

    private fun awaitSwitch(ready: () -> Boolean) {
        try {
            compose.waitUntil(30_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                compose.onAllNodesWithTag("tab_empty").fetchSemanticsNodes()
                ready()
            }
        } catch (failure: Throwable) {
            throw AssertionError(
                "Editor=${notes.editingState.value}; save=${notes.saveStatus.value}; pendingId=${activity.viewModel.pendingOpenNoteId.value}; pendingNew=${activity.viewModel.pendingOpenNewNote.value}; toast=${org.robolectric.shadows.ShadowToast.getTextOfLatestToast()}",
                failure,
            )
        }
    }

    @Test
    fun theFourthTabOpensScreenTimeInsteadOfABlankPage() {
        launch()
        compose.onNodeWithTag("tab_time").assertIsDisplayed().performClick()
        compose.onNodeWithText("Screen Time", substring = false).assertIsDisplayed()
        compose.onNodeWithTag("tab_empty_tab").assertDoesNotExist()
    }

    @Test
    fun privateWindowProtectionLastsUntilTheOutgoingScreenIsGone() {
        app.getSharedPreferences("focus_prefs", 0)
            .edit()
            .putBoolean("secure_recents", false)
            .commit()
        launch()
        send(Intent().putExtra("navigate_to", "empty"))
        fun protected() =
            activity.window.attributes.flags and
                android.view.WindowManager.LayoutParams.FLAG_SECURE != 0
        assertTrue(protected())
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { activity.viewModel.triggerNavigation("account") }
        compose.mainClock.advanceTimeBy(64)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(34))
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull(activity.viewModel.pendingNavigationRoute.value)
            assertTrue("Outgoing Notes must stay protected during navigation", protected())
        }
        compose.mainClock.advanceTimeBy(1000)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(34))
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(
                "Public Profile can follow the user's screenshot preference after transition",
                protected(),
            )
        }
    }

    @Test
    fun screenTimeIsHonouredAsTheLaunchPreference() {
        app.getSharedPreferences("focus_app_prefs", 0)
            .edit()
            .putString("default_start_tab", "time")
            .commit()
        launch()
        compose.onNodeWithText("Screen Time", substring = false).assertIsDisplayed()
    }

    @Test
    fun aHabitShortcutWorksWhileThePageIsAlreadyOpenAndDoesNotLeakIntoTasks() {
        launch()
        send(Intent().putExtra("navigate_to", "habits"))
        send(Intent().putExtra("navigate_to", "habits").putExtra("open_add_dialog", true))
        compose.onNodeWithText("New Habit").assertIsDisplayed()
        compose.runOnIdle { assertNull(activity.viewModel.pendingOpenAddDialog.value) }
        activity.onBackPressedDispatcher.onBackPressed()
        settle()
        send(Intent().putExtra("navigate_to", "todos"))
        settle()
        compose.onNodeWithText("New Task").assertDoesNotExist()
        send(Intent().putExtra("navigate_to", "habits").putExtra("open_add_dialog", true))
        settle()
        compose.onNodeWithText("New Habit").assertIsDisplayed()
    }

    @Test
    fun rotatingAfterANoteShortcutDoesNotReplaceTheDraftWithABlankNote() {
        launch(Intent(app, MainActivity::class.java).putExtra("open_new_note", true))
        compose.waitUntil(10_000) { notes.editingState.value != null }
        lateinit var session: String
        compose.runOnIdle {
            notes.updateEditorTitle("Keep my draft")
            session = notes.editingState.value!!.sessionId
        }
        controller!!.configurationChange(
            android.content.res.Configuration(app.resources.configuration).apply {
                orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
        )
        activity = controller!!.get()
        notes = ViewModelProvider(activity)[NotesViewModel::class.java]
        compose.runOnIdle {
            assertEquals(session, notes.editingState.value!!.sessionId)
            assertEquals("Keep my draft", notes.editingState.value!!.title)
        }
    }

    @Test
    fun aSecondNoteShortcutSavesTheCurrentNoteBeforeSwitching() {
        launch(Intent(app, MainActivity::class.java).putExtra("open_new_note", true))
        compose.waitUntil(10_000) { notes.editingState.value != null }
        lateinit var session: String
        compose.runOnIdle {
            notes.updateEditorTitle("Keep before switching")
            session = notes.editingState.value!!.sessionId
        }
        send(Intent().putExtra("open_new_note", true).putExtra("as_checklist", true))
        awaitSwitch {
            notes.editingState.value?.let { it.sessionId != session && it.isChecklist } == true
        }
        assertTrue(
            runBlocking {
                NoteDatabase.getInstance(app).noteDao().getAllNotesList().any {
                    it.title == "Keep before switching"
                }
            }
        )
    }

    @Test
    fun openingAPinnedNoteKeepsTheCurrentDraftAndReturnsToProfile() {
        val targetId = runBlocking {
            NoteDatabase.getInstance(app)
                .noteDao()
                .insertNote(com.focusbyrj.app.data.note.NoteEntity(title = "Pinned target"))
        }
        launch(Intent(app, MainActivity::class.java).putExtra("open_new_note", true))
        compose.waitUntil(10_000) { notes.editingState.value != null }
        compose.runOnIdle {
            notes.updateEditorTitle("Draft before pinned target")
            activity.viewModel.triggerOpenNote(targetId, returnRoute = "account")
        }
        awaitSwitch { notes.editingState.value?.originalId == targetId }
        assertEquals("account", notes.returnRouteOnClose.value)
        assertTrue(
            runBlocking {
                NoteDatabase.getInstance(app).noteDao().getAllNotesList().any {
                    it.title == "Draft before pinned target"
                }
            }
        )
    }

    @Test
    fun reopeningTheCurrentNoteFromProfileKeepsItsDraftAndReturnDestination() {
        val id = runBlocking {
            NoteDatabase.getInstance(app)
                .noteDao()
                .insertNote(com.focusbyrj.app.data.note.NoteEntity(title = "Current pinned note"))
        }
        launch(Intent(app, MainActivity::class.java).putExtra("open_note_id", id))
        awaitSwitch { notes.editingState.value?.originalId == id }
        val session = notes.editingState.value!!.sessionId
        send(Intent().putExtra("navigate_to", "account"))
        compose.runOnIdle {
            notes.updateEditorTitle("Keep the latest draft")
            activity.viewModel.triggerOpenNote(id, returnRoute = "account")
        }
        awaitSwitch { notes.returnRouteOnClose.value == "account" }
        assertEquals(session, notes.editingState.value!!.sessionId)
        assertEquals("Keep the latest draft", notes.editingState.value!!.title)
    }

    @Test
    fun aLateMicrophoneGrantDoesNotStartRecordingInADifferentNote() {
        org.robolectric.Shadows.shadowOf(app)
            .denyPermissions(android.Manifest.permission.RECORD_AUDIO)
        org.robolectric.shadows.ShadowToast.reset()
        launch(Intent(app, MainActivity::class.java).putExtra("open_voice_note", true))
        val request =
            requireNotNull(org.robolectric.Shadows.shadowOf(activity).lastRequestedPermission) {
                "Voice shortcut must request microphone access"
            }
        assertTrue(request.requestedPermissions.contains(android.Manifest.permission.RECORD_AUDIO))
        send(Intent().putExtra("open_new_note", true))
        compose.waitUntil(10_000) { notes.editingState.value != null }
        val session = notes.editingState.value!!.sessionId
        compose.runOnIdle {
            activity.onRequestPermissionsResult(
                request.requestCode,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                intArrayOf(android.content.pm.PackageManager.PERMISSION_GRANTED),
            )
        }
        assertEquals(session, notes.editingState.value!!.sessionId)
        assertFalse(notes.recordingState.value.isSessionOpen)
        assertEquals(
            "The note changed. Tap record again when ready. Your work is kept.",
            org.robolectric.shadows.ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun anUnknownExternalDestinationFallsBackWithoutCrashing() {
        launch(Intent(app, MainActivity::class.java).putExtra("navigate_to", "unknown-route"))
        compose.onNodeWithTag("tab_dashboard").assertIsDisplayed()
    }
}
