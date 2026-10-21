package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskPlanning
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
class AssistantOfflineExpansionTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    private lateinit var vocab: com.focusbyrj.app.data.VocabDatabase
    @Before fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        vocab = Room.inMemoryDatabaseBuilder(base, com.focusbyrj.app.data.VocabDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("vocabDatabase\$delegate").apply { isAccessible = true; set(app, lazyOf(vocab)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        AssistantSessionStore(context).clear()
        for (name in listOf("focus_prefs", "bubble_prefs", "ayva_action_receipts", "ayva_personalization")) context.getSharedPreferences(name, 0).edit().clear().commit()
        FocusEconomyManager.init(context)
        AyvaTalkEngine.resetConversation()
    }
    @After fun tearDown() { AssistantSessionStore(context).clear(); db.close(); vocab.close(); AyvaTalkEngine.resetConversation() }
    private suspend fun say(text: String) = OfflineAssistant.respond(text, context) ?: error("Not handled: $text")
    private fun state() = AssistantSessionStore(context).load()
    private suspend fun add(title: String, priority: Boolean = false) = db.taskDao().insertTask(Task(title = title, isPriority = priority))
    private suspend fun task(id: Long) = db.taskDao().getTaskById(id)!!
    private suspend fun select(id: Long) { say("let's work on id:$id") }
    private fun confirm(response: AyvaTalkEngine.TalkResponse) = response.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva confirm ") }.query

    @Test fun dictionarySensesPlainEnglishAndExamplesRemainSourceTied() = runBlocking {
        val definition = say("what does reluctant mean?")
        assertTrue(definition.formattedText, definition.formattedText.contains("not eager"))
        assertTrue(say("simpler").formattedText.contains("Not eager"))
        val first = say("give an example").formattedText
        val second = say("another example").formattedText
        assertNotEquals(first, second)
        assertTrue(say("other meaning").formattedText.contains("disinclined"))
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        assertTrue(AssistantDictionaryStore.relatedForms(context, "jogging").contains("jog"))
        assertEquals(3, AssistantDictionaryStore.lookup(context, "reluctant").size)
    }
    @Test fun dictionaryBookmarkUsesNativeStoreAndQuizAndDoesNotClaimLearning() = runBlocking {
        say("define reluctant")
        val preview = say("save this word"); assertTrue(vocab.vocabDao().getAllOws().isEmpty())
        val token = confirm(preview); say(token); say(token)
        val saved = vocab.vocabDao().getAllOws().single()
        assertEquals(1, saved.isBookmarked); assertNull(saved.learnedAt)
        assertEquals(saved.id, vocab.dictionaryBookmarkDao().get("8a983f858b")!!.owsId)
        val launch = say("quiz me on it"); assertEquals("/vocab_quiz saved ows:${saved.id}", launch.cliCommandRedirect)
        val words = com.focusbyrj.app.data.VocabRepository(vocab.vocabDao()).getQuizWords(bookmarksOnly = true, bookmarkId = saved.id)
        assertEquals(listOf(saved.id), words.second.map { it.id })
        say("undo"); assertEquals(0, vocab.vocabDao().getOwsById(saved.id!!)!!.isBookmarked)
    }
    @Test fun staleDictionaryButtonsCannotSaveDifferentWordAndUnknownsCannotCreateTasks() = runBlocking {
        val first = say("define reluctant").actions.filterIsInstance<TalkAction.AskQuery>().first { it.label == "Save word" }
        say("define witty"); say(first.query); assertNull(state().confirmationId)
        assertTrue(vocab.vocabDao().getAllOws().isEmpty())
        val unknown = say("what does zzznonwordxyz mean")
        assertTrue(unknown.formattedText.contains("don't have an entry"))
        assertTrue(AyvaIntentRouter.route("define zzznonwordxyz") is RouterDestination.TalkQuery)
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }
    @Test fun meaningsOfCommandsAreInertAndCancelOldActionAuthority() = runBlocking {
        val id = add("Keep me"); val old = confirm(say("delete all tasks"))
        say("what does delete all tasks mean?"); say("yes"); say(old)
        assertFalse(task(id).isTrashed)
        val definition = say("define delete").formattedText
        assertTrue(definition.contains("delete", true))
        assertFalse(definition.contains("Wordset")); assertFalse(definition.contains("WordNet")); assertFalse(definition.contains("dictionary credits"))
        assertFalse(task(id).isTrashed)
    }
    @Test fun sentencePatternsPreserveNegationAndUnsupportedConstructionsStayUnknown() = runBlocking {
        val positive = say("explain sentence I would have gone had I known").formattedText
        val negative = say("explain sentence I would not have gone had I known").formattedText
        assertTrue(positive.contains("If I had known, I would have gone")); assertTrue(negative.contains("would not have gone"))
        assertTrue(say("explain sentence I am not feeling under the weather").formattedText.contains("do not feel unwell"))
        assertTrue(say("explain sentence I used to run").formattedText.contains("past habit"))
        assertTrue(say("explain sentence I am used to running").formattedText.contains("familiar"))
        assertTrue(say("explain sentence Oh great, another delay").formattedText.contains("can't reliably"))
    }
    @Test fun conversationInterruptionsJokesAndCasualYesNeverAuthorizeOldDeletion() = runBlocking {
        val id = add("Keep me"); val old = confirm(say("delete all tasks"))
        val first = say("tell me a joke").formattedText
        assertNotEquals(first, say("another one").formattedText)
        say("yes"); say(old); assertFalse(task(id).isTrashed)
        say("I finally finished it"); assertFalse(task(id).isCompleted)
    }
    @Test fun empathyListeningAndGamesDoNotCreateTasksOrSaveEmotionalProfiles() = runBlocking {
        val before = context.getSharedPreferences("ayva_personalization", 0).all.toMap()
        val sympathy = say("I had a rough day").formattedText
        assertTrue(sympathy.contains("sounds like")); assertNull(state().planning)
        say("just listen")
        assertTrue(say("I feel exhausted").formattedText.contains("I feel exhausted"))
        assertTrue(AssistantDiscussion.hasContext(state()))
        say("just distract me"); assertTrue(say("piano").formattedText.contains("got it"))
        say("another one"); say("stop")
        assertEquals(before, context.getSharedPreferences("ayva_personalization", 0).all)
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }
    @Test fun storyChoicesAndStaleButtonsStayInsideTheirCurrentConversation() = runBlocking {
        val story = say("tell me a story")
        val old = story.actions.filterIsInstance<TalkAction.AskQuery>().first()
        say("knock"); assertTrue(say("return the book").formattedText.contains("The end"))
        say("tell me a joke"); val text = say(old.query).formattedText
        assertTrue(text.contains("expired")); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
    }
    @Test fun explicitToneQuietAndSuggestionPreferencesAreInspectableAndForgettable() = runBlocking {
        say("be serious"); assertFalse(context.getSharedPreferences("ayva_personalization", 0).contains("tone"))
        say("remember that I prefer a direct tone"); assertEquals("direct", AssistantPersonalization.tone(context))
        say("remember quiet conversation"); assertTrue(AssistantPersonalization.quiet(context))
        assertFalse(say("hello").formattedText.contains("?"))
        say("remember task suggestions"); assertFalse(AssistantPersonalization.suggestions(context))
        assertTrue(say("what do you remember about me").formattedText.contains("direct"))
        say("forget my preferences"); assertFalse(AssistantPersonalization.quiet(context)); assertEquals("warm", AssistantPersonalization.tone(context))
    }
    @Test fun suggestionsRequireOptInAndDismissalAppliesCooldownWithoutWrites() = runBlocking {
        val id = add("Priority report", true)
        assertFalse(say("hello").formattedText.contains("Optional:"))
        say("remember task suggestions")
        val suggestion = say("hello"); assertTrue(suggestion.formattedText.contains("Optional:"))
        say("not now"); assertFalse(say("hello").formattedText.contains("Optional:"))
        assertFalse(task(id).isCompleted); assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }
    @Test fun composableTaskViewsKeepDisplayedIdsAndFilters() = runBlocking {
        val a = add("Read history", true); add("Call Dad")
        val now = System.currentTimeMillis()
        val dueOnly = db.taskDao().insertTask(Task(title = "Read fiction", isPriority = true, dueDate = now + 86_400_000))
        val scheduled = db.taskDao().insertTask(Task(title = "Read science", isPriority = true, dueDate = now + 86_400_000,
            planningJson = TaskPlanning(minutes = 20, start = now + 3_600_000).json()))
        say("show priority unscheduled tasks about read")
        assertEquals(listOf(dueOnly, a), state().taskListIds)
        say("complete the first one")
        assertTrue(task(dueOnly).isCompleted); assertFalse(task(a).isCompleted); assertFalse(task(scheduled).isCompleted)
        say("show priority undated tasks about read")
        assertEquals(listOf(a), state().taskListIds)
        say("complete the first one"); assertTrue(task(a).isCompleted)
    }
    @Test fun bundledVocabAssetMigratesAndKeepsOriginalRows() = runBlocking {
        val imported = Room.databaseBuilder(context, com.focusbyrj.app.data.VocabDatabase::class.java, "dictionary-migration-test")
            .createFromAsset("vocab.db").addMigrations(com.focusbyrj.app.data.VocabDatabase.MIGRATION_1_2, com.focusbyrj.app.data.VocabDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        try { assertEquals(1773, imported.vocabDao().getAllIdioms().size); assertEquals(2027, imported.vocabDao().getAllOws().size); assertNull(imported.dictionaryBookmarkDao().get("missing")) }
        finally { imported.close(); context.deleteDatabase("dictionary-migration-test") }
    }
    @Test fun jokeExplanationAndStoryRestartUseCurrentPackContext() = runBlocking {
        say("tell me a joke")
        assertTrue(say("why is that funny").formattedText.contains("calendar"))
        val first = say("tell me a story").formattedText
        say("knock"); say("return the book")
        assertNotEquals(first, say("tell me a story").formattedText)
    }

    @Test fun taskDatabaseMigrationKeepsTasksAndCreatesPlanJournal() = runBlocking {
        val name = "assistant-plan-migration"
        val old = Room.databaseBuilder(context, FocusDatabase::class.java, name).allowMainThreadQueries().build()
        val id = old.taskDao().insertTask(Task(title = "Keep through migration", subtasksJson = "[]"))
        old.openHelper.writableDatabase.apply {
            execSQL("DROP TABLE assistant_plan_receipts")
            execSQL("ALTER TABLE tasks RENAME TO tasks_newer")
            execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, details TEXT NOT NULL, dueDate INTEGER, isCompleted INTEGER NOT NULL, type TEXT NOT NULL, recurrence TEXT NOT NULL, isPersistent INTEGER NOT NULL, isPriority INTEGER NOT NULL, completedAt INTEGER, updatedAt INTEGER NOT NULL, isTrashed INTEGER NOT NULL, trashedAt INTEGER, deletedAt INTEGER, subtasksJson TEXT NOT NULL)")
            execSQL("INSERT INTO tasks SELECT id,title,details,dueDate,isCompleted,type,recurrence,isPersistent,isPriority,completedAt,updatedAt,isTrashed,trashedAt,deletedAt,subtasksJson FROM tasks_newer")
            execSQL("DROP TABLE tasks_newer")
            execSQL("UPDATE room_master_table SET identity_hash = '26cbbdd592e512fbe7c330ba6a8f8063' WHERE id = 42")
            version = 12
        }
        old.close()
        val migrated = Room.databaseBuilder(context, FocusDatabase::class.java, name).addMigrations(FocusDatabase.MIGRATION_12_13, FocusDatabase.MIGRATION_13_14).allowMainThreadQueries().build()
        try {
            assertEquals("Keep through migration", migrated.taskDao().getTaskById(id)!!.title)
            assertTrue(migrated.assistantPlanReceiptDao().pending().isEmpty())
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun hobbyConversationKeepsItsTopicAndExplicitTaskInterruptsIt() = runBlocking {
        assertTrue(say("I like reading").formattedText.contains("Books"))
        assertTrue(say("fantasy").formattedText.contains("world"))
        assertTrue(say("tell me more").formattedText.contains("world"))
        assertEquals("reading", state().featureState!!.getJSONObject("social").getString("interest"))
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        say("buy milk tomorrow")
        assertEquals(1, db.taskDao().getAllActiveTasksList().size)
        assertFalse(context.getSharedPreferences("ayva_personalization", 0).contains("interest"))
    }

    @Test fun overdueAndPriorityFiltersComposeWithoutIncludingFutureOrNormalTasks() = runBlocking {
        val chosen = db.taskDao().insertTask(Task(title = "Overdue priority", isPriority = true, dueDate = System.currentTimeMillis() - 60_000))
        db.taskDao().insertTask(Task(title = "Overdue normal", dueDate = System.currentTimeMillis() - 60_000))
        db.taskDao().insertTask(Task(title = "Future priority", isPriority = true, dueDate = System.currentTimeMillis() + 86_400_000))
        say("show overdue priority tasks")
        assertEquals(listOf(chosen), state().taskListIds)
    }
    @Test fun qualifiedYesAndClockOnlyRevisionKeepPreviewDates() = runBlocking {
        add("One"); add("Two")
        val first = say("reschedule all tasks to Friday at 9am")
        val before = state().pending.filterIsInstance<AssistantAction.EditTasks>().map { it.value.toLong() }
        assertTrue(say("yes but at eight").formattedText.contains("AM or PM"))
        say(confirm(first)); assertTrue(db.taskDao().getAllActiveTasksList().all { it.dueDate == null })
        val revised = say("at 8pm")
        val after = state().pending.filterIsInstance<AssistantAction.EditTasks>().map { it.value.toLong() }
        assertEquals(before.size, after.size)
        for (i in before.indices) {
            val a = java.util.Calendar.getInstance().apply { timeInMillis = before[i] }
            val b = java.util.Calendar.getInstance().apply { timeInMillis = after[i] }
            assertEquals(a.get(java.util.Calendar.DAY_OF_YEAR), b.get(java.util.Calendar.DAY_OF_YEAR))
            assertEquals(20, b.get(java.util.Calendar.HOUR_OF_DAY))
        }
        say(confirm(revised)); assertTrue(db.taskDao().getAllActiveTasksList().all { it.dueDate != null })
    }

    @Test fun timeOnlySavedTaskCorrectionKeepsItsExistingDay() = runBlocking {
        val date = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 5); set(java.util.Calendar.HOUR_OF_DAY, 9) }.timeInMillis
        val id = db.taskDao().insertTask(Task(title = "Future appointment", dueDate = date))
        select(id); say("at 8pm")
        val before = java.util.Calendar.getInstance().apply { timeInMillis = date }
        val after = java.util.Calendar.getInstance().apply { timeInMillis = task(id).dueDate!! }
        assertEquals(before.get(java.util.Calendar.DAY_OF_YEAR), after.get(java.util.Calendar.DAY_OF_YEAR))
        assertEquals(20, after.get(java.util.Calendar.HOUR_OF_DAY))
    }

    @Test fun explicitTalkAndFeatureQuestionsInterruptGamesAndHobbyBranches() = runBlocking {
        say("give me a riddle")
        assertTrue(say("/talk help").formattedText.contains("settings"))
        say("give me a riddle")
        assertTrue(say("/talk settings").formattedText.contains("groups"))
        say("I like reading")
        assertTrue(say("what is my reminder interval").formattedText.contains("Currently"))
        say("/talk buy milk"); assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        assertTrue(say("/talk tell me a joke").formattedText.contains("calendar"))
    }

    @Test fun rejectedHistoricalReplyCannotLeaveAnotherItemsConfirmationLive() = runBlocking {
        val keep = add("Keep me"); val gone = add("Gone")
        val old = confirm(say("delete all tasks"))
        db.taskDao().updateTask(task(gone).copy(isTrashed = true))
        OfflineAssistant.respond("yes", context, replyTarget = AssistantItemReference("task", gone, "Gone"), quotedReply = "An old reply")
        say("yes"); say(old); assertFalse(task(keep).isTrashed)
        assertNull(state().taskId); assertNull(state().confirmationId)
    }

    @Test fun ordinaryWantLikeAndEnjoyStatementsStayConversationRatherThanQuickCapture() = runBlocking {
        for (text in listOf("I want to watch sunsets", "I like wandering", "I enjoy sketching")) {
            assertTrue(AyvaIntentRouter.route(text) is RouterDestination.TalkQuery)
            AyvaTalkEngine.answerTalkQueryWithActions(text, context)
        }
        assertTrue(db.taskDao().getAllActiveTasksList().isEmpty())
        say("buy milk tomorrow"); assertEquals(1, db.taskDao().getAllActiveTasksList().size)
    }

}
