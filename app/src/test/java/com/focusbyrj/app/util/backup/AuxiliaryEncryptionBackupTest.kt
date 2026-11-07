package com.focusbyrj.app.util.backup

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.CompletedTaskHistoryManager
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34], application = FocusApplication::class)
class AuxiliaryEncryptionBackupTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusApplication>()

    @Test fun completedHistorySurvivesVerifiedPortableBackupAndIsReencryptedOnRestore() = runBlocking {
        val prefs = app.getSharedPreferences(CompletedTaskHistoryManager.PREFS_NAME, 0)
        prefs.edit().clear().commit()
        CompletedTaskHistoryManager.recordCompletedTask(app, Task(id = 74001, title = "SYNTHETIC_PRIVATE_HISTORY"))
        val stored = prefs.getString(CompletedTaskHistoryManager.KEY_HISTORY, null)!!
        assertFalse(stored.contains("SYNTHETIC_PRIVATE_HISTORY"))
        val file = File(app.cacheDir, "history-portable-roundtrip.fbck")
        val uri = Uri.fromFile(file)
        val password = "synthetic-portable-password"
        val exported = BackupRestoreManager.createEncryptedBackup(app, uri, password)
        assertTrue(exported.exceptionOrNull()?.toString(), exported.isSuccess)
        assertFalse(file.readBytes().decodeToString().contains("SYNTHETIC_PRIVATE_HISTORY"))
        val verified = BackupRestoreManager.verifyEncryptedBackup(app, uri, password)
        assertTrue(verified.exceptionOrNull()?.toString(), verified.isSuccess)
        prefs.edit().clear().commit()
        val restored = BackupRestoreManager.restoreEncryptedBackup(app, uri, password, cleanRestore = true)
        assertTrue(restored.exceptionOrNull()?.toString(), restored.isSuccess)
        assertTrue(CompletedTaskHistoryManager.getTodayCompletedTasks(app).any { it.id == 74001L && it.title == "SYNTHETIC_PRIVATE_HISTORY" })
        assertNotEquals("Portable restore must install fresh device-bound ciphertext", stored, prefs.getString(CompletedTaskHistoryManager.KEY_HISTORY, null))
    }

    @Test fun damagedHistoryCannotBeSilentlyOverwrittenByANewRecordOrExport() = runBlocking {
        val prefs = app.getSharedPreferences(CompletedTaskHistoryManager.PREFS_NAME, 0)
        val damaged = "AYH1:invalid_authenticated_container"
        prefs.edit().putString(CompletedTaskHistoryManager.KEY_HISTORY, damaged).commit()
        CompletedTaskHistoryManager.recordCompletedTask(app, Task(id = 74002, title = "Synthetic new completion"))
        assertEquals(damaged, prefs.getString(CompletedTaskHistoryManager.KEY_HISTORY, null))
        val file = File(app.cacheDir, "damaged-history.fbck")
        val result = BackupRestoreManager.createEncryptedBackup(app, Uri.fromFile(file), "synthetic-portable-password")
        assertTrue("A full backup must not silently omit unreadable history", result.isFailure)
        assertEquals(damaged, prefs.getString(CompletedTaskHistoryManager.KEY_HISTORY, null))
    }
    @Test fun currentArchiveWithoutHistoryPreservesMergeHistoryAndClearsItForCleanRestore() = runBlocking {
        val prefs = app.getSharedPreferences(CompletedTaskHistoryManager.PREFS_NAME, 0)
        prefs.edit().clear().commit()
        val archive = File(app.cacheDir, "history-optional-current.fbck")
        val password = "synthetic-current-password"
        BackupRestoreManager.createEncryptedBackup(app, Uri.fromFile(archive), password).getOrThrow()
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zip ->
            java.util.zip.ZipInputStream(CryptoBackupEngine.openDecryptingStream(archive.inputStream(), password.toCharArray())).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val raw = input.readBytes()
                    val value = if (entry.name == "data.json") {
                        org.json.JSONObject(raw.decodeToString()).apply {
                            getJSONObject("preferences").remove(CompletedTaskHistoryManager.PREFS_NAME)
                            optJSONObject("preferenceTypes")?.remove(CompletedTaskHistoryManager.PREFS_NAME)
                        }.toString().toByteArray()
                    } else raw
                    zip.putNextEntry(java.util.zip.ZipEntry(entry.name)); zip.write(value); zip.closeEntry()
                }
            }
        }
        archive.outputStream().use { CryptoBackupEngine.encrypt(output.toByteArray(), password.toCharArray(), it) }
        CompletedTaskHistoryManager.recordCompletedTask(app, Task(id = 74003, title = "Synthetic local history"))
        BackupRestoreManager.restoreEncryptedBackup(app, Uri.fromFile(archive), password, cleanRestore = false).getOrThrow()
        assertTrue(CompletedTaskHistoryManager.getTodayCompletedTasks(app).any { it.id == 74003L })
        BackupRestoreManager.restoreEncryptedBackup(app, Uri.fromFile(archive), password, cleanRestore = true).getOrThrow()
        assertTrue(CompletedTaskHistoryManager.getTodayCompletedTasks(app).isEmpty())
    }

}
