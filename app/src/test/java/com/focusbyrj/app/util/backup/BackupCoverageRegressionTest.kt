package com.focusbyrj.app.util.backup

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.FocusSchedule
import com.focusbyrj.app.data.chat.ActiveDrillStateEntity
import com.focusbyrj.app.data.chat.AyvaChatDatabase
import com.focusbyrj.app.data.note.*
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** Portable settings, IDs and active-state recovery regressions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FocusApplication::class)
class BackupCoverageRegressionTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusApplication>()
    private val notes get() = NoteDatabase.getInstance(app).noteDao()
    private val password = "synthetic-coverage-probe-password"
    @Before fun setup() = runBlocking {
        notes.deleteAllNotes()
        app.database.scheduleDao().deleteAllSchedules()
        ArchiveVaultSecurity.lockVault()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME,0).edit().clear().commit()
        for (name in listOf("ayva_personalization", "profile_pinned_notes_prefs", "note_widget_prefs"))
            app.getSharedPreferences(name,0).edit().clear().commit()
        AyvaChatDatabase.getDatabase(app).chatDao().clearActiveDrills()
    }
    private suspend fun archive() = File(app.cacheDir,"coverage-${UUID.randomUUID()}.fbck").also {
        BackupRestoreManager.createEncryptedBackup(app,Uri.fromFile(it),password).getOrThrow()
    }
    private fun image(text:String) = File(app.filesDir,"keep_images/${UUID.randomUUID()}.jpg").also {
        EncryptedMediaStorage.writeEncryptedBytes(it,text.toByteArray())
    }

    @Test fun personalizationProfilePinsAndDisabledScheduleSurviveSnapshotRestore() = runBlocking {
        val preferences=app.getSharedPreferences("ayva_personalization",0)
        preferences.edit().putString("name","Backed up name").putString("tone","direct").putBoolean("quiet",true).putInt("focus_minutes",45).commit()
        notes.insertNote(NoteEntity(id=42,title="Pinned public note"))
        app.getSharedPreferences("focus_economy_prefs",0).edit().putString("user_name","Backup profile").commit()
        app.getSharedPreferences("aptitude_economy_prefs",0).edit().putInt("aptitude_xp",350).commit()
        app.getSharedPreferences("ayva_alert_color_prefs",0).edit().putBoolean("chat_updates_enabled",false).commit()
        app.getSharedPreferences("profile_pinned_notes_prefs",0).edit().putStringSet("profile_pinned_ids",setOf("42")).commit()
        app.database.scheduleDao().insertSchedule(FocusSchedule(name="Disabled schedule",startHour=9,startMinute=0,endHour=10,endMinute=0,daysOfWeek="2",isEnabled=false))
        val file=archive()
        BackupRestoreManager.prepareRestore(app,Uri.fromFile(file),password).getOrThrow().use { prepared ->
            val manifest=requireNotNull(prepared.manifest)
            val prefs=manifest.getJSONObject("preferences")
            assertTrue(prefs.has("focus_economy_prefs"))
            assertTrue(prefs.has("aptitude_economy_prefs"))
            assertTrue(prefs.has("ayva_personalization"))
            assertTrue(prefs.has("profile_pinned_notes_prefs"))
            assertFalse(manifest.getJSONArray("schedules").getJSONObject(0).getBoolean("isEnabled"))
            assertFalse(prefs.getJSONObject("ayva_alert_color_prefs").getBoolean("chat_updates_enabled"))
        }
        preferences.edit().putString("name","Current local name").putBoolean("quiet",false).putInt("focus_minutes",20).commit()
        app.getSharedPreferences("profile_pinned_notes_prefs",0).edit().clear().commit()
        app.getSharedPreferences("focus_economy_prefs",0).edit().putString("user_name","Current profile").commit()
        app.getSharedPreferences("aptitude_economy_prefs",0).edit().putInt("aptitude_xp",25).commit()
        app.getSharedPreferences("ayva_alert_color_prefs",0).edit().putBoolean("chat_updates_enabled",true).commit()
        BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(file),password,true).getOrThrow()
        assertEquals("Backed up name",preferences.getString("name",null))
        assertTrue(preferences.getBoolean("quiet",false))
        assertEquals(45,preferences.getInt("focus_minutes",0))
        assertEquals(setOf("42"),app.getSharedPreferences("profile_pinned_notes_prefs",0).getStringSet("profile_pinned_ids",emptySet()))
        assertEquals("Backup profile",app.getSharedPreferences("focus_economy_prefs",0).getString("user_name",null))
        assertEquals(350,app.getSharedPreferences("aptitude_economy_prefs",0).getInt("aptitude_xp",0))
        assertFalse(app.getSharedPreferences("ayva_alert_color_prefs",0).getBoolean("chat_updates_enabled",true))
        assertFalse("A paused schedule must remain paused",app.database.scheduleDao().getAllSchedulesSync().single().isEnabled)
    }

    @Test fun mergeRemapsNoteMediaWidgetAndProfilePinLinks() = runBlocking {
        val original=image("Original backed up image")
        notes.insertNote(NoteEntity(id=42,title="Backup note",imageUrisJson=JSONArray(listOf(original.path)).toString()))
        app.getSharedPreferences("profile_pinned_notes_prefs",0).edit().putStringSet("profile_pinned_ids",setOf("42")).commit()
        val widget=app.getSharedPreferences("note_widget_prefs",0)
        widget.edit().putString("note_filter_mode_17","SPECIFIC").putLong("note_specific_id_17",42).commit()
        val file=archive()
        notes.insertNote(NoteEntity(id=42,title="Newer current note"))
        BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(file),password,false).getOrThrow()
        val restored=notes.getAllNotesList().single { it.title=="Backup note" }
        assertNotEquals(42L,restored.id)
        val restoredImage=File(JSONArray(restored.imageUrisJson).getString(0))
        assertNotEquals(original.path,restoredImage.path)
        assertEquals("Original backed up image",EncryptedMediaStorage.readDecryptedBytes(restoredImage)!!.decodeToString())
        assertEquals(restored.id,widget.getLong("note_specific_id_17",-1))
        assertEquals(setOf("42",restored.id.toString()),app.getSharedPreferences("profile_pinned_notes_prefs",0).getStringSet("profile_pinned_ids",emptySet()))
        assertEquals("Backup note",notes.getNoteByIdSync(widget.getLong("note_specific_id_17",-1))!!.title)
        assertEquals("Newer current note",notes.getNoteByIdSync(42)!!.title)
    }

    @Test fun deletedOriginalImageCanBeRecoveredAndNewRowsDoNotReuseDeletedIds() = runBlocking {
        val original=image("Bytes inside the portable backup")
        val old=notes.insertNote(NoteEntity(title="Backup note",imageUrisJson=JSONArray(listOf(original.path)).toString()))
        val file=archive()
        notes.hardDeleteNotesByIds(listOf(old))
        val later=notes.insertNote(NoteEntity(title="Created after deletion"))
        assertTrue(later>old)
        assertTrue(original.delete())
        BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(file),password,true).getOrThrow()
        val restored=notes.getAllNotesList().single()
        assertEquals(old,restored.id)
        val restoredImage=File(JSONArray(restored.imageUrisJson).getString(0))
        assertNotEquals(original.path,restoredImage.path)
        assertEquals("Bytes inside the portable backup",EncryptedMediaStorage.readDecryptedBytes(restoredImage)!!.decodeToString())
        val next=notes.insertNote(NoteEntity(title="New after restore"))
        assertTrue(next>later)
    }

    @Test fun replacementEndsTheLocalActiveDrill() = runBlocking {
        val file=archive()
        val dao=AyvaChatDatabase.getDatabase(app).chatDao()
        val id=UUID.randomUUID().toString()
        dao.saveActiveDrill(ActiveDrillStateEntity(sessionId=id,difficulty="easy"))
        BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(file),password,true).getOrThrow()
        assertNull(dao.getLatestActiveDrill())
    }

    @Test fun widgetsFromAnotherInstallationAreDisconnectedInsteadOfTargetingUnrelatedRows() = runBlocking {
        notes.insertNote(NoteEntity(id=42,title="Backed up note"))
        val prefs=app.getSharedPreferences("note_widget_prefs",0)
        prefs.edit().putLong("note_specific_id_17",42).putString("note_filter_mode_17","SPECIFIC").commit()
        val file=archive()
        app.getSharedPreferences("widget_binding_origin",0).edit().putString("id",UUID.randomUUID().toString()).commit()
        prefs.edit().putLong("note_specific_id_17",99).putLong("note_current_id_17",99).commit()
        val result=BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(file),password,true).getOrThrow()
        assertEquals(-1L,prefs.getLong("note_specific_id_17",-1))
        assertFalse(prefs.contains("note_current_id_17"))
        assertTrue(result.warnings.any { "Reconnect" in it })
    }

    @Test fun lateRestoreFailureRecoversTheActiveDrillAndPersonalization() = runBlocking {
        val generation=com.focusbyrj.app.data.drill.DrillSessionRepository.storageGeneration
        val file=archive()
        val dao=AyvaChatDatabase.getDatabase(app).chatDao()
        val id=UUID.randomUUID().toString()
        dao.saveActiveDrill(ActiveDrillStateEntity(sessionId=id,difficulty="easy"))
        app.getSharedPreferences("ayva_personalization",0).edit().putString("name","Current identity").commit()
        try {
            RestoreJournal.checkpointHook={ if(it=="preferences") error("Synthetic late failure") }
            assertTrue(BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(file),password,true).isFailure)
        } finally { RestoreJournal.checkpointHook=null }
        assertEquals(id,dao.getLatestActiveDrill()?.sessionId)
        assertEquals(generation,com.focusbyrj.app.data.drill.DrillSessionRepository.storageGeneration)
        assertEquals("Current identity",app.getSharedPreferences("ayva_personalization",0).getString("name",null))
    }

    @Test fun aPreRestoreDrillCannotSettleRewardsIntoTheRestoredProfile() = runBlocking {
        val session=com.focusbyrj.app.ui.screens.DrillSession(difficulty="easy",correct=5,total=5,gold=500)
        val file=archive()
        BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(file),password,true).getOrThrow()
        com.focusbyrj.app.data.drill.DrillSessionRepository.init(app)
        val before=app.getSharedPreferences("focus_economy_prefs",0).all.toMap()
        assertTrue(runCatching { com.focusbyrj.app.util.DrillSummaryHelper.generateSummaryMessage(session) }.isFailure)
        assertEquals(before,app.getSharedPreferences("focus_economy_prefs",0).all)
        assertNull(com.focusbyrj.app.data.drill.DrillDatabase.getDatabase(app).drillSessionDao().getSessionById(session.sessionId))
    }

    @Test fun unrelatedMaintenanceDoesNotInvalidateAnOngoingDrill() = runBlocking {
        val generation=com.focusbyrj.app.data.drill.DrillSessionRepository.storageGeneration
        RestoreJournal.mutate(app) { Result.success(Unit) }.getOrThrow()
        assertEquals(generation,com.focusbyrj.app.data.drill.DrillSessionRepository.storageGeneration)
    }

    @Test fun lateFailureReconcilesAssistantAlarmsToRecoveredPreferencesInBothDirections() = runBlocking {
        val alarms = org.robolectric.Shadows.shadowOf(app.getSystemService(android.app.AlarmManager::class.java))
        fun hasAlarm(action: String) = alarms.scheduledAlarms.any {
            org.robolectric.Shadows.shadowOf(it.operation).savedIntent.action == action
        }
        val morning = com.focusbyrj.app.service.DailySummaryReceiver.ACTION_MORNING_SUMMARY
        val practice = com.focusbyrj.app.service.AptitudeReminderReceiver.ACTION_EXACT_DRILL
        app.getSharedPreferences("bubble_prefs",0).edit().putBoolean("vacation_mode",false)
            .putString("streak_notification_time","06:00 PM").commit()
        com.focusbyrj.app.util.AyvaAlertCategory.BRIEFING.setEnabled(app,true)
        com.focusbyrj.app.util.AyvaAlertCategory.DRILL_PRACTICE.setEnabled(app,true)
        for (enabled in listOf(true,false)) {
            com.focusbyrj.app.util.AyvaAlertCategory.setUpdatesEnabled(app,!enabled)
            val file = archive()
            com.focusbyrj.app.util.AyvaAlertCategory.setUpdatesEnabled(app,enabled)
            assertEquals(enabled,hasAlarm(morning)); assertEquals(enabled,hasAlarm(practice))
            var reached = false
            try {
                RestoreJournal.checkpointHook = { phase -> if (phase == "before_commit") {
                    reached = true
                    assertEquals(!enabled,hasAlarm(morning)); assertEquals(!enabled,hasAlarm(practice))
                    throw java.io.IOException("Synthetic post-scheduling restore failure")
                } }
                assertTrue(BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(file),password,true).isFailure)
            } finally { RestoreJournal.checkpointHook = null }
            assertTrue(reached)
            assertEquals(enabled,com.focusbyrj.app.util.AyvaAlertCategory.updatesEnabled(app))
            assertEquals(enabled,hasAlarm(morning)); assertEquals(enabled,hasAlarm(practice))
        }
    }

    @Test fun replacingAnEmptyInboxDoesNotKeepAnOldUnreadBadge() = runBlocking {
        AyvaChatDatabase.getDatabase(app).chatDao().clearAllMessages()
        val file=archive()
        app.getSharedPreferences("bubble_chat_prefs",0).edit().putInt("unread_message_count",17).commit()
        BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(file),password,true).getOrThrow()
        assertEquals(0,com.focusbyrj.app.util.BubbleChatManager.getUnreadCount(app))
    }
    private suspend fun editedArchive(edit:(org.json.JSONObject)->Unit):File {
        val source=archive()
        val root=BackupRestoreManager.prepareRestore(app,Uri.fromFile(source),password).getOrThrow().use {
            org.json.JSONObject(checkNotNull(it.manifest).toString())
        }
        edit(root)
        return File(app.cacheDir,"edited-${UUID.randomUUID()}.fbck").also { file ->
            file.outputStream().use { raw ->
                CryptoBackupEngine.openEncryptingStream(raw,password.toCharArray()).use { encrypted ->
                    java.util.zip.ZipOutputStream(encrypted).use { zip ->
                        zip.putNextEntry(java.util.zip.ZipEntry("data.json"));zip.write(root.toString().toByteArray());zip.closeEntry()
                    }
                }
            }
        }
    }

    @Test fun olderBackupsKeepMissingAssistantChoicesAndRestoreUnknownSchedulesPaused()=runBlocking {
        app.database.scheduleDao().insertSchedule(FocusSchedule(name="Older schedule",startHour=9,startMinute=0,endHour=10,endMinute=0,daysOfWeek="2",isEnabled=true))
        val file=editedArchive { root ->
            root.remove("widgetBindingOrigin")
            root.getJSONArray("schedules").getJSONObject(0).remove("isEnabled")
            val preferences=root.getJSONObject("preferences")
            preferences.remove("ayva_personalization");preferences.remove(PortableNoteSettings.PROFILE_PINS)
        }
        app.getSharedPreferences("ayva_personalization",0).edit().putString("name","Current identity").commit()
        val result=BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(file),password,true).getOrThrow()
        assertFalse(app.database.scheduleDao().getAllSchedulesSync().single().isEnabled)
        assertEquals("Current identity",app.getSharedPreferences("ayva_personalization",0).getString("name",null))
        assertTrue(result.warnings.any { "paused" in it })
        assertTrue(result.warnings.any { "preferences" in it })
    }

    @Test fun invalidPersonalizationTypesAreRejectedBeforeReplacingCurrentNotes()=runBlocking {
        notes.insertNote(NoteEntity(id=42,title="Preserve current note"))
        val file=editedArchive { root ->
            root.getJSONObject("preferences").getJSONObject("ayva_personalization").put("quiet","true")
            root.getJSONObject("preferenceTypes").getJSONObject("ayva_personalization").put("quiet","string")
            root.getJSONArray("notes").getJSONObject(0).put("title","Must not be applied")
        }
        assertTrue(BackupRestoreManager.restoreEncryptedBackup(app,Uri.fromFile(file),password,true).isFailure)
        assertEquals("Preserve current note",notes.getNoteByIdSync(42)!!.title)
    }

}
