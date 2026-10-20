package com.focusbyrj.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.util.assistant.*
import com.focusbyrj.app.util.backup.BackupRestoreManager
import com.focusbyrj.app.util.backup.CryptoBackupEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlanningArchiveIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: FocusDatabase
    private var failModeCommit = false
    private val password = "ArchiveRegression2026!"

    @Before fun setUp() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Application>()
        lateinit var attachedApp: FocusApplication
        val wrapped = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = attachedApp
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val prefs = super.getSharedPreferences(name, mode)
                if (name != "ayva_composer") return prefs
                return object : SharedPreferences by prefs {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = prefs.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                                editor.putString(key, value); return this
                            }
                            override fun commit(): Boolean {
                                if (failModeCommit) { failModeCommit = false; return false }
                                return editor.commit()
                            }
                        }
                    }
                }
            }
        }
        val app = FocusApplication()
        attachedApp = app
        Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true; invoke(app, wrapped) }
        db = Room.inMemoryDatabaseBuilder(base, FocusDatabase::class.java).allowMainThreadQueries().build()
        app.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(app, lazyOf(db)) }
        context = object : ContextWrapper(wrapped) { override fun getApplicationContext(): Context = app }
        ConversationPlanStore.clearAll(context); AssistantSessionStore(context).clear()
        context.getSharedPreferences("ayva_composer", 0).edit().clear().commit()
        ArchiveVaultSecurity.disablePasscode(context)
        NoteDatabase.getInstance(app).noteDao().deleteAllNotes()
    }

    @After fun tearDown() {
        failModeCommit = false
        ConversationPlanStore.clearAll(context); AssistantSessionStore(context).clear()
        ChatModeStore.set(context, ChatMode.CHAT); db.close()
    }

    private fun current() = ConversationPlanStore(context).active()!!
    private fun document(plan: PlanWorkspace) = JSONObject().put("version", 1).put("active", plan.id)
        .put("plans", JSONArray().put(plan.json()))
    private fun seed(plan: PlanWorkspace) { ConversationPlanStore(context).put(plan) }
    private fun root(payload: JSONObject? = null, tasks: List<Task> = emptyList()) = JSONObject().put("version", 1)
        .put("notes", JSONArray()).put("tasks", JSONArray(tasks.map { task -> JSONObject()
            .put("id", task.id).put("title", task.title).put("details", task.details)
            .put("updatedAt", task.updatedAt).put("dueDate", task.dueDate ?: JSONObject.NULL).put("planningJson", task.planningJson) }))
        .apply { payload?.let { put("assistantPlanning", it) } }
    private fun archive(root: JSONObject): Uri {
        val file = File(context.cacheDir, "planning-import.focusbackup")
        CryptoBackupEngine.openEncryptingStream(file.outputStream(), password.toCharArray()).use { encrypted ->
            ZipOutputStream(encrypted).use { zip ->
                zip.putNextEntry(ZipEntry("data.json")); zip.write(root.toString().toByteArray()); zip.closeEntry()
            }
        }
        return Uri.fromFile(file)
    }
    private suspend fun restore(root: JSONObject, clean: Boolean = true) =
        BackupRestoreManager.restoreEncryptedBackup(context, archive(root), password, cleanRestore = clean)
    private suspend fun say(text: String) = OfflineAssistant.respond(text, context)!!.formattedText

    @Test fun encryptedBackupRoundTripPreservesGraphAndRequiresFreshReview() = runBlocking {
        val task = Task(id = 41, title = "Prepare bags", updatedAt = 1000)
        db.taskDao().insertTask(task)
        val parent = PlanStep(title = task.title, taskSnapshot = TaskJson.encode(task).toString())
        val child = PlanStep(title = "Pack charger", parentId = parent.id, minutes = 10, start = 1_900_000_000_000L)
        val plan = PlanWorkspace(goal = "Private trip goal", steps = listOf(parent, child), selected = child.id,
            facts = mapOf("destination" to "Goa"), reviewedRevision = 1, reviewedAt = System.currentTimeMillis(),
            reviewProcess = "old-review", savedUndoId = "old-undo", pending = JSONObject().put("kind", "date"))
        seed(plan)
        val file = File(context.cacheDir, "planning-roundtrip.focusbackup")
        val exported = BackupRestoreManager.createEncryptedBackup(context, Uri.fromFile(file), password)
        assertTrue(exported.exceptionOrNull()?.stackTraceToString(), exported.isSuccess)
        assertEquals(1, exported.getOrThrow().planCount)
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains(plan.goal))
        val payload = CryptoBackupEngine.openDecryptingStream(file.inputStream(), password.toCharArray()).use { encrypted ->
            ZipInputStream(encrypted).use { zip ->
                var json: JSONObject? = null
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "data.json") json = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                }
                json!!.getJSONObject("assistantPlanning")
            }
        }
        assertTrue(payload.getJSONArray("plans").getJSONObject(0).isNull("process"))
        val result = BackupRestoreManager.restoreEncryptedBackup(context, Uri.fromFile(file), password, cleanRestore = true)
        assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
        assertEquals(1, result.getOrThrow().planCount)
        val restored = current()
        assertNotEquals(plan.id, restored.id)
        assertEquals(child.parentId, restored.steps.last().parentId)
        assertEquals(child.start, restored.steps.last().start)
        assertEquals(task, TaskJson.decode(JSONObject(restored.steps.first().taskSnapshot!!)))
        assertEquals(task, db.taskDao().getTaskById(41))
        assertNull(restored.reviewedRevision); assertNull(restored.savedUndoId); assertNull(restored.pending)
        say("/plan apply revision ${plan.id} 1 old-review")
        assertEquals(1, db.taskDao().getAllTasksList().size)
        say("save plan"); say("apply plan")
        assertEquals(2, db.taskDao().getAllActiveTasksList().size)
    }

    @Test fun mergeRemapsTaskIdsAndKeepsLocalPlansAndTasks() = runBlocking {
        val local = Task(id = 7, title = "Local task", updatedAt = 100)
        db.taskDao().insertTask(local)
        val localPlan = PlanWorkspace(goal = "Local plan", steps = listOf(PlanStep(title = local.title,
            taskSnapshot = TaskJson.encode(local).toString())), reviewedRevision = 1, reviewProcess = "local-review")
        seed(localPlan)
        val remote = Task(id = 7, title = "Remote task", updatedAt = 200)
        val remotePlan = PlanWorkspace(goal = "Imported plan", steps = listOf(PlanStep(title = remote.title,
            taskSnapshot = TaskJson.encode(remote).toString())))
        val result = restore(root(document(remotePlan), listOf(remote)), clean = false)
        assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
        val imported = ConversationPlanStore(context).all().single { it.goal == remotePlan.goal }
        val linked = TaskJson.decode(JSONObject(imported.steps.single().taskSnapshot!!))
        assertNotEquals(7L, linked.id)
        assertEquals(remote.copy(id = linked.id), db.taskDao().getTaskById(linked.id))
        assertEquals(local, db.taskDao().getTaskById(7))
        assertEquals(localPlan.id, current().id)
        assertNull(current().reviewedRevision)
    }

    @Test fun restoreDoesNotSilentlyAdoptNativeTaskEdits() = runBlocking {
        val prior = Task(id = 5, title = "Expected title", updatedAt = 100)
        val plan = PlanWorkspace(goal = "Project", steps = listOf(PlanStep(title = prior.title, taskSnapshot = TaskJson.encode(prior).toString())))
        val native = prior.copy(title = "Native edit", updatedAt = 200)
        assertTrue(restore(root(document(plan), listOf(native))).isSuccess)
        say("/plan"); say("save plan")
        assertTrue(say("apply plan").contains("changed outside"))
        assertEquals(native, db.taskDao().getTaskById(5))
    }

    @Test fun missingLinkedTaskCanBeExplicitlyUnlinkedWithoutTouchingAnotherTask() = runBlocking {
        val missing = Task(id = 9, title = "Missing task", updatedAt = 100)
        val plan = PlanWorkspace(goal = "Recovered plan", steps = listOf(PlanStep(title = missing.title, taskSnapshot = TaskJson.encode(missing).toString())))
        assertTrue(restore(root(document(plan))).isSuccess)
        assertTrue(current().steps.single().taskLinkMissing)
        say("/plan"); say("save plan")
        assertTrue(say("apply plan").contains("link is missing"))
        assertTrue(db.taskDao().getAllTasksList().isEmpty())
        say("unlink Missing task"); say("save plan"); say("apply plan")
        assertEquals("Missing task", db.taskDao().getAllActiveTasksList().single().title)
    }

    @Test fun deletingMissingDraftStepDoesNotTrashACollidingTaskId() = runBlocking {
        val missing = Task(id = 9, title = "Missing", updatedAt = 100)
        val local = Task(id = 9, title = "Unrelated", updatedAt = 200)
        db.taskDao().insertTask(local)
        val plan = PlanWorkspace(goal = "Recovered", steps = listOf(PlanStep(title = missing.title, taskSnapshot = TaskJson.encode(missing).toString()), PlanStep(title = "New step")))
        assertTrue(restore(root(document(plan)), clean = false).isSuccess)
        say("/plan"); say("remove Missing"); say("save plan"); say("apply plan")
        assertEquals(local, db.taskDao().getTaskById(9))
        assertFalse(db.taskDao().getTaskById(9)!!.isTrashed)
    }

    @Test fun malformedPlanAndDuplicateTaskIdsAreRejectedBeforeMutation() = runBlocking {
        val local = Task(id = 1, title = "Keep me")
        db.taskDao().insertTask(local)
        val plan = PlanWorkspace(goal = "Local", steps = listOf(PlanStep(title = "Local step")))
        seed(plan)
        val bad = document(plan)
        bad.getJSONArray("plans").getJSONObject(0).getJSONArray("steps").getJSONObject(0).put("parent", "unknown")
        assertTrue(restore(root(bad)).isFailure)
        assertEquals(plan.json().toString(), current().json().toString())
        assertTrue(restore(root(tasks = listOf(local, local.copy(title = "Duplicate")))).isFailure)
        assertEquals(listOf(local), db.taskDao().getAllTasksList())
    }

    @Test fun olderBackupsClearPlansOnReplacementAndPreserveThemOnMerge() = runBlocking {
        val plan = PlanWorkspace(goal = "Local", steps = listOf(PlanStep(title = "Local step")), reviewedRevision = 1, savedUndoId = "old")
        seed(plan); AssistantSessionStore(context).save(AssistantSession(undoId = "old"))
        assertTrue(restore(root(), clean = false).isSuccess)
        assertEquals(plan.id, current().id); assertNull(current().savedUndoId)
        assertNull(AssistantSessionStore(context).load().undoId)
        assertTrue(restore(root(), clean = true).isSuccess)
        assertTrue(ConversationPlanStore(context).all().isEmpty())
        assertEquals(ChatMode.CHAT, ChatModeStore.get(context))
    }

    @Test fun lateRestoreFailureRollsBackTasksPlansSessionAndMode() = runBlocking {
        val local = Task(id = 1, title = "Local", updatedAt = 100, planningJson = TaskPlanning(goal = "Local work", minutes = 20).json())
        db.taskDao().insertTask(local)
        val plan = PlanWorkspace(goal = "Local plan", steps = listOf(PlanStep(title = "Draft")))
        seed(plan); ChatModeStore.set(context, ChatMode.PLAN)
        AssistantSessionStore(context).save(AssistantSession(lastReply = "Local reply", undoId = "local-undo"))
        val workspaceBytes = File(context.filesDir, "ayva_plan_workspaces.enc").readBytes()
        failModeCommit = true // fail after new tasks and plan files have been installed
        val incoming = PlanWorkspace(goal = "Incoming", steps = listOf(PlanStep(title = "New draft")))
        val result = restore(root(document(incoming), listOf(Task(id = 2, title = "Incoming task"))))
        assertTrue(result.isFailure)
        assertEquals(listOf(local), db.taskDao().getAllTasksList())
        assertArrayEquals(workspaceBytes, File(context.filesDir, "ayva_plan_workspaces.enc").readBytes())
        assertEquals("Local reply", AssistantSessionStore(context).load().lastReply)
        assertEquals("local-undo", AssistantSessionStore(context).load().undoId)
        assertEquals(ChatMode.PLAN, ChatModeStore.get(context))
    }

    @Test fun mergeCapacityIsCheckedBeforeReplacingTasks() = runBlocking {
        repeat(10) { seed(PlanWorkspace(goal = "Local $it")) }
        val local = Task(id = 1, title = "Local")
        db.taskDao().insertTask(local)
        val result = restore(root(document(PlanWorkspace(goal = "Imported"))), clean = false)
        assertTrue(result.isFailure)
        assertEquals(10, ConversationPlanStore(context).all().size)
        assertEquals(listOf(local), db.taskDao().getAllTasksList())
    }

    @Test fun replacementRestoreCanRecoverFromAnUnreadableLocalWorkspace() = runBlocking {
        File(context.filesDir, "ayva_plan_workspaces.enc").writeText("unreadable old workspace")
        val incoming = PlanWorkspace(goal = "Recovered goal", steps = listOf(PlanStep(title = "Recovered step")))
        val result = restore(root(document(incoming)))
        assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
        assertEquals(incoming.goal, current().goal)
    }

    @Test fun exportingUnreadablePlansFailsRatherThanSilentlyOmittingThem() = runBlocking {
        File(context.filesDir, "ayva_plan_workspaces.enc").writeText("unreadable workspace")
        val result = BackupRestoreManager.createEncryptedBackup(context, Uri.fromFile(File(context.cacheDir, "incomplete.focusbackup")), password)
        assertTrue(result.isFailure)
        assertEquals("unreadable workspace", File(context.filesDir, "ayva_plan_workspaces.enc").readText())
    }
    @Test fun encryptedMergeRemapsCanonicalGraphAndExpectedWorkspaceVersionsTogether() = runBlocking {
        val local = Task(id = 7, title = "Unrelated local task")
        db.taskDao().insertTask(local)
        val parent = Task(id = 7, title = "Prepare", updatedAt = 100,
            planningJson = TaskPlanning(goal = "Trip", minutes = 10, start = 1_900_000_000_000L).json())
        val child = Task(id = 8, title = "Book", updatedAt = 101,
            planningJson = TaskPlanning(goal = "Trip", parentTaskId = 7, prerequisiteTaskIds = setOf(7), minutes = 20,
                start = 1_900_000_600_000L).json())
        val parentStep = PlanStep(title = parent.title, minutes = 10, start = parent.planning!!.start, taskSnapshot = TaskJson.encode(parent).toString())
        val childStep = PlanStep(title = child.title, parentId = parentStep.id, prerequisites = setOf(parentStep.id), minutes = 20,
            start = child.planning!!.start, taskSnapshot = TaskJson.encode(child).toString())
        val result = restore(root(document(PlanWorkspace(goal = "Trip", steps = listOf(childStep, parentStep))), listOf(child, parent)), clean = false)
        assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
        val restored = current()
        restored.steps.forEach { step ->
            val expected = TaskJson.decode(JSONObject(step.taskSnapshot!!))
            assertEquals(expected, db.taskDao().getTaskById(expected.id))
        }
        val rows = db.taskDao().getAllTasksList().associateBy { it.title }
        assertEquals(rows.getValue("Prepare").id, rows.getValue("Book").planning!!.parentTaskId)
        assertEquals(setOf(rows.getValue("Prepare").id), rows.getValue("Book").planning!!.prerequisiteTaskIds)
        assertEquals(local, db.taskDao().getTaskById(7))
        say("/plan save plan"); assertTrue(say("apply plan").contains("Saved"))
        assertEquals(3, db.taskDao().getAllTasksList().size)
    }
    @Test fun malformedNativePlanningFailsBeforeReplacingTasksAndWorkspace() = runBlocking {
        val local = Task(id = 7, title = "Keep")
        db.taskDao().insertTask(local)
        val localPlan = PlanWorkspace(goal = "Keep plan"); seed(localPlan)
        val bad = Task(id = 4, title = "Bad", planningJson = "{not valid}")
        val result = restore(root(tasks = listOf(bad)))
        assertTrue(result.isFailure)
        assertEquals(listOf(local), db.taskDao().getAllTasksList()); assertEquals(localPlan.id, current().id)
    }

}
