package com.focusbyrj.app.data

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.util.backup.RestoreJournal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keep answer writes pending until acknowledgement, and retry before settling a drill. */
internal class VocabQuizWriter(context: Context) {
    private val app = context.applicationContext as FocusApplication
    private data class Answer(val type: String, val id: Int, val correct: Boolean)
    private val pending = java.util.concurrent.ConcurrentLinkedQueue<Answer>()
    private val mutex = Mutex()

    fun enqueue(type: String, id: Int, correct: Boolean) {
        pending.add(Answer(type, id, correct))
    }

    suspend fun flush(): Result<Unit> = try {
        mutex.withLock {
            while (true) {
                val answer = pending.peek() ?: break
                // Admission happens before Room dispatch, so a queued answer never
                // blocks a Room executor needed by an ongoing recovery/settlement.
                RestoreJournal.consistent(app) {
                    app.vocabRepository.recordQuizResult(answer.type, answer.id, answer.correct)
                }
                pending.remove()
            }
        }
        Result.success(Unit)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        Result.failure(error)
    }
}
