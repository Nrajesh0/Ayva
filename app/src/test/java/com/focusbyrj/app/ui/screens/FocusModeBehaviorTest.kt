package com.focusbyrj.app.ui.screens

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.FocusSessionManager
import com.focusbyrj.app.util.FocusStatsManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class FocusModeBehaviorTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Application
    private lateinit var notifications: NotificationManager

    @Before fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        FocusSessionManager.stop(context)
        context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        FocusStatsManager.init(context)
        FocusEconomyManager.init(context)
        notifications = context.getSystemService(NotificationManager::class.java)
    }

    @After fun finish() {
        FocusSessionManager.stop(context)
    }

    @Test fun dashboardStartsWithoutNotificationPolicyAccess() {
        shadowOf(notifications).setNotificationPolicyAccessGranted(false)
        var started = false
        compose.setContent {
            MaterialTheme {
                DeepWorkCard(timeRemaining = 1500L, onToggleSession = { started = true }, onSetTime = {})
            }
        }
        compose.onNodeWithText("Start").performClick()
        compose.runOnIdle { assertTrue(started) }
        assertFalse(notifications.isNotificationPolicyAccessGranted)
    }

    @Test fun startingAndStoppingFocusPreserveUserChosenDnd() {
        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        FocusSessionManager.start(context, 1)
        assertTrue(FocusSessionManager.active.value)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, notifications.currentInterruptionFilter)
        FocusSessionManager.stop(context)
        assertFalse(FocusSessionManager.active.value)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, notifications.currentInterruptionFilter)
    }

    @Test fun timerExpiryPreservesUserChosenDnd() {
        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        FocusSessionManager.start(context, 1)
        context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE).edit()
            .putLong("focus_end_elapsed_ms", SystemClock.elapsedRealtime() - 1).commit()
        FocusSessionManager.restore(context)
        assertFalse(FocusSessionManager.active.value)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, notifications.currentInterruptionFilter)
    }
}
