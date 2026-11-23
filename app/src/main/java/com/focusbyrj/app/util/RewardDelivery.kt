package com.focusbyrj.app.util

import androidx.room.withTransaction
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.util.backup.MaintenanceGate
import java.util.Calendar
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Credit and receipt acknowledgement are crash-safe; the backup gate covers both stores. */
internal object RewardDelivery {
    private val delivery = Mutex()

    suspend fun drain(app: FocusApplication) =
        MaintenanceGate.shared {
            delivery.withLock {
                FocusEconomyManager.ensureInitialized(app)
                val dao = app.database.earnedRewardDao()
                val pending = dao.pending()
                for (reward in pending) {
                    if (reward.kind == "ROUTINE")
                        FocusStatsManager.creditRoutineReceipt(app, reward.id, reward.endAt)
                    FocusEconomyManager.creditReceipt(reward.id, reward.xp, reward.gold)
                    dao.applied(reward.id)
                }
                for ((sourceId, period) in
                    pending
                        .filter { it.kind == "HABIT_LOG" }
                        .map { it.sourceId to it.period }
                        .distinct()) {
                    dao.compactAppliedLogs(sourceId, period)
                }
                FocusEconomyManager.clearReceiptMarker()
                FocusStatsManager.clearReceiptMarkers(app)
            }
        }

    suspend fun reconcileRoutines(
        app: FocusApplication,
        now: Long = System.currentTimeMillis(),
        startNewRuns: Boolean = true,
    ): List<String> =
        MaintenanceGate.shared {
            val db = app.database
            val completed = mutableListOf<String>()
            db.withTransaction {
                val schedules =
                    db.scheduleDao().getAllSchedulesSync().associateBy { it.id.toLong() }
                val dao = db.earnedRewardDao()
                for (run in dao.running()) {
                    val current = schedules[run.sourceId]
                    val original =
                        runCatching { ScheduleJson.decode(org.json.JSONObject(run.snapshot)) }
                            .getOrNull()
                    if (current == null || current != original || !current.isEnabled)
                        dao.finishRun(run.id, "CANCELLED")
                    else if (now >= run.endAt) {
                        dao.finishRun(run.id, "PENDING")
                        completed += current.name
                    }
                }
                val at = Calendar.getInstance().apply { timeInMillis = now }
                for (schedule in schedules.values.filter { startNewRuns && it.isActiveAt(at) }) {
                    val window = routineWindow(schedule, at)
                    dao.insert(
                        EarnedReward(
                            kind = "ROUTINE",
                            sourceId = schedule.id.toLong(),
                            period = window.first,
                            xp = 100,
                            gold = 25,
                            state = "RUNNING",
                            snapshot = ScheduleJson.encode(schedule).toString(),
                            endAt = window.second,
                        )
                    )
                }
            }
            drain(app)
            completed
        }

    /**
     * End minute is inclusive in isActiveAt; complete at the next minute, including overnight/DST.
     */
    internal fun routineWindow(schedule: FocusSchedule, at: Calendar): Pair<String, Long> {
        val start =
            (at.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, schedule.startHour)
                set(Calendar.MINUTE, schedule.startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis > at.timeInMillis) add(Calendar.DAY_OF_YEAR, -1)
            }
        val end =
            (start.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, schedule.endHour)
                set(Calendar.MINUTE, schedule.endMinute)
                if (timeInMillis < start.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
                add(Calendar.MINUTE, 1)
            }
        // A routine's start date identifies the run even if its times are edited that day.
        val day =
            (start.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        return String.format(
            java.util.Locale.US,
            "%04d-%02d-%02d",
            day.get(Calendar.YEAR),
            day.get(Calendar.MONTH) + 1,
            day.get(Calendar.DAY_OF_MONTH),
        ) to end.timeInMillis
    }
}
