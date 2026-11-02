package com.focusbyrj.app.ui.components

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.util.DevicePermission
import com.focusbyrj.app.util.DevicePermissionState
import com.focusbyrj.app.util.PermissionUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31,34,35,36], application=Application::class)
class DevicePermissionModelTest {
    @Test fun notificationReadinessMatchesTheRealNotificationManagerAndSharedModel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager=app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(manager).setNotificationsEnabled(false)
        val state = DevicePermissionState.read(app)
        assertFalse(PermissionUtils.hasNotificationPermission(app))
        assertFalse(DevicePermission.NOTIFICATIONS in state.granted)
        assertFalse(state.remindersReady)
        assertEquals(state.ready, PermissionUtils.areAllPermissionsGranted(app))
    }
    @Test fun exactAlarmAvailabilityIsAccountedForOnlyWhereApplicable() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val state = DevicePermissionState.read(app)
        if (Build.VERSION.SDK_INT >= 31) {
            assertFalse(DevicePermission.ALARMS in state.granted)
            assertFalse(state.ready)
            assertTrue(DevicePermission.ALARMS.applicable)
        } else {
            assertTrue(DevicePermission.ALARMS in state.granted)
            assertFalse(DevicePermission.ALARMS.applicable)
        }
    }
}
