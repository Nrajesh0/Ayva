/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.notes

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
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
import java.util.UUID

class AudioMemoManager(private val context: Context) {

    private val tag = "AudioMemoManager"

    // SpeechRecognizer calls and state transitions run on the main thread.
    data class RecordingState(
        val isRecording: Boolean = false,
        val elapsedSeconds: Int = 0,
        val currentAmplitude: Float = 0f,
        val liveTranscript: String = "",
        val currentOutputFile: File? = null,
        val statusMessage: String = "",
        val errorMessage: String? = null
    ) {
        val isSessionOpen: Boolean get() = isRecording || errorMessage != null
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

    @androidx.annotation.MainThread
    fun startRecording(onTranscriptUpdate: (String) -> Unit = {}) {
        stopPlayback()
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
            currentAmplitude = 0f,
            statusMessage = "Starting offline transcription...",
            errorMessage = null
        )
        if (!initSpeechRecognizer(generation)) return
        startListeningInternal(generation)
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
                private fun isCurrent() = isCurrentSession(generation) && speechRecognizer === recognizer

                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isCurrent()) return
                    clearRecognitionTimeout()
                    _recordingState.value = _recordingState.value.copy(statusMessage = "Listening...")
                }

                override fun onBeginningOfSpeech() {
                    if (!isCurrent()) return
                    clearRecognitionTimeout()
                    _recordingState.value = _recordingState.value.copy(statusMessage = "Listening...")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    if (isCurrent()) targetAmplitude = ((rmsdB + 2f) / 10f).coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (!isCurrent()) return
                    targetAmplitude = 0f
                    _recordingState.value = _recordingState.value.copy(statusMessage = "Finishing this phrase...")
                    setRecognitionTimeout(generation, "The speech service stopped responding. Your transcript is kept; retry to continue.")
                }

                override fun onError(error: Int) {
                    if (!isCurrent()) return
                    clearRecognitionTimeout()
                    targetAmplitude = 0f
                    Log.w(tag, "On-device recognition error: $error")
                    when (error) {
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
                    transientErrors = 0
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
                        currentPartialText = partial
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
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                selectedLanguage?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
            }
            _recordingState.value = _recordingState.value.copy(statusMessage = "Starting offline transcription...")
            setRecognitionTimeout(generation, "The speech service did not start. Enable an offline speech language in Voice settings, then retry.")
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
        _recordingState.value = _recordingState.value.copy(statusMessage = "Checking installed offline speech languages...")
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

    private fun restartListening(generation: Int, delayMs: Long, rebind: Boolean = false) {
        if (!isCurrentSession(generation)) return
        _recordingState.value = _recordingState.value.copy(statusMessage = "Listening...")
        mainHandler.postDelayed({
            if (isCurrentSession(generation) && (!rebind || initSpeechRecognizer(generation))) {
                startListeningInternal(generation)
            }
        }, delayMs)
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
        recordingJob?.cancel()
        recordingJob = null
        cleanupSpeechRecognizer()
        targetAmplitude = 0f
    }

    private fun failRecording(message: String) {
        endRecognition()
        _recordingState.value = _recordingState.value.copy(
            isRecording = false, currentAmplitude = 0f, statusMessage = message, errorMessage = message
        )
    }

    private fun cleanupSpeechRecognizer() {
        val recognizer = speechRecognizer
        speechRecognizer = null // Ignore callbacks from cancel/destroy, including synchronous ones.
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
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

    // ==========================================
    // PLAYBACK STATE
    // ==========================================
    data class PlaybackState(
        val currentPath: String? = null,
        val isPlaying: Boolean = false,
        val currentPositionMs: Int = 0,
        val durationMs: Int = 0,
        val speed: Float = 1.0f
    )

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    fun playOrToggle(audioPath: String) {
        if (_playbackState.value.currentPath == audioPath) {
            if (_playbackState.value.isPlaying) {
                pausePlayback()
            } else {
                resumePlayback()
            }
            return
        }

        stopPlayback()

        var player: MediaPlayer? = null
        try {
            val file = File(audioPath)
            if (!file.exists()) return

            val decryptedBytes = com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(file)
            val newPlayer = MediaPlayer().apply {
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    true
                }
                if (decryptedBytes != null && com.focusbyrj.app.util.crypto.EncryptedMediaStorage.isEncrypted(file)) {
                    setDataSource(object : android.media.MediaDataSource() {
                        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                            if (position >= decryptedBytes.size) return -1
                            val length = minOf(size, (decryptedBytes.size - position).toInt())
                            System.arraycopy(decryptedBytes, position.toInt(), buffer, offset, length)
                            return length
                        }
                        override fun getSize(): Long = decryptedBytes.size.toLong()
                        override fun close() {}
                    })
                } else {
                    setDataSource(audioPath)
                }
                prepare()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && _playbackState.value.speed != 1.0f) {
                    try {
                        val params = playbackParams
                        params.speed = _playbackState.value.speed
                        playbackParams = params
                    } catch (_: Exception) {}
                }
                setOnCompletionListener {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = 0
                    )
                    playbackJob?.cancel()
                }
                start()
            }
            player = newPlayer
            mediaPlayer = newPlayer

            _playbackState.value = _playbackState.value.copy(
                currentPath = audioPath,
                isPlaying = true,
                currentPositionMs = 0,
                durationMs = newPlayer.duration
            )

            startPlaybackTracking()
        } catch (e: Exception) {
            try {
                player?.release()
            } catch (_: Throwable) {}
            stopPlayback()
        }
    }

    private fun pausePlayback() {
        try {
            mediaPlayer?.pause()
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
            playbackJob?.cancel()
        } catch (_: Exception) {}
    }

    private fun resumePlayback() {
        try {
            mediaPlayer?.start()
            _playbackState.value = _playbackState.value.copy(isPlaying = true)
            startPlaybackTracking()
        } catch (_: Exception) {}
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
        } catch (_: Exception) {}
    }

    fun setPlaybackSpeed(speed: Float) {
        try {
            mediaPlayer?.let { player ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val params = player.playbackParams
                    params.speed = speed
                    player.playbackParams = params
                }
            }
            _playbackState.value = _playbackState.value.copy(speed = speed)
        } catch (_: Exception) {}
    }

    fun skip(deltaMs: Int) {
        val current = _playbackState.value.currentPositionMs
        val dur = _playbackState.value.durationMs
        val target = (current + deltaMs).coerceIn(0, if (dur > 0) dur else Int.MAX_VALUE)
        seekTo(target)
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {} finally {
            mediaPlayer = null
        }
        _playbackState.value = PlaybackState()
    }

    private fun startPlaybackTracking() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            while (isActive && _playbackState.value.isPlaying) {
                delay(100)
                val pos = try {
                    mediaPlayer?.currentPosition ?: 0
                } catch (_: Exception) {
                    0
                }
                val dur = try {
                    mediaPlayer?.duration ?: 0
                } catch (_: Exception) {
                    0
                }
                _playbackState.value = _playbackState.value.copy(
                    currentPositionMs = pos,
                    durationMs = if (dur > 0) dur else _playbackState.value.durationMs
                )
            }
        }
    }

    fun release() {
        cancelRecording()
        stopPlayback()
    }

    companion object {
        fun formatDuration(ms: Long): String {
            val totalSeconds = (ms / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }

        fun getAudioDurationMs(path: String): Long {
            var mmr: android.media.MediaMetadataRetriever? = null
            var decryptedBytes: ByteArray? = null
            return try {
                val file = File(path)
                if (!file.exists()) return 0L
                val retriever = android.media.MediaMetadataRetriever()
                mmr = retriever
                val bytes = com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(file)
                decryptedBytes = bytes
                if (bytes != null && com.focusbyrj.app.util.crypto.EncryptedMediaStorage.isEncrypted(file)) {
                    retriever.setDataSource(object : android.media.MediaDataSource() {
                        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                            if (position >= bytes.size) return -1
                            val length = minOf(size, (bytes.size - position).toInt())
                            System.arraycopy(bytes, position.toInt(), buffer, offset, length)
                            return length
                        }
                        override fun getSize(): Long = bytes.size.toLong()
                        override fun close() {}
                    })
                } else {
                    retriever.setDataSource(path)
                }
                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationStr?.toLongOrNull() ?: 0L
            } catch (_: Throwable) {
                0L
            } finally {
                try {
                    mmr?.release()
                } catch (_: Throwable) {}
                if (decryptedBytes != null) {
                    java.util.Arrays.fill(decryptedBytes, 0.toByte())
                }
            }
        }

        fun copyAudioFile(context: Context, originalPath: String): String? {
            return try {
                val src = File(originalPath)
                if (!src.exists()) return null
                if (!com.focusbyrj.app.data.note.NoteMediaManager.isAllowedMediaFile(src, context)) {
                    android.util.Log.w("AudioMemoManager", "Refusing to copy audio from unverified or restricted path: $originalPath")
                    return null
                }
                val audioDir = File(context.filesDir, "keep_audio").apply { if (!exists()) mkdirs() }
                val ext = src.extension.ifEmpty { "m4a" }
                val dest = File(audioDir, "audio_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext")
                val rawBytes = com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(src) ?: return null
                com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(dest, rawBytes)
                java.util.Arrays.fill(rawBytes, 0.toByte())
                dest.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
