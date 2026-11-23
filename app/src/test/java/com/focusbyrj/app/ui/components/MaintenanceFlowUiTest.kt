package com.focusbyrj.app.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.MainActivity
import com.focusbyrj.app.util.backup.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FocusApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaintenanceFlowUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun inSessionRecoveryFailureBlocksOrdinaryScreensAndRetryKeepsTheJournal() {
        val app = ApplicationProvider.getApplicationContext<FocusApplication>()
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val journal = File(app.filesDir, "restore_before_state.enc")
        try {
            controller.setup()
            EncryptedSnapshotFile.write(journal, "{\"version\":99}")
            compose.runOnIdle { MaintenanceGate.requireRecovery() }
            compose.onNodeWithText("Recovery needs attention").assertIsDisplayed()
            compose.onNodeWithText("Retry recovery").performClick()
            compose.waitUntil(10_000) {
                compose
                    .onAllNodesWithText(
                        "Recovery could not finish. Check free storage, then retry."
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithText("Recovery needs attention").assertIsDisplayed()
            assertTrue(journal.exists())
            assertTrue(MaintenanceGate.recoveryRequired)
            compose.onNodeWithText("Close app").assertIsDisplayed()
        } finally {
            journal.delete()
            MaintenanceGate.recovered()
            controller.pause().stop().destroy()
        }
    }
}
