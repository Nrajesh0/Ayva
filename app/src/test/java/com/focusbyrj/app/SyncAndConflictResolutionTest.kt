package com.focusbyrj.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.data.note.NoteMediaManager
import com.focusbyrj.app.util.CompletedTaskHistoryManager
import com.focusbyrj.app.util.sync.VaultSyncManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SyncAndConflictResolutionTest {

    @Test
    fun testNoteMediaDeletionOnTombstone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imagesDir = File(context.filesDir, "keep_images").apply { mkdirs() }
        val audioDir = File(context.filesDir, "keep_audio").apply { mkdirs() }

        val imageFile = File(imagesDir, "test_sync_img.webp").apply {
            writeBytes(ByteArray(1024) { 0xFF.toByte() })
        }
        val audioFile = File(audioDir, "test_sync_audio.m4a").apply {
            writeBytes(ByteArray(2048) { 0xAA.toByte() })
        }

        assertTrue(imageFile.exists())
        assertTrue(audioFile.exists())

        val imagesJson = JSONArray().put(imageFile.absolutePath).toString()
        val audioJson = JSONArray().put(audioFile.absolutePath).toString()

        val note = NoteEntity(
            id = 555L,
            title = "Note to be deleted by tombstone",
            imageUrisJson = imagesJson,
            audioUrisJson = audioJson
        )

        // Perform media deletion as executed when purging a deleted note
        NoteMediaManager.deleteNoteMediaFiles(note)

        // Both files should be wiped and unlinked from the filesystem
        assertFalse("Image file should be deleted on tombstone", imageFile.exists())
        assertFalse("Audio file should be deleted on tombstone", audioFile.exists())
    }

    @Test
    fun testAllowedMediaFilePathValidation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imagesDir = File(context.filesDir, "keep_images").apply { mkdirs() }
        val validImage = File(imagesDir, "valid_photo.jpg")

        assertTrue("File inside keep_images should be allowed", NoteMediaManager.isAllowedMediaFile(validImage, context))

        val dbFile = File(context.filesDir, "../databases/keep_notes_vault.db")
        assertFalse("Database files must NEVER be allowed media files", NoteMediaManager.isAllowedMediaFile(dbFile, context))

        val prefsFile = File(context.filesDir, "../shared_prefs/prefs.xml")
        assertFalse("SharedPrefs files must NEVER be allowed media files", NoteMediaManager.isAllowedMediaFile(prefsFile, context))
    }

    @Test
    fun testCompletedTaskHistoryRecordingAndMidnightPurge() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val startOfToday = CompletedTaskHistoryManager.getStartOfTodayMs()

        // 1. Record a task completed today
        val taskToday = Task(
            id = 101L,
            title = "Morning Gym Routine",
            details = "Completed 5 sets",
            isCompleted = false
        )
        CompletedTaskHistoryManager.recordCompletedTask(context, taskToday, startOfToday + 3600000L) // 1 hour into today

        val todayRecords = CompletedTaskHistoryManager.getTodayCompletedTasks(context)
        assertEquals(1, todayRecords.size)
        assertEquals("Morning Gym Routine", todayRecords[0].title)

        // 2. Add an old yesterday task (completed before midnight)
        val taskYesterday = Task(
            id = 102L,
            title = "Yesterday Task",
            isCompleted = false
        )
        CompletedTaskHistoryManager.recordCompletedTask(context, taskYesterday, startOfToday - 10000L)

        // Only today's task should be returned by getTodayCompletedTasks
        val todayAfterYesterday = CompletedTaskHistoryManager.getTodayCompletedTasks(context)
        assertEquals(1, todayAfterYesterday.size)
        assertEquals("Morning Gym Routine", todayAfterYesterday[0].title)

        // 3. Explicit purge call
        CompletedTaskHistoryManager.purgeExpiredCompletedTasks(context)
        assertEquals(1, CompletedTaskHistoryManager.getTodayCompletedCount(context))
    }

    @Test
    fun testVaultPayloadSerializationStructure() {
        val note = NoteEntity(
            id = 1L,
            title = "Test Note",
            content = "Test Content",
            updatedAt = 1000L
        )
        val task = Task(
            id = 10L,
            title = "Test Task",
            updatedAt = 1000L
        )

        val payload = VaultSyncManager.VaultPayload(
            notes = listOf(note),
            tasks = listOf(task),
            exportedAt = System.currentTimeMillis(),
            appVersion = "1.9.9"
        )

        assertEquals(1, payload.notes.size)
        assertEquals(1, payload.tasks.size)
        assertEquals("1.9.9", payload.appVersion)
    }

    @Test
    fun testNoteTrashedAtPreservedInNoteEntity() {
        val now = System.currentTimeMillis()
        val note = NoteEntity(
            id = 123L,
            title = "Trashed Note",
            isTrashed = true,
            trashedAt = now
        )
        assertTrue(note.isTrashed)
        assertEquals(now, note.trashedAt)
    }
}
