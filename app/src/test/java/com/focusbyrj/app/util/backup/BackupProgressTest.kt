package com.focusbyrj.app.util.backup

import java.util.concurrent.Executors
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BackupProgressTest {
    @Test fun dispatchChangesAndNestedOperationsRestoreTheirOwnListener() = runBlocking {
        val messages = mutableListOf<String>()
        withContext(BackupProgress { messages += "outer:$it" }) {
            withContext(Dispatchers.IO) { BackupProgress.report("reading") }
            withContext(Dispatchers.IO + BackupProgress { messages += "inner:$it" }) { BackupProgress.report("checking") }
            BackupProgress.report("saving")
        }
        BackupProgress.report("outside")
        assertEquals(listOf("outer:reading", "inner:checking", "outer:saving"), messages)
    }

    @Test fun interleavedOperationsOnOneThreadCannotShareProgress() = runBlocking {
        val a = mutableListOf<String>(); val b = mutableListOf<String>()
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            listOf(
                async(dispatcher + BackupProgress { a += it }) { BackupProgress.report("a1"); yield(); BackupProgress.report("a2") },
                async(dispatcher + BackupProgress { b += it }) { BackupProgress.report("b1"); yield(); BackupProgress.report("b2") }
            ).awaitAll()
            withContext(dispatcher) { BackupProgress.report("unrelated") }
        }
        assertEquals(listOf("a1", "a2"), a); assertEquals(listOf("b1", "b2"), b)
    }

    @Test fun observerFailureCannotInterruptTheDataOperation() = runBlocking {
        var completed = false
        withContext(BackupProgress { throw IllegalStateException("Synthetic UI observer failure") }) {
            BackupProgress.report("checking")
            completed = true
        }
        assertTrue(completed)
    }
}
