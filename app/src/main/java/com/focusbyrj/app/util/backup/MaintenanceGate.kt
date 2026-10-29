package com.focusbyrj.app.util.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Admission barrier shared by SQLite writers, including widgets and background workers. */
internal object MaintenanceGate {
    private val monitor = Object()
    private val exclusive = Mutex()
    private val owner = ThreadLocal<Boolean>()
    private val depth = ThreadLocal.withInitial { 0 }
    private var waiting = false
    private val _busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val busy: kotlinx.coroutines.flow.StateFlow<Boolean> = _busy
    val isBusy: Boolean get() = _busy.value
    private var writers = 0
    @Volatile var recoveryRequired = false
        private set

    fun inheritedContext() = owner.asContextElement(owner.get() == true)

    fun enter() {
        if (owner.get() == true) return
        synchronized(monitor) {
            if (depth.get() == 0) {
                if (waiting) throw android.database.sqlite.SQLiteException("Data maintenance is in progress. Your change was not applied; retry when it finishes.")
                check(!recoveryRequired) { "Data recovery is required before saving. Restart Run to recover safely." }
                writers++
            }
            depth.set(depth.get() + 1)
        }
    }
    fun exit() {
        if (owner.get() == true) return
        synchronized(monitor) {
            val next = depth.get() - 1
            check(next >= 0)
            depth.set(next)
            if (next == 0) { writers--; monitor.notifyAll() }
        }
    }
    fun <T> write(block: () -> T): T { enter(); try { return block() } finally { exit() } }
    fun requireRecovery() { recoveryRequired = true }

    suspend fun <T> exclusively(block: suspend () -> T): T = exclusive.withLock {
        withContext(Dispatchers.IO + owner.asContextElement(true)) {
            synchronized(monitor) {
                waiting = true
                _busy.value = true
                try { while (writers > 0) monitor.wait() }
                catch (error: Throwable) { waiting = false; _busy.value = false; monitor.notifyAll(); throw error }
            }
            try { block() }
            finally { synchronized(monitor) { waiting = false; _busy.value = false; monitor.notifyAll() } }
        }
    }
    fun recovered() { recoveryRequired = false }
}
