package com.focusbyrj.app.util.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupActivityLogTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun successfulRetryKeepsPriorFailureAndHistoryIsBounded() {
        val failed = BackupActivityLog.begin(context, BackupActivityLog.Operation.AUTOMATIC)
        BackupActivityLog.finish(context, failed, BackupActivityLog.Outcome.RETRY_SCHEDULED, BackupActivityLog.Reason.DESTINATION)
        val succeeded = BackupActivityLog.begin(context, BackupActivityLog.Operation.AUTOMATIC)
        BackupActivityLog.finish(context, succeeded, BackupActivityLog.Outcome.COMPLETED)
        assertEquals(
            listOf(BackupActivityLog.Outcome.COMPLETED, BackupActivityLog.Outcome.RETRY_SCHEDULED),
            BackupActivityLog.entries(context).map { it.outcome }
        )

        repeat(60) {
            val id = BackupActivityLog.begin(context, BackupActivityLog.Operation.CHECK)
            BackupActivityLog.finish(context, id, BackupActivityLog.Outcome.COMPLETED)
        }
        assertEquals(60, BackupActivityLog.entries(context).size)
        assertTrue(BackupActivityLog.entries(context).all { it.operation == BackupActivityLog.Operation.CHECK })
    }

    @Test fun finishingActiveWorkDoesNotRecreateClearedHistory() {
        val id = BackupActivityLog.begin(context, BackupActivityLog.Operation.SNAPSHOT_RESTORE)
        BackupActivityLog.clear(context)
        BackupActivityLog.finish(context, id, BackupActivityLog.Outcome.COMPLETED)
        assertTrue(BackupActivityLog.entries(context).isEmpty())
    }
}
