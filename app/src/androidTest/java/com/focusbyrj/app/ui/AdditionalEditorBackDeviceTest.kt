package com.focusbyrj.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.ui.screens.HabitEditorBottomSheet
import com.focusbyrj.app.ui.screens.notes.KeepNoteEditor
import com.focusbyrj.app.ui.screens.notes.NotesViewModel
import com.focusbyrj.app.widget.QuickEditNoteActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class HabitSheetBackDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun prepare() = prepareKeyboard()

    @Test fun keyboardBackKeepsDraftAndReopeningTheSheetRecoversIt() {
        var open by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (open) HabitEditorBottomSheet(null, onDismiss = { open = false }, onSave = { _, _ -> })
                else Button(onClick = { open = true }) { Text("Reopen habit editor") }
            }
        }
        val field = compose.onAllNodes(hasSetTextAction()).onFirst()
        field.performClick()
        field.performTextClearance()
        field.performTextInput("Device habit draft")
        compose.waitUntil(60_000) { imeVisible() }
        Espresso.pressBack()
        compose.waitUntil(60_000) { !imeVisible() }
        compose.onNodeWithText("Device habit draft").assertIsDisplayed()
        screenshot("habit-sheet-after-keyboard-back.png")
        Espresso.pressBack()
        compose.onNodeWithText("Reopen habit editor").assertIsDisplayed().performClick()
        compose.onNodeWithText("Device habit draft").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MainNoteBackDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun prepare() = prepareKeyboard()

    @Test fun keyboardBackKeepsTheNoteAndSecondBackClosesItsEditor() {
        var open by mutableStateOf(true)
        var note by mutableStateOf(NotesViewModel.EditingNoteState())
        compose.setContent {
            MaterialTheme {
                if (open) KeepNoteEditor(
                    state = note, allLabels = emptyList(),
                    onTitleChange = { note = note.copy(title = it) },
                    onContentChange = { note = note.copy(content = it) },
                    onTogglePin = {}, onColorChange = {}, onToggleChecklistMode = {},
                    onToggleChecklistItem = {}, onUpdateChecklistItemText = { _, _ -> },
                    onRemoveChecklistItem = {}, onMoveChecklistItem = { _, _ -> },
                    onAddImageUri = {}, onAddDrawing = {}, onRemoveImage = {},
                    onAddLabel = {}, onRemoveLabel = {}, onToggleLabel = {},
                    onCreateAndAddLabel = {}, onArchive = {}, onDelete = {},
                    onClose = { open = false }
                ) else Text("Note editor closed")
            }
        }
        val field = compose.onAllNodes(hasSetTextAction()).onFirst()
        field.performClick()
        field.performTextClearance()
        field.performTextInput("Device main note draft")
        compose.waitUntil(60_000) { imeVisible() }
        Espresso.pressBack()
        compose.waitUntil(60_000) { !imeVisible() }
        compose.onNodeWithText("Device main note draft").assertIsDisplayed()
        assertEquals("Device main note draft", note.title)
        screenshot("main-note-after-keyboard-back.png")
        Espresso.pressBack()
        compose.onNodeWithText("Note editor closed").assertIsDisplayed()
        assertEquals("Device main note draft", note.title)
    }
}

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class QuickNoteBackDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<QuickEditNoteActivity>()
    @Before fun prepare() = prepareKeyboard()

    @Test fun keyboardBackKeepsTextAndSecondBackSavesTheNoteBeforeClosing() {
        val activity = compose.activity
        val title = "Device quick note ${System.nanoTime()}"
        val dao = NoteDatabase.getInstance(InstrumentationRegistry.getInstrumentation().targetContext).noteDao()
        try {
            val field = compose.onAllNodes(hasSetTextAction()).onFirst()
            field.performClick()
            field.performTextClearance()
            field.performTextInput(title)
            compose.waitUntil(60_000) { imeVisible() }
            Espresso.pressBack()
            compose.waitUntil(60_000) { !imeVisible() }
            assertFalse(activity.isFinishing)
            compose.onNodeWithText(title).assertIsDisplayed()
            Espresso.pressBackUnconditionally()
            compose.waitUntil(60_000) { activity.isFinishing }
            val saved = runBlocking(Dispatchers.IO) { dao.getAllActiveNotesSync().filter { it.title == title } }
            assertEquals(1, saved.size)
        } finally {
            runBlocking(Dispatchers.IO) {
                val ownNotes = dao.getAllNotesList().filter { it.title == title }
                if (ownNotes.isNotEmpty()) dao.hardDeleteNotesByIds(ownNotes.map { it.id })
            }
        }
    }
}
