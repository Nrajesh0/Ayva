package com.focusbyrj.app.service

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.WindowManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BubbleChatVisibilityTest {
    private lateinit var controller: ServiceController<BubbleService>
    private lateinit var service: BubbleService

    @Before fun startBubble() {
        BubbleService.isChatOpen = false
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE).edit().clear().putBoolean("bubble_enabled", true).commit()
        ShadowSettings.setCanDrawOverlays(true)
        controller = Robolectric.buildService(BubbleService::class.java).create()
        service = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Bubble starts attached", isAdded())
    }

    @After fun stopBubble() {
        controller.destroy()
        BubbleService.isChatOpen = false
    }

    @Test fun openingChatRemovesLauncherAndClosingRestoresItsPosition() {
        val params = ReflectionHelpers.getField<WindowManager.LayoutParams>(service, "layoutParams")
        params.x = 0
        params.y = 420
        broadcast("com.focusbyrj.app.CHAT_OPENED")
        assertFalse(isAdded())
        broadcast("com.focusbyrj.app.CHAT_OPENED")
        assertFalse(isAdded())
        broadcast("com.focusbyrj.app.CHAT_CLOSED")
        assertTrue(isAdded())
        assertEquals(0, params.x)
        assertEquals(420, params.y)
    }

    @Test fun settingsAlertsAndPermissionRestoreCannotCoverOpenChat() {
        broadcast("com.focusbyrj.app.CHAT_OPENED")
        broadcast(BubbleService.ACTION_SETTINGS_CHANGED)
        broadcast("com.focusbyrj.app.SHOW_BUBBLE")
        service.sendBroadcast(Intent(BubbleService.ACTION_SHOW_ALERT_PREVIEW).setPackage(service.packageName).putExtra(BubbleService.EXTRA_ALERT_TEXT, "Task reminder"))
        shadowOf(Looper.getMainLooper()).idle()
        broadcast(BubbleService.ACTION_HIDE_FOR_PERMISSION)
        broadcast(BubbleService.ACTION_RESTORE_FROM_PERMISSION)
        assertFalse(isAdded())
        broadcast("com.focusbyrj.app.CHAT_CLOSED")
        assertTrue(isAdded())
    }

    @Test fun closingChatRespectsDisabledBubbleSetting() {
        broadcast("com.focusbyrj.app.CHAT_OPENED")
        service.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE).edit().putBoolean("bubble_enabled", false).commit()
        broadcast("com.focusbyrj.app.CHAT_CLOSED")
        assertFalse(isAdded())
    }

    private fun isAdded(): Boolean = ReflectionHelpers.getField(service, "isBubbleAdded")
    private fun broadcast(action: String) {
        service.sendBroadcast(Intent(action).setPackage(service.packageName))
        shadowOf(Looper.getMainLooper()).idle()
    }
}
