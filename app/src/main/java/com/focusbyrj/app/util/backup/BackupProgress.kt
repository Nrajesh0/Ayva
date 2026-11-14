package com.focusbyrj.app.util.backup

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement

/** Operation-local, non-secret status. It follows IO dispatches and never enters saved state. */
internal class BackupProgress(private val listener: (String) -> Unit) :
    ThreadContextElement<((String) -> Unit)?>, AbstractCoroutineContextElement(Key) {
    private var phase = "starting"
    private var phaseStarted = System.nanoTime()
    private val measuredListener: (String) -> Unit = { message ->
        if (com.focusbyrj.app.BuildConfig.DEBUG) {
            val next = when {
                "password" in message || "key" in message -> "credential"
                "attachment" in message -> "attachments"
                "Verif" in message || "Checking" in message -> "verification"
                "recovery" in message || "Recover" in message -> "recovery"
                "Restoring" in message -> "apply"
                "Saving" in message || "Encrypting" in message -> "write"
                else -> "prepare"
            }
            synchronized(this) {
                if (next != phase) {
                    val now = System.nanoTime()
                    // Fixed phase codes and elapsed milliseconds only. Never log the message,
                    // URI, file name, note content, PIN or credential.
                    runCatching { android.util.Log.d("BackupTiming", "$phase: ${(now - phaseStarted) / 1_000_000}ms") }
                    phase = next; phaseStarted = now
                }
            }
        }
        listener(message)
    }
    companion object Key : CoroutineContext.Key<BackupProgress> {
        private val current = ThreadLocal<((String) -> Unit)?>()
        fun report(message: String) {
            // A UI observer must never change whether a data operation succeeds.
            try { current.get()?.invoke(message) } catch (_: Exception) { }
        }
    }
    override fun updateThreadContext(context: CoroutineContext): ((String) -> Unit)? =
        current.get().also { current.set(measuredListener) }
    override fun restoreThreadContext(context: CoroutineContext, oldState: ((String) -> Unit)?) {
        if (oldState == null) current.remove() else current.set(oldState)
    }
}
