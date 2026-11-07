package com.focusbyrj.app

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.focusbyrj.app.data.note.*
import com.focusbyrj.app.service.HabitReceiver
import com.focusbyrj.app.service.HabitActionReceiver
import com.focusbyrj.app.util.*
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import com.focusbyrj.app.util.diagnostics.AppLogger
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34], application = Application::class)
class PreReleaseDataSafetyTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var db: NoteDatabase
    @Before fun setup() {
        MediaRetirement.initialize(app)
        db = Room.inMemoryDatabaseBuilder(app, NoteDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun cleanup() { db.close() }
    private fun image(name: String): File = File(app.filesDir, "keep_images/$name").apply {
        EncryptedMediaStorage.writeEncryptedBytes(this, "synthetic media".toByteArray())
        setLastModified(System.currentTimeMillis() - 3_600_000L)
    }
    private fun note(file: File) = NoteEntity(title = "Synthetic", imageUrisJson = JSONArray(listOf(file.path)).toString())
    /** A new process loses transient leases, while its authenticated retirement journal survives. */
    @Suppress("UNCHECKED_CAST")
    private fun nextProcess() {
        val field = MediaRetirement::class.java.getDeclaredField("livePaths").apply { isAccessible = true }
        (field.get(MediaRetirement) as MutableSet<String>).clear()
    }

    @Test fun unknownFilesAreNeverDeletedBecauseOfAge() = runBlocking {
        val file = image("unknown.jpg")
        nextProcess()
        NoteMediaManager.cleanOrphanedMedia(app, db.noteDao())
        assertTrue(file.exists())
    }

    @Test fun provenUnreferencedRetirementCanBeReclaimedAfterProcessLeasesEnd() = runBlocking {
        val file = image("retired.jpg")
        val id = db.noteDao().insertNote(note(file))
        db.noteDao().deleteNote(db.noteDao().getNoteByIdSync(id)!!)
        assertTrue(file.exists())
        NoteMediaManager.cleanOrphanedMedia(app, db.noteDao())
        assertTrue("A live-process creation lease retains the file", file.exists())
        nextProcess()
        NoteMediaManager.cleanOrphanedMedia(app, db.noteDao())
        assertFalse(file.exists())
    }

    @Test fun newlySavedReferenceCannotBeDeletedByAnOlderSnapshot() = runBlocking {
        val file = image("shared.jpg")
        val real = db.noteDao()
        val old = real.insertNote(note(file))
        real.deleteNote(real.getNoteByIdSync(old)!!)
        nextProcess()
        var id = 0L
        val interleaved = object : NoteDao by real {
            override suspend fun withMediaReferenceSnapshot(consume: suspend (List<NoteEntity>) -> Unit) {
                real.withMediaReferenceSnapshot { snapshot ->
                    id = real.insertNote(note(file))
                    consume(snapshot)
                }
            }
        }
        NoteMediaManager.cleanOrphanedMedia(app, interleaved)
        assertNotNull(real.getNoteByIdSync(id))
        assertTrue(file.exists())
    }

    @Test fun unreadableReferencesAndPendingRecoveryRetainRetiredFiles() = runBlocking {
        val file = image("recoverable.jpg")
        val dao = db.noteDao()
        val id = dao.insertNote(note(file)); dao.deleteNote(dao.getNoteByIdSync(id)!!)
        nextProcess()
        val broken = dao.insertNote(NoteEntity(imageUrisJson = "[\"${file.path}\""))
        NoteMediaManager.cleanOrphanedMedia(app, dao)
        assertTrue(file.exists())
        dao.deleteNote(dao.getNoteByIdSync(broken)!!)
        val recovery = File(app.filesDir, "pending_vault_draft.enc.bak").apply { writeText("synthetic interrupted recovery write") }
        NoteMediaManager.cleanOrphanedMedia(app, dao)
        assertTrue(file.exists()); recovery.delete(); Unit
    }

    @Test fun unsupportedUriReferencesCannotAuthorizeReclaimingTheLocalFile() = runBlocking {
        val file = image("uri-reference.jpg")
        val dao = db.noteDao()
        val old = dao.insertNote(note(file))
        dao.deleteNote(dao.getNoteByIdSync(old)!!)
        nextProcess()
        dao.insertNote(NoteEntity(imageUrisJson = JSONArray(listOf(file.toURI().toString())).toString()))
        NoteMediaManager.cleanOrphanedMedia(app, dao)
        assertTrue("An uncertain URI representation must retain recoverable media", file.exists())
    }

    @Test fun everyCredentialLabelAndThrowableAreRedactedBeforeLogcat() {
        for (label in listOf("api_key", "apikey", "access_token", "auth_token", "token", "passphrase", "mnemonic", "password", "PIN", "recoveryKey")) {
            val value = "SYNTHETIC_AUDIT_VALUE_ABC123"
            val raw = "$label=\"$value with spaces\""
            assertFalse(label, AppLogger.sanitize(raw).contains(value))
            AppLogger.e("PreReleasePrivacy", raw, IllegalStateException(raw))
            assertFalse(label, ShadowLog.getLogsForTag("PreReleasePrivacy").last().msg.contains(value))
            assertFalse(AppLogger.getRecentLogsSnapshot().last().contains(value))
        }
    }

    @Test fun fullHabitIdentitiesKeepNotificationsAndActionTargetsSeparate() {
        val manager = app.getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 33) shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        HabitReceiver.createHabitNotificationChannel(app)
        val intents = listOf(1L, 400001L, Long.MAX_VALUE).map { id ->
            val intent = Intent(app, HabitActionReceiver::class.java).setAction("audit")
                .setData(HabitNotifications.actionUri(id, "increment")).putExtra(HabitActionReceiver.EXTRA_HABIT_ID, id)
            val action = PendingIntent.getBroadcast(app, 1000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(app, HabitReceiver.CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Synthetic $id").addAction(0, "Done", action).build()
            HabitNotifications.post(app, manager, id, n)
            action
        }
        assertEquals(3, manager.activeNotifications.size)
        assertEquals(3, intents.toSet().size)
        assertEquals(1L, shadowOf(intents[0]).savedIntent.getLongExtra(HabitActionReceiver.EXTRA_HABIT_ID, -1))
        HabitNotifications.cancel(manager, 400001L)
        assertEquals(setOf(HabitNotifications.tag(1L), HabitNotifications.tag(Long.MAX_VALUE)), manager.activeNotifications.map { it.tag }.toSet())
    }

    @Test fun lockScreenDefaultsAreRedactedAndExistingPublicCopiesAreUpdated() {
        if (android.os.Build.VERSION.SDK_INT >= 33) shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(NotificationManager::class.java)
        HabitReceiver.createHabitNotificationChannel(app)
        val builder = NotificationCompat.Builder(app, HabitReceiver.CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Synthetic sensitive habit").setGroup(HabitReceiver.HABITS_GROUP_KEY)
        val private = ReminderPrivacy.configure(builder, app, HabitReceiver.CHANNEL_ID).build()
        assertEquals(Notification.VISIBILITY_PRIVATE, private.visibility)
        assertFalse(private.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("sensitive"))
        manager.notify("audit", 0, builder.setVisibility(Notification.VISIBILITY_PUBLIC).build())
        ReminderPrivacy.setAllowsContent(app, false)
        assertEquals(Notification.VISIBILITY_PRIVATE, manager.activeNotifications.single().notification.visibility)
        // Preferences can change through restore while Android retains an old public copy.
        manager.notify("audit", 0, builder.setVisibility(Notification.VISIBILITY_PUBLIC).build())
        ReminderPrivacy.refreshActive(app)
        assertEquals(Notification.VISIBILITY_PRIVATE, manager.activeNotifications.single().notification.visibility)
    }

    @Test fun optionalBatteryAndPrecisionAccessDoNotBlockBasicReadiness() {
        val state = DevicePermissionState(setOf(DevicePermission.USAGE, DevicePermission.OVERLAY, DevicePermission.NOTIFICATIONS))
        assertTrue(state.ready); assertTrue(state.remindersReady)
        assertFalse(state.preciseRemindersReady)
        assertTrue(state.backgroundReliabilityRecommended)
    }
    @Test fun taskNotificationTapsHaveAnActivityDestinationWithoutOverlayAccess() {
        val source = Intent(app, com.focusbyrj.app.service.TaskActionReceiver::class.java).putExtra("taskTitle", "Synthetic")
        val overlay = PendingIntent.getBroadcast(app, 4321, source, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(false)
        val first = TaskReminderTap.intent(app, 1L, source, overlay)
        val other = TaskReminderTap.intent(app, 400001L, source, overlay)
        assertTrue(shadowOf(first).isActivityIntent)
        assertNotEquals(first, other)
        assertEquals(1L, shadowOf(first).savedIntent.getLongExtra("taskId", -1L))
        val reschedule = TaskReminderTap.intent(app, 1L, source, overlay, reschedule = true)
        assertTrue(shadowOf(reschedule).savedIntent.getBooleanExtra("openReschedule", false))
        assertNotEquals(first, reschedule)
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        assertEquals(overlay, TaskReminderTap.intent(app, 1L, source, overlay))
    }

    @Test fun missingCredentialsCannotGenerateAReplacementKeyOverAuxiliaryRecoverySidecars() {
        DatabaseKeyProvider.clearCachedPassphrase()
        val prefs = app.getSharedPreferences("focus_notes_vault_security_prefs", 0)
        prefs.edit().clear().commit()
        val base = app.getDatabasePath("ayva_chat.db").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf()) }
        val wal = File(base.path + "-wal").apply { writeText("Synthetic recoverable database sidecar") }
        val before = wal.readBytes()
        try {
            try { DatabaseKeyProvider.getOrCreatePassphrase(app); fail("Missing credentials must not replace an existing store's key") }
            catch (_: IllegalStateException) { }
            assertArrayEquals(before, wal.readBytes())
            assertTrue(prefs.all.isEmpty())
        } finally { base.delete(); wal.delete(); DatabaseKeyProvider.clearCachedPassphrase() }
    }

    @Test fun malformedTextSpansCannotHideLaterValidBlockMediaFromCleanup() = runBlocking {
        val file = image("after-malformed-text.jpg")
        val dao = db.noteDao()
        val retired = dao.insertNote(note(file)); dao.deleteNote(dao.getNoteByIdSync(retired)!!)
        val blocks = org.json.JSONObject().put("blocks", JSONArray()
            .put(org.json.JSONObject().put("type", "text").put("text", "Synthetic").put("spans", JSONArray().put(org.json.JSONObject.NULL)))
            .put(org.json.JSONObject().put("type", "image").put("uri", file.path)))
        dao.insertNote(NoteEntity(content = com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager.BLOCKS_PREFIX + blocks.toString() + com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager.BLOCKS_SUFFIX))
        nextProcess()
        NoteMediaManager.cleanOrphanedMedia(app, dao)
        assertTrue("A lenient display-parser fallback must never erase ownership", file.exists())
    }

    @Test fun corruptionCallbackCannotDeleteAnExistingUserDatabase() {
        val name = "synthetic-corruption-guard.db"
        var captured: androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration? = null
        var destructiveCallbackCalled = false
        val framework = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        val spy = object : androidx.sqlite.db.SupportSQLiteOpenHelper.Factory {
            override fun create(configuration: androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration): androidx.sqlite.db.SupportSQLiteOpenHelper {
                captured = configuration
                return framework.create(configuration)
            }
        }
        val callback = object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE retained (content TEXT NOT NULL)")
                db.execSQL("INSERT INTO retained VALUES ('synthetic private row')")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected fixture upgrade")
            override fun onCorruption(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                destructiveCallbackCalled = true; app.deleteDatabase(name)
            }
        }
        val helper = com.focusbyrj.app.util.backup.MaintenanceOpenHelperFactory(spy).create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(app).name(name).callback(callback).build())
        try {
            val database = helper.writableDatabase
            val before = app.getDatabasePath(name).readBytes()
            try { captured!!.callback.onCorruption(database); fail("A corrupt store must fail closed") }
            catch (_: SecurityException) { }
            assertFalse(destructiveCallbackCalled)
            assertFalse(captured!!.allowDataLossOnRecovery)
            assertArrayEquals(before, app.getDatabasePath(name).readBytes())
            database.query("SELECT content FROM retained").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("synthetic private row", cursor.getString(0))
            }
        } finally { helper.close(); app.deleteDatabase(name) }
    }

}
