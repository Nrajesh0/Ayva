package com.focusbyrj.app.service

import android.app.Application
import android.content.Intent
import androidx.room.Room
import com.focusbyrj.app.data.FocusDatabase
import kotlinx.coroutines.runBlocking
import android.os.Looper
import android.os.PowerManager
import com.focusbyrj.app.FocusApplication
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FocusScreenStateTest {
    @Test fun coldScreenOffStartDoesNotPollAndUnlockRestartsMonitoring() {
        val app = RuntimeEnvironment.getApplication()
        val power = app.getSystemService(PowerManager::class.java)
        shadowOf(power).setIsInteractive(false)
        val focus = FocusApplication()
        Application::class.java.getDeclaredMethod("attach", android.content.Context::class.java)
            .apply { isAccessible = true; invoke(focus, app) }
        val db = Room.inMemoryDatabaseBuilder(app, FocusDatabase::class.java).build()
        focus.javaClass.getDeclaredField("database\$delegate").apply { isAccessible = true; set(focus, lazyOf(db)) }
        val controller = Robolectric.buildService(FocusBlockerService::class.java)
        val service = controller.get()
        ReflectionHelpers.setField(service, "mApplication", focus)
        controller.create()
        try {
            assertNull(ReflectionHelpers.getField<Job?>(service, "monitoringJob"))
            shadowOf(power).setIsInteractive(true)
            app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(ReflectionHelpers.getField<Job>(service, "monitoringJob").isActive)
            shadowOf(power).setIsInteractive(false)
            app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(ReflectionHelpers.getField<Job?>(service, "monitoringJob"))
        } finally {
            controller.destroy()
            runBlocking { ReflectionHelpers.getField<Job>(service, "job").join() }
            db.close()
        }
    }
}
