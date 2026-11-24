package com.focusbyrj.app.data

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.drill.DrillSessionRepository
import com.focusbyrj.app.data.note.NotesStorageVersion
import com.focusbyrj.app.util.backup.RestoreJournal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keep answer writes pending until acknowledgement, and retry before settling a drill. */
internal class VocabQuizWriter(context: Context) {
    private val app = context.applicationContext as FocusApplication
    class StaleAnswersException : IllegalStateException(
        "Answers from the previous data session were discarded. Restored progress was kept."
    )

    private data class Answer(
        val type: String, val id: Int, val correct: Boolean,
        val notesGeneration: Long, val drillGeneration: Long
    )
    private val pending = java.util.concurrent.ConcurrentLinkedQueue<Answer>()
    private val mutex = Mutex()

    private var discardedStaleAnswers = false

    fun enqueue(type: String, id: Int, correct: Boolean, drillGeneration: Long = DrillSessionRepository.storageGeneration) {
        pending.add(Answer(type, id, correct, NotesStorageVersion.current(), drillGeneration))
    }

    suspend fun flush(): Result<Unit> = try {
        mutex.withLock {
            while (true) {
                val answer = pending.peek() ?: break
                // Admission happens before Room dispatch, so a queued answer never
                // blocks a Room executor needed by an ongoing recovery/settlement.
                RestoreJournal.consistent(app) {
                    if (answer.notesGeneration != NotesStorageVersion.current() ||
                        answer.drillGeneration != DrillSessionRepository.storageGeneration) {
                        discardedStaleAnswers = true
                    } else {
                        app.vocabRepository.recordQuizResult(answer.type, answer.id, answer.correct)
                    }
                }
                pending.remove()
            }
            if (discardedStaleAnswers) {
                discardedStaleAnswers = false
                throw StaleAnswersException()
            }
        }
        Result.success(Unit)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        Result.failure(error)
    }
}
