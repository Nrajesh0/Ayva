/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitRepository
import com.focusbyrj.app.data.HabitWithProgress
import com.focusbyrj.app.util.HabitAlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HabitViewModel(
    private val repository: HabitRepository,
    application: Application
) : AndroidViewModel(application) {

    val habitsWithProgress: StateFlow<List<HabitWithProgress>> =
        repository.activeHabitsWithProgress.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addHabit(habit: Habit, onStored: () -> Unit = {}) = saveHabit(habit, true, onStored)

    fun updateHabit(habit: Habit, onStored: () -> Unit = {}) = saveHabit(habit, false, onStored)

    private fun saveHabit(habit: Habit, creating: Boolean, onStored: () -> Unit) = viewModelScope.launch {
        val stored = try {
            if (creating) habit.copy(id = repository.insertHabit(habit))
            else {
                repository.updateHabit(habit)
                habit
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = if (error is IllegalArgumentException) {
                error.message ?: "Check the habit options. Your draft is kept."
            } else "Could not save habit. Your draft is kept; try again."
            android.widget.Toast.makeText(getApplication(), message, android.widget.Toast.LENGTH_LONG).show()
            return@launch
        }
        onStored()
        try {
            HabitAlarmScheduler.scheduleHabitReminder(getApplication(), stored)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            android.widget.Toast.makeText(getApplication(),
                "Habit saved. The reminder could not be scheduled; check reminder access in Settings.",
                android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun mutate(action: suspend () -> Unit) = viewModelScope.launch {
        try { action() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            android.widget.Toast.makeText(getApplication(), error.message ?: "Could not update habit. Try again.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun refreshReminder(habit: Habit, lastCompleted: Long? = null, completed: Boolean = false) {
        try { HabitAlarmScheduler.scheduleHabitReminder(getApplication(), habit, lastCompleted, isGoalCompletedToday = completed) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            android.widget.Toast.makeText(getApplication(), "Habit saved. Reminder refresh failed; check reminder access in Settings.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun setPaused(habit: Habit, paused: Boolean) = mutate {
        val updated = repository.setPaused(habit.id, paused) ?: error("This habit no longer exists.")
        try {
            val completed = repository.isCompleteToday(updated, repository.getTodayDateString())
            refreshReminder(updated, completed = completed)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            android.widget.Toast.makeText(getApplication(), "Habit updated. Reminder refresh failed; check reminder access in Settings.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun deleteHabit(habit: Habit) = mutate {
        repository.deleteHabit(habit)
        try { HabitAlarmScheduler.cancelHabitReminder(getApplication(), habit.id) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            android.widget.Toast.makeText(getApplication(), "Habit deleted. Reminder cleanup will retry when the app opens.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun incrementProgress(habit: Habit, onDone: () -> Unit = {}) {
        if (habit.isPaused) { onDone(); return }
        mutate {
            try {
                var log: com.focusbyrj.app.data.HabitLog? = null
                try {
                    com.focusbyrj.app.util.backup.MaintenanceGate.shared {
                        log = repository.incrementHabitProgress(habit.id)
                        com.focusbyrj.app.util.RewardDelivery.drain(getApplication())
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) {
                    if (log == null) throw error
                    android.widget.Toast.makeText(getApplication(), "Progress saved. Rewards are pending and will retry automatically.", android.widget.Toast.LENGTH_LONG).show()
                }
                val stored = log ?: return@mutate
                refreshReminder(habit, stored.lastCompletedTimestamp, stored.completedCount >= stored.targetCount)
            } finally { onDone() }
        }
    }

    fun decrementProgress(habit: Habit) {
        if (habit.isPaused) return
        mutate {
            val log = repository.decrementHabitProgress(habit.id)
            refreshReminder(habit, log?.lastCompletedTimestamp, (log?.completedCount ?: 0) >= (log?.targetCount ?: habit.targetPerDay))
        }
    }

    fun setProgress(habit: Habit, count: Int) = mutate {
        val log = repository.setHabitProgress(habit.id, count)
        refreshReminder(habit, log.lastCompletedTimestamp, log.completedCount >= log.targetCount)
    }

}

class HabitViewModelFactory(
    private val repository: HabitRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HabitViewModel::class.java)) {
            return HabitViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
