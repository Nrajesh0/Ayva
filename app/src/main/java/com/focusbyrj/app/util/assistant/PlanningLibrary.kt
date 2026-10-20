package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.util.backup.EncryptedSnapshotFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Explicitly saved templates, discussion summaries and planning preferences. No action tokens. */
internal class PlanningLibrary(private val context: Context) {
    private val file = File(context.filesDir, "ayva_planning_library.enc")
    private fun empty() = JSONObject().put("version", 1).put("templates", JSONArray()).put("discussions", JSONArray())
    private fun read(): JSONObject = if (!file.exists()) empty() else {
        require(file.length() <= 2_000_000)
        JSONObject(EncryptedSnapshotFile.read(file)).also(::validate)
    }
    private fun write(root: JSONObject) { validate(root); EncryptedSnapshotFile.write(file, root.toString()) }
    fun clear() = androidx.core.util.AtomicFile(file).delete()
    fun export() = read()
    fun restore(payload: JSONObject?, clean: Boolean) {
        payload?.let(::validate)
        val result = if (clean) payload?.let { JSONObject(it.toString()) } ?: empty() else read()
        if (!clean && payload != null) for (kind in listOf("templates", "discussions")) {
            val old = result.getJSONArray(kind); val added = payload.getJSONArray(kind)
            for (i in 0 until added.length()) {
                val item = JSONObject(added.getJSONObject(i).toString()); val original = item.getString("name")
                var name = original; var n = 2
                while ((0 until old.length()).any { old.getJSONObject(it).getString("name").equals(name, true) }) name = "${original.take(65)} ($n)".also { n++ }
                item.put("name", name); old.put(item)
            }
        }
        write(result)
    }
    fun preflight(payload: JSONObject?, clean: Boolean) {
        payload?.let(::validate)
        if (!clean && payload != null) require(read().toString().toByteArray().size + payload.toString().toByteArray().size <= 2_000_000) { "The combined planning library exceeds local storage limits" }
        if (!clean && payload != null) for (kind in listOf("templates", "discussions"))
            require(read().getJSONArray(kind).length() + payload.getJSONArray(kind).length() <= if (kind == "templates") 20 else 10) { "Too many saved $kind to merge; remove an unused entry first" }
    }
    private fun put(kind: String, name: String, value: JSONObject) {
        require(name.isNotBlank() && name.length <= 80) { "Use a name under 80 characters" }
        val root = read(); val a = root.getJSONArray(kind)
        require((0 until a.length()).none { a.getJSONObject(it).getString("name").equals(name, true) }) { "That name is already saved. Delete it explicitly or choose another name" }
        a.put(JSONObject().put("name", name).put("value", value)); write(root)
    }
    fun list(kind: String) = read().getJSONArray(kind).let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("name") } }
    private fun get(kind: String, name: String): JSONObject {
        val a = read().getJSONArray(kind)
        return (0 until a.length()).map { a.getJSONObject(it) }.singleOrNull { it.getString("name").equals(name, true) }?.getJSONObject("value")
            ?: throw IllegalArgumentException("No saved ${kind.removeSuffix("s")} named ‘$name’. Ask to list $kind")
    }
    fun delete(kind: String, name: String) {
        val root = read(); val a = root.getJSONArray(kind)
        val kept = (0 until a.length()).map { a.getJSONObject(it) }.filterNot { it.getString("name").equals(name, true) }
        require(kept.size < a.length()) { "That saved name was not found" }
        root.put(kind, JSONArray(kept)); write(root)
    }
    fun saveTemplate(name: String, plan: PlanWorkspace) {
        val workIds = plan.steps.filter { it.workItem }.map { it.id }.toSet()
        val template = plan.copy(id = UUID.randomUUID().toString(), revision = 1, facts = emptyMap(),
            steps = plan.steps.filter { it.id in workIds }.map { it.copy(parentId = it.parentId?.takeIf(workIds::contains), prerequisites = it.prerequisites.filter(workIds::contains).toSet(), taskSnapshot = null, taskLinkMissing = false, start = null, sessions = emptyList(), deadline = null, earliestStart = null, protected = false) },
            removed = emptyList(), pending = null, reviewedRevision = null, reviewedAt = 0, reviewProcess = null, savedRevision = null,
            previousDraft = null, savedUndoId = null, undoDrafts = emptyList(), redoDrafts = emptyList(), hypotheticalView = false, selected = null, editFocus = null,
            policy = plan.policy.copy(excludedDates = emptySet()))
        put("templates", name, template.json())
    }
    fun useTemplate(name: String) = PlanWorkspace.read(get("templates", name)).copy(id = UUID.randomUUID().toString())
    fun saveDiscussion(name: String, state: AssistantSession) = put("discussions", name, AssistantDiscussion.archive(state))
    fun resumeDiscussion(name: String, state: AssistantSession) = AssistantDiscussion.resumeArchive(state, get("discussions", name))
    fun rememberPreferences(plan: PlanWorkspace) {
        val root = read().put("policy", plan.policy.copy(excludedDates = emptySet()).json())
        plan.facts["availability"]?.takeIf { "daily" in it }?.let { root.put("availability", it) }
        write(root)
    }
    fun setDefaultMinutes(minutes: Int) { require(minutes in 1..1440); write(read().put("defaultMinutes", minutes)) }
    fun defaultMinutes(): Int? = read().nullableLong("defaultMinutes")?.toInt()
    fun newPlan(goal: String, facts: Map<String, String>, domain: String): PlanWorkspace {
        val root = read()
        return PlanWorkspace(goal = goal, domain = domain, facts = root.nullableString("availability")?.let { mapOf("availability" to it) }.orEmpty() + root.nullableLong("defaultMinutes")?.let { mapOf("default task minutes" to it.toString()) }.orEmpty() + facts,
            policy = PlanPolicy.read(root.optJSONObject("policy")))
    }
    fun preferences(): String {
        val root = read(); val policy = root.optJSONObject("policy")?.let(PlanPolicy::read)
        return buildString {
            append("Saved planning preferences:")
            append("\nDefault task duration: ${root.nullableLong("defaultMinutes")?.let { "$it minutes" } ?: "not set"}")
            append("\nUsual availability: ${root.nullableString("availability") ?: "not set"}")
            if (policy != null) append("\nBuffer: ${policy.bufferMinutes} minutes; working days: ${policy.dayLabels}; session limit: ${policy.maximumSession ?: "not set"}; daily minutes: ${policy.dailyMinutes ?: "not set"}")
            append("\n${list("templates").size} templates; ${list("discussions").size} saved discussions. Use ‘forget planning preferences’ or delete a named entry to remove it.")
        }
    }
    fun forgetPreferences() { val root = read(); listOf("policy", "availability", "defaultMinutes").forEach(root::remove); write(root) }
    companion object {
        fun validate(root: JSONObject) {
            require(root.getInt("version") == 1 && root.toString().toByteArray().size <= 2_000_000)
            PlanPolicy.read(root.optJSONObject("policy"))
            root.nullableLong("defaultMinutes")?.let { require(it in 1..1440) }
            root.nullableString("availability")?.let { require(it.length <= 100 && Regex("^\\d{1,2}:00–\\d{1,2}:00 daily.*$").matches(it)) }
            for (kind in listOf("templates", "discussions")) {
                val a = root.getJSONArray(kind); require(a.length() <= if (kind == "templates") 20 else 10)
                val names = mutableSetOf<String>()
                for (i in 0 until a.length()) {
                    val item = a.getJSONObject(i); val name = item.getString("name")
                    require(name.isNotBlank() && name.length <= 80 && names.add(name.lowercase()))
                    if (kind == "templates") {
                        val p = PlanWorkspace.read(item.getJSONObject("value"))
                        require(p.reviewedRevision == null && p.reviewProcess == null && p.savedUndoId == null && p.pending == null && p.removed.isEmpty() && p.undoDrafts.isEmpty() && p.redoDrafts.isEmpty() && p.previousDraft == null)
                        require(p.steps.all { it.taskSnapshot == null && !it.taskLinkMissing && it.start == null && it.deadline == null && it.sessions.isEmpty() })
                    } else AssistantDiscussion.sanitizeArchive(item.getJSONObject("value"))
                }
            }
        }
    }
}
