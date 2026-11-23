package com.focusbyrj.app.ui.navigation

import android.app.Application
import android.content.Intent
import com.focusbyrj.app.ui.screens.launchTabOptions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34], application = Application::class)
class MainNavigationTest {
    @Test
    fun everyLaunchChoiceIsARealDockDestination() {
        assertEquals(
            MainNavigation.tabs.map { it.route }.toSet(),
            launchTabOptions.map { it.route }.toSet(),
        )
        launchTabOptions.forEach { assertEquals(it.route, MainNavigation.launchTab(it.route)) }
        assertFalse(MainNavigation.tabs.contains(Screen.EmptyTab))
    }

    @Test
    fun legacyBlankAndUnknownDestinationsHaveSafeFallbacks() {
        assertEquals("time", MainNavigation.launchTab("empty_tab"))
        assertEquals("time", MainNavigation.destination("empty_tab"))
        assertEquals("dashboard", MainNavigation.launchTab("not_a_page"))
        assertNull(MainNavigation.destination("settings_data?restore=true"))
        assertEquals(
            "settings_automatic_backups",
            MainNavigation.destination("settings_automatic_backups"),
        )
    }

    @Test
    fun requestsAreConsumedOnceWithoutRemovingUnrelatedExtras() {
        val intent =
            Intent()
                .putExtra("open_new_note", true)
                .putExtra("as_checklist", true)
                .putExtra("keep", "value")
        val request = MainLaunchRequest.consume(intent)!!
        assertEquals("empty", request.route)
        assertEquals(true, request.newNote)
        assertNull(MainLaunchRequest.consume(intent))
        assertEquals("value", intent.getStringExtra("keep"))
    }

    @Test
    fun addWithoutARouteGoesStraightToTaskCreation() {
        val request = MainLaunchRequest.consume(Intent().putExtra("open_add_dialog", true))!!
        assertEquals("todos", request.route)
        assertTrue(request.openEditor)
    }

    @Test
    fun habitRequestsStayScopedToHabits() {
        val request =
            MainLaunchRequest.consume(
                Intent().putExtra("NAV_DESTINATION", "habits").putExtra("open_add_dialog", true)
            )!!
        assertEquals("habits", request.route)
        assertTrue(request.openEditor)
    }

    @Test
    fun noteRequestsCannotAlsoOpenATaskEditor() {
        val request =
            MainLaunchRequest.consume(
                Intent()
                    .putExtra("open_note_id", 71L)
                    .putExtra("open_add_dialog", true)
                    .putExtra("open_new_note", true)
            )!!
        assertEquals(71L, request.noteId)
        assertFalse(request.openEditor)
        assertNull(request.newNote)
        assertEquals("empty", request.route)
    }

    @Test
    fun plainNewNotesRetainTheirFalseChecklistValue() {
        assertEquals(
            false,
            MainLaunchRequest.consume(Intent().putExtra("open_new_note", true))!!.newNote,
        )
    }

    @Test
    fun voiceRequestsAreRoutedToThePermissionOwningScreen() {
        val request = MainLaunchRequest.consume(Intent().putExtra("open_voice_note", true))!!
        assertEquals("empty", request.route)
        assertTrue(request.voiceNote)
        assertFalse(request.openEditor)
    }

    @Test
    fun malformedAndUnknownExternalRoutesCannotBecomeStartDestinations() {
        val request =
            MainLaunchRequest.consume(Intent().putExtra("navigate_to", "removed_screen"))!!
        assertEquals("dashboard", request.route)
        assertTrue(request.invalidDestination)
        val malformed =
            MainLaunchRequest.consume(
                Intent().putExtra("navigate_to", 42).putExtra("open_note_id", -7L)
            )!!
        assertEquals("dashboard", malformed.route)
        assertNull(malformed.noteId)
    }

    @Test
    fun aNormalLauncherIntentIsNotAnEditorCommand() {
        assertNull(MainLaunchRequest.consume(Intent(Intent.ACTION_MAIN)))
        assertNull(MainLaunchRequest.consume(null))
    }
}
