package com.focusbyrj.app.data.note

/** A whole-store restore invalidates existing editors even if imported timestamps collide. */
object NotesStorageVersion {
    private val value = java.util.concurrent.atomic.AtomicLong()
    fun current(): Long = value.get()
    fun replaced() { value.incrementAndGet() }
}
