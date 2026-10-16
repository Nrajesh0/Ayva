package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitLog
import com.focusbyrj.app.data.HabitRepository
import com.focusbyrj.app.data.AppRestriction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise the action engine against a real Room database, not just intent classification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AyvaChatActionIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase

    @Before fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply {
            isAccessible = true
            invoke(app, base)
        }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply {
            isAccessible = true
            set(app, lazyOf(db))
        }
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = app
        }
        AyvaTalkEngine.resetConversation()
    }

    @After fun tearDown() {
        FocusSessionManager.stop(context)
        AyvaTalkEngine.resetConversation()
        db.close()
    }

    @Test fun talkTaskCreationRetainsRecurrenceAndOriginalTitleCase() = runBlocking {
        val response = AyvaTalkEngine.answerTalkQueryWithActions("remind me to email Dr Rao every day at 8am", context)
        val task = db.taskDao().getAllActiveTasksList().single()
        assertEquals("Email Dr Rao", task.title)
        assertEquals(RecurrencePattern.DAILY, task.recurrence)
        assertNotNull(task.dueDate)
        assertTrue(response.formattedText.contains("Email Dr Rao"))
    }

    @Test fun rescheduleChoiceUpdatesOnlyItsStableId() = runBlocking {
        val first = db.taskDao().insertTask(Task(title = "Call mom"))
        val second = db.taskDao().insertTask(Task(title = "Call mom tomorrow"))
        val response = AyvaTalkEngine.answerTalkQueryWithActions("reschedule id:$first tomorrow at 6pm", context)
        assertNotNull(db.taskDao().getTaskById(first)!!.dueDate)
        assertNull(db.taskDao().getTaskById(second)!!.dueDate)
        assertTrue(response.formattedText.contains("Rescheduled"))
    }

    @Test fun completionDoesNotInterpretCallAsAll() = runBlocking {
        val first = db.taskDao().insertTask(Task(title = "Call mom"))
        val second = db.taskDao().insertTask(Task(title = "Buy milk"))
        AyvaTalkEngine.answerTalkQueryWithActions("complete Call mom", context)
        assertTrue(db.taskDao().getTaskById(first)!!.isCompleted)
        assertFalse(db.taskDao().getTaskById(second)!!.isCompleted)
    }

    @Test fun cancelTaskIsAnActionRatherThanAQuestion() = runBlocking {
        val first = db.taskDao().insertTask(Task(title = "Keep me"))
        val second = db.taskDao().insertTask(Task(title = "Remove me"))
        AyvaTalkEngine.answerTalkQueryWithActions("cancel task 2", context)
        assertFalse(db.taskDao().getTaskById(first)!!.isTrashed)
        assertTrue(db.taskDao().getTaskById(second)!!.isTrashed)
    }

    @Test fun amInsideATitleIsNotATimeExpression() = runBlocking {
        val first = db.taskDao().insertTask(Task(title = "Sam report"))
        AyvaTalkEngine.answerTalkQueryWithActions("complete Sam report", context)
        assertTrue(db.taskDao().getTaskById(first)!!.isCompleted)
    }

    @Test fun explicitUnblockAllIsRecognized() = runBlocking {
        db.appRestrictionDao().insertRestriction(AppRestriction("com.example.keep", "Keep", isRestricted = true))
        val response = AyvaTalkEngine.answerTalkQueryWithActions("unblock all", context)
        assertTrue(db.appRestrictionDao().getAllRestrictionsSync().isEmpty())
        assertTrue(response.formattedText.contains("unlocked"))
    }

    @Test fun deleteChoiceNeverDeletesTheWholeList() = runBlocking {
        val first = db.taskDao().insertTask(Task(title = "Call mom"))
        val second = db.taskDao().insertTask(Task(title = "Buy milk"))
        val response = AyvaTalkEngine.answerTalkQueryWithActions("delete id:$first", context)
        assertTrue(db.taskDao().getTaskById(first)!!.isTrashed)
        assertFalse(db.taskDao().getTaskById(second)!!.isTrashed)
        assertTrue(response.formattedText.contains("Trash"))
    }

    @Test fun missingTaskIdCannotCreateAnotherTask() = runBlocking {
        val first = db.taskDao().insertTask(Task(title = "Keep me"))
        AyvaTalkEngine.answerTalkQueryWithActions("reschedule id:999 tomorrow", context)
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
        assertNull(db.taskDao().getTaskById(first)!!.dueDate)
    }

    @Test fun greetingUsesLiveTaskContext() = runBlocking {
        db.taskDao().insertTask(Task(title = "Finish report", dueDate = System.currentTimeMillis() + 3_600_000))
        val response = AyvaTalkEngine.answerTalkQueryWithActions("hello", context)
        assertTrue(response.formattedText.contains("1 pending"))
        assertTrue(response.formattedText.contains("Finish report"))
    }

    @Test fun explainingAnActionCannotExecuteIt() = runBlocking {
        val first = db.taskDao().insertTask(Task(title = "Keep me"))
        AyvaTalkEngine.answerTalkQueryWithActions("how do I delete all tasks?", context)
        assertFalse(db.taskDao().getTaskById(first)!!.isTrashed)
    }

    @Test fun notesRemainIsolatedFromChat() = runBlocking {
        val response = AyvaTalkEngine.answerTalkQueryWithActions("read my notes", context)
        assertTrue(response.formattedText.contains("cannot be created, searched, or viewed"))
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }

    @Test fun focusRequestStartsActualCountdown() = runBlocking {
        val response = AyvaTalkEngine.answerTalkQueryWithActions("focus 25", context)
        assertTrue(response.formattedText.contains("Started"))
        assertTrue(FocusSessionManager.active.value)
        assertEquals(1500L, FocusSessionManager.initialSeconds.value)
    }

    @Test fun timedBlockCannotSilentlyBecomeAnIndefiniteBlock() = runBlocking {
        val existing = AppRestriction("com.example.keep", "Keep", isRestricted = true, mode = "SOFT")
        db.appRestrictionDao().insertRestriction(existing)
        val response = AyvaTalkEngine.answerTalkQueryWithActions("block YouTube for 30 minutes", context)
        assertTrue(response.formattedText.contains("Timed blocks aren't supported"))
        assertEquals(listOf(existing), db.appRestrictionDao().getAllRestrictionsSync())
    }

    @Test fun habitProgressIsOnlyCompleteAfterReachingItsTarget() = runBlocking {
        val habitId = db.habitDao().insertHabit(Habit(title = "Water", targetPerDay = 3))
        val today = HabitRepository(db.habitDao()).getTodayDateString()
        db.habitDao().insertOrUpdateLog(HabitLog(habitId = habitId, date = today, completedCount = 1, targetCount = 3))
        val response = AyvaTalkEngine.answerTalkQueryWithActions("my habits", context)
        assertTrue(response.formattedText.contains("1/3"))
        assertFalse(response.formattedText.contains("✓"))
    }

    @Test fun habitActionActuallyIncrementsProgress() = runBlocking {
        val habitId = db.habitDao().insertHabit(Habit(title = "Reading"))
        AyvaTalkEngine.answerTalkQueryWithActions("habit done Reading", context)
        val logs = db.habitDao().getAllLogsForHabitSync(habitId)
        assertEquals(1, logs.single().completedCount)
    }

    @Test fun topicContextRecordsEarlyRepliesAndClearResetsIt() = runBlocking {
        AyvaTalkEngine.answerTalkQueryWithActions("soft mode wait timer", context)
        val followUp = AyvaTalkEngine.answerTalkQueryWithActions("how to change it?", context)
        assertTrue(followUp.formattedText.contains("Soft Mode Wait Timer"))
        assertEquals(2, AyvaTalkEngine.getRecentHistory().size)
        AyvaTalkEngine.answerTalkQueryWithActions("hello", context)
        assertEquals("greeting", AyvaTalkEngine.getRecentHistory().last().topicId)
        AyvaTalkEngine.resetConversation()
        assertTrue(AyvaTalkEngine.getRecentHistory().isEmpty())
        assertNull(AyvaTalkEngine.lastQueriedTopicId)
    }
}
