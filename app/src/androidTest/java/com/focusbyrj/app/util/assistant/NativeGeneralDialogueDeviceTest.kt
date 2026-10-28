package com.focusbyrj.app.util.assistant

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Installed-APK check: extraction, executable permissions, protocol and private scratch cleanup. */
@RunWith(AndroidJUnit4::class)
class NativeGeneralDialogueDeviceTest {
    @Test fun workerRunsFromInstalledLibraryDirectoryWithoutKeepingInput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("what could I try", NativeGeneralDialogue.interpret(context, "Could you possibly recommend something relaxing?"))
        assertEquals("I feel tired", NativeGeneralDialogue.interpret(context, "I feel exhausted"))
        assertNull(NativeGeneralDialogue.interpret(context, "can zebras juggle purple cucumbers"))
        assertNull(NativeGeneralDialogue.interpret(context, "I am not worried about studying"))
        assertFalse(File(context.cacheDir, "general-native-turn").exists())
        val assets = File(context.noBackupFilesDir, "general-native-assets")
        assertTrue(assets.walkTopDown().any { it.name == ".ready" })
        assertFalse(assets.walkTopDown().any { it.isFile && it.name.startsWith("topic_run-transient") })
    }
}
