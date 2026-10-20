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
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AssistantDiscussionTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        AssistantSessionStore(context).clear(); ConversationPlanStore(context).clear(); ChatModeStore.set(context, ChatMode.CHAT)
        listOf("ayva_personalization", "ayva_action_receipts", "ayva_suggestion_receipts").forEach { context.getSharedPreferences(it, 0).edit().clear().commit() }
    }
    @After fun cleanup() {
        AssistantSessionStore(context).clear(); ConversationPlanStore(context).clear(); ChatModeStore.set(context, ChatMode.CHAT); db.close()
    }
    private suspend fun say(input: String) = OfflineAssistant.respond(input, context) ?: error("Not handled: $input")
    private fun state() = AssistantSessionStore(context).load()
    private fun discussion() = state().featureState!!.getJSONObject("social").getJSONObject("discussion")
    private fun notes() = discussion().getJSONArray("notes").toString()

    @Test fun detailsSurviveReloadComparisonAndExplicitPlanHandoff() = runBlocking {
        say("/talk let's talk through a trip to Goa")
        say("options are train or bus")
        say("I care about quiet travel")
        say("budget is 10000")
        say("actually budget is 15000")
        say("dates are not fixed")
        val comparison = say("/talk compare them").formattedText
        assertTrue(comparison.contains("train / bus")); assertTrue(comparison.contains("quiet travel"))
        assertTrue(comparison.contains("15000")); assertFalse(comparison.contains("10000"))
        assertFalse(comparison.contains("train is cheaper"))
        say("let's go with the first one")
        assertEquals("train", discussion().getString("choice"))
        assertTrue(say("what did I tell you").formattedText.contains("Current choice: train"))
        assertTrue(File(context.filesDir, "ayva_conversation_state.enc").readBytes().toString(Charsets.UTF_8).let { !it.contains("quiet travel") })
        say("/talk let's plan it")
        val plan = ConversationPlanStore(context).active()!!
        assertEquals("a trip to Goa", plan.goal); assertEquals("Goa", plan.facts["destination"])
        assertEquals("15000", plan.facts["budget"]!!.filter(Char::isDigit))
        assertEquals("train", plan.facts["current discussion choice"])
        assertTrue(plan.facts.values.any { it.contains("dates are not fixed") })
        assertTrue(plan.facts.values.none { it.contains("10000") })
        assertEquals(ChatMode.PLAN, ChatModeStore.get(context)); assertTrue(plan.steps.isEmpty())
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        assertTrue(context.getSharedPreferences("ayva_personalization", 0).all.isEmpty())
    }
    @Test fun handoffCreatesSeparateWorkspaceAndKeepsOlderPlan() = runBlocking {
        say("/plan exam preparation"); say("add Revise algebra for 30 minutes")
        val original = ConversationPlanStore(context).active()!!
        say("/talk I have a trip to Kochi next month")
        say("I can only travel on weekends")
        say("/plan")
        val plans = ConversationPlanStore(context).all()
        assertEquals(2, plans.size); assertEquals(original, plans.single { it.id == original.id })
        val next = ConversationPlanStore(context).active()!!
        assertNotEquals(original.id, next.id); assertTrue(next.goal.contains("Kochi"))
        assertTrue(next.facts.values.any { it.contains("weekends") })
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun discussionCannotAuthorizeAnOlderDestructivePreview() = runBlocking {
        val id = db.taskDao().insertTask(Task(title = "Keep this"))
        val old = say("delete all tasks").actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva confirm ") }.query
        say("let's talk through changing jobs")
        assertNull(state().confirmationId); assertNull(state().expected)
        say("yes"); say(old)
        assertFalse(db.taskDao().getTaskById(id)!!.isTrashed)
        assertTrue(ConversationPlanStore(context).all().isEmpty())
    }
    @Test fun explicitTaskAndAppCommandsInterruptDiscussion() = runBlocking {
        say("let's talk through switching jobs"); say("I care about flexibility")
        say("remind me to call Mum tomorrow")
        assertEquals("Call Mum", db.taskDao().getAllActiveTasksList().single().title)
        assertFalse(AssistantDiscussion.hasContext(state()))
        say("let's talk through moving house")
        val before = notes()
        val response = say("/talk how do reminders work")
        assertFalse(response.formattedText.contains("What information would help you decide"))
        assertEquals(before, state().featureState!!.getJSONObject("social").getJSONObject("discussion").getJSONArray("notes").toString())
        say("let's talk through moving house")
        say("what time is it"); assertEquals("clock", state().topic)
        say("/TALK let's talk through moving house")
        assertEquals("moving house", discussion().getString("topic"))
        assertTrue(say("what is 12 plus 8").formattedText.contains("20"))
    }
    @Test fun taskTitleAnswerKeepsItsOwnerEvenIfDiscussionExistsInHistory() = runBlocking {
        say("let's talk through moving house")
        say("remind me tomorrow at 6pm")
        assertEquals("task_title", state().expected)
        say("\"Trip to Goa\"")
        assertEquals("Trip to Goa", db.taskDao().getAllActiveTasksList().single().title)
        assertNull(state().expected)
        say("remind me tomorrow"); assertEquals("task_title", state().expected)
        assertNull(ConversationPlanner.respond(context, "plan this", state()))
        say("\"Plan this\"")
        assertTrue(db.taskDao().getAllActiveTasksList().any { it.title == "Plan this" })
        assertTrue(ConversationPlanStore(context).all().isEmpty())
    }
    @Test fun expiredOrAbandonedDiscussionCannotBeUsedByPronounHandoff() = runBlocking {
        say("let's talk through buying a bike")
        val expired = state(); expired.featureState!!.getJSONObject("social").put("at", System.currentTimeMillis() - 900_001)
        AssistantSessionStore(context).save(expired)
        assertTrue(say("let's plan it").formattedText.contains("What would you like to plan"))
        assertTrue(ConversationPlanStore(context).all().isEmpty())
        say("let's talk through buying a car"); say("tell me a joke")
        say("plan that")
        assertTrue(ConversationPlanStore(context).all().isEmpty()); assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("let's talk through choosing a course"); say("never mind"); say("plan that")
        assertTrue(ConversationPlanStore(context).all().isEmpty()); assertFalse(AssistantDiscussion.hasContext(state()))
    }
    @Test fun preciseCorrectionsKeepOtherEvidenceAndAmbiguousRepairsDoNotOverwrite() = runBlocking {
        say("let's talk through finding an apartment")
        say("I need a quiet street"); say("I need a short commute")
        val original = notes()
        assertTrue(say("actually near the station").formattedText.contains("Which detail"))
        assertEquals(original, notes())
        say("replace quiet street with quiet building")
        assertTrue(notes().contains("quiet building")); assertTrue(notes().contains("short commute")); assertFalse(notes().contains("quiet street"))
        say("budget is 20000")
        val before = notes()
        assertTrue(say("budget is 25000").formattedText.contains("correction")); assertEquals(before, notes())
        say("actually budget is 25000"); assertTrue(notes().contains("25000")); assertFalse(notes().contains("20000"))
        val corrected = notes()
        say("budget is 99999?"); assertEquals(corrected, notes())
        say("dates are not fixed")
        val withDates = notes()
        say("replace budget is 25000 with dates are fixed")
        assertEquals(withDates, notes())
    }
    @Test fun uncertainAnswersAndWhyUseCurrentQuestionWithoutInventingFacts() = runBlocking {
        say("I'm deciding between train and bus")
        assertTrue(say("why are you asking").formattedText.contains("priorities"))
        val before = notes()
        assertTrue(say("I don't know").formattedText.contains("leave that open")); assertEquals(before, notes())
        say("I prefer a quiet journey")
        assertEquals("quiet", criteria().getJSONArray("rules").getJSONObject(0).getString("feature"))
        assertTrue(criteria().getJSONArray("rules").getJSONObject(0).getString("source").contains("quiet journey"))
        assertTrue(say("which would you choose").formattedText.contains("details you supply"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun clearAndForgetRemoveDiscussionWithoutAffectingSavedRecords() = runBlocking {
        db.taskDao().insertTask(Task(title = "Keep this"))
        say("let's talk through a new hobby"); say("I care about low cost")
        say("forget this discussion")
        assertFalse(AssistantDiscussion.hasContext(state()))
        say("plan that"); assertTrue(ConversationPlanStore(context).all().isEmpty())
        say("let's talk through a new hobby")
        OfflineAssistant.respond("/clear", context)
        assertNull(state().featureState); assertEquals(1, db.taskDao().getAllTasksList().size)
    }
    @Test fun quotedNegatedAndHypotheticalPlanningDoesNotEnterPlanMode() = runBlocking {
        say("let's talk through changing jobs")
        for (input in listOf("don't plan it", "what if we plan it", "\"let's plan it\"")) {
            say(input)
            assertTrue(ConversationPlanStore(context).all().isEmpty()); assertNotEquals(ChatMode.PLAN, ChatModeStore.get(context))
        }
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun planModePrefixKeepsTheDiscussionWhenRequestingHandoff() = runBlocking {
        say("let's talk through a trip to Jaipur")
        say("I need to avoid overnight travel")
        say("/plan let's plan it")
        val plan = ConversationPlanStore(context).active()!!
        assertEquals("a trip to Jaipur", plan.goal)
        assertTrue(plan.facts.values.any { it.contains("overnight travel") })
        assertEquals(ChatMode.PLAN, ChatModeStore.get(context)); assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun firstPersonDecisionAndRequirementsDoNotBecomeTasks() = runBlocking {
        say("I need to decide whether to switch jobs")
        say("I need to keep my evenings free")
        assertTrue(notes().contains("evenings free")); assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("/talk let's plan it")
        assertEquals("whether to switch jobs", ConversationPlanStore(context).active()!!.goal)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun discussionUndoCannotReverseAnUnrelatedSavedChange() = runBlocking {
        val id = db.taskDao().insertTask(Task(title = "Pay rent"))
        say("complete Pay rent"); assertTrue(db.taskDao().getTaskById(id)!!.isCompleted)
        say("let's talk through a new hobby"); say("I care about low cost")
        val before = notes()
        say("undo that")
        assertTrue(db.taskDao().getTaskById(id)!!.isCompleted); assertEquals(before, notes())
        say("/undo"); assertFalse(db.taskDao().getTaskById(id)!!.isCompleted)
    }
    @Test fun explicitResumeAfterAnInterruptionRestoresEvidenceAndCancelsOtherAuthority() = runBlocking {
        val id = db.taskDao().insertTask(Task(title = "Keep this"))
        say("/talk let's talk through a trip to Goa")
        say("I need quiet accommodation")
        val details = notes()
        say("/talk what time is it"); assertEquals("clock", state().topic)
        val response = say("/talk back to our discussion")
        assertTrue(response.formattedText.contains("quiet accommodation")); assertEquals(details, notes())
        say("/create Extra item")
        val old = say("delete all tasks").actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva confirm ") }.query
        say("back to our discussion"); assertNull(state().confirmationId)
        say("yes"); say(old)
        assertFalse(db.taskDao().getTaskById(id)!!.isTrashed)
        say("back to our discussion"); say("/talk let's plan it")
        assertTrue(ConversationPlanStore(context).active()!!.facts.values.any { it.contains("quiet accommodation") })
    }
    @Test fun expiredDiscussionCannotResumeAfterNewerAppActivity() = runBlocking {
        say("let's talk through a trip to Mysuru"); say("what time is it")
        val old = state(); old.featureState!!.getJSONObject("social").put("at", System.currentTimeMillis() - 900_001)
        AssistantSessionStore(context).save(old)
        assertTrue(say("/talk back to our discussion").formattedText.contains("isn't a recent discussion"))
        assertFalse(AssistantDiscussion.hasContext(state())); assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    private fun comparison() = discussion().getJSONObject("comparison")
    private fun figure(option: String, metric: String): String? {
        val array = comparison().getJSONArray("observations")
        return (0 until array.length()).map { array.getJSONObject(it) }.singleOrNull {
            it.getString("option") == option && it.getString("metric") == metric
        }?.getJSONObject("quantity")?.let { it.getString("amount") + " " + it.getString("unit") }
    }
    @Test fun numericTradeoffsPriorityAndUserChoiceCarryIntoPlanWithoutTaskWrites() = runBlocking {
        say("/talk let's talk through a trip to Goa"); say("/talk options are train or bus")
        say("/talk train costs INR 2500 and takes 6 hours")
        say("/talk bus takes 9 hours and costs INR 1800")
        val tradeoff = say("/talk compare them").formattedText
        assertTrue(tradeoff, tradeoff.contains("bus has 700 INR less cost than train"))
        assertTrue(tradeoff, tradeoff.contains("train has 180 minutes less travel time than bus"))
        assertTrue(tradeoff.contains("trade-off")); assertTrue(discussion().isNull("choice"))
        say("/talk cost matters more than time")
        assertTrue(say("/talk compare them").formattedText.contains("bus has the lowest supplied figure"))
        say("/talk prioritize travel time")
        assertTrue(say("/talk why").formattedText.contains("train has the lowest supplied figure"))
        say("/talk I choose train")
        say("/talk let's plan it")
        val plan = ConversationPlanStore(context).active()!!
        assertTrue(plan.facts["comparison figures supplied"]!!.contains("train — cost: 2500 INR; travel time: 360 minutes"))
        assertEquals("lower travel time", plan.facts["comparison priority"])
        assertEquals("train", plan.facts["current discussion choice"])
        assertTrue(plan.steps.isEmpty()); assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun limitsRejectChoicesAndBlockedHandoffKeepsTheOlderWorkspace() = runBlocking {
        say("/plan project launch"); say("add Review release for 30 minutes")
        val original = ConversationPlanStore(context).active()!!
        say("/talk let's talk through a trip"); say("options are train or bus")
        say("train costs INR 2500 and takes 6 hours"); say("bus costs INR 1800 and takes 9 hours")
        say("cost limit is INR 2000"); say("travel time limit is 8 hours")
        assertTrue(say("compare them").formattedText.contains("None of these options meets"))
        say("I choose bus"); assertTrue(discussion().isNull("choice"))
        say("actually cost limit is INR 3000")
        assertTrue(say("compare them").formattedText.contains("train is the only option"))
        say("I choose train")
        say("actually train costs INR 3500")
        assertTrue(say("/plan let's plan it").formattedText.contains("exceeds your limit"))
        assertEquals(ChatMode.TALK, ChatModeStore.get(context))
        assertEquals(original, ConversationPlanStore(context).active()); assertEquals(1, ConversationPlanStore(context).all().size)
        say("actually cost limit is INR 4000"); say("/talk let's plan it")
        assertEquals(2, ConversationPlanStore(context).all().size)
        assertEquals("train", ConversationPlanStore(context).active()!!.facts["current discussion choice"])
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun missingFigureQuestionSurvivesReloadAndExplicitDiscussionResumption() = runBlocking {
        say("I'm deciding between train and bus")
        say("train costs INR 2500 and takes 6 hours"); say("bus costs INR 1800")
        assertTrue(say("compare them").formattedText.contains("How long does bus take"))
        assertTrue(say("why are you asking").formattedText.contains("bus"))
        say("/talk what time is it")
        assertTrue(say("/talk back to our discussion").formattedText.contains("How long does bus take"))
        say("/talk nine hours")
        assertEquals("540 minutes", figure("bus", "TIME")); assertEquals("360 minutes", figure("train", "TIME"))
        assertTrue(comparison().isNull("pending"))
        assertTrue(say("compare them").formattedText.contains("180 minutes less travel time"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun currencyClarificationDoesNotAssumeAUnitOrConvertDifferentCurrencies() = runBlocking {
        say("I'm deciding between train and bus")
        assertTrue(say("train costs 2500").formattedText.contains("Which currency"))
        assertNull(figure("train", "COST"))
        say("INR"); assertEquals("2500 INR", figure("train", "COST"))
        say("bus costs USD 20")
        val unknown = say("compare them").formattedText
        assertTrue(unknown, unknown.contains("bus's cost in INR")); assertFalse(unknown.contains("lowest supplied figure"))
        say("USD 21"); assertEquals("20 USD", figure("bus", "COST")); assertFalse(comparison().isNull("pending"))
        say("actually INR 1900"); assertEquals("1900 INR", figure("bus", "COST"))
        assertTrue(say("compare them").formattedText.contains("bus is no worse"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun conflictingCompoundMeasurementEditsAreAtomicAndCorrectionsChangeOnlyNamedFields() = runBlocking {
        say("I'm deciding between train and bus")
        say("train costs INR 2500 and takes 6 hours")
        val before = comparison().getJSONArray("observations").toString()
        assertTrue(say("train costs INR 2600 and takes 7 hours").formattedText.contains("haven't changed either figure"))
        assertEquals(before, comparison().getJSONArray("observations").toString())
        say("actually train costs INR 2600 and takes 7 hours")
        assertEquals("2600 INR", figure("train", "COST")); assertEquals("420 minutes", figure("train", "TIME"))
        say("actually the first one takes 7.5 hours")
        assertEquals("2600 INR", figure("train", "COST")); assertEquals("450 minutes", figure("train", "TIME"))
        val fixed = comparison().getJSONArray("observations").toString()
        say("train costs INR 1?"); say("what if train costs INR 1")
        assertEquals(fixed, comparison().getJSONArray("observations").toString())
    }
    @Test fun changingOptionsDropsRemovedFiguresAndClearsOldShortAnswerTargets() = runBlocking {
        say("I'm deciding between train and bus")
        say("train takes 6 hours"); say("compare them")
        assertFalse(comparison().isNull("pending"))
        say("options are BUS or plane")
        assertEquals(0, comparison().getJSONArray("observations").length()); assertTrue(comparison().isNull("pending"))
        say("3 hours"); assertNull(figure("BUS", "TIME")); assertNull(figure("plane", "TIME"))
        say("the second one takes 2 days"); assertEquals("2880 minutes", figure("plane", "TIME"))
        say("options are plane or BUS"); assertEquals("2880 minutes", figure("plane", "TIME"))
        assertTrue(comparison().isNull("pending")); assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun unknownValuesRemainUnknownAndCanBeExplicitlyCleared() = runBlocking {
        say("I'm deciding between train and bus")
        say("train takes 6 hours"); say("compare them"); say("I don't know")
        assertNull(figure("bus", "TIME")); assertTrue(comparison().isNull("pending"))
        assertFalse(say("compare them").formattedText.contains("bus has the lowest"))
        say("bus takes 8 hours"); say("forget the first one travel time")
        assertNull(figure("train", "TIME")); assertEquals("480 minutes", figure("bus", "TIME"))
        say("time limit is 7 hours"); say("clear time limit")
        assertEquals(0, comparison().getJSONArray("limits").length())
        say("forget comparison figures")
        assertEquals(0, comparison().getJSONArray("observations").length())
        assertEquals(2, discussion().getJSONArray("options").length())
    }
    @Test fun overallBudgetAndQualitativePrioritiesDoNotBecomeNumericOptionLimits() = runBlocking {
        say("I'm deciding between train and bus")
        say("budget is 5000"); say("I care about quiet travel")
        say("train costs INR 3000"); say("bus costs INR 2000")
        assertEquals(0, comparison().getJSONArray("limits").length()); assertTrue(comparison().isNull("priority"))
        val result = say("compare them").formattedText
        assertTrue(result.contains("on each supplied cost/time figure")); assertTrue(result.contains("Other factors may still matter"))
        assertTrue(discussion().isNull("choice")); assertTrue(criteria().getJSONArray("rules").getJSONObject(0).getString("source").contains("quiet travel"))
        say("/plan")
        val plan = ConversationPlanStore(context).active()!!
        assertNull(plan.facts["comparison limits"]); assertNull(plan.facts["current discussion choice"])
        assertTrue(plan.facts.values.any { it.contains("quiet travel") })
    }
    @Test fun equalFiguresAreATieAndQuestionAmountsCannotReplaceKnownValues() = runBlocking {
        say("I'm deciding between train and bus")
        say("train costs INR 2500.00 and takes 1.5 hours")
        say("bus costs INR 2500 and takes 90 minutes")
        val result = say("compare them").formattedText
        assertTrue(result, result.contains("tie on all supplied")); assertFalse(result.contains("better on at least one"))
        val before = comparison().getJSONArray("observations").toString()
        say("actually train costs INR -1"); say("actually train takes 0 hours")
        assertEquals(before, comparison().getJSONArray("observations").toString())
        say("I'm not sure"); assertTrue(db.taskDao().getAllTasksList().isEmpty())
        assertTrue(comparison().isNull("pending"))
    }
    @Test fun olderDiscussionFramesWithoutComparisonStateStillResume() = runBlocking {
        say("let's talk through moving house"); say("I need a short commute")
        val old = state(); old.featureState!!.getJSONObject("social").getJSONObject("discussion").remove("comparison")
        AssistantSessionStore(context).save(old)
        assertTrue(say("back to our discussion").formattedText.contains("short commute"))
        assertEquals(0, comparison().getJSONArray("observations").length())
        say("options are city or suburb"); say("city costs INR 25000")
        assertEquals("25000 INR", figure("city", "COST")); assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun singleFactorQuestionsPreserveOverallPriorityAndExposeLimitViolations() = runBlocking {
        say("/talk I'm deciding between train and bus")
        say("/talk train costs INR 2500 and takes 6 hours")
        say("/talk bus costs INR 1800 and takes 9 hours")
        say("/talk prioritize travel time"); say("/talk time limit is 8 hours")
        val cost = say("/talk which one is cheaper?").formattedText
        assertTrue(cost, cost.contains("bus has the lowest supplied cost"))
        assertTrue(cost.contains("700 INR")); assertTrue(cost.contains("exceeds your limit"))
        assertTrue(say("/talk why?").formattedText.contains("bus has the lowest supplied cost"))
        assertEquals("TIME", comparison().getString("priority")); assertTrue(discussion().isNull("choice"))
        assertTrue(say("/talk which is faster?").formattedText.contains("train has the lowest supplied travel time"))
        say("forget the bus cost")
        assertTrue(say("which costs less?").formattedText.contains("bus's cost in INR"))
        say("1900"); assertEquals("1900 INR", figure("bus", "COST"))
        assertTrue(say("why").formattedText.contains("bus has the lowest supplied cost"))
        assertEquals("TIME", comparison().getString("priority"))
        assertTrue(say("compare them").formattedText.contains("train is the only option"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    private fun criteria() = comparison().getJSONObject("criteria")
    private fun quality(option: String, feature: String): Boolean? {
        val facts = criteria().getJSONArray("evidence")
        return (0 until facts.length()).map { facts.getJSONObject(it) }.singleOrNull {
            it.getString("option") == option && it.getString("feature") == feature
        }?.getBoolean("value")
    }
    @Test fun qualitativeRequirementsExcludeKnownConflictsAndCarryEvidenceIntoPlan() = runBlocking {
        say("/talk let's talk through a trip to Goa"); say("/talk options are train or bus")
        say("/talk I need quiet travel"); say("/talk I can't travel overnight")
        say("/talk train is quiet and not overnight")
        say("/talk bus isn't quiet and is overnight")
        say("/talk train costs INR 2500 and takes 6 hours"); say("/talk bus costs INR 1800 and takes 9 hours")
        val result = say("/talk compare them").formattedText
        assertTrue(result, result.contains("bus conflicts with your requirement"))
        assertTrue(result.contains("train is the only option"))
        assertEquals(true, quality("train", "quiet")); assertEquals(false, quality("train", "overnight"))
        assertEquals(false, quality("bus", "quiet")); assertEquals(true, quality("bus", "overnight"))
        say("I choose bus"); assertTrue(discussion().isNull("choice"))
        say("I choose train"); say("/talk let's plan it")
        val plan = ConversationPlanStore(context).active()!!
        assertEquals("train", plan.facts["current discussion choice"])
        assertTrue(plan.facts["qualitative criteria supplied"]!!.contains("required not overnight"))
        assertTrue(plan.facts.values.any { it.contains("train: quiet yes; overnight no") })
        assertTrue(plan.steps.isEmpty()); assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun hardUnknownsUseScopedYesNoAndSurviveInterruptionWithoutSavingTasks() = runBlocking {
        say("I'm deciding between train and bus"); say("I need quiet travel")
        assertTrue(say("compare them").formattedText.contains("Is train quiet"))
        assertTrue(say("why are you asking").formattedText.contains("Is train quiet"))
        say("/talk what time is it")
        assertTrue(say("/talk back to our discussion").formattedText.contains("Is train quiet"))
        say("yes"); assertEquals(true, quality("train", "quiet")); assertNull(quality("bus", "quiet"))
        assertTrue(say("compare them").formattedText.contains("Is bus quiet"))
        say("I don't know"); assertNull(quality("bus", "quiet")); assertTrue(criteria().isNull("pending"))
        say("I choose bus"); assertEquals("bus", discussion().getString("choice"))
        assertTrue(say("/plan").formattedText.contains("Before planning bus"))
        assertTrue(ConversationPlanStore(context).all().isEmpty()); assertEquals(ChatMode.TALK, ChatModeStore.get(context))
        say("no"); assertEquals(false, quality("bus", "quiet")); assertEquals(true, quality("train", "quiet"))
        assertTrue(say("/talk let's plan it").formattedText.contains("conflicts with your requirement"))
        say("I choose train"); say("/talk let's plan it")
        assertEquals("train", ConversationPlanStore(context).active()!!.facts["current discussion choice"])
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun qualitativePreferencesRemainSoftAndTopPriorityCanChangeAcrossDimensions() = runBlocking {
        say("I'm deciding between train and bus"); say("I prefer quiet travel")
        say("train is quiet"); say("bus is noisy")
        say("train costs INR 2500"); say("bus costs INR 1800")
        val result = say("compare them").formattedText
        assertTrue(result, result.contains("bus is no worse on each supplied cost/time figure"))
        assertFalse(result.contains("bus conflicts")); assertEquals(false, quality("bus", "quiet"))
        say("I care most about quiet travel")
        assertEquals("quiet", criteria().getString("primary")); assertTrue(comparison().isNull("priority"))
        assertTrue(say("compare them").formattedText.contains("descriptions favor train"))
        val cheap = say("which is cheaper?").formattedText
        assertTrue(cheap.contains("bus has the lowest supplied cost")); assertEquals("quiet", criteria().getString("primary"))
        say("prioritize cost")
        assertEquals("COST", comparison().getString("priority")); assertTrue(criteria().isNull("primary"))
        say("I choose bus"); assertEquals("bus", discussion().getString("choice"))
        say("/plan"); assertEquals("bus", ConversationPlanStore(context).active()!!.facts["current discussion choice"])
    }
    @Test fun qualitativeCorrectionsAreAtomicAndCanInvalidateAnExistingChoice() = runBlocking {
        say("I'm deciding between train and bus"); say("I need quiet travel")
        say("I don't want overnight travel")
        say("train is quiet and not overnight"); say("I choose train")
        val before = criteria().getJSONArray("evidence").toString()
        assertTrue(say("train is not quiet and is overnight").formattedText.contains("contradicts"))
        assertEquals(before, criteria().getJSONArray("evidence").toString())
        say("actually the first one is not quiet and is overnight")
        assertEquals(false, quality("train", "quiet")); assertEquals(true, quality("train", "overnight"))
        assertTrue(say("/plan let's plan it").formattedText.contains("conflicts with your requirement"))
        say("clear requirement for quiet"); say("clear requirement for overnight")
        say("/talk let's plan it")
        assertEquals("train", ConversationPlanStore(context).active()!!.facts["current discussion choice"])
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun knownQualitativeConflictsDoNotOverwriteAnOlderWorkspaceDuringHandoff() = runBlocking {
        say("/plan project"); say("add Research for 30 minutes")
        val original = ConversationPlanStore(context).active()!!
        say("/talk I'm deciding between train and bus"); say("I choose train")
        say("I don't want overnight travel"); say("train is overnight")
        val result = say("/plan let's plan it").formattedText
        assertTrue(result.contains("not overnight")); assertEquals(ChatMode.TALK, ChatModeStore.get(context))
        assertEquals(original, ConversationPlanStore(context).active()); assertEquals(1, ConversationPlanStore(context).all().size)
        say("actually train is not overnight"); say("/talk let's plan it")
        assertEquals(2, ConversationPlanStore(context).all().size); assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun changedOptionsDropQualityAnswersAndOldQuestionTargets() = runBlocking {
        say("I'm deciding between train and bus"); say("I need quiet travel")
        say("train is quiet"); say("compare them")
        assertFalse(criteria().isNull("pending"))
        say("options are plane or TRAIN")
        assertEquals(true, quality("TRAIN", "quiet")); assertNull(quality("plane", "quiet")); assertTrue(criteria().isNull("pending"))
        say("yes"); assertNull(quality("plane", "quiet")); assertNull(quality("bus", "quiet"))
        assertTrue(say("compare them").formattedText.contains("Is plane quiet"))
        say("noisy"); assertEquals(false, quality("plane", "quiet"))
        assertEquals(true, quality("TRAIN", "quiet")); assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun numericQuestionsPreserveQualitativeRulesAndUseOnlyOneReplyTarget() = runBlocking {
        say("I'm deciding between train and bus"); say("I need quiet travel")
        say("compare them"); assertFalse(criteria().isNull("pending"))
        say("train costs INR 2500"); say("which is cheaper?")
        assertTrue(criteria().isNull("pending")); assertFalse(comparison().isNull("pending"))
        say("1900"); assertEquals("1900 INR", figure("bus", "COST")); assertNull(quality("bus", "quiet"))
        assertTrue(say("compare them").formattedText.contains("Is train quiet"))
        say("not quiet"); assertEquals(false, quality("train", "quiet"))
        say("bus is quiet")
        assertTrue(say("compare them").formattedText.contains("bus is the only option"))
    }
    @Test fun questionsHypotheticalsAndUncertainDescriptionsDoNotAssertQualities() = runBlocking {
        say("I'm deciding between train and bus"); say("I need quiet travel")
        say("train is quiet?"); say("what if train is quiet"); say("train may be quiet")
        assertNull(quality("train", "quiet"))
        say("train is quiet and not quiet")
        assertNull(quality("train", "quiet"))
        say("train isn't quiet and overnight")
        assertNull(quality("train", "quiet")); assertNull(quality("train", "overnight"))
        say("I prefer train"); assertTrue(say("/plan").formattedText.contains("Before planning train"))
        assertTrue(ConversationPlanStore(context).all().isEmpty()); assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun scopedQualityAnswersCannotAuthorizeAnOlderTaskDeletion() = runBlocking {
        val id = db.taskDao().insertTask(Task(title = "Keep this"))
        val old = say("delete all tasks").actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva confirm ") }.query
        say("I'm deciding between train and bus"); say("I need quiet travel"); say("compare them")
        say("yes"); assertEquals(true, quality("train", "quiet")); say(old)
        assertFalse(db.taskDao().getTaskById(id)!!.isTrashed)
        assertNull(quality("bus", "quiet"))
    }
    @Test fun priorNumericFramesWithoutCriteriaRemainReadable() = runBlocking {
        say("I'm deciding between train and bus"); say("train costs INR 2500 and takes 6 hours")
        val old = state(); old.featureState!!.getJSONObject("social").getJSONObject("discussion").getJSONObject("comparison").remove("criteria")
        AssistantSessionStore(context).save(old)
        assertTrue(say("back to our discussion").formattedText.contains("2500 INR"))
        assertEquals("360 minutes", figure("train", "TIME")); assertEquals(0, criteria().getJSONArray("rules").length())
        say("I need quiet travel"); say("train is quiet")
        assertEquals(true, quality("train", "quiet"))
    }

    @Test fun requirementsAndDescriptionsGivenBeforeOptionsBecomeComparisonContext() = runBlocking {
        say("/talk let's talk through a trip to Goa")
        say("I need quiet travel"); say("I can't travel overnight")
        say("train is quiet and not overnight"); say("bus is noisy and is overnight")
        assertTrue(notes().contains("quiet travel"))
        say("options are train or bus")
        assertEquals(2, criteria().getJSONArray("rules").length())
        assertEquals(true, quality("train", "quiet")); assertEquals(false, quality("train", "overnight"))
        assertEquals(false, quality("bus", "quiet")); assertEquals(true, quality("bus", "overnight"))
        assertTrue(say("compare them").formattedText.contains("train is the only option confirmed"))
        say("I choose bus"); assertTrue(discussion().isNull("choice"))
        say("I choose train"); say("/plan")
        assertEquals("train", ConversationPlanStore(context).active()!!.facts["current discussion choice"])
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun contradictoryEarlierCriteriaRemainVisibleUntilTheUserCorrectsThem() = runBlocking {
        say("let's talk through a trip")
        say("I need quiet travel"); say("I don't want quiet travel")
        val reply = say("options are train or bus").formattedText
        assertTrue(reply, reply.contains("Start the changed criterion"))
        say("train is quiet"); say("I choose train")
        assertTrue(say("/plan").formattedText.contains("earlier detail still needs clarification"))
        assertTrue(ConversationPlanStore(context).all().isEmpty())
        say("actually I don't want quiet travel")
        assertFalse(notes().contains("I don't want quiet travel"))
        say("bus is not quiet"); say("I choose bus"); say("/talk let's plan it")
        assertEquals("bus", ConversationPlanStore(context).active()!!.facts["current discussion choice"])
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }
    @Test fun sixQualitiesAndSixOptionsFitEncryptedStateAndWorkspaceFactBounds() = runBlocking {
        val options = (1..6).map { "route " + ('a' + it - 1).toString().repeat(85) }
        val features = (1..6).map { "quality " + ('a' + it - 1).toString().repeat(30) }
        say("let's talk through choosing a route")
        say("options are " + options.joinToString(" or "))
        features.forEach { say("I need $it") }
        for (option in options) for (feature in features) say("$option has $feature")
        assertEquals(36, criteria().getJSONArray("evidence").length())
        val before = criteria().getJSONArray("rules").toString()
        assertTrue(say("I need another feature").formattedText.contains("six qualities"))
        assertEquals(before, criteria().getJSONArray("rules").toString())
        say("I choose " + options.first()); say("/plan")
        val plan = ConversationPlanStore(context).active()!!
        plan.validate()
        assertTrue(plan.facts.size <= 30); assertTrue(plan.facts.values.all { it.length <= 2000 })
        assertTrue(plan.facts.keys.count { it.startsWith("qualitative descriptions") } >= 2)
        assertEquals(options.first(), plan.facts["current discussion choice"])
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

}
