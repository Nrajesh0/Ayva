package com.focusbyrj.app.util.backup

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MaintenanceAdmissionTest {
    @Test
    fun exclusiveSnapshotWaitsForAnAdmittedMultiStoreWrite() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val captured = CompletableDeferred<Unit>()
        val writer =
            launch(Dispatchers.Default) {
                MaintenanceGate.shared {
                    entered.complete(Unit)
                    release.await()
                    // A waiting snapshot cannot reject the second store in the admitted unit.
                    MaintenanceGate.write { assertTrue(MaintenanceGate.requiresDurableCommit) }
                }
            }
        entered.await()
        val snapshot =
            launch(Dispatchers.Default) { MaintenanceGate.exclusively { captured.complete(Unit) } }
        try {
            withTimeout(10_000) { while (!MaintenanceGate.isBusy) yield() }
            assertFalse(captured.isCompleted)
            release.complete(Unit)
            writer.join()
            snapshot.join()
            assertTrue(captured.isCompleted)
        } finally {
            release.complete(Unit)
            writer.cancelAndJoin()
            snapshot.cancelAndJoin()
        }
    }
}
