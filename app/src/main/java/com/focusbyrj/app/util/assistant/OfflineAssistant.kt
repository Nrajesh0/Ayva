package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

/** Deterministic, offline conversation coordinator. Only typed actions can change app state. */
object OfflineAssistant {
    private val mutex = AssistantStateLock.mutex
    private fun token() = UUID.randomUUID().toString()

    suspend fun respond(input: String, context: Context, priority: Boolean = false, persistent: Boolean = false, requestId: String? = null, replyTarget: AssistantItemReference? = null, quotedReply: String? = null): AyvaTalkEngine.TalkResponse? = mutex.withLock {
        val app = context.applicationContext as? FocusApplication ?: return@withLock null
        val store = AssistantSessionStore(context)
        val state = store.load()
        AssistantContext.expire(state)
        if (requestId != null && requestId in state.acknowledgedActionIds) return@withLock reply("That request has already been processed.")
        try {
            if (input.length > 4000) return@withLock rejectInput(context, state, store,
                "Please keep each request under 4,000 characters, or send it in smaller parts. Previous confirmation controls are cancelled; saved items are unchanged.")
            if (quotedReply != null && !prepareReply(context, state, replyTarget, quotedReply)) {
                return@withLock rejectInput(context, state, store,
                    "That reply refers to an item that is no longer available. Please choose a task or habit before trying again. Previous confirmation controls are cancelled.")
            }
            var response = turn(input, context, app, state, priority, persistent)
            if (response != null) response = ConversationVoice.finish(context, state, response)
            if (response != null && state.expected != null && AssistantPresentation.data(response.jsonPayload)?.has("receipt") != true) {
                response = AssistantPresentation.attach(response, itemReference(context, state))
            }
            if (response != null) {
                response = AssistantPresentation.withReplyTarget(response, AssistantPresentation.reference(response.jsonPayload) ?: itemReference(context, state))
                val route = ConversationRouting.candidates(input, state).firstOrNull()
                // Diagnostics must not recreate context that a forget/clear operation removed.
                route?.let { state.featureState?.put("lastConversationRoute", "${it.owner.name.lowercase()}: ${it.evidence}") }
                state.lastReply = response.formattedText.take(4000)
                state.recentInputs = (state.recentInputs + input.take(500)).takeLast(6)
                requestId?.let { state.acknowledgedActionIds = (state.acknowledgedActionIds + it).takeLast(30) }
            }
            store.save(state)
            response
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // A failed interruption cannot leave an unrelated old confirmation live.
            state.clearPending(); state.switchTopic("conversation")
            state.featureState?.optJSONObject("guidedPlan")?.put("nonce", JSONObject.NULL)?.put("paused", true)
            invalidatePlanReview(context)
            runCatching { store.save(state) }
            // An operation may already have committed: never turn a failure into a generic success reply.
            reply("I couldn't finish that request: ${e.message ?: "an app operation failed"}. Check the affected item before retrying.")
        }
    }

    private fun invalidatePlanReview(context: Context) {
        runCatching {
            val workspaces = ConversationPlanStore(context)
            workspaces.active()?.takeIf { it.reviewedRevision != null }?.let {
                workspaces.put(it.copy(reviewedRevision = null, reviewedAt = 0, reviewProcess = null))
            }
        }
    }

    private fun rejectInput(context: Context, state: AssistantSession, store: AssistantSessionStore, message: String): AyvaTalkEngine.TalkResponse {
        AssistantConversation.suspendQuestion(state)
        state.clearPending(); state.switchTopic("conversation")
        state.featureState?.optJSONObject("guidedPlan")?.put("nonce", JSONObject.NULL)?.put("paused", true)
        invalidatePlanReview(context)
        state.lastReply = message
        store.save(state)
        return reply(message)
    }

    suspend fun execute(context: Context, action: AssistantAction): AyvaTalkEngine.TalkResponse = mutex.withLock {
        val state = AssistantSessionStore(context).load()
        val response = apply(context, state, listOf(action))
        state.lastReply = response.formattedText
        AssistantSessionStore(context).save(state)
        response
    }

    suspend fun reset(context: Context) = mutex.withLock {
        AssistantSessionStore(context).clear()
        ConversationPlanStore.clearAll(context)
        ChatModeStore.set(context, ChatMode.CHAT)
    }

    suspend fun clarifyUnknown(context: Context, input: String): AyvaTalkEngine.TalkResponse = mutex.withLock {
        AssistantReplyStyle.unknown(context, input.take(4000), AssistantSessionStore(context).load())
    }

    /** Read-only UI projection of the canonical session. Reading never refreshes its lifetime. */
    suspend fun chatState(context: Context): AssistantChatState = mutex.withLock {
        val state = AssistantSessionStore(context).load()
        AssistantChatState(itemReference(context, state), state.confirmationId, state.undoId, state.touchedAt + 900_001L)
    }

    suspend fun availableReferences(context: Context): List<AssistantItemReference> = mutex.withLock {
        val app = context.applicationContext as? FocusApplication ?: return@withLock emptyList()
        app.database.taskDao().getAllActiveTasksList().filter { !it.isCompleted && !it.isTrashed }
            .sortedWith(compareByDescending<Task> { it.isPriority }.thenBy { it.dueDate ?: Long.MAX_VALUE })
            .map { AssistantItemReference("task", it.id, it.title, AssistantPresentation.due(it.dueDate)) } +
            app.database.habitDao().getAllHabitsSync().filter { !it.isArchived }
                .map { AssistantItemReference("habit", it.id, it.title) }
    }

    private suspend fun referenceAvailable(context: Context, reference: AssistantItemReference): Boolean {
        val app = context.applicationContext as? FocusApplication ?: return false
        return when (reference.kind) {
            "task" -> app.database.taskDao().getTaskById(reference.id)?.let { !it.isTrashed } == true
            "habit" -> app.database.habitDao().getHabitById(reference.id)?.let { !it.isArchived } == true
            else -> false
        }
    }

    private fun focusReference(state: AssistantSession, reference: AssistantItemReference?) {
        state.clearPending(); state.clearSuspended()
        when (reference?.kind) {
            "task" -> state.focusTask(reference.id)
            "habit" -> { state.switchTopic("habits"); state.habitId = reference.id }
            else -> state.switchTopic("conversation")
        }
    }

    /** Selecting a reference is explicit and validated; a transcript label cannot execute an action. */
    suspend fun selectReference(context: Context, reference: AssistantItemReference): Boolean = mutex.withLock {
        if (!referenceAvailable(context, reference)) return@withLock false
        val store = AssistantSessionStore(context); val state = store.load()
        focusReference(state, reference)
        store.save(state); true
    }

    private suspend fun prepareReply(context: Context, state: AssistantSession, reference: AssistantItemReference?, quotedReply: String): Boolean {
        if (reference != null && !referenceAvailable(context, reference)) return false
        val same = reference == null || when (reference.kind) {
            "task" -> state.taskId == reference.id && state.habitId == null
            "habit" -> state.habitId == reference.id && state.taskId == null
            else -> false
        }
        // Answer the live question (including a new reminder draft); never restore historical pending state.
        val currentQuestion = same && state.expected != null && quotedReply.take(4000) == state.lastReply
        if (!currentQuestion) focusReference(state, reference)
        return true
    }

    suspend fun releaseReference(context: Context) = mutex.withLock {
        val store = AssistantSessionStore(context); val state = store.load()
        state.clearPending(); state.switchTopic("conversation"); state.clearSuspended()
        store.save(state)
    }

    private suspend fun itemReference(context: Context, state: AssistantSession): AssistantItemReference? {
        // A new-item/setting question or disambiguation is not a reference to the previous task.
        if (state.expected in setOf("task_title", "habit_title", "setting_value") ||
            (state.expected in setOf("choice", "legacy_choice") && state.choices.size > 1) ||
            state.pending.any { it !is AssistantAction.EditTasks && it !is AssistantAction.CompleteTasks && it !is AssistantAction.TrashTasks }) return null
        val app = context.applicationContext as? FocusApplication ?: return null
        state.taskId?.let { id -> app.database.taskDao().getTaskById(id)?.takeUnless { it.isTrashed }?.let {
            return AssistantItemReference("task", id, it.title, AssistantPresentation.due(it.dueDate))
        } }
        state.habitId?.let { id -> app.database.habitDao().getHabitById(id)?.takeUnless { it.isArchived }?.let {
            return AssistantItemReference("habit", id, it.title)
        } }
        return null
    }

    /** Called from the chat boundary with a real completed-test card, never with user-authored JSON. */
    suspend fun recordLearningResult(context: Context, summary: String, timestamp: Long) = mutex.withLock {
        if (summary.length > 500_000 || System.currentTimeMillis() - timestamp !in 0..900_000L) return@withLock
        val json = runCatching { JSONObject(summary) }.getOrNull() ?: return@withLock
        val id = json.optString("sessionId").takeIf { it.isNotBlank() && it.length <= 100 } ?: return@withLock
        val store = AssistantSessionStore(context); val state = store.load()
        if (state.learningResultId == id || timestamp < (state.learning?.startedAt ?: 0L)) return@withLock
        val difficulty = json.optString("difficulty")
        if (difficulty !in setOf("easy", "medium", "hard", "vocab")) return@withLock
        val kind = if (json.optBoolean("isBlitz")) "blitz" else if (difficulty == "vocab") "vocab" else "drill"
        val count = json.optInt("targetQuestions", 0)
        if (count !in setOf(-1, 10, 20) && !(kind == "vocab" && count in 1..20)) return@withLock
        val total = json.optInt("total", -1); val correct = json.optInt("correct", -1)
        if (total < 0 || correct !in 0..total) return@withLock
        if (timestamp < (state.featureState?.optJSONObject("wordRefs")?.optLong("at") ?: 0L)) { state.learningResultId = id; store.save(state); return@withLock }
        AssistantConversation.suspendQuestion(state); state.clearPending(); state.switchTopic("learning")
        state.learning = AssistantLearningContext(kind, difficulty, count, timestamp, total, correct)
        state.learningResultId = id
        store.save(state)
    }

    suspend fun captureChoices(context: Context, prompt: String, choices: List<ConflictOption>, priority: Boolean, persistent: Boolean): AyvaTalkEngine.TalkResponse = mutex.withLock {
        val state = AssistantSessionStore(context).load()
        state.clearPending()
        state.expected = "legacy_choice"; state.confirmationId = token(); state.confirmationAt = System.currentTimeMillis()
        state.pendingPriority = priority; state.pendingPersistent = persistent
        state.choices = choices.take(10).map { AssistantChoice(it.label, it.command) }
        AssistantSessionStore(context).save(state)
        choiceReply(prompt, state)
    }

    suspend fun rememberTopic(context: Context, response: AyvaTalkEngine.TalkResponse, query: String) = mutex.withLock {
        if (response.topicId in setOf("assistant", "tasks", "habits", "conversation", "confirmation", "choice")) return@withLock
        val state = AssistantSessionStore(context).load()
        response.topicId?.let { state.switchTopic(if (it in setOf("fallback", "chat")) "conversation" else "faq:$it") }
        state.lastReply = response.formattedText.take(4000)
        state.recentInputs = (state.recentInputs + query.take(500)).takeLast(6)
        AssistantSessionStore(context).save(state)
    }

    private suspend fun turn(raw: String, context: Context, app: FocusApplication, state: AssistantSession, priority: Boolean, persistent: Boolean): AyvaTalkEngine.TalkResponse? {
        val command = raw.trim().substringBefore(' ').lowercase()
        val supportText = if (command in setOf("/plan", "/talk", "/ask")) raw.trim().substringAfter(' ', "") else raw.trim()
        // Preserve the existing immediate-support owner before planning and general-topic handlers.
        if (Regex("(?i)^i (?:am going to|plan to|will) (?:kill myself|end my life|hurt myself now)\\b").containsMatchIn(supportText)) {
            invalidatePlanReview(context)
            ChatModeStore.set(context, ChatMode.TALK)
            return AssistantSocial.answer(context, state, supportText, supportText)
        }
        val planBodyRaw = if (command == "/plan") raw.trim().substringAfter(' ', "").trim().lowercase() else raw.trim().lowercase()
        // Dotted plan history controls must use the same authoritative undo/redo owner.
        val planBody = if ((command == "/plan" || ChatModeStore.get(context) == ChatMode.PLAN) && planBodyRaw.startsWith("."))
            planBodyRaw.drop(1).trim() else planBodyRaw
        val memoryText = (if (command in setOf("/talk", "/ask")) raw.trim().substringAfter(' ', "") else raw.trim())
        ConversationVoice.preference(context, state, memoryText)?.let { return it }
        AyvaPersonality.preference(context, state, memoryText)?.let { return it }
        AyvaPersonality.answer(context, state, memoryText)?.let {
            if (command in setOf("/talk", "/ask")) ChatModeStore.set(context, ChatMode.TALK)
            return it
        }
        val library = PlanningLibrary(context)
        Regex("(?i)^save (?:this )?discussion as (.+)$").matchEntire(memoryText)?.let {
            library.saveDiscussion(it.groupValues[1].trim(), state)
            return reply("Saved this discussion as ‘${it.groupValues[1].trim()}’. Resume it by name later; old questions and task confirmations are not saved.")
        }
        Regex("(?i)^resume (?:saved )?discussion (.+)$").matchEntire(memoryText)?.let {
            library.resumeDiscussion(it.groupValues[1].trim(), state); ChatModeStore.set(context, ChatMode.TALK)
            return reply(AssistantDiscussion.answer(state, "what did we decide")!!.text)
        }
        Regex("(?i)^delete (?:saved )?(discussion|template) (.+)$").matchEntire(memoryText)?.let {
            library.delete(if (it.groupValues[1].equals("discussion", true)) "discussions" else "templates", it.groupValues[2].trim())
            return reply("Deleted that saved ${it.groupValues[1]}. Current drafts and native tasks are unchanged.")
        }
        if (memoryText.lowercase() in setOf("list discussions", "saved discussions", "list templates", "my templates")) {
            val kind = if ("discussion" in memoryText.lowercase()) "discussions" else "templates"
            return reply("Saved $kind: " + library.list(kind).joinToString().ifBlank { "none" })
        }
        if (memoryText.lowercase() in setOf("what do you remember", "planning preferences", "show planning preferences")) return reply(library.preferences() + "\n" + library.describeFacts())
        if (memoryText.equals("forget planning preferences", true)) { library.forgetPreferences(); return reply("Forgot saved planning preferences. Templates, discussions and tasks are unchanged.") }
        if (memoryText.equals("forget planning library", true)) { library.clear(); return reply("Deleted saved discussions, templates and planning preferences. Native tasks and current drafts are unchanged.") }
        Regex("(?i)^(?:my usual task duration|default task duration) (?:is|:) (.+)$").matchEntire(memoryText)?.let {
            val minutes = PlanLanguage.duration(it.groupValues[1]) ?: return reply("What default duration should I remember, in minutes or hours?")
            library.setDefaultMinutes(minutes); return reply("Remembered $minutes minutes as your default for new plan steps without an explicit estimate.")
        }
        if ((command == "/plan" || ChatModeStore.get(context) == ChatMode.PLAN) && planBody in setOf("plan history", "saved history", "undo saved changes", "redo saved changes")) {
            val workspace = ConversationPlanStore(context).active() ?: return reply("There is no active plan")
            require(!workspace.hypotheticalView) { "Return to the active draft before using saved history" }
            if (planBody.endsWith("history")) return reply(SavedPlanHistory.describe(context, workspace.id))
            if (SavedPlanHistory.canUndo(context, workspace.id) || planBody == "redo saved changes") {
                val message = SavedPlanHistory.move(context, workspace.id, redo = planBody == "redo saved changes")
                state.clearPending(); state.undo = emptyList(); state.undoId = null
                return reply(message)
            }
        }
        if ((command == "/plan" || ChatModeStore.get(context) == ChatMode.PLAN) && planBody in setOf("redo", "redo that", "redo draft")) {
            val workspaces = ConversationPlanStore(context)
            val workspace = workspaces.active() ?: return reply("There is no active plan draft")
            require(!workspace.hypotheticalView) { "Return to the active draft before redoing an edit" }
            workspaces.put(workspace.historyMove(redo = true))
            state.clearPending()
            return reply("Redid the draft edit. Saved tasks are unchanged. Say ‘show plan’ to review it.")
        }
        if ((command == "/plan" || ChatModeStore.get(context) == ChatMode.PLAN) && planBody in setOf("undo", "undo that", "undo last action", "undo plan", "undo saved changes")) {
            val workspaces = ConversationPlanStore(context)
            val workspace = workspaces.active()
            if (workspace?.hypotheticalView == true)
                return reply("That was a hypothetical alternative. Say ‘show plan’ to return to your active draft before undoing a change. Your saved tasks are unchanged.")
            if (workspace?.pending != null && planBody != "undo saved changes") {
                workspaces.put(workspace.copy(pending = null, reviewedRevision = null, reviewedAt = 0, reviewProcess = null))
                return reply("Cancelled the unanswered planning change. Draft steps and saved tasks are unchanged.")
            }
            if (workspace != null && workspace.savedRevision != workspace.revision && planBody != "undo saved changes") {
                if (workspace.undoDrafts.isEmpty() && workspace.previousDraft == null) return reply("There is no earlier draft edit to undo. Saved tasks are unchanged.")
                val restored = workspace.historyMove(redo = false)
                workspaces.put(restored)
                return reply("Undid the last draft edit. Saved tasks are unchanged. Say ‘show plan’ to review the draft.")
            }
            if (workspace != null && SavedPlanHistory.canUndo(context, workspace.id)) {
                val message = SavedPlanHistory.move(context, workspace.id, redo = false)
                state.clearPending(); state.undo = emptyList(); state.undoId = null
                return reply(message)
            }
            if (workspace == null || workspace.savedUndoId == null || workspace.savedUndoId != state.undoId)
                return reply("There is no current saved change for this plan to undo. Your draft is still here.")
            val id = state.undoId ?: return reply("There is no current saved-task change to undo. Your plan draft is still here.")
            return internalCommand("undo $id", context, app, state)
        }
        // Normal chat gives a parsed calendar date/clock time task ownership before
        // planning, discussion and feature ambiguity. Explicit modes and live task edits
        // retain their handlers; date-only replies must never create a task named "tomorrow".
        if (ChatModeStore.get(context) == ChatMode.CHAT && !raw.trimStart().startsWith("/") &&
            !(state.topic == "tasks" && state.expected != null)) {
            val datedText = AssistantTaskLanguage.normalize(raw)
            val correction = Regex("(?i)^(?:actually|instead|make (?:it|that)|change (?:it|that)|move |reschedule )").containsMatchIn(datedText)
            if (!correction && AssistantTaskLanguage.request(datedText) == null) {
                val parsed = SmartDateParser.parse(datedText)
                if (parsed.timestamp != null) {
                    if (parsed.hasTaskText) {
                        state.clearPending(); state.clearSuspended()
                        return create(context, app, state, datedText, priority, persistent, false)
                    }
                    if (state.expected == null && state.taskId == null) {
                        state.clearPending(); state.switchTopic("tasks"); state.expected = "task_title"
                        state.pendingPriority = priority; state.pendingPersistent = persistent
                        state.pending = listOf(AssistantAction.CreateTask(Task(title = "", dueDate = parsed.timestamp)))
                        return reply("What should I remind you about on ${SmartDateParser.formatDueDate(parsed.timestamp)}?")
                    }
                }
            }
        }
        if (command !in setOf("/clear", "/clean", "/wipe")) {
            ConversationPlanner.respond(context, raw, state)?.let { return it }
        } else {
            ConversationPlanStore.clearAll(context)
            ChatModeStore.set(context, ChatMode.CHAT)
        }
        if (command in setOf("/clear", "/clean", "/wipe")) { state.clearPending(); state.taskId = null; state.habitId = null; state.topic = null; state.undo = emptyList(); state.undoId = null; state.candidates = emptyList(); state.recentInputs = emptyList(); state.lastReply = null; state.clearTaskReferences(); state.clearSuspended(); state.learning = null; state.learningResultId = null; state.planning = null; state.featureState = null; state.vocabularyCardId = null; return null }
        var text = raw.trim()
        if (command in setOf("/habit", "/habits") && raw.trim().indexOf(' ') < 0) { AssistantConversation.suspendQuestion(state); state.clearPending(); state.switchTopic("habits"); return null }
        if (text.startsWith("/ayva ")) return internalCommand(text.removePrefix("/ayva "), context, app, state)
        val appMode = command in setOf("/talk", "/ask")
        val taskAlias = command in setOf("/create", "/add", "/todo") || (command == "/task" && raw.substringAfter(' ', "").lowercase() !in setOf("", "all", "today", "list", "pending", "everything"))
        if (command in setOf("/talk", "/ask")) text = raw.substringAfter(' ', "")
        else if (taskAlias) text = raw.substringAfter(' ', "")
        else if (command in setOf("/reschedule", "/complete", "/delete", "/reopen", "/priority", "/focus", "/pomodoro", "/undo", "/routine", "/habit", "/habits", "/lock", "/unlock", "/block", "/unblock")) text = com.focusbyrj.app.util.command.AyvaChatCommands.talkQuery(raw)
        else if (raw.startsWith("/")) {
            AssistantConversation.suspendQuestion(state)
            state.clearPending()
            if (command == "/tasks" || command == "/task") rememberList(raw, app, state)
            else {
                val learning = AssistantLearningLanguage.command(raw)
                state.switchTopic(if (learning != null) "learning" else command.removePrefix("/"))
                if (learning != null) state.learning = learning
            }
            return null
        }
        if (AssistantGuidedPlanning.acceptsLiteral(state) && Regex("(?i)^(?:goal|name|steps|topics):").containsMatchIn(text.trim()))
            return AssistantGuidedPlanning.answer(context, state, text.trim()) ?: reply("Open the current plan before editing its fields.")
        val quotedText = text.trim()
        val literal = quotedText.takeIf { it.length >= 2 && (it.first() == '"' && it.last() == '"' || it.first() == '“' && it.last() == '”') }?.let { it.substring(1, it.lastIndex) }
        val literalField = state.expected in setOf("task_title", "habit_title", "task_new_title", "task_details") ||
            state.expected == "habit_edit_value" && (state.pending.singleOrNull() as? AssistantAction.EditHabit)?.field == HabitField.TITLE
        if (!appMode && !taskAlias && literal != null && literalField) {
            // Literal field data precedes typo normalization, settings help and command dispatch.
            val opaque = "\"$literal\""
            return when (state.expected) {
                "task_title" -> if (literal.isBlank() || literal.length > 500) reply("Use a task title of 1–500 characters.") else create(context, app, state, literal, state.pendingPriority, state.pendingPersistent, false, literalTitle = true)
                "habit_title" -> if (literal.isBlank() || literal.length > 200) reply("Use a habit title of 1–200 characters.") else
                    propose(context, app, state, listOf(AssistantAction.CreateHabit(literal)), "Create habit **$literal** with a target of once a day?")
                "habit_edit_value" -> habitEditAnswer(context, app, state, opaque) ?: reply(expectedHint(state))
                else -> editAnswer(context, app, state, opaque, app.database.taskDao().getAllActiveTasksList()) ?: reply(expectedHint(state))
            }
        }
        text = AssistantTaskLanguage.normalize(text)
        text = text.replace(Regex("(?i)^confirm[_ ]delete[_ ](all|overdue)$")) { "delete ${it.groupValues[1]} tasks" }
        if (taskAlias) { if (state.expected != "task_title") AssistantConversation.suspendQuestion(state); return create(context, app, state, text, priority, persistent, true) }
        if (appMode && state.expected != null && (state.topic in setOf("tasks", "planning") || state.expected?.startsWith("task_") == true || state.expected == "correction_date")) {
            AssistantConversation.suspendQuestion(state); state.clearPending(); state.switchTopic("app_help")
        }
        val lower = text.lowercase().trimEnd('.', '!', '?')
        ConversationReasoning.general(context, state, text)?.let { return it }
        val interpretation = AssistantInterpretation.interpret(text)
        if (interpretation.blocksDispatch) {
            AssistantConversation.suspendQuestion(state); state.clearPending()
            state.switchTopic("conversation")
            return reply(AssistantInterpretation.explanation(interpretation))
        }
        ConversationRegistry.answer(state, text)?.let { return reply(it) }
        AssistantGuidedPlanning.answer(context, state, text)?.let { return it }
        if (ConversationRouting.winner(text, state) == ConversationRouting.Owner.KNOWLEDGE) GeneralConversation.answer(context, state, text)?.let { return it }
        AssistantDictionary.answer(context, app, state, text)?.let { return featureTurn(context, app, state, it) }
        AssistantPersonalization.handle(context, text)?.let { result ->
            if (result.forgetConversation) {
                ConversationPlanStore.clearAll(context); ChatModeStore.set(context, ChatMode.CHAT)
                state.clearPending(); state.taskId = null; state.habitId = null; state.topic = null; state.undo = emptyList(); state.undoId = null; state.recentInputs = emptyList()
                state.clearTaskReferences(); state.clearSuspended(); state.learning = null; state.planning = null; state.featureState = null
            }
            return pendingReply(context, state, result.text + if (state.expected == null) "" else " ${expectedHint(state)}")
        }
        AssistantReplyStyle.preference(context, text)?.let { message ->
            return pendingReply(context, state, message + if (state.expected == null) "" else " ${expectedHint(state)}")
        }
        if (AssistantLanguage.acknowledgement(text)) return pendingReply(context, state, AssistantReplyStyle.acknowledgement(context, state))
        AssistantInitiative.dismiss(context, state, text)?.let { return it }
        GeneralConversation.answer(context, state, text)?.let { return it }
        AssistantSocial.answer(context, state, text, if (appMode) raw.substringAfter(' ', "") else raw, appMode)?.let { return AssistantInitiative.attach(context, app, state, text, it) }
        AssistantConversationSupport.answer(context, app, state, text)?.let { return pendingReply(context, state, it) }
        AssistantHabitConversation.information(app, state, text)?.let { return pendingReply(context, state, it) }
        if (lower in setOf("not yet", "wait", "hold on") && state.expected == "confirm")
            return pendingReply(context, state, "Okay. The preview is still here; nothing in it has been applied.")
        if (lower in setOf("cancel", "never mind", "nevermind", "forget it", "stop that")) { if (state.expected == "plan_budget") state.planning = null; state.featureState?.remove("routineDraft"); state.featureState?.remove("appSelection"); state.clearPending(); state.clearSuspended(); return reply("Cancelled the pending request. Your saved items are unchanged.") }
        if (lower in setOf("undo", "undo that", "undo last", "undo last action", "take that back")) {
            state.clearPending()
            return undo(context, state, state.undoId)
        }
        if (lower in setOf("yes", "yep", "yeah", "confirm", "do it", "go ahead", "sure", "okay", "ok") && !expectsBoolean(state)) {
            if (state.expected == "confirm" && state.confirmationId != null) return confirm(context, app, state, state.confirmationId!!)
            if (state.choices.size == 1) return choose(context, app, state, 0)
            return reply(if (state.expected != null) "I still need a specific answer to the last question. ${expectedHint(state)}" else "I'm ready. Tell me what you'd like to do.")
        }
        if (lower in setOf("no", "nope", "not now") && !expectsBoolean(state)) { if (state.expected == "plan_budget") state.planning = null; state.featureState?.remove("routineDraft"); state.clearPending(); state.clearSuspended(); return reply("Okay. I haven't applied the pending action.") }
        if (state.choices.isNotEmpty()) {
            choiceIndex(lower, state.choices.size)?.let { return choose(context, app, state, it) }
            val label = lower.replace(Regex("^(?:no[, ]+|not that one[, ]+|i meant )"), "").removePrefix("the ")
            val matching = state.choices.indices.filter { state.choices[it].label.equals(label, true) }
            if (matching.size == 1) return choose(context, app, state, matching.single())
            if (lower in setOf("done", "it", "that", "the other one", "not that one")) return choiceReply("I still need to know which item you mean.", state)
        }
        if (appMode && AssistantCompoundParser.split(text).size > 1) {
            state.clearPending()
            state.switchTopic("app_help")
            return reply("Please send one app question or setting change at a time in /talk. This keeps each proposed change clear.")
        }
        if (lower in setOf("change it back", "put it back") && state.topic?.startsWith("setting:") == true) {
            val key = state.topic!!.removePrefix("setting:")
            val record = state.undo.singleOrNull()?.takeIf { it.optString("kind") == "pref" && it.optString("key") == key }
            return if (record != null) undo(context, state, state.undoId) else reply("There's no matching recent setting change to undo. Name the setting and the value you want.")
        }
        AssistantRoutineConversation.answer(context, app, state, text)?.let { turn ->
            return if (turn is FeatureTurn.Reply) pendingReply(context, state, turn.text) else featureTurn(context, app, state, turn)
        }
        AssistantHabitConversation.revise(app, state, text)?.let { turn ->
            return if (turn is FeatureTurn.Reply) pendingReply(context, state, turn.text) else {
                val action = (turn as FeatureTurn.Preview).actions.single() as AssistantAction.EditHabit
                previewHabitEdit(context, app, state, action)
            }
        }
        AssistantAppControl.answer(context, app, state, text)?.let { return featureTurn(context, app, state, it) }
        // App/settings questions interrupt stale task slots before task-edit or creation heuristics.
        if (appMode || state.expected != "task_edit_field" || AssistantEditDialogue.field(text) == null)
            AssistantAppKnowledge.answer(context, state, text, appMode)?.let { return featureTurn(context, app, state, it) }
        if (appMode && OfflineNluEngine.isExplicitCreation(text) && !AssistantFeatureLanguage.isRequest(text)) {
            state.clearPending(); state.switchTopic("app_help")
            return reply("We can talk this through here. To make it a saved action, use /plan for a plan or /create for one task.")
        }
        if (!appMode && Regex("(?i)^remind me(?:\\s+.*)?$").matches(text) && !text.lowercase().startsWith("remind me to ") && !Regex("(?i)\\b(?:about|of)\\b").containsMatchIn(text)) {
            val date = SmartDateParser.parse(text.removePrefix("remind me").trim())
            if (text.equals("remind me", true) || (date.timestamp != null && !date.hasTaskText)) {
                AssistantConversation.suspendQuestion(state)
                state.clearPending(); state.switchTopic("tasks"); state.expected = "task_title"; state.pendingPriority = priority; state.pendingPersistent = persistent
                state.pending = listOf(AssistantAction.CreateTask(Task(title = "", dueDate = date.timestamp)))
                return reply("What should I remind you about${date.timestamp?.let { " on ${SmartDateParser.formatDueDate(it)}" }.orEmpty()}?")
            }
        }
        val tasks = app.database.taskDao().getAllActiveTasksList()
        val pending = tasks.filter { !it.isCompleted }
        if (state.taskId != null && tasks.none { it.id == state.taskId }) state.taskId = null

        AssistantPlanning.budgetAnswer(context, state, text, pending, isNewIntent(lower))?.let { return it }
        if (AssistantRoutineDialogue.hasDraft(state)) AssistantRoutineDialogue.answer(context, app, state, text, isNewIntent(lower))?.let { return featureTurn(context, app, state, it) }

        if (AssistantConversation.wantsResume(lower)) {
            if (state.expected in setOf("confirm", "choice", "legacy_choice")) state.clearPending()
            return if (AssistantConversation.resumeQuestion(state, tasks, lower)) {
                val names = state.selectedTaskIds.mapNotNull { id -> tasks.firstOrNull { it.id == id }?.title }
                reply("Let's continue${if (names.isEmpty()) "" else " with " + names.take(5).joinToString(", ") { "**$it**" }}. ${expectedHint(state)}")
            }
            else reply("There's no unfinished question available to resume. Tell me the request again; old confirmations won't be reused.")
        }
        AssistantConversation.reference(text)?.let { reference ->
            val implicitReference = reference == "__other__" || (lower.startsWith("no") && !Regex("\\btask\\b").containsMatchIn(lower))
            AssistantConversation.suspendQuestion(state)
            state.clearPending()
            if (implicitReference && state.topic !in setOf("tasks", "planning")) return reply("Which ${if (state.topic == "habits") "habit" else "item"} do you mean? Name it or choose one of the displayed options.")
            val subject = when (reference) {
                "__previous__" -> state.recentTaskIds.firstOrNull { id -> id != state.taskId && pending.any { it.id == id } }?.let { "id:$it" }
                "__other__" -> {
                    val matching = if (lower.contains("other reading one")) AssistantTaskLanguage.select("read", pending, state).tasks.map { it.id } else pending.map { it.id }
                    state.candidates.filter { id -> id != state.taskId && id in matching }.singleOrNull()?.let { "id:$it" }
                }
                else -> reference
            }
            return handleTask(context, app, state, AssistantTaskRequest("focus", subject.orEmpty()), tasks)
        }
        editAnswer(context, app, state, text, tasks)?.let { return it }
        habitEditAnswer(context, app, state, text)?.let { return it }
        if (state.expected == "correction_date" && !isNewIntent(lower)) {
            val date = SmartDateParser.parse(text)
            if (date.timestamp == null || date.hasTaskText) return reply("What exact day and time should I use? For example, ‘tomorrow at 3pm’. Nothing has changed yet.")
            if (state.versions.any { (id, version) -> tasks.none { it.id == id && it.updatedAt == version && !it.isCompleted } }) { state.clearPending(); return reply("That task changed while we were talking. Please ask for the correction again.") }
            val edits = state.pending.map { if (it is AssistantAction.EditTasks && it.field == TaskField.DUE && it.value.isBlank()) it.copy(value = date.timestamp.toString()) else it }
            return propose(context, app, state, edits, correctionPreview(edits))
        }

        // Slot replies are constrained to the type requested, and a new explicit intent interrupts them.
        if (!appMode && state.expected == "task_title" && !AssistantFeatureLanguage.isRequest(text) && (!isNewIntent(lower) || OfflineNluEngine.isExplicitCreation(text))) {
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
            val date = SmartDateParser.parse(text.replaceFirst(Regex("(?i)^(?:actually|instead)[, ]+"), ""))
            if (date.timestamp != null && !date.hasTaskText) {
                if (state.versions.any { (id, version) -> tasks.none { it.id == id && it.updatedAt == version && !it.isCompleted } }) { state.clearPending(); return reply("That task changed while we were talking. Please ask for the new time again.") }
                val ids = state.pending.filterIsInstance<AssistantAction.EditTasks>().firstOrNull()?.ids ?: listOfNotNull(state.taskId)
                val edits = ids.map { id ->
                    val previous = app.database.taskDao().getTaskById(id)?.dueDate
                    AssistantAction.EditTasks(listOf(id), TaskField.DUE,
                        (if (!date.hasTime && previous != null) preserveTime(date.timestamp, previous) else date.timestamp).toString())
                }
                if (ids.size > 1) return propose(context, app, state, edits, "Reschedule these ${ids.size} tasks to ${SmartDateParser.formatDueDate(date.timestamp)}?")
                return apply(context, state, edits)
            }
            if (Regex("(?i)\\b(?:later|lunch|morning|afternoon|evening|tomorrow|today|next|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b").containsMatchIn(text) && AssistantTaskLanguage.request(text) == null)
                return reply("I couldn't read that date. What exact day and time should I use? For example, ‘tomorrow at 3pm’.")
        }

        AssistantTaskDateRepair.repair(text, state, tasks)?.let { revision ->
            revision.clarification?.let { message ->
                if (revision.stale) state.clearPending() else state.confirmationId = null
                return reply(message)
            }
            if (revision.actions.isEmpty()) {
                state.clearPending()
                return reply("No date changes remain in that preview. Your saved tasks are unchanged.")
            }
            val preview = revision.actions.filterIsInstance<AssistantAction.EditTasks>().joinToString("\n") { edit ->
                "• **${tasks.first { it.id == edit.ids.single() }.title}** — ${SmartDateParser.formatDueDate(edit.value.toLong())}"
            }
            state.focusTasks(revision.actions.filterIsInstance<AssistantAction.EditTasks>().flatMap { it.ids })
            return propose(context, app, state, revision.actions, "Updated preview—apply these date changes?\n$preview")
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
        // Any answer consumed by its live slot has returned above. Preserve an
        // unfinished question even when the interruption is a noun/status query.
        AssistantConversation.suspendQuestion(state)
        state.clearPending()
        AssistantTranscriptFeatures.answer(context, app, state, text)?.let { return featureTurn(context, app, state, it) }
        val parts = AssistantCompoundParser.split(text)
        if (parts.size > 1) return compound(context, app, state, parts, priority, persistent)

        // Privacy boundary precedes creation heuristics; an explicit reminder about a note is still a task.
        if (!taskAlias && Regex("(?i)^(?:read|show|search|find|edit|delete|create|add|new|open|list)\\s+(?:my\\s+|a\\s+)?notes?\\b|^(?:my )?notes$").containsMatchIn(text)) {
            state.clearPending(); state.switchTopic("notes")
            return reply("For privacy, notes cannot be created, searched, or viewed through the chat window. Open Keep Notes to view or change them.", listOf(TalkAction.NavigateAppScreen("notes", "Open Keep Notes", "📝")))
        }
        AssistantChecklist.answer(app, state, text)?.let { return featureTurn(context, app, state, it) }
        AssistantFeatures.answer(context, app, state, text)?.let { return featureTurn(context, app, state, it) }
        AssistantTaskViews.answer(state, text, tasks)?.let { return it }
        AssistantPlanning.respond(context, state, text, pending)?.let { return it }
        val contextualDone = lower == "done" && (state.topic == "habits" && state.habitId != null || state.topic in setOf("tasks", "planning") && (state.taskId != null || state.selectedTaskIds.isNotEmpty()))
        val question = interpretation.kind in setOf(UtteranceKind.QUESTION, UtteranceKind.CONVERSATION) && !contextualDone && !OfflineNluEngine.isExplicitCreation(text)
        state.taskId?.let { id -> tasks.find { it.id == id && !it.isCompleted } }?.let { task ->
            AssistantConversation.correction(text, task)?.let { correction ->
                correction.clarification?.let { return reply(it) }
                if (correction.needsDate) {
                    state.expected = "correction_date"; state.pending = correction.actions
                    state.versions = mapOf(task.id to task.updatedAt)
                    return reply("I can correct **${task.title}**. What exact day and time do you mean? For example, ‘tomorrow at 3pm’. Nothing has changed yet.")
                }
                val edits = correction.actions.map { if (correction.preserveClock && task.dueDate != null && it is AssistantAction.EditTasks && it.field == TaskField.DUE) it.copy(value = preserveTime(it.value.toLong(), task.dueDate).toString()) else it }
                return if (edits.size > 1) propose(context, app, state, edits, correctionPreview(edits))
                else apply(context, state, edits)
            }
        }
        if (!question) {
            capabilityRequest(context, app, state, text, tasks)?.let { return it }
            AssistantEditDialogue.request(text, state)?.let { return handleTask(context, app, state, it, tasks) }
            if (state.topic == "habits" && lower in setOf("done", "complete it", "log it", "log that", "finished it", "mark it done")) {
                return featureRequest(context, app, state, "log habit it")
            }
            if (lower == "done" && state.taskId != null) return handleTask(context, app, state, AssistantTaskRequest("complete", "it"), tasks)
            val timeReply = text.replace(Regex("(?i)^(?:actually|instead|make (?:it|that)|change (?:it|that) to)\\s+"), "")
            val timedTask = tasks.firstOrNull { it.id == state.taskId && !it.isCompleted && it.dueDate != null }
            if (timedTask != null && AssistantTaskDateRepair.clockOnly(timeReply)) {
                val existing = AssistantAction.EditTasks(listOf(timedTask.id), TaskField.DUE, timedTask.dueDate.toString())
                val versions = mapOf(timedTask.id to timedTask.updatedAt)
                val temporary = state.copy(expected = "confirm", pending = listOf(existing), versions = versions)
                val revision = AssistantTaskDateRepair.repair(timeReply, temporary, tasks)!!
                state.guards = mapOf("task:${timedTask.id}" to TaskJson.encode(timedTask).toString())
                if (revision.clarification != null) {
                    state.expected = "confirm"; state.pending = listOf(existing); state.versions = versions; state.confirmationId = null
                    return reply(revision.clarification)
                }
                return apply(context, state, revision.actions)
            }
            val parsedReply = SmartDateParser.parse(timeReply)
            if (state.taskId != null && parsedReply.timestamp != null && !parsedReply.hasTaskText) {
                return handleTask(context, app, state, AssistantTaskRequest("due", "it", parsedReply.timestamp.toString(), !parsedReply.hasTime), tasks)
            }
            if (!Regex("(?i)^(?:complete|check|log|done|finished)\\s+habit\\b").containsMatchIn(text)) {
                AssistantTaskLanguage.request(text.replaceFirst(Regex("(?i)^actually[, ]+"), ""))?.let { return handleTask(context, app, state, it, tasks) }
            }
        }
        if (Regex("(?i)^(?:fix|edit|update) (?:it|that|this|them)$").matches(text))
            return reply("What would you like me to change? Name the task or setting and what should be different.")
        val explicit = OfflineNluEngine.isExplicitCreation(text) || Regex("(?i)^(?:urgent|important|priority)[: ,]").containsMatchIn(text)
        if (explicit && !appMode) return create(context, app, state, text, priority, persistent, false)
        if (state.topic?.startsWith("setting:") == true) {
            val spec = AssistantPreferences.specs.find { it.key == state.topic?.removePrefix("setting:") }
            val followUp = Regex("(?i)^(?:(?:change|set|make|turn) (?:it|that)(?: to)?|actually)(?:\\s+(.*))?$").matchEntire(text)
            if (spec != null && followUp != null) {
                val value = AssistantPreferences.extractValue(spec, followUp.groupValues[1])
                if (value == null) { state.clearPending(); state.switchTopic("setting:${spec.key}"); state.expected = "setting_value"; state.pending = listOf(AssistantAction.SetPreference(spec.key, spec.type, "")); return reply("${spec.title}: ${settingHint(spec)}") }
                return propose(context, app, state, listOf(AssistantAction.SetPreference(spec.key, spec.type, value)), "Set ${spec.title} to $value ${spec.unit}?")
            }
        }
        dialogue(context, app, state, text, pending)?.let { return AssistantInitiative.attach(context, app, state, text, it) }
        knowledgeFollowUp(context, state, lower)?.let { return it }
        featureRequest(context, app, state, text)?.let { return it }
        if (AssistantAppKnowledge.legacyFeature(text)) return null
        if (question || OfflineNluEngine.isConversationalStatement(text)) {
            state.clearPending()
            if (!(state.topic?.startsWith("faq:") == true && AssistantConversation.isTopicFollowUp(lower))) state.switchTopic("conversation")
            return if (appMode) appHelpFallback(state, text) else null
        }
        val fallback = OfflineNluEngine.parse(text, pending)
        if (fallback.intent in setOf(NluIntent.COMPLETE, NluIntent.DELETE, NluIntent.RESCHEDULE)) {
            val operation = when (fallback.intent) { NluIntent.COMPLETE -> "complete"; NluIntent.DELETE -> "trash"; else -> "due" }
            val subject = if (fallback.isAllTasks) if (lower.contains("overdue")) "overdue tasks" else "all tasks"
                else fallback.targetTask?.id?.let { "id:$it" } ?: fallback.filterQuery.orEmpty()
            return handleTask(context, app, state, AssistantTaskRequest(operation, subject, fallback.targetDateMs?.toString(), fallback.targetDateMs != null && !fallback.hasExplicitTimeSpecified), tasks)
        }
        if (fallback.intent == NluIntent.CREATE_TASK && !appMode) return create(context, app, state, text, priority, persistent, false)
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
        GeneralConversation.answer(context, state, text, fallback = true)?.let { return it }
        if (appMode) return appHelpFallback(state, text)
        // Keep existing app-specific FAQ and feature dispatch; remember its choices at the UI boundary.
        AssistantConversation.suspendQuestion(state); state.clearPending(); state.switchTopic("conversation")
        return null
    }

    private fun appHelpFallback(state: AssistantSession, input: String): AyvaTalkEngine.TalkResponse {
        AssistantConversation.suspendQuestion(state); state.clearPending(); state.switchTopic("app_help")
        if (!Regex("(?i)\\b(?:setting|permission|ayva|app|notification|backup|widget|vault|screen time)\\b").containsMatchIn(input)) {
            val turn = AssistantDiscussion.answer(state, "let's talk through ${input.take(350)}")!!
            AssistantDiscussion.resumeArchive(state, turn.frame!!)
            return reply("Tell me a little more about ${input.take(200)}. What part would you like to work through? I can use the details you give me to compare choices or organize next steps.")
        }
        return reply("Which part of Ayva do you mean? Name the setting or screen, or describe what you expected to happen.")
    }

    private suspend fun create(context: Context, app: FocusApplication, state: AssistantSession, text: String, priority: Boolean, persistent: Boolean, allowDuplicate: Boolean, literalTitle: Boolean = false): AyvaTalkEngine.TalkResponse {
        var draft = if (literalTitle) Task(title = text, isPriority = priority, isPersistent = persistent) else AssistantTaskLanguage.draft(text, priority, persistent)
        if (draft != null && draft.dueDate == null && state.expected == "task_title") {
            val due = state.pending.filterIsInstance<AssistantAction.CreateTask>().singleOrNull()?.draft?.dueDate
            if (due != null) draft = draft.copy(dueDate = due)
        }
        if (draft == null) {
            state.clearPending(); state.switchTopic("tasks"); state.expected = "task_title"; state.pendingPriority = priority; state.pendingPersistent = persistent
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
        selected.clarification?.let { return reply(it) }
        if (request.operation == "focus" && selected.tasks.size != 1 && !selected.group) state.switchTopic("tasks")
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
        state.focusTasks(ids)
        if (request.operation == "edit") {
            state.clearPending(); state.expected = "task_edit_field"
            state.versions = selected.tasks.associate { it.id to it.updatedAt }
            return reply("Which part of ${if (ids.size == 1) "**${selected.tasks.single().title}**" else "these ${ids.size} tasks"} should change—date, title, priority, details, repeat schedule or persistent reminders?",
                listOf("date" to "Date or time", "title" to "Title", "priority" to "Priority").map { (query, label) ->
                    TalkAction.AskQuery(query, label, if (AssistantReplyStyle.emoji(context)) "💬" else "")
                })
        }
        if (request.operation == "focus") return reply(if (ids.size == 1) "We're talking about **${selected.tasks.single().title}**. You can change its time, rename it or mark it done. Selecting it hasn't changed the task."
            else "Selected these ${ids.size} tasks:\n" + selected.tasks.joinToString("\n") { "• ${it.title}" } + "\nYou can say ‘move them to Friday’ or ‘make them priority’. Selecting them hasn't changed the tasks.")
        if (request.operation == "due" && request.value == null) {
            state.clearPending(); state.expected = "task_date"
            state.pending = listOf(AssistantAction.EditTasks(ids, TaskField.DUE, ""))
            state.versions = selected.tasks.associate { it.id to it.updatedAt }
            return reply("When should ${if (ids.size == 1) "**${selected.tasks.single().title}**" else "these ${ids.size} tasks"} be due? Try ‘tomorrow at 6pm’ or ‘in 30 minutes’.")
        }
        val actions = selected.tasks.map { task ->
            val adjusted = if (request.operation == "due" && request.value != "none" && request.preserveClock && task.dueDate != null) {
                request.copy(value = preserveTime(request.value!!.toLong(), task.dueDate).toString())
            } else request
            AssistantTaskLanguage.action(adjusted, listOf(task.id)) ?: error("I need a value for that change")
        }
        if (selected.group || ids.size > 1) return propose(context, app, state, actions, "Confirm: ${request.operation} these **${ids.size} tasks**?\n" + selected.tasks.take(10).mapIndexed { index, task ->
            "• ${task.title}" + (actions[index] as? AssistantAction.EditTasks)?.takeIf { it.field == TaskField.DUE }?.let { " — ${if (it.value == "none") "no due date" else SmartDateParser.formatDueDate(it.value.toLong())}" }.orEmpty()
        }.joinToString("\n"))
        return apply(context, state, actions)
    }

    private fun expectsBoolean(state: AssistantSession) = state.expected in setOf("task_priority", "task_persistent") ||
        state.expected == "setting_value" && state.pending.filterIsInstance<AssistantAction.SetPreference>().singleOrNull()?.type == "boolean"

    private fun confirmationActions(context: Context, state: AssistantSession): List<TalkAction> {
        val id = state.confirmationId ?: return emptyList()
        return listOf(TalkAction.AskQuery("/ayva confirm $id", "Confirm", if (AssistantReplyStyle.emoji(context)) "✅" else ""),
            TalkAction.AskQuery("/ayva cancel $id", "Cancel", if (AssistantReplyStyle.emoji(context)) "✖" else ""))
    }

    private fun pendingReply(context: Context, state: AssistantSession, message: String): AyvaTalkEngine.TalkResponse =
        if (state.choices.isNotEmpty()) choiceReply(message, state)
        else reply(message, if (state.expected == "confirm") confirmationActions(context, state) else emptyList())

    private suspend fun editAnswer(context: Context, app: FocusApplication, state: AssistantSession, text: String, tasks: List<Task>): AyvaTalkEngine.TalkResponse? {
        if (state.expected !in AssistantEditDialogue.slots) return null
        val field = AssistantEditDialogue.field(text)
        val priority = AssistantEditDialogue.priority(text)
        val persistent = AssistantEditDialogue.toggle(text)
        val recurrence = AssistantEditDialogue.recurrence(text)
        val quoted = text.length >= 2 && text.first() == '"' && text.last() == '"'
        val anotherRequest = AssistantTaskLanguage.request(text) != null || AssistantEditDialogue.request(text, state) != null ||
            AssistantHabitEditing.request(text, state) != null ||
            text.lowercase() in setOf("timer status", "time remaining", "my habits", "habits", "my routines", "routines", "status")
        val titleAnswer = state.expected in setOf("task_new_title", "task_details") && (quoted || !anotherRequest &&
            (!isNewIntent(text.lowercase()) || OfflineNluEngine.isExplicitCreation(text) &&
                !Regex("(?i)^(?:(?:add|create|remind|schedule|remember)\\b|new\\s+(?:task|todo|reminder|habit|note|routine)\\b)").containsMatchIn(text)))
        val valid = when (state.expected) {
            "task_edit_field" -> field != null
            "task_priority" -> priority != null
            "task_persistent" -> persistent != null
            "task_recurrence" -> recurrence != null
            else -> titleAnswer
        }
        if (!valid) {
            if (isNewIntent(text.lowercase()) || anotherRequest) return null
            return reply(expectedHint(state))
        }
        val ids = state.selectedTaskIds.ifEmpty { listOfNotNull(state.taskId) }
        if (ids.isEmpty() || ids.size > 100 || ids.any { id -> tasks.none { it.id == id && !it.isCompleted && !it.isTrashed && it.updatedAt == state.versions[id] } }) {
            state.clearPending()
            return reply("That task selection changed or is no longer available. Choose the task again before editing it.")
        }
        if (state.expected == "task_edit_field") {
            if (field == TaskField.DUE) return handleTask(context, app, state,
                AssistantTaskRequest("due", if (ids.size == 1) "id:${ids.single()}" else "selected tasks"), tasks)
            state.expected = when (field) {
                TaskField.TITLE -> "task_new_title"; TaskField.DETAILS -> "task_details"; TaskField.RECURRENCE -> "task_recurrence"
                TaskField.PERSISTENT -> "task_persistent"; else -> "task_priority"
            }
            state.pending = listOf(AssistantAction.EditTasks(ids, field!!, ""))
            return reply(expectedHint(state))
        }
        val value = when (state.expected) {
            "task_priority" -> priority.toString()
            "task_persistent" -> persistent.toString()
            "task_recurrence" -> recurrence!!
            "task_details" -> if (text.equals("clear details", true)) "" else if (quoted) text.substring(1, text.lastIndex) else text
            else -> if (quoted) text.substring(1, text.lastIndex) else text
        }
        if (state.expected == "task_new_title" && (value.isBlank() || value.length > 500)) return reply("Use a title of 1–500 characters. ${expectedHint(state)}")
        val targetField = when (state.expected) {
            "task_priority" -> TaskField.PRIORITY; "task_details" -> TaskField.DETAILS; "task_recurrence" -> TaskField.RECURRENCE
            "task_persistent" -> TaskField.PERSISTENT; else -> TaskField.TITLE
        }
        val names = ids.map { id -> tasks.first { it.id == id }.title }
        val subject = names.joinToString(", ") { "**$it**" }
        val preview = when (targetField) {
            TaskField.TITLE -> "Rename $subject to **$value**?"
            TaskField.PRIORITY -> "Set $subject to **${if (priority == true) "high" else "normal"} priority**?"
            TaskField.PERSISTENT -> "Turn persistent reminders **${if (persistent == true) "on" else "off"}** for $subject?"
            TaskField.RECURRENCE -> "Set the repeat schedule for $subject to **${value.lowercase()}**?"
            else -> if (value.isEmpty()) "Clear the details of $subject?" else "Replace the details of $subject with:\n$value\nApply this change?"
        }
        return propose(context, app, state, listOf(AssistantAction.EditTasks(ids, targetField, value)), preview)
    }

    private fun suggestion(context: Context, query: String, label: String) =
        TalkAction.AskQuery(query, label, if (AssistantReplyStyle.emoji(context)) "💬" else "")

    private suspend fun habitEditAnswer(context: Context, app: FocusApplication, state: AssistantSession, text: String): AyvaTalkEngine.TalkResponse? {
        if (state.expected != "habit_edit_value") return null
        val draft = state.pending.filterIsInstance<AssistantAction.EditHabit>().singleOrNull() ?: run { state.clearPending(); return reply("Please ask for that habit edit again.") }
        val quoted = text.startsWith('"') && text.endsWith('"') && text.length >= 2
        if (!quoted && (AssistantHabitEditing.request(text, state) != null || AssistantTaskLanguage.request(text) != null ||
                isNewIntent(text.lowercase()) && !(draft.field == HabitField.TITLE && OfflineNluEngine.isExplicitCreation(text) &&
                    !Regex("(?i)^(?:(?:add|create|remind|schedule|remember)\\b|new\\s+(?:task|todo|reminder|habit|note|routine)\\b)").containsMatchIn(text)))) return null
        val habit = app.database.habitDao().getHabitById(draft.id)
        if (habit != draft.expected) { state.clearPending(); return reply("That habit changed while we were talking. Please ask for the edit again.") }
        val value = AssistantHabitEditing.value(draft.field, text) ?: return reply(AssistantHabitEditing.hint(draft.field))
        return previewHabitEdit(context, app, state, draft.copy(value = value))
    }

    private suspend fun handleHabitEdit(context: Context, app: FocusApplication, state: AssistantSession, request: HabitEditRequest): AyvaTalkEngine.TalkResponse {
        val restoring = request.field == HabitField.ARCHIVED && request.value == "false"
        val habits = app.database.habitDao().getAllHabitsSync().filter { it.isArchived == restoring }
        val subject = request.subject.trim()
        val identified = subject.startsWith("id:", true) || subject.lowercase() in setOf("it", "that", "this")
        val matches = when {
            subject.startsWith("id:", true) -> habits.filter { it.id == subject.substringAfter(':').toLongOrNull() }
            subject.lowercase() in setOf("it", "that", "this") -> habits.filter { state.topic == "habits" && it.id == state.habitId }
            else -> habits.filter { it.title.equals(subject, true) }.ifEmpty { habits.filter { it.title.contains(subject, true) } }
        }
        if (matches.isEmpty() && identified) return reply("That habit is no longer available for this edit. Name the habit explicitly.")
        if (matches.size != 1) {
            state.switchTopic("habits")
            return setChoices(state, "Which ${if (restoring) "archived " else ""}habit should I edit? Nothing has changed.", matches.ifEmpty { habits }.take(8).map { habit ->
                val data = JSONObject().put("id", habit.id).put("field", request.field.name).put("value", request.value ?: JSONObject.NULL)
                AssistantChoice(habit.title, "habit_request:$data")
            })
        }
        val habit = matches.single()
        state.switchTopic("habits"); state.habitId = habit.id
        val value = request.value?.let { AssistantHabitEditing.value(request.field, it) }
        val action = AssistantAction.EditHabit(habit.id, request.field, value.orEmpty(), habit)
        if (value == null) {
            state.clearPending(); state.expected = "habit_edit_value"; state.pending = listOf(action)
            return reply("For **${habit.title}**: ${AssistantHabitEditing.hint(request.field)}")
        }
        return previewHabitEdit(context, app, state, action)
    }

    private suspend fun previewHabitEdit(context: Context, app: FocusApplication, state: AssistantSession, action: AssistantAction.EditHabit): AyvaTalkEngine.TalkResponse {
        val habit = action.expected
        val preview = when (action.field) {
            HabitField.INTERVAL -> "Switch **${habit.title}** to interval reminders every ${action.value} minutes within its saved window? Reminders remain ${if (habit.isReminderEnabled) "on" else "off"}."
            HabitField.WINDOW -> action.value.split(',').map(String::toInt).let { "Set **${habit.title}** to interval mode with window ${AssistantFeatureLanguage.timeText(it[0], it[1])}? Its interval and reminder toggle stay as saved." }
            HabitField.REMINDER_TIME -> "Switch **${habit.title}** to one daily reminder at ${"%02d:%02d".format(action.value.removePrefix("@").toInt() / 60, action.value.removePrefix("@").toInt() % 60)} (device local time)? Its reminder toggle stays as saved."
            HabitField.TITLE -> "Rename habit **${habit.title}** to **${action.value}**?"
            HabitField.TARGET -> "Change **${habit.title}** from ${habit.targetPerDay} to **${action.value} per day**? Existing daily logs keep their recorded targets."
            HabitField.REMINDER -> "Turn reminders **${if (action.value == "true") "on" else "off"}** for habit **${habit.title}**?"
            HabitField.ARCHIVED -> "${if (action.value == "true") "Archive" else "Restore"} habit **${habit.title}**? Its history will be preserved."
        }
        return propose(context, app, state, listOf(action), preview)
    }

    private suspend fun capabilityRequest(context: Context, app: FocusApplication, state: AssistantSession, text: String, tasks: List<Task>): AyvaTalkEngine.TalkResponse? {
        AssistantHabitEditing.request(text, state)?.let { return handleHabitEdit(context, app, state, it) }
        val lower = text.lowercase().trimEnd('.', '!', '?')
        if (lower in setOf("archived habits", "show archived habits", "my archived habits")) {
            val habits = app.database.habitDao().getAllHabitsSync().filter { it.isArchived }
            state.switchTopic("habits")
            return reply(if (habits.isEmpty()) "You have no archived habits." else "Archived habits:\n" + habits.take(20).joinToString("\n") { "• **${it.title}**" } +
                if (habits.size > 20) "\nShowing 20 of ${habits.size}." else "",
                habits.take(3).map { suggestion(context, "restore habit id:${it.id}", "Restore ${it.title}") })
        }
        Regex("(?i)^(?:show|check) habit (.+)$").matchEntire(text.trimEnd('.', '!', '?'))?.let { match ->
            val subject = match.groupValues[1]
            val habits = app.database.habitDao().getAllHabitsSync().filter { !it.isArchived }
            val matches = when {
                subject.startsWith("id:", true) -> habits.filter { it.id == subject.substringAfter(':').toLongOrNull() }
                subject.lowercase() in setOf("it", "that") -> habits.filter { state.topic == "habits" && it.id == state.habitId }
                else -> habits.filter { it.title.equals(subject, true) }.ifEmpty { habits.filter { it.title.contains(subject, true) } }
            }
            if (matches.size != 1) {
                state.switchTopic("habits")
                return setChoices(state, "Which habit would you like to check?", matches.ifEmpty { habits }.take(8).map { AssistantChoice(it.title, "show habit id:${it.id}") })
            }
            val habit = matches.single()
            val progress = app.habitRepository.activeHabitsWithProgress.first().firstOrNull { it.habit.id == habit.id }
                ?: return reply("That habit is no longer active. Please choose it again.")
            state.switchTopic("habits"); state.habitId = habit.id
            return reply("**${habit.title}** — ${progress.completedToday}/${progress.targetToday} today.\nCurrent streak: ${progress.currentStreak} day(s); best: ${progress.bestStreak}.\nSaved daily target: ${habit.targetPerDay}. Reminders ${if (habit.isReminderEnabled) "on" else "off"}.\nSaved schedule: ${AssistantHabitConversation.schedule(habit)} (device local time).\nYou can log it, rename it, change its goal, reminder time or interval, or archive it.",
                listOf(suggestion(context, "log habit id:${habit.id}", "Log progress"), suggestion(context, "change habit id:${habit.id} goal", "Change goal")))
        }
        if (lower in setOf("what am i focusing on", "what am i focusing on?", "focus task", "show focus task")) return focusStatus(context, app, state)
        if (lower == "complete focus task") {
            val id = FocusSessionManager.taskId(context) ?: return reply("No running focus session is linked to a task. Say ‘focus on’ followed by a task name.")
            val task = tasks.firstOrNull { it.id == id && !it.isCompleted && !it.isTrashed && it.deletedAt == null }
                ?: return reply("The linked task is already completed or no longer active. The countdown is unchanged.")
            return handleTask(context, app, state, AssistantTaskRequest("complete", "id:${task.id}"), tasks)
        }
        AssistantFocusLanguage.request(text, tasks)?.let { request ->
            val subject = request.subject
            val suppliedDuration = request.duration
            val minutes = if (suppliedDuration == null) AssistantPersonalization.focusMinutes(context) else durationMinutes(suppliedDuration)
                ?: return reply("Choose a focus duration from 1–240 minutes, such as ‘focus on it for 25 minutes’.")
            val pendingTasks = tasks.filter { !it.isCompleted && !it.isTrashed && it.deletedAt == null }
            val selected = if (request.literal) AssistantTaskSelection(pendingTasks.filter { it.title.equals(subject, true) }, false)
                else AssistantTaskLanguage.select(subject, pendingTasks, state)
            selected.clarification?.let { return reply(it) }
            if (selected.tasks.size != 1) {
                val available = selected.tasks.ifEmpty { tasks.filter { !it.isCompleted && !it.isTrashed && it.deletedAt == null } }
                return setChoices(state, "Which single task should this focus session be for?", available.take(8).map {
                    AssistantChoice(it.title, "focus on id:${it.id} for $minutes minutes")
                })
            }
            return apply(context, state, listOf(AssistantAction.StartFocus(minutes, selected.tasks.single().id)))
        }
        return null
    }

    private suspend fun focusStatus(context: Context, app: FocusApplication, state: AssistantSession): AyvaTalkEngine.TalkResponse {
        FocusSessionManager.refresh(context)
        state.switchTopic("focus")
        if (!FocusSessionManager.active.value) return reply("No focus session is running.")
        val remaining = FocusSessionManager.remainingSeconds.value
        val id = FocusSessionManager.taskId(context)
        val task = id?.let { app.database.taskDao().getTaskById(it) }
        val current = task?.takeIf { !it.isTrashed && it.deletedAt == null }
        val detail = when {
            id == null -> ""
            current == null -> " The linked task is no longer active."
            current.isCompleted -> " **${current.title}** is completed; the countdown is still running."
            else -> " You're focusing on **${current.title}**."
        }
        val actions = if (current != null && !current.isCompleted) listOf(suggestion(context, "complete id:${current.id}", "Mark task done")) else emptyList()
        return reply("Your focus session has **${remaining / 60}m ${remaining % 60}s** remaining.$detail", actions)
    }

    private suspend fun featureRequest(context: Context, app: FocusApplication, state: AssistantSession, text: String): AyvaTalkEngine.TalkResponse? {
        val lower = text.lowercase().trimEnd('.', '!', '?')
        if (OfflineNluEngine.isQuestion(text)) capabilityRequest(context, app, state, text, app.database.taskDao().getAllActiveTasksList())?.let { return it }
        AssistantLearningLanguage.parse(text, state)?.let { request ->
            return when (request) {
                is LearningRequest.Launch -> {
                    state.switchTopic("learning"); state.learning = request.configuration
                    AyvaTalkEngine.TalkResponse("Opening your practice window.", cliCommandRedirect = request.configuration.command())
                }
                is LearningRequest.Open -> { state.switchTopic("vocabulary"); AyvaTalkEngine.TalkResponse("Opening vocabulary.", cliCommandRedirect = request.command) }
                is LearningRequest.Reply -> { if (request.focusLearning) state.switchTopic("learning"); reply(request.text) }
            }
        }
        AssistantHabitLanguage.report(text, state)?.let { (target, count) ->
            return featureRequest(context, app, state, "log habit $target $count times")
        }
        if (Regex("^(?:i )?(?:just )?(?:drank|had|finished)\\b.*\\bwater$").matches(lower)) return reply("How many glasses should I log? Say ‘I drank two glasses of water’. I won't convert litres or minutes into habit counts.")
        if (lower in setOf("routine", "routines", "my routines", "list routines", "show routines", "active routines", "which routine is active")) {
            state.clearPending(); state.switchTopic("routines")
            state.featureState?.remove("routineRef")
            val schedules = app.database.scheduleDao().getAllSchedulesSync()
            return reply(if (schedules.isEmpty()) "You haven't created any routines. Open schedules to configure their time windows and app restrictions." else schedules.joinToString("\n") {
                "• **${it.name}** — ${if (it.isActiveAt()) "active now" else if (it.isEnabled) "enabled for its scheduled window" else "disabled"} (${String.format(java.util.Locale.US, "%02d:%02d–%02d:%02d", it.startHour, it.startMinute, it.endHour, it.endMinute)})"
            }, schedules.take(3).map { TalkAction.AskQuery("${if (it.isEnabled) "disable" else "enable"} routine id:${it.id}", "${if (it.isEnabled) "Disable" else "Enable"} ${it.name}") })
        }
        if (Regex("^(?:start |begin |set )?(?:a )?(?:focus(?: session| sprint)?|pomodoro|timer)(?: for| )?\\s*(?:\\d.*)?$").matches(lower) || lower in setOf("start focus", "start pomodoro", "start timer")) {
            val minutes = durationMinutes(lower) ?: if (!Regex("\\d").containsMatchIn(lower)) AssistantPersonalization.focusMinutes(context) else return reply("Choose a focus duration from 1–240 minutes, such as ‘start focus for 25 minutes’.")
            return apply(context, state, listOf(AssistantAction.StartFocus(minutes)))
        }
        if (lower in setOf("stop focus", "stop focus session", "end focus", "stop timer", "cancel timer", "end session")) {
            FocusSessionManager.refresh(context)
            if (!FocusSessionManager.active.value) return reply("No focus session is running.")
            return propose(context, app, state, listOf(AssistantAction.StopFocus), "Stop the current focus countdown?")
        }
        if (lower in setOf("timer status", "focus timer", "how much time is left", "how long is left", "time remaining", "is my timer running")) {
            FocusSessionManager.refresh(context)
            return focusStatus(context, app, state)
        }
        Regex("(?i)^(?:add|create|new|track)\\s+(?:a\\s+)?habit(?:\\s+(.+))?$").matchEntire(text)?.let {
            val content = it.groupValues[1].trim()
            if (content.isBlank()) { state.clearPending(); state.switchTopic("habits"); state.expected = "habit_title"; return reply("What habit would you like to track?") }
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
            if (matched.size != 1) { state.switchTopic("habits"); return setChoices(state, "Which habit would you like to log?", (matched.ifEmpty { habits }).take(8).map { AssistantChoice(it.title, actionCommand(AssistantAction.LogHabit(it.id, count))) }) }
            return apply(context, state, listOf(AssistantAction.LogHabit(matched.single().id, count)))
        }
        if (lower in setOf("habit", "habits", "my habits", "show habits", "habit progress", "how are my habits")) {
            state.clearPending(); state.switchTopic("habits")
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
            if (matches.size != 1) { state.switchTopic("routines"); return setChoices(state, "Which routine? Enabling uses its saved schedule.", matches.ifEmpty { schedules }.take(8).map { AssistantChoice(it.name, actionCommand(AssistantAction.SetRoutine(it.id, enabled))) }) }
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
                    state.clearPending(); state.switchTopic("setting:${spec.key}"); state.expected = "setting_value"; state.pending = listOf(AssistantAction.SetPreference(spec.key, spec.type, ""))
                    return reply("${parsedValue.exceptionOrNull()?.message?.let { "$it. " }.orEmpty()}What should ${spec.title} be? ${settingHint(spec)}")
                }
                return propose(context, app, state, listOf(AssistantAction.SetPreference(spec.key, spec.type, value)), "Set ${spec.title} to **$value ${spec.unit.takeUnless { it == "clock" }.orEmpty()}**?")
            }
            if (Regex("(?i)^(?:what(?:'s| is) my|show|check|current|how long|how often)\\b").containsMatchIn(text)) {
                state.switchTopic("setting:${spec.key}")
                return reply("${spec.title} is **${AssistantPreferences.read(context, spec)} ${spec.unit.takeUnless { it == "clock" }.orEmpty()}**. You can say ‘change ${spec.aliases.first()}’." )
            }
        }
        return null
    }

    private suspend fun featureTurn(context: Context, app: FocusApplication, state: AssistantSession, turn: FeatureTurn): AyvaTalkEngine.TalkResponse = when (turn) {
        is FeatureTurn.Reply -> if (turn.redirect != null) AyvaTalkEngine.TalkResponse(turn.text, cliCommandRedirect = turn.redirect) else {
            val response = reply(turn.text, turn.actions, turn.topicId ?: "conversation")
            if (turn.settingKey == null) response else AssistantResponseContent.setting(response, turn.settingKey)
        }
        is FeatureTurn.Preview -> propose(context, app, state, turn.actions, turn.text)
    }

    /** Only finalized audio enters this bounded, encrypted context; it never executes an action. */
    suspend fun recordTranscript(context: Context, text: String, timestamp: Long) = mutex.withLock {
        if (System.currentTimeMillis() - timestamp !in 0..900_000L) return@withLock
        val state = AssistantSessionStore(context).load()
        if (text.isBlank() || text.length > 4000) { state.featureState?.remove("transcript"); state.featureState?.remove("transcriptDrafts"); AssistantSessionStore(context).save(state); return@withLock }
        if (state.featureState == null) state.featureState = JSONObject()
        state.featureState!!.put("transcript", JSONObject().put("text", text).put("at", timestamp))
        state.featureState!!.remove("transcriptDrafts")
        AssistantSessionStore(context).save(state)
    }

    suspend fun recordVocabularyCard(context: Context, json: String, timestamp: Long, cardId: String? = null) = mutex.withLock {
        val app = context.applicationContext as? FocusApplication ?: return@withLock
        val state = AssistantSessionStore(context).load()
        if (json.length > 50000 || System.currentTimeMillis() - timestamp !in 0..900_000L) return@withLock
        if (cardId != null) {
            if (cardId.isBlank() || cardId.length > 120 || state.vocabularyCardId == cardId) return@withLock
            state.vocabularyCardId = cardId
        }
        if (timestamp >= (state.learning?.startedAt ?: 0L)) AssistantVocabularyFeatures.recordCard(app, state, json, timestamp)
        AssistantSessionStore(context).save(state)
    }

    private suspend fun propose(context: Context, app: FocusApplication, state: AssistantSession, actions: List<AssistantAction>, text: String): AyvaTalkEngine.TalkResponse {
        for (action in actions.filterIsInstance<AssistantAction.SetRoutine>().filter { !it.enabled }) {
            val schedule = app.database.scheduleDao().getScheduleById(action.id) ?: return reply("That routine no longer exists.")
            if (!AssistantActionExecutor.canDisableRoutine(context, schedule)) return reply("An active strict routine or focus session cannot be stopped from chat.")
        }
        if (actions.isNotEmpty() && actions.all { it is AssistantAction.SetRoutine }) state.switchTopic("routines")
        when (val action = actions.singleOrNull()) {
            is AssistantAction.SetPreference -> state.switchTopic("setting:${action.key}")
            is AssistantAction.SetRoutine -> {
                state.switchTopic("routines")
                app.database.scheduleDao().getScheduleById(action.id)?.let { AssistantRoutineConversation.remember(state, it) }
            }
            is AssistantAction.SaveRoutine -> {
                state.switchTopic("routines")
                if (action.expected != null) AssistantRoutineConversation.remember(state, action.expected) else state.featureState?.remove("routineRef")
            }
            is AssistantAction.SaveDictionaryWord -> state.switchTopic("dictionary")
            is AssistantAction.BookmarkWord -> state.switchTopic("vocabulary")
            is AssistantAction.SetAppRule -> { state.featureState?.remove("appSelection"); state.switchTopic("blocking") }
            is AssistantAction.SetAppRules -> state.switchTopic("blocking")
            is AssistantAction.CreateHabit, is AssistantAction.LogHabit -> state.switchTopic("habits")
            is AssistantAction.EditHabit -> { state.switchTopic("habits"); state.habitId = action.id }
            AssistantAction.StopFocus -> state.switchTopic("focus")
            else -> Unit
        }
        if (actions.size > 1 && actions.any { it is AssistantAction.SetRoutine || it is AssistantAction.SaveRoutine }) state.featureState?.remove("routineRef")
        state.clearPending(); state.expected = "confirm"; state.confirmationId = token(); state.confirmationAt = System.currentTimeMillis(); state.pending = actions
        val ids = actions.flatMap { when (it) { is AssistantAction.EditTasks -> it.ids; is AssistantAction.CompleteTasks -> it.ids; is AssistantAction.TrashTasks -> it.ids; else -> emptyList() } }.distinct()
        state.versions = ids.associateWith { app.database.taskDao().getTaskById(it)?.updatedAt ?: -1 }
        state.guards = ids.associate { taskId -> "task:$taskId" to (app.database.taskDao().getTaskById(taskId)?.let { TaskJson.encode(it).toString() } ?: "missing") } + actions.mapNotNull { action -> when (action) {
            is AssistantAction.SetAppRules -> "apps.inventory" to AssistantAppControl.inventory(context)
            is AssistantAction.SetPreference -> "pref:${action.key}" to AssistantPreferences.read(context, AssistantPreferences.specs.first { it.key == action.key })
            is AssistantAction.SetRoutine -> "routine:${action.id}" to (app.database.scheduleDao().getScheduleById(action.id)?.let { RoutineJson.encode(it).toString() } ?: "missing")
            AssistantAction.StopFocus -> "focus" to focusReceipt(context)
            is AssistantAction.EditHabit -> "habit:${action.id}" to HabitJson.encode(action.expected).toString()
            else -> null
        } }.toMap()
        return reply(text, confirmationActions(context, state), "confirmation")
    }

    private suspend fun confirm(context: Context, app: FocusApplication, state: AssistantSession, id: String): AyvaTalkEngine.TalkResponse {
        if (state.expected != "confirm" || state.confirmationId != id || !AssistantContext.live(state.confirmationAt)) return reply("That confirmation has expired or was already handled. Please send the request again.")
        for ((taskId, version) in state.versions) require(app.database.taskDao().getTaskById(taskId)?.updatedAt == version) { "A selected task changed. Please review the request again" }
        for ((key, before) in state.guards) {
            val current = when {
                key.startsWith("task:") -> app.database.taskDao().getTaskById(key.removePrefix("task:").toLong())?.let { TaskJson.encode(it).toString() } ?: "missing"
                key.startsWith("pref:") -> AssistantPreferences.read(context, AssistantPreferences.specs.first { it.key == key.removePrefix("pref:") })
                key.startsWith("routine:") -> app.database.scheduleDao().getScheduleById(key.removePrefix("routine:").toInt())?.let { RoutineJson.encode(it).toString() } ?: "missing"
                key == "apps.inventory" -> AssistantAppControl.inventory(context)
                key == "focus" -> focusReceipt(context)
                key.startsWith("habit:") -> app.database.habitDao().getHabitById(key.removePrefix("habit:").toLong())?.let { HabitJson.encode(it).toString() }
                else -> error("Unknown action guard")
            }
            require(current == before) { "The selected item, setting or session changed. Please review the request again" }
        }
        return apply(context, state, state.pending.toList(), id, reviewed = true)
    }

    private suspend fun apply(context: Context, state: AssistantSession, actions: List<AssistantAction>, receipt: String = token(), reviewed: Boolean = false): AyvaTalkEngine.TalkResponse {
        actions.forEach(AssistantCapabilities::validate)
        if (!reviewed && actions.any(AssistantCapabilities::requiresReview)) {
            val app = context.applicationContext as FocusApplication
            val details = actions.map { AssistantConversationSupport.describe(context, app, it) }
            return propose(context, app, state, actions, "Review this change?\n" + details.joinToString("\n") { "• $it" })
        }
        if (receipt in state.acknowledgedActionIds) return reply("That action was already applied.")
        val writeGuards = state.guards.toMutableMap()
        state.clearPending()
        state.acknowledgedActionIds = (state.acknowledgedActionIds + receipt).takeLast(30)
        // Consume a confirmation before mutation so a stale/repeated button cannot replay it.
        AssistantSessionStore(context).save(state)
        val results = mutableListOf<String>()
        var partialFailure = false
        val appliedTaskIds = mutableListOf<Long>()
        val taskBatch = actions.all { it is AssistantAction.CreateTask || it is AssistantAction.EditTasks || it is AssistantAction.CompleteTasks || it is AssistantAction.TrashTasks }
        state.undo = emptyList(); state.undoId = null
        // One reviewed Trash group needs one recovery snapshot and one database
        // transaction, even when the parser supplied one action per selected row.
        val executionActions = if (actions.size > 1 && actions.all { it is AssistantAction.TrashTasks }) {
            listOf(AssistantAction.TrashTasks(actions.filterIsInstance<AssistantAction.TrashTasks>().flatMap { it.ids }.distinct()))
        } else actions
        for (action in executionActions) {
            try {
                val result = AssistantActionExecutor.execute(context, action, writeGuards)
                result.undo.filter { it.optString("kind") == "task" }.forEach { record ->
                    val after = record.getJSONObject("after")
                    writeGuards["task:${after.getLong("id")}"] = after.toString()
                }
                if (action is AssistantAction.SetAppRules) AssistantAppControl.applied(state)
                results.add(result.text)
                if (taskBatch) appliedTaskIds += when (action) {
                    is AssistantAction.EditTasks -> action.ids
                    is AssistantAction.CompleteTasks -> action.ids
                    is AssistantAction.TrashTasks -> action.ids
                    else -> listOfNotNull(result.taskId)
                }
                result.taskId?.let { state.focusTask(it) }
                result.habitId?.let { state.switchTopic("habits"); state.habitId = it }
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
                    is AssistantAction.SetPreference -> state.switchTopic("setting:${action.key}")
                    is AssistantAction.SetRoutine, is AssistantAction.SaveRoutine -> {
                        state.switchTopic("routines")
                        val schedules = (context.applicationContext as FocusApplication).database.scheduleDao()
                        val routine = if (action is AssistantAction.SetRoutine) schedules.getScheduleById(action.id)
                            else schedules.getAllSchedulesSync().singleOrNull { it.name == (action as AssistantAction.SaveRoutine).draft.name }
                        if (actions.size == 1 && routine != null) AssistantRoutineConversation.remember(state, routine) else state.featureState?.remove("routineRef")
                    }
            is AssistantAction.SaveDictionaryWord -> state.switchTopic("dictionary")
            is AssistantAction.BookmarkWord -> state.switchTopic("vocabulary")
            is AssistantAction.SetAppRule -> { state.featureState?.remove("appSelection"); state.switchTopic("blocking") }
            is AssistantAction.SetAppRules -> state.switchTopic("blocking")
                    is AssistantAction.StartFocus, AssistantAction.StopFocus -> state.switchTopic("focus")
                    else -> Unit
                }
                AssistantSessionStore(context).save(state)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                partialFailure = true
                results.add("Couldn't apply the next action: ${e.message}. Remaining actions were not run.")
                break
            }
        }
        if (taskBatch && appliedTaskIds.isNotEmpty()) state.focusTasks(appliedTaskIds)
        val actionsOut = mutableListOf<TalkAction>()
        state.undoId?.let { actionsOut.add(TalkAction.AskQuery("/ayva undo $it", if (state.undo.size > 1) "Undo task changes" else "Undo last change", "↶")) }
        if (state.taskId != null) actionsOut.add(TalkAction.AskQuery("reschedule id:${state.taskId}", "Change time", "⏰"))
        val result = reply(results.joinToString("\n"), actionsOut)
        return AssistantPresentation.attach(result, itemReference(context, state), AssistantPresentation.taskReceipt(state.undo, partialFailure))
    }

    private suspend fun undo(context: Context, state: AssistantSession, id: String?): AyvaTalkEngine.TalkResponse {
        if (id == null || id != state.undoId || state.undo.isEmpty()) return reply("There's no recent action available to undo.")
        val record = state.undo.singleOrNull()?.takeIf { it.optString("kind") == "task" }
        val restored = record?.let { it.optJSONObject("before") ?: it.optJSONObject("after") }?.let(TaskJson::decode)
        val planStore = runCatching { ConversationPlanStore(context) }.getOrNull()
        val owner = planStore?.all()?.singleOrNull { it.savedUndoId == id }
        if (owner != null && SavedPlanHistory.canUndo(context, owner.id)) {
            val message = SavedPlanHistory.move(context, owner.id, redo = false)
            state.undo = emptyList(); state.undoId = null; state.clearPending()
            return reply(message)
        }
        val text = AssistantActionExecutor.undo(context, state.undo)
        planStore?.reconcileUndo(id, state.undo)
        if (state.undo.any { it.optString("kind") == "app_rules" }) state.featureState?.remove("appSelection")
        state.undo = emptyList(); state.undoId = null; state.clearPending()
        return AssistantPresentation.attach(reply(text), restored?.let { AssistantItemReference("task", it.id, it.title, "Restored · ${AssistantPresentation.due(it.dueDate)}") })
    }

    private suspend fun internalCommand(command: String, context: Context, app: FocusApplication, state: AssistantSession): AyvaTalkEngine.TalkResponse {
        if (command.startsWith("suggestion ")) return AssistantInitiative.dismiss(context, state, "/ayva $command") ?: reply("That suggestion is no longer active.")
        if (command.startsWith("social ")) return AssistantSocial.answer(context, state, "/ayva $command") ?: reply("That conversation choice expired.")
        if (command.startsWith("dictionary ")) return AssistantDictionary.answer(context, app, state, "/ayva $command")?.let { featureTurn(context, app, state, it) } ?: reply("That dictionary action is unavailable.")
        if (command.startsWith("plan ") || command.startsWith("plan-save ")) return AssistantGuidedPlanning.answer(context, state, "/ayva $command") ?: reply("That planning action is unavailable.")
        val parts = command.split(Regex("\\s+"))
        return when (parts.firstOrNull()) {
            "confirm" -> confirm(context, app, state, parts.getOrNull(1).orEmpty())
            "undo" -> undo(context, state, parts.getOrNull(1))
            "cancel" -> if (parts.getOrNull(1) == state.confirmationId) { if (state.pending.any { it is AssistantAction.SetAppRules }) state.featureState?.remove("appSelection"); state.clearPending(); reply("Cancelled.") } else reply("That request is no longer pending.")
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
        if (choice.command.startsWith("habit_request:")) {
            val json = JSONObject(choice.command.removePrefix("habit_request:"))
            return handleHabitEdit(context, app, state, HabitEditRequest("id:${json.getLong("id")}", HabitField.valueOf(json.getString("field")),
                if (json.isNull("value")) null else json.getString("value")))
        }
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
        state.clearPending(); state.expected = "choice"; state.confirmationId = token(); state.confirmationAt = System.currentTimeMillis(); state.choices = choices
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
        if (selectedIds.size == choices.size) { state.switchTopic("tasks"); state.rememberTaskList(selectedIds) }
        return choiceReply(prompt, state)
    }
    private fun choiceReply(prompt: String, state: AssistantSession) = reply(prompt + "\n" + state.choices.mapIndexed { i, c -> "${i + 1}. ${c.label}" }.joinToString("\n") + "\nYou can reply with a number or tap a choice.", state.choices.mapIndexed { i, c -> TalkAction.AskQuery("/ayva choose ${state.confirmationId} $i", c.label) }, "choice")
    private fun choiceIndex(input: String, size: Int): Int? {
        val cleaned = input.replace(Regex("^(?:no[, ]+|not that one[, ]+|i meant )"), "").removePrefix("the ").removeSuffix(" one").removeSuffix(" option").removePrefix("option ").removePrefix("number ")
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
    private fun correctionPreview(actions: List<AssistantAction>) = "Correct this same task?\n" + actions.filterIsInstance<AssistantAction.EditTasks>().joinToString("\n") {
        if (it.field == TaskField.DUE) "• Due: ${SmartDateParser.formatDueDate(it.value.toLong())}" else "• Title: ${it.value}"
    }
    private fun expectedHint(state: AssistantSession) = AssistantReplyStyle.expectedHint(state)
    private fun isNewIntent(text: String) = AssistantFeatureLanguage.isRequest(text) || AssistantPlanning.isDirectRequest(text) || OfflineNluEngine.isConversationalStatement(text) || OfflineNluEngine.isExplicitCreation(text) || OfflineNluEngine.isQuestion(text) || Regex("^(?:add|create|new|start|stop|delete|complete|reschedule|show|list|set|change|rename|archive|restore|unarchive|enable|disable|focus|log|undo|help|practice|teach me|give me|learn|drill|vocab|vocabulary|blitz)\\b").containsMatchIn(text) || Regex("^(?:i )?(?:just )?(?:drank|had|finished)\\b.*\\bwater$").matches(text)
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
        state.switchTopic("tasks")
        state.rememberTaskList(list.sortedWith(compareByDescending<Task> { it.isPriority }.thenBy { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.id }).map { it.id })
    }

    private suspend fun dialogue(context: Context, app: FocusApplication, state: AssistantSession, input: String, tasks: List<Task>): AyvaTalkEngine.TalkResponse? = AssistantDialogue.answer(context, app, state, input, tasks)
    private fun knowledgeFollowUp(context: Context, state: AssistantSession, input: String): AyvaTalkEngine.TalkResponse? = AssistantDialogue.knowledgeFollowUp(context, state, input)

    private suspend fun compound(context: Context, app: FocusApplication, state: AssistantSession, parts: List<String>, priority: Boolean, persistent: Boolean): AyvaTalkEngine.TalkResponse {
        if (parts.size > 5) return reply("Please send at most five actions at a time. Nothing has been changed.")
        val planned = mutableListOf<AssistantAction>()
        val tasks = app.database.taskDao().getAllActiveTasksList().filter { !it.isCompleted }
        for (part in parts) {
            if (AssistantFeatureLanguage.isRequest(part)) return reply("Send feature commands separately so I can collect their settings safely. Nothing in this batch has changed.")
            val request = AssistantTaskLanguage.request(part.replaceFirst(Regex("(?i)^actually[, ]+"), ""))
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
