package com.focusbyrj.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class AyvaTalkEngineTest {

    @Test
    fun testPersistentRemindersQueryIsSpecific() = kotlinx.coroutines.runBlocking {
        val answer = AyvaTalkEngine.answerTalkQuery(context = androidx.test.core.app.ApplicationProvider.getApplicationContext(), query = "what is persistent reminders?")
        
        // Must contain persistent reminders info
        assertTrue(answer.contains("Persistent Task Reminders"))
        assertTrue(answer.contains("App Settings") || answer.contains("Persistent Reminders"))
        
        // MUST NOT contain unrelated settings
        assertFalse(answer.contains("Soft Mode Wait Timer"))
        assertFalse(answer.contains("Default Launch Tab"))
        assertFalse(answer.contains("Uninstall Protection"))
        assertFalse(answer.contains("Heatmap"))
    }

    @Test
    fun testWaitTimerQueryIsSpecific() = kotlinx.coroutines.runBlocking {
        val answer = AyvaTalkEngine.answerTalkQuery(context = androidx.test.core.app.ApplicationProvider.getApplicationContext(), query = "how does soft mode wait timer work?")
        
        assertTrue(answer.contains("Soft Mode Wait Timer"))
        // assertTrue(answer.contains("5 seconds to 60 seconds"))
        assertFalse(answer.contains("Persistent Reminder Interval"))
        assertFalse(answer.contains("Default Launch Tab"))
    }

    @Test
    fun testWhyNotBlockingQueryIsSpecific() = kotlinx.coroutines.runBlocking {
        val answer = AyvaTalkEngine.answerTalkQuery(context = androidx.test.core.app.ApplicationProvider.getApplicationContext(), query = "why are apps not blocking?")
        // Let's assert on things that are reliably present regardless of whether live permission check or static fallback happens
        assertTrue(answer.lowercase().contains("permission"))
        assertFalse(answer.contains("Heatmap"))
        // Strict user constraint: No accessibility
        assertFalse(answer.contains("Accessibility Service"))
    }

    @Test
    fun testFuzzyMatchingAndTypoTolerance() = kotlinx.coroutines.runBlocking {
        val typoAnswer = AyvaTalkEngine.answerTalkQuery(context = androidx.test.core.app.ApplicationProvider.getApplicationContext(), query = "/talk persistant remidners")
        assertTrue(typoAnswer.contains("Persistent Task Reminders"))

        val typoUninstall = AyvaTalkEngine.answerTalkQuery(context = androidx.test.core.app.ApplicationProvider.getApplicationContext(), query = "/talk uninstal protect")
        assertTrue(typoUninstall.contains("Uninstall Protection"))
    }

    @Test
    fun testActionsAndDeepLinking() = kotlinx.coroutines.runBlocking {
        val resp = AyvaTalkEngine.answerTalkQueryWithActions(context = androidx.test.core.app.ApplicationProvider.getApplicationContext(), query = "/talk persistent reminders")
        assertNotNull(resp.actions)
        assertTrue(resp.actions.isNotEmpty())
        assertTrue(resp.actions.any { it.label.contains("Settings") || it.label.contains("5m") || it.label.contains("15m") })
    }

    @Test
    fun testFollowUpContextResolution() = kotlinx.coroutines.runBlocking {
        // Query topic first
        AyvaTalkEngine.answerTalkQueryWithActions(context = androidx.test.core.app.ApplicationProvider.getApplicationContext(), query = "/talk soft mode wait timer")
        
        // Follow up with pronoun
        val followUp = AyvaTalkEngine.answerTalkQueryWithActions(context = androidx.test.core.app.ApplicationProvider.getApplicationContext(), query = "how to change it?")
        assertTrue(followUp.formattedText.contains("Soft Mode Wait Timer"))
    }

    @Test
    fun testNoPinLockQueryExplainsActualMechanism() = kotlinx.coroutines.runBlocking {
        val answer = AyvaTalkEngine.answerTalkQuery(context = androidx.test.core.app.ApplicationProvider.getApplicationContext(), query = "how to set pin lock?")
        assertTrue(answer.contains("No PIN Lock Required") || answer.contains("does not have or use a PIN lock"))
        assertTrue(answer.contains("Soft Mode") || answer.contains("Strict Mode") || answer.contains("Uninstall Protection"))
    }

    @Test
    fun testSecurityAndPermissionsQuery() = kotlinx.coroutines.runBlocking {
        val answer = AyvaTalkEngine.answerTalkQuery(context = androidx.test.core.app.ApplicationProvider.getApplicationContext(), query = "/talk security and permissions")
        assertTrue(answer.contains("Security") || answer.contains("Usage Access") || answer.contains("Permissions"))
        assertTrue(answer.contains("Usage Access") || answer.contains("Display Over") || answer.contains("Overlay") || answer.contains("Battery"))
    }
}
