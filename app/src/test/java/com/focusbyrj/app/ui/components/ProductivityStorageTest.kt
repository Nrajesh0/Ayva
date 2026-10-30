package com.focusbyrj.app.ui.components

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ProductivityStorageTest {
    @Test fun storageCountsFilesThroughParentAliasesButDoesNotFollowChildLinks() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val root = File(c.cacheDir, "storage-fixture").apply { mkdirs() }
        val alias = File(c.cacheDir, "storage-alias")
        try {
            val inner = File(root, "inner").apply { mkdirs() }; File(inner, "bytes").writeBytes(ByteArray(17))
            Files.createSymbolicLink(File(inner, "loop").toPath(), root.toPath())
            Files.createSymbolicLink(alias.toPath(), root.toPath())
            assertEquals(17L, StorageInventory.size(root))
            assertEquals(17L, StorageInventory.size(File(alias, "inner")))
        } finally { Files.deleteIfExists(alias.toPath()); Files.deleteIfExists(File(root, "inner/loop").toPath()); root.deleteRecursively() }
    }
}
