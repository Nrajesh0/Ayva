package com.focusbyrj.app.service

import android.app.AlarmManager
import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34, 36], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
class BubbleRecoveryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val controllers = mutableListOf<ServiceController<BubbleService>>()
    private val main get() = shadowOf(Looper.getMainLooper())

    @Before fun prepare() {
        BubbleService.isChatOpen = false
        context.getSharedPreferences("bubble_prefs", 0).edit().clear().putBoolean("bubble_enabled", true).commit()
        ShadowSettings.setCanDrawOverlays(true)
    }
    @After fun cleanup() {
        BubbleService.releaseTemporaryHide(context, "one")
        BubbleService.releaseTemporaryHide(context, "two")
        main.idle()
        controllers.reversed().forEach { it.destroy() }
        BubbleService.isChatOpen = false
    }
    private fun create(): BubbleService = Robolectric.buildService(BubbleService::class.java).create()
        .also { controllers.add(it); main.idle() }.get()
    private fun added(service: BubbleService): Boolean = ReflectionHelpers.getField(service, "isBubbleAdded")
    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action).setPackage(context.packageName)); main.idle()
    }

    @Test fun dismissStillReturnsAfterTenMinutesIncludingServiceRecreation() {
        val first = create()
        first.snoozeBubble()
        assertFalse(added(first))
        controllers.removeAt(0).destroy()
        val restored = create()
        val handler = ReflectionHelpers.getField<Handler>(restored, "snoozeHandler")
        val callback = ReflectionHelpers.getField<Runnable>(restored, "snoozeExpiredRunnable")
        assertTrue(handler.hasCallbacks(callback))
        assertFalse(added(restored))
        main.idleFor(Duration.ofMinutes(10))
        assertFalse(BubbleService.isSnoozed(context))
        assertTrue(added(restored))
    }

    @Test fun disablingCancelsReturnsAndRejectsAStaleStart() {
        create().snoozeBubble()
        context.getSharedPreferences("bubble_prefs", 0).edit().putBoolean("bubble_enabled", false).commit()
        BubbleService.disable(context)
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertFalse(alarms.any { shadowOf(it.operation).savedIntent.action in setOf(
            BubbleService.ACTION_RESUME_BUBBLE, BubbleService.ACTION_SNOOZE_EXPIRED) })
        val stale = create()
        assertFalse(added(stale))
        assertFalse(BubbleService.isRunning)
        assertEquals(Service.START_NOT_STICKY, stale.onStartCommand(Intent(BubbleService.ACTION_SNOOZE_EXPIRED), 0, 3))
        assertTrue(shadowOf(stale).isStoppedBySelf)
    }

    @Test fun temporaryHideOwnersSurviveVerificationAndDoNotCancelSnooze() {
        val service = create()
        BubbleService.hideTemporarily(context, "one")
        BubbleService.hideTemporarily(context, "two")
        main.idle()
        broadcast(BubbleService.ACTION_VERIFY_BUBBLE_STATE)
        broadcast("com.focusbyrj.app.CHAT_CLOSED")
        broadcast(Intent.ACTION_SCREEN_ON)
        assertFalse(added(service))
        BubbleService.releaseTemporaryHide(context, "one"); main.idle()
        assertFalse(added(service))
        service.snoozeBubble()
        BubbleService.releaseTemporaryHide(context, "two"); main.idle()
        assertTrue(BubbleService.isSnoozed(context))
        assertFalse(added(service))
        service.resumeBubble()
        assertTrue(added(service))
    }

    @Test fun newAlertsAndEarlyBackupDeliveryCannotEndSnooze() {
        val service = create()
        service.snoozeBubble()
        context.sendBroadcast(Intent(BubbleService.ACTION_SHOW_ALERT_PREVIEW).setPackage(context.packageName)
            .putExtra(BubbleService.EXTRA_ALERT_TEXT, "Task reminder"))
        main.idle()
        service.onStartCommand(Intent(BubbleService.ACTION_SNOOZE_EXPIRED), 0, 4)
        assertTrue(BubbleService.isSnoozed(context))
        assertFalse(added(service))
        assertNull(ReflectionHelpers.getField<View?>(service, "previewPillView"))
    }

    @Test fun landscapeHidesForVideoAndPortraitRestores() {
        val service = create()
        val params = ReflectionHelpers.getField<WindowManager.LayoutParams>(service, "layoutParams")
        params.y = 500
        RuntimeEnvironment.setQualifiers("w852dp-h393dp-xhdpi")
        service.onConfigurationChanged(context.resources.configuration)
        assertFalse(added(service))
        broadcast(BubbleService.ACTION_VERIFY_BUBBLE_STATE)
        assertFalse(added(service))
        RuntimeEnvironment.setQualifiers("w393dp-h852dp-xhdpi")
        service.onConfigurationChanged(context.resources.configuration)
        assertTrue(added(service))
        assertEquals(500, params.y)
    }

    @Test fun permissionRevocationStopsAnInvisibleService() {
        val service = create()
        ShadowSettings.setCanDrawOverlays(false)
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 5))
        assertFalse(added(service))
        assertFalse(BubbleService.isRunning)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }
    @Test fun regrantingPermissionRecoversEnabledBubbleWithoutClearingSnooze() {
        BubbleService.snooze(context)
        val deadline = context.getSharedPreferences("bubble_prefs", 0).getLong(BubbleService.PREFS_KEY_SNOOZED_UNTIL, 0)
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(BubbleService.startIfEnabled(context))
        ShadowSettings.setCanDrawOverlays(true)
        assertTrue(BubbleService.startIfEnabled(context))
        val service = create()
        assertTrue(BubbleService.isSnoozed(context))
        assertFalse(added(service))
        service.onStartCommand(null, 0, 6)
        assertEquals(deadline, context.getSharedPreferences("bubble_prefs", 0).getLong(BubbleService.PREFS_KEY_SNOOZED_UNTIL, 0))
    }

}
