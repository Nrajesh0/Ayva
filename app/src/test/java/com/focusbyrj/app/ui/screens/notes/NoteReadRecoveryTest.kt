package com.focusbyrj.app.ui.screens.notes

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NoteReadRecoveryTest {
    @Test fun retainsRowsUntilSuccessfulRetry() = runTest {
        val reader = NoteReadMonitor()
        val shown = mutableListOf<List<String>>()
        var attempt = 0
        val subscription = backgroundScope.launch {
            reader.observe {
                flow {
                    if (++attempt == 1) {
                        emit(listOf("Saved note"))
                        throw IOException("read stopped")
                    }
                    emit(listOf("Updated note"))
                }
            }.collect { shown += it }
        }
        runCurrent()
        assertTrue(reader.error.value)
        assertEquals(listOf(listOf("Saved note")), shown)
        reader.retry()
        runCurrent()
        assertFalse(reader.error.value)
        assertEquals(listOf(listOf("Saved note"), listOf("Updated note")), shown)
        subscription.cancel()
    }

    @Test fun initialFailureEmitsNoEmptySuccessAndClearsOnDisposal() = runTest {
        val reader = NoteReadMonitor()
        var emissions = 0
        val subscription = backgroundScope.launch {
            reader.observe<List<String>> { throw IOException("read failed") }.collect { emissions++ }
        }
        runCurrent()
        assertEquals(0, emissions)
        assertTrue(reader.error.value)
        subscription.cancel()
        runCurrent()
        assertFalse(reader.error.value)
    }

    @Test fun downstreamFailureDoesNotRestartTheSource() = runTest {
        val reader = NoteReadMonitor()
        val downstream = IOException("consumer failed")
        var calls = 0
        val result = runCatching {
            reader.observe { calls++; flowOf("Saved note") }.collect { throw downstream }
        }
        assertSame(downstream, result.exceptionOrNull())
        assertEquals(1, calls)
        assertFalse(reader.error.value)
    }
}
