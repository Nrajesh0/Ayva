package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

/** Deterministic, offline conversation coordinator. Only typed actions can change app state. */
object OfflineAssistant {
    private val mutex = Mutex()
    private fun token() = UUID.randomUUID().toString()

    suspend fun respond(input: String, context: Context, priority: Boolean = false, persistent: Boolean = false, requestId: String? = null): AyvaTalkEngine.TalkResponse? = mutex.withLock {
        val app = context.applicationContext as? FocusApplication ?: return@withLock null
        val store = AssistantSessionStore(context)
        val state = store.load()
        if (requestId != null && requestId in state.acknowledgedActionIds) return@withLock reply("That request has already been processed.")
        if (input.length > 4000) return@withLock reply("Please keep each request under 4,000 characters, or send it in smaller parts.")
        try {
            val response = turn(input, context, app, state, priority, persistent)
            if (response != null) {
                state.lastReply = response.formattedText.take(4000)
                state.recentInputs = (state.recentInputs + input.take(500)).takeLast(6)
                requestId?.let { state.acknowledgedActionIds = (state.acknowledgedActionIds + it).takeLast(30) }
            }
            store.save(state)
            response
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // An operation may already have committed: never turn a failure into a generic success reply.
            reply("I couldn't finish that request: ${e.message ?: "an app operation failed"}. Check the affected item before retrying.")
        }
    }

    suspend fun execute(context: Context, action: AssistantAction): AyvaTalkEngine.TalkResponse = mutex.withLock {
        val state = AssistantSessionStore(context).load()
        val response = apply(context, state, listOf(action))
        state.lastReply = response.formattedText
        AssistantSessionStore(context).save(state)
        response
    }

    suspend fun reset(context: Context) = mutex.withLock { AssistantSessionStore(context).clear() }

    suspend fun captureChoices(context: Context, prompt: String, choices: List<ConflictOption>, priority: Boolean, persistent: Boolean): AyvaTalkEngine.TalkResponse = mutex.withLock {
        val state = AssistantSessionStore(context).load()
        state.clearPending()
        state.expected = "legacy_choice"; state.confirmationId = token()
        state.pendingPriority = priority; state.pendingPersistent = persistent
        state.choices = choices.take(10).map { AssistantChoice(it.label, it.command) }
        AssistantSessionStore(context).save(state)
        choiceReply(prompt, state)
    }

    suspend fun rememberTopic(context: Context, response: AyvaTalkEngine.TalkResponse, query: String) = mutex.withLock {
        if (response.topicId in setOf("assistant", "tasks", "habits", "conversation", "confirmation", "choice")) return@withLock
        val state = AssistantSessionStore(context).load()
        state.topic = response.topicId?.let { "faq:$it" }
        state.taskId = null; state.habitId = null
        state.lastReply = response.formattedText.take(4000)
        state.recentInputs = (state.recentInputs + query.take(500)).takeLast(6)
        AssistantSessionStore(context).save(state)
    }

    private suspend fun turn(raw: String, context: Context, app: FocusApplication, state: AssistantSession, priority: Boolean, persistent: Boolean): AyvaTalkEngine.TalkResponse? {
        val command = raw.trim().substringBefore(' ').lowercase()
        if (command in setOf("/clear", "/clean", "/wipe")) { state.clearPending(); state.taskId = null; state.habitId = null; state.topic = null; state.undo = emptyList(); state.undoId = null; state.candidates = emptyList(); state.recentInputs = emptyList(); state.lastReply = null; return null }
        var text = raw.trim()
        if (command in setOf("/habit", "/habits") && raw.trim().indexOf(' ') < 0) { state.clearPending(); state.topic = "habits"; state.taskId = null; return null }
        if (text.startsWith("/ayva ")) return internalCommand(text.removePrefix("/ayva "), context, app, state)
        val taskAlias = command in setOf("/create", "/add", "/todo") || (command == "/task" && raw.substringAfter(' ', "").lowercase() !in setOf("", "all", "today", "list", "pending", "everything"))
        if (command in setOf("/talk", "/ask")) text = raw.substringAfter(' ', "")
        else if (taskAlias) text = raw.substringAfter(' ', "")
        else if (command in setOf("/reschedule", "/complete", "/delete", "/reopen", "/priority", "/focus", "/pomodoro", "/undo", "/routine", "/habit", "/habits")) text = com.focusbyrj.app.util.command.AyvaChatCommands.talkQuery(raw)
        else if (raw.startsWith("/")) {
            state.clearPending()
            if (command == "/tasks" || command == "/task") rememberList(raw, app, state)
            return null
        }
        text = AssistantTaskLanguage.normalize(text)
        text = text.replace(Regex("(?i)^confirm[_ ]delete[_ ](all|overdue)$")) { "delete ${it.groupValues[1]} tasks" }
        if (taskAlias) return create(context, app, state, text, priority, persistent, true)
        val lower = text.lowercase().trimEnd('.', '!', '?')
        if (lower in setOf("cancel", "never mind", "nevermind", "forget it", "stop that")) { state.clearPending(); return reply("Cancelled the pending request. Your saved items are unchanged.") }
        if (lower in setOf("undo", "undo that", "undo last", "undo last action", "take that back")) return undo(context, state, state.undoId)
        if (lower in setOf("yes", "yep", "yeah", "confirm", "do it", "go ahead", "sure", "okay", "ok") && !(state.expected == "setting_value" && state.pending.filterIsInstance<AssistantAction.SetPreference>().singleOrNull()?.type == "boolean")) {
            if (state.expected == "confirm" && state.confirmationId != null) return confirm(context, app, state, state.confirmationId!!)
            if (state.choices.size == 1) return choose(context, app, state, 0)
            return reply(if (state.expected != null) "I still need a specific answer to the last question. ${expectedHint(state)}" else "I'm ready. Tell me what you'd like to do.")
        }
        if (lower in setOf("no", "nope", "not now") && !(state.expected == "setting_value" && state.pending.filterIsInstance<AssistantAction.SetPreference>().singleOrNull()?.type == "boolean")) { state.clearPending(); return reply("Okay. I haven't applied the pending action.") }
        if (state.choices.isNotEmpty()) {
            choiceIndex(lower, state.choices.size)?.let { return choose(context, app, state, it) }
        }
        if (Regex("(?i)^remind me(?:\\s+.*)?$").matches(text) && !text.lowercase().startsWith("remind me to ") && !Regex("(?i)\\b(?:about|of)\\b").containsMatchIn(text)) {
            val date = SmartDateParser.parse(text.removePrefix("remind me").trim())
            if (text.equals("remind me", true) || (date.timestamp != null && !date.hasTaskText)) {
                state.clearPending(); state.expected = "task_title"; state.pendingPriority = priority; state.pendingPersistent = persistent
                state.pending = listOf(AssistantAction.CreateTask(Task(title = "", dueDate = date.timestamp)))
                return reply("What should I remind you about${date.timestamp?.let { " on ${SmartDateParser.formatDueDate(it)}" }.orEmpty()}?")
            }
        }
        val tasks = app.database.taskDao().getAllActiveTasksList()
        val pending = tasks.filter { !it.isCompleted }
        if (state.taskId != null && tasks.none { it.id == state.taskId }) state.taskId = null

        // Slot replies are constrained to the type requested, and a new explicit intent interrupts them.
        if (state.expected == "task_title" && (!isNewIntent(lower) || OfflineNluEngine.isExplicitCreation(text))) {
            return create(context, app, state, text, state.pendingPriority, state.pendingPersistent, false)
        }
        if (state.expected == "habit_title" && !isNewIntent(lower)) return propose(context, app, state, listOf(AssistantAction.CreateHabit(text)), "Create habit **$text** with a target of once a day?")
        if (state.expected == "setting_value" && state.pending.singleOrNull() is AssistantAction.SetPreference && !isNewIntent(lower)) {
            val old = state.pending.single() as AssistantAction.SetPreference
            val spec = AssistantPreferences.specs.first { it.key == old.key }
            val value = AssistantPreferences.extractValue(spec, text)
                ?: return reply("${spec.title}: ${settingHint(spec)}")
            return propose(context, app, state, listOf(old.copy(value = value)), "Set ${spec.title} to **$value ${spec.unit.takeUnless { it == "clock" }.orEmpty()}**?")
        }
        if (state.expected == "task_date" && !isNewIntent(lower)) {
            val date = SmartDateParser.parse(text)
            if (date.timestamp != null && !date.hasTaskText) {
                val ids = state.pending.filterIsInstance<AssistantAction.EditTasks>().firstOrNull()?.ids ?: listOfNotNull(state.taskId)
                val edits = ids.map { id ->
                    val previous = app.database.taskDao().getTaskById(id)?.dueDate
                    AssistantAction.EditTasks(listOf(id), TaskField.DUE,
                        (if (!date.hasTime && previous != null) preserveTime(date.timestamp, previous) else date.timestamp).toString())
                }
                if (ids.size > 1) return propose(context, app, state, edits, "Reschedule these ${ids.size} tasks to ${SmartDateParser.formatDueDate(date.timestamp)}?")
                return apply(context, state, edits)
            }
        }

        // A new request replaces an old preview. A later bare “yes” must not confirm
        // an unrelated action that is no longer the topic of the conversation.
        val pendingSetting = state.pending.singleOrNull() as? AssistantAction.SetPreference
        if (state.expected == "confirm" && pendingSetting != null && Regex("(?i)^(?:actually|instead|make it|change it to)\\s+").containsMatchIn(text)) {
            val spec = AssistantPreferences.specs.first { it.key == pendingSetting.key }
            val value = AssistantPreferences.extractValue(spec, text)
            if (value == null) {
                state.clearPending(); state.expected = "setting_value"; state.pending = listOf(pendingSetting.copy(value = ""))
                return reply("${spec.title}: ${settingHint(spec)}")
            }
            return propose(context, app, state, listOf(pendingSetting.copy(value = value)), "Set ${spec.title} to $value ${spec.unit}?")
        }
        state.clearPending()
        val parts = AssistantCompoundParser.split(text)
        if (parts.size > 1) return compound(context, app, state, parts, priority, persistent)

        // Privacy boundary precedes creation heuristics; an explicit reminder about a note is still a task.
        if (!taskAlias && Regex("(?i)^(?:read|show|search|find|edit|delete|create|add|new|open|list)\\s+(?:my\\s+|a\\s+)?notes?\\b|^(?:my )?notes$").containsMatchIn(text)) {
            state.clearPending(); state.topic = "notes"; state.taskId = null; state.habitId = null
            return reply("For privacy, notes cannot be created, searched, or viewed through the chat window. Open Keep Notes to view or change them.", listOf(TalkAction.NavigateAppScreen("notes", "Open Keep Notes", "📝")))
        }
        if (lower in setOf("forget me", "forget my preferences", "forget what i told you")) {
            context.getSharedPreferences("ayva_personalization", Context.MODE_PRIVATE).edit().clear().commit()
            state.clearPending(); state.taskId = null; state.habitId = null; state.topic = null; state.undo = emptyList(); state.undoId = null; state.recentInputs = emptyList()
            return reply("I've cleared my conversational context and your chat preferences. Saved tasks, habits and notes are unchanged.")
        }
        Regex("(?i)^(?:call me|my name is)\\s+(.+)$").matchEntire(text)?.let {
            val name = it.groupValues[1].trim().take(40)
            require(name.isNotBlank() && name.none(Char::isISOControl))
            check(context.getSharedPreferences("ayva_personalization", Context.MODE_PRIVATE).edit().putString("name", name).commit())
            return reply("I'll call you $name. Say ‘forget my preferences’ to clear that.")
        }
        if (lower in setOf("be concise", "short answers", "less detail", "more detail", "detailed answers", "less emoji", "no emoji")) {
            val concise = lower !in setOf("more detail", "detailed answers")
            context.getSharedPreferences("ayva_personalization", Context.MODE_PRIVATE).edit().putBoolean("concise", concise).commit()
            return reply(if (concise) "I'll keep my own replies brief." else "I'll include more context when it's useful.")
        }

        val question = OfflineNluEngine.isQuestion(text) && !OfflineNluEngine.isExplicitCreation(text)
        if (!question) {
            if (state.topic == "habits" && lower in setOf("done", "complete it", "log it", "log that", "finished it", "mark it done")) {
                return featureRequest(context, app, state, "log habit it")
            }
            if (lower == "done" && state.taskId != null) return handleTask(context, app, state, AssistantTaskRequest("complete", "it"), tasks)
            val timeReply = text.replace(Regex("(?i)^(?:actually|instead|make (?:it|that)|change (?:it|that) to)\\s+"), "")
            val parsedReply = SmartDateParser.parse(timeReply)
            if (state.taskId != null && parsedReply.timestamp != null && !parsedReply.hasTaskText) {
                return handleTask(context, app, state, AssistantTaskRequest("due", "it", parsedReply.timestamp.toString(), !parsedReply.hasTime), tasks)
            }
            if (!Regex("(?i)^(?:complete|check|log|done|finished)\\s+habit\\b").containsMatchIn(text)) {
                AssistantTaskLanguage.request(text)?.let { return handleTask(context, app, state, it, tasks) }
            }
        }
        val explicit = OfflineNluEngine.isExplicitCreation(text) || Regex("(?i)^(?:urgent|important|priority)[: ,]").containsMatchIn(text)
        if (explicit) return create(context, app, state, text, priority, persistent, false)
        if (state.topic?.startsWith("setting:") == true) {
            val spec = AssistantPreferences.specs.find { it.key == state.topic?.removePrefix("setting:") }
            val followUp = Regex("(?i)^(?:(?:change|set|make|turn) (?:it|that)(?: to)?|actually)(?:\\s+(.*))?$").matchEntire(text)
            if (spec != null && followUp != null) {
                val value = AssistantPreferences.extractValue(spec, followUp.groupValues[1])
                if (value == null) { state.clearPending(); state.expected = "setting_value"; state.pending = listOf(AssistantAction.SetPreference(spec.key, spec.type, "")); return reply("${spec.title}: ${settingHint(spec)}") }
                return propose(context, app, state, listOf(AssistantAction.SetPreference(spec.key, spec.type, value)), "Set ${spec.title} to $value ${spec.unit}?")
            }
        }
        dialogue(context, app, state, text, pending)?.let { return it }
        knowledgeFollowUp(context, state, lower)?.let { return it }
        featureRequest(context, app, state, text)?.let { return it }
        if (question || OfflineNluEngine.isConversationalStatement(text)) { state.clearPending(); return null }
        val fallback = OfflineNluEngine.parse(text, pending)
        if (fallback.intent in setOf(NluIntent.COMPLETE, NluIntent.DELETE, NluIntent.RESCHEDULE)) {
            val operation = when (fallback.intent) { NluIntent.COMPLETE -> "complete"; NluIntent.DELETE -> "trash"; else -> "due" }
            val subject = if (fallback.isAllTasks) if (lower.contains("overdue")) "overdue tasks" else "all tasks"
                else fallback.targetTask?.id?.let { "id:$it" } ?: fallback.filterQuery.orEmpty()
            return handleTask(context, app, state, AssistantTaskRequest(operation, subject, fallback.targetDateMs?.toString(), fallback.targetDateMs != null && !fallback.hasExplicitTimeSpecified), tasks)
        }
        if (fallback.intent == NluIntent.CREATE_TASK) return create(context, app, state, text, priority, persistent, false)
        if (fallback.intent == NluIntent.CONFLICT && fallback.conflictOptions.isNotEmpty()) {
            val result = setChoices(state, fallback.conflictPrompt ?: "Which action did you mean?", fallback.conflictOptions.take(10).map { AssistantChoice(it.label, it.command) })
            state.pendingPriority = priority; state.pendingPersistent = persistent
            return result
        }
        if (fallback.intent in setOf(NluIntent.START_ROUTINE, NluIntent.STOP_ROUTINE, NluIntent.LIST_ROUTINES)) {
            val query = if (fallback.intent == NluIntent.LIST_ROUTINES) "routines"
                else "${if (fallback.intent == NluIntent.START_ROUTINE) "enable" else "disable"} routine ${if (fallback.isAllTasks) "all" else fallback.targetRoutineName.orEmpty()}".trim()
            return featureRequest(context, app, state, query)
        }
        // Keep existing app-specific FAQ and feature dispatch; remember its choices at the UI boundary.
        return null
    }

    private suspend fun create(context: Context, app: FocusApplication, state: AssistantSession, text: String, priority: Boolean, persistent: Boolean, allowDuplicate: Boolean): AyvaTalkEngine.TalkResponse {
        var draft = AssistantTaskLanguage.draft(text, priority, persistent)
        if (draft != null && draft.dueDate == null && state.expected == "task_title") {
            val due = state.pending.filterIsInstance<AssistantAction.CreateTask>().singleOrNull()?.draft?.dueDate
            if (due != null) draft = draft.copy(dueDate = due)
        }
        if (draft == null) {
            state.clearPending(); state.expected = "task_title"; state.pendingPriority = priority; state.pendingPersistent = persistent
            return reply("What should the task be? You can include a date, such as ‘call the dentist tomorrow at 9am’.")
        }
        val duplicates = app.database.taskDao().getAllActiveTasksList().filter { !it.isCompleted && it.title.equals(draft.title, true) }
        if (!allowDuplicate && duplicates.isNotEmpty()) {
            val existing = duplicates.first()
            state.taskId = existing.id
            val choices = mutableListOf(AssistantChoice("Add a separate task", actionCommand(AssistantAction.CreateTask(draft))))
            if (draft.dueDate != null) choices.add(AssistantChoice("Update the existing time", actionCommand(AssistantAction.EditTasks(listOf(existing.id), TaskField.DUE, draft.dueDate.toString()))))
            choices.add(AssistantChoice("Complete the existing task", actionCommand(AssistantAction.CompleteTasks(listOf(existing.id)))))
            return setChoices(state, "You already have **${existing.title}**. What would you like to do?", choices)
        }
        return apply(context, state, listOf(AssistantAction.CreateTask(draft)))
    }

    private suspend fun handleTask(context: Context, app: FocusApplication, state: AssistantSession, request: AssistantTaskRequest, tasks: List<Task>): AyvaTalkEngine.TalkResponse {
        val available = if (request.operation == "reopen") tasks.filter { it.isCompleted } else tasks.filter { !it.isCompleted }
        val selected = AssistantTaskLanguage.select(request.subject, available, state)
        if (selected.tasks.isEmpty()) {
            if (available.isEmpty()) return reply("There are no ${if (request.operation == "reopen") "completed" else "pending"} tasks to ${request.operation}. Add a task directly, for example ‘buy milk tomorrow’.")
            val candidates = available.sortedWith(compareByDescending<Task> { it.isPriority }.thenBy { it.dueDate ?: Long.MAX_VALUE }).take(8)
            val choices = candidates.map { task -> AssistantChoice(task.title, requestCommand(request.copy(subject = "id:${task.id}"))) }
            return setChoices(state, if (request.subject.isBlank() || request.subject in setOf("it", "that", "this")) "Which task would you like to ${request.operation}?" else "I couldn't find an exact task for **${request.subject}**. Choose a task:", choices)
        }
        if (selected.tasks.size > 1 && !selected.group) {
            return setChoices(state, "Several tasks match **${request.subject}**. Which one?", selected.tasks.take(10).map { AssistantChoice(it.title, requestCommand(request.copy(subject = "id:${it.id}"))) })
        }
        val ids = selected.tasks.map { it.id }
        state.taskId = ids.singleOrNull(); state.topic = "tasks"
        if (request.operation == "due" && request.value == null) {
            state.clearPending(); state.expected = "task_date"
            state.pending = listOf(AssistantAction.EditTasks(ids, TaskField.DUE, ""))
            return reply("When should ${if (ids.size == 1) "**${selected.tasks.single().title}**" else "these ${ids.size} tasks"} be due? Try ‘tomorrow at 6pm’ or ‘in 30 minutes’.")
        }
        val actions = selected.tasks.map { task ->
            val adjusted = if (request.operation == "due" && request.value != "none" && request.preserveClock && task.dueDate != null) {
                request.copy(value = preserveTime(request.value!!.toLong(), task.dueDate).toString())
            } else request
            AssistantTaskLanguage.action(adjusted, listOf(task.id)) ?: error("I need a value for that change")
        }
        if (selected.group || ids.size > 1) return propose(context, app, state, actions, "Confirm: ${request.operation} these **${ids.size} tasks**?\n" + selected.tasks.take(10).joinToString("\n") { "• ${it.title}" })
        return apply(context, state, actions)
    }

    private suspend fun featureRequest(context: Context, app: FocusApplication, state: AssistantSession, text: String): AyvaTalkEngine.TalkResponse? {
        val lower = text.lowercase().trimEnd('.', '!', '?')
        if (Regex("^(?:(?:start|begin|run|do|give me) )?(?:a )?(?:math |arithmetic )?(?:drill|quiz)(?: (?:easy|medium|hard|10|20|questions|unlimited))*$").matches(lower) || lower in setOf("practice math", "math practice", "do math", "arithmetic practice")) {
            val difficulty = listOf("hard", "medium", "easy").find { Regex("\\b$it\\b").containsMatchIn(lower) } ?: "easy"
            val count = if (lower.contains("unlimited")) "unlimited" else if (Regex("\\b20\\b").containsMatchIn(lower)) "20" else "10"
            return AyvaTalkEngine.TalkResponse("Opening the $difficulty arithmetic drill.", cliCommandRedirect = "/drill $difficulty $count")
        }
        if (lower in setOf("blitz", "start blitz", "speed drill", "start speed drill")) return AyvaTalkEngine.TalkResponse("Opening the speed blitz.", cliCommandRedirect = "/blitz")
        if (lower in setOf("vocab", "vocabulary", "teach me a word", "learn a word", "vocabulary practice")) return AyvaTalkEngine.TalkResponse("Opening vocabulary practice.", cliCommandRedirect = "/vocab")
        if (lower in setOf("vocabulary quiz", "start vocabulary quiz", "vocab quiz")) return AyvaTalkEngine.TalkResponse("Opening the vocabulary quiz.", cliCommandRedirect = "/vocab_quiz")
        if (lower in setOf("routine", "routines", "my routines", "list routines", "show routines", "active routines", "which routine is active")) {
            state.clearPending(); state.topic = "routines"; state.taskId = null; state.habitId = null
            val schedules = app.database.scheduleDao().getAllSchedulesSync()
            return reply(if (schedules.isEmpty()) "You haven't created any routines. Open schedules to configure their time windows and app restrictions." else schedules.joinToString("\n") {
                "• **${it.name}** — ${if (it.isActiveAt()) "active now" else if (it.isEnabled) "enabled for its scheduled window" else "disabled"} (${String.format(java.util.Locale.US, "%02d:%02d–%02d:%02d", it.startHour, it.startMinute, it.endHour, it.endMinute)})"
            }, schedules.take(3).map { TalkAction.AskQuery("${if (it.isEnabled) "disable" else "enable"} routine id:${it.id}", "${if (it.isEnabled) "Disable" else "Enable"} ${it.name}") })
        }
        if (Regex("^(?:start |begin |set )?(?:a )?(?:focus(?: session| sprint)?|pomodoro|timer)(?: for| )?\\s*(?:\\d.*)?$").matches(lower) || lower in setOf("start focus", "start pomodoro", "start timer")) {
            val minutes = durationMinutes(lower) ?: if (!Regex("\\d").containsMatchIn(lower)) 25 else return reply("Choose a focus duration from 1–240 minutes, such as ‘start focus for 25 minutes’.")
            return apply(context, state, listOf(AssistantAction.StartFocus(minutes)))
        }
        if (lower in setOf("stop focus", "stop focus session", "end focus", "stop timer", "cancel timer", "end session")) {
            FocusSessionManager.refresh(context)
            if (!FocusSessionManager.active.value) return reply("No focus session is running.")
            return propose(context, app, state, listOf(AssistantAction.StopFocus), "Stop the current focus countdown?")
        }
        if (lower in setOf("timer status", "focus timer", "how much time is left", "how long is left", "time remaining", "is my timer running")) {
            FocusSessionManager.refresh(context)
            val remaining = FocusSessionManager.remainingSeconds.value
            return reply(if (FocusSessionManager.active.value) "Your focus session has **${remaining / 60}m ${remaining % 60}s** remaining." else "No focus session is running.")
        }
        Regex("(?i)^(?:add|create|new|track)\\s+(?:a\\s+)?habit(?:\\s+(.+))?$").matchEntire(text)?.let {
            val content = it.groupValues[1].trim()
            if (content.isBlank()) { state.clearPending(); state.expected = "habit_title"; return reply("What habit would you like to track?") }
            val target = Regex("(?i)\\s+(\\d+)\\s+times?(?:\\s+(?:a|per|every)\\s+day|\\s+daily)?$").find(content)
            val title = target?.let { content.substring(0, it.range.first).trim() } ?: content
            return propose(context, app, state, listOf(AssistantAction.CreateHabit(title, target?.groupValues?.get(1)?.toIntOrNull() ?: 1)), "Create habit **$title**${target?.let { " with target ${it.groupValues[1]} per day" }.orEmpty()}?")
        }
        Regex("(?i)^(?:(?:log|complete|done|check|finished)\\s+habit|habit\\s+(?:done|complete|log)|i did my habit)(?:\\s+(.+))?$").matchEntire(text)?.let {
            val habits = app.habitRepository.getAllActiveHabits().first()
            val rawTarget = it.groupValues[1].trim()
            val countMatch = Regex("(?i)\\s+(\\d+)\\s*(?:times?|x)$").find(rawTarget)
            val count = countMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val target = countMatch?.let { rawTarget.substring(0, it.range.first).trim() } ?: rawTarget
            val matched = if (target.startsWith("id:")) habits.filter { it.id == target.removePrefix("id:").toLongOrNull() }
                else if (target in setOf("it", "that", "")) habits.filter { it.id == state.habitId }
                else habits.filter { it.title.equals(target, true) }.ifEmpty { habits.filter { it.title.contains(target, true) } }
            if (matched.size != 1) return setChoices(state, "Which habit would you like to log?", (matched.ifEmpty { habits }).take(8).map { AssistantChoice(it.title, actionCommand(AssistantAction.LogHabit(it.id, count))) })
            return apply(context, state, listOf(AssistantAction.LogHabit(matched.single().id, count)))
        }
        if (lower in setOf("habit", "habits", "my habits", "show habits", "habit progress", "how are my habits")) {
            state.clearPending(); state.topic = "habits"; state.taskId = null
            val habits = app.habitRepository.activeHabitsWithProgress.first()
            return reply(if (habits.isEmpty()) "You have no active habits. Try ‘add habit reading’." else "Today's habits:\n" + habits.joinToString("\n") { "• **${it.habit.title}** — ${it.completedToday}/${it.targetToday}${if (it.isCompletedToday) " ✓" else ""}, streak ${it.currentStreak} day(s)" }, habits.filter { !it.isCompletedToday }.take(3).map { TalkAction.AskQuery("log habit id:${it.habit.id}", "Log ${it.habit.title}") })
        }
        val routineText = text.replace(Regex("(?i)^(start|enable|resume|stop|disable|pause)\\s+(?:the\\s+)?(.+?)\\s+routines?$")) {
            "${it.groupValues[1]} routine ${it.groupValues[2]}"
        }
        Regex("(?i)^(?:(start|enable|resume|stop|disable|pause)\\s+(?:the\\s+)?routine|routine\\s+(start|enable|resume|stop|disable|pause))(?:\\s+(.+))?$").matchEntire(routineText)?.let {
            val verb = it.groupValues[1].ifBlank { it.groupValues[2] }.lowercase()
            val enabled = verb in setOf("start", "enable", "resume")
            val target = it.groupValues[3]
            val schedules = app.database.scheduleDao().getAllSchedulesSync()
            if (target.lowercase() in setOf("all", "all routines", "all my routines", "all schedules")) {
                if (schedules.isEmpty()) return reply("You haven't created any routines yet.")
                return propose(context, app, state, schedules.map { AssistantAction.SetRoutine(it.id, enabled) }, "${if (enabled) "Enable" else "Disable"} these ${schedules.size} routines?\n" + schedules.joinToString("\n") { "• ${it.name}" })
            }
            val matches = if (target.startsWith("id:")) schedules.filter { it.id == target.removePrefix("id:").toIntOrNull() }
                else schedules.filter { it.name.equals(target, true) }.ifEmpty { if (target.isBlank()) schedules else schedules.filter { it.name.contains(target, true) } }
            if (matches.size != 1) return setChoices(state, "Which routine? Enabling uses its saved schedule.", matches.ifEmpty { schedules }.take(8).map { AssistantChoice(it.name, actionCommand(AssistantAction.SetRoutine(it.id, enabled))) })
            return propose(context, app, state, listOf(AssistantAction.SetRoutine(matches.single().id, enabled)), "${if (enabled) "Enable" else "Disable"} routine **${matches.single().name}**?")
        }
        val spec = AssistantPreferences.find(text)
        if (spec != null) {
            val mutation = Regex("(?i)^(?:set|change|switch|turn|enable|disable|activate|deactivate|freeze|unfreeze|make)\\b").containsMatchIn(text) ||
                (spec.type == "boolean" && Regex("(?i)\\b(?:on|off)$").containsMatchIn(text))
            if (mutation) {
                val parsedValue = runCatching { AssistantPreferences.extractValue(spec, text) }
                val value = parsedValue.getOrNull()
                if (value == null) {
                    state.clearPending(); state.expected = "setting_value"; state.pending = listOf(AssistantAction.SetPreference(spec.key, spec.type, ""))
                    return reply("${parsedValue.exceptionOrNull()?.message?.let { "$it. " }.orEmpty()}What should ${spec.title} be? ${settingHint(spec)}")
                }
                return propose(context, app, state, listOf(AssistantAction.SetPreference(spec.key, spec.type, value)), "Set ${spec.title} to **$value ${spec.unit.takeUnless { it == "clock" }.orEmpty()}**?")
            }
            if (Regex("(?i)^(?:what(?:'s| is) my|show|check|current|how long|how often)\\b").containsMatchIn(text)) return reply("${spec.title} is **${AssistantPreferences.read(context, spec)} ${spec.unit.takeUnless { it == "clock" }.orEmpty()}**. You can say ‘change ${spec.aliases.first()}’." )
        }
        return null
    }

    private suspend fun propose(context: Context, app: FocusApplication, state: AssistantSession, actions: List<AssistantAction>, text: String): AyvaTalkEngine.TalkResponse {
        for (action in actions.filterIsInstance<AssistantAction.SetRoutine>().filter { !it.enabled }) {
            val schedule = app.database.scheduleDao().getScheduleById(action.id) ?: return reply("That routine no longer exists.")
            if (!AssistantActionExecutor.canDisableRoutine(context, schedule)) return reply("An active strict routine or focus session cannot be stopped from chat.")
        }
        state.clearPending(); state.expected = "confirm"; state.confirmationId = token(); state.pending = actions
        val ids = actions.flatMap { when (it) { is AssistantAction.EditTasks -> it.ids; is AssistantAction.CompleteTasks -> it.ids; is AssistantAction.TrashTasks -> it.ids; else -> emptyList() } }.distinct()
        state.versions = ids.associateWith { app.database.taskDao().getTaskById(it)?.updatedAt ?: -1 }
        state.guards = actions.mapNotNull { action -> when (action) {
            is AssistantAction.SetPreference -> "pref:${action.key}" to AssistantPreferences.read(context, AssistantPreferences.specs.first { it.key == action.key })
            is AssistantAction.SetRoutine -> "routine:${action.id}" to app.database.scheduleDao().getScheduleById(action.id)?.isEnabled.toString()
            AssistantAction.StopFocus -> "focus" to focusReceipt(context)
            else -> null
        } }.toMap()
        return reply(text, listOf(TalkAction.AskQuery("/ayva confirm ${state.confirmationId}", "Confirm", "✅"), TalkAction.AskQuery("/ayva cancel ${state.confirmationId}", "Cancel", "✖")), "confirmation")
    }

    private suspend fun confirm(context: Context, app: FocusApplication, state: AssistantSession, id: String): AyvaTalkEngine.TalkResponse {
        if (state.expected != "confirm" || state.confirmationId != id) return reply("That confirmation has expired or was already handled. Please send the request again.")
        for ((taskId, version) in state.versions) require(app.database.taskDao().getTaskById(taskId)?.updatedAt == version) { "A selected task changed. Please review the request again" }
        for ((key, before) in state.guards) {
            val current = when {
                key.startsWith("pref:") -> AssistantPreferences.read(context, AssistantPreferences.specs.first { it.key == key.removePrefix("pref:") })
                key.startsWith("routine:") -> app.database.scheduleDao().getScheduleById(key.removePrefix("routine:").toInt())?.isEnabled.toString()
                key == "focus" -> focusReceipt(context)
                else -> error("Unknown action guard")
            }
            require(current == before) { "The selected setting or session changed. Please review the request again" }
        }
        return apply(context, state, state.pending.toList(), id)
    }

    private suspend fun apply(context: Context, state: AssistantSession, actions: List<AssistantAction>, receipt: String = token()): AyvaTalkEngine.TalkResponse {
        if (receipt in state.acknowledgedActionIds) return reply("That action was already applied.")
        state.clearPending()
        state.acknowledgedActionIds = (state.acknowledgedActionIds + receipt).takeLast(30)
        // Consume a confirmation before mutation so a stale/repeated button cannot replay it.
        AssistantSessionStore(context).save(state)
        val results = mutableListOf<String>()
        val taskBatch = actions.all { it is AssistantAction.CreateTask || it is AssistantAction.EditTasks || it is AssistantAction.CompleteTasks || it is AssistantAction.TrashTasks }
        state.undo = emptyList(); state.undoId = null
        for (action in actions) {
            try {
                val result = AssistantActionExecutor.execute(context, action)
                results.add(result.text)
                result.taskId?.let { state.taskId = it; state.habitId = null; state.topic = "tasks" }
                result.habitId?.let { state.habitId = it; state.taskId = null; state.topic = "habits" }
                if (result.changed && result.undo.isNotEmpty()) {
                    if (taskBatch) {
                        val merged = state.undo.associateBy { it.getJSONObject("after").getLong("id") }.toMutableMap()
                        for (record in result.undo) {
                            val taskId = record.getJSONObject("after").getLong("id")
                            val previous = merged[taskId]
                            if (previous != null) {
                                record.put("before", previous.opt("before"))
                                if (previous.has("recurring")) record.put("recurring", previous.getJSONObject("recurring"))
                            }
                            merged[taskId] = record
                        }
                        state.undo = merged.values.toList()
                    } else state.undo = result.undo
                    state.undoId = token()
                }
                when (action) {
                    is AssistantAction.SetPreference -> { state.topic = "setting:${action.key}"; state.taskId = null }
                    is AssistantAction.SetRoutine -> { state.topic = "routines"; state.taskId = null }
                    is AssistantAction.StartFocus, AssistantAction.StopFocus -> state.topic = "focus"
                    else -> Unit
                }
                AssistantSessionStore(context).save(state)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                results.add("Couldn't apply the next action: ${e.message}. Remaining actions were not run.")
                break
            }
        }
        val actionsOut = mutableListOf<TalkAction>()
        state.undoId?.let { actionsOut.add(TalkAction.AskQuery("/ayva undo $it", if (state.undo.size > 1) "Undo task changes" else "Undo last change", "↶")) }
        if (state.taskId != null) actionsOut.add(TalkAction.AskQuery("reschedule id:${state.taskId}", "Change time", "⏰"))
        return reply(results.joinToString("\n"), actionsOut)
    }

    private suspend fun undo(context: Context, state: AssistantSession, id: String?): AyvaTalkEngine.TalkResponse {
        if (id == null || id != state.undoId || state.undo.isEmpty()) return reply("There's no recent action available to undo.")
        val text = AssistantActionExecutor.undo(context, state.undo)
        state.undo = emptyList(); state.undoId = null; state.clearPending()
        return reply(text)
    }

    private suspend fun internalCommand(command: String, context: Context, app: FocusApplication, state: AssistantSession): AyvaTalkEngine.TalkResponse {
        val parts = command.split(Regex("\\s+"))
        return when (parts.firstOrNull()) {
            "confirm" -> confirm(context, app, state, parts.getOrNull(1).orEmpty())
            "undo" -> undo(context, state, parts.getOrNull(1))
            "cancel" -> if (parts.getOrNull(1) == state.confirmationId) { state.clearPending(); reply("Cancelled.") } else reply("That request is no longer pending.")
            "choose" -> if (parts.getOrNull(1) == state.confirmationId && state.choices.isNotEmpty()) {
                val index = parts.getOrNull(2)?.toIntOrNull() ?: -1
                if (index in state.choices.indices) choose(context, app, state, index) else reply("Choose one of the displayed options.")
            } else reply("Those choices have expired or were already handled. Please ask again.")
            else -> reply("That assistant action is not available.")
        }
    }

    private suspend fun choose(context: Context, app: FocusApplication, state: AssistantSession, index: Int): AyvaTalkEngine.TalkResponse {
        val choice = state.choices[index]
        val priority = state.pendingPriority; val persistent = state.pendingPersistent
        state.clearPending()
        AssistantSessionStore(context).save(state)
        if (choice.command.startsWith("request:")) {
            val json = JSONObject(choice.command.removePrefix("request:"))
            val request = AssistantTaskRequest(json.getString("operation"), json.getString("subject"), if (json.isNull("value")) null else json.getString("value"), json.optBoolean("preserveClock"))
            return handleTask(context, app, state, request, app.database.taskDao().getAllActiveTasksList())
        }
        if (choice.command.startsWith("action:")) return apply(context, state, listOf(AssistantAction.fromJson(JSONObject(choice.command.removePrefix("action:")))))
        return turn(choice.command, context, app, state, priority, persistent)
            ?: AyvaTalkEngine.TalkResponse("Opening ${choice.label}.", cliCommandRedirect = choice.command)
    }

    private fun setChoices(state: AssistantSession, prompt: String, choices: List<AssistantChoice>): AyvaTalkEngine.TalkResponse {
        if (choices.isEmpty()) return reply("No matching items are available. You can create a task or habit first.")
        state.clearPending(); state.expected = "choice"; state.confirmationId = token(); state.choices = choices
        val selectedIds = choices.mapNotNull { choice ->
            runCatching {
                when {
                    choice.command.startsWith("request:") -> JSONObject(choice.command.removePrefix("request:")).getString("subject").removePrefix("id:").toLongOrNull()
                    choice.command.startsWith("action:") -> when (val action = AssistantAction.fromJson(JSONObject(choice.command.removePrefix("action:")))) {
                        is AssistantAction.EditTasks -> action.ids.singleOrNull()
                        is AssistantAction.CompleteTasks -> action.ids.singleOrNull()
                        is AssistantAction.TrashTasks -> action.ids.singleOrNull()
                        else -> null
                    }
                    else -> null
                }
            }.getOrNull()
        }
        if (selectedIds.size == choices.size) state.candidates = selectedIds
        return choiceReply(prompt, state)
    }
    private fun choiceReply(prompt: String, state: AssistantSession) = reply(prompt + "\n" + state.choices.mapIndexed { i, c -> "${i + 1}. ${c.label}" }.joinToString("\n") + "\nYou can reply with a number or tap a choice.", state.choices.mapIndexed { i, c -> TalkAction.AskQuery("/ayva choose ${state.confirmationId} $i", c.label) }, "choice")
    private fun choiceIndex(input: String, size: Int): Int? {
        val cleaned = input.removePrefix("the ").removeSuffix(" one").removeSuffix(" option").removePrefix("option ").removePrefix("number ")
        val ordinal = when (cleaned) { "first" -> 1; "second" -> 2; "third" -> 3; "fourth" -> 4; "fifth" -> 5; "last" -> size; else -> cleaned.toIntOrNull() }
        return ordinal?.minus(1)?.takeIf { it in 0 until size }
    }
    private fun focusReceipt(context: Context): String = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE).let {
        "${it.getBoolean("isSessionActive", false)}:${it.getLong("focus_end_wall_ms", 0)}:${it.getLong("focus_end_elapsed_ms", 0)}"
    }
    private fun actionCommand(action: AssistantAction) = "action:${action.toJson()}"
    private fun requestCommand(request: AssistantTaskRequest): String = "request:" + JSONObject()
        .put("operation", request.operation).put("subject", request.subject)
        .put("value", request.value ?: JSONObject.NULL).put("preserveClock", request.preserveClock).toString()
    private fun expectedHint(state: AssistantSession) = when (state.expected) { "task_date" -> "Tell me a date or time."; "task_title", "habit_title" -> "Tell me its title."; "choice", "legacy_choice" -> "Choose a numbered option."; "setting_value" -> "Tell me the new setting value."; else -> "Please answer the pending question." }
    private fun isNewIntent(text: String) = OfflineNluEngine.isConversationalStatement(text) || OfflineNluEngine.isExplicitCreation(text) || OfflineNluEngine.isQuestion(text) || Regex("^(?:start|stop|delete|complete|reschedule|show|list|set|change|log|undo|help)\\b").containsMatchIn(text)
    private fun settingHint(spec: AssistantPreferenceSpec) = when (spec.type) { "boolean" -> "Use on or off."; "int" -> "Use ${spec.range?.first}–${spec.range?.last} ${spec.unit}."; else -> if (spec.unit == "clock") "Use a time such as 9:30am or 18:00." else "Choose ${spec.values.joinToString()}." }
    private fun reply(text: String, actions: List<TalkAction> = emptyList(), topic: String = "assistant") = AyvaTalkEngine.TalkResponse(text, actions, topic, AyvaTalkEngine.serializeActionsJson(topic, actions))
    private fun preserveTime(date: Long, previous: Long): Long {
        val old = Calendar.getInstance().apply { timeInMillis = previous }
        return Calendar.getInstance().apply { timeInMillis = date; set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, old.get(Calendar.MINUTE)); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    }
    private fun durationMinutes(text: String): Int? {
        val matches = Regex("(?<![\\p{L}\\p{N}.])-?\\d+(?:\\.\\d+)?\\s*(?:hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)?\\b").findAll(text).toList()
        if (matches.size != 1) return null
        val raw = matches.single().value
        val amount = Regex("-?\\d+(?:\\.\\d+)?").find(raw)?.value?.toDoubleOrNull() ?: return null
        val unit = raw.replace(Regex("^-?\\d+(?:\\.\\d+)?"), "").trim()
        val minutes = amount * when { unit.startsWith("h") -> 60.0; unit.startsWith("s") -> 1.0 / 60.0; else -> 1.0 }
        return minutes.takeIf { it.isFinite() && it % 1.0 == 0.0 && it in 1.0..240.0 }?.toInt()
    }

    private suspend fun rememberList(input: String, app: FocusApplication, state: AssistantSession) {
        val all = app.database.taskDao().getAllActiveTasksList().filter { !it.isCompleted }
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        val list = if (Regex("(?i)\\b(?:all|pending|everything)\\b").containsMatchIn(input)) all else all.filter { it.dueDate == null || it.dueDate < tomorrow }
        state.candidates = list.sortedWith(compareByDescending<Task> { it.isPriority }.thenBy { it.dueDate ?: Long.MAX_VALUE }).map { it.id }; state.topic = "tasks"
    }

    private suspend fun dialogue(context: Context, app: FocusApplication, state: AssistantSession, input: String, tasks: List<Task>): AyvaTalkEngine.TalkResponse? = AssistantDialogue.answer(context, app, state, input, tasks)
    private fun knowledgeFollowUp(context: Context, state: AssistantSession, input: String): AyvaTalkEngine.TalkResponse? = AssistantDialogue.knowledgeFollowUp(context, state, input)

    private suspend fun compound(context: Context, app: FocusApplication, state: AssistantSession, parts: List<String>, priority: Boolean, persistent: Boolean): AyvaTalkEngine.TalkResponse {
        if (parts.size > 5) return reply("Please send at most five actions at a time. Nothing has been changed.")
        val planned = mutableListOf<AssistantAction>()
        val tasks = app.database.taskDao().getAllActiveTasksList().filter { !it.isCompleted }
        for (part in parts) {
            val request = AssistantTaskLanguage.request(part)
            if (request != null && request.subject.lowercase() in setOf("it", "that", "this task") && planned.lastOrNull() is AssistantAction.CreateTask) {
                val previous = planned.removeAt(planned.lastIndex) as AssistantAction.CreateTask
                val draft = when (request.operation) {
                    "priority" -> previous.draft.copy(isPriority = request.value == "true")
                    "persistent" -> previous.draft.copy(isPersistent = request.value == "true")
                    "due" -> request.value?.toLongOrNull()?.let { previous.draft.copy(dueDate = it) }
                    "title" -> previous.draft.copy(title = request.value.orEmpty())
                    else -> null
                } ?: return reply("I need a separate, specific request for ‘$part’. Nothing has been changed.")
                planned.add(AssistantAction.CreateTask(draft)); continue
            }
            val action: AssistantAction? = when {
                request != null -> {
                    val selected = AssistantTaskLanguage.select(request.subject, tasks, state)
                    if (selected.tasks.isEmpty() || (selected.tasks.size > 1 && !selected.group)) null else AssistantTaskLanguage.action(request, selected.tasks.map { it.id })
                }
                OfflineNluEngine.isExplicitCreation(part) -> AssistantTaskLanguage.draft(part, priority, persistent)?.let { draft ->
                    if (tasks.any { it.title.equals(draft.title, true) } || planned.filterIsInstance<AssistantAction.CreateTask>().any { it.draft.title.equals(draft.title, true) }) null else AssistantAction.CreateTask(draft)
                }
                Regex("(?i)^(?:start|begin)\\s+(?:focus|pomodoro|timer)").containsMatchIn(part) -> durationMinutes(part)?.let { AssistantAction.StartFocus(it) }
                else -> null
            }
            if (action == null) return reply("I need a specific target or value for ‘$part’. Send that request separately first. Nothing in this batch has been changed.")
            planned.add(action)
        }
        val preview = planned.mapIndexed { index, action -> "${index + 1}. ${when (action) {
            is AssistantAction.CreateTask -> "Add **${action.draft.title}**${action.draft.dueDate?.let { " (${SmartDateParser.formatDueDate(it)})" }.orEmpty()}${if (action.draft.isPriority) " · priority" else ""}"
            is AssistantAction.EditTasks -> "Change ${action.field.name.lowercase()} on ${action.ids.size} selected task(s) to ${action.value}"
            is AssistantAction.CompleteTasks -> "Complete ${action.ids.size} selected task(s)"
            is AssistantAction.TrashTasks -> "Move ${action.ids.size} selected task(s) to Trash"
            is AssistantAction.StartFocus -> "Start ${action.minutes}-minute focus countdown"
            else -> "Apply ${action.javaClass.simpleName}"
        }}" }.joinToString("\n")
        return propose(context, app, state, planned, "Apply this batch?\n$preview\nI'll report each result and stop if an action fails. ${if (planned.all { it !is AssistantAction.StartFocus }) "Undo restores the task changes in this batch." else "Undo applies to the last reversible change."}")
    }
}
