package com.focusbyrj.app.util.assistant

/** Original, bounded English content. Alternatives have explicit applicability conditions. */
internal object GeneralDialoguePacks {
    data class Suggestion(val id: String, val text: String, val reason: String, val example: String)
    fun elaborations(topic: String): List<String> = when (topic) {
        "unwinding" -> listOf("Rest can mean less noise, fewer decisions, or simply having time that's yours.", "An activity you usually enjoy can still feel like effort when you're drained.")
        "study stress" -> listOf("A difficult practice session is information about that session, not a verdict on your ability.", "Comparing your progress with someone else's can hide how different your starting points are.")
        "work frustrations" -> listOf("Being busy and making progress aren't always the same thing.", "It can be hard to switch off when the expectations keep changing.")
        "reading interests" -> listOf("It's fine to leave a book unfinished when it isn't giving you what you wanted.", "Sometimes a familiar story suits you better than finding a new favourite.")
        "music interests" -> listOf("The same song can land differently depending on what is happening in your life.", "You don't have to explain your taste for it to be yours.")
        "cooking interests" -> listOf("Repeating a recipe can be satisfying too; every meal doesn't need to be an experiment.", "Texture and timing can change a dish as much as its ingredients.")
        else -> listOf("You don't have to arrive at a neat conclusion to talk about it.", "We can focus on one part rather than trying to untangle everything at once.")
    }
    fun explanation(topic: String, facts: Map<String, String>): Pair<String, String> = when (topic) {
        "unwinding" -> "I'm connecting your preferences with how much effort a break would take. ${if (facts["company"] == "avoid crowds") "You said crowds wear you out, so a crowded setting would work against that." else "What feels restful depends on you, rather than how productive an activity looks."}" to
            "For example, a quiet evening can feel restorative even if you don't finish a book or accomplish anything measurable."
        "study stress" -> "I'm separating how studying feels from evidence about what you can recall. Worry alone doesn't measure your preparation." to
            "You might feel unsure about a chapter but recall most of it when you try; the remaining gaps give you something specific to revisit."
        "work frustrations" -> "Workload, interruptions and unclear expectations can produce similar frustration but need different responses." to
            "An interrupted afternoon may leave work unfinished even when you put in plenty of effort; that differs from not knowing how to do it."
        "reading interests" -> "Different books suit different moods and purposes. Enjoyment doesn't have to follow a fixed genre or reading target." to "You might enjoy a challenging book on one day and want the comfort of a familiar story on another."
        "music interests" -> "A preference can come from the sound, words, memories or mood associated with a song." to "A song you don't usually choose might become a favourite because it reminds you of a particular moment."
        "cooking interests" -> "Familiar techniques reduce the number of things you need to figure out while trying something new." to "Keeping the cooking method familiar while changing one seasoning makes the effect easier to notice."
        else -> "I'm responding to what you shared, rather than assuming you need immediate advice. You can choose whether to explore it or simply talk." to "You can describe a difficult day without needing to turn it into a list of things to fix."
    }
    fun suggestions(topic: String, facts: Map<String, String>): List<Suggestion> = when (topic) {
        "unwinding", "how you're feeling" -> buildList {
            if (facts["setting"] != "outdoors") {
                add(Suggestion("suggestion:quiet-reading", "Try a quiet reading break at home, with no target to finish anything.",
                    "It's an indoor option with little setup, and you can stop whenever you want. It also avoids crowds.",
                    "Pick a familiar book, settle somewhere comfortable, and read only as long as it feels restful."))
                add(Suggestion("suggestion:music", "Put on a familiar album and give yourself permission to do nothing else.",
                    "Familiar music gives the break a little structure without adding another job to complete.",
                    "Choose something you already enjoy rather than spending the whole break searching for the perfect playlist."))
            }
            if (facts["setting"] != "indoors" && facts["energy"] != "low") add(Suggestion("suggestion:walk",
                "A short walk somewhere familiar and quiet could be a change of pace.",
                "A familiar route reduces decisions; choosing a quieter time or place may help avoid crowds.",
                "You could take the quieter route near home and turn back whenever you've had enough."))
        }
        "study stress" -> listOfNotNull(
            if (facts["study difficulty"] == "amount to cover") Suggestion("suggestion:study-scope", "Choose one essential topic and identify its smallest useful practice question.", "Reducing the scope gives you a place to begin without pretending the whole syllabus has become small.", "Pick one concept from a chapter and check whether you can explain it before adding the rest.") else null,
            if (facts["study difficulty"] == "staying focused") Suggestion("suggestion:study-focus", "Remove one avoidable interruption and choose a clear stopping point for this attempt.", "A bounded attempt makes it easier to notice whether the obstacle is the environment, the material or your energy.", "Put unrelated tabs aside, answer one question, then check whether you need a break or a clearer explanation.") else null
        ) + listOf(Suggestion("suggestion:recall", "Try one small topic: recall what you can, then check just the gaps.",
            "A small recall attempt shows a specific next step instead of leaving you with the feeling that everything needs work.",
            "Close one page of notes, explain its main idea aloud, then reopen it to check what you missed."),
            Suggestion("suggestion:study-pressure", "Separate what you can practise from what you can't control about the result.",
                "A result is uncertain; a concrete piece of practice is something you can choose and adjust.",
                "Instead of trying to guarantee a score, choose one kind of mistake to understand better."))
        "work frustrations" -> listOf(Suggestion("suggestion:work-boundary", "Identify the one interruption or expectation causing the most friction, then address that specifically.",
            "A specific request is easier to act on than a general complaint about everything being stressful.",
            "For repeated interruptions, you might ask a colleague to collect non-urgent questions for a single check-in."),
            Suggestion("suggestion:work-priority", "Ask which outcome matters most when several requests compete.",
                "Making the trade-off explicit helps avoid silently accepting incompatible expectations.",
                "You could say: ‘I can finish this first or switch to that. Which is more urgent?’"))
        "reading interests" -> listOf(Suggestion("suggestion:reading", "Choose by the reading experience you want: comfort, suspense, or learning something.",
            "That gives you a useful filter without needing a list of fashionable titles.", "For a restful evening, revisiting a favourite can be easier than starting something demanding."))
        "music interests" -> listOf(Suggestion("suggestion:listening", "Pick one song you keep returning to and notice what draws you back.",
            "The rhythm, lyrics or atmosphere can give you a clearer sense of what you enjoy.", "Listen once for the words and once for the instruments; you may notice a different favourite part."))
        "cooking interests" -> listOf(Suggestion("suggestion:cooking", "Start from an ingredient you already have and a technique you feel comfortable with.",
            "That keeps the experiment manageable while leaving room to change one thing.", "Use a familiar recipe and vary one seasoning rather than changing every ingredient at once."))
        else -> emptyList()
    }

    fun reflect(topic: String, text: String, facts: Map<String, String>, acts: List<String>, questions: Boolean, playful: Boolean): String {
        val continuing = acts.any { it == "reflect:$topic" }
        return when (topic) {
            "unwinding" -> when {
                facts["company"] == "avoid crowds" -> "Then a quieter setting matters as much as the activity itself. A break shouldn't leave you more drained."
                facts["setting"] != null -> "${facts.getValue("setting").replaceFirstChar(Char::uppercase)} fits what you're considering. We can keep the other preferences in view too."
                continuing -> "Having some unstructured time can be the point; you don't have to make the evening productive."
                else -> "A quiet evening can be a welcome reset.${if (playful) " No achievement badge required." else " It doesn't have to turn into another project."}"
            }
            "study stress" -> when {
                facts["study difficulty"] == "amount to cover" -> "A large amount to cover can make every starting point feel wrong. Separating essential material from optional detail can make the next choice clearer."
                facts["study difficulty"] == "staying focused" -> "Losing focus can come from interruptions, unclear next steps or low energy. We can work with the part you actually notice rather than treating it as a character flaw."
                facts["study difficulty"] == "exam pressure" -> "The exam can feel threatening even when some preparation is going well. Practising a small part under familiar conditions can make the unknowns more specific."
                facts["study difficulty"] == "recalling material" -> "Forgetting during practice can feel discouraging. It also identifies what needs another pass, rather than proving you know nothing."
                continuing -> "Let's separate the amount left to cover from the pressure you're putting on the outcome. They need different responses."
                questions -> "That sounds like a lot of pressure. Is it the amount to cover, remembering it, or the exam itself that's worrying you most?"
                else -> "That sounds like a lot of pressure. Feeling worried doesn't tell us how well prepared you actually are."
            }
            "work frustrations" -> when {
                facts["work difficulty"] == "interruptions" -> "Repeated interruptions make it hard to feel finished with anything, even when you're working all day."
                continuing -> "It may help to separate the workload itself from unclear expectations around it."
                questions -> "What has been most frustrating: the workload, interruptions, or how people are treating you?"
                else -> "Work can be draining when the effort you put in doesn't translate into a sense of progress."
            }
            "reading interests" -> if (continuing) "What you enjoy about a book can matter more than its genre label." else "Books can offer very different things: escape, a fresh perspective, or simply good company."
            "music interests" -> if (continuing) "Sometimes it's a particular sound or lyric that makes a song stick, rather than the whole genre." else "Music can fit a mood or change it. Familiar favourites and new discoveries have different appeals."
            "cooking interests" -> if (continuing) "Getting comfortable with a technique gives you more room to improvise." else "Cooking has a nice balance of familiarity and experimentation. You can change one thing and taste the difference."
            else -> when {
                Regex("\\b(?:happy|excited)\\b").containsMatchIn(text) -> "That sounds like something worth enjoying without immediately moving on to the next thing."
                facts["energy"] == "low" -> "You sound worn out. We can keep this easy; there's no need to solve everything right now."
                continuing -> "We can stay with what you're feeling without rushing to explain it away."
                else -> "I'm listening.${if (questions) " What's been weighing on you?" else " We can take this at your pace."}"
            }
        }
    }
}
