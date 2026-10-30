package com.focusbyrj.app.ui.security

import android.app.Application
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ScreenCaptureProtectionTest {
    @Test fun protectionTracksPreferenceAndStopsObservingAfterDestroy() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val prefs = activity.getSharedPreferences("focus_prefs", 0)
        fun protected() = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        prefs.edit().putBoolean("secure_recents", true).commit()
        activity.installScreenCaptureProtection()
        assertTrue(protected())
        prefs.edit().putBoolean("secure_recents", false).commit()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(protected())
        prefs.edit().putBoolean("secure_recents", true).commit()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(protected())
        controller.pause().stop().destroy()
        prefs.edit().putBoolean("secure_recents", false).commit()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(protected())
    }

    @Test fun disablingPreferenceDoesNotRemoveExistingSensitiveProtection() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        activity.installScreenCaptureProtection()
        activity.getSharedPreferences("focus_prefs", 0).edit().putBoolean("secure_recents", false).commit()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        controller.pause().stop().destroy()
    }
}
