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

    fun addHabit(habit: Habit) {
        viewModelScope.launch {
            val id = repository.insertHabit(habit)
            val created = habit.copy(id = id)
            HabitAlarmScheduler.scheduleHabitReminder(getApplication(), created)
        }
    }

    fun updateHabit(habit: Habit) {
        viewModelScope.launch {
            repository.updateHabit(habit)
            HabitAlarmScheduler.scheduleHabitReminder(getApplication(), habit)
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            HabitAlarmScheduler.cancelHabitReminder(getApplication(), habit.id)
            repository.deleteHabit(habit)
        }
    }

    fun incrementProgress(habit: Habit) {
        viewModelScope.launch {
            val updatedLog = repository.incrementHabitProgress(habit.id)
            val isGoalMet = updatedLog.completedCount >= habit.targetPerDay
            val xpReward = if (isGoalMet) 25 else 10
            val goldReward = if (isGoalMet) 15 else 5
            com.focusbyrj.app.util.FocusEconomyManager.addRewards(xpReward, goldReward)

            // Reschedule interval if applicable
            HabitAlarmScheduler.scheduleHabitReminder(
                getApplication(),
                habit,
                updatedLog.lastCompletedTimestamp,
                isGoalCompletedToday = isGoalMet
            )
        }
    }

    fun decrementProgress(habit: Habit) {
        viewModelScope.launch {
            repository.decrementHabitProgress(habit.id)
        }
    }

    fun setProgress(habit: Habit, count: Int) {
        viewModelScope.launch {
            repository.setHabitProgress(habit.id, count)
        }
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
