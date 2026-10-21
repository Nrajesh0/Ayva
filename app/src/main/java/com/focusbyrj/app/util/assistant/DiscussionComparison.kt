package com.focusbyrj.app.util.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Locale

/** Typed, user-supplied measurements; calculations are advisory and never select or save an option. */
internal data class DiscussionComparison(
    val observations: List<Observation> = emptyList(),
    val limits: List<Bound> = emptyList(),
    val priority: Metric? = null,
    val pending: Question? = null,
    val view: Metric? = null,
    val criteria: DiscussionCriteria = DiscussionCriteria(),
    val weights: Map<Metric, Int> = emptyMap()
) {
    enum class Metric(val label: String) { COST("cost"), TIME("travel time") }
    data class Quantity(val amount: BigDecimal, val unit: String, val upper: BigDecimal? = null) {
        val ceiling get() = upper ?: amount
        fun display() = "${amount.stripTrailingZeros().toPlainString()}${upper?.let { "–${it.stripTrailingZeros().toPlainString()}" }.orEmpty()} $unit"
        fun json() = JSONObject().put("amount", amount.toPlainString()).put("unit", unit).put("upper", upper?.toPlainString() ?: JSONObject.NULL)
        fun same(other: Quantity) = unit == other.unit && amount.compareTo(other.amount) == 0 && ceiling.compareTo(other.ceiling) == 0
    }
    data class Observation(val option: String, val metric: Metric, val quantity: Quantity)
    data class Bound(val metric: Metric, val quantity: Quantity)
    data class Question(val option: String, val metric: Metric, val currency: String? = null, val deferredCost: BigDecimal? = null, val deferredUpper: BigDecimal? = null)
    data class Turn(val state: DiscussionComparison, val text: String)
    private data class Parsed(val quantity: Quantity? = null, val deferredCost: BigDecimal? = null, val deferredUpper: BigDecimal? = null)

    fun json(): JSONObject = JSONObject().put("version", 2).put("observations", JSONArray(observations.map {
        JSONObject().put("option", it.option).put("metric", it.metric.name).put("quantity", it.quantity.json())
    })).put("limits", JSONArray(limits.map {
        JSONObject().put("metric", it.metric.name).put("quantity", it.quantity.json())
    })).put("priority", priority?.name ?: JSONObject.NULL).put("view", view?.name ?: JSONObject.NULL).put("pending", pending?.let {
        JSONObject().put("option", it.option).put("metric", it.metric.name).put("currency", it.currency ?: JSONObject.NULL)
            .put("deferredCost", it.deferredCost?.toPlainString() ?: JSONObject.NULL).put("deferredUpper", it.deferredUpper?.toPlainString() ?: JSONObject.NULL)
    } ?: JSONObject.NULL).put("criteria", criteria.json()).put("weights", JSONObject(weights.mapKeys { it.key.name }))

    fun retain(options: List<String>) = copy(observations = observations.mapNotNull { observation ->
        options.singleOrNull { it.equals(observation.option, true) }?.let { observation.copy(option = it) }
    }, pending = null, view = null, criteria = criteria.retain(options))
    fun hasQuestion() = pending != null || criteria.pending != null
    fun withoutQuestions() = copy(pending = null, view = null, criteria = criteria.copy(pending = null))
    fun openQuestion() = criteria.openQuestion() ?: pending?.let(::questionText)
    fun explain(options: List<String>) = view?.let { compareMetric(options, it) } ?: compare(options)
    private fun value(option: String, metric: Metric) = observations.singleOrNull { it.option == option && it.metric == metric }?.quantity
    private fun bound(metric: Metric) = limits.singleOrNull { it.metric == metric }?.quantity
    private fun update(option: String, metric: Metric, quantity: Quantity) = copy(
        observations = observations.filterNot { it.option == option && it.metric == metric } + Observation(option, metric, quantity), pending = null)
    private fun questionText(question: Question): String = when {
        question.deferredCost != null -> "Which currency is ${question.option}'s cost of ${question.deferredCost.toPlainString()} in?"
        question.metric == Metric.TIME -> "How long does ${question.option} take? Include minutes, hours or days, or say ‘I don't know’."
        question.currency != null -> "What is ${question.option}'s cost in ${question.currency}? Supply a figure in that currency; I can't convert exchange rates."
        else -> "What does ${question.option} cost? Include the currency, or say ‘I don't know’."
    }

    /** Only a currently asked measurement can consume a short answer. Corrections otherwise need a named option. */
    fun answer(input: String, options: List<String>): Turn? {
        val weighted = Regex("(?i)^(?:use )?weights? (?:of )?(?:cost|price) (\\d+) (?:and |, ?)?(?:time|travel time) (\\d+)$").matchEntire(PlanLanguage.normalize(input))
        if (weighted != null) {
            val cost = weighted.groupValues[1].toIntOrNull(); val time = weighted.groupValues[2].toIntOrNull()
            require(cost != null && time != null && cost in 1..100 && time in 1..100) { "Use explicit cost and time weights from 1 to 100" }
            val next = copy(weights = mapOf(Metric.COST to cost, Metric.TIME to time), priority = null, pending = null, view = null, criteria = criteria.copy(primary = null, pending = null))
            return Turn(next, "Using your cost:time weights of $cost:$time. " + next.weightedReport(options))
        }
        if (input.lowercase() in setOf("clear weights", "remove weights")) return Turn(copy(weights = emptyMap()), "Removed numerical weights. Qualitative requirements remain in place.")
        numericAnswer(input, options)?.let { result ->
            return result.copy(state = result.state.copy(criteria = result.state.criteria.copy(pending = null)))
        }
        criteria.answer(input, options)?.let { result ->
            return Turn(copy(criteria = result.state, pending = null, view = null, priority = if (result.setsPriority) null else priority, weights = if (result.setsPriority) emptyMap() else weights), result.text)
        }
        return null
    }

    private fun numericAnswer(input: String, options: List<String>): Turn? {
        val original = PlanLanguage.normalize(input)
        val text = original.trimEnd('.', '!')
        val lower = text.lowercase(Locale.ROOT)
        val query = lower.trimEnd('?', '.', '!')
        val dimension = Regex("^which(?: option| one)? (?:is|would be) (cheaper|cheapest|less expensive|faster|fastest|quicker)$").matchEntire(query)?.groupValues?.get(1)
            ?: Regex("^which(?: option| one)? (costs less|takes less time)$").matchEntire(query)?.groupValues?.get(1)
        if (dimension != null) return compareMetric(options, if (dimension in setOf("cheaper", "cheapest", "less expensive", "costs less")) Metric.COST else Metric.TIME)
        if (lower.contains('?') || lower.startsWith('/') || options.isEmpty()) return null
        val correction = PlanLanguage.correctionValue(text)
        val body = correction ?: text
        val bodyLower = body.lowercase(Locale.ROOT)
        if (pending != null && pending.option in options && lower in setOf("i don't know", "not sure", "i'm not sure", "i am not sure", "skip", "leave it unknown"))
            return Turn(copy(pending = null), "We'll leave ${pending.option}'s ${pending.metric.label} unknown. I won't use an unknown value as zero.")
        val preference = Regex("(?i)^(?:prioriti[sz]e|i care most about|my priority is|i prefer lower) (cost|price|time|travel time)$").matchEntire(body)
            ?.groupValues?.get(1) ?: Regex("(?i)^(cost|price|time|travel time) matters more than (?:cost|price|time|travel time)$").matchEntire(body)?.groupValues?.get(1)
        if (preference != null) {
            val metric = metric(preference)
            return Turn(copy(priority = metric, pending = null, view = null, criteria = criteria.copy(primary = null), weights = emptyMap()), "I'll compare by lower ${metric.label} first, while keeping your stated limits. You can change that priority.")
        }
        if (bodyLower == "clear comparison priority") return Turn(copy(priority = null, pending = null, view = null, criteria = criteria.copy(primary = null)), "Cleared the comparison priority. We can look at the trade-offs together.")
        if (bodyLower == "forget comparison figures") return Turn(DiscussionComparison(criteria = criteria.copy(pending = null)), "Cleared comparison figures, limits and cost/time priority. Your qualitative criteria, discussion topic and options remain.")
        val clearLimit = Regex("(?i)^(?:clear|remove) (cost|price|time|travel time) limit$").matchEntire(body)
        if (clearLimit != null) {
            val metric = metric(clearLimit.groupValues[1])
            return Turn(copy(limits = limits.filterNot { it.metric == metric }, pending = null, view = null), "Removed the ${metric.label} limit.")
        }
        val limit = Regex("(?i)^(?:my )?(?:maximum |max )?(cost|price|time|travel time) limit (?:is |of |: ?)?(.+)$").matchEntire(body)
            ?: Regex("(?i)^maximum (cost|price|time|travel time) (?:is |of |: ?)?(.+)$").matchEntire(body)
        if (limit != null) {
            val metric = metric(limit.groupValues[1]); val parsed = parse(metric, limit.groupValues[2])
            val quantity = parsed.quantity ?: return Turn(copy(pending = null), "Give the ${metric.label} limit with ${if (metric == Metric.COST) "a currency, such as ‘cost limit is INR 3000’" else "minutes, hours or days"}. I haven't changed the limit.")
            if (quantity.upper != null) return Turn(copy(pending = null), "A hard limit needs one maximum, rather than a range. Which maximum should I use? Your earlier limit is unchanged.")
            val old = bound(metric)
            if (old != null && !old.same(quantity) && correction == null)
                return Turn(copy(pending = null), "The ${metric.label} limit is ${old.display()}. Say ‘actually, ${metric.label} limit is ${quantity.display()}’ to replace it.")
            return Turn(copy(limits = limits.filterNot { it.metric == metric } + Bound(metric, quantity), pending = null, view = null), "${metric.label.replaceFirstChar(Char::uppercaseChar)} limit: ${quantity.display()}.")
        }
        val ordinals = listOf("first", "second", "third", "fourth", "fifth", "sixth")
        val names = options.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) } + "|(?:first|second|third|fourth|fifth|sixth) one"
        fun resolve(reference: String) = options.singleOrNull { it.equals(reference, true) }
            ?: options.getOrNull(ordinals.indexOf(reference.lowercase(Locale.ROOT).removeSuffix(" one")))
        val clear = Regex("(?i)^(?:forget|clear) (?:the )?($names) (cost|price|time|travel time)$").matchEntire(body)
        if (clear != null) {
            val option = resolve(clear.groupValues[1]) ?: return Turn(copy(pending = null), "That option isn't in this comparison. Options: ${options.joinToString(" / ")}.")
            val metric = metric(clear.groupValues[2])
            return Turn(copy(observations = observations.filterNot { it.option == option && it.metric == metric }, pending = null), "Left $option's ${metric.label} unknown.")
        }
        val costFirst = Regex("(?i)^(?:the )?($names) (?:costs?|price is) (.+?)(?: and (?:it )?takes? (.+))?$").matchEntire(body)
        val timeFirst = Regex("(?i)^(?:the )?($names) takes? (.+?)(?: and (?:it )?costs? (.+))?$").matchEntire(body)
        val match = costFirst ?: timeFirst
        if (match != null) {
            val option = resolve(match.groupValues[1]) ?: return Turn(copy(pending = null), "That option isn't in this comparison. Options: ${options.joinToString(" / ")}.")
            val first = if (costFirst != null) Metric.COST else Metric.TIME
            val requests = listOf(first to match.groupValues[2]) + match.groupValues[3].takeIf(String::isNotBlank)?.let {
                listOf((if (first == Metric.COST) Metric.TIME else Metric.COST) to it)
            }.orEmpty()
            val parsed = requests.map { (metric, raw) -> metric to parse(metric, raw) }
            // Contradictory batches are atomic; a new sibling figure cannot hide an unaccepted correction.
            val conflict = parsed.firstOrNull { (metric, parsed) -> parsed.quantity?.let { new -> value(option, metric)?.let { !it.same(new) } } == true }
            if (conflict != null && correction == null) {
                val old = value(option, conflict.first)!!
                return Turn(copy(pending = null), "You previously gave $option's ${conflict.first.label} as ${old.display()}. Start the correction with ‘actually’. I haven't changed either figure.")
            }
            var next = copy(pending = null, view = null)
            val accepted = mutableListOf<String>()
            for ((metric, parsed) in parsed) {
                parsed.quantity?.let { next = next.update(option, metric, it); accepted += "${metric.label} ${it.display()}" }
            }
            val missing = parsed.firstOrNull { it.second.quantity == null }
            if (missing != null) {
                val question = Question(option, missing.first, deferredCost = missing.second.deferredCost, deferredUpper = missing.second.deferredUpper)
                val prefix = if (accepted.isEmpty()) "" else "Noted for $option: ${accepted.joinToString("; ")}. "
                return Turn(next.copy(pending = question), prefix + questionText(question))
            }
            return Turn(next, "Noted for $option: ${accepted.joinToString("; ")}.")
        }
        if (pending != null && pending.option in options) {
            val currency = currencyOnly(body)
            if (pending.deferredCost != null && currency != null) {
                val quantity = Quantity(pending.deferredCost, currency, pending.deferredUpper)
                val old = value(pending.option, Metric.COST)
                if (old != null && !old.same(quantity) && correction == null)
                    return Turn(copy(pending = null), "That differs from ${pending.option}'s earlier cost of ${old.display()}. Restate the corrected cost with ‘actually’.")
                return Turn(update(pending.option, Metric.COST, quantity), "Noted ${pending.option}'s cost: ${quantity.display()}.")
            }
            if (pending.metric == Metric.COST && currency != null) {
                if (pending.currency != null && currency != pending.currency) return Turn(this, questionText(pending))
                val question = pending.copy(currency = currency)
                return Turn(copy(pending = question), questionText(question))
            }
            val parsed = parse(pending.metric, body, pending.currency).quantity
            if (parsed != null && pending.currency != null && parsed.unit != pending.currency) return Turn(this, questionText(pending))
            if (parsed != null && (pending.currency == null || parsed.unit == pending.currency)) {
                val old = value(pending.option, pending.metric)
                if (old != null && !old.same(parsed) && correction == null)
                    return Turn(copy(pending = null), "That differs from ${pending.option}'s earlier ${pending.metric.label}. Restate the corrected figure with ‘actually’.")
                return Turn(update(pending.option, pending.metric, parsed), "Noted ${pending.option}'s ${pending.metric.label}: ${parsed.display()}.")
            }
        }
        return null
    }

    fun choiceProblem(option: String): String? {
        val exceeded = limits.mapNotNull { limit -> value(option, limit.metric)?.takeIf {
            it.unit == limit.quantity.unit && it.ceiling > limit.quantity.amount
        }?.let { "${limit.metric.label} ${it.display()} ${if (it.amount > limit.quantity.amount) "exceeds" else "may exceed"} your limit of ${limit.quantity.display()}" } }
        return exceeded.takeIf { it.isNotEmpty() }?.joinToString("; ")?.let { "$option's $it. Revise the limit or choose another option before planning that choice." }
            ?: criteria.conflict(option)
    }

    private fun weightedReport(options: List<String>): String {
        if (weights.isEmpty()) return "No numerical weights are set."
        val eligible = options.filter { choiceProblem(it) == null }
        if (eligible.size < 2) return "A weighted comparison needs at least two options satisfying the known requirements."
        if (criteria.nextQuestion(eligible) != null) return "Confirm the required qualities before using a weighted recommendation."
        for (metric in weights.keys) {
            val quantities = eligible.map { value(it, metric) }
            if (quantities.any { it == null || it.upper != null }) return "Weighted ranking needs exact known cost and time figures; unknowns and ranges won't be replaced with averages."
            if (quantities.filterNotNull().map { it.unit }.distinct().size != 1) return "Use comparable units for each dimension before weighting. I won't convert currencies."
        }
        val scores = eligible.associateWith { option -> weights.entries.fold(BigDecimal.ZERO) { total, (metric, weight) ->
            val low = eligible.minOf { value(it, metric)!!.amount }; val high = eligible.maxOf { value(it, metric)!!.amount }
            val normalized = if (high.compareTo(low) == 0) BigDecimal.ZERO else
                (value(option, metric)!!.amount - low).divide(high - low, java.math.MathContext.DECIMAL64)
            total + normalized * weight.toBigDecimal()
        } }
        val best = scores.values.minOrNull()!!
        val leaders = scores.filterValues { it.compareTo(best) == 0 }.keys
        return "On your explicit weights, ${leaders.joinToString(" / ")} ${if (leaders.size == 1) "ranks first" else "tie"}. " +
            "Each dimension is scaled from the lowest to highest supplied figure among feasible options, then weighted; lower is better. " +
            "This is a preference heuristic, not an objective quality score. Changing weights or the option set can change the ranking; other qualities remain separate."
    }

    fun compare(options: List<String>): Turn {
        if (observations.any { it.quantity.upper != null }) return compareRanges(options)
        if (options.size < 2) return Turn(copy(pending = null), "We need at least two options before comparing. Say ‘options are A or B’.")
        val metrics = (observations.map { it.metric } + limits.map { it.metric } + listOfNotNull(priority)).distinct().sortedBy { it.ordinal }
        if (metrics.isEmpty() && !criteria.hasData()) return Turn(withoutQuestions(), "I don't have cost or travel-time figures yet. Tell me, for example, ‘${options.first()} costs INR 2500 and takes 6 hours’. We can also keep discussing other factors.")
        val lines = options.map { option -> "$option: " + metrics.joinToString("; ") { metric -> "${metric.label} ${value(option, metric)?.display() ?: "unknown"}" } }
        val excluded = options.mapNotNull { option -> choiceProblem(option)?.let { option to it.substringBefore(". Revise") } }.toMap()
        val eligible = options.filterNot { it in excluded }
        val qualityQuestion = criteria.nextQuestion(eligible)
        val missing = eligible.firstNotNullOfOrNull { option -> metrics.firstNotNullOfOrNull { metric ->
            val quantity = value(option, metric)
            val currency = if (metric == Metric.COST) bound(metric)?.unit ?: eligible.firstNotNullOfOrNull { value(it, metric)?.unit } else null
            if (quantity == null || currency != null && quantity.unit != currency) Question(option, metric, currency) else null
        } }
        val report = buildString {
            append("Options: ${options.joinToString(" / ")}\n")
            if (metrics.isNotEmpty()) append("Using the figures you supplied:\n" + lines.joinToString("\n"))
            if (criteria.hasData()) append("\n" + criteria.report(options))
            if (limits.isNotEmpty()) append("\nLimits: " + limits.joinToString("; ") { "${it.metric.label} at most ${it.quantity.display()}" })
            if (excluded.isNotEmpty()) append("\nOutside your stated limits: " + excluded.values.joinToString("; "))
            if (eligible.isEmpty()) append("\nNone of these options meets all the known limits. Which limit or option can change?")
            else if (qualityQuestion != null) append("\nOne quality is still unconfirmed. " + criteria.openQuestion(qualityQuestion))
            else if (metrics.isEmpty()) {
                if (eligible.size == 1 && criteria.rules.any { it.strength == DiscussionCriteria.Strength.REQUIRED })
                    append("\n${eligible.single()} is the only option confirmed to meet the stated requirements from your descriptions.")
                else append("\nWe can compare these qualities using the details you supply. Which preference matters most?")
            }
            else if (missing != null) append("\nI need a comparable figure before drawing a conclusion. " + questionText(missing))
            else if (eligible.size == 1) append("\n${eligible.single()} is the only option meeting these numeric limits and confirmed requirements on the supplied figures. Other factors may still matter.")
            else {
                val preferred = priority
                if (preferred != null) {
                    val best = eligible.minOf { value(it, preferred)!!.amount }
                    val leaders = eligible.filter { value(it, preferred)!!.amount.compareTo(best) == 0 }
                    append("\nYour priority is lower ${preferred.label}. ${leaders.joinToString(" / ")} ${if (leaders.size == 1) "has the lowest supplied figure" else "tie on that figure"}, while meeting the stated numeric limits.")
                } else {
                    val frontier = eligible.filter { option -> eligible.none { other -> other != option &&
                        metrics.all { value(other, it)!!.amount <= value(option, it)!!.amount } && metrics.any { value(other, it)!!.amount < value(option, it)!!.amount }
                    } }
                    if (eligible.all { option -> metrics.all { value(option, it)!!.amount.compareTo(value(eligible.first(), it)!!.amount) == 0 } })
                        append("\nThese options tie on all supplied cost/time figures. Other factors may help you choose.")
                    else if (frontier.size == 1) append("\n${frontier.single()} is no worse on each supplied cost/time figure and better on at least one. Other factors may still matter.")
                    else append("\nThere is a trade-off among ${frontier.joinToString(" / ")}; no single option is better on all these figures. Which matters more: lower cost or less travel time?")
                }
                for (metric in metrics) {
                    val sorted = eligible.sortedBy { value(it, metric)!!.amount }
                    val low = sorted.first(); val high = sorted.last()
                    val delta = value(high, metric)!!.amount - value(low, metric)!!.amount
                    if (delta.signum() > 0) append("\n$low has ${Quantity(delta, value(low, metric)!!.unit).display()} less ${metric.label} than $high.")
                }
            }
            if (weights.isNotEmpty()) append("\n" + weightedReport(options))
            append("\nYour choice remains yours; say ‘I choose [option]’ when ready.")
        }
        return Turn(copy(pending = if (qualityQuestion == null) missing else null, view = null, criteria = criteria.copy(pending = qualityQuestion)), report)
    }

    /** A question about one factor is a view of the evidence, not a change of the user's priority. */
    private fun compareMetric(options: List<String>, metric: Metric): Turn {
        if (observations.any { it.metric == metric && it.quantity.upper != null }) return compareRanges(options, metric)
        if (options.size < 2) return Turn(copy(pending = null), "Tell me at least two options to compare first. Say ‘options are A or B’.")
        val currency = if (metric == Metric.COST) options.firstNotNullOfOrNull { value(it, metric)?.unit } else null
        val missing = options.firstNotNullOfOrNull { option ->
            val quantity = value(option, metric)
            if (quantity == null || currency != null && quantity.unit != currency) Question(option, metric, currency) else null
        }
        val report = buildString {
            append("Comparing ${metric.label} from your supplied figures:\n")
            append(options.joinToString("\n") { "$it: ${value(it, metric)?.display() ?: "unknown"}" })
            if (missing != null) append("\n" + questionText(missing))
            else {
                val smallest = options.minOf { value(it, metric)!!.amount }
                val leaders = options.filter { value(it, metric)!!.amount.compareTo(smallest) == 0 }
                append("\n${leaders.joinToString(" / ")} ${if (leaders.size == 1) "has the lowest supplied ${metric.label}" else "tie for the lowest supplied ${metric.label}"}.")
                val largest = options.maxOf { value(it, metric)!!.amount }
                if (largest > smallest) append(" The difference between the lowest and highest is ${Quantity(largest - smallest, value(leaders.first(), metric)!!.unit).display()}.")
            }
            val conflicts = options.mapNotNull(::choiceProblem)
            if (conflicts.isNotEmpty()) append("\nOutside your stated limits: " + conflicts.joinToString("; ") { it.substringBefore(". Revise") })
            if (criteria.hasData()) append("\n" + criteria.report(options))
            append("\nWe'll also check your limits and other priorities before choosing.")
        }
        return Turn(copy(pending = missing, view = metric, criteria = criteria.copy(pending = null)), report)
    }

    private fun compareRanges(options: List<String>, only: Metric? = null): Turn {
        if (options.size < 2) return Turn(copy(pending = null), "Tell me at least two options to compare")
        val metrics = only?.let(::listOf) ?: (observations.map { it.metric } + limits.map { it.metric } + listOfNotNull(priority)).distinct()
        var question: Question? = null
        val report = buildString {
            append("Comparing your supplied ranges; no probability or average is assumed.")
            if (only == null && criteria.hasData()) append("\n" + criteria.report(options))
            metrics.forEach { metric ->
                append("\n${metric.label}: " + options.joinToString("; ") { option -> "$option ${value(option, metric)?.display() ?: "unknown"}" })
                val known = options.mapNotNull { value(it, metric) }
                if (known.map { it.unit }.distinct().size > 1) append("\nThe units differ; I haven't converted them.")
                else if (known.size < options.size) {
                    val missing = options.first { value(it, metric) == null }
                    if (question == null) question = Question(missing, metric, known.firstOrNull()?.unit?.takeIf { metric == Metric.COST })
                } else {
                    val guaranteed = options.filter { a -> options.filterNot { it == a }.all { b -> value(a, metric)!!.ceiling < value(b, metric)!!.amount } }
                    if (guaranteed.size == 1) append("\n${guaranteed.single()} has lower ${metric.label} throughout the supplied ranges.")
                    else append("\nThe ${metric.label} ranges overlap or tie, so there is no guaranteed lowest option.")
                }
            }
            if (only == null) options.forEach { option -> choiceProblem(option)?.let { append("\n" + it) } }
            if (question != null) append("\n" + questionText(question!!))
            else append("\nYou can narrow a range with an explicit correction, discuss priorities, or leave the uncertainty recorded.")
        }
        return Turn(copy(pending = question, view = only, criteria = criteria.copy(pending = null)), report)
    }

    fun unresolvedCost(): String? = pending?.takeIf { it.deferredCost != null }?.let {
        "${it.option}: ${it.deferredCost!!.stripTrailingZeros().toPlainString()}${it.deferredUpper?.let { upper -> "–${upper.stripTrailingZeros().toPlainString()}" }.orEmpty()} (currency not specified)"
    }
    fun evidence(options: List<String>): String = options.mapNotNull { option ->
        val known = Metric.entries.mapNotNull { metric -> value(option, metric)?.let { "${metric.label}: ${it.display()}" } }
        known.takeIf { it.isNotEmpty() }?.let { "$option — ${it.joinToString("; ")}" }
    }.joinToString("\n")
    fun planFacts(options: List<String>): Map<String, String> = linkedMapOf<String, String>().apply {
        unresolvedCost()?.let { put("unresolved cost supplied", it) }
        evidence(options).takeIf(String::isNotBlank)?.let { put("comparison figures supplied", it) }
        if (limits.isNotEmpty()) put("comparison limits", limits.joinToString("; ") { "${it.metric.label} at most ${it.quantity.display()}" })
        priority?.let { put("comparison priority", "lower ${it.label}") }
        putAll(criteria.planFacts(options))
    }

    companion object {
        private fun metric(text: String) = if (text.lowercase(Locale.ROOT) in setOf("cost", "price")) Metric.COST else Metric.TIME
        private val currencies = mapOf("₹" to "INR", "inr" to "INR", "rs" to "INR", "rs." to "INR", "rupees" to "INR", "$" to "USD", "usd" to "USD", "€" to "EUR", "eur" to "EUR", "£" to "GBP", "gbp" to "GBP")
        private fun currencyOnly(text: String) = currencies[text.lowercase(Locale.ROOT).trim()]
        private fun number(text: String): BigDecimal? {
            if (!text.matches(Regex("(?:\\d+|\\d{1,3}(?:,\\d{3})+|\\d{1,2}(?:,\\d{2})*,\\d{3})(?:\\.\\d{1,3})?"))) return null
            return text.replace(",", "").toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO && it <= BigDecimal("1000000000000") }
        }
        private fun parse(metric: Metric, text: String, requestedCurrency: String? = null): Parsed {
            val range = (if (text.trim().startsWith("between ", true)) Regex("(?i)^between (.+?)\\s+and\\s+(.+)$")
                else Regex("(?i)^(.+?)\\s*(?: to |–| - )\\s*(.+)$")).matchEntire(text.trim())
            if (range != null) {
                val left = parse(metric, range.groupValues[1], requestedCurrency)
                val right = parse(metric, range.groupValues[2], requestedCurrency)
                if (metric == Metric.COST && left.deferredCost != null && right.deferredCost != null && left.deferredCost <= right.deferredCost)
                    return Parsed(deferredCost = left.deferredCost, deferredUpper = right.deferredCost)
                var a = left.quantity
                var b = right.quantity
                if (a == null && b != null && metric == Metric.COST) a = parse(metric, range.groupValues[1], b.unit).quantity
                if (b == null && a != null && metric == Metric.COST) b = parse(metric, range.groupValues[2], a.unit).quantity
                // A shared trailing duration unit is explicit, e.g. 30 to 45 minutes.
                if (a == null && b != null && metric == Metric.TIME) {
                    val unit = Regex("(?i)(minutes?|mins?|hours?|hrs?|days?)$").find(range.groupValues[2])?.value
                    if (unit != null) a = parse(metric, "${range.groupValues[1]} $unit").quantity
                }
                if (a != null && b != null && a.unit == b.unit && a.upper == null && b.upper == null && a.amount <= b.amount)
                    return Parsed(Quantity(a.amount, a.unit, b.amount))
                return Parsed()
            }
            val raw = text.trim().lowercase(Locale.ROOT)
            if (metric == Metric.COST) {
                val token = currencies.keys.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) }
                Regex("^($token)\\s*(.+)$").matchEntire(raw)?.let { match -> number(match.groupValues[2])?.let { return Parsed(Quantity(it, currencies.getValue(match.groupValues[1]))) } }
                Regex("^(.+?)\\s*($token)$").matchEntire(raw)?.let { match -> number(match.groupValues[1])?.let { return Parsed(Quantity(it, currencies.getValue(match.groupValues[2]))) } }
                val amount = number(raw)
                return if (amount != null && requestedCurrency != null) Parsed(Quantity(amount, requestedCurrency)) else Parsed(deferredCost = amount)
            }
            PlanLanguage.duration(raw)?.let { return Parsed(Quantity(it.toBigDecimal(), "minutes")) }
            val duration = Regex("^(\\d+(?:\\.\\d{1,3})?)\\s*(minutes?|mins?|hours?|hrs?|days?)$").matchEntire(raw) ?: return Parsed()
            val amount = number(duration.groupValues[1]) ?: return Parsed()
            val factor = when { duration.groupValues[2].startsWith("h") -> BigDecimal(60); duration.groupValues[2].startsWith("d") -> BigDecimal(1440); else -> BigDecimal.ONE }
            val minutes = amount * factor
            return Parsed(minutes.takeIf { it > BigDecimal.ZERO && it <= BigDecimal(525600) }?.let { Quantity(it, "minutes") })
        }
        fun read(json: JSONObject?, options: List<String>): DiscussionComparison {
            if (json == null) return DiscussionComparison()
            require(json.optInt("version", 1) in 1..2)
            fun quantity(json: JSONObject): Quantity {
                val amount = json.getString("amount").toBigDecimal()
                val unit = json.getString("unit")
                require(amount >= BigDecimal.ZERO && amount <= BigDecimal("1000000000000") && amount.scale() in 0..6)
                require(unit in setOf("INR", "USD", "EUR", "GBP", "minutes"))
                val upper = json.optString("upper").takeUnless { it.isBlank() || it == "null" }?.toBigDecimal()
                require(upper == null || upper >= amount && upper <= BigDecimal("1000000000000") && upper.scale() in 0..6)
                return Quantity(amount, unit, upper)
            }
            val values = json.getJSONArray("observations"); val bounds = json.getJSONArray("limits")
            require(values.length() <= 12 && bounds.length() <= 2)
            val observations = (0 until values.length()).map {
                val item = values.getJSONObject(it)
                Observation(item.getString("option"), Metric.valueOf(item.getString("metric")), quantity(item.getJSONObject("quantity")))
            }
            val limits = (0 until bounds.length()).map {
                val item = bounds.getJSONObject(it); Bound(Metric.valueOf(item.getString("metric")), quantity(item.getJSONObject("quantity")))
            }
            require(observations.all { it.option in options && (it.metric == Metric.TIME) == (it.quantity.unit == "minutes") })
            require(limits.all { (it.metric == Metric.TIME) == (it.quantity.unit == "minutes") })
            require(observations.distinctBy { it.option to it.metric }.size == observations.size && limits.distinctBy { it.metric }.size == limits.size)
            val priority = json.optString("priority").takeUnless { it == "null" || it.isBlank() }?.let(Metric::valueOf)
            val view = json.optString("view").takeUnless { it == "null" || it.isBlank() }?.let(Metric::valueOf)
            val pending = json.optJSONObject("pending")?.let { p -> Question(p.getString("option"), Metric.valueOf(p.getString("metric")),
                p.optString("currency").takeUnless { it == "null" || it.isBlank() }, p.optString("deferredCost").takeUnless { it == "null" || it.isBlank() }?.toBigDecimal(),
                p.optString("deferredUpper").takeUnless { it == "null" || it.isBlank() }?.toBigDecimal()) }
            require(pending == null || pending.option in options && (pending.currency == null || pending.currency in setOf("INR", "USD", "EUR", "GBP")) &&
                (pending.deferredCost == null || pending.metric == Metric.COST && pending.deferredCost >= BigDecimal.ZERO && pending.deferredCost <= BigDecimal("1000000000000")))
            require(pending?.deferredUpper == null || pending.deferredCost != null && pending.deferredUpper >= pending.deferredCost && pending.deferredUpper <= BigDecimal("1000000000000"))
            val criteria = DiscussionCriteria.read(json.optJSONObject("criteria"), options)
            require(pending == null || criteria.pending == null)
            require(priority == null || criteria.primary == null)
            val weights = json.optJSONObject("weights")?.let { w ->
                w.keys().asSequence().associate { key -> Metric.valueOf(key) to w.getInt(key).also { require(it in 1..100) } }
            }.orEmpty()
            require(weights.isEmpty() || weights.keys == setOf(Metric.COST, Metric.TIME))
            return DiscussionComparison(observations, limits, priority, pending, view, criteria, weights)
        }
    }
}
