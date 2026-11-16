package com.focusbyrj.app.ui.tours

import android.app.Application
import androidx.compose.ui.geometry.Rect
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TourPreferencesTest {
    private lateinit var app: Application
    private lateinit var store: TourPreferences

    @Before
    fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("app_tours", 0).edit().clear().commit()
        store = TourPreferences(app)
    }

    @Test
    fun freshInstallNeedsAnExplicitChoice() {
        store.initialize(existingInstall = false)
        assertTrue(store.welcomePending)
        assertFalse(store.automatic)
        assertFalse(store.eligible(AppTours.navigation))
        store.chooseWelcome(true)
        assertTrue(store.eligible(AppTours.navigation))
        assertFalse(store.eligible(AppTours.tasks))
        store.finish(AppTours.navigation)
        assertTrue(store.eligible(AppTours.tasks))
        assertFalse(store.eligible(AppTours.focus))
    }

    @Test
    fun existingInstallDoesNotSuddenlyShowTours() {
        store.initialize(existingInstall = true)
        assertFalse(store.welcomePending)
        assertFalse(store.eligible(AppTours.navigation))
        store.initialize(existingInstall = false)
        assertFalse(store.welcomePending)
    }

    @Test
    fun exploringMyselfDisablesAutomaticToursAcrossRestarts() {
        store.initialize(false)
        store.chooseWelcome(false)
        val restarted = TourPreferences(app)
        assertFalse(restarted.welcomePending)
        assertFalse(restarted.automatic)
        assertFalse(restarted.eligible(AppTours.notes))
    }

    @Test
    fun completionAndSkipPersistButExplicitReplayStillWorks() {
        store.setAutomatic(true)
        store.finish(AppTours.notes)
        assertFalse(TourPreferences(app).eligible(AppTours.notes))
        store.replay(AppTours.notes)
        assertTrue(store.eligible(AppTours.notes))
        assertFalse(store.eligible(AppTours.profile))
        val serial = store.replaySerial
        store.finish(AppTours.notes)
        assertNull(store.replayId)
        assertFalse(store.eligible(AppTours.notes))
        store.replay(AppTours.notes)
        assertTrue(store.replaySerial > serial)
    }

    @Test
    fun replayDoesNotTurnOnUnwantedFutureHints() {
        store.chooseWelcome(false)
        store.replay(AppTours.backups)
        assertTrue(store.eligible(AppTours.backups))
        assertFalse(store.automatic)
        store.finish(AppTours.backups)
        assertFalse(store.eligible(AppTours.tasks))
    }

    @Test
    fun cancellingReplayDoesNotMarkAnUnseenTourComplete() {
        store.replay(AppTours.automatic)
        store.cancelReplay()
        assertFalse(store.done(AppTours.automatic.id))
        assertNull(store.replayId)
    }

    @Test
    fun switchingHintsOffPreservesCompletedTours() {
        store.setAutomatic(true)
        store.finish(AppTours.tasks)
        store.setAutomatic(false)
        assertFalse(store.eligible(AppTours.notes))
        store.setAutomatic(true)
        assertFalse(store.eligible(AppTours.tasks))
        assertTrue(store.eligible(AppTours.notes))
        assertFalse(store.welcomePending)
    }

    @Test
    fun replayingTasksAlsoMakesTheDateTipAvailable() {
        store.setAutomatic(true)
        store.finish(AppTours.taskDates)
        store.replay(AppTours.tasks)
        store.finish(AppTours.tasks)
        assertTrue(store.eligible(AppTours.taskDates))
    }

    @Test
    fun tourStateDoesNotChangeAppDataOrSecurityPreferences() {
        val prefs = app.getSharedPreferences("focus_prefs", 0)
        prefs
            .edit()
            .putBoolean("secure_recents", true)
            .putString("test_payload", "retained")
            .commit()
        val before = prefs.all.toMap()
        store.initialize(false)
        store.chooseWelcome(true)
        AppTours.replayable.forEach {
            store.replay(it)
            store.finish(it)
        }
        store.setAutomatic(false)
        assertEquals(before, prefs.all)
    }

    @Test
    fun placementUsesTheSideWithRoomForLargeText() {
        assertEquals(
            TourCardSpace(true, 596),
            tourCardSpace(Rect(20f, 50f, 100f, 160f), 800, 40, 32, 12),
        )
        assertEquals(
            TourCardSpace(false, 648),
            tourCardSpace(Rect(20f, 700f, 100f, 750f), 800, 40, 32, 12),
        )
    }

    @Test
    fun placementNeverReturnsNegativeHeightOnTinyScreens() {
        assertEquals(0, tourCardSpace(Rect(0f, 0f, 100f, 200f), 200, 30, 30, 12).maxHeight)
    }

    @Test
    fun replayCatalogOnlyHasKnownStableDestinationsAndShortSteps() {
        assertEquals(AppTours.replayable.size, AppTours.replayable.map { it.id }.toSet().size)
        AppTours.replayable.forEach { tour ->
            assertTrue(tour.steps.size in 1..3)
            assertTrue(tour.steps.all { it.title.length <= 40 && it.explanation.length <= 180 })
        }
    }
}
