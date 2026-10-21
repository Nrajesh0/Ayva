package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.util.assistant.*
import com.focusbyrj.app.ui.components.EditorDraftStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ConversationalPlannerTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    private val zone = TimeZone.getTimeZone("UTC")
    private val now get() = Calendar.getInstance(zone).apply { clear(); set(2027, 0, 4, 8, 0) }.timeInMillis // Monday
    @Before fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        ConversationPlanStore(context).clear(); AssistantSessionStore(context).clear()
        listOf("ayva_composer", "ayva_personalization", "ayva_suggestion_receipts").forEach { context.getSharedPreferences(it, 0).edit().clear().commit() }
    }
    @After fun tearDown() { ConversationPlanStore(context).clear(); AssistantSessionStore(context).clear(); ChatModeStore.set(context, ChatMode.CHAT); db.close() }
    private suspend fun say(text: String) = OfflineAssistant.respond(text, context) ?: error("Not handled: $text")
    private fun plan() = ConversationPlanStore(context).active()!!
    private fun scheduled(title: String, hour: Int, duration: Int) = PlanStep(title = title, minutes = duration, start = now + (hour - 8) * 3_600_000L)

    @Test fun planningIsConversationOnlyAndNeverWritesBeforeReview() = runBlocking {
        assertTrue(say("/plan a trip to Goa").formattedText.contains("dates"))
        say("add Research transport for 30 minutes")
        say("add Pack bag for 20 minutes")
        say("give the second one half an hour")
        assertEquals(30, plan().steps[1].minutes)
        say("Pack bag depends on Research transport")
        assertEquals(setOf(plan().steps[0].id), plan().steps[1].prerequisites)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("save plan")
        assertNotNull(plan().reviewedRevision)
        say("rename Pack bag to Packing")
        assertNull(plan().reviewedRevision)
        say("apply plan") // must review the changed revision, not execute
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("apply plan")
        assertEquals(2, db.taskDao().getAllActiveTasksList().size)
        say("apply plan")
        assertEquals(2, db.taskDao().getAllTasksList().size)
    }
    @Test fun interruptedPlanSurvivesSessionExpiryAndModeChanges() = runBlocking {
        say("/plan exam preparation"); say("add Algebra for 45 minutes")
        say("/talk hello")
        assertEquals(ChatMode.TALK, ChatModeStore.get(context))
        val state = AssistantSessionStore(context).load()
        state.touchedAt = 1; AssistantSessionStore(context).save(state)
        AssistantSessionStore(context).clear()
        assertEquals("Algebra", plan().steps.single().title)
        say("Back to my exam plan")
        assertEquals(ChatMode.PLAN, ChatModeStore.get(context))
        assertEquals("Algebra", plan().steps.single().title)
    }
    @Test fun ordinaryUnrecognizedPlanRepliesCannotCreateTasks() = runBlocking {
        say("/plan trip")
        say("perhaps the second option would be more relaxing")
        say("yes")
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        assertTrue(plan().steps.isEmpty())
    }
    @Test fun hypotheticalDoesNotChangeTheActivePlan() = runBlocking {
        say("/plan project"); say("add Build prototype for 30 minutes")
        val original = plan().json().toString()
        val answer = say("what if rename Build prototype to Try something else")
        assertTrue(answer.formattedText.contains("Alternative only"))
        // The conversation records that an alternative is being viewed; the active graph is unchanged.
        assertTrue(plan().hypotheticalView)
        assertEquals(original, plan().copy(hypotheticalView = false, alternative = null).json().toString())
        assertEquals("Try something else", PlanWorkspace.read(JSONObject(plan().alternative!!)).steps.single().title)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun ambiguousReferenceIsClarifiedWithStableIds() = runBlocking {
        say("/plan trip"); say("add Book train, Book hotel")
        say("rename book to Booking")
        assertNotNull(plan().pending)
        say("the second one")
        assertEquals(listOf("Book train", "Booking"), plan().steps.map { it.title })
    }
    @Test fun invalidCompoundRequestDoesNotPartlyApply() = runBlocking {
        say("/plan project"); say("add Prototype")
        val before = plan().json().toString()
        say("rename Prototype to First version; teleport the deadline")
        assertEquals(before, plan().json().toString())
    }
    @Test fun realCyclesAndOverlapsAreRejectedAtomically() {
        val a = scheduled("A", 9, 30)
        val b = scheduled("B", 9, 20)
        assertThrows(IllegalArgumentException::class.java) { PlanWorkspace(steps = listOf(a, b)).validate() }
        val c = PlanStep(title = "C")
        assertThrows(IllegalArgumentException::class.java) {
            PlanWorkspace(steps = listOf(a.copy(start = null, prerequisites = setOf(c.id)), c.copy(prerequisites = setOf(a.id)))).validate()
        }
    }
    @Test fun schedulerRespectsDependenciesDeadlinesAndExistingCommitments() {
        val fixed = scheduled("Workout", 9, 60)
        val a = PlanStep(title = "Research", minutes = 30)
        val b = PlanStep(title = "Book", minutes = 30, prerequisites = setOf(a.id))
        val p = PlanWorkspace(timezone = "UTC", steps = listOf(fixed, b, a))
        val result = PlanScheduler.arrange(p, now, days = 1, startHour = 9, endHour = 12)
        assertTrue(result.unscheduled.isEmpty())
        val first = result.steps.first { it.id == a.id }; val second = result.steps.first { it.id == b.id }
        assertEquals(fixed.end, first.start)
        assertEquals(first.end, second.start)
        assertEquals(fixed, result.steps.first { it.id == fixed.id })
        p.copy(steps = result.steps).validate()
    }
    @Test fun infeasiblePlanIsPartialRatherThanViolatingDeadline() {
        val step = PlanStep(title = "Study", minutes = 180, deadline = now + 2 * 3_600_000L)
        val result = PlanScheduler.arrange(PlanWorkspace(timezone = "UTC", steps = listOf(step)), now, days = 1, startHour = 9, endHour = 10)
        assertEquals(listOf(step.id), result.unscheduled)
        assertNull(result.steps.single().start)
    }
    @Test fun dateGrammarRequiresAnUnambiguousTimeAndRejectsDstGaps() {
        assertNotNull(PlanLanguage.date("Friday at seven", now, "UTC").question)
        assertNotNull(PlanLanguage.date("Friday at 7pm", now, "UTC").at)
        assertNotNull(PlanLanguage.date("2027-02-30 at 9am", now, "UTC").question)
        assertNotNull(PlanLanguage.date("2027-03-14 at 2:30am", now, "America/New_York").question)
        assertNotNull(PlanLanguage.date("2027-11-07 at 1:30am", now, "America/New_York").question)
    }
    @Test fun scheduledSubtasksRetainTheirOwnDurationsAndTimesAcrossReload() = runBlocking {
        say("/plan trip"); say("add Prepare bags"); say("add subtask Pack charger under Prepare bags")
        say("give Pack charger 10 minutes")
        say("schedule Pack charger on tomorrow at 9am")
        val current = plan()
        assertEquals(current.steps.first().id, current.steps.last().parentId)
        assertNotNull(current.steps.last().start)
        assertEquals(current.json().toString(), PlanWorkspace.read(current.json()).json().toString())
    }
    @Test fun commitRejectsNativeEditsAndKeepsAllOtherTasksUnchanged() = runBlocking {
        say("/plan project"); say("add Prototype"); say("save plan"); say("apply plan")
        val old = db.taskDao().getAllTasksList().single()
        db.taskDao().updateTask(old.copy(title = "Native title", updatedAt = old.updatedAt + 1))
        say("rename Prototype to Chat title"); say("save plan")
        assertTrue(say("apply plan").formattedText.contains("changed outside"))
        assertEquals("Native title", db.taskDao().getAllTasksList().single().title)
    }
    @Test fun editorDraftEncryptionAndDiscardPreventLateAutosaveResurrection() {
        val store = EditorDraftStore(context, "test-editor")
        store.save(JSONObject().put("title", "A private draft"))
        assertEquals("A private draft", EditorDraftStore(context, "test-editor").read().getString("title"))
        val files = java.io.File(context.filesDir, "editor_drafts").listFiles().orEmpty()
        assertTrue(files.none { it.readText().contains("A private draft") })
        store.discard(); store.save(JSONObject().put("title", "late callback"))
        assertFalse(EditorDraftStore(context, "test-editor").read().has("title"))
    }
    @Test fun selectingModePreservesUnsentTextAndSelection() {
        val input = TextFieldValue("/plan Book train", TextRange(11))
        val selected = ChatMode.activate(input, ChatMode.PLAN)
        assertEquals("Book train", selected.text); assertEquals(5, selected.selection.end)
        val unrelated = TextFieldValue("I plan to study", TextRange(3))
        assertEquals(unrelated, ChatMode.activate(unrelated, ChatMode.PLAN))
        assertNull(ChatMode.suggested(unrelated.text))
    }
    @Test fun greetingDoesNotListTasksOrDistantOccasions() = runBlocking {
        db.taskDao().insertTask(Task(title = "Distant anniversary", type = TaskType.ANNIVERSARY, dueDate = System.currentTimeMillis() + 180 * 86_400_000L))
        val greeting = say("hi")
        assertFalse(greeting.formattedText.contains("Distant anniversary"))
        assertFalse(greeting.formattedText.contains("pending task"))
        val snapshot = com.focusbyrj.app.util.context.AyvaContextEngine.captureSnapshot(context, db.taskDao().getAllTasksList())
        assertEquals(0, snapshot.pendingTasksCount); assertNull(snapshot.nextDueTaskTitle)
    }
    @Test fun timeClarificationRetainsTheOriginalDay() = runBlocking {
        say("/plan trip"); say("add Pack bag for 20 minutes")
        assertTrue(say("schedule Pack bag on tomorrow at seven").formattedText.contains("AM or PM"))
        say("pm")
        val date = Calendar.getInstance().apply { timeInMillis = plan().steps.single().start!! }
        assertEquals(19, date.get(Calendar.HOUR_OF_DAY))
    }
    @Test fun missingDurationDoesNotShiftRelativeDateDuringClarification() = runBlocking {
        say("/plan project"); say("add Prototype")
        say("schedule Prototype on tomorrow at 9am")
        val pendingAt = plan().pending!!.getLong("at")
        say("30 minutes")
        assertEquals(pendingAt, plan().steps.single().start)
    }
    @Test fun multipleFactsAreCapturedWithoutRepeatingDestinationQuestion() = runBlocking {
        say("/plan trip")
        say("Goa, three days next month, around ₹15,000. I can prepare only after work.")
        assertEquals("Goa", plan().facts["destination"])
        assertEquals("three days", plan().facts["trip length"])
        assertEquals("₹15,000", plan().facts["budget"])
        assertTrue(plan().facts.containsKey("availability note"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun interruptedCommitCanRecoverWithoutDuplicateTasks() = runBlocking {
        say("/plan project"); say("add Prototype"); say("save plan")
        val before = plan()
        say("apply plan")
        assertEquals(1, db.taskDao().getAllTasksList().size)
        // Simulate process death between the database receipt and workspace reconciliation.
        ConversationPlanStore(context).put(before)
        say("apply plan")
        assertEquals(1, db.taskDao().getAllTasksList().size)
        assertNotNull(plan().steps.single().taskSnapshot)
    }
    @Test fun savedPlanCanBeEditedAndRemovedWithVersionAwareUndo() = runBlocking {
        say("/plan project"); say("add Prototype, Review"); say("save plan"); say("apply plan")
        val id = db.taskDao().getAllActiveTasksList().first { it.title == "Prototype" }.id
        say("rename Prototype to First version"); say("save plan"); say("apply plan")
        assertEquals("First version", db.taskDao().getTaskById(id)!!.title)
        say("remove Review"); say("save plan"); say("apply plan")
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
        assertEquals(1, db.taskDao().getTrashedTasksSync().size)
    }
    @Test fun changingDurationCannotOverlapAnExistingSession() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes, Review for 30 minutes")
        say("schedule Research on tomorrow at 9am"); say("schedule Review on tomorrow at 10am")
        val original = plan().steps.first().minutes
        assertTrue(say("give Research 90 minutes").formattedText.contains("overlap"))
        assertEquals(original, plan().steps.first().minutes)
    }
    @Test fun graphSerializationAndLimitsRejectUnknownDependencies() {
        val unknown = PlanStep(title = "A", prerequisites = setOf("missing"))
        assertThrows(IllegalArgumentException::class.java) { PlanWorkspace(steps = listOf(unknown)).validate() }
        val p = PlanWorkspace(goal = "A real plan", steps = listOf(PlanStep(title = "A", minutes = 10)), facts = mapOf("budget" to "₹200"))
        assertEquals(p, PlanWorkspace.read(p.json()))
    }
    @Test fun planUndoCanBeRequestedThroughTheComposer() = runBlocking {
        say("/plan project"); say("add Prototype"); say("save plan"); say("apply plan")
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
        say("/plan undo")
        assertEquals(0, db.taskDao().getAllActiveTasksList().size)
        assertNull(plan().savedRevision)
        assertNull(plan().steps.single().taskSnapshot)
        say("save plan"); say("apply plan")
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun lateSaveCallbackCannotDeleteTheNextEditorDraft() {
        val first = EditorDraftStore(context, "racing-editor").beginEditing()
        first.save(JSONObject().put("title", "First form"))
        val next = EditorDraftStore(context, "racing-editor").beginEditing()
        next.save(JSONObject().put("title", "New unsaved form"))
        first.discard()
        first.save(JSONObject().put("title", "Old disposal"))
        assertEquals("New unsaved form", next.read().getString("title"))
        next.discard()
    }
    @Test fun nativeDetailsSurviveReviewedTitleChanges() = runBlocking {
        say("/plan project"); say("add Prototype"); say("save plan"); say("apply plan")
        val task = db.taskDao().getAllTasksList().single()
        db.taskDao().updateTask(task.copy(details = "User's carefully written details", updatedAt = task.updatedAt + 1))
        say("refresh saved tasks")
        say("rename Prototype to First version"); say("save plan"); say("apply plan")
        assertEquals("User's carefully written details", db.taskDao().getTaskById(task.id)!!.details)
    }
    @Test fun unreadableDraftIsPreservedInsteadOfBeingOverwrittenByAnEmptyForm() {
        val key = "corrupt-editor"
        val store = EditorDraftStore(context, key)
        store.save(JSONObject().put("title", "Original text"))
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = java.io.File(context.filesDir, "editor_drafts/$hash.enc")
        val corrupt = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        file.writeBytes(corrupt)
        val reopened = EditorDraftStore(context, key).beginEditing()
        reopened.read()
        assertTrue(reopened.restoreFailed)
        reopened.save(JSONObject().put("title", "Empty form"))
        assertArrayEquals(corrupt, file.readBytes())
        reopened.discard()
    }
    @Test fun oldApplyChipCannotSaveAnotherReviewedPlan() = runBlocking {
        say("/plan trip"); say("add Pack bag")
        val old = say("save plan").actions.filterIsInstance<TalkAction.AskQuery>().single().query
        say("/plan new plan Exam preparation"); say("add Algebra"); say("save plan")
        assertTrue(say(old).formattedText.contains("older plan"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("apply plan")
        assertEquals("Algebra", db.taskDao().getAllTasksList().single().title)
    }
    @Test fun availabilityDoesNotSilentlyBecomeADailyRecurringWindow() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes")
        assertTrue(say("I'm free from 6pm to 9pm").formattedText.contains("today, or every day"))
        assertNull(plan().steps.single().start)
        say("daily")
        assertNotNull(plan().steps.single().start)
        assertTrue(plan().facts["availability"]!!.contains("daily"))
    }
    @Test fun schedulerKeepsLocalWindowBoundariesAcrossDaylightSavingChanges() {
        val localZone = TimeZone.getTimeZone("America/New_York")
        val midnight = Calendar.getInstance(localZone).apply { clear(); set(2027, 2, 14, 0, 0) }.timeInMillis
        val longStep = PlanStep(title = "Four real hours", minutes = 240)
        val p = PlanWorkspace(timezone = localZone.id, steps = listOf(longStep))
        // On the spring transition, midnight–4am contains only three real hours.
        val result = PlanScheduler.arrange(p, midnight, days = 1, startHour = 0, endHour = 4)
        assertEquals(listOf(longStep.id), result.unscheduled)
        assertNull(result.steps.single().start)
    }
    @Test fun undoAfterDraftEditDoesNotUndoAnEarlierTaskCommit() = runBlocking {
        say("/plan project"); say("add Prototype"); say("save plan"); say("apply plan")
        say("rename Prototype to First version")
        assertTrue(say("/plan undo").formattedText.contains("draft edit"))
        assertEquals("Prototype", plan().steps.single().title)
        assertEquals("Prototype", db.taskDao().getAllActiveTasksList().single().title)
        say("/plan undo")
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun undoInAnotherWorkspaceCannotReachThePriorPlansTaskCommit() = runBlocking {
        say("/plan project"); say("add Prototype"); say("save plan"); say("apply plan")
        say("/plan new plan Exam preparation")
        say("/plan undo")
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun planningTogetherAsksForTheGoalWithoutLosingTheNextRepliesFacts() = runBlocking {
        say("let's plan together")
        assertTrue(plan().goal.isBlank())
        val response = say("a trip to Goa")
        assertEquals("trip", plan().domain)
        assertEquals("Goa", plan().facts["destination"])
        assertFalse(response.formattedText.contains("destination in mind"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun saveReviewShowsEveryStepAndNamesTasksThatWillBeTrashed() = runBlocking {
        say("/plan project")
        say("add " + (1..16).joinToString { "Stage $it" })
        assertTrue(say("save plan").formattedText.contains("16. Stage 16"))
        say("apply plan")
        say("remove Stage 16")
        assertTrue(say("save plan").formattedText.contains("Move to Trash: Stage 16"))
        assertEquals(16, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun rejectedInputCancelsWorkspaceReviewAsWellAsLegacyConfirmation() = runBlocking {
        say("/plan project"); say("add Prototype")
        val old = say("save plan").actions.filterIsInstance<TalkAction.AskQuery>().single().query
        say("x".repeat(4001))
        assertNull(plan().reviewedRevision)
        say(old)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun durationGrammarComposesWordNumbersUnitsAndFractions() {
        mapOf("one hour and thirty minutes" to 90, "an hour and a half" to 90,
            "forty-five minutes" to 45, "a quarter of an hour" to 15,
            "two and a half hours" to 150, "1.25 hours" to 75, "2h 15m" to 135).forEach { (text, expected) ->
            assertEquals(text, expected, PlanLanguage.duration(text))
        }
        listOf("-30 minutes", "zero minutes", "one and a half minutes", "one hour bananas", "one hour and -20 minutes", "2000 hours", "1e1 minutes").forEach {
            assertNull(it, PlanLanguage.duration(it))
        }
    }

    @Test fun durationCorrectionUsesTheFocusedStepAcrossModeChanges() = runBlocking {
        say("/plan project"); say("add Research for one hour and fifteen minutes, Review for 20 minutes")
        say("give Research one hour and a half"); say("actually, forty-five minutes")
        assertEquals(listOf(45, 20), plan().steps.map { it.minutes })
        say("/talk hello"); say("/plan"); say("make that half an hour")
        assertEquals(listOf(30, 20), plan().steps.map { it.minutes })
        assertEquals(PlanEditField.DURATION, plan().editFocus!!.field)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun invalidDurationsCannotFallThroughToALooserParser() = runBlocking {
        say("/plan project"); say("add Research for 45 minutes")
        say("give Research -30 minutes")
        assertEquals(45, plan().steps.single().minutes)
        say("give Research - 30 minutes")
        assertEquals(45, plan().steps.single().minutes)
        say("cancel that change")
        say("add Invalid session for -30 minutes")
        assertEquals(1, plan().steps.size)
    }

    @Test fun dateCorrectionKeepsDeadlineMeaningSeparateFromWorkTime() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes"); say("deadline Research is tomorrow")
        val first = plan().steps.single().deadline!!
        say("actually day after tomorrow")
        val step = plan().steps.single()
        assertTrue(step.deadline!! > first); assertNull(step.start)
        assertEquals(PlanEditField.DEADLINE, plan().editFocus!!.field)
    }

    @Test fun ambiguousDateCorrectionAsksWorkTimeOrDeadline() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes")
        assertTrue(say("actually tomorrow at 9am").formattedText.contains("work time or the deadline"))
        assertNull(plan().steps.single().start); assertNull(plan().steps.single().deadline)
        assertTrue(say("save plan").formattedText.contains("unanswered"))
        say("deadline")
        assertNotNull(plan().steps.single().deadline); assertNull(plan().steps.single().start)
    }

    @Test fun clockCorrectionRetainsTheChosenWorkDay() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes"); say("schedule Research on day after tomorrow at 9am")
        val original = plan().steps.single().start!!
        say("actually at 10am")
        assertEquals(original + 3_600_000L, plan().steps.single().start); assertNull(plan().steps.single().deadline)
    }

    @Test fun relativeWorkTimeEditsUseDurationMeaningAndRemainDrafts() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes"); say("schedule Research on tomorrow at 9am")
        val original = plan().steps.single().start!!
        say("move it half an hour later")
        assertEquals(original + 30 * 60_000L, plan().steps.single().start)
        say("shift it fifteen minutes earlier")
        assertEquals(original + 15 * 60_000L, plan().steps.single().start)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun relativeShiftsRespectSessionsInOtherWorkspaces() = runBlocking {
        say("/plan new plan Project A"); say("add Research for 30 minutes"); say("schedule Research on tomorrow at 9am")
        val original = plan().steps.single().start!!
        say("/plan new plan Project B"); say("add Review for 30 minutes"); say("schedule Review on tomorrow at 10am")
        say("resume plan Project A")
        assertTrue(say("move it an hour later").formattedText.contains("overlap"))
        assertEquals(original, plan().steps.single().start)
    }

    @Test fun durationCorrectionsCannotOverlapAnotherWorkspace() = runBlocking {
        say("/plan new plan Project A"); say("add Research for 30 minutes"); say("schedule Research on tomorrow at 9am")
        say("/plan new plan Project B"); say("add Review for 30 minutes"); say("schedule Review on tomorrow at 10am")
        say("resume plan Project A")
        assertTrue(say("give Research ninety minutes").formattedText.contains("overlap"))
        assertEquals(30, plan().steps.single().minutes)
    }

    @Test fun dateClarificationKeepsOriginalTomorrowAfterMidnight() = runBlocking {
        val state = AssistantSession()
        ConversationPlanner.respond(context, "/plan project", state, now)
        ConversationPlanner.respond(context, "add Research for 30 minutes", state, now)
        ConversationPlanner.respond(context, "schedule Research on tomorrow at 7", state, now)
        assertNotNull(plan().pending)
        ConversationPlanner.respond(context, "pm", state, now + 16 * 3_600_000L)
        assertEquals(now + 35 * 3_600_000L, plan().steps.single().start)
    }

    @Test fun referenceClarificationDoesNotReinterpretRelativeDay() = runBlocking {
        val state = AssistantSession()
        ConversationPlanner.respond(context, "/plan trip", state, now)
        ConversationPlanner.respond(context, "add Book train for 30 minutes, Book hotel for 30 minutes", state, now)
        ConversationPlanner.respond(context, "schedule book on tomorrow at 9am", state, now)
        assertEquals("reference", plan().pending!!.getString("kind"))
        ConversationPlanner.respond(context, "the second one", state, now + 16 * 3_600_000L)
        assertEquals(now + 25 * 3_600_000L, plan().steps[1].start)
    }

    @Test fun timeKindClarificationRetainsItsOriginalDay() = runBlocking {
        val state = AssistantSession()
        ConversationPlanner.respond(context, "/plan project", state, now)
        ConversationPlanner.respond(context, "add Research for 30 minutes", state, now)
        ConversationPlanner.respond(context, "actually tomorrow at 9am", state, now)
        ConversationPlanner.respond(context, "work time", state, now + 16 * 3_600_000L)
        assertEquals(now + 25 * 3_600_000L, plan().steps.single().start)
    }

    @Test fun unresolvedChangesCannotSaveAndCancellationRequiresANewReview() = runBlocking {
        say("/plan trip"); say("add Book train, Book hotel"); say("save plan"); say("rename book to Booking")
        assertTrue(say("apply plan").formattedText.contains("unanswered"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("cancel that change")
        assertNull(plan().pending); assertNull(plan().reviewedRevision)
        say("apply plan")
        assertTrue(db.taskDao().getAllTasksList().isEmpty()); assertNotNull(plan().reviewedRevision)
    }

    @Test fun workTimeAndDeadlineCanBeClearedIndependently() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes")
        say("schedule Research on tomorrow at 9am"); say("deadline Research is day after tomorrow")
        val deadline = plan().steps.single().deadline
        say("unschedule it")
        assertNull(plan().steps.single().start); assertEquals(deadline, plan().steps.single().deadline)
        assertEquals(30, plan().steps.single().minutes)
        say("clear deadline for it"); assertNull(plan().steps.single().deadline)
    }

    @Test fun correctedDurationCanAnswerAnUnfinishedScheduleQuestion() = runBlocking {
        say("/plan project"); say("add Research"); say("schedule Research on tomorrow at 9am")
        val requested = plan().pending!!.getLong("at")
        say("actually half an hour")
        assertEquals(requested, plan().steps.single().start); assertEquals(30, plan().steps.single().minutes)
    }

    @Test fun compoundRepairsRemainAtomicWhenAnotherClauseNeedsClarification() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes, Review for 30 minutes"); say("give Research 45 minutes")
        val original = plan().json().toString()
        say("actually one hour; schedule Review on tomorrow at 7")
        assertEquals(original, plan().json().toString())
    }

    @Test fun conflictingWorkOffersAnAlternativeAndWaitsForAgreement() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes, Review for 30 minutes")
        say("schedule Research on tomorrow at 9am"); say("schedule Review on tomorrow at 10am")
        val original = plan().steps
        assertTrue(say("give Research 90 minutes").formattedText.contains("use that time"))
        assertEquals(original, plan().steps)
        assertEquals("work_alternative", plan().pending!!.getString("kind"))
        assertTrue(say("why did you ask").formattedText.contains("overlaps"))
        say("use that time")
        assertEquals(original[1].end, plan().steps[0].start)
        assertEquals(90, plan().steps[0].minutes); assertEquals(original[1], plan().steps[1])
        assertNull(plan().pending); assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun alternativeIsRecheckedAfterAnotherPlanChanges() = runBlocking {
        say("/plan new plan Project B"); say("add Meeting for 30 minutes"); say("schedule Meeting on tomorrow at 9am")
        val meetingStart = plan().steps.single().start!!
        say("/plan new plan Project A"); say("add Research for 30 minutes"); say("schedule Research on tomorrow at 9am")
        assertNotNull(plan().pending)
        say("resume plan Project B"); say("give Meeting 60 minutes")
        say("resume plan Project A"); say("use that time")
        assertNull(plan().steps.single().start)
        assertEquals(meetingStart + 60 * 60_000L, plan().pending!!.getLong("at"))
        say("use that time")
        assertEquals(meetingStart + 60 * 60_000L, plan().steps.single().start)
    }

    @Test fun conflictCanBeResolvedByShorteningTheOriginalRequest() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes, Review for 30 minutes")
        say("schedule Research on tomorrow at 9am"); say("schedule Review on tomorrow at 10am")
        val original = plan().steps.first().start
        say("give Research 90 minutes"); say("actually forty-five minutes")
        assertEquals(original, plan().steps.first().start)
        assertEquals(45, plan().steps.first().minutes); assertNull(plan().pending)
    }

    @Test fun alternativeDateClarificationKeepsTheProposedDuration() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes, Review for 30 minutes")
        say("schedule Research on tomorrow at 9am"); say("schedule Review on tomorrow at 10am")
        val morning = plan().steps.first().start!!
        say("give Research 90 minutes"); say("tomorrow at 7"); say("pm")
        assertEquals(90, plan().steps.first().minutes)
        assertEquals(morning + 10 * 3_600_000L, plan().steps.first().start)
    }

    @Test fun alternativeDoesNotEscapeAnExplicitAvailabilityWindow() {
        val meeting = scheduled("Meeting", 9, 60)
        val research = scheduled("Research", 9, 30)
        val plan = PlanWorkspace(timezone = "UTC", steps = listOf(meeting, research.copy(start = null)),
            facts = mapOf("availability" to "9:00–10:00 daily (next 28 days)"))
        assertNull(PlanWorkAlternatives.laterOnSameDay(plan, research, emptyList(), now))
        assertEquals(meeting.end, PlanWorkAlternatives.laterOnSameDay(plan.copy(facts = emptyMap()), research, emptyList(), now)!!.start)
    }

    @Test fun uncertainRepliesLeaveUnansweredChangesOpenWithoutInventingDates() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes"); say("schedule Research on tomorrow at 7")
        say("not sure")
        assertNull(plan().pending); assertNull(plan().steps.single().start)
        say("save plan"); say("apply plan")
        assertNull(db.taskDao().getAllTasksList().single().dueDate)
    }

    @Test fun negativeCorrectionCannotBecomeASchedulingRequest() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes"); say("schedule Research on tomorrow at 9am")
        val original = plan().steps
        say("actually don't schedule it tomorrow at 11am")
        assertEquals(original, plan().steps)
    }

    @Test fun explainingAnAmbiguityPreservesTheUnansweredQuestion() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes"); say("actually tomorrow at 9am")
        val before = plan().json().toString()
        assertTrue(say("why did you ask").formattedText.contains("deadline is when it must be finished"))
        assertEquals(before, plan().json().toString())
    }

    @Test fun overflowingWorkIntervalsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PlanWorkspace(steps = listOf(PlanStep(title = "Invalid interval", minutes = 30, start = Long.MAX_VALUE - 10))).validate() }
    }

    @Test fun cancellationCanRemoveAStaleDeletionIntentWithoutOverwritingTheSavedTask() = runBlocking {
        say("/plan project"); say("add Research, Review"); say("save plan"); say("apply plan")
        val saved = db.taskDao().getAllTasksList().single { it.title == "Research" }
        say("remove Research")
        val native = saved.copy(title = "Native edit", updatedAt = saved.updatedAt + 1)
        db.taskDao().updateTask(native)
        say("save plan"); assertTrue(say("apply plan").formattedText.contains("changed outside"))
        say("cancel removal of Research"); assertTrue(plan().removed.isEmpty())
        say("save plan"); say("apply plan")
        assertEquals(native, db.taskDao().getTaskById(saved.id))
        assertEquals(2, db.taskDao().getAllActiveTasksList().size)
    }

    @Test fun recognizedNoOpDoesNotInvalidateAnotherClauseInTheSameDraftTurn() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes")
        say("cancel pending removals; give Research forty-five minutes")
        assertEquals(45, plan().steps.single().minutes)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun hypotheticalFollowUpCannotAcceptAnOlderConflictProposal() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes, Review for 30 minutes")
        say("schedule Research on tomorrow at 9am"); say("schedule Review on tomorrow at 10am")
        val original = plan().steps
        say("give Research 90 minutes")
        say("what if give Research 120 minutes")
        say("use that time")
        assertEquals(original, plan().steps)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun hypotheticalReadCannotReuseAnEarlierSaveReview() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes")
        val old = say("save plan").actions.filterIsInstance<TalkAction.AskQuery>().single().query
        say("what if give Research 45 minutes")
        say(old)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        assertEquals(30, plan().steps.single().minutes)
    }

    @Test fun hypotheticalConflictDoesNotOfferAnActionAgainstTheMainPlan() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes, Review for 30 minutes")
        say("schedule Review on tomorrow at 9am")
        val response = say("what if schedule Research on tomorrow at 9am")
        assertFalse(response.formattedText.contains("Say ‘use that time’"))
        assertNull(plan().pending)
    }

    @Test fun returningFromHypotheticalRestatesTheRealUnansweredChange() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes, Review for 30 minutes")
        say("schedule Research on tomorrow at 9am"); say("schedule Review on tomorrow at 10am")
        say("give Research 90 minutes"); say("what if give Research 120 minutes")
        assertTrue(say("/plan").formattedText.contains("90 minutes"))
        say("use that time")
        assertEquals(90, plan().steps.first().minutes)
    }

    @Test fun planIntentHashRemainsCompatibleWithPreContextReceipts() {
        val step = PlanStep(id = "compat-step", title = "Research", minutes = 30, workReminders = false)
        val plan = PlanWorkspace(id = "compat-plan", goal = "Compatibility", timezone = "UTC", steps = listOf(step))
        // Fixed digest of the first-slice canonical payload, before UI-only context fields existed.
        assertEquals("94b42a99fb836a933e463f8ea1ed896ba260fa9132eaf0aaa42d61fdf48b84ef", ConversationPlanCommitter.hash(plan))
    }

    @Test fun undoFromHypotheticalCannotReachTheSavedTaskUntilReturningToTheDraft() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes"); say("save plan"); say("apply plan")
        val saved = db.taskDao().getAllTasksList().single()
        say("what if rename Research to Alternative research")
        say("undo saved changes")
        assertEquals(saved, db.taskDao().getTaskById(saved.id))
        say("/plan"); say("undo saved changes")
        assertTrue(db.taskDao().getTaskById(saved.id)!!.isTrashed)
    }
}
