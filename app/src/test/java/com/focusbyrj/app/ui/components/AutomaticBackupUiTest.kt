package com.focusbyrj.app.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.*
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.ui.screens.AutomaticBackupsScreen
import com.focusbyrj.app.ui.screens.RecoveryKeyExportDialog
import com.focusbyrj.app.ui.screens.security.RestoreBackupPasswordDialog
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import com.focusbyrj.app.util.AppThemeManager
import com.focusbyrj.app.util.ThemeMode
import com.focusbyrj.app.util.backup.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutomaticBackupUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    @Before fun setup() { AppThemeManager.init(app);AppThemeManager.setThemeMode(app,ThemeMode.OLED)
        android.provider.Settings.Global.putFloat(app.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        runCatching {androidx.work.WorkManager.getInstance(app)}.getOrElse {
            androidx.work.WorkManager.initialize(app,androidx.work.Configuration.Builder().build())
        }
    }
    private fun page() {compose.setContent{FocusByRjTheme {val nav=rememberNavController();NavHost(nav,startDestination="automatic") {composable("automatic"){AutomaticBackupsScreen(nav)}}}}}
    private fun capture(name:String) {
        compose.runOnIdle {
            val view=compose.activity.window.decorView;val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image));File("build/reports/automatic-backups/$name.png").apply{parentFile!!.mkdirs();outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}};image.recycle()
        }
    }
    @Test fun initialSetupClearlySeparatesKeyExportFromHavingABackupAndDisablesUnconfiguredSchedule() {
        page();compose.waitForIdle()
        compose.onNodeWithText("Not configured").assertExists()
        compose.onNodeWithText("Create recovery key").assertExists()
        compose.onNodeWithText("Last verified full backup: None yet").assertExists()
        capture("automatic-backup-setup")
        compose.onAllNodesWithText("Automatic backups",substring=false).onFirst().assertExists()
        assertFalse(AutomaticBackupStore(app).config().enabled)
    }
    @Test fun settingsKeepExportAcknowledgementAndValidateRetentionWithoutDeletingExistingFiles() {
        val store=AutomaticBackupStore(app,TestBackupWrapper())
        BackupRecoveryKey.generate().use{store.install(it)}
        store.save(store.config().copy(keyExported=true,keyAcknowledged=true,tree="content://test/tree/folder",keep=7))
        page();compose.waitForIdle();capture("automatic-backup-key-ready")
        compose.onNodeWithText("Keep 7 verified backups").performScrollTo().assertExists()
        compose.onAllNodesWithText("Set").onLast().performClick()
        compose.onNodeWithText("Maximum backups").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("3")
        compose.onNodeWithText("Save").performClick();compose.waitForIdle()
        compose.waitUntil(5000){store.config().keep==3}
        assertTrue(store.config().keyAcknowledged);assertTrue(store.records().isEmpty())
    }
    @Test fun calendarFrequencyOffersAllFourUnitsAndSavesTheSelectedInterval() {
        page();compose.waitForIdle()
        compose.onNodeWithText("Every 1 day").performScrollTo()
        compose.onAllNodesWithText("Set").onFirst().performClick()
        compose.onNodeWithText("Months").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("2")
        compose.onNodeWithText("Save").performClick();compose.waitForIdle()
        compose.waitUntil(5000){AutomaticBackupStore(app).config().frequency.unit=="MONTHS"}
        assertEquals(2,AutomaticBackupStore(app).config().frequency.every)
        compose.onNodeWithText("Every 2 months").assertExists()
    }
    @Test fun replacingAKeyCanBeCancelledWithoutChangingTheCredentialOrBackupSetup() {
        val store=AutomaticBackupStore(app,TestBackupWrapper());BackupRecoveryKey.generate().use{store.install(it)}
        val before=app.getSharedPreferences("automatic_full_backups_v1",0).all.toMap()
        page();compose.waitForIdle()
        compose.onNodeWithText("New key").performClick()
        compose.onNodeWithText("Create a new backup key?").assertExists()
        compose.onNodeWithText("Cancel").performClick();compose.waitForIdle()
        assertEquals(before,app.getSharedPreferences("automatic_full_backups_v1",0).all)
    }
    @Test fun newlyCreatedKeyPromptOffersExportAndKeepsFailuresVisibleForRetry() {
        var busy by mutableStateOf(false);var error by mutableStateOf<String?>(null);var visible by mutableStateOf(true)
        var selected:String?=null
        compose.setContent{FocusByRjTheme{if(visible)RecoveryKeyExportDialog(busy,error,
            onExportPdf={selected="pdf";busy=true},onExportFile={selected="file"},onDismiss={visible=false})}}
        compose.onNodeWithText("Save your recovery key").assertExists()
        compose.onNodeWithText("Export PDF").performClick();assertEquals("pdf",selected)
        compose.onNodeWithText("Later").assertIsNotEnabled();compose.onNodeWithText("Key file").assertIsNotEnabled()
        compose.runOnIdle{busy=false;error="Destination unavailable. Choose another folder."}
        compose.onNodeWithText("Destination unavailable. Choose another folder.").assertExists()
        compose.onNodeWithText("Key file").performClick();assertEquals("file",selected)
        compose.onNodeWithText("Save your recovery key").assertExists()
        compose.onNodeWithText("Later").performClick();compose.onNodeWithText("Save your recovery key").assertDoesNotExist()
    }
    @Test fun returningFromTheExportPickerRequiresVaultUnlockBeforeAnyKeyIsWritten() {
        val store=AutomaticBackupStore(app,TestBackupWrapper());BackupRecoveryKey.generate().use{store.install(it)}
        val prefs=app.getSharedPreferences(com.focusbyrj.app.data.note.ArchiveVaultSecurity.PREFS_NAME,0)
        prefs.edit().putString(com.focusbyrj.app.data.note.ArchiveVaultSecurity.KEY_STATUS,"enabled").commit()
        com.focusbyrj.app.data.note.ArchiveVaultSecurity.lockVault()
        try {
            page();compose.waitForIdle()
            compose.onAllNodesWithText("Export").onFirst().performClick()
            val activity=org.robolectric.Shadows.shadowOf(compose.activity)
            val picker=activity.nextStartedActivityForResult
            assertNotNull(picker);assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT,picker.intent.action)
            compose.runOnIdle{activity.receiveResult(picker.intent,android.app.Activity.RESULT_OK,android.content.Intent().setData(android.net.Uri.parse("content://unavailable/recovery-key.pdf")))}
            compose.onNodeWithText("Enter 6-digit passcode to unlock").assertExists()
            assertFalse(store.config().keyExported);assertFalse(store.config().enabled)
        } finally {prefs.edit().clear().commit();com.focusbyrj.app.data.note.ArchiveVaultSecurity.lockVault()}
    }
    @Test fun importingADifferentKeyRequiresConfirmationAndCancelPreservesTheCurrentCredential() {
        val store=AutomaticBackupStore(app,TestBackupWrapper());BackupRecoveryKey.generate().use{store.install(it)}
        val before=app.getSharedPreferences("automatic_full_backups_v1",0).all.toMap()
        page();compose.waitForIdle()
        compose.onNodeWithText("Enter").performClick()
        BackupRecoveryKey.generate().use {candidate ->
            compose.onNode(hasSetTextAction()).performTextReplacement(candidate.credential())
            compose.onNodeWithText("Use key").performClick()
        }
        compose.onNodeWithText("Use a different backup key?").assertExists()
        assertEquals(before,app.getSharedPreferences("automatic_full_backups_v1",0).all)
        compose.onNodeWithText("Cancel").performClick();compose.waitForIdle()
        assertEquals(before,app.getSharedPreferences("automatic_full_backups_v1",0).all)
    }
    @Test fun restoreDialogAcceptsAnExportedRecoveryKeyAndStillPreservesExactManualPasswords() {
        var visible by mutableStateOf(true);var supplied:String?=null
        BackupRecoveryKey.generate().use {key ->
            compose.setContent{FocusByRjTheme {if(visible)RestoreBackupPasswordDialog(false,{visible=false}){password,_->supplied=password;visible=false}}}
            compose.onAllNodes(isToggleable()).onFirst().performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement(key.credential().replace("-"," \n"))
            compose.onNodeWithText("Decrypt & Restore").performClick();compose.waitForIdle()
            assertEquals(key.credential(),supplied)
        }
        supplied=null;visible=true;compose.waitForIdle()
        val password="  Exact manual password  "
        compose.onNode(hasSetTextAction()).performTextReplacement(password)
        compose.onNodeWithText("Decrypt & Restore").performClick();compose.waitForIdle()
        assertEquals(password,supplied)
    }
    @Test fun revocationNeedsConfirmationAndExplainsExistingArchiveAccess() {
        val store = AutomaticBackupStore(app, TestBackupWrapper())
        BackupRecoveryKey.generate().use { store.install(it) }
        store.authorizeVault(ByteArray(32) { it.toByte() })
        page()
        compose.onNodeWithText("Revoke").performScrollTo().performClick()
        compose.onNodeWithText("Revoke private-note backup access?").assertExists()
        compose.onNodeWithText("Existing backup files will be kept", substring = true).assertExists()
        assertTrue(store.hasVaultAuthorization())
        compose.onNodeWithText("Keep access").performClick()
        assertTrue(store.hasVaultAuthorization())
        compose.onNodeWithText("Revoke").performScrollTo().performClick()
        compose.onNodeWithText("Revoke access").performClick()
        compose.waitUntil(5000) { !store.hasVaultAuthorization() }
        assertNull(store.staged())
    }

}
