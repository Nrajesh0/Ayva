/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.frequency
import com.focusbyrj.app.FocusApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.ArrayDeque

/**
 * Queue item for floating on-screen reminder overlays.
 */
sealed class OverlayQueueItem {
    internal data class RestoredItem(val reminder: PendingOverlayReminder) : OverlayQueueItem()
    data class HabitItem(
        val habit: Habit,
        val completedCount: Int,
        val targetCount: Int,
        val currentStreak: Int = 0,
        val reminderDate: String = LocalDate.now().toString()
    ) : OverlayQueueItem()

    data class TaskItem(
        val taskId: Long,
        val taskTitle: String,
        val taskDetails: String,
        val taskDueDate: Long,
        val taskTypeStr: String,
        val taskRecurrenceStr: String,
        val isPersistent: Boolean,
        val openRescheduleInitially: Boolean
    ) : OverlayQueueItem()
}

/**
 * Serializes task and habit reminder windows on the main thread.
 *
 * Ensures that:
 * 1. Only ONE floating window is active on screen at a time.
 * 2. Simultaneous or overlapping reminders (Habits or Tasks) enter a smooth FIFO queue.
 * 3. Once the user completes or dismisses the current alert, the next queued alert
 *    animates in seamlessly with a subtle transition delay.
 * 4. Duplicate alerts for the same item are prevented from spamming the queue.
 */
object UnifiedOverlayCoordinator {
    private const val TAG = "UnifiedOverlayCoord"
    private val queue = ArrayDeque<OverlayQueueItem>()
    private var currentActiveItem: OverlayQueueItem? = null
    private var nextPresentationId = 0L
    private var activePresentationId: Long? = null
    private val handler = Handler(Looper.getMainLooper())
    private var activeWatchdogRunnable: Runnable? = null
    private var pendingAdvanceRunnable: Runnable? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var store: ReminderOverlayStore? = null
    private var storeContext: Context? = null
    private val deferredFailures = mutableListOf<PendingOverlayReminder>()

    private fun OverlayQueueItem.record(): PendingOverlayReminder = when (this) {
        is OverlayQueueItem.TaskItem -> PendingOverlayReminder("task", taskId, taskDueDate,
            openReschedule = openRescheduleInitially)
        is OverlayQueueItem.HabitItem -> PendingOverlayReminder("habit", habit.id, reminderDate = reminderDate)
        is OverlayQueueItem.RestoredItem -> reminder
    }

    private fun savePending() {
        // A dismissed active item remains reserved during the animation gap but must
        // no longer be replayed after a process restart.
        val active = if (pendingAdvanceRunnable == null) listOfNotNull(currentActiveItem) else emptyList()
        store?.write(((active + queue).map { it.record() } + deferredFailures).distinctBy { it.key })
    }

    private fun initialize(context: Context) {
        if (store != null) return
        storeContext = context
        store = ReminderOverlayStore(context)
        store!!.read().forEach { queue.addLast(OverlayQueueItem.RestoredItem(it)) }
    }

    fun restorePending(context: Context) {
        val appContext = context.applicationContext ?: context
        handler.post {
            initialize(appContext)
            if (!isAnyOverlayShowing && pendingAdvanceRunnable == null && queue.isNotEmpty()) {
                presentItem(appContext, queue.removeFirst())
            }
        }
    }

    internal fun ownsPresentation(presentationId: Long): Boolean =
        activePresentationId == presentationId && pendingAdvanceRunnable == null

    internal fun requestsReschedule(presentationId: Long): Boolean =
        ownsPresentation(presentationId) && currentActiveItem?.record()?.openReschedule == true

    val isAnyOverlayShowing: Boolean
        get() = currentActiveItem != null || HabitFloatingOverlayManager.isShowing || TaskReminderOverlayManager.isShowing

    fun enqueueHabit(
        context: Context,
        habit: Habit,
        completedCount: Int,
        targetCount: Int,
        currentStreak: Int = 0
    ) {
        val appContext = context.applicationContext ?: context
        handler.post {
            val item = OverlayQueueItem.HabitItem(habit, completedCount, targetCount, currentStreak)
            handleEnqueue(appContext, item)
        }
    }

    fun enqueueTask(
        context: Context,
        taskId: Long,
        taskTitle: String,
        taskDetails: String,
        taskDueDate: Long,
        taskTypeStr: String,
        taskRecurrenceStr: String,
        isPersistent: Boolean,
        openRescheduleInitially: Boolean = false
    ) {
        val appContext = context.applicationContext ?: context
        handler.post {
            val item = OverlayQueueItem.TaskItem(
                taskId = taskId,
                taskTitle = taskTitle,
                taskDetails = taskDetails,
                taskDueDate = taskDueDate,
                taskTypeStr = taskTypeStr,
                taskRecurrenceStr = taskRecurrenceStr,
                isPersistent = isPersistent,
                openRescheduleInitially = openRescheduleInitially
            )
            handleEnqueue(appContext, item)
        }
    }

    fun removeQueuedHabit(habitId: Long) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            queue.removeAll { it.record().key == "habit:$habitId" }
            deferredFailures.removeAll { it.key == "habit:$habitId" }
            cancelActiveIfMatching("habit:$habitId")
            savePending()
        }
    }

    private fun cancelActiveIfMatching(key: String) {
        // An attached window releases its slot from its real removal callback.
        // Habit exit duration follows Android's animation scale, so a fixed delay
        // from the cancellation request could otherwise open the next window early.
        if (currentActiveItem?.record()?.key == key &&
            !HabitFloatingOverlayManager.isShowing && !TaskReminderOverlayManager.isShowing) {
            storeContext?.let { onOverlayDismissed(it, activePresentationId) }
        }
    }

    private fun handleEnqueue(context: Context, item: OverlayQueueItem) {
        initialize(context)
        // Always append before presenting, including during the dismissal transition.
        // Never evict reminders: a burst must not silently discard older entries.
        val duplicate = (listOfNotNull(currentActiveItem) + queue).any { it.record().key == item.record().key }
        if (duplicate) {
            // Notification actions may request the rescheduler for a reminder that
            // is already active or waiting. Preserve FIFO and honor that intent.
            if (item is OverlayQueueItem.TaskItem && item.openRescheduleInitially) {
                if (currentActiveItem?.record()?.key == item.record().key) {
                    currentActiveItem = item
                    TaskReminderOverlayManager.openReschedule(item.taskId)
                } else {
                    val updated = queue.map { if (it.record().key == item.record().key) item else it }
                    queue.clear()
                    queue.addAll(updated)
                }
                savePending()
            }
            return
        }
        deferredFailures.removeAll { it.key == item.record().key }
        queue.addLast(item)
        savePending()
        if (!isAnyOverlayShowing && pendingAdvanceRunnable == null) {
            presentItem(context, queue.removeFirst())
        }
    }

    fun removeQueuedTask(taskId: Long) {
        handler.post {
            queue.removeAll { it.record().key == "task:$taskId" }
            deferredFailures.removeAll { it.key == "task:$taskId" }
            cancelActiveIfMatching("task:$taskId")
            savePending()
        }
    }

    private fun presentItem(context: Context, item: OverlayQueueItem) {
        currentActiveItem = item
        val presentationId = ++nextPresentationId
        activePresentationId = presentationId
        savePending()
        scope.launch {
            var validationFailed = false
            val refreshed = try {
                if (context is FocusApplication || context.applicationContext is FocusApplication) {
                    withContext(Dispatchers.IO) { refreshItem(context, item) }
                } else refreshItem(context, item)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.e(TAG, "Cannot validate pending reminder; leaving it available for retry", error)
                // Keep the original record on disk. Other reminders must still run.
                // Releasing through the normal dismissal path would erase this record.
                validationFailed = true
                null
            }
            if (!ownsPresentation(presentationId) || pendingAdvanceRunnable != null) return@launch
            if (refreshed == null) {
                if (validationFailed) deferredFailures.add(item.record())
                onOverlayDismissed(context, presentationId)
                return@launch
            }
            val ready = if (refreshed is OverlayQueueItem.TaskItem) refreshed.copy(
                openRescheduleInitially = refreshed.openRescheduleInitially || requestsReschedule(presentationId)
            ) else refreshed
            currentActiveItem = ready
            when (ready) {
                is OverlayQueueItem.HabitItem -> HabitFloatingOverlayManager.showHabitOverlayDirect(
                    context, presentationId, ready.habit, ready.completedCount,
                    ready.targetCount, ready.currentStreak)
                is OverlayQueueItem.TaskItem -> TaskReminderOverlayManager.showReminderOverlayDirect(
                    context, presentationId, ready.taskId, ready.taskTitle, ready.taskDetails,
                    ready.taskDueDate, ready.taskTypeStr, ready.taskRecurrenceStr,
                    ready.isPersistent, ready.openRescheduleInitially)
                is OverlayQueueItem.RestoredItem -> error("Unresolved reminder")
            }
            startWatchdog(context, presentationId)
        }
    }

    /** Recheck when a queued item reaches the front: it may have changed while waiting. */
    internal suspend fun refreshItem(context: Context, item: OverlayQueueItem): OverlayQueueItem? {
        val app = (context as? FocusApplication) ?: context.applicationContext as? FocusApplication
            ?: return item.takeUnless { it is OverlayQueueItem.RestoredItem }
        val record = item.record()
        if (record.kind == "task") {
            val task = app.database.taskDao().getTaskById(record.id) ?: return null
            val due = task.dueDate ?: return null
            if (task.isCompleted || task.isTrashed || task.deletedAt != null ||
                due != record.dueDate || due > System.currentTimeMillis() + 60_000L) return null
            return OverlayQueueItem.TaskItem(task.id, task.title.trim().ifEmpty { "Task Reminder" },
                task.details, due, task.type.name, task.recurrence.name, task.isPersistent, record.openReschedule)
        }
        if (record.reminderDate != LocalDate.now().toString()) return null
        val habit = app.habitRepository.getHabitById(record.id) ?: return null
        if (habit.isArchived || habit.isPaused || !habit.isReminderEnabled ||
            !habit.frequency.scheduled(LocalDate.now())) return null
        val todayLog = app.database.habitDao().getLogForHabitAndDate(habit.id, record.reminderDate)
        val completed = todayLog?.completedCount ?: 0
        if (completed >= habit.targetPerDay) return null
        if (habit.frequency.mode == "WEEKLY" &&
            habit.frequency.weekCount(app.database.habitDao().getAllLogsForHabitSync(habit.id)) >= habit.frequency.weeklyTarget) return null
        val streak = if (habit.frequency.mode == "WEEKLY") 0 else
            app.habitRepository.calculateStreaks(habit.id, habit.targetPerDay, todayLog).first
        return OverlayQueueItem.HabitItem(habit, completed, habit.targetPerDay, streak, record.reminderDate)
    }

    private fun startWatchdog(context: Context, presentationId: Long) {
        // Anti-deadlock watchdog: if overlay was rejected, failed, or dismissed immediately without callback
        activeWatchdogRunnable?.let { handler.removeCallbacks(it) }
        val watchdog = Runnable {
            if (activePresentationId == presentationId && !HabitFloatingOverlayManager.isShowing && !TaskReminderOverlayManager.isShowing) {
                Log.w(TAG, "Watchdog detected overlay not showing after presentation; advancing queue.")
                onOverlayFailed(context, presentationId)
            }
        }
        activeWatchdogRunnable = watchdog
        handler.postDelayed(watchdog, 1500L)
    }

    internal fun onOverlayFailed(context: Context, presentationId: Long) {
        if (!ownsPresentation(presentationId) || pendingAdvanceRunnable != null) return
        currentActiveItem?.let { deferredFailures.add(it.record()) }
        onOverlayDismissed(context, presentationId)
    }

    // Every production callback carries its presentation ID. A late callback from an
    // old window must never dismiss a different reminder that now owns the slot.
    @Deprecated("Pass the window's presentation ID to reject stale callbacks")
    fun onOverlayDismissed(context: Context) = onOverlayDismissed(context, activePresentationId)

    fun onOverlayDismissed(context: Context, presentationId: Long?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { onOverlayDismissed(context, presentationId) }
            return
        }
        if (presentationId == null || presentationId != activePresentationId || pendingAdvanceRunnable != null) return
        activeWatchdogRunnable?.let { handler.removeCallbacks(it) }
        activeWatchdogRunnable = null
        val advance = Runnable {
            pendingAdvanceRunnable = null
            currentActiveItem = null
            activePresentationId = null
            savePending()
            if (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                Log.d(TAG, "Displaying next queued overlay. Remaining: ${queue.size}")
                presentItem(storeContext ?: context, next)
            }
        }
        pendingAdvanceRunnable = advance
        handler.postDelayed(advance, 280L)
        savePending()
    }

    fun clearQueue() {
        val action = Runnable {
            TaskReminderOverlayManager.clearOverlay()
            HabitFloatingOverlayManager.clearOverlay()
            activeWatchdogRunnable?.let { handler.removeCallbacks(it) }
            activeWatchdogRunnable = null
            pendingAdvanceRunnable?.let { handler.removeCallbacks(it) }
            pendingAdvanceRunnable = null
            queue.clear()
            deferredFailures.clear()
            currentActiveItem = null
            activePresentationId = null
            savePending()
            store = null
            storeContext = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            handler.post(action)
        }
    }
}
