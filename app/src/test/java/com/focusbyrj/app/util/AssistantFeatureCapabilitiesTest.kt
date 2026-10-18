package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.data.drill.*
import com.focusbyrj.app.util.assistant.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AssistantFeatureCapabilitiesTest {
    private lateinit var context: Context
    private lateinit var app: FocusApplication
    private lateinit var db: FocusDatabase
    private lateinit var vocab: VocabDatabase
    private lateinit var drill: DrillDatabase
    private val pkg = "com.example.reader"
    @Before fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        vocab = Room.inMemoryDatabaseBuilder(base, VocabDatabase::class.java).allowMainThreadQueries().build()
        drill = Room.inMemoryDatabaseBuilder(base, DrillDatabase::class.java).allowMainThreadQueries().build()
        for ((field, value) in listOf("database\$delegate" to lazyOf(db), "vocabDatabase\$delegate" to lazyOf(vocab))) {
            app.javaClass.getDeclaredField(field).apply { isAccessible = true; set(app, value) }
        }
        DrillDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true; set(null, drill) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        shadowOf(base.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg; enabled = true; nonLocalizedLabel = "Reader" }
        })
        AssistantSessionStore(context).clear()
        for (name in listOf("focus_prefs", "bubble_prefs", "ayva_action_receipts", "ayva_personalization", "daily_learning_quests_prefs", "focus_economy_prefs", "ayva_backup_verification", "auto_backup_scheduler_prefs")) context.getSharedPreferences(name, 0).edit().clear().commit()
        FocusEconomyManager.init(context); AyvaTalkEngine.resetConversation()
    }
    @After fun tearDown() {
        AssistantSessionStore(context).clear(); FocusSessionManager.stop(context)
        DrillDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true; set(null, null) }
        db.close(); vocab.close(); drill.close(); AyvaTalkEngine.resetConversation()
    }
    private suspend fun say(text: String) = OfflineAssistant.respond(text, context) ?: error("Not handled: $text")
    private fun state() = AssistantSessionStore(context).load()
    private fun confirm(r: AyvaTalkEngine.TalkResponse) = r.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva confirm ") }.query
    private fun undo(r: AyvaTalkEngine.TalkResponse) = r.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva undo ") }.query
    private suspend fun wizard(name: String = "Study"): AyvaTalkEngine.TalkResponse {
        say("create routine $name"); say("weekdays"); say("22:00 to 06:00"); say(pkg); return say("soft")
    }
    private fun idiom(id: Int = 1, name: String = "break the ice", bookmark: Int = 0, learned: Long? = null, mastered: Int = 0) {
        vocab.openHelper.writableDatabase.execSQL("INSERT INTO idioms (id, idiom, meaning, is_mastered, is_bookmarked, learned_at) VALUES (?, ?, ?, ?, ?, ?)", arrayOf(id, name, "Start a conversation", mastered, bookmark, learned))
    }
    private fun word(id: Int = 2, name: String = "curious", learned: Long? = null, mastered: Int = 0) {
        vocab.openHelper.writableDatabase.execSQL("INSERT INTO ows (id, term, definition, is_mastered, is_bookmarked, learned_at) VALUES (?, ?, ?, ?, ?, ?)", arrayOf(id, name, "Eager to learn", mastered, 0, learned))
    }
    private suspend fun savedTest(id: String, kind: String = "drill", difficulty: String? = "easy", correct: Int = 1, total: Int = 2, seconds: Long = 30, at: Long = System.currentTimeMillis()) {
        val q = DrillQuestionResult(1, "Arithmetic", questionText = "2 + 3 = ?", options = listOf("4", "5"), correctIndex = 1, userSelectedIndex = 0, status = "wrong", explanation = "2 + 3 is 5")
        val summary = DrillSummary(id, "Learner", totalQuestions = total, correctCount = correct, timeSpentSeconds = seconds, practiceKind = kind, difficulty = difficulty, questions = listOf(q))
        drill.drillSessionDao().insertSession(DrillSessionEntity(id, "Learner", total, correct, seconds, 0, kind == "blitz", timestamp = at, summaryJson = summary.toJson()))
    }
    @Test fun routineWizardPreviewsBeforeSavingAndCanUndo() = runBlocking {
        val preview = wizard(); assertTrue(preview.formattedText.contains("overnight")); assertTrue(db.scheduleDao().getAllSchedulesSync().isEmpty())
        val token = confirm(preview); val saved = say(token)
        val routine = db.scheduleDao().getAllSchedulesSync().single()
        assertEquals("2,3,4,5,6", routine.daysOfWeek); assertEquals("SOFT", routine.mode)
        say(token); assertEquals(1, db.scheduleDao().getAllSchedulesSync().size)
        say(undo(saved)); assertTrue(db.scheduleDao().getAllSchedulesSync().isEmpty())
    }
    @Test fun invalidRoutineAnswersStayInTheSameSlot() = runBlocking {
        say("create routine Study"); say("someday"); assertEquals("days", state().featureState!!.getJSONObject("routineDraft").getString("field"))
        say("weekdays"); say("25:00 to 26:00"); assertEquals("time", state().featureState!!.getJSONObject("routineDraft").getString("field"))
        say("9am to 10am"); say("com.android.settings"); assertEquals("apps", state().featureState!!.getJSONObject("routineDraft").getString("field"))
        assertTrue(db.scheduleDao().getAllSchedulesSync().isEmpty())
    }
    @Test fun showAppsKeepsRoutineQuestion() = runBlocking {
        say("create routine Study"); say("weekdays"); say("9am to 10am"); say("show apps")
        assertEquals("feature_value", state().expected); assertEquals("apps", state().featureState!!.getJSONObject("routineDraft").getString("field"))
    }
    @Test fun interruptedRoutineQuestionCanResumeWithoutRestoringAConfirmation() = runBlocking {
        say("create routine Study"); say("weekdays"); say("backup status")
        assertNull(state().expected); say("back to the routine")
        assertEquals("feature_value", state().expected); assertNull(state().confirmationId)
        say("9am to 10am"); say(pkg); assertNotNull(confirm(say("soft")))
    }
    @Test fun cancelledRoutineDoesNotReturnOnResume() = runBlocking {
        say("create routine Study"); say("cancel"); say("back to the routine")
        assertNull(state().expected); assertFalse(state().featureState?.has("routineDraft") == true)
    }
    @Test fun routineEditRejectsExternalChanges() = runBlocking {
        say(confirm(wizard())); val old = db.scheduleDao().getAllSchedulesSync().single()
        say("edit routine Study"); say("name"); val preview = say("Deep Study")
        db.scheduleDao().insertSchedule(old.copy(name = "Changed elsewhere")); say(confirm(preview))
        assertEquals("Changed elsewhere", db.scheduleDao().getScheduleById(old.id)!!.name)
    }
    @Test fun routineOverlapHandlesWeekBoundaryAndDisabledSchedules() {
        val a = FocusSchedule(name = "Night", startHour = 22, startMinute = 0, endHour = 6, endMinute = 0, daysOfWeek = "7")
        val b = FocusSchedule(name = "Morning", startHour = 5, startMinute = 0, endHour = 7, endMinute = 0, daysOfWeek = "1")
        assertTrue(AssistantFeatureLanguage.overlaps(a,b)); assertFalse(AssistantFeatureLanguage.overlaps(a,b.copy(isEnabled = false)))
        assertFalse(AssistantFeatureLanguage.overlaps(a,b.copy(daysOfWeek = "2")))
    }
    @Test fun activeStrictRoutineCannotBeEdited() = runBlocking {
        db.scheduleDao().insertSchedule(FocusSchedule(name = "Strict", startHour = 0, startMinute = 0, endHour = 23, endMinute = 59, daysOfWeek = "1,2,3,4,5,6,7", appsToBlock = pkg))
        assertTrue(say("edit routine Strict").formattedText.contains("cannot be edited")); assertNull(state().expected)
    }
    @Test fun deviceHealthCannotBeCreatedAsTaskWhenTaskTitleIsPending() = runBlocking {
        say("remind me tomorrow"); val r = say("check device health")
        assertTrue(r.formattedText.contains("Device checks")); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }
    @Test fun missingUsagePermissionIsUnknownNotZero() = runBlocking {
        val r = say("compare my screen time"); assertTrue(r.formattedText.contains("unavailable") || r.formattedText.contains("missing")); assertFalse(r.formattedText.contains("today: 0m"))
    }
    @Test fun dailyAppLimitPreviewsHasUndoAndCannotReplay() = runBlocking {
        val p = say("set a daily limit for app $pkg to 30 minutes")
        assertNull(db.appRestrictionDao().getRestriction(pkg)); val token = confirm(p); val saved = say(token)
        assertEquals(30, db.appRestrictionDao().getRestriction(pkg)!!.timeLimitMinutes)
        say(token); say(undo(saved)); assertNull(db.appRestrictionDao().getRestriction(pkg))
    }
    @Test fun staleAppRulePreviewCannotOverwriteNewerRule() = runBlocking {
        val preview = say("set a daily limit for app $pkg to 30 minutes")
        db.appRestrictionDao().insertRestriction(AppRestriction(pkg, "Reader", timeLimitMinutes = 20))
        say(confirm(preview)); assertEquals(20, db.appRestrictionDao().getRestriction(pkg)!!.timeLimitMinutes)
    }
    @Test fun appRuleUndoRespectsFocusProtection() = runBlocking {
        val saved = say(confirm(say("set a daily limit for app $pkg to 30 minutes")))
        say("start focus for 5 minutes"); say(undo(saved)); assertNotNull(db.appRestrictionDao().getRestriction(pkg))
    }
    @Test fun appPronounMustHaveFreshUsageContext() = runBlocking {
        assertTrue(say("restrict that app").formattedText.contains("fresh usage")); assertTrue(db.appRestrictionDao().getAllRestrictionsSync().isEmpty())
    }
    @Test fun blockingExplanationSeparatesConfiguredRulesAndVisibleOverlay() = runBlocking {
        db.appRestrictionDao().insertRestriction(AppRestriction(pkg, "Reader", true, restrictionMode = "TIME_LIMIT", timeLimitMinutes = 30))
        val r = say("why is Reader blocked").formattedText
        assertTrue(r.contains("30-minute")); assertTrue(r.contains("not proof")); assertTrue(r.contains("unknown"))
    }
    @Test fun advancedHabitScheduleUsesExistingSchedulerAndFullUndo() = runBlocking {
        val id = db.habitDao().insertHabit(Habit(title = "Water", isReminderEnabled = false))
        val old = db.habitDao().getHabitById(id)!!
        val preview = say("set habit Water interval to 90 minutes"); assertEquals(old, db.habitDao().getHabitById(id))
        val saved = say(confirm(preview)); val after = db.habitDao().getHabitById(id)!!
        assertEquals(90, after.totalIntervalMinutes); assertEquals(HabitType.INTERVAL_WINDOW, after.type); assertFalse(after.isReminderEnabled)
        say(undo(saved)); assertEquals(old, db.habitDao().getHabitById(id))
    }
    @Test fun habitOvernightAndFixedClockAreExplicit() = runBlocking {
        val id = db.habitDao().insertHabit(Habit(title = "Water", isReminderEnabled = false))
        say(confirm(say("set habit Water window to 22:00 to 06:00")))
        assertEquals(22, db.habitDao().getHabitById(id)!!.windowStartHour)
        val preview = say("set habit Water reminder time to 9am"); assertTrue(preview.formattedText.contains("09:00")); say(confirm(preview))
        assertEquals(HabitType.ONCE_DAILY, db.habitDao().getHabitById(id)!!.type); assertEquals(9, db.habitDao().getHabitById(id)!!.fixedReminderHour)
    }
    @Test fun unsafeHabitIntervalsAreRejected() {
        for (raw in listOf("0 minutes", "4 minutes", "25 hours", "9999999999999999999999")) assertNull(AssistantHabitEditing.value(HabitField.INTERVAL, raw))
        assertEquals("90", AssistantHabitEditing.value(HabitField.INTERVAL,"90 minutes")); assertNull(AssistantHabitEditing.value(HabitField.WINDOW,"9am to 9am"))
    }
    @Test fun storedVocabularyDefinitionAndBookmarkUndo() = runBlocking {
        idiom(); val lookup = say("explain idiom break the ice"); assertTrue(lookup.formattedText.contains("Start a conversation"))
        val preview = say("bookmark that idiom"); assertEquals(0, vocab.vocabDao().getIdiomById(1)!!.isBookmarked)
        val saved = say(confirm(preview)); assertEquals(1, vocab.vocabDao().getIdiomById(1)!!.isBookmarked)
        say(undo(saved)); assertEquals(0, vocab.vocabDao().getIdiomById(1)!!.isBookmarked)
    }
    @Test fun nativeCardReferencesAskForAmbiguousWordAndSupportTypedId() = runBlocking {
        idiom(); word()
        OfflineAssistant.recordVocabularyCard(context, JSONObject().put("idiom", JSONObject().put("id", 1)).put("ows", JSONObject().put("id", 2)).toString(), System.currentTimeMillis())
        val ambiguous = say("bookmark that word"); assertEquals(2, ambiguous.actions.size); assertNull(state().confirmationId)
        val preview = say((ambiguous.actions.first() as TalkAction.AskQuery).query); say(confirm(preview))
        assertEquals(1, vocab.vocabDao().getIdiomById(1)!!.isBookmarked)
    }
    @Test fun wordPronounDoesNotFollowAnotherTopic() = runBlocking {
        idiom(); say("look up break the ice"); say("backup status")
        assertTrue(say("bookmark that idiom").formattedText.contains("can't identify")); assertEquals(0, vocab.vocabDao().getIdiomById(1)!!.isBookmarked)
    }
    @Test fun missedQuizOnlyUsesActualMissFlagsAndDoesNotChangeMastery() = runBlocking {
        idiom(1, learned = 1); idiom(3, name = "spill the beans", learned = 1000); word(2, learned = 1); word(4, name = "bold", learned = 1000, mastered = 1)
        val words = app.vocabRepository.getQuizWords(true)
        assertEquals(listOf(1), words.first.map { it.id }); assertEquals(listOf(2), words.second.map { it.id })
        val r = say("review words i missed"); assertTrue(r.formattedText.contains("break the ice")); assertFalse(r.formattedText.contains("spill the beans"))
        assertEquals("/vocab_quiz missed", say("practice missed words").cliCommandRedirect)
        assertEquals(0, vocab.vocabDao().getIdiomById(1)!!.isMastered)
    }
    @Test fun unknownWordIsNeverInvented() = runBlocking { assertTrue(say("explain word nonexistentword").formattedText.contains("won't invent")) }
    @Test fun bookmarkConfirmationRejectsExternalBookmarkChange() = runBlocking {
        idiom(); val p = say("bookmark idiom break the ice"); vocab.vocabDao().setIdiomBookmarked(1,1)
        assertTrue(say(confirm(p)).formattedText.contains("changed")); assertEquals(1, vocab.vocabDao().getIdiomById(1)!!.isBookmarked)
    }
    @Test fun coachingUsesSavedMistakesAndDoesNotInventTimes() = runBlocking {
        savedTest("first"); val r = say("review my mistakes").formattedText
        assertTrue(r.contains("2 + 3")); assertTrue(r.contains("Your answer: 4")); assertTrue(r.contains("Saved correct answer: 5"))
        assertTrue(say("where am i slow").formattedText.contains("not measured"))
    }
    @Test fun testComparisonDoesNotMixPracticeKindsOrUnknownDifficulty() = runBlocking {
        val now = System.currentTimeMillis(); savedTest("old", at = now-3000, difficulty = "medium"); savedTest("blitz",kind = "blitz",at = now-2000); savedTest("new", at = now-1000)
        val r = say("compare my last two tests").formattedText; assertTrue(r.contains("Previous arithmetic drill")); assertTrue(r.contains("doesn't establish a speed improvement"))
    }
    @Test fun malformedHistoryIsNotUsedForCoaching() = runBlocking {
        drill.drillSessionDao().insertSession(DrillSessionEntity("bad", "Bad", 2, 1, 30, 0, false, summaryJson = "{}"))
        assertTrue(say("review my mistakes").formattedText.contains("No matching valid"))
    }
    @Test fun legacyFabricatedQuestionMeasurementsAreUnknown() {
        val obj = JSONObject().put("timeTakenSec", 24).put("accuracyPct", 85).put("status", "wrong")
        val q = DrillQuestionResult.fromJson(obj); assertEquals(-1, q.timeTakenSec); assertEquals(-1, q.accuracyPct); assertEquals("", q.explanation)
        obj.put("measurementVersion", 2); assertEquals(24, DrillQuestionResult.fromJson(obj).timeTakenSec)
    }
    @Test fun transcriptPreviewIsEditableAndOldTokenCannotRun() = runBlocking {
        OfflineAssistant.recordTranscript(context,"remind me to call Mum tomorrow; buy milk",System.currentTimeMillis())
        val p = say("make tasks from what I just said"); val old = confirm(p); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        val revised = say("drop transcript item 2"); say(old); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        val saved = say(confirm(revised)); assertEquals(1, db.taskDao().getAllActiveTasksList().size)
        say(undo(saved)); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }
    @Test fun transcriptEditReplacesExactlyOneItem() = runBlocking {
        OfflineAssistant.recordTranscript(context,"buy milk; call Mum",System.currentTimeMillis()); say("preview my transcript")
        val p = say("change transcript item 2 to remind me to call Dad tomorrow"); assertTrue(p.formattedText.contains("Dad")); say(confirm(p))
        assertEquals(2, db.taskDao().getAllActiveTasksList().size); assertTrue(db.taskDao().getAllActiveTasksList().any { it.title.contains("Dad") })
    }
    @Test fun unsupportedTranscriptPartRejectsEntireBatch() = runBlocking {
        OfflineAssistant.recordTranscript(context,"buy milk; delete all tasks",System.currentTimeMillis()); val r = say("preview my transcript")
        assertTrue(r.formattedText.contains("couldn't safely")); assertNull(state().confirmationId); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }
    @Test fun staleOrClearedTranscriptCannotCreateTasks() = runBlocking {
        OfflineAssistant.recordTranscript(context,"buy milk",System.currentTimeMillis()-900001); assertTrue(say("preview my transcript").formattedText.contains("fresh"))
        OfflineAssistant.recordTranscript(context,"buy milk",System.currentTimeMillis()); say("forget me"); assertTrue(say("preview my transcript").formattedText.contains("fresh"))
    }
    @Test fun duplicateTranscriptNeverAddsAnotherTask() = runBlocking {
        db.taskDao().insertTask(Task(title = "buy milk")); OfflineAssistant.recordTranscript(context,"buy milk",System.currentTimeMillis())
        assertTrue(say("preview my transcript").formattedText.contains("duplicates")); assertEquals(1,db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun transcriptDoesNotAbsorbPartialOrCancelledCapture() = runBlocking {
        OfflineAssistant.recordTranscript(context,"buy milk",System.currentTimeMillis()); OfflineAssistant.recordTranscript(context,"",System.currentTimeMillis())
        assertTrue(say("preview my transcript").formattedText.contains("fresh"))
    }
    @Test fun backupReceiptsAreHonestAboutTheirLimits() = runBlocking {
        assertTrue(say("backup status").formattedText.contains("no verified export"))
        context.getSharedPreferences("ayva_backup_verification",0).edit().putLong("last_verified_export_ms",System.currentTimeMillis()-1000).commit()
        val r = say("backup status").formattedText; assertTrue(r.contains("last verified")); assertTrue(r.contains("does not prove"))
    }
    @Test fun rewardReportHasNoClaimOrSpendSideEffects() = runBlocking {
        val prefs = context.getSharedPreferences("daily_learning_quests_prefs",0)
        val date = java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(java.util.Date())
        prefs.edit().putString("quest_date",date).putInt("quest_drills_completed",1).putString("quest_early_bird_earned_date",date).commit()
        val before = FocusEconomyManager.profileFlow.value
        val r = say("what can i claim").formattedText; assertTrue(r.contains("ready to claim")); assertTrue(r.contains("pending") || r.contains("Pending"))
        assertEquals(before,FocusEconomyManager.profileFlow.value); assertFalse(prefs.getBoolean("quest_reward_claimed",false))
    }
    @Test fun featureCommandsInCompoundDoNotBecomeTasks() = runBlocking {
        say("buy milk; check device health"); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty()); assertNull(state().confirmationId)
    }
    @Test fun nativeCardIsConsumedOnceAndCannotReturnAfterForget() = runBlocking {
        idiom(); val json = JSONObject().put("idiom", JSONObject().put("id",1)).toString(); val now = System.currentTimeMillis()
        OfflineAssistant.recordVocabularyCard(context,json,now,"brief-1")
        say("forget me"); OfflineAssistant.recordVocabularyCard(context,json,now,"brief-1")
        assertNull(state().featureState); assertTrue(say("bookmark that idiom").formattedText.contains("can't identify"))
    }
    @Test fun oldNativeCardDoesNotReplaceNewerLearningTopic() = runBlocking {
        idiom(); val now = System.currentTimeMillis()
        val summary = JSONObject().put("sessionId","test-new").put("difficulty","easy").put("targetQuestions",10).put("total",2).put("correct",1)
        OfflineAssistant.recordLearningResult(context,summary.toString(),now)
        OfflineAssistant.recordVocabularyCard(context,JSONObject().put("idiom",JSONObject().put("id",1)).toString(),now-1000,"old-brief")
        assertEquals("learning",state().topic)
    }
    @Test fun corruptOptionFilteringPreservesCorrectAnswerIdentity() {
        val q = DrillQuestionResult.fromJson(JSONObject().put("options",JSONArray().put(JSONObject.NULL).put("4").put("5")).put("correctIndex",2).put("userSelectedIndex",1).put("status","wrong"))
        assertEquals("5",q.options[q.correctIndex]); assertEquals("4",q.options[q.userSelectedIndex])
    }
    @Test fun usageEventsAreClippedToMidnightAndCountForegroundOnly() {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        shadowOf(ops).setMode(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),context.packageName,android.app.AppOpsManager.MODE_ALLOWED)
        val manager = shadowOf(context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager)
        val start = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY,0); set(java.util.Calendar.MINUTE,0); set(java.util.Calendar.SECOND,0); set(java.util.Calendar.MILLISECOND,0) }.timeInMillis
        manager.addEvent(pkg,start-1000,android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND)
        manager.addEvent(pkg,start+3000,android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND)
        manager.addEvent("com.android.systemui",start+4000,android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND)
        manager.addEvent("com.android.systemui",start+5000,android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND)
        val report = AssistantUsage.day(context,0)
        assertTrue(report.available); assertEquals(3000L, report.durations[pkg]); assertFalse(report.durations.containsKey("com.android.systemui"))
    }
    @Test fun grantedUsageAccessWithNoEventsStillReportsUnknown() {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        shadowOf(ops).setMode(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),context.packageName,android.app.AppOpsManager.MODE_ALLOWED)
        assertFalse(AssistantUsage.day(context,0).available)
    }
    @Test fun featureCommandInsideTranscriptCannotBecomeTask() = runBlocking {
        OfflineAssistant.recordTranscript(context,"buy milk; check device health",System.currentTimeMillis())
        assertTrue(say("preview my transcript").formattedText.contains("couldn't safely")); assertNull(state().confirmationId)
    }

    @Test fun oversizedNewTranscriptClearsPreviousExtractionContext() = runBlocking {
        OfflineAssistant.recordTranscript(context,"buy milk",System.currentTimeMillis())
        OfflineAssistant.recordTranscript(context,"a".repeat(4001),System.currentTimeMillis())
        assertTrue(say("preview my transcript").formattedText.contains("fresh")); assertNull(state().confirmationId)
    }

    @Test fun usageDoesNotCountPoweredOffTimeAcrossAReboot() {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        shadowOf(ops).setMode(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),context.packageName,android.app.AppOpsManager.MODE_ALLOWED)
        val manager = shadowOf(context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager)
        val start = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY,0); set(java.util.Calendar.MINUTE,0); set(java.util.Calendar.SECOND,0); set(java.util.Calendar.MILLISECOND,0) }.timeInMillis
        manager.addEvent(pkg,start-1000,android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND)
        manager.addEvent("android",start+2000,android.app.usage.UsageEvents.Event.DEVICE_SHUTDOWN)
        manager.addEvent("android",start+5000,android.app.usage.UsageEvents.Event.DEVICE_STARTUP)
        manager.addEvent(pkg,start+6000,android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND)
        manager.addEvent(pkg,start+8000,android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND)
        assertEquals(4000L,AssistantUsage.day(context,0).durations[pkg])
    }
    @Test fun rebootWithoutShutdownDiscardsAnUnreliableOpenSession() {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        shadowOf(ops).setMode(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),context.packageName,android.app.AppOpsManager.MODE_ALLOWED)
        val manager = shadowOf(context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager)
        val start = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY,0); set(java.util.Calendar.MINUTE,0); set(java.util.Calendar.SECOND,0); set(java.util.Calendar.MILLISECOND,0) }.timeInMillis
        manager.addEvent(pkg,start-1000,android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND)
        manager.addEvent("android",start+5000,android.app.usage.UsageEvents.Event.DEVICE_STARTUP)
        manager.addEvent(pkg,start+6000,android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND)
        manager.addEvent(pkg,start+8000,android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND)
        assertEquals(2000L,AssistantUsage.day(context,0).durations[pkg])
    }

}
