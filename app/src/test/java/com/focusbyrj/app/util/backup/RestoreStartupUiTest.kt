package com.focusbyrj.app.util.backup

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.MainActivity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FocusApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RestoreStartupUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun failedStartupRecoveryShowsRetryWithoutOpeningNormalDataScreens() =
        runBlocking<Unit> {
            val app = ApplicationProvider.getApplicationContext<FocusApplication>()
            val journal = File(app.filesDir, "restore_before_state.enc")
            check(!journal.exists())
            // Authenticated but unsupported local journal: recovery must retain it.
            EncryptedSnapshotFile.write(journal, "{\"version\":99}")
            ReflectionHelpers.setField(app, "startupRecoveryPending", true)
            MaintenanceGate.requireRecovery()
            val controller = Robolectric.buildActivity(MainActivity::class.java)
            try {
                val activity = controller.setup().get()
                compose.onNodeWithText("Recovery needs attention").assertIsDisplayed()
                assertThrows(UninitializedPropertyAccessException::class.java) {
                    activity.viewModel
                }
                compose.onNodeWithText("Retry recovery").performClick()
                compose.waitUntil(30_000) {
                    compose
                        .onAllNodesWithText("Recovery could not finish.", substring = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                assertTrue(app.startupRecoveryPending)
                assertTrue(MaintenanceGate.recoveryRequired)
                assertTrue(journal.exists())
                compose.onNodeWithText("Close").assertIsEnabled()
            } finally {
                controller.pause().stop().destroy()
                androidx.core.util.AtomicFile(journal).delete()
                RestoreJournal.consistent(app) { Unit }
                ReflectionHelpers.setField(app, "startupRecoveryPending", false)
            }
        }
}
