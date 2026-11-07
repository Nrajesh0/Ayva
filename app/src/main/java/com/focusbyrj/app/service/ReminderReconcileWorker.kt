package com.focusbyrj.app.service

import android.content.Context
import androidx.work.*
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.util.HabitAlarmScheduler
import com.focusbyrj.app.util.TaskReminderHelper
import com.focusbyrj.app.util.WorkSessionReminders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Durable alarm restoration; one failed family must not skip the other families. */
class ReminderReconcileWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? FocusApplication ?: return@withContext Result.retry()
        var succeeded = true
        suspend fun attempt(label: String, block: suspend () -> Unit) {
            try { block() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                succeeded = false
                android.util.Log.w("ReminderReconcile", "Could not restore $label", error)
            }
        }
        attempt("daily summaries") { DailySummaryReceiver.scheduleDailySummaries(app) }
        attempt("drill reminders") { AptitudeReminderReceiver.scheduleDrillReminders(app) }
        attempt("habit reminders") {
            app.habitRepository.getAllActiveHabits().first().forEach { habit ->
                attempt("habit ${habit.id}") {
                    val today = app.habitRepository.getTodayDateString()
                    val log = app.database.habitDao().getLogForHabitAndDate(habit.id, today)
                    HabitAlarmScheduler.scheduleHabitReminder(app, habit,
                        lastCompletedTimestamp = log?.lastCompletedTimestamp,
                        isGoalCompletedToday = (log?.completedCount ?: 0) >= habit.targetPerDay)
                }
            }
        }
        if (inputData.getBoolean(RESTORE_ABSOLUTE, false)) {
            attempt("focus timer") { com.focusbyrj.app.util.FocusSessionManager.restore(app) }
            attempt("work sessions") { WorkSessionReminders.reconcileAll(app) }
            attempt("task reminders") {
                app.database.taskDao().getAllActiveTasksList().forEach { task ->
                    attempt("task ${task.id}") { TaskReminderHelper.scheduleReminder(app, task) }
                }
            }
        }
        if (succeeded) Result.success() else Result.retry()
    }

    companion object {
        private const val RESTORE_ABSOLUTE = "restore_absolute_reminders"
        fun enqueue(context: Context, restoreAbsolute: Boolean): Operation {
            // A timezone event must not replace an outstanding boot restoration.
            val name = if (restoreAbsolute) "run_boot_reminders" else "run_calendar_reminders"
            val request = OneTimeWorkRequestBuilder<ReminderReconcileWorker>()
                .setInputData(workDataOf(RESTORE_ABSOLUTE to restoreAbsolute))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            return WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
