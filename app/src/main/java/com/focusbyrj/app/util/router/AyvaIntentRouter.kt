/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.util.router

import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.ConflictOption
import com.focusbyrj.app.util.NluIntent
import com.focusbyrj.app.util.OfflineNluEngine
import com.focusbyrj.app.util.SmartDateParser
import com.focusbyrj.app.util.ChatTaskDraft

sealed class RouterDestination {
    data class DirectCommand(val command: String, val executeImmediately: Boolean = true) : RouterDestination()
    data class TaskCreation(val title: String, val dueDate: Long? = null, val isPriority: Boolean = false) : RouterDestination()
    data class ConflictCard(val prompt: String, val options: List<ConflictOption>) : RouterDestination()
    data class TalkQuery(val query: String) : RouterDestination()
}

object AyvaIntentRouter {

    /**
     * Resolves user input into a target execution destination adhering strictly to the
     * Task Creation Priority & Conflict Arbitration Matrix.
     *
     * Rule: Creating tasks takes priority over any function interpretation. If there is
     * ambiguity between creating a task and executing a feature, Ayva asks via an interactive
     * conflict card with Task Creation prominently listed as Option 1.
     */
    fun route(
        input: String,
        pendingTasks: List<Task> = emptyList(),
        knownHabits: List<String> = emptyList(),
        knownRoutines: List<String> = emptyList()
    ): RouterDestination {
        val trimmed = OfflineNluEngine.normalizeRequest(input)
        // Sanitize newlines from user input before embedding in ConflictOption commands.
        // A raw "\n/clear" in trimmed would be split by the compound command handler as two commands (BATCH-9-P2-011).
        val safeTrimmed = trimmed.replace(Regex("[\r\n]+"), " ").trim()
        val lower = trimmed.lowercase()
        if (trimmed.isBlank()) return RouterDestination.TalkQuery(trimmed)

        // 1. Direct Slash Commands
        if (trimmed.startsWith("/")) {
            return handleSlashCommand(trimmed)
        }

        if (com.focusbyrj.app.util.assistant.AssistantLanguage.actionGuard(trimmed) != null) return RouterDestination.TalkQuery(trimmed)

        if (lower in setOf("task", "tasks", "todo", "todos", "todo list", "my tasks", "task list")) {
            return RouterDestination.DirectCommand("/tasks")
        }

        // 2. Explicit Task Creation Syntax (e.g. "add buy groceries tomorrow", "remind me to call mom 5pm")
        if (OfflineNluEngine.isExplicitCreation(trimmed)) {
            val draft = ChatTaskDraft.parse(trimmed) ?: return RouterDestination.TalkQuery("help adding a task")
            val title = draft.title
            val dueDate = draft.dueDate
            // Check for duplicate active task
            val duplicate = findDuplicateTask(title, pendingTasks)
            if (duplicate != null) {
                return RouterDestination.ConflictCard(
                    prompt = "📋 **Existing Task Found:** You already have active task **'${duplicate.title}'**.\nWould you like to add this as a separate task, complete the existing one, or reschedule it?",
                    options = listOf(
                        ConflictOption(
                            label = "Add as New Task",
                            emoji = "➕",
                            command = "/create $safeTrimmed"
                        ),
                        ConflictOption(
                            label = "Mark Existing Complete",
                            emoji = "✅",
                            command = "/talk complete id:${duplicate.id}"
                        ),
                        ConflictOption(
                            label = "Reschedule Existing",
                            emoji = "⏰",
                            command = "/talk reschedule id:${duplicate.id}${if (dueDate != null) " ${SmartDateParser.formatDueDate(dueDate)}" else ""}"
                        )
                    )
                )
            }
            return RouterDestination.TaskCreation(title = title, dueDate = dueDate)
        }

        // 2.5 Conversational Expressions (e.g. "I am feeling lazy today", "I feel tired")
        val conversationalPrefixes = listOf("i am ", "i'm ", "i feel ", "feeling ", "today is ", "it is ", "it's ", "how is ", "what is ")
        if (OfflineNluEngine.isConversationalStatement(trimmed) || conversationalPrefixes.any { lower.startsWith(it) }) {
            return RouterDestination.TalkQuery(trimmed)
        }

        // 3. Questions (e.g. "How does arithmetic drill work?", "What to buy for dinner?")
        if (OfflineNluEngine.isQuestion(trimmed)) {
            // Check if question looks like an actionable todo item (e.g. "What to study tonight", "How to prepare speech")
            val isActionableQuestion = (lower.startsWith("what to ") || lower.startsWith("how to ") || lower.startsWith("where to ")) &&
                    trimmed.split(" ").size >= 3
            if (isActionableQuestion) {
                val cleanTitle = trimmed.removeSuffix("?").trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                return RouterDestination.ConflictCard(
                    prompt = "🤔 **Intent Clarification:** Would you like to create a Todo task for **'$cleanTitle'** or ask Ayva?",
                    options = listOf(
                        ConflictOption(
                            label = "Add as Todo Task",
                            emoji = "➕",
                            command = "/create $cleanTitle"
                        ),
                        ConflictOption(
                            label = "Ask Ayva",
                            emoji = "💡",
                            command = "/talk $safeTrimmed"
                        )
                    )
                )
            }
            return RouterDestination.TalkQuery(trimmed)
        }

        // Explicit feature requests should execute their feature, not offer to create a task.
        val featureCommand = when (lower.trimEnd('?', '!')) {
            "tasks", "my tasks", "todo", "todos", "todo list", "show tasks", "list tasks" -> "/tasks"
            "all tasks", "show all tasks", "list all tasks" -> "/tasks all"
            "summary", "daily summary", "briefing", "my day", "recap" -> "/summary"
            "profile", "my profile", "stats", "xp" -> "/profile"
            "habit", "habits", "my habits", "show habits", "open habits" -> "/habits"
            "routine", "routines", "my routines", "show routines", "list routines" -> "/routines"
            "drill", "math", "quiz", "arithmetic", "start drill", "start math drill" -> "/drill"
            "daily quests", "quests", "open chest", "mystery box" -> "/quests"
            else -> null
        }
        if (featureCommand != null) return RouterDestination.DirectCommand(featureCommand)
        if (Regex("^(?:start|stop|enable|disable|pause|resume)\\s+.*\\broutine\\b").containsMatchIn(lower) ||
            Regex("^(?:start|set)\\s+(?:a\\s+)?(?:timer|focus|pomodoro)\\b").containsMatchIn(lower) ||
            Regex("^(?:check|get|read)\\s+(?:(?:my|the)\\s+)?(?:permissions?|settings?|status|screen time|usage|notes?)\\b").containsMatchIn(lower) ||
            Regex("^(?:add|create|new|log)\\s+(?:(?:a|an)\\s+)?(?:habit|note)s?\\b").containsMatchIn(lower) ||
            Regex("^(?:set|change|turn|enable|disable|toggle|freeze|unfreeze)\\b").containsMatchIn(lower) ||
            lower.startsWith("reschedule id:") || lower.startsWith("complete id:") || lower.startsWith("delete id:")) {
            return RouterDestination.DirectCommand("/talk $safeTrimmed")
        }

        val assistantTopics = listOf("help", "guide", "commands", "features", "options", "settings", "permissions", "notes", "my notes", "open notes", "status", "focus status", "advice", "tips", "motivate me", "joke", "tell me a joke", "breathe", "breathing", "screen time", "screentime", "vacation mode")
        if (lower in assistantTopics ||
            Regex("\\b(?:tired|stressed|overwhelmed|exhausted|distracted|procrastinating|usage|permissions?|vacation)\\b").containsMatchIn(lower) ||
            Regex("^(?:log|complete|done|check|finished)\\s+habit\\b").containsMatchIn(lower) ||
            Regex("^(?:search|find|read|write|edit|delete)\\s+(?:my\\s+)?notes?\\b").containsMatchIn(lower)) {
            return RouterDestination.TalkQuery(trimmed)
        }

        // 4. Routine Ambiguity (e.g. "morning exercise", "evening study session", "bedtime reading")
        val routineKeywords = listOf("morning", "evening", "bedtime", "routine", "schedule")
        val containsRoutineWord = routineKeywords.any { containsPhrase(lower, it) }
        val isNotPureRoutineCommand = lower != "routine" && lower != "routines" && lower != "schedules" &&
                !lower.startsWith("start routine") && !lower.startsWith("stop routine")
        if (containsRoutineWord && isNotPureRoutineCommand && trimmed.split(" ").size >= 2) {
            val (title, dueDate) = OfflineNluEngine.extractTaskCreationDetails(trimmed)
            return RouterDestination.ConflictCard(
                prompt = "🌅 **Conflict Detected:** Did you mean to create a Todo task for **'$title'** or trigger a Routine?",
                options = listOf(
                    ConflictOption(
                        label = "Add as Todo Task: '$title'",
                        emoji = "➕",
                        command = "/create $safeTrimmed"
                    ),
                    ConflictOption(
                        label = "Open Routines",
                        emoji = "🔄",
                        command = "/routines"
                    )
                )
            )
        }

        // 5. Habit Ambiguity (e.g. "drink 2L water", "read 20 pages", "stretch for 10m")
        val habitKeywords = listOf("water", "drink", "hydrate", "read", "stretch", "meditate", "journal", "workout", "exercise", "walk")
        val matchesHabit = habitKeywords.any { containsPhrase(lower, it) } || (lower.contains("drink") && lower.contains("water")) || knownHabits.filter { it.isNotBlank() }.any { containsPhrase(lower, it.lowercase()) }
        if (matchesHabit && trimmed.split(" ").size >= 2 && !lower.startsWith("add habit") && !lower.startsWith("log habit")) {
            val (title, dueDate) = OfflineNluEngine.extractTaskCreationDetails(trimmed)
            return RouterDestination.ConflictCard(
                prompt = "💧 **Conflict Detected:** Did you mean to create a Todo task for **'$title'** or check your Habits?",
                options = listOf(
                    ConflictOption(
                        label = "Add as Todo Task: '$title'",
                        emoji = "➕",
                        command = "/create $safeTrimmed"
                    ),
                    ConflictOption(
                        label = "View Habits Hub",
                        emoji = "📋",
                        command = "/habits"
                    )
                )
            )
        }

        // 6. Focus Timer Ambiguity (e.g. "study for 2 hours", "focus 25m", "deep work session")
        val timerKeywords = listOf("focus", "deep work", "study session", "timer", "pomodoro")
        val matchesTimer = timerKeywords.any { containsPhrase(lower, it) } && !lower.startsWith("start timer") && !lower.startsWith("set timer")
        if (matchesTimer && trimmed.split(" ").size >= 2) {
            val (title, dueDate) = OfflineNluEngine.extractTaskCreationDetails(trimmed)
            return RouterDestination.ConflictCard(
                prompt = "⏳ **Conflict Detected:** Would you like to add **'$title'** to your Todo list or start a Focus Timer?",
                options = listOf(
                    ConflictOption(
                        label = "Add as Todo Task: '$title'",
                        emoji = "➕",
                        command = "/create $safeTrimmed"
                    ),
                    ConflictOption(
                        label = "Start Focus Mode",
                        emoji = "🎯",
                        command = "/focus 25"
                    )
                )
            )
        }

        // 7. Math Drill Ambiguity (e.g. "math practice chapter 4", "arithmetic homework", "quiz prep")
        val drillKeywords = listOf("math", "drill", "arithmetic", "calculate", "quiz")
        val matchesDrill = drillKeywords.any { containsPhrase(lower, it) } && lower != "drill" && lower != "math" && lower != "quiz" && lower != "arithmetic"
        if (matchesDrill && trimmed.split(" ").size >= 2) {
            val (title, dueDate) = OfflineNluEngine.extractTaskCreationDetails(trimmed)
            return RouterDestination.ConflictCard(
                prompt = "🧮 **Conflict Detected:** Would you like to create a Todo task for **'$title'** or launch an Arithmetic Drill?",
                options = listOf(
                    ConflictOption(
                        label = "Add as Todo Task: '$title'",
                        emoji = "➕",
                        command = "/create $safeTrimmed"
                    ),
                    ConflictOption(
                        label = "Start Math Drill",
                        emoji = "⚡",
                        command = "/drill"
                    )
                )
            )
        }

        // 8. App Block Ambiguity (e.g. "block instagram after 6pm", "limit youtube tonight")
        val blockKeywords = listOf("block", "lock", "restrict", "limit", "shield")
        val matchesBlock = blockKeywords.any { lower.startsWith(it) } && (lower.contains("after") || lower.contains("at") || lower.contains("tomorrow") || lower.contains("tonight"))
        if (matchesBlock) {
            val (title, dueDate) = OfflineNluEngine.extractTaskCreationDetails(trimmed)
            return RouterDestination.ConflictCard(
                prompt = "🔒 **Conflict Detected:** Did you mean to schedule a reminder task **'$title'** or enforce an instant app lock?",
                options = listOf(
                    ConflictOption(
                        label = "Add as Todo Task: '$title'",
                        emoji = "➕",
                        command = "/create $safeTrimmed"
                    ),
                    ConflictOption(
                        label = "Configure App Blocker",
                        emoji = "🛡️",
                        command = "/talk block"
                    )
                )
            )
        }

        // 9. Summary Ambiguity (e.g. "review presentation", "summary for meeting", "recap sales")
        val summaryKeywords = listOf("summary", "review", "recap", "briefing")
        val matchesSummary = summaryKeywords.any { containsPhrase(lower, it) } && lower != "summary" && lower != "recap" && lower != "briefing"
        if (matchesSummary && trimmed.split(" ").size >= 2) {
            val (title, dueDate) = OfflineNluEngine.extractTaskCreationDetails(trimmed)
            return RouterDestination.ConflictCard(
                prompt = "📊 **Conflict Detected:** Did you mean to create a task for **'$title'** or view your Daily Summary?",
                options = listOf(
                    ConflictOption(
                        label = "Add as Todo Task: '$title'",
                        emoji = "➕",
                        command = "/create $safeTrimmed"
                    ),
                    ConflictOption(
                        label = "View Daily Summary",
                        emoji = "📈",
                        command = "/summary"
                    )
                )
            )
        }

        // 10. Run through OfflineNluEngine
        val nluResult = OfflineNluEngine.parse(trimmed, pendingTasks)
        when (nluResult.intent) {
            NluIntent.CONFLICT -> {
                // Ensure Option 1 is ALWAYS the Task Creation option
                val reorderedOptions = prioritizeTaskOption(nluResult.conflictOptions)
                return RouterDestination.ConflictCard(
                    prompt = nluResult.conflictPrompt ?: "🤔 **Conflict Detected:** Please choose your intended action:",
                    options = reorderedOptions
                )
            }
            NluIntent.CREATE_TASK -> {
                val title = nluResult.createdTaskTitle ?: trimmed
                if (title.isBlank()) return RouterDestination.TalkQuery("help adding a task")
                return RouterDestination.TaskCreation(title = title, dueDate = nluResult.targetDateMs)
            }
            NluIntent.LIST_TASKS -> return RouterDestination.DirectCommand(if (nluResult.isAllTasks) "/tasks all" else "/tasks")
            NluIntent.SHOW_PROFILE -> return RouterDestination.DirectCommand("/profile")
            NluIntent.SHOW_SUMMARY -> return RouterDestination.DirectCommand("/summary")
            NluIntent.START_DRILL -> return RouterDestination.DirectCommand("/drill")
            NluIntent.CLEAR_CHAT -> return RouterDestination.DirectCommand("/clear")
            NluIntent.LIST_ROUTINES -> return RouterDestination.DirectCommand("/routines")
            NluIntent.BLOCK_APP, NluIntent.BLOCK_FILTER, NluIntent.UNBLOCK,
            NluIntent.START_ROUTINE, NluIntent.STOP_ROUTINE, NluIntent.COMPLETE,
            NluIntent.DELETE, NluIntent.RESCHEDULE -> return RouterDestination.DirectCommand("/talk $safeTrimmed")
            NluIntent.UNKNOWN -> {
                val conversationalPrefixes = listOf("i am ", "i'm ", "i feel ", "feeling ", "today is ", "it is ", "it's ", "how is ", "what is ")
                val isConversationalStatement = conversationalPrefixes.any { lower.startsWith(it) }
                if (isConversationalStatement) {
                    return RouterDestination.TalkQuery(trimmed)
                }

                // Check if date parser extracted a valid time entity (e.g. "meeting with bob 4pm")
                val parsed = SmartDateParser.parse(trimmed)
                if (parsed.timestamp != null && parsed.hasTaskText) {
                    return RouterDestination.TaskCreation(title = parsed.cleanText, dueDate = parsed.timestamp)
                }
                // Pure conversation / FAQ / personality query - send to Talk Engine (no phantom tasks!)
                if (parsed.timestamp != null || trimmed.split(Regex("\\s+")).size > 12) {
                    return RouterDestination.TalkQuery(trimmed)
                }
                // Preserve quick capture for bare titles, but require one tap for ambiguous text.
                return RouterDestination.ConflictCard(
                    "Would you like to add **'$safeTrimmed'** as a task, or talk about it?",
                    listOf(
                        ConflictOption("Add as Todo Task", "➕", "/create $safeTrimmed"),
                        ConflictOption("Ask Ayva", "💬", "/talk $safeTrimmed")
                    )
                )
            }
        }
    }

    private fun containsPhrase(text: String, phrase: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(phrase)}(?![\\p{L}\\p{N}])").containsMatchIn(text)

    private fun handleSlashCommand(commandText: String): RouterDestination {
        val parts = commandText.split(Regex("\\s+"))
        val cmd = parts[0].lowercase()
        return when (cmd) {
            "/task", "/create", "/add", "/todo" -> {
                val rest = parts.drop(1).joinToString(" ").trim()
                if (cmd == "/task" && rest.lowercase() in setOf("", "all", "today", "list", "pending", "everything")) {
                    return RouterDestination.DirectCommand(commandText)
                }
                val draft = ChatTaskDraft.parse(rest)
                    ?: return RouterDestination.TalkQuery("help adding a task")
                RouterDestination.TaskCreation(draft.title, draft.dueDate)
            }
            "/habit", "/habits" -> if (parts.size > 1) {
                RouterDestination.DirectCommand("/talk ${com.focusbyrj.app.util.command.AyvaChatCommands.talkQuery(commandText)}")
            } else RouterDestination.DirectCommand(commandText)
            else -> RouterDestination.DirectCommand(commandText)
        }
    }

    private fun findDuplicateTask(queryTitle: String, tasks: List<Task>): Task? {
        val lower = queryTitle.lowercase().trim()
        return tasks.find {
            val tLower = it.title.lowercase().trim()
            tLower == lower || (tLower.length > 5 && lower.length > 5 && OfflineNluEngine.levenshtein(tLower, lower) <= 2)
        }
    }

    private fun prioritizeTaskOption(options: List<ConflictOption>): List<ConflictOption> {
        val taskOptionIndex = options.indexOfFirst {
            it.label.contains("New Task", ignoreCase = true) ||
            it.label.contains("Create", ignoreCase = true) ||
            it.label.contains("Add", ignoreCase = true) ||
            it.command.startsWith("/create") ||
            it.command.startsWith("/task")
        }
        if (taskOptionIndex == 0) return options
        if (taskOptionIndex > 0) {
            val taskOpt = options[taskOptionIndex]
            val mutable = options.toMutableList()
            mutable.removeAt(taskOptionIndex)
            mutable.add(0, taskOpt)
            return mutable
        }
        // A destructive-action clarification is not an invitation to create a new task.
        return options
    }
}
