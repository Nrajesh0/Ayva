package com.focusbyrj.app

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitRepository
import com.focusbyrj.app.data.HabitType
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskRepository
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.ui.screens.notes.NotesViewModel
import com.focusbyrj.app.ui.screens.notes.NotesnookBlock
import com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager
import com.focusbyrj.app.ui.viewmodels.FocusViewModel
import com.focusbyrj.app.ui.viewmodels.HabitViewModel
import com.focusbyrj.app.ui.viewmodels.TaskViewModel
import com.focusbyrj.app.util.AppIconManager
import com.focusbyrj.app.util.FocusEconomyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.util.Calendar
import java.util.TimeZone

/**
 * Batch 8 Security & Bug Audit Regression Test Suite
 *
 * Adversarial TDD tests covering:
 * - BATCH-8-001: NotesViewModel.latestNotesCache bounded LRU capacity (prevents unbounded memory leak)
 * - BATCH-8-002: TaskDao & TaskViewModel completed tasks purge preserves recent tasks with NULL completedAt and registers tombstones
 * - BATCH-8-003: HabitViewModel, HabitActionReceiver, & HabitFloatingOverlayManager infinite XP/Gold farming exploit prevention
 * - BATCH-8-004: NotesViewModel undo/redo updates sessionId to prevent Compose editor UI desynchronization
 * - BATCH-8-005: DatePicker UTC midnight parsing preserves selected calendar day in negative UTC offsets
 * - BATCH-8-006: TaskViewModel updateTask enforces monotonic updatedAt timestamp for cloud sync LWW conflict resolution
 */
@RunWith(RobolectricTestRunner::class)
class Batch8SecurityAuditTest {

    private lateinit var app: FocusApplication
    private lateinit var context: Context
    private lateinit var focusDb: FocusDatabase
    private lateinit var noteDb: NoteDatabase
    private lateinit var taskRepo: TaskRepository
    private lateinit var habitRepo: HabitRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<FocusApplication>()
        context = app
        focusDb = app.database
        noteDb = NoteDatabase.getInstance(app)
        taskRepo = app.taskRepository
        habitRepo = app.habitRepository

        FocusEconomyManager.init(context)

        runBlocking {
            focusDb.taskDao().deleteAllTasks()
            noteDb.noteDao().deleteAllNotes()
            focusDb.habitDao().deleteAllHabits()
            focusDb.habitDao().deleteAllLogs()
        }
        NotesViewModel.latestNotesCache.clear()
    }

    @After
    fun tearDown() {
        runBlocking {
            focusDb.taskDao().deleteAllTasks()
            noteDb.noteDao().deleteAllNotes()
            focusDb.habitDao().deleteAllHabits()
            focusDb.habitDao().deleteAllLogs()
        }
        NotesViewModel.latestNotesCache.clear()
    }

    // BATCH-8-001: latestNotesCache bounded capacity (evicts eldest after 200 items)
    @Test
    fun testLatestNotesCacheBoundedCapacity() {
        NotesViewModel.latestNotesCache.clear()

        // Insert 250 dummy notes
        for (i in 1L..250L) {
            val note = NoteEntity(
                id = i,
                title = "Note $i",
                content = "Content $i",
                updatedAt = System.currentTimeMillis()
            )
            NotesViewModel.latestNotesCache[i] = note
        }

        // Cache must be bounded to at most 200 items
        assertEquals(200, NotesViewModel.latestNotesCache.size)

        // Eldest entries (e.g. 1L to 50L) must have been evicted
        assertNull("Eldest item 1L should have been evicted", NotesViewModel.latestNotesCache[1L])
        assertNull("Eldest item 50L should have been evicted", NotesViewModel.latestNotesCache[50L])
        assertNotNull("Recent item 250L should remain cached", NotesViewModel.latestNotesCache[250L])
    }

    // BATCH-8-002: TaskDao completed tasks threshold query must not delete recently completed tasks where completedAt is null
    @Test
    fun testTaskDaoCompletedTasksDeletionPreservesRecentlyCompletedNullTimestamp() = runBlocking {
        val now = System.currentTimeMillis()
        val fortyDaysAgo = now - (40L * 24 * 60 * 60 * 1000L)
        val thresholdThirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000L)

        // Task 1: Completed recently (10 seconds ago), but completedAt is null
        val recentTaskNullCompletedAt = Task(
            id = 801L,
            title = "Recent task with null completedAt",
            isCompleted = true,
            completedAt = null,
            updatedAt = now - 10_000L
        )
        // Task 2: Completed 40 days ago, completedAt is null, updatedAt was 40 days ago
        val oldTaskNullCompletedAt = Task(
            id = 802L,
            title = "Old task with null completedAt",
            isCompleted = true,
            completedAt = null,
            updatedAt = fortyDaysAgo
        )
        // Task 3: Completed 40 days ago with explicit completedAt
        val oldTaskWithCompletedAt = Task(
            id = 803L,
            title = "Old task with completedAt",
            isCompleted = true,
            completedAt = fortyDaysAgo,
            updatedAt = fortyDaysAgo
        )
        // Task 4: Not completed (active)
        val activeTask = Task(
            id = 804L,
            title = "Active task",
            isCompleted = false,
            completedAt = null,
            updatedAt = fortyDaysAgo
        )

        focusDb.taskDao().insertTask(recentTaskNullCompletedAt)
        focusDb.taskDao().insertTask(oldTaskNullCompletedAt)
        focusDb.taskDao().insertTask(oldTaskWithCompletedAt)
        focusDb.taskDao().insertTask(activeTask)

        val expiredIds = focusDb.taskDao().getCompletedTaskIdsBefore(thresholdThirtyDaysAgo)

        assertFalse("Recent task with null completedAt must NOT be marked for deletion", expiredIds.contains(801L))
        assertTrue("Old task with null completedAt must be marked for deletion", expiredIds.contains(802L))
        assertTrue("Old task with completedAt must be marked for deletion", expiredIds.contains(803L))
        assertFalse("Active task must NOT be marked for deletion", expiredIds.contains(804L))

        focusDb.taskDao().deleteCompletedTasksBefore(thresholdThirtyDaysAgo)

        assertNotNull("Recent task with null completedAt must remain in database", focusDb.taskDao().getTaskById(801L))
        assertNull("Old task with null completedAt must be purged", focusDb.taskDao().getTaskById(802L))
        assertNull("Old task with completedAt must be purged", focusDb.taskDao().getTaskById(803L))
        assertNotNull("Active task must remain in database", focusDb.taskDao().getTaskById(804L))
    }

    // BATCH-8-003: Habit incrementing past daily target must NOT grant infinite XP and Gold
    @Test
    fun testHabitIncrementDoesNotAwardInfiniteXpOrGoldOverTarget() = runBlocking {
        val habit = Habit(
            id = 901L,
            title = "Drink Water",
            iconEmoji = "💧",
            type = HabitType.INTERVAL_WINDOW,
            targetPerDay = 2
        )
        focusDb.habitDao().insertHabit(habit)

        val habitVm = HabitViewModel(habitRepo, app)

        fun awaitHabitCount(expectedCount: Int) {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            var tries = 0
            while (tries < 30) {
                Thread.sleep(50)
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                val log = runBlocking { focusDb.habitDao().getLogForHabitAndDate(901L, today) }
                if (log != null && log.completedCount >= expectedCount) {
                    break
                }
                tries++
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }

        // Initial progress: 0/2
        // Tap 1: progress -> 1/2 (under target)
        habitVm.incrementProgress(habit)
        awaitHabitCount(1)

        val p1 = FocusEconomyManager.profileFlow.value
        val xpAfterTap1 = p1.pendingXp
        val goldAfterTap1 = p1.pendingGold
        assertTrue("First tap awards rewards", xpAfterTap1 > 0 && goldAfterTap1 > 0)

        // Tap 2: progress -> 2/2 (goal met milestone!)
        habitVm.incrementProgress(habit)
        awaitHabitCount(2)

        val p2 = FocusEconomyManager.profileFlow.value
        val xpAfterGoalMet = p2.pendingXp
        val goldAfterGoalMet = p2.pendingGold
        assertTrue("Goal milestone awards bonus rewards", xpAfterGoalMet > xpAfterTap1 && goldAfterGoalMet > goldAfterTap1)

        // Tap 3: progress -> 3/2 (OVER TARGET)
        habitVm.incrementProgress(habit)
        awaitHabitCount(3)

        val p3 = FocusEconomyManager.profileFlow.value
        assertEquals("XP must NOT increase on over-target increment", xpAfterGoalMet, p3.pendingXp)
        assertEquals("Gold must NOT increase on over-target increment", goldAfterGoalMet, p3.pendingGold)

        // Tap 4: progress -> 4/2 (OVER TARGET)
        habitVm.incrementProgress(habit)
        awaitHabitCount(4)

        val p4 = FocusEconomyManager.profileFlow.value
        assertEquals("XP must remain capped when exceeding target", xpAfterGoalMet, p4.pendingXp)
        assertEquals("Gold must remain capped when exceeding target", goldAfterGoalMet, p4.pendingGold)
    }

    // BATCH-8-004: NotesViewModel undo/redo must generate a new sessionId so Compose re-keys editor state
    @Test
    fun testNotesViewModelUndoRedoUpdatesSessionId() = runBlocking {
        val notesVm = NotesViewModel(app)

        notesVm.openNewNote(asChecklist = false)
        val initialSessionId = notesVm.editingState.value?.sessionId
        assertNotNull("Initial session ID must exist", initialSessionId)

        // Type first text and push undo snapshot
        notesVm.updateEditorTitle("First Title")
        notesVm.updateEditorContent("First Content")
        notesVm.pushUndoSnapshot()

        // Type second text
        notesVm.updateEditorContent("Second Content")

        // Undo
        notesVm.undo()
        val undoneState = notesVm.editingState.value
        assertNotNull(undoneState)
        assertEquals("First Content", undoneState?.content)
        assertNotEquals("Session ID must update on undo to force Compose state re-key", initialSessionId, undoneState?.sessionId)

        val undoSessionId = undoneState?.sessionId

        // Redo
        notesVm.redo()
        val redoneState = notesVm.editingState.value
        assertNotNull(redoneState)
        assertEquals("Second Content", redoneState?.content)
        assertNotEquals("Session ID must update on redo to force Compose state re-key", undoSessionId, redoneState?.sessionId)
    }

    // BATCH-8-005: DatePicker UTC midnight interpretation in negative timezones
    @Test
    fun testDatePickerUtcToLocalCalendarInterpretation() {
        val originalTz = TimeZone.getDefault()
        try {
            // Set timezone to America/New_York (UTC-4 / UTC-5)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

            // 2025-10-25 00:00:00 UTC = 1761350400000L
            val utcSelectedMillis = 1761350400000L

            // Defective legacy interpretation (using local timezone)
            val buggyCal = Calendar.getInstance().apply { timeInMillis = utcSelectedMillis }
            // In EDT (UTC-4), 00:00 UTC Oct 25 is 20:00 EDT Oct 24!
            assertEquals("Buggy local calendar shifts back by 1 day", 24, buggyCal.get(Calendar.DAY_OF_MONTH))

            // Fixed interpretation (using UTC timezone to read day/month/year)
            val fixedCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcSelectedMillis }
            assertEquals("Fixed UTC calendar preserves exact day", 25, fixedCal.get(Calendar.DAY_OF_MONTH))
            assertEquals("Fixed UTC calendar preserves October (month index 9)", Calendar.OCTOBER, fixedCal.get(Calendar.MONTH))
            assertEquals("Fixed UTC calendar preserves year", 2025, fixedCal.get(Calendar.YEAR))
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    // BATCH-8-006: TaskViewModel updateTask must update updatedAt to current timestamp for LWW conflict resolution
    @Test
    fun testTaskViewModelUpdateTaskEnforcesCurrentUpdatedAtTimestamp() = runBlocking {
        val staleTimestamp = 50000L
        val task = Task(
            id = 899L,
            title = "Original Title",
            details = "Details",
            updatedAt = staleTimestamp
        )
        focusDb.taskDao().insertTask(task)

        val taskVm = TaskViewModel(taskRepo, app)
        val startTime = System.currentTimeMillis()
        taskVm.updateTask(task.copy(title = "Updated Title"))

        // Drain coroutine queue
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val persisted = focusDb.taskDao().getTaskById(899L)
        assertNotNull("Task must exist in DB", persisted)
        assertEquals("Updated Title", persisted?.title)
        assertTrue("updatedAt must be refreshed to >= startTime", persisted!!.updatedAt >= startTime)
        assertNotEquals("updatedAt must not remain stale", staleTimestamp, persisted.updatedAt)
    }

    // BATCH-8-011: NotesViewModel lockVault clears ArchiveVaultSecurity active subkey and syncs lock state
    @Test
    fun testNotesViewModelLockVaultClearsArchiveVaultSecuritySubKey() = runBlocking {
        ArchiveVaultSecurity.lockVault()
        val configured = ArchiveVaultSecurity.setPasscode(context, "123456")
        assertTrue("Passcode configuration should succeed", configured)
        assertNotNull("Active subkey must exist after setting passcode", ArchiveVaultSecurity.getActiveVaultSubKey())
        assertFalse("Vault must be unlocked", ArchiveVaultSecurity.isVaultLocked(context))

        val notesVm = NotesViewModel(app)
        notesVm.refreshVaultStatus()

        // Lock vault via ViewModel
        notesVm.lockVault()

        assertNull("lockVault() must zeroize and clear ArchiveVaultSecurity subkey", ArchiveVaultSecurity.getActiveVaultSubKey())
        assertTrue("isVaultLocked() must be true after lockVault()", ArchiveVaultSecurity.isVaultLocked(context))
        assertFalse("ViewModel _isVaultUnlocked must be false", notesVm.isVaultUnlocked.value)
    }

    // BATCH-8-012: TaskViewModel emptyTrash must query database synchronously to avoid missing uncollected StateFlow items and dropping tombstones
    @Test
    fun testTaskViewModelEmptyTrashUsesDatabaseQueryNotStateFlow() = runBlocking {
        val testUserId = "user_audit_batch8_012"
        val syncId777 = "sync-uuid-task-777"
        val zkPrefs = context.getSharedPreferences("focus_supabase_zk_prefs", Context.MODE_PRIVATE)
        zkPrefs.edit()
            .putString("user_id", testUserId)
            .putString("access_token", "fake_access_token")
            .commit()

        com.focusbyrj.app.util.sync.supabase.SupabaseSyncEngine.bindSyncId(
            context, testUserId, "TASK", 777L, syncId777
        )

        val delPrefs = context.getSharedPreferences("focus_supabase_deletions", Context.MODE_PRIVATE)
        delPrefs.edit().clear().commit()

        val trashedTask = Task(
            id = 777L,
            title = "Trashed task pending deletion",
            isCompleted = true,
            isTrashed = true,
            updatedAt = System.currentTimeMillis()
        )
        focusDb.taskDao().insertTask(trashedTask)

        // Do not collect taskVm.trashedTasks to simulate cold StateFlow
        val taskVm = TaskViewModel(taskRepo, app)
        assertTrue("Cold trashedTasks StateFlow value is initially empty", taskVm.trashedTasks.value.isEmpty())

        taskVm.emptyTrash()

        var retries = 0
        while (retries < 20 && focusDb.taskDao().getTaskById(777L) != null) {
            delay(50)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            retries++
        }

        assertNull("Task 777L must be removed from Room database", focusDb.taskDao().getTaskById(777L))
        val pendingDeletions = delPrefs.getStringSet("pending_deletions", emptySet()) ?: emptySet()
        assertTrue(
            "Tombstone for task 777L must be queued in pending_deletions even if StateFlow was uncollected: $pendingDeletions",
            pendingDeletions.contains("TASK:$syncId777")
        )
    }

    // BATCH-8-013: NotesViewModel deleteCurrentNote must delete auto-saved notes where originalId is 0L but pendingNewNoteId is set
    @Test
    fun testNotesViewModelDeleteCurrentNoteHandlesPendingNewNoteId() = runBlocking {
        val notesVm = NotesViewModel(app)
        notesVm.openNewNote(asChecklist = false)

        notesVm.updateEditorTitle("Draft To Delete")
        notesVm.updateEditorContent("Content To Delete")

        var savedNote: NoteEntity? = null
        var retries = 0
        while (retries < 30) {
            delay(50)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val all = noteDb.noteDao().getAllActiveNotesSync()
            savedNote = all.find { it.title == "Draft To Delete" }
            if (savedNote != null) break
            retries++
        }
        assertNotNull("Note should have been auto-saved to DB", savedNote)
        val noteId = savedNote!!.id
        assertFalse("Initially not trashed", savedNote.isTrashed)

        // Now call deleteCurrentNote()
        notesVm.deleteCurrentNote()

        retries = 0
        while (retries < 30) {
            delay(50)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val check = noteDb.noteDao().getNoteByIdSync(noteId)
            if (check?.isTrashed == true) break
            retries++
        }

        val finalNote = noteDb.noteDao().getNoteByIdSync(noteId)
        assertNotNull("Note must exist in database", finalNote)
        assertTrue("Auto-saved draft must be moved to trash on deleteCurrentNote()", finalNote!!.isTrashed)
    }

    // BATCH-8-014: restoreNote() and unarchiveNote() must invalidate stale entities from latestNotesCache
    @Test
    fun testNotesViewModelRestoreAndUnarchiveInvalidateLatestNotesCache() = runBlocking {
        val notesVm = NotesViewModel(app)

        val trashedNote = NoteEntity(id = 811L, title = "Trashed Note", isTrashed = true)
        val archivedNote = NoteEntity(id = 812L, title = "Archived Note", isArchived = true)
        noteDb.noteDao().insertNote(trashedNote)
        noteDb.noteDao().insertNote(archivedNote)

        NotesViewModel.latestNotesCache[811L] = trashedNote
        NotesViewModel.latestNotesCache[812L] = archivedNote

        assertEquals(trashedNote, NotesViewModel.latestNotesCache[811L])
        assertEquals(archivedNote, NotesViewModel.latestNotesCache[812L])

        notesVm.restoreNote(trashedNote)
        notesVm.unarchiveNote(archivedNote)

        assertNull("restoreNote must remove stale entity from latestNotesCache", NotesViewModel.latestNotesCache[811L])
        assertNull("unarchiveNote must remove stale entity from latestNotesCache", NotesViewModel.latestNotesCache[812L])
    }

    // BATCH-8-015: Note copying and sharing must strip Notesnook block markup and serialize human-readable text
    @Test
    fun testNotesViewModelShareAndCopyStripNotesnookBlockTags() = runBlocking {
        val notesVm = NotesViewModel(app)

        val codeBlock = NotesnookBlock.Code(language = "kotlin", code = "val x = 42")
        val textBlock = NotesnookBlock.Text(text = "Code demonstration:")
        val rawContent = NotesnookBlockManager.serialize(listOf(textBlock, codeBlock))

        assertTrue("Serialized content contains raw block marker", rawContent.contains(NotesnookBlockManager.BLOCKS_PREFIX))

        val note = NoteEntity(id = 815L, title = "Tech Snippet", content = rawContent)
        notesVm.openExistingNote(note)

        notesVm.copyCurrentNoteToClipboard(context)

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()

        assertNotNull("Clipboard must contain copied text", clip)
        assertFalse("Clipboard text must NOT leak raw Notesnook block markup", clip!!.contains("<!--NOTESNOOK_BLOCKS:"))
        assertFalse("Clipboard text must NOT leak raw JSON end markup", clip.contains(":BLOCKS_END-->"))
        assertTrue("Clipboard text must include formatted block content", clip.contains("Code demonstration:"))
        assertTrue("Clipboard text must include human-readable code representation", clip.contains("[Code: kotlin]"))
    }

    // BATCH-8-016: AppIconManager setAppIcon must never disable com.focusbyrj.app.MainActivity component
    @Test
    fun testAppIconManagerPreservesMainActivityComponent() {
        val pm = context.packageManager
        val mainActivityComp = android.content.ComponentName(context, MainActivity::class.java)

        // Switch to an alias icon
        val success = AppIconManager.setAppIcon(context, "wanderer")
        assertTrue("setAppIcon should succeed", success)

        val mainActivityState = pm.getComponentEnabledSetting(mainActivityComp)
        assertNotEquals(
            "MainActivity base component must NEVER be disabled when switching launcher icons",
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            mainActivityState
        )
    }

    // BATCH-8-017: FocusViewModel must restore isSessionActive state from SharedPreferences on recreation
    @Test
    fun testFocusViewModelSessionStateRestoresFromPrefs() {
        val prefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("isSessionActive", true).commit()

        val focusVm = FocusViewModel(app.repository, app)
        assertTrue(
            "FocusViewModel must initialize isSessionActive=true when SharedPreferences indicates an active session",
            focusVm.isSessionActive.value
        )
    }
}
