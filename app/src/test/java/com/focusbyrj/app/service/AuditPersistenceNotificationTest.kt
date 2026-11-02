package com.focusbyrj.app.service

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.focusbyrj.app.R
import com.focusbyrj.app.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class AuditPersistenceNotificationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @Before fun prepare() {
        context.getSharedPreferences("aptitude_economy_prefs", 0).edit().clear().commit()
        FocusEconomyManager.init(context)
        DailyQuestManager.init(context)
        AptitudeManager.init(context)
        context.getSharedPreferences("focus_prefs", 0).edit().putString("task_notification_style", "Notification").commit()
        manager.cancelAll()
    }

    @Test fun drillProgressSurvivesReloadIncludingStreakAndCounts() {
        AptitudeManager.recordDrillResult(30, 5, 3)
        AptitudeManager.refreshProfile()
        assertEquals(30, AptitudeManager.profileFlow.value.xp)
        val prefs = context.getSharedPreferences("aptitude_economy_prefs", 0)
        assertEquals(1, prefs.getInt("total_drills", 0))
        assertEquals(5, prefs.getInt("total_questions", 0))
        assertEquals(3, prefs.getInt("correct_questions", 0))
        assertEquals(1, prefs.getInt("current_streak", 0))
        AptitudeManager.recordDrillResult(20, 2, 2)
        AptitudeManager.init(context)
        assertEquals(50, AptitudeManager.profileFlow.value.xp)
        assertEquals(2, prefs.getInt("total_drills", 0))
        assertEquals(1, prefs.getInt("current_streak", 0))
    }

    @Test fun taskPostsAndCancellationPreserveServicesAndFullLongIds() = runBlocking {
        manager.createNotificationChannel(NotificationChannel("service", "Service", 2))
        listOf(1001, 2001, 4001).forEach { id ->
            manager.notify(id, NotificationCompat.Builder(context, "service").setSmallIcon(R.drawable.ic_app_logo)
                .setContentTitle("Service $id").build())
        }
        val ids = listOf(1001L, 2001L, 4001L, 1001L + (1L shl 32))
        val receiver = TaskReminderReceiver()
        val filter = android.content.IntentFilter("run.test.TASK_REMINDER")
        if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
        try {
            for (id in ids) {
                context.sendBroadcast(Intent("run.test.TASK_REMINDER").setPackage(context.packageName)
                    .putExtra("taskId", id).putExtra("taskTitle", "Task $id"))
                shadowOf(android.os.Looper.getMainLooper()).idle()
                val pending = shadowOf(receiver).originalPendingResult
                org.robolectric.shadow.api.Shadow.extract<org.robolectric.shadows.ShadowBroadcastPendingResult>(pending)
                    .future.get(10, java.util.concurrent.TimeUnit.SECONDS)
            }
        } finally { context.unregisterReceiver(receiver) }
        ids.forEach { id ->
            val task = manager.activeNotifications.single { it.tag == TaskNotifications.tag(id) }
            assertEquals(id, shadowOf(task.notification.contentIntent).savedIntent.getLongExtra("taskId", -1))
        }
        assertNotEquals(
            manager.activeNotifications.single { it.tag == TaskNotifications.tag(ids.first()) }.notification.contentIntent,
            manager.activeNotifications.single { it.tag == TaskNotifications.tag(ids.last()) }.notification.contentIntent)
        ids.forEach { TaskNotifications.cancel(context, it) }
        listOf(1001, 2001, 4001).forEach { id ->
            assertEquals("Service $id", manager.activeNotifications.single { it.id == id && it.tag == null }
                .notification.extras.getString(NotificationCompat.EXTRA_TITLE))
        }
        TaskReminderHelper.cleanUpTaskSummaryNotification(context)
        assertEquals(3, manager.activeNotifications.size)
    }

    @Test fun legacyTaskIsRetiredWithoutCancellingAnUnrelatedNotification() {
        manager.createNotificationChannel(NotificationChannel("task_reminders", "Tasks", 3))
        val task = NotificationCompat.Builder(context, "task_reminders").setSmallIcon(R.drawable.ic_app_logo)
            .setGroup(TaskReminderReceiver.TASKS_GROUP_KEY).setContentTitle("Old task").build()
        manager.notify(24, task)
        TaskNotifications.post(manager, 24, task)
        assertFalse(manager.activeNotifications.any { it.tag == null && it.id == 24 })
        manager.notify(24, NotificationCompat.Builder(context, "task_reminders").setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle("Unrelated").build())
        TaskNotifications.cancel(context, 24)
        assertEquals("Unrelated", manager.activeNotifications.single().notification.extras.getString(NotificationCompat.EXTRA_TITLE))
    }
}
