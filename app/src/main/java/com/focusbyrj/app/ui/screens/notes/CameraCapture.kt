package com.focusbyrj.app.ui.screens.notes

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast

/** Capture remains delegated to the camera app; a failed launch never changes the note. */
internal fun launchCameraCapture(context: Context, launch: () -> Unit) {
    val message = try {
        launch()
        return
    } catch (_: ActivityNotFoundException) {
        "No camera app is available. You can choose an image from your gallery instead."
    } catch (_: SecurityException) {
        "Android or your device policy blocked the camera. You can choose an image from your gallery instead."
    } catch (_: IllegalStateException) {
        "The camera could not open. Try again or choose an image from your gallery."
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
