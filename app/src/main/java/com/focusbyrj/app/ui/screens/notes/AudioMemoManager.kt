/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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

    // ==========================================
    // RECORDING STATE
    // ==========================================
    data class RecordingState(
        val isRecording: Boolean = false,
        val elapsedSeconds: Int = 0,
        val currentAmplitude: Float = 0f,
        val liveTranscript: String = "",
        val currentOutputFile: File? = null,
        val statusMessage: String = ""
    )

    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var recordingJob: Job? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val accumulatedSentences = mutableListOf<String>()
    private var currentPartialText = ""
    private var onTranscriptCallback: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var targetAmplitude: Float = 0.05f
    private var isListeningActive = false

    fun startRecording(onTranscriptUpdate: (String) -> Unit = {}) {
        stopPlayback()
        cancelRecording()

        accumulatedSentences.clear()
        currentPartialText = ""
        onTranscriptCallback = onTranscriptUpdate
        targetAmplitude = 0.05f
        isListeningActive = false

        _recordingState.value = RecordingState(
            isRecording = true,
            elapsedSeconds = 0,
            currentAmplitude = 0.1f,
            liveTranscript = "",
            currentOutputFile = null,
            statusMessage = "Listening..."
        )

        // 1. Initialize stable SpeechRecognizer on Main Thread
        mainHandler.post {
            initSpeechRecognizer()
            startListeningInternal()
        }

        // 2. Real-time timer and smooth amplitude decay tracker
        val startTimeMs = System.currentTimeMillis()
        recordingJob = scope.launch {
            while (isActive && _recordingState.value.isRecording) {
                delay(60)
                val elapsed = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
                val current = _recordingState.value.currentAmplitude
                val nextAmp = if (targetAmplitude > current) {
                    current + (targetAmplitude - current) * 0.45f
                } else {
                    (current * 0.86f).coerceAtLeast(0.04f)
                }

                _recordingState.value = _recordingState.value.copy(
                    elapsedSeconds = elapsed,
                    currentAmplitude = nextAmp
                )
            }
        }
    }

    private fun initSpeechRecognizer() {
        cleanupSpeechRecognizer()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(tag, "SpeechRecognizer is not available on this device")
            _recordingState.value = _recordingState.value.copy(
                statusMessage = "Speech recognition unavailable"
            )
            return
        }

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(tag, "SpeechRecognizer: Ready for speech")
                    isListeningActive = true
                    _recordingState.value = _recordingState.value.copy(statusMessage = "Listening...")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(tag, "SpeechRecognizer: Beginning of speech")
                    isListeningActive = true
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Normalize RMS dB (-2dB to +10dB) to range 0.05 .. 1.0
                    val normalized = ((rmsdB + 2f) / 10f).coerceIn(0.05f, 1f)
                    targetAmplitude = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(tag, "SpeechRecognizer: End of speech chunk")
                    targetAmplitude = 0.05f
                    isListeningActive = false
                }

                override fun onError(error: Int) {
                    Log.w(tag, "SpeechRecognizer onError: $error")
                    isListeningActive = false
                    targetAmplitude = 0.05f

                    if (!_recordingState.value.isRecording) return

                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            Log.e(tag, "Microphone permission missing!")
                            _recordingState.value = _recordingState.value.copy(
                                statusMessage = "Microphone permission required"
                            )
                            return
                        }
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            // Normal silence or pause in conversation - resume listening quietly
                            restartListeningQuietly(150)
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                        SpeechRecognizer.ERROR_CLIENT -> {
                            // Re-bind gently with slight delay
                            restartListeningWithRebind(350)
                        }
                        else -> {
                            // Any network or server hiccup, retry
                            restartListeningQuietly(300)
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListeningActive = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    Log.d(tag, "SpeechRecognizer onResults: $text")

                    if (text.isNotBlank()) {
                        val formatted = text.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        }
                        accumulatedSentences.add(formatted)
                        currentPartialText = ""
                        updateFullTranscript()
                    }

                    // Seamlessly continue listening for the next sentence
                    if (_recordingState.value.isRecording) {
                        restartListeningQuietly(100)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim().orEmpty()
                    if (partial.isNotBlank()) {
                        currentPartialText = partial
                        updateFullTranscript()
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer = recognizer
        } catch (e: Exception) {
            Log.e(tag, "Failed to create SpeechRecognizer: ${e.message}", e)
        }
    }

    private fun startListeningInternal() {
        if (!_recordingState.value.isRecording) return
        val recognizer = speechRecognizer ?: return

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error starting listening: ${e.message}", e)
        }
    }

    private fun restartListeningQuietly(delayMs: Long) {
        if (!_recordingState.value.isRecording) return
        mainHandler.postDelayed({
            if (_recordingState.value.isRecording) {
                try {
                    speechRecognizer?.cancel()
                    startListeningInternal()
                } catch (e: Exception) {
                    Log.w(tag, "Error restarting listening quietly: ${e.message}")
                    initSpeechRecognizer()
                    startListeningInternal()
                }
            }
        }, delayMs)
    }

    private fun restartListeningWithRebind(delayMs: Long) {
        if (!_recordingState.value.isRecording) return
        mainHandler.postDelayed({
            if (_recordingState.value.isRecording) {
                initSpeechRecognizer()
                startListeningInternal()
            }
        }, delayMs)
    }

    private fun updateFullTranscript() {
        val sentences = accumulatedSentences.joinToString(" ") { sentence ->
            if (!sentence.endsWith(".") && !sentence.endsWith("?") && !sentence.endsWith("!")) {
                "$sentence."
            } else {
                sentence
            }
        }
        val full = if (currentPartialText.isNotBlank()) {
            if (sentences.isNotBlank()) "$sentences $currentPartialText" else currentPartialText
        } else {
            sentences
        }.trim()

        _recordingState.value = _recordingState.value.copy(liveTranscript = full)
        onTranscriptCallback?.invoke(full)
    }

    private fun cleanupSpeechRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {} finally {
            speechRecognizer = null
            isListeningActive = false
        }
    }

    fun stopRecording(): Pair<String?, String> {
        val transcript = _recordingState.value.liveTranscript.trim()

        recordingJob?.cancel()
        recordingJob = null

        mainHandler.post {
            cleanupSpeechRecognizer()
        }

        _recordingState.value = RecordingState(isRecording = false)

        return Pair(null, transcript)
    }

    fun cancelRecording() {
        recordingJob?.cancel()
        recordingJob = null

        mainHandler.post {
            cleanupSpeechRecognizer()
        }

        _recordingState.value = RecordingState(isRecording = false)
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

        try {
            val file = File(audioPath)
            if (!file.exists()) return

            val player = MediaPlayer().apply {
                setDataSource(audioPath)
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
            mediaPlayer = player

            _playbackState.value = _playbackState.value.copy(
                currentPath = audioPath,
                isPlaying = true,
                currentPositionMs = 0,
                durationMs = player.duration
            )

            startPlaybackTracking()
        } catch (e: Exception) {
            e.printStackTrace()
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
            return try {
                val mmr = android.media.MediaMetadataRetriever()
                mmr.setDataSource(path)
                val durationStr = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                mmr.release()
                durationStr?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
                0L
            }
        }

        fun copyAudioFile(context: Context, originalPath: String): String? {
            return try {
                val src = File(originalPath)
                if (!src.exists()) return null
                val audioDir = File(context.filesDir, "keep_audio").apply { if (!exists()) mkdirs() }
                val ext = src.extension.ifEmpty { "m4a" }
                val dest = File(audioDir, "audio_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext")
                src.copyTo(dest, overwrite = true)
                dest.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
