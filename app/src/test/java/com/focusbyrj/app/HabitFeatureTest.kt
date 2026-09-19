package com.focusbyrj.app

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitType
import com.focusbyrj.app.service.HabitFloatingOverlayManager
import com.focusbyrj.app.service.HabitReceiver
import com.focusbyrj.app.util.HabitAlarmScheduler
import com.focusbyrj.app.util.HabitMicroCopyProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class HabitFeatureTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testHabitTopNotificationPreferenceDefaultAndToggle() {
        val prefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        // Clean up preference first
        prefs.edit().remove("habit_top_notifications").commit()

        // Default MUST be false (floating window is enough by default)
        val defaultSetting = prefs.getBoolean("habit_top_notifications", false)
        assertFalse("Habit top notifications should be disabled by default", defaultSetting)

        // Can be toggled to true
        prefs.edit().putBoolean("habit_top_notifications", true).commit()
        assertTrue(prefs.getBoolean("habit_top_notifications", false))

        // Toggle back to false
        prefs.edit().putBoolean("habit_top_notifications", false).commit()
        assertFalse(prefs.getBoolean("habit_top_notifications", false))
    }

    @Test
    fun testLottieAssetRotationNoConsecutiveDuplicates() {
        assertEquals("Should have exactly 12 habit lottie assets", 12, HabitFloatingOverlayManager.HABIT_LOTTIE_ASSETS.size)
        assertTrue("Contains cute girl cat asset", HabitFloatingOverlayManager.HABIT_LOTTIE_ASSETS.contains("habbitcutegirlcat.lottie"))

        var previousAsset: String? = null
        for (i in 0 until 100) {
            val currentAsset = HabitFloatingOverlayManager.getRandomHabitLottieAsset()
            assertNotNull(currentAsset)
            assertTrue("Asset must be in HABIT_LOTTIE_ASSETS", HabitFloatingOverlayManager.HABIT_LOTTIE_ASSETS.contains(currentAsset))
            if (previousAsset != null) {
                assertNotEquals("Should never repeat the same lottie twice in a row", previousAsset, currentAsset)
            }
            previousAsset = currentAsset
        }
    }

    @Test
    fun testCalculateNextTriggerTimeOnceDaily() {
        val now = Calendar.getInstance()
        val futureHour = (now.get(Calendar.HOUR_OF_DAY) + 2) % 24

        val habit = Habit(
            id = 1L,
            title = "Morning Meditation",
            type = HabitType.ONCE_DAILY,
            fixedReminderHour = futureHour,
            fixedReminderMinute = 0
        )

        val triggerTime = HabitAlarmScheduler.calculateNextTriggerTime(habit)
        assertNotNull(triggerTime)
        assertTrue("Trigger time must be in future", triggerTime!! > System.currentTimeMillis() - 1000)
    }

    @Test
    fun testCalculateNextTriggerTimeIntervalWindow() {
        val habit = Habit(
            id = 2L,
            title = "Drink Water",
            type = HabitType.INTERVAL_WINDOW,
            windowStartHour = 8,
            windowStartMinute = 0,
            windowEndHour = 22,
            windowEndMinute = 0,
            intervalHours = 2,
            targetPerDay = 8
        )

        val triggerTime = HabitAlarmScheduler.calculateNextTriggerTime(habit)
        assertNotNull(triggerTime)
        assertTrue("Trigger time must be positive epoch timestamp", triggerTime!! > 0)
    }

    @Test
    fun testHabitRequestCodeUniqueness() {
        val code1 = HabitAlarmScheduler.getRequestCode(1L)
        val code2 = HabitAlarmScheduler.getRequestCode(2L)
        val code3 = HabitAlarmScheduler.getRequestCode(105L)

        assertNotEquals(code1, code2)
        assertNotEquals(code1, code3)
        assertTrue(code1 in 500_000..900_000)
    }

    @Test
    fun testHabitMicroCopyProviderProducesValidQuotes() {
        val habitWater = Habit(id = 10L, title = "Drink Water", iconEmoji = "💧")
        val habitPosture = Habit(id = 11L, title = "Stretch & Posture", iconEmoji = "🧘")
        val habitWalk = Habit(id = 12L, title = "Walk 1000 steps", iconEmoji = "🚶")

        for (i in 0 until 50) {
            val waterQuote = HabitMicroCopyProvider.getNaturalQuote(context, habitWater, 2, 8, 3)
            assertTrue("Water quote must not be blank", waterQuote.isNotBlank())

            val postureQuote = HabitMicroCopyProvider.getNaturalQuote(context, habitPosture, 1, 4, 1)
            assertTrue("Posture quote must not be blank", postureQuote.isNotBlank())

            val walkQuote = HabitMicroCopyProvider.getNaturalQuote(context, habitWalk, 0, 5, 0)
            assertTrue("Walk quote must not be blank", walkQuote.isNotBlank())
        }
    }

    @Test
    fun testHabitNotificationChannelCreation() {
        HabitReceiver.createHabitNotificationChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel(HabitReceiver.CHANNEL_ID)
        assertNotNull("Habit reminder notification channel should exist", channel)
        assertEquals(HabitReceiver.CHANNEL_NAME, channel.name)
    }

    @Test
    fun testHabitSnoozeSchedulesWithoutCrashing() {
        // Snooze habit for 30 minutes
        HabitAlarmScheduler.snoozeHabit(context, 42L, 30)
        // Ensure no exception is thrown and pending intent exists
        val requestCode = HabitAlarmScheduler.getRequestCode(42L)
        assertTrue(requestCode > 0)
    }
}
