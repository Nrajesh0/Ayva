package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.util.assistant.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/** Release gates for real conversations and their native task writes, not phrase-by-phrase coverage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TripChecklistConversationTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        ConversationPlanStore.clearAll(context); AssistantSessionStore(context).clear(); PlanningLibrary(context).clear()
        ChatModeStore.set(context, ChatMode.CHAT)
    }
    @After fun cleanup() {
        ConversationPlanStore.clearAll(context); AssistantSessionStore(context).clear(); PlanningLibrary(context).clear()
        ChatModeStore.set(context, ChatMode.CHAT); db.close()
    }
    private suspend fun say(text: String): String {
        // Match the composer, which prefixes ordinary messages while Plan mode is selected.
        val sent = if (ChatModeStore.get(context) == ChatMode.PLAN && !text.startsWith("/")) "/plan $text" else text
        return OfflineAssistant.respond(sent, context)?.formattedText ?: error("Unhandled: $text")
    }
    private fun plan() = ConversationPlanStore(context).active()!!
    private fun items() = plan().trip!!.items.map { it.title }
    private fun date(at: Long?) = at?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(plan().timezone)).toLocalDate().toString() }

    @Test fun goaConversationCreatesOneNativeChecklistWithFinalDateAndNoDuplicateSave() = runBlocking {
        assertTrue(say("I want to plan a trip to Goa").contains("main task"))
        assertEquals("Goa", plan().facts["destination"])
        assertTrue(say("it's 26th").contains("month"))
        say("July 2029")
        assertEquals("2029-07-26", plan().facts["travel date"])
        say("Book a bus and pack clothes")
        say("I also want to buy apples")
        say("We should visit Baga beach there")
        assertEquals(4, items().size)
        say("Actually make it 28th")
        say("Remove buying apples from the list")
        val summary = say("what do we have till now")
        assertTrue(summary.contains("July 28, 2029")); assertTrue(summary.contains("During the trip")); assertFalse(summary.contains("apples", true))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        assertTrue(say("that's it").contains("Saved"))
        val task = db.taskDao().getAllActiveTasksList().single()
        assertEquals("Trip to Goa", task.title); assertEquals("2029-07-28", date(task.dueDate))
        assertEquals(setOf("Book a bus", "Pack clothes", "Visit Baga beach"), task.subtasks.map { it.title }.toSet())
        say("that's it"); assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun examPurposeDatesAndQuantitiesSurviveRestartAndPause() = runBlocking {
        say("I'm planning for ibps exam trip")
        say("ibps exam")
        assertEquals("IBPS exam", plan().facts["purpose"]); assertNull(plan().facts["destination"])
        say("I'm thinking 26th")
        val checkpoint = plan().json().toString()
        AssistantSessionStore(context).clear()
        assertEquals(checkpoint, plan().json().toString())
        say("July 2029")
        say("the exam is on 27 July 2029")
        say("to Chennai")
        say("download admit card, bring photos, take 3 pens")
        assertEquals(3, items().size); assertTrue(items().contains("Take 3 pens"))
        val review = say("what have we got so far")
        assertTrue(review.contains("Exam date: July 27, 2029")); assertTrue(review.contains("Travel date: July 26, 2029"))
        say("end"); assertEquals(ChatMode.CHAT, ChatModeStore.get(context)); assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("/plan"); assertEquals(3, items().size)
        say("that's everything")
        val task = db.taskDao().getAllActiveTasksList().single()
        assertEquals(3, task.subtasks.size); assertTrue(task.title.contains("IBPS exam")); assertEquals("2029-07-26", date(task.dueDate))
    }
    @Test fun compoundEditsAreOneUndoAndAmbiguousRemovalNeverGuesses() = runBlocking {
        say("Let's plan a trip to Goa on 26 July 2029")
        say("Book flights and book a car")
        say("buy apples")
        say("make it 28th, remove apples, and add pack dresses")
        assertEquals("2029-07-28", plan().facts["travel date"]); assertFalse(items().any { it.contains("apples") })
        say("undo")
        assertEquals("2029-07-26", plan().facts["travel date"]); assertTrue(items().any { it.contains("apples") })
        say("remove book")
        assertEquals("trip_reference", plan().pending!!.getString("kind")); assertEquals(3, items().size)
        say("the car")
        assertEquals(2, items().size); assertTrue(items().any { it.contains("flights") })
        val before = plan().trip
        say("add sunscreen; teleport everything")
        assertEquals(before, plan().trip)
    }
    @Test fun relativePreparationDatesMoveButIndependentDatesStayPut() = runBlocking {
        say("I want to plan a trip to Goa on 26 July 2029")
        say("pack clothes the day before leaving")
        say("book flights")
        say("move flights to 20 July 2029")
        say("change the date to 28 July 2029")
        assertEquals("2029-07-27", date(plan().steps.single { it.title == "Pack clothes" }.deadline))
        assertEquals("2029-07-20", date(plan().steps.single { it.title == "Book flights" }.deadline))
        say("save it")
        val tasks = db.taskDao().getAllActiveTasksList()
        val parent = tasks.single { it.title == "Trip to Goa" }
        assertEquals(2, tasks.count { it.planning?.parentTaskId == parent.id })
    }
    @Test fun newDetailsDoNotDiscardPendingMonthAndFinishDoesNotSilentlySaveAnOldDate() = runBlocking {
        say("I want to plan a trip to Goa")
        say("26th"); say("bring photos")
        assertEquals("trip_date", plan().pending!!.getString("kind"))
        assertTrue(say("what we got till now").contains("Bring photos"))
        say("that's it"); assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("February 2029"); say("the exam is on 27 February 2029")
        say("change the date to 28 February 2029")
        say("that's it"); assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("change the date to 26 February 2029"); say("save it")
        assertEquals("2029-02-26", date(db.taskDao().getAllActiveTasksList().single().dueDate))
    }
    @Test fun savedChecklistEditsReuseTaskIdentityAndRefuseOutsideEdits() = runBlocking {
        say("I want to plan a trip to Goa on 26 July 2029")
        say("buy apples"); say("save it")
        val original = db.taskDao().getAllActiveTasksList().single()
        say("remove apples"); say("bring photos"); say("save it")
        val updated = db.taskDao().getAllActiveTasksList().single()
        assertEquals(original.id, updated.id); assertEquals(listOf("Bring photos"), updated.subtasks.map { it.title })
        db.taskDao().updateTask(updated.copy(title = "Edited elsewhere", updatedAt = updated.updatedAt + 1))
        say("take 3 pens"); val refusal = say("save it")
        assertTrue(refusal.contains("changed outside", true))
        assertEquals("Edited elsewhere", db.taskDao().getAllActiveTasksList().single().title)
    }
    @Test fun suggestionsAndSpeculationStaySeparateAndChecklistIdentitySurvivesQuantityCorrection() = runBlocking {
        say("I want to plan a trip to Goa")
        say("Maybe we could book flights")
        assertTrue(items().isEmpty())
        say("give me ideas"); assertTrue(items().isEmpty())
        say("bring photos")
        val id = plan().trip!!.items.single().id
        say("make that 4 photos")
        assertEquals(id, plan().trip!!.items.single().id); assertEquals(listOf("Bring 4 photos"), items())
        say("add bring 4 photos"); assertEquals(1, items().size)
        say("just talk it through first"); say("I want to buy apples")
        assertEquals(1, items().size)
        say("add buy apples"); assertEquals(2, items().size)
    }
    @Test fun invalidCalendarDatesAndExplicitEmptyPlanEntryKeepTheirMeaning() = runBlocking {
        say("/plan"); say("a trip to Goa")
        assertNotNull(plan().trip)
        say("31 February 2029")
        assertNull(plan().facts["travel date"]); assertNotNull(plan().pending)
        say("26 July 2029")
        assertNull(plan().pending); assertEquals("2029-07-26", plan().facts["travel date"])
        say("book flights")
        say("book a bus instead of flights")
        assertEquals(listOf("Book a bus"), items())
        say("done")
        assertEquals(listOf("Book a bus"), db.taskDao().getAllActiveTasksList().single().subtasks.map { it.title })
    }
}
