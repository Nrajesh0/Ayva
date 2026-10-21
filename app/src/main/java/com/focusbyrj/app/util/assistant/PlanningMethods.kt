package com.focusbyrj.app.util.assistant

/** Authored methods compose across goals; none asserts live prices, timetables or exam rules. */
internal object PlanningMethods {
    data class Step(val title: String, val reason: String, val aliases: Set<String> = emptySet())
    data class Method(val id: String, val matches: Regex, val steps: List<Step>)
    private val methods = listOf(
        Method("travel", Regex("(?i)\\b(trip|travel|holiday|vacation|journey)\\b"), listOf(
            Step("Confirm arrival requirements", "An arrival deadline anchors the journey; check the actual location and required time.", setOf("arrival", "venue")),
            Step("Compare transport options", "Compare arrival time, cost and reliability using actual information before choosing.", setOf("tickets", "train", "transport", "flight")),
            Step("Decide whether accommodation is needed", "Staying nearby can create an arrival buffer; overnight travel may avoid lodging but leaves less recovery time.", setOf("hotel", "stay", "accommodation")),
            Step("Prepare travel essentials", "Gather the documents and belongings you know you need before leaving.", setOf("packing", "pack")))),
        Method("study", Regex("(?i)\\b(study|prepare for|preparation|revision|learn|syllabus)\\b"), listOf(
            Step("Identify topics and starting level", "Use your actual topic list and current strengths to avoid spending equal time on everything.", setOf("topics", "syllabus")),
            Step("Practise a small set of questions", "Trying questions exposes specific gaps that rereading may hide.", setOf("practice", "practise", "questions")),
            Step("Review errors and difficult topics", "Understanding the cause of an error makes the next practice session more useful.", setOf("review", "mistakes")),
            Step("Revisit material after a gap", "Spaced retrieval checks what remains accessible beyond the first session.", setOf("revision", "remember")))),
        Method("exam", Regex("(?i)\\b(exam|ibps|test centre)\\b"), listOf(
            Step("Check official exam instructions", "Confirm your own reporting time, venue and document requirements from the official instructions.", setOf("admit card", "instructions", "exam")))),
        Method("project", Regex("(?i)\\b(project|build|launch|release|deliver|website|app)\\b"), listOf(
            Step("Define the smallest useful outcome", "A concrete result makes scope and completion easier to judge.", setOf("scope", "outcome")),
            Step("Identify prerequisites and resources", "Find blocked work early, before it holds up the main effort.", setOf("resources", "prerequisites")),
            Step("Build a first usable version", "A small complete version gives you something concrete to improve.", setOf("build", "version")),
            Step("Review against the intended outcome", "Check whether the result solves the original need before adding more.", setOf("review", "check")))),
        Method("event", Regex("(?i)\\b(event|party|wedding|celebration|conference)\\b"), listOf(
            Step("Confirm purpose, people and date", "These determine the scale and essential arrangements.", setOf("guests", "date")),
            Step("Arrange the essential resources", "Focus first on what the event cannot work without.", setOf("venue", "supplies")),
            Step("Confirm responsibilities", "Named owners and clear handoffs prevent preparation from falling between people.", setOf("people", "owners")))),
        Method("errands", Regex("(?i)\\b(errands?|shopping|groceries)\\b"), listOf(
            Step("List essentials and locations", "Separate necessities from optional stops before choosing an order.", setOf("list")),
            Step("Check opening times and group nearby stops", "Actual opening times and locations help avoid wasted journeys.", setOf("route", "stops")))),
        Method("routine", Regex("(?i)\\b(routine|habit|morning|evening)\\b"), listOf(
            Step("Choose a small repeatable starting action", "A manageable start is easier to repeat than an ambitious list.", setOf("start", "cue")),
            Step("Try it and review the friction", "Adjust the routine to what actually happens, rather than assuming the first design fits.", setOf("review"))))
    )
    private val generic = Method("general", Regex(".*"), listOf(
        Step("Describe a concrete successful outcome", "A clear result lets us work backwards without pretending to know your subject."),
        Step("Identify what is already done and what is blocking progress", "Your knowledge of the goal supplies the details; I can help organize them."),
        Step("Choose the next useful action", "A small feasible action can move things forward even while other details remain open.")))
    fun groups(goal: String): List<Method> = methods.filter { it.matches.containsMatchIn(goal) || it.id == "study" && Regex("(?i)exam").containsMatchIn(goal) && !Regex("(?i)trip|travel|journey").containsMatchIn(goal) }.ifEmpty { listOf(generic) }
    fun method(id: String): Method? = (methods + generic).firstOrNull { it.id == id }
    fun title(id: String) = when (id) {
        "travel" -> "Travel arrangements"; "study" -> "Study and practice"; "exam" -> "Exam attendance"; "project" -> "Project delivery"
        "event" -> "Event preparation"; "errands" -> "Errands"; "routine" -> "Routine design"; else -> "Work toward the outcome"
    }
    fun domain(id: String) = when (id) { "travel" -> "trip"; "exam" -> "general"; else -> id }
    fun forGoal(goal: String): List<Step> = groups(goal).flatMap { it.steps }.distinctBy { it.title }
    fun reason(goal: String, reference: String): String? {
        val words = reference.lowercase().split(Regex("[^\\p{L}]+" )).filter { it.length > 2 }.toSet()
        return forGoal(goal).filter { step -> step.aliases.any { it in reference.lowercase() } || step.title.lowercase().split(' ').count { it in words } >= 2 }
            .map { it.reason }.distinct().joinToString(" ").takeIf { it.isNotBlank() }
    }
}
