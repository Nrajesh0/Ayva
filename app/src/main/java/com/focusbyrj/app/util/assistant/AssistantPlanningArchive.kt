package com.focusbyrj.app.util.assistant

import android.content.Context
import androidx.core.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Plan-only archive adapter. No transcript is interpreted as executable or restorable authority. */
internal object AssistantPlanningArchive {
    private val files = listOf("ayva_plan_workspaces.enc", "ayva_conversation_state.enc", "ayva_saved_plan_history.enc", "ayva_planning_library.enc")
    class Recovery internal constructor(internal val bytes: Map<String, ByteArray?>)

    private fun withoutAuthority(plan: PlanWorkspace) = plan.copy(
        reviewedRevision = null, reviewedAt = 0, reviewProcess = null, reviewDeviceTimezone = null, savedUndoId = null,
        pending = null, previousDraft = null, undoDrafts = emptyList(), redoDrafts = emptyList(), editFocus = null, hypotheticalView = false
    )

    fun export(context: Context): JSONObject {
        val document = ConversationPlanStore(context).document()
        val plans = validateDocument(document).map(::withoutAuthority)
        return document.put("plans", JSONArray(plans.map { it.json() })).put("library", PlanningLibrary(context).export())
    }

    /** Validate before any database or preference is changed. Missing means an older backup. */
    fun preflight(context: Context, payload: JSONObject?, clean: Boolean) {
        PlanningLibrary(context).preflight(payload?.optJSONObject("library"), clean)
        val imported = payload?.let(::validateDocument).orEmpty()
        require(imported.all { it.revision < Int.MAX_VALUE }) { "Plan revision cannot be restored" }
        val existing = if (clean) emptyList() else ConversationPlanStore(context).all()
        require(existing.size + imported.size <= 10) {
            "Merging would exceed ten plan workspaces. Discard an unused workspace or use a replacement restore."
        }
    }

    /** taskIds includes every archived task, including those assigned new IDs by a merge. */
    fun restore(context: Context, payload: JSONObject?, taskIds: Map<Long, Long>, clean: Boolean) {
        preflight(context, payload, clean)
        val store = if (clean) null else ConversationPlanStore(context)
        val existingDocument = store?.document()
        val existing = store?.all().orEmpty().map(::withoutAuthority)
        val idMap = mutableMapOf<String, String>()
        fun remap(step: PlanStep): PlanStep {
            val snapshot = step.taskSnapshot ?: return step
            val original = TaskJson.decode(JSONObject(snapshot))
            val id = taskIds[original.id]
            // Preserve the full expected version: backup must not silently adopt an outside edit.
            return if (id == null || step.taskLinkMissing) step.copy(taskLinkMissing = true)
            else step.copy(taskSnapshot = TaskJson.encode(original.copy(id = id, planningJson = com.focusbyrj.app.data.TaskPlanning.remap(original.planningJson, taskIds))).toString())
        }
        PlanningLibrary(context).preflight(payload?.optJSONObject("library"), clean)
        val imported = payload?.let(::validateDocument).orEmpty().map { original ->
            val id = UUID.randomUUID().toString()
            idMap[original.id] = id // fresh namespace prevents replay of pre-restore receipts
            withoutAuthority(original).copy(id = id, revision = original.revision + 1, savedRevision = null,
                steps = original.steps.map(::remap),
                // A removal whose task is absent from the restored archive is already satisfied.
                removed = original.removed.filter { step ->
                    step.taskSnapshot?.let { taskIds.containsKey(TaskJson.decode(JSONObject(it)).id) } == true && !step.taskLinkMissing
                }.map(::remap))
        }
        val active = existingDocument?.nullableString("active") ?: payload?.nullableString("active")?.let(idMap::get)
        val document = JSONObject().put("version", 1).put("plans", JSONArray((existing + imported).map { it.json() }))
            .put("active", active ?: JSONObject.NULL)
        // Use a fresh store for clean restore so an unreadable old file can still be replaced.
        if (clean) {
            validateDocument(document)
            com.focusbyrj.app.util.backup.EncryptedSnapshotFile.write(File(context.filesDir, files[0]), document.toString())
        } else store!!.replaceDocument(document)
        PlanningLibrary(context).restore(payload?.optJSONObject("library"), clean)
        SavedPlanHistory.clear(context)
        AssistantSessionStore(context).clear()
        ChatModeStore.set(context, ChatMode.CHAT)
    }

    /** Local safety snapshots preserve damaged bytes without claiming they are valid plans. */
    fun localDamagedRecovery(context: Context): JSONObject = JSONObject().put("version", 1).put("files", JSONObject().apply {
        captureRecovery(context).bytes.forEach { (name, bytes) -> put(name, bytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) } ?: JSONObject.NULL) }
    })

    /** Local encrypted bytes only; recovery is never imported from an archive. */
    fun captureRecovery(context: Context): Recovery = Recovery(files.associateWith { name ->
        val file = File(context.filesDir, name)
        val atomic = AtomicFile(file)
        if (!file.exists() && !File(file.path + ".bak").exists()) null
        else atomic.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= if (name == "ayva_saved_plan_history.enc") 9_000_000 else 2_500_000) { "Assistant recovery file is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    })

    fun recover(context: Context, recovery: Recovery) {
        for (name in files) {
            val atomic = AtomicFile(File(context.filesDir, name))
            val bytes = recovery.bytes[name]
            if (bytes == null) { atomic.delete(); continue }
            val out = atomic.startWrite()
            try { out.write(bytes); atomic.finishWrite(out) }
            catch (error: Exception) { atomic.failWrite(out); throw error }
        }
    }
}
