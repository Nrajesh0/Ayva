package com.focusbyrj.app

import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.AbstractComposeView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

/** Framework smoke check only: Robolectric cannot establish OEM or native-library compatibility. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35, 36], application = FocusApplication::class)
class Android16StartupInvestigationTest {
    @Test fun startupReachesTheMainActivityOnAndroid15And16() {
        val app = ApplicationProvider.getApplicationContext<FocusApplication>()
        assertTrue(Build.VERSION.SDK_INT in 35..36)
        assertNotNull(app.repository)
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            try {
                controller.setup()
                assertNotNull(controller.get().viewModel)
                assertNotNull(controller.get().taskViewModel)
                assertNotNull(controller.get().habitViewModel)
            } finally {
                // This diagnostic uses a controller rather than ComposeTestRule, so
                // dispose its roots explicitly before later UI tests register idling.
                fun dispose(view: View) {
                    if (view is AbstractComposeView) view.disposeComposition()
                    if (view is ViewGroup) for (i in 0 until view.childCount) dispose(view.getChildAt(i))
                }
                dispose(controller.get().window.decorView)
            }
        }
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }
}
