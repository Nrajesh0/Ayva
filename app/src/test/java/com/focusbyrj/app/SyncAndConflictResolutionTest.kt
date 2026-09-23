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

    @Test
    fun testMediaPendingDeletionQueue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imgName = "note_photo_abc.jpg"
        val audioName = "voice_memo_xyz.mp3"

        com.focusbyrj.app.util.sync.supabase.SupabaseStorageEngine.recordPendingMediaDeletion(context, "/path/to/files/$imgName")
        com.focusbyrj.app.util.sync.supabase.SupabaseStorageEngine.recordPendingMediaDeletion(context, audioName)

        val pending = com.focusbyrj.app.util.sync.supabase.SupabaseStorageEngine.getPendingMediaDeletions(context)
        assertTrue(pending.contains(imgName))
        assertTrue(pending.contains(audioName))

        com.focusbyrj.app.util.sync.supabase.SupabaseStorageEngine.clearPendingMediaDeletions(context, setOf(imgName))
        val remaining = com.focusbyrj.app.util.sync.supabase.SupabaseStorageEngine.getPendingMediaDeletions(context)
        assertFalse(remaining.contains(imgName))
        assertTrue(remaining.contains(audioName))
    }

    @Test
    fun testCompletedTaskHistoryRecordingAndMidnightPurge() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val startOfToday = com.focusbyrj.app.util.CompletedTaskHistoryManager.getStartOfTodayMs()
        
        // 1. Record a task completed today
        val taskToday = com.focusbyrj.app.data.Task(
            id = 101L,
            title = "Morning Gym Routine",
            details = "Completed 5 sets",
            isCompleted = false
        )
        com.focusbyrj.app.util.CompletedTaskHistoryManager.recordCompletedTask(context, taskToday, startOfToday + 3600000L) // 1 hour into today

        val todayRecords = com.focusbyrj.app.util.CompletedTaskHistoryManager.getTodayCompletedTasks(context)
        assertEquals(1, todayRecords.size)
        assertEquals("Morning Gym Routine", todayRecords[0].title)

        // 2. Add an old yesterday task (completed before midnight)
        val taskYesterday = com.focusbyrj.app.data.Task(
            id = 102L,
            title = "Yesterday Task",
            isCompleted = false
        )
        com.focusbyrj.app.util.CompletedTaskHistoryManager.recordCompletedTask(context, taskYesterday, startOfToday - 10000L)

        // Only today's task should be returned by getTodayCompletedTasks
        val todayAfterYesterday = com.focusbyrj.app.util.CompletedTaskHistoryManager.getTodayCompletedTasks(context)
        assertEquals(1, todayAfterYesterday.size)
        assertEquals("Morning Gym Routine", todayAfterYesterday[0].title)

        // 3. Explicit purge call
        com.focusbyrj.app.util.CompletedTaskHistoryManager.purgeExpiredCompletedTasks(context)
        assertEquals(1, com.focusbyrj.app.util.CompletedTaskHistoryManager.getTodayCompletedCount(context))
    }

    @Test
    fun testMediaAttachmentPathSanitizationAndSelfHealing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val userId = "test-user-uuid-999"

        // 1. Verify recordPendingMediaDeletion sanitizes local paths ending in .enc
        val corruptedLocalPath = "/data/user/0/com.focusbyrj.app/files/keep_images/63f1000c-f1bf-443f-a653-9da56fd83982.enc"
        com.focusbyrj.app.util.sync.supabase.SupabaseStorageEngine.recordPendingMediaDeletion(
            context = context,
            pathOrFileName = corruptedLocalPath,
            explicitUserId = userId
        )

        val pending = com.focusbyrj.app.util.sync.supabase.SupabaseStorageEngine.getPendingMediaDeletions(context, userId = userId)
        val expectedCloudPath = "$userId/63f1000c-f1bf-443f-a653-9da56fd83982.enc"
        assertTrue(
            "Pending deletion should contain sanitized cloud path '$expectedCloudPath', but had: $pending",
            pending.contains(expectedCloudPath)
        )
        assertFalse(
            "Pending deletion MUST NOT leak internal phone path",
            pending.contains(corruptedLocalPath)
        )

        // 2. Verify downloadMedia local cache hit reuses existing encrypted file and populates manifest
        val keepImagesDir = File(context.filesDir, "keep_images").apply { mkdirs() }
        val localMediaFile = File(keepImagesDir, "63f1000c-f1bf-443f-a653-9da56fd83982.enc").apply {
            writeBytes("mock encrypted content".toByteArray())
        }
        assertTrue(localMediaFile.exists())

        // Simulate session state for KeyManager
        val sessionPrefs = context.getSharedPreferences("focus_supabase_zk_prefs", Context.MODE_PRIVATE)
        sessionPrefs.edit()
            .putString("user_id", userId)
            .putString("access_token", "mock_token")
            .commit()

        // Call downloadMedia with the corrupted cloudPath pointing to internal path
        val resolvedPath = com.focusbyrj.app.util.sync.supabase.SupabaseStorageEngine.downloadMedia(
            context = context,
            cloudPath = corruptedLocalPath,
            subDirName = "keep_images",
            accessToken = "mock_token",
            dataKey = ByteArray(32) { 0x01 }
        )

        assertEquals(localMediaFile.absolutePath, resolvedPath)

        // Verify manifest now contains mapped cloud UUID
        val mappedUuid = com.focusbyrj.app.util.sync.supabase.SupabaseStorageEngine.getCloudUuid(
            context,
            userId,
            localMediaFile.absolutePath
        )
        assertEquals("63f1000c-f1bf-443f-a653-9da56fd83982", mappedUuid)
    }

    @Test
    fun testClearSessionPreservesUserNamespacedSyncMapAndManifest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val userId = "user-persist-123"
        val localId = 77L
        val cloudId = "cloud-uuid-77"
        val localImgPath = "/data/user/0/com.focusbyrj.app/files/keep_images/media77.enc"

        // 1. Bind sync ID and media manifest
        SupabaseSyncEngine.bindSyncId(context, userId, "NOTE", localId, cloudId)
        com.focusbyrj.app.util.sync.supabase.SupabaseStorageEngine.bindCloudUuid(context, userId, localImgPath, "uuid-media-77")

        // 2. Set mock session and sequence number
        val sessionPrefs = context.getSharedPreferences("focus_supabase_zk_prefs", Context.MODE_PRIVATE)
        sessionPrefs.edit().putString("access_token", "jwt_token").putString("user_id", userId).commit()
        com.focusbyrj.app.util.sync.supabase.SupabaseKeyManager.updateSequenceNumberIfHigher(context, cloudId, 5L)

        // 3. Invoke clearSession (e.g. user taps Log Out)
        com.focusbyrj.app.util.sync.supabase.SupabaseKeyManager.clearSession(context)

        // 4. Session tokens and sequence numbers must be cleared
        val sessionAfter = com.focusbyrj.app.util.sync.supabase.SupabaseKeyManager.getSessionState(context)
        assertFalse("Session must not be signed in after clearSession", sessionAfter.isSignedIn)
        assertEquals(0L, com.focusbyrj.app.util.sync.supabase.SupabaseKeyManager.getSequenceNumber(context, cloudId))

        // 5. User-namespaced sync mappings and manifest MUST be preserved to prevent duplicate notes/tasks upon re-login
        val localIdAfter = SupabaseSyncEngine.getLocalIdForSyncId(context, userId, "NOTE", cloudId)
        assertEquals("Local ID mapping must persist across sign-outs for the same user", localId, localIdAfter)

        val cloudUuidAfter = com.focusbyrj.app.util.sync.supabase.SupabaseStorageEngine.getCloudUuid(context, userId, localImgPath)
        assertEquals("Media manifest mapping must persist across sign-outs for the same user", "uuid-media-77", cloudUuidAfter)
    }

    @Test
    fun testIsMatchingItemHasNoSideEffects() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val userId = "user-side-effect-test"
        val unmappedLocalId = 8888L

        // Prior to call, localId has no sync ID
        val syncIdBefore = SupabaseSyncEngine.getExistingSyncId(context, userId, "NOTE", unmappedLocalId)
        org.junit.Assert.assertNull("Unmapped note must have null existing sync ID", syncIdBefore)

        // Query isMatchingItem via reflection since it is private
        val isMatchingMethod = SupabaseSyncEngine::class.java.getDeclaredMethod(
            "isMatchingItem",
            Context::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Long::class.javaPrimitiveType
        ).apply { isAccessible = true }

        val result = isMatchingMethod.invoke(
            SupabaseSyncEngine,
            context,
            "arbitrary-cloud-uuid-999",
            userId,
            "NOTE",
            unmappedLocalId
        ) as Boolean

        assertFalse("Arbitrary cloud UUID should not match unmapped local note", result)

        // After call, localId MUST STILL have no sync ID — isMatchingItem must not generate random UUID side-effects!
        val syncIdAfter = SupabaseSyncEngine.getExistingSyncId(context, userId, "NOTE", unmappedLocalId)
        org.junit.Assert.assertNull("isMatchingItem must NOT generate or write a sync ID as a side-effect", syncIdAfter)
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
