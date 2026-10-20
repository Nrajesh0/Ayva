package com.focusbyrj.app.util.assistant

import android.content.Context
import androidx.room.withTransaction
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.AssistantPlanReceipt
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskPlanning
import com.focusbyrj.app.util.backup.EncryptedSnapshotFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Local, version-guarded native history. Archived conversations never restore write authority. */
internal object SavedPlanHistory {
    private const val NAME = "ayva_saved_plan_history.enc"
    fun clear(context: Context) = androidx.core.util.AtomicFile(File(context.filesDir, NAME)).delete()
    private fun read(context: Context): MutableList<JSONObject> {
        val file = File(context.filesDir, NAME)
        if (!file.exists()) return mutableListOf()
        require(file.length() <= 8_000_000)
        val root = JSONObject(EncryptedSnapshotFile.read(file)); require(root.getInt("version") == 1)
        val a = root.getJSONArray("entries"); require(a.length() <= 200)
        return (0 until a.length()).map { a.getJSONObject(it) }.toMutableList()
    }
    private fun write(context: Context, entries: List<JSONObject>) {
        val kept = entries.takeLast(200).toMutableList()
        fun document() = JSONObject().put("version", 1).put("entries", JSONArray(kept)).toString()
        while (document().toByteArray().size > 8_000_000 && kept.size > 1) kept.removeAt(0)
        val json = document(); require(json.toByteArray().size <= 8_000_000) { "This history entry exceeds local storage limits; use a smaller plan" }
        EncryptedSnapshotFile.write(File(context.filesDir, NAME), json)
    }
    fun confirm(context: Context, receipt: String) {
        val entries = read(context)
        entries.firstOrNull { it.getString("receipt") == receipt }?.put("confirmed", true)
        write(context, entries)
    }
    /** Written inside the task transaction. An entry without its committed receipt is never executable. */
    fun record(context: Context, plan: PlanWorkspace, before: List<Task?>, after: List<Task>, receipt: String) {
        val entries = read(context).filterNot { it.getString("plan") == plan.id && it.optBoolean("undone") }.toMutableList()
        if (entries.any { it.getString("receipt") == receipt }) return
        val links = JSONObject(); plan.steps.forEachIndexed { i, step -> links.put(step.id, after[i].id) }
        val entry = JSONObject().put("plan", plan.id).put("receipt", receipt).put("revision", plan.revision)
            .put("before", JSONArray(before.map { it?.let(TaskJson::encode) ?: JSONObject.NULL }))
            .put("after", JSONArray(after.map(TaskJson::encode))).put("expected", JSONArray(after.map(TaskJson::encode)))
            .put("removed", JSONArray(plan.removed.map { it.json() })).put("links", links)
            .put("undone", false).put("sequence", 0)
        entries.add(entry)
        val samePlan = entries.filter { it.getString("plan") == plan.id }
        val obsolete = samePlan.dropLast(20).toSet()
        write(context, entries.filterNot { it in obsolete })
    }
    private fun rows(a: JSONArray) = (0 until a.length()).map { TaskJson.decode(a.getJSONObject(it)) }
    private fun content(task: Task) = TaskJson.encode(task).apply { remove("updatedAt") }.toString()
    suspend fun canUndo(context: Context, planId: String): Boolean {
        val app = context.applicationContext as FocusApplication
        return read(context).any { it.getString("plan") == planId && !it.optBoolean("undone") && (it.optBoolean("confirmed") || app.database.assistantPlanReceiptDao().get(it.getString("receipt")) != null) }
    }
    suspend fun describe(context: Context, planId: String): String {
        val app = context.applicationContext as FocusApplication
        val entries = read(context).filter { it.getString("plan") == planId && (it.optBoolean("confirmed") || app.database.assistantPlanReceiptDao().get(it.getString("receipt")) != null) }
        return if (entries.isEmpty()) "No local saved-task history for this plan." else entries.joinToString("\n") {
            "Revision ${it.getInt("revision")}: ${it.getJSONArray("after").length()} task change(s)${if (it.optBoolean("undone")) " · undone" else ""}"
        } + "\nUse ‘undo saved changes’ or ‘redo saved changes’. Native edits must still match the expected versions."
    }
    suspend fun move(context: Context, planId: String, redo: Boolean, beforeFileCommit: (() -> Unit)? = null): String {
        val app = context.applicationContext as FocusApplication
        val entries = read(context)
        val eligible = entries.filter { it.getString("plan") == planId && (it.optBoolean("confirmed") || app.database.assistantPlanReceiptDao().get(it.getString("receipt")) != null) }
        val entry = (if (redo) eligible.firstOrNull { it.optBoolean("undone") } else eligible.lastOrNull { !it.optBoolean("undone") })
            ?: return if (redo) "There are no saved plan changes to redo." else "There are no saved plan changes to undo."
        val expected = rows(entry.getJSONArray("expected"))
        val unchanged = expected.indices.map { i -> !entry.getJSONArray("before").isNull(i) &&
            TaskJson.decode(entry.getJSONArray("before").getJSONObject(i)) == TaskJson.decode(entry.getJSONArray("after").getJSONObject(i)) }
        val key = "history:${entry.getString("receipt")}:${entry.getInt("sequence")}:${if (redo) "redo" else "undo"}"
        val hash = MessageDigest.getInstance("SHA-256").digest(entry.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val receipt = app.database.withTransaction {
            val old = app.database.assistantPlanReceiptDao().get(key)
            if (old != null) { require(old.payloadHash == hash); old } else {
                expected.forEachIndexed { i, row -> if (!unchanged[i]) require(app.database.taskDao().getTaskById(row.id) == row) { "‘${row.title}’ changed outside this history. No saved changes were ${if (redo) "redone" else "undone"}." } }
                val desired = if (redo) entry.getJSONArray("after") else entry.getJSONArray("before")
                val now = System.currentTimeMillis()
                val changes = expected.mapIndexed { i, current ->
                    if (unchanged[i]) return@mapIndexed app.database.taskDao().getTaskById(current.id) ?: current
                    val target = if (desired.isNull(i)) current.copy(isTrashed = true, trashedAt = now) else TaskJson.decode(desired.getJSONObject(i))
                    if (target == current) current else target.copy(updatedAt = maxOf(now, current.updatedAt + 1))
                }
                val all = app.database.taskDao().getAllTasksList()
                val byId = changes.associateBy { it.id }
                TaskPlanning.validateGraph(all.map { byId[it.id] ?: it })
                val outside = canonicalWorkSessions(all.filterNot { it.id in byId })
                require(canonicalWorkSessions(changes.filterIndexed { i, row -> !unchanged[i] && row != expected[i] }).none { a -> outside.any { PlanWorkspace.overlaps(a, it) } }) { "Restoring these work times would conflict with other saved work. Nothing changed." }
                changes.filterIndexed { i, row -> !unchanged[i] && row != expected[i] }.forEach { app.database.taskDao().updateTask(it) }
                AssistantPlanReceipt(key, entry.getInt("sequence"), hash, JSONArray(changes.mapIndexed { i, row -> TaskJson.encode(row).put("noEffects", unchanged[i] || row == expected[i]) }).toString(), now)
                    .also { app.database.assistantPlanReceiptDao().insert(it) }
            }
        }
        val changed = rows(JSONArray(receipt.tasksJson)); val byId = changed.associateBy { it.id }
        val writtenRows = changed.filterIndexed { i, _ -> !unchanged[i] }.associateBy { it.id }
        entry.put("expected", JSONArray(changed.map(TaskJson::encode))).put("undone", !redo).put("sequence", entry.getInt("sequence") + 1)
        // Rebase only rows changed by this operation, never adopt an outside native edit.
        entries.filterNot { it === entry }.forEach { item ->
            item.put("expected", JSONArray(rows(item.getJSONArray("expected")).map { guard ->
                val row = writtenRows[guard.id]
                TaskJson.encode(if (row != null && content(row) == content(guard)) row else guard)
            }))
        }
        val store = ConversationPlanStore(context)
        store.all().firstOrNull { it.id == planId }?.let { workspace ->
            val links = entry.getJSONObject("links")
            val steps = workspace.steps.map { step ->
                val id = links.optLong(step.id, 0)
                val row = byId[id] ?: return@map step
                step.copy(taskSnapshot = if (row.isTrashed) null else TaskJson.encode(row).toString(), taskLinkMissing = false)
            }
            val removed = if (!redo) (0 until entry.getJSONArray("removed").length()).mapNotNull { i ->
                val step = PlanStep.read(entry.getJSONArray("removed").getJSONObject(i))
                val id = step.taskSnapshot?.let { TaskJson.decode(JSONObject(it)).id }
                val row = byId[id] ?: return@mapNotNull null
                if (row.isTrashed) null else step.copy(taskSnapshot = TaskJson.encode(row).toString())
            } else emptyList()
            store.put(workspace.copy(revision = workspace.revision + 1, steps = steps,
                removed = (workspace.removed + removed).distinctBy { it.id }, savedRevision = null, savedUndoId = null,
                previousDraft = null, undoDrafts = emptyList(), redoDrafts = emptyList(), pending = null,
                reviewedRevision = null, reviewedAt = 0, reviewProcess = null))
        }
        beforeFileCommit?.invoke() // Crash-window regression: canonical receipt committed, history still at old checkpoint.
        write(context, entries)
        val pending = AssistantPlanStore.retryEffects(context) > 0
        return "${if (redo) "Redid" else "Undid"} saved plan changes. Your draft is retained for review.${if (pending) " Reminder/widget reconciliation needs a retry." else ""}"
    }
}
