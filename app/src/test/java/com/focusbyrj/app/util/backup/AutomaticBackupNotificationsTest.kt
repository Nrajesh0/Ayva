package com.focusbyrj.app.util.backup

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomaticBackupNotificationsTest {
    @Test fun foregroundProgressUsesRealAttachmentCountsAndAnIndeterminateFallback() {
        val notifications = AutomaticBackupNotifications(ApplicationProvider.getApplicationContext<Context>())
        assertTrue(notifications.isEnabled(BackupNotificationOption.PROGRESS))
        assertFalse(notifications.isEnabled(BackupNotificationOption.COMPLETION))
        assertTrue(notifications.isEnabled(BackupNotificationOption.FAILURE))

        val preparing = notifications.progress(42, "Preparing backup")
        assertTrue(preparing.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        val checking = notifications.progress(42, "Checking attachment 2 of 7")
        assertFalse(checking.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(2, checking.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals(7, checking.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertTrue(checking.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)

        notifications.setEnabled(BackupNotificationOption.PROGRESS, false)
        val foreground = notifications.progress(42, "Checking attachment 2 of 7")
        assertEquals("Automatic backup is running.", foreground.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(0, foreground.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertTrue(foreground.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }
}
