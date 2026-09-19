package com.focusbyrj.app

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.util.AyvaAlertCategory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AyvaAlertThemeTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testAlertCategoryInference() {
        // Briefing
        val brief = AyvaAlertCategory.infer("☀️ Good morning! Let's build your vocabulary today.", isMorning = true)
        assertEquals(AyvaAlertCategory.BRIEFING, brief)
        assertEquals("#0F766E", brief.defaultBgHex)

        // Drill practice
        val drill = AyvaAlertCategory.infer("⚡ Quick Mental Math Challenge! Keep your streak alive", isStreakPrompt = true)
        assertEquals(AyvaAlertCategory.DRILL_PRACTICE, drill)
        assertEquals("#6D28D9", drill.defaultBgHex)

        // Excessive usage / screen time
        val screenTime = AyvaAlertCategory.infer("☕ You've been active on your phone for 2 hours straight. Unplug for a brief walk.")
        assertEquals(AyvaAlertCategory.EXCESSIVE_USAGE, screenTime)
        assertEquals("#C2410C", screenTime.defaultBgHex)

        // Bedtime / Late night
        val bedtime = AyvaAlertCategory.infer("🌙 It's past your bedtime (10:30 PM). Put screens away and rest well.")
        assertEquals(AyvaAlertCategory.BEDTIME_SLEEP, bedtime)
        assertEquals("#3730A3", bedtime.defaultBgHex)

        // Critical limit
        val critical = AyvaAlertCategory.infer("Instagram daily limit reached. App strictly locked.")
        assertEquals(AyvaAlertCategory.CRITICAL_LIMIT, critical)
        assertEquals("#BE123C", critical.defaultBgHex)

        // General fallback
        val general = AyvaAlertCategory.infer("Hello Ayva, how can I help you today?")
        assertEquals(AyvaAlertCategory.GENERAL, general)
        assertEquals("#0284C7", general.defaultBgHex)
    }

    @Test
    fun testNotificationChannelsCreation() {
        AyvaAlertCategory.createNotificationChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        AyvaAlertCategory.entries.forEach { category ->
            val channel = nm.getNotificationChannel(category.channelId)
            assertNotNull("Channel for ${category.name} must exist", channel)
            assertEquals(category.channelName, channel.name.toString())
        }
    }

    @Test
    fun testCategoryTogglesAndCustomColors() {
        val brief = AyvaAlertCategory.BRIEFING
        assertTrue(brief.isEnabled(context))

        brief.setEnabled(context, false)
        assertFalse(brief.isEnabled(context))

        brief.setEnabled(context, true)
        assertTrue(brief.isEnabled(context))

        // Custom preset test
        val preset = com.focusbyrj.app.util.AyvaColorPresets.getPresetById("magenta")
        assertNotNull(preset)
        brief.setCustomColors(context, preset!!)

        assertEquals("#A21CAF", brief.getBgHex(context))
        assertEquals("#E879F9", brief.getStrokeHex(context))
        assertEquals("#FAE8FF", brief.getHeaderTextHex(context))

        // Reset
        brief.resetToDefault(context)
        assertEquals(brief.defaultBgHex, brief.getBgHex(context))
    }
}
