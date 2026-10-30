package com.focusbyrj.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.util.backup.BackupRestoreManager
import com.focusbyrj.app.util.backup.CryptoBackupEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BackupVerificationTest {
    private fun archive(context: Context, missingMedia: Boolean = false): File {
        val f = File.createTempFile("verification", ".runbackup", context.cacheDir)
        val root = JSONObject().put("version", 2).put("tasks", JSONArray().put(JSONObject().put("id", 1).put("title", "Keep")))
            .put("mediaManifest", if (missingMedia) JSONArray().put(JSONObject().put("path", "keep_images/missing.jpg").put("size", 3).put("sha256", "bad")) else JSONArray())
        f.outputStream().use { output -> CryptoBackupEngine.openEncryptingStream(output, "password123".toCharArray()).use { encrypted ->
            ZipOutputStream(encrypted).use { zip -> zip.putNextEntry(ZipEntry("data.json")); zip.write(root.toString().toByteArray()); zip.closeEntry() }
        } }
        return f
    }
    @Test fun verificationReadsWithoutRestoringOrChangingPreferences() = runBlocking {
        val c = ApplicationProvider.getApplicationContext<Context>()
        c.getSharedPreferences("focus_prefs", 0).edit().putString("sentinel", "keep").commit()
        val f = archive(c)
        try {
            val result = BackupRestoreManager.verifyEncryptedBackup(c, Uri.fromFile(f), "password123")
            assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
            assertTrue(result.getOrThrow().contains("tasks: 1"))
            assertEquals("keep", c.getSharedPreferences("focus_prefs", 0).getString("sentinel", null))
            assertFalse(c.cacheDir.listFiles().orEmpty().any { it.name.startsWith("verify_backup_") })
        } finally { f.delete() }
    }
    @Test fun rejectsWrongPasswordTruncationAndMissingAttachments() = runBlocking {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val f = archive(c); val missing = archive(c, true)
        try {
            assertTrue(BackupRestoreManager.verifyEncryptedBackup(c, Uri.fromFile(f), "wrong").isFailure)
            assertTrue(BackupRestoreManager.verifyEncryptedBackup(c, Uri.fromFile(missing), "password123").isFailure)
            val bytes = f.readBytes(); f.writeBytes(bytes.copyOf(bytes.size - 8))
            assertTrue(BackupRestoreManager.verifyEncryptedBackup(c, Uri.fromFile(f), "password123").isFailure)
        } finally { f.delete(); missing.delete() }
    }
}
