package com.focusbyrj.app.util.assistant

import android.content.Context
import androidx.room.withTransaction
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.AssistantPlanReceipt
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.TaskReminderHelper
import com.focusbyrj.app.widget.TodoWidgetProvider
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Atomic task creation + durable deduplication; reminder effects can be retried after a crash. */
internal object AssistantPlanStore {
    data class Saved(val tasks: List<Task>, val effectsPending: Boolean, val repeated: Boolean) {
        fun undo() = tasks.map { JSONObject().put("kind", "task").put("before", JSONObject.NULL).put("after", TaskJson.encode(it)) }
    }
    // Bind idempotency to concrete task contents. Renewing review/revision after a crash
    // must not turn an already committed plan into a different request.
    fun hash(draft: PlanningDraft): String {
        val payload = JSONArray(draft.tasks().map { TaskJson.encode(it).apply { remove("updatedAt"); remove("id") } }).toString()
        return MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    suspend fun save(context: Context, draft: PlanningDraft, beforeReceipt: (() -> Unit)? = null): Saved {
        require(AssistantContext.live(draft.at) && AssistantContext.live(draft.reviewedAt) && draft.nonce != null && draft.question == "review" && !draft.paused) { "Review this plan again before saving" }
        require(draft.timezone == java.util.TimeZone.getDefault().id) { "The timezone changed. Review the plan again" }
        require(draft.items.isNotEmpty() && draft.items.size <= 20 && draft.items.all { it.title.isNotBlank() && it.title.length <= 200 })
        require(draft.effort == null || draft.budget == null || draft.effort <= draft.budget) { "The stated effort exceeds the available time. Reduce the plan or change the budget first" }
        val app = context.applicationContext as FocusApplication
        val receipts = app.database.assistantPlanReceiptDao()
        var repeated = false
        val receipt = app.database.withTransaction {
            val prior = receipts.get(draft.id)
            if (prior != null) {
                require(prior.payloadHash == hash(draft)) { "This draft was already saved with different contents" }
                repeated = true; prior
            } else {
                val existing = app.database.taskDao().getAllActiveTasksList()
                val tasks = draft.tasks()
                require(existing.none { task -> !task.isCompleted && tasks.any { it.title.equals(task.title, true) } }) { "A task with one of these titles already exists. Change or remove that entry before saving" }
                val created = tasks.map { task -> task.copy(id = app.database.taskDao().insertTask(task)) }
                beforeReceipt?.invoke() // Failure injection at the transaction boundary, used by crash-window regression.
                AssistantPlanReceipt(draft.id, draft.revision, hash(draft), JSONArray(created.map(TaskJson::encode)).toString(), System.currentTimeMillis()).also { receipts.insert(it) }
            }
        }
        val rows = JSONArray(receipt.tasksJson).let { a -> (0 until a.length()).map { TaskJson.decode(a.getJSONObject(it)) } }
        val pending = if (receipt.effectsPending) !effects(context, receipt) else false
        receipts.prune(System.currentTimeMillis() - 86_400_000L)
        return Saved(rows, pending, repeated)
    }

    private suspend fun effects(context: Context, receipt: AssistantPlanReceipt): Boolean {
        val app = context.applicationContext as FocusApplication
        return try {
            val a = JSONArray(receipt.tasksJson)
            for (i in 0 until a.length()) {
                val saved = TaskJson.decode(a.getJSONObject(i))
                val current = app.database.taskDao().getTaskById(saved.id) ?: continue
                // Reconciliation never resurrects a deleted item or overwrites a native edit.
                if (current != saved) continue
                if (current.isCompleted || current.isTrashed || current.deletedAt != null) TaskReminderHelper.cancelReminder(context, current)
                else { TaskReminderHelper.cancelReminder(context, current); TaskReminderHelper.scheduleReminder(context, current) }
            }
            TodoWidgetProvider.updateAllWidgets(context)
            app.database.assistantPlanReceiptDao().finishEffects(receipt.draftId)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }
    suspend fun retryEffects(context: Context): Int {
        val app = context.applicationContext as FocusApplication
        var remaining = 0
        for (receipt in app.database.assistantPlanReceiptDao().pending()) if (!effects(context, receipt)) remaining++
        return remaining
    }
}
