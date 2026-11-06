package com.focusbyrj.app.ui.screens

import android.app.Application
import android.content.Intent
import android.os.Looper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34, 36], application = Application::class)
class BlockActivityReceiverTest {
    @Test
    fun closeRequiresAnExplicitAppDestinationAcrossSupportedAndroidVersions() {
        val controller = Robolectric.buildActivity(BlockActivity::class.java).create()
        val activity = controller.get()
        try {
            activity.sendBroadcast(Intent("com.focusbyrj.app.CLOSE_BLOCK_SCREEN"))
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(activity.isFinishing)

            activity.sendBroadcast(Intent("com.focusbyrj.app.CLOSE_BLOCK_SCREEN").setPackage(activity.packageName))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(activity.isFinishing)
        } finally {
            controller.destroy()
        }
    }
}
