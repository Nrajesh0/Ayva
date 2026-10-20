package com.focusbyrj.app.util.assistant

import android.content.Context
import androidx.room.withTransaction
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.AssistantPlanReceipt
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskPlanning
import com.focusbyrj.app.util.backup.EncryptedSnapshotFile
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Durable workspaces are separate from the short-lived action/confirmation session. */
internal class ConversationPlanStore(private val context: Context) {
    companion object {
        fun clearAll(context: Context) { androidx.core.util.AtomicFile(File(context.filesDir, "ayva_plan_workspaces.enc")).delete(); SavedPlanHistory.clear(context) }
    }
    private val file = File(context.filesDir, "ayva_plan_workspaces.enc")
    private var data: JSONObject = if (file.exists()) {
        require(file.length() <= 2_000_000) { "Plan storage is too large; your existing plans were preserved" }
        JSONObject(EncryptedSnapshotFile.read(file)).also { require(it.getInt("version") == 1) }
    } else JSONObject().put("version", 1).put("plans", JSONArray())

    fun all(): List<PlanWorkspace> = data.getJSONArray("plans").let { a ->
        require(a.length() <= 10); (0 until a.length()).map { PlanWorkspace.read(a.getJSONObject(it)) }
    }
    fun active(): PlanWorkspace? = all().find { it.id == data.optString("active") }
    fun document(): JSONObject = JSONObject(data.toString())
    fun replaceDocument(document: JSONObject) {
        validateDocument(document)
        val text = document.toString()
        require(text.toByteArray(Charsets.UTF_8).size <= 2_000_000)
        EncryptedSnapshotFile.write(file, text)
        data = JSONObject(text)
    }
    fun put(plan: PlanWorkspace) {
        plan.validate()
        val existing = all()
        require(existing.size < 10 || existing.any { it.id == plan.id }) { "You have ten saved plan workspaces. Discard an unused workspace before starting another." }
        data.put("plans", JSONArray((existing.filterNot { it.id == plan.id } + plan).map { it.json() })).put("active", plan.id)
        persist()
    }
    fun remove(id: String) {
        data.put("plans", JSONArray(all().filterNot { it.id == id }.map { it.json() }))
        if (data.optString("active") == id) data.remove("active")
        persist()
    }
    fun clear() { clearAll(context) }
    /** Keep the proposal editable after its canonical writes are undone. */
    suspend fun reconcileUndo(token: String, records: List<JSONObject>) {
        val owner = all().singleOrNull { it.savedUndoId == token } ?: return
        val app = context.applicationContext as FocusApplication
        val byId = records.filter { it.optString("kind") == "task" }.associateBy { it.getJSONObject("after").getLong("id") }
        val steps = owner.steps.map { step ->
            val id = step.taskSnapshot?.let { TaskJson.decode(JSONObject(it)).id } ?: return@map step
            val record = byId[id] ?: return@map step
            step.copy(taskSnapshot = if (record.isNull("before")) null else
                app.database.taskDao().getTaskById(id)?.let { TaskJson.encode(it).toString() })
        }
        val historical = owner.previousDraft?.let { PlanWorkspace.read(JSONObject(it)) }
        val sources = owner.steps + owner.removed + historical?.steps.orEmpty() + historical?.removed.orEmpty()
        val removed = byId.values.filter { !it.isNull("before") && TaskJson.decode(it.getJSONObject("after")).isTrashed }.map { record ->
            val id = record.getJSONObject("after").getLong("id")
            val current = app.database.taskDao().getTaskById(id) ?: error("The restored task is unavailable")
            val original = sources.firstOrNull { it.taskSnapshot?.let { raw -> TaskJson.decode(JSONObject(raw)).id == id } == true }
                ?: PlanStep(title = current.title)
            original.copy(taskSnapshot = TaskJson.encode(current).toString())
        }
        put(owner.copy(revision = owner.revision + 1, steps = steps, removed = removed, savedRevision = null, savedUndoId = null,
            previousDraft = null, reviewedRevision = null, reviewedAt = 0, reviewProcess = null))
    }
    private fun persist() {
        while (data.toString().toByteArray().size > 2_000_000) {
            val plans = all()
            val candidate = plans.firstOrNull { it.undoDrafts.isNotEmpty() || it.redoDrafts.isNotEmpty() || it.previousDraft != null }
                ?: error("Plan storage is too large; discard an unused workspace")
            val trimmed = candidate.copy(undoDrafts = candidate.undoDrafts.drop(1), redoDrafts = candidate.redoDrafts.drop(1), previousDraft = null)
            data.put("plans", JSONArray(plans.map { if (it.id == trimmed.id) trimmed.json() else it.json() }))
        }
        val text = data.toString(); require(text.toByteArray().size <= 2_000_000)
        EncryptedSnapshotFile.write(file, text)
    }
}

internal fun validateDocument(document: JSONObject): List<PlanWorkspace> {
    require(document.getInt("version") == 1) { "Unsupported plan workspace version" }
    require(document.toString().toByteArray(Charsets.UTF_8).size <= 2_000_000) { "Plan storage is too large" }
    val array = document.getJSONArray("plans")
    require(array.length() <= 10) { "A backup can contain at most ten plan workspaces" }
    val plans = (0 until array.length()).map { PlanWorkspace.read(array.getJSONObject(it)) }
    require(plans.map { it.id }.distinct().size == plans.size) { "Duplicate plan workspace IDs" }
    require(document.nullableString("active") == null || plans.any { it.id == document.getString("active") }) { "Unknown active plan" }
    plans.flatMap { it.steps + it.removed }.forEach { step ->
        step.taskSnapshot?.let { require(TaskJson.decode(JSONObject(it)).id > 0) { "Invalid linked task ID" } }
    }
    return plans
}

/** Atomic canonical-task writes; the existing receipt outbox reconciles reminders/widgets. */
internal object ConversationPlanCommitter {
    data class Result(val plan: PlanWorkspace, val before: List<Task?>, val after: List<Task>, val effectsPending: Boolean)
    fun hash(plan: PlanWorkspace): String {
        val j = plan.json().put("version", 1) // Schema transport does not change the content-bound receipt identity.
        listOf("reviewedAt", "reviewedRevision", "process", "savedRevision", "selected", "pending", "previousDraft", "savedUndo", "editFocus", "hypotheticalView", "undoDrafts", "redoDrafts", "reviewDeviceTimezone").forEach(j::remove)
        if (plan.policy == PlanPolicy()) j.remove("policy")
        // IDs/snapshots are reconciliation output, not part of the user's intended content.
        for (key in listOf("steps", "removed")) j.getJSONArray(key).let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).apply {
                remove("task"); remove("taskLinkMissing")
                if (getJSONArray("sessions").length() == 0) remove("sessions")
                if (!optBoolean("workReminders")) remove("workReminders")
                if (isNull("earliestStart")) remove("earliestStart")
                if (!optBoolean("protected")) remove("protected")
                if (optInt("minimumChunk", 15) == 15) remove("minimumChunk")
                if (isNull("maximumEstimate")) remove("maximumEstimate")
                if (optInt("afterMinutes") == 0) remove("afterMinutes")
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(j.toString().toByteArray()).joinToString("") { "%02x".format(it) }
    }
    suspend fun commit(context: Context, plan: PlanWorkspace, now: Long, process: String): Result {
        plan.validate()
        require(!plan.hypotheticalView) { "Return to the active draft before saving" }
        require((plan.steps + plan.removed).none { it.taskLinkMissing }) { "A restored task link is missing. Remove that step or say ‘unlink <step name>’ to keep it as a new draft task, then review again" }
        require(plan.steps.any { it.workItem && !it.completed } || plan.removed.any { it.taskSnapshot != null }) { "Add an active work step before applying; context is already saved in the workspace" }
        require(plan.reviewedRevision == plan.revision && plan.reviewProcess?.startsWith("$process:") == true && now - plan.reviewedAt in 0..900_000L) { "Review the current revision before applying it" }
        require((plan.reviewDeviceTimezone ?: plan.timezone) == java.util.TimeZone.getDefault().id) { "Your time zone changed. Review the plan's times before saving" }
        val app = context.applicationContext as FocusApplication
        val key = "workspace:${plan.id}:${plan.revision}"
        val hash = hash(plan)
        val before = mutableListOf<Task?>()
        val receipt = app.database.withTransaction {
            val previous = app.database.assistantPlanReceiptDao().get(key)
            if (previous != null) {
                require(previous.payloadHash == hash) { "This revision was already saved with different contents" }
                previous
            } else {
                val all = app.database.taskDao().getAllActiveTasksList()
                val result = mutableListOf<Task>()
                val snapshots = plan.steps.mapNotNull { it.taskSnapshot?.let { raw -> TaskJson.decode(JSONObject(raw)) } } + plan.removed.mapNotNull { it.taskSnapshot?.let { raw -> TaskJson.decode(JSONObject(raw)) } }
                for (snapshot in snapshots) require(app.database.taskDao().getTaskById(snapshot.id) == snapshot) {
                    "‘${snapshot.title}’ changed outside this plan. Reopen the saved task and reconcile it before applying; no plan changes were saved."
                }
                val occupied = occupiedWork(context, plan, ConversationPlanStore(context))
                require(plan.steps.none { step -> occupied.any { PlanWorkspace.conflicts(step, it, plan.policy.bufferMinutes) } }) {
                    "A saved task or another draft's work session conflicts with this plan. Reschedule the conflicting step and review again; nothing was saved."
                }
                // Allocate all IDs first: the user may put children/dependents before their parents.
                val allocated = plan.steps.map { step ->
                    val old = step.taskSnapshot?.let { TaskJson.decode(JSONObject(it)) }
                    if (old == null) require(all.none { !it.isCompleted && it.title.equals(step.title, true) }) { "An active task named ‘${step.title}’ already exists. Rename the step or use the existing task." }
                    val candidate = if (old != null && (step.completed || !step.workItem)) old.also { require(step.title == it.title && step.deadline == it.dueDate) { "Read-only context changed; refresh the draft" } } else (old ?: Task(title = step.title, details = "Plan: ${plan.goal}")).copy(
                        title = step.title, dueDate = step.deadline, updatedAt = now)
                    before.add(old)
                    if (old == null) candidate.copy(id = app.database.taskDao().insertTask(candidate)) else candidate
                }
                val taskIds = plan.steps.mapIndexed { i, step -> step.id to allocated[i].id }.toMap()
                for ((index, step) in plan.steps.withIndex()) {
                    val metadata = TaskPlanning(goal = plan.goal, parentTaskId = step.parentId?.let(taskIds::getValue),
                        prerequisiteTaskIds = step.prerequisites.map(taskIds::getValue).toSet(), minutes = step.minutes,
                        start = step.start, timezone = plan.timezone, position = index, sessions = step.sessions, workReminders = step.workReminders, afterMinutes = step.afterMinutes)
                    metadata.validate(allocated[index].id)
                    val contextOnly = step.completed || !step.workItem
                    if (contextOnly) {
                        val old = com.focusbyrj.app.data.TaskPlanning.read(allocated[index].planningJson)
                        require(metadata.parentTaskId == old?.parentTaskId && metadata.prerequisiteTaskIds == old?.prerequisiteTaskIds.orEmpty() && step.workSessions == old?.workSessions.orEmpty()) { "Read-only context links or work changed; refresh the draft" }
                    }
                    val saved = if (contextOnly) allocated[index] else allocated[index].copy(planningJson = metadata.json())
                    if (!contextOnly) app.database.taskDao().updateTask(saved)
                    result.add(saved)
                }
                TaskPlanning.validateGraph(result)
                for (removed in plan.removed) {
                    val old = removed.taskSnapshot?.let { TaskJson.decode(JSONObject(it)) } ?: continue
                    val trashed = old.copy(isTrashed = true, trashedAt = now, updatedAt = now)
                    app.database.taskDao().updateTask(trashed); before.add(old); result.add(trashed)
                }
                SavedPlanHistory.record(context, plan, before, result, key)
                AssistantPlanReceipt(key, plan.revision, hash, JSONArray(result.mapIndexed { i, task -> TaskJson.encode(task).put("noEffects", before[i] == task) }).toString(), now).also { app.database.assistantPlanReceiptDao().insert(it) }
            }
        }
        SavedPlanHistory.confirm(context, key)
        val rows = JSONArray(receipt.tasksJson).let { a -> (0 until a.length()).map { TaskJson.decode(a.getJSONObject(it)) } }
        val saved = plan.copy(steps = plan.steps.mapIndexed { i, step -> step.copy(taskSnapshot = TaskJson.encode(rows[i]).toString()) }, removed = emptyList(),
            savedRevision = plan.revision, undoDrafts = emptyList(), redoDrafts = emptyList(), previousDraft = null, reviewedRevision = null, reviewedAt = 0, reviewProcess = null)
        ConversationPlanStore(context).put(saved)
        val pending = AssistantPlanStore.retryEffects(context) > 0
        return Result(saved, before, rows, pending)
    }
}
