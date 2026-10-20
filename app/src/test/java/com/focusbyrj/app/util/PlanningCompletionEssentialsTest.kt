package com.focusbyrj.app.util

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.util.assistant.*
import com.focusbyrj.app.util.backup.DataSafetyManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/** Essential invariants only: native writes, alarm identity, crash recovery and retained authority. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlanningCompletionEssentialsTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    private lateinit var oldZone: TimeZone
    private val now get() = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2027, 0, 4, 8, 0) }.timeInMillis
    @Before fun setup() {
        oldZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        ConversationPlanStore.clearAll(context); PlanningLibrary(context).clear(); AssistantSessionStore(context).clear(); ChatModeStore.set(context, ChatMode.CHAT)
        File(context.filesDir, "work_reminder_ledger.enc").delete()
    }
    @After fun cleanup() {
        ConversationPlanStore.clearAll(context); PlanningLibrary(context).clear(); AssistantSessionStore(context).clear()
        ChatModeStore.set(context, ChatMode.CHAT); db.close(); TimeZone.setDefault(oldZone)
    }
    private suspend fun say(text: String) = OfflineAssistant.respond(text, context)?.formattedText ?: error("Unhandled: $text")
    private fun plan() = ConversationPlanStore(context).active()!!
    private suspend fun save(plan: PlanWorkspace): ConversationPlanCommitter.Result {
        val at = System.currentTimeMillis()
        return ConversationPlanCommitter.commit(context, plan.copy(reviewedRevision = plan.revision, reviewedAt = at, reviewProcess = "test:review"), at, "test")
    }
    private fun session(id: String, hour: Int, minutes: Int = 20) = WorkSession(id, now + (hour - 8) * 3_600_000L, minutes)

    @Test fun separateWorkAlarmsSurviveWithoutDeadlineAndMovesCancelOldIdentities(): Unit = runBlocking {
        val base = System.currentTimeMillis() + 3_600_000
        val sessions = listOf(WorkSession("one", base, 20), WorkSession("two", base + 3_600_000, 20))
        val metadata = TaskPlanning(minutes = 40).withSessions(sessions)
        val task = Task(id = 41, title = "Study", dueDate = base + 5 * 3_600_000, planningJson = metadata.json())
        val alarms = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        TaskReminderHelper.scheduleReminder(context, task)
        assertEquals(3, alarms.scheduledAlarms.size)
        val moved = metadata.withSessions(sessions.map { if (it.id == "one") it.copy(start = base + 2 * 3_600_000) else it })
        TaskReminderHelper.scheduleReminder(context, task.copy(dueDate = null, planningJson = moved.json()))
        assertEquals(setOf(base + 3_600_000, base + 2 * 3_600_000), alarms.scheduledAlarms.map { it.triggerAtTime }.toSet())
        TaskReminderHelper.cancelReminder(context, task)
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }
    @Test fun workDeliveryRejectsMovedCancelledCompletedAndExpiredSessions() {
        val slot = session("work", 9)
        val task = Task(title = "Research", planningJson = TaskPlanning(minutes = 20).withSessions(listOf(slot)).json())
        assertNotNull(WorkSessionReminders.currentSession(task, "work", slot.start, slot.start))
        assertNull(WorkSessionReminders.currentSession(task, "work", slot.start - 1, slot.start))
        assertNull(WorkSessionReminders.currentSession(task.copy(isCompleted = true), "work", slot.start, slot.start))
        assertNull(WorkSessionReminders.currentSession(task.copy(planningJson = task.planning!!.copy(workReminders = false).json()), "work", slot.start, slot.start))
        assertNull(WorkSessionReminders.currentSession(task, "work", slot.start, slot.end))
    }
    @Test fun duplicateDeliveryPostsOnlyOnceAndReadsCurrentTask(): Unit = runBlocking {
        val slot = session("work", 9)
        val id = db.taskDao().insertTask(Task(title = "Current title", planningJson = TaskPlanning(minutes = 20).withSessions(listOf(slot)).json()))
        WorkSessionReminders.deliver(context, id, slot.id, slot.start, slot.start)
        WorkSessionReminders.deliver(context, id, slot.id, slot.start, slot.start)
        val notifications = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        assertEquals(1, notifications.allNotifications.size)
        db.taskDao().getTaskById(id)!!.let { db.taskDao().updateTask(it.copy(isCompleted = true)) }
        WorkSessionReminders.deliver(context, id, slot.id, slot.start, slot.start)
        assertEquals(1, notifications.allNotifications.size)
    }
    @Test fun multiSessionCanonicalSaveKeepsGapsFreeAndSerializesExactIntervals(): Unit = runBlocking {
        val step = PlanStep(title = "Study", minutes = 40).withSessions(listOf(session("first", 9), session("second", 11)))
        val gap = PlanStep(title = "Errand", minutes = 20, start = now + 2 * 3_600_000)
        val p = PlanWorkspace(goal = "Exam", steps = listOf(step, gap))
        p.validate()
        assertFalse(PlanWorkspace.overlaps(step, gap))
        save(p)
        val row = db.taskDao().getAllTasksList().single { it.title == "Study" }
        assertEquals(step.sessions, row.planning!!.sessions)
        assertEquals(row, TaskJson.decode(TaskJson.encode(row)))
        assertEquals(step.sessions, PlanWorkspace.read(p.json()).steps.first().sessions)
    }
    @Test fun chunkSchedulerHonorsBuffersWeekdaysAndDailyCapacity() {
        val step = PlanStep(title = "Revision", minutes = 140)
        val p = PlanWorkspace(steps = listOf(step), policy = PlanPolicy(bufferMinutes = 5, workingDays = (2..6).toSet(), maximumSession = 45, dailyMinutes = 90))
        val result = PlanScheduler.arrange(p, now, days = 2, startHour = 9, endHour = 12)
        assertTrue(result.unscheduled.isEmpty())
        val work = result.steps.single().workSessions
        assertEquals(140, work.sumOf { it.minutes }); assertEquals(4, work.size)
        work.zipWithNext().forEach { (a, b) -> assertTrue(a.end + 5 * 60_000 <= b.start) }
        val days = work.groupBy { Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = it.start }.get(Calendar.DAY_OF_YEAR) }
        assertTrue(days.values.all { sessions -> sessions.sumOf { it.minutes } <= 90 })
    }
    @Test fun infeasibleChunkedTaskNeverPublishesIncompleteAllocation() {
        val step = PlanStep(title = "Revision", minutes = 60, deadline = now + 80 * 60_000)
        val p = PlanWorkspace(steps = listOf(step), policy = PlanPolicy(maximumSession = 15))
        val result = PlanScheduler.arrange(p, now, days = 1, startHour = 9, endHour = 10)
        assertEquals(listOf(step.id), result.unscheduled); assertTrue(result.steps.single().workSessions.isEmpty())
        assertTrue(result.reasons.getValue(step.id).contains("cannot fit"))
    }
    @Test fun estimateRangeReservesUpperBoundAndEarliestStart() {
        val step = PlanStep(title = "Revision", minutes = 20, maximumEstimate = 40, earliestStart = now + 2 * 3_600_000)
        val result = PlanScheduler.arrange(PlanWorkspace(steps = listOf(step)), now, days = 1, startHour = 9, endHour = 12)
        assertEquals(40, result.steps.single().workSessions.sumOf { it.minutes })
        assertEquals(step.earliestStart, result.steps.single().start)
    }
    @Test fun draftHistoryHasMultipleUndoRedoStepsWithoutNativeWrites(): Unit = runBlocking {
        say("/plan trip"); say("add Research for 20 minutes"); say("add Packing for 30 minutes")
        say("rename Research to Transport research")
        say("undo"); assertEquals("Research", plan().steps.first().title)
        say("undo"); assertEquals(1, plan().steps.size)
        say("redo"); assertEquals(2, plan().steps.size)
        say("redo"); assertEquals("Transport research", plan().steps.first().title)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun savedHistoryUndoRedoSurvivesSessionResetAndDoesNotDuplicateTasks(): Unit = runBlocking {
        say("/plan project"); say("add Prototype"); say("save plan"); say("apply plan")
        say("rename Prototype to Finished prototype"); say("save plan"); say("apply plan")
        AssistantSessionStore(context).clear()
        say("undo saved changes"); assertEquals("Prototype", db.taskDao().getAllActiveTasksList().single().title)
        say("undo saved changes"); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        say("redo saved changes"); assertEquals("Prototype", db.taskDao().getAllActiveTasksList().single().title)
        say("redo saved changes"); assertEquals("Finished prototype", db.taskDao().getAllActiveTasksList().single().title)
        assertEquals(1, db.taskDao().getAllTasksList().size)
    }
    @Test fun savedHistoryRefusesToOverwriteLaterNativeEdit(): Unit = runBlocking {
        say("/plan project"); say("add Prototype"); say("save plan"); say("apply plan")
        val before = db.taskDao().getAllTasksList().single()
        val edited = before.copy(details = "Native detail", updatedAt = before.updatedAt + 1)
        db.taskDao().updateTask(edited)
        assertTrue(say("undo saved changes").contains("changed outside"))
        assertEquals(edited, db.taskDao().getTaskById(before.id))
    }
    @Test fun historyCrashRetryReusesCommittedReceipt(): Unit = runBlocking {
        say("/plan project"); say("add Prototype"); say("save plan"); say("apply plan")
        val id = plan().id
        try { SavedPlanHistory.move(context, id, redo = false, beforeFileCommit = { error("injected history crash") }); fail("Expected failure") }
        catch (e: IllegalStateException) { assertEquals("injected history crash", e.message) }
        val committed = db.taskDao().getAllTasksList().single()
        assertTrue(committed.isTrashed)
        SavedPlanHistory.move(context, id, redo = false)
        assertEquals(committed, db.taskDao().getAllTasksList().single())
        SavedPlanHistory.move(context, id, redo = true)
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun nativeHierarchyAndSessionEditsRejectCyclesConflictsAndStaleVersions(): Unit = runBlocking {
        val first = db.taskDao().insertTask(Task(title = "First", planningJson = TaskPlanning(minutes = 20).withSessions(listOf(session("one", 9))).json()))
        val child = db.taskDao().insertTask(Task(title = "Child", planningJson = TaskPlanning(parentTaskId = first).json()))
        val parent = db.taskDao().getTaskById(first)!!
        try { db.taskDao().updateTaskChecked(parent.copy(planningJson = parent.planning!!.copy(parentTaskId = child).json())); fail("Cycle") }
        catch (_: IllegalArgumentException) { }
        try { db.taskDao().insertTaskChecked(Task(title = "Conflict", planningJson = parent.planningJson)); fail("Conflict") }
        catch (_: IllegalArgumentException) { }
        assertEquals(2, db.taskDao().getAllTasksList().size)
        db.taskDao().updateTask(parent.copy(updatedAt = parent.updatedAt + 1))
        try { db.taskDao().updateTaskChecked(parent.copy(title = "Stale"), parent.updatedAt); fail("Stale") }
        catch (_: IllegalArgumentException) { }
        assertEquals("First", db.taskDao().getTaskById(first)!!.title)
    }
    @Test fun rollingSnapshotRestoresWorkspaceLibraryAndClearsAuthority(): Unit = runBlocking {
        val noteDao = NoteDatabase.getInstance(context).noteDao(); noteDao.deleteAllNotes()
        say("/plan a trip"); say("add Research for 20 minutes"); say("save plan")
        val original = plan(); PlanningLibrary(context).saveTemplate("Trip", original)
        val path = DataSafetyManager.writePreOpSnapshot(context, noteDao, "completion_test", db)!!
        ConversationPlanStore.clearAll(context); PlanningLibrary(context).clear()
        val result = DataSafetyManager.restoreSnapshot(context, path, noteDao, db)
        assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
        assertEquals(original.steps.single().title, plan().steps.single().title)
        assertNotEquals(original.id, plan().id); assertNull(plan().reviewedRevision)
        assertEquals(listOf("Trip"), PlanningLibrary(context).list("templates"))
        assertEquals(ChatMode.CHAT, ChatModeStore.get(context)); assertTrue(db.taskDao().getAllTasksList().isEmpty())
        File(path).delete()
    }
    @Test fun savedDiscussionRestoresAssertionsWithoutPendingQuestionsOrTaskAuthority(): Unit = runBlocking {
        say("let's talk through travel"); say("options are Train or Bus"); say("Train costs INR 400")
        say("compare options")
        say("save discussion as Travel")
        AssistantSessionStore(context).clear()
        say("resume discussion Travel")
        assertTrue(say("what did we decide").contains("Train"))
        assertTrue(AssistantDiscussion.hasContext(AssistantSessionStore(context).load()))
        assertNull(AssistantSessionStore(context).load().confirmationId)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun templatesRemoveNativeLinksAndDatesAndRequireReview(): Unit = runBlocking {
        val step = PlanStep(title = "Research", minutes = 20, start = now + 3_600_000, deadline = now + 86_400_000)
        val saved = save(PlanWorkspace(goal = "Trip", steps = listOf(step))).plan
        PlanningLibrary(context).saveTemplate("Trip", saved)
        val copy = PlanningLibrary(context).useTemplate("Trip")
        assertNotEquals(saved.id, copy.id); assertNull(copy.steps.single().taskSnapshot)
        assertNull(copy.steps.single().start); assertNull(copy.steps.single().deadline); assertNull(copy.reviewedRevision)
        assertEquals(1, db.taskDao().getAllTasksList().size)
    }
    @Test fun numericRangesDoNotInventCertainWinnersAndBlockUnconfirmedHardLimits(): Unit = runBlocking {
        val options = listOf("Train", "Bus")
        var comparison = DiscussionComparison()
        comparison = comparison.answer("Train costs INR 400 to 600", options)!!.state
        comparison = comparison.answer("Bus costs INR 500", options)!!.state
        val report = comparison.compare(options).text
        assertTrue(report, report.contains("overlap")); assertFalse(report.contains("has the lowest"))
        comparison = comparison.answer("cost at most INR 550", options)?.state ?: comparison
        // Directly install the same typed bound to verify choice safety independently of phrase coverage.
        comparison = comparison.copy(limits = listOf(DiscussionComparison.Bound(DiscussionComparison.Metric.COST, DiscussionComparison.Quantity(550.toBigDecimal(), "INR"))))
        assertTrue(comparison.choiceProblem("Train")!!.contains("may exceed"))
        assertEquals(comparison.observations, DiscussionComparison.read(comparison.json(), options).observations)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun compoundDurationIsNotMistakenForAnUncertaintyRange() {
        val comparison = DiscussionComparison().answer("Train takes one hour and thirty minutes", listOf("Train", "Bus"))!!.state
        assertEquals(90.toBigDecimal(), comparison.observations.single().quantity.amount)
        assertNull(comparison.observations.single().quantity.upper)
    }
    @Test fun listeningKeepsContextWithoutCreatingTasks(): Unit = runBlocking {
        say("just listen"); say("I am worried about my trip")
        val response = say("I need some time to think")
        assertTrue(response, response.contains("I hear you"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun dependentReplanPreservesUnrelatedAndProtectedWork() {
        val research = PlanStep(title = "Research", minutes = 30, start = now + 3_600_000)
        val booking = PlanStep(title = "Booking", minutes = 20, start = now + 2 * 3_600_000, prerequisites = setOf(research.id), afterMinutes = 15)
        val fixed = PlanStep(title = "Protected meeting", minutes = 20, start = now + 4 * 3_600_000, protected = true)
        val original = PlanWorkspace(goal = "Trip", steps = listOf(research, booking, fixed), facts = mapOf("availability" to "9:00–17:00 daily (next 28 days)"))
        val result = AdvancedPlanLanguage.edit(original, "move Research to tomorrow at 11am and replan its dependents", now, emptyList())!!.plan
        assertEquals(fixed, result.steps.last())
        assertEquals(result.steps.first().end!! + 15 * 60_000, result.steps[1].start)
        assertEquals(booking.afterMinutes, PlanWorkspace.read(result.json()).steps[1].afterMinutes)
        result.validate()
    }
    @Test fun authoredBreakdownRequiresExplicitAcceptanceAndCreatesOnlyDraftChildren() {
        val research = PlanStep(title = "Research", minutes = 30)
        val original = PlanWorkspace(goal = "Trip", steps = listOf(research))
        val proposal = AdvancedPlanLanguage.edit(original, "break down Research", now, emptyList())!!.plan
        assertEquals(1, proposal.steps.size); assertNotNull(proposal.pending)
        val accepted = AdvancedPlanLanguage.edit(proposal, "use that breakdown", now, emptyList())!!.plan
        assertEquals(4, accepted.steps.size)
        assertTrue(accepted.steps.drop(1).all { it.parentId == research.id && it.start == null && it.taskSnapshot == null })
    }
    @Test fun recurringWorkKeepsLocalClockAcrossDstAndDropsOldGraph() {
        val zone = TimeZone.getTimeZone("America/New_York")
        fun clock(day: Int, hour: Int, minute: Int = 0) = Calendar.getInstance(zone).apply { clear(); set(2027, Calendar.MARCH, day, hour, minute) }.timeInMillis
        val oldDue = clock(7, 9); val nextDue = clock(14, 9)
        val original = TaskPlanning(parentTaskId = 9, prerequisiteTaskIds = setOf(8), minutes = 20, start = clock(7, 8), timezone = zone.id)
        val next = original.forNextOccurrence(oldDue, nextDue)
        assertNull(next.parentTaskId); assertTrue(next.prerequisiteTaskIds.isEmpty())
        assertEquals(clock(14, 8), next.start)
        assertEquals(167 * 3_600_000L, next.start!! - original.start!!)
        assertNull(original.copy(start = clock(7, 2, 30)).forNextOccurrence(oldDue, nextDue).start)
    }
    @Test fun newReminderIntentChangesReceiptHashButLegacyDisabledIntentRemainsCompatible() {
        val step = PlanStep(id = "compat-step", title = "Research", minutes = 30, workReminders = false)
        val old = PlanWorkspace(id = "compat-plan", goal = "Compatibility", timezone = "UTC", steps = listOf(step))
        assertEquals("94b42a99fb836a933e463f8ea1ed896ba260fa9132eaf0aaa42d61fdf48b84ef", ConversationPlanCommitter.hash(old))
        assertNotEquals(ConversationPlanCommitter.hash(old), ConversationPlanCommitter.hash(old.copy(steps = listOf(step.copy(workReminders = true)))))
    }

    @Test fun explicitPlanZoneCanDifferFromDeviceAfterFreshReview(): Unit = runBlocking {
        say("/plan trip"); say("add Research for 20 minutes")
        say("use time zone Asia/Tokyo"); say("save plan"); say("apply plan")
        assertEquals("Asia/Tokyo", db.taskDao().getAllTasksList().single().planning!!.timezone)
    }
    @Test fun deviceZoneChangeInvalidatesReviewWithoutLosingThePlan(): Unit = runBlocking {
        say("/plan trip"); say("add Research"); say("save plan")
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        assertTrue(say("apply plan").contains("time zone changed"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("save plan"); say("apply plan")
        assertEquals(1, db.taskDao().getAllTasksList().size)
    }
    @Test fun clockFoldAndGapWindowsAreNotGuessed() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val fold = Calendar.getInstance(zone).apply { clear(); set(2027, Calendar.NOVEMBER, 7, 1, 0); isLenient = false }
        val gap = Calendar.getInstance(zone).apply { clear(); set(2027, Calendar.MARCH, 14, 2, 30); isLenient = false }
        assertNull(PlanClock.boundary(fold)); assertNull(PlanClock.boundary(gap))
    }

    @Test fun versionOnePlansAndNativeMetadataRemainReadableWithoutEnablingNewAlarms() {
        val old = PlanWorkspace(goal = "Legacy", steps = listOf(PlanStep(title = "Research", minutes = 20))).json().put("version", 1)
        old.getJSONArray("steps").getJSONObject(0).remove("workReminders")
        val restored = PlanWorkspace.read(old)
        assertFalse(restored.steps.single().workReminders)
        assertEquals(2, restored.json().getInt("version"))
        val metadata = JSONObject(TaskPlanning(minutes = 20).json()).put("version", 1).apply { remove("sessions"); remove("workReminders"); remove("afterMinutes") }
        assertFalse(TaskPlanning.read(metadata.toString())!!.workReminders)
        assertEquals(2, JSONObject(TaskPlanning.read(metadata.toString())!!.json()).getInt("version"))
    }
    @Test fun freeDayConstraintCoversWorkThatCrossesMidnight() {
        val thursday = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2027, Calendar.JANUARY, 7, 23, 30) }.timeInMillis
        val step = PlanStep(title = "Late work", minutes = 90, start = thursday)
        val plan = PlanWorkspace(steps = listOf(step), policy = PlanPolicy(workingDays = (1..7).toSet() - Calendar.FRIDAY))
        assertThrows(IllegalArgumentException::class.java) { plan.validate() }
        assertTrue(plan.copy(policy = PlanPolicy()).let { runCatching { it.validate() }.isSuccess })
    }

    @Test fun rangeCurrencyClarificationRetainsBothSuppliedBoundsAcrossReload() {
        val options = listOf("Train", "Bus")
        val awaiting = DiscussionComparison().answer("Train costs 400 to 600", options)!!.state
        assertEquals(400.toBigDecimal(), awaiting.pending!!.deferredCost)
        assertEquals(600.toBigDecimal(), awaiting.pending!!.deferredUpper)
        val reloaded = DiscussionComparison.read(awaiting.json(), options)
        val answered = reloaded.answer("INR", options)!!.state
        assertEquals(400.toBigDecimal(), answered.observations.single().quantity.amount)
        assertEquals(600.toBigDecimal(), answered.observations.single().quantity.upper)
        assertEquals(2, answered.json().getInt("version"))
    }

    @Test fun clearingChatPreservesExplicitLibraryButForgettingLibraryDeletesIt(): Unit = runBlocking {
        val library = PlanningLibrary(context)
        library.saveTemplate("Trip", PlanWorkspace(goal = "Trip", steps = listOf(PlanStep(title = "Research"))))
        library.setDefaultMinutes(30)
        say("/plan project"); say("add Prototype")
        OfflineAssistant.reset(context)
        assertTrue(ConversationPlanStore(context).all().isEmpty())
        assertEquals(listOf("Trip"), library.list("templates")); assertEquals(30, library.defaultMinutes())
        say("forget planning library")
        assertTrue(library.list("templates").isEmpty()); assertNull(library.defaultMinutes())
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun rangedHardLimitAsksForOneMaximumWithoutInventingABound() {
        val before = DiscussionComparison()
        val result = before.answer("cost limit is INR 400 to 600", listOf("Train", "Bus"))!!
        assertTrue(result.text.contains("one maximum")); assertTrue(result.state.limits.isEmpty())
    }

    @Test fun adoptingNativeTaskReusesItsIdAndPreservesDetailsUntilReviewed(): Unit = runBlocking {
        val id = db.taskDao().insertTask(Task(title = "Research", details = "Native details"))
        val original = db.taskDao().getTaskById(id)!!
        say("/plan project"); say("use existing task Research"); say("give Research 30 minutes")
        assertEquals(original, db.taskDao().getTaskById(id))
        say("save plan"); say("apply plan")
        val rows = db.taskDao().getAllTasksList(); assertEquals(listOf(id), rows.map { it.id })
        assertEquals("Native details", rows.single().details); assertEquals(30, rows.single().planning!!.minutes)
    }
    @Test fun adoptionPreservesCompletedPrerequisitesAsReadonlyContext(): Unit = runBlocking {
        val parent = Task(id = 71, title = "Completed research", isCompleted = true, completedAt = now)
        db.taskDao().insertTask(parent)
        val id = db.taskDao().insertTask(Task(title = "Booking", planningJson = TaskPlanning(prerequisiteTaskIds = setOf(parent.id), minutes = 20).json()))
        say("/plan trip"); say("use existing task Booking")
        assertEquals(2, plan().steps.size); assertTrue(plan().steps.last().completed)
        say("schedule Booking to tomorrow at 9am"); say("save plan"); say("apply plan")
        assertEquals(parent, db.taskDao().getTaskById(parent.id))
        assertEquals(setOf(parent.id), db.taskDao().getTaskById(id)!!.planning!!.prerequisiteTaskIds)
    }
    @Test fun completedContextIsNeverAutomaticallyScheduled() {
        val done = Task(id = 71, title = "Done", isCompleted = true)
        val parent = PlanStep(title = done.title, minutes = 30, taskSnapshot = TaskJson.encode(done).toString())
        val child = PlanStep(title = "Next", minutes = 20, prerequisites = setOf(parent.id))
        val result = PlanScheduler.arrange(PlanWorkspace(steps = listOf(parent, child)), now, days = 1, startHour = 9, endHour = 12)
        assertTrue(result.unscheduled.isEmpty()); assertNull(result.steps.first().start); assertNotNull(result.steps.last().start)
    }
    @Test fun chatAgendaIncludesWorkAndExcludesFutureWorkAndDistantOccasions(): Unit = runBlocking {
        fun at(days: Int) = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_YEAR, days) }.timeInMillis
        val today = db.taskDao().insertTask(Task(title = "Work today", planningJson = TaskPlanning(minutes = 20, start = at(0)).json()))
        db.taskDao().insertTask(Task(title = "Future work", planningJson = TaskPlanning(minutes = 20, start = at(2)).json()))
        db.taskDao().insertTask(Task(title = "Distant birthday", type = TaskType.BIRTHDAY, dueDate = at(90)))
        val answer = say("my agenda")
        assertTrue(answer, answer.contains("Work today")); assertFalse(answer.contains("Future work")); assertFalse(answer.contains("Distant birthday"))
        assertEquals(listOf(today), AssistantSessionStore(context).load().taskListIds)
    }
    @Test fun deadlineOnlyFiltersDoNotMistakeWorkForADueDate() {
        val start = now; val end = now + 86_400_000
        val task = Task(title = "Work", dueDate = end + 3_600_000, planningJson = TaskPlanning(minutes = 20, start = start + 3_600_000).json())
        assertTrue(TaskScheduleView.matches(task, start, end))
        assertFalse(TaskScheduleView.matches(task, start, end, deadlinesOnly = true))
        assertTrue(TaskScheduleView.describe(task).contains("work")); assertTrue(TaskScheduleView.describe(task).contains("due"))
    }
    @Test fun nativeEditAfterAdoptionStillBlocksCanonicalApply(): Unit = runBlocking {
        val id = db.taskDao().insertTask(Task(title = "Research"))
        say("/plan project"); say("use existing task Research"); say("rename Research to New research"); say("save plan")
        val row = db.taskDao().getTaskById(id)!!; val native = row.copy(details = "New native detail", updatedAt = row.updatedAt + 1)
        db.taskDao().updateTask(native)
        assertTrue(say("apply plan").contains("changed outside")); assertEquals(native, db.taskDao().getTaskById(id))
    }
    @Test fun undoDoesNotOverwriteAnUnchangedContextRecordEditedElsewhere(): Unit = runBlocking {
        val parent = Task(id = 71, title = "Done", isCompleted = true)
        db.taskDao().insertTask(parent)
        val id = db.taskDao().insertTask(Task(title = "Next", planningJson = TaskPlanning(prerequisiteTaskIds = setOf(parent.id)).json()))
        val before = db.taskDao().getTaskById(id)!!
        say("/plan project"); say("use existing task Next"); say("give Next 20 minutes"); say("save plan"); say("apply plan")
        val native = parent.copy(details = "Native context edit", updatedAt = parent.updatedAt + 1)
        db.taskDao().updateTask(native)
        assertTrue(say("undo saved changes").contains("Undid"))
        assertEquals(native, db.taskDao().getTaskById(parent.id)); assertEquals(before.planningJson, db.taskDao().getTaskById(id)!!.planningJson)
    }
    @Test fun occasionParentRemainsReadonlyWhenPlanningItsWorkTask(): Unit = runBlocking {
        val occasion = Task(id = 71, title = "Birthday", type = TaskType.BIRTHDAY, dueDate = System.currentTimeMillis() + 86_400_000)
        db.taskDao().insertTask(occasion)
        db.taskDao().insertTask(Task(title = "Buy gift", planningJson = TaskPlanning(parentTaskId = occasion.id).json()))
        say("/plan trip"); say("use existing task Buy gift"); say("give Buy gift 20 minutes"); say("save plan"); say("apply plan")
        assertEquals(occasion, db.taskDao().getTaskById(occasion.id))
    }

    @Test fun nativeSelectionCanBecomeASeparatePlanWithoutWritingOrReusingStaleReferences(): Unit = runBlocking {
        val id = db.taskDao().insertTask(Task(title = "Research", details = "Keep this detail"))
        val original = db.taskDao().getTaskById(id)!!
        val sessions = AssistantSessionStore(context)
        for (command in listOf("plan it", "/talk plan it", "/plan")) {
            ConversationPlanStore.clearAll(context); ChatModeStore.set(context, ChatMode.CHAT)
            sessions.save(sessions.load().apply { focusTask(id) })
            assertTrue(command, say(command).contains("selected saved task"))
            assertEquals(id, TaskJson.decode(JSONObject(plan().steps.single().taskSnapshot!!)).id)
            assertEquals(original, db.taskDao().getTaskById(id))
        }
        ConversationPlanStore.clearAll(context); ChatModeStore.set(context, ChatMode.CHAT)
        sessions.save(sessions.load().apply { focusTask(id); referenceAt = 1L })
        say("/plan")
        assertTrue(plan().steps.isEmpty()); assertEquals(original, db.taskDao().getTaskById(id))
    }

    @Test fun removingTheLastSavedStepRequiresReviewAndCanBeUndone(): Unit = runBlocking {
        say("/plan project"); say("add Prototype"); say("save plan"); say("apply plan")
        val original = db.taskDao().getAllActiveTasksList().single()
        say("remove Prototype")
        assertTrue(plan().steps.isEmpty()); assertEquals(1, plan().removed.size)
        assertFalse(db.taskDao().getTaskById(original.id)!!.isTrashed)
        say("save plan")
        assertFalse(db.taskDao().getTaskById(original.id)!!.isTrashed)
        say("apply plan")
        assertTrue(db.taskDao().getTaskById(original.id)!!.isTrashed)
        say("undo saved changes")
        val restored = db.taskDao().getTaskById(original.id)!!
        assertFalse(restored.isTrashed); assertEquals(original.title, restored.title)
        assertEquals(original.planningJson, restored.planningJson)
    }

    @Test fun reusingATemplateOmitsOccasionContextWithoutCopyingItsNativeLinks(): Unit = runBlocking {
        val occasion = Task(id = 71, title = "Birthday", type = TaskType.BIRTHDAY)
        db.taskDao().insertTask(occasion)
        val id = db.taskDao().insertTask(Task(title = "Buy gift", planningJson = TaskPlanning(parentTaskId = occasion.id).json()))
        say("/plan gift"); say("use existing task Buy gift")
        PlanningLibrary(context).saveTemplate("Gift preparation", plan())
        val copy = PlanningLibrary(context).useTemplate("Gift preparation")
        copy.validate()
        assertEquals(listOf("Buy gift"), copy.steps.map { it.title })
        assertNull(copy.steps.single().parentId); assertNull(copy.steps.single().taskSnapshot)
        assertEquals(occasion.id, db.taskDao().getTaskById(id)!!.planning!!.parentTaskId)
        assertEquals(occasion, db.taskDao().getTaskById(occasion.id))
    }

}
