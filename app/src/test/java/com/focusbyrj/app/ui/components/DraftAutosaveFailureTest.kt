package com.focusbyrj.app.ui.components

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.security.MessageDigest

/** The same autosave used by the bubble, task and habit editors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35, 36], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DraftAutosaveFailureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun typingAndDisposingKeepTheLastEncryptedDraftWhenStorageRejectsWrites() {
        val key = "typing-storage-failure"
        val store = EditorDraftStore(app, key).beginEditing()
        store.save(JSONObject().put("text", "Previously saved"))
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(app.filesDir, "editor_drafts/$hash.enc")
        val previous = file.readBytes()
        val blocker = File(file.path + ".new").apply { mkdirs() }
        var visible by mutableStateOf(true)
        var typed by mutableStateOf("Previously saved")
        try {
            compose.setContent { MaterialTheme { if (visible) {
                OutlinedTextField(typed, { typed = it }, label = { androidx.compose.material3.Text("Message") })
                PersistEditorDraft(store, JSONObject().put("text", typed))
            } } }
            compose.onNode(hasSetTextAction()).performTextReplacement("/talk I was typing")
            compose.mainClock.advanceTimeBy(300)
            compose.waitForIdle()
            compose.onNode(hasSetTextAction()).assertTextContains("/talk I was typing")
            assertEquals("Couldn't save your draft. Keep this editor open and try again.", ShadowToast.getTextOfLatestToast())
            assertArrayEquals(previous, file.readBytes())
            compose.runOnIdle { visible = false }
            compose.waitForIdle()
            assertArrayEquals(previous, file.readBytes())
        } finally { blocker.deleteRecursively() }
    }

    @Test fun aLaterEditRetriesAutosaveAfterStorageRecovers() {
        val key = "typing-storage-retry"
        val store = EditorDraftStore(app, key).beginEditing()
        store.save(JSONObject().put("text", "Earlier draft"))
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(app.filesDir, "editor_drafts/$hash.enc")
        val blocker = File(file.path + ".new").apply { mkdirs() }
        var typed by mutableStateOf("Earlier draft")
        try {
            compose.setContent { MaterialTheme {
                OutlinedTextField(typed, { typed = it })
                PersistEditorDraft(store, JSONObject().put("text", typed))
            } }
            compose.onNode(hasSetTextAction()).performTextReplacement("Plain chat typing")
            compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
            assertEquals("Earlier draft", store.read().getString("text"))
            blocker.deleteRecursively()
            compose.onNode(hasSetTextAction()).performTextReplacement("Latest draft after recovery")
            compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
            assertEquals("Latest draft after recovery", store.read().getString("text"))
            assertTrue(com.focusbyrj.app.util.backup.SnapshotCryptoEngine.isEncrypted(file))
        } finally { blocker.deleteRecursively() }
    }
}
