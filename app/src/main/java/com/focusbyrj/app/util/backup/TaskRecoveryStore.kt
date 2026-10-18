package com.focusbyrj.app.util.backup

import android.content.Context
import androidx.room.withTransaction
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.TaskReminderHelper
import com.focusbyrj.app.util.assistant.TaskJson
import com.focusbyrj.app.widget.TodoWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Selected-task recovery only: no Notes database, vault state or all-data backup dependency. */
internal object TaskRecoveryStore {
    const val DIRECTORY = "auto_backups/task_only"
    const val SCOPE = "selected_tasks_v1"
    private const val MAX_SNAPSHOTS = 10

    /** Caller holds the task transaction; persist the exact before/after rows before any write. */
    suspend fun write(context: Context, changes: List<Pair<Task, Task>>): File = withContext(Dispatchers.IO) {
        require(changes.size in 1..100 && changes.map { it.first.id }.distinct().size == changes.size)
        require(changes.all { (before, after) -> before.id > 0 && before.id == after.id })
        val directory = File(context.filesDir, DIRECTORY)
        check(directory.isDirectory || directory.mkdirs()) { "Couldn't create task recovery storage" }
        val payload = JSONObject().apply {
            put("scope", SCOPE)
            put("snapshotVersion", 1)
            put("createdAt", System.currentTimeMillis())
            put("taskCount", changes.size)
            put("tasks", JSONArray().apply { changes.forEach { put(TaskJson.encode(it.first)) } })
            put("expectedTasks", JSONArray().apply { changes.forEach { put(TaskJson.encode(it.second)) } })
        }
        val file = File(directory, "tasks_${System.currentTimeMillis()}_${UUID.randomUUID()}.json")
        require(payload.toString().toByteArray(Charsets.UTF_8).size <= 7_900_000) { "Task recovery payload is too large" }
        EncryptedSnapshotFile.write(file, payload.toString())
        // Verify the durable payload before permitting destructive changes.
        check(EncryptedSnapshotFile.read(file) == payload.toString()) { "Task recovery verification failed" }
        directory.listFiles { candidate -> candidate.isFile && candidate.name.endsWith(".json") }
            .orEmpty().filter { it != file }.sortedByDescending { it.lastModified() }
            .drop(MAX_SNAPSHOTS - 1).forEach { it.delete() }
        file
    }

    /** Native recovery screen only; chat exposes no restore command or arbitrary path input. */
    suspend fun restore(context: Context, path: String, database: FocusDatabase): Result<Int> = try {
        val file = File(path).canonicalFile
        require(file.parentFile == File(context.filesDir, DIRECTORY).canonicalFile) { "Not a task recovery file" }
        require(file.length() in 1..8_000_000) { "Invalid task recovery size" }
        require(SnapshotCryptoEngine.isEncrypted(file)) { "Task recovery must be encrypted" }
        val root = JSONObject(withContext(Dispatchers.IO) { EncryptedSnapshotFile.read(file) })
        require(root.getString("scope") == SCOPE && root.getInt("snapshotVersion") == 1)
        val originals = root.getJSONArray("tasks")
        val expected = root.getJSONArray("expectedTasks")
        require(originals.length() in 1..100 && originals.length() == expected.length())
        val records = (0 until originals.length()).map {
            TaskJson.decode(originals.getJSONObject(it)) to TaskJson.decode(expected.getJSONObject(it))
        }
        require(records.map { it.first.id }.distinct().size == records.size)
        require(records.all { it.first.id > 0 && it.first.id == it.second.id })
        val restored = database.withTransaction {
            val changes = records.map { (original, after) ->
                val current = database.taskDao().getTaskById(original.id)
                // A previously restored row or any later edit is never overwritten.
                require(current == after) { "A task changed or was removed. No tasks were restored." }
                current to original.copy(updatedAt = maxOf(System.currentTimeMillis(), current.updatedAt + 1))
            }
            // Retain a task-only rollback point for recovery itself.
            write(context, changes)
            changes.forEach { database.taskDao().updateTask(it.second) }
            changes.map { it.second }
        }
        try {
            restored.forEach {
                TaskReminderHelper.cancelReminder(context, it)
                if (!it.isCompleted && !it.isTrashed) TaskReminderHelper.scheduleReminder(context, it)
            }
            TodoWidgetProvider.updateAllWidgets(context)
        } catch (error: Exception) {
            throw IllegalStateException("Tasks were restored, but reminders or widgets could not be refreshed", error)
        }
        Result.success(restored.size)
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Result.failure(error)
    }
}
