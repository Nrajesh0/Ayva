package com.focusbyrj.app.ui.screens.notes

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update

/** Keep the last successful emission downstream while a failed source waits for retry. */
internal class NoteReadMonitor {
    private val retry = MutableStateFlow(0L)
    private val failed = MutableStateFlow(false)
    val error = failed.asStateFlow()

    fun retry() {
        retry.update { it + 1 }
    }

    fun <T> observe(source: () -> Flow<T>): Flow<T> = flow {
        var attempt = retry.value
        try {
            emitAll(flow { emitAll(source()) }
                .onStart { attempt = retry.value }
                .onEach { failed.value = false }
                .retryWhen { cause, _ ->
                    if (cause is CancellationException || cause !is Exception) throw cause
                    failed.value = true
                    retry.first { it != attempt }
                    true
                })
        } finally {
            failed.value = false
        }
    }
}
