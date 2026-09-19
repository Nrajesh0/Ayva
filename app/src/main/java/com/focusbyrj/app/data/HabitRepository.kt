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

package com.focusbyrj.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class HabitRepository(private val habitDao: HabitDao) {

    private val dateFormatLocal = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    private fun getDateFormat(): SimpleDateFormat = dateFormatLocal.get() ?: SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun getTodayDateString(): String = getDateFormat().format(Date())

    fun getAllActiveHabits(): Flow<List<Habit>> = habitDao.getAllActiveHabits()

    fun getTodayLogs(): Flow<List<HabitLog>> = habitDao.getLogsForDate(getTodayDateString())

    private fun getPast7Dates(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val dayLetters = listOf("S", "M", "T", "W", "T", "F", "S")
        val cal = Calendar.getInstance()
        val df = getDateFormat()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        for (i in 0 until 7) {
            val dateStr = df.format(cal.time)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday, ...
            val letter = dayLetters[(dayOfWeek - 1).coerceIn(0, 6)]
            list.add(Pair(dateStr, letter))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return list
    }

    private val todayDateTicker: Flow<String> = flow {
        while (true) {
            emit(getTodayDateString())
            kotlinx.coroutines.delay(60000)
        }
    }.distinctUntilChanged()

    /**
     * Flow that pairs each active habit with its progress for today,
     * computing current streak, best streak, 7-day history, and total completions.
     * Re-evaluates automatically at midnight / date change via todayDateTicker.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeHabitsWithProgress: Flow<List<HabitWithProgress>> =
        todayDateTicker.flatMapLatest { todayStr ->
            combine(
                habitDao.getAllActiveHabits(),
                habitDao.getLogsForDate(todayStr)
            ) { habits, todayLogs ->
                val logMap = todayLogs.associateBy { it.habitId }
                val past7 = getPast7Dates()
                val allLogsList = habitDao.getAllLogsSync()
                val allLogsByHabit = allLogsList.groupBy { it.habitId }

                habits.map { habit ->
                    val todayLog = logMap[habit.id]
                    val allLogs = allLogsByHabit[habit.id] ?: emptyList()
                    val allLogsMap = allLogs.associateBy { it.date }

                    val (currentStreak, bestStreak) = calculateStreaksFromLogs(
                        habit.id,
                        habit.targetPerDay,
                        todayLog,
                        allLogs
                    )

                    val weeklyHistory = past7.map { (dateStr, letter) ->
                        val log = if (dateStr == todayStr) todayLog else allLogsMap[dateStr]
                        val count = log?.completedCount ?: 0
                        val target = log?.targetCount ?: habit.targetPerDay
                        HabitDaySummary(
                            date = dateStr,
                            dayOfWeekLetter = letter,
                            completedCount = count,
                            targetCount = target,
                            isCompleted = count >= target,
                            isToday = dateStr == todayStr
                        )
                    }

                    val totalCompletions = allLogs.sumOf { it.completedCount } +
                        (if (todayLog != null && !allLogsMap.containsKey(todayStr)) todayLog.completedCount else 0)

                    HabitWithProgress(
                        habit = habit,
                        todayLog = todayLog,
                        currentStreak = currentStreak,
                        bestStreak = bestStreak,
                        weeklyHistory = weeklyHistory,
                        totalCompletionsAllTime = totalCompletions,
                        streakFrozenToday = false
                    )
                }
            }
        }.flowOn(Dispatchers.IO)

    suspend fun getHabitById(habitId: Long): Habit? = withContext(Dispatchers.IO) {
        habitDao.getHabitById(habitId)
    }

    suspend fun insertHabit(habit: Habit): Long = withContext(Dispatchers.IO) {
        habitDao.insertHabit(habit)
    }

    suspend fun updateHabit(habit: Habit) = withContext(Dispatchers.IO) {
        habitDao.updateHabit(habit)
    }

    suspend fun deleteHabit(habit: Habit) = withContext(Dispatchers.IO) {
        habitDao.deleteLogsForHabit(habit.id)
        habitDao.deleteHabit(habit)
    }

    suspend fun incrementHabitProgress(habitId: Long): HabitLog = withContext(Dispatchers.IO) {
        val today = getTodayDateString()
        val habit = habitDao.getHabitById(habitId) ?: throw IllegalArgumentException("Habit not found")
        val existingLog = habitDao.getLogForHabitAndDate(habitId, today)
        val now = System.currentTimeMillis()

        val updatedLog = if (existingLog != null) {
            existingLog.copy(
                completedCount = existingLog.completedCount + 1,
                lastCompletedTimestamp = now
            )
        } else {
            HabitLog(
                habitId = habitId,
                date = today,
                completedCount = 1,
                targetCount = habit.targetPerDay,
                lastCompletedTimestamp = now
            )
        }

        habitDao.insertOrUpdateLog(updatedLog)
        updatedLog
    }

    suspend fun decrementHabitProgress(habitId: Long): HabitLog? = withContext(Dispatchers.IO) {
        val today = getTodayDateString()
        val existingLog = habitDao.getLogForHabitAndDate(habitId, today) ?: return@withContext null
        if (existingLog.completedCount <= 0) return@withContext existingLog

        val updatedLog = existingLog.copy(
            completedCount = (existingLog.completedCount - 1).coerceAtLeast(0)
        )
        habitDao.insertOrUpdateLog(updatedLog)
        updatedLog
    }

    suspend fun setHabitProgress(habitId: Long, count: Int): HabitLog = withContext(Dispatchers.IO) {
        val today = getTodayDateString()
        val habit = habitDao.getHabitById(habitId) ?: throw IllegalArgumentException("Habit not found")
        val existingLog = habitDao.getLogForHabitAndDate(habitId, today)
        val now = System.currentTimeMillis()

        val updatedLog = if (existingLog != null) {
            existingLog.copy(
                completedCount = count.coerceAtLeast(0),
                lastCompletedTimestamp = now
            )
        } else {
            HabitLog(
                habitId = habitId,
                date = today,
                completedCount = count.coerceAtLeast(0),
                targetCount = habit.targetPerDay,
                lastCompletedTimestamp = now
            )
        }
        habitDao.insertOrUpdateLog(updatedLog)
        updatedLog
    }

    /**
     * Compute current streak and best streak for a habit by traversing past daily logs.
     */
    private fun calculateStreaksFromLogs(
        habitId: Long,
        targetPerDay: Int,
        todayLog: HabitLog?,
        logs: List<HabitLog>
    ): Pair<Int, Int> {
        if (logs.isEmpty()) {
            val isTodayDone = (todayLog?.completedCount ?: 0) >= (todayLog?.targetCount ?: targetPerDay)
            val streak = if (isTodayDone) 1 else 0
            return Pair(streak, streak)
        }

        val completedDates = HashSet<String>()
        for (log in logs) {
            val target = if (log.targetCount > 0) log.targetCount else targetPerDay
            if (log.completedCount >= target) {
                completedDates.add(log.date)
            }
        }
        if (todayLog != null && todayLog.completedCount >= (if (todayLog.targetCount > 0) todayLog.targetCount else targetPerDay)) {
            completedDates.add(todayLog.date)
        }

        val calendar = Calendar.getInstance()
        val df = getDateFormat()
        val todayStr = df.format(calendar.time)

        // Calculate current streak
        var currentStreak = 0
        val isTodayCompleted = completedDates.contains(todayStr)

        if (isTodayCompleted) {
            currentStreak++
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            while (completedDates.contains(df.format(calendar.time))) {
                currentStreak++
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }
        } else {
            // Check if yesterday was completed (streak alive, but today pending)
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            while (completedDates.contains(df.format(calendar.time))) {
                currentStreak++
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }
        }

        // Calculate best streak historically
        val sortedDates = completedDates.mapNotNull {
            try { df.parse(it) } catch (e: Exception) { null }
        }.sorted()

        var bestStreak = 0
        var tempStreak = 0
        var prevCal: Calendar? = null

        for (date in sortedDates) {
            val curCal = Calendar.getInstance().apply { time = date }
            if (prevCal == null) {
                tempStreak = 1
            } else {
                // DST-safe consecutive day check
                val nextDayCal = (prevCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                val isConsecutive = nextDayCal.get(Calendar.YEAR) == curCal.get(Calendar.YEAR) &&
                        nextDayCal.get(Calendar.DAY_OF_YEAR) == curCal.get(Calendar.DAY_OF_YEAR)
                val isSameDay = prevCal.get(Calendar.YEAR) == curCal.get(Calendar.YEAR) &&
                        prevCal.get(Calendar.DAY_OF_YEAR) == curCal.get(Calendar.DAY_OF_YEAR)

                if (isConsecutive) {
                    tempStreak++
                } else if (!isSameDay) {
                    tempStreak = 1
                }
            }
            if (tempStreak > bestStreak) {
                bestStreak = tempStreak
            }
            prevCal = curCal
        }

        if (currentStreak > bestStreak) {
            bestStreak = currentStreak
        }

        return Pair(currentStreak, bestStreak)
    }

    suspend fun calculateStreaks(
        habitId: Long,
        targetPerDay: Int,
        todayLog: HabitLog?
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val logs = habitDao.getAllLogsForHabitSync(habitId)
        calculateStreaksFromLogs(habitId, targetPerDay, todayLog, logs)
    }

    companion object {
        fun getPresetHabits(): List<Habit> = listOf(
            Habit(
                title = "Drink Water",
                description = "Stay hydrated throughout your active hours",
                iconEmoji = "🥤",
                colorHex = "#38BDF8", // Cyan / Sky blue
                type = HabitType.INTERVAL_WINDOW,
                targetPerDay = 8,
                intervalHours = 1,
                intervalMinutes = 30,
                windowStartHour = 8,
                windowEndHour = 20,
                reminderSound = "WATER_DROP"
            ),
            Habit(
                title = "Daily Medicine",
                description = "Prescribed vitamins or daily medication",
                iconEmoji = "💊",
                colorHex = "#F43F5E", // Rose
                type = HabitType.ONCE_DAILY,
                targetPerDay = 1,
                fixedReminderHour = 9,
                fixedReminderMinute = 0,
                reminderSound = "CHIME"
            ),
            Habit(
                title = "Read 20 Minutes",
                description = "Expand knowledge and unwind before bed",
                iconEmoji = "📖",
                colorHex = "#A855F7", // Purple
                type = HabitType.ONCE_DAILY,
                targetPerDay = 1,
                fixedReminderHour = 21,
                fixedReminderMinute = 0,
                reminderSound = "ZEN"
            ),
            Habit(
                title = "Posture & Stretch",
                description = "Reset neck, shoulders, and spine posture",
                iconEmoji = "🧍",
                colorHex = "#10B981", // Emerald
                type = HabitType.INTERVAL_WINDOW,
                targetPerDay = 4,
                intervalHours = 2,
                intervalMinutes = 15,
                windowStartHour = 10,
                windowEndHour = 19,
                reminderSound = "ZEN"
            ),
            Habit(
                title = "Mindful Meditation",
                description = "5 minutes of breathwork and calm focus",
                iconEmoji = "🧘",
                colorHex = "#F59E0B", // Amber
                type = HabitType.ONCE_DAILY,
                targetPerDay = 1,
                fixedReminderHour = 7,
                fixedReminderMinute = 30,
                reminderSound = "ZEN"
            )
        )
    }
}
