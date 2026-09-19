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

package com.focusbyrj.app.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.focusbyrj.app.data.Habit
import java.util.ArrayDeque

/**
 * Queue item for floating on-screen reminder overlays.
 */
sealed class OverlayQueueItem {
    data class HabitItem(
        val habit: Habit,
        val completedCount: Int,
        val targetCount: Int,
        val currentStreak: Int = 0
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
 * Production-grade Unified Overlay Coordinator.
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
    private val handler = Handler(Looper.getMainLooper())

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

    private fun handleEnqueue(context: Context, item: OverlayQueueItem) {
        if (isAnyOverlayShowing) {
            // Prevent duplicate entries in queue
            val isDuplicate = queue.any { existing ->
                when {
                    existing is OverlayQueueItem.HabitItem && item is OverlayQueueItem.HabitItem ->
                        existing.habit.id == item.habit.id
                    existing is OverlayQueueItem.TaskItem && item is OverlayQueueItem.TaskItem ->
                        existing.taskId == item.taskId
                    else -> false
                }
            } || when (val active = currentActiveItem) {
                is OverlayQueueItem.HabitItem -> item is OverlayQueueItem.HabitItem && active.habit.id == item.habit.id
                is OverlayQueueItem.TaskItem -> item is OverlayQueueItem.TaskItem && active.taskId == item.taskId
                null -> false
            }

            if (!isDuplicate) {
                queue.addLast(item)
                Log.d(TAG, "Overlay queued. Pending queue count: ${queue.size}")
            }
        } else {
            presentItem(context, item)
        }
    }

    private fun presentItem(context: Context, item: OverlayQueueItem) {
        currentActiveItem = item
        when (item) {
            is OverlayQueueItem.HabitItem -> {
                HabitFloatingOverlayManager.showHabitOverlayDirect(
                    context = context,
                    habit = item.habit,
                    completedCount = item.completedCount,
                    targetCount = item.targetCount,
                    currentStreak = item.currentStreak
                )
            }
            is OverlayQueueItem.TaskItem -> {
                TaskReminderOverlayManager.showReminderOverlayDirect(
                    context = context,
                    taskId = item.taskId,
                    taskTitle = item.taskTitle,
                    taskDetails = item.taskDetails,
                    taskDueDate = item.taskDueDate,
                    taskTypeStr = item.taskTypeStr,
                    taskRecurrenceStr = item.taskRecurrenceStr,
                    isPersistent = item.isPersistent,
                    openRescheduleInitially = item.openRescheduleInitially
                )
            }
        }

        // Anti-deadlock watchdog: if overlay was rejected, failed, or dismissed immediately without callback
        handler.postDelayed({
            if (currentActiveItem == item && !HabitFloatingOverlayManager.isShowing && !TaskReminderOverlayManager.isShowing) {
                Log.w(TAG, "Watchdog detected overlay not showing after presentation; advancing queue.")
                onOverlayDismissed(context)
            }
        }, 1500L)
    }

    fun onOverlayDismissed(context: Context) {
        handler.postDelayed({
            currentActiveItem = null
            if (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                Log.d(TAG, "Displaying next queued overlay. Remaining: ${queue.size}")
                presentItem(context, next)
            }
        }, 280L)
    }

    fun clearQueue() {
        handler.post {
            queue.clear()
            currentActiveItem = null
        }
    }
}
