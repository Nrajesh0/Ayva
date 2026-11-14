package com.focusbyrj.app.util.backup

import android.content.Context
import androidx.work.*
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Retry derived state only. Never reapplies an archive or changes restored records. */
class RestoreRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        RestoreJournal.consistent(applicationContext) {
            withContext(Dispatchers.IO) {
                val app = applicationContext as FocusApplication
                FocusEconomyManager.init(app)
                FocusStatsManager.init(app)
                BubbleChatManager.reloadAfterRestore(app)
                AptitudeManager.init(app)
                DailyQuestManager.init(app)
                com.focusbyrj.app.service.DailySummaryReceiver.scheduleDailySummaries(app)
                com.focusbyrj.app.service.AptitudeReminderReceiver.scheduleDrillReminders(app)
                HabitAlarmScheduler.rescheduleAllHabits(app)
                WorkSessionReminders.reconcileAll(app)
                app.database.taskDao().getAllTasksList().filter { !it.isCompleted && !it.isTrashed }
                    .forEach { TaskReminderHelper.scheduleReminder(app, it) }
                com.focusbyrj.app.widget.NoteWidgetProvider.updateAllWidgets(app)
                com.focusbyrj.app.widget.TodoWidgetProvider.updateAllWidgets(app)
                com.focusbyrj.app.widget.ExamCountdownProvider.updateAll(app)
            }
        }
        Result.success()
    } catch (e: CancellationException) { throw e }
      catch (_: Exception) { Result.retry() }

    companion object {
        fun enqueue(context: Context) {
            // A failed reconciliation enqueue must not undo a sound data commit.
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork("restore-refresh", ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<RestoreRefreshWorker>()
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
            }
        }
    }
}
