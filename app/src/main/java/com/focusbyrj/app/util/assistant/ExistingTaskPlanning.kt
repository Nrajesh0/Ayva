package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.planning
import org.json.JSONObject
import java.util.UUID

/** Read-only adoption of canonical rows into a proposal, preserving prerequisite/parent closure. */
internal object ExistingTaskPlanning {
    suspend fun adopt(context: Context, plan: PlanWorkspace, reference: String): PlanWorkspace {
        val app = context.applicationContext as FocusApplication
        val rows = app.database.taskDao().getAllTasksList().filter { !it.isTrashed && it.deletedAt == null }.associateBy { it.id }
        val explicitId = Regex("(?i)^id[: ](\\d+)$").matchEntire(reference.trim())?.groupValues?.get(1)?.toLongOrNull()
        val matches = if (explicitId != null) listOfNotNull(rows[explicitId]) else rows.values.filter { it.title.equals(reference.trim().trim('"'), true) }
        require(matches.size == 1) { "Name one exact saved task, or use ‘id:42’ to identify it. No draft changes were made" }
        val existing = plan.steps.mapNotNull { step -> step.taskSnapshot?.let { TaskJson.decode(JSONObject(it)).id to step } }.toMap()
        val needed = linkedSetOf<Long>(); val pending = java.util.ArrayDeque<Long>(); pending.add(matches.single().id)
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!needed.add(id)) continue
            require(needed.size <= 100) { "That connected task graph exceeds 100 steps" }
            val task = rows[id] ?: throw IllegalArgumentException("A parent or prerequisite is unavailable. Resolve the missing link in the task editor first")
            require(task.title.length <= 200) { "Shorten ‘${task.title.take(80)}’ to 200 characters before using it in a plan" }
            val metadata = task.planning
            require(metadata == null || !metadata.parentMissing && metadata.missingPrerequisites == 0) { "Resolve this task's missing parent or prerequisites in the native editor first" }
            metadata?.let { (it.prerequisiteTaskIds + listOfNotNull(it.parentTaskId)).forEach(pending::add) }
            existing[id]?.let { require(TaskJson.decode(JSONObject(it.taskSnapshot!!)) == task) { "‘${task.title}’ changed outside this draft. Refresh saved tasks before linking more work" } }
        }
        val ids = needed.associateWith { existing[it]?.id ?: UUID.nameUUIDFromBytes("${plan.id}:native:$it".toByteArray(Charsets.UTF_8)).toString() }
        val added = needed.filterNot { it in existing }.map { id ->
            val task = rows.getValue(id); val metadata = task.planning
            PlanStep(id = ids.getValue(id), title = task.title, parentId = metadata?.parentTaskId?.let(ids::getValue),
                prerequisites = metadata?.prerequisiteTaskIds.orEmpty().map(ids::getValue).toSet(),
                minutes = metadata?.minutes, deadline = task.dueDate, start = metadata?.start,
                taskSnapshot = TaskJson.encode(task).toString(), source = "linked saved task", sessions = metadata?.sessions.orEmpty(),
                workReminders = metadata?.workReminders ?: false, afterMinutes = metadata?.afterMinutes ?: 0)
        }
        if (added.isEmpty()) return plan.copy(selected = ids.getValue(matches.single().id))
        return plan.changed(steps = plan.steps + added, selected = ids.getValue(matches.single().id)).also { it.validate() }
    }
}
