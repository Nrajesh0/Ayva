package com.focusbyrj.app.util.assistant

/** Authored subject relations for continuing a discussion, without inventing specific plots or reviews. */
internal object EverydayTopics {
    data class Facet(val id: String, val words: Set<String>, val point: String, val why: String, val example: String)
    data class Turn(val facet: String, val text: String, val preference: String? = null)
    private val topics = mapOf(
        "music" to listOf(
            Facet("words", setOf("lyrics", "words", "meaning", "story", "voice", "singer"),
                "Lyrics give a song a point of view. The words and the way a voice delivers them can suggest different moods, even over the same melody.",
                "Phrasing changes emphasis: a pause, a stretched syllable or a quiet delivery can make the same line feel different. The listener's own associations also matter.",
                "Think of one line sung softly and then shouted. Its literal meaning stays the same while the emotional emphasis can change."),
            Facet("rhythm", setOf("beat", "rhythm", "dance", "drums", "tempo"),
                "Rhythm can make music feel steady, restless or spacious. It is also possible to enjoy a groove without paying much attention to the words.",
                "Repeated patterns create expectations. A pause or an unexpected accent can stand out because it changes a pattern you were following.",
                "Clap a steady beat, then leave one beat silent. That small gap can draw more attention than another loud clap."),
            Facet("atmosphere", setOf("background", "mood", "calm", "atmosphere", "relaxing", "instrumental"),
                "Sometimes the appeal is the atmosphere rather than a specific line or instrument. Enjoying music in the background is a different kind of attention, not an inferior one.",
                "Texture, pace and repetition can become part of a setting. Familiarity and personal associations help explain why different people find different music calming.",
                "A sparse instrumental and a crowded arrangement can have the same tempo but feel very different in the room.")),
        "reading" to listOf(
            Facet("character", setOf("characters", "character", "people", "relationships"),
                "A character can be interesting because of the gap between what they want, what they say and what they actually do.",
                "That gap lets a reader interpret motives instead of receiving every answer directly. Contradictory behaviour can be meaningful when the story gives it context.",
                "A generous character who cannot accept help offers a tension to explore without needing a villain or a dramatic twist."),
            Facet("world", setOf("world", "fantasy", "setting", "escape", "worldbuilding"),
                "A convincing fictional world often comes from how ordinary details fit together, not just how many places or rules are named.",
                "Consistent consequences help a reader understand what choices cost within that world. Small daily details make large ideas feel inhabited.",
                "A setting can become vivid through how people travel to work, share meals or deal with a rule that makes everyday life awkward."),
            Facet("language", setOf("language", "writing", "prose", "poetry", "style"),
                "The pleasure can be in the sentences themselves: rhythm, precision, an image or an unexpected way of looking at something ordinary.",
                "Sentence length and word choice affect pace and attention. A change in rhythm can make a quiet moment feel as deliberate as an event.",
                "Compare a short sentence after a long paragraph with the same sentence in a list. Its surrounding rhythm changes its impact.")),
        "films" to listOf(
            Facet("atmosphere", setOf("atmosphere", "visuals", "cinematography", "music", "quiet", "mood"),
                "A film can build atmosphere through framing, sound, lighting and what it leaves outside the shot. Dialogue is only one part of how it communicates.",
                "What you see and hear guides attention. Holding a shot or leaving a sound unresolved can create anticipation without explaining it in words.",
                "A familiar room shown in a long silent shot can feel different from the same room shown through rapid cuts and loud music."),
            Facet("ending", setOf("ending", "twist", "ambiguous", "open", "resolution"),
                "An open ending can invite interpretation, while a resolved ending can offer satisfaction. Either needs to fit the questions the story has developed.",
                "Ambiguity is more rewarding when there is enough evidence to support an interpretation. Simply withholding information can feel different from leaving a meaningful tension open.",
                "A story might settle what a character decides while leaving the long-term consequences uncertain."),
            Facet("character", setOf("character", "characters", "acting", "performance"),
                "A performance can reveal a character through hesitation, posture or a reaction that differs from their words.",
                "Those details give the audience something to interpret. They can also make a character feel more complicated than a description of their personality would.",
                "Someone saying ‘I'm fine’ while avoiding a familiar place gives the audience two kinds of evidence to weigh.")),
        "cooking" to listOf(
            Facet("experiment", setOf("experiment", "experimenting", "creative", "flavour", "flavors", "flavours"),
                "Experimenting in cooking is easier to learn from when you know what changed. Adjusting several things at once can make a result hard to explain.",
                "A single change lets you connect an outcome to a possible cause. It does not establish a universal recipe; ingredients and equipment still matter.",
                "Comparing two versions of the same familiar dish with one seasoning difference can tell you more than changing every ingredient."),
            Facet("process", setOf("process", "relaxing", "baking", "routine", "recipe", "recipes"),
                "Following a recipe can be satisfying in its own right. A familiar sequence can leave room to notice texture, smell and small changes along the way.",
                "There is a difference between enjoying the activity and wanting the finished dish. Recognizing which matters more can explain why a simple meal sometimes feels more rewarding.",
                "Preparing a familiar dish slowly for yourself and preparing the same dish in a rush can be quite different experiences.")),
        "coding" to listOf(
            Facet("building", setOf("building", "build", "project", "app", "website", "making"),
                "Building something gives an idea a concrete shape. A small version can be satisfying because you can use it and see what is missing.",
                "A usable result gives more specific feedback than an abstract wish list. It also makes the next decision about scope easier to discuss.",
                "A tiny tool that does one useful thing can teach you more about its users than a long list of planned features."),
            Facet("puzzle", setOf("puzzle", "debugging", "bugs", "problem", "solving", "understanding"),
                "Debugging has a particular kind of curiosity: the behaviour is evidence, and the explanation has to account for it.",
                "A good hypothesis predicts something you can check. Changing several things without checking the hypothesis can make the cause harder to identify.",
                "If a bug appears only after resuming an app, comparing the state before and after resumption is a more focused starting point than rewriting unrelated screens.")),
        "walking" to listOf(
            Facet("noticing", setOf("details", "photography", "notice", "noticing", "colours", "colors", "sounds"),
                "A familiar place can become interesting through a detail you usually pass by: a reflection, a repeated shape or a sound behind the traffic.",
                "Attention changes what stands out. The place may be the same while the detail you follow gives it a different character.",
                "A street can be described through its colours one day and through its sounds another, with quite different results."),
            Facet("quiet", setOf("quiet", "nature", "peace", "calm", "scenery", "alone"),
                "The appeal can simply be a change of setting and a little space. It doesn't have to become exercise tracking or another achievement.",
                "Different surroundings can give a conversation or a train of thought a different pace. What feels restful depends on the person and the setting.",
                "A short familiar route can be enough when the point is a change of scene rather than reaching somewhere new."))
    )
    fun answer(topic: String, input: String, previousFacet: String?, turn: Int): Turn? {
        if (AssistantInterpretation.interpret(input).kind == UtteranceKind.REQUEST ||
            ConversationMeaning.kind(input) == ConversationMeaning.Kind.COMMAND && !input.trim().equals("give me an example", true) ||
            Regex("(?i)\\b(?:tasks?|habits?|routines?|settings?|drill|focus)\\b").containsMatchIn(input)) return null
        val facets = topics[topic] ?: return null
        val lower = PlanLanguage.normalize(input).lowercase().trimEnd('.', '!', '?')
        val explicit = facets.map { facet -> facet to facet.words.count { word -> Regex("(?<![\\p{L}])${Regex.escape(word)}(?![\\p{L}])").containsMatchIn(lower) } }
            .filter { it.second > 0 }.maxByOrNull { it.second }?.first
        val followup = lower in setOf("why", "why is that", "how", "tell me more", "more", "give me an example", "for example", "simpler", "shorter")
        if (explicit == null && !followup) return null
        val facet = explicit ?: facets.firstOrNull { it.id == previousFacet } ?: return null
        val rejected = Regex("\\b(?:don't|do not|dislike|hate|not into|no longer enjoy)\\b").containsMatchIn(lower)
        val tentative = Regex("\\b(?:maybe|perhaps|might|not sure)\\b").containsMatchIn(lower)
        if (explicit != null && rejected) return Turn(facet.id,
            "We'll set ${facet.id} aside. We can explore ${facets.filter { it.id != facet.id }.joinToString(" or ") { it.id }} instead.", "avoid")
        val preference = if (explicit == null) null else if (tentative) "tentative" else "interested"
        val body = when {
            lower in setOf("give me an example", "for example") -> facet.example
            lower in setOf("why", "why is that", "how", "tell me more", "more") -> facet.why
            lower in setOf("simpler", "shorter") -> facet.point.substringBefore('.') + "."
            explicit?.id == previousFacet && turn > 0 -> facet.why
            else -> facet.point
        }
        return Turn(facet.id, (if (tentative) "A possibility, then. " else "") + body, preference)
    }
}
