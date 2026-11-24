package com.focusbyrj.app.util.backup

import android.content.Context
import androidx.core.util.AtomicFile
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** Best-effort device-local events. Callers never use history to determine commit or recovery. */
internal object BackupActivityLog {
    enum class Operation(val title: String) {
        AUTOMATIC("Automatic backup"), MANUAL_PREPARE("Prepare manual backup"), MANUAL_SAVE("Save manual backup"),
        CHECK("Check backup"), PORTABLE_RESTORE("Restore encrypted backup"), SNAPSHOT_CREATE("Create safety snapshot"),
        SNAPSHOT_RESTORE("Restore safety snapshot"), TRASH_CLEANUP("Clean expired Trash")
    }

    enum class Outcome(val label: String) {
        RUNNING("In progress"), READY("Ready to save"), COMPLETED("Completed"), WARNING("Completed with a warning"),
        FAILED("Failed"), INTERRUPTED("Interrupted"), RECOVERED("Recovered"), RETRY_SCHEDULED("Retry scheduled")
    }

    enum class Reason(val label: String) {
        KEY("Recovery key"), DESTINATION("Backup location"), ARCHIVE("Encrypted archive"), VERIFY("Verification"),
        BOOKKEEPING("Backup records"), RETENTION("Older backups"), SYSTEM("Android background work"),
        CHECKING("Archive check"), CLEANUP("Device cleanup")
    }

    data class Entry(
        val id: UUID, val process: UUID, val timestamp: Long, val operation: Operation,
        val outcome: Outcome, val reason: Reason? = null
    )

    private const val LIMIT = 60
    private const val MAX_BYTES = 64 * 1024
    private val process = UUID.randomUUID()
    private val active = mutableSetOf<UUID>()

    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "backup_activity.json"))

    private fun read(context: Context): List<Entry> {
        val bytes = try {
            file(context).openRead().use { input ->
                val buffer = ByteArray(MAX_BYTES + 1)
                var size = 0
                while (size < buffer.size) {
                    val count = input.read(buffer, size, buffer.size - size)
                    if (count < 0) break
                    size += count
                }
                buffer.copyOf(size)
            }
        } catch (_: FileNotFoundException) {
            return emptyList()
        }
        require(bytes.size <= MAX_BYTES) { "Backup activity is too large" }
        val rows = JSONArray(bytes.decodeToString())
        require(rows.length() <= LIMIT)
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            Entry(
                UUID.fromString(row.getString("id")), UUID.fromString(row.getString("process")),
                row.getLong("at"), Operation.valueOf(row.getString("operation")),
                Outcome.valueOf(row.getString("outcome")),
                row.optString("reason").takeIf { it.isNotEmpty() }?.let(Reason::valueOf)
            )
        }
    }

    private fun write(context: Context, rows: List<Entry>) {
        val value = file(context)
        val output = value.startWrite()
        try {
            val json = JSONArray(rows.map { row ->
                JSONObject().put("id", row.id.toString()).put("process", row.process.toString())
                    .put("at", row.timestamp).put("operation", row.operation.name).put("outcome", row.outcome.name)
                    .put("reason", row.reason?.name.orEmpty())
            })
            output.write(json.toString().encodeToByteArray())
            output.fd.sync()
            value.finishWrite(output)
        } catch (error: Throwable) {
            value.failWrite(output)
            throw error
        }
    }

    @Synchronized fun entries(context: Context): List<Entry> = read(context).asReversed().map { row ->
        if (row.outcome == Outcome.RUNNING && (row.process != process || row.id !in active)) {
            row.copy(outcome = Outcome.INTERRUPTED)
        } else row
    }

    // Clearing removes existing entries, including active entries. Finishing work does not add them back.
    @Synchronized fun clear(context: Context) = write(context, emptyList())

    @Synchronized fun begin(context: Context, operation: Operation): UUID? = runCatching {
        val id = UUID.randomUUID()
        val row = Entry(id, process, System.currentTimeMillis(), operation, Outcome.RUNNING)
        write(context, (read(context) + row).takeLast(LIMIT))
        active += id
        id
    }.getOrNull()

    @Synchronized fun finish(context: Context, id: UUID?, outcome: Outcome, reason: Reason? = null) {
        if (id == null) return
        active -= id
        runCatching {
            val rows = read(context)
            if (rows.any { it.id == id && it.outcome == Outcome.RUNNING }) {
                write(context, rows.map {
                    if (it.id == id && it.outcome == Outcome.RUNNING) it.copy(outcome = outcome, reason = reason) else it
                })
            }
        }
    }

    suspend fun <T> track(
        context: Context,
        operation: Operation,
        success: Outcome = Outcome.COMPLETED,
        block: suspend () -> T
    ): T {
        val id = begin(context, operation)
        var outcome = Outcome.FAILED
        try {
            return block().also { outcome = success }
        } catch (error: CancellationException) {
            outcome = Outcome.INTERRUPTED
            throw error
        } finally {
            finish(context, id, outcome)
        }
    }
}
