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
        if (!clean && payload != null) {
            val facts = result.optJSONObject("conversationFacts") ?: JSONObject()
            payload.optJSONObject("conversationFacts")?.let { imported -> imported.keys().asSequence().forEach { name ->
                val existing = facts.keys().asSequence().firstOrNull { it.equals(name, true) }
                require(existing == null || facts.getString(existing) == imported.getString(name)) { "Conflicting conversation memory: $name" }
                facts.put(existing ?: name, imported.getString(name))
            } }
            result.put("conversationFacts", facts)
        }
        write(result)
    }
    fun preflight(payload: JSONObject?, clean: Boolean) {
        payload?.let(::validate)
        if (!clean && payload != null) {
            val current = read().optJSONObject("conversationFacts") ?: JSONObject()
            val incoming = payload.optJSONObject("conversationFacts") ?: JSONObject()
            val names = current.keys().asSequence().map { it.lowercase() }.toMutableSet()
            incoming.keys().asSequence().forEach { name ->
                val old = current.keys().asSequence().firstOrNull { it.equals(name, true) }
                require(old == null || current.getString(old) == incoming.getString(name)) { "Conflicting conversation memory: $name" }
                names.add(name.lowercase())
            }
            require(names.size <= 30) { "Combined conversation memory exceeds thirty facts" }
        }
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
    fun rememberFact(name: String, value: String) {
        require(name.isNotBlank() && name.length <= 80 && value.isNotBlank() && value.length <= 500) { "Use a short name and a definition under 500 characters" }
        val root = read(); val facts = root.optJSONObject("conversationFacts") ?: JSONObject()
        val key = facts.keys().asSequence().firstOrNull { it.equals(name, true) } ?: name
        require(!facts.has(key) || facts.getString(key) == value) { "That memory already has a different value. Use ‘correct remembered <name> to <value>’ to change it explicitly" }
        require(facts.has(key) || facts.length() < 30) { "Conversation memory is full. Forget an old fact first" }
        facts.put(key, value); root.put("conversationFacts", facts); write(root)
    }
    fun correctFact(name: String, value: String) {
        require(name.isNotBlank() && name.length <= 80 && value.isNotBlank() && value.length <= 500) { "Use a short name and a definition under 500 characters" }
        val root = read()
        val facts = root.optJSONObject("conversationFacts") ?: throw IllegalArgumentException("There are no saved conversation facts to correct")
        val key = facts.keys().asSequence().firstOrNull { it.equals(name, true) }
            ?: throw IllegalArgumentException("There is no saved fact named ‘$name’. Ask what facts I remember first")
        facts.put(key, value); write(root)
    }
    fun fact(name: String): String? = read().optJSONObject("conversationFacts")?.let { facts ->
        facts.keys().asSequence().firstOrNull { it.equals(name, true) }?.let(facts::getString)
    }
    fun describeFacts(): String = read().optJSONObject("conversationFacts")?.let { facts ->
        facts.keys().asSequence().map { "• $it: ${facts.getString(it)} (you supplied this)" }.joinToString("\n")
    }?.takeIf { it.isNotBlank() } ?: "No conversation facts are explicitly saved."
    fun forgetFact(name: String): Boolean {
        val root = read(); val facts = root.optJSONObject("conversationFacts") ?: return false
        val key = facts.keys().asSequence().firstOrNull { it.equals(name, true) } ?: return false
        facts.remove(key); write(root); return true
    }
    fun forgetFacts() { val root = read(); root.remove("conversationFacts"); write(root) }
    fun saveTemplate(name: String, original: PlanWorkspace) {
        val plan = if (original.trip != null) TripPlanningBridge.promote(original) else original
        val workIds = plan.steps.filter { it.workItem }.map { it.id }.toSet()
        val template = plan.copy(id = UUID.randomUUID().toString(), revision = 1, facts = plan.facts.filterKeys { it == "planning style" },
            steps = plan.steps.filter { it.id in workIds }.map { it.copy(parentId = it.parentId?.takeIf(workIds::contains), prerequisites = it.prerequisites.filter(workIds::contains).toSet(), taskSnapshot = null, taskLinkMissing = false, start = null, sessions = emptyList(), deadline = null, earliestStart = null, protected = false, workState = PlanWorkState.READY, blockReason = null) },
            removed = emptyList(), pending = null, reviewedRevision = null, reviewedAt = 0, reviewProcess = null, savedRevision = null,
            previousDraft = null, savedUndoId = null, undoDrafts = emptyList(), redoDrafts = emptyList(), hypotheticalView = false, selected = null, editFocus = null,
            milestones = plan.milestones.map { it.copy(stepIds = it.stepIds.intersect(workIds), facts = emptyMap(), bindings = emptyMap(), boundDeadline = null) },
            goalDeadline = null, policy = plan.policy.copy(excludedDates = emptySet(), dateBudgets = emptyMap()), dialogue = PlanningDialogueState(), alternative = null, archived = false)
        put("templates", name, template.json())
    }
    fun useTemplate(name: String) = PlanWorkspace.read(get("templates", name)).copy(id = UUID.randomUUID().toString())
    fun saveDiscussion(name: String, state: AssistantSession) = put("discussions", name, if (AssistantDiscussion.hasContext(state)) AssistantDiscussion.archive(state) else if (state.featureState?.optString("activeDialogue") == "reasoning") ConversationReasoning.archive(state) else GeneralConversation.archive(state))
    fun resumeDiscussion(name: String, state: AssistantSession) = AssistantDiscussion.resumeArchive(state, get("discussions", name))
    fun rememberPreferences(plan: PlanWorkspace) {
        val root = read().put("policy", plan.policy.copy(excludedDates = emptySet(), dateBudgets = emptyMap()).json())
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
            root.optJSONObject("conversationFacts")?.let { facts ->
                require(facts.length() <= 30)
                val names = mutableSetOf<String>()
                facts.keys().asSequence().forEach { name ->
                    require(name.isNotBlank() && name.length <= 80 && names.add(name.lowercase()))
                    require(facts.getString(name).length in 1..500)
                }
            }
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
