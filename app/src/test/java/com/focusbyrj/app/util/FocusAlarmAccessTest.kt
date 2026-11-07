package com.focusbyrj.app.util

import android.app.AlarmManager
import android.app.Application
import com.focusbyrj.app.service.FocusSessionEndReceiver
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34, 36], application = Application::class)
class FocusAlarmAccessTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val alarms get() = app.getSystemService(AlarmManager::class.java)
    @Before fun setup() {
        app.getSharedPreferences("focus_prefs", 0).edit().clear().commit()
        FocusStatsManager.init(app)
        FocusEconomyManager.init(app)
    }
    @After fun cleanup() { FocusSessionManager.stop(app) }

    @Test fun restoringAfterAccessChangesPreservesDeadlineDurationAndTaskAssociation() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        FocusSessionManager.start(app, 25, taskId = 42L)
        val prefs = app.getSharedPreferences("focus_prefs", 0)
        val wallDeadline = prefs.getLong("focus_end_wall_ms", 0)
        val elapsedDeadline = prefs.getLong("focus_end_elapsed_ms", 0)
        for (access in listOf(false, true, false)) {
            // Android cancels exact alarms on access revocation. Simulate cancellation
            // separately from recovery, so a stale alarm cannot make this assertion pass.
            val prior = shadowOf(alarms).scheduledAlarms.single {
                shadowOf(it.operation).savedIntent.component?.className == FocusSessionEndReceiver::class.java.name
            }
            alarms.cancel(checkNotNull(prior.operation))
            ShadowAlarmManager.setCanScheduleExactAlarms(access)
            FocusSessionManager.restore(app)
            val restored = shadowOf(alarms).scheduledAlarms.single {
                shadowOf(it.operation).savedIntent.component?.className == FocusSessionEndReceiver::class.java.name
            }
            assertEquals(access, restored.windowLengthMs == 0L)
            assertTrue(restored.isAllowWhileIdle)
            assertEquals(wallDeadline, prefs.getLong("focus_end_wall_ms", 0))
            assertEquals(elapsedDeadline, prefs.getLong("focus_end_elapsed_ms", 0))
            assertEquals(1500L, FocusSessionManager.initialSeconds.value)
            assertEquals(42L, FocusSessionManager.taskId(app))
            assertTrue(FocusSessionManager.active.value)
        }
    }
}
