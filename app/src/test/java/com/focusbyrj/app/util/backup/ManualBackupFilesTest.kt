package com.focusbyrj.app.util.backup

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class ManualBackupFilesTest {
    private val app get() = ApplicationProvider.getApplicationContext<Context>()
    private val folder get() = File(app.noBackupFilesDir, "manual_backup_staging")
    private val metadata = BackupRestoreManager.BackupMetadata(4, 1, "test", 1, 1, 0, 0, 0, 0)
    private val ciphertext = ByteArray(160_000) { (it % 251).toByte() }
    private fun prepared(): PreparedManualBackup {
        folder.mkdirs()
        val id = UUID.randomUUID().toString()
        val file = File(folder, "$id.enc").apply { writeBytes(ciphertext) }
        return PreparedManualBackup(id, file, metadata, file.inputStream().use { digestBackup(it) }, SystemClock.elapsedRealtime())
    }
    @After fun clean() { folder.deleteRecursively() }

    @Test fun copyRequiresExactReadBackEqualityAndKeepsSourceUntilCallerDiscardsIt() = runBlocking {
        val value = prepared(); val output = ByteArrayOutputStream()
        ManualBackupFiles.copyVerified(value, { output }, { output.toByteArray().inputStream() })
        assertArrayEquals(ciphertext, output.toByteArray())
        assertTrue(value.file.exists())
        ManualBackupFiles.discard(value)
        assertFalse(value.file.exists())
    }

    @Test fun corruptTruncatedAndAppendedProviderResultsNeverConfirmSuccess() = runBlocking {
        val value = prepared()
        val variants = listOf(ciphertext.copyOf(ciphertext.size - 1), ciphertext + byteArrayOf(1),
            ciphertext.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() })
        for (bytes in variants) {
            try {
                ManualBackupFiles.copyVerified(value, { ByteArrayOutputStream() }, { bytes.inputStream() })
                fail("Damaged destination was accepted")
            } catch (_: IllegalStateException) { }
            catch (_: IllegalArgumentException) { }
            assertTrue(value.file.exists())
        }
    }

    @Test fun permissionLossAndDiskFullLeaveTheVerifiedSourceAvailableForRetry() = runBlocking {
        val value = prepared()
        for (failure in listOf(SecurityException("permission revoked"), IOException("storage full"))) {
            try {
                ManualBackupFiles.copyVerified(value, { object : OutputStream() {
                    override fun write(b: Int) { throw failure }
                } }, { fail("Must not verify a failed write"); byteArrayOf().inputStream() })
                fail("Failed write was accepted")
            } catch (actual: Exception) { assertEquals(failure.javaClass, actual.javaClass); assertEquals(failure.message, actual.message) }
            assertEquals(value.digest, value.file.inputStream().use { digestBackup(it) })
        }
    }

    @Test fun cancelledCopyDoesNotConfirmSuccessOrDeleteAnotherBackup() = runBlocking {
        val value = prepared()
        val existing = File(app.filesDir, "existing.focusbackup").apply { writeText("keep") }
        try {
            ManualBackupFiles.copyVerified(value, { object : OutputStream() {
                override fun write(b: Int) { throw CancellationException("cancelled") }
            } }, { fail("Must not verify cancellation"); byteArrayOf().inputStream() })
            fail("Cancellation was ignored")
        } catch (_: CancellationException) { }
        assertEquals("keep", existing.readText())
        assertTrue(value.file.exists())
    }

    @Test fun expiredOrChangedStagesAreRejectedBeforeOpeningTheDestination() = runBlocking {
        val value = prepared()
        for (stage in listOf(value.copy(preparedAt = SystemClock.elapsedRealtime() - ManualBackupFiles.LIFETIME_MS), value)) {
            if (stage === value) value.file.appendText("changed")
            try {
                ManualBackupFiles.copyVerified(stage, { fail("Invalid source must not open destination"); ByteArrayOutputStream() }, { byteArrayOf().inputStream() })
                fail("Invalid stage accepted")
            } catch (_: IllegalStateException) { }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun interruptedPreparationRemovesItsOwnPartialCiphertext() = runBlocking {
        var target: File? = null
        val creator: suspend (Context, Uri, String, ByteArray?) -> Result<BackupRestoreManager.BackupMetadata> = { _, uri, _, _ ->
            target = File(uri.path!!).apply { writeText("partial encrypted output") }
            throw CancellationException("backgrounded")
        }
        try { ManualBackupFiles.prepare(app, "long-test-password", create = creator); fail("Cancellation ignored") }
        catch (_: CancellationException) { }
        assertTrue(folder.listFiles().orEmpty().isEmpty())
        assertOrphanCanBePruned(target!!)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun cancellationBetweenIoCompletionAndCallerDispatchCannotLeakThePreparedFile() {
        val callerScheduler = TestCoroutineScheduler()
        val ioScheduler = TestCoroutineScheduler()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(callerScheduler))
        var target: File? = null
        var delivered: PreparedManualBackup? = null
        try {
            val job = scope.launch {
                delivered = ManualBackupFiles.prepare(app, "long-test-password", ioDispatcher = StandardTestDispatcher(ioScheduler),
                    create = { _, uri, _, _ ->
                        target = File(uri.path!!).apply { writeBytes(ciphertext) }
                        Result.success(metadata)
                    })
            }
            callerScheduler.runCurrent()
            ioScheduler.runCurrent() // IO completed, but the caller has not received ownership.
            assertTrue(target!!.exists())
            assertNull(delivered)
            job.cancel()
            callerScheduler.runCurrent()
            assertTrue(job.isCancelled)
            assertNull(delivered)
            assertFalse(target!!.exists())
            assertOrphanCanBePruned(target!!)
        } finally { scope.cancel() }
    }

    private fun assertOrphanCanBePruned(file: File) {
        file.writeBytes(ciphertext)
        assertTrue(file.setLastModified(1000))
        ManualBackupFiles.prune(app, ManualBackupFiles.LIFETIME_MS + 2000)
        assertFalse("Discard/cancellation must release ownership for orphan cleanup", file.exists())
    }

    @Test fun cleanupPreservesAnActivePreparationAndItsNewReadyLeaseAfterSlowVerification() = runBlocking {
        val value = ManualBackupFiles.prepare(app, "long-test-password", create = { _, uri, _, _ ->
            val file = File(uri.path!!).apply {
                writeBytes(ciphertext)
                assertTrue(setLastModified(System.currentTimeMillis() - ManualBackupFiles.LIFETIME_MS - 2000))
            }
            ManualBackupFiles.prune(app) // Verification can still be using this completed output.
            assertTrue(file.exists())
            Result.success(metadata)
        })
        try {
            assertFalse(ManualBackupFiles.expired(value))
            ManualBackupFiles.prune(app)
            assertTrue(value.file.exists())
            val output = ByteArrayOutputStream()
            ManualBackupFiles.copyVerified(value, { output }, { output.toByteArray().inputStream() })
            assertArrayEquals(ciphertext, output.toByteArray())
        } finally { ManualBackupFiles.discard(value) }
        assertOrphanCanBePruned(value.file)
    }

    @Test fun clockChangesCannotDeleteAnOwnedReadyOrSavingStage() = runBlocking {
        val value = ManualBackupFiles.prepare(app, "long-test-password", create = { _, uri, _, _ ->
            File(uri.path!!).writeBytes(ciphertext)
            Result.success(metadata)
        })
        val wallTime = System.currentTimeMillis()
        fun pruneAcrossClockChanges() {
            ManualBackupFiles.prune(app, wallTime + ManualBackupFiles.LIFETIME_MS + 2000)
            ManualBackupFiles.prune(app, wallTime - ManualBackupFiles.LIFETIME_MS - 2000)
            assertTrue(value.file.exists())
            assertFalse(ManualBackupFiles.expired(value))
        }
        try {
            pruneAcrossClockChanges()
            val output = ByteArrayOutputStream()
            ManualBackupFiles.copyVerified(value,
                { pruneAcrossClockChanges(); output },
                { pruneAcrossClockChanges(); output.toByteArray().inputStream() })
            assertArrayEquals(ciphertext, output.toByteArray())
        } finally { ManualBackupFiles.discard(value) }
        assertOrphanCanBePruned(value.file)
    }

    @Test fun orphanCleanupOnlyRemovesExpiredAppOwnedStageFiles() {
        folder.mkdirs()
        val old = File(folder, "${UUID.randomUUID()}.enc").apply { writeText("expired"); setLastModified(1000) }
        val fresh = File(folder, "${UUID.randomUUID()}.enc").apply { writeText("active"); setLastModified(ManualBackupFiles.LIFETIME_MS + 1500) }
        val unrelated = File(folder, "keep.txt").apply { writeText("user file"); setLastModified(1000) }
        ManualBackupFiles.prune(app, ManualBackupFiles.LIFETIME_MS + 2000)
        assertFalse(old.exists()); assertTrue(fresh.exists()); assertTrue(unrelated.exists())
    }
}
