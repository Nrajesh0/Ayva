/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.util.router

import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.ConflictOption
import com.focusbyrj.app.util.NluIntent
import com.focusbyrj.app.util.OfflineNluEngine
import com.focusbyrj.app.util.SmartDateParser

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
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        // 1. Direct Slash Commands
        if (trimmed.startsWith("/")) {
            return handleSlashCommand(trimmed)
        }

        // 2. Explicit Task Creation Syntax (e.g. "add buy groceries tomorrow", "remind me to call mom 5pm")
        if (OfflineNluEngine.isExplicitCreation(trimmed)) {
            val (title, dueDate) = OfflineNluEngine.extractTaskCreationDetails(trimmed)
            // Check for duplicate active task
            val duplicate = findDuplicateTask(title, pendingTasks)
            if (duplicate != null) {
                return RouterDestination.ConflictCard(
                    prompt = "📋 **Existing Task Found:** You already have active task **'${duplicate.title}'**.\nWould you like to add this as a separate task, complete the existing one, or reschedule it?",
                    options = listOf(
                        ConflictOption(
                            label = "Add as New Task",
                            emoji = "➕",
                            command = "/create $trimmed"
                        ),
                        ConflictOption(
                            label = "Mark Existing Complete",
                            emoji = "✅",
                            command = "/talk complete ${duplicate.title}"
                        ),
                        ConflictOption(
                            label = "Reschedule Existing",
                            emoji = "⏰",
                            command = "/talk reschedule ${duplicate.title} tomorrow"
                        )
                    )
                )
            }
            return RouterDestination.TaskCreation(title = title, dueDate = dueDate)
        }

        // 2.5 Conversational Expressions (e.g. "I am feeling lazy today", "I feel tired")
        val conversationalPrefixes = listOf("i am ", "i'm ", "i feel ", "feeling ", "today is ", "it is ", "it's ", "how is ", "what is ")
        if (conversationalPrefixes.any { lower.startsWith(it) }) {
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
                            command = "/talk $trimmed"
                        )
                    )
                )
            }
            return RouterDestination.TalkQuery(trimmed)
        }

        // 4. Routine Ambiguity (e.g. "morning exercise", "evening study session", "bedtime reading")
        val routineKeywords = listOf("morning", "evening", "bedtime", "routine", "schedule")
        val containsRoutineWord = routineKeywords.any { lower.contains(it) }
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
                        command = "/create $trimmed"
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
        val matchesHabit = habitKeywords.any { lower.contains(it) } || (lower.contains("drink") && lower.contains("water")) || knownHabits.any { lower.contains(it.lowercase()) }
        if (matchesHabit && trimmed.split(" ").size >= 2 && !lower.startsWith("add habit") && !lower.startsWith("log habit")) {
            val (title, dueDate) = OfflineNluEngine.extractTaskCreationDetails(trimmed)
            return RouterDestination.ConflictCard(
                prompt = "💧 **Conflict Detected:** Did you mean to create a Todo task for **'$title'** or check your Habits?",
                options = listOf(
                    ConflictOption(
                        label = "Add as Todo Task: '$title'",
                        emoji = "➕",
                        command = "/create $trimmed"
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
        val matchesTimer = timerKeywords.any { lower.contains(it) } && !lower.startsWith("start timer") && !lower.startsWith("set timer")
        if (matchesTimer && trimmed.split(" ").size >= 2) {
            val (title, dueDate) = OfflineNluEngine.extractTaskCreationDetails(trimmed)
            return RouterDestination.ConflictCard(
                prompt = "⏳ **Conflict Detected:** Would you like to add **'$title'** to your Todo list or start a Focus Timer?",
                options = listOf(
                    ConflictOption(
                        label = "Add as Todo Task: '$title'",
                        emoji = "➕",
                        command = "/create $trimmed"
                    ),
                    ConflictOption(
                        label = "Start Focus Mode",
                        emoji = "🎯",
                        command = "/talk focus"
                    )
                )
            )
        }

        // 7. Math Drill Ambiguity (e.g. "math practice chapter 4", "arithmetic homework", "quiz prep")
        val drillKeywords = listOf("math", "drill", "arithmetic", "calculate", "quiz")
        val matchesDrill = drillKeywords.any { lower.contains(it) } && lower != "drill" && lower != "math" && lower != "quiz" && lower != "arithmetic"
        if (matchesDrill && trimmed.split(" ").size >= 2) {
            val (title, dueDate) = OfflineNluEngine.extractTaskCreationDetails(trimmed)
            return RouterDestination.ConflictCard(
                prompt = "🧮 **Conflict Detected:** Would you like to create a Todo task for **'$title'** or launch an Arithmetic Drill?",
                options = listOf(
                    ConflictOption(
                        label = "Add as Todo Task: '$title'",
                        emoji = "➕",
                        command = "/create $trimmed"
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
                        command = "/create $trimmed"
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
        val matchesSummary = summaryKeywords.any { lower.contains(it) } && lower != "summary" && lower != "recap" && lower != "briefing"
        if (matchesSummary && trimmed.split(" ").size >= 2) {
            val (title, dueDate) = OfflineNluEngine.extractTaskCreationDetails(trimmed)
            return RouterDestination.ConflictCard(
                prompt = "📊 **Conflict Detected:** Did you mean to create a task for **'$title'** or view your Daily Summary?",
                options = listOf(
                    ConflictOption(
                        label = "Add as Todo Task: '$title'",
                        emoji = "➕",
                        command = "/create $trimmed"
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
                val reorderedOptions = prioritizeTaskOption(trimmed, nluResult.conflictOptions)
                return RouterDestination.ConflictCard(
                    prompt = nluResult.conflictPrompt ?: "🤔 **Conflict Detected:** Please choose your intended action:",
                    options = reorderedOptions
                )
            }
            NluIntent.CREATE_TASK -> {
                val title = nluResult.createdTaskTitle ?: trimmed
                return RouterDestination.TaskCreation(title = title, dueDate = nluResult.targetDateMs)
            }
            NluIntent.LIST_TASKS -> return RouterDestination.DirectCommand("/tasks")
            NluIntent.SHOW_PROFILE -> return RouterDestination.DirectCommand("/profile")
            NluIntent.SHOW_SUMMARY -> return RouterDestination.DirectCommand("/summary")
            NluIntent.START_DRILL -> return RouterDestination.DirectCommand("/drill")
            NluIntent.CLEAR_CHAT -> return RouterDestination.DirectCommand("/clear")
            NluIntent.LIST_ROUTINES -> return RouterDestination.DirectCommand("/routines")
            NluIntent.BLOCK_APP -> return RouterDestination.DirectCommand("/block ${nluResult.targetFilterOrAppName ?: ""}".trim())
            NluIntent.BLOCK_FILTER -> return RouterDestination.DirectCommand("/block ${nluResult.targetFilterOrAppName ?: ""}".trim())
            NluIntent.UNBLOCK -> return RouterDestination.DirectCommand("/unblock ${nluResult.targetFilterOrAppName ?: ""}".trim())
            NluIntent.START_ROUTINE -> return RouterDestination.DirectCommand("/routine start ${nluResult.targetRoutineName ?: ""}".trim())
            NluIntent.STOP_ROUTINE -> return RouterDestination.DirectCommand("/routine stop ${nluResult.targetRoutineName ?: ""}".trim())
            NluIntent.COMPLETE -> {
                val task = nluResult.targetTask
                return if (task != null) {
                    RouterDestination.DirectCommand("/talk complete ${task.title}")
                } else {
                    RouterDestination.DirectCommand("/tasks")
                }
            }
            NluIntent.DELETE -> {
                val task = nluResult.targetTask
                return if (task != null) {
                    RouterDestination.DirectCommand("/talk delete ${task.title}")
                } else {
                    RouterDestination.DirectCommand("/tasks")
                }
            }
            NluIntent.RESCHEDULE -> {
                val task = nluResult.targetTask
                val dateStr = if (nluResult.targetDateMs != null) " ${SmartDateParser.formatDueDate(nluResult.targetDateMs)}" else ""
                return if (task != null) {
                    RouterDestination.DirectCommand("/talk reschedule ${task.title}$dateStr")
                } else {
                    RouterDestination.DirectCommand("/tasks")
                }
            }
            NluIntent.UNKNOWN -> {
                val conversationalPrefixes = listOf("i am ", "i'm ", "i feel ", "feeling ", "today is ", "it is ", "it's ", "how is ", "what is ")
                val isConversationalStatement = conversationalPrefixes.any { lower.startsWith(it) }
                if (isConversationalStatement) {
                    return RouterDestination.TalkQuery(trimmed)
                }

                // Check if date parser extracted a valid time entity (e.g. "meeting with bob 4pm")
                val parsed = SmartDateParser.parse(trimmed)
                if (parsed.timestamp != null && parsed.cleanText.isNotBlank()) {
                    return RouterDestination.TaskCreation(title = parsed.cleanText, dueDate = parsed.timestamp)
                }
                // Pure conversation / FAQ / personality query - send to Talk Engine (no phantom tasks!)
                return RouterDestination.TalkQuery(trimmed)
            }
        }
    }

    private fun handleSlashCommand(commandText: String): RouterDestination {
        val parts = commandText.split(Regex("\\s+"))
        val cmd = parts[0].lowercase()
        return when (cmd) {
            "/task", "/create" -> {
                val rest = parts.drop(1).joinToString(" ").trim()
                if (rest.isNotBlank()) {
                    val (title, dueDate) = if (OfflineNluEngine.isExplicitCreation(rest)) {
                        OfflineNluEngine.extractTaskCreationDetails(rest)
                    } else {
                        val parsed = SmartDateParser.parse(rest)
                        Pair(parsed.cleanText.ifBlank { rest }, parsed.timestamp)
                    }
                    RouterDestination.TaskCreation(title = title, dueDate = dueDate)
                } else {
                    RouterDestination.DirectCommand("/task")
                }
            }
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

    private fun prioritizeTaskOption(rawInput: String, options: List<ConflictOption>): List<ConflictOption> {
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
        // If no task option was present, inject it as Option 1!
        val injected = ConflictOption(
            label = "Add as Todo Task",
            emoji = "➕",
            command = "/create $rawInput"
        )
        return listOf(injected) + options
    }
}
