package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AssistantDialogue {
    private fun reply(text: String, actions: List<TalkAction> = emptyList(), topic: String = "conversation") =
        AyvaTalkEngine.TalkResponse(text, actions, topic, AyvaTalkEngine.serializeActionsJson(topic, actions))

    suspend fun answer(context: Context, app: FocusApplication, state: AssistantSession, input: String, tasks: List<Task>): AyvaTalkEngine.TalkResponse? {
        val lower = input.lowercase().trimEnd('.', '!', '?')
        val ordered = tasks.sortedWith(compareByDescending<Task> { it.isPriority }.thenBy { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.id })
        val next = ordered.firstOrNull()
        if (lower in setOf("back to that task list", "back to the task list", "show that task list again", "back to that list")) {
            if (state.taskListIds.isEmpty() || !state.taskListComplete || System.currentTimeMillis() - state.taskListAt !in 0..900_000L)
                return reply("That task list is no longer available. Say ‘show my tasks’ for a fresh list.")
            val byId = app.database.taskDao().getAllActiveTasksList().associateBy { it.id }
            if (state.taskListIds.any { it !in byId }) return reply("An item from that list is no longer available. Say ‘show my tasks’ for a fresh list.")
            state.clearPending(); state.switchTopic("tasks"); state.candidates = state.taskListIds
            return AssistantResponseContent.taskList(reply("Here's the same task list, in the same order, with current details:\n" + state.taskListIds.mapIndexed { index, id ->
                val task = byId.getValue(id)
                "${index + 1}. **${task.title}** — ${task.dueDate?.let(SmartDateParser::formatDueDate) ?: "no due date"}${if (task.isCompleted) " · completed" else ""}"
            }.joinToString("\n"), topic = "tasks"), state.taskListIds.map { byId.getValue(it) }, state.taskListAt)
        }
        if (lower in setOf("hi", "hello", "hey", "hey ayva", "good morning", "good afternoon", "good evening", "howdy")) {
            state.clearPending(); state.switchTopic("conversation")
            val name = context.getSharedPreferences("ayva_personalization", Context.MODE_PRIVATE).getString("name", null)
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Hello" }
            val overdue = tasks.count { it.dueDate?.let { date -> date < System.currentTimeMillis() } == true }
            return reply("$greeting${name?.let { ", $it" }.orEmpty()}. ${if (tasks.isEmpty()) "Your task list is clear." else "You have ${tasks.size} pending task(s)${if (overdue > 0) ", $overdue overdue" else ""}."}\n${next?.let { "Next up: **${it.title}**. " }.orEmpty()}Tell me what you need, or add something directly.", listOf(TalkAction.AskQuery("what should I do next", "Help me choose"), TalkAction.AskQuery("/tasks", "My tasks")), "greeting")
        }
        if (lower in setOf("how are you", "how are you doing", "what's up", "whats up")) return reply("Ready to help. We can organize your tasks, track a habit, or start a focus session. What's on your mind?")
        if (lower in setOf("thanks", "thank you", "thx", "thank you ayva", "thanks ayva", "great", "perfect", "nice")) return reply(if (state.taskId != null) "You're welcome. You can keep going, change that task, or say ‘undo’ for my last reversible change." else "You're welcome. I'm here when you need a hand.")
        if (lower in setOf("bye", "goodbye", "see you", "goodnight", "good night")) return reply("Take care. Your saved tasks and habits will be here when you return.")
        if (lower in setOf("help", "what can you do", "what can you help with", "commands", "show examples")) return reply(
            AssistantReplyStyle.help(context), listOf("/tasks" to "Tasks", "my habits" to "Habits").map { (query, label) ->
                TalkAction.AskQuery(query, label, if (AssistantReplyStyle.emoji(context)) "💬" else "")
            })
        if (lower in setOf("what were we talking about", "what did i ask", "what did i just say", "summarize our chat", "summarise our chat")) {
            return reply(if (state.recentInputs.isEmpty()) "I don't have a recent conversation in this session. Tell me what you'd like to work on." else "Your recent requests were:\n" + state.recentInputs.takeLast(4).joinToString("\n") { "• $it" } + "\nMy conversational context expires after 15 minutes of inactivity.")
        }
        if (lower in setOf("what is my name", "what's my name", "do you know my name")) {
            val name = context.getSharedPreferences("ayva_personalization", Context.MODE_PRIVATE).getString("name", null)
            return reply(name?.let { "You asked me to call you **$it**." } ?: "You haven't given me a name. Say ‘call me’ followed by your name.")
        }
        if (lower in setOf("what time is it", "what's the time", "what day is it", "what is today's date", "what's today's date")) { state.switchTopic("clock"); return reply(SimpleDateFormat("EEEE, d MMMM yyyy · h:mm a", Locale.getDefault()).format(Date())) }
        if (lower in setOf("no that's not what i meant", "that's wrong", "you misunderstood", "not what i meant", "that isn't what i meant")) { state.clearPending(); return reply("Let's correct it. Tell me what should change—its title, date, priority, or the action itself. Say ‘undo’ to reverse my last reversible change.") }

        AssistantCalculator.answer(input)?.let { return reply(it) }
        if (Regex("(?i)^(?:convert|how many)\\s+\\d").containsMatchIn(lower)) {
            Regex("(?i)^(?:convert|how many)\\s+(\\d+(?:\\.\\d+)?)\\s+(seconds?|minutes?|hours?|days?)\\s+(?:to|in|into)\\s+(seconds?|minutes?|hours?|days?)$").matchEntire(lower)?.let {
                fun factor(unit: String): Double = when { unit.startsWith("second") -> 1.0; unit.startsWith("minute") -> 60.0; unit.startsWith("hour") -> 3600.0; else -> 86400.0 }
                val value = it.groupValues[1].toDouble() * factor(it.groupValues[2]) / factor(it.groupValues[3])
                require(value.isFinite() && value < 1e15)
                return reply("${it.groupValues[1]} ${it.groupValues[2]} = **${java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()} ${it.groupValues[3]}**")
            }
        }
        if (lower in setOf("what did you add", "what did i add", "show that task", "show its details", "what about that task", "what is it", "what's its due date", "when is it due")) {
            val task = state.taskId?.let { app.database.taskDao().getTaskById(it) }
            return reply(task?.let { "**${it.title}**\nDue: ${it.dueDate?.let(SmartDateParser::formatDueDate) ?: "no due date"}\n${if (it.isCompleted) "Completed" else if (it.isTrashed) "In Trash" else "Pending"} · ${if (it.isPriority) "priority" else "normal priority"} · ${it.recurrence.name.lowercase()}${it.details.takeIf(String::isNotBlank)?.let { details -> "\n${details.take(500)}" }.orEmpty()}" } ?: "Which task do you mean? Ask ‘show my tasks’ first.")
        }
        val listQuery = Regex("(?i)^(?:(?:show|list|view|what are|what's on|what is on)\\s+)?(?:me\\s+)?(?:my\\s+)?(?:all |pending |overdue |priority |completed |today's |tomorrow's )?(?:tasks|todos|to-do list|task list)(?:\\s+(?:for )?(today|tomorrow|all))?$").matches(input.trimEnd('?')) ||
            lower in setOf("my agenda", "what do i have today", "what do i have tomorrow", "show overdue tasks", "overdue tasks", "priority tasks", "completed tasks")
        if (listQuery) {
            val completed = lower.contains("completed")
            var selected = app.database.taskDao().getAllActiveTasksList().filter { it.isCompleted == completed }
            val now = System.currentTimeMillis()
            when {
                lower.contains("overdue") -> selected = selected.filter { it.dueDate?.let { date -> date < now } == true }
                lower.contains("priority") -> selected = selected.filter { it.isPriority }
                lower.contains("today") || lower.contains("tomorrow") -> {
                    val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); if (lower.contains("tomorrow")) add(Calendar.DAY_OF_YEAR, 1) }
                    val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                    selected = selected.filter { it.dueDate?.let { date -> date in start.timeInMillis until end } == true }
                }
            }
            selected = selected.sortedWith(compareByDescending<Task> { it.isPriority }.thenBy { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.id })
            state.clearPending(); state.switchTopic("tasks"); state.rememberTaskList(selected.take(20).map { it.id })
            val text = if (selected.isEmpty()) "No tasks match that view." else selected.take(20).mapIndexed { i, t -> "${i + 1}. **${t.title}**${if (t.isPriority) " · priority" else ""}${t.dueDate?.let { " — ${SmartDateParser.formatDueDate(it)}" }.orEmpty()}" }.joinToString("\n") + if (selected.size > 20) "\nShowing 20 of ${selected.size}." else ""
            return AssistantResponseContent.taskList(reply(text + if (selected.isNotEmpty()) "\nSelect a task to act, or tell me what to change." else "", topic = "tasks"), selected, state.taskListAt)
        }
        AssistantPlanning.respond(context, state, input, tasks)?.let { return it }
        if (Regex("(?i)\\b(?:weather|breaking news|stock price|browse the web|search the web|capital of)\\b").containsMatchIn(input)) return reply("I work offline with app data, built-in help, dates and basic arithmetic. I can't look up live or general web information. I can help plan your next task instead.")
        return null
    }

    fun knowledgeFollowUp(context: Context, state: AssistantSession, input: String): AyvaTalkEngine.TalkResponse? {
        if (input !in setOf("why", "where", "how", "more", "tell me more", "explain that", "how to change it", "how do i change it", "where is it", "how does it work")) return null
        val id = state.topic?.takeIf { it.startsWith("faq:") }?.removePrefix("faq:") ?: return null
        val topics = JSONArray(context.assets.open("ayva_knowledge_base.json").bufferedReader().use { it.readText() })
        val topic = (0 until topics.length()).map { topics.getJSONObject(it) }.find { it.optString("id") == id } ?: return null
        val title = topic.getString("title")
        val body = when {
            input.startsWith("where") -> topic.optString("whereToGo")
            input.contains("change") || input == "how" -> topic.optJSONArray("whatToClick")?.let { a -> (0 until a.length()).map { "${it + 1}. ${a.getString(it)}" }.joinToString("\n") }
            input == "why" -> topic.optString("whyOrTip").ifBlank { topic.optString("directExplanation") }
            else -> topic.optString("directExplanation")
        }.orEmpty()
        val actions = topic.optString("route").takeIf(String::isNotBlank)?.let { listOf(TalkAction.NavigateAppScreen(it, "Open $title")) }.orEmpty()
        return AyvaTalkEngine.TalkResponse("**$title**\n$body", actions, id, AyvaTalkEngine.serializeActionsJson(id, actions))
    }
}
