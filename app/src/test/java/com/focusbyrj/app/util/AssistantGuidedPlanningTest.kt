package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.assistant.*
import com.focusbyrj.app.util.router.AyvaIntentRouter
import com.focusbyrj.app.util.router.RouterDestination
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AssistantGuidedPlanningTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    @Before fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        AssistantSessionStore(context).clear()
        for (name in listOf("focus_prefs", "bubble_prefs", "ayva_action_receipts", "ayva_personalization")) context.getSharedPreferences(name, 0).edit().clear().commit()
        FocusEconomyManager.init(context)
        AyvaTalkEngine.resetConversation()
    }
    @After fun tearDown() { AssistantSessionStore(context).clear(); db.close(); AyvaTalkEngine.resetConversation() }
    private suspend fun say(text: String) = OfflineAssistant.respond(text, context) ?: error("Not handled: $text")
    private fun state() = AssistantSessionStore(context).load()
    private suspend fun add(title: String, priority: Boolean = false) = db.taskDao().insertTask(Task(title = title, isPriority = priority))
    private suspend fun task(id: Long) = db.taskDao().getTaskById(id)!!
    private suspend fun select(id: Long) { say("let's work on id:$id") }
    private fun confirm(response: AyvaTalkEngine.TalkResponse) = response.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva confirm ") }.query

    private fun plan() = PlanningDraft.read(state().featureState!!.getJSONObject("guidedPlan"))
    private fun saveQuery(response: AyvaTalkEngine.TalkResponse) = response.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva plan-save ") }.query
    private suspend fun review(): AyvaTalkEngine.TalkResponse {
        say("help me plan my trip"); say("Pack bag; Buy tickets"); say("skip"); return say("skip")
    }
    @Test fun reviewedSaveIsBoundToRevisionAndOldButtonsDoNotWrite() = runBlocking {
        val old = saveQuery(review())
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        val updated = say("rename 2 to Arrange transport")
        say(old); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        say(saveQuery(updated)); say(saveQuery(updated))
        assertEquals(setOf("Pack bag", "Arrange transport"), db.taskDao().getAllActiveTasksList().map { it.title }.toSet())
        say("undo"); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }
    @Test fun checklistKeepsStableChildIdsAndSeparateTasksHaveNoChildren() = runBlocking {
        review(); val before = plan().items.map { it.id }
        val r = say("one checklist"); say(saveQuery(r))
        val task = db.taskDao().getAllActiveTasksList().single()
        assertEquals(before, com.focusbyrj.app.data.Subtask.listFromJson(task.subtasksJson).map { it.id })
        assertFalse(task.isCompleted)
    }
    @Test fun atomicSaveRollsBackOnFailureAndDurableReceiptDeduplicatesRetries() = runBlocking {
        review(); val draft = plan()
        try { AssistantPlanStore.save(context, draft) { error("simulated process failure before receipt") }; fail("Expected failure") } catch (_: IllegalStateException) {}
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        assertNull(db.assistantPlanReceiptDao().get(draft.id))
        val first = AssistantPlanStore.save(context, draft)
        val retry = AssistantPlanStore.save(context, draft)
        assertTrue(retry.repeated)
        assertEquals(first.tasks.map { it.id }, retry.tasks.map { it.id })
        assertEquals(2, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun interruptionRevokesSaveAndBareYesCannotRestoreIt() = runBlocking {
        val old = saveQuery(review())
        AyvaTalkEngine.answerTalkQueryWithActions("tell me a joke", context)
        say(old); say("yes")
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        say(saveQuery(say("back to my plan")))
        assertEquals(2, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun staleQuestionCannotReplaceAnswersAndCapacityConflictBlocksSaving() = runBlocking {
        say("help me plan my exam")
        val old = plan(); say("Algebra; Geometry"); say("skip"); say("30 minutes")
        say(AssistantGuidedPlanning.command(old.id, old.revision, "Chemistry"))
        assertEquals(listOf("Algebra", "Geometry"), plan().items.map { it.title })
        val conflict = say("effort: 60 minutes")
        assertTrue(conflict.formattedText.contains("exceeds"))
        assertTrue(conflict.actions.filterIsInstance<TalkAction.AskQuery>().none { it.query.startsWith("/ayva plan-save ") })
        val fixed = say("budget: 90 minutes"); say(saveQuery(fixed))
        assertEquals(2, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun concurrentNativeDuplicateAndTimezoneChangeAreNotOverwritten() = runBlocking {
        val old = saveQuery(review()); add("Pack bag")
        say(old)
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
        val snapshot = state(); val current = plan(); snapshot.featureState!!.put("guidedPlan", current.copy(timezone = "Changed/Zone").json()); AssistantSessionStore(context).save(snapshot)
        say(old); assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun optionalTemplateCanBeRemovedAndCancelledWithoutWrites() = runBlocking {
        val start = say("plan my trip")
        val choice = start.actions.filterIsInstance<TalkAction.AskQuery>().first { it.label.contains("optional") }
        say(choice.query); assertTrue(plan().items.all { it.source == "optional template" })
        val ids = plan().items.map { it.id }; say("remove 2")
        assertEquals(ids.filterIndexed { i, _ -> i != 1 }, plan().items.map { it.id })
        say("cancel planning"); assertNull(state().featureState?.optJSONObject("guidedPlan")); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }
    @Test fun checklistEditsAreReviewedGuardedAndKeepTheirIds() = runBlocking {
        review(); say(saveQuery(say("one checklist")))
        val parent = db.taskDao().getAllActiveTasksList().single()
        val before = com.focusbyrj.app.data.Subtask.listFromJson(parent.subtasksJson)
        say("show checklist"); val preview = say("complete checklist item 1")
        assertEquals(parent.subtasksJson, task(parent.id).subtasksJson)
        say(confirm(preview))
        val after = com.focusbyrj.app.data.Subtask.listFromJson(task(parent.id).subtasksJson)
        assertEquals(before.map { it.id }, after.map { it.id }); assertTrue(after.first().isDone); assertFalse(task(parent.id).isCompleted)
        val stale = confirm(say("remove checklist item 2"))
        db.taskDao().updateTask(task(parent.id).copy(subtasksJson = "[]"))
        say(stale); assertEquals("[]", task(parent.id).subtasksJson)
    }
    @Test fun processRecreationRetainsDraftButRevokesItsSaveAuthority() = runBlocking {
        val old = saveQuery(review())
        val file = java.io.File(context.filesDir, "ayva_conversation_state.enc")
        val json = org.json.JSONObject(com.focusbyrj.app.util.backup.EncryptedSnapshotFile.read(file)).put("process", "old-process")
        com.focusbyrj.app.util.backup.EncryptedSnapshotFile.write(file, json.toString())
        say(old); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        say(saveQuery(say("back to my plan"))); assertEquals(2, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun legacyChecklistIdsAreStableAcrossReads() {
        val old = "[{\"text\":\"Pack charger\",\"isChecked\":true}]"
        assertEquals(com.focusbyrj.app.data.Subtask.listFromJson(old), com.focusbyrj.app.data.Subtask.listFromJson(old))
    }

    @Test fun independentDeadlinesBlockChecklistConversionUntilResolved() = runBlocking {
        review(); val original = plan().items.map { it.id }
        say("deadline 2: tomorrow at 4pm")
        assertEquals(original, plan().items.map { it.id })
        assertNull(plan().items.first().dueDate); assertNotNull(plan().items[1].dueDate)
        val conflict = say("one checklist")
        assertTrue(conflict.actions.filterIsInstance<TalkAction.AskQuery>().none { it.query.startsWith("/ayva plan-save ") })
        say(saveQuery(say("separate tasks")))
        val rows = db.taskDao().getAllActiveTasksList().associateBy { it.title }
        assertNull(rows.getValue("Pack bag").dueDate); assertNotNull(rows.getValue("Buy tickets").dueDate)
        say("reschedule the second one")
        assertEquals(listOf(rows.getValue("Buy tickets").id), state().pending.filterIsInstance<AssistantAction.EditTasks>().single().ids)
    }
    @Test fun suppliedExamTopicsCanBecomeEditableOptionalStudySteps() = runBlocking {
        say("plan my exam"); say("Algebra; Geometry"); say("skip"); say("skip")
        say("suggest study steps")
        assertEquals(6, plan().items.size)
        assertTrue(plan().items.all { it.source == "optional template" && (it.title.endsWith("Algebra") || it.title.endsWith("Geometry")) })
        say("remove 2"); say("name: Finals preparation")
        say(saveQuery(say("one checklist")))
        val parent = db.taskDao().getAllActiveTasksList().single()
        assertEquals("Finals preparation", parent.title)
        assertEquals(5, com.focusbyrj.app.data.Subtask.listFromJson(parent.subtasksJson).size)
    }

    @Test fun oversizedInputRevokesOldTaskAndPlanConfirmationAuthority() = runBlocking {
        val id = add("Keep me")
        val oldTask = confirm(say("delete all tasks"))
        say("x".repeat(4001)); say("yes"); say(oldTask)
        assertFalse(task(id).isTrashed)
        val oldPlan = saveQuery(review())
        say("x".repeat(4001)); say("yes"); say(oldPlan)
        assertEquals(listOf(id), db.taskDao().getAllActiveTasksList().map { it.id })
    }

    @Test fun renewedReviewAfterCommitAndProcessDeathRecoversSameTaskBatch() = runBlocking {
        review(); val draft = plan()
        val committed = AssistantPlanStore.save(context, draft) // Simulate a crash after commit, before coordinator/session receipt.
        val file = java.io.File(context.filesDir, "ayva_conversation_state.enc")
        val json = org.json.JSONObject(com.focusbyrj.app.util.backup.EncryptedSnapshotFile.read(file)).put("process", "prior-process")
        com.focusbyrj.app.util.backup.EncryptedSnapshotFile.write(file, json.toString())
        val renewed = say("back to my plan")
        assertTrue(plan().revision > draft.revision)
        val result = say(saveQuery(renewed))
        assertTrue(result.formattedText.contains("already saved"))
        assertEquals(committed.tasks.map { it.id }, db.taskDao().getAllActiveTasksList().map { it.id })
        assertEquals(committed.tasks.map { it.id }, state().taskListIds)
    }

    @Test fun tripGoalStaysInDraftAndSavedDescriptionsWithoutInventingTasks() = runBlocking {
        val start = say("help me plan my trip to Goa next Friday")
        assertEquals("trip", plan().domain)
        assertEquals("to Goa next Friday", plan().goal)
        assertTrue(start.formattedText.contains("Goa"))
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        say("goal: Visit Kochi!")
        assertEquals("Visit Kochi!", plan().goal)
        assertTrue(say("what are we planning?").formattedText.contains("Visit Kochi!"))
        say("steps: Pack bag; Arrange transport"); say("skip")
        say(saveQuery(say("skip")))
        val rows = db.taskDao().getAllActiveTasksList()
        assertEquals(setOf("Pack bag", "Arrange transport"), rows.map { it.title }.toSet())
        assertTrue(rows.all { it.details == "Visit Kochi!" && it.dueDate == null })
    }

    @Test fun dayBudgetUsesSavedTasksAndNeverCreatesPlanningProse() = runBlocking {
        val existing = add("Read chapter")
        val future = db.taskDao().insertTask(Task(title = "Later work", dueDate = System.currentTimeMillis() + 10 * 86_400_000L))
        say("help me plan my day for 2 hours")
        assertEquals(120, state().planning!!.minutes)
        assertTrue(state().planning!!.todayOnly)
        assertTrue(existing in state().taskListIds)
        assertFalse(future in state().taskListIds)
        assertEquals(2, db.taskDao().getAllActiveTasksList().size)
    }

    @Test fun unsupportedPlanningTopicAsksForScopeInsteadOfAddingTask() = runBlocking {
        assertTrue(AyvaIntentRouter.route("plan my wedding Friday", emptyList()) is RouterDestination.TalkQuery)
        val response = say("plan my wedding Friday")
        assertTrue(response.formattedText.contains("guided checklist"))
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }

    @Test fun collaborativeTripRequestOverridesVacationHelpAndSavesOnlyAfterReview() = runBlocking {
        val prefs = context.getSharedPreferences("bubble_prefs", 0)
        prefs.edit().putBoolean("vacation_mode", true).commit()
        AyvaTalkEngine.answerTalkQueryWithActions("/talk vacation mode", context)
        val input = "can we plan things for a trip"
        assertTrue(AyvaIntentRouter.route(input) is RouterDestination.TalkQuery)
        val response = AyvaTalkEngine.answerTalkQueryWithActions(input, context)
        assertNotNull(response.formattedText, state().featureState?.optJSONObject("guidedPlan"))
        assertEquals("trip", plan().domain)
        assertEquals("items", plan().question)
        assertTrue(response.formattedText.contains("preparation steps"))
        assertFalse(response.formattedText.contains("Streak Freeze"))
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        assertTrue(prefs.getBoolean("vacation_mode", false))
        say("steps: Pack bag; Book train"); say("skip")
        val review = say("skip")
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        say(saveQuery(review))
        assertEquals(setOf("Pack bag", "Book train"), db.taskDao().getAllActiveTasksList().map { it.title }.toSet())
        assertTrue(prefs.getBoolean("vacation_mode", false))
    }

    @Test fun collaborativePlanningParaphrasesShareOneDispatcher() = runBlocking {
        for ((input, domain) in listOf(
            "could we plan a holiday to Goa" to "trip",
            "can you help us plan tasks for our trip" to "trip",
            "let's make a plan for my vacation" to "trip",
            "I'd like to plan my trip" to "trip",
            "I would like to plan my exam" to "exam",
            "can we prepare for an exam" to "exam",
            "could we plan my day from scratch" to "day"
        )) {
            AssistantSessionStore(context).clear(); AyvaTalkEngine.resetConversation()
            assertTrue(input, AyvaIntentRouter.route(input) is RouterDestination.TalkQuery)
            AyvaTalkEngine.answerTalkQueryWithActions(input, context)
            assertEquals(input, domain, plan().domain)
            assertEquals("items", plan().question)
            assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        }
    }

    @Test fun planningScopeAnswerStaysInPlanningButSettingsCanInterrupt() = runBlocking {
        say("can we plan things together")
        assertEquals("planning_domain", state().expected)
        say("a trip to Goa")
        assertEquals("trip", plan().domain)
        assertEquals("to Goa", plan().goal)
        say("cancel planning")
        say("could we make a plan")
        assertEquals("planning_domain", state().expected)
        val setting = AyvaTalkEngine.answerTalkQueryWithActions("what is vacation mode", context)
        assertTrue(setting.formattedText.contains("Vacation", true))
        assertNull(state().featureState?.optJSONObject("guidedPlan"))
        assertNotEquals("planning_domain", state().expected)
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }

    @Test fun planningLanguageCannotOverrideQuotesNegationOrExplicitTaskCapture() = runBlocking {
        for (input in listOf("don't plan a trip", "what if we plan a trip", "\"can we plan things for a trip\"")) {
            AyvaTalkEngine.answerTalkQueryWithActions(input, context)
            assertNull(state().featureState?.optJSONObject("guidedPlan"))
        }
        say("remind me to plan things for a trip tomorrow")
        assertTrue(db.taskDao().getAllActiveTasksList().single().title.contains("plan things for a trip", true))
        val explanation = AyvaTalkEngine.answerTalkQueryWithActions("/talk what is vacation mode", context)
        assertTrue(explanation.formattedText.contains("Vacation", true))
        assertNull(state().featureState?.optJSONObject("guidedPlan"))
    }
}
