package com.focusbyrj.app.util.backup

import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.util.RewardDelivery
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FocusApplication::class)
class JournalSchemaUpgradeTest {
    private class Interrupted : Error()

    @Test
    fun preUpgradeJournalRestoresRecordsAndBalancesThroughTheAdditiveRewardSchema() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<FocusApplication>()
        val db = app.database
        val prefs = app.getSharedPreferences("focus_economy_prefs", 0)
        db.habitDao().deleteAllLogs()
        db.habitDao().deleteAllHabits()
        db.earnedRewardDao().deleteAll()
        val id = db.habitDao().insertHabit(Habit(title = "Kept old habit", targetPerDay = 2))
        db.habitDao()
            .insertOrUpdateLog(
                HabitLog(habitId = id, date = "2026-11-22", completedCount = 1, targetCount = 2)
            )
        prefs.edit().putInt("pending_xp", 10).commit()
        val file = File(app.filesDir, "restore_before_state.enc")
        try {
            try {
                RestoreJournal.mutate<Unit>(app) {
                    db.habitDao()
                        .deleteHabitWithLogsTransactional(id, db.habitDao().getHabitById(id)!!)
                    throw Interrupted()
                }
                fail("Expected interruption")
            } catch (_: Interrupted) {}
            val old = JSONObject(EncryptedSnapshotFile.read(file))
            val databases = old.getJSONArray("databases")
            val focus =
                (0 until databases.length())
                    .map { databases.getJSONObject(it) }
                    .single { snapshot ->
                        val tables = snapshot.getJSONArray("tables")
                        (0 until tables.length()).any {
                            tables.getJSONObject(it).getString("name") == "tasks"
                        }
                    }
            val tables = focus.getJSONArray("tables")
            focus
                .put("schema", 16)
                .put(
                    "tables",
                    JSONArray(
                        (0 until tables.length())
                            .map { tables.getJSONObject(it) }
                            .filter { it.getString("name") != "earned_rewards" }
                    ),
                )
            MaintenanceGate.exclusively { EncryptedSnapshotFile.write(file, old.toString()) }
            RestoreJournal.recover(app)
            assertFalse(MaintenanceGate.recoveryRequired)
            assertFalse(file.exists())
            assertEquals("Kept old habit", db.habitDao().getHabitById(id)?.title)
            assertEquals(1, db.habitDao().getLogForHabitAndDate(id, "2026-11-22")?.completedCount)
            assertEquals(2, db.earnedRewardDao().all().size)
            db.habitDao().decrementChecked(id, "2026-11-22")
            db.habitDao().incrementWithReward(id, "2026-11-22", 2)
            RewardDelivery.drain(app)
            assertEquals(10, prefs.getInt("pending_xp", 0))
        } finally {
            if (file.exists()) RestoreJournal.recover(app)
            MaintenanceGate.recovered()
        }
    }
}
