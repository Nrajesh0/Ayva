package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.AyvaTalkEngine
import com.focusbyrj.app.util.FocusSessionManager
import com.focusbyrj.app.util.TalkAction
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Planning context is ephemeral and stores identities, never executable commands. */
data class AssistantPlanContext(
    val createdAt: Long = System.currentTimeMillis(),
    val minutes: Int? = null,
    val limit: Int = 3,
    val order: String = "priority",
    val todayOnly: Boolean = false,
    val excluded: List<Long> = emptyList()
) {
    fun live() = System.currentTimeMillis() - createdAt in 0..900_000L
    fun toJson() = JSONObject().put("at", createdAt).put("minutes", minutes ?: JSONObject.NULL).put("limit", limit)
        .put("order", order).put("today", todayOnly).put("excluded", JSONArray(excluded.take(100)))
    companion object {
        fun fromJson(j: JSONObject): AssistantPlanContext {
            val minutes = if (j.isNull("minutes")) null else j.getInt("minutes").also { require(it in 1..480) }
            return AssistantPlanContext(j.getLong("at"), minutes, j.getInt("limit").also { require(it in 1..5) },
                j.getString("order").also { require(it in setOf("priority", "due")) }, j.optBoolean("today"), j.optJSONArray("excluded")?.longs()?.distinct()?.take(100).orEmpty())
        }
    }
}

/** A plan allocates work blocks; it never invents task completion estimates or reschedules items. */
object AssistantPlanning {
    private val nextRequests = setOf("what should i do next", "what next", "help me prioritize", "help me prioritise", "what should i focus on", "help me focus", "motivate me", "i don't know where to start")
    private val dayRequests = setOf("plan my day", "help me plan my day")
    private val planRequests = setOf("make a plan", "help me plan", "plan my tasks")
    private val timeRequests = setOf("plan my time", "help me plan my time", "plan the next", "plan my next")
    private val followUps = setOf("show my plan", "back to my plan", "continue my plan", "skip that one", "skip this one", "another option", "something else", "next task", "only today", "include future tasks", "priorities first", "due dates first", "include skipped tasks", "why this order", "why that one", "why")
    private fun clean(input: String) = input.lowercase().trim().trimEnd('.', '!', '?')
    private fun overwhelmed(input: String) = Regex("\\b(?:tired|exhausted|stressed|overwhelmed|distracted|procrastinating|lazy|stuck|burned out)\\b").containsMatchIn(input)
    private fun budgetText(input: String): String? {
        val lower = clean(input).replaceFirst(Regex("^(?:actually|instead)[, ]+"), "")
        Regex("^(?:help me )?plan my day (?:for|with) (.+)$").matchEntire(lower)?.let { return it.groupValues[1] }
        val plan = Regex("^(?:plan (?:my |the )?next|plan for|help me plan for) (.+)$").matchEntire(lower)
        if (plan != null) return plan.groupValues[1]
        val have = Regex("^(?:i have got|i've got|i (?:only )?have|i can spare) (.+?)(?: (?:free|available))?$").matchEntire(lower)?.groupValues?.get(1) ?: return null
        return have.takeIf { Regex("(?:\\b(?:minutes?|mins?|hours?|hrs?)\\b|\\d\\s*[mh]\\b)").containsMatchIn(it) }
    }
    fun isDirectRequest(input: String): Boolean {
        val lower = clean(input)
        return lower in nextRequests + dayRequests + planRequests + timeRequests || budgetText(lower) != null
    }
    fun minutes(input: String): Int? {
        val lower = clean(input).removePrefix("for ")
        val words = mapOf("an hour" to 60, "one hour" to 60, "half an hour" to 30, "half hour" to 30, "a minute" to 1,
            "five minutes" to 5, "ten minutes" to 10, "fifteen minutes" to 15, "twenty minutes" to 20, "thirty minutes" to 30)
        words[lower]?.let { return it }
        val match = Regex("^(\\d+(?:\\.\\d+)?)\\s*(minutes?|mins?|m|hours?|hrs?|h)?$").matchEntire(lower) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        val value = number * if (match.groupValues[2].startsWith("h")) 60 else 1
        return value.takeIf { it.isFinite() && it % 1.0 == 0.0 && it in 1.0..480.0 }?.toInt()
    }
    fun budgetAnswer(context: Context, state: AssistantSession, input: String, tasks: List<Task>, interrupted: Boolean): AyvaTalkEngine.TalkResponse? {
        if (state.expected != "plan_budget") return null
        val phrase = budgetText(input)
        if ((interrupted || isDirectRequest(input)) && phrase == null) return null
        val value = minutes(phrase ?: input) ?: return reply("How much time do you have? Use 1–480 whole minutes, for example ‘30 minutes’ or ‘half an hour’.")
        val draft = state.planning?.takeIf { it.live() } ?: run { state.clearPending(); state.planning = null; return reply("That planning question expired. Ask for a new plan.") }
        return render(context, state, tasks, draft.copy(minutes = value))
    }

    fun respond(context: Context, state: AssistantSession, input: String, tasks: List<Task>): AyvaTalkEngine.TalkResponse? {
        val lower = clean(input)
        val budget = if (lower in dayRequests) null else budgetText(lower)
        val direct = isDirectRequest(input)
        val emotion = overwhelmed(lower) && (com.focusbyrj.app.util.OfflineNluEngine.isConversationalStatement(input) ||
            Regex("^(?:tired|exhausted|stressed|overwhelmed|distracted|procrastinating|lazy|stuck|burned out)$").matches(lower)) &&
            !com.focusbyrj.app.util.OfflineNluEngine.isExplicitCreation(input)
        val continuation = lower in followUps || lower.startsWith("skip planned id:")
        val returnToPlan = lower in setOf("show my plan", "back to my plan", "continue my plan")
        if (!direct && !emotion && !(continuation && (state.topic == "planning" || returnToPlan))) return null
        val previous = state.planning?.takeIf { it.live() }
        if (continuation && previous == null) {
            state.planning = null; state.switchTopic("planning"); state.rememberTaskList(emptyList())
            return reply("That plan is no longer available. Say ‘plan my day’ or ‘I have 30 minutes’ for a fresh plan.")
        }
        var plan = if (continuation) previous!! else AssistantPlanContext(
            minutes = budget?.let(::minutes), limit = if (lower in nextRequests || emotion) 1 else AssistantPersonalization.planSize(context),
            order = AssistantPersonalization.planOrder(context), todayOnly = lower in dayRequests || Regex("^(?:help me )?plan my day (?:for|with) ").containsMatchIn(lower))
        if (budget != null && previous != null && state.topic == "planning") plan = previous.copy(minutes = minutes(budget), createdAt = System.currentTimeMillis())
        if (lower in timeRequests || budget != null && minutes(budget) == null) {
            state.clearPending(); state.switchTopic("planning"); state.planning = plan.copy(minutes = null); state.expected = "plan_budget"
            return reply("How much time should the plan cover? Use 1–480 whole minutes, such as ‘30 minutes’ or ‘2 hours’. Saved tasks won't be changed.")
        }
        if (continuation) {
            plan = when (lower) {
                "only today" -> plan.copy(todayOnly = true)
                "include future tasks" -> plan.copy(todayOnly = false)
                "priorities first" -> plan.copy(order = "priority")
                "due dates first" -> plan.copy(order = "due")
                "include skipped tasks" -> plan.copy(excluded = emptyList())
                else -> plan
            }
            if (lower in setOf("skip that one", "skip this one", "another option", "something else", "next task") || lower.startsWith("skip planned id:")) {
                val id = if (lower.startsWith("skip planned id:")) lower.substringAfter(':').toLongOrNull() else state.taskId
                if (id == null || id !in state.taskListIds) return reply("That item isn't in the current displayed plan. Say ‘show my plan’ first.")
                if (plan.excluded.size >= 100) return reply("Start a fresh plan to continue, or say ‘include skipped tasks’. Nothing in your task list has changed.")
                plan = plan.copy(excluded = (plan.excluded + id).distinct())
            }
        }
        return render(context, state, tasks, plan, emotion, explain = lower in setOf("why", "why this order", "why that one"))
    }

    private fun render(context: Context, state: AssistantSession, tasks: List<Task>, plan: AssistantPlanContext, emotion: Boolean = false, explain: Boolean = false): AyvaTalkEngine.TalkResponse {
        val now = System.currentTimeMillis()
        val endToday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val eligible = tasks.filter { !it.isCompleted && !it.isTrashed && it.deletedAt == null && it.id !in plan.excluded && (!plan.todayOnly || it.dueDate == null || it.dueDate < endToday) }
        val comparator = if (plan.order == "due") compareBy<Task> { it.dueDate ?: Long.MAX_VALUE }.thenByDescending { it.isPriority }.thenBy { it.id }
            else compareBy<Task> { if (it.dueDate != null && it.dueDate < now) 0 else 1 }.thenByDescending { it.isPriority }.thenBy { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.id }
        val ordered = eligible.sortedWith(comparator)
        val shown = ordered.take(minOf(plan.limit, plan.minutes ?: 5))
        state.clearPending(); state.switchTopic("planning"); state.planning = plan
        if (shown.isEmpty()) {
            state.rememberTaskList(emptyList())
            return reply(if (tasks.none { !it.isCompleted && !it.isTrashed && it.deletedAt == null }) "Your task list is clear. Tell me one thing you'd like to get done."
                else "No tasks match this plan${if (plan.todayOnly) " for today" else ""}${if (plan.excluded.isNotEmpty()) " after skipping ${plan.excluded.size}" else ""}. Try ‘include future tasks’ or ‘include skipped tasks’. Saved tasks are unchanged.")
        }
        state.focusTask(shown.first().id, "planning"); state.rememberTaskList(shown.map { it.id })
        fun reason(task: Task) = when {
            task.dueDate?.let { it < now } == true -> "overdue"
            plan.order == "due" && task.dueDate != null -> "nearest due dates first"
            task.isPriority -> "marked priority"
            task.dueDate != null -> "upcoming due date"
            else -> "no due date; current task order"
        }
        val preferred = if (emotion) minOf(5, AssistantPersonalization.focusMinutes(context)) else AssistantPersonalization.focusMinutes(context)
        var remaining = plan.minutes ?: 0
        val blocks = shown.mapIndexed { index, _ ->
            val block = if (plan.minutes == null) preferred else minOf(preferred, remaining / (shown.size - index))
            remaining -= if (plan.minutes == null) 0 else block
            block
        }
        val brief = AssistantReplyStyle.concise(context) && !explain
        val rows = shown.mapIndexed { i, task ->
            "${i + 1}. **${task.title}**${if (plan.minutes != null) " — ${blocks[i]}-minute work block" else ""}${if (!brief || shown.size == 1) " (${reason(task)})" else ""}"
        }.joinToString("\n")
        val header = when { emotion -> "Let's make this smaller."; plan.minutes != null -> "A plan for your ${plan.minutes} minutes:"; else -> "I'd start here${if (plan.todayOnly) " for today" else ""}:" }
        val rules = if (explain) "\nOrder: ${if (plan.order == "due") "nearest due dates, then priority for ties" else "overdue first, then priority and nearest due dates"}. ${if (plan.todayOnly) "Future-dated tasks are excluded; undated tasks are included." else "Future-dated and undated tasks are eligible."}" else ""
        val budgetNote = if (plan.minutes != null) "\nThese are work blocks, not estimates of task completion.${if (remaining > 0) " $remaining minutes remain unallocated." else ""}"
            else "\nTry one small step for ${blocks.first()} minutes."
        FocusSessionManager.refresh(context)
        val active = FocusSessionManager.active.value
        val timerNote = if (active) "\nYour focus countdown is already running with ${(FocusSessionManager.remainingSeconds.value + 59) / 60} minute(s) left; this plan hasn't changed it." else ""
        val icon = if (AssistantReplyStyle.emoji(context)) "💬" else ""
        val first = shown.first()
        val actions = listOf(
            TalkAction.AskQuery(if (active) "time remaining" else "focus on id:${first.id} for ${blocks.first()} minutes", if (active) "Timer status" else "Start focus", icon),
            TalkAction.AskQuery("skip planned id:${first.id}", "Another option", icon),
            TalkAction.AskQuery("complete id:${first.id}", "Mark complete", icon))
        return reply("$header\n$rows$rules$budgetNote$timerNote\nSaved tasks are unchanged. You can change this plan without saving a preference.", actions)
    }
    private fun reply(text: String, actions: List<TalkAction> = emptyList()) = AyvaTalkEngine.TalkResponse(text, actions, "planning", AyvaTalkEngine.serializeActionsJson("planning", actions))
}
