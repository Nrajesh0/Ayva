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
        shadowOf(context.packageManager).addResolveInfoForIntent(
            android.content.Intent("android.speech.RecognitionService"), android.content.pm.ResolveInfo())
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

    @Test fun actionTranscriptIncludesOnlyRecognizerFinalResults() {
        val speech = start()
        speech.triggerOnPartialResults(words("buy milk"))
        assertEquals("", manager.finalizedTranscript())
        speech.triggerOnResults(words("buy milk"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1600))
        speech.triggerOnPartialResults(words("delete all tasks"))
        assertEquals("buy milk", manager.finalizedTranscript())
        manager.stopRecording()
        assertEquals("", manager.finalizedTranscript())
    }

    @Test fun emptyTerminalResultDoesNotPromotePartialCaptionForActions() {
        val speech = start()
        speech.triggerOnPartialResults(words("buy milk"))
        speech.triggerOnResults(Bundle())
        assertEquals("", manager.finalizedTranscript())
        assertTrue(manager.recordingState.value.liveTranscript.contains("milk"))
    }

    @Test fun defaultServiceWorksWithoutDedicatedOnDeviceService() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)
        assertTrue(SpeechRecognizer.isRecognitionAvailable(context))
        val speech = start()
        assertTrue(manager.recordingState.value.isRecording)
        assertFalse(speech.lastRecognizerIntent.hasExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE))
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
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1600))
        speech.triggerOnPartialResults(words("and bread"))
        speech.triggerOnResults(words("and bread"))
        assertEquals("Remember the milk. And bread.", manager.stopRecording().second)
        assertEquals("Remember the milk. And bread.", updates.last())
    }

    @Test fun emptyFinalKeepsLastVisiblePartialWithoutDuplicatingIt() {
        val speech = start()
        speech.triggerOnPartialResults(words("words already seen"))
        speech.triggerOnResults(Bundle())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1600))
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
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1600))
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
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1600))
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
        shadowOf(context.packageManager).setResolveInfosForIntent(android.content.Intent("android.speech.RecognitionService"), emptyList())
        manager.startRecording()
        assertFalse(manager.recordingState.value.isRecording)
        assertTrue(manager.recordingState.value.errorMessage!!.contains("phone speech service"))
        assertEquals(0f, manager.recordingState.value.currentAmplitude)
    }

    @Test @Config(sdk = [28]) fun olderAndroidUsesPhoneService() {
        val speech = start()
        speech.triggerOnPartialResults(words("works on older phones"))
        assertEquals("works on older phones", manager.recordingState.value.liveTranscript)
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
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500L * (attempt + 1)))
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
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1600))
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
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1600))
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
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1600))
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
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1600))
        shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerOnResults(words("continue"))
        assertEquals("Keep this draft. Continue.", manager.stopRecording().second)
    }
    @Test fun rateLimitWaitsWithoutRebindingAndKeepsOnlyFinalWordsForActions() {
        val speech = start()
        val recognizer = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        speech.triggerOnPartialResults(words("keep these words"))
        speech.triggerOnError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS)
        assertEquals(30, manager.recordingState.value.retryAfterSeconds)
        assertNull(manager.recordingState.value.errorMessage)
        assertEquals("Keep these words.", manager.recordingState.value.liveTranscript)
        assertEquals("", manager.finalizedTranscript())
        // Duplicated terminal events must not queue overlapping recognition requests.
        speech.triggerOnResults(words("late words"))
        speech.triggerOnError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(29100))
        assertTrue(manager.recordingState.value.isRecording)
        assertEquals(1, manager.recordingState.value.retryAfterSeconds)
        assertSame(recognizer, ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900))
        assertTrue(manager.recordingState.value.statusMessage.contains("Starting phone"))
        assertFalse(speech.isDestroyed)
        speech.triggerOnResults(words("continue here"))
        assertEquals("Keep these words. Continue here.", manager.stopRecording().second)
    }

    @Test fun persistentRateLimitStopsAndExplicitRetryCannotBypassCooldown() {
        val speech = start()
        speech.triggerOnReadyForSpeech(Bundle())
        speech.triggerOnError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        speech.triggerOnError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        speech.triggerOnError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS)
        assertFalse(manager.recordingState.value.isRecording)
        assertTrue(manager.recordingState.value.errorMessage!!.contains("limiting requests"))
        assertFalse(manager.recordingState.value.errorMessage!!.contains("language"))
        assertTrue(speech.isDestroyed)
        val previous = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        manager.retryRecording()
        manager.retryRecording()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(119))
        assertSame(previous, ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val next = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        assertNotSame(previous, next)
        assertFalse(shadowOf(next).lastRecognizerIntent.hasExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE))
    }

    @Test fun cancelDuringRateLimitWaitCannotRestartTheMicrophone() {
        val speech = start()
        speech.triggerOnError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS)
        manager.cancelRecording()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(3))
        assertFalse(manager.recordingState.value.isSessionOpen)
        assertTrue(speech.isDestroyed)
        assertEquals("", manager.recordingState.value.liveTranscript)
    }

    @Test fun phraseRestartsArePacedInsteadOfImmediatelyRequestingTheServiceAgain() {
        val speech = start()
        val firstRequest = speech.lastRecognizerIntent
        speech.triggerOnResults(words("first phrase"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertSame(firstRequest, speech.lastRecognizerIntent)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1300))
        assertNotSame(firstRequest, speech.lastRecognizerIntent)
        speech.triggerOnResults(words("next phrase"))
        assertEquals("First phrase. Next phrase.", manager.stopRecording().second)
    }

    @Test fun userPauseIsResumableAndNeverClaimsListeningDuringCooldown() {
        val speech = start()
        assertFalse(manager.recordingState.value.isListening)
        speech.triggerOnReadyForSpeech(Bundle())
        assertTrue(manager.recordingState.value.isListening)
        speech.triggerOnPartialResults(words("keep this caption"))
        manager.pauseByUser()
        assertTrue(manager.recordingState.value.isSessionOpen)
        assertTrue(manager.recordingState.value.isPaused)
        assertFalse(manager.recordingState.value.isListening)
        assertFalse(manager.recordingState.value.isRecording)
        assertNull(manager.recordingState.value.errorMessage)
        assertEquals("keep this caption", manager.recordingState.value.liveTranscript)
        manager.retryRecording()
        assertFalse(manager.recordingState.value.isPaused)
        assertFalse(manager.recordingState.value.isListening)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1600))
        val resumed = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        resumed.triggerOnReadyForSpeech(Bundle())
        assertTrue(manager.recordingState.value.isListening)
        resumed.triggerOnError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS)
        assertFalse(manager.recordingState.value.isListening)
        assertTrue(manager.recordingState.value.isRecording)
        assertTrue(manager.recordingState.value.liveTranscript.contains("caption"))
    }

    @Test fun startupRejectionDiscardsFailedConnectionAndOnlyRetriesOnce() {
        val failed = start()
        failed.triggerOnError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS)
        assertTrue(failed.isDestroyed)
        assertFalse(manager.recordingState.value.isListening)
        assertEquals(30, manager.recordingState.value.retryAfterSeconds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(29100))
        assertSame(failed, shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()))
        assertEquals(1, manager.recordingState.value.retryAfterSeconds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900))
        val retry = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        assertNotSame(failed, retry)
        failed.triggerOnPartialResults(words("stale words"))
        assertEquals("", manager.recordingState.value.liveTranscript)
        retry.triggerOnError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS)
        assertFalse(manager.recordingState.value.isRecording)
        assertTrue(manager.recordingState.value.errorMessage!!.contains("rejected the connection"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(3))
        assertSame(retry, shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()))
    }

    @Test fun newConnectionCanRecoverFromStartupRejection() {
        val failed = start()
        failed.triggerOnError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        val retry = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        retry.triggerOnReadyForSpeech(Bundle())
        retry.triggerOnPartialResults(words("working again"))
        assertTrue(manager.recordingState.value.isListening)
        assertEquals(0, manager.recordingState.value.retryAfterSeconds)
        assertEquals("working again", manager.recordingState.value.liveTranscript)
        retry.triggerOnResults(words("working again"))
        assertEquals("working again", manager.finalizedTranscript())
    }

    @Test fun repeatedStartDoesNotReplaceActiveCaptureOrCallback() {
        var first = ""
        var second = ""
        manager.startRecording { first = it }
        shadowOf(Looper.getMainLooper()).idle()
        val speech = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        manager.startRecording { second = it }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1600))
        assertSame(speech, ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        shadowOf(speech).triggerOnPartialResults(words("one microphone"))
        assertEquals("one microphone", first)
        assertEquals("", second)
    }

    @Test fun transferringMicrophoneOwnershipKeepsCallerTranscriptsPrivate() {
        val first = start()
        first.triggerOnPartialResults(words("private draft"))
        val other = com.focusbyrj.app.util.speech.OnDeviceTranscriber(context)
        try {
            other.startRecording()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(first.isDestroyed)
            assertFalse(manager.recordingState.value.isRecording)
            assertEquals("private draft", manager.recordingState.value.liveTranscript)
            assertEquals("", other.recordingState.value.liveTranscript)
            first.triggerOnResults(words("late private words"))
            assertEquals("", other.recordingState.value.liveTranscript)
            shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerOnResults(words("fresh words"))
            assertEquals("fresh words", other.finalizedTranscript())
            assertEquals("", manager.finalizedTranscript())
        } finally { other.release() }
    }

    @Test fun busyServiceReusesConnectionAndReleaseCancelsQueuedRetry() {
        val speech = start()
        val first = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        speech.triggerOnError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1600))
        assertSame(first, ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        assertFalse(speech.isDestroyed)
        speech.triggerOnError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        manager.release()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertTrue(speech.isDestroyed)
        assertFalse(manager.recordingState.value.isSessionOpen)
    }

    @Test fun failureDetailsContainServiceMetadataWithoutCapturedWords() {
        val speech = start()
        speech.triggerOnPartialResults(words("private caption for diagnostics test"))
        speech.triggerOnError(SpeechRecognizer.ERROR_AUDIO)
        val state = manager.recordingState.value
        assertEquals("private caption for diagnostics test", state.liveTranscript)
        assertTrue(state.diagnosticInfo.contains("Error: 3"))
        assertTrue(state.diagnosticInfo.contains("default phone speech service; provider may use online recognition"))
        assertTrue(state.diagnosticInfo.contains("Enabled recognition services:"))
        assertFalse(state.diagnosticInfo.contains("private caption"))
        manager.cancelRecording()
        assertEquals("", manager.recordingState.value.diagnosticInfo)
    }

}
