package com.focusbyrj.app.util

import com.focusbyrj.app.data.Task
import java.util.Calendar

enum class NluIntent {
    CREATE_TASK, RESCHEDULE, COMPLETE, DELETE, LIST_TASKS, BLOCK_APP, BLOCK_FILTER, UNBLOCK, LIST_ROUTINES, START_ROUTINE, STOP_ROUTINE, START_DRILL, SHOW_PROFILE, SHOW_SUMMARY, CLEAR_CHAT, CONFLICT, UNKNOWN
}

data class ConflictOption(
    val label: String,
    val emoji: String,
    val command: String
)

data class TargetTaskResult(
    val targetTask: Task? = null,
    val isAll: Boolean = false,
    val candidateTasks: List<Task> = emptyList(),
    val extractedKeyword: String? = null
)

data class NluParsedResult(
    val intent: NluIntent,
    val targetTask: Task? = null,
    val isAllTasks: Boolean = false,
    val targetDateMs: Long? = null,
    val blockMode: String? = null,
    val targetFilterOrAppName: String? = null,
    val targetRoutineName: String? = null,
    val matchingTasks: List<Task> = emptyList(),
    val filterQuery: String? = null,
    val hasExplicitTimeSpecified: Boolean = false,
    val createdTaskTitle: String? = null,
    val conflictPrompt: String? = null,
    val conflictOptions: List<ConflictOption> = emptyList()
)

object OfflineNluEngine {

    // 1. Levenshtein Distance for fuzzy typo matching
    fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1)
        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }

    // 2. Fuzzy Token Matcher
    private fun matchesAnyFuzzy(tokens: List<String>, targets: List<String>, maxDist: Int = 1): Boolean {
        for (token in tokens) {
            for (target in targets) {
                if (token.length > 4 && target.length > 4) {
                    if (levenshtein(token, target) <= maxDist) return true
                } else {
                    if (token == target) return true
                }
            }
        }
        return false
    }

    // 3. Explicit Task Creation Detection
    fun isExplicitCreation(query: String): Boolean {
        val lower = query.lowercase().trim()
        val isRoutine = lower.contains("routine") || lower.contains("schedule routine")
        val isAppOrBlock = lower.contains("block") || lower.contains("app") || lower.contains("filter")
        val isNoteOrHabit = lower.contains("note") || lower.contains("notes") || lower.contains("habit") || lower.contains("habits")
        if (isRoutine || isAppOrBlock || isNoteOrHabit) return false

        // Common action verbs at sentence start (e.g. "check ac prices", "buy groceries", "call dentist", "pay rent")
        val actionVerbPrefix = Regex("^(?:check|call|buy|email|clean|fix|read|write|order|pay|cook|meet|send|visit|book|study|prep|prepare|get|make|wash|inspect|verify|pick\\s+up|drop\\s+off)\\b\\s+[a-zA-Z0-9]")
        if (actionVerbPrefix.containsMatchIn(lower) && !lower.startsWith("check off") && !lower.startsWith("check out") && !lower.startsWith("check status") && !lower.startsWith("check in")) {
            return true
        }

        val regex = Regex("(?i)^\\s*(?:(?:add|create|new|schedule)(?:\\s+(?:a\\s+)?(?:task|todo|reminder))?|remind\\s+me\\s+to|please\\s+remind\\s+me\\s+to|remember\\s+to|need\\s+to|i\\s+need\\s+to|have\\s+to|i\\s+have\\s+to|must\\b|todo\\s*:?|task\\s*:?|don't\\s+forget\\s+to|put\\s+(?:my\\s+)?(?:task|todo|it|this)?\\s*.+?\\s+on\\s+(?:my\\s+)?(?:list|radar|agenda))")
        return regex.containsMatchIn(lower) ||
               lower.startsWith("todo:") || lower.startsWith("task:") || lower.startsWith("todo ") || lower.startsWith("task ") ||
               ((lower.startsWith("add ") || lower.startsWith("create ") || lower.startsWith("schedule ")) && !lower.startsWith("add block") && !lower.startsWith("add routine"))
    }

    fun extractTaskCreationDetails(query: String): Pair<String, Long?> {
        val parsed = SmartDateParser.parse(query)
        var title = parsed.cleanText.ifBlank { query }
        // Clean leading creation prefixes
        title = title.replace(Regex("(?i)^\\s*(?:(?:add|create|new|schedule)(?:\\s+(?:a\\s+)?(?:task|todo|reminder))?|remind\\s+me\\s+to|please\\s+remind\\s+me\\s+to|remember\\s+to|need\\s+to|i\\s+need\\s+to|have\\s+to|i\\s+have\\s+to|must\\b|todo\\s*:?|task\\s*:?|don't\\s+forget\\s+to)\\s*[,:\\-]?\\s*"), "")
        // Clean trailing phrases like "on my list", "to my radar"
        title = title.replace(Regex("(?i)\\s+(?:on\\s+(?:my\\s+)?(?:list|radar|agenda)|to\\s+(?:my\\s+)?(?:list|radar|agenda|tasks))$"), "")
        title = title.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return Pair(title.ifBlank { "New Task" }, parsed.timestamp)
    }

    // 4. Intent Classification
    fun classifyIntent(query: String, pendingTasks: List<Task> = emptyList()): NluIntent {
        val lower = query.lowercase().trim()
        val tokens = lower.split(Regex("\\s+"))

        // Tier 1: Explicit creation prefix recognition ALWAYS takes precedence
        if (isExplicitCreation(query)) {
            return NluIntent.CREATE_TASK
        }

        val isClearChat = matchesAnyFuzzy(tokens, listOf("clear", "clean", "reset", "wipe")) && matchesAnyFuzzy(tokens, listOf("chat", "messages", "screen", "history", "all"))
        if (isClearChat) return NluIntent.CLEAR_CHAT

        val isStartDrill = matchesAnyFuzzy(tokens, listOf("drill", "arithmetic", "calculate", "quiz")) || lower.contains("practice math")
        if (isStartDrill) return NluIntent.START_DRILL

        val isShowProfile = matchesAnyFuzzy(tokens, listOf("profile", "level", "aptitude", "streak", "stats", "statistics", "points", "xp"))
        if (isShowProfile) return NluIntent.SHOW_PROFILE

        val isShowSummary = (matchesAnyFuzzy(tokens, listOf("summary", "briefing", "recap", "dashboard")) || lower.contains("my day") || lower == "today") && !lower.contains("task")
        if (isShowSummary) return NluIntent.SHOW_SUMMARY

        val isRoutineKeyword = matchesAnyFuzzy(tokens, listOf("routine", "routines", "schedule", "schedules"))
        val isStartRoutine = (matchesAnyFuzzy(tokens, listOf("start", "enable", "activate", "resume", "begin", "launch")) || (lower.contains("turn") && lower.contains("on"))) && isRoutineKeyword && !lower.contains("task")
        val isStopRoutine = (matchesAnyFuzzy(tokens, listOf("stop", "disable", "deactivate", "pause", "halt", "end", "quit")) || (lower.contains("turn") && lower.contains("off"))) && isRoutineKeyword && !lower.contains("task")
        val isListRoutines = (matchesAnyFuzzy(tokens, listOf("list", "show", "what", "display", "check", "status")) && matchesAnyFuzzy(tokens, listOf("routine", "routines", "schedules", "timings"))) || lower == "routines" || lower == "schedules" || lower == "my routines"
        if (isStartRoutine) return NluIntent.START_ROUTINE
        if (isStopRoutine) return NluIntent.STOP_ROUTINE
        if (isListRoutines) return NluIntent.LIST_ROUTINES

        val isBlock = matchesAnyFuzzy(tokens, listOf("block", "lock", "restrict", "shield")) && (lower.contains("app") || lower.contains("apps") || lower.contains("filter") || lower.contains("category") || lower.contains("mode") || lower.contains("site") || lower.contains("instagram") || lower.contains("youtube") || lower.contains("social") || lower.contains("game") || lower.contains("games"))
        val isUnblock = matchesAnyFuzzy(tokens, listOf("unblock", "unlock", "allow", "unshield")) && (lower.contains("app") || lower.contains("apps") || lower.contains("filter") || lower.contains("category") || lower.contains("mode") || lower.contains("site") || lower.contains("instagram") || lower.contains("youtube") || lower.contains("social") || lower.contains("game") || lower.contains("games"))
        if (isBlock) return if (lower.contains("filter") || lower.contains("category")) NluIntent.BLOCK_FILTER else NluIntent.BLOCK_APP
        if (isUnblock) return NluIntent.UNBLOCK

        val isListTasks = ((matchesAnyFuzzy(tokens, listOf("list", "show", "what", "pending", "overdue", "today")) || lower.startsWith("what are")) && 
                           (lower.contains("task") || lower.contains("to do") || lower.contains("todo") || lower.contains("agenda"))) || 
                           lower == "tasks" || lower == "my tasks" || lower == "todo list" || lower == "todo" || lower == "todos"
        if (isListTasks) return NluIntent.LIST_TASKS

        // Tier 2: Disambiguate Complete
        // Bare "check" is NEVER complete! Only "check off", "mark done/complete", "finished with", "done with"
        val isComplete = lower.contains("check off") || lower.contains("checked off") ||
                         (lower.contains("mark") && (lower.contains("done") || lower.contains("complete") || lower.contains("completed"))) ||
                         lower.startsWith("done with") || lower.startsWith("finished with") || lower.startsWith("completed ") ||
                         lower == "complete" || lower == "done" || lower == "finish" ||
                         (matchesAnyFuzzy(tokens, listOf("complete", "finish", "done")) && !lower.contains("tomorrow") && !lower.contains("at ") && !lower.contains("by ") && !lower.contains("pm") && !lower.contains("am"))

        // Tier 2: Disambiguate Reschedule
        // Pure reschedule words
        val isExplicitRescheduleWord = matchesAnyFuzzy(tokens, listOf("reschedule", "postpone", "bump")) ||
                                       (lower.contains("change") && (lower.contains("due date") || lower.contains("deadline") || lower.contains("due time"))) ||
                                       (lower.contains("move") && (lower.contains("due date") || lower.contains("deadline"))) ||
                                       (lower.contains("push") && (lower.contains("due date") || lower.contains("deadline") || lower.contains("overdue"))) ||
                                       (lower.contains("delay") && (lower.contains("task") || lower.contains("due"))) ||
                                       (lower.contains("overdue") && (lower.contains("tomorrow") || lower.contains("tonight") || lower.contains("next week") || lower.contains("to ")))

        val isDelete = (matchesAnyFuzzy(tokens, listOf("delete", "remove", "trash", "cancel")) && (lower.contains("task") || pendingTasks.isNotEmpty())) ||
                       (lower.contains("clean") && (lower.contains("overdue") || lower.contains("tasks"))) ||
                       (lower.contains("clear") && lower.contains("overdue"))

        return when {
            isDelete -> NluIntent.DELETE
            isExplicitRescheduleWord -> NluIntent.RESCHEDULE
            isComplete -> NluIntent.COMPLETE
            else -> NluIntent.UNKNOWN
        }
    }

    // 4. Time Entity Extraction (NER)
    fun extractTimeEntity(query: String): Pair<Long?, Boolean> {
        // High-precision SmartDateParser handles complex relative & absolute formats
        val parsed = SmartDateParser.parse(query)
        if (parsed.timestamp != null) {
            return Pair(parsed.timestamp, parsed.hasTime)
        }

        val lower = query.lowercase()
        val now = System.currentTimeMillis()
        
        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        // Time parsing like "9pm" or "at 5"
        var customTimeMs: Long? = null
        var hasTime = false
        val timeRegex = Regex("(?i)\\b(?:at|by|to|for)?\\s*([1-9]|1[0-2])\\s*(am|pm)?\\b")
        val match = timeRegex.find(lower)
        if (match != null) {
            var hour = match.groupValues[1].toInt()
            val ampm = match.groupValues[2].lowercase()
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            if (ampm.isEmpty() && hour in 1..7) hour += 12
            
            customTimeMs = startOfDay + (hour * 3600000L)
            hasTime = true
        }

        val baseDay = when {
            lower.contains("tomorrow") || lower.contains("tmrw") || lower.contains("tmr") -> startOfDay + 86400000L
            lower.contains("next week") -> startOfDay + (7 * 86400000L)
            lower.contains("tonight") || lower.contains("today") -> startOfDay
            lower.contains("monday") -> getNextDayOfWeek(Calendar.MONDAY, startOfDay)
            lower.contains("tuesday") -> getNextDayOfWeek(Calendar.TUESDAY, startOfDay)
            lower.contains("wednesday") -> getNextDayOfWeek(Calendar.WEDNESDAY, startOfDay)
            lower.contains("thursday") -> getNextDayOfWeek(Calendar.THURSDAY, startOfDay)
            lower.contains("friday") -> getNextDayOfWeek(Calendar.FRIDAY, startOfDay)
            lower.contains("saturday") -> getNextDayOfWeek(Calendar.SATURDAY, startOfDay)
            lower.contains("sunday") -> getNextDayOfWeek(Calendar.SUNDAY, startOfDay)
            else -> null
        }
        
        if (baseDay != null && customTimeMs != null) {
             val diff = customTimeMs - startOfDay
             return Pair(baseDay + diff, true)
        } else if (baseDay != null) {
             val defaultHour = if (lower.contains("tonight")) 20 else 8
             return Pair(baseDay + (defaultHour * 3600000L), lower.contains("tonight"))
        } else if (customTimeMs != null) {
             return Pair(customTimeMs, true)
        }

        return Pair(null, false)
    }
    
    private fun getNextDayOfWeek(targetDayOfWeek: Int, startOfDayMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDayMs }
        var addDays = targetDayOfWeek - cal.get(Calendar.DAY_OF_WEEK)
        if (addDays <= 0) addDays += 7
        return startOfDayMs + (addDays * 86400000L)
    }

    // 5. Target Entity Extraction (Which task?)
    fun extractTargetTaskInfo(query: String, pendingTasks: List<Task>): TargetTaskResult {
        val lower = query.lowercase().trim()
        val isAll = lower.contains("all") || lower.contains("everything") || 
                    (lower.contains("overdue") && (lower.contains("clean") || lower.contains("clear") || lower.contains("tasks") || lower.contains("push") || lower.contains("postpone") || lower.contains("reschedule") || lower.contains("move") || lower.contains("triage")))
        if (isAll) return TargetTaskResult(targetTask = null, isAll = true)

        if (pendingTasks.isEmpty()) return TargetTaskResult(targetTask = null, isAll = false)
        if (pendingTasks.size == 1 && (lower.contains("it") || lower.contains("that") || lower.contains("the task") || lower == "reschedule" || lower == "complete" || lower == "delete")) {
            return TargetTaskResult(targetTask = pendingTasks.first(), isAll = false, candidateTasks = listOf(pendingTasks.first()))
        }

        // Semantic relative task descriptions
        val now = System.currentTimeMillis()
        if (lower.contains("overdue")) {
            val overdue = pendingTasks.find { it.dueDate != null && it.dueDate < now }
            if (overdue != null) return TargetTaskResult(targetTask = overdue, candidateTasks = listOf(overdue))
        }
        if (lower.contains("priority") || lower.contains("important") || lower.contains("urgent") || lower.contains("top task")) {
            val priorityTask = pendingTasks.find { it.isPriority }
            if (priorityTask != null) return TargetTaskResult(targetTask = priorityTask, candidateTasks = listOf(priorityTask))
        }
        if (lower.contains("last one") || lower.contains("last task")) {
            val lastTask = pendingTasks.lastOrNull()
            return TargetTaskResult(targetTask = lastTask, candidateTasks = if (lastTask != null) listOf(lastTask) else emptyList())
        }
        
        // Ordinal & Digit Check (e.g. "task 1", "task #1", "#1", "1st task", "first task", "complete 1", "reschedule 1 to 5pm")
        // NOTE: We ensure digits that belong to time expressions (e.g., "5pm", "5:30", "at 5", "to 5pm") are NOT mistaken for task index.
        val numRegex = Regex("(?:task\\s*#?\\s*(\\d+)|#\\s*(\\d+)|\\b(\\d+)(?:st|nd|rd|th)?\\s+(?:task|one)\\b|\\b(?:complete|finish|done|delete|remove|reschedule|postpone|move|bump|check)\\s+(?:task\\s+|#)?(\\d+)(?!\\s*(?:am|pm|o'?clock|[:.]\\d{2}))\\b)")
        val numMatch = numRegex.find(lower)
        if (numMatch != null) {
            val numStr = (numMatch.groupValues[1].ifEmpty { null }
                ?: numMatch.groupValues[2].ifEmpty { null }
                ?: numMatch.groupValues[3].ifEmpty { null }
                ?: numMatch.groupValues[4].ifEmpty { null })
            val num = numStr?.toIntOrNull()
            if (num != null) {
                val index = num - 1
                if (index in pendingTasks.indices) {
                    val task = pendingTasks[index]
                    return TargetTaskResult(targetTask = task, candidateTasks = listOf(task))
                }
            }
        }

        // Word ordinals
        val wordRegex = Regex("\\b(first|second|third|fourth|fifth|1st|2nd|3rd|4th|5th)\\b(?:\\s+(?:task|one))?")
        val wordMatch = wordRegex.find(lower)
        if (wordMatch != null) {
            val wordStr = wordMatch.groupValues[1]
            val index = when (wordStr) {
                "first", "1st" -> 0
                "second", "2nd" -> 1
                "third", "3rd" -> 2
                "fourth", "4th" -> 3
                "fifth", "5th" -> 4
                else -> -1
            }
            if (index in pendingTasks.indices) {
                val task = pendingTasks[index]
                return TargetTaskResult(targetTask = task, candidateTasks = listOf(task))
            }
        }

        // 6. Natural Language Word Extraction for filtering tasks
        // Clean out action verbs, prepositions, dates, and times to isolate the user's task keywords
        var candidateKeyword = lower
            // Remove slash command prefixes
            .replace(Regex("^/(?:talk|reschedule|complete|delete)\\s*"), "")
            // Remove intent verbs
            .replace(Regex("(?i)\\b(reschedule|postpone|move|delay|bump|push|change|set|complete|finish|done|delete|remove|cancel)\\b"), " ")
            // Remove helper phrasing
            .replace(Regex("(?i)\\b(please|can\\s+you|could\\s+you|ayva|need\\s+to|want\\s+to|have\\s+to|remind\\s+me\\s+to|remember\\s+to)\\b"), " ")
            // Remove relative and absolute date expressions
            .replace(Regex("(?i)\\b(tomorrow|tmrw|tmr|today|tonight|yesterday|next\\s+week|next\\s+month|next\\s+year|day\\s+after\\s+tomorrow)\\b"), " ")
            .replace(Regex("(?i)\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\\b"), " ")
            .replace(Regex("(?i)\\b(morning|afternoon|evening|night|noon|midnight)\\b"), " ")
            .replace(Regex("(?i)\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s+\\d{1,2}(?:st|nd|rd|th)?(?:\\s+\\d{4})?\\b"), " ")
            .replace(Regex("(?i)\\b\\d{1,2}(?:st|nd|rd|th)?\\s+(?:of\\s+)?(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*(?:\\s+\\d{4})?\\b"), " ")
            .replace(Regex("(?i)\\b\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}\\b"), " ")
            .replace(Regex("(?i)\\b\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4}\\b"), " ")
            // Remove time expressions (e.g., 5pm, 5:30, at 5, to 4pm, 9 o'clock)
            .replace(Regex("(?i)\\b(?:at|by|to|for|around)?\\s*\\d{1,2}(?:[:.]\\d{2})?\\s*(?:am|pm)?\\b"), " ")
            .replace(Regex("(?i)\\b\\d{1,2}\\s*o'?clock\\b"), " ")
            .replace(Regex("(?i)\\b(?:in\\s+\\d+\\s*(?:m|min|mins|minutes?|h|hr|hrs|hours?|d|days?|w|weeks?))\\b"), " ")
            // Remove prepositions & filler articles
            .replace(Regex("(?i)\\b(the|my|a|an|task|tasks|todo|todos|due|date|time|for|at|on|to|by|until|in|with|of)\\b"), " ")
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (candidateKeyword.isNotBlank()) {
            val candidateTokens = candidateKeyword.split(Regex("\\s+")).filter { it.length >= 2 }

            // Score each task against candidate keyword
            data class ScoredTask(val task: Task, val score: Int)
            val scoredList = pendingTasks.map { task ->
                val titleLower = task.title.lowercase().trim()
                var score = 0

                // 1. Exact or whole-phrase substring match
                if (titleLower == candidateKeyword) {
                    score += 100
                } else if (titleLower.contains(candidateKeyword)) {
                    score += 80
                } else if (candidateKeyword.contains(titleLower)) {
                    score += 75
                }

                // 2. Token-level overlap (supports 2+ letter words like "gym", "run", "pay", "ui", "qa")
                val titleTokens = titleLower.split(Regex("[^a-zA-Z0-9]+")).filter { it.length >= 2 }
                for (cToken in candidateTokens) {
                    for (tToken in titleTokens) {
                        if (cToken == tToken) {
                            score += 30
                        } else if (tToken.startsWith(cToken) || cToken.startsWith(tToken)) {
                            score += 20
                        } else if (cToken.length >= 3 && tToken.length >= 3) {
                            val dist = levenshtein(cToken, tToken)
                            if (dist <= 1) {
                                score += 15
                            }
                        }
                    }
                }

                ScoredTask(task, score)
            }.filter { it.score > 0 }.sortedByDescending { it.score }

            if (scoredList.isNotEmpty()) {
                val topScore = scoredList.first().score
                val topTask = scoredList.first().task
                val matchingTasks = scoredList.map { it.task }

                // If only 1 task matched, or top match is overwhelmingly better
                if (scoredList.size == 1 || (topScore >= 75 && (scoredList.getOrNull(1)?.score ?: 0) < topScore / 2)) {
                    return TargetTaskResult(targetTask = topTask, candidateTasks = matchingTasks, extractedKeyword = candidateKeyword)
                } else {
                    // Multiple tasks match user's words (e.g. "Gym Push" and "Gym Legs")
                    return TargetTaskResult(targetTask = null, candidateTasks = matchingTasks, extractedKeyword = candidateKeyword)
                }
            } else {
                // User specified a word that didn't match any pending task
                return TargetTaskResult(targetTask = null, candidateTasks = emptyList(), extractedKeyword = candidateKeyword)
            }
        }

        // If no keyword was extracted and there's only 1 pending task, only target that single task if the user explicitly referred to it with pronouns or bare command
        if (pendingTasks.size == 1 && (lower.contains("it") || lower.contains("that") || lower.contains("the task") || lower == "reschedule" || lower == "complete" || lower == "delete" || lower == "finish" || lower == "done")) {
            return TargetTaskResult(targetTask = pendingTasks.first(), candidateTasks = pendingTasks)
        }

        return TargetTaskResult(targetTask = null, candidateTasks = pendingTasks, extractedKeyword = null)
    }

    fun extractTargetTask(query: String, pendingTasks: List<Task>): Pair<Task?, Boolean> {
        val result = extractTargetTaskInfo(query, pendingTasks)
        return Pair(result.targetTask, result.isAll)
    }
    
    // Extractor for Block Intent
    fun extractBlockTarget(query: String): Pair<String?, String?> {
        val lower = query.lowercase()
        val mode = if (lower.contains("soft block") || lower.contains("soft mode") || lower.contains("soft lock") || lower.contains("soft")) "SOFT" else "HARD"
        
        val match = Regex("\\b(block|lock|restrict|shield|unblock|unlock|allow|unshield)\\b\\s+(.*)").find(lower)
        var targetName = match?.groupValues?.get(2) ?: lower
        targetName = targetName.replace(Regex("\\b(soft|hard|in|mode|filter|category|apps|app|the|a|an)\\b"), " ")
        targetName = targetName.replace(Regex("\\s+"), " ").trim()
        
        return Pair(targetName.ifBlank { null }, mode)
    }
    
    // Extractor for Routine Intent
    fun extractRoutineTarget(query: String): Pair<String?, Boolean> {
        val lower = query.lowercase().trim()
        val isAll = lower.contains("all")
        
        val match = Regex("\\b(start|begin|launch|enable|activate|resume|stop|disable|deactivate|pause|halt|end|turn\\s+on|turn\\s+off)\\b\\s+(.*)").find(lower)
        var targetName = match?.groupValues?.get(2) ?: lower
        targetName = targetName.replace(Regex("\\b(routine|routines|schedule|schedules|the|my|a|an|all)\\b"), " ")
        targetName = targetName.replace(Regex("\\s+"), " ").trim()
        
        return Pair(targetName.ifEmpty { null }, isAll)
    }

    // Master Parser
    fun parse(query: String, pendingTasks: List<Task>): NluParsedResult {
        val trimmed = query.trim()
        val lower = trimmed.lowercase()

        // 1. Check explicit creation prefix
        if (isExplicitCreation(trimmed)) {
            val (taskTitle, dueDate) = extractTaskCreationDetails(trimmed)
            // Check if an existing task already has a matching or similar title
            val matchingExisting = pendingTasks.find { existing ->
                val t1 = existing.title.trim().lowercase()
                val t2 = taskTitle.trim().lowercase()
                t1 == t2 || (t1.length >= 4 && t2.length >= 4 && (t1.contains(t2) || t2.contains(t1) || levenshtein(t1, t2) <= 1))
            }
            if (matchingExisting != null) {
                val dateStr = if (dueDate != null) " ${SmartDateParser.formatDueDate(dueDate)}" else " tomorrow"
                val options = listOf(
                    ConflictOption(
                        label = if (dueDate != null) "Update Time" else "Reschedule '${matchingExisting.title.take(32)}'",
                        emoji = "⏰",
                        command = "/talk reschedule ${matchingExisting.title}$dateStr"
                    ),
                    ConflictOption(
                        label = "Mark Done: '${matchingExisting.title.take(32)}'",
                        emoji = "✅",
                        command = "/talk complete ${matchingExisting.title}"
                    ),
                    ConflictOption(
                        label = "Create Duplicate Task",
                        emoji = "➕",
                        command = "/create $trimmed"
                    )
                )
                return NluParsedResult(
                    intent = NluIntent.CONFLICT,
                    targetTask = matchingExisting,
                    matchingTasks = listOf(matchingExisting),
                    filterQuery = taskTitle,
                    conflictPrompt = "📋 **Existing Task Found:** You already have an active task **'${matchingExisting.title}'**${if (matchingExisting.dueDate != null) " (Due: ${SmartDateParser.formatDueDate(matchingExisting.dueDate)})" else ""}.\nWould you like to update its scheduled time or create a separate task?",
                    conflictOptions = options
                )
            }

            return NluParsedResult(
                intent = NluIntent.CREATE_TASK,
                createdTaskTitle = taskTitle,
                targetDateMs = dueDate,
                hasExplicitTimeSpecified = dueDate != null
            )
        }

        // 2. Daily Quests / Mystery Chest vs Todo Tasks
        val isQuestsOrChest = lower in listOf("quests", "daily quests", "chest", "box", "mystery", "mystery chest", "mystery box", "open chest", "daily challenges", "claim rewards", "claim chest")
        if (isQuestsOrChest) {
            val options = listOf(
                ConflictOption(
                    label = "Open Daily Quests & Chest",
                    emoji = "🎁",
                    command = "/quests"
                ),
                ConflictOption(
                    label = "View Todo Tasks",
                    emoji = "📋",
                    command = "/tasks"
                )
            )
            return NluParsedResult(
                intent = NluIntent.CONFLICT,
                conflictPrompt = "🎁 **Daily Quests or Todo Tasks?** Did you mean your Daily Learning Quests & Mystery Chest or your Todo Tasks list?",
                conflictOptions = options
            )
        }

        // 3. Habit Tracker vs Todo Task
        val isHabitQuery = lower in listOf("habit", "habits", "my habits", "show habits", "habit tracker", "track habits", "habits list", "open habits", "new habit", "track habit") ||
            (lower.startsWith("habit") && !lower.contains("delete") && !lower.contains("cancel")) ||
            (lower.contains("daily") && (lower.contains("meditat") || lower.contains("water") || lower.contains("gym") || lower.contains("workout") || lower.contains("exercise") || lower.contains("walk") || lower.contains("journal") || lower.contains("read"))) ||
            (lower.contains("every day") && (lower.contains("meditat") || lower.contains("water") || lower.contains("gym") || lower.contains("workout") || lower.contains("exercise") || lower.contains("walk") || lower.contains("journal") || lower.contains("read")))
        if (isHabitQuery) {
            val options = listOf(
                ConflictOption(
                    label = "Open Habit Tracker",
                    emoji = "🌱",
                    command = "/habit"
                ),
                ConflictOption(
                    label = "Add as Todo Task",
                    emoji = "📋",
                    command = "/create $trimmed"
                )
            )
            return NluParsedResult(
                intent = NluIntent.CONFLICT,
                conflictPrompt = "🌱 **Habit Tracker or Todo Task?** Would you like to track this in your Daily Habit Tracker or add it as a Todo task?",
                conflictOptions = options
            )
        }

        // 4. Focus Session / Timer vs Todo Task
        val focusDurationRegex = Regex("(?i)\\b(?:focus|study|deep\\s*work|pomodoro|work|read|code)\\s+(?:for\\s+)?(\\d+)\\s*(?:m|min|mins|minutes|h|hr|hrs|hours)\\b")
        val isGeneralFocusQuery = Regex("(?i)^\\s*(?:start\\s+)?(?:focus(?:\\s+session)?|deep\\s*work|pomodoro)(?:\\s+(?:for|on)\\s+.+)?\\s*$").matches(lower)
        val focusDurationMatch = focusDurationRegex.find(lower)
        if (focusDurationMatch != null || isGeneralFocusQuery) {
            val durationLabel = focusDurationMatch?.groupValues?.get(1)?.let { "$it mins" } ?: ""
            val focusButtonLabel = if (durationLabel.isNotEmpty()) "Start $durationLabel Focus" else "Start Focus Session"
            val options = listOf(
                ConflictOption(
                    label = focusButtonLabel,
                    emoji = "⏱️",
                    command = "/talk routines"
                ),
                ConflictOption(
                    label = "Add to Todo List",
                    emoji = "📋",
                    command = "/create $trimmed"
                )
            )
            return NluParsedResult(
                intent = NluIntent.CONFLICT,
                conflictPrompt = "⏱️ **Focus Session or Todo Task?** Would you like to start a focused timer session or add this to your Todo list?",
                conflictOptions = options
            )
        }

        // 5. Math Drill vs Todo Task
        val isMathDrillQuery = lower in listOf("math", "drill", "math drill", "practice math", "math practice", "arithmetic", "arithmetic practice", "do math", "math quiz", "quiz", "solve math") ||
            (lower.contains("math") && (lower.contains("practice") || lower.contains("drill") || lower.contains("quiz") || lower.contains("solve"))) ||
            (lower.contains("arithmetic") && (lower.contains("practice") || lower.contains("drill") || lower.contains("quiz")))
        if (isMathDrillQuery) {
            val options = listOf(
                ConflictOption(
                    label = "Start Math Drill (10Q)",
                    emoji = "🧮",
                    command = "/drill easy 10"
                ),
                ConflictOption(
                    label = "Start 5-Min Blitz",
                    emoji = "⚡",
                    command = "/blitz"
                ),
                ConflictOption(
                    label = "Add to Todo List",
                    emoji = "📋",
                    command = "/create $trimmed"
                )
            )
            return NluParsedResult(
                intent = NluIntent.CONFLICT,
                conflictPrompt = "🧮 **Math Drill or Todo Task?** Would you like to practice arithmetic in an interactive Drill right now, or save it to your Todo list?",
                conflictOptions = options
            )
        }

        // 6. Routine / Schedule vs Todo Task
        val isAmbiguousRoutine = (lower in listOf("routine", "routines", "my routines", "schedules", "my schedules", "morning routine", "night routine", "bedtime routine", "evening routine", "study routine", "daily routine", "work routine")) &&
            !lower.startsWith("list ") && !lower.startsWith("start ") && !lower.startsWith("stop ") && !lower.startsWith("enable ") && !lower.startsWith("disable ")
        if (isAmbiguousRoutine) {
            val options = listOf(
                ConflictOption(
                    label = "Manage Routines",
                    emoji = "⚡",
                    command = "/talk routines"
                ),
                ConflictOption(
                    label = "Add to Todo List",
                    emoji = "📋",
                    command = "/create $trimmed"
                )
            )
            return NluParsedResult(
                intent = NluIntent.CONFLICT,
                conflictPrompt = "⚡ **Routine or Todo Task?** Did you mean to view or activate your Focus Routines, or add this as a Todo task?",
                conflictOptions = options
            )
        }

        // 7. Clear Screen vs Clear / Complete Tasks
        val isClearAmbiguous = lower in listOf("clear all", "clean all", "wipe all", "clean up", "clear screen", "clear tasks", "clear chat", "clear everything") || (lower == "clear" && pendingTasks.isNotEmpty())
        if (isClearAmbiguous) {
            val options = listOf(
                ConflictOption(
                    label = "Clear Chat Screen",
                    emoji = "💬",
                    command = "/clear"
                ),
                ConflictOption(
                    label = "Complete All Tasks",
                    emoji = "✅",
                    command = "/talk complete all"
                ),
                ConflictOption(
                    label = "Delete All Tasks",
                    emoji = "🗑️",
                    command = "/talk delete all"
                )
            )
            return NluParsedResult(
                intent = NluIntent.CONFLICT,
                conflictPrompt = "🧹 **Disambiguation:** What would you like to clear?",
                conflictOptions = options
            )
        }

        // 8. Stats / Profile vs Daily Task Summary
        val isSummaryOrProfileAmbiguous = (lower in listOf("stats", "my stats", "summary", "daily summary", "my summary", "recap", "profile", "my profile", "xp", "level", "my day")) &&
            !lower.contains("task") && !lower.contains("todo")
        if (isSummaryOrProfileAmbiguous) {
            val options = listOf(
                ConflictOption(
                    label = "Aptitude Profile & XP",
                    emoji = "🏆",
                    command = "/profile"
                ),
                ConflictOption(
                    label = "Daily Task Summary",
                    emoji = "📈",
                    command = "/summary"
                )
            )
            return NluParsedResult(
                intent = NluIntent.CONFLICT,
                conflictPrompt = "📊 **Choose Summary View:** Would you like to view your Aptitude & XP Profile or your Daily Task Summary?",
                conflictOptions = options
            )
        }

        var intent = classifyIntent(trimmed, pendingTasks)
        val targetInfo = extractTargetTaskInfo(trimmed, pendingTasks)
        val (timeMs, hasTime) = extractTimeEntity(trimmed)
        
        var targetFilterOrAppName: String? = null
        var blockMode: String? = null
        var targetRoutineName: String? = null
        var isAllRoutines = false
        
        if (intent == NluIntent.BLOCK_APP || intent == NluIntent.BLOCK_FILTER || intent == NluIntent.UNBLOCK) {
            val (name, mode) = extractBlockTarget(trimmed)
            targetFilterOrAppName = name
            blockMode = mode
            if (name.isNullOrEmpty() && !lower.contains("all")) {
                intent = NluIntent.UNKNOWN
            }
        } else if (intent == NluIntent.START_ROUTINE || intent == NluIntent.STOP_ROUTINE) {
            val (routineName, allRoutines) = extractRoutineTarget(trimmed)
            targetRoutineName = routineName
            isAllRoutines = allRoutines
        }

        val hasCheckVerb = lower.contains("check") && !lower.contains("check off")
        val hasMoveVerb = lower.startsWith("move ") || lower.contains(" move ")
        val hasPushVerb = lower.startsWith("push ") || lower.contains(" push ")
        val hasFinishVerb = lower.startsWith("finish ") || lower.startsWith("complete ")
        val hasChangeVerb = lower.startsWith("change ")

        // If classified as RESCHEDULE:
        if (intent == NluIntent.RESCHEDULE) {
            if (pendingTasks.isEmpty() && (lower.startsWith("reschedule") || lower.startsWith("postpone"))) {
                return NluParsedResult(
                    intent = NluIntent.RESCHEDULE,
                    targetDateMs = timeMs,
                    hasExplicitTimeSpecified = hasTime
                )
            }
            if (targetInfo.isAll) {
                return NluParsedResult(
                    intent = NluIntent.RESCHEDULE,
                    isAllTasks = true,
                    targetDateMs = timeMs,
                    hasExplicitTimeSpecified = hasTime
                )
            }
            if (targetInfo.candidateTasks.size > 1) {
                // CONFLICT: Multiple candidate tasks match
                val dateStr = if (timeMs != null) " ${SmartDateParser.formatDueDate(timeMs)}" else ""
                val options = targetInfo.candidateTasks.take(3).map { t ->
                    ConflictOption(
                        label = "Reschedule '${t.title.take(32)}'",
                        emoji = "⏰",
                        command = "/talk reschedule ${t.title}$dateStr"
                    )
                } + ConflictOption(
                    label = "Create as New Task",
                    emoji = "➕",
                    command = "/create $trimmed"
                )
                return NluParsedResult(
                    intent = NluIntent.CONFLICT,
                    matchingTasks = targetInfo.candidateTasks,
                    filterQuery = targetInfo.extractedKeyword,
                    conflictPrompt = "🤔 **Conflict Detected:** Multiple tasks match **'${targetInfo.extractedKeyword ?: "query"}'**. Which one would you like to update?",
                    conflictOptions = options
                )
            } else if (targetInfo.targetTask != null) {
                // Exactly 1 task matches
                if (hasMoveVerb || hasPushVerb || hasChangeVerb) {
                    val dateStr = if (timeMs != null) SmartDateParser.formatDueDate(timeMs) else "tomorrow"
                    val (newTitle, _) = extractTaskCreationDetails(trimmed)
                    val options = listOf(
                        ConflictOption(
                            label = "Reschedule '${targetInfo.targetTask.title.take(32)}'",
                            emoji = "⏰",
                            command = "/talk reschedule ${targetInfo.targetTask.title} $dateStr"
                        ),
                        ConflictOption(
                            label = "Create New: '${newTitle.take(32)}'",
                            emoji = "➕",
                            command = "/create $trimmed"
                        )
                    )
                    return NluParsedResult(
                        intent = NluIntent.CONFLICT,
                        targetTask = targetInfo.targetTask,
                        matchingTasks = listOf(targetInfo.targetTask),
                        filterQuery = targetInfo.extractedKeyword,
                        conflictPrompt = "🤔 **Conflict Detected:** You have an active task **'${targetInfo.targetTask.title}'**. Did you mean to reschedule it or create a new task?",
                        conflictOptions = options
                    )
                }
            } else {
                // 0 tasks match!
                if (hasMoveVerb || hasPushVerb || hasChangeVerb || (timeMs != null && !lower.contains("reschedule") && !lower.contains("postpone"))) {
                    val (newTitle, dueDate) = extractTaskCreationDetails(trimmed)
                    return NluParsedResult(
                        intent = NluIntent.CREATE_TASK,
                        createdTaskTitle = newTitle,
                        targetDateMs = dueDate ?: timeMs,
                        hasExplicitTimeSpecified = hasTime
                    )
                } else {
                    val dateStr = if (timeMs != null) " ${SmartDateParser.formatDueDate(timeMs)}" else ""
                    val kw = targetInfo.extractedKeyword ?: trimmed
                    val options = listOf(
                        ConflictOption(
                            label = "Create Task: '${kw.take(32)}'",
                            emoji = "➕",
                            command = "/create $kw$dateStr".trim()
                        ),
                        ConflictOption(
                            label = "View Active Tasks",
                            emoji = "📋",
                            command = "/tasks"
                        )
                    )
                    return NluParsedResult(
                        intent = NluIntent.CONFLICT,
                        filterQuery = kw,
                        conflictPrompt = "🔍 **No active task found matching '$kw'.** Would you like to create it as a new task?",
                        conflictOptions = options
                    )
                }
            }
        }

        // If classified as COMPLETE:
        if (intent == NluIntent.COMPLETE) {
            if (pendingTasks.isEmpty()) {
                return NluParsedResult(
                    intent = NluIntent.COMPLETE
                )
            }
            if (targetInfo.isAll) {
                return NluParsedResult(
                    intent = NluIntent.COMPLETE,
                    isAllTasks = true
                )
            }
            if (targetInfo.candidateTasks.size > 1) {
                val options = targetInfo.candidateTasks.take(3).map { t ->
                    ConflictOption(
                        label = "Complete '${t.title.take(32)}'",
                        emoji = "✅",
                        command = "/talk complete ${t.title}"
                    )
                } + ConflictOption(
                    label = "Create as New Task",
                    emoji = "➕",
                    command = "/create $trimmed"
                )
                return NluParsedResult(
                    intent = NluIntent.CONFLICT,
                    matchingTasks = targetInfo.candidateTasks,
                    filterQuery = targetInfo.extractedKeyword,
                    conflictPrompt = "🤔 **Conflict Detected:** Multiple tasks match **'${targetInfo.extractedKeyword ?: "query"}'**. Which one did you complete?",
                    conflictOptions = options
                )
            } else if (targetInfo.targetTask != null) {
                if (timeMs != null || hasFinishVerb) {
                    val (newTitle, dueDate) = extractTaskCreationDetails(trimmed)
                    val dateStr = if (timeMs != null) SmartDateParser.formatDueDate(timeMs) else ""
                    val options = mutableListOf(
                        ConflictOption(
                            label = "Complete '${targetInfo.targetTask.title.take(32)}'",
                            emoji = "✅",
                            command = "/talk complete ${targetInfo.targetTask.title}"
                        )
                    )
                    if (timeMs != null) {
                        options.add(
                            ConflictOption(
                                label = "Reschedule '${targetInfo.targetTask.title.take(32)}'",
                                emoji = "⏰",
                                command = "/talk reschedule ${targetInfo.targetTask.title} $dateStr"
                            )
                        )
                    }
                    options.add(
                        ConflictOption(
                            label = "Create New: '${newTitle.take(32)}'",
                            emoji = "➕",
                            command = "/create $trimmed"
                        )
                    )
                    return NluParsedResult(
                        intent = NluIntent.CONFLICT,
                        targetTask = targetInfo.targetTask,
                        matchingTasks = listOf(targetInfo.targetTask),
                        filterQuery = targetInfo.extractedKeyword,
                        conflictPrompt = "🤔 **Conflict Detected:** You have an active task **'${targetInfo.targetTask.title}'**. What would you like to do?",
                        conflictOptions = options
                    )
                }
            } else {
                // 0 tasks matched for complete
                if (timeMs != null || hasCheckVerb || hasFinishVerb) {
                    val (newTitle, dueDate) = extractTaskCreationDetails(trimmed)
                    return NluParsedResult(
                        intent = NluIntent.CREATE_TASK,
                        createdTaskTitle = newTitle,
                        targetDateMs = dueDate ?: timeMs,
                        hasExplicitTimeSpecified = hasTime
                    )
                }
            }
        }

        // If classified as DELETE:
        if (intent == NluIntent.DELETE) {
            if (targetInfo.candidateTasks.size > 1) {
                val options = targetInfo.candidateTasks.take(3).map { t ->
                    ConflictOption(
                        label = "Delete '${t.title.take(32)}'",
                        emoji = "🗑️",
                        command = "/talk delete ${t.title}"
                    )
                } + ConflictOption(
                    label = "Cancel",
                    emoji = "❌",
                    command = "/tasks"
                )
                return NluParsedResult(
                    intent = NluIntent.CONFLICT,
                    matchingTasks = targetInfo.candidateTasks,
                    filterQuery = targetInfo.extractedKeyword,
                    conflictPrompt = "🗑️ **Which task would you like to remove?**",
                    conflictOptions = options
                )
            } else if (pendingTasks.size > 1 && targetInfo.targetTask == null && !targetInfo.isAll) {
                val options = pendingTasks.take(3).map { t ->
                    ConflictOption(
                        label = "Delete '${t.title.take(32)}'",
                        emoji = "🗑️",
                        command = "/talk delete ${t.title}"
                    )
                } + ConflictOption(
                    label = "Cancel",
                    emoji = "❌",
                    command = "/tasks"
                )
                return NluParsedResult(
                    intent = NluIntent.CONFLICT,
                    conflictPrompt = "🗑️ **Which task would you like to delete?**",
                    conflictOptions = options
                )
            }
        }

        // Fallback for strict single-word or direct command phrases only
        if (intent == NluIntent.UNKNOWN) {
            val isQuestion = lower.startsWith("how") || lower.startsWith("why") ||
                             lower.startsWith("what") || lower.startsWith("where") ||
                             lower.startsWith("who") || lower.startsWith("can") ||
                             lower.startsWith("could") || lower.startsWith("explain") ||
                             lower.contains("?")
            if (!isQuestion && targetInfo.targetTask != null && timeMs == null) {
                val options = listOf(
                    ConflictOption(
                        label = "Mark Done: '${targetInfo.targetTask.title.take(32)}'",
                        emoji = "✅",
                        command = "/talk complete ${targetInfo.targetTask.title}"
                    ),
                    ConflictOption(
                        label = "Reschedule '${targetInfo.targetTask.title.take(32)}'",
                        emoji = "⏰",
                        command = "/talk reschedule ${targetInfo.targetTask.title} tomorrow"
                    ),
                    ConflictOption(
                        label = "Create Duplicate",
                        emoji = "➕",
                        command = "/create $trimmed"
                    )
                )
                return NluParsedResult(
                    intent = NluIntent.CONFLICT,
                    targetTask = targetInfo.targetTask,
                    matchingTasks = listOf(targetInfo.targetTask),
                    filterQuery = targetInfo.extractedKeyword,
                    conflictPrompt = "🤔 **Active Task Found:** You already have **'${targetInfo.targetTask.title}'** on your radar. What would you like to do?",
                    conflictOptions = options
                )
            }
            if (!isQuestion && timeMs != null) {
                if (targetInfo.candidateTasks.size > 1) {
                    val dateStr = " ${SmartDateParser.formatDueDate(timeMs)}"
                    val options = targetInfo.candidateTasks.take(3).map { t ->
                        ConflictOption(
                            label = "Reschedule '${t.title.take(32)}'",
                            emoji = "⏰",
                            command = "/talk reschedule ${t.title}$dateStr"
                        )
                    } + ConflictOption(
                        label = "Create as New Task",
                        emoji = "➕",
                        command = "/create $trimmed"
                    )
                    return NluParsedResult(
                        intent = NluIntent.CONFLICT,
                        matchingTasks = targetInfo.candidateTasks,
                        filterQuery = targetInfo.extractedKeyword,
                        conflictPrompt = "🤔 **Conflict Detected:** Multiple tasks match **'${targetInfo.extractedKeyword ?: "query"}'**. Which one would you like to update?",
                        conflictOptions = options
                    )
                } else if (targetInfo.targetTask != null) {
                    val dateStr = SmartDateParser.formatDueDate(timeMs)
                    val (newTitle, _) = extractTaskCreationDetails(trimmed)
                    val options = listOf(
                        ConflictOption(
                            label = "Update Time",
                            emoji = "⏰",
                            command = "/talk reschedule ${targetInfo.targetTask.title} $dateStr"
                        ),
                        ConflictOption(
                            label = "Create New: '${newTitle.take(32)}'",
                            emoji = "➕",
                            command = "/create $trimmed"
                        )
                    )
                    return NluParsedResult(
                        intent = NluIntent.CONFLICT,
                        targetTask = targetInfo.targetTask,
                        matchingTasks = listOf(targetInfo.targetTask),
                        filterQuery = targetInfo.extractedKeyword,
                        conflictPrompt = "📋 **Existing Task Found:** You already have an active task **'${targetInfo.targetTask.title}'**.\nDid you mean to update its due time to $dateStr or create a separate task?",
                        conflictOptions = options
                    )
                }

                val (newTitle, dueDate) = extractTaskCreationDetails(trimmed)
                return NluParsedResult(
                    intent = NluIntent.CREATE_TASK,
                    createdTaskTitle = newTitle,
                    targetDateMs = dueDate ?: timeMs,
                    hasExplicitTimeSpecified = hasTime
                )
            }

            if (trimmed == "tasks" || trimmed == "todos" || trimmed == "my tasks" || trimmed == "pending tasks" || trimmed == "task list") {
                intent = NluIntent.LIST_TASKS
            } else if (trimmed == "profile" || trimmed == "my profile" || trimmed == "stats" || trimmed == "my stats" || trimmed == "xp") {
                intent = NluIntent.SHOW_PROFILE
            } else if (trimmed == "summary" || trimmed == "my summary" || trimmed == "daily summary" || trimmed == "briefing") {
                intent = NluIntent.SHOW_SUMMARY
            } else if (trimmed == "math" || trimmed == "drill" || trimmed == "math drill" || trimmed == "quiz") {
                intent = NluIntent.START_DRILL
            } else if (trimmed == "clear chat" || trimmed == "wipe chat" || trimmed == "clean chat") {
                intent = NluIntent.CLEAR_CHAT
            } else if (trimmed == "routines" || trimmed == "schedules" || trimmed == "my routines") {
                intent = NluIntent.LIST_ROUTINES
            }
        }
        
        return NluParsedResult(
            intent = intent,
            targetTask = targetInfo.targetTask,
            isAllTasks = if (isAllRoutines) true else targetInfo.isAll,
            targetDateMs = timeMs,
            blockMode = blockMode,
            targetFilterOrAppName = targetFilterOrAppName,
            targetRoutineName = targetRoutineName,
            matchingTasks = targetInfo.candidateTasks,
            filterQuery = targetInfo.extractedKeyword,
            hasExplicitTimeSpecified = hasTime
        )
    }
}

