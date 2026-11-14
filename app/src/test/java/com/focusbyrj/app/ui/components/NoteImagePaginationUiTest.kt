package com.focusbyrj.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.ui.screens.notes.KeepEditorImageCollage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FocusApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class NoteImagePaginationUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun inlineArticlePreviewPreservesAspectCaptionAndOriginalOpenAction() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<FocusApplication>()
        val bitmap = android.graphics.Bitmap.createBitmap(600, 300, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        val path = requireNotNull(com.focusbyrj.app.data.note.NoteImageHelper.processAndSaveBitmap(app, bitmap))
        var opened = false
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    com.focusbyrj.app.ui.screens.notes.NotesnookImageBlockWidget(
                        block = com.focusbyrj.app.ui.screens.notes.NotesnookBlock.Image(uri = path, caption = "Saved caption"),
                        onUpdate = {}, onDelete = {}, onClick = { opened = true },
                        isDark = false, noteTextColor = androidx.compose.ui.graphics.Color.Black, isReadOnly = true
                    )
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Saved caption").fetchSemanticsNodes().any { it.size.height > 100 }
        }
        compose.onNodeWithText("Retry image").assertDoesNotExist()
        val node = compose.onNodeWithContentDescription("Saved caption").fetchSemanticsNode()
        org.junit.Assert.assertEquals(2f, node.size.width.toFloat() / node.size.height, .05f)
        compose.onNodeWithContentDescription("Saved caption").performTouchInput { click(androidx.compose.ui.geometry.Offset(20f, 20f)) }
        compose.runOnIdle { org.junit.Assert.assertTrue(opened) }
        java.io.File(path).delete(); bitmap.recycle()
    }

    @Test fun imagesBeyondTheOldFiftyImageLimitRemainReachable() {
        var selected = ""
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    KeepEditorImageCollage((1..60).map { "/missing/image$it.jpg" }, { selected = it }, {})
                }
            }
        }
        compose.onNodeWithText("1–12 of 60").assertExists()
        repeat(4) { compose.onNodeWithText("Next").performClick() }
        compose.onNodeWithText("49–60 of 60").assertExists()
        compose.onAllNodesWithContentDescription("Note image").onLast().performScrollTo().performTouchInput { click(androidx.compose.ui.geometry.Offset(20f, 20f)) }
        compose.runOnIdle { assertEquals("/missing/image60.jpg", selected) }
    }
}
