package com.focusbyrj.app.ui.components

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.util.backup.*
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EditorDraftRecoveryTest {
    @Test
    fun recoveryBlocksRecordWritesButCanStillPreserveAnUncommittedDraftOnShutdown() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = EditorDraftStore(app, "recovery-draft").beginEditing()
        try {
            MaintenanceGate.requireRecovery()
            store.save(JSONObject().put("title", "Synthetic private unfinished edit"))
            assertEquals(
                "Synthetic private unfinished edit",
                EditorDraftStore(app, "recovery-draft").read().getString("title"),
            )
            assertThrows(IllegalStateException::class.java) {
                MaintenanceGate.write { error("Should not reach ordinary writer") }
            }
            val draft =
                File(app.filesDir, "editor_drafts").listFiles()!!.single { file ->
                    runCatching {
                            EncryptedSnapshotFile.read(file)
                                .contains("Synthetic private unfinished edit")
                        }
                        .getOrDefault(false)
                }
            assertFalse(draft.readText().contains("Synthetic private unfinished edit"))
            assertThrows(IllegalArgumentException::class.java) {
                EncryptedSnapshotFile.writeEditorDraft(
                    app,
                    File(app.filesDir, "ordinary-record.enc"),
                    "invalid",
                )
            }
        } finally {
            MaintenanceGate.recovered()
            store.discard()
        }
    }
}
