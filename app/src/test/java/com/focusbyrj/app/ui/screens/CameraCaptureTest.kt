package com.focusbyrj.app.ui.screens

import android.app.Application
import android.content.ActivityNotFoundException
import com.focusbyrj.app.ui.screens.notes.launchCameraPreview
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34], application = Application::class)
class CameraCaptureTest {
    @Test fun delegatedCaptureLaunchesOnceWithoutRequestingCameraAccess() {
        val app = RuntimeEnvironment.getApplication()
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, app.checkSelfPermission(android.Manifest.permission.CAMERA))
        var launches = 0
        launchCameraPreview(app) { launches++ }
        assertEquals(1, launches)
    }

    @Test fun missingCameraAndPolicyDenialsOfferGalleryFallbackWithoutCrashing() {
        val app = RuntimeEnvironment.getApplication()
        for (failure in listOf(ActivityNotFoundException("No handler"), SecurityException("Device policy"), IllegalStateException("Launcher unavailable"))) {
            launchCameraPreview(app) { throw failure }
            assertTrue(ShadowToast.getTextOfLatestToast().toString().contains("gallery", ignoreCase = true))
        }
    }
}
