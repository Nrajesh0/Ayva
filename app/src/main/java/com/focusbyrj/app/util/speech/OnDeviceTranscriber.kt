/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.util.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** On-device speech only; no saved-media playback, Notes or vault capability. */
class OnDeviceTranscriber(private val context: Context) {

    private val tag = "OnDeviceTranscriber"

    // SpeechRecognizer calls and state transitions run on the main thread.
    data class RecordingState(
        val isRecording: Boolean = false,
        val isListening: Boolean = false,
        val isPaused: Boolean = false,
        val elapsedSeconds: Int = 0,
        val currentAmplitude: Float = 0f,
        val liveTranscript: String = "",
        val currentOutputFile: File? = null,
        val statusMessage: String = "",
        val errorMessage: String? = null
    ) {
        val isSessionOpen: Boolean get() = isRecording || isPaused || errorMessage != null
    }

    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var recordingJob: Job? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val accumulatedSentences = mutableListOf<String>()
    private val finalizedSentences = mutableListOf<String>()

    /** Recognizer final results only; partial captions remain available separately for editing. */
    @androidx.annotation.MainThread
    fun finalizedTranscript(): String = finalizedSentences.joinToString("; ")
    private var currentPartialText = ""
    private var onTranscriptCallback: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var targetAmplitude = 0f
    private var sessionGeneration = 0
    private var transientErrors = 0
    private var selectedLanguage: String? = null
    private var checkedLanguageSupport = false
    private var recognitionTimeout: Runnable? = null
    private var pendingRestart: Runnable? = null
    private var recognitionActive = false
    // Deliberately survive cancel/retry, so repeated taps cannot bypass a service cooldown.
    private var nextAllowedStartAt = 0L
    private var rateLimitErrors = 0

    @androidx.annotation.MainThread
    fun startRecording(onTranscriptUpdate: (String) -> Unit = {}) {
        cancelRecording()
        onTranscriptCallback = onTranscriptUpdate
        beginRecording()
    }

    @androidx.annotation.MainThread
    fun retryRecording() {
        if (_recordingState.value.isRecording || !_recordingState.value.isSessionOpen) return
        // Preserve visible words and the original caller's draft prefix on retry.
        finishSegment(null)
        beginRecording()
    }

    private fun beginRecording() {
        val generation = ++sessionGeneration
        transientErrors = 0
        selectedLanguage = null
        checkedLanguageSupport = false
        targetAmplitude = 0f
        _recordingState.value = _recordingState.value.copy(
            isRecording = true,
            isPaused = false,
            isListening = false,
            currentAmplitude = 0f,
            statusMessage = "Starting offline transcription...",
            errorMessage = null
        )
        val waitMs = nextAllowedStartAt - android.os.SystemClock.elapsedRealtime()
        if (waitMs > 0) {
            restartListening(generation, waitMs, rebind = true,
                status = "Waiting for the speech service… Your transcript is kept.")
        } else {
            if (!initSpeechRecognizer(generation)) return
            startListeningInternal(generation)
        }
        if (!_recordingState.value.isRecording) return

        val previousElapsed = _recordingState.value.elapsedSeconds
        val startedAt = android.os.SystemClock.elapsedRealtime()
        recordingJob = scope.launch {
            while (isActive && isCurrentSession(generation)) {
                delay(60)
                val current = _recordingState.value.currentAmplitude
                val next = if (targetAmplitude > current) {
                    current + (targetAmplitude - current) * 0.45f
                } else {
                    current * 0.86f
                }
                _recordingState.value = _recordingState.value.copy(
                    elapsedSeconds = previousElapsed + ((android.os.SystemClock.elapsedRealtime() - startedAt) / 1000).toInt(),
                    currentAmplitude = next
                )
            }
        }
    }

    private fun isCurrentSession(generation: Int) =
        generation == sessionGeneration && _recordingState.value.isRecording

    private fun initSpeechRecognizer(generation: Int): Boolean {
        cleanupSpeechRecognizer()
        try {
            // The default recognition service and the on-device service are independent.
            // isRecognitionAvailable() can be false while on-device recognition works.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                failRecording("Offline live transcription requires Android 12 or later.")
                return false
            }
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                failRecording("No on-device speech service is available. Enable or update your device's speech service in Voice settings, then retry.")
                return false
            }
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                private fun isCurrent() = isCurrentSession(generation) && speechRecognizer === recognizer && recognitionActive

                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isCurrent()) return
                    clearRecognitionTimeout()
                    _recordingState.value = _recordingState.value.copy(statusMessage = "Listening...", isListening = true)
                }

                override fun onBeginningOfSpeech() {
                    if (!isCurrent()) return
                    clearRecognitionTimeout()
                    _recordingState.value = _recordingState.value.copy(statusMessage = "Listening...", isListening = true)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    if (isCurrent() && rmsdB.isFinite()) {
                        targetAmplitude = ((rmsdB + 2f) / 10f).coerceIn(0f, 1f)
                        if (!_recordingState.value.isListening) _recordingState.value = _recordingState.value.copy(isListening = true, statusMessage = "Listening...")
                    }
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (!isCurrent()) return
                    targetAmplitude = 0f
                    _recordingState.value = _recordingState.value.copy(statusMessage = "Finishing this phrase...", isListening = false)
                    setRecognitionTimeout(generation, "The speech service stopped responding. Your transcript is kept; retry to continue.")
                }

                override fun onError(error: Int) {
                    if (!isCurrent()) return
                    clearRecognitionTimeout()
                    targetAmplitude = 0f
                    recognitionActive = false
                    Log.w(tag, "On-device recognition error: $error")
                    when (error) {
                        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> {
                            finishSegment(null)
                            val seconds = 30L shl rateLimitErrors.coerceAtMost(2)
                            nextAllowedStartAt = android.os.SystemClock.elapsedRealtime() + seconds * 1000
                            if (++rateLimitErrors <= 2) {
                                restartListening(generation, seconds * 1000,
                                    status = "The speech service needs a pause. Retrying in $seconds seconds; your transcript is kept.")
                            } else {
                                failRecording("The speech service is limiting requests. Wait $seconds seconds before retrying. Your transcript is kept.")
                            }
                        }
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            finishSegment(null)
                            transientErrors = 0
                            restartListening(generation, 500)
                        }
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            failRecording("Microphone permission is required. Allow microphone access for Ayva in app settings, then retry.")
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> recoverOfflineLanguage(generation, recognizer)
                        SpeechRecognizer.ERROR_AUDIO ->
                            failRecording("Cannot access the microphone. Check the device microphone privacy toggle and close other recording apps, then retry.")
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                            if (++transientErrors <= 3) restartListening(generation, 500L * transientErrors, rebind = true)
                            else failRecording("The on-device speech service could not start. Check Voice settings, then retry.")
                        }
                        else -> failRecording("Offline transcription could not continue (error $error). Check your offline speech language in Voice settings, then retry.")
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (!isCurrent()) return
                    clearRecognitionTimeout()
                    recognitionActive = false
                    transientErrors = 0
                    if (!results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().isNullOrBlank()) rateLimitErrors = 0
                    finishSegment(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
                    // A terminal callback ends the phrase. Cancelling here can generate a
                    // new ERROR_CLIENT and race the next phrase; simply start the next one.
                    restartListening(generation, 150)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!isCurrent()) return
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    if (partial.isNotBlank()) {
                        clearRecognitionTimeout()
                        rateLimitErrors = 0
                        currentPartialText = partial
                        _recordingState.value = _recordingState.value.copy(isListening = true, statusMessage = "Listening...")
                        updateFullTranscript()
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            return true
        } catch (e: Exception) {
            Log.w(tag, "Could not initialize on-device recognition", e)
            failRecording("The on-device speech service could not start. Check Voice settings, then retry.")
            return false
        }
    }

    private fun startListeningInternal(generation: Int) {
        if (!isCurrentSession(generation)) return
        val recognizer = speechRecognizer ?: return
        if (recognitionActive) return
        val waitMs = nextAllowedStartAt - android.os.SystemClock.elapsedRealtime()
        if (waitMs > 0) {
            restartListening(generation, waitMs,
                status = "Waiting for the speech service… Your transcript is kept.")
            return
        }
        pendingRestart?.let(mainHandler::removeCallbacks)
        pendingRestart = null
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                selectedLanguage?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
            }
            _recordingState.value = _recordingState.value.copy(statusMessage = "Starting offline transcription...", isListening = false)
            setRecognitionTimeout(generation, "The speech service did not start. Enable an offline speech language in Voice settings, then retry.")
            recognitionActive = true
            nextAllowedStartAt = android.os.SystemClock.elapsedRealtime() + 1500
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.w(tag, "Could not start on-device recognition", e)
            failRecording("Cannot start offline transcription. Check microphone access and Voice settings, then retry.")
        }
    }

    private fun recoverOfflineLanguage(generation: Int, recognizer: SpeechRecognizer) {
        val message = "The offline speech language pack is missing or unsupported. Download a supported language in your device's Voice settings, then retry."
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || checkedLanguageSupport) {
            failRecording(message)
            return
        }
        checkedLanguageSupport = true
        _recordingState.value = _recordingState.value.copy(statusMessage = "Checking installed offline speech languages...", isListening = false)
        setRecognitionTimeout(generation, message)
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            recognizer.checkRecognitionSupport(intent, { task -> mainHandler.post(task) }, object : android.speech.RecognitionSupportCallback {
                override fun onSupportResult(support: android.speech.RecognitionSupport) {
                    if (!isCurrentSession(generation) || speechRecognizer !== recognizer) return
                    clearRecognitionTimeout()
                    val installed = support.installedOnDeviceLanguages
                    val preferred = Locale.getDefault()
                    val language = installed.firstOrNull { it.equals(preferred.toLanguageTag(), ignoreCase = true) }
                        ?: installed.firstOrNull { Locale.forLanguageTag(it.replace('_', '-')).language == preferred.language }
                    if (language == null) {
                        failRecording(message)
                    } else {
                        // For example, use an installed en-US pack when the device locale
                        // is en-IN. Never select an online language or download silently.
                        selectedLanguage = language
                        startListeningInternal(generation)
                    }
                }

                override fun onError(error: Int) {
                    if (isCurrentSession(generation) && speechRecognizer === recognizer) failRecording(message)
                }
            })
        } catch (_: Exception) {
            failRecording(message)
        }
    }

    private fun restartListening(generation: Int, delayMs: Long, rebind: Boolean = false,
                                 status: String = "Continuing offline transcription…") {
        if (!isCurrentSession(generation)) return
        pendingRestart?.let(mainHandler::removeCallbacks)
        _recordingState.value = _recordingState.value.copy(statusMessage = status, isListening = false, currentAmplitude = 0f)
        pendingRestart = Runnable {
            pendingRestart = null
            if (isCurrentSession(generation) && (!rebind || initSpeechRecognizer(generation))) {
                startListeningInternal(generation)
            }
        }
        mainHandler.postDelayed(pendingRestart!!, maxOf(delayMs,
            nextAllowedStartAt - android.os.SystemClock.elapsedRealtime()))
    }

    private fun setRecognitionTimeout(generation: Int, message: String) {
        clearRecognitionTimeout()
        recognitionTimeout = Runnable { if (isCurrentSession(generation)) failRecording(message) }
        mainHandler.postDelayed(recognitionTimeout!!, 15_000)
    }

    private fun clearRecognitionTimeout() {
        recognitionTimeout?.let(mainHandler::removeCallbacks)
        recognitionTimeout = null
    }

    private fun finishSegment(finalText: String?) {
        finalText?.trim()?.takeIf(String::isNotBlank)?.let { finalizedSentences.add(it) }
        val text = finalText?.trim().takeUnless { it.isNullOrBlank() } ?: currentPartialText
        if (text.isNotBlank()) {
            accumulatedSentences.add(text.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            })
        }
        currentPartialText = ""
        updateFullTranscript()
    }

    private fun updateFullTranscript() {
        val sentences = accumulatedSentences.joinToString(" ") { sentence ->
            if (!sentence.endsWith(".") && !sentence.endsWith("?") && !sentence.endsWith("!")) "$sentence." else sentence
        }
        val full = listOf(sentences, currentPartialText).filter { it.isNotBlank() }.joinToString(" ").trim()
        _recordingState.value = _recordingState.value.copy(liveTranscript = full)
        onTranscriptCallback?.invoke(full)
    }

    private fun endRecognition() {
        ++sessionGeneration // Invalidate callbacks before cancelling the Android service.
        mainHandler.removeCallbacksAndMessages(null)
        recognitionTimeout = null
        pendingRestart = null
        recordingJob?.cancel()
        recordingJob = null
        cleanupSpeechRecognizer()
        targetAmplitude = 0f
    }

    private fun failRecording(message: String) {
        endRecognition()
        _recordingState.value = _recordingState.value.copy(
            isRecording = false, isListening = false, isPaused = false, currentAmplitude = 0f, statusMessage = message, errorMessage = message
        )
    }

    private fun cleanupSpeechRecognizer() {
        val recognizer = speechRecognizer
        recognitionActive = false
        speechRecognizer = null // Ignore callbacks from cancel/destroy, including synchronous ones.
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
    }

    @androidx.annotation.MainThread
    fun pauseByUser() {
        if (!_recordingState.value.isRecording) return
        endRecognition()
        _recordingState.value = _recordingState.value.copy(isRecording = false, isListening = false,
            isPaused = true, currentAmplitude = 0f, statusMessage = "Paused", errorMessage = null)
    }

    @androidx.annotation.MainThread
    fun pauseRecording() {
        if (_recordingState.value.isRecording) {
            failRecording("Transcription paused when Ayva left the screen. Retry to continue, or use the text already captured.")
        }
    }

    @androidx.annotation.MainThread
    fun stopRecording(): Pair<String?, String> {
        val transcript = _recordingState.value.liveTranscript.trim()
        cancelRecording()
        return Pair(null, transcript)
    }

    @androidx.annotation.MainThread
    fun cancelRecording() {
        endRecognition()
        onTranscriptCallback = null
        accumulatedSentences.clear()
        finalizedSentences.clear()
        currentPartialText = ""
        _recordingState.value = RecordingState()
    }

    fun release() = cancelRecording()
}
