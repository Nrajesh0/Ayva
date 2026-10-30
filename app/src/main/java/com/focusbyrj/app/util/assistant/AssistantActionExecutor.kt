package com.focusbyrj.app.util.assistant

import android.content.Context
import androidx.room.withTransaction
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.util.*
import com.focusbyrj.app.widget.TodoWidgetProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class AssistantActionResult(
    val text: String, val taskId: Long? = null, val habitId: Long? = null,
    val undo: List<JSONObject> = emptyList(), val changed: Boolean = true
)

/** The same mutation path serves conversation, slash commands, choices and task-card completion. */
object AssistantActionExecutor {
    private val mutex = Mutex()

    fun canDisableRoutine(context: Context, schedule: FocusSchedule): Boolean =
        !context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE).getBoolean("isSessionActive", false) &&
            !(schedule.mode == "HARD" && schedule.isActiveAt())

    suspend fun execute(context: Context, action: AssistantAction, expected: Map<String, String> = emptyMap()): AssistantActionResult = mutex.withLock {
        AssistantCapabilities.validate(action)
        val app = context.applicationContext as? FocusApplication ?: error("App database is unavailable")
        val dao = app.database.taskDao()
        fun now(previous: Long = 0) = maxOf(System.currentTimeMillis(), previous + 1)
        fun undoTask(before: Task?, after: Task, recurring: Task? = null) = JSONObject().apply {
            put("kind", "task"); put("before", before?.let { TaskJson.encode(it) } ?: JSONObject.NULL)
            put("after", TaskJson.encode(after)); recurring?.let { put("recurring", TaskJson.encode(it)) }
        }
        fun refresh(task: Task) {
            TaskReminderHelper.cancelReminder(context, task)
            if (!task.isCompleted && !task.isTrashed) TaskReminderHelper.scheduleReminder(context, task)
            TodoWidgetProvider.updateAllWidgets(context)
        }
        when (action) {
            is AssistantAction.SaveRoutine, is AssistantAction.SaveDictionaryWord, is AssistantAction.BookmarkWord, is AssistantAction.SetAppRule, is AssistantAction.SetAppRules -> AssistantFeatureActions.execute(context, app, action)
            is AssistantAction.CreateTask -> {
                require(action.draft.title.isNotBlank() && action.draft.title.length <= 500) { "Use a task title of 1–500 characters" }
                val draft = action.draft.copy(id = 0, isCompleted = false, isTrashed = false, completedAt = null, trashedAt = null, deletedAt = null, updatedAt = now())
                val created = draft.copy(id = dao.insertTaskChecked(draft))
                refresh(created)
                AssistantActionResult("Added **${created.title}**${created.dueDate?.let { " — ${SmartDateParser.formatDueDate(it)}" }.orEmpty()}${if (created.isPriority) " · priority" else ""}${if (created.isPersistent) " · persistent" else ""}${if (created.recurrence != RecurrencePattern.NONE) " · ${created.recurrence.name.lowercase()}" else ""}.", created.id, undo = listOf(undoTask(null, created)))
            }
            is AssistantAction.EditTasks, is AssistantAction.CompleteTasks, is AssistantAction.TrashTasks -> {
                val ids = when (action) {
                    is AssistantAction.EditTasks -> action.ids
                    is AssistantAction.CompleteTasks -> action.ids
                    is AssistantAction.TrashTasks -> action.ids
                    else -> emptyList()
                }.distinct()
                require(ids.isNotEmpty() && ids.size <= 100) { "Choose between 1 and 100 tasks" }
                val changes = mutableListOf<Triple<Task, Task, Task?>>()
                app.database.withTransaction {
                    val originals = ids.map { dao.getTaskById(it) ?: error("A selected task no longer exists") }
                    for (original in originals) expected["task:${original.id}"]?.let { snapshot ->
                        require(original == TaskJson.decode(JSONObject(snapshot))) { "A reviewed task changed before the write. Review it again" }
                    }
                    require(originals.none { it.isTrashed || it.deletedAt != null }) { "A selected task is in Trash; restore it before editing" }
                    for (before in originals) {
                        if (action is AssistantAction.CompleteTasks && before.isCompleted) continue
                        require(!before.isCompleted || (action is AssistantAction.EditTasks && action.field == TaskField.REOPEN)) { "That task is already completed" }
                        val after = when (action) {
                            is AssistantAction.CompleteTasks -> before.copy(isCompleted = true, completedAt = now(), updatedAt = now(before.updatedAt))
                            is AssistantAction.TrashTasks -> before.copy(isTrashed = true, trashedAt = now(), updatedAt = now(before.updatedAt))
                            is AssistantAction.EditTasks -> {
                                val updated = when (action.field) {
                                    TaskField.TITLE -> { require(action.value.isNotBlank() && action.value.length <= 500); before.copy(title = action.value) }
                                    TaskField.DUE -> before.copy(dueDate = if (action.value == "none") null else action.value.toLong().also { require(it > 0) })
                                    TaskField.PRIORITY -> before.copy(isPriority = action.value.toBooleanStrict())
                                    TaskField.PERSISTENT -> before.copy(isPersistent = action.value.toBooleanStrict())
                                    TaskField.RECURRENCE -> before.copy(recurrence = RecurrencePattern.valueOf(action.value))
                                    TaskField.DETAILS -> { require(action.value.length <= 10_000); before.copy(details = action.value) }
                                    TaskField.CHECKLIST -> { require(ids.size == 1); before.copy(subtasksJson = AssistantChecklist.validate(before.subtasksJson, action.value)) }
                                    TaskField.REOPEN -> before.copy(isCompleted = false, completedAt = null)
                                }
                                updated.copy(updatedAt = now(before.updatedAt))
                            }
                            else -> error("Unsupported task operation")
                        }
                        if (action is AssistantAction.CompleteTasks) com.focusbyrj.app.data.TaskCompletionRules.validate(after, if (action.overrideDependencies) null else dao.getAllTasksList().map { if (it.id in ids) it.copy(isCompleted = true) else it })
                        require(after.planning?.availableFrom == null || after.dueDate == null || after.planning!!.availableFrom!! <= after.dueDate!!) { "Start showing must be on or before the due date" }
                        var recurring: Task? = null
                        if (action is AssistantAction.CompleteTasks && before.recurrence != RecurrencePattern.NONE) {
                            val next = TaskReminderHelper.generateNextRecurringTask(after)
                            recurring = next.copy(id = dao.insertTask(next))
                        }
                        changes.add(Triple(before, after, recurring))
                    }
                    if (action is AssistantAction.TrashTasks) {
                        require(dao.getAllTasksList().none { it.id !in ids && !it.isTrashed && it.planning?.prerequisiteTaskIds.orEmpty().any { id -> id in ids } }) { "Other tasks depend on this selection. Remove their Waiting for links before deleting." }
                        try {
                            com.focusbyrj.app.util.backup.TaskRecoveryStore.write(context, changes.map { it.first to it.second })
                        } catch (error: Exception) {
                            if (error is kotlinx.coroutines.CancellationException) throw error
                            throw IllegalStateException("Couldn't create the task recovery snapshot; tasks have not been moved to Trash", error)
                        }
                    }
                    changes.forEach { dao.updateTask(it.second) }
                }
                for ((before, after, recurring) in changes) {
                    if (action is AssistantAction.CompleteTasks) {
                        CompletedTaskHistoryManager.recordCompletedTask(context, after, after.completedAt!!)
                        // Reopening/undoing a task cannot farm an additional completion reward.
                        val prefs = context.getSharedPreferences("ayva_action_receipts", Context.MODE_PRIVATE)
                        val rewarded = prefs.getStringSet("rewarded_tasks", emptySet()).orEmpty().toMutableSet()
                        if (rewarded.add(before.id.toString())) {
                            check(prefs.edit().putStringSet("rewarded_tasks", rewarded).commit())
                            FocusEconomyManager.completeTaskReward(before.title, before.isPriority, before.type)
                        }
                    } else if (before.isCompleted && !after.isCompleted) {
                        CompletedTaskHistoryManager.removeCompletedTasks(context, setOf(before.id))
                    }
                    refresh(after); recurring?.let { refresh(it) }
                }
                if (changes.isEmpty()) return@withLock AssistantActionResult("That task is already completed. I haven't awarded another reward.", ids.singleOrNull(), changed = false)
                val selected = changes.first().second
                val subject = if (changes.size == 1) "**${selected.title}**" else "${changes.size} tasks"
                val text = when (action) {
                    is AssistantAction.CompleteTasks -> "Completed $subject."
                    is AssistantAction.TrashTasks -> "Moved $subject to Trash."
                    is AssistantAction.EditTasks -> when (action.field) {
                        TaskField.DUE -> if (selected.dueDate == null) "Removed the due date from $subject." else "Rescheduled $subject to ${SmartDateParser.formatDueDate(selected.dueDate)}."
                        TaskField.PRIORITY -> if (selected.isPriority) "Marked $subject as priority." else "Removed priority from $subject."
                        TaskField.PERSISTENT -> "Persistent reminders ${if (selected.isPersistent) "on" else "off"} for $subject."
                        TaskField.REOPEN -> "Reopened $subject. Previously earned completion rewards won't be awarded again."
                        else -> "Updated $subject${if (action.field == TaskField.RECURRENCE) " — ${selected.recurrence.name.lowercase()}" else ""}."
                    }
                    else -> "Updated $subject."
                }
                AssistantActionResult(text, selected.id, undo = changes.map { undoTask(it.first, it.second, it.third) })
            }
            is AssistantAction.CreateHabit -> {
                require(action.title.isNotBlank() && action.title.length <= 200 && action.target in 1..100) { "Use a habit title and a daily target from 1–100" }
                require(app.habitRepository.getAllActiveHabits().first().none { it.title.equals(action.title, true) }) { "That habit already exists" }
                val id = app.habitRepository.insertHabit(Habit(title = action.title, targetPerDay = action.target))
                HabitAlarmScheduler.rescheduleAllHabits(context)
                AssistantActionResult("Created habit **${action.title}** — target ${action.target} per day.", habitId = id)
            }
            is AssistantAction.LogHabit -> {
                require(action.count in 1..100) { "Log between 1 and 100 repetitions" }
                val habit = app.habitRepository.getHabitById(action.id) ?: error("That habit no longer exists")
                require(!habit.isArchived && !habit.isPaused) { "Resume this habit before logging progress" }
                val date = app.habitRepository.getTodayDateString()
                val todayLog = app.database.habitDao().getLogForHabitAndDate(habit.id, date)
                val target = todayLog?.targetCount?.takeIf { it > 0 } ?: habit.targetPerDay
                val old = todayLog?.completedCount ?: 0
                if (old >= target) return@withLock AssistantActionResult("**${habit.title}** is already complete for today ($old/${target}).", habitId = habit.id, changed = false)
                val count = (old + action.count).coerceAtMost(target)
                val log = app.habitRepository.setHabitProgress(habit.id, count)
                HabitAlarmScheduler.scheduleHabitReminder(context, habit, log.lastCompletedTimestamp, isGoalCompletedToday = count >= target)
                val rewardPrefs = context.getSharedPreferences("ayva_action_receipts", Context.MODE_PRIVATE)
                val rewarded = if (rewardPrefs.getString("habit_reward_date", "") == date) rewardPrefs.getStringSet("habit_reward_steps", emptySet()).orEmpty().toMutableSet() else mutableSetOf()
                val newSteps = ((old + 1)..count).filter { rewarded.add("${habit.id}:$it") }
                check(rewardPrefs.edit().putString("habit_reward_date", date).putStringSet("habit_reward_steps", rewarded).commit())
                for (step in newSteps) FocusEconomyManager.addRewards(if (step == target) 25 else 10, if (step == target) 15 else 5)
                val inverse = JSONObject().put("kind", "habit").put("id", habit.id).put("date", date).put("before", old).put("after", log.completedCount)
                AssistantActionResult("Logged **${habit.title}** — ${log.completedCount}/${log.targetCount} today${if (log.completedCount >= log.targetCount) "; daily target reached" else ""}.", habitId = habit.id, undo = listOf(inverse))
            }
            is AssistantAction.EditHabit -> {
                val updated = app.database.withTransaction {
                    val habit = app.database.habitDao().getHabitById(action.id) ?: error("That habit no longer exists")
                    require(habit == action.expected && habit.id == action.expected.id) { "That habit changed. Please review the edit again" }
                    require(!habit.isArchived || action.field == HabitField.ARCHIVED && action.value == "false") { "Restore that habit before editing it" }
                    val value = AssistantHabitEditing.value(action.field, action.value) ?: error(AssistantHabitEditing.hint(action.field))
                    val after = when (action.field) {
                        HabitField.INTERVAL -> habit.copy(type = com.focusbyrj.app.data.HabitType.INTERVAL_WINDOW, intervalHours = value.toInt() / 60, intervalMinutes = value.toInt() % 60)
                        HabitField.WINDOW -> value.split(',').map(String::toInt).let { habit.copy(type = com.focusbyrj.app.data.HabitType.INTERVAL_WINDOW, windowStartHour = it[0] / 60, windowStartMinute = it[0] % 60, windowEndHour = it[1] / 60, windowEndMinute = it[1] % 60) }
                        HabitField.REMINDER_TIME -> habit.copy(type = com.focusbyrj.app.data.HabitType.ONCE_DAILY, fixedReminderHour = value.removePrefix("@").toInt() / 60, fixedReminderMinute = value.removePrefix("@").toInt() % 60)
                        HabitField.TITLE -> habit.copy(title = value)
                        HabitField.TARGET -> habit.copy(targetPerDay = value.toInt())
                        HabitField.REMINDER -> habit.copy(isReminderEnabled = value.toBooleanStrict())
                        HabitField.PAUSED -> habit.withPaused(value.toBooleanStrict())
                        HabitField.ARCHIVED -> habit.copy(isArchived = value.toBooleanStrict())
                    }
                    if (!after.isArchived) require(app.database.habitDao().getAllHabitsSync().none {
                        it.id != after.id && !it.isArchived && it.title.equals(after.title, true)
                    }) { "An active habit already uses that name" }
                    app.database.habitDao().updateHabit(after)
                    after
                }
                refreshHabit(context, app, updated)
                val detail = when (action.field) {
                    HabitField.INTERVAL, HabitField.WINDOW, HabitField.REMINDER_TIME -> "Updated the reminder schedule for **${updated.title}**. Reminders remain ${if (updated.isReminderEnabled) "enabled" else "disabled"}."
                    HabitField.TITLE -> "Renamed habit to **${updated.title}**."
                    HabitField.TARGET -> "Set **${updated.title}** to ${updated.targetPerDay} per day. Any existing daily log keeps its recorded target."
                    HabitField.REMINDER -> "Reminders ${if (updated.isReminderEnabled) "on" else "off"} for **${updated.title}**."
                    HabitField.PAUSED -> "${if (updated.isPaused) "Paused" else "Resumed"} habit **${updated.title}**. Its history is preserved."
                    HabitField.ARCHIVED -> "${if (updated.isArchived) "Archived" else "Restored"} habit **${updated.title}**. Its history is preserved."
                }
                val inverse = JSONObject().put("kind", "habit_edit").put("before", HabitJson.encode(action.expected)).put("after", HabitJson.encode(updated))
                AssistantActionResult(detail, habitId = updated.id, undo = listOf(inverse), changed = updated != action.expected)
            }
            is AssistantAction.SetRoutine -> {
                val dao = app.database.scheduleDao()
                val pair = app.database.withTransaction {
                    val schedule = dao.getScheduleById(action.id) ?: error("That routine no longer exists")
                    expected["routine:${action.id}"]?.let { require(schedule == RoutineJson.decode(JSONObject(it))) { "That reviewed routine changed. Review it again" } }
                    if (!action.enabled) require(canDisableRoutine(context, schedule)) { "An active strict routine or focus session cannot be stopped from chat" }
                    val updated = schedule.copy(isEnabled = action.enabled)
                    dao.insertSchedule(updated)
                    schedule to updated
                }
                val (schedule, updated) = pair
                AssistantActionResult("**${schedule.name}** ${if (!action.enabled) "disabled" else if (updated.isActiveAt()) "enabled and active now" else "enabled for its scheduled window"}.",
                    undo = listOf(JSONObject().put("kind", "routine_edit").put("before", RoutineJson.encode(schedule)).put("after", RoutineJson.encode(updated))))
            }
            is AssistantAction.SetPreference -> {
                val capability = AssistantCapabilities.setting(action.key)
                val spec = capability.spec
                val old = capability.current(context)
                expected["pref:${action.key}"]?.let { require(it == old) { "That setting changed. Review it again" } }
                AssistantPreferences.apply(context, action.key, action.type, action.value)
                AssistantActionResult("${spec.title}: **${AssistantPreferences.display(spec, action.value)}**.", undo = listOf(JSONObject().put("kind", "pref").put("key", action.key).put("type", action.type).put("before", old).put("after", action.value)))
            }
            is AssistantAction.StartFocus -> {
                val task = action.taskId?.let { id -> dao.getTaskById(id)?.takeIf { !it.isCompleted && !it.isTrashed && it.deletedAt == null }
                    ?: error("That focus task is no longer available") }
                FocusSessionManager.start(context, action.minutes, task?.id)
                AssistantActionResult("Started a **${action.minutes}-minute focus session**${task?.let { " for **${it.title}**" }.orEmpty()}. The countdown is running; your existing app restrictions remain in effect.")
            }
            AssistantAction.StopFocus -> {
                FocusSessionManager.stop(context)
                AssistantActionResult("Focus session stopped. Your saved app restrictions are still configured.")
            }
        }
    }

    suspend fun undo(context: Context, records: List<JSONObject>): String = mutex.withLock {
        require(records.isNotEmpty()) { "There's nothing to undo" }
        val app = context.applicationContext as FocusApplication
        val tasks = records.filter { it.getString("kind") == "task" }
        // Validate every target before changing any row; never overwrite a newer edit.
        app.database.withTransaction {
            for (record in tasks) {
                val after = TaskJson.decode(record.getJSONObject("after"))
                require(app.database.taskDao().getTaskById(after.id) == after) { "That item changed since my action. I won't overwrite the newer version." }
                if (record.has("recurring")) {
                    val recurring = TaskJson.decode(record.getJSONObject("recurring"))
                    require(app.database.taskDao().getTaskById(recurring.id) == recurring) { "The next recurring task has changed; undo it from the task screen." }
                }
            }
            for (record in tasks.asReversed()) {
                val after = TaskJson.decode(record.getJSONObject("after"))
                val restored = if (record.isNull("before")) after.copy(isTrashed = true, trashedAt = System.currentTimeMillis(), updatedAt = maxOf(System.currentTimeMillis(), after.updatedAt + 1))
                    else TaskJson.decode(record.getJSONObject("before")).copy(updatedAt = maxOf(System.currentTimeMillis(), after.updatedAt + 1))
                app.database.taskDao().updateTask(restored)
                if (after.isCompleted && !restored.isCompleted) CompletedTaskHistoryManager.removeCompletedTasks(context, setOf(after.id))
                TaskReminderHelper.cancelReminder(context, after)
                if (!restored.isCompleted && !restored.isTrashed) TaskReminderHelper.scheduleReminder(context, restored)
                if (record.has("recurring")) {
                    val recurring = TaskJson.decode(record.getJSONObject("recurring"))
                    app.database.taskDao().updateTask(recurring.copy(isTrashed = true, trashedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
                    TaskReminderHelper.cancelReminder(context, recurring)
                }
            }
        }
        for (record in records.filter { it.getString("kind") != "task" }.asReversed()) {
            when (record.getString("kind")) {
                "routine_edit", "app_rule", "app_rules", "vocab_bookmark" -> AssistantFeatureActions.undo(context, app, record)
                "habit" -> {
                    val date = app.habitRepository.getTodayDateString()
                    require(date == record.getString("date")) { "Yesterday's habit log cannot be undone as today's progress" }
                    val current = app.database.habitDao().getLogForHabitAndDate(record.getLong("id"), date)
                    require(current?.completedCount == record.getInt("after")) { "Habit progress changed since my action" }
                    val log = app.habitRepository.setHabitProgress(record.getLong("id"), record.getInt("before"))
                    app.habitRepository.getHabitById(record.getLong("id"))?.let { habit ->
                        HabitAlarmScheduler.scheduleHabitReminder(context, habit, log.lastCompletedTimestamp, isGoalCompletedToday = log.completedCount >= log.targetCount)
                    }
                }
                "habit_edit" -> {
                    val after = HabitJson.decode(record.getJSONObject("after"))
                    val before = HabitJson.decode(record.getJSONObject("before"))
                    app.database.withTransaction {
                        require(app.database.habitDao().getHabitById(after.id) == after) { "That habit changed since my action. I won't overwrite the newer version." }
                        if (!before.isArchived) require(app.database.habitDao().getAllHabitsSync().none {
                            it.id != before.id && !it.isArchived && it.title.equals(before.title, true)
                        }) { "Another active habit now uses the previous name" }
                        app.database.habitDao().updateHabit(before)
                    }
                    refreshHabit(context, app, before)
                }
                "pref" -> {
                    val spec = AssistantPreferences.specs.first { it.key == record.getString("key") }
                    require(AssistantPreferences.read(context, spec) == record.getString("after")) { "That setting changed since my action" }
                    AssistantPreferences.apply(context, spec.key, spec.type, record.getString("before"))
                }
            }
        }
        TodoWidgetProvider.updateAllWidgets(context)
        val kinds = records.map { it.getString("kind") }.toSet()
        when (kinds.singleOrNull()) {
            "pref" -> records.singleOrNull()?.let { record ->
                val spec = AssistantPreferences.specs.first { it.key == record.getString("key") }
                "Restored **${spec.title}** to **${AssistantPreferences.display(spec, record.getString("before"))}**."
            } ?: "Restored the previous settings."
            "routine_edit" -> "Restored the previous routine settings."
            "app_rule", "app_rules" -> "Restored the previous manual app lock settings."
            "vocab_bookmark" -> "Restored the previous vocabulary bookmark."
            "habit_edit" -> "Restored the previous habit settings."
            else -> if (kinds.any { it == "task" || it == "habit" })
                "Undid my last reversible change. Previously earned rewards are kept; repeating the same completion won't earn them again."
            else "Undid my last reversible change."
        }
    }

    private suspend fun refreshHabit(context: Context, app: FocusApplication, habit: Habit) {
        val log = app.database.habitDao().getLogForHabitAndDate(habit.id, app.habitRepository.getTodayDateString())
        HabitAlarmScheduler.scheduleHabitReminder(context, habit, log?.lastCompletedTimestamp,
            isGoalCompletedToday = log != null && log.completedCount >= log.targetCount)
    }
}
