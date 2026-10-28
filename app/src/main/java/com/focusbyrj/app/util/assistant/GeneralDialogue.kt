package com.focusbyrj.app.util.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Read-only dialogue engine. It receives no Context, repositories, session or action capabilities. */
internal object GeneralDialogue {
    data class Style(val questions: Boolean, val listening: Boolean, val playful: Boolean)
    data class Turn(val topic: String, val text: String, val frame: JSONObject, val rule: String)
    private data class Fact(val key: String, val value: String, val tentative: Boolean, val source: String) {
        fun json() = JSONObject().put("key", key).put("value", value).put("tentative", tentative).put("source", source.take(500))
    }
    private data class Answer(val id: String, val short: String, val reason: String, val example: String) {
        fun json() = JSONObject().put("id", id).put("short", short).put("reason", reason).put("example", example)
    }
    private fun values(j: JSONObject, key: String): List<String> = j.optJSONArray(key)?.let { a ->
        (0 until minOf(a.length(), 20)).map { a.optString(it).take(500) }
    }.orEmpty()
    private fun has(text: String, pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
    private val question = Regex("^(?:what|why|how|where|when|who|which|can|could|would|should|do|does|is|are)\\b")
    private val followups = setOf("why", "why that", "why is that", "why do you say that", "how so", "explain that",
        "shorter", "simpler", "keep it short", "in simple terms", "give me an example", "an example", "for example",
        "more", "tell me more", "go on", "more detail", "continue")
    private val summaries = setOf("what do we have so far", "what have we got so far", "what did we discuss",
        "what were we discussing", "what did we decide", "what did i tell you", "what do you remember from this chat", "summarize", "sum it up")
    private val advice = Regex("^(?:what (?:could|can|should) i (?:try|do)(?: (?:about it|instead|to help))?|any (?:ideas|suggestions|advice)|what would you suggest|what do you suggest|give me (?:an idea|advice)|something else|another (?:idea|option)|anything else|what about (?:me|that))$")

    // Frames stay bounded and use the existing encrypted session persistence. This is recall,
    // not the authority lifetime in AssistantContext. It never validates an action reference.
    fun recalled(frame: JSONObject?, now: Long = System.currentTimeMillis()): JSONObject? = frame?.takeIf {
        it.optInt("version") == 1 && now - it.optLong("at") in 0..604_800_000L
    }

    fun answer(input: String, previous: JSONObject?, style: Style, sourceText: String = input): Turn? {
        val localInput = if (Regex("(?i)^(?:(?:could|can|would) you )?(?:possibly )?(?:recommend|suggest) (?:something relaxing|something to unwind|a way to relax)[.!?]*$").matches(input.trim())) "what could I try" else input
        val text = contextualReply(localInput, previous).trim().replace('’', '\'').take(4000)
        val lower = text.lowercase(Locale.ROOT).trimEnd('.', '!', '?').trim()
        val asking = question.containsMatchIn(lower) || text.endsWith('?')
        val old = previous?.optJSONObject("dialogueV2")?.takeIf { it.optInt("version") == 1 }
        val concept = ConversationKnowledge.match(text)
        val explaining = concept != null && has(lower, "^(?:what|why|how|explain|tell me about|can you explain|could you explain)\\b")
        val rejecting = has(lower, "^(?:i (?:don't|do not) (?:like|want|enjoy)|no |not that|that doesn't (?:appeal|sound)|i hate )")
        val previousSuggestion = old?.optJSONObject("answer")?.optString("id")?.startsWith("suggestion:") == true
        val selectedTopic = if (explaining) concept!!.aliases.first() else if (rejecting && previousSuggestion) null else topic(lower)?.let {
            // A short feeling update is usually about the topic already being discussed.
            if (it == "how you're feeling" && old != null && old.optBoolean("closed") != true) null else it
        }
        val active = previous != null
        val topic = selectedTopic ?: previous?.optString("topic")?.takeIf(String::isNotBlank) ?: return null
        val changed = selectedTopic != null && selectedTopic != previous?.optString("topic")
        val frame = if (previous == null || changed) JSONObject().put("version", 1).put("topic", topic)
            .put("notes", JSONArray()).put("stance", "explore").put("questions", true) else JSONObject(previous.toString())
        val data = if (changed || old == null) JSONObject().put("version", 1) else JSONObject(old.toString())
        val facts = data.optJSONArray("facts")?.let { a -> (0 until minOf(a.length(), 20)).mapNotNull {
            a.optJSONObject(it)?.takeIf { f -> f.optString("key").isNotBlank() && f.optString("value").isNotBlank() }
                ?.let { f -> Fact(f.optString("key").take(100), f.optString("value").take(500), f.optBoolean("tentative"), f.optString("source").take(500)) }
        } }.orEmpty().toMutableList()
        val seen = values(data, "acts")
        fun result(body: String, rule: String, note: String? = null, answer: Answer? = null): Turn {
            data.put("acts", JSONArray((seen + rule).takeLast(12))).put("lastRule", rule)
            if (answer != null) data.put("answer", answer.json())
            else if (!rule.startsWith("followup:")) data.remove("answer")
            data.put("responses", JSONArray((values(data, "responses") + body).takeLast(10)))
            if (note != null) frame.put("notes", JSONArray((values(frame, "notes") + (if (note == text) sourceText else note).take(500)).takeLast(10)))
            val notes = values(frame, "notes").toMutableList()
            for (index in facts.indices) {
                val fact = facts[index]
                if (fact.source !in notes) {
                    val source = "${fact.key}: ${fact.value}${if (fact.tentative) " (tentative)" else ""}"
                    facts[index] = fact.copy(source = source)
                    notes += source
                }
            }
            frame.put("notes", JSONArray(notes.distinct().takeLast(10)))
            data.put("facts", JSONArray(facts.takeLast(20).map(Fact::json)))
            frame.put("dialogueV2", data).put("topic", topic)
            return Turn(topic, body, frame, rule)
        }
        fun followUp(a: Answer): String = when {
            lower in setOf("shorter", "simpler", "keep it short", "in simple terms") -> a.short
            lower in setOf("give me an example", "an example", "for example") -> a.example
            lower in setOf("more", "tell me more", "go on", "more detail", "continue") -> a.reason + " " + a.example
            else -> a.reason
        }
        if (explaining) {
            val c = concept!!
            frame.put("concept", c.id)
            val a = Answer("knowledge:${c.id}", c.simple, c.reason, c.example)
            return result(ConversationKnowledge.explain(text, c.id)!!, a.id, answer = a)
        }
        val last = data.optJSONObject("answer")?.let { Answer(it.optString("id"), it.optString("short"), it.optString("reason"), it.optString("example")) }
        if (rejecting && previousSuggestion && last != null && !has(lower, "\\b(?:indoors?|outdoors?)\\b")) {
            val named = when {
                has(lower, "\\b(?:books?|reading)\\b") -> "suggestion:quiet-reading"
                has(lower, "\\b(?:music|songs?|albums?)\\b") -> "suggestion:music"
                has(lower, "\\bwalk(?:ing)?\\b") -> "suggestion:walk"
                has(lower, "\\b(?:that|it)\\b") -> last.id
                else -> null
            }
            if (named != null) {
                val excluded = (values(data, "excluded") + named).distinct().takeLast(12)
                data.put("excluded", JSONArray(excluded))
                val next = GeneralDialoguePacks.suggestions(topic, facts.filterNot { it.tentative }.associate { it.key to it.value })
                    .firstOrNull { it.id !in excluded }
                if (next != null) {
                    val a = Answer(next.id, next.text, next.reason, next.example)
                    return result("We'll leave that out. ${a.short}", "replace:${a.id}", text, a)
                }
                data.remove("answer")
                return result("We'll leave that out. I don't have another suitable suggestion with the current preferences.", "no-options", text)
            }
        }
        if (active && lower in followups && last != null) return result(followUp(last), "followup:${last.id}:$lower")
        if (old != null && lower in followups && last == null)
            return result(if (seen.lastOrNull() == "clarify:reference") "I haven't identified what you mean. You can name the detail, ask for a recap, or explore another activity." else "Which detail would you like me to explain?", "clarify:reference")
        if (active && lower in summaries) {
            val details = facts.map { "${it.key}: ${it.value}${if (it.tentative) " (tentative)" else ""}" }
            val notes = values(frame, "notes").filter { note -> facts.none { it.source == note } }.takeLast(3)
            val lines = (details + notes).distinct().takeLast(8)
            return result("We're discussing $topic." + if (lines.isEmpty()) " No details decided yet." else lines.joinToString("\n• ", "\n• "), "recap")
        }
        if (old != null && lower in setOf("that's all", "that's it", "that is all", "end", "done talking", "let's stop", "bye")) {
            data.remove("answer")
            data.put("closed", true)
            return result("Okay. We can pick this up whenever you like.", "close")
        }
        if (old != null && lower in setOf("i disagree", "that's not what i meant", "you misunderstood", "that doesn't help")) {
            data.put("repair", true)
            return result(if (last != null) "Which part of ‘${last.short}’ doesn't fit?" else "Which detail should I correct?", "repair")
        }
        if (old != null && lower in setOf("that's my preference", "that is my preference", "use that preference", "keep that preference")) {
            val candidates = facts.filter { it.tentative }
            if (candidates.size != 1) return result(if (candidates.isEmpty()) "There isn't a tentative preference to confirm." else "Which tentative preference should I keep?", "clarify:preference")
            val chosen = candidates.single()
            val removed = facts.filter { it.key == chosen.key }.map { it.source }
            facts.removeAll { it.key == chosen.key }
            val confirmed = chosen.copy(tentative = false, source = "${chosen.key}: ${chosen.value}")
            facts += confirmed
            frame.put("notes", JSONArray(values(frame, "notes").filterNot { it in removed }))
            return result("Kept ${chosen.value} as your ${chosen.key} preference for this conversation.", "confirm:preference", confirmed.source)
        }
        if (old != null && lower in setOf("forget that possibility", "drop that possibility", "leave that undecided")) {
            val candidates = facts.filter { it.tentative }
            if (candidates.size != 1) return result(if (candidates.isEmpty()) "There isn't a tentative preference to drop." else "Which possibility should I drop?", "clarify:possibility")
            val chosen = candidates.single()
            facts.remove(chosen)
            frame.put("notes", JSONArray(values(frame, "notes").filterNot { it == chosen.source }))
            return result("Dropped that possibility. Your firm preferences stay as they were.", "drop:possibility")
        }
        // A mention, question or quoted example is not an assertion about the user.
        val extracted = if (asking || has(lower, "^(?:my (?:friend|family|colleague|boss)|he |she |they |you |what if |suppose |imagine )")) emptyList()
            else extract(lower.replace(Regex("\"[^\"]*\"|“[^”]*”|`[^`]*`"), ""), sourceText)
        val correction = has(lower, "^(?:actually|i meant|no[, ]|rather|instead)\\b")
        if (!asking && old != null && has(lower, "\\bindoors?\\s+(?:or|and)\\s+outdoors?\\b|\\boutdoors?\\s+(?:or|and)\\s+indoors?\\b")) {
            return result("We can keep both settings as options. I haven't made either a firm preference.", "alternatives:setting", text)
        }
        if (extracted.isNotEmpty()) {
            val removedSources = mutableSetOf<String>()
            extracted.forEach { fact ->
                // A correction changes only the identified dimension, never the last arbitrary note.
                facts.filter { it.key == fact.key && (!fact.tentative || it.tentative) }.forEach { removedSources += it.source }
                facts.removeAll { it.key == fact.key && (!fact.tentative || it.tentative) }
                facts += fact
            }
            if (removedSources.isNotEmpty()) {
                // A single sentence can supply several facts. Keep unaffected dimensions
                // available to the existing archive/handoff without retaining a stale claim.
                val retained = mutableListOf<String>()
                for (index in facts.indices) {
                    val fact = facts[index]
                    if (fact.source in removedSources && fact !in extracted) {
                        val source = "${fact.key}: ${fact.value}${if (fact.tentative) " (tentative)" else ""}"
                        facts[index] = fact.copy(source = source)
                        retained += source
                    }
                }
                frame.put("notes", JSONArray((values(frame, "notes").filterNot { it in removedSources } + retained).takeLast(10)))
            }
            data.remove("repair"); data.remove("closed")
            if (extracted.all { it.tentative }) return result("We can consider ${extracted.joinToString(", ") { it.value }}. I'll keep that separate from your firm preferences.", "possibility", text)
            if (correction) {
                data.remove("answer") // Do not explain a suggestion based on a superseded constraint.
                return result("Got it — ${extracted.joinToString(", ") { "${it.key}: ${it.value}${if (it.tentative) " for now" else ""}" }}.", "correct:${extracted.joinToString { it.key }}", text)
            }
        }
        if (active && correction && extracted.isEmpty()) {
            val replacement = Regex("^(?:actually[, ]+|i meant )(.+?) (?:not|instead of) (.+)$").matchEntire(lower)
            val notes = values(frame, "notes")
            val target = replacement?.groupValues?.get(2)
            val matches = if (target != null) notes.filter { it.contains(target, ignoreCase = true) } else emptyList()
            if (matches.size == 1) {
                val updated = notes.map { if (it == matches.single()) text else it }
                frame.put("notes", JSONArray(updated))
                facts.removeAll { it.source == matches.single() }
                data.remove("answer")
                return result("Thanks for correcting that: ${replacement!!.groupValues[1]}.", "correct:note")
            }
            return result("Which earlier detail are you correcting?", "clarify:correction")
        }
        if (old != null && advice.matches(lower)) {
            if (style.listening && lower in setOf("something else", "anything else")) return result("I'm listening. You can take it in whichever direction you want.", "listen")
            val excluded = values(data, "excluded").toMutableList()
            if (lower in setOf("something else", "another idea", "another option", "anything else") && last != null) excluded += last.id
            data.put("excluded", JSONArray(excluded.distinct().takeLast(12)))
            val options = GeneralDialoguePacks.suggestions(topic, facts.filterNot { it.tentative }.associate { it.key to it.value })
            val chosen = options.firstOrNull { it.id !in excluded }
            if (chosen != null) {
                val a = Answer(chosen.id, chosen.text, chosen.reason, chosen.example)
                return result(a.short, "suggest:${a.id}", answer = a)
            }
            return result(if (options.isEmpty()) "What would you like to be different about $topic?" else "We've covered the options I have for those preferences. Which constraint could change?", "clarify:options")
        }
        // A location restriction can revise the last suggestion immediately without replaying it.
        if (old != null && extracted.isNotEmpty() && last?.id?.startsWith("suggestion:") == true) {
            val next = GeneralDialoguePacks.suggestions(topic, facts.filterNot { it.tentative }.associate { it.key to it.value })
                .firstOrNull { it.id != last.id && it.id !in values(data, "excluded") }
            if (next != null) {
                val a = Answer(next.id, next.text, next.reason, next.example)
                return result("${extracted.joinToString(", ") { it.value.replaceFirstChar(Char::uppercase) }} — ${a.short.replaceFirstChar(Char::lowercase)}", "revise:${a.id}", text, a)
            }
        }
        if (!asking && (selectedTopic != null || extracted.isNotEmpty() || old != null && has(lower, "^(?:i |i'm |my |because |but |it |that |not |nothing )"))) {
            data.remove("closed")
            val listening = style.listening || frame.optString("stance") == "listen"
            val firmFacts = facts.filterNot { it.tentative }.associate { it.key to it.value }
            val response = if (listening) {
                if (seen.lastOrNull() == "listen") "Take your time. You don't need to turn this into a solution." else "I'm here. We can leave advice aside."
            } else GeneralDialoguePacks.reflect(topic, lower, firmFacts, seen, style.questions, style.playful)
            val recentResponses = values(data, "responses")
            val body = if (response !in recentResponses) response else {
                val alternatives = GeneralDialoguePacks.elaborations(topic)
                alternatives.firstOrNull { it !in recentResponses } ?: if (style.questions && !listening)
                    "We've reached the edge of this discussion pack. I can recap what you shared, offer a supported suggestion, or help you choose another activity through Explore Ayva." else "We can leave this here. You can ask for a recap or explore another activity whenever you like."
            }
            val explanation = GeneralDialoguePacks.explanation(topic, firmFacts)
            return result(body, if (listening) "listen" else "reflect:$topic", text,
                Answer("reflection:$topic", body, explanation.first, explanation.second))
        }
        if (old != null && (question.containsMatchIn(lower) || text.endsWith('?'))) {
            return result(if (has(lower, "\\b(?:today|latest|current|live|price|weather|score)\\b"))
                "I can't check live information. If you share the details, we can discuss what they mean."
            else "I don't have a reliable answer to that in my offline knowledge. I can recap this discussion or show the activities I do support—say ‘explore Ayva’.", "unknown")
        }
        return null
    }

    // Resolve only answers offered by this experience's own question. Keep original
    // wording as sourceText so normalization never invents a user quotation.
    private fun contextualReply(input: String, previous: JSONObject?): String {
        if (input.trim().endsWith('?')) return input
        val short = input.trim().lowercase(Locale.ROOT).trimEnd('.', '!', '?')
        val topic = previous?.optString("topic") ?: return input
        val prompted = previous.optJSONObject("dialogueV2")?.optJSONObject("answer")?.optString("short")?.contains('?') == true
        if (topic != "unwinding" && !prompted) return input
        return when (topic) {
            "study stress" -> when (short) {
                "remembering it", "remembering", "memory", "recall" -> "I keep forgetting the study material"
                "the amount", "amount", "the amount to cover", "syllabus", "too much" -> "I have too much material to cover"
                "focus", "distraction", "distractions", "staying focused" -> "I get distracted while studying"
                "the exam", "exam itself", "exam" -> "I feel exam pressure"
                else -> input
            }
            "work frustrations" -> when (short) {
                "interruptions", "being interrupted" -> "I keep getting interrupted at work"
                "workload", "the workload" -> "My workload is frustrating"
                else -> input
            }
            "unwinding" -> when (short) {
                "inside", "indoors" -> "I prefer indoors"
                "outside", "outdoors" -> "I prefer outdoors"
                "alone", "by myself" -> "I prefer alone time"
                else -> input
            }
            else -> input
        }
    }

    private fun topic(text: String): String? = when {
        has(text, "^(?:i |i'm |my |let's talk about ).*\\b(?:quiet evenings?|crowds?|alone time|unwind|relax|recharge)\\b") || has(text, "^crowds? (?:wear|tire)") -> "unwinding"
        has(text, "^(?:i |i'm |my |let's talk about ).*\\b(?:studying|study|exam|revision)\\b") && has(text, "\\b(?:worried|worry|stressed|stress|overwhelmed|nervous|anxious|struggling)\\b") -> "study stress"
        has(text, "^(?:i |i'm |my |let's talk about ).*\\b(?:work|boss|job|colleague)\\b") && has(text, "\\b(?:frustrat|stress|overwhelm|exhaust|interrupt|annoy)\\w*") -> "work frustrations"
        has(text, "^(?:i |i'm |my ).*\\b(?:reading|books?|novels?)\\b") -> "reading interests"
        has(text, "^(?:i |i'm |my ).*\\b(?:music|songs?|albums?)\\b") -> "music interests"
        has(text, "^(?:i |i'm |my ).*\\b(?:cooking|recipes?|baking)\\b") -> "cooking interests"
        has(text, "^(?:i'm|i am|i feel|i've been|i have been) (?:really |so |a bit )?(?:tired|sad|lonely|stressed|overwhelmed|frustrated|happy|excited)\\b") -> "how you're feeling"
        else -> null
    }

    private fun extract(text: String, original: String): List<Fact> {
        val tentative = has(text, "\\b(?:maybe|perhaps|might|possibly)\\b")
        fun denied(word: String) = has(text, "\\b(?:not|nothing|no|avoid|without|don't (?:want|like|prefer)|do not (?:want|like|prefer)) (?:to be )?$word\\b")
        return buildList {
            fun fact(key: String, value: String) { add(Fact(key, value, tentative, original)) }
            when {
                has(text, "\\bindoors?\\s+(?:or|and)\\s+outdoors?\\b|\\boutdoors?\\s+(?:or|and)\\s+indoors?\\b") -> Unit
                (denied("outdoors?") || has(text, "\\bindoors?\\b")) && !denied("indoors?") -> fact("setting", "indoors")
                (denied("indoors?") || has(text, "\\boutdoors?\\b")) && !denied("outdoors?") -> fact("setting", "outdoors")
            }
            if (has(text, "\\bquiet\\b") && !denied("quiet") && !has(text, "\\b(?:dislike|hate) quiet\\b")) fact("atmosphere", "quiet")
            if (has(text, "\\bcrowds? (?:wear|tire)|\\b(?:avoid|hate|dislike) crowds?\\b|\\bno crowds\\b")) fact("company", "avoid crowds")
            if (has(text, "\\b(?:by myself|on my own|alone)\\b") && !has(text, "\\b(?:not|don't want to be) alone\\b")) fact("company", "alone")
            if (has(text, "\\bwith (?:a friend|friends|family)\\b") && !denied("with (?:a friend|friends|family)")) fact("company", "with ${if (text.contains("family")) "family" else "friends"}")
            if (has(text, "\\b(?:exhausted|tired|low energy)\\b") && !has(text, "\\bnot (?:exhausted|tired)\\b")) fact("energy", "low")
            if (has(text, "\\b(?:forget|forgetting|can't remember)\\b") && !has(text, "\\b(?:don't|do not|never) forget\\b|\\bnot forgetting\\b")) fact("study difficulty", "recalling material")
            if (has(text, "\\b(?:too much to cover|amount to cover|too much material|syllabus feels huge)\\b")) fact("study difficulty", "amount to cover")
            if (has(text, "\\b(?:distracted|distraction|staying focused|lose focus)\\b")) fact("study difficulty", "staying focused")
            if (has(text, "\\b(?:exam itself|exam pressure|test anxiety)\\b")) fact("study difficulty", "exam pressure")
            if (has(text, "\\binterrupt(?:ions|ed|ing)?\\b")) fact("work difficulty", "interruptions")
            Regex("^(?:i (?:like|love|enjoy|prefer)|i'm interested in) (.{1,160})$").matchEntire(text)?.let {
                if (none { f -> f.key in setOf("setting", "atmosphere", "company") }) fact("interest", it.groupValues[1])
            }
        }
    }
}
