package com.focusbyrj.app.util

import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.util.backup.*
import java.util.Calendar
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FocusApplication::class)
class RewardConsistencyTest {
    private val app
        get() = ApplicationProvider.getApplicationContext<FocusApplication>()

    private val db
        get() = app.database

    private val economy
        get() = app.getSharedPreferences("focus_economy_prefs", 0)

    @Before
    fun setup() = runBlocking {
        db.earnedRewardDao().deleteAll()
        db.habitDao().deleteAllLogs()
        db.habitDao().deleteAllHabits()
        db.scheduleDao().deleteAllSchedules()
        economy.edit().clear().commit()
        FocusEconomyManager.init(app)
    }

    private suspend fun habit(target: Int = 1): Long =
        db.habitDao().insertHabit(Habit(title = "Synthetic habit", targetPerDay = target))

    private val date = "2026-11-22"

    @Test
    fun undoAndRepeatCannotEarnTheGoalTwice() = runBlocking {
        val id = habit()
        db.habitDao().incrementWithReward(id, date, 1)
        RewardDelivery.drain(app)
        assertEquals(25, economy.getInt("pending_xp", 0))
        db.habitDao().decrementChecked(id, date)
        db.habitDao().incrementWithReward(id, date, 2)
        RewardDelivery.drain(app)
        assertEquals(25, economy.getInt("pending_xp", 0))
        assertEquals(15, economy.getInt("pending_gold", 0))
    }

    @Test
    fun concurrentLogsEachHaveOneDurableOrdinal() = runBlocking {
        val id = habit(10)
        val results = coroutineScope {
            (1..10)
                .map {
                    async(Dispatchers.IO) {
                        db.habitDao().incrementWithReward(id, date, it.toLong()).completedCount
                    }
                }
                .awaitAll()
        }
        assertEquals((1..10).toList(), results.sorted())
        RewardDelivery.drain(app)
        assertEquals(115, economy.getInt("pending_xp", 0))
        assertEquals(60, economy.getInt("pending_gold", 0))
        assertEquals(2, db.earnedRewardDao().all().size)
    }

    @Test
    fun crashAfterCreditBeforeAcknowledgementCannotReplayEvenIfAnotherReceiptSortsEarlier() =
        runBlocking {
            val credited =
                EarnedReward(
                    id = "z-before-crash",
                    kind = "HABIT_GOAL",
                    sourceId = 1,
                    period = date,
                    xp = 15,
                    gold = 10,
                )
            db.earnedRewardDao().insert(credited)
            FocusEconomyManager.creditReceipt(credited.id, credited.xp, credited.gold)
            // Simulate startup with a second pending event sorted before the unacknowledged credit.
            FocusEconomyManager.init(app)
            db.earnedRewardDao().insert(credited.copy(id = "a-new", sourceId = 2))
            RewardDelivery.drain(app)
            assertEquals(30, economy.getInt("pending_xp", 0))
            assertEquals(20, economy.getInt("pending_gold", 0))
            assertTrue(db.earnedRewardDao().pending().isEmpty())
            assertFalse(economy.contains("reward_delivery_ids"))
        }

    private suspend fun routine(): Pair<FocusSchedule, Long> {
        val now = Calendar.getInstance().apply { set(Calendar.MILLISECOND, 0) }
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val row =
            FocusSchedule(
                name = "Synthetic routine",
                startHour = (hour + 23) % 24,
                startMinute = 0,
                endHour = (hour + 2) % 24,
                endMinute = 0,
                daysOfWeek = "1,2,3,4,5,6,7",
                appsToBlock = "synthetic.pkg|HARD",
            )
        val id = db.scheduleDao().insertSchedule(row).toInt()
        return row.copy(id = id) to now.timeInMillis
    }

    @Test
    fun uncertainCommitRetriesDurablyWithoutLosingInterveningRewards() = runBlocking {
        val row = EarnedReward(kind = "HABIT_GOAL", sourceId = 1, period = date, xp = 25, gold = 15)
        db.earnedRewardDao().insert(row)
        val real = economy
        val failOnce = java.util.concurrent.atomic.AtomicBoolean(true)
        val faulty =
            object : android.content.SharedPreferences by real {
                override fun edit(): android.content.SharedPreferences.Editor {
                    val delegate = real.edit()
                    val type = android.content.SharedPreferences.Editor::class.java
                    return java.lang.reflect.Proxy.newProxyInstance(
                        type.classLoader,
                        arrayOf(type),
                    ) { proxy, method, args ->
                        val result = method.invoke(delegate, *(args ?: emptyArray()))
                        when {
                            method.name == "commit" && failOnce.compareAndSet(true, false) -> false
                            method.returnType == type -> proxy
                            else -> result
                        }
                    } as android.content.SharedPreferences.Editor
                }
            }
        val field =
            FocusEconomyManager::class.java.getDeclaredField("prefs").apply { isAccessible = true }
        try {
            field.set(null, faulty)
            assertThrows(IllegalStateException::class.java) {
                FocusEconomyManager.creditReceipt(row.id, row.xp, row.gold)
            }
            FocusEconomyManager.addExactRewards(5, 2)
            RewardDelivery.drain(app)
            assertEquals(30, real.getInt("pending_xp", 0))
            assertEquals(17, real.getInt("pending_gold", 0))
            assertTrue(db.earnedRewardDao().pending().isEmpty())
        } finally {
            FocusEconomyManager.init(app)
        }
    }

    @Test
    fun naturalCompletionEarnsOnceWithRoutineNotificationsDisabled() = runBlocking {
        app.getSharedPreferences("focus_prefs", 0)
            .edit()
            .putBoolean("routine_notifications", false)
            .commit()
        val (row, now) = routine()
        RewardDelivery.reconcileRoutines(app, now)
        val end = db.earnedRewardDao().running().single().endAt
        assertTrue(RewardDelivery.reconcileRoutines(app, end).contains(row.name))
        RewardDelivery.reconcileRoutines(app, end + 60_000)
        assertEquals(100, economy.getInt("pending_xp", 0))
        assertEquals(25, economy.getInt("pending_gold", 0))
    }

    @Test
    fun disablingEditingOrDeletingIsNotRoutineCompletion() = runBlocking {
        val (row, now) = routine()
        RewardDelivery.reconcileRoutines(app, now)
        val end = db.earnedRewardDao().running().single().endAt
        db.scheduleDao().updateChecked(row.copy(isEnabled = false), row)
        db.scheduleDao().updateChecked(row, row.copy(isEnabled = false))
        RewardDelivery.reconcileRoutines(app, now + 1000)
        assertTrue(db.earnedRewardDao().running().isEmpty())
        RewardDelivery.reconcileRoutines(app, end + 1)
        assertEquals(0, economy.getInt("pending_xp", 0))
    }

    @Test
    fun disablingAfterTheDeadlineKeepsAnEarnedRewardWhenThePollWasDelayed() = runBlocking {
        val (row, now) = routine()
        RewardDelivery.reconcileRoutines(app, now)
        val run = db.earnedRewardDao().running().single()
        // Model a missed background poll: the persisted deadline has passed before the edit.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE earned_rewards SET endAt = ? WHERE id = ?",
            arrayOf(System.currentTimeMillis() - 1, run.id),
        )
        db.scheduleDao().updateChecked(row.copy(isEnabled = false), row)
        assertTrue(db.earnedRewardDao().all().single().state in setOf("PENDING", "APPLIED"))
        RewardDelivery.drain(app)
        RewardDelivery.drain(app)
        assertEquals(100, economy.getInt("pending_xp", 0))
        assertEquals(25, economy.getInt("pending_gold", 0))
    }

    @Test
    fun overnightWindowCompletesAfterTheInclusiveEndMinute() {
        val at = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 23, 1, 0, 0) }
        val row =
            FocusSchedule(
                name = "Night",
                startHour = 22,
                startMinute = 0,
                endHour = 6,
                endMinute = 0,
                daysOfWeek = "1,2,3,4,5,6,7",
            )
        val (period, end) = RewardDelivery.routineWindow(row, at)
        assertEquals("2026-11-22", period)
        assertEquals(
            6,
            Calendar.getInstance().apply { timeInMillis = end }.get(Calendar.HOUR_OF_DAY),
        )
        assertEquals(1, Calendar.getInstance().apply { timeInMillis = end }.get(Calendar.MINUTE))
    }

    @Test
    fun portableRestoreKeepsEarnedHistoryAndRemapsCopiedHabitIds() = runBlocking {
        val id = habit()
        db.habitDao().incrementWithReward(id, date, 1)
        RewardDelivery.drain(app)
        val archive = RewardArchive.encode(db.earnedRewardDao().all())
        val copyId = db.habitDao().insertHabit(db.habitDao().getHabitById(id)!!.copy(id = 0))
        RewardArchive.restore(db, archive, false, mapOf(id to copyId), emptyMap())
        db.habitDao().incrementWithReward(copyId, date, 2)
        RewardDelivery.drain(app)
        assertEquals(25, economy.getInt("pending_xp", 0))
        assertEquals(4, db.earnedRewardDao().all().size)
    }

    @Test
    fun assistantAndHabitScreenShareTheSameEarnedHistory() = runBlocking {
        val id = habit()
        val today = app.habitRepository.getTodayDateString()
        db.habitDao().incrementWithReward(id, today, 1)
        RewardDelivery.drain(app)
        db.habitDao().decrementChecked(id, today)
        com.focusbyrj.app.util.assistant.AssistantActionExecutor.execute(
            app,
            com.focusbyrj.app.util.assistant.AssistantAction.LogHabit(id),
        )
        assertEquals(25, economy.getInt("pending_xp", 0))
    }

    @Test
    fun fullPortableArchiveRestoresBalancesAndReceiptsTogether() = runBlocking {
        val id = habit()
        db.habitDao().incrementWithReward(id, date, 1)
        val file = java.io.File(app.cacheDir, "reward-archive.fbck")
        try {
            val password = "synthetic-reward-backup-password"
            BackupRestoreManager.createEncryptedBackup(
                    app,
                    android.net.Uri.fromFile(file),
                    password,
                )
                .getOrThrow()
            assertEquals(25, economy.getInt("pending_xp", 0))
            db.habitDao().deleteAllLogs()
            db.earnedRewardDao().deleteAll()
            economy.edit().putInt("pending_xp", 999).commit()
            BackupRestoreManager.restoreEncryptedBackup(
                    app,
                    android.net.Uri.fromFile(file),
                    password,
                    true,
                )
                .getOrThrow()
            assertEquals(25, economy.getInt("pending_xp", 0))
            db.habitDao().decrementChecked(id, date)
            db.habitDao().incrementWithReward(id, date, 2)
            RewardDelivery.drain(app)
            assertEquals(25, economy.getInt("pending_xp", 0))
        } finally {
            file.delete()
        }
    }

    @Test
    fun manualProgressAndOlderBackupsDoNotReawardHistoricalCredit() = runBlocking {
        val id = habit(2)
        db.habitDao().setProgressChecked(id, date, 2, 1)
        db.habitDao().decrementChecked(id, date)
        db.habitDao().incrementWithReward(id, date, 2)
        RewardDelivery.drain(app)
        assertEquals(0, economy.getInt("pending_xp", 0))
        RewardArchive.restore(db, null, true, mapOf(id to id), emptyMap())
        db.habitDao().decrementChecked(id, date)
        db.habitDao().incrementWithReward(id, date, 3)
        RewardDelivery.drain(app)
        assertEquals(0, economy.getInt("pending_xp", 0))
    }
}
