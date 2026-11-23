package com.focusbyrj.app.ui.components

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EditorDraftHandoverTest {
    private val app
        get() = ApplicationProvider.getApplicationContext<Application>()

    private fun file(key: String) =
        File(
            app.filesDir,
            "editor_drafts/" +
                MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") {
                    "%02x".format(it)
                } +
                ".enc",
        )

    @Test
    fun earlierEncryptedIdentifierIsRetiredOnlyAfterVerifiedReplacement() {
        val old = EditorDraftStore(app, "old-synthetic-task").beginEditing()
        old.save(JSONObject().put("title", "Keep earlier encrypted work"))
        val current =
            EditorDraftStore(app, "stable-synthetic-task", listOf("old-synthetic-task"))
                .beginEditing()
        assertEquals("Keep earlier encrypted work", current.read().getString("title"))
        assertTrue(file("old-synthetic-task").exists())
        current.save(JSONObject().put("title", "Updated safely"))
        assertFalse(file("old-synthetic-task").exists())
        assertEquals("Updated safely", current.read().getString("title"))
        old.save(JSONObject().put("title", "Late old disposal"))
        assertFalse(file("old-synthetic-task").exists())
        assertEquals("Updated safely", current.read().getString("title"))
    }

    @Test
    fun failedNewWriteRetainsTheEarlierEncryptedDraft() {
        val old = EditorDraftStore(app, "old-before-failure").beginEditing()
        old.save(JSONObject().put("title", "Recover this work"))
        val current =
            EditorDraftStore(app, "blocked-new-draft", listOf("old-before-failure")).beginEditing()
        assertEquals("Recover this work", current.read().getString("title"))
        val blocked = file("blocked-new-draft")
        check(blocked.mkdir())
        File(blocked, "occupied").writeText("Synthetic nonempty destination")
        try {
            assertThrows(Exception::class.java) {
                current.save(JSONObject().put("title", "New unsaved changes"))
            }
            assertEquals("Recover this work", old.read().getString("title"))
            assertTrue(file("old-before-failure").isFile)
        } finally {
            blocked.deleteRecursively()
        }
    }

    @Test
    fun multibyteDraftWithinTheBoundIsAuthenticatedAndReadable() {
        val detail = "界".repeat(90_000)
        val store = EditorDraftStore(app, "large-unicode-draft").beginEditing()
        store.save(JSONObject().put("title", "Long draft").put("details", detail))
        assertTrue(file("large-unicode-draft").length() > 260_000)
        assertTrue(file("large-unicode-draft").length() < EditorDraftStore.MAX_ENCRYPTED_BYTES)
        assertEquals(detail, store.read().getString("details"))
        assertFalse(file("large-unicode-draft").readText().contains("Long draft"))
    }
}
