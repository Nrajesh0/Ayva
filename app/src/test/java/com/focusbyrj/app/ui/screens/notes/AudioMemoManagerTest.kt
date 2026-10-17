package com.focusbyrj.app.ui.screens.notes

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.RecognitionSupport
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer
import java.time.Duration
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudioMemoManagerTest {
    private lateinit var context: Context
    private lateinit var manager: AudioMemoManager
    private lateinit var originalLocale: Locale

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        manager = AudioMemoManager(context)
    }

    @After fun tearDown() {
        manager.release()
        shadowOf(Looper.getMainLooper()).idle()
        Locale.setDefault(originalLocale)
    }

    private fun start(): ShadowSpeechRecognizer {
        manager.startRecording()
        shadowOf(Looper.getMainLooper()).idle()
        return shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }

    private fun words(text: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }

    @Test fun onDeviceRecognitionDoesNotDependOnDefaultService() {
        assertFalse(SpeechRecognizer.isRecognitionAvailable(context))
        val speech = start()
        assertTrue(manager.recordingState.value.isRecording)
        assertTrue(speech.lastRecognizerIntent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertTrue(speech.lastRecognizerIntent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
        speech.triggerOnReadyForSpeech(Bundle())
        speech.triggerOnPartialResults(words("remember the milk"))
        assertEquals("remember the milk", manager.recordingState.value.liveTranscript)
    }

    @Test fun finalResultReplacesPartialAndNextPhraseAppendsOnce() {
        val updates = mutableListOf<String>()
        manager.startRecording { updates.add(it) }
        shadowOf(Looper.getMainLooper()).idle()
        val speech = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        speech.triggerOnPartialResults(words("remember"))
        speech.triggerOnPartialResults(words("remember the milk"))
        speech.triggerOnResults(words("remember the milk"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        speech.triggerOnPartialResults(words("and bread"))
        speech.triggerOnResults(words("and bread"))
        assertEquals("Remember the milk. And bread.", manager.stopRecording().second)
        assertEquals("Remember the milk. And bread.", updates.last())
    }

    @Test fun emptyFinalKeepsLastVisiblePartialWithoutDuplicatingIt() {
        val speech = start()
        speech.triggerOnPartialResults(words("words already seen"))
        speech.triggerOnResults(Bundle())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        speech.triggerOnResults(words("next phrase"))
        assertEquals("Words already seen. Next phrase.", manager.stopRecording().second)
    }

    @Test fun missingLanguageStopsAndKeepsTranscriptForRetry() {
        val speech = start()
        speech.triggerOnPartialResults(words("keep my words"))
        speech.triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        speech.triggerSupportError(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(manager.recordingState.value.isRecording)
        assertTrue(manager.recordingState.value.isSessionOpen)
        assertTrue(manager.recordingState.value.errorMessage!!.contains("language pack"))
        assertEquals("keep my words", manager.recordingState.value.liveTranscript)
        assertTrue(speech.isDestroyed)
        manager.retryRecording()
        shadowOf(Looper.getMainLooper()).idle()
        val retry = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        retry.triggerOnResults(words("continue here"))
        assertEquals("Keep my words. Continue here.", manager.stopRecording().second)
    }

    @Test fun retryKeepsOriginalTranscriptCallback() {
        var draft = "typed prefix"
        manager.startRecording { draft = "typed prefix $it" }
        shadowOf(Looper.getMainLooper()).idle()
        val speech = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        speech.triggerOnPartialResults(words("first phrase"))
        speech.triggerOnError(SpeechRecognizer.ERROR_AUDIO)
        manager.retryRecording()
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerOnResults(words("second phrase"))
        assertEquals("typed prefix First phrase. Second phrase.", draft)
    }

    @Test fun permissionErrorDoesNotLoop() {
        val speech = start()
        speech.triggerOnError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertFalse(manager.recordingState.value.isRecording)
        assertEquals(0, manager.recordingState.value.elapsedSeconds)
        assertTrue(manager.recordingState.value.errorMessage!!.contains("permission"))
        assertTrue(speech.isDestroyed)
    }

    @Test fun unavailableServiceDoesNotPretendToListen() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)
        manager.startRecording()
        assertFalse(manager.recordingState.value.isRecording)
        assertTrue(manager.recordingState.value.errorMessage!!.contains("on-device speech service"))
        assertEquals(0f, manager.recordingState.value.currentAmplitude)
    }

    @Test @Config(sdk = [28]) fun olderAndroidExplainsOfflineRequirement() {
        manager.startRecording()
        assertFalse(manager.recordingState.value.isRecording)
        assertTrue(manager.recordingState.value.errorMessage!!.contains("Android 12"))
    }

    @Test fun unresponsiveServiceTimesOutInsteadOfRecordingForever() {
        start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        assertFalse(manager.recordingState.value.isRecording)
        assertTrue(manager.recordingState.value.errorMessage!!.contains("did not start"))
    }

    @Test fun repeatedServiceErrorsHaveBoundedRetries() {
        var speech = start()
        repeat(4) { attempt ->
            speech.triggerOnError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500L * (attempt + 1)))
            speech = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        }
        assertFalse(manager.recordingState.value.isRecording)
        assertTrue(manager.recordingState.value.errorMessage!!.contains("could not start"))
    }

    @Test fun cancelledAndPreviousRecognizersCannotUpdateNewSession() {
        val old = start()
        old.triggerOnResults(words("old phrase")) // Queues another phrase.
        manager.cancelRecording()
        manager.startRecording()
        shadowOf(Looper.getMainLooper()).idle()
        old.triggerOnPartialResults(words("stale secret"))
        old.triggerOnError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertEquals("", manager.recordingState.value.liveTranscript)
        assertNull(manager.recordingState.value.errorMessage)
        val current = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        current.triggerOnResults(words("new phrase"))
        assertEquals("New phrase.", manager.stopRecording().second)
        assertFalse(manager.recordingState.value.isSessionOpen)
    }

    @Test fun stopKeepsPartialAndRejectsLateResults() {
        val speech = start()
        speech.triggerOnPartialResults(words("unfinished phrase"))
        assertEquals("unfinished phrase", manager.stopRecording().second)
        speech.triggerOnResults(words("late result"))
        assertEquals("", manager.recordingState.value.liveTranscript)
        assertFalse(manager.recordingState.value.isSessionOpen)
    }

    @Test fun missingRegionalEnglishPackUsesInstalledEnglishVariant() {
        Locale.setDefault(Locale.forLanguageTag("en-IN"))
        val speech = start()
        speech.triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        speech.triggerSupportResult(RecognitionSupport.Builder()
            .addInstalledOnDeviceLanguage("en-US").addInstalledOnDeviceLanguage("fr-FR").build())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("en-US", speech.lastRecognizerIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        speech.triggerOnResults(words("english works"))
        assertEquals("English works.", manager.stopRecording().second)
    }

    @Test fun onlineEnglishAndUnrelatedOfflineLanguagesAreNotUsedAsFallback() {
        val speech = start()
        speech.triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        speech.triggerSupportResult(RecognitionSupport.Builder()
            .addOnlineLanguage("en-US").addInstalledOnDeviceLanguage("fr-FR").build())
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(manager.recordingState.value.isRecording)
        assertNotNull(manager.recordingState.value.errorMessage)
    }

    @Test fun languageFallbackDoesNotLoopWhenInstalledPackStillFails() {
        val speech = start()
        speech.triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        speech.triggerSupportResult(RecognitionSupport.Builder().addInstalledOnDeviceLanguage("en-US").build())
        shadowOf(Looper.getMainLooper()).idle()
        speech.triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        assertFalse(manager.recordingState.value.isRecording)
        assertNotNull(manager.recordingState.value.errorMessage)
    }

    @Test fun pauseReleasesMicrophoneAndRetainsWordsForExplicitRetry() {
        val speech = start()
        speech.triggerOnPartialResults(words("keep this draft"))
        manager.pauseRecording()
        assertFalse(manager.recordingState.value.isRecording)
        assertTrue(speech.isDestroyed)
        assertEquals("keep this draft", manager.recordingState.value.liveTranscript)
        manager.retryRecording()
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerOnResults(words("continue"))
        assertEquals("Keep this draft. Continue.", manager.stopRecording().second)
    }
}
