package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.Subtask
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.note.NoteDao
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.assistant.AssistantSessionStore
import com.focusbyrj.app.util.backup.DataSafetyManager
import com.focusbyrj.app.util.backup.EncryptedSnapshotFile
import com.focusbyrj.app.util.backup.SnapshotCryptoEngine
import com.focusbyrj.app.util.backup.TaskRecoveryStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AssistantNotesIsolationTest {
    private lateinit var context: Context
    private lateinit var database: FocusDatabase
    private lateinit var notes: NoteDatabase
    private val noteQueries = CopyOnWriteArrayList<String>()
    @Volatile private var monitor = false
    private var previousNotes: Any? = null
    private val instanceField get() = NoteDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private val sentinel = "PRIVATE_NOTE_SENTINEL_7429"

    @Before fun setUp() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val app = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, base) }
        database = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(database)) }
        context = object : ContextWrapper(base) { override fun getApplicationContext(): Context = app }
        notes = Room.inMemoryDatabaseBuilder(base, NoteDatabase::class.java).allowMainThreadQueries()
            .setQueryCallback(object : RoomDatabase.QueryCallback {
                override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                    // Room may finish setup transaction bookkeeping asynchronously.
                    // Record every statement that actually touches the private table.
                    if (monitor && sqlQuery.contains("keep_notes", ignoreCase = true)) noteQueries.add(sqlQuery)
                }
            }, Executor { it.run() }).build()
        notes.noteDao().insertNote(NoteEntity(title = sentinel, content = "$sentinel body", isArchived = true))
        previousNotes = instanceField.get(null)
        instanceField.set(null, notes)
        File(context.filesDir, "auto_backups").deleteRecursively()
        AssistantSessionStore(context).clear()
        AyvaTalkEngine.resetConversation()
        FocusEconomyManager.init(context)
        monitor = true
    }

    @After fun tearDown() {
        monitor = false
        instanceField.set(null, previousNotes)
        notes.close()
        database.close()
        AssistantSessionStore(context).clear()
        AyvaTalkEngine.resetConversation()
    }

    private suspend fun say(text: String) = AyvaTalkEngine.answerTalkQueryWithActions(text, context).also {
        assertFalse(it.formattedText.contains(sentinel))
    }
    private suspend fun trashAll() {
        val preview = say("delete all tasks")
        val confirm = preview.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva confirm ") }
        assertTrue(say(confirm.query).formattedText.contains("Trash"))
    }
    private fun recoveryFiles() = File(context.filesDir, TaskRecoveryStore.DIRECTORY).listFiles { f -> f.name.endsWith(".json") }.orEmpty()
    private fun assertNoNoteAccess() = assertTrue("Chat touched Notes: $noteQueries", noteQueries.isEmpty())

    @Test fun trashAndUndoUseOnlySelectedTaskData() = runBlocking {
        val id = database.taskDao().insertTask(Task(title = "Milk", details = "Oat", subtasksJson = Subtask.listToJson(listOf(Subtask(title = "Check price")))))
        trashAll()
        val snapshot = recoveryFiles().single()
        assertTrue(SnapshotCryptoEngine.isEncrypted(snapshot))
        val payload = EncryptedSnapshotFile.read(snapshot)
        val root = JSONObject(payload)
        assertEquals(TaskRecoveryStore.SCOPE, root.getString("scope"))
        assertFalse(root.has("notes")); assertFalse(root.has("noteCount")); assertFalse(root.has("vaultStatus"))
        assertFalse(payload.contains(sentinel))
        assertEquals(1, root.getJSONArray("tasks").length())
        say("undo")
        assertFalse(database.taskDao().getTaskById(id)!!.isTrashed)
        assertNoNoteAccess()
    }

    @Test fun privacyHelpStatusAndCrossTopicRepliesDoNotQueryNotes() = runBlocking {
        database.taskDao().insertTask(Task(title = "Read"))
        for (text in listOf("read my notes", "search my notes", "/talk notes vault pin", "backup status", "check device health", "show my tasks", "where were we", "repeat that")) say(text)
        assertNoNoteAccess()
        assertTrue(recoveryFiles().isEmpty())
    }

    @Test fun taskRecoveryIsDiscoverableAndNativeRestoreNeverCallsNotesDao() = runBlocking {
        val id = database.taskDao().insertTask(Task(title = "Read", details = "Chapter two"))
        trashAll()
        val snapshot = recoveryFiles().single()
        val info = DataSafetyManager.listAvailableSnapshots(context).single()
        assertEquals("Task recovery", info.tag)
        assertEquals(-1, info.noteCount)
        val forbiddenDao = Proxy.newProxyInstance(NoteDao::class.java.classLoader, arrayOf(NoteDao::class.java)) { _, method, _ ->
            error("Unexpected Notes DAO call: ${method.name}")
        } as NoteDao
        val result = DataSafetyManager.restoreSnapshot(context, snapshot.absolutePath, forbiddenDao, database)
        assertTrue(result.exceptionOrNull()?.message, result.isSuccess)
        assertEquals(1, result.getOrThrow())
        val restored = database.taskDao().getTaskById(id)!!
        assertFalse(restored.isTrashed)
        assertEquals("Chapter two", restored.details)
        assertNoNoteAccess()
    }

    @Test fun recoveryFailurePreventsEveryTaskMutation() = runBlocking {
        val id = database.taskDao().insertTask(Task(title = "Keep"))
        File(context.filesDir, "auto_backups").writeText("not a directory")
        val preview = say("delete all tasks")
        val confirm = preview.actions.filterIsInstance<TalkAction.AskQuery>().first { it.query.startsWith("/ayva confirm ") }
        assertTrue(say(confirm.query).formattedText.contains("have not been moved"))
        assertFalse(database.taskDao().getTaskById(id)!!.isTrashed)
        assertNoNoteAccess()
    }

    @Test fun recoveryRejectsWholeGroupWhenOneTaskChanged() = runBlocking {
        val first = database.taskDao().insertTask(Task(title = "First"))
        val second = database.taskDao().insertTask(Task(title = "Second"))
        trashAll()
        val edited = database.taskDao().getTaskById(second)!!.copy(title = "Edited elsewhere", updatedAt = System.currentTimeMillis() + 1)
        database.taskDao().updateTask(edited)
        assertTrue(TaskRecoveryStore.restore(context, recoveryFiles().single().absolutePath, database).isFailure)
        assertTrue(database.taskDao().getTaskById(first)!!.isTrashed)
        assertEquals(edited, database.taskDao().getTaskById(second))
        assertNoNoteAccess()
    }

    @Test fun corruptRecoveryOrForeignPathCannotChangeTasks() = runBlocking {
        val id = database.taskDao().insertTask(Task(title = "Keep"))
        trashAll()
        val snapshot = recoveryFiles().single()
        snapshot.writeText("{\"scope\":\"selected_tasks_v1\"}")
        assertTrue(TaskRecoveryStore.restore(context, snapshot.absolutePath, database).isFailure)
        val foreign = File(context.filesDir, "foreign.json").apply { writeText("{}") }
        assertTrue(TaskRecoveryStore.restore(context, foreign.absolutePath, database).isFailure)
        assertTrue(database.taskDao().getTaskById(id)!!.isTrashed)
        assertNoNoteAccess()
    }

    @Test fun repeatedRecoveryRefusesToOverwriteTheRestoredTask() = runBlocking {
        val id = database.taskDao().insertTask(Task(title = "Keep"))
        trashAll()
        val snapshot = recoveryFiles().single()
        assertTrue(TaskRecoveryStore.restore(context, snapshot.absolutePath, database).isSuccess)
        val restored = database.taskDao().getTaskById(id)
        assertTrue(TaskRecoveryStore.restore(context, snapshot.absolutePath, database).isFailure)
        assertEquals(restored, database.taskDao().getTaskById(id))
        assertNoNoteAccess()
    }

    @Test fun independentAllDataBackupStillIncludesNativeNotes() = runBlocking {
        monitor = false // This is an explicit native backup operation, outside chat.
        assertTrue(DataSafetyManager.writeDailyBackup(context, notes.noteDao(), database))
        val snapshot = DataSafetyManager.listAvailableSnapshots(context).single()
        assertEquals(1, snapshot.noteCount)
        assertTrue(EncryptedSnapshotFile.read(File(snapshot.absolutePath)).contains(sentinel))
    }

    @Test @Config(sdk = [34])
    fun chatSpeechHasNoNotesOrSavedMediaCapability() {
        org.robolectric.shadows.ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        val transcriber = com.focusbyrj.app.util.speech.OnDeviceTranscriber(context)
        try {
            transcriber.startRecording()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val recognizer = org.robolectric.shadows.ShadowSpeechRecognizer.getLatestSpeechRecognizer()
            val shadow = org.robolectric.Shadows.shadowOf(recognizer)
            assertTrue(shadow.lastRecognizerIntent.getBooleanExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
            shadow.triggerOnPartialResults(android.os.Bundle().apply {
                putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("buy milk"))
            })
            assertEquals("", transcriber.finalizedTranscript())
            assertEquals("buy milk", transcriber.recordingState.value.liveTranscript)
            assertTrue(recoveryFiles().isEmpty())
            assertNoNoteAccess()
        } finally { transcriber.release() }
    }

    @Test fun oldMediaEncryptedSessionExpiresWithoutRestoringConfirmationAuthority() {
        val file = File(context.filesDir, "ayva_conversation_state.enc")
        com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(file,
            com.focusbyrj.app.util.assistant.AssistantSession(expected = "confirm", confirmationId = "old-token").toJson().toString().toByteArray())
        val restored = AssistantSessionStore(context).load()
        assertNull(restored.confirmationId)
        assertNull(restored.expected)
        assertFalse(file.exists())
        assertNoNoteAccess()
    }
}
