package com.focusbyrj.app.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class LegacyDialogueCleanupTest {
    @Test fun removesRetiredExtractionAndScratchWithoutTouchingUserFiles() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val history = File(context.filesDir, "chat-history-kept").apply { writeText("keep") }
        val retired = File(context.noBackupFilesDir, "general-native-assets/hash/DICT").apply { mkdirs() }
        File(retired, "public-data").writeText("obsolete")
        val scratch = File(context.cacheDir, "general-native-turn").apply { mkdirs() }
        File(scratch, "temp").writeText("obsolete")
        val otherCache = File(context.cacheDir, "other-cache").apply { writeText("keep") }
        val protectedFolder = File(context.filesDir, "protected-folder").apply { mkdirs() }
        val protectedFile = File(protectedFolder, "user-data").apply { writeText("keep") }
        Files.createSymbolicLink(File(retired, "link").toPath(), protectedFolder.toPath())
        repeat(2) { LegacyDialogueCleanup.remove(context) }
        assertFalse(File(context.noBackupFilesDir, "general-native-assets").exists())
        assertFalse(scratch.exists())
        assertEquals("keep", history.readText())
        assertEquals("keep", otherCache.readText())
        assertEquals("keep", protectedFile.readText())
    }
}
