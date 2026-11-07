package com.focusbyrj.app.util

import android.app.AlarmManager
import android.app.Application
import android.content.Intent
import com.focusbyrj.app.service.ReminderClockReceiver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34, 36], application = Application::class)
class AlarmPermissionRecoveryTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun firstForegroundAndEachPermissionTransitionQueueRecoveryWithoutRepeatedReplacement() = runBlocking {
        val modes = mutableListOf<Boolean>()
        val recovery = AlarmPermissionRecoveryCoordinator { modes.add(PermissionUtils.hasExactAlarmPermission(it)) }
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertTrue(recovery.reconcile(app))
        assertFalse(recovery.reconcile(app))
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(recovery.reconcile(app))
        assertFalse(recovery.reconcile(app))
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertTrue(recovery.reconcile(app))
        assertEquals(listOf(false, true, false), modes)
        // A new process has no state marker, even if access remains denied.
        assertTrue(AlarmPermissionRecoveryCoordinator { modes.add(false) }.reconcile(app))
    }

    @Test fun failedOrCancelledEnqueueIsRetriedInsteadOfRememberingSuccess() = runBlocking {
        for (failure in listOf(IllegalStateException("Queue unavailable"), CancellationException("Caller left"))) {
            var attempts = 0
            val recovery = AlarmPermissionRecoveryCoordinator { if (++attempts == 1) throw failure }
            try { recovery.reconcile(app); fail("Must propagate enqueue failure") }
            catch (error: Exception) {
                // Coroutine stack recovery can copy exceptions across dispatcher boundaries.
                assertEquals(failure.javaClass, error.javaClass)
                assertEquals(failure.message, error.message)
            }
            assertTrue(recovery.reconcile(app))
            assertFalse(recovery.reconcile(app))
            assertEquals(2, attempts)
        }
    }

    @Test fun concurrentResumeAndBroadcastDoNotReplaceEachOthersRecovery() = runBlocking {
        var enqueued = 0
        val recovery = AlarmPermissionRecoveryCoordinator { delay(30); enqueued++ }
        val queued = List(8) { async { recovery.reconcile(app) } }.awaitAll()
        assertEquals(1, queued.count { it })
        assertEquals(1, enqueued)
    }

    @Test fun cancellingAContendingObservationDoesNotCancelTheDurableEnqueueOwner() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var enqueued = 0
        val recovery = AlarmPermissionRecoveryCoordinator { enqueued++; entered.complete(Unit); finish.await() }
        val owner = async { recovery.reconcile(app) }
        entered.await()
        try { withTimeout(50L) { recovery.reconcile(app) }; fail("Contending caller must be cancellable") }
        catch (_: TimeoutCancellationException) { }
        assertTrue(owner.isActive)
        finish.complete(Unit)
        assertTrue(owner.await())
        assertFalse(recovery.reconcile(app))
        assertEquals(1, enqueued)
    }

    @Test fun permissionChangingWhileEnqueueingIsRecoveredOnNextObservation() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        var enqueued = 0
        val recovery = AlarmPermissionRecoveryCoordinator {
            if (++enqueued == 1) ShadowAlarmManager.setCanScheduleExactAlarms(true)
        }
        assertTrue(recovery.reconcile(app))
        assertTrue(recovery.reconcile(app))
        assertFalse(recovery.reconcile(app))
        assertEquals(2, enqueued)
    }

    @Test fun protectedGrantBroadcastCanReachThePrivateRecoveryReceiver() {
        val receivers = app.packageManager.queryBroadcastReceivers(
            Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED).setPackage(app.packageName), 0)
        val receiver = receivers.single { it.activityInfo.name == ReminderClockReceiver::class.java.name }
        assertFalse(receiver.activityInfo.exported)
    }
}
