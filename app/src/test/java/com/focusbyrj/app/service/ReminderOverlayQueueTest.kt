package com.focusbyrj.app.service

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.Habit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSettings
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34, 35, 36], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class ReminderOverlayQueueTest {
    private lateinit var context: Context
    private val main get() = shadowOf(Looper.getMainLooper())

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ShadowSettings.setCanDrawOverlays(true)
        TaskReminderOverlayManager.hideOverlay(context)
        HabitFloatingOverlayManager.dismissOverlay()
        main.idleFor(Duration.ofSeconds(1))
        UnifiedOverlayCoordinator.clearQueue()
        main.idle()
    }

    @After fun cleanup() {
        TaskReminderOverlayManager.hideOverlay(context)
        HabitFloatingOverlayManager.dismissOverlay()
        main.idleFor(Duration.ofSeconds(1))
        UnifiedOverlayCoordinator.clearQueue()
        main.idle()
    }

    private fun task(id: Long) {
        TaskReminderOverlayManager.showReminderOverlay(context, id, "Task $id", "Details", 1L,
            "TASK", "NONE", false)
    }
    private fun currentTask(): Long = TaskReminderOverlayManager::class.java.getDeclaredField("currentTaskId")
        .apply { isAccessible = true }.getLong(TaskReminderOverlayManager)
    private fun currentHabit(): Long = HabitFloatingOverlayManager::class.java.getDeclaredField("currentHabitId")
        .apply { isAccessible = true }.getLong(HabitFloatingOverlayManager)

    @Test fun simultaneousTasksWaitUntilEachPopupIsDismissed() {
        task(1); task(2); task(3)
        main.idleFor(Duration.ofSeconds(3))
        assertEquals("Creating a popup must not release its queue slot", 1L, currentTask())
        TaskReminderOverlayManager.hideOverlay(context)
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(2L, currentTask())
        TaskReminderOverlayManager.hideOverlay(context)
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(3L, currentTask())
    }

    @Test fun mixedHabitAndTaskRemindersUseOneFifo() {
        task(1)
        HabitFloatingOverlayManager.showHabitOverlay(context, Habit(id = 2, title = "Read"), 0, 1)
        task(3)
        main.idleFor(Duration.ofSeconds(2))
        assertEquals(1L, currentTask())
        assertFalse(HabitFloatingOverlayManager.isShowing)
        TaskReminderOverlayManager.hideOverlay(context)
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(2L, currentHabit())
        assertFalse(TaskReminderOverlayManager.isShowing)
        HabitFloatingOverlayManager.dismissOverlay()
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(3L, currentTask())
    }
    private fun activeToken(): Long = UnifiedOverlayCoordinator::class.java.getDeclaredField("activePresentationId")
        .apply { isAccessible = true }.get(UnifiedOverlayCoordinator) as Long

    @Test fun popupRemainsAttachedUntilUserActs() {
        task(1); task(2)
        main.idleFor(Duration.ofSeconds(20))
        val view = TaskReminderOverlayManager::class.java.getDeclaredField("overlayView")
            .apply { isAccessible = true }.get(TaskReminderOverlayManager) as android.view.View
        assertNotNull("WindowManager must actually own the displayed view", view.parent)
        assertEquals(1L, currentTask())
    }

    @Test fun burstsLargerThanTenDoNotDiscardReminders() {
        (1L..25L).forEach { task(it) }
        main.idle()
        for (id in 1L..25L) {
            assertEquals("Every reminder must be shown in arrival order", id, currentTask())
            TaskReminderOverlayManager.hideOverlay(context)
            main.idleFor(Duration.ofSeconds(1))
        }
        assertFalse(UnifiedOverlayCoordinator.isAnyOverlayShowing)
    }

    @Test fun duplicateAlarmsDoNotRepeatActiveOrWaitingReminders() {
        task(1); task(2); task(1); task(2); task(3)
        main.idle()
        for (id in 1L..3L) {
            assertEquals(id, currentTask())
            TaskReminderOverlayManager.hideOverlay(context)
            main.idleFor(Duration.ofSeconds(1))
        }
        assertFalse(UnifiedOverlayCoordinator.isAnyOverlayShowing)
    }

    @Test fun arrivalDuringTransitionStaysBehindAlreadyWaitingReminder() {
        task(1); task(2); main.idle()
        TaskReminderOverlayManager.hideOverlay(context)
        main.idle()
        task(3)
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(2L, currentTask())
        TaskReminderOverlayManager.hideOverlay(context)
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(3L, currentTask())
    }

    @Test fun repeatedDismissalsAdvanceExactlyOnce() {
        task(1); task(2); task(3); main.idle()
        repeat(5) { TaskReminderOverlayManager.hideOverlay(context) }
        main.idleFor(Duration.ofSeconds(2))
        assertEquals(2L, currentTask())
    }

    @Test fun lateCallbackFromOldWindowCannotDismissNewWindow() {
        task(1); task(2); task(3); main.idle()
        val oldToken = activeToken()
        TaskReminderOverlayManager.hideOverlay(context)
        main.idleFor(Duration.ofSeconds(1))
        UnifiedOverlayCoordinator.onOverlayDismissed(context, oldToken)
        main.idleFor(Duration.ofSeconds(2))
        assertEquals(2L, currentTask())
    }

    @Test fun ignoringWaitingTaskDoesNotCloseUnrelatedActiveTask() {
        task(1); task(2); task(3); main.idle()
        com.focusbyrj.app.util.TaskReminderHelper.ignoreTask(context, 2)
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(1L, currentTask())
        TaskReminderOverlayManager.hideOverlay(context)
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(3L, currentTask())
    }

    @Test fun cancellingWaitingHabitDoesNotCloseActiveHabit() {
        (1L..3L).forEach {
            HabitFloatingOverlayManager.showHabitOverlay(context, Habit(id = it, title = "Habit $it"), 0, 1)
        }
        main.idle()
        HabitFloatingOverlayManager.dismissHabit(2)
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(1L, currentHabit())
        HabitFloatingOverlayManager.dismissHabit(1)
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(3L, currentHabit())
    }

    @Test fun taskAndHabitWithSameNumericIdAreDifferentReminders() {
        task(1)
        HabitFloatingOverlayManager.showHabitOverlay(context, Habit(id = 1, title = "Read"), 0, 1)
        main.idle()
        TaskReminderOverlayManager.hideOverlay(context)
        main.idleFor(Duration.ofSeconds(1))
        assertTrue(HabitFloatingOverlayManager.isShowing)
        assertEquals(1L, currentHabit())
    }

    @Test fun simultaneousBackgroundEnqueuesAreSerializedWithoutLoss() {
        val threads = (1L..20L).map { id -> Thread { task(id) }.apply { start() } }
        threads.forEach { it.join() }
        main.idle()
        val seen = mutableSetOf<Long>()
        repeat(20) {
            assertTrue("No duplicate popup", seen.add(currentTask()))
            TaskReminderOverlayManager.hideOverlay(context)
            main.idleFor(Duration.ofSeconds(1))
        }
        assertEquals((1L..20L).toSet(), seen)
    }

    @Test fun missingOverlayPermissionDoesNotBlockFollowingRemindersAndRetainsThemForRetry() {
        ShadowSettings.setCanDrawOverlays(false)
        task(1); task(2); task(3)
        main.idleFor(Duration.ofSeconds(4))
        assertFalse(UnifiedOverlayCoordinator.isAnyOverlayShowing)
        assertEquals(listOf(1L, 2L, 3L), ReminderOverlayStore(context).read().map { it.id })
        assertNull("Background Activity launches cannot guarantee sequential popups",
            shadowOf(context as Application).nextStartedActivity)
    }

    @Test fun clearingQueueCancelsPendingPresentationBeforeItCanOpen() {
        task(1); task(2)
        android.os.Handler(Looper.getMainLooper()).post { UnifiedOverlayCoordinator.clearQueue() }
        main.idleFor(Duration.ofSeconds(2))
        assertFalse(UnifiedOverlayCoordinator.isAnyOverlayShowing)
        assertTrue(ReminderOverlayStore(context).read().isEmpty())
    }

    @Test fun rescheduleActionForWaitingTaskPreservesItsPositionAndOpensEditor() {
        task(1); task(2); task(3)
        main.idle()
        TaskReminderOverlayManager.showReminderOverlay(context, 2, "Task 2", "Details", 1L,
            "TASK", "NONE", false, openRescheduleInitially = true)
        main.idle()
        assertEquals(1L, currentTask())
        TaskReminderOverlayManager.hideOverlay(context)
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(2L, currentTask())
        val editor = TaskReminderOverlayManager::class.java.getDeclaredField("rescheduleView")
            .apply { isAccessible = true }.get(TaskReminderOverlayManager) as android.view.View
        assertEquals(android.view.View.VISIBLE, editor.visibility)
    }

    @Test fun rescheduleActionForActiveTaskOpensEditorWithoutDuplicatingPopup() {
        task(1); task(2); main.idle()
        TaskReminderOverlayManager.showReminderOverlay(context, 1, "Task 1", "Details", 1L,
            "TASK", "NONE", false, openRescheduleInitially = true)
        main.idle()
        val editor = TaskReminderOverlayManager::class.java.getDeclaredField("rescheduleView")
            .apply { isAccessible = true }.get(TaskReminderOverlayManager) as android.view.View
        assertEquals(android.view.View.VISIBLE, editor.visibility)
        TaskReminderOverlayManager.hideOverlay(context)
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(2L, currentTask())
    }

    @Test fun unexpectedWindowRemovalReleasesQueueAndRetainsInterruptedReminder() {
        task(1); task(2); main.idle()
        val view = TaskReminderOverlayManager::class.java.getDeclaredField("overlayView")
            .apply { isAccessible = true }.get(TaskReminderOverlayManager) as android.view.View
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        wm.removeViewImmediate(view)
        main.idleFor(Duration.ofSeconds(2))
        assertEquals(2L, currentTask())
        assertEquals(setOf(1L, 2L), ReminderOverlayStore(context).read().map { it.id }.toSet())
    }

    @Test fun clearingAnOpenWindowAllowsFreshReminderImmediately() {
        task(1); main.idle()
        UnifiedOverlayCoordinator.clearQueue()
        task(2)
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(2L, currentTask())
    }

    @Test fun backButtonDismissesOnlyCurrentPopupAndShowsNext() {
        task(1); task(2); main.idle()
        val view = TaskReminderOverlayManager::class.java.getDeclaredField("overlayView")
            .apply { isAccessible = true }.get(TaskReminderOverlayManager) as android.view.View
        view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(2L, currentTask())
    }

    @Test fun cancellationWaitsForActualHabitExitWithSlowSystemAnimations() {
        val scale = android.animation.ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }
        scale.invoke(null, 10f)
        try {
            HabitFloatingOverlayManager.showHabitOverlay(context, Habit(id = 1, title = "Read"), 0, 1)
            task(2)
            main.idleFor(Duration.ofMillis(300))
            HabitFloatingOverlayManager.dismissHabit(1)
            main.idleFor(Duration.ofMillis(350))
            assertFalse("Next popup must wait for the current window to finish exiting", TaskReminderOverlayManager.isShowing)
            main.idleFor(Duration.ofSeconds(10))
            assertEquals(2L, currentTask())
        } finally {
            scale.invoke(null, 1f)
        }
    }

}
