package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.util.assistant.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Only the new data/authority boundaries and the reported entry-path failure are exercised here. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ConversationQualitySafetyTest {
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
    @After fun cleanup() { ConversationPlanStore.clearAll(context); AssistantSessionStore(context).clear(); PlanningLibrary(context).clear(); ChatModeStore.set(context, ChatMode.CHAT); db.close() }
    private suspend fun say(text: String) = OfflineAssistant.respond(text, context) ?: error("Not handled: $text")
    private fun plan() = ConversationPlanStore(context).active()!!

    @Test fun reportedJourneyReplacesIrrelevantGoalAndEditsDraftWithoutNativeWrites() = runBlocking {
        say("/plan hey")
        val reply = say("lets plan a trip together")
        assertFalse(reply.formattedText.contains("Tell me a step"))
        assertFalse(plan().goal.equals("hey", true))
        say("its for my ibps exam")
        say("Chennai, next Friday, I need to be there by 8am")
        assertEquals("Chennai", plan().facts["destination"])
        assertEquals("8am", plan().facts["arrive by"])
        say("we need train tickets and somewhere to stay")
        assertEquals(2, plan().trip!!.items.size)
        val before = plan().steps
        say("why a hotel? could I travel overnight?")
        assertEquals(before, plan().steps)
        say("yes overnight, remove accommodation, add checking the admit card")
        assertEquals(2, plan().trip!!.items.size)
        assertTrue(plan().trip!!.items.any { it.title.contains("admit card", true) })
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("save the agreed tasks")
        assertNotNull(plan().reviewedRevision)
        say("we need pack the bag")
        assertNull(plan().reviewedRevision)
        say("apply plan")
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("just talk it through first")
        val kept = plan().steps
        say("we need another bag")
        assertEquals(kept, plan().steps)
    }

    @Test fun alternativesAndCompoundFailuresCannotApplySavedTaskAuthority() = runBlocking {
        say("/plan build a garden project"); say("add Buy seeds for 20 minutes")
        say("save plan")
        say("what if rename Buy seeds to Compare seeds")
        assertEquals("Buy seeds", plan().steps.single().title)
        assertNotNull(plan().alternative)
        val beforePause = plan().steps
        say("pause Buy seeds")
        assertEquals(beforePause, plan().steps)
        say("use this alternative")
        assertEquals("Compare seeds", plan().steps.single().title)
        assertNull(plan().reviewedRevision)
        val original = plan().steps
        say("rename Compare seeds to Choose seeds and remove missing step")
        assertEquals(original, plan().steps)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun splitMergePublishesBothDraftsAtomicallyAndKeepsWork() = runBlocking {
        say("/plan a project"); say("add Research for 20 minutes"); say("add Build for 30 minutes")
        say("split Research into a new plan called Investigation")
        assertEquals(2, ConversationPlanStore(context).all().size)
        assertEquals(listOf("Build"), plan().steps.map { it.title })
        say("merge plan Investigation")
        assertEquals(1, ConversationPlanStore(context).all().size)
        assertEquals(setOf("Research", "Build"), plan().steps.map { it.title }.toSet())
        val id = plan().id
        say("archive plan")
        assertNull(ConversationPlanStore(context).active())
        say("resume plan a project")
        assertEquals(id, plan().id)
        say("lets plan a project together")
        assertEquals(id, plan().id)
        assertEquals(1, ConversationPlanStore(context).all().size)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun explicitFactsSurviveArchiveAndConflictsFailBeforeMutation() {
        val library = PlanningLibrary(context)
        library.rememberFact("my project", "garden redesign")
        val archived = library.export()
        library.clear(); library.restore(archived, true)
        assertEquals("garden redesign", library.fact("my project"))
        val bad = JSONObject(archived.toString()).put("conversationFacts", JSONObject().put("my project", "unrelated work"))
        assertThrows(IllegalArgumentException::class.java) { library.preflight(bad, false) }
        assertEquals("garden redesign", library.fact("my project"))
        assertThrows(IllegalArgumentException::class.java) { library.rememberFact("my project", "unrelated work") }
        library.correctFact("MY PROJECT", "balcony garden")
        assertEquals("balcony garden", library.fact("my project"))
        assertThrows(IllegalArgumentException::class.java) { library.correctFact("missing memory", "new value") }
        assertEquals("balcony garden", library.fact("my project"))
        val old = PlanWorkspace(goal = "test")
        val raw = old.json().apply { remove("dialogue"); remove("alternative") }
        assertEquals(PlanningDialogueState(), PlanWorkspace.read(raw).dialogue)
        assertEquals(ConversationPlanCommitter.hash(old), ConversationPlanCommitter.hash(old.copy(dialogue = PlanningDialogueState(notes = listOf("hello")))))
    }

    @Test fun planComparisonRetainsFactsAndBlocksInvalidChoicesWithoutTaskWrites() = runBlocking {
        say("/plan a trip"); say("we need tickets")
        say("options are Train or Bus")
        say("Train costs INR 100 and takes 2 hours")
        say("Bus costs INR 200 and takes 1 hour")
        say("cost limit is INR 150")
        val decision = say("I choose Bus")
        assertFalse(plan().facts["current discussion choice"] == "Bus")
        assertNotNull(plan().dialogue.discussion)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("I choose Train")
        assertEquals("Train", plan().facts["current discussion choice"])
        val serialized = plan().json()
        assertEquals("Train", PlanWorkspace.read(serialized).facts["current discussion choice"])
    }

    @Test fun generalTopicCannotHandOffAnOlderDiscussionOrReviveAuthority() = runBlocking {
        say("let's talk through a trip")
        say("options are Train or Bus")
        say("I choose Train")
        say("tell me about active recall")
        say("plan it")
        assertTrue(plan().goal.contains("active recall"))
        assertFalse(plan().facts.values.any { it == "Train" })
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun todaysCapacityCannotRelaxGlobalCapsOrLeakToTomorrow() {
        val today = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { clear(); set(2027, 0, 4, 9, 0) }
        val tomorrow = (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        val policy = PlanPolicy(dailyMinutes = 120, dateBudgets = mapOf("2027-01-04" to 30))
        assertEquals(30, policy.budget(today))
        assertEquals(120, policy.budget(tomorrow))
        assertEquals(120, policy.copy(dateBudgets = mapOf("2027-01-04" to 240)).budget(today))
        assertEquals(policy, PlanPolicy.read(policy.json()))
    }

    @Test fun naturalFactsCannotStealExplicitSchedulingOrPendingProfileQuestions() = runBlocking {
        say("/plan a trip"); say("add Pack bag for 20 minutes")
        say("schedule Pack bag tomorrow at 10am")
        assertNotNull(plan().steps.single().start)
        val first = plan().steps.single().start!!
        say("schedule Pack bag on tomorrow at 10:30am")
        assertTrue(plan().steps.single().start!! > first)
        say("move Pack bag tomorrow at 11am")
        assertTrue(plan().steps.single().start!! > first)
        say("exit plan")
        say("remind me tomorrow at 6pm")
        assertEquals("task_title", AssistantSessionStore(context).load().expected)
        say("what do you remember about me")
        assertEquals("task_title", AssistantSessionStore(context).load().expected)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun higherPrioritySupportCannotBeCapturedByPlanOrGeneralTopics() = runBlocking {
        say("/plan a trip"); say("we need tickets"); say("save plan")
        assertNotNull(plan().reviewedRevision)
        val support = say("I am going to hurt myself now")
        assertTrue(support.formattedText.contains("immediate safety"))
        assertEquals(ChatMode.TALK, ChatModeStore.get(context))
        assertNull(plan().reviewedRevision)
        say("tell me about active recall")
        assertTrue(say("I am going to hurt myself now").formattedText.contains("immediate safety"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun pausedPrerequisitesCannotBeScheduledOrSilentlyLoseProtectedDependentTimes() = runBlocking {
        val prerequisite = PlanStep(title = "Research", minutes = 20)
        val dependent = PlanStep(title = "Build", minutes = 30, prerequisites = setOf(prerequisite.id))
        val initial = PlanWorkspace(goal = "Ship project", timezone = "UTC", steps = listOf(prerequisite, dependent))
        val oldHash = ConversationPlanCommitter.hash(initial)
        val legacy = initial.json()
        assertFalse(legacy.getJSONArray("steps").getJSONObject(0).has("workState"))
        assertEquals(oldHash, ConversationPlanCommitter.hash(PlanWorkspace.read(legacy)))
        assertTrue(runCatching { PlanProgress.answer(initial, "pause Research and remove Build") }.isFailure)
        val blocked = PlanProgress.answer(initial, "Research is blocked because the API is unavailable")!!.plan
        assertEquals(PlanWorkState.BLOCKED, blocked.steps.first().workState)
        assertEquals("the API is unavailable", blocked.steps.first().blockReason)
        val arranged = PlanScheduler.arrange(blocked, 1_800_000_000_000L, 1, 8, 18)
        assertEquals(2, arranged.unscheduled.size)
        assertTrue(arranged.steps.all { it.start == null })
        assertTrue(PlanProgress.answer(blocked, "what can I do now")!!.text.contains("prerequisite"))
        val resumed = PlanProgress.answer(blocked, "unblock Research")!!.plan
        assertEquals(PlanWorkState.READY, resumed.steps.first().workState)
        assertNull(resumed.steps.first().blockReason)
        val skipped = PlanProgress.answer(initial, "skip Research for now")!!.plan
        assertEquals(2, skipped.steps.size)
        assertEquals(PlanWorkState.SKIPPED, skipped.steps.first().workState)
        val roundTrip = PlanWorkspace.read(JSONObject(skipped.json().toString()))
        assertEquals(PlanWorkState.SKIPPED, roundTrip.steps.first().workState)
        val at = 1_800_000_000_000L
        val scheduled = initial.copy(steps = listOf(prerequisite.copy(start = at), dependent.copy(start = at + 30 * 60_000L, protected = true)))
        scheduled.validate()
        assertTrue(runCatching { PlanProgress.answer(scheduled, "pause Research") }.isFailure)
        assertNotNull(scheduled.steps.first().start)
        val cleared = PlanProgress.answer(scheduled.copy(steps = scheduled.steps.map { it.copy(protected = false) }), "pause Research")!!.plan
        assertTrue(cleared.steps.all { it.start == null })
        ConversationPlanStore(context).put(initial); ChatModeStore.set(context, ChatMode.PLAN)
        say("Research is blocked because the API is unavailable")
        assertEquals(PlanWorkState.BLOCKED, plan().steps.first().workState)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun milestonesKeepTravelAndStudyFactsSeparateAndRetainReviewedNativeSave() = runBlocking {
        say("/plan prepare for an exam trip")
        say("suggest milestones")
        assertTrue(plan().steps.isEmpty())
        say("use those milestones")
        assertEquals(setOf("travel", "study", "exam"), plan().milestones.map { it.method }.toSet())
        say("focus on travel")
        say("budget is INR 5000")
        say("destination is Chennai")
        say("focus on study")
        say("budget is INR 300")
        say("quant and reasoning")
        say("we need another practice session")
        val travel = plan().milestones.single { it.method == "travel" }
        val study = plan().milestones.single { it.method == "study" }
        assertEquals("INR 5000", travel.facts["budget"])
        assertEquals("Chennai", travel.facts["destination"])
        assertEquals("INR 300", study.facts["budget"])
        assertEquals("quant and reasoning", study.facts["topics"])
        assertNull(plan().facts["budget"])
        assertTrue(plan().steps.single { it.title.equals("another practice session", true) }.id in study.stepIds)
        say("for travel, deadline is next Friday")
        assertTrue(plan().steps.filter { it.id in travel.stepIds }.all { it.deadline != null })
        assertTrue(plan().steps.filter { it.id in study.stepIds }.all { it.deadline == null })
        say("save the agreed tasks")
        assertNotNull(plan().reviewedRevision)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("for study, budget is INR 450")
        assertNull(plan().reviewedRevision)
        say("apply plan")
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("save the agreed tasks"); say("apply plan")
        assertEquals(plan().steps.size, db.taskDao().getAllTasksList().size)
        assertTrue(db.taskDao().getAllTasksList().none { task -> plan().milestones.any { it.title == task.title } })
    }

    @Test fun milestoneLinksSurviveSplitMergeTemplateAndArchiveWithoutLosingScope() = runBlocking {
        val research = PlanStep(title = "Research", minutes = 20)
        val build = PlanStep(title = "Build", minutes = 30)
        val group = PlanMilestone(title = "Preparation", domain = "project", stepIds = setOf(research.id, build.id), facts = mapOf("success" to "a working prototype"))
        val initial = PlanWorkspace(goal = "A project", steps = listOf(research, build), milestones = listOf(group))
        initial.validate(); ConversationPlanStore(context).put(initial); ChatModeStore.set(context, ChatMode.PLAN)
        say("split Research into a new plan called Investigation")
        assertEquals(setOf(build.id), plan().milestones.single().stepIds)
        assertEquals(setOf(research.id), ConversationPlanStore(context).all().single { it.goal == "Investigation" }.milestones.single().stepIds)
        say("merge plan Investigation")
        assertEquals(plan().steps.map { it.id }.toSet(), plan().milestones.single().stepIds)
        assertEquals("a working prototype", plan().milestones.single().facts["success"])
        val payload = AssistantPlanningArchive.export(context)
        AssistantPlanningArchive.restore(context, payload, emptyMap(), true)
        assertEquals(2, plan().milestones.single().stepIds.size)
        val library = PlanningLibrary(context); library.saveTemplate("Project layout", plan())
        val template = library.useTemplate("Project layout")
        assertTrue(template.milestones.single().facts.isEmpty())
        assertEquals(template.steps.map { it.id }.toSet(), template.milestones.single().stepIds)
        val old = PlanWorkspace(goal = "Old draft")
        assertFalse(old.json().has("milestones"))
        assertEquals(ConversationPlanCommitter.hash(old), ConversationPlanCommitter.hash(PlanWorkspace.read(old.json())))
        val bad = plan().json(); bad.getJSONArray("milestones").getJSONObject(0).put("steps", org.json.JSONArray(listOf("unavailable")))
        assertTrue(runCatching { PlanWorkspace.read(bad) }.isFailure)
        assertEquals(2, plan().steps.size)
    }

    @Test fun milestoneCompoundEditsAndProtectedScopeChangesAreAtomic() = runBlocking {
        say("/plan a project"); say("add Build for 30 minutes")
        val original = plan().steps
        say("add milestone Logistics; remove missing step")
        assertEquals(original, plan().steps)
        assertTrue(plan().milestones.isEmpty())
        say("suggest milestones"); say("add Research for 20 minutes")
        val count = plan().steps.size
        say("use those milestones")
        assertEquals(count, plan().steps.size)
        assertTrue(plan().milestones.isEmpty())
        val step = plan().steps.first().copy(protected = true)
        val group = PlanMilestone(title = "Delivery", stepIds = setOf(step.id))
        ConversationPlanStore(context).put(plan().copy(steps = listOf(step), selected = step.id, editFocus = null, milestones = listOf(group)))
        say("for Delivery, deadline is next Friday")
        assertNull(plan().steps.single().deadline)
        assertTrue(plan().milestones.single().facts.isEmpty())
    }

    @Test fun explicitRequestsOutrankGeneralDiscussionAndCreativeContinuations() = runBlocking {
        say("tell me about active recall")
        val first = AssistantSessionStore(context).load()
        assertEquals(ConversationRouting.Owner.NATIVE, ConversationRouting.winner("show my tasks", first))
        assertEquals(ConversationRouting.Owner.DICTIONARY, ConversationRouting.winner("define harbour", first))
        say("options are Train or Bus")
        assertEquals("discussion", AssistantSessionStore(context).load().featureState?.optString("activeDialogue"))
        say("tell me a tiny story about a lighthouse")
        val creative = AssistantSessionStore(context).load()
        assertEquals("creative", creative.featureState?.optString("activeDialogue"))
        val stage = creative.featureState?.optJSONObject("creativeStory")?.optInt("stage")
        say("show my tasks")
        assertEquals(stage, AssistantSessionStore(context).load().featureState?.optJSONObject("creativeStory")?.optInt("stage"))
        say("remind me tomorrow at 6pm")
        assertEquals("task_title", AssistantSessionStore(context).load().expected)
        say("who are you")
        assertEquals("task_title", AssistantSessionStore(context).load().expected)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun reasoningRecomputesConditionalConsequencesWithoutTaskAuthority() = runBlocking {
        say("reason about the journey")
        say("if the train is late then the transfer is missed")
        say("fact: the train is late")
        assertTrue(say("what follows").formattedText.contains("the transfer is missed"))
        assertTrue(say("why the transfer is missed").formattedText.contains("your rule"))
        say("retract premise the train is late")
        assertTrue(say("what follows").formattedText.contains("No additional conclusion"))
        val hypothetical = say("what if the train is late")
        assertTrue(hypothetical.formattedText.contains("the transfer is missed"))
        assertTrue(ConversationReasoning.read(AssistantSessionStore(context).load().featureState?.optJSONObject("reasoningFrame")).claims.isEmpty())
        say("fact: the train is late"); say("fact: the connection is short")
        say("if the train is late and the connection is short then choose a safer connection")
        assertTrue(say("what follows").formattedText.contains("choose a safer connection"))
        say("retract rule if the train is late and the connection is short then choose a safer connection")
        assertFalse(say("what follows").formattedText.contains("choose a safer connection"))
        say("fact: not the train is late")
        assertTrue(say("what follows").formattedText.contains("conflicting"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("save this discussion as Journey reasoning")
        assertTrue(PlanningLibrary(context).list("discussions").contains("Journey reasoning"))
        // Converging proof paths must not recursively duplicate unbounded explanation strings.
        val branching = ConversationReasoning.Frame(claims = (0..3).map { ConversationReasoning.Claim("premise $it") },
            rules = (4..15).map { i -> ConversationReasoning.Rule((i - 3 until i).joinToString(" and ") { "premise $it" }, "premise $i") })
        val trace = ConversationReasoning.turn(branching.json(), "why premise 15")!!.text
        assertTrue(trace.contains("trace shortened"))
        assertTrue(trace.length < 1_950)
    }

    @Test fun acceptedBreakdownPreservesOutcomeReferencesAndCannotReplayAStaleProposal() = runBlocking {
        say("/plan a project"); say("add Build version for 60 minutes"); say("add Release for 15 minutes")
        say("Release depends on Build version")
        val original = plan().steps.first().id
        say("break down Build version")
        assertEquals(2, plan().steps.size)
        say("add Check resources for 10 minutes")
        say("use that breakdown")
        assertEquals(3, plan().steps.size)
        say("break down Build version"); say("use that breakdown")
        val final = plan().steps.single { it.id == original }
        assertEquals("breakdown verification", final.source)
        assertNull(final.minutes)
        assertEquals(3, final.prerequisites.size)
        assertEquals(setOf(original), plan().steps.single { it.title == "Release" }.prerequisites)
        assertTrue(plan().steps.filter { it.source == "accepted breakdown" }.all { it.minutes == null })
        plan().validate()
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("save the agreed tasks"); say("apply plan")
        assertEquals(plan().steps.size, db.taskDao().getAllTasksList().size)
    }

    @Test fun sharedDateReconciliationPreservesUnrelatedWorkAndBoundsNewActions() = runBlocking {
        val zone = java.util.TimeZone.getDefault()
        val now = System.currentTimeMillis()
        fun day(offset: Int) = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).apply { timeZone = zone }.format(java.util.Date(now + offset * 86_400_000L))
        val study = PlanStep(title = "Study", minutes = 30, start = now + 7 * 86_400_000L)
        val release = PlanStep(title = "Submit", minutes = 15, start = now + 8 * 86_400_000L, prerequisites = setOf(study.id))
        val other = PlanStep(title = "Other work", minutes = 20, start = now + 2 * 86_400_000L)
        val group = PlanMilestone(title = "Study milestone", domain = "study", method = "study", stepIds = setOf(study.id))
        ConversationPlanStore(context).put(PlanWorkspace(goal = "Exam project", steps = listOf(study, release, other), milestones = listOf(group)))
        ChatModeStore.set(context, ChatMode.PLAN)
        say("shared exam date is ${day(10)}"); say("use shared exam date as deadline for study")
        val before = plan().steps
        say("shared exam date is ${day(5)}")
        assertEquals(before, plan().steps)
        assertNotNull(plan().dialogue.sharedChange)
        say("apply shared change")
        assertEquals(before, plan().steps)
        say("shared exam date is ${day(5)}"); say("reschedule affected work")
        assertNull(plan().steps.single { it.id == study.id }.start)
        assertNull(plan().steps.single { it.id == release.id }.start)
        assertEquals(other.start, plan().steps.single { it.id == other.id }.start)
        say("focus on study"); say("we need extra practice")
        assertEquals(plan().milestones.single().boundDeadline, plan().steps.single { it.title.equals("extra practice", true) }.deadline)
        val loaded = PlanWorkspace.read(plan().json())
        assertEquals(plan().milestones, loaded.milestones)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        val old = PlanWorkspace(goal = "Old format")
        assertFalse(old.json().has("goalDeadline"))
        assertEquals(ConversationPlanCommitter.hash(old), ConversationPlanCommitter.hash(PlanWorkspace.read(old.json())))
    }

    @Test fun reasoningScopesAndLiteralPlanListsRemainDataAcrossReload() = runBlocking {
        say("/plan a project"); say("add \"Buy milk, tea and remove bread\"")
        assertEquals(1, plan().steps.size)
        assertEquals("Buy milk, tea and remove bread", plan().steps.single().title)
        say("create milestone Travel")
        say("fact: the venue is known"); say("if the venue is known then compare routes")
        val group = plan().milestones.single()
        assertNotNull(plan().dialogue.reasoning?.optJSONObject(group.id))
        val loaded = PlanWorkspace.read(plan().json())
        assertNotNull(loaded.dialogue.reasoning?.optJSONObject(group.id))
        say("back to the whole plan"); say("what follows")
        assertEquals(1, plan().steps.size)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun legacyGamesYieldToExplicitRequestsAndScopedVoiceDoesNotFillTaskPrompts() = runBlocking {
        say("give me a riddle")
        val frame = AssistantSessionStore(context).load().featureState?.optJSONObject("social")
        val tries = frame?.optInt("tries")
        say("show my tasks")
        assertEquals(tries, AssistantSessionStore(context).load().featureState?.optJSONObject("social")?.optInt("tries"))
        say("for this chat ask fewer questions")
        assertFalse(ConversationVoice.questions(context, AssistantSessionStore(context).load()))
        val greeting = say("hi")
        assertFalse(greeting.formattedText.contains("?"))
        say("remind me tomorrow at 6pm")
        assertEquals("task_title", AssistantSessionStore(context).load().expected)
        say("for this chat be concise")
        assertEquals("task_title", AssistantSessionStore(context).load().expected)
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
    }

    @Test fun weightedAdviceCannotInventUnknownDataOrBypassHardLimits() {
        val options = listOf("Train", "Bus")
        var c = DiscussionComparison()
        c = c.answer("Train costs INR 100 and takes 2 hours", options)!!.state
        c = c.answer("Bus costs INR 200 and takes 1 hour", options)!!.state
        val weighted = c.answer("weights cost 3 and time 1", options)!!
        assertTrue(weighted.text.contains("Train ranks first"))
        c = weighted.state.answer("cost limit is INR 150", options)!!.state
        assertFalse(c.compare(options).text.contains("Bus ranks first"))
        assertEquals(c.weights, DiscussionComparison.read(c.json(), options).weights)
        val unknown = DiscussionComparison().answer("weights cost 3 and time 1", options)!!
        assertTrue(unknown.text.contains("exact known"))
    }
}
