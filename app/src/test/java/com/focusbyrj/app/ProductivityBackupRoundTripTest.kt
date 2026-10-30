package com.focusbyrj.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.*
import com.focusbyrj.app.util.backup.BackupRestoreManager
import com.focusbyrj.app.widget.ExamCountdown
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ProductivityBackupRoundTripTest {
    @Test fun mergeRestoresHabitFrequencyAndRebindsRoutineWithoutOverwritingLibrary() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>() as FocusApplication
        val frequency = HabitFrequency(mode = "WEEKLY", weeklyTarget = 4).json()
        val id = app.database.habitDao().insertHabit(Habit(title = "Roundtrip habit", frequencyJson = frequency))
        val library = ProductivityLibrary(app)
        val routineId = library.put("chain", JSONObject().put("kind", "chain").put("name", "Roundtrip routine").put("habits", JSONArray(listOf(id))))
        val templateId = library.put("template", ProductivityLibrary.template(Task(title = "Roundtrip template", subtasksJson = Subtask.listToJson(listOf(Subtask(title = "Step"))))))
        val event = ExamCountdown().json()
        app.getSharedPreferences("exam_countdowns", 0).edit().putString("roundtrip", event).commit()
        val backup = File(app.cacheDir, "productivity-roundtrip.fbck")
        try {
            val created = BackupRestoreManager.createEncryptedBackup(app, Uri.fromFile(backup), "roundtrip-password")
            assertTrue(created.exceptionOrNull()?.stackTraceToString(), created.isSuccess)
            assertTrue(BackupRestoreManager.verifyEncryptedBackup(app, Uri.fromFile(backup), "roundtrip-password").isSuccess)
            val restored = BackupRestoreManager.restoreEncryptedBackup(app, Uri.fromFile(backup), "roundtrip-password", cleanRestore = false)
            assertTrue(restored.exceptionOrNull()?.stackTraceToString(), restored.isSuccess)
            val imported = app.database.habitDao().getAllHabitsSync().single { it.title == "Roundtrip habit" && it.id != id }
            assertEquals(frequency, imported.frequencyJson)
            val chains = library.entries("chain")
            assertTrue(chains.any { it.first == routineId })
            val incoming = chains.single { it.first != routineId && it.second.getString("name") == "Roundtrip routine" }
            assertEquals(imported.id, incoming.second.getJSONArray("habits").getLong(0))
            assertTrue(library.entries("template").any { it.first == templateId })
            assertEquals(2, library.entries("template").count { it.second.getString("name") == "Roundtrip template" })
            assertEquals(2, app.getSharedPreferences("exam_countdowns", 0).all.values.count { it == event })
        } finally { backup.delete() }
    }
}
