package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.util.AyvaTalkEngine
import org.json.JSONArray
import org.json.JSONObject

/** Bounded propositional reasoning over supplied claims. No world-fact inference or action dispatch. */
internal object ConversationReasoning {
    data class Claim(val text: String, val assumption: Boolean = false)
    data class Rule(val premise: String, val consequence: String)
    data class Frame(val topic: String = "the idea we're discussing", val claims: List<Claim> = emptyList(), val rules: List<Rule> = emptyList()) {
        fun json() = JSONObject().put("version", 1).put("topic", topic).put("claims", JSONArray(claims.map { JSONObject().put("text", it.text).put("assumption", it.assumption) }))
            .put("rules", JSONArray(rules.map { JSONObject().put("if", it.premise).put("then", it.consequence) })).put("at", System.currentTimeMillis())
    }
    data class Result(val frame: Frame, val text: String)
    private fun key(text: String) = ConversationLanguage.space(text).lowercase().trimEnd('.', '!', '?')
    private fun opposite(text: String) = key(text).let { if (it.startsWith("not ")) it.removePrefix("not ") else "not $it" }
    fun read(j: JSONObject?): Frame {
        if (j == null) return Frame()
        require(j.optInt("version") == 1 && j.toString().toByteArray().size <= 16_000)
        val c = j.getJSONArray("claims"); val r = j.getJSONArray("rules")
        require(c.length() <= 24 && r.length() <= 12 && j.getString("topic").length in 1..300)
        fun value(text: String) = text.also { require(it.isNotBlank() && it.length <= 300) }
        return Frame(j.getString("topic"), (0 until c.length()).map { Claim(value(c.getJSONObject(it).getString("text")), c.getJSONObject(it).optBoolean("assumption")) },
            (0 until r.length()).map { Rule(value(r.getJSONObject(it).getString("if")), value(r.getJSONObject(it).getString("then"))) })
    }
    private fun supportingTerms(expression: String, proof: Map<String, String>): List<String>? {
        // A bounded OR of AND terms. Parenthesized/nested Boolean expressions are deliberately not guessed.
        if (ConversationLanguage.unquote(expression) != expression || expression.contains('(') || expression.contains(')')) return listOf(key(expression)).takeIf { it.all(proof::containsKey) }
        val alternatives = expression.split(Regex("(?i)\\s+or\\s+"))
        if (alternatives.size > 4) return null
        return alternatives.map { it.split(Regex("(?i)\\s+and\\s+")).map(::key) }.firstOrNull { terms ->
            terms.size <= 4 && terms.all { it in proof && opposite(it) !in proof }
        }
    }
    private fun closure(frame: Frame): Map<String, String> {
        val proof = linkedMapOf<String, String>()
        frame.claims.forEach { proof[key(it.text)] = "${if (it.assumption) "Assumption" else "You supplied"}: ${it.text}" }
        repeat(8) {
            frame.rules.forEach { rule ->
                val terms = supportingTerms(rule.premise, proof)?.distinct()
                if (proof.size < 48 && terms != null && key(rule.consequence) !in proof)
                    proof[key(rule.consequence)] = "From ‘${rule.premise}’, using your rule ‘if ${rule.premise}, then ${rule.consequence}’. ${terms.mapNotNull(proof::get).joinToString(" ")}".let { trace ->
                        if (trace.length <= 1_800) trace else trace.take(1_770) + " [trace shortened]"
                    }
            }
        }
        return proof
    }
    fun explicit(input: String): Boolean = Regex("(?i)^(?:let'?s reason (?:about|through)|reason about|fact:|we know that |assume |suppose that |if .+ then |what follows|what can we conclude|correct premise |retract premise |retract rule |show (?:the )?premises|clear reasoning|back to reasoning)").containsMatchIn(input)
    fun turn(previous: JSONObject?, input: String): Result? {
        val text = ConversationLanguage.normalize(input).trimEnd('.', '!', '?'); val lower = text.lowercase(); val frame = read(previous)
        Regex("(?i)^(?:let'?s reason (?:about|through)|reason about) (.{1,300})$").matchEntire(text)?.let { return Result(Frame(it.groupValues[1]), "Let's examine ${it.groupValues[1]}. We can separate what you know, what you're assuming and any conditional relationships.") }
        if (lower == "clear reasoning") return Result(Frame(frame.topic), "Cleared this reasoning scratchpad. It hasn't changed a plan or a saved task.")
        if (lower in setOf("show premises", "show the premises", "back to reasoning")) return Result(frame, describe(frame))
        Regex("(?i)^(?:fact:|we know that|assume|suppose that) (.{1,300})$").matchEntire(text)?.let { m ->
            val claim = Claim(m.groupValues[1], lower.startsWith("assume") || lower.startsWith("suppose"))
            val claims = frame.claims.filterNot { key(it.text) == key(claim.text) } + claim
            require(claims.size <= 24) { "This reasoning context has 24 premises. Retract one before adding another." }
            val next = frame.copy(claims = claims)
            return Result(next, "Kept ‘${claim.text}’ as ${if (claim.assumption) "an assumption" else "a fact you supplied"}." + conflict(next))
        }
        Regex("(?i)^if (.{1,300}) then (.{1,300})$").matchEntire(text)?.let { m ->
            val rule = Rule(m.groupValues[1].trimEnd(','), m.groupValues[2]); val rules = (frame.rules + rule).distinct()
            require(rules.size <= 12) { "Keep this context to twelve conditional rules" }
            return Result(frame.copy(rules = rules), "Kept that as a conditional relationship you supplied. It doesn't establish that either part is true.")
        }
        Regex("(?i)^retract rule if (.+?) then (.+)$").matchEntire(text)?.let { m ->
            val kept = frame.rules.filterNot { key(it.premise) == key(m.groupValues[1].trimEnd(',')) && key(it.consequence) == key(m.groupValues[2]) }
            return Result(frame.copy(rules = kept), if (kept.size == frame.rules.size) "That exact rule isn't recorded." else "Retracted the rule. The remaining consequences are recomputed from the other premises and relationships.")
        }
        Regex("(?i)^correct premise (.+?) to (.{1,300})$").matchEntire(text)?.let { m ->
            val old = frame.claims.singleOrNull { key(it.text) == key(m.groupValues[1]) } ?: return Result(frame, "Which recorded premise should I correct? ${describe(frame)}")
            val next = frame.copy(claims = frame.claims.map { if (it == old) it.copy(text = m.groupValues[2]) else it })
            return Result(next, "Corrected that premise. Consequences will be recomputed from the current premises." + conflict(next))
        }
        Regex("(?i)^retract premise (.+)$").matchEntire(text)?.let { m ->
            val kept = frame.claims.filterNot { key(it.text) == key(m.groupValues[1]) }
            return Result(frame.copy(claims = kept), if (kept.size == frame.claims.size) "That exact premise isn't recorded." else "Retracted that premise; consequences depending on it no longer follow unless another recorded premise supports them.")
        }
        val hypothetical = Regex("(?i)^(?:what if|what would change if) (.{1,300})$").matchEntire(text)
        if (hypothetical != null && frame.rules.any { key(it.premise) == key(hypothetical.groupValues[1]) || key(it.consequence) == key(hypothetical.groupValues[1]) }) {
            val temporary = frame.copy(claims = frame.claims + Claim(hypothetical.groupValues[1], true)); val proof = closure(temporary)
            val extra = proof.keys - closure(frame).keys
            return Result(frame, "Under that temporary assumption: ${extra.joinToString("; ").ifBlank { "no new consequence follows from the recorded rules" }}." + conflict(temporary) + " The assumption wasn't saved and no tasks changed.")
        }
        if (lower in setOf("what follows", "what can we conclude", "what follows from that")) {
            val proof = closure(frame); val derived = proof.keys - frame.claims.map { key(it.text) }.toSet()
            return Result(frame, if (conflict(frame).isNotEmpty()) "We need to resolve the conflicting premises before relying on a conclusion.${conflict(frame)}" else
                if (derived.isEmpty()) "No additional conclusion follows from the recorded premises and rules. Missing information stays unknown." else
                    "Using the supplied premises and rules: ${derived.take(8).joinToString("; ")}${if (derived.size > 8) "; ${derived.size - 8} more are available by asking why a particular consequence follows" else ""}. ${if (frame.claims.any { it.assumption }) "These conclusions remain conditional on the assumptions." else "They depend on your supplied facts and relationships."}")
        }
        Regex("(?i)^why (?:would )?(.+)$").matchEntire(text)?.let { m ->
            val proof = closure(frame); val known = proof[key(m.groupValues[1])] ?: return null
            return Result(frame, known + " This is a trace through supplied premises, not independent verification of the claim.")
        }
        return null
    }
    private fun conflict(frame: Frame): String {
        val proof = closure(frame); val collisions = proof.keys.filter { !it.startsWith("not ") && opposite(it) in proof }
        return if (collisions.isEmpty()) "" else " Conflicting statements are recorded about ${collisions.joinToString()}; I won't choose one as true."
    }
    private fun describe(frame: Frame) = "Reasoning about ${frame.topic}:\n" + frame.claims.joinToString("\n") { "• ${if (it.assumption) "Assumption" else "You supplied"}: ${it.text}" } +
        frame.rules.joinToString("", prefix = "\n") { "• If ${it.premise}, then ${it.consequence}\n" }
    fun merge(a: JSONObject?, b: JSONObject?): JSONObject? {
        if (a == null) return b?.let { read(it).json() }; if (b == null) return read(a).json()
        val first = read(a); val second = read(b)
        return first.copy(claims = (first.claims + second.claims).distinct(), rules = (first.rules + second.rules).distinct()).json().also { read(it) }
    }
    fun general(context: Context, state: AssistantSession, input: String): AyvaTalkEngine.TalkResponse? {
        if (state.expected != null) return null
        val features = state.featureState ?: JSONObject().also { state.featureState = it }
        val previous = features.optJSONObject("reasoningFrame")?.takeIf { AssistantContext.live(it.optLong("at")) }
        if (!explicit(input) && (state.topic != "conversation" || features.optString("activeDialogue") != "reasoning")) return null
        if (!explicit(input) && ConversationRouting.winner(input, state) != ConversationRouting.Owner.REASONING) return null
        if (ConversationRouting.nativeRequest(input) && !explicit(input)) return null
        val result = turn(previous, input) ?: return null
        features.put("reasoningFrame", result.frame.json()).put("activeDialogue", "reasoning")
        state.switchTopic("conversation")
        return AyvaTalkEngine.TalkResponse(result.text, topicId = "conversation")
    }
    fun archive(state: AssistantSession): JSONObject {
        val frame = read(state.featureState?.optJSONObject("reasoningFrame"))
        return AssistantDiscussion.sanitizeArchive(JSONObject().put("topic", frame.topic).put("options", JSONArray()).put("question", "").put("stance", "think")
            .put("notes", JSONArray((frame.claims.map { "${if (it.assumption) "Assumption" else "User supplied"}: ${it.text}" } + frame.rules.map { "If ${it.premise}, then ${it.consequence}" }).takeLast(10).map { JSONObject().put("key", "reasoning").put("text", it.take(400)) })))
    }
    fun handoff(state: AssistantSession, input: String): AssistantDiscussion.Seed? {
        if (state.expected != null || state.topic != "conversation" || state.featureState?.optString("activeDialogue") != "reasoning" || !AssistantDiscussion.requestsHandoff(input) && input != "/plan") return null
        val frame = read(state.featureState?.optJSONObject("reasoningFrame"))
        return AssistantDiscussion.Seed(frame.topic, (frame.claims.map { "${if (it.assumption) "Assumption" else "You supplied"}: ${it.text}" } + frame.rules.map { "If ${it.premise}, then ${it.consequence}" }).takeLast(10).mapIndexed { i, value -> "discussion ${i + 1}" to value }.toMap())
    }
}

internal object PlanningReasoning {
    fun validate(book: JSONObject?) {
        if (book == null) return
        require(book.length() <= 13 && book.toString().toByteArray().size <= 64_000)
        book.keys().asSequence().forEach { require(it.length in 1..80); ConversationReasoning.read(book.getJSONObject(it)) }
    }
    fun subset(plan: PlanWorkspace, ids: Set<String>): JSONObject? {
        val book = plan.dialogue.reasoning ?: return null
        val scopes = plan.milestones.filter { it.stepIds.any(ids::contains) }.map { it.id }.toSet() + "whole"
        return JSONObject().also { out -> book.keys().asSequence().filter(scopes::contains).forEach { out.put(it, JSONObject(book.getJSONObject(it).toString())) } }
    }
    fun merged(first: PlanWorkspace, second: PlanWorkspace, groups: List<PlanMilestone>): JSONObject? {
        if (first.dialogue.reasoning == null && second.dialogue.reasoning == null) return null
        val result = first.dialogue.reasoning?.let { JSONObject(it.toString()) } ?: JSONObject()
        second.dialogue.reasoning?.let { book -> book.keys().asSequence().forEach { key ->
            val target = if (key == "whole") key else {
                val old = second.milestones.single { it.id == key }
                groups.single { it.title.equals(old.title, true) }.id
            }
            result.put(target, ConversationReasoning.merge(result.optJSONObject(target), book.getJSONObject(key)))
        } }
        validate(result); return result
    }
    fun answer(plan: PlanWorkspace, input: String): PlanningDialogue.Turn? {
        val key = plan.dialogue.focusMilestone ?: "whole"
        val previous = plan.dialogue.reasoning?.optJSONObject(key)
        if (!ConversationReasoning.explicit(input) && previous == null) return null
        val result = ConversationReasoning.turn(previous, input) ?: return null
        val book = plan.dialogue.reasoning?.let { JSONObject(it.toString()) } ?: JSONObject()
        book.put(key, result.frame.json()); validate(book)
        return PlanningDialogue.Turn(plan.copy(dialogue = plan.dialogue.copy(reasoning = book)), result.text)
    }
}
