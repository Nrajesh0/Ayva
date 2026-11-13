package com.focusbyrj.app.util.backup

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement

/** Operation-local, non-secret status. It follows IO dispatches and never enters saved state. */
internal class BackupProgress(private val listener: (String) -> Unit) :
    ThreadContextElement<((String) -> Unit)?>, AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<BackupProgress> {
        private val current = ThreadLocal<((String) -> Unit)?>()
        fun report(message: String) {
            // A UI observer must never change whether a data operation succeeds.
            try { current.get()?.invoke(message) } catch (_: Exception) { }
        }
    }
    override fun updateThreadContext(context: CoroutineContext): ((String) -> Unit)? =
        current.get().also { current.set(listener) }
    override fun restoreThreadContext(context: CoroutineContext, oldState: ((String) -> Unit)?) {
        if (oldState == null) current.remove() else current.set(oldState)
    }
}
