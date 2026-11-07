package com.focusbyrj.app.util

import android.content.Context
import androidx.work.await
import com.focusbyrj.app.service.ReminderReconcileWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Re-arm saved reminders after returning from settings or restarting after permission revocation. */
internal object AlarmPermissionRecovery {
    private val coordinator = AlarmPermissionRecoveryCoordinator()
    suspend fun reconcile(context: Context) = coordinator.reconcile(context)
}

internal class AlarmPermissionRecoveryCoordinator(
    private val enqueue: suspend (Context) -> Unit = { context ->
        ReminderReconcileWorker.enqueue(context, restoreAbsolute = true)
            .await()
    }
) {
    private val mutex = Mutex()
    private var observedAccess: Boolean? = null

    // Bound both lock contention and the cancellable queue wait for goAsync receivers.
    suspend fun reconcile(context: Context): Boolean = withTimeout(8_000L) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val app = context.applicationContext
                val access = PermissionUtils.hasExactAlarmPermission(app)
                if (observedAccess == access) return@withLock false
                // Android kills the process and cancels exact alarms on revocation. The first
                // foreground in every process must restore them, even when access is still denied.
                // Only remember a state after durable enqueue succeeds; failures retry on resume.
                enqueue(app)
                observedAccess = access
                true
            }
        }
    }
}
