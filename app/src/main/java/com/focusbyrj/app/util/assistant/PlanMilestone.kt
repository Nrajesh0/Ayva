package com.focusbyrj.app.util.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Organizational context only: a milestone is never allocated as a fake native work task. */
internal data class PlanMilestone(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val domain: String = "general",
    val method: String = "",
    val stepIds: Set<String> = emptySet(),
    val facts: Map<String, String> = emptyMap(),
    val bindings: Map<String, String> = emptyMap(), val boundDeadline: Long? = null
) {
    fun json() = JSONObject().put("id", id).put("title", title).put("domain", domain).put("method", method)
        .put("steps", JSONArray(stepIds.toList())).put("facts", JSONObject(facts))
        .apply { if (bindings.isNotEmpty()) put("bindings", JSONObject(bindings)); boundDeadline?.let { put("boundDeadline", it) } }
    fun validate(ids: Set<String>) {
        require(id.length in 1..80 && title.isNotBlank() && title.length <= 200 && domain.length <= 40 && method.length <= 40)
        require(bindings.size <= 1 && bindings.all { (key, value) -> key == "deadline" && value in setOf("shared exam date", "shared event date", "shared arrival requirement") })
        require(boundDeadline == null || boundDeadline > 0)
        require(stepIds.size <= 100 && stepIds.all(ids::contains)) { "A milestone refers to unavailable draft work" }
        require(facts.size <= 20 && facts.all { (key, value) -> key.isNotBlank() && key.length <= 60 && value.isNotBlank() && value.length <= 2000 })
    }
    companion object {
        fun readList(array: JSONArray?): List<PlanMilestone> {
            if (array == null) return emptyList()
            require(array.length() <= 12)
            return (0 until array.length()).map { index ->
                val j = array.getJSONObject(index); val steps = j.getJSONArray("steps"); require(steps.length() <= 100)
                val ids = (0 until steps.length()).map { steps.getString(it) }; require(ids.distinct().size == ids.size)
                val f = j.getJSONObject("facts"); require(f.length() <= 20)
                PlanMilestone(j.getString("id"), j.getString("title"), j.optString("domain", "general"), j.optString("method"), ids.toSet(), f.keys().asSequence().associateWith { f.getString(it) }, j.optJSONObject("bindings")?.let { b -> b.keys().asSequence().associateWith { b.getString(it) } }.orEmpty(), j.nullableLong("boundDeadline"))
            }
        }
        fun reconcile(groups: List<PlanMilestone>, before: List<PlanStep>, after: List<PlanStep>, focus: String?): List<PlanMilestone> {
            val ids = after.map { it.id }.toSet(); val added = ids - before.map { it.id }.toSet() - groups.flatMap { it.stepIds }.toSet()
            return groups.map { it.copy(stepIds = it.stepIds.intersect(ids) + if (it.id == focus) added else emptySet()) }
        }
        fun merged(first: List<PlanMilestone>, other: List<PlanMilestone>, ids: Map<String, String>): List<PlanMilestone> {
            val result = first.toMutableList()
            other.forEach { group ->
                val remapped = group.copy(id = UUID.randomUUID().toString(), stepIds = group.stepIds.map(ids::getValue).toSet())
                val match = result.indexOfFirst { it.title.equals(group.title, true) }
                if (match < 0) result += remapped else {
                    val existing = result[match]
                    require(existing.domain == group.domain && existing.method == group.method && existing.bindings == group.bindings && existing.boundDeadline == group.boundDeadline && group.facts.none { (key, value) -> existing.facts[key]?.let { it != value } == true }) {
                        "The milestones named ‘${group.title}’ have different details. Resolve or rename those before merging."
                    }
                    result[match] = existing.copy(stepIds = existing.stepIds + remapped.stepIds, facts = existing.facts + group.facts)
                }
            }
            require(result.size <= 12) { "The merged plan would exceed twelve milestones" }
            return result
        }
    }
}
