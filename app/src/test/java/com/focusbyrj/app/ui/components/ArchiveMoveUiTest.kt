package com.focusbyrj.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.note.*
import com.focusbyrj.app.ui.screens.notes.*
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FocusApplication::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArchiveMoveUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<FocusApplication>()
    private val dao get() = NoteDatabase.getInstance(app).noteDao()
    private lateinit var model: NotesViewModel
    private lateinit var store: androidx.lifecycle.ViewModelStore
    @Before fun setup() = runBlocking {
        dao.deleteAllNotes(); NotesViewModel.latestNotesCache.clear()
        com.focusbyrj.app.util.crypto.VaultArchiveDeposit.disable(app)
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        assertTrue(ArchiveVaultSecurity.setPasscode(app, "123456")); ArchiveVaultSecurity.lockVault()
        model = NotesViewModel(app)
        store = androidx.lifecycle.ViewModelStore().also { it.put("archive-ui", model) }
    }
    @After fun cleanup() = runBlocking {
        store.clear(); ArchiveVaultSecurity.lockVault(); NotesViewModel.latestNotesCache.clear()
        com.focusbyrj.app.util.crypto.VaultArchiveDeposit.disable(app)
        dao.deleteAllNotes()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        Unit
    }

    @Test fun inlinePinStaysInEditorAndNavigationWaitsForVerifiedSave() {
        val note = NoteEntity(id = 82001, title = "Archive fixture", content = "Keep content")
        runBlocking { dao.insertNote(note) }
        model.openExistingNote(note); model.setReturnRouteOnClose("profile")
        val routes = mutableListOf<String>()
        compose.setContent { FocusByRjTheme { NotesScreen(model, onNavigateToRoute = { routes += it }) } }
        compose.runOnIdle { model.archiveCurrentNote() }
        compose.onNodeWithText("Unlock to archive").assertIsDisplayed()
        assertTrue(routes.isEmpty()); assertNotNull(model.editingState.value)
        "123456".forEach { compose.onNodeWithText(it.toString()).performClick() }
        try {
            compose.waitUntil(30_000) {
                // A semantics query drains Robolectric's paused UI queue, like the backup UI fixtures.
                compose.onAllNodesWithText("Moving to Archive").fetchSemanticsNodes()
                runBlocking { dao.getNoteByIdSync(note.id)?.isArchived == true } && routes.size == 1
            }
        } catch (error: Throwable) {
            throw AssertionError("Archive phase=${model.archiveMove.value.phase}, message=${model.archiveMove.value.message}, editorOpen=${model.editingState.value != null}, routes=${routes.size}, storedArchived=${runBlocking { dao.getNoteByIdSync(note.id)?.isArchived }}", error)
        }
        compose.runOnIdle {
            assertEquals(listOf("profile"), routes)
            assertNull(model.editingState.value)
            assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
            assertTrue(runBlocking { VaultPayloadEncryptor.isVaultEncrypted(dao.getNoteByIdSync(note.id)!!) })
        }
        compose.onNodeWithText("Unlock to archive").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-hdpi")
    fun cancellingInlineUnlockOnSmallScreenKeepsDraftAndDoesNotNavigate() {
        model.openNewNote(returnRoute = "profile"); model.updateEditorContent("Draft survives cancellation")
        val routes = mutableListOf<String>()
        compose.setContent { FocusByRjTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                NotesScreen(model, onNavigateToRoute = { routes += it })
            }
        } }
        compose.runOnIdle { model.archiveCurrentNote() }
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Draft survives cancellation", model.editingState.value!!.content)
            assertTrue(routes.isEmpty()); assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        }
        compose.onNodeWithText("Unlock to archive").assertDoesNotExist()
    }
    @Test fun settingsCanChooseBothArchiveMethodsWithoutRemovingViewProtection() {
        compose.setContent { FocusByRjTheme {
            com.focusbyrj.app.ui.screens.PrivateVaultSettingsScreen(androidx.navigation.compose.rememberNavController(), model)
        } }
        compose.onNodeWithContentDescription("Ask for PIN when archiving").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithText("Allow archiving without PIN").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ask for PIN when archiving").assertIsOn().performClick()
        "123456".forEach { compose.onNodeWithText(it.toString()).performClick() }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Updating archive option").fetchSemanticsNodes()
            model.archiveWithoutPin.value && !model.archiveOption.value.busy
        }
        compose.onNodeWithContentDescription("Ask for PIN when archiving").assertIsOff()
        compose.onNodeWithText("Viewing and unarchiving always need your PIN.").assertExists()
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        compose.onNodeWithContentDescription("Ask for PIN when archiving").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Updating archive option").fetchSemanticsNodes()
            !model.archiveWithoutPin.value && !model.archiveOption.value.busy
        }
        compose.onNodeWithContentDescription("Ask for PIN when archiving").assertIsOn()
        compose.onNodeWithText("Allow archiving without PIN").assertDoesNotExist()
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
    }

}
