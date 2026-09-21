package com.focusbyrj.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.data.note.NoteMediaManager
import com.focusbyrj.app.util.sync.supabase.SupabaseSyncEngine
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SyncAndConflictResolutionTest {

    @Test
    fun testTaskTimestampUpdatesOnCompletedTaskEdit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val taskId = 9999L
        val completedAt = 1700000000000L

        // 1. Initial state of completed task
        val initialFingerprint = "Task Title|false|high|$completedAt|null"
        val initialTs = SupabaseSyncEngine.getOrUpdateTaskTimestamp(
            context,
            taskId,
            initialFingerprint,
            completedAt
        )
        // Should preserve the explicit completedAt time initially
        assertEquals(completedAt, initialTs)

        // 2. Unchanged completed task should return the exact same timestamp
        val unchangedTs = SupabaseSyncEngine.getOrUpdateTaskTimestamp(
            context,
            taskId,
            initialFingerprint,
            completedAt
        )
        assertEquals(initialTs, unchangedTs)

        // 3. User modifies the completed task (e.g. title changes to "Updated Task Title")
        val editedFingerprint = "Updated Task Title|false|high|$completedAt|null"
        val editedTs = SupabaseSyncEngine.getOrUpdateTaskTimestamp(
            context,
            taskId,
            editedFingerprint,
            completedAt
        )

        // The edited timestamp MUST be newer than initialTs so it wins conflict resolution and syncs to cloud
        assertTrue("Edited task timestamp should be newer than completedAt", editedTs > completedAt)
        assertNotEquals(initialTs, editedTs)
    }

    @Test
    fun testNoteMediaDeletionOnSyncTombstone() {
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

        // Perform media deletion as executed when receiving cloud tombstone
        NoteMediaManager.deleteNoteMediaFiles(note)

        // Both files should be forensically wiped and unlinked from the filesystem
        assertFalse("Image file should be deleted on tombstone", imageFile.exists())
        assertFalse("Audio file should be deleted on tombstone", audioFile.exists())
    }

    @Test
    fun testBidirectionalSyncIdMapping() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val userId = "user-test-uuid-123"
        val localId = 42L

        val syncId = SupabaseSyncEngine.getOrCreateSyncId(context, userId, "NOTE", localId)
        assertTrue(syncId.isNotBlank())

        val mappedLocalId = SupabaseSyncEngine.getLocalIdForSyncId(context, userId, "NOTE", syncId)
        assertEquals(localId, mappedLocalId)

        // Verifying idempotence
        val sameSyncId = SupabaseSyncEngine.getOrCreateSyncId(context, userId, "NOTE", localId)
        assertEquals(syncId, sameSyncId)
    }
}
