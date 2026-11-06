package com.focusbyrj.app.ui.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.ui.screens.BackupFlow
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FocusApplication::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ManualBackupUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<FocusApplication>()
    @Before fun setup() {
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        ArchiveVaultSecurity.lockVault()
        // Robolectric recreates Application between methods, while AndroidX's
        // default factory can retain the earlier one. Keep the ViewModel and
        // fixture databases/files on the same current test application.
        compose.activityRule.scenario.onActivity { activity ->
            val model = androidx.lifecycle.ViewModelProvider(activity,
                androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app))[
                com.focusbyrj.app.ui.screens.ManualBackupViewModel::class.java]
            assertSame(app, model.getApplication<android.app.Application>())
        }
    }
    @After fun cleanup() {
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        ArchiveVaultSecurity.lockVault()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        File(app.noBackupFilesDir, "manual_backup_staging").deleteRecursively()
    }
    private fun page() = compose.setContent {
        FocusByRjTheme { BackupFlow { actions -> Button(onClick = actions.export, enabled = !actions.busy) { Text("Start manual backup") } } }
    }
    private fun captureDialog(name: String) {
        // PixelCopy requires device redraw callbacks unavailable in this headless runner.
        // Draw the actual dialog root with native Canvas, as the existing UI captures do.
        compose.runOnIdle {
            val manager = org.robolectric.util.ReflectionHelpers.callStaticMethod<Any>(
                Class.forName("android.view.WindowManagerGlobal"), "getInstance")
            val roots = org.robolectric.util.ReflectionHelpers.getField<List<android.view.View>>(manager, "mViews")
            val dialog = roots.last { it !== compose.activity.window.decorView }
            val bitmap = android.graphics.Bitmap.createBitmap(dialog.width, dialog.height, android.graphics.Bitmap.Config.ARGB_8888)
            dialog.draw(android.graphics.Canvas(bitmap))
            File("build/reports/manual-backups/$name.png").apply {
                parentFile!!.mkdirs()
                outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            bitmap.recycle()
        }
    }

    @Test fun standalonePasswordDialogCanBeEnteredAndCancelled() {
        var dismissed = false
        compose.setContent { FocusByRjTheme {
            com.focusbyrj.app.ui.screens.security.ExportBackupPasswordDialog(false, { dismissed = true }, {})
        } }
        compose.onNodeWithText("Backup Password").performTextInput("test-password")
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-hdpi")
    fun passwordEntryAndConfirmationRemainReachableOnSmallScreensWithLargeText() {
        var supplied: String? = null
        compose.setContent { FocusByRjTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                com.focusbyrj.app.ui.screens.security.ExportBackupPasswordDialog(false, {}, { supplied = it })
            }
        } }
        compose.onNodeWithText("Backup Password").performScrollTo().performTextInput("long-manual-test-password")
        compose.onNodeWithText("Confirm Password").performScrollTo().performTextInput("long-manual-test-password")
        captureDialog("manual-password-small-large-text")
        compose.onNodeWithText("Prepare encrypted backup").performClick()
        assertEquals("long-manual-test-password", supplied)
    }

    @Test fun vaultAuthenticationIsInlineAndCancellingPasswordEntryRelocksIt() {
        assertTrue(ArchiveVaultSecurity.setPasscode(app, "123456")); ArchiveVaultSecurity.lockVault()
        page()
        compose.onNodeWithText("Start manual backup").performClick()
        compose.onNodeWithText("Unlock for this backup").assertIsDisplayed()
        "123456".forEach { compose.onNodeWithText(it.toString()).performClick() }
        compose.onNodeWithText("Set manual backup password").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        compose.onNodeWithText("Start manual backup").assertIsEnabled()
    }

    @Test fun repeatedConfirmationBeforeRecompositionPreparesOneCompletePrivateBackup() {
        val notes = com.focusbyrj.app.data.note.NoteDatabase.getInstance(app).noteDao()
        assertTrue(ArchiveVaultSecurity.setPasscode(app, "123456"))
        val key = ArchiveVaultSecurity.getActiveVaultSubKey()!!
        val original = try {
            com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(
                com.focusbyrj.app.data.note.NoteEntity(id = 91234, title = "Private backup fixture", content = "Keep original data", isArchived = true), key)
        } finally { key.fill(0) }
        kotlinx.coroutines.runBlocking { notes.insertNote(original) }
        ArchiveVaultSecurity.lockVault()
        try {
            page()
            compose.onNodeWithText("Start manual backup").performClick()
            "123456".forEach { compose.onNodeWithText(it.toString()).performClick() }
            compose.onNodeWithText("Backup Password").performTextInput("test-manual-password")
            compose.onNodeWithText("Confirm Password").performTextInput("test-manual-password")
            val click = compose.onNodeWithText("Prepare encrypted backup").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            compose.runOnIdle { assertTrue(click()); assertTrue(click()) }
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Encrypted backup ready").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Backup Not Created").assertDoesNotExist()
            assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
            kotlinx.coroutines.runBlocking { assertEquals(original, notes.getNoteByIdSync(91234)) }
            assertEquals(1, File(app.noBackupFilesDir, "manual_backup_staging").listFiles().orEmpty().size)
            compose.onNodeWithText("Discard").performClick()
            assertTrue(File(app.noBackupFilesDir, "manual_backup_staging").listFiles().orEmpty().isEmpty())
        } finally { kotlinx.coroutines.runBlocking { notes.deleteNote(original) } }
    }

    @Test fun thePickerOnlyOpensAfterPreparationAndCancellationNeverReportsASave() {
        page()
        compose.onNodeWithText("Start manual backup").performClick()
        compose.onNodeWithText("Backup Password").performTextInput("test-manual-password")
        compose.onNodeWithText("Confirm Password").performTextInput("test-manual-password")
        assertNull(shadowOf(compose.activity).nextStartedActivityForResult)
        compose.onNodeWithText("Prepare encrypted backup").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Encrypted backup ready").fetchSemanticsNodes().isNotEmpty() }
        captureDialog("manual-backup-ready")
        assertNull(shadowOf(compose.activity).nextStartedActivityForResult)
        compose.onNodeWithText("Choose save location").performClick()
        val picker = shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull(picker); assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.intent.action)
        compose.runOnIdle { compose.activity.activityResultRegistry.dispatchResult(picker.requestCode, Activity.RESULT_CANCELED, null) }
        compose.onNodeWithText("Backup Not Created").assertExists()
        compose.onNodeWithText("Save cancelled.", substring = true).assertExists()
        compose.onNodeWithText("Backup Saved Successfully").assertDoesNotExist()
        assertTrue(File(app.noBackupFilesDir, "manual_backup_staging").listFiles().orEmpty().isEmpty())

        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Start manual backup").performClick()
        compose.onNodeWithText("Backup Password").performTextInput("new-manual-password")
        compose.onNodeWithText("Confirm Password").performTextInput("new-manual-password")
        compose.onNodeWithText("Prepare encrypted backup").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Encrypted backup ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Choose save location").performClick()
        val newerPicker = shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull(newerPicker)
        assertNotEquals("A newer archive must not reuse the older result registration", picker.requestCode, newerPicker.requestCode)
        compose.runOnIdle { compose.activity.activityResultRegistry.dispatchResult(picker.requestCode, Activity.RESULT_OK,
            Intent().setData(Uri.parse("content://old-picker/must-not-be-written"))) }
        compose.onNodeWithText("Backup Not Created").assertDoesNotExist()
        compose.onNodeWithText("Backup Saved Successfully").assertDoesNotExist()
        compose.runOnIdle { compose.activity.activityResultRegistry.dispatchResult(newerPicker.requestCode, Activity.RESULT_CANCELED, null) }
        compose.onNodeWithText("Save cancelled.", substring = true).assertExists()
        assertTrue(File(app.noBackupFilesDir, "manual_backup_staging").listFiles().orEmpty().isEmpty())
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-hdpi")
    fun aVerifiedSaveCanBeDismissedAndRestartedWithLargeText() {
        val destination = File(app.cacheDir, "manual-ui-export.focusbackup")
        val provider = object : android.content.ContentProvider() {
            override fun onCreate() = true
            override fun openFile(uri: Uri, mode: String) = android.os.ParcelFileDescriptor.open(
                destination, android.os.ParcelFileDescriptor.parseMode(mode))
            override fun getType(uri: Uri) = "application/octet-stream"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): android.database.Cursor? = null
            override fun insert(uri: Uri, values: android.content.ContentValues?): Uri? = null
            override fun update(uri: Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        provider.attachInfo(app, android.content.pm.ProviderInfo().apply { authority = "run.manual.ui.tests" })
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("run.manual.ui.tests", provider)
        try {
            compose.setContent { FocusByRjTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                    BackupFlow { actions -> Button(onClick = actions.export, enabled = !actions.busy) { Text("Start manual backup") } }
                }
            } }
            compose.onNodeWithText("Start manual backup").performClick()
            compose.onNodeWithText("Backup Password").performScrollTo().performTextInput("test-manual-password")
            compose.onNodeWithText("Confirm Password").performScrollTo().performTextInput("test-manual-password")
            compose.onNodeWithText("Prepare encrypted backup").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Encrypted backup ready").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Choose save location").performClick()
            val picker = shadowOf(compose.activity).nextStartedActivityForResult
            assertNotNull(picker)
            compose.runOnIdle { compose.activity.activityResultRegistry.dispatchResult(picker.requestCode, Activity.RESULT_OK,
                Intent().setData(Uri.parse("content://run.manual.ui.tests/backup"))) }
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Backup Saved Successfully").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Your backup was saved and read back successfully", substring = true).performScrollTo()
            compose.onNodeWithText("Done").assertIsDisplayed()
            captureDialog("manual-backup-saved-small-large-text")
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithText("Backup Not Created").assertDoesNotExist()
            compose.onNodeWithText("Start manual backup").assertIsEnabled().performClick()
            compose.onNodeWithText("Set manual backup password").assertIsDisplayed()
            assertTrue(destination.length() > 0)
            assertTrue(File(app.noBackupFilesDir, "manual_backup_staging").listFiles().orEmpty().isEmpty())
        } finally { destination.delete() }
    }
}
