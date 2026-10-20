package com.focusbyrj.app.util.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Explicit user criteria and three-valued evidence: true, false, or absent/unknown. */
internal data class DiscussionCriteria(
    val rules: List<Rule> = emptyList(),
    val evidence: List<Evidence> = emptyList(),
    val primary: String? = null,
    val pending: Question? = null
) {
    enum class Strength { REQUIRED, PREFERRED }
    data class Rule(val feature: String, val desired: Boolean, val strength: Strength, val source: String)
    data class Evidence(val option: String, val feature: String, val value: Boolean, val source: String)
    data class Question(val option: String, val feature: String)
    data class Turn(val state: DiscussionCriteria, val text: String, val setsPriority: Boolean = false, val accepted: Boolean = true, val needsCorrection: Boolean = false)
    private data class Property(val feature: String, val value: Boolean)
    fun hasData() = rules.isNotEmpty() || evidence.isNotEmpty()
    fun json() = JSONObject().put("version", 1).put("rules", JSONArray(rules.map {
        JSONObject().put("feature", it.feature).put("desired", it.desired).put("strength", it.strength.name).put("source", it.source)
    })).put("evidence", JSONArray(evidence.map {
        JSONObject().put("option", it.option).put("feature", it.feature).put("value", it.value).put("source", it.source)
    })).put("primary", primary ?: JSONObject.NULL).put("pending", pending?.let {
        JSONObject().put("option", it.option).put("feature", it.feature)
    } ?: JSONObject.NULL)
    fun retain(options: List<String>) = copy(evidence = evidence.mapNotNull { fact ->
        options.singleOrNull { it.equals(fact.option, true) }?.let { fact.copy(option = it) }
    }, pending = null)
    private fun value(option: String, feature: String) = evidence.singleOrNull { it.option == option && it.feature == feature }?.value
    private fun features() = (rules.map { it.feature } + evidence.map { it.feature }).distinct()
    private fun matches(option: String, rule: Rule) = value(option, rule.feature)?.let { it == rule.desired }
    private fun label(rule: Rule) = (if (rule.desired) "" else "not ") + rule.feature
    fun openQuestion(question: Question? = pending): String? = question?.let {
        val predicate = if (it.feature.contains(' ') || it.feature in setOf("wifi", "parking", "transfers", "transfer")) "Does ${it.option} have ${it.feature}" else "Is ${it.option} ${it.feature}"
        "$predicate? Say yes or no, or ‘I don't know’."
    }
    fun conflict(option: String): String? = rules.filter { it.strength == Strength.REQUIRED && matches(option, it) == false }
        .takeIf { it.isNotEmpty() }?.joinToString("; ") { "requires ${label(it)}" }?.let {
            "$option conflicts with your requirement: $it. Revise the requirement or choose another option before planning that choice."
        }
    fun missingRequirement(option: String) = rules.firstOrNull { it.strength == Strength.REQUIRED && value(option, it.feature) == null }?.let { Question(option, it.feature) }
    fun nextQuestion(options: List<String>): Question? = options.firstNotNullOfOrNull(::missingRequirement)
        ?: primary?.let { feature -> options.firstOrNull { value(it, feature) == null }?.let { Question(it, feature) } }
    fun report(options: List<String>): String = buildString {
        if (rules.isNotEmpty()) append("Your criteria:\n" + rules.joinToString("\n") {
            "• ${if (it.strength == Strength.REQUIRED) "Required" else "Preferred"}: ${label(it)} (you said: ${it.source})"
        })
        if (features().isNotEmpty()) {
            if (isNotEmpty()) append('\n')
            append("Qualities you supplied:\n" + options.joinToString("\n") { option ->
                "$option: " + features().joinToString("; ") { feature -> "$feature ${value(option, feature)?.let { if (it) "yes" else "no" } ?: "unknown"}" }
            })
        }
        primary?.let { feature ->
            val rule = rules.single { it.feature == feature }
            val matching = options.filter { matches(it, rule) == true }
            append("\nYour top qualitative priority is ${label(rule)}.")
            if (matching.isNotEmpty()) append(" Your descriptions favor ${matching.joinToString(" / ")} on that quality.")
        }
    }

    fun answer(input: String, options: List<String>): Turn? {
        if (options.isEmpty()) return null
        val text = PlanLanguage.normalize(input).trimEnd('.', '!')
        if (text.contains('?') || text.startsWith('/')) return null
        val correction = PlanLanguage.correctionValue(text)
        val body = correction ?: text
        val lower = body.lowercase(Locale.ROOT)
        if (pending != null) {
            if (lower in setOf("i don't know", "not sure", "i'm not sure", "i am not sure", "skip", "leave it unknown"))
                return Turn(copy(pending = null), "We'll leave ${pending.option}'s ${pending.feature} unknown.")
            val answer = when (lower) {
                "yes", "yes it is", "yes it does", "it is", "it does", "it has", "correct" -> true
                "no", "no it isn't", "it isn't", "it is not", "it doesn't", "it does not" -> false
                else -> property(body)?.takeIf { it.feature == pending.feature }?.value
            }
            if (answer != null) {
                val old = value(pending.option, pending.feature)
                if (old != null && old != answer && correction == null) return Turn(copy(pending = null), "That differs from the earlier description. Name the option and start the correction with ‘actually’.", accepted = false)
                val fact = Evidence(pending.option, pending.feature, answer, text)
                return Turn(copy(evidence = evidence.filterNot { it.option == fact.option && it.feature == fact.feature } + fact, pending = null),
                    "Noted for ${fact.option}: ${fact.feature} ${if (fact.value) "yes" else "no"}.")
            }
        }
        if (lower == "clear qualitative priority") return Turn(copy(primary = null, pending = null), "Cleared the top qualitative priority; your criteria remain.")
        if (lower == "forget qualitative criteria") return Turn(DiscussionCriteria(), "Cleared qualitative criteria and descriptions. Your figures and options remain.")
        val clear = Regex("(?i)^(?:clear|remove) (requirement|preference|criterion)(?: for)? (.+)$").matchEntire(body)
        if (clear != null) {
            val property = property(clear.groupValues[2]) ?: return null
            val strength = when (clear.groupValues[1].lowercase(Locale.ROOT)) { "requirement" -> Strength.REQUIRED; "preference" -> Strength.PREFERRED; else -> null }
            val removed = rules.filter { it.feature == property.feature && (strength == null || it.strength == strength) }
            return Turn(copy(rules = rules - removed.toSet(), primary = primary.takeUnless { it == property.feature && removed.isNotEmpty() }, pending = null), "Cleared that ${clear.groupValues[1].lowercase(Locale.ROOT)} for ${property.feature}; its descriptions remain.")
        }
        val ruleMatch = Regex("(?i)^(i (?:need|require|must have)|require|i (?:can't|cannot|must not) (?:have|use|travel)|i (?:don't|do not) want|avoid|i must avoid|i prefer|i care about|i care most about|my priority is|prioriti[sz]e) (.+)$").matchEntire(body)
        if (ruleMatch != null) {
            val verb = ruleMatch.groupValues[1].lowercase(Locale.ROOT)
            val phrase = ruleMatch.groupValues[2]
            // “I can't travel overnight” has a property, not an action to execute.
            if (options.any { it.equals(phrase.removePrefix("the "), true) } || phrase.startsWith("to ", true)) return null
            val property = property(phrase) ?: return null
            val avoid = verb.contains("can't") || verb.contains("cannot") || verb.contains("must not") || verb.contains("don't") || verb.contains("do not") || verb.contains("avoid")
            val strength = if (verb in setOf("i prefer", "i care about", "i care most about", "my priority is", "prioritize", "prioritise")) Strength.PREFERRED else Strength.REQUIRED
            val top = verb in setOf("i care most about", "my priority is", "prioritize", "prioritise")
            val rule = Rule(property.feature, if (avoid) !property.value else property.value, strength, text)
            if (text.length > 200) return Turn(this, "Keep each qualitative criterion under 200 characters.", accepted = false)
            if (property.feature !in features() && features().size >= 6) return Turn(this, "We already have six qualities in this comparison. Clear an unused criterion or description before adding another.", accepted = false)
            val old = rules.singleOrNull { it.feature == rule.feature }
            if (old != null && old.desired != rule.desired && correction == null)
                return Turn(copy(pending = null), "You previously wanted ${label(old)}. Start the changed criterion with ‘actually’ so I can replace it.", accepted = false, needsCorrection = true)
            return Turn(copy(rules = rules.filterNot { it.feature == rule.feature } + rule, primary = if (top) rule.feature else primary, pending = null),
                "${if (strength == Strength.REQUIRED) "Required" else "Preferred"}: ${label(rule)}.${if (top) " That's your top qualitative priority." else ""}", top)
        }
        val names = options.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) } + "|(?:first|second|third|fourth|fifth|sixth) one"
        fun resolve(ref: String) = options.singleOrNull { it.equals(ref, true) } ?: options.getOrNull(listOf("first", "second", "third", "fourth", "fifth", "sixth").indexOf(ref.lowercase(Locale.ROOT).removeSuffix(" one")))
        val forget = Regex("(?i)^(?:forget|clear) (?:the )?($names) (.+)$").matchEntire(body)
        if (forget != null) {
            val option = resolve(forget.groupValues[1]) ?: return null
            val property = property(forget.groupValues[2]) ?: return null
            if (property.feature !in features()) return null
            return Turn(copy(evidence = evidence.filterNot { it.option == option && it.feature == property.feature }, pending = null), "Left $option's ${property.feature} unknown.")
        }
        val predicates = "isn't|is not|is|has no|has|doesn't have|does not have|involves|doesn't involve|does not involve"
        fun negative(verb: String) = verb.contains("n't", true) || verb.contains(" not", true) || verb.equals("has no", true)
        val statement = Regex("(?i)^(?:the )?($names) ($predicates) (.+)$").matchEntire(body) ?: return null
        val option = resolve(statement.groupValues[1]) ?: return Turn(copy(pending = null), "That option isn't in this comparison.", accepted = false)
        val clauses = statement.groupValues[3].split(Regex("\\s+and\\s+", RegexOption.IGNORE_CASE))
        val properties = clauses.mapIndexed { index, clause ->
            val explicit = Regex("(?i)^($predicates) (.+)$").matchEntire(clause)
            val negated = if (index == 0) negative(statement.groupValues[2]) else explicit?.let { negative(it.groupValues[1]) } ?: false
            if (index > 0 && negative(statement.groupValues[2]) && explicit == null && !Regex("(?i)^(?:not|no) ").containsMatchIn(clause)) null
            else property(explicit?.groupValues?.get(2) ?: clause)?.let { if (negated) it.copy(value = !it.value) else it }
        }
        if (clauses.size > 6 || properties.any { it == null }) return Turn(copy(pending = null), "Describe the qualities separately from cost or time figures. I haven't changed the descriptions.", accepted = false)
        val facts = properties.filterNotNull().map { Evidence(option, it.feature, it.value, text) }
        if ((features() + facts.map { it.feature }).distinct().size > 6 || text.length > 200)
            return Turn(this, "Keep this comparison to six qualities and each description under 200 characters.", accepted = false)
        if (facts.groupBy { it.feature }.any { (_, values) -> values.map { it.value }.distinct().size > 1 })
            return Turn(copy(pending = null), "Those descriptions disagree about the same quality. Tell me which one applies; I haven't changed either.", accepted = false, needsCorrection = true)
        if (facts.any { value(it.option, it.feature)?.let { old -> old != it.value } == true } && correction == null)
            return Turn(copy(pending = null), "That contradicts an earlier description. Start the correction with ‘actually’. I haven't changed the descriptions.", accepted = false, needsCorrection = true)
        return Turn(copy(evidence = evidence.filterNot { old -> facts.any { it.option == old.option && it.feature == old.feature } } + facts.distinctBy { it.feature }, pending = null),
            "Noted for $option: " + facts.joinToString("; ") { "${it.feature} ${if (it.value) "yes" else "no"}" } + ".")
    }

    fun planFacts(options: List<String>): Map<String, String> = linkedMapOf<String, String>().apply {
        if (rules.isNotEmpty()) put("qualitative criteria supplied", rules.joinToString("\n") { "${it.strength.name.lowercase(Locale.ROOT)} ${label(it)} — ${it.source}" })
        primary?.let { put("qualitative priority", it) }
        var chunk = ""; var index = 1
        for (option in options) {
            val facts = evidence.filter { it.option == option }
            if (facts.isEmpty()) continue
            val line = "$option: " + facts.joinToString("; ") { "${it.feature} ${if (it.value) "yes" else "no"}" }
            if (chunk.isNotEmpty() && chunk.length + line.length + 1 > 2000) { put("qualitative descriptions ${index++}", chunk); chunk = "" }
            chunk += (if (chunk.isEmpty()) "" else "\n") + line
        }
        if (chunk.isNotEmpty()) put("qualitative descriptions $index", chunk)
    }
    companion object {
        private val reserved = Regex("(?i)^(?:cost|price|time|travel time|lower cost|low cost|less travel time|cheaper|faster|better|worse|more|less|higher|lower|not sure|unknown|maybe|perhaps|probably|to)\\b")
        private fun property(input: String): Property? {
            var phrase = input.lowercase(Locale.ROOT).trim().replace(Regex("^(?:a|an|the) "), "")
            var value = true
            if (phrase.startsWith("not ") || phrase.startsWith("no ")) { value = false; phrase = phrase.substringAfter(' ') }
            phrase = phrase.replace(Regex(" (?:travel|journeys?|options?)$"), "").replace("wi-fi", "wifi")
            if (reserved.containsMatchIn(phrase) || !phrase.matches(Regex("[a-z][a-z0-9 -]{0,39}")) || phrase.contains(" and ")) return null
            if (phrase == "noisy") { phrase = "quiet"; value = !value }
            return Property(phrase, value)
        }
        fun read(json: JSONObject?, options: List<String>): DiscussionCriteria {
            if (json == null) return DiscussionCriteria()
            require(json.getInt("version") == 1)
            val ruleArray = json.getJSONArray("rules"); val factArray = json.getJSONArray("evidence")
            require(ruleArray.length() <= 6 && factArray.length() <= 36)
            val rules = (0 until ruleArray.length()).map {
                val r = ruleArray.getJSONObject(it); require(r.get("desired") is Boolean)
                Rule(r.getString("feature"), r.getBoolean("desired"), Strength.valueOf(r.getString("strength")), r.getString("source"))
            }
            val evidence = (0 until factArray.length()).map {
                val f = factArray.getJSONObject(it); require(f.get("value") is Boolean)
                Evidence(f.getString("option"), f.getString("feature"), f.getBoolean("value"), f.getString("source"))
            }
            val primary = json.optString("primary").takeUnless { it.isBlank() || it == "null" }
            val pending = json.optJSONObject("pending")?.let { Question(it.getString("option"), it.getString("feature")) }
            val result = DiscussionCriteria(rules, evidence, primary, pending)
            require(result.features().size <= 6 && result.features().all { property(it)?.let { p -> p.feature == it && p.value } == true })
            require(rules.distinctBy { it.feature }.size == rules.size && evidence.distinctBy { it.option to it.feature }.size == evidence.size)
            require(rules.all { it.source.length in 1..200 } && evidence.all { it.option in options && it.source.length in 1..200 })
            require(primary == null || rules.any { it.feature == primary })
            require(pending == null || pending.option in options && pending.feature in result.features())
            return result
        }
    }
}
