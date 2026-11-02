package com.focusbyrj.app.service

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class ReminderRecoveryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var work: WorkManager
    @Before fun prepare() {
        runCatching { WorkManager.initialize(context, Configuration.Builder().setMinimumLoggingLevel(android.util.Log.ERROR).build()) }
        work = WorkManager.getInstance(context)
        work.cancelUniqueWork("run_boot_reminders").result.get(5, TimeUnit.SECONDS)
        work.cancelUniqueWork("run_calendar_reminders").result.get(5, TimeUnit.SECONDS)
    }
    @After fun cleanup() {
        work.cancelUniqueWork("run_boot_reminders").result.get(5, TimeUnit.SECONDS)
        work.cancelUniqueWork("run_calendar_reminders").result.get(5, TimeUnit.SECONDS)
    }

    @Test fun rejectedBubbleStartCannotPreventBootAlarmRecoveryOrClearSnooze() = runBlocking {
        ShadowSettings.setCanDrawOverlays(true)
        val deadline = System.currentTimeMillis() + BubbleService.DEFAULT_SNOOZE_DURATION_MS
        context.getSharedPreferences("bubble_prefs", 0).edit().clear().putBoolean("bubble_enabled", true)
            .putLong(BubbleService.PREFS_KEY_SNOOZED_UNTIL, deadline).commit()
        context.getSharedPreferences("focus_prefs", 0).edit().putBoolean("isSessionActive", false).commit()
        var rejected = false
        val refusing = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun startForegroundService(intent: Intent): ComponentName? {
                if (intent.component?.className == BubbleService::class.java.name) {
                    rejected = true
                    throw SecurityException("Startup denied")
                }
                return super.startForegroundService(intent)
            }
        }
        BootReceiver().onReceive(refusing, Intent(Intent.ACTION_BOOT_COMPLETED))
        withTimeout(10_000L) {
            while (work.getWorkInfosForUniqueWork("run_boot_reminders").get().none { it.state != WorkInfo.State.CANCELLED }) delay(20)
        }
        assertTrue(rejected)
        assertEquals(deadline, context.getSharedPreferences("bubble_prefs", 0).getLong(BubbleService.PREFS_KEY_SNOOZED_UNTIL, 0))
    }

    @Test fun clockReconciliationDoesNotCancelPendingBootRestoration() {
        ReminderReconcileWorker.enqueue(context, true).result.get(5, TimeUnit.SECONDS)
        ReminderReconcileWorker.enqueue(context, false).result.get(5, TimeUnit.SECONDS)
        assertTrue(work.getWorkInfosForUniqueWork("run_boot_reminders").get().any { it.state != WorkInfo.State.CANCELLED })
        assertTrue(work.getWorkInfosForUniqueWork("run_calendar_reminders").get().any { it.state != WorkInfo.State.CANCELLED })
        val registered = context.packageManager.queryBroadcastReceivers(Intent(Intent.ACTION_TIMEZONE_CHANGED).setPackage(context.packageName), 0)
        assertTrue(registered.any { it.activityInfo.name == ReminderClockReceiver::class.java.name && !it.activityInfo.exported })
    }
}
