package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.planning

/** Explicit canonical work intervals only. Deadlines do not occupy time. */
internal fun canonicalWorkSessions(tasks: List<Task>): List<PlanStep> = tasks.mapNotNull { task ->
    if (task.isCompleted || task.isTrashed || task.deletedAt != null) return@mapNotNull null
    val metadata = task.planning ?: return@mapNotNull null
    metadata.start ?: return@mapNotNull null
    PlanStep(id = "task:${task.id}", title = task.title, minutes = metadata.minutes, start = metadata.start, sessions = metadata.sessions, workReminders = metadata.workReminders)
}

internal suspend fun occupiedWork(context: Context, plan: PlanWorkspace, store: ConversationPlanStore): List<PlanStep> {
    val tasks = (context.applicationContext as FocusApplication).database.taskDao().getAllActiveTasksList()
    val own = (plan.steps + plan.removed).mapNotNull { it.taskSnapshot?.let { raw -> TaskJson.decode(org.json.JSONObject(raw)).id } }.toSet()
    val byId = tasks.associateBy { it.id }
    val otherDrafts = store.all().filterNot { it.id == plan.id }.flatMap { workspace -> workspace.steps.filter { step ->
        val snapshot = step.taskSnapshot?.let { TaskJson.decode(org.json.JSONObject(it)) }
        val current = snapshot?.id?.let(byId::get)
        val native = current?.planning
        when {
            snapshot == null || step.taskLinkMissing -> true
            current == null || current.isCompleted || current.isTrashed || current.deletedAt != null -> false
            native == null -> true // a pre-schema-14 workspace still owns its legacy interval
            step.start == native.start && step.minutes == native.minutes && step.sessions == native.sessions -> false
            else -> workspace.revision != workspace.savedRevision // reserve an unsaved proposal, not a stale saved copy
        }
    } }
    return canonicalWorkSessions(tasks.filterNot { it.id in own }) + otherDrafts
}
