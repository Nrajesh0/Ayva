package com.focusbyrj.app.ui.screens.notes

import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.focusbyrj.app.FocusApplication
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=FocusApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteEditorRefreshRegressionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun sameSessionAttachmentSurvivesTyping() {
        val state = mutableStateOf(NotesViewModel.EditingNoteState(title="A sample",content="Body"))
        compose.setContent { MaterialTheme {
            KeepNoteEditor(state=state.value, allLabels=emptyList(),onTitleChange={},
                onContentChange={state.value = state.value.copy(content=it)},onTogglePin={},onColorChange={},
                onToggleChecklistMode={},onToggleChecklistItem={},onUpdateChecklistItemText={_,_->},
                onRemoveChecklistItem={},onMoveChecklistItem={_,_->},onAddImageUri={},onAddDrawing={},
                onRemoveImage={},onAddLabel={},onRemoveLabel={},onToggleLabel={},onCreateAndAddLabel={},
                onArchive={},onDelete={},onClose={})
        } }
        compose.onNodeWithTag("editor_content_input").assertExists()
        compose.runOnIdle {
            val blocks = NotesnookBlockManager.parse(state.value.content) +
                NotesnookBlock.Attachment(uri="/notes/review.txt",fileName="review.txt", fileSize="1 KB")
            state.value = state.value.copy(content=NotesnookBlockManager.serialize(blocks))
        }
        compose.onNode(hasText("Body") and hasSetTextAction()).performTextInput(" changed")
        compose.runOnIdle {
            assertTrue(state.value.content.contains("changed"))
            assertTrue(state.value.content.contains("review.txt"))
        }
    }
}
