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

    @Test fun examOpeningKeepsBareDateAndDotCommandsNeverBecomeTopics() = runBlocking {
        val today = java.time.LocalDate.now()
        val reply = say("/plan rrb je exam on ${today.dayOfMonth}th")
        assertFalse(reply, reply.contains("city", true))
        assertEquals(today.toString(), plan().facts["exam date"])
        assertEquals(today.toString(), date(plan().steps.single().deadline))
        assertEquals("Rrb je exam", plan().goal)
        say("download admit card")
        say("bring 3 pens")
        val before = plan().revision
        val summary = say(".what do we have so far")
        assertTrue(summary, summary.contains("3 pens", true))
        assertEquals(before, plan().revision)
        assertNull(plan().facts["topics"])
        say(".remove that")
        assertEquals(listOf("Download admit card"), items())
        say(".change date to 28 July 2029")
        assertEquals("2029-07-28", plan().facts["exam date"])
        val unchanged = plan().json().toString()
        say(".something unclear about this")
        assertEquals(unchanged, plan().json().toString())
        say(".that's it")
        assertEquals("2029-07-28", date(db.taskDao().getAllActiveTasksList().single().dueDate))
    }

    @Test fun groceryChecklistAcceptsItemsAndKeepsQuestionsAndClearSeparate() = runBlocking {
        say("/plan trip to Goa")
        val reply = say("/plan grocery list")
        assertFalse(reply, reply.contains("city", true) || reply.contains("travelling", true))
        say("apples and milk")
        assertEquals(listOf("Apples", "Milk"), items())
        val before = plan().revision
        assertTrue(say("what do we have so far").contains("Milk"))
        say(".why do we need milk?")
        assertEquals(before, plan().revision)
        say("clear")
        assertEquals(2, items().size)
        say("remove that")
        assertEquals(listOf("Apples"), items())
        say(".clear checklist")
        assertTrue(items().isEmpty())
        say(".undo")
        assertEquals(listOf("Apples"), items())
        say(".change date to 26 July 2029")
        assertEquals("2029-07-26", plan().facts["target date"])
        say(".that's it")
        val task = db.taskDao().getAllActiveTasksList().single()
        assertEquals("Grocery list", task.title)
        assertEquals(listOf("Apples"), task.subtasks.map { it.title })
        assertEquals("2029-07-26", date(task.dueDate))
    }

    @Test fun bareDatesUseLocalMonthAndRejectInvalidOrPastDays() {
        val now = Instant.parse("2026-10-24T12:00:00Z").toEpochMilli()
        val draft = PlanWorkspace(goal = "Trip", domain = "trip", timezone = "Asia/Kolkata")
        assertEquals("2026-10-26", TripDate.read("26th", draft, now)!!.date.toString())
        assertNull(TripDate.read("23rd", draft, now)!!.date)
        val february = Instant.parse("2027-02-01T12:00:00Z").toEpochMilli()
        assertNull(TripDate.read("30th", draft, february)!!.date)
        val established = draft.copy(facts = mapOf("travel date" to "2029-07-26"))
        assertEquals("2029-07-28", TripDate.read("28th", established, now)!!.date.toString())
    }

    @Test fun checklistProgressSurvivesEditsUndoAndNativeSave() = runBlocking {
        say("/plan trip to Goa on 26 July 2029")
        say("add buy apples")
        say("add pack dresses")
        say("mark apples as done")
        assertTrue(plan().steps.first().checklist.first { it.title.contains("apples", true) }.isDone)
        val revision = plan().revision
        say("check off apples")
        assertEquals(revision, plan().revision)
        val remaining = say("what's left")
        assertTrue(remaining, remaining.contains("dresses", true))
        assertFalse(remaining, remaining.contains("apples", true))
        say("rename apples to buy green apples")
        assertTrue(plan().steps.first().checklist.first { it.title.contains("apples", true) }.isDone)
        say("reopen apples")
        assertFalse(plan().steps.first().checklist.first { it.title.contains("apples", true) }.isDone)
        say("undo")
        assertTrue(plan().steps.first().checklist.first { it.title.contains("apples", true) }.isDone)
        say("that's it")
        val saved = db.taskDao().getAllActiveTasksList().single()
        assertTrue(Subtask.listFromJson(saved.subtasksJson).first { it.title.contains("apples", true) }.isDone)
    }

    @Test fun progressDoesNotPartiallyChangeAmbiguousOrDatedGroups() = runBlocking {
        say("/plan trip to Goa on 26 July 2029")
        say("add buy apples")
        say("add book flights by 20 July 2029")
        val before = plan().revision
        say("check off apples and flights")
        assertEquals(before, plan().revision)
        say("check off apples and missing item")
        assertEquals(before, plan().revision)
        say("apples is done")
        assertTrue(plan().steps.first().checklist.single().isDone)
        val completedRevision = plan().revision
        say("move apples to 21 July 2029")
        assertEquals(completedRevision, plan().revision)
        assertTrue(plan().steps.first().checklist.single().isDone)
        say("add pack dresses by 20th")
        val pending = plan().pending.toString()
        say("mark apples as not done")
        assertEquals(pending, plan().pending.toString())
        assertFalse(plan().steps.first().checklist.first { it.title.contains("apples", true) }.isDone)
    }

    @Test fun suggestionAcceptanceUsesProposalsAndKeepsNativeChecklistIdentity() = runBlocking {
        say("/plan trip to Goa on 26 July 2029")
        say("give me ideas")
        val proposed = plan().dialogue.proposals.toList()
        assertTrue(proposed.isNotEmpty())
        assertTrue(items().isEmpty())
        say("add those")
        assertEquals(proposed, items())
        assertTrue(plan().dialogue.proposals.isEmpty())
        val before = plan().trip
        say("add those")
        assertEquals(before, plan().trip)
        say("we need photos")
        say("make that 3")
        assertTrue(items().toString(), items().any { it.equals("3 photos", ignoreCase = true) })
        say("remove photos")
        say("bring 3 photos")
        val selected = plan().trip!!.selected
        say("make that 4")
        assertEquals(selected, plan().trip!!.selected)
        assertTrue(items().contains("Bring 4 photos"))
        assertEquals("2029-07-26", plan().facts["travel date"])
        say("move photos to during the trip")
        assertEquals("During the trip", plan().trip!!.items.single { it.id == selected }.phase)
        say("that's it")
        val task = db.taskDao().getAllActiveTasksList().single()
        assertEquals(proposed.size + 1, task.subtasks.size)
        assertTrue(task.subtasks.any { it.id == selected && it.title == "Bring 4 photos" })
    }

    @Test fun fullDateAnswersKeepPendingRoleAndQuestionsNeverMutateDraft() = runBlocking {
        say("/plan ibps exam trip")
        say("the exam is on 26th of an undecided month")
        assertEquals("exam date", plan().pending!!.optString("role"))
        say("bring photos")
        val before = plan().json().toString()
        assertTrue(say("when is my exam?").contains("isn't set"))
        assertEquals(before, plan().json().toString())
        say("27 July 2029")
        assertEquals("2029-07-27", plan().facts["exam date"])
        assertNull(plan().facts["travel date"])
        assertNull(plan().pending)
        say("travel date is 26 July 2029")
        say("to Chennai")
        val revision = plan().revision
        assertTrue(say("when are we leaving?").contains("July 26, 2029"))
        assertEquals(revision, plan().revision)
        say("return date is 26 July 2029")
        assertNull(plan().facts["return date"])
        assertNotNull(plan().pending)
        say("that's it")
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("return date is 28 July 2029")
        assertNull(plan().pending)
        say("that's it")
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }

    @Test fun selectiveSuggestionsAndContextQuestionsSurviveInterruptions() = runBlocking {
        say("/plan trip to Goa")
        say("26th of an undecided month")
        say("give me ideas")
        val suggestions = plan().dialogue.proposals.toList()
        say("add first and third suggestions")
        assertEquals(listOf(suggestions[0], suggestions[2]), items())
        assertEquals("trip_date", plan().pending!!.optString("kind"))
        val revision = plan().revision
        assertTrue(say("did we add pack clothes?").contains("Pack clothes"))
        assertTrue(say("what's next?").contains("month"))
        assertEquals(revision, plan().revision)
        say("add those except accommodation")
        assertFalse(items().contains("Arrange accommodation"))
        assertEquals(3, items().size)
        say("July 2029")
        say("return date is 29 July 2029")
        assertTrue(say("how long is the trip?").contains("4 calendar days"))
        say("can you change destination to Chennai?")
        assertTrue(say("where are we going?").contains("Chennai"))
        say("that's it")
        val task = db.taskDao().getAllActiveTasksList().single()
        assertEquals("Trip to Chennai", task.title)
        assertEquals(3, task.subtasks.size)
    }

    @Test fun relativeFollowupsRecoverMissingReferencesWithoutLosingDateQuestions() = runBlocking {
        say("/plan trip to Goa")
        say("26th of an undecided month")
        say("pack clothes")
        say("book flights")
        say("move luggage to sometime")
        assertEquals("trip_reference", plan().pending!!.optString("kind"))
        say("pack clothes")
        assertEquals("trip_item_date", plan().pending!!.optString("kind"))
        say("2 days before leaving")
        assertEquals("trip_date", plan().pending!!.optString("kind"))
        assertEquals(-2, plan().trip!!.items.single { it.title == "Pack clothes" }.offset)
        assertTrue(say("when should I pack clothes?").contains("travel date not set"))
        say("July 2029")
        assertTrue(say("when should I pack clothes?").contains("July 24, 2029"))
        say("change the date to 28th")
        assertTrue(say("when should I pack clothes?").contains("July 26, 2029"))
        say("please remove flights")
        say("that's it")
        val tasks = db.taskDao().getAllActiveTasksList()
        val root = tasks.single { it.title == "Trip to Goa" }
        val child = tasks.single { it.title == "Pack clothes" }
        assertEquals(root.id, child.planning!!.parentTaskId)
        assertEquals("2029-07-26", date(child.dueDate))
    }

    @Test fun interruptedDateRolesAndConflictsCannotBeSkippedIntoASave() = runBlocking {
        say("/plan ibps exam trip")
        say("travel date is 26th of an undecided month")
        say("exam date is 27th of an undecided month")
        say("bring photos")
        say("August 2029")
        assertEquals("2029-08-27", plan().facts["exam date"])
        assertEquals("travel date", plan().pending!!.optString("role"))
        say("26 July 2029")
        say("change exam date to 28th")
        assertEquals("2029-08-28", plan().facts["exam date"])
        assertEquals("2029-07-26", plan().facts["travel date"])
        say("return date is 27 August 2029")
        say("skip")
        assertEquals("trip_conflict", plan().pending!!.optString("kind"))
        say("that's it")
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("change return date to 29 August 2029")
        assertNull(plan().pending)
        say("clear return date")
        assertNull(plan().facts["return date"])
        say("to Chennai")
        say("that's it")
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }

    @Test fun datedAdditionsRescheduleInPlaceAndSaveLinkedNativeTasks() = runBlocking {
        say("/plan trip to Goa on 26 July 2029")
        say("book flights by 20 July 2029")
        val id = plan().trip!!.items.single().id
        assertEquals("Book flights", items().single())
        assertEquals("2029-07-20", plan().trip!!.items.single().date)
        say("I need to book flights on 21 July 2029")
        assertEquals(id, plan().trip!!.items.single().id)
        assertEquals("2029-07-21", plan().trip!!.items.single().date)
        say("visit Baga beach on 27 July 2029")
        val revision = plan().revision
        val activities = say("what are we doing there?")
        assertTrue(activities.contains("Baga beach")); assertFalse(activities.contains("Book flights"))
        assertEquals(revision, plan().revision)
        say("that's it")
        val tasks = db.taskDao().getAllActiveTasksList()
        val parent = tasks.single { it.title == "Trip to Goa" }
        assertEquals(2, tasks.count { it.planning?.parentTaskId == parent.id })
        assertEquals("2029-07-21", date(tasks.single { it.title == "Book flights" }.dueDate))
    }

    @Test fun itemDateClarificationCanBeReplacedOrRemovedWithoutChangingTravelDate() = runBlocking {
        say("/plan trip to Goa")
        say("travel date is 26th of an undecided month")
        say("download admit card on 24th of an undecided month")
        assertEquals("trip_item_date", plan().pending!!.optString("kind"))
        say("bring photos")
        say("that's it")
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("move admit card to 25 July 2029")
        assertEquals("trip_date", plan().pending!!.optString("kind"))
        say("July 2029")
        assertEquals("2029-07-26", plan().facts["travel date"])
        say("move photos to sometime")
        say("remove photos")
        assertNull(plan().pending)
        say("that's it")
        assertEquals(2, db.taskDao().getAllActiveTasksList().size)
    }

    @Test fun groupedRemovalIsAtomicUndoableAndKeepsCombinedTitlesWhole() = runBlocking {
        say("/plan trip to Goa on 26 July 2029")
        say("buy apples; bring photos; pack clothes")
        val before = plan().trip!!.items
        say("remove apples and missing luggage")
        assertEquals(before, plan().trip!!.items)
        say("remove first and third")
        assertEquals(listOf("Bring photos"), items())
        say("undo")
        assertEquals(before, plan().trip!!.items)
        say("add Pack shirts and socks")
        say("remove Pack shirts and socks")
        assertEquals(before, plan().trip!!.items)
        say("remove apples and photos")
        say("that's it")
        assertEquals(listOf("Pack clothes"), db.taskDao().getAllActiveTasksList().single().subtasks.map { it.title })
    }

    @Test fun dateShiftsUseEachItemsOwnDateAndKeepRelativePreparationLinked() = runBlocking {
        say("/plan trip to Goa on 26 July 2029")
        say("book flights by June 20, 2029")
        assertEquals("2029-06-20", plan().trip!!.items.single().date)
        say("move flights two days later")
        assertEquals("2029-06-22", plan().trip!!.items.single().date)
        assertEquals("2029-07-26", plan().facts["travel date"])
        say("pack clothes 2 days before leaving")
        say("move clothes one day later")
        assertEquals(-1, plan().trip!!.items.single { it.title == "Pack clothes" }.offset)
        say("move trip two days later")
        assertEquals("2029-07-28", plan().facts["travel date"])
        assertTrue(say("when should I pack clothes?").contains("July 27, 2029"))
        say("that's it")
        val tasks = db.taskDao().getAllActiveTasksList()
        assertEquals("2029-06-22", date(tasks.single { it.title == "Book flights" }.dueDate))
        assertEquals("2029-07-27", date(tasks.single { it.title == "Pack clothes" }.dueDate))
    }

    @Test fun movingSeveralItemsIsAtomicAndOneUndoRestoresAllDates() = runBlocking {
        say("/plan trip to Goa on 26 July 2029")
        say("book flights on 20 July 2029; pack clothes on 24 July 2029")
        val before = plan().trip!!.items
        say("move flights and clothes to July 23, 2029")
        assertTrue(plan().trip!!.items.all { it.date == "2029-07-23" })
        say("undo")
        assertEquals(before, plan().trip!!.items)
        say("move flights and missing luggage to 22 July 2029")
        assertEquals(before, plan().trip!!.items)
        say("move flights and clothes to during the trip")
        assertTrue(plan().trip!!.items.all { it.phase == "During the trip" })
        say("undo")
        assertEquals(before, plan().trip!!.items)
    }

    @Test fun undatedItemShiftAsksForDateAndExplicitExamShiftChecksConflicts() = runBlocking {
        say("/plan trip to Goa on 26 July 2029")
        say("bring photos")
        say("move photos two days later")
        assertEquals("trip_item_date", plan().pending!!.optString("kind"))
        assertNull(plan().trip!!.items.single().date)
        say("25 July 2029")
        say("exam date is 27 July 2029")
        say("move exam two days earlier")
        assertEquals("2029-07-27", plan().facts["exam date"])
        assertEquals("trip_conflict", plan().pending!!.optString("kind"))
        say("that's it")
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("cancel that change")
        say("move exam one day later")
        assertEquals("2029-07-28", plan().facts["exam date"])
        say("that's it")
        assertEquals(2, db.taskDao().getAllActiveTasksList().size)
    }

    @Test fun goaConversationCreatesOneNativeChecklistWithFinalDateAndNoDuplicateSave() = runBlocking {
        assertTrue(say("I want to plan a trip to Goa").contains("main task"))
        assertEquals("Goa", plan().facts["destination"])
        assertTrue(say("it's 26th of an undecided month").contains("month"))
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
        say("I'm thinking 26th of an undecided month")
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
        say("/plan trip to Goa on 26 July 2029")
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
        say("/plan trip to Goa on 26 July 2029")
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
        say("26th of an undecided month"); say("bring photos")
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
        say("/plan trip to Goa on 26 July 2029")
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
    @Test fun compactFactsAndJointDateCorrectionRemainAtomic() = runBlocking {
        say("/plan trip")
        say("its for my ibps exam")
        say("Chennai, next Friday, I need to be there by 8am")
        assertEquals("Chennai", plan().facts["destination"])
        assertEquals("8am", plan().facts["arrive by"])
        say("we need train tickets and somewhere to stay")
        say("yes overnight, remove accommodation, add checking the admit card")
        assertEquals(2, items().size)
        assertTrue(plan().facts["travel choice"]!!.contains("overnight"))
        say("the exam is on 27 July 2029")
        say("travel date is 26 July 2029")
        say("travel date is 29 July 2029 and the exam is on 30 July 2029")
        assertEquals("2029-07-29", plan().facts["travel date"])
        assertEquals("2029-07-30", plan().facts["exam date"])
        assertNull(plan().pending)
        say("that's it")
        assertEquals(2, db.taskDao().getAllActiveTasksList().single().subtasks.size)
    }
    @Test fun pendingMonthSurvivesReferencesAndReviewCannotBeReused() = runBlocking {
        say("/plan trip")
        say("what should we do")
        assertNull(plan().facts["destination"])
        say("add Book train and book hotel")
        assertEquals("destination", plan().dialogue.question)
        say("26th of an undecided month")
        say("rename book to Booking")
        say("the second one")
        assertEquals("trip_date", plan().pending!!.getString("kind"))
        say("remove train")
        assertEquals("trip_date", plan().pending!!.getString("kind"))
        say("July 2029")
        say("save plan")
        assertNotNull(plan().reviewedRevision)
        say("move Booking to 25th")
        // Established month makes this complete; a new missing item date must still revoke authority.
        assertNull(plan().reviewedRevision)
        say("save plan")
        say("move Booking to sometime")
        assertNull(plan().reviewedRevision)
        assertEquals("trip_item_date", plan().pending!!.getString("kind"))
        say("cancel that change")
        say("apply plan")
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun refreshPreservesNativeCompletionAndReaddedDatedIdentity() = runBlocking {
        say("/plan trip to Goa")
        say("26 July 2029")
        say("add Pack clothes and book flights")
        say("move flights to 20 July 2029")
        say("that's it")
        val root = db.taskDao().getAllActiveTasksList().single { it.subtasks.isNotEmpty() }
        val flight = db.taskDao().getAllActiveTasksList().single { it.id != root.id }
        say("the exam is on 31 February 2029")
        val clarification = plan().pending!!.toString()
        val edited = root.subtasks.single().copy(title = "Pack light clothes", isDone = true)
        db.taskDao().updateTask(root.copy(subtasksJson = org.json.JSONArray(listOf(edited.toJsonObject())).toString(), updatedAt = root.updatedAt + 1))
        say("refresh saved tasks")
        assertEquals(clarification, plan().pending!!.toString())
        say("cancel that change")
        assertTrue(items().contains("Pack light clothes"))
        assertTrue(plan().steps.single { it.id == plan().trip!!.rootId }.checklist.single().isDone)
        val id = plan().trip!!.items.single { it.title == "Book flights" }.id
        say("remove flying")
        say("add Book flights")
        assertEquals(id, plan().trip!!.items.single { it.title == "Book flights" }.id)
        say("that's it")
        assertEquals(setOf(root.id, flight.id), db.taskDao().getAllActiveTasksList().map { it.id }.toSet())
        assertTrue(db.taskDao().getTaskById(root.id)!!.subtasks.single().isDone)
        say("undo saved changes")
        assertEquals(setOf(root.id, flight.id), db.taskDao().getAllActiveTasksList().map { it.id }.toSet())
        say("archive plan")
        assertNull(ConversationPlanStore(context).active())
        say("/plan resume plan Trip to Goa")
        assertNotNull(plan().trip)
        plan().validate()
    }
    @Test fun oldDraftMigrationAndAdvancedPromotionRetainIdsAndDates() = runBlocking {
        val first = PlanStep(title = "Book train")
        val second = PlanStep(title = "Print tickets", deadline = Instant.parse("2029-07-20T10:00:00Z").toEpochMilli())
        val old = PlanWorkspace(goal = "Trip to Goa", domain = "trip", steps = listOf(first, second), facts = mapOf("destination" to "Goa", "travel date" to "2029-07-26"))
        ConversationPlanStore(context).put(old)
        say("/plan show plan")
        assertEquals(old.id, plan().id)
        assertEquals("2029-07-26", date(plan().steps.single { it.id == plan().trip!!.rootId }.deadline))
        assertEquals(setOf(first.id, second.id), plan().trip!!.items.map { it.id }.toSet())
        assertEquals(second.deadline, plan().steps.single { it.id == second.id }.deadline)
        say("give Book train 30 minutes")
        assertNull(plan().trip)
        assertEquals(setOf(first.id, second.id), plan().steps.map { it.id }.toSet())
        assertEquals(30, plan().steps.single { it.id == first.id }.minutes)
        say("Print tickets depends on Book train")
        assertEquals(setOf(first.id), plan().steps.single { it.id == second.id }.prerequisites)
        say("save plan"); say("apply plan")
        assertEquals(2, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun initialExamDateIsNotDepartureAndProjectionRejectsLostItems() = runBlocking {
        say("/plan trip to Chennai for an exam on 27 July 2029")
        assertEquals("2029-07-27", plan().facts["exam date"])
        assertNull(plan().facts["travel date"])
        say("add Pens")
        val original = plan()
        assertThrows(IllegalArgumentException::class.java) {
            original.copy(steps = original.steps.map { it.copy(checklist = emptyList()) }).validate()
        }
        say("save this plan as template Exam travel")
        say("use template Exam travel")
        assertTrue(plan().steps.any { it.title == "Pens" })
        assertTrue(plan().steps.all { it.deadline == null && it.taskSnapshot == null })
    }

    @Test fun refreshKeepsRelativeDatesAndNativeTitleWhileAdvancedSaveMovesOnlyOpenItems() = runBlocking {
        say("/plan trip to Kyoto")
        say("26 July 2029")
        say("add Pack clothes the day before leaving")
        say("add Book flights and print tickets")
        say("that's it")
        val root = db.taskDao().getAllActiveTasksList().single { it.subtasks.isNotEmpty() }
        val checks = root.subtasks.map { it.copy(isDone = it.title == "Print tickets") }
        db.taskDao().updateTask(root.copy(title = "Our Kyoto adventure", subtasksJson = Subtask.listToJson(checks), updatedAt = root.updatedAt + 1))
        say("refresh saved tasks")
        say("make it 28th")
        assertEquals("2029-07-27", date(plan().steps.single { it.title == "Pack clothes" }.deadline))
        assertEquals("Our Kyoto adventure", plan().steps.single { it.id == plan().trip!!.rootId }.title)
        say("use advanced planning")
        assertNull(plan().trip)
        say("save plan"); say("apply plan")
        val saved = db.taskDao().getTaskById(root.id)!!
        assertEquals(listOf("Print tickets"), saved.subtasks.map { it.title })
        assertTrue(saved.subtasks.single().isDone)
        assertEquals(1, db.taskDao().getAllActiveTasksList().count { it.title == "Book flights" })
        assertEquals(root.id, db.taskDao().getAllActiveTasksList().single { it.title == "Book flights" }.planning!!.parentTaskId)
    }

    @Test fun phasesAndPreparationWarningsUseTheActualDestinationAndDates() = runBlocking {
        say("/plan trip to Kyoto")
        say("tomorrow")
        assertTrue(say("add Pack clothes 3 days before leaving").contains("past"))
        say("add Visit shrines while in Kyoto")
        say("add Print photos after returning from Kyoto")
        assertEquals("During the trip", plan().trip!!.items.single { it.title == "Visit shrines" }.phase)
        assertEquals("After returning", plan().trip!!.items.single { it.title == "Print photos" }.phase)
        say("add Book flights")
        assertTrue(say("move flights to 26 July 2029").contains("after departure"))
    }

}
